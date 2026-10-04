package com.wonderplay.download

import android.content.Context
import androidx.room.Room
import androidx.work.*
import com.wonderplay.data.TrackCodec
import com.wonderplay.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DownloadRepository(private val context:Context,private val library:LibraryStore) {
 internal val dao=Room.databaseBuilder(context.applicationContext,DownloadDatabase::class.java,"wonderplay-downloads.db").build().downloads()
 internal val gate=Mutex()
 internal val folder=File(context.filesDir,"downloads").apply {mkdirs()}
 val entries=dao.observe()
 init {
  CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
   entries.collect { gate.withLock {
    val keep=dao.all().flatMap {entry -> when(entry.status) {
      "Ready" -> listOf(entry.file,entry.artwork)
      "Queued","Preparing","Downloading" -> listOf("${entry.generation}.part","${entry.generation}.audio","${entry.generation}.jpg")
      else -> emptyList()
    }}.toSet()
    folder.listFiles()?.filter {it.isFile && it.name !in keep}?.forEach {it.delete()}
   }}
  }
 }
 private val work get()=WorkManager.getInstance(context)
 suspend fun enqueue(track:Track) = gate.withLock {
  require(track.source!="local") {"This file is already available offline."}
  val old=dao.get(track.id)
  if(old?.status in setOf("Queued","Preparing","Downloading","Ready")) return@withLock
  val token=UUID.randomUUID().toString()
  dao.put(DownloadEntry(track.id,TrackCodec.encode(track),token))
  val constraints=Constraints.Builder().setRequiredNetworkType(if(library.settings.first().wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build()
  val request=OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("id" to track.id,"generation" to token)).setConstraints(constraints).addTag("wonderplay-download").build()
  work.enqueueUniqueWork("download:${track.id}",ExistingWorkPolicy.REPLACE,request)
 }
 suspend fun cancel(id:String) = gate.withLock {
  work.cancelUniqueWork("download:$id")
  dao.get(id)?.takeIf {it.status!="Ready"}?.let {dao.put(it.copy(status="Cancelled",error=""))}
 }
 suspend fun remove(id:String) = gate.withLock {
  work.cancelUniqueWork("download:$id")
  dao.get(id)?.let { entry -> privateFile(entry.file)?.delete(); privateFile(entry.artwork)?.delete() }
  dao.delete(id)
 }
 internal fun privateFile(name:String):File? = name.takeIf {it.matches(Regex("[A-Za-z0-9-]+\\.(audio|jpg)"))}?.let {File(folder,it)}
 suspend fun offline(track:Track):PlaybackSource? = withContext(Dispatchers.IO) {
  val entry=dao.get(track.id)?.takeIf {it.status=="Ready"} ?: return@withContext null
  val file=privateFile(entry.file)
  if(file==null || !file.isFile || file.length()!=entry.bytes || entry.bytes<=0) {
   gate.withLock {if(dao.get(track.id)?.generation==entry.generation) dao.put(entry.copy(status="Failed",file="",error="Offline file is missing. Download it again."))}
   return@withContext null
  }
  PlaybackSource(file.toURI().toString(),qualityLabel="Downloaded · Offline")
 }
 fun track(entry:DownloadEntry):Track = TrackCodec.decode(entry.payload).let {track ->
  val art=privateFile(entry.artwork)?.takeIf {it.isFile}?.toURI()?.toString()
  if(art!=null) track.copy(artworkUrl=art) else track
 }
}
