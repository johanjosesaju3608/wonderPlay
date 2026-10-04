package com.wonderplay.source

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

@org.robolectric.annotation.Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class YouTubeMusicSourceTest {
    @Test fun musicMetadataProducesCorrectPlayableIdentity() {
        val item = StreamInfoItem(0,"https://www.youtube.com/watch?v=dQw4w9WgXcQ","Never Gonna Give You Up",StreamType.VIDEO_STREAM)
        item.uploaderName="Rick Astley";item.duration=213
        item.thumbnails=listOf(Image("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",480,360,Image.ResolutionLevel.MEDIUM))
        val track=YouTubeMusicSource.track(item)!!
        assertEquals("youtube:dQw4w9WgXcQ",track.id)
        assertEquals("youtube",track.source)
        assertEquals(213000L,track.durationMs)
        assertEquals("Rick Astley",track.artist)
    }
    @Test fun malformedAndLiveEntriesAreNotMusicTracks() {
        val item=StreamInfoItem(0,"https://www.youtube.com/watch?v=bad","Invalid",StreamType.VIDEO_STREAM)
        item.duration=100
        assertNull(YouTubeMusicSource.track(item))
        assertThrows(com.wonderplay.domain.SourceException::class.java) {YouTubeMusicSource.validId("../not-a-track")}
    }
    @org.junit.Test fun officialPlaylistFilterRejectsCommunityAndPersonalMixes() {
        org.junit.Assert.assertTrue(YouTubeMusicSource.isOfficialPlaylist("RDCLAK5uy_kCicKSTh7ylcZSwvrN0vV4dI3eqEpXR4A"))
        org.junit.Assert.assertFalse(YouTubeMusicSource.isOfficialPlaylist("PLabcdefghijklmnop"))
        org.junit.Assert.assertFalse(YouTubeMusicSource.isOfficialPlaylist("RDAMVMabcdefghijk"))
    }

    @org.junit.Test fun featuredSearchParserSkipsCommunityRowsAndKeepsOfficialTitles() {
        fun row(id: String): org.json.JSONObject {
            val title = org.json.JSONObject().put("runs", org.json.JSONArray().put(org.json.JSONObject().put("text", "Official fixture")))
            val column = org.json.JSONObject().put("musicResponsiveListItemFlexColumnRenderer", org.json.JSONObject().put("text", title))
            val endpoint = org.json.JSONObject().put("browseEndpoint", org.json.JSONObject().put("browseId", "VL$id"))
            val item = org.json.JSONObject().put("navigationEndpoint", endpoint).put("flexColumns", org.json.JSONArray().put(column))
            return org.json.JSONObject().put("musicResponsiveListItemRenderer", item)
        }
        val root = org.json.JSONObject().put("contents", org.json.JSONObject().put("items", org.json.JSONArray().put(row("RDCLAK5uy_kCicKSTh7ylcZSwvrN0vV4dI3eqEpXR4A")).put(row("PLabcdefghijklmnop"))))
        val result = FeaturedPlaylists.parseSearch(root)
        org.junit.Assert.assertEquals(1, result.size)
        org.junit.Assert.assertEquals("Official fixture", result.single().title)
    }

    @org.junit.Test fun exactNamedCollectionAppearsBeforeBroadAlbumMatches() {
        val broad = com.wonderplay.domain.MusicCollection("album", "Bollywood hits", "Album")
        val exact = com.wonderplay.domain.MusicCollection("official", "Bollywood Hitlist", "Official playlist")
        org.junit.Assert.assertEquals(exact, YouTubeMusicSource.rankCollections("bollywood hitlist", listOf(broad, exact)).first())
    }

    @Test fun publicMuxedAudioIsFallbackWhenSeparateAudioIsAbsent() {
        fun video(id: String, resolution: String, onlyVideo: Boolean = false) = org.schabi.newpipe.extractor.stream.VideoStream.Builder().setId(id)
            .setContent("https://example.org/$id", true).setMediaFormat(org.schabi.newpipe.extractor.MediaFormat.MPEG_4).setResolution(resolution).setIsVideoOnly(onlyVideo).build()
        val result = YouTubeMusicSource.publicPlayback(emptyList(), listOf(video("large", "720p"), video("small", "360p")))
        assertEquals("https://example.org/small", result.uri)
        assertTrue(result.qualityLabel.contains("public video"))
        assertThrows(com.wonderplay.domain.SourceException::class.java) { YouTubeMusicSource.publicPlayback(emptyList(), listOf(video("silent", "360p", true))) }
    }

}
