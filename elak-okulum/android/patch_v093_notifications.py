from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
api = ROOT / "app/src/main/java/com/elak/okulum/auth/CentralApi.kt"
home = ROOT / "app/src/main/java/com/elak/okulum/ElakHomeActivity.kt"
manifest = ROOT / "app/src/main/AndroidManifest.xml"
gradle = ROOT / "app/build.gradle.kts"
login = ROOT / "app/src/main/java/com/elak/okulum/LoginActivity.kt"

# ---- CentralApi: announcements_list ----
s = api.read_text(encoding="utf-8")
s = s.replace("ELAK-Okulum/0.9.2 CORE Android", "ELAK-Okulum/0.9.3 CORE Android")

announcement_model = '''    data class Announcement(\n        val id: Long,\n        val title: String,\n        val body: String,\n        val level: String,\n        val createdAt: String\n    )\n\n'''
needle = '''    data class RefreshResult(\n        val accessToken: String,\n        val refreshToken: String,\n        val expiresIn: Long\n    )\n\n'''
if "data class Announcement(" not in s:
    if needle not in s:
        raise SystemExit("CentralApi RefreshResult anchor not found")
    s = s.replace(needle, needle + announcement_model)

announcement_fun = '''    fun announcements(accessToken: String): List<Announcement> {\n        if (accessToken.isBlank()) return emptyList()\n        val response = request("announcements_list", "GET", decodeSession(accessToken), null)\n        val arr = response.json.optJSONArray("announcements") ?: JSONArray()\n        val out = mutableListOf<Announcement>()\n        for (i in 0 until arr.length()) {\n            val o = arr.optJSONObject(i) ?: continue\n            out += Announcement(\n                id = o.optLong("id"),\n                title = o.optString("title"),\n                body = o.optString("body"),\n                level = o.optString("level", "info"),\n                createdAt = o.optString("created_at")\n            )\n        }\n        return out.sortedByDescending { it.id }\n    }\n\n'''
if "fun announcements(accessToken: String)" not in s:
    anchor = "    fun logout(accessToken: String) {"
    if anchor not in s:
        raise SystemExit("CentralApi logout anchor not found")
    s = s.replace(anchor, announcement_fun + anchor)
api.write_text(s, encoding="utf-8")

# ---- Home: schedule background sync and native notification center ----
s = home.read_text(encoding="utf-8")
if "com.elak.okulum.notification.NotificationCenterActivity" not in s:
    import_anchor = "import com.elak.okulum.izin.IzinSession\n"
    if import_anchor not in s:
        raise SystemExit("Home import anchor not found")
    s = s.replace(import_anchor, import_anchor + "import com.elak.okulum.notification.NotificationCenterActivity\nimport com.elak.okulum.notification.NotificationSyncWorker\n")

if "NotificationSyncWorker.schedule(this)" not in s:
    oncreate_anchor = "        showHome()\n        refreshCentralProfileIfNeeded()"
    if oncreate_anchor not in s:
        raise SystemExit("Home onCreate anchor not found")
    s = s.replace(oncreate_anchor, "        showHome()\n        NotificationSyncWorker.schedule(this)\n        refreshCentralProfileIfNeeded()")

pattern = re.compile(r'    private fun showNotifications\(\) \{.*?\n    \}\n\n    private fun showCalendar\(\)', re.S)
replacement = '''    private fun showNotifications() {\n        startActivity(Intent(this, NotificationCenterActivity::class.java))\n        root.postDelayed({ currentTab = "home"; selectBottom("home") }, 250)\n    }\n\n    private fun showCalendar()'''
if "NotificationCenterActivity::class.java" not in s:
    s, n = pattern.subn(replacement, s, count=1)
    if n != 1:
        raise SystemExit("Home showNotifications block not found")

s = s.replace('box.addView(infoRow("Sürüm", "0.9.2"))', 'box.addView(infoRow("Sürüm", "0.9.3"))')
home.write_text(s, encoding="utf-8")

# ---- Manifest ----
s = manifest.read_text(encoding="utf-8")
activity_line = '        <activity android:name=".notification.NotificationCenterActivity" android:exported="false" android:launchMode="singleTop" />\n'
if ".notification.NotificationCenterActivity" not in s:
    anchor = '        <activity android:name=".ElakHomeActivity" android:exported="false" android:launchMode="singleTop" />\n'
    if anchor not in s:
        raise SystemExit("Manifest home activity anchor not found")
    s = s.replace(anchor, anchor + activity_line)
manifest.write_text(s, encoding="utf-8")

# ---- Version ----
s = gradle.read_text(encoding="utf-8")
s = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 39', s)
s = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.9.3"', s)
gradle.write_text(s, encoding="utf-8")

s = login.read_text(encoding="utf-8")
s = s.replace("ELAK Okulum 0.9.2", "ELAK Okulum 0.9.3")
login.write_text(s, encoding="utf-8")

print("ELAK Okulum 0.9.3 notification center patch applied")
