package com.wonderplay.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.wonderplay.domain.AppSettings
import com.wonderplay.domain.Track
import com.wonderplay.source.LrcParser
import com.wonderplay.source.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LyricsIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track("fixture", "Lyrics fixture", "Test artist")
    @Test fun syncedViewSeeksPausesAndClosesWithoutAffectingQueue() {
        var position by mutableLongStateOf(1000L)
        var playing by mutableStateOf(true)
        var open by mutableStateOf(true)
        var sought = -1L
        compose.setContent {
            WonderTheme(AppSettings(reducedMotion = true)) {
                if(open) FullLyrics(Lyrics(lines = LrcParser.parse("[00:01]First fixture\n[00:05]Second fixture\n[00:10]Third fixture")), track, position,
                    { sought = it; position = it }, { playing = !playing }, playing) { open = false }
            }
        }
        compose.onNodeWithTag("Full screen lyrics").assertIsDisplayed()
        compose.onNodeWithText("Second fixture").performClick()
        compose.runOnIdle { assertEquals(5000L, sought) }
        compose.onNodeWithContentDescription("Pause lyrics playback").performClick()
        compose.onNodeWithContentDescription("Play lyrics playback").assertExists()
        compose.onNodeWithContentDescription("Close lyrics").performClick()
        compose.onNodeWithTag("Full screen lyrics").assertDoesNotExist()
    }
    @Test fun plainLyricsClearlyRemainUnsynced() {
        compose.setContent { WonderTheme(AppSettings(reducedMotion = true)) { FullLyrics(Lyrics(plain = "Plain fixture\nMore fixture", provider = "lyrics.ovh"), track, 0L, {}, {}, false, {}) } }
        compose.onNodeWithText("Unsynced · lyrics.ovh").assertIsDisplayed()
        compose.onNodeWithText("Plain fixture").assertIsDisplayed()
        compose.onNodeWithText("Following").assertDoesNotExist()
    }
    @Test fun previewShowsFourLinesAndOpensFullScreen() {
        val lyric = Lyrics(lines = LrcParser.parse("[00:01]Fixture one\n[00:05]Fixture two\n[00:10]Fixture three\n[00:15]Fixture four\n[00:20]Fixture five"))
        compose.setContent { WonderTheme(AppSettings(reducedMotion = true)) { LyricsPanelContent(com.wonderplay.source.LyricsState(track.id, lyrics = lyric), track, 1000, true, {}, {}, {}) } }
        compose.onNodeWithText("Fixture four").assertExists()
        compose.onNodeWithText("Fixture five").assertDoesNotExist()
        compose.onNodeWithTag("Synced lyrics preview").performClick()
        compose.onNodeWithTag("Full screen lyrics").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close lyrics").performClick()
        compose.onNodeWithTag("Full screen lyrics").assertDoesNotExist()
    }
    @Test fun missingLyricsShowClearStatusAndSearchAction() {
        compose.setContent { WonderTheme(AppSettings()) { LyricsPanelContent(com.wonderplay.source.LyricsState(track.id, message = "No lyrics found in the available sources."), track, 0, false, {}, {}, {}) } }
        compose.onNodeWithText("No lyrics found in the available sources.").assertIsDisplayed()
        compose.onNodeWithText("Search for lyrics").assertIsDisplayed()
        compose.onNodeWithText("Show lyrics").assertDoesNotExist()
    }

    @Test fun romanizationPillPreservesSeekingAndReturnsToOriginal() {
        val original = Lyrics(lines = listOf(com.wonderplay.source.LyricLine(1000, "नमस्ते"), com.wonderplay.source.LyricLine(5000, "दुनिया")))
        var sought = -1L
        compose.setContent { WonderTheme(AppSettings(reducedMotion = true)) { FullLyrics(original, track, 0, { sought = it }, {}, false, {}) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Romanized").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Romanized").performClick()
        val roman = com.wonderplay.source.Romanization.variant(original)!!
        compose.onNodeWithText(roman.lines[1].text).performClick()
        compose.runOnIdle { assertEquals(5000L, sought) }
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithText("नमस्ते").assertIsDisplayed()
    }

}
