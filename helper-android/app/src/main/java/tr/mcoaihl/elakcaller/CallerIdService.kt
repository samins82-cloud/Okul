package tr.mcoaihl.elakcaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.concurrent.thread

class CallerIdService : CallScreeningService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var overlayWindow: WindowManager? = null

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val response = CallResponse.Builder().setDisallowCall(false).setRejectCall(false).setSilenceCall(false).build()
        thread {
            try {
                val prefs = getSharedPreferences("elak_caller", Context.MODE_PRIVATE)
                val token = prefs.getString("token","").orEmpty()
                val base = prefs.getString("base_url","https://elak.mcoaihl.com/rehber/") ?: "https://elak.mcoaihl.com/rehber/"
                if (token.isNotBlank() && number.isNotBlank()) {
                    val best = lookup(base, number, token)
                    if (best != null) showCaller(best)
                }
            } catch (_: Exception) {
            } finally {
                try { respondToCall(callDetails, response) } catch (_: Exception) {}
            }
        }
    }

    private fun lookup(baseRaw: String, number: String, token: String): JSONObject? {
        val base = if (baseRaw.endsWith("/")) baseRaw else "$baseRaw/"
        val u = URL(base + "api.php?action=caller_lookup&phone=" + URLEncoder.encode(number,"UTF-8"))
        val c = (u.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 1800; readTimeout = 1800; setRequestProperty("Authorization","Bearer $token")
        }
        if (c.responseCode !in 200..299) return null
        val text = c.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(text).optJSONObject("best")
    }

    private fun showCaller(best: JSONObject) {
        if (Settings.canDrawOverlays(this)) showOverlay(best) else showNotification(best)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radiusDp).toFloat() }

    private fun showOverlay(best: JSONObject) {
        val name = best.optString("name","ELAK Rehber")
        val detail = best.optString("detail","Kurum Rehberi")
        val phone = best.optString("phone","")
        mainHandler.post {
            try {
                removeOverlay()
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                overlayWindow = wm
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18),dp(14),dp(18),dp(14))
                    background = rounded(Color.WHITE,18)
                    elevation = dp(12).toFloat()
                }
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val icon = TextView(this).apply {
                    text = "☎"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(Color.rgb(29,103,173),14)
                    setPadding(dp(10),dp(8),dp(10),dp(8))
                }
                val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,dp(8),0) }
                val title = TextView(this).apply { text = name; textSize = 18f; setTextColor(Color.rgb(22,43,66)); setTypeface(typeface,Typeface.BOLD) }
                val sub = TextView(this).apply { text = detail; textSize = 13f; setTextColor(Color.rgb(72,94,116)) }
                val ph = TextView(this).apply { text = phone; textSize = 12f; setTextColor(Color.rgb(29,103,173)); visibility = if(phone.isBlank()) View.GONE else View.VISIBLE }
                copy.addView(title); copy.addView(sub); copy.addView(ph)
                val close = Button(this).apply {
                    text = "×"; textSize = 18f; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0; setPadding(dp(10),dp(4),dp(10),dp(4)); setOnClickListener { removeOverlay() }
                }
                top.addView(icon,LinearLayout.LayoutParams(dp(46),dp(46)))
                top.addView(copy,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
                top.addView(close,LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT))
                val brand = TextView(this).apply { text = "ELAK Akıllı Rehber · Arayan Tanındı"; textSize = 10f; setTextColor(Color.rgb(108,126,144)); setPadding(0,dp(9),0,0) }
                card.addView(top); card.addView(brand)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP; x = 0; y = dp(18) }
                val outer = LinearLayout(this).apply { setPadding(dp(12),0,dp(12),0); addView(card,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)) }
                overlayView = outer
                wm.addView(outer,params)
                mainHandler.postDelayed({ removeOverlay() },30000)
            } catch (_: Exception) { showNotification(best) }
        }
    }

    private fun removeOverlay() {
        mainHandler.post {
            try { overlayView?.let { overlayWindow?.removeView(it) } } catch (_: Exception) {}
            overlayView = null; overlayWindow = null
        }
    }

    private fun showNotification(best: JSONObject) {
        val name = best.optString("name","ELAK Rehber")
        val detail = best.optString("detail","Kurum Rehberi")
        val phone = best.optString("phone","")
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = "elak_caller_id"
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(channelId,"ELAK Arayan Tanıma",NotificationManager.IMPORTANCE_HIGH).apply { description="Kurum rehberinde eşleşen gelen aramalar"; enableVibration(true) })
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("https://elak.mcoaihl.com/rehber/"))
        val pi = PendingIntent.getActivity(this,4101,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this,channelId).setSmallIcon(android.R.drawable.sym_action_call).setContentTitle(name).setContentText(detail).setStyle(Notification.BigTextStyle().bigText(detail + if (phone.isNotBlank()) "\n$phone" else "")).setCategory(Notification.CATEGORY_CALL).setPriority(Notification.PRIORITY_HIGH).setColor(Color.rgb(36,111,192)).setContentIntent(pi).setAutoCancel(true).build()
        nm.notify(4101,n)
    }

    override fun onDestroy() { removeOverlay(); super.onDestroy() }
}
