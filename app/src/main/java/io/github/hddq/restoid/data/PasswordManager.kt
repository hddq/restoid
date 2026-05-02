package io.github.hddq.restoid.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PasswordManager(private val context: Context) {

    private val temporaryPasswords = mutableMapOf<String, String>()

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "repository_passwords",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePassword(repositoryPath: String, password: String) {
        encryptedPrefs.edit()
            .putString(repositoryPath, password)
            .apply()
    }

    fun savePasswordTemporary(repositoryPath: String, password: String) {
        temporaryPasswords[repositoryPath] = password
    }

    fun getPassword(repositoryPath: String): String? {
        return temporaryPasswords[repositoryPath] ?: encryptedPrefs.getString(repositoryPath, null)
    }

    fun removePassword(repositoryPath: String) {
        // Also remove from temporary cache if it exists there
        temporaryPasswords.remove(repositoryPath)
        encryptedPrefs.edit()
            .remove(repositoryPath)
            .apply()
    }

    fun hasPassword(repositoryPath: String): Boolean {
        return temporaryPasswords.containsKey(repositoryPath) || encryptedPrefs.contains(repositoryPath)
    }

    fun hasStoredPassword(repositoryPath: String): Boolean {
        return encryptedPrefs.contains(repositoryPath)
    }

    fun removeStoredPassword(repositoryPath: String) {
        encryptedPrefs.edit()
            .remove(repositoryPath)
            .apply()
    }

    fun clearTemporaryPasswords() {
        temporaryPasswords.clear()
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

    fun removeS3SecretAccessKey(repositoryKey: String) {
        removePassword(s3SecretAccessKeyKey(repositoryKey))
    }

    fun removeStoredS3SecretAccessKey(repositoryKey: String) {
        removeStoredPassword(s3SecretAccessKeyKey(repositoryKey))
    }
}
