package de.ca.qcc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MismatchEntity::class,
        ScanEntity::class,
        DuplicateSearchEntity::class,
        DuplicateFindEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mismatchDao(): MismatchDao
    abstract fun scanDao(): ScanDao
    abstract fun duplicateDao(): DuplicateDao
}
