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

    // Matches /streamq/{videoId}/{height}
    private val streamQualityRegex = Regex("""/streamq/\d+/(\d+)""")

    // Matches the HLS manifest path (only offered for "original")
    private val hlsQualityRegex = Regex("""/hls/\d+/master\.m3u8""", RegexOption.IGNORE_CASE)

    // Matches the progressive "original" file: /stream/{hash}.mp4 (hash is hex, 16+ chars)
    private val originalStreamRegex =
        Regex("""/stream/[a-f0-9]{16,}(?:\.\w+)?(?:\?|$)""", RegexOption.IGNORE_CASE)

    /**
     * Fetches the playable stream URL for [videoId] at [quality].
     *
     * Resolution strategy (per quality):
     *   1. Request the server-rendered watch page for that quality
     *      (/watch/{id}?quality={quality}).
     *   2. Prefer the first matching <source> element. That is what the site
     *      actually serves as the primary playback path for every quality:
     *        - 144p/240p/360p -> /streamq/{id}/{height}?t=...
     *        - original       -> /stream/{hash}.mp4?t=...
     *   3. ONLY for "original", if no working <source> was found, fall back to
     *      the optional adaptive HLS manifest exposed via data-hls-src.
     *      HLS is a bonus, not the canonical source; the HAR shows the
     *      progressive /stream/ and /streamq/ URLs are the real playback
     *      path and HLS is only advertised on the original-quality page.
     *   4. Last resort: whatever the fallback parser extracts.
     */
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

            // -------- 1. Prefer progressive <source> for every quality --------
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

                    // Only probe HLS; progressive MP4s are served inline by the
                    // player and we don't want to pay a round trip for them.
                    if (isHls && !probeMediaUrl(abs)) {
                        Log.w(TAG, "HLS <source> is dead (404/403): $abs")
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

            // -------- 2. For "original" only: optional HLS fallback --------
            // Per HAR: data-hls-src is only present on the original-quality
            // page. It's the adaptive manifest. Only try it if the progressive
            // /stream/{hash}.mp4 source above didn't pan out.
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

            // -------- 3. Last-resort parser --------
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

    /**
     * Lightweight reachability check for HLS manifests. Uses GET with a
     * one-byte Range instead of HEAD, because some servers (including Tor
     * hidden services) reply to HEAD with a misleading status but fail on
     * GET, and vice versa. Range: bytes=0-0 only pulls the first byte.
     */
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
                // 200 for full, 206 for partial content. Some servers return
                // 200 even with a Range header, so accept both.
                resp.isSuccessful || resp.code == 206
            }
        }.getOrDefault(false)
    }

    /**
     * Classifies a URL by quality:
     *   - "/streamq/{id}/{height}" -> the height (144/240/360/...)
     *   - "/stream/{hash}.mp4"     -> "original"
     *   - "/hls/{id}/master.m3u8"  -> "original"
     * Returns null when the URL doesn't match a known pattern (caller treats
     * that as "unknown", which only counts as a match for "original").
     */
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
