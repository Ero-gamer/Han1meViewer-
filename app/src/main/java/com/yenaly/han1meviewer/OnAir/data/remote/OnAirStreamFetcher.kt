package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import com.yenaly.han1meviewer.OnAir.data.model.StreamResult
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirWatchParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object OnAirStreamFetcher {

    private const val TAG = "OnAirStreamFetcher"

    private val streamQualityRegex = Regex("""/streamq/\d+/(\d+)""")
    private val hlsQualityRegex = Regex("""/hls/\d+/master\.m3u8""", RegexOption.IGNORE_CASE)
    private val originalStreamRegex = Regex("""/stream/[a-f0-9]{16,}""", RegexOption.IGNORE_CASE)

    suspend fun fetch(videoId: String, quality: String): StreamResult? {
        if (videoId.isBlank() || quality.isBlank()) return null
        return try {
            val response = OnAirNetwork.service.getWatchPage(
                videoId = videoId,
                quality = quality,
            )
            if (response.code() == 404) return null
            val html = response.body()?.string().orEmpty()
            if (html.isBlank()) return null

            val base = OnAirNetwork.baseUrl
            val watchUrl = base.trimEnd('/') + "/watch/" + videoId +
                    "?quality=" + quality

            val doc = Jsoup.parse(html, watchUrl)
            val videoEl = doc.selectFirst("video#watch-video")
                ?: doc.selectFirst("video#watch-video-fallback")
                ?: doc.selectFirst("video")

            if (quality == "original" && videoEl != null) {
                val hlsAttr = videoEl.attr("data-hls-src").takeIf { it.isNotBlank() }
                if (hlsAttr != null) {
                    val abs = resolve(hlsAttr, base) ?: return null
                    if (!probeMediaUrl(abs)) {
                        Log.w(TAG, "original HLS is dead (404/403): $abs")
                        return null
                    }
                    Log.d(TAG, "original HLS: $abs")
                    return StreamResult(
                        quality = "original",
                        url = abs,
                        mediaType = "application/vnd.apple.mpegurl",
                        isHls = true,
                    )
                }
            }

            if (videoEl != null) {
                val sources = videoEl.select("source")
                for (source in sources) {
                    val raw = source.attr("src").takeIf { it.isNotBlank() }
                        ?: source.attr("data-savepage-src").takeIf { it.isNotBlank() }
                        ?: continue
                    val fromUrl = qualityOf(raw)
                    val matches = if (quality == "original") {
                        fromUrl == null || fromUrl == "original"
                    } else {
                        fromUrl == quality
                    }
                    if (!matches) continue
                    val abs = resolve(raw, base) ?: continue
                    if (abs.contains("/hls/", ignoreCase = true) ||
                        abs.contains(".m3u8", ignoreCase = true)
                    ) {
                        if (!probeMediaUrl(abs)) {
                            Log.w(TAG, "HLS source is dead (404/403): $abs")
                            continue
                        }
                    }
                    val mime = source.attr("type").takeIf { it.isNotBlank() }
                        ?: guessMime(abs)
                    Log.d(TAG, "quality=$quality source: $abs")
                    return StreamResult(
                        quality = quality,
                        url = abs,
                        mediaType = mime,
                        isHls = abs.contains(".m3u8"),
                    )
                }
            }

            val parsed = OnAirWatchParser.parseStreamFromPage(html, watchUrl, quality)
            if (parsed != null && parsed.isHls && !probeMediaUrl(parsed.url)) {
                Log.w(TAG, "parsed HLS is dead (404/403): ${parsed.url}")
                return null
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "fetch($videoId, $quality) failed", e)
            null
        }
    }

    private suspend fun probeMediaUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val base = OnAirNetwork.baseUrl
            val cookie = OnAirCookieManager.cookiesRaw
            val req = Request.Builder()
                .url(url)
                .head()
                .header("Referer", base)
                .header("Origin", base.trimEnd('/'))
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
                )
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .build()
            OnAirNetwork.sharedOkHttpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful || resp.code == 206
            }
        }.getOrDefault(false)
    }

    private fun qualityOf(url: String): String? {
        streamQualityRegex.find(url)?.let { return it.groupValues[1] }
        if (hlsQualityRegex.containsMatchIn(url) ||
            url.contains(".m3u8", ignoreCase = true) ||
            originalStreamRegex.containsMatchIn(url)
        ) {
            return "original"
        }
        return null
    }

    private fun resolve(raw: String, base: String): String? {
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "http:$raw"
            raw.startsWith("/") -> base.trimEnd('/') + raw
            else -> base.trimEnd('/') + "/" + raw
        }
    }

    private fun guessMime(url: String): String? = when {
        url.contains(".webm", ignoreCase = true) -> "video/webm"
        url.contains(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
        url.contains(".mp4", ignoreCase = true) -> "video/mp4"
        url.contains("/streamq/", ignoreCase = true) -> "video/mp4"
        else -> null
    }
}
