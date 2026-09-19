package com.elak.okulum.izin

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.OkulumSession
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class IzinActivityModern : AppCompatActivity() {
    companion object { const val EXTRA_URL = "izin_url" }

    private val navy = Color.rgb(8, 31, 68)
    private val blue = Color.rgb(37, 99, 235)
    private val cyan = Color.rgb(8, 145, 178)
    private val green = Color.rgb(5, 150, 105)
    private val orange = Color.rgb(234, 88, 12)
    private val purple = Color.rgb(124, 58, 237)
    private val red = Color.rgb(220, 38, 38)
    private val pageBg = Color.rgb(244, 247, 252)
    private val text = Color.rgb(15, 34, 62)
    private val muted = Color.rgb(100, 116, 139)
    private val border = Color.rgb(222, 229, 240)

    private lateinit var session: IzinSession
    private lateinit var root: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var userText: TextView
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var loading: ProgressBar
    private val navButtons = linkedMapOf<String, TextView>()
    private var currentNav = "home"
    private var selectedStudent: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE
        buildShell()
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        session = IzinSession(this)
        startSession()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBg) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(10), dp(12)); background = gradient(navy, Color.rgb(15, 52, 103), 0)
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleText = TextView(this).apply { text = "İzin Takip"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) }
        userText = TextView(this).apply { text = "Bağlanıyor…"; textSize = 12f; setTextColor(Color.rgb(205, 220, 244)); setPadding(0, dp(3), 0, 0) }
        titles.addView(titleText); titles.addView(userText)
        val refresh = TextView(this).apply {
            text = "↻"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(Color.argb(28,255,255,255), 12); contentDescription = "Yenile"
            setOnClickListener { startSession() }
        }
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(refresh, LinearLayout.LayoutParams(dp(44), dp(44)))

        content = FrameLayout(this).apply { setBackgroundColor(pageBg) }
        loading = ProgressBar(this).apply { visibility = View.GONE }
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(5), dp(6), dp(5), dp(6)); setBackgroundColor(Color.WHITE); elevation = dp(10).toFloat()
        }
        root.addView(header)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(nav)
        setContentView(root)
    }

    private fun startSession() {
        setBusy(true)
        thread {
            var result: IzinApi.LoginResult? = null
            var message: String? = null
            try { if (session.cookie.isNotBlank()) result = IzinApi.bootstrap(session.cookie) } catch (_: Exception) { session.clear() }
            if (result == null) {
                val okulum = OkulumSession(this)
                if (okulum.username.isNotBlank() && okulum.password.isNotBlank()) {
                    try { result = IzinApi.login(okulum.username, okulum.password) } catch (e: Exception) { message = e.message }
                } else message = "ELAK Okulum kişisel oturum bilgisi henüz alınamadı."
            }
            runOnUiThread {
                setBusy(false)
                if (result != null) { session.save(result!!); onSessionReady() }
                else showAutoLoginProblem(message)
            }
        }
    }

    private fun showAutoLoginProblem(message: String?) {
        nav.removeAllViews(); navButtons.clear()
        titleText.text = "İzin Takip"
        userText.text = "ELAK Okulum ile otomatik oturum"
        val page = pageColumn()
        val box = card(accent = orange)
        box.addView(bigIcon("🔐", orange))
        box.addView(cardHeading("Otomatik giriş tamamlanamadı"))
        box.addView(bodyText(message ?: "İzin Takip oturumu açılamadı."))
        box.addView(bodyText("Bu ekranda ayrıca kullanıcı adı veya şifre istenmez. ELAK Okulum ana girişinde kişisel kullanıcı hesabınızla bir kez giriş yapmanız yeterlidir."))
        val retry = primaryButton("Tekrar Dene", blue)
        val back = secondaryButton("ELAK Ana Sayfaya Dön", navy)
        box.addView(space(12)); box.addView(retry); box.addView(space(8)); box.addView(back)
        retry.setOnClickListener { startSession() }
        back.setOnClickListener { finish() }
        page.addView(box)
        showContent(wrapScroll(page))
    }

    private fun onSessionReady() {
        titleText.text = "İzin Takip"
        userText.text = "${session.fullName.ifBlank { session.username }}  •  ${roleLabel()}"
        buildNavigation()
        if (session.role == "security") showSecurity() else showDashboard()
    }

    private fun buildNavigation() {
        nav.removeAllViews(); navButtons.clear()
        if (session.role == "admin") {
            addNav("home", "⌂", "Ana") { showDashboard() }
            if (can("students_view")) addNav("students", "♟", "Öğrenci") { showStudents() }
            if (can("permission_create")) addNav("new", "+", "Yeni İzin") { showNewPermission() }
            if (can("security_view")) addNav("security", "✓", "Güvenlik") { showSecurity() }
            if (can("permissions_view")) addNav("records", "≡", "Kayıtlar") { showPermissions() }
        } else {
            addNav("security", "✓", "Güvenlik") { showSecurity() }
            addNav("refresh", "↻", "Yenile") { showSecurity() }
        }
        selectNav(currentNav)
    }

    private fun addNav(key: String, icon: String, label: String, action: () -> Unit) {
        val b = TextView(this).apply {
            text = "$icon\n$label"; gravity = Gravity.CENTER; textSize = 10.5f; setTextColor(muted)
            setPadding(dp(3), dp(4), dp(3), dp(4)); background = rounded(Color.TRANSPARENT, 12)
            setOnClickListener { currentNav = key; selectNav(key); action() }
        }
        navButtons[key] = b
        nav.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f))
    }

    private fun selectNav(key: String) {
        navButtons.forEach { (k, b) ->
            val selected = k == key
            b.setTextColor(if (selected) blue else muted)
            b.background = rounded(if (selected) Color.rgb(235, 242, 255) else Color.TRANSPARENT, 12)
            b.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun showDashboard() {
        currentNav = "home"; selectNav(currentNav); titleText.text = "İzin Takip • Ana Sayfa"; setBusy(true)
        thread {
            try { val d = IzinApi.dashboard(session); runOnUiThread { setBusy(false); renderDashboard(d) } }
            catch (e: Exception) { runOnUiThread { handleError(e) } }
        }
    }

    private fun renderDashboard(d: JSONObject) {
        val stats = d.optJSONObject("stats") ?: JSONObject()
        val pending = d.optJSONArray("pending") ?: JSONArray()
        val exits = d.optJSONArray("exits") ?: JSONArray()
        val trend = d.optJSONArray("trend") ?: JSONArray()
        val classes = d.optJSONArray("classes") ?: JSONArray()
        val page = pageColumn()

        page.addView(hero("Günlük Durum", "İzin, çıkış ve dönüş hareketleri anlık olarak güncellenir."))
        page.addView(statGrid(arrayOf(
            Triple("Bugünkü İzin", stats.optInt("today"), blue), Triple("Dışarıda", stats.optInt("outside"), orange),
            Triple("Çıkış Bekleyen", stats.optInt("pending"), purple), Triple("Bugün Dönen", stats.optInt("returned_today"), green),
            Triple("Aktif Öğrenci", stats.optInt("students"), cyan), Triple("Bildirim Bekleyen", stats.optInt("notification_pending"), red)
        )))

        page.addView(sectionTitle("Çıkış Bekleyenler", "Güvenliğin işlem yapmasını bekleyen son izinler"))
        if (pending.length() == 0) page.addView(emptyCard("✓", "Bekleyen izin yok", "Şu anda çıkış bekleyen öğrenci bulunmuyor."))
        else for (i in 0 until minOf(pending.length(), 6)) page.addView(permissionInfoCard(pending.optJSONObject(i), "ÇIKIŞ BEKLİYOR", purple))

        page.addView(sectionTitle("Son Hareketler", "En son gerçekleşen okul çıkışları"))
        if (exits.length() == 0) page.addView(emptyCard("↔", "Hareket yok", "Henüz çıkış hareketi bulunmuyor."))
        else for (i in 0 until minOf(exits.length(), 6)) {
            val p = exits.optJSONObject(i)
            val status = p?.optString("status", "") ?: ""
            page.addView(permissionInfoCard(p, status, statusColor(status)))
        }

        if (trend.length() > 0) {
            page.addView(sectionTitle("Son 7 Gün", "Günlük izin yoğunluğu"))
            val box = card(); var max = 1
            for (i in 0 until trend.length()) max = maxOf(max, trend.optJSONObject(i)?.optInt("total") ?: 0)
            for (i in 0 until trend.length()) {
                val x = trend.optJSONObject(i) ?: continue
                box.addView(progressLine(x.optString("label"), x.optInt("total"), max, blue))
            }
            page.addView(box)
        }
        if (classes.length() > 0) {
            page.addView(sectionTitle("Öğrenci Dağılımı", "Aktif öğrencilerin sınıf düzeylerine göre dağılımı"))
            val box = card(); var max = 1
            for (i in 0 until classes.length()) max = maxOf(max, classes.optJSONObject(i)?.optInt("total") ?: 0)
            val colors = intArrayOf(blue, cyan, green, orange, purple, red)
            for (i in 0 until classes.length()) {
                val x = classes.optJSONObject(i) ?: continue
                box.addView(progressLine(x.optString("label"), x.optInt("total"), max, colors[i % colors.size]))
            }
            page.addView(box)
        }
        showContent(wrapScroll(page))
    }

    private fun showStudents() {
        currentNav = "students"; selectNav(currentNav); titleText.text = "İzin Takip • Öğrenciler"
        val page = pageColumn(); page.addView(hero("Öğrenciler", "Veli ve izin bilgileriyle birlikte hızlı öğrenci arama"))
        val search = input("Ad, soyad, okul no veya sınıf")
        val button = primaryButton("Öğrenci Ara", blue)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(search); page.addView(space(8)); page.addView(button); page.addView(space(12)); page.addView(list)
        showContent(wrapScroll(page))
        fun load() {
            list.removeAllViews(); list.addView(emptyText("Yükleniyor…"))
            thread {
                try {
                    val arr = IzinApi.students(session, search.text.toString().trim())
                    runOnUiThread {
                        list.removeAllViews()
                        if (arr.length() == 0) list.addView(emptyCard("🔎", "Öğrenci bulunamadı", "Arama ölçütlerini değiştirin."))
                        for (i in 0 until minOf(arr.length(), 120)) list.addView(studentCard(arr.optJSONObject(i), false))
                    }
                } catch(e:Exception) { runOnUiThread { list.removeAllViews(); list.addView(emptyText(e.message ?: "Öğrenciler yüklenemedi.")) } }
            }
        }
        button.setOnClickListener { load() }; load()
    }

    private fun showNewPermission() {
        currentNav = "new"; selectNav(currentNav); titleText.text = "İzin Takip • Yeni İzin"; selectedStudent = null
        val page = pageColumn(); page.addView(hero("Yeni İzin", "Öğrenciyi seçin, veli ve teslim bilgilerini kontrol ederek izni oluşturun."))
        val search = input("Öğrenci adı, okul no veya sınıf")
        val searchButton = primaryButton("Öğrenci Ara", blue)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val selectedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val reason = input("İzin nedeni (örn. Hastane)")
        val receiver = input("Teslim alan kişi")
        val approval = modernSpinner(listOf("Veli onay şekli", "Telefon", "SMS", "WhatsApp", "Dilekçe", "Yüz Yüze"))
        val sameDay = modernSpinner(listOf("Aynı gün dönüş?", "Evet, aynı gün dönecek", "Hayır, dönüş yapmayacak"))
        val note = input("Açıklama / not").apply { minLines = 3; gravity = Gravity.TOP }
        val save = primaryButton("İZNİ OLUŞTUR", green)
        page.addView(search); page.addView(space(8)); page.addView(searchButton); page.addView(space(10)); page.addView(results); page.addView(selectedBox)
        page.addView(space(12)); page.addView(formLabel("İzin Bilgileri")); page.addView(reason); page.addView(space(8)); page.addView(receiver); page.addView(space(8)); page.addView(approval); page.addView(space(8)); page.addView(sameDay); page.addView(space(8)); page.addView(note); page.addView(space(14)); page.addView(save)
        showContent(wrapScroll(page))

        searchButton.setOnClickListener {
            val q = search.text.toString().trim(); if(q.isBlank()){ toast("Öğrenci arama bilgisi girin."); return@setOnClickListener }
            results.removeAllViews(); results.addView(emptyText("Aranıyor…"))
            thread {
                try {
                    val arr=IzinApi.students(session,q)
                    runOnUiThread {
                        results.removeAllViews(); if(arr.length()==0) results.addView(emptyText("Öğrenci bulunamadı."))
                        for(i in 0 until minOf(arr.length(),40)){
                            val s=arr.optJSONObject(i)?:continue; val item=studentCard(s,true)
                            item.setOnClickListener {
                                selectedStudent=s; selectedBox.removeAllViews(); selectedBox.addView(selectedStudentCard(s)); results.removeAllViews()
                                receiver.setText(s.optString("authorized_person").ifBlank { s.optString("parent_name") })
                            }
                            results.addView(item)
                        }
                    }
                } catch(e:Exception){ runOnUiThread { results.removeAllViews(); results.addView(emptyText(e.message ?: "Arama yapılamadı.")) } }
            }
        }
        save.setOnClickListener {
            val s=selectedStudent ?: run { toast("Önce öğrenci seçin."); return@setOnClickListener }
            if(reason.text.toString().trim().isBlank()){ toast("İzin nedenini yazın."); return@setOnClickListener }
            if(approval.selectedItemPosition==0 || sameDay.selectedItemPosition==0){ toast("Veli onayı ve dönüş bilgisini seçin."); return@setOnClickListener }
            save.isEnabled=false
            thread {
                try {
                    val r=IzinApi.createPermission(session,s.optLong("id"),reason.text.toString().trim(),receiver.text.toString().trim(),approval.selectedItem.toString(),sameDay.selectedItemPosition==1,note.text.toString().trim())
                    runOnUiThread { save.isEnabled=true; toast(r.optString("message","İzin oluşturuldu.")); showDashboard() }
                } catch(e:Exception){ runOnUiThread { save.isEnabled=true; toast(e.message ?: "İzin oluşturulamadı.") } }
            }
        }
    }

    private fun showSecurity() {
        currentNav="security"; selectNav(currentNav); titleText.text="İzin Takip • Güvenlik"; setBusy(true)
        thread { try { val d=IzinApi.securityQueue(session); runOnUiThread { setBusy(false); renderSecurity(d) } } catch(e:Exception){ runOnUiThread { handleError(e) } } }
    }

    private fun renderSecurity(d:JSONObject){
        val waiting=d.optJSONArray("waiting")?:JSONArray(); val returning=d.optJSONArray("returning")?:JSONArray(); val page=pageColumn()
        page.addView(hero("Güvenlik Paneli", "Öğrenci çıkış ve dönüş işlemlerini tek dokunuşla tamamlayın."))
        page.addView(countBanner("Çıkış Bekleyen", waiting.length(), purple)); page.addView(space(8))
        if(waiting.length()==0) page.addView(emptyCard("✓","Çıkış bekleyen yok","Yeni izinler burada görünecek."))
        for(i in 0 until waiting.length()) page.addView(securityCard(waiting.optJSONObject(i),true))
        page.addView(sectionTitle("Dönüş Bekleyenler", "Aynı gün dönüş yapacak öğrenciler")); page.addView(countBanner("Dışarıda / dönüş bekleyen", returning.length(), orange)); page.addView(space(8))
        if(returning.length()==0) page.addView(emptyCard("↩","Dönüş bekleyen yok","Aynı gün dönüşlü öğrenciler burada görünecek."))
        for(i in 0 until returning.length()) page.addView(securityCard(returning.optJSONObject(i),false))
        showContent(wrapScroll(page))
    }

    private fun showPermissions(){
        currentNav="records"; selectNav(currentNav); titleText.text="İzin Takip • Kayıtlar"
        val page=pageColumn(); page.addView(hero("İzin Kayıtları","İzin, çıkış, dönüş ve bildirim ayrıntılarını görüntüleyin."))
        val search=input("Öğrenci, sınıf, neden veya izin veren"); val filter=modernSpinner(listOf("Tüm durumlar","Onaylandı","Dışarıda","Döndü")); val button=primaryButton("Kayıtları Listele",blue); val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        page.addView(search); page.addView(space(8)); page.addView(filter); page.addView(space(8)); page.addView(button); page.addView(space(12)); page.addView(list); showContent(wrapScroll(page))
        fun load(){
            list.removeAllViews(); list.addView(emptyText("Yükleniyor…")); val status=if(filter.selectedItemPosition==0)"" else filter.selectedItem.toString()
            thread {
                try {
                    val arr=IzinApi.permissions(session,search.text.toString().trim(),status)
                    runOnUiThread {
                        list.removeAllViews(); if(arr.length()==0) list.addView(emptyCard("📋","Kayıt bulunamadı","Filtreyi veya arama ölçütünü değiştirin."))
                        for(i in 0 until arr.length()) list.addView(recordCard(arr.optJSONObject(i)))
                    }
                } catch(e:Exception){ runOnUiThread { list.removeAllViews(); list.addView(emptyText(e.message ?: "Kayıtlar yüklenemedi.")) } }
            }
        }
        button.setOnClickListener{load()}; load()
    }

    private fun studentCard(s:JSONObject?, selectable:Boolean):View{
        if(s==null)return emptyText("Öğrenci okunamadı.")
        val accent=if(s.optInt("is_outside")==1) orange else blue; val box=card(accent=accent)
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(initials(s.optString("full_name"), accent))
        val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)}
        info.addView(cardHeading(s.optString("full_name"))); info.addView(bodyText("${s.optString("class_name")}  •  No: ${s.optString("student_no")}"))
        head.addView(info,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); if(selectable) head.addView(badge("SEÇ",blue)) else if(s.optInt("is_outside")==1) head.addView(badge("DIŞARIDA",orange))
        box.addView(head)
        if(s.has("parent_name")){
            box.addView(divider()); box.addView(detailRow("Veli",s.optString("parent_name","-"))); box.addView(phoneRow(s.optString("parent_phone")))
            box.addView(detailRow("Teslim Yetkilisi",s.optString("authorized_person","-").ifBlank{"-"})); box.addView(detailRow("Okul",if(s.optString("school_level")=="high")"Lise" else "Ortaokul"))
        }
        return box
    }

    private fun selectedStudentCard(s:JSONObject):View{
        val box=card(accent=green); box.addView(TextView(this).apply{text="✓  Seçilen Öğrenci";textSize=12f;setTextColor(green);setTypeface(typeface,Typeface.BOLD)})
        box.addView(cardHeading(s.optString("full_name"))); box.addView(bodyText("${s.optString("class_name")} • No: ${s.optString("student_no")}")); box.addView(detailRow("Veli",s.optString("parent_name","-"))); box.addView(phoneRow(s.optString("parent_phone"))); box.addView(detailRow("Teslim Yetkilisi",s.optString("authorized_person","-").ifBlank{"-"})); return box
    }

    private fun securityCard(p:JSONObject?, exit:Boolean):View{
        if(p==null)return emptyText("Kayıt okunamadı.")
        val accent=if(exit) purple else orange; val box=card(accent=accent)
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(initials(p.optString("full_name"),accent)); val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)}
        info.addView(cardHeading(p.optString("full_name"))); info.addView(bodyText("${p.optString("class_name")} • No: ${p.optString("student_no")}")); head.addView(info,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); head.addView(badge(if(exit)"BEKLİYOR" else "DIŞARIDA",accent)); box.addView(head)
        box.addView(divider()); box.addView(detailRow("Neden",p.optString("reason","-"))); box.addView(detailRow("Veli",p.optString("parent_name","-"))); box.addView(phoneRow(p.optString("parent_phone"))); box.addView(detailRow("Teslim",p.optString("receiver",p.optString("authorized_person","-")).ifBlank{"-"})); box.addView(detailRow("Veli Onayı",p.optString("approval_method","-"))); box.addView(detailRow("Aynı Gün Dönüş",if(p.optInt("same_day_return")==1)"Evet" else "Hayır")); box.addView(detailRow("İzin Veren",p.optString("approved_name","-"))); box.addView(detailRow("İzin Zamanı",shortDate(p.optString("approved_at")))); if(!exit) box.addView(detailRow("Çıkış",shortDate(p.optString("exited_at")))); if(p.optString("note").isNotBlank()) box.addView(detailRow("Not",p.optString("note")))
        if(canSecurityProcess()){
            val b=primaryButton(if(exit)"ÖĞRENCİYE ÇIKIŞ VER" else "DÖNÜŞÜ KAYDET",if(exit) orange else green)
            b.setOnClickListener{ confirm(if(exit)"Öğrenciye çıkış verilsin mi?" else "Öğrencinin dönüşü kaydedilsin mi?"){ runAction({if(exit)IzinApi.securityExit(session,p.optLong("id")) else IzinApi.securityReturn(session,p.optLong("id"))}){showSecurity()} } }
            box.addView(space(10)); box.addView(b)
        }
        return box
    }

    private fun permissionInfoCard(p:JSONObject?,label:String,accent:Int):View{
        if(p==null)return emptyText("Kayıt okunamadı.")
        val box=card(accent=accent); val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(initials(p.optString("full_name"),accent)); val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)}
        info.addView(cardHeading(p.optString("full_name"))); info.addView(bodyText("${p.optString("class_name")} • No: ${p.optString("student_no")}")); head.addView(info,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); if(label.isNotBlank())head.addView(badge(label,accent)); box.addView(head); box.addView(divider()); box.addView(detailRow("Neden",p.optString("reason","-"))); box.addView(detailRow("Teslim",p.optString("receiver","-").ifBlank{"-"})); box.addView(detailRow("İzin Veren",p.optString("approved_name","-"))); box.addView(detailRow("İzin",shortDate(p.optString("approved_at")))); if(p.optString("exited_at").isNotBlank())box.addView(detailRow("Çıkış",shortDate(p.optString("exited_at")))); if(p.optString("parent_phone").isNotBlank())box.addView(phoneRow(p.optString("parent_phone"))); return box
    }

    private fun recordCard(p:JSONObject?):View{
        if(p==null)return emptyText("Kayıt okunamadı.")
        val status=p.optString("status"); val accent=statusColor(status); val box=card(accent=accent)
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}; head.addView(initials(p.optString("full_name"),accent)); val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)}; info.addView(cardHeading(p.optString("full_name"))); info.addView(bodyText("${p.optString("class_name")} • No: ${p.optString("student_no")}")); head.addView(info,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); head.addView(badge(status.ifBlank{"-"},accent)); box.addView(head); box.addView(divider())
        box.addView(detailRow("Neden",p.optString("reason","-"))); box.addView(detailRow("Teslim Alan",p.optString("receiver","-").ifBlank{"-"})); box.addView(detailRow("Onay Yöntemi",p.optString("approval_method","-"))); box.addView(detailRow("Aynı Gün Dönüş",if(p.optInt("same_day_return")==1)"Evet" else "Hayır")); box.addView(detailRow("İzin Veren",joinUser(p,"approved_name","approved_username"))); box.addView(detailRow("İzin Zamanı",shortDate(p.optString("approved_at")))); if(p.optString("exit_name").isNotBlank())box.addView(detailRow("Çıkış İşlemi",joinUser(p,"exit_name","exit_username")+" • "+shortDate(p.optString("exited_at")))); if(p.optString("return_name").isNotBlank())box.addView(detailRow("Dönüş İşlemi",joinUser(p,"return_name","return_username")+" • "+shortDate(p.optString("returned_at")))); box.addView(detailRow("Veli Bildirimi",p.optString("notification_status","-"))); if(p.optString("note").isNotBlank())box.addView(detailRow("Not",p.optString("note")))
        if(can("permission_cancel")&&(status=="Onaylandı"||status=="Dışarıda")){val b=secondaryButton("İzni İptal Et",red);b.setOnClickListener{confirm("İzin iptal edilsin mi?"){runAction({IzinApi.cancelPermission(session,p.optLong("id"))}){showPermissions()}}};box.addView(space(8));box.addView(b)}
        return box
    }

    private fun phoneRow(phone:String):View{
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(3),0,dp(3))}
        row.addView(TextView(this).apply{text="Veli Telefon";textSize=12f;setTextColor(muted)},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        val value=phone.ifBlank{"-"}; val t=TextView(this).apply{text=value;textSize=12.5f;setTextColor(if(phone.isBlank())text else blue);setTypeface(typeface,Typeface.BOLD)}
        if(phone.isNotBlank())t.setOnClickListener{dial(phone)}; row.addView(t)
        if(phone.isNotBlank()){ val wa=TextView(this).apply{text="  WhatsApp";textSize=11.5f;setTextColor(green);setTypeface(typeface,Typeface.BOLD);setPadding(dp(8),0,0,0);setOnClickListener{whatsapp(phone)}};row.addView(wa) }
        return row
    }

    private fun countBanner(label:String,count:Int,color:Int):View=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(10));background=rounded(tint(color,0.10f),12);addView(TextView(this@IzinActivityModern).apply{text=label;textSize=13f;setTextColor(color);setTypeface(typeface,Typeface.BOLD)},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));addView(TextView(this@IzinActivityModern).apply{text=count.toString();textSize=20f;setTextColor(color);setTypeface(typeface,Typeface.BOLD)})}

    private fun hero(title:String,sub:String):View=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14));background=gradient(Color.rgb(30,64,175),Color.rgb(6,148,162),16);addView(TextView(this@IzinActivityModern).apply{text=title;textSize=21f;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD)});addView(TextView(this@IzinActivityModern).apply{text=sub;textSize=12.5f;setTextColor(Color.rgb(224,242,254));setPadding(0,dp(4),0,0)});layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=dp(14)}}

    private fun statGrid(items:Array<Triple<String,Int,Int>>):View{
        val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}; var i=0
        while(i<items.size){ val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; for(j in 0..1){ if(i+j<items.size){ val x=items[i+j]; row.addView(statCard(x.first,x.second,x.third),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f).apply{if(j==0)marginEnd=dp(5) else marginStart=dp(5)}) } }; col.addView(row,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=dp(10)}); i+=2 }
        return col
    }
    private fun statCard(label:String,value:Int,color:Int):View=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(12),dp(13),dp(12));background=rounded(Color.WHITE,14,border);elevation=dp(1).toFloat();addView(TextView(this@IzinActivityModern).apply{text=label;textSize=11.5f;setTextColor(muted)});addView(TextView(this@IzinActivityModern).apply{text=value.toString();textSize=26f;setTextColor(color);setTypeface(typeface,Typeface.BOLD)})}

    private fun progressLine(label:String,value:Int,max:Int,color:Int):View{
        val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(5),0,dp(5))}; val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        top.addView(TextView(this).apply{text=label;textSize=12f;setTextColor(text)},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));top.addView(TextView(this).apply{text=value.toString();textSize=12f;setTextColor(color);setTypeface(typeface,Typeface.BOLD)});row.addView(top)
        val track=FrameLayout(this).apply{background=rounded(Color.rgb(235,239,246),6)};val fill=View(this).apply{background=rounded(color,6)};track.addView(fill,FrameLayout.LayoutParams(dp(20),dp(7)))
        track.post{fill.layoutParams=fill.layoutParams.apply{width=maxOf(dp(8),(track.width*value/max.toFloat()).toInt())}}
        row.addView(track,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(7)).apply{topMargin=dp(5)});return row
    }

    private fun runAction(block:()->JSONObject,after:()->Unit){setBusy(true);thread{try{val j=block();runOnUiThread{setBusy(false);toast(j.optString("message","İşlem tamamlandı."));after()}}catch(e:Exception){runOnUiThread{handleError(e)}}}}
    private fun handleError(e:Exception){setBusy(false);val m=e.message?:"İşlem tamamlanamadı.";if(m.contains("oturumu sona erdi",true)){session.clear();startSession()}else toast(m)}
    private fun can(permission:String):Boolean=session.role=="admin"&&(session.adminAccess=="full"||permission in session.permissions)
    private fun canSecurityProcess():Boolean=session.role=="security"||can("security_process")
    private fun roleLabel():String=when{session.role=="security"->"Güvenlik";session.adminAccess=="full"->"Tam Yetkili Yönetici";else->"Yetkili Yönetici"}
    private fun statusColor(s:String):Int=when(s){"Dışarıda"->orange;"Döndü"->green;"Onaylandı"->purple;"İptal"->red;else->blue}
    private fun joinUser(p:JSONObject,name:String,user:String):String{val n=p.optString(name,"-");val u=p.optString(user);return if(u.isBlank())n else "$n ($u)"}
    private fun shortDate(v:String):String{if(v.isBlank())return "-";return if(v.length>=16) v.substring(8,10)+"."+v.substring(5,7)+"."+v.substring(0,4)+" "+v.substring(11,16) else v}

    private fun showContent(view:View){content.removeAllViews();content.addView(view,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));content.addView(loading,FrameLayout.LayoutParams(dp(44),dp(44),Gravity.CENTER));loading.bringToFront()}
    private fun setBusy(v:Boolean){loading.visibility=if(v)View.VISIBLE else View.GONE;loading.bringToFront()}
    private fun pageColumn():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(24))}
    private fun wrapScroll(child:View):ScrollView=ScrollView(this).apply{isFillViewport=true;addView(child)}
    private fun sectionTitle(title:String,sub:String):View=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(2),dp(13),dp(2),dp(9));addView(TextView(this@IzinActivityModern).apply{text=title;textSize=18f;setTextColor(text);setTypeface(typeface,Typeface.BOLD)});addView(TextView(this@IzinActivityModern).apply{text=sub;textSize=12f;setTextColor(muted);setPadding(0,dp(2),0,0)})}
    private fun card(accent:Int?=null):LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(12),dp(13),dp(12));background=rounded(Color.WHITE,14,border);elevation=dp(1).toFloat();layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=dp(9)};if(accent!=null){val bar=View(this@IzinActivityModern).apply{background=rounded(accent,3)};addView(bar,LinearLayout.LayoutParams(dp(34),dp(4)).apply{bottomMargin=dp(8)})}}
    private fun cardHeading(v:String):TextView=TextView(this).apply{text=v;textSize=15.5f;setTextColor(text);setTypeface(typeface,Typeface.BOLD)}
    private fun bodyText(v:String):TextView=TextView(this).apply{text=v;textSize=12.5f;setTextColor(muted);setPadding(0,dp(3),0,0)}
    private fun detailRow(label:String,value:String):View=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(3),0,dp(3));addView(TextView(this@IzinActivityModern).apply{text=label;textSize=12f;setTextColor(muted)},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,0.42f));addView(TextView(this@IzinActivityModern).apply{text=value.ifBlank{"-"};textSize=12.5f;setTextColor(text);setTypeface(typeface,Typeface.BOLD)},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,0.58f))}
    private fun divider():View=View(this).apply{setBackgroundColor(Color.rgb(237,241,247));layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1)).apply{topMargin=dp(9);bottomMargin=dp(7)}}
    private fun initials(name:String,color:Int):TextView=TextView(this).apply{val p=name.trim().split(Regex("\\s+")).filter{it.isNotBlank()};text=(p.firstOrNull()?.take(1).orEmpty()+p.drop(1).lastOrNull()?.take(1).orEmpty()).uppercase();gravity=Gravity.CENTER;textSize=14f;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);background=rounded(color,22);layoutParams=LinearLayout.LayoutParams(dp(44),dp(44))}
    private fun badge(v:String,color:Int):TextView=TextView(this).apply{text=v;textSize=9.5f;setTextColor(color);setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;setPadding(dp(8),dp(5),dp(8),dp(5));background=rounded(tint(color,.11f),20)}
    private fun formLabel(v:String):TextView=TextView(this).apply{text=v;textSize=14f;setTextColor(text);setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(4),0,dp(7))}
    private fun input(h:String):EditText=EditText(this).apply{hint=h;textSize=13.5f;setTextColor(text);setHintTextColor(Color.rgb(148,163,184));setPadding(dp(12),dp(11),dp(12),dp(11));background=rounded(Color.WHITE,11,border);layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)}
    private fun modernSpinner(items:List<String>):Spinner=Spinner(this).apply{adapter=ArrayAdapter(this@IzinActivityModern,android.R.layout.simple_spinner_dropdown_item,items);background=rounded(Color.WHITE,11,border);setPadding(dp(8),dp(4),dp(8),dp(4));layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50))}
    private fun primaryButton(label:String,color:Int):Button=Button(this).apply{text=label;isAllCaps=false;textSize=13.5f;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);background=rounded(color,11);layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48))}
    private fun secondaryButton(label:String,color:Int):Button=Button(this).apply{text=label;isAllCaps=false;textSize=13f;setTextColor(color);background=rounded(Color.WHITE,11,color);layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46))}
    private fun emptyCard(icon:String,title:String,sub:String):View=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(18),dp(20),dp(18),dp(20));background=rounded(Color.WHITE,14,border);addView(TextView(this@IzinActivityModern).apply{text=icon;textSize=24f;gravity=Gravity.CENTER});addView(TextView(this@IzinActivityModern).apply{text=title;textSize=14f;setTextColor(text);setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;setPadding(0,dp(6),0,0)});addView(TextView(this@IzinActivityModern).apply{text=sub;textSize=12f;setTextColor(muted);gravity=Gravity.CENTER;setPadding(0,dp(3),0,0)});layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=dp(9)}}
    private fun emptyText(v:String):TextView=TextView(this).apply{text=v;textSize=12.5f;setTextColor(muted);gravity=Gravity.CENTER;setPadding(dp(12),dp(20),dp(12),dp(20))}
    private fun bigIcon(v:String,color:Int):TextView=TextView(this).apply{text=v;textSize=30f;gravity=Gravity.CENTER;setTextColor(color);setPadding(0,dp(5),0,dp(9))}
    private fun space(h:Int):View=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(h))}
    private fun rounded(fill:Int,r:Int,stroke:Int?=null)=android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.RECTANGLE;setColor(fill);cornerRadius=dp(r).toFloat();if(stroke!=null)setStroke(dp(1),stroke)}
    private fun gradient(a:Int,b:Int,r:Int)=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(a,b)).apply{cornerRadius=dp(r).toFloat()}
    private fun tint(color:Int,alpha:Float):Int=Color.argb((255*alpha).toInt(),Color.red(color),Color.green(color),Color.blue(color))
    private fun confirm(m:String,action:()->Unit){AlertDialog.Builder(this).setMessage(m).setNegativeButton("Vazgeç",null).setPositiveButton("Onayla"){_,_->action()}.show()}
    private fun toast(m:String)=Toast.makeText(this,m,Toast.LENGTH_LONG).show()
    private fun dial(phone:String){try{startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:${phone.filter{it.isDigit()||it=='+'}}")))}catch(_:Exception){}}
    private fun whatsapp(phone:String){var p=phone.filter{it.isDigit()};if(p.startsWith("0"))p="90"+p.drop(1);try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/$p")))}catch(_:Exception){}}
    private fun dp(v:Int):Int=(v*resources.displayMetrics.density+.5f).toInt()
}
