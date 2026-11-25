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
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var mediaEngine: MediaEngine
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val selectedUris = mutableMapOf<String, Uri>()

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = contentResolver.getType(uri).orEmpty()
            val kind = when {
                mime.startsWith("audio/") -> "audio"
                mime.startsWith("video/") -> "video"
                mime.startsWith("image/") -> "image"
                else -> null
            }
            if (kind != null) selectedUris[kind] = uri
        }
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaEngine = MediaEngine(this)
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
                            if (status) status.textContent = 'Motor multimedia conectado · extracción M4A';
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
            .put("mediaEngine", "m4a-extract-trim")
            .toString()

        @JavascriptInterface
        fun exportM4a(sourceKind: String, startMs: Long, endMs: Long, projectName: String): String {
            val source = selectedUris[sourceKind] ?: return JSONObject()
                .put("error", "No se encontró el archivo de origen seleccionado").toString()
            return try {
                mediaEngine.exportM4a(source, startMs, endMs, projectName).toString()
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Error al exportar").toString()
            }
        }

        @JavascriptInterface
        fun shareExport(uriString: String): Boolean {
            return try {
                val uri = Uri.parse(uriString)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "Compartir audio")) }
                true
            } catch (_: Exception) { false }
        }
    }
}
