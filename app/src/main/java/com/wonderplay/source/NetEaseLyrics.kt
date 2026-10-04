package com.wonderplay.source

import com.wonderplay.domain.Track
import com.wonderplay.metadata.MetadataResolver
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import kotlin.math.abs

/** Metadata-matched public lyrics only; never requests a music stream or a user account. */
class NetEaseLyrics(private val http: SourceHttpClient) {
    suspend fun find(track: Track): Lyrics? {
        val query = LyricsRepository.cleanTitle(track.title) + " " + LyricsRepository.cleanArtist(track.artist)
        val url = "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", query).addQueryParameter("type", "1").addQueryParameter("limit", "8").build()
        val records = http.jsonOrNull(url)?.optJSONObject("result")?.optJSONArray("songs") ?: return null
        val candidates = (0 until records.length()).mapNotNull { records.optJSONObject(it) }.filter { matches(track, it) }
            .sortedBy { abs(it.optLong("duration") - track.durationMs) }.take(2)
        for(record in candidates) {
            val id = record.optLong("id").takeIf { it > 0 } ?: continue
            val lyricUrl = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder().addQueryParameter("id", id.toString())
                .addQueryParameter("lv", "-1").addQueryParameter("tv", "-1").addQueryParameter("rv", "-1").build()
            http.jsonOrNull(lyricUrl)?.let(::decode)?.let { return it }
        }
        return null
    }
    companion object {
        internal fun matches(track: Track, record: JSONObject): Boolean {
            val artists = record.optJSONArray("artists") ?: return false
            val wanted = MetadataResolver.key(LyricsRepository.cleanArtist(track.artist))
            val artistMatch = (0 until artists.length()).any { MetadataResolver.key(artists.optJSONObject(it)?.optString("name").orEmpty()) == wanted }
            val duration = record.optLong("duration")
            return artistMatch && MetadataResolver.key(LyricsRepository.cleanTitle(track.title)) == MetadataResolver.key(record.optString("name")) &&
                (track.durationMs <= 0 || (duration > 0 && abs(duration - track.durationMs) <= 8000))
        }
        internal fun decode(root: JSONObject): Lyrics? {
            if(root.optInt("code", 200) != 200 || root.optBoolean("nolyric")) return null
            val lrc = root.optJSONObject("lrc")?.optString("lyric").orEmpty().take(300000).trim()
            val lines = LrcParser.parse(lrc)
            val plain = if(lines.isNotEmpty()) lines.joinToString("\n") { it.text } else lrc
            if(plain.isBlank()) return null
            val roman = root.optJSONObject("romalrc")?.optString("lyric").orEmpty().take(300000).trim()
            val romanLines = LrcParser.parse(roman)
            return Lyrics(plain, lines, provider = "NetEase", romanizedPlain = if(romanLines.isNotEmpty()) romanLines.joinToString("\n") { it.text } else roman,
                romanizedLines = romanLines, romanizationSource = if(roman.isNotBlank()) "NetEase" else null)
        }
    }
}
