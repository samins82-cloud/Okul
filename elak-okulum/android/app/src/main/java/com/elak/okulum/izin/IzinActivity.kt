package com.elak.okulum.izin

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
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

class IzinActivity : AppCompatActivity() {
    companion object { const val EXTRA_URL = "izin_url" }

    private val navy = Color.rgb(9, 30, 66)
    private val blue = Color.rgb(31, 102, 211)
    private val green = Color.rgb(18, 153, 103)
    private val orange = Color.rgb(234, 143, 31)
    private val red = Color.rgb(210, 54, 67)
    private val pageBg = Color.rgb(244, 247, 251)
    private val muted = Color.rgb(91, 105, 125)

    private lateinit var session: IzinSession
    private lateinit var root: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var userText: TextView
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var loading: ProgressBar
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
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(12))
            setBackgroundColor(navy)
        }
        titleText = TextView(this).apply {
            text = "ELAK • İzin Takip"
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        userText = TextView(this).apply {
            text = "Bağlanıyor…"
            textSize = 12.5f
            setTextColor(Color.rgb(201, 216, 238))
            setPadding(0, dp(4), 0, 0)
        }
        top.addView(titleText)
        top.addView(userText)
        content = FrameLayout(this).apply { setBackgroundColor(pageBg) }
        loading = ProgressBar(this).apply { visibility = View.GONE }
        content.addView(loading, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(7), dp(6), dp(7))
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        root.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(nav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun startSession() {
        setBusy(true)
        thread {
            var result: IzinApi.LoginResult? = null
            var error: String? = null
            try {
                if (session.cookie.isNotBlank()) result = IzinApi.bootstrap(session.cookie)
            } catch (_: Exception) {
                session.clear()
            }
            if (result == null) {
                val okulum = OkulumSession(this)
                if (okulum.username.isNotBlank() && okulum.password.isNotBlank()) {
                    try { result = IzinApi.login(okulum.username, okulum.password) }
                    catch (e: Exception) { error = e.message }
                }
            }
            runOnUiThread {
                setBusy(false)
                if (result != null) {
                    session.save(result!!)
                    onSessionReady()
                } else showLogin(error)
            }
        }
    }

    private fun showLogin(message: String? = null) {
        nav.removeAllViews()
        titleText.text = "ELAK • İzin Takip"
        userText.text = "Native Android uygulaması"
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }
        box.addView(sectionTitle("İzin Takip Girişi", "Kullanıcı bilgilerinizle güvenli oturum açın."))
        if (!message.isNullOrBlank()) box.addView(infoBox(message, red))
        val user = input("Kullanıcı adı")
        val pass = input("Şifre").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val button = primaryButton("Giriş Yap", blue)
        box.addView(user); box.addView(space(10)); box.addView(pass); box.addView(space(16)); box.addView(button)
        scroll.addView(box)
        showContent(scroll)
        button.setOnClickListener {
            val u = user.text.toString().trim()
            val p = pass.text.toString()
            if (u.isBlank() || p.isBlank()) { toast("Kullanıcı adı ve şifreyi girin."); return@setOnClickListener }
            setBusy(true)
            thread {
                try {
                    val login = IzinApi.login(u, p)
                    OkulumSession(this).saveCredentials(u, p)
                    session.save(login)
                    runOnUiThread { setBusy(false); onSessionReady() }
                } catch (e: Exception) {
                    runOnUiThread { setBusy(false); toast(e.message ?: "Giriş yapılamadı.") }
                }
            }
        }
    }

    private fun onSessionReady() {
        titleText.text = "ELAK • İzin Takip"
        userText.text = "${session.fullName.ifBlank { session.username }} • ${roleLabel()}"
        buildNavigation()
        if (session.role == "security") showSecurity() else showDashboard()
    }

    private fun buildNavigation() {
        nav.removeAllViews()
        if (session.role == "admin") {
            addNav("Ana", "⌂") { showDashboard() }
            if (can("permission_create")) addNav("Yeni İzin", "+") { showNewPermission() }
            if (can("security_view")) addNav("Güvenlik", "✓") { showSecurity() }
            if (can("permissions_view")) addNav("Kayıtlar", "≡") { showPermissions() }
        } else {
            addNav("Güvenlik", "✓") { showSecurity() }
            addNav("Yenile", "↻") { showSecurity() }
        }
        addNav("Çıkış", "⇥") { logout() }
    }

    private fun addNav(label: String, icon: String, action: () -> Unit) {
        val button = TextView(this).apply {
            text = "$icon\n$label"
            gravity = Gravity.CENTER
            textSize = 11.5f
            setTextColor(navy)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener { action() }
        }
        nav.addView(button, LinearLayout.LayoutParams(0, dp(54), 1f))
    }

    private fun showDashboard() {
        titleText.text = "İzin Takip • Ana Sayfa"
        setBusy(true)
        thread {
            try {
                val json = IzinApi.dashboard(session)
                runOnUiThread { setBusy(false); renderDashboard(json) }
            } catch (e: Exception) { runOnUiThread { handleError(e) } }
        }
    }

    private fun renderDashboard(json: JSONObject) {
        val stats = json.optJSONObject("stats") ?: JSONObject()
        val pending = json.optJSONArray("pending") ?: JSONArray()
        val page = pageColumn()
        page.addView(sectionTitle("Günlük Durum", "Okul izin hareketlerine genel bakış"))
        page.addView(statRow(statCard("Bugün", stats.optInt("today"), blue), statCard("Dışarıda", stats.optInt("outside"), orange)))
        page.addView(statRow(statCard("Bekleyen", stats.optInt("pending"), Color.rgb(109, 84, 181)), statCard("Dönen", stats.optInt("returned_today"), green)))
        page.addView(space(14))
        page.addView(sectionTitle("Çıkış Bekleyenler", "İdare tarafından izin verilen öğrenciler"))
        if (pending.length() == 0) page.addView(emptyText("Bekleyen izin yok."))
        else for (i in 0 until pending.length()) page.addView(permissionCard(pending.optJSONObject(i), false, false))
        showContent(wrapScroll(page))
    }

    private fun showNewPermission() {
        titleText.text = "İzin Takip • Yeni İzin"
        selectedStudent = null
        val page = pageColumn()
        page.addView(sectionTitle("Yeni İzin Oluştur", "Öğrenciyi adı, soyadı veya okul numarasıyla arayın."))
        val search = input("Öğrenci adı veya numarası")
        val searchButton = secondaryButton("Öğrenci Ara")
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val selected = infoBox("Henüz öğrenci seçilmedi.", muted)
        val reason = input("İzin nedeni")
        val receiver = input("Teslim alan kişi")
        val approval = Spinner(this).apply { adapter = ArrayAdapter(this@IzinActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Veli onay şekli", "Telefon", "SMS", "WhatsApp", "Dilekçe", "Yüz Yüze")) }
        val sameDay = Spinner(this).apply { adapter = ArrayAdapter(this@IzinActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Aynı gün dönüş?", "Evet, aynı gün dönecek", "Hayır, dönüş yapmayacak")) }
        val note = input("Açıklama").apply { minLines = 3; gravity = Gravity.TOP }
        val save = primaryButton("İzni Kaydet", green)
        page.addView(search); page.addView(space(8)); page.addView(searchButton); page.addView(space(10)); page.addView(results); page.addView(space(10)); page.addView(selected); page.addView(space(14)); page.addView(reason); page.addView(space(10)); page.addView(receiver); page.addView(space(10)); page.addView(approval, matchWrap()); page.addView(space(10)); page.addView(sameDay, matchWrap()); page.addView(space(10)); page.addView(note); page.addView(space(16)); page.addView(save)
        showContent(wrapScroll(page))

        searchButton.setOnClickListener {
            val q = search.text.toString().trim()
            if (q.isBlank()) { toast("Arama bilgisi girin."); return@setOnClickListener }
            results.removeAllViews(); results.addView(emptyText("Aranıyor…"))
            thread {
                try {
                    val arr = IzinApi.studentSearch(session, q)
                    runOnUiThread {
                        results.removeAllViews()
                        if (arr.length() == 0) results.addView(emptyText("Öğrenci bulunamadı."))
                        for (i in 0 until arr.length()) {
                            val student = arr.optJSONObject(i) ?: continue
                            val item = studentPickCard(student)
                            item.setOnClickListener {
                                selectedStudent = student
                                selected.text = "Seçilen: ${student.optString("full_name")} • ${student.optString("class_name")} • No: ${student.optString("student_no")}" + if (student.optInt("is_outside") == 1) " • DIŞARIDA" else ""
                                results.removeAllViews()
                            }
                            results.addView(item)
                        }
                    }
                } catch (e: Exception) { runOnUiThread { results.removeAllViews(); results.addView(emptyText(e.message ?: "Arama yapılamadı.")) } }
            }
        }

        save.setOnClickListener {
            val student = selectedStudent
            if (student == null) { toast("Önce öğrenci seçin."); return@setOnClickListener }
            if (reason.text.toString().trim().isBlank()) { toast("İzin nedenini yazın."); return@setOnClickListener }
            if (approval.selectedItemPosition == 0 || sameDay.selectedItemPosition == 0) { toast("Veli onayı ve dönüş bilgisini seçin."); return@setOnClickListener }
            save.isEnabled = false
            thread {
                try {
                    val json = IzinApi.createPermission(session, student.optLong("id"), reason.text.toString().trim(), receiver.text.toString().trim(), approval.selectedItem.toString(), sameDay.selectedItemPosition == 1, note.text.toString().trim())
                    runOnUiThread { save.isEnabled = true; toast(json.optString("message", "İzin kaydedildi.")); showPermissions() }
                } catch (e: Exception) { runOnUiThread { save.isEnabled = true; toast(e.message ?: "İzin kaydedilemedi.") } }
            }
        }
    }

    private fun showSecurity() {
        titleText.text = "İzin Takip • Güvenlik"
        setBusy(true)
        thread {
            try {
                val json = IzinApi.securityQueue(session)
                runOnUiThread { setBusy(false); renderSecurity(json) }
            } catch (e: Exception) { runOnUiThread { handleError(e) } }
        }
    }

    private fun renderSecurity(json: JSONObject) {
        val waiting = json.optJSONArray("waiting") ?: JSONArray()
        val returning = json.optJSONArray("returning") ?: JSONArray()
        val page = pageColumn()
        page.addView(sectionTitle("Çıkış Bekleyenler", "İzin verilmiş, henüz okuldan çıkmamış öğrenciler"))
        if (waiting.length() == 0) page.addView(emptyText("Çıkış bekleyen öğrenci yok."))
        for (i in 0 until waiting.length()) page.addView(permissionCard(waiting.optJSONObject(i), true, false))
        page.addView(space(16))
        page.addView(sectionTitle("Dönüş Bekleyenler", "Aynı gün dönecek öğrenciler"))
        if (returning.length() == 0) page.addView(emptyText("Dönüş bekleyen öğrenci yok."))
        for (i in 0 until returning.length()) page.addView(permissionCard(returning.optJSONObject(i), false, true))
        showContent(wrapScroll(page))
    }

    private fun showPermissions() {
        titleText.text = "İzin Takip • Kayıtlar"
        val page = pageColumn()
        val search = input("Öğrenci, sınıf veya izin nedeni ara")
        val filter = Spinner(this).apply { adapter = ArrayAdapter(this@IzinActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Tüm durumlar", "Onaylandı", "Dışarıda", "Döndü")) }
        val load = primaryButton("Listele", blue)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionTitle("İzin Kayıtları", "Aktif izinleri görüntüleyin ve yönetin.")); page.addView(search); page.addView(space(8)); page.addView(filter, matchWrap()); page.addView(space(8)); page.addView(load); page.addView(space(12)); page.addView(list)
        showContent(wrapScroll(page))
        fun reload() {
            list.removeAllViews(); list.addView(emptyText("Yükleniyor…"))
            val status = if (filter.selectedItemPosition == 0) "" else filter.selectedItem.toString()
            thread {
                try {
                    val arr = IzinApi.permissions(session, search.text.toString().trim(), status)
                    runOnUiThread {
                        list.removeAllViews()
                        if (arr.length() == 0) list.addView(emptyText("Kayıt bulunamadı."))
                        for (i in 0 until arr.length()) list.addView(permissionRecordCard(arr.optJSONObject(i)))
                    }
                } catch (e: Exception) { runOnUiThread { list.removeAllViews(); list.addView(emptyText(e.message ?: "Kayıtlar yüklenemedi.")) } }
            }
        }
        load.setOnClickListener { reload() }
        reload()
    }

    private fun permissionRecordCard(p: JSONObject?): View {
        if (p == null) return emptyText("Kayıt okunamadı.")
        val box = card(); val status = p.optString("status")
        box.addView(cardTitle(p.optString("full_name"), "${p.optString("class_name")} • No: ${p.optString("student_no")}"))
        box.addView(detail("Neden", p.optString("reason", "-"))); box.addView(detail("Durum", status)); box.addView(detail("Çıkış", p.optString("exited_at", "-").ifBlank { "-" })); box.addView(detail("Dönüş", p.optString("returned_at", "-").ifBlank { "-" }))
        if (can("permission_cancel") && (status == "Onaylandı" || status == "Dışarıda")) {
            val cancel = secondaryButton("İzni İptal Et", red)
            cancel.setOnClickListener { confirm("İzin iptal edilsin mi?") { runAction({ IzinApi.cancelPermission(session, p.optLong("id")) }) { showPermissions() } } }
            box.addView(space(8)); box.addView(cancel)
        }
        return box
    }

    private fun permissionCard(p: JSONObject?, allowExit: Boolean, allowReturn: Boolean): View {
        if (p == null) return emptyText("Kayıt okunamadı.")
        val box = card()
        box.addView(cardTitle(p.optString("full_name"), "${p.optString("class_name")} • No: ${p.optString("student_no")}")); box.addView(detail("İzin", p.optString("reason", "-"))); box.addView(detail("Teslim", p.optString("receiver", p.optString("authorized_person", "-")).ifBlank { "-" }))
        if (allowExit && canSecurityProcess()) {
            val b = primaryButton("Çıkış Ver", orange)
            b.setOnClickListener { confirm("Öğrenciye çıkış verilsin mi?") { runAction({ IzinApi.securityExit(session, p.optLong("id")) }) { showSecurity() } } }
            box.addView(space(8)); box.addView(b)
        }
        if (allowReturn && canSecurityProcess()) {
            val b = primaryButton("Dönüş Yap", green)
            b.setOnClickListener { confirm("Öğrencinin dönüşü kaydedilsin mi?") { runAction({ IzinApi.securityReturn(session, p.optLong("id")) }) { showSecurity() } } }
            box.addView(space(8)); box.addView(b)
        }
        return box
    }

    private fun studentPickCard(student: JSONObject): View {
        val box = card(); box.addView(cardTitle(student.optString("full_name"), "${student.optString("class_name")} • No: ${student.optString("student_no")}"))
        if (student.optInt("is_outside") == 1) box.addView(detail("Durum", "DIŞARIDA"))
        box.addView(TextView(this).apply { text = "Seç"; setTextColor(blue); setTypeface(typeface, Typeface.BOLD); textSize = 13f; gravity = Gravity.END })
        return box
    }

    private fun runAction(block: () -> JSONObject, after: () -> Unit) {
        setBusy(true)
        thread {
            try {
                val json = block()
                runOnUiThread { setBusy(false); toast(json.optString("message", "İşlem tamamlandı.")); after() }
            } catch (e: Exception) { runOnUiThread { handleError(e) } }
        }
    }

    private fun handleError(e: Exception) {
        setBusy(false)
        val message = e.message ?: "İşlem tamamlanamadı."
        if (message.contains("oturumu sona erdi", ignoreCase = true)) { session.clear(); showLogin(message) } else toast(message)
    }

    private fun logout() { session.clear(); showLogin("Oturum kapatıldı.") }
    private fun can(permission: String): Boolean = session.role == "admin" && (session.adminAccess == "full" || permission in session.permissions)
    private fun canSecurityProcess(): Boolean = session.role == "security" || can("security_process")
    private fun roleLabel(): String = when { session.role == "security" -> "Güvenlik"; session.adminAccess == "full" -> "Tam Yetkili Yönetici"; else -> "Yetkili Yönetici" }

    private fun showContent(view: View) {
        content.removeAllViews()
        content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(loading, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER))
        loading.bringToFront()
    }
    private fun setBusy(value: Boolean) { loading.visibility = if (value) View.VISIBLE else View.GONE; loading.bringToFront() }
    private fun pageColumn(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
    private fun wrapScroll(child: View): ScrollView = ScrollView(this).apply { addView(child) }

    private fun sectionTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(12))
        addView(TextView(this@IzinActivity).apply { text = title; textSize = 20f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD) })
        addView(TextView(this@IzinActivity).apply { text = subtitle; textSize = 13f; setTextColor(muted); setPadding(0, dp(3), 0, 0) })
    }
    private fun statRow(a: View, b: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        setPadding(0, 0, 0, dp(10))
    }
    private fun statCard(label: String, value: Int, accent: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(Color.WHITE, 14); elevation = dp(2).toFloat()
        addView(TextView(this@IzinActivity).apply { text = label; textSize = 12.5f; setTextColor(muted) })
        addView(TextView(this@IzinActivity).apply { text = value.toString(); textSize = 27f; setTextColor(accent); setTypeface(typeface, Typeface.BOLD) })
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); background = rounded(Color.WHITE, 14); elevation = dp(1).toFloat(); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
    }
    private fun cardTitle(title: String, sub: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@IzinActivity).apply { text = title; textSize = 16f; setTextColor(navy); setTypeface(typeface, Typeface.BOLD) })
        addView(TextView(this@IzinActivity).apply { text = sub; textSize = 12.5f; setTextColor(muted); setPadding(0, dp(2), 0, dp(7)) })
    }
    private fun detail(label: String, value: String): View = TextView(this).apply { text = "$label: $value"; textSize = 13f; setTextColor(Color.rgb(47, 58, 75)); setPadding(0, dp(2), 0, dp(2)) }
    private fun input(hintText: String): EditText = EditText(this).apply { hint = hintText; textSize = 14f; setTextColor(navy); setHintTextColor(Color.rgb(133, 145, 161)); setPadding(dp(12), dp(11), dp(12), dp(11)); background = rounded(Color.WHITE, 10, Color.rgb(215, 222, 232)); layoutParams = matchWrap() }
    private fun primaryButton(label: String, color: Int): Button = Button(this).apply { text = label; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE); background = rounded(color, 10); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)) }
    private fun secondaryButton(label: String, color: Int = blue): Button = Button(this).apply { text = label; isAllCaps = false; textSize = 13.5f; setTextColor(color); background = rounded(Color.WHITE, 10, color); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)) }
    private fun infoBox(textValue: String, accent: Int): TextView = TextView(this).apply { text = textValue; textSize = 13f; setTextColor(accent); setPadding(dp(12), dp(11), dp(12), dp(11)); background = rounded(Color.WHITE, 10, Color.rgb(220, 226, 235)) }
    private fun emptyText(textValue: String): TextView = TextView(this).apply { text = textValue; gravity = Gravity.CENTER; textSize = 13f; setTextColor(muted); setPadding(dp(12), dp(18), dp(12), dp(18)) }
    private fun space(height: Int): View = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null): android.graphics.drawable.GradientDrawable = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radiusDp).toFloat(); if (stroke != null) setStroke(dp(1), stroke) }
    private fun confirm(message: String, action: () -> Unit) { AlertDialog.Builder(this).setMessage(message).setNegativeButton("Vazgeç", null).setPositiveButton("Onayla") { _, _ -> action() }.show() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
