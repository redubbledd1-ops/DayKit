package com.dd.daykit

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.VerticalScrollArrows
import java.text.DecimalFormat
import kotlin.math.abs
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

class CalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)
        
        // Initialize ALL state holders for proper state restoration on app restart
        TimerSettingsStateHolder.init(this)
        GlobalTimerManager.init(this)
        StopwatchStateHolder.init(this)
        GlobalInAppMessageManager.init(this)

        setContent {
            MaterialTheme {
                CalculatorScreen()
            }
        }
    }
}

@Composable
fun CalculatorScreen() {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var result by rememberSaveable { mutableStateOf("") }
    val sessionHistory = rememberSaveable(
        saver = listSaver<SnapshotStateList<Pair<String, String>>, String>(
            save = { history -> history.flatMap { listOf(it.first, it.second) } },
            restore = { restored ->
                mutableStateListOf<Pair<String, String>>().apply {
                    restored.chunked(2).forEach { pair ->
                        if (pair.size == 2) add(pair[0] to pair[1])
                    }
                }
            }
        )
    ) { mutableStateListOf<Pair<String, String>>() }

    val configuration = LocalConfiguration.current
    val isVerticalMode = configuration.screenHeightDp > configuration.screenWidthDp
    if (isVerticalMode) {
        CalculatorVertical(
            textFieldValue = textFieldValue,
            onTextFieldValueChange = { textFieldValue = it },
            result = result,
            onResultChange = { result = it },
            sessionHistory = sessionHistory
        )
    } else {
        CalculatorHorizontal(
            textFieldValue = textFieldValue,
            onTextFieldValueChange = { textFieldValue = it },
            result = result,
            onResultChange = { result = it },
            sessionHistory = sessionHistory
        )
    }
}

