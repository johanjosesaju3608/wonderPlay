package com.wonderplay.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import com.wonderplay.domain.AppSettings
import com.wonderplay.domain.PlaybackSource
import com.wonderplay.domain.SourceException
import com.wonderplay.source.SourceRegistry
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal data class ResolutionInfo(val resolving: Boolean = false, val quality: String = "Source quality", val error: String? = null)

/** Only transient resolution metadata, never audio, is shared with the in-process controller. */
internal object ResolutionState {
    val values = MutableStateFlow<Map<String, ResolutionInfo>>(emptyMap())
    fun set(id: String, value: ResolutionInfo) { values.update { it + (id to value) } }
    fun retain(ids: Set<String>) { values.update { current -> current.filterKeys { it in ids } } }
}

internal fun remotePlaybackAllowed(wifiOnly: Boolean, onWifi: Boolean): Boolean = !wifiOnly || onWifi

/** An inexpensive volatile policy check also protects already-open network streams. */
internal class PlaybackNetworkPolicy(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    @Volatile var settings = AppSettings()
    @Volatile private var onWifi = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { refresh() }
        override fun onAvailable(network: Network) { refresh() }
        override fun onLost(network: Network) { refresh() }
    }
    init {
        refresh()
        connectivity.registerDefaultNetworkCallback(callback)
    }
    private fun refresh() {
        onWifi = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
    fun check() {
        if (!remotePlaybackAllowed(settings.wifiOnly, onWifi)) {
            throw IOException("Wi-Fi only is enabled. Connect to Wi-Fi or change it in Settings.")
        }
    }
    fun close() { runCatching { connectivity.unregisterNetworkCallback(callback) } }
}

@UnstableApi
internal class PlaybackResolver(
    private val sources: SourceRegistry,
    private val policy: PlaybackNetworkPolicy,
) : ResolvingDataSource.Resolver {
    private data class Cached(val source: PlaybackSource, val time: Long, val quality: com.wonderplay.domain.AudioQuality)
    private val resolved = ConcurrentHashMap<String, Cached>()

    fun retain(ids: Set<String>) { resolved.keys.retainAll(ids); ResolutionState.retain(ids) }
    fun invalidate(id: String) { resolved.remove(id); ResolutionState.set(id, ResolutionInfo()) }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != TrackMediaCodec.SCHEME) return dataSpec
        val id = dataSpec.uri.lastPathSegment ?: throw IOException("Missing playback item")
        val track = try {
            TrackMediaCodec.decode(dataSpec.uri.getQueryParameter("metadata") ?: throw IOException("Missing track metadata"))
        } catch (error: Exception) { throw IOException("The saved track is invalid. Search for it again.", error) }
        try {
            val offline = runBlocking { sources.offlinePlayback(track) }
            if(offline != null) { ResolutionState.set(id, ResolutionInfo(quality=offline.qualityLabel)); return dataSpec.withUri(Uri.parse(offline.uri)) }
            if (track.source != "local") policy.check()
            val quality = policy.settings.audioQuality
            val cached = resolved[id]?.takeIf { it.quality == quality && android.os.SystemClock.elapsedRealtime() - it.time < 300_000L }
            if (cached != null) {
                ResolutionState.set(id, ResolutionInfo(quality = cached.source.qualityLabel))
                return dataSpec.withUri(Uri.parse(cached.source.uri))
            }
            ResolutionState.set(id, ResolutionInfo(resolving = true))
            // Media3 calls this on its loader thread. Cancelling a source interrupts this
            // thread and runBlocking cancels the provider coroutine before it can commit.
            val source = runBlocking { withTimeout(25_000) { sources.resolvePlayback(track, quality) } }
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            resolved[id] = Cached(source, android.os.SystemClock.elapsedRealtime(), quality)
            ResolutionState.set(id, ResolutionInfo(quality = source.qualityLabel))
            return dataSpec.withUri(Uri.parse(source.uri))
        } catch (cancelled: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Playback selection changed")
        } catch (cancelled: CancellationException) {
            throw InterruptedIOException("The source took too long to respond. Tap retry.")
        } catch (failure: Exception) {
            val message = when (failure) {
                is SourceException, is IOException -> failure.message ?: "This track is unavailable. Tap retry."
                else -> "Could not open this track. Check your connection and retry."
            }
            ResolutionState.set(id, ResolutionInfo(error = message))
            throw IOException(message, failure)
        }
    }
}

@UnstableApi
internal class NetworkPolicyDataSource(
    private val upstream: DataSource,
    private val policy: PlaybackNetworkPolicy,
) : DataSource {
    private var remote = false
    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)
    override fun open(dataSpec: DataSpec): Long {
        remote = dataSpec.uri.scheme in setOf("https", "http")
        if (remote) policy.check()
        return upstream.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remote) policy.check()
        return upstream.read(buffer, offset, length)
    }
    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun close() = upstream.close()
}
