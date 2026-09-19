from pathlib import Path
import base64, gzip, re

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
api = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinApi.kt"

ACTIVITY_GZ_B64 = """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..."""
activity.write_bytes(gzip.decompress(base64.b64decode(ACTIVITY_GZ_B64)))

s = api.read_text(encoding="utf-8")
s = re.sub(r'private const val UA = "ELAK-Okulum/0\.8\.\d+ Android"', 'private const val UA = "ELAK-Okulum/0.8.13 Android"', s)

old_dashboard = '    fun dashboard(session: IzinSession): JSONObject = requestJson(session, "dashboard")\n'
new_dashboard = '''    fun dashboard(session: IzinSession): JSONObject {
        val data = requestJson(session, "dashboard")
        try {
            val outside = permissions(session, "", "Dışarıda")
            var activeToday = 0
            for (i in 0 until outside.length()) {
                val p = outside.optJSONObject(i) ?: continue
                if (isToday(p.optString("exited_at"))) activeToday++
            }
            data.optJSONObject("stats")?.put("outside", activeToday)
        } catch (_: Exception) { }
        return data
    }
'''
if old_dashboard not in s: raise SystemExit("dashboard marker not found")
s = s.replace(old_dashboard, new_dashboard, 1)

old_security = '    fun securityQueue(session: IzinSession): JSONObject = requestJson(session, "security_queue")\n'
new_security = '''    fun securityQueue(session: IzinSession): JSONObject {
        val data = requestJson(session, "security_queue")
        val raw = data.optJSONArray("returning") ?: JSONArray()
        val todayOnly = JSONArray()
        for (i in 0 until raw.length()) {
            val row = raw.optJSONObject(i) ?: continue
            if (isToday(row.optString("exited_at"))) todayOnly.put(row)
        }
        data.put("returning", todayOnly)
        return data
    }
'''
if old_security not in s: raise SystemExit("security marker not found")
s = s.replace(old_security, new_security, 1)

create_pattern = re.compile(r'''    fun createPermission\(session: IzinSession, studentId: Long, reason: String, receiver: String, approvalMethod: String, sameDayReturn: Boolean, note: String\): JSONObject =\n        requestJson\(session, "permission_create", method = "POST", body = JSONObject\(\)\n            \.put\("student_id", studentId\)\n            \.put\("reason", reason\)\n            \.put\("receiver", receiver\)\n            \.put\("approval_method", approvalMethod\)\n            \.put\("same_day_return", if \(sameDayReturn\) 1 else 0\)\n            \.put\("note", note\)\)\n''')
new_create = '''    fun syncStudentContact(session: IzinSession, student: JSONObject): JSONObject {
        if (!session.isReady) throw IllegalStateException("İzin Takip oturumu bulunamadı.")
        val fields = linkedMapOf(
            "csrf" to session.csrf,
            "id" to student.optLong("id").toString(),
            "full_name" to student.optString("full_name"),
            "student_no" to student.optString("student_no"),
            "class_name" to student.optString("class_name"),
            "school_level" to student.optString("school_level"),
            "gender" to student.optString("gender"),
            "parent_name" to student.optString("parent_name"),
            "parent_phone" to student.optString("parent_phone"),
            "authorized_person" to student.optString("authorized_person")
        )
        val form = fields.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        val conn = open(BASE + "api.php?action=student_save&_=" + System.currentTimeMillis(), "POST", session.cookie).apply {
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("X-CSRF-Token", session.csrf)
        }
        conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
        return readJson(session, conn)
    }

    fun createPermission(
        session: IzinSession,
        studentId: Long,
        reason: String,
        receiver: String,
        approvalMethod: String,
        sameDayReturn: Boolean,
        note: String,
        parentName: String = "",
        parentPhone: String = "",
        authorizedPerson: String = ""
    ): JSONObject = requestJson(session, "permission_create", method = "POST", body = JSONObject()
        .put("student_id", studentId)
        .put("reason", reason)
        .put("receiver", receiver)
        .put("approval_method", approvalMethod)
        .put("same_day_return", if (sameDayReturn) 1 else 0)
        .put("note", note)
        .put("parent_name", parentName)
        .put("parent_phone", parentPhone)
        .put("authorized_person", authorizedPerson))
'''
s, n = create_pattern.subn(new_create, s, count=1)
if n != 1: raise SystemExit("createPermission marker not found")

marker = '    private fun requestJson(session: IzinSession, action: String, query: String = "", method: String = "GET", body: JSONObject? = null): JSONObject {\n'
helper = '''    private fun isToday(value: String): Boolean {
        if (value.length < 10) return false
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return value.substring(0, 10) == today
    }

    private fun readJson(session: IzinSession, conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val text = readText(conn)
        extractCookie(conn).takeIf { it.isNotBlank() }?.let { session.cookie = it }
        conn.disconnect()
        val json = try { JSONObject(text.trim().removePrefix("\uFEFF").ifBlank { "{}" }) }
        catch (_: Exception) { throw IllegalStateException("Sunucudan geçersiz yanıt alındı.") }
        if (code == 401) { session.clear(); throw IllegalStateException("İzin Takip oturumu sona erdi.") }
        if (code !in 200..299 || (json.has("ok") && !json.optBoolean("ok", false))) {
            throw IllegalStateException(json.optString("message", json.optString("error", "İşlem başarısız (HTTP $code).")))
        }
        return json
    }

'''
if marker not in s: raise SystemExit("requestJson marker not found")
s = s.replace(marker, helper + marker, 1)

api.write_text(s, encoding="utf-8")
print("v0.8.13 izin UX/Rehber/daily rollover patch applied")
