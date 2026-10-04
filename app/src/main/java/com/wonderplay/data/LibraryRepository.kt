package com.wonderplay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import com.wonderplay.domain.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import java.io.IOException

private val Context.libraryPreferences by preferencesDataStore("wonderplay_preferences")

class LibraryRepository(
    context: Context,
    private val database: LibraryDatabase = Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "wonderplay.db").build(),
    private val preferences: DataStore<Preferences> = context.applicationContext.libraryPreferences,
) : LibraryStore {
    private val dao = database.libraryDao()
    private val preferenceFlow = preferences.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    override val favorites = dao.favorites().map { values -> values.map { TrackCodec.decode(it.payload) } }
    override val history = dao.history().map { values -> values.map { TrackCodec.decode(it.payload) } }
    override val localTracks = dao.localTracks().map { values -> values.map { TrackCodec.decode(it.payload) } }
    override val playlists = combine(dao.playlists(), dao.playlistTracks()) { lists, entries ->
        val grouped = entries.groupBy { it.playlistId }
        lists.map { Playlist(it.id, it.name, grouped[it.id].orEmpty().map { row -> TrackCodec.decode(row.payload) }) }
    }
    override val recentSearches = preferenceFlow.map { decodeSearches(it[Keys.searches]) }.distinctUntilChanged()
    override val settings = preferenceFlow.map { values ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(values[Keys.theme].orEmpty()) }.getOrDefault(ThemeMode.DARK),
            haptics = values[Keys.haptics] ?: true, reducedMotion = values[Keys.reducedMotion] ?: false,
            wifiOnly = values[Keys.wifiOnly] ?: false, highQuality = values[Keys.highQuality] ?: true,
            audioQuality = values[Keys.audioQuality]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() } ?: if(values[Keys.highQuality] == false) AudioQuality.MEDIUM else AudioQuality.HIGH,
            albumColors = true, autoplay = values[Keys.autoplay] ?: true,
            searchSource = runCatching { SearchSource.valueOf(values[Keys.searchSource].orEmpty()) }.getOrDefault(SearchSource.YOUTUBE),
        )
    }.distinctUntilChanged()

    override suspend fun toggleFavorite(track: Track) = database.withTransaction {
        dao.putTrack(track.entity())
        if (dao.isFavorite(track.id)) dao.deleteFavorite(track.id) else dao.putFavorite(FavoriteEntity(track.id, System.currentTimeMillis()))
    }
    override suspend fun recordPlay(track: Track) = database.withTransaction {
        dao.putTrack(track.entity()); dao.putHistory(HistoryEntity(track.id, System.currentTimeMillis())); dao.trimHistory()
    }
    override suspend fun createPlaylist(name: String): Long = dao.createPlaylist(PlaylistEntity(name = cleanName(name), createdAt = System.currentTimeMillis()))
    override suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, cleanName(name))
    override suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)
    override suspend fun addToPlaylist(id: Long, track: Track) = database.withTransaction {
        require(dao.playlistExists(id)) { "This playlist no longer exists." }
        dao.putTrack(track.entity())
        val end = (dao.playlistEntries(id).maxOfOrNull { it.position } ?: -1) + 1
        dao.addPlaylistTrack(PlaylistTrackEntity(id, track.id, end))
    }
    override suspend fun removeFromPlaylist(id: Long, trackId: String) = database.withTransaction {
        dao.removePlaylistTrack(id, trackId)
        dao.playlistEntries(id).forEachIndexed { position, entry -> dao.setPlaylistPosition(id, entry.trackId, position) }
    }
    override suspend fun movePlaylistTrack(id: Long, from: Int, to: Int) = database.withTransaction {
        val entries = dao.playlistEntries(id).toMutableList()
        if (from !in entries.indices || to !in entries.indices || from == to) return@withTransaction
        entries.add(to, entries.removeAt(from))
        entries.forEachIndexed { index, entry -> dao.setPlaylistPosition(id, entry.trackId, index) }
    }
    override suspend fun saveLocalTrack(track: Track) = database.withTransaction {
        require(track.source == "local") { "Only local audio can be imported." }
        dao.putTrack(track.entity()); dao.putLocal(LocalEntity(track.id, System.currentTimeMillis()))
    }
    override suspend fun removeLocalTrack(trackId: String) { if (trackId.startsWith("local:")) dao.deleteTrack(trackId) }
    override suspend fun addSearch(query: String) {
        val cleaned = query.trim().replace(Regex("\\s+"), " ").take(200)
        if (cleaned.isBlank()) return
        preferences.edit { values ->
            values[Keys.searches] = JSONArray((listOf(cleaned) + decodeSearches(values[Keys.searches]).filterNot { it.equals(cleaned, true) }).take(12)).toString()
        }
    }
    override suspend fun clearHistory() = dao.clearHistory()
    override suspend fun clearSearches() { preferences.edit { it.remove(Keys.searches) } }
    override suspend fun updateSettings(value: AppSettings) {
        preferences.edit { values ->
            values[Keys.theme] = value.theme.name; values[Keys.haptics] = value.haptics
            values[Keys.albumColors] = value.albumColors; values[Keys.searchSource] = value.searchSource.name
            values[Keys.autoplay] = value.autoplay; values[Keys.reducedMotion] = value.reducedMotion; values[Keys.wifiOnly] = value.wifiOnly; values[Keys.highQuality] = value.audioQuality == AudioQuality.HIGH; values[Keys.audioQuality] = value.audioQuality.name
        }
    }
    override suspend fun saveQueue(tracks: List<Track>, index: Int, positionMs: Long) = database.withTransaction {
        dao.clearQueue()
        dao.putQueue(tracks.mapIndexed { position, track -> QueueEntity(position, TrackCodec.encode(track)) })
        dao.putQueueState(QueueStateEntity(trackIndex = if (tracks.isEmpty()) -1 else index.coerceIn(tracks.indices), positionMs = positionMs.coerceAtLeast(0)))
    }
    override suspend fun restoreQueue(): Triple<List<Track>, Int, Long> = database.withTransaction {
        val tracks = dao.queue().map { TrackCodec.decode(it.payload) }
        val state = dao.queueState()
        Triple(tracks, if (tracks.isEmpty()) -1 else (state?.trackIndex ?: 0).coerceIn(tracks.indices), state?.positionMs?.coerceAtLeast(0) ?: 0)
    }

    private fun Track.entity() = TrackEntity(id, TrackCodec.encode(this))
    private fun cleanName(name: String) = name.trim().take(120).also { require(it.isNotEmpty()) { "Give your playlist a name." } }
    private fun decodeSearches(value: String?): List<String> = runCatching {
        val array = JSONArray(value ?: "[]")
        (0 until array.length()).map { array.getString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
    private object Keys {
        val autoplay = booleanPreferencesKey("autoplay")
        val albumColors = booleanPreferencesKey("album_colors"); val searchSource = stringPreferencesKey("search_source")
        val searches = stringPreferencesKey("recent_searches"); val theme = stringPreferencesKey("theme")
        val haptics = booleanPreferencesKey("haptics"); val reducedMotion = booleanPreferencesKey("reduced_motion")
        val audioQuality = stringPreferencesKey("audio_quality"); val wifiOnly = booleanPreferencesKey("wifi_only"); val highQuality = booleanPreferencesKey("high_quality")
    }
}
