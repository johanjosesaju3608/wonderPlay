package com.wonderplay.source

import com.wonderplay.domain.Track
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val plain: String = "", val lines: List<LyricLine> = emptyList(), val provider: String = "LRCLIB", val instrumental: Boolean = false, val romanizedPlain: String = "", val romanizedLines: List<LyricLine> = emptyList(), val romanizationSource: String? = null)
data class LyricsState(val trackId: String? = null, val loading: Boolean = false, val lyrics: Lyrics? = null, val message: String? = null)

object LrcParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    fun parse(text: String): List<LyricLine> {
        val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return text.take(300000).lineSequence().take(6000).flatMap { line ->
            val words = line.replace(stamp, "").trim()
            stamp.findAll(line).mapNotNull { m ->
                val seconds = m.groupValues[2].toInt()
                if (seconds >= 60) null else LyricLine((m.groupValues[1].toLong() * 60000 + seconds * 1000 + m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull().orEmptyLong() + offset).coerceAtLeast(0), words)
            }
        }.sortedBy { it.timeMs }.distinct().take(5000).toList()
    }
    private fun Long?.orEmptyLong() = this ?: 0L
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0; var high = lines.lastIndex; var result = -1
        while (low <= high) { val mid = (low + high) / 2; if (lines[mid].timeMs <= positionMs) { result = mid; low = mid + 1 } else high = mid - 1 }
        return result
    }
}

/** Metadata-only requests, cancelled when the current track changes; no playback dependency. */
class LyricsRepository(private val http: SourceHttpClient = SourceHttpClient(okhttp3.OkHttpClient.Builder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(7, java.util.concurrent.TimeUnit.SECONDS).callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build())) {
    private val cache = object : LinkedHashMap<String, Lyrics>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?) = size > 40
    }
    private val health = LyricsProviderHealth()
    suspend fun find(track: Track): LyricsState {
        if(track.source == "local") return LyricsState(track.id)
        synchronized(cache) { cache[track.id] }?.let { return LyricsState(track.id, lyrics = it) }
        var failed = false
        try {
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", cleanTitle(track.title)).addQueryParameter("artist_name", cleanArtist(track.artist))
                .addQueryParameter("album_name", track.album).apply { if (track.durationMs > 0) addQueryParameter("duration", (track.durationMs / 1000).toString()) }.build()
            http.jsonOrNull(url)?.let(::decode)?.let { return remember(track, it) }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
        try {
            val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("track_name", cleanTitle(track.title)).addQueryParameter("artist_name", cleanArtist(track.artist)).build()
            val candidates = JSONArray(http.text(url) ?: "[]")
            val ranked = (0 until candidates.length()).mapNotNull { candidates.optJSONObject(it) }.filter { matches(track, it) }
                .sortedWith(compareBy<JSONObject> { if (it.optString("syncedLyrics").let { s -> s.isNotBlank() && s != "null" }) 0 else 1 }.thenBy { abs(it.optDouble("duration", 0.0) - track.durationMs / 1000.0) })
            ranked.firstNotNullOfOrNull(::decode)?.let { return remember(track, it) }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
        // LRCLIB always gets first refusal. Only missing lyrics reach free fallbacks.
        for(provider in health.order()) {
            if(!health.available(provider)) { failed = true; continue }
            try {
                val lyrics = when(provider) {
                    "NetEase" -> NetEaseLyrics(http).find(track)
                    else -> {
                        val url = "https://api.lyrics.ovh/v1".toHttpUrl().newBuilder().addPathSegment(cleanArtist(track.artist)).addPathSegment(cleanTitle(track.title)).build()
                        val plain = http.jsonOrNull(url)?.optString("lyrics").orEmpty().take(100000).trim()
                        plain.takeIf { it.isNotBlank() }?.let { Lyrics(plain = it, provider = "lyrics.ovh") }
                    }
                }
                health.success(provider)
                if(lyrics != null) return remember(track, lyrics)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { health.failure(provider); failed = true }
        }
        return LyricsState(track.id, message = if (failed) "Couldn't check every lyrics source. Retry or search the web." else "No lyrics found in the available sources.")
    }
    private fun remember(track: Track, lyrics: Lyrics): LyricsState { synchronized(cache) { cache[track.id] = lyrics }; return LyricsState(track.id, lyrics = lyrics) }
    companion object {
        internal fun cleanTitle(value: String) = value.replace(Regex("\\s*[\\[(]from\\s+.*?[\\])]", RegexOption.IGNORE_CASE), "").replace(Regex("\\s*[\\[(](?:official.*|lyrics?|audio|.*music video.*)[\\])]", RegexOption.IGNORE_CASE), "").trim()
        internal fun cleanArtist(value: String) = value.removeSuffix(" - Topic").trim()
        private fun normalized(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
        internal fun matches(track: Track, record: JSONObject): Boolean {
            val duration = record.optDouble("duration", 0.0)
            return normalized(cleanTitle(track.title)) == normalized(record.optString("trackName")) &&
                normalized(cleanArtist(track.artist)) == normalized(record.optString("artistName")) &&
                (track.durationMs <= 0 || duration <= 0 || abs(duration - track.durationMs / 1000.0) <= 8)
        }
        internal fun decode(record: JSONObject): Lyrics? {
            fun value(key: String) = record.optString(key).take(300000).takeUnless { it == "null" }.orEmpty().trim()
            val lines = LrcParser.parse(value("syncedLyrics")); val plain = value("plainLyrics")
            val instrumental = record.optBoolean("instrumental")
            return if (lines.isNotEmpty() || plain.isNotBlank() || instrumental) Lyrics(plain, lines, instrumental = instrumental) else null
        }
    }
}
