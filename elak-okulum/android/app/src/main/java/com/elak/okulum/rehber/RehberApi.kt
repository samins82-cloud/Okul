package com.elak.okulum.rehber

import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object RehberApi {
    private const val BASE = "https://elak.mcoaihl.com/rehber/"
    private const val UA = "ELAK-Okulum/0.8.0 Android"

    data class LoginResult(
        val token: String,
        val userName: String = "",
        val role: String = "",
        val schoolScope: String = ""
    )

    fun login(username: String, password: String): LoginResult {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("device", "android")
            .put("device_name", "ELAK Okulum")
        val json = requestJson("?api=login", "POST", null, body)
        val data = json.optJSONObject("data") ?: json
        val token = pickString(data, "token")
            ?: throw IllegalStateException(json.optString("error", "Rehber oturum anahtarı alınamadı."))
        val user = data.optJSONObject("user")
        return LoginResult(
            token = token,
            userName = pickString(data, "name", "display_name", "username")
                ?: user?.let { pickString(it, "name", "display_name", "username") }.orEmpty(),
            role = pickString(data, "role") ?: user?.let { pickString(it, "role") }.orEmpty(),
            schoolScope = pickString(data, "school_scope", "schoolScope").orEmpty()
        )
    }

    fun sync(token: String): JSONObject = requestJson("?api=sync", "GET", token, null)

    fun revision(token: String): JSONObject =
        requestJson("?api=revision&_ts=" + System.currentTimeMillis(), "GET", token, null)

    fun changeSummary(token: String, since: String): JSONObject =
        requestJson("?api=change_summary&since=" + java.net.URLEncoder.encode(since, "UTF-8"), "GET", token, null)

    fun appVersion(): JSONObject =
        requestJson("?api=app_version&_ts=" + System.currentTimeMillis(), "GET", null, null)

    fun studentDetail(token: String, id: Long): JSONObject =
        requestJson("?api=student_detail&id=" + id, "GET", token, null)

    fun updateStudent(token: String, payload: JSONObject): JSONObject =
        requestJson("?api=student_update", "POST", token, payload)

    fun uploadStudentPhoto(token: String, studentId: Long, bytes: ByteArray, mime: String = "image/jpeg"): JSONObject {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val body = JSONObject()
            .put("student_id", studentId)
            .put("photo_base64", base64)
            .put("mime_type", mime)
        return requestJson("?api=student_photo", "POST", token, body)
    }

    fun photoBytes(token: String, studentId: Long, version: Long = 0L): ByteArray? {
        return try {
            val suffix = if (version > 0) "&v=" + version else ""
            val conn = (URL(BASE + "?api=photo&id=" + studentId + suffix).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Authorization", "Bearer " + token)
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "image/*")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.use { input ->
                val buffer = ByteArray(8192)
                val out = ByteArrayOutputStream()
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            conn.disconnect()
            bytes
        } catch (_: Exception) {
            null
        }
    }

    fun webSso(token: String): String? {
        return try {
            val json = requestJson("?api=web_sso", "POST", token, JSONObject())
            val data = json.optJSONObject("data") ?: json
            pickString(data, "token", "sso_token", "code")
        } catch (_: Exception) {
            null
        }
    }

    private fun requestJson(path: String, method: String, token: String?, body: JSONObject?): JSONObject {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
            setRequestProperty("User-Agent", UA)
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer " + token)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        if (body != null) {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        return readJsonResponse(conn)
    }

    private fun readJsonResponse(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = if (stream != null) {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } else {
            ""
        }
        conn.disconnect()
        val json = try {
            JSONObject(text.ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject().put("error", text)
        }
        val explicitlyFailed = json.has("ok") && !json.optBoolean("ok", false)
        if (code !in 200..299 || explicitlyFailed) {
            throw IllegalStateException(
                json.optString("error", json.optString("message", "Sunucu hatası (" + code + ")"))
            )
        }
        return json
    }

    private fun pickString(json: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val v = json.optString(key, "").trim()
            if (v.isNotEmpty() && v != "null") return v
        }
        return null
    }
}
