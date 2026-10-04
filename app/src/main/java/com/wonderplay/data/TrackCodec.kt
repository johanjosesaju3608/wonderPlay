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
        )
    }
}
