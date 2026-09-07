package com.dd.daykit

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Minimale, losstaande volledig-scherm popup voor weermeldingen (los van het bestaande
 * wekker-alarmsysteem — geen sluimer/HA-koppeling nodig hiervoor). Toont titel + bericht,
 * speelt kort het standaard notificatiegeluid en sluit op tik.
 */
class WeatherAlertPopupActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
    }

    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setShowWhenLocked(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val title = intent.getStringExtra(EXTRA_TITLE) ?: LanguageManager.getString("weather_notification_title")
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""

        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.play()
        } catch (e: Exception) {
            // Geluid is best-effort — de popup blijft ook zonder geluid werken.
        }

        setContent {
            MaterialTheme {
                WeatherAlertPopupScreen(title = title, message = message) {
                    ringtone?.stop()
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
    }
}

@Composable
private fun WeatherAlertPopupScreen(title: String, message: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Cloud,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = textColor,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
        ) {
            Text(LanguageManager.getString("alarm_dismiss"))
        }
    }
}
