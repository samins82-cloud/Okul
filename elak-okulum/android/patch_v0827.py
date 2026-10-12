from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/elak/okulum/MainActivity.kt"
HOME = ROOT / "app/src/main/java/com/elak/okulum/ElakHomeActivity.kt"
IZIN = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinActivityModern.kt"
API = ROOT / "app/src/main/java/com/elak/okulum/izin/IzinApi.kt"

# ---------------- MainActivity: persist CORE credentials in Android Keystore-backed bridge
s = MAIN.read_text(encoding="utf-8")
if "import com.elak.okulum.auth.SecureOkulumCredentials" not in s:
    s = s.replace("import com.elak.okulum.izin.IzinApi", "import com.elak.okulum.auth.SecureOkulumCredentials\nimport com.elak.okulum.izin.IzinActivityModern\nimport com.elak.okulum.izin.IzinApi")

# Never open legacy /izin routes in the generic web portal.
old = '''        if (intent.getBooleanExtra(EXTRA_FORCE_WEB, false)) {
            val url = intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.startsWith("https://") }
                ?: CORE_BASE
            showWebPortal(url)
            return
        }'''
new = '''        if (intent.getBooleanExtra(EXTRA_FORCE_WEB, false)) {
            val url = intent.getStringExtra(EXTRA_START_URL)?.takeIf { it.startsWith("https://") }
                ?: CORE_BASE
            val u = try { Uri.parse(url) } catch (_: Exception) { null }
            if (u != null && u.path.orEmpty().startsWith("/izin")) {
                startActivity(Intent(this, IzinActivityModern::class.java))
                finish()
                return
            }
            showWebPortal(url)
            return
        }'''
if old in s:
    s = s.replace(old, new, 1)

# Restore missing password from the dedicated Keystore bridge before trying CORE re-login.
needle = '''    private fun restoreOrLogin(session: OkulumSession) {
        if (busy) return'''
replacement = '''    private fun restoreOrLogin(session: OkulumSession) {
        if (session.password.isBlank()) {
            SecureOkulumCredentials(this).load()?.let { saved ->
                if (session.username.isBlank() || session.username.equals(saved.username, true)) {
                    session.saveCredentials(session.orgCode, saved.username, saved.password)
                }
            }
        }
        if (busy) return'''
if needle in s and "SecureOkulumCredentials(this).load()" not in s:
    s = s.replace(needle, replacement, 1)

# After a successful CORE login, also store credentials in Android Keystore bridge.
needle = '''                state = OkulumCoreApi.login(code, user, pass)
                OkulumSession(this).saveCore(state!!, pass)'''
replacement = '''                state = OkulumCoreApi.login(code, user, pass)
                OkulumSession(this).saveCore(state!!, pass)
                SecureOkulumCredentials(this).save(user, pass)'''
if needle in s:
    s = s.replace(needle, replacement, 1)

# Native-module binding uses the encrypted bridge as a fallback, never asks module password again.
old = '''        val user = core.username
        val pass = core.password
        if (user.isBlank() || pass.isBlank()) return'''
new = '''        var user = core.username
        var pass = core.password
        if (user.isBlank() || pass.isBlank()) {
            SecureOkulumCredentials(this).load()?.let { saved ->
                if (user.isBlank() || user.equals(saved.username, true)) {
                    user = saved.username
                    pass = saved.password
                }
            }
        }
        if (user.isBlank() || pass.isBlank()) return'''
if old in s:
    s = s.replace(old, new, 1)

# Generic portal also redirects any discovered izin link to native screen.
needle = '''                if (u.host?.equals("elak.mcoaihl.com", true) == true && u.path.orEmpty().startsWith("/rehber")) {
                    startActivity(Intent(this@MainActivity, RehberActivity81::class.java)); return true
                }'''
