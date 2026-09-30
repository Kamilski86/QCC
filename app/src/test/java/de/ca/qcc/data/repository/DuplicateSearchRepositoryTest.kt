package de.ca.qcc.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.domain.model.LocateTarget
import de.ca.qcc.domain.model.RegisterFindResult
import de.ca.qcc.testutil.FakeCsvWriter
import de.ca.qcc.testutil.inMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DuplicateSearchRepositoryTest {

    // SGTIN encoded with the default filter value 1
    private val target = LocateTarget(
        epc = "3034F824285A980300000006",
        sgtin = "4065546.092768.12884901894"
    )

    private lateinit var db: AppDatabase
    private lateinit var writer: FakeCsvWriter
    private lateinit var repository: DuplicateSearchRepository

    @Before
    fun setUp() {
        db = inMemoryDb(ApplicationProvider.getApplicationContext())
        writer = FakeCsvWriter()
        repository = DuplicateSearchRepository(db, writer)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun registerFind_numbersFindsSequentially_andNormalizesTid() = runTest {
        val id = repository.startSearch(target, store = "0815")

        assertEquals(RegisterFindResult.Saved(1), repository.registerFind(id, "e280aa"))
        assertEquals(RegisterFindResult.Saved(2), repository.registerFind(id, "E280BB"))
    }

    @Test
    fun registerFind_knownTid_isAlreadyFound_evenInLaterSearch() = runTest {
        val first = repository.startSearch(target, store = null)
        repository.registerFind(first, "E280AA")
        repository.finishSearch(first)

        val second = repository.startSearch(target, store = null)

        assertEquals(RegisterFindResult.AlreadyFound(1), repository.registerFind(second, "e280aa"))
    }

    @Test
    fun getOpenSearch_restoresTargetStoreAndCount() = runTest {
        val id = repository.startSearch(target, store = "0815")
        repository.registerFind(id, "E280AA")

        val open = repository.getOpenSearch()!!

        assertEquals(id, open.id)
        assertEquals(target, open.target)
        assertEquals("0815", open.store)
        assertEquals(1, open.foundCount)
    }

    @Test
    fun finishSearch_withFinds_writesCsv_andClosesSearch() = runTest {
        val id = repository.startSearch(target, store = "0815")
        repository.registerFind(id, "E280AA")
        repository.registerFind(id, "E280BB")

        val fileName = repository.finishSearch(id)!!

        assertTrue(fileName.matches(Regex("""duplicates_\d{8}_\d{6}_\d+\.csv""")))
        val lines = writer.files.getValue(fileName).trim().lines()
        assertEquals("Timestamp,SGTIN,EPC,TID,Find No,Store", lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].endsWith(",4065546.092768.12884901894,3034F824285A980300000006,E280AA,1,0815"))
        assertTrue(lines[2].endsWith(",E280BB,2,0815"))
        assertNull(repository.getOpenSearch())
    }

    @Test
    fun finishSearch_withoutFinds_writesNoFile() = runTest {
        val id = repository.startSearch(target, store = null)

        assertNull(repository.finishSearch(id))
        assertTrue(writer.files.isEmpty())
        assertNull(repository.getOpenSearch())
    }

    @Test
    fun registerFind_sameTidTwiceInSameSearch_isAlreadyFound() = runTest {
        val id = repository.startSearch(target, store = null)

        assertEquals(RegisterFindResult.Saved(1), repository.registerFind(id, "E280AA"))
        assertEquals(RegisterFindResult.AlreadyFound(1), repository.registerFind(id, "e280aa"))
    }

    @Test
    fun startSearch_closesPreviouslyOpenSearch() = runTest {
        val first = repository.startSearch(target, store = null)
        val second = repository.startSearch(target, store = null)

        assertEquals(second, repository.getOpenSearch()!!.id)
        assertTrue(db.duplicateDao().getSearch(first)?.endedAt != null)
    }

    @Test
    fun finishSearch_twice_returnsSameFileName_andExportsOnce() = runTest {
        val id = repository.startSearch(target, store = null)
        repository.registerFind(id, "E280AA")

        val first = repository.finishSearch(id)
        val second = repository.finishSearch(id)

        assertEquals(first, second)
        assertEquals(1, writer.files.size)
    }

    @Test
    fun finishSearch_whenWriterFails_keepsSearchOpen_andCanBeRetried() = runTest {
        val id = repository.startSearch(target, store = null)
        repository.registerFind(id, "E280AA")
        writer.failNext = true

        var failed = false
        try {
            repository.finishSearch(id)
        } catch (e: java.io.IOException) {
            failed = true
        }

        assertTrue(failed)
        assertTrue(repository.getOpenSearch() != null)
        val fileName = repository.finishSearch(id)
        assertTrue(writer.files.containsKey(fileName))
        assertNull(repository.getOpenSearch())
    }

    @Test
    fun finishSearch_escapesStoreNameInCsv() = runTest {
        val id = repository.startSearch(target, store = "Store \"A\", B")
        repository.registerFind(id, "E280AA")

        val fileName = repository.finishSearch(id)!!

        assertTrue(writer.files.getValue(fileName).trim().lines()[1].endsWith(",\"Store \"\"A\"\", B\""))
    }

    @Test
    fun startSearch_withoutPriorSearch_opensSearch() = runTest {
        val id = repository.startSearch(target, store = null)

        assertTrue(id > 0)
        assertEquals(id, repository.getOpenSearch()!!.id)
    }
}
