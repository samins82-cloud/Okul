package com.elak.okulum.rehber

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class CallerCache(context: Context) : SQLiteOpenHelper(context.applicationContext, "elak_okulum_rehber.db", null, 1) {
    data class Match(
        val guardianName: String,
        val relationship: String,
        val studentName: String,
        val schoolNo: String,
        val className: String,
        val extraCount: Int
    )

    data class DirectoryEntry(
        val studentName: String,
        val schoolNo: String,
        val className: String,
        val guardianName: String,
        val relationship: String,
        val phoneKey: String
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE callers(id INTEGER PRIMARY KEY AUTOINCREMENT, phone_key TEXT NOT NULL, guardian_name TEXT NOT NULL DEFAULT '', relationship TEXT NOT NULL DEFAULT '', student_name TEXT NOT NULL DEFAULT '', school_no TEXT NOT NULL DEFAULT '', class_name TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE INDEX idx_callers_phone_key ON callers(phone_key)")
        db.execSQL("CREATE INDEX idx_callers_class_name ON callers(class_name)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun replaceFromSync(payload: JSONObject): Int {
        val rows = flatten(payload)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("callers", null, null)
            var count = 0
            for (r in rows) {
                val phone = first(r, "phone", "phone_number", "guardian_phone", "student_phone", "mobile", "tel")
                val key = PhoneUtil.normalize(phone)
                if (key.length < 10) continue
                val cv = ContentValues().apply {
                    put("phone_key", key)
                    put("guardian_name", first(r, "guardian_name", "guardianName", "parent_name", "name", "veli_adi"))
                    put("relationship", first(r, "relationship", "relation", "guardian_relation", "yakinlik"))
                    put("student_name", first(r, "student_name", "studentName", "ogrenci_adi", "full_name"))
                    put("school_no", first(r, "school_no", "schoolNo", "student_no", "number"))
                    put("class_name", first(r, "class_name", "className", "class", "sinif"))
                }
                db.insert("callers", null, cv)
                count++
            }
            db.setTransactionSuccessful()
            return count
        } finally {
            db.endTransaction()
        }
    }

    fun lookup(rawPhone: String): Match? {
        val key = PhoneUtil.normalize(rawPhone)
        if (key.length < 10) return null
        readableDatabase.rawQuery(
            "SELECT guardian_name,relationship,student_name,school_no,class_name FROM callers WHERE phone_key=? ORDER BY class_name,student_name",
            arrayOf(key)
        ).use { c ->
            if (!c.moveToFirst()) return null
            val first = Match(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(), 0)
            var total = 1
            while (c.moveToNext()) total++
            return first.copy(extraCount = (total - 1).coerceAtLeast(0))
        }
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM callers", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun listClasses(): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT DISTINCT class_name FROM callers WHERE class_name<>'' ORDER BY class_name", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0).orEmpty())
        }
        return out
    }

    fun listEntries(guardianMode: Boolean, query: String, classFilter: String): List<DirectoryEntry> {
        val raw = ArrayList<DirectoryEntry>()
        readableDatabase.rawQuery(
            "SELECT student_name,school_no,class_name,guardian_name,relationship,phone_key FROM callers ORDER BY class_name,student_name,guardian_name",
            null
        ).use { c ->
            while (c.moveToNext()) {
                raw.add(DirectoryEntry(
                    c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                    c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getString(5).orEmpty()
                ))
            }
        }
        val locale = java.util.Locale.forLanguageTag("tr-TR")
        val q = query.trim().lowercase(locale)
        val filtered = raw.filter { e ->
            (classFilter.isBlank() || e.className == classFilter) &&
                (q.isBlank() || listOf(e.studentName, e.schoolNo, e.className, e.guardianName, e.relationship, e.phoneKey)
                    .any { it.lowercase(locale).contains(q) })
        }
        if (guardianMode) {
            return filtered.filter { it.relationship.lowercase(locale).let { r -> r != "öğrenci" && r != "ogrenci" } }
                .distinctBy { "${it.phoneKey}|${it.guardianName}|${it.studentName}" }
        }
        return filtered.groupBy { "${it.schoolNo}|${it.studentName}|${it.className}" }.values.map { rows ->
            rows.firstOrNull { it.relationship.lowercase(locale).let { r -> r == "öğrenci" || r == "ogrenci" } } ?: rows.first()
        }.sortedWith(compareBy<DirectoryEntry> { it.className }.thenBy { it.studentName })
    }

    private fun flatten(payload: JSONObject): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val direct = firstArray(payload, "entries", "callers", "directory", "contacts")
            ?: payload.optJSONObject("data")?.let { firstArray(it, "entries", "callers", "directory", "contacts") }
        if (direct != null) {
            for (i in 0 until direct.length()) direct.optJSONObject(i)?.let(out::add)
            if (out.isNotEmpty()) return out
        }

        val students = firstArray(payload, "students") ?: payload.optJSONObject("data")?.let { firstArray(it, "students") }
        if (students != null) {
            for (i in 0 until students.length()) {
                val s = students.optJSONObject(i) ?: continue
                val base = JSONObject()
                    .put("student_name", first(s, "student_name", "name", "full_name"))
                    .put("school_no", first(s, "school_no", "number", "student_no"))
                    .put("class_name", first(s, "class_name", "class", "sinif"))
                val studentPhone = first(s, "student_phone", "phone", "mobile")
                if (studentPhone.isNotBlank()) out.add(JSONObject(base.toString()).put("phone", studentPhone).put("relationship", "öğrenci"))
                val guardians = firstArray(s, "guardians", "parents", "veliler")
                if (guardians != null) {
                    for (j in 0 until guardians.length()) {
                        val g = guardians.optJSONObject(j) ?: continue
                        out.add(JSONObject(base.toString())
                            .put("phone", first(g, "phone", "mobile", "phone_number"))
                            .put("guardian_name", first(g, "name", "guardian_name", "full_name"))
                            .put("relationship", first(g, "relationship", "relation", "type")))
                    }
                }
            }
        }
        return out
    }

    private fun first(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "").trim()
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
    }

    private fun firstArray(o: JSONObject, vararg keys: String): JSONArray? {
        for (k in keys) o.optJSONArray(k)?.let { return it }
        return null
    }
}
