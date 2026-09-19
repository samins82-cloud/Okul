package com.elak.okulum.izin

import android.content.Context

class IzinSession(context: Context) {
    private val prefs = context.getSharedPreferences("elak_izin_session", Context.MODE_PRIVATE)

    var cookie: String
        get() = prefs.getString("cookie", "").orEmpty()
        set(value) { prefs.edit().putString("cookie", value).apply() }

    var csrf: String
        get() = prefs.getString("csrf", "").orEmpty()
        set(value) { prefs.edit().putString("csrf", value).apply() }

    var role: String
        get() = prefs.getString("role", "").orEmpty()
        set(value) { prefs.edit().putString("role", value).apply() }

    var fullName: String
        get() = prefs.getString("full_name", "").orEmpty()
        set(value) { prefs.edit().putString("full_name", value).apply() }

    var username: String
        get() = prefs.getString("username", "").orEmpty()
        set(value) { prefs.edit().putString("username", value).apply() }

    var adminAccess: String
        get() = prefs.getString("admin_access", "").orEmpty()
        set(value) { prefs.edit().putString("admin_access", value).apply() }

    var schoolScope: String
        get() = prefs.getString("school_scope", "both").orEmpty().ifBlank { "both" }
        set(value) { prefs.edit().putString("school_scope", value).apply() }

    var permissionsCsv: String
        get() = prefs.getString("permissions", "").orEmpty()
        set(value) { prefs.edit().putString("permissions", value).apply() }

    val permissions: Set<String>
        get() = permissionsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val isReady: Boolean
        get() = cookie.isNotBlank() && csrf.isNotBlank()

    fun save(result: IzinApi.LoginResult) {
        prefs.edit()
            .putString("cookie", result.cookie)
            .putString("csrf", result.csrf)
            .putString("role", result.role)
            .putString("full_name", result.fullName)
            .putString("username", result.username)
            .putString("admin_access", result.adminAccess)
            .putString("school_scope", result.schoolScope)
            .putString("permissions", result.permissions.joinToString(","))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
