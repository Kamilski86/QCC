package de.ca.qcc.testutil

import android.content.Context
import androidx.room.Room
import de.ca.qcc.data.export.CsvDocumentWriter
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.device.zebra.TriggerMode
import de.ca.qcc.device.zebra.TriggerModeArbiter
import de.ca.qcc.domain.model.ReaderStatus
import de.ca.qcc.feature.duplicates.ProximityFeedback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

fun inMemoryDb(context: Context): AppDatabase =
    Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()

class FakeCsvWriter : CsvDocumentWriter {
    val files = mutableMapOf<String, String>()
    var failNext = false
    override suspend fun write(fileName: String, content: String) {
        if (failNext) {
            failNext = false
            throw java.io.IOException("write failed")
        }
        files[fileName] = content
    }
}

class FakeDuplicateReader : RfidReaderGateway {
    val status = MutableStateFlow(ReaderStatus(isConnected = true))
    val proximity = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val trigger = MutableSharedFlow<Boolean>(extraBufferCapacity = 16)
    var mode = TriggerMode.QCC
    var epcsNearby: Result<List<String>> = Result.success(emptyList())
    var tidsNearby: Result<List<String>> = Result.success(emptyList())
    val locateCalls = mutableListOf<Pair<String, String>>()
    var locating = false
    var startLocateResult: Result<Unit> = Result.success(Unit)
    var startGate: CompletableDeferred<Unit>? = null
    var tidsGate: CompletableDeferred<Unit>? = null

    override fun observeStatus(): Flow<ReaderStatus> = status
    override fun observeTagScans(): Flow<String> = emptyFlow()
    override suspend fun discoverReaders(): List<String> = emptyList()
    override suspend fun connect(readerId: String): Result<Unit> = Result.success(Unit)
    override suspend fun disconnect() = Unit
    override suspend fun triggerInventory() = Unit
    override suspend fun setPower(level: Int): Result<Unit> = Result.success(Unit)
    private val arbiter = TriggerModeArbiter()
    override fun setTriggerMode(mode: TriggerMode) { this.mode = mode }
    override fun claimTriggerMode(owner: Any, mode: TriggerMode) { this.mode = arbiter.claim(owner, mode) }
    override fun releaseTriggerMode(owner: Any) { arbiter.release(owner)?.let { this.mode = it } }
    override fun observeHandheldTrigger(): Flow<Boolean> = trigger
    override fun observeProximity(): Flow<Int> = proximity
    override suspend fun startLocate(epc: String, mask: String): Result<Unit> {
        locateCalls += epc to mask
        startGate?.await()
        if (startLocateResult.isSuccess) locating = true
        return startLocateResult
    }
    override suspend fun stopLocate() { locating = false }
    override suspend fun readEpcsNearby(): Result<List<String>> = epcsNearby
    override suspend fun readTidsNearby(epc: String, mask: String): Result<List<String>> {
        tidsGate?.await()
        return tidsNearby
    }
}

class FakeProximityFeedback : ProximityFeedback {
    val updates = mutableListOf<Int>()
    var stopCount = 0
    override fun update(proximity: Int) { updates += proximity }
    override fun stop() { stopCount++ }
    override fun release() = Unit
}
