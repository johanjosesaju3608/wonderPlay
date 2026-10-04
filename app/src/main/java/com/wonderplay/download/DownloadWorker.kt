package com.wonderplay.download

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wonderplay.WonderPlayApp
import com.wonderplay.data.TrackCodec
import com.wonderplay.player.PlaybackNetworkPolicy
import com.wonderplay.source.SourceHttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class DownloadWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
 override suspend fun doWork():Result = withContext(Dispatchers.IO) {
  val container=(applicationContext as WonderPlayApp).container
  val repo=container.downloads
  val id=inputData.getString("id") ?: return@withContext Result.failure()
  val token=inputData.getString("generation") ?: return@withContext Result.failure()
  val entry=repo.dao.get(id)?.takeIf {it.generation==token && it.status in setOf("Queued","Preparing","Downloading")} ?: return@withContext Result.failure()
  val temporary=File(repo.folder,"$token.part"); val completed=File(repo.folder,"$token.audio"); val artwork=File(repo.folder,"$token.jpg")
  val policy=PlaybackNetworkPolicy(applicationContext)
  var published=false
  try {
   policy.settings=container.library.settings.first();policy.check()
   repo.dao.progress(id,token,"Preparing",0,0)
   val permit=withTimeout(90000) {PermittedAudioSource().find(TrackCodec.decode(entry.payload))} ?: throw IOException("No matching recording with download permission was found. Streaming is still available.")
   if(repo.folder.usableSpace < permit.bytes+16L*1024*1024) throw IOException("Not enough free storage for this download.")
   repo.dao.progress(id,token,"Downloading",0,permit.bytes)
   val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(8,TimeUnit.MINUTES)
    .addNetworkInterceptor { chain ->
     val u=chain.request().url
     if(u.scheme!="https" || (u.host!="archive.org" && !u.host.endsWith(".archive.org"))) throw IOException("The download source redirected outside its permitted host.")
     chain.proceed(chain.request())
    }.build()
   client.newCall(Request.Builder().url(permit.url).header("User-Agent",SourceHttpClient.USER_AGENT).build()).execute().use { response ->
    if(!response.isSuccessful) throw IOException("The download source is unavailable. Try again.")
    val body=response.body ?: throw IOException("Empty download")
    if(body.contentLength()>PermittedAudioSource.MAX_BYTES) throw IOException("This download is too large.")
    var count=0L; var last=0L
    body.byteStream().use { input -> temporary.outputStream().use { output ->
     count=BoundedDownload.copy(input,output,permit.bytes,check={ensureActive()},progress={bytes ->
      val now=android.os.SystemClock.elapsedRealtime()
      if(now-last>=500) {policy.check();runBlocking { policy.settings=container.library.settings.first();policy.check();repo.dao.progress(id,token,"Downloading",bytes,permit.bytes) };last=now}
     })
    } }
    if(count!=permit.bytes) throw IOException("The download was incomplete. Try again.")
   }
   val retriever=MediaMetadataRetriever()
   try {retriever.setDataSource(temporary.path);val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
    if(duration<=0 || kotlin.math.abs(duration-permit.track.durationMs)>10000) throw IOException("The downloaded audio could not be verified.")
   } finally {retriever.release()}
   ensureActive()
   runCatching {SourceHttpClient().imageBytes("https://archive.org/services/img/${permit.track.sourceId.substringBefore('/')}".toHttpUrl())}.getOrNull()?.let {artwork.writeBytes(it)}
   repo.gate.withLock {
    ensureActive()
    val current=repo.dao.get(id)
    if(current?.generation!=token || current.status!="Downloading") throw CancellationException("Download cancelled")
    if(!temporary.renameTo(completed)) throw IOException("Could not save the completed download.")
    published=repo.dao.complete(id,token,completed.length(),completed.name,if(artwork.isFile) artwork.name else "",permit.license,permit.page)==1
   }
   if(published) Result.success() else Result.failure()
  } catch(cancelled:CancellationException) {
   withContext(NonCancellable) {repo.dao.progress(id,token,"Queued",0,0)}
   throw cancelled
  } catch(error:Exception) {
   repo.dao.progress(id,token,"Failed",0,0,error.message?.take(200) ?: "Download failed. Try again.")
   Result.failure()
  } finally {
   policy.close();temporary.delete()
   if(!published) {completed.delete();artwork.delete()}
  }
 }
}
