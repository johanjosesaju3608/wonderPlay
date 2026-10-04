package com.wonderplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonderplay.AppViewModel
import com.wonderplay.domain.Track

@Composable
internal fun HomeScreen(vm: AppViewModel, history: List<Track>, favorites: List<Track>, local: List<Track>, onSearch: () -> Unit,
    onImport: () -> Unit, onSettings: () -> Unit, onLibrary: (String) -> Unit, onMenu: (Track) -> Unit, currentId: String?) {
    val home by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadDiscovery() }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp + LocalOverlayBottom.current)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = Space.page, end = 12.dp, top = 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                WonderMark(Modifier.size(28.dp)); Spacer(Modifier.width(10.dp))
                Text("wonderPlay", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TactileIcon(Icons.Rounded.Settings, "Settings", onSettings)
            }
        }
        item {
            Column(Modifier.padding(horizontal = Space.page).padding(top = 14.dp, bottom = 28.dp)) {
                Text("JUST YOU & THE MUSIC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Text(if (history.isEmpty()) "Good music.\nYour own rhythm." else "Back to\nyour kind of sound.", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(16.dp))
                Text(if (history.isEmpty()) "Find a favorite artist. Discover a new song. Make a little room for listening." else "A familiar favorite, or something you haven’t heard yet. Settle in.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 340.dp))
                PrimaryAction("Find your next listen", Icons.Rounded.Search, Modifier.padding(top = 24.dp), onSearch)
            }
        }
        item { SectionHeading(if(home.personalized) "Made for your listening" else "Discover a new favorite", if(home.personalized) "Inspired by your recent plays and favorites" else "Your next favorite starts here") }
        if(home.recommendationsLoading && home.recommendations.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Space.page)) }
        if(home.recommendations.isNotEmpty()) item { ArtworkRail(home.recommendations, vm, onMenu) }
        home.recommendationsError?.let { message -> item { FailureState(message, { vm.loadDiscovery(true) }) } }
        item { SectionHeading("Featured playlists", "From YouTube Music", "Refresh", vm::loadFeatured) }
        if (home.featuredLoading && home.featured.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Space.page)) }
        home.featuredError?.let { error -> item { FailureState(error, vm::loadFeatured) } }
        if (home.featured.isNotEmpty()) item {
            LazyRow(contentPadding = PaddingValues(horizontal = Space.page, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(home.featured, key = { it.id }) { list ->
                    Column(Modifier.width(160.dp).clickable { vm.openPlaylist(list) }) {
                        Artwork(Track("playlist:${list.id}", list.title, "YouTube Music", artworkUrl = list.artworkUrl), Modifier.size(160.dp).clip(Shape.artwork), "Open ${list.title}")
                        Text(list.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                        Text(list.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionHeading("Recently played", action = "See all", onAction = { onLibrary("history") }) }
            item { ArtworkRail(history.take(12), vm, onMenu) }
        } else {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = Shape.control,
                    modifier = Modifier.padding(horizontal = Space.page).fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                            Text("A little less noise.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                        }
                        Text("No account. No ads. A place for your favorite recordings and your own collection.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item { Spacer(Modifier.height(24.dp)); SectionHeading("On repeat", "The tracks you’ve made your own", "Favorites", { onLibrary("favorites") }) }
            items(favorites.take(4), key = { "favorite:${it.id}" }) { track -> TrackRow(track, { vm.player.play(favorites, favorites.indexOf(track)) }, { onMenu(track) }, current = currentId == track.id, favorite = true) }
        }
        item { Spacer(Modifier.height(28.dp)); SectionHeading("Closer to home", if (local.isEmpty()) "Your collection belongs here, too" else "Music from your device", if (local.isNotEmpty()) "See all" else null, { onLibrary("local") }) }
        if (local.isEmpty()) item { ActionRow(Icons.Rounded.FolderOpen, "Bring your own music", "Open audio files. Listen anywhere.", onImport) }
        else items(local.take(3), key = { "local:${it.id}" }) { track -> TrackRow(track, { vm.player.play(local, local.indexOf(track)) }, { onMenu(track) }, current = currentId == track.id) }
    }
}

@Composable
private fun ArtworkRail(tracks: List<Track>, vm: AppViewModel, onMenu: (Track) -> Unit) {
    val haptics = LocalWonderHaptics.current
    LazyRow(contentPadding = PaddingValues(horizontal = Space.page, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(tracks, key = { it.id }) { track ->
            Column(Modifier.width(150.dp)) {
                Artwork(track, Modifier.size(150.dp).clip(Shape.artwork).clickable { haptics.perform(HapticEvent.TAP); vm.player.play(tracks, tracks.indexOf(track)) }, "Play ${track.title}")
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    }
                    TactileIcon(Icons.Rounded.MoreHoriz, "Options for ${track.title}", { onMenu(track) })
                }
            }
        }
    }
}

@Composable
internal fun SearchScreen(vm: AppViewModel, query: String, tracks: List<Track>, searching: Boolean, error: String?, hasMore: Boolean,
    recent: List<String>, favorites: List<Track>, currentId: String?, onMenu: (Track) -> Unit, onExternalSearch: (String) -> Unit) {
    val searchUi by vm.ui.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { vm.loadDiscovery() }
    Column {
        ScreenHeader("Search", "Find a sound that stays with you")
        Row(Modifier.padding(horizontal = Space.page).padding(bottom = 8.dp).fillMaxWidth().heightIn(min = 56.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, Shape.control).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            BasicTextField(value = query, onValueChange = vm::search, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focusManager.clearFocus() }),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp).focusRequester(focus).semantics { contentDescription = "Search music" },
                decorationBox = { inner -> Box { if (query.isEmpty()) Text("Songs, artists, a feeling…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
            if (query.isNotEmpty()) TactileIcon(Icons.Rounded.Close, "Clear search", { vm.search(""); keyboard?.hide(); focusManager.clearFocus() })
            else Spacer(Modifier.width(16.dp))
        }
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).padding(horizontal = Space.page), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface)
        else Spacer(Modifier.height(2.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + LocalOverlayBottom.current)) {
            if (query.isBlank()) {
                if (recent.isNotEmpty()) {
                    item { Spacer(Modifier.height(12.dp)); SectionHeading("Recent searches", action = "Clear", onAction = vm::clearSearches) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recent, key = { it }) { term ->
                                SuggestionChip(modifier = Modifier.widthIn(max = 220.dp), onClick = { keyboard?.hide(); focusManager.clearFocus(); vm.search(term) }, label = { Text(term, maxLines = 1) }, shape = androidx.compose.foundation.shape.CircleShape)
                            }
                        }
                    }
                }
                item { SectionHeading("Charts & top songs", "Official YouTube Music playlists") }
                if(searchUi.chartsLoading && searchUi.charts.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(Space.page)) }
                searchUi.chartsError?.let { message -> item { FailureState(message, { vm.loadDiscovery(true) }) } }
                items(searchUi.charts.chunked(2), key = { row -> "charts:" + row.first().id }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { list ->
                            Surface(onClick = { keyboard?.hide(); focusManager.clearFocus(); vm.openPlaylist(list) }, modifier = Modifier.weight(1f), shape = Shape.artwork, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Column(Modifier.padding(12.dp)) {
                                    Artwork(Track("chart:${list.id}", list.title, "YouTube Music", artworkUrl = list.artworkUrl), Modifier.fillMaxWidth().aspectRatio(1.6f).clip(Shape.control), "Open ${list.title}")
                                    Text(list.title, Modifier.padding(top = 10.dp).heightIn(min = 40.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if(row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item { Spacer(Modifier.height(16.dp)); SectionHeading(if(searchUi.personalized) "Made for your listening" else "Discover a new favorite",
                    if(searchUi.personalized) "Inspired by your recent plays and favorites" else "Listen to a few songs to make this yours") }
                if(searchUi.recommendationsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(Space.page)) }
                searchUi.recommendationsError?.let { message -> item { FailureState(message, { vm.loadDiscovery(true) }) } }
                items(searchUi.recommendations, key = { "recommendation:${it.id}" }) { track ->
                    TrackRow(track, { vm.player.play(searchUi.recommendations, searchUi.recommendations.indexOf(track)) }, { onMenu(track) }, current = track.id == currentId, favorite = favorites.any { it.id == track.id })
                }
            } else {
                if (searchUi.searchCollections.isNotEmpty()) {
                    item { SectionHeading("Albums & official playlists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = Space.page, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(searchUi.searchCollections, key = { it.id }) { list ->
                                Column(Modifier.width(160.dp).clickable { keyboard?.hide(); focusManager.clearFocus(); vm.openPlaylist(list) }) {
                                    Artwork(Track("collection:${list.id}", list.title, list.subtitle, artworkUrl = list.artworkUrl), Modifier.size(160.dp).clip(Shape.artwork), "Open ${list.title}")
                                    Text(list.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(list.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (searchUi.collectionsLoading) item { Text("Finding albums and official playlists…", Modifier.padding(horizontal = Space.page, vertical = 12.dp), style = MaterialTheme.typography.bodySmall) }
                searchUi.collectionsError?.let { message -> item { FailureState(message, vm::retrySearch) } }
                if (tracks.isNotEmpty()) {
                    item { Text("${tracks.size}${if (hasMore) "+" else ""} TRACKS", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.page, vertical = 20.dp)) }
                    items(tracks, key = { it.id }) { track -> TrackRow(track,
                        { keyboard?.hide(); focusManager.clearFocus(); vm.player.play(tracks, tracks.indexOf(track)) }, { keyboard?.hide(); onMenu(track) },
                        current = track.id == currentId, favorite = favorites.any { it.id == track.id }) }
                }
                if (error != null) item { FailureState(error, vm::retrySearch) }
                else if (!searching && !searchUi.collectionsLoading && tracks.isEmpty() && searchUi.searchCollections.isEmpty()) item { EmptyState("A different kind of discovery", "No playable tracks matched “${query.trim()}”. Try an artist, track title, or genre.", Icons.Rounded.SearchOff) }
                if (hasMore && !searching && error == null) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { QuietAction("More tracks", Icons.Rounded.Add, onClick = vm::loadMore) } }
                item {
                    Column(Modifier.padding(horizontal = Space.page, vertical = 22.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Open the original service", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
                        Text("If a track is unavailable here, you can also search in YouTube Music.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                        TextButton(onClick = { keyboard?.hide(); onExternalSearch(query) }, contentPadding = PaddingValues(0.dp)) {
                            Text("Open YouTube Music"); Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
