package de.ca.qcc.feature.duplicates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeepIntervalTest {

    @Test
    fun noSignal_isSilent() {
        assertNull(beepIntervalMs(0))
        assertNull(beepIntervalMs(-5))
    }

    @Test
    fun interval_shrinksLinearlyFrom1000To80() {
        assertEquals(1000L, beepIntervalMs(1))
        assertEquals(545L, beepIntervalMs(50))
        assertEquals(80L, beepIntervalMs(100))
    }

    @Test
    fun valuesAbove100_areClamped() {
        assertEquals(80L, beepIntervalMs(150))
    }

    @Test
    fun interval_isNonIncreasingOver1To100() {
        var previous = beepIntervalMs(1)!!
        for (p in 2..100) {
            val current = beepIntervalMs(p)!!
            assertTrue("p=$p: $current > $previous", current <= previous)
            previous = current
        }
    }

    @Test
    fun interval_staysBetween80And1000() {
        for (p in 1..100) {
            val v = beepIntervalMs(p)!!
            assertTrue("p=$p: $v", v in 80L..1000L)
        }
    }
}
