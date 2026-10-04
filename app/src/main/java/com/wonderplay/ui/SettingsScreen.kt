package com.wonderplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wonderplay.AppViewModel
import com.wonderplay.BuildConfig
import com.wonderplay.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsScreen(settings:AppSettings,vm:AppViewModel,onBack:()->Unit) {
    var info by remember { mutableStateOf<String?>(null) }
    var clearHistory by remember { mutableStateOf(false) }
    Column {
        ScreenHeader("Settings","A little more your own.",onBack)
        LazyColumn(modifier=Modifier.testTag("Settings list"),contentPadding=PaddingValues(bottom=24.dp + LocalOverlayBottom.current)) {
            item { SectionHeading("Appearance") }
            item { Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { theme -> FilterChip(settings.theme==theme,{vm.updateSettings(settings.copy(theme=theme))},label={Text(theme.name.lowercase().replaceFirstChar(Char::uppercaseChar))},modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=Shape.control) }
            } }
            item { Information("Color style","Album colors follow the current cover, with coffee as the fallback.") }
            item { SettingSwitch("Tactile feedback","Subtle haptics for meaningful actions",settings.haptics) {vm.updateSettings(settings.copy(haptics=it))} }
            item { SettingSwitch("Reduce motion","Use immediate, simpler transitions",settings.reducedMotion) {vm.updateSettings(settings.copy(reducedMotion=it))} }
            item { Spacer(Modifier.height(16.dp));SectionHeading("Listening") }
            item { SettingSwitch("Wi-Fi-only streaming","Local files still work offline. Search and artwork may use mobile data.",settings.wifiOnly) {vm.updateSettings(settings.copy(wifiOnly=it))} }
            item { SettingSwitch("Autoplay similar songs","Continue with related music when the queue runs low",settings.autoplay) {vm.updateSettings(settings.copy(autoplay=it))} }
            item { Information("Audio quality","Control streaming data use. Low aims for 64 kbps, Medium for 128 kbps, and High uses the best available audio. Sources with one format keep their original quality. Applies to newly loaded streams.") }
            item { Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AudioQuality.entries.forEach { quality -> FilterChip(selected=settings.audioQuality==quality,onClick={vm.updateSettings(settings.copy(audioQuality=quality))},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primaryContainer,selectedLabelColor=MaterialTheme.colorScheme.onPrimaryContainer),label={Text(quality.name.lowercase().replaceFirstChar(Char::uppercaseChar))},shape=androidx.compose.foundation.shape.CircleShape,modifier=Modifier.weight(1f)) }
            } }
            item { Spacer(Modifier.height(16.dp));SectionHeading("On this device") }
            item { ActionRow(Icons.Rounded.History,"Clear listening history") {clearHistory=true} }
            item { ActionRow(Icons.Rounded.Search,"Clear recent searches") {vm.clearSearches()} }
            item { ActionRow(Icons.Rounded.CleaningServices,"Clear artwork cache","Your playlists, favorites and local artwork stay saved") {vm.clearArtworkCache()} }
            item { Spacer(Modifier.height(16.dp));SectionHeading("About wonderPlay") }
            item { ActionRow(Icons.Rounded.MusicNote,"Music sources") {info="Sources"} }
            item { ActionRow(Icons.Rounded.PrivacyTip,"Privacy") {info="Privacy"} }
            item { ActionRow(Icons.Rounded.Code,"Open-source licenses") {info="Licenses"} }
            item { Information("wonderPlay ${BuildConfig.VERSION_NAME}","Independent. Open source. Made for listening. No affiliation with YouTube, Google, Apple or Spotify.") }
        }
    }
    if(clearHistory) AlertDialog(onDismissRequest={clearHistory=false},title={Text("Clear listening history?")},text={Text("Your favorites and playlists remain saved.")},confirmButton={TextButton(onClick={vm.clearHistory();clearHistory=false}){Text("Clear history")}},dismissButton={TextButton(onClick={clearHistory=false}){Text("Cancel")}})
    info?.let { title ->
        val context=LocalContext.current
        val text by produceState(initialValue="",title) {
            value=when(title) {
                "Sources" -> "YouTube Music is the default search catalog. Publicly available audio plays inside wonderPlay using the open-source NewPipe extractor. Restricted or unavailable tracks may not play. No account or paid-content access is implemented.\n\nChoose your own audio files through Android’s file picker for local playback. Files are not uploaded or copied.\n\nThe external YouTube Music link is available as a fallback. YouTube tracks are streamed. Downloads can save an exact licensed matching recording from Internet Archive; Library → Downloads also lets you browse licensed music. Downloaded audio stays inside the app, with its source and license shown."
                "Privacy" -> "No accounts, ads, analytics, tracking IDs or cloud AI. Favorites, playlists, searches, settings and history stay in private app storage. Android cloud backup is disabled.\n\nSearch, recommendations, autoplay radio and streaming requests go to YouTube/Google and their media hosts. Lyrics requests go to LRCLIB and, when needed, NetEase and lyrics.ovh. Download availability and licensed music requests go to Internet Archive. Eligible downloads stay in private app storage with source and license links. Artwork loads from provider hosts. Missing canonical artwork may be matched using MusicBrainz and Cover Art Archive. Providers receive your IP address and requested music metadata.\n\nSelected local files use Android’s read grants. No microphone, camera, location or broad storage access. Uninstalling deletes your local library metadata."
                else -> withContext(Dispatchers.IO) { listOf("THIRD-PARTY-NOTICES.txt","wonderPlay-MIT.txt","Apache-2.0.txt","GPL-3.0.txt").joinToString("\n\n") { name -> context.assets.open("licenses/$name").bufferedReader().use {it.readText()} } }
            }
        }
        if(text.isNotBlank()) InformationDialog(title,text,settings.reducedMotion) {info=null}
    }
}
@Composable
private fun SettingSwitch(title:String,subtitle:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().clickable {onChange(!checked)}.padding(horizontal=24.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=18.dp)) {Text(title,style=MaterialTheme.typography.bodyLarge);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))}
        Switch(checked,onChange)
    }
}
@Composable
private fun Information(title:String,body:String) { Column(Modifier.padding(horizontal=24.dp,vertical=14.dp)) {Text(title,style=MaterialTheme.typography.titleSmall);Text(body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))} }

@Composable
private fun InformationDialog(title:String,text:String,reduced:Boolean,onDismiss:()->Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {entered=true}
    val alpha by androidx.compose.animation.core.animateFloatAsState(if(entered) 1f else 0f,androidx.compose.animation.core.tween(if(reduced) 0 else 150),label="Information fade")
    val scale by androidx.compose.animation.core.animateFloatAsState(if(entered) 1f else .97f,androidx.compose.animation.core.tween(if(reduced) 0 else 150),label="Information scale")
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss) {
        Surface(shape=Shape.artwork,color=MaterialTheme.colorScheme.surfaceContainerHigh,modifier=Modifier.fillMaxWidth().graphicsLayer {this.alpha=alpha;scaleX=scale;scaleY=scale}) {
            Column(Modifier.padding(24.dp)) {
                Text(title,style=MaterialTheme.typography.headlineSmall)
                Text(text,modifier=Modifier.padding(top=16.dp).heightIn(max=440.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End).padding(top=12.dp)) {Text("Done")}
            }
        }
    }
}
