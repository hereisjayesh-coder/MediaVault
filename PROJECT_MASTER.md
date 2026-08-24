# MediaVault Project Master Specification

## 1. Project Identity

**Project Name:** MediaVault
**Planned GitHub Repository:** `mediavault-android`
**Project Type:** Open-source Android application
**Distribution:** GitHub Releases / direct APK distribution
**Primary UI:** Native Android, Kotlin, Jetpack Compose
**Design:** Light, minimal, blue primary accent, clean typography, generous spacing,
subtle borders/elevation only (no neon/glow/gradients), no advertisements. Approved
2026-08-24 (see §37 Decision Log) — replaces the earlier AMOLED-dark design named here.

---

## 2. Product Vision

MediaVault is a local-first Android media application combining:

* Multi-source media downloading
* Intelligent download/network management
* Local media library
* Built-in video/audio player
* Multi-audio and subtitle selection
* Torrent and magnet-link downloading
* Download history/statistics
* Supported-source browser
* GitHub-based release/update information

The goal is a robust, maintainable application designed for long-term use rather than a simple one-purpose downloader.

---

## 3. Existing Development Environment

Do NOT provide generic installation instructions.

Already available:

* Android Studio
* Android SDK
* Android build tools/platform tools
* JDK/JBR
* Gradle/Gradle Wrapper
* Kotlin/Android environment
* Git
* VS Code
* Claude Code / coding agent
* Codex
* Physical Android phone with USB debugging

The current project is created in a new directory on the user's SSD.

First inspect the actual environment and project before making assumptions.

---

## 4. Core Technology Decisions

### Android

* Kotlin
* Jetpack Compose
* Material 3
* Coroutines
* Flow
* Room
* Media3
* Android platform APIs
* Hilt only where useful
* Clean Architecture / MVVM

### Media Extraction

Use a modular `ExtractorEngine` abstraction.

Initial backend:

* yt-dlp

The UI and application core must NOT depend directly on yt-dlp internals.

Architecture:

`Application → ExtractorEngine → yt-dlp backend`

Future extractors/backends must be replaceable without rewriting the UI.

### Media Processing

Use a dedicated media-processing abstraction.

Initial planned backend:

* FFmpeg

Do not tightly couple the rest of the application to FFmpeg.

### Torrent

Use a dedicated `TorrentEngine`.

Planned backend:

* libtorrent

Required capabilities:

* Magnet links
* `.torrent` files
* Metadata retrieval
* File selection
* Download queue
* Progress
* Pause/resume
* Error handling

Do not implement piracy-specific discovery or torrent indexing.

---

## 5. Main Application Areas

Primary navigation:

1. Home
2. Downloads
3. Library
4. Player
5. Settings

Additional flows/screens may exist when functionally necessary.

---

## 6. Home

Home should provide:

* URL input
* Analyze button
* Supported Sources shortcut
* Recent downloads
* Download/activity summary
* Relevant storage/network information

The interface should remain minimal.

---

## 7. URL Analysis

When a supported URL is analyzed:

Display where available:

* Thumbnail
* Title
* Duration
* Source
* Available formats
* Resolution
* FPS
* Codec
* Container
* Estimated file size
* Video/audio streams
* Audio languages
* Subtitle tracks

Example:

`1080p • MP4 • 844 MB`

The user selects the desired format before downloading unless automatic-download rules intentionally select one.

---

## 8. Intelligent Mobile-Data System

This is a core product feature.

Users can configure:

### Per-download mobile-data limit

Example:

`500 MB`

### Daily mobile-data budget

Example:

`2 GB/day`

The application must track actual transferred bytes.

Decision engine:

`Current network + download size + remaining daily budget + user settings`

Possible outcomes:

* Allow automatically
* Warn user
* Queue for Wi-Fi
* Block

Example:

Daily budget: 2 GB
Remaining: 600 MB
Video: 500 MB

→ Automatically download if the user's rules permit.

Video: 700 MB

→ Warn / recommend Wi-Fi.

The app must never silently violate configured limits.

---

## 9. Smart Quality Recommendation

When mobile data is limited, compare available formats against the remaining budget.

Example:

* 1080p = 844 MB
* 720p = 412 MB
* 480p = 188 MB
* Remaining budget = 300 MB

Recommendation:

`480p • 188 MB`

The user remains in control.

---

## 10. Download Manager

Required:

* Queue
* Progress
* Pause
* Resume
* Cancel
* Retry
* Failure states
* Background downloading
* Storage checks
* Duplicate detection
* Download history
* Actual transferred-byte tracking

States should include:

* Queued
* Analyzing
* Downloading
* Processing
* Merging
* Completed
* Paused
* Cancelled
* Failed

---

## 11. Storage

Use Android Storage Access Framework.

Users choose their download location.

Do not hard-code a single storage path.

Before downloading, check available storage.

If insufficient:

Show required size and available size before starting.

---

## 12. Local Media Library

Library should contain both:

* Downloaded media
* User-imported/local media

Capabilities:

* Recently downloaded
* Recently watched
* Favorites
* Video/audio categories
* Search
* Sort
* Filter
* Duplicate handling
* File information
* Rename
* Delete
* Share
* Open/play

---

## 13. Local Media Scanner

Users can select folders and scan local media.

Detect:

* Video
* Audio
* Resolution
* Duration
* Codec
* Container
* Audio tracks
* Subtitle tracks
* File size

No media needs to be uploaded to a server.

---

## 14. Built-in Media Player

Use Android Media3.

Required capabilities:

* Play/pause
* Seek
* Fullscreen
* Playback speed
* Resume playback
* Playback history
* Picture-in-picture
* Audio-track selection
* Subtitle selection

Multi-audio example:

* Hindi
* English
* Japanese

The player must allow switching between available audio tracks.

If language metadata is unavailable, show generic track labels instead of guessing.

---

## 15. Subtitle Support

Detect available embedded subtitle tracks.

Support selecting:

* English
* Hindi
* Japanese
* Other available languages

Future capability may include external subtitle files.

---

## 16. Supported Sources

Create a searchable source index.

Features:

* Alphabetical sorting
* Search
* Category filtering
* Source icons where practical
* Supported-source count
* Source-specific entry flow

Do not permanently advertise an exact website count.

Use wording such as:

`1,700+ supported extractors/services`

because third-party websites can change and extractor support can break.

The source index must reflect the actual engine where practical.

---

## 17. Source Search

User should be able to search:

`YouTube`

`Vimeo`

`Reddit`

etc.

Tapping an item opens the appropriate analysis/download flow.

For a roughly 1,700-item dataset, use a sensible indexed search approach. Do not add complex algorithms merely for appearance.

---

## 18. Download Statistics

Track:

* Total downloads
* Completed
* Failed
* Total bytes downloaded
* Mobile-data usage
* Wi-Fi usage
* Most-used quality
* Most-used source
* Storage usage

Statistics remain local unless explicitly changed later.

---

## 19. Update Architecture

Do NOT design the application to silently replace executable code inside itself.

Separate:

* Application version
* Extraction engine version
* Media-processing version

The application may display:

`Current version: X`

