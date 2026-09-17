package com.elak.okulum.rehber

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date
import kotlin.concurrent.thread

class RehberActivity : AppCompatActivity() {
    private val session by lazy { RehberSession(this) }
    private lateinit var webView: WebView
    private lateinit var status: TextView

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshStatus() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 26, 56)
        buildUi()
        refreshStatus()
        openWeb()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 12, 12, 12)
            setBackgroundColor(Color.rgb(8, 26, 56))
        }
        top.addView(TextView(this).apply {
            text = "Akıllı Rehber"
            textSize = 18f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Button(this).apply { text = "↻"; setOnClickListener { syncNow(true) } })
        top.addView(Button(this).apply { text = "⚙"; setOnClickListener { showSetup() } })
        root.addView(top)

        status = TextView(this).apply {
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setTextColor(Color.DKGRAY)
        }
        root.addView(status)

        webView = WebView(this).apply { setBackgroundColor(Color.WHITE) }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            defaultTextEncodingName = "UTF-8"
            userAgentString = "$userAgentString ELAK-Okulum-Rehber/0.6.0"
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return if (uri.host?.endsWith("mcoaihl.com") == true || uri.host == "dp01.ni.net.tr") false
                else try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true } catch (_: Exception) { false }
            }
        }
    }

    private fun openWeb() {
        val token = session.token
        if (token.isNullOrBlank()) {
            webView.loadUrl("https://elak.mcoaihl.com/rehber/")
            return
        }
        thread {
            val sso = RehberApi.webSso(token)
            runOnUiThread {
                val url = if (!sso.isNullOrBlank()) {
                    "https://elak.mcoaihl.com/rehber/index.php?mobile_sso=${Uri.encode(sso)}"
                } else {
                    "https://elak.mcoaihl.com/rehber/"
                }
                webView.loadUrl(url)
            }
        }
        syncNow(false)
    }

    private fun syncNow(showToast: Boolean) {
        val token = session.token
        if (token.isNullOrBlank()) {
            if (showToast) showSetup()
            return
        }
        status.text = "Rehber senkronize ediliyor…"
        thread {
            try {
                val payload = RehberApi.sync(token)
                val count = CallerCache(this).replaceFromSync(payload)
                session.lastSync = System.currentTimeMillis()
                runOnUiThread {
                    refreshStatus()
                    if (showToast) Toast.makeText(this, "$count arayan kaydı güncellendi.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    refreshStatus()
                    if (showToast) Toast.makeText(this, "Senkronizasyon: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSetup() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 20, 36, 8)
        }
        val user = EditText(this).apply {
            hint = "Akıllı Rehber kullanıcı adı"
            setText(session.username)
        }
        val pass = EditText(this).apply {
            hint = "Akıllı Rehber şifresi"
            inputType = 0x00000081
        }
        wrap.addView(user)
        wrap.addView(pass)
        wrap.addView(Button(this).apply {
            text = "Arayan Kimliği Yetkisi"
            setOnClickListener { requestCallScreeningRole() }
        })
        wrap.addView(Button(this).apply {
            text = "Arayan Kartını Ekran Üstünde Göster"
            setOnClickListener { requestOverlay() }
        })
        if (Build.VERSION.SDK_INT >= 33) {
            wrap.addView(Button(this).apply {
                text = "Bildirim İzni"
                setOnClickListener { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            })
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Akıllı Rehber Etkinleştirme")
            .setView(wrap)
            .setNegativeButton("Kapat", null)
            .setNeutralButton("Oturumu Temizle") { _, _ ->
                session.clear()
                CallerCache(this).writableDatabase.delete("callers", null, null)
                refreshStatus()
            }
            .setPositiveButton("Giriş + Senkronize", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u = user.text.toString().trim()
                val p = pass.text.toString()
                if (u.isBlank() || p.isBlank()) {
                    Toast.makeText(this, "Kullanıcı adı ve şifre gerekli.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                thread {
                    try {
                        val login = RehberApi.login(u, p)
                        session.token = login.token
                        session.username = u
                        val payload = RehberApi.sync(login.token)
                        val count = CallerCache(this).replaceFromSync(payload)
                        session.lastSync = System.currentTimeMillis()
                        runOnUiThread {
                            dialog.dismiss()
                            refreshStatus()
                            openWeb()
                            Toast.makeText(this, "Akıllı Rehber etkin. $count kayıt eşleştirildi.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            Toast.makeText(this, e.message ?: "Giriş başarısız.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            } else {
                Toast.makeText(this, "Arayan kimliği rolü zaten etkin veya kullanılamıyor.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "Ekran üstü arayan kartı etkin.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshStatus() {
        val count = CallerCache(this).count()
        val role = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else false
        val synced = if (session.lastSync > 0) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastSync))
        } else "henüz yok"
        status.text = "Arayan kimliği: ${if (role) "Açık" else "Kapalı"} · Yerel kayıt: $count · Son eşitleme: $synced"
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
