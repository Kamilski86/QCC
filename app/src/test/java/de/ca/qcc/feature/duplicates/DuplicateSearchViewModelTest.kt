package de.ca.qcc.feature.duplicates

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.data.local.StorePreferences
import de.ca.qcc.data.repository.DuplicateSearchRepository
import de.ca.qcc.device.zebra.TriggerMode
import de.ca.qcc.domain.model.LocateTarget
import de.ca.qcc.testutil.FakeCsvWriter
import de.ca.qcc.testutil.FakeDuplicateReader
import de.ca.qcc.testutil.FakeProximityFeedback
import de.ca.qcc.testutil.inMemoryDb
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DuplicateSearchViewModelTest {

    // SGTIN input with the default filter value 1 encodes to exactly this EPC
    private val epc = "3034F824285A980300000006"
    private val sgtin = "4065546.092768.12884901894"
    private val tid = "E2801191A503006B7A41A41F"

    private lateinit var db: AppDatabase
    private lateinit var writer: FakeCsvWriter
    private lateinit var repository: DuplicateSearchRepository
    private lateinit var prefs: StorePreferences
    private lateinit var reader: FakeDuplicateReader
    private lateinit var feedback: FakeProximityFeedback

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = inMemoryDb(app)
        writer = FakeCsvWriter()
        repository = DuplicateSearchRepository(db, writer)
        prefs = StorePreferences(app)
        reader = FakeDuplicateReader()
        feedback = FakeProximityFeedback()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DuplicateSearchViewModel(reader, repository, prefs, feedback)

    private fun DuplicateSearchViewModel.startSearchFor(input: String) {
        onInputChange(input)
        applyInput()
        startSearch()
    }

    @Test
    fun init_usesCaptureMode_andPrefillsLastStore() {
        prefs.lastStore = "0815"

        val vm = createViewModel()

        assertEquals(TriggerMode.CAPTURE, reader.mode)
        assertEquals("0815", vm.uiState.value.store)
        assertEquals(DuplicatePhase.TARGET, vm.uiState.value.phase)
    }

    @Test
    fun triggerInTargetPhase_withOneTag_setsTarget() = runTest {
        reader.epcsNearby = Result.success(listOf(epc))
        val vm = createViewModel()

        reader.trigger.emit(true)

        assertEquals(epc, vm.uiState.value.target?.epc)
        assertEquals(sgtin, vm.uiState.value.target?.sgtin)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun capture_withSeveralTags_asksToIsolate() {
        reader.epcsNearby = Result.success(listOf(epc, "3034F824285A980300000007"))
        val vm = createViewModel()

        vm.captureFromReader()

        assertNull(vm.uiState.value.target)
        assertEquals(DuplicateMessage.CaptureMultipleTags, vm.uiState.value.message)
    }

    @Test
    fun capture_withoutTag_reportsNoTag() {
        val vm = createViewModel()

        vm.captureFromReader()

        assertEquals(DuplicateMessage.CaptureNoTag, vm.uiState.value.message)
    }

    @Test
    fun invalidInput_showsInvalidTarget() {
        val vm = createViewModel()

        vm.onInputChange("not-a-target")
        vm.applyInput()

        assertNull(vm.uiState.value.target)
        assertEquals(DuplicateMessage.InvalidTarget, vm.uiState.value.message)
    }

    @Test
    fun sgtinInput_withDefaultFilter_encodesFilterOne() {
        val vm = createViewModel()

        vm.onInputChange(sgtin)
        vm.applyInput()

        assertEquals(LocateTarget.DEFAULT_SGTIN_FILTER, vm.uiState.value.sgtinFilter)
        assertEquals("3034F824285A980300000006", vm.uiState.value.target?.epc)
        assertEquals(sgtin, vm.uiState.value.target?.sgtin)
    }

    @Test
    fun sgtinInput_withFilterZero_encodesFilterZero() {
        val vm = createViewModel()

        vm.onSgtinFilterChange(0)
        vm.onInputChange(sgtin)
        vm.applyInput()

        assertEquals(0, vm.uiState.value.sgtinFilter)
        assertEquals("3014F824285A980300000006", vm.uiState.value.target?.epc)
    }

    @Test
    fun changingSgtinFilter_reencodesTypedSgtin() {
        val vm = createViewModel()
        vm.onInputChange(sgtin)
        assertEquals("3034F824285A980300000006", vm.uiState.value.target?.epc)

        vm.onSgtinFilterChange(2)

        assertEquals("3054F824285A980300000006", vm.uiState.value.target?.epc)
        assertNull(vm.uiState.value.message)
        assertEquals(2, vm.uiState.value.sgtinFilter)
    }

    @Test
    fun typingValidInput_setsTargetWithoutApply() {
        val vm = createViewModel()

        vm.onInputChange(sgtin)

        assertEquals(epc, vm.uiState.value.target?.epc)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun typingIncompleteInput_hasNoTarget_andNoErrorWhileTyping() {
        val vm = createViewModel()
        vm.onInputChange(sgtin)

        vm.onInputChange("4065546.0927")

        assertNull(vm.uiState.value.target)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun startSearch_parsesTypedInputWithoutApply() {
        val vm = createViewModel()
        vm.onInputChange(sgtin)

        vm.startSearch()

        assertEquals(DuplicatePhase.SEARCH, vm.uiState.value.phase)
        assertEquals(epc, vm.uiState.value.target?.epc)
    }

    @Test
    fun startSearch_withInvalidInput_reportsInvalidTarget_andStaysInTargetPhase() {
        val vm = createViewModel()
        vm.onInputChange("not-a-target")

        vm.startSearch()

        assertEquals(DuplicatePhase.TARGET, vm.uiState.value.phase)
        assertEquals(DuplicateMessage.InvalidTarget, vm.uiState.value.message)
    }

    @Test
    fun onlyPlainSuccessMessages_areShownWithoutDialog() {
        val plain = listOf(
            DuplicateMessage.Saved(1),
            DuplicateMessage.Exported("a.csv"),
            DuplicateMessage.NothingExported
        )
        val needsOk = listOf(
            DuplicateMessage.AlreadyFound(1, tid, 0L),
            DuplicateMessage.SavedAgain(1, tid, 0L),
            DuplicateMessage.NoTagAtReader,
            DuplicateMessage.MultipleTags,
            DuplicateMessage.CaptureNoTag,
            DuplicateMessage.CaptureMultipleTags,
            DuplicateMessage.InvalidTarget,
            DuplicateMessage.ReaderNotConnected,
            DuplicateMessage.ReaderError("x"),
            DuplicateMessage.ExportFailed("x")
        )

        assertTrue(plain.none { it.requiresAcknowledgement })
        assertTrue(needsOk.all { it.requiresAcknowledgement })
    }

    @Test
    fun startSearch_switchesToLocateMode_andRemembersStore() {
        val vm = createViewModel()
        vm.onStoreChange(" 0815 ")

        vm.startSearchFor(sgtin)

        assertEquals(DuplicatePhase.SEARCH, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.searchId)
        assertEquals(TriggerMode.LOCATE, reader.mode)
        assertEquals("0815", prefs.lastStore)
    }

    @Test
    fun startSearch_withoutReader_isBlocked() {
        reader.status.value = reader.status.value.copy(isConnected = false)
        val vm = createViewModel()

        vm.startSearchFor(sgtin)

        assertEquals(DuplicatePhase.TARGET, vm.uiState.value.phase)
        assertEquals(DuplicateMessage.ReaderNotConnected, vm.uiState.value.message)
    }

    @Test
    fun holdingTrigger_locatesExactly_andDrivesFeedback() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(sgtin)

        reader.trigger.emit(true)
        assertEquals(epc to LocateTarget.FULL_MASK, reader.locateCalls.last())
        assertTrue(vm.uiState.value.locating)

        reader.proximity.emit(70)
        assertEquals(70, vm.uiState.value.proximity)
        assertEquals(listOf(70), feedback.updates)

        reader.trigger.emit(false)
        assertFalse(vm.uiState.value.locating)
        assertFalse(reader.locating)
        assertEquals(0, vm.uiState.value.proximity)
        assertTrue(feedback.stopCount > 0)
    }

    @Test
    fun continuousSearch_runsWithoutTrigger_andIgnoresRelease() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(epc)

        vm.setContinuous(true)
        assertTrue(reader.locating)
        assertEquals(epc to LocateTarget.FULL_MASK, reader.locateCalls.last())

        reader.trigger.emit(false)
        assertTrue(vm.uiState.value.locating)
    }

    @Test
    fun readerDisconnect_duringSearch_stopsLocating() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        vm.setContinuous(true)

        reader.status.value = reader.status.value.copy(isConnected = false)

        assertFalse(vm.uiState.value.locating)
        assertFalse(vm.uiState.value.continuous)
        assertEquals(DuplicateMessage.ReaderNotConnected, vm.uiState.value.message)
    }

    @Test
    fun confirmFind_handlesAllOutcomes() {
        val vm = createViewModel()
        vm.startSearchFor(epc)

        reader.tidsNearby = Result.success(emptyList())
        vm.confirmFind()
        assertEquals(DuplicateMessage.NoTagAtReader, vm.uiState.value.message)

        reader.tidsNearby = Result.success(listOf("T1", "T2"))
        vm.confirmFind()
        assertEquals(DuplicateMessage.MultipleTags, vm.uiState.value.message)
        assertEquals(0, vm.uiState.value.foundCount)

        reader.tidsNearby = Result.success(listOf(tid.lowercase()))
        vm.confirmFind()
        assertEquals(DuplicateMessage.Saved(1), vm.uiState.value.message)
        assertEquals(1, vm.uiState.value.foundCount)
        assertEquals(tid, vm.uiState.value.lastTid)

        vm.confirmFind()
        val message = vm.uiState.value.message as DuplicateMessage.AlreadyFound
        assertEquals(1, message.findNo)
        assertEquals(tid, message.tid)
        assertEquals(1, vm.uiState.value.foundCount)
        assertEquals(tid, vm.uiState.value.lastTid)

        reader.tidsNearby = Result.failure(IllegalStateException("boom"))
        vm.confirmFind()
        assertEquals(DuplicateMessage.ReaderError("boom"), vm.uiState.value.message)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun confirmFind_inContinuousMode_resumesLocating() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        vm.setContinuous(true)
        reader.tidsNearby = Result.success(listOf(tid))

        vm.confirmFind()

        assertTrue(vm.uiState.value.locating)
        assertEquals(2, reader.locateCalls.size)
    }

    @Test
    fun endSearch_withFinds_exports_andReturnsToTargetPhase() {
        val vm = createViewModel()
        vm.onStoreChange("0815")
        vm.onSgtinFilterChange(3)
        vm.startSearchFor(epc)
        reader.tidsNearby = Result.success(listOf(tid))
        vm.confirmFind()

        vm.endSearch()

        val state = vm.uiState.value
        val fileName = writer.files.keys.single()
        assertEquals(DuplicateMessage.Exported(fileName), state.message)
        assertEquals(DuplicatePhase.TARGET, state.phase)
        assertNull(state.target)
        assertEquals("0815", state.store)
        assertEquals(3, state.sgtinFilter)
        assertEquals(TriggerMode.CAPTURE, reader.mode)
    }

    @Test
    fun endSearch_withoutFinds_exportsNothing() {
        val vm = createViewModel()
        vm.startSearchFor(epc)

        vm.endSearch()

        assertEquals(DuplicateMessage.NothingExported, vm.uiState.value.message)
        assertTrue(writer.files.isEmpty())
    }

    @Test
    fun openSearch_isResumedOnStart() = runTest {
        val target = LocateTarget(epc, sgtin)
        val id = repository.startSearch(target, store = "0815")
        repository.registerFind(id, tid)

        val vm = createViewModel()

        val state = vm.uiState.value
        assertEquals(DuplicatePhase.SEARCH, state.phase)
        assertEquals(id, state.searchId)
        assertEquals(1, state.foundCount)
        assertEquals("0815", state.store)
        assertEquals(TriggerMode.LOCATE, reader.mode)
    }

    @Test
    fun triggerAfterScreenStopped_doesNotStartLocating() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(sgtin)
        vm.onScreenStopped()
        val callsBefore = reader.locateCalls.size

        reader.trigger.emit(true)

        assertEquals(callsBefore, reader.locateCalls.size)
        assertFalse(vm.uiState.value.locating)
    }

    @Test
    fun screenStopped_stopsLocating_andRestoresQccMode() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        vm.setContinuous(true)

        vm.onScreenStopped()

        assertFalse(reader.locating)
        assertFalse(vm.uiState.value.continuous)
        assertEquals(TriggerMode.QCC, reader.mode)

        vm.onScreenStarted()
        assertEquals(TriggerMode.LOCATE, reader.mode)
    }

    @Test
    fun endSearch_exportFailure_keepsSearchOpen_andIsRetryable() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        reader.tidsNearby = Result.success(listOf(tid))
        vm.confirmFind()
        writer.failNext = true

        vm.endSearch()

        assertEquals(DuplicatePhase.SEARCH, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.busy)
        assertTrue(vm.uiState.value.message is DuplicateMessage.ExportFailed)

        vm.endSearch()

        assertEquals(DuplicatePhase.TARGET, vm.uiState.value.phase)
        assertEquals(1, writer.files.size)
    }

    @Test
    fun startLocateFailure_resetsLocatingAndContinuous() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        reader.startLocateResult = Result.failure(IllegalStateException("nope"))

        vm.setContinuous(true)

        assertFalse(vm.uiState.value.locating)
        assertFalse(vm.uiState.value.continuous)
        assertEquals(DuplicateMessage.ReaderError("nope"), vm.uiState.value.message)
    }

    @Test
    fun proximityWhileNotLocating_isIgnored() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(epc)

        reader.proximity.emit(50)

        assertEquals(0, vm.uiState.value.proximity)
        assertTrue(feedback.updates.isEmpty())
    }

    @Test
    fun releaseBeforeStartCompletes_endsNotLocating() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        val gate = CompletableDeferred<Unit>()
        reader.startGate = gate

        reader.trigger.emit(true)
        reader.trigger.emit(false)
        gate.complete(Unit)

        assertFalse(reader.locating)
        assertFalse(vm.uiState.value.locating)
    }

    @Test
    fun pressReleasePress_endsLocating() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        val gate = CompletableDeferred<Unit>()
        reader.startGate = gate

        reader.trigger.emit(true)
        reader.trigger.emit(false)
        reader.trigger.emit(true)
        gate.complete(Unit)

        assertTrue(reader.locating)
        assertTrue(vm.uiState.value.locating)
    }

    @Test
    fun onCleared_restoresQccMode() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        assertEquals(TriggerMode.LOCATE, reader.mode)

        vm.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)

        assertEquals(TriggerMode.QCC, reader.mode)
    }

    @Test
    fun startSearchCompletingAfterScreenStopped_keepsQccMode() {
        val vm = createViewModel()
        vm.onInputChange(epc)
        vm.applyInput()

        vm.onScreenStopped()
        vm.startSearch()

        assertEquals(TriggerMode.QCC, reader.mode)
    }

    @Test
    fun setContinuous_ignoredInTargetPhase() {
        val vm = createViewModel()

        vm.setContinuous(true)

        assertFalse(vm.uiState.value.continuous)
        assertFalse(reader.locating)
    }

    @Test
    fun setContinuous_ignoredWhileBusy() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        val gate = CompletableDeferred<Unit>()
        reader.tidsGate = gate
        vm.confirmFind()
        assertTrue(vm.uiState.value.busy)

        vm.setContinuous(true)

        assertFalse(vm.uiState.value.continuous)
        gate.complete(Unit)
    }

    @Test
    fun startSearch_calledTwice_createsOneSearch() {
        val vm = createViewModel()
        vm.onInputChange(epc)
        vm.applyInput()

        vm.startSearch()
        val id = vm.uiState.value.searchId
        vm.startSearch()

        assertNotNull(id)
        assertEquals(id, vm.uiState.value.searchId)
    }

    @Test
    fun disconnectDuringConfirmFind_clearsContinuous() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        vm.setContinuous(true)
        reader.tidsNearby = Result.success(listOf(tid))

        reader.status.value = reader.status.value.copy(isConnected = false)
        vm.confirmFind()

        assertFalse(vm.uiState.value.continuous)
    }

    @Test
    fun staleViewModelStopping_doesNotResetModeOfNewerViewModel() {
        // Quick Duplicates -> Dashboard -> Duplicates: the old destination is torn down
        // after the new one has started and must not hand the trigger back to QCC
        val old = createViewModel()
        val current = createViewModel()
        current.onScreenStarted()

        old.onScreenStopped()

        assertEquals(TriggerMode.CAPTURE, reader.mode)
    }

    @Test
    fun screenStoppedTwice_doesNotResetModeOfNewerViewModel() {
        val old = createViewModel()
        old.onScreenStopped()
        val current = createViewModel()

        old.onScreenStopped() // ON_STOP and onDispose both report the same stop

        assertEquals(TriggerMode.CAPTURE, reader.mode)
        assertNotNull(current)
    }

    @Test
    fun continuousRequestedAfterScreenStopped_doesNotStartLocating() {
        val vm = createViewModel()
        vm.startSearchFor("4065546.092768.12884901894")
        vm.onScreenStopped()

        vm.setContinuous(true)

        assertFalse(reader.locating)
        assertTrue(reader.locateCalls.isEmpty())
    }

    @Test
    fun confirmFind_tidFromEarlierSearch_countsAndAsksForAcknowledgement() = runTest {
        val target = LocateTarget(epc, sgtin)
        val earlier = repository.startSearch(target, store = null)
        repository.registerFind(earlier, tid)
        val foundBefore = db.duplicateDao().getFinds(earlier).single().foundAt
        repository.finishSearch(earlier)
        val vm = createViewModel()
        vm.startSearchFor(epc)
        reader.tidsNearby = Result.success(listOf(tid))

        vm.confirmFind()

        val state = vm.uiState.value
        assertEquals(1, state.foundCount)
        assertEquals(tid, state.lastTid)
        assertEquals(DuplicateMessage.SavedAgain(1, tid, foundBefore), state.message)
        assertTrue(state.message!!.requiresAcknowledgement)
    }

    @Test
    fun pendingAcknowledgement_ignoresTrigger_untilDismissed() = runTest {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        reader.tidsNearby = Result.success(listOf(tid))
        vm.confirmFind()
        vm.confirmFind() // same tag again -> AlreadyFound dialog
        assertTrue(vm.uiState.value.message!!.requiresAcknowledgement)

        reader.trigger.emit(true)
        assertFalse(vm.uiState.value.locating)

        vm.onMessageDismissed()
        assertNull(vm.uiState.value.message)
        reader.trigger.emit(true)
        assertTrue(vm.uiState.value.locating)
    }

    @Test
    fun continuousMode_waitsForDismissBeforeLocatingAgain() {
        val vm = createViewModel()
        vm.startSearchFor(epc)
        reader.tidsNearby = Result.success(listOf(tid))
        vm.confirmFind()
        vm.setContinuous(true)
        vm.confirmFind() // AlreadyFound dialog opens
        assertFalse(vm.uiState.value.locating)

        vm.onMessageDismissed()

        assertTrue(vm.uiState.value.locating)
    }

    @Test
    fun resumedSearch_restoresLastTid() = runTest {
        val id = repository.startSearch(LocateTarget(epc, sgtin), store = null)
        repository.registerFind(id, tid)

        assertEquals(tid, createViewModel().uiState.value.lastTid)
    }
}
