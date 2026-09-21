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
    private val pageBg = Color.rgb(245, 248, 252)
    private val text = Color.rgb(23, 43, 75)
    private val muted = Color.rgb(103, 119, 141)
    private val blue = Color.rgb(37, 99, 235)
    private val green = Color.rgb(16, 163, 105)
    private val orange = Color.rgb(238, 145, 32)
    private val purple = Color.rgb(117, 82, 190)
    private val cyan = Color.rgb(21, 145, 177)
    private val red = Color.rgb(210, 61, 70)

    private lateinit var root: LinearLayout
    private lateinit var contentHost: FrameLayout
    private lateinit var outsideValue: TextView
    private lateinit var pendingValue: TextView
    private lateinit var todayValue: TextView
    private lateinit var returnedValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE
        buildShell()
        applyInsets()
        renderHome()
    }

    override fun onResume() {
        super.onResume()
        if (::outsideValue.isInitialized) refreshLiveSummary()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }
        contentHost = FrameLayout(this).apply { setBackgroundColor(pageBg) }
        root.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildBottomNav(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
        setContentView(root)
    }

    private fun applyInsets() {
        val controller = WindowInsetsControllerCompat(window, root)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = true
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun renderHome() {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }
        page.addView(buildHeader())
        page.addView(space(16))
        page.addView(sectionTitle("Bugün", "Okulun anlık durumuna hızlı bakış"))
        page.addView(buildStats())
        page.addView(space(18))
        page.addView(sectionTitle("Uygulamalar", "Yetkinize göre okul modüllerine erişin"))
        page.addView(buildModuleGrid())
        page.addView(space(16))
        page.addView(buildInfoBanner())
        scroll.addView(page)
        contentHost.removeAllViews()
        contentHost.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        refreshLiveSummary()
    }

    private fun buildHeader(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = rounded(navy, 20)
            elevation = dp(3).toFloat()
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "ELAK"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(34, 92, 170), 12)
            setPadding(dp(11), dp(7), dp(11), dp(7))
        }
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@DashboardActivity).apply {
                text = "ELAK Okulum"
                textSize = 21f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@DashboardActivity).apply {
                text = "Mahmud Celaleddin Ökten AİHL"
                textSize = 12.5f
                setTextColor(Color.rgb(205, 219, 240))
            })
        }
        top.addView(brand)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(this).apply {
            text = "●"
            textSize = 18f
            setTextColor(Color.rgb(75, 215, 155))
            contentDescription = "Bağlı"
        })
        box.addView(top)

        val username = OkulumSession(this).username.ifBlank { "ELAK kullanıcısı" }
        box.addView(TextView(this).apply {
            text = "Hoş geldiniz, $username"
            textSize = 13f
            setTextColor(Color.rgb(220, 230, 246))
            setPadding(0, dp(13), 0, 0)
        })
        return box
    }

    private fun buildStats(): View {
        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            useDefaultMargins = false
        }
        outsideValue = statValue("--", orange)
        pendingValue = statValue("--", purple)
        todayValue = statValue("--", blue)
        returnedValue = statValue("--", green)
        addStat(grid, "Dışarıda", outsideValue, "Öğrenci", orange) { openIzin() }
        addStat(grid, "Çıkış Bekleyen", pendingValue, "İzin", purple) { openIzin() }
        addStat(grid, "Bugünkü İzin", todayValue, "Kayıt", blue) { openIzin() }
        addStat(grid, "Dönen", returnedValue, "Bugün", green) { openIzin() }
        return grid
    }

    private fun addStat(grid: GridLayout, label: String, value: TextView, suffix: String, accent: Int, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 16)
            elevation = dp(1).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(TextView(this@DashboardActivity).apply {
                text = label
                textSize = 12f
                setTextColor(muted)
            })
            addView(value)
            addView(TextView(this@DashboardActivity).apply {
                text = suffix
                textSize = 10.5f
                setTextColor(accent)
                setTypeface(typeface, Typeface.BOLD)
            })
        }
        val lp = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        grid.addView(card, lp)
    }

    private fun statValue(initial: String, accent: Int) = TextView(this).apply {
        text = initial
        textSize = 27f
        setTextColor(accent)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(2), 0, 0)
    }

    private fun buildModuleGrid(): View {
        val grid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
        }
        addModule(grid, "Akıllı Rehber", "Öğrenci ve veli iletişimi", "☎", blue) { openRehber() }
        addModule(grid, "İzin Takip", "Çıkış, dönüş ve güvenlik", "✓", orange) { openIzin() }
        addModule(grid, "DYK / Etkinlik", "Yoklama ve devam takibi", "≡", green) { openWeb("https://www.mcoaihl.com/dyk_soset/index.php") }
        addModule(grid, "Deneme Sonuçları", "LGS / YKS sonuç ve analiz", "★", purple) { openWeb("https://elak.mcoaihl.com/deneme-sonuc/index.php") }
        addModule(grid, "Kabul Sınavı", "Aday, sonuç ve salon işlemleri", "⌁", cyan) { openWeb("https://www.mcoaihl.com/kabulsinavsonuc/") }
        addModule(grid, "Yeni Kayıt", "Öğrenci / veli bilgi formları", "+", Color.rgb(33, 150, 115)) { openWeb("https://mcoaihl.com/yenikayit/") }
        addModule(grid, "Okulum Web", "Mevcut ELAK okul portalı", "↗", Color.rgb(72, 86, 110)) { openWeb("https://elak.mcoaihl.com/okulum/") }
        addModule(grid, "E-Yönetim", "Tüm okul dijital sistemleri", "▦", red) { openWeb("https://www.mcoaihl.com/") }
        return grid
    }

    private fun addModule(grid: GridLayout, title: String, subtitle: String, icon: String, accent: Int, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.WHITE, 17)
            elevation = dp(1).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        card.addView(TextView(this).apply {
            text = icon
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(accent, 13)
            layoutParams = LinearLayout.LayoutParams(dp(45), dp(45))
        })
        card.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(11), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = subtitle
            textSize = 11.5f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
            maxLines = 2
        })
        val lp = GridLayout.LayoutParams().apply {
            width = 0
            height = dp(142)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        grid.addView(card, lp)
    }

    private fun buildInfoBanner(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(13), dp(15), dp(13))
        background = rounded(Color.rgb(235, 243, 255), 14)
        addView(TextView(this@DashboardActivity).apply {
            text = "ELAK Okulum 0.9"
            textSize = 13f
            setTextColor(blue)
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(this@DashboardActivity).apply {
            text = "Ana ekran artık native Android. Modüller tek uygulamada birleşirken mevcut çalışan sistemler korunur."
            textSize = 11.5f
            setTextColor(Color.rgb(70, 94, 128))
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun buildBottomNav(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        addBottomItem(bar, "⌂", "Ana", blue) { renderHome() }
        addBottomItem(bar, "●", "Bildirim", muted) { future("Bildirim Merkezi") }
        addBottomItem(bar, "✉", "Mesajlar", muted) { future("Mesajlaşma") }
        addBottomItem(bar, "□", "Takvim", muted) { future("Okul Takvimi") }
        addBottomItem(bar, "◉", "Profil", muted) { showProfile() }
        return bar
    }

    private fun addBottomItem(bar: LinearLayout, icon: String, label: String, color: Int, action: () -> Unit) {
        bar.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(TextView(this@DashboardActivity).apply {
                text = icon
                textSize = 16f
                setTextColor(color)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@DashboardActivity).apply {
                text = label
                textSize = 10f
                setTextColor(color)
                gravity = Gravity.CENTER
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun refreshLiveSummary() {
        val session = IzinSession(this)
        if (!session.isReady) {
            outsideValue.text = "--"
            pendingValue.text = "--"
            todayValue.text = "--"
            returnedValue.text = "--"
            return
        }
        thread {
            try {
                val json = IzinApi.dashboard(session)
                val stats = json.optJSONObject("stats")
                runOnUiThread {
                    outsideValue.text = stats?.optInt("outside", 0).toString()
                    pendingValue.text = stats?.optInt("pending", 0).toString()
                    todayValue.text = stats?.optInt("today", 0).toString()
                    returnedValue.text = stats?.optInt("returned_today", 0).toString()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    outsideValue.text = "--"
                    pendingValue.text = "--"
                    todayValue.text = "--"
                    returnedValue.text = "--"
                }
            }
        }
    }

    private fun openRehber() {
        startActivity(Intent(this, RehberActivity81::class.java))
    }

    private fun openIzin() {
        startActivity(Intent(this, IzinActivity88::class.java))
    }

    private fun openWeb(url: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_URL, url))
    }

    private fun future(name: String) {
        Toast.makeText(this, "$name, ELAK 0.9 entegrasyonunda bu alt menüye bağlanacak.", Toast.LENGTH_SHORT).show()
    }

    private fun showProfile() {
        val username = OkulumSession(this).username.ifBlank { "Henüz kayıtlı kullanıcı yok" }
        AlertDialog.Builder(this)
            .setTitle("ELAK Okulum")
            .setMessage("Kullanıcı: $username\nSürüm: 0.9.0\n\nTek uygulama · tek hesap · rol bazlı modüller")
            .setNegativeButton("Kapat", null)
            .setPositiveButton("Web Portal") { _, _ -> openWeb("https://elak.mcoaihl.com/okulum/") }
            .show()
    }

    private fun sectionTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), 0, dp(2), dp(8))
        addView(TextView(this@DashboardActivity).apply {
            text = title
            textSize = 19f
            setTextColor(text)
            setTypeface(typeface, Typeface.BOLD)
        })
        addView(TextView(this@DashboardActivity).apply {
            text = subtitle
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(2), 0, 0)
        })
    }

    private fun space(h: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun rounded(fill: Int, radiusDp: Int) = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
