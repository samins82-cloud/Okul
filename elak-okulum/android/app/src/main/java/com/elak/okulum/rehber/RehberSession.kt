package com.elak.okulum.rehber

import android.content.Context

class RehberSession(context: Context) {
    private val prefs = context.getSharedPreferences("elak_okulum_rehber", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString("token", value).apply() }

    var username: String
        get() = prefs.getString("username", "").orEmpty()
        set(value) { prefs.edit().putString("username", value).apply() }

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(value) { prefs.edit().putLong("last_sync", value).apply() }

    fun clear() { prefs.edit().clear().apply() }
}
