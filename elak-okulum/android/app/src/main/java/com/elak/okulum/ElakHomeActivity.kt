package com.elak.okulum

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.izin.IzinActivity88
import com.elak.okulum.rehber.CallerCache
import com.elak.okulum.rehber.RehberActivity81
import com.elak.okulum.rehber.RehberSession
import java.util.Locale

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

    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private val navItems = linkedMapOf<String, TextView>()
    private lateinit var cache: CallerCache
    private var role = "manager"
    private var displayName = ""
    private var currentTab = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE
        cache = CallerCache(this)
        role = normalizeRole(cache.profileRole())
        displayName = cache.profileName().ifBlank { OkulumSession(this).username }
        buildShell()
        showHome()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }
        content = FrameLayout(this).apply { setBackgroundColor(pageBg) }
        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(6), dp(5), dp(6))
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)))
        setContentView(root)

        val controller = WindowInsetsControllerCompat(window, root)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = true
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        buildBottomNav()
    }

    private fun buildBottomNav() {
        bottomNav.removeAllViews(); navItems.clear()
        addBottom("home", "⌂", "Ana Sayfa", blue) { showHome() }
        addBottom("notifications", "●", "Bildirimler", red) { showNotifications() }
        addBottom("calendar", "▣", "Takvim", purple) { showCalendar() }
        addBottom("messages", "✉", "Mesajlar", green) { showMessages() }
        addBottom("profile", "●", "Profil", orange) { showProfile() }
    }

    private fun addBottom(key: String, icon: String, label: String, color: Int, action: () -> Unit) {
        val item = TextView(this).apply {
            text = "$icon\n$label"
            gravity = Gravity.CENTER
            textSize = 10.5f
            setTextColor(muted)
            setPadding(dp(2), dp(4), dp(2), dp(4))
            setOnClickListener { currentTab = key; selectBottom(key); action() }
        }
        item.tag = color
        navItems[key] = item
        bottomNav.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun selectBottom(key: String) {
        navItems.forEach { (k, item) ->
            val selected = k == key
            val c = item.tag as Int
            item.setTextColor(if (selected) c else muted)
            item.background = rounded(if (selected) tint(c, 0.10f) else Color.TRANSPARENT, 15, if (selected) tint(c, .24f) else Color.TRANSPARENT)
            item.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun showHome() {
        currentTab = "home"; selectBottom(currentTab)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(24)) }
        page.addView(headerCard())
        page.addView(space(12))
        page.addView(todayCard())
        page.addView(sectionTitle("Hızlı İşlemler", "Gün içinde en sık kullandığınız işlemler"))
        page.addView(quickRow())
        page.addView(sectionTitle("ELAK Modülleri", roleModuleSubtitle()))
        page.addView(moduleGrid())
        page.addView(sectionTitle("Bugünkü Akış", "Duyuru, yoklama, izin ve randevu hareketleri burada birleşecek"))
        page.addView(feedPreview())
        show(page)
    }

    private fun headerCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(16))
            background = gradient(navy, navy2, 22)
        }
        val school = TextView(this).apply {
            text = "Mahmud Celaleddin Ökten AİHL"
            textSize = 11.5f; setTextColor(Color.rgb(196, 215, 241)); setTypeface(typeface, Typeface.BOLD)
        }
        val hello = TextView(this).apply {
            text = if (displayName.isBlank()) "ELAK Okulum" else "Merhaba, $displayName"
            textSize = 23f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(5), 0, 0)
        }
        val roleText = TextView(this).apply {
            text = "${roleLabel()}  •  Tek uygulama, tek oturum"
            textSize = 12f; setTextColor(Color.rgb(210, 224, 244)); setPadding(0, dp(5), 0, 0)
        }
        box.addView(school); box.addView(hello); box.addView(roleText)
        return box
    }

    private fun todayCard(): View {
        val box = card(Color.WHITE, blue)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(this).apply { text = "Bugün"; textSize = 18f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) })
        left.addView(TextView(this).apply {
            text = when (role) {
                "teacher" -> "Dersleriniz, yoklamalarınız ve randevularınız tek akışta"
                "guardian" -> "Çocuğunuzla ilgili okul hareketleri tek akışta"
                "security" -> "Öğrenci çıkış ve dönüş işlemleri anlık"
                else -> "Okulun günlük yönetim özeti tek ekranda"
            }
            textSize = 11.5f; setTextColor(muted); setPadding(0, dp(4), 0, 0)
        })
        val badge = TextView(this).apply {
            text = "CANLI"; textSize = 10f; gravity = Gravity.CENTER; setTextColor(green); setTypeface(typeface, Typeface.BOLD)
            background = rounded(tint(green, .10f), 10, tint(green, .22f)); setPadding(dp(9), dp(5), dp(9), dp(5))
        }
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(badge)
        box.addView(row)
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }
        stats.addView(miniStat("Bildirim", "0", red), LinearLayout.LayoutParams(0, dp(72), 1f))
        stats.addView(miniStat("Randevu", "0", purple), LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginStart = dp(7) })
        stats.addView(miniStat("İzin", "—", orange), LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginStart = dp(7) })
        box.addView(stats)
        return box
    }

    private fun miniStat(label: String, value: String, color: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        background = rounded(tint(color, .075f), 13, tint(color, .16f))
        addView(TextView(this@ElakHomeActivity).apply { text = value; textSize = 20f; setTextColor(color); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
        addView(TextView(this@ElakHomeActivity).apply { text = label; textSize = 10.5f; setTextColor(muted); gravity = Gravity.CENTER })
    }

    private fun quickRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val items = when (role) {
            "security" -> listOf(
                Quick("İzin Takip", "Çıkış / Dönüş", orange) { openIzin() },
                Quick("Rehber", "Öğrenci ara", cyan) { openRehber() }
            )
            "guardian" -> listOf(
                Quick("Devamsızlık", "Anlık takip", red) { openPortal() },
                Quick("Randevu", "Öğretmen görüşmesi", purple) { openPortal("https://elak.mcoaihl.com/randevu/") }
            )
            "teacher" -> listOf(
                Quick("Yoklama", "Ders başında", green) { openPortal() },
                Quick("Akıllı Tahta", "QR ile aç", blue) { openPortal() }
            )
            else -> listOf(
                Quick("İzin Takip", "Çıkış / Dönüş", orange) { openIzin() },
                Quick("Akıllı Rehber", "Veli / Öğrenci", cyan) { openRehber() }
            )
        }
        items.forEachIndexed { i, q ->
            row.addView(quickCard(q), LinearLayout.LayoutParams(0, dp(92), 1f).apply { if (i > 0) marginStart = dp(9) })
        }
        return row
    }

    private data class Quick(val title: String, val desc: String, val color: Int, val action: () -> Unit)
    private fun quickCard(q: Quick): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(11), dp(12), dp(11))
        background = gradient(q.color, darker(q.color), 17); elevation = dp(2).toFloat(); setOnClickListener { q.action() }
        addView(TextView(this@ElakHomeActivity).apply { text = q.title; textSize = 14f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
        addView(TextView(this@ElakHomeActivity).apply { text = q.desc; textSize = 10.5f; setTextColor(Color.argb(215,255,255,255)); setPadding(0,dp(5),0,0) })
    }

    private data class Module(val key: String, val icon: String, val title: String, val desc: String, val color: Int, val action: () -> Unit)

    private fun moduleGrid(): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val modules = modulesForRole()
        var i = 0
        while (i < modules.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0..1) {
                val item = modules.getOrNull(i + col)
                if (item != null) row.addView(moduleCard(item), LinearLayout.LayoutParams(0, dp(116), 1f).apply { if (col == 1) marginStart = dp(9) })
                else row.addView(space(1), LinearLayout.LayoutParams(0, 1, 1f))
            }
            outer.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(116)).apply { if (i > 0) topMargin = dp(9) })
            i += 2
        }
        return outer
    }

    private fun modulesForRole(): List<Module> {
        if (role == "security") return listOf(
            Module("izin","↔","İzin Takip","Öğrenci çıkış ve dönüş",orange){openIzin()},
            Module("rehber","☎","Akıllı Rehber","Öğrenci / veli iletişim",cyan){openRehber()}
        )
        if (role == "guardian") return listOf(
            Module("attendance","✓","Devamsızlık","Var / yok / geç kayıtları",red){openPortal()},
            Module("announcements","●","Duyurular","Okul ve sınıf duyuruları",blue){showNotifications()},
            Module("exams","▣","Sınav Takvimi","Yaklaşan sınavlar",purple){showCalendar()},
            Module("schedule","▦","Ders Programı","Haftalık ders çizelgesi",cyan){openPortal()},
            Module("appointment","◷","Randevu","Öğretmen görüşmesi",green){openPortal("https://elak.mcoaihl.com/randevu/")},
            Module("survey","☑","Anketler","Tercih ve izin anketleri",orange){openPortal()},
            Module("trip","⌖","Geziler","Kabul / ret işlemleri",green){openPortal()},
            Module("docs","▤","Belgeler","Form ve dokümanlar",blue){openPortal()}
        )
        if (role == "teacher") return listOf(
            Module("attendance","✓","e-Yoklama","Sınıfta hızlı yoklama",green){openPortal()},
            Module("schedule","▦","Ders Programım","Haftalık program",purple){openPortal()},
            Module("board","QR","Akıllı Tahta","QR / cihaz yönetimi",blue){openPortal()},
            Module("rehber","☎","Akıllı Rehber","Veli iletişim rehberi",cyan){openRehber()},
            Module("appointment","◷","Randevularım","Veli görüşmeleri",orange){openPortal("https://elak.mcoaihl.com/randevu/")},
            Module("comment","✎","Öğretmen Yorumu","Öğrenci gelişim notu",green){openPortal()},
            Module("announcements","●","Duyurular","Sınıf ve okul akışı",red){showNotifications()},
            Module("docs","▤","Belgeler","Form ve dokümanlar",blue){openPortal()}
        )
        return listOf(
            Module("izin","↔","İzin Takip","Çıkış, dönüş ve güvenlik",orange){openIzin()},
            Module("rehber","☎","Akıllı Rehber","Öğrenci ve veli rehberi",cyan){openRehber()},
            Module("attendance","✓","Online Yoklama","Örgün, DYK ve etkinlik",green){openPortal()},
            Module("appointment","◷","Veli Randevu","Görüşme saatleri ve kayıtlar",purple){openPortal("https://elak.mcoaihl.com/randevu/")},
            Module("announcements","●","Duyuru Merkezi","Hedefli bildirim ve okunma",red){showNotifications()},
            Module("schedule","▦","Ders Programı","Öğretmen ve sınıf programı",blue){openPortal()},
            Module("board","QR","Akıllı Tahta","QR, kilit ve cihaz yönetimi",cyan){openPortal()},
            Module("exams","▣","LGS / YKS","Deneme ve akademik takip",purple){openPortal()},
            Module("survey","☑","Anket & Onay","Veli görüşü ve izinler",green){openPortal()},
            Module("trip","⌖","Etkinlik & Gezi","Katılım ve veli onayı",orange){openPortal()},
            Module("docs","▤","Belgeler","Formlar ve dokümanlar",blue){openPortal()},
            Module("staff","♟","Öğretmen İşlemleri","Nöbet ve personel süreçleri",red){openPortal()}
        )
    }

    private fun moduleCard(m: Module): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(12), dp(12), dp(10)); gravity = Gravity.CENTER_VERTICAL
        background = rounded(Color.WHITE, 17, line); elevation = dp(1).toFloat(); setOnClickListener { m.action() }
        val top = LinearLayout(this@ElakHomeActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this@ElakHomeActivity).apply {
            text = m.icon; textSize = 17f; gravity = Gravity.CENTER; setTextColor(m.color); setTypeface(typeface, Typeface.BOLD)
            background = rounded(tint(m.color,.10f), 12); setPadding(dp(8),dp(5),dp(8),dp(5))
        })
        top.addView(TextView(this@ElakHomeActivity).apply {
            text = m.title; textSize = 13.5f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD); setPadding(dp(9),0,0,0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(top)
        addView(TextView(this@ElakHomeActivity).apply { text = m.desc; textSize = 10.5f; setTextColor(muted); setPadding(0,dp(8),0,0); maxLines = 2 })
    }

    private fun feedPreview(): View {
        val box = card(Color.WHITE, purple)
        box.addView(feedLine("Bildirim Merkezi", "Devamsızlık, duyuru ve kişisel bildirimler", red))
        box.addView(divider())
        box.addView(feedLine("Randevu & Takvim", "Yaklaşan görüşme ve sınavlar", purple))
        box.addView(divider())
        box.addView(feedLine("Okul Hareketleri", "İzin, yoklama ve güvenlik kayıtları", orange))
        return box
    }

    private fun feedLine(title: String, desc: String, color: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(7),0,dp(7))
        addView(View(this@ElakHomeActivity).apply { background = rounded(color, 8) }, LinearLayout.LayoutParams(dp(5), dp(40)))
        val tx = LinearLayout(this@ElakHomeActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10),0,0,0) }
        tx.addView(TextView(this@ElakHomeActivity).apply { text=title; textSize=12.5f; setTextColor(ink); setTypeface(typeface,Typeface.BOLD) })
        tx.addView(TextView(this@ElakHomeActivity).apply { text=desc; textSize=10.5f; setTextColor(muted); setPadding(0,dp(2),0,0) })
        addView(tx)
    }

    private fun showNotifications() {
        currentTab="notifications"; selectBottom(currentTab)
        show(simpleSection("Bildirim Merkezi", "İlkSMS modelindeki gibi Devamsızlık / Özel / Genel bildirimleri tek yerde toplayacağız.", listOf(
            Triple("Devamsızlık", "Yoklama alınır alınmaz ilgili veliye", red),
            Triple("Özel", "Sınav, öğretmen yorumu, randevu ve öğrenciye özel kayıtlar", purple),
            Triple("Genel", "Okul ve sınıf duyuruları", blue),
            Triple("Okundu Bilgisi", "İdare gönderim ve okunma durumunu görecek", green)
        )))
    }

    private fun showCalendar() {
        currentTab="calendar"; selectBottom(currentTab)
        show(simpleSection("Takvim", "Sınav, randevu, gezi, DYK ve okul etkinlikleri tek takvimde birleşecek.", listOf(
            Triple("Sınav Takvimi", "Ders ve tarih bazlı", purple),
            Triple("Randevular", "Öğretmen / veli görüşmeleri", green),
            Triple("Etkinlik & Gezi", "Katılım ve onay süreleri", orange),
            Triple("DYK / Program", "Ders ve çalışma takvimi", blue)
        )))
    }

    private fun showMessages() {
        currentTab="messages"; selectBottom(currentTab)
        show(simpleSection("Mesajlar", "Kontrollü öğretmen–veli iletişimi; okulun belirlediği saat ve yetki kurallarıyla.", listOf(
            Triple("Birebir Mesaj", "Öğretmen–veli arasında kayıtlı görüşme", green),
            Triple("Dosya / Görsel", "Mesaj içinde güvenli paylaşım", blue),
            Triple("Okundu", "Teslim ve okunma durumu", purple),
            Triple("İletişim Kuralı", "Sohbeti kimin başlatacağı okul ayarından", orange)
        )))
    }

    private fun showProfile() {
        currentTab="profile"; selectBottom(currentTab)
        val page = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(24)) }
        page.addView(headerCard())
        page.addView(sectionTitle("Hesap", "ELAK Okulum kişisel oturum ve cihaz ayarları"))
        val box = card(Color.WHITE, orange)
        box.addView(infoRow("Ad Soyad", displayName.ifBlank{"-"}))
        box.addView(divider())
        box.addView(infoRow("Rol", roleLabel()))
        box.addView(divider())
        box.addView(infoRow("Sürüm", "0.9.0"))
        box.addView(divider())
        box.addView(infoRow("Okul", "Mahmud Celaleddin Ökten AİHL"))
        page.addView(box)
        val portal=actionButton("Eski ELAK Portalını Aç", blue); portal.setOnClickListener{openPortal()}; page.addView(space(12));page.addView(portal)
        val logout=actionButton("Oturumu Kapat", red); logout.setOnClickListener{confirmLogout()};page.addView(space(9));page.addView(logout)
        show(page)
    }

    private fun simpleSection(title:String, sub:String, items:List<Triple<String,String,Int>>):View {
        val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(24))}
        page.addView(hero(title,sub))
        items.forEach{(a,b,c)-> page.addView(card(Color.WHITE,c).apply{addView(TextView(this@ElakHomeActivity).apply{text=a;textSize=15f;setTextColor(ink);setTypeface(typeface,Typeface.BOLD)});addView(TextView(this@ElakHomeActivity).apply{text=b;textSize=11.5f;setTextColor(muted);setPadding(0,dp(5),0,0)})},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{topMargin=dp(9)})}
        return page
    }

    private fun openIzin(){ startActivity(Intent(this, IzinActivity88::class.java)) }
    private fun openRehber(){ startActivity(Intent(this, RehberActivity81::class.java)) }
    private fun openPortal(url:String="https://elak.mcoaihl.com/okulum/"){
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_FORCE_WEB,true).putExtra(MainActivity.EXTRA_START_URL,url))
    }

    private fun confirmLogout(){
        AlertDialog.Builder(this).setTitle("Oturumu kapat").setMessage("ELAK Okulum kişisel oturumu bu cihazdan kapatılsın mı?")
            .setNegativeButton("Vazgeç",null).setPositiveButton("Çıkış") { _,_->
                OkulumSession(this).clear(); RehberSession(this).apply{token="";username="";lastSync=0L}
                val i=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(i); finish()
            }.show()
    }

    private fun roleModuleSubtitle():String = when(role){
        "teacher"->"Öğretmenin günlük sınıf işleri tek yerde"
        "guardian"->"Çocuğunuza ait okul bilgileri tek yerde"
        "security"->"Güvenlik ve öğrenci hareketleri"
        else->"Okul yönetimi için merkezi çalışma alanı"
    }
    private fun roleLabel():String = when(role){"school_admin"->"Tam Yetkili Yönetici";"manager"->"Yönetici";"teacher"->"Öğretmen";"guardian"->"Veli";"security"->"Güvenlik";"dormitory"->"Pansiyon";else->"Kullanıcı"}
    private fun normalizeRole(raw:String):String{
        val r=raw.trim().lowercase(Locale.forLanguageTag("tr-TR"))
        return when{r.contains("veli")||r.contains("guardian")||r.contains("parent")->"guardian";r.contains("öğret")||r.contains("ogret")||r.contains("teacher")->"teacher";r.contains("güven")||r.contains("guven")||r.contains("security")->"security";r.contains("pans")||r.contains("dorm")->"dormitory";r.contains("admin")||r.contains("tam")||r.contains("yönet")||r.contains("yonet")||r.contains("manager")->"manager";else->"manager"}
    }

    private fun sectionTitle(title:String,sub:String):View=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(3),dp(18),dp(3),dp(8));addView(TextView(this@ElakHomeActivity).apply{text=title;textSize=17f;setTextColor(ink);setTypeface(typeface,Typeface.BOLD)});addView(TextView(this@ElakHomeActivity).apply{text=sub;textSize=10.5f;setTextColor(muted);setPadding(0,dp(2),0,0)})}
    private fun hero(title:String,sub:String):View=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18));background=gradient(blue,cyan,20);addView(TextView(this@ElakHomeActivity).apply{text=title;textSize=23f;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD)});addView(TextView(this@ElakHomeActivity).apply{text=sub;textSize=12f;setTextColor(Color.argb(225,255,255,255));setPadding(0,dp(6),0,0)})}
    private fun card(bg:Int=Color.WHITE,accent:Int?=null):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(15),dp(14),dp(15),dp(14));background=rounded(bg,17,line);elevation=dp(1).toFloat();if(accent!=null)addView(View(this@ElakHomeActivity).apply{background=rounded(accent,4)},LinearLayout.LayoutParams(dp(42),dp(4)).apply{bottomMargin=dp(9)})}
    private fun infoRow(k:String,v:String):View=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(7),0,dp(7));addView(TextView(this@ElakHomeActivity).apply{text=k;textSize=11f;setTextColor(muted)},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,.42f));addView(TextView(this@ElakHomeActivity).apply{text=v;textSize=12f;setTextColor(ink);setTypeface(typeface,Typeface.BOLD)},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,.58f))}
    private fun actionButton(label:String,color:Int):TextView=TextView(this).apply{text=label;textSize=13f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);background=rounded(color,13);setPadding(dp(12),dp(13),dp(12),dp(13))}
    private fun divider():View=View(this).apply{setBackgroundColor(line);layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,1)}
    private fun space(h:Int):View=View(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(h))}
    private fun show(view:View){val scroll=ScrollView(this).apply{isFillViewport=true;addView(view)};content.removeAllViews();content.addView(scroll,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))}
    private fun rounded(color:Int,radius:Int,stroke:Int=Color.TRANSPARENT)=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;cornerRadius=dp(radius).toFloat();setColor(color);if(stroke!=Color.TRANSPARENT)setStroke(dp(1),stroke)}
    private fun gradient(a:Int,b:Int,radius:Int)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply{cornerRadius=dp(radius).toFloat()}
    private fun tint(color:Int,amount:Float):Int{val r=Color.red(color);val g=Color.green(color);val b=Color.blue(color);return Color.rgb((255-(255-r)*amount).toInt().coerceIn(0,255),(255-(255-g)*amount).toInt().coerceIn(0,255),(255-(255-b)*amount).toInt().coerceIn(0,255))}
    private fun darker(color:Int)=Color.rgb((Color.red(color)*.78f).toInt(),(Color.green(color)*.78f).toInt(),(Color.blue(color)*.78f).toInt())
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}
