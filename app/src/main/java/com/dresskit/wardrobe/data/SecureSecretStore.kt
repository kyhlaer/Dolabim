package com.dresskit.wardrobe.data

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

/**
 * Encrypts sensitive user credentials (Gemini API keys, Hugging Face tokens)
 * at rest using a hardware-backed / AndroidKeyStore AES-256-GCM key and
 * transparently upgrades legacy plaintext SharedPreferences entries.
 */
internal object SecureSecretStore {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "dresskit_secret_aes_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX_ENC_V1 = "enc_v1:"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    private val LOCK = Any()

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        require(iv != null && iv.size == GCM_IV_LEN) { "Invalid GCM IV length" }
        val ciphertext = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return PREFIX_ENC_V1 + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encodedPayload: String): String? {
        if (!encodedPayload.startsWith(PREFIX_ENC_V1)) return null
        val b64 = encodedPayload.removePrefix(PREFIX_ENC_V1)
        val combined = runCatching { Base64.decode(b64, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (combined.size <= GCM_IV_LEN + 16) return null
        val iv = combined.copyOfRange(0, GCM_IV_LEN)
        val ciphertext = combined.copyOfRange(GCM_IV_LEN, combined.size)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(ciphertext)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    fun getSecret(
        context: Context,
        prefsName: String,
        prefKey: String,
        validator: (String) -> Boolean
    ): String = synchronized(LOCK) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val raw = prefs.getString(prefKey, "")?.trim().orEmpty()
        if (raw.isEmpty()) return ""

        if (raw.startsWith(PREFIX_ENC_V1)) {
            val decrypted = runCatching { decrypt(raw)?.trim().orEmpty() }.getOrNull().orEmpty()
            return if (validator(decrypted)) decrypted else ""
        }

        // Legacy plaintext migration: re-encrypt valid secret or purge invalid entry
        if (!validator(raw)) {
            prefs.edit().remove(prefKey).apply()
            return ""
        }
        runCatching {
            val sealed = encrypt(raw)
            prefs.edit().putString(prefKey, sealed).apply()
        }
        return raw
    }

    fun setSecret(
        context: Context,
        prefsName: String,
        prefKey: String,
        value: String,
        validator: (String) -> Boolean
    ) = synchronized(LOCK) {
        val clean = value.trim()
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (clean.isEmpty()) {
            prefs.edit().remove(prefKey).apply()
            return
        }
        if (!validator(clean)) return
        // Fail closed: never fall back to writing plaintext secrets
        val sealed = runCatching { encrypt(clean) }.getOrNull() ?: return
        prefs.edit().putString(prefKey, sealed).apply()
    }

    fun clearSecret(context: Context, prefsName: String, prefKey: String) = synchronized(LOCK) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .remove(prefKey)
            .apply()
    }
}
