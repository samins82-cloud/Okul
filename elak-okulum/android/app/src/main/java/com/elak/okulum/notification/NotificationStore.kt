package com.elak.okulum.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.elak.okulum.OkulumSession
import com.elak.okulum.R
import com.elak.okulum.auth.CentralApi
import org.json.JSONArray
import org.json.JSONObject

object NotificationStore {
    data class Item(
        val id: Long,
        val title: String,
        val body: String,
        val level: String,
        val createdAt: String
    )

    data class SyncResult(
        val items: List<Item>,
        val unread: Int,
        val freshCount: Int
    )

    private const val PREFS = "elak_notification_state"
    private const val CHANNEL_ID = "elak_school_updates"

    fun sync(context: Context, postSystemNotifications: Boolean): SyncResult {
        val session = OkulumSession(context)
        val items = try {
            fetchWithRecovery(context, session)
        } catch (_: Exception) {
            cached(context)
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = accountKey(session)
        val read = prefs.getStringSet("read_$key", emptySet())?.toMutableSet() ?: mutableSetOf()
        val notified = prefs.getStringSet("notified_$key", emptySet())?.toMutableSet() ?: mutableSetOf()
        val initializedKey = "initialized_$key"
        val initialized = prefs.getBoolean(initializedKey, false)

        val ids = items.map { it.id.toString() }.toSet()
        var fresh = 0
        if (!initialized) {
            // İlk kurulumda geçmiş duyuruları sistem tepsisine yağdırma.
            notified.addAll(ids)
        } else {
            val newItems = items.filter { it.id.toString() !in notified }
            fresh = newItems.size
            if (postSystemNotifications) newItems.sortedBy { it.id }.forEach { postSystem(context, it) }
            notified.addAll(newItems.map { it.id.toString() })
        }

        // Silinen/eskimiş kayıtları tercihlerden temizle.
        read.retainAll(ids)
        notified.retainAll(ids)
        prefs.edit()
            .putBoolean(initializedKey, true)
            .putStringSet("read_$key", read)
            .putStringSet("notified_$key", notified)
            .putString("feed_$key", toJson(items).toString())
            .apply()

        return SyncResult(items, items.count { it.id.toString() !in read }, fresh)
    }

    fun cached(context: Context): List<Item> {
        val session = OkulumSession(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString("feed_${accountKey(session)}", "[]").orEmpty()
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val out = mutableListOf<Item>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out += fromJson(it) }
        return out.sortedByDescending { it.id }
    }

    fun unreadCount(context: Context, items: List<Item> = cached(context)): Int {
        val session = OkulumSession(context)
        val read = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("read_${accountKey(session)}", emptySet()).orEmpty()
        return items.count { it.id.toString() !in read }
    }

    fun isRead(context: Context, id: Long): Boolean {
        val session = OkulumSession(context)
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("read_${accountKey(session)}", emptySet()).orEmpty().contains(id.toString())
    }

    fun markRead(context: Context, id: Long) {
        val session = OkulumSession(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "read_${accountKey(session)}"
        val set = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        set += id.toString()
        prefs.edit().putStringSet(key, set).apply()
    }

    fun markAllRead(context: Context, items: List<Item>) {
        val session = OkulumSession(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("read_${accountKey(session)}", items.map { it.id.toString() }.toSet())
            .apply()
    }

    private fun fetchWithRecovery(context: Context, session: OkulumSession): List<Item> {
        if (session.hasCentralSession) {
            try { return CentralApi.announcements(session.accessToken).map { it.toItem() } }
            catch (_: Exception) { }
        }
        if (!session.hasCredentials) return cached(context)
        val auth = CentralApi.login(session.username, session.password, session.schoolCode)
        session.saveCentral(session.username, session.password, auth)
        return CentralApi.announcements(session.accessToken).map { it.toItem() }
    }

    private fun CentralApi.Announcement.toItem() = Item(id, title, body, level, createdAt)

    private fun accountKey(session: OkulumSession): String {
        val school = session.schoolCode.ifBlank { "school" }.lowercase()
        val user = session.username.ifBlank { "user" }.lowercase()
        return (school + "__" + user).replace(Regex("[^a-z0-9_]+"), "_")
    }

    private fun toJson(items: List<Item>): JSONArray = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("body", item.body)
                .put("level", item.level)
                .put("created_at", item.createdAt))
        }
    }

    private fun fromJson(o: JSONObject) = Item(
        id = o.optLong("id"),
        title = o.optString("title"),
        body = o.optString("body"),
        level = o.optString("level", "info"),
        createdAt = o.optString("created_at")
    )

    private fun postSystem(context: Context, item: Item) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        ensureChannel(context)
        val intent = Intent(context, NotificationCenterActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("notification_id", item.id)
        val pending = PendingIntent.getActivity(
            context,
            (item.id and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_elak_okulum)
            .setContentTitle(item.title.ifBlank { "ELAK Okulum" })
            .setContentText(item.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify((item.id and 0x7FFFFFFF).toInt(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Okul Bildirimleri", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Duyuru, devamsızlık, randevu ve okul bilgilendirmeleri"
            }
        )
    }
}
