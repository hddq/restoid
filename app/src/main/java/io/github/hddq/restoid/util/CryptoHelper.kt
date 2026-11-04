package io.github.hddq.restoid.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.UnrecoverableEntryException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoHelper {
    private const val KEY_ALIAS = "restoid_passwords_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SEPARATOR = "]"

    sealed interface DecryptionResult {
        data class Success(val value: String) : DecryptionResult
        object Invalid : DecryptionResult
        object FailedTemporarily : DecryptionResult
    }

    private val keyLock = Any()
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getSecretKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            return generateSecretKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw UnrecoverableKeyException("Stored key is not a secret key")
    }

    private fun regenerateSecretKey(): SecretKey {
        keyStore.deleteEntry(KEY_ALIAS)
        return generateSecretKey()
    }

    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the [plainText] using AES-GCM and returns a Base64 encoded string containing
     * the IV and the ciphertext separated by [IV_SEPARATOR].
     */
    fun encrypt(plainText: String): String {
        return synchronized(keyLock) {
            try {
                encryptWithKey(plainText, getSecretKey())
            } catch (_: KeyPermanentlyInvalidatedException) {
                encryptWithKey(plainText, regenerateSecretKey())
            } catch (_: UnrecoverableKeyException) {
                encryptWithKey(plainText, regenerateSecretKey())
            } catch (_: UnrecoverableEntryException) {
                encryptWithKey(plainText, regenerateSecretKey())
            }
        }
    }

    private fun encryptWithKey(plainText: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        
        val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedString = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$ivString$IV_SEPARATOR$encryptedString"
    }

    /**
     * Decrypts the [cipherText] (expected to be in the format "IV]Ciphertext").
     * Returns [DecryptionResult.Success] on success, [DecryptionResult.Invalid] when the data
     * or key is permanently invalid/corrupted, or [DecryptionResult.FailedTemporarily] when
     * a transient KeyStore error occurred.
     */
    fun decryptDetailed(cipherText: String): DecryptionResult {
        val parts = cipherText.split(IV_SEPARATOR)
        if (parts.size != 2) return DecryptionResult.Invalid
        
        val iv = try {
            Base64.decode(parts[0], Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return DecryptionResult.Invalid
        }
        
        val encrypted = try {
            Base64.decode(parts[1], Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return DecryptionResult.Invalid
        }
        
        val secretKey = try {
            getSecretKey()
        } catch (_: KeyPermanentlyInvalidatedException) {
            return DecryptionResult.Invalid
        } catch (_: UnrecoverableKeyException) {
            return DecryptionResult.Invalid
        } catch (_: UnrecoverableEntryException) {
            return DecryptionResult.Invalid
        } catch (_: Exception) {
            return DecryptionResult.FailedTemporarily
        }
        
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encrypted)
            DecryptionResult.Success(String(decryptedBytes, Charsets.UTF_8))
        } catch (_: AEADBadTagException) {
            // Authentication tag mismatch: encrypted with an older/different key or corrupted ciphertext.
            DecryptionResult.Invalid
        } catch (_: BadPaddingException) {
            DecryptionResult.Invalid
        } catch (_: IllegalBlockSizeException) {
            DecryptionResult.Invalid
        } catch (_: KeyPermanentlyInvalidatedException) {
            DecryptionResult.Invalid
        } catch (_: Exception) {
            // Transient KeyStore or system failure; do not treat as permanently corrupt.
            DecryptionResult.FailedTemporarily
        }
    }

    /**
     * Decrypts the [cipherText] (expected to be in the format "IV]Ciphertext").
     * Returns null if decryption fails.
     */
    fun decrypt(cipherText: String): String? {
        return when (val result = decryptDetailed(cipherText)) {
            is DecryptionResult.Success -> result.value
            else -> null
        }
    }
}
