package com.dd.daykit

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Gestructureerde vervanging voor de oude aanpak waarbij weer-waarschuwingsteksten (regen, storm,
 * hagel, ...) als kant-en-klare Nederlandse zinnen werden opgebouwd (bv. "Kans op regen") en later
 * weer uit elkaar gehaald werden door te zoeken naar de letterlijke tekst "kans op " (voor de
 * titel/bericht-opsplitsing in meldingen, zie het oude [splitBadWeatherLabels]). Dat werkte alleen
 * toevallig omdat de app enkel Nederlands ondersteunde: in élke andere taal levert diezelfde
 * prefix-match niets op, en viel de opsplitsing stil terug op rommelige tekst.
 *
 * [WeatherReason] draagt het "wat" (type + intensiteit) en "hoe zeker" (kans) apart door de hele
 * pipeline mee, tot aan het allerlaatste render-moment - pas dan wordt (in de op dat moment actieve
 * taal) de uiteindelijke tekst samengesteld via [LanguageManager.getString], zowel voor los-lopende
 * schermteksten ([render]) als voor de titel/bericht-opsplitsing in meldingen ([toNotificationParts]).
 */
enum class WeatherReasonType {
    THUNDER, HAIL, ICE_ROAD, WET_SNOW,
    DRIZZLE_LIGHT, DRIZZLE_NORMAL, DRIZZLE_HEAVY, FREEZING,
    RAIN_LIGHT, RAIN_NORMAL, RAIN_HEAVY,
    SNOW_LIGHT, SNOW_NORMAL, SNOW_HEAVY, SNOW_GRAINS,
    RAIN_SHOWERS_LIGHT, RAIN_SHOWERS_NORMAL, RAIN_SHOWERS_HEAVY,
    SNOW_SHOWERS_LIGHT, SNOW_SHOWERS_HEAVY,
    /** Fallback zonder specifieke WMO-code beschikbaar (bv. cross-check faalde) - zelfde tekst als [RAIN_NORMAL]. */
    MIXED
}

/**
 * Of dit type onder "regen" valt in de brede zin (motregen, buien, generieke regen).
 *
 * Gebruikt om een gemeten regenperiode aan de juiste redenen te hangen: onweer of hagel in dezelfde
 * melding hebben hun eigen tijdvak en mogen dat van de regen niet overnemen.
 */
val WeatherReasonType.isRainLike: Boolean
    get() = this == WeatherReasonType.DRIZZLE_LIGHT || this == WeatherReasonType.DRIZZLE_NORMAL ||
        this == WeatherReasonType.DRIZZLE_HEAVY || this == WeatherReasonType.RAIN_LIGHT ||
        this == WeatherReasonType.RAIN_NORMAL || this == WeatherReasonType.RAIN_HEAVY ||
        this == WeatherReasonType.RAIN_SHOWERS_LIGHT || this == WeatherReasonType.RAIN_SHOWERS_NORMAL ||
        this == WeatherReasonType.RAIN_SHOWERS_HEAVY

/** Kans-niveau, zelfde grenzen als de oude probabilityLabel(): CERTAIN (>=80%) toont de platte
 * constatering zelf, HIGH (50-79%) / LOW (1-49%) / NONE (0%) wikkelen het zelfstandig-naamwoord in
 * een kans-sjabloon dat per taal vertaald is (zie "weather_prob_high/low/none" in LanguageManager). */
enum class ProbabilityTier { CERTAIN, HIGH, LOW, NONE }

fun tierForProbability(probability: Int): ProbabilityTier = when {
    probability >= 80 -> ProbabilityTier.CERTAIN
    probability >= 50 -> ProbabilityTier.HIGH
    probability > 0 -> ProbabilityTier.LOW
    else -> ProbabilityTier.NONE
}

/**
 * Eén losse weer-waarschuwings-reden. Twee smaken:
 * - "Tiered" ([flatText] == null): types mét kansdata (regen/sneeuw/hagel/ijzel/onweer) - de tekst
 *   hangt af van [probability] via [ProbabilityTier].
 * - "Flat" ([flatText] != null): types zonder kansdata (storm/orkaanachtige wind/extreme hitte) -
 *   altijd dezelfde, al volledig vertaalde tekst, ongeacht kans.
 */
