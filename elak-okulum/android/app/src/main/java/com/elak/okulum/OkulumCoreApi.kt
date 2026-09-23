package com.elak.okulum

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object OkulumCoreApi {
    private const val API = "https://elak.mcoaihl.com/api.php"
    private const val UA = "ELAK-Okulum/0.9.2 Android"

    data class CoreState(
        val cookie: String,
        val csrf: String,
        val orgCode: String,
        val orgName: String,
        val expiry: String,
        val username: String,
        val displayName: String,
        val roleKey: String,
        val roleName: String,
        val legacy: Boolean,
        val licenseModules: JSONObject,
        val permissions: JSONObject,
        val catalog: JSONArray,
        val summary: JSONObject,
        val settings: JSONObject
    )

    private data class Response(val json: JSONObject, val cookie: String)

    fun login(code: String, username: String, password: String): CoreState {
        val cleanCode = code.trim().uppercase()
        val cleanUser = username.trim()
        if (cleanCode.isBlank()) throw IllegalArgumentException("Kurum kodunu girin.")
        if (cleanUser.isBlank()) throw IllegalArgumentException("Kullanıcı kodunu girin.")
        if (password.isBlank()) throw IllegalArgumentException("Şifreyi girin.")

        val first = request("bootstrap", "GET", null, null, null)
        val csrf = first.json.optString("csrf")
        if (csrf.isBlank()) throw IllegalStateException("ELAK CORE güvenlik anahtarı alınamadı.")

        val body = JSONObject()
            .put("code", cleanCode)
            .put("username", cleanUser)
            .put("password", password)
        val logged = request("school_login", "POST", first.cookie, csrf, body)
        val cookie = logged.cookie.ifBlank { first.cookie }
        val loggedCsrf = logged.json.optString("csrf").ifBlank { csrf }

        // school_login lisans ve kullanıcıyı döndürür; ikinci bootstrap katalog,
        // kurum ayarları ve güncel oturum durumunu tek yanıtta tamamlar.
        val boot = request("bootstrap", "GET", cookie, loggedCsrf, null)
        return parseState(
            boot.json,
            boot.cookie.ifBlank { cookie },
            boot.json.optString("csrf").ifBlank { loggedCsrf },
            cleanCode,
            cleanUser
        )
    }

    fun resume(cookie: String, fallbackCode: String, fallbackUsername: String): CoreState {
        if (cookie.isBlank()) throw IllegalStateException("ELAK CORE oturumu bulunamadı.")
        val boot = request("bootstrap", "GET", cookie, null, null)
        return parseState(
            boot.json,
            boot.cookie.ifBlank { cookie },
            boot.json.optString("csrf"),
            fallbackCode.trim().uppercase(),
            fallbackUsername.trim()
        )
    }

    private fun parseState(
        root: JSONObject,
        cookie: String,
        csrf: String,
        fallbackCode: String,
        fallbackUsername: String
    ): CoreState {
        val license = root.optJSONObject("license")
            ?: throw IllegalStateException("Kurum oturumu sona ermiş veya lisans pasif.")
        val user = root.optJSONObject("user")
            ?: throw IllegalStateException("Kullanıcı oturumu alınamadı.")
        val modules = license.optJSONObject("modules") ?: JSONObject()
        val perms = user.optJSONObject("permissions") ?: JSONObject()
        val catalog = root.optJSONArray("modules") ?: JSONArray()
        return CoreState(
            cookie = cookie,
            csrf = csrf,
            orgCode = license.optString("org_code").ifBlank { fallbackCode },
            orgName = license.optString("org_name").ifBlank { license.optString("name") },
            expiry = license.optString("expiry"),
            username = user.optString("username").ifBlank { fallbackUsername },
            displayName = user.optString("full_name").ifBlank { fallbackUsername },
            roleKey = user.optString("role_key").ifBlank { "user" },
            roleName = user.optString("role_name").ifBlank { roleLabel(user.optString("role_key")) },
            legacy = user.optBoolean("legacy", false),
            licenseModules = modules,
            permissions = perms,
            catalog = catalog,
            summary = root.optJSONObject("summary") ?: JSONObject(),
            settings = root.optJSONObject("settings") ?: JSONObject()
        )
    }

    private fun request(
        action: String,
        method: String,
        cookie: String?,
        csrf: String?,
        body: JSONObject?
    ): Response {
        val conn = (URL("$API?action=$action&_=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
            setRequestProperty("User-Agent", UA)
            if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
            if (!csrf.isNullOrBlank()) setRequestProperty("X-CSRF-Token", csrf)
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        if (method != "GET") {
            conn.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val text = readText(conn).trim().removePrefix("\uFEFF")
        val newCookie = extractCookie(conn).ifBlank { cookie.orEmpty() }
        conn.disconnect()

        val json = try { JSONObject(text.ifBlank { "{}" }) }
        catch (_: Exception) { throw IllegalStateException("ELAK CORE sunucusundan geçersiz yanıt alındı.") }
        if (code !in 200..299 || !json.optBoolean("ok", false)) {
            throw IllegalStateException(json.optString("error").ifBlank { "ELAK CORE giriş hatası (HTTP $code)." })
        }
        return Response(json, newCookie)
    }

    private fun extractCookie(conn: HttpURLConnection): String {
        val values = conn.headerFields.entries
            .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
            .flatMap { it.value ?: emptyList() }
        return values.firstOrNull { it.startsWith("ELAKCORESESSID=", ignoreCase = true) }
            ?.substringBefore(';')?.trim().orEmpty()
    }

    private fun readText(conn: HttpURLConnection): String {
        val stream = try { if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream }
        catch (_: Exception) { conn.errorStream } ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun roleLabel(key: String): String = when (key.lowercase()) {
        "admin" -> "Kurum Yöneticisi"
        "manager" -> "İdareci"
        "teacher" -> "Öğretmen"
        "guidance" -> "Rehber Öğretmen"
        "security" -> "Güvenlik"
        "parent", "guardian" -> "Veli"
        else -> "Kullanıcı"
    }
}
