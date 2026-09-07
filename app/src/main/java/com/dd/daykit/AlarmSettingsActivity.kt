package com.dd.daykit

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.dd.daykit.ui.getHorizontalAlignment
import kotlinx.coroutines.launch

class AlarmSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                AlarmSettingsScreen()
            }
        }
    }
}

@Composable
fun AlarmSettingsScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    val currentLanguage by LanguageManager.currentLanguage
    remember(currentLanguage.code) { Unit }

    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var selectedCalendarIds by remember { mutableStateOf(SettingsManager.getTriggerCalendarIds(context)) }
    var showDisableConfirmDialog by remember { mutableStateOf<String?>(null) }
    var pendingDisableTriggerName by remember { mutableStateOf("") }
    var featureStatus by remember { mutableStateOf<CalendarFeatureStatus>(CalendarFeatureStatus.Ready) }

    fun persistCalendarTriggers(newSelection: Set<String>) {
        selectedCalendarIds = newSelection
        SettingsManager.saveTriggerCalendarIds(context, newSelection)
        // Zelfde sync als KalenderAlarm-instellingen > Sync; lifecycleScope i.p.v. compose scope zodat dit niet stil valt na dialoog.
        activity.lifecycleScope.launch {
            KalenderAlarmManualSync.run(activity.applicationContext)
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        featureStatus = GoogleCalendarFeatureChecker.evaluate(context)
        val allCalendars = getCalendars(context)
        calendars = allCalendars.sortedByDescending { calendar ->
            calendar.id.toString() in selectedCalendarIds
        }
    }

    val triggerRulesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            val triggerId = result.data?.getStringExtra("TRIGGER_ID")
            if (triggerId != null) {
                val newSelection = selectedCalendarIds.toMutableSet()
                newSelection.remove(triggerId)
                persistCalendarTriggers(newSelection)
            }
        }
    }

    fun launchTriggerRules(calendar: CalendarInfo, firstActivation: Boolean) {
        val intent = Intent(context, com.dd.daykit.rules.TriggerRulesActivity::class.java).apply {
            putExtra("TRIGGER_ID", calendar.id.toString())
            putExtra("TRIGGER_NAME", calendar.displayName)
            putExtra("IS_FIRST_ACTIVATION", firstActivation)
        }
        triggerRulesLauncher.launch(intent)
    }

    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))
    val textAlignmentSetting = SettingsManager.getTextAlignment(context)
    val contentHorizontalAlignment = getHorizontalAlignment(textAlignmentSetting)

    LaunchedEffect(Unit) {
        featureStatus = GoogleCalendarFeatureChecker.evaluate(context)
        val allCalendars = getCalendars(context)
        calendars = allCalendars.sortedByDescending { calendar ->
            calendar.id.toString() in selectedCalendarIds
        }
    }

    LaunchedEffect(selectedCalendarIds) {
        val allCalendars = getCalendars(context)
        calendars = allCalendars.sortedByDescending { calendar ->
            calendar.id.toString() in selectedCalendarIds
        }
    }

    SettingsManager.saveLastVisitedSettingsPage(context, "ALARM_SETTINGS")

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = contentHorizontalAlignment,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        LanguageManager.getString("calendar_triggers"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = textColor
                    )
                }

                if (featureStatus == CalendarFeatureStatus.CalendarPermissionMissing) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                LanguageManager.getString("alarm_triggers_permission_rationale"),
                                color = textColor.copy(alpha = 0.9f)
                            )
                            Button(
                                onClick = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                            ) {
                                Text(LanguageManager.getString("calendar_grant_access"))
                            }
                        }
                    }
                } else if (featureStatus == CalendarFeatureStatus.CalendarAccessUnavailable) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                LanguageManager.getString("alarm_triggers_unavailable_body"),
                                color = textColor.copy(alpha = 0.9f)
                            )
                            Button(
                                onClick = {
                                    featureStatus = GoogleCalendarFeatureChecker.evaluate(context)
                                    val allCalendars = getCalendars(context)
                                    calendars = allCalendars.sortedByDescending { calendar ->
                                        calendar.id.toString() in selectedCalendarIds
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor.copy(alpha = 0.85f),
                                    contentColor = buttonTextColor
                                )
                            ) {
                                Text(LanguageManager.getString("calendar_retry"))
                            }
                        }
                    }
                } else if (calendars.isEmpty()) {
                    item {
                        Text(
                            LanguageManager.getString("alarm_no_calendars_help"),
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    items(calendars, key = { it.id }) { calendar ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = calendar.id.toString() in selectedCalendarIds,
                                onCheckedChange = { isChecked ->
                                    val idStr = calendar.id.toString()
                                    if (isChecked) {
                                        val newSelection = selectedCalendarIds.toMutableSet()
                                        val wasUnchecked = idStr !in selectedCalendarIds
                                        newSelection.add(idStr)
                                        persistCalendarTriggers(newSelection)
                                        if (wasUnchecked) {
                                            launchTriggerRules(calendar, firstActivation = true)
                                        }
                                    } else {
                                        pendingDisableTriggerName = calendar.displayName
                                        showDisableConfirmDialog = idStr
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = buttonColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val idStr = calendar.id.toString()
                                        if (idStr in selectedCalendarIds) {
                                            pendingDisableTriggerName = calendar.displayName
                                            showDisableConfirmDialog = idStr
                                        } else {
                                            val newSelection = selectedCalendarIds.toMutableSet()
                                            val wasUnchecked = idStr !in selectedCalendarIds
                                            newSelection.add(idStr)
                                            persistCalendarTriggers(newSelection)
                                            if (wasUnchecked) {
                                                launchTriggerRules(calendar, firstActivation = true)
                                            }
                                        }
                                    }
                            ) {
                                Text(text = calendar.displayName, color = textColor)
                                Text(
                                    text = calendar.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    val idStr = calendar.id.toString()
                                    val wasUnchecked = idStr !in selectedCalendarIds
                                    if (wasUnchecked) {
                                        val newSelection = selectedCalendarIds.toMutableSet()
                                        newSelection.add(idStr)
                                        persistCalendarTriggers(newSelection)
                                        launchTriggerRules(calendar, firstActivation = true)
                                    } else {
                                        launchTriggerRules(calendar, firstActivation = false)
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = buttonColor.copy(alpha = 0.3f),
                                    contentColor = buttonTextColor
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    LanguageManager.getString("calendar_triggers_options"),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDisableConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDisableConfirmDialog = null },
            containerColor = backgroundColor,
            title = {
                Text(
                    LanguageManager.getString("confirm_disable_trigger_title"),
                    color = textColor
                )
            },
            text = {
                Text(
                    LanguageManager.getString("confirm_disable_trigger_message").replace("{name}", pendingDisableTriggerName),
                    color = textColor.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val triggerId = showDisableConfirmDialog
                        if (triggerId != null) {
                            val newSelection = selectedCalendarIds.toMutableSet()
                            newSelection.remove(triggerId)
                            persistCalendarTriggers(newSelection)
                        }
                        showDisableConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Text(LanguageManager.getString("yes_disable"))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDisableConfirmDialog = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                ) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }
}
