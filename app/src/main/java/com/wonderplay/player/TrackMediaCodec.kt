package com.wonderplay.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.wonderplay.domain.Track
import org.json.JSONObject
import java.util.UUID

/** Metadata travels with each queue occurrence, including duplicate tracks. */
internal object TrackMediaCodec {
    private const val EXTRA_TRACK = "wonderplay.track"
    const val SCHEME = "wonderplay"

    fun encode(track: Track): String = JSONObject().apply {
        put("id", track.id); put("title", track.title); put("artist", track.artist)
        put("album", track.album); put("artworkUrl", track.artworkUrl)
        put("durationMs", track.durationMs); put("source", track.source)
        put("sourceId", track.sourceId); put("streamUrl", track.streamUrl)
        put("artistId", track.artistId); put("albumId", track.albumId)
        put("year", track.year); put("genre", track.genre)
        put("explicit", track.explicit); put("permalink", track.permalink)
        put("artists", org.json.JSONArray().apply { track.artists.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("artworkUrl",it.artworkUrl)) } })
    }.toString()

    fun decode(raw: String): Track {
        val json = JSONObject(raw)
        fun optional(key: String) = if (json.isNull(key)) null else json.optString(key).takeIf(String::isNotBlank)
        return Track(
            id = json.getString("id"), title = json.optString("title", "Untitled"),
            artist = json.optString("artist", "Unknown artist"), album = json.optString("album"),
            artworkUrl = optional("artworkUrl"), durationMs = json.optLong("durationMs"),
            source = json.optString("source", "youtube"), sourceId = json.optString("sourceId"),
            streamUrl = optional("streamUrl"), artistId = optional("artistId"),
            albumId = optional("albumId"), year = optional("year"), genre = optional("genre"),
            explicit = json.optBoolean("explicit"), permalink = optional("permalink"),
            artists = json.optJSONArray("artists")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { com.wonderplay.domain.ArtistRef(it.optString("id"),it.optString("name"),it.optString("artworkUrl").takeIf { url -> url.startsWith("https://") }) } } }.orEmpty(),
        )
    }

    fun item(track: Track, occurrenceId: String = UUID.randomUUID().toString()): MediaItem {
        val raw = encode(track)
        val uri = Uri.Builder().scheme(SCHEME).authority("track").appendPath(occurrenceId)
            .appendQueryParameter("metadata", raw).build()
        return MediaItem.Builder().setMediaId(occurrenceId).setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album)
                .setArtworkUri(track.artworkUrl?.let { com.wonderplay.metadata.ArtworkUrls.forSize(it, 1024) }?.let(Uri::parse))
                .setIsPlayable(true).setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { putString(EXTRA_TRACK, raw) }).build())
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .build()
    }

    fun track(item: MediaItem): Track? = runCatching {
        item.mediaMetadata.extras?.getString(EXTRA_TRACK)?.let(::decode)
            ?: item.requestMetadata.mediaUri?.getQueryParameter("metadata")?.let(::decode)
    }.getOrNull()

    fun playable(item: MediaItem): MediaItem {
        val track = track(item) ?: throw IllegalArgumentException("This queue item has no track metadata")
        return item(track, item.mediaId.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString())
    }
}
