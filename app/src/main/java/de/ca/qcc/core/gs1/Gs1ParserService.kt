package de.ca.qcc.core.gs1

import de.ca.qcc.domain.model.LocateTarget
import de.ca.qcc.domain.model.QrScanResult
import de.ca.qcc.domain.model.RfidScanResult
import java.math.BigInteger

class Gs1ParserService {

    private val gcpBits = intArrayOf(40, 37, 34, 30, 27, 24, 20)
    private val gcpDigits = intArrayOf(12, 11, 10, 9, 8, 7, 6)
    private val itemRefBits = intArrayOf(4, 7, 10, 14, 17, 20, 24)
    private val itemRefDigits = intArrayOf(1, 2, 3, 4, 5, 6, 7)

    // SGTIN-96 Header = 00110000 = 0x30
    private val sgtin96Header = "00110000"

    // Für eure Artikel aktuell fest angenommen:
    // Company Prefix = 7 Stellen
    private val qrCompanyPrefixDigits = 7
    private val sgtinSerialBits = 38

    fun parseQr(raw: String): QrScanResult {
        val normalized = raw.trim()
        val regex = Regex(""".*/01/(\d{14})/21/([^/]+).*""")
        val match = regex.find(normalized)
            ?: throw IllegalArgumentException("QR-Code enthält keine erwartete GTIN/Seriennummer-Struktur")

        val gtin = match.groupValues[1]
        val serial = match.groupValues[2]
        val sgtin = buildQrSgtin(gtin, serial)

        return QrScanResult(
            rawValue = normalized,
            gtin = gtin,
            serial = serial,
            sgtin = sgtin
        )
    }

    fun parseManualQrGtin(gtin: String): QrScanResult {
        val normalized = gtin.trim()
        require(normalized.matches(Regex("""\d{14}"""))) {
            "Manuelle GTIN muss genau 14-stellig sein"
        }

        return QrScanResult(
            rawValue = normalized,
            gtin = normalized,
            serial = null
        )
    }

    fun parseEpcToSgtin(
        epc: String,
        readerName: String? = null,
        battery: Int? = null
    ): RfidScanResult {
        val cleanHex = epc.trim().uppercase()

        require(cleanHex.isNotBlank()) {
            "EPC darf nicht leer sein"
        }

        require(cleanHex.matches(Regex("^[0-9A-F]+$"))) {
            "EPC enthält ungültige Zeichen"
        }

        require(cleanHex.length % 2 == 0) {
            "EPC-Hexwert muss eine gerade Anzahl Zeichen haben"
        }

        // SGTIN-96 = 96 Bit = 24 Hex-Zeichen
        require(cleanHex.length == 24) {
            "Nur SGTIN-96 mit 24 Hex-Zeichen wird aktuell unterstützt"
        }

        val binary = hexToBinary(cleanHex)

        val header = binary.substring(0, 8)
        require(header == sgtin96Header) {
            "Nicht unterstützter EPC-Header: $header"
        }

        val partition = binary.substring(11, 14).toInt(2)
        require(partition in 0..6) {
            "Ungültige SGTIN-Partition: $partition"
        }

        val gcp = binaryToGcp(binary, partition)
        val itemRef = binaryToItemRef(binary, partition)
        val serial = binaryToSerial(binary, partition)

        val sgtinPureIdentity = "$gcp.$itemRef.$serial"
        val gtin = pureIdentityToGtin(gcp, itemRef)

        require(gtin.isNotBlank() && gtin != "00000000000000") {
            "RFID EPC konnte nicht sinnvoll in GTIN umgewandelt werden"
        }

        require(serial.isNotBlank() && serial != "0000000000") {
            "RFID EPC enthält keine gültige Seriennummer"
        }

        return RfidScanResult(
            epc = cleanHex,
            sgtin = sgtinPureIdentity,
            gtin = gtin,
            readerName = readerName,
            readerBattery = battery
        )
    }

