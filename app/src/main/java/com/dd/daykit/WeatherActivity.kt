package com.dd.daykit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tornado
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import com.google.android.gms.location.LocationServices
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.dd.daykit.data.HaEntity
import com.dd.daykit.data.HomeAssistantRepository
import com.dd.daykit.data.HomeAssistantSettingsStorage
import com.dd.daykit.data.SpeakerContext
import com.dd.daykit.data.SpeakerSettings
import com.dd.daykit.data.WeatherRepository
import com.dd.daykit.network.GeocodingClient
import com.dd.daykit.network.HomeAssistantClient
import com.dd.daykit.ui.NavigationBar
import com.dd.daykit.ui.SwipeIndicators
import com.dd.daykit.ui.modals.*
import com.dd.daykit.viewmodel.HaSettingsViewModel
import com.dd.daykit.viewmodel.HaSettingsViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ── UI state ─────────────────────────────────────────────────────────────────

/** Eén losse waarschuwing (bv. "het regent hard", "kans op extreme hitte") met bijpassend icoon. */
data class WeatherAlert(val reason: WeatherReason, val icon: ImageVector)

private sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Warning(
        /** Eén of meerdere tegelijk geldende waarschuwingen (bv. regen én hitte tegelijk). */
        val alerts: List<WeatherAlert>,
        /** Null = geen gekoppeld agenda-event; dit is dan een "nu is het al slecht weer"-waarschuwing. */
        val minutesUntilEvent: Long? = null,
        val eventName: String? = null,
        val eventTimeStr: String? = null,
        val currentTemp: Double? = null,
        val todayMinTemp: Double? = null,
        val todayMaxTemp: Double? = null,
        val currentRainChance: Int? = null
    ) : WeatherUiState()
    data class Calm(
        val todayWarnings: List<WeatherReason> = emptyList(),
        val tomorrowWarnings: List<WeatherReason> = emptyList(),
        val currentTemp: Double? = null,
        val todayMinTemp: Double? = null,
        val todayMaxTemp: Double? = null,
        val currentRainChance: Int? = null,
        val currentWindSpeed: Double? = null,
        val currentWindDirection: Int? = null,
        val currentCondition: WeatherCondition? = null,
        /** Ruwe WMO-weercode van dit moment, voor een net iets fijnmaziger icoon dan [currentCondition] alleen (zie [currentConditionIcon]). */
        val currentWeatherCode: Int? = null,
        /** Vervangt 's avonds (vanaf 18:00) de "geen waarschuwing"-tekst als morgen duidelijk warmer/kouder wordt. */
        val eveningTempChangeMessage: String? = null
    ) : WeatherUiState()
}

/**
 * Proces-brede cache van de laatst geladen weer-homepage-data (weerkaart + widgets). Blijft in
 * leven zolang de app draait, ook als [WeatherActivity] opnieuw wordt aangemaakt (bv. even weg en
 * terug). Zo kan de pagina bij een volgend bezoek meteen de laatst bekende (evt. wat oudere) data
 * tonen i.p.v. een spinner, terwijl er op de achtergrond ververst wordt.
 */
private object WeatherHomeCache {
    var lastUiState: WeatherUiState? = null
    var lastWidgetValues: Map<String, WeatherWidgetLiveValue> = emptyMap()
    var widgetsEverLoaded: Boolean = false
}

// ── Data loading ──────────────────────────────────────────────────────────────

private suspend fun loadWeatherUiState(
    context: android.content.Context,
    repo: WeatherRepository
): WeatherUiState = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()

    // Slecht weer en temperatuurwissel hebben elk hun eigen agenda-selectie (Slecht weer >
    // Agenda items / Grote temperatuurverandering > Agenda items) — dus mogelijk een ander
    // "eerstvolgend" event per categorie. Niet-geselecteerde agenda's mogen NOOIT meetellen: als
    // de selectie leeg is (standaard, niets gekozen), wordt er expliciet geen event opgehaald —
    // dit voorkomt dat een niet-gekoppelde agenda alsnog een melding kan triggeren. Als
    // "Koppelen aan agenda-afspraken" uit staat, worden event-gebonden meldingen helemaal
    // overgeslagen (dan blijft alleen de kloktijd-gebaseerde meldingen/kaart over).
    val linkToCalendar = SettingsManager.getWeatherLinkToCalendar(context)
    val badWeatherCalendarIds = SettingsManager.getWeatherBadWeatherCalendarIds(context)
    val tempChangeCalendarIds = SettingsManager.getWeatherTempChangeCalendarIds(context)
    val nextEventBadWeather = if (!linkToCalendar || badWeatherCalendarIds.isEmpty()) {
        null
    } else {
        getUpcomingWakeUpEvents(context, CalendarView.NEXT_7_DAYS, badWeatherCalendarIds)
            .firstOrNull { it.epochMillis > now }
    }
    val nextEventTempChange = if (!linkToCalendar || tempChangeCalendarIds.isEmpty()) {
        null
    } else {
        getUpcomingWakeUpEvents(context, CalendarView.NEXT_7_DAYS, tempChangeCalendarIds)
            .firstOrNull { it.epochMillis > now }
    }

    val lat = SettingsManager.getWeatherLatitude(context)
    val lon = SettingsManager.getWeatherLongitude(context)
    val weatherModel = SettingsManager.getWeatherModel(context)
    val rainThreshold = SettingsManager.getWeatherRainThreshold(context)
    val rainEnabled = SettingsManager.getWeatherRainAlarmEnabled(context)
    val tempChangeEnabled = SettingsManager.getWeatherTempChangeEnabled(context)
    val tempThreshold = SettingsManager.getWeatherTempChangeThreshold(context)

    val forecast = repo.getForecast(lat, lon, weatherModel).getOrNull()
    val todayAlerts = if (forecast != null) checkTodayWarning(context, forecast, rainEnabled, rainThreshold, now) else null
    val tomorrowAlerts = if (forecast != null) checkTomorrowWarning(context, forecast, rainEnabled, rainThreshold, now) else null
    val currentWeather = repo.getCurrentWeather(lat, lon, weatherModel).getOrNull()
    val (todayMinTemp, todayMaxTemp) = if (forecast != null) computeTodayMinMaxTemp(forecast) else null to null
    val eveningSwitchTime = SettingsManager.getWeatherEveningSwitchTime(context)
    val eveningTempChangeMessage = if (forecast != null) {
        computeEveningTempChangeMessage(forecast, tempChangeEnabled, tempThreshold, eveningSwitchTime)
    } else null

    // Morgen alleen tonen naast vandaag als dat ook echt nieuwe/nuttige info toevoegt: vandaag
    // niets te melden, morgen een ANDER type weer dan vandaag (bv. vandaag regen, morgen hagel),
    // of hetzelfde type maar HEVIGER (bv. vandaag lichte regen, morgen zware regen). Als morgen
    // hetzelfde of minder erg is dan vandaag (bv. vandaag zware regen, morgen lichte regen), voegt
    // dat niets toe en blijft alleen vandaag zichtbaar.
    val showTomorrowAlongsideToday = todayAlerts == null || todayAlerts.isEmpty ||
        (tomorrowAlerts != null && tomorrowAddsNewInfo(todayAlerts.categories, tomorrowAlerts.categories))
    val visibleTomorrowAlerts = if (showTomorrowAlongsideToday) tomorrowAlerts?.labels.orEmpty() else emptyList()

    // "Nu is het al slecht weer" — los van agenda-events. Dit is de vangnet-waarschuwing voor het
    // geval er geen (relevant) agenda-event is, maar het huidige moment zelf al aan de Slecht-weer-
    // criteria voldoet (regen boven de drempel, of een ingeschakelde "Extra weersomstandigheid").
    // Meerdere tegelijk geldende typen (bv. regen én hitte) worden allemaal getoond, onder elkaar.
    val effectiveRainChance = currentWeather?.let {
        effectivePrecipitationProbability(it.precipitationProbability, it.precipitation, it.condition)
    }

    val currentConditionsWarning = currentWeather?.let { cw ->
        val alerts = activeWeatherAlerts(
            context, cw.weatherCode, cw.condition, cw.temperature, cw.precipitationProbability, cw.precipitation,
            cw.windGusts, rainEnabled, rainThreshold
        )
        if (alerts.isEmpty()) null else WeatherUiState.Warning(
            alerts = alerts,
            currentTemp = cw.temperature,
            todayMinTemp = todayMinTemp,
            todayMaxTemp = todayMaxTemp,
            currentRainChance = effectiveRainChance
        )
    }

    fun calmState() = WeatherUiState.Calm(
        todayWarnings = todayAlerts?.labels.orEmpty(),
        tomorrowWarnings = visibleTomorrowAlerts,
        currentTemp = currentWeather?.temperature,
        todayMinTemp = todayMinTemp,
        todayMaxTemp = todayMaxTemp,
        currentRainChance = effectiveRainChance,
        currentWindSpeed = currentWeather?.windSpeed,
        currentWindDirection = currentWeather?.windDirection,
        currentCondition = currentWeather?.condition,
        currentWeatherCode = currentWeather?.weatherCode,
        eveningTempChangeMessage = eveningTempChangeMessage
    )

    if (forecast == null || (nextEventBadWeather == null && nextEventTempChange == null)) {
        return@withContext currentConditionsWarning ?: calmState()
    }

    // Slecht weer: tegen het (eventueel eigen) eerstvolgende event van die selectie. Ook hier
    // alleen tonen op het startscherm als dat event binnen 24 uur is. Meerdere types tegelijk
    // (bv. regen én hitte rond hetzelfde event) worden allemaal getoond, onder elkaar.
    var badWeatherWarning: WeatherUiState.Warning? = null
    if (nextEventBadWeather != null && (nextEventBadWeather.epochMillis - now) <= 24 * 60 * 60 * 1000L) {
        // Neerslag-beslissing, dus het uurblok waarin de afspraak valt (zie
        // WeatherRepository.getPrecipitationHourForTime) - niet het dichtstbijzijnde stempel, dat bij
        // een afspraak vroeg in het uur naar het uur ervóór wees. Zelfde bron als de melding, zodat
        // deze kaart en de melding niet uit elkaar kunnen lopen.
        val eventWeather = repo.getPrecipitationHourForTime(lat, lon, nextEventBadWeather.epochMillis, weatherModel).getOrNull()
        if (eventWeather != null) {
            val alerts = activeWeatherAlerts(
                context, eventWeather.weatherCode, eventWeather.condition, eventWeather.temperature,
                eventWeather.precipitationProbability, eventWeather.precipitation, eventWeather.windGusts,
                rainEnabled, rainThreshold
            )
            if (alerts.isNotEmpty()) {
                badWeatherWarning = WeatherUiState.Warning(
                    alerts = alerts,
                    minutesUntilEvent = (nextEventBadWeather.epochMillis - now) / 60_000L,
                    eventName = nextEventBadWeather.label,
                    eventTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextEventBadWeather.epochMillis)),
                    currentTemp = currentWeather?.temperature,
                    todayMinTemp = todayMinTemp,
                    todayMaxTemp = todayMaxTemp,
                    currentRainChance = effectiveRainChance
                )
            }
        }
    }

    // Temperatuurwissel: tegen het (eventueel eigen) eerstvolgende event van die selectie.
    // Alleen als kaart op het startscherm tonen als dat event binnen 24 uur is — verder weg is
    // een "groot temperatuurverschil"-melding op het startscherm niet zinvol.
    var tempChangeWarning: WeatherUiState.Warning? = null
    if (tempChangeEnabled && nextEventTempChange != null && (nextEventTempChange.epochMillis - now) <= 24 * 60 * 60 * 1000L) {
        val tomorrowMillis = nextEventTempChange.epochMillis + 24 * 60 * 60 * 1000L
        val diff = repo.getTemperatureChange(lat, lon, nextEventTempChange.epochMillis, tomorrowMillis, weatherModel)
            .getOrNull() ?: 0.0
        if (kotlin.math.abs(diff) >= tempThreshold) {
            val tempEventWeather = repo.getWeatherForTime(lat, lon, nextEventTempChange.epochMillis, weatherModel).getOrNull()
            tempChangeWarning = WeatherUiState.Warning(
                alerts = listOf(WeatherAlert(
                    WeatherReason.flat(LanguageManager.getString("weather_temp_change_expected")),
                    conditionIcon(tempEventWeather?.condition, tempEventWeather?.weatherCode, tempEventWeather?.temperature),
                )),
                minutesUntilEvent = (nextEventTempChange.epochMillis - now) / 60_000L,
                eventName = nextEventTempChange.label,
                eventTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextEventTempChange.epochMillis)),
                currentTemp = currentWeather?.temperature,
                todayMinTemp = todayMinTemp,
                todayMaxTemp = todayMaxTemp,
                currentRainChance = effectiveRainChance
            )
        }
    }

    // Als beide categorieën iets te melden hebben, toon de kaart voor het eerstkomende event.
    val chosen = when {
        badWeatherWarning != null && tempChangeWarning != null -> {
            if (badWeatherWarning.minutesUntilEvent!! <= tempChangeWarning.minutesUntilEvent!!) badWeatherWarning else tempChangeWarning
        }
        badWeatherWarning != null -> badWeatherWarning
        tempChangeWarning != null -> tempChangeWarning
        else -> null
    }

    // Event-gebonden waarschuwing heeft voorrang; zonder relevant event valt dit terug op "nu al
    // slecht weer" (currentConditionsWarning), en pas daarna op de rustige staat.
    chosen ?: currentConditionsWarning ?: calmState()
}

private fun computeTodayMinMaxTemp(forecast: WeatherForecast): Pair<Double?, Double?> {
    val today = LocalDateTime.now(ZoneId.systemDefault()).toLocalDate()
    return computeMinMaxTempForDate(forecast, today)
}

/** Min./max. temperatuur voor een specifieke kalenderdag uit de forecast (null/null als die dag niet in de data zit). */
fun computeMinMaxTempForDate(forecast: WeatherForecast, date: java.time.LocalDate): Pair<Double?, Double?> {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    var min: Double? = null
    var max: Double? = null

    forecast.hourly.time.forEachIndexed { i, timeStr ->
        val dt = try {
            LocalDateTime.parse(timeStr, formatter)
        } catch (e: Exception) { return@forEachIndexed }

        if (dt.toLocalDate() == date) {
            val temp = forecast.hourly.temperature2m.getOrNull(i) ?: return@forEachIndexed
            if (min == null || temp < min!!) min = temp
            if (max == null || temp > max!!) max = temp
        }
    }

    return min to max
}

/** Max. temperatuur voor een specifieke kalenderdag uit de forecast (of null als die dag niet in de data zit). */
fun computeMaxTempForDate(forecast: WeatherForecast, date: java.time.LocalDate): Double? {
    return computeMinMaxTempForDate(forecast, date).second
}

/**
 * Rondt een Celsius-temperatuur (Open-Meteo levert altijd Celsius) af naar de weergave-eenheid
 * van de gebruiker (Instellingen > Weer > Algemeen > Fahrenheit). Puur voor weergave — interne
 * drempels/vergelijkingen (bv. Extra weersomstandigheden > hitte-drempel, temperatuurwissel-
 * drempel) blijven altijd in Celsius werken, dus die worden hier niet aangepast.
 */
private fun displayTemp(context: android.content.Context, celsius: Double): Int {
    val value = if (SettingsManager.getWeatherUseFahrenheit(context)) celsius * 9.0 / 5.0 + 32.0 else celsius
    return value.roundToInt()
}

/**
 * Representatief weertype voor een dag: het weertype van het uur dat het dichtst bij het
 * middaguur (12:00) ligt, als korte samenvatting voor de dagenlijst. Null als die dag niet in de
 * forecast-data zit.
 */
fun representativeConditionForDate(forecast: WeatherForecast, date: java.time.LocalDate): WeatherCondition? {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    var best: WeatherCondition? = null
    var bestDiff = Int.MAX_VALUE

    forecast.hourly.time.forEachIndexed { i, timeStr ->
        val dt = try {
            LocalDateTime.parse(timeStr, formatter)
        } catch (e: Exception) { return@forEachIndexed }

        if (dt.toLocalDate() == date) {
            val code = forecast.hourly.weathercode.getOrNull(i) ?: return@forEachIndexed
            val diff = kotlin.math.abs(dt.hour - 12)
            if (diff < bestDiff) {
                bestDiff = diff
                best = WeatherCondition.fromWmoCode(code)
            }
        }
    }

    return best
}

/**
 * 's Avonds (vanaf het ingestelde omschakel-tijdstip, standaard 18:00 lokale tijd) een korte tekst
 * over het temperatuurverschil met morgen, voor op de "geen waarschuwing"-kaart. Null buiten dat
 * venster, als temperatuurwissel-alarm uit staat, of als het verschil onder de ingestelde drempel
 * blijft. [switchTime] is een "HH:mm"-string, instelbaar via Weerinstellingen > Algemeen.
 */
fun computeEveningTempChangeMessage(forecast: WeatherForecast, tempChangeEnabled: Boolean, tempThreshold: Int, switchTime: String): String? {
    if (!tempChangeEnabled) return null
    val nowLocal = LocalDateTime.now(ZoneId.systemDefault())
    val switchLocalTime = parseTimeOrDefault(switchTime, LocalTime.of(18, 0))
    if (nowLocal.toLocalTime().isBefore(switchLocalTime)) return null

    val today = nowLocal.toLocalDate()
    val tomorrow = today.plusDays(1)
    val maxToday = computeMaxTempForDate(forecast, today) ?: return null
    val maxTomorrow = computeMaxTempForDate(forecast, tomorrow) ?: return null
    val diff = maxTomorrow - maxToday
    if (kotlin.math.abs(diff) < tempThreshold) return null

    val rounded = kotlin.math.round(kotlin.math.abs(diff)).toInt()
    val key = if (diff >= 0) "weather_evening_warmer" else "weather_evening_colder"
    return LanguageManager.getString(key).replace("{degrees}", rounded.toString())
}

/** Parseert een "HH:mm"-string naar [LocalTime], met terugval op [default] bij een ongeldige waarde. */
private fun parseTimeOrDefault(value: String, default: LocalTime): LocalTime {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: return default
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: return default
    return LocalTime.of(hour, minute)
}

/**
 * Over welke dag een weermelding gaat, t.o.v. het moment waarop hij verstuurd wordt. Bepaalt of
 * er "Morgen" (of "Overmorgen") in de tekst hoort - dat stond eerder vast ingebakken in de
 * teksten zelf, waardoor een melding over een agenda-item van vandaag ook "morgen" zei en de
 * "Dag ervoor"-temperatuurmelding (die over overmorgen gaat) net zo goed.
 */
enum class WeatherAlertDay { TODAY, TOMORROW, DAY_AFTER_TOMORROW }

/**
 * Bepaalt of [targetMillis] vandaag, morgen of overmorgen valt t.o.v. [nowMillis]. Alles daarbuiten
 * telt als [WeatherAlertDay.TODAY]: dan komt er geen (mogelijk misleidende) dagaanduiding in de
 * tekst en blijft het bij de kale melding. Bewust op kalenderdag, niet op "binnen 24 uur" - een
 * afspraak morgenochtend om 08:00 is "morgen", ook als dat over 10 uur is.
 */
fun weatherAlertDayFor(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): WeatherAlertDay {
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val target = java.time.Instant.ofEpochMilli(targetMillis).atZone(zone).toLocalDate()
    return when (java.time.temporal.ChronoUnit.DAYS.between(today, target)) {
        1L -> WeatherAlertDay.TOMORROW
        2L -> WeatherAlertDay.DAY_AFTER_TOMORROW
        else -> WeatherAlertDay.TODAY
    }
}

/**
 * Zet "Morgen " voor [text] als [day] morgen is, en laat de tekst verder met rust. Voor meldingen
 * waarvan de titel niet al zelf een dagaanduiding opbouwt (zie
 * [WeatherReason.toRainNotificationParts], dat doet hetzelfde voor regen-meldingen).
 *
 * Overmorgen krijgt bewust geen eigen voorvoegsel hier: die situatie bestaat alleen bij de
 * dagelijkse temperatuurmelding, en die heeft een eigen, complete zin.
 */
fun withTomorrowPrefix(text: String, day: WeatherAlertDay): String {
    if (day != WeatherAlertDay.TOMORROW) return text
    val prefix = LanguageManager.getString("weather_tomorrow_prefix")
    // Niet nog een keer "Morgen" ervoor plakken als de tekst er al mee begint.
    if (text.startsWith(prefix, ignoreCase = true)) return text
    return "$prefix ${text.replaceFirstChar { it.lowercase() }}"
}

/**
 * Submelding voor temperatuurwissel-meldingen (gebruikt door de worker + exacte dagelijkse
 * meldingen): het verschil t.o.v. de vergelijkingsdag (bv. "8 graden meer dan vandaag"). De titel
 * met de daadwerkelijke voorspelde temperatuur zit apart in [formatTempChangeTitle] - zelfde
 * titel/bericht-opsplitsing als bij regen-meldingen ([formatRainAlertTitleAndMessage]).
 *
 * [comparedToTomorrow] is voor de "Dag ervoor"-melding: die vergelijkt overmorgen met MORGEN, en
 * zei desondanks "dan vandaag".
 */
fun formatTempChangeMessage(diff: Double, threshold: Int, comparedToTomorrow: Boolean = false): String {
    val rounded = kotlin.math.round(kotlin.math.abs(diff)).toInt()
    val key = when {
        comparedToTomorrow && diff >= 0 -> "weather_temp_change_more_than_tomorrow"
        comparedToTomorrow -> "weather_temp_change_less_than_tomorrow"
        diff >= 0 -> "weather_temp_change_more"
        else -> "weather_temp_change_less"
    }
    return LanguageManager.getString(key).replace("{degrees}", rounded.toString())
}

/**
 * Titel voor temperatuurwissel-meldingen: de daadwerkelijk voorspelde temperatuur voor de dag in
 * kwestie, mét de juiste dagaanduiding (bv. "Het wordt morgen ongeveer 22 graden").
 *
 * [day] bepaalt welke zin gebruikt wordt. Voorheen stond "morgen" vast in de tekst, ook wanneer de
 * melding over vandaag of overmorgen ging.
 */
fun formatTempChangeTitle(
    context: android.content.Context,
    maxTemp: Double,
    day: WeatherAlertDay = WeatherAlertDay.TOMORROW
): String {
    val temp = displayTemp(context, maxTemp)
    val key = when (day) {
        WeatherAlertDay.TOMORROW -> "weather_temp_change_title"
        WeatherAlertDay.DAY_AFTER_TOMORROW -> "weather_temp_change_title_day_after"
        WeatherAlertDay.TODAY -> "weather_temp_change_title_plain"
    }
    return LanguageManager.getString(key).replace("{temp}", temp.toString())
}

