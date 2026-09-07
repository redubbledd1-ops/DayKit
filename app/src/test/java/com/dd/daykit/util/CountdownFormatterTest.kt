package com.dd.daykit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownFormatterTest {

    @Test
    fun zeroAndNegative_allModes_useZeroOutputs() {
        assertEquals("0:00", CountdownFormatter.format(0L, CountdownMode.COMPACT_TRAY))
        assertEquals("00:00", CountdownFormatter.format(0L, CountdownMode.PRECISE_UI))
        assertEquals("0 min", CountdownFormatter.format(0L, CountdownMode.SHORT_LABEL))

        assertEquals("0:00", CountdownFormatter.format(-5_000L, CountdownMode.COMPACT_TRAY))
        assertEquals("00:00", CountdownFormatter.format(-1L, CountdownMode.PRECISE_UI))
        assertEquals("0 min", CountdownFormatter.format(-999L, CountdownMode.SHORT_LABEL))
    }

    @Test
    fun exactlyOneHour_allModes() {
        val ms = 3_600_000L
        assertEquals("1:00:00", CountdownFormatter.format(ms, CountdownMode.COMPACT_TRAY))
        assertEquals("1:00:00", CountdownFormatter.format(ms, CountdownMode.PRECISE_UI))
        assertEquals("1u", CountdownFormatter.format(ms, CountdownMode.SHORT_LABEL))
    }

    @Test
    fun exactlyTenMinutes_allModes() {
        val ms = 600_000L
        assertEquals("10:00", CountdownFormatter.format(ms, CountdownMode.COMPACT_TRAY))
        assertEquals("10:00", CountdownFormatter.format(ms, CountdownMode.PRECISE_UI))
        assertEquals("10 min", CountdownFormatter.format(ms, CountdownMode.SHORT_LABEL))
    }

    @Test
    fun justBelowTenMinutes_usesSubTenMinuteRules() {
        val ms = 599_000L // 9:59
        assertEquals("9:59", CountdownFormatter.format(ms, CountdownMode.COMPACT_TRAY))
        assertEquals("09:59", CountdownFormatter.format(ms, CountdownMode.PRECISE_UI))
        assertEquals("9 min", CountdownFormatter.format(ms, CountdownMode.SHORT_LABEL))
    }

    @Test
    fun justBelowOneHour_usesSubHourRules() {
        val ms = 3_599_999L // 59:59.999 → floor 59:59
        assertEquals("59:59", CountdownFormatter.format(ms, CountdownMode.COMPACT_TRAY))
        assertEquals("59:59", CountdownFormatter.format(ms, CountdownMode.PRECISE_UI))
        assertEquals("59 min", CountdownFormatter.format(ms, CountdownMode.SHORT_LABEL))
    }

    @Test
    fun arbitraryOverOneHour_compactAndPreciseAndShort() {
        val ms = 3_725_000L // 1h 2m 5s
        assertEquals("1:02:05", CountdownFormatter.format(ms, CountdownMode.COMPACT_TRAY))
        assertEquals("1:02:05", CountdownFormatter.format(ms, CountdownMode.PRECISE_UI))
        assertEquals("1u", CountdownFormatter.format(ms, CountdownMode.SHORT_LABEL))
    }

    @Test
    fun arbitraryBetweenTenMinutesAndOneHour() {
        val ms = 2_585_000L // 43m 5s
        assertEquals("43:05", CountdownFormatter.format(ms, CountdownMode.COMPACT_TRAY))
        assertEquals("43:05", CountdownFormatter.format(ms, CountdownMode.PRECISE_UI))
        assertEquals("43 min", CountdownFormatter.format(ms, CountdownMode.SHORT_LABEL))
    }

    @Test
    fun arbitraryUnderTenMinutes() {
        val ms = 598_000L // 9m 58s
        assertEquals("9:58", CountdownFormatter.format(ms, CountdownMode.COMPACT_TRAY))
        assertEquals("09:58", CountdownFormatter.format(ms, CountdownMode.PRECISE_UI))
        assertEquals("9 min", CountdownFormatter.format(ms, CountdownMode.SHORT_LABEL))
    }

    @Test
    fun preciseUiNoSeconds_zeroAndOverOneHour() {
        assertEquals("0:00", CountdownFormatter.format(0L, CountdownMode.PRECISE_UI_NO_SECONDS))
        assertEquals("19:02", CountdownFormatter.format(68_532_000L, CountdownMode.PRECISE_UI_NO_SECONDS)) // 19h 2m 12s
        assertEquals("42:05", CountdownFormatter.format(2_525_000L, CountdownMode.PRECISE_UI_NO_SECONDS)) // 42m 5s
    }
}
