package tr.mcoaihl.elakcaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.concurrent.thread

class CallerIdService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .build()

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
            requestMethod = "GET"
            connectTimeout = 1800
            readTimeout = 1800
            setRequestProperty("Authorization","Bearer $token")
        }
        if (c.responseCode !in 200..299) return null
        val text = c.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(text).optJSONObject("best")
    }

    private fun showCaller(best: JSONObject) {
        val name = best.optString("name","ELAK Rehber")
        val detail = best.optString("detail","Kurum Rehberi")
        val phone = best.optString("phone","")
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = "elak_caller_id"
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(channelId,"ELAK Arayan Tanıma",NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Kurum rehberinde eşleşen gelen aramalar"
                enableVibration(true)
            })
        }
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("https://elak.mcoaihl.com/rehber/"))
        val pi = PendingIntent.getActivity(this,4101,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this,channelId)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(name)
            .setContentText(detail)
            .setStyle(Notification.BigTextStyle().bigText(detail + if (phone.isNotBlank()) "\n$phone" else ""))
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(Notification.PRIORITY_HIGH)
            .setColor(Color.rgb(36,111,192))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(4101,n)
    }
}
