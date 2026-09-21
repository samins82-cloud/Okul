package com.elak.okulum.auth

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Android istemcisini mevcut elak.mcoaihl.com ELAK CORE ile birleştirir.
 *
 * Sunucuda ikinci bir kullanıcı/veritabanı sistemi oluşturmaz. Web portalının
 * kullandığı /api.php, ELAKCORESESSID ve CSRF akışını kullanır. Oturum belirteci
 * OkulumSession tarafından cihazda şifreli saklanır. Sunucu oturumu sona ererse
 * LoginActivity kayıtlı kimlik bilgileriyle sessizce yeniden giriş yapabilir.
 */
object CentralApi {
    private const val BASE = "https://elak.mcoaihl.com/api.php?action="
    private const val ORIGIN = "https://elak.mcoaihl.com"
    private const val UA = "ELAK-Okulum/0.9.2 CORE Android"
    private const val SESSION_COOKIE = "ELAKCORESESSID"

    data class Link(
        val type: String,
        val externalId: String,
        val label: String,
        val schoolScope: String,
        val metaJson: String
    )

    data class Module(
        val key: String,
        val title: String,
        val description: String,
        val category: String,
        val icon: String,
        val color: String,
        val type: String,
        val url: String,
        val sso: Boolean,
        val canManage: Boolean
    )

    data class Profile(
        val id: Long,
        val username: String,
        val name: String,
        val primaryRole: String,
        val roles: List<String>,
        val schoolScope: String,
        val permissions: List<String>,
        val links: List<Link>,
        val modules: List<Module>,
        val schoolName: String,
        val schoolShortName: String,
        val schoolCode: String,
        val schoolLogoUrl: String
    )

