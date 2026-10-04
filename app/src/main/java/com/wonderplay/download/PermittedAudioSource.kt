package com.wonderplay.download

import com.wonderplay.domain.Track
import com.wonderplay.metadata.MetadataResolver
import com.wonderplay.source.LyricsRepository
import com.wonderplay.source.SourceHttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import kotlin.math.abs

/** Published license metadata is required; a public streaming URL is never an entitlement. */
data class DownloadEntitlement(val track: Track, val url: String, val bytes: Long, val license: String, val page: String)
class PermittedAudioSource(private val http: SourceHttpClient = SourceHttpClient()) {
 suspend fun search(query: String): List<DownloadEntitlement> = lookup(query, 4)
 suspend fun find(track: Track): DownloadEntitlement? {
  if(track.source == "local") return null
  if(track.source == "archive") {
   val item=track.sourceId.substringBefore('/'); val file=track.sourceId.substringAfter('/', "")
   if(!item.matches(Regex("[A-Za-z0-9_.-]{1,150}"))) return null
   return decode(item,http.json("https://archive.org/metadata/$item".toHttpUrl())).firstOrNull { it.track.sourceId.substringAfter('/') == file && matches(track,it.track) }
  }
  return lookup(track.artist,8).firstOrNull { matches(track,it.track) }
 }
 private suspend fun lookup(query: String, count: Int): List<DownloadEntitlement> = coroutineScope {
  val clean=query.take(80).replace(Regex("[^\\p{L}\\p{N} ]")," ").trim()
  val search=if(clean.isBlank()) "collection:blocsonic" else "(title:(\"$clean\") OR creator:(\"$clean\"))"
  val url="https://archive.org/advancedsearch.php".toHttpUrl().newBuilder().addQueryParameter("q","mediatype:audio AND collection:netlabels AND licenseurl:* AND $search")
   .addQueryParameter("output","json").addQueryParameter("rows",count.toString()).addQueryParameter("fl[]","identifier").build()
  val docs=http.json(url).optJSONObject("response")?.optJSONArray("docs") ?: return@coroutineScope emptyList()
  (0 until minOf(docs.length(),count)).chunked(3).flatMap { indices -> indices.map { index -> async {
   val item=docs.optJSONObject(index)?.optString("identifier").orEmpty()
   if(!item.matches(Regex("[A-Za-z0-9_.-]{1,150}"))) emptyList() else try { decode(item,http.json("https://archive.org/metadata/$item".toHttpUrl())) }
   catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled} catch(_:Exception) {emptyList()}
  } }.awaitAll().flatten() }.distinctBy {it.track.id}.take(60)
 }
 companion object {
  const val MAX_BYTES=128L*1024*1024
  internal fun license(value:String):String? {
   val u=value.toHttpUrlOrNull() ?: return null
   if(u.host !in setOf("creativecommons.org","www.creativecommons.org")) return null
   if(!Regex("/(?:licenses/(?:by|by-sa|by-nd|by-nc|by-nc-sa|by-nc-nd)/(?:1\\.0|2\\.0|2\\.5|3\\.0|4\\.0)|publicdomain/zero/1\\.0)/?").matches(u.encodedPath)) return null
   return u.newBuilder().scheme("https").query(null).fragment(null).build().toString()
  }
  internal fun matches(wanted:Track, found:Track):Boolean = MetadataResolver.key(LyricsRepository.cleanTitle(wanted.title)) == MetadataResolver.key(found.title) &&
   MetadataResolver.key(LyricsRepository.cleanArtist(wanted.artist)) == MetadataResolver.key(found.artist) && wanted.durationMs>0 && abs(wanted.durationMs-found.durationMs)<=5000
  internal fun decode(item:String,root:JSONObject):List<DownloadEntitlement> {
   if(root.optBoolean("is_dark") || root.optBoolean("nodownload")) return emptyList()
   val metadata=root.optJSONObject("metadata") ?: return emptyList()
   if(metadata.optBoolean("nodownload")) return emptyList()
   val licensed=license(metadata.optString("licenseurl")) ?: return emptyList()
   val files=root.optJSONArray("files") ?: return emptyList()
   val page="https://archive.org/details/$item"
   return (0 until minOf(files.length(),500)).mapNotNull { i ->
    val f=files.optJSONObject(i) ?: return@mapNotNull null
    val name=f.optString("name"); val size=f.optLong("size"); val title=f.optString("title").trim(); val artist=f.optString("creator").ifBlank {f.optString("artist")}.ifBlank {metadata.optString("creator")}.trim()
    val format=f.optString("format")
    if(format !in setOf("VBR MP3","MP3","128Kbps MP3","Ogg Vorbis","FLAC") || name.isBlank() || name.length>300 || name.startsWith('/') || name.contains('\\') || name.split('/').any {it==".." || it=="."} || size !in 1024..MAX_BYTES || title.isBlank() || artist.isBlank()) return@mapNotNull null
    val length=f.optString("length"); val parts=length.split(':').mapNotNull {it.toDoubleOrNull()}; val seconds=if(parts.size==1) parts[0] else if(parts.size in 2..3) parts.fold(0.0) {a,v->a*60+v} else 0.0
    if(seconds !in 30.0..1800.0) return@mapNotNull null
    val fileId="$item/$name"; val url="https://archive.org/download".toHttpUrl().newBuilder().addPathSegment(item).apply {name.split('/').forEach(::addPathSegment)}.build().toString()
    val track=Track("archive:$fileId",title,artist,album=metadata.optString("title"),durationMs=(seconds*1000).toLong(),source="archive",sourceId=fileId,artworkUrl="https://archive.org/services/img/$item",permalink=page)
    DownloadEntitlement(track,url,size,licensed,page)
   }.distinctBy {MetadataResolver.key(it.track.title)+MetadataResolver.key(it.track.artist)}
  }
 }
}
