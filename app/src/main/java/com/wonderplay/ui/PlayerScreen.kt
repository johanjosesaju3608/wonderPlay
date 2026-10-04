package com.wonderplay.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.wonderplay.AppViewModel
import com.wonderplay.domain.*
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSurface(state:PlayerState,vm:AppViewModel,expanded:Boolean,onExpanded:(Boolean)->Unit,favorite:Boolean,onFavorite:()->Unit,onQueue:()->Unit,onMenu:()->Unit) {
    val current=state.current ?: return
    val reduced=LocalReducedMotion.current
    val haptics=LocalWonderHaptics.current
    val animatedFraction by animateFloatAsState(if(expanded) 1f else 0f,if(reduced) tween(0) else spring(dampingRatio=.88f,stiffness=420f),label="player expansion")
    val fraction = playerLayoutFraction(animatedFraction)
    val scope=rememberCoroutineScope()
    val boundaryFade=remember { Animatable(1f) }
    val threshold=with(LocalDensity.current) { 48.dp.toPx() }
    fun pulse() { scope.launch { boundaryFade.animateTo(.2f,tween(if(reduced) 0 else 100)); boundaryFade.animateTo(1f,tween(if(reduced) 0 else 180)) } }
    val miniGesture = if(expanded) Modifier else Modifier.pointerInput(haptics,reduced,threshold) {
        var dx=0f; var dy=0f
        detectDragGestures(onDragStart={dx=0f;dy=0f},onDragEnd={
            when(miniPlayerGesture(dx,dy,threshold)) {
                MiniPlayerGesture.EXPAND -> { haptics.perform(HapticEvent.SELECT);onExpanded(true) }
                MiniPlayerGesture.DISMISS -> { haptics.perform(HapticEvent.DISMISS);onExpanded(false);vm.player.clearQueue() }
                MiniPlayerGesture.PREVIOUS -> {haptics.perform(HapticEvent.SKIP);vm.player.skipFromGesture(true,::pulse)}
                MiniPlayerGesture.NEXT -> {haptics.perform(HapticEvent.SKIP);vm.player.skipFromGesture(false,::pulse)}
                MiniPlayerGesture.NONE -> Unit
            }
        }) { change,drag -> change.consume();dx+=drag.x;dy+=drag.y }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width=maxWidth; val height=maxHeight
        val artSize=lerp(50.dp,minOf(width-48.dp,height*.39f),fraction)
        Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=lerp(88.dp,0.dp,fraction),start=lerp(12.dp,0.dp,fraction),end=lerp(12.dp,0.dp,fraction))
            .fillMaxWidth().height(lerp(72.dp,height,fraction)).alpha(boundaryFade.value).testTag(if(expanded) "Expanded player" else "Mini player").then(miniGesture),shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=22.dp,topEnd=22.dp,bottomStart=lerp(22.dp,0.dp,fraction),bottomEnd=lerp(22.dp,0.dp,fraction)),color=MaterialTheme.colorScheme.surfaceContainer,tonalElevation=0.dp) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(LocalPlayerGradient.current))) {
                val artModifier=Modifier.offset(x=lerp(10.dp,(width-artSize)/2,fraction),y=lerp(10.dp,64.dp,fraction)).size(artSize)
                Crossfade(current,modifier=artModifier,animationSpec=tween(if(reduced) 0 else 220),label="cover") { track ->
                    var dx by remember { mutableFloatStateOf(0f) }; var dy by remember { mutableFloatStateOf(0f) }
                    Artwork(track,Modifier.fillMaxSize().then(if(!expanded) Modifier else Modifier.pointerInput(expanded) {
                        detectDragGestures(onDragStart={dx=0f;dy=0f},onDragCancel={dx=0f;dy=0f},onDragEnd={
                            if(expanded) when { dy>90 && dy>abs(dx)->onExpanded(false); dx < -100 -> {haptics.perform(HapticEvent.SKIP);vm.player.next()}; dx>100 -> {haptics.perform(HapticEvent.SKIP);vm.player.previous()} }
                            else if(dy < -50) onExpanded(true)
                        }) { change,drag -> change.consume();dx+=drag.x;dy+=drag.y }
                    }),description="Artwork for ${track.title}")
                }
                if(fraction<.5f) Row(Modifier.fillMaxSize().clickable(enabled=!expanded) { haptics.perform(HapticEvent.SELECT);onExpanded(true) }.padding(start=72.dp,end=8.dp).alpha(1-fraction*2),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(current.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall); Text(current.artist,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
                    TactileIcon(if(state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(state.isPlaying) "Pause" else "Play",vm.player::togglePlayPause)
                    TactileIcon(Icons.Rounded.SkipNext,"Next track",vm.player::next,enabled=state.queue.size>1,event=HapticEvent.SKIP)
                }
                if(fraction>.5f) {
                    Row(Modifier.fillMaxWidth().height(56.dp).alpha((fraction-.5f)*2).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        TactileIcon(Icons.Rounded.KeyboardArrowDown,"Close player",{onExpanded(false)},event=HapticEvent.DISMISS)
                        Text("NOW PLAYING",style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        TactileIcon(Icons.Rounded.MoreHoriz,"Track options",onMenu)
                    }
                    Column(Modifier.fillMaxSize().padding(top=64.dp+artSize+22.dp).alpha((fraction-.5f)*2).verticalScroll(rememberScrollState()).padding(horizontal=28.dp).padding(bottom=22.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(current.title,style=MaterialTheme.typography.headlineMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(current.artist,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=5.dp))
                            }
                            TactileIcon(if(favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,if(favorite) "Remove favorite" else "Favorite track",onFavorite,selected=favorite,event=HapticEvent.FAVORITE)
                        }
                        DownloadAction(current,vm)
                        Spacer(Modifier.height(14.dp))
                        var seeking by remember(current.id) { mutableStateOf(false) }; var seek by remember(current.id) { mutableFloatStateOf(0f) }
                        Slider(value=if(seeking) seek else state.positionMs.toFloat().coerceIn(0f,state.durationMs.coerceAtLeast(1).toFloat()),
                            onValueChange={if(!seeking) haptics.perform(HapticEvent.SEEK);seeking=true;seek=it},
                            onValueChangeFinished={vm.player.seekTo(seek.toLong());seeking=false;haptics.perform(HapticEvent.SEEK)},valueRange=0f..state.durationMs.coerceAtLeast(1).toFloat(),enabled=state.durationMs>0,
                            modifier=Modifier.fillMaxWidth().semantics {contentDescription="Playback position"},
                            thumb={ Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary,androidx.compose.foundation.shape.CircleShape)) },
                            track={ Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.16f),androidx.compose.foundation.shape.CircleShape)) {
                                val progress=((if(seeking) seek else state.positionMs.toFloat())/state.durationMs.coerceAtLeast(1)).coerceIn(0f,1f)
                                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary,androidx.compose.foundation.shape.CircleShape))
                            } })
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(timeLabel(if(seeking) seek.toLong() else state.positionMs),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(timeLabel(state.durationMs),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                            TactileIcon(Icons.Rounded.Shuffle,"${if(state.shuffle) "Disable" else "Enable"} shuffle",{vm.player.setShuffle(!state.shuffle)},selected=state.shuffle)
                            TactileIcon(Icons.Rounded.SkipPrevious,"Previous track",vm.player::previous,event=HapticEvent.SKIP)
                            TactileIcon(if(state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(state.isPlaying) "Pause playback" else "Start playback",vm.player::togglePlayPause,Modifier.size(72.dp),filled=true)
                            TactileIcon(Icons.Rounded.SkipNext,"Next track",vm.player::next,enabled=state.queue.size>1,event=HapticEvent.SKIP)
                            TactileIcon(if(state.repeat==RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,"Repeat: ${state.repeat.name.lowercase()}",{vm.player.setRepeat(RepeatMode.entries[(state.repeat.ordinal+1)%3])},selected=state.repeat!=RepeatMode.OFF)
                        }
                        if(state.phase in listOf(PlaybackPhase.RESOLVING,PlaybackPhase.BUFFERING)) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                        state.error?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error);TextButton(onClick=vm.player::retry) { Text("Retry playback") } }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text(if(current.source=="local") "ON YOUR DEVICE" else "${if(current.source=="archive") "INTERNET ARCHIVE" else "YOUTUBE MUSIC"} · ${state.qualityLabel.uppercase()}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                            TactileIcon(Icons.Rounded.QueueMusic,"Open queue",onQueue)
                        }
                        LyricsPanel(vm, state)
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueueSheet(state:PlayerState,vm:AppViewModel,onDismiss:()->Unit) {
    val haptics=LocalWonderHaptics.current
    val threshold=with(LocalDensity.current){72.dp.toPx()}
    val latest by rememberUpdatedState(state)
    val order = state.playbackOrder.takeIf { it.sorted() == state.queue.indices.toList() } ?: state.queue.indices.toList()
    Column(Modifier.fillMaxWidth().heightIn(max=600.dp)) {
        ScreenHeader("Up next","${state.queue.size} tracks") { TextButton(onClick={vm.player.clearQueue();onDismiss()}) {Text("Clear")} }
        Text(if(state.shuffle) "Shuffle playback order. Turn shuffle off to reorder." else "Hold the handle to reorder. Tap a track to play.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=24.dp,vertical=8.dp))
        LazyColumn(Modifier.weight(1f,false),contentPadding=PaddingValues(bottom=24.dp)) {
            itemsIndexed(order) { _,index ->
                val track = state.queue[index]
                Row(verticalAlignment=Alignment.CenterVertically) {
                    var amount by remember { mutableFloatStateOf(0f) }; var moving by remember { mutableIntStateOf(index) }
                    Icon(Icons.Rounded.DragHandle,"Reorder ${track.title}",Modifier.size(48.dp).padding(12.dp).pointerInput(state.shuffle) {
                        if (!state.shuffle) detectDragGesturesAfterLongPress(onDragStart={moving=index;amount=0f;haptics.perform(HapticEvent.DRAG_START)},onDragCancel={amount=0f},onDragEnd={amount=0f}) { change,drag ->
                            change.consume();amount+=drag.y
                            if(abs(amount)>threshold) { val next=(moving+if(amount>0) 1 else -1).coerceIn(latest.queue.indices);if(next!=moving) {vm.player.move(moving,next);moving=next;haptics.perform(HapticEvent.REORDER)};amount=0f }
                        }
                    })
                    Column(Modifier.weight(1f).clickable {vm.player.playIndex(index)}.padding(vertical=14.dp)) {Text(track.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(index==state.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface);Text(track.artist,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    TactileIcon(Icons.Rounded.ArrowUpward,"Move ${track.title} up",{vm.player.move(index,index-1)},enabled=index>0 && !state.shuffle,event=HapticEvent.REORDER)
                    TactileIcon(Icons.Rounded.Close,"Remove ${track.title} from queue",{vm.player.remove(index)})
                }
            }
        }
    }
}
