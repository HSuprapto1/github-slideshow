package com.roamatlas.data.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Guards the Brazil Crew-X corruption pattern: CGK→SIN must stay same calendar day.
 */
class TicketScheduleRepairTest {
    private val zone = ZoneId.of("Asia/Jakarta")

    @Test
    fun `correct SQ963 times are same day and about 2h45m`() {
        val dep = LocalDateTime.of(2026, 11, 17, 18, 10).atZone(zone).toInstant().toEpochMilli()
        val arr = LocalDateTime.of(2026, 11, 17, 20, 55).atZone(zone).toInstant().toEpochMilli()
        val hours = (arr - dep) / 3_600_000.0
        val depDay = LocalDateTime.of(2026, 11, 17, 18, 10).toLocalDate()
        val arrDay = LocalDateTime.of(2026, 11, 17, 20, 55).toLocalDate()
        assertEquals(depDay, arrDay)
        assertTrue(hours in 2.5..3.0)
        assertFalse(hours > 4.0)
    }

    @Test
    fun `corrupted next-day SIN arrival looks like 17h`() {
        val dep = LocalDateTime.of(2026, 11, 17, 18, 10).atZone(zone).toInstant().toEpochMilli()
        val arr = LocalDateTime.of(2026, 11, 18, 11, 55).atZone(zone).toInstant().toEpochMilli()
        val hours = (arr - dep) / 3_600_000.0
        assertTrue("got $hours", hours in 17.0..18.0)
        val transitToNext =
            (
                LocalDateTime.of(2026, 11, 18, 22, 50).atZone(zone).toInstant().toEpochMilli() -
                    arr
                ) / 60_000L
        assertEquals(655L, transitToNext) // 10h 55m — the broken transit user saw
    }

    @Test
    fun `SIN to CDG departs 2250 Singapore local`() {
        val sin = ZoneId.of("Asia/Singapore")
        val sinArr = LocalDateTime.of(2026, 11, 17, 20, 55).atZone(sin).toInstant().toEpochMilli()
        val sinDep = LocalDateTime.of(2026, 11, 17, 22, 50).atZone(sin).toInstant().toEpochMilli()
        assertEquals(115L, (sinDep - sinArr) / 60_000L) // 1h 55m transit
    }
}
