package de.ca.qcc.feature.duplicates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
