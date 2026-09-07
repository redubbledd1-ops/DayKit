package com.dd.daykit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dd.daykit.LanguageManager
import com.dd.daykit.SettingsManager

/**
 * Compacte bevestiging voor lokaal Agenda Alarm aan/uit (zelfde donkere thema als de app).
 */
@Composable
fun AgendaAlarmLocalActivationConfirmDialog(
    title: String,
    timeHHmm: String,
    eventLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonContentColor = Color(SettingsManager.getButtonTextColor(context))
    val containerColor = Color(SettingsManager.getBackgroundColor(context))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        titleContentColor = textColor,
        textContentColor = textColor,
        confirmButton = {},
        dismissButton = {},
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Text(
                    text = timeHHmm,
                    color = textColor.copy(alpha = 0.95f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = LanguageManager.getString("agenda_alarm_local_confirm_message"),
                    modifier = Modifier.fillMaxWidth(),
                    color = textColor.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                if (eventLabel.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = eventLabel,
                        modifier = Modifier.fillMaxWidth(),
                        color = textColor.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = buttonColor.copy(alpha = 0.85f),
                            contentColor = buttonContentColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = LanguageManager.getString("dialog_no"),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(28.dp))
                    FilledIconButton(
                        onClick = onConfirm,
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = buttonColor,
                            contentColor = buttonContentColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = LanguageManager.getString("dialog_yes"),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    )
}
