package com.malaxg.milo.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesYuanWithoutFloatingPointRounding() {
        assertEquals(1234L, MoneyCents.fromYuan("12.34").value)
        assertEquals(5L, MoneyCents.fromYuan(".05").value)
        assertEquals(-120L, MoneyCents.fromYuan("-1.2").value)
    }

    @Test
    fun rejectsInvalidPrecisionAndOverflow() {
        assertEquals(0L, MoneyCents.fromYuan("1.239").value)
        assertEquals(0L, MoneyCents.fromYuan("92233720368547758.08").value)
        assertEquals(0L, MoneyCents.fromYuan("abc").value)
    }

    @Test
    fun formatsTheMinimumLongValue() {
        assertEquals("-92233720368547758.08", MoneyCents(Long.MIN_VALUE).toDisplayString())
    }

    @Test
    fun formatsCentsBeyondDoublePrecisionExactly() {
        assertEquals("90071992547409.93", MoneyCents(9_007_199_254_740_993L).toDisplayString())
    }
}
