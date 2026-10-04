package com.gyan.app.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.gyan.app.data.GyanRepository
import com.gyan.app.data.StudyFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileOrganizer {

    fun root(context: Context): File = File(context.filesDir, "gyan").apply { mkdirs() }
    fun inbox(context: Context): File = File(root(context), "inbox").apply { mkdirs() }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val idx = it.getColumnIndex("_display_name")
            if (idx >= 0 && it.moveToFirst()) return it.getString(idx)
        }
        return null
    }

    /** Copy a shared/opened document into the GYAN inbox and register it in the DB. */
    suspend fun importFromUri(context: Context, uri: Uri): StudyFileEntity? = withContext(Dispatchers.IO) {
        try {
            val name = queryDisplayName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val safeName = name.replace("/", "_")
            val dest = File(inbox(context), "${System.currentTimeMillis()}_$safeName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            val entity = StudyFileEntity(
                path = dest.absolutePath,
                displayName = safeName,
                category = "INBOX"
            )
            GyanRepository.get(context).dao.upsertFile(entity)
            entity
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Move a file from the inbox into Subject/Category storage. */
    suspend fun organize(context: Context, entity: StudyFileEntity, subjectId: Long?, category: String): Unit =
        withContext(Dispatchers.IO) {
            val src = File(entity.path)
            if (!src.exists()) return@withContext
            val folder = File(root(context), "${subjectId ?: 0}/${category.replace("/", "_")}").apply { mkdirs() }
            val dest = File(folder, src.name)
            if (src.absolutePath != dest.absolutePath) {
                src.copyTo(dest, overwrite = true)
                src.delete()
            }
            val updated = entity.copy(path = dest.absolutePath, subjectId = subjectId, category = category)
            GyanRepository.get(context).dao.upsertFile(updated)
        }

    suspend fun rename(context: Context, entity: StudyFileEntity, newName: String): Unit = withContext(Dispatchers.IO) {
        val src = File(entity.path)
        val dest = File(src.parentFile, newName.replace("/", "_"))
        if (src.renameTo(dest)) {
            GyanRepository.get(context).dao.deleteFile(entity)
            GyanRepository.get(context).dao.upsertFile(entity.copy(path = dest.absolutePath, displayName = dest.name))
        }
    }

    suspend fun delete(context: Context, entity: StudyFileEntity): Unit = withContext(Dispatchers.IO) {
        File(entity.path).delete()
        GyanRepository.get(context).dao.deleteFile(entity)
    }

    fun open(context: Context, entity: StudyFileEntity) {
        try {
            val file = File(entity.path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val ext = MimeTypeMap.getFileExtensionFromUrl(file.name)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                ?: "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
