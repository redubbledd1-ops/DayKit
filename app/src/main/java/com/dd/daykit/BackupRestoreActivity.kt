package com.dd.daykit

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.BackupRestoreScreen
import com.dd.daykit.ui.theme.AgendaWekkerTheme

class BackupRestoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)
        setContent {
            AgendaWekkerTheme {
                BackupRestoreScreen(
                    onBack = { finish() },
                    initialRestoreUri = intent.getStringExtra(EXTRA_RESTORE_URI)?.let(Uri::parse)
                )
            }
        }
    }

    companion object {
        const val EXTRA_RESTORE_URI = "RESTORE_URI"
    }
}
