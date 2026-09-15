package com.dd.daykit

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Regressietests voor de weer-scan: welke uren tellen mee, wanneer is er sprake van regen, en welk
 * tijdvak rolt daaruit.
 *
 * Draait puur op de JVM, zonder toestel of netwerk. De forecast-objecten worden opgebouwd met
 * dezelfde vorm als de API levert (ISO-tijdstempels, lijsten die korter mogen zijn dan de uurlijst),
 * en de laatste test parseert een echt API-antwoord zodat ook de veldnamen gecontroleerd worden.
 *
 * Alles wat hier getest wordt is bewust de laag ónder de instellingen: geen Context, geen
 * SettingsManager, geen vertalingen. Dat maakt de tests deterministisch en snel.
 */
class WeatherScanTest {

    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun epochAt(iso: String): Long =
        LocalDateTime.parse(iso, fmt).atZone(zone).toInstant().toEpochMilli()

    /** Bouwt een forecast van [hours] uur vanaf [startIso], met per uur-index de opgegeven waarden. */
    private fun forecastOf(
        startIso: String = "2026-08-04T00:00",
        hours: Int = 48,
        probability: (Int) -> Int = { 0 },
        weatherCode: (Int) -> Int = { 0 },
        precipitation: (Int) -> Double = { 0.0 },
        temperature: (Int) -> Double = { 15.0 },
        windGusts: (Int) -> Double = { 5.0 },
        minutely15: Minutely15Data? = null
    ): WeatherForecast {
        val start = LocalDateTime.parse(startIso, fmt)
        val indices = (0 until hours)
        return WeatherForecast(
            latitude = 52.37,
            longitude = 4.89,
            hourly = HourlyData(
                time = indices.map { start.plusHours(it.toLong()).format(fmt) },
                temperature2m = indices.map { temperature(it) },
                precipitationProbability = indices.map { probability(it) },
                weathercode = indices.map { weatherCode(it) },
                windspeed10m = indices.map { windGusts(it) },
                windGusts10m = indices.map { windGusts(it) },
                precipitation = indices.map { precipitation(it) }
            ),
            minutely15 = minutely15
        )
    }

    /** Uur-index binnen [forecastOf] die hoort bij een tijdstempel op dag 1. */
    private fun hourIndex(hour: Int) = hour

    // ── Vensterregel: een uurwaarde beschrijft het uur vóór zijn stempel ──────────────────────

    @Test
    fun dagvenster_neemt_het_uur_voor_het_stempel() {
        val forecast = forecastOf()
        val scan = scanBadWeatherWindow(
            forecast,
            epochAt("2026-08-04T06:00"),
            epochAt("2026-08-04T23:00")
        )

        // Het stempel van 06:00 beschrijft 05:00-06:00 (nacht) en valt dus buiten de dag; het
        // stempel van 23:00 beschrijft 22:00-23:00 en hoort er nog wél bij.
        assertEquals(epochAt("2026-08-04T07:00"), scan.hourSamples.first().epochMillis)
        assertEquals(epochAt("2026-08-04T23:00"), scan.hourSamples.last().epochMillis)
        assertEquals(17, scan.hourSamples.size)
    }

    // ── Per uur beoordelen i.p.v. maxima over het venster ─────────────────────────────────────

