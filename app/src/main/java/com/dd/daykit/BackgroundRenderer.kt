package com.dd.daykit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import com.dd.daykit.rememberInAppNotificationsEnabled
import com.dd.daykit.ui.InternalInAppBannerLog
import com.dd.daykit.ui.UnifiedInternalMessageBannerRow

// Helper function to convert alignment string to Compose Alignment
fun getAlignmentFromString(alignment: String): Alignment {
    return when (alignment) {
        "TOP_START" -> Alignment.TopStart
        "TOP_CENTER" -> Alignment.TopCenter
        "TOP_END" -> Alignment.TopEnd
        "CENTER_START" -> Alignment.CenterStart
        "CENTER" -> Alignment.Center
        "CENTER_END" -> Alignment.CenterEnd
        "BOTTOM_START" -> Alignment.BottomStart
        "BOTTOM_CENTER" -> Alignment.BottomCenter
        "BOTTOM_END" -> Alignment.BottomEnd
        else -> Alignment.Center
    }
}

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var backgroundType by remember { mutableStateOf(SettingsManager.getBackgroundType(context)) }
    var backgroundColor by remember { mutableStateOf(Color(SettingsManager.getBackgroundColor(context))) }
    var imageUri by remember { mutableStateOf(SettingsManager.getBackgroundImageUri(context)) }
    var gifUri by remember { mutableStateOf(SettingsManager.getBackgroundGifUri(context)) }
    
    // Listen for design updates
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                backgroundType = SettingsManager.getBackgroundType(context)
                backgroundColor = Color(SettingsManager.getBackgroundColor(context))
                imageUri = SettingsManager.getBackgroundImageUri(context)
                gifUri = SettingsManager.getBackgroundGifUri(context)
            }
        }
        val filter = IntentFilter("com.dd.daykit.DESIGN_UPDATED")
        // Fix for Android 14: Use ContextCompat.registerReceiver with flags
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        when (backgroundType) {
            "color" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                )
            }
            "image" -> {
                if (imageUri != null) {
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(imageUri))
                            .crossfade(true)
                            .build()
                    )
                    Image(
                        painter = painter,
                        contentDescription = "Background Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor)
                    )
                }
            }
            "gif" -> {
                if (gifUri != null) {
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(gifUri))
                            .decoderFactory(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    ImageDecoderDecoder.Factory()
                                } else {
                                    GifDecoder.Factory()
                                }
                            )
                            .crossfade(true)
                            .build()
                    )
                    Image(
                        painter = painter,
                        contentDescription = "Background GIF",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor)
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                )
            }
        }
        
        // Main content - takes full space, not affected by banners
        content()
        
        // Global status banners as OVERLAY (no layout space taken)
        val inAppNotificationsEnabled = rememberInAppNotificationsEnabled()
        val isSnoozeActive by AlarmStateManager.isSnoozeActive.collectAsState()

        if (inAppNotificationsEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .align(Alignment.TopCenter)
            ) {
                Column {
                    if (isSnoozeActive) {
                        com.dd.daykit.ui.GlobalSnoozeBanner()
                    }
                    com.dd.daykit.ui.GlobalTimerStatusBar()
                }
            }
        }

        // Global sound playing indicator
        val isSoundPlaying by SoundStateManager.isPlaying
        if (inAppNotificationsEnabled && isSoundPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .align(Alignment.TopCenter)
            ) {
                SideEffect {
                    InternalInAppBannerLog.render(
                        "BackgroundRenderer",
                        "sound_playing",
                        "UnifiedInternalMessageBannerRow"
                    )
                }
                UnifiedInternalMessageBannerRow(
                    modifier = Modifier.fillMaxWidth(),
                    pipeline = "BackgroundRenderer",
                    slot = "sound_playing",
                    contentColor = Color.White,
                    leadingIcon = Icons.AutoMirrored.Outlined.VolumeUp,
                    title = LanguageManager.getString("sound_playing"),
                    trailingText = null,
                    actions = emptyList(),
                    includeRowBackground = true,
                    rowBackgroundColor = Color(0xFF333333).copy(alpha = 0.9f),
                    reservedActionSlots = 0,
                    endClose = { SoundStateManager.stopCurrentSound() },
                    endCloseContentDescription = "Stop sound",
                )
            }
        }
    }
}
