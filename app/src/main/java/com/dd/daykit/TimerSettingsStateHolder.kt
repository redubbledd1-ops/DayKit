package com.dd.daykit

import android.content.Context
import android.media.AudioAttributes
import androidx.compose.runtime.mutableStateOf

object TimerSettingsStateHolder {
    private const val TAG = "TimerSettingsStateHolder"
    private const val PREFS_NAME = "TimerSettings"
    private const val KEY_VOLUME = "volume"
    private const val KEY_VIBRATE = "vibrate"
    private const val KEY_TONE_URI = "tone_uri"
    private const val KEY_LAST_TIME = "last_time_millis"
    private const val KEY_POPUP_ENABLED = "popup_enabled"
    private const val KEY_USE_MOBILE_VOLUME = "use_mobile_volume"
    private const val KEY_MULTI_TIMER = "multi_timer_enabled"
    private const val KEY_DD_MUSIC_LINKED = "dd_music_linked"
    private const val KEY_DD_MUSIC_SUMMARY = "dd_music_summary"
    private const val KEY_DD_MUSIC_PLAY_URL = "dd_music_play_url"

    const val DEFAULT_POPUP_ENABLED = true

    val volume = mutableStateOf(1.0f) // 0.0 - 1.0
    val vibrationEnabled = mutableStateOf(true)
    val toneUri = mutableStateOf<String?>(null)
    val lastTimeMillis = mutableStateOf(300000L) // Default 5 min (300000ms)
    val multiTimerEnabled = mutableStateOf(false)
    val popupEnabled = mutableStateOf(DEFAULT_POPUP_ENABLED)
    /** `false` = links/UIT: custom volume via slider. `true` = rechts/AAN: systeem alarm-stream volume; slider waarde wordt niet gebruikt. */
    val useMobileVolume = mutableStateOf(false)
    /** Timer-afloop koppelen aan DD Music (geldt voor alle timers - hoofdtimer + extra timers).
     *  Wát er precies afspeelt kiest de gebruiker in DD Music zelf (zie DdMusicBridge). */
    val ddMusicLinked = mutableStateOf(false)
    /** Leesbare samenvatting van de DD Music-keuze (songtitel/playlistnaam/etc), teruggestuurd
     *  via broadcast door DdMusicLinkUpdateReceiver. Null tot er een keuze bekend is. */
    val ddMusicSummary = mutableStateOf<String?>(null)
    /** URL waarop DD Music het gekozen nummer serveert (alleen bij mode "song"), voor gebruik
     *  op de HA-speaker i.p.v. het normale timergeluid. */
    val ddMusicPlayUrl = mutableStateOf<String?>(null)
    @Volatile
    private var popupRuntimeSuppressed = false

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        volume.value = prefs.getFloat(KEY_VOLUME, 1.0f)
        vibrationEnabled.value = prefs.getBoolean(KEY_VIBRATE, true)
        toneUri.value = prefs.getString(KEY_TONE_URI, null)
        lastTimeMillis.value = prefs.getLong(KEY_LAST_TIME, 300000L)
        popupEnabled.value = prefs.getBoolean(KEY_POPUP_ENABLED, DEFAULT_POPUP_ENABLED)
        useMobileVolume.value = prefs.getBoolean(KEY_USE_MOBILE_VOLUME, false)
        multiTimerEnabled.value = prefs.getBoolean(KEY_MULTI_TIMER, false)
        ddMusicLinked.value = prefs.getBoolean(KEY_DD_MUSIC_LINKED, false)
        ddMusicSummary.value = prefs.getString(KEY_DD_MUSIC_SUMMARY, null)
        ddMusicPlayUrl.value = prefs.getString(KEY_DD_MUSIC_PLAY_URL, null)
        android.util.Log.i(
            TAG,
            "SETTING_LOAD useMobileVolume=${useMobileVolume.value} volume=${volume.value} popupEnabled=${popupEnabled.value} runtimeSuppressed=$popupRuntimeSuppressed"
        )
    }

    /**
     * @param syncWhen true: [SharedPreferences.Editor.commit] (direct op schijf), o.a. voor de
     * mobiel-volume-schakelaar zodat een snelle [init] daarna niet weer oude prefs inleest.
     */
    /** Persist idle input duration (e.g. explicit reset to 00:00:00). Does not touch other settings keys individually. */
    fun persistLastInputTime(context: Context, millis: Long, sync: Boolean = true) {
        lastTimeMillis.value = millis
        save(context, sync)
    }

    fun save(context: Context, sync: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putFloat(KEY_VOLUME, volume.value)
            .putBoolean(KEY_VIBRATE, vibrationEnabled.value)
            .putString(KEY_TONE_URI, toneUri.value)
            .putLong(KEY_LAST_TIME, lastTimeMillis.value)
            .putBoolean(KEY_POPUP_ENABLED, popupEnabled.value)
            .putBoolean(KEY_USE_MOBILE_VOLUME, useMobileVolume.value)
            .putBoolean(KEY_MULTI_TIMER, multiTimerEnabled.value)
            .putBoolean(KEY_DD_MUSIC_LINKED, ddMusicLinked.value)
            .putString(KEY_DD_MUSIC_SUMMARY, ddMusicSummary.value)
            .putString(KEY_DD_MUSIC_PLAY_URL, ddMusicPlayUrl.value)
        val ok = if (sync) editor.commit() else {
            editor.apply()
            true
        }
        android.util.Log.i(
            TAG,
            "SETTING_SAVE sync=$sync commitOk=$ok useMobileVolume=${useMobileVolume.value} volume=${volume.value} popupEnabled=${popupEnabled.value}"
        )
    }

    /**
     * Alleen [KEY_VOLUME] wegschrijven met [SharedPreferences.Editor.apply].
     * Gebruik dit bij slepen van de volumeslider zodat een oude async `apply()` van een eerdere
     * volledige [save] nooit [KEY_USE_MOBILE_VOLUME] weer overschrijft na een switch-[commit].
     */
    fun persistVolumeFractionOnly(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_VOLUME, volume.value)
            .apply()
    }

    fun setPopupEnabled(context: Context, enabled: Boolean) {
        popupEnabled.value = enabled
        if (enabled) {
            popupRuntimeSuppressed = false
        }
        val committed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_POPUP_ENABLED, enabled)
            .commit()
        android.util.Log.i(
            TAG,
            "TOGGLE_CHANGED popupEnabled=$enabled commitSuccess=$committed runtimeSuppressed=$popupRuntimeSuppressed"
        )
    }

    fun isPopupStartupAllowed(): Boolean = popupEnabled.value && !popupRuntimeSuppressed

    /** Timer geluid op alarm-stream (past bij [AudioManager.STREAM_ALARM]). */
    fun timerAlarmAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()


    fun clearPopupRuntimeSuppression(reason: String) {
        if (popupRuntimeSuppressed) {
            android.util.Log.i(TAG, "POPUP_FAILURE_RECOVERY runtimeSuppressed=false reason=$reason")
        }
        popupRuntimeSuppressed = false
    }

    fun suppressPopupRuntime(reason: String, throwable: Throwable? = null) {
        popupRuntimeSuppressed = true
        if (throwable != null) {
            android.util.Log.e(
                TAG,
                "POPUP_FAILURE_FALLBACK runtimeSuppressed=true reason=$reason persistedPopupEnabled=${popupEnabled.value}",
                throwable
            )
        } else {
            android.util.Log.w(
                TAG,
                "POPUP_FAILURE_FALLBACK runtimeSuppressed=true reason=$reason persistedPopupEnabled=${popupEnabled.value}"
            )
        }
    }
}