    data class AuthResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val profile: Profile
    )

    data class RefreshResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    )

    private data class CoreSession(val cookie: String, val csrf: String)
    private data class CoreResponse(val json: JSONObject, val session: CoreSession)

    fun login(username: String, password: String, schoolCode: String): AuthResult {
        val code = schoolCode.trim().uppercase()
        val user = username.trim()
        if (code.isBlank()) throw IllegalArgumentException("Okul kodu gerekli.")
        if (user.isBlank() || password.isBlank()) throw IllegalArgumentException("Kullanıcı adı ve şifre gerekli.")

        // 1) PHP oturumunu ve CSRF anahtarını güvenli şekilde başlat.
        val boot = request("bootstrap", "GET", null, null)

        // 2) Web portalıyla aynı school_login sözleşmesini kullan.
        val loginBody = JSONObject()
            .put("code", code)
            .put("username", user)
            .put("password", password)
        val signedIn = request("school_login", "POST", boot.session, loginBody)

        // session_regenerate_id() sonrasında yeni cookie gelebilir. Son durumun tamamını
        // tekrar bootstrap'tan okuyarak kurum, kullanıcı, modül ve ayarları tek kaynaktan al.
        val current = request("bootstrap", "GET", signedIn.session, null)
        val profile = parseCoreProfile(current.json, user, code)
        if (!profile.schoolCode.equals(code, ignoreCase = true)) {
            throw IllegalStateException("Okul doğrulaması başarısız.")
        }

        val token = encodeSession(current.session)
        return AuthResult(
            accessToken = token,
            refreshToken = token,
            expiresIn = 7L * 24L * 60L * 60L,
            profile = profile
        )
    }

    fun refresh(refreshToken: String): RefreshResult {
        val old = decodeSession(refreshToken)
        val current = request("bootstrap", "GET", old, null)
        // Oturum gerçekten kuruma bağlı mı kontrol et; anonim bootstrap'ı geçerli sayma.
        if (current.json.optJSONObject("license") == null || current.json.optJSONObject("user") == null) {
            throw IllegalStateException("Merkezi oturumun süresi doldu.")
        }
        val token = encodeSession(current.session)
        return RefreshResult(token, token, 7L * 24L * 60L * 60L)
    }

    fun me(accessToken: String): Profile {
        val session = decodeSession(accessToken)
        val current = request("bootstrap", "GET", session, null)
        return parseCoreProfile(current.json, "", "")
    }

    fun logout(accessToken: String) {
        if (accessToken.isBlank()) return
        try {
            var session = decodeSession(accessToken)
            // CSRF güncel değilse bootstrap ile tazele.
            val boot = request("bootstrap", "GET", session, null)
            session = boot.session
            request("logout", "POST", session, JSONObject())
        } catch (_: Exception) {
            // Yerel oturum her durumda ayrıca temizlenir.
        }
    }

    private fun parseCoreProfile(root: JSONObject, usernameFallback: String, codeFallback: String): Profile {
        val license = root.optJSONObject("license")
            ?: throw IllegalStateException("Kurum oturumu bulunamadı. Lütfen tekrar giriş yapın.")
        val user = root.optJSONObject("user")
            ?: throw IllegalStateException("Kullanıcı oturumu bulunamadı. Lütfen tekrar giriş yapın.")
        val settings = root.optJSONObject("settings") ?: JSONObject()

        val role = user.optString("role_key").ifBlank { "user" }
        val permissions = permissionList(user.opt("permissions"))
        val modules = coreModules(root.optJSONArray("modules"), license.optJSONObject("modules"), license.optString("org_code"))
        val code = license.optString("org_code").ifBlank { codeFallback.trim().uppercase() }
        val schoolName = license.optString("org_name").ifBlank { code }
        val logo = absoluteUrl(settings.optString("logo_url"))

        return Profile(
            id = user.optLong("id", 0L),
            username = user.optString("username").ifBlank { usernameFallback },
            name = user.optString("full_name").ifBlank { usernameFallback },
            primaryRole = role,
            roles = listOf(role),
            schoolScope = "both",
            permissions = permissions,
            links = emptyList(),
            modules = modules,
            schoolName = schoolName,
            schoolShortName = code,
            schoolCode = code,
            schoolLogoUrl = logo
        )
    }

    /** Web index.php içindeki ALL_MODULES kataloğuyla aynı anahtarları kullanır. */
    private fun coreModules(apiRows: JSONArray?, licenseModules: JSONObject?, schoolCode: String): List<Module> {
        data class Seed(val key: String, val title: String, val icon: String, val desc: String, val url: String, val color: String)
        val seeds = linkedMapOf(
            "izin_takip" to Seed("izin_takip", "İzin Takip", "↔", "Öğrenci izin, okuldan çıkış ve dönüş işlemleri.", if (isMco(schoolCode)) "https://mcoaihl.com/izin/" else "", "#EA580C"),
            "akilli_rehber" to Seed("akilli_rehber", "Akıllı Rehber", "☎", "Öğrenci, veli ve personel iletişim rehberi.", if (isMco(schoolCode)) "$ORIGIN/rehber/" else "", "#0891B2"),
            "yoklama" to Seed("yoklama", "Online Yoklama", "✓", "Öğrenci devam-devamsızlığını dijital ortamda yönetin.", "", "#059669"),
            "ders_programi" to Seed("ders_programi", "Ders Programı", "▦", "Kurumun günlük ve haftalık ders programları.", "", "#2563EB"),
            "lgs_yks" to Seed("lgs_yks", "LGS / YKS", "▣", "Deneme sonuçları, analizler ve akademik takip.", if (isMco(schoolCode)) "$ORIGIN/deneme-sonuc/" else "", "#7C3AED"),
            "ortak" to Seed("ortak", "Ortak Sınav", "📝", "Ortak sınav planlama ve analiz modülü.", "", "#2563EB"),
            "kelebek" to Seed("kelebek", "Kelebek Sınav Yerleştirme", "🦋", "Salon, oturma, gözetmen ve rapor yönetimi.", "$ORIGIN/kelebek/core-entry.php", "#4F46E5"),
            "sorumluluk" to Seed("sorumluluk", "Sorumluluk Sınavı", "🎯", "Sorumluluk sınavlarını planlayın ve izleyin.", "", "#DC2626"),
            "ogretmen" to Seed("ogretmen", "Öğretmen Nöbet", "👩‍🏫", "Öğretmen nöbet çizelgelerini yönetin.", "", "#1060A0"),
            "ogrenci" to Seed("ogrenci", "Öğrenci Nöbet", "🧑‍🎓", "Öğrenci nöbet planlamasını yönetin.", "", "#A82010")
        )

        // DB kataloğu varsa web portalındaki gibi aynı anahtardaki varsayılanı günceller.
        if (apiRows != null) {
            for (i in 0 until apiRows.length()) {
                val row = apiRows.optJSONObject(i) ?: continue
                if (row.optInt("active", 1) == 0) continue
                val key = row.optString("module_key").trim()
                if (key.isBlank()) continue
                val old = seeds[key]
                val rawRoute = row.optString("route").trim()
                val route = if (rawRoute.isBlank()) old?.url.orEmpty() else absoluteUrl(rawRoute)
                seeds[key] = Seed(
                    key = key,
                    title = row.optString("module_name").ifBlank { old?.title ?: key },
                    icon = row.optString("icon").ifBlank { old?.icon ?: "•" },
                    desc = row.optString("description").ifBlank { old?.desc.orEmpty() },
                    url = route,
                    color = old?.color ?: "#2563EB"
                )
            }
        }

        // Web portalı yalnız lisans modules nesnesinde true olan kartları aktif gösteriyor.
        val enabled = licenseModules ?: JSONObject()
        return seeds.values.filter { seed -> enabled.optBoolean(seed.key, false) }.map { seed ->
            Module(
                key = seed.key,
                title = seed.title,
                description = seed.desc,
                category = "school",
                icon = seed.icon,
                color = seed.color,
                type = if (seed.url.isBlank()) "pending" else "web",
                url = seed.url,
                sso = false,
                canManage = permissionsCanManage(seed.key)
            )
        }
    }

    private fun permissionsCanManage(key: String): Boolean = key in setOf("yoklama", "ortak", "sorumluluk", "ogretmen", "ogrenci")

    private fun permissionList(value: Any?): List<String> {
        val out = mutableListOf<String>()
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (value.optBoolean(k, false)) out += k
                }
            }
            is JSONArray -> for (i in 0 until value.length()) value.optString(i).takeIf { it.isNotBlank() }?.let { out += it }
        }
        return out.distinct()
    }

    private fun request(action: String, method: String, session: CoreSession?, body: JSONObject?): CoreResponse {
        val url = URL(BASE + URLEncoder.encode(action, "UTF-8"))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 15000
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
            setRequestProperty("User-Agent", UA)
            setRequestProperty("X-Requested-With", "ELAK-Okulum-Android")
            session?.cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
            if (method != "GET" && session != null && session.csrf.isNotBlank()) {
                setRequestProperty("X-CSRF-Token", session.csrf)
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }

        if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val updatedCookie = extractSessionCookie(conn).ifBlank { session?.cookie.orEmpty() }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = if (stream != null) BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() } else ""
        conn.disconnect()

        val trimmed = text.trimStart()
        if (trimmed.startsWith("<!DOCTYPE", true) || trimmed.startsWith("<html", true)) {
            throw IllegalStateException("ELAK CORE servisi geçersiz sayfa döndürdü (HTTP $code).")
        }
        val json = try { JSONObject(text.ifBlank { "{}" }) } catch (_: Exception) {
            throw IllegalStateException("ELAK CORE geçersiz yanıt verdi (HTTP $code).")
        }
        val csrf = json.optString("csrf").ifBlank { session?.csrf.orEmpty() }
        if (code !in 200..299 || (json.has("ok") && !json.optBoolean("ok", false))) {
            throw IllegalStateException(json.optString("error").ifBlank { "ELAK CORE bağlantısı başarısız (HTTP $code)." })
        }
        if (updatedCookie.isBlank()) throw IllegalStateException("ELAK CORE oturum bilgisi alınamadı.")
        return CoreResponse(json, CoreSession(updatedCookie, csrf))
    }

    private fun extractSessionCookie(conn: HttpURLConnection): String {
        for ((key, values) in conn.headerFields) {
            if (!key.equals("Set-Cookie", ignoreCase = true)) continue
            for (raw in values.orEmpty()) {
                val first = raw.substringBefore(';').trim()
                if (first.startsWith("$SESSION_COOKIE=", ignoreCase = true)) return first
            }
        }
        return ""
    }

    private fun encodeSession(session: CoreSession): String {
        val raw = JSONObject().put("cookie", session.cookie).put("csrf", session.csrf).toString()
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decodeSession(token: String): CoreSession {
        try {
            val raw = String(Base64.decode(token, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
            val json = JSONObject(raw)
            val cookie = json.optString("cookie")
            if (cookie.isBlank() || !cookie.startsWith("$SESSION_COOKIE=", true)) throw IllegalArgumentException()
            return CoreSession(cookie, json.optString("csrf"))
        } catch (_: Exception) {
            throw IllegalStateException("Merkezi oturum bilgisi geçersiz.")
        }
    }

    private fun absoluteUrl(raw: String): String {
        val v = raw.trim()
        if (v.isBlank()) return ""
        if (v.startsWith("https://", true)) return v
        if (v.startsWith("http://", true)) return "" // Mobilde düz HTTP kabul edilmez.
        return if (v.startsWith('/')) ORIGIN + v else "$ORIGIN/$v"
    }

    private fun isMco(code: String): Boolean = code.equals("759975", true) || code.equals("MCOAIHL", true)
}
