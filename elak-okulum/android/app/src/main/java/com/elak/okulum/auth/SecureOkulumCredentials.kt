package com.elak.okulum.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureOkulumCredentials(private val context: Context) {
    data class Credentials(val username: String, val password: String)

    companion object {
        private const val STORE = "elak_okulum_secure"
        private const val KEY_ALIAS = "elak_okulum_sso_aes"
        private const val U = "u"
        private const val P = "p"
    }

    private val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun save(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) return
        prefs.edit()
            .putString(U, encrypt(username.trim()))
            .putString(P, encrypt(password))
            .apply()
    }

    fun load(): Credentials? {
        val eu = prefs.getString(U, null) ?: return null
        val ep = prefs.getString(P, null) ?: return null
        return try {
            val username = decrypt(eu)
            val password = decrypt(ep)
            if (username.isBlank() || password.isBlank()) null else Credentials(username, password)
        } catch (_: Exception) {
            clear(); null
        }
    }

    fun clear() {
        prefs.edit().remove(U).remove(P).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split('.', limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }
}
