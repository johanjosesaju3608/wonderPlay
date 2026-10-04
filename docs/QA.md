# wonderPlay 1.0.6 validation

- 51 unit tests passed, including fallback order/health, NetEase recording matching, LRCLIB short-circuit behavior, romanization availability, public muxed-audio fallback and radio duplicate filtering.
- 12 Android 15 emulator tests passed, including next-source preparation before the current local track ended, continued playback after transition, and original/romanized lyric switching with unchanged seek timestamps. Existing player, gestures, shuffle, search, library and lyrics checks also passed.
- Debug/release lint and debug/release builds passed. The signed APK passed signature and 16 KB alignment checks, uses the existing release certificate, and reports versionCode 7 / versionName 1.0.6.
- Separate live debug check: the first Jhoome Jo Pathaan search result played; LRCLIB returned 43 synced lines; radio expanded a one-song queue to seven. Seeking to 25 seconds before the end prepared the next source before the transition, and Nashe Si Chadh Gayi played next. Clearing stopped the queue. An earlier probe expected remote preloading within the first 30 seconds and timed out: Media3 prioritizes ongoing playback loading before preloading; this was not a playback failure.
- Signed-release checks: installation over 1.0.5 succeeded without uninstalling between versions; Home recommendations loaded, playlist artwork appeared behind the floating navigation, and Jhoome Jo Pathaan played with active mini-player controls.
- The reported “public source not found” failure could not be reproduced for Jhoome Jo Pathaan. Public audio formats resolved and played during checks. The new muxed-stream fallback handles missing separate audio streams; restricted/removed or changing upstream content can still fail. No universal availability or zero-gap guarantee is made.

See [lyrics source selection](LYRICS_SOURCES.md) for the provisional provider order and excluded alternatives. Local files do not query lyrics providers or autoplay radio. Fallback provider failures affect session-only ranking; no reliability telemetry is collected. Native romanization is available on Android 10+, with provider romanization also supported on older versions.

## Earlier 1.0.5 validation


- 40 unit tests passed, including recommendation recency/favorite weighting, local-file exclusion, artist diversity, deduplication, whole-name artist matching and exclusion of alternate uploads of known recordings.
- 10 Android 15 emulator tests passed. Search opens unfocused, tapping the field focuses it, clearing hides focus, and returning to Search or rotating does not focus the field. Settings/library/playlist creation, playback, shuffle, player expansion, mini-player gestures and lyrics remain covered.
- Debug and release lint passed. The final signed APK passed signature and 16 KB alignment checks; versionCode is 6 and versionName is 1.0.5. The release key is unchanged and update installation over 1.0.4 succeeded.
- Live signed-release discovery loaded four official cards, including Trending 20 India and Daily Top Music Videos - India, alongside featured song playlists. Trending 20 India opened with 20 playable tracks. Keyboard state was checked before and after explicitly tapping the search field.
- Favoriting a Daft Punk track produced actual new Daft Punk recommendations. Recent searches appeared as pills. Discovery and personalized-screen screenshots were inspected.
- A focused read-only code review found no significant blockers. Recommendation extraction does not modify the user's search continuation cursor; ranking runs away from the UI thread. Chart data is cached for ten minutes. Reduced motion disables page slides/fades.

## Recommendation behavior

Artist affinity is computed on-device from recent remote listening (with recency weights) and favorites. Up to three artist searches supply candidates, with up to four unheard tracks per artist and twelve total. Known recording uploads are filtered while distinct versions remain eligible. Imported local-file metadata is excluded. A new listener receives starter discovery results. Recommendations are not a signed-in YouTube personalized feed and use no cloud inference. Source availability affects discovery; failures have retry controls.

## wonderPlay 1.0.4 validation

