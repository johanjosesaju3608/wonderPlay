package com.wonderplay.source

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.wonderplay.domain.*
import com.wonderplay.metadata.MetadataResolver
import com.wonderplay.metadata.ArtworkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class SourceRegistry(private val context: Context, private val library: LibraryStore, private val downloads: com.wonderplay.download.DownloadRepository? = null) {
    private val youtube = YouTubeMusicSource()
    private val artwork = ArtworkResolver()
    suspend fun search(query: String, offset: Int = 0): SearchResult {
        val local=if(offset==0) library.localTracks.first().filter { (it.title+" "+it.artist).contains(query,true) } else emptyList()
        return try { val remote=youtube.search(query,offset); remote.copy(tracks=local+remote.tracks) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: SourceException) { if(local.isNotEmpty()) SearchResult(local) else throw failure }
    }
    suspend fun offlinePlayback(track: Track) = downloads?.offline(track)
    suspend fun resolvePlayback(track: Track): PlaybackSource = when(track.source) {
        "youtube" -> youtube.resolvePlayback(track)
        "archive" -> com.wonderplay.download.PermittedAudioSource().find(track)?.let { PlaybackSource(it.url, qualityLabel="Licensed source audio") } ?: throw SourceException("This licensed recording is no longer available.")
        "local" -> withContext(Dispatchers.IO) {
            val uri=Uri.parse(track.streamUrl ?: throw SourceException("Choose this audio file again."))
            try {
                when(uri.scheme) {
                    "content" -> context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { } ?: throw SourceException("This file is unavailable. Choose it again.")
                    "file" -> { val file=File(uri.path ?: "").canonicalFile; val allowed=listOf(context.filesDir,context.cacheDir).any { file.path.startsWith(it.canonicalPath+File.separator) }; if(!allowed || !file.isFile) throw SourceException("This file is unavailable. Choose it again.") }
                    else -> throw SourceException("This audio location is not supported.")
                }
                PlaybackSource(uri.toString(),qualityLabel="Local file")
            } catch(error: SecurityException) { throw SourceException("Read access expired. Choose this file again.",error) }
        }
        else -> throw SourceException("Open this track in its source app to listen.")
    }
    suspend fun getArtist(track: Track): Artist {
        if(track.source=="youtube") return youtube.getArtist(track.artist)
        val tracks=stored().filter { it.artist.equals(track.artist,true) }
        return Artist(track.artist,track.artist,track.artworkUrl,tracks.ifEmpty { listOf(track) })
    }
    suspend fun getAlbum(track: Track): MusicCollection {
        val tracks=stored().filter { track.album.isNotBlank() && it.album==track.album && it.artist==track.artist }.ifEmpty { listOf(track) }
        return MusicCollection(track.albumId ?: track.id,track.album.ifBlank { track.title },track.artist,track.artworkUrl,tracks,track.year)
    }
    suspend fun getRelatedTracks(track: Track): List<Track> = stored().filter { it.artist==track.artist && it.id!=track.id }
    suspend fun searchCollections(query: String) = youtube.searchCollections(query)
    suspend fun discoverSongs(query: String) = youtube.discoverSongs(query)
    suspend fun radio(track: Track) = YouTubeRadio().next(track)
    suspend fun charts() = FeaturedPlaylists().charts()
    suspend fun featuredPlaylists() = FeaturedPlaylists().load()
    suspend fun getPlaylist(id: String) = FeaturedPlaylists().open(id)
    private suspend fun stored() = (library.favorites.first()+library.history.first()+library.localTracks.first()+library.playlists.first().flatMap { it.tracks }).distinctBy { it.id }
    suspend fun enrichArtwork(track: Track): Track = if(track.artworkUrl!=null || track.source=="local") track else track.copy(artworkUrl=artwork.resolve(track))
    fun clearMetadataCache() = artwork.clear()
    suspend fun importLocal(uri: Uri): Track = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Choose an audio file using the document picker." }
        val id=MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null }.orEmpty()
        val retriever=MediaMetadataRetriever()
        try {
            retriever.setDataSource(context,uri)
            fun value(key:Int)=retriever.extractMetadata(key)?.takeIf(String::isNotBlank)
            val embedded=retriever.embeddedPicture
            val art=if(embedded!=null && embedded.size<=5*1024*1024) {
                val folder=File(context.filesDir,"local-artwork").apply { mkdirs() }; File(folder,"$id.jpg").apply { writeBytes(embedded) }.toURI().toString()
            } else null
            MetadataResolver.normalize(Track("local:$id",value(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name.substringBeforeLast('.').ifBlank { "Untitled" },
                value(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown artist",album=value(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),artworkUrl=art,
                durationMs=value(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,source="local",sourceId=id,streamUrl=uri.toString(),year=value(MediaMetadataRetriever.METADATA_KEY_YEAR)))
        } catch(error: Exception) { throw SourceException("Couldn’t read this audio file.",error) }
        finally { retriever.release() }
    }
}
