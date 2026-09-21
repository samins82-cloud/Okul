package com.elak.okulum.notification

import android.content.Context
import java.util.UUID

object DeviceIdentity {
    private const val PREFS = "elak_mobile_device"
    private const val KEY = "device_id"

    fun id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getString(KEY, "").orEmpty()
        if (old.isNotBlank()) return old
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).apply()
        return created
    }
}
