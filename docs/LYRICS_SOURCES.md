# Free lyrics source order

The default order is LRCLIB → NetEase → lyrics.ovh. LRCLIB remains first; fallback order responds to provider failures during the current app session. No API key, account or paid service is used.

| Source | Priority rationale | Coverage and limits |
| --- | --- | --- |
| [LRCLIB](https://lrclib.net/docs) | Public lyrics API with synchronized/plain lyrics and duration metadata; best fit for the player. | Community-contributed catalog. Exact lookup followed by conservative title, artist and duration matching. |
| [NetEase](https://music.163.com/) | Large music-service catalog, timed lyrics, artist/duration metadata and occasional romanized lyrics. | Public first-party endpoints are undocumented and can change or vary by region. Only closely matching recordings are accepted. |
| [lyrics.ovh](https://lyricsovh.docs.apiary.io/) | Simple public API, useful as an independent fallback. | Plain lyrics only, with less recording metadata for validation. |

Comparable lyric-catalog sizes and independent uptime statistics are not available. This is a provisional suitability ranking, not a measured claim that one library is larger. During release checks, NetEase returned synchronized lyrics for the matched Jhoome Jo Pathaan recording; lyrics.ovh returned missing for that song. LRCLIB also contained a matching synchronized record.

A failed fallback request adds a penalty to that provider’s rank. Two failures trigger a ten-minute cooldown. A successful response resets its penalty; a valid response with no lyrics is not counted as a service failure. LRCLIB is always tried first, and no fallback is queried when LRCLIB supplies usable lyrics. Local tracks make no lyrics requests.

Musixmatch was excluded because no developer key is available. SimpMusic’s lyrics service returned HTTP 403 in the development environment. [BetterLyrics authentication documentation](https://lyrics-api-docs.boidu.dev/docs/authentication) requires a key for uncached requests to its general endpoint, so it was not selected as an automatic key-free fallback. These observations do not claim permanent service unavailability.

Provider romanization is preferred. On Android 10 or newer, native ICU can alternatively transliterate supported non-Latin text on the device. The option appears only when it produces Latin text; it is labeled romanization, not English translation. Original timestamps remain unchanged. Unavailable lyrics retain retry and external web-search actions.