data class WeatherReason private constructor(
    val type: WeatherReasonType,
    val probability: Int,
    private val flatText: String? = null,
    /**
     * Wanneer dit weertype zich voordoet, of null als dat niet bepaald kon worden (bv. bij een
     * beoordeling van één los uur, waar geen periode uit af te leiden valt).
     *
     * Bewust per reden en niet één keer voor de hele melding: regen van 14 tot 20 uur met onweer
     * van 17 tot 18 zijn twee mededelingen met elk een eigen tijd. Één gedeeld tijdvak zou van het
     * onweer een uren durende aangelegenheid maken.
     */
    val period: WeatherTimeBlock? = null
) {
    companion object {
        /** Vanaf deze duur wordt het tijdvak "de hele dag" i.p.v. een van-tot dat de halve dag beslaat. */
        private const val ALL_DAY_MILLIS = 12L * 60L * 60L * 1000L

        fun tiered(type: WeatherReasonType, probability: Int, period: WeatherTimeBlock? = null) =
            WeatherReason(type, probability, period = period)
        /** [text] moet al de juiste vertaalde tekst zijn (zie bv. weather_hurricane_noun/weather_storm_noun/weather_heat_noun). */
        fun flat(text: String, period: WeatherTimeBlock? = null) =
            WeatherReason(WeatherReasonType.MIXED, 100, flatText = text, period = period)
    }

    /** Dezelfde reden met [period] eraan gehangen - handig als de periode pas later bekend is. */
    fun withPeriod(period: WeatherTimeBlock?): WeatherReason = copy(period = period)

    private val tier: ProbabilityTier get() = tierForProbability(probability)

    private val statementKey: String get() = "weather_statement_${type.name.lowercase()}"
    private val nounKey: String get() = "weather_noun_${type.name.lowercase()}"

    /** Volledige tekst in de huidige taal, bv. "Kleine kans op regen" of "Het regent hard". */
    fun render(capitalizeFirst: Boolean): String {
        val text = flatText ?: when (tier) {
            ProbabilityTier.CERTAIN -> LanguageManager.getString(statementKey)
            ProbabilityTier.HIGH -> LanguageManager.getString("weather_prob_high").replace("{noun}", LanguageManager.getString(nounKey))
            ProbabilityTier.LOW -> LanguageManager.getString("weather_prob_low").replace("{noun}", LanguageManager.getString(nounKey))
            ProbabilityTier.NONE -> LanguageManager.getString("weather_prob_none").replace("{noun}", LanguageManager.getString(nounKey))
        }
        return if (capitalizeFirst) text.replaceFirstChar { it.uppercase() } else text.replaceFirstChar { it.lowercase() }
    }

    /**
     * Het tijdvak als leesbare tekst ("van 14:00 tot 20:00"), of null als er geen periode bekend is.
     *
     * Drie vormen, omdat één sjabloon niet in alle gevallen klopt:
     * - loopt het al ("tot 20:00") - "van 14 tot 20 uur" zeggen terwijl het buiten al regent is raar;
     * - duurt het [ALL_DAY_MILLIS] of langer, dan "de hele dag" in plaats van een lange reeks uren;
     * - anders gewoon van-tot.
     * Volgt er later nog een periode, dan wordt daar "en later opnieuw" aan geplakt - zie
     * [WeatherTimeBlock.hasLater], dat alleen slaat op periodes die dezelfde melding aangaan.
     *
     * De tijd wordt opgemaakt in de taal die in de app is ingesteld, dus 14:00 of 2:00 PM al naar
     * gelang wat daar gebruikelijk is.
     */
    fun periodText(now: Long = System.currentTimeMillis()): String? {
        val block = period ?: return null
        val formatter = DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(LanguageManager.getLocale())
            .withZone(ZoneId.systemDefault())
        fun at(millis: Long) = formatter.format(Instant.ofEpochMilli(millis))

        val base = when {
            block.endMillis - block.startMillis >= ALL_DAY_MILLIS ->
                LanguageManager.getString("weather_period_all_day")
            block.startMillis <= now ->
                LanguageManager.getString("weather_period_until").replace("{to}", at(block.endMillis))
            else -> LanguageManager.getString("weather_period_range")
                .replace("{from}", at(block.startMillis))
                .replace("{to}", at(block.endMillis))
        }
        return if (block.hasLater) {
            LanguageManager.getString("weather_period_later_again").replace("{period}", base)
        } else {
            base
        }
    }

    /** [text] met het tijdvak erachter (of ervoor, afhankelijk van de taal), als dat bekend is. */
    private fun withPeriodText(text: String, now: Long): String {
        val periodText = periodText(now) ?: return text
        return LanguageManager.getString("weather_with_period")
            .replace("{text}", text)
            .replace("{period}", periodText)
    }

    /**
     * Titel + kans-tekst voor meldingen (vervangt het oude, string-ontledende [combineWeatherReasonsForNotification]-
     * voorganger `splitBadWeatherLabels`, dat op de letterlijke Nederlandse tekst "kans op " zocht).
     * Bij CERTAIN (>=80%) of een flat-reden is er geen aparte kans om te tonen, dus dan wordt de
     * volledige constatering in beide teruggegeven (zelfde gedrag als voorheen).
     *
     * Het tijdvak komt in de titel, niet in het bericht: daar staat de kans, en die twee horen niet
     * door elkaar te lopen. Bij meerdere redenen houdt elke reden zo zijn eigen tijd - "Regen van
     * 14:00 tot 20:00, onweer van 17:00 tot 19:00".
     */
    fun toNotificationParts(now: Long = System.currentTimeMillis()): Pair<String, String> {
        if (flatText != null) {
            val text = flatText.replaceFirstChar { it.uppercase() }
            return withPeriodText(text, now) to text
        }
        val noun = LanguageManager.getString(nounKey).replaceFirstChar { it.uppercase() }
        return when (tier) {
            ProbabilityTier.CERTAIN -> {
                val statement = LanguageManager.getString(statementKey).replaceFirstChar { it.uppercase() }
                withPeriodText(statement, now) to statement
            }
            ProbabilityTier.HIGH -> withPeriodText(noun, now) to LanguageManager.getString("weather_chance_high")
            ProbabilityTier.LOW -> withPeriodText(noun, now) to LanguageManager.getString("weather_chance_low")
            ProbabilityTier.NONE -> withPeriodText(noun, now) to LanguageManager.getString("weather_chance_none")
        }
    }

    /**
     * Titel + kans-percentage voor regen-specifieke meldingen (vervangt het oude
     * `formatRainAlertTitleAndMessage`). In tegenstelling tot [toNotificationParts] toont dit ALTIJD
     * het percentage als bericht, ook bij CERTAIN - zelfde gedrag als voorheen.
     */
    fun toRainNotificationParts(isTomorrow: Boolean, now: Long = System.currentTimeMillis()): Pair<String, String> {
        val reason = if (isTomorrow) {
            "${LanguageManager.getString("weather_tomorrow_prefix")} ${render(capitalizeFirst = false)}"
        } else {
            render(capitalizeFirst = true)
        }
        val title = withPeriodText(reason, now)
        val message = LanguageManager.getString("weather_chance_percent").replace("{percent}", probability.toString())
        return title to message
    }

}

