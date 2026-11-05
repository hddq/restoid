package io.github.hddq.restoid.data

import android.content.Context
import android.content.SharedPreferences
import io.github.hddq.restoid.util.CryptoHelper

class PasswordManager(private val context: Context) {

    private val lock = Any()
    private val temporaryPasswords = mutableMapOf<String, String>()

    private val prefs: SharedPreferences = context.getSharedPreferences("repository_passwords_native", Context.MODE_PRIVATE)

    init {
        // Clean up the old EncryptedSharedPreferences file if it exists.
        context.deleteSharedPreferences("repository_passwords")
    }

    fun savePassword(repositoryPath: String, password: String) {
        val encrypted = CryptoHelper.encrypt(password)
        synchronized(lock) {
            temporaryPasswords[repositoryPath] = password
            prefs.edit()
                .putString(repositoryPath, encrypted)
                .apply()
        }
    }

    fun savePasswordTemporary(repositoryPath: String, password: String) {
        synchronized(lock) {
            temporaryPasswords[repositoryPath] = password
        }
    }

    fun getPassword(repositoryPath: String): String? {
        val (temp, encrypted) = synchronized(lock) {
            temporaryPasswords[repositoryPath] to prefs.getString(repositoryPath, null)
        }
        if (temp != null) return temp
        if (encrypted == null) return null
        
        return when (val result = CryptoHelper.decryptDetailed(encrypted)) {
            is CryptoHelper.DecryptionResult.Success -> result.value
            is CryptoHelper.DecryptionResult.Invalid -> {
                // Cannot decrypt stored password (e.g. keystore changed after backup restore or corrupted).
                // Only clean up if the stored entry still matches the failed value to avoid deleting a concurrent user save.
                synchronized(lock) {
                    if (prefs.getString(repositoryPath, null) == encrypted) {
                        prefs.edit().remove(repositoryPath).apply()
                    }
                }
                null
            }
            is CryptoHelper.DecryptionResult.FailedTemporarily -> {
                // Temporary KeyStore / system error; do NOT delete the stored password, as it may be recoverable later.
                null
            }
        }
    }

    fun removePassword(repositoryPath: String) {
        synchronized(lock) {
            temporaryPasswords.remove(repositoryPath)
            prefs.edit()
                .remove(repositoryPath)
                .apply()
        }
    }

    fun hasPassword(repositoryPath: String): Boolean {
        synchronized(lock) {
            if (temporaryPasswords.containsKey(repositoryPath)) return true
        }
        return hasStoredPassword(repositoryPath)
    }

    /**
     * Checks whether an encrypted entry exists in storage, regardless of whether decryption succeeds.
     */
    fun hasStoredPasswordEntry(repositoryPath: String): Boolean {
        synchronized(lock) {
            return prefs.contains(repositoryPath)
        }
    }

    fun hasStoredPassword(repositoryPath: String): Boolean {
        val encrypted = synchronized(lock) {
            prefs.getString(repositoryPath, null)
        } ?: return false

        return when (CryptoHelper.decryptDetailed(encrypted)) {
            is CryptoHelper.DecryptionResult.Success -> true
            is CryptoHelper.DecryptionResult.Invalid -> {
                synchronized(lock) {
                    if (prefs.getString(repositoryPath, null) == encrypted) {
                        prefs.edit().remove(repositoryPath).apply()
                    }
                }
                false
            }
            is CryptoHelper.DecryptionResult.FailedTemporarily -> {
                // A stored entry is not an available credential while KeyStore is unavailable.
                // Callers that only need to know whether an entry exists use
                // hasStoredPasswordEntry() instead.
                false
            }
        }
    }

    fun removeStoredPassword(repositoryPath: String) {
        synchronized(lock) {
            temporaryPasswords.remove(repositoryPath)
            prefs.edit()
                .remove(repositoryPath)
                .apply()
        }
    }

    fun clearTemporaryPasswords() {
        synchronized(lock) {
            temporaryPasswords.clear()
        }
    }

    private fun sftpPasswordKey(repositoryKey: String): String {
        return "sftp_ssh_password::$repositoryKey"
    }

    private fun sftpKeyKey(repositoryKey: String): String {
        return "sftp_ssh_key::$repositoryKey"
    }

    private fun sftpKeyPassphraseKey(repositoryKey: String): String {
        return "sftp_ssh_key_passphrase::$repositoryKey"
    }

    private fun restUsernameKey(repositoryKey: String): String {
        return "rest_http_username::$repositoryKey"
    }

