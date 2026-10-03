package io.homeassistant.companion.android.common.data.customheaders

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import timber.log.Timber

/**
 * Encrypts and decrypts the custom headers before they are written to disk.
 */
internal interface HeaderCipher {
    fun encrypt(plainText: String): String

    /** @return the decrypted text or `null` if [cipherText] can't be decrypted, e.g. the key was invalidated. */
    fun decrypt(cipherText: String): String?
}

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "ha_custom_headers_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_SIZE_BITS = 256
private const val TAG_SIZE_BITS = 128
private const val IV_SIZE_BYTES = 12

/**
 * [HeaderCipher] using an AES-GCM key that never leaves the Android Keystore. The key is not part
 * of any backup, so encrypted values restored on another device are unreadable by design.
 */
internal class KeystoreHeaderCipher @Inject constructor() : HeaderCipher {

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String? = try {
        val bytes = Base64.decode(cipherText, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, bytes, 0, IV_SIZE_BYTES))
        }
        String(cipher.doFinal(bytes, IV_SIZE_BYTES, bytes.size - IV_SIZE_BYTES), Charsets.UTF_8)
    } catch (e: Exception) {
        // Never log the content, only the failure class
        Timber.e(e, "Unable to decrypt the custom headers")
        null
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
        }.generateKey()
    }
}
