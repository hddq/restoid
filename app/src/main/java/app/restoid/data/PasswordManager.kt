package app.restoid.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PasswordManager(private val context: Context) {
    
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
    
    fun getPassword(repositoryPath: String): String? {
        return encryptedPrefs.getString(repositoryPath, null)
    }
    
    fun removePassword(repositoryPath: String) {
        encryptedPrefs.edit()
            .remove(repositoryPath)
            .apply()
    }
    
    fun hasPassword(repositoryPath: String): Boolean {
        return encryptedPrefs.contains(repositoryPath)
    }
}