package com.elak.okulum.notification

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elak.okulum.OkulumSession
import kotlin.concurrent.thread

class NotificationCenterActivity : AppCompatActivity() {
    private val navy = Color.rgb(8, 31, 68)
    private val blue = Color.rgb(37, 99, 235)
    private val red = Color.rgb(220, 38, 38)
    private val green = Color.rgb(5, 150, 105)
    private val orange = Color.rgb(234, 88, 12)
    private val purple = Color.rgb(124, 58, 237)
    private val bg = Color.rgb(244, 247, 252)
    private val ink = Color.rgb(15, 34, 62)
    private val muted = Color.rgb(100, 116, 139)
    private val line = Color.rgb(224, 231, 240)

    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var countText: TextView
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var session: OkulumSession
    private var items: List<NotificationStore.Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = OkulumSession(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = navy
        window.navigationBarColor = Color.WHITE
        buildUi()
        applyInsets()
        requestNotificationPermissionIfNeeded()
        items = NotificationStore.cached(this)
        render()
        refresh()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.getLongExtra("notification_id", 0L)?.takeIf { it > 0 }?.let {
            NotificationStore.markRead(this, it)
            render()
        }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundColor(navy)
            addView(TextView(this@NotificationCenterActivity).apply {
                text = "ELAK Okulum"; textSize = 11.5f; setTextColor(Color.rgb(194, 213, 239)); setTypeface(typeface, Typeface.BOLD)
            })
            val titleRow = LinearLayout(this@NotificationCenterActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            titleRow.addView(TextView(this@NotificationCenterActivity).apply {
                text = "Bildirim Merkezi"; textSize = 23f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            titleRow.addView(TextView(this@NotificationCenterActivity).apply {
                text = "Geri"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                background = rounded(Color.argb(32,255,255,255), 11, Color.argb(70,255,255,255))
                setPadding(dp(12), dp(7), dp(12), dp(7)); setOnClickListener { finish() }
            })
            addView(titleRow)
            addView(TextView(this@NotificationCenterActivity).apply {
                text = session.schoolName.ifBlank { session.schoolCode.ifBlank { "Okul" } }
                textSize = 11.5f; setTextColor(Color.rgb(211, 224, 243)); setPadding(0, dp(4), 0, 0)
            })
        }
        root.addView(header)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(8))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        countText = TextView(this).apply { textSize = 14f; setTextColor(ink); setTypeface(typeface, Typeface.BOLD) }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        top.addView(countText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(progress, LinearLayout.LayoutParams(dp(28), dp(28)))
        controls.addView(top)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        buttons.addView(actionButton("Yenile", blue) { refresh() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        buttons.addView(actionButton("Tümünü Okundu Yap", green) {
            NotificationStore.markAllRead(this@NotificationCenterActivity, items); render()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) })
        controls.addView(buttons)
        statusText = TextView(this).apply { textSize = 11.5f; setTextColor(muted); setPadding(dp(2), dp(8), dp(2), 0) }
        controls.addView(statusText)
        root.addView(controls)

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(22)) }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun refresh() {
        progress.visibility = View.VISIBLE
        statusText.text = "ELAK CORE ile eşitleniyor…"
        thread {
            try {
                val result = NotificationStore.sync(this, postSystemNotifications = false)
                items = result.items
                runOnUiThread {
                    progress.visibility = View.GONE
                    statusText.text = if (result.freshCount > 0) "${result.freshCount} yeni bildirim alındı." else "Bildirimler güncel."
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    statusText.text = "Çevrimdışı kayıtlar gösteriliyor. ${e.message.orEmpty()}"
                    items = NotificationStore.cached(this)
                    render()
                }
            }
        }
    }

    private fun render() {
        val unread = NotificationStore.unreadCount(this, items)
        countText.text = "${items.size} bildirim · $unread okunmamış"
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Henüz aktif okul bildirimi bulunmuyor."
                textSize = 13f; gravity = Gravity.CENTER; setTextColor(muted)
                setPadding(dp(18), dp(30), dp(18), dp(30)); background = rounded(Color.WHITE, 16, line)
            })
            return
        }
        items.sortedByDescending { it.id }.forEachIndexed { index, item ->
            list.addView(notificationCard(item), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = dp(9)
            })
        }
    }

    private fun notificationCard(item: NotificationStore.Item): View {
        val read = NotificationStore.isRead(this, item.id)
        val accent = when (item.level.lowercase()) {
            "danger", "error", "critical" -> red
            "warning", "warn" -> orange
            "success" -> green
            else -> blue
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(if (read) Color.WHITE else tint(accent, .055f), 16, if (read) line else tint(accent, .22f))
            elevation = if (read) 0f else dp(1).toFloat()
            setOnClickListener {
                NotificationStore.markRead(this@NotificationCenterActivity, item.id)
                showDetail(item)
                render()
            }
            val top = LinearLayout(this@NotificationCenterActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(View(this@NotificationCenterActivity).apply { background = rounded(accent, 6) }, LinearLayout.LayoutParams(dp(6), dp(38)))
            top.addView(LinearLayout(this@NotificationCenterActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0)
                addView(TextView(this@NotificationCenterActivity).apply {
                    text = item.title.ifBlank { "Okul Bildirimi" }; textSize = 14.5f; setTextColor(ink); setTypeface(typeface, if (read) Typeface.NORMAL else Typeface.BOLD)
                    maxLines = 2
                })
                addView(TextView(this@NotificationCenterActivity).apply {
                    text = formatDate(item.createdAt); textSize = 10.5f; setTextColor(muted); setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (!read) top.addView(TextView(this@NotificationCenterActivity).apply {
                text = "YENİ"; textSize = 9.5f; setTextColor(accent); setTypeface(typeface, Typeface.BOLD)
                background = rounded(tint(accent, .11f), 9); setPadding(dp(8), dp(4), dp(8), dp(4))
            })
            addView(top)
            addView(TextView(this@NotificationCenterActivity).apply {
                text = item.body; textSize = 12f; setTextColor(muted); setPadding(dp(16), dp(10), 0, 0); maxLines = 3
            })
        }
    }

    private fun showDetail(item: NotificationStore.Item) {
        AlertDialog.Builder(this)
            .setTitle(item.title.ifBlank { "Okul Bildirimi" })
            .setMessage(item.body + if (item.createdAt.isBlank()) "" else "\n\n${formatDate(item.createdAt)}")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9301)
        }
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 12f; setTextColor(Color.WHITE); background = rounded(color, 11); setOnClickListener { action() }
    }

    private fun applyInsets() {
        WindowInsetsControllerCompat(window, root).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = true }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun formatDate(raw: String): String = raw.replace('T', ' ').take(16).ifBlank { "Tarih bilgisi yok" }

    private fun rounded(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(color)
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun tint(color: Int, amount: Float): Int {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return Color.rgb(
            (255 - (255 - r) * amount).toInt().coerceIn(0, 255),
            (255 - (255 - g) * amount).toInt().coerceIn(0, 255),
            (255 - (255 - b) * amount).toInt().coerceIn(0, 255)
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
}
