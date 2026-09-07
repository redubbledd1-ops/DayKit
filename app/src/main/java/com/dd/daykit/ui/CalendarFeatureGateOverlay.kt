package com.dd.daykit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dd.daykit.CalendarFeatureStatus
import com.dd.daykit.LanguageManager

/**
 * Compact card for calendar permission / provider issues (main Alarm screen).
 */
@Composable
fun CalendarFeatureGateCard(
    status: CalendarFeatureStatus,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    onGrantCalendarPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLanguage by LanguageManager.currentLanguage
    remember(currentLanguage.code) { Unit }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3E2723).copy(alpha = 0.92f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val title = when (status) {
                CalendarFeatureStatus.CalendarPermissionMissing ->
                    LanguageManager.getString("calendar_gate_title_permission")
                CalendarFeatureStatus.CalendarAccessUnavailable ->
                    LanguageManager.getString("calendar_gate_title_unavailable")
                CalendarFeatureStatus.Ready -> ""
            }
            val body = when (status) {
                CalendarFeatureStatus.CalendarPermissionMissing ->
                    LanguageManager.getString("calendar_gate_body_permission")
                CalendarFeatureStatus.CalendarAccessUnavailable ->
                    LanguageManager.getString("calendar_gate_body_unavailable")
                CalendarFeatureStatus.Ready -> ""
            }
            if (title.isNotEmpty()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (body.isNotEmpty()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (status == CalendarFeatureStatus.CalendarPermissionMissing) {
                Button(
                    onClick = onGrantCalendarPermission,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("calendar_grant_access"))
                }
            }

            if (status == CalendarFeatureStatus.CalendarAccessUnavailable) {
                Button(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor.copy(alpha = 0.85f),
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("calendar_open_app_settings"))
                }
            }

            if (status != CalendarFeatureStatus.Ready) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor.copy(alpha = 0.75f),
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("calendar_retry"))
                }
            }
        }
    }
}

/**
 * Full-screen dimming overlay (optional flows).
 */
@Composable
fun CalendarFeatureGateOverlay(
    status: CalendarFeatureStatus,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    onGrantCalendarPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 28.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        CalendarFeatureGateCard(
            status = status,
            textColor = textColor,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            onGrantCalendarPermission = onGrantCalendarPermission,
            onOpenAppSettings = onOpenAppSettings,
            onRetry = onRetry
        )
    }
}
