# Changelog

All notable changes to this project are documented here. This project has not yet made
a tagged release; entries below track development stages instead of version numbers.

## [Unreleased]

### Added — FFmpeg Merge Support

- Video-only formats (the common case for high-quality streams) are no longer shown
  disabled with "Requires merging — not available yet." A new `MediaProcessor`
  abstraction, backed by a maintained FFmpegKit fork (LGPL v3), remuxes a
  separately-downloaded video-only and audio-only stream into one file — always a
  stream-copy (`-c copy`), never a re-encode, so there's no quality loss and no
  transcode time/battery cost.
- The single-item format list now offers one selectable option per resolution *and*
  per available audio language, never just "the best" language — no track is silently
  dropped. A video-only format with genuinely no audio track anywhere is still shown,
  with its real resolution and size, but marked unavailable rather than hidden.
- Output container is chosen automatically and never guessed: MP4 video pairs to MP4,
  WEBM to WEBM, any other combination falls back to MKV — always a safe remux target,
  never a transcode.
- Downloads screen gained a distinct "Merging" status for the brief remux step after
  both streams finish downloading; a split video+audio download can be cancelled but
  not paused mid-merge (a stream-copy remux is normally seconds long), and a task
  interrupted mid-merge by an app kill safely restarts both downloads and re-merges
  rather than being left stuck.
