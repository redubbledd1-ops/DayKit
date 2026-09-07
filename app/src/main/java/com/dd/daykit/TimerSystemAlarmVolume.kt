package com.dd.daykit

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlin.math.roundToInt

/**
 * Schakelaar "Mobiel geluid volume gebruiken" in [TimerSettingsScreen] / [TimerActivity]:
 * - **UIT (false):** custom volume — slider actief; deze functie zet [STREAM_ALARM] naar de slider.
 * - **AAN (true):** mobiel/systeem — slider uit; **niet** aanroepen; alarm volgt hardware/systeem-alarmvolume.
 */
object TimerSystemAlarmVolume {

    private const val TAG = "TimerSystemAlarmVolume"

    fun syncAlarmStreamFromSlider(context: Context) {
        try {
            val app = context.applicationContext
            TimerSettingsStateHolder.init(app)
            if (TimerSettingsStateHolder.useMobileVolume.value) {
                Log.d(TAG, "sync skipped useMobileVolume=true")
                return
            }
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val frac = TimerSettingsStateHolder.volume.value.coerceIn(0f, 1f)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
            val idx = (frac * max).roundToInt().coerceIn(0, max)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                idx,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
            )
            Log.d(TAG, "STREAM_ALARM set idx=$idx max=$max frac=$frac")
        } catch (e: Exception) {
            Log.w(TAG, "syncAlarmStream failed", e)
        }
    }

    /**
     * DD Music speelt altijd via de normale media-stream (nooit de ALARM-stream, zie
     * PlayerProvider in DD Music zelf). Om DD Music toch op het geconfigureerde timer-volume te
     * horen te zijn, nemen we hier dezelfde slider-waarde over naar [AudioManager.STREAM_MUSIC] -
     * los van [syncAlarmStreamFromSlider], die alleen relevant is voor het ingebouwde
     * ringtone-geluid.
     */
    fun syncMediaStreamFromSlider(context: Context) {
        try {
            val app = context.applicationContext
            TimerSettingsStateHolder.init(app)
            if (TimerSettingsStateHolder.useMobileVolume.value) {
                Log.d(TAG, "media sync skipped useMobileVolume=true")
                return
            }
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val frac = TimerSettingsStateHolder.volume.value.coerceIn(0f, 1f)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val idx = (frac * max).roundToInt().coerceIn(0, max)
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                idx,
                0
            )
            Log.d(TAG, "STREAM_MUSIC set idx=$idx max=$max frac=$frac")
        } catch (e: Exception) {
            Log.w(TAG, "syncMediaStream failed", e)
        }
    }
}
