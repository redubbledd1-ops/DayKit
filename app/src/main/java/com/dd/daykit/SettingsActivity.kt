package com.dd.daykit

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.getTextAlign
import com.dd.daykit.ui.settingsHubSwipeGesture

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)
        LanguageManager.init(this)

        setContent {
            MaterialTheme {
                SettingsScreenWrapper { finish() }
            }
        }
    }
}

@Composable
fun SettingsScreenWrapper(onBack: () -> Unit) {
    var showLanguageSettings by remember { mutableStateOf(false) }

    if (showLanguageSettings) {
        LanguageSelectionScreen(
            onBack = { showLanguageSettings = false }
        )
    } else {
        SettingsScreen(
            onBack = onBack,
            onOpenLanguageSettings = { showLanguageSettings = true }
        )
    }
}

@Composable
fun LanguageSelectionScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(ctx))
    val buttonColor = Color(SettingsManager.getButtonColor(ctx))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(ctx))
    val currentLanguage by LanguageManager.currentLanguage

    var selectedLanguage by remember { mutableStateOf(currentLanguage) }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    LanguageManager.getString("language"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColor
                )
                Spacer(Modifier.height(32.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(Language.entries.toList()) { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (language == selectedLanguage),
                                    onClick = { selectedLanguage = language }
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (language == selectedLanguage),
                                onClick = { selectedLanguage = language },
                                colors = RadioButtonDefaults.colors(selectedColor = buttonColor, unselectedColor = textColor)
                            )
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor.copy(alpha = 0.7f), contentColor = buttonTextColor)
                    ) {
                        Text(LanguageManager.getString("cancel"))
                    }
                    Button(
                        onClick = {
                            LanguageManager.setLanguage(ctx, selectedLanguage)
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                    ) {
                        Text(LanguageManager.getString("save"))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreenMenuButton(
    text: String,
    onClick: () -> Unit,
    buttonColor: Color,
    buttonTextColor: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = buttonTextColor,
        ),
    ) {
        Text(text)
    }
}

