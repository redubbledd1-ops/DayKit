package com.dd.daykit.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dd.daykit.AppBackground
import com.dd.daykit.BackupManager
import com.dd.daykit.LanguageManager
import com.dd.daykit.SettingsManager
import kotlinx.coroutines.launch

/**
 * Backup & Overzetten bestaat uit drie pagina's: [Start] toont de keuze tussen maken en
 * terugzetten, [Create] maakt een backup, en [RestorePassword] vraagt het wachtwoord van het
 * gekozen bestand.
 *
 * Er zat hiertussen ooit een overzichtspagina met de backups die op het toestel gevonden waren.
 * Die is vervallen: sinds de app geen "alle bestanden"-toegang meer vraagt kan zo'n lijst alleen
 * nog bestanden bevatten die deze app zelf onder deze pakketnaam heeft weggeschreven, en dat is
 * precies níét het geval waarvoor je terugzet (een backup van een ander toestel). Terugzetten
 * opent daarom meteen de systeem-bestandskiezer, die elk bestand aankan.
 */
private enum class BackupRestoreScreenMode {
    Start,
    Create,
    RestorePassword
}

@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    initialRestoreUri: Uri? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))

    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var backupInProgress by remember { mutableStateOf(false) }
    var backupError by remember { mutableStateOf<String?>(null) }

    var selectedRestoreUri by remember(initialRestoreUri) { mutableStateOf(initialRestoreUri) }
    var selectedRestoreFilename by remember(initialRestoreUri) {
        mutableStateOf(initialRestoreUri?.let { getDisplayName(context, it) }.orEmpty())
    }
    var restorePassword by remember { mutableStateOf("") }
    var restoreInProgress by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var showRestartDialog by remember { mutableStateOf(false) }

    // Wordt de gebruiker rechtstreeks naar een backupbestand gestuurd (bv. vanaf het hoofdscherm),
    // dan slaan we de tussenpagina over. Het pijltje terug moet dan ook meteen het hele scherm
    // sluiten in plaats van naar de startpagina te gaan waar de gebruiker nooit is geweest.
    val openedDirectlyOnFile = initialRestoreUri != null
    var screenMode by remember(initialRestoreUri) {
        mutableStateOf(
            if (openedDirectlyOnFile) BackupRestoreScreenMode.RestorePassword
            else BackupRestoreScreenMode.Start
        )
    }

    val backupPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            selectedRestoreFilename = getDisplayName(context, uri)
            restorePassword = ""
            restoreError = null
            screenMode = BackupRestoreScreenMode.RestorePassword
        }
    }

    val runRestore: () -> Unit = {
        val restoreUri = selectedRestoreUri
        when {
            restorePassword.isBlank() -> restoreError = LanguageManager.getString("backup_error_enter_restore_password")
            restoreUri == null -> restoreError = LanguageManager.getString("backup_error_choose_file")
            else -> {
                coroutineScope.launch {
                    restoreInProgress = true
                    restoreError = null
                    val result = BackupManager.restoreBackup(context, restoreUri, restorePassword)
                    restoreInProgress = false
                    result.fold(
                        onSuccess = { showRestartDialog = true },
                        onFailure = { restoreError = localizedBackupFailure(it, "backup_restore_failed") }
                    )
                }
            }
        }
    }

    when (screenMode) {
        BackupRestoreScreenMode.Start -> BackupStartScreen(
            onBack = onBack,
            onCreateBackup = {
                backupPassword = ""
                backupPasswordConfirm = ""
                backupError = null
                screenMode = BackupRestoreScreenMode.Create
            },
            // Meteen de systeem-bestandskiezer: die heeft geen enkele opslagpermissie nodig en
            // kan bij álle backupbestanden, ook die van een ander toestel of een oudere
            // installatie. Na het kiezen springt [backupPickerLauncher] door naar het
            // wachtwoordscherm; annuleert de gebruiker, dan blijft deze pagina gewoon staan.
            onRestoreBackup = { backupPickerLauncher.launch(arrayOf("*/*")) },
            snackbarHostState = snackbarHostState,
            textColor = textColor,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor
        )

        BackupRestoreScreenMode.Create -> CreateBackupScreen(
            onBack = {
                backupError = null
                screenMode = BackupRestoreScreenMode.Start
            },
            backupPassword = backupPassword,
            onBackupPasswordChange = {
                backupPassword = it
                backupError = null
            },
            backupPasswordConfirm = backupPasswordConfirm,
            onBackupPasswordConfirmChange = {
                backupPasswordConfirm = it
                backupError = null
            },
            backupInProgress = backupInProgress,
            backupError = backupError,
            onCreateBackup = {
                when {
                    backupPassword.isBlank() -> backupError = LanguageManager.getString("backup_error_enter_password")
                    backupPassword != backupPasswordConfirm -> backupError = LanguageManager.getString("backup_error_passwords_mismatch")
                    else -> {
                        coroutineScope.launch {
                            backupInProgress = true
                            backupError = null
                            val result = BackupManager.createBackup(context, backupPassword)
                            backupInProgress = false
                            result.fold(
                                onSuccess = { uri ->
                                    backupPassword = ""
                                    backupPasswordConfirm = ""
                                    // Terug naar Backup & overzetten. De pagina bleef hiervoor
                                    // staan met lege wachtwoordvelden, alsof je meteen nog een
                                    // backup moest maken - terwijl het klaar was.
                                    screenMode = BackupRestoreScreenMode.Start
                                    snackbarHostState.showSnackbar(localizedBackupCreatedMessage(getDisplayName(context, uri)))
                                },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(localizedBackupFailure(error, "backup_create_failed"))
                                }
                            )
                        }
                    }
                }
            },
            snackbarHostState = snackbarHostState,
            textColor = textColor,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor
        )

        BackupRestoreScreenMode.RestorePassword -> RestorePasswordScreen(
            filename = selectedRestoreFilename.ifBlank {
                selectedRestoreUri?.lastPathSegment.orEmpty()
            },
            restorePassword = restorePassword,
            onRestorePasswordChange = {
                restorePassword = it
                restoreError = null
            },
            restoreError = restoreError,
            restoreInProgress = restoreInProgress,
            onBack = {
                if (openedDirectlyOnFile) {
                    onBack()
                } else {
                    restoreError = null
                    // Bestand tóch niet? Terug naar de keuze maken/terugzetten, niet naar een
                    // tussenpagina die er niet meer is.
                    selectedRestoreUri = null
                    selectedRestoreFilename = ""
                    screenMode = BackupRestoreScreenMode.Start
                }
            },
            onRestore = runRestore,
            textColor = textColor,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(LanguageManager.getString("backup_restored_title")) },
            text = { Text(LanguageManager.getString("backup_restored_message")) },
            confirmButton = {
                Button(
                    onClick = { restartApp(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    )
                ) {
                    Text(LanguageManager.getString("restart_app"))
                }
            }
        )
    }
}

