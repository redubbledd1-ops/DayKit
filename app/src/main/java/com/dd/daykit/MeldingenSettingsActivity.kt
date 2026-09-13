package com.dd.daykit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.getHorizontalAlignment
import com.dd.daykit.ui.getTextAlign

class MeldingenSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        val triggerId = intent.getStringExtra("TRIGGER_ID")
        val triggerName = intent.getStringExtra("TRIGGER_NAME")

        setContent {
            MaterialTheme {
                MeldingenSettingsScreen(triggerId = triggerId, triggerName = triggerName, onBack = { finish() })
            }
        }
    }
}

@Composable
fun MeldingenSettingsScreen(triggerId: String? = null, triggerName: String? = null, onBack: () -> Unit) {
    val ctx = LocalContext.current

    val currentLanguage by LanguageManager.currentLanguage
    remember(currentLanguage.code) { Unit }

    val textColor = Color(SettingsManager.getTextColor(ctx))
    val buttonColor = Color(SettingsManager.getButtonColor(ctx))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(ctx))
    val textAlignment = SettingsManager.getTextAlignment(ctx)
    val titleTextAlign = getTextAlign(textAlignment)

    var popupEnabled by remember { mutableStateOf(SettingsManager.getCalendarPopupLast30Min(ctx, triggerId)) }
    var popupWindowMinutes by remember { mutableStateOf(SettingsManager.getCalendarPopupWindowMinutes(ctx, triggerId)) }

    var buttonlessEnabled by remember { mutableStateOf(SettingsManager.getButtonlessNotificationEnabled(ctx, triggerId)) }
    var buttonlessMinutes by remember { mutableStateOf(SettingsManager.getButtonlessNotificationMinutes(ctx, triggerId)) }

    AppBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = getHorizontalAlignment(textAlignment)
            ) {
                Text(
                    if (!triggerName.isNullOrBlank()) {
                        "${LanguageManager.getString("ka_meldingen")} - $triggerName"
                    } else {
                        LanguageManager.getString("ka_meldingen")
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColor,
                    textAlign = titleTextAlign,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            LanguageManager.getString("popup_last"),
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = popupEnabled,
                        onCheckedChange = {
                            popupEnabled = it
                            SettingsManager.saveCalendarPopupLast30Min(ctx, triggerId, it)
                            ctx.sendBroadcast(
                                Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED)
                                    .setPackage(ctx.packageName)
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = buttonColor,
                            checkedTrackColor = buttonColor.copy(alpha = 0.5f)
                        )
                    )
                }

                if (popupEnabled) {
                    Spacer(Modifier.height(12.dp))
                    // Mag nooit langer zijn dan de buttonless melding hieronder.
                    val popupMaxMinutes = buttonlessMinutes.coerceIn(
                        SettingsManager.CALENDAR_POPUP_WINDOW_MIN_MINUTES,
                        SettingsManager.CALENDAR_POPUP_WINDOW_MAX_MINUTES
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            SettingsManager.getPopupWindowDisplayText(popupWindowMinutes),
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = popupWindowMinutes.toFloat(),
                        onValueChange = { raw ->
                            // Per 5 minuten i.p.v. per minuut - alleen de ondergrens (1 min) blijft
                            // als losse uitzondering bereikbaar helemaal onderaan de slider.
                            popupWindowMinutes = (Math.round(raw / 5f) * 5).coerceIn(
                                SettingsManager.CALENDAR_POPUP_WINDOW_MIN_MINUTES,
                                popupMaxMinutes
                            )
                        },
                        onValueChangeFinished = {
                            SettingsManager.saveCalendarPopupWindowMinutes(ctx, triggerId, popupWindowMinutes)
                            ctx.sendBroadcast(
                                Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED)
                                    .setPackage(ctx.packageName)
                            )
                        },
                        valueRange = SettingsManager.CALENDAR_POPUP_WINDOW_MIN_MINUTES.toFloat()..popupMaxMinutes.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = buttonColor,
                            activeTrackColor = buttonColor
                        )
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${LanguageManager.getString("buttonless_notification")} ",
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = buttonlessEnabled,
                        onCheckedChange = {
                            buttonlessEnabled = it
                            SettingsManager.saveButtonlessNotificationEnabled(ctx, triggerId, it)
                            ctx.sendBroadcast(
                                Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED)
                                    .setPackage(ctx.packageName)
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = buttonColor,
                            checkedTrackColor = buttonColor.copy(alpha = 0.5f)
                        )
                    )
                }

                if (buttonlessEnabled) {
                    Spacer(Modifier.height(12.dp))
                    val hours = buttonlessMinutes / 60
                    val mins = buttonlessMinutes % 60
                    val displayText = if (mins == 0) "$hours ${LanguageManager.getString("buttonless_hours_before")}"
                        else "$hours:${mins.toString().padStart(2, '0')} ${LanguageManager.getString("buttonless_hours_before")}"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            displayText,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = buttonlessMinutes.toFloat(),
                        onValueChange = { raw ->
                            buttonlessMinutes = (Math.round(raw / 30f) * 30).coerceIn(120, 840)
                        },
                        onValueChangeFinished = {
                            SettingsManager.saveButtonlessNotificationMinutes(ctx, triggerId, buttonlessMinutes)
                            // De Stop-knop melding mag nooit langer zijn dan dit venster - als het
                            // hierdoor is teruggeklemd, neem de opgeslagen waarde over.
                            popupWindowMinutes = SettingsManager.getCalendarPopupWindowMinutes(ctx, triggerId)
                            ctx.sendBroadcast(
                                Intent(CalendarUpdateReceiver.ACTION_ALARM_UPDATED)
                                    .setPackage(ctx.packageName)
                            )
                        },
                        valueRange = 120f..840f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = buttonColor,
                            activeTrackColor = buttonColor
                        )
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor),
                ) {
                    Text(LanguageManager.getString("back"))
                }
            }

            com.dd.daykit.ui.SwipeIndicators(
                modifier = Modifier.align(Alignment.BottomCenter),
                isVertical = true,
                showDown = true,
                onSwipeDown = onBack
            )
        }
    }
}
