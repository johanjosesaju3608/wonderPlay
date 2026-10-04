# Architecture and behavioral guarantees


## Ownership

The foreground MediaSessionService owns a single ExoPlayer. UI lifecycle changes do not recreate it. The activity's PlayerController connects through MediaController and maps native queue/playback events into immutable PlayerState. The domain layer carries normalized tracks rather than provider JSON.

## Search

A new query cancels the previous job and changes a generation token. The result can update the screen only if its generation is still current. Text enters the UI before networking. A 250 ms debounce limits wasted requests. Results do not wait for image downloads or canonical artwork enrichment. Provider pagination is bounded.

## Playback

Metadata is put into Media3's queue immediately. Stream resolution happens on the player loader thread through a resolving data source, outside the main thread. Source requests time out and obey cancellation. Queue occurrences have unique identities so duplicate songs are independently removable/reorderable. Standard Media3 buffering prepares subsequent items without permanently saving audio. Native player state controls the displayed play/pause state, position and failure/retry behavior.

## Storage

Room stores metadata, ordered playlist membership, favorites, history, local file references and queue snapshots. Playlist edits and favorite toggles are transactional. DataStore preferences emit reactive state. Queue/position restoration is paused. Media bytes are not persisted by the remote provider.

## Source boundary

MusicSource exposes search, track/artist/album/playlist metadata, playback resolution and related tracks. The local source handles user-selected URIs. YouTubeMusicSource uses NewPipe Extractor for YouTube Music song search and public audio resolution. YouTube Music is the remote catalog. FeaturedPlaylists parses anonymous home-feed playlist endpoints. LyricsRepository cancels stale requests, caches up to 40 successful lookups in memory, and uses LRCLIB, then strictly matched NetEase, then lyrics.ovh fallback, with session-local fallback health ranking. There is no account or paid-content access.

## Artwork and metadata

Provider data is normalized without removing meaningful live/acoustic/remix/remastered versions. The ranking layer has a deterministic local scorer and engineered features; it does not falsely claim to ship a trained neural model. Artwork resolution uses exact-enough artist/title matching and bounded metadata requests, falling back to source artwork or the design system's deterministic artwork treatment. Images are decoded for their display size and cached separately from audio. Album colors uses a 96-pixel software decode and Palette off the main thread, then enforces text contrast. Missing covers fall back to Coffee. Expansion springs are clamped before use in layout. The player uses a gradient instead of a full-screen blurred image layer.

## Privacy and export

No developer server receives library or device data. Only source/music metadata services are contacted for explicit browsing/playback and related artwork. The repository excludes local SDK paths, signing keys, passwords, generated build outputs and IDE state. APKs belong to release assets, not Git history.

## Search and shuffle in 1.0.4

Song search keeps its own NewPipe paging cursor. Collection search uses an independent album extractor plus the dedicated YouTube Music featured-playlist filter, with conservative editorial ID checks. Both jobs are cancelled/replaced when the query changes. Exact collection names rank before broad matches.

The service anchors shuffle order to the current queue occurrence when shuffle is enabled or the queue is replaced. The controller publishes Media3's playback order for Up Next. Local tracks do not request lyrics. The floating navigation uses the existing dynamic Material colors and honors reduced motion.

Media3 preloads eight seconds of the next queue item. Near the end of a remote queue, anonymous YouTube Music radio suggestions are filtered for duplicate recordings and appended only if the queue occurrence snapshot remains unchanged. Autoplay is optional and excludes local files.
