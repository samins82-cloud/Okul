from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
api = ROOT / "app/src/main/java/com/elak/okulum/auth/CentralApi.kt"
store = ROOT / "app/src/main/java/com/elak/okulum/notification/NotificationStore.kt"
center = ROOT / "app/src/main/java/com/elak/okulum/notification/NotificationCenterActivity.kt"
home = ROOT / "app/src/main/java/com/elak/okulum/ElakHomeActivity.kt"
manifest = ROOT / "app/src/main/AndroidManifest.xml"
gradle = ROOT / "app/build.gradle.kts"
login = ROOT / "app/src/main/java/com/elak/okulum/LoginActivity.kt"

# ---- Gradle / version ----
s = gradle.read_text(encoding="utf-8")
if 'com.google.firebase:firebase-messaging' not in s:
    s = s.replace('    implementation("androidx.work:work-runtime-ktx:2.10.0")\n', '    implementation("androidx.work:work-runtime-ktx:2.10.0")\n    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))\n    implementation("com.google.firebase:firebase-messaging")\n')
s = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 40', s)
s = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.9.3.1"', s)
gradle.write_text(s, encoding="utf-8")

# ---- CentralApi mobile push transport ----
s = api.read_text(encoding="utf-8")
s = s.replace('ELAK-Okulum/0.9.3 CORE Android', 'ELAK-Okulum/0.9.3.1 CORE Android')
if 'private const val PUSH_BASE' not in s:
    s = s.replace('    private const val ORIGIN = "https://elak.mcoaihl.com"\n', '    private const val ORIGIN = "https://elak.mcoaihl.com"\n    private const val PUSH_BASE = "https://elak.mcoaihl.com/mobile-push.php?action="\n')

mobile_public = '''    fun registerPushDevice(accessToken: String, fcmToken: String, deviceId: String, appVersion: String) {\n        if (accessToken.isBlank() || fcmToken.isBlank() || deviceId.isBlank()) return\n        val body = JSONObject()\n            .put("fcm_token", fcmToken)\n            .put("device_id", deviceId)\n            .put("app_version", appVersion)\n        pushRequest("device_register", "POST", decodeSession(accessToken), body)\n    }\n\n    fun notificationFeed(accessToken: String): List<Announcement> {\n        if (accessToken.isBlank()) return emptyList()\n        val response = pushRequest("notification_list", "GET", decodeSession(accessToken), null)\n        val arr = response.json.optJSONArray("notifications") ?: JSONArray()\n        val out = mutableListOf<Announcement>()\n        for (i in 0 until arr.length()) {\n            val o = arr.optJSONObject(i) ?: continue\n            out += Announcement(\n                id = o.optLong("id"),\n                title = o.optString("title"),\n                body = o.optString("body"),\n                level = o.optString("level", "info"),\n                createdAt = o.optString("created_at")\n            )\n        }\n        return out.sortedByDescending { it.createdAt }\n    }\n\n    fun notificationAck(accessToken: String, notificationId: Long, deviceId: String, state: String) {\n        if (accessToken.isBlank() || notificationId <= 0L || deviceId.isBlank()) return\n        val body = JSONObject()\n            .put("notification_id", notificationId)\n            .put("device_id", deviceId)\n            .put("state", state)\n        pushRequest("notification_ack", "POST", decodeSession(accessToken), body)\n    }\n\n'''
if 'fun registerPushDevice(' not in s:
    anchor = '    fun logout(accessToken: String) {'
    if anchor not in s:
        raise SystemExit('CentralApi logout anchor missing')
    s = s.replace(anchor, mobile_public + anchor)

