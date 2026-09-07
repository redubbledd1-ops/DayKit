package com.dd.daykit

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.SwipeIndicators
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dd.daykit.ui.SettingsScreenTemplate
import com.dd.daykit.ui.NavigationBar // Added import for NavigationBar
import com.dd.daykit.ui.swipeUpBackGesture
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ComponentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        setContent {
            MaterialTheme {
                ComponentsScreen { finish() }
            }
        }
    }
}

@Composable
fun ComponentsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isVertical = configuration.screenHeightDp > configuration.screenWidthDp
    val textColor = Color(SettingsManager.getTextColor(context))
    val backgroundColor = Color(SettingsManager.getBackgroundColor(context))
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)

    // New: Get showNavButtons state and observe changes
    var showNavButtons by remember { mutableStateOf(SettingsManager.getShowNavButtons(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.dd.daykit.DESIGN_UPDATED") {
                    showNavButtons = SettingsManager.getShowNavButtons(context)
                }
            }
        }
        val filter = IntentFilter("com.dd.daykit.DESIGN_UPDATED")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    var screenOrder by remember { mutableStateOf(SettingsManager.getScreenOrder(context)) }
    var enabledScreens by remember { mutableStateOf(SettingsManager.getEnabledScreens(context)) }
    var startScreenId by remember { mutableStateOf(SettingsManager.getStartScreenId(context)) }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var overscrollJob by remember { mutableStateOf<Job?>(null) }
    
    val dragDropState = remember {
        DragDropState(lazyListState) { fromIndex, toIndex ->
            val newList = screenOrder.toMutableList().apply {
                // Adjust for the header item in the list
                val actualFrom = fromIndex - 1
                val actualTo = toIndex - 1
                if (actualFrom >= 0 && actualTo >= 0 && actualFrom < size && actualTo < size) {
                    add(actualTo, removeAt(actualFrom))
                }
            }
            screenOrder = newList
        }
    }

    val navigateBackToSettings: () -> Unit = {
        android.util.Log.d("SettingsNavRestore", "componentsSwipeBack triggered")
        context.startActivity(Intent(context, SettingsActivity::class.java))
        (context as? Activity)?.finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .swipeUpBackGesture(
                enabled = swipeEnabled,
                lazyListState = lazyListState,
                isBlocked = { dragDropState.draggingItemIndex != null },
                onBack = navigateBackToSettings,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding() // Use safeDrawingPadding for edge-to-edge
        ) {
            SettingsScreenTemplate(
                title = LanguageManager.getString("nav_components"),
                onBack = onBack,
                showBackArrow = false,
                showTitle = false,
                onSave = {
                    SettingsManager.saveScreenOrder(context, screenOrder)
                    val finalEnabled = enabledScreens.toMutableSet().apply { add("SETTINGS") }
                    SettingsManager.saveEnabledScreens(context, finalEnabled)
                    SettingsManager.saveStartScreenId(context, startScreenId)

                    context.sendBroadcast(
                        Intent("com.dd.daykit.DESIGN_UPDATED")
                            .setPackage(context.packageName)
                    )

                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                    (context as? Activity)?.finish()
                },
                lazyListState = lazyListState,
                lazyListModifier = Modifier.pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, offset ->
                            change.consume()
                            dragDropState.onDrag(offset)

                            if (overscrollJob?.isActive == true) return@detectDragGesturesAfterLongPress

                            dragDropState.checkForOverScroll()
                                .takeIf { it != 0f }
                                ?.let { scrollAmount ->
                                    overscrollJob = coroutineScope.launch {
                                        lazyListState.scrollBy(scrollAmount)
                                    }
                                }
                                ?: run { overscrollJob?.cancel() }
                        },
                        onDragStart = { offset -> dragDropState.onDragStart(offset) },
                        onDragEnd = { dragDropState.onDragInterrupted() },
                        onDragCancel = { dragDropState.onDragInterrupted() }
                    )
                },
                modifier = Modifier.weight(1f) // Make SettingsScreenTemplate fill remaining space
            ) {
                item {
                    Text(LanguageManager.getString("screen_order_visibility"), style = MaterialTheme.typography.titleLarge, color = textColor)
                    Spacer(Modifier.height(16.dp))
                }

                itemsIndexed(screenOrder, key = { _, item -> item }) { index, screenId ->
                    val absoluteIndex = index + 1 // Account for header
                    val isBeingDragged = absoluteIndex == dragDropState.draggingItemIndex
                    val isSettings = screenId == "SETTINGS"
                    val screen = Screen.values().find { it.id == screenId }
                    val displayName = screen?.titleKey?.let { LanguageManager.getString(it) } ?: screenId

                    val draggingModifier = if (isBeingDragged) {
                        Modifier
                            .graphicsLayer {
                                translationY = dragDropState.draggingItemOffset
                            }
                            .background(Color.Gray.copy(alpha = 0.5f))
                    } else {
                        Modifier
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(draggingModifier),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSettings || enabledScreens.contains(screenId),
                            onCheckedChange = { isChecked ->
                                if (!isSettings) {
                                    val newEnabled = enabledScreens.toMutableSet()
                                    if (isChecked) newEnabled.add(screenId) else newEnabled.remove(screenId)
                                    enabledScreens = newEnabled
                                    if (!isChecked && startScreenId == screenId) {
                                        startScreenId = "SETTINGS"
                                    }
                                }
                            },
                            enabled = !isSettings,
                            colors = CheckboxDefaults.colors(
                                checkedColor = textColor,
                                uncheckedColor = textColor.copy(alpha = 0.6f),
                                checkmarkColor = Color.Black,
                                disabledCheckedColor = textColor.copy(alpha = 0.5f)
                            )
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(displayName, color = textColor, style = MaterialTheme.typography.bodyLarge)
                            if (screenId == startScreenId) {
                                Text(
                                    LanguageManager.getString("start_screen_indicator"),
                                    color = textColor.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        RadioButton(
                            selected = (screenId == startScreenId),
                            onClick = {
                                if (isSettings || enabledScreens.contains(screenId)) {
                                    startScreenId = screenId
                                }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = textColor, unselectedColor = textColor.copy(alpha = 0.6f))
                        )
                    }
                }
            }

            // Conditionally display the NavigationBar at the bottom
            if (showNavButtons) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .align(Alignment.CenterHorizontally)
                ) {
                    NavigationBar(currentPage = "SETTINGS")
                }
            }
        }

        SwipeIndicators(
            isVertical = isVertical,
            showUp = true,
            upIndicatorPointsDown = true,
            upTouchSize = 120.dp,
            onSwipeUp = navigateBackToSettings,
        )
    }
}


