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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        private const val CORE_BASE = "https://elak.mcoaihl.com/"
        const val EXTRA_FORCE_WEB = "elak_force_web"
        const val EXTRA_START_URL = "elak_start_url"
    }

    private val navy = Color.rgb(8, 31, 68)
    private val blue = Color.rgb(37, 99, 235)
    private val pageBg = Color.rgb(244, 247, 252)
    private val ink = Color.rgb(15, 34, 62)
    private val muted = Color.rgb(100, 116, 139)
    private val red = Color.rgb(220, 38, 38)

    private var busy = false
    private lateinit var orgField: EditText
    private lateinit var userField: EditText
    private lateinit var passField: EditText
    private lateinit var loginButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))

        if (intent.getBooleanExtra(EXTRA_FORCE_WEB, false)) {
            val url = intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.startsWith("https://") }
                ?: CORE_BASE
            showWebPortal(url)
            return
        }

        val session = OkulumSession(this)
        showLogin(session.orgCode, session.username)

        if (session.orgCode.isNotBlank() && session.username.isNotBlank()) {
            status.text = "Merkezi oturum kontrol ediliyor…"
            restoreOrLogin(session)
        }
    }

    private fun restoreOrLogin(session: OkulumSession) {
        if (busy) return
        busy = true
        setBusy(true)
        thread {
            var state: OkulumCoreApi.CoreState? = null
            var error: String? = null
            try {
                if (session.coreCookie.isNotBlank()) {
                    state = OkulumCoreApi.resume(session.coreCookie, session.orgCode, session.username)
                }
            } catch (_: Exception) { }

            if (state == null && session.password.isNotBlank()) {
                try {
                    state = OkulumCoreApi.login(session.orgCode, session.username, session.password)
                } catch (e: Exception) {
                    error = e.message
                }
            }

            state?.let { session.saveCore(it) }
            runOnUiThread {
                busy = false
                setBusy(false)
                if (state != null) {
                    bindNativeModulesAsync(session)
                    openHome()
                } else {
                    status.setTextColor(if (error.isNullOrBlank()) muted else red)
                    status.text = if (error.isNullOrBlank()) {
                        "Kurum kodu, kullanıcı kodu ve şifrenizle giriş yapın."
                    } else {
                        "Otomatik oturum yenilenemedi. Şifrenizi girerek devam edin."
                    }
                    passField.requestFocus()
                }
            }
        }
    }

    private fun login() {
        if (busy) return
        val code = orgField.text.toString().trim().uppercase()
        val user = userField.text.toString().trim()
        val pass = passField.text.toString()
        when {
            code.isBlank() -> { showError("Kurum kodunu girin."); orgField.requestFocus(); return }
            user.isBlank() -> { showError("Kullanıcı kodunu girin."); userField.requestFocus(); return }
            pass.isBlank() -> { showError("Şifreyi girin."); passField.requestFocus(); return }
        }

        busy = true
        setBusy(true)
        status.setTextColor(muted)
        status.text = "ELAK CORE ile giriş yapılıyor…"
        thread {
            var state: OkulumCoreApi.CoreState? = null
            var error: String? = null
            try {
                state = OkulumCoreApi.login(code, user, pass)
                OkulumSession(this).saveCore(state!!, pass)
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Giriş tamamlanamadı."
            }
            runOnUiThread {
                busy = false
                setBusy(false)
                if (state != null) {
                    bindNativeModulesAsync(OkulumSession(this))
                    openHome()
                } else showError(error ?: "Giriş tamamlanamadı.")
            }
        }
    }

    /**
     * Kullanıcı modüllerde tekrar parola girmez. CORE girişinde doğrulanmış ve cihazda
     * şifreli saklanan kişisel hesap, yalnız kurum lisansında açık olan native modülleri
     * arka planda hazırlar. Modül bağlantısı başarısız olsa bile CORE oturumu bozulmaz.
     */
    private fun bindNativeModulesAsync(core: OkulumSession) {
        val user = core.username
        val pass = core.password
        if (user.isBlank() || pass.isBlank()) return
        thread {
            if (core.moduleEnabled("akilli_rehber")) {
                try {
                    val login = RehberApi.login(user, pass)
                    val rs = RehberSession(this)
                    rs.token = login.token
                    rs.username = user
                    CallerCache(this).replaceFromSync(RehberApi.sync(login.token))
                    rs.lastSync = System.currentTimeMillis()
                } catch (_: Exception) { }
            }

            if (core.moduleEnabled("izin_takip")) {
                try {
                    val login = IzinApi.login(user, pass)
                    IzinSession(this).save(login)
                } catch (_: Exception) { }
            }
        }
    }

    private fun showLogin(prefillCode: String, prefillUser: String) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(25))
            setBackgroundColor(navy)
            addView(txt("ELAK OKULUM", 12f, Color.rgb(184, 209, 239), true))
            addView(txt("Kurum Girişi", 27f, Color.WHITE, true).apply { setPadding(0, dp(5), 0, 0) })
            addView(txt("Tek hesap • tek oturum • lisansa göre modüller", 12.5f, Color.rgb(210, 225, 245), false).apply { setPadding(0, dp(6), 0, 0) })
        }
        root.addView(head)

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(23), dp(20), dp(28)) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(19), dp(19), dp(19), dp(19))
            background = rounded(Color.WHITE, 21)
            elevation = dp(3).toFloat()
        }
        card.addView(txt("ELAK CORE hesabınız", 19f, ink, true))
        card.addView(txt("Kurum kodunuz hesabın hangi okula ait olduğunu belirler. Kullanıcı kodu ve şifreniz yalnız bir kez girilir.", 12.5f, muted, false).apply { setPadding(0, dp(6), 0, dp(16)) })

        orgField = field("Kurum Kodu", prefillCode).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        userField = field("Kullanıcı Kodu", prefillUser)
        passField = field("Şifre", "").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setOnEditorActionListener { _, _, _ -> login(); true }
        }
        card.addView(orgField, lp54())
        card.addView(userField, lp54(dp(10)))
        card.addView(passField, lp54(dp(10)))

        loginButton = Button(this).apply {
            text = "ELAK CORE ile Giriş Yap"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(blue, 14)
            setOnClickListener { login() }
        }
        card.addView(loginButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(16) })

        progress = ProgressBar(this).apply { visibility = View.GONE }
        card.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)).apply { topMargin = dp(7) })
        status = txt("", 12f, muted, false).apply { gravity = Gravity.CENTER }
        card.addView(status)

        body.addView(card)
        body.addView(txt("ELAK Okulum v0.9.2  •  CORE merkezi oturum", 10.5f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0) })
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun openHome() {
        startActivity(Intent(this, ElakHomeActivity::class.java))
        finish()
    }

    private fun showWebPortal(url: String) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE

        val frame = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val web = WebView(this)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        frame.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP))
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, i ->
            val b = i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom); i
        }

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)
        val core = OkulumSession(this)
        if (core.coreCookie.isNotBlank()) {
            cm.setCookie(CORE_BASE, core.coreCookie + "; Path=/; Secure; SameSite=Lax")
            cm.flush()
        }
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString ELAK-Okulum/0.9.2 tr-TR"
        }
        web.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(v: WebView?, p: Int) {
                bar.progress = p; bar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                if (u.host?.equals("elak.mcoaihl.com", true) == true && u.path.orEmpty().startsWith("/rehber")) {
                    startActivity(Intent(this@MainActivity, RehberActivity81::class.java)); return true
                }
                if (u.scheme == "http" || u.scheme == "https") return false
                return try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.toString()))); true } catch (_: Exception) { false }
            }
        }
        web.loadUrl(url, mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.6"))
    }

    private fun setBusy(value: Boolean) {
        loginButton.isEnabled = !value
        progress.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        status.setTextColor(red)
        status.text = message
    }

    private fun field(hintText: String, value: String): EditText = EditText(this).apply {
        hint = hintText; setSingleLine(true); textSize = 15f; setText(value)
        setPadding(dp(14), 0, dp(14), 0); background = fieldBg()
    }

    private fun lp54(top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = top }
    private fun txt(t: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(color)
    }
    private fun fieldBg() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(13).toFloat(); setColor(Color.rgb(248, 250, 252)); setStroke(dp(1), Color.rgb(215, 224, 235))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
}
