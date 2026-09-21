package com.elak.okulum

import android.content.Context
import com.elak.okulum.rehber.LocalCrypto

class OkulumSession(context: Context) {
    private val prefs = context.getSharedPreferences("elak_okulum_session", Context.MODE_PRIVATE)

    var username: String
        get() = LocalCrypto.decrypt(prefs.getString("username_enc", ""))
        set(value) { prefs.edit().putString("username_enc", LocalCrypto.encrypt(value)).apply() }

    var password: String
        get() = LocalCrypto.decrypt(prefs.getString("password_enc", ""))
        set(value) { prefs.edit().putString("password_enc", LocalCrypto.encrypt(value)).apply() }

    var displayName: String
        get() = LocalCrypto.decrypt(prefs.getString("display_name_enc", ""))
        set(value) { prefs.edit().putString("display_name_enc", LocalCrypto.encrypt(value)).apply() }

    var role: String
        get() = prefs.getString("role", "").orEmpty()
        set(value) { prefs.edit().putString("role", value).apply() }

    var schoolScope: String
        get() = prefs.getString("school_scope", "").orEmpty()
        set(value) { prefs.edit().putString("school_scope", value).apply() }

    val hasCredentials: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    val hasIdentity: Boolean
        get() = username.isNotBlank() && (displayName.isNotBlank() || role.isNotBlank())

    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString("username_enc", LocalCrypto.encrypt(username.trim()))
            .putString("password_enc", LocalCrypto.encrypt(password))
            .apply()
    }

    fun saveIdentity(
        username: String,
        password: String,
        displayName: String,
        role: String,
        schoolScope: String
    ) {
        prefs.edit()
            .putString("username_enc", LocalCrypto.encrypt(username.trim()))
            .putString("password_enc", LocalCrypto.encrypt(password))
            .putString("display_name_enc", LocalCrypto.encrypt(displayName.trim()))
            .putString("role", role.trim())
            .putString("school_scope", schoolScope.trim())
            .apply()
    }

    fun clear() { prefs.edit().clear().apply() }
}
