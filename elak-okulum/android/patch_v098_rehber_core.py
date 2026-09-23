from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
rehber = ROOT / "app/src/main/java/com/elak/okulum/rehber/RehberApi.kt"
login = ROOT / "app/src/main/java/com/elak/okulum/LoginActivity.kt"
gradle = ROOT / "app/build.gradle.kts"
home = ROOT / "app/src/main/java/com/elak/okulum/ElakHomeActivity.kt"
central = ROOT / "app/src/main/java/com/elak/okulum/auth/CentralApi.kt"
fcm = ROOT / "app/src/main/java/com/elak/okulum/notification/FcmBootstrap.kt"

# Rehber must use ELAK CORE / Kurum Veri Merkezi bridge.
s = rehber.read_text(encoding="utf-8")
if 'https://elak.mcoaihl.com/core-rehber.php' not in s:
    raise SystemExit('RehberApi CORE bridge base is missing')
s = re.sub(r'private const val UA = "[^"]+"', 'private const val UA = "ELAK-Okulum/0.9.8 Android"', s)
rehber.write_text(s, encoding="utf-8")

# Pass the institution code to Rehber login for exact multi-school matching.
s = login.read_text(encoding="utf-8")
old = '''            // Eski MCOAIHL modüllerine yalnız MCOAIHL okul kodunda geri dönülür.\n            val legacyMco = code.equals("MCOAIHL", ignoreCase = true)\n            if (legacyMco) {\n                try { rehber = RehberApi.login(user, pass) } catch (e: Exception) { rehberError = e.message }\n                try { izin = IzinApi.login(user, pass) } catch (e: Exception) { izinError = e.message }\n            }\n'''
new = '''            // Akıllı Rehber tüm lisanslı kurumlarda CORE / Kurum Veri Merkezi üzerinden çalışır.\n            try { rehber = RehberApi.login(code, user, pass) } catch (e: Exception) { rehberError = e.message }\n\n            // İzin modülünün eski MCO bağlantısı şimdilik yalnız MCOAIHL için korunur.\n            val legacyMco = code.equals("MCOAIHL", ignoreCase = true)\n            if (legacyMco) {\n                try { izin = IzinApi.login(user, pass) } catch (e: Exception) { izinError = e.message }\n            }\n'''
if old in s:
    s = s.replace(old, new)
elif 'RehberApi.login(user, pass)' in s:
    s = s.replace('RehberApi.login(user, pass)', 'RehberApi.login(code, user, pass)')

# Refresh the Rehber token whenever a saved CORE session is resumed.
helper = '''    private fun refreshRehberSession(code: String, user: String, pass: String) {\n        if (code.isBlank() || user.isBlank() || pass.isBlank()) return\n        try {\n            val r = RehberApi.login(code, user, pass)\n            RehberSession(this).apply { token = r.token; username = user }\n        } catch (_: Exception) { }\n    }\n\n'''
if 'private fun refreshRehberSession(' not in s:
    anchor = '    private fun normalizedSchoolCode(): String = schoolCode.text.toString().trim().uppercase()\n'
    if anchor not in s:
        raise SystemExit('LoginActivity normalizedSchoolCode anchor missing')
    s = s.replace(anchor, helper + anchor)

resume_anchor = '                        session.updateCentralProfile(profile)\n                        runOnUiThread {'
resume_repl = '                        session.updateCentralProfile(profile)\n                        refreshRehberSession(profile.schoolCode, session.username, session.password)\n                        runOnUiThread {'
s = s.replace(resume_anchor, resume_repl)

refresh_anchor = '                        session.updateCentralProfile(profile)\n                        runOnUiThread {\n                            schoolCode.setText(profile.schoolCode)'
refresh_repl = '                        session.updateCentralProfile(profile)\n                        refreshRehberSession(profile.schoolCode, session.username, session.password)\n                        runOnUiThread {\n                            schoolCode.setText(profile.schoolCode)'
# The generic replacement above already covers both occurrences; this keeps the patch idempotent.
s = s.replace('ELAK Okulum 0.9.3.1', 'ELAK Okulum 0.9.8')
s = s.replace('ELAK Okulum 0.9.3', 'ELAK Okulum 0.9.8')
login.write_text(s, encoding="utf-8")

# Version must be greater than the installed 0.9.3.1 / code 40.
s = gradle.read_text(encoding="utf-8")
s = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 41', s)
s = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.9.8"', s)
gradle.write_text(s, encoding="utf-8")

# Keep visible/version transport labels consistent.
s = home.read_text(encoding="utf-8")
s = s.replace('0.9.3.1', '0.9.8').replace('0.9.3', '0.9.8')
home.write_text(s, encoding="utf-8")

s = central.read_text(encoding="utf-8")
s = s.replace('ELAK-Okulum/0.9.3.1 CORE Android', 'ELAK-Okulum/0.9.8 CORE Android')
s = s.replace('ELAK-Okulum/0.9.3 CORE Android', 'ELAK-Okulum/0.9.8 CORE Android')
central.write_text(s, encoding="utf-8")

s = fcm.read_text(encoding="utf-8")
s = s.replace('"0.9.3.1"', '"0.9.8"')
fcm.write_text(s, encoding="utf-8")

print('ELAK Okulum 0.9.8 Kurum Veri Merkezi / Akilli Rehber patch applied')
