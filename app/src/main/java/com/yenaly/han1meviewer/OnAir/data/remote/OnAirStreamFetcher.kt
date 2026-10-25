package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import com.yenaly.han1meviewer.OnAir.data.model.StreamResult
import com.yenaly.han1meviewer.OnAir.data.parser.OnAirWatchParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

object OnAirStreamFetcher {

    private const val TAG = "OnAirStreamFetcher"

    private val streamQualityRegex = Regex("""/streamq/\d+/(\d+)""")
    private val hlsQualityRegex = Regex("""/hls/\d+/master\.m3u8""", RegexOption.IGNORE_CASE)
    private val originalStreamRegex =
        Regex("""/stream/[a-f0-9]{16,}(?:\.\w+)?(?:\?|$)""", RegexOption.IGNORE_CASE)

    suspend fun fetch(videoId: String, quality: String): StreamResult? {
        if (videoId.isBlank() || quality.isBlank()) return null
        val first = fetchOnce(videoId, quality)
        if (first != null) return first
        Log.w(TAG, "all sources dead for $videoId/$quality, retrying with fresh token")
        return fetchOnce(videoId, quality)
    }

    private suspend fun fetchOnce(videoId: String, quality: String): StreamResult? {
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

            if (videoEl != null) {
                val sources = videoEl.select("source")
                for (source in sources) {
                    val raw = source.attr("src").takeIf { it.isNotBlank() }
                        ?: source.attr("data-savepage-src").takeIf { it.isNotBlank() }
                        ?: continue

                    val fromUrl = qualityOf(raw)
                    val matches = when (quality) {
                        "original" -> fromUrl == null || fromUrl == "original"
                        else -> fromUrl == quality
                    }
                    if (!matches) continue

                    val abs = resolve(raw, base) ?: continue
                    val isHls = abs.contains(".m3u8", ignoreCase = true) ||
                            abs.contains("/hls/", ignoreCase = true)

                    if (!probeMediaUrl(abs)) {
                        Log.w(TAG, "source is dead (404/403) — skipping: $abs")
                        continue
                    }

                    val mime = source.attr("type").takeIf { it.isNotBlank() }
                        ?: guessMime(abs)
                    Log.d(TAG, "quality=$quality source: $abs (hls=$isHls)")
                    return StreamResult(
                        quality = quality,
                        url = abs,
                        mediaType = mime,
                        isHls = isHls,
                    )
                }
            }

            if (quality == "original" && videoEl != null) {
                val hlsAttr = videoEl.attr("data-hls-src").takeIf { it.isNotBlank() }
                if (hlsAttr != null) {
                    val abs = resolve(hlsAttr, base)
                    if (abs != null && probeMediaUrl(abs)) {
                        Log.d(TAG, "original HLS fallback: $abs")
                        return StreamResult(
                            quality = "original",
                            url = abs,
                            mediaType = "application/vnd.apple.mpegurl",
                            isHls = true,
                        )
                    } else {
                        Log.w(TAG, "original HLS fallback is dead: ${abs ?: hlsAttr}")
                    }
                }
            }

            val parsed = OnAirWatchParser.parseStreamFromPage(html, watchUrl, quality)
            if (parsed != null) {
                if (probeMediaUrl(parsed.url)) {
                    return parsed
                }
                Log.w(TAG, "parsed source is dead (404/403): ${parsed.url}")
                return null
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "fetchOnce($videoId, $quality) failed", e)
            null
        }
    }

    private suspend fun probeMediaUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val base = OnAirNetwork.baseUrl
            val cookie = OnAirCookieManager.cookiesRaw
            val req = Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=0-0")
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
        if (originalStreamRegex.containsMatchIn(url)) return "original"
        if (hlsQualityRegex.containsMatchIn(url) ||
            url.contains(".m3u8", ignoreCase = true)
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
