package de.ca.qcc.feature.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ca.qcc.core.gs1.Gs1ParserService
import de.ca.qcc.data.local.StorePreferences
import de.ca.qcc.data.repository.DuplicateSearchRepository
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.device.zebra.TriggerMode
import de.ca.qcc.domain.model.LocateTarget
import de.ca.qcc.domain.model.RegisterFindResult
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

enum class DuplicatePhase { TARGET, SEARCH }

// Typed so the UI can localize; the ViewModel never builds user-visible text
sealed interface DuplicateMessage {
    data class Saved(val findNo: Int) : DuplicateMessage
    data class AlreadyFound(val findNo: Int) : DuplicateMessage
    data object NoTagAtReader : DuplicateMessage
    data object MultipleTags : DuplicateMessage
    data object CaptureNoTag : DuplicateMessage
    data object CaptureMultipleTags : DuplicateMessage
    data object InvalidTarget : DuplicateMessage
    data object ReaderNotConnected : DuplicateMessage
    data class ReaderError(val detail: String) : DuplicateMessage
    data class Exported(val fileName: String) : DuplicateMessage
    data object NothingExported : DuplicateMessage
    data class ExportFailed(val detail: String) : DuplicateMessage
}

data class DuplicateUiState(
    val phase: DuplicatePhase = DuplicatePhase.TARGET,
    val input: String = "",
    val sgtinFilter: Int = LocateTarget.DEFAULT_SGTIN_FILTER,
    val target: LocateTarget? = null,
    val store: String = "",
    val searchId: Long? = null,
    val foundCount: Int = 0,
    val lastTid: String? = null,
    val proximity: Int = 0,
    val locating: Boolean = false,
    val continuous: Boolean = false,
    val busy: Boolean = false,
    val readerConnected: Boolean = false,
    val message: DuplicateMessage? = null
)

