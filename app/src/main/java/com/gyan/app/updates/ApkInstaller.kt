package com.gyan.app.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ApkInstaller {

    /**
     * Downloads the APK from [apkUrl] into cache/updates/gyan-update.apk and fires
     * the system install prompt.
     *
     * @param onProgress called with bytes-downloaded and total-bytes (-1 if unknown).
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDir, "gyan-update.apk")

        // Download
        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) {
                error("Server returned ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            var downloaded = 0L
            conn.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buf = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        // Trigger install on the main thread
        withContext(Dispatchers.Main) {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        }
    }
}