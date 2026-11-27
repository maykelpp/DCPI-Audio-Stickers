package com.dcpi.audiostickers

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * ElizaWA local status vault.
 * Source access is granted by the user through the Storage Access Framework.
 * Copies are stored inside the app's private files directory and grouped by
 * a contact label supplied by the user (WhatsApp's cached status files do not
 * reliably expose the publisher's contact identity).
 */
class ElizaWAStore(private val context: Context) {
    private val root: File
        get() = File(context.filesDir, "ElizaWA").apply { mkdirs() }

    fun listAvailable(treeUri: Uri?): JSONArray {
        val result = JSONArray()
        if (treeUri == null) return result
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return result
        tree.listFiles()
            .asSequence()
            .filter { it.isFile && (it.type?.startsWith("image/") == true || it.type?.startsWith("video/") == true) }
            .sortedByDescending { it.lastModified() }
            .take(300)
            .forEach { doc ->
                result.put(JSONObject()
                    .put("id", doc.uri.toString())
                    .put("name", doc.name ?: "estado")
                    .put("mime", doc.type ?: "application/octet-stream")
                    .put("size", doc.length())
                    .put("modified", doc.lastModified()))
            }
        return result
    }

    fun listSaved(): JSONArray {
        val result = JSONArray()
        if (!root.exists()) return result
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { contactDir ->
            contactDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.forEach { file ->
                val mime = when (file.extension.lowercase(Locale.ROOT)) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    "mp4", "3gp", "webm", "mkv" -> "video/mp4"
                    else -> "application/octet-stream"
                }
                result.put(JSONObject()
                    .put("id", contactDir.name + "/" + file.name)
                    .put("contact", contactDir.name)
                    .put("name", file.name)
                    .put("mime", mime)
                    .put("size", file.length())
                    .put("modified", file.lastModified())
                    .put("url", Uri.fromFile(file).toString()))
            }
        }
        return result
    }

    fun copySelected(treeUri: Uri?, selectedJson: String, contactLabel: String): JSONObject {
        require(treeUri != null) { "Primero selecciona la carpeta de estados de WhatsApp." }
        val selected = JSONArray(selectedJson)
        require(selected.length() in 1..30) { "Selecciona entre 1 y 30 estados por operación." }
        val safeContact = sanitizeContact(contactLabel).ifBlank { "Sin asignar" }
        val destination = File(root, safeContact).apply { mkdirs() }
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("No se puede leer la carpeta seleccionada.")
        val allowed = tree.listFiles().associateBy { it.uri.toString() }
        val copied = JSONArray()
        var failed = 0
        for (i in 0 until selected.length()) {
            val sourceUri = selected.optString(i)
            val source = allowed[sourceUri]
            if (source == null || !source.isFile) {
                failed++
                continue
            }
            try {
                val originalName = sanitizeFileName(source.name ?: "estado_${System.currentTimeMillis()}")
                val target = uniqueFile(destination, originalName)
                context.contentResolver.openInputStream(source.uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) }
                } ?: throw IllegalStateException("No se pudo abrir el archivo de origen.")
                copied.put(JSONObject()
                    .put("name", target.name)
                    .put("contact", safeContact)
                    .put("size", target.length()))
            } catch (_: Exception) {
                failed++
            }
        }
        return JSONObject()
            .put("copied", copied)
            .put("count", copied.length())
            .put("failed", failed)
            .put("contact", safeContact)
    }

    fun deleteSaved(relativeId: String): Boolean {
        val base = root.canonicalFile
        val target = File(root, relativeId).canonicalFile
        if (!target.path.startsWith(base.path + File.separator) || !target.isFile) return false
        return target.delete()
    }

    private fun sanitizeContact(value: String): String =
        value.trim().replace(Regex("[\\\\/:*?\"<>|\\\\p{Cntrl}]"), "_")
            .trim('.', ' ').take(48)

    private fun sanitizeFileName(value: String): String {
        val cleaned = value.replace(Regex("[\\\\/:*?\"<>|\\\\p{Cntrl}]"), "_")
            .trim('.', ' ')
            .take(100)
        return cleaned.ifBlank { "estado_${System.currentTimeMillis()}" }
    }

    private fun uniqueFile(directory: File, name: String): File {
        val baseName = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var candidate = File(directory, name)
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "$baseName ($index)$extension")
            index++
        }
        return candidate
    }
}
