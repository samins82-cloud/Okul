package com.elak.okulum.rehber

import android.app.AlertDialog
import android.app.Dialog
import android.app.role.RoleManager
import android.content.Intent
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.elak.okulum.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import kotlin.concurrent.thread

class RehberActivity : AppCompatActivity() {
    private val navy = Color.rgb(11, 61, 145)
    private val darkNavy = Color.rgb(8, 36, 79)
    private val red = Color.rgb(225, 6, 0)
    private val blue = Color.rgb(0, 118, 238)
    private val turquoise = Color.rgb(0, 180, 198)
    private val orange = Color.rgb(255, 142, 25)
    private val purple = Color.rgb(108, 74, 238)
    private val green = Color.rgb(18, 175, 84)
    private val light = Color.rgb(247, 250, 253)
    private val soft = Color.rgb(233, 238, 245)
    private val ink = Color.rgb(8, 36, 79)
    private val muted = Color.rgb(98, 113, 132)

    private val session by lazy { RehberSession(this) }
    private val cache by lazy { CallerCache(this) }

    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerBack: TextView
    private lateinit var content: FrameLayout
    private lateinit var bottomBar: LinearLayout
    private val navItems = linkedMapOf<String, Pair<ImageView, TextView>>()

    private var activeScreen = "home"
    private var guardianMode = false
    private var selectedClass = ""
    private var cardView = false
    private var pendingPhotoStudent: CallerCache.Student? = null

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (activeScreen == "settings") showSettings()
        else if (activeScreen == "home") showHome()
    }

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
        RehberSyncWorker.schedule(this)
        cardView = session.cardView
        showHome()
        if (!session.token.isNullOrBlank()) syncNow(false)
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(light)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.WHITE)
            elevation = dp(3).toFloat()
        }
        headerBack = TextView(this).apply {
            text = "‹"
            textSize = 35f
            gravity = Gravity.CENTER
            setTextColor(navy)
            visibility = View.GONE
            setOnClickListener { showHome() }
        }
        header.addView(headerBack, LinearLayout.LayoutParams(dp(42), dp(52)))

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_elak_okulum)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        brand.addView(TextView(this).apply {
            text = "ELAK"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(red)
        })
        headerTitle = TextView(this).apply {
            text = "Akıllı Rehber"
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(darkNavy)
        }
        brand.addView(headerTitle)
        header.addView(brand, LinearLayout.LayoutParams(0, dp(52), 1f))

        header.addView(roundHeaderButton(R.drawable.ic_rehber_sync, navy) { syncNow(true) })
        header.addView(roundHeaderButton(R.drawable.ic_rehber_settings, darkNavy) { showSettings() })
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))

        content = FrameLayout(this).apply { setBackgroundColor(light) }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(3))
            setBackgroundColor(Color.WHITE)
            elevation = dp(10).toFloat()
        }
        addNav("home", R.drawable.ic_rehber_home, "Ana Sayfa") { showHome() }
        addNav("directory", R.drawable.ic_rehber_people, "Rehber") { showDirectory(false) }
        addNav("calls", R.drawable.ic_rehber_call, "Aramalar") { showCalls() }
        addNav("ann", R.drawable.ic_rehber_announcement, "Duyurular") { showAnnouncements() }
        addNav("settings", R.drawable.ic_rehber_settings, "Ayarlar") { showSettings() }
        root.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(67)))

        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun roundHeaderButton(iconRes: Int, color: Int, action: () -> Unit): View {
        return FrameLayout(this).apply {
            background = rounded(Color.rgb(244, 247, 251), dp(14).toFloat())
            setOnClickListener { action() }
            val iv = ImageView(this@RehberActivity).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(color)
                setPadding(dp(11), dp(11), dp(11), dp(11))
            }
            addView(iv, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(5) }
        }
    }

    private fun addNav(key: String, iconRes: Int, label: String, action: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.rgb(132, 143, 157))
            setPadding(dp(5), dp(4), dp(5), 0)
        }
        val text = TextView(this).apply {
            this.text = label
            textSize = 9.5f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(132, 143, 157))
        }
        box.addView(icon, LinearLayout.LayoutParams(dp(32), dp(30)))
        box.addView(text, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(23)))
        navItems[key] = icon to text
        bottomBar.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun selectNav(key: String) {
        activeScreen = key
        navItems.forEach { (k, pair) ->
            val active = k == key
            pair.first.imageTintList = ColorStateList.valueOf(if (active) red else Color.rgb(132, 143, 157))
            pair.second.setTextColor(if (active) red else Color.rgb(132, 143, 157))
            pair.second.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun setHeader(title: String, back: Boolean) {
        headerBack.visibility = if (back) View.VISIBLE else View.GONE
        headerTitle.text = title
    }

    private fun showHome() {
        selectNav("home")
        setHeader("Akıllı Rehber", false)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(18))
        }

        val profile = cache.profileName().ifBlank { session.username.ifBlank { "ELAK Kullanıcısı" } }
        val role = cache.profileRole()
        body.addView(TextView(this).apply {
            text = "ELAK Mobil"
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        body.addView(TextView(this).apply {
            text = if (role.isBlank()) profile else profile + " · " + role
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(2), 0, dp(10))
        })

        body.addView(statusStrip())

        tileRow(
            body,
            tile(R.drawable.ic_rehber_people, "Öğrenci\nRehberi", blue, cache.studentCount()) { showDirectory(false) },
            tile(R.drawable.ic_rehber_people, "Veli\nRehberi", red, cache.guardianCount()) { showDirectory(true) }
        )
        tileRow(
            body,
            tile(R.drawable.ic_rehber_class, "Sınıf\nListeleri", turquoise, cache.classCount()) { showClasses() },
            tile(R.drawable.ic_rehber_announcement, "Duyurular", orange, cache.listAnnouncements().size) { showAnnouncements() }
        )
        tileRow(
            body,
            tile(R.drawable.ic_rehber_call, "Aramalar", purple, cache.listHistory(200).size) { showCalls() },
            tile(R.drawable.ic_rehber_settings, "Ayarlar", Color.rgb(116, 139, 168), -1) { showSettings() }
        )

        val slogan = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(236, 245, 255), dp(18).toFloat())
        }
        slogan.addView(TextView(this).apply {
            text = "🎓"
            textSize = 28f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        slogan.addView(TextView(this).apply {
            text = "Daha iyi bir iletişim,\ndaha parlak yarınlar için…"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(darkNavy)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(slogan, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(8), 0, 0))
        scroll.addView(body)
        replaceContent(scroll)
    }

    private fun statusStrip(): View {
        val caller = if (isCallScreeningActive()) "Açık" else "Kapalı"
        val last = if (session.lastSync > 0) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastSync))
        } else "Henüz yok"
        return TextView(this).apply {
            text = "Arayan kimliği: " + caller + "  ·  Kayıt: " + cache.count() + "  ·  Son eşitleme: " + last
            textSize = 10.5f
            setTextColor(muted)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = rounded(Color.WHITE, dp(14).toFloat(), soft)
        }.also {
            it.layoutParams = marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(9))
        }
    }

    private fun tile(iconRes: Int, label: String, color: Int, count: Int, action: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(10))
            background = gradientTile(color)
            elevation = dp(4).toFloat()
            setOnClickListener { action() }
            addView(ImageView(this@RehberActivity).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                setPadding(dp(3), dp(3), dp(3), dp(3))
            }, LinearLayout.LayoutParams(dp(47), dp(47)))
            addView(TextView(this@RehberActivity).apply {
                text = label
                textSize = 16f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            if (count >= 0) addView(TextView(this@RehberActivity).apply {
                text = count.toString() + " kayıt"
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.argb(225, 255, 255, 255))
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun tileRow(parent: LinearLayout, left: View, right: View) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(left, weightLp(1f, dp(4), dp(4)))
        row.addView(right, weightLp(1f, dp(4), dp(4)))
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145)))
    }

    private fun showDirectory(guardian: Boolean, initialClass: String = "") {
        guardianMode = guardian
        session.lastDirectoryMode = if (guardian) "guardian" else "student"
        if (initialClass.isNotBlank()) selectedClass = initialClass
        selectNav("directory")
        setHeader(if (guardian) "Veli Rehberi" else "Öğrenci Rehberi", true)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), 0)
        }
        page.addView(statusStrip())

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.addView(segment("ÖĞRENCİ REHBERİ", !guardian) { showDirectory(false) }, weightLp(1f, dp(3), dp(3)))
        tabs.addView(segment("VELİ REHBERİ", guardian) { showDirectory(true) }, weightLp(1f, dp(3), dp(3)))
        page.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(6))
        }
        val search = EditText(this).apply {
            hint = if (guardian) "Veli adı / öğrenci adı / no ara…" else "Öğrenci adı veya numarası ile ara…"
            textSize = 13.5f
            isSingleLine = true
            setPadding(dp(13), 0, dp(10), 0)
            background = rounded(Color.WHITE, dp(16).toFloat(), soft)
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(47), 1f))
        val toggle = TextView(this).apply {
            text = if (cardView) "☷" else "▦"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(blue)
            background = rounded(Color.WHITE, dp(15).toFloat(), soft)
        }
        searchRow.addView(toggle, marginLp(dp(47), dp(47), dp(7), 0, 0, 0))
        page.addView(searchRow)

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val classes = listOf("Tüm Sınıflar") + cache.listClasses().map { it.name }.filter { it.isNotBlank() }.distinct()
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@RehberActivity, android.R.layout.simple_spinner_dropdown_item, classes)
            setSelection(classes.indexOf(selectedClass).takeIf { it >= 0 } ?: 0, false)
            background = rounded(Color.rgb(239, 244, 250), dp(13).toFloat())
        }
        filterRow.addView(spinner, LinearLayout.LayoutParams(0, dp(44), 1f))

        val groupButton = TextView(this).apply {
            text = "W Grup"
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(green, dp(13).toFloat())
            setOnClickListener { openSelectedClassWhatsapp(spinner.selectedItem?.toString().orEmpty()) }
        }
        filterRow.addView(groupButton, marginLp(dp(74), dp(44), dp(7), 0, 0, 0))
        page.addView(filterRow)

        val countText = TextView(this).apply {
            textSize = 10.5f
            setTextColor(muted)
            setPadding(dp(2), dp(6), 0, dp(4))
        }
        page.addView(countText)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun render() {
            list.removeAllViews()
            val query = search.text?.toString().orEmpty().trim()
            val spinnerClass = spinner.selectedItem?.toString().orEmpty()
            val classFilter = if (query.isNotBlank() || spinnerClass == "Tüm Sınıflar") "" else spinnerClass
            if (guardian) {
                val rows = cache.listGuardians(query, classFilter)
                countText.text = rows.size.toString() + " veli kaydı"
                if (rows.isEmpty()) emptyState(list, "Veli kaydı bulunamadı. Eşitleme için ↻ düğmesine dokunun.")
                rows.forEach { list.addView(guardianRow(it)) }
            } else {
                val rows = cache.listStudents(query, classFilter)
                countText.text = rows.size.toString() + " öğrenci"
                if (rows.isEmpty()) emptyState(list, "Öğrenci kaydı bulunamadı. Eşitleme için ↻ düğmesine dokunun.")
                rows.forEach { list.addView(studentRow(it)) }
            }
        }

        search.addTextChangedListener(SimpleTextWatcher { render() })
        spinner.onItemSelectedListener = SimpleItemSelected { position ->
            selectedClass = if (position <= 0) "" else classes[position]
            render()
        }
        toggle.setOnClickListener {
            cardView = !cardView
            session.cardView = cardView
            toggle.text = if (cardView) "☷" else "▦"
            render()
        }
        render()
        replaceContent(page)
    }

    private fun studentRow(student: CallerCache.Student): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(if (cardView) 10 else 7), dp(7), dp(if (cardView) 10 else 7))
            background = rounded(Color.WHITE, dp(if (cardView) 18 else 12).toFloat(), soft)
            elevation = if (cardView) dp(2).toFloat() else 0f
            setOnClickListener { showStudentDetail(student.id) }
        }
        val photoHolder = FrameLayout(this)
        val placeholder = TextView(this).apply {
            text = student.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Ö"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(navy)
            background = rounded(Color.rgb(228, 238, 251), dp(25).toFloat())
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.TRANSPARENT, dp(25).toFloat())
            clipToOutline = true
        }
        photoHolder.addView(placeholder, FrameLayout.LayoutParams(dp(50), dp(50)))
        photoHolder.addView(photo, FrameLayout.LayoutParams(dp(50), dp(50)))
        row.addView(photoHolder, LinearLayout.LayoutParams(dp(50), dp(50)))
        if (student.hasPhoto) loadPhoto(photo, student.id, student.photoVersion)

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, dp(4), 0)
        }
        labels.addView(TextView(this).apply {
            text = student.name.ifBlank { "Öğrenci" }
            textSize = 14.5f
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        labels.addView(TextView(this).apply {
            text = listOf(student.className, if (student.schoolNo.isNotBlank()) "No: " + student.schoolNo else "").filter { it.isNotBlank() }.joinToString(" · ")
            textSize = 10.5f
            maxLines = 1
            setTextColor(muted)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (student.phone.isNotBlank()) {
            row.addView(actionButton(R.drawable.ic_rehber_call, blue) { dial(student.phone) })
            row.addView(textActionButton("W", green) { whatsapp(student.phone) })
        }
        return rowWrap(row)
    }

    private fun guardianRow(g: CallerCache.Guardian): View {
        val relationColor = relationshipColor(g.relationship)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(if (cardView) 10 else 7), dp(7), dp(if (cardView) 10 else 7))
            background = rounded(Color.WHITE, dp(if (cardView) 18 else 12).toFloat(), soft)
            elevation = if (cardView) dp(2).toFloat() else 0f
            setOnClickListener { if (g.studentId > 0) showStudentDetail(g.studentId) }
        }
        row.addView(TextView(this).apply {
            text = relationShort(g.relationship)
            textSize = 9.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(relationColor)
            background = rounded(Color.argb(25, Color.red(relationColor), Color.green(relationColor), Color.blue(relationColor)), dp(22).toFloat())
        }, LinearLayout.LayoutParams(dp(50), dp(50)))

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, dp(4), 0)
        }
        labels.addView(TextView(this).apply {
            text = g.name.ifBlank { g.relationship.ifBlank { "Veli" } }
            textSize = 14.5f
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        labels.addView(TextView(this).apply {
            text = listOf(g.relationship, g.studentName, g.className, if (g.schoolNo.isNotBlank()) "No: " + g.schoolNo else "").filter { it.isNotBlank() }.joinToString(" · ")
            textSize = 10.5f
            maxLines = 2
            setTextColor(muted)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (g.phone.isNotBlank()) {
            row.addView(actionButton(R.drawable.ic_rehber_call, blue) { dial(g.phone) })
            row.addView(textActionButton("W", green) { whatsapp(g.phone) })
        }
        return rowWrap(row)
    }

    private fun rowWrap(view: View): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(1), dp(if (cardView) 4 else 2), dp(1), dp(if (cardView) 4 else 2))
            addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun showStudentDetail(studentId: Long) {
        val student = cache.getStudent(studentId) ?: return
        val guardians = cache.guardiansForStudent(studentId)
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(light)
        }

        val photoBox = FrameLayout(this).apply { foregroundGravity = Gravity.CENTER }
        val placeholder = TextView(this).apply {
            text = student.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Ö"
            textSize = 34f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(navy)
            background = rounded(Color.rgb(225, 236, 250), dp(55).toFloat())
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.TRANSPARENT, dp(55).toFloat())
            clipToOutline = true
            setOnClickListener { showLargePhoto(student) }
        }
        photoBox.addView(placeholder, FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER))
        photoBox.addView(photo, FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER))
        body.addView(photoBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112)))
        if (student.hasPhoto) loadPhoto(photo, student.id, student.photoVersion)

        body.addView(TextView(this).apply {
            text = student.name
            textSize = 22f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            setPadding(0, dp(10), 0, dp(2))
        })
        body.addView(TextView(this).apply {
            text = student.className
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(blue)
            setPadding(0, 0, 0, dp(10))
        })

        infoRow(body, "Öğrenci Numarası", student.schoolNo)
        if (student.phone.isNotBlank()) phoneRow(body, "Öğrenci Telefonu", student.phone, blue)

        body.addView(TextView(this).apply {
            text = "Veli İletişim Bilgileri"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            setPadding(dp(2), dp(14), 0, dp(6))
        })
        if (guardians.isEmpty()) {
            body.addView(TextView(this).apply {
                text = "Veli iletişim bilgisi bulunamadı. Eşitlemeyi yenileyin."
                textSize = 12f
                setTextColor(muted)
                setPadding(dp(10), dp(12), dp(10), dp(12))
                background = rounded(Color.WHITE, dp(14).toFloat(), soft)
            })
        } else guardians.forEach { body.addView(guardianDetailCard(it)) }

        val canEdit = student.canEdit || cache.mobileAccess().equals("both", true)
        if (canEdit) {
            val editRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            editRow.addView(plainButton("Düzenle", blue) { dialog.dismiss(); showStudentEditor(student) }, weightLp(1f, 0, dp(4)))
            editRow.addView(plainButton("Fotoğraf", orange) {
                pendingPhotoStudent = student
                photoPicker.launch("image/*")
            }, weightLp(1f, dp(4), 0))
            body.addView(editRow, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 0, dp(12), 0, 0))
        }

        body.addView(plainButton("Kapat", Color.rgb(220, 226, 234)) { dialog.dismiss() }, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 0, dp(12), 0, 0))
        scroll.addView(body)
        dialog.setContentView(scroll)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .94).toInt(), (resources.displayMetrics.heightPixels * .88).toInt())
    }

    private fun infoRow(parent: LinearLayout, title: String, value: String) {
        if (value.isBlank()) return
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.WHITE, dp(14).toFloat(), soft)
        }
        row.addView(TextView(this).apply { text = title; textSize = 9.5f; setTextColor(muted) })
        row.addView(TextView(this).apply { text = value; textSize = 13.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink) })
        parent.addView(row, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(5), 0, dp(5)))
    }

    private fun phoneRow(parent: LinearLayout, title: String, phone: String, color: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(7), dp(7))
            background = rounded(Color.WHITE, dp(14).toFloat(), soft)
        }
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@RehberActivity).apply { text = title; textSize = 9.5f; setTextColor(muted) })
            addView(TextView(this@RehberActivity).apply { text = PhoneUtil.display(phone); textSize = 13.5f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink) })
        }
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(actionButton(R.drawable.ic_rehber_call, color) { dial(phone) })
        row.addView(textActionButton("W", green) { whatsapp(phone) })
        parent.addView(row, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(5), 0, dp(5)))
    }

    private fun guardianDetailCard(g: CallerCache.Guardian): View {
        val color = relationshipColor(g.relationship)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(8), dp(7), dp(8))
            background = rounded(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)), dp(14).toFloat())
            val labels = LinearLayout(this@RehberActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@RehberActivity).apply {
                    text = g.relationship.ifBlank { "Veli" }
                    textSize = 9.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(color)
                })
                addView(TextView(this@RehberActivity).apply {
                    text = g.name.ifBlank { "Veli" }
                    textSize = 13.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ink)
                })
                if (g.phone.isNotBlank()) addView(TextView(this@RehberActivity).apply {
                    text = PhoneUtil.display(g.phone)
                    textSize = 11f
                    setTextColor(ink)
                })
            }
            addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (g.phone.isNotBlank()) {
                addView(actionButton(R.drawable.ic_rehber_call, blue) { dial(g.phone) })
                addView(textActionButton("W", green) { whatsapp(g.phone) })
            }
        }.also {
            it.layoutParams = marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(4), 0, dp(4))
        }
    }

    private fun showStudentEditor(student: CallerCache.Student) {
        val guardians = cache.guardiansForStudent(student.id)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val studentPhone = EditText(this).apply {
            hint = "Öğrenci telefonu"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(PhoneUtil.display(student.phone))
        }
        box.addView(studentPhone)
        val inputs = arrayListOf<Triple<CallerCache.Guardian, EditText, EditText>>()
        guardians.forEach { g ->
            box.addView(TextView(this).apply {
                text = g.relationship.ifBlank { "Veli" }
                setTextColor(relationshipColor(g.relationship))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            })
            val name = EditText(this).apply { hint = "Veli Ad Soyad"; setText(g.name) }
            val phone = EditText(this).apply { hint = "Telefon"; inputType = android.text.InputType.TYPE_CLASS_PHONE; setText(PhoneUtil.display(g.phone)) }
            box.addView(name)
            box.addView(phone)
            inputs.add(Triple(g, name, phone))
        }
        AlertDialog.Builder(this)
            .setTitle("Öğrenci Bilgilerini Düzenle")
            .setView(box)
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Kaydet") { _, _ ->
                val guardiansJson = JSONArray()
                inputs.forEach { item ->
                    guardiansJson.put(
                        JSONObject()
                            .put("id", item.first.id)
                            .put("relationship", item.first.relationship.ifBlank { "Veli" })
                            .put("name", item.second.text.toString().trim())
                            .put("phone", PhoneUtil.normalize(item.third.text.toString()))
                    )
                }
                val payload = JSONObject()
                    .put("student_id", student.id)
                    .put("school_no", student.schoolNo)
                    .put("name", student.name)
                    .put("student_phone", PhoneUtil.normalize(studentPhone.text.toString()))
                    .put("guardians", guardiansJson)
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
        Toast.makeText(this, "Fotoğraf yükleniyor…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Fotoğraf okunamadı.")
                RehberApi.uploadStudentPhoto(token, student.id, bytes)
                PhotoStore.removeStudent(this, student.id)
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
        val dialog = Dialog(this)
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(image)
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .96).toInt(), (resources.displayMetrics.heightPixels * .78).toInt())
        thread {
            val bytes = PhotoStore.get(this, token, student.id, student.photoVersion) ?: return@thread
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@thread
            runOnUiThread { image.setImageBitmap(bitmap) }
        }
    }

    private fun loadPhoto(image: ImageView, studentId: Long, version: Long) {
        val token = session.token ?: return
        thread {
            val bytes = PhotoStore.get(this, token, studentId, version) ?: return@thread
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@thread
            runOnUiThread { if (!isFinishing) image.setImageBitmap(bitmap) }
        }
    }

    private fun showClasses() {
        selectNav("directory")
        setHeader("Sınıf Listeleri", true)
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
                setPadding(dp(13), dp(11), dp(8), dp(11))
                background = rounded(Color.WHITE, dp(16).toFloat(), soft)
                setOnClickListener { showDirectory(false, cls.name) }
            }
            card.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_rehber_class)
                imageTintList = ColorStateList.valueOf(turquoise)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = rounded(Color.rgb(231, 249, 250), dp(14).toFloat())
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, 0, 0)
                addView(TextView(this@RehberActivity).apply {
                    text = cls.name
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ink)
                })
                addView(TextView(this@RehberActivity).apply {
                    text = cls.studentCount.toString() + " öğrenci" + if (cls.isClassTeacher) " · Sınıfınız" else ""
                    textSize = 10.5f
                    setTextColor(muted)
                })
            }
            card.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (cls.whatsappUrl.isNotBlank()) card.addView(textActionButton("W", green) { openUri(cls.whatsappUrl) })
            card.addView(TextView(this).apply { text = "›"; textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.LTGRAY) }, LinearLayout.LayoutParams(dp(34), dp(44)))
            body.addView(card, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(4), 0, dp(4)))
        }
        scroll.addView(body)
        replaceContent(scroll)
    }

    private fun showAnnouncements() {
        selectNav("ann")
        setHeader("Duyurular", true)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        val rows = cache.listAnnouncements()
        if (rows.isEmpty()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rounded(Color.WHITE, dp(17).toFloat(), soft)
            }
            card.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_rehber_announcement)
                imageTintList = ColorStateList.valueOf(orange)
            }, LinearLayout.LayoutParams(dp(38), dp(38)))
            card.addView(TextView(this).apply {
                text = "Öğrenci, veli ve telefon bilgileri değiştiğinde uygulama eşitleme ile güncellenir."
                textSize = 12.5f
                setTextColor(ink)
                setPadding(dp(10), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(card)
        } else rows.forEach { a ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(Color.WHITE, dp(16).toFloat(), soft)
                addView(TextView(this@RehberActivity).apply {
                    text = a.title.ifBlank { "Duyuru" }
                    textSize = 15.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ink)
                })
                if (a.summary.isNotBlank()) addView(TextView(this@RehberActivity).apply {
                    text = a.summary
                    textSize = 12f
                    setTextColor(muted)
                    setPadding(0, dp(5), 0, 0)
                })
                if (a.publishedAt.isNotBlank()) addView(TextView(this@RehberActivity).apply {
                    text = a.publishedAt
                    textSize = 10f
                    setTextColor(orange)
                    setPadding(0, dp(7), 0, 0)
                })
            }
            body.addView(card, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(4), 0, dp(4)))
        }
        scroll.addView(body)
        replaceContent(scroll)
    }

    private fun showCalls() {
        selectNav("calls")
        setHeader("Son Gelen Aramalar", true)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        page.addView(TextView(this).apply {
            text = "Play Protect uyumlu sürümde yalnız uygulamanın gördüğü gelen aramalar listelenir."
            textSize = 10.5f
            setTextColor(muted)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = rounded(Color.rgb(239, 245, 252), dp(14).toFloat())
        })
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(16)) }
        val rows = cache.listHistory(150)
        if (rows.isEmpty()) emptyState(body, "Henüz gelen arama kaydı yok.")
        rows.forEach { h ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(11), dp(9), dp(7), dp(9))
                background = rounded(Color.WHITE, dp(14).toFloat(), soft)
                if (h.studentId > 0) setOnClickListener { showStudentDetail(h.studentId) }
            }
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@RehberActivity).apply {
                    text = if (h.matched) h.guardianName.ifBlank { "Okul Rehberinde Bulundu" } else "Kayıtlı Değil"
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (h.matched) ink else red)
                })
                addView(TextView(this@RehberActivity).apply {
                    text = if (h.matched) listOf(h.relationship, h.studentName, h.className).filter { it.isNotBlank() }.joinToString(" · ") else PhoneUtil.display(h.phone)
                    textSize = 10.5f
                    setTextColor(muted)
                })
                addView(TextView(this@RehberActivity).apply {
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.callTime))
                    textSize = 9.5f
                    setTextColor(Color.GRAY)
                })
            }
            card.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (h.phone.isNotBlank()) {
                card.addView(actionButton(R.drawable.ic_rehber_call, blue) { dial(h.phone) })
                card.addView(textActionButton("W", green) { whatsapp(h.phone) })
            }
            body.addView(card, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(3), 0, dp(3)))
        }
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(plainButton("Arama Geçmişini Temizle", Color.rgb(232, 236, 242)) {
            AlertDialog.Builder(this)
                .setTitle("Arama Geçmişi")
                .setMessage("Uygulamadaki arama kayıtları temizlensin mi?")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Temizle") { _, _ -> cache.clearHistory(); showCalls() }
                .show()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)))
        replaceContent(page)
    }

    private fun showSettings() {
        selectNav("settings")
        setHeader("Ayarlar", true)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        val profile = cache.profileName().ifBlank { session.username.ifBlank { "ELAK Kullanıcısı" } }
        settingsCard(body, "Profil Bilgileri", profile + if (cache.profileRole().isNotBlank()) "\n" + cache.profileRole() else "", navy, null)

        val callerActive = isCallScreeningActive()
        settingsCard(
            body,
            "Arayan Kimliği",
            if (callerActive) "Açık · Gelen numara okul rehberinde yerel olarak eşleştirilir." else "Kapalı · Etkinleştirmek için dokunun.",
            if (callerActive) green else red
        ) { requestCallScreeningRole() }

        val last = if (session.lastSync > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.lastSync)) else "Henüz yok"
        settingsCard(body, "Veri Senkronizasyonu", "Öğrenci: " + cache.studentCount() + " · Veli: " + cache.guardianCount() + " · Son: " + last, blue) { syncNow(true) }
        settingsCard(body, "Gelen Arama Testi", "Rehberdeki ilk veli kaydıyla arayan kartını test edin.", purple) { launchIncomingTest() }
        settingsCard(body, "Sınıf WhatsApp Grupları", "Tanımlı sınıf grup bağlantılarını açın.", green) { openClassWhatsappGroups() }
        settingsCard(body, "Liste / Kart Görünümü", if (cardView) "Kart görünümü etkin" else "Liste görünümü etkin", turquoise) {
            cardView = !cardView
            session.cardView = cardView
            showSettings()
        }
        if (cache.mobileAccess().isBlank() || cache.mobileAccess().equals("both", true)) {
            settingsCard(body, "Web Öğrenci–Veli Rehberi", "Yönetim işlemleri için güvenli SSO ile web panelini açın.", orange) { openWebAdmin() }
        }
        settingsCard(body, "Rehber Hesabını Yeniden Bağla", if (session.token.isNullOrBlank()) "Bağlantı yok" else "Bağlı kullanıcı: " + session.username, darkNavy) { showSetup() }
        settingsCard(body, "Gizlilik ve Güvenlik", "Telefon numaraları uygulama içinde tutulur; Rehber oturumu Android Keystore ile şifrelenir.", Color.rgb(92, 108, 128), null)
        settingsCard(body, "ELAK Okulum · Akıllı Rehber", "v0.8.0 · Play Protect uyumlu · Aynı imza ile güncellenebilir", Color.rgb(92, 108, 128), null)
        scroll.addView(body)
        replaceContent(scroll)
    }

    private fun settingsCard(parent: LinearLayout, title: String, description: String, accent: Int, action: (() -> Unit)?) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(11), dp(10), dp(11))
            background = rounded(Color.WHITE, dp(16).toFloat(), soft)
            if (action != null) setOnClickListener { action() }
        }
        card.addView(TextView(this).apply {
            text = "●"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(accent)
        }, LinearLayout.LayoutParams(dp(28), dp(42)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@RehberActivity).apply {
                text = title
                textSize = 14.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ink)
            })
            addView(TextView(this@RehberActivity).apply {
                text = description
                textSize = 10.5f
                setTextColor(muted)
                setPadding(0, dp(3), 0, 0)
            })
        }
        card.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) card.addView(TextView(this).apply { text = "›"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(Color.LTGRAY) }, LinearLayout.LayoutParams(dp(30), dp(42)))
        parent.addView(card, marginLp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(4), 0, dp(4)))
    }

    private fun showSetup() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(7), dp(24), 0)
        }
        box.addView(TextView(this).apply {
            text = "Okulum hesabıyla otomatik eşleşmediyse Akıllı Rehber hesabını yalnız bir kez tanımlayın. Şifre saklanmaz."
            textSize = 11.5f
            setTextColor(muted)
        })
        val user = EditText(this).apply { hint = "Kullanıcı adı"; setText(session.username) }
        val pass = EditText(this).apply { hint = "Şifre"; inputType = 0x00000081 }
        box.addView(user)
        box.addView(pass)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Rehber Güvenli Giriş")
            .setView(box)
            .setNegativeButton("Kapat", null)
            .setNeutralButton("Oturumu Temizle") { _, _ -> session.clear(); Toast.makeText(this, "Rehber oturumu temizlendi.", Toast.LENGTH_LONG).show(); showSettings() }
            .setPositiveButton("Giriş Yap ve Eşitle", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val username = user.text.toString().trim()
                val password = pass.text.toString()
                if (username.isBlank() || password.isBlank()) {
                    Toast.makeText(this, "Kullanıcı adı ve şifre gerekli.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                thread {
                    try {
                        val login = RehberApi.login(username, password)
                        session.token = login.token
                        session.username = username
                        val count = cache.replaceFromSync(RehberApi.sync(login.token))
                        session.lastSync = System.currentTimeMillis()
                        runOnUiThread {
                            dialog.dismiss()
                            Toast.makeText(this, "Rehber hazır: " + cache.studentCount() + " öğrenci · " + cache.guardianCount() + " veli · " + count + " telefon", Toast.LENGTH_LONG).show()
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
        if (showToast) Toast.makeText(this, "Öğrenci ve veli kayıtları eşitleniyor…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val count = cache.replaceFromSync(RehberApi.sync(token))
                session.lastSync = System.currentTimeMillis()
                runOnUiThread {
                    when (activeScreen) {
                        "home" -> showHome()
                        "directory" -> showDirectory(guardianMode, selectedClass)
                        "calls" -> showCalls()
                        "ann" -> showAnnouncements()
                        "settings" -> showSettings()
                    }
                    if (showToast) Toast.makeText(this, cache.studentCount().toString() + " öğrenci · " + cache.guardianCount() + " veli · " + count + " telefon güncellendi.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { if (showToast) Toast.makeText(this, "Eşitleme: " + (e.message ?: "başarısız"), Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun launchIncomingTest() {
        val guardian = cache.listGuardians().firstOrNull()
        if (guardian == null) {
            Toast.makeText(this, "Test için önce veli kayıtlarını eşitleyin.", Toast.LENGTH_LONG).show()
            return
        }
        val student = cache.getStudent(guardian.studentId)
        startActivity(Intent(this, IncomingCallerActivity::class.java).apply {
            putExtra("guardian", guardian.name)
            putExtra("relationship", guardian.relationship)
            putExtra("student", guardian.studentName)
            putExtra("school_no", guardian.schoolNo)
            putExtra("class_name", guardian.className)
            putExtra("student_id", guardian.studentId)
            putExtra("has_photo", student?.hasPhoto ?: false)
            putExtra("photo_version", student?.photoVersion ?: 0L)
            putExtra("phone", guardian.phone)
            putExtra("extra", 0)
            putExtra("related_students", listOf(guardian.studentName, guardian.className).filter { it.isNotBlank() }.joinToString(" · "))
        })
    }

    private fun openSelectedClassWhatsapp(className: String) {
        if (className.isBlank() || className == "Tüm Sınıflar") {
            openClassWhatsappGroups()
            return
        }
        val item = cache.listClasses().firstOrNull { it.name == className }
        if (item?.whatsappUrl.isNullOrBlank()) Toast.makeText(this, "Bu sınıf için WhatsApp grup bağlantısı tanımlı değil.", Toast.LENGTH_LONG).show()
        else openUri(item!!.whatsappUrl)
    }

    private fun openClassWhatsappGroups() {
        val rows = cache.listClasses().filter { it.whatsappUrl.isNotBlank() }
        if (rows.isEmpty()) {
            Toast.makeText(this, "Yetkili olduğunuz sınıflar için WhatsApp grup bağlantısı yok.", Toast.LENGTH_LONG).show()
            return
        }
        val names = rows.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Sınıf WhatsApp Grupları").setItems(names) { _, which -> openUri(rows[which].whatsappUrl) }.show()
    }

    private fun openWebAdmin() {
        val token = session.token
        if (token.isNullOrBlank()) {
            showSetup()
            return
        }
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
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            } else Toast.makeText(this, "Arayan kimliği zaten açık veya cihazda kullanılamıyor.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isCallScreeningActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try { getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING) } catch (_: Exception) { false }
    }

    private fun dial(phone: String) {
        val n = PhoneUtil.international(phone)
        if (n.isBlank()) return
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + n))) } catch (_: Exception) { }
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

    private fun actionButton(iconRes: Int, color: Int, action: () -> Unit): View {
        return FrameLayout(this).apply {
            background = rounded(color, dp(12).toFloat())
            setOnClickListener { action() }
            addView(ImageView(this@RehberActivity).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(dp(43), dp(43)).apply { marginStart = dp(5) }
        }
    }

    private fun textActionButton(text: String, color: Int, action: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(color, dp(12).toFloat())
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(43), dp(43)).apply { marginStart = dp(5) }
        }
    }

    private fun plainButton(text: String, color: Int, action: () -> Unit): TextView {
        val pale = color == Color.rgb(220, 226, 234) || color == Color.rgb(232, 236, 242)
        return TextView(this).apply {
            this.text = text
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (pale) ink else Color.WHITE)
            background = rounded(color, dp(13).toFloat())
            setOnClickListener { action() }
        }
    }

    private fun segment(text: String, selected: Boolean, action: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else ink)
            background = rounded(if (selected) red else Color.WHITE, dp(13).toFloat(), if (selected) red else soft)
            setOnClickListener { action() }
        }
    }

    private fun emptyState(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(dp(14), dp(34), dp(14), dp(34))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun relationShort(value: String): String {
        val r = value.lowercase()
        return when {
            r.contains("anne") || r.contains("mother") -> "ANNE"
            r.contains("baba") || r.contains("father") -> "BABA"
            r.contains("öğr") || r.contains("ogr") || r.contains("student") -> "ÖĞR."
            else -> "VELİ"
        }
    }

    private fun relationshipColor(value: String): Int {
        val r = value.lowercase()
        return when {
            r.contains("anne") || r.contains("mother") -> red
            r.contains("baba") || r.contains("father") -> blue
            else -> orange
        }
    }

    private fun replaceContent(view: View) {
        content.removeAllViews()
        content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun rounded(color: Int, radius: Float, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }
    }

    private fun gradientTile(color: Int): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(lighten(color, 1.08f), darken(color, .86f))).apply {
            cornerRadius = dp(23).toFloat()
        }
    }

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    private fun lighten(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    private fun marginLp(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(w, h).apply { setMargins(l, t, r, b) }
    }

    private fun weightLp(weight: Float, left: Int, right: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply { setMargins(left, 0, right, 0) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeScreen != "home") showHome() else super.onBackPressed()
    }

    private class SimpleTextWatcher(private val after: () -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
        override fun afterTextChanged(s: android.text.Editable?) { after() }
    }

    private class SimpleItemSelected(private val selected: (Int) -> Unit) : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { selected(position) }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) { }
    }
}
