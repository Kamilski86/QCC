package de.ca.qcc.device.zebra

import android.content.Context
import com.zebra.rfid.api3.ACCESS_OPERATION_CODE
import com.zebra.rfid.api3.ACCESS_OPERATION_STATUS
import com.zebra.rfid.api3.AccessFilter
import com.zebra.rfid.api3.FILTER_MATCH_PATTERN
import com.zebra.rfid.api3.MEMORY_BANK
import com.zebra.rfid.api3.TagData
import com.zebra.rfid.api3.TagPatternBase
import com.zebra.rfid.api3.DYNAMIC_POWER_OPTIMIZATION
import com.zebra.rfid.api3.ENUM_TRANSPORT
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE
import com.zebra.rfid.api3.InvalidUsageException
import com.zebra.rfid.api3.OperationFailureException
import com.zebra.rfid.api3.RFIDReader
import com.zebra.rfid.api3.ReaderDevice
import com.zebra.rfid.api3.Readers
import com.zebra.rfid.api3.RfidEventsListener
import com.zebra.rfid.api3.RfidReadEvents
import com.zebra.rfid.api3.RfidStatusEvents
import com.zebra.rfid.api3.SESSION
import com.zebra.rfid.api3.INVENTORY_STATE
import com.zebra.rfid.api3.SL_FLAG
import com.zebra.rfid.api3.START_TRIGGER_TYPE
import com.zebra.rfid.api3.STATUS_EVENT_TYPE
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE
import com.zebra.rfid.api3.TriggerInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ca.qcc.domain.model.ReaderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class ZebraRfd8500Gateway @Inject constructor(
    @ApplicationContext private val context: Context
) : RfidReaderGateway {

    private val statusFlow = MutableStateFlow(ReaderStatus())

    // Emits exactly one EPC per inventory session (hardware trigger or button)
    private val tagFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // NEW: Emit trigger press events for unified control
    private val triggerFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val readers: Readers by lazy { Readers(context, ENUM_TRANSPORT.BLUETOOTH) }
    private var availableReaders: List<ReaderDevice> = emptyList()
    @Volatile private var reader: RFIDReader? = null
    private var connectedReaderName: String? = null
    private var eventHandler: EventHandler? = null

    // Index into reader's transmit power level table (0 = hardware minimum)
    @Volatile private var currentPowerIndex: Int = 0
    private var maxPowerIndex: Int = 200

    // Prevents emitting more than one tag per inventory session; thread-safe
    private val inventoryActive = AtomicBoolean(false)

    // ── Duplicate search state ──
    @Volatile private var triggerMode = TriggerMode.QCC

    private val handheldTriggerFlow = MutableSharedFlow<Boolean>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val proximityFlow = MutableSharedFlow<Int>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val locateActive = AtomicBoolean(false)
    private val nearbyActive = AtomicBoolean(false)

    // Set while readEpcsNearby()/readTidsNearby() collect; receives every read TagData
    @Volatile private var nearbySink: ((TagData) -> Unit)? = null

    override fun observeStatus(): Flow<ReaderStatus> = statusFlow.asStateFlow()
    override fun observeTagScans(): Flow<String> = tagFlow.asSharedFlow()

    // NEW: Method to observe trigger presses for unified control
    override fun observeTriggerEvents(): Flow<Unit> = triggerFlow.asSharedFlow()

    override fun setTriggerMode(mode: TriggerMode) {
        triggerMode = mode
    }

    override fun observeHandheldTrigger(): Flow<Boolean> = handheldTriggerFlow.asSharedFlow()

    override fun observeProximity(): Flow<Int> = proximityFlow.asSharedFlow()

    // ── Event handler ────────────────────────────────────────────────────────

    private inner class EventHandler : RfidEventsListener {

        // Called on the SDK thread when a tag is decoded
        override fun eventReadNotify(e: RfidReadEvents) {
            nearbySink?.let { sink ->
                reader?.Actions?.getReadTags(100)?.forEach(sink)
                return
            }

            if (locateActive.get()) {
                reader?.Actions?.getReadTags(100)
                    ?.filter { it.isContainsLocationInfo }
                    ?.forEach { proximityFlow.tryEmit(it.LocationInfo.relativeDistance.toInt()) }
                return
            }

            // QCC: only handle the first tag; compareAndSet ensures exactly-once delivery
            if (!inventoryActive.compareAndSet(true, false)) return

            // Stop immediately — we only want exactly 1 tag
            runCatching { reader?.Actions?.Inventory?.stop() }

            val tags = reader?.Actions?.getReadTags(100) ?: return
            val epc = tags.firstOrNull { !it.tagID.isNullOrBlank() }?.tagID ?: return
            tagFlow.tryEmit(epc)
        }

        // Called on the SDK thread for trigger press/release and disconnect
        override fun eventStatusNotify(e: RfidStatusEvents) {
            when (e.StatusEventData.getStatusEventType()) {
                STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT -> {
                    when (e.StatusEventData.HandheldTriggerEventData.getHandheldEvent()) {
                        HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED -> {
                            if (triggerMode == TriggerMode.QCC) {
                                startInventoryInternal()
                                triggerFlow.tryEmit(Unit) // Emit trigger press event
                            } else {
                                handheldTriggerFlow.tryEmit(true)
                            }
                        }
                        HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED -> {
                            if (triggerMode == TriggerMode.QCC) {
                                // If trigger released before a tag was read, stop inventory
                                if (inventoryActive.compareAndSet(true, false)) {
                                    runCatching { reader?.Actions?.Inventory?.stop() }
                                }
                            } else {
                                handheldTriggerFlow.tryEmit(false)
                            }
                        }
                        else -> Unit
                    }
                }
                STATUS_EVENT_TYPE.DISCONNECTION_EVENT -> {
                    inventoryActive.set(false)
                    locateActive.set(false)
                    nearbySink = null
                    proximityFlow.tryEmit(0)
                    runCatching { reader?.Events?.removeEventsListener(eventHandler) }
                    reader = null
                    connectedReaderName = null
                    updateReaderStatus("Nicht verbunden")
                }
                else -> Unit
            }
        }
    }

    // ── Inventory control ────────────────────────────────────────────────────

    private fun startInventoryInternal() {
        if (!inventoryActive.compareAndSet(false, true)) return
        runCatching { reader?.Actions?.Inventory?.perform() }
            .onFailure { inventoryActive.set(false) }
    }

    // Called by the UI button (non-blocking — result arrives via observeTagScans())
    override suspend fun triggerInventory() {
        val currentReader = reader
            ?: throw IllegalStateException("Reader nicht verbunden")
        if (!currentReader.isConnected)
            throw IllegalStateException("Reader nicht verbunden")
        startInventoryInternal()
    }

    // ── Duplicate search ─────────────────────────────────────────────────────

    override suspend fun startLocate(epc: String, mask: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = requireConnectedReader()
                check(!nearbyActive.get() && !inventoryActive.get()) { "Reader beschäftigt" }
                if (!locateActive.compareAndSet(false, true)) return@runCatching
                try {
                    r.Actions.TagLocationing.Perform(epc, mask, null)
                } catch (t: Throwable) {
                    locateActive.set(false)
                    throw t
                }
            }.rethrowCancellation().readableFailure()
        }

    override suspend fun stopLocate() {
        withContext(Dispatchers.IO) {
            if (locateActive.compareAndSet(true, false)) {
                runCatching { reader?.Actions?.TagLocationing?.Stop() }
                proximityFlow.tryEmit(0)
            }
        }
    }

    override suspend fun readEpcsNearby(): Result<List<String>> = readNearby(
        start = { it.Actions.Inventory.perform() },
        stop = { it.Actions.Inventory.stop() },
        extract = { tag -> tag.tagID?.takeIf { it.isNotBlank() } }
    )

    override suspend fun readTidsNearby(epc: String, mask: String): Result<List<String>> = readNearby(
        start = { r ->
            val params = r.Actions.TagAccess.ReadAccessParams().apply {
                memoryBank = MEMORY_BANK.MEMORY_BANK_TID
                offset = 0
                count = 6 // 96-bit TID; count = 0 padded the result with zeros on the RFD8500
            }
            r.Actions.TagAccess.readEvent(params, epcFilter(epc, mask), null)
        },
        stop = { it.Actions.TagAccess.stopAccess() },
        extract = { tag ->
            tag.memoryBankData?.takeIf {
                tag.opCode == ACCESS_OPERATION_CODE.ACCESS_OPERATION_READ &&
                    tag.opStatus == ACCESS_OPERATION_STATUS.ACCESS_SUCCESS &&
                    it.isNotBlank()
            }
        }
    )

    // Reads at low power (see NEARBY_POWER_PERCENT) for a short window so only tags directly at the reader answer
    private suspend fun readNearby(
        start: (RFIDReader) -> Unit,
        stop: (RFIDReader) -> Unit,
        extract: (TagData) -> String?
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            // Only the call that set the flag may reset it
            check(nearbyActive.compareAndSet(false, true)) { "Reader beschäftigt" }
            try {
                val r = requireConnectedReader()
                check(!locateActive.get() && !inventoryActive.get()) { "Reader beschäftigt" }
                val found = LinkedHashSet<String>()
                var powerRestoreError: Throwable? = null
                nearbySink = { tag -> extract(tag)?.let { synchronized(found) { found.add(it.uppercase()) } } }
                try {
                    applyAntennaConfig(r, nearbyPowerIndex())
                    start(r)
                    delay(NEARBY_READ_WINDOW_MS)
                    check(reader === r && r.isConnected) { "Reader nicht verbunden" }
                } finally {
                    // In finally so a cancelled read never leaves the reader running
                    runCatching { stop(r) }
                    nearbySink = null
                    powerRestoreError = restorePower(r)
                }
                // Reached only if the read itself succeeded, so an earlier error is never masked
                powerRestoreError?.let {
                    throw IllegalStateException("Leistung konnte nicht wiederhergestellt werden", it)
                }
                synchronized(found) { found.toList() }
            } finally {
                nearbyActive.set(false)
            }
        }.rethrowCancellation().readableFailure()
    }

    // Restores the user-selected power (one retry); returns the error if both attempts fail
    private fun restorePower(r: RFIDReader): Throwable? {
        // A disconnected reader gets its power re-applied by configureReader() on reconnect
        if (!runCatching { r.isConnected }.getOrDefault(false)) return null
        val error = runCatching { applyAntennaConfig(r, currentPowerIndex) }
            .recoverCatching { applyAntennaConfig(r, currentPowerIndex) }
            .exceptionOrNull() ?: return null
        updateReaderStatus("Power-Fehler: ${error.message ?: error.javaClass.simpleName}")
        return error
    }

    private fun <T> Result<T>.rethrowCancellation(): Result<T> =
        onFailure { if (it is CancellationException) throw it }

    private fun epcFilter(epc: String, mask: String) = AccessFilter().apply {
        TagPatternA = TagPatternBase().apply {
            memoryBank = MEMORY_BANK.MEMORY_BANK_EPC
            bitOffset = 32 // skip CRC + PC words
            setTagPattern(epc)
            tagPatternBitCount = 96
            setTagMask(mask)
            tagMaskBitCount = 96
        }
        accessFilterMatchPattern = FILTER_MATCH_PATTERN.A
    }

    private fun requireConnectedReader(): RFIDReader =
        reader?.takeIf { it.isConnected } ?: throw IllegalStateException("Reader nicht verbunden")

    private fun <T> Result<T>.readableFailure(): Result<T> {
        val e = exceptionOrNull() as? OperationFailureException ?: return this
        return Result.failure(IllegalStateException("result=${e.results}, vendor=${e.vendorMessage}"))
    }

    private fun nearbyPowerIndex() =
        (maxPowerIndex * NEARBY_POWER_PERCENT / 100)
            .coerceAtLeast(MIN_NEARBY_POWER_INDEX)
            .coerceAtMost(maxPowerIndex)

    private companion object {
        const val NEARBY_READ_WINDOW_MS = 700L

        // Hardware test: index 0 is unreliable; 5 % reads reliably at contact and nothing at ~30 cm
        const val NEARBY_POWER_PERCENT = 5

        // Percent is of the power index range (dBm table); index 15 of 300 was verified on the
        // RFD8500. The floor guards the default maxPowerIndex = 200 fallback when capabilities
        // could not be read.
        const val MIN_NEARBY_POWER_INDEX = 15
    }

    // ── Connection ───────────────────────────────────────────────────────────

    override suspend fun discoverReaders(): List<String> = withContext(Dispatchers.IO) {
        try {
            val list = readers.GetAvailableRFIDReaderList() ?: emptyList()
            availableReaders = list
            val names = list.mapNotNull { runCatching { it.name }.getOrNull() }
            updateReaderStatus(if (names.isEmpty()) "Kein Zebra-Reader gefunden" else "Reader gefunden")
            names
        } catch (e: SecurityException) {
            updateReaderStatus("Bluetooth-Berechtigung fehlt: ${e.message}")
            emptyList()
        } catch (t: Throwable) {
            updateReaderStatus("Reader-Suche fehlgeschlagen: ${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }
    }

    override suspend fun connect(readerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (availableReaders.isEmpty())
                availableReaders = readers.GetAvailableRFIDReaderList() ?: emptyList()

            val target = availableReaders.firstOrNull {
                runCatching { it.name == readerId }.getOrDefault(false)
            } ?: throw IllegalStateException("Reader '$readerId' nicht gefunden")

            val selectedReader = target.rfidReader
                ?: throw IllegalStateException("RFIDReader-Objekt nicht verfügbar")

            if (!selectedReader.isConnected) {
                try {
                    selectedReader.connect()
                } catch (e: OperationFailureException) {
                    throw IllegalStateException(
                        "Zebra Connect fehlgeschlagen: result=${e.results}, vendor=${e.vendorMessage}"
                    )
                }
            }

            reader = selectedReader
            connectedReaderName = readerId

            runCatching { selectedReader.PostConnectReaderUpdate() }

            configureReader(selectedReader)
            updateReaderStatus("Verbunden")
        }.onFailure { e ->
            reader = null
            connectedReaderName = null
            updateReaderStatus("Verbindung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                reader?.let {
                    runCatching { it.Actions.Inventory.stop() }
                    if (locateActive.get()) runCatching { it.Actions.TagLocationing.Stop() }
                    eventHandler?.let { h -> runCatching { it.Events.removeEventsListener(h) } }
                    if (it.isConnected) runCatching { it.disconnect() }
                }
            } finally {
                inventoryActive.set(false)
                locateActive.set(false)
                nearbySink = null
                reader = null
                connectedReaderName = null
                updateReaderStatus("Nicht verbunden")
            }
        }
    }

    // ── Configuration ────────────────────────────────────────────────────────

    private fun configureReader(rfidReader: RFIDReader) {
        try {
            // Query actual power table so indices stay within valid range
            try {
                val levels = rfidReader.ReaderCapabilities.getTransmitPowerLevelValues()
                if (levels != null && levels.isNotEmpty()) {
                    maxPowerIndex = levels.size - 1
                    currentPowerIndex = 0
                }
            } catch (_: Exception) {}

            // Register event listener
            if (eventHandler == null) eventHandler = EventHandler()
            runCatching { rfidReader.Events.addEventsListener(eventHandler) }

            // Enable handheld trigger events
            runCatching { rfidReader.Events.setHandheldEvent(true) }
            // Enable tag read events (data delivered via eventReadNotify)
            runCatching { rfidReader.Events.setTagReadEvent(true) }
            runCatching { rfidReader.Events.setAttachTagDataWithReadEvent(false) }

            // Route physical trigger to RFID mode (not barcode scanner beam)
            runCatching { rfidReader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true) }

            // Trigger config: start and stop immediately on command
            runCatching {
                val triggerInfo = TriggerInfo()
                triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE)
                triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE)
                rfidReader.Config.setStartTrigger(triggerInfo.StartTrigger)
                rfidReader.Config.setStopTrigger(triggerInfo.StopTrigger)
            }

            // DPO off for RFD8500
            if ((connectedReaderName ?: "").contains("RFD8500", ignoreCase = true)) {
                runCatching { rfidReader.Config.setDPOState(DYNAMIC_POWER_OPTIMIZATION.DISABLE) }
            }

            rfidReader.Events.setInventoryStartEvent(false)
            rfidReader.Events.setInventoryStopEvent(false)

            // Antenna power
            applyAntennaConfig(rfidReader, currentPowerIndex)

            // Singulation: single session, read all tags in state A
            val singulation = rfidReader.Config.Antennas.getSingulationControl(1)
            singulation.setSession(SESSION.SESSION_S0)
            singulation.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A)
            singulation.Action.setSLFlag(SL_FLAG.SL_ALL)
            rfidReader.Config.Antennas.setSingulationControl(1, singulation)

            runCatching { rfidReader.Config.setAccessOperationWaitTimeout(1000) }
        } catch (e: InvalidUsageException) {
            updateReaderStatus("Konfigurationsfehler: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: OperationFailureException) {
            updateReaderStatus("Konfigurationsfehler: result=${e.results}, vendor=${e.vendorMessage}")
        } catch (t: Throwable) {
            updateReaderStatus("Konfigurationsfehler: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    // ── Power ────────────────────────────────────────────────────────────────

    override suspend fun setPower(level: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val currentReader = reader
            ?: return@withContext Result.failure(IllegalStateException("Reader nicht verbunden"))

        runCatching {
            val mappedIndex = mapUiPowerToZebraIndex(level)
            currentPowerIndex = mappedIndex
            applyAntennaConfig(currentReader, mappedIndex)
            updateReaderStatus("Verbunden")
        }.onFailure { e ->
            updateReaderStatus("Power-Fehler: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun applyAntennaConfig(rfidReader: RFIDReader, powerIndex: Int) {
        val config = rfidReader.Config.Antennas.getAntennaRfConfig(1)
        config.setTransmitPowerIndex(powerIndex)
        config.setrfModeTableIndex(0)
        config.setTari(0)
        rfidReader.Config.Antennas.setAntennaRfConfig(1, config)
    }

    private fun mapUiPowerToZebraIndex(level: Int): Int {
        val clamped = level.coerceIn(1, 30)
        return (clamped - 1) * maxPowerIndex / 29
    }

    // ── Status ───────────────────────────────────────────────────────────────

    private fun updateReaderStatus(message: String) {
        val name = runCatching {
            connectedReaderName ?: reader?.hostName ?: "RFD8500"
        }.getOrDefault(connectedReaderName ?: "RFD8500")

        statusFlow.value = ReaderStatus(
            isConnected = reader?.isConnected == true,
            readerName = name,
            batteryPercent = null,
            message = message
        )
    }
}
