package de.ca.qcc.feature.scan

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.data.repository.MismatchRepository
import de.ca.qcc.device.honeywell.BarcodeScannerGateway
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.domain.model.ComparisonStatus
import de.ca.qcc.domain.model.ReaderStatus
import de.ca.qcc.domain.usecase.CompareScansUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScanViewModelTest {

    private val validQr = "http://qr.c-a.com/01/04065546927682/21/12884901894"
    private val matchingEpc = "3014F824285A980300000006"
    // 24 Hex-Zeichen, aber kein SGTIN-96-Header
    private val nonSgtinEpc = "E28068940000500000000001"

    private class FakeBarcodeScanner : BarcodeScannerGateway {
        val scans = MutableSharedFlow<String>(extraBufferCapacity = 8)
        override fun observeScans(): Flow<String> = scans
        override suspend fun triggerScan() = Unit
        override suspend fun stopScan() = Unit
    }

    private class FakeRfidReader : RfidReaderGateway {
        val tags = MutableSharedFlow<String>(extraBufferCapacity = 8)
        override fun observeStatus(): Flow<ReaderStatus> = flowOf(ReaderStatus())
        override fun observeTagScans(): Flow<String> = tags
        override suspend fun discoverReaders(): List<String> = emptyList()
        override suspend fun connect(readerId: String): Result<Unit> = Result.success(Unit)
        override suspend fun disconnect() = Unit
        override suspend fun triggerInventory() = Unit
        override suspend fun setPower(level: Int): Result<Unit> = Result.success(Unit)
    }

    private lateinit var db: AppDatabase
    private lateinit var barcode: FakeBarcodeScanner
    private lateinit var rfid: FakeRfidReader
    private lateinit var viewModel: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val repository = MismatchRepository(db)
        barcode = FakeBarcodeScanner()
        rfid = FakeRfidReader()
        viewModel = ScanViewModel(app, barcode, rfid, CompareScansUseCase(repository), repository)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun scanCount() = db.scanDao().getAllForExport().size
    private suspend fun mismatchCount() = db.mismatchDao().observeLatest(100).first().size

    @Test
    fun invalidEpc_setsReadableError_andKeepsListeningForTags() = runTest {
        rfid.tags.emit(nonSgtinEpc)

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertNull(state.rfid)
        assertEquals(false, state.isScanning)

        rfid.tags.emit(matchingEpc)
        assertEquals(matchingEpc, viewModel.uiState.value.rfid?.epc)
    }

    @Test
    fun rfidFirstThenQr_matchesAndPersistsOnlyScan() = runTest {
        rfid.tags.emit(matchingEpc)
        barcode.scans.emit(validQr)

        assertEquals(ComparisonStatus.MATCH, viewModel.uiState.value.comparison.status)
        assertEquals(1, scanCount())
        assertEquals(0, mismatchCount())
    }

    @Test
    fun hardwareQrAfterCompletedCycle_startsNewCycleInsteadOfComparingStaleRfid() = runTest {
        barcode.scans.emit(validQr)
        rfid.tags.emit(matchingEpc)
        assertEquals(ComparisonStatus.MATCH, viewModel.uiState.value.comparison.status)

        barcode.scans.emit(validQr)

        val state = viewModel.uiState.value
        assertNotNull(state.qr)
        assertNull(state.rfid)
        assertEquals(ComparisonStatus.IDLE, state.comparison.status)
        assertEquals(1, scanCount())
    }

    @Test
    fun rfidAfterCompletedCycle_startsNewCycleInsteadOfComparingStaleQr() = runTest {
        barcode.scans.emit(validQr)
        rfid.tags.emit(matchingEpc)

        rfid.tags.emit(matchingEpc)

        val state = viewModel.uiState.value
        assertNull(state.qr)
        assertNotNull(state.rfid)
        assertEquals(ComparisonStatus.IDLE, state.comparison.status)
        assertEquals(1, scanCount())
    }

    @Test
    fun scansWhileSuspended_areIgnored_andResumeWorks() = runTest {
        viewModel.setSuspended(true)

        barcode.scans.emit(validQr)
        rfid.tags.emit(matchingEpc)

        assertNull(viewModel.uiState.value.qr)
        assertNull(viewModel.uiState.value.rfid)
        assertEquals(0, scanCount())

        viewModel.setSuspended(false)
        barcode.scans.emit(validQr)
        rfid.tags.emit(matchingEpc)

        assertEquals(ComparisonStatus.MATCH, viewModel.uiState.value.comparison.status)
        assertEquals(1, scanCount())
    }
}
