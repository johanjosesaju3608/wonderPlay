package com.wonderplay

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import com.wonderplay.domain.*
import com.wonderplay.player.PlayerController
import com.wonderplay.source.LyricsRepository
import com.wonderplay.source.LyricsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Presentation state contains only metadata; audio lifecycle belongs to the service. */
data class UiState(
    val query: String = "",
    val searchTracks: List<Track> = emptyList(),
    val searchCollections: List<MusicCollection> = emptyList(),
    val collectionsLoading: Boolean = false,
    val collectionsError: String? = null,
    val searching: Boolean = false,
    val searchError: String? = null,
    val hasMore: Boolean = false,
    val collection: MusicCollection? = null,
    val artist: Artist? = null,
    val detailLoading: Boolean = false,
    val featured: List<MusicCollection> = emptyList(),
    val featuredLoading: Boolean = false,
    val featuredError: String? = null,
    val charts: List<MusicCollection> = emptyList(),
    val chartsLoading: Boolean = false,
    val chartsError: String? = null,
    val recommendations: List<Track> = emptyList(),
    val recommendationsLoading: Boolean = false,
    val recommendationsError: String? = null,
    val personalized: Boolean = false,
    val message: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as WonderPlayApp).container
    private val library = container.library
    private val sources = container.sources
    val downloads = container.downloads.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val licensedMusic = MutableStateFlow<List<com.wonderplay.download.DownloadEntitlement>>(emptyList())
    val licensedLoading = MutableStateFlow(false)
    val licensedError = MutableStateFlow<String?>(null)
    private var licensedJob: Job? = null
    val player = PlayerController(application, library, sources)
    private val mutableUi = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = mutableUi.asStateFlow()
    val settings = library.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val favorites = library.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = library.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val localTracks = library.localTracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = library.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentSearches = library.recentSearches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val lyricsRepository = LyricsRepository()
    private val mutableLyrics = MutableStateFlow(LyricsState())
    val lyrics = mutableLyrics.asStateFlow()
    private var discoveryRequested = false
    private var discoverySignature: String? = null
    private var chartsLoadedAt = 0L
    private var chartsJob: Job? = null
    private var recommendationsJob: Job? = null
    private var lyricsJob: Job? = null
    private var featuredJob: Job? = null
    private var collectionSearchJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var searchGeneration = 0
    private var remoteOffset = 0

    init {
        player.connect()
        loadFeatured()
        viewModelScope.launch {
            combine(history, favorites) { h, f -> h to f }.collect { (h, f) ->
                if (discoveryRequested) loadRecommendations(h, f)
            }
        }
        viewModelScope.launch {
            player.state.map { it.current }.distinctUntilChangedBy { it?.id }.collect { track ->
                lyricsJob?.cancel()
                mutableLyrics.value = LyricsState(track?.id)
                if (track != null && track.source != "local") loadLyrics(track)
            }
        }
    }

    fun loadDiscovery(force: Boolean = false) {
        if(!com.wonderplay.source.connected(getApplication())) return
        discoveryRequested = true
        val now = android.os.SystemClock.elapsedRealtime()
        if (chartsJob?.isActive != true && (force || mutableUi.value.charts.isEmpty() || now - chartsLoadedAt > 600_000)) {
            mutableUi.update { it.copy(chartsLoading = true, chartsError = null) }
            chartsJob = viewModelScope.launch {
                try {
                    val charts = sources.charts()
                    chartsLoadedAt = android.os.SystemClock.elapsedRealtime()
                    mutableUi.update { it.copy(charts = charts, chartsLoading = false) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableUi.update { it.copy(chartsLoading = false, chartsError = "Couldn't load charts. Check your connection and retry.") } }
            }
        }
        loadRecommendations(history.value, favorites.value, force)
    }
    private fun loadRecommendations(h: List<Track>, f: List<Track>, force: Boolean = false) {
        if(!com.wonderplay.source.connected(getApplication())) return
        val signature = h.joinToString { it.id } + "|" + f.joinToString { it.id }
        if (!force && signature == discoverySignature) return
        discoverySignature = signature
        recommendationsJob?.cancel()
        val seeds = Recommendations.seeds(h, f)
        mutableUi.update { it.copy(recommendations = emptyList(), recommendationsLoading = true, recommendationsError = null, personalized = seeds.isNotEmpty()) }
        recommendationsJob = viewModelScope.launch {
            try {
                val candidates = kotlinx.coroutines.coroutineScope {
                    (seeds.map { it.artist } .ifEmpty { listOf("top songs") }).map { query ->
                        async {
                            try { sources.discoverSongs(query) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { emptyList() }
                        }
                    }.map { it.await() }.flatten()
                }
                val tracks = withContext(Dispatchers.Default) { Recommendations.rank(candidates, h, f) }
                mutableUi.update { it.copy(recommendations = tracks, recommendationsLoading = false,
                    recommendationsError = if(tracks.isEmpty()) "No new recommendations right now. Try again after listening to more music." else null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUi.update { it.copy(recommendationsLoading = false, recommendationsError = "Couldn't load recommendations. Try again.") } }
        }
    }

    fun retryLyrics() { player.state.value.current?.takeIf { it.source != "local" }?.let(::loadLyrics) }
    private fun loadLyrics(track: Track) {
        if(!com.wonderplay.source.connected(getApplication())) return
        lyricsJob?.cancel()
        mutableLyrics.value = LyricsState(track.id, loading = true)
        lyricsJob = viewModelScope.launch {
            val result = lyricsRepository.find(track)
            if (player.state.value.current?.id == track.id) mutableLyrics.value = result
        }
    }
    fun loadFeatured() {
        if(!com.wonderplay.source.connected(getApplication())) return
        if (featuredJob?.isActive == true) return
        mutableUi.update { it.copy(featuredLoading = true, featuredError = null) }
        featuredJob = viewModelScope.launch {
            try { val lists = sources.featuredPlaylists(); mutableUi.update { it.copy(featured = lists, featuredLoading = false) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUi.update { it.copy(featuredLoading = false, featuredError = "Couldn't load featured playlists. Check your connection and retry.") } }
        }
    }
    fun openPlaylist(list: MusicCollection) {
        detailJob?.cancel()
        mutableUi.update { it.copy(collection = null, artist = null, detailLoading = true) }
        detailJob = viewModelScope.launch {
            try { val full = sources.getPlaylist(list.id); mutableUi.update { it.copy(collection = full.copy(title = list.title, subtitle = list.subtitle + if(full.subtitle.startsWith("First ")) " · ${full.subtitle}" else "", artworkUrl = full.artworkUrl ?: list.artworkUrl), detailLoading = false) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableUi.update { it.copy(detailLoading = false, message = "Couldn't open this playlist. Try again.") } }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        collectionSearchJob?.cancel()
        val generation = ++searchGeneration
        remoteOffset = 0
        mutableUi.update { it.copy(query = query, searchTracks = emptyList(), searchCollections = emptyList(), collectionsLoading = query.isNotBlank(), collectionsError = null, searching = query.isNotBlank(), searchError = null, hasMore = false) }
        if (query.isBlank()) return
        collectionSearchJob = viewModelScope.launch {
            delay(400)
            try {
                val lists = sources.searchCollections(query.trim())
                if (generation == searchGeneration) mutableUi.update { it.copy(searchCollections = lists, collectionsLoading = false) }
                if (lists.isNotEmpty()) library.addSearch(query.trim())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == searchGeneration) mutableUi.update { it.copy(collectionsLoading = false, collectionsError = "Couldn't check albums and official playlists. Retry search.") } }
        }
        searchJob = viewModelScope.launch {
            delay(250)
            runSearch(query.trim(), generation, append = false)
        }
    }

    fun retrySearch() { search(mutableUi.value.query) }

    fun loadMore() {
        val current = mutableUi.value
        if (current.searching || !current.hasMore || current.query.isBlank()) return
        val generation = searchGeneration
        mutableUi.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch { runSearch(current.query.trim(), generation, append = true) }
    }

    private suspend fun runSearch(query: String, generation: Int, append: Boolean) {
        try {
            val result = sources.search(query, if (append) remoteOffset else 0)
            if (generation != searchGeneration) return
            // Provider pages are 30 entries, even if ranking filters some candidates.
            remoteOffset += 30
            mutableUi.update { state ->
                state.copy(searchTracks = (if (append) state.searchTracks + result.tracks else result.tracks).distinctBy { it.id }, searching = false, searchError = null, hasMore = result.hasMore)
            }
            if (!append && result.tracks.isNotEmpty()) library.addSearch(query)
            for (track in result.tracks.filter { it.artworkUrl == null && it.source != "local" }.take(3)) {
                val enriched = sources.enrichArtwork(track)
                if (generation != searchGeneration) return
                if (enriched.artworkUrl != null) mutableUi.update { it.copy(searchTracks = it.searchTracks.map { old -> if (old.id == enriched.id) enriched else old }) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (generation != searchGeneration) return
            debugLog("Search failed", error)
            mutableUi.update { it.copy(searching = false, searchError = error.userMessage("Music source unavailable. Check your connection and try again.")) }
        }
    }

    fun download(track: Track) = mutate {
        if(track.source=="local") {message("This track is already on your device.");return@mutate}
        container.downloads.enqueue(track);message("Checking download availability. View progress in Library → Downloads.")
    }
    fun cancelDownload(id:String) = mutate {container.downloads.cancel(id)}
    fun removeDownload(id:String) = mutate {
        if(player.state.value.current?.id==id) {message("Clear the current player before removing this download.");return@mutate}
        container.downloads.remove(id)
    }
    fun downloadedTrack(entry:com.wonderplay.download.DownloadEntry) = container.downloads.track(entry)
    fun discoverLicensedMusic(query:String="") {
        licensedJob?.cancel()
        licensedJob=viewModelScope.launch {
            licensedLoading.value=true;licensedError.value=null
            try { licensedMusic.value=withTimeout(90000) {com.wonderplay.download.PermittedAudioSource().search(query)} }
            catch(cancelled:CancellationException) {throw cancelled}
            catch(_:Exception) {licensedError.value="Couldn't load music available for download. Try again."}
            finally {licensedLoading.value=false}
        }
    }
    fun toggleFavorite(track: Track) = mutate { library.toggleFavorite(track) }
    fun createPlaylist(name: String, firstTrack: Track? = null) = mutate {
        val clean = name.trim().take(80)
        if (clean.isEmpty()) { message("Give your playlist a name."); return@mutate }
        val id = library.createPlaylist(clean)
        if (firstTrack != null) library.addToPlaylist(id, firstTrack)
        message("Playlist created")
    }
    fun renamePlaylist(id: Long, name: String) = mutate {
        val clean = name.trim().take(80)
        if (clean.isNotEmpty()) library.renamePlaylist(id, clean)
    }
    fun deletePlaylist(id: Long) = mutate { library.deletePlaylist(id) }
    fun addToPlaylist(id: Long, track: Track) = mutate { library.addToPlaylist(id, track); message("Added to playlist") }
    fun removeFromPlaylist(id: Long, trackId: String) = mutate { library.removeFromPlaylist(id, trackId) }
    fun movePlaylistTrack(id: Long, from: Int, to: Int) = mutate { library.movePlaylistTrack(id, from, to) }
    fun removeLocal(track: Track) = mutate { library.removeLocalTrack(track.id) }

    fun importLocal(uris: List<Uri>) = mutate {
        var added = 0
        var failed = 0
        for (uri in uris.distinct()) {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val track = sources.importLocal(uri)
                library.saveLocalTrack(track)
                added++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { debugLog("Import failed", error); failed++ }
        }
        message(when {
            failed == 0 -> "$added ${if (added == 1) "track" else "tracks"} added to your library"
            added == 0 -> "Couldn’t read those audio files. Choose files available on this device."
            else -> "$added tracks added. $failed couldn’t be read."
        })
    }

    fun openArtist(track: Track) {
        detailJob?.cancel()
        mutableUi.update { it.copy(collection = null, artist = null, detailLoading = true) }
        detailJob = viewModelScope.launch {
            try {
                val artist = sources.getArtist(track)
                mutableUi.update { it.copy(artist = artist, detailLoading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                debugLog("Artist unavailable", error)
                mutableUi.update { it.copy(detailLoading = false, message = error.userMessage("Couldn’t open this artist. Try again.")) }
            }
        }
    }

    fun openAlbum(track: Track) {
        detailJob?.cancel()
        mutableUi.update { it.copy(collection = null, artist = null, detailLoading = true) }
        detailJob = viewModelScope.launch {
            try {
                val album = sources.getAlbum(track)
                mutableUi.update { it.copy(collection = album, detailLoading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                debugLog("Album unavailable", error)
                mutableUi.update { it.copy(detailLoading = false, message = error.userMessage("Couldn’t open this album. Try again.")) }
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        mutableUi.update { it.copy(collection = null, artist = null, detailLoading = false) }
    }
    fun updateSettings(value: AppSettings) = mutate { library.updateSettings(value) }
    fun clearHistory() = mutate { library.clearHistory(); message("Listening history cleared") }
    fun clearSearches() = mutate { library.clearSearches() }
    fun clearArtworkCache() = mutate {
        sources.clearMetadataCache()
        val loader = getApplication<Application>().imageLoader
        loader.memoryCache?.clear()
        withContext(Dispatchers.IO) { loader.diskCache?.clear() }
        message("Artwork cache cleared")
    }
    fun dismissMessage() { mutableUi.update { it.copy(message = null) } }
    private fun message(value: String) { mutableUi.update { it.copy(message = value) } }
    private fun mutate(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { debugLog("Library operation failed", error); message(error.userMessage("Couldn’t save that change. Please try again.")) }
    } }
    private fun Exception.userMessage(fallback: String): String = if (this is SourceException) message ?: fallback else fallback
    private fun debugLog(message: String, error: Exception) { if (BuildConfig.DEBUG) Log.w("wonderPlay", message, error) }
    override fun onCleared() { player.release(); super.onCleared() }
}
