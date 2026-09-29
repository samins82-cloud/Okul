package com.elak.okulum.rehber

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.elak.okulum.R

object CallerCardNotifier {
    private const val CHANNEL_ID = "elak_incoming_caller"
    private const val NOTIFICATION_ID = 8202

    fun show(context: Context, match: CallerCache.Match?, phone: String) {
        if (match == null) return
        createChannel(context)

        val all = CallerCache(context).lookupAll(phone)
        val guardianMatches = all.filter {
            val r = it.relationship.lowercase()
            !(r.contains("öğrenci") || r.contains("ogrenci") || r.contains("student")) || it.guardianName.isNotBlank()
        }
        val related = (if (guardianMatches.isNotEmpty()) guardianMatches else all)
            .filter { it.studentId > 0L }
            .distinctBy { it.studentId }
        val displayMatch = match.copy(extraCount = (related.size - 1).coerceAtLeast(0))

        val intent = Intent(context, IncomingCallerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("guardian", displayMatch.guardianName)
            putExtra("relationship", displayMatch.relationship)
            putExtra("student", displayMatch.studentName)
            putExtra("school_no", displayMatch.schoolNo)
            putExtra("class_name", displayMatch.className)
            putExtra("student_id", displayMatch.studentId)
            putExtra("has_photo", displayMatch.hasPhoto)
            putExtra("photo_version", displayMatch.photoVersion)
            putExtra("phone", phone)
            putExtra("extra", displayMatch.extraCount)
        }
        val pending = PendingIntent.getActivity(
            context,
            (displayMatch.studentId % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val overlayShown = CallerOverlay.show(context, displayMatch, phone)
        val title = if (related.size > 1) {
            "Gelen Arama · ${related.size} öğrenci eşleşti"
        } else {
            "Gelen Arama · " + displayMatch.studentName
        }
        val content = if (related.size > 1) {
            related.map { it.studentName }.filter { it.isNotBlank() }.distinct().take(3).joinToString(" · ")
        } else {
            (displayMatch.relationship.ifBlank { "Veli" }) + " · " + PhoneUtil.display(phone)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rehber_call)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColor(Color.rgb(10,57,102))
            .setContentIntent(pending)

        val canFullScreen = if (Build.VERSION.SDK_INT >= 34) {
            try { manager.canUseFullScreenIntent() } catch (_: Exception) { false }
        } else true
        if (!overlayShown && canFullScreen) builder.setFullScreenIntent(pending, true)

        try { manager.notify(NOTIFICATION_ID, builder.build()) } catch (_: Exception) { }

        // Android 14+ arka plan Activity başlangıçlarını kısıtlar. Ekran üstü kart izni
        // yoksa eski cihazlarda doğrudan Activity açmayı son bir yedek olarak koruyoruz.
        if (!overlayShown && Build.VERSION.SDK_INT < 34) {
            try { context.startActivity(intent) } catch (_: Exception) { }
        }
    }

    fun dismiss(context: Context) {
        CallerOverlay.dismiss(context)
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID) } catch (_: Exception) { }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "ELAK Gelen Arama Kartı", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Okul rehberindeki arayan veli ve ilişkili öğrencileri gösterir."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
}

object CallerOverlay {
    private var windowManager: WindowManager? = null
    private var currentView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0

    fun canDraw(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(context: Context, match: CallerCache.Match, phone: String): Boolean {
        if (!canDraw(context)) return false
        val app = context.applicationContext
        return try {
            handler.post {
                try {
                    dismiss(app)
                    val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val view = buildView(app, match, phone)
                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        y = dp(app, 54)
                    }
                    wm.addView(view, params)
                    windowManager = wm
                    currentView = view
                    val myGeneration = ++generation
                    handler.postDelayed({ if (myGeneration == generation) dismiss(app) }, 45_000L)
                } catch (_: Exception) {
                    dismiss(app)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun dismiss(context: Context? = null) {
        generation++
        handler.removeCallbacksAndMessages(null)
        val view = currentView
        val fallback = context?.applicationContext?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val wm = windowManager ?: fallback
        if (view != null && wm != null) {
            try { wm.removeViewImmediate(view) } catch (_: Exception) { }
        }
        currentView = null
        windowManager = null
    }

    private fun buildView(context: Context, match: CallerCache.Match, phone: String): View {
        val navy = Color.rgb(10, 57, 102)
        val blue = Color.rgb(20, 137, 227)
        val green = Color.rgb(25, 178, 91)
        val muted = Color.rgb(92, 112, 136)
        val ink = Color.rgb(9, 49, 88)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(context, 18).toFloat()
                setStroke(dp(context, 2), navy)
            }
            elevation = dp(context, 12).toFloat()
        }

        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(context).apply {
            text = "☎  ELAK ARAYAN KİMLİĞİ"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(navy)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(TextView(context).apply {
            text = "×"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 38, 38))
            setOnClickListener { dismiss(context) }
        }, LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)))
        card.addView(head)

        val guardian = match.guardianName.ifBlank { match.relationship.ifBlank { "Kayıtlı Veli" } }
        card.addView(TextView(context).apply {
            text = guardian
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
        })
        card.addView(TextView(context).apply {
            text = match.relationship.ifBlank { "Veli" } + "  ·  " + PhoneUtil.display(phone)
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(context, 2), 0, dp(context, 7))
        })

        val studentText = buildString {
            append(match.studentName.ifBlank { "Öğrenci" })
            if (match.className.isNotBlank()) append("  ·  ").append(match.className)
            if (match.schoolNo.isNotBlank()) append("  ·  No: ").append(match.schoolNo)
            if (match.extraCount > 0) append("  (+").append(match.extraCount).append(" öğrenci)")
        }
        card.addView(TextView(context).apply {
            text = studentText
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(blue)
            setPadding(0, 0, 0, dp(context, 9))
        })

        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton(context, "KAPAT", Color.rgb(100, 116, 139)) { dismiss(context) }, LinearLayout.LayoutParams(0, dp(context, 42), 1f).apply { marginEnd = dp(context, 5) })
        actions.addView(actionButton(context, "DETAYI AÇ", green) { openFullCard(context, match, phone) }, LinearLayout.LayoutParams(0, dp(context, 42), 1f).apply { marginStart = dp(context, 5) })
        card.addView(actions)
        card.setOnClickListener { openFullCard(context, match, phone) }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun actionButton(context: Context, label: String, color: Int, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(context, 11).toFloat()
            }
            setOnClickListener { action() }
        }

    private fun openFullCard(context: Context, match: CallerCache.Match, phone: String) {
        dismiss(context)
        val intent = Intent(context, IncomingCallerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("guardian", match.guardianName)
            putExtra("relationship", match.relationship)
            putExtra("student", match.studentName)
            putExtra("school_no", match.schoolNo)
            putExtra("class_name", match.className)
            putExtra("student_id", match.studentId)
            putExtra("has_photo", match.hasPhoto)
            putExtra("photo_version", match.photoVersion)
            putExtra("phone", phone)
            putExtra("extra", match.extraCount)
        }
        try { context.startActivity(intent) } catch (_: Exception) { }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + .5f).toInt()
}
