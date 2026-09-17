package com.example.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai.core.AIProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

enum class AppTheme { SYSTEM, LIGHT, DARK }

class SettingsPreferences(private val context: Context) {
    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val AI_MODE_KEY = stringPreferencesKey("ai_processing_mode")

    val themeMode: Flow<AppTheme> = context.settingsDataStore.data.map { prefs ->
        try {
            AppTheme.valueOf(prefs[THEME_KEY] ?: AppTheme.DARK.name)
        } catch (e: Exception) {
            AppTheme.DARK
        }
    }

    val aiMode: Flow<AIProviderType> = context.settingsDataStore.data.map { prefs ->
        try {
            AIProviderType.valueOf(prefs[AI_MODE_KEY] ?: AIProviderType.AUTO.name)
        } catch (e: Exception) {
            AIProviderType.AUTO
        }
    }

    suspend fun setThemeMode(theme: AppTheme) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_KEY] = theme.name
        }
    }

    suspend fun setAiMode(mode: AIProviderType) {
        context.settingsDataStore.edit { prefs ->
            prefs[AI_MODE_KEY] = mode.name
        }
    }
}
