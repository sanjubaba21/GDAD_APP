package com.gdad.bags.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyAmountsTest {
    @Test
    fun parsesRupeesToExactPaisaWithoutBinaryRounding() {
        assertEquals(1L, MoneyAmounts.parsePaisa("0.01"))
        assertEquals(10L, MoneyAmounts.parsePaisa("0.10"))
        assertEquals(12_345L, MoneyAmounts.parsePaisa("123.45"))
        assertEquals(Long.MAX_VALUE, MoneyAmounts.parsePaisa("92233720368547758.07"))
    }

    @Test
    fun rejectsExtraPrecisionNegativeAndOverflowingInputs() {
        assertNull(MoneyAmounts.parsePaisa("1.001"))
        assertNull(MoneyAmounts.parsePaisa("-0.01"))
        assertNull(MoneyAmounts.parsePaisa("92233720368547758.08"))
        assertNull(MoneyAmounts.parsePaisa("not money"))
        assertNull(MoneyAmounts.parsePaisa("0", minimumPaisa = 1))
    }

    @Test
    fun arithmeticFailsClosedOnOverflow() {
        assertEquals(600L, MoneyAmounts.multiplyPaisa(200, 3))
        assertNull(MoneyAmounts.multiplyPaisa(Long.MAX_VALUE, 2))
        assertNull(MoneyAmounts.multiplyPaisa(Long.MAX_VALUE, 2L))
        assertEquals(6L, MoneyAmounts.sumPaisa(listOf(1, 2, 3)))
        assertEquals(-1L, MoneyAmounts.sumPaisa(listOf(Long.MAX_VALUE, -Long.MAX_VALUE, -1)))
        assertNull(MoneyAmounts.sumPaisa(listOf(Long.MAX_VALUE, 1)))
        assertNull(MoneyAmounts.subtractPaisa(Long.MIN_VALUE, 1))
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE) + Money(1) }
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE) * 2 }
    }

    @Test
    fun proratingRoundsHalfUpWithoutOverflow() {
        assertEquals(33L, MoneyAmounts.proratePaisa(100, 1, 3))
        assertEquals(67L, MoneyAmounts.proratePaisa(100, 2, 3))
        assertEquals(Long.MAX_VALUE, MoneyAmounts.proratePaisa(Long.MAX_VALUE, 1, 1))
        assertNull(MoneyAmounts.proratePaisa(100, 2, 1))
    }

    @Test
    fun formattingNeverUsesFloatingPoint() {
        assertEquals("NPR 0.01", MoneyAmounts.formatNpr(1))
        assertEquals("NPR 92233720368547758.07", MoneyAmounts.formatNpr(Long.MAX_VALUE))
        assertEquals("NPR -0.01", MoneyAmounts.formatNpr(-1))
    }
}
