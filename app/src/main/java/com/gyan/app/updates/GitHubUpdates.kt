package com.gyan.app.updates

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

data class AvailableUpdate(val version: String, val releaseUrl: String)

/** Checks the public GitHub Releases Atom feed; no token, SDK or app backend is used. */
object GitHubUpdates {
    private const val OWNER = "Het-Shah3011"
    private const val REPOSITORY = "Gyan"
    private const val FEED_URL = "https://github.com/$OWNER/$REPOSITORY/releases.atom"

    suspend fun latest(currentVersion: String): AvailableUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/atom+xml")
            setRequestProperty("User-Agent", "GYAN-Android")
        }
        try {
            if (connection.responseCode !in 200..299) error("GitHub returned ${connection.responseCode}")
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(connection.inputStream, "UTF-8")
            }
            var inEntry = false
            var title: String? = null
            var releaseUrl: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "entry" -> { inEntry = true; title = null; releaseUrl = null }
                        "title" -> if (inEntry) title = parser.nextText().trim()
                        "link" -> if (inEntry && (parser.getAttributeValue(null, "rel") == "alternate" || releaseUrl == null)) {
                            releaseUrl = parser.getAttributeValue(null, "href")
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) {
                        val version = title?.removePrefix("v")?.removePrefix("V")
                        if (!version.isNullOrBlank() && isNewer(version, currentVersion)) {
                            return@withContext AvailableUpdate(version, releaseUrl ?: "https://github.com/$OWNER/$REPOSITORY/releases/latest")
                        }
                        inEntry = false
                    }
                }
                event = parser.next()
            }
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String) = Regex("\\d+").findAll(value).mapNotNull { it.value.toLongOrNull() }.toList()
        val left = parts(remote)
        val right = parts(current)
        if (left.isEmpty() || right.isEmpty()) return remote != current
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0L }
            val b = right.getOrElse(index) { 0L }
            if (a != b) return a > b
        }
        return false
    }
}
