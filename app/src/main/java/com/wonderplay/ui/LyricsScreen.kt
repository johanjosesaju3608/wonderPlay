package com.wonderplay.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonderplay.AppViewModel
import com.wonderplay.domain.PlayerState
import com.wonderplay.domain.Track
import com.wonderplay.source.LrcParser
import com.wonderplay.source.Lyrics
import com.wonderplay.source.LyricsState
import com.wonderplay.source.Romanization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LyricsPanel(vm: AppViewModel, player: PlayerState) {
    val loaded by vm.lyrics.collectAsStateWithLifecycle()
    val track = player.current?.takeIf { it.source != "local" } ?: return
    val state = if (loaded.trackId == track.id) loaded else LyricsState(track.id, loading = true)
    LyricsPanelContent(state, track, player.positionMs, player.isPlaying, vm.player::seekTo, vm.player::togglePlayPause, vm::retryLyrics)
}

@Composable
internal fun LyricsPanelContent(state: LyricsState, track: Track, positionMs: Long, isPlaying: Boolean, onSeek: (Long) -> Unit, onToggle: () -> Unit, onRetry: () -> Unit) {
    var full by rememberSaveable(track.id) { mutableStateOf(false) }
    val lyric = state.lyrics
    val context = LocalContext.current
    fun searchWeb() {
        val uri = Uri.Builder().scheme("https").authority("www.google.com").appendPath("search").appendQueryParameter("q", "${track.title} ${track.artist} lyrics").build()
        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No browser is available to search for lyrics.", Toast.LENGTH_SHORT).show() }
    }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp).testTag("Lyrics panel")) {
        when {
            state.loading -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("Finding lyrics…", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall) }
            lyric?.instrumental == true -> Text("Instrumental · no sung lyrics", style = MaterialTheme.typography.bodyMedium)
            lyric != null && lyric.lines.isNotEmpty() -> {
                Surface(Modifier.fillMaxWidth().clickable { full = true }.testTag("Synced lyrics preview"), shape = Shape.panel, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Lyrics", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); Icon(Icons.Rounded.OpenInFull, "Expand lyrics", Modifier.size(18.dp)) }
                        val index = LrcParser.activeIndex(lyric.lines, positionMs)
                        val start = (index - 1).coerceAtLeast(0).coerceAtMost((lyric.lines.size - 4).coerceAtLeast(0))
                        val reduced = LocalReducedMotion.current
                        AnimatedContent(start, transitionSpec = { androidx.compose.animation.fadeIn(tween(if(reduced) 0 else 220)) togetherWith androidx.compose.animation.fadeOut(tween(if(reduced) 0 else 160)) }, label = "Lyrics preview") { first ->
                            Column(Modifier.padding(top = 10.dp).height(176.dp)) {
                                repeat(4) { row ->
                                    val lineIndex = first + row
                                    val line = lyric.lines.getOrNull(lineIndex)
                                    Text(line?.text?.ifBlank { "♪" }.orEmpty(), Modifier.fillMaxWidth().height(44.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium, color = if (lineIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                                }
                            }
                        }
                        Text("Synced · ${lyric.provider}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            lyric != null -> {
                Text("Lyrics available · ${lyric.provider}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { full = true }) { Text("Show lyrics"); Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.Notes, null) }
            }
            else -> {
                Text(state.message ?: "No lyrics found in the available sources.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row { TextButton(onClick = onRetry) { Text("Retry") }; TextButton(onClick = ::searchWeb) { Text("Search for lyrics"); Icon(Icons.Rounded.OpenInNew, null, Modifier.padding(start = 6.dp).size(16.dp)) } }
            }
        }
    }
    if (full && lyric != null) FullLyrics(lyric, track, positionMs, onSeek, onToggle, isPlaying) { full = false }
}

@Composable
internal fun FullLyrics(lyrics: Lyrics, track: Track, positionMs: Long, onSeek: (Long) -> Unit, onToggle: () -> Unit, playing: Boolean, onDismiss: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val scroll = rememberLazyListState()
    var romanized by rememberSaveable(track.id) { mutableStateOf(false) }
    val roman by produceState<Lyrics?>(null, lyrics) { value = withContext(Dispatchers.Default) { Romanization.variant(lyrics) } }
    val shown = if(romanized) roman ?: lyrics else lyrics
    val active = LrcParser.activeIndex(shown.lines, positionMs)
    var follow by rememberSaveable(track.id) { mutableStateOf(true) }
    LaunchedEffect(active, follow, romanized) {
        if (follow && active >= 0) {
            if (reduced) scroll.scrollToItem(active) else scroll.animateScrollToItem(active)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("Full screen lyrics"), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(LocalPlayerGradient.current)).statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TactileIcon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", onDismiss)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TactileIcon(if(playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if(playing) "Pause lyrics playback" else "Play lyrics playback", onToggle)
                }
                if(roman != null) Row(Modifier.padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(romanized, { romanized = !romanized }, label = { Text(if(romanized) "Original" else "Romanized") }, shape = androidx.compose.foundation.shape.CircleShape)
                    if(romanized) Text(roman?.romanizationSource.orEmpty(), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (shown.lines.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Synced · ${lyrics.provider}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        FilterChip(follow, { follow = !follow }, label = { Text(if(follow) "Following" else "Follow playback") })
                    }
                    LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 240.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        itemsIndexed(shown.lines) { index, line ->
                            val color by animateColorAsState(if(index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), tween(if(reduced) 0 else 250), label = "Active lyric")
                            Text(line.text.ifBlank { "♪" }, Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
                        }
                    }
                } else {
                    Text("Unsynced · ${lyrics.provider}", Modifier.padding(horizontal = 28.dp, vertical = 12.dp), style = MaterialTheme.typography.labelSmall)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(shown.plain.lines()) { _, line -> Text(line, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
            }
        }
    }
}
