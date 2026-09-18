package com.elak.okulum.rehber

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class IncomingCallerActivity : AppCompatActivity() {
    private val navy = Color.rgb(10, 57, 102)
    private val ink = Color.rgb(9, 49, 88)
    private val green = Color.rgb(25, 178, 91)
    private val red = Color.rgb(245, 55, 55)
    private val blue = Color.rgb(20, 137, 227)
    private val muted = Color.rgb(115, 132, 153)
    private val mother = Color.rgb(185, 62, 119)
    private val orange = Color.rgb(243, 147, 33)

    private var relatedMatches: List<CallerCache.Match> = emptyList()
    private var incomingPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        incomingPhone = intent.getStringExtra("phone").orEmpty()
        val fallbackGuardian = intent.getStringExtra("guardian").orEmpty()
        val fallbackRelationship = intent.getStringExtra("relationship").orEmpty()
        val fallbackStudent = intent.getStringExtra("student").orEmpty()
        val fallbackClass = intent.getStringExtra("class_name").orEmpty()
        val fallbackSchoolNo = intent.getStringExtra("school_no").orEmpty()
        val fallbackStudentId = intent.getLongExtra("student_id", 0L)
        val fallbackHasPhoto = intent.getBooleanExtra("has_photo", false)
        val fallbackPhotoVersion = intent.getLongExtra("photo_version", 0L)

        val all = if (incomingPhone.isNotBlank()) CallerCache(this).lookupAll(incomingPhone) else emptyList()
        val guardianMatches = all.filter { !isStudentRelationship(it.relationship) || it.guardianName.isNotBlank() }
        relatedMatches = (if (guardianMatches.isNotEmpty()) guardianMatches else all)
            .filter { it.studentId > 0L }
            .distinctBy { it.studentId }

        if (relatedMatches.isEmpty() && fallbackStudentId > 0L) {
            relatedMatches = listOf(
                CallerCache.Match(
                    guardianName = fallbackGuardian,
                    relationship = fallbackRelationship,
                    studentName = fallbackStudent,
                    schoolNo = fallbackSchoolNo,
                    className = fallbackClass,
                    studentId = fallbackStudentId,
                    hasPhoto = fallbackHasPhoto,
                    photoVersion = fallbackPhotoVersion,
                    phone = PhoneUtil.normalize(incomingPhone),
                    extraCount = 0
                )
            )
        }

        val primary = relatedMatches.firstOrNull()
        val guardianName = relatedMatches.map { it.guardianName.trim() }.filter { it.isNotBlank() }.distinct().firstOrNull()
            ?: fallbackGuardian.ifBlank { fallbackRelationship.ifBlank { "Veli" } }
        val relationships = relatedMatches.map { relationLabel(it.relationship) }.filter { it.isNotBlank() }.distinct()
        val relationshipText = when {
            relationships.isNotEmpty() -> relationships.joinToString(" / ")
            fallbackRelationship.isNotBlank() -> relationLabel(fallbackRelationship)
            else -> "Veli"
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(navy)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(10), dp(20), dp(24))
            setBackgroundColor(navy)
        }

        root.addView(TextView(this).apply {
            text = "Gelen Arama"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        })

        val avatarHolder = FrameLayout(this)
        val avatarText = TextView(this).apply {
            text = if (relatedMatches.size > 1) relatedMatches.size.toString() else primary?.studentName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "E"
            textSize = if (relatedMatches.size > 1) 38f else 54f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = circle(Color.rgb(24, 117, 136), Color.WHITE, dp(3))
        }
        val primaryPhoto = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = circle(Color.TRANSPARENT, Color.WHITE, dp(3))
            clipToOutline = true
        }
        avatarHolder.addView(avatarText, FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER))
        avatarHolder.addView(primaryPhoto, FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER))
        root.addView(avatarHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(108)))

        if (relatedMatches.size == 1 && primary != null && (primary.hasPhoto || primary.photoVersion > 0L)) {
            val token = RehberSession(this).token.orEmpty()
            if (token.isNotBlank()) {
                FastPhotoLoader.load(this, token, primary.studentId, primary.photoVersion, primaryPhoto) {
                    avatarText.visibility = View.INVISIBLE
                }
            }
        } else {
            primaryPhoto.visibility = View.GONE
        }

        root.addView(TextView(this).apply {
            text = if (relatedMatches.size > 1) {
                relatedMatches.size.toString() + " Öğrenci Eşleşti"
            } else {
                primary?.studentName?.ifBlank { fallbackStudent.ifBlank { "Öğrenci" } } ?: fallbackStudent.ifBlank { "Öğrenci" }
            }
            textSize = if (relatedMatches.size > 1) 22f else 25f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(10), dp(4), dp(4))
        })

        if (relatedMatches.size == 1) {
            val cls = primary?.className.orEmpty().ifBlank { fallbackClass }
            if (cls.isNotBlank()) root.addView(classBadge(cls))
        } else {
            root.addView(TextView(this).apply {
                text = "Aynı veli numarasına bağlı kardeş kayıtları"
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(196, 217, 238))
                setPadding(0, dp(2), 0, dp(2))
            })
        }

        val guardianCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(14), dp(17), dp(14))
            background = rounded(Color.WHITE, dp(19).toFloat())
        }
        guardianCard.addView(TextView(this).apply {
            text = "Arayan Veli"
            textSize = 11f
            setTextColor(muted)
        })
        guardianCard.addView(TextView(this).apply {
            text = guardianName
            textSize = 20f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, 0)
        })
        guardianCard.addView(TextView(this).apply {
            text = relationshipText
            textSize = 12.5f
            setTextColor(if (relationships.any { it == "Anne" }) mother else if (relationships.any { it == "Baba" }) blue else orange)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, 0)
        })
        if (incomingPhone.isNotBlank()) guardianCard.addView(TextView(this).apply {
            text = displayInternational(incomingPhone)
            textSize = 17.5f
            setTextColor(ink)
            setPadding(0, dp(6), 0, dp(9))
        })
        guardianCard.addView(TextView(this).apply {
            text = "WhatsApp'tan Yaz"
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(green, dp(13).toFloat())
            setOnClickListener {
                val n = PhoneUtil.international(incomingPhone)
                if (n.isNotBlank()) try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n"))) } catch (_: Exception) { }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)))
        root.addView(guardianCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(15) })

        if (relatedMatches.isNotEmpty()) {
            val studentsCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(8))
                background = rounded(Color.WHITE, dp(19).toFloat())
            }
            studentsCard.addView(TextView(this).apply {
                text = if (relatedMatches.size > 1) "İlişkili Öğrenciler (${relatedMatches.size})" else "Öğrenci"
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ink)
                setPadding(dp(4), 0, 0, dp(7))
            })
            relatedMatches.forEachIndexed { index, match ->
                studentsCard.addView(studentMatchRow(match))
                if (index < relatedMatches.lastIndex) {
                    studentsCard.addView(View(this).apply { setBackgroundColor(Color.rgb(232, 237, 243)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { leftMargin = dp(5); rightMargin = dp(5) })
                }
            }
            root.addView(studentsCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }

        root.addView(TextView(this).apply {
            text = "✓ Okul Rehberinde Bulundu"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(119, 235, 180))
            setPadding(0, dp(12), 0, dp(8))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(circleAction("×", "Kapat", red) {
            CallerCardNotifier.dismiss(this)
            finish()
        }, LinearLayout.LayoutParams(0, dp(104), 1f))
        actions.addView(circleAction("✓", "Rehberde Aç", green) {
            openRelatedStudent()
        }, LinearLayout.LayoutParams(0, dp(104), 1f))
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)))

        root.addView(View(this), LinearLayout.LayoutParams(1, dp(72)))
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun studentMatchRow(match: CallerCache.Match): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(7), dp(3), dp(7))
            setOnClickListener { openStudent(match.studentId) }
        }

        val avatar = FrameLayout(this)
        val letter = TextView(this).apply {
            text = match.studentName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Ö"
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(navy)
            background = circle(Color.rgb(225, 238, 247), Color.TRANSPARENT, 0)
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = circle(Color.TRANSPARENT, Color.TRANSPARENT, 0)
            clipToOutline = true
        }
        avatar.addView(letter, FrameLayout.LayoutParams(dp(50), dp(50)))
        avatar.addView(photo, FrameLayout.LayoutParams(dp(50), dp(50)))
        row.addView(avatar, LinearLayout.LayoutParams(dp(50), dp(50)))

        val token = RehberSession(this).token.orEmpty()
        if (token.isNotBlank() && match.studentId > 0 && (match.hasPhoto || match.photoVersion > 0L)) {
            FastPhotoLoader.load(this, token, match.studentId, match.photoVersion, photo) {
                letter.visibility = View.INVISIBLE
            }
        } else {
            photo.visibility = View.GONE
        }

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, dp(5), 0)
        }
        labels.addView(TextView(this).apply {
            text = match.studentName.ifBlank { "Öğrenci" }
            textSize = 14f
            maxLines = 1
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        labels.addView(TextView(this).apply {
            text = listOf(match.className, if (match.schoolNo.isNotBlank()) "No: ${match.schoolNo}" else "").filter { it.isNotBlank() }.joinToString(" · ")
            textSize = 10.5f
            setTextColor(muted)
        })
        labels.addView(TextView(this).apply {
            text = relationLabel(match.relationship)
            textSize = 10.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(relationColor(match.relationship))
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(dp(28), dp(48)))
        return row
    }

    private fun openRelatedStudent() {
        if (relatedMatches.isEmpty()) {
            CallerCardNotifier.dismiss(this)
            finish()
            return
        }
        if (relatedMatches.size == 1) {
            openStudent(relatedMatches.first().studentId)
            return
        }
        val items = relatedMatches.map {
            listOf(it.studentName, it.className, if (it.schoolNo.isNotBlank()) "No: ${it.schoolNo}" else "")
                .filter { part -> part.isNotBlank() }
                .joinToString(" · ")
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Rehberde açılacak öğrenciyi seçin")
            .setItems(items) { _, which -> openStudent(relatedMatches[which].studentId) }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun openStudent(studentId: Long) {
        if (studentId <= 0L) return
        CallerCardNotifier.dismiss(this)
        startActivity(Intent(this, RehberActivity84::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("open_student_id", studentId)
        })
        finish()
    }

    private fun classBadge(className: String): View = TextView(this).apply {
        text = className
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(dp(14), dp(5), dp(14), dp(5))
        background = rounded(Color.rgb(48, 103, 157), dp(16).toFloat())
    }

    private fun circleAction(symbol: String, label: String, color: Int, action: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }
        box.addView(TextView(this).apply {
            text = symbol
            textSize = 37f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = circle(color, Color.TRANSPARENT, 0)
        }, LinearLayout.LayoutParams(dp(68), dp(68)))
        box.addView(TextView(this).apply {
            text = label
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(3), 0, 0)
        })
        return box
    }

    private fun isStudentRelationship(raw: String): Boolean {
        val v = raw.lowercase()
        return v.contains("öğrenci") || v.contains("ogrenci") || v.contains("student")
    }

    private fun relationLabel(raw: String): String {
        val v = raw.lowercase()
        return when {
            v.contains("baba") || v.contains("father") -> "Baba"
            v.contains("anne") || v.contains("mother") -> "Anne"
            v.contains("diğer") || v.contains("diger") || v.contains("other") -> "Diğer Veli"
            v.contains("öğrenci") || v.contains("ogrenci") || v.contains("student") -> "Öğrenci"
            else -> raw.ifBlank { "Veli" }
        }
    }

    private fun relationColor(raw: String): Int {
        val v = raw.lowercase()
        return when {
            v.contains("baba") || v.contains("father") -> blue
            v.contains("anne") || v.contains("mother") -> mother
            else -> orange
        }
    }

    private fun displayInternational(raw: String): String {
        val n = PhoneUtil.normalize(raw)
        return if (n.length == 10) "+90 ${n.substring(0,3)} ${n.substring(3,6)} ${n.substring(6,8)} ${n.substring(8)}" else PhoneUtil.display(raw)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun circle(color: Int, stroke: Int, width: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (width > 0) setStroke(width, stroke)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()
}
