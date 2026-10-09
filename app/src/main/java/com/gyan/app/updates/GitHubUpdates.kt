package com.gyan.app.updates

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
    /** Direct URL to the APK asset, or null if not found */
    val apkDownloadUrl: String? = null
)

/** Checks the public GitHub Releases JSON API; no token required for public repos. */
object GitHubUpdates {
    private const val OWNER = "Het-Shah3011"
    private const val REPOSITORY = "Gyan"
    private const val API_URL =
        "https://api.github.com/repos/$OWNER/$REPOSITORY/releases/latest"

    suspend fun latest(currentVersion: String): AvailableUpdate? =
        withContext(Dispatchers.IO) {
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "GYAN-Android/$currentVersion")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    Log.w("GyanUpdates", "GitHub API returned ${conn.responseCode}")
                    return@withContext null
                }
                val body = conn.inputStream.bufferedReader().readText()
                val release = JSONObject(body)

                // Draft / pre-release -> ignore
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                    return@withContext null
                }

                val tagName = release.optString("tag_name", "").trimStart('v', 'V')
                if (tagName.isBlank()) return@withContext null
                if (!isNewer(tagName, currentVersion)) return@withContext null

                val htmlUrl = release.optString(
                    "html_url",
                    "https://github.com/$OWNER/$REPOSITORY/releases/latest"
                )

                // Find an APK asset - prefer "play" flavour, fall back to any .apk
                val assets: JSONArray = release.optJSONArray("assets") ?: JSONArray()
                var playApk: String? = null
                var anyApk: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        if (anyApk == null) anyApk = url
                        if (name.contains("play", ignoreCase = true)) playApk = url
                    }
                }
                val apkUrl = playApk ?: anyApk

                AvailableUpdate(tagName, htmlUrl, apkUrl)
            } finally {
                conn.disconnect()
            }
        }

    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) =
            Regex("\\d+").findAll(v).mapNotNull { it.value.toLongOrNull() }.toList()

        val l = parts(remote)
        val r = parts(current)
        if (l.isEmpty() || r.isEmpty()) return remote != current
        for (i in 0 until maxOf(l.size, r.size)) {
            val a = l.getOrElse(i) { 0L }
            val b = r.getOrElse(i) { 0L }
            if (a != b) return a > b
        }
        return false
    }
}