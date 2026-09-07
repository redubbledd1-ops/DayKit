package com.dd.daykit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Photoshop-style HSV color picker
 */
@Composable
fun ColorPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }

    // Initialize from color
    LaunchedEffect(initialColor) {
        val hsv = colorToHSV(initialColor)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    // Notify color changes
    LaunchedEffect(hue, saturation, value) {
        onColorChanged(hsvToColor(hue, saturation, value))
    }

    Row(modifier = modifier) {
        // Main SV picker (saturation/value)
        SVPicker(
            hue = hue,
            saturation = saturation,
            value = value,
            onSaturationValueChange = { s, v ->
                saturation = s
                value = v
            },
            modifier = Modifier
                .size(250.dp)
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
        )

        Spacer(Modifier.width(12.dp))

        // Hue bar
        HueBar(
            hue = hue,
            onHueChange = { hue = it },
            modifier = Modifier
                .width(30.dp)
                .height(250.dp)
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
        )
    }
}

/**
 * Saturation/Value picker (main color square)
 */
@Composable
private fun SVPicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    onSaturationValueChange(s, v)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onSaturationValueChange(s, v)
                }
            }
    ) {
        // Draw saturation gradient (left to right: white to hue color)
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.White,
                    hsvToColor(hue, 1f, 1f)
                )
            )
        )

        // Draw value gradient (top to bottom: transparent to black)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black
                )
            )
        )

        // Draw cursor
        val x = saturation * size.width
        val y = (1f - value) * size.height
        drawCircle(
            color = Color.White,
            radius = 8f,
            center = Offset(x, y)
        )
        drawCircle(
            color = Color.Black,
            radius = 6f,
            center = Offset(x, y)
        )
    }
}

/**
 * Hue bar (vertical rainbow)
 */
@Composable
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newHue = (offset.y / size.height * 360f).coerceIn(0f, 360f)
                    onHueChange(newHue)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val newHue = (change.position.y / size.height * 360f).coerceIn(0f, 360f)
                    onHueChange(newHue)
                }
            }
    ) {
        // Draw hue gradient
        val hueColors = listOf(
            Color.Red,      // 0°
            Color.Yellow,   // 60°
            Color.Green,    // 120°
            Color.Cyan,     // 180°
            Color.Blue,     // 240°
            Color.Magenta,  // 300°
            Color.Red       // 360°
        )
        
        drawRect(
            brush = Brush.verticalGradient(hueColors)
        )

        // Draw cursor
        val y = (hue / 360f) * size.height
        drawRect(
            color = Color.White,
            topLeft = Offset(0f, y - 2f),
            size = androidx.compose.ui.geometry.Size(size.width, 4f)
        )
        drawRect(
            color = Color.Black,
            topLeft = Offset(0f, y - 1f),
            size = androidx.compose.ui.geometry.Size(size.width, 2f)
        )
    }
}

/**
 * Convert Color to HSV
 */
private fun colorToHSV(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    
    // Hue
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0) it + 360f else it }
    
    // Saturation
    val saturation = if (max == 0f) 0f else delta / max
    
    // Value
    val value = max
    
    return floatArrayOf(hue, saturation, value)
}

/**
 * Convert HSV to Color
 */
private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = hue / 60f
    val i = h.toInt()
    val f = h - i
    
    val p = value * (1f - saturation)
    val q = value * (1f - f * saturation)
    val t = value * (1f - (1f - f) * saturation)
    
    val (r, g, b) = when (i % 6) {
        0 -> Triple(value, t, p)
        1 -> Triple(q, value, p)
        2 -> Triple(p, value, t)
        3 -> Triple(p, q, value)
        4 -> Triple(t, p, value)
        else -> Triple(value, p, q)
    }
    
    return Color(r, g, b)
}
