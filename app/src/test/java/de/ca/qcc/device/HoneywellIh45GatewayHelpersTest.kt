package de.ca.qcc.device

import de.ca.qcc.device.honeywell.HoneywellIh45Gateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoneywellIh45GatewayHelpersTest {
    private val full = "FFFFFFFFFFFFFFFFFFFFFFFF"
    private val epc = "3034F839BC1B974300000406"

    @Test
    fun fullMaskMatchesOnlyTheExactEpc() {
        assertTrue(HoneywellIh45Gateway.matches(epc, epc, full))
        assertFalse(HoneywellIh45Gateway.matches("3034F839BC1B974300000407", epc, full))
    }

    @Test
    fun maskIgnoresZeroBits() {
        val serialIgnored = "FFFFFFFFFFFFFFFF00000000"
        assertTrue(HoneywellIh45Gateway.matches("3034F839BC1B974300000407", epc, serialIgnored))
    }

    @Test
    fun malformedInputNeverMatches() {
        assertFalse(HoneywellIh45Gateway.matches("", epc, full))
        assertFalse(HoneywellIh45Gateway.matches(epc, "xyz", full))
    }

    @Test
    fun rssiMapsToProximityRange() {
        assertEquals(0, HoneywellIh45Gateway.rssiToProximity(-90))
        assertEquals(0, HoneywellIh45Gateway.rssiToProximity(-46))
        assertEquals(100, HoneywellIh45Gateway.rssiToProximity(-27))
        assertEquals(100, HoneywellIh45Gateway.rssiToProximity(-10))
    }

    @Test
    fun onlyContactReachesTheConfirmThreshold() {
        // touching the reader: -23..-27; about 30 cm away: -30
        assertTrue(HoneywellIh45Gateway.rssiToProximity(-24) >= 95)
        assertTrue(HoneywellIh45Gateway.rssiToProximity(-28) >= 95)
        assertTrue(HoneywellIh45Gateway.rssiToProximity(-30) < 90)
        assertTrue(HoneywellIh45Gateway.rssiToProximity(-38) < 50)
    }

    @Test
    fun closestTagWinsAndWeakNeighboursAreIgnored() {
        val hits = listOf("held" to -30, "neighbour" to -34, "bin" to -62)
        val picked = HoneywellIh45Gateway.selectClosest(hits, minRssi = -45, marginDb = 8) { it.second }
        assertEquals(listOf("held", "neighbour"), picked.map { it.first })
    }

    @Test
    fun nothingIsPickedWhenEvenTheStrongestIsTooWeak() {
        val hits = listOf("far" to -58, "farther" to -66)
        assertTrue(HoneywellIh45Gateway.selectClosest(hits, minRssi = -45, marginDb = 8) { it.second }.isEmpty())
        assertTrue(HoneywellIh45Gateway.selectClosest(emptyList<Pair<String, Int>>(), -45, 8) { it.second }.isEmpty())
    }

    @Test
    fun nearFieldIsDecidedByThePowerLevel() {
        // touching: still read at the lowest power; close: still read at the reduced power
        assertEquals(100, HoneywellIh45Gateway.proximityFor(HoneywellIh45Gateway.LEVEL_LOWEST, -40))
        assertEquals(90, HoneywellIh45Gateway.proximityFor(HoneywellIh45Gateway.LEVEL_REDUCED, -40))
        // at full power even a perfect RSSI stays below the confirm threshold
        assertTrue(HoneywellIh45Gateway.proximityFor(HoneywellIh45Gateway.LEVEL_FULL, -20) < 95)
        assertEquals(0, HoneywellIh45Gateway.proximityFor(HoneywellIh45Gateway.LEVEL_FULL, -60))
    }
}