/**
 * Gedeeld omhulsel voor alle backup-pagina's: achtergrond, veilige marges, het pijltje naar
 * beneden om terug te gaan, en het scrollgedrag.
 *
 * Past de inhoud binnen het scherm, dan staat die verticaal gecentreerd; is de inhoud langer,
 * dan groeit de kolom door en kan er gescrold worden. Dat komt doordat [verticalScroll] de
 * minimumhoogte van [fillMaxSize] doorgeeft: de kolom is dus minstens zo hoog als het scherm,
 * en [Arrangement.spacedBy] met [Alignment.CenterVertically] centreert binnen die hoogte.
 *
 * De scrollstatus gaat ook naar [swipeUpBackGesture], zodat omhoog vegen midden in een lange
 * pagina gewoon scrollt en niet per ongeluk het scherm sluit.
 *
 * [scrollable] uit betekent: de pagina regelt haar eigen scrollen en de kolom hier blijft precies
 * één schermhoogte. [swipeBackEnabled] uit zet de veeg-om-terug-te-gaan uit, voor pagina's waar
 * die met het scrollen botst. [contentBottomPadding] houdt de onderkant van de inhoud vrij van
 * het terug-pijltje.
 *
 * Beide schakelaars staan sinds het vervallen van de backup-overzichtspagina nergens meer op
 * non-default; ze blijven staan omdat een volgende lijstpagina ze meteen weer nodig heeft.
 */
@Composable
private fun BackupPageScaffold(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    scrollable: Boolean = true,
    swipeBackEnabled: Boolean = true,
    contentBottomPadding: Dp = 96.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val swipeEnabled = SettingsManager.getSwipeEnabled(context) && swipeBackEnabled
    val scrollState = rememberScrollState()

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .swipeUpBackGesture(
                    enabled = swipeEnabled,
                    scrollState = scrollState,
                    onBack = onBack,
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = contentBottomPadding),
                verticalArrangement = Arrangement.spacedBy(
                    14.dp,
                    if (scrollable) Alignment.CenterVertically else Alignment.Top
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )

            StandardBackupBackArrow(
                onClick = onBack,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            snackbarHostState?.let { host ->
                SnackbarHost(
                    hostState = host,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                )
            }
        }
    }
}

