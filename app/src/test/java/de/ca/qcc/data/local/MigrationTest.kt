package de.ca.qcc.data.local

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate6To7_addsDuplicateTables_andKeepsScans() {
        helper.createDatabase(DB_NAME, 6).apply {
            execSQL(
                "INSERT INTO scans (timestamp, gtinQr, gtinTag, isMatch, sgtinQr, sgtinTag, qrRaw) " +
                    "VALUES (1, 'a', 'b', 1, 'x', 'y', 'z')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 7, true, MIGRATION_6_7)

        db.query("SELECT COUNT(*) FROM scans").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.execSQL("INSERT INTO duplicate_searches (startedAt, sgtin, epc) VALUES (1, 's', 'e')")
        db.execSQL("INSERT INTO duplicate_finds (searchId, foundAt, tid, findNo) VALUES (1, 2, 'T1', 1)")
        db.query("SELECT COUNT(*) FROM duplicate_finds").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun migratedSchema_enforcesUniqueTidAndCascade() {
        helper.createDatabase(DB_NAME, 6).close()
        val db = helper.runMigrationsAndValidate(DB_NAME, 7, true, MIGRATION_6_7)

        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("INSERT INTO duplicate_searches (startedAt, sgtin, epc) VALUES (1, 's', 'e')")
        db.execSQL("INSERT INTO duplicate_finds (searchId, foundAt, tid, findNo) VALUES (1, 2, 'T1', 1)")

        db.execSQL("INSERT OR IGNORE INTO duplicate_finds (searchId, foundAt, tid, findNo) VALUES (1, 3, 'T1', 2)")
        assertEquals(1, count(db, "duplicate_finds"))

        var thrown = false
        try {
            db.execSQL("INSERT INTO duplicate_finds (searchId, foundAt, tid, findNo) VALUES (1, 3, 'T1', 2)")
        } catch (e: SQLiteConstraintException) {
            thrown = true
        }
        assertTrue("duplicate TID must violate the unique index", thrown)

        db.execSQL("DELETE FROM duplicate_searches")
        assertEquals(0, count(db, "duplicate_finds"))
    }

    @Test
    fun migrate7To8_keepsFinds_allowsSameTidInOtherSearch_butNotTwiceInOne() {
        helper.createDatabase(DB_NAME, 7).apply {
            execSQL("INSERT INTO duplicate_searches (startedAt, sgtin, epc) VALUES (1, 's', 'e')")
            execSQL("INSERT INTO duplicate_finds (searchId, foundAt, tid, findNo) VALUES (1, 2, 'T1', 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 8, true, MIGRATION_7_8)

        assertEquals(1, count(db, "duplicate_finds"))
        db.query("SELECT previousFoundAt FROM duplicate_finds").use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
        }
        db.execSQL("INSERT INTO duplicate_searches (startedAt, sgtin, epc) VALUES (3, 's', 'e')")
        db.execSQL("INSERT INTO duplicate_finds (searchId, foundAt, tid, findNo, previousFoundAt) VALUES (2, 4, 'T1', 1, 2)")
        assertEquals(2, count(db, "duplicate_finds"))

        var thrown = false
        try {
            db.execSQL("INSERT INTO duplicate_finds (searchId, foundAt, tid, findNo) VALUES (2, 5, 'T1', 2)")
        } catch (e: SQLiteConstraintException) {
            thrown = true
        }
        assertTrue("same TID twice in one search must violate the unique index", thrown)
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
