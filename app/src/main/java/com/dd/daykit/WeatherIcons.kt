package com.dd.daykit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom weer-iconen — 24x24 viewport, altijd effen zwart getekend zodat de `tint`-parameter van
 * [androidx.compose.material3.Icon] (zie gebruik in [conditionIcon]/[currentConditionIcon] in
 * WeatherActivity.kt) de uiteindelijke kleur bepaalt — zelfde aanpak als de bestaande motregen-
 * stippen hiervoor al deden.
 *
 * Bouwstenen (wolk/cirkel/streep/vlok) zijn losse functies zodat elk icoon ze kan combineren i.p.v.
 * dat elke conditie een losstaand onsamenhangend icoon heeft.
 *
 * Dag/nacht: helder / licht bewolkt / half bewolkt hebben een maan-variant voor 's nachts (het
 * huidige-weer-icoon op de Weer-startpagina is het icoon dat 's nachts bekeken wordt).
 * Intensiteit: regen, regenbuien, sneeuw en sneeuwbuien hebben licht/matig/zwaar-varianten,
 * gekozen op basis van de ruwe WMO-code (zie `rainCodeIntensity` in WeatherActivity.kt). Hagel
 * heeft licht/zwaar (WMO kent alleen die twee hagel-codes).
 */

private val blackFill = SolidColor(Color.Black)

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, cx + r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, cx - r, cy)
    close()
}

/** Puffige wolk-silhouet, natuurlijke positie in het 24x24 vlak (ruwweg x:0-24, y:6-22). */
private fun PathBuilder.cloudOutline() {
    moveTo(19.35f, 12.04f)
    curveTo(18.67f, 8.59f, 15.64f, 6f, 12f, 6f)
    curveTo(9.11f, 6f, 6.6f, 7.64f, 5.35f, 10.04f)
    curveTo(2.34f, 10.36f, 0f, 12.9f, 0f, 16f)
    curveTo(0f, 19.31f, 2.69f, 22f, 6f, 22f)
    lineTo(19f, 22f)
    curveTo(21.76f, 22f, 24f, 19.76f, 24f, 17f)
    curveTo(24f, 14.36f, 21.95f, 12.22f, 19.35f, 12.04f)
    close()
}

/** Klein sneeuwvlokje: 3 kruisende streepjes door het middelpunt (asterisk). */
private fun PathBuilder.flake(cx: Float, cy: Float, r: Float) {
    moveTo(cx, cy - r); lineTo(cx, cy + r)
    moveTo(cx - r * 0.87f, cy - r * 0.5f); lineTo(cx + r * 0.87f, cy + r * 0.5f)
    moveTo(cx + r * 0.87f, cy - r * 0.5f); lineTo(cx - r * 0.87f, cy + r * 0.5f)
}

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

// ---- Helder / licht bewolkt / half bewolkt — dag (zon) en nacht (maan) ----

