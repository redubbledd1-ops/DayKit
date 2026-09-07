package com.dd.daykit

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Notificatie-host voor één extra-timer-slot zodat [androidx.core.app.NotificationCompat.Builder.setColorized]
 * effect heeft (FGS-koppeling). Timer-logica blijft in [TimerPopupForegroundService].
 */
abstract class ExtraTimerForegroundServiceBase : Service() {

    protected abstract val slotIndex: Int

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            PopupNotificationFoundation.ensureExtraTimerChannel(this, slotIndex)
            Log.i(tag, "SERVICE_LIFECYCLE onCreate slotIndex=$slotIndex")
        }.onFailure { error ->
            Log.e(tag, "SERVICE_LIFECYCLE onCreate failed slotIndex=$slotIndex", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(tag, "SERVICE_START action=$action slotIndex=$slotIndex startId=$startId")
        return when (action) {
            ACTION_STOP -> {
                shutdown(reason = "stop_action")
                START_NOT_STICKY
            }
            ACTION_SYNC, null -> {
                runCatching { syncForegroundNotification() }.onFailure { error ->
                    Log.e(tag, "EXTRA_FGS_SYNC_FAILED slotIndex=$slotIndex", error)
                    shutdown(reason = "sync_failure")
                }
                START_STICKY
            }
            else -> {
                Log.i(tag, "SERVICE_RECREATED action=$action slotIndex=$slotIndex")
                runCatching { syncForegroundNotification() }.onFailure { error ->
                    Log.e(tag, "EXTRA_FGS_RECREATE_FAILED slotIndex=$slotIndex", error)
                    shutdown(reason = "recreate_failure")
                }
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.i(tag, "SERVICE_LIFECYCLE onDestroy slotIndex=$slotIndex")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(tag, "SERVICE_LIFECYCLE onTaskRemoved slotIndex=$slotIndex — service continues")
        super.onTaskRemoved(rootIntent)
    }

    private fun syncForegroundNotification() {
        ExtraTimerManager.init(this)
        val slot = ExtraTimerManager.timers.getOrNull(slotIndex)
        if (slot == null) {
            Log.i(tag, "EXTRA_FGS_NO_SLOT slotIndex=$slotIndex — shutting down")
            shutdown(reason = "slot_missing")
            return
        }
        val shouldShow = slot.state == GlobalTimerManager.TimerState.RUNNING ||
            slot.state == GlobalTimerManager.TimerState.PAUSED
        if (!shouldShow) {
            Log.i(tag, "EXTRA_FGS_NO_ACTIVE_SLOT slotIndex=$slotIndex — shutting down")
            shutdown(reason = "slot_inactive")
            return
        }
        val notification = ExtraTimerPopupNotifications.build(this, slot, slotIndex)
        val notificationId = ExtraTimerPopupNotifications.notificationId(slotIndex)
        PopupNotificationFoundation.startForeground(this, notificationId, notification)
        Log.i(tag, "EXTRA_FGS_STARTED slotIndex=$slotIndex notificationId=$notificationId")
    }

    private fun shutdown(reason: String) {
        Log.i(tag, "SERVICE_LIFECYCLE shutdown slotIndex=$slotIndex reason=$reason")
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private val tag: String
        get() = "ExtraTimerFgs$slotIndex"

    companion object {
        const val ACTION_SYNC = "com.dd.daykit.extra_timer_fgs.SYNC"
        const val ACTION_STOP = "com.dd.daykit.extra_timer_fgs.STOP"

        fun serviceClassForIndex(index: Int): Class<out Service>? = when (index) {
            0 -> ExtraTimerForegroundService0::class.java
            1 -> ExtraTimerForegroundService1::class.java
            else -> null
        }

        fun start(context: Context, index: Int) {
            val clazz = serviceClassForIndex(index)
                ?: throw IllegalArgumentException("No extra timer FGS for index=$index")
            val intent = Intent(context, clazz).apply { action = ACTION_SYNC }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, index: Int) {
            val clazz = serviceClassForIndex(index) ?: return
            context.startService(Intent(context, clazz).apply { action = ACTION_STOP })
        }
    }
}

class ExtraTimerForegroundService0 : ExtraTimerForegroundServiceBase() {
    override val slotIndex: Int = 0
}

class ExtraTimerForegroundService1 : ExtraTimerForegroundServiceBase() {
    override val slotIndex: Int = 1
}