@Composable
fun CalculatorHorizontal(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    result: String,
    onResultChange: (String) -> Unit,
    sessionHistory: SnapshotStateList<Pair<String, String>>
) {
    val context = LocalContext.current
    val currentScreenId = "CALCULATOR"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val showNavButtons = SettingsManager.getShowNavButtons(context)

    // Define custom color scheme based on user settings
    val customColorScheme = MaterialTheme.colorScheme.copy(
        primary = textColor,
        onPrimary = buttonTextColor,
        // Ensure other colors that might use purple are overridden if necessary
        // outlineVariant used for selection handles sometimes
    )
    
    var showHistory by remember { mutableStateOf(false) }
    var historyBounds by remember { mutableStateOf<Rect?>(null) }

    BackHandler(enabled = showHistory) {
        showHistory = false
    }

    fun updateLiveResult() {
        val input = textFieldValue.text
        if (input.isBlank()) {
            onResultChange("")
            return
        }
        val eval = evaluateExpression(input, incompleteAsError = false)
        onResultChange(if (!eval.isNaN()) formatResult(eval) else "")
    }

    fun onButtonClick(symbol: String) {
        if (result == "Error" && symbol != "Wis") {
            return
        }

        val currentText = textFieldValue.text
        val selection = textFieldValue.selection

        when (symbol) {
            "Wis" -> {
                onTextFieldValueChange(TextFieldValue(""))
                onResultChange("")
                sessionHistory.clear()
            }
            "⌫" -> {
                if (currentText.isNotEmpty()) {
                    // Remove character before cursor or selected text
                    if (selection.collapsed) {
                        if (selection.start > 0) {
                            val newText = currentText.removeRange(selection.start - 1, selection.start)
                            val newCursor = selection.start - 1
                            onTextFieldValueChange(TextFieldValue(newText, TextRange(newCursor)))
                            updateLiveResult()
                        }
                    } else {
                        // Remove selection
                        val newText = currentText.removeRange(selection.start, selection.end)
                        val newCursor = selection.start
                        onTextFieldValueChange(TextFieldValue(newText, TextRange(newCursor)))
                        updateLiveResult()
                    }
                }
            }
            "=" -> {
                if (currentText.isNotEmpty()) {
                    val finalEval = evaluateExpression(currentText, incompleteAsError = true)
                    val finalResultString = formatResult(finalEval)

                    if (finalResultString != "Error") {
                        if (sessionHistory.none { it.first == currentText && it.second == finalResultString }) {
                           sessionHistory.add(Pair(currentText, finalResultString))
                        }
                        val newText = finalResultString.replace(",", ".")
                        onTextFieldValueChange(TextFieldValue(newText, TextRange(newText.length)))
                        onResultChange("")
                    } else {
                        onResultChange("Error")
                    }
                }
            }
            "Save" -> {
                 if (sessionHistory.isNotEmpty() || (currentText.isNotEmpty() && result.isNotEmpty() && result != "Error")) {
                    val steps = sessionHistory.map { CalculatorStep(it.first, it.second) }.toMutableList()
                    if (currentText.isNotEmpty() && result.isNotEmpty() && result != "Error") {
                        steps.add(CalculatorStep(currentText, result))
                    }
                    if (steps.isNotEmpty()) {
                        SettingsManager.saveCalculatorHistoryEntry(context, steps)
                    }
                }
            }
            "()" -> {
                // Logic adapted to insert at cursor
                val cursor = selection.start
                val charBefore = if (cursor > 0) currentText[cursor - 1] else null
                
                val openBrackets = currentText.count { it == '(' }
                val closeBrackets = currentText.count { it == ')' }
                
                val toInsert = if (charBefore != null && (charBefore.isDigit() || charBefore == ')')) {
                     if (openBrackets > closeBrackets) ")" else "(" 
                } else {
                    "("
                }
                
                val newText = currentText.replaceRange(selection.start, selection.end, toInsert)
                val newCursor = selection.start + toInsert.length
                onTextFieldValueChange(TextFieldValue(newText, TextRange(newCursor)))
                updateLiveResult()
            }
            else -> {
                val newText = currentText.replaceRange(selection.start, selection.end, symbol)
                val newCursor = selection.start + symbol.length
                onTextFieldValueChange(TextFieldValue(newText, TextRange(newCursor)))
                updateLiveResult()
            }
        }
    }

    // Apply custom theme to override default purple handles
    MaterialTheme(colorScheme = customColorScheme) {
        if (showHistory) {
            CalculatorHistoryScreen(
                onBack = { showHistory = false },
                onSelectEntry = { steps ->
                    if (steps.isNotEmpty()) {
                        val lastValidStep = steps.lastOrNull { it.result != "Error" } ?: return@CalculatorHistoryScreen
                        val newText = lastValidStep.expression.replace(",", ".")
                        onTextFieldValueChange(TextFieldValue(newText, TextRange(newText.length)))
                        onResultChange(lastValidStep.result)
                        sessionHistory.clear()
                        sessionHistory.addAll(steps.map { Pair(it.expression, it.result) })
                        showHistory = false
                        updateLiveResult()
                    }
                }
            )
        } else {
            AppBackground {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                                val startedInHistory = historyBounds?.contains(down.position) == true
                                if (startedInHistory) {
                                    while (true) {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull()
                                        if (change == null || !change.pressed) break
                                    }
                                    return@awaitEachGesture
                                }
                                var dragY = 0f
                                var dragX = 0f
                                var triggered = false

                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull()
                                    if (change == null || !change.pressed) break

                                    if (!triggered) {
                                        dragX += change.position.x - change.previousPosition.x
                                        dragY += change.position.y - change.previousPosition.y
                                        val dragThreshold = 50f

                                        if (abs(dragX) > abs(dragY)) {
                                            if (dragX > dragThreshold) {
                                                NavigationManager.navigateLeft(context, currentScreenId)
                                                triggered = true
                                            } else if (dragX < -dragThreshold) {
                                                NavigationManager.navigateRight(context, currentScreenId)
                                                triggered = true
                                            }
                                        } else {
                                            if (dragY < -dragThreshold) {
                                                showHistory = true
                                                triggered = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val leftTarget = NavigationManager.getSwipeLeftTarget(context, currentScreenId)
                    val rightTarget = NavigationManager.getSwipeRightTarget(context, currentScreenId)

                    // Keep swipe arrows behind calculator controls so keypad taps always win.
                    SwipeIndicators(
                        isVertical = false,
                        showLeft = leftTarget != null,
                        showRight = rightTarget != null,
                        showDown = true,
                        onSwipeLeft = { NavigationManager.navigateLeft(context, currentScreenId) },
                        onSwipeRight = { NavigationManager.navigateRight(context, currentScreenId) },
                        onSwipeDown = { showHistory = true }
                    )

                    val inputScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(bottom = if (showNavButtons) 64.dp else 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 68.dp)
                                    .verticalScroll(inputScrollState),
                                horizontalAlignment = Alignment.End
                            ) {
                                val customTextSelectionColors = TextSelectionColors(
                                    handleColor = textColor,
                                    backgroundColor = textColor.copy(alpha = 0.4f)
                                )
                                CompositionLocalProvider(
                                    LocalTextInputService provides null,
                                    LocalTextSelectionColors provides customTextSelectionColors
                                ) {
                                    BasicTextField(
                                        value = textFieldValue,
                                        onValueChange = { onTextFieldValueChange(it); updateLiveResult() },
                                        textStyle = MaterialTheme.typography.displaySmall.copy(
                                            color = textColor,
                                            textAlign = TextAlign.End,
                                            lineHeight = 40.sp
                                        ),
                                        maxLines = 16,
                                        cursorBrush = SolidColor(textColor),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Text(
                                    text = if (result.isNotEmpty()) "= $result" else "",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = if (result == "Error") Color.Red else textColor.copy(alpha = 0.7f),
                                    textAlign = TextAlign.End,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val keypadRows = listOf(
                                listOf("7", "8", "9", "x", "/"),
                                listOf("4", "5", "6", "-", "⌫"),
                                listOf("1", "2", "3", "+", "Save"),
                                listOf("()", "0", ".", "=", "Wis")
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(0.76f)
                                    .zIndex(1f)
                            ) {
                                keypadRows.forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        row.forEach { symbol ->
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clip(CircleShape)
                                                    .background(buttonColor)
                                                    .clickable { onButtonClick(symbol) }
                                            ) {
                                                when (symbol) {
                                                    "Save" -> Icon(
                                                        Icons.Default.Save,
                                                        contentDescription = "Save",
                                                        tint = buttonTextColor,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    else -> {
                                                        val baseStyle = if (symbol.length > 1) {
                                                            MaterialTheme.typography.titleLarge
                                                        } else {
                                                            MaterialTheme.typography.headlineMedium
                                                        }
                                                        Text(
                                                            text = symbol,
                                                            style = baseStyle.copy(fontSize = baseStyle.fontSize * 0.75f),
                                                            color = buttonTextColor,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Clip,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .widthIn(min = 156.dp, max = 220.dp)
                                .fillMaxHeight()
                                .padding(end = 44.dp, bottom = 72.dp)
                                .onGloballyPositioned { coordinates ->
                                    historyBounds = coordinates.boundsInRoot()
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                    }
                                },
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = LanguageManager.getString("history"),
                                style = MaterialTheme.typography.titleSmall,
                                color = textColor.copy(alpha = 0.75f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(sessionHistory.asReversed()) { _, item ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = item.first,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor.copy(alpha = 0.8f),
                                            textAlign = TextAlign.End
                                        )
                                        Text(
                                            text = "= ${item.second}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor.copy(alpha = 0.8f),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showNavButtons) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .safeDrawingPadding()
                        ) {
                            NavigationBar(currentPage = currentScreenId)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorVertical(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    result: String,
    onResultChange: (String) -> Unit,
    sessionHistory: SnapshotStateList<Pair<String, String>>
) {
    val context = LocalContext.current
    val currentScreenId = "CALCULATOR"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    val showNavButtons = SettingsManager.getShowNavButtons(context)

    val customColorScheme = MaterialTheme.colorScheme.copy(
        primary = textColor,
        onPrimary = buttonTextColor,
    )

    var showHistory by remember { mutableStateOf(false) }
    var historyBounds by remember { mutableStateOf<Rect?>(null) }

    BackHandler(enabled = showHistory) {
        showHistory = false
    }

    fun updateLiveResult() {
        val input = textFieldValue.text
        if (input.isBlank()) {
            onResultChange("")
            return
        }
        val eval = evaluateExpression(input, incompleteAsError = false)
        onResultChange(if (!eval.isNaN()) formatResult(eval) else "")
    }

    fun onButtonClick(symbol: String) {
        if (result == "Error" && symbol != "Wis") {
            return
        }

        val currentText = textFieldValue.text
        val selection = textFieldValue.selection

        when (symbol) {
            "Wis" -> {
                onTextFieldValueChange(TextFieldValue(""))
                onResultChange("")
                sessionHistory.clear()
            }
            "⌫" -> {
                if (currentText.isNotEmpty()) {
                    if (selection.collapsed) {
                        if (selection.start > 0) {
                            val newText = currentText.removeRange(selection.start - 1, selection.start)
                            val newCursor = selection.start - 1
                            onTextFieldValueChange(TextFieldValue(newText, TextRange(newCursor)))
                            updateLiveResult()
                        }
                    } else {
                        val newText = currentText.removeRange(selection.start, selection.end)
                        val newCursor = selection.start
                        onTextFieldValueChange(TextFieldValue(newText, TextRange(newCursor)))
                        updateLiveResult()
                    }
                }
            }
            "=" -> {
                if (currentText.isNotEmpty()) {
                    val finalEval = evaluateExpression(currentText, incompleteAsError = true)
                    val finalResultString = formatResult(finalEval)

                    if (finalResultString != "Error") {
                        if (sessionHistory.none { it.first == currentText && it.second == finalResultString }) {
                            sessionHistory.add(Pair(currentText, finalResultString))
                        }
                        val newText = finalResultString.replace(",", ".")
                        onTextFieldValueChange(TextFieldValue(newText, TextRange(newText.length)))
                        onResultChange("")
                    } else {
                        onResultChange("Error")
                    }
                }
            }
            "Save" -> {
                if (sessionHistory.isNotEmpty() || (currentText.isNotEmpty() && result.isNotEmpty() && result != "Error")) {
                    val steps = sessionHistory.map { CalculatorStep(it.first, it.second) }.toMutableList()
                    if (currentText.isNotEmpty() && result.isNotEmpty() && result != "Error") {
                        steps.add(CalculatorStep(currentText, result))
                    }
                    if (steps.isNotEmpty()) {
                        SettingsManager.saveCalculatorHistoryEntry(context, steps)
                    }
                }
            }
            "()" -> {
                val cursor = selection.start
                val charBefore = if (cursor > 0) currentText[cursor - 1] else null

                val openBrackets = currentText.count { it == '(' }
                val closeBrackets = currentText.count { it == ')' }

                val toInsert = if (charBefore != null && (charBefore.isDigit() || charBefore == ')')) {
                    if (openBrackets > closeBrackets) ")" else "("
                } else {
                    "("
                }

                val newText = currentText.replaceRange(selection.start, selection.end, toInsert)
                val newCursor = selection.start + toInsert.length
                onTextFieldValueChange(TextFieldValue(newText, TextRange(newCursor)))
                updateLiveResult()
            }
            else -> {
                val newText = currentText.replaceRange(selection.start, selection.end, symbol)
                val newCursor = selection.start + symbol.length
                onTextFieldValueChange(TextFieldValue(newText, TextRange(newCursor)))
                updateLiveResult()
            }
        }
    }

    MaterialTheme(colorScheme = customColorScheme) {
        if (showHistory) {
            CalculatorHistoryScreen(
                onBack = { showHistory = false },
                onSelectEntry = { steps ->
                    if (steps.isNotEmpty()) {
                        val lastValidStep = steps.lastOrNull { it.result != "Error" } ?: return@CalculatorHistoryScreen
                        val newText = lastValidStep.expression.replace(",", ".")
                        onTextFieldValueChange(TextFieldValue(newText, TextRange(newText.length)))
                        onResultChange(lastValidStep.result)
                        sessionHistory.clear()
                        sessionHistory.addAll(steps.map { Pair(it.expression, it.result) })
                        showHistory = false
                        updateLiveResult()
                    }
                }
            )
        } else {
            AppBackground {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                                val startedInHistory = historyBounds?.contains(down.position) == true
                                if (startedInHistory) {
                                    while (true) {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull()
                                        if (change == null || !change.pressed) break
                                    }
                                    return@awaitEachGesture
                                }
                                var dragY = 0f
                                var dragX = 0f
                                var triggered = false

                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull()
                                    if (change == null || !change.pressed) break

                                    if (!triggered) {
                                        dragX += change.position.x - change.previousPosition.x
                                        dragY += change.position.y - change.previousPosition.y
                                        val dragThreshold = 50f

                                        if (abs(dragX) > abs(dragY)) {
                                            if (dragX > dragThreshold) {
                                                NavigationManager.navigateLeft(context, currentScreenId)
                                                triggered = true
                                            } else if (dragX < -dragThreshold) {
                                                NavigationManager.navigateRight(context, currentScreenId)
                                                triggered = true
                                            }
                                        } else {
                                            if (dragY < -dragThreshold) {
                                                showHistory = true
                                                triggered = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val leftTarget = NavigationManager.getSwipeLeftTarget(context, currentScreenId)
                    val rightTarget = NavigationManager.getSwipeRightTarget(context, currentScreenId)

                    SwipeIndicators(
                        isVertical = true,
                        showLeft = leftTarget != null,
                        showRight = rightTarget != null,
                        showDown = true,
                        onSwipeLeft = { NavigationManager.navigateLeft(context, currentScreenId) },
                        onSwipeRight = { NavigationManager.navigateRight(context, currentScreenId) },
                        onSwipeDown = { showHistory = true }
                    )

                    val inputScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(0.48f)
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .onGloballyPositioned { coordinates ->
                                    historyBounds = coordinates.boundsInRoot()
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                    }
                                },
                            reverseLayout = true,
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.End
                        ) {
                            itemsIndexed(sessionHistory.asReversed()) { _, item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "${item.first} =",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor.copy(alpha = 0.6f),
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        text = item.second,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor.copy(alpha = 0.8f),
                                        textAlign = TextAlign.End,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .verticalScroll(inputScrollState),
                            horizontalAlignment = Alignment.End
                        ) {
                            val customTextSelectionColors = TextSelectionColors(
                                handleColor = textColor,
                                backgroundColor = textColor.copy(alpha = 0.4f)
                            )
                            CompositionLocalProvider(
                                LocalTextInputService provides null,
                                LocalTextSelectionColors provides customTextSelectionColors
                            ) {
                                BasicTextField(
                                    value = textFieldValue,
                                    onValueChange = { onTextFieldValueChange(it); updateLiveResult() },
                                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                                        color = textColor,
                                        textAlign = TextAlign.End,
                                        lineHeight = 30.sp
                                    ),
                                    maxLines = 10,
                                    cursorBrush = SolidColor(textColor),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Text(
                                text = if (result.isNotEmpty()) "= $result" else "",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (result == "Error") Color.Red else textColor.copy(alpha = 0.7f),
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        val keypad = listOf(
                            listOf("Wis", "Save", "⌫", "/"),
                            listOf("7", "8", "9", "x"),
                            listOf("4", "5", "6", "-"),
                            listOf("1", "2", "3", "+"),
                            listOf("()", "0", ".", "=")
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .zIndex(1f)
                        ) {
                            keypad.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    row.forEach { symbol ->
                                        CalculatorButton(
                                            symbol = symbol,
                                            onClick = { onButtonClick(symbol) },
                                            buttonColor = buttonColor,
                                            textColor = buttonTextColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(108.dp))
                    }

                    if (showNavButtons) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .safeDrawingPadding()
                        ) {
                            NavigationBar(currentPage = currentScreenId)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorButton(
    symbol: String,
    onClick: () -> Unit,
    buttonColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable { onClick() }
    ) {
        when (symbol) {
            "Save" -> Icon(Icons.Default.Save, contentDescription = "Save", tint = textColor)
            else -> {
                val style = if (symbol.length > 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
                Text(text = symbol, style = style, color = textColor)
            }
        }
    }
}

@Composable
fun CalculatorHistoryScreen(
    onBack: () -> Unit,
    onSelectEntry: (List<CalculatorStep>) -> Unit
) {
    val context = LocalContext.current
    val currentScreenId = "CALCULATOR"
    val arrowNavigationEnabled = SettingsManager.getArrowMode(context) != "Verborgen"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val currentHistoryList = remember(refreshTrigger) { SettingsManager.getCalculatorHistory(context) }

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var itemToRenameIndex by remember { mutableStateOf(-1) }
    var newName by remember { mutableStateOf("") }
    val historyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(LanguageManager.getString("confirm_delete_title")) },
            text = { Text(LanguageManager.getString("confirm_delete_msg")) }, 
            confirmButton = {
                Button(onClick = {
                    SettingsManager.clearCalculatorHistory(context)
                    refreshTrigger++
                    showDeleteConfirmation = false
                }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)) {
                    Text(LanguageManager.getString("delete_all"))
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirmation = false }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(LanguageManager.getString("rename")) },
            text = {
                TextField(value = newName, onValueChange = { newName = it }, placeholder = { Text(LanguageManager.getString("name_placeholder")) })
            },
            confirmButton = {
                Button(onClick = {
                    if (itemToRenameIndex != -1) {
                        SettingsManager.updateCalculatorHistoryName(context, itemToRenameIndex, newName)
                        refreshTrigger++
                    }
                    showRenameDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)) {
                    Text(LanguageManager.getString("save"))
                }
            },
            dismissButton = {
                Button(onClick = { showRenameDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)) {
                    Text(LanguageManager.getString("cancel"))
                }
            }
        )
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dragThreshold = 50f
                            if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                if (dragAmount.x > dragThreshold) {
                                    NavigationManager.navigateLeft(context, currentScreenId)
                                } else if (dragAmount.x < -dragThreshold) {
                                    NavigationManager.navigateRight(context, currentScreenId)
                                }
                            } else {
                                if (dragAmount.y > dragThreshold) {
                                    onBack()
                                }
                            }
                        }
                    }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(LanguageManager.getString("calc_saved_history"), style = MaterialTheme.typography.headlineSmall, color = textColor)
                Spacer(Modifier.height(8.dp))
                if (arrowNavigationEnabled) {
                    Box(
                        modifier = Modifier.size(60.dp).clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Back Up", tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Vervangt het oude canGoUp/canGoDown IconButton-paar (dat puur door de lijst
                // van opgeslagen berekeningen scrolde via animateScrollToItem) door hetzelfde
                // VerticalScrollArrows-patroon als de andere schermen. Toont alleen hele kaarten:
                // hoogte beperkt tot een exact veelvoud van (item-hoogte + spacing) i.p.v. de
                // volledige resterende ruimte te vullen.
                val density = LocalDensity.current
                var availableHeightPx by remember { mutableStateOf(0) }
                val itemHeightPx = historyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                val itemSpacingPx = with(density) { 16.dp.toPx() }

                val lazyColumnHeightModifier = if (itemHeightPx > 0 && availableHeightPx > 0) {
                    val unitPx = itemHeightPx + itemSpacingPx
                    val whole = kotlin.math.floor(availableHeightPx / unitPx).toInt().coerceAtLeast(1)
                    Modifier.height(with(density) { (whole * unitPx).toDp() })
                } else {
                    Modifier.fillMaxHeight()
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { availableHeightPx = it.height }
                ) {
                    LazyColumn(
                        userScrollEnabled = false,
                        modifier = Modifier
                            .then(lazyColumnHeightModifier)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val dragThreshold = 50f
                                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                        if (dragAmount.x > dragThreshold) {
                                            NavigationManager.navigateLeft(context, currentScreenId)
                                        } else if (dragAmount.x < -dragThreshold) {
                                            NavigationManager.navigateRight(context, currentScreenId)
                                        }
                                    } else {
                                        if (dragAmount.y > dragThreshold) {
                                            onBack()
                                        }
                                    }
                                }
                            },
                        state = historyListState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(currentHistoryList) { index, entry ->
                            var expanded by remember { mutableStateOf(false) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dragThreshold = 50f
                                            if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                                if (dragAmount.x > dragThreshold) {
                                                    NavigationManager.navigateLeft(context, currentScreenId)
                                                } else if (dragAmount.x < -dragThreshold) {
                                                    NavigationManager.navigateRight(context, currentScreenId)
                                                }
                                            } else {
                                                if (dragAmount.y > dragThreshold) {
                                                    onBack()
                                                }
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            val displayName = if (entry.name.isNotEmpty()) entry.name else "Sessie ${currentHistoryList.size - index}"
                                            Text(
                                                text = displayName,
                                                color = textColor,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.clickable { onSelectEntry(entry.steps) } // Click on name/title to load full session
                                            )
                                            for (step in entry.steps) {
                                                Text(
                                                    text = step.expression,
                                                    color = textColor.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.clickable { onSelectEntry(entry.steps) }
                                                )
                                                Text(
                                                    text = "= ${step.result}",
                                                    color = textColor.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.clickable { onSelectEntry(entry.steps) }
                                                )
                                            }
                                        }
                                        IconButton(onClick = { expanded = !expanded }) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = textColor)
                                        }
                                    }
                                    if (expanded) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    itemToRenameIndex = index
                                                    newName = entry.name
                                                    showRenameDialog = true
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
                                            ) { Text(LanguageManager.getString("rename")) }
                                            Button(
                                                onClick = {
                                                    SettingsManager.deleteCalculatorHistoryEntry(context, index)
                                                    refreshTrigger++
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor) // Changed color to use theme
                                            ) { Text(LanguageManager.getString("delete_short")) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)) {
                            Text(LanguageManager.getString("back"))
                        }
                        Button(onClick = { showDeleteConfirmation = true }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)) {
                            Text(LanguageManager.getString("delete_all"))
                        }
                    }
                }
            }

            // canScrollForward telt de 300dp bottom-padding (layout-clearance boven
            // terug/verwijder-knoppen) mee als "nog scrollbare inhoud" - negeer dat voor de
            // knop-status: pijl uit zodra de laatste sessie-kaart zelf volledig zichtbaar is.
            val lastVisible = historyListState.layoutInfo.visibleItemsInfo.lastOrNull()
            val lastItemFullyVisible = lastVisible != null &&
                lastVisible.index == historyListState.layoutInfo.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= historyListState.layoutInfo.viewportEndOffset
            val calculatorCanScrollDown = historyListState.layoutInfo.totalItemsCount > 0 && !lastItemFullyVisible

            VerticalScrollArrows(
                listState = historyListState,
                coroutineScope = coroutineScope,
                tint = textColor.copy(alpha = 0.5f),
                overrideCanScrollDown = calculatorCanScrollDown,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 8.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
    }
}

fun evaluateExpression(expression: String, incompleteAsError: Boolean = false): Double {
    return try {
        // Automatically add multiplication operator for parentheses, e.g., "5(2)" becomes "5*(2)"
        val processed = expression.replace(Regex("(\\d|\\))\\("), "$1*(")
        // Replace all multiplication variants (x, X, ×) with *
        val normalized = processed
            .replace("x", "*")
            .replace("X", "*")
            .replace("×", "*")
            .replace(",", ".")
        val tokens = tokenize(normalized)

        // For live results, don't show an an error for incomplete expressions.
        if (!incompleteAsError) {
            if (tokens.isEmpty() || tokens.last() in setOf("+", "-", "*", "/", "%", "(")) {
                return Double.NaN // This is not an error, just an incomplete sum.
            }
        }
        
        val postfix = toPostfix(tokens)
        evaluatePostfix(postfix)
    } catch (t: Throwable) {
        // Catch any and all exceptions (like IllegalArgumentException from postfix conversion) and return NaN.
        Double.NaN
    }
}

private fun formatResult(value: Double): String {
    if (value.isInfinite() || value.isNaN()) return "Error"
    val df = DecimalFormat("#.##########")
    return df.format(value).replace(".", ",")
}

private fun tokenize(expression: String): List<String> {
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < expression.length) {
        val char = expression[i]
        when {
            char.isDigit() || char == '.' -> {
                val sb = StringBuilder()
                while (i < expression.length && (expression[i].isDigit() || expression[i] == '.')) {
                    sb.append(expression[i])
                    i++
                }
                tokens.add(sb.toString())
                continue 
            }
            char in "+-/*%()" -> tokens.add(char.toString())
        }
        i++
    }
    return tokens
}

private fun precedence(op: String): Int = when (op) {
    "+", "-" -> 1
    "*", "/" , "%" -> 2
    else -> 0
}

private fun toPostfix(tokens: List<String>): List<String> {
    val output = mutableListOf<String>()
    val operators = mutableListOf<String>() 

    for (token in tokens) {
        if (token.toDoubleOrNull() != null) {
            output.add(token)
        } else if (token == "(") {
            operators.add(token)
        } else if (token == ")") {
            while (operators.isNotEmpty() && operators.last() != "(") {
                output.add(operators.removeAt(operators.lastIndex))
            }
            if (operators.isEmpty()) throw IllegalArgumentException("Mismatched parentheses.")
            operators.removeAt(operators.lastIndex)
        } else { // Operator
            while (operators.isNotEmpty() && operators.last() != "(" && precedence(operators.last()) >= precedence(token)) {
                output.add(operators.removeAt(operators.lastIndex))
            }
            operators.add(token)
        }
    }
    while (operators.isNotEmpty()) {
        if (operators.last() == "(") throw IllegalArgumentException("Mismatched parentheses.")
        output.add(operators.removeAt(operators.lastIndex))
    }
    return output
}

private fun evaluatePostfix(postfix: List<String>): Double {
    if (postfix.isEmpty()) return 0.0
    val stack = mutableListOf<Double>()
    for (token in postfix) {
        if (token.toDoubleOrNull() != null) {
            stack.add(token.toDouble())
        } else {
            if (stack.size < 2) throw IllegalArgumentException("Invalid expression: operator without operands.")
            val b = stack.removeAt(stack.lastIndex)
            val a = stack.removeAt(stack.lastIndex)
            val result = when (token) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b == 0.0) return Double.NaN else a / b
                "%" -> a % b
                else -> throw IllegalArgumentException("Unknown operator: $token")
            }
            stack.add(result)
        }
    }
    if (stack.size != 1) throw IllegalArgumentException("Invalid expression: too many operands.")
    return stack.first()
}
