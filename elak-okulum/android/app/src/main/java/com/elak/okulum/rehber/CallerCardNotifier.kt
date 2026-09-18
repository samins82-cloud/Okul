package com.elak.okulum.rehber

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
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

        val intent = Intent(context, IncomingCallerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("guardian", match.guardianName)
            putExtra("relationship", match.relationship)
            putExtra("student", match.studentName)
            putExtra("school_no", match.schoolNo)
            putExtra("class_name", match.className)
            putExtra("student_id", match.studentId)
            putExtra("has_photo", match.hasPhoto)
            putExtra("photo_version", match.photoVersion)
            putExtra("phone", phone)
            putExtra("extra", (related.size - 1).coerceAtLeast(0))
        }
        val pending = PendingIntent.getActivity(
            context,
            (match.studentId % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (related.size > 1) {
            "Gelen Arama · ${related.size} öğrenci eşleşti"
        } else {
            "Gelen Arama · " + match.studentName
        }
        val content = if (related.size > 1) {
            related.map { it.studentName }.filter { it.isNotBlank() }.distinct().take(3).joinToString(" · ")
        } else {
            (match.relationship.ifBlank { "Veli" }) + " · " + PhoneUtil.display(phone)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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
            .setFullScreenIntent(pending, true)
            .build()
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) { }
        try { context.startActivity(intent) } catch (_: Exception) { }
    }

    fun dismiss(context: Context) {
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
