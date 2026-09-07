package com.dd.daykit.ui

import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.Job
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dd.daykit.AppBackground
import com.dd.daykit.NavigationManager
import com.dd.daykit.Screen
import com.dd.daykit.SettingsManager
import com.dd.daykit.LanguageManager

/**
 * Helper functions for consistent alignment across all screens
 */

fun getBoxAlignmentFromString(alignment: String): Alignment {
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

fun getHorizontalAlignment(alignment: String): Alignment.Horizontal {
    return when {
        alignment.contains("START") -> Alignment.Start
        alignment.contains("END") -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
}

fun getTextAlign(alignment: String): TextAlign {
    return when {
        alignment.endsWith("_START") -> TextAlign.Start
        alignment.endsWith("_END") -> TextAlign.End
        else -> TextAlign.Center
    }
}

fun getHorizontalArrangement(alignment: String): Arrangement.Horizontal {
    return when {
        alignment.endsWith("_START") -> Arrangement.Start
        alignment.endsWith("_END") -> Arrangement.End
        else -> Arrangement.Center
    }
}

@Composable
fun StandardActionButtons(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String? = null,
    saveText: String? = null
) {
    val context = LocalContext.current
    val currentLanguage by LanguageManager.currentLanguage
    
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    
    val actualCancelText = cancelText ?: LanguageManager.getString("cancel")
    val actualSaveText = saveText ?: LanguageManager.getString("save")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        Button(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = textColor.copy(alpha = 0.2f),
                contentColor = textColor
            )
        ) {
            Text(actualCancelText)
        }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
        ) {
            Text(actualSaveText)
        }
    }
}

/**
 * Round Material3 icon button used on Timer and Stopwatch (same sizing/colors as timer primary actions).
 * Pass colors from [SettingsManager] (e.g. button / button text) so controls match Calculator/Timer.
 */
