package com.elak.okulum.rehber

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MissedCallNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val all = (title + " " + text + " " + big).lowercase(java.util.Locale.forLanguageTag("tr-TR"))
        val missed = all.contains("cevapsız") || all.contains("missed call") || all.contains("cevapsiz")
        if (missed) CallerCache(this).markLatestMissed()
    }
}
