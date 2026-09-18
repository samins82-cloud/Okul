package com.elak.okulum.rehber

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class RehberActivity84 : AppCompatActivity() {
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
    private val dataPool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val renderGeneration = AtomicInteger(0)
    private val screenCache = mutableMapOf<String, View>()

    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var back: TextView
    private lateinit var content: FrameLayout
    private lateinit var bottom: LinearLayout
    private val nav = linkedMapOf<String, Pair<ImageView, TextView>>()

    private var active = ""
    private var guardianMode = false
    private var level = "middle"
    private var selectedClass = ""
    private var gridMode = false
    private var lastNavAt = 0L
    private var pendingPhotoStudent: CallerCache.Student? = null

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        invalidateScreen("settings")
        if (active == "settings") showSettings()
    }
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val student = pendingPhotoStudent
        pendingPhotoStudent = null
        if (uri != null && student != null) uploadPhoto(student, uri)
    }

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
        if (openStudent > 0) {
            showHome()
            showStudentDetail(openStudent)
        } else showHome()
        if (!session.token.isNullOrBlank()) syncNow(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val studentId = intent.getLongExtra("open_student_id", 0L)
        if (studentId > 0) showStudentDetail(studentId)
    }

    override fun onDestroy() {
        renderGeneration.incrementAndGet()
        dataPool.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
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
            isClickable = true
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastNavAt < 120L && active == key) return@setOnClickListener
                lastNavAt = now
                action()
            }
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
        val view = screenCache["home"] ?: buildHome().also { screenCache["home"] = it }
        swap(view)
    }

    private fun buildHome(): View {
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(18))
        }
        body.addView(TextView(this).apply {
            text = "ELAK Mobil"
            textSize = 23f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        body.addView(TextView(this).apply {
            val p = cache.profileName().ifBlank { session.username.ifBlank { "ELAK Kullanıcısı" } }
            text = if (cache.profileRole().isBlank()) p else p + " · " + cache.profileRole()
            textSize = 11.2f
            setTextColor(muted)
            setPadding(0, dp(2), 0, dp(8))
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
        return scroll
    }

    private fun tile(iconRes: Int, label: String, count: String, color: Int, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(8), dp(7), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(lighten(color, 1.05f), darken(color, .87f))).apply { cornerRadius = dp(20).toFloat() }
            elevation = dp(3).toFloat()
            setOnClickListener { action() }
            addView(ImageView(this@RehberActivity84).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(TextView(this@RehberActivity84).apply {
                text = label
                textSize = 14.7f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
            if (count.isNotBlank()) addView(TextView(this@RehberActivity84).apply {
                text = count
                textSize = 9.5f
                gravity = Gravity.CENTER
                setTextColor(Color.argb(230, 255, 255, 255))
                setPadding(0, dp(2), 0, 0)
            })
        }

    private fun tileRow(parent: LinearLayout, a: View, b: View) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(5))
        }
        row.addView(a, LinearLayout.LayoutParams(0, dp(118), 1f).apply { setMargins(dp(5), 0, dp(7), 0) })
        row.addView(b, LinearLayout.LayoutParams(0, dp(118), 1f).apply { setMargins(dp(7), 0, dp(5), 0) })
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(128)))
    }

    private fun statusStrip(): View {
        val last = if (session.lastSync > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastSync)) else "Henüz yok"
        val caller = if (isCallScreeningActive()) "Açık" else "Kapalı"
        return TextView(this).apply {
            text = "Arayan kimliği: $caller  ·  Telefon kaydı: ${cache.count()}  ·  Son eşitleme: $last"
            textSize = 10.1f
            setTextColor(muted)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.WHITE, dp(15).toFloat(), soft)
        }.also { it.layoutParams = marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(8)) }
    }

    private fun showDirectory(guardian: Boolean) {
        guardianMode = guardian
        selectNav("directory")
        setHeader(if (guardian) "Veli Rehberi" else "Öğrenci Rehberi", true)
        swap(buildDirectory(guardian))
    }

    private fun buildDirectory(guardian: Boolean): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), 0)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(Color.WHITE, dp(18).toFloat(), soft)
        }

        val levelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lateinit var middle: TextView
        lateinit var high: TextView
        fun paintLevel() {
            middle.background = rounded(if (level == "middle") blue else Color.rgb(242,246,250), dp(13).toFloat(), soft)
            middle.setTextColor(if (level == "middle") Color.WHITE else ink)
            high.background = rounded(if (level == "high") blue else Color.rgb(242,246,250), dp(13).toFloat(), soft)
            high.setTextColor(if (level == "high") Color.WHITE else ink)
        }
        middle = TextView(this).apply {
            text = "Ortaokul 5–8"
            textSize = 13.2f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }
        high = TextView(this).apply {
            text = "Lise 9–12"
            textSize = 13.2f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }
        levelRow.addView(middle, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(4) })
        levelRow.addView(high, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(4) })
        controls.addView(levelRow)
        paintLevel()

        val search = EditText(this).apply {
            hint = if (guardian) "Veli adı / öğrenci adı / no ara…" else "Öğrenci adı veya okul no ara…"
            textSize = 13.3f
            isSingleLine = true
            setPadding(dp(14), 0, dp(12), 0)
            background = rounded(Color.rgb(255,245,245), dp(15).toFloat(), Color.rgb(224,105,105))
        }
        controls.addView(search, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(52), 0, dp(9), 0, 0))

        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val classBox = TextView(this).apply {
            textSize = 13.5f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            setTextColor(Color.rgb(30,30,30))
            background = rounded(Color.rgb(245,248,251), dp(13).toFloat(), soft)
        }
        filterRow.addView(classBox, LinearLayout.LayoutParams(0, dp(47), 1f))
        val listBtn = TextView(this).apply {
            text = "☷"; textSize = 23f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(blue, dp(12).toFloat())
        }
        val gridBtn = TextView(this).apply {
            text = "▦"; textSize = 21f; gravity = Gravity.CENTER; setTextColor(ink)
        }
        val waBtn = TextView(this).apply {
            text = "W"; textSize = 16f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE); background = rounded(teal, dp(12).toFloat())
        }
        filterRow.addView(listBtn, marginLp(dp(47), dp(47), dp(7), 0, 0, 0))
        filterRow.addView(gridBtn, marginLp(dp(47), dp(47), dp(5), 0, 0, 0))
        filterRow.addView(waBtn, marginLp(dp(47), dp(47), dp(5), 0, 0, 0))
        controls.addView(filterRow, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(47), 0, dp(9), 0, 0))
        page.addView(controls)

        val progress = ProgressBar(this).apply { visibility = View.GONE }
        page.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        val listHost = FrameLayout(this)
        page.addView(listHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun levelClasses(): List<String> = cache.listClasses().map { it.name }.filter { classMatchesLevel(it) }.distinct().sortedWith(compareBy({ gradeOf(it) }, { it }))
        fun refreshClassText() { classBox.text = if (selectedClass.isBlank()) "Tüm Sınıflar   ▾" else "$selectedClass   ▾" }
        refreshClassText()

        var searchRunnable: Runnable? = null
        fun loadData() {
            val generation = renderGeneration.incrementAndGet()
            val q = search.text.toString().trim()
            val cls = if (q.isBlank()) selectedClass else ""
            val currentLevel = level
            val currentGuardian = guardian
            progress.visibility = View.VISIBLE
            dataPool.execute {
                if (!currentGuardian) {
                    val rows = cache.listStudents(q, cls).filter { matchesLevel(it.className, currentLevel) }
                    main.post {
                        if (generation != renderGeneration.get() || isFinishing) return@post
                        progress.visibility = View.GONE
                        showStudentVirtualList(listHost, rows)
                    }
                } else {
                    val rows = cache.listGuardians(q, cls).filter { matchesLevel(it.className, currentLevel) }
                    val grouped = rows.groupBy { it.studentId }.entries
                        .mapNotNull { e -> e.value.firstOrNull()?.let { GuardianGroup(e.key, it.studentName, it.schoolNo, it.className, e.value) } }
                        .sortedBy { it.studentName }
                    main.post {
                        if (generation != renderGeneration.get() || isFinishing) return@post
                        progress.visibility = View.GONE
                        showGuardianVirtualList(listHost, grouped)
                    }
                }
            }
        }

        middle.setOnClickListener {
            if (level != "middle") {
                level = "middle"; selectedClass = ""; paintLevel(); refreshClassText(); loadData()
            }
        }
        high.setOnClickListener {
            if (level != "high") {
                level = "high"; selectedClass = ""; paintLevel(); refreshClassText(); loadData()
            }
        }
        classBox.setOnClickListener {
            val items = listOf("Tüm Sınıflar") + levelClasses()
            AlertDialog.Builder(this).setTitle("Sınıf Seç").setItems(items.toTypedArray()) { _, which ->
                selectedClass = if (which == 0) "" else items[which]
                refreshClassText(); loadData()
            }.show()
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { main.removeCallbacks(it) }
                searchRunnable = Runnable { loadData() }
                main.postDelayed(searchRunnable!!, 160L)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        listBtn.setOnClickListener {
            if (gridMode) {
                gridMode = false
                listBtn.background = rounded(blue, dp(12).toFloat()); listBtn.setTextColor(Color.WHITE)
                gridBtn.background = null; gridBtn.setTextColor(ink)
                loadData()
            }
        }
        gridBtn.setOnClickListener {
            if (!gridMode) {
                gridMode = true
                gridBtn.background = rounded(blue, dp(12).toFloat()); gridBtn.setTextColor(Color.WHITE)
                listBtn.background = null; listBtn.setTextColor(ink)
                loadData()
            }
        }
        waBtn.setOnClickListener {
            val choices = if (selectedClass.isBlank()) cache.listClasses().filter { classMatchesLevel(it.name) && it.whatsappUrl.isNotBlank() }
            else cache.listClasses().filter { it.name == selectedClass && it.whatsappUrl.isNotBlank() }
            when {
                choices.isEmpty() -> Toast.makeText(this, "Bu seçim için WhatsApp grup bağlantısı tanımlı değil.", Toast.LENGTH_LONG).show()
                choices.size == 1 -> openUri(choices.first().whatsappUrl)
                else -> AlertDialog.Builder(this).setTitle("Sınıf WhatsApp Grupları").setItems(choices.map { it.name }.toTypedArray()) { _, i -> openUri(choices[i].whatsappUrl) }.show()
            }
        }

        loadData()
        return page
    }

    private data class GuardianGroup(
        val studentId: Long,
        val studentName: String,
        val schoolNo: String,
        val className: String,
        val guardians: List<CallerCache.Guardian>
    )

    private fun showStudentVirtualList(host: FrameLayout, rows: List<CallerCache.Student>) {
        host.removeAllViews()
        if (rows.isEmpty()) {
            host.addView(emptyText("Öğrenci kaydı bulunamadı."), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            return
        }
        if (gridMode) {
            val grid = GridView(this).apply {
                numColumns = 2
                horizontalSpacing = dp(7)
                verticalSpacing = dp(7)
                setPadding(dp(2), dp(9), dp(2), dp(14))
                clipToPadding = false
                adapter = StudentGridAdapter(rows)
                setOnItemClickListener { _, _, position, _ -> showStudentDetail(rows[position].id) }
            }
            host.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            val list = ListView(this).apply {
                divider = null
                dividerHeight = 0
                setPadding(0, dp(7), 0, dp(12))
                clipToPadding = false
                adapter = StudentListAdapter(rows)
                setOnItemClickListener { _, _, position, _ -> showStudentDetail(rows[position].id) }
            }
            host.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun showGuardianVirtualList(host: FrameLayout, rows: List<GuardianGroup>) {
        host.removeAllViews()
        if (rows.isEmpty()) {
            host.addView(emptyText("Veli kaydı bulunamadı."), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            return
        }
        val list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setPadding(0, dp(7), 0, dp(12))
            clipToPadding = false
            adapter = GuardianListAdapter(rows)
            setOnItemClickListener { _, _, position, _ -> showStudentDetail(rows[position].studentId) }
        }
        host.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private inner class StudentListAdapter(private val rows: List<CallerCache.Student>) : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = rows[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val s = rows[position]
            val card = (convertView as? LinearLayout) ?: LinearLayout(this@RehberActivity84).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Color.WHITE, dp(17).toFloat(), soft)
                tag = StudentHolder(
                    avatar = createAvatarFrame().also { addView(it.first, LinearLayout.LayoutParams(dp(52), dp(52))) }.second,
                    name = TextView(this@RehberActivity84).apply { textSize = 15.5f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) },
                    meta = TextView(this@RehberActivity84).apply { textSize = 10.8f; setTextColor(muted) },
                    phone = TextView(this@RehberActivity84).apply { textSize = 10.8f; setTextColor(muted) }
                ).also { h ->
                    val info = LinearLayout(this@RehberActivity84).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
                    info.addView(h.name)
                    info.addView(h.meta)
                    info.addView(h.phone)
                    addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            }
            card.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(86))
            val holder = card.tag as StudentHolder
            bindAvatar(holder.avatar, s)
            holder.name.text = s.name
            holder.meta.text = s.className + "  ·  No: " + s.schoolNo
            holder.phone.text = "Öğrenci Tel: " + if (s.phone.isBlank()) "Eklenmemiş" else PhoneUtil.display(s.phone)
            return LinearLayout(this@RehberActivity84).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(2), dp(4), dp(2), dp(4))
                addView(card)
            }
        }
    }

    private data class StudentHolder(val avatar: AvatarHolder, val name: TextView, val meta: TextView, val phone: TextView)

    private inner class StudentGridAdapter(private val rows: List<CallerCache.Student>) : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = rows[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val s = rows[position]
            val box = (convertView as? LinearLayout) ?: LinearLayout(this@RehberActivity84).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(9), dp(8), dp(9))
                background = rounded(Color.WHITE, dp(16).toFloat(), soft)
                val av = createAvatarFrame()
                addView(av.first, LinearLayout.LayoutParams(dp(50), dp(50)))
                val n = TextView(this@RehberActivity84).apply { textSize = 11.5f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setTextColor(ink); maxLines = 2 }
                val m = TextView(this@RehberActivity84).apply { textSize = 9.4f; gravity = Gravity.CENTER; setTextColor(muted) }
                addView(n); addView(m)
                tag = GridHolder(av.second, n, m)
            }
            val h = box.tag as GridHolder
            bindAvatar(h.avatar, s)
            h.name.text = s.name
            h.meta.text = s.className + " · No: " + s.schoolNo
            box.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(128))
            return box
        }
    }

    private data class GridHolder(val avatar: AvatarHolder, val name: TextView, val meta: TextView)

    private inner class GuardianListAdapter(private val rows: List<GuardianGroup>) : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = rows[position].studentId
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val g = rows[position]
            val student = cache.getStudent(g.studentId) ?: CallerCache.Student(g.studentId, g.studentName, g.schoolNo, g.className, "", false, 0L, false)
            val outer = LinearLayout(this@RehberActivity84).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(dp(12), dp(11), dp(10), dp(11))
                background = rounded(Color.WHITE, dp(17).toFloat(), soft)
            }
            val av = createAvatarFrame()
            outer.addView(av.first, LinearLayout.LayoutParams(dp(50), dp(50)))
            bindAvatar(av.second, student)
            val mainCol = LinearLayout(this@RehberActivity84).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
            mainCol.addView(TextView(this@RehberActivity84).apply { text = g.studentName; textSize = 15.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink) })
            mainCol.addView(TextView(this@RehberActivity84).apply { text = g.className + "  ·  No: " + g.schoolNo; textSize = 10.7f; setTextColor(muted) })
            g.guardians.sortedWith(compareBy({ relationOrder(it.relationship) }, { it.name })).forEach { mainCol.addView(guardianLine(it)) }
            outer.addView(mainCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return LinearLayout(this@RehberActivity84).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(2), dp(4), dp(2), dp(4))
                addView(outer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    private fun guardianLine(g: CallerCache.Guardian): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(this).apply { text = relationLabel(g.relationship); textSize = 11.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(relationColor(g.relationship)) })
        info.addView(TextView(this).apply { text = if (g.phone.isBlank()) "Telefon eklenmemiş" else displayInternational(g.phone); textSize = 10.8f; setTextColor(muted) })
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (g.phone.isNotBlank()) {
            row.addView(squareAction("☎", brightBlue) { dial(g.phone) })
            row.addView(squareAction("W", green) { whatsapp(g.phone) })
        }
        return row
    }

    private data class AvatarHolder(val letter: TextView, val image: ImageView)

    private fun createAvatarFrame(): Pair<FrameLayout, AvatarHolder> {
        val box = FrameLayout(this)
        val letter = TextView(this).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(19,119,137), dp(26).toFloat())
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = rounded(Color.TRANSPARENT, dp(26).toFloat())
        }
        box.addView(letter, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        box.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return box to AvatarHolder(letter, image)
    }

    private fun bindAvatar(h: AvatarHolder, s: CallerCache.Student) {
        h.letter.text = s.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Ö"
        h.image.setImageDrawable(null)
        h.image.visibility = View.INVISIBLE
        h.image.tag = null
        if (s.hasPhoto) {
            val token = session.token.orEmpty()
            FastPhotoLoader.load(this, token, s.id, s.photoVersion, h.image) { h.image.visibility = View.VISIBLE }
        }
    }

    private fun showStudentDetail(studentId: Long) {
        val s = cache.getStudent(studentId) ?: return
        val guardians = cache.guardiansForStudent(studentId)
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); setBackgroundColor(light) }
        val av = createAvatarFrame()
        body.addView(av.first, LinearLayout.LayoutParams(dp(76), dp(76)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        bindAvatar(av.second, s)
        body.addView(TextView(this).apply { text = s.name; textSize = 20f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setTextColor(ink); setPadding(0, dp(9), 0, dp(2)) })
        body.addView(TextView(this).apply { text = s.className + " · No: " + s.schoolNo; textSize = 11.8f; gravity = Gravity.CENTER; setTextColor(muted) })
        if (s.phone.isNotBlank()) body.addView(phoneDetail("Öğrenci", s.phone))
        guardians.forEach { g -> body.addView(phoneDetail(relationLabel(g.relationship), g.phone, relationColor(g.relationship))) }
        if (s.canEdit || cache.mobileAccess().equals("both", true)) {
            body.addView(TextView(this).apply {
                text = "Fotoğrafı Değiştir"
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = rounded(orange, dp(13).toFloat())
                setOnClickListener { pendingPhotoStudent = s; photoPicker.launch("image/*") }
            }, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(46), 0, dp(12), 0, 0))
        }
        body.addView(TextView(this).apply {
            text = "Kapat"
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(Color.rgb(225,231,238), dp(13).toFloat())
            setTextColor(ink)
            setOnClickListener { d.dismiss() }
        }, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(46), 0, dp(10), 0, 0))
        val scroll = ScrollView(this); scroll.addView(body)
        d.setContentView(scroll); d.show()
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * .94).toInt(), (resources.displayMetrics.heightPixels * .82).toInt())
    }

    private fun phoneDetail(label: String, phone: String, color: Int = blue): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(9), dp(7), dp(9)); background = rounded(Color.WHITE, dp(14).toFloat(), soft)
        val info = LinearLayout(this@RehberActivity84).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@RehberActivity84).apply { text = label; textSize = 10.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(color) })
            addView(TextView(this@RehberActivity84).apply { text = if (phone.isBlank()) "Telefon eklenmemiş" else displayInternational(phone); textSize = 12f; setTextColor(ink) })
        }
        addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (phone.isNotBlank()) {
            addView(squareAction("☎", brightBlue) { dial(phone) })
            addView(squareAction("W", green) { whatsapp(phone) })
        }
    }.also { it.layoutParams = marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(5), 0, dp(5)) }

    private fun uploadPhoto(student: CallerCache.Student, uri: Uri) {
        val token = session.token ?: return
        Toast.makeText(this, "Fotoğraf yükleniyor…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Fotoğraf okunamadı.")
                RehberApi.uploadStudentPhoto(token, student.id, bytes)
                FastPhotoLoader.remove(this, student.id)
                runOnUiThread { Toast.makeText(this, "Fotoğraf güncellendi.", Toast.LENGTH_LONG).show(); syncNow(false) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Fotoğraf: " + (e.message ?: "yüklenemedi"), Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showClasses() {
        selectNav("directory")
        setHeader("Sınıf Listeleri", true)
        val key = "classes"
        val view = screenCache[key] ?: run {
            val list = ListView(this).apply {
                divider = null; dividerHeight = 0; setPadding(dp(14), dp(8), dp(14), dp(12)); clipToPadding = false
                val rows = cache.listClasses()
                adapter = object : BaseAdapter() {
                    override fun getCount() = rows.size
                    override fun getItem(position: Int) = rows[position]
                    override fun getItemId(position: Int) = rows[position].id
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                        val c = rows[position]
                        return TextView(this@RehberActivity84).apply {
                            text = c.name + "   ·   " + c.studentCount + " öğrenci"
                            textSize = 14f; setTextColor(ink); setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(Color.WHITE, dp(15).toFloat(), soft)
                        }.let { v -> LinearLayout(this@RehberActivity84).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4)); addView(v) } }
                    }
                }
                setOnItemClickListener { _, _, position, _ ->
                    val c = rows[position]; level = if (gradeOf(c.name) in 9..12) "high" else "middle"; selectedClass = c.name; showDirectory(false)
                }
            }
            list.also { screenCache[key] = it }
        }
        swap(view)
    }

    private fun showCalls() {
        selectNav("calls"); setHeader("Son Gelen Aramalar", true)
        val rows = cache.listHistory(150)
        val list = ListView(this).apply {
            divider = null; dividerHeight = 0; setPadding(dp(14), dp(8), dp(14), dp(12)); clipToPadding = false
            adapter = object : BaseAdapter() {
                override fun getCount() = rows.size
                override fun getItem(position: Int) = rows[position]
                override fun getItemId(position: Int) = rows[position].id
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val h = rows[position]
                    val tv = (convertView as? TextView) ?: TextView(this@RehberActivity84).apply { textSize = 12.3f; setTextColor(ink); setPadding(dp(14), dp(11), dp(14), dp(11)); background = rounded(Color.WHITE, dp(15).toFloat(), soft) }
                    tv.text = (if (h.matched) h.studentName + " · " + h.relationship else "Kayıtlı Değil") + "\n" + displayInternational(h.phone) + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.callTime))
                    return tv
                }
            }
            setOnItemClickListener { _, _, position, _ -> if (rows[position].studentId > 0) showStudentDetail(rows[position].studentId) }
        }
        swap(list)
    }

    private fun showAnnouncements() {
        selectNav("ann"); setHeader("Duyurular", true)
        val key = "ann"
        val view = screenCache[key] ?: run {
            val rows = cache.listAnnouncements()
            ListView(this).apply {
                divider = null; dividerHeight = 0; setPadding(dp(14), dp(8), dp(14), dp(12)); clipToPadding = false
                adapter = object : BaseAdapter() {
                    override fun getCount() = rows.size
                    override fun getItem(position: Int) = rows[position]
                    override fun getItemId(position: Int) = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                        val a = rows[position]
                        val tv = (convertView as? TextView) ?: TextView(this@RehberActivity84).apply { textSize = 12.5f; setTextColor(ink); setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Color.WHITE, dp(15).toFloat(), soft) }
                        tv.text = a.title + if (a.summary.isNotBlank()) "\n" + a.summary else ""
                        return tv
                    }
                }
            }.also { screenCache[key] = it }
        }
        swap(view)
    }

    private fun showSettings() {
        selectNav("settings"); setHeader("Ayarlar", true)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(18)) }
        setting(body, "Arayan Kimliği", if (isCallScreeningActive()) "Açık" else "Kapalı") { requestCallScreeningRole() }
        setting(body, "Veri Senkronizasyonu", "${cache.studentCount()} öğrenci · ${cache.guardianCount()} veli") { syncNow(true) }
        setting(body, "Gelen Arama Testi", "Tam ekran kartı test edin") {
            val g = cache.listGuardians().firstOrNull()
            if (g == null) Toast.makeText(this, "Önce veli kayıtlarını eşitleyin.", Toast.LENGTH_LONG).show()
            else CallerCardNotifier.show(this, cache.lookup(g.phone), g.phone)
        }
        setting(body, "Fotoğraf Önbelleği", "RAM önbelleğini temizle") { FastPhotoLoader.clearMemory(); Toast.makeText(this, "Fotoğraf önbelleği temizlendi.", Toast.LENGTH_SHORT).show() }
        setting(body, "Web Rehberi", "Yönetim panelini aç") { openUri("https://elak.mcoaihl.com/rehber/") }
        setting(body, "Sürüm", "v0.8.4 · Performans") { }
        scroll.addView(body)
        swap(scroll)
    }

    private fun setting(parent: LinearLayout, name: String, desc: String, action: () -> Unit) {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Color.WHITE, dp(15).toFloat(), soft); setOnClickListener { action() }
            addView(TextView(this@RehberActivity84).apply { text = name; textSize = 14.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink) })
            addView(TextView(this@RehberActivity84).apply { text = desc; textSize = 10.5f; setTextColor(muted); setPadding(0, dp(3), 0, 0) })
        }
        parent.addView(v, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(4), 0, dp(4)))
    }

    private fun syncNow(showToast: Boolean) {
        val token = session.token
        if (token.isNullOrBlank()) {
            if (showToast) Toast.makeText(this, "Rehber hesabı bağlı değil.", Toast.LENGTH_LONG).show()
            return
        }
        if (showToast) Toast.makeText(this, "Rehber eşitleniyor…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val count = cache.replaceFromSync(RehberApi.sync(token))
                session.lastSync = System.currentTimeMillis()
                runOnUiThread {
                    screenCache.clear(); renderGeneration.incrementAndGet()
                    when (active) {
                        "home" -> showHome()
                        "directory" -> showDirectory(guardianMode)
                        "calls" -> showCalls()
                        "ann" -> showAnnouncements()
                        "settings" -> showSettings()
                        else -> showHome()
                    }
                    if (showToast) Toast.makeText(this, "${cache.studentCount()} öğrenci · ${cache.guardianCount()} veli · $count telefon güncellendi.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { if (showToast) Toast.makeText(this, "Eşitleme: " + (e.message ?: "başarısız"), Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun invalidateScreen(key: String) { screenCache.remove(key) }

    private fun swap(view: View) {
        if (content.childCount == 1 && content.getChildAt(0) === view) return
        (view.parent as? ViewGroup)?.removeView(view)
        content.removeAllViews()
        content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.alpha = .93f
        view.animate().alpha(1f).setDuration(70L).start()
    }

    private fun emptyText(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; gravity = Gravity.CENTER; setTextColor(muted)
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            else Toast.makeText(this, "Arayan kimliği zaten açık veya kullanılamıyor.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isCallScreeningActive(): Boolean = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) false else try {
        getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    } catch (_: Exception) { false }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun classMatchesLevel(name: String) = matchesLevel(name, level)
    private fun matchesLevel(name: String, lv: String): Boolean { val g = gradeOf(name); return if (lv == "middle") g in 5..8 else g in 9..12 }
    private fun gradeOf(name: String): Int = Regex("(\\d{1,2})").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    private fun relationOrder(r: String): Int { val v = r.lowercase(Locale.forLanguageTag("tr-TR")); return when { v.contains("baba") || v.contains("father") -> 0; v.contains("anne") || v.contains("mother") -> 1; else -> 2 } }
    private fun relationLabel(r: String): String { val v = r.lowercase(Locale.forLanguageTag("tr-TR")); return when { v.contains("baba") || v.contains("father") -> "Baba"; v.contains("anne") || v.contains("mother") -> "Anne"; else -> if (r.isBlank()) "Diğer" else r } }
    private fun relationColor(r: String): Int { val v = r.lowercase(Locale.forLanguageTag("tr-TR")); return when { v.contains("baba") || v.contains("father") -> fatherColor; v.contains("anne") || v.contains("mother") -> motherColor; else -> orange } }
    private fun displayInternational(raw: String): String { val n = PhoneUtil.normalize(raw); return if (n.length == 10) "+90 ${n.substring(0,3)} ${n.substring(3,6)} ${n.substring(6,8)} ${n.substring(8)}" else PhoneUtil.display(raw) }
    private fun dial(p: String) { val n = PhoneUtil.international(p); if (n.isNotBlank()) try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$n"))) } catch (_: Exception) {} }
    private fun whatsapp(p: String) { val n = PhoneUtil.international(p); if (n.isNotBlank()) openUri("https://wa.me/$n") }
    private fun openUri(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { Toast.makeText(this, "Bağlantı açılamadı.", Toast.LENGTH_SHORT).show() } }
    private fun squareAction(label: String, color: Int, action: () -> Unit): TextView = TextView(this).apply { text = label; textSize = 15f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = rounded(color, dp(10).toFloat()); setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(dp(39), dp(39)).apply { marginStart = dp(5) } }
    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply { setColor(color); cornerRadius = radius; if (stroke != null) setStroke(dp(1), stroke) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun marginLp(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int) = LinearLayout.LayoutParams(w, h).apply { setMargins(l,t,r,b) }
    private fun darken(c: Int, f: Float) = Color.rgb((Color.red(c)*f).toInt().coerceIn(0,255),(Color.green(c)*f).toInt().coerceIn(0,255),(Color.blue(c)*f).toInt().coerceIn(0,255))
    private fun lighten(c: Int, f: Float) = Color.rgb((Color.red(c)*f).toInt().coerceIn(0,255),(Color.green(c)*f).toInt().coerceIn(0,255),(Color.blue(c)*f).toInt().coerceIn(0,255))

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (active != "home") showHome() else super.onBackPressed() }
}
