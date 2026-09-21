from pathlib import Path

ROOT = Path(__file__).resolve().parent
p = ROOT / "app/src/main/java/com/elak/okulum/ElakHomeActivity.kt"
s = p.read_text(encoding="utf-8")

if 'private var schoolName = ""' not in s:
    s = s.replace(
        '    private var displayName = ""\n    private var currentTab = "home"',
        '    private var displayName = ""\n    private var schoolName = ""\n    private var schoolCode = ""\n    private var currentTab = "home"'
    )

s = s.replace(
    '        displayName = account.displayName.ifBlank { account.username }\n        buildShell()',
    '        displayName = account.displayName.ifBlank { account.username }\n        schoolName = account.schoolName.ifBlank { account.schoolCode.ifBlank { "ELAK Okulum" } }\n        schoolCode = account.schoolCode.ifBlank { "759975" }\n        buildShell()'
)
s = s.replace('            text = "Mahmud Celaleddin Ökten AİHL"', '            text = schoolName')
s = s.replace(
    '                    role = resolveRole(account.roles); displayName = account.displayName.ifBlank { account.username }\n                    if (currentTab == "home") showHome()',
    '                    role = resolveRole(account.roles); displayName = account.displayName.ifBlank { account.username }\n                    schoolName = account.schoolName.ifBlank { account.schoolCode.ifBlank { "ELAK Okulum" } }\n                    schoolCode = account.schoolCode.ifBlank { schoolCode }\n                    if (currentTab == "home") showHome()'
)

old_visible = '''    private fun visibleModules(): List<Module> {
        val all = allModules()
        if (account.centralEnabled) return all.filter { account.can("module.${it.permissionKey}") }
        return when (role) {
            "security" -> all.filter { it.key in setOf("izin","rehber") }
            "guardian" -> all.filter { it.key in setOf("announcements","exams","schedule","appointment","survey","trip","docs") }
            "teacher" -> all.filter { it.key in setOf("attendance","schedule","board","rehber","appointment","announcements","docs") }
            "student" -> all.filter { it.key in setOf("announcements","exams","schedule","docs") }
            "dormitory" -> all.filter { it.key in setOf("izin","rehber","announcements","docs") }
            else -> all
        }
    }
'''
new_visible = '''    private fun visibleModules(): List<Module> {
        if (account.centralEnabled) {
            return account.centralModules.mapNotNull { o ->
                val key = o.optString("key").trim()
                if (key.isBlank()) return@mapNotNull null
                Module(
                    key = key,
                    permissionKey = key,
                    icon = o.optString("icon").ifBlank { centralIcon(key) },
                    title = o.optString("title").ifBlank { key },
                    desc = o.optString("description").ifBlank { "ELAK okul modülü" },
                    color = centralColor(o.optString("color"), blue)
                ) { routeCentralModule(o) }
            }
        }
        val all = allModules()
        return when (role) {
            "security" -> all.filter { it.key in setOf("izin","rehber") }
            "guardian" -> all.filter { it.key in setOf("announcements","exams","schedule","appointment","survey","trip","docs") }
            "teacher" -> all.filter { it.key in setOf("attendance","schedule","board","rehber","appointment","announcements","docs") }
            "student" -> all.filter { it.key in setOf("announcements","exams","schedule","docs") }
            "dormitory" -> all.filter { it.key in setOf("izin","rehber","announcements","docs") }
            else -> all
        }
    }
'''
if old_visible in s:
    s = s.replace(old_visible, new_visible)

helpers = r'''
    private fun centralColor(raw: String, fallback: Int): Int = try {
        if (raw.isBlank()) fallback else Color.parseColor(raw)
    } catch (_: Exception) { fallback }

    private fun centralIcon(key: String): String = when (key) {
        "izin_takip" -> "↔"
        "akilli_rehber" -> "☎"
        "yoklama" -> "✓"
        "ders_programi" -> "▦"
        "lgs_yks" -> "▣"
        "ortak" -> "📝"
        "kelebek" -> "🦋"
        "sorumluluk" -> "🎯"
        "ogretmen" -> "👩‍🏫"
        "ogrenci" -> "🧑‍🎓"
        else -> "•"
    }

    private fun routeCentralModule(o: org.json.JSONObject) {
        val key = o.optString("key").trim()
        val title = o.optString("title").ifBlank { "ELAK Modül" }
        val url = o.optString("url").trim()
        val isMco = schoolCode.equals("759975", true) || schoolCode.equals("MCOAIHL", true)

        if (isMco && key == "izin_takip") { openIzin(); return }
        if (isMco && key == "akilli_rehber") { openRehber(); return }
        if (key == "duyurular") { showNotifications(); return }

        if (url.startsWith("https://", true)) {
            startActivity(Intent(this, WebModuleActivity::class.java)
                .putExtra(WebModuleActivity.EXTRA_TITLE, title)
                .putExtra(WebModuleActivity.EXTRA_URL, url))
            return
        }

        android.widget.Toast.makeText(
            this,
            "$title aktif ancak yayın yolu henüz tanımlanmamış.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

'''
needle = '    private fun moduleGrid(): View {'
if 'private fun routeCentralModule' not in s and needle in s:
    s = s.replace(needle, helpers + needle)

s = s.replace(
    'private fun openPortal(url:String="https://elak.mcoaihl.com/okulum/")',
    'private fun openPortal(url:String="https://elak.mcoaihl.com/")'
)

p.write_text(s, encoding="utf-8")
print("0.9.2 live ELAK CORE multi-school module patch applied")
