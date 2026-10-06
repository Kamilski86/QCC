package de.ca.qcc.device.zebra

import de.ca.qcc.device.honeywell.HoneywellIh45Gateway
import de.ca.qcc.domain.model.ReaderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app talks to exactly one RFID reader at a time: the Honeywell IH45 while it is plugged in
 * (USB has priority), otherwise the Zebra RFD8500 over Bluetooth. Switching disconnects the other
 * reader first. All flows follow the active reader, so the view models never notice the switch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SwitchingRfidReaderGateway internal constructor(
    private val zebra: RfidReaderGateway,
    private val ih45: RfidReaderGateway,
    ih45UsbPresent: StateFlow<Boolean>,
    scope: CoroutineScope
) : RfidReaderGateway {

    @Inject
    constructor(zebra: ZebraRfd8500Gateway, ih45: HoneywellIh45Gateway) : this(
        zebra, ih45, ih45.usbPresent, CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )

    private val active = MutableStateFlow(zebra)
    private val ih45Ids = HashSet<String>()
    private val selectionLock = Mutex()
    @Volatile private var lastPower: Int? = null

    init {
        // Plugging the IH45 in or out changes the reader (the first value is the start state)
        scope.launch { ih45UsbPresent.drop(1).collect { autoSelect() } }
    }

    override fun observeStatus(): Flow<ReaderStatus> = active.flatMapLatest { it.observeStatus() }
    override fun observeTagScans(): Flow<String> = active.flatMapLatest { it.observeTagScans() }
    override fun observeTriggerEvents(): Flow<Unit> = active.flatMapLatest { it.observeTriggerEvents() }
    override fun observeHandheldTrigger(): Flow<Boolean> = active.flatMapLatest { it.observeHandheldTrigger() }
    override fun observeProximity(): Flow<Int> = active.flatMapLatest { it.observeProximity() }

    // IH45 first: connecting the first entry then follows the USB priority
    override suspend fun discoverReaders(): List<String> {
        val ih45Names = ih45.discoverReaders()
        synchronized(ih45Ids) { ih45Ids.clear(); ih45Ids.addAll(ih45Names) }
        return ih45Names + zebra.discoverReaders()
    }

    override suspend fun connect(readerId: String): Result<Unit> = selectionLock.withLock { connectLocked(readerId) }

    private suspend fun connectLocked(readerId: String): Result<Unit> {
        val isIh45 = synchronized(ih45Ids) { readerId in ih45Ids }
        val target = if (isIh45) ih45 else zebra
        val other = if (isIh45) zebra else ih45
        if (target === active.value && target.observeStatus().first().isConnected) return Result.success(Unit)

        other.disconnect()
        active.value = target
        return target.connect(readerId).onSuccess { lastPower?.let { target.setPower(it) } }
    }

    // Reader change after hot-plugging the IH45: reconnect with the same priority as at startup
    private suspend fun autoSelect() = selectionLock.withLock {
        val names = discoverReaders()
        names.firstOrNull()?.let { connectLocked(it) } ?: active.value.disconnect()
    }

    override suspend fun disconnect() = active.value.disconnect()

    override suspend fun triggerInventory() = active.value.triggerInventory()

    override suspend fun setPower(level: Int): Result<Unit> {
        lastPower = level
        return active.value.setPower(level)
    }

    // Both readers get the mode, so it is already right whichever one becomes active
    override fun setTriggerMode(mode: TriggerMode) {
        zebra.setTriggerMode(mode); ih45.setTriggerMode(mode)
    }

    override fun claimTriggerMode(owner: Any, mode: TriggerMode) {
        zebra.claimTriggerMode(owner, mode); ih45.claimTriggerMode(owner, mode)
    }

    override fun releaseTriggerMode(owner: Any) {
        zebra.releaseTriggerMode(owner); ih45.releaseTriggerMode(owner)
    }

    override suspend fun startLocate(epc: String, mask: String) = active.value.startLocate(epc, mask)
    override suspend fun stopLocate() = active.value.stopLocate()
    override suspend fun readEpcsNearby() = active.value.readEpcsNearby()
    override suspend fun readTidsNearby(epc: String, mask: String) = active.value.readTidsNearby(epc, mask)
}
