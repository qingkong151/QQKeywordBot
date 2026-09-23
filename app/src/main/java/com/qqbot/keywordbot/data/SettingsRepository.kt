package com.qqbot.keywordbot.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置仓库：存储 OpenAI API Key、Base URL 等配置。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
    }

    val openAiApiKey: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.OPENAI_API_KEY] ?: "" }

    val openAiBaseUrl: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[Keys.OPENAI_BASE_URL] ?: "https://api.openai.com/v1" }

    suspend fun setOpenAiApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OPENAI_API_KEY] = key
        }
    }

    suspend fun setOpenAiBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OPENAI_BASE_URL] = url
        }
    }
}
