package com.dd.daykit

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/** Runtime check + settings deep-link for full-screen intent (API 34+). */
object FullScreenIntentPermission {

    private const val TAG = "FullScreenIntentPerm"

    data class CheckResult(
        val canUse: Boolean,
        val mode: String,
    )

    /** @return [CheckResult.mode] describes how [canUse] was determined. */
    fun check(context: Context): CheckResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return CheckResult(
                canUse = true,
                mode = "assumed_granted_pre_api34"
            )
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val granted = nm.canUseFullScreenIntent()
        return CheckResult(
            canUse = granted,
            mode = "api34_canUseFullScreenIntent"
        )
    }

    fun canUse(context: Context): Boolean = check(context).canUse

    fun logState(context: Context, callerTag: String) {
        val result = check(context)
        Log.i(
            TAG,
            "[$callerTag] canUseFullScreenIntent=${result.canUse} mode=${result.mode} sdk=${Build.VERSION.SDK_INT}"
        )
        if (!result.canUse) {
            Log.w(
                TAG,
                "[$callerTag] Full-screen intent permission missing — lockscreen alarm UI may not auto-open"
            )
        }
    }

    /**
     * Opens the best available system screen for full-screen / alarm notification access.
     * @return human-readable label of the intent that was launched, or null on failure.
     */
    fun openSettings(context: Context): String? = openFullScreenNotificationSettings(context)

    fun openFullScreenNotificationSettings(context: Context): String? {
        return PermissionSettingsNavigator.openFullScreenIntentSettings(context)
    }
}
