package com.elak.okulum

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.izin.IzinActivity88
import com.elak.okulum.izin.IzinSession
import com.elak.okulum.rehber.RehberActivity81
import com.elak.okulum.rehber.RehberSession
import org.json.JSONArray
import org.json.JSONObject

class ElakHomeActivity : AppCompatActivity() {
    private val navy = Color.rgb(8, 31, 68)
    private val navy2 = Color.rgb(17, 55, 105)
    private val pageBg = Color.rgb(244, 247, 252)
    private val ink = Color.rgb(15, 34, 62)
    private val muted = Color.rgb(100, 116, 139)
    private val line = Color.rgb(224, 231, 240)
    private val blue = Color.rgb(37, 99, 235)
    private val cyan = Color.rgb(8, 145, 178)
    private val green = Color.rgb(5, 150, 105)
    private val orange = Color.rgb(234, 88, 12)
    private val purple = Color.rgb(124, 58, 237)
    private val red = Color.rgb(220, 38, 38)

    private lateinit var core: OkulumSession
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var bottom: LinearLayout
    private val nav = linkedMapOf<String, TextView>()

    data class Module(
        val key: String,
        val name: String,
        val icon: String,
        val desc: String,
        val route: String,
        val color: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        core = OkulumSession(this)
        if (!core.isReady) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        buildShell()
        showHome()
    }

