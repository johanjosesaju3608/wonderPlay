package com.wonderplay.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wonderplay.domain.Track

@Composable
internal fun Artwork(track: Track?, modifier: Modifier = Modifier, description: String? = null, artwork: String? = track?.artworkUrl, seed: String = track?.id.orEmpty()) {
    BoxWithConstraints(modifier.clip(Shape.artwork)) {
        ArtFallback(seed, Modifier.matchParentSize())
        if (!artwork.isNullOrBlank()) {
            val context = LocalContext.current
            val reduced = LocalReducedMotion.current
            val density = androidx.compose.ui.platform.LocalDensity.current
            val pixels = with(density) { maxWidth.toPx() }
            val bucket = when { pixels <= 128 -> 128; pixels <= 256 -> 256; pixels <= 512 -> 512; else -> 1024 }
            val largeUrl = remember(artwork,bucket) { com.wonderplay.metadata.ArtworkUrls.forSize(artwork,bucket,upgradeVideo=true) }
            var useOriginal by remember(artwork,bucket) { mutableStateOf(false) }
            val imageUrl = if(useOriginal) artwork else largeUrl
            val request = remember(imageUrl, reduced, context, bucket) { ImageRequest.Builder(context).data(imageUrl).size(bucket, bucket).crossfade(if (reduced) 0 else 220).build() }
            AsyncImage(model = request, onError = { if(largeUrl!=artwork) useOriginal=true }, onSuccess = { if (bucket >= 512 && it.result.image.width < 256 && largeUrl != artwork) useOriginal = true }, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun SectionHeading(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.small), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
internal fun TrackRow(track: Track, onPlay: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier,
    current: Boolean = false, favorite: Boolean = false, trailing: @Composable (() -> Unit)? = null, number: Int? = null, scrollingTitle: Boolean = false) {
    val haptics = LocalWonderHaptics.current
    Row(modifier.fillMaxWidth().heightIn(min = 76.dp).combinedClickable(
        role = Role.Button, onClickLabel = "Play ${track.title}", onLongClickLabel = "Track options",
        onClick = { haptics.perform(HapticEvent.TAP); onPlay() },
        onLongClick = { haptics.perform(HapticEvent.DRAG_START); onMenu() }
    ).padding(start = Space.page, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (number != null) Text(number.toString(), style = MaterialTheme.typography.bodySmall,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp))
        Artwork(track, Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) {
            Text(track.title, modifier = if(scrollingTitle && !LocalReducedMotion.current) Modifier.basicMarquee(iterations=Int.MAX_VALUE) else Modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                if (current) { Icon(Icons.Rounded.Equalizer, "Current track", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
                if (track.explicit) { Text("E", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(5.dp)) }
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f, false).then(if(scrollingTitle && !LocalReducedMotion.current) Modifier.basicMarquee(iterations=Int.MAX_VALUE) else Modifier))
                if (favorite) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Favorite, "Favorite", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary) }
            }
        }
        if (trailing != null) trailing() else {
            if (track.durationMs > 0) Text(timeLabel(track.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp))
            TactileIcon(Icons.Rounded.MoreHoriz, "Options for ${track.title}", onMenu)
        }
    }
}

@Composable
internal fun PrimaryAction(label: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalWonderHaptics.current
    Button(onClick = { haptics.perform(HapticEvent.TAP); onClick() }, shape = Shape.control, modifier = modifier.heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp), elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun QuietAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalWonderHaptics.current
    FilledTonalButton(onClick = { haptics.perform(HapticEvent.TAP); onClick() }, shape = Shape.control,
        modifier = modifier.heightIn(min = 52.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(label)
    }
}

@Composable
internal fun EmptyState(title: String, body: String, icon: ImageVector = Icons.Rounded.LibraryMusic,
    action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.section)) {
        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceContainer, Shape.control), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        if (action != null && onAction != null) PrimaryAction(action, modifier = Modifier.padding(top = 22.dp), onClick = onAction)
    }
}

@Composable
internal fun FailureState(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Space.page)) {
        Text("A quiet interruption", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        QuietAction("Try again", Icons.Rounded.Refresh, onClick = retry)
    }
}

@Composable
internal fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack == null) Space.page else 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) { TactileIcon(Icons.Rounded.ArrowBack, "Back", onBack); Spacer(Modifier.width(4.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        action?.invoke()
    }
}

internal fun timeLabel(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) / 1000).toInt()
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}
