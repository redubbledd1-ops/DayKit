package com.dd.daykit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * First-time shortcut on the KalenderAlarm hub: jump straight to calendar trigger selection.
 * Uses the same layout and controls as [com.dd.daykit.AlarmSettingsScreen] setup blocks.
 */
@Composable
fun KalenderAlarmTriggerOnboardingCard(
    title: String,
    buttonLabel: String,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    onSelectTrigger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSelectTrigger,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = buttonTextColor,
            ),
        ) {
            Text(buttonLabel)
        }
    }
}
