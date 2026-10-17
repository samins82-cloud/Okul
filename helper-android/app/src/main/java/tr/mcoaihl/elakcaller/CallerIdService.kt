package tr.mcoaihl.elakcaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
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
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .build()

        // Android'in çağrı akışını ağ isteği yüzünden bekletme.
        try {
            respondToCall(callDetails, response)
        } catch (_: Exception) {
        }

        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        if (number.isBlank()) return

        thread(name = "elak-caller-lookup") {
            try {
                val prefs = getSharedPreferences("elak_caller", Context.MODE_PRIVATE)
                val token = prefs.getString("token", "").orEmpty()
                val base = prefs.getString("base_url", BASE_URL).orEmpty().ifBlank { BASE_URL }
                if (token.isBlank()) return@thread

                val result = lookup(base, number, token) ?: return@thread
                if (result.optJSONObject("best") != null) {
                    showCaller(result, base, token)
                }
            } catch (_: Exception) {
                // Telefon görüşmesi akışını hiçbir koşulda bozma.
            }
        }
    }

    private fun lookup(baseRaw: String, number: String, token: String): JSONObject? {
        val base = safeBase(baseRaw)
        val url = URL(
            base + "api.php?action=caller_lookup&phone=" +
                URLEncoder.encode(number, Charsets.UTF_8.name())
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1200
            readTimeout = 1500
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "ELAK-Caller/1.2.0")
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun showCaller(result: JSONObject, base: String, token: String) {
        val best = result.optJSONObject("best") ?: return
        if (Settings.canDrawOverlays(this)) {
            showOverlay(best, base, token)
        } else {
            showNotification(best)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun typeLabel(best: JSONObject): String = when (best.optString("type")) {
        "guardian" -> "Veli"
        "student" -> "Öğrenci"
        "staff" -> "Personel"
        else -> "Kurum Rehberi"
    }

    private fun studentLine(student: JSONObject): String {
        val name = student.optString("student_name", "Öğrenci")
        val className = student.optString("class_name", "")
        val relationship = student.optString("relationship", "")
        return buildString {
            append(name)
            if (className.isNotBlank()) append(" · ").append(className)
            if (relationship.isNotBlank() && relationship != "Öğrenci") {
                append(" · ").append(relationship)
            }
        }
    }

    private fun notificationText(best: JSONObject): String {
        val students = best.optJSONArray("students") ?: JSONArray()
        if (students.length() == 0) return best.optString("detail", "Kurum Rehberi")
        val lines = mutableListOf<String>()
        for (i in 0 until students.length()) {
            val student = students.optJSONObject(i) ?: continue
            lines += studentLine(student)
        }
        return lines.joinToString("\n").ifBlank { best.optString("detail", "Kurum Rehberi") }
    }

    private fun showOverlay(best: JSONObject, base: String, token: String) {
        val name = best.optString("name", "ELAK Rehber")
        val phone = best.optString("phone", "")
        val students = best.optJSONArray("students") ?: JSONArray()
        val firstStudent = students.optJSONObject(0)
        val firstStudentId = firstStudent?.optInt("student_id", 0) ?: 0

        mainHandler.post {
            try {
                removeOverlayNow()

                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                overlayWindow = windowManager

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(15), dp(13), dp(15), dp(13))
                    background = rounded(
                        Color.WHITE,
                        20,
                        Color.rgb(211, 226, 239)
                    )
                    elevation = dp(14).toFloat()
                }

                val top = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val avatarWrap = FrameLayout(this).apply {
                    background = rounded(Color.rgb(235, 245, 255), 15)
                }
                val placeholder = TextView(this).apply {
                    text = firstStudent?.optString("student_name", name)
                        ?.trim()
                        ?.firstOrNull()
                        ?.uppercaseChar()
                        ?.toString()
                        ?: "E"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(35, 105, 166))
                    setTypeface(typeface, Typeface.BOLD)
                }
                val photo = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    visibility = View.GONE
                    clipToOutline = true
                    background = rounded(Color.rgb(235, 245, 255), 15)
                }
                avatarWrap.addView(
                    placeholder,
                    FrameLayout.LayoutParams(dp(54), dp(60))
                )
                avatarWrap.addView(
                    photo,
                    FrameLayout.LayoutParams(dp(54), dp(60))
                )

                val copy = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(8), 0)
                }
                val kicker = TextView(this).apply {
                    text = "ELAK · GELEN ARAMA"
                    textSize = 9f
                    setTextColor(Color.rgb(42, 117, 177))
                    setTypeface(typeface, Typeface.BOLD)
                }
                val title = TextView(this).apply {
                    text = name
                    textSize = 18f
                    setTextColor(Color.rgb(22, 52, 82))
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 2
                }
                val sub = TextView(this).apply {
                    text = buildString {
                        append(typeLabel(best))
                        if (students.length() > 0) {
                            append(" · ").append(students.length()).append(" öğrenci")
                        } else if (best.optString("detail").isNotBlank()) {
                            append(" · ").append(best.optString("detail"))
                        }
                    }
                    textSize = 12f
                    setTextColor(Color.rgb(93, 116, 137))
                    maxLines = 2
                }
                val phoneView = TextView(this).apply {
                    text = phone
                    textSize = 11f
                    setTextColor(Color.rgb(42, 117, 177))
                    visibility = if (phone.isBlank()) View.GONE else View.VISIBLE
                }
                copy.addView(kicker)
                copy.addView(title)
                copy.addView(sub)
                copy.addView(phoneView)

                val close = TextView(this).apply {
                    text = "×"
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(92, 110, 128))
                    background = rounded(Color.rgb(244, 247, 250), 12)
                    setOnClickListener { removeOverlay() }
                }

                top.addView(avatarWrap, LinearLayout.LayoutParams(dp(54), dp(60)))
                top.addView(
                    copy,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )
                top.addView(close, LinearLayout.LayoutParams(dp(38), dp(38)))
                card.addView(top)

                if (students.length() > 0) {
                    val studentList = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, dp(10), 0, 0)
                    }
                    for (i in 0 until students.length()) {
                        val student = students.optJSONObject(i) ?: continue
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(11), dp(8), dp(11), dp(8))
                            background = rounded(
                                Color.rgb(248, 251, 254),
                                12,
                                Color.rgb(227, 236, 244)
                            )
                        }
                        val dot = TextView(this).apply {
                            text = student.optString("student_name", "?")
                                .trim()
                                .firstOrNull()
                                ?.uppercaseChar()
                                ?.toString()
                                ?: "?"
                            textSize = 12f
                            gravity = Gravity.CENTER
                            setTextColor(Color.rgb(38, 108, 169))
                            setTypeface(typeface, Typeface.BOLD)
                            background = rounded(Color.rgb(232, 244, 255), 10)
                        }
                        val studentCopy = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(9), 0, 0, 0)
                        }
                        val studentName = TextView(this).apply {
                            text = student.optString("student_name", "Öğrenci")
                            textSize = 12f
                            setTextColor(Color.rgb(31, 58, 84))
                            setTypeface(typeface, Typeface.BOLD)
                        }
                        val studentMeta = TextView(this).apply {
                            val className = student.optString("class_name", "")
                            val relationship = student.optString("relationship", "")
                            text = listOf(className, relationship)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                            textSize = 10f
                            setTextColor(Color.rgb(103, 124, 143))
                        }
                        studentCopy.addView(studentName)
                        studentCopy.addView(studentMeta)
                        row.addView(dot, LinearLayout.LayoutParams(dp(32), dp(36)))
                        row.addView(
                            studentCopy,
                            LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        )
                        studentList.addView(
                            row,
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = if (i == students.length() - 1) 0 else dp(6) }
                        )
                    }
                    card.addView(studentList)
                } else {
                    val detail = TextView(this).apply {
                        text = best.optString("detail", "Kurum Rehberi")
                        textSize = 11f
                        setTextColor(Color.rgb(92, 113, 132))
                        setPadding(0, dp(9), 0, 0)
                    }
                    card.addView(detail)
                }

                val footer = TextView(this).apply {
                    text = "Arama engellenmez · ELAK Akıllı Rehber"
                    textSize = 9f
                    setTextColor(Color.rgb(126, 143, 159))
                    setPadding(0, dp(9), 0, 0)
                }
                card.addView(footer)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP
                    x = 0
                    y = dp(16)
                }

                val outer = LinearLayout(this).apply {
                    setPadding(dp(10), 0, dp(10), 0)
                    addView(
                        card,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }

                overlayView = outer
                windowManager.addView(outer, params)
                mainHandler.postDelayed({ removeOverlay() }, 30000)

                if (firstStudentId > 0) {
                    thread(name = "elak-caller-photo") {
                        val bitmap = loadStudentPhoto(base, firstStudentId, token)
                        if (bitmap != null) {
                            mainHandler.post {
                                if (overlayView === outer) {
                                    photo.setImageBitmap(bitmap)
                                    photo.visibility = View.VISIBLE
                                    placeholder.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                showNotification(best)
            }
        }
    }

    private fun loadStudentPhoto(baseRaw: String, studentId: Int, token: String): Bitmap? {
        if (studentId <= 0) return null
        val base = safeBase(baseRaw)
        val url = URL(base + "api.php?action=native_student_photo&id=$studentId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1200
            readTimeout = 1500
            useCaches = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "image/*")
            setRequestProperty("User-Agent", "ELAK-Caller/1.2.0")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            BitmapFactory.decodeStream(connection.inputStream)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun removeOverlayNow() {
        try {
            overlayView?.let { overlayWindow?.removeView(it) }
        } catch (_: Exception) {
        }
        overlayView = null
        overlayWindow = null
    }

    private fun removeOverlay() {
        mainHandler.post { removeOverlayNow() }
    }

    private fun showNotification(best: JSONObject) {
        val name = best.optString("name", "ELAK Rehber")
        val text = notificationText(best)
        val phone = best.optString("phone", "")
        val manager = getSystemService(NotificationManager::class.java)
        val channelId = "elak_caller_id"

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "ELAK Arayan Tanıma",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Kurum rehberinde eşleşen gelen aramalar"
                    enableVibration(true)
                }
            )
        }

        val open = Intent(Intent.ACTION_VIEW, Uri.parse(BASE_URL))
        val pendingIntent = PendingIntent.getActivity(
            this,
            4101,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = buildString {
            append(text)
            if (phone.isNotBlank()) append("\n").append(phone)
        }

        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(name)
            .setContentText(text.replace("\n", " · "))
            .setStyle(Notification.BigTextStyle().bigText(bigText))
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(Notification.PRIORITY_HIGH)
            .setColor(Color.rgb(36, 111, 192))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(4101, notification)
    }

    private fun safeBase(raw: String): String {
        return try {
            val uri = Uri.parse(raw)
            if (uri.scheme == "https" && uri.host.equals("elak.mcoaihl.com", true)) {
                val normalized = if (raw.endsWith("/")) raw else "$raw/"
                if (normalized.contains("/rehber/")) normalized else BASE_URL
            } else {
                BASE_URL
            }
        } catch (_: Exception) {
            BASE_URL
        }
    }

    override fun onDestroy() {
        removeOverlayNow()
        super.onDestroy()
    }

    companion object {
        private const val BASE_URL = "https://elak.mcoaihl.com/rehber/"
    }
}
