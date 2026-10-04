package com.wonderplay.domain

import kotlinx.coroutines.flow.Flow

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
    val source: String = "youtube",
    val sourceId: String = id.substringAfter(':'),
    val streamUrl: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val explicit: Boolean = false,
    val permalink: String? = null,
    val artists: List<ArtistRef> = emptyList(),
)

data class ArtistRef(val id: String, val name: String, val artworkUrl: String? = null)
data class TrackDetails(val track: Track, val artists: List<ArtistRef>, val albums: List<MusicCollection>)
data class MusicCollection(val id: String, val title: String, val subtitle: String = "", val artworkUrl: String? = null, val tracks: List<Track> = emptyList(), val year: String? = null)
data class Artist(val id: String, val name: String, val artworkUrl: String? = null, val tracks: List<Track> = emptyList(), val albums: List<MusicCollection> = emptyList(), val playlists: List<MusicCollection> = emptyList())
data class Playlist(val id: Long, val name: String, val tracks: List<Track> = emptyList())
data class SearchResult(val tracks: List<Track>, val hasMore: Boolean = false)
data class PlaybackSource(val uri: String, val mimeType: String? = null, val qualityLabel: String = "Source quality")
class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)
interface MusicSource {
    val id: String
    suspend fun search(query: String, offset: Int = 0): SearchResult
    suspend fun getTrack(id: String): Track
    suspend fun getAlbum(id: String): MusicCollection
    suspend fun getArtist(id: String): Artist
    suspend fun getPlaylist(id: String): MusicCollection
    suspend fun resolvePlayback(track: Track): PlaybackSource
    suspend fun getRelatedTracks(track: Track): List<Track>
}

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class SearchSource { YOUTUBE }
enum class AudioQuality(val ceilingKbps: Int) { LOW(64), MEDIUM(128), HIGH(Int.MAX_VALUE) }
data class AppSettings(val theme: ThemeMode = ThemeMode.DARK, val haptics: Boolean = true, val reducedMotion: Boolean = false, val wifiOnly: Boolean = false, val highQuality: Boolean = true, val albumColors: Boolean = true, val searchSource: SearchSource = SearchSource.YOUTUBE, val autoplay: Boolean = true, val audioQuality: AudioQuality = AudioQuality.HIGH)
interface LibraryStore {
    val favorites: Flow<List<Track>>
    val history: Flow<List<Track>>
    val playlists: Flow<List<Playlist>>
    val localTracks: Flow<List<Track>>
    val recentSearches: Flow<List<String>>
    val settings: Flow<AppSettings>
    suspend fun toggleFavorite(track: Track)
    suspend fun recordPlay(track: Track)
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(id: Long, name: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun addToPlaylist(id: Long, track: Track)
    suspend fun removeFromPlaylist(id: Long, trackId: String)
    suspend fun movePlaylistTrack(id: Long, from: Int, to: Int)
    suspend fun saveLocalTrack(track: Track)
    suspend fun removeLocalTrack(trackId: String)
    suspend fun addSearch(query: String)
    suspend fun clearHistory()
    suspend fun clearSearches()
    suspend fun updateSettings(value: AppSettings)
    suspend fun saveQueue(tracks: List<Track>, index: Int, positionMs: Long)
    suspend fun restoreQueue(): Triple<List<Track>, Int, Long>
}

enum class PlaybackPhase { IDLE, RESOLVING, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }
enum class RepeatMode { OFF, ALL, ONE }
data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val error: String? = null,
    val qualityLabel: String = "Source quality",
    val playbackOrder: List<Int> = emptyList(),
) { val current: Track? get() = queue.getOrNull(index) }
