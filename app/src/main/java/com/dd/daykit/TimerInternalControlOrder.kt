package com.dd.daykit

/**
 * Vaste volgorde timer-acties: **Save → Stop → Play/Pause** (links → rechts).
 *
 * Gebruikt door:
 * - [GlobalInAppMessageManager] (interne meldingsbalk)
 * - [TimerActivity] (zelfde volgorde in de UI; spacing via [com.dd.daykit.ui.InternalInAppBannerDim.actionIconGap])
 *
 * Bij **running** is de save-cel leeg ([InternalInAppBannerActionKind.ACTION_ROW_EMPTY]) zodat Stop en Pause
 * op vaste posities blijven zonder layout-shift.
 */
object TimerInternalControlOrder {

    fun inAppBannerKinds(state: GlobalTimerManager.TimerState): List<InternalInAppBannerActionKind> = when (state) {
        GlobalTimerManager.TimerState.RUNNING -> listOf(
            InternalInAppBannerActionKind.ACTION_ROW_EMPTY,
            InternalInAppBannerActionKind.TIMER_STOP,
            InternalInAppBannerActionKind.TIMER_PAUSE,
        )
        GlobalTimerManager.TimerState.PAUSED -> listOf(
            InternalInAppBannerActionKind.TIMER_SAVE,
            InternalInAppBannerActionKind.TIMER_STOP,
            InternalInAppBannerActionKind.TIMER_PLAY,
        )
        else -> emptyList()
    }
}
