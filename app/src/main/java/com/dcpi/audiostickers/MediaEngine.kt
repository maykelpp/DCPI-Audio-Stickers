package com.dcpi.audiostickers

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 4 audio pipeline: Media3 decodes the source, applies gain, trims and encodes AAC.
 * Output is an audio-only MP4 container (.m4a). Video rendering/export is not implemented here.
 */
@OptIn(UnstableApi::class)
class MediaEngine(private val context: Context) {

    fun exportMp4(
        source: Uri,
        startMs: Long,
        endMs: Long,
        displayName: String,
        volume: Float,
        quality: String
    ): JSONObject {
        require(startMs >= 0 && endMs > startMs) { "Rango de recorte no válido" }
        require(volume in 0f..1f) { "Volumen no válido" }
        val bitrate = when (quality) {
            "standard" -> 96_000
            "high" -> 160_000
            "max" -> 256_000
            else -> throw IllegalArgumentException("Calidad de exportación no válida")
        }
        MediaExtractor().let { extractor ->
            try {
                extractor.setDataSource(context, source, null)
                val hasVideo = (0 until extractor.trackCount).any {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                }
                require(hasVideo) { "El archivo seleccionado no contiene vídeo" }
            } finally { extractor.release() }
        }
        val temp = File.createTempFile("dcpi_video_", ".mp4", context.cacheDir)
        val latch = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val transformerRef = AtomicReference<Transformer?>(null)
        val mediaItem = MediaItem.Builder().setUri(source)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build()
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(listOf(GainProcessor(DefaultGainProvider.Builder(volume).build())), emptyList()))
            .build()
        Handler(Looper.getMainLooper()).post {
            try {
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(bitrate).build())
                    .build()
                val transformer = Transformer.Builder(context)
                    .setEncoderFactory(encoderFactory)
                    .setVideoMimeType("video/avc")
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .build()
                transformerRef.set(transformer)
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { latch.countDown() }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        failure.set(exportException); latch.countDown()
                    }
                })
                transformer.start(editedItem, temp.absolutePath)
            } catch (t: Throwable) { failure.set(t); latch.countDown() }
        }
        if (!latch.await(8, TimeUnit.MINUTES)) {
            Handler(Looper.getMainLooper()).post { transformerRef.get()?.cancel() }
            temp.delete()
            throw IllegalStateException("La exportación de vídeo tardó demasiado y se canceló")
        }
        failure.get()?.let { temp.delete(); throw IllegalStateException(it.message ?: "No se pudo procesar el vídeo", it) }
        require(temp.exists() && temp.length() > 0L) { "El motor no generó el vídeo de salida" }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "dcpi_video" }.removeSuffix(".mp4") + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/DCPI Audio Stickers")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val outUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: run { temp.delete(); error("No se pudo crear el archivo de salida") }
        try {
            context.contentResolver.openOutputStream(outUri)?.use { output ->
                FileInputStream(temp).use { input -> input.copyTo(output) }
            } ?: error("No se pudo escribir el archivo")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(outUri, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
            }
        } catch (e: Exception) {
            context.contentResolver.delete(outUri, null, null); throw e
        } finally { temp.delete() }
        return JSONObject().put("uri", outUri.toString()).put("name", safeName)
            .put("size", temp.length()).put("format", "mp4").put("bitrate", bitrate).put("volume", volume)
    }

    fun exportM4a(
        source: Uri,
        startMs: Long,
        endMs: Long,
        displayName: String,
        volume: Float,
        quality: String
    ): JSONObject {
        require(startMs >= 0 && endMs > startMs) { "Rango de recorte no válido" }
        require(volume in 0f..1f) { "Volumen no válido" }

        val bitrate = when (quality) {
            "standard" -> 96_000
            "high" -> 160_000
            "max" -> 256_000
            else -> throw IllegalArgumentException("Calidad de exportación no válida")
        }

        // Fail early with a clear message if the selected source has no audio track.
        MediaExtractor().let { extractor ->
            try {
                extractor.setDataSource(context, source, null)
                val hasAudio = (0 until extractor.trackCount).any {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                require(hasAudio) { "El archivo no contiene una pista de audio compatible" }
            } finally {
                extractor.release()
            }
        }

        val temp = File.createTempFile("dcpi_processed_", ".mp4", context.cacheDir)
        val latch = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val transformerRef = AtomicReference<Transformer?>(null)
        val mediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(
                Effects(
                    listOf(GainProcessor(DefaultGainProvider.Builder(volume).build())),
                    emptyList()
                )
            )
            .build()

        Handler(Looper.getMainLooper()).post {
            try {
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(bitrate).build())
                    .build()
                val transformer = Transformer.Builder(context)
                    .setEncoderFactory(encoderFactory)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .build()
                transformerRef.set(transformer)
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        latch.countDown()
                    }
                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        failure.set(exportException)
                        latch.countDown()
                    }
                })
                transformer.start(editedItem, temp.absolutePath)
            } catch (t: Throwable) {
                failure.set(t)
                latch.countDown()
            }
        }

        if (!latch.await(5, TimeUnit.MINUTES)) {
            Handler(Looper.getMainLooper()).post { transformerRef.get()?.cancel() }
            temp.delete()
            throw IllegalStateException("La exportación tardó demasiado y se canceló")
        }
        failure.get()?.let {
            temp.delete()
            throw IllegalStateException(it.message ?: "No se pudo procesar el audio", it)
        }
        require(temp.exists() && temp.length() > 0L) { "El motor no generó un archivo de audio" }

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
            ?: run { temp.delete(); error("No se pudo crear el archivo de salida") }
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
        } finally {
            temp.delete()
        }
        return JSONObject()
            .put("uri", outUri.toString())
            .put("name", safeName)
            .put("size", context.contentResolver.openFileDescriptor(outUri, "r")?.use { it.statSize } ?: 0L)
            .put("format", "m4a")
            .put("bitrate", bitrate)
            .put("volume", volume)
    }
}