val SunIcon: ImageVector by lazy {
    icon("Sun") {
        path(fill = blackFill) { circle(12f, 11f, 6.5f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(12f, 2.5f); lineTo(12f, 0.5f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(12f, 21.5f); lineTo(12f, 19.5f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(3f, 11f); lineTo(1f, 11f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(23f, 11f); lineTo(21f, 11f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(5.5f, 4.5f); lineTo(4.1f, 3.1f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(19.9f, 18.9f); lineTo(18.5f, 17.5f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(18.5f, 4.5f); lineTo(19.9f, 3.1f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(4.1f, 18.9f); lineTo(5.5f, 17.5f) }
    }
}

val MoonIcon: ImageVector by lazy {
    icon("Moon") {
        path(fill = blackFill) { circle(12f, 11f, 6.5f) }
    }
}

val SunSmallCloudIcon: ImageVector by lazy {
    icon("SunSmallCloud") {
        path(fill = blackFill) { circle(10f, 8f, 6.5f) }
        group(scaleX = 0.55f, scaleY = 0.55f, translationX = 8.5f, translationY = 11f) {
            path(fill = blackFill) { cloudOutline() }
        }
    }
}

val MoonSmallCloudIcon: ImageVector by lazy {
    icon("MoonSmallCloud") {
        path(fill = blackFill) { circle(10f, 8f, 6.5f) }
        group(scaleX = 0.55f, scaleY = 0.55f, translationX = 8.5f, translationY = 11f) {
            path(fill = blackFill) { cloudOutline() }
        }
    }
}

val SunCloudIcon: ImageVector by lazy {
    icon("SunCloud") {
        path(fill = blackFill) { circle(7.5f, 8f, 5f) }
        path(fill = blackFill) { cloudOutline() }
    }
}

val MoonCloudIcon: ImageVector by lazy {
    icon("MoonCloud") {
        path(fill = blackFill) { circle(7.5f, 8f, 5f) }
        path(fill = blackFill) { cloudOutline() }
    }
}

val CloudyIcon: ImageVector by lazy {
    icon("Cloudy") {
        group(scaleX = 0.7f, scaleY = 0.7f, translationX = 3f, translationY = -4f) {
            path(fill = blackFill) { cloudOutline() }
        }
        path(fill = blackFill) { cloudOutline() }
    }
}

val FogIcon: ImageVector by lazy {
    icon("Fog") {
        group(scaleX = 0.8f, scaleY = 0.8f, translationX = 2.4f, translationY = -3f) {
            path(fill = blackFill) { cloudOutline() }
        }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(3f, 17f); lineTo(21f, 17f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(5f, 19.5f); lineTo(18f, 19.5f) }
        path(stroke = blackFill, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round) { moveTo(2f, 22f); lineTo(22f, 22f) }
    }
}

val DrizzleCloudIcon: ImageVector by lazy {
    icon("DrizzleCloud") {
        group(scaleX = 0.85f, scaleY = 0.85f, translationX = 2f, translationY = -3f) {
            path(fill = blackFill) { cloudOutline() }
        }
        path(fill = blackFill) { circle(6f, 20f, 0.9f) }
        path(fill = blackFill) { circle(12f, 22f, 0.9f) }
        path(fill = blackFill) { circle(18f, 20f, 0.9f) }
        path(fill = blackFill) { circle(9f, 23.5f, 0.9f) }
        path(fill = blackFill) { circle(15f, 23.5f, 0.9f) }
    }
}

// ---- Regen (licht/matig/zwaar) ----

private fun ImageVector.Builder.rainCloud() {
    group(scaleX = 0.85f, scaleY = 0.85f, translationX = 2f, translationY = -3f) {
        path(fill = blackFill) { cloudOutline() }
    }
}

private fun ImageVector.Builder.rainStreak(x: Float) {
    path(stroke = blackFill, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
        moveTo(x, 19f); lineTo(x - 1.5f, 23.5f)
    }
}

val RainLightIcon: ImageVector by lazy {
    icon("RainLight") { rainCloud(); rainStreak(12f) }
}
val RainModerateIcon: ImageVector by lazy {
    icon("RainModerate") { rainCloud(); rainStreak(8f); rainStreak(16f) }
}
val RainHeavyIcon: ImageVector by lazy {
    icon("RainHeavy") { rainCloud(); rainStreak(6f); rainStreak(12f); rainStreak(18f) }
}

// ---- Regenbuien (zon piept er tussendoor) — licht/matig/zwaar ----

private fun ImageVector.Builder.showerSunCloud() {
    path(fill = blackFill) { circle(7f, 7f, 4f) }
    group(scaleX = 0.85f, scaleY = 0.85f, translationX = 2f, translationY = -1f) {
        path(fill = blackFill) { cloudOutline() }
    }
}

val RainShowersLightIcon: ImageVector by lazy {
    icon("RainShowersLight") { showerSunCloud(); rainStreak(12f) }
}
val RainShowersModerateIcon: ImageVector by lazy {
    icon("RainShowersModerate") { showerSunCloud(); rainStreak(8f); rainStreak(16f) }
}
val RainShowersHeavyIcon: ImageVector by lazy {
    icon("RainShowersHeavy") { showerSunCloud(); rainStreak(7f); rainStreak(13f); rainStreak(18f) }
}

// ---- IJzel (bevriezende regen/motregen) ----

val FreezingRainIcon: ImageVector by lazy {
    icon("FreezingRain") {
        rainCloud()
        rainStreak(7f)
        path(stroke = blackFill, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Round) { flake(15f, 21.5f, 1.8f) }
    }
}

// ---- Sneeuw (licht/matig/zwaar) ----

private fun ImageVector.Builder.snowFlakeStroke(cx: Float, cy: Float, r: Float) {
    path(stroke = blackFill, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Round) { flake(cx, cy, r) }
}

val SnowLightIcon: ImageVector by lazy {
    icon("SnowLight") { rainCloud(); snowFlakeStroke(12f, 21.5f, 1.8f) }
}
val SnowModerateIcon: ImageVector by lazy {
    icon("SnowModerate") { rainCloud(); snowFlakeStroke(8f, 21f, 1.7f); snowFlakeStroke(16f, 22f, 1.7f) }
}
val SnowHeavyIcon: ImageVector by lazy {
    icon("SnowHeavy") {
        rainCloud()
        snowFlakeStroke(6f, 20.5f, 1.6f)
        snowFlakeStroke(12f, 21.5f, 1.8f)
        snowFlakeStroke(18f, 20.5f, 1.6f)
    }
}

// ---- Sneeuwbuien (zon piept er tussendoor) — licht/matig/zwaar ----

val SnowShowersLightIcon: ImageVector by lazy {
    icon("SnowShowersLight") { showerSunCloud(); snowFlakeStroke(12f, 22f, 1.8f) }
}
val SnowShowersModerateIcon: ImageVector by lazy {
    icon("SnowShowersModerate") { showerSunCloud(); snowFlakeStroke(8f, 21f, 1.6f); snowFlakeStroke(16f, 22f, 1.6f) }
}
val SnowShowersHeavyIcon: ImageVector by lazy {
    icon("SnowShowersHeavy") {
        showerSunCloud()
        snowFlakeStroke(6f, 20.5f, 1.5f)
        snowFlakeStroke(12f, 21.5f, 1.6f)
        snowFlakeStroke(18f, 20.5f, 1.5f)
    }
}

// ---- Natte sneeuw (mix van regen en sneeuw) ----

val WetSnowIcon: ImageVector by lazy {
    icon("WetSnow") {
        rainCloud()
        rainStreak(7f)
        snowFlakeStroke(15f, 22f, 1.7f)
    }
}

// ---- Onweer (los flits-icoon, geen wolk) ----

val ThunderstormIcon: ImageVector by lazy {
    icon("Thunderstorm") {
        path(fill = blackFill) {
            moveTo(15f, 1f)
            lineTo(7f, 14f)
            lineTo(12f, 14f)
            lineTo(9f, 23f)
            lineTo(19f, 9f)
            lineTo(13.5f, 9f)
            close()
        }
    }
}

// ---- Hagel (los, geen wolk, kleine harde bolletjes) — licht/zwaar ----

val HailLightIcon: ImageVector by lazy {
    icon("HailLight") {
        path(fill = blackFill) { circle(9f, 12f, 1.3f) }
        path(fill = blackFill) { circle(15f, 12f, 1.3f) }
    }
}
val HailHeavyIcon: ImageVector by lazy {
    icon("HailHeavy") {
        path(fill = blackFill) { circle(6.5f, 9f, 1.3f) }
        path(fill = blackFill) { circle(13f, 7.5f, 1.3f) }
        path(fill = blackFill) { circle(19f, 9.5f, 1.3f) }
        path(fill = blackFill) { circle(9.5f, 15f, 1.3f) }
        path(fill = blackFill) { circle(16f, 15.5f, 1.3f) }
    }
}

// ---- Gladde weg (los ijskristal, voor de "gladheid"-waarschuwing) ----

val IcyRoadIcon: ImageVector by lazy {
    icon("IcyRoad") {
        path(stroke = blackFill, strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round) { flake(12f, 12f, 6.5f) }
    }
}

// ---- Windrichting — één pijl, in de UI geroteerd op de exacte graad (0° = noord, wijst omhoog) ----

val WindDirectionArrowIcon: ImageVector by lazy {
    icon("WindDirectionArrow") {
        path(fill = blackFill) {
            moveTo(12f, 2f)
            lineTo(18f, 12f)
            lineTo(13.5f, 12f)
            lineTo(13.5f, 22f)
            lineTo(10.5f, 22f)
            lineTo(10.5f, 12f)
            lineTo(6f, 12f)
            close()
        }
    }
}
