package de.ca.qcc.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "mismatches")
data class MismatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val epc: String,
    val sgtin: String,
    val gtinTag: String,
    val gtinQr: String
)

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val gtinQr: String,
    val gtinTag: String,
    val isMatch: Boolean,
    val sgtinQr: String = "",
    val sgtinTag: String = "",
    val qrRaw: String = ""
)

@Entity(tableName = "duplicate_searches")
data class DuplicateSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val sgtin: String,
    val epc: String,
    val store: String? = null,
    val exportFileName: String? = null
)

@Entity(
    tableName = "duplicate_finds",
    foreignKeys = [
        ForeignKey(
            entity = DuplicateSearchEntity::class,
            parentColumns = ["id"],
            childColumns = ["searchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["searchId", "tid"], unique = true), Index(value = ["tid"])]
)
data class DuplicateFindEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val searchId: Long,
    val foundAt: Long,
    val tid: String,
    val findNo: Int,
    // When this TID was last found in an earlier search (null = first time seen)
    val previousFoundAt: Long? = null
)
