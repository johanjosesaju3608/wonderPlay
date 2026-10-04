# wonderPlay 1.1 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task in this session.

**Goal:** Ship provider-only romanization, flush expanded-player bottom corners, permission-aware private offline downloads, and a polished repository.
**Architecture:** A separate Room download database keeps download jobs and completion records independent of the existing library. WorkManager owns persistent transfers. Playback chooses validated private audio before remote resolution. Internet Archive supplies explicitly licensed audio with conservative recording matching; YouTube playback availability is not download entitlement.
**Tech Stack:** Kotlin, Compose, Room, WorkManager, OkHttp, Media3.
**Spec:** ../specs/2026-10-04-wonderplay-1.1-design.md

## Global constraints
- App-private audio only; no export, broad storage permission, accounts or API keys.
- Preserve library and original track identity; never substitute mismatched recordings.
- Mini-player shape/gestures unchanged. No generated transliteration.
- Publish 1.1.0 using the existing signing key after validation.

## Review focus
- Cancellation racing completion must never publish cancelled audio.
- Missing/truncated files must never be considered ready offline.
- License/title/artist/duration mismatches must reject download candidates.
- Wi-Fi-only restrictions must not block completed private audio.
- Downloads must survive process recreation without deleting library records.

### Task 1: Lyrics, corners and repository
- [x] Add a failing test asserting unsupported non-Latin lyrics have no romanized variant.
- [x] Remove ICU conversion; test provider text retains timestamps; change Android fixture to provider-supplied romanization.
- [x] Interpolate expanded bottom corner radius to zero; retain mini-player radius.
- [x] Apply approved README; preserve build/signing details in BUILDING.md and version history in releases.

### Task 2: Entitlement provider and persistent downloads
Files: download/PermittedAudioSource.kt, DownloadDatabase.kt, DownloadRepository.kt, DownloadWorker.kt; WonderPlayApp.kt; gradle/libs.versions.toml.
Interfaces: PermittedAudioSource.find(track): DownloadEntitlement?; DownloadRepository.entries: Flow<List<DownloadEntry>>; enqueue(track), cancel(id), remove(id), offline(track): PlaybackSource?.
- [x] Write failing tests for exact recording/license matching, excluded restricted items and malformed file paths.
- [x] Implement bounded Internet Archive licensed-audio lookup and store source/license attribution. Permit only recognized CC licenses/CC0, not unspecified or merely uploader-claimed “free”.
- [x] Persist jobs in separate Room DB and queue unique WorkManager work with connectivity constraints.
- [x] Transfer to a bounded temporary file with storage checks, cancellation and progress; verify decodable audio then atomic rename. Retain offline artwork when available.
- [x] Test fixture download completion/cancellation/private path/missing-file behavior; test database recreation.

### Task 3: Playback and controls
Files: PlaybackResolution.kt, SourceRegistry.kt, AppViewModel.kt, DownloadsScreen.kt, LibraryScreen.kt, WonderPlayRoot.kt, PlayerScreen.kt.
- [x] Add offline-source preference before remote network policy.
- [x] Add menu and expanded-player download actions; Library Downloads shows progress, cancel/retry/remove and attribution.
- [x] Permit discovery of licensed recordings inside Downloads without changing YouTube song search; matching lookup for existing remote tracks remains conservative.
- [x] Verify offline playback after restart, and safe removal/cancellation.

### Task 4: Verification/release
- [x] Run unit tests, Android integration tests, debug/release lint and builds; request focused fresh review.
- [x] Verify signed update over 1.0.6, private offline playback and final screens.
- [ ] Update privacy/docs, commit/push and publish signed 1.1.0 APK/checksum; verify public download hash.

## Execution rulings
- Work in the existing task-owned checkout to preserve configured builds/signing and the approved local spec; no unrelated user checkout is being modified.
- Separate Room database avoids a migration of the existing library schema and isolates download failures; library records remain untouched.
- Public license metadata is not a blanket legal guarantee. Download coverage is limited to matched eligible recordings; no source-ripping fallback.

Verification ledger: 59 unit tests, 15 Android tests and a separate real licensed-download check passed. Offline playback after force-stop with airplane mode/Wi-Fi disabled passed. Final debug/release lint/build and signed update over 1.0.6 passed; same certificate and 16 KB alignment verified. Focused review reported no critical/important findings.