/**
 * Startpagina: alleen de keuze tussen een backup maken of er een terugzetten.
 *
 * Heeft een eigen snackbar-plek omdat het maken van een backup meteen hierheen terugkeert - de
 * "backup gemaakt"-melding hoort dan hier te verschijnen, niet op de pagina die net gesloten is.
 */
@Composable
private fun BackupStartScreen(
    onBack: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    snackbarHostState: SnackbarHostState,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color
) {
    BackupPageScaffold(onBack = onBack, snackbarHostState = snackbarHostState) {
        Text(
            text = LanguageManager.getString("backup_restore"),
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onCreateBackup,
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = buttonTextColor
            )
        ) {
            Text(LanguageManager.getString("backup_create"))
        }

        Button(
            onClick = onRestoreBackup,
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = buttonTextColor
            )
        ) {
            Text(LanguageManager.getString("backup_restore_action"))
        }
    }
}

/** Pagina om een nieuwe backup te maken: wachtwoord kiezen en bevestigen. */
@Composable
private fun CreateBackupScreen(
    onBack: () -> Unit,
    backupPassword: String,
    onBackupPasswordChange: (String) -> Unit,
    backupPasswordConfirm: String,
    onBackupPasswordConfirmChange: (String) -> Unit,
    backupInProgress: Boolean,
    backupError: String?,
    onCreateBackup: () -> Unit,
    snackbarHostState: SnackbarHostState,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color
) {
    BackupPageScaffold(onBack = onBack, snackbarHostState = snackbarHostState) {
        Text(
            text = LanguageManager.getString("backup_create"),
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = backupPassword,
            onValueChange = onBackupPasswordChange,
            label = { Text(LanguageManager.getString("backup_password")) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = backupPasswordConfirm,
            onValueChange = onBackupPasswordConfirmChange,
            label = { Text(LanguageManager.getString("backup_confirm_password")) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        backupError?.let {
            Text(
                text = it,
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onCreateBackup,
            enabled = !backupInProgress,
            modifier = Modifier.widthIn(min = 180.dp, max = 280.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = buttonTextColor
            )
        ) {
            Text(
                if (backupInProgress) LanguageManager.getString("backup_create_in_progress")
                else LanguageManager.getString("backup_create")
            )
        }
    }
}

/** Wachtwoordpagina voor de backup die in de bestandskiezer is aangewezen. */
@Composable
private fun RestorePasswordScreen(
    filename: String,
    restorePassword: String,
    onRestorePasswordChange: (String) -> Unit,
    restoreError: String?,
    restoreInProgress: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color
) {
    BackupPageScaffold(onBack = onBack) {
        Text(
            text = LanguageManager.getString("backup_restore_action"),
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = LanguageManager.getString("backup_selected"),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = filename,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = restorePassword,
            onValueChange = onRestorePasswordChange,
            label = { Text(LanguageManager.getString("backup_password")) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        restoreError?.let { error ->
            Text(
                text = error,
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (restoreInProgress) {
            Text(
                text = LanguageManager.getString("backup_restore_in_progress"),
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {
                if (!restoreInProgress) onRestore()
            },
            enabled = !restoreInProgress,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = buttonTextColor
            )
        ) {
            Text(
                if (restoreInProgress) LanguageManager.getString("backup_restore_in_progress")
                else LanguageManager.getString("backup_restore_button")
            )
        }
    }
}

@Composable
private fun StandardBackupBackArrow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SwipeIndicators(
        modifier = modifier,
        isVertical = true,
        showDown = true,
        onSwipeDown = onClick
    )
}

private fun localizedBackupCreatedMessage(filename: String): String {
    return String.format(
        LanguageManager.getLocale(),
        LanguageManager.getString("backup_created_format"),
        filename
    )
}

private fun localizedBackupFailure(error: Throwable, fallbackKey: String): String {
    val key = when (error.message) {
        "Wachtwoord mag niet leeg zijn" -> "backup_error_password_empty"
        "Kan backupbestand niet openen" -> "backup_error_file_open_failed"
        "Verkeerd wachtwoord" -> "backup_error_wrong_password"
        else -> fallbackKey
    }
    return LanguageManager.getString(key)
}

private fun getDisplayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    }.getOrNull()?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex).orEmpty()
        }
    }
    return uri.lastPathSegment.orEmpty()
}

private fun restartApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    if (launchIntent != null) {
        context.startActivity(launchIntent)
    }
    (context as? Activity)?.finish()
}
