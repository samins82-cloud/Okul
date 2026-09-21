package com.elak.okulum

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.auth.CentralApi
import com.elak.okulum.auth.SecureOkulumCredentials
import com.elak.okulum.izin.IzinApi
import com.elak.okulum.izin.IzinSession
import com.elak.okulum.rehber.RehberApi
import com.elak.okulum.rehber.RehberSession
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {
    private val navy = Color.rgb(8, 31, 67)
    private val blue = Color.rgb(37, 99, 235)
    private val bg = Color.rgb(245, 248, 252)
    private val muted = Color.rgb(94, 111, 136)
    private val red = Color.rgb(202, 63, 75)

    private lateinit var root: LinearLayout
    private lateinit var schoolCode: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var errorText: TextView
    private lateinit var loginButton: Button
    private lateinit var offlineButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var session: OkulumSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE
        session = OkulumSession(this)
        buildUi()
        applyInsets()

        schoolCode.setText(session.schoolCode.ifBlank { "MCOAIHL" })
        if (session.hasCredentials) {
            username.setText(session.username)
            password.setText(session.password)
            root.post { resumeOrAuthenticate() }
        }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(28))
        }

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(24), dp(18), dp(24))
            background = round(navy, 22)
            addView(TextView(this@LoginActivity).apply {
                text = "ELAK"; textSize = 30f; setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            })
            addView(TextView(this@LoginActivity).apply {
                text = "Okulum"; textSize = 20f; setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            })
            addView(TextView(this@LoginActivity).apply {
                text = "Tek uygulama · farklı okullar · ayrı yetkiler"
                textSize = 12.5f; setTextColor(Color.rgb(211, 224, 242)); gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        })

        page.addView(space(24))
        page.addView(TextView(this).apply {
            text = "Okul hesabınıza giriş yapın"
            textSize = 22f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD)
        })
        page.addView(TextView(this).apply {
            text = "Okul kodunuz hesabın hangi okula ait olduğunu belirler. Rolünüz ve modül yetkileriniz otomatik uygulanır."
            textSize = 13f; setTextColor(muted); setPadding(0, dp(5), 0, dp(18))
        })

        schoolCode = field("Okul kodu (örn. MCOAIHL)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        username = field("Kullanıcı adı")
        password = field("Şifre").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        errorText = TextView(this).apply {
            visibility = View.GONE; textSize = 12.5f; setTextColor(red)
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = round(Color.rgb(255, 242, 244), 11)
        }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        loginButton = Button(this).apply {
            text = "Giriş Yap"; isAllCaps = false; textSize = 15f; setTextColor(Color.WHITE)
            background = round(blue, 12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
            setOnClickListener {
                val code = normalizedSchoolCode()
                val u = username.text.toString().trim()
                val p = password.text.toString()
                if (code.isBlank()) showError("Okul kodunu girin.")
                else if (u.isBlank() || p.isBlank()) showError("Kullanıcı adı ve şifreyi girin.")
                else authenticate(code, u, p, false)
            }
        }
        offlineButton = Button(this).apply {
            text = "Kayıtlı Hesapla Çevrimdışı Devam Et"; isAllCaps = false
            visibility = if (session.hasIdentity) View.VISIBLE else View.GONE
            setTextColor(navy); background = round(Color.WHITE, 12, Color.rgb(207, 217, 232))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            setOnClickListener {
                val typed = normalizedSchoolCode()
                val saved = session.schoolCode.trim().uppercase()
                if (saved.isNotBlank() && typed != saved) {
                    showError("Çevrimdışı hesap $saved okuluna aittir. Okul kodunu $saved yapın veya çevrimiçi giriş yapın.")
                } else openHome()
            }
        }

        page.addView(schoolCode); page.addView(space(10)); page.addView(username); page.addView(space(10));
        page.addView(password); page.addView(space(12)); page.addView(errorText); page.addView(space(12));
        page.addView(loginButton); page.addView(space(10)); page.addView(offlineButton)
        page.addView(space(18)); page.addView(progress, LinearLayout.LayoutParams(dp(38), dp(38)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        page.addView(TextView(this).apply {
            text = "ELAK Okulum 0.9.2 · Çok okullu merkezi kullanıcı sistemi"
            textSize = 11f; gravity = Gravity.CENTER; setTextColor(muted); setPadding(0, dp(22), 0, 0)
        })

        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun applyInsets() {
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, i ->
            val b = i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom); i
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun resumeOrAuthenticate() {
        setBusy(true)
        loginButton.text = "Oturum Doğrulanıyor…"
        thread {
            if (session.hasCentralSession) {
                try {
                    val profile = CentralApi.me(session.accessToken)
                    if (session.schoolCode.isBlank() || profile.schoolCode.equals(session.schoolCode, ignoreCase = true)) {
                        session.updateCentralProfile(profile)
                        runOnUiThread {
                            schoolCode.setText(profile.schoolCode)
                            setBusy(false); openHome()
                        }
                        return@thread
                    }
                } catch (_: Exception) {
                    try {
                        val refreshed = CentralApi.refresh(session.refreshToken)
                        session.saveRefreshedTokens(refreshed)
                        val profile = CentralApi.me(refreshed.accessToken)
                        session.updateCentralProfile(profile)
                        runOnUiThread {
                            schoolCode.setText(profile.schoolCode)
                            setBusy(false); openHome()
                        }
                        return@thread
                    } catch (_: Exception) {
                        session.clearCentralOnly()
                    }
                }
            }
            runOnUiThread { loginButton.text = "Giriş Yap" }
            authenticate(session.schoolCode.ifBlank { "MCOAIHL" }, session.username, session.password, true)
        }
    }

    private fun authenticate(code: String, user: String, pass: String, automatic: Boolean) {
        setBusy(true)
        errorText.visibility = View.GONE
        if (automatic) runOnUiThread { loginButton.text = "Oturum Doğrulanıyor…" }

        thread {
            var central: CentralApi.AuthResult? = null
            var centralError: String? = null
            try { central = CentralApi.login(user, pass, code) } catch (e: Exception) { centralError = e.message }

            var rehber: RehberApi.LoginResult? = null
            var izin: IzinApi.LoginResult? = null
            var rehberError: String? = null
            var izinError: String? = null

            // Eski MCOAIHL modüllerine yalnız MCOAIHL okul kodunda geri dönülür.
            val legacyMco = code.equals("MCOAIHL", ignoreCase = true)
            if (legacyMco) {
                try { rehber = RehberApi.login(user, pass) } catch (e: Exception) { rehberError = e.message }
                try { izin = IzinApi.login(user, pass) } catch (e: Exception) { izinError = e.message }
            }

            if (central == null && rehber == null && izin == null) {
                val message = centralError?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(rehberError, izinError).firstOrNull { it.isNotBlank() }
                    ?: "ELAK hesabı doğrulanamadı."
                runOnUiThread {
                    setBusy(false); loginButton.text = "Giriş Yap"
                    offlineButton.visibility = if (session.hasIdentity) View.VISIBLE else View.GONE
                    showError(message)
                }
                return@thread
            }

            if (rehber != null) RehberSession(this).apply { token = rehber!!.token; username = user }
            if (izin != null) IzinSession(this).save(izin!!)

            if (central != null) {
                session.saveCentral(user, pass, central!!)
            } else {
                val displayName = rehber?.userName?.takeIf { it.isNotBlank() }
                    ?: izin?.fullName?.takeIf { it.isNotBlank() } ?: user
                val role = rehber?.role?.takeIf { it.isNotBlank() }
                    ?: izin?.role?.takeIf { it.isNotBlank() } ?: "user"
                val scope = rehber?.schoolScope?.takeIf { it.isNotBlank() }
                    ?: izin?.schoolScope?.takeIf { it.isNotBlank() } ?: "both"
                session.saveIdentity(
                    username = user,
                    password = pass,
                    displayName = displayName,
                    role = role,
                    schoolScope = scope,
                    schoolCode = code,
                    schoolName = "Mahmud Celaleddin Ökten Anadolu İmam Hatip Lisesi"
                )
            }

            SecureOkulumCredentials(this).save(user, pass)
            runOnUiThread {
                schoolCode.setText(session.schoolCode.ifBlank { code })
                setBusy(false); openHome()
            }
        }
    }

    private fun normalizedSchoolCode(): String = schoolCode.text.toString().trim().uppercase()

    private fun openHome() {
        startActivity(Intent(this, ElakHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun showError(message: String) {
        errorText.text = message; errorText.visibility = View.VISIBLE
    }

    private fun setBusy(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
        loginButton.isEnabled = !value; schoolCode.isEnabled = !value; username.isEnabled = !value; password.isEnabled = !value
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText; textSize = 15f; setTextColor(navy); setHintTextColor(Color.rgb(128, 143, 164))
        setPadding(dp(14), dp(12), dp(14), dp(12)); background = round(Color.WHITE, 12, Color.rgb(210, 220, 233))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
    }

    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun round(fill: Int, radius: Int, stroke: Int? = null) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
}