private val LazyListItemInfo.offsetEnd: Int
    get() = this.offset + this.size

@Stable
class DragDropState(
    val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemInitialOffset by mutableStateOf(0)
    var draggingItemOffset by mutableStateOf(0f)
        private set

    private val currentDraggingItemInfo: LazyListItemInfo?
        get() = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingItemIndex }

    fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.also {
                draggingItemIndex = it.index
                draggingItemInitialOffset = it.offset
            }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemOffset = 0f
    }

    fun onDrag(offset: Offset) {
        draggingItemOffset += offset.y
        val draggingItem = currentDraggingItemInfo ?: return

        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middle = startOffset + (endOffset - startOffset) / 2f

        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.find {
            middle.toInt() in it.offset..it.offsetEnd && draggingItem.index != it.index
        }

        if (targetItem != null) {
            if (draggingItem.index != targetItem.index) {
                onMove(draggingItem.index, targetItem.index)
                draggingItemIndex = targetItem.index
                draggingItemOffset = 0f
            }
        }
    }
    
    fun checkForOverScroll(): Float {
        val draggingItem = currentDraggingItemInfo ?: return 0f
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size

        val viewportStartOffset = lazyListState.layoutInfo.viewportStartOffset
        val viewportEndOffset = lazyListState.layoutInfo.viewportEndOffset

        return when {
            draggingItemOffset > 0 -> (endOffset - viewportEndOffset + 50f).coerceAtLeast(0f)
            draggingItemOffset < 0 -> (startOffset - viewportStartOffset - 50f).coerceAtMost(0f)
            else -> 0f
        }
    }
}