package de.ca.qcc.feature.diagnostics

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.app.PendingIntent
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.honeywell.rfidservice.ConnectionState
import com.honeywell.rfidservice.EventListener
import com.honeywell.rfidservice.RfidManager
import com.honeywell.rfidservice.TriggerMode
import com.honeywell.rfidservice.rfid.OnTagReadListener
import com.honeywell.rfidservice.rfid.RfidReader
import com.honeywell.rfidservice.rfid.TagAdditionData
import com.honeywell.rfidservice.rfid.TagReadData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TEMPORARY hardware probe for the Honeywell IH45 (SDK honeywell_rfid_sdk). Logs every SDK callback so
 * we can learn how USB/Bluetooth connect, the trigger and tag reads behave before writing the gateway.
 * Removed once HoneywellIh45Gateway exists (tasks/todo.md, phase 0).
 */
@Singleton
class Ih45Probe @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val logFlow = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = logFlow.asStateFlow()

    @Volatile private var manager: RfidManager? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun log(message: String) {
        Log.d(TAG, message)
        val line = "${timeFormat.format(Date())}  $message"
        logFlow.update { (it + line).takeLast(MAX_LINES) }
    }

    fun clear() = logFlow.update { emptyList() }

    private inline fun <T> attempt(what: String, block: () -> T): T? =
        try {
            block().also { log("$what -> $it") }
        } catch (t: Throwable) {
            log("$what FAILED: ${t.javaClass.simpleName}: ${t.message}")
            null
        }

    private val listener = object : EventListener {
        override fun onDeviceConnected(p0: Any?) = log("onDeviceConnected($p0)")
        override fun onDeviceDisconnected(p0: Any?) = log("onDeviceDisconnected($p0)")
        override fun onDeviceDisconnectInfo(p0: com.honeywell.rfidservice.DeviceDisconnectInfo?) =
            log("onDeviceDisconnectInfo($p0)")
        override fun onUsbDeviceAttached(p0: Any?) = log("onUsbDeviceAttached($p0)")
        override fun onUsbDeviceDetached(p0: Any?) = log("onUsbDeviceDetached($p0)")
        override fun onReaderCreated(p0: Boolean, p1: RfidReader?) {
            log("onReaderCreated(ok=$p0, reader=${p1 != null})")
            p1?.setOnTagReadListener(tagListener)
        }
        override fun onRfidTriggered(p0: Boolean) = log("onRfidTriggered(pressed=$p0)")
        override fun onTriggerModeSwitched(p0: TriggerMode?) = log("onTriggerModeSwitched($p0)")
        override fun onReceivedFindingTag(p0: Int) = log("onReceivedFindingTag($p0)")
        override fun onReadRemoteRssi(p0: BluetoothDevice?, p1: Int) = log("onReadRemoteRssi(${p0?.name}, $p1)")
    }

    // The SDK delivers each tag many times per second: log one line per second per EPC with the count
    private var lastEpc = ""
    private var lastLogMs = 0L
    private var suppressed = 0

    private val tagListener = object : OnTagReadListener {
        override fun onTagRead(p0: Array<out TagReadData>?) {
            p0?.forEach { t ->
                val now = System.currentTimeMillis()
                if (t.epcHexStr == lastEpc && now - lastLogMs < 1000) { suppressed++; return@forEach }
                val extra = t.additionData?.joinToString("") { "%02X".format(it) }.orEmpty()
                log("TAG epc=${t.epcHexStr} rssi=${t.rssi} ant=${t.antenna} tid/add=$extra (+$suppressed duplicates)")
                lastEpc = t.epcHexStr; lastLogMs = now; suppressed = 0
            }
        }
    }

    fun init() {
        log("SDK init (RfidManager.create)")
        attempt("RfidManager.create") {
            RfidManager.create(context, object : RfidManager.CreatedCallback {
                override fun onCreated(p0: RfidManager?) {
                    manager = p0
                    p0?.addEventListener(listener)
                    log("onCreated: manager ready=${p0?.isReady}, sdk=${p0?.sdkVersionName}")
                }
            })
        }
    }

    private fun mgr(): RfidManager? = manager.also { if (it == null) log("Not initialised - press Init first") }

    fun state() {
        val m = mgr() ?: return
        log("state=${m.connectionState} connected=${m.isConnected} usbDevice=${m.isUsbDevice} " +
            "serialDevice=${m.isSerialDevice} byUsb=${m.isConnectedByUsb} bySerial=${m.isConnectedBySerial} " +
            "readerAvailable=${m.readerAvailable()} ref=${m.readerRef}")
    }

    fun attachedUsb() {
        val m = mgr() ?: return
        attempt("getAttachedUsbDevice") { m.attachedUsbDevice?.let { "path=${it.path} name=${it.name} baud=${it.baudrate}" } }
    }

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> {
        val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
        val devices = try { adapter?.bondedDevices?.toList().orEmpty() } catch (t: SecurityException) {
            log("Bluetooth permission missing: ${t.message}"); emptyList()
        }
        log("Bonded BT devices (${devices.size}): " + devices.joinToString { "${it.name} [${it.address}]" })
        return devices
    }

    fun connectUsb() {
        val m = mgr() ?: return
        val usb = m.attachedUsbDevice
        if (usb == null) { log("connectUsb: no USB device attached"); return }
        m.setAutoReconnect(false)
        attempt("connect(UsbPort ${usb.path})") { m.connect(usb) }
    }

    @SuppressLint("MissingPermission")
    fun connectBluetooth(address: String) {
        val m = mgr() ?: return
        val device = context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
        if (device == null) { log("connectBluetooth: no adapter/device"); return }
        m.setAutoReconnect(false)
        attempt("connect(BT $address)") { m.connect(device) }
    }

    fun disconnect() {
        val m = mgr() ?: return
        attempt("disconnect") { m.disconnect() }
    }

    fun createReader() {
        val m = mgr() ?: return
        attempt("createReader") { m.createReader() }
    }

    fun setTriggerMode(mode: TriggerMode) {
        val m = mgr() ?: return
        attempt("setTriggerMode($mode)") { m.setTriggerMode(mode) }
    }

    fun info() {
        val m = mgr() ?: return
        attempt("triggerMode") { m.triggerMode }
        attempt("battery") { m.batteryLevel }
        attempt("serial") { m.sn }
        attempt("rfidModuleHw/Sw") { "${m.rfidModuleHWVersion} / ${m.rfidModuleSWVersion}" }
        attempt("btModuleHw/Sw") { "${m.bluetoothModuleHwVersion} / ${m.bluetoothModuleSwVersion}" }
        val r = m.reader
        if (r == null) { log("reader=null (createReader first)"); return }
        attempt("reader.available") { r.available() }
        attempt("reader.isHonReader") { r.isHonReader }
        attempt("region") { r.region }
        attempt("antennaPower") { r.antennaPower.joinToString { "ant${it.antennaId}: read=${it.readPower} write=${it.writePower}" } }
        attempt("session") { r.session }
        attempt("profile") { r.profile }
    }

    fun startRead(withTid: Boolean) {
        val r = mgr()?.reader ?: run { log("reader=null (createReader first)"); return }
        r.setOnTagReadListener(tagListener)
        attempt("read(${if (withTid) "EPC+TID" else "EPC"})") {
            if (withTid) r.read(TagAdditionData.TID_BANK) else r.read(TagAdditionData.NONE)
        }
    }

    fun stopRead() {
        val r = mgr()?.reader ?: run { log("reader=null"); return }
        attempt("stopRead") { r.stopRead() }
    }

    // Raw SDK unit is still unknown (default read=500): set, read back, and watch RSSI / read range
    fun setPower(raw: Int) {
        val r = mgr()?.reader ?: run { log("reader=null"); return }
        attempt("stopRead before power change") { r.stopRead() }
        Thread.sleep(500)
        attempt("setAntennaPower(raw $raw)") {
            r.antennaPower = arrayOf(com.honeywell.rfidservice.rfid.AntennaPower(1, raw, raw))
        }
        attempt("antennaPower readback") { r.antennaPower.joinToString { "ant${it.antennaId}: read=${it.readPower} write=${it.writePower}" } }
    }

    // ── What Android itself sees (independent of the Honeywell SDK) ──

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val d = i.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            when (i.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> log("USB attached: ${d?.describe()}")
                UsbManager.ACTION_USB_DEVICE_DETACHED -> log("USB detached: ${d?.deviceName}")
                ACTION_USB_PERMISSION ->
                    log("USB permission result for ${d?.deviceName}: granted=${i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)}")
            }
        }
    }
    private var usbReceiverRegistered = false

    private fun UsbDevice.describe(): String {
        val usb = context.getSystemService(UsbManager::class.java)
        val ifaces = (0 until interfaceCount).joinToString(",") { getInterface(it).interfaceClass.toString() }
        return "$deviceName vid:pid=%04X:%04X class=$deviceClass ifaceClasses=[$ifaces] perm=${usb.hasPermission(this)}"
            .format(vendorId, productId)
    }

    private fun ensureUsbReceiver() {
        if (usbReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(usbReceiver, filter)
        usbReceiverRegistered = true
    }

    fun usbDevices() {
        ensureUsbReceiver()
        val usb = context.getSystemService(UsbManager::class.java)
        log("Android sees ${usb.deviceList.size} USB device(s) (SDK ${Build.VERSION.SDK_INT})")
        usb.deviceList.values.forEach { log("  " + it.describe()) }
    }

    fun requestUsbPermission() {
        ensureUsbReceiver()
        val usb = context.getSystemService(UsbManager::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val intent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags)
        usb.deviceList.values.filter { !usb.hasPermission(it) }.forEach {
            log("requesting USB permission for ${it.deviceName}")
            usb.requestPermission(it, intent)
        }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, i: Intent) {
            if (i.action != BluetoothDevice.ACTION_FOUND) return
            val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            log("BT classic found: ${d.name} [${d.address}] bond=${d.bondState}")
        }
    }

    @SuppressLint("MissingPermission")
    fun scanBluetooth() {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) { log("Bluetooth off or unavailable"); return }
        log("BT scan 12 s (classic + LE) - IH45 must be switched on and discoverable")
        try {
            context.registerReceiver(classicReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            adapter.startDiscovery()
            val leCallback = object : ScanCallback() {
                override fun onScanResult(type: Int, r: ScanResult) =
                    log("BT LE found: ${r.device.name ?: r.scanRecord?.deviceName} [${r.device.address}] rssi=${r.rssi}")
            }
            adapter.bluetoothLeScanner?.startScan(leCallback)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    adapter.cancelDiscovery()
                    adapter.bluetoothLeScanner?.stopScan(leCallback)
                    context.unregisterReceiver(classicReceiver)
                } catch (t: Throwable) { log("scan stop: ${t.message}") }
                log("BT scan finished")
            }, 12_000)
        } catch (t: SecurityException) {
            log("BT scan denied: ${t.message}")
        }
    }

    private companion object {
        const val ACTION_USB_PERMISSION = "de.ca.qcc.USB_PERMISSION"
        const val TAG = "QccIh45Probe"
        const val MAX_LINES = 300
    }
}
