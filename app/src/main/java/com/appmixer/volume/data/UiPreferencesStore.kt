package com.appmixer.volume.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Persists [UiPreferences] in the same DataStore the app preferences use,
 * under its own key so the two can evolve independently.
 */
class UiPreferencesStore(private val dataStore: DataStore<Preferences>) {
    companion object {
        private const val TAG = "AppMixer.UiPreferences"

        private val key = stringPreferencesKey("ui")

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    fun track(onChange: (UiPreferences) -> Unit) {
        scope.launch {
            dataStore.data.collect { preferences ->
                val stored = preferences[key]
                val value = if (stored == null) {
                    UiPreferences()
                } else {
                    try {
                        json.decodeFromString<UiPreferences>(stored)
                    } catch (e: Exception) {
                        // A settings file we can't parse (downgrade, corruption)
                        // shouldn't take the whole app down -- fall back to defaults.
                        Log.e(TAG, "Can't read UI preferences, using defaults", e)
                        UiPreferences()
                    }
                }

                onChange(value)
            }
        }
    }

    fun save(value: UiPreferences) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[key] = json.encodeToString(value)
            }
        }
    }
}
