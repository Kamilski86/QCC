package de.ca.qcc.device.zebra

import de.ca.qcc.domain.model.ReaderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

enum class TriggerMode { QCC, CAPTURE, LOCATE }

// Only the latest claimant may hand the trigger back to QCC, so a screen that is torn down late
// (navigation animation, cleared view model) cannot reset the mode of the screen that replaced it
class TriggerModeArbiter {
    private var owner: Any? = null

    @Synchronized
    fun claim(owner: Any, mode: TriggerMode): TriggerMode {
        this.owner = owner
        return mode
    }

    // Returns QCC if `owner` still holds the trigger, null if it was already taken over
    @Synchronized
    fun release(owner: Any): TriggerMode? {
        if (this.owner !== owner) return null
        this.owner = null
        return TriggerMode.QCC
    }
}

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

    // Non-QCC screens claim the trigger under their own identity and release it when leaving
    fun claimTriggerMode(owner: Any, mode: TriggerMode) = setTriggerMode(mode)
    fun releaseTriggerMode(owner: Any) = setTriggerMode(TriggerMode.QCC)

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
