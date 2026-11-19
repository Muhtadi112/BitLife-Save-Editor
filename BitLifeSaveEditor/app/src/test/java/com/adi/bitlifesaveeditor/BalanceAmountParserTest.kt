package com.adi.bitlifesaveeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceAmountParserTest {
    @Test
    fun parsesIndonesianAndEuropeanGrouping() {
        assertEquals(1_234_567.89, BalanceAmountParser.parse("1.234.567,89")!!, 0.0)
        assertEquals(1_234.56, BalanceAmountParser.parse("1234,56")!!, 0.0)
    }

    @Test
    fun parsesUsGrouping() {
        assertEquals(1_234_567.89, BalanceAmountParser.parse("1,234,567.89")!!, 0.0)
    }

    @Test
    fun acceptsLargeExactlyRepresentableBalance() {
        assertEquals(1.0e18, BalanceAmountParser.parse("1000000000000000000")!!, 0.0)
        assertEquals(9_007_199_254_740_992.0, BalanceAmountParser.parse("9007199254740992")!!, 0.0)
    }

    @Test
    fun rejectsInputThatWouldSilentlyRoundInDouble() {
        assertNull(BalanceAmountParser.parse("9007199254740993"))
    }

    @Test
    fun rejectsMalformedGroupingNegativeAndOutOfRangeValues() {
        assertNull(BalanceAmountParser.parse("1,23,4"))
        assertNull(BalanceAmountParser.parse("-100"))
        assertNull(BalanceAmountParser.parse("1000000000000000001"))
    }
}
