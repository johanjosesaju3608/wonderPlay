package com.wonderplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wonderplay.AppViewModel
import com.wonderplay.domain.*

@Composable
internal fun ArtistPickerScreen(artists:List<ArtistRef>,vm:AppViewModel,onBack:()->Unit) {
    Column {ScreenHeader("Artists","Choose an artist",onBack)
        LazyColumn(contentPadding=PaddingValues(bottom=24.dp+LocalOverlayBottom.current)) {items(artists.chunked(2),key={it.first().id}) {row ->
            DetailCards(row.map {DetailCard(it.id,it.name,"Artist",it.artworkUrl) {vm.openArtist(it)}})
        }}
    }
}
@Composable
internal fun ArtistDetailScreen(artist:Artist,vm:AppViewModel,onBack:()->Unit,onMenu:(Track)->Unit,currentId:String?) {
    val local=artist.id.startsWith("local-artist:")
    Column {ScreenHeader(artist.name,if(local) "Artist · On your device" else "Artist · YouTube Music",onBack)
        LazyColumn(contentPadding=PaddingValues(bottom=24.dp+LocalOverlayBottom.current)) {
            item {Artwork(null,Modifier.fillMaxWidth().padding(horizontal=Space.page).height(210.dp),artwork=artist.artworkUrl,seed=artist.id)}
            item {SectionHeading(if(local) "Tracks" else "Top tracks",if(local) "From your library" else "Live from the artist's YouTube Music page")}
            if(artist.tracks.isEmpty()) item {Text("No top tracks were returned for this artist.",Modifier.padding(Space.page),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(artist.tracks,key={it.id}) {track ->TrackRow(track,{vm.player.play(artist.tracks,artist.tracks.indexOf(track))},{onMenu(track)},current=track.id==currentId,number=artist.tracks.indexOf(track)+1)}
            if(artist.playlists.isNotEmpty()) {
                item {SectionHeading("Artist playlists","Official selections & playlists from the artist page")}
                items(artist.playlists.chunked(2),key={"playlists:${it.first().id}"}) {row ->DetailCards(row.map {list->DetailCard(list.id,list.title,list.subtitle,list.artworkUrl) {vm.openPlaylist(list)}})}
            }
            if(artist.albums.isNotEmpty()) {
                item {SectionHeading("Albums")}
                items(artist.albums.chunked(2),key={"albums:${it.first().id}"}) {row ->DetailCards(row.map {list->DetailCard(list.id,list.title,list.subtitle,list.artworkUrl) {vm.openPlaylist(list)}})}
            }
        }
    }
}
@Composable
internal fun TrackDetailsScreen(details:TrackDetails,vm:AppViewModel,onBack:()->Unit,onMenu:(Track)->Unit) {
    val track=details.track
    Column {ScreenHeader("Track details",onBack=onBack)
        LazyColumn(contentPadding=PaddingValues(bottom=24.dp+LocalOverlayBottom.current)) {
            item {TrackRow(track,{vm.player.play(listOf(track))},{onMenu(track)})}
            item {Column(Modifier.padding(Space.page)) {
                if(track.album.isNotBlank()) Text(track.album,style=MaterialTheme.typography.titleMedium)
                Text(if(track.source=="local") "On your device" else if(track.source=="archive") "Internet Archive" else "YouTube Music",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(track.durationMs>0) Text(timeLabel(track.durationMs),style=MaterialTheme.typography.bodySmall)
                track.year?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
            }}
            item {SectionHeading("Artists")}
            if(details.artists.isEmpty()) item {Text("Linked artist details are unavailable for this track.",Modifier.padding(Space.page),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(details.artists.chunked(2),key={"artists:${it.first().id}"}) {row ->DetailCards(row.map {ref->DetailCard(ref.id,ref.name,"Artist",ref.artworkUrl) {vm.openArtist(ref)}})}
            item {SectionHeading("Albums")}
            if(details.albums.isEmpty()) item {Text("No linked album was provided for this recording.",Modifier.padding(Space.page),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(details.albums.chunked(2),key={"albums:${it.first().id}"}) {row ->DetailCards(row.map {list->DetailCard(list.id,list.title,"Album",list.artworkUrl) {if(track.source=="local" || track.source=="archive") vm.openAlbum(track) else vm.openPlaylist(list)}})}
        }
    }
}
private data class DetailCard(val id:String,val title:String,val subtitle:String,val art:String?,val click:()->Unit)
@Composable
private fun DetailCards(cards:List<DetailCard>) {
    Row(Modifier.fillMaxWidth().padding(horizontal=Space.page,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        cards.forEach {card ->Surface(onClick=card.click,shape=Shape.artwork,color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.weight(1f)) {
            Column(Modifier.padding(12.dp)) {
                Artwork(null,Modifier.fillMaxWidth().aspectRatio(1f).clip(Shape.control),artwork=card.art,seed=card.id)
                Text(card.title,Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(card.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }}
        if(cards.size==1) Spacer(Modifier.weight(1f))
    }
}
