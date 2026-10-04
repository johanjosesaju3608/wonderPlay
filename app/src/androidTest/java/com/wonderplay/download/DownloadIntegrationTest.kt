package com.wonderplay.download
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.wonderplay.*
import com.wonderplay.data.TrackCodec
import com.wonderplay.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class DownloadIntegrationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val track=Track("youtube:offline-fixture","Offline fixture","Fixture",durationMs=30000)
 private fun repo()=(compose.activity.application as WonderPlayApp).container.downloads
 private suspend fun ready():DownloadEntry {
  val repository=repo();val token=UUID.randomUUID().toString()
  val entry=DownloadEntry(track.id,TrackCodec.encode(track),token,status="Downloading")
  val samples=44100*30;val data=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN)
  data.put("RIFF".toByteArray()).putInt(36+samples*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples*2)
  repeat(samples) {data.putShort(0)}
  repository.gate.withLock {
   repository.dao.put(entry)
   File(repository.folder,"$token.audio").writeBytes(data.array())
   assertEquals(1,repository.dao.complete(entry.id,token,data.array().size.toLong(),"$token.audio","","https://creativecommons.org/publicdomain/zero/1.0/","https://example.test/fixture"))
  }
  return repository.dao.get(track.id)!!
 }
 @Test fun cancelledOrReplacedJobsCannotPublishAudio() = runBlocking {
  val repository=repo();val entry=DownloadEntry(track.id,TrackCodec.encode(track),UUID.randomUUID().toString(),status="Cancelled")
  repository.dao.put(entry)
  assertEquals(0,repository.dao.complete(entry.id,entry.generation,100,"bad.audio","","license","page"))
  repository.dao.put(entry.copy(generation="replacement",status="Downloading"))
  assertEquals(0,repository.dao.complete(entry.id,entry.generation,100,"bad.audio","","license","page"))
  repository.remove(entry.id)
 }
 @Test fun completedRecordPersistsAndMissingFileIsNotOffline() = runBlocking {
  val repository=repo();val entry=ready()
  try {
   val second=androidx.room.Room.databaseBuilder(compose.activity,DownloadDatabase::class.java,"wonderplay-downloads.db").build()
   assertEquals("Ready",second.downloads().get(track.id)?.status);second.close()
   assertNotNull(repository.offline(track))
   File(repository.folder,entry.file).delete()
   assertNull(repository.offline(track));assertEquals("Failed",repository.dao.get(track.id)?.status)
  } finally {repository.remove(track.id)}
 }
 @Test fun downloadedRemoteTrackPlaysWithWifiOnlyAndOriginalIdentity() = runBlocking {
  val repository=repo();ready()
  lateinit var vm:AppViewModel
  compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[AppViewModel::class.java]}
  try {
   val container=(compose.activity.application as WonderPlayApp).container
   val policy=com.wonderplay.player.PlaybackNetworkPolicy(compose.activity).apply {settings=AppSettings(wifiOnly=true)}
   try {
    val uri=com.wonderplay.player.TrackMediaCodec.item(track).localConfiguration!!.uri
    val resolved=com.wonderplay.player.PlaybackResolver(container.sources,policy).resolveDataSpec(androidx.media3.datasource.DataSpec(uri))
    assertEquals("file",resolved.uri.scheme)
   } finally {policy.close()}
   compose.runOnUiThread {vm.player.play(listOf(track))}
   compose.waitUntil(10000) {vm.player.state.value.isPlaying}
   assertEquals(track.id,vm.player.state.value.current?.id)
   assertTrue(vm.player.state.value.qualityLabel.contains("Offline"))
  } finally {compose.runOnUiThread {vm.player.clearQueue()};repository.remove(track.id)}
 }
}
