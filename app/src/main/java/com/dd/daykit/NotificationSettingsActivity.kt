package com.dd.daykit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.swipeUpBackGesture

class NotificationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                NotificationSettingsScreen { finish() }
            }
        }
    }
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.screenHeightDp > configuration.screenWidthDp
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))
    val scrollState = rememberScrollState()
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)

    val horizontalPadding = if (isPortrait) 24.dp else 16.dp

    val navigateBackToSettings: () -> Unit = {
        context.startActivity(Intent(context, SettingsActivity::class.java))
        (context as? Activity)?.finish()
    }

    var inAppNotificationsEnabled by remember { mutableStateOf(SettingsManager.getInAppNotificationsEnabled(context)) }
    var fullScreenAlarmEnabled by remember { mutableStateOf(SettingsManager.getFullScreenAlarmEnabled(context)) }
    var drawOverOtherAppsEnabled by remember { mutableStateOf(SettingsManager.getDrawOverOtherAppsEnabled(context)) }
    var canUseFullScreenIntent by remember { mutableStateOf(FullScreenIntentPermission.canUse(context)) }
    var hasOverlayPermission by remember { mutableStateOf(OverlayPermission.hasSystemPermission(context)) }
    var showFullScreenPermissionDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canUseFullScreenIntent = FullScreenIntentPermission.canUse(context)
                hasOverlayPermission = OverlayPermission.hasSystemPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        SettingsManager.saveLastVisitedSubSettingsPage(context, "NOTIFICATIONS")
        android.util.Log.d("SettingsNavRestore", "saveLastSubSettingsPage page=NOTIFICATIONS (screen open)")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .swipeUpBackGesture(
                enabled = swipeEnabled,
                scrollState = scrollState,
                onBack = navigateBackToSettings,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isPortrait) {
                                Modifier.heightIn(min = maxHeight)
                            } else {
                                Modifier
                            }
                        )
                        .verticalScroll(scrollState)
                        .padding(horizontal = horizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (isPortrait) {
                        Arrangement.Center
                    } else {
                        Arrangement.Top
                    }
                ) {
                    if (!isPortrait) {
                        Spacer(Modifier.height(8.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isPortrait) Modifier.widthIn(max = 520.dp) else Modifier
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = LanguageManager.getString("notifications"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(if (isPortrait) 28.dp else 12.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NotificationSettingItem(
                                title = LanguageManager.getString("in_app_notifications"),
                                subtitle = LanguageManager.getString("in_app_notifications_desc"),
                                enabled = inAppNotificationsEnabled,
                                onToggle = { inAppNotificationsEnabled = it },
                                textColor = textColor,
                                buttonColor = buttonColor,
                                modifier = Modifier.fillMaxWidth()
                            )

                            NotificationSettingItem(
                                title = LanguageManager.getString("permission_full_screen_intent"),
                                subtitle = LanguageManager.getString("full_screen_alarm_setting_desc"),
                                warning = if (fullScreenAlarmEnabled && !canUseFullScreenIntent) {
                                    LanguageManager.getString("full_screen_alarm_permission_missing")
                                } else {
                                    null
                                },
                                enabled = fullScreenAlarmEnabled,
                                onToggle = {
                                    fullScreenAlarmEnabled = it
                                    if (it && !canUseFullScreenIntent) {
                                        showFullScreenPermissionDialog = true
                                    }
                                },
                                textColor = textColor,
                                buttonColor = buttonColor,
                                modifier = Modifier.fillMaxWidth()
                            )

                            NotificationSettingItem(
                                title = LanguageManager.getString("permission_draw_over_other_apps"),
                                subtitle = LanguageManager.getString("draw_over_other_apps_setting_desc"),
                                enabled = drawOverOtherAppsEnabled,
                                onToggle = {
                                    drawOverOtherAppsEnabled = it
                                },
                                trailingAction = if (!hasOverlayPermission) {
                                    {
                                        MissingOverlayPermissionButton(
                                            textColor = textColor,
                                            onClick = { OverlayPermission.openSettings(context) },
                                        )
                                    }
                                } else {
                                    null
                                },
                                textColor = textColor,
                                buttonColor = buttonColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(if (isPortrait) 24.dp else 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            Button(
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = buttonTextColor
                                )
                            ) {
                                Text(LanguageManager.getString("cancel"))
                            }
                            Button(
                                onClick = {
                                    SettingsManager.saveInAppNotificationsEnabled(context, inAppNotificationsEnabled)
                                    SettingsManager.saveFullScreenAlarmEnabled(context, fullScreenAlarmEnabled)
                                    SettingsManager.saveDrawOverOtherAppsEnabled(context, drawOverOtherAppsEnabled)
                                    onBack()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonColor,
                                    contentColor = buttonTextColor
                                )
                            ) {
                                Text(LanguageManager.getString("save"))
                            }
                        }
                    }

                    Spacer(Modifier.height(if (isPortrait) 24.dp else 8.dp))
                }
            }

            if (SettingsManager.getShowNavButtons(context)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    NavigationBar(currentPage = "SETTINGS")
                }
            }
        }

        SwipeIndicators(
            isVertical = isPortrait,
            showUp = true,
            upIndicatorPointsDown = true,
            upTouchSize = 120.dp,
            onSwipeUp = { navigateBackToSettings() },
        )
    }

    if (showFullScreenPermissionDialog) {
        PermissionSettingsDialog(
            title = LanguageManager.getString("permission_full_screen_intent"),
            message = LanguageManager.getString("full_screen_alarm_permission_dialog"),
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            onDismiss = { showFullScreenPermissionDialog = false },
            onOpenSettings = {
                showFullScreenPermissionDialog = false
                FullScreenIntentPermission.openFullScreenNotificationSettings(context)
            },
        )
    }

}

@Composable
fun NotificationSettingItem(
    title: String,
    subtitle: String,
    warning: String? = null,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    buttonColor: Color,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f)
            )
            if (!warning.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFA726)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (trailingAction != null) {
            trailingAction()
        } else {
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.size(width = 50.dp, height = 24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = buttonColor,
                    checkedTrackColor = buttonColor.copy(alpha = 0.5f),
                    uncheckedThumbColor = textColor.copy(alpha = 0.5f),
                    uncheckedTrackColor = textColor.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
private fun MissingOverlayPermissionButton(
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 24.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = LanguageManager.getString("permission_open_android_settings"),
            tint = textColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun PermissionSettingsDialog(
    title: String,
    message: String,
    buttonColor: Color,
    buttonTextColor: Color,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = buttonTextColor,
                ),
            ) {
                Text(LanguageManager.getString("permission_open_android_settings"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LanguageManager.getString("cancel"))
            }
        },
    )
}
