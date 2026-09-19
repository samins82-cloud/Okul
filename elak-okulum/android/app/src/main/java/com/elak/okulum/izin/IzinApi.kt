package com.elak.okulum.izin

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object IzinApi {
    private const val BASE = "https://www.mcoaihl.com/izin/"
    private const val UA = "ELAK-Okulum/0.8.12 Android"

    data class LoginResult(
        val cookie: String,
        val csrf: String,
        val role: String,
        val fullName: String,
        val username: String,
        val adminAccess: String,
        val schoolScope: String,
        val permissions: List<String>
    )

    fun login(username: String, password: String): LoginResult {
        val body = "login=1&username=${enc(username.trim())}&password=${enc(password)}"
        val conn = open(BASE + "index.php", "POST", null).apply {
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Referer", BASE + "index.php")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val postText = readText(conn)
        val cookie = extractCookie(conn)
        conn.disconnect()
        if (cookie.isBlank()) {
            val message = extractLoginError(postText)
            throw IllegalStateException(message ?: "İzin Takip oturumu açılamadı.")
        }
        if (code !in 200..399) throw IllegalStateException("İzin Takip giriş hatası (HTTP $code).")
        val result = bootstrap(cookie)
        if (result.username.isBlank()) throw IllegalStateException("İzin Takip kullanıcı bilgisi alınamadı.")
        return result
    }

    fun bootstrap(cookie: String): LoginResult {
        val conn = open(BASE + "index.php", "GET", cookie)
        val code = conn.responseCode
        val text = readText(conn)
        val refreshedCookie = extractCookie(conn).ifBlank { cookie }
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("İzin Takip oturumu doğrulanamadı (HTTP $code).")
        if (text.contains("name=\"login\"", ignoreCase = true) || text.contains("KULLANICI GİRİŞİ", ignoreCase = true)) {
            throw IllegalStateException("İzin Takip oturumu sona erdi.")
        }
        return parseAppBootstrap(text, refreshedCookie)
    }

    fun dashboard(session: IzinSession): JSONObject = requestJson(session, "dashboard")

    fun students(session: IzinSession, query: String = ""): JSONArray =
        requestJson(session, "students", "&q=${enc(query)}").optJSONArray("students") ?: JSONArray()

    fun studentSearch(session: IzinSession, query: String): JSONArray =
        requestJson(session, "student_search", "&q=${enc(query)}").optJSONArray("students") ?: JSONArray()

    fun permissions(session: IzinSession, query: String = "", status: String = ""): JSONArray =
        requestJson(session, "permissions", "&q=${enc(query)}&status=${enc(status)}").optJSONArray("permissions") ?: JSONArray()

    fun securityQueue(session: IzinSession): JSONObject = requestJson(session, "security_queue")

    fun createPermission(session: IzinSession, studentId: Long, reason: String, receiver: String, approvalMethod: String, sameDayReturn: Boolean, note: String): JSONObject =
        requestJson(session, "permission_create", method = "POST", body = JSONObject()
            .put("student_id", studentId)
            .put("reason", reason)
            .put("receiver", receiver)
            .put("approval_method", approvalMethod)
            .put("same_day_return", if (sameDayReturn) 1 else 0)
            .put("note", note))

    fun securityExit(session: IzinSession, permissionId: Long): JSONObject =
        requestJson(session, "security_exit", method = "POST", body = JSONObject().put("id", permissionId))

    fun securityReturn(session: IzinSession, permissionId: Long): JSONObject =
        requestJson(session, "security_return", method = "POST", body = JSONObject().put("id", permissionId))

    fun cancelPermission(session: IzinSession, permissionId: Long): JSONObject =
        requestJson(session, "permission_cancel", method = "POST", body = JSONObject().put("id", permissionId))

    private fun requestJson(session: IzinSession, action: String, query: String = "", method: String = "GET", body: JSONObject? = null): JSONObject {
        if (!session.isReady) throw IllegalStateException("İzin Takip oturumu bulunamadı.")
        val scope = if (session.role == "admin") "&view_scope=${enc(session.schoolScope)}" else ""
        val conn = open(BASE + "api.php?action=${enc(action)}$query$scope&_=${System.currentTimeMillis()}", method, session.cookie).apply {
            setRequestProperty("Accept", "application/json")
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("X-CSRF-Token", session.csrf)
            }
        }
        if (method != "GET") {
            val payload = JSONObject(body?.toString() ?: "{}").put("csrf", session.csrf)
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val text = readText(conn)
        extractCookie(conn).takeIf { it.isNotBlank() }?.let { session.cookie = it }
        conn.disconnect()
        val json = try { JSONObject(text.trim().removePrefix("\uFEFF").ifBlank { "{}" }) }
        catch (_: Exception) { throw IllegalStateException("Sunucudan geçersiz yanıt alındı.") }
        if (code == 401) {
            session.clear()
            throw IllegalStateException("İzin Takip oturumu sona erdi.")
        }
        if (code !in 200..299 || (json.has("ok") && !json.optBoolean("ok", false))) {
            throw IllegalStateException(json.optString("message", json.optString("error", "İşlem başarısız (HTTP $code).")))
        }
        return json
    }

    private fun parseAppBootstrap(html: String, cookie: String): LoginResult {
        val csrfRaw = findGroup(html, "csrf\\s*:\\s*(\"(?:\\\\.|[^\"])*\")")
            ?: throw IllegalStateException("İzin Takip güvenlik anahtarı alınamadı.")
        val roleRaw = findGroup(html, "role\\s*:\\s*(\"(?:\\\\.|[^\"])*\")") ?: "\"\""
        val userRaw = findGroup(html, "user\\s*:\\s*(\\{.*?\\})\\s*,\\s*permissions\\s*:") ?: "{}"
        val permissionsRaw = findGroup(html, "permissions\\s*:\\s*(\\[.*?\\])\\s*,\\s*permissionCatalog\\s*:") ?: "[]"
        val csrf = JSONObject("{\"v\":$csrfRaw}").optString("v")
        val role = JSONObject("{\"v\":$roleRaw}").optString("v")
        val user = JSONObject(userRaw)
        val arr = JSONArray(permissionsRaw)
        val permissions = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { permissions.add(it) }
        return LoginResult(
            cookie = cookie,
            csrf = csrf,
            role = role,
            fullName = user.optString("full_name"),
            username = user.optString("username"),
            adminAccess = user.optString("admin_access", "authorized"),
            schoolScope = user.optString("school_scope", "both").ifBlank { "both" },
            permissions = permissions
        )
    }

    private fun open(url: String, method: String, cookie: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            useCaches = false
            setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
            setRequestProperty("User-Agent", UA)
            if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
        }

    private fun readText(conn: HttpURLConnection): String {
        val stream = try { if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream }
        catch (_: Exception) { conn.errorStream } ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun extractCookie(conn: HttpURLConnection): String {
        val values = conn.headerFields.entries
            .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
            .flatMap { it.value ?: emptyList() }
        return values.firstOrNull { it.startsWith("MCOAIHL_IZIN_SESSID=", ignoreCase = true) }
            ?.substringBefore(';')?.trim().orEmpty()
    }

    private fun extractLoginError(html: String): String? =
        Regex("<div[^>]*class=\"alert error\"[^>]*>(.*?)</div>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("<[^>]+>"), "")?.trim()

    private fun findGroup(text: String, pattern: String): String? =
        Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL)).find(text)?.groupValues?.getOrNull(1)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
