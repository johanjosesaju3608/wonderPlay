# Privacy and source behavior


wonderPlay has no account service, telemetry endpoint, ad SDK, cloud inference, or developer-operated backend.

## Data on your device

Room stores track metadata, favorites, playlists and their order, listening history, recent searches, selected local-file URIs, and the saved queue/position. DataStore stores preferences. Coil caches artwork in memory and the app cache. Ordinary ExoPlayer buffering supports streaming; there is no download manager or permanent stream cache.

Library data is private app storage. Android cloud backup is disabled. Clearing app data or uninstalling deletes it. Clearing listening history and recent searches is available in the app. Choosing local files grants read access through Android's Storage Access Framework; files are not uploaded or copied into a music download directory.

## Network requests

- Artwork hosts returned by the public provider: requests for cover images displayed in the app.
- MusicBrainz (`musicbrainz.org`), where canonical artwork matching is requested: normalized artist/song text. Lookups are bounded and rate limited.
- Cover Art Archive (`coverartarchive.org`, potentially redirecting to `archive.org` infrastructure): release IDs and cover image requests after a metadata match.
- YouTube/Google (`music.youtube.com`, `youtube.com`, Google video/image hosts): featured-playlist requests, selected searches, recommendation and radio requests, track identifiers, public player metadata and audio requests via NewPipe Extractor. External service links remain available.

- LRCLIB (`lrclib.net`): song title, artist, album and duration for synced/plain lyrics lookup.
- NetEase (`music.163.com`): song title and artist for matching, then the matched song identifier for lyrics when LRCLIB has no usable result.
- lyrics.ovh (`api.lyrics.ovh`): artist and song title when earlier providers have no usable result. Lyrics are cached only in memory (up to 40 tracks). External lyrics search opens the browser with title and artist.

These providers receive ordinary HTTP information, including the user's IP address. wonderPlay adds no persistent device identifier. Third-party providers have independent policies and availability. Search does not use any remote AI model.

## Access and limits

Only publicly streamable, non-gated source audio is accepted. No login credentials, source access tokens, payment access, DRM keys are collected or implemented. NewPipe resolves public YouTube audio; the app does not unlock restricted content.

Wi-Fi-only restrictions are enforced for remote playback. Browsing/search, lyrics and artwork requests still use the available connection. They are not an app-wide firewall. Local files remain playable without networking.

## Permissions

- Internet and network state: search, artwork, streaming, and checking playback network preferences.
- Foreground media playback and wake lock: continuous playback and Android media controls.
- Vibration: optional tactile feedback.

There is no microphone, camera, location, contacts, phone state, broad file access or advertising ID permission. Media-session notifications use Android's media notification handling without an unrelated onboarding permission wall.

Recommendations rank music on your device using recent remote listening and favorites. Up to three preferred artist names are sent to YouTube Music to find new songs. Local-file metadata is excluded. There is no cloud inference or developer recommendation backend. Charts and starter discovery also use anonymous YouTube Music requests.

Optional autoplay sends the current remote track identifier to YouTube Music for radio suggestions. The next track may be resolved and buffered before the current song finishes. Wi-Fi-only playback restrictions also apply to that buffering. Romanization uses provider-supplied text or Android’s on-device ICU transliteration; lyrics are not sent to a translation service.
