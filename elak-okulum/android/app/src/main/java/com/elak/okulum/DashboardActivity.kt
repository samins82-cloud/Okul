package com.elak.okulum

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.izin.IzinActivity88
import com.elak.okulum.izin.IzinApi
import com.elak.okulum.izin.IzinSession
import com.elak.okulum.rehber.RehberActivity81
import kotlin.concurrent.thread

class DashboardActivity : AppCompatActivity() {
    private val navy = Color.rgb(8, 31, 67)
    private val bg = Color.rgb(245, 248, 252)
    private val ink = Color.rgb(24, 44, 75)
    private val muted = Color.rgb(101, 118, 141)
    private val blue = Color.rgb(37, 99, 235)
    private val green = Color.rgb(18, 158, 106)
    private val orange = Color.rgb(234, 143, 31)
    private val purple = Color.rgb(111, 78, 184)

    private lateinit var root: LinearLayout
    private lateinit var host: FrameLayout
    private lateinit var outside: TextView
    private lateinit var pending: TextView
    private lateinit var today: TextView
    private lateinit var returned: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        host = FrameLayout(this).apply { setBackgroundColor(bg) }
        root.addView(host, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
        setContentView(root)
        WindowInsetsControllerCompat(window, root).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = true }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, i ->
            val b = i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom); i
        }
        renderHome()
    }

    override fun onResume() { super.onResume(); if (::outside.isInitialized) refreshSummary() }

    private fun renderHome() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(24)) }
        page.addView(header())
        page.addView(space(16))
        page.addView(section("Bugün", "Okulun anlık durumuna hızlı bakış"))
        page.addView(statsGrid())
        page.addView(space(18))
        page.addView(section("Uygulamalar", "Okul modüllerine tek ekrandan erişin"))
        page.addView(moduleGrid())
        page.addView(space(14))
        page.addView(infoCard())
        val scroll = ScrollView(this).apply { addView(page) }
        host.removeAllViews(); host.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        refreshSummary()
    }

    private fun header(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = round(navy, 20)
        elevation = dp(3).toFloat()
        val row = LinearLayout(this@DashboardActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this@DashboardActivity).apply {
            text = "ELAK"; textSize = 12f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            background = round(Color.rgb(36, 91, 169), 12); setPadding(dp(11), dp(7), dp(11), dp(7))
        })
        row.addView(LinearLayout(this@DashboardActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@DashboardActivity).apply { text = "ELAK Okulum"; textSize = 21f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
            addView(TextView(this@DashboardActivity).apply { text = "Mahmud Celaleddin Ökten AİHL"; textSize = 12f; setTextColor(Color.rgb(207, 220, 239)) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this@DashboardActivity).apply { text = "●"; textSize = 18f; setTextColor(Color.rgb(79, 218, 157)) })
        addView(row)
        val u = OkulumSession(this@DashboardActivity).username.ifBlank { "ELAK kullanıcısı" }
        addView(TextView(this@DashboardActivity).apply { text = "Hoş geldiniz, $u"; textSize = 13f; setTextColor(Color.rgb(221, 231, 246)); setPadding(0, dp(12), 0, 0) })
    }

    private fun statsGrid(): View {
        val g = GridLayout(this).apply { columnCount = 2 }
        outside = value("--", orange); pending = value("--", purple); today = value("--", blue); returned = value("--", green)
        addStat(g, "Dışarıda", outside, "öğrenci", orange)
        addStat(g, "Çıkış Bekleyen", pending, "izin", purple)
        addStat(g, "Bugünkü İzin", today, "kayıt", blue)
        addStat(g, "Dönen", returned, "bugün", green)
        return g
    }

    private fun addStat(g: GridLayout, label: String, v: TextView, suffix: String, accent: Int) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = round(Color.WHITE, 16); elevation = dp(1).toFloat()
            setOnClickListener { openIzin() }
            addView(TextView(this@DashboardActivity).apply { text = label; textSize = 12f; setTextColor(muted) })
            addView(v)
            addView(TextView(this@DashboardActivity).apply { text = suffix; textSize = 10.5f; setTextColor(accent); setTypeface(typeface, Typeface.BOLD) })
        }
        g.addView(c, GridLayout.LayoutParams().apply { width = 0; height = -2; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4)) })
    }

    private fun value(s: String, color: Int) = TextView(this).apply { text = s; textSize = 27f; setTextColor(color); setTypeface(typeface, Typeface.BOLD) }

    private fun moduleGrid(): View {
        val g = GridLayout(this).apply { columnCount = 2 }
        addModule(g, "Akıllı Rehber", "Öğrenci ve veli iletişimi", "☎", blue) { startActivity(Intent(this, RehberActivity81::class.java)) }
        addModule(g, "İzin Takip", "Çıkış, dönüş ve güvenlik", "✓", orange) { openIzin() }
        addModule(g, "DYK / Etkinlik", "Yoklama ve devam takibi", "≡", green) { web("DYK / Sosyal Etkinlik", "https://www.mcoaihl.com/dyk_soset/index.php") }
        addModule(g, "Deneme Sonuçları", "LGS / YKS sonuç ve analiz", "★", purple) { web("Deneme Sonuçları", "https://elak.mcoaihl.com/deneme-sonuc/index.php") }
        addModule(g, "Kabul Sınavı", "Aday, sonuç ve salon işlemleri", "⌁", Color.rgb(21,145,177)) { web("Kabul Sınavı", "https://www.mcoaihl.com/kabulsinavsonuc/") }
        addModule(g, "Yeni Kayıt", "Öğrenci / veli bilgi formları", "+", Color.rgb(36,150,112)) { web("Yeni Kayıt", "https://mcoaihl.com/yenikayit/") }
        addModule(g, "Okulum Web", "Mevcut okul portalı", "↗", Color.rgb(75,91,115)) { web("ELAK Okulum", "https://elak.mcoaihl.com/okulum/") }
        addModule(g, "E-Yönetim", "Tüm dijital okul sistemleri", "▦", Color.rgb(202,63,75)) { web("E-Yönetim", "https://www.mcoaihl.com/") }
        return g
    }

    private fun addModule(g: GridLayout, title: String, sub: String, icon: String, accent: Int, action: () -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(12)); background = round(Color.WHITE, 17); elevation = dp(1).toFloat(); setOnClickListener { action() }
            addView(TextView(this@DashboardActivity).apply { text = icon; textSize = 21f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = round(accent, 13); layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)) })
            addView(TextView(this@DashboardActivity).apply { text = title; textSize = 14f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(10), 0, 0) })
            addView(TextView(this@DashboardActivity).apply { text = sub; textSize = 11.5f; setTextColor(muted); setPadding(0, dp(3), 0, 0); maxLines = 2 })
        }
        g.addView(c, GridLayout.LayoutParams().apply { width = 0; height = dp(140); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4)) })
    }

    private fun bottomBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE); elevation = dp(10).toFloat(); setPadding(dp(4), dp(5), dp(4), dp(5))
        bottom(this, "⌂", "Ana", blue) { renderHome() }
        bottom(this, "●", "Bildirim", muted) { upcoming("Bildirim Merkezi") }
        bottom(this, "✉", "Mesajlar", muted) { upcoming("Mesajlaşma") }
        bottom(this, "□", "Takvim", muted) { upcoming("Okul Takvimi") }
        bottom(this, "◉", "Profil", muted) { profile() }
    }

    private fun bottom(bar: LinearLayout, icon: String, label: String, color: Int, action: () -> Unit) {
        bar.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setOnClickListener { action() }
            addView(TextView(this@DashboardActivity).apply { text = icon; textSize = 16f; setTextColor(color); gravity = Gravity.CENTER })
            addView(TextView(this@DashboardActivity).apply { text = label; textSize = 10f; setTextColor(color); gravity = Gravity.CENTER })
        }, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun refreshSummary() {
        val s = IzinSession(this)
        if (!s.isReady) { outside.text = "--"; pending.text = "--"; today.text = "--"; returned.text = "--"; return }
        thread {
            try {
                val st = IzinApi.dashboard(s).optJSONObject("stats")
                runOnUiThread {
                    outside.text = st?.optInt("outside", 0).toString(); pending.text = st?.optInt("pending", 0).toString()
                    today.text = st?.optInt("today", 0).toString(); returned.text = st?.optInt("returned_today", 0).toString()
                }
            } catch (_: Exception) { }
        }
    }

    private fun web(title: String, url: String) = startActivity(Intent(this, WebModuleActivity::class.java).putExtra(WebModuleActivity.EXTRA_TITLE, title).putExtra(WebModuleActivity.EXTRA_URL, url))
    private fun openIzin() = startActivity(Intent(this, IzinActivity88::class.java))
    private fun upcoming(name: String) = Toast.makeText(this, "$name sonraki 0.9 adımında native olarak bağlanacak.", Toast.LENGTH_SHORT).show()
    private fun profile() {
        val u = OkulumSession(this).username.ifBlank { "Kayıtlı kullanıcı yok" }
        AlertDialog.Builder(this).setTitle("ELAK Okulum").setMessage("Kullanıcı: $u\nSürüm: 0.9.0\n\nTek uygulama · tek hesap · rol bazlı modüller").setNegativeButton("Kapat", null).setPositiveButton("Web Portal") { _, _ -> web("ELAK Okulum", "https://elak.mcoaihl.com/okulum/") }.show()
    }

    private fun section(t: String, s: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(2), 0, dp(2), dp(8))
        addView(TextView(this@DashboardActivity).apply { text = t; textSize = 19f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) })
        addView(TextView(this@DashboardActivity).apply { text = s; textSize = 12f; setTextColor(muted); setPadding(0, dp(2), 0, 0) })
    }
    private fun infoCard(): View = TextView(this).apply { text = "ELAK Okulum 0.9 · Native ana ekran aktif. Çalışan modüller korunarak tek uygulama altında birleşiyor."; textSize = 11.5f; setTextColor(Color.rgb(62,90,126)); setPadding(dp(14), dp(13), dp(14), dp(13)); background = round(Color.rgb(235,243,255), 14) }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun round(fill: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radius).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
}
