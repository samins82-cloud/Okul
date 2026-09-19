from pathlib import Path
import base64, gzip, re

ROOT = Path(__file__).resolve().parent
activity = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
api = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinApi.kt"

parts_dir = ROOT / "patches"
ACTIVITY_GZ_B64 = "".join((parts_dir / ("izin13_%d.b64" % i)).read_text(encoding="utf-8").strip() for i in range(1, 6))
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
if old_dashboard not in s:
    raise SystemExit("dashboard marker not found")
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
if old_security not in s:
    raise SystemExit("security marker not found")
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
if n != 1:
    raise SystemExit("createPermission marker not found")

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
if marker not in s:
    raise SystemExit("requestJson marker not found")
s = s.replace(marker, helper + marker, 1)

api.write_text(s, encoding="utf-8")
print("v0.8.13 izin UX/Rehber/daily rollover patch applied")
