package com.elak.okulum.rehber

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 26, 56)
        val guardian = intent.getStringExtra("guardian").orEmpty()
        val relationship = intent.getStringExtra("relationship").orEmpty()
        val student = intent.getStringExtra("student").orEmpty()
        val schoolNo = intent.getStringExtra("school_no").orEmpty()
        val className = intent.getStringExtra("class_name").orEmpty()
        val phone = intent.getStringExtra("phone").orEmpty()
        val extra = intent.getIntExtra("extra", 0)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(34, 44, 34, 34)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "ELAK Okulum · Arayan Bilgisi"
            textSize = 14f
            setTextColor(Color.DKGRAY)
        })
        root.addView(TextView(this).apply {
            text = guardian.ifBlank { "Okul Rehberinde Bulundu" }
            textSize = 26f
            setTextColor(Color.rgb(8, 26, 56))
            setPadding(0, 28, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = listOf(
                relationship.ifBlank { "Veli" },
                student,
                schoolNo.takeIf { it.isNotBlank() }?.let { "No: $it" },
                className
            ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ") +
                if (extra > 0) "\n+$extra ilişkili kayıt" else ""
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 0)
        }
        actions.addView(Button(this).apply {
            text = "WhatsApp"
            setOnClickListener {
                val n = PhoneUtil.normalize(phone)
                val intl = if (n.length == 10) "90$n" else n
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$intl"))) } catch (_: Exception) { }
            }
        })
        actions.addView(Button(this).apply { text = "Kapat"; setOnClickListener { finish() } })
        root.addView(actions)
        setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
}
