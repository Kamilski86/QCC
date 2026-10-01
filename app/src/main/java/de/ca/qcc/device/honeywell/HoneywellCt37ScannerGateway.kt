package de.ca.qcc.device.honeywell

import android.content.Context
import android.util.Log
import com.honeywell.aidc.AidcManager
import com.honeywell.aidc.BarcodeFailureEvent
import com.honeywell.aidc.BarcodeReadEvent
import com.honeywell.aidc.BarcodeReader
import com.honeywell.aidc.TriggerStateChangeEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

class HoneywellCt37ScannerGateway @Inject constructor(
    @ApplicationContext context: Context
) :
    BarcodeScannerGateway,
    BarcodeReader.BarcodeListener,
    BarcodeReader.TriggerListener {

    private val scanFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var aidcManager: AidcManager? = null
    private var barcodeReader: BarcodeReader? = null

    init {
        runCatching {
            AidcManager.create(context.applicationContext) { manager ->
                aidcManager = manager
                runCatching {
                    val reader = manager.createBarcodeReader()
                    reader.addBarcodeListener(this)
                    reader.addTriggerListener(this)
                    // CLIENT_CONTROL: hardware trigger fires onTriggerEvent,
                    // software button calls aim/light/decode directly.
                    // Both deliver results through onBarcodeEvent.
                    runCatching {
                        reader.setProperty(
                            BarcodeReader.PROPERTY_TRIGGER_CONTROL_MODE,
                            BarcodeReader.TRIGGER_CONTROL_MODE_CLIENT_CONTROL
                        )
                    }
                    barcodeReader = reader
                    // Claim immediately so the callback doesn't lose the window
                    // between AidcManager.create() and the first onResume().
                    runCatching { reader.claim() }
                }
            }
        }
    }

    override fun observeScans(): Flow<String> = scanFlow.asSharedFlow()

    override suspend fun triggerScan() {
        val reader = barcodeReader
        Log.d(TAG, "triggerScan (reader=${reader != null})")
        reader ?: throw IllegalStateException("Scanner nicht verfügbar")
        reader.aim(true)
        reader.light(true)
        reader.decode(true)
    }

    override suspend fun stopScan() {
        runCatching {
            barcodeReader?.aim(false)
            barcodeReader?.light(false)
            barcodeReader?.decode(false)
        }
    }

    fun claim() {
        runCatching { barcodeReader?.claim() }
    }

    fun release() {
        runCatching { barcodeReader?.release() }
    }

    fun close() {
        runCatching { barcodeReader?.close() }
        runCatching { aidcManager?.close() }
    }

    // Hardware trigger pressed/released → aim, illuminate, decode
    override fun onTriggerEvent(event: TriggerStateChangeEvent) {
        val reader = barcodeReader ?: return
        runCatching {
            reader.aim(event.state)
            reader.light(event.state)
            reader.decode(event.state)
        }
    }

    // Barcode decoded → stop scanner, emit data
    override fun onBarcodeEvent(event: BarcodeReadEvent) {
        Log.d(TAG, "barcode read: ${event.barcodeData?.take(40)}")
        runCatching {
            barcodeReader?.aim(false)
            barcodeReader?.light(false)
            barcodeReader?.decode(false)
        }
        event.barcodeData?.takeIf { it.isNotBlank() }?.let { scanFlow.tryEmit(it) }
    }

    override fun onFailureEvent(event: BarcodeFailureEvent) { Log.d(TAG, "barcode failure") }

    private companion object {
        const val TAG = "QccCt37"
    }
}
