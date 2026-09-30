package de.ca.qcc.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CsvDocumentWriter {
    suspend fun write(fileName: String, content: String)
}

// Writes into the public Documents directory (MediaStore on API 29+)
class MediaStoreCsvDocumentWriter(private val context: Context) : CsvDocumentWriter {
    override suspend fun write(fileName: String, content: String): Unit = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            ) ?: throw IllegalStateException("MediaStore insert failed")

            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: throw IllegalStateException("Could not open output stream")
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                .also { if (!it.exists()) it.mkdirs() }
            java.io.File(dir, fileName).writeText(content)
        }
    }
}

fun String.csvEscape(): String {
    val needsQuoting = contains(',') || contains('"') || contains('\n') || contains('\r')
    return if (needsQuoting) "\"${replace("\"", "\"\"")}\"" else this
}
