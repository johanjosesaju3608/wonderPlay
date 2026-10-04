# wonderPlay 1.1.1 implementation plan

Execution: inline, explicitly requested by the user.

1. Add regression tests for actual audio stream selection, structured catalog metadata and unsupported download controls. Run tests to establish missing behavior.
2. Add AudioQuality settings migration, source-level selection and quality-aware resolver cache. Add separate settings pills and restrained dialog transition.
3. Add bounded YouTube Music metadata/artist browse parser, canonical identities, artist picker and track-details cards. Preserve optional identities in both metadata codecs.
4. Add eligibility caching with bounded concurrent lookups; retain active/ready controls. Move status icon below Like. Keep license verification at download time.
5. Personalize featured playlists from artist affinity, refresh when affinity changes. Add spring pull refresh and content appearance; keep local library reactive. Add marquee and current-track menu guard.
6. Run unit, Android and lint checks; inspect focused review, fix findings. Verify signed upgrade, then commit/push/release 1.1.1 APK and checksum.

## Review and verification

Implemented inline. Focused review found a partial artist-credit merge; fixed by merging canonical matched-song artists with Now Playing identities. A signed UI check found the options sheet's partial expansion placed lower actions under system navigation; changed it to a fully expanded, scrollable sheet and added reachability assertions.

65 unit tests and 17 permanent Android tests pass. A separate temporary live-catalog probe passed with four collaborators and the Pathaan album, plus ten ranked Weeknd tracks and twenty artist-page/official playlists. Temporary network probe source is not committed. Signature matches previous releases. Final APK/publication verification is recorded in the release QA note.
