package de.ca.qcc.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.honeywell.rfidservice.TriggerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BondedDevice(val name: String, val address: String)

@HiltViewModel
class Ih45DiagnosticViewModel @Inject constructor(
    private val probe: Ih45Probe
) : ViewModel() {
    val log: StateFlow<List<String>> = probe.log

    private val bondedFlow = MutableStateFlow<List<BondedDevice>>(emptyList())
    val bonded: StateFlow<List<BondedDevice>> = bondedFlow.asStateFlow()

    // SDK calls can block (connect, reads) - never run them on the main thread
    private fun io(block: () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }

    fun init() = io(probe::init)
    fun usbDevices() = io(probe::usbDevices)
    fun requestUsbPermission() = io(probe::requestUsbPermission)
    fun scanBluetooth() = io(probe::scanBluetooth)
    fun state() = io(probe::state)
    fun attachedUsb() = io(probe::attachedUsb)
    fun connectUsb() = io(probe::connectUsb)
    fun connectBluetooth(address: String) = io { probe.connectBluetooth(address) }
    fun disconnect() = io(probe::disconnect)
    fun createReader() = io(probe::createReader)
    fun info() = io(probe::info)
    fun startRead(withTid: Boolean) = io { probe.startRead(withTid) }
    fun stopRead() = io(probe::stopRead)
    fun setPower(dbm: Int) = io { probe.setPower(dbm) }
    fun triggerRfid() = io { probe.setTriggerMode(TriggerMode.RFID) }
    fun triggerBarcode() = io { probe.setTriggerMode(TriggerMode.BARCODE) }
    fun clear() = probe.clear()

    fun refreshBonded() = io {
        bondedFlow.value = probe.bondedDevices().map { BondedDevice(it.name ?: "?", it.address) }
    }
}
