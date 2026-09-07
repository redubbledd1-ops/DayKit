package com.dd.daykit

/**
 * Vaste volgorde stopwatch-acties op de **interne** meldingsbalk (links → rechts):
 * - **Running:** Lap → Stop → Pause
 * - **Paused:** Save → Stop → Hervatten (Play)
 *
 * Zelfde semantiek als de stopwatch-pagina ([NewFeatureScreen]); spacing via
 * [com.dd.daykit.ui.InternalInAppBannerDim.actionIconGap].
 */
object StopwatchInternalControlOrder {

    fun inAppBannerKinds(state: StopwatchState): List<InternalInAppBannerActionKind> = when (state) {
        StopwatchState.RUNNING -> listOf(
            InternalInAppBannerActionKind.STOPWATCH_LAP,
            InternalInAppBannerActionKind.STOPWATCH_STOP,
            InternalInAppBannerActionKind.STOPWATCH_PAUSE,
        )
        StopwatchState.PAUSED -> listOf(
            InternalInAppBannerActionKind.STOPWATCH_SAVE,
            InternalInAppBannerActionKind.STOPWATCH_STOP,
            InternalInAppBannerActionKind.STOPWATCH_RESUME,
        )
        else -> emptyList()
    }
}
