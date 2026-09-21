package com.elak.okulum.auth

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object CentralApi {
    private const val BASE = "https://elak.mcoaihl.com/api/v1/index.php?r="
    private const val UA = "ELAK-Okulum/0.9.2 Android"
    private const val SCHOOL_CODE = "MCOAIHL"

    data class Link(
        val type: String,
        val externalId: String,
        val label: String,
        val schoolScope: String,
        val metaJson: String
    )

    data class Profile(
        val id: Long,
        val username: String,
        val name: String,
        val primaryRole: String,
        val roles: List<String>,
        val schoolScope: String,
        val permissions: List<String>,
        val links: List<Link>,
        val schoolName: String,
        val schoolCode: String
    )

    data class AuthResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val profile: Profile
    )

    data class RefreshResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )

    fun login(username: String, password: String): AuthResult {
        val body = JSONObject()
            .put("username", username.trim())
            .put("password", password)
            .put("school_code", SCHOOL_CODE)
            .put("device_name", "Android")
            .put("app_version", "0.9.2")
        val json = request("auth/login", "POST", null, body)
        val data = json.optJSONObject("data") ?: json
        return AuthResult(
            accessToken = data.optString("access_token"),
            refreshToken = data.optString("refresh_token"),
            expiresIn = data.optLong("expires_in", 900L),
            profile = parseProfile(data.optJSONObject("user") ?: JSONObject())
        ).also {
            if (it.accessToken.isBlank() || it.refreshToken.isBlank() || it.profile.username.isBlank()) {
                throw IllegalStateException("Merkezi oturum bilgisi eksik döndü.")
            }
        }
    }

    fun refresh(refreshToken: String): RefreshResult {
        val body = JSONObject()
            .put("refresh_token", refreshToken)
            .put("device_name", "Android")
            .put("app_version", "0.9.2")
        val json = request("auth/refresh", "POST", null, body)
        val data = json.optJSONObject("data") ?: json
        return RefreshResult(
            accessToken = data.optString("access_token"),
            refreshToken = data.optString("refresh_token"),
            expiresIn = data.optLong("expires_in", 900L)
        ).also {
            if (it.accessToken.isBlank() || it.refreshToken.isBlank()) {
                throw IllegalStateException("Oturum yenilenemedi.")
            }
        }
    }

    fun me(accessToken: String): Profile {
        val json = request("me", "GET", accessToken, null)
        val data = json.optJSONObject("data") ?: json
        return parseProfile(data.optJSONObject("user") ?: data)
    }

    fun logout(accessToken: String) {
        if (accessToken.isBlank()) return
        try { request("auth/logout", "POST", accessToken, JSONObject()) } catch (_: Exception) { }
    }

    private fun parseProfile(o: JSONObject): Profile {
        val roles = stringList(o.optJSONArray("roles"))
        val permissions = stringList(o.optJSONArray("permissions"))
        val links = mutableListOf<Link>()
        val array = o.optJSONArray("links") ?: JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            links += Link(
                type = item.optString("type"),
                externalId = item.optString("external_id"),
                label = item.optString("label"),
                schoolScope = item.optString("school_scope", "both"),
                metaJson = (item.optJSONObject("meta") ?: JSONObject()).toString()
            )
        }
        val school = o.optJSONObject("school") ?: JSONObject()
        val primary = o.optString("primary_role").ifBlank { roles.firstOrNull().orEmpty() }
        return Profile(
            id = o.optLong("id", 0L),
            username = o.optString("username"),
            name = o.optString("name"),
            primaryRole = primary,
            roles = roles.ifEmpty { listOf(primary.ifBlank { "user" }) },
            schoolScope = o.optString("school_scope", "both"),
            permissions = permissions,
            links = links,
            schoolName = school.optString("name"),
            schoolCode = school.optString("code")
        )
    }

    private fun stringList(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until a.length()) {
            val v = a.optString(i).trim()
            if (v.isNotBlank() && v != "null") out += v
        }
        return out.distinct()
    }

    private fun request(route: String, method: String, token: String?, body: JSONObject?): JSONObject {
        val conn = (URL(BASE + java.net.URLEncoder.encode(route, "UTF-8")).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 9000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
            setRequestProperty("User-Agent", UA)
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = if (stream != null) BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() } else ""
        conn.disconnect()
        val json = try { JSONObject(text.ifBlank { "{}" }) } catch (_: Exception) { JSONObject().put("error", text) }
        if (code !in 200..299 || (json.has("ok") && !json.optBoolean("ok", false))) {
            throw IllegalStateException(json.optString("error", "Merkezi ELAK servisine bağlanılamadı."))
        }
        return json
    }
}