replacement = '''                if (u.path.orEmpty().startsWith("/izin") && (u.host?.contains("mcoaihl.com", true) == true)) {
                    startActivity(Intent(this@MainActivity, IzinActivityModern::class.java)); return true
                }
                if (u.host?.equals("elak.mcoaihl.com", true) == true && u.path.orEmpty().startsWith("/rehber")) {
                    startActivity(Intent(this@MainActivity, RehberActivity81::class.java)); return true
                }'''
if needle in s:
    s = s.replace(needle, replacement, 1)

s = s.replace("ELAK Okulum v0.9.2", "ELAK Okulum v0.9.18")
s = s.replace("ELAK-Okulum/0.9.2 tr-TR", "ELAK-Okulum/0.9.18 tr-TR")
MAIN.write_text(s, encoding="utf-8")

# ---------------- Home: open native izin directly, no compatibility/web wrapper
s = HOME.read_text(encoding="utf-8")
if "import com.elak.okulum.izin.IzinActivityModern" not in s:
    s = s.replace("import com.elak.okulum.izin.IzinActivity88", "import com.elak.okulum.izin.IzinActivity88\nimport com.elak.okulum.izin.IzinActivityModern")
s = s.replace('"izin_takip"->startActivity(Intent(this,IzinActivity88::class.java))', '"izin_takip"->startActivity(Intent(this,IzinActivityModern::class.java))')
s = s.replace('mini("Sürüm","0.9.3",blue)', 'mini("Sürüm","0.9.18",blue)')
HOME.write_text(s, encoding="utf-8")

# ---------------- Izin native activity: CORE SSO -> secure bridge fallback; never show username/password form
s = IZIN.read_text(encoding="utf-8")
if "import com.elak.okulum.auth.SecureOkulumCredentials" not in s:
    s = s.replace("import com.elak.okulum.OkulumSession", "import com.elak.okulum.OkulumSession\nimport com.elak.okulum.auth.SecureOkulumCredentials")

old = '''            if (result == null) {
                val okulum = OkulumSession(this)
                if (okulum.username.isNotBlank() && okulum.password.isNotBlank()) {
                    try { result = IzinApi.login(okulum.username, okulum.password) } catch (e: Exception) { message = e.message }
                } else message = "ELAK Okulum kişisel oturum bilgisi henüz alınamadı."
            }'''
new = '''            if (result == null) {
                val okulum = OkulumSession(this)
                var username = okulum.username
                var password = okulum.password
                if (username.isBlank() || password.isBlank()) {
                    SecureOkulumCredentials(this).load()?.let { saved ->
                        if (username.isBlank() || username.equals(saved.username, true)) {
                            username = saved.username
                            password = saved.password
                            okulum.saveCredentials(okulum.orgCode, username, password)
                        }
                    }
                }
                if (username.isNotBlank() && password.isNotBlank()) {
                    try {
                        result = IzinApi.login(username, password)
                    } catch (e: Exception) {
                        message = e.message
                    }
                } else {
                    message = "ELAK Okulum oturumu bulunamadı. Ana uygulamaya dönüp CORE oturumunu yenileyin."
                }
            }'''
if old in s:
    s = s.replace(old, new, 1)

# Make retry page explicit: no second module credentials are ever requested.
s = s.replace(
    "Bu ekranda ayrıca kullanıcı adı veya şifre istenmez. ELAK Okulum ana girişinde kişisel kullanıcı hesabınızla bir kez giriş yapmanız yeterlidir.",
    "İzin Takip ayrı kullanıcı adı veya şifre istemez. ELAK Okulum CORE oturumunuz otomatik kullanılır."
)
IZIN.write_text(s, encoding="utf-8")

# ---------------- API user-agent version
s = API.read_text(encoding="utf-8")
s = re.sub(r'ELAK-Okulum/0\.9\.\d+ Android', 'ELAK-Okulum/0.9.18 Android', s)
API.write_text(s, encoding="utf-8")

print("v0.9.18 native Izin routing + CORE SSO bridge applied")
