package com.gdad.bags.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Exact helpers for UI money input and display. Business values remain integer paisa. */
object MoneyAmounts {
    fun parsePaisa(input: String, minimumPaisa: Long = 0): Long? = runCatching {
        require(minimumPaisa >= 0)
        BigDecimal(input.trim())
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it >= minimumPaisa }
    }.getOrNull()

    fun formatNpr(paisa: Long): String =
        "Rs ${BigDecimal.valueOf(paisa, 2).setScale(2).toPlainString()}"

    fun multiplyPaisa(unitPaisa: Long, quantity: Int): Long? =
        multiplyPaisa(unitPaisa, quantity.toLong())

    fun multiplyPaisa(unitPaisa: Long, quantity: Long): Long? =
        runCatching { Math.multiplyExact(unitPaisa, quantity) }.getOrNull()

    fun sumPaisa(values: Iterable<Long>): Long? = runCatching {
        values.fold(0L, Math::addExact)
    }.getOrNull()

    fun subtractPaisa(value: Long, deduction: Long): Long? =
        runCatching { Math.subtractExact(value, deduction) }.getOrNull()

    /** Rounds a proportional value to the nearest paisa without an overflowing multiply. */
    fun proratePaisa(totalPaisa: Long, part: Int, whole: Int): Long? = runCatching {
        require(totalPaisa >= 0)
        require(whole > 0)
        require(part in 0..whole)
        val quotient = totalPaisa / whole
        val remainder = totalPaisa % whole
        Math.addExact(
            Math.multiplyExact(quotient, part.toLong()),
            (Math.multiplyExact(remainder, part.toLong()) + whole / 2L) / whole,
        )
    }.getOrNull()
}
