package de.ca.qcc.device.zebra

import de.ca.qcc.domain.model.ReaderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

enum class TriggerMode { QCC, CAPTURE, LOCATE }

interface RfidReaderGateway {
    fun observeStatus(): Flow<ReaderStatus>
    fun observeTagScans(): Flow<String>
    fun observeTriggerEvents(): Flow<Unit> = emptyFlow() // Default empty implementation
    suspend fun discoverReaders(): List<String>
    suspend fun connect(readerId: String): Result<Unit>
    suspend fun disconnect()
    suspend fun triggerInventory()
    suspend fun setPower(level: Int): Result<Unit>

    // ── Duplicate search (defaults keep test fakes compiling) ──

    // QCC = trigger runs the QCC single-tag scan; other modes only report presses
    fun setTriggerMode(mode: TriggerMode) = Unit

    // true = pressed, false = released; emitted only while mode != QCC
    fun observeHandheldTrigger(): Flow<Boolean> = emptyFlow()

    // Relative distance 0..100 while locating
    fun observeProximity(): Flow<Int> = emptyFlow()

    suspend fun startLocate(epc: String, mask: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Locate not supported"))

    suspend fun stopLocate() = Unit

    // Distinct EPCs read at minimum power
    suspend fun readEpcsNearby(): Result<List<String>> =
        Result.failure(UnsupportedOperationException("Nearby read not supported"))

    // Distinct TIDs of tags matching epc/mask, read at minimum power
    suspend fun readTidsNearby(epc: String, mask: String): Result<List<String>> =
        Result.failure(UnsupportedOperationException("TID read not supported"))
}