    private fun restPasswordKey(repositoryKey: String): String {
        return "rest_http_password::$repositoryKey"
    }

    private fun s3AccessKeyIdKey(repositoryKey: String): String {
        return "s3_access_key_id::$repositoryKey"
    }

    private fun s3SecretAccessKeyKey(repositoryKey: String): String {
        return "s3_secret_access_key::$repositoryKey"
    }

    fun saveSftpPassword(repositoryKey: String, password: String) {
        savePassword(sftpPasswordKey(repositoryKey), password)
    }

    fun saveSftpPasswordTemporary(repositoryKey: String, password: String) {
        savePasswordTemporary(sftpPasswordKey(repositoryKey), password)
    }

    fun getSftpPassword(repositoryKey: String): String? {
        return getPassword(sftpPasswordKey(repositoryKey))
    }

    fun hasSftpPassword(repositoryKey: String): Boolean {
        return hasPassword(sftpPasswordKey(repositoryKey))
    }

    fun hasStoredSftpPassword(repositoryKey: String): Boolean {
        return hasStoredPassword(sftpPasswordKey(repositoryKey))
    }

    fun hasStoredSftpPasswordEntry(repositoryKey: String): Boolean {
        return hasStoredPasswordEntry(sftpPasswordKey(repositoryKey))
    }

    fun removeSftpPassword(repositoryKey: String) {
        removePassword(sftpPasswordKey(repositoryKey))
    }

    fun removeStoredSftpPassword(repositoryKey: String) {
        removeStoredPassword(sftpPasswordKey(repositoryKey))
    }

    fun saveSftpKey(repositoryKey: String, key: String) {
        savePassword(sftpKeyKey(repositoryKey), key)
    }

    fun saveSftpKeyTemporary(repositoryKey: String, key: String) {
        savePasswordTemporary(sftpKeyKey(repositoryKey), key)
    }

    fun getSftpKey(repositoryKey: String): String? {
        return getPassword(sftpKeyKey(repositoryKey))
    }

    fun hasSftpKey(repositoryKey: String): Boolean {
        return hasPassword(sftpKeyKey(repositoryKey))
    }

    fun hasStoredSftpKey(repositoryKey: String): Boolean {
        return hasStoredPassword(sftpKeyKey(repositoryKey))
    }

    fun hasStoredSftpKeyEntry(repositoryKey: String): Boolean {
        return hasStoredPasswordEntry(sftpKeyKey(repositoryKey))
    }

    fun removeSftpKey(repositoryKey: String) {
        removePassword(sftpKeyKey(repositoryKey))
    }

    fun removeStoredSftpKey(repositoryKey: String) {
        removeStoredPassword(sftpKeyKey(repositoryKey))
    }

    fun saveSftpKeyPassphrase(repositoryKey: String, passphrase: String) {
        savePassword(sftpKeyPassphraseKey(repositoryKey), passphrase)
    }

    fun saveSftpKeyPassphraseTemporary(repositoryKey: String, passphrase: String) {
        savePasswordTemporary(sftpKeyPassphraseKey(repositoryKey), passphrase)
    }

    fun getSftpKeyPassphrase(repositoryKey: String): String? {
        return getPassword(sftpKeyPassphraseKey(repositoryKey))
    }

    fun hasSftpKeyPassphrase(repositoryKey: String): Boolean {
        return hasPassword(sftpKeyPassphraseKey(repositoryKey))
    }

    fun hasStoredSftpKeyPassphrase(repositoryKey: String): Boolean {
        return hasStoredPassword(sftpKeyPassphraseKey(repositoryKey))
    }

    fun hasStoredSftpKeyPassphraseEntry(repositoryKey: String): Boolean {
        return hasStoredPasswordEntry(sftpKeyPassphraseKey(repositoryKey))
    }

    fun removeSftpKeyPassphrase(repositoryKey: String) {
        removePassword(sftpKeyPassphraseKey(repositoryKey))
    }

    fun removeStoredSftpKeyPassphrase(repositoryKey: String) {
        removeStoredPassword(sftpKeyPassphraseKey(repositoryKey))
    }

    fun saveRestUsername(repositoryKey: String, username: String) {
        savePassword(restUsernameKey(repositoryKey), username)
    }

    fun saveRestUsernameTemporary(repositoryKey: String, username: String) {
        savePasswordTemporary(restUsernameKey(repositoryKey), username)
    }

    fun getRestUsername(repositoryKey: String): String? {
        return getPassword(restUsernameKey(repositoryKey))
    }

