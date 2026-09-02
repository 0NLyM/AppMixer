package com.appmixer.volume.compose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

private const val TAG = "AppMixer.Broadcast"

/**
 * Listens to system broadcasts for as long as the calling composable is in
 * the tree.
 *
 * Registration goes through the *application* context: registering against
 * an activity or service context means the framework may tear the receiver
 * down with that context before Compose gets to dispose this effect, and
 * the unregister that follows then throws
 * `IllegalArgumentException: Receiver not registered`. Both calls are also
 * guarded, so a receiver that is somehow already gone can't take the app
 * down on the way out.
 */
@Composable
fun SystemBroadcastEffect(
    vararg actions: String,
    onReceive: (Intent) -> Unit
) {
    val context = LocalContext.current
    val currentOnReceive by rememberUpdatedState(onReceive)

    // Arrays have identity equality, so key on the action list itself.
    DisposableEffect(context, actions.joinToString(",")) {
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    currentOnReceive(intent)
                }
            }
        }

        val registered = try {
            appContext.registerReceiver(
                receiver,
                IntentFilter().apply { actions.forEach(::addAction) },
                Context.RECEIVER_NOT_EXPORTED
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Can't register receiver for ${actions.joinToString()}", e)
            false
        }

        onDispose {
            if (registered) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Receiver was already unregistered", e)
                }
            }
        }
    }
}
