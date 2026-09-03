package com.nomixer.volume.system

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.nomixer.volume.EnableBinderProxy
import com.nomixer.volume.ToggleableBinderProxy
import org.joor.Reflect
import java.util.WeakHashMap

private const val TAG = "NoMixer.AudioProxy"

/**
 * Privileged access to the audio service, the same way
 * [NotificationManagerProxy] gets at the notification one: the service's
 * binder is wrapped so calls made inside [EnableBinderProxy] methods travel
 * through Shizuku.
 *
 * This exists for the silent ringer mode. `AudioManager.setRingerMode`
 * refuses to switch a phone to silent unless the caller holds Do Not Disturb
 * access, which an ordinary app doesn't have -- so the ring/vibrate/silent
 * switch could only ever reach two of its three positions, and the third
 * threw. Routed through Shizuku the call is made with the privileges the
 * rest of the app already relies on.
 */
class AudioManagerProxy private constructor(context: Context) {
    companion object {
        private val cache = WeakHashMap<Context, AudioManagerProxy>()

        operator fun invoke(context: Context): AudioManagerProxy {
            return cache.getOrPut(context) { AudioManagerProxy(context) }
        }
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)!!

    init {
        // Failing to wrap isn't fatal: the unprivileged path below still
        // works for ring and vibrate, which is what an app can set anyway.
        try {
            val service = Reflect.onClass(AudioManager::class.java).call("getService").get<Any>()
            ToggleableBinderProxy.wrap(service)
        } catch (e: Exception) {
            Log.w(TAG, "Can't wrap the audio service binder", e)
        }
    }

    // Not private: the aspect that turns the binder proxy on rewrites
    // annotated methods, and the other proxies in this package keep theirs
    // visible for the same reason.
    @EnableBinderProxy
    fun setRingerModeElevated(mode: Int) {
        audioManager.ringerMode = mode
    }

    /**
     * Sets the ringer mode, elevated if it has to be. Returns whether the
     * phone actually ended up in [mode] -- the caller decides what to show,
     * rather than being told a change happened that didn't.
     */
    fun setRingerMode(mode: Int): Boolean {
        try {
            audioManager.ringerMode = mode
            if (audioManager.ringerMode == mode) {
                return true
            }
        } catch (e: SecurityException) {
            Log.i(TAG, "Plain ringer mode change to $mode refused, retrying elevated", e)
        }

        return try {
            setRingerModeElevated(mode)
            audioManager.ringerMode == mode
        } catch (e: Exception) {
            Log.w(TAG, "Can't set ringer mode $mode", e)
            false
        }
    }
}
