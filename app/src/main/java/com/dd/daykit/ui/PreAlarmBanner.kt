package com.dd.daykit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dd.daykit.rememberInAppNotificationsEnabled
import com.dd.daykit.rules.PreAlarmCancelReason
import com.dd.daykit.rules.PreAlarmUiStatus

/**
 * Pre-alarm status banner shown in the app at T-5 minutes.
 * 
 * Scenarios:
 * A) User is out of bed → Alarm will NOT fire (can force it)
 * B) Status uncertain → Alarm will fire normally
 * C) User is in bed → Alarm will fire
 *
 * CHECK_DISABLED and NOT_CHECKED are valid configurations — no banner on the home screen.
 */
fun isPreAlarmBannerDisplayable(status: PreAlarmUiStatus?): Boolean {
    val s = status ?: return false
    return s.reason != PreAlarmCancelReason.CHECK_DISABLED &&
        s.reason != PreAlarmCancelReason.NOT_CHECKED
}

@Composable
fun PreAlarmBanner(
    status: PreAlarmUiStatus?,
    visible: Boolean,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    onDismiss: () -> Unit,
    onForceAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!rememberInAppNotificationsEnabled()) return

    AnimatedVisibility(
        visible = visible && isPreAlarmBannerDisplayable(status),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        SideEffect {
            InternalInAppBannerLog.render(
                "PreAlarmBanner",
                "pre_alarm_card",
                "PreAlarmBanner (Card layout — niet de compacte statusstrip)"
            )
        }
        status?.let { preAlarmStatus ->
            val (title, message, showForceButton) = getPreAlarmContent(preAlarmStatus)
            val bannerColor = getBannerColor(preAlarmStatus, buttonColor)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = bannerColor.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Header row with title and close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🧠",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Sluiten",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // Message
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 4.dp, bottom = if (showForceButton) 8.dp else 0.dp)
                    )
                    
                    // Force alarm button (only for Scenario A)
                    if (showForceButton) {
                        Button(
                            onClick = onForceAlarm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                contentColor = buttonTextColor
                            ),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Alarm toch af laten gaan")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get the content for the pre-alarm banner based on status.
 * Returns: Triple(title, message, showForceButton)
 */
private fun getPreAlarmContent(status: PreAlarmUiStatus): Triple<String, String, Boolean> {
    return when {
        // Scenario A: User is out of bed - alarm will be cancelled
        !status.willAlarmFire && status.reason == PreAlarmCancelReason.USER_OUT_OF_BED -> {
            Triple(
                "Pre-alarm check",
                "Je bent nu uit bed.\nHet alarm over 5 minuten zal NIET afgaan.",
                true // Show force button
            )
        }
        
        // Scenario B: Status uncertain (fail-safe) - alarm will fire
        status.willAlarmFire && isUncertainReason(status.reason) -> {
            Triple(
                "Pre-alarm check",
                "Je status is onzeker.\nHet alarm zal normaal afgaan.",
                false
            )
        }
        
        // Scenario C: User is in bed - alarm will fire
        status.willAlarmFire && status.reason == PreAlarmCancelReason.USER_IN_BED -> {
            Triple(
                "Pre-alarm check",
                "Je ligt nog in bed.\nHet alarm zal over 5 minuten afgaan.",
                false
            )
        }
        
        // Scenario: Insufficient duration - alarm will fire
        status.willAlarmFire && status.reason == PreAlarmCancelReason.INSUFFICIENT_DURATION -> {
            Triple(
                "Pre-alarm check",
                "Je bent kort uit bed geweest.\nHet alarm zal normaal afgaan.",
                false
            )
        }
        
        // Scenario: User not home
        status.reason == PreAlarmCancelReason.USER_NOT_HOME -> {
            Triple(
                "Pre-alarm check",
                "Je bent niet thuis.\nHet alarm zal normaal afgaan.",
                false
            )
        }
        
        // Default fallback
        else -> {
            Triple(
                "Pre-alarm check",
                if (status.willAlarmFire) "Het alarm zal afgaan." else "Het alarm zal niet afgaan.",
                !status.willAlarmFire
            )
        }
    }
}

/**
 * Check if the reason indicates an uncertain/fail-safe scenario.
 */
private fun isUncertainReason(reason: PreAlarmCancelReason): Boolean {
    return reason in listOf(
        PreAlarmCancelReason.SENSOR_ERROR,
        PreAlarmCancelReason.SENSOR_UNRELIABLE,
        PreAlarmCancelReason.HA_CONNECTION_ERROR,
        PreAlarmCancelReason.PRESENCE_ERROR
    )
}

/**
 * Get the banner accent color based on status.
 */
private fun getBannerColor(status: PreAlarmUiStatus, defaultColor: Color): Color {
    return when {
        // Alarm will be cancelled - warning/orange
        !status.willAlarmFire -> Color(0xFFFF9800)
        // Uncertain status - yellow
        isUncertainReason(status.reason) -> Color(0xFFFFC107)
        // Normal - use default button color
        else -> defaultColor
    }
}