@HiltViewModel
class DuplicateSearchViewModel @Inject constructor(
    private val rfid: RfidReaderGateway,
    private val repository: DuplicateSearchRepository,
    private val storePreferences: StorePreferences,
    private val feedback: ProximityFeedback
) : ViewModel() {

    private val parser = Gs1ParserService()

    // Serializes locate start/stop so a quick release cannot overtake a pending start
    private val locateMutex = Mutex()

    // Once false, late async work must not switch the reader out of QCC mode again
    private var screenActive = true

    private val _uiState = MutableStateFlow(DuplicateUiState(store = storePreferences.lastStore))
    val uiState: StateFlow<DuplicateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            rfid.observeStatus().collect { status ->
                _uiState.update { it.copy(readerConnected = status.isConnected) }
                if (!status.isConnected) {
                    val wasLocating = _uiState.value.locating
                    _uiState.update {
                        it.copy(
                            continuous = false,
                            message = if (wasLocating) DuplicateMessage.ReaderNotConnected else it.message
                        )
                    }
                    // Finds are already persisted
                    if (wasLocating) stopLocating()
                }
            }
        }
        viewModelScope.launch { rfid.observeProximity().collect(::onProximity) }
        viewModelScope.launch { rfid.observeHandheldTrigger().collect(::onTrigger) }
        viewModelScope.launch {
            repository.getOpenSearch()?.let { open ->
                _uiState.update {
                    it.copy(
                        phase = DuplicatePhase.SEARCH,
                        input = open.target.epc,
                        target = open.target,
                        store = open.store.orEmpty(),
                        searchId = open.id,
                        foundCount = open.foundCount
                    )
                }
            }
            applyTriggerMode()
        }
    }

    // ── Screen lifecycle ──

    fun onScreenStarted() {
        screenActive = true
        applyTriggerMode()
    }

    fun onScreenStopped() {
        screenActive = false
        _uiState.update { it.copy(continuous = false) }
        viewModelScope.launch { stopLocating() }
        rfid.releaseTriggerMode(this)
    }

    override fun onCleared() {
        feedback.release()
        rfid.releaseTriggerMode(this)
    }

    // ── Phase 1: target ──

    fun onInputChange(text: String) =
        _uiState.update { it.copy(input = text, target = null, message = null) }

    // Only affects typed SGTINs; a hex EPC carries its own filter value
    fun onSgtinFilterChange(filter: Int) =
        _uiState.update { it.copy(sgtinFilter = filter, target = null, message = null) }

    fun onStoreChange(text: String) = _uiState.update { it.copy(store = text) }

    fun applyInput() {
        val state = _uiState.value
        runCatching { parser.parseLocateTarget(state.input, state.sgtinFilter) }
            .onSuccess { target -> _uiState.update { it.copy(target = target, message = null) } }
            .onFailure { _uiState.update { it.copy(target = null, message = DuplicateMessage.InvalidTarget) } }
    }

    fun captureFromReader() {
        val state = _uiState.value
        if (state.busy) return
        if (!state.readerConnected) {
            _uiState.update { it.copy(message = DuplicateMessage.ReaderNotConnected) }
            return
        }
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            rfid.readEpcsNearby()
                .onSuccess { epcs ->
                    when (epcs.size) {
                        0 -> _uiState.update { it.copy(message = DuplicateMessage.CaptureNoTag) }
                        1 -> {
                            _uiState.update { it.copy(input = epcs.single()) }
                            applyInput()
                        }
                        else -> _uiState.update { it.copy(message = DuplicateMessage.CaptureMultipleTags) }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(message = e.toReaderError()) } }
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun startSearch() {
        val state = _uiState.value
        val target = state.target ?: return
        if (state.busy || state.phase != DuplicatePhase.TARGET) return
        if (!state.readerConnected) {
            _uiState.update { it.copy(message = DuplicateMessage.ReaderNotConnected) }
            return
        }
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val store = state.store.trim()
            try {
                storePreferences.lastStore = store
                val searchId = repository.startSearch(target, store.ifBlank { null })
                _uiState.update {
                    it.copy(
                        phase = DuplicatePhase.SEARCH,
                        store = store,
                        searchId = searchId,
                        foundCount = 0,
                        lastTid = null,
                        busy = false,
                        message = null
                    )
                }
                applyTriggerMode()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, message = e.toReaderError()) }
            }
        }
    }

    // ── Phase 2: search ──

    fun setContinuous(enabled: Boolean) {
        val state = _uiState.value
        if (state.busy || state.phase != DuplicatePhase.SEARCH) return
        _uiState.update { it.copy(continuous = enabled) }
        viewModelScope.launch { if (enabled) startLocating() else stopLocating() }
    }

    fun confirmFind() {
        val state = _uiState.value
        val target = state.target ?: return
        val searchId = state.searchId ?: return
        if (state.busy) return
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            stopLocating()
            val message = rfid.readTidsNearby(target.epc, LocateTarget.FULL_MASK).fold(
                onSuccess = { tids ->
                    when (tids.size) {
                        0 -> DuplicateMessage.NoTagAtReader
                        1 -> registerFind(searchId, tids.single())
                        else -> DuplicateMessage.MultipleTags
                    }
                },
                onFailure = { it.toReaderError() }
            )
            _uiState.update { it.copy(busy = false, message = message) }
            if (_uiState.value.continuous) startLocating()
        }
    }

    fun endSearch() {
        val state = _uiState.value
        val searchId = state.searchId ?: return
        if (state.busy) return
        _uiState.update { it.copy(busy = true, continuous = false) }
        viewModelScope.launch {
            stopLocating()
            try {
                val fileName = repository.finishSearch(searchId)
                val current = _uiState.value
                _uiState.value = DuplicateUiState(
                    sgtinFilter = current.sgtinFilter,
                    store = current.store,
                    readerConnected = current.readerConnected,
                    message = fileName?.let { DuplicateMessage.Exported(it) }
                        ?: DuplicateMessage.NothingExported
                )
                applyTriggerMode()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(busy = false, message = DuplicateMessage.ExportFailed(e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    // ── Internals ──

    private suspend fun registerFind(searchId: Long, tid: String): DuplicateMessage =
        when (val result = repository.registerFind(searchId, tid)) {
            is RegisterFindResult.Saved -> {
                _uiState.update { it.copy(foundCount = result.findNo, lastTid = tid.uppercase()) }
                DuplicateMessage.Saved(result.findNo)
            }
            is RegisterFindResult.AlreadyFound -> DuplicateMessage.AlreadyFound(result.findNo)
        }

    private fun onTrigger(pressed: Boolean) {
        if (!screenActive) return
        val state = _uiState.value
        when (state.phase) {
            DuplicatePhase.TARGET -> if (pressed) captureFromReader()
            DuplicatePhase.SEARCH -> if (!state.continuous && !state.busy) {
                viewModelScope.launch { if (pressed) startLocating() else stopLocating() }
            }
        }
    }

    private fun onProximity(value: Int) {
        if (!screenActive) return
        if (!_uiState.value.locating) return
        val proximity = value.coerceIn(0, 100)
        _uiState.update { it.copy(proximity = proximity) }
        feedback.update(proximity)
    }

    private suspend fun startLocating() = locateMutex.withLock {
        val state = _uiState.value
        val target = state.target ?: return@withLock
        // A late request (continuous toggle, confirm) must not revive locating after leaving
        if (!screenActive || state.locating || state.phase != DuplicatePhase.SEARCH) return@withLock
        _uiState.update { it.copy(locating = true) }
        rfid.startLocate(target.epc, LocateTarget.FULL_MASK).onFailure { e ->
            _uiState.update { it.copy(locating = false, continuous = false, message = e.toReaderError()) }
        }
    }

    // Not cancellable: a cancelled scope must still stop the reader and the feedback
    private suspend fun stopLocating() = locateMutex.withLock {
        if (!_uiState.value.locating) return@withLock
        _uiState.update { it.copy(locating = false, proximity = 0) }
        withContext(NonCancellable) {
            feedback.stop()
            rfid.stopLocate()
        }
    }

    private fun applyTriggerMode() {
        if (!screenActive) {
            rfid.releaseTriggerMode(this)
            return
        }
        rfid.claimTriggerMode(
            this,
            if (_uiState.value.phase == DuplicatePhase.SEARCH) TriggerMode.LOCATE else TriggerMode.CAPTURE
        )
    }

    private fun Throwable.toReaderError() =
        DuplicateMessage.ReaderError(message ?: javaClass.simpleName)
}
