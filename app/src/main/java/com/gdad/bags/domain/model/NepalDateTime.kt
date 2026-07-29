package com.gdad.bags.domain.model

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Canonical business clock for Nepal-facing dates and timestamps. */
object NepalDateTime {
    val zoneId: ZoneId = ZoneId.of("Asia/Kathmandu")

    fun today(clock: Clock = Clock.systemUTC()): LocalDate =
        LocalDate.now(clock.withZone(zoneId))

    fun todayIso(clock: Clock = Clock.systemUTC()): String = today(clock).toString()

    fun isValidIsoDate(value: String): Boolean =
        runCatching { LocalDate.parse(value) }.isSuccess
}