/** Portrait (rechtop): één verticale lijst, 1 knop per rij. */
@Composable
private fun SettingsPortraitButtonList(
    buttonColor: Color,
    buttonTextColor: Color,
    onComponents: () -> Unit,
    onNavigation: () -> Unit,
    onNotifications: () -> Unit,
    onDesign: () -> Unit,
    onLanguage: () -> Unit,
    onShortcuts: () -> Unit,
    onBackupRestore: () -> Unit,
    onAgenda: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(0.7f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsScreenMenuButton(
            text = LanguageManager.getString("nav_components"),
            onClick = onComponents,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsScreenMenuButton(
            text = LanguageManager.getString("navigation"),
            onClick = onNavigation,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsScreenMenuButton(
            text = LanguageManager.getString("notifications"),
            onClick = onNotifications,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsScreenMenuButton(
            text = LanguageManager.getString("design"),
            onClick = onDesign,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsScreenMenuButton(
            text = LanguageManager.getString("language"),
            onClick = onLanguage,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsScreenMenuButton(
            text = LanguageManager.getString("shortcuts"),
            onClick = onShortcuts,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsScreenMenuButton(
            text = LanguageManager.getString("backup_restore"),
            onClick = onBackupRestore,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingsScreenMenuButton(
            text = LanguageManager.getString("nav_agenda"),
            onClick = onAgenda,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Landscape: 2 kolommen × 4 rijen
 * Links: Onderdelen, Navigatie, Meldingen, Ontwerp — Rechts: Taal, Snelkoppelingen, Back-up & Herstel, Agenda
 */
@Composable
private fun SettingsLandscapeButtonGrid(
    buttonColor: Color,
    buttonTextColor: Color,
    onComponents: () -> Unit,
    onNavigation: () -> Unit,
    onNotifications: () -> Unit,
    onDesign: () -> Unit,
    onLanguage: () -> Unit,
    onShortcuts: () -> Unit,
    onBackupRestore: () -> Unit,
    onAgenda: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.9f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsScreenMenuButton(
                text = LanguageManager.getString("nav_components"),
                onClick = onComponents,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsScreenMenuButton(
                text = LanguageManager.getString("navigation"),
                onClick = onNavigation,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsScreenMenuButton(
                text = LanguageManager.getString("notifications"),
                onClick = onNotifications,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsScreenMenuButton(
                text = LanguageManager.getString("design"),
                onClick = onDesign,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsScreenMenuButton(
                text = LanguageManager.getString("language"),
                onClick = onLanguage,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsScreenMenuButton(
                text = LanguageManager.getString("shortcuts"),
                onClick = onShortcuts,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsScreenMenuButton(
                text = LanguageManager.getString("backup_restore"),
                onClick = onBackupRestore,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsScreenMenuButton(
                text = LanguageManager.getString("nav_agenda"),
                onClick = onAgenda,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenLanguageSettings: () -> Unit) {
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var textColor by remember { mutableStateOf(Color(SettingsManager.getTextColor(ctx))) }
    var buttonColor by remember { mutableStateOf(Color(SettingsManager.getButtonColor(ctx))) }
    var buttonTextColor by remember { mutableStateOf(Color(SettingsManager.getButtonTextColor(ctx))) }
    var textAlignment by remember { mutableStateOf(SettingsManager.getTextAlignment(ctx)) }
    var showNavButtons by remember { mutableStateOf(SettingsManager.getShowNavButtons(ctx)) }

    val currentLanguage by LanguageManager.currentLanguage

    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                textColor = Color(SettingsManager.getTextColor(ctx))
                buttonColor = Color(SettingsManager.getButtonColor(ctx))
                buttonTextColor = Color(SettingsManager.getButtonTextColor(ctx))
                textAlignment = SettingsManager.getTextAlignment(ctx)
                showNavButtons = SettingsManager.getShowNavButtons(ctx)
                if (context is ComponentActivity) {
                    SettingsManager.applySystemBarColors(context.window, ctx)
                }
            }
        }
        val filter = IntentFilter("com.dd.daykit.DESIGN_UPDATED")
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            ctx.unregisterReceiver(receiver)
        }
    }
    
    LaunchedEffect(Unit) {
        SettingsManager.saveLastVisitedSettingsPage(ctx, "SETTINGS")
    }

    val navigateToLastSubSetting: () -> Unit = {
        val lastPage = SettingsManager.getLastVisitedSubSettingsPage(ctx)
        android.util.Log.d(
            "SettingsNavRestore",
            "restoreLastSubSettingsPage saved=$lastPage",
        )
        when (lastPage) {
            "COMPONENTS" -> ctx.startActivity(Intent(ctx, ComponentsActivity::class.java))
            "NAVIGATION" -> ctx.startActivity(Intent(ctx, NavigationSettingsActivity::class.java))
            "NOTIFICATIONS" -> ctx.startActivity(Intent(ctx, NotificationSettingsActivity::class.java))
            "DESIGN" -> ctx.startActivity(Intent(ctx, DesignSettingsActivity::class.java))
            "LANGUAGE" -> onOpenLanguageSettings()
            "SHORTCUTS" -> ctx.startActivity(Intent(ctx, ShortcutsActivity::class.java))
            "BACKUP_RESTORE" -> ctx.startActivity(Intent(ctx, BackupRestoreActivity::class.java))
            "AGENDA" -> ctx.startActivity(Intent(ctx, AgendaSettingsActivity::class.java))
            else -> {
                android.util.Log.d(
                    "SettingsNavRestore",
                    "restoreLastSubSettingsPage skipped (unknown or empty)",
                )
            }
        }
    }

    val titleTextAlign = getTextAlign(textAlignment)
    val portraitScrollState = rememberScrollState()
    val swipeEnabled = SettingsManager.getSwipeEnabled(ctx)

    val openComponents = {
        SettingsManager.saveLastVisitedSubSettingsPage(ctx, "COMPONENTS")
        ctx.startActivity(Intent(ctx, ComponentsActivity::class.java))
    }
    val openNavigation = {
        SettingsManager.saveLastVisitedSubSettingsPage(ctx, "NAVIGATION")
        ctx.startActivity(Intent(ctx, NavigationSettingsActivity::class.java))
    }
    val openNotifications = {
        SettingsManager.saveLastVisitedSubSettingsPage(ctx, "NOTIFICATIONS")
        android.util.Log.d("SettingsNavRestore", "saveLastSubSettingsPage page=NOTIFICATIONS")
        ctx.startActivity(Intent(ctx, NotificationSettingsActivity::class.java))
    }
    val openDesign = {
        SettingsManager.saveLastVisitedSubSettingsPage(ctx, "DESIGN")
        ctx.startActivity(Intent(ctx, DesignSettingsActivity::class.java))
    }
    val openLanguage = {
        SettingsManager.saveLastVisitedSubSettingsPage(ctx, "LANGUAGE")
        onOpenLanguageSettings()
    }
    val openShortcuts = {
        SettingsManager.saveLastVisitedSubSettingsPage(ctx, "SHORTCUTS")
        ctx.startActivity(Intent(ctx, ShortcutsActivity::class.java))
    }
    val openBackupRestore = {
        SettingsManager.saveLastVisitedSubSettingsPage(ctx, "BACKUP_RESTORE")
        ctx.startActivity(Intent(ctx, BackupRestoreActivity::class.java))
    }
    val openAgenda = {
        SettingsManager.saveLastVisitedSubSettingsPage(ctx, "AGENDA")
        ctx.startActivity(Intent(ctx, AgendaSettingsActivity::class.java))
    }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .settingsHubSwipeGesture(
                    enabled = swipeEnabled,
                    scrollState = if (isPortrait) portraitScrollState else null,
                    onSwipeDown = navigateToLastSubSetting,
                    onSwipeLeft = { NavigationManager.navigateLeft(ctx, "SETTINGS") },
                    onSwipeRight = { NavigationManager.navigateRight(ctx, "SETTINGS") },
                ),
        ) {
            val leftTarget = NavigationManager.getSwipeLeftTarget(ctx, "SETTINGS")
            val rightTarget = NavigationManager.getSwipeRightTarget(ctx, "SETTINGS")

            SwipeIndicators(
                isVertical = isPortrait,
                showLeft = leftTarget != null,
                showRight = rightTarget != null,
                onSwipeLeft = { NavigationManager.navigateLeft(ctx, "SETTINGS") },
                onSwipeRight = { NavigationManager.navigateRight(ctx, "SETTINGS") },
                showUp = true,
                onSwipeUp = navigateToLastSubSetting
            )

            val navBarBottomInset = if (showNavButtons) 72.dp else 0.dp

            if (isPortrait) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(bottom = navBarBottomInset),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .verticalScroll(portraitScrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    Text(
                        LanguageManager.getString("settings"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = textColor,
                        textAlign = titleTextAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsPortraitButtonList(
                        buttonColor = buttonColor,
                        buttonTextColor = buttonTextColor,
                        onComponents = openComponents,
                        onNavigation = openNavigation,
                        onNotifications = openNotifications,
                        onDesign = openDesign,
                        onLanguage = openLanguage,
                        onShortcuts = openShortcuts,
                        onBackupRestore = openBackupRestore,
                        onAgenda = openAgenda,
                    )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 32.dp, vertical = 20.dp)
                        .padding(bottom = navBarBottomInset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        LanguageManager.getString("settings"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = textColor,
                        textAlign = titleTextAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsLandscapeButtonGrid(
                        buttonColor = buttonColor,
                        buttonTextColor = buttonTextColor,
                        onComponents = openComponents,
                        onNavigation = openNavigation,
                        onNotifications = openNotifications,
                        onDesign = openDesign,
                        onLanguage = openLanguage,
                        onShortcuts = openShortcuts,
                        onBackupRestore = openBackupRestore,
                        onAgenda = openAgenda,
                    )
                }
            }
            
            if (showNavButtons) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                ) {
                    NavigationBar(currentPage = "SETTINGS")
                }
            }
        }
    }
}
