package de.ca.qcc.device.honeywell

import android.content.Context
import android.util.Log
import com.honeywell.rfidservice.EventListener
import com.honeywell.rfidservice.RfidManager
import com.honeywell.rfidservice.TriggerMode as Ih45TriggerMode
import com.honeywell.rfidservice.rfid.OnTagReadListener
import com.honeywell.rfidservice.rfid.AntennaPower
import com.honeywell.rfidservice.rfid.RfidReader
import com.honeywell.rfidservice.rfid.TagAdditionData
import com.honeywell.rfidservice.rfid.TagReadData
import com.usb.UsbPort
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.device.zebra.TriggerMode
import de.ca.qcc.device.zebra.TriggerModeArbiter
import de.ca.qcc.domain.model.ReaderStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Honeywell IH45 UHF reader, connected through its USB serial port (/dev/ttyACM0) with the
 * Honeywell RFID SDK. Same contract as [de.ca.qcc.device.zebra.ZebraRfd8500Gateway]: QCC reads
 * exactly one EPC per trigger press, the duplicate finder uses nearby reads and RSSI locating.
 *
 * Verified on hardware (2026-10-01): USB connect, trigger events in RFID mode, EPC and TID reads,
 * power set while not reading. The SDK reports every tag many times per second.
 *
 * Range is not controlled by lowering the transmit power (5 dBm reads only at contact, anything in
 * between is a knob the user must tune): the reader always runs at full power and the tag at the
 * reader is the one with the strongest RSSI. The user-selected power level is ignored.
 */
