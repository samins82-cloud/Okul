package com.elak.okulum.rehber

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

object CallerOverlay {
    private var current: View? = null

    fun show(context: Context, match: CallerCache.Match) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !Settings.canDrawOverlays(context)) return
        remove(context)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 24, 34, 24)
            setBackgroundColor(Color.rgb(10, 37, 68))
            addView(TextView(context).apply {
                text = match.guardianName.ifBlank { "Okul Rehberinde Bulundu" }
                textSize = 18f
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                val rel = match.relationship.ifBlank { "Veli" }
                val student = listOf(match.studentName, match.schoolNo.takeIf { it.isNotBlank() }?.let { "No: $it" }, match.className)
                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                text = "$rel\n$student" + if (match.extraCount > 0) "\n+${match.extraCount} ilişkili kayıt" else ""
                textSize = 14f
                setTextColor(Color.LTGRAY)
            })
        }
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 90
        }
        try {
            wm.addView(box, lp)
            current = box
            box.postDelayed({ remove(context) }, 12000)
        } catch (_: Exception) { }
    }

    fun remove(context: Context) {
        val v = current ?: return
        current = null
        try { (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) { }
    }
}
