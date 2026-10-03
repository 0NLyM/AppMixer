package com.nomixer.volume.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AppPreferencesStore(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val key = stringPreferencesKey("apps")

        private const val TAG = "NoMixer.AppPrefs"

        private val json = Json { ignoreUnknownKeys = true }
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    @Serializable
    private data class SerializedState(
        val values: MutableList<AppPreferences>,
        val indices: MutableMap<String, Int>,
        val systemSliderVisibility: MutableMap<String, Boolean> = mutableMapOf()
    )

    private val lock = Any()
    private var state = SerializedState(mutableListOf(), mutableMapOf())
    fun getSystemSliderVisible(id: String): Boolean {
        return synchronized(lock) { state.systemSliderVisibility[id] ?: true }
    }

    fun setSystemSliderVisible(id: String, value: Boolean) {
        val changed = synchronized(lock) {
            val oldValue = state.systemSliderVisibility[id] ?: true
            if (oldValue == value) {
                return@synchronized false
            }

            val updated = state.systemSliderVisibility.toMutableMap()
            updated[id] = value
            state = state.copy(systemSliderVisibility = updated)
            true
        }

        if (changed) {
            save()
        }
    }

    var systemSliderVisibility: Map<String, Boolean>
        get() = synchronized(lock) { state.systemSliderVisibility.toMap() }
        set(value) {
            val changed = synchronized(lock) {
                if (state.systemSliderVisibility == value) {
                    return@synchronized false
                }

                state = state.copy(systemSliderVisibility = value.toMutableMap())
                true
            }

            if (changed) {
                save()
            }
        }

    private val loaded = CompletableDeferred<Unit>()

    /**
     * Reads what was saved, once, as the store is made -- and runs [block]
     * when it has.
     *
     * Once, not for every change of the store: this store is the only writer
     * of its key, so what it reads back after a save is its own echo -- an
     * older picture than the live one, since the user may have dragged
     * further since. Swapping that in for the live state (as following the
     * store used to) gave every [App] a new preferences object and rewound
     * its volume to whatever the last write to land had held, mid-drag; and
     * writes made in between went to the old object, and were never saved.
     */
    fun whenLoaded(block: () -> Unit) {
        scope.launch {
            loaded.await()
            block()
        }
    }

    private suspend fun load() {
        try {
            val valueJson = dataStore.data.first()[key]
            if (valueJson != null) {
                val saved = json.decodeFromString<SerializedState>(valueJson)
                synchronized(lock) {
                    // Whatever was changed before the load finished wins
                    // over what was saved.
                    saved.systemSliderVisibility.putAll(state.systemSliderVisibility)
                    state = saved
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Can't read the saved app preferences", e)
        }
        loaded.complete(Unit)
    }

    fun getOrCreate(packageName: String): AppPreferences {
        synchronized(lock) {
            val index = state.indices[packageName]
            if (index != null) {
                return state.values[index]
            }

            val value = AppPreferences()
            state.indices[packageName] = state.values.size
            state.values.add(value)
            return value
        }
    }

    /**
     * Writes the state as it is by the time the write runs. One writer, so
     * writes land in order, and a burst of changes (a drag) is one write of
     * the latest rather than one per step racing each other.
     */
    private val saves = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            load()
            for (ignored in saves) {
                val encoded = synchronized(lock) { Json.encodeToString(state) }
                dataStore.edit { preferences ->
                    preferences[key] = encoded
                }
            }
        }
    }

    fun save() {
        saves.trySend(Unit)
    }
}