- 34 unit tests passed: metadata/ranking, queue state, Room persistence, expansion bounds, dynamic-color contrast, high-resolution artwork URLs, system artwork metadata, lyric parsing/timing/matching and featured playlist/song parsing.
- 10 Android 15 emulator tests passed: playback/seek/pause/error recovery, library/settings/search flows, mini-player gestures, repeated expansion/recreation, four-line lyrics preview/full-screen opening, lyric-line seeking, plain lyrics and unavailable-lyrics states.
- Debug and release lint passed. Signed release APK passed signature and 16 KB alignment checks.
- Signed 1.0.4 installed successfully over the published 1.0.3 APK using the unchanged release key.
- Live signed-release checks: regional featured playlists loaded; a playlist opened with canonical song titles/artists and 100 tracks; song search, expanded player, LRCLIB synced preview and full-screen lyrics worked. YouTube audio played after retrying an upstream HTML-instead-of-JSON response. Android reported the wonderPlay media session active and PLAYING with advancing position/buffering.

## Lyrics

Local files are excluded from lyrics lookup and the lyrics panel. LRCLIB exact lookup is followed by title/artist/duration-checked search, then metadata-matched NetEase and lyrics.ovh plain-lyrics fallback. Requests are cancellable when the track changes and successful results are cached only in memory, bounded to 40 tracks. LRC tests cover fractions, repeated stamps, offset tags, intros, backward seeking and invalid timestamps. Synced previews have four fixed rows. Full-screen lyrics follow playback, allow tapping a timestamped line to seek and support disabling following for manual browsing. Reduce motion disables transitions and animated following. Plain lyrics are labelled unsynced.

Unavailable results identify the available sources, not the entire internet. Provider failures are distinguished from confirmed missing responses. Retry and an external web-search button remain available. No lyrics are bundled in source/APK assets.

## System media controls

MediaSession receives the high-resolution (up to 1024 pixels) cover URL rather than the original 120-pixel Google thumbnail. Media3 decodes and shares artwork within Android's device-specific bitmap limits. The release exposes an active standard MediaSession, metadata, audio attributes and a MediaStyle notification. No physical Samsung or Vivo device was available for this release. In particular, Vivo Origin Island recognition has not been verified or claimed fixed; firmware-specific eligibility is outside emulator coverage.

## Limits

Featured playlists use the anonymous YouTube Music regional home feed and music playlist metadata. Lists are bounded to five pages and larger lists identify the loaded subset. No account sync or personalized signed-in feed is implemented. YouTube, LRCLIB, NetEase and lyrics.ovh availability and upstream response changes can affect requests. Missing artwork uses the existing theme fallback. Dynamic colors are automatic and Audius is no longer a supported provider; saved library metadata is retained.

Wi-Fi-only applies to remote playback, not browsing, lyrics or artwork. No remote offline downloads, lossless guarantee, crossfade or loudness normalization is shipped. Physical-device Origin Island/Now Bar appearance, Bluetooth routes, haptics, battery behavior and refresh-rate performance require device testing.

## 1.0.4 checks

The new shuffle test selects a nonzero initial index, confirms it starts the shuffled playback order, visits every other queue occurrence exactly once, wraps on repeat-all and turns shuffle off without resetting an advanced position. The test waits for repeat-mode application before requesting navigation. Queue order is read from Media3's timeline, not the saved library order. The service anchors a newly shuffled/replaced queue to the current item. Existing four-way mini-player gestures, expansion and recreation remain covered.

Cover URL tests include `w120-h120` and `s120` music image formats, unrelated/local hosts, and opt-in high-resolution video candidates. The UI rejects small successful placeholder images and falls back to the original thumbnail; unverified video candidates are not substituted into system metadata.

Search tests cover official editorial IDs versus community/personal-mix IDs, featured-result parsing and exact collection-name ranking. Live checks verify Random Access Memories album search/opening and Bollywood Hitlist official-playlist search/opening. The floating bar has accessible Home/Search/Library tabs and remains separated from the mini-player.

An initial Android emulator run was aborted by a system crash. Verification was repeated in a fresh emulator session; the aborted run is not counted as passing.

Playlist preloading uses Media3’s [PreloadConfiguration](https://developer.android.com/blog/posts/elevating-media-playback-introducing-preloading-with-media3-part-1): up to eight seconds of next-track media, once active playback loading allows it. Buffering stays transient and follows playback network restrictions.
