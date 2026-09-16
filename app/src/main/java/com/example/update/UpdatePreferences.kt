package com.example.update

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.updateDataStore by preferencesDataStore(name = "update_prefs")

class UpdatePreferences(private val context: Context) {
    private val AUTO_UPDATE_KEY = booleanPreferencesKey("auto_update_enabled")
    private val LAST_CHECK_KEY = longPreferencesKey("last_check_time")

    val autoUpdateEnabled: Flow<Boolean> = context.updateDataStore.data.map { it[AUTO_UPDATE_KEY] ?: false }
    val lastCheckTime: Flow<Long> = context.updateDataStore.data.map { it[LAST_CHECK_KEY] ?: 0L }

    suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        context.updateDataStore.edit { it[AUTO_UPDATE_KEY] = enabled }
    }

    suspend fun setLastCheckTime(time: Long) {
        context.updateDataStore.edit { it[LAST_CHECK_KEY] = time }
    }
}
