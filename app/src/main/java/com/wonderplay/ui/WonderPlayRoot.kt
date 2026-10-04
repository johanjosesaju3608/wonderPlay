package com.wonderplay.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonderplay.AppViewModel
import com.wonderplay.domain.*
import com.wonderplay.source.YouTubeMusicSource

private data class Page(val tab: String, val settings: Boolean, val detailOpen: Boolean, val detail: MusicCollection?, val library: String, val artist:Artist?=null,val artists:List<ArtistRef> = emptyList(),val trackDetails:TrackDetails?=null) {
    val key get() = when { settings -> "settings"; detailOpen -> "detail:${artist?.id ?: trackDetails?.track?.id ?: detail?.id ?: artists.joinToString {it.id}}"; tab == "Library" -> "Library:$library"; else -> tab }
    val order get() = when { settings || detailOpen -> 20; tab == "Library" && library != "all" -> 10; else -> listOf("Home", "Search", "Library").indexOf(tab) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WonderPlayRoot(viewModel:AppViewModel) {
    val vm=viewModel
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val locals by vm.localTracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val recent by vm.recentSearches.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Home") }
    var libraryRoute by rememberSaveable { mutableStateOf("all") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var queue by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Track?>(null) }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var collection by remember { mutableStateOf<MusicCollection?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val context=LocalContext.current
    val view=LocalView.current
    val haptics=remember(view,settings.haptics) { HapticsController(view,settings.haptics) }
    val snackbar=remember { SnackbarHostState() }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importLocal(it) }
    val import={ picker.launch(arrayOf("audio/*")) }
    val search={ vm.search(""); tab="Search"; libraryRoute="all"; collection=null; vm.closeAllDetails() }
    fun open(uri:Uri) { try { context.startActivity(Intent(Intent.ACTION_VIEW,uri)) } catch(_:android.content.ActivityNotFoundException) { Toast.makeText(context,"No app can open this link.",Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }
    LaunchedEffect(player.current) { if(player.current==null) { expanded=false; queue=false } }
    val detail=collection ?: ui.collection ?: ui.artist?.let { MusicCollection(it.id,it.name,"Artist · ${it.tracks.size} tracks",it.artworkUrl,it.tracks) }
    BackHandler(enabled=expanded || showSettings || detail!=null || ui.artistPicker.isNotEmpty() || ui.trackDetails!=null || ui.detailLoading || libraryRoute!="all" || tab!="Home") {
        when { expanded -> expanded=false; showSettings -> showSettings=false; detail!=null || ui.artistPicker.isNotEmpty() || ui.trackDetails!=null || ui.detailLoading -> { collection=null; vm.closeDetail() }; libraryRoute!="all" -> libraryRoute="all"; else -> tab="Home" }
    }
    WonderTheme(settings, player.current?.artworkUrl) {
        CompositionLocalProvider(LocalWonderHaptics provides haptics) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    CompositionLocalProvider(LocalOverlayBottom provides (if(player.current != null) 184.dp else 100.dp)) {
                        val page = Page(tab, showSettings, detail != null || ui.artistPicker.isNotEmpty() || ui.trackDetails!=null || ui.detailLoading, detail, libraryRoute, ui.artist, ui.artistPicker, ui.trackDetails)
                        AnimatedContent(page, modifier = Modifier.fillMaxSize().clipToBounds(), contentKey = { it.key },
                            transitionSpec = {
                                val duration = if(settings.reducedMotion) 0 else 240
                                val direction = if(targetState.order >= initialState.order) 1 else -1
                                (slideInHorizontally(tween(duration)) { direction * it / 6 } + fadeIn(tween(duration))) togetherWith
                                    (slideOutHorizontally(tween(duration)) { -direction * it / 6 } + fadeOut(tween(duration)))
                            }, label = "Page transition") { shown ->
                            when {
                                shown.settings -> SettingsScreen(settings,vm) { showSettings=false }
                                shown.detailOpen -> when {
                                    shown.artist!=null -> ArtistDetailScreen(shown.artist,vm,vm::closeDetail,{menu=it},player.current?.id)
                                    shown.artists.isNotEmpty() -> ArtistPickerScreen(shown.artists,vm,vm::closeDetail)
                                    shown.trackDetails!=null -> TrackDetailsScreen(shown.trackDetails,vm,vm::closeDetail,{menu=it})
                                    shown.detail!=null -> CollectionScreen(shown.detail,vm,{collection=null;vm.closeDetail()},{menu=it},player.current?.id)
                                    else -> Column {ScreenHeader("Opening music",onBack=vm::closeDetail);LinearProgressIndicator(Modifier.fillMaxWidth().padding(Space.page))}
                                }
                                shown.tab=="Home" -> HomeScreen(vm,history,favorites,locals,search,import,{showSettings=true},{libraryRoute=it;tab="Library"},{menu=it},player.current?.id)
                                shown.tab=="Search" -> SearchScreen(vm,ui.query,ui.searchTracks,ui.searching,ui.searchError,ui.hasMore,recent,favorites,player.current?.id,{menu=it},{open(YouTubeMusicSource.searchUrl(it))})
                                else -> LibraryScreen(vm,favorites,history,locals,playlists,shown.library,{libraryRoute=it},import,search,{collection=it},{menu=it},player.current?.id)
                            }
                        }
                    }
                    FloatingNavigation(tab, { label -> focusManager.clearFocus(); keyboard?.hide(); haptics.perform(HapticEvent.SELECT); if(label=="Search" && tab!="Search") vm.search(""); tab=label; showSettings=false; collection=null; vm.closeAllDetails() }, Modifier.align(Alignment.BottomCenter))
                    player.current?.let { PlayerSurface(player,vm,expanded,{expanded=it},favorites.any { t->t.id==it.id },{vm.toggleFavorite(it)},{queue=true},{menu=it}) }
                    SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(bottom=if(player.current!=null) 152.dp else 72.dp))
                }
            }
            if(queue) ModalBottomSheet(onDismissRequest={queue=false},containerColor=MaterialTheme.colorScheme.surface) { QueueSheet(player,vm,{queue=false}) }
            menu?.let { track ->
                ModalBottomSheet(onDismissRequest={menu=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.85f).dp).verticalScroll(rememberScrollState())) {
                    TrackRow(track,{vm.player.play(listOf(track));menu=null},{menu=null})
                    ActionRow(if(favorites.any { it.id==track.id }) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,"${if(favorites.any { it.id==track.id }) "Remove from" else "Add to"} favorites") { vm.toggleFavorite(track); menu=null }
                    DownloadAction(track,vm,menu=true,onDone={menu=null})
                    if(track.id != player.current?.id) ActionRow(Icons.Rounded.PlaylistPlay,"Play next") { vm.player.addNext(track); menu=null }
                    ActionRow(Icons.Rounded.QueueMusic,"Add to queue") { vm.player.enqueue(track); menu=null }
                    ActionRow(Icons.Rounded.PlaylistAdd,"Add to playlist") { addTrack=track; menu=null }
                    ActionRow(Icons.Rounded.Person,"View artist") { vm.openArtist(track); menu=null; expanded=false }
                    ActionRow(Icons.Rounded.Info,"Track details") { vm.openTrackDetails(track); menu=null; expanded=false }
                    if(track.source=="local") ActionRow(Icons.Rounded.RemoveCircleOutline,"Remove from library","The file itself stays on your device") { vm.removeLocal(track); menu=null }
                    Spacer(Modifier.height(24.dp))
                    }
                }
            }
            addTrack?.let { track -> ModalBottomSheet(onDismissRequest={addTrack=null},containerColor=MaterialTheme.colorScheme.surface) {
                ScreenHeader("Add to playlist",track.title)
                LazyColumn(Modifier.heightIn(max=440.dp)) {
                    item { ActionRow(Icons.Rounded.Add,"New playlist") { newPlaylist=true } }
                    items(playlists,key={it.id}) { list -> ActionRow(Icons.Rounded.QueueMusic,list.name,"${list.tracks.size} tracks") { vm.addToPlaylist(list.id,track); addTrack=null } }
                }
                Spacer(Modifier.height(24.dp))
            } }
            if(newPlaylist) NameDialog("New playlist","Name","Create",onDismiss={newPlaylist=false}) { vm.createPlaylist(it,addTrack); newPlaylist=false; addTrack=null }
        }
    }
}
