package org.dslul.openboard.inputmethod.latin.ai

import android.content.Context
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.dslul.openboard.inputmethod.latin.BuildConfig

object ApiKeyProvider {
    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_OPENAI = "OPENAI_API_KEY"

    fun getOpenAiKey(context: Context): String? {
        // 1) Dev override from BuildConfig (read from local.properties in debug only)
        if (BuildConfig.DEBUG && !BuildConfig.OPENAI_API_KEY_DEV.isNullOrBlank()) {
            return BuildConfig.OPENAI_API_KEY_DEV
        }

        // 2) EncryptedSharedPreferences (prod path)
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val prefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.getString(KEY_OPENAI, null)
        } catch (t: Throwable) {
            null
        }
    }

    fun saveOpenAiKey(context: Context, key: String) {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val prefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.edit().putString(KEY_OPENAI, key).apply()
        } catch (_: Throwable) { }
    }
}


