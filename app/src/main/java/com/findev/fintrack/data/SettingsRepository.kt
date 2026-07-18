package com.findev.fintrack.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Which color scheme the app uses, regardless of what the system is set to. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

private val THEME_MODE = stringPreferencesKey("theme_mode")
private val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")

/** App preferences that are not domain data and never sync. */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        // An unknown stored value means a downgrade or a hand-edited file; follow the system.
        prefs[THEME_MODE]?.let { stored ->
            ThemeMode.entries.firstOrNull { it.name == stored }
        } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_MODE] = mode.name }
    }

    /**
     * Off until asked for. This is the app's only network call, and turning it on by default
     * would put an offline-first app on the network without the user ever saying so.
     */
    val autoUpdateCheck: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_UPDATE_CHECK] ?: false
    }

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[AUTO_UPDATE_CHECK] = enabled }
    }
}
