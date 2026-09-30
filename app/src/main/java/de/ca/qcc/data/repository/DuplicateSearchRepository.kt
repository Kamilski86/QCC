package de.ca.qcc.data.repository

import androidx.room.withTransaction
import de.ca.qcc.data.export.CsvDocumentWriter
import de.ca.qcc.data.export.csvEscape
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.data.local.DuplicateFindEntity
import de.ca.qcc.data.local.DuplicateSearchEntity
import de.ca.qcc.domain.model.DuplicateSearch
import de.ca.qcc.domain.model.LocateTarget
import de.ca.qcc.domain.model.RegisterFindResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class DuplicateSearchRepository @Inject constructor(
    private val db: AppDatabase,
    private val csvWriter: CsvDocumentWriter
) {
    private val dao get() = db.duplicateDao()

    // Only one search may be open at a time: a still-open predecessor is closed without export
    suspend fun startSearch(target: LocateTarget, store: String?): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        dao.getOpenSearch()?.let { dao.finishSearch(it.id, now, null) }
        dao.insertSearch(
            DuplicateSearchEntity(
                startedAt = now,
                sgtin = target.sgtin,
                epc = target.epc,
                store = store
            )
        )
    }

    suspend fun getOpenSearch(): DuplicateSearch? {
        val search = dao.getOpenSearch() ?: return null
        return DuplicateSearch(
            id = search.id,
            target = search.toTarget(),
            store = search.store,
            foundCount = dao.countFinds(search.id)
        )
    }

    // TID is globally unique: a tag found in any earlier search is reported, not counted again
    suspend fun registerFind(searchId: Long, tid: String): RegisterFindResult = db.withTransaction {
        val normalized = tid.uppercase()
        dao.findByTid(normalized)?.let { return@withTransaction RegisterFindResult.AlreadyFound(it.findNo) }
        val findNo = dao.countFinds(searchId) + 1
        val rowId = dao.insertFind(
            DuplicateFindEntity(
                searchId = searchId,
                foundAt = System.currentTimeMillis(),
                tid = normalized,
                findNo = findNo
            )
        )
        if (rowId != -1L) {
            RegisterFindResult.Saved(findNo)
        } else {
            // Lost a race against a concurrent registration of the same TID
            RegisterFindResult.AlreadyFound(checkNotNull(dao.findByTid(normalized)).findNo)
        }
    }

    // Idempotent. Returns the exported file name, or null when the search had no finds.
    // The CSV is written before the search is closed, so a failed export can be retried.
    suspend fun finishSearch(searchId: Long): String? {
        val search = dao.getSearch(searchId) ?: return null
        if (search.endedAt != null) return search.exportFileName
        val finds = dao.getFinds(searchId)
        val fileName = if (finds.isEmpty()) {
            null
        } else {
            val name = "duplicates_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_$searchId.csv"
            csvWriter.write(name, buildDuplicatesCsv(search, finds))
            name
        }
        dao.finishSearch(searchId, System.currentTimeMillis(), fileName)
        return fileName
    }

    // The stored EPC already carries the chosen filter value; locating is always exact
    private fun DuplicateSearchEntity.toTarget() = LocateTarget(epc = epc, sgtin = sgtin)
}

// Column headers stay English regardless of app language
internal fun buildDuplicatesCsv(search: DuplicateSearchEntity, finds: List<DuplicateFindEntity>): String {
    val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return buildString {
        appendLine("Timestamp,SGTIN,EPC,TID,Find No,Store")
        finds.forEach { find ->
            appendLine(
                listOf(
                    tsFormat.format(Date(find.foundAt)),
                    search.sgtin,
                    search.epc,
                    find.tid,
                    find.findNo.toString(),
                    search.store.orEmpty()
                ).joinToString(",") { it.csvEscape() }
            )
        }
    }
}
