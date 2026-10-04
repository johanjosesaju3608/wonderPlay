# wonderPlay

<img src="docs/branding/wonderplay-logo.svg" alt="wonderPlay logo" width="160" />

A quiet, open-source Android music player with real streaming, a local-first library, and a persistent background player. No account wall, advertising SDK, analytics or cloud inference.

## Install

Download **wonderPlay-1.0.6.apk** from [Releases](https://github.com/johanjosesaju3608/wonderPlay/releases). Android 8.0 (API 26) or newer is supported. The app targets Android 16 (API 36), and the universal APK supports modern Samsung Galaxy devices, including the S25. Android may ask you to allow installation from your browser or file manager.

## New in 1.0.6

- Free lyrics fallback: LRCLIB first, then metadata-matched NetEase, then lyrics.ovh. Repeatedly failing fallback providers are temporarily deprioritized. No API key is required.
- Full-screen lyrics can switch to provider romanization or on-device Latin transliteration on Android 10+, when supported. Timestamps and line seeking are preserved.
- Content scrolls behind the floating navigation and mini-player. Tab icons briefly shrink and dim, then recover as the selected pill expands, without a ripple.
- Home includes listening-based recommendations. Optional autoplay adds YouTube Music radio suggestions when the queue runs low; local songs are excluded.
- Media3 preloads up to eight seconds of the next track. Public playback can use audio from a muxed stream if a separate audio stream is unavailable.

## New in 1.0.5

- Navigation tap feedback stays within its rounded pill. Gentle directional slide/fade transitions connect tabs, settings and collection pages; reduced motion disables them.
- Search opens to discovery without opening the keyboard. Tap the search field to type; recent searches appear as compact pills.
- Official YouTube Music charts and top-song playlists appear as rounded, two-column cards.
- Song suggestions use recent listening and favorite artists to find unheard tracks, with artist variety. New listeners get a starter discovery selection. Local-file metadata is excluded from recommendation requests.
- Discovery requests are independent from search pagination; chart results are cached for ten minutes and recommendations refresh when listening history or favorites change.

## New in 1.0.4

- Search includes albums and official YouTube Music featured playlists by name. Exact titles rank first; community playlists and personal mixes are excluded. Collection search is cancellable and independent from song-search paging.
- Floating themed navigation with a selected icon-and-label pill, accessible tab labels and reduced-motion support.
- Local tracks no longer fetch or display lyrics.
- More cover URL formats request sharp artwork. Expanded video thumbnails try a higher-resolution image, falling back when unavailable or a small placeholder is returned.
- Shuffled playback starts with the chosen track and visits the remaining queue; Up Next displays the real playback order. Turn shuffle off to reorder manually.

## New in 1.0.3

- Four-line synced lyrics preview beneath the expanded player's controls. Tap to open full-screen lyrics, follow playback, seek by tapping a line, or browse with following switched off.
- LRCLIB exact lookup and conservative title/artist/duration matching, with lyrics.ovh fallback for plain lyrics. Missing lyrics show provider availability status, retry and an external web search; no claim that lyrics do not exist anywhere.
- Live, region-aware YouTube Music featured playlists on Home, with refresh and playlist opening.
- High-resolution cover URLs supplied to Android media metadata; dynamic album colors are automatic. Audius has been removed.
- Samsung Now Bar and Vivo Origin Island remain controlled by device firmware. Standard MediaSession playback and artwork are exposed; no physical Vivo device was available to verify Origin Island.

## Music sources

- **Your files:** choose audio through Android’s file picker. The app retains read access without uploading or copying audio files. Local playback works offline.
- **YouTube Music (default):** song search and in-app public audio playback through NewPipe Extractor. No account integration or paid-content access. Restricted tracks and upstream changes can affect availability. The original service can also be opened externally.

The app is independent and unaffiliated with YouTube, Google, Apple or Spotify. Upstream availability is not guaranteed. No provider API key or login is required.

## Features

- Dark, light and system appearance; automatic album colors, readable cover-based accents and player gradients, tactile controls and reduced-motion settings.
- Cancellable, debounced search with local music ranking and duplicate filtering. Results render before artwork loads.
- Media3 foreground playback, lock-screen/notification/headset controls, audio focus and unplug handling.
- Persistent mini-player with four-way swipes: up to expand, down to stop/clear, right for previous and left for next. Queue boundaries fade the bar without restarting playback.
- Expanding Now Playing, scrubbing, shuffle, repeat, queue reorder/removal and retry.
- Room-backed favorites, history and playlists with create/rename/delete, add/remove and ordering controls.
- Artist/album details from provider metadata, plus albums/artists drawn from your actual library.
- Selected-file import, embedded artwork, offline library metadata, Wi-Fi-only streaming preference, and cache/history clearing.
- Source artwork with deterministic fallback covers; missing remote artwork can use bounded MusicBrainz/Cover Art Archive matching.

## Screenshots

Real release-build emulator captures are included in [docs/screenshots/](docs/screenshots/).

## Build

Use JDK 17, Android SDK platform 36 and Build Tools 35.0.0 or newer. Set `ANDROID_HOME`, or create an untracked `local.properties` containing `sdk.dir=/your/sdk`.

```sh
git clone https://github.com/johanjosesaju3608/wonderPlay.git
cd wonderPlay
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Start an emulator or connect an Android device with USB debugging, then run:

```sh
./gradlew connectedDebugAndroidTest
```

Gradle 8.13 (checksum-pinned), AGP 8.13.2 and Kotlin 2.3.0 are a tested compatible toolchain. Dependency versions are centralized in `gradle/libs.versions.toml`.

## Signed release builds

Keep the same private release keystore for compatible updates. Never commit the key or passwords.

```sh
export WONDERPLAY_KEYSTORE=/secure/location/wonderplay-release.jks
export WONDERPLAY_KEY_ALIAS=wonderplay
export WONDERPLAY_STORE_PASSWORD='your-store-password'
export WONDERPLAY_KEY_PASSWORD='your-key-password'
./gradlew testDebugUnitTest lintRelease assembleRelease
./scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk
```

Without signing variables, release output is unsigned and cannot be installed. The published APK uses a dedicated release key; a debug APK has a different signature. The release keystore is stored privately outside this repository.

A manual CI workflow template is in `docs/ci/android.yml`. The currently available GitHub credential cannot publish active workflow files. To enable it with an appropriately authorized credential, move the template to `.github/workflows/android.yml`.

## Architecture

| Package | Responsibility |
| --- | --- |
| `domain` | Provider-independent models and contracts |
| `source` | YouTube Music / NewPipe, featured playlists, lyrics and selected-file import |
| `metadata` | Normalization, deterministic ranking, duplicate handling, artwork fallback |
| `data` | Room transactions, saved queue and DataStore preferences |
| `player` | Service-owned ExoPlayer, MediaSession, reactive controller, queue and stream resolution |
| `ui` | Compose design system, screens, sheets and haptics |

The foreground service owns audio; activity recreation does not create another player. Restored queues start paused. Stream resolution runs on the player’s loader thread, outside Compose rendering. Audio is buffered transiently, not saved for offline downloading.

## Privacy

Library metadata and settings stay in private app storage; Android cloud backup is disabled. YouTube/Google receives search/track/stream, charts, featured-playlist recommendation artist-search and radio requests. LRCLIB, NetEase and lyrics.ovh receive song metadata for lyrics lookup. Artwork comes from provider hosts, with MusicBrainz/Cover Art Archive fallback for missing remote artwork. Providers receive ordinary network metadata such as your IP address. There is no developer-operated backend. See [PRIVACY.md](PRIVACY.md).

## Validation and limits

See [docs/QA.md](docs/QA.md) for executed checks. Galaxy S25-specific haptics, Bluetooth routing, battery behavior and 120 Hz frame pacing require physical-device testing; emulator results do not certify them.

The player streams the source’s available representation without transcoding. No lossless, crossfade or normalization claim is made. Featured playlists load up to five pages (usually 500 tracks); larger lists explicitly identify the loaded subset. Artist views depend on available search results. Metadata completeness and artwork availability vary. Remote music needs a connection; Wi-Fi-only applies to playback, not all search/artwork traffic.

## Troubleshooting

- **No search match:** try another title or artist.
- **Playback unavailable:** check your connection and Wi-Fi-only preference, then retry. Restricted or removed tracks cannot be unlocked by the client.
- **Local file inaccessible:** reselect a moved/deleted file or renew its document-provider access.
- **Samsung background interruptions:** check the app’s battery/background restrictions in Android settings.
- **Signature conflict during install:** an APK signed with another key cannot update this one. Uninstalling the old build removes its local library.

## License

Original app code: [MIT](LICENSE). The combined app is distributed under [GPL-3.0-or-later](LICENSE-GPL-3.0), including NewPipe Extractor. Music and cover artwork remain owned by their respective rights holders. Dependency notices and bundled license texts are in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and the app’s settings.
