package com.wonderplay.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.wonderplay.MainActivity
import com.wonderplay.WonderPlayApp
import com.wonderplay.domain.Track
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Exercises the actual local library, mini-player spring, expanded controls and recreation. */
class PlayerExpansionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun localTrackCanExpandCollapseRepeatedlyWithoutCrashing() {
        val app=ApplicationProvider.getApplicationContext<WonderPlayApp>()
        val file=File(app.cacheDir,"expansion-test.wav")
        val samples=44100*30
        val bytes=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36+samples*2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples*2)
        repeat(samples){bytes.putShort(0)};file.writeBytes(bytes.array())
        val track=Track("local:expansion","Expansion regression","Local fixture",source="local",streamUrl=file.toURI().toString(),durationMs=30000)
        runBlocking { app.container.library.saveLocalTrack(track) }
        compose.onNodeWithContentDescription("Library",useUnmergedTree=true).performClick()
        compose.onAllNodesWithText("On device",useUnmergedTree=true).onFirst().performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText(track.title).fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithText(track.title).onFirst().performClick()
        compose.waitUntil(5000){compose.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty()}
        repeat(5) {
            compose.onAllNodesWithText(track.title).onLast().performClick()
            compose.onNodeWithContentDescription("Close player").assertIsDisplayed()
            compose.onNodeWithContentDescription("Playback position").assertExists()
            compose.onNodeWithContentDescription("Close player").performClick()
        }
        compose.onAllNodesWithText(track.title).onLast().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Close player").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close player").performClick()
        compose.onNodeWithContentDescription("Options for ${track.title}").performClick()
        compose.onNodeWithText("Play next").assertDoesNotExist()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("View artist").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Track details").performScrollTo().assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        runBlocking { app.container.library.removeLocalTrack(track.id) }
    }
}
