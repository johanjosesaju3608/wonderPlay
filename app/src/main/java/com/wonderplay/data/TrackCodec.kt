package com.wonderplay.data

import com.wonderplay.domain.Track
import org.json.JSONObject

/** Stable metadata-only storage; download state is maintained separately. */
internal object TrackCodec {
    fun encode(track: Track): String = JSONObject().apply {
        put("id", track.id); put("title", track.title); put("artist", track.artist)
        put("album", track.album); put("artworkUrl", track.artworkUrl)
        put("durationMs", track.durationMs); put("source", track.source)
        put("sourceId", track.sourceId); put("streamUrl", if (track.source == "local") track.streamUrl else null)
        put("artistId", track.artistId); put("albumId", track.albumId)
        put("year", track.year); put("genre", track.genre)
        put("explicit", track.explicit); put("permalink", track.permalink)
        put("artists", org.json.JSONArray().apply { track.artists.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("artworkUrl",it.artworkUrl)) } })
    }.toString()

    fun decode(value: String): Track = JSONObject(value).let { json ->
        fun optional(key: String): String? = json.optString(key).takeUnless { it.isBlank() || it == "null" }
        Track(
            id = json.getString("id"), title = json.optString("title", "Untitled"),
            artist = json.optString("artist", "Unknown artist"), album = json.optString("album"),
            artworkUrl = optional("artworkUrl"), durationMs = json.optLong("durationMs").coerceAtLeast(0),
            source = json.optString("source", "youtube"), sourceId = json.getString("sourceId"),
            streamUrl = optional("streamUrl"), artistId = optional("artistId"), albumId = optional("albumId"),
            year = optional("year"), genre = optional("genre"), explicit = json.optBoolean("explicit"),
            permalink = optional("permalink"),
            artists = json.optJSONArray("artists")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { com.wonderplay.domain.ArtistRef(it.optString("id"),it.optString("name"),it.optString("artworkUrl").takeIf { url -> url.startsWith("https://") }) } } }.orEmpty(),
        )
    }
}
