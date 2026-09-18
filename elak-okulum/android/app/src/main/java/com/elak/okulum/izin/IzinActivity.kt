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
import android.os.Handler
import android.os.Looper
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
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.OkulumSession
import org.json.JSONObject
import java.io.File

class IzinActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "izin_url"
        private const val START_URL = "https://www.mcoaihl.com/izin/index.php"
        private const val ALT_URL = "https://mcoaihl.com/izin/index.php"
        private const val INTERNAL_HOST = "mcoaihl.com"
        private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }

    private data class PendingDownload(
        val url: String,
        val userAgent: String,
        val contentDisposition: String,
        val mimeType: String
    )

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private var fileCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null
    private var pendingDownload: PendingDownload? = null
    private var alternateHostTried = false
    private var currentHostReloadTried = false
    private var showingLocalError = false
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
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        buildUi()
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        configureWebView()

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            showingLocalError = false
        } else {
            val initial = normalizeInternalUrl(intent.getStringExtra(EXTRA_URL)) ?: START_URL
            loadMainUrl(initial, true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        normalizeInternalUrl(intent.getStringExtra(EXTRA_URL))?.let { loadMainUrl(it, true) }
    }

    private fun buildUi() {
        // Bağımsız ELAK İzin Takip APK'sı ile aynı: tam ekran WebView + ince yükleme çubuğu.
        root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        webView = WebView(this).apply { setBackgroundColor(Color.WHITE) }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(progressBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP))
        setContentView(root)
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
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
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = MOBILE_UA
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return handleNavigation(request?.url?.toString().orEmpty())
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return handleNavigation(url.orEmpty())
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (!showingLocalError) progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                progressBar.visibility = View.GONE
                if (!showingLocalError && isInternalUrl(url.orEmpty())) {
                    injectOkulumAutoLogin()
                    scheduleRealBlankCheck(url.orEmpty())
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val detail = "WebView hata kodu ${error?.errorCode ?: -1}: ${error?.description.orEmpty()}"
                    handleMainFrameFailure(request.url?.toString() ?: START_URL, "Sayfa yüklenemedi", detail)
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true && errorResponse != null && errorResponse.statusCode >= 400) {
                    handleMainFrameFailure(
                        request.url?.toString() ?: START_URL,
                        "Sunucu sayfayı açamadı",
                        "HTTP ${errorResponse.statusCode} ${errorResponse.reasonPhrase.orEmpty()}"
                    )
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                val failing = error?.url ?: START_URL
                if (tryAlternateHost(failing)) return
                showLocalError(
                    "Güvenli bağlantı kurulamadı",
                    "Sunucunun SSL sertifikası Android tarafından doğrulanamadı. Sertifika kontrolü güvenlik nedeniyle kapatılmadı.",
                    failing
                )
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                showLocalError(
                    "Android WebView yeniden başlatılmalı",
                    "Android System WebView işlemi kapandı. Uygulamayı kapatıp yeniden açın; sorun sürerse Android System WebView ve Chrome'u güncelleyin.",
                    START_URL
                )
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (!showingLocalError) progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                openFileChooser()
                return true
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val item = PendingDownload(
                url = url ?: return@setDownloadListener,
                userAgent = userAgent.orEmpty(),
                contentDisposition = contentDisposition.orEmpty(),
                mimeType = mimeType.orEmpty()
            )
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingDownload = item
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                enqueueDownload(item)
            }
        }
    }

    private fun injectOkulumAutoLogin() {
        if (autoLoginAttempts >= 2) return
        val session = OkulumSession(this)
        val username = session.username
        val password = session.password
        if (username.isBlank() || password.isBlank()) return

        autoLoginAttempts++
        val userJs = JSONObject.quote(username)
        val passJs = JSONObject.quote(password)
        val js = """
            (function(){
              try {
                var passwords = Array.prototype.slice.call(document.querySelectorAll('input[type="password"]'))
                  .filter(function(e){ return e.offsetParent !== null && !e.disabled; });
                if (passwords.length !== 1) return false;
                var p = passwords[0];
                var form = p.form || p.closest('form');
                if (!form) return false;
                var candidates = [
                  '#schoolUser','#username','#user','#kullanici','#kullaniciAdi',
                  'input[name="username"]','input[name="user"]','input[name="kullanici"]',
                  'input[name="kullanici_adi"]','input[name="email"]','input[type="text"]','input[type="email"]'
                ];
                var u = null;
                for (var i=0;i<candidates.length;i++) {
                  var found = form.querySelector(candidates[i]);
                  if (found && found.offsetParent !== null && !found.disabled) { u = found; break; }
                }
                if (!u) return false;
                function setValue(el, value) {
                  var proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                  var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                  if (desc && desc.set) desc.set.call(el, value); else el.value = value;
                  el.dispatchEvent(new Event('input',{bubbles:true}));
                  el.dispatchEvent(new Event('change',{bubbles:true}));
                }
                setValue(u, $userJs);
                setValue(p, $passJs);
                var submit = form.querySelector('button[type="submit"],input[type="submit"],#loginBtn,.login-btn,.btn-login');
                setTimeout(function(){
                  try {
                    if (form.requestSubmit) form.requestSubmit(submit || undefined);
                    else if (submit) submit.click();
                    else form.submit();
                  } catch(e) { if (submit) submit.click(); else form.submit(); }
                }, 120);
                return true;
              } catch(e) { return false; }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            if (result == "true") {
                Toast.makeText(this, "ELAK Okulum hesabıyla İzin Takip açılıyor…", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun normalizeInternalUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(raw)
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase().orEmpty()
            if (scheme !in listOf("http", "https")) return null
            if (!(host == INTERNAL_HOST || host.endsWith(".$INTERNAL_HOST") || host == "elak.mcoaihl.com")) return null
            val path = uri.path.orEmpty()
            val correctedHost = if (host == "elak.mcoaihl.com") "www.mcoaihl.com" else host
            val correctedPath = if (path == "/izin" || path == "/izin/") "/izin/index.php" else path
            uri.buildUpon().scheme("https").authority(correctedHost).path(correctedPath).build().toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun loadMainUrl(url: String, resetAttempts: Boolean) {
        showingLocalError = false
        if (resetAttempts) {
            currentHostReloadTried = false
            alternateHostTried = false
            autoLoginAttempts = 0
        }
        webView.loadUrl(
            normalizeInternalUrl(url) ?: START_URL,
            mapOf(
                "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
                "Cache-Control" to "no-cache"
            )
        )
    }

    private fun isInternalUrl(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase().orEmpty()
            host == INTERNAL_HOST || host.endsWith(".$INTERNAL_HOST")
        } catch (_: Exception) {
            false
        }
    }

    private fun isWwwHost(url: String): Boolean {
        return try { Uri.parse(url).host.equals("www.mcoaihl.com", true) } catch (_: Exception) { false }
    }

    private fun tryAlternateHost(failingUrl: String): Boolean {
        if (alternateHostTried) return false
        alternateHostTried = true
        currentHostReloadTried = false
        val target = if (isWwwHost(failingUrl)) ALT_URL else START_URL
        Toast.makeText(this, "Alternatif okul adresi deneniyor…", Toast.LENGTH_SHORT).show()
        loadMainUrl(target, false)
        return true
    }

    private fun handleMainFrameFailure(failingUrl: String, title: String, detail: String) {
        if (tryAlternateHost(failingUrl)) return
        showLocalError(title, detail, failingUrl)
    }

    private fun scheduleRealBlankCheck(url: String) {
        handler.postDelayed({
            if (isFinishing || showingLocalError) return@postDelayed
            webView.evaluateJavascript(
                "(function(){try{return JSON.stringify({html:(document.documentElement&&document.documentElement.outerHTML?document.documentElement.outerHTML.length:0),body:(document.body&&document.body.innerHTML?document.body.innerHTML.length:0)});}catch(e){return JSON.stringify({html:-1,body:-1});}})();"
            ) { value ->
                if (showingLocalError || value == null) return@evaluateJavascript
                val decoded = value.replace("\\\"", "\"").trim('"')
                val htmlLen = extractJsonInt(decoded, "html")
                val bodyLen = extractJsonInt(decoded, "body")
                if (htmlLen >= 200 || bodyLen >= 80 || htmlLen < 0) return@evaluateJavascript

                if (!currentHostReloadTried) {
                    currentHostReloadTried = true
                    webView.clearCache(true)
                    Toast.makeText(this, "Sayfa yeniden yükleniyor…", Toast.LENGTH_SHORT).show()
                    loadMainUrl(url, false)
                    return@evaluateJavascript
                }
                if (tryAlternateHost(url)) return@evaluateJavascript
                showLocalError(
                    "Sunucudan boş içerik geldi",
                    "Sunucu bağlantıyı kabul etti ancak HTML içeriği boş döndü. Telefon tarayıcısında çalışan aynı adresi açabilirsiniz.",
                    url
                )
            }
        }, 4500)
    }

    private fun extractJsonInt(json: String, key: String): Int {
        return try {
            val token = "\"$key\":"
            var start = json.indexOf(token)
            if (start < 0) return -1
            start += token.length
            var end = start
            while (end < json.length && (json[end].isDigit() || json[end] == '-')) end++
            json.substring(start, end).toInt()
        } catch (_: Exception) {
            -1
        }
    }

    private fun showLocalError(title: String, detail: String, url: String) {
        showingLocalError = true
        progressBar.visibility = View.GONE
        val safeTitle = htmlEscape(title)
        val safeDetail = htmlEscape(detail)
        val safeUrl = htmlEscape(url)
        val html = """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{margin:0;background:#eef4fb;font-family:Arial,sans-serif;color:#10233f}.wrap{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}.card{width:100%;max-width:520px;background:#fff;border:1px solid #d9e4f1;border-radius:22px;padding:24px;box-shadow:0 18px 55px rgba(15,47,92,.13)}.icon{width:56px;height:56px;border-radius:18px;background:#e8f1ff;color:#1769e0;display:grid;place-items:center;font-size:29px;font-weight:bold}h1{font-size:22px;margin:16px 0 8px}p{font-size:14px;line-height:1.55;color:#566a84}.url{font-size:11px;background:#f6f8fb;border:1px solid #e1e8f0;border-radius:10px;padding:10px;word-break:break-all;color:#50627a}.btn{display:block;text-align:center;text-decoration:none;background:#1769e0;color:#fff;padding:13px 15px;border-radius:12px;font-weight:bold;margin-top:12px}.btn2{background:#0f766e}.small{font-size:12px;color:#7a899d;margin-top:12px}</style></head>
            <body><div class="wrap"><div class="card"><div class="icon">!</div><h1>$safeTitle</h1><p>$safeDetail</p><div class="url">$safeUrl</div><a class="btn" href="elak://retry">Tekrar Dene</a><a class="btn btn2" href="elak://browser">Tarayıcıda Aç</a><div class="small">ELAK Okulum · İzin Takip</div></div></div></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://www.mcoaihl.com/", html, "text/html", "UTF-8", null)
    }

    private fun htmlEscape(value: String): String {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    }

    private fun handleNavigation(url: String): Boolean {
        if (url.isBlank()) return false
        if (url.startsWith("elak://retry")) {
            loadMainUrl(START_URL, true)
            return true
        }
        if (url.startsWith("elak://browser")) {
            openExternal(START_URL)
            return true
        }
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase().orEmpty()
            if ((scheme == "https" || scheme == "http") && (host == INTERNAL_HOST || host.endsWith(".$INTERNAL_HOST"))) {
                showingLocalError = false
                return false
            }
            if (scheme == "intent") {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                if (intent.resolveActivity(packageManager) != null) startActivity(intent)
                else intent.getStringExtra("browser_fallback_url")?.let { openExternal(it) }
                return true
            }
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            Toast.makeText(this, "Bağlantı açılamadı.", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun openFileChooser() {
        val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "image/jpeg",
                "image/png",
                "image/webp"
            ))
        }

        var cameraIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            val cameraDir = File(cacheDir, "camera").apply { mkdirs() }
            val photoFile = File.createTempFile("student_", ".jpg", cameraDir)
            cameraOutputUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            cameraIntent?.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri)
            cameraIntent?.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (cameraIntent?.resolveActivity(packageManager) == null) cameraIntent = null
        } catch (_: Exception) {
            cameraIntent = null
            cameraOutputUri = null
        }

        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentIntent)
            putExtra(Intent.EXTRA_TITLE, "Dosya / Fotoğraf Seç")
            if (cameraIntent != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        }
        try {
            fileChooserLauncher.launch(chooser)
        } catch (_: Exception) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            Toast.makeText(this, "Dosya seçici açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enqueueDownload(item: PendingDownload) {
        try {
            var filename = URLUtil.guessFileName(item.url, item.contentDisposition, item.mimeType)
            if (filename.isBlank()) filename = "ELAK-dosya"
            val request = DownloadManager.Request(Uri.parse(item.url)).apply {
                setTitle(filename)
                setDescription("ELAK İzin Takip dosyası indiriliyor")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                CookieManager.getInstance().getCookie(item.url)?.let { addRequestHeader("Cookie", it) }
                addRequestHeader("User-Agent", MOBILE_UA)
                if (item.mimeType.isNotBlank()) setMimeType(item.mimeType)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "Dosya İndirilenler klasörüne kaydediliyor.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            openExternal(item.url)
        }
    }

    private fun openExternal(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Telefon tarayıcısı açılamadı.", Toast.LENGTH_SHORT).show() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack() && !showingLocalError) webView.goBack() else finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        CookieManager.getInstance().flush()
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = null
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
