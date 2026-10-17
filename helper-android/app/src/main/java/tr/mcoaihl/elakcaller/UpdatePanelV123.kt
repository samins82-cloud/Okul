package tr.mcoaihl.elakcaller

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdatePanelV123 {
    private const val PANEL_TAG = "elak_update_panel_v123"
    private const val STATUS_TAG = "elak_update_status_v123"
    private const val UPDATE_META_URL =
        "https://elak.mcoaihl.com/rehber/downloads/ELAK-Arayan-Tanima.json"

    fun attach(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val decor = activity.window?.decorView ?: return
        val existing = decor.findViewWithTag<View>(PANEL_TAG)
        if (existing != null) return

        val scroll = findScrollView(decor) ?: return
        val root = scroll.getChildAt(0) as? LinearLayout ?: return

        val density = activity.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(radiusDp).toFloat()
                if (strokeColor != null) setStroke(dp(1), strokeColor)
            }

        val card = LinearLayout(activity).apply {
            tag = PANEL_TAG
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.WHITE, 18, Color.rgb(219, 231, 241))
            elevation = dp(3).toFloat()
        }

        val title = TextView(activity).apply {
            text = "Uygulama Güncellemesi"
            textSize = 14f
            setTextColor(Color.rgb(30, 62, 92))
            setTypeface(typeface, Typeface.BOLD)
        }
        val info = TextView(activity).apply {
            text = "Yeni sürüm bulunduğunda ELAK sizi uyarır. İsterseniz aşağıdaki düğmeyle hemen kontrol edebilirsiniz."
            textSize = 11f
            setTextColor(Color.rgb(92, 112, 132))
            setPadding(0, dp(6), 0, dp(10))
        }
        val status = TextView(activity).apply {
            tag = STATUS_TAG
            text = "v${currentVersionName(activity)} · Otomatik güncelleme aktif"
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setTextColor(Color.rgb(28, 126, 91))
            background = rounded(Color.rgb(235, 248, 242), 12)
        }
        val button = Button(activity).apply {
            text = "Güncellemeyi Kontrol Et"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(33, 108, 174), 13)
            minHeight = dp(46)
            minimumHeight = dp(46)
            setOnClickListener {
                checkNow(activity, status, this)
            }
        }

        card.addView(title)
        card.addView(info)
        card.addView(status)
        card.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        val insertAt = (root.childCount - 1).coerceAtLeast(0)
        root.addView(
            card,
            insertAt,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        )
    }

    private fun checkNow(activity: Activity, status: TextView, button: Button) {
        if (activity.isFinishing || activity.isDestroyed) return
        button.isEnabled = false
        status.text = "Güncelleme kontrol ediliyor…"
        status.setTextColor(Color.rgb(42, 117, 177))

        thread(name = "elak-manual-update-status") {
            try {
                val url = URL("$UPDATE_META_URL?t=${System.currentTimeMillis()}")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 5000
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("User-Agent", "ELAK-Caller-Manual-Check/${currentVersionName(activity)}")
                }

                val json = try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw IllegalStateException("HTTP $code")
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }

                val latestCode = json.optLong("version_code", 0L)
                val latestVersion = json.optString("version", "").trim()
                val currentCode = currentVersionCode(activity)

                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    button.isEnabled = true
                    if (latestCode > currentCode) {
                        status.text = "Yeni sürüm: v${latestVersion.ifBlank { "?" }}"
                        status.setTextColor(Color.rgb(176, 91, 24))
                        Toast.makeText(activity, "Yeni ELAK sürümü bulundu.", Toast.LENGTH_SHORT).show()
                        UpdateManager.checkForUpdates(activity, silent = false, force = true)
                    } else {
                        status.text = "v${currentVersionName(activity)} · Güncel"
                        status.setTextColor(Color.rgb(28, 126, 91))
                        Toast.makeText(activity, "Uygulama güncel.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    button.isEnabled = true
                    status.text = "v${currentVersionName(activity)} · Kontrol edilemedi"
                    status.setTextColor(Color.rgb(166, 95, 26))
                    Toast.makeText(
                        activity,
                        "Güncelleme kontrolü yapılamadı: ${e.message ?: "Bağlantı hatası"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun findScrollView(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findScrollView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun currentVersionCode(activity: Activity): Long =
        activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode

    private fun currentVersionName(activity: Activity): String =
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?"
}
