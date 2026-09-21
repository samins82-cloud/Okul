package com.elak.okulum.notification

import com.elak.okulum.OkulumSession
import com.elak.okulum.auth.CentralApi
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.concurrent.thread

class ElakFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmBootstrap.register(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val id = data["notification_id"]?.toLongOrNull() ?: return
        val title = data["title"].orEmpty().ifBlank { message.notification?.title.orEmpty() }
        val body = data["body"].orEmpty().ifBlank { message.notification?.body.orEmpty() }
        val level = data["level"].orEmpty().ifBlank { "info" }
        val createdAt = data["created_at"].orEmpty()

        val item = NotificationStore.Item(id, title, body, level, createdAt)
        NotificationStore.receivePush(applicationContext, item)

        val session = OkulumSession(applicationContext)
        if (session.hasCentralSession) {
            thread {
                try {
                    CentralApi.notificationAck(
                        session.accessToken,
                        id,
                        DeviceIdentity.id(applicationContext),
                        "delivered"
                    )
                } catch (_: Exception) { }
            }
        }
    }
}
