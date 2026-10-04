package com.wonderplay.source

import com.wonderplay.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** Linked metadata only: display separators never create artist identities. */
internal class MusicCatalog(private val http: SourceHttpClient = SourceHttpClient()) {
    private suspend fun request(endpoint: String, args: JSONObject): JSONObject {
        val client = JSONObject().put("clientName","WEB_REMIX").put("clientVersion","1.20260930.01.00").put("hl","en").put("gl","US")
        args.put("context",JSONObject().put("client",client))
        return JSONObject(http.text("https://music.youtube.com/youtubei/v1/$endpoint".toHttpUrl(),args) ?: throw SourceException("Music details are unavailable. Try again."))
    }
    suspend fun details(track: Track): TrackDetails {
        val root = request("next",JSONObject().put("videoId",YouTubeMusicSource.validId(track.sourceId)))
        val row = objects(root,"playlistPanelVideoRenderer").firstOrNull { it.optString("videoId")==track.sourceId }
        val long = row?.optJSONObject("longBylineText")
        val short = row?.optJSONObject("shortBylineText")
        var refs = (artistRefs(long)+artistRefs(short)+track.artists).distinctBy {it.id}
        var albums = albumRefs(long)+albumRefs(short)
        // Song search carries album links that a music-video Now Playing row may omit.
        try {
            val songs=request("search",JSONObject().put("query","${track.title} ${refs.firstOrNull()?.name ?: track.artist}").put("params","EgWKAQIIAQ%3D%3D"))
            val matches=objects(songs,"musicResponsiveListItemRenderer").filter {candidate ->
                val candidateId=candidate.optJSONObject("playlistItemData")?.optString("videoId")
                val names=artistRefs(column(candidate,1))
                candidateId==track.sourceId || (com.wonderplay.metadata.MetadataResolver.key(text(column(candidate,0)))==com.wonderplay.metadata.MetadataResolver.key(track.title) && names.any {ref->refs.any {it.id==ref.id}})
            }
            val linked=matches.flatMap {candidate ->albumRefs(column(candidate,1))+albumRefs(column(candidate,2))}
            albums=albums+linked
            refs=(refs+matches.flatMap {artistRefs(column(it,1))}).distinctBy {it.id}
        } catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
        catch(_:Exception) { /* Keep canonical Now Playing identities when search is unavailable. */ }
        val resolved = coroutineScope { refs.take(12).map { ref -> async {
            val art = try { FeaturedPlaylists().search(ref.name).filter {com.wonderplay.metadata.MetadataResolver.key(it.title).contains(com.wonderplay.metadata.MetadataResolver.key(ref.name))}.sortedBy {if(it.title.startsWith("Presenting",true)) 0 else if(it.title.contains("Best",true)) 1 else 2}.firstOrNull()?.artworkUrl }
            catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(_:Exception) {null}
            val fallback=if(art==null && ref.artworkUrl==null) try {
                val page=request("browse",JSONObject().put("browseId",ref.id))
                image(objects(page,"musicImmersiveHeaderRenderer").firstOrNull() ?: objects(page,"musicVisualHeaderRenderer").firstOrNull())
            } catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled} catch(_:Exception) {null} else null
            ref.copy(artworkUrl=art ?: ref.artworkUrl ?: fallback)
        } }.map {it.await()} }
        val linked = albums.distinctBy {it.id}.map {it.copy(artworkUrl=track.artworkUrl)}
        return TrackDetails(track.copy(artists=resolved,artistId=resolved.firstOrNull()?.id ?: track.artistId,albumId=linked.firstOrNull()?.id ?: track.albumId,album=linked.firstOrNull()?.title ?: track.album),resolved,linked)
    }
    suspend fun artist(ref: ArtistRef): Artist {
        var id = ref.id
        var name = ref.name
        if(!id.startsWith("UC")) {
            val search = request("search",JSONObject().put("query",name).put("params","EgWKAQIgAQ%3D%3D"))
            val row = objects(search,"musicResponsiveListItemRenderer").firstOrNull { pageType(it.optJSONObject("navigationEndpoint"))=="MUSIC_PAGE_TYPE_ARTIST" }
                ?: throw SourceException("No official artist page was returned.")
            id = row.optJSONObject("navigationEndpoint")!!.optJSONObject("browseEndpoint")!!.getString("browseId")
            name = text(column(row,0)).ifBlank {name}
        }
        if(!id.matches(Regex("UC[A-Za-z0-9_-]{2,100}"))) throw SourceException("Invalid artist page.")
        val root=request("browse",JSONObject().put("browseId",id))
        val header=objects(root,"musicImmersiveHeaderRenderer").firstOrNull() ?: objects(root,"musicVisualHeaderRenderer").firstOrNull() ?: objects(root,"musicHeaderRenderer").firstOrNull()
        name=text(header?.optJSONObject("title")).ifBlank {name}
        val shelf=objects(root.optJSONObject("contents"),"musicShelfRenderer").firstOrNull { text(it.optJSONObject("title")).let {title->title.equals("Songs",true) || title.equals("Top songs",true)} }
        var tracks=shelf?.let(::tracks).orEmpty()
        // The artist's Songs shelf links to its live full ranking, usually longer than the preview.
        val more=shelf?.optJSONObject("bottomEndpoint")?.optJSONObject("browseEndpoint")
            ?: shelf?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
        if(tracks.size<10 && more!=null) {
            try { val page=request("browse",JSONObject().put("browseId",more.getString("browseId")).apply { more.optString("params").takeIf {it.isNotBlank()}?.let {put("params",it)} }); tracks=(tracks(page)+tracks).distinctBy {it.id} }
            catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(_:Exception) { /* Keep the live preview if the full shelf is unavailable. */ }
        }
        val official=try { FeaturedPlaylists().search(name).filter {it.title.contains(name,true)} }
        catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
        catch(_:Exception) {emptyList()}
        val owned=collections(root,"MUSIC_PAGE_TYPE_PLAYLIST")
        return Artist(id,name,ref.artworkUrl ?: official.firstOrNull()?.artworkUrl ?: image(header),tracks.take(10),collections(root,"MUSIC_PAGE_TYPE_ALBUM"),(official+owned).distinctBy {it.id})
    }
    suspend fun album(list: MusicCollection): MusicCollection {
        if(!list.id.matches(Regex("MPRE[A-Za-z0-9_-]{5,100}"))) return FeaturedPlaylists().open(list.id)
        val root=request("browse",JSONObject().put("browseId",list.id))
        val header=objects(root,"musicResponsiveHeaderRenderer").firstOrNull() ?: objects(root,"musicDetailHeaderRenderer").firstOrNull()
        return list.copy(title=text(header?.optJSONObject("title")).ifBlank {list.title},artworkUrl=image(header) ?: list.artworkUrl,tracks=tracks(root)).also {if(it.tracks.isEmpty()) throw SourceException("This album didn't return playable tracks.")}
    }
    companion object {
        internal fun text(value: JSONObject?): String = value?.optJSONArray("runs")?.let {a -> (0 until a.length()).joinToString("") { a.optJSONObject(it)?.optString("text").orEmpty() }}.orEmpty()
        private fun pageType(endpoint:JSONObject?)=endpoint?.optJSONObject("browseEndpoint")?.optJSONObject("browseEndpointContextSupportedConfigs")?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")
        internal fun artistRefs(value:JSONObject?):List<ArtistRef> = value?.optJSONArray("runs")?.let {a -> (0 until a.length()).mapNotNull {i ->
            val run=a.optJSONObject(i) ?: return@mapNotNull null
            val endpoint=run.optJSONObject("navigationEndpoint")
            val id=endpoint?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if(pageType(endpoint)=="MUSIC_PAGE_TYPE_ARTIST" && id.startsWith("UC") && run.optString("text").isNotBlank()) ArtistRef(id,run.getString("text")) else null
        }}.orEmpty().distinctBy {it.id}
        private fun albumRefs(value:JSONObject?):List<MusicCollection> = value?.optJSONArray("runs")?.let {a -> (0 until a.length()).mapNotNull {i ->
            val run=a.optJSONObject(i) ?: return@mapNotNull null;val endpoint=run.optJSONObject("navigationEndpoint")
            val id=endpoint?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if(pageType(endpoint)=="MUSIC_PAGE_TYPE_ALBUM" && id.startsWith("MPRE")) MusicCollection(id,run.optString("text"),"Album") else null
        }}.orEmpty()
        private fun column(row:JSONObject,i:Int)=row.optJSONArray("flexColumns")?.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
        private fun objects(root:Any?,key:String):List<JSONObject> {
            val found=mutableListOf<JSONObject>()
            fun walk(v:Any?,depth:Int) {
                if(depth>35 || found.size>=200) return
                when(v) {is JSONObject -> {v.optJSONObject(key)?.let(found::add);v.keys().forEach {walk(v.opt(it),depth+1)}};is JSONArray -> (0 until v.length()).forEach {walk(v.opt(it),depth+1)}}
            };walk(root,0);return found
        }
        private fun image(root:JSONObject?):String? = objects(root,"thumbnail").flatMap {obj -> obj.optJSONArray("thumbnails")?.let {a -> (0 until a.length()).mapNotNull {a.optJSONObject(it)}}.orEmpty() }.maxByOrNull {it.optInt("width")}?.optString("url")?.takeIf {it.startsWith("https://")}
        internal fun tracks(root:JSONObject):List<Track> = objects(root,"musicResponsiveListItemRenderer").mapNotNull {row ->
            val id=row.optJSONObject("playlistItemData")?.optString("videoId").orEmpty().ifBlank { objects(row,"watchEndpoint").firstOrNull()?.optString("videoId").orEmpty() }
            val title=text(column(row,0));val byline=column(row,1);val refs=artistRefs(byline)
            if(!id.matches(Regex("[A-Za-z0-9_-]{11}")) || title.isBlank()) return@mapNotNull null
            var duration=text(row.optJSONArray("fixedColumns")?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text"))
            if(duration.isBlank()) duration=column(row,1)?.optJSONArray("runs")?.let {a->(0 until a.length()).mapNotNull {a.optJSONObject(it)?.optString("text")}.firstOrNull {it.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"))}}.orEmpty()
            val linkedAlbum=(albumRefs(column(row,1))+albumRefs(column(row,2))).firstOrNull()
            val secs=duration.split(':').mapNotNull(String::toLongOrNull).takeIf {it.size in 2..3}?.fold(0L) {n,v->n*60+v} ?: 0
            Track("youtube:$id",title,refs.joinToString(", ") {it.name}.ifBlank {text(byline)},album=linkedAlbum?.title ?: text(column(row,2)),artworkUrl=image(row),durationMs=secs*1000,sourceId=id,artists=refs,artistId=refs.firstOrNull()?.id,albumId=linkedAlbum?.id,permalink="https://music.youtube.com/watch?v=$id")
        }.distinctBy {it.id}
        private fun collections(root:JSONObject,type:String):List<MusicCollection> = objects(root.optJSONObject("contents"),"musicTwoRowItemRenderer").mapNotNull {row ->
            val endpoint=row.optJSONObject("navigationEndpoint");val id=endpoint?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if(pageType(endpoint)!=type) return@mapNotNull null
            val title=text(row.optJSONObject("title"));if(title.isBlank()) return@mapNotNull null
            MusicCollection(id.removePrefix("VL"),title,if(type=="MUSIC_PAGE_TYPE_ALBUM") "Album" else "From the artist's YouTube Music page",image(row))
        }.distinctBy {it.id}
    }
}
