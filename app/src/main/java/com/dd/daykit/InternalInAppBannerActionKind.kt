package com.dd.daykit

/**
 * Icon-only actions for internal in-app banners ([com.dd.daykit.ui.UnifiedInternalMessageBannerRow]).
 * Resolved to icons + side effects in the UI layer from [GlobalInAppMessage.actions].
 */
enum class InternalInAppBannerActionKind {
    /** Geen icoon; houdt een vaste rastercel voor stabiele layout (timer/stopwatch). */
    ACTION_ROW_EMPTY,
    AGENDA_SNOOZE,
    AGENDA_STOP,
    TIMER_PLAY,
    TIMER_PAUSE,
    TIMER_STOP,
    TIMER_SAVE,
    STOPWATCH_LAP,
    STOPWATCH_PAUSE,
    STOPWATCH_RESUME,
    STOPWATCH_SAVE,
    /** Stopwatch volledig stoppen zonder op te slaan (interne banner). */
    STOPWATCH_STOP,
}
