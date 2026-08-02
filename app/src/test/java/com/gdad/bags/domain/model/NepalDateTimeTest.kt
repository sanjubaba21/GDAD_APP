package com.gdad.bags.domain.model

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class NepalDateTimeTest {
    @Test
    fun businessDateRollsOverAtKathmanduMidnight() {
        val beforeMidnight = Clock.fixed(
            Instant.parse("2026-07-28T18:14:59Z"),
            ZoneOffset.UTC,
        )
        val afterMidnight = Clock.fixed(
            Instant.parse("2026-07-28T18:15:00Z"),
            ZoneOffset.UTC,
        )

        assertEquals(LocalDate.parse("2026-07-28"), NepalDateTime.today(beforeMidnight))
        assertEquals("2026-07-29", NepalDateTime.todayIso(afterMidnight))
    }

    @Test
    fun businessDateAcceptsOnlyRealIsoCalendarDates() {
        assertEquals(true, NepalDateTime.isValidIsoDate("2026-07-29"))
        assertEquals(false, NepalDateTime.isValidIsoDate("2026-02-29"))
        assertEquals(false, NepalDateTime.isValidIsoDate("29-07-2026"))
    }
}
