package app.picnic.player.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256/GCM encryption backed by a non-exportable key in the Android Keystore
 * (best-practice token storage). Used to encrypt access tokens before
 * they touch DataStore — the key never leaves the TEE/StrongBox.
 */
@Singleton
class SecureStore @Inject constructor() {

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }
        val generator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Encrypts [plain] → Base64(iv ‖ ciphertext). */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    /** Decrypts a value from [encrypt], or null if it cannot be read (e.g. the
     *  Keystore key was invalidated). */
    fun decrypt(data: String): String? = runCatching {
        val raw = Base64.decode(data, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = raw.copyOfRange(GCM_IV_BYTES, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "picnic.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
