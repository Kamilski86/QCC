package de.ca.qcc.core.gs1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gs1ParserServiceTest2 {

    private val parser = Gs1ParserService()

    @Test
    fun parseQr_extractsGtinAndSerial() {
        val result = parser.parseQr("http://qr.c-a.com/01/04065546927682/21/12884901894")

        assertEquals("04065546927682", result.gtin)
        assertEquals("12884901894", result.serial)
    }

    @Test
    fun parseManualQrGtin_accepts14DigitGtin() {
        val result = parser.parseManualQrGtin("04065546927682")

        assertEquals("04065546927682", result.gtin)
        assertEquals("04065546927682", result.rawValue)
        assertNull(result.serial)
    }

    @Test
    fun parseEpcToSgtin_convertsKnownExampleCorrectly() {
        val result = parser.parseEpcToSgtin("3014F824285A980300000006")

        assertEquals("3014F824285A980300000006", result.epc)
        assertEquals("4065546.092768.12884901894", result.sgtin)
        assertEquals("04065546927682", result.gtin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseEpcToSgtin_rejectsInvalidHexCharacters() {
        parser.parseEpcToSgtin("3014F824285A98030000000Z")
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseEpcToSgtin_rejectsWrongLength() {
        parser.parseEpcToSgtin("3014F824285A9803")
    }
}