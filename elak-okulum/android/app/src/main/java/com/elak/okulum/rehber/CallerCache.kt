package com.elak.okulum.rehber

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class CallerCache(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "elak_okulum_rehber.db", null, 3) {

    data class Match(
        val guardianName: String,
        val relationship: String,
        val studentName: String,
        val schoolNo: String,
        val className: String,
        val studentId: Long,
        val hasPhoto: Boolean,
        val photoVersion: Long,
        val phone: String,
        val extraCount: Int
    )

    data class Student(
        val id: Long,
        val name: String,
        val schoolNo: String,
        val className: String,
        val phone: String,
        val hasPhoto: Boolean,
        val photoVersion: Long,
        val canEdit: Boolean
    )

    data class Guardian(
        val id: Long,
        val studentId: Long,
        val name: String,
        val relationship: String,
        val phone: String,
        val studentName: String,
        val schoolNo: String,
        val className: String
    )

    data class ClassInfo(
        val id: Long,
        val name: String,
        val grade: String,
        val whatsappUrl: String,
        val isClassTeacher: Boolean,
        val studentCount: Int = 0
    )

    data class Announcement(
        val id: String,
        val title: String,
        val summary: String,
        val publishedAt: String,
        val priority: String
    )

    data class History(
        val id: Long,
        val phone: String,
        val callTime: Long,
        val matched: Boolean,
        val guardianName: String,
        val relationship: String,
        val studentName: String,
        val className: String,
        val schoolNo: String,
        val studentId: Long,
        val status: String
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE students(
                student_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                school_no TEXT NOT NULL DEFAULT '',
                class_name TEXT NOT NULL DEFAULT '',
                phone TEXT NOT NULL DEFAULT '',
                has_photo INTEGER NOT NULL DEFAULT 0,
                photo_version INTEGER NOT NULL DEFAULT 0,
                can_edit INTEGER NOT NULL DEFAULT 0
            )""".trimIndent())
        db.execSQL("CREATE INDEX idx_students_class ON students(class_name)")
        db.execSQL("CREATE INDEX idx_students_name ON students(name)")

        db.execSQL("""CREATE TABLE guardians(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL DEFAULT 0,
                name TEXT NOT NULL DEFAULT '',
                relationship TEXT NOT NULL DEFAULT '',
                phone TEXT NOT NULL DEFAULT ''
            )""".trimIndent())
        db.execSQL("CREATE INDEX idx_guardians_student ON guardians(student_id)")
        db.execSQL("CREATE INDEX idx_guardians_phone ON guardians(phone)")

        db.execSQL("""CREATE TABLE callers(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phone_key TEXT NOT NULL DEFAULT '',
                label TEXT NOT NULL DEFAULT '',
                student_id INTEGER NOT NULL DEFAULT 0,
                student_name TEXT NOT NULL DEFAULT '',
                school_no TEXT NOT NULL DEFAULT '',
                class_name TEXT NOT NULL DEFAULT '',
                guardian_name TEXT NOT NULL DEFAULT '',
                relationship TEXT NOT NULL DEFAULT '',
                has_photo INTEGER NOT NULL DEFAULT 0,
                photo_version INTEGER NOT NULL DEFAULT 0
            )""".trimIndent())
        db.execSQL("CREATE INDEX idx_callers_phone ON callers(phone_key)")
        db.execSQL("CREATE INDEX idx_callers_student ON callers(student_id)")

        db.execSQL("""CREATE TABLE classes(
                class_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                grade TEXT NOT NULL DEFAULT '',
                whatsapp_group_url TEXT NOT NULL DEFAULT '',
                is_class_teacher INTEGER NOT NULL DEFAULT 0
            )""".trimIndent())
        db.execSQL("CREATE INDEX idx_classes_name ON classes(name)")

        db.execSQL("""CREATE TABLE announcements(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL DEFAULT '',
                summary TEXT NOT NULL DEFAULT '',
                published_at TEXT NOT NULL DEFAULT '',
                priority TEXT NOT NULL DEFAULT ''
            )""".trimIndent())

        db.execSQL("""CREATE TABLE incoming_history(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phone TEXT NOT NULL DEFAULT '',
                call_time INTEGER NOT NULL,
                matched INTEGER NOT NULL DEFAULT 0,
                guardian_name TEXT NOT NULL DEFAULT '',
                relationship TEXT NOT NULL DEFAULT '',
                student_name TEXT NOT NULL DEFAULT '',
                class_name TEXT NOT NULL DEFAULT '',
                school_no TEXT NOT NULL DEFAULT '',
                student_id INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'Gelen Arama'
            )""".trimIndent())
        db.execSQL("CREATE INDEX idx_history_time ON incoming_history(call_time DESC)")
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf("students", "guardians", "callers", "classes", "announcements", "incoming_history", "meta").forEach {
            db.execSQL("DROP TABLE IF EXISTS " + it)
        }
        onCreate(db)
    }

    fun replaceFromSync(payload: JSONObject): Int {
        val root = payload.optJSONObject("data") ?: payload
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("students", null, null)
            db.delete("guardians", null, null)
            db.delete("callers", null, null)
            db.delete("classes", null, null)
            db.delete("announcements", null, null)

            val studentMap = parseStudents(db, root)
            parseClasses(db, root)
            parseAnnouncements(db, root)
            val callerCount = parseCallers(db, root, studentMap)
            if (studentMap.isEmpty()) deriveStudentsFromCallers(db)

            setMetaInternal(db, "display_name", first(root, "display_name", "displayName", "user_name", "name"))
            setMetaInternal(db, "role", first(root, "role", "role_name", "roleLabel"))
            setMetaInternal(db, "revision", first(root, "revision", "directory_revision", "rev"))
            db.setTransactionSuccessful()
            return callerCount
        } finally {
            db.endTransaction()
        }
    }

    private data class Seed(
        val id: Long,
        val name: String,
        val no: String,
        val className: String,
        val phone: String,
        val hasPhoto: Boolean,
        val photoVersion: Long,
        val canEdit: Boolean
    )

    private fun parseStudents(db: SQLiteDatabase, root: JSONObject): MutableMap<Long, Seed> {
        val map = linkedMapOf<Long, Seed>()
        val arr = firstArray(root, "students_summary", "students", "student_directory", "studentDirectory")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val id = firstLong(s, "student_id", "id", "studentId").takeIf { it > 0 } ?: (i + 1L)
                val seed = Seed(
                    id,
                    first(s, "student_name", "name", "full_name", "display_name"),
                    first(s, "school_no", "student_no", "number", "schoolNo"),
                    first(s, "class_name", "class", "sinif", "className"),
                    PhoneUtil.normalize(first(s, "student_phone", "phone", "mobile", "tel")),
                    firstBool(s, "has_photo", "hasPhoto", "photo"),
                    firstLong(s, "photo_version", "photoVersion", "photo_rev"),
                    firstBool(s, "can_edit", "canEdit", "editable")
                )
                map[id] = seed
                insertStudent(db, seed)

                val gs = firstArray(s, "guardians", "parents", "veliler", "contacts")
                if (gs != null) {
                    for (j in 0 until gs.length()) {
                        val g = gs.optJSONObject(j) ?: continue
                        insertGuardian(
                            db,
                            id,
                            first(g, "name", "guardian_name", "full_name", "display_name"),
                            first(g, "relationship", "relation", "type", "yakinlik", "label"),
                            PhoneUtil.normalize(first(g, "phone", "mobile", "phone_number", "tel"))
                        )
                    }
                }
            }
        }
        return map
    }

    private fun parseClasses(db: SQLiteDatabase, root: JSONObject) {
        val arr = firstArray(root, "classes", "class_infos", "classInfos", "class_list")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val id = firstLong(c, "class_id", "id", "classId").takeIf { it > 0 } ?: (i + 1L)
                val cv = ContentValues().apply {
                    put("class_id", id)
                    put("name", first(c, "name", "class_name", "className"))
                    put("grade", first(c, "grade", "level"))
                    put("whatsapp_group_url", first(c, "whatsapp_group_url", "whatsappGroupUrl", "whatsapp_url", "group_url"))
                    put("is_class_teacher", if (firstBool(c, "is_class_teacher", "isClassTeacher")) 1 else 0)
                }
                db.insertWithOnConflict("classes", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    private fun parseAnnouncements(db: SQLiteDatabase, root: JSONObject) {
        val arr = firstArray(root, "announcements", "duyurular", "notices")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val id = first(a, "id", "announcement_id", "uuid").ifBlank { "a_" + i + "_" + System.currentTimeMillis() }
                val cv = ContentValues().apply {
                    put("id", id)
                    put("title", first(a, "title", "baslik", "subject"))
                    put("summary", first(a, "summary", "message", "content", "body", "aciklama"))
                    put("published_at", first(a, "published_at", "publishedAt", "date", "created_at"))
                    put("priority", first(a, "priority", "level", "type"))
                }
                db.insertWithOnConflict("announcements", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    private fun parseCallers(db: SQLiteDatabase, root: JSONObject, students: Map<Long, Seed>): Int {
        val arr = firstArray(root, "entries", "callers", "directory", "contacts")
        var count = 0
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val phone = PhoneUtil.normalize(first(r, "phone", "phone_number", "guardian_phone", "student_phone", "mobile", "tel"))
                if (phone.length < 10) continue
                val studentId = firstLong(r, "student_id", "studentId", "id")
                val seed = students[studentId]
                val studentName = first(r, "student_name", "studentName", "ogrenci_adi", "full_name").ifBlank { seed?.name.orEmpty() }
                val schoolNo = first(r, "school_no", "schoolNo", "student_no", "number").ifBlank { seed?.no.orEmpty() }
                val className = first(r, "class_name", "className", "class", "sinif").ifBlank { seed?.className.orEmpty() }
                val guardianName = first(r, "guardian_name", "guardianName", "parent_name", "veli_adi")
                val relationship = first(r, "relationship", "relation", "guardian_relation", "yakinlik", "label")
                val cv = ContentValues().apply {
                    put("phone_key", phone)
                    put("label", first(r, "label", "display_label", "type"))
                    put("student_id", studentId)
                    put("student_name", studentName)
                    put("school_no", schoolNo)
                    put("class_name", className)
                    put("guardian_name", guardianName)
                    put("relationship", relationship)
                    put("has_photo", if (firstBool(r, "has_photo", "hasPhoto") || seed?.hasPhoto == true) 1 else 0)
                    put("photo_version", firstLong(r, "photo_version", "photoVersion").takeIf { it > 0 } ?: seed?.photoVersion ?: 0L)
                }
                db.insert("callers", null, cv)
                count++

                if (studentId > 0 && guardianName.isNotBlank()) {
                    val exists = db.rawQuery(
                        "SELECT COUNT(*) FROM guardians WHERE student_id=? AND phone=? AND name=?",
                        arrayOf(studentId.toString(), phone, guardianName)
                    ).use { c -> c.moveToFirst() && c.getInt(0) > 0 }
                    if (!exists) insertGuardian(db, studentId, guardianName, relationship, phone)
                }
            }
        }
        return count
    }

    private fun deriveStudentsFromCallers(db: SQLiteDatabase) {
        db.rawQuery(
            """SELECT student_id,MAX(student_name),MAX(school_no),MAX(class_name),MAX(has_photo),MAX(photo_version)
               FROM callers WHERE student_id>0 GROUP BY student_id""".trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val phone = db.rawQuery(
                    "SELECT phone_key FROM callers WHERE student_id=? AND lower(relationship) IN ('öğrenci','ogrenci','student') LIMIT 1",
                    arrayOf(id.toString())
                ).use { p -> if (p.moveToFirst()) p.getString(0).orEmpty() else "" }
                insertStudent(
                    db,
                    Seed(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), phone, c.getInt(4) == 1, c.getLong(5), false)
                )
            }
        }
    }

    private fun insertStudent(db: SQLiteDatabase, s: Seed) {
        val cv = ContentValues().apply {
            put("student_id", s.id)
            put("name", s.name)
            put("school_no", s.no)
            put("class_name", s.className)
            put("phone", s.phone)
            put("has_photo", if (s.hasPhoto) 1 else 0)
            put("photo_version", s.photoVersion)
            put("can_edit", if (s.canEdit) 1 else 0)
        }
        db.insertWithOnConflict("students", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertGuardian(db: SQLiteDatabase, studentId: Long, name: String, relationship: String, phone: String) {
        if (studentId <= 0 || (name.isBlank() && phone.isBlank())) return
        val cv = ContentValues().apply {
            put("student_id", studentId)
            put("name", name)
            put("relationship", relationship)
            put("phone", phone)
        }
        db.insert("guardians", null, cv)
    }

    fun lookupAll(rawPhone: String): List<Match> {
        val key = PhoneUtil.normalize(rawPhone)
        if (key.length < 10) return emptyList()
        val out = ArrayList<Match>()
        readableDatabase.rawQuery(
            """SELECT guardian_name,relationship,student_name,school_no,class_name,student_id,has_photo,photo_version,phone_key
               FROM callers WHERE phone_key=? ORDER BY class_name,student_name""".trimIndent(),
            arrayOf(key)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    Match(
                        cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(),
                        cursor.getString(3).orEmpty(), cursor.getString(4).orEmpty(), cursor.getLong(5),
                        cursor.getInt(6) == 1, cursor.getLong(7), cursor.getString(8).orEmpty(), 0
                    )
                )
            }
        }
        return out.distinctBy { it.studentId.toString() + "|" + it.studentName + "|" + it.guardianName + "|" + it.relationship }
    }

    fun lookup(rawPhone: String): Match? {
        val all = lookupAll(rawPhone)
        if (all.isEmpty()) return null
        return all.first().copy(extraCount = (all.size - 1).coerceAtLeast(0))
    }

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(DISTINCT phone_key) FROM callers", null)
        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun studentCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM students", null)
        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun guardianCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM guardians", null)
        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun classCount(): Int = readableDatabase.rawQuery("SELECT COUNT(DISTINCT class_name) FROM students WHERE class_name<>''", null)
        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun listClasses(): List<ClassInfo> {
        val out = ArrayList<ClassInfo>()
        readableDatabase.rawQuery(
            """SELECT c.class_id,c.name,c.grade,c.whatsapp_group_url,c.is_class_teacher,
               (SELECT COUNT(*) FROM students s WHERE s.class_name=c.name)
               FROM classes c ORDER BY c.name""".trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(ClassInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getInt(4) == 1, c.getInt(5)))
            }
        }
        if (out.isEmpty()) {
            readableDatabase.rawQuery(
                "SELECT class_name,COUNT(*) FROM students WHERE class_name<>'' GROUP BY class_name ORDER BY class_name",
                null
            ).use { c ->
                var id = 1L
                while (c.moveToNext()) out.add(ClassInfo(id++, c.getString(0).orEmpty(), "", "", false, c.getInt(1)))
            }
        }
        return out
    }

    fun listStudents(query: String = "", classFilter: String = ""): List<Student> {
        val out = ArrayList<Student>()
        val locale = Locale.forLanguageTag("tr-TR")
        val q = query.trim().lowercase(locale)
        readableDatabase.rawQuery(
            "SELECT student_id,name,school_no,class_name,phone,has_photo,photo_version,can_edit FROM students ORDER BY class_name,name",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val s = Student(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getInt(5) == 1, c.getLong(6), c.getInt(7) == 1)
                if (classFilter.isNotBlank() && s.className != classFilter) continue
                if (q.isNotBlank() && !matchesQuery(q, listOf(s.name, s.className), s.schoolNo, s.phone)) continue
                out.add(s)
            }
        }
        return out
    }

    fun getStudent(id: Long): Student? =
        readableDatabase.rawQuery(
            "SELECT student_id,name,school_no,class_name,phone,has_photo,photo_version,can_edit FROM students WHERE student_id=? LIMIT 1",
            arrayOf(id.toString())
        ).use { c ->
            if (!c.moveToFirst()) null
            else Student(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getInt(5) == 1, c.getLong(6), c.getInt(7) == 1)
        }

    fun listGuardians(query: String = "", classFilter: String = ""): List<Guardian> {
        val out = ArrayList<Guardian>()
        val locale = Locale.forLanguageTag("tr-TR")
        val q = query.trim().lowercase(locale)
        readableDatabase.rawQuery(
            """SELECT g.id,g.student_id,g.name,g.relationship,g.phone,s.name,s.school_no,s.class_name
               FROM guardians g LEFT JOIN students s ON s.student_id=g.student_id
               ORDER BY s.class_name,s.name,g.relationship,g.name""".trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val g = Guardian(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getString(5).orEmpty(), c.getString(6).orEmpty(), c.getString(7).orEmpty())
                if (classFilter.isNotBlank() && g.className != classFilter) continue
                if (q.isNotBlank() && !matchesQuery(q, listOf(g.name, g.relationship, g.studentName, g.className), g.schoolNo, g.phone)) continue
                out.add(g)
            }
        }
        return out.distinctBy { it.phone + "|" + it.name + "|" + it.studentId }
    }

    fun guardiansForStudent(studentId: Long): List<Guardian> {
        val out = ArrayList<Guardian>()
        readableDatabase.rawQuery(
            """SELECT g.id,g.student_id,g.name,g.relationship,g.phone,s.name,s.school_no,s.class_name
               FROM guardians g LEFT JOIN students s ON s.student_id=g.student_id
               WHERE g.student_id=? ORDER BY g.id""".trimIndent(),
            arrayOf(studentId.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(Guardian(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getString(5).orEmpty(), c.getString(6).orEmpty(), c.getString(7).orEmpty()))
        }
        return out
    }

    fun listAnnouncements(): List<Announcement> {
        val out = ArrayList<Announcement>()
        readableDatabase.rawQuery("SELECT id,title,summary,published_at,priority FROM announcements ORDER BY published_at DESC", null).use { c ->
            while (c.moveToNext()) out.add(Announcement(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty()))
        }
        return out
    }

    fun addHistory(phone: String, match: Match?, status: String = "Gelen Arama") {
        val cv = ContentValues().apply {
            put("phone", PhoneUtil.normalize(phone))
            put("call_time", System.currentTimeMillis())
            put("matched", if (match != null) 1 else 0)
            put("guardian_name", match?.guardianName.orEmpty())
            put("relationship", match?.relationship.orEmpty())
            put("student_name", match?.studentName.orEmpty())
            put("class_name", match?.className.orEmpty())
            put("school_no", match?.schoolNo.orEmpty())
            put("student_id", match?.studentId ?: 0L)
            put("status", status)
        }
        writableDatabase.insert("incoming_history", null, cv)
        writableDatabase.execSQL("DELETE FROM incoming_history WHERE id NOT IN (SELECT id FROM incoming_history ORDER BY call_time DESC LIMIT 200)")
    }

    fun markLatestMissed() {
        val now = System.currentTimeMillis()
        writableDatabase.execSQL(
            "UPDATE incoming_history SET status='Cevapsız Arama' WHERE id=(SELECT id FROM incoming_history WHERE call_time>? ORDER BY call_time DESC LIMIT 1)",
            arrayOf(now - 180_000L)
        )
    }

    fun listHistory(missedOnly: Boolean = true, limit: Int = 100): List<History> {
        val out = ArrayList<History>()
        val where = if (missedOnly) "WHERE status='Cevapsız Arama'" else ""
        val sql = "SELECT id,phone,call_time,matched,guardian_name,relationship,student_name,class_name,school_no,student_id,status FROM incoming_history " + where + " ORDER BY call_time DESC LIMIT ?"
        readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) out.add(
                History(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getInt(3) == 1, c.getString(4).orEmpty(), c.getString(5).orEmpty(), c.getString(6).orEmpty(), c.getString(7).orEmpty(), c.getString(8).orEmpty(), c.getLong(9), c.getString(10).orEmpty())
            )
        }
        return out
    }

    fun clearHistory() {
        writableDatabase.delete("incoming_history", null, null)
    }

    fun profileName(): String = getMeta("display_name")
    fun profileRole(): String = getMeta("role")
    fun revision(): String = getMeta("revision")

    private fun getMeta(key: String): String = readableDatabase.rawQuery("SELECT value FROM meta WHERE key=? LIMIT 1", arrayOf(key))
        .use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" }

    private fun setMetaInternal(db: SQLiteDatabase, key: String, value: String) {
        val cv = ContentValues().apply { put("key", key); put("value", value) }
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun first(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val value = o.opt(k)
            if (value != null && value != JSONObject.NULL) {
                val v = value.toString().trim()
                if (v.isNotEmpty() && v != "null") return v
            }
        }
        return ""
    }

    private fun firstLong(o: JSONObject, vararg keys: String): Long {
        for (k in keys) {
            if (!o.has(k)) continue
            val v = o.opt(k)
            when (v) {
                is Number -> return v.toLong()
                is String -> v.toLongOrNull()?.let { return it }
            }
        }
        return 0L
    }

    private fun firstBool(o: JSONObject, vararg keys: String): Boolean {
        for (k in keys) {
            if (!o.has(k)) continue
            val v = o.opt(k)
            when (v) {
                is Boolean -> return v
                is Number -> return v.toInt() != 0
                is String -> if (v.equals("true", true) || v == "1" || v.equals("yes", true) || v.equals("evet", true)) return true
            }
        }
        return false
    }

    private fun matchesQuery(qRaw: String, textValues: List<String>, schoolNo: String, phone: String): Boolean {
        val locale = Locale.forLanguageTag("tr-TR")
        val q = qRaw.trim().lowercase(locale)
        if (q.isBlank()) return true
        if (q.all { it.isDigit() }) {
            if (schoolNo.trim() == q) return true
            val normalizedPhone = PhoneUtil.normalize(phone)
            val normalizedQuery = PhoneUtil.normalize(q)
            return normalizedQuery.length >= 10 && normalizedPhone == normalizedQuery
        }
        return textValues.any { value ->
            value.lowercase(locale)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }
                .any { word -> word.startsWith(q) }
        }
    }

    private fun firstArray(o: JSONObject, vararg keys: String): JSONArray? {
        for (k in keys) o.optJSONArray(k)?.let { return it }
        return null
    }
}
