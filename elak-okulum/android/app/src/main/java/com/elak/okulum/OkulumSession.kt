package com.elak.okulum

import android.content.Context
import com.elak.okulum.rehber.LocalCrypto

class OkulumSession(context: Context) {
    private val prefs = context.getSharedPreferences("elak_okulum_session", Context.MODE_PRIVATE)

    var username: String
        get() = LocalCrypto.decrypt(prefs.getString("username_enc", ""))
        set(value) {
            prefs.edit().putString("username_enc", LocalCrypto.encrypt(value)).apply()
        }

    var password: String
        get() = LocalCrypto.decrypt(prefs.getString("password_enc", ""))
        set(value) {
            prefs.edit().putString("password_enc", LocalCrypto.encrypt(value)).apply()
        }

    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString("username_enc", LocalCrypto.encrypt(username.trim()))
            .putString("password_enc", LocalCrypto.encrypt(password))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
