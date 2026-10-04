package com.wonderplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wonderplay.AppViewModel
import com.wonderplay.domain.*

@Composable
internal fun LibraryScreen(vm: AppViewModel, favorites: List<Track>, history: List<Track>, locals: List<Track>, playlists: List<Playlist>,
    route: String, onRoute: (String) -> Unit, onImport: () -> Unit, onSearch: () -> Unit,
    onCollection: (MusicCollection) -> Unit, onMenu: (Track) -> Unit, currentId: String?) {
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Playlist?>(null) }
    var delete by remember { mutableStateOf<Playlist?>(null) }
    val selected = route.removePrefix("playlist:").toLongOrNull()?.let { id -> playlists.firstOrNull { it.id == id } }
    val allTracks = remember(favorites, history, locals, playlists) { (favorites + locals + history + playlists.flatMap { it.tracks }).distinctBy { it.id } }
    Column {
        if (selected != null) {
            PlaylistScreen(selected, vm, onBack = { onRoute("all") }, onMenu = onMenu, currentId = currentId,
                rename = { rename = selected }, delete = { delete = selected }, onSearch = onSearch)
        } else {
            ScreenHeader("Your library", "Kept close. Always yours.", action = { TactileIcon(Icons.Rounded.Add, "Create playlist", { create = true }) })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.page).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Overview", "favorites" to "Favorites", "local" to "On device", "downloads" to "Downloads", "albums" to "Albums", "artists" to "Artists", "history" to "History").forEach { (id, label) ->
                    val haptics = LocalWonderHaptics.current
                    FilterChip(selected = route == id, onClick = { haptics.perform(HapticEvent.SELECT); onRoute(id) }, label = { Text(label) },
                        shape = Shape.control, border = null,
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface, selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary), modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            when (route) {
                "downloads" -> DownloadsScreen(vm,onMenu)
                "all" -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalOverlayBottom.current)) {
                    item { Row(Modifier.padding(horizontal = Space.page, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LibraryTile("Favorites", "${favorites.size} tracks", Icons.Rounded.Favorite, Modifier.weight(1f)) { onRoute("favorites") }
                        LibraryTile("On device", "${locals.size} tracks", Icons.Rounded.FolderOpen, Modifier.weight(1f)) { onRoute("local") }
                    } }
                    item { Spacer(Modifier.height(24.dp)); SectionHeading("Playlists", action = "New", onAction = { create = true }) }
                    if (playlists.isEmpty()) item { EmptyState("Make a little collection", "For the long way home, the late hours, or whatever feels right.", Icons.Rounded.PlaylistAdd,
                        "Create a playlist", { create = true }) }
                    items(playlists, key = { it.id }) { playlist ->
                        CollectionRow(playlist.name, "${playlist.tracks.size} tracks", playlist.tracks.firstOrNull(), Icons.Rounded.QueueMusic) { onRoute("playlist:${playlist.id}") }
                    }
                    item { Spacer(Modifier.height(16.dp)); ActionRow(Icons.Rounded.History, "Listening history", "${history.size} recent tracks") { onRoute("history") } }
                    item { ActionRow(Icons.Rounded.FileDownload, "Bring your own music", "Choose audio files from your device", onImport) }
                }
                "albums", "artists" -> {
                    val groups = remember(allTracks, route) { allTracks.groupBy { if (route == "albums") it.album.ifBlank { "Singles & unknown albums" } + " · " + it.artist else it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
                    if (groups.isEmpty()) EmptyState("A library that grows with you", "Save a favorite, start a playlist, or import your music to find it here.", Icons.Rounded.Album, "Find music", onSearch)
                    else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalOverlayBottom.current)) {
                        items(groups.entries.toList(), key = { it.key }) { group ->
                            CollectionRow(group.key, "${group.value.size} saved tracks", group.value.first(), if (route == "albums") Icons.Rounded.Album else Icons.Rounded.Person) {
                                onCollection(MusicCollection("library:${route}:${group.key}", group.key, "In your library", group.value.first().artworkUrl, group.value))
                            }
                        }
                    }
                }
                else -> {
                    val tracks = when (route) { "favorites" -> favorites; "local" -> locals; else -> history }
                    val title = when (route) { "favorites" -> "The ones you love"; "local" -> "Your music, with you"; else -> "A fresh start" }
                    val body = when (route) { "favorites" -> "Tap the heart on a track to keep it here."; "local" -> "Open audio files from your device. They stay on your device, ready for offline listening."; else -> "Tracks you listen to will find their way here." }
                    if (tracks.isEmpty()) EmptyState(title, body, if (route == "local") Icons.Rounded.FolderOpen else Icons.Rounded.MusicNote,
                        if (route == "local") "Choose audio files" else "Find music", if (route == "local") onImport else onSearch)
                    else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalOverlayBottom.current)) {
                        item { CollectionActions(tracks, vm, extraLabel = if (route == "local") "Import" else null, extra = onImport) }
                        items(tracks, key = { it.id }) { track -> TrackRow(track, { vm.player.play(tracks, tracks.indexOf(track)) }, { onMenu(track) },
                            current = currentId == track.id, favorite = favorites.any { it.id == track.id }) }
                    }
                }
            }
        }
    }
    if (create) NameDialog("New playlist", "Give it a name", "Create", onDismiss = { create = false }) { vm.createPlaylist(it); create = false }
    rename?.let { playlist -> NameDialog("Rename playlist", "Playlist name", "Save", initial = playlist.name, onDismiss = { rename = null }) { vm.renamePlaylist(playlist.id, it); rename = null } }
    delete?.let { playlist -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete ${playlist.name}?") }, text = { Text("This removes the playlist from your library. Your audio files and favorites stay where they are.") },
        confirmButton = { TextButton(onClick = { vm.deletePlaylist(playlist.id); delete = null; onRoute("all") }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { delete = null }) { Text("Keep playlist") } }) }
}