@Singleton
class HoneywellIh45Gateway @Inject constructor(
    @ApplicationContext private val context: Context
) : RfidReaderGateway {

    private val statusFlow = MutableStateFlow(ReaderStatus(message = "Nicht verbunden"))
    private val tagFlow = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val triggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val handheldTriggerFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val proximityFlow = MutableSharedFlow<Int>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // True while the IH45 is plugged in; the switching gateway reselects the reader on changes
    private val usbPresentFlow = MutableStateFlow(false)
    val usbPresent: StateFlow<Boolean> = usbPresentFlow.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val managerReady = CompletableDeferred<RfidManager>()
    @Volatile private var manager: RfidManager? = null
    @Volatile private var reader: RfidReader? = null
    @Volatile private var connected = false
    @Volatile private var readerCreated: CompletableDeferred<RfidReader?>? = null
    @Volatile private var deviceConnected: CompletableDeferred<Unit>? = null

    @Volatile private var triggerMode = TriggerMode.QCC
    private val triggerArbiter = TriggerModeArbiter()

    // QCC: strongest RSSI per EPC seen since the trigger press; the closest tag wins after a short window
    private val qccHits = ConcurrentHashMap<String, Int>()
    @Volatile private var qccSession = 0
    @Volatile private var qccWindowStarted = false

    // Locate: recent hits (with TID) of the wanted EPC; confirming a find reads from here
    private val locateHits = ConcurrentHashMap<String, Hit>()

    private val inventoryActive = AtomicBoolean(false)
    private val locateActive = AtomicBoolean(false)
    private val nearbyActive = AtomicBoolean(false)
    @Volatile private var nearbySink: ((TagReadData) -> Unit)? = null

    private class Hit(val epc: String, val tid: String, val rssi: Int, val timeMs: Long)
    @Volatile private var locateEpc: String? = null
    @Volatile private var locateMask: String? = null

    init {
        // The SDK service must run early so USB attach events are not missed
        runCatching {
            RfidManager.create(context, object : RfidManager.CreatedCallback {
                override fun onCreated(p0: RfidManager?) {
                    if (p0 == null) return
                    manager = p0
                    p0.addEventListener(eventListener)
                    usbPresentFlow.value = p0.attachedUsbDevice != null
                    managerReady.complete(p0)
                    // A cable that was plugged in before the app started is reported late (no attach event)
                    if (!usbPresentFlow.value) scope.launch { pollForUsb(p0) }
                }
            })
        }.onFailure { Log.w(TAG, "RfidManager.create failed", it) }
    }

    private suspend fun pollForUsb(m: RfidManager, attempts: Int = USB_POLL_ATTEMPTS): UsbPort? {
        repeat(attempts) {
            m.attachedUsbDevice?.let { usbPresentFlow.value = true; return it }
            delay(USB_POLL_INTERVAL_MS)
        }
        return m.attachedUsbDevice
    }

    private val eventListener = object : EventListener {
        override fun onUsbDeviceAttached(p0: Any?) {
            Log.d(TAG, "USB attached")
            usbPresentFlow.value = true
        }

        override fun onUsbDeviceDetached(p0: Any?) {
            Log.d(TAG, "USB detached")
            usbPresentFlow.value = false
            markDisconnected()
        }

        override fun onDeviceConnected(p0: Any?) {
            Log.d(TAG, "device connected: $p0")
            deviceConnected?.complete(Unit)
        }

        override fun onDeviceDisconnected(p0: Any?) {
            Log.d(TAG, "device disconnected: $p0")
            markDisconnected()
        }

        override fun onReaderCreated(p0: Boolean, p1: RfidReader?) {
            p1?.setOnTagReadListener(tagListener)
            readerCreated?.complete(if (p0) p1 else null)
        }

        override fun onRfidTriggered(p0: Boolean) {
            if (!connected) return
            if (p0) onTriggerPressed() else onTriggerReleased()
        }
    }

    private fun onTriggerPressed() {
        Log.d(TAG, "trigger pressed (mode=$triggerMode)")
        if (triggerMode == TriggerMode.QCC) {
            scope.launch { startInventoryInternal() }
            triggerFlow.tryEmit(Unit)
        } else {
            handheldTriggerFlow.tryEmit(true)
        }
    }

    private fun onTriggerReleased() {
        if (triggerMode == TriggerMode.QCC) {
            // Released: take the closest tag read so far, or stop if there was none
            if (inventoryActive.get()) finishQcc(qccSession)
        } else {
            handheldTriggerFlow.tryEmit(false)
        }
    }

    private fun markDisconnected() {
        connected = false
        inventoryActive.set(false)
        locateActive.set(false)
        nearbySink = null
        reader = null
        proximityFlow.tryEmit(0)
        updateStatus("Nicht verbunden")
    }

    // The SDK delivers every tag many times per second, possibly from several threads
    private val tagListener = object : OnTagReadListener {
        override fun onTagRead(p0: Array<out TagReadData>?) {
            val tags = p0 ?: return
            nearbySink?.let { sink -> tags.forEach(sink); return }

            if (locateActive.get()) {
                handleLocate(tags)
                return
            }

            if (inventoryActive.get()) collectQcc(tags)
        }
    }

    // ── QCC: the closest tag wins ────────────────────────────────────────────

    private fun collectQcc(tags: Array<out TagReadData>) {
        var heardOne = false
        for (t in tags) {
            if (t.epcHexStr.isBlank() || t.rssi < QCC_MIN_RSSI) continue
            qccHits.merge(t.epcHexStr.uppercase(), t.rssi) { a, b -> maxOf(a, b) }
            heardOne = true
        }
        // The first usable tag opens a short window in which a closer tag can still take over
        if (heardOne && !qccWindowStarted) {
            qccWindowStarted = true
            val session = qccSession
            scope.launch { delay(QCC_WINDOW_MS); finishQcc(session) }
        }
    }

    private fun finishQcc(session: Int) {
        if (session != qccSession || !inventoryActive.compareAndSet(true, false)) return
        stopReadQuietly()
        val best = qccHits.entries.maxByOrNull { it.value }
        Log.d(TAG, "QCC result=${best?.key} rssi=${best?.value} candidates=$qccHits")
        best?.let { tagFlow.tryEmit(it.key) }
    }

    // ── Flows ────────────────────────────────────────────────────────────────

    override fun observeStatus(): Flow<ReaderStatus> = statusFlow.asStateFlow()
    override fun observeTagScans(): Flow<String> = tagFlow.asSharedFlow()
    override fun observeTriggerEvents(): Flow<Unit> = triggerFlow.asSharedFlow()
    override fun observeHandheldTrigger(): Flow<Boolean> = handheldTriggerFlow.asSharedFlow()
    override fun observeProximity(): Flow<Int> = proximityFlow.asSharedFlow()

    // ── Trigger mode ─────────────────────────────────────────────────────────

    override fun claimTriggerMode(owner: Any, mode: TriggerMode) =
        setTriggerMode(triggerArbiter.claim(owner, mode))

    override fun releaseTriggerMode(owner: Any) {
        triggerArbiter.release(owner)?.let { setTriggerMode(it) }
            ?: Log.d(TAG, "releaseTriggerMode ignored: $owner no longer owns the trigger")
    }

    override fun setTriggerMode(mode: TriggerMode) {
        Log.d(TAG, "triggerMode $triggerMode -> $mode")
        triggerMode = mode
        if (mode == TriggerMode.QCC && locateActive.get()) scope.launch { stopLocate() }
    }

    // ── Connection ───────────────────────────────────────────────────────────

    // The IH45 has no discovery: it is "found" when its USB serial port is present
    override suspend fun discoverReaders(): List<String> = withContext(Dispatchers.IO) {
        val m = awaitManager() ?: return@withContext emptyList()
        val usb = m.attachedUsbDevice ?: pollForUsb(m, DISCOVER_POLL_ATTEMPTS)
        usbPresentFlow.value = usb != null
        if (usb == null) {
            updateStatus("Kein IH45 per USB gefunden")
            emptyList()
        } else {
            listOf(usb.name ?: READER_NAME)
        }
    }

    override suspend fun connect(readerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val m = awaitManager() ?: error("Honeywell RFID Service nicht verfügbar")
            if (connected && reader != null) return@runCatching
            val usb = m.attachedUsbDevice ?: error("IH45 nicht per USB verbunden")

            Log.d(TAG, "connecting ${usb.path}")
            m.setAutoReconnect(false)
            // createReader only works once the SDK reports the device as connected
            val deviceUp = CompletableDeferred<Unit>().also { deviceConnected = it }
            check(m.connect(usb)) { "IH45 Verbindung fehlgeschlagen" }
            // withTimeoutOrNull: a plain withTimeout would look like a cancellation and hide the failure
            withTimeoutOrNull(DEVICE_CONNECT_TIMEOUT_MS) { deviceUp.await() }
                ?: error("IH45 meldet keine Verbindung")
            Log.d(TAG, "device up, creating reader")

            val r = createReaderWithRetry(m) ?: error("IH45 Reader konnte nicht erzeugt werden")
            reader = r
            connected = true
            m.setTriggerMode(Ih45TriggerMode.RFID)
            applyPower(r, MAX_POWER_RAW)
            Log.d(TAG, "connected, power raw=$MAX_POWER_RAW")
            updateStatus("Verbunden")
        }.rethrowCancellation().onFailure { e ->
            Log.w(TAG, "connect failed", e)
            runCatching { manager?.disconnect() }
            markDisconnected()
            updateStatus("Verbindung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            runCatching { stopReadQuietly() }
            runCatching { manager?.disconnect() }
            markDisconnected()
        }
    }

    private suspend fun createReaderWithRetry(m: RfidManager): RfidReader? {
        repeat(READER_CREATE_ATTEMPTS) { attempt ->
            val created = CompletableDeferred<RfidReader?>().also { readerCreated = it }
            m.createReader()
            withTimeoutOrNull(READER_CREATE_TIMEOUT_MS) { created.await() }?.let { return it }
            Log.w(TAG, "createReader attempt ${attempt + 1} got no reader")
        }
        return null
    }

    private suspend fun awaitManager(): RfidManager? =
        manager ?: runCatching { withTimeout(MANAGER_TIMEOUT_MS) { managerReady.await() } }.getOrNull()

    // ── QCC single tag ───────────────────────────────────────────────────────

    override suspend fun triggerInventory() {
        check(connected && reader != null) { "Reader nicht verbunden" }
        startInventoryInternal()
    }

    private fun startInventoryInternal() {
        val r = reader ?: return
        if (!inventoryActive.compareAndSet(false, true)) return
        qccSession++
        qccHits.clear()
        qccWindowStarted = false
        val started = startRead(r, TagAdditionData.NONE)
        Log.d(TAG, "inventory started=$started")
        if (!started) inventoryActive.set(false)
    }

    // A previous read may still be running: stop it and retry once
    private fun startRead(r: RfidReader, data: TagAdditionData): Boolean {
        if (runCatching { r.read(data) }.getOrDefault(false)) return true
        stopReadQuietly()
        Thread.sleep(RESTART_DELAY_MS)
        return runCatching { r.read(data) }.onFailure { Log.w(TAG, "read failed", it) }.getOrDefault(false)
    }

    private fun stopReadQuietly() {
        runCatching { reader?.stopRead() }.onFailure { Log.w(TAG, "stopRead failed", it) }
    }

    // ── Power ────────────────────────────────────────────────────────────────

    // The selected level only applies to the Zebra: the IH45 always reads at full power (see class doc)
    override suspend fun setPower(level: Int): Result<Unit> {
        Log.d(TAG, "setPower level=$level ignored (always full power)")
        return if (connected) Result.success(Unit) else Result.failure(IllegalStateException("Reader nicht verbunden"))
    }

    // Setting the power only works while the reader is not reading (verified on hardware)
    private fun applyPower(r: RfidReader, raw: Int) {
        var lastError: Throwable? = null
        repeat(2) {
            try {
                runCatching { r.stopRead() }
                Thread.sleep(RESTART_DELAY_MS)
                r.antennaPower = arrayOf(AntennaPower(1, raw, raw))
                return
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IllegalStateException("Sendeleistung nicht setzbar: ${lastError?.message}", lastError)
    }

    // ── Duplicate search ─────────────────────────────────────────────────────

    // The IH45 has no tag-locating command we rely on. Far away (full power) the RSSI of the wanted EPC
    // gives the distance; in the near field the RSSI saturates (touching and 20 cm read the same), so
    // the power is stepped down while the tag is read strongly: a tag that still answers at 5 dBm is
    // touching the reader. The reads carry the TID, so confirming a find needs no second read.
    override suspend fun startLocate(epc: String, mask: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val r = requireReader()
            check(!nearbyActive.get() && !inventoryActive.get()) { "Reader beschäftigt" }
            if (!locateActive.compareAndSet(false, true)) return@runCatching
            try {
                locatePowerLock.withLock {
                    locateEpc = epc
                    locateMask = mask
                    locateHits.clear()
                    resetLocateLevel(LEVEL_FULL)
                    if (locateLevel != LEVEL_FULL || currentPowerRaw != MAX_POWER_RAW) applyPower(r, MAX_POWER_RAW)
                    currentPowerRaw = MAX_POWER_RAW
                    check(startRead(r, TagAdditionData.TID_BANK)) { "Locate konnte nicht gestartet werden" }
                }
            } catch (t: Throwable) {
                locateActive.set(false)
                throw t
            }
            locateJob = scope.launch { locateController() }
        }.rethrowCancellation()
    }

    // The hits stay: the view model stops locating first and then confirms the find
    override suspend fun stopLocate() {
        withContext(Dispatchers.IO) {
            if (locateActive.compareAndSet(true, false)) {
                locateJob?.cancel()
                locatePowerLock.withLock {
                    lastLocateEndLevel = locateLevel
                    stopReadQuietly()
                    // Everything else (QCC, nearby reads) expects full power
                    val r = reader
                    if (r != null && currentPowerRaw != MAX_POWER_RAW) {
                        runCatching { applyPower(r, MAX_POWER_RAW) }
                            .onSuccess { currentPowerRaw = MAX_POWER_RAW }
                            .onFailure { Log.w(TAG, "restoring full power failed", it) }
                    }
                    locateLevel = LEVEL_FULL
                }
                proximityFlow.tryEmit(0)
            }
        }
    }

    // Level 0 = full power, 1 = reduced, 2 = lowest (touching). Reads of the current level only.
    private val locatePowerLock = Mutex()
    @Volatile private var locateJob: Job? = null
    @Volatile private var currentPowerRaw = MAX_POWER_RAW
    @Volatile private var locateLevel = LEVEL_FULL
    @Volatile private var lastLocateEndLevel = LEVEL_FULL
    @Volatile private var levelEnteredMs = 0L
    @Volatile private var levelFirstReadMs = 0L
    @Volatile private var levelLastReadMs = 0L
    @Volatile private var strongSinceMs = 0L
    @Volatile private var lastStrongMs = 0L
    @Volatile private var lastEmitMs = 0L
    @Volatile private var nextStepDownMs = 0L
    @Volatile private var switchingPower = false
    @Volatile private var lastLocateMatchMs = 0L

    private fun emitProximity(value: Int) {
        lastEmitMs = System.currentTimeMillis()
        proximityFlow.tryEmit(value)
    }

    private fun resetLocateLevel(level: Int) {
        val now = System.currentTimeMillis()
        locateLevel = level
        levelEnteredMs = now
        levelFirstReadMs = 0
        levelLastReadMs = 0
        strongSinceMs = 0
        lastLocateMatchMs = now
    }

    private fun handleLocate(tags: Array<out TagReadData>) {
        if (switchingPower) return // reads around a power change belong to neither level
        val epc = locateEpc ?: return
        val mask = locateMask ?: return
        val now = System.currentTimeMillis()
        var best: Int? = null
        for (t in tags) {
            if (!matches(t.epcHexStr, epc, mask)) continue
            best = maxOf(best ?: t.rssi, t.rssi)
            val tid = tidOf(t)
            if (tid.isNotBlank()) locateHits[tid] = Hit(t.epcHexStr.uppercase(), tid, t.rssi, now)
        }
        best ?: return
        lastLocateMatchMs = now
        levelLastReadMs = now
        if (levelFirstReadMs == 0L) levelFirstReadMs = now
        val level = locateLevel
        if (level == LEVEL_FULL && best >= STEP_DOWN_RSSI) {
            // A streak only ends when no strong read came for a while: single weak reads are normal
            if (strongSinceMs == 0L || now - lastStrongMs > STRONG_GAP_MS) strongSinceMs = now
            lastStrongMs = now
        }
        val proximity = proximityFor(level, best)
        Log.d(TAG, "locate level=$level rssi=$best -> proximity=$proximity")
        emitProximity(proximity)
    }

    // Steps the power down while the tag is read strongly, and back up when it is lost
    private suspend fun locateController() {
        while (locateActive.get()) {
            delay(CONTROL_INTERVAL_MS)
            if (switchingPower || !locateActive.get()) continue
            val now = System.currentTimeMillis()
            val level = locateLevel
            val steady = levelFirstReadMs != 0L && now - levelFirstReadMs >= DWELL_MS && now - levelLastReadMs < LOSS_MS
            val lost = now - maxOf(levelLastReadMs, levelEnteredMs) > LOSS_MS
            when (level) {
                LEVEL_FULL ->
                    if (strongSinceMs != 0L && now - lastStrongMs <= STRONG_GAP_MS &&
                        now - strongSinceMs >= DWELL_MS && now >= nextStepDownMs
                    ) switchLevel(LEVEL_REDUCED)
                LEVEL_REDUCED -> when {
                    steady -> switchLevel(LEVEL_LOWEST)
                    lost -> { nextStepDownMs = now + RETRY_COOLDOWN_MS; switchLevel(LEVEL_FULL) }
                }
                LEVEL_LOWEST -> if (lost) switchLevel(LEVEL_REDUCED)
            }
            if (!locateActive.get() || switchingPower) continue
            if (level == LEVEL_FULL) {
                // The SDK reports nothing once the tag is out of range: let the feedback fall silent
                if (now - lastLocateMatchMs > LOCATE_SILENCE_MS) emitProximity(0)
            } else if (now - levelLastReadMs < LOSS_MS && now - lastEmitMs >= REFRESH_MS) {
                // Weak reads come only a few times a second: keep showing the level until it is really lost
                emitProximity(proximityFor(level, 0))
            }
        }
    }

    private suspend fun switchLevel(level: Int) = locatePowerLock.withLock {
        val r = reader
        if (r == null || !locateActive.get()) return@withLock
        switchingPower = true
        try {
            Log.d(TAG, "locate power level ${locateLevel} -> $level")
            val raw = when (level) { LEVEL_FULL -> MAX_POWER_RAW; LEVEL_REDUCED -> REDUCED_POWER_RAW; else -> LOWEST_POWER_RAW }
            applyPower(r, raw)
            currentPowerRaw = raw
            // Reading the TID needs more power than answering an inventory: only at full power
            val data = if (level == LEVEL_FULL) TagAdditionData.TID_BANK else TagAdditionData.NONE
            if (!startRead(r, data)) Log.w(TAG, "locate restart failed")
            resetLocateLevel(level)
        } catch (t: Throwable) {
            Log.w(TAG, "locate power change failed", t)
        } finally {
            switchingPower = false
            // The pause must not count as silence
            lastLocateMatchMs = System.currentTimeMillis()
        }
    }

    override suspend fun readEpcsNearby(): Result<List<String>> = withContext(Dispatchers.IO) {
        collectNearby(TagAdditionData.NONE) { true }
            .map { hits -> closest(hits).map { it.epc }.distinct() }
    }

    override suspend fun readTidsNearby(epc: String, mask: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            // Normal case: the find is confirmed right after locating, so the hits are fresh
            val now = System.currentTimeMillis()
            // After the near-field steps no TID was read for the tag at the reader (EPC-only reads), and
            // the cache would hold whatever was swept before: read live in that case
            val fresh = if (lastLocateEndLevel != LEVEL_FULL) emptyList() else
                locateHits.values.filter { now - it.timeMs <= LOCATE_CACHE_MS && matches(it.epc, epc, mask) }
            val hits = if (fresh.isNotEmpty()) fresh else
                collectNearby(TagAdditionData.TID_BANK) { matches(it.epcHexStr, epc, mask) }.getOrThrow()
            closest(hits).map { it.tid }.filter { it.isNotBlank() }.distinct()
        }.rethrowCancellation()
    }

    // The tags that count as "at the reader": strong enough, and close to the strongest one
    private fun closest(hits: Collection<Hit>): List<Hit> = selectClosest(hits, NEARBY_MIN_RSSI, NEARBY_MARGIN_DB) { it.rssi }
        .also { Log.d(TAG, "nearby candidates=${hits.map { "${it.epc}/${it.tid.takeLast(6)}=${it.rssi}" }} -> ${it.size}") }

    // Reads for a short window; the strongest RSSI per EPC/TID pair is kept
    private suspend fun collectNearby(
        data: TagAdditionData,
        accept: (TagReadData) -> Boolean
    ): Result<List<Hit>> = runCatching {
        check(nearbyActive.compareAndSet(false, true)) { "Reader beschäftigt" }
        try {
            val r = requireReader()
            check(!locateActive.get() && !inventoryActive.get()) { "Reader beschäftigt" }
            val found = ConcurrentHashMap<String, Hit>()
            nearbySink = { t ->
                if (accept(t)) {
                    val hit = Hit(t.epcHexStr.uppercase(), tidOf(t), t.rssi, System.currentTimeMillis())
                    found.merge("${hit.epc}|${hit.tid}", hit) { a, b -> if (b.rssi > a.rssi) b else a }
                }
            }
            try {
                check(startRead(r, data)) { "Lesen konnte nicht gestartet werden" }
                delay(NEARBY_READ_WINDOW_MS)
                check(reader === r && connected) { "Reader nicht verbunden" }
            } finally {
                // In finally so a cancelled read never leaves the reader running
                runCatching { r.stopRead() }
                nearbySink = null
            }
            found.values.toList()
        } finally {
            nearbyActive.set(false)
        }
    }.rethrowCancellation()

    private fun tidOf(t: TagReadData): String = t.additionData?.joinToString("") { "%02X".format(it) }.orEmpty()

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun requireReader(): RfidReader =
        reader?.takeIf { connected } ?: throw IllegalStateException("Reader nicht verbunden")

    private fun <T> Result<T>.rethrowCancellation(): Result<T> =
        onFailure { if (it is CancellationException) throw it }

    private fun updateStatus(message: String) {
        val battery = if (connected) {
            runCatching { manager?.batteryLevel?.trim()?.toInt() }.getOrNull()
        } else null
        statusFlow.value = ReaderStatus(
            isConnected = connected,
            readerName = READER_NAME,
            batteryPercent = battery,
            message = message
        )
    }

    companion object {
        const val READER_NAME = "Honeywell-IH45"
        private const val TAG = "QccIh45"

        // Verified on hardware: 500 reads only a tag at contact, 0 and 100 read nothing, 3000 reads the room
        internal const val MAX_POWER_RAW = 3000

        // RSSI (dBm, at full power) tuning, see class doc. Field data (2026-10-01): a tag at the reader
        // reads -23..-43, a neighbouring tag in the other hand 5..8 dB weaker, tags a bit away -56..-60.
        internal const val QCC_MIN_RSSI = -50
        internal const val NEARBY_MIN_RSSI = -50
        internal const val NEARBY_MARGIN_DB = 5
        private const val QCC_WINDOW_MS = 400L
        private const val LOCATE_CACHE_MS = 3_000L
        private const val NEARBY_READ_WINDOW_MS = 700L
        private const val RESTART_DELAY_MS = 300L
        private const val LOCATE_SILENCE_MS = 1_500L
        private const val USB_POLL_INTERVAL_MS = 500L
        private const val USB_POLL_ATTEMPTS = 20 // 10 s after SDK start
        private const val DISCOVER_POLL_ATTEMPTS = 6 // 3 s while the app looks for a reader
        private const val MANAGER_TIMEOUT_MS = 5_000L
        private const val DEVICE_CONNECT_TIMEOUT_MS = 10_000L
        private const val READER_CREATE_TIMEOUT_MS = 5_000L
        private const val READER_CREATE_ATTEMPTS = 2

        // Strongest tag, plus every tag within `marginDb` of it; nothing if even the strongest is too weak
        internal fun <T : Any> selectClosest(hits: Collection<T>, minRssi: Int, marginDb: Int, rssi: (T) -> Int): List<T> {
            val best = hits.maxOfOrNull(rssi) ?: return emptyList()
            if (best < minRssi) return emptyList()
            return hits.filter { rssi(it) >= best - marginDb }
        }

        // Exact match under `mask` (hex, 1-bits compared); an empty or malformed value never matches
        internal fun matches(tagEpc: String, epc: String, mask: String): Boolean = runCatching {
            val m = BigInteger(mask, 16)
            BigInteger(tagEpc, 16).and(m) == BigInteger(epc, 16).and(m)
        }.getOrDefault(false)

        // Field data (2026-10-01, full power): a tag touching the reader reads -23..-27, about 30 cm away
        // -30, 50 cm and more -35..-40. The near field is steep and saturates, so the scale starts at
        // -46 (0 %) and reaches 100 % only at contact (-27 and stronger); -28 gives 95 %.
        internal fun rssiToProximity(rssi: Int): Int =
            (((rssi - PROXIMITY_FAR_RSSI) * 100f) / (PROXIMITY_NEAR_RSSI - PROXIMITY_FAR_RSSI)).roundToInt().coerceIn(0, 100)

        // Reduced levels override the RSSI scale: still read at 10 dBm = close, still read at 5 dBm = touching
        internal fun proximityFor(level: Int, rssi: Int): Int = when (level) {
            LEVEL_LOWEST -> 100
            LEVEL_REDUCED -> 90
            else -> rssiToProximity(rssi) * FULL_POWER_MAX_PROXIMITY / 100
        }

        internal const val LEVEL_FULL = 0
        internal const val LEVEL_REDUCED = 1
        internal const val LEVEL_LOWEST = 2

        // Raw power (1/100 dBm): 500 reads only a tag at contact (verified), 1000 only the tag in the hand
        internal const val LOWEST_POWER_RAW = 500
        internal const val REDUCED_POWER_RAW = 1000

        // At full power the RSSI only covers the far range; closer is decided by the lower power levels
        private const val FULL_POWER_MAX_PROXIMITY = 80
        private const val STEP_DOWN_RSSI = -29
        private const val DWELL_MS = 300L
        private const val LOSS_MS = 2_000L
        private const val STRONG_GAP_MS = 500L
        private const val REFRESH_MS = 250L
        private const val RETRY_COOLDOWN_MS = 4_000L
        private const val CONTROL_INTERVAL_MS = 100L

        private const val PROXIMITY_FAR_RSSI = -46
        private const val PROXIMITY_NEAR_RSSI = -27
    }
}
