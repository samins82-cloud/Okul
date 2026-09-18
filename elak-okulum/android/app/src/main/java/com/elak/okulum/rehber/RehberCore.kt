package com.elak.okulum.rehber

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.MessageDigest
import javax.crypto.Mac
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

// ===== RehberModels.kt =====
data class StudentRecord(
    val id: Long,
    val name: String,
    val schoolNo: String,
    val className: String,
    val phone: String,
    val hasPhoto: Boolean,
    val photoVersion: Long,
    val canEdit: Boolean
)

data class GuardianRecord(
    val id: Long,
    val studentId: Long,
    val name: String,
    val relationship: String,
    val phone: String,
    val studentName: String = "",
    val schoolNo: String = "",
    val className: String = ""
)

data class ClassRecord(
    val id: Long,
    val name: String,
    val grade: String,
    val whatsappGroupUrl: String,
    val isClassTeacher: Boolean
)

data class AnnouncementRecord(
    val id: String,
    val title: String,
    val summary: String,
    val publishedAt: String,
    val priority: String
)

data class HistoryRecord(
    val id: Long,
    val phone: String,
    val callTime: Long,
    val matched: Boolean,
    val guardianName: String,
    val relationship: String,
    val studentName: String,
    val className: String,
    val schoolNo: String,
    val studentId: Long,
    val status: String
)

// ===== PhoneUtil.kt =====
object PhoneUtil {
    /** Canonical Turkish national number: 10 digits, e.g. 5321234567. */
    fun normalize(raw: String?): String {
        var d = raw.orEmpty().filter { it.isDigit() }
        if (d.startsWith("0090")) d = d.drop(4)
        if (d.startsWith("90") && d.length >= 12) d = d.drop(2)
        if (d.startsWith("0") && d.length >= 11) d = d.drop(1)
        if (d.length > 10) d = d.takeLast(10)
        return d
    }

    fun international(raw: String?): String {
        val n = normalize(raw)
        return if (n.length == 10) "90$n" else n
    }

    fun display(raw: String?): String {
        val n = normalize(raw)
        return if (n.length == 10) "0\${n.substring(0, 3)} \${n.substring(3, 6)} \${n.substring(6, 8)} \${n.substring(8)}" else raw.orEmpty()
    }
}

// ===== LocalCrypto.kt =====
object LocalCrypto {
    private const val KEY_ALIAS = "elak_local_data_aes_v2"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String?): String {
        val value = plain.orEmpty()
        if (value.isBlank()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val all = ByteArray(cipher.iv.size + encrypted.size)
            System.arraycopy(cipher.iv, 0, all, 0, cipher.iv.size)
            System.arraycopy(encrypted, 0, all, cipher.iv.size, encrypted.size)
            Base64.encodeToString(all, Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    fun decrypt(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return try {
            val all = Base64.decode(encoded, Base64.NO_WRAP)
            if (all.size < 13) return ""
            val iv = all.copyOfRange(0, 12)
            val cipherText = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}

// ===== PhoneHasher.kt =====
object PhoneHasher {
    private const val KEY_ALIAS = "elak_caller_hmac_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return gen.generateKey()
    }

    fun hash(raw: String?): String {
        val normalized = PhoneUtil.normalize(raw)
        if (normalized.length < 10) return ""
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(key())
            Base64.encodeToString(mac.doFinal(normalized.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (_: Exception) {
            // Deterministic fallback keeps caller lookup usable on unusual devices.
            Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()), Base64.NO_WRAP)
        }
    }
}

// ===== RehberSession.kt =====
class RehberSession(context: Context) {
