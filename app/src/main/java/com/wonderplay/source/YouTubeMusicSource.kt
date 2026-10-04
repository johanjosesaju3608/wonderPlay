package com.wonderplay.source

import android.net.Uri
import com.wonderplay.domain.*
import com.wonderplay.metadata.MetadataResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public YouTube Music search and publicly available audio, using NewPipe's maintained parser. */
class YouTubeMusicSource : MusicSource {
    override val id = "youtube"
    private val searchLock = Mutex()
    private var lastQuery = ""
    private var nextPage: Page? = null
    init { initialize() }

    override suspend fun search(query: String, offset: Int): SearchResult = searchLock.withLock {
        extract {
            val extractor = ServiceList.YouTube.getSearchExtractor(query.take(200), listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
            val page = if (offset == 0 || lastQuery != query) {
                extractor.fetchPage(); extractor.initialPage
            } else {
                val continuation = nextPage ?: return@extract SearchResult(emptyList())
                extractor.getPage(continuation)
            }
            lastQuery = query
            nextPage = page.nextPage
            SearchResult(page.items.filterIsInstance<StreamInfoItem>().mapNotNull(::track), page.hasNextPage())
        }
    }
    // Discovery never changes the continuation cursor used by the user's search.
    suspend fun discoverSongs(query: String): List<Track> = extract {
        val extractor = ServiceList.YouTube.getSearchExtractor(query.take(200), listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
        extractor.fetchPage()
        extractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull(::track)
    }
    suspend fun searchCollections(query: String): List<MusicCollection> {
        var albumError: SourceException? = null
        val albums = try { extract {
            val extractor = ServiceList.YouTube.getSearchExtractor(query.take(200), listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), "")
            extractor.fetchPage()
            extractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
                val playlistId = Uri.parse(item.url).getQueryParameter("list") ?: return@mapNotNull null
                MusicCollection(playlistId, item.name, "Album · ${item.uploaderName.orEmpty()}", item.thumbnails.maxByOrNull { it.width }?.url)
            }
        } } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: SourceException) { albumError = failure; emptyList() }
        val playlists = try { FeaturedPlaylists().search(query) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: SourceException) { if (albums.isEmpty()) throw failure else emptyList() }
        if (albums.isEmpty() && playlists.isEmpty() && albumError != null) throw albumError
        return rankCollections(query, (albums + playlists).distinctBy { it.id })
    }
    override suspend fun getTrack(id: String): Track = extract {
        val clean = validId(id)
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$clean")
        MetadataResolver.normalize(Track("youtube:$clean", info.name, info.uploaderName.orEmpty(),
            artworkUrl = info.thumbnails.maxByOrNull { it.width }?.url, durationMs = info.duration.coerceAtLeast(0) * 1000,
            source = this.id, sourceId = clean, permalink = "https://music.youtube.com/watch?v=$clean"))
    }
    override suspend fun resolvePlayback(track: Track): PlaybackSource = extract {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=${validId(track.sourceId)}")
        publicPlayback(info.audioStreams, info.videoStreams)
    }
    override suspend fun getArtist(id: String): Artist = Artist(id, id, tracks = search(id).tracks)
    override suspend fun getAlbum(id: String): MusicCollection = MusicCollection(id, id, tracks = search(id).tracks)
    override suspend fun getPlaylist(id: String): MusicCollection = extract {
        val extractor = ServiceList.YouTube.getPlaylistExtractor("https://www.youtube.com/playlist?list=${id.substringAfter(':')}")
        extractor.fetchPage()
        MusicCollection(id, extractor.name, artworkUrl = extractor.thumbnails.maxByOrNull { it.width }?.url,
            tracks = extractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull(::track))
    }
    override suspend fun getRelatedTracks(track: Track): List<Track> = search(track.artist).tracks.filterNot { it.id == track.id }
    private suspend fun <T> extract(block: () -> T): T = try { runInterruptible(Dispatchers.IO, block) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: SourceException) { throw error }
        catch (error: Exception) { throw SourceException("YouTube Music could not provide this request. Try again or choose another track.", error) }

    companion object {
        internal fun publicPlayback(audioStreams: List<AudioStream>, videoStreams: List<VideoStream>): PlaybackSource {
            val audio = audioStreams.filter { it.isUrl && it.content.startsWith("https://") }.maxByOrNull { it.averageBitrate }
            if(audio != null) return PlaybackSource(audio.content, audio.format?.mimeType, "${audio.averageBitrate.coerceAtLeast(0)} kbps · ${audio.format?.name ?: "Audio"}")
            // Some public videos expose muxed audio/video but no separate audio stream.
            // Use the smallest such stream with video decoding disabled by the service.
            val muxed = videoStreams.filter { !it.isVideoOnly && it.isUrl && it.content.startsWith("https://") }
                .minByOrNull { it.resolution.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
                ?: throw SourceException("No playable public stream was returned. Retry or open this track in YouTube Music.")
            return PlaybackSource(muxed.content, muxed.format?.mimeType, "Audio from public video stream")
        }

        @Volatile private var initialized = false
        @Synchronized private fun initialize() {
            if (!initialized) { NewPipe.init(YouTubeDownloader()); initialized = true }
        }
        internal fun rankCollections(query: String, collections: List<MusicCollection>): List<MusicCollection> {
            val key = MetadataResolver.key(query)
            return collections.sortedBy {
                val title = MetadataResolver.key(it.title)
                when { title == key -> 0; title.startsWith(key) -> 1; title.contains(key) -> 2; key.split(' ').all { word -> MetadataResolver.key(it.title + " " + it.subtitle).contains(word) } -> 3; else -> 4 }
            }
        }
        internal fun isOfficialPlaylist(id: String) = id.startsWith("RDCLAK5uy_") && id.matches(Regex("[A-Za-z0-9_-]{20,100}"))
        internal fun validId(id: String): String = id.substringAfter(':').also {
            if (!it.matches(Regex("[A-Za-z0-9_-]{11}"))) throw SourceException("Invalid YouTube track.")
        }
        internal fun track(item: StreamInfoItem): Track? {
            val id = Uri.parse(item.url).getQueryParameter("v") ?: return null
            if (!id.matches(Regex("[A-Za-z0-9_-]{11}")) || item.duration <= 0) return null
            return MetadataResolver.normalize(Track("youtube:$id", item.name, item.uploaderName.orEmpty(),
                artworkUrl = item.thumbnails.maxByOrNull { it.width }?.url, durationMs = item.duration * 1000,
                source = "youtube", sourceId = id, permalink = "https://music.youtube.com/watch?v=$id"))
        }
        fun searchUrl(query: String): Uri = Uri.Builder().scheme("https").authority("music.youtube.com")
            .appendPath("search").appendQueryParameter("q", query.take(200)).build()
    }
}

private class YouTubeDownloader : Downloader() {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS).build()
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
        request.headers().forEach { (key, values) -> values.forEach { builder.addHeader(key, it) } }
        val body = request.dataToSend()?.toRequestBody("application/json".toMediaType())
        builder.method(request.httpMethod(), body)
        client.newCall(builder.build()).execute().use { response ->
            val source = response.body?.source()
            if (source != null) {
                source.request(16L * 1024 * 1024 + 1)
                if (source.buffer.size > 16L * 1024 * 1024) throw IOException("Response too large")
            }
            return Response(response.code, response.message, response.headers.toMultimap(), source?.readUtf8().orEmpty(), response.request.url.toString())
        }
    }
}