- Room database bumped to schema version 4 with another real, additive migration (the
  second stream's format id and cache path) — every pre-existing download row is
  unaffected, exactly as before this feature existed.
- **Verified live on a physical device (Pixel 7a)**: analyzed a real split-stream
  YouTube source (Blender Foundation's "Sintel" trailer), downloaded the 1080p-tier
  paired video+audio option, watched it complete, confirmed via logcat that FFmpegKit
  actually ran a merge session, confirmed the finished file's MP4 box structure contains
  both a video and an audio track (parsed directly, no ffprobe needed), and played it
  back successfully in the internal Media3 player with a real `AudioTrack` audio session
  — see `PROJECT_MASTER.md` §34/§37 for the full walkthrough.

### Added — Private Library + In-App Playback Foundation

- Completed downloads now become real, playable Library items instead of just files on
  disk. New downloads are saved to MediaVault's own app-private storage by default — the
  folder-picker step that used to appear before every download is gone. Private storage
  is never indexed by the system Gallery/Photos app and is removed automatically if
  MediaVault is uninstalled.
- New Library screen: search, sort (Recent/Name/Size), thumbnail/duration/resolution/
  size/type per item, and an honest empty state. A three-dot menu on every item offers
  Play, Share, Export to device, Rename, Delete, and Details. Deleting an item always
  removes its database record, even if the file is somehow already gone — never leaves a
  stale entry behind.
- A real in-app player, built on Media3: play/pause, seek, playback speed, fullscreen,
  and audio-track/subtitle-track selection when a file actually has more than one track
  (language labels are only ever shown when the source provided them — never guessed).
  Tapping a Library item opens the player; playback position is saved continuously and
  resumes automatically next time, including from the bottom-tab Player entry with no
  item explicitly chosen (it resumes whatever was played most recently).
- Downloads completed by the previous SAF-based milestone keep working exactly as
  before — they still show up in the Library, still play, and can still be shared or
  exported; only renaming an old SAF-based item isn't supported (it reports a clear
  message rather than failing silently), since that would need a permission grant this
  app no longer requests by default.
- **Bug found and fixed during this stage's own device testing:** the player's fullscreen
  mode let the video surface claim all available space, pushing its own play/pause/seek/
  speed controls completely off-screen and unreachable. Fixed so the video fills the
  space above the controls instead of consuming it.
- Room database bumped to schema version 3 with another real, additive migration
  (duration/resolution/thumbnail metadata columns) — verified live that every
  pre-existing download and Library item survived the update untouched.
- FFmpeg was **not** added this stage, consistent with earlier decisions — only formats
  that need no video+audio merge are downloadable at all, so the player never needed to
  handle a merged file.
- Verified live on a physical device (Pixel 7a) with a short (19-second) test video:
  downloaded it straight to private storage, confirmed it appears in the Library with
  correct metadata, played it to completion in-app, seeked partway through, force-stopped
  and reopened the app and confirmed playback resumed from the saved position, renamed
  and deleted it via the three-dot menu, and confirmed both the Library entry and the
  underlying file were fully removed (app storage usage dropped back to a negligible
  11 KB afterward).
- New unit tests: `FileNamingTest`, `LibraryQueryTest`, `LibraryRepositoryTest` (real
  filesystem rename against a temp directory), `PlayerViewModelTest`
  (resume-from-saved-position, immediate persistence on pause/seek, missing-file
  handling), and new metadata-mapping tests in `MediaVaultDownloadEngineTest`.

### Added — Playlist Download Engine

- Playlist downloading is now real: selecting items from a detected playlist and tapping
  "Download entire playlist"/"Download selected" queues genuine, independent downloads
  instead of only reporting what would be queued. Each selected item becomes its own
  `DownloadTask`, in original playlist order, tracked individually (own progress,
  pause/resume/cancel/retry) and as a group (playlist title/thumbnail, completed/failed/
  queued/remaining counts, current item).
- One quality choice applies across the whole playlist via a new portable
  `QualityDescriptor` (resolution/container/has-video/has-audio), matched against each
  item's own independently-resolved formats — never a raw shared `formatId`. An item with
  no matching format fails clearly rather than silently downloading a different quality.
- "Skip already downloaded" (on by default) checks each item's stable source id against
  completed downloads before queueing, so re-running a playlist download never silently
  duplicates a file.
- Downloads screen gained a "Playlists" section: thumbnail, title, live counts, current
  item, overall progress bar, Pause all/Cancel all/Retry failed, and a per-item status row
  — sitting above the existing Active/Queued/Failed/Completed sections, which now show
  only non-playlist downloads.
- Process-death recovery now also resumes playlist items that were still resolving their
  format when the app died, instead of leaving them stuck — verified live by force-
  stopping mid-resolution and confirming the item reached a normal terminal state and
  retried successfully on relaunch.
- Filenames get a zero-padded playlist-index prefix (`002 - Title.ext`) for both ordering
  and collision avoidance; storage otherwise reuses the existing single-destination SAF
  folder flow unchanged.
- Room database bumped to schema version 2 with a real, additive migration (the project's
  first — earlier stages could evolve the schema in place since no real device data
  existed yet). Verified live that pre-existing downloads/media survive the update.
- FFmpeg was **not** added this stage, matching the earlier single-item decision: only
  formats that need no video+audio merge are selectable, so no playlist item ever required
  one.
- **Real external content restriction found during testing, not a MediaVault bug:** one
  test playlist had every item blocked at the source (confirmed independently via the
  yt-dlp CLI: a copyright claim). The per-item failure path surfaced this cleanly without
  crashing or stalling the rest of the group.
- Verified live on a physical device (Pixel 7a) with a small playlist: analyze → select
  several items → choose a format → queue → download to completion with correctly-named
  files, real-time playlist progress, a genuinely-unavailable item failing without
  stopping the rest of the group, and process-death recovery mid-format-resolution.
- New unit tests: `QualityDescriptorTest`, `PlaylistProgressTest`,
  `MediaVaultDownloadEngineTest` (playlist task creation/ordering/duplicate-detection,
  retry decisions, process-recovery grouping), plus new playlist-flow coverage in
  `HomeViewModelTest`.

### Added — Supported Sources catalog

- New Supported Sources screen and detail screen (`app/ui/screens/sources/`), reachable
  from Home's Popular Sources section ("See all" / tapping a chip) and two new nav routes
  — search field, category filter chips, a live "N of M sources" count, an A→Z indexed
  list with sticky letter headers, and a favicon-or-generated-initials icon per source.
- The catalog is generated from the actual installed yt-dlp extractor registry by a new
  offline script, `core/extractor-ytdlp/scripts/generate_source_catalog.py` — not
  hand-typed. It groups yt-dlp's ~1,740 extractor classes into **1,027 real services**
  (deduplicating variants like `youtube`/`youtube:tab`/`youtube:search` into one `YouTube`
  entry while keeping every underlying extractor id for future analysis routing),
  substantially more than the 5 names previously shown on Home. Output is a committed
  JSON asset, not fetched from a server and not regenerated on app launch — see
  PROJECT_MASTER.md §37 for the full generation/regeneration approach.
- New `Source`/`SourceCategory` domain model (`core:model`) and
  `SourceCatalogRepository`/`SourceCatalogIndex` (`core:domain`) — the UI depends only on
  these, never on yt-dlp internals. Search/category-filter/A→Z-grouping is a plain
  precomputed-lowercase-blob + linear scan, fast enough for ~1,000 records without
  needing SQLite FTS.
- Favicons are fetched via a public favicon-lookup URL and cached locally by Coil's
  normal disk cache (no bundled image assets); a source with no known domain, or whose
  favicon fails to load, falls back to a generated initials avatar so the catalog never
  looks broken.
- The catalog and detail screen are explicit that support isn't guaranteed forever:
  wording reads "Supported by current extraction engine (yt-dlp `<version>`)" rather than
  claiming a permanent count or that every listed service currently works.
- **Bug found and fixed during this stage's own QA, before device testing:** an initial
  domain-grouping heuristic (`domain.split(".")[0]`) mis-merged unrelated services that
  share a generic subdomain label — e.g. NRK (`tv.nrk.no`), JTBC (`tv.jtbc.co.kr`), and
  Sohu (`tv.sohu.com`) were all collapsing into one bogus "tv" catalog entry. Fixed with
  public-suffix-aware domain-label parsing.
- **Bug found and fixed during on-device testing:** the bottom navigation bar's tab-switch
  helper could land on the wrong screen when returning to a tab (e.g. Home) from the new
  drill-in Sources/detail routes, because its `popUpTo`/`saveState`/`restoreState` pattern
  — designed for switching between sibling tabs — got confused by the extra routes above
  a tab on the back stack. Fixed by preferring a direct `popBackStack` to an already-open
  tab before falling back to the save/restore pattern for a tab never visited yet.
- Verified live on a physical device (Pixel 7a): searched YouTube, Reddit, TikTok, Vimeo,
  and Facebook (all found, including alias matches like `youtu.be`), tested category
  filtering, and navigated into source detail and back to Home. No crashes.

### Added — Real DownloadEngine

- `DownloadEngine` now has a real implementation, `MediaVaultDownloadEngine`
  (`app/download/`): a Room-backed queue (one active transfer at a time, survives
  process death), real progress (bytes/total/speed/ETA, polled from yt-dlp), pause/resume
  (cooperative stop via yt-dlp's `progress_hooks`, protocol-aware — non-resumable formats
  get an honest clean restart instead of a corrupted resume), cancel/retry, a foreground
  service for background transfers, and SAF-based storage (destination folder chosen once
  via `ACTION_OPEN_DOCUMENT_TREE`, persisted, free-space checked before starting).
- `YtDlpExtractorEngine.download()` is implemented (previously "not implemented"):
  delegates the actual transfer to yt-dlp itself via a new Python bridge function.
- `AndroidNetworkPolicyManager` is implemented: Wi-Fi/mobile detection, a per-download
  limit and daily mobile-data budget (both DataStore-backed, with real rollover), and
  Allow/Warn/QueueForWifi/Block decisions the download engine actually acts on. Real
  transferred bytes are recorded against the daily budget on completion — no fabricated
  usage numbers.
- Home screen: the format list is now genuinely selectable (single choice, showing
  resolution/fps/container/codec/size/audio info), with a Download button and SAF folder
  picker wired to the real engine.
- Downloads screen: replaced the placeholder with real Active/Queued/Failed/Completed
  sections, progress bars, and Pause/Resume/Cancel/Retry/Open actions.
- **Deliberately not added: FFmpeg.** Format selection is restricted to muxed
  (video+audio) or audio-only formats; video-only formats are shown but disabled with a
  "Requires merging — not available yet" note. See PROJECT_MASTER.md §37 for the full
  decision record.
- **Bug found and fixed via real-device testing:** `YtDlpResultMapper` was filtering out
  every audio-only format before it reached the UI (a leftover from before downloading
  existed). Combined with the FFmpeg restriction above, a typical modern YouTube video
  — all video-only + audio-only DASH streams, no muxed format — had zero selectable
  formats. Fixed to include audio-only formats; storyboard/thumbnail-scrubbing entries
  are still excluded.
- Verified live on a physical device (Pixel 7a): analyzed a public-domain test video,
  selected an audio-only format, picked a SAF folder, downloaded to completion with a
  correctly-sized file on disk, and confirmed the completed state survives a full
  process restart. No crashes.

### Changed — UI design system (light/blue)

- Replaced the original minimal black/AMOLED theme with an approved light design
  system: white/light-gray surfaces, a blue primary accent, clean typography, generous
  spacing, and subtle borders/elevation instead of glow/gradients. See
  PROJECT_MASTER.md §37 for the full decision record.
- New app icon: a blue "M" mark on a white rounded-square card, used both as the
  launcher icon and as an in-app logo (`MediaVaultLogo`).
- New shared components (`ui/components/`): `MediaVaultLogo`, `MediaVaultTopBar`,
  `MediaVaultCard`, `SectionLabel`, `EmptyStateCard` — used consistently across all
  five screens instead of each screen styling itself independently.
- Home screen gained: a time-of-day greeting, a Popular Sources chip row (static —
  no source-index screen exists yet), a Quick Actions grid linking to the real
  Downloads/Library/Player/Settings screens (no fake counts), a Recent Activity empty
  state (no download-history persistence exists yet), and a device status row showing
  **real** free storage space and network type (`DeviceStatusProvider`, backed by
  `StatFs`/`ConnectivityManager` — not a `NetworkPolicyManager` implementation).
- Downloads/Library/Player/Settings are now consistently styled `EmptyStateCard`
  placeholders (icon + honest "not implemented yet" copy) instead of bare centered text.
- All existing functionality — URL analysis, playlist analysis/selection, cancellation —
  is unchanged; only presentation was touched. Verified live on a physical device
  (navigation, Home, single-video analysis) with no regressions or crashes.
- **Found while testing, not caused by this change:** yt-dlp's `youtube:tab` extractor
  (used for playlist URLs) is currently returning `HTTP Error 400` from YouTube for
  every playlist tried, including one that worked in the prior session — looks like an
  upstream YouTube API change; single-video analysis is unaffected, and the playlist
  mapping/UI code is unchanged and still covered by 15 passing unit tests.

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