/** Leesbare tekst voor de "Bereik rond tijdstip"-instelling (1 min tot 12 uur, exponentiële schaal). */
fun formatRangeMinutes(minutes: Int): String {
    if (minutes <= 1) return LanguageManager.getString("weather_range_exact")
    if (minutes < 60) return LanguageManager.getString("weather_min_unit").replace("{min}", minutes.toString())
    val hours = minutes / 60
    val rem = minutes % 60
    return if (rem == 0) {
        LanguageManager.getString("weather_hour_unit").replace("{hours}", hours.toString())
    } else {
        LanguageManager.getString("weather_hour_min_unit").replace("{hours}", hours.toString()).replace("{min}", rem.toString())
    }
}

/** Leesbare tekst voor "X min van tevoren"-sliders (bv. "Melding voor agenda item") - 0 = exact bij aanvang. */
fun formatMinutesBeforeLabel(minutes: Int): String {
    if (minutes <= 0) return LanguageManager.getString("weather_exact_at_start")
    val hours = minutes / 60
    val rem = minutes % 60
    val timeStr = when {
        hours == 0 -> LanguageManager.getString("weather_min_unit").replace("{min}", rem.toString())
        rem == 0 -> LanguageManager.getString("weather_hour_unit").replace("{hours}", hours.toString())
        else -> LanguageManager.getString("weather_hour_min_unit").replace("{hours}", hours.toString()).replace("{min}", rem.toString())
    }
    return LanguageManager.getString("weather_minutes_before_suffix").replace("{time}", timeStr)
}

