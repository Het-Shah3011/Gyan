package com.gyan.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GYAN_AD_CONFIG_URL =
    "https://raw.githubusercontent.com/Het-Shah3011/Gyan/main/ads.json"

private data class GyanAd(
    val title: String,
    val description: String,
    val imageUrl: String,
    val link: String,
    val buttonText: String
)

private object GyanAdFeed {
    @Volatile private var cachedAds: List<GyanAd> = emptyList()
    @Volatile private var fetchedAtMillis: Long = 0L

    suspend fun load(): List<GyanAd> = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - fetchedAtMillis < 30 * 60 * 1000L) return@withContext cachedAds

        val connection = (URL(GYAN_AD_CONFIG_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GYAN-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                fetchedAtMillis = System.currentTimeMillis()
                return@withContext cachedAds
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.length > 256_000) return@withContext cachedAds
            val root = JSONObject(body)
            if (root.optInt("schemaVersion") != 1) return@withContext cachedAds
            val array = root.optJSONArray("ads")
            val parsed = buildList {
                if (array != null) for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    if (!item.optBoolean("enabled", false)) continue
                    val title = item.optString("title").trim().take(80)
                    val link = item.optString("link").trim()
                    if (title.isBlank() || !link.startsWith("https://", ignoreCase = true)) continue
                    val image = item.optString("imageUrl").trim()
                    add(GyanAd(
                        title = title,
                        description = item.optString("description").trim().take(140),
                        imageUrl = image.takeIf { it.startsWith("https://", ignoreCase = true) }.orEmpty(),
                        link = link,
                        buttonText = item.optString("buttonText", "Open").trim().take(18).ifBlank { "Open" }
                    ))
                }
            }
            cachedAds = parsed
            fetchedAtMillis = System.currentTimeMillis()
            parsed
        } catch (_: Exception) {
            fetchedAtMillis = System.currentTimeMillis()
            cachedAds
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
fun GyanAdCard(modifier: Modifier = Modifier) {
    val ads by produceState(initialValue = emptyList<GyanAd>()) { value = GyanAdFeed.load() }
    if (ads.isEmpty()) return

    val ad = remember(ads) {
        val dayIndex = (System.currentTimeMillis() / 86_400_000L).rem(ads.size).toInt()
        ads[dayIndex]
    }
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = ad.imageUrl) {
        value = if (ad.imageUrl.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(ad.imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                }
                try {
                    if (connection.responseCode !in 200..299 || connection.contentLengthLong > 4_000_000L) null
                    else connection.inputStream.use { stream -> BitmapFactory.decodeStream(stream) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
    val openAd = remember(ad.link, context) {
        {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.link))) }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = openAd),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Row(
            Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap != null) {
                Image(bitmap!!.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
            } else {
                Surface(modifier = Modifier.size(58.dp), shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("AD", color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("SPONSORED", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(ad.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ad.description.isNotBlank()) Text(ad.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(ad.buttonText, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
