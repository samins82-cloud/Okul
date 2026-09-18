package com.elak.okulum.izin

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
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
import android.net.http.SslError
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
import java.io.File
import java.util.Locale

class IzinActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "izin_url"
        private const val START_URL = "https://mcoaihl.com/izin/index.php"
        private const val ALT_URL = "https://www.mcoaihl.com/izin/index.php"
        private val INTERNAL_HOSTS = setOf("mcoaihl.com", "www.mcoaihl.com")
    }

    private data class PendingDownload(
        val url: String,
        val userAgent: String,
        val contentDisposition: String,
        val mimeType: String
    )

    private lateinit var root: LinearLayout
    private lateinit var webFrame: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorTitle: TextView
    private lateinit var errorDetail: TextView

    private var fileCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null
    private var pendingDownload: PendingDownload? = null
    private var alternateHostTried = false
    private var blankCheckGeneration = 0

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
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 249, 253))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.WHITE)
            elevation = dp(5).toFloat()
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(9, 49, 88))
            contentDescription = "Geri"
            setOnClickListener { handleBack() }
        }
        val titleWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleWrap.addView(TextView(this).apply {
            text = "İzin Takip"
            textSize = 19f
            setTextColor(Color.rgb(9, 49, 88))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        titleWrap.addView(TextView(this).apply {
            text = "ELAK Okulum"
            textSize = 10.5f
            setTextColor(Color.rgb(111, 128, 149))
        })
        val browser = TextView(this).apply {
            text = "↗"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(27, 103, 166))
            contentDescription = "Tarayıcıda Aç"
            setOnClickListener { openExternal(webView.url ?: START_URL) }
        }
        val refresh = TextView(this).apply {
            text = "↻"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(27, 103, 166))
            contentDescription = "Yenile"
            setOnClickListener {
                alternateHostTried = false
                hideError()
                if (webView.url.isNullOrBlank()) webView.loadUrl(START_URL) else webView.reload()
            }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(50)))
        header.addView(titleWrap, LinearLayout.LayoutParams(0, dp(50), 1f))
        header.addView(browser, LinearLayout.LayoutParams(dp(44), dp(50)))
        header.addView(refresh, LinearLayout.LayoutParams(dp(44), dp(50)))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))

        webFrame = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
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
        }
        errorTitle = TextView(this).apply {
            text = "Bağlantı kurulamadı"
            textSize = 20f
            setTextColor(Color.rgb(16, 35, 63))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        errorDetail = TextView(this).apply {
            text = "İnternet bağlantınızı kontrol edip yeniden deneyin."
            textSize = 13.5f
            setTextColor(Color.rgb(86, 106, 132))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(18))
        }
        val retry = Button(this).apply {
            text = "Tekrar Dene"
            setOnClickListener {
                alternateHostTried = false
                hideError()
                webView.loadUrl(START_URL)
            }
        }
        val openBrowser = Button(this).apply {
            text = "Tarayıcıda Aç"
            setOnClickListener { openExternal(webView.url ?: START_URL) }
        }
        errorPanel.addView(errorTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        errorPanel.addView(errorDetail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        errorPanel.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(8) })
        errorPanel.addView(openBrowser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        webFrame.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webFrame.addView(progressBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP))
        webFrame.addView(errorPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(webFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
            loadWithOverviewMode = true
            useWideViewPort = true
            defaultTextEncodingName = "UTF-8"
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString ELAK-Okulum/0.8.7 Izin-Takip/1.0.2 tr-TR"
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
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
                hideError()
                progressBar.visibility = View.VISIBLE
                blankCheckGeneration++
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                CookieManager.getInstance().flush()
                scheduleBlankCheck(url.orEmpty())
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    handleMainFrameFailure("WebView hata kodu ${error.errorCode}: ${error.description}", request.url?.toString())
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                    handleMainFrameFailure("HTTP ${errorResponse?.statusCode}: ${errorResponse?.reasonPhrase.orEmpty()}", request.url?.toString())
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                showLocalError(
                    "Güvenli bağlantı kurulamadı",
                    "Sunucunun SSL sertifikası Android tarafından doğrulanamadı. Sertifika kontrolü güvenlik nedeniyle kapatılmadı."
                )
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                showLocalError(
                    "Android WebView yeniden başlatılmalı",
                    "WebView işlemi kapandı. Uygulamayı kapatıp yeniden açın; sorun sürerse Android System WebView ve Chrome'u güncelleyin."
                )
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

    private fun loadInitialUrl(raw: String?) {
        alternateHostTried = false
        hideError()
        val target = normalizeIzinUrl(raw) ?: START_URL
        webView.loadUrl(target, mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"))
    }

    private fun normalizeIzinUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(raw)
            if (uri.scheme !in listOf("http", "https")) return null
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path.orEmpty()
            if (host == "elak.mcoaihl.com" && path.startsWith("/izin")) {
                uri.buildUpon().authority("mcoaihl.com").scheme("https").build().toString()
            } else if (host in INTERNAL_HOSTS && path.startsWith("/izin")) {
                uri.buildUpon().scheme("https").build().toString()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun handleNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme == "elak") {
            when (uri.host?.lowercase()) {
                "retry" -> {
                    alternateHostTried = false
                    hideError()
                    webView.loadUrl(START_URL)
                }
                "browser" -> openExternal(webView.url ?: START_URL)
            }
            return true
        }
        if (scheme == "intent") {
            return try {
                startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME))
                true
            } catch (_: Exception) {
                false
            }
        }
        if (scheme == "http" || scheme == "https") {
            val host = uri.host?.lowercase().orEmpty()
            if (host in INTERNAL_HOSTS && uri.path.orEmpty().startsWith("/izin")) return false
            openExternal(uri.toString())
            return true
        }
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun openFileChooser(params: WebChromeClient.FileChooserParams?): Boolean {
        return try {
            val accept = params?.acceptTypes?.filter { it.isNotBlank() }.orEmpty()
            val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (accept.size == 1) accept.first() else "*/*"
                if (accept.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, accept.toTypedArray())
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
            }

            val initialIntents = mutableListOf<Intent>()
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (cameraIntent.resolveActivity(packageManager) != null) {
                val cameraDir = File(cacheDir, "izin-camera").apply { mkdirs() }
                val photoFile = File.createTempFile("izin_", ".jpg", cameraDir)
                val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
                cameraOutputUri = photoUri
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                initialIntents.add(cameraIntent)
            }

            val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, contentIntent)
                putExtra(Intent.EXTRA_TITLE, "Dosya / Fotoğraf Seç")
                if (initialIntents.isNotEmpty()) putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
            }
            fileChooserLauncher.launch(chooser)
            true
        } catch (_: Exception) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            cameraOutputUri = null
            Toast.makeText(this, "Dosya seçici açılamadı.", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun enqueueDownload(item: PendingDownload) {
        try {
            val fileName = URLUtil.guessFileName(item.url, item.contentDisposition, item.mimeType)
            val request = DownloadManager.Request(Uri.parse(item.url)).apply {
                setTitle(fileName)
                setDescription("ELAK İzin Takip dosyası indiriliyor")
                if (item.mimeType.isNotBlank()) setMimeType(item.mimeType)
                if (item.userAgent.isNotBlank()) addRequestHeader("User-Agent", item.userAgent)
                addRequestHeader("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.7")
                CookieManager.getInstance().getCookie(item.url)?.let { addRequestHeader("Cookie", it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "Dosya İndirilenler klasörüne kaydediliyor: $fileName", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            try {
                openExternal(item.url)
            } catch (_: Exception) {
                Toast.makeText(this, "Dosya indirilemedi.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun scheduleBlankCheck(url: String) {
        val generation = ++blankCheckGeneration
        webView.postDelayed({
            if (generation != blankCheckGeneration || isFinishing || errorPanel.visibility == View.VISIBLE) return@postDelayed
            webView.evaluateJavascript(
                "(function(){try{return (document.documentElement&&document.documentElement.outerHTML?document.documentElement.outerHTML.length:0);}catch(e){return -1;}})();"
            ) { result ->
                val len = result?.replace("\"", "")?.trim()?.toIntOrNull() ?: -1
                if (generation == blankCheckGeneration && len in 0..120 && isEntryUrl(url)) {
                    if (!tryAlternateHost()) {
                        showLocalError(
                            "Sunucudan boş içerik geldi",
                            "Sunucu bağlantıyı kabul etti ancak HTML içeriği boş. Telefon tarayıcında çalışan aynı adresi açmak için Tarayıcıda Aç düğmesini kullanabilirsiniz."
                        )
                    }
                }
            }
        }, 1800L)
    }

    private fun handleMainFrameFailure(detail: String, failingUrl: String?) {
        progressBar.visibility = View.GONE
        if (isEntryUrl(failingUrl.orEmpty()) && tryAlternateHost()) return
        showLocalError("Sayfa yüklenemedi", detail)
    }

    private fun tryAlternateHost(): Boolean {
        if (alternateHostTried) return false
        alternateHostTried = true
        webView.loadUrl(ALT_URL, mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.7"))
        Toast.makeText(this, "Alternatif okul adresi deneniyor…", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun isEntryUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val p = uri.path.orEmpty().trimEnd('/')
            uri.host?.lowercase() in INTERNAL_HOSTS && (p == "/izin" || p == "/izin/index.php")
        } catch (_: Exception) {
            false
        }
    }

    private fun showLocalError(title: String, detail: String) {
        progressBar.visibility = View.GONE
        errorTitle.text = title
        errorDetail.text = detail
        errorPanel.visibility = View.VISIBLE
        errorPanel.bringToFront()
    }

    private fun hideError() {
        errorPanel.visibility = View.GONE
        progressBar.bringToFront()
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Telefon tarayıcısı açılamadı.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleBack() {
        if (errorPanel.visibility == View.VISIBLE) {
            hideError()
            if (webView.url.isNullOrBlank()) webView.loadUrl(START_URL) else webView.reload()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        blankCheckGeneration++
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBack()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
