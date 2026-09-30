package de.ca.qcc.data.repository

import android.content.Context
import de.ca.qcc.data.export.MediaStoreCsvDocumentWriter
import de.ca.qcc.data.export.csvEscape
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.data.local.MismatchEntity
import de.ca.qcc.data.local.ScanEntity
import de.ca.qcc.domain.model.MismatchRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class MismatchRepository @Inject constructor(private val db: AppDatabase) {

    fun observeLatest(limit: Int): Flow<List<MismatchRecord>> = db.mismatchDao().observeLatest(limit).map { list ->
        list.map { MismatchRecord(it.id, it.timestamp, it.epc, it.sgtin, it.gtinTag, it.gtinQr) }
    }

    fun observeTodayCount(): Flow<Int> = db.mismatchDao().observeTodayCount()

    fun observeTodayScanCount(): Flow<Int> = db.scanDao().observeTodayCount()

    suspend fun add(record: MismatchRecord) {
        db.mismatchDao().insert(MismatchEntity(record.id, record.timestamp, record.epc, record.sgtin, record.gtinTag, record.gtinQr))
    }

    suspend fun addScan(gtinQr: String, gtinTag: String, isMatch: Boolean, sgtinQr: String, sgtinTag: String, qrRaw: String) {
        db.scanDao().insert(ScanEntity(
            timestamp = System.currentTimeMillis(),
            gtinQr = gtinQr,
            gtinTag = gtinTag,
            isMatch = isMatch,
            sgtinQr = sgtinQr,
            sgtinTag = sgtinTag,
            qrRaw = qrRaw
        ))
    }

    suspend fun exportPendingCsv(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val items = db.scanDao().getAllForExport()
            val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "scans_$fileTimestamp.csv"

            val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val csvContent = buildString {
                appendLine("Timestamp,SGTIN,SGTIN Tag,QR Raw,Status")
                items.forEach {
                    val rowTs = tsFormat.format(Date(it.timestamp))
                    val status = if (it.isMatch) "Match" else "Mismatch"
                    appendLine(
                        "$rowTs,${it.sgtinQr.csvEscape()},${it.sgtinTag.csvEscape()},${it.qrRaw.csvEscape()},${status.csvEscape()}"
                    )
                }
            }

            MediaStoreCsvDocumentWriter(context).write(fileName, csvContent)

            fileName
        }
    }
}
