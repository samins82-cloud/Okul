package com.elak.okulum.rehber

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import kotlin.concurrent.thread

class RehberActivity : AppCompatActivity() {
    private val navy = Color.rgb(8, 36, 79)
    private val red = Color.rgb(235, 37, 43)
    private val blue = Color.rgb(13, 125, 235)
    private val teal = Color.rgb(21, 187, 184)
    private val orange = Color.rgb(255, 153, 27)
    private val purple = Color.rgb(108, 74, 238)
    private val green = Color.rgb(18, 175, 84)
    private val light = Color.rgb(246, 249, 253)
    private val ink = Color.rgb(10, 39, 83)

    private val session by lazy { RehberSession(this) }
    private val cache by lazy { CallerCache(this) }

    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var toolbarTitle: TextView
    private lateinit var backButton: TextView
    private lateinit var content: FrameLayout
    private lateinit var bottomBar: LinearLayout
    private val navViews = linkedMapOf<String, TextView>()
    private var activeScreen = "home"
    private var directoryGuardianMode = false
    private var selectedClass = ""
    private var pendingPhotoStudent: CallerCache.Student? = null

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { renderSettingsIfVisible() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { renderSettingsIfVisible() }
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val student = pendingPhotoStudent
        pendingPhotoStudent = null
        if (uri != null && student != null) uploadPhoto(student, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE
        buildShell()
        RehberSyncWorker.schedule(this)
        showHome()
        if (!session.token.isNullOrBlank()) syncNow(false)
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(light)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            setBackgroundColor(navy)
        }
        backButton = TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            visibility = View.GONE
            setOnClickListener { showHome() }
        }
        toolbar.addView(backButton, LinearLayout.LayoutParams(dp(42), dp(48)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brand.addView(TextView(this).apply {
            text = "ELAK"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        toolbarTitle = TextView(this).apply {
            text = " Akıllı Rehber"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, 0, 0)
        }
        brand.addView(toolbarTitle)
        toolbar.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1f))

        toolbar.addView(iconButton("↻") { syncNow(true) })
        toolbar.addView(iconButton("⚙") { showSettings() })
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))

        content = FrameLayout(this).apply { setBackgroundColor(light) }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(5), dp(4), dp(6))
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        addNav("home", "⌂", "Ana Sayfa") { showHome() }
        addNav("directory", "▣", "Rehber") { showDirectory(false) }
        addNav("calls", "☎", "Aramalar") { showCalls() }
        addNav("ann", "●", "Duyurular") { showAnnouncements() }
        addNav("settings", "⚙", "Ayarlar") { showSettings() }
        root.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun addNav(key: String, icon: String, label: String, action: () -> Unit) {
        val tv = TextView(this).apply {
            text = icon + "\n" + label
            gravity = Gravity.CENTER
            textSize = 10.5f
            setTextColor(Color.rgb(122, 136, 153))
            setPadding(dp(1), dp(2), dp(1), dp(1))
            setOnClickListener { action() }
        }
        navViews[key] = tv
        bottomBar.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun selectNav(key: String) {
        activeScreen = key
        navViews.forEach { (k, v) ->
            v.setTextColor(if (k == key) red else Color.rgb(125, 139, 156))
            v.setTypeface(null, if (k == key) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun showHome() {
        selectNav("home")
        setToolbar("Akıllı Rehber", false)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
        }
        val profile = cache.profileName().ifBlank { session.username.ifBlank { "ELAK Kullanıcısı" } }
        val role = cache.profileRole()
        body.addView(TextView(this).apply {
            text = "ELAK Mobil"
            textSize = 28f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        body.addView(TextView(this).apply {
            text = if (role.isBlank()) profile else profile + " · " + role
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(2), 0, dp(14))
        })

        tileRow(body,
            tile("♟", "Öğrenci\nRehberi", blue, cache.studentCount().toString()) { showDirectory(false) },
            tile("♟♟", "Veli\nRehberi", red, cache.guardianCount().toString()) { showDirectory(true) }
        )
        tileRow(body,
            tile("♙", "Sınıf\nListeleri", teal, cache.classCount().toString()) { showClasses() },
            tile("◖", "Duyurular", orange, cache.listAnnouncements().size.toString()) { showAnnouncements() }
        )
        tileRow(body,
            tile("☎", "Aramalar", purple, cache.listHistory(true, 200).size.toString()) { showCalls() },
            tile("⚙", "Ayarlar", Color.rgb(116, 139, 168), "") { showSettings() }
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.rgb(234, 244, 255), dp(20).toFloat())
        }
        info.addView(TextView(this).apply {
            text = "Daha iyi bir iletişim, daha güçlü bir okul için…"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            gravity = Gravity.CENTER
        })
        val last = if (session.lastSync > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastSync)) else "Henüz eşitlenmedi"
        info.addView(TextView(this).apply {
            text = "Son eşitleme: " + last
            textSize = 11f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        })
        body.addView(info, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(8), 0, 0))
        scroll.addView(body)
        replaceContent(scroll)
    }

    private fun tile(icon: String, label: String, color: Int, count: String, action: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(10))
            background = gradientTile(color)
            elevation = dp(4).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(TextView(this@RehberActivity).apply {
                text = icon
                textSize = 31f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@RehberActivity).apply {
                text = label
                textSize = 17f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            if (count.isNotBlank()) addView(TextView(this@RehberActivity).apply {
                text = count + " kayıt"
                textSize = 10.5f
                setTextColor(Color.argb(225, 255, 255, 255))
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun tileRow(parent: LinearLayout, left: View, right: View) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(left, marginWeightLp(1f, dp(4), dp(4)))
        row.addView(right, marginWeightLp(1f, dp(4), dp(4)))
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(154)))
    }

    private fun showDirectory(guardian: Boolean, initialClass: String = "") {
        directoryGuardianMode = guardian
        session.lastDirectoryMode = if (guardian) "guardian" else "student"
        if (initialClass.isNotBlank()) selectedClass = initialClass
        selectNav("directory")
        setToolbar(if (guardian) "Veli Rehberi" else "Öğrenci Rehberi", true)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), 0)
        }

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val studentTab = segment("ÖĞRENCİ REHBERİ", !guardian) { showDirectory(false) }
        val guardianTab = segment("VELİ REHBERİ", guardian) { showDirectory(true) }
        tabs.addView(studentTab, marginWeightLp(1f, dp(3), dp(3)))
        tabs.addView(guardianTab, marginWeightLp(1f, dp(3), dp(3)))
        page.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(6))
        }
        val search = EditText(this).apply {
            hint = if (guardian) "Veli, öğrenci, no veya telefon ara" else "Öğrenci adı veya numarası ile ara"
            textSize = 14f
            isSingleLine = true
            setPadding(dp(14), 0, dp(10), 0)
            background = rounded(Color.WHITE, dp(16).toFloat(), Color.rgb(225, 232, 241))
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))

        val viewToggle = TextView(this).apply {
            text = if (session.cardView) "☷" else "▦"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(blue)
            background = rounded(Color.WHITE, dp(14).toFloat(), Color.rgb(225, 232, 241))
        }
        searchRow.addView(viewToggle, marginLp(dp(48), dp(48), dp(8), 0, 0, 0))
        page.addView(searchRow)

        val classSpinner = Spinner(this)
        val classes = listOf("Tüm Sınıflar") + cache.listClasses().map { it.name }.filter { it.isNotBlank() }.distinct()
        classSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, classes)
        val selected = classes.indexOf(selectedClass).takeIf { it >= 0 } ?: 0
        classSpinner.setSelection(selected, false)
        page.addView(classSpinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))

        val listScroll = ScrollView(this).apply { isFillViewport = true }
        val listBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(18))
        }
        listScroll.addView(listBody)
        page.addView(listScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun render() {
            listBody.removeAllViews()
            val query = search.text?.toString().orEmpty().trim()
            val spinnerClass = classSpinner.selectedItem?.toString().orEmpty()
            val classFilter = if (query.isNotBlank() || spinnerClass == "Tüm Sınıflar") "" else spinnerClass
            if (guardian) {
                val rows = cache.listGuardians(query, classFilter)
                if (rows.isEmpty()) emptyState(listBody, "Veli kaydı bulunamadı.")
                rows.forEach { listBody.addView(guardianRow(it, session.cardView)) }
            } else {
                val rows = cache.listStudents(query, classFilter)
                if (rows.isEmpty()) emptyState(listBody, "Öğrenci kaydı bulunamadı.")
                rows.forEach { listBody.addView(studentRow(it, session.cardView)) }
            }
        }

        search.addTextChangedListener(SimpleTextWatcher { render() })
        classSpinner.onItemSelectedListener = SimpleItemSelected { position ->
            selectedClass = if (position <= 0) "" else classes[position]
            render()
        }
        viewToggle.setOnClickListener {
            session.cardView = !session.cardView
            viewToggle.text = if (session.cardView) "☷" else "▦"
            render()
        }
        render()
        replaceContent(page)
    }

    private fun studentRow(student: CallerCache.Student, card: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(if (card) 12 else 8), dp(8), dp(if (card) 12 else 8))
            background = rounded(Color.WHITE, dp(if (card) 20 else 12).toFloat(), Color.rgb(230, 235, 242))
            elevation = if (card) dp(3).toFloat() else 0f
            setOnClickListener { showStudentDetail(student.id) }
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(232, 238, 246), dp(26).toFloat())
            clipToOutline = true
        }
        row.addView(photo, LinearLayout.LayoutParams(dp(52), dp(52)))
        if (student.hasPhoto) loadPhoto(photo, student.id, student.photoVersion)

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(5), 0)
        }
        textBox.addView(TextView(this).apply {
            this.text = student.name.ifBlank { "Öğrenci" }
            textSize = if (card) 16f else 15f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        })
        textBox.addView(TextView(this).apply {
            this.text = listOf(student.className, if (student.schoolNo.isNotBlank()) "No: " + student.schoolNo else "").filter { it.isNotBlank() }.joinToString("  ·  ")
            textSize = 11.5f
            setTextColor(Color.rgb(95, 112, 132))
            maxLines = 1
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (student.phone.isNotBlank()) {
            row.addView(actionSquare("☎", blue) { dial(student.phone) })
            row.addView(actionSquare("W", green) { whatsapp(student.phone) })
        } else {
            row.addView(TextView(this).apply {
                text = "›"
                textSize = 28f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(38), dp(44)))
        }
        return wrapRow(row, card)
    }

    private fun guardianRow(g: CallerCache.Guardian, card: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(if (card) 12 else 9), dp(8), dp(if (card) 12 else 9))
            background = rounded(Color.WHITE, dp(if (card) 20 else 12).toFloat(), Color.rgb(230, 235, 242))
            elevation = if (card) dp(3).toFloat() else 0f
            setOnClickListener { if (g.studentId > 0) showStudentDetail(g.studentId) }
        }
        val relColor = relationshipColor(g.relationship)
        val badge = TextView(this).apply {
            text = relationShort(g.relationship)
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(relColor)
            background = rounded(Color.argb(24, Color.red(relColor), Color.green(relColor), Color.blue(relColor)), dp(22).toFloat())
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(48), dp(48)))

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(4), 0)
        }
        textBox.addView(TextView(this).apply {
            this.text = g.name.ifBlank { g.relationship.ifBlank { "Veli" } }
            textSize = 15.5f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        })
        textBox.addView(TextView(this).apply {
            this.text = listOf(g.relationship, g.studentName, g.className, if (g.schoolNo.isNotBlank()) "No: " + g.schoolNo else "").filter { it.isNotBlank() }.joinToString(" · ")
            textSize = 11f
            setTextColor(Color.rgb(95, 112, 132))
            maxLines = 2
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (g.phone.isNotBlank()) {
            row.addView(actionSquare("☎", blue) { dial(g.phone) })
            row.addView(actionSquare("W", green) { whatsapp(g.phone) })
        }
        return wrapRow(row, card)
    }

    private fun wrapRow(view: View, card: Boolean): View {
        val holder = LinearLayout(this)
        holder.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return holder.apply {
            setPadding(dp(2), dp(if (card) 5 else 2), dp(2), dp(if (card) 5 else 2))
        }
    }

    private fun showStudentDetail(studentId: Long) {
        val student = cache.getStudent(studentId) ?: return
        val guardians = cache.guardiansForStudent(studentId)
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(light)
        }

        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(229, 235, 244), dp(56).toFloat())
            clipToOutline = true
            setOnClickListener { showLargePhoto(student) }
        }
        val photoHolder = LinearLayout(this).apply { gravity = Gravity.CENTER }
        photoHolder.addView(photo, LinearLayout.LayoutParams(dp(112), dp(112)))
        box.addView(photoHolder)
        if (student.hasPhoto) loadPhoto(photo, student.id, student.photoVersion)

        box.addView(TextView(this).apply {
            text = student.name
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            setPadding(0, dp(10), 0, dp(4))
        })
        box.addView(TextView(this).apply {
            text = student.className
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(blue)
            setTypeface(typeface, Typeface.BOLD)
        })

        detailLine(box, "Öğrenci Numarası", student.schoolNo, "♟")
        if (student.phone.isNotBlank()) detailPhoneLine(box, "Öğrenci Telefonu", student.phone)

        box.addView(TextView(this).apply {
            text = "Veli İletişim Bilgileri"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            setPadding(dp(2), dp(18), 0, dp(8))
        })
        if (guardians.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "Veli bilgisi eklenmemiş."
                textSize = 13f
                setTextColor(Color.GRAY)
                setPadding(dp(10), dp(14), dp(10), dp(14))
            })
        } else guardians.forEach { box.addView(guardianDetailCard(it)) }

        if (student.canEdit) {
            val editRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            editRow.addView(plainButton("Bilgileri Düzenle", blue) { dialog.dismiss(); showStudentEditor(student) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            editRow.addView(plainButton("Fotoğraf Değiştir", orange) {
                pendingPhotoStudent = student
                photoPicker.launch("image/*")
            }, marginWeightLp(1f, dp(7), 0))
            box.addView(editRow, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 0, dp(12), 0, 0))
        }

        box.addView(plainButton("Kapat", Color.rgb(219, 225, 234)) { dialog.dismiss() }, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(52), 0, dp(14), 0, 0))
        scroll.addView(box)
        dialog.setContentView(scroll)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .94).toInt(), (resources.displayMetrics.heightPixels * .88).toInt())
    }

    private fun detailLine(parent: LinearLayout, title: String, value: String, icon: String) {
        if (value.isBlank()) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.WHITE, dp(14).toFloat())
        }
        row.addView(TextView(this).apply { text = icon; textSize = 20f; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(38), dp(42)))
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@RehberActivity).apply { text = title; textSize = 10.5f; setTextColor(Color.GRAY) })
            addView(TextView(this@RehberActivity).apply { text = value; textSize = 14f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        parent.addView(row, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(8), 0, 0))
    }

    private fun detailPhoneLine(parent: LinearLayout, title: String, phone: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = rounded(Color.WHITE, dp(14).toFloat())
        }
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@RehberActivity).apply { text = title; textSize = 10.5f; setTextColor(Color.GRAY) })
            addView(TextView(this@RehberActivity).apply { text = PhoneUtil.display(phone); textSize = 14f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) })
        }
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(actionSquare("☎", blue) { dial(phone) })
        row.addView(actionSquare("W", green) { whatsapp(phone) })
        parent.addView(row, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(8), 0, 0))
    }

    private fun guardianDetailCard(g: CallerCache.Guardian): View {
        val c = relationshipColor(g.relationship)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(8), dp(9))
            background = rounded(Color.argb(24, Color.red(c), Color.green(c), Color.blue(c)), dp(15).toFloat())
            val labels = LinearLayout(this@RehberActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@RehberActivity).apply {
                    text = g.relationship.ifBlank { "Veli" }
                    textSize = 11f
                    setTextColor(c)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(this@RehberActivity).apply {
                    text = g.name.ifBlank { "Veli" }
                    textSize = 14f
                    setTextColor(ink)
                    setTypeface(typeface, Typeface.BOLD)
                })
                if (g.phone.isNotBlank()) addView(TextView(this@RehberActivity).apply {
                    text = PhoneUtil.display(g.phone)
                    textSize = 12f
                    setTextColor(ink)
                })
            }
            addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (g.phone.isNotBlank()) {
                addView(actionSquare("☎", blue) { dial(g.phone) })
                addView(actionSquare("W", green) { whatsapp(g.phone) })
            }
        }.also { it.layoutParams = marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(5), 0, 0) }
    }

    private fun showStudentEditor(student: CallerCache.Student) {
        val guardians = cache.guardiansForStudent(student.id)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(10), dp(28), dp(4))
        }
        val studentPhone = EditText(this).apply {
            hint = "Öğrenci telefonu"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(PhoneUtil.display(student.phone))
        }
        wrap.addView(studentPhone)
        val guardianInputs = arrayListOf<Triple<CallerCache.Guardian, EditText, EditText>>()
        guardians.forEach { g ->
            wrap.addView(TextView(this).apply {
                text = g.relationship.ifBlank { "Veli" } + " · " + g.name
                setTextColor(relationshipColor(g.relationship))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            })
            val name = EditText(this).apply { hint = "Ad Soyad"; setText(g.name) }
            val phone = EditText(this).apply { hint = "Telefon"; inputType = android.text.InputType.TYPE_CLASS_PHONE; setText(PhoneUtil.display(g.phone)) }
            wrap.addView(name); wrap.addView(phone)
            guardianInputs.add(Triple(g, name, phone))
        }
        AlertDialog.Builder(this)
            .setTitle("Öğrenci Bilgilerini Düzenle")
            .setView(wrap)
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val gs = JSONArray()
                guardianInputs.forEach { item ->
                    gs.put(JSONObject()
                        .put("id", item.first.id)
                        .put("guardian_id", item.first.id)
                        .put("name", item.second.text.toString().trim())
                        .put("guardian_name", item.second.text.toString().trim())
                        .put("relationship", item.first.relationship)
                        .put("phone", PhoneUtil.normalize(item.third.text.toString())))
                }
                val payload = JSONObject()
                    .put("id", student.id)
                    .put("student_id", student.id)
                    .put("student_phone", PhoneUtil.normalize(studentPhone.text.toString()))
                    .put("guardians", gs)
                saveStudentEdit(payload)
            }
            .show()
    }

    private fun saveStudentEdit(payload: JSONObject) {
        val token = session.token ?: return
        Toast.makeText(this, "Bilgiler kaydediliyor…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                RehberApi.updateStudent(token, payload)
                runOnUiThread {
                    Toast.makeText(this, "Bilgiler güncellendi.", Toast.LENGTH_LONG).show()
                    syncNow(false)
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Kayıt: " + (e.message ?: "başarısız"), Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun uploadPhoto(student: CallerCache.Student, uri: Uri) {
        val token = session.token ?: return
        thread {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Fotoğraf okunamadı.")
                RehberApi.uploadStudentPhoto(token, student.id, bytes)
                runOnUiThread {
                    Toast.makeText(this, "Fotoğraf güncellendi.", Toast.LENGTH_LONG).show()
                    syncNow(false)
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Fotoğraf: " + (e.message ?: "yüklenemedi"), Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showLargePhoto(student: CallerCache.Student) {
        if (!student.hasPhoto) return
        val token = session.token ?: return
        val d = Dialog(this)
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            setOnClickListener { d.dismiss() }
        }
        d.setContentView(image)
        d.show()
        d.window?.setLayout((resources.displayMetrics.widthPixels * .96).toInt(), (resources.displayMetrics.heightPixels * .78).toInt())
        thread {
            val bytes = RehberApi.photoBytes(token, student.id, student.photoVersion) ?: return@thread
            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            runOnUiThread { image.setImageBitmap(bm) }
        }
    }

    private fun loadPhoto(image: ImageView, studentId: Long, version: Long) {
        val token = session.token ?: return
        thread {
            val bytes = RehberApi.photoBytes(token, studentId, version) ?: return@thread
            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@thread
            runOnUiThread { if (!isFinishing) image.setImageBitmap(bm) }
        }
    }

    private fun showClasses() {
        selectNav("directory")
        setToolbar("Sınıf Listeleri", true)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        val classes = cache.listClasses()
        if (classes.isEmpty()) emptyState(body, "Sınıf bilgisi bulunamadı.")
        classes.forEach { cls ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(10), dp(12))
                background = rounded(Color.WHITE, dp(16).toFloat(), Color.rgb(228, 234, 242))
                setOnClickListener { showDirectory(false, cls.name) }
            }
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@RehberActivity).apply {
                    text = cls.name
                    textSize = 17f
                    setTextColor(ink)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(this@RehberActivity).apply {
                    text = cls.studentCount.toString() + " öğrenci" + if (cls.isClassTeacher) " · Sınıfınız" else ""
                    textSize = 11f
                    setTextColor(Color.GRAY)
                })
            }
            card.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (cls.whatsappUrl.isNotBlank()) card.addView(actionSquare("W", green) { openUri(cls.whatsappUrl) })
            card.addView(TextView(this).apply { text = "›"; textSize = 28f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(36), dp(44)))
            body.addView(card, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(5), 0, dp(5)))
        }
        scroll.addView(body)
        replaceContent(scroll)
    }

    private fun showAnnouncements() {
        selectNav("ann")
        setToolbar("Duyurular", true)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        val rows = cache.listAnnouncements()
        if (rows.isEmpty()) emptyState(body, "Yeni duyuru bulunmuyor.")
        rows.forEach { a ->
            val c = if (a.priority.lowercase().contains("yüksek") || a.priority.lowercase().contains("high")) red else orange
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(Color.WHITE, dp(16).toFloat(), Color.rgb(229, 235, 242))
            }
            card.addView(TextView(this).apply {
                text = a.title.ifBlank { "Duyuru" }
                textSize = 16f
                setTextColor(ink)
                setTypeface(typeface, Typeface.BOLD)
            })
            if (a.summary.isNotBlank()) card.addView(TextView(this).apply {
                text = a.summary
                textSize = 13f
                setTextColor(Color.DKGRAY)
                setPadding(0, dp(6), 0, 0)
            })
            card.addView(TextView(this).apply {
                text = a.publishedAt
                textSize = 10.5f
                setTextColor(c)
                setPadding(0, dp(8), 0, 0)
            })
            body.addView(card, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(5), 0, dp(5)))
        }
        scroll.addView(body)
        replaceContent(scroll)
    }

    private fun showCalls(all: Boolean = false) {
        selectNav("calls")
        setToolbar(if (all) "Aramalar" else "Cevapsız Aramalar", true)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chips.addView(segment("CEVAPSIZ", !all) { showCalls(false) }, marginWeightLp(1f, dp(3), dp(3)))
        chips.addView(segment("TÜMÜ", all) { showCalls(true) }, marginWeightLp(1f, dp(3), dp(3)))
        page.addView(chips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        val rows = cache.listHistory(!all, 150)
        if (rows.isEmpty()) emptyState(body, if (all) "Arama kaydı bulunmuyor." else "Cevapsız arama bulunmuyor.")
        rows.forEach { h ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(8), dp(10))
                background = rounded(Color.WHITE, dp(15).toFloat(), Color.rgb(229, 235, 242))
                if (h.studentId > 0) setOnClickListener { showStudentDetail(h.studentId) }
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply {
                text = if (h.matched) h.guardianName.ifBlank { "Okul Rehberinde Bulundu" } else "Kayıtlı Değil"
                textSize = 15f
                setTextColor(if (h.matched) ink else red)
                setTypeface(typeface, Typeface.BOLD)
            })
            labels.addView(TextView(this).apply {
                text = if (h.matched) listOf(h.relationship, h.studentName, h.className).filter { it.isNotBlank() }.joinToString(" · ") else PhoneUtil.display(h.phone)
                textSize = 11.5f
                setTextColor(Color.DKGRAY)
            })
            labels.addView(TextView(this).apply {
                text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.callTime)) + " · " + h.status
                textSize = 10f
                setTextColor(if (h.status.contains("Cevapsız")) red else Color.GRAY)
            })
            card.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (h.phone.isNotBlank()) {
                card.addView(actionSquare("☎", blue) { dial(h.phone) })
                card.addView(actionSquare("W", green) { whatsapp(h.phone) })
            }
            body.addView(card, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(4), 0, dp(4)))
        }
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(plainButton("Listeyi Temizle", Color.rgb(236, 239, 244)) {
            AlertDialog.Builder(this).setTitle("Arama Listesi").setMessage("Arama kayıtları temizlensin mi?")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Temizle") { _, _ -> cache.clearHistory(); showCalls(all) }.show()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        replaceContent(page)
    }

    private fun showSettings() {
        selectNav("settings")
        setToolbar("Ayarlar", true)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(20))
        }
        val profile = cache.profileName().ifBlank { session.username.ifBlank { "ELAK Kullanıcısı" } }
        settingsCard(body, "Profil Bilgileri", profile + if (cache.profileRole().isNotBlank()) "\n" + cache.profileRole() else "", null)

        val role = isCallScreeningActive()
        settingsCard(body, "Arayan Kimliği", if (role) "Açık · Telefon aramalarında okul rehberi eşleşir." else "Kapalı · Etkinleştirmek için dokunun.", if (role) green else red) {
            requestCallScreeningRole()
        }
        val overlay = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) "Açık" else "Kapalı"
        settingsCard(body, "Arama Kartı", overlay + " · Gelen aramada öğrenci/veli kartını gösterir.", if (overlay == "Açık") green else orange) {
            requestOverlay()
        }
        settingsCard(body, "Cevapsız Arama Tanıma", "Telefonun cevapsız arama bildirimlerinden listeyi günceller.", purple) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        val last = if (session.lastSync > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastSync)) else "Henüz yok"
        settingsCard(body, "Veri Senkronizasyonu", "Kayıt: " + cache.count() + " · Son eşitleme: " + last, blue) {
            syncNow(true)
        }
        settingsCard(body, "Liste / Kart Görünümü", if (session.cardView) "Kart görünümü etkin" else "Liste görünümü etkin", teal) {
            session.cardView = !session.cardView
            showSettings()
        }
        settingsCard(body, "Web Rehberini Aç", "Yönetim işlemleri için güvenli SSO ile web paneline geç.", orange) {
            openWebAdmin()
        }
        settingsCard(body, "Rehber Hesabını Yeniden Bağla", if (session.token.isNullOrBlank()) "Bağlantı yok" else "Bağlı kullanıcı: " + session.username, navy) {
            showSetup()
        }
        settingsCard(body, "Gizlilik ve Güvenlik", "Rehber verileri yalnız uygulama içinde kullanılır. Arayan kimliği yerel önbellekten eşleşir.", null)
        settingsCard(body, "ELAK Okulum · Akıllı Rehber", "v0.7.0 · 1.5.1 mobil tasarım ve özellik birleşimi", null)
        scroll.addView(body)
        replaceContent(scroll)
    }

    private fun renderSettingsIfVisible() {
        if (activeScreen == "settings") showSettings()
    }

    private fun settingsCard(parent: LinearLayout, title: String, desc: String, accent: Int?, action: (() -> Unit)? = null) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, dp(16).toFloat(), Color.rgb(229, 235, 242))
            if (action != null) setOnClickListener { action() }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 15.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accent ?: ink)
        })
        card.addView(TextView(this).apply {
            text = desc
            textSize = 11.5f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, 0)
        })
        parent.addView(card, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(5), 0, dp(5)))
    }

    private fun showSetup() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(8), dp(28), 0)
        }
        wrap.addView(TextView(this).apply {
            text = if (session.token.isNullOrBlank()) "Okulum hesabıyla otomatik eşleşmediyse Akıllı Rehber hesabını yalnız bir kez tanımlayın." else "Akıllı Rehber oturumu bağlı. Buradan hesabı yenileyebilirsiniz."
            textSize = 12f
            setTextColor(Color.DKGRAY)
        })
        val user = EditText(this).apply { hint = "Kullanıcı adı"; setText(session.username) }
        val pass = EditText(this).apply { hint = "Şifre"; inputType = 0x00000081 }
        wrap.addView(user); wrap.addView(pass)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Rehber Güvenli Giriş")
            .setView(wrap)
            .setNegativeButton("Kapat", null)
            .setNeutralButton("Oturumu Temizle") { _, _ ->
                session.clear()
                Toast.makeText(this, "Rehber oturumu temizlendi.", Toast.LENGTH_LONG).show()
                showSettings()
            }
            .setPositiveButton("Giriş Yap ve Eşitle", null)
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
                        val count = cache.replaceFromSync(RehberApi.sync(login.token))
                        session.lastSync = System.currentTimeMillis()
                        runOnUiThread {
                            dialog.dismiss()
                            Toast.makeText(this, "Rehber hazır: " + count + " arayan kaydı.", Toast.LENGTH_LONG).show()
                            showHome()
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

    private fun syncNow(showToast: Boolean) {
        val token = session.token
        if (token.isNullOrBlank()) {
            if (showToast) showSetup()
            return
        }
        if (showToast) Toast.makeText(this, "Rehber eşitleniyor…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val count = cache.replaceFromSync(RehberApi.sync(token))
                session.lastSync = System.currentTimeMillis()
                runOnUiThread {
                    when (activeScreen) {
                        "home" -> showHome()
                        "directory" -> showDirectory(directoryGuardianMode, selectedClass)
                        "ann" -> showAnnouncements()
                        "calls" -> showCalls()
                        "settings" -> showSettings()
                    }
                    if (showToast) Toast.makeText(this, count.toString() + " arayan kaydı güncellendi.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (showToast) Toast.makeText(this, "Senkronizasyon: " + (e.message ?: "başarısız"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openWebAdmin() {
        val token = session.token
        if (token.isNullOrBlank()) { showSetup(); return }
        thread {
            val sso = RehberApi.webSso(token)
            runOnUiThread {
                val url = if (!sso.isNullOrBlank()) "https://elak.mcoaihl.com/rehber/index.php?mobile_sso=" + Uri.encode(sso) else "https://elak.mcoaihl.com/rehber/"
                openUri(url)
            }
        }
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            } else Toast.makeText(this, "Arayan kimliği rolü zaten etkin veya kullanılamıyor.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isCallScreeningActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try { getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING) } catch (_: Exception) { false }
    }

    private fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
        } else Toast.makeText(this, "Arama kartı izni açık.", Toast.LENGTH_SHORT).show()
    }

    private fun dial(phone: String) {
        val n = PhoneUtil.international(phone)
        if (n.isBlank()) return
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + n))) } catch (_: Exception) {}
    }

    private fun whatsapp(phone: String) {
        val n = PhoneUtil.international(phone)
        if (n.isBlank()) return
        openUri("https://wa.me/" + n)
    }

    private fun openUri(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Bağlantı açılamadı.", Toast.LENGTH_SHORT).show() }
    }

    private fun replaceContent(view: View) {
        content.removeAllViews()
        content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun setToolbar(title: String, showBack: Boolean) {
        toolbarTitle.text = " " + title
        backButton.visibility = if (showBack) View.VISIBLE else View.GONE
    }

    private fun iconButton(text: String, action: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(25, 255, 255, 255), dp(12).toFloat())
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(5) }
        }

    private fun segment(text: String, selected: Boolean, action: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else ink)
            background = rounded(if (selected) red else Color.WHITE, dp(14).toFloat(), if (selected) red else Color.rgb(225, 232, 241))
            setOnClickListener { action() }
        }

    private fun actionSquare(label: String, color: Int, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = if (label == "W") 15f else 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(color, dp(12).toFloat())
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(43), dp(43)).apply { marginStart = dp(5) }
        }

    private fun plainButton(label: String, color: Int, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (color == Color.rgb(219, 225, 234) || color == Color.rgb(236, 239, 244)) ink else Color.WHITE)
            background = rounded(color, dp(14).toFloat())
            setOnClickListener { action() }
        }

    private fun emptyState(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(dp(10), dp(42), dp(10), dp(42))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun relationShort(relation: String): String {
        val r = relation.lowercase()
        return when {
            r.contains("anne") || r.contains("mother") -> "ANNE"
            r.contains("baba") || r.contains("father") -> "BABA"
            r.contains("öğr") || r.contains("ogr") || r.contains("student") -> "ÖĞR."
            else -> "VELİ"
        }
    }

    private fun relationshipColor(relation: String): Int {
        val r = relation.lowercase()
        return when {
            r.contains("anne") || r.contains("mother") -> red
            r.contains("baba") || r.contains("father") -> blue
            else -> orange
        }
    }

    private fun rounded(color: Int, radius: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun gradientTile(color: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(color, darken(color, .82f))
        ).apply { cornerRadius = dp(24).toFloat() }

    private fun darken(color: Int, factor: Float): Int =
        Color.rgb((Color.red(color) * factor).toInt(), (Color.green(color) * factor).toInt(), (Color.blue(color) * factor).toInt())

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()

    private fun marginLp(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h).apply { setMargins(l, t, r, b) }

    private fun marginWeightLp(weight: Float, left: Int, right: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply { setMargins(left, 0, right, 0) }

    override fun onResume() {
        super.onResume()
        if (activeScreen == "settings") showSettings()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeScreen != "home") showHome() else super.onBackPressed()
    }

    private class SimpleTextWatcher(val after: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) { after() }
    }

    private class SimpleItemSelected(val selected: (Int) -> Unit) : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { selected(position) }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
