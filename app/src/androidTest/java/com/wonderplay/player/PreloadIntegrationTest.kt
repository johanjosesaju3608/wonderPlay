package com.wonderplay.player

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.wonderplay.AppViewModel
import com.wonderplay.MainActivity
import com.wonderplay.domain.Track
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PreloadIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun nextSourceLoadsBeforeCurrentEndsAndTransitionsWithoutStopping() {
        val file = File(compose.activity.cacheDir, "preload-fixture.wav")
        val samples = 44100 * 30
        val data = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { data.putShort(0) }; file.writeBytes(data.array())
        lateinit var vm: AppViewModel
        compose.runOnUiThread { vm = ViewModelProvider(compose.activity)[AppViewModel::class.java] }
        val tracks = (0..1).map { Track("local:preload-$it", "Preload $it", "Fixture", source = "local", streamUrl = file.toURI().toString(), durationMs = 30000) }
        try {
            compose.runOnUiThread { vm.player.setShuffle(false); vm.player.setRepeat(com.wonderplay.domain.RepeatMode.OFF); vm.player.play(tracks) }
            compose.waitUntil(10000) { vm.player.state.value.isPlaying && ResolutionState.values.value.values.count { it.quality == "Local file" } >= 2 }
            assertEquals(0, vm.player.state.value.index)
            assertTrue(vm.player.state.value.positionMs < 15000)
            compose.runOnUiThread { vm.player.seekTo(29500) }
            compose.waitUntil(3000) { vm.player.state.value.index == 1 && vm.player.state.value.isPlaying }
            assertEquals(2, vm.player.state.value.queue.size)
        } finally { compose.runOnUiThread { vm.player.clearQueue() }; file.delete() }
    }
}