`Latest GitHub release: Y`

Provide a clickable GitHub release/repository link.

For GitHub-distributed APK releases, normal release replacement/install is preferred.

User data must survive normal upgrades.

Provide future support for:

* Settings export/import
* Database backup/restore
* Library metadata preservation

---

## 20. Extraction Engine Future-Proofing

Create:

`ExtractorEngine`

with a stable application-level interface.

Example conceptual operations:

* analyze URL
* obtain metadata
* obtain formats
* obtain tracks
* download
* cancel
* report progress

Current implementation:

`YtDlpExtractorEngine`

Future implementation can change independently.

The UI must never call yt-dlp directly.

---

## 21. Network Abstraction

Create:

`NetworkPolicyManager`

This owns decisions concerning:

* Wi-Fi
* mobile data
* metered networks
* daily budget
* per-download threshold
* remaining budget
* smart quality recommendation

UI only consumes policy results.

---

## 22. Download Abstraction

Create:

`DownloadEngine`

Normal media downloads and torrent downloads must be separately implementable.

Possible future implementations:

* HTTP media downloader
* yt-dlp downloader
* Torrent downloader

Do not tightly couple them.

---

## 23. Player Abstraction

Create a player-facing abstraction so the application core is not tightly coupled to Media3 internals.

Media3 is the initial implementation.

---

## 24. Database

Use Room.

Design extensible entities for:

* Download
* MediaItem
* MediaTrack
* SubtitleTrack
* DownloadFormat
* DownloadTask
* NetworkUsage
* PlaybackState
* LibraryFolder
* AppSettings
* EngineVersion

Avoid unnecessary database complexity.

---

## 25. Privacy

Default philosophy:

* Local-first
* No advertising
* No unnecessary analytics
* No unnecessary accounts
* No unnecessary server infrastructure
* User-controlled storage
* Clear network behavior

Document actual data collection honestly.

Never claim "zero data" unless technically verified.

---

## 26. Open Source

Repository must contain:

* `README.md`
* `LICENSE`
* `PRIVACY.md`
* `TERMS.md`
* `CONTRIBUTING.md`
* `CHANGELOG.md`
* `THIRD-PARTY-NOTICES.md`

Properly credit all upstream projects.

Important upstream projects include:

* yt-dlp
* FFmpeg
* Media3
* libtorrent
* other dependencies actually used

Never claim ownership of upstream code.

Explain architecture and technology publicly in GitHub documentation.

---

## 27. User Responsibility

Documentation must clearly state that users are responsible for ensuring they have permission to download, store, reproduce, or use content.

Do not market the application as a piracy tool.

Do not implement DRM circumvention.

Do not remove copyright notices or upstream licenses.

Legal text should be factual and reviewed before public distribution.

---

## 28. Git Strategy

GitHub is part of the development workflow, not just a final upload.

Every meaningful implementation stage must be committed.

Use small descriptive commits.

Examples:

`chore: initialize android project`

`feat: establish application architecture`

`feat: add database foundation`

`feat: add extractor abstraction`

`feat: add download manager`

`feat: add network policy`

`feat: add media library`

`feat: add player`

`feat: add torrent engine`

`docs: update project architecture`

Before every change:

* inspect `git status`
* inspect relevant files
* preserve existing work

After meaningful changes:

* build
* test
* inspect diff
* commit

Never create giant undocumented commits for major features.

---

## 29. Development Rule

The coding agent must behave as an implementation engineer.

The product manager/technical lead decides:

* Architecture
* Features
* UX
* Navigation
* Data models
* Technical tradeoffs
* Priority
* What to keep/remove/change

The coding agent implements the decision.

The coding agent must not introduce architecture merely because it personally prefers it.

If an implementation detail is technically dangerous, unnecessary, or incompatible, report it before making a destructive change.

---

## 30. Existing Environment Rule

Never tell the user to:

* reinstall Android Studio
* reinstall Android SDK
* reinstall JDK
* reinstall Git
* recreate the development environment

unless an actual verified problem exists.

Always inspect first.

---

## 31. Full-V1 Requirement

This is NOT an intentionally tiny MVP.

The first major application should attempt to include the planned platform capabilities from the beginning:

* Downloader
* Smart mobile-data management
* Download queue
* Local library
* Built-in player
* Multi-audio
* Subtitles
* Local media scanning
* Statistics
* Torrent/magnet functionality
* Supported-source index/search
* GitHub update information
* Open-source documentation
* Privacy/legal documentation
* Robust architecture and testing

Features may be implemented in stages, but the architecture must support the complete product.

---

## 32. Quality Standard

Do not optimize for speed of typing.

Optimize for:

* Correctness
* Maintainability
* Testability
* Clear architecture
* Real error handling
* Good Android UX
* Long-term extensibility
* Reproducible builds
* Documented decisions

Do not create fake functionality merely to make a screen appear complete.

---

## 33. Decision Log Rule

Whenever a major architecture/product decision changes:

1. Update this file.
2. Explain the decision briefly.
3. Record why the previous approach was changed.
4. Commit the update.

This file is the permanent project memory.

---

## 34. Current Project State

_Last updated: 2026-08-25, after the Private Library + In-App Playback Foundation stage._

