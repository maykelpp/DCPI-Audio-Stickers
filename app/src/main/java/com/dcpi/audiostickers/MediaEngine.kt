package com.dcpi.audiostickers

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Phase 2: lossless audio-track extraction and trimming.
 * Keeps the source audio codec; M4A/MP4 container output only.
 */
class MediaEngine(private val context: Context) {
    fun exportM4a(source: Uri, startMs: Long, endMs: Long, displayName: String): JSONObject {
        require(startMs >= 0 && endMs > startMs) { "Rango de recorte no válido" }
        val temp = File.createTempFile("dcpi_audio_", ".m4a", context.cacheDir)
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, source, null)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = f
                    break
                }
            }
            require(audioTrack >= 0 && audioFormat != null) { "No se encontró una pista de audio compatible" }
            extractor.selectTrack(audioTrack)
            val muxerLocal = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = muxerLocal
            val outTrack = muxerLocal.addTrack(audioFormat!!)
            muxerLocal.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val maxSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
            val buffer = ByteBuffer.allocateDirect(maxSize)
            val info = MediaMuxer.BufferInfo()
            var firstPts = -1L
            var written = 0
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val pts = extractor.sampleTime
                if (pts < 0 || pts >= endUs) break
                if (pts >= startUs) {
                    if (firstPts < 0) firstPts = pts
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = (pts - firstPts).coerceAtLeast(0)
                    info.flags = extractor.sampleFlags
                    buffer.position(0)
                    buffer.limit(size)
                    muxerLocal.writeSampleData(outTrack, buffer, info)
                    written++
                }
                if (!extractor.advance()) break
            }
            require(written > 0) { "El recorte no contiene muestras de audio" }
            muxerLocal.stop()
            muxerLocal.release()
            muxer = null
            extractor.release()
            extractor = null

            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "dcpi_audio" }.removeSuffix(".m4a") + ".m4a"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/DCPI Audio Stickers")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val outUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("No se pudo crear el archivo de salida")
            try {
                context.contentResolver.openOutputStream(outUri)?.use { output ->
                    FileInputStream(temp).use { input -> input.copyTo(output) }
                } ?: error("No se pudo escribir el archivo")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    context.contentResolver.update(outUri, done, null, null)
                }
            } catch (e: Exception) {
                context.contentResolver.delete(outUri, null, null)
                throw e
            }
            return JSONObject()
                .put("uri", outUri.toString())
                .put("name", safeName)
                .put("size", temp.length())
                .put("format", "m4a")
                .put("samples", written)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            temp.delete()
        }
    }
}
