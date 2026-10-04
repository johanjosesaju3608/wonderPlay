package com.wonderplay.source

import com.wonderplay.domain.Track
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FreeLyricsTest {
    @Test fun nonLatinLyricsWithoutProviderRomanizationStayOriginal() { assertNull(Romanization.variant(Lyrics(plain = "മലയാളം"))) }
    @Test fun filmSuffixIsMetadataButLiveVersionsRemainDistinct() {
        assertEquals("Jhoome Jo Pathaan", LyricsRepository.cleanTitle("Jhoome Jo Pathaan (From \"Pathaan\")"))
        assertEquals("Song (Live)", LyricsRepository.cleanTitle("Song (Live)"))
    }
    @Test fun netEaseChecksArtistTitleAndDurationInsteadOfFirstSearchHit() {
        val track = Track("song", "Song (From Movie)", "Artist", durationMs = 180000)
        val record = JSONObject().put("name", "Song").put("duration", 181000).put("artists", JSONArray().put(JSONObject().put("name", "Other")).put(JSONObject().put("name", "Artist")))
        assertTrue(NetEaseLyrics.matches(track, record))
        assertFalse(NetEaseLyrics.matches(track.copy(title = "Song (Live)"), record))
        assertFalse(NetEaseLyrics.matches(track, record.put("duration", 200000)))
    }
    @Test fun providedRomanizationKeepsItsOwnTimestampsAndIsNotTranslation() {
        val root = JSONObject().put("code", 200).put("lrc", JSONObject().put("lyric", "[00:01]原文\n[00:05]次行"))
            .put("romalrc", JSONObject().put("lyric", "[00:01]genbun\n[00:05]jigyou"))
            .put("tlyric", JSONObject().put("lyric", "[00:01]Translated text"))
        val lyrics = NetEaseLyrics.decode(root)!!
        val roman = Romanization.variant(lyrics)!!
        assertEquals(listOf(1000L, 5000L), roman.lines.map { it.timeMs })
        assertEquals("genbun", roman.lines.first().text)
        assertEquals("原文", lyrics.lines.first().text)
        assertEquals("NetEase", roman.romanizationSource)
    }
    @Test fun unavailableLyricsDoNotBecomeAnInstrumentalClaim() {
        assertNull(NetEaseLyrics.decode(JSONObject().put("nolyric", true)))
        assertNull(NetEaseLyrics.decode(JSONObject().put("code", 403)))
    }
    @Test fun unhealthyFallbackMovesDownAndRecoversAfterCooldown() {
        var time = 0L
        val health = LyricsProviderHealth { time }
        assertEquals("NetEase", health.order().first())
        health.failure("NetEase"); assertEquals("lyrics.ovh", health.order().first())
        health.failure("NetEase"); assertFalse(health.available("NetEase"))
        time = 600001; assertTrue(health.available("NetEase"))
        health.success("NetEase"); assertEquals("NetEase", health.order().first())
    }
    @Config(sdk = [28])
    @Test fun olderDevicesDoNotCallUnavailableNativeTransliteration() {
        assertNull(Romanization.variant(Lyrics(plain = "नमस्ते")))
    }
    @Test fun lrclibSuccessNeverRequestsAnotherProvider() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<String>()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url.host
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"plainLyrics\":\"Primary fixture\"}".toResponseBody()).build()
        }.build()
        assertEquals("LRCLIB", LyricsRepository(SourceHttpClient(client)).find(Track("fixture", "Song", "Artist")).lyrics!!.provider)
        assertEquals(listOf("lrclib.net"), requests)
    }
    @Test fun missingLrclibAutomaticallyUsesMatchedNetEaseBeforePlainFallback() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<String>()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url; requests += url.host + url.encodedPath
            val response = when {
                url.host == "lrclib.net" && url.encodedPath.endsWith("get") -> 404 to ""
                url.host == "lrclib.net" -> 200 to "[]"
                url.encodedPath.contains("search") -> 200 to "{\"result\":{\"songs\":[{\"id\":123,\"name\":\"Song\",\"duration\":180000,\"artists\":[{\"name\":\"Artist\"}]}]}}"
                else -> 200 to "{\"code\":200,\"lrc\":{\"lyric\":\"[00:01]Fallback fixture\"}}"
            }
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(response.first).message("fixture").body(response.second.toResponseBody()).build()
        }.build()
        val loaded = LyricsRepository(SourceHttpClient(client)).find(Track("fixture", "Song", "Artist", durationMs = 180000)).lyrics!!
        assertEquals("NetEase", loaded.provider)
        assertEquals("Fallback fixture", loaded.lines.first().text)
        assertFalse(requests.any { it.contains("lyrics.ovh") })
    }

}
