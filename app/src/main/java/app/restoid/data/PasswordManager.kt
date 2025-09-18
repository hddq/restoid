package app.restoid.data

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

    fun clearTemporaryPasswords() {
        temporaryPasswords.clear()
    }
}