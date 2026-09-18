package com.elak.okulum.rehber

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class RehberActivity82 : AppCompatActivity() {
    private val navy = Color.rgb(10, 57, 102)
    private val blue = Color.rgb(27, 103, 166)
    private val brightBlue = Color.rgb(20, 137, 227)
    private val red = Color.rgb(226, 0, 0)
    private val green = Color.rgb(25, 178, 91)
    private val teal = Color.rgb(34, 183, 180)
    private val orange = Color.rgb(243, 147, 33)
    private val purple = Color.rgb(105, 72, 232)
    private val light = Color.rgb(247, 250, 253)
    private val soft = Color.rgb(238, 243, 249)
    private val ink = Color.rgb(9, 49, 88)
    private val muted = Color.rgb(111, 128, 149)
    private val motherColor = Color.rgb(185, 62, 119)
    private val fatherColor = Color.rgb(48, 134, 80)

    private val cache by lazy { CallerCache(this) }
    private val session by lazy { RehberSession(this) }

    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var back: TextView
    private lateinit var content: FrameLayout
    private lateinit var bottom: LinearLayout
    private val nav = linkedMapOf<String, Pair<ImageView, TextView>>()

    private var active = "home"
    private var guardianMode = false
    private var level = "middle"
    private var selectedClass = ""
    private var gridMode = false

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (active == "settings") showSettings() else showHome()
    }
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        buildShell()
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        RehberSyncWorker.schedule(this)
        requestNotificationPermissionIfNeeded()
        val openStudent = intent.getLongExtra("open_student_id", 0L)
        if (openStudent > 0) showStudentDetail(openStudent) else showHome()
        if (!session.token.isNullOrBlank()) syncNow(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val studentId = intent.getLongExtra("open_student_id", 0L)
        if (studentId > 0) showStudentDetail(studentId)
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(light)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        back = TextView(this).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(navy)
            visibility = View.GONE
            setOnClickListener { showHome() }
        }
        title = TextView(this).apply {
            text = "Akıllı Rehber"
            textSize = 21f
            setTextColor(ink)
            setTypeface(typeface, Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
        }
        val refresh = TextView(this).apply {
            text = "↻"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(navy)
            setOnClickListener { syncNow(true) }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(42), dp(50)))
        header.addView(title, LinearLayout.LayoutParams(0, dp(50), 1f))
        header.addView(refresh, LinearLayout.LayoutParams(dp(46), dp(50)))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)))

        content = FrameLayout(this).apply { setBackgroundColor(light) }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(10))
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
        }
        addNav("home", R.drawable.ic_rehber_home, "Ana Sayfa") { showHome() }
        addNav("directory", R.drawable.ic_rehber_people, "Rehber") { showDirectory(false) }
        addNav("calls", R.drawable.ic_rehber_call, "Aramalar") { showCalls() }
        addNav("ann", R.drawable.ic_rehber_announcement, "Duyurular") { showAnnouncements() }
        addNav("settings", R.drawable.ic_rehber_settings, "Ayarlar") { showSettings() }
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)))
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun addNav(key: String, iconRes: Int, label: String, action: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.rgb(132, 148, 164))
            setPadding(dp(5), dp(3), dp(5), 0)
        }
        val text = TextView(this).apply {
            this.text = label
            textSize = 9.5f
            setTextColor(Color.rgb(132, 148, 164))
            gravity = Gravity.CENTER
        }
        box.addView(icon, LinearLayout.LayoutParams(dp(33), dp(31)))
        box.addView(text, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)))
        nav[key] = icon to text
        bottom.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun selectNav(key: String) {
        active = key
        nav.forEach { (k, pair) ->
            val selected = k == key
            pair.first.imageTintList = ColorStateList.valueOf(if (selected) red else Color.rgb(132, 148, 164))
            pair.second.setTextColor(if (selected) red else Color.rgb(132, 148, 164))
            pair.second.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun setHeader(text: String, showBack: Boolean) {
        title.text = text
        back.visibility = if (showBack) View.VISIBLE else View.GONE
    }

    private fun showHome() {
        selectNav("home")
        setHeader("Akıllı Rehber", false)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(18))
        }
        body.addView(TextView(this).apply {
            text = "ELAK Mobil"
            textSize = 24f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        body.addView(TextView(this).apply {
            val p = cache.profileName().ifBlank { session.username.ifBlank { "ELAK Kullanıcısı" } }
            text = if (cache.profileRole().isBlank()) p else p + " · " + cache.profileRole()
            textSize = 11.5f
            setTextColor(muted)
            setPadding(0, dp(2), 0, dp(10))
        })
        body.addView(statusStrip())
        tileRow(body,
            tile(R.drawable.ic_rehber_people, "Öğrenci\nRehberi", cache.studentCount().toString() + " öğrenci", brightBlue) { showDirectory(false) },
            tile(R.drawable.ic_rehber_people, "Veli\nRehberi", cache.guardianCount().toString() + " veli", red) { showDirectory(true) })
        tileRow(body,
            tile(R.drawable.ic_rehber_class, "Sınıf\nListeleri", cache.classCount().toString() + " sınıf", teal) { showClasses() },
            tile(R.drawable.ic_rehber_announcement, "Duyurular", cache.listAnnouncements().size.toString() + " duyuru", orange) { showAnnouncements() })
        tileRow(body,
            tile(R.drawable.ic_rehber_call, "Aramalar", cache.listHistory(200).size.toString() + " arama", purple) { showCalls() },
            tile(R.drawable.ic_rehber_settings, "Ayarlar", "", Color.rgb(117, 140, 168)) { showSettings() })
        scroll.addView(body)
        replaceContent(scroll)
    }

    private fun tile(iconRes: Int, label: String, count: String, color: Int, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(lighten(color, 1.05f), darken(color, .87f))).apply { cornerRadius = dp(22).toFloat() }
            elevation = dp(3).toFloat()
            setOnClickListener { action() }
            addView(ImageView(this@RehberActivity82).apply { setImageResource(iconRes); imageTintList = ColorStateList.valueOf(Color.WHITE); setPadding(dp(4), dp(4), dp(4), dp(4)) }, LinearLayout.LayoutParams(dp(43), dp(43)))
            addView(TextView(this@RehberActivity82).apply { text = label; textSize = 15.5f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD) })
            if (count.isNotBlank()) addView(TextView(this@RehberActivity82).apply { text = count; textSize = 9.8f; gravity = Gravity.CENTER; setTextColor(Color.argb(230,255,255,255)); setPadding(0,dp(3),0,0) })
        }

    private fun tileRow(parent: LinearLayout, a: View, b: View) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(a, weightLp(1f, dp(4), dp(4)))
        row.addView(b, weightLp(1f, dp(4), dp(4)))
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)))
    }

    private fun statusStrip(): View {
        val last = if (session.lastSync > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastSync)) else "Henüz yok"
        val caller = if (isCallScreeningActive()) "Açık" else "Kapalı"
        return TextView(this).apply {
            text = "Arayan kimliği: $caller  ·  Telefon kaydı: ${cache.count()}  ·  Son eşitleme: $last"
            textSize = 10.2f
            setTextColor(muted)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.WHITE, dp(15).toFloat(), soft)
        }.also { it.layoutParams = marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(10)) }
    }

    private fun showDirectory(guardian: Boolean) {
        guardianMode = guardian
        selectNav("directory")
        setHeader(if (guardian) "Veli Rehberi" else "Öğrenci Rehberi", true)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), 0)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(Color.WHITE, dp(18).toFloat(), soft)
        }

        val levelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lateinit var middle: TextView
        lateinit var high: TextView
        fun updateLevelButtons() {
            middle.background = rounded(if (level == "middle") blue else Color.rgb(242,246,250), dp(13).toFloat(), soft)
            middle.setTextColor(if (level == "middle") Color.WHITE else ink)
            high.background = rounded(if (level == "high") blue else Color.rgb(242,246,250), dp(13).toFloat(), soft)
            high.setTextColor(if (level == "high") Color.WHITE else ink)
        }
        middle = TextView(this).apply {
            text = "Ortaokul 5–8"
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { level = "middle"; selectedClass = ""; updateLevelButtons(); showDirectory(guardian) }
        }
        high = TextView(this).apply {
            text = "Lise 9–12"
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { level = "high"; selectedClass = ""; updateLevelButtons(); showDirectory(guardian) }
        }
        levelRow.addView(middle, weightLp(1f, 0, dp(4)))
        levelRow.addView(high, weightLp(1f, dp(4), 0))
        controls.addView(levelRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        updateLevelButtons()

        val search = EditText(this).apply {
            hint = if (guardian) "Veli adı / öğrenci adı / no ara…" else "Öğrenci adı veya okul no ara…"
            textSize = 13.5f
            isSingleLine = true
            setPadding(dp(14), 0, dp(12), 0)
            background = rounded(Color.rgb(255,245,245), dp(15).toFloat(), Color.rgb(224,105,105))
        }
        controls.addView(search, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(53), 0, dp(9), 0, 0))

        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val classBox = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            setTextColor(Color.rgb(30,30,30))
            background = rounded(Color.rgb(245,248,251), dp(13).toFloat(), soft)
        }
        filterRow.addView(classBox, LinearLayout.LayoutParams(0, dp(48), 1f))
        val listBtn = TextView(this).apply {
            text = "☷"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(blue, dp(12).toFloat())
        }
        val gridBtn = TextView(this).apply {
            text = "▦"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(ink)
        }
        val waBtn = TextView(this).apply {
            text = "◉"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(teal, dp(12).toFloat())
        }
        filterRow.addView(listBtn, marginLp(dp(48), dp(48), dp(7), 0, 0, 0))
        filterRow.addView(gridBtn, marginLp(dp(48), dp(48), dp(5), 0, 0, 0))
        filterRow.addView(waBtn, marginLp(dp(48), dp(48), dp(5), 0, 0, 0))
        controls.addView(filterRow, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 0, dp(9), 0, 0))
        page.addView(controls)

        val listScroll = ScrollView(this).apply { isFillViewport = true }
        val listBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(18)) }
        listScroll.addView(listBody)
        page.addView(listScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun levelClasses(): List<String> = cache.listClasses().map { it.name }.filter { classMatchesLevel(it) }.distinct().sortedWith(compareBy({ gradeOf(it) }, { it }))
        fun refreshClassText() { classBox.text = if (selectedClass.isBlank()) "Tüm Sınıflar   ▾" else selectedClass + "   ▾" }
        refreshClassText()

        fun render() {
            listBody.removeAllViews()
            val query = search.text.toString().trim()
            if (!guardian) {
                val students = cache.listStudents(query, if (query.isBlank()) selectedClass else "")
                    .filter { classMatchesLevel(it.className) }
                if (students.isEmpty()) emptyState(listBody, "Öğrenci kaydı bulunamadı.")
                if (gridMode) renderStudentGrid(listBody, students) else students.forEach { listBody.addView(studentCard(it)) }
            } else {
                val guardians = cache.listGuardians(query, if (query.isBlank()) selectedClass else "")
                    .filter { classMatchesLevel(it.className) }
                val grouped = guardians.groupBy { it.studentId }.entries.sortedBy { it.value.firstOrNull()?.studentName.orEmpty() }
                if (grouped.isEmpty()) emptyState(listBody, "Veli kaydı bulunamadı.")
                grouped.forEach { entry -> listBody.addView(guardianStudentCard(entry.key, entry.value)) }
            }
        }

        classBox.setOnClickListener {
            val items = listOf("Tüm Sınıflar") + levelClasses()
            AlertDialog.Builder(this).setTitle("Sınıf Seç")
                .setItems(items.toTypedArray()) { _, which -> selectedClass = if (which == 0) "" else items[which]; refreshClassText(); render() }
                .show()
        }
        search.addTextChangedListener(SimpleTextWatcher { render() })
        listBtn.setOnClickListener {
            gridMode = false
            listBtn.background = rounded(blue, dp(12).toFloat()); listBtn.setTextColor(Color.WHITE)
            gridBtn.background = null; gridBtn.setTextColor(ink)
            render()
        }
        gridBtn.setOnClickListener {
            gridMode = true
            gridBtn.background = rounded(blue, dp(12).toFloat()); gridBtn.setTextColor(Color.WHITE)
            listBtn.background = null; listBtn.setTextColor(ink)
            render()
        }
        waBtn.setOnClickListener {
            val choices = if (selectedClass.isBlank()) cache.listClasses().filter { classMatchesLevel(it.name) && it.whatsappUrl.isNotBlank() } else cache.listClasses().filter { it.name == selectedClass && it.whatsappUrl.isNotBlank() }
            if (choices.isEmpty()) Toast.makeText(this, "Bu seçim için WhatsApp grup bağlantısı tanımlı değil.", Toast.LENGTH_LONG).show()
            else if (choices.size == 1) openUri(choices.first().whatsappUrl)
            else AlertDialog.Builder(this).setTitle("Sınıf WhatsApp Grupları").setItems(choices.map { it.name }.toTypedArray()) { _, i -> openUri(choices[i].whatsappUrl) }.show()
        }
        render()
        replaceContent(page)
    }

    private fun studentCard(s: CallerCache.Student): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.WHITE, dp(18).toFloat(), soft)
            setOnClickListener { showStudentDetail(s.id) }
        }
        card.addView(studentAvatar(s), LinearLayout.LayoutParams(dp(54), dp(54)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        info.addView(TextView(this).apply { text = s.name; textSize = 16f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) })
        val meta = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        meta.addView(TextView(this@RehberActivity82).apply { text = s.className; textSize = 10.8f; setTextColor(navy); setPadding(dp(8),dp(2),dp(8),dp(2)); background = rounded(Color.rgb(231,243,255), dp(10).toFloat()) })
        meta.addView(TextView(this@RehberActivity82).apply { text = "  No: " + s.schoolNo; textSize = 11f; setTextColor(muted) })
        info.addView(meta)
        info.addView(TextView(this).apply { text = "Öğrenci Tel: " + if (s.phone.isBlank()) "Eklenmemiş" else PhoneUtil.display(s.phone); textSize = 11f; setTextColor(muted); setPadding(0,dp(4),0,0) })
        card.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return wrapCard(card)
    }

    private fun renderStudentGrid(parent: LinearLayout, students: List<CallerCache.Student>) {
        var row: LinearLayout? = null
        students.forEachIndexed { i, s ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8),dp(10),dp(8),dp(10))
                background = rounded(Color.WHITE, dp(17).toFloat(), soft)
                setOnClickListener { showStudentDetail(s.id) }
                addView(studentAvatar(s), LinearLayout.LayoutParams(dp(52),dp(52)))
                addView(TextView(this@RehberActivity82).apply { text = s.name; textSize = 12f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setTextColor(ink); maxLines = 2 })
                addView(TextView(this@RehberActivity82).apply { text = s.className + " · No: " + s.schoolNo; textSize = 9.8f; gravity = Gravity.CENTER; setTextColor(muted) })
            }
            row?.addView(card, LinearLayout.LayoutParams(0, dp(140), 1f).apply { setMargins(dp(3),dp(3),dp(3),dp(3)) })
        }
        if (students.size % 2 == 1) row?.addView(View(this), LinearLayout.LayoutParams(0,dp(1),1f))
    }

    private fun guardianStudentCard(studentId: Long, guardians: List<CallerCache.Guardian>): View {
        val first = guardians.first()
        val student = cache.getStudent(studentId)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(12), dp(12), dp(10), dp(12))
            background = rounded(Color.WHITE, dp(18).toFloat(), soft)
            setOnClickListener { showStudentDetail(studentId) }
        }
        outer.addView(studentAvatar(student ?: CallerCache.Student(studentId, first.studentName, first.schoolNo, first.className, "", false,0,false)), LinearLayout.LayoutParams(dp(52), dp(52)))
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        main.addView(TextView(this).apply { text = first.studentName; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink) })
        val meta = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        meta.addView(TextView(this@RehberActivity82).apply { text = first.className; textSize = 10.8f; setTextColor(navy); setPadding(dp(7),dp(1),dp(7),dp(1)); background = rounded(Color.rgb(232,243,255),dp(9).toFloat()) })
        meta.addView(TextView(this@RehberActivity82).apply { text = "  •  No: " + first.schoolNo; textSize = 10.8f; setTextColor(muted) })
        main.addView(meta)
        guardians.sortedWith(compareBy({ relationOrder(it.relationship) }, { it.name })).forEach { g -> main.addView(guardianLine(g)) }
        outer.addView(main, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return wrapCard(outer)
    }

    private fun guardianLine(g: CallerCache.Guardian): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0,dp(7),0,0) }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(this).apply { text = relationLabel(g.relationship); textSize = 12f; setTypeface(typeface, Typeface.BOLD); setTextColor(relationColor(g.relationship)) })
        info.addView(TextView(this).apply { text = if (g.phone.isBlank()) "Telefon eklenmemiş" else displayInternational(g.phone); textSize = 11.3f; setTextColor(muted) })
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (g.phone.isNotBlank()) {
            row.addView(squareAction("☎", brightBlue) { dial(g.phone) })
            row.addView(squareAction("◉", green) { whatsapp(g.phone) })
        }
        return row
    }

    private fun studentAvatar(s: CallerCache.Student): View {
        val box = FrameLayout(this)
        val letter = TextView(this).apply {
            text = "E"
            textSize = 25f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(19,119,137), dp(27).toFloat())
        }
        val photo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = rounded(Color.TRANSPARENT,dp(27).toFloat()) }
        box.addView(letter, FrameLayout.LayoutParams(dp(54),dp(54)))
        box.addView(photo, FrameLayout.LayoutParams(dp(54),dp(54)))
        if (s.hasPhoto) loadPhoto(photo, s.id, s.photoVersion)
        return box
    }

    private fun wrapCard(v: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2),dp(4),dp(2),dp(4))
        addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun showStudentDetail(studentId: Long) {
        val s = cache.getStudent(studentId) ?: return
        val guardians = cache.guardiansForStudent(studentId)
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(18)); setBackgroundColor(light) }
        body.addView(studentAvatar(s), LinearLayout.LayoutParams(dp(70),dp(70)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        body.addView(TextView(this).apply { text = s.name; textSize = 21f; gravity = Gravity.CENTER; setTypeface(typeface,Typeface.BOLD); setTextColor(ink); setPadding(0,dp(10),0,dp(2)) })
        body.addView(TextView(this).apply { text = s.className + " · No: " + s.schoolNo; textSize = 12f; gravity = Gravity.CENTER; setTextColor(muted) })
        if (s.phone.isNotBlank()) body.addView(phoneDetail("Öğrenci", s.phone))
        guardians.forEach { g -> body.addView(phoneDetail(relationLabel(g.relationship), g.phone, relationColor(g.relationship))) }
        body.addView(TextView(this).apply { text = "Kapat"; textSize = 13f; gravity = Gravity.CENTER; setTypeface(typeface,Typeface.BOLD); background = rounded(Color.rgb(225,231,238),dp(13).toFloat()); setTextColor(ink); setOnClickListener { d.dismiss() } }, marginLp(ViewGroup.LayoutParams.MATCH_PARENT,dp(48),0,dp(14),0,0))
        val scroll = ScrollView(this); scroll.addView(body); d.setContentView(scroll); d.show(); d.window?.setBackgroundDrawableResource(android.R.color.transparent); d.window?.setLayout((resources.displayMetrics.widthPixels*.94).toInt(),(resources.displayMetrics.heightPixels*.82).toInt())
    }

    private fun phoneDetail(label: String, phone: String, color: Int = blue): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(9),dp(7),dp(9)); background = rounded(Color.WHITE,dp(14).toFloat(),soft)
        val info = LinearLayout(this@RehberActivity82).apply { orientation = LinearLayout.VERTICAL; addView(TextView(this@RehberActivity82).apply { text = label; textSize = 10.5f; setTypeface(typeface,Typeface.BOLD); setTextColor(color) }); addView(TextView(this@RehberActivity82).apply { text = if (phone.isBlank()) "Telefon eklenmemiş" else displayInternational(phone); textSize = 12f; setTextColor(ink) }) }
        addView(info,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); if (phone.isNotBlank()) { addView(squareAction("☎",brightBlue){dial(phone)}); addView(squareAction("◉",green){whatsapp(phone)}) }
    }.also { it.layoutParams = marginLp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,0,dp(5),0,dp(5)) }

    private fun showClasses() {
        selectNav("directory"); setHeader("Sınıf Listeleri", true)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(18)) }
        val scroll = ScrollView(this); val rows = cache.listClasses(); if (rows.isEmpty()) emptyState(body,"Sınıf bulunamadı.")
        rows.forEach { c -> body.addView(TextView(this).apply { text = c.name + "   ·   " + c.studentCount + " öğrenci"; textSize = 14f; setTextColor(ink); setPadding(dp(14),dp(14),dp(14),dp(14)); background = rounded(Color.WHITE,dp(15).toFloat(),soft); setOnClickListener { level = if (gradeOf(c.name) in 9..12) "high" else "middle"; selectedClass = c.name; showDirectory(false) } }, marginLp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,0,dp(4),0,dp(4))) }
        scroll.addView(body); replaceContent(scroll)
    }

    private fun showCalls() {
        selectNav("calls"); setHeader("Son Gelen Aramalar", true)
        val scroll = ScrollView(this); val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(18)) }
        val rows = cache.listHistory(150); if (rows.isEmpty()) emptyState(body,"Henüz arama kaydı yok.")
        rows.forEach { h -> body.addView(TextView(this).apply { text = (if (h.matched) h.studentName + " · " + h.relationship else "Kayıtlı Değil") + "\n" + displayInternational(h.phone) + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(h.callTime)); textSize = 12.5f; setTextColor(ink); setPadding(dp(14),dp(11),dp(14),dp(11)); background = rounded(Color.WHITE,dp(15).toFloat(),soft); if (h.studentId>0) setOnClickListener { showStudentDetail(h.studentId) } }, marginLp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,0,dp(4),0,dp(4))) }
        scroll.addView(body); replaceContent(scroll)
    }

    private fun showAnnouncements() {
        selectNav("ann"); setHeader("Duyurular", true)
        val scroll = ScrollView(this); val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(18)) }
        val rows = cache.listAnnouncements(); if (rows.isEmpty()) emptyState(body,"Yeni duyuru bulunmuyor.")
        rows.forEach { a -> body.addView(TextView(this).apply { text = a.title + if (a.summary.isNotBlank()) "\n" + a.summary else ""; textSize = 12.5f; setTextColor(ink); setPadding(dp(14),dp(12),dp(14),dp(12)); background = rounded(Color.WHITE,dp(15).toFloat(),soft) }, marginLp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,0,dp(4),0,dp(4))) }
        scroll.addView(body); replaceContent(scroll)
    }

    private fun showSettings() {
        selectNav("settings"); setHeader("Ayarlar", true)
        val scroll = ScrollView(this); val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(18)) }
        setting(body,"Arayan Kimliği",if(isCallScreeningActive()) "Açık" else "Kapalı") { requestCallScreeningRole() }
        setting(body,"Veri Senkronizasyonu","${cache.studentCount()} öğrenci · ${cache.guardianCount()} veli") { syncNow(true) }
        setting(body,"Gelen Arama Testi","Tam ekran kartı test edin") {
            val g=cache.listGuardians().firstOrNull(); if(g==null) Toast.makeText(this,"Önce veli kayıtlarını eşitleyin.",Toast.LENGTH_LONG).show() else CallerCardNotifier.show(this,cache.lookup(g.phone),g.phone)
        }
        setting(body,"Web Rehberi","Yönetim panelini aç") { openUri("https://elak.mcoaihl.com/rehber/") }
        setting(body,"Sürüm","v0.8.2") { }
        scroll.addView(body); replaceContent(scroll)
    }

    private fun setting(parent: LinearLayout, name: String, desc: String, action: () -> Unit) {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(12)); background=rounded(Color.WHITE,dp(15).toFloat(),soft); setOnClickListener{action()}; addView(TextView(this@RehberActivity82).apply{text=name;textSize=14.5f;setTypeface(typeface,Typeface.BOLD);setTextColor(ink)}); addView(TextView(this@RehberActivity82).apply{text=desc;textSize=10.5f;setTextColor(muted);setPadding(0,dp(3),0,0)}) }
        parent.addView(v,marginLp(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,0,dp(4),0,dp(4)))
    }

    private fun syncNow(showToast: Boolean) {
        val token=session.token; if(token.isNullOrBlank()){ if(showToast) Toast.makeText(this,"Rehber hesabı bağlı değil.",Toast.LENGTH_LONG).show(); return }
        if(showToast) Toast.makeText(this,"Rehber eşitleniyor…",Toast.LENGTH_SHORT).show()
        thread { try { val count=cache.replaceFromSync(RehberApi.sync(token)); session.lastSync=System.currentTimeMillis(); runOnUiThread { when(active){"home"->showHome();"directory"->showDirectory(guardianMode);"calls"->showCalls();"ann"->showAnnouncements();"settings"->showSettings()}; if(showToast) Toast.makeText(this,"${cache.studentCount()} öğrenci · ${cache.guardianCount()} veli · $count telefon güncellendi.",Toast.LENGTH_LONG).show() } } catch(e:Exception){ runOnUiThread{ if(showToast) Toast.makeText(this,"Eşitleme: "+(e.message?:"başarısız"),Toast.LENGTH_LONG).show() } } }
    }

    private fun requestCallScreeningRole() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){ val rm=getSystemService(RoleManager::class.java); if(rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)&&!rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) else Toast.makeText(this,"Arayan kimliği zaten açık veya kullanılamıyor.",Toast.LENGTH_SHORT).show() }
    }
    private fun isCallScreeningActive():Boolean=if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) false else try{getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)}catch(_:Exception){false}
    private fun requestNotificationPermissionIfNeeded(){ if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

    private fun classMatchesLevel(name:String):Boolean { val g=gradeOf(name); return if(level=="middle") g in 5..8 else g in 9..12 }
    private fun gradeOf(name:String):Int = Regex("(\\d{1,2})").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    private fun relationOrder(r:String):Int { val v=r.lowercase(Locale.forLanguageTag("tr-TR")); return when { v.contains("baba")||v.contains("father")->0; v.contains("anne")||v.contains("mother")->1; else->2 } }
    private fun relationLabel(r:String):String { val v=r.lowercase(Locale.forLanguageTag("tr-TR")); return when { v.contains("baba")||v.contains("father")->"Baba"; v.contains("anne")||v.contains("mother")->"Anne"; else->if(r.isBlank())"Diğer" else r } }
    private fun relationColor(r:String):Int { val v=r.lowercase(Locale.forLanguageTag("tr-TR")); return when { v.contains("baba")||v.contains("father")->fatherColor; v.contains("anne")||v.contains("mother")->motherColor; else->orange } }
    private fun displayInternational(raw:String):String { val n=PhoneUtil.normalize(raw); return if(n.length==10) "+90 ${n.substring(0,3)} ${n.substring(3,6)} ${n.substring(6,8)} ${n.substring(8)}" else PhoneUtil.display(raw) }
    private fun dial(p:String){ val n=PhoneUtil.international(p); if(n.isNotBlank()) try{startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:+$n")))}catch(_:Exception){} }
    private fun whatsapp(p:String){ val n=PhoneUtil.international(p); if(n.isNotBlank()) openUri("https://wa.me/$n") }
    private fun openUri(url:String){ try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){Toast.makeText(this,"Bağlantı açılamadı.",Toast.LENGTH_SHORT).show()} }
    private fun squareAction(label:String,color:Int,action:()->Unit):TextView=TextView(this).apply{text=label;textSize=17f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);background=rounded(color,dp(11).toFloat());setOnClickListener{action()};layoutParams=LinearLayout.LayoutParams(dp(42),dp(42)).apply{marginStart=dp(5)}}
    private fun loadPhoto(iv:ImageView,id:Long,version:Long){ val token=session.token?:return; thread{ val bytes=PhotoStore.get(this,token,id,version)?:return@thread; val bm=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:return@thread; runOnUiThread{if(!isFinishing)iv.setImageBitmap(bm)} } }
    private fun emptyState(p:LinearLayout,t:String){p.addView(TextView(this).apply{text=t;textSize=13f;gravity=Gravity.CENTER;setTextColor(muted);setPadding(dp(12),dp(36),dp(12),dp(36))})}
    private fun replaceContent(v:View){content.removeAllViews();content.addView(v,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))}
    private fun rounded(color:Int,radius:Float,stroke:Int?=null)=GradientDrawable().apply{setColor(color);cornerRadius=radius;if(stroke!=null)setStroke(dp(1),stroke)}
    private fun dp(v:Int)= (v*resources.displayMetrics.density+.5f).toInt()
    private fun weightLp(w:Float,l:Int,r:Int)=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,w).apply{setMargins(l,0,r,0)}
    private fun marginLp(w:Int,h:Int,l:Int,t:Int,r:Int,b:Int)=LinearLayout.LayoutParams(w,h).apply{setMargins(l,t,r,b)}
    private fun darken(c:Int,f:Float)=Color.rgb((Color.red(c)*f).toInt().coerceIn(0,255),(Color.green(c)*f).toInt().coerceIn(0,255),(Color.blue(c)*f).toInt().coerceIn(0,255))
    private fun lighten(c:Int,f:Float)=Color.rgb((Color.red(c)*f).toInt().coerceIn(0,255),(Color.green(c)*f).toInt().coerceIn(0,255),(Color.blue(c)*f).toInt().coerceIn(0,255))
    @Deprecated("Deprecated in Java") override fun onBackPressed(){if(active!="home")showHome() else super.onBackPressed()}
    private class SimpleTextWatcher(private val after:()->Unit):android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){};override fun afterTextChanged(s:android.text.Editable?){after()}}
}