@Composable
fun ScreenRoundIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 52.dp,
    iconSize: Dp = 28.dp,
) {
    Button(
        onClick = onClick,
        // .size() (i.p.v. defaultMinSize) forceert een exact vierkant, ook bij kleinere maten —
        // anders duwt Material3's eigen minimumbreedte (~58dp) de knop bij kleine buttonSize
        // breder dan hoog, en wordt hij ovaal in plaats van rond.
        modifier = modifier.size(buttonSize),
        contentPadding = PaddingValues(buttonSize * 0.23f),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun NavigationBar(
    currentPage: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))
    val currentLanguage by LanguageManager.currentLanguage

    val items = remember(currentLanguage, context) {
        val screens = NavigationManager.getOrderedScreens(context)
        screens.map { screen ->
            val label = screen.titleKey.let { LanguageManager.getString(it) }
            val intentFactory: () -> Intent = {
                Intent(context, screen.activityClass).apply {
                    if (screen == Screen.AGENDA_ALARM) {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    } else {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    putExtra("FROM_NAVIGATION", true)
                }
            }
            NavItem(screen.id, label, intentFactory)
        }
    }

    val maxVisibleItems = 3
    val totalItems = items.size

    val initialIndex = items.indexOfFirst { it.id == currentPage }
    val initialWindowStart = if (initialIndex != -1) {
        val idealStart = initialIndex - (maxVisibleItems / 2)
        idealStart.coerceIn(0, (totalItems - maxVisibleItems).coerceAtLeast(0))
    } else {
        0
    }

    var windowStart by remember { mutableIntStateOf(initialWindowStart) }

    LaunchedEffect(currentPage) {
        val currentIndex = items.indexOfFirst { it.id == currentPage }
        if (currentIndex != -1) {
            if (currentIndex < windowStart) {
                windowStart = currentIndex
            } else if (currentIndex >= windowStart + maxVisibleItems) {
                windowStart = currentIndex - maxVisibleItems + 1
            }
        }
    }

    if (windowStart > totalItems - maxVisibleItems) {
        windowStart = (totalItems - maxVisibleItems).coerceAtLeast(0)
    }
    
    val arrowVisible = totalItems > maxVisibleItems

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center  // Center content since no side arrows
    ) {
        // REMOVED: Left chevron arrow
        // REMOVED: Right chevron arrow
        // Navigation bar now shows only the centered buttons without side arrows
        
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val visibleItems = items.drop(windowStart).take(maxVisibleItems)
            visibleItems.forEach { item ->
                TextButton(
                    onClick = {
                        if (currentPage != item.id) {
                            context.startActivity(item.intentFactory())
                        }
                    }
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (currentPage == item.id) textColor else textColor.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

data class NavItem(val id: String, val label: String, val intentFactory: () -> Intent)

@Composable
fun StandardScreenTemplate(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    val textAlignmentSetting = SettingsManager.getTextAlignment(context)

    val titleTextAlign = getTextAlign(textAlignmentSetting)
    val horizontalAlignment = getHorizontalAlignment(textAlignmentSetting)

    AppBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(32.dp),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = textColor,
                textAlign = titleTextAlign,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SettingsScreenTemplate(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    showBackArrow: Boolean = true,
    showTitle: Boolean = true,
    titleTopPadding: Dp = 16.dp,
    lazyListState: LazyListState = rememberLazyListState(),
    lazyListModifier: Modifier = Modifier, // New modifier for the LazyColumn
    content: LazyListScope.() -> Unit
) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    val textAlignmentSetting = SettingsManager.getTextAlignment(context)

    val titleTextAlign = getTextAlign(textAlignmentSetting)
    val contentHorizontalAlignment = getHorizontalAlignment(textAlignmentSetting)

    AppBackground(modifier = modifier) { // Main modifier applies here
        Box(modifier = Modifier.fillMaxSize()) {
            SwipeIndicators(
                isVertical = true,
                showLeft = showBackArrow,
                onSwipeLeft = { onBack() }
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                if (showTitle) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = textColor,
                        textAlign = titleTextAlign,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = titleTopPadding, bottom = 16.dp)
                    )
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = lazyListModifier // Use the new specific modifier here
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    content()
                }

                StandardActionButtons(
                    onCancel = onBack,
                    onSave = onSave,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }
        }
    }
}

@Composable
fun SwipeIndicators(
    modifier: Modifier = Modifier,
    isVertical: Boolean = true,
    showLeft: Boolean = false,
    showRight: Boolean = false,
    showUp: Boolean = false,
    /** When true with [showUp], shows a down arrow at the bottom while keeping upward swipe/click behavior. */
    upIndicatorPointsDown: Boolean = false,
    showDown: Boolean = false,
    upTouchSize: Dp = 80.dp,
    downBottomPadding: Dp = 64.dp,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {}
) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    val arrowMode = SettingsManager.getArrowMode(context)
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)

    if (arrowMode == "Verborgen") return

    val iconSize = 48.dp
    val iconTint = textColor.copy(alpha = 0.5f)
    val touchSize = if (isVertical) 64.dp else 80.dp
    val resolvedUpTouchSize = if (isVertical) 56.dp else upTouchSize
    val resolvedDownBottomPadding = if (isVertical) 48.dp else downBottomPadding

    // Original working Y position for side arrows
    val sideArrowAlignmentLeft = BiasAlignment(horizontalBias = -1f, verticalBias = -0.08f)
    val sideArrowAlignmentRight = BiasAlignment(horizontalBias = 1f, verticalBias = -0.08f)
    val landscapeUpAlignment = BiasAlignment(horizontalBias = 1f, verticalBias = -0.72f)
    val landscapeDownAlignment = BiasAlignment(horizontalBias = 1f, verticalBias = 0.62f)

    Box(modifier = modifier.fillMaxSize().safeDrawingPadding().padding(0.dp)) {
        if (showLeft) {
            Box(
                modifier = Modifier
                    .align(sideArrowAlignmentLeft)
                    .size(touchSize)
                    .pointerInput(Unit) {
                        if (!swipeEnabled) return@pointerInput
                        var totalX = 0f
                        var totalY = 0f
                        var triggered = false
                        detectDragGestures(
                            onDragStart = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            },
                            onDragEnd = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            },
                            onDragCancel = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            }
                        ) { change, dragAmount ->
                            if (triggered) return@detectDragGestures
                            totalX += dragAmount.x
                            totalY += dragAmount.y
                            val dragThreshold = 40f
                            if (abs(totalX) > abs(totalY) && totalX > dragThreshold) {
                                change.consume()
                                triggered = true
                                onSwipeLeft()
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSwipeLeft() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Swipe Left",
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        if (showRight) {
             Box(
                modifier = Modifier
                    .align(sideArrowAlignmentRight)
                    .size(touchSize)
                    .pointerInput(Unit) {
                        if (!swipeEnabled) return@pointerInput
                        var totalX = 0f
                        var totalY = 0f
                        var triggered = false
                        detectDragGestures(
                            onDragStart = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            },
                            onDragEnd = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            },
                            onDragCancel = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            }
                        ) { change, dragAmount ->
                            if (triggered) return@detectDragGestures
                            totalX += dragAmount.x
                            totalY += dragAmount.y
                            val dragThreshold = 40f
                            if (abs(totalX) > abs(totalY) && totalX < -dragThreshold) {
                                change.consume()
                                triggered = true
                                onSwipeRight()
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSwipeRight() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Swipe Right",
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        if (showUp) {
            val upIndicatorAtBottom = upIndicatorPointsDown
            val upIndicatorAlignment = when {
                upIndicatorAtBottom && isVertical -> Alignment.BottomCenter
                upIndicatorAtBottom -> landscapeDownAlignment
                isVertical -> Alignment.TopCenter
                else -> landscapeUpAlignment
            }
            val upIndicatorTouchSize = if (upIndicatorAtBottom) touchSize else resolvedUpTouchSize
            Box(
                modifier = Modifier
                    .align(upIndicatorAlignment)
                    .then(
                        if (upIndicatorAtBottom && isVertical) {
                            Modifier.padding(bottom = resolvedDownBottomPadding)
                        } else {
                            Modifier
                        }
                    )
                    .size(upIndicatorTouchSize)
                    .pointerInput(Unit) {
                        if (!swipeEnabled) return@pointerInput
                        var totalX = 0f
                        var totalY = 0f
                        var triggered = false
                        detectDragGestures(
                            onDragStart = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            },
                            onDragEnd = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            },
                            onDragCancel = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            }
                        ) { change, dragAmount ->
                            if (triggered) return@detectDragGestures
                            totalX += dragAmount.x
                            totalY += dragAmount.y
                            val dragThreshold = 40f
                            if (abs(totalY) > abs(totalX) && abs(totalY) > dragThreshold) {
                                change.consume()
                                triggered = true
                                onSwipeUp()
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSwipeUp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (upIndicatorPointsDown) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.Default.KeyboardArrowUp
                    },
                    contentDescription = if (upIndicatorPointsDown) "Back" else "Swipe Up",
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        if (showDown) {
            Box(
                modifier = Modifier
                    .align(if (isVertical) Alignment.BottomCenter else landscapeDownAlignment)
                    .then(if (isVertical) Modifier.padding(bottom = resolvedDownBottomPadding) else Modifier)
                    .size(touchSize)
                    .pointerInput(Unit) {
                        if (!swipeEnabled) return@pointerInput
                        var totalX = 0f
                        var totalY = 0f
                        var triggered = false
                        detectDragGestures(
                            onDragStart = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            },
                            onDragEnd = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            },
                            onDragCancel = {
                                totalX = 0f
                                totalY = 0f
                                triggered = false
                            }
                        ) { change, dragAmount ->
                            if (triggered) return@detectDragGestures
                            totalX += dragAmount.x
                            totalY += dragAmount.y
                            val dragThreshold = 40f
                            if (abs(totalY) > abs(totalX) && abs(totalY) > dragThreshold) {
                                change.consume()
                                triggered = true
                                onSwipeDown()
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSwipeDown() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Swipe Down",
                    tint = iconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/**
 * Observes swipe navigation in [PointerEventPass.Initial] so drags that start on buttons or
 * other interactives still reach navigation handlers. Pointer events are only consumed after a
 * swipe crosses [thresholdPx], which avoids accidental button presses during swipes.
 *
 * [isBlocked] is polled on every pointer move of the current gesture. Once it reports true (e.g.
 * an item drag/reorder has started elsewhere in the same list), navigation is latched off for the
 * remainder of that gesture - even if [isBlocked] flips back to false mid-touch - so a single
 * continuous finger-down can never be reinterpreted as a back-swipe after it started a different
 * gesture. This only affects the gesture in progress; once the finger lifts, the next touch is
 * evaluated fresh.
 */
private fun Modifier.observeSwipeNavigationGestures(
    enabled: Boolean,
    scrollState: ScrollState? = null,
    lazyListState: LazyListState? = null,
    thresholdPx: Float = 80f,
    isBlocked: () -> Boolean = { false },
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
): Modifier {
    if (!enabled) return this
    val hasUp = onSwipeUp != null
    val hasDown = onSwipeDown != null
    val hasLeft = onSwipeLeft != null
    val hasRight = onSwipeRight != null
    if (!hasUp && !hasDown && !hasLeft && !hasRight) return this

    // Down-swipe hub (settings): allow with scroll when no up handler. Sub-pages use up-only back.
    val canClaimDownSwipe = hasDown && lazyListState == null && (scrollState == null || !hasUp)

    return pointerInput(scrollState, lazyListState, thresholdPx) {
        fun atScrollTop(): Boolean {
            scrollState?.let { return it.value == 0 }
            lazyListState?.let {
                return it.firstVisibleItemIndex == 0 && it.firstVisibleItemScrollOffset == 0
            }
            return true
        }

        val touchSlop = viewConfiguration.touchSlop.toFloat()

        awaitEachGesture {
            // Main pass lets Button/clickable win the down event; Initial runs parent-first.
            val down = awaitFirstDown(
                pass = PointerEventPass.Initial,
                requireUnconsumed = false,
            )
            val pointerId = down.id
            var totalX = 0f
            var totalY = 0f
            var triggered = false
            var claimGesture = false
            var navigationBlocked = isBlocked()

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || !change.pressed) break

                if (!navigationBlocked && isBlocked()) {
                    // Another gesture (e.g. long-press drag reorder) claimed this touch.
                    // Latch off for the rest of this finger-down so it can't fall back to
                    // triggering navigation once that other gesture ends/cancels.
                    navigationBlocked = true
                }

                if (!triggered) {
                    if (navigationBlocked) {
                        totalX = 0f
                        totalY = 0f
                        claimGesture = false
                    } else if (!atScrollTop()) {
                        totalX = 0f
                        totalY = 0f
                        claimGesture = false
                    } else {
                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y
                        val absX = abs(totalX)
                        val absY = abs(totalY)

                        if (!claimGesture) {
                            claimGesture = when {
                                hasUp && totalY < -touchSlop && absY > absX -> true
                                canClaimDownSwipe && totalY > touchSlop && absY > absX -> true
                                hasLeft && totalX > touchSlop && absX > absY -> true
                                hasRight && totalX < -touchSlop && absX > absY -> true
                                else -> false
                            }
                        }

                        if (claimGesture) {
                            change.consume()
                        }

                        when {
                            hasUp && totalY < -thresholdPx && absY > absX -> {
                                triggered = true
                                onSwipeUp?.invoke()
                            }
                            hasDown && totalY > thresholdPx && absY > absX -> {
                                triggered = true
                                onSwipeDown?.invoke()
                            }
                            hasLeft && totalX > thresholdPx && absX > absY -> {
                                triggered = true
                                onSwipeLeft?.invoke()
                            }
                            hasRight && totalX < -thresholdPx && absX > absY -> {
                                triggered = true
                                onSwipeRight?.invoke()
                            }
                        }
                    }

                    if (triggered) {
                        event.changes.forEach { it.consume() }
                        break
                    }
                }
            }
        }
    }
}

/**
 * Upward swipe to go back when scroll is at the top. Works over buttons and other interactives.
 *
 * [isBlocked] can report that a different gesture (e.g. a long-press item drag used for
 * reordering) has started within the same list; while true, this back-swipe is disabled for the
 * remainder of the current touch, see [observeSwipeNavigationGestures].
 */
fun Modifier.swipeUpBackGesture(
    enabled: Boolean,
    scrollState: ScrollState? = null,
    lazyListState: LazyListState? = null,
    thresholdPx: Float = 80f,
    isBlocked: () -> Boolean = { false },
    onBack: () -> Unit,
): Modifier = observeSwipeNavigationGestures(
    enabled = enabled,
    scrollState = scrollState,
    lazyListState = lazyListState,
    thresholdPx = thresholdPx,
    isBlocked = isBlocked,
    onSwipeUp = onBack,
)

/**
 * Hub gestures for the main settings screen: swipe down opens last sub-page; horizontal swipes
 * use the same thresholds and pointer handling as [swipeUpBackGesture].
 */
fun Modifier.settingsHubSwipeGesture(
    enabled: Boolean,
    scrollState: ScrollState? = null,
    thresholdPx: Float = 80f,
    onSwipeDown: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = observeSwipeNavigationGestures(
    enabled = enabled,
    scrollState = scrollState,
    thresholdPx = thresholdPx,
    onSwipeDown = onSwipeDown,
    onSwipeLeft = onSwipeLeft,
    onSwipeRight = onSwipeRight,
)

/**
 * Verticaal gecentreerde omhoog/omlaag-pijltjes rechts op het scherm om een LazyColumn met
 * userScrollEnabled = false stapsgewijs te scrollen. Los van [SwipeIndicators] (dat is voor
 * swipe-navigatie TUSSEN schermen, dit is voor scrollen BINNEN een lijst).
 * Toont niets als de lijst toch al volledig past (geen scroll mogelijk in beide richtingen).
 */
@Composable
fun VerticalScrollArrows(
    listState: LazyListState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier,
    tint: Color = Color.Gray,
    // Overschrijft de ingebouwde canScrollForward-check. Nodig voor lijsten met grote
    // contentPadding onderaan (bv. layout-clearance) die canScrollForward anders laat
    // denken dat er nog inhoud is terwijl het laatste item al volledig zichtbaar is.
    overrideCanScrollDown: Boolean? = null
) {
    val canScrollUp = listState.canScrollBackward
    val canScrollDown = overrideCanScrollDown ?: listState.canScrollForward

    if (!canScrollUp && !canScrollDown) return

    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var targetIndex by remember { mutableIntStateOf(-1) }

    val scrollBy: (Boolean) -> Unit = { up ->
        val maxIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        val base = if (scrollJob?.isActive == true && targetIndex in 0..maxIndex) {
            targetIndex
        } else {
            listState.firstVisibleItemIndex
        }
        targetIndex = (base + if (up) -1 else 1).coerceIn(0, maxIndex)
        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            listState.animateScrollToItem(targetIndex)
        }
    }

    // Beide knoppen blijven altijd aanwezig (vaste layout-hoogte/positie) - alleen
    // alpha/enabled wisselen per richting, zodat het paar nooit verspringt tijdens scrollen.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            enabled = canScrollUp,
            onClick = { scrollBy(true) },
            modifier = Modifier.alpha(if (canScrollUp) 1f else 0f)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Scroll omhoog",
                tint = tint,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(36.dp))
        IconButton(
            enabled = canScrollDown,
            onClick = { scrollBy(false) },
            modifier = Modifier.alpha(if (canScrollDown) 1f else 0f)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll omlaag",
                tint = tint,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

data class RingtoneInfo(val uri: Uri, val title: String, val isCustom: Boolean = false)

@Composable
fun RingtonePickerDialog(
    onDismissRequest: () -> Unit,
    onRingtoneSelected: (Uri?) -> Unit,
    currentUriString: String?,
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color,
    onAddCustomSound: (() -> Unit)? = null,
    // DD Music: optioneel item bovenaan de lijst waarmee DD Music i.p.v. een normaal
    // alarmgeluid gebruikt kan worden. onUseDdMusic is null = DD Music niet beschikbaar/
    // niet van toepassing (bv. onbekend of geïnstalleerd), dan blijft dit item verborgen.
    ddMusicLinked: Boolean = false,
    ddMusicSummary: String? = null,
    onUseDdMusic: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val ringtoneManager = RingtoneManager(context)
    ringtoneManager.setType(RingtoneManager.TYPE_ALARM)
    val cursor = ringtoneManager.cursor
    // Bundled sound is now the app-wide default/fallback (replaces the OS default alarm sound).
    // The OS default still shows up further down as a normal, separately selectable system ringtone.
    val defaultUri = Uri.parse(com.dd.daykit.SettingsManager.DEFAULT_ALARM_SOUND_URI)
    val defaultRingtoneInfo = RingtoneInfo(defaultUri, LanguageManager.getString("default_alarm_sound"))
    val currentLanguage by LanguageManager.currentLanguage
    
    // Load custom sounds
    val customSoundManager = remember { com.dd.daykit.CustomSoundManager(context) }
    var customSounds by remember { mutableStateOf<List<com.dd.daykit.database.CustomAlarmSound>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        customSounds = customSoundManager.getAllSounds()
    }

    val ringtones = remember(currentLanguage, customSounds) {
        buildList {
            // Eigen geluiden eerst/bovenaan — dat zijn de geluiden die het vaakst gekozen worden,
            // dus die hoeven niet onder de hele systeemlijst weggestopt te blijven.
            customSounds.forEach { customSound ->
                val uri = customSoundManager.getUriForSound(customSound)
                add(RingtoneInfo(uri, customSound.displayName, isCustom = true))
            }
            add(defaultRingtoneInfo)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val uri = ringtoneManager.getRingtoneUri(cursor.position)
                    add(RingtoneInfo(uri, title))
                }
            }
        }
    }

    var selectedUri by remember { mutableStateOf(currentUriString?.let { Uri.parse(it) } ?: defaultUri) }
    // Lokale, wederzijds-exclusieve selectie-state: precies één van (normaal geluid / DD Music)
    // is ooit "geselecteerd" getoond. Start gelijk aan de binnengekomen ddMusicLinked-prop; een
    // tik op een normaal geluid schakelt 'm lokaal uit. Tikken op DD Music zet 'm bewust NIET
    // meteen op true - dat gebeurt pas op een volgende keer openen, ná bevestiging door DD Music.
    //
    // Geket op ddMusicLinked (i.p.v. kaal remember): deze dialoog blijft namelijk gewoon
    // openstaan terwijl DD Music op de voorgrond staat (showRingtoneDialog wordt niet gesloten
    // bij het tikken op "Gebruik DD Music"). Zonder deze key bleef ddMusicSelected na terugkeer
    // vastzitten op de oude waarde van vóór de DD Music-keuze totdat het hele scherm opnieuw
    // geopend werd - de koppeling "kwam niet direct zichtbaar door". Met deze key herstart de
    // lokale state zodra de prop van buitenaf verandert (bv. via reloadDdMusicState() ná resume),
    // terwijl een lokale tik binnen dezelfde compositie gewoon blijft werken.
    var ddMusicSelected by remember(ddMusicLinked) { mutableStateOf(ddMusicLinked) }

    // Sound preview state - preview does NOT change selection
    var previewingUri by remember { mutableStateOf<Uri?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    
    // Stop preview function
    val stopPreview = {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) { /* already stopped */ }
        }
        mediaPlayer = null
        previewingUri = null
    }
    
    // Play preview function - does NOT change selection
    val playPreview: (Uri) -> Unit = { uri ->
        if (previewingUri == uri) {
            // Stop if already playing this one
            stopPreview()
        } else {
            // Stop current and play new
            stopPreview()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setOnCompletionListener { previewingUri = null }
                    setOnErrorListener { _, _, _ -> previewingUri = null; true }
                    prepare()
                    start()
                }
                previewingUri = uri
            } catch (e: Exception) {
                previewingUri = null
            }
        }
    }
    
    // Cleanup MediaPlayer on dismiss
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    
    // Stop preview when dialog closes
    val handleDismiss = {
        stopPreview()
        onDismissRequest()
    }
    
    val handleConfirm = {
        stopPreview()
        if (!ddMusicSelected) {
            onRingtoneSelected(if (selectedUri == defaultUri) null else selectedUri)
        } else {
            // DD Music blijft de actieve keuze (niks aan normale geluiden gewijzigd) - niks
            // om op te slaan hier, dat is al (of wordt nog) via DD Music's bevestiging geregeld.
            onDismissRequest()
        }
    }

    AlertDialog(
        onDismissRequest = handleDismiss,
        containerColor = containerColor,
        title = { Text(LanguageManager.getString("choose_alarm_sound"), color = textColor) },
        text = {
            LazyColumn {
                if (onUseDdMusic != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUseDdMusic() }
                                .background(
                                    if (ddMusicSelected) buttonColor.copy(alpha = 0.15f)
                                    else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = ddMusicSelected,
                                onClick = onUseDdMusic,
                                colors = RadioButtonDefaults.colors(selectedColor = buttonColor, unselectedColor = textColor.copy(alpha = 0.6f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (ddMusicSelected) (ddMusicSummary ?: "DD Music") else "Gebruik DD Music Als Alarm",
                                    color = if (ddMusicSelected) buttonColor else textColor,
                                    fontWeight = if (ddMusicSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (ddMusicSelected) {
                                    Text(
                                        text = "Tik om te wijzigen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                items(ringtones) { ringtoneInfo ->
                    // !ddMusicSelected: zodra DD Music (nog) actief is, mag geen enkel normaal
                    // geluid ook als geselecteerd getoond worden - precies 1 keuze tegelijk.
                    val isSelected = !ddMusicSelected && ringtoneInfo.uri == selectedUri
                    val isPreviewing = previewingUri == ringtoneInfo.uri
                    val selectThisSound = {
                        selectedUri = ringtoneInfo.uri
                        ddMusicSelected = false
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = selectThisSound)
                            .background(
                                if (isSelected) buttonColor.copy(alpha = 0.15f)
                                else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = selectThisSound,
                            colors = RadioButtonDefaults.colors(selectedColor = buttonColor, unselectedColor = textColor.copy(alpha = 0.6f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ringtoneInfo.title,
                                color = if (isSelected) buttonColor else textColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (ringtoneInfo.isCustom) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Custom geluid",
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFFFFD700) // Goud
                                    )
                                    Text(
                                        text = "Eigen geluid",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        // Verwijder-knop (X) links van de play-knop - alleen voor eigen geluiden.
                        if (ringtoneInfo.isCustom) {
                            IconButton(
                                onClick = {
                                    // Stop preview if playing this sound
                                    if (isPreviewing) stopPreview()
                                    // Find and delete the custom sound
                                    val customSound = customSounds.find {
                                        customSoundManager.getUriForSound(it) == ringtoneInfo.uri
                                    }
                                    customSound?.let { sound ->
                                        coroutineScope.launch {
                                            customSoundManager.deleteSound(sound)
                                            // Refresh the list
                                            customSounds = customSoundManager.getAllSounds()
                                            // If deleted sound was selected, reset to default
                                            if (selectedUri == ringtoneInfo.uri) {
                                                selectedUri = defaultUri
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Verwijderen",
                                    tint = Color.Red.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Play/Stop icon button
                        IconButton(
                            onClick = { playPreview(ringtoneInfo.uri) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewing) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = if (isPreviewing) "Stop" else "Play",
                                tint = if (isPreviewing) Color.Red else textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // Add custom sound button at bottom
                if (onAddCustomSound != null) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onAddCustomSound,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(LanguageManager.getString("add_custom_sound"))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = handleConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
            ) { Text(LanguageManager.getString("ok")) }
        },
        dismissButton = {
            Button(
                onClick = handleDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
            ) { Text(LanguageManager.getString("cancel")) }
        }
    )
}

@Composable
fun ClockView(
    layout: String,
    timeToNextAlarm: String?,
    nextAlarmLabel: String?,
    currentTime: String,
    textColor: Color,
    textAlign: TextAlign,
    countdownSecondsMode: String
) {
    Column(
        horizontalAlignment = when (textAlign) {
            TextAlign.Start -> Alignment.Start
            TextAlign.End -> Alignment.End
            else -> Alignment.CenterHorizontally
        }
    ) {
        if (layout == "COUNTDOWN_TOP" || layout == "COUNTDOWN_ONLY") {
            if (timeToNextAlarm != null) {
                Text(
                    text = timeToNextAlarm,
                    style = MaterialTheme.typography.displayLarge,
                    color = textColor,
                    textAlign = textAlign
                )
                if (nextAlarmLabel != null && nextAlarmLabel.isNotEmpty()) {
                    Text(
                        text = nextAlarmLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = textAlign
                    )
                }
            }
        }
        
        if (layout != "COUNTDOWN_ONLY") {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayMedium,
                color = textColor,
                textAlign = textAlign
            )
        }
        
        if (layout == "COUNTDOWN_BOTTOM") {
            if (timeToNextAlarm != null) {
                Text(
                    text = timeToNextAlarm,
                    style = MaterialTheme.typography.displayLarge,
                    color = textColor,
                    textAlign = textAlign
                )
                if (nextAlarmLabel != null && nextAlarmLabel.isNotEmpty()) {
                    Text(
                        text = nextAlarmLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = textAlign
                    )
                }
            }
        }
    }
}

@Composable
fun NumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { if (value > range.first) onValueChange(value - 1) else onValueChange(range.last) }) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrease",
                tint = textStyle.color
            )
        }
        Text(
            text = value.toString().padStart(2, '0'),
            style = textStyle,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = { if (value < range.last) onValueChange(value + 1) else onValueChange(range.first) }) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increase",
                tint = textStyle.color
            )
        }
    }
}
