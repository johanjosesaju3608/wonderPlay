package com.wonderplay.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.wonderplay.domain.SourceException
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wonderplay.MainActivity
import com.wonderplay.R
import com.wonderplay.WonderPlayApp
import com.wonderplay.domain.LibraryStore
import com.wonderplay.domain.Track
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import com.wonderplay.domain.RadioPolicy
import com.wonderplay.source.SourceRegistry
import okhttp3.OkHttpClient

/** The service exclusively owns the player; screens only hold a MediaController. */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saves = Channel<QueueSnapshot>(Channel.CONFLATED)
    private lateinit var player: ExoPlayer
    private lateinit var library: LibraryStore
    private lateinit var resolver: PlaybackResolver
    private lateinit var networkPolicy: PlaybackNetworkPolicy
    private lateinit var sources: SourceRegistry
    private var radioJob: Job? = null
    private var radioRetryAt = 0L
    private var session: MediaSession? = null
    private var userTouchedQueue = false
    private var restoreComplete = false
    private var shuffledItems = emptyList<String>()
    private var recordedOccurrence: String? = null
    private data class QueueSnapshot(val tracks: List<Track>, val index: Int, val position: Long)

    override fun onCreate() {
        super.onCreate()
        val container = (application as WonderPlayApp).container
        library = container.library
        sources = container.sources
        networkPolicy = PlaybackNetworkPolicy(this)
        resolver = PlaybackResolver(container.sources, networkPolicy)
        val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS).followRedirects(true).build()
        val upstream = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(http).setUserAgent("wonderPlay/1.0"))
        val gated = DataSource.Factory { NetworkPolicyDataSource(upstream.createDataSource(), networkPolicy) }
        val factory = ResolvingDataSource.Factory(gated, resolver)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory).setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy(5) {
                override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                    // Missing files and unavailable/restricted resolutions need user action, not repeated network retries.
                    var cause: Throwable? = info.exception
                    repeat(8) { if(cause is SourceException) return C.TIME_UNSET; cause = cause?.cause }
                    return super.getRetryDelayMsFor(info)
                }
            }))
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 120_000, 700, 3_000).build())
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(8_000_000L))
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
                if (player.shuffleModeEnabled && (items != shuffledItems || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED))) {
                    shuffledItems = items
                    this@PlaybackService.player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(anchoredShuffleOrder(items.size, player.currentMediaItemIndex), System.nanoTime()))
                } else if (!player.shuffleModeEnabled) shuffledItems = emptyList()

                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                    resolver.retain((0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet())
                }
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) persist()
                scheduleRadio()
                if (player.isPlaying && recordedOccurrence != player.currentMediaItem?.mediaId) {
                    recordedOccurrence = player.currentMediaItem?.mediaId
                    player.currentMediaItem?.let(TrackMediaCodec::track)?.let { track ->
                        scope.launch { runCatching { library.recordPlay(track) } }
                    }
                }
            }
        })
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(intent)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
                    if (controller.packageName != packageName) {
                        return Futures.immediateFailedFuture(SecurityException("Only wonderPlay can edit this queue"))
                    }
                    radioJob?.cancel(); radioRetryAt = 0L
                    userTouchedQueue = true
                    return try { Futures.immediateFuture(mediaItems.map(TrackMediaCodec::playable)) }
                    catch (error: Exception) { Futures.immediateFailedFuture(error) }
                }
            }).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_notification)
        })
        persistenceScope.launch {
            try { for (snapshot in saves) runCatching { library.saveQueue(snapshot.tracks, snapshot.index, snapshot.position) } }
            finally { persistenceScope.cancel() }
        }
        scope.launch {
            library.settings.catch { emit(com.wonderplay.domain.AppSettings()) }.collect { settings ->
                networkPolicy.settings = settings
                if(!settings.autoplay) radioJob?.cancel() else scheduleRadio()
                // Adaptive providers can offer alternatives. Fixed MP3 sources keep their
                // actual source representation; the UI never promises invented fidelity.
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setMaxAudioBitrate(if (settings.highQuality) Int.MAX_VALUE else 160_000)
                    .setForceHighestSupportedBitrate(settings.highQuality).build()
            }
        }
        scope.launch {
            val saved = runCatching { library.restoreQueue() }.getOrNull()
            if (!userTouchedQueue && player.mediaItemCount == 0 && saved != null && saved.first.isNotEmpty()) {
                player.setMediaItems(saved.first.map { TrackMediaCodec.item(it) },
                    saved.second.coerceIn(saved.first.indices), saved.third.coerceAtLeast(0))
                // Restoring does not resolve a stream or make sound. Explicit play prepares.
                player.playWhenReady = false
            }
            restoreComplete = true
            persist()
        }
        scope.launch {
            while (true) { delay(3_000); if (player.isPlaying) persist(); scheduleRadio() }
        }
    }

    private fun scheduleRadio() {
        if(!com.wonderplay.source.connected(this)) return
        if(!networkPolicy.settings.autoplay || !player.playWhenReady || player.playerError != null || player.repeatMode != Player.REPEAT_MODE_OFF || radioJob?.isActive == true) return
        if(android.os.SystemClock.elapsedRealtime() < radioRetryAt) return
        val current = player.currentMediaItem?.let(TrackMediaCodec::track)?.takeIf { it.source == "youtube" } ?: return
        val next = player.currentTimeline.getNextWindowIndex(player.currentMediaItemIndex, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        if(next != C.INDEX_UNSET && player.currentTimeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled) != C.INDEX_UNSET) return
        val snapshot = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val tracks = (0 until player.mediaItemCount).mapNotNull { TrackMediaCodec.track(player.getMediaItemAt(it)) }
        radioJob = scope.launch {
            try {
                delay(250)
                networkPolicy.check()
                val candidates = withTimeout(20_000) { sources.radio(current) }
                val additions = withContext(Dispatchers.Default) { RadioPolicy.select(candidates, tracks) }
                val unchanged = snapshot == (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
                if(unchanged && networkPolicy.settings.autoplay && player.repeatMode == Player.REPEAT_MODE_OFF && additions.isNotEmpty()) {
                    // Preserve user queues and never resurrect a cleared/replaced selection.
                    player.addMediaItems(additions.map(TrackMediaCodec::item))
                    if(player.playbackState == Player.STATE_ENDED && player.playWhenReady) {
                        player.seekTo(snapshot.size, 0); player.prepare()
                    }
                    // Bound long sessions without discarding unplayed items or shuffle history.
                    if(!player.shuffleModeEnabled && player.currentMediaItemIndex > 100) player.removeMediaItems(0, player.currentMediaItemIndex - 30)
                } else radioRetryAt = android.os.SystemClock.elapsedRealtime() + 60_000
            } catch(_: TimeoutCancellationException) { radioRetryAt = android.os.SystemClock.elapsedRealtime() + 60_000 }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { radioRetryAt = android.os.SystemClock.elapsedRealtime() + 60_000 }
        }
    }

    private fun persist() {
        if (!restoreComplete && !userTouchedQueue) return
        val tracks = (0 until player.mediaItemCount).mapNotNull { TrackMediaCodec.track(player.getMediaItemAt(it)) }
        saves.trySend(QueueSnapshot(tracks, if (tracks.isEmpty()) -1 else player.currentMediaItemIndex.coerceIn(tracks.indices),
            player.currentPosition.coerceAtLeast(0)))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        persist()
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) stopSelf()
    }

    override fun onDestroy() {
        if (::player.isInitialized) persist()
        saves.close()
        scope.cancel()
        if (::networkPolicy.isInitialized) networkPolicy.close()
        session?.release()
        session = null
        if (::player.isInitialized) player.release()
        ResolutionState.values.value = emptyMap()
        super.onDestroy()
    }
}
