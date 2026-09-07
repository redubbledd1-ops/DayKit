package com.dd.daykit

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ButtonlessNotificationService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            PopupNotificationFoundation.ensureCalendarAlarmChannel(this)
            Log.i(TAG, "SERVICE_LIFECYCLE onCreate popupType=buttonless")
        }.onFailure { error ->
            Log.e(TAG, "onCreate ensureChannel failed", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "SERVICE_START action=$action startId=$startId")
        return when (action) {
            ACTION_SYNC -> {
                syncWithAlarm(
                    intent?.getStringExtra(EXTRA_ALARM_JSON).orEmpty(),
                    intent?.getStringExtra(EXTRA_REASON).orEmpty()
                )
                START_STICKY
            }
            ACTION_STOP_SERVICE -> {
                shutdown(removeNotification = true, reason = intent?.getStringExtra(EXTRA_REASON).orEmpty())
                START_NOT_STICKY
            }
            else -> {
                val restoredJson = readPersistedAlarmJson()
                if (!restoredJson.isNullOrEmpty()) {
                    Log.i(TAG, "SERVICE_RECREATED — restoring from persisted alarm JSON")
                    syncWithAlarm(restoredJson, "oom_restore")
                } else {
                    shutdown(removeNotification = true, reason = "system_recreate_no_data")
                }
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "SERVICE_LIFECYCLE onDestroy popupType=buttonless")
        tickerJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "SERVICE_LIFECYCLE onTaskRemoved popupType=buttonless — service continues")
        super.onTaskRemoved(rootIntent)
    }

    private fun persistAlarmJson(alarmJson: String) {
        runCatching {
            getSharedPreferences("buttonless_notification_state", Context.MODE_PRIVATE)
                .edit().putString("buttonless_alarm_json", alarmJson).apply()
        }
    }

    private fun clearPersistedAlarmJson() {
        runCatching {
            getSharedPreferences("buttonless_notification_state", Context.MODE_PRIVATE)
                .edit().remove("buttonless_alarm_json").apply()
        }
    }

    private fun readPersistedAlarmJson(): String? =
        runCatching {
            getSharedPreferences("buttonless_notification_state", Context.MODE_PRIVATE)
                .getString("buttonless_alarm_json", null)
        }.getOrNull()

    private fun syncWithAlarm(alarmJson: String, reason: String) {
        persistAlarmJson(alarmJson)
        if (alarmJson.isEmpty()) {
            shutdown(removeNotification = true, reason = "empty_alarm_json_$reason")
            return
        }
        val alarm = runCatching { Json.decodeFromString<AlarmItem>(alarmJson) }.getOrElse {
            Log.e(TAG, "decode alarm failed", it)
            shutdown(removeNotification = true, reason = "bad_json")
            return
        }

        if (!SettingsManager.getButtonlessNotificationEnabled(this)) {
            shutdown(removeNotification = true, reason = "buttonless_disabled")
            return
        }

        val now = System.currentTimeMillis()
        val windowMs = SettingsManager.getButtonlessNotificationMinutes(this).coerceAtLeast(120) * 60_000L
        if (alarm.epochMillis <= now) {
            shutdown(removeNotification = true, reason = "alarm_in_past")
            return
        }
        val remainingMs = alarm.epochMillis - now
        if (remainingMs > windowMs) {
            shutdown(removeNotification = true, reason = "outside_buttonless_window")
            return
        }

        // Er mag maar 1 melding tegelijk zijn voor 1 alarm: zodra we binnen het venster van de
        // Stop-knop melding vallen, heeft die altijd voorrang en blijft deze buttonless melding uit.
        if (isWithinStopPopupWindow(alarm, now)) {
            shutdown(removeNotification = true, reason = "superseded_by_stop_popup")
            return
        }

        LanguageManager.init(applicationContext)
        tickerJob?.cancel()

        val initial = buildNotification(alarm)
        PopupNotificationFoundation.startForeground(this, NOTIFICATION_ID, initial)

        tickerJob = scope.launch {
            while (isActive) {
                delay(60_000L)
                if (!SettingsManager.getButtonlessNotificationEnabled(this@ButtonlessNotificationService)) {
                    shutdown(removeNotification = true, reason = "buttonless_disabled_mid_run")
                    break
                }
                val n = System.currentTimeMillis()
                if (alarm.epochMillis <= n) {
                    shutdown(removeNotification = true, reason = "alarm_time_reached")
                    break
                }
                if (isWithinStopPopupWindow(alarm, n)) {
                    shutdown(removeNotification = true, reason = "superseded_by_stop_popup_mid_run")
                    break
                }
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(alarm))
            }
        }
    }

    /** Zit [now] binnen het venster van de Stop-knop melding ([SettingsManager.getCalendarPopupWindowMinutes])? */
    private fun isWithinStopPopupWindow(alarm: AlarmItem, now: Long): Boolean {
        if (!SettingsManager.getCalendarPopupLast30Min(this)) return false
        val stopPopupWindowMs = SettingsManager.getCalendarPopupWindowMinutes(this).coerceAtLeast(1) * 60_000L
        val remainingMs = alarm.epochMillis - now
        return remainingMs in 0..stopPopupWindowMs
    }

    private fun buildNotification(alarm: AlarmItem): android.app.Notification {
        val colors = PopupNotificationFoundation.resolveThemeColors(this)
        val generic = LanguageManager.getString("screen_agenda_alarm")
        val eventTitle = alarm.label.trim().takeIf { it.isNotEmpty() }
        val subtitle = eventTitle ?: generic
        val title = AgendaAlarmNotificationCopy.buttonlessCountdownTitle(this, alarm.epochMillis)

        return PopupNotificationFoundation
            .alarmTrayCompactBuilder(this, CHANNEL_ID, colors.backgroundColor)
            .setOngoing(true)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSilent(true)
            .build()
    }

    private fun shutdown(removeNotification: Boolean, reason: String) {
        Log.i(TAG, "SERVICE_SHUTDOWN reason=$reason removeNotification=$removeNotification")
        clearPersistedAlarmJson()
        tickerJob?.cancel()
        tickerJob = null
        if (removeNotification) {
            runCatching {
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
            }
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    companion object {
        private const val TAG = "ButtonlessNotifService"
        const val EXTRA_ALARM_JSON = "alarm_json"
        const val EXTRA_REASON = "reason"

        internal const val ACTION_SYNC = "com.dd.daykit.buttonless_notif.SYNC"
        private const val ACTION_STOP_SERVICE = "com.dd.daykit.buttonless_notif.STOP_SERVICE"

        private const val CHANNEL_ID = PopupNotificationFoundation.CALENDAR_ALARM_CHANNEL_ID
        private const val NOTIFICATION_ID = 90403

        fun startWithAlarmJson(context: Context, alarmJson: String, reason: String) {
            val app = context.applicationContext
            val i = Intent(app, ButtonlessNotificationService::class.java).apply {
                action = ACTION_SYNC
                putExtra(EXTRA_ALARM_JSON, alarmJson)
                putExtra(EXTRA_REASON, reason)
            }
            runCatching { ContextCompat.startForegroundService(app, i) }
                .onFailure { Log.e(TAG, "startWithAlarmJson failed reason=$reason", it) }
        }

        fun stopNotification(context: Context, reason: String) {
            val app = context.applicationContext
            val i = Intent(app, ButtonlessNotificationService::class.java).apply {
                action = ACTION_STOP_SERVICE
                putExtra(EXTRA_REASON, reason)
            }
            runCatching { app.startService(i) }.onFailure { Log.w(TAG, "stopNotification failed", it) }
        }
    }
}
