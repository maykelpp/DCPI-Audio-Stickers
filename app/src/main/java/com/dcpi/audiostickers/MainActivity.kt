package com.dcpi.audiostickers

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var mediaEngine: MediaEngine
    private lateinit var elizaWAStore: ElizaWAStore
    private val statusExecutor = Executors.newSingleThreadExecutor()
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val selectedUris = mutableMapOf<String, Uri>()
    private val projectUris = mutableMapOf<String, Uri>()
    private val uriPrefs by lazy { getSharedPreferences("dcpi_sources", MODE_PRIVATE) }
    private val elizaPrefs by lazy { getSharedPreferences("elizawa", MODE_PRIVATE) }
    private val openStatusesTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                elizaPrefs.edit().putString("statuses_tree", uri.toString()).apply()
                webView.evaluateJavascript("window.ElizaWA && window.ElizaWA.onFolderSelected(true)", null)
            } catch (e: Exception) {
                webView.evaluateJavascript("window.ElizaWA && window.ElizaWA.onFolderSelected(false)", null)
            }
        } else {
            webView.evaluateJavascript("window.ElizaWA && window.ElizaWA.onFolderSelected(false)", null)
        }
    }

    private fun restoreSelectedUris() {
        for (kind in listOf("audio", "video", "image")) {
            val value = uriPrefs.getString(kind, null) ?: continue
            try { selectedUris[kind] = Uri.parse(value) } catch (_: Exception) {}
        }
        uriPrefs.all.forEach { (key, value) ->
            if (key.startsWith("project:") && value is String) {
                try { projectUris[key.removePrefix("project:")] = Uri.parse(value) } catch (_: Exception) {}
            }
        }
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = contentResolver.getType(uri).orEmpty()
            val kind = when {
                mime.startsWith("audio/") -> "audio"
                mime.startsWith("video/") -> "video"
                mime.startsWith("image/") -> "image"
                else -> null
            }
            if (kind != null) {
                selectedUris[kind] = uri
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                uriPrefs.edit().putString(kind, uri.toString()).apply()
            }
        }
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaEngine = MediaEngine(this)
        elizaWAStore = ElizaWAStore(this)
        restoreSelectedUris()
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            addJavascriptInterface(AndroidBridge(), "DCPIAndroid")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(
                        """
                        (function() {
                          try {
                            const info = JSON.parse(DCPIAndroid.getPlatformInfo());
                            window.DCPI = window.DCPI || {};
                            window.DCPI.nativeBridge = info;
                            const status = document.getElementById('engine-state');
                            if (status) status.textContent = 'Android conectado · exportación M4A/MP4';
                            const version = document.getElementById('app-ver');
                            if (version) version.textContent = info.version;
                          } catch (e) { console.error('DCPI bridge:', e); }
                        })();
                        """.trimIndent(), null
                    )
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    view: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    params: FileChooserParams?
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    val types = params?.acceptTypes
                        ?.filter { it.isNotBlank() }
                        ?.toTypedArray()
                        ?.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
                    openDocument.launch(types)
                    return true
                }
            }
        }
        setContentView(FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(-1, -1))
        })
        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        statusExecutor.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun getPlatformInfo(): String = JSONObject()
            .put("app", "DCPI Audio Stickers")
            .put("version", BuildConfig.VERSION_NAME)
            .put("android", android.os.Build.VERSION.RELEASE)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("bridge", "connected")
            .put("mediaEngine", "media3-aac-m4a-mp4")
            .put("elizawa", "status-vault")
            .toString()

        @JavascriptInterface
        fun registerProjectSource(projectId: String, sourceKind: String): Boolean {
            if (projectId.isBlank() || sourceKind !in listOf("audio", "video")) return false
            val source = selectedUris[sourceKind] ?: return false
            val key = "$projectId:$sourceKind"
            projectUris[key] = source
            uriPrefs.edit().putString("project:$key", source.toString()).apply()
            return true
        }

        @JavascriptInterface
        fun deleteProjectSources(projectId: String) {
            if (projectId.isBlank()) return
            listOf("audio", "video").forEach { kind ->
                val key = "$projectId:$kind"
                projectUris.remove(key)
                uriPrefs.edit().remove("project:$key").apply()
            }
        }

        @JavascriptInterface
        fun exportM4a(projectId: String, sourceKind: String, startMs: Long, endMs: Long, projectName: String, volume: Float, quality: String): String {
            val source = projectUris["$projectId:$sourceKind"] ?: return JSONObject()
                .put("error", "Este proyecto no tiene guardado su archivo de origen. Vuelve a importarlo para exportar.").toString()
            return try {
                mediaEngine.exportM4a(source, startMs, endMs, projectName, volume.coerceIn(0f, 1f), quality).toString()
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Error al exportar").toString()
            }
        }

        @JavascriptInterface
        fun exportMp4(projectId: String, startMs: Long, endMs: Long, projectName: String, volume: Float, quality: String): String {
            val source = projectUris["$projectId:video"] ?: return JSONObject()
                .put("error", "Este proyecto no tiene guardado su vídeo original. Vuelve a importarlo para exportar.").toString()
            return try {
                mediaEngine.exportMp4(source, startMs, endMs, projectName, volume.coerceIn(0f, 1f), quality).toString()
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Error al exportar el vídeo").toString()
            }
        }

        @JavascriptInterface
        fun shareExport(uriString: String, mimeType: String): Boolean {
            return try {
                val uri = Uri.parse(uriString)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = if (mimeType == "video/mp4") "video/mp4" else "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, if (mimeType == "video/mp4") "Compartir vídeo" else "Compartir audio")) }
                true
            } catch (_: Exception) { false }
        }
    }
}
