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
        val related = CallerCache(context).lookupAll(phone)
        val relatedText = related.mapNotNull { m ->
            listOf(m.studentName, m.className).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }
        }.distinct().joinToString("\n")
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
            putExtra("extra", match.extraCount)
            putExtra("related_students", relatedText)
        }
        val pending = PendingIntent.getActivity(
            context,
            (match.studentId % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rehber_call)
            .setContentTitle("Gelen Arama · " + match.studentName)
            .setContentText((match.relationship.ifBlank { "Veli" }) + " · " + PhoneUtil.display(phone))
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
                description = "Okul rehberindeki arayan veli ve öğrenci bilgisini gösterir."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }
}