    private fun buildShell() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE

        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBg) }
        content = FrameLayout(this).apply { setBackgroundColor(pageBg) }
        bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(6), dp(5), dp(6))
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.requestApplyInsets(root)
        buildBottom()
    }

    private fun buildBottom() {
        addNav("home", "⌂", "Ana Sayfa", blue) { showHome() }
        addNav("notifications", "●", "Bildirimler", red) { showInfo("Bildirimler", "Kurum ve modül bildirimleri burada birleşecek.", red) }
        addNav("calendar", "▣", "Takvim", purple) { showInfo("Takvim", "Sınav, randevu ve okul takvimi burada birleşecek.", purple) }
        addNav("messages", "✉", "Mesajlar", green) { showInfo("Mesajlar", "Okul içi mesajlar ve veli iletişimleri burada birleşecek.", green) }
        addNav("profile", "●", "Profil", orange) { showProfile() }
    }

    private fun addNav(key: String, icon: String, label: String, color: Int, action: () -> Unit) {
        val v = TextView(this).apply {
            text = "$icon\n$label"
            textSize = 10.5f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(2), dp(4), dp(2), dp(4))
            tag = color
            setOnClickListener { selectNav(key); action() }
        }
        nav[key] = v
        bottom.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun selectNav(key: String) {
        nav.forEach { (k, v) ->
            val c = v.tag as Int
            val selected = k == key
            v.setTextColor(if (selected) c else muted)
            v.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            v.background = rounded(if (selected) tint(c, .09f) else Color.TRANSPARENT, 14, if (selected) tint(c, .18f) else null)
        }
    }

    private fun showHome() {
        selectNav("home")
        val page = column()
        page.addView(headerCard())
        page.addView(space(12))
        page.addView(statusCard())
        page.addView(section("ELAK Modülleri", "Kurum lisansınıza göre aktif ve pasif modüller"))
        page.addView(moduleGrid())
        page.addView(section("Tek Oturum", "Kurum kodu + kullanıcı kodu + şifre ile bir kez giriş"))
        page.addView(infoCard("Akıllı Rehber ve İzin Takip ikinci kez parola istemeden hazırlanır. Öğrenci/veli iletişim verisi Rehber eşitlemesinden ortak kullanılır.", cyan))
        show(page)
    }

    private fun headerCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(16))
        background = gradient(navy, navy2, 22)
        addView(text(core.orgName.ifBlank { "ELAK Okulum" }, 12f, Color.rgb(196, 215, 241), true))
        addView(text("Merhaba, ${core.displayName.ifBlank { core.username }}", 23f, Color.WHITE, true).apply { setPadding(0, dp(5), 0, 0) })
        addView(text("${core.roleName.ifBlank { roleLabel(core.roleKey) }}  •  ${core.orgCode}", 12f, Color.rgb(210, 224, 244), false).apply { setPadding(0, dp(5), 0, 0) })
    }

    private fun statusCard(): View {
        val mods = allModules()
        val active = mods.count { core.moduleEnabled(it.key) }
        val box = card(Color.WHITE, blue)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Kurum Modülleri", 18f, ink, true))
            addView(text("Lisans durumuna göre otomatik yönetiliyor", 11.5f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(badge("$active / ${mods.size} AKTİF", green))
        box.addView(top)
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }
        stats.addView(mini("Aktif", active.toString(), green), LinearLayout.LayoutParams(0, dp(68), 1f))
        stats.addView(mini("Pasif", (mods.size - active).toString(), muted), LinearLayout.LayoutParams(0, dp(68), 1f).apply { marginStart = dp(8) })
        stats.addView(mini("Sürüm", "0.9.2", blue), LinearLayout.LayoutParams(0, dp(68), 1f).apply { marginStart = dp(8) })
        box.addView(stats)
        return box
    }

    private fun moduleGrid(): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = allModules()
        var i = 0
        while (i < list.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(2) { col ->
                val m = list.getOrNull(i + col)
                if (m == null) row.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
                else row.addView(moduleCard(m), LinearLayout.LayoutParams(0, dp(126), 1f).apply { if (col == 1) marginStart = dp(9) })
            }
            outer.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(126)).apply { if (i > 0) topMargin = dp(9) })
            i += 2
        }
        return outer
    }

    private fun moduleCard(m: Module): View {
        val enabled = core.moduleEnabled(m.key)
        val bg = if (enabled) Color.WHITE else Color.rgb(239, 242, 246)
        val accent = if (enabled) m.color else Color.rgb(148, 163, 184)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(10))
            background = rounded(bg, 17, if (enabled) tint(accent, .22f) else Color.rgb(218, 224, 232))
            elevation = if (enabled) dp(1).toFloat() else 0f
            addView(TextView(this@ElakHomeActivity).apply { text = m.icon; textSize = 24f; setTextColor(accent) })
            addView(text(m.name, 14f, if (enabled) ink else Color.rgb(100, 116, 139), true).apply { setPadding(0, dp(4), 0, 0) })
            addView(text(if (enabled) m.desc else "Kurum lisansında pasif", 10.5f, if (enabled) muted else Color.rgb(148, 163, 184), false).apply { setPadding(0, dp(3), 0, 0); maxLines = 2 })
            setOnClickListener {
                if (!enabled) Toast.makeText(this@ElakHomeActivity, "${m.name} kurum lisansında pasif.", Toast.LENGTH_SHORT).show()
                else openModule(m)
            }
        }
    }

    private fun openModule(m: Module) {
        when (m.key) {
            "akilli_rehber" -> startActivity(Intent(this, RehberActivity81::class.java))
            "izin_takip" -> startActivity(Intent(this, IzinActivity88::class.java))
            else -> {
                val route = m.route.trim()
                if (route.isBlank()) {
                    Toast.makeText(this, "${m.name} için yayın yolu henüz tanımlanmadı.", Toast.LENGTH_LONG).show()
                } else {
                    val url = if (route.startsWith("http")) route else "https://elak.mcoaihl.com/${route.trimStart('/')}"
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_FORCE_WEB, true)
                        putExtra(MainActivity.EXTRA_START_URL, url)
                    })
                }
            }
        }
    }

    private fun allModules(): List<Module> {
        val defaults = linkedMapOf(
            "izin_takip" to Module("izin_takip", "İzin Takip", "🪪", "Öğrenci izin, çıkış ve dönüş işlemleri", "https://mcoaihl.com/izin/", green),
            "akilli_rehber" to Module("akilli_rehber", "Akıllı Rehber", "☎", "Öğrenci, veli ve personel iletişim rehberi", "https://elak.mcoaihl.com/rehber/", blue),
            "yoklama" to Module("yoklama", "Online Yoklama", "📋", "Örgün eğitim, açık lise, DYK ve etkinlik yoklamaları", "", cyan),
            "ders_programi" to Module("ders_programi", "Ders Programı", "🗓", "Günlük ve haftalık ders programları", "", orange),
            "lgs_yks" to Module("lgs_yks", "LGS / YKS", "📊", "Deneme sonuçları, analiz ve öğrenci takibi", "https://elak.mcoaihl.com/deneme-sonuc/", purple),
            "ortak" to Module("ortak", "Ortak Sınav", "📝", "Ortak sınav planlama ve analiz", "", red),
            "kelebek" to Module("kelebek", "Kelebek Sınav", "🦋", "Sınav salonu ve yerleştirme", "/kelebek/core-entry.php", Color.rgb(79, 70, 229)),
            "sorumluluk" to Module("sorumluluk", "Sorumluluk Sınavı", "🎯", "Sorumluluk sınavı işlemleri", "", red),
            "ogretmen" to Module("ogretmen", "Öğretmen Nöbet", "👩‍🏫", "Öğretmen nöbet çizelgeleri", "", blue),
            "ogrenci" to Module("ogrenci", "Öğrenci Nöbet", "🧑‍🎓", "Öğrenci nöbet planlaması", "", orange)
        )

        val cat: JSONArray = core.catalog()
        for (i in 0 until cat.length()) {
            val j = cat.optJSONObject(i) ?: continue
            val key = j.optString("module_key").ifBlank { j.optString("id") }
            if (key.isBlank()) continue
            val old = defaults[key]
            val name = j.optString("module_name").ifBlank { j.optString("label") }.ifBlank { old?.name ?: key }
            val icon = j.optString("icon").ifBlank { old?.icon ?: "🧩" }
            val desc = j.optString("description").ifBlank { j.optString("desc") }.ifBlank { old?.desc ?: "ELAK modülü" }
            val route = j.optString("route").ifBlank { old?.route.orEmpty() }
            defaults[key] = Module(key, name, icon, desc, route, old?.color ?: blue)
        }
        return defaults.values.toList()
    }

    private fun showInfo(title: String, desc: String, color: Int) {
        val page = column()
        page.addView(infoCard(title, color, true))
        page.addView(section(title, desc))
        page.addView(infoCard("Bu alan CORE merkezi verileriyle geliştirilmeye devam edecek.", color))
        show(page)
    }

    private fun showProfile() {
        selectNav("profile")
        val page = column()
        page.addView(infoCard("Profil", orange, true))
        val box = card(Color.WHITE, orange)
        box.addView(detail("Ad Soyad", core.displayName.ifBlank { core.username }))
        box.addView(detail("Kullanıcı Kodu", core.username))
        box.addView(detail("Kurum", core.orgName.ifBlank { core.orgCode }))
        box.addView(detail("Kurum Kodu", core.orgCode))
        box.addView(detail("Rol", core.roleName.ifBlank { roleLabel(core.roleKey) }))
        box.addView(detail("Lisans Bitiş", core.expiry.ifBlank { "—" }))
        val logout = Button(this).apply {
            text = "Çıkış Yap"; isAllCaps = false; setTextColor(red); background = rounded(Color.WHITE, 12, tint(red, .45f))
            setOnClickListener {
                AlertDialog.Builder(this@ElakHomeActivity)
                    .setMessage("ELAK Okulum oturumu kapatılsın mı?")
                    .setNegativeButton("Vazgeç", null)
                    .setPositiveButton("Çıkış Yap") { _, _ -> logout() }
                    .show()
            }
        }
        box.addView(logout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(14) })
        page.addView(box)
        show(page)
    }

    private fun logout() {
        core.clear()
        RehberSession(this).clear()
        IzinSession(this).clear()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun show(child: View) {
        content.removeAllViews()
        content.addView(ScrollView(this).apply { isFillViewport = true; addView(child) }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(24)) }
    private fun section(title: String, sub: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(15), dp(2), dp(9))
        addView(text(title, 18f, ink, true)); addView(text(sub, 11.5f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
    }
    private fun card(fill: Int, accent: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(14), dp(15), dp(14)); background = rounded(fill, 17, tint(accent, .20f)); elevation = dp(1).toFloat()
    }
    private fun infoCard(v: String, color: Int, title: Boolean = false) = TextView(this).apply {
        text = v; textSize = if (title) 22f else 12.5f; setTextColor(if (title) ink else color); if (title) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(16), dp(14), dp(16), dp(14)); background = rounded(if (title) Color.WHITE else tint(color, .07f), 15, tint(color, .18f))
    }
    private fun mini(label: String, value: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rounded(tint(color, .075f), 12, tint(color, .15f))
        addView(text(value, 19f, color, true).apply { gravity = Gravity.CENTER }); addView(text(label, 10f, muted, false).apply { gravity = Gravity.CENTER })
    }
    private fun detail(k: String, v: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(5), 0, dp(5))
        addView(text(k, 12f, muted, false), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .4f))
        addView(text(v.ifBlank { "—" }, 12.5f, ink, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .6f))
    }
    private fun badge(v: String, color: Int) = TextView(this).apply {
        text = v; textSize = 9.5f; setTextColor(color); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(dp(8), dp(5), dp(8), dp(5)); background = rounded(tint(color, .1f), 20)
    }
    private fun text(v: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = v; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun roleLabel(k: String) = when (k.lowercase()) {
        "admin" -> "Kurum Yöneticisi"; "manager" -> "İdareci"; "teacher" -> "Öğretmen"; "guidance" -> "Rehber Öğretmen"; "security" -> "Güvenlik"; "parent", "guardian" -> "Veli"; else -> "Kullanıcı"
    }
    private fun space(h: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun rounded(fill: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(r).toFloat(); if (stroke != null) setStroke(dp(1), stroke) }
    private fun gradient(a: Int, b: Int, r: Int) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(a, b)).apply { cornerRadius = dp(r).toFloat() }
    private fun tint(c: Int, a: Float) = Color.argb((255 * a).toInt(), Color.red(c), Color.green(c), Color.blue(c))
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
}
