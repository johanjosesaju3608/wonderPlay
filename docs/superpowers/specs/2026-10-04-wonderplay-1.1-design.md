# wonderPlay 1.1 design for review

## Outcome

Remove generated on-device romanization, fix expanded-player bottom corners, and add private offline downloads without treating public streaming availability as download permission. Existing streaming, queue, lyrics, artwork and mini-player gestures remain functional.

## Lyrics and player shape

Remove Android ICU conversion entirely. Show the Original/Romanized pill only when a matched lyrics provider supplies a distinct romanized variant. Keep provider attribution and provider timestamps; translations are not romanization. Malayalam and other scripts remain in their original form when no provider romanization exists. Do not query a fallback solely to replace an otherwise successful LRCLIB result in this release.

Expanded player retains its existing top corners, but bottom corners interpolate to zero during expansion and finish flush against the bottom edge. Mini-player shape and gestures stay unchanged.

## Download permission

Streaming and downloading are separate capabilities. Add an explicit provider download entitlement containing a permitted download URL and permission provenance. A public playback URL alone does not grant this entitlement. Download actions are enabled only where a provider authorizes downloading or suitable authorization is established. YouTube tracks currently have no such entitlement in the app; do not present their public stream URLs as permitted downloads. Show a concise unavailability explanation in the requested download controls. Imported local files already play offline and do not need another downloaded copy.

A source with explicitly permitted downloads, or documented authorization for the requested catalog, is required to make remote downloads available to users. Source selection is the outstanding product decision; do not add an unrelated catalog silently.

## Storage and persistence

Store audio in internal files/downloads, not Android shared Downloads or the artwork cache. Do not export audio through MediaStore, FileProvider, share sheets or a documents provider. App uninstall/data clearing removes downloads. App backup remains disabled. Keep track identity and downloaded representation metadata separate from library metadata.

Add Room download records with track ID, status, private filename, MIME type, byte count and permission provenance. Use an explicit migration preserving favorites, playlists, history and saved queues. Avoid storing expiring playback credentials as durable track metadata.

Use a persistent WorkManager queue for user-requested eligible downloads. Show queued/downloading/ready/failed states and progress. Offer cancel, retry and remove. Respect Wi-Fi-only settings and check storage space. Write to a bounded temporary file, validate completed content, then atomically promote it. Partial files are never playable. Retry interrupted work from a fresh authorized URL; do not assume range resume works. Remove partial files after cancellation/failure and reconcile records/files after restart. Download artwork for offline display when available; keep it app-private.

## Playback and user interface

Add Download/Cancel download/Remove download to the existing three-dot track menu and a clearly labeled download control in the expanded player. Display why unsupported tracks cannot download. Add a Downloads section in Library with states, retry/removal and size information.

Playback checks a completed private file before remote resolution and playback network restrictions, keeping the original track identity for queue/favorites/history. Missing or corrupt downloads are marked unavailable and may fall back to streaming only when connected; offline errors explain what happened. Cache clearing does not delete downloads. Deleting an actively used file must coordinate with playback so it cannot abruptly invalidate the open track. Local offline playback must not start lyrics, recommendations or radio network requests while disconnected.

## Validation and release

Verify provider-only romanization and absent-pill behavior, expanded bottom corners and unchanged mini-player gestures. Test entitlement enforcement, interrupted/cancelled downloads, storage limits, atomic completion, database migration, removal and missing-file recovery. Use controlled audio fixtures for deterministic download tests. Verify offline playback after process restart with networking disabled and preservation of queue/favorites.

Run unit and Android integration tests, debug/release lint, APK signing/alignment checks, and compatible update installation over 1.0.6. Update privacy/source documentation with the actual selected download provider and publish signed wonderPlay 1.1.0 plus checksum to the existing GitHub repository after verification.

## Repository presentation

Replace the industrial front page with a centered existing logo, “Good music. Your own rhythm.” tagline, prominent latest-release download link and three real app screenshots. Keep five short user-facing feature bullets, simple installation help, a brief source attribution and licensing links. Move build/signing commands into BUILDING.md, historical changes to release notes, and device-specific test limits to QA.md. Do not publish unreleased 1.1 features as available or remove required credits/privacy information. A draft front page is available locally at /tmp/wonderplay-README-preview.md for review.

## Download approaches researched

1. Recommended: private offline downloads from explicit artist-authorized or appropriately licensed direct audio URLs, with retained permission/source metadata. This can serve owned or licensed recordings, but does not supply a rights-cleared catalog of mainstream YouTube songs.
2. License-aware music-service integration: requires checking the service's app/API terms and each recording's download permission. This adds a separate catalog, and its library may not cover the songs users search for on YouTube Music. Do not silently substitute recordings.
3. General YouTube caching like SimpMusic: technically available, but no published blanket download authorization was established. App-private storage and disclaimers do not establish permission. Do not describe this as a legally safe solution.

SimpMusic documents private offline caching and separate file export (https://www.simpmusic.org/docs/guide/downloads-offline). Its repository includes a disclaimer and assigns users responsibility for local law and platform terms (https://github.com/maxrave-dev/SimpMusic). YouTube terms distinguish authorized access from downloading (https://uk.youtube.com/t/terms). YouTube API developer policies prohibit offline copies without prior written approval (https://developers.google.com/youtube/terms/developer-policies); that API policy applies to API clients, and is not itself a claim that wonderPlay uses that API. No legal opinion or guarantee follows from these technical findings.