    fun buildQrSgtin(gtin: String, serial: String): String {
        val normalizedGtin = gtin.trim()
        val normalizedSerial = serial.trim()

        require(normalizedGtin.matches(Regex("""\d{14}"""))) {
            "QR-GTIN muss 14-stellig sein"
        }

        require(normalizedSerial.isNotBlank()) {
            "QR-Seriennummer fehlt"
        }

        val indicator = normalizedGtin.first()
        val withoutIndicatorAndCheck = normalizedGtin.substring(1, 13)

        val companyPrefix = withoutIndicatorAndCheck.substring(0, qrCompanyPrefixDigits)
        val itemReferenceWithoutIndicator = withoutIndicatorAndCheck.substring(qrCompanyPrefixDigits)
        val itemReference = "$indicator$itemReferenceWithoutIndicator"

        return "$companyPrefix.$itemReference.$normalizedSerial"
    }

    fun parseLocateTarget(
        input: String,
        sgtinFilter: Int = LocateTarget.DEFAULT_SGTIN_FILTER
    ): LocateTarget {
        val normalized = input.trim()
        return when {
            normalized.matches(Regex("^[0-9A-Fa-f]{24}$")) -> {
                // The EPC carries its own filter value; sgtinFilter does not apply
                val parsed = parseEpcToSgtin(normalized)
                LocateTarget(epc = parsed.epc, sgtin = parsed.sgtin)
            }
            normalized.matches(Regex("""^\d+\.\d+\.\d+$""")) -> {
                val (gcp, itemRef, serial) = normalized.split('.')
                val parsed = parseEpcToSgtin(encodeSgtin96(gcp, itemRef, serial, sgtinFilter))
                LocateTarget(epc = parsed.epc, sgtin = normalized)
            }
            else -> throw IllegalArgumentException("Eingabe ist weder EPC (24 Hex) noch SGTIN")
        }
    }

    fun encodeSgtin96(gcp: String, itemRef: String, serial: String, filter: Int): String {
        val partition = gcpDigits.indexOf(gcp.length)
        require(partition >= 0) { "Company Prefix muss 6-12 Stellen haben" }
        require(itemRef.length == itemRefDigits[partition]) {
            "Item Reference muss ${itemRefDigits[partition]} Stellen haben"
        }
        require(filter in 0..7) { "Filterwert muss 0-7 sein" }
        require(serial == "0" || !serial.startsWith("0")) {
            "SGTIN-96 Seriennummer darf keine führende Null haben"
        }
        val serialValue = serial.toLong()
        require(serialValue < (1L shl sgtinSerialBits)) { "Seriennummer zu groß für SGTIN-96" }

        val bits = buildString {
            append(sgtin96Header)
            append(filter.toString(2).padStart(3, '0'))
            append(partition.toString(2).padStart(3, '0'))
            append(gcp.toLong().toString(2).padStart(gcpBits[partition], '0'))
            append(itemRef.toLong().toString(2).padStart(itemRefBits[partition], '0'))
            append(serialValue.toString(2).padStart(sgtinSerialBits, '0'))
        }
        return BigInteger(bits, 2).toString(16).padStart(24, '0').uppercase()
    }

    private fun hexToBinary(hex: String): String {
        val bits = hex.length * 4
        val bin = BigInteger(hex, 16).toString(2)
        return bin.padStart(bits, '0')
    }

    private fun binaryToGcp(binary: String, partition: Int): String {
        val value = binary.substring(14, 14 + gcpBits[partition])
        return value.toLong(2).toString().padStart(gcpDigits[partition], '0')
    }

    private fun binaryToItemRef(binary: String, partition: Int): String {
        val start = 14 + gcpBits[partition]
        val value = binary.substring(start, start + itemRefBits[partition])
        return value.toLong(2).toString().padStart(itemRefDigits[partition], '0')
    }

    private fun binaryToSerial(binary: String, partition: Int): String {
        val start = 14 + gcpBits[partition] + itemRefBits[partition]
        return binary.substring(start).toLong(2).toString()
    }

    private fun pureIdentityToGtin(gcp: String, itemRef: String): String {
        val indicator = itemRef.first().toString()
        val itemRefWithoutIndicator = itemRef.drop(1)

        val gtin13 = indicator + gcp + itemRefWithoutIndicator
        val checkDigit = calculateCheckDigit(gtin13)

        return gtin13 + checkDigit
    }

    private fun calculateCheckDigit(numberWithoutCheckDigit: String): Int {
        var sum = 0

        for (i in numberWithoutCheckDigit.indices) {
            val digit = numberWithoutCheckDigit[numberWithoutCheckDigit.length - 1 - i].digitToInt()
            sum += if (i % 2 == 0) digit * 3 else digit
        }

        return (10 - (sum % 10)) % 10
    }
}