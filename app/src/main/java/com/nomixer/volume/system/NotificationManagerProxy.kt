package com.nomixer.volume.system

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.nomixer.volume.EnableBinderProxy
import com.nomixer.volume.ToggleableBinderProxy
import org.joor.Reflect
import java.util.WeakHashMap

class NotificationManagerProxy private constructor(context: Context) {
    companion object {
        private val cache = WeakHashMap<Context, NotificationManagerProxy>()

        operator fun invoke(context: Context): NotificationManagerProxy {
            return cache.getOrPut(context) { NotificationManagerProxy(context) }
        }
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)!!

    init {
        val service = Reflect.onClass(NotificationManager::class.java).call("getService").get<Any>()
        ToggleableBinderProxy.wrap(service)
    }

    @EnableBinderProxy
    fun getCurrentInterruptionFilter(): Int {
        return notificationManager.currentInterruptionFilter
    }

    @EnableBinderProxy
    fun setInterruptionFilter(filter: Int) {
        try {
            notificationManager.setInterruptionFilter(filter)
        } catch (e: SecurityException) {
            // Without Shizuku this is an app's own call, and changing Do Not
            // Disturb is a thing the platform lets only an app with policy
            // access do: refused, and left as it was.
            Log.w("NoMixer.Notification", "Do Not Disturb change refused", e)
        }
    }
}
