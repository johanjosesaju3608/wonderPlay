package com.wonderplay.download
import com.wonderplay.domain.Track
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[35])
class PermittedAudioTest {
 private fun item() = JSONObject().put("metadata", JSONObject().put("licenseurl", "http://creativecommons.org/licenses/by-nc-sa/3.0/").put("creator", "Artist"))
  .put("files", JSONArray().put(JSONObject().put("name", "song.mp3").put("title", "Song").put("creator", "Artist").put("format", "VBR MP3").put("size", "10000").put("length", "03:00")))
 @Test fun exactRecordingRequiresTitleArtistAndDuration() {
  val found = PermittedAudioSource.decode("release", item()).single()
  assertTrue(PermittedAudioSource.matches(Track("id", "Song", "Artist", durationMs=180000), found.track))
  assertFalse(PermittedAudioSource.matches(Track("id", "Song (Live)", "Artist", durationMs=180000), found.track))
  assertFalse(PermittedAudioSource.matches(Track("id", "Song", "Other", durationMs=180000), found.track))
  assertFalse(PermittedAudioSource.matches(Track("id", "Song", "Artist", durationMs=200000), found.track))
 }
 @Test fun unspecifiedLicenseAndRestrictedItemsAreRejected() {
  assertTrue(PermittedAudioSource.decode("release", item().put("is_dark", true)).isEmpty())
  val j=item();j.getJSONObject("metadata").remove("licenseurl");assertTrue(PermittedAudioSource.decode("release",j).isEmpty())
  assertTrue(PermittedAudioSource.decode("release",item().put("nodownload",true)).isEmpty())
 }
 @Test fun unsafeFileNamesAndOversizedFilesAreRejected() {
  val j=item();j.getJSONArray("files").getJSONObject(0).put("name","../song.mp3");assertTrue(PermittedAudioSource.decode("release",j).isEmpty())
  j.getJSONArray("files").getJSONObject(0).put("name","song.mp3").put("size",200000000);assertTrue(PermittedAudioSource.decode("release",j).isEmpty())
 }
 @Test fun licenseHostCannotBeSpoofed() {
  assertNull(PermittedAudioSource.license("https://creativecommons.org.evil.test/licenses/by/4.0/"))
  assertNull(PermittedAudioSource.license("https://example.org/free"))
  assertNotNull(PermittedAudioSource.license("https://creativecommons.org/publicdomain/zero/1.0/"))
 }
}
