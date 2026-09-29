from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
cache = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerCache.kt'
activity = ROOT / 'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'

# 1) Search engine: partial name/surname, multiple words, Turkish/ascii equivalents,
# partial school number and partial phone number.
c = cache.read_text(encoding='utf-8')
old = '''    private fun matchesQuery(qRaw: String, textValues: List<String>, schoolNo: String, phone: String): Boolean {
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
'''
new = '''    private fun searchKey(raw: String): String {
        return raw.trim()
            .lowercase(Locale.forLanguageTag("tr-TR"))
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun matchesQuery(qRaw: String, textValues: List<String>, schoolNo: String, phone: String): Boolean {
        val q = searchKey(qRaw)
        if (q.isBlank()) return true

        val qDigits = qRaw.filter { it.isDigit() }
        if (qRaw.trim().all { it.isDigit() } && qDigits.isNotBlank()) {
            val noDigits = schoolNo.filter { it.isDigit() }
            val phoneDigits = PhoneUtil.normalize(phone)
            return noDigits.contains(qDigits) || phoneDigits.contains(qDigits)
        }

        val haystack = searchKey(textValues.joinToString(" ") + " " + schoolNo)
        val tokens = q.split(' ').filter { it.isNotBlank() }
        return tokens.isNotEmpty() && tokens.all { token -> haystack.contains(token) }
    }
'''
if old not in c:
    raise SystemExit('CallerCache matchesQuery marker missing')
c = c.replace(old, new, 1)
cache.write_text(c, encoding='utf-8')

# 2) While the user is actively searching, search the whole institution instead
# of hiding matches because Middle/High or class filter is currently selected.
r = activity.read_text(encoding='utf-8')
old_students = '''            val q = search.text.toString().trim()
            val cls = if (q.isBlank()) selectedClass else ""
            val currentLevel = level
            val currentGuardian = guardian
            progress.visibility = View.VISIBLE
            dataPool.execute {
                if (!currentGuardian) {
                    val rows = cache.listStudents(q, cls).filter { matchesLevel(it.className, currentLevel) }
'''
new_students = '''            val q = search.text.toString().trim()
            val searching = q.isNotBlank()
            val cls = if (!searching) selectedClass else ""
            val currentLevel = level
            val currentGuardian = guardian
            progress.visibility = View.VISIBLE
            dataPool.execute {
                if (!currentGuardian) {
                    val rows = cache.listStudents(q, cls).filter { searching || matchesLevel(it.className, currentLevel) }
'''
if old_students not in r:
    raise SystemExit('Rehber student search loadData marker missing')
r = r.replace(old_students, new_students, 1)

old_guardians = '''                    val rows = cache.listGuardians(q, cls).filter { matchesLevel(it.className, currentLevel) }
'''
new_guardians = '''                    val rows = cache.listGuardians(q, cls).filter { searching || matchesLevel(it.className, currentLevel) }
'''
if old_guardians not in r:
    raise SystemExit('Rehber guardian search marker missing')
r = r.replace(old_guardians, new_guardians, 1)

# Faster feedback without hammering the DB.
r = r.replace('main.postDelayed(searchRunnable!!, 160L)', 'main.postDelayed(searchRunnable!!, 120L)', 1)
r = re.sub(r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)', 'setting(body, "Sürüm", "v0.9.9 · Rehber Arama")', r, count=1)
activity.write_text(r, encoding='utf-8')

print('v0.9.9 Rehber flexible institution-wide search patch applied')
