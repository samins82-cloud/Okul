package com.elak.okulum.izin

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.auth.SecureOkulumCredentials
import org.json.JSONObject
import java.io.File
import java.util.Locale

class IzinActivity88 : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "izin_url"
        private const val START_URL = "https://www.mcoaihl.com/izin/index.php"
        private const val ALT_URL = "https://mcoaihl.com/izin/index.php"
        private val INTERNAL_HOSTS = setOf("mcoaihl.com", "www.mcoaihl.com")
    }

    private data class PendingDownload(val url: String, val userAgent: String, val contentDisposition: String, val mimeType: String)

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorPanel: LinearLayout
    private var fileCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null
    private var pendingDownload: PendingDownload? = null
    private var alternateHostTried = false
    private var autoLoginAttempts = 0

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback ?: return@registerForActivityResult
        val output: Array<Uri>? = when {
            result.resultCode != Activity.RESULT_OK -> null
            result.data?.data != null -> arrayOf(result.data!!.data!!)
            result.data?.clipData != null -> {
                val clip = result.data!!.clipData!!
                Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
            }
            cameraOutputUri != null -> arrayOf(cameraOutputUri!!)
            else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        }
        callback.onReceiveValue(output)
        fileCallback = null
        cameraOutputUri = null
    }

    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val item = pendingDownload
        pendingDownload = null
        if (granted && item != null) enqueueDownload(item)
        else if (!granted) Toast.makeText(this, "Dosyayı indirmek için depolama izni gerekli.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        buildUi()
        configureInsets()
        configureWebView()
        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            hideError()
        } else {
            loadInitialUrl(intent.getStringExtra(EXTRA_URL))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.let { loadInitialUrl(it) }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        webView = WebView(this).apply { setBackgroundColor(Color.WHITE) }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(238, 244, 251))
            visibility = View.GONE
            addView(TextView(this@IzinActivity88).apply {
                text = "İzin Takip açılmadı"
                textSize = 20f
                setTextColor(Color.rgb(16, 35, 63))
                gravity = Gravity.CENTER
            })
            addView(TextView(this@IzinActivity88).apply {
                text = "Bağlantınızı kontrol edip tekrar deneyin."
                textSize = 13.5f
                setTextColor(Color.rgb(86, 106, 132))
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(18))
            })
            addView(Button(this@IzinActivity88).apply {
                text = "Tekrar Dene"
                setOnClickListener { alternateHostTried = false; autoLoginAttempts = 0; hideError(); webView.loadUrl(START_URL) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(8) })
            addView(Button(this@IzinActivity88).apply {
                text = "Tarayıcıda Aç"
                setOnClickListener { openExternal(webView.url ?: START_URL) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(progressBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP))
        root.addView(errorPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun configureInsets() {
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            loadsImagesAutomatically = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            loadWithOverviewMode = false
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = 100
            defaultTextEncodingName = "UTF-8"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36 ELAK-Okulum/0.8.8"
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
            override fun onShowFileChooser(webView: WebView?, filePathCallback: android.webkit.ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return openFileChooser(fileChooserParams)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return handleNavigation(uri)
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                hideError(); progressBar.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                CookieManager.getInstance().flush()
                tryAutoLogin()
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) handleMainFrameFailure(request.url?.toString())
            }
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) handleMainFrameFailure(request.url?.toString())
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                if (!tryAlternateHost(error?.url)) showError()
            }
            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                showError(); return true
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val item = PendingDownload(url ?: return@setDownloadListener, userAgent.orEmpty(), contentDisposition.orEmpty(), mimeType.orEmpty())
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = item
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else enqueueDownload(item)
        }
    }

    private fun tryAutoLogin() {
        if (autoLoginAttempts >= 3) return
        val creds = SecureOkulumCredentials(this).load() ?: return
        val u = JSONObject.quote(creds.username)
        val p = JSONObject.quote(creds.password)
        val js = """
            (function(){
              try{
                var pass=document.querySelector('input[type=password]');
                if(!pass) return 'NO_LOGIN_FORM';
                var form=pass.form || pass.closest('form');
                if(!form) return 'NO_FORM';
                var user=form.querySelector('input[type=email],input[name*=user i],input[name*=kullan i],input[id*=user i],input[id*=kullan i],input[type=text]');
                if(!user) return 'NO_USER';
                function setv(el,v){
                  var proto=Object.getPrototypeOf(el); var d=Object.getOwnPropertyDescriptor(proto,'value');
                  if(d&&d.set) d.set.call(el,v); else el.value=v;
                  el.dispatchEvent(new Event('input',{bubbles:true}));
                  el.dispatchEvent(new Event('change',{bubbles:true}));
                }
                setv(user,$u); setv(pass,$p);
                var btn=form.querySelector('button[type=submit],input[type=submit],button:not([type])');
                setTimeout(function(){ if(btn) btn.click(); else if(form.requestSubmit) form.requestSubmit(); else form.submit(); },120);
                return 'SUBMITTED';
              }catch(e){return 'ERR';}
            })();
        """.trimIndent()
        autoLoginAttempts++
        webView.evaluateJavascript(js, null)
    }

    private fun loadInitialUrl(raw: String?) {
        alternateHostTried = false
        autoLoginAttempts = 0
        hideError()
        webView.loadUrl(normalizeIzinUrl(raw) ?: START_URL, mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7", "Cache-Control" to "no-cache"))
    }

    private fun normalizeIzinUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(raw)
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path.orEmpty()
            if (uri.scheme !in listOf("http", "https")) null
            else if ((host in INTERNAL_HOSTS || host == "elak.mcoaihl.com") && path.startsWith("/izin")) {
                val normalizedPath = if (path == "/izin" || path == "/izin/") "/izin/index.php" else path
                uri.buildUpon().scheme("https").authority(if (host == "elak.mcoaihl.com") "www.mcoaihl.com" else host).path(normalizedPath).build().toString()
            } else null
        } catch (_: Exception) { null }
    }

    private fun handleNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme == "http" || scheme == "https") {
            val host = uri.host?.lowercase().orEmpty()
            if (host in INTERNAL_HOSTS) return false
            openExternal(uri.toString()); return true
        }
        if (scheme == "intent") {
            return try { startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)); true } catch (_: Exception) { true }
        }
        return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true } catch (_: Exception) { true }
    }

    private fun openFileChooser(params: WebChromeClient.FileChooserParams?): Boolean {
        return try {
            val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/pdf",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "image/jpeg", "image/png", "image/webp"
                ))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
            }
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val cameraDir = File(cacheDir, "camera").apply { mkdirs() }
            val photoFile = File.createTempFile("student_", ".jpg", cameraDir)
            cameraOutputUri = FileProvider.getUriForFile(this, packageName + ".fileprovider", photoFile)
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri)
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, contentIntent)
                putExtra(Intent.EXTRA_TITLE, "Dosya / Fotoğraf Seç")
                if (cameraIntent.resolveActivity(packageManager) != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            }
            fileChooserLauncher.launch(chooser); true
        } catch (_: Exception) {
            fileCallback?.onReceiveValue(null); fileCallback = null; false
        }
    }

    private fun enqueueDownload(item: PendingDownload) {
        try {
            val filename = URLUtil.guessFileName(item.url, item.contentDisposition, item.mimeType).ifBlank { "ELAK-dosya" }
            val req = DownloadManager.Request(Uri.parse(item.url)).apply {
                setTitle(filename)
                setDescription("ELAK İzin Takip dosyası indiriliyor")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                CookieManager.getInstance().getCookie(item.url)?.let { addRequestHeader("Cookie", it) }
                if (item.userAgent.isNotBlank()) addRequestHeader("User-Agent", item.userAgent)
                if (item.mimeType.isNotBlank()) setMimeType(item.mimeType)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "Dosya İndirilenler klasörüne kaydediliyor.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { openExternal(item.url) }
    }

    private fun handleMainFrameFailure(url: String?) {
        if (!tryAlternateHost(url)) showError()
    }

    private fun tryAlternateHost(failingUrl: String?): Boolean {
        if (alternateHostTried) return false
        alternateHostTried = true
        val host = try { Uri.parse(failingUrl ?: "").host.orEmpty() } catch (_: Exception) { "" }
        webView.loadUrl(if (host.equals("www.mcoaihl.com", true)) ALT_URL else START_URL)
        return true
    }

    private fun showError() { progressBar.visibility = View.GONE; errorPanel.visibility = View.VISIBLE; errorPanel.bringToFront() }
    private fun hideError() { errorPanel.visibility = View.GONE; progressBar.bringToFront() }
    private fun openExternal(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()

    override fun onSaveInstanceState(outState: Bundle) { webView.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() { CookieManager.getInstance().flush(); webView.stopLoading(); webView.destroy(); super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else finish() }
}
