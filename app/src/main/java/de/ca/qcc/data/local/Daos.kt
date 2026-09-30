package de.ca.qcc.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MismatchDao {
    @Query("SELECT * FROM mismatches ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<MismatchEntity>>

    @Query("SELECT COUNT(*) FROM mismatches WHERE date(timestamp/1000, 'unixepoch', 'localtime') = date('now', 'localtime')")
    fun observeTodayCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MismatchEntity): Long
}

@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: ScanEntity): Long

    @Query("SELECT COUNT(*) FROM scans WHERE date(timestamp/1000, 'unixepoch', 'localtime') = date('now', 'localtime')")
    fun observeTodayCount(): Flow<Int>

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    suspend fun getAllForExport(): List<ScanEntity>
}

@Dao
interface DuplicateDao {
    @Insert
    suspend fun insertSearch(search: DuplicateSearchEntity): Long

    @Query("SELECT * FROM duplicate_searches WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getOpenSearch(): DuplicateSearchEntity?

    @Query("SELECT * FROM duplicate_searches WHERE id = :id")
    suspend fun getSearch(id: Long): DuplicateSearchEntity?

    @Query("UPDATE duplicate_searches SET endedAt = :endedAt, exportFileName = :fileName WHERE id = :id")
    suspend fun finishSearch(id: Long, endedAt: Long, fileName: String?)

    @Insert
    suspend fun insertFind(find: DuplicateFindEntity): Long

    @Query("SELECT * FROM duplicate_finds WHERE tid = :tid LIMIT 1")
    suspend fun findByTid(tid: String): DuplicateFindEntity?

    @Query("SELECT * FROM duplicate_finds WHERE searchId = :searchId ORDER BY findNo")
    suspend fun getFinds(searchId: Long): List<DuplicateFindEntity>

    @Query("SELECT COUNT(*) FROM duplicate_finds WHERE searchId = :searchId")
    suspend fun countFinds(searchId: Long): Int
}
