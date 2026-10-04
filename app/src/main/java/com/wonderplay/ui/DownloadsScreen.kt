package com.wonderplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wonderplay.AppViewModel
import com.wonderplay.domain.Track

@Composable
internal fun DownloadAction(track:Track,vm:AppViewModel,menu:Boolean=false,onDone:()->Unit={}) {
 val entries by vm.downloads.collectAsStateWithLifecycle()
 val eligibility by vm.downloadEligibility.collectAsStateWithLifecycle()
 val entry=entries.firstOrNull {it.id==track.id}
 LaunchedEffect(track.id) {vm.checkDownload(track)}
 val active=entry?.status in setOf("Queued","Preparing","Downloading")
 if(track.source=="local" || (entry?.status!="Ready" && !active && eligibility[track.id]!=true)) return
 val label=when {entry?.status=="Ready"->"Downloaded";active->"Cancel download";else->"Download"}
 val action={when {entry?.status=="Ready"->if(menu) vm.removeDownload(track.id) else vm.showDownloadedStatus();active->vm.cancelDownload(track.id);else->vm.download(track)};onDone()}
 if(menu) ActionRow(if(entry?.status=="Ready") Icons.Rounded.OfflinePin else Icons.Rounded.Download,if(entry?.status=="Ready") "Remove download" else label,onClick=action)
 else Box(Modifier.size(48.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {
     IconButton(onClick=action) { Icon(when {entry?.status=="Ready"->Icons.Rounded.OfflinePin;active->Icons.Rounded.Close;else->Icons.Rounded.Download},label,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp)) }
     if(active) {
         if(entry?.status=="Downloading" && entry.total>0) CircularProgressIndicator(progress={(entry.bytes.toFloat()/entry.total).coerceIn(0f,1f)},modifier=Modifier.size(38.dp),strokeWidth=2.dp)
         else CircularProgressIndicator(modifier=Modifier.size(38.dp),strokeWidth=2.dp)
     }
 }
}

@Composable
internal fun DownloadsScreen(vm:AppViewModel,onMenu:(Track)->Unit) {
 val entries by vm.downloads.collectAsStateWithLifecycle()
 val music by vm.licensedMusic.collectAsStateWithLifecycle()
 val loading by vm.licensedLoading.collectAsStateWithLifecycle()
 val error by vm.licensedError.collectAsStateWithLifecycle()
 var query by rememberSaveable {mutableStateOf("")}
 var browse by rememberSaveable {mutableStateOf(false)}
 val uri=LocalUriHandler.current
 LazyColumn(contentPadding=PaddingValues(bottom=24.dp+LocalOverlayBottom.current)) {
  item {Column(Modifier.padding(horizontal=Space.page,vertical=12.dp)) {
   Text("Music for offline moments",style=MaterialTheme.typography.titleLarge)
   Text("Downloads stay inside wonderPlay. Only recordings with a published download license are saved.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   TextButton(onClick={browse=!browse;if(browse && music.isEmpty()) vm.discoverLicensedMusic()}) {Text(if(browse) "Hide music available to download" else "Find music available to download")}
  }}
  if(entries.isEmpty()) item {Text("Your downloads will appear here.",Modifier.padding(Space.page),color=MaterialTheme.colorScheme.onSurfaceVariant)}
  items(entries,key={it.id}) {entry ->
   val track=vm.downloadedTrack(entry)
   Column {
    TrackRow(track,{if(entry.status=="Ready") vm.player.play(entries.filter {it.status=="Ready"}.map(vm::downloadedTrack),entries.filter {it.status=="Ready"}.indexOf(entry).coerceAtLeast(0))},{onMenu(track)})
    Column(Modifier.padding(start=Space.page,end=Space.page,bottom=16.dp)) {
     Text(if(entry.status=="Ready") "Offline · ${"%.1f".format(entry.bytes/1048576.0)} MB" else entry.status,style=MaterialTheme.typography.labelLarge)
     if(entry.status=="Downloading" && entry.total>0) LinearProgressIndicator(progress={ (entry.bytes.toFloat()/entry.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().padding(top=8.dp))
     if(entry.error.isNotBlank()) Text(entry.error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     Row {
      when(entry.status) {
       "Ready" -> {TextButton(onClick={vm.removeDownload(entry.id)}) {Text("Remove")};TextButton(onClick={uri.openUri(entry.license)}) {Text("License")};TextButton(onClick={uri.openUri(entry.page)}) {Text("Source")}}
       "Queued","Preparing","Downloading" -> TextButton(onClick={vm.cancelDownload(entry.id)}) {Text("Cancel")}
       else -> {TextButton(onClick={vm.download(track)}) {Text("Retry")};TextButton(onClick={vm.removeDownload(entry.id)}) {Text("Remove")}}
      }
     }
    }
   }
  }
  if(browse) {
   item {Column(Modifier.padding(Space.page)) {
    Text("Licensed music",style=MaterialTheme.typography.titleLarge)
    OutlinedTextField(query,{query=it},label={Text("Search artists or releases")},singleLine=true,modifier=Modifier.fillMaxWidth())
    TextButton(onClick={vm.discoverLicensedMusic(query)},enabled=!loading) {Text("Search")}
    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let {Text(it)}
    if(!loading && error==null && music.isEmpty()) Text("No licensed recordings found for this search.")
    Text("Published Creative Commons licenses from Internet Archive netlabels. These are separate from the YouTube catalog.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }}
   items(music,key={"licensed:${it.track.id}"}) {permit -> Column {TrackRow(permit.track,{vm.player.play(music.map {it.track},music.indexOf(permit))},{onMenu(permit.track)});Row(Modifier.padding(horizontal=Space.page)) {DownloadAction(permit.track,vm);TextButton(onClick={uri.openUri(permit.license)}) {Text("License")}}}}
  }
 }
}
