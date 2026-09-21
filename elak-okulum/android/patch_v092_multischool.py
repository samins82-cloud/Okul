from pathlib import Path

ROOT = Path(__file__).resolve().parent
p = ROOT / "app/src/main/java/com/elak/okulum/ElakHomeActivity.kt"
s = p.read_text(encoding="utf-8")

s = s.replace(
    '    private var displayName = ""\n    private var currentTab = "home"',
    '    private var displayName = ""\n    private var schoolName = ""\n    private var schoolCode = ""\n    private var currentTab = "home"'
)

s = s.replace(
    '        displayName = account.displayName.ifBlank { account.username }\n\n        buildShell()',
    '        displayName = account.displayName.ifBlank { account.username }\n        schoolName = account.schoolName.ifBlank { "Mahmud Celaleddin Ökten Anadolu İmam Hatip Lisesi" }\n        schoolCode = account.schoolCode.ifBlank { "MCOAIHL" }\n\n        buildShell()'
)

s = s.replace(
    '            text = "Mahmud Celaleddin Ökten AİHL"',
    '            text = schoolName'
)

s = s.replace(
    '        box.addView(infoRow("Okul", "Mahmud Celaleddin Ökten AİHL"))',
    '        box.addView(infoRow("Okul", schoolName))\n        box.addView(divider())\n        box.addView(infoRow("Okul Kodu", schoolCode))'
)

# Merkezi API aktif olduğunda hızlı işlemler okulun kendi modüllerinden gelir.
s = s.replace(
    '    private fun quickRow(): View {\n        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }\n        val items = when (role) {',
    '''    private fun quickRow(): View {\n        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }\n        val centralAccount = OkulumSession(this)\n        if (centralAccount.centralEnabled && centralAccount.centralModules.isNotEmpty()) {\n            centralAccount.centralModules.take(2).forEachIndexed { i, o ->\n                val q = Quick(\n                    o.optString("title", o.optString("key", "Modül")),\n                    o.optString("description").ifBlank { "Okul modülü" },\n                    centralColor(o.optString("color"), if (i == 0) blue else green)\n                ) { routeCentralModule(o) }\n                row.addView(quickCard(q), LinearLayout.LayoutParams(0, dp(92), 1f).apply { if (i > 0) marginStart = dp(9) })\n            }\n            return row\n        }\n        val items = when (role) {'''
)

# Merkezi API aktif olduğunda kart listesi rol sabitlerinden değil sunucudaki okul konfigürasyonundan gelir.
s = s.replace(
    '    private fun modulesForRole(): List<Module> {\n        if (role == "security") return listOf(',
    '''    private fun modulesForRole(): List<Module> {\n        val centralAccount = OkulumSession(this)\n        if (centralAccount.centralEnabled && centralAccount.centralModules.isNotEmpty()) {\n            return centralAccount.centralModules.map { o ->\n                val key = o.optString("key")\n                Module(\n                    key,\n                    centralIcon(key, o.optString("icon")),\n                    o.optString("title", key),\n                    o.optString("description"),\n                    centralColor(o.optString("color"), blue)\n                ) { routeCentralModule(o) }\n            }\n        }\n        if (role == "security") return listOf('''
)

helpers = r'''
    private fun centralColor(raw: String, fallback: Int): Int = try {
        if (raw.isBlank()) fallback else Color.parseColor(raw)
    } catch (_: Exception) { fallback }

    private fun centralIcon(key: String, configured: String): String {
        if (configured.isNotBlank()) return configured
        return when (key) {
            "izin_takip" -> "↔"
            "akilli_rehber" -> "☎"
            "online_yoklama" -> "✓"
            "veli_randevu" -> "◷"
            "duyurular" -> "●"
            "ders_programi" -> "▦"
            "akilli_tahta" -> "QR"
            "lgs_yks" -> "▣"
            "anketler" -> "☑"
            "etkinlik_gezi" -> "⌖"
            "belgeler" -> "▤"
            "ogretmen_islemleri" -> "♟"
            else -> "•"
        }
    }

    private fun routeCentralModule(o: org.json.JSONObject) {
        val key = o.optString("key")
        val title = o.optString("title", "ELAK Modül")
        val configuredUrl = o.optString("url").trim()
        val isMco = schoolCode.equals("MCOAIHL", ignoreCase = true)

        // MCOAIHL'deki yerel/native modüller korunur. Başka okula asla MCO oturumu açılmaz.
        if (isMco && key == "izin_takip") { openIzin(); return }
        if (isMco && key == "akilli_rehber") { openRehber(); return }

        if (configuredUrl.isNotBlank()) {
            startActivity(Intent(this, WebModuleActivity::class.java)
                .putExtra(WebModuleActivity.EXTRA_TITLE, title)
                .putExtra(WebModuleActivity.EXTRA_URL, configuredUrl))
            return
        }

        if (key == "duyurular") { showNotifications(); return }
        if (key == "veli_randevu" && isMco) { openPortal("https://elak.mcoaihl.com/randevu/"); return }
        if (key == "online_yoklama" && isMco) { openPortal("https://www.mcoaihl.com/dyk_soset/index.php"); return }
        if (key == "lgs_yks" && isMco) { openPortal("https://elak.mcoaihl.com/deneme-sonuc/index.php"); return }
        if (isMco) { openPortal(); return }

        android.widget.Toast.makeText(this, "$title için okul modül adresi henüz tanımlanmamış.", android.widget.Toast.LENGTH_SHORT).show()
    }

'''
needle = '    private fun moduleCard(m: Module): View = LinearLayout(this).apply {'
if helpers.strip() not in s and needle in s:
    s = s.replace(needle, helpers + needle)

p.write_text(s, encoding="utf-8")
print("0.9.2 multi-school branding and module routing patch applied")