fun reverseGeocodeLocationName(context: android.content.Context, latitude: Double, longitude: Double): String? {
    return try {
        val geocoder = Geocoder(context, Locale("nl"))
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        val address = addresses?.firstOrNull() ?: return null
        val city = address.locality ?: address.subAdminArea ?: address.adminArea
        val country = address.countryName
        when {
            city != null && country != null -> "$city, $country"
            city != null -> city
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Ernst per waarschuwings-categorie voor een dag-scan (0 = niet van toepassing). Regen/sneeuw
 * krijgen een intensiteits-niveau (1=zacht, 2=normaal, 3=hard) via de WMO-code, de rest is
 * binair (1 = van toepassing). Dit maakt het mogelijk om vandaag en morgen per type te
 * vergelijken: is morgen een ANDER type dan vandaag, of hetzelfde type maar HEVIGER?
 */
data class CategorySeverity(
    val hurricane: Int = 0,
    val storm: Int = 0,
    val thunder: Int = 0,
    val hail: Int = 0,
    val ice: Int = 0,
    val snow: Int = 0,
    val rain: Int = 0,
    val heat: Int = 0
)

/** True als [tomorrow] op minstens één categorie nieuwe of ergere info toevoegt t.o.v. [today]. */
private fun tomorrowAddsNewInfo(today: CategorySeverity, tomorrow: CategorySeverity): Boolean {
    return tomorrow.hurricane > today.hurricane ||
        tomorrow.storm > today.storm ||
        tomorrow.thunder > today.thunder ||
        tomorrow.hail > today.hail ||
        tomorrow.ice > today.ice ||
        tomorrow.snow > today.snow ||
        tomorrow.rain > today.rain ||
        tomorrow.heat > today.heat
}

/** Resultaat van een dag-venster-check: alle van-toepassing-zijnde labels (kan leeg zijn) + ernst per
 * categorie + het kale regenpercentage (null als regen geen actieve reden is) - voor de
 * regen-specifieke meldingopmaak, zie [formatRainAlertTitleAndMessage]. */
data class DayWeatherAlerts(val labels: List<WeatherReason>, val categories: CategorySeverity, val rainProbability: Int? = null) {
    val isEmpty: Boolean get() = labels.isEmpty()
}

/**
 * Regenkans (het kale percentage) als regen een actieve reden is in deze scan (zelfde voorwaarde
 * als het regen-blok in [activeWeatherAlertsForScan]), anders null. Gebruikt om naast de
 * kwalitatieve tekst ("Kleine kans op regen") ook het percentage zelf te kunnen tonen in de
 * melding/popup.
 */
/**
 * Neerslagkans die bij de regen-melding hoort, of null als er geen percentage te tonen valt.
 *
 * Levert het model helemaal geen kansdata en steunt de melding puur op de WMO-code
 * ([RainOutcome.fromCodeOnly]), dan komt hier bewust null uit. Voorheen werd in dat geval "0% kans"
 * meegestuurd, wat naast een titel als "Het regent hard" tegenstrijdig staat; met null valt de
 * melding terug op de kwalitatieve tekst zonder percentage.
 */
fun rainAlertProbability(scan: BadWeatherScan, rainEnabled: Boolean, rainThreshold: Int): Int? {
    val rain = evaluateRain(scan, rainEnabled, rainThreshold) ?: return null
    return if (rain.fromCodeOnly) null else rain.probability
}

/** Zelfde als [rainAlertProbability], maar voor één specifiek weersmoment i.p.v. een dag-scan -
 * zelfde voorwaarde als het regen-blok in [activeWeatherAlerts]. */
fun rainAlertProbabilityForHour(
    condition: WeatherCondition,
    precipitationProbability: Int,
    precipitation: Double,
    rainEnabled: Boolean,
    rainThreshold: Int
): Int? {
    if (!rainEnabled) return null
    val isRainCondition = condition == WeatherCondition.RAIN || condition == WeatherCondition.DRIZZLE ||
        condition == WeatherCondition.RAIN_SHOWERS
    if (isRainCondition) {
        return if (precipitationProbability > 0) precipitationProbability else 80
    }
    val passesRainCrossCheck = rainThreshold == 0 || precipitation > 0.0
    return if (precipitationProbability >= rainThreshold && passesRainCrossCheck) precipitationProbability else null
}

// Titel + submelding voor regen-specifieke meldingen: zie WeatherReason.toRainNotificationParts().
// Voor de algemene (niet-regen-specifieke) reden/kans-opsplitsing: zie combineWeatherReasonsForNotification().

/**
 * Dagrand voor de dag-vensters ("Dag ervoor" / "Zelfde dag"): de nachtelijke uren tellen niet mee.
 *
 * Zonder deze grens liep het venster van vandaag door tot middernacht, waardoor regen om 23:00 een
 * melding opleverde midden op de dag - terwijl er op dat tijdstip niets gepland staat en niemand er
 * iets aan heeft. Beide dag-meldingen gaan over "hoe wordt mijn dag", niet over de nacht.
 *
 * Let op: dit geldt alleen voor de twee dag-vensters. De melding rond een agenda-item kijkt naar het
 * tijdstip van dat item zelf en mag dus wél buiten deze uren vallen (nachtdienst, vroege vlucht).
 */
const val WEATHER_DAY_WINDOW_START_HOUR = 6
const val WEATHER_DAY_WINDOW_END_HOUR = 23

/**
 * Begin- en eindtijdstip (epoch ms) van het dag-venster op [date]: precies van
 * [WEATHER_DAY_WINDOW_START_HOUR] tot [WEATHER_DAY_WINDOW_END_HOUR].
 *
 * [scanBadWeatherWindow] telt een uur mee zodra de periode die het beschrijft dit venster raakt, dus
 * het uur van 22:00 tot 23:00 hoort er nog bij en het uur daarna niet meer.
 */
private fun dayWindowFor(date: java.time.LocalDate, zone: ZoneId): Pair<Long, Long> {
    val start = date.atTime(WEATHER_DAY_WINDOW_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    val end = date.atTime(WEATHER_DAY_WINDOW_END_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    return start to end
}

/**
 * Welke stukken van [date] er beoordeeld moeten worden, volgens het ingestelde bereik.
 *
 * - Bereik "hele dag": één venster van de hele dag (binnen de dagrand).
 * - Bereik "rond agenda-items": één venster per afspraak die dag, met de ingestelde marge ervoor en
 *   erna, geknipt op de dagrand. Staan er die dag geen afspraken, dan is de lijst leeg en volgt er
 *   dus geen melding - er is dan niets waar het weer je bij in de weg zit.
 *
 * Valt terug op de hele dag als er geen agenda gekoppeld is (dan valt er niets rond te kijken).
 */
fun weatherScanWindowsForDay(context: android.content.Context, date: java.time.LocalDate): List<Pair<Long, Long>> {
    val zone = ZoneId.systemDefault()
    val dayWindow = dayWindowFor(date, zone)

    val linkToCalendar = SettingsManager.getWeatherLinkToCalendar(context)
    if (SettingsManager.getWeatherAlertScopeWholeDay(context) || !linkToCalendar) {
        return listOf(dayWindow)
    }

    val calendarIds = SettingsManager.getWeatherBadWeatherCalendarIds(context)
    if (calendarIds.isEmpty()) return listOf(dayWindow)

    val marginMillis = SettingsManager.getWeatherAlertBeforeEventRangeMinutes(context)
        .coerceAtLeast(1) * 60_000L
    val (dayStart, dayEnd) = dayWindow

    return getUpcomingWakeUpEvents(context, CalendarView.NEXT_7_DAYS, calendarIds)
        .filter { Instant.ofEpochMilli(it.epochMillis).atZone(zone).toLocalDate() == date }
        .map { event ->
            maxOf(event.epochMillis - marginMillis, dayStart) to minOf(event.epochMillis + marginMillis, dayEnd)
        }
        .filter { (start, end) -> start < end }
}

/**
 * Waarschuwingen voor de kalenderdag van morgen (zie [WEATHER_DAY_WINDOW_START_HOUR] voor de
 * dagrand).
 *
 * Voorheen was dit een rollend venster van `nu + 24u` tot `nu + 48u`. Ging de melding om 20:00 af,
 * dan waarschuwde hij dus voor morgenavond 20:00 tot overmorgenavond 20:00: morgen overdag werd
 * nooit gescand en overmorgen wél. Vandaar meldingen over regen en onweer die "morgen" nergens te
 * bekennen waren - hij keek naar een ander etmaal dan hij zei. Het dagoverzicht in dit scherm
 * ([buildDayForecasts]) gebruikte al wél echte kalenderdagen; nu doen deze twee dat ook.
 */
fun checkTomorrowWarning(
    context: android.content.Context,
    forecast: WeatherForecast,
    rainEnabled: Boolean,
    rainThreshold: Int,
    now: Long
): DayWeatherAlerts {
    val zone = ZoneId.systemDefault()
    val tomorrow = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
    val scan = scanBadWeatherWindows(forecast, weatherScanWindowsForDay(context, tomorrow))
    return DayWeatherAlerts(
        labels = activeWeatherAlertsForScan(context, scan, rainEnabled, rainThreshold, forecast),
        categories = categorySeverityForScan(context, scan, rainEnabled, rainThreshold),
        rainProbability = rainAlertProbability(scan, rainEnabled, rainThreshold)
    )
}

/**
 * Zelfde scan als [checkTomorrowWarning], maar voor de rest van vandaag: vanaf nu tot de dagrand.
 *
 * Gaat deze melding al vóór de dagrand af (bv. 05:00), dan begint het venster alsnog pas bij
 * [WEATHER_DAY_WINDOW_START_HOUR] - anders zou de laatste nacht-regen nog meegeteld worden. Valt
 * het moment ná de dagrand, dan is het venster leeg en levert de scan niets op: er is dan geen dag
 * meer om over te waarschuwen.
 */
fun checkTodayWarning(
    context: android.content.Context,
    forecast: WeatherForecast,
    rainEnabled: Boolean,
    rainThreshold: Int,
    now: Long
): DayWeatherAlerts {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    // Wat al voorbij is telt niet meer mee: elk venster begint op zijn vroegst nu.
    val windows = weatherScanWindowsForDay(context, today)
        .map { (start, end) -> maxOf(start, now) to end }
        .filter { (start, end) -> start < end }
    val scan = scanBadWeatherWindows(forecast, windows)
    return DayWeatherAlerts(
        labels = activeWeatherAlertsForScan(context, scan, rainEnabled, rainThreshold, forecast),
        categories = categorySeverityForScan(context, scan, rainEnabled, rainThreshold),
        rainProbability = rainAlertProbability(scan, rainEnabled, rainThreshold)
    )
}

/**
 * Alle van-toepassing-zijnde waarschuwingen voor een dag-venster-scan ([BadWeatherScan]) —
 * meerdere tegelijk mogelijk (bv. zowel regen als hitte op dezelfde dag), in volgorde van ernst.
 * De basis regen-check respecteert de "Regenalarm"-toggle; de overige typen zijn allemaal opt-in
 * via Slecht weer > Extra weersomstandigheden en gelden voor alle Slecht-weer-triggers (Kort voor
 * vertrek, Dag ervoor, Zelfde dag) — niet alleen voor deze dag-venster-checks. Regen/sneeuw/
 * hagel/ijzel/onweer krijgen een kans-tier ([ProbabilityTier], vertaald op render-moment);
 * storm/orkaanwind/hitte blijven een platte constatering (geen kansdata beschikbaar voor die types).
 * Retourneert [WeatherReason] (i.p.v. kant-en-klare strings) zodat vertaling pas op het render-
 * moment gebeurt - zie WeatherReason.kt.
 *
 * Geef [forecast] mee om elke reden zijn eigen periode te laten dragen ([WeatherReason.period]).
 * Zonder forecast blijven de redenen hetzelfde, alleen zonder tijdvak - handig waar dat toch niet
 * getoond wordt.
 */
fun activeWeatherAlertsForScan(
    context: android.content.Context,
    scan: BadWeatherScan,
    rainEnabled: Boolean,
    rainThreshold: Int,
    forecast: WeatherForecast? = null
): List<WeatherReason> = buildList {
    // Elk type meet zijn eigen periode door: het venster zegt alleen dát het zich voordoet, deze
    // filters zeggen wanneer het begint en ophoudt. Onweer van 17 tot 18 uur blijft zo los van de
    // regen van 14 tot 20 uur, ook al kwamen ze uit dezelfde scan.
    fun periodFor(matches: (WeatherHourSample) -> Boolean): WeatherTimeBlock? {
        val fc = forecast ?: return null
        val flagged = scan.hourSamples.filter(matches)
        if (flagged.isEmpty()) return null
        return weatherTimeBlocks(fc, flagged, matches).firstOrNull()
    }

    if (SettingsManager.getWeatherExtraHurricaneEnabled(context) &&
        scan.maxWindGust >= SettingsManager.getWeatherExtraHurricaneThreshold(context)
    ) {
        val limit = SettingsManager.getWeatherExtraHurricaneThreshold(context)
        add(WeatherReason.flat(
            LanguageManager.getString("weather_hurricane_noun"),
            periodFor { it.windGusts >= limit }
        ))
    }
    if (SettingsManager.getWeatherExtraStormEnabled(context) &&
        scan.maxWindGust >= SettingsManager.getWeatherExtraStormThreshold(context)
    ) {
        val limit = SettingsManager.getWeatherExtraStormThreshold(context)
        add(WeatherReason.flat(
            LanguageManager.getString("weather_storm_noun"),
            periodFor { it.windGusts >= limit }
        ))
    }
    if (scan.hasThunder) {
        add(WeatherReason.tiered(
            WeatherReasonType.THUNDER, scan.thunderProbability,
            periodFor { it.condition == WeatherCondition.THUNDERSTORM }
        ))
    }
    if (SettingsManager.getWeatherExtraHailEnabled(context) && scan.hasHail) {
        add(WeatherReason.tiered(
            WeatherReasonType.HAIL, scan.hailProbability,
            periodFor { it.condition == WeatherCondition.HAIL }
        ))
    }
    if (SettingsManager.getWeatherExtraIceRoadEnabled(context) && scan.hasIce) {
        add(WeatherReason.tiered(
            WeatherReasonType.ICE_ROAD, scan.iceProbability,
            periodFor {
                it.condition == WeatherCondition.FREEZING_RAIN || it.condition == WeatherCondition.FREEZING_DRIZZLE
            }
        ))
    }
    val isSnowHour = { sample: WeatherHourSample ->
        sample.condition == WeatherCondition.SNOW || sample.condition == WeatherCondition.SNOW_SHOWERS
    }
    if (SettingsManager.getWeatherExtraWetSnowEnabled(context) && scan.hasWetSnow) {
        add(WeatherReason.tiered(
            WeatherReasonType.WET_SNOW, scan.snowProbability,
            // Natte sneeuw is sneeuw bij temperaturen boven nul; de periode loopt dus alleen zolang
            // dat ook echt zo is.
            periodFor { isSnowHour(it) && it.temperature > 0.0 }
        ))
    } else if (SettingsManager.getWeatherExtraSnowEnabled(context) && scan.hasSnow) {
        add(WeatherReason.tiered(
            weatherReasonTypeForCode(scan.maxSnowCode), scan.snowProbability,
            periodFor(isSnowHour)
        ))
    }
    // Regen: kans boven de drempel én bevestiging (regencode of daadwerkelijke neerslag) binnen
    // hetzelfde uur - zie [evaluateRain] voor waarom dat laatste eerder misging.
    evaluateRain(scan, rainEnabled, rainThreshold)?.let { rain ->
        val condition = WeatherCondition.fromWmoCode(rain.weatherCode)
        val isActualRainCode = condition == WeatherCondition.RAIN || condition == WeatherCondition.DRIZZLE ||
            condition == WeatherCondition.RAIN_SHOWERS || condition == WeatherCondition.FREEZING_RAIN ||
            condition == WeatherCondition.FREEZING_DRIZZLE
        // Geen regen-code op het piekuur (bv. Open-Meteo geeft soms een neerslagkans door bij een
        // uur dat als "helder" geboekt staat): dan generiek "regen" i.p.v. een onzinnige tekst als
        // "kans op helder weer". Bij [RainOutcome.fromCodeOnly] forceert kans 100 de CERTAIN-tier,
        // dus de platte constatering i.p.v. een "kans op"-zin.
        val type = if (isActualRainCode) weatherReasonTypeForCode(rain.weatherCode) else WeatherReasonType.RAIN_NORMAL
        val period = forecast?.let { rainTimeBlocks(it, rain, rainThreshold).firstOrNull() }
        add(WeatherReason.tiered(type, rain.probability, period))
    }
    if (SettingsManager.getWeatherExtraHeatEnabled(context) &&
        scan.maxTemp >= SettingsManager.getWeatherExtraHeatThreshold(context)
    ) {
        val limit = SettingsManager.getWeatherExtraHeatThreshold(context)
        add(WeatherReason.flat(
            LanguageManager.getString("weather_heat_noun"),
            periodFor { it.temperature >= limit }
        ))
    }
}

/** Numerieke ernst-inschatting van een dag-scan, gebruikt om "vandaag" tegen "morgen" af te wegen. */
/** 1 = zacht/licht, 2 = normaal/matig, 3 = hard/hevig — afgeleid van de exacte WMO-code. */
private fun rainCodeIntensity(code: Int): Int = when (code) {
    55, 65, 75, 82, 86 -> 3
    53, 63, 73, 81 -> 2
    else -> 1
}

private fun categorySeverityForScan(
    context: android.content.Context,
    scan: BadWeatherScan,
    rainEnabled: Boolean,
    rainThreshold: Int
): CategorySeverity {
    val hurricane = if (SettingsManager.getWeatherExtraHurricaneEnabled(context) &&
        scan.maxWindGust >= SettingsManager.getWeatherExtraHurricaneThreshold(context)) 1 else 0
    val storm = if (SettingsManager.getWeatherExtraStormEnabled(context) &&
        scan.maxWindGust >= SettingsManager.getWeatherExtraStormThreshold(context)) 1 else 0
    val thunder = if (scan.hasThunder) 1 else 0
    val hail = if (SettingsManager.getWeatherExtraHailEnabled(context) && scan.hasHail) 1 else 0
    val ice = if (SettingsManager.getWeatherExtraIceRoadEnabled(context) && scan.hasIce) 1 else 0
    val heat = if (SettingsManager.getWeatherExtraHeatEnabled(context) &&
        scan.maxTemp >= SettingsManager.getWeatherExtraHeatThreshold(context)) 1 else 0
    val snow = if ((SettingsManager.getWeatherExtraWetSnowEnabled(context) || SettingsManager.getWeatherExtraSnowEnabled(context)) && scan.hasSnow) {
        rainCodeIntensity(scan.maxSnowCode)
    } else 0
    // Zelfde per-uur-beoordeling als de melding zelf, zodat "voegt morgen iets toe t.o.v. vandaag?"
    // op precies dezelfde regen-uitkomst leunt als de tekst die de gebruiker uiteindelijk ziet.
    val rain = evaluateRain(scan, rainEnabled, rainThreshold)?.let { rainCodeIntensity(it.weatherCode) } ?: 0
    return CategorySeverity(hurricane, storm, thunder, hail, ice, snow, rain, heat)
}

data class BadWeatherScan(
    val maxRain: Int,
    /** WMO-weercode op het moment van [maxRain] — voor een intensiteits-omschrijving i.p.v. percentage. */
    val maxRainCode: Int,
    /** True als minstens één uur een regen-WMO-code heeft, ongeacht neerslagkans. */
    val hasRainCode: Boolean,
    /** Ergste regen-WMO-code in het window (hoogste intensiteit), voor fallback als neerslagkans ontbreekt. */
    val worstRainCode: Int,
    val hasSnow: Boolean,
    val maxSnowCode: Int,
    /** Neerslagkans op het moment van [maxSnowCode] — voor de kans-formulering. */
    val snowProbability: Int,
    val hasIce: Boolean,
    val iceProbability: Int,
    val hasHail: Boolean,
    val hailProbability: Int,
    val hasThunder: Boolean,
    val thunderProbability: Int,
    val hasWetSnow: Boolean,
    val maxWindGust: Double,
    val maxTemp: Double,
    val maxPrecipitation: Double,
    /** Elk gescand uur apart, in tijdsvolgorde — zie [WeatherHourSample] en [evaluateRain]. */
    val hourSamples: List<WeatherHourSample> = emptyList()
)

/**
 * De regen-relevante meetwaarden van één gescand uur, bij elkaar gehouden.
 *
 * Bestaat omdat [BadWeatherScan] verder alleen maxima over het hele venster bewaart, en die maxima
 * uit verschillende uren kunnen komen. De regen-beslissing werd daardoor genomen op een combinatie
 * die nergens tegelijk voorkwam: "hoogste neerslagkans in het venster" (bv. 47% om 10:00) samen met
 * "ergens in het venster een regencode of neerslag" (bv. een bui om 23:00). Zo bevestigde het ene
 * uur de kans van het andere en ontstonden meldingen die nergens op sloegen. Per uur bewaren maakt
 * de eis "kans én bevestiging in hetzelfde uur" mogelijk - zie [evaluateRain].
 */
data class WeatherHourSample(
    val epochMillis: Long,
    val probability: Int,
    val weatherCode: Int,
    val precipitation: Double,
    /** True als [weatherCode] zelf regen/motregen/buien aangeeft. */
    val hasRainCode: Boolean,
    val condition: WeatherCondition,
    val temperature: Double,
    val windGusts: Double
)

/**
 * Aaneengesloten periode waarin het weer aan de waarschuwings-voorwaarde voldoet, klaar om als
 * tijdvak in een melding te zetten ("Regen om 2 tot 8").
 *
 * Gebruik [rainTimeBlocks] om deze te krijgen: die meet de periode door over de hele voorspelling.
 * [buildWeatherTimeBlocks] rechtstreeks aanroepen op de uren van één venster levert grenzen op die
 * op dat venster geknipt zijn - en dus een tijdvak dat exact klinkt maar te kort is.
 */
data class WeatherTimeBlock(
    /** Begin van de periode. */
    val startMillis: Long,
    /** Einde van de periode. */
    val endMillis: Long,
    /** True als er binnen hetzelfde venster later nóg een blok volgt, na een gat van meerdere uren. */
    val hasLater: Boolean
)

/**
 * Hoeveel droge uren er tussen twee rake uren mogen zitten zonder dat het als twee losse periodes
 * geldt.
 *
 * Op 1 uur: regen om 14, 15, droog om 16, weer om 17 en 18 wordt één periode van 14 tot 18. Zonder
 * die soepelheid valt vrijwel elke regenperiode uiteen in losse uurtjes; met veel meer wordt "van 14
 * tot 19" beloofd waar het er drie regende.
 */
const val WEATHER_BLOCK_MAX_GAP_HOURS = 1

private const val ONE_HOUR_MILLIS = 60L * 60L * 1000L

/**
 * Voegt losse rake uren samen tot periodes.
 *
 * Over de tijdstippen: Open-Meteo geeft neerslag en neerslagkans als waarde ván het uur ervóór - het
 * getal bij 15:00 beschrijft dus 14:00 tot 15:00. Een periode begint daarom een uur vóór het eerste
 * rake tijdstip en eindigt op het laatste. Drie rake uren op 15, 16 en 17 betekent dus regen van 14
 * tot 17 uur, niet van 15 tot 18.
 */
fun buildWeatherTimeBlocks(hours: List<WeatherHourSample>): List<WeatherTimeBlock> {
    if (hours.isEmpty()) return emptyList()
    val sorted = hours.sortedBy { it.epochMillis }
    val maxGapMillis = (WEATHER_BLOCK_MAX_GAP_HOURS + 1) * ONE_HOUR_MILLIS

    val ranges = mutableListOf<Pair<Long, Long>>()
    var first = sorted.first().epochMillis
    var last = first
    sorted.drop(1).forEach { sample ->
        if (sample.epochMillis - last <= maxGapMillis) {
            last = sample.epochMillis
        } else {
            ranges.add(first to last)
            first = sample.epochMillis
            last = sample.epochMillis
        }
    }
    ranges.add(first to last)

    return ranges.mapIndexed { index, (firstHit, lastHit) ->
        WeatherTimeBlock(
            startMillis = firstHit - ONE_HOUR_MILLIS,
            endMillis = lastHit,
            hasLater = index < ranges.lastIndex
        )
    }
}

/**
 * Uitkomst van de regen-beoordeling over een venster: welke uren voldoen er écht, en met welke kans
 * en weercode moet daarover gecommuniceerd worden.
 */
data class RainOutcome(
    /** Hoogste neerslagkans onder [hours]. Bij [fromCodeOnly] is dit 100 (de code stelt het vast). */
    val probability: Int,
    /** Weercode uit hetzelfde uur als [probability], of de zwaarste code bij [fromCodeOnly]. */
    val weatherCode: Int,
    /** De uren die zelf aan de voorwaarde voldoen, in tijdsvolgorde. Basis voor het tijdvak in de tekst. */
    val hours: List<WeatherHourSample>,
    /**
     * True als het model helemaal geen neerslagkansen levert (overal 0) en de beoordeling puur op de
     * WMO-code steunt. De kans is dan niet bekend, dus er hoort geen percentage getoond te worden.
     */
    val fromCodeOnly: Boolean
)

/**
 * De periodes waarin het regent, doorgemeten over de héle voorspelling in plaats van alleen binnen
 * het gescande venster.
 *
 * Het venster bepaalt of er gewaarschuwd moet worden ("raakt dit mijn afspraak?"), maar het is een
 * slechte maatstaf voor hoe lang het duurt. Bij een afspraak van 12 tot 15 uur en regen van 14 tot
 * 20 uur ziet de vensterscan alleen het stukje tot 15:00 en zou de melding "van 14 tot 15 uur"
 * zeggen: exact klinkend en fout. Andersom net zo goed: regen die al om 12:00 begon en doorloopt,
 * hoort niet als "vanaf 14:00" gemeld te worden.
 *
 * Daarom hier de scheiding: [outcome] zegt wát er gemeld wordt (bepaald binnen het venster), deze
 * functie zegt hoe lang het duurt (bepaald over alles wat het model levert). Alleen periodes die
 * minstens één uur uit [outcome] bevatten tellen mee - regen elders in de week blijft dus buiten
 * beeld.
 */
fun rainTimeBlocks(forecast: WeatherForecast, outcome: RainOutcome, rainThreshold: Int): List<WeatherTimeBlock> =
    weatherTimeBlocks(forecast, outcome.hours) { qualifiesAsRainExtent(it, rainThreshold) }

/**
 * De regenperiode rond [timestamp], voor de melding die op één specifiek uur beoordeelt ("kijk puur
 * naar het weer op het tijdstip van de afspraak").
 *
 * Ook daar hoort een tijdvak bij: juist bij een afspraak wil je weten hoe lang die bui aanhoudt. Dat
 * ene uur zegt daar niets over, dus wordt het vanaf daar doorgemeten - precies zoals bij een
 * vensterscan. Levert een periode van één uur op als de regen niet doorloopt.
 */
fun rainPeriodAround(forecast: WeatherForecast, timestamp: Long, rainThreshold: Int): WeatherTimeBlock? {
    val hour = hourSamplesFor(forecast, timestamp - 1, timestamp).firstOrNull() ?: return null
    return weatherTimeBlocks(forecast, listOf(hour)) { qualifiesAsRainExtent(it, rainThreshold) }.firstOrNull()
}

/**
 * Algemene versie van [rainTimeBlocks]: meet de periodes door voor elk weertype.
 *
 * [flaggedHours] zijn de uren die binnen het gescande venster aanleiding gaven tot de waarschuwing;
 * [stillCounts] bepaalt welke uren daarbuiten bij dezelfde periode horen. Zo krijgt onweer zijn eigen
 * begin en einde, los van dat van de regen, ook al zaten ze in hetzelfde venster.
 */
fun weatherTimeBlocks(
    forecast: WeatherForecast,
    flaggedHours: List<WeatherHourSample>,
    stillCounts: (WeatherHourSample) -> Boolean
): List<WeatherTimeBlock> {
    val flagged = flaggedHours.map { it.epochMillis }.toSet()
    if (flagged.isEmpty()) return emptyList()

    val qualifying = allHourSamples(forecast).filter(stillCounts)
    // Vangnet: zou het doormeten om wat voor reden dan ook niets opleveren (bv. een uur dat binnen
    // het venster via de code-fallback meetelde), val dan terug op de uren uit het venster zelf.
    val blocks = buildWeatherTimeBlocks(qualifying.ifEmpty { flaggedHours })

    val matching = blocks.filter { block ->
        flagged.any { it > block.startMillis && it <= block.endMillis }
    }
    // hasLater opnieuw bepalen binnen wat er daadwerkelijk gemeld wordt. Over de hele voorspelling
    // geteld zou "en later opnieuw" ook aanslaan op regen van overmorgen, terwijl die zin alleen
    // bedoeld is voor een tweede periode die dezelfde melding aangaat.
    val relevant = matching.mapIndexed { index, block ->
        // Randen naar het kwartier verscherpen waar die data bestaat; zo niet, dan blijft het hele
        // uur staan (zie refineBlockEdges).
        refineBlockEdges(forecast, block.copy(hasLater = index < matching.lastIndex))
    }
    return relevant.ifEmpty { buildWeatherTimeBlocks(flaggedHours) }
}

private const val QUARTER_HOUR_MILLIS = 15L * 60L * 1000L

/**
 * Schuift de randen van [block] naar het kwartier waarop de regen echt begint en ophoudt.
 *
 * De uurdata kan niet fijner dan een heel uur: valt de bui tussen half 1 en 3, dan komt daar "van 12
 * tot 3" uit. Open-Meteo levert wél neerslag per kwartier, en daarmee is die rand scherp te krijgen.
 *
 * Uitsluitend de randen - de beslissing óf er gewaarschuwd wordt blijft volledig op de uurdata
 * staan. Reden: een neerslagkans per kwartier bestaat niet (dat is een uurwaarde), dus de drempel
 * die de gebruiker instelt is er niet op toe te passen.
 *
 * Buiten het dekkingsgebied van de hoge-resolutiemodellen smeert Open-Meteo de uurwaarde uit over
 * vier gelijke kwartieren. Dan hebben alle kwartieren van dat uur dezelfde waarde en verandert deze
 * functie niets - het hele uur blijft staan. Precies goed: verzonnen precisie is erger dan een grof
 * maar eerlijk tijdvak.
 *
 * Ook de kwartierwaarde beschrijft de periode ervóór (de waarde op 12:30 gaat over 12:15 tot 12:30),
 * dezelfde afspraak als bij de uurdata.
 */
fun refineBlockEdges(forecast: WeatherForecast, block: WeatherTimeBlock): WeatherTimeBlock {
    val minutely = forecast.minutely15 ?: return block
    if (minutely.time.isEmpty()) return block

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    val zone = ZoneId.systemDefault()
    val wet = mutableListOf<Long>()
    minutely.time.forEachIndexed { i, timeStr ->
        val precip = minutely.precipitation.getOrNull(i) ?: 0.0
        if (precip <= 0.0) return@forEachIndexed
        val millis = try {
            LocalDateTime.parse(timeStr, formatter).atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) { return@forEachIndexed }
        wet.add(millis)
    }
    if (wet.isEmpty()) return block

    // Alleen binnen het eerste en laatste uur van de periode kijken. Daarbuiten is al vastgesteld
    // dat het regent (begin) of juist niet meer (eind); dat mag deze verfijning niet omgooien.
    val firstHourEnd = block.startMillis + ONE_HOUR_MILLIS
    val lastHourStart = block.endMillis - ONE_HOUR_MILLIS

    val firstWet = wet.filter { it > block.startMillis && it <= firstHourEnd }.minOrNull()
    val lastWet = wet.filter { it > lastHourStart && it <= block.endMillis }.maxOrNull()

    // Valt er in het randuur volgens de kwartierdata geen druppel, dan is dat een tegenspraak met de
    // uurdata waar we niets zinnigs mee kunnen - dan blijft de oorspronkelijke rand staan.
    val start = firstWet?.minus(QUARTER_HOUR_MILLIS) ?: block.startMillis
    val end = lastWet ?: block.endMillis
    if (start >= end) return block
    return block.copy(startMillis = start, endMillis = end)
}

/**
 * Of een uur meetelt bij het doormeten van een regenperiode.
 *
 * Gelijk aan de detectie-voorwaarde in [evaluateRain], met één verschil: hier is een regencode of
 * daadwerkelijke neerslag altijd vereist. Bij drempel 0% ("altijd een melding", om te testen) laat
 * de detectie namelijk elk uur door, en dan zou een periode zich uitstrekken over de hele
 * voorspelling in plaats van over de regen.
 */
private fun qualifiesAsRainExtent(sample: WeatherHourSample, rainThreshold: Int): Boolean =
    sample.probability >= rainThreshold && (sample.hasRainCode || sample.precipitation > 0.0)

/** Bouwt het [WeatherHourSample] voor index [i] uit de uurdata van [forecast]. */
private fun rainHourSampleAt(forecast: WeatherForecast, i: Int, epochMillis: Long): WeatherHourSample {
    val code = forecast.hourly.weathercode[i]
    val condition = WeatherCondition.fromWmoCode(code)
    return WeatherHourSample(
        epochMillis = epochMillis,
        probability = forecast.hourly.precipitationProbability.getOrNull(i) ?: 0,
        weatherCode = code,
        precipitation = forecast.hourly.precipitation.getOrNull(i) ?: 0.0,
        hasRainCode = condition == WeatherCondition.RAIN || condition == WeatherCondition.DRIZZLE ||
            condition == WeatherCondition.RAIN_SHOWERS,
        condition = condition,
        temperature = forecast.hourly.temperature2m.getOrNull(i) ?: -273.0,
        windGusts = forecast.hourly.windGusts10m.getOrNull(i)
            ?: forecast.hourly.windspeed10m.getOrNull(i) ?: 0.0
    )
}

/**
 * Alle uren uit [forecast] waarvan de beschreven periode het venster raakt, in tijdsvolgorde.
 *
 * Losgetrokken van [scanBadWeatherWindow] zodat het doormeten van een regenperiode
 * ([rainTimeBlocks]) dezelfde uren en dezelfde vensterregel gebruikt als de detectie, zonder de rest
 * van die scan (sneeuw, onweer, wind, temperatuur) opnieuw te hoeven doen.
 */
fun hourSamplesFor(forecast: WeatherForecast, windowStart: Long, windowEnd: Long): List<WeatherHourSample> =
    hourSamplesForWindows(forecast, listOf(windowStart to windowEnd))

/**
 * Als [hourSamplesFor], maar voor meerdere vensters tegelijk: een uur telt mee zodra het er minstens
 * één raakt. Nodig sinds het bereik "alleen rond agenda-items" kan zijn - dan is er niet één venster
 * per dag, maar één per afspraak.
 */
fun hourSamplesForWindows(
    forecast: WeatherForecast,
    windows: List<Pair<Long, Long>>
): List<WeatherHourSample> {
    if (windows.isEmpty()) return emptyList()
    // Een uurwaarde beschrijft het uur vóór zijn stempel (zie
    // WeatherRepository.getPrecipitationHourForTime), dus het stempel van 15:00 gaat over 14:00 tot
    // 15:00. Meetellen als díé periode een venster raakt - niet als het stempel zelf erin valt.
    // Anders telde bij een venster van 06:00 tot 23:00 het stempel van 06:00 mee (dat de nacht van
    // 05:00 tot 06:00 beschrijft) terwijl het laatste uur vóór 23:00 juist wegviel: het hele venster
    // lag dan een uur verkeerd.
    return allHourSamples(forecast).filter { sample ->
        windows.any { (start, end) -> sample.epochMillis > start && sample.epochMillis - ONE_HOUR_MILLIS < end }
    }
}

private var cachedSampleSource: WeatherForecast? = null
private var cachedSamples: List<WeatherHourSample> = emptyList()

/**
 * Alle uren uit [forecast], één keer geparst en daarna onthouden.
 *
 * Het omzetten van de tijdstempels is verreweg het duurste deel van deze hele scan, en sinds elk
 * weertype zijn eigen periode doormeet over de volledige voorspelling zou dat anders tientallen
 * keren achter elkaar gebeuren - per dag in het weekoverzicht nog eens apart. De cache hangt aan de
 * identiteit van het forecast-object, dus zodra er verse data opgehaald wordt, vervalt hij vanzelf.
 */
private fun allHourSamples(forecast: WeatherForecast): List<WeatherHourSample> {
    if (cachedSampleSource === forecast) return cachedSamples

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    val zone = ZoneId.systemDefault()
    val samples = mutableListOf<WeatherHourSample>()
    forecast.hourly.time.forEachIndexed { i, timeStr ->
        val epochMillis = try {
            LocalDateTime.parse(timeStr, formatter).atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) { return@forEachIndexed }
        samples.add(rainHourSampleAt(forecast, i, epochMillis))
    }
    cachedSamples = samples
    cachedSampleSource = forecast
    return samples
}

/**
 * Beoordeelt de regen-voorwaarde per uur en vat het resultaat samen. Geeft null als er geen regen
 * gemeld hoort te worden.
 *
 * Een uur telt mee als het zélf boven de drempel zit én die kans in datzelfde uur bevestigd wordt
 * door een regen-WMO-code of daadwerkelijke neerslag. Die cross-check bestond al (zonder haalt
 * ensemble-ruis - hoge kans, 0 mm, geen regencode - onterecht de meldingen binnen), maar mocht
 * voorheen over verschillende uren heen bevestigd worden.
 *
 * Drempel 0% betekent bewust "altijd een melding" (om meldingen te testen): dan telt elk uur mee en
 * blijft de uitkomst gelijk aan vroeger, namelijk de hoogste kans in het venster met de weercode van
 * datzelfde uur.
 *
 * Let op het verschil met de losse-uur-variant [activeWeatherAlerts], die bij een regen-WMO-code de
 * drempel bewust helemaal negeert. Dat is daar zinnig (je kijkt naar één specifiek tijdstip), maar
 * over een heel venster zou het betekenen dat één bui-uurtje de hele dag laat waarschuwen.
 */
fun evaluateRain(scan: BadWeatherScan, rainEnabled: Boolean, rainThreshold: Int): RainOutcome? {
    if (!rainEnabled) return null

    val qualifying = scan.hourSamples.filter { sample ->
        sample.probability >= rainThreshold &&
            (rainThreshold == 0 || sample.hasRainCode || sample.precipitation > 0.0)
    }
    if (qualifying.isNotEmpty()) {
        // Hoogste kans bepaalt de formulering; de weercode komt uit datzelfde uur, zodat de tekst
        // ("flinke motregen", "zware regen") past bij het moment waarop die kans geldt.
        val peak = qualifying.maxByOrNull { it.probability } ?: return null
        return RainOutcome(peak.probability, peak.weatherCode, qualifying, fromCodeOnly = false)
    }

    // Vangnet: sommige modellen leveren helemaal geen neerslagkans (overal 0) terwijl de weercode
    // wel degelijk regen aangeeft. Dan is de code de enige bron - alleen als er echt nergens
    // kansdata is, zodat een enkel 0%-uur tussen normale uren dit vangnet niet oproept.
    if (scan.hourSamples.isNotEmpty() && scan.hourSamples.all { it.probability == 0 }) {
        val codeHours = scan.hourSamples.filter { it.hasRainCode }
        if (codeHours.isNotEmpty()) {
            val worst = codeHours.maxByOrNull { rainCodeIntensity(it.weatherCode) } ?: codeHours.first()
            return RainOutcome(100, worst.weatherCode, codeHours, fromCodeOnly = true)
        }
    }
    return null
}

fun scanBadWeatherWindow(forecast: WeatherForecast, windowStart: Long, windowEnd: Long): BadWeatherScan =
    scanBadWeatherWindows(forecast, listOf(windowStart to windowEnd))

/**
 * Als [scanBadWeatherWindow], maar over meerdere losse vensters - bv. één per agenda-afspraak van
 * die dag, in plaats van de hele dag in één keer. Een lege lijst levert een lege scan op: er is dan
 * niets om naar te kijken, dus ook niets om over te waarschuwen.
 */
fun scanBadWeatherWindows(forecast: WeatherForecast, windows: List<Pair<Long, Long>>): BadWeatherScan {
    var maxRain = 0
    var maxRainCode = 0
    var hasRainCode = false
    var worstRainCode = 0
    var hasSnow = false
    var maxSnowCode = 0
    var snowProbability = 0
    var hasIce = false
    var iceProbability = 0
    var hasHail = false
    var hailProbability = 0
    var hasThunder = false
    var thunderProbability = 0
    var hasWetSnow = false
    var maxWindGust = 0.0
    var maxTemp = -273.0
    var maxPrecipitation = 0.0

    // De uren komen uit [hourSamplesForWindows]: die kent de vensterregel (een uurwaarde beschrijft
    // het uur vóór zijn stempel) en cachet het parsen. Vroeger stond die lus hier nog een keer apart.
    val hourSamples = hourSamplesForWindows(forecast, windows)

    hourSamples.forEach { sample ->
        val code = sample.weatherCode
        val rain = sample.probability
        val condition = sample.condition
        val temp = sample.temperature
        // Neerslagkans en weercode altijd samen van hetzelfde uur bijhouden (nooit los van
        // elkaar) — anders kan het uur met de hoogste kans toevallig een niet-regen-code
        // hebben (bv. Open-Meteo geeft soms een neerslagkans door bij een uur dat als
        // "helder" geboekt staat), en zou de melding een onzinnige tekst als "kans op helder
        // weer" tonen. Zie ook de fallback in [activeWeatherAlertsForScan].
        if (rain >= maxRain) {
            maxRain = rain
            maxRainCode = code
        }
        if (sample.hasRainCode) {
            hasRainCode = true
            if (rainCodeIntensity(code) > rainCodeIntensity(worstRainCode)) worstRainCode = code
        }
        if (condition == WeatherCondition.SNOW || condition == WeatherCondition.SNOW_SHOWERS) {
            hasSnow = true
            if (rain >= snowProbability) {
                maxSnowCode = code
                snowProbability = rain
            }
            if (temp > 0.0) hasWetSnow = true
        }
        if (condition == WeatherCondition.FREEZING_RAIN || condition == WeatherCondition.FREEZING_DRIZZLE) {
            hasIce = true
            if (rain > iceProbability) iceProbability = rain
        }
        if (condition == WeatherCondition.HAIL) {
            hasHail = true
            if (rain > hailProbability) hailProbability = rain
        }
        if (condition == WeatherCondition.THUNDERSTORM) {
            hasThunder = true
            if (rain > thunderProbability) thunderProbability = rain
        }
        if (sample.precipitation > maxPrecipitation) maxPrecipitation = sample.precipitation
        if (sample.windGusts > maxWindGust) maxWindGust = sample.windGusts
        if (temp > maxTemp) maxTemp = temp
    }

    return BadWeatherScan(
        maxRain, maxRainCode, hasRainCode, worstRainCode, hasSnow, maxSnowCode, snowProbability,
        hasIce, iceProbability, hasHail, hailProbability, hasThunder, thunderProbability,
        hasWetSnow, maxWindGust, maxTemp, maxPrecipitation, hourSamples
    )
}

/** Eén regel in het per-dag-overzicht (Weer > swipe omhoog). */
private data class DayForecast(
    val date: java.time.LocalDate,
    val minTemp: Double?,
    val maxTemp: Double?,
    val condition: WeatherCondition?,
    /** Eén of meerdere waarschuwingen voor die dag (bv. regen én hitte tegelijk). */
    val warningLabels: List<WeatherReason>,
    /** Ruwe WMO-code van het zwaarste moment die dag, voor de licht/matig/zwaar-icoonvariant (zie [conditionIcon]). */
    val representativeWeatherCode: Int? = null,
)

/** Bouwt het per-dag-overzicht voor de komende [days] dagen (inclusief vandaag) uit de forecast. */
private fun buildDayForecasts(
    context: android.content.Context,
    forecast: WeatherForecast,
    rainEnabled: Boolean,
    rainThreshold: Int,
    days: Int = 7
): List<DayForecast> {
    val zone = ZoneId.systemDefault()
    val today = LocalDateTime.now(zone).toLocalDate()

    return (0 until days).map { offset ->
        val date = today.plusDays(offset.toLong())
        val (min, max) = computeMinMaxTempForDate(forecast, date)
        val condition = representativeConditionForDate(forecast, date)
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val scan = scanBadWeatherWindow(forecast, dayStart, dayEnd)
        val warningLabels = activeWeatherAlertsForScan(context, scan, rainEnabled, rainThreshold, forecast)
        val representativeCode = when (condition) {
            WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS ->
                scan.maxSnowCode.takeIf { it != 0 }
            // Zelfde regen-uitkomst als de waarschuwing in deze rij, zodat het icoon (licht/matig/
            // zwaar) hoort bij het uur waarop die waarschuwing slaat. Valt er niets te waarschuwen,
            // dan alsnog de zwaarste regencode van de dag voor een passend icoon.
            WeatherCondition.RAIN, WeatherCondition.DRIZZLE, WeatherCondition.RAIN_SHOWERS ->
                evaluateRain(scan, rainEnabled, rainThreshold)?.weatherCode?.takeIf { it != 0 }
                    ?: scan.worstRainCode.takeIf { it != 0 }
            // Hagel-intensiteit (WMO 96 licht / 99 zwaar) wordt niet apart bijgehouden in de
            // dag-scan — dagoverzicht valt terug op de lichte hagel-variant, geen functioneel gemis.
            else -> null
        }
        DayForecast(date, min, max, condition, warningLabels, representativeCode)
    }
}

/** Dag-label in de huidige app-taal: "Vandaag", "Morgen", of anders de weekdagnaam + datum. */
private fun dayLabelForDate(date: java.time.LocalDate): String {
    val zone = ZoneId.systemDefault()
    val today = LocalDateTime.now(zone).toLocalDate()
    return when (date) {
        today -> LanguageManager.getString("weather_today_word")
        today.plusDays(1) -> LanguageManager.getString("weather_tomorrow_prefix")
        else -> {
            val millis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            SimpleDateFormat("EEEE d MMM", LanguageManager.getLocale()).format(Date(millis)).replaceFirstChar { it.uppercase() }
        }
    }
}

/** Simpele dag/nacht-check (lokale tijd) voor de zon/maan-varianten van helder/licht bewolkt/half bewolkt. */
private fun isNightNow(): Boolean {
    val hour = LocalTime.now(ZoneId.systemDefault()).hour
    return hour < 6 || hour >= 20
}

/**
 * Icoon per weersconditie. [code] geeft, waar beschikbaar, de ruwe WMO-weercode door voor
 * licht/matig/zwaar-varianten van regen, regenbuien, sneeuw en sneeuwbuien (zie [rainCodeIntensity]).
 * [temperatureC] bepaalt of sneeuw als "natte sneeuw" getoond wordt (zelfde grens als de
 * natte-sneeuw-waarschuwing elders: boven 0°C). [isNight] kiest de maan i.p.v. de zon voor
 * helder/licht bewolkt/half bewolkt — relevant voor het huidige-weer-icoon, dat 's nachts bekeken
 * wordt; dagoverzicht-rijen geven altijd de dag-variant door.
 */
private fun conditionIcon(
    condition: WeatherCondition?,
    code: Int? = null,
    temperatureC: Double? = null,
    isNight: Boolean = false,
): ImageVector = when (condition) {
    WeatherCondition.CLEAR -> if (isNight) MoonIcon else SunIcon
    WeatherCondition.MOSTLY_CLEAR -> if (isNight) MoonSmallCloudIcon else SunSmallCloudIcon
    WeatherCondition.PARTLY_CLOUDY -> if (isNight) MoonCloudIcon else SunCloudIcon
    WeatherCondition.CLOUDY -> CloudyIcon
    WeatherCondition.FOG -> FogIcon
    WeatherCondition.DRIZZLE, WeatherCondition.FREEZING_DRIZZLE -> DrizzleCloudIcon
    WeatherCondition.FREEZING_RAIN -> FreezingRainIcon
    WeatherCondition.RAIN -> when (code?.let { rainCodeIntensity(it) } ?: 2) {
        1 -> RainLightIcon
        3 -> RainHeavyIcon
        else -> RainModerateIcon
    }
    WeatherCondition.RAIN_SHOWERS -> when (code?.let { rainCodeIntensity(it) } ?: 2) {
        1 -> RainShowersLightIcon
        3 -> RainShowersHeavyIcon
        else -> RainShowersModerateIcon
    }
    WeatherCondition.SNOW -> if (temperatureC != null && temperatureC > 0.0) {
        WetSnowIcon
    } else {
        when (code?.let { rainCodeIntensity(it) } ?: 2) {
            1 -> SnowLightIcon
            3 -> SnowHeavyIcon
            else -> SnowModerateIcon
        }
    }
    WeatherCondition.SNOW_SHOWERS -> if (temperatureC != null && temperatureC > 0.0) {
        WetSnowIcon
    } else {
        when (code?.let { rainCodeIntensity(it) } ?: 2) {
            1 -> SnowShowersLightIcon
            3 -> SnowShowersHeavyIcon
            else -> SnowShowersModerateIcon
        }
    }
    WeatherCondition.THUNDERSTORM -> ThunderstormIcon
    WeatherCondition.HAIL -> if (code == 99) HailHeavyIcon else HailLightIcon
    null -> CloudyIcon
}

/**
 * Icoon voor het huidige weer op de Weer-startpagina. Gebruikt, waar beschikbaar, de ruwe WMO-
 * weercode en temperatuur voor net wat meer nuance dan [conditionIcon] alleen kan bieden binnen
 * dezelfde [WeatherCondition], en kiest zelf dag/nacht (zie [isNightNow]) — dit is het icoon dat
 * ook 's nachts bekeken wordt.
 */
private fun currentConditionIcon(code: Int?, condition: WeatherCondition?, temperatureC: Double? = null): ImageVector =
    conditionIcon(condition, code, temperatureC, isNightNow())

/** Kwalitatief label voor de windsnelheid (km/u), voor onder de huidige-weer-kaart. */
private fun windSpeedLabel(windSpeed: Double): String = LanguageManager.getString(
    when {
        windSpeed < 5 -> "weather_wind_calm"
        windSpeed < 20 -> "weather_wind_weak"
        windSpeed < 40 -> "weather_wind_moderate"
        windSpeed < 60 -> "weather_wind_strong"
        else -> "weather_wind_very_strong"
    }
)

private fun windDirectionLabel(degrees: Int): String = LanguageManager.getString(
    when ((degrees + 22) / 45 % 8) {
        0 -> "weather_dir_n"
        1 -> "weather_dir_ne"
        2 -> "weather_dir_e"
        3 -> "weather_dir_se"
        4 -> "weather_dir_s"
        5 -> "weather_dir_sw"
        6 -> "weather_dir_w"
        7 -> "weather_dir_nw"
        else -> "weather_dir_n"
    }
)

/** Bijpassend icoon per windsnelheid-tier — zwaarder icoon naarmate de wind harder waait. */
private fun windIconForSpeed(windSpeed: Double): ImageVector = when {
    windSpeed < 40 -> Icons.Filled.Air
    windSpeed < 60 -> Icons.Filled.Cyclone
    else -> Icons.Filled.Tornado
}

/**
 * Korte natuurlijke-taal samenvatting van het huidige weer (temperatuur + droog/nat), gebruikt op
 * de "geen waarschuwing"-kaart in plaats van kaal "geen waarschuwing" te tonen — bv. "Warm en
 * droog" (kort, geen "het is"-inleiding).
 */
private fun currentWeatherSummary(condition: WeatherCondition?, temperature: Double, precipitationProbability: Int? = null): String {
    val tempKey = when {
        temperature >= 28 -> "weather_temp_warm_extreme"
        temperature >= 22 -> "weather_temp_warm"
        temperature >= 15 -> "weather_temp_pleasant"
        temperature >= 8 -> "weather_temp_cool"
        temperature >= 0 -> "weather_temp_cold"
        else -> "weather_temp_cold_extreme"
    }
    val conditionKey = when (condition) {
        WeatherCondition.CLEAR, WeatherCondition.MOSTLY_CLEAR, WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.CLOUDY, WeatherCondition.FOG, null -> {
            if (precipitationProbability != null && precipitationProbability >= 50) "weather_cond_rainy" else "weather_cond_dry"
        }
        WeatherCondition.DRIZZLE, WeatherCondition.FREEZING_DRIZZLE -> "weather_cond_light_wet"
        WeatherCondition.RAIN, WeatherCondition.RAIN_SHOWERS, WeatherCondition.FREEZING_RAIN -> "weather_cond_wet"
        WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> "weather_cond_snowy"
        WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL -> "weather_cond_turbulent"
    }
    val tempWord = LanguageManager.getString(tempKey).replaceFirstChar { it.uppercase() }
    val conditionWord = LanguageManager.getString(conditionKey)
    return LanguageManager.getString("weather_summary_combined").replace("{temp}", tempWord).replace("{condition}", conditionWord)
}

private fun conditionLabel(condition: WeatherCondition?): String = LanguageManager.getString(
    when (condition) {
        WeatherCondition.CLEAR -> "weather_condition_clear"
        WeatherCondition.MOSTLY_CLEAR -> "weather_condition_mostly_clear"
        WeatherCondition.PARTLY_CLOUDY -> "weather_condition_partly_cloudy"
        WeatherCondition.CLOUDY -> "weather_condition_cloudy"
        WeatherCondition.FOG -> "weather_condition_fog"
        WeatherCondition.DRIZZLE -> "weather_condition_drizzle"
        WeatherCondition.FREEZING_DRIZZLE -> "weather_condition_freezing_drizzle"
        WeatherCondition.RAIN -> "weather_condition_rain"
        WeatherCondition.FREEZING_RAIN -> "weather_condition_freezing_rain"
        WeatherCondition.SNOW -> "weather_condition_snow"
        WeatherCondition.SNOW_SHOWERS -> "weather_condition_snow_showers"
        WeatherCondition.RAIN_SHOWERS -> "weather_condition_rain_showers"
        WeatherCondition.THUNDERSTORM -> "weather_condition_thunderstorm"
        WeatherCondition.HAIL -> "weather_condition_hail"
        null -> "weather_condition_unknown"
    }
)

// weatherCodeDescription/weatherCodeNounPhrase/probabilityLabel/splitBadWeatherLabels zijn vervangen
// door het gestructureerde WeatherReason-model (zie WeatherReason.kt) - dat vertaalt pas op het
// render-moment i.p.v. Nederlandse zinnen op te bouwen en ze later weer te ontleden.

/**
 * Corrigeert de weergegeven neerslagkans wanneer ensemble-modellen een hoge probability geven
 * maar er geen neerslag daadwerkelijk verwacht wordt (precipitation = 0mm en geen regen-WMO-code).
 * Voorkomt dat de UI "90%" toont terwijl alle andere signalen droog weer aangeven.
 */
private fun effectivePrecipitationProbability(
    precipitationProbability: Int,
    precipitation: Double,
    condition: WeatherCondition
): Int {
    val isWetCondition = condition == WeatherCondition.RAIN || condition == WeatherCondition.DRIZZLE ||
        condition == WeatherCondition.RAIN_SHOWERS || condition == WeatherCondition.FREEZING_RAIN ||
        condition == WeatherCondition.FREEZING_DRIZZLE || condition == WeatherCondition.SNOW ||
        condition == WeatherCondition.SNOW_SHOWERS || condition == WeatherCondition.THUNDERSTORM ||
        condition == WeatherCondition.HAIL
    if (isWetCondition) return precipitationProbability
    if (precipitation > 0.0) return precipitationProbability
    return minOf(precipitationProbability, 15)
}

/**
 * Alle van-toepassing-zijnde waarschuwingen voor één weersmoment (nu, of een specifiek
 * event-tijdstip) — meerdere tegelijk mogelijk (bv. regen én hitte tegelijk), elk met bijpassend
 * icoon, in volgorde van ernst. Regen/sneeuw/hagel/ijzel/onweer krijgen een kans-tier (vertaald op
 * render-moment, zie [WeatherReason]); storm/orkaanwind/hitte zijn platte constateringen.
 */
fun activeWeatherAlerts(
    context: android.content.Context,
    weatherCode: Int,
    condition: WeatherCondition,
    temperature: Double,
    precipitationProbability: Int,
    precipitation: Double,
    windGusts: Double,
    rainEnabled: Boolean,
    rainThreshold: Int
): List<WeatherAlert> = buildList {
    if (SettingsManager.getWeatherExtraHurricaneEnabled(context) &&
        windGusts >= SettingsManager.getWeatherExtraHurricaneThreshold(context)
    ) {
        val text = LanguageManager.getString("weather_hurricane_gusts").replace("{speed}", windGusts.roundToInt().toString())
        add(WeatherAlert(WeatherReason.flat(text), Icons.Filled.Tornado))
    }
    if (SettingsManager.getWeatherExtraStormEnabled(context) &&
        windGusts >= SettingsManager.getWeatherExtraStormThreshold(context)
    ) {
        val text = LanguageManager.getString("weather_storm_gusts").replace("{speed}", windGusts.roundToInt().toString())
        add(WeatherAlert(WeatherReason.flat(text), Icons.Filled.Cyclone))
    }
    if (condition == WeatherCondition.THUNDERSTORM) {
        val prob = if (precipitationProbability > 0) precipitationProbability else 80
        add(WeatherAlert(WeatherReason.tiered(WeatherReasonType.THUNDER, prob), ThunderstormIcon))
    }
    if (SettingsManager.getWeatherExtraHailEnabled(context) && condition == WeatherCondition.HAIL) {
        val prob = if (precipitationProbability > 0) precipitationProbability else 80
        val hailIcon = if (weatherCode == 99) HailHeavyIcon else HailLightIcon
        add(WeatherAlert(WeatherReason.tiered(WeatherReasonType.HAIL, prob), hailIcon))
    }
    if (SettingsManager.getWeatherExtraIceRoadEnabled(context) &&
        (condition == WeatherCondition.FREEZING_RAIN || condition == WeatherCondition.FREEZING_DRIZZLE)
    ) {
        val prob = if (precipitationProbability > 0) precipitationProbability else 80
        add(WeatherAlert(WeatherReason.tiered(WeatherReasonType.ICE_ROAD, prob), IcyRoadIcon))
    }
    val isSnowCondition = condition == WeatherCondition.SNOW || condition == WeatherCondition.SNOW_SHOWERS
    if (isSnowCondition && SettingsManager.getWeatherExtraWetSnowEnabled(context) && temperature > 0.0) {
        val prob = if (precipitationProbability > 0) precipitationProbability else 80
        add(WeatherAlert(WeatherReason.tiered(WeatherReasonType.WET_SNOW, prob), WetSnowIcon))
    } else if (isSnowCondition && SettingsManager.getWeatherExtraSnowEnabled(context)) {
        val prob = if (precipitationProbability > 0) precipitationProbability else 80
        val snowIcon = when (rainCodeIntensity(weatherCode)) {
            3 -> if (condition == WeatherCondition.SNOW_SHOWERS) SnowShowersHeavyIcon else SnowHeavyIcon
            2 -> if (condition == WeatherCondition.SNOW_SHOWERS) SnowShowersModerateIcon else SnowModerateIcon
            else -> if (condition == WeatherCondition.SNOW_SHOWERS) SnowShowersLightIcon else SnowLightIcon
        }
        add(WeatherAlert(WeatherReason.tiered(weatherReasonTypeForCode(weatherCode), prob), snowIcon))
    }
    val isRainCondition = condition == WeatherCondition.RAIN || condition == WeatherCondition.DRIZZLE ||
        condition == WeatherCondition.RAIN_SHOWERS
    if (rainEnabled) {
        if (isRainCondition) {
            // WMO code zegt regen — altijd tonen. Sommige modellen leveren geen neerslagkans
            // (retourneren null, default 0); gebruik dan 80 zodat de tier de beschrijving als feit
            // toont i.p.v. "kleine kans" bij een code die al regen aangeeft.
            val prob = if (precipitationProbability > 0) precipitationProbability else 80
            val rainIcon = when (rainCodeIntensity(weatherCode)) {
                3 -> if (condition == WeatherCondition.RAIN_SHOWERS) RainShowersHeavyIcon else RainHeavyIcon
                2 -> if (condition == WeatherCondition.RAIN_SHOWERS) RainShowersModerateIcon else RainModerateIcon
                else -> if (condition == WeatherCondition.RAIN_SHOWERS) RainShowersLightIcon else RainLightIcon
            }
            add(WeatherAlert(WeatherReason.tiered(weatherReasonTypeForCode(weatherCode), prob), rainIcon))
        } else if (precipitationProbability >= rainThreshold && (rainThreshold == 0 || precipitation > 0.0)) {
            // WMO code zegt geen regen, maar neerslagkans is hoog EN er is daadwerkelijk neerslag
            // verwacht — val terug op generiek "regen". Zonder precipitation-check zou ensemble-ruis
            // (hoge kans maar 0mm) onterecht regen-alerts triggeren. Drempel op 0 = altijd doorlaten
            // (zelfde bypass als bij de hele-dag-scan, zie activeWeatherAlertsForScan) - anders komt
            // een kleine kans nooit langs omdat modellen daarbij vaak 0.0mm teruggeven.
            add(WeatherAlert(WeatherReason.tiered(WeatherReasonType.RAIN_NORMAL, precipitationProbability), RainModerateIcon))
        }
    }
    if (SettingsManager.getWeatherExtraHeatEnabled(context) &&
        temperature >= SettingsManager.getWeatherExtraHeatThreshold(context)
    ) {
        add(WeatherAlert(WeatherReason.flat(LanguageManager.getString("weather_heat_statement")), Icons.Filled.WbSunny))
    }
}

// ── Activity ──────────────────────────────────────────────────────────────────

class WeatherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SettingsManager.applySystemBarColors(window, this)

        TimerSettingsStateHolder.init(this)
        GlobalTimerManager.init(this)
        StopwatchStateHolder.init(this)
        GlobalInAppMessageManager.init(this)

        setContent {
            MaterialTheme {
                WeatherScreen()
            }
        }
    }
}

// ── Screens ───────────────────────────────────────────────────────────────────

@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val currentScreenId = "WEATHER"
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val showNavButtons = SettingsManager.getShowNavButtons(context)
    val swipeEnabled = SettingsManager.getSwipeEnabled(context)
    val configuration = LocalConfiguration.current
    val isVerticalMode = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val arrowBottomPadding = if (isVerticalMode) 2.dp else 64.dp

    var showSettings by remember { mutableStateOf(false) }
    var settingsSubPage by remember { mutableStateOf("main") }
    var showForecast by remember { mutableStateOf(false) }
    // Start vanuit de proces-brede cache (zie WeatherHomeCache hieronder) i.p.v. altijd Loading:
    // als er van een eerdere keer al (evt. wat oudere) data bekend is, tonen we die meteen terwijl
    // er op de achtergrond ververst wordt, i.p.v. steeds opnieuw een lege/spinner-staat te tonen.
    var uiState by remember { mutableStateOf(WeatherHomeCache.lastUiState ?: WeatherUiState.Loading) }
    // Gedeelde instantie (zie WeatherRepository.shared) i.p.v. een nieuwe per Activity-instantie,
    // zodat de 30-minuten-cache ook overleeft als je even weg en terug navigeert.
    val weatherRepo = remember { WeatherRepository.shared }

    // Herlaadt bij het (terug)betreden van Weer home — ook bij terugkeer vanuit Instellingen of
    // Weer per dag, zodat instellingen die net gewijzigd zijn (locatie, weermodel, drempels, ...)
    // meteen zichtbaar zijn in plaats van de oude, bij het eerste bezoek geladen staat. Blijft
    // daarna elke minuut verversen zolang de homepage in beeld is (zelfde ritme als de widgets
    // hieronder) — zodat de kaart ook update als hij gewoon een tijd open blijft staan, bv. de
    // aftelling tot een event, het 18:00-omslagpunt voor de avond-temperatuurwissel-tekst, of een
    // event dat net binnen de 24-uurs-drempel valt. WeatherRepository cachet de forecast zelf 30
    // minuten, dus dit poll'tje kost geen extra netwerkverkeer per minuut.
    // Zet uiState NIET meer terug naar Loading bij elke (her)start van dit effect: dat gaf een
    // onnodige spinner-flits telkens als je terugkomt van Instellingen/Weer per dag, terwijl de
    // vorige data nog prima klopt. De verse data vervangt de oude gewoon zodra hij binnen is.
    LaunchedEffect(showSettings, showForecast) {
        if (showSettings || showForecast) return@LaunchedEffect
        while (true) {
            uiState = loadWeatherUiState(context, weatherRepo)
            WeatherHomeCache.lastUiState = uiState
            delay(60_000)
        }
    }

    // Widgets (Weer instellingen > Widgets): tot 2 widgets met tot 3 HA-entiteiten elk, boven het
    // weer op de homepage. Config zelf wordt pas opnieuw gelezen bij terugkeer van Instellingen;
    // de live waardes worden elke minuut ververst zolang de homepage in beeld is.
    val widget1Entities = remember(showSettings) { SettingsManager.getWeatherWidgetEntities(context, 1) }
    val widget2Entities = remember(showSettings) { SettingsManager.getWeatherWidgetEntities(context, 2) }
    val activeWidgets = remember(widget1Entities, widget2Entities) {
        listOf(widget1Entities, widget2Entities).filter { it.isNotEmpty() }
    }
    // Ook hier starten vanuit de laatst bekende waardes i.p.v. leeg, zelfde reden als hierboven.
    var widgetLiveValues by remember { mutableStateOf(WeatherHomeCache.lastWidgetValues) }
    // Wordt true zodra de widgets minstens 1x geprobeerd zijn te laden (ook als er geen widgets
    // geconfigureerd zijn) — gebruikt hieronder om de weerkaart en de widgets pas samen te tonen.
    var widgetsReady by remember { mutableStateOf(WeatherHomeCache.widgetsEverLoaded) }
    val haWidgetStorage = remember { HomeAssistantSettingsStorage(context.applicationContext) }
    val haWidgetRepository = remember { HomeAssistantRepository(HomeAssistantClient, haWidgetStorage) }

    LaunchedEffect(showSettings, showForecast, activeWidgets) {
        if (showSettings || showForecast) return@LaunchedEffect
        val entityIds = activeWidgets.flatten().map { it.entityId }.distinct()
        if (entityIds.isEmpty()) {
            widgetLiveValues = emptyMap()
            WeatherHomeCache.lastWidgetValues = emptyMap()
            widgetsReady = true
            WeatherHomeCache.widgetsEverLoaded = true
            return@LaunchedEffect
        }
        while (true) {
            val values = mutableMapOf<String, WeatherWidgetLiveValue>()
            withContext(Dispatchers.IO) {
                entityIds.forEach { entityId ->
                    try {
                        val response = haWidgetRepository.getEntityState(entityId)
                        val friendlyName = response.attributes?.get("friendly_name")?.toString()?.trim('"')
                        val unit = response.attributes?.get("unit_of_measurement")?.toString()?.trim('"')
                        val deviceClass = response.attributes?.get("device_class")?.toString()?.trim('"')
                        values[entityId] = WeatherWidgetLiveValue(state = response.state, unit = unit, friendlyName = friendlyName, deviceClass = deviceClass)
                    } catch (e: Exception) {
                        // Kortstondige netwerkhapering: val terug op de laatst bekende waarde voor
                        // deze entiteit i.p.v. hem leeg te tonen ("—") terwijl er al eens een
                        // waarde bekend was.
                        widgetLiveValues[entityId]?.let { values[entityId] = it }
                    }
                }
            }
            widgetLiveValues = values
            WeatherHomeCache.lastWidgetValues = values
            widgetsReady = true
            WeatherHomeCache.widgetsEverLoaded = true
            delay(60_000)
        }
    }

    // Pas alles-tegelijk tonen zodra zowel de weerkaart als de widgets minstens 1x klaar zijn -
    // anders verschijnen de widgets soms merkbaar eerder (of later) dan de rest van de pagina.
    val weatherReady = uiState !is WeatherUiState.Loading
    val pageContentReady = weatherReady && widgetsReady

    AppBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(showSettings, showForecast) {
                    awaitEachGesture {
                        @Suppress("UNUSED_VARIABLE")
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        var dragY = 0f
                        var dragX = 0f
                        var triggered = false

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull()
                            if (change == null || !change.pressed) break

                            if (!triggered) {
                                if (!swipeEnabled) continue
                                dragX += change.position.x - change.previousPosition.x
                                dragY += change.position.y - change.previousPosition.y

                                val dragThreshold = 50f

                                if (showSettings) {
                                    if (settingsSubPage == "main" && abs(dragY) > abs(dragX) && dragY < -dragThreshold) {
                                        showSettings = false
                                        triggered = true
                                    } else if (settingsSubPage == "main" && abs(dragY) > abs(dragX) && dragY > dragThreshold) {
                                        // Swipe omlaag op de Weerinstellingen-hoofdpagina -> direct naar
                                        // laatst bezochte weer-subpagina, zelfde patroon als hoofdinstellingen.
                                        val lastWeatherPage = SettingsManager.getLastVisitedWeatherSubPage(context)
                                        if (lastWeatherPage.isNotEmpty()) {
                                            settingsSubPage = lastWeatherPage
                                            triggered = true
                                        }
                                    }
                                } else if (showForecast) {
                                    if (abs(dragY) > abs(dragX) && dragY > dragThreshold) {
                                        showForecast = false
                                        triggered = true
                                    }
                                } else {
                                    if (abs(dragX) > abs(dragY)) {
                                        if (dragX > dragThreshold) {
                                            NavigationManager.navigateLeft(context, currentScreenId)
                                            triggered = true
                                        } else if (dragX < -dragThreshold) {
                                            NavigationManager.navigateRight(context, currentScreenId)
                                            triggered = true
                                        }
                                    } else {
                                        if (dragY > dragThreshold) {
                                            showSettings = true
                                            triggered = true
                                        } else if (dragY < -dragThreshold) {
                                            showForecast = true
                                            triggered = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            val leftTarget = NavigationManager.getSwipeLeftTarget(context, currentScreenId)
            val rightTarget = NavigationManager.getSwipeRightTarget(context, currentScreenId)

            if (showSettings) {
                WeatherSettingsPage(
                    subPage = settingsSubPage,
                    onSubPageChange = { settingsSubPage = it },
                    onBack = { showSettings = false }
                )

                SwipeIndicators(
                    isVertical = isVerticalMode,
                    showLeft = false,
                    showRight = false,
                    showDown = settingsSubPage == "main",
                    downBottomPadding = arrowBottomPadding,
                    onSwipeDown = { if (settingsSubPage == "main") showSettings = false }
                )
            } else if (showForecast) {
                WeatherForecastPage(
                    textColor = textColor,
                    buttonColor = buttonColor,
                    weatherRepo = weatherRepo
                )

                SwipeIndicators(
                    isVertical = isVerticalMode,
                    showLeft = false,
                    showRight = false,
                    showUp = true,
                    onSwipeUp = { showForecast = false }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!pageContentReady) {
                        // Widgets en weerkaart pas samen tonen zodra beide minstens 1x klaar zijn,
                        // i.p.v. de widgets soms merkbaar eerder (of later) te laten verschijnen
                        // dan de rest van de pagina.
                        CircularProgressIndicator(
                            color = textColor.copy(alpha = 0.35f),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        WeatherWidgetsRow(
                            widgets = activeWidgets,
                            liveValues = widgetLiveValues,
                            textColor = textColor,
                            buttonColor = buttonColor
                        )
                        when (val state = uiState) {
                            is WeatherUiState.Loading -> {
                                CircularProgressIndicator(
                                    color = textColor.copy(alpha = 0.35f),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            is WeatherUiState.Warning -> {
                                WeatherWarningContent(state = state, textColor = textColor)
                            }
                            is WeatherUiState.Calm -> {
                                WeatherCalmContent(
                                    state = state,
                                    textColor = textColor,
                                    buttonColor = buttonColor
                                )
                            }
                        }
                    }
                }

                SwipeIndicators(
                    isVertical = isVerticalMode,
                    showLeft = leftTarget != null,
                    showRight = rightTarget != null,
                    showUp = true,
                    showDown = true,
                    downBottomPadding = arrowBottomPadding,
                    onSwipeLeft = { NavigationManager.navigateLeft(context, currentScreenId) },
                    onSwipeRight = { NavigationManager.navigateRight(context, currentScreenId) },
                    // Let op: onSwipeUp/onSwipeDown zijn hier "bovenste pijltje" en "onderste
                    // pijltje", niet de veegrichting. Het bovenste pijltje hoort naar de
                    // Weerinstellingen te gaan en het onderste naar Weer per dag - dat is ook wat
                    // de veeggebaren over de hele pagina hierboven doen (omlaag vegen haalt de
                    // instellingen van bovenaf binnen, omhoog vegen haalt de dagen van onderaf).
                    // De pijltjes deden precies het omgekeerde van die gebaren.
                    onSwipeUp = { showSettings = true },
                    onSwipeDown = { showForecast = true }
                )

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

/**
 * Per-dag weersverwachting (Weer home > swipe omhoog). Toont voor elke dag het weertype
 * (representatief voor het middaguur), min/max-temperatuur, en — indien van toepassing — een
 * korte tekst voor eventuele slecht-weer-waarschuwingen die dag (zelfde criteria als de
 * "Slecht weer"-instellingen op het startscherm).
 */
@Composable
private fun WeatherForecastPage(
    textColor: Color,
    buttonColor: Color,
    weatherRepo: WeatherRepository
) {
    val context = LocalContext.current
    var forecast by remember { mutableStateOf<WeatherForecast?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val lat = SettingsManager.getWeatherLatitude(context)
        val lon = SettingsManager.getWeatherLongitude(context)
        val model = SettingsManager.getWeatherModel(context)
        forecast = weatherRepo.getForecast(lat, lon, model).getOrNull()
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Iets meer ruimte dan de andere subpagina's — hier staat boven in de layout ook het
        // "terug"-pijltje (showUp), en zonder extra ruimte loopt de header-tekst daar doorheen.
        Spacer(Modifier.height(56.dp))
        WeatherSubPageHeader("Weer per dag", textColor)

        val currentForecast = forecast
        when {
            isLoading -> {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    color = textColor.copy(alpha = 0.35f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
            currentForecast == null -> {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = LanguageManager.getString("weather_fetch_forecast_error"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                val rainEnabled = SettingsManager.getWeatherRainAlarmEnabled(context)
                val rainThreshold = SettingsManager.getWeatherRainThreshold(context)
                val dayForecasts = remember(currentForecast) {
                    buildDayForecasts(context, currentForecast, rainEnabled, rainThreshold, days = 7)
                }
                dayForecasts.forEach { day ->
                    WeatherDayRow(day = day, textColor = textColor, buttonColor = buttonColor)
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun WeatherDayRow(day: DayForecast, textColor: Color, buttonColor: Color) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = conditionIcon(day.condition, day.representativeWeatherCode, day.maxTemp),
            contentDescription = null,
            tint = textColor.copy(alpha = 0.8f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(dayLabelForDate(day.date), style = MaterialTheme.typography.bodyLarge, color = textColor)
            Text(
                conditionLabel(day.condition),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f)
            )
            day.warningLabels.forEach { label ->
                Text(
                    text = label.render(capitalizeFirst = true),
                    style = MaterialTheme.typography.bodySmall,
                    color = buttonColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Text(
            text = buildString {
                if (day.maxTemp != null) append("${displayTemp(context, day.maxTemp)}°")
                if (day.minTemp != null) {
                    if (day.maxTemp != null) append(" / ")
                    append("${displayTemp(context, day.minTemp)}°")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun WeatherSettingsPage(
    subPage: String,
    onSubPageChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val textColor = Color(SettingsManager.getTextColor(context))
    val buttonColor = Color(SettingsManager.getButtonColor(context))

    BackHandler {
        if (subPage == "main") onBack() else onSubPageChange(weatherSettingsParentOf(subPage))
    }

    when (subPage) {
        "locatie" -> WeatherLocationSettingsPage(
            textColor = textColor,
            buttonColor = buttonColor,
            onBack = { onSubPageChange("algemeen") }
        )
        "weermodel" -> WeatherModelSettingsPage(
            textColor = textColor,
            buttonColor = buttonColor,
            onBack = { onSubPageChange("algemeen") }
        )
        "slecht_weer" -> WeatherBadWeatherSettingsPage(
            textColor = textColor,
            buttonColor = buttonColor,
            onBack = { onSubPageChange("main") },
            onNavigateToCalendars = { onSubPageChange("slecht_weer_agenda_items") },
            onNavigateToExtra = { onSubPageChange("slecht_weer_extra") }
        )
        "slecht_weer_extra" -> WeatherExtraBadWeatherSettingsPage(
            textColor = textColor,
            buttonColor = buttonColor,
            onBack = { onSubPageChange("slecht_weer") }
        )
        "slecht_weer_agenda_items" -> WeatherCalendarSelectionPage(
            title = LanguageManager.getString("weather_calendar_items_title").replace("{category}", LanguageManager.getString("weather_label_bad_weather")),
            textColor = textColor,
            buttonColor = buttonColor,
            getSelected = SettingsManager::getWeatherBadWeatherCalendarIds,
            saveSelected = SettingsManager::saveWeatherBadWeatherCalendarIds,
            otherCategoryLabel = LanguageManager.getString("weather_nav_temp_change"),
            getOtherSelected = SettingsManager::getWeatherTempChangeCalendarIds,
            onBack = { onSubPageChange("slecht_weer") }
        )
        "temp_verschil" -> WeatherTempChangeSettingsPage(
            textColor = textColor,
            buttonColor = buttonColor,
            onBack = { onSubPageChange("main") },
            onNavigateToCalendars = { onSubPageChange("temp_verschil_agenda_items") }
        )
        "temp_verschil_agenda_items" -> WeatherCalendarSelectionPage(
            title = LanguageManager.getString("weather_calendar_items_title").replace("{category}", LanguageManager.getString("weather_label_temp_change_short")),
            textColor = textColor,
            buttonColor = buttonColor,
            getSelected = SettingsManager::getWeatherTempChangeCalendarIds,
            saveSelected = SettingsManager::saveWeatherTempChangeCalendarIds,
            otherCategoryLabel = LanguageManager.getString("weather_label_bad_weather"),
            getOtherSelected = SettingsManager::getWeatherBadWeatherCalendarIds,
            onBack = { onSubPageChange("temp_verschil") }
        )
        "meldingen" -> WeatherNotificationSettingsPage(
            textColor = textColor,
            buttonColor = buttonColor,
            buttonTextColor = Color(SettingsManager.getButtonTextColor(context)),
            onSubPageChange = onSubPageChange,
            onBack = { onSubPageChange("algemeen") }
        )
        "weergave" -> WeatherDisplaySettingsPage(
            textColor = textColor,
            buttonColor = buttonColor,
            onBack = { onSubPageChange("algemeen") }
        )
        "algemeen" -> WeatherGeneralHubPage(
            textColor = textColor,
            onSubPageChange = { page ->
                SettingsManager.saveLastVisitedWeatherSubPage(context, page)
                onSubPageChange(page)
            },
            onBack = { onSubPageChange("main") }
        )
        "home_assistant" -> WeatherHomeAssistantSettingsPage(
            textColor = textColor,
            buttonColor = buttonColor,
            buttonTextColor = Color(SettingsManager.getButtonTextColor(context)),
            containerColor = Color(SettingsManager.getBackgroundColor(context)),
            onBack = { onSubPageChange("meldingen") }
        )
        "widgets" -> WeatherWidgetsSettingsPage(
            textColor = textColor,
            buttonColor = buttonColor,
            onBack = { onSubPageChange("algemeen") }
        )
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = LanguageManager.getString("weather_page_title"),
                style = MaterialTheme.typography.headlineSmall,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            WeatherNavRow(LanguageManager.getString("weather_nav_general")) {
                SettingsManager.saveLastVisitedWeatherSubPage(context, "algemeen")
                onSubPageChange("algemeen")
            }
            Spacer(Modifier.height(10.dp))
            WeatherNavRow(LanguageManager.getString("weather_label_bad_weather")) {
                SettingsManager.saveLastVisitedWeatherSubPage(context, "slecht_weer")
                onSubPageChange("slecht_weer")
            }
            Spacer(Modifier.height(10.dp))
            WeatherNavRow(LanguageManager.getString("weather_nav_temp_change")) {
                SettingsManager.saveLastVisitedWeatherSubPage(context, "temp_verschil")
                onSubPageChange("temp_verschil")
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

/** Voor de hiërarchische terugknop: welke pagina moet terugkomen vanuit een geneste subpagina. */
private fun weatherSettingsParentOf(subPage: String): String = when (subPage) {
    "slecht_weer_agenda_items" -> "slecht_weer"
    "slecht_weer_extra" -> "slecht_weer"
    "temp_verschil_agenda_items" -> "temp_verschil"
    "home_assistant" -> "meldingen"
    "locatie", "weermodel", "meldingen", "widgets", "weergave" -> "algemeen"
    else -> "main"
}

/**
 * Doorlink-knop naar een subpagina — zelfde gevulde-knop-ontwerp met de app-brede knopkleuren als
 * overal elders in de app (bv. Agenda-instellingen), i.p.v. de oude tekst-links/pijltje-rechts-rij.
 * Gecentreerde tekst, geen chevron.
 */
@Composable
private fun WeatherNavRow(title: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val buttonColor = Color(SettingsManager.getButtonColor(context))
    val buttonTextColor = Color(SettingsManager.getButtonTextColor(context))
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = buttonTextColor
        )
    ) {
        Text(text = title, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WeatherSubPageHeader(title: String, textColor: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = textColor,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
    )
}

/**
 * Terug-knop onderaan een subpagina, gecentreerd onder de inhoud met extra ruimte — vervangt de
 * oude pijl-naast-de-titel bovenaan. Wordt als laatste element in elke subpagina-Column gezet.
 */
@Composable
private fun WeatherBottomBackButton(textColor: Color, onBack: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    WeatherBackIcon(textColor = textColor, onBack = onBack)
    Spacer(Modifier.height(64.dp))
}

/**
 * Het pijltje zelf, los van [WeatherBottomBackButton]'s Column-spacing - zodat een pagina die de
 * knop buiten de content-Column positioneert (bv. vast onderaan in een Box, zie
 * WeatherGeneralHubPage) exact dezelfde IconButton/Icon-configuratie hergebruikt i.p.v. een eigen
 * kopie die er per ongeluk toch net anders uitziet. Tint = 50% textColor, zelfde
 * [com.dd.daykit.ui.SwipeIndicators]-conventie (`iconTint = textColor.copy(alpha = 0.5f)` in
 * UIComponents.kt) als alle andere pijl-iconen in de app - vol-dekkende textColor viel hiernaast
 * net iets donkerder/feller op.
 */
@Composable
private fun WeatherBackIcon(
    textColor: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier.size(64.dp)
) {
    IconButton(
        onClick = onBack,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = LanguageManager.getString("back"),
            tint = textColor.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun WeatherLocationSettingsPage(textColor: Color, buttonColor: Color, onBack: () -> Unit) {
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var currentLocationName by remember { mutableStateOf(SettingsManager.getWeatherLocationName(context)) }
    var isGpsLoading by remember { mutableStateOf(false) }
    var showGpsOffDialog by remember { mutableStateOf(false) }
    var gpsStatusMessage by remember { mutableStateOf<String?>(null) }
    var isGpsMode by remember { mutableStateOf(SettingsManager.getWeatherLocationIsGps(context)) }
    var syncIntervalMinutes by remember { mutableStateOf(SettingsManager.getWeatherLocationSyncIntervalMinutes(context).toFloat()) }
    val coroutineScope = rememberCoroutineScope()
    val sliderColors = SliderDefaults.colors(
        thumbColor = buttonColor,
        activeTrackColor = buttonColor,
        disabledThumbColor = buttonColor.copy(alpha = 0.3f),
        disabledActiveTrackColor = buttonColor.copy(alpha = 0.3f)
    )

    fun fetchGpsLocation() {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showGpsOffDialog = true
            return
        }
        gpsStatusMessage = null
        isGpsLoading = true
        LocationServices.getFusedLocationProviderClient(context)
            .lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    SettingsManager.saveWeatherLatitude(context, location.latitude)
                    SettingsManager.saveWeatherLongitude(context, location.longitude)
                    SettingsManager.saveWeatherLocationIsGps(context, true)
                    isGpsMode = true
                    WeatherLocationSyncWorker.schedule(context.applicationContext, ExistingPeriodicWorkPolicy.REPLACE)
                    coroutineScope.launch {
                        val resolvedName = withContext(Dispatchers.IO) {
                            reverseGeocodeLocationName(context, location.latitude, location.longitude)
                        } ?: LanguageManager.getString("weather_gps_location_fallback")
                        SettingsManager.saveWeatherLocationName(context, resolvedName)
                        currentLocationName = resolvedName
                        isGpsLoading = false
                    }
                } else {
                    isGpsLoading = false
                    gpsStatusMessage = LanguageManager.getString("weather_no_location_available")
                }
            }
            .addOnFailureListener { isGpsLoading = false }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchGpsLocation()
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        isSearching = true
        try {
            val response = withContext(Dispatchers.IO) { GeocodingClient.api.searchLocation(searchQuery) }
            searchResults = response.results ?: emptyList()
        } catch (e: Exception) {
            searchResults = emptyList()
        } finally {
            isSearching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader(LanguageManager.getString("weather_nav_location"), textColor)

        Text(
            text = LanguageManager.getString("weather_current_location").replace("{name}", currentLocationName),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (isGpsMode) LanguageManager.getString("weather_location_source_gps") else LanguageManager.getString("weather_location_source_fixed"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(LanguageManager.getString("weather_search_location")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = buttonColor
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = buttonColor,
                unfocusedBorderColor = textColor.copy(alpha = 0.25f),
                focusedLabelColor = buttonColor,
                unfocusedLabelColor = textColor.copy(alpha = 0.55f),
                cursorColor = buttonColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )

        if (searchResults.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            searchResults.forEach { result ->
                val subtitle = listOfNotNull(result.admin1, result.country).joinToString(", ")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val name = if (subtitle.isNotEmpty()) "${result.name}, $subtitle" else result.name
                            SettingsManager.saveWeatherLatitude(context, result.latitude)
                            SettingsManager.saveWeatherLongitude(context, result.longitude)
                            SettingsManager.saveWeatherLocationName(context, name)
                            SettingsManager.saveWeatherLocationIsGps(context, false)
                            isGpsMode = false
                            WeatherLocationSyncWorker.cancel(context.applicationContext)
                            currentLocationName = name
                            searchQuery = ""
                            searchResults = emptyList()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(result.name, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        if (subtitle.isNotEmpty()) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.55f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    fetchGpsLocation()
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor.copy(alpha = 0.15f),
                contentColor = textColor
            )
        ) {
            if (isGpsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = buttonColor)
            } else {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (isGpsLoading) LanguageManager.getString("weather_fetching_location") else LanguageManager.getString("weather_use_gps_location"))
        }

        if (gpsStatusMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = gpsStatusMessage ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isGpsMode) {
            WeatherSectionHeader(LanguageManager.getString("weather_auto_update_header"), textColor)
            Text(
                text = LanguageManager.getString("weather_sync_interval_desc").replace("{min}", syncIntervalMinutes.toInt().toString()),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = syncIntervalMinutes,
                onValueChange = { syncIntervalMinutes = it },
                onValueChangeFinished = {
                    SettingsManager.saveWeatherLocationSyncIntervalMinutes(context, syncIntervalMinutes.toLong())
                    WeatherLocationSyncWorker.schedule(context.applicationContext, ExistingPeriodicWorkPolicy.REPLACE)
                },
                valueRange = 15f..240f,
                steps = 14,
                colors = sliderColors
            )
        }

        WeatherBottomBackButton(textColor, onBack)
    }

    if (showGpsOffDialog) {
        AlertDialog(
            onDismissRequest = { showGpsOffDialog = false },
            title = { Text(LanguageManager.getString("weather_gps_off_title")) },
            text = { Text(LanguageManager.getString("weather_gps_off_message")) },
            confirmButton = {
                TextButton(onClick = {
                    showGpsOffDialog = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text(LanguageManager.getString("weather_gps_off_settings_button"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpsOffDialog = false }) {
                    Text(LanguageManager.getString("weather_cancel"))
                }
            }
        )
    }
}

/**
 * Open-Meteo levert per locatie meerdere weermodellen (nationale weerdiensten). "Automatisch"
 * laat Open-Meteo zelf het best passende model kiezen (dit is ook de vorige, vaste instelling).
 * Niet elk model dekt elke locatie — als een gekozen model hier niet beschikbaar is, faalt de
 * ophaal-call gewoon (zelfde foutafhandeling als bij een netwerkstoring) en valt de app terug op
 * de vorige cache.
 */
private data class WeatherModelOption(val id: String, val labelKey: String, val descriptionKey: String)

private val WEATHER_MODEL_OPTIONS = listOf(
    WeatherModelOption("best_match", "weather_model_auto_label", "weather_model_auto_desc"),
    WeatherModelOption("ecmwf_ifs025", "ECMWF", "weather_model_ecmwf_desc"),
    WeatherModelOption("knmi_seamless", "KNMI Harmonie", "weather_model_knmi_desc"),
    WeatherModelOption("icon_seamless", "ICON (DWD)", "weather_model_icon_desc"),
    WeatherModelOption("gfs_seamless", "GFS (NOAA)", "weather_model_gfs_desc"),
    WeatherModelOption("ukmo_seamless", "UK Met Office", "weather_model_ukmo_desc"),
    WeatherModelOption("meteofrance_seamless", "Météo-France", "weather_model_meteofrance_desc"),
    WeatherModelOption("gem_seamless", "GEM", "weather_model_gem_desc")
)

@Composable
private fun WeatherModelSettingsPage(textColor: Color, buttonColor: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedModel by remember { mutableStateOf(SettingsManager.getWeatherModel(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader(LanguageManager.getString("weather_nav_model"), textColor)

        Text(
            text = LanguageManager.getString("weather_model_description"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        WEATHER_MODEL_OPTIONS.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedModel = option.id
                        SettingsManager.saveWeatherModel(context, option.id)
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedModel == option.id,
                    onClick = {
                        selectedModel = option.id
                        SettingsManager.saveWeatherModel(context, option.id)
                    },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = buttonColor,
                        unselectedColor = textColor.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(LanguageManager.getString(option.labelKey), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    Text(
                        LanguageManager.getString(option.descriptionKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.55f)
                    )
                }
            }
        }

        WeatherBottomBackButton(textColor, onBack)
    }
}

@Composable
private fun WeatherBadWeatherSettingsPage(
    textColor: Color,
    buttonColor: Color,
    onBack: () -> Unit,
    onNavigateToCalendars: () -> Unit,
    onNavigateToExtra: () -> Unit
) {
    val context = LocalContext.current

    var rainEnabled by remember { mutableStateOf(SettingsManager.getWeatherRainAlarmEnabled(context)) }
    var rainThreshold by remember { mutableStateOf(SettingsManager.getWeatherRainThreshold(context).toFloat()) }
    var rainMinutesBefore by remember { mutableStateOf(SettingsManager.getWeatherRainMinutesBefore(context).toFloat()) }
    var alertBeforeEvent by remember { mutableStateOf(SettingsManager.getWeatherAlertBeforeEventEnabled(context)) }
    var alertBeforeEventMaxPerDay by remember { mutableStateOf(SettingsManager.getWeatherAlertBeforeEventMaxPerDay(context).toFloat()) }
    var alertBeforeEventNoMax by remember { mutableStateOf(SettingsManager.getWeatherAlertBeforeEventNoMax(context)) }
    var alertBeforeEventWholeDay by remember { mutableStateOf(SettingsManager.getWeatherAlertScopeWholeDay(context)) }
    var alertBeforeEventRangeMinutes by remember { mutableStateOf(SettingsManager.getWeatherAlertBeforeEventRangeMinutes(context)) }
    var alertBeforeEventRangePos by remember {
        mutableStateOf(
            if (alertBeforeEventRangeMinutes <= 1) 0f
            else (kotlin.math.ln(alertBeforeEventRangeMinutes.toDouble()) / kotlin.math.ln(720.0)).toFloat().coerceIn(0f, 1f)
        )
    }
    var alertDayBefore by remember { mutableStateOf(SettingsManager.getWeatherAlertDayBeforeEnabled(context)) }
    var alertDayBeforeTime by remember { mutableStateOf(SettingsManager.getWeatherAlertDayBeforeTime(context)) }
    var alertSameDay by remember { mutableStateOf(SettingsManager.getWeatherAlertSameDayEnabled(context)) }
    var alertSameDayTime by remember { mutableStateOf(SettingsManager.getWeatherAlertSameDayTime(context)) }

    val sliderColors = SliderDefaults.colors(
        thumbColor = buttonColor,
        activeTrackColor = buttonColor,
        disabledThumbColor = buttonColor.copy(alpha = 0.3f),
        disabledActiveTrackColor = buttonColor.copy(alpha = 0.3f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader(LanguageManager.getString("weather_label_bad_weather"), textColor)

        WeatherSectionHeader(LanguageManager.getString("weather_section_rain_alarm"), textColor)
        WeatherSettingRow(LanguageManager.getString("weather_rain_alarm_toggle"), textColor, buttonColor, rainEnabled) {
            rainEnabled = it
            SettingsManager.saveWeatherRainAlarmEnabled(context, it)
            WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = LanguageManager.getString("weather_rain_threshold_desc").replace("{percent}", rainThreshold.toInt().toString()),
            style = MaterialTheme.typography.bodySmall,
            color = if (rainEnabled) textColor.copy(alpha = 0.75f) else textColor.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = rainThreshold,
            onValueChange = { rainThreshold = it; SettingsManager.saveWeatherRainThreshold(context, it.toInt()) },
            valueRange = 0f..100f,
            enabled = rainEnabled,
            colors = sliderColors
        )
        Spacer(Modifier.height(8.dp))
        WeatherNavRow(LanguageManager.getString("weather_nav_extra_bad_weather"), onClick = onNavigateToExtra)
        Text(
            text = LanguageManager.getString("weather_extra_conditions_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Bereik staat bewust vóór de drie triggers: het bepaalt waar élk van hen naar kijkt. Stond
        // dit als sub-instelling onder "Melding voor agenda item", waardoor het leek te gelden voor
        // die ene melding terwijl "Dag ervoor" en "Zelfde dag" er niets van aantrokken.
        WeatherSectionHeader(LanguageManager.getString("weather_section_scope"), textColor)
        WeatherSettingRow(LanguageManager.getString("weather_whole_day_toggle"), textColor, buttonColor, alertBeforeEventWholeDay) {
            alertBeforeEventWholeDay = it
            SettingsManager.saveWeatherAlertScopeWholeDay(context, it)
        }
        Text(
            text = if (alertBeforeEventWholeDay) {
                LanguageManager.getString("weather_scope_desc_whole_day")
            } else {
                LanguageManager.getString("weather_scope_desc_events")
            },
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
        if (!alertBeforeEventWholeDay) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (alertBeforeEventRangeMinutes <= 1) {
                    LanguageManager.getString("weather_range_exact_no_margin")
                } else {
                    LanguageManager.getString("weather_range_around").replace("{range}", formatRangeMinutes(alertBeforeEventRangeMinutes))
                },
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = alertBeforeEventRangePos,
                onValueChange = {
                    alertBeforeEventRangePos = it
                    val minutes = kotlin.math.round(Math.pow(720.0, it.toDouble())).toInt().coerceIn(1, 720)
                    alertBeforeEventRangeMinutes = minutes
                    SettingsManager.saveWeatherAlertBeforeEventRangeMinutes(context, minutes)
                },
                valueRange = 0f..1f,
                colors = sliderColors
            )
            if (alertBeforeEventRangeMinutes > 1) {
                val each = formatRangeMinutes(alertBeforeEventRangeMinutes)
                Text(
                    text = LanguageManager.getString("weather_range_before_after_desc").replace("{each}", each),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = LanguageManager.getString("weather_scope_events_no_items"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }

        WeatherSectionHeader(LanguageManager.getString("weather_section_when_warn"), textColor)
        WeatherSettingRow(LanguageManager.getString("weather_day_before"), textColor, buttonColor, alertDayBefore) {
            alertDayBefore = it
            SettingsManager.saveWeatherAlertDayBeforeEnabled(context, it)
            WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
        }
        if (alertDayBefore) {
            Spacer(Modifier.height(8.dp))
            WeatherTimePicker(
                value = alertDayBeforeTime,
                textColor = textColor,
                onValueChange = {
                    alertDayBeforeTime = it
                    SettingsManager.saveWeatherAlertDayBeforeTime(context, it)
                    WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        WeatherSettingRow(LanguageManager.getString("weather_same_day_morning"), textColor, buttonColor, alertSameDay) {
            alertSameDay = it
            SettingsManager.saveWeatherAlertSameDayEnabled(context, it)
            WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
        }
        if (alertSameDay) {
            Spacer(Modifier.height(8.dp))
            WeatherTimePicker(
                value = alertSameDayTime,
                textColor = textColor,
                onValueChange = {
                    alertSameDayTime = it
                    SettingsManager.saveWeatherAlertSameDayTime(context, it)
                    WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        WeatherSettingRow(LanguageManager.getString("weather_notify_before_event"), textColor, buttonColor, alertBeforeEvent) {
            alertBeforeEvent = it
            SettingsManager.saveWeatherAlertBeforeEventEnabled(context, it)
        }
        if (alertBeforeEvent) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatMinutesBeforeLabel(rainMinutesBefore.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = rainMinutesBefore,
                onValueChange = { rainMinutesBefore = it; SettingsManager.saveWeatherRainMinutesBefore(context, it.toInt()) },
                valueRange = 0f..120f,
                steps = 119,
                colors = sliderColors
            )
            Spacer(Modifier.height(4.dp))
            WeatherSettingRow(LanguageManager.getString("weather_no_max_test"), textColor, buttonColor, alertBeforeEventNoMax) {
                alertBeforeEventNoMax = it
                SettingsManager.saveWeatherAlertBeforeEventNoMax(context, it)
            }
            Text(
                text = LanguageManager.getString("weather_no_max_test_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (!alertBeforeEventNoMax) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = LanguageManager.getString("weather_max_per_day").replace("{count}", alertBeforeEventMaxPerDay.toInt().toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Slider(
                    value = alertBeforeEventMaxPerDay,
                    onValueChange = { alertBeforeEventMaxPerDay = it; SettingsManager.saveWeatherAlertBeforeEventMaxPerDay(context, it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = sliderColors
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    WeatherAlertWorker.runOnceNow(context.applicationContext)
                    android.widget.Toast.makeText(context, LanguageManager.getString("weather_test_now_toast"), android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) {
                Text(LanguageManager.getString("weather_test_now_button"))
            }
            Text(
                text = LanguageManager.getString("weather_test_now_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        WeatherSectionHeader(LanguageManager.getString("weather_section_calendars"), textColor)
        Text(
            text = LanguageManager.getString("weather_choose_calendars_bad_weather"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        )
        WeatherNavRow(LanguageManager.getString("weather_nav_calendar_items"), onClick = onNavigateToCalendars)

        WeatherBottomBackButton(textColor, onBack)
    }
}

/**
 * Extra, los toggle-bare slecht-weer-typen (standaard allemaal aan) — storm, sneeuw, gladde weg,
 * hagel, natte sneeuw, extreme hitte en orkaanachtige wind. Gebruikt dezelfde agenda-selectie en
 * dezelfde "wanneer waarschuwen"-triggers (Melding voor agenda item / Dag ervoor / Zelfde dag) als de
 * rest van Slecht weer — hier wordt alleen ingesteld wélke weertypen meetellen.
 */
@Composable
private fun WeatherExtraBadWeatherSettingsPage(
    textColor: Color,
    buttonColor: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var stormEnabled by remember { mutableStateOf(SettingsManager.getWeatherExtraStormEnabled(context)) }
    var stormThreshold by remember { mutableStateOf(SettingsManager.getWeatherExtraStormThreshold(context).toFloat()) }
    var hurricaneEnabled by remember { mutableStateOf(SettingsManager.getWeatherExtraHurricaneEnabled(context)) }
    var hurricaneThreshold by remember { mutableStateOf(SettingsManager.getWeatherExtraHurricaneThreshold(context).toFloat()) }
    var snowEnabled by remember { mutableStateOf(SettingsManager.getWeatherExtraSnowEnabled(context)) }
    var wetSnowEnabled by remember { mutableStateOf(SettingsManager.getWeatherExtraWetSnowEnabled(context)) }
    var hailEnabled by remember { mutableStateOf(SettingsManager.getWeatherExtraHailEnabled(context)) }
    var iceRoadEnabled by remember { mutableStateOf(SettingsManager.getWeatherExtraIceRoadEnabled(context)) }
    var heatEnabled by remember { mutableStateOf(SettingsManager.getWeatherExtraHeatEnabled(context)) }
    var heatThreshold by remember { mutableStateOf(SettingsManager.getWeatherExtraHeatThreshold(context).toFloat()) }

    val sliderColors = SliderDefaults.colors(
        thumbColor = buttonColor,
        activeTrackColor = buttonColor,
        disabledThumbColor = buttonColor.copy(alpha = 0.3f),
        disabledActiveTrackColor = buttonColor.copy(alpha = 0.3f)
    )

    fun reschedule() {
        WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader(LanguageManager.getString("weather_extra_conditions_title"), textColor)
        Text(
            text = LanguageManager.getString("weather_extra_conditions_page_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        WeatherSectionHeader(LanguageManager.getString("weather_section_wind"), textColor)
        WeatherSettingRow(LanguageManager.getString("weather_storm_toggle"), textColor, buttonColor, stormEnabled) {
            stormEnabled = it
            SettingsManager.saveWeatherExtraStormEnabled(context, it)
            reschedule()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = LanguageManager.getString("weather_gusts_from").replace("{speed}", stormThreshold.toInt().toString()),
            style = MaterialTheme.typography.bodySmall,
            color = if (stormEnabled) textColor.copy(alpha = 0.75f) else textColor.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = stormThreshold,
            onValueChange = { stormThreshold = it; SettingsManager.saveWeatherExtraStormThreshold(context, it.toInt()) },
            valueRange = 50f..150f,
            enabled = stormEnabled,
            colors = sliderColors
        )

        Spacer(Modifier.height(12.dp))
        WeatherSettingRow(LanguageManager.getString("weather_hurricane_toggle"), textColor, buttonColor, hurricaneEnabled) {
            hurricaneEnabled = it
            SettingsManager.saveWeatherExtraHurricaneEnabled(context, it)
            reschedule()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = LanguageManager.getString("weather_gusts_from").replace("{speed}", hurricaneThreshold.toInt().toString()),
            style = MaterialTheme.typography.bodySmall,
            color = if (hurricaneEnabled) textColor.copy(alpha = 0.75f) else textColor.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = hurricaneThreshold,
            onValueChange = { hurricaneThreshold = it; SettingsManager.saveWeatherExtraHurricaneThreshold(context, it.toInt()) },
            valueRange = 100f..200f,
            enabled = hurricaneEnabled,
            colors = sliderColors
        )

        WeatherSectionHeader(LanguageManager.getString("weather_section_precipitation"), textColor)
        WeatherSettingRow(LanguageManager.getString("weather_snow_toggle"), textColor, buttonColor, snowEnabled) {
            snowEnabled = it
            SettingsManager.saveWeatherExtraSnowEnabled(context, it)
            reschedule()
        }
        Spacer(Modifier.height(8.dp))
        WeatherSettingRow(LanguageManager.getString("weather_wet_snow_toggle"), textColor, buttonColor, wetSnowEnabled) {
            wetSnowEnabled = it
            SettingsManager.saveWeatherExtraWetSnowEnabled(context, it)
            reschedule()
        }
        Spacer(Modifier.height(8.dp))
        WeatherSettingRow(LanguageManager.getString("weather_hail_toggle"), textColor, buttonColor, hailEnabled) {
            hailEnabled = it
            SettingsManager.saveWeatherExtraHailEnabled(context, it)
            reschedule()
        }
        Spacer(Modifier.height(8.dp))
        WeatherSettingRow(LanguageManager.getString("weather_ice_road_toggle"), textColor, buttonColor, iceRoadEnabled) {
            iceRoadEnabled = it
            SettingsManager.saveWeatherExtraIceRoadEnabled(context, it)
            reschedule()
        }

        WeatherSectionHeader(LanguageManager.getString("weather_section_temperature"), textColor)
        WeatherSettingRow(LanguageManager.getString("weather_extreme_heat_toggle"), textColor, buttonColor, heatEnabled) {
            heatEnabled = it
            SettingsManager.saveWeatherExtraHeatEnabled(context, it)
            reschedule()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = LanguageManager.getString("weather_from_temp").replace("{temp}", heatThreshold.toInt().toString()),
            style = MaterialTheme.typography.bodySmall,
            color = if (heatEnabled) textColor.copy(alpha = 0.75f) else textColor.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = heatThreshold,
            onValueChange = { heatThreshold = it; SettingsManager.saveWeatherExtraHeatThreshold(context, it.toInt()) },
            valueRange = 25f..40f,
            enabled = heatEnabled,
            colors = sliderColors
        )

        WeatherBottomBackButton(textColor, onBack)
    }
}

@Composable
private fun WeatherTempChangeSettingsPage(
    textColor: Color,
    buttonColor: Color,
    onBack: () -> Unit,
    onNavigateToCalendars: () -> Unit
) {
    val context = LocalContext.current

    var tempChangeEnabled by remember { mutableStateOf(SettingsManager.getWeatherTempChangeEnabled(context)) }
    var tempThreshold by remember { mutableStateOf(SettingsManager.getWeatherTempChangeThreshold(context).toFloat()) }

    var alertDayBefore by remember { mutableStateOf(SettingsManager.getWeatherTempChangeAlertDayBeforeEnabled(context)) }
    var alertDayBeforeTime by remember { mutableStateOf(SettingsManager.getWeatherTempChangeAlertDayBeforeTime(context)) }
    var alertSameDay by remember { mutableStateOf(SettingsManager.getWeatherTempChangeAlertSameDayEnabled(context)) }
    var alertSameDayTime by remember { mutableStateOf(SettingsManager.getWeatherTempChangeAlertSameDayTime(context)) }
    var alertFirstEvent by remember { mutableStateOf(SettingsManager.getWeatherTempChangeAlertFirstEventEnabled(context)) }
    var alertFirstEventMinutesBefore by remember { mutableStateOf(SettingsManager.getWeatherTempChangeAlertFirstEventMinutesBefore(context).toFloat()) }
    var alertFirstEventMaxPerDay by remember { mutableStateOf(SettingsManager.getWeatherTempChangeAlertFirstEventMaxPerDay(context).toFloat()) }
    var alertFirstEventNoMax by remember { mutableStateOf(SettingsManager.getWeatherTempChangeAlertFirstEventNoMax(context)) }
    var alertFirstEventWholeDay by remember { mutableStateOf(SettingsManager.getWeatherTempChangeAlertFirstEventWholeDay(context)) }

    val sliderColors = SliderDefaults.colors(
        thumbColor = buttonColor,
        activeTrackColor = buttonColor,
        disabledThumbColor = buttonColor.copy(alpha = 0.3f),
        disabledActiveTrackColor = buttonColor.copy(alpha = 0.3f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader(LanguageManager.getString("weather_nav_temp_change"), textColor)
        Text(
            text = LanguageManager.getString("weather_temp_change_intro"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        WeatherSectionHeader(LanguageManager.getString("weather_section_temp_change"), textColor)
        WeatherSettingRow(LanguageManager.getString("weather_temp_change_alarm_toggle"), textColor, buttonColor, tempChangeEnabled) {
            tempChangeEnabled = it
            SettingsManager.saveWeatherTempChangeEnabled(context, it)
            WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = LanguageManager.getString("weather_temp_diff_desc").replace("{temp}", tempThreshold.toInt().toString()),
            style = MaterialTheme.typography.bodySmall,
            color = if (tempChangeEnabled) textColor.copy(alpha = 0.75f) else textColor.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Slider(
            value = tempThreshold,
            onValueChange = { tempThreshold = it; SettingsManager.saveWeatherTempChangeThreshold(context, it.toInt()) },
            valueRange = 1f..15f,
            steps = 13,
            enabled = tempChangeEnabled,
            colors = sliderColors
        )

        WeatherSectionHeader(LanguageManager.getString("weather_section_when_warn"), textColor)
        WeatherSettingRow(LanguageManager.getString("weather_day_before_forecast"), textColor, buttonColor, alertDayBefore) {
            alertDayBefore = it
            SettingsManager.saveWeatherTempChangeAlertDayBeforeEnabled(context, it)
            WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
        }
        Text(
            text = LanguageManager.getString("weather_day_before_forecast_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = if (alertDayBefore) textColor.copy(alpha = 0.75f) else textColor.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
        if (alertDayBefore) {
            Spacer(Modifier.height(8.dp))
            WeatherTimePicker(
                value = alertDayBeforeTime,
                textColor = textColor,
                onValueChange = {
                    alertDayBeforeTime = it
                    SettingsManager.saveWeatherTempChangeAlertDayBeforeTime(context, it)
                    WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        WeatherSettingRow(LanguageManager.getString("weather_same_day_fixed_time"), textColor, buttonColor, alertSameDay) {
            alertSameDay = it
            SettingsManager.saveWeatherTempChangeAlertSameDayEnabled(context, it)
            WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
        }
        Text(
            text = LanguageManager.getString("weather_same_day_fixed_time_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = if (alertSameDay) textColor.copy(alpha = 0.75f) else textColor.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
        if (alertSameDay) {
            Spacer(Modifier.height(8.dp))
            WeatherTimePicker(
                value = alertSameDayTime,
                textColor = textColor,
                onValueChange = {
                    alertSameDayTime = it
                    SettingsManager.saveWeatherTempChangeAlertSameDayTime(context, it)
                    WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        WeatherSettingRow(LanguageManager.getString("weather_notify_before_event"), textColor, buttonColor, alertFirstEvent) {
            alertFirstEvent = it
            SettingsManager.saveWeatherTempChangeAlertFirstEventEnabled(context, it)
        }
        Text(
            text = if (alertFirstEventMinutesBefore.toInt() <= 0) {
                LanguageManager.getString("weather_warn_at_event")
            } else {
                LanguageManager.getString("weather_warn_minutes_before").replace("{min}", alertFirstEventMinutesBefore.toInt().toString())
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (alertFirstEvent) textColor.copy(alpha = 0.75f) else textColor.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
        if (alertFirstEvent) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = LanguageManager.getString("weather_minutes_before_zero_note").replace("{min}", alertFirstEventMinutesBefore.toInt().toString()),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = alertFirstEventMinutesBefore,
                onValueChange = {
                    alertFirstEventMinutesBefore = it
                    SettingsManager.saveWeatherTempChangeAlertFirstEventMinutesBefore(context, it.toInt())
                },
                valueRange = 0f..60f,
                steps = 11,
                colors = sliderColors
            )
            Spacer(Modifier.height(4.dp))
            WeatherSettingRow(LanguageManager.getString("weather_no_max_test"), textColor, buttonColor, alertFirstEventNoMax) {
                alertFirstEventNoMax = it
                SettingsManager.saveWeatherTempChangeAlertFirstEventNoMax(context, it)
            }
            Text(
                text = LanguageManager.getString("weather_no_max_test_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (!alertFirstEventNoMax) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = LanguageManager.getString("weather_max_per_day").replace("{count}", alertFirstEventMaxPerDay.toInt().toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Slider(
                    value = alertFirstEventMaxPerDay,
                    onValueChange = {
                        alertFirstEventMaxPerDay = it
                        SettingsManager.saveWeatherTempChangeAlertFirstEventMaxPerDay(context, it.toInt())
                    },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = sliderColors
                )
            }
            Spacer(Modifier.height(4.dp))
            WeatherSettingRow(LanguageManager.getString("weather_whole_day_toggle"), textColor, buttonColor, alertFirstEventWholeDay) {
                alertFirstEventWholeDay = it
                SettingsManager.saveWeatherTempChangeAlertFirstEventWholeDay(context, it)
            }
            Text(
                text = if (alertFirstEventWholeDay) {
                    LanguageManager.getString("weather_temp_whole_day_desc_on")
                } else {
                    LanguageManager.getString("weather_temp_whole_day_desc_off")
                },
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    WeatherAlertWorker.runOnceNow(context.applicationContext)
                    android.widget.Toast.makeText(context, LanguageManager.getString("weather_test_now_toast"), android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) {
                Text(LanguageManager.getString("weather_test_now_button"))
            }
            Text(
                text = LanguageManager.getString("weather_test_now_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        WeatherSectionHeader(LanguageManager.getString("weather_section_calendars"), textColor)
        Text(
            text = LanguageManager.getString("weather_choose_calendars_temp_change"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        )
        WeatherNavRow(LanguageManager.getString("weather_nav_calendar_items"), onClick = onNavigateToCalendars)

        WeatherBottomBackButton(textColor, onBack)
    }
}

@Composable
private fun WeatherNotificationSettingsPage(
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    onSubPageChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Alleen nodig voor de "Uitspreken"-toggle hieronder (spiegelt HomeAssistantSettings.weatherTtsEnabled,
    // gedeeld met de Home Assistant-subpagina) - zelfde constructie-patroon als WeatherHomeAssistantSettingsPage.
    val storage = remember { HomeAssistantSettingsStorage(context.applicationContext) }
    val repository = remember { HomeAssistantRepository(HomeAssistantClient, storage) }
    val factory = remember { HaSettingsViewModelFactory(storage, repository) }
    val viewModel: HaSettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    var notificationsEnabled by remember { mutableStateOf(SettingsManager.getWeatherNotificationsEnabled(context)) }
    var linkToCalendar by remember { mutableStateOf(SettingsManager.getWeatherLinkToCalendar(context)) }
    var notifyEnabled by remember { mutableStateOf(SettingsManager.getWeatherNotifyEnabled(context)) }
    var popupEnabled by remember { mutableStateOf(SettingsManager.getWeatherPopupEnabled(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader(LanguageManager.getString("weather_nav_notifications"), textColor)

        WeatherSectionHeader(LanguageManager.getString("weather_section_general"), textColor)
        WeatherSettingRow(LanguageManager.getString("weather_notifications_enable_toggle"), textColor, buttonColor, notificationsEnabled) {
            notificationsEnabled = it
            SettingsManager.saveWeatherNotificationsEnabled(context, it)
            WeatherAlertWorker.syncScheduleWithSettings(context.applicationContext)
            WeatherDailyAlertScheduler.rescheduleAll(context.applicationContext)
            if (it && !hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (notificationsEnabled && !hasNotificationPermission) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = LanguageManager.getString("weather_no_permission_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor.copy(alpha = 0.15f), contentColor = textColor)
            ) {
                Text(LanguageManager.getString("weather_allow_notifications_button"))
            }
        }
        Spacer(Modifier.height(4.dp))
        WeatherSettingRow(LanguageManager.getString("weather_link_to_calendar_toggle"), textColor, buttonColor, linkToCalendar) {
            linkToCalendar = it
            SettingsManager.saveWeatherLinkToCalendar(context, it)
        }

        if (notificationsEnabled) {
            WeatherSectionHeader(LanguageManager.getString("weather_section_how_receive"), textColor)
            Text(
                text = LanguageManager.getString("weather_how_receive_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            WeatherSettingRow(LanguageManager.getString("weather_notify_toggle"), textColor, buttonColor, notifyEnabled) {
                notifyEnabled = it
                SettingsManager.saveWeatherNotifyEnabled(context, it)
            }
            Text(
                text = LanguageManager.getString("weather_notify_toggle_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            WeatherSettingRow(LanguageManager.getString("weather_popup_toggle"), textColor, buttonColor, popupEnabled) {
                popupEnabled = it
                SettingsManager.saveWeatherPopupEnabled(context, it)
            }
            Text(
                text = LanguageManager.getString("weather_popup_toggle_desc").replace("{notify_label}", LanguageManager.getString("weather_notify_toggle")),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            WeatherSettingRow(LanguageManager.getString("weather_speak_toggle"), textColor, buttonColor, uiState.weatherTtsEnabled) {
                viewModel.toggleWeatherTts(it)
            }
            Text(
                text = LanguageManager.getString("weather_speak_toggle_desc"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            )
        }

        WeatherSectionHeader(LanguageManager.getString("weather_section_home_assistant"), textColor)
        Text(
            text = LanguageManager.getString("weather_ha_link_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        WeatherNavRow("Home Assistant") { onSubPageChange("home_assistant") }

        WeatherBottomBackButton(textColor, onBack)
    }
}

/**
 * Hub-pagina voor alle "algemene" weerinstellingen: locatie, weermodel, meldingen, widgets en
 * weergave (C/F + avondwissel). Vervangt de oude platte lijst op de startpagina.
 */
@Composable
private fun WeatherGeneralHubPage(
    textColor: Color,
    onSubPageChange: (String) -> Unit,
    onBack: () -> Unit
) {
    // Box i.p.v. 1 Column: de terugknop hoort onderaan het scherm vast te staan, los van hoeveel
    // ruimte de nav-knoppen erboven innemen - in een Column met verticalArrangement.Center schoof
    // de knop mee met de content omhoog naar het midden i.p.v. onderaan te blijven. Nav-knoppen
    // blijven wel gecentreerd via hun eigen Column, alleen niet meer gekoppeld aan de terugknop-positie.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WeatherSubPageHeader(LanguageManager.getString("weather_nav_general"), textColor)

            WeatherNavRow(LanguageManager.getString("weather_nav_model")) {
                onSubPageChange("weermodel")
            }
            Spacer(Modifier.height(10.dp))
            WeatherNavRow(LanguageManager.getString("weather_nav_location")) {
                onSubPageChange("locatie")
            }
            Spacer(Modifier.height(10.dp))
            WeatherNavRow(LanguageManager.getString("weather_nav_display")) {
                onSubPageChange("weergave")
            }
            Spacer(Modifier.height(10.dp))
            WeatherNavRow(LanguageManager.getString("weather_nav_widgets")) {
                onSubPageChange("widgets")
            }
            Spacer(Modifier.height(10.dp))
            WeatherNavRow(LanguageManager.getString("weather_nav_notifications")) {
                onSubPageChange("meldingen")
            }
        }

        WeatherBackIcon(
            textColor = textColor,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(64.dp)
        )
    }
}

/**
 * Weergave-instellingen: temperatuureenheid en avondwissel-tijdstip.
 */
@Composable
private fun WeatherDisplaySettingsPage(textColor: Color, buttonColor: Color, onBack: () -> Unit) {
    val context = LocalContext.current

    var eveningSwitchTime by remember { mutableStateOf(SettingsManager.getWeatherEveningSwitchTime(context)) }
    var useFahrenheit by remember { mutableStateOf(SettingsManager.getWeatherUseFahrenheit(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader(LanguageManager.getString("weather_nav_display"), textColor)

        WeatherSectionHeader(LanguageManager.getString("weather_section_temp_unit"), textColor)
        Text(
            text = LanguageManager.getString("weather_temp_unit_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        listOf(false to LanguageManager.getString("weather_celsius_label"), true to LanguageManager.getString("weather_fahrenheit_label")).forEach { (isFahrenheit, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        useFahrenheit = isFahrenheit
                        SettingsManager.saveWeatherUseFahrenheit(context, isFahrenheit)
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = useFahrenheit == isFahrenheit,
                    onClick = {
                        useFahrenheit = isFahrenheit
                        SettingsManager.saveWeatherUseFahrenheit(context, isFahrenheit)
                    },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = buttonColor,
                        unselectedColor = textColor.copy(alpha = 0.4f)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        WeatherSectionHeader(LanguageManager.getString("weather_section_today_tomorrow"), textColor)
        Text(
            text = LanguageManager.getString("weather_evening_switch_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        WeatherTimePicker(
            value = eveningSwitchTime,
            textColor = textColor,
            onValueChange = {
                eveningSwitchTime = it
                SettingsManager.saveWeatherEveningSwitchTime(context, it)
            }
        )

        WeatherBottomBackButton(textColor, onBack)
    }
}

/**
 * Home Assistant-koppeling, rechtstreeks vanuit Weer instellingen — dezelfde gedeelde koppeling
 * (DataStore-opslag) als de HA-instellingen bij Agenda-alarm/Timer, hier alleen een selectie van de
 * standaard koppelingsknoppen (plakken/export, HA-URL's, koppelen/verbonden, token als fallback,
 * entiteiten, externe speaker) zodat je HA ook vanuit het weerscherm kunt koppelen — bedoeld als
 * basis voor latere weermeldingen via een HA-speaker/widgets.
 */
@Composable
private fun WeatherHomeAssistantSettingsPage(
    textColor: Color,
    buttonColor: Color,
    buttonTextColor: Color,
    containerColor: Color,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { HomeAssistantSettingsStorage(context.applicationContext) }
    val repository = remember { HomeAssistantRepository(HomeAssistantClient, storage) }
    val factory = remember { HaSettingsViewModelFactory(storage, repository) }
    val viewModel: HaSettingsViewModel = viewModel(factory = factory)

    val uiState by viewModel.uiState.collectAsState()
    val qrPairingState by viewModel.qrPairingState.collectAsState()
    val backupAlarmTestState by viewModel.backupAlarmTestState.collectAsState()
    val haEntityUpdateState by viewModel.haEntityUpdateState.collectAsState()

    var showUrlsModal by remember { mutableStateOf(false) }
    var showTokenModal by remember { mutableStateOf(false) }
    var showEntitiesModal by remember { mutableStateOf(false) }
    var showSpeakerModal by remember { mutableStateOf(false) }
    var showPairModal by remember { mutableStateOf(false) }
    var showPasteModal by remember { mutableStateOf(false) }
    var scannedPairingPayload by remember {
        mutableStateOf<com.dd.daykit.homeassistant.PairingClient.PairingPayload?>(null)
    }

    // QR-scanner (setup-code koppelflow) — zelfde camera-permissie + zxing scanner als de
    // hoofd-HA-instellingenpagina.
    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val parsed = com.dd.daykit.homeassistant.PairingClient.parseScannedPayload(result.contents)
            if (parsed != null) {
                scannedPairingPayload = parsed
            } else {
                android.widget.Toast.makeText(context, LanguageManager.getString("weather_ha_qr_not_recognized"), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val launchQrScan = {
        qrScanLauncher.launch(
            ScanOptions().apply {
                setPrompt(LanguageManager.getString("weather_ha_scan_prompt"))
                setBeepEnabled(false)
                setOrientationLocked(true)
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            }
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchQrScan()
        } else {
            android.widget.Toast.makeText(context, LanguageManager.getString("weather_ha_camera_permission_needed"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val onScanQrClick = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchQrScan()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.applyHomeAssistantSettings(SettingsManager.getBatteryUsagePerHour(context))
    }

    // Zelfde her-controle bij terugkeren naar dit scherm als op de HA-instellingenpagina van het
    // alarm/de timer, zodat een wijziging die je net in Home Assistant deed ook hier meteen als
    // melding verschijnt i.p.v. pas bij een volgende keer openen. Eerste ON_RESUME overslaan: die
    // valt samen met de LaunchedEffect hierboven, die de check zelf al doet.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var skipFirstResume = true
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (skipFirstResume) skipFirstResume = false else viewModel.checkForHaEntityUpdate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isPaired = uiState.longLivedToken.isNotBlank() && !uiState.activeBaseUrl.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader("Home Assistant", textColor)
        Text(
            text = LanguageManager.getString("weather_ha_page_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showPasteModal = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
            ) {
                Text(LanguageManager.getString("ha_paste"))
            }
            Button(
                onClick = {
                    val exportText = com.dd.daykit.viewmodel.generateExportText(
                        uiState = uiState,
                        batteryUsage = SettingsManager.getBatteryUsagePerHour(context),
                        speakerContext = SpeakerContext.WEATHER
                    )
                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("HA Settings", exportText)
                    clipboardManager.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, LanguageManager.getString("weather_ha_settings_copied"), android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonTextColor)
            ) {
                Text(LanguageManager.getString("ha_export"))
            }
        }
        Spacer(Modifier.height(8.dp))

        SettingCard(
            title = LanguageManager.getString("ha_urls"),
            description = "${uiState.baseUrls.filter { it.isNotBlank() }.size} ${LanguageManager.getString("ha_urls_configured")}",
            onClick = { showUrlsModal = true },
            textColor = textColor,
            buttonColor = buttonColor
        )
        Spacer(Modifier.height(8.dp))

        if (isPaired) {
            PairedCard(
                activeBaseUrl = uiState.activeBaseUrl!!,
                onDisconnect = { viewModel.clearPairing() },
                textColor = textColor,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor
            )
        } else {
            SettingCard(
                title = LanguageManager.getString("weather_ha_pair_title"),
                description = LanguageManager.getString("weather_ha_pair_desc"),
                onClick = {
                    scannedPairingPayload = null
                    viewModel.clearQrPairingMessage()
                    showPairModal = true
                },
                textColor = textColor,
                buttonColor = buttonColor
            )
        }

        if (!isPaired) {
            Spacer(Modifier.height(8.dp))
            SettingCard(
                title = LanguageManager.getString("ha_token"),
                description = if (uiState.longLivedToken.isNotBlank()) LanguageManager.getString("ha_token_configured") else LanguageManager.getString("ha_not_configured"),
                onClick = { showTokenModal = true },
                textColor = textColor,
                buttonColor = buttonColor
            )
        }
        Spacer(Modifier.height(8.dp))

        SettingCard(
            title = LanguageManager.getString("ha_entities"),
            description = LanguageManager.getString("ha_entities_add_desc"),
            onClick = { showEntitiesModal = true },
            textColor = textColor,
            buttonColor = buttonColor
        )
        // Zelfde bewuste keuze als op de hoofd-HA-instellingenpagina: alleen tonen (en dus alleen
        // meetellen voor de layout) als er echt een verschil gevonden is (entiteiten en/of velden).
        if (haEntityUpdateState.hasChanges) {
            HaEntityUpdateCheckSection(
                viewModel = viewModel,
                textColor = textColor,
                buttonColor = buttonColor,
                buttonTextColor = buttonTextColor,
                containerColor = containerColor
            )
        }
        Spacer(Modifier.height(8.dp))

        SettingCard(
            title = LanguageManager.getString("ha_speaker"),
            description = LanguageManager.getString("ha_speaker_desc"),
            onClick = { showSpeakerModal = true },
            textColor = textColor,
            buttonColor = buttonColor
        )

        WeatherSectionHeader(LanguageManager.getString("weather_section_tts"), textColor)
        Text(
            text = LanguageManager.getString("weather_tts_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        WeatherSettingRow(LanguageManager.getString("weather_tts_speaker_toggle"), textColor, buttonColor, uiState.weatherTtsEnabled) {
            viewModel.toggleWeatherTts(it)
        }
        if (uiState.weatherTtsEnabled && uiState.weatherSpeaker.entityId.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = LanguageManager.getString("weather_tts_select_speaker_first"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        WeatherBottomBackButton(textColor, onBack)
    }

    // ── Modals — zelfde wiring als HaSettingsActivity.kt, gedeelde HaSettingsViewModel-acties ──

    UrlsModal(
        visible = showUrlsModal,
        initialUrls = uiState.baseUrls,
        onDismiss = { showUrlsModal = false },
        onSave = { urls ->
            viewModel.replaceAllUrls(urls)
            viewModel.saveSettings()
            showUrlsModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    TokenModal(
        visible = showTokenModal,
        initialToken = uiState.longLivedToken,
        onDismiss = { showTokenModal = false },
        onSave = { token ->
            viewModel.updateToken(token)
            viewModel.saveSettings()
            showTokenModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    LaunchedEffect(qrPairingState) {
        if (showPairModal && !qrPairingState.isPairing && !qrPairingState.isError && !qrPairingState.lastMessage.isNullOrBlank()) {
            showPairModal = false
            scannedPairingPayload = null
            viewModel.clearQrPairingMessage()
        }
    }

    PairModal(
        visible = showPairModal,
        initialBaseUrl = uiState.activeBaseUrl ?: uiState.baseUrls.firstOrNull { it.isNotBlank() } ?: "",
        scannedPayload = scannedPairingPayload,
        isPairing = qrPairingState.isPairing,
        statusMessage = qrPairingState.lastMessage,
        statusIsError = qrPairingState.isError,
        onDismiss = {
            showPairModal = false
            scannedPairingPayload = null
        },
        onScanQrClick = onScanQrClick,
        onPair = { url, code ->
            viewModel.pairWithSetupCode(url, code, SettingsManager.getBatteryUsagePerHour(context))
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    EntitiesModal(
        visible = showEntitiesModal,
        availableEntities = uiState.entities,
        initialSelected = uiState.entities.filter { it.isNotBlank() },
        onDismiss = { showEntitiesModal = false },
        onSave = { entities ->
            viewModel.replaceAllEntities(entities)
            viewModel.saveSettings()
            viewModel.loadMediaPlayers()
            viewModel.loadPresenceEntities()
            showEntitiesModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    LaunchedEffect(showSpeakerModal) {
        if (showSpeakerModal) {
            viewModel.loadLocalSounds()
        }
    }

    SpeakerModal(
        visible = showSpeakerModal,
        availableSpeakers = uiState.availableMediaPlayers.map { it.entityId },
        initialSelectedSpeaker = uiState.weatherSpeaker.entityId,
        initialSpeakerMode = when (uiState.weatherSpeaker.mode) {
            com.dd.daykit.data.ExternalSpeakerMode.DISABLED -> SpeakerMode.DISABLED
            com.dd.daykit.data.ExternalSpeakerMode.DEFAULT -> SpeakerMode.STANDARD
            com.dd.daykit.data.ExternalSpeakerMode.BACKUP_ONLY -> SpeakerMode.BACKUP
            com.dd.daykit.data.ExternalSpeakerMode.BOTH -> SpeakerMode.BOTH
        },
        availableLocalSounds = uiState.availableLocalSounds,
        isLoadingSounds = uiState.isLoadingSounds,
        initialBatteryUsage = SettingsManager.getBatteryUsagePerHour(context),
        initialVolume = uiState.weatherSpeaker.volume,
        initialSkipVolume = uiState.weatherSpeaker.skipVolume,
        initialSelectedSoundId = uiState.weatherSpeaker.soundId,
        speakerContext = SpeakerContext.WEATHER,
        isTesting = backupAlarmTestState.isTesting,
        testMessage = backupAlarmTestState.lastMessage,
        onDismiss = { showSpeakerModal = false },
        onSave = { speaker, mode, batteryUsage, volume, skipVolume, selectedSoundId, applyToOtherContexts ->
            val externalMode = when (mode) {
                SpeakerMode.DISABLED -> com.dd.daykit.data.ExternalSpeakerMode.DISABLED
                SpeakerMode.STANDARD -> com.dd.daykit.data.ExternalSpeakerMode.DEFAULT
                SpeakerMode.BACKUP -> com.dd.daykit.data.ExternalSpeakerMode.BACKUP_ONLY
                SpeakerMode.BOTH -> com.dd.daykit.data.ExternalSpeakerMode.BOTH
            }
            SettingsManager.saveBatteryUsagePerHour(context, batteryUsage)
            viewModel.syncBatteryUsageToHomeAssistant(batteryUsage)
            // Slaat op voor Weer + eventueel aangevinkte andere onderdelen (de "ook toepassen
            // op..."-melding in SpeakerModal) in 1 keer, en persisteert direct.
            viewModel.saveSpeakerSettings(
                SpeakerContext.WEATHER,
                SpeakerSettings(
                    entityId = speaker,
                    mode = externalMode,
                    volume = volume,
                    skipVolume = skipVolume,
                    soundId = selectedSoundId
                ),
                applyToOtherContexts
            )
            showSpeakerModal = false
        },
        onTestSpeaker = { speakerId ->
            viewModel.selectSpeaker(SpeakerContext.WEATHER, speakerId)
            viewModel.testSpeaker(context, SpeakerContext.WEATHER)
        },
        onTestBackupAlarm = { volume, skipVolume, selectedSoundId ->
            viewModel.testBackupAlarmWithCurrentState(SpeakerContext.WEATHER, volume, skipVolume, selectedSoundId)
        },
        onTestWeatherSpeaker = { mode, speakerId, volume, skipVolume ->
            // Geen speakerId ("Mobiel speaker" gekozen) betekent geen HA-speaker om te testen,
            // ongeacht de (dan toch niet getoonde) modus-waarde - zie isWeatherPhoneOnly in
            // SpeakerModal.kt. Alleen met een gekozen HA-entiteit doet de modus ertoe.
            val hasHaSpeaker = !speakerId.isNullOrBlank()
            val testHaSpeaker = hasHaSpeaker && (mode == SpeakerMode.STANDARD || mode == SpeakerMode.BOTH)
            val testPhoneSpeaker = !hasHaSpeaker || mode == SpeakerMode.BOTH
            viewModel.testWeatherSpeaker(context, testHaSpeaker, testPhoneSpeaker, speakerId, volume, skipVolume)
        },
        onBatteryUsageChanged = { batteryUsage ->
            SettingsManager.saveBatteryUsagePerHour(context, batteryUsage)
            viewModel.syncBatteryUsageToHomeAssistant(batteryUsage)
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )

    PasteModal(
        visible = showPasteModal,
        onDismiss = { showPasteModal = false },
        onParsed = { parseResult ->
            // Gedeeld met HaSettingsActivity, zodat het Weer-scherm exact dezelfde velden
            // overneemt als het Agenda-alarm/Timer-scherm.
            applyHaPasteResult(
                context = context,
                viewModel = viewModel,
                uiState = uiState,
                speakerContext = SpeakerContext.WEATHER,
                parseResult = parseResult
            )
            viewModel.saveSettings()
            com.dd.daykit.network.HomeAssistantClient.clearCache()
            viewModel.applyHomeAssistantSettings(SettingsManager.getBatteryUsagePerHour(context))
            showPasteModal = false
        },
        textColor = textColor,
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
        containerColor = containerColor
    )
}

/** Vult een lijst rijen aan tot precies 3, zodat er altijd 3 invoerrijen te zien zijn om te bewerken. */
private fun padWidgetRows(rows: List<WeatherWidgetEntityConfig>): List<WeatherWidgetEntityConfig> {
    val trimmed = rows.take(3)
    return trimmed + List(3 - trimmed.size) { WeatherWidgetEntityConfig("", "") }
}

/**
 * Configuratiepagina voor de weer-widgets op de Weer-homepage: max 2 widgets, elk met max 3 HA-
 * entiteiten met een optionele eigen naam. Entiteit-id's worden hier gekozen uit de gedeelde
 * "Entiteiten"-lijst (Home Assistant > Entiteiten) via een uitklap-picker — niet met de hand
 * getypt — zodat er geen typefouten kunnen ontstaan en alleen entiteiten te kiezen zijn die al
 * bekend zijn bij de app. Waardes zelf worden pas live opgehaald op de homepage (zie
 * [WeatherWidgetsRow]).
 */
@Composable
private fun WeatherWidgetsSettingsPage(textColor: Color, buttonColor: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val containerColor = Color(SettingsManager.getBackgroundColor(context))

    // Zelfde gedeelde HA-koppeling/entiteitenlijst als WeatherHomeAssistantSettingsPage - de
    // widgets kiezen uit de entiteiten die daar (of bij Agenda-alarm/Timer) al zijn toegevoegd.
    val storage = remember { HomeAssistantSettingsStorage(context.applicationContext) }
    val repository = remember { HomeAssistantRepository(HomeAssistantClient, storage) }
    val factory = remember { HaSettingsViewModelFactory(storage, repository) }
    val haViewModel: HaSettingsViewModel = viewModel(factory = factory)
    val haUiState by haViewModel.uiState.collectAsState()
    val availableEntities = remember(haUiState.entities) { haUiState.entities.filter { it.isNotBlank() } }

    var widget1Rows by remember { mutableStateOf(padWidgetRows(SettingsManager.getWeatherWidgetEntities(context, 1))) }
    var widget2Rows by remember { mutableStateOf(padWidgetRows(SettingsManager.getWeatherWidgetEntities(context, 2))) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader(LanguageManager.getString("weather_nav_widgets"), textColor)
        Text(
            text = LanguageManager.getString("weather_widgets_page_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        if (availableEntities.isEmpty()) {
            Text(
                text = LanguageManager.getString("weather_widgets_no_entities"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        WeatherSectionHeader(LanguageManager.getString("weather_widget_1_header"), textColor)
        widget1Rows.forEachIndexed { index, row ->
            WeatherWidgetEntityRow(
                entityId = row.entityId,
                name = row.name,
                availableEntities = availableEntities,
                textColor = textColor,
                buttonColor = buttonColor,
                containerColor = containerColor,
                onEntityIdChange = { newId ->
                    widget1Rows = widget1Rows.toMutableList().also { list ->
                        val current = list[index]
                        // Stel bij het kiezen van een entiteit meteen een naam voor, gebaseerd op de
                        // entiteit-id, zodat het veld niet leeg/technisch blijft staan - blijft gewoon
                        // handmatig aan te passen. Alleen als er nog geen (eigen) naam stond, anders
                        // niet een net ingetypte naam overschrijven.
                        val suggestedName = if (current.name.isBlank()) formatEntityIdAsLabel(newId) else current.name
                        list[index] = current.copy(entityId = newId, name = suggestedName)
                    }
                    SettingsManager.saveWeatherWidgetEntities(context, 1, widget1Rows)
                },
                onNameChange = { newName ->
                    widget1Rows = widget1Rows.toMutableList().also { it[index] = it[index].copy(name = newName) }
                    SettingsManager.saveWeatherWidgetEntities(context, 1, widget1Rows)
                }
            )
            if (index < widget1Rows.lastIndex) Spacer(Modifier.height(10.dp))
        }

        WeatherSectionHeader(LanguageManager.getString("weather_widget_2_header"), textColor)
        widget2Rows.forEachIndexed { index, row ->
            WeatherWidgetEntityRow(
                entityId = row.entityId,
                name = row.name,
                availableEntities = availableEntities,
                textColor = textColor,
                buttonColor = buttonColor,
                containerColor = containerColor,
                onEntityIdChange = { newId ->
                    widget2Rows = widget2Rows.toMutableList().also { list ->
                        val current = list[index]
                        val suggestedName = if (current.name.isBlank()) formatEntityIdAsLabel(newId) else current.name
                        list[index] = current.copy(entityId = newId, name = suggestedName)
                    }
                    SettingsManager.saveWeatherWidgetEntities(context, 2, widget2Rows)
                },
                onNameChange = { newName ->
                    widget2Rows = widget2Rows.toMutableList().also { it[index] = it[index].copy(name = newName) }
                    SettingsManager.saveWeatherWidgetEntities(context, 2, widget2Rows)
                }
            )
            if (index < widget2Rows.lastIndex) Spacer(Modifier.height(10.dp))
        }

        WeatherBottomBackButton(textColor, onBack)
    }
}

/**
 * Eén invoerrij (entiteit-id + optionele eigen naam) binnen [WeatherWidgetsSettingsPage].
 * Entiteit-id is een uitklap-picker op de gedeelde entiteitenlijst — geen vrij tekstveld: laadt
 * de lijst zodra je erop tikt, en klapt vanzelf weer dicht zodra er een gekozen is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherWidgetEntityRow(
    entityId: String,
    name: String,
    availableEntities: List<String>,
    textColor: Color,
    buttonColor: Color,
    containerColor: Color,
    onEntityIdChange: (String) -> Unit,
    onNameChange: (String) -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = buttonColor,
        unfocusedBorderColor = textColor.copy(alpha = 0.25f),
        focusedLabelColor = buttonColor,
        unfocusedLabelColor = textColor.copy(alpha = 0.55f),
        cursorColor = buttonColor,
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = isDropdownExpanded,
            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
        ) {
            OutlinedTextField(
                value = entityId,
                onValueChange = {},
                readOnly = true,
                label = { Text(LanguageManager.getString("weather_entity_id_label")) },
                placeholder = { Text(LanguageManager.getString("weather_choose_entity_placeholder")) },
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = LanguageManager.getString("weather_expand_content_desc"),
                        tint = buttonColor
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = fieldColors
            )
            ExposedDropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { isDropdownExpanded = false },
                modifier = Modifier.background(containerColor)
            ) {
                if (availableEntities.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(LanguageManager.getString("weather_no_entities_available"), color = textColor.copy(alpha = 0.5f)) },
                        onClick = { isDropdownExpanded = false },
                        enabled = false,
                        colors = MenuDefaults.itemColors(textColor = textColor.copy(alpha = 0.5f))
                    )
                }
                availableEntities.forEach { entity ->
                    DropdownMenuItem(
                        text = { Text(entity, color = textColor) },
                        onClick = {
                            onEntityIdChange(entity)
                            isDropdownExpanded = false
                        },
                        colors = MenuDefaults.itemColors(textColor = textColor)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(LanguageManager.getString("weather_custom_name_label")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors
        )
    }
}

/**
 * Herbruikbare "welke agenda's tellen mee"-pagina — identiek mechanisme voor Slecht weer en
 * Grote temperatuurverandering, elk met hun eigen onafhankelijke opslag (niet gekoppeld aan de
 * agenda-alarm-kalenders). Standaard staat alles UIT (leeg = geen enkele agenda telt mee) — pas
 * nadat de gebruiker zelf agenda's aanvinkt, tellen die mee voor meldingen van deze categorie.
 */
@Composable
private fun WeatherCalendarSelectionPage(
    title: String,
    textColor: Color,
    buttonColor: Color,
    getSelected: (android.content.Context) -> Set<String>,
    saveSelected: (android.content.Context, Set<String>) -> Unit,
    otherCategoryLabel: String,
    getOtherSelected: (android.content.Context) -> Set<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var selectedCalendarIds by remember { mutableStateOf(getSelected(context)) }
    var showCopyConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        calendars = withContext(Dispatchers.IO) { getCalendars(context) }
    }

    if (showCopyConfirm) {
        AlertDialog(
            onDismissRequest = { showCopyConfirm = false },
            title = { Text(LanguageManager.getString("weather_confirm_title")) },
            text = { Text(LanguageManager.getString("weather_confirm_copy_calendars_desc").replace("{other}", otherCategoryLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    val copied = getOtherSelected(context)
                    selectedCalendarIds = copied
                    saveSelected(context, copied)
                    showCopyConfirm = false
                }) {
                    Text(LanguageManager.getString("weather_confirm_use_same"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyConfirm = false }) {
                    Text(LanguageManager.getString("weather_cancel"))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(8.dp))
        WeatherSubPageHeader(title, textColor)

        OutlinedButton(
            onClick = { showCopyConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
        ) {
            Text(
                LanguageManager.getString("weather_use_same_calendars_button").replace("{other}", otherCategoryLabel),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))

        Text(
            text = LanguageManager.getString("weather_calendar_selection_default_off_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        if (calendars.isEmpty()) {
            Text(
                text = LanguageManager.getString("weather_no_calendars_found"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            calendars.forEach { calendar ->
                val idStr = calendar.id.toString()
                val isChecked = idStr in selectedCalendarIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSelection = if (isChecked) {
                                selectedCalendarIds - idStr
                            } else {
                                selectedCalendarIds + idStr
                            }
                            selectedCalendarIds = newSelection
                            saveSelected(context, newSelection)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = buttonColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(calendar.displayName, style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
        }

        WeatherBottomBackButton(textColor, onBack)
    }
}

// ── Settings helpers ──────────────────────────────────────────────────────────

@Composable
private fun WeatherSectionHeader(title: String, textColor: Color) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = textColor.copy(alpha = 0.45f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun WeatherSettingRow(
    label: String,
    textColor: Color,
    buttonColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = buttonColor,
                checkedTrackColor = buttonColor.copy(alpha = 0.5f),
                uncheckedThumbColor = textColor.copy(alpha = 0.5f),
                uncheckedTrackColor = textColor.copy(alpha = 0.2f)
            )
        )
    }
}

/**
 * Compact uur:min-stappenteller voor de "Tijdstip"-instellingen (Slecht weer / Temperatuurwissel
 * > Dag ervoor / Zelfde dag). Hergebruikt [SwipeableTimeUnitEnhanced] (dezelfde op-en-neer-pijltjes
 * als Timer) — wielformaat gelijk aan Timer's compacte tier (165dp), tekstgrootte gelijk aan
 * Witgoed's compacte lettergrootte (headlineSmall), dicht op elkaar gezet.
 */
@Composable
private fun WeatherTimePicker(
    value: String,
    textColor: Color,
    onValueChange: (String) -> Unit
) {
    val parts = value.split(":")
    val parsedHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val parsedMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    var hour by remember(value) { mutableStateOf(parsedHour) }
    var minute by remember(value) { mutableStateOf(parsedMinute) }

    fun emit(h: Int, m: Int) {
        onValueChange(String.format("%02d:%02d", h, m))
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        SwipeableTimeUnitEnhanced(
            value = hour,
            onValueChange = { hour = it; emit(it, minute) },
            range = 0..23,
            label = LanguageManager.getString("weather_hour_short_label"),
            textColor = textColor,
            arrowSize = 24.dp,
            valueBoxHeight = 46.dp,
            columnWidth = 56.dp,
            valueStyle = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .offset(y = (-6).dp)
        )
        SwipeableTimeUnitEnhanced(
            value = minute,
            onValueChange = { minute = it; emit(hour, it) },
            range = 0..59,
            label = LanguageManager.getString("weather_min_short_label"),
            textColor = textColor,
            arrowSize = 24.dp,
            valueBoxHeight = 46.dp,
            columnWidth = 56.dp,
            valueStyle = MaterialTheme.typography.headlineSmall
        )
    }
}

// ── Content composables ───────────────────────────────────────────────────────

/**
 * Rij met de geconfigureerde weer-widgets (Weer instellingen > Widgets), boven de rest van de
 * homepage-inhoud (dus ook boven het wolkje van [WeatherCalmContent]). Leeg als er geen widget is
 * ingesteld. Bij 1 widget staat die gecentreerd, bij 2 komen ze naast elkaar te staan.
 */
@Composable
private fun WeatherWidgetsRow(
    widgets: List<List<WeatherWidgetEntityConfig>>,
    liveValues: Map<String, WeatherWidgetLiveValue>,
    textColor: Color,
    buttonColor: Color
) {
    if (widgets.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = if (widgets.size == 1) Arrangement.Center else Arrangement.spacedBy(8.dp)
    ) {
        widgets.forEach { rows ->
            WeatherWidgetCard(
                rows = rows,
                liveValues = liveValues,
                textColor = textColor,
                buttonColor = buttonColor,
                modifier = if (widgets.size == 1) Modifier.widthIn(max = 220.dp) else Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WeatherWidgetCard(
    rows: List<WeatherWidgetEntityConfig>,
    liveValues: Map<String, WeatherWidgetLiveValue>,
    textColor: Color,
    buttonColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, buttonColor.copy(alpha = 0.25f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rows.forEachIndexed { index, row ->
                val live = liveValues[row.entityId]
                val label = row.name.ifBlank { live?.friendlyName?.takeIf { it.isNotBlank() } ?: formatEntityIdAsLabel(row.entityId) }
                val valueText = live?.let {
                    formatWidgetValue(row.entityId, it) + (it.unit?.takeIf { u -> u.isNotBlank() }?.let { u -> " $u" } ?: "")
                } ?: "—"
                // Naam vóór de waarde, op dezelfde regel (bv. "Thuis Temp 21°C") i.p.v. naam boven
                // waarde op aparte regels.
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$label ",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.65f)
                    )
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor
                    )
                }
                if (index < rows.lastIndex) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Device classes (HA's `device_class`-attribuut) waarvan de waarde als heel getal getoond wordt
 * i.p.v. met decimalen - luchtkwaliteit-achtige metingen (CO2 in ppm, fijnstof PM1/PM2.5/PM10 in
 * µg/m³, enz.) zijn in de praktijk altijd hele getallen; de ".0"/".3" erachter is ruis van de API,
 * geen zinvolle precisie.
 */
private val WIDGET_WHOLE_NUMBER_DEVICE_CLASSES = setOf(
    "carbon_dioxide", "carbon_monoxide", "pm1", "pm25", "pm10",
    "nitrogen_dioxide", "nitrogen_monoxide", "nitrous_oxide", "ozone",
    "sulphur_dioxide", "volatile_organic_compounds", "volatile_organic_compounds_parts", "aqi"
)

/** Fallback op basis van de entiteit-id zelf, voor als HA geen (herkende) device_class meegeeft. */
private val WIDGET_WHOLE_NUMBER_ID_HINTS = listOf("co2", "pm2_5", "pm25", "pm_2_5", "pm10", "pm_10", "pm1", "voc", "aqi")

private fun shouldRoundWidgetValueToWholeNumber(entityId: String, deviceClass: String?): Boolean {
    if (!deviceClass.isNullOrBlank() && deviceClass.lowercase() in WIDGET_WHOLE_NUMBER_DEVICE_CLASSES) return true
    val idLower = entityId.lowercase()
    return WIDGET_WHOLE_NUMBER_ID_HINTS.any { idLower.contains(it) }
}

/**
 * Formatteert de state-waarde van een widget-entiteit voor weergave. Voor CO2/fijnstof/andere
 * "hele getal"-achtige metingen (zie [shouldRoundWidgetValueToWholeNumber]) wordt afgerond naar een
 * heel getal (dus "0.0" -> "0", "418.0" -> "418", "12.6" -> "13") i.p.v. de rauwe HA-state met
 * decimalen te tonen. Andere entiteiten (temperatuur, vochtigheid, enz.) blijven ongewijzigd.
 */
private fun formatWidgetValue(entityId: String, live: WeatherWidgetLiveValue): String {
    if (!shouldRoundWidgetValueToWholeNumber(entityId, live.deviceClass)) return live.state
    val numeric = live.state.toDoubleOrNull() ?: return live.state
    return kotlin.math.round(numeric).toInt().toString()
}

/** Nette label-fallback als er geen eigen naam en geen HA "friendly_name" beschikbaar is. */
private fun formatEntityIdAsLabel(entityId: String): String {
    val withoutDomain = entityId.substringAfter('.', entityId)
    return withoutDomain.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun WeatherWarningContent(state: WeatherUiState.Warning, textColor: Color) {
    val minutesUntilEvent = state.minutesUntilEvent
    val minutesText = when {
        minutesUntilEvent == null -> null
        minutesUntilEvent < 1 -> LanguageManager.getString("weather_now")
        minutesUntilEvent < 60 -> LanguageManager.getString("weather_in_minutes").replace("{min}", minutesUntilEvent.toString())
        else -> {
            val h = minutesUntilEvent / 60
            val m = minutesUntilEvent % 60
            if (m == 0L) {
                LanguageManager.getString("weather_in_hours").replace("{hours}", h.toString())
            } else {
                LanguageManager.getString("weather_in_hours_minutes").replace("{hours}", h.toString()).replace("{min}", m.toString())
            }
        }
    }

    // Groot icoon van de eerste (ernstigste) waarschuwing.
    Icon(
        imageVector = state.alerts.first().icon,
        contentDescription = null,
        tint = textColor,
        modifier = Modifier.size(64.dp)
    )
    WeatherTempSummary(
        currentTemp = state.currentTemp,
        todayMinTemp = state.todayMinTemp,
        todayMaxTemp = state.todayMaxTemp,
        currentRainChance = state.currentRainChance,
        textColor = textColor
    )
    Spacer(Modifier.height(20.dp))
    // Meerdere tegelijk geldende waarschuwingen (bv. regen én hitte) komen onder elkaar te staan.
    state.alerts.forEachIndexed { index, alert ->
        if (index > 0) Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = alert.icon,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = alert.reason.render(capitalizeFirst = true),
                style = if (state.alerts.size > 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    if (state.eventName != null && minutesText != null) {
        // Gekoppeld aan een agenda-event binnen 24 uur.
        Text(
            text = minutesText,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = LanguageManager.getString("weather_linked_to").replace("{name}", state.eventName ?: "").replace("{time}", state.eventTimeStr ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.45f),
            textAlign = TextAlign.Center
        )
    } else {
        // Geen gekoppeld event — dit is de huidige situatie, los van je agenda.
        Text(
            text = LanguageManager.getString("weather_right_now"),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WeatherTempSummary(
    currentTemp: Double?,
    todayMinTemp: Double?,
    todayMaxTemp: Double?,
    currentRainChance: Int?,
    textColor: Color
) {
    if (currentTemp == null) return
    val context = LocalContext.current
    Spacer(Modifier.height(12.dp))
    Text(
        text = "${displayTemp(context, currentTemp)}°",
        style = MaterialTheme.typography.displayLarge,
        color = textColor,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (todayMinTemp != null && todayMaxTemp != null) {
            Text(
                text = "${displayTemp(context, todayMinTemp)}° / ${displayTemp(context, todayMaxTemp)}°",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.6f)
            )
        }
        if (currentRainChance != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WaterDrop,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "${currentRainChance}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun WeatherCalmContent(
    state: WeatherUiState.Calm,
    textColor: Color,
    buttonColor: Color
) {
    // Icoon van het actuele weer (per uur bepaald, zie loadWeatherUiState/getCurrentWeather) i.p.v.
    // een vast wolk-icoon - laat dus zon, motregen, regen, mist, sneeuw etc. echt verschillen.
    Icon(
        imageVector = currentConditionIcon(state.currentWeatherCode, state.currentCondition, state.currentTemp),
        contentDescription = null,
        tint = textColor.copy(alpha = 0.35f),
        modifier = Modifier.size(64.dp)
    )
    WeatherTempSummary(
        currentTemp = state.currentTemp,
        todayMinTemp = state.todayMinTemp,
        todayMaxTemp = state.todayMaxTemp,
        currentRainChance = state.currentRainChance,
        textColor = textColor
    )
    Spacer(Modifier.height(20.dp))
    // "Geen waarschuwing" is een dooie tekst — toon in plaats daarvan een korte samenvatting van
    // het huidige weer (bv. "Het is warm en droog"), tenzij er 's avonds al een temperatuurwissel-
    // melding voor morgen klaarstaat (die heeft voorrang, net als voorheen).
    val headline = when {
        state.eveningTempChangeMessage != null -> state.eveningTempChangeMessage.replaceFirstChar { it.uppercase() }
        state.currentTemp != null -> currentWeatherSummary(state.currentCondition, state.currentTemp, state.currentRainChance)
        else -> LanguageManager.getString("weather_no_warning")
    }
    Text(
        text = headline,
        style = MaterialTheme.typography.headlineMedium,
        color = textColor,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    // Windsnelheid (met bijpassend icoon) komt in plaats van de vroegere "rustig weer voor je
    // komende afspraken"-tekst. Is er al een andere uitleg (temperatuurwissel voor morgen), dan
    // komt die eerst, met de windsnelheid erachter op dezelfde regel.
    val windSpeed = state.currentWindSpeed
    if (windSpeed != null) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (state.eveningTempChangeMessage != null) {
                Text(
                    text = "${LanguageManager.getString("weather_tomorrow_prefix")} ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
            Icon(
                imageVector = windIconForSpeed(windSpeed),
                contentDescription = null,
                tint = textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = windSpeedLabel(windSpeed),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor.copy(alpha = 0.6f)
            )
            if (windSpeed >= 5) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${windSpeed.roundToInt()}${LanguageManager.getString("weather_speed_unit")}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f)
                )
                state.currentWindDirection?.let { degrees ->
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = WindDirectionArrowIcon,
                        contentDescription = windDirectionLabel(degrees),
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(14.dp)
                            .rotate(degrees.toFloat())
                    )
                }
            }
        }
    } else {
        Text(
            text = if (state.eveningTempChangeMessage != null) LanguageManager.getString("weather_tomorrow_prefix") else LanguageManager.getString("weather_calm_default"),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
    // Meerdere waarschuwingen voor dezelfde dag (bv. regen én hitte) komen als losse pilletjes
    // onder elkaar te staan, in plaats van samengevoegd tot één regel.
    var chipIndex = 0
    state.todayWarnings.forEach { label ->
        Spacer(Modifier.height(if (chipIndex == 0) 24.dp else 8.dp))
        val chipText = LanguageManager.getString("weather_today_chip").replace("{label}", label.render(capitalizeFirst = false))
        WeatherWarningChip(chipText, textColor, buttonColor)
        chipIndex++
    }
    state.tomorrowWarnings.forEach { label ->
        Spacer(Modifier.height(if (chipIndex == 0) 24.dp else 8.dp))
        val chipText = LanguageManager.getString("weather_tomorrow_chip").replace("{label}", label.render(capitalizeFirst = false))
        WeatherWarningChip(chipText, textColor, buttonColor)
        chipIndex++
    }
}

@Composable
private fun WeatherWarningChip(text: String, textColor: Color, buttonColor: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = buttonColor.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = textColor.copy(alpha = 0.7f)
        )
    }
}