* **Completed downloads are now managed MediaVault Library items, playable inside the
  app.** New downloads land in app-private storage by default (no SAF folder picker in
  the download flow any more), get indexed automatically as `MediaItemEntity` rows with
  real metadata, and can be played, searched, sorted, renamed, deleted, exported, or
  shared from a real Library screen. FFmpeg was **not** added this stage — see §37.
  - **Private storage**: new `MediaVaultStorage` (`app/storage/`) exposes
    `context.getExternalFilesDir(null)/media` — app-specific external storage, removed
    automatically on uninstall, never indexed by MediaStore/Gallery, reachable with plain
    `File` I/O and no storage permission or SAF grant. `MediaVaultDownloadEngine.copyToDestination()`
    now always copies the finished cache file here (collision-safe via
    `nextAvailableFileName`) instead of the old SAF `DocumentsContract` path — see §37's
    private-storage decision. `DownloadRequest`/`PlaylistDownloadRequest.destinationTreeUri`
    were removed (the `DownloadTaskEntity` column stays, now unused, for schema
    stability); `HomeViewModel`'s SAF-folder-picker gating (`awaitingDestinationPick`,
    `onDestinationFolderPicked`, `DownloadDestinationStore`) was deleted outright as dead
    code rather than left half-wired. **Verified live**: `adb shell run-as` on a freshly
    completed download showed the file under the app's private `files/media/` directory
    with `drwxrws---` permissions (denied to a plain, non-`run-as` shell), and a MediaStore
    `content://media/external/audio/media` query for the file's name returned nothing —
    genuinely not exposed to the Gallery.
  - **Preserving pre-existing files**: downloads completed by the prior (SAF-based)
    milestone still have valid `content://` `MediaItemEntity` rows — `LibraryRepository`
    handles both `file://` (new, private) and `content://` (legacy) schemes for
    exists-checks, Play (Media3 accepts `content://` URIs directly), Share, and Export;
    only Rename is `file://`-only, since renaming a SAF document needs a tree-permission
    grant this app no longer requests, and reports a clear "can't rename" instead of
    silently failing. **Verified live**: all 5 pre-migration Library rows loaded, showed
    correct existing-file state, and their three-dot menus worked (Details opened
    correctly, honestly omitting duration/resolution — that metadata didn't exist before
    this migration).
  - **Metadata plumbing**: `DownloadTaskEntity` gained `durationSeconds`/`resolutionLabel`
    (populated at enqueue for single items, at playlist-item format-resolution for
    playlist items); `MediaItemEntity` gained `resolutionLabel`/`thumbnailUrl` (carried
    from the source, not locally generated — reuses the existing Coil pipeline, avoids
    `MediaMetadataRetriever` frame-extraction complexity this milestone didn't need).
  - **Library screen** (`ui/screens/library/`): search (case-insensitive title substring)
    and sort (Recent/Name/Size) are a pure `List<MediaItemEntity>.filterAndSort()` function
    in `app/library/LibraryQuery.kt` — the same "linear scan over a small in-memory list"
    approach already used by `SourceCatalogIndex`, not dynamic SQL. An honest empty state
    distinguishes "library is genuinely empty" from "no search matches." Each row shows
    thumbnail/title/duration/resolution/size/media type, a "File missing" badge when
    `LibraryRepository.fileExists()` is false (disabling Play/Share/Export/Rename, keeping
    Delete enabled so a stale record can always be cleaned up), and a three-dot menu
    (Play/Share/Export to device/Rename/Delete/Details).
  - **Three-dot menu**: Share/Export build a `content://` URI via a new `FileProvider`
    (manifest + `res/xml/file_paths.xml`, scoped only to the private `media/` folder) for
    `file://` items, or reuse the existing `content://` URI directly for legacy items.
    Export uses `ActivityResultContracts.CreateDocument` (no persisted destination). Delete
    always removes the DB row, even if the file is already gone — never leaves an orphaned
    record — and best-effort deletes the file (`File.delete()` or
    `DocumentsContract.deleteDocument()` depending on scheme). Rename sanitizes the new
    title (reused, shared `sanitizeFileName`/`nextAvailableFileName` — promoted out of the
    download engine into `app/util/FileNaming.kt` — the same collision-safety and
    path-traversal protection as new downloads) and does a real `File.renameTo()`.
    **Verified live**: renamed a completed test download, confirmed the new title
    appeared immediately; deleted it and confirmed both the Library row disappeared and
    `adb shell du -sh` on the app's external data directory dropped to 11 KB (nothing
    left behind).
  - **Media3 player** (`app/player/Media3PlayerEngine.kt`): the first real implementation
    of the pre-existing `PlayerEngine` abstraction (defined, unused, in an earlier stage).
    Play/pause/seek/speed/audio-track/subtitle-track selection all go through the
    interface; the one deliberate exception is `Media3PlayerEngine.rawPlayer`, an escape
    hatch `PlayerViewModel.attachVideoSurface()` uses to bind Media3's `PlayerView` to a
    real `Player` — no player-rendering library can avoid exposing *something*
    implementation-specific for video output, so this is documented rather than hidden.
    Audio/subtitle track labels are never guessed: `label ?: languageCode ?: "Track N"`,
    matching `MediaTrackInfo`'s existing "never invent a language" contract. Playback
    position is persisted immediately on pause/seek and every 3 seconds while playing (not
    only at teardown) via `LibraryRepository.updatePlaybackPosition` →
    `MediaItemEntity.lastPlaybackPositionMs` (a field that already existed, unused, from
    an earlier stage). A `LastPlayedStore` (DataStore) lets the bottom-tab "Player" entry
    (reached with no specific item id, unlike a Library drill-in) resume whatever was
    opened most recently. **Verified live**: played a 19-second test clip to completion,
    seeked to 0:09, force-stopped the app, relaunched, tapped the bottom-tab Player entry
    with no Library interaction — it resumed the same item from 0:10 and kept playing.
  - **A real bug found and fixed during this stage's own device testing**: fullscreen
    initially gave the video surface `Modifier.fillMaxSize()` inside the controls'
    `Column`, which let it claim every remaining pixel and push the play/pause/seek/speed/
    track/exit-fullscreen row entirely off-screen — fullscreen played correctly but its
    own controls became unreachable. Fixed by using `Modifier.weight(1f)` instead, so the
    video fills available space above the controls rather than swallowing them.
  - **Known limitation**: no video-capable test source was available this stage — every
    format offered on both a legacy playlist and a fresh test video was audio-only (the
    project's existing muxed-or-audio-only restriction plus the reality that
    contemporary/older YouTube videos alike now serve exclusively split DASH streams — see
    §37's 2026-08-24 FFmpeg-scoping decision and the mapper-bug note below it). Multi-track
    audio/subtitle selection UI was code-reviewed and exercised with 0-and-1-track cases
    live, but a real multi-track (audio or subtitle) source was never available to verify
    the actual track-switching call end-to-end on-device.
  - Unit tests added: `FileNamingTest` (8, sanitize/collision including a literal
    path-traversal input), `LibraryQueryTest` (7, search/sort/missing-file),
    `LibraryRepositoryTest` (4, real `File.renameTo()` against a temp directory —
    Context-free, so genuinely exercises the OS rename rather than a fake),
    `PlayerViewModelTest` (8, resume-from-saved-position, pause/seek persist immediately,
    missing-file handling, last-played fallback), plus `buildMediaItemEntity` mapping
    tests added to `MediaVaultDownloadEngineTest`. `./gradlew build` and the full unit
    test suite (97 tests across `core:domain` and `:app`, 0 failures) both pass. As with
    `MediaVaultDownloadEngine` before it, the Android/Context-touching parts of
    `AndroidLibraryRepository` (the `content://` fallback path, `FileProvider`, Room
    wiring) and `Media3PlayerEngine` are verified via real-device testing and code review
    rather than JVM unit tests — this project still has no Robolectric/Mockito.
* **Playlist downloading is real — a detected playlist queues actual, independent
  downloads, not just a report.** Builds directly on `MediaVaultDownloadEngine` and the
  existing `ExtractorEngine`/Room/SAF/`NetworkPolicyManager` stack; no new engine, no
  site-specific logic.
  - **One `DownloadTaskEntity` per selected item**, in original playlist order, carrying
    `playlistId`/`sourceMediaId`/`playlistItemIndex` plus denormalized `playlistTitle`/
    `playlistThumbnailUrl` and the chosen `qualityResolutionLabel`/`qualityContainer`/
    `qualityHasVideo`/`qualityHasAudio` on every row (`DownloadTaskDao.getByPlaylistId`).
    Order is preserved purely by strictly-increasing `createdAtEpochMs`, which the
    existing queue already picks oldest-`QUEUED`-first — no new ordering concept needed.
  - **Selection**: entire playlist, individual items, range-select, clear, and a live
    "Download selected (N)" count were already in `HomeScreen`/`HomeViewModel` from the
    prior stage (previously report-only) — now wired to actually queue.
  - **One format choice per playlist, no site-specific logic**: new `QualityDescriptor`
    (`core:domain/download/`) — resolution label + container + has-video/has-audio — is a
    portable fingerprint resolved once from a reference item's real formats, then matched
    against every other selected item's *own* independently-analyzed format list via
    `List<MediaFormat>.findMatching()`. Playlist items are lightweight/flat-extracted (see
    §34 prior stage), so each item's real format list is only known once resolved. No
    match found for a given item → that item is marked failed with a clear "not
    available" message; **never silently substituted** for a different quality.
  - **Sequential per-item format resolution**: `resolvePlaylistFormats()` walks ANALYZING
    tasks in playlist order, calling `ExtractorEngine.analyze()` once per item (avoids
    concurrent Chaquopy calls), re-reads each task's live status before acting (so a
    concurrent cancel/pause is respected), and kicks `processQueue()` after each resolved
    item so downloading and remaining resolution interleave rather than blocking.
  - **Duplicate protection**: "Skip already downloaded" toggle (default on) checks each
    item's `sourceMediaId` against completed downloads before enqueueing
    (`DownloadTaskDao.countBySourceMediaIdAndStatus`); matches are inserted as `CANCELLED`
    with an "Already downloaded — skipped" message rather than silently re-downloaded or
    silently dropped from the list.
  - **Group-level and per-item control**: existing `pause`/`cancel`/`retry` were
    refactored into public wrappers over private `pauseTask`/`cancelTask`/`retryTask`
    helpers, reused by new `pausePlaylist`/`cancelPlaylist`/`retryFailedInPlaylist`, which
    iterate `getByPlaylistId`. A failed item never blocks the rest of the group — each
    task's terminal state is independent, and `retryFailedInPlaylist` only touches FAILED/
    CANCELLED rows.
  - **Network policy is per-task, not bypassed for multi-select**: each playlist item goes
    through the same `AndroidNetworkPolicyManager` checks (mobile per-download limit,
    daily budget, Wi-Fi queueing, real transferred-bytes accounting) as a single download
    — queuing ten items queues ten individually-policed transfers, not one exempt batch.
  - **Storage**: reuses the existing SAF destination-folder flow; filenames get a
    zero-padded 3-digit playlist-item-index prefix (e.g. `002 - Title.ext`) for both
    ordering and collision avoidance, with the SAF provider's own auto-rename-on-collision
    as the final safety net (no separate playlist subfolder — the existing flat
    destination-folder design was preserved rather than introduced this stage).
  - **Downloads UI**: new `PlaylistProgress`/`List<DownloadProgress>.toPlaylistProgressGroups()`
    (`core:domain/download/`, pure and unit-tested) aggregate playlist tasks into
    completed/failed/skipped/queued/active counts and the current actively-downloading
    item's title. `DownloadsScreen` gained a "Playlists" section (thumbnail, title, counts
    line, "Now: <item>" line, overall progress bar, Pause all/Cancel all/Retry failed, and
    a per-item status row with one context-appropriate action each) above the existing
    Active/Queued/Failed/Completed sections, which now render only non-playlist tasks.
  - **Process-death recovery extended**: `recoverAfterProcessDeath()` (already reset
    DOWNLOADING/PROCESSING → PAUSED) now also finds every playlist with a still-ANALYZING
    task (`List<DownloadTaskEntity>.playlistIdsNeedingResolution()`, pure and unit-tested)
    and re-invokes `resolvePlaylistFormats()` for each — resolution resumes rather than
    leaving items stuck forever. **Verified live** (Pixel 7a): queued 2 playlist items,
    force-stopped the app ~1s after tapping Queue (mid ANALYZING), relaunched, and the
    Downloads screen showed the interrupted item correctly reach a terminal FAILED state
    (not stuck) with a working Retry that completed it on the next attempt, while the
    other selected item — already downloaded in an earlier test run — was correctly
    skipped as `Cancelled` rather than re-downloaded.
  - **Room migration**: `MediaVaultDatabase` version 1→2, with a real `Migration(1,2)`
    (`core/database/Migrations.kt`, six additive `ALTER TABLE ADD COLUMN` statements,
    registered via `.addMigrations()` in `DatabaseModule`) — the first real migration in
    this project, since the device now carries genuine prior downloads/media unlike
    earlier schema-only-evolved-at-v1 stages. Verified live: pre-existing download and
    media rows survived the app update untouched.
  - **Testability seam**: new `DownloadForegroundServiceStarter` interface (prod impl
    calls the real `DownloadForegroundService.start()`) removes the one remaining
    Context-touching call from `MediaVaultDownloadEngine`'s queue-creation paths, enabling
    JVM unit tests without Robolectric/Mockito (still absent from this project). Combined
    with pure top-level functions (`buildPlaylistTaskEntities`, `retryNextStatusOrNull`,
    `playlistIdsNeedingResolution`) extracted out of the Android/Room/coroutine-coupled
    engine class.
  - **FFmpeg: still not added**, per this stage's own explicit constraint (same as §37's
    2026-08-24 muxed/audio-only decision). No playlist item in real-device testing ever
    required a merge to be selectable, since only muxed/audio-only formats are offered.
    Video-only playlist formats correctly remain shown-but-disabled with "Requires
    merging — not available yet", identical to the single-item flow.
  - **Real content-restriction finding, not a MediaVault bug**: one candidate playlist
    ("JODA15") had every item individually blocked at extraction time —
    `python -m yt_dlp` confirmed `Video unavailable. It was blocked due to the claimed
    content by Zee Entertainment Enterprises Limited (ZEEL)` — despite listing fine in the
    flat playlist view. The per-item failure UI (clear message, no crash, rest of the
    group unaffected) handled it correctly; testing switched to a different, working
    playlist for the successful-download demonstration.
  - **Verified live** (Pixel 7a, small playlist — a public YouTube channel's video list):
    analyzed, selected several items, chose an audio-only format, queued, downloaded to
    completion with correctly-named/sized files at the chosen SAF location, watched
    playlist progress counts and per-item status update in real time, confirmed a
    genuinely-blocked item failed without stopping the rest of the group, and confirmed
    process-death recovery as described above.
  - Unit tests added: `QualityDescriptorTest` (5), `PlaylistProgressTest` (6),
    `MediaVaultDownloadEngineTest` (13, covering `buildPlaylistTaskEntities` ordering/
    duplicate-marking, `retryNextStatusOrNull` decisions, and process-recovery grouping),
    plus new playlist-flow tests in `HomeViewModelTest`. `./gradlew build` and the full
    unit test suite pass.
* **Supported Sources catalog is real, not a placeholder.** A new `Source`/`SourceCategory`
  domain model (`core:model`) and `SourceCatalogRepository`/`SourceCatalog`/
  `SourceCatalogIndex` (`core:domain/source/`) sit behind a generated, bundled JSON asset —
  the UI never touches yt-dlp internals or Chaquopy for this.
  - **Generation**: `core/extractor-ytdlp/scripts/generate_source_catalog.py` is a
    controlled, offline script (not run by Gradle or the app) that reads the *installed*
    yt-dlp's own extractor registry (`yt_dlp.extractor.gen_extractor_classes()`, ~1740
    classes) and groups extractor variants into one row per real service — e.g. `youtube`,
    `youtube:tab`, `youtube:search`, ... all collapse into one `YouTube` entry, keeping
    every underlying extractor id (`extractorIds`) for future analysis routing. Grouping
    keys off each extractor's own real test-case URL domain (registrable-label aware, so
    `tv.nrk.no` groups under `nrk` not `tv`) with a small curated override map for the
    handful of top services worth correcting by hand (YouTube, TikTok, Twitch, ...);
    category comes from a short keyword-rule table plus `age_limit` for Adult, falling
    back to `VIDEO`. Run it by hand after any yt-dlp version bump:
    `python core/extractor-ytdlp/scripts/generate_source_catalog.py`, then commit the
    regenerated `core/extractor-ytdlp/src/main/assets/source_catalog.json` (~290KB).
    Current catalog: **1,027 grouped services** from 1,741 raw extractor classes,
    `engineVersion` "2026.08.19" recorded alongside the sources so the UI's "Supported by
    current extraction engine" wording stays honest and never claims a permanent count
    (see §16).
  - **Runtime loading**: `YtDlpSourceCatalogRepository` (`core:extractor-ytdlp`) reads the
    asset once via `Context.assets`, parses it with the same `kotlinx.serialization` setup
    already used for yt-dlp's own JSON, and caches the result in memory — not refetched
    from a server, not regenerated on launch.
  - **Search/filter/index**: `SourceCatalogIndex` (`core:domain`, pure Kotlin/JVM, no
    Android dependency) precomputes a lowercased "name + domain + aliases" blob per source
    once, then does a plain case-insensitive substring scan per query — deliberately not
    SQLite FTS or a trie; a linear scan over ~1,000 short strings is comfortably fast
    enough and avoids the schema/migration overhead a Room-backed catalog would add.
    Category filtering and A→Z bucketing (with a `#` bucket for non-letter-starting names)
    live on the same class.
  - **UI**: new `SourcesScreen`/`SourceDetailScreen`
    (`app/ui/screens/sources/`) reachable via a "See all"/chip tap on Home's existing
    Popular Sources section (`PopularSourcesSection`, now a real shortcut rather than
    inert chips) and two new NavHost routes (`sources`, `sources/{sourceId}`) — not bottom
    tabs. Search field, category filter chips (`SourceCategory.entries` + "All"), a live
    "N of M sources" count, an A→Z sticky-header list, and a favicon-or-initials icon per
    row (`SourceIcon`: Coil `SubcomposeAsyncImage` fetches `faviconUrl`
    — Google's public `s2/favicons` endpoint — falling back to a generated initials
    avatar on load failure or when no domain is known; Coil's normal disk cache means
    icons are fetched once, not on every screen visit). Detail screen shows
    name/logo/domain/categories/supported-status/engine-version and a "Go to Analyzer"
    button that returns to Home — deliberately does not invent per-source functionality
    (no auto-filled URL, no site-specific options).
  - **A real bug found and fixed during this stage's own device testing**: the bottom
    navigation bar's tab-switch helper (`navigateToDestination` in `MediaVaultNavHost`)
    used the standard `popUpTo(start){saveState=true} + restoreState=true` pattern, which
    works for switching between sibling bottom tabs but got confused once the new
    `sources`/`sources/{id}` drill-in routes sat above a tab on the back stack — tapping
    "Home" (from the bottom bar *or* the detail screen's "Go to Analyzer" button) would
    silently land back on the Sources screen instead. Fixed by trying a direct
    `popBackStack(route, false)` first and only falling back to the save/restore dance for
    a tab never visited yet.
* **`DownloadEngine` now has a real implementation:** `MediaVaultDownloadEngine`
  (`app/download/`), bound via Hilt in `DownloadModule`. Source-agnostic — it consumes
  `ExtractorEngine.download()` for the actual byte transfer (implemented this stage in
  `YtDlpExtractorEngine`/`mediavault_ytdlp.py`) and knows nothing about yt-dlp itself.
  - **Queue**: one active transfer at a time, oldest `QUEUED` task first, guarded by a
    `Mutex`; the rest of the queue stays untouched. `DownloadTaskEntity` (Room) is the
    single source of truth, so the queue survives process death — `recoverAfterProcessDeath()`
    (called from `MediaVaultApplication.onCreate()`) resets any task still marked
    DOWNLOADING/PROCESSING back to PAUSED, then resumes the queue.
  - **States**: QUEUED → DOWNLOADING → PROCESSING → COMPLETED, plus PAUSED, CANCELLED,
    FAILED. `pause`/`resume`/`cancel`/`retry` each guard on the task's current status so a
    stale UI tap can never clobber a terminal state (e.g. Pause racing a just-finished
    download).
  - **Pause/resume**: cooperative — `mediavault_ytdlp.py`'s `request_stop()` flags a
    task id; yt-dlp's own `progress_hooks` (called per chunk) checks it and raises, which
    is far more reliable than JVM `Thread.interrupt()` racing Python bytecode boundaries.
    `MediaFormat.supportsResume` (true only for `http`/`https` protocol formats — false
    for HLS/DASH segment formats) decides what happens next: resumable formats keep their
    partial file and continue; non-resumable ones have their partial file deleted on pause
    so a later "resume" is an honest clean restart, never a silently corrupted file.
  - **Progress**: Kotlin polls `get_progress()` every 750ms (consistent with this
    project's existing "call a Python function, get JSON back" interop pattern, since
    Chaquopy callbacks in the other direction are unreliable) — bytes transferred, total
    bytes, speed, ETA, mapped into `ExtractionEvent.Progress` then `DownloadProgress`.
  - **Storage**: destination folder is a persisted SAF tree URI
    (`DownloadDestinationStore`, DataStore-backed, first activated this stage); the
    engine downloads to a real path in the app's cache dir (yt-dlp needs plain file I/O),
    checks free space against the format's estimated size first, then copies the
    finished file into the SAF folder via `DocumentsContract.createDocument` (no new
    dependency — plain platform API) and deletes the cache copy. A `MediaItemEntity` row
    is inserted on completion.
  - **Network policy**: `AndroidNetworkPolicyManager` (`app/policy/`) is now implemented
    — Wi-Fi/mobile detection via `ConnectivityManager`, a per-download limit and daily
    mobile-data budget (`NetworkPolicyStore`, DataStore-backed, real rollover via
    `LocalDate.now().toEpochDay()`), `Allow`/`Warn`/`QueueForWifi`/`Block` decisions. The
    engine blocks/queues-for-Wi-Fi at download start and records real transferred bytes
    against the daily budget on completion — no fabricated usage numbers.
  - **Background execution**: `DownloadForegroundService` (foreground, `dataSync` type),
    started when a task is enqueued/resumed/retried and stopped once the queue is idle;
    single ongoing notification summarizing the active/queued state.
  - **FFmpeg**: deliberately **not added**. Per the milestone's explicit constraint,
    format selection (`HomeViewModel.isSelectableForDownload`) only allows muxed
    (video+audio) or audio-only formats; video-only formats are shown but disabled with
    a "Requires merging — not available yet" note. This was a scoping decision, not an
    oversight — see §37 Decision Log, 2026-08-24.
* **Bug found and fixed via real-device testing**: `YtDlpResultMapper` was filtering
  `MediaAnalysisResult.formats` down to video-having formats only (a leftover from when
  `formats` was display-only, before downloading existed). Combined with the FFmpeg
  restriction above, a typical modern YouTube video — which is 100% split video/audio
  DASH streams, no muxed format — had **zero** selectable formats. Fixed to include
  audio-only formats too (storyboard/thumbnail-scrubbing entries, which report neither
  video nor audio, are still excluded). Verified live: format list now shows real
  audio-only entries (e.g. "M4A • 10 MB • audio only (mp4a.40.2)"), selecting one and
  downloading produces a byte-for-byte-correct file at the chosen SAF location.
* Home screen: format list is now genuinely selectable (single radio-button choice,
  showing resolution/fps/container/codec/size/audio info per format via an expanded
  `formatFormatSummary`), a Download button gated on a selection, and a SAF folder
  picker (`ActivityResultContracts.OpenDocumentTree`) triggered on first use and
  persisted thereafter. Playlist "download" actions are still report-only — see §37.
* Downloads screen: no longer a placeholder. Real sections (Active/Queued/Failed/
  Completed) backed by `DownloadsViewModel.observeAll()`, with progress bars,
  transferred/total/speed/ETA, and Pause/Resume/Cancel/Retry/Open actions per task.
* Room database: `download_tasks` gained `destinationTreeUri`/`destinationUri`/
  `localCachePath` columns (schema still version 1 — pre-release, no installed base to
  migrate, consistent with how this table has evolved in place before).
* Verified live on a physical device (Pixel 7a): analyzed a public-domain test video
  (Big Buck Bunny, Blender Foundation), selected an audio-only format, picked a SAF
  folder, downloaded to completion with a correctly-sized file on disk, confirmed the
  Downloads screen and the completed state survive a full process restart (force-stop +
  relaunch), and confirmed the foreground service starts and stops cleanly with no
  crashes throughout. The system "Open" action did not visibly launch a viewer for the
  `.m4a` file on this device — not chased further this stage; worth revisiting (possibly
  a missing default handler for `content://` URIs from a different SAF provider, or a
  MIME-type mismatch). Pause/resume was implemented and code-reviewed (including the
  terminal-state race fix above) but not separately exercised on-device this stage — the
  file downloaded too quickly on Wi-Fi to reliably catch mid-transfer by hand.
* `gradlew build` and the full unit test suite (all modules) both succeed after these
  changes, including new tests for format selection/enqueue flow (`HomeViewModelTest`,
  with new `FakeDownloadEngine`/`FakeDownloadDestinationProvider`), format-summary
  formatting (video-only/muxed/audio-only cases), and download-side error mapping
  (`DownloadErrorMapperTest`). Queue/state-transition/persistence logic inside
  `MediaVaultDownloadEngine` itself is Android/Room/SAF-coupled and has no Robolectric
  or Mockito infra in this project yet, so it was verified through the real-device run
  above and code review rather than JVM unit tests — flagged here rather than silently
  overclaiming coverage.

_Prior state, before the real DownloadEngine stage:_

* Multi-module Gradle project: `app`, `core:model`, `core:common`, `core:domain`,
  `core:database`, `core:extractor-ytdlp` (Kotlin, Jetpack Compose, Material 3, AGP
  9.2.1, Kotlin 2.4.10).
* **UI redesigned** (see §1, §37 Decision Log 2026-08-24): light theme, white/light-gray
  surfaces, blue primary accent (`#2F6FEB`), a blue "M" logo mark, consistent top bar and
  bottom navigation across all five screens. Shared components in
  `ui/components/` (`MediaVaultLogo`, `MediaVaultTopBar`, `MediaVaultCard`,
  `SectionLabel`, `EmptyStateCard`) are reused by Home and by the four placeholder
  screens. Home also gained: a time-of-day greeting, a static Popular Sources chip row,
  a Quick Actions grid linking to the real Downloads/Library/Player/Settings screens, a
  Recent Activity empty state, and a real device storage/network status row
  (`DeviceStatusProvider`, backed by `StatFs`/`ConnectivityManager` — genuine data, no
  `NetworkPolicyManager` logic implied). Downloads/Library/Player/Settings remain
  `EmptyStateCard` placeholders, now styled consistently rather than fabricated.
* Room database foundation in place (`download_tasks`, `media_items` tables).
  `download_tasks` gained optional `sourceMediaId`/`playlistId`/`playlistItemIndex`
  columns to prepare for a future playlist-aware download queue — unused until
  `DownloadEngine` has an implementation.
* Core engine interfaces defined in `core/domain`: `ExtractorEngine`, `DownloadEngine`,
  `TorrentEngine`, `NetworkPolicyManager`, `UpdateManager`, `PlayerEngine`.
  - **`ExtractorEngine` now has a real implementation:** `YtDlpExtractorEngine`
    (`core:extractor-ytdlp`), backed by yt-dlp `2026.8.19` running via Chaquopy
    (see §37 Decision Log, 2026-08-24, for why — not `youtubedl-android`).
    `analyze()` returns `ExtractionResult`, a sealed `Single` (one item — the original
    title/thumbnail/duration/source/webpage URL/formats/audio tracks/subtitles) or
    `Playlist` (`PlaylistAnalysisResult`: title/thumbnail/source/item count/ordered
    `PlaylistItem`s, each with id/title/thumbnail/duration/url/availability).
    Playlist items are lightweight (flat-extracted); resolving one item's full formats
    means calling `analyze()` again with that item's own URL. Best-effort `cancel()`.
    `download()` intentionally returns "not implemented".
  - `DownloadRequest` gained optional `sourceMediaId`/`playlistContext` fields so a
    future `DownloadEngine` implementation can give each playlist item its own ordered
    `DownloadTask` and support "skip already downloaded" via stable ids.
  - `DownloadEngine`, `TorrentEngine`, `NetworkPolicyManager`, `UpdateManager`,
    `PlayerEngine` still have no concrete implementation.
* Home screen: URL field, Analyze button, loading/cancel state, error state; single-item
  result preview (thumbnail via Coil, title, source, duration, format list); playlist
  result shows a "Playlist detected" header, item count, and an ordered item list with
  per-item checkboxes, select-range, "Download entire playlist"/"Download selected"
  (both report what they'd queue — no download actually starts). Wired to
  `HomeViewModel` via Hilt.
* Hilt wired in; Room database and `ExtractorEngine` (bound to `YtDlpExtractorEngine`)
  are the provided dependencies.
* Open-source repo docs written and current: README, LICENSE, PRIVACY, TERMS,
  CONTRIBUTING, CHANGELOG, THIRD-PARTY-NOTICES.
* `gradlew build` succeeds; unit tests cover the JSON mapper (single video, playlist,
  empty playlist, mixed-availability playlist), the error mapper, formatting helpers,
  and `HomeViewModel` (analysis + playlist selection + device-status loading, using a
  fake `ExtractorEngine`/`DeviceStatusProvider`) — 41 tests, 0 failures. An on-device
  instrumented test runs the real yt-dlp extraction path (requires
  `core:extractor-ytdlp`'s own `AndroidManifest.xml` to declare `INTERNET`, since its
  test APK is a separate process from `:app`). The redesigned debug APK has been
  installed and exercised on a physical device (Pixel 7a): navigation, Home (greeting,
  URL analyze, popular sources, quick actions, recent activity, real storage/network
  status), and single-video analysis all verified live with no crashes.
* **Known external issue found while testing (not caused by this stage's changes):**
  yt-dlp `2026.8.19`'s `youtube:tab` extractor — used for `youtube.com/playlist?list=…`
  URLs — is currently returning `HTTP Error 400: Bad Request` from YouTube's browse API
  for every playlist tested, including one that worked in the previous session. Direct
  single-video analysis is unaffected. This looks like an upstream YouTube API change
  outpacing this pinned yt-dlp version, not a MediaVault bug — the playlist mapping/UI
  logic itself is unchanged and still covered by 15 passing unit tests. Next
  yt-dlp-focused session should investigate/bump the pinned version.
* Git repository initialized locally and pushed to GitHub
  (`https://github.com/hereisjayesh-coder/MediaVault`, branch `master`).

Not yet started: media processing/FFmpeg — i.e. merged video+audio downloads (still
deliberately avoided — see above), torrent downloading, user-selected-folder/full-device
media scanning (the Library only indexes MediaVault-managed downloads so far — see this
stage's private-storage note above), app lock/biometric security, picture-in-picture,
source search (beyond the Supported Sources catalog itself — §17's "tapping an item opens
the appropriate analysis/download flow" is satisfied by returning to Home, not a
source-aware analyzer), and update checking.

The next implementation step must always be determined from the actual repository state, not from assumptions in this document.

---

## 35. Agent Startup Rule

At the beginning of every coding session:

1. Read `PROJECT_MASTER.md`.
2. Read `CHANGELOG.md`.
3. Check `git status`.
4. Check recent commits.
5. Inspect the relevant existing implementation.
6. Do not recreate existing work.
7. Continue from the current repository state.

Never rely on previous chat history being available.

---

## 36. Source of Truth

Priority order:

1. Current repository code
2. `PROJECT_MASTER.md`
3. Git history
4. `CHANGELOG.md`
5. Other documentation
6. Previous AI conversation context

If conversation memory conflicts with the repository, inspect the repository before changing anything.

---

## 37. Decision Log

### 2026-08-24 — yt-dlp runs via Chaquopy, not the `youtubedl-android` wrapper

**Decision:** `ExtractorEngine`'s first implementation (`YtDlpExtractorEngine`, in the
new `core:extractor-ytdlp` module) embeds a Python interpreter via
[Chaquopy](https://chaquo.com/chaquopy/) and `pip install`s yt-dlp directly, rather than
depending on `io.github.junkfood02.youtubedl-android`, the most commonly used
ready-made Kotlin wrapper for yt-dlp on Android.

**Why the alternative was rejected:** `youtubedl-android` is GPLv3-licensed. Bundling it
into MediaVault and distributing the resulting APK would put the combined binary under
GPLv3 obligations — in practice this would mean relicensing MediaVault away from MIT
(this is exactly why other apps built on that wrapper, e.g. Seal, are GPLv3 themselves).
Section 1 of this document did not fix MediaVault's license, but MIT was chosen at
project init and changing it as a side effect of a dependency choice is exactly the kind
of decision this log exists to make explicit rather than silent.

**Why Chaquopy was viable:** Chaquopy went fully open-source and MIT-licensed as of
v12.0.1 (previously it required a paid commercial license for closed-source
distribution). Combined with yt-dlp's own Unlicense, the whole extraction path stays
permissively licensed.

**Tradeoff accepted:** MediaVault owns a small Kotlin↔Python bridge
(`core/extractor-ytdlp/src/main/python/mediavault_ytdlp.py`) instead of using an
off-the-shelf Kotlin API, and cancellation of an in-flight extraction is best-effort
(interrupting a background thread) rather than a clean process kill, since Chaquopy has
no built-in call-cancellation primitive. AGP was also pinned to `9.2.1` (down from
`9.3.2`) because Chaquopy's Gradle plugin documents support only up to AGP `9.2.x`.

**Where this is documented for contributors:** README.md ("Why yt-dlp runs via
Chaquopy, not a prebuilt wrapper") and THIRD-PARTY-NOTICES.md.

### 2026-08-24 — UI redesign: light/blue design system replaces AMOLED-dark

**Decision:** MediaVault switches from the original minimal black/AMOLED theme (set in
§1 at project init) to a light design system: white/light-gray surfaces, a blue primary
accent, clean typography, generous spacing, and subtle borders/elevation instead of
glow, gradients, or decorative filler. The app logo is a blue "M" mark on a white
rounded-square card.

**Why:** The product owner reviewed a full UI mockup/showcase (multiple screen
concepts, several logo options) and approved this light/blue direction over the
original dark one. This is a product/design decision, not a technical one — the
backend architecture (`ExtractorEngine`, Room, Hilt, module boundaries) is unchanged.

**What changed:** `ui/theme/{Color,Theme,Type}.kt` (light `ColorScheme`, expanded type
scale), the launcher icon (white background, blue "M" foreground), `themes.xml`
(`Theme.Material.Light`, light status/nav bars), new shared components
(`MediaVaultLogo`, `MediaVaultTopBar`, `MediaVaultCard`, `SectionLabel`,
`EmptyStateCard`) used across all five screens, and a Home screen redesign (greeting,
popular-sources row, quick-actions grid linking to the real Downloads/Library/
Player/Settings screens, a recent-activity empty state, and a real device
storage/network status row backed by a new `DeviceStatusProvider`).

**What did not change:** `ExtractorEngine`/`YtDlpExtractorEngine`, `HomeViewModel`'s
analyze/cancel/playlist-selection logic, Room schema, DI graph shape (aside from the
new `DeviceStatusProvider` binding) — the redesign is presentation-layer only.

**Placeholders kept honest:** Popular Sources is a static, non-interactive list (no
source-index/search screen exists yet); Quick Actions' subtitles carry no fake counts;
Recent Activity shows a real empty state (no download-history persistence exists yet);
Downloads/Library/Player/Settings remain `EmptyStateCard` placeholders styled to match,
not fabricated functional screens.

---

### 2026-08-24 — Format selection restricted to muxed/audio-only to avoid FFmpeg

**Decision:** This stage implements the real `DownloadEngine`, but does **not** add
FFmpeg. `HomeViewModel.isSelectableForDownload()` only allows a format to be chosen for
download when it is muxed (has both video and audio) or audio-only; video-only formats
(the common case for high-quality YouTube DASH streams) are shown in the list — with
resolution/fps/codec/size, per the milestone's display requirement — but disabled with a
"Requires merging — not available yet" note rather than silently hidden.

**Why:** The milestone instructions were explicit: do not add FFmpeg unless a selected
format genuinely requires a video+audio merge, and if one does, stop and report the
exact requirement before adding the dependency. Restricting selection to formats that
never need a merge satisfies that instruction by construction — no merge is ever
required, so no dependency decision was needed. This is a scoping decision, not an
oversight: the alternative (adding FFmpeg to merge separate video+audio DASH streams)
is a substantial dependency-and-licensing decision on its own and belongs in a future,
explicitly-scoped stage if/when merged-quality downloads are wanted.

**Consequence found during real-device testing:** combined with a pre-existing mapper
bug (`YtDlpResultMapper` was filtering out audio-only formats entirely — see the Current
Project State section above), this initially meant a typical modern YouTube video had
*zero* selectable formats, since it offers only video-only and audio-only streams, never
muxed. Fixed by including audio-only formats in the selectable list; video-only formats
correctly remain disabled. Users can download the best available audio-only stream today;
merged video+audio downloads remain a future, separately-scoped decision.

---

### 2026-08-24 — Supported Sources catalog: generated offline, grouped by real domain

**Decision:** The ~1,700-service catalog is produced by a standalone Python script
(`core/extractor-ytdlp/scripts/generate_source_catalog.py`) run by hand against the
pinned yt-dlp install, committed as a static JSON asset
(`core/extractor-ytdlp/src/main/assets/source_catalog.json`) and loaded into memory once
at runtime. It is not generated by Gradle, not regenerated on app launch, and not fetched
from a server.

**Why:** The catalog only changes when yt-dlp itself is upgraded — regenerating it on
every build or every launch would be pure waste, and a server round-trip would make the
list dependent on connectivity for a screen whose whole point is "what can I download
right now with what's installed." A committed asset keeps it reproducible, diffable in
code review, and instant to load.

**Grouping approach:** yt-dlp exposes ~1,740 extractor *classes*, not ~1,740 *services* —
most real services (YouTube, Vimeo, ...) register several variant extractors internally.
The generator groups variants by the registrable domain label parsed from each
extractor's own first real test-case URL (not `_VALID_URL`'s regex source, which is far
harder to parse reliably), with public-suffix-aware handling so a subdomain like
`tv.nrk.no` groups under `nrk`, not the generic-looking `tv` label — an earlier, naive
`domain.split(".")[0]` approach was caught wrongly merging unrelated services (e.g. NRK,
JTBC, and Sohu all collapsing into one bogus "tv" entry) during this stage's own QA pass
before it ever reached device testing.

**Accuracy tradeoff accepted:** category assignment is a short keyword-rule table plus a
small curated override map for the handful of top services users are likely to search
for (YouTube, TikTok, Instagram, ...) — not a hand-classification of all ~1,700 entries,
which isn't practical or maintainable. Everything unmatched defaults to `VIDEO` (yt-dlp's
core purpose). The UI never claims a fixed count or that every listed service currently
works — see §16 and the "Supported by current extraction engine (yt-dlp `<version>`)"
wording on the detail screen.

---

### 2026-08-25 — Playlist quality choice resolved as a portable descriptor, not a shared formatId

**Decision:** A single quality choice for a whole playlist is represented as a
`QualityDescriptor` (resolution label + container + has-video/has-audio) rather than a
literal yt-dlp `formatId` shared across items. Each selected item is matched against this
descriptor independently once its own real formats are resolved
(`List<MediaFormat>.findMatching()`); no match means that item fails clearly rather than
falling back to a different quality.

**Why:** Playlist items are lightweight/flat-extracted (see §34, real `ExtractorEngine`
stage) — a raw `formatId` from one video has no guaranteed meaning for another video's
own format list, and nothing in this project's architecture assumes otherwise. A shape-
based descriptor is the smallest concept that lets "1080p MP4" mean the same thing across
independently-analyzed items without inventing any site-specific matching logic, which the
milestone explicitly ruled out.

**Consequence:** per-item unavailability is a first-class, visible outcome (a playlist
item can legitimately fail to match while its siblings succeed), not an error path to be
special-cased away — reinforced by the milestone's own "never silently substitute
quality" requirement.

---

### 2026-08-25 — Real Room migration (v1→v2) instead of evolving the schema in place

**Decision:** The playlist columns added to `download_tasks` this stage went through a
real, additive `Migration(1,2)` (`core/database/Migrations.kt`, registered via
`.addMigrations()`), rather than reusing the earlier sessions' shortcut of adjusting the
entity at schema version 1.

**Why:** Every prior schema change happened before this device carried any real user
data, so bumping the in-place version 1 definition was harmless. That is no longer true —
the Pixel 7a test device now holds genuine downloads and media rows from previous stages.
Skipping a real migration here would have either force-uninstalled the app (losing that
data) or crashed on the next launch. Verified live: the existing Big Buck Bunny download
and a previously-cancelled task both survived the version-2 app update untouched.

---

### 2026-08-25 — Downloads default to app-private storage; the SAF folder picker is gone

**Decision:** `MediaVaultDownloadEngine` now copies every finished download into a new
`MediaVaultStorage`-managed app-private directory (`context.getExternalFilesDir(null)/media`)
by default. The `ACTION_OPEN_DOCUMENT_TREE` folder-picker step that used to gate every
download in `HomeViewModel` was removed outright, along with `DownloadDestinationStore`
and its DI binding — not left disabled or dead-coded. `DownloadRequest`/
`PlaylistDownloadRequest.destinationTreeUri` were deleted from the domain layer;
`DownloadTaskEntity.destinationTreeUri` (the Room column) stays, now permanently unused,
since dropping a Room column cleanly requires a full table rebuild migration and the
milestone's instructions were explicit about reusing the schema and adding only what's
needed — an unused nullable column is a smaller footprint than that rebuild.

**Why:** This milestone's explicit goal was "completed downloads become managed
MediaVault Library items, playable inside the app" — which only works cleanly if
MediaVault actually owns where the file lives. The previous SAF flow let a download land
anywhere the user picked (often a public Downloads folder), meaning MediaVault could
never fully guarantee rename/delete/metadata consistency for it, and every first-ever
download paid a folder-picker permission-grant tax before it could even start. Files a
user explicitly wants outside the app remain fully reachable via the new Library
"Export to device" and "Share" actions — SAF is still used there, just at export time
instead of download time, which is a more precise fit for what SAF's document-picker
model is actually for (one deliberate hand-off of one file), rather than a
permanently-held write grant to an entire folder.

**Consequence — Gallery exposure:** app-private external storage
(`getExternalFilesDir`) is never indexed by MediaStore/Gallery and is removed
automatically on uninstall, satisfying the "do not automatically expose downloads to the
public Gallery" requirement by construction rather than by a scanning opt-out. Verified
live via `adb shell run-as` (file only readable as the app's own UID) and a MediaStore
content query (no match).

**Consequence — backward compatibility:** downloads completed by the prior SAF-based
milestone keep their `content://` URIs and remain fully valid, playable Library rows —
see the private-storage note in §34's Current Project State for how `LibraryRepository`
handles both URI schemes. Nothing already on a user's device breaks or disappears; only
the default for *new* downloads changed.

---

**END OF MASTER SPECIFICATION**
