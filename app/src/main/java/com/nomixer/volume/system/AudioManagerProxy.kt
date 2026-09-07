package com.nomixer.volume.system

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.nomixer.volume.EnableBinderProxy
import com.nomixer.volume.ToggleableBinderProxy
import org.joor.Reflect
import rikka.shizuku.Shizuku
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

    /**
     * Whether the audio service's binder has actually been wrapped yet --
     * set only on success, so a failed attempt (Shizuku not connected or
     * not yet authorized at the time) gets retried the next time a
     * privileged call is actually needed, rather than being given up on
     * for the rest of the process's life the way a one-shot `init` attempt
     * was. Wrapping itself doesn't need Shizuku connected -- it's a local
     * reflection change to the binder proxy, only actually exercised later
     * when a call is made with [ToggleableBinderProxy.enabled] on -- so
     * this only ever needs a second try if the reflection itself failed,
     * not because of Shizuku's own connection timing.
     */
    private var wrapped = false

    init {
        ensureWrapped()
    }

    // Failing to wrap isn't fatal: the unprivileged path below still works
    // for ring and vibrate, which is what an app can set anyway.
    private fun ensureWrapped() {
        if (wrapped) {
            return
        }

        try {
            val service = Reflect.onClass(AudioManager::class.java).call("getService").get<Any>()
            ToggleableBinderProxy.wrap(service)
            wrapped = true
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

        ensureWrapped()

        return try {
            setRingerModeElevated(mode)
            val landed = audioManager.ringerMode == mode
            if (!landed) {
                Log.w(
                    TAG,
                    "Elevated ringer mode change to $mode didn't land " +
                        "(binder wrapped: $wrapped, Shizuku alive: ${Shizuku.pingBinder()})"
                )
            }
            landed
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Can't set ringer mode $mode (binder wrapped: $wrapped, " +
                    "Shizuku alive: ${Shizuku.pingBinder()})",
                e
            )
            false
        }
    }
}
