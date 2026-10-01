package de.ca.qcc.feature.scan

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ca.qcc.core.gs1.Gs1ParserService
import de.ca.qcc.data.repository.MismatchRepository
import de.ca.qcc.device.honeywell.BarcodeScannerGateway
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.domain.model.ComparisonResult
import de.ca.qcc.domain.model.ComparisonStatus
import de.ca.qcc.domain.model.QrScanResult
import de.ca.qcc.domain.model.ReaderStatus
import de.ca.qcc.domain.model.RfidScanResult
import de.ca.qcc.domain.usecase.CompareScansUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val qr: QrScanResult? = null,
    val rfid: RfidScanResult? = null,
    val comparison: ComparisonResult = ComparisonResult(
        ComparisonStatus.IDLE,
        message = "Bereit"
    ),
    val readerStatus: ReaderStatus = ReaderStatus(),
    val scansToday: Int = 0,
    val error: String? = null,
    val manualQrConfirmed: Boolean = false,
    val isScanning: Boolean = false,
    val readerPower: Float = 1f
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    application: Application,
    private val barcodeScanner: BarcodeScannerGateway,
    private val rfidReader: RfidReaderGateway,
    private val compareScansUseCase: CompareScansUseCase,
    mismatchRepository: MismatchRepository
) : AndroidViewModel(application) {

    private fun Throwable.toReadableError(): String {
        return buildString {
            append(this@toReadableError.javaClass.simpleName)
            this@toReadableError.message?.takeIf { it.isNotBlank() }?.let {
                append(": ")
                append(it)
            }
        }
    }

    private val parser = Gs1ParserService()
    private var tone: ToneGenerator? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getApplication<Application>().getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun getTone(): ToneGenerator? {
        return try {
            tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).also { tone = it }
        } catch (_: RuntimeException) { null }
    }

    override fun onCleared() {
        super.onCleared()
        tone?.release()
        tone = null
    }

    // Set while another use case (duplicate finder) owns the scanners; QCC then ignores their events
    @Volatile private var suspended = false

    fun setSuspended(value: Boolean) { suspended = value }

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            barcodeScanner.observeScans().collect { raw ->
                if (!suspended) handleQr(raw)
            }
        }

        viewModelScope.launch {
            rfidReader.observeTagScans().collect { epc ->
                if (!suspended) handleRfid(epc)
            }
        }

        viewModelScope.launch {
            rfidReader.observeStatus().collect { status ->
                _uiState.value = _uiState.value.copy(readerStatus = status)
            }
        }

        viewModelScope.launch {
            mismatchRepository.observeTodayScanCount().collect { count ->
                _uiState.value = _uiState.value.copy(scansToday = count)
            }
        }

        // NEW: Listen to Zebra trigger and start both scans
        viewModelScope.launch {
            rfidReader.observeTriggerEvents().collect {
                if (suspended) return@collect
                // Zebra trigger pressed - start both QR and RFID scanning
                triggerQrScan()
                triggerRfidScan()
            }
        }

        viewModelScope.launch {
            runCatching {
                val readers = rfidReader.discoverReaders()
                if (readers.isNotEmpty()) {
                    rfidReader.connect(readers.first())
                        .onSuccess { rfidReader.setPower(_uiState.value.readerPower.toInt()) }
                }
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(error = ex.toReadableError())
            }
        }
    }

    private fun startNewCycleIfNeeded() {
        val state = _uiState.value
        val hasCompletedCycle = state.qr != null && state.rfid != null
        val hasFinalResult = state.comparison.status != ComparisonStatus.IDLE

        if (hasCompletedCycle || hasFinalResult) {
            _uiState.value = state.copy(
                qr = null,
                rfid = null,
                comparison = ComparisonResult(
                    ComparisonStatus.IDLE,
                    message = "Starte neuen Scan"
                ),
                error = null,
                manualQrConfirmed = false
            )
        }
    }

    fun triggerQrScan() {
        viewModelScope.launch {
            startNewCycleIfNeeded()
            _uiState.value = _uiState.value.copy(isScanning = true)
            runCatching { barcodeScanner.triggerScan() }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.toReadableError()) }
            _uiState.value = _uiState.value.copy(isScanning = false)
        }
    }

    fun setManualQrGtin(gtin: String) {
        runCatching { parser.parseManualQrGtin(gtin) }
            .onSuccess { parsed ->
                _uiState.value = _uiState.value.copy(
                    qr = parsed,
                    rfid = null,
                    comparison = ComparisonResult(
                        ComparisonStatus.IDLE,
                        message = "Manuelle GTIN übernommen"
                    ),
                    error = null,
                    manualQrConfirmed = true
                )
                signalScan()
            }
            .onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    error = ex.message ?: "Ungültige manuelle GTIN",
                    manualQrConfirmed = false
                )
            }
    }

    fun triggerRfidScan() {
        viewModelScope.launch {
            startNewCycleIfNeeded()
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)
            runCatching { rfidReader.triggerInventory() }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        error = it.toReadableError(),
                        isScanning = false
                    )
                }
            // Result arrives via observeTagScans() → handleRfid()
        }
    }

    // Called for both hardware trigger and UI button results
    private fun handleRfid(epc: String) {
        val status = _uiState.value.readerStatus
        runCatching {
            parser.parseEpcToSgtin(
                epc = epc,
                readerName = status.readerName,
                battery = status.batteryPercent
            )
        }
            .onSuccess { parsed ->
                startNewCycleIfNeeded()
                _uiState.value = _uiState.value.copy(
                    rfid = parsed,
                    error = null,
                    isScanning = false
                )
                signalScan()
                compareIfReady()
            }
            .onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    error = ex.message ?: "RFID-Fehler",
                    isScanning = false
                )
            }
    }

    private fun handleQr(raw: String) {
        runCatching { parser.parseQr(raw) }
            .onSuccess { parsed ->
                // CT37 hardware key bypasses triggerQrScan(), so clear a finished cycle here
                startNewCycleIfNeeded()
                _uiState.value = _uiState.value.copy(
                    qr = parsed,
                    // rfid bleibt erhalten
                    comparison = ComparisonResult(
                        ComparisonStatus.IDLE,
                        message = "QR gelesen"
                    ),
                    error = null,
                    manualQrConfirmed = false
                )
                signalScan()
                compareIfReady()
            }
            .onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    error = ex.message ?: "QR-Fehler"
                )
            }
    }

    private fun compareIfReady() {
        val state = _uiState.value
        val qr = state.qr ?: return
        val rfid = state.rfid ?: return

        viewModelScope.launch {
            val comparison = compareScansUseCase(qr, rfid)
            _uiState.value = _uiState.value.copy(comparison = comparison)
            signalResult(comparison.status)

            // Stop the QR scanner after comparison result is displayed
            runCatching { barcodeScanner.stopScan() }
        }
    }

    fun reconnectReader() {
        viewModelScope.launch {
            runCatching {
                val readers = rfidReader.discoverReaders()

                if (readers.isEmpty()) {
                    throw IllegalStateException("Kein Zebra-Reader gefunden")
                }

                rfidReader.connect(readers.first()).getOrThrow()
                rfidReader.setPower(_uiState.value.readerPower.toInt()).getOrThrow()
            }.onSuccess {
                _uiState.value = _uiState.value.copy(error = null)
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    error = ex.toReadableError()
                )
            }
        }
    }

    fun setReaderPower(level: Float) {
        viewModelScope.launch {
            rfidReader.setPower(level.toInt())
                .onSuccess {
                    _uiState.value = _uiState.value.copy(readerPower = level)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        error = it.toReadableError()
                    )
                }
        }
    }

    fun reset() {
        _uiState.value = _uiState.value.copy(
            qr = null,
            rfid = null,
            comparison = ComparisonResult(
                ComparisonStatus.IDLE,
                message = "Bereit"
            ),
            error = null,
            manualQrConfirmed = false
        )

        // Stop the QR scanner when resetting
        viewModelScope.launch {
            runCatching { barcodeScanner.stopScan() }
        }
    }

    private fun signalScan() {
        getTone()?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        vibrate(60)
    }

    private fun signalResult(status: ComparisonStatus) {
        when (status) {
            ComparisonStatus.MATCH -> {
                getTone()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
                vibrate(100)
            }
            ComparisonStatus.MISMATCH -> {
                getTone()?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
                vibrate(250)
            }
            else -> Unit
        }
    }

    private fun vibrate(duration: Long) {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { /* ignore */ }
    }
}
