package com.wonderplay.source

import com.wonderplay.domain.*
import com.wonderplay.metadata.MetadataResolver
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale

/** Public music radio supplies related songs, independently of search paging. */
class YouTubeRadio(private val http: SourceHttpClient = SourceHttpClient()) {
    suspend fun next(track: Track): List<Track> {
        val id = YouTubeMusicSource.validId(track.sourceId)
        val client = JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", "1.20260930.01.00").put("hl", "en").put("gl", Locale.getDefault().country.takeIf { it.length == 2 } ?: "US")
        val body = JSONObject().put("context", JSONObject().put("client", client)).put("videoId", id)
            .put("playlistId", "RDAMVM$id").put("isAudioOnly", true).put("enablePersistentPlaylistPanel", true)
        return parse(JSONObject(http.text("https://music.youtube.com/youtubei/v1/next".toHttpUrl(), body)
            ?: throw SourceException("Related songs are unavailable.")))
    }
    companion object {
        private fun text(value: JSONObject?) = value?.optJSONArray("runs")?.let { a -> (0 until a.length()).joinToString("") { a.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty()
        internal fun parse(root: JSONObject): List<Track> {
            val result = mutableListOf<Track>()
            fun visit(value: Any?, depth: Int) {
                if(depth > 35 || result.size >= 60) return
                when(value) {
                    is JSONObject -> {
                        value.optJSONObject("playlistPanelVideoRenderer")?.let { row ->
                            val id = row.optString("videoId")
                            val title = text(row.optJSONObject("title"))
                            val byline = row.optJSONObject("longBylineText")?.optJSONArray("runs")
                            fun typed(kind: String) = byline?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter {
                                it.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optJSONObject("browseEndpointContextSupportedConfigs")?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType") == kind
                            }.let { if(kind == "MUSIC_PAGE_TYPE_ARTIST") it.take(1) else it }.joinToString(", ") { it.optString("text") } }.orEmpty()
                            val artist = typed("MUSIC_PAGE_TYPE_ARTIST").ifBlank { text(row.optJSONObject("shortBylineText")).substringBefore(" • ") }
                            val parts = text(row.optJSONObject("lengthText")).split(':').map { it.toLongOrNull() }
                            val seconds = if(parts.size in 2..3 && parts.all { it != null }) parts.fold(0L) { sum, part -> sum * 60 + part!! } else 0L
                            val images = row.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val art = images?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.maxByOrNull { it.optInt("width") }?.optString("url") }?.takeIf { it.startsWith("https://") }
                            if(id.matches(Regex("[A-Za-z0-9_-]{11}")) && title.isNotBlank() && artist.isNotBlank() && seconds in 45..900)
                                result += MetadataResolver.normalize(Track("youtube:$id", title, artist, typed("MUSIC_PAGE_TYPE_ALBUM"), art, seconds * 1000, sourceId = id, permalink = "https://music.youtube.com/watch?v=$id"))
                        }
                        value.keys().forEach { visit(value.opt(it), depth + 1) }
                    }
                    is JSONArray -> for(i in 0 until value.length()) visit(value.opt(i), depth + 1)
                }
            }
            visit(root.optJSONObject("contents"), 0)
            return result.distinctBy { it.id }
        }
    }
}
