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
    private lateinit var loginButton: Button
    private lateinit var usernameField: EditText
    private lateinit var passwordField: EditText
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))

        if (intent.getBooleanExtra(EXTRA_FORCE_WEB, false)) {
            showWebPortal(intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.startsWith("https://") } ?: BASE_URL)
            return
        }

        val okulum = OkulumSession(this)
        val izin = IzinSession(this)
        if (izin.isReady && okulum.username.isNotBlank()) {
            ensureRehberInBackground(okulum.username, okulum.password)
            openHome()
            return
        }

        val secure = SecureOkulumCredentials(this).load()
        val user = secure?.username?.trim().orEmpty().ifBlank { okulum.username.trim() }
        val pass = secure?.password.orEmpty().ifBlank { okulum.password }
        showNativeLogin(user)
        if (user.isNotBlank() && pass.isNotBlank()) attemptLogin(user, pass, true)
    }

    private fun showNativeLogin(prefill: String) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBg) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(26))
            setBackgroundColor(navy)
            addView(label("ELAK OKULUM", 12f, Color.rgb(185, 210, 240), true))
            addView(label("Tek Hesapla Giriş", 27f, Color.WHITE, true).apply { setPadding(0, dp(5), 0, 0) })
            addView(label("Mahmud Celaleddin Ökten AİHL", 13f, Color.rgb(210, 225, 245), false).apply { setPadding(0, dp(6), 0, 0) })
        }
        root.addView(header)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(28)) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.WHITE, 22)
            elevation = dp(3).toFloat()
            addView(label("ELAK hesabınız", 19f, ink, true))
            addView(label("Bir kez giriş yapın; Okulum, Akıllı Rehber ve İzin Takip aynı oturumu kullanır.", 12.5f, muted, false).apply { setPadding(0, dp(6), 0, dp(16)) })
        }

        usernameField = EditText(this).apply {
            hint = "Kullanıcı adı"; setSingleLine(true); textSize = 15f; setText(prefill); setPadding(dp(14), 0, dp(14), 0); background = fieldBackground()
        }
        passwordField = EditText(this).apply {
            hint = "Şifre"; setSingleLine(true); textSize = 15f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(14), 0, dp(14), 0); background = fieldBackground()
        }
        card.addView(usernameField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        card.addView(passwordField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(11) })

        loginButton = Button(this).apply {
            text = "Giriş Yap"; textSize = 15f; isAllCaps = false; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = rounded(blue, 15)
            setOnClickListener { attemptLogin(usernameField.text.toString().trim(), passwordField.text.toString(), false) }
        }
        card.addView(loginButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(16) })

        progress = ProgressBar(this).apply { visibility = View.GONE }
        card.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)).apply { topMargin = dp(8) })
        statusText = label("", 12f, muted, false).apply { gravity = Gravity.CENTER }
        card.addView(statusText)
        body.addView(card)
        body.addView(label("ELAK Okulum v0.9.2  •  Merkezi oturum", 10.5f, muted, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0) })

        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        WindowInsetsControllerCompat(window, root).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = true }
        ViewCompat.requestApplyInsets(root)
    }

    private fun attemptLogin(user: String, pass: String, automatic: Boolean) {
        if (loggingIn) return
        if (user.isBlank() || pass.isBlank()) { showError("Kullanıcı adı ve şifreyi girin."); return }
        loggingIn = true; loginButton.isEnabled = false; progress.visibility = View.VISIBLE
        statusText.setTextColor(muted); statusText.text = if (automatic) "Oturumunuz yenileniyor…" else "Giriş yapılıyor…"

        thread {
            var error: String? = null
            try {
                val izinLogin = IzinApi.login(user, pass)
                IzinSession(this).save(izinLogin)
                OkulumSession(this).saveCredentials(user, pass)
                SecureOkulumCredentials(this).save(user, pass)

                try {
                    val r = RehberApi.login(user, pass)
                    RehberSession(this).apply { token = r.token; username = user }
                    CallerCache(this).replaceFromSync(RehberApi.sync(r.token))
                    RehberSession(this).lastSync = System.currentTimeMillis()
                } catch (_: Exception) { }
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: "Giriş tamamlanamadı."
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loggingIn = false; progress.visibility = View.GONE; loginButton.isEnabled = true
                if (error == null) openHome()
                else showError((if (automatic) "Otomatik giriş tamamlanamadı. Şifrenizi bir kez girin.\n" else "") + error)
            }
        }
    }

    private fun ensureRehberInBackground(user: String, pass: String) {
        if (!RehberSession(this).token.isNullOrBlank() || user.isBlank() || pass.isBlank()) return
        thread {
            try {
                val r = RehberApi.login(user, pass)
                val s = RehberSession(this); s.token = r.token; s.username = user
                CallerCache(this).replaceFromSync(RehberApi.sync(r.token)); s.lastSync = System.currentTimeMillis()
            } catch (_: Exception) { }
        }
    }

    private fun showError(message: String) {
        statusText.setTextColor(red); statusText.text = message; passwordField.requestFocus()
    }

    private fun openHome() { startActivity(Intent(this, ElakHomeActivity::class.java)); finish() }

    private fun showWebPortal(url: String) {
        WindowCompat.setDecorFitsSystemWindows(window, false); window.statusBarColor = navy; window.navigationBarColor = Color.WHITE
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        val web = WebView(this)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        frame.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP)); setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, i -> val b=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()); v.setPadding(b.left,b.top,b.right,b.bottom); i }
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        with(web.settings) { javaScriptEnabled=true; domStorageEnabled=true; databaseEnabled=true; allowFileAccess=false; allowContentAccess=true; cacheMode=WebSettings.LOAD_DEFAULT; userAgentString="$userAgentString ELAK-Okulum/0.9.2 tr-TR" }
        web.webChromeClient = object: android.webkit.WebChromeClient(){ override fun onProgressChanged(v:WebView?, p:Int){ bar.progress=p; bar.visibility=if(p in 1..99) View.VISIBLE else View.GONE } }
        web.webViewClient = object: WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView?, request:WebResourceRequest?):Boolean{
                val u=request?.url?:return false; val scheme=u.scheme?.lowercase().orEmpty(); val host=u.host?.lowercase().orEmpty(); val path=u.path.orEmpty()
                if((scheme=="http"||scheme=="https")&&host=="elak.mcoaihl.com"&&path.startsWith("/rehber")){ startActivity(Intent(this@MainActivity,RehberActivity81::class.java)); return true }
                if((scheme=="http"||scheme=="https")&&host in setOf("elak.mcoaihl.com","mcoaihl.com","www.mcoaihl.com")&&path.startsWith("/izin")){ startActivity(Intent(this@MainActivity,IzinActivity88::class.java).putExtra(IzinActivity88.EXTRA_URL,u.toString())); return true }
                if((scheme=="http"||scheme=="https")&&host in INTERNAL_HOSTS) return false
                return try{ startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.toString()))); true }catch(_:Exception){ false }
            }
        }
        web.loadUrl(url, mapOf("Accept-Language" to "tr-TR,tr;q=0.9,en;q=0.6"))
    }

    private fun label(text:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{this.text=text;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD)}
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;cornerRadius=dp(radius).toFloat();setColor(color)}
    private fun fieldBackground()=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;cornerRadius=dp(13).toFloat();setColor(Color.rgb(248,250,252));setStroke(dp(1),Color.rgb(215,224,235))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density+.5f).toInt()
}
