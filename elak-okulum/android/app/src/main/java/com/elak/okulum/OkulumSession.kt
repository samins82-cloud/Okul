package com.elak.okulum

import android.content.Context
import com.elak.okulum.auth.CentralApi
import com.elak.okulum.rehber.LocalCrypto
import org.json.JSONArray
import org.json.JSONObject

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
        get() = prefs.getString("school_scope", "both").orEmpty().ifBlank { "both" }
        set(value) { prefs.edit().putString("school_scope", value.ifBlank { "both" }).apply() }

    var accessToken: String
        get() = LocalCrypto.decrypt(prefs.getString("central_access_enc", ""))
        set(value) { prefs.edit().putString("central_access_enc", LocalCrypto.encrypt(value)).apply() }

    var refreshToken: String
        get() = LocalCrypto.decrypt(prefs.getString("central_refresh_enc", ""))
        set(value) { prefs.edit().putString("central_refresh_enc", LocalCrypto.encrypt(value)).apply() }

    var accessExpiresAt: Long
        get() = prefs.getLong("central_access_expires_at", 0L)
        set(value) { prefs.edit().putLong("central_access_expires_at", value).apply() }

    var centralEnabled: Boolean
        get() = prefs.getBoolean("central_enabled", false)
        set(value) { prefs.edit().putBoolean("central_enabled", value).apply() }

    var rolesCsv: String
        get() = prefs.getString("roles_csv", "").orEmpty()
        set(value) { prefs.edit().putString("roles_csv", value).apply() }

    var permissionsCsv: String
        get() = prefs.getString("permissions_csv", "").orEmpty()
        set(value) { prefs.edit().putString("permissions_csv", value).apply() }

    var linksJson: String
        get() = prefs.getString("links_json", "[]").orEmpty().ifBlank { "[]" }
        set(value) { prefs.edit().putString("links_json", value).apply() }

    val roles: Set<String>
        get() = rolesCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
            .ifEmpty { setOfNotNull(role.takeIf { it.isNotBlank() }) }

    val permissions: Set<String>
        get() = permissionsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()

    val hasCredentials: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    val hasIdentity: Boolean
        get() = username.isNotBlank() && (displayName.isNotBlank() || role.isNotBlank())

    val hasCentralSession: Boolean
        get() = centralEnabled && accessToken.isNotBlank() && refreshToken.isNotBlank()

    val linkedStudents: List<JSONObject>
        get() = linked("student")

    val linkedClasses: List<JSONObject>
        get() = linked("class")

    fun can(permission: String): Boolean = permissions.contains("*") || permissions.contains(permission)

    fun hasRole(vararg keys: String): Boolean {
        val normalized = roles.map { it.lowercase() }.toSet()
        return keys.any { normalized.contains(it.lowercase()) }
    }

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
            .putString("roles_csv", role.trim())
            .putString("school_scope", schoolScope.trim().ifBlank { "both" })
            .putBoolean("central_enabled", false)
            .apply()
    }

    fun saveCentral(username: String, password: String, auth: CentralApi.AuthResult) {
        val profile = auth.profile
        val links = JSONArray()
        profile.links.forEach { link ->
            links.put(JSONObject()
                .put("type", link.type)
                .put("external_id", link.externalId)
                .put("label", link.label)
                .put("school_scope", link.schoolScope)
                .put("meta", try { JSONObject(link.metaJson) } catch (_: Exception) { JSONObject() }))
        }
        prefs.edit()
            .putString("username_enc", LocalCrypto.encrypt(username.trim()))
            .putString("password_enc", LocalCrypto.encrypt(password))
            .putString("display_name_enc", LocalCrypto.encrypt(profile.name.trim()))
            .putString("role", profile.primaryRole.trim())
            .putString("roles_csv", profile.roles.joinToString(","))
            .putString("school_scope", profile.schoolScope.ifBlank { "both" })
            .putString("permissions_csv", profile.permissions.joinToString(","))
            .putString("links_json", links.toString())
            .putString("central_access_enc", LocalCrypto.encrypt(auth.accessToken))
            .putString("central_refresh_enc", LocalCrypto.encrypt(auth.refreshToken))
            .putLong("central_access_expires_at", System.currentTimeMillis() + auth.expiresIn * 1000L)
            .putBoolean("central_enabled", true)
            .apply()
    }

    fun saveRefreshedTokens(result: CentralApi.RefreshResult) {
        prefs.edit()
            .putString("central_access_enc", LocalCrypto.encrypt(result.accessToken))
            .putString("central_refresh_enc", LocalCrypto.encrypt(result.refreshToken))
            .putLong("central_access_expires_at", System.currentTimeMillis() + result.expiresIn * 1000L)
            .putBoolean("central_enabled", true)
            .apply()
    }

    fun updateCentralProfile(profile: CentralApi.Profile) {
        val links = JSONArray()
        profile.links.forEach { link ->
            links.put(JSONObject()
                .put("type", link.type)
                .put("external_id", link.externalId)
                .put("label", link.label)
                .put("school_scope", link.schoolScope)
                .put("meta", try { JSONObject(link.metaJson) } catch (_: Exception) { JSONObject() }))
        }
        prefs.edit()
            .putString("display_name_enc", LocalCrypto.encrypt(profile.name.trim()))
            .putString("role", profile.primaryRole.trim())
            .putString("roles_csv", profile.roles.joinToString(","))
            .putString("school_scope", profile.schoolScope.ifBlank { "both" })
            .putString("permissions_csv", profile.permissions.joinToString(","))
            .putString("links_json", links.toString())
            .apply()
    }

    private fun linked(type: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        val arr = try { JSONArray(linksJson) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("type") == type) out += o
        }
        return out
    }

    fun clearCentralOnly() {
        prefs.edit()
            .remove("central_access_enc")
            .remove("central_refresh_enc")
            .remove("central_access_expires_at")
            .putBoolean("central_enabled", false)
            .apply()
    }

    fun clear() { prefs.edit().clear().apply() }
}
