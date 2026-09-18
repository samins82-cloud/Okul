package com.elak.okulum.rehber

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class IncomingCallerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 36, 79)
        val guardian = intent.getStringExtra("guardian").orEmpty()
        val relationship = intent.getStringExtra("relationship").orEmpty()
        val student = intent.getStringExtra("student").orEmpty()
        val schoolNo = intent.getStringExtra("school_no").orEmpty()
        val className = intent.getStringExtra("class_name").orEmpty()
        val phone = intent.getStringExtra("phone").orEmpty()
        val studentId = intent.getLongExtra("student_id", 0L)
        val hasPhoto = intent.getBooleanExtra("has_photo", false)
        val photoVersion = intent.getLongExtra("photo_version", 0L)
        val extra = intent.getIntExtra("extra", 0)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(Color.rgb(247, 249, 253))
        }
        root.addView(TextView(this).apply {
            text = "ELAK Akıllı Rehber"
            textSize = 15f
            setTextColor(Color.rgb(8, 36, 79))
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Okul Rehberinde Bulundu"
            textSize = 12f
            setTextColor(Color.rgb(22, 166, 87))
            setPadding(0, dp(4), 0, dp(14))
        })

        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(226, 233, 243), dp(50).toFloat())
            clipToOutline = true
        }
        root.addView(photo, LinearLayout.LayoutParams(dp(100), dp(100)))
        if (hasPhoto && studentId > 0) {
            val token = RehberSession(this).token
            if (!token.isNullOrBlank()) thread {
                val bytes = PhotoStore.get(this, token, studentId, photoVersion) ?: return@thread
                val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@thread
                runOnUiThread { photo.setImageBitmap(bm) }
            }
        }

        root.addView(TextView(this).apply {
            text = guardian.ifBlank { relationship.ifBlank { "Veli" } }
            textSize = 25f
            setTextColor(Color.rgb(8, 36, 79))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = relationship.ifBlank { "Veli" }
            textSize = 13f
            setTextColor(relationColor(relationship))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = listOf(student, className, if (schoolNo.isNotBlank()) "No: " + schoolNo else "").filter { it.isNotBlank() }.joinToString(" · ")
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(10), 0, dp(2))
        })
        if (extra > 0) root.addView(TextView(this).apply {
            text = "+" + extra + " ilişkili öğrenci kaydı"
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        })
        if (phone.isNotBlank()) root.addView(TextView(this).apply {
            text = PhoneUtil.display(phone)
            textSize = 15f
            setTextColor(Color.rgb(8, 36, 79))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }
        actions.addView(action("☎ Ara", Color.rgb(13,125,235)) {
            val n = PhoneUtil.international(phone)
            if (n.isNotBlank()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+" + n)))
        })
        actions.addView(action("WhatsApp", Color.rgb(18,175,84)) {
            val n = PhoneUtil.international(phone)
            if (n.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + n)))
        })
        root.addView(actions)
        root.addView(action("Kapat", Color.rgb(218,224,233)) { finish() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(18) })
        setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun action(label: String, color: Int, click: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (color == Color.rgb(218,224,233)) Color.rgb(8,36,79) else Color.WHITE)
        background = rounded(color, dp(14).toFloat())
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(5), 0, dp(5), 0) }
    }

    private fun relationColor(rel: String): Int {
        val r = rel.lowercase()
        return when {
            r.contains("anne") -> Color.rgb(235,37,43)
            r.contains("baba") -> Color.rgb(13,125,235)
            else -> Color.rgb(255,140,20)
        }
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()
}
