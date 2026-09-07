package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Central visibility gate for in-app banners/strips (not Android notifications or fullscreen alarm UI).
 */
object InAppNotificationsVisibility {

    const val ACTION_CHANGED = "com.dd.daykit.IN_APP_NOTIFICATIONS_CHANGED"

    internal val enabledState = mutableStateOf(SettingsManager.DEFAULT_IN_APP_NOTIFICATIONS_ENABLED)

    fun readEnabled(context: Context): Boolean =
        SettingsManager.getInAppNotificationsEnabled(context.applicationContext)

    fun syncFromPrefs(context: Context) {
        enabledState.value = readEnabled(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        enabledState.value = enabled
        context.applicationContext.sendBroadcast(
            Intent(ACTION_CHANGED).setPackage(context.packageName),
        )
    }
}

@Composable
fun rememberInAppNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context) {
        InAppNotificationsVisibility.syncFromPrefs(context)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                InAppNotificationsVisibility.syncFromPrefs(ctx)
            }
        }
        val filter = IntentFilter(InAppNotificationsVisibility.ACTION_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                InAppNotificationsVisibility.syncFromPrefs(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return InAppNotificationsVisibility.enabledState.value
}
