package de.ca.qcc.testutil

import android.content.Context
import androidx.room.Room
import de.ca.qcc.data.export.CsvDocumentWriter
import de.ca.qcc.data.local.AppDatabase

fun inMemoryDb(context: Context): AppDatabase =
    Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()

class FakeCsvWriter : CsvDocumentWriter {
    val files = mutableMapOf<String, String>()
    override suspend fun write(fileName: String, content: String) {
        files[fileName] = content
    }
}