    @Test
    fun kans_uit_het_ene_uur_wordt_niet_bevestigd_door_een_ander_uur() {
        // 47% om 10:00 bij een heldere hemel, en pas om 20:00 een bui. Voorheen bevestigde die bui
        // de kans van 's ochtends en ging de melding af.
        val forecast = forecastOf(
            probability = { i -> if (i == hourIndex(10)) 47 else if (i == hourIndex(20)) 30 else 0 },
            weatherCode = { i -> if (i == hourIndex(20)) 61 else 0 },
            precipitation = { i -> if (i == hourIndex(20)) 0.4 else 0.0 }
        )
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))

        assertNull(evaluateRain(scan, rainEnabled = true, rainThreshold = 40))
    }

    @Test
    fun hoge_kans_zonder_neerslag_of_regencode_blijft_stil() {
        val forecast = forecastOf(probability = { i -> if (i == hourIndex(10)) 55 else 0 })
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))

        assertNull(evaluateRain(scan, rainEnabled = true, rainThreshold = 40))
    }

    @Test
    fun kans_en_bevestiging_in_hetzelfde_uur_levert_wel_een_melding() {
        val regenUren = setOf(hourIndex(15), hourIndex(16))
        val forecast = forecastOf(
            probability = { i -> if (i in regenUren) if (i == hourIndex(15)) 80 else 75 else 0 },
            weatherCode = { i -> if (i == hourIndex(15)) 65 else if (i == hourIndex(16)) 63 else 0 },
            precipitation = { i -> if (i in regenUren) 2.0 else 0.0 }
        )
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))

        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)
        assertNotNull(rain)
        assertEquals(80, rain!!.probability)
        // Weercode uit hetzelfde uur als de hoogste kans, niet uit een willekeurig ander uur.
        assertEquals(65, rain.weatherCode)
        assertFalse(rain.fromCodeOnly)
        assertEquals(2, rain.hours.size)
    }

    @Test
    fun testdrempel_nul_meldt_altijd() {
        val forecast = forecastOf(probability = { i -> if (i == hourIndex(10)) 20 else 0 })
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))

        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 0)
        assertNotNull(rain)
        assertEquals(20, rain!!.probability)
    }

    @Test
    fun zonder_kansdata_steunt_de_melding_op_de_weercode() {
        val forecast = forecastOf(
            weatherCode = { i -> if (i == hourIndex(15)) 55 else 0 },
            precipitation = { i -> if (i == hourIndex(15)) 0.3 else 0.0 }
        )
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))

        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)
        assertNotNull(rain)
        assertTrue(rain!!.fromCodeOnly)
        // Geen percentage tonen bij een melding die alleen op de code steunt.
        assertNull(rainAlertProbability(scan, rainEnabled = true, rainThreshold = 40))
    }

    // ── Tijdvakken ───────────────────────────────────────────────────────────────────────────

    @Test
    fun tijdvak_loopt_van_een_uur_voor_het_eerste_stempel_tot_het_laatste() {
        val regenUren = setOf(hourIndex(15), hourIndex(16), hourIndex(17))
        val forecast = regenForecast(regenUren)
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))
        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)!!

        val block = rainTimeBlocks(forecast, rain, 40).single()
        assertEquals(epochAt("2026-08-04T14:00"), block.startMillis)
        assertEquals(epochAt("2026-08-04T17:00"), block.endMillis)
        assertFalse(block.hasLater)
    }

    @Test
    fun tijdvak_wordt_doorgemeten_buiten_het_detectievenster() {
        // Regen van 14:00 tot 20:00, afspraak van 12:00 tot 15:00. Het venster ziet maar één uur;
        // het gemelde tijdvak moet toch de hele bui beslaan.
        val regenUren = (15..20).map { hourIndex(it) }.toSet()
        val forecast = regenForecast(regenUren)
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T12:00"), epochAt("2026-08-04T15:00"))
        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)!!

        assertEquals(1, rain.hours.size)
        val block = rainTimeBlocks(forecast, rain, 40).single()
        assertEquals(epochAt("2026-08-04T14:00"), block.startMillis)
        assertEquals(epochAt("2026-08-04T20:00"), block.endMillis)
    }

    @Test
    fun gat_van_een_uur_wordt_dichtgeplakt() {
        // Nat om 15 en 16, droog om 17, weer nat om 18 en 19 - dat is één periode.
        val regenUren = setOf(hourIndex(15), hourIndex(16), hourIndex(18), hourIndex(19))
        val forecast = regenForecast(regenUren)
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))
        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)!!

        val block = rainTimeBlocks(forecast, rain, 40).single()
        assertEquals(epochAt("2026-08-04T14:00"), block.startMillis)
        assertEquals(epochAt("2026-08-04T19:00"), block.endMillis)
    }

    @Test
    fun groter_gat_geeft_twee_periodes_met_later_opnieuw() {
        val regenUren = setOf(hourIndex(9), hourIndex(10), hourIndex(15), hourIndex(16))
        val forecast = regenForecast(regenUren)
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))
        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)!!

        val blocks = rainTimeBlocks(forecast, rain, 40)
        assertEquals(2, blocks.size)
        assertEquals(epochAt("2026-08-04T08:00"), blocks[0].startMillis)
        assertEquals(epochAt("2026-08-04T10:00"), blocks[0].endMillis)
        assertTrue(blocks[0].hasLater)
        assertFalse(blocks[1].hasLater)
    }

    @Test
    fun regen_elders_in_de_week_wordt_niet_meegetrokken() {
        val regenUren = setOf(hourIndex(15), hourIndex(16), hourIndex(40))
        val forecast = regenForecast(regenUren)
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))
        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)!!

        val blocks = rainTimeBlocks(forecast, rain, 40)
        assertEquals(1, blocks.size)
        assertEquals(epochAt("2026-08-04T14:00"), blocks.single().startMillis)
        assertEquals(epochAt("2026-08-04T16:00"), blocks.single().endMillis)
    }

    // ── Kwartierdata ─────────────────────────────────────────────────────────────────────────

    @Test
    fun kwartierdata_verscherpt_de_randen() {
        // Uurdata zegt 12:00-15:00; in werkelijkheid begint het om 12:30 en stopt het om 14:45.
        val natteKwartieren = quarterHours(
            "2026-08-04T12:45", "2026-08-04T13:00", "2026-08-04T13:15", "2026-08-04T13:30",
            "2026-08-04T13:45", "2026-08-04T14:00", "2026-08-04T14:15", "2026-08-04T14:30",
            "2026-08-04T14:45"
        )
        val forecast = regenForecast(setOf(hourIndex(13), hourIndex(14), hourIndex(15)), natteKwartieren)
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))
        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)!!

        val block = rainTimeBlocks(forecast, rain, 40).single()
        assertEquals(epochAt("2026-08-04T12:30"), block.startMillis)
        assertEquals(epochAt("2026-08-04T14:45"), block.endMillis)
    }

    @Test
    fun geinterpoleerde_kwartierdata_verandert_niets() {
        // Buiten het dekkingsgebied smeert Open-Meteo de uurwaarde uit over vier gelijke kwartieren;
        // dan is er geen extra informatie en moet het hele uur blijven staan.
        val alleKwartieren = quarterHours(
            *(0..11).map { i ->
                LocalDateTime.parse("2026-08-04T12:15", fmt).plusMinutes(15L * i).format(fmt)
            }.toTypedArray()
        )
        val forecast = regenForecast(setOf(hourIndex(13), hourIndex(14), hourIndex(15)), alleKwartieren)
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))
        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)!!

        val block = rainTimeBlocks(forecast, rain, 40).single()
        assertEquals(epochAt("2026-08-04T12:00"), block.startMillis)
        assertEquals(epochAt("2026-08-04T15:00"), block.endMillis)
    }

    @Test
    fun zonder_kwartierdata_blijft_het_tijdvak_op_hele_uren() {
        val forecast = regenForecast(setOf(hourIndex(13), hourIndex(14), hourIndex(15)))
        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))
        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)!!

        val block = rainTimeBlocks(forecast, rain, 40).single()
        assertEquals(epochAt("2026-08-04T12:00"), block.startMillis)
        assertEquals(epochAt("2026-08-04T15:00"), block.endMillis)
    }

    // ── Meerdere vensters (bereik "rond agenda-items") ────────────────────────────────────────

    @Test
    fun losse_vensters_per_afspraak_laten_regen_ertussen_buiten_beschouwing() {
        val regenUren = setOf(hourIndex(9), hourIndex(10), hourIndex(23))
        val forecast = regenForecast(regenUren)

        val rondAfspraak = scanBadWeatherWindows(
            forecast,
            listOf(epochAt("2026-08-04T13:00") to epochAt("2026-08-04T15:00"))
        )
        assertNull(evaluateRain(rondAfspraak, rainEnabled = true, rainThreshold = 40))

        val heleDag = scanBadWeatherWindow(forecast, epochAt("2026-08-04T06:00"), epochAt("2026-08-04T23:00"))
        assertNotNull(evaluateRain(heleDag, rainEnabled = true, rainThreshold = 40))
    }

    @Test
    fun geen_vensters_betekent_geen_melding() {
        val forecast = regenForecast(setOf(hourIndex(9), hourIndex(10)))
        val scan = scanBadWeatherWindows(forecast, emptyList())

        assertTrue(scan.hourSamples.isEmpty())
        assertNull(evaluateRain(scan, rainEnabled = true, rainThreshold = 40))
    }

    // ── Vorm van het API-antwoord ────────────────────────────────────────────────────────────

    @Test
    fun api_antwoord_wordt_correct_ingelezen_inclusief_kwartierblok() {
        val json = """
            {
              "latitude": 52.37,
              "longitude": 4.89,
              "generationtime_ms": 0.21,
              "utc_offset_seconds": 7200,
              "timezone": "Europe/Amsterdam",
              "hourly_units": {"temperature_2m": "°C"},
              "hourly": {
                "time": ["2026-08-04T14:00", "2026-08-04T15:00", "2026-08-04T16:00"],
                "temperature_2m": [18.2, 18.9, 19.1],
                "precipitation_probability": [10, 80, null],
                "precipitation": [0.0, 2.1, null],
                "weathercode": [3, 63, 61],
                "windspeed_10m": [12.0, 14.5, 13.0],
                "wind_gusts_10m": [22.0, 30.5, 28.0],
                "winddirection_10m": [230, 245, null]
              },
              "minutely_15": {
                "time": ["2026-08-04T14:45", "2026-08-04T15:00", "2026-08-04T15:15"],
                "precipitation": [0.0, 0.7, null]
              }
            }
        """.trimIndent()

        val forecast = Json { ignoreUnknownKeys = true }.decodeFromString<WeatherForecast>(json)

        assertEquals(3, forecast.hourly.time.size)
        // Ontbrekende waarden komen als null binnen en mogen de scan niet laten omvallen.
        assertNull(forecast.hourly.precipitationProbability[2])
        assertNotNull(forecast.minutely15)
        assertEquals(3, forecast.minutely15!!.time.size)
        assertEquals(0.7, forecast.minutely15!!.precipitation[1]!!, 0.0001)

        val scan = scanBadWeatherWindow(forecast, epochAt("2026-08-04T14:00"), epochAt("2026-08-04T17:00"))
        val rain = evaluateRain(scan, rainEnabled = true, rainThreshold = 40)
        assertNotNull(rain)
        assertEquals(80, rain!!.probability)
        assertEquals(63, rain.weatherCode)
    }

    // ── Temperatuurwissel: anker-afspraak (dag van tevoren vs vandaag) ────────────────────────

    private fun eventAt(iso: String, label: String = "fietsen naar huis") =
        AlarmItem(id = epochAt(iso), epochMillis = epochAt(iso), label = label)

    @Test
    fun dag_van_tevoren_negeert_afspraak_van_alleen_vandaag() {
        val now = epochAt("2026-09-15T13:00")
        val todayOnly = listOf(eventAt("2026-09-15T15:00"))
        assertNull(
            pickTempChangeAnchorEvent(
                todayOnly, now,
                dayBeforeEnabled = true, sameDayEnabled = false, firstEventEnabled = false
            )
        )
    }

    @Test
    fun dag_van_tevoren_pakt_afspraak_van_morgen_niet_van_vandaag() {
        val now = epochAt("2026-09-15T13:00")
        val events = listOf(
            eventAt("2026-09-15T15:00", "vandaag fietsen"),
            eventAt("2026-09-16T15:00", "morgen fietsen")
        )
        val picked = pickTempChangeAnchorEvent(
            events, now,
            dayBeforeEnabled = true, sameDayEnabled = false, firstEventEnabled = false
        )
        assertEquals("morgen fietsen", picked?.label)
        assertEquals(epochAt("2026-09-16T15:00"), picked?.epochMillis)
    }

    @Test
    fun dag_van_tevoren_toont_melding_als_alleen_morgen_een_afspraak_heeft() {
        val now = epochAt("2026-09-15T13:00")
        val tomorrowOnly = listOf(eventAt("2026-09-16T15:00"))
        val picked = pickTempChangeAnchorEvent(
            tomorrowOnly, now,
            dayBeforeEnabled = true, sameDayEnabled = false, firstEventEnabled = false
        )
        assertEquals(epochAt("2026-09-16T15:00"), picked?.epochMillis)
    }

    @Test
    fun zelfde_dag_pakt_afspraak_van_vandaag_niet_van_morgen() {
        val now = epochAt("2026-09-15T13:00")
        val events = listOf(
            eventAt("2026-09-15T15:00", "vandaag"),
            eventAt("2026-09-16T15:00", "morgen")
        )
        val picked = pickTempChangeAnchorEvent(
            events, now,
            dayBeforeEnabled = false, sameDayEnabled = true, firstEventEnabled = false
        )
        assertEquals("vandaag", picked?.label)
    }

    @Test
    fun vergelijking_bij_afspraak_morgen_gebruikt_dezelfde_kloktijd_vandaag() {
        val now = epochAt("2026-09-15T13:00")
        val event = epochAt("2026-09-16T15:00")
        val (from, to) = tempChangeCompareTimes(event, now)
        assertEquals(epochAt("2026-09-15T15:00"), from)
        assertEquals(event, to)
    }

    @Test
    fun vergelijking_bij_afspraak_vandaag_kijkt_naar_dezelfde_kloktijd_morgen() {
        val now = epochAt("2026-09-15T13:00")
        val event = epochAt("2026-09-15T15:00")
        val (from, to) = tempChangeCompareTimes(event, now)
        assertEquals(event, from)
        assertEquals(epochAt("2026-09-16T15:00"), to)
    }

    // ── Hulpjes ──────────────────────────────────────────────────────────────────────────────

    /** Forecast waarin de opgegeven uur-indexen echte regen hebben (80%, matige regen, 1 mm). */
    private fun regenForecast(
        regenUren: Set<Int>,
        minutely15: Minutely15Data? = null
    ): WeatherForecast = forecastOf(
        probability = { i -> if (i in regenUren) 80 else 0 },
        weatherCode = { i -> if (i in regenUren) 63 else 0 },
        precipitation = { i -> if (i in regenUren) 1.0 else 0.0 },
        minutely15 = minutely15
    )

    /** Kwartierblok waarin precies de opgegeven tijdstempels neerslag hebben. */
    private fun quarterHours(vararg natteStempels: String): Minutely15Data {
        val start = LocalDateTime.parse("2026-08-04T00:00", fmt)
        val alle = (0 until 4 * 48).map { start.plusMinutes(15L * it).format(fmt) }
        val nat = natteStempels.toSet()
        return Minutely15Data(
            time = alle,
            precipitation = alle.map { if (it in nat) 0.5 else 0.0 }
        )
    }
}
