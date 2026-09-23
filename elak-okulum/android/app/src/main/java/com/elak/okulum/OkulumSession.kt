package com.elak.okulum

import android.content.Context
import com.elak.okulum.rehber.LocalCrypto
import org.json.JSONArray
import org.json.JSONObject

class OkulumSession(context: Context) {
    private val prefs = context.getSharedPreferences("elak_okulum_session", Context.MODE_PRIVATE)

    var orgCode: String
        get() = decrypt("org_code_enc")
        set(value) = putEncrypted("org_code_enc", value.trim().uppercase())

    var username: String
        get() = decrypt("username_enc")
        set(value) = putEncrypted("username_enc", value.trim())

    var password: String
        get() = decrypt("password_enc")
        set(value) = putEncrypted("password_enc", value)

    var coreCookie: String
        get() = decrypt("core_cookie_enc")
        set(value) = putEncrypted("core_cookie_enc", value)

    var csrf: String
        get() = decrypt("csrf_enc")
        set(value) = putEncrypted("csrf_enc", value)

    var orgName: String
        get() = prefs.getString("org_name", "").orEmpty()
        set(value) { prefs.edit().putString("org_name", value).apply() }

    var displayName: String
        get() = prefs.getString("display_name", "").orEmpty()
        set(value) { prefs.edit().putString("display_name", value).apply() }

    var roleKey: String
        get() = prefs.getString("role_key", "").orEmpty()
        set(value) { prefs.edit().putString("role_key", value).apply() }

    var roleName: String
        get() = prefs.getString("role_name", "").orEmpty()
        set(value) { prefs.edit().putString("role_name", value).apply() }

    var expiry: String
        get() = prefs.getString("expiry", "").orEmpty()
        set(value) { prefs.edit().putString("expiry", value).apply() }

    var modulesJson: String
        get() = prefs.getString("modules_json", "{}").orEmpty().ifBlank { "{}" }
        set(value) { prefs.edit().putString("modules_json", value.ifBlank { "{}" }).apply() }

    var catalogJson: String
        get() = prefs.getString("catalog_json", "[]").orEmpty().ifBlank { "[]" }
        set(value) { prefs.edit().putString("catalog_json", value.ifBlank { "[]" }).apply() }

    var permissionsJson: String
        get() = prefs.getString("permissions_json", "{}").orEmpty().ifBlank { "{}" }
        set(value) { prefs.edit().putString("permissions_json", value.ifBlank { "{}" }).apply() }

    var summaryJson: String
        get() = prefs.getString("summary_json", "{}").orEmpty().ifBlank { "{}" }
        set(value) { prefs.edit().putString("summary_json", value.ifBlank { "{}" }).apply() }

    var settingsJson: String
        get() = prefs.getString("settings_json", "{}").orEmpty().ifBlank { "{}" }
        set(value) { prefs.edit().putString("settings_json", value.ifBlank { "{}" }).apply() }

    val isReady: Boolean
        get() = orgCode.isNotBlank() && username.isNotBlank() && coreCookie.isNotBlank()

    fun saveCredentials(username: String, password: String) {
        this.username = username
        this.password = password
    }

    fun saveCredentials(orgCode: String, username: String, password: String) {
        this.orgCode = orgCode
        this.username = username
        this.password = password
    }

    fun saveCore(state: OkulumCoreApi.CoreState, password: String? = null) {
        val e = prefs.edit()
            .putString("org_code_enc", LocalCrypto.encrypt(state.orgCode))
            .putString("username_enc", LocalCrypto.encrypt(state.username))
            .putString("core_cookie_enc", LocalCrypto.encrypt(state.cookie))
            .putString("csrf_enc", LocalCrypto.encrypt(state.csrf))
            .putString("org_name", state.orgName)
            .putString("display_name", state.displayName)
            .putString("role_key", state.roleKey)
            .putString("role_name", state.roleName)
            .putString("expiry", state.expiry)
            .putString("modules_json", state.licenseModules.toString())
            .putString("catalog_json", state.catalog.toString())
            .putString("permissions_json", state.permissions.toString())
            .putString("summary_json", state.summary.toString())
            .putString("settings_json", state.settings.toString())
        if (password != null) e.putString("password_enc", LocalCrypto.encrypt(password))
        e.apply()
    }

    fun moduleEnabled(key: String): Boolean = try {
        JSONObject(modulesJson).optBoolean(key, false)
    } catch (_: Exception) { false }

    fun catalog(): JSONArray = try { JSONArray(catalogJson) } catch (_: Exception) { JSONArray() }

    fun permissions(): JSONObject = try { JSONObject(permissionsJson) } catch (_: Exception) { JSONObject() }

    fun hasPermission(key: String): Boolean {
        val p = permissions()
        return p.optBoolean("*", false) || p.optBoolean(key, false)
    }

    fun clearModuleSessionsOnly() {
        prefs.edit()
            .remove("core_cookie_enc")
            .remove("csrf_enc")
            .apply()
    }

    fun clear() { prefs.edit().clear().apply() }

    private fun decrypt(key: String): String = LocalCrypto.decrypt(prefs.getString(key, ""))
    private fun putEncrypted(key: String, value: String) {
        prefs.edit().putString(key, LocalCrypto.encrypt(value)).apply()
    }
}
