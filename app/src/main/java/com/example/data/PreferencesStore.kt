package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.billCircleDataStore by preferencesDataStore(name = "billcircle_prefs")

class PreferencesStore(private val context: Context) {

    private val keyLanguage = stringPreferencesKey("preferred_language")
    private val keyDemoMode = booleanPreferencesKey("demo_mode_enabled")

    val languageFlow: Flow<AppLanguage> = context.billCircleDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[keyLanguage])
    }

    val demoModeFlow: Flow<Boolean> = context.billCircleDataStore.data.map { prefs ->
        prefs[keyDemoMode] ?: false
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.billCircleDataStore.edit { prefs ->
            prefs[keyLanguage] = language.code
        }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        context.billCircleDataStore.edit { prefs ->
            prefs[keyDemoMode] = enabled
        }
    }
}