push_request = '''    private fun pushRequest(action: String, method: String, session: CoreSession?, body: JSONObject?): CoreResponse {\n        val url = URL(PUSH_BASE + URLEncoder.encode(action, "UTF-8"))\n        val conn = (url.openConnection() as HttpURLConnection).apply {\n            requestMethod = method\n            connectTimeout = 8000\n            readTimeout = 15000\n            useCaches = false\n            instanceFollowRedirects = false\n            setRequestProperty("Accept", "application/json")\n            setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")\n            setRequestProperty("User-Agent", UA)\n            setRequestProperty("X-Requested-With", "ELAK-Okulum-Android")\n            session?.cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }\n            if (method != "GET" && session != null && session.csrf.isNotBlank()) {\n                setRequestProperty("X-CSRF-Token", session.csrf)\n            }\n            if (body != null) {\n                doOutput = true\n                setRequestProperty("Content-Type", "application/json; charset=UTF-8")\n            }\n        }\n        if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }\n        val code = conn.responseCode\n        val updatedCookie = extractSessionCookie(conn).ifBlank { session?.cookie.orEmpty() }\n        val stream = if (code in 200..299) conn.inputStream else conn.errorStream\n        val text = if (stream != null) BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() } else ""\n        conn.disconnect()\n        val trimmed = text.trimStart()\n        if (trimmed.startsWith("<!DOCTYPE", true) || trimmed.startsWith("<html", true)) {\n            throw IllegalStateException("Mobil bildirim servisi kullanılamıyor (HTTP $code).")\n        }\n        val json = try { JSONObject(text.ifBlank { "{}" }) } catch (_: Exception) {\n            throw IllegalStateException("Mobil bildirim servisi geçersiz yanıt verdi (HTTP $code).")\n        }\n        val csrf = json.optString("csrf").ifBlank { session?.csrf.orEmpty() }\n        if (code !in 200..299 || (json.has("ok") && !json.optBoolean("ok", false))) {\n            throw IllegalStateException(json.optString("error").ifBlank { "Mobil bildirim servisine bağlanılamadı (HTTP $code)." })\n        }\n        if (updatedCookie.isBlank()) throw IllegalStateException("ELAK CORE oturum bilgisi alınamadı.")\n        return CoreResponse(json, CoreSession(updatedCookie, csrf))\n    }\n\n'''
if 'private fun pushRequest(' not in s:
    anchor = '    private fun extractSessionCookie(conn: HttpURLConnection): String {'
    if anchor not in s:
        raise SystemExit('CentralApi extractSessionCookie anchor missing')
    s = s.replace(anchor, push_request + anchor)
api.write_text(s, encoding="utf-8")

# ---- NotificationStore: prefer targeted feed and accept FCM data ----
s = store.read_text(encoding="utf-8")
old = '''    private fun fetchWithRecovery(context: Context, session: OkulumSession): List<Item> {\n        if (session.hasCentralSession) {\n            try { return CentralApi.announcements(session.accessToken).map { it.toItem() } }\n            catch (_: Exception) { }\n        }\n        if (!session.hasCredentials) return cached(context)\n        val auth = CentralApi.login(session.username, session.password, session.schoolCode)\n        session.saveCentral(session.username, session.password, auth)\n        return CentralApi.announcements(session.accessToken).map { it.toItem() }\n    }\n'''
new = '''    private fun fetchWithRecovery(context: Context, session: OkulumSession): List<Item> {\n        if (session.hasCentralSession) {\n            try { return CentralApi.notificationFeed(session.accessToken).map { it.toItem() } } catch (_: Exception) { }\n            try { return CentralApi.announcements(session.accessToken).map { it.toItem() } } catch (_: Exception) { }\n        }\n        if (!session.hasCredentials) return cached(context)\n        val auth = CentralApi.login(session.username, session.password, session.schoolCode)\n        session.saveCentral(session.username, session.password, auth)\n        try { return CentralApi.notificationFeed(session.accessToken).map { it.toItem() } } catch (_: Exception) { }\n        return CentralApi.announcements(session.accessToken).map { it.toItem() }\n    }\n'''
if old in s:
    s = s.replace(old, new)

receive = '''    fun receivePush(context: Context, item: Item) {\n        val session = OkulumSession(context)\n        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n        val key = accountKey(session)\n        val merged = (listOf(item) + cached(context).filter { it.id != item.id })\n            .sortedByDescending { it.createdAt }\n            .take(100)\n        val notified = prefs.getStringSet("notified_$key", emptySet())?.toMutableSet() ?: mutableSetOf()\n        notified += item.id.toString()\n        prefs.edit()\n            .putBoolean("initialized_$key", true)\n            .putStringSet("notified_$key", notified)\n            .putString("feed_$key", toJson(merged).toString())\n            .apply()\n        postSystem(context, item)\n    }\n\n'''
if 'fun receivePush(context: Context, item: Item)' not in s:
    anchor = '    fun cached(context: Context): List<Item> {'
    if anchor not in s:
        raise SystemExit('NotificationStore cached anchor missing')
    s = s.replace(anchor, receive + anchor)
