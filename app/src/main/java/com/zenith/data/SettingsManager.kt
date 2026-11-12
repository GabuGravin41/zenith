package com.zenith.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    
    companion object {
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_PROVIDER_URL = stringPreferencesKey("ai_provider_url")
        // Default key — user can set in Settings
        private const val DEFAULT_API_KEY = ""
        private const val DEFAULT_PROVIDER_URL = "https://openrouter.ai/api/v1"
    }

    val aiApiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AI_API_KEY] ?: DEFAULT_API_KEY
    }

    val aiProviderUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AI_PROVIDER_URL] ?: DEFAULT_PROVIDER_URL
    }

    suspend fun saveAiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_API_KEY] = key
        }
    }

    suspend fun saveAiProviderUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_PROVIDER_URL] = url
        }
    }
}