/** Combineert meerdere redenen (bv. regen én storm op dezelfde dag) tot 1 titel + 1 kans-tekst voor een melding. */
fun combineWeatherReasonsForNotification(reasons: List<WeatherReason>): Pair<String, String> {
    val parts = reasons.map { it.toNotificationParts() }
    return parts.joinToString(", ") { it.first } to parts.joinToString(", ") { it.second }
}

/** WMO-weercode → [WeatherReasonType], voor regen/sneeuw-achtige condities (zie [scanBadWeatherWindow]). */
fun weatherReasonTypeForCode(code: Int): WeatherReasonType = when (code) {
    51 -> WeatherReasonType.DRIZZLE_LIGHT
    53 -> WeatherReasonType.DRIZZLE_NORMAL
    55 -> WeatherReasonType.DRIZZLE_HEAVY
    56, 57, 66, 67 -> WeatherReasonType.FREEZING
    61 -> WeatherReasonType.RAIN_LIGHT
    63 -> WeatherReasonType.RAIN_NORMAL
    65 -> WeatherReasonType.RAIN_HEAVY
    71 -> WeatherReasonType.SNOW_LIGHT
    73 -> WeatherReasonType.SNOW_NORMAL
    75 -> WeatherReasonType.SNOW_HEAVY
    77 -> WeatherReasonType.SNOW_GRAINS
    80 -> WeatherReasonType.RAIN_SHOWERS_LIGHT
    81 -> WeatherReasonType.RAIN_SHOWERS_NORMAL
    82 -> WeatherReasonType.RAIN_SHOWERS_HEAVY
    85 -> WeatherReasonType.SNOW_SHOWERS_LIGHT
    86 -> WeatherReasonType.SNOW_SHOWERS_HEAVY
    else -> WeatherReasonType.MIXED
}
