package com.elak.okulum.rehber

import android.content.Context

class RehberSession(context: Context) {
    private val prefs = context.getSharedPreferences("elak_okulum_rehber", Context.MODE_PRIVATE)

    var token: String?
        get() {
            val encrypted = LocalCrypto.decrypt(prefs.getString("token_enc", ""))
            if (encrypted.isNotBlank()) return encrypted
            val legacy = prefs.getString("token", "").orEmpty()
            if (legacy.isNotBlank()) {
                prefs.edit().putString("token_enc", LocalCrypto.encrypt(legacy)).remove("token").apply()
                return legacy
            }
            return null
        }
        set(value) {
            prefs.edit()
                .putString("token_enc", LocalCrypto.encrypt(value))
                .remove("token")
                .apply()
        }

    var username: String
        get() {
            val encrypted = LocalCrypto.decrypt(prefs.getString("username_enc", ""))
            if (encrypted.isNotBlank()) return encrypted
            val legacy = prefs.getString("username", "").orEmpty()
            if (legacy.isNotBlank()) {
                prefs.edit().putString("username_enc", LocalCrypto.encrypt(legacy)).remove("username").apply()
                return legacy
            }
            return ""
        }
        set(value) {
            prefs.edit()
                .putString("username_enc", LocalCrypto.encrypt(value))
                .remove("username")
                .apply()
        }

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(value) { prefs.edit().putLong("last_sync", value).apply() }

    var cardView: Boolean
        get() = prefs.getBoolean("card_view", false)
        set(value) { prefs.edit().putBoolean("card_view", value).apply() }

    var lastDirectoryMode: String
        get() = prefs.getString("directory_mode", "student").orEmpty()
        set(value) { prefs.edit().putString("directory_mode", value).apply() }

    fun clear() { prefs.edit().clear().apply() }
}
