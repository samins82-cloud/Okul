package com.elak.okulum

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.auth.SecureOkulumCredentials
import com.elak.okulum.izin.IzinActivity88
import com.elak.okulum.izin.IzinApi
import com.elak.okulum.izin.IzinSession
import com.elak.okulum.rehber.CallerCache
import com.elak.okulum.rehber.RehberActivity81
import com.elak.okulum.rehber.RehberApi
import com.elak.okulum.rehber.RehberSession
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    companion object {
        private const val BASE_URL = "https://elak.mcoaihl.com/okulum/"
        const val EXTRA_FORCE_WEB = "elak_force_web"
        const val EXTRA_START_URL = "elak_start_url"
        private val INTERNAL_HOSTS = setOf("elak.mcoaihl.com", "mcoaihl.com", "www.mcoaihl.com", "dp01.ni.net.tr")
    }

    private val navy = Color.rgb(8, 31, 68)
    private val blue = Color.rgb(37, 99, 235)
    private val pageBg = Color.rgb(244, 247, 252)
    private val ink = Color.rgb(15, 34, 62)
    private val muted = Color.rgb(100, 116, 139)
    private val red = Color.rgb(220, 38, 38)

    private var loggingIn = false
    private var forceWebMode = false
    private var requestedStartUrl = BASE_URL

    private lateinit var loginButton: Button
    private lateinit var usernameField: EditText
    private lateinit var passwordField: EditText
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        forceWebMode = intent.getBooleanExtra(EXTRA_FORCE_WEB, false)
        requestedStartUrl = intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.startsWith("https://") } ?: BASE_URL

        if (forceWebMode) {
            showWebPortal()
            return
        }

        val izinSession = IzinSession(this)
        val okulum = OkulumSession(this)
        if (izinSession.isReady && okulum.username.isNotBlank()) {
            ensureRehberInBackground(okulum.username, okulum.password)
            openHome()
            return
        }

        val saved = SecureOkulumCredentials(this).load()
        val username = saved?.username?.trim().orEmpty().ifBlank { okulum.username.trim() }
        val password = saved?.password.orEmpty().ifBlank { okulum.password }
        showNativeLogin(username)

        if (username.isNotBlank() && password.isNotBlank()) {
            statusText.text = "Oturumunuz yenileniyor…"
            attemptLogin(username, password, true)
        }
    }

    private fun showNativeLogin(prefillUsername: String = "") {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }
        ViewCompat.setOnApplyWindowInsetsListener(outer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(26))
            setBackgroundColor(navy)
        }
        header.addView(TextView(this).apply {
            text = "ELAK OKULUM"
            textSize = 12f
            setTextColor(Color.rgb(185, 210, 240))
            setTypeface(typeface, Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Tek Hesapla Giriş"
            textSize = 27f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, 0)
        })
        header.addView(TextView(this).apply {
            text = "Mahmud Celaleddin Ökten AİHL"
            textSize = 13f
            setTextColor(Color.rgb(210, 225, 245))
            setPadding(0, dp(6), 0, 0)
        })
        outer.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.WHITE, 22)
            elevation = dp(3).toFloat()
        }
        card.addView(TextView(this).apply {
            text = "ELAK hesabınız"
            textSize = 19f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Bir kez giriş yapın; Okulum, Akıllı Rehber ve İzin Takip aynı oturumu kullanır."
            textSize = 12.5f
            setTextColor(muted)
            setPadding(0, dp(6), 0, dp(16))
        })

        usernameField = EditText(this).apply {
            hint = "Kullanıcı adı"
            setSingleLine(true)
            textSize = 15f
            setText(prefillUsername)
            setPadding(dp(14), 0, dp(14), 0)
            background = fieldBackground()
        }
        passwordField = EditText(this).apply {
            hint = "Şifre"
            setSingleLine(true)
            textSize = 15f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(14), 0, dp(14), 0)
            background = fieldBackground()
        }
        card.addView(usernameField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        card.addView(passwordField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(11) })

        loginButton = Button(this).apply {
            text = "Giriş Yap"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(blue, 15)
            setOnClickListener {
                attemptLogin(usernameField.text.toString().trim(), passwordField.text.toString(), false)
            }
        }
        card.addView(loginButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(16) })

        progress = ProgressBar(this).apply { visibility = View.GONE }
        card.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)).apply { topMargin = dp(8) })

        statusText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        card.addView(statusText)

        body.addView(card)
        body.addView(TextView(this).apply {
            text = "ELAK Okulum v0.9.2  •  Merkezi oturum"
            textSize = 10.5f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        })
        scroll.addView(body)
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(outer)
        WindowInsetsControllerCompat(window, outer).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.requestApplyInsets(outer)
    }

    private fun attemptLogin(username: String, password: String, automatic: Boolean) {
        if (loggingIn) return
        if (username.isBlank() || password.isBlank()) {
            statusText.setTextColor(red)
            statusText.text = "Kullanıcı adı ve şifreyi girin."
            return
        }
        loggingIn = true
        loginButton.isEnabled = false
        progress.visibility = View.VISIBLE
        statusText.setTextColor(muted)
        statusText.text = if (automatic) "Oturumunuz yenileniyor…" else "Giriş yapılıyor…"

        thread {
            var error: String? = null
            try {
                val izinLogin = IzinApi.login(username, password)
                IzinSession(this).save(izinLogin)
                OkulumSession(this).saveCredentials(username, password)
                SecureOkulumCredentials(this).save(username, password)

                try {
                    val rehberLogin = RehberApi.login(username, password)
                    val rehberSession = RehberSession(this)
                    rehberSession.token = rehberLogin.token
                    rehberSession.username = username
                    CallerCache(this).replaceFromSync(RehberApi.sync(rehberLogin.token))
                    rehberSession.lastSync = System.currentTimeMillis()
                } catch (_: Exception) {
                    // Ana ELAK girişi başarılıysa kullanıcı içeri alınır.
                    // Rehber, açıldığında kayıtlı merkezi hesapla sessizce tekrar bağlanır.
                }
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Giriş tamamlanamadı."
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loggingIn = false
                progress.visibility = View.GONE
                loginButton.isEnabled = true
                if (error == null) {
                    openHome()
                } else {
                    statusText.setTextColor(red)
                    statusText.text = if (automatic) "Otomatik giriş tamamlanamadı. Şifrenizi bir kez girin.\n$error" else error
                    passwordField.requestFocus()
                }
            }
        }
    }

    private fun ensureRehberInBackground(username: String, password: String) {
        if (RehberSession(this).token.isNotBlank() || username.isBlank() || password.isBlank()) return
        thread {
            try {
                val login = RehberApi.login(username, password)
                val session = RehberSession(this)
                session.token = login.token
                session.username = username
                CallerCache(this).replaceFromSync(RehberApi.sync(login.token))
                session.lastSync = System.currentTimeMillis()
            } catch (_: Exception) { }
        }
    }

    private fun openHome() {
        startActivity(Intent(this, ElakHomeActivity::class.java))
        finish()
    }

    private fun showWebPortal() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE

        val frame = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val web = WebView(this).apply { setBackgroundColor(Color.WHITE) }
        val loading = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        frame.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(loading, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP))
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        WindowInsetsControllerCompat(window, frame).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            loadsImagesAutomatically = true
            defaultTextEncodingName = "UTF-8"
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString ELAK-Okulum/0.9.2 tr-TR"
        }
        web.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loading.progress = newProgress
                loading.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase().orEmpty()
                val host = uri.host?.lowercase().orEmpty()
                val path = uri.path.orEmpty()
                if ((scheme == "http" || scheme == "https") && host == "elak.mcoaihl.com" && path.startsWith("/rehber")) {
                    startActivity(Intent(this@MainActivity, RehberActivity81::class.java))
                    return true
                }
                if ((scheme == "http" || scheme == "https") && host in setOf("elak.mcoaihl.com", "mcoaihl.com", "www.mcoaihl.com") && path.startsWith("/izin")) {
                    startActivity(Intent(this@MainActivity, IzinActivity88::class.java).putExtra(IzinActivity88.EXTRA_URL, uri.toString()))
                    return true
                }
                if ((scheme == "http" || scheme == "https") && host in INTERNAL_HOSTS) return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString())))
                    true
                } catch (_: Exception) { false }
            }
        }
        web.loadUrl(requestedStartUrl, mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.6"))
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    private fun fieldBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(13).toFloat()
        setColor(Color.rgb(248, 250, 252))
        setStroke(dp(1), Color.rgb(215, 224, 235))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()
}
