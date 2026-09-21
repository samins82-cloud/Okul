package com.elak.okulum.notification

import android.content.Context
import com.elak.okulum.OkulumSession
import com.elak.okulum.R
import com.elak.okulum.auth.CentralApi
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.concurrent.thread

object FcmBootstrap {
    fun initialize(context: Context): Boolean {
        val appContext = context.applicationContext
        return try {
            val appId = appContext.getString(R.string.google_app_id).trim()
            val apiKey = appContext.getString(R.string.google_api_key).trim()
            val projectId = appContext.getString(R.string.project_id).trim()
            val senderId = appContext.getString(R.string.gcm_defaultSenderId).trim()
            if (appId.isBlank() || apiKey.isBlank() || projectId.isBlank() || senderId.isBlank()) return false

            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(
                    appContext,
                    FirebaseOptions.Builder()
                        .setApplicationId(appId)
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                        .setGcmSenderId(senderId)
                        .build()
                )
            }
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token.isNotBlank()) register(appContext, token)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun register(context: Context, token: String) {
        if (token.isBlank()) return
        val session = OkulumSession(context)
        if (!session.hasCentralSession) return
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.9.3.1"
        } catch (_: Exception) { "0.9.3.1" }
        thread {
            try {
                CentralApi.registerPushDevice(
                    session.accessToken,
                    token,
                    DeviceIdentity.id(context),
                    versionName
                )
            } catch (_: Exception) { }
        }
    }
}
