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

class CallerIdServiceV121 : CallScreeningService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var overlayWindow: WindowManager? = null

    private data class PhotoSlot(
        val studentId: Int,
        val placeholder: TextView,
        val image: ImageView
    )

    override fun onScreenCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .build()

        try {
            respondToCall(callDetails, response)
        } catch (_: Exception) {
        }

        // Yalnız gelen GSM aramalarını sorgula; çağrı asla engellenmez.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        if (number.isBlank()) {
            recordCallStatus("Arama algılandı; numara gizli")
            return
        }
        recordCallStatus("Gelen arama algılandı; ELAK sorgulanıyor")
        // Android kısa ömürlü arama servisini kapatsa bile kullanıcı ilk kartı hemen görsün.
        // Numaranın telefonda kayıtlı olması gerekmez: eşleştirme yalnız ELAK/KVM üzerindendir.
        showCaller(statusCard(number, "ELAK Rehber Sorgulanıyor", "Gelen numara kontrol ediliyor", "pending"), BASE_URL, "")

        thread(name = "elak-caller-lookup") {
            try {
                val prefs = getSharedPreferences("elak_caller", Context.MODE_PRIVATE)
                val token = prefs.getString("token", "").orEmpty()
                val base = prefs.getString("base_url", BASE_URL).orEmpty().ifBlank { BASE_URL }
                if (token.isBlank()) {
                    recordCallStatus("Cihaz eşleştirilmemiş")
                    showCaller(statusCard(number, "Cihaz Eşleştirilmemiş", "ELAK Arayan Tanıma uygulamasını kuruma bağlayın", "unpaired"), base, token)
                    return@thread
                }

                val result = lookup(base, number, token)
                if (result == null) {
                    recordCallStatus("Sunucudan eşleştirme yanıtı alınamadı")
                    showCaller(statusCard(number, "Rehber Sorgulanamadı", "Sunucudan yanıt alınamadı", "unavailable"), base, token)
                    return@thread
                }
                if (result.optJSONObject("best") != null) {
                    recordCallStatus("Numara KVM ile eşleşti; kart hazırlanıyor")
                    showCaller(result, base, token)
                } else {
                    recordCallStatus("Arama algılandı; bu numara yetkili rehberde bulunamadı")
                    showCaller(statusCard(number, "Numara Tanımlanamadı", "ELAK yetkili rehberinde eşleşen kayıt bulunamadı", "unmatched"), base, token)
                }
            } catch (e: Exception) {
                // Kişisel telefon numarası ve token asla hata günlüğüne yazılmaz.
                recordCallStatus("Arama sorgusunda hata: ${e.javaClass.simpleName}")
                showCaller(statusCard(number, "Rehber Sorgulanamadı", "Arama sırasında sunucu yanıtı alınamadı", "unavailable"), BASE_URL, "")
            }
        }
    }

    private fun statusCard(number: String, name: String, detail: String, type: String): JSONObject =
        JSONObject()
            .put("type", type)
            .put("name", name)
            .put("detail", detail)
            .put("phone", number)
            .put("students", JSONArray())

    private fun recordCallStatus(message: String) {
        getSharedPreferences("elak_caller", Context.MODE_PRIVATE).edit()
            .putString("last_call_result", message)
            .putLong("last_call_at", System.currentTimeMillis())
            .apply()
    }

    private fun lookup(baseRaw: String, number: String, token: String): JSONObject? {
        val base = safeBase(baseRaw)
        val url = URL(
            base + "api.php?action=caller_lookup&phone=" +
                URLEncoder.encode(number, Charsets.UTF_8.name())
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2500
            readTimeout = 3500
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "ELAK-Caller/1.2.5")
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                recordCallStatus("Sunucu isteği HTTP $code ile başarısız")
                return null
            }
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
            recordCallStatus("Ekran üstü izni kapalı; bildirim denendi")
            try { showNotification(best) }
            catch (e: Exception) { recordCallStatus("Bildirim gösterilemedi: ${e.javaClass.simpleName}") }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun typeLabel(best: JSONObject): String = when (best.optString("type")) {
        "guardian" -> "Veli"
        "student" -> "Öğrenci"
        "staff" -> "Personel"
        "pending" -> "Arama Tanılama"
        "unmatched" -> "Kayıt Bulunamadı"
        "unavailable" -> "Bağlantı Sorunu"
        "unpaired" -> "Kurulum Gerekli"
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
        if (students.length() == 0) {
            return best.optString("detail", "Kurum Rehberi")
        }

        val lines = mutableListOf<String>()
        for (i in 0 until students.length()) {
            val student = students.optJSONObject(i) ?: continue
            lines += studentLine(student)
        }
        return lines.joinToString("\n")
            .ifBlank { best.optString("detail", "Kurum Rehberi") }
    }

    private fun createAvatarSlot(
        student: JSONObject?,
        fallbackName: String,
        compact: Boolean
    ): Pair<FrameLayout, PhotoSlot> {
        val width = if (compact) 42 else 54
        val height = if (compact) 46 else 60
        val radius = if (compact) 21 else 15
        val studentId = student?.optInt("student_id", 0) ?: 0
        val studentName = student?.optString("student_name", fallbackName)
            ?.ifBlank { fallbackName }
            ?: fallbackName
        val initial = studentName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "E"

        val wrap = FrameLayout(this).apply {
            background = rounded(Color.rgb(235, 245, 255), radius)
            clipToOutline = true
        }
        val placeholder = TextView(this).apply {
            text = initial
            textSize = if (compact) 16f else 20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(35, 105, 166))
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(Color.rgb(235, 245, 255), radius)
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            clipToOutline = true
            background = rounded(Color.rgb(235, 245, 255), radius)
        }

        wrap.addView(
            placeholder,
            FrameLayout.LayoutParams(dp(width), dp(height))
        )
        wrap.addView(
            photo,
            FrameLayout.LayoutParams(dp(width), dp(height))
        )

        return wrap to PhotoSlot(studentId, placeholder, photo)
    }

    private fun createAvatarGroup(
        students: JSONArray,
        fallbackName: String
    ): Pair<View, List<PhotoSlot>> {
        if (students.length() <= 1) {
            val pair = createAvatarSlot(students.optJSONObject(0), fallbackName, compact = false)
            return pair.first to listOf(pair.second)
        }

        val group = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val slots = mutableListOf<PhotoSlot>()
        val visibleCount = minOf(students.length(), 2)

        for (i in 0 until visibleCount) {
            val pair = createAvatarSlot(students.optJSONObject(i), fallbackName, compact = true)
            group.addView(
                pair.first,
                LinearLayout.LayoutParams(dp(42), dp(46)).apply {
                    if (i > 0) marginStart = dp(5)
                }
            )
            slots += pair.second
        }

        if (students.length() > 2) {
            val more = TextView(this).apply {
                text = "+${students.length() - 2}"
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(76, 98, 118))
                setTypeface(typeface, Typeface.BOLD)
                background = rounded(
                    Color.rgb(242, 246, 249),
                    15,
                    Color.rgb(220, 230, 238)
                )
            }
            group.addView(
                more,
                LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                    marginStart = dp(5)
                }
            )
        }

        return group to slots
    }

    private fun showOverlay(best: JSONObject, base: String, token: String) {
        val name = best.optString("name", "ELAK Rehber")
        val phone = best.optString("phone", "")
        val students = best.optJSONArray("students") ?: JSONArray()

        mainHandler.post {
            try {
                removeOverlayNow()

                val windowManager = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager
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

                val avatarPair = createAvatarGroup(students, name)
                val avatarView = avatarPair.first
                val photoSlots = avatarPair.second

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

                val avatarWidth = when {
                    students.length() <= 1 -> dp(54)
                    students.length() == 2 -> dp(89)
                    else -> dp(126)
                }
                top.addView(
                    avatarView,
                    LinearLayout.LayoutParams(avatarWidth, dp(60))
                )
                top.addView(
                    copy,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
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
                            ).apply {
                                bottomMargin =
                                    if (i == students.length() - 1) 0 else dp(6)
                            }
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
                val cardStatus = when (best.optString("type")) {
                    "pending" -> "Gelen arama algılandı; kart açıldı, ELAK sorgulanıyor"
                    "unmatched" -> "Arama kartı gösterildi; yetkili rehberde numara eşleşmedi"
                    "unavailable" -> "Arama kartı gösterildi; sunucu yanıtı alınamadı"
                    "unpaired" -> "Arama kartı gösterildi; cihaz eşleştirilmemiş"
                    else -> "Numara eşleşti; arama kartı Android ekranında gösterildi"
                }
                recordCallStatus(cardStatus)
                // Önceki aramanın kapanma işi yeni aramanın kartını kapatmasın.
                mainHandler.postDelayed({
                    if (overlayView === outer) removeOverlayNow()
                }, 30000)

                photoSlots.forEachIndexed { index, slot ->
                    if (slot.studentId <= 0) return@forEachIndexed
                    thread(name = "elak-caller-photo-${index + 1}") {
                        val bitmap = loadStudentPhoto(base, slot.studentId, token)
                        if (bitmap != null) {
                            mainHandler.post {
                                if (overlayView === outer) {
                                    slot.image.setImageBitmap(bitmap)
                                    slot.image.visibility = View.VISIBLE
                                    slot.placeholder.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                recordCallStatus("Ekran kartı açılamadı: ${e.javaClass.simpleName}; bildirim denendi")
                try { showNotification(best) }
                catch (_: Exception) { recordCallStatus("Kart ve bildirim gösterilemedi: ${e.javaClass.simpleName}") }
            }
        }
    }

    private fun loadStudentPhoto(
        baseRaw: String,
        studentId: Int,
        token: String
    ): Bitmap? {
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
            setRequestProperty("User-Agent", "ELAK-Caller/1.2.5")
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
            if (
                uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("elak.mcoaihl.com", ignoreCase = true)
            ) {
                BASE_URL
            } else {
                BASE_URL
            }
        } catch (_: Exception) {
            BASE_URL
        }
    }

    override fun onDestroy() {
        // CallScreeningService kısa ömürlüdür. Kartı burada kaldırırsak çağrı sırasında
        // anlık görünüp kaybolabilir; 30 saniyelik kapatma görevine bırakılır.
        super.onDestroy()
    }

    companion object {
        private const val BASE_URL = "https://elak.mcoaihl.com/rehber/"
    }
}
