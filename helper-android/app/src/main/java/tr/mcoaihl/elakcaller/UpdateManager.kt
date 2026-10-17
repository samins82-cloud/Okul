package tr.mcoaihl.elakcaller

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

object UpdateManager {
    private const val UPDATE_META_URL =
        "https://elak.mcoaihl.com/rehber/downloads/ELAK-Arayan-Tanima.json"
    private const val UPDATE_PREFS = "elak_caller_update"
    private const val LAST_CHECK = "last_check"
    private const val PENDING_RELEASE = "pending_release"
    private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

    data class ReleaseInfo(
        val version: String,
        val versionCode: Long,
        val apkUrl: String,
        val sha256: String,
        val size: Long,
        val notes: String
    ) {
        fun toJson(): String = JSONObject()
            .put("version", version)
            .put("version_code", versionCode)
            .put("apk_url", apkUrl)
            .put("sha256", sha256)
            .put("size", size)
            .put("notes", notes)
            .toString()

        companion object {
            fun fromJson(raw: String): ReleaseInfo? = try {
                val json = JSONObject(raw)
                ReleaseInfo(
                    version = json.optString("version", ""),
                    versionCode = json.optLong("version_code", 0L),
                    apkUrl = json.optString("apk_url", ""),
                    sha256 = json.optString("sha256", ""),
                    size = json.optLong("size", 0L),
                    notes = json.optString("notes", "")
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun checkForUpdates(activity: Activity, silent: Boolean, force: Boolean = false) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = activity.getSharedPreferences(UPDATE_PREFS, Activity.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(LAST_CHECK, 0L)
        if (!force && silent && now - last < AUTO_CHECK_INTERVAL_MS) return
        prefs.edit().putLong(LAST_CHECK, now).apply()

        thread(name = "elak-update-check") {
            try {
                val release = fetchRelease()
                val currentCode = currentVersionCode(activity)
                val hasUpdate = release.versionCode > currentCode
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (hasUpdate) {
                        showUpdateDialog(activity, release)
                    } else if (!silent) {
                        Toast.makeText(
                            activity,
                            "Uygulama güncel · v${currentVersionName(activity)}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                if (!silent) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Güncelleme kontrolü yapılamadı: ${e.message ?: "Bağlantı hatası"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    fun resumePendingInstall(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = activity.getSharedPreferences(UPDATE_PREFS, Activity.MODE_PRIVATE)
        val raw = prefs.getString(PENDING_RELEASE, "").orEmpty()
        if (raw.isBlank()) return
        val release = ReleaseInfo.fromJson(raw) ?: run {
            prefs.edit().remove(PENDING_RELEASE).apply()
            return
        }
        if (release.versionCode <= currentVersionCode(activity)) {
            prefs.edit().remove(PENDING_RELEASE).apply()
            return
        }
        if (activity.packageManager.canRequestPackageInstalls()) {
            prefs.edit().remove(PENDING_RELEASE).apply()
            downloadAndInstall(activity, release)
        }
    }

    private fun showUpdateDialog(activity: Activity, release: ReleaseInfo) {
        val message = buildString {
            append("Mevcut sürüm: v${currentVersionName(activity)}\n")
            append("Yeni sürüm: v${release.version}\n")
            if (release.notes.isNotBlank()) {
                append("\n")
                append(release.notes)
            }
        }
        AlertDialog.Builder(activity)
            .setTitle("Yeni sürüm mevcut")
            .setMessage(message)
            .setPositiveButton("Güncelle") { _, _ -> startUpdate(activity, release) }
            .setNegativeButton("Daha Sonra", null)
            .show()
    }

    private fun startUpdate(activity: Activity, release: ReleaseInfo) {
        if (!isTrustedUpdateUrl(release.apkUrl)) {
            Toast.makeText(activity, "Güncelleme adresi doğrulanamadı.", Toast.LENGTH_LONG).show()
            return
        }

        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.getSharedPreferences(UPDATE_PREFS, Activity.MODE_PRIVATE)
                .edit()
                .putString(PENDING_RELEASE, release.toJson())
                .apply()
            Toast.makeText(
                activity,
                "ELAK güncellemesini kurabilmek için bu kaynaktan uygulama yüklemeye izin verin. Uygulamaya dönünce indirme otomatik başlayacak.",
                Toast.LENGTH_LONG
            ).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
            return
        }

        downloadAndInstall(activity, release)
    }

    private fun downloadAndInstall(activity: Activity, release: ReleaseInfo) {
        Toast.makeText(activity, "ELAK güncellemesi indiriliyor…", Toast.LENGTH_SHORT).show()
        thread(name = "elak-update-download") {
            try {
                val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IllegalStateException("İndirme klasörü açılamadı")
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, "ELAK-Arayan-Tanima-${release.version}.apk")
                if (target.exists()) target.delete()

                val digest = MessageDigest.getInstance("SHA-256")
                val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 20000
                    useCaches = false
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
                    setRequestProperty("User-Agent", "ELAK-Caller-Updater/${currentVersionName(activity)}")
                }

                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw IllegalStateException("HTTP $code")
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count <= 0) break
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            output.flush()
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                if (release.size > 0 && target.length() != release.size) {
                    target.delete()
                    throw IllegalStateException("İndirilen APK boyutu doğrulanamadı")
                }

                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (release.sha256.isNotBlank() && !actualSha.equals(release.sha256, true)) {
                    target.delete()
                    throw IllegalStateException("APK bütünlük doğrulaması başarısız")
                }

                activity.runOnUiThread {
                    try {
                        installApk(activity, target)
                    } catch (e: Exception) {
                        Toast.makeText(
                            activity,
                            "Kurulum ekranı açılamadı: ${e.message ?: "Hata"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Güncelleme indirilemedi: ${e.message ?: "Bağlantı hatası"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun installApk(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    private fun fetchRelease(): ReleaseInfo {
        val metaUrl = URL("$UPDATE_META_URL?t=${System.currentTimeMillis()}")
        if (!isTrustedUpdateUrl(metaUrl.toString())) {
            throw IllegalStateException("Güncelleme kaynağı güvenilir değil")
        }
        val connection = (metaUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 5000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "ELAK-Caller-Updater")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = json.optString("version", "").trim()
            val versionCode = json.optLong("version_code", 0L)
            if (version.isBlank() || versionCode <= 0L) {
                throw IllegalStateException("Sürüm bilgisi eksik")
            }
            val apkUrl = json.optString("apk_url", "").trim().ifBlank {
                UPDATE_META_URL.substringBeforeLast('/') + "/ELAK-Arayan-Tanima.apk"
            }
            if (!isTrustedUpdateUrl(apkUrl)) {
                throw IllegalStateException("APK adresi güvenilir değil")
            }
            return ReleaseInfo(
                version = version,
                versionCode = versionCode,
                apkUrl = apkUrl,
                sha256 = json.optString("sha256", "").trim(),
                size = json.optLong("size", 0L),
                notes = json.optString("notes", "").trim()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isTrustedUpdateUrl(raw: String): Boolean = try {
        val uri = Uri.parse(raw)
        uri.scheme.equals("https", true) &&
            uri.host.equals("elak.mcoaihl.com", true) &&
            uri.path.orEmpty().startsWith("/rehber/downloads/")
    } catch (_: Exception) {
        false
    }

    private fun currentVersionCode(activity: Activity): Long =
        activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode

    private fun currentVersionName(activity: Activity): String =
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?"
}
