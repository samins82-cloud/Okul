package com.elak.okulum.rehber

import android.content.Intent
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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.concurrent.thread

class IncomingCallerActivity : AppCompatActivity() {
    private val navy = Color.rgb(10, 57, 102)
    private val ink = Color.rgb(9, 49, 88)
    private val green = Color.rgb(25, 178, 91)
    private val red = Color.rgb(245, 55, 55)
    private val blue = Color.rgb(20, 137, 227)
    private val muted = Color.rgb(115, 132, 153)

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
        val guardian = intent.getStringExtra("guardian").orEmpty()
        val relationship = intent.getStringExtra("relationship").orEmpty()
        val student = intent.getStringExtra("student").orEmpty()
        val className = intent.getStringExtra("class_name").orEmpty()
        val phone = intent.getStringExtra("phone").orEmpty()
        val studentId = intent.getLongExtra("student_id", 0L)
        val hasPhoto = intent.getBooleanExtra("has_photo", false)
        val photoVersion = intent.getLongExtra("photo_version", 0L)

        val scroll = ScrollView(this).apply { setBackgroundColor(navy) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(12), dp(24), dp(24))
            setBackgroundColor(navy)
        }

        root.addView(TextView(this).apply {
            text = "Gelen Arama"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(12))
        })

        val avatarHolder = FrameLayout(this)
        val letter = TextView(this).apply {
            text = "E"
            textSize = 54f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = circle(Color.rgb(24, 117, 136), Color.WHITE, dp(3))
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = circle(Color.TRANSPARENT, Color.WHITE, dp(3))
            clipToOutline = true
        }
        avatarHolder.addView(letter, FrameLayout.LayoutParams(dp(108), dp(108), Gravity.CENTER))
        avatarHolder.addView(photo, FrameLayout.LayoutParams(dp(108), dp(108), Gravity.CENTER))
        root.addView(avatarHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112)))
        if (hasPhoto && studentId > 0) {
            val token = RehberSession(this).token
            if (!token.isNullOrBlank()) thread {
                val bytes = PhotoStore.get(this, token, studentId, photoVersion) ?: return@thread
                val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@thread
                runOnUiThread { photo.setImageBitmap(bm) }
            }
        }

        root.addView(TextView(this).apply {
            text = student.ifBlank { "Öğrenci" }
            textSize = 26f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(12), dp(4), dp(6))
        })
        root.addView(TextView(this).apply {
            text = className
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(5), dp(14), dp(5))
            background = rounded(Color.rgb(48, 103, 157), dp(16).toFloat())
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(Color.WHITE, dp(20).toFloat())
        }
        card.addView(TextView(this).apply {
            text = "Arayan Veli"
            textSize = 11.5f
            setTextColor(muted)
        })
        card.addView(TextView(this).apply {
            text = guardian.ifBlank { relationship.ifBlank { "Veli" } }
            textSize = 21f
            setTextColor(ink)
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = relationLabel(relationship)
            textSize = 12.5f
            setTextColor(relationColor(relationship))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(3), 0, 0)
        })
        if (phone.isNotBlank()) card.addView(TextView(this).apply {
            text = displayInternational(phone)
            textSize = 18f
            setTextColor(ink)
            setPadding(0, dp(7), 0, dp(10))
        })
        card.addView(TextView(this).apply {
            text = "WhatsApp'tan Yaz"
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(green, dp(14).toFloat())
            setOnClickListener {
                val n = PhoneUtil.international(phone)
                if (n.isNotBlank()) try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n"))) } catch (_: Exception) { }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) })

        root.addView(TextView(this).apply {
            text = "✓ Okul Rehberinde Bulundu"
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(119, 235, 180))
            setPadding(0, dp(14), 0, dp(12))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(circleAction("×", "Kapat", red) {
            CallerCardNotifier.dismiss(this)
            finish()
        }, LinearLayout.LayoutParams(0, dp(112), 1f))
        actions.addView(circleAction("✓", "Rehberde Aç", green) {
            CallerCardNotifier.dismiss(this)
            startActivity(Intent(this, RehberActivity82::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("open_student_id", studentId)
            })
            finish()
        }, LinearLayout.LayoutParams(0, dp(112), 1f))
        root.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112)))

        root.addView(View(this), LinearLayout.LayoutParams(1, dp(120)))
        scroll.removeAllViews()
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun circleAction(symbol: String, label: String, color: Int, action: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }
        box.addView(TextView(this).apply {
            text = symbol
            textSize = 39f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = circle(color, Color.TRANSPARENT, 0)
        }, LinearLayout.LayoutParams(dp(72), dp(72)))
        box.addView(TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, 0)
        })
        return box
    }

    private fun relationLabel(raw: String): String {
        val v = raw.lowercase()
        return when {
            v.contains("baba") || v.contains("father") -> "Baba"
            v.contains("anne") || v.contains("mother") -> "Anne"
            else -> raw.ifBlank { "Veli" }
        }
    }

    private fun relationColor(raw: String): Int {
        val v = raw.lowercase()
        return when {
            v.contains("baba") || v.contains("father") -> blue
            v.contains("anne") || v.contains("mother") -> Color.rgb(185, 62, 119)
            else -> Color.rgb(243, 147, 33)
        }
    }

    private fun displayInternational(raw: String): String {
        val n = PhoneUtil.normalize(raw)
        return if (n.length == 10) "+90 ${n.substring(0,3)} ${n.substring(3,6)} ${n.substring(6,8)} ${n.substring(8)}" else PhoneUtil.display(raw)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun circle(color: Int, stroke: Int, width: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (width > 0) setStroke(width, stroke)
    }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()
}