    fun hasRestUsername(repositoryKey: String): Boolean {
        return hasPassword(restUsernameKey(repositoryKey))
    }

    fun hasStoredRestUsername(repositoryKey: String): Boolean {
        return hasStoredPassword(restUsernameKey(repositoryKey))
    }

    fun hasStoredRestUsernameEntry(repositoryKey: String): Boolean {
        return hasStoredPasswordEntry(restUsernameKey(repositoryKey))
    }

    fun removeRestUsername(repositoryKey: String) {
        removePassword(restUsernameKey(repositoryKey))
    }

    fun removeStoredRestUsername(repositoryKey: String) {
        removeStoredPassword(restUsernameKey(repositoryKey))
    }

    fun saveRestPassword(repositoryKey: String, password: String) {
        savePassword(restPasswordKey(repositoryKey), password)
    }

    fun saveRestPasswordTemporary(repositoryKey: String, password: String) {
        savePasswordTemporary(restPasswordKey(repositoryKey), password)
    }

    fun getRestPassword(repositoryKey: String): String? {
        return getPassword(restPasswordKey(repositoryKey))
    }

    fun hasRestPassword(repositoryKey: String): Boolean {
        return hasPassword(restPasswordKey(repositoryKey))
    }

    fun hasStoredRestPassword(repositoryKey: String): Boolean {
        return hasStoredPassword(restPasswordKey(repositoryKey))
    }

    fun hasStoredRestPasswordEntry(repositoryKey: String): Boolean {
        return hasStoredPasswordEntry(restPasswordKey(repositoryKey))
    }

    fun removeRestPassword(repositoryKey: String) {
        removePassword(restPasswordKey(repositoryKey))
    }

    fun removeStoredRestPassword(repositoryKey: String) {
        removeStoredPassword(restPasswordKey(repositoryKey))
    }

    fun saveS3AccessKeyId(repositoryKey: String, accessKeyId: String) {
        savePassword(s3AccessKeyIdKey(repositoryKey), accessKeyId)
    }

    fun saveS3AccessKeyIdTemporary(repositoryKey: String, accessKeyId: String) {
        savePasswordTemporary(s3AccessKeyIdKey(repositoryKey), accessKeyId)
    }

    fun getS3AccessKeyId(repositoryKey: String): String? {
        return getPassword(s3AccessKeyIdKey(repositoryKey))
    }

    fun hasS3AccessKeyId(repositoryKey: String): Boolean {
        return hasPassword(s3AccessKeyIdKey(repositoryKey))
    }

    fun hasStoredS3AccessKeyId(repositoryKey: String): Boolean {
        return hasStoredPassword(s3AccessKeyIdKey(repositoryKey))
    }

    fun hasStoredS3AccessKeyIdEntry(repositoryKey: String): Boolean {
        return hasStoredPasswordEntry(s3AccessKeyIdKey(repositoryKey))
    }

    fun removeS3AccessKeyId(repositoryKey: String) {
        removePassword(s3AccessKeyIdKey(repositoryKey))
    }

    fun removeStoredS3AccessKeyId(repositoryKey: String) {
        removeStoredPassword(s3AccessKeyIdKey(repositoryKey))
    }

    fun saveS3SecretAccessKey(repositoryKey: String, secretAccessKey: String) {
        savePassword(s3SecretAccessKeyKey(repositoryKey), secretAccessKey)
    }

    fun saveS3SecretAccessKeyTemporary(repositoryKey: String, secretAccessKey: String) {
        savePasswordTemporary(s3SecretAccessKeyKey(repositoryKey), secretAccessKey)
    }

    fun getS3SecretAccessKey(repositoryKey: String): String? {
        return getPassword(s3SecretAccessKeyKey(repositoryKey))
    }

    fun hasS3SecretAccessKey(repositoryKey: String): Boolean {
        return hasPassword(s3SecretAccessKeyKey(repositoryKey))
    }

    fun hasStoredS3SecretAccessKey(repositoryKey: String): Boolean {
        return hasStoredPassword(s3SecretAccessKeyKey(repositoryKey))
    }

    fun hasStoredS3SecretAccessKeyEntry(repositoryKey: String): Boolean {
        return hasStoredPasswordEntry(s3SecretAccessKeyKey(repositoryKey))
    }

    fun removeS3SecretAccessKey(repositoryKey: String) {
        removePassword(s3SecretAccessKeyKey(repositoryKey))
    }

    fun removeStoredS3SecretAccessKey(repositoryKey: String) {
        removeStoredPassword(s3SecretAccessKeyKey(repositoryKey))
    }
}
