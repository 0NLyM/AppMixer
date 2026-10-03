package com.nomixer.volume.data

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * What an app was set to must stay what it was set to: the store neither
 * swaps the live preferences for an older copy of them nor loses writes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class AppPreferencesStoreTest {
    /**
     * One DataStore per test, as the app has one: DataStore refuses a second
     * on the same file. A new [AppPreferencesStore] on it is a restart.
     */
    private val dataStore = PreferenceDataStoreFactory.create {
        File.createTempFile("apps", ".preferences_pb").also {
            it.delete()
            it.deleteOnExit()
        }
    }

    private fun storeOn(dataStore: DataStore<Preferences>): AppPreferencesStore =
        AppPreferencesStore(dataStore).also(::awaitLoaded)

    private fun awaitLoaded(store: AppPreferencesStore) = runBlocking {
        val loaded = CompletableDeferred<Unit>()
        store.whenLoaded { loaded.complete(Unit) }
        withTimeout(5_000) { loaded.await() }
    }

    /** Waits until a fresh store reading the same file sees [volume] for [packageName]. */
    private fun assertSaved(packageName: String, volume: Float) {
        val deadline = System.currentTimeMillis() + 5_000
        var seen = Float.NaN
        while (System.currentTimeMillis() < deadline) {
            seen = storeOn(dataStore).getOrCreate(packageName).volume
            if (seen == volume) {
                return
            }
            Thread.sleep(50)
        }
        assertEquals(volume, seen, 0f)
    }

    @Test
    fun theLiveObjectSurvivesItsOwnSaves() {
        val store = storeOn(dataStore)
        val live = store.getOrCreate("a.b")

        // A drag: many changes, each saved, the last one the one that counts.
        for (step in 1..40) {
            live.volume = step / 100f
            store.save()
        }
        Thread.sleep(500)

        assertSame(live, store.getOrCreate("a.b"))
        assertEquals(0.4f, live.volume, 0f)
        assertSaved("a.b", 0.4f)
    }

    @Test
    fun aVolumeSurvivesARestart() {
        val first = storeOn(dataStore)
        first.getOrCreate("a.b").volume = 0.25f
        first.getOrCreate("c.d").hidden = true
        first.save()
        assertSaved("a.b", 0.25f)

        val second = storeOn(dataStore)
        assertEquals(0.25f, second.getOrCreate("a.b").volume, 0f)
        assertEquals(true, second.getOrCreate("c.d").hidden)
    }

    @Test
    fun changingASliderSettingBeforeTheLoadDoesNotWipeTheSavedVolumes() {
        val first = storeOn(dataStore)
        first.getOrCreate("a.b").volume = 0.6f
        first.save()
        assertSaved("a.b", 0.6f)

        val second = AppPreferencesStore(dataStore)
        second.setSystemSliderVisible("media", false)
        awaitLoaded(second)

        assertEquals(0.6f, second.getOrCreate("a.b").volume, 0f)
        assertEquals(false, second.getSystemSliderVisible("media"))
    }
}
