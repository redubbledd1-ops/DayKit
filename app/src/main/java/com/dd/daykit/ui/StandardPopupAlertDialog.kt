package com.dd.daykit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Right-aligned icon action for [StandardPopupAlertDialog] (and future popups).
 */
data class PopupIconAction(
    val imageVector: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)

/**
 * Shared Material3 [AlertDialog] layout: **title** + **icon buttons on the right** in one row,
 * **message** small underneath — same pattern as the former timer-finished popup (no custom
 * one-off layout). Use this for new popups so colors/typography stay consistent.
 *
 * @param title Main heading (e.g. “Timer klaar”).
 * @param message Secondary line, shown smaller under the title row (e.g. explanation).
 * @param iconActions Typically 1–3 [FilledIconButton]s shown **to the right of the title**.
 */
@Composable
fun StandardPopupAlertDialog(
    title: String,
    message: String,
    iconActions: List<PopupIconAction>,
    containerColor: Color,
    textColor: Color,
    buttonColor: Color,
    buttonContentColor: Color,
    onDismissRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = containerColor,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = title,
                        color = textColor,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    iconActions.forEach { action ->
                        FilledIconButton(
                            onClick = action.onClick,
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = buttonColor,
                                contentColor = buttonContentColor
                            )
                        ) {
                            Icon(
                                imageVector = action.imageVector,
                                contentDescription = action.contentDescription,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        },
        text = {
            Text(
                text = message,
                color = textColor.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = { }
    )
}