store.write_text(s, encoding="utf-8")

# ---- NotificationCenter: report read ----
s = center.read_text(encoding="utf-8")
if 'private fun ackRead(' not in s:
    helper = '''    private fun ackRead(id: Long) {\n        if (id <= 0L || !session.hasCentralSession) return\n        thread {\n            try { CentralApi.notificationAck(session.accessToken, id, DeviceIdentity.id(this), "read") } catch (_: Exception) { }\n        }\n    }\n\n'''
    anchor = '    private fun requestNotificationPermissionIfNeeded() {'
    if anchor not in s:
        raise SystemExit('NotificationCenter permission anchor missing')
    s = s.replace(anchor, helper + anchor)
if 'import com.elak.okulum.auth.CentralApi' not in s:
    s = s.replace('import com.elak.okulum.OkulumSession\n', 'import com.elak.okulum.OkulumSession\nimport com.elak.okulum.auth.CentralApi\n')
s = s.replace('            NotificationStore.markRead(this, it)\n            render()', '            NotificationStore.markRead(this, it)\n            ackRead(it)\n            render()')
s = s.replace('                NotificationStore.markRead(this@NotificationCenterActivity, item.id)\n                showDetail(item)', '                NotificationStore.markRead(this@NotificationCenterActivity, item.id)\n                ackRead(item.id)\n                showDetail(item)')
# Fresh launch from a system notification.
needle = '        items = NotificationStore.cached(this)\n        render()\n        refresh()'
if needle in s and 'intent.getLongExtra("notification_id"' not in s.split('override fun onNewIntent')[0]:
    s = s.replace(needle, '        items = NotificationStore.cached(this)\n        intent.getLongExtra("notification_id", 0L).takeIf { it > 0L }?.let { NotificationStore.markRead(this, it); ackRead(it) }\n        render()\n        refresh()')
center.write_text(s, encoding="utf-8")

# ---- Home initializes FCM ----
s = home.read_text(encoding="utf-8")
if 'import com.elak.okulum.notification.FcmBootstrap' not in s:
    s = s.replace('import com.elak.okulum.notification.NotificationSyncWorker\n', 'import com.elak.okulum.notification.NotificationSyncWorker\nimport com.elak.okulum.notification.FcmBootstrap\n')
if 'FcmBootstrap.initialize(this)' not in s:
    s = s.replace('        NotificationSyncWorker.schedule(this)\n', '        NotificationSyncWorker.schedule(this)\n        FcmBootstrap.initialize(this)\n')
s = s.replace('box.addView(infoRow("Sürüm", "0.9.3"))', 'box.addView(infoRow("Sürüm", "0.9.3.1"))')
home.write_text(s, encoding="utf-8")

# ---- Manifest FCM service ----
s = manifest.read_text(encoding="utf-8")
service = '''        <service\n            android:name=".notification.ElakFirebaseMessagingService"\n            android:exported="false">\n            <intent-filter>\n                <action android:name="com.google.firebase.MESSAGING_EVENT" />\n            </intent-filter>\n        </service>\n'''
if '.notification.ElakFirebaseMessagingService' not in s:
    anchor = '        <service\n            android:name=".rehber.CallerIdService"'
    if anchor not in s:
        raise SystemExit('Manifest service anchor missing')
    s = s.replace(anchor, service + '\n' + anchor)
manifest.write_text(s, encoding="utf-8")

s = login.read_text(encoding="utf-8")
s = s.replace('ELAK Okulum 0.9.3', 'ELAK Okulum 0.9.3.1')
login.write_text(s, encoding="utf-8")

print('ELAK Okulum 0.9.3.1 FCM/receipt patch applied')
