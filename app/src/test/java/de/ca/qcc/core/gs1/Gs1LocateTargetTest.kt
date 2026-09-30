package de.ca.qcc.core.gs1

import de.ca.qcc.domain.model.LocateTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class Gs1LocateTargetTest {

    private val parser = Gs1ParserService()

    @Test
    fun epcInput_isNormalized_andSearchedExactly() {
        val target = parser.parseLocateTarget(" 3014f824285a980300000006 ")

        assertEquals("3014F824285A980300000006", target.epc)
        assertEquals("4065546.092768.12884901894", target.sgtin)
        assertEquals(LocateTarget.FULL_MASK, target.mask)
        assertFalse(target.filterMasked)
    }

    @Test
    fun sgtinInput_encodesEpcWithFilterZero_andIgnoresFilterBits() {
        val target = parser.parseLocateTarget("4065546.092768.12884901894")

        assertEquals("3014F824285A980300000006", target.epc)
        assertEquals("4065546.092768.12884901894", target.sgtin)
        assertEquals(LocateTarget.FILTER_IGNORING_MASK, target.mask)
        assertTrue(target.filterMasked)
    }

    @Test
    fun masks_haveExpectedBehavior() {
        assertEquals("F".repeat(24), LocateTarget.FULL_MASK)
        val maskBig = BigInteger(LocateTarget.FILTER_IGNORING_MASK, 16)
        fun enc(f: Int) = BigInteger(parser.encodeSgtin96("4065546", "092768", "12884901894", f), 16)
        val base = enc(0).and(maskBig)
        for (f in 0..7) assertEquals(base, enc(f).and(maskBig))
        assertNotEquals(base, enc(0).flipBit(95 - 11).and(maskBig))
        assertNotEquals(base, enc(0).flipBit(95 - 7).and(maskBig))
    }

    @Test
    fun serialBoundaries_encode() {
        val max = parser.encodeSgtin96("4065546", "092768", "274877906943", filter = 0)
        assertEquals("4065546.092768.274877906943", parser.parseEpcToSgtin(max).sgtin)
        val zero = parser.encodeSgtin96("4065546", "092768", "0", filter = 0)
        assertEquals(24, zero.length)
        assertEquals(BigInteger.ZERO, BigInteger(zero, 16).and(BigInteger.ONE.shiftLeft(38).subtract(BigInteger.ONE)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun allZeroGtin_isRejected() {
        parser.parseLocateTarget("0000000.000000.1")
    }

    @Test
    fun encodeSgtin96_roundTripsForAllPartitions() {
        val cases = listOf(
            "123456789012" to "0",
            "12345678901" to "12",
            "1234567890" to "123",
            "123456789" to "1234",
            "12345678" to "12345",
            "4065546" to "092768",
            "123456" to "1234567"
        )
        cases.forEach { (gcp, itemRef) ->
            val epc = parser.encodeSgtin96(gcp, itemRef, "987654", filter = 3)
            assertEquals("$gcp.$itemRef.987654", parser.parseEpcToSgtin(epc).sgtin)
            val filterBits = BigInteger(epc, 16).shiftRight(85).and(BigInteger.valueOf(7)).toInt()
            assertEquals(3, filterBits)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun urnNotation_isRejected() {
        parser.parseLocateTarget("urn:epc:id:sgtin:4065546.092768.12884901894")
    }

    @Test(expected = IllegalArgumentException::class)
    fun garbage_isRejected() {
        parser.parseLocateTarget("ABC")
    }

    @Test(expected = IllegalArgumentException::class)
    fun companyPrefixTooShort_isRejected() {
        parser.parseLocateTarget("12345.1234567.1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun itemReferenceWithWrongLength_isRejected() {
        parser.parseLocateTarget("4065546.92768.1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun serialWithLeadingZero_isRejected() {
        parser.parseLocateTarget("4065546.092768.012")
    }

    @Test(expected = IllegalArgumentException::class)
    fun serialTooLarge_isRejected() {
        parser.parseLocateTarget("4065546.092768.274877906944")
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonSgtinEpc_isRejected() {
        parser.parseLocateTarget("E28068940000500000000001")
    }
}
