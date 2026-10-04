# wonderPlay 1.1.1 — approved design

User approved the combined scope and explicitly requested immediate inline implementation and release.

Keep streaming behavior and existing library intact. Hide downloads until a published matching-recording license is verified; keep active and completed downloads visible. Put the expanded-player control directly beneath Like with circular progress, cancel and completed states. NCS needs standalone playback/download permission; its UGC policy is not sufficient.

Persist three quality levels, High by default; migrate the previous preference. Low targets 64 kbps, Medium 128 kbps, High the highest available audio. Choose actual provider streams, using the lowest available fallback when a requested ceiling is unavailable. Apply to future resolution without interrupting the current track.

Home and Search support spring pull refresh and updated-content fade; preserve query and playback. Library observes local database changes and needs no refresh. Rank official featured playlists using recent artist affinity, with the regional feed as fallback.

Retrieve linked artist and album identities from YouTube Music metadata rather than splitting display names. Multiple artists open a two-column picker. Artist pages show live top ten tracks followed by playlists returned from the canonical artist page and relevant official featured playlists. Track details show the track followed by artist and album cards. Missing metadata is explicit; never fabricate identities or ownership.

Hide Play next on the current song. Scroll overflowing search and mini-player labels, respecting reduced motion. Quality pills appear beneath the description. About dialogs use restrained fade/scale motion.

Validate parser identity and ownership boundaries, quality selection/migration, download eligibility, existing playback tests, emulator gestures and settings. Build signed versionCode 9/versionName 1.1.1, push source and publish APK with checksum.
