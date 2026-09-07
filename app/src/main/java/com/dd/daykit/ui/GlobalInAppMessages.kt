package com.dd.daykit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dd.daykit.GlobalInAppMessageManager
import com.dd.daykit.SettingsManager
import com.dd.daykit.rememberInAppNotificationsEnabled

/**
 * Displays all active global in-app messages stacked vertically (zelfde strip als [GlobalTimerStatusBar]).
 * Gebruikt [UnifiedInternalMessageBannerRow] met vaste actieslots en type-icoon.
 */
@Composable
fun GlobalInAppMessageBanners(
    modifier: Modifier = Modifier
) {
    if (!rememberInAppNotificationsEnabled()) return

    val context = LocalContext.current
    val messages = GlobalInAppMessageManager.activeMessages

    if (messages.isEmpty()) return

    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        messages.forEach { message ->
            SideEffect {
                InternalInAppBannerLog.render(
                    "GlobalInAppMessageBanners",
                    "embedded:${message.id}",
                    "UnifiedInternalMessageBannerRow type=${message.type} actions=${message.actions.size}"
                )
            }

            UnifiedInternalMessageBannerRow(
                pipeline = "GlobalInAppMessageBanners",
                slot = "embedded:${message.id}",
                contentColor = buttonTextColor,
                leadingIcon = globalMessageLeadingIcon(message.type),
                title = message.title,
                trailingText = message.text,
                actions = message.actions,
                includeRowBackground = true,
                rowBackgroundColor = buttonColor.copy(alpha = 0.7f),
                reservedActionSlots = InternalInAppBannerDim.GLOBAL_MESSAGE_ACTION_SLOTS,
                onTitleAreaClick = {
                    GlobalInAppMessageManager.navigateToDestination(context, message.destination)
                },
            )
        }
    }
}
