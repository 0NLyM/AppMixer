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
 * through Shizuku, unconditionally -- every call goes elevated, with no
 * plain-call-first attempt in between.
 *
 * This exists for the silent ringer mode. `AudioManager.setRingerMode`
 * refuses to switch a phone to silent unless the caller holds Do Not Disturb
 * access, which an ordinary app doesn't have -- so the ring/vibrate/silent
 * switch could only ever reach two of its three positions, and the third
 * threw. Routed through Shizuku the call is made with the privileges the
 * rest of the app already relies on.
 *
 * An earlier version of this proxy tried a plain, unprivileged call first
 * and only escalated to Shizuku on `SecurityException` or a failed
 * same-line readback, returning a verified boolean for the caller to branch
 * on. That branching -- absent from [NotificationManagerProxy], whose own
 * Do Not Disturb toggle has worked throughout -- is what several rounds of
 * "fix the ringer switch" changes kept circling around without ever
 * actually landing. This proxy now mirrors [NotificationManagerProxy]
 * exactly: one elevated setter, one elevated getter, no verification, no
 * fallback ladder. The caller reads the real mode back itself afterwards.
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
     * was.
     */
    private var wrapped = false

    init {
        ensureWrapped()
    }

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

    @EnableBinderProxy
    fun setRingerMode(mode: Int) {
        ensureWrapped()
        audioManager.ringerMode = mode
    }

    @EnableBinderProxy
    fun getRingerMode(): Int {
        ensureWrapped()
        return audioManager.ringerMode
    }
}
