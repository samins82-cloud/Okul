from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
cache = ROOT / 'app/src/main/java/com/elak/okulum/rehber/CallerCache.kt'
activity = ROOT / 'app/src/main/java/com/elak/okulum/rehber/RehberActivity84.kt'

# 1) Search engine: partial name/surname, multiple words, Turkish/ascii equivalents,
# partial school number and partial phone number. Replace whatever matchesQuery
# implementation previous patches produced.
c = cache.read_text(encoding='utf-8')
search_impl = '''    private fun searchKey(raw: String): String {
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
# Remove an earlier searchKey if this patch is re-run.
c = re.sub(r'''    private fun searchKey\(raw: String\): String \{.*?\n    \}\n\n''', '', c, count=1, flags=re.S)
pat = re.compile(r'''    private fun matchesQuery\(qRaw: String, textValues: List<String>, schoolNo: String, phone: String\): Boolean \{.*?\n    \}\n\n(?=    private fun first\()''', re.S)
c, n = pat.subn(search_impl, c, count=1)
if n != 1:
    raise SystemExit('CallerCache matchesQuery function not found')
cache.write_text(c, encoding='utf-8')

# 2) While actively searching, search the whole institution instead of hiding
# matches because Middle/High or class filter is selected.
r = activity.read_text(encoding='utf-8')
old_cls = '            val cls = if (q.isBlank()) selectedClass else ""\n'
new_cls = '            val searching = q.isNotBlank()\n            val cls = if (!searching) selectedClass else ""\n'
if old_cls in r:
    r = r.replace(old_cls, new_cls, 1)
elif 'val searching = q.isNotBlank()' not in r:
    raise SystemExit('Rehber search class-filter marker missing')

old_students = '                    val rows = cache.listStudents(q, cls).filter { matchesLevel(it.className, currentLevel) }\n'
new_students = '                    val rows = cache.listStudents(q, cls).filter { searching || matchesLevel(it.className, currentLevel) }\n'
if old_students in r:
    r = r.replace(old_students, new_students, 1)
elif new_students not in r:
    raise SystemExit('Rehber student level-filter marker missing')

old_guardians = '                    val rows = cache.listGuardians(q, cls).filter { matchesLevel(it.className, currentLevel) }\n'
new_guardians = '                    val rows = cache.listGuardians(q, cls).filter { searching || matchesLevel(it.className, currentLevel) }\n'
if old_guardians in r:
    r = r.replace(old_guardians, new_guardians, 1)
elif new_guardians not in r:
    raise SystemExit('Rehber guardian level-filter marker missing')

r = r.replace('main.postDelayed(searchRunnable!!, 160L)', 'main.postDelayed(searchRunnable!!, 120L)', 1)
r = re.sub(r'setting\(body,\s*"Sürüm",\s*"[^"]+"\)', 'setting(body, "Sürüm", "v0.9.9 · Rehber Arama")', r, count=1)
activity.write_text(r, encoding='utf-8')

print('v0.9.9 Rehber flexible institution-wide search patch applied')