@Composable
private fun LibraryTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val haptics = LocalWonderHaptics.current
    Surface(onClick = { haptics.perform(HapticEvent.TAP); onClick() }, color = MaterialTheme.colorScheme.surfaceContainer, shape = Shape.control, modifier = modifier) {
        Column(Modifier.padding(18.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(24.dp)); Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun ActionRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    val haptics = LocalWonderHaptics.current
    Row(Modifier.fillMaxWidth().clickable { haptics.perform(HapticEvent.TAP); onClick() }.padding(horizontal = Space.page, vertical = 14.dp).heightIn(min = 38.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CollectionRow(title: String, subtitle: String, track: Track?, icon: ImageVector, onClick: () -> Unit) {
    val haptics = LocalWonderHaptics.current
    Row(Modifier.fillMaxWidth().clickable { haptics.perform(HapticEvent.TAP); onClick() }.padding(horizontal = Space.page, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (track == null) Box(Modifier.size(60.dp).background(MaterialTheme.colorScheme.surfaceContainer, Shape.artwork), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        } else Artwork(track, Modifier.size(60.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun CollectionActions(tracks: List<Track>, vm: AppViewModel, extraLabel: String? = null, extra: () -> Unit = {}) {
    if (tracks.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryAction("Play", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { vm.player.setShuffle(false); vm.player.play(tracks) }
        QuietAction("Shuffle", Icons.Rounded.Shuffle, Modifier.weight(1f)) { vm.player.play(tracks, tracks.indices.random()); vm.player.setShuffle(true) }
        if (extraLabel != null) TactileIcon(Icons.Rounded.Add, extraLabel, extra)
    }
}

@Composable
private fun PlaylistScreen(playlist: Playlist, vm: AppViewModel, onBack: () -> Unit, onMenu: (Track) -> Unit, currentId: String?, rename: () -> Unit, delete: () -> Unit, onSearch: () -> Unit) {
    var options by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    Column {
        ScreenHeader(playlist.name, "${playlist.tracks.size} tracks · Playlist", onBack) {
            Box { TactileIcon(Icons.Rounded.MoreHoriz, "Playlist options", { options = true })
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text(if (editing) "Finish editing" else "Edit tracks") }, onClick = { editing = !editing; options = false }, leadingIcon = { Icon(Icons.Rounded.Sort, null) })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { options = false; rename() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { options = false; delete() }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
                }
            }
        }
        if (playlist.tracks.isEmpty()) EmptyState("A blank side, just for you", "Find a song, open its options, and add it to this playlist.", Icons.Rounded.PlaylistAdd, "Find music", onSearch)
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalOverlayBottom.current)) {
            item { CollectionActions(playlist.tracks, vm) }
            if (editing) item { Text("Use the arrows to arrange your tracks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.page, vertical = 8.dp)) }
            items(playlist.tracks, key = { it.id }) { track ->
                val index = playlist.tracks.indexOf(track)
                TrackRow(track, { vm.player.play(playlist.tracks, index) }, { onMenu(track) }, current = currentId == track.id,
                    trailing = if (editing) ({ Row {
                        TactileIcon(Icons.Rounded.ArrowUpward, "Move ${track.title} up", { vm.movePlaylistTrack(playlist.id, index, index - 1) }, enabled = index > 0, event = HapticEvent.REORDER)
                        TactileIcon(Icons.Rounded.ArrowDownward, "Move ${track.title} down", { vm.movePlaylistTrack(playlist.id, index, index + 1) }, enabled = index < playlist.tracks.lastIndex, event = HapticEvent.REORDER)
                        TactileIcon(Icons.Rounded.Close, "Remove ${track.title} from playlist", { vm.removeFromPlaylist(playlist.id, track.id) })
                    } }) else null)
            }
        }
    }
}

@Composable
internal fun CollectionScreen(collection: MusicCollection, vm: AppViewModel, onBack: () -> Unit, onMenu: (Track) -> Unit, currentId: String?) {
    Column {
        ScreenHeader(collection.title, collection.subtitle.ifBlank { "${collection.tracks.size} tracks" }, onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalOverlayBottom.current)) {
            item { Row(Modifier.padding(horizontal = Space.page, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(collection.tracks.firstOrNull(), Modifier.size(110.dp), artwork = collection.artworkUrl, seed = collection.id)
                Column(Modifier.padding(start = 20.dp)) {
                    Text("${collection.tracks.size} tracks", style = MaterialTheme.typography.titleMedium)
                    collection.year?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                    val duration = collection.tracks.sumOf { it.durationMs }
                    if (duration > 0) Text("${duration / 60000} minutes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            } }
            item { CollectionActions(collection.tracks, vm) }
            if (collection.tracks.isEmpty()) item { EmptyState("Nothing to play here yet", "This source didn’t return any available tracks.") }
            items(collection.tracks, key = { it.id }) { track -> TrackRow(track, { vm.player.play(collection.tracks, collection.tracks.indexOf(track)) }, { onMenu(track) }, current = currentId == track.id) }
        }
    }
}

@Composable
internal fun NameDialog(title: String, label: String, action: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(value, { value = it.take(100) }, singleLine = true, label = { Text(label) }, shape = Shape.control,
            modifier = Modifier.fillMaxWidth())
    }, confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onSave(value.trim()) }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
