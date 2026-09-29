from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
cache = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerCache.kt'
activity = ROOT / 'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'

# Cache the local directory in memory. Previously every keystroke scanned the whole
# SQLite table and rebuilt every Student/Guardian object before filtering.
c = cache.read_text(encoding='utf-8')

class_marker = '''class CallerCache(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "elak_okulum_rehber.db", null, 4) {
'''
class_repl = class_marker + '''
    @Volatile private var studentSnapshot: List<Student>? = null
    @Volatile private var guardianSnapshot: List<Guardian>? = null
'''
if class_marker not in c:
    raise SystemExit('CallerCache class marker missing')
c = c.replace(class_marker, class_repl, 1)

# Invalidate snapshots only when a directory sync actually replaces local data.
sync_marker = '''            db.setTransactionSuccessful()
            return callerCount
'''
sync_repl = '''            db.setTransactionSuccessful()
            studentSnapshot = null
            guardianSnapshot = null
            return callerCount
'''
if sync_marker not in c:
    raise SystemExit('CallerCache sync marker missing')
c = c.replace(sync_marker, sync_repl, 1)

student_pat = re.compile(r'''    fun listStudents\(query: String = "", classFilter: String = ""\): List<Student> \{.*?\n    \}\n\n    fun getStudent''', re.S)
student_new = '''    private fun allStudentsSnapshot(): List<Student> {
        studentSnapshot?.let { return it }
        synchronized(this) {
            studentSnapshot?.let { return it }
            val rows = ArrayList<Student>()
            readableDatabase.rawQuery(
                "SELECT student_id,name,school_no,class_name,phone,has_photo,photo_version,can_edit FROM students ORDER BY class_name,name",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    rows.add(Student(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getInt(5) == 1, c.getLong(6), c.getInt(7) == 1))
                }
            }
            studentSnapshot = rows
            return rows
        }
    }

    fun listStudents(query: String = "", classFilter: String = ""): List<Student> {
        val q = query.trim()
        return allStudentsSnapshot().asSequence()
            .filter { classFilter.isBlank() || it.className == classFilter }
            .filter { q.isBlank() || matchesQuery(q, listOf(it.name, it.className), it.schoolNo, it.phone) }
            .toList()
    }

    fun getStudent'''
c, n = student_pat.subn(student_new, c, count=1)
if n != 1:
    raise SystemExit('CallerCache listStudents marker missing')

guardian_pat = re.compile(r'''    fun listGuardians\(query: String = "", classFilter: String = ""\): List<Guardian> \{.*?\n    \}\n\n    fun guardiansForStudent''', re.S)
guardian_new = '''    private fun allGuardiansSnapshot(): List<Guardian> {
        guardianSnapshot?.let { return it }
        synchronized(this) {
            guardianSnapshot?.let { return it }
            val rows = ArrayList<Guardian>()
            readableDatabase.rawQuery(
                """SELECT g.id,g.student_id,g.name,g.relationship,g.phone,s.name,s.school_no,s.class_name
                   FROM guardians g LEFT JOIN students s ON s.student_id=g.student_id
                   ORDER BY s.class_name,s.name,g.relationship,g.name""".trimIndent(),
                null
            ).use { c ->
                while (c.moveToNext()) {
                    rows.add(Guardian(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty(), c.getString(5).orEmpty(), c.getString(6).orEmpty(), c.getString(7).orEmpty()))
                }
            }
            guardianSnapshot = rows.distinctBy { it.phone + "|" + it.name + "|" + it.studentId }
            return guardianSnapshot ?: rows
        }
    }

    fun listGuardians(query: String = "", classFilter: String = ""): List<Guardian> {
        val q = query.trim()
        return allGuardiansSnapshot().asSequence()
            .filter { classFilter.isBlank() || it.className == classFilter }
            .filter { q.isBlank() || matchesQuery(q, listOf(it.name, it.relationship, it.studentName, it.className), it.schoolNo, it.phone) }
            .toList()
    }

    fun guardiansForStudent'''
c, n = guardian_pat.subn(guardian_new, c, count=1)
if n != 1:
    raise SystemExit('CallerCache listGuardians marker missing')

cache.write_text(c, encoding='utf-8')

# Reduce typing debounce. The generation guard already discards stale results, so
# a short delay is enough and feels instant with the in-memory snapshot.
r = activity.read_text(encoding='utf-8')
r, n = re.subn(r'main\.postDelayed\(searchRunnable!!,\s*\d+L\)', 'main.postDelayed(searchRunnable!!, 35L)', r, count=1)
if n != 1:
    raise SystemExit('Rehber search debounce marker missing')

# Do not flash a progress bar on every typed character; keep it for initial/filter loads.
r = r.replace('''            progress.visibility = View.VISIBLE
            dataPool.execute {''', '''            progress.visibility = if (q.isBlank()) View.VISIBLE else View.GONE
            dataPool.execute {''', 1)

r = re.sub(r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)', 'setting(body, "Sürüm", "v0.9.10 · Hızlı Arama")', r, count=1)
activity.write_text(r, encoding='utf-8')

print('v0.9.10 in-memory Rehber search cache + 35ms debounce applied')
