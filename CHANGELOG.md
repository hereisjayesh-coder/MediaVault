# Changelog

All notable changes to this project are documented here. This project has not yet made
a tagged release; entries below track development stages instead of version numbers.

## [Unreleased]

### Added — Playlist analysis support

- `ExtractorEngine.analyze` now returns `ExtractionResult`, a sealed type of `Single`
  (exactly the previous `MediaAnalysisResult`, unchanged) or `Playlist`
  (`PlaylistAnalysisResult`) — single-video analysis behaves exactly as before, just
  wrapped. See `core/domain/.../extractor/PlaylistModels.kt`.
- `PlaylistAnalysisResult` carries the playlist's title, thumbnail, source, a
  best-effort `PlaylistCollectionType` (playlist/channel/other), a reported item count
  where available, and an ordered list of lightweight `PlaylistItem`s (id, title,
  thumbnail, duration, url, availability) — resolving one item's full formats happens
  later, on demand, via another `analyze()` call on that item's own URL.
- `YtDlpExtractorEngine`: the Python bridge now uses `extract_flat="in_playlist"`
  instead of `noplaylist=True`, so a playlist/channel URL returns its full ordered
  entry list (lightweight) instead of resolving only the first video. Unavailable
  entries (private/deleted, or a source-reported `null` slot) are mapped to
  `isAvailable = false` rather than dropped, preserving position.
- `DownloadRequest` and `DownloadTaskEntity` gained optional `sourceMediaId` and
  playlist-grouping fields (`playlistContext` / `playlistId` + `playlistItemIndex`) so
  a future download queue can turn each playlist item into its own ordered
  `DownloadTask` and support "skip already downloaded" via stable ids. No download
  behavior changed — these fields are unused until `DownloadEngine` has an
  implementation.
- Home screen: playlists show a "Playlist detected" header, item count, and an ordered,
  lazily-rendered item list with per-item checkboxes. Selection toolbar supports
  select-range (tap a start and end item), select all via "Download entire playlist",
  and "Cancel selection". Download actions report what they would queue rather than
  starting anything — `DownloadEngine` still has no implementation.
- Unit tests: playlist vs single-video detection, playlist field mapping, item order,
  empty playlist, and mixed available/unavailable items (both a `null` source entry and
  a private/deleted-by-title entry); `HomeViewModel` selection tests (toggle, range,
  cancel, download-selected/-entire-playlist messaging).
- Fix: `core:extractor-ytdlp`'s own `AndroidManifest.xml` now declares
  `INTERNET` — its standalone instrumented-test APK is a separate process from `:app`
  and doesn't inherit `:app`'s manifest permissions, which was silently breaking
  on-device network calls made only from that test APK.

Still not implemented: actual downloading (single or playlist), FFmpeg/media
processing, and torrent functionality.

### Added — yt-dlp extractor engine

- `YtDlpExtractorEngine`, the first real implementation of `ExtractorEngine`, backed by
  yt-dlp running inside an embedded Python interpreter via
  [Chaquopy](https://chaquo.com/chaquopy/) (MIT-licensed as of v12.0.1) rather than the
  more common GPLv3-licensed `youtubedl-android` wrapper — this keeps MediaVault fully
  MIT/permissive. See `core/extractor-ytdlp/`.
- New `core:extractor-ytdlp` Gradle module: the Python bridge script
  (`src/main/python/mediavault_ytdlp.py`), JSON models + mapper for yt-dlp's info-dict,
  and a best-effort mapper from yt-dlp/Python errors to `AppError`.
- `ExtractorEngine.analyze` now takes a `taskId` so an in-flight analysis can be
  cancelled via the existing `cancel(taskId)`; `MediaAnalysisResult` gained a
  `webpageUrl` field. Both are incremental, backwards-compatible-in-spirit extensions
  of the interface added during the foundation stage.
- Home screen: real URL input, Analyze button, loading/cancel state, error state, and a
  result preview (thumbnail via Coil, title, source, duration, format list) wired to a
  new `HomeViewModel`.
- Unit tests for the JSON mapper (fixture-based), the error mapper, the formatting
  helpers, and `HomeViewModel` (using a fake `ExtractorEngine`); an instrumented test
  that analyzes a real public test video end-to-end on-device.
- AGP pinned to 9.2.1 (down from 9.3.2) for Chaquopy compatibility, which documents
  support up to 9.2.x.

Still not implemented: actual downloading, FFmpeg/media processing, and torrent
functionality — this stage is analysis-only, per plan.

### Added — Project foundation

- Initial multi-module Gradle project (`app`, `core:model`, `core:common`,
  `core:domain`, `core:database`) using Kotlin, Jetpack Compose, and Material 3.
- Minimal black/AMOLED Compose theme.
- Bottom-navigation skeleton with placeholder screens: Home, Downloads, Library,
  Player, Settings.
- Room database foundation (`MediaVaultDatabase`) with `download_tasks` and
  `media_items` tables.
- Core architecture interfaces establishing the abstraction boundaries the rest of the
  app will be built against: `ExtractorEngine`, `DownloadEngine`, `TorrentEngine`,
  `NetworkPolicyManager`, `UpdateManager`, `PlayerEngine`.
- Hilt wired in for dependency injection, with the Room database as the first provided
  dependency.
- Repository documentation: README, LICENSE, PRIVACY, TERMS, CONTRIBUTING,
  THIRD-PARTY-NOTICES.

No extraction, downloading, media processing, or torrent functionality is implemented
yet — this stage is the architectural foundation only.
