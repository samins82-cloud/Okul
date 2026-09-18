package com.elak.okulum

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.auth.SecureOkulumCredentials
import com.elak.okulum.izin.IzinActivity88
import com.elak.okulum.rehber.CallerCache
import com.elak.okulum.rehber.RehberActivity81
import com.elak.okulum.rehber.RehberApi
import com.elak.okulum.rehber.RehberSession
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    companion object {
        private const val BASE_URL = "https://elak.mcoaihl.com/okulum/"
        private val INTERNAL_HOSTS = setOf("elak.mcoaihl.com", "mcoaihl.com", "www.mcoaihl.com", "dp01.ni.net.tr")
    }

    private data class PendingDownload(val url: String, val userAgent: String, val contentDisposition: String, val mimeType: String)

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorPanel: LinearLayout
    private var fileCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private var pendingDownload: PendingDownload? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback ?: return@registerForActivityResult
        callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }
    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val item = pendingDownload; pendingDownload = null
        if (granted && item != null) enqueueDownload(item) else if (!granted) Toast.makeText(this, "Dosya indirme izni verilmedi.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.rgb(8, 26, 56)
        window.navigationBarColor = Color.WHITE
        buildUi(); configureInsets(); configureWebView(); loadHome()
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        webView = WebView(this).apply { setBackgroundColor(Color.WHITE) }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(44, 44, 44, 44); setBackgroundColor(Color.WHITE); visibility = View.GONE
            addView(TextView(this@MainActivity).apply { text = "Bağlantı kurulamadı"; textSize = 20f; setTextColor(Color.rgb(8, 26, 56)); gravity = Gravity.CENTER })
            addView(TextView(this@MainActivity).apply { text = "İnternet bağlantınızı kontrol edip yeniden deneyin."; textSize = 14f; setTextColor(Color.DKGRAY); gravity = Gravity.CENTER; setPadding(0, 12, 0, 20) })
            addView(Button(this@MainActivity).apply { text = "Yeniden Dene"; setOnClickListener { hideError(); if (webView.url.isNullOrBlank()) loadHome() else webView.reload() } })
        }
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(progressBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8, Gravity.TOP))
        root.addView(errorPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun configureInsets() {
        val controller = WindowInsetsControllerCompat(window, root)
        controller.isAppearanceLightStatusBars = false; controller.isAppearanceLightNavigationBars = true
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    inner class NativeBridge {
        @JavascriptInterface
        fun okulumLogin(username: String?, password: String?) {
            val u = username.orEmpty().trim(); val p = password.orEmpty()
            if (u.isBlank() || p.isBlank()) return

            OkulumSession(this@MainActivity).saveCredentials(u, p)
            SecureOkulumCredentials(this@MainActivity).save(u, p)

            thread {
                try {
                    val login = RehberApi.login(u, p)
                    val session = RehberSession(this@MainActivity)
                    session.token = login.token; session.username = u
                    val count = CallerCache(this@MainActivity).replaceFromSync(RehberApi.sync(login.token))
                    session.lastSync = System.currentTimeMillis()
                    runOnUiThread { Toast.makeText(this@MainActivity, "Akıllı Rehber otomatik bağlandı: $count telefon", Toast.LENGTH_SHORT).show() }
                } catch (_: Exception) { }
            }
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.addJavascriptInterface(NativeBridge(), "ELAKNative")
        with(webView.settings) {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            allowFileAccess = false; allowContentAccess = true; loadsImagesAutomatically = true
            defaultTextEncodingName = "UTF-8"; textZoom = 100; cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            userAgentString = "$userAgentString ELAK-Okulum/0.8.8 tr-TR"
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) { progressBar.progress = newProgress; progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE }
            override fun onShowFileChooser(webView: WebView?, filePathCallback: android.webkit.ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null); fileCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
                    fileChooserLauncher.launch(intent); true
                } catch (_: Exception) {
                    fileCallback?.onReceiveValue(null); fileCallback = null; Toast.makeText(this@MainActivity, "Dosya seçici açılamadı.", Toast.LENGTH_LONG).show(); false
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase().orEmpty(); val host = uri.host?.lowercase().orEmpty(); val path = uri.path.orEmpty()
                if ((scheme == "http" || scheme == "https") && host == "elak.mcoaihl.com" && path.startsWith("/rehber")) {
                    startActivity(Intent(this@MainActivity, RehberActivity81::class.java)); return true
                }
                if ((scheme == "http" || scheme == "https") && host in setOf("elak.mcoaihl.com", "mcoaihl.com", "www.mcoaihl.com") && path.startsWith("/izin")) {
                    startActivity(Intent(this@MainActivity, IzinActivity88::class.java).putExtra(IzinActivity88.EXTRA_URL, uri.toString()))
                    return true
                }
                if ((scheme == "http" || scheme == "https") && host in INTERNAL_HOSTS) return false
                return try { startActivity(if (scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, uri)); true } catch (_: Exception) { false }
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { hideError() }
            override fun onPageFinished(view: WebView?, url: String?) { CookieManager.getInstance().flush(); injectLoginBridge() }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) { if (request?.isForMainFrame == true) showError() }
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val item = PendingDownload(url ?: return@setDownloadListener, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType.orEmpty())
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = item; storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else enqueueDownload(item)
        }
    }

    private fun injectLoginBridge() {
        val js = """
            (function(){
              if(window.__elakNativeHooked) return;
              window.__elakNativeHooked=true;
              function send(){
                try{
                  var u=document.getElementById('schoolUser');
                  var p=document.getElementById('schoolPass');
                  if(window.ELAKNative && u && p && u.value && p.value){ window.ELAKNative.okulumLogin(u.value,p.value); }
                }catch(e){}
              }
              var b=document.getElementById('loginBtn'); if(b) b.addEventListener('click',send,true);
              var p=document.getElementById('schoolPass'); if(p) p.addEventListener('keydown',function(e){if(e.key==='Enter')send();},true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun enqueueDownload(item: PendingDownload) {
        try {
            if (!item.url.startsWith("http://") && !item.url.startsWith("https://")) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))); return }
            val fileName = URLUtil.guessFileName(item.url, item.contentDisposition, item.mimeType)
            val request = DownloadManager.Request(Uri.parse(item.url)).apply {
                setTitle(fileName); setDescription("ELAK Okulum dosyası indiriliyor")
                if (item.mimeType.isNotBlank()) setMimeType(item.mimeType)
                if (item.userAgent.isNotBlank()) addRequestHeader("User-Agent", item.userAgent)
                CookieManager.getInstance().getCookie(item.url)?.let { addRequestHeader("Cookie", it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true); setAllowedOverRoaming(true)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "İndirme başlatıldı: $fileName", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) } catch (_: Exception) { Toast.makeText(this, "Dosya indirilemedi.", Toast.LENGTH_LONG).show() }
        }
    }

    private fun loadHome() { webView.loadUrl(BASE_URL, mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.6")) }
    private fun showError() { progressBar.visibility = View.GONE; errorPanel.visibility = View.VISIBLE; errorPanel.bringToFront() }
    private fun hideError() { errorPanel.visibility = View.GONE; progressBar.bringToFront() }
    override fun onResume() { super.onResume(); CookieManager.getInstance().flush() }
    override fun onDestroy() { fileCallback?.onReceiveValue(null); fileCallback = null; webView.stopLoading(); webView.destroy(); super.onDestroy() }
    @Deprecated("Deprecated in Java") override fun onBackPressed() {
        if (errorPanel.visibility == View.VISIBLE) { hideError(); loadHome() }
        else if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
