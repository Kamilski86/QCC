package de.ca.qcc.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReaderStatus(
    val isConnected: Boolean = false,
    val readerName: String? = null,
    val batteryPercent: Int? = null,
    val message: String = "Nicht verbunden"
)

@Serializable
data class QrScanResult(
    val rawValue: String,
    val gtin: String,
    val serial: String?,
    val sgtin: String? = null
)

@Serializable
data class RfidScanResult(
    val epc: String,
    val sgtin: String,
    val gtin: String,
    val scannedAt: Long = System.currentTimeMillis(),
    val readerName: String? = null,
    val readerBattery: Int? = null
)

@Serializable
enum class ComparisonStatus { IDLE, MATCH, MISMATCH, ERROR }

@Serializable
data class ComparisonResult(
    val status: ComparisonStatus,
    val gtinQr: String? = null,
    val gtinTag: String? = null,
    val message: String,
    val comparedAt: Long = System.currentTimeMillis()
)

@Serializable
data class MismatchRecord(
    val id: Long = 0,
    val timestamp: Long,
    val epc: String,
    val sgtin: String,
    val gtinTag: String,
    val gtinQr: String
)

@Serializable
data class LocateTarget(
    val epc: String,
    val sgtin: String
) {
    companion object {
        val FULL_MASK: String = "F".repeat(24)

        // GS1 filter value "point of sale trade item"
        const val DEFAULT_SGTIN_FILTER = 1
    }
}

data class DuplicateSearch(
    val id: Long,
    val target: LocateTarget,
    val store: String?,
    val foundCount: Int
)

sealed interface RegisterFindResult {
    data class Saved(val findNo: Int) : RegisterFindResult
    data class AlreadyFound(val findNo: Int) : RegisterFindResult
}
