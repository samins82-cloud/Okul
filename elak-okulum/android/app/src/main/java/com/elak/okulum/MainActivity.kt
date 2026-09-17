package com.elak.okulum

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val BASE_URL = "https://elak.mcoaihl.com/okulum/"
        private val INTERNAL_HOSTS = setOf(
            "elak.mcoaihl.com",
            "mcoaihl.com",
            "www.mcoaihl.com",
            "dp01.ni.net.tr"
        )
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Türkçe yerel ayarı: WebView/PHP tarih-sayı gösterimlerinde tutarlılık sağlar.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))

        // Android 15+ edge-to-edge davranışında içeriğin saat, kamera deliği ve
        // alt gezinme çubuğunun altında kalmaması için sistem boşluklarını biz yönetiyoruz.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.rgb(8, 26, 56)
        window.navigationBarColor = Color.WHITE

        webView = WebView(this)
        webView.setBackgroundColor(Color.WHITE)
        setContentView(webView)

        val insetsController = WindowInsetsControllerCompat(window, webView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = true

        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(webView)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            loadsImagesAutomatically = true
            defaultTextEncodingName = "UTF-8"
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString ELAK-Okulum/0.4.1 tr-TR"
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase().orEmpty()
                val host = uri.host?.lowercase().orEmpty()

                if ((scheme == "http" || scheme == "https") && host in INTERNAL_HOSTS) return false

                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        webView.loadUrl(
            BASE_URL,
            mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.6")
        )
    }

    override fun onResume() {
        super.onResume()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
