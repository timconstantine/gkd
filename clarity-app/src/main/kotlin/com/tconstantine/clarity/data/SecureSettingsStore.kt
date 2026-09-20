package com.tconstantine.clarity.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tconstantine.clarity.ai.AnthropicModel

/** Stores the user's own Anthropic API key on-device, encrypted; nothing here ever leaves the device except the key itself, sent as an auth header directly to Anthropic. */
class SecureSettingsStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "clarity_secure_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var model: AnthropicModel
        get() = AnthropicModel.fromApiId(prefs.getString(KEY_MODEL, null))
        set(value) = prefs.edit().putString(KEY_MODEL, value.apiId).apply()

    private companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
    }
}
