package com.elak.okulum.rehber

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object LocalCrypto {
    private const val KEY_ALIAS = "elak_rehber_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

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
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val all = ByteArray(cipher.iv.size + encrypted.size)
            System.arraycopy(cipher.iv, 0, all, 0, cipher.iv.size)
            System.arraycopy(encrypted, 0, all, cipher.iv.size, encrypted.size)
            Base64.encodeToString(all, Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }

    fun decrypt(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return try {
            val all = Base64.decode(value, Base64.NO_WRAP)
            if (all.size <= 12) return ""
            val iv = all.copyOfRange(0, 12)
            val encrypted = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }
}
