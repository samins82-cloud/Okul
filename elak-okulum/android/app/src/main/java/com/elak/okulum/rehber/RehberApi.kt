package com.elak.okulum.rehber

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object RehberApi {
    private const val BASE = "https://elak.mcoaihl.com/rehber/"

    data class LoginResult(val token: String)

    fun login(username: String, password: String): LoginResult {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("device", "android")
            .put("device_name", "ELAK Okulum")
        val json = request("?api=login", "POST", null, body)
        val token = pickString(json, "token")
            ?: json.optJSONObject("data")?.let { pickString(it, "token") }
            ?: throw IllegalStateException(json.optString("error", "Rehber oturum anahtarı alınamadı."))
        return LoginResult(token)
    }

    fun sync(token: String): JSONObject = request("?api=sync", "GET", token, null)

    fun webSso(token: String): String? {
        return try {
            val json = request("?api=web_sso", "POST", token, JSONObject())
            pickString(json, "token", "sso_token", "code")
                ?: json.optJSONObject("data")?.let { pickString(it, "token", "sso_token", "code") }
        } catch (_: Exception) {
            null
        }
    }

    private fun request(path: String, method: String, token: String?, body: JSONObject?): JSONObject {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
            setRequestProperty("User-Agent", "ELAK-Okulum/0.6.0 Android")
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
        val explicitlyFailed = json.has("ok") && !json.optBoolean("ok", false)
        if (code !in 200..299 || explicitlyFailed) {
            throw IllegalStateException(json.optString("error", json.optString("message", "Sunucu hatası ($code)")))
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
