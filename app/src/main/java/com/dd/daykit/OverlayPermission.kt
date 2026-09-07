package com.dd.daykit

import android.content.Context
import android.os.Build
import android.provider.Settings

/** Runtime check + settings deep-link for "Draw over other apps". */
object OverlayPermission {

    data class CheckResult(
        val hasSystemPermission: Boolean,
        val enabledByUser: Boolean,
        val canUse: Boolean,
        val mode: String,
    )

    fun check(context: Context): CheckResult {
        val app = context.applicationContext
        val hasSystemPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(app)
        } else {
            true
        }
        val enabledByUser = SettingsManager.getDrawOverOtherAppsEnabled(app)
        return CheckResult(
            hasSystemPermission = hasSystemPermission,
            enabledByUser = enabledByUser,
            canUse = enabledByUser && hasSystemPermission,
            mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                "api23_canDrawOverlays"
            } else {
                "assumed_granted_pre_api23"
            },
        )
    }

    fun hasSystemPermission(context: Context): Boolean = check(context).hasSystemPermission

    fun canUse(context: Context): Boolean = check(context).canUse

    fun openSettings(context: Context): String? {
        return PermissionSettingsNavigator.openOverlaySettings(context)
    }
}
