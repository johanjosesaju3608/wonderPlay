package com.wonderplay.source

import com.wonderplay.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream

@org.robolectric.annotation.Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class Release111Test {
    @Test fun qualitySelectsActualStreamsAndLowestFallback() {
        fun stream(rate:Int) = AudioStream.Builder().setId("$rate").setContent("https://example.org/$rate",true).setMediaFormat(MediaFormat.M4A).setAverageBitrate(rate).build()
        val streams=listOf(stream(0),stream(48),stream(128),stream(256))
        assertEquals("https://example.org/48",YouTubeMusicSource.publicPlayback(streams,emptyList(),AudioQuality.LOW).uri)
        assertEquals("https://example.org/128",YouTubeMusicSource.publicPlayback(streams,emptyList(),AudioQuality.MEDIUM).uri)
        assertEquals("https://example.org/256",YouTubeMusicSource.publicPlayback(streams,emptyList(),AudioQuality.HIGH).uri)
        assertEquals("https://example.org/128",YouTubeMusicSource.publicPlayback(listOf(stream(128),stream(256)),emptyList(),AudioQuality.LOW).uri)
        assertEquals("https://example.org/0",YouTubeMusicSource.publicPlayback(listOf(stream(0)),emptyList(),AudioQuality.LOW).uri)
    }
    @Test fun linkedArtistsPreserveBandNamesAndRejectNonArtistLinks() {
        val text=JSONObject("""{"runs":[{"text":"Earth, Wind & Fire","navigationEndpoint":{"browseEndpoint":{"browseId":"UCband","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},{"text":" & "},{"text":"Guest","navigationEndpoint":{"browseEndpoint":{"browseId":"UCguest","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},{"text":"Album","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREalbum","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}}]}""")
        val artists=MusicCatalog.artistRefs(text)
        assertEquals(listOf("Earth, Wind & Fire","Guest"),artists.map {it.name})
        assertEquals(listOf("UCband","UCguest"),artists.map {it.id})
    }
    @Test fun liveSongRowPreservesCollaboratorsAlbumAndDuration() {
        val root=JSONObject(javaClass.getResource("/catalog-song-row.json")!!.readText())
        val track=MusicCatalog.tracks(root).single()
        assertEquals("AU9AdGIdWZs",track.sourceId)
        assertEquals(4,track.artists.size)
        assertEquals("Pathaan",track.album)
        assertEquals("MPREb_y3t1dfSARfb",track.albumId)
        assertEquals(209000L,track.durationMs)
    }
    @Test fun liveArtistRowIsPlayableAndHasCanonicalIdentity() {
        val root=JSONObject(javaClass.getResource("/catalog-artist-row.json")!!.readText())
        val track=MusicCatalog.tracks(root).single()
        assertEquals("youtube",track.source)
        assertTrue(track.sourceId.matches(Regex("[A-Za-z0-9_-]{11}")))
        assertTrue(track.artists.any {it.id.startsWith("UC")})
    }

}
