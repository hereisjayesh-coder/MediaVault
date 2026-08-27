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

_Last updated: 2026-08-27, after the Subtitle Display Styles stage._

* **Subtitle appearance is now user-configurable — Classic / Clean / Outlined.** A small,
  isolated addition on top of the existing Media3 subtitle-rendering path; no navigation,
  downloader, or unrelated player code touched.
  - **Three styles**: Classic (white text, black semi-transparent background — the
    traditional TV-caption look), Clean (white text, no background — the new default),
    Outlined (white text, no background, a subtle black outline for readability over
    bright video). All three keep plain white foreground text; only background/edge
    treatment differs.
  - **Reusable, not hard-coded**: `SubtitleStyle` (`app/player/SubtitleStyle.kt`) is a
    3-value enum; `SubtitleStyle.toSpec()` maps each value to a small, Android-independent
    `SubtitleStyleSpec` (foreground/background as plain `0xAARRGGBB` ints, an
    `EDGE_TYPE_NONE`/`EDGE_TYPE_OUTLINE` enum) — genuinely pure Kotlin, unlike almost
    everything else in the player layer, so it's plain-JUnit-testable without Robolectric.
    `PlayerScreen.kt`'s private `SubtitleStyleSpec.toCaptionStyleCompat()` is the one place
    this becomes a real Media3 `CaptionStyleCompat`, applied to `PlayerView`'s
    `SubtitleView` via `setStyle()` on every recomposition. `subtitleView.setApplyEmbeddedStyles(false)`/
    `setApplyEmbeddedFontSizes(false)` ensure the chosen style always wins over a source's
    own embedded subtitle styling — required for the three styles to reliably look the same
    across different sources.
  - **Persisted independently of the app's Light/Dark/System theme**: new
    `SubtitleStyleStore` (`app/player/`, DataStore-backed, bound via `PlayerModule`) mirrors
    the existing `DataStoreThemeStore`/`AudioPreferenceStore` pattern exactly — its own
    `subtitle_style` DataStore file, defaulting to `CLEAN` until the user picks something
    else, applied to every video and every app session, with zero coupling to
    `ThemeStore`/`ThemeMode`.
  - **Track selection unchanged**: the style picker lives as a new "Style" section
    (divider + 3 checkable rows) appended to the existing Subtitles popup menu in
    `PlayerControlsPanel`'s `SubtitleMenu` — no new icon, no layout change to the main
    controls row, no change to `onSubtitleTrackSelected`/track enumeration.
  - **Verified live on a physical device (Pixel 7a)** against the same
    `multitrack_test.mp4` fixture used for audio/subtitle-track verification: all three
    styles render visibly distinct (Classic's black bar vs. Clean's fully transparent
    background vs. Outlined's dark text edge, all confirmed by screenshot comparison at
    the same timestamp), switching styles takes effect immediately without restarting
    playback, subtitle-track switching (en/es) still works correctly with any style
    active, and fullscreen's control row shows no layout regression (same icon set, same
    positions).
  - Targeted unit tests added to `PlayerViewModelTest` (default-is-Clean, selecting a style
    persists and reflects into `uiState`, a style already persisted from a previous session
    loads correctly) using a new `FakeSubtitleStyleProvider`. Only `:app:testDebugUnitTest
    --tests PlayerViewModelTest` and a compile check were run — no shared code changed, so
    the full suite wasn't re-run.

* **Navigation/Download/Player checkpoint closed out.** A milestone review found the
  Downloads Open / Home tab reset / Player back-transition fixes (§37, 2026-08-27) and the
  Player gesture/controls/watch-history work (below) were already correctly implemented on
  `master` — confirmed by re-reading the code, not just trusting the changelog. Two real
  gaps were found and closed this stage:
  - **Downloads "Open" silently failed instead of erroring** when a task's Library row
    could no longer be resolved (`DownloadsViewModel.openInPlayer()` returned early with no
    user feedback). Now surfaces an inline error card (matching `HomeScreen`'s existing
    `MessageCard` error-styling convention) reading "Couldn't find this item in your
    Library. It may have been removed or renamed." instead of doing nothing.
  - **Subtitle-track verification was undocumented and contradicted between docs**:
    `CHANGELOG.md` claimed subtitle switching was verified against a 2-track fixture, but
    this file's own corresponding entry (below) only documented audio-track verification.
    **Re-verified live on a physical device (Pixel 7a)** this stage: imported the existing
    `multitrack_test.mp4` fixture (previously placed in the app's private storage by an
    earlier stage but not indexed in Library — imported via the standard "Import a file"
    flow from a SAF-visible copy, since Android's Storage Access Framework cannot browse an
    app's own `Android/data` folder even for that same app's picker) and confirmed both
    audio tracks ("en"/"es") and both subtitle tracks ("en"/"es") switch correctly — English
    showed "ENGLISH SUBTITLE TRACK - line three", Spanish showed "PISTA DE SUBTITULOS EN
    ESPANOL - linea tres" at the same timestamp, confirming the track actually changes
    rather than the label alone. Both docs now agree.
  - No other item in the checkpoint (Home reset, Player transition, gesture contract,
    watch-history tab, PiP) needed any code change — each was independently re-confirmed
    present in the code before being left alone, per this project's "don't redo completed
    work" rule.

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

* **FFmpeg is now integrated — merged video+audio downloads are real, ending the
  deliberate restriction described in §37's 2026-08-24 and 2026-08-25 scoping entries.**
  A selected video-only format is no longer shown-but-disabled; it is paired with a
  compatible audio-only track and downloaded as a real, selectable option.
  - **New `MediaProcessor` abstraction** (`core:domain/processing/`), mirroring how
    `ExtractorEngine` keeps yt-dlp out of the rest of the app: a `merge(MergeRequest):
    Flow<ProcessingEvent>` contract plus `cancel(taskId)`, with no FFmpeg type leaking
    above this layer. `FFmpegMediaProcessor` (`app/processing/`) is the only
    implementation, backed by the [FFmpegKit](https://github.com/moizhassankh/ffmpeg-kit-android-16KB)
    Maven dependency (`com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1` — a maintained,
    16KB-page-size-compliant repackaging of the now-archived `arthenica/ffmpeg-kit`,
    LGPL v3 — see `THIRD-PARTY-NOTICES.md`). Every invocation is `-c copy`
    (`-map 0:v:0 -map 1:a:0`) — a pure stream-copy remux, **never** a re-encode/
    transcode, which is both what the milestone requires (no quality loss, no
    re-encoding time/battery cost) and keeps FFmpeg usage inside the LGPL-only
    portion of FFmpegKit with no GPL-only codec ever invoked.
  - **`DownloadOption`/`buildDownloadOptions`** (`core:domain/download/`, pure and
    unit-tested) replaces the old flat `MediaFormat` list on the single-item screen. A
    muxed or audio-only format still becomes one direct, already-selectable option,
    unchanged from before. Every video-only format becomes one selectable paired option
    per distinct audio *language* available (never just "the best" one — no language
    silently dropped), with only the largest same-language variant offered per language
    to avoid bitrate-noise. A video-only format with genuinely no audio track anywhere
    still gets exactly one row, shown with its real resolution but marked
    `unavailableReason` — "never silently hide," matching this project's existing
    playlist/mapper conventions. No resolution tier is hardcoded; whatever heights the
    extractor reports become whatever rows exist.
  - **Output container is chosen, never guessed**: MP4 video + M4A/MP4 audio remuxes to
    MP4; WEBM + WEBM remuxes to WEBM; any other pairing falls back to MKV, the universal
    stream-copy-safe container — still never a transcode.
  - **`MediaVaultDownloadEngine.runSplitStreamDownload()`**: downloads the video-only
    stream, then the audio-only stream (combined progress reported throughout via a new
    `MERGING` `DownloadStatus`, added to the existing enum rather than overloading
    `PROCESSING`), then hands both cache files to `MediaProcessor.merge()` and reuses the
    exact same `finish()` path every direct download already uses to reach the Library —
    no separate merged-file code path. A split-stream task is never byte-offset-resumable
    (`canResume = false`, regardless of the underlying format's own `supportsResume`);
    Pause is a no-op while `MERGING` (a stream-copy remux of already-downloaded files is
    normally seconds long) but Cancel still works mid-merge. Process-death recovery
    (`recoverAfterProcessDeath()`) now also resets a stuck `MERGING` task to `PAUSED`,
    same as `DOWNLOADING`/`PROCESSING` — resuming re-downloads both streams and re-merges
    from clean, which is wasteful but safe, identical to how every other
    non-resumable task already restarts.
  - **Room migration**: `MediaVaultDatabase` version 3→4, with a real, additive
    `Migration(3,4)` (two new nullable `download_tasks` columns — `audioFormatId`/
    `audioLocalCachePath` — both null for every pre-existing row, which is exactly the
    "direct download" behavior those rows already had).
  - **`MediaFormat` gained `heightPx`/`widthPx`/`languageCode`**, populated in
    `YtDlpResultMapper` from yt-dlp's own reported `height`/`width`/`language` fields
    (never guessed) — needed for reliable resolution sorting/grouping and per-language
    audio pairing, since the existing `resolutionLabel` is display text only.
  - **UI**: `HomeScreen`'s format list now renders `DownloadOption` rows via
    `downloadOptionSummary()` (e.g. "1080p60 • MP4 • avc1 • 92 MB • + audio [en]"), with
    a "Video + audio will be combined automatically after downloading" hint on paired
    rows; `DownloadsScreen` shows a distinct "Merging" status label and a Cancel-only
    action while `MERGING`.
  - **Verified live** (Pixel 7a): analyzed a real YouTube source (Blender Foundation's
    "Sintel" open movie trailer) offering only split video-only/audio-only DASH streams,
    selected the 1920x818 ("1080p"-tier) MP4 `avc1` paired option (176 MB estimate),
    downloaded and watched the task move `Downloading` → `Completed` in the Downloads
    screen (logcat confirmed FFmpegKit's native library actually loaded and ran a merge
    session — `Loading ffmpeg-kit` / `Loaded ffmpeg-kit-custom-arm64-v8a-6.0-20251215` —
    between the download finishing and the task completing, with no exceptions). Pulled
    the finished 184,792,468-byte file off the device and parsed its MP4 box structure
    directly (no ffprobe needed): both a `vide`- and a `soun`-handler `trak` are present,
    proving the remux genuinely combined both streams rather than the task completing
    with only one. The file appeared correctly in the Library (`14:48 • 1920x818 •
    172 MB`) and played back normally in the internal Media3 player — video frames
    rendered and the seek position advanced in real time (0:03 → 0:20 over ~4 real
    seconds), and `dumpsys audio` showed a genuine `AudioTrack` player started by
    `com.mediavault.app` at the same time, confirming audio was actually decoding and
    playing, not just a silent video. This closes out the on-device verification gap
    called out when this stage's code was first committed.
* **The Player is now a dedicated, immersive playback experience, not a sixth piece of
  five-tab content.** Tapping a Library item or the Player tab's "Continue watching" card
  opens a separate `player/{mediaItemId}` route with no bottom navigation bar at all
  (rather than the old design, where `PlayerScreen` was just another tab page inside the
  same `Scaffold` as Home/Downloads/Library/Settings).
  - **Player tab vs. dedicated screen split**: the `Player` bottom-tab route now renders a
    new, lightweight `PlayerHubScreen`/`PlayerHubViewModel` — a "Continue watching" card
    (thumbnail, title, progress bar, Resume) for whatever was last played, or an honest
    empty state — that never creates a real `PlayerEngine`/`ExoPlayer` instance just from
    visiting the tab (the old design did, meaning simply tapping the Player tab silently
    started audio playback in the background — a real behavior bug this redesign fixes by
    construction). Tapping the card navigates to the same dedicated `player/{id}` route a
    Library item opens.
  - **Aspect-correct video surface**: `PlaybackState` gained `videoAspectRatio` (derived
    from Media3's own `player.videoSize`, rotation and pixel-aspect already applied) and
    `isLooping`/`isEnded`. Landscape/square content sizes its container by width
    (`fillMaxWidth().aspectRatio(ratio)` — no space reserved beyond what the video itself
    occupies); portrait content and true fullscreen both fit within whatever height is
    actually available, centered (`BoxWithConstraints` + a `fitWithinBounds` helper).
    Fit/Fill/Zoom/Original map 1:1 to Media3 `PlayerView`'s own `AspectRatioFrameLayout`
    resize modes, selectable from a new control.
  - **A real bug found and fixed during this stage's own device testing**: the very first
    on-device pass reproduced the reported "giant black empty area" almost immediately —
    relocated, not fixed, by an early version of the aspect-fit work. Root cause: the
    outer container had an unconditional `Color.Black` background while its `Column`
    children were never `weight`-filled in embedded mode, so any leftover vertical space
    below the compact video+controls rendered as a stray black void. Fixed by making the
    root background match the fullscreen/PiP state (light theme background when embedded,
    black only when truly fullscreen) and scoping `Color.Black` to the video's own
    aspect-fit box specifically. A second, related bug surfaced right after: fullscreen
    still showed a persistent light-gray strip where the status bar used to be, because
    `Scaffold`'s shared `innerPadding` doesn't reactively shrink when this screen
    imperatively hides system bars. Fixed by having `MediaVaultNavHost` give the dedicated
    player route none of `Scaffold`'s inset padding at all, and having the Player manage
    its own `statusBarsPadding()`/`navigationBarsPadding()` off its own `isFullscreen`
    state instead — both verified live afterward with no stray empty regions in either
    mode.
  - **Fullscreen redesigned as true immersive playback**: controls now float over the
    video as a semi-transparent overlay (top bar + bottom controls, `AnimatedVisibility`
    fade) instead of the old design, which pushed the video into a smaller `weight(1f)`
    box below a still-visible top bar. Controls auto-hide ~3.5s into playback and
    reappear on tapping the video; verified live (letterboxed correctly, auto-hide/tap
    both worked).
  - **New controls**: -10s/+10s (`PlayerViewModel.seekBy`, clamped to the item's real
    duration), Loop (`PlayerEngine.setLooping`, backed by Media3 `REPEAT_MODE_ONE`),
    Picture-in-picture (`Activity.enterPictureInPictureMode`, manifest now declares
    `android:supportsPictureInPicture="true"`; a new `LocalIsInPictureInPicture`
    composition local, set from `MainActivity.onPictureInPictureModeChanged`, swaps the
    custom Compose controls for Media3 `PlayerView`'s own minimal controller while in PiP,
    since Compose's touch targets aren't usable at PiP's tiny window size), a sleep timer
    (fixed 10/30/60-minute durations, or "end of this video" — pauses instead of
    auto-advancing at the next natural stop), and a media details dialog. The details
    dialog is the same `MediaDetailsDialog` composable the Library's three-dot menu
    already used — extracted out of `LibraryScreen.kt` into `ui/components/` so both
    screens share one implementation rather than duplicating it.
  - **Playlist Previous/Next**: `LibraryRepository.getPlaylistSiblings()` (new) resolves a
    Library item's playlist order by joining `MediaItemEntity.sourceDownloadTaskId` →
    `DownloadTaskEntity.playlistId` → that playlist's other `DownloadTaskEntity` rows
    (already ordered by `playlistItemIndex`) → their own Library rows, skipping any
    sibling that never finished downloading. Previous/Next only render when this resolves
    to more than one item — never shown for standalone media. Reaching the end of an item
    with a next sibling auto-advances to it; reaching the end of standalone media (or the
    last item in a playlist) leaves it stopped, with Play now meaning "replay from the
    start" rather than doing nothing.
  - **Preferred audio language remembered**: a new `AudioPreferenceStore` (DataStore)
    records the language code of the last audio track a user explicitly picked; the next
    file offering a track in that same language auto-selects it on load. Only ever applied
    when a real, source-reported language code matches — never guessed, consistent with
    this project's existing "never invent a language" rule for `MediaTrackInfo`.
  - **External subtitle support**: deliberately not built this stage (still embedded
    tracks only), but `PlayerEngine.selectSubtitleTrack`'s id-based contract already
    generalizes to it without a breaking change — documented inline as the extension point
    rather than half-built.
  - **Verified live** (Pixel 7a, the same downloaded Sintel file from this milestone's own
    device pass): Library → Player and Player tab "Resume" → Player both opened the
    dedicated screen correctly with the bottom nav absent in both cases; the 1920x818
    (2.35:1) landscape video rendered aspect-correct with no stray empty space in both
    embedded and fullscreen layouts (the two bugs above, found and fixed live); seeking
    via the timeline and the +10s button were confirmed pixel-exact (0:29 → 0:39); the
    "replay from start" completed-playback path was triggered live by letting the film run
    to its real end (14:48/14:48, Play icon shown, tapping it correctly restarted at
    0:01); app force-stop/relaunch correctly resumed mid-file every time; real system
    Picture-in-Picture was entered and exited cleanly with no crash; the media details
    dialog showed correct, real file data; and the aspect-ratio menu opened, listed
    Fit/Fill/Zoom/Original, and applied a selection without error (Fit vs. Zoom render
    identically in this specific embedded layout, since the container is already sized to
    the video's own aspect ratio with nothing to crop — expected, not a bug). **Not
    exercised live this session**: a 9:16/portrait source, multi-audio-track switching,
    and embedded subtitles — no test file with any of those properties was available:
    Sintel is 2.35:1 with a single English audio track and no subtitles, so the
    audio-track and subtitle menus correctly stayed hidden (matching their own
    `if (tracks.size > 1)`/`if (tracks.isNotEmpty())` gating) rather than being exercised.
    Speed/Loop/sleep-timer selection were code-reviewed and unit-tested but not
    individually tap-verified this pass, having already spent considerable device-testing
    time isolating and fixing the two layout bugs above.
* **Player controls, popup positioning, and touch gestures polished** — a focused
  follow-up to the Player Redesign stage above, fixing five specific UX gaps without
  touching the download/library/FFmpeg engines.
  - **Popup menus now anchor to their own trigger button** (Speed/Audio/Subtitle/
    Aspect-ratio/Sleep-timer), instead of a shared, fixed screen position. Root cause:
    each menu's `IconButton` and `DropdownMenu` were declared as loose siblings inside a
    shared scrollable `Row`, so `DropdownMenu`'s `Popup` anchored to whatever position its
    own composable slot happened to land at rather than the specific button pressed. Fixed
    by a new `PopupMenuButton` composable that wraps a trigger + its `DropdownMenu` in one
    `Box` each — the standard, documented Compose pattern for this — plus a shared
    `MenuCheckItem` row (label + trailing check icon, replacing an appended `"✓"` string)
    used by all five menus for consistent formatting. `DropdownMenu`'s built-in fade/scale
    transition and in-bounds clamping were already there; nothing new was added for those.
  - **Fullscreen now rotates to landscape for landscape/square content** (`ApplyLandscapeLock`,
    `Activity.requestedOrientation` = `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` on enter,
    `SCREEN_ORIENTATION_UNSPECIFIED` on exit), matching mainstream video apps — safe
    against Activity recreation since `orientation` is already in `MainActivity`'s
    `android:configChanges` (added earlier for PiP). Portrait-aspect content is
    deliberately never forced into landscape (would misuse the frame). The fullscreen
    overlay controls (top bar + bottom controls) now also carry
    `systemBarsPadding()`/`displayCutoutPadding()`, so they stay clear of a notch, punch
    hole, or gesture-nav area in both orientations — the video surface itself stays true
    edge-to-edge; only the *controls* are inset-safe.
  - **YouTube-style gestures on the video surface**: tapping the left/right third
    seeks -10s/+10s with a transient on-screen "-10s"/"+10s" bubble (auto-dismissing after
    650ms); tapping the center third toggles the fullscreen overlay (unchanged); holding
    anywhere jumps to 2x for the duration of the hold and restores the *exact* prior speed
    (not just 1x) on release, with a "2.0x speed" pill shown while held. All three resolve
    from a single `awaitEachGesture` per touch (never a separate tap detector layered under
    a long-press one), so a real long-press can never also fire a stray seek/toggle
    underneath it. `PlayerViewModel.onLongPressSpeedEngaged`/`onLongPressSpeedReleased`
    remember whichever speed was active before the hold, including a non-default one.
  - **Spacing polish**: base padding around the controls increased (12→16dp top bar,
    16→20dp controls row) so nothing sits flush against a screen edge even before insets
    are added; sleep timer options changed from 10/30/60/off/end-of-media to
    15/30/60/off/end-of-video per this stage's explicit spec.
  - **Verified live** (Pixel 7a, same Sintel file): Speed, Aspect-ratio, and Sleep-timer
    menus each opened directly next to their own icon (not a shared corner) — confirmed
    across three different icon positions in the same row. Sleep timer menu showed exactly
    "Off / 15 minutes / 30 minutes / 60 minutes / End of this video". Left/right region
    taps were verified pixel-exact while paused (2:54→2:44 on a left tap, 2:44→2:54 on the
    following right tap), each with its feedback bubble visible on the correct side.
    Holding the video showed the "2.0x speed" pill and audibly/visibly sped up playback;
    releasing removed the pill and — confirmed via the Speed menu's own checkmark —
    restored the exact pre-hold speed (1.25x, itself the result of an earlier real tap,
    not a scripted 1x baseline), matching the "remember the real prior speed" requirement
    more rigorously than a clean-1x test would have. Tapping fullscreen on the landscape
    video rotated the device to landscape live, with playback continuing unbroken through
    the rotation and both overlay bars showing correct margin from every screen edge;
    exiting fullscreen rotated the device back to portrait and returned to the exact
    embedded layout, also with playback unbroken throughout. Picture-in-Picture was
    re-verified working after the gesture-detection rewrite (real floating system window,
    correct content, no crash). **Not exercised live this session** (same missing-source
    limitation as the prior stage): a 9:16/portrait source's fullscreen behavior, and
    multi-audio-track/subtitle menus — Sintel still offers only one audio track and no
    subtitles.
* **Format selection/download UI redesigned**: the single-item format picker
  (`HomeScreen`/`AnalysisResultCard`) no longer renders one flat `DownloadOption` list —
  `buildDownloadOptions`' output is now split (`DownloadOption.section`/
  `groupedBySection()`, `core:domain/download/`) into Video and Audio sections (plus a
  defensive, currently-always-empty "Other" section so a future format shape isn't
  silently dropped), Video sorted highest-to-lowest resolution. Each row's text comes
  from new `HomeFormatting` helpers (`videoOptionTitle`/`videoOptionSubtitle`/
  `audioAvailabilityLabel`/`audioOptionTitle`/`audioOptionSubtitle`) — resolution/fps,
  container, codec, the final estimated size, and audio availability for Video rows;
  format, codec, bitrate, and size for Audio rows.
  - **`MediaFormat.bitrateKbps`** (new field, `core:model`) is populated in
    `YtDlpResultMapper` from yt-dlp's own `abr`, falling back to `tbr` — never
    estimated — needed since nothing previously carried audio bitrate at all.
  - **Persistent bottom action bar**: `HomeScreenContent` is now a `Box` with the
    scrollable `LazyColumn` plus a bottom-aligned bar (`DownloadActionBar` for the
    single-item flow, `PlaylistQueueActionBar` for the playlist quality-setup step),
    which stays visible while the list scrolls (it sits outside the `LazyColumn`, not
    as a trailing item) and needs no extra inset handling since it's already inside the
    outer `Scaffold`'s `innerPadding`. Disabled with a prompt until a selection is
    made; shows the selected quality/estimated size (single-item) or item
    count/quality/running total (playlist) once one is. The playlist setup card's
    inline Queue button was removed in favor of this bar; Cancel stays inline.
  - **`NetworkPolicyManager` is now consulted before enqueueing**, not only once
    `MediaVaultDownloadEngine.runDownload()` starts the actual transfer (which still
    does its own check too — this is additive, not a replacement, so the sole-owner
    contract in the interface's KDoc still holds). `HomeViewModel.beginEnqueueSelectedFormat`/
    `confirmPlaylistQueue` call `evaluate()` first: `Block` shows the reason and never
    enqueues; `Warn` shows a new `NetworkWarning` (`HomeUiState`) that blocks the
    `AlertDialog`-based `NetworkWarningDialog` until the user taps "Download anyway"
    (`onNetworkWarningConfirmed`) or cancels — never silently proceeds on a risky
    download; `QueueForWifi` still enqueues but tells the user up front it will wait.
    A playlist's estimate is the chosen format's own size times the item count
    (`estimatedPlaylistTotalSizeBytes`) — the same rough-estimate caveat the playlist
    setup step already had.
  - **Bug fix, not a new feature**: the old `downloadOptionSummary()` unconditionally
    showed "video only" for any `DownloadOption` whose `audioFormat` field was null —
    which included every *muxed* direct option (a format that already has its own
    embedded audio, so `audioFormat` is never set for it; only `videoFormat.hasAudio`
    reflects that). Fixed as part of this redesign's `videoOptionSubtitle`/
    `audioAvailabilityLabel`, which check `videoFormat.hasAudio` directly.
  - **Verified live** (Pixel 7a): analyzed a real YouTube source (Big Buck Bunny 60fps
    4K, split video-only/audio-only DASH streams) — Video section sorted 4K → 1440p →
    1080p → 720p → 480p → 144p with correct per-row text, Audio section below it with
    real bitrates (129/66/65/50/49 kbps) from `abr`, and the bottom bar staying pinned
    through the full scroll while correctly toggling disabled↔enabled and updating its
    text as different rows were selected. Selected a WEBM/opus audio-only row, tapped
    Download, and confirmed directly in the app's own Room database (pulled via `adb`,
    queried with `sqlite3`/Python) that the resulting task reached `COMPLETED` with the
    exact `formatId` chosen in the UI — proof the network-policy-gated enqueue path
    genuinely runs end-to-end, not just navigates to Downloads optimistically. **Not
    exercised live this session**: the playlist quality-setup bar (no playlist URL was
    rehearsed this session) and an actual `Block`/`Warn`/`QueueForWifi` decision (the
    test device had no mobile-data budget restriction configured to trigger one) —
    both are covered by unit tests (`HomeViewModelTest`) but not confirmed on-device.

* **Global theme system**: `Settings` gained a real Appearance section (a placeholder
  screen until now) with a Light/Dark/System default picker. `com.mediavault.app.settings`
  (new package) holds `ThemeMode` (the enum + its pure `resolveIsDark(systemInDark)`
  resolution logic), `ThemeStore`/`DataStoreThemeStore` (DataStore-backed persistence,
  same pattern as `NetworkPolicyStore`, bound via a new `SettingsModule`), and
  `ThemeViewModel` (a thin Hilt `ViewModel` wrapping the store, used by both
  `MainActivity` and `SettingsScreen` — every instance reflects the same persisted
  value since both read through the one `ThemeStore` singleton).
  - **`MediaVaultTheme` now takes a `themeMode` param** and picks between the existing
    light `ColorScheme` and a new true-dark/AMOLED-friendly one
    (`MediaVaultDarkColorScheme`, `ui/theme/Theme.kt`) built from new dark tokens in
    `Color.kt` (near-black background/surfaces, a lighter blue accent tuned for
    contrast on dark). No other screen/component needed changes — everything already
    read colors from `MaterialTheme.colorScheme` rather than hard-coded values, so
    swapping the scheme instance re-themes the whole app (Home, Downloads, Library,
    Player's embedded chrome, Settings, Supported Sources, dialogs, menus, bottom
    nav, the format-selection UI and its persistent Download bar) automatically. The
    Player's own video canvas (fullscreen letterboxing, gesture-bubble scrims) stays
    intentionally black/white regardless of theme, matching standard video-player UX
    — not a gap, a deliberate exception already true before this stage.
  - **Startup flash prevention**: a new `values-night/themes.xml` covers the native
    pre-Compose frame for the common "system is in dark mode, user hasn't overridden
    it" case at zero runtime cost. `MainActivity.onCreate` additionally resolves the
    persisted preference synchronously (`runBlocking` over a single small DataStore
    read — the same accepted trade-off other startup-critical tiny preferences use
    when a full splash-screen library isn't in scope) before `setContent`, setting
    the window background (`window.setBackgroundDrawable`) and system bar icon style
    (`enableEdgeToEdge`) to match immediately — covering the explicit-override case
    the resource qualifier alone can't (Dark selected while the system is Light, or
    vice versa). A `LaunchedEffect(isDark)` inside `setContent` re-applies the same
    window chrome reactively whenever the resolved theme changes at runtime, so the
    system bar icons never go stale relative to the app's own background after a
    live theme switch or an OS-level dark-mode toggle in SYSTEM mode.
  - **No separate player-only theme setting** — the dedicated Player screen reads the
    same global `MediaVaultTheme` as every other screen; only its video canvas itself
    is exempt, per above.
  - **Downloads screen audited, not changed**: `DownloadTaskCard.progressDetailLabel()`
    already showed downloaded/total size, throughput, and ETA before this stage: this
    was verified before writing any code, and nothing there was touched.
  - **Verified live** (Pixel 7a): cold-launched with the device's system theme in dark
    mode and confirmed the app followed it (System default, the shipped default);
    switched to Light and to Dark from Settings and confirmed every visible surface
    re-themed instantly and correctly each time; force-stopped and relaunched with
    Dark explicitly selected and confirmed Settings still showed Dark selected (not
    reverted to System) after the cold restart, proving persistence; confirmed the
    Downloads screen (playlists, task cards, status colors, progress bars) still
    renders correctly, unregressed, in dark mode. **Not exercised this session**:
    Player/Library/Supported Sources screens' dark rendering specifically, and an
    in-progress download's live speed/ETA line in dark mode (both share the same
    `MaterialTheme.colorScheme` tokens verified elsewhere; out of this stage's
    testing scope per its own instructions, which excluded unrelated player/source/
    torrent testing).
* **Local media import, and a privacy-first storage/export model**: Library gained an
  explicit "Add media" entry point (`ACTION_OPEN_DOCUMENT` for one file,
  `ACTION_OPEN_DOCUMENT_TREE` for a folder) — the *only* way media MediaVault didn't
  download itself ever enters the Library. There is deliberately no device-wide or
  gallery scan anywhere in this codebase; a folder import reads only that one folder's
  direct children (`DocumentFile.listFiles()`, no recursion into subfolders), and no
  broad storage/media permission is requested for any of this.
  - **New `com.mediavault.app.library` types**: `MediaFileClassifier.kt` (pure —
    extension→`MediaType`, filename→default title, unit-tested), `MediaMetadataProbe`/
    `AndroidMediaMetadataProbe` (wraps `android.media.MediaMetadataRetriever` — duration,
    resolution, a video frame or embedded audio cover art; a broad `catch (Exception)`
    at this one boundary since the retriever is documented to throw inconsistently for
    corrupt/DRM/unsupported files — the "handle unsupported formats gracefully"
    requirement made concrete), and `MediaImportRepository`/`AndroidMediaImportRepository`
    (orchestrates: takes a best-effort persistable read grant via
    `takePersistableUriPermission`, classifies, probes, persists a small cached JPEG
    thumbnail under the app's own cache dir, upserts a `MediaItemEntity` with
    `isImported = true`, `sourceDownloadTaskId = null`). No Room migration needed —
    `isImported`/`sourceDownloadTaskId` already existed on the entity, unused until now.
  - **`MediaOrigin` (`LibraryQuery.kt`)** is the single classification `MediaItemEntity`
    lives by: `DOWNLOADED` (`!isImported`), `IMPORTED` (`isImported`, no
    `sourceDownloadTaskId` — came from outside MediaVault), `SAVED_TO_GALLERY`
    (`isImported` *with* a `sourceDownloadTaskId` — a MediaVault download later moved to
    the Gallery, see below). Both the Library card badge and the Details dialog's new
    "Origin" row derive from this one function, never their own copy of the same
    `when`. `canDeleteUnderlyingFile()` (`!isImported`) is the one predicate
    `AndroidLibraryRepository.delete()` defers to — the actual safety mechanism behind
    "removing an imported item must never delete the original": only a row MediaVault
    unambiguously owns the file for gets its file/document deleted; everything else
    just loses its Library row (plus releasing MediaVault's own read grant, and
    cleaning up the cached thumbnail file it wrote).
  - **"Save to device" replaces the old single "Export"**, opening a Gallery/Files
    choice. "Save to Files" is the pre-existing `ACTION_CREATE_DOCUMENT` `exportTo()`,
    unchanged, just relabeled and reachable from the new chooser. "Save to Gallery" is
    new (`LibraryRepository.saveToGallery`) — see the 2026-08-26 storage-architecture
    decision log entry below for why it's implemented as a real move (copy into
    `MediaStore`, verify, delete the now-redundant private copy, repoint the Library
    row) for a MediaVault-private download specifically, and a plain copy for anything
    `content://`-sourced (imported or a legacy SAF download) that MediaVault doesn't
    own. Gated to API 29+ (`MediaStore.VOLUME_EXTERNAL_PRIMARY`/`RELATIVE_PATH`/
    `IS_PENDING`); below that, a clear `AppError.Unsupported` message points at Save to
    Files rather than requesting `WRITE_EXTERNAL_STORAGE`.
  - **Player unchanged** — confirmed before writing any code that `Media3PlayerEngine`
    already builds `MediaItem.fromUri()` from a plain URI string with no scheme-specific
    branching, so an imported `content://` item plays with zero player-layer changes,
    same as the pre-existing legacy-SAF-download rows already did.
  - **Verified live** (Pixel 7a): imported a single video (correct duration/resolution/
    size/thumbnail extracted from a real frame); imported a folder containing two
    videos and one audio file — all three indexed with zero skipped, correct per-file
    metadata (including an audio file's real 10:34 duration and 10 MB size); played an
    imported audio file through the real dedicated Player screen (timeline advancing,
    correct total duration); removed an imported item from Library via the three-dot
    menu and confirmed via `adb shell ls` that the original file on disk was byte-for-
    byte untouched; force-stopped and cold-relaunched and confirmed every remaining
    imported entry — and the earlier removal — persisted; deleted the underlying file
    out from under a still-listed imported item and confirmed it degraded to a red
    "File missing" badge rather than crashing or vanishing. **Not exercised this
    session**: an actual revoked SAF persistable-permission grant specifically (no
    direct `adb` mechanism to simulate it) — covered instead by the equivalent
    moved/deleted-file path, which exercises the same `fileExists()` failure-handling
    code; and "Save to Gallery" itself (no test asked for it beyond the architecture
    decision, and the physical device's Gallery app wasn't inspected post-save).

* **Player → Library navigation transition polish**: `MediaVaultNavHost`'s `NavHost` gained
  a coordinated 220ms crossfade (`enterTransition`/`exitTransition`/`popEnterTransition`/
  `popExitTransition`) applied uniformly to every route change, replacing the previous
  instant cut. This specifically fixed the Library↔Player transition: the dedicated
  Player's `VideoSurface` now constructs its `PlayerView` (`app/ui/screens/player/
  PlayerScreen.kt`) inside a `ContextThemeWrapper` applying a new
  `R.style.PlayerViewTextureView` (`res/values/styles.xml`), forcing Media3's
  `surface_type` to `texture_view` instead of the default `SurfaceView`. A `SurfaceView`
  composites on its own `SurfaceFlinger` layer outside Compose's normal draw/alpha
  pipeline — Google's own `PlayerView` docs cite this as the reason a `SurfaceView`-backed
  player can flicker/pop/lag behind an animated parent, which is exactly what made the
  Library↔Player fade look broken before this stage: the destination's background faded
  in smoothly while the video surface itself popped in/out a beat later. `TextureView` is
  a plain `View` that participates in the normal pipeline (alpha included) at a small
  extra compositing cost — the standard fix for a player embedded in an animated
  transition. No DRM/secure-surface usage exists anywhere in this codebase, so the
  `TextureView` switch has no protected-content downside here.
  - **Lifecycle/release behavior was already correct, not newly added**: Navigation-
    Compose keeps a popped `NavBackStackEntry` (and its Composable content) alive until
    its exit transition actually finishes, so `PlayerScreen`'s `DisposableEffect(Unit) {
    onDispose { viewModel.onScreenLeft() } }` — which pauses playback and persists the
    current position without releasing the engine, since the `ViewModel` can still be
    alive on a saved back-stack entry — fires only once the 220ms fade completes, not at
    the moment the user taps back. `PlayerViewModel.onCleared()` (real `ViewModelStore`
    teardown: release the engine, persist the final position via one deliberate blocking
    call) is unaffected by the animation and continues to fire after `onScreenLeft()`.
    Audio/video therefore keep running through the fade instead of cutting off mid-frame,
    a deliberate choice for a smoother transition rather than an abrupt cut the instant
    the exit animation starts.
  - **New targeted unit tests** (`PlayerViewModelTest`) covering exactly this boundary,
    which previously had no test coverage: leaving the screen pauses and persists without
    releasing the engine; clearing the `ViewModel` releases the engine and persists the
    final position; clearing with no active playback persists nothing. The clearing tests
    reach `onCleared()` via a same-module `androidx.lifecycle.ViewModelStore` (`put` +
    `clear()`) rather than calling `ViewModel.clear()` directly — that method is
    `internal` to the `lifecycle-viewmodel` module (confirmed against the pinned 2.11.0
    source), so it isn't callable from app-module test code.
  - **Verification completed in a later session, and it found a real bug**: the
    `TextureView` half of this stage broke actual video rendering (solid black in both
    embedded and fullscreen layouts, confirmed via the device's own native screenshot
    mechanism, not just `adb screencap`) and was reverted back to the default
    `SurfaceView`. `PlayerViewModelTest`'s lifecycle cases and the `NavHost` crossfade
    itself were unaffected and verified working. See the 2026-08-27 Player/Navigation/
    Downloads Stabilization entry below for the full pass.

* **Player, Navigation & Downloads UX stabilization**: a milestone fixing a batch of
  closely-related defects across Downloads, the dedicated Player, the Player tab, and
  Library, verified live end-to-end on a Pixel 7a using a real Android SDK/JDK/adb
  toolchain (Android Studio's bundled JBR, the project's own SDK install, and adb —
  located on-disk and wired into this project's local Claude Code settings rather than
  installing anything new; see this stage's own decision log entry below).
  - **Downloads**: `DownloadEngine.remove(taskId)` (new) deletes only a
    FAILED/CANCELLED/COMPLETED task's own queue row — never touches
    `mediaItemDao`/Library media, by construction (the two are different tables, and
    `remove()` doesn't reference the media DAO at all). Wired to a "Remove" button per
    task and per playlist item, with a confirmation dialog only for removing a
    COMPLETED task (the one case a user could plausibly mistake for deleting the actual
    file). Fixed a real bug where a `MERGING` (split video+audio) task matched no
    section filter at all and vanished from the list mid-merge; `MERGING` now counts as
    `Active`. `Cancelled` is now its own section, previously merged into `Failed`.
  - **Player gesture contract rewritten**: single tap only toggles controls and never
    seeks (previously, tapping the left/right thirds seeked ±10s on a single tap —
    against this milestone's explicit contract); double-tap left/right now seeks ∓10s,
    triple-tap ∓30s; long-press-to-2x unchanged. The tap-count/zone decision table
    (`resolveTapAction`/`tapZoneFor`, `ui/screens/player/PlayerGestures.kt`) is pure and
    unit-tested (10 cases) separately from the pointer-timing state machine that feeds
    it, which now counts up to 3 quick taps in the same third within the platform's own
    double-tap window before committing to an action.
  - **Player tab real watch history**: `MediaItemEntity.lastWatchedAtEpochMs` (Room
    migration 4→5, backfilled from `addedAtEpochMs` for any row already mid-playback so
    existing in-progress items don't vanish on upgrade) drives a genuine Continue
    Watching / Recently Watched split (`ui/screens/player/WatchHistory.kt`'s pure,
    unit-tested `toWatchHistorySections()`), replacing the old single-item card.
  - **Library menu narrowed by origin**: an imported/`content://` item's three-dot menu
    now hides Save to device and Rename — neither can act meaningfully on a file
    MediaVault doesn't own (Rename already silently no-ops there; Save to device is a
    copy of a file already outside MediaVault). MediaVault-owned downloads keep the
    full menu unchanged.
  - **`MediaThumbnail`** (`ui/components/AppComponents.kt`) centralizes the
    thumbnail-with-fallback block Downloads, Library, and the Player tab had each
    implemented separately.
  - **Two real defects found only by live device testing, both fixed and re-verified**:
    (1) the previous stage's `TextureView` change (above) silently broke video
    rendering entirely; (2) the new watch-history card's `Row` was missing
    `fillMaxWidth()` beneath a `weight(1f)` title column, and its thumbnail column had
    no fixed width beneath a `fillMaxWidth()` progress bar — together this starved the
    title column to zero width, rendering the remaining-time label one character per
    line instead of as text. Neither was visible from source review alone.
  - **Verified live on a physical device (Pixel 7a)**: Downloads remove (task
    disappears from its list; Library entry for the same title independently confirmed
    untouched); Library→Player→back; the Player tab showing multiple real Continue
    Watching/Recently Watched entries simultaneously; single-tap confirmed never
    seeking (position advanced only by real elapsed time); multi-tap confirmed seeking
    in the correct direction; long-press-2x confirmed via the on-screen "2.0x speed"
    bubble; portrait and landscape-fullscreen playback with real, correctly-rendered
    video; the audio-track menu against a legitimate local multi-track test fixture (2
    real audio tracks, "en"/"es", selection reflected live) rather than claiming
    untested coverage — **subtitle-track switching against the same fixture's 2 real
    subtitle tracks was verified in a later stage (2026-08-27, see §34's most recent
    entry), not this one; this entry's silence on subtitles reflected a genuine gap at
    the time, since closed**; rapid tab-switching with no stale state, clipped cards, or
    visual artifacts. 169 unit tests pass, debug APK builds and installs clean.
    **Not exercised this session**: exact ±10s/±30s seek magnitudes live (adb-driven
    tap timing isn't precise enough to isolate a seek from concurrent normal playback
    advancing the position at the same time — magnitude correctness is instead
    guaranteed by `PlayerGesturesTest`'s exact-value assertions) and Picture-in-Picture
    end-to-end — covered in a dedicated follow-up session, immediately below.
* **Picture-in-Picture, verified end-to-end on a physical device (Pixel 7a)**: opened a
  Library video, started playback, entered PiP, and confirmed the video kept rendering
  correctly in the floating window across several real minutes (checked via multiple
  screenshots showing distinct, advancing frames, not a frozen first frame). Tapping the
  PiP window restored the full player with the position correctly advanced the whole
  time it was in PiP (e.g. 3:37 → 7:45 across ~4 real minutes of continuous playback),
  and normal Library/Player-tab navigation worked immediately afterward, including the
  now-finished item correctly showing under Recently Watched with a fully-filled
  progress bar. No regressions found — no code changes were needed.

* **Three live-testing bug reports fixed: Downloads "Open" navigation, Home tab reset,
  Player back-transition pop.** All three were found during a real Pixel 7a session, not
  code review, and none needed a redesign — each was a targeted fix inside the existing
  architecture.
  - **Downloads "Open" did nothing:** `DownloadsScreen`'s `openDownloadedFile()` fired an
    `Intent.ACTION_VIEW` on the download's raw private `file://` URI, which Android 24+
    throws `FileUriExposedException` for — silently swallowed by a `runCatching`, so the
    button visibly did nothing. Deleted that function entirely rather than switching it
    to a `FileProvider` content URI, since the milestone required reusing Library's
    already-correct playback path, not a second one. `LibraryRepository` gained
    `getBySourceDownloadTaskId(taskId)` (resolves the Library row a finished download
    produced via the existing `sourceDownloadTaskId` link); `DownloadsViewModel.openInPlayer()`
    resolves that id and publishes it through a new one-shot `openMediaItemId` `StateFlow`
    (mirroring the existing `errorMessage`/`infoMessage` consume pattern), which
    `DownloadsScreen` observes and forwards to `MediaVaultNavHost`'s `player/{id}` route —
    the identical route Library already navigates to.
  - **Home didn't reset when returning to the tab:** Home is the nav graph's
    `startDestination`, and `navigateToDestination()`'s `popUpTo(graph.findStartDestination().id)`
    is exclusive by default — Home is architecturally never popped off the back stack by
    any tab switch, so its `ViewModel` (and whatever analysis result was showing) survives
    indefinitely no matter how many other tabs are visited. Fixed with a targeted
    `HomeViewModel.resetToCleanState()` (clears the analysis/UI state back to defaults,
    cancels any in-flight analysis job, but deliberately keeps the already-loaded device
    status, which doesn't go stale) invoked via `remember(Unit)` at the top of
    `HomeScreen`'s composable body — `remember` runs synchronously before the first
    `collectAsState()` read (avoiding a one-frame stale flash), and fires on every fresh
    entry because Navigation-Compose tears down and rebuilds Home's composable content on
    each tab switch even though the underlying `ViewModel` instance persists.
  - **Player → Back transition visibly popped:** root cause was the same SurfaceView
    compositing limitation already documented in the 2026-08-27 Player↔Library entry
    below — `PlayerView`'s default `SurfaceView` composites on its own `SurfaceFlinger`
    layer outside Compose's alpha pipeline, so the previous `popExitTransition =
    fadeOut(tween(220))` animated nothing on the actual video pixels: the destination
    faded in on schedule while the still-fully-opaque video sat frozen, then vanished in
    one frame the instant the 220ms exit transition elapsed and Compose disposed it.
    Fixed by changing only `popExitTransition` to `ExitTransition.None` — Player's content
    stays fully visible/unanimated for the same duration Navigation-Compose already keeps
    it composed (matching `popEnterTransition`'s 220ms), so the incoming screen's fade-in
    visually covers it before it's actually removed, leaving nothing to visibly pop.
    `TextureView` was deliberately not reintroduced (see the 2026-08-27 entry below for
    why it's avoided) — this fix touches only transition timing, not the surface type.
  - **Verified live on a physical device (Pixel 7a):** paste-URL → analyze → download →
    auto-redirect to Downloads → Open → correct Player screen with the correct resumed
    position (8:37/8:52 on "Lua Tools"); Player/Downloads → Home tab → clean default Home
    (MediaVault header, greeting, empty analyze field, Popular Sources/Quick Actions/
    Recent Activity), re-confirmed with a fresh analysis result that it doesn't linger
    after leaving and returning; Library → Player unaffected; a mid-transition screenshot
    (chained `input keyevent 4; screencap`) during Player→Back showing the destination's
    bottom nav already switched while Player's video/UI remained fully intact and opaque
    — no black/blank frame; a second clean Library→Player→Back round trip; a rapid
    tab-switching stress pass with no stale state or navigation artifacts. 175 unit tests
    pass (3 new for Downloads' `openInPlayer`/`consumeOpenInPlayer`, 3 new for
    `HomeViewModel.resetToCleanState()`), debug APK builds and installs clean.

* **Network timeout errors now classify distinctly from generic network failures.** A new
  `AppError.Timeout` sits alongside `AppError.Network` in `core:common`'s existing error
  hierarchy; `YtDlpErrorMapper.toAppError()` checks for yt-dlp's "... timed out" text before
  its generic network-failure branch and produces "Connection timed out. This source may be
  unavailable or blocked on your current network." — deliberately hedged, not asserting a
  specific cause. Found via a real analyze attempt against a URL that hung for ~20s before
  yt-dlp's own timeout fired; the app previously reported this identically to any other
  network failure. No extractor/networking/downloader logic changed — classification only.
  3 unit tests in `YtDlpErrorMapperTest` cover it.

Not yet started: torrent downloading, app lock/biometric security, source search (beyond
the Supported Sources catalog itself — §17's "tapping an item opens the appropriate
analysis/download flow" is satisfied by returning to Home, not a source-aware analyzer),
and update checking. Local media import (a user-picked file or folder, via SAF) exists
as of the 2026-08-26 stage below — deliberately *not* full-device/gallery scanning,
which this project has explicitly ruled out as a privacy matter, not just an
unimplemented one; see that stage's own entry for why.

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

### 2026-08-25 — FFmpeg added: a real `MediaProcessor` abstraction, wired via a maintained FFmpegKit fork

**Decision:** This stage lifts the FFmpeg restriction that §37's 2026-08-24 and prior
entries deliberately imposed. A new `MediaProcessor` interface (`core:domain/processing/`)
is bound to `FFmpegMediaProcessor`, backed by the
[`ffmpeg-kit-android-16KB`](https://github.com/moizhassankh/ffmpeg-kit-android-16KB)
Maven dependency (`com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`). `MediaVaultDownloadEngine`
now downloads a video-only and audio-only stream separately and hands both to
`MediaProcessor.merge()`, which always runs FFmpeg with `-c copy` — a stream-copy remux,
never a re-encode. See §34's Current Project State for the full implementation
breakdown (`DownloadOption`/`buildDownloadOptions`, the `MERGING` status, the v3→v4
migration, etc.).

**Why FFmpegKit's `arthenica/ffmpeg-kit` fork instead of the original:** the original
`arthenica/ffmpeg-kit` project (the de facto standard FFmpeg wrapper for Android/Kotlin)
was archived by its author in 2024 and its last published binaries predate Android 15's
16 KB memory-page-size requirement for native libraries, which would make it fail to
load on newer devices/App Bundle targets going forward. `moizhassankh/ffmpeg-kit-android-16KB`
republishes the same FFmpeg build recompiled with 16 KB page alignment, under the same
LGPL v3 terms as the original's LGPL package variant, so this project gets a
still-maintained artifact without changing the licensing analysis §26/`THIRD-PARTY-NOTICES.md`
already committed to for FFmpeg.

**Why `-c copy` only, never a transcode:** the milestone's requirement (and this
project's general "never silently substitute/degrade quality" principle — see the
2026-08-24 Supported Sources and playlist decisions) is to combine two
already-downloaded streams, not to re-encode them. Stream-copy is lossless and far
cheaper (seconds, not minutes, of device CPU/battery) than a real transcode would be, and
it also keeps this project's FFmpeg usage inside the LGPL-only portion of FFmpegKit —
transcoding into some codecs would require GPL-only encoders, which §26/
`THIRD-PARTY-NOTICES.md`'s existing "FFmpeg licensing" note already flagged as a decision
this project has not made and does not need to make for this feature.

**Why a new `MERGING` status instead of reusing `PROCESSING`:** `PROCESSING` already
means "finished downloading, being copied into the Library" (see the private-storage
stage). Overloading it for "FFmpeg is remuxing two files" would make `DownloadsScreen`
and process-death recovery unable to tell the two apart, so a new, distinct terminal-
adjacent status was added to the existing enum instead.

**Consequence — verified live on a physical device (Pixel 7a):** the on-device pass this
entry originally flagged as outstanding has since been completed — real split-stream
source, real FFmpeg merge (confirmed via logcat and by parsing the resulting file's MP4
box structure for both a video and an audio track), and real playback of the merged file
from the Library through the Media3 player, with a genuine `AudioTrack` audio session
confirmed via `dumpsys audio`. See §34's Current Project State for the full walkthrough.

---

### 2026-08-25 — Player rebuilt as a dedicated immersive screen, split from the Player tab

**Decision:** The Player tab no longer *is* the player. It now renders a lightweight
`PlayerHubScreen` (a "continue watching" card, no `PlayerEngine` created) inside the
normal five-tab layout; opening either that card or a Library item instead navigates to
a separate `player/{mediaItemId}` route that `MediaVaultNavHost` renders with zero
`Scaffold` chrome — no bottom nav, no shared inset padding. The dedicated screen sizes
its video surface to the source's real aspect ratio (via a new `videoAspectRatio` on
`PlaybackState`) rather than assuming 16:9, and fullscreen now overlays floating,
auto-hiding controls over the full screen instead of squeezing the video into a smaller
box above a still-visible top bar.

**Why:** The milestone's brief was explicit that a five-tab content page and a dedicated
playback experience are different things, and the previous design conflated them — the
Player tab and a Library item opened the literal same composable, and merely *visiting*
the tab silently started an `ExoPlayer` instance and began playing audio in the
background, which is a real behavior bug, not just a UX preference. Splitting the two
fixes that by construction (the hub never touches `PlayerEngine`), and matches how every
mainstream video app treats "now playing" (a small resumable card) versus "watching"
(a full, chrome-free surface) as separate screens.

**Consequence — two real layout bugs found and fixed during this stage's own device
testing:** see §34's Current Project State for the full root-cause writeup. In short: (1)
an unconditional black background on the screen's root `Box` bled into whatever vertical
space its non-`weight`-filled `Column` left unused in embedded mode, relocating rather
than fixing the "giant black empty area" this stage was meant to eliminate; (2)
`Scaffold`'s `innerPadding` doesn't reactively shrink when a screen imperatively hides
system bars, leaving a persistent light-gray strip where the status bar used to be in
fullscreen. Both were caught live on a Pixel 7a within the same session, fixed, and
re-verified with screenshots before this entry was written — see the Current Project
State bullet for exactly what "verified live" and "not exercised live" mean for this
stage (a 9:16 source, multi-audio-track switching, and embedded subtitles had no
available test file this session and were not exercised on-device, unlike everything
else in the checklist).

---

### 2026-08-25 — Player popup anchoring, fullscreen rotation, and gesture polish

**Decision:** This stage is a scoped follow-up to the Player Redesign entry above,
targeting five specific UX gaps rather than further architecture — wrapping each popup
menu's trigger and `DropdownMenu` in a shared `Box` so it anchors to the actual button
pressed, locking device orientation to landscape when fullscreen is entered on
landscape/square content (restoring free rotation on exit), adding YouTube-style
region-tap seek (-10s/+10s) and hold-for-2x gestures directly on the video surface, and
tightening control spacing. No changes were made to `DownloadEngine`, `ExtractorEngine`,
FFmpeg, or the Library data model — this stage touched only `app/ui/screens/player/`,
`PlayerViewModel`, and `MainActivity`'s already-existing PiP-driven `configChanges`.

**Why the popup anchoring bug happened:** `DropdownMenu` positions its `Popup` relative
to whatever composable node hosts it, not "the sibling that was tapped." With each
`IconButton`/`DropdownMenu` pair declared as loose siblings inside one shared scrollable
`Row`, every menu's popup anchored to a position determined by the Row's own layout
rather than the specific icon the user pressed — which one testing session generalized
as "everything opens at a fixed corner." Wrapping trigger and menu together in one `Box`
each is the standard, documented fix, confirmed live across three different icon
positions in the same control row (Speed, Aspect-ratio, Sleep-timer each opened directly
under their own icon, not a shared position).

**Why orientation-locking instead of leaving rotation to the OS default:** the milestone
asked for fullscreen to "behave correctly for both portrait and landscape" and for
exiting fullscreen to "restore the previous orientation/state cleanly" — read as a
request for the same auto-rotate-to-landscape behavior mainstream video apps already
have, not just tolerating whatever orientation the device happens to be in. Portrait
content is deliberately excluded from the lock (forcing a 9:16 video into a landscape
frame would be wrong), matching how `VideoArea`'s own aspect-ratio branching already
treats portrait content as "use available height in place" rather than "always
letterbox like landscape does."

**Why gestures resolve from one `awaitEachGesture` rather than two separate detectors:**
a tap detector and a long-press detector layered on the same pointer input region can
both react to the same touch stream unless one explicitly wins, risking a seek AND a
speed-boost firing off the same finger-down. Racing `waitForUpOrCancellation()` against
the long-press timeout inside a single gesture loop makes the two outcomes structurally
exclusive — confirmed live: holding the video engaged 2x and only 2x (no stray seek),
and quick left/right taps seeked exactly 10 seconds with no speed-boost side effect.

**Consequence — verified live on a physical device (Pixel 7a), same session:** every
item above was confirmed working with real taps/holds and real device rotation — see
§34's Current Project State for the full walkthrough, including the two "the tap missed
the actual button" false alarms this session's own testing hit (auto-hidden fullscreen
controls being tapped after they'd already faded, and a scale-factor slip converting a
landscape screenshot's on-screen position to a device coordinate) that turned out to be
testing-methodology errors, not app bugs, once re-tested with correct timing/coordinates.

---

### 2026-08-26 — Format selection redesigned into Video/Audio sections with a persistent Download bar; `NetworkPolicyManager` moved to enqueue-time

**Decision:** The single-item format picker's flat `DownloadOption` list is now split into
Video/Audio sections (plus a defensive "Other" catch-all), each row showing resolution/
fps/container/codec/final-size/audio-availability (Video) or format/codec/bitrate/size
(Audio) instead of one packed summary string. A persistent bottom bar replaced the
inline Download/Queue buttons for both the single-item and playlist-setup flows.
`NetworkPolicyManager.evaluate()` — already the sole owner of block/warn/budget logic —
is now also called from `HomeViewModel` before `DownloadEngine.enqueue()`/
`enqueuePlaylist()`, in addition to `MediaVaultDownloadEngine.runDownload()`'s own
existing check at actual-transfer time.

**Why sectioned rows instead of one summary string:** the milestone's brief was that a
long, undifferentiated list of formats is hard to scan, and that "audio availability"
specifically must never be ambiguous or omitted — auditing the old `downloadOptionSummary()`
while rewriting it surfaced a real bug (see below), which sectioning and per-field rows
made impossible to reintroduce (each field is now its own explicit check, not folded
into one string-building `when`).

**Why `MediaFormat.bitrateKbps` is a new field rather than derived at display time:**
yt-dlp reports audio bitrate (`abr`, or `tbr` as a fallback) per-format, and nothing in
the domain model carried it before this stage — display code has nothing to derive a
bitrate *from* without the mapper populating it first. Populated only from what yt-dlp
actually reports, consistent with `heightPx`/`widthPx`/`languageCode`'s existing
"never guessed" precedent from the FFmpeg-merge stage.

**Why the persistent bar sits in a `Box` outside the `LazyColumn` instead of a "sticky
header/footer" list item:** Compose's `LazyColumn` has no built-in sticky-footer
primitive (only sticky *headers*), and simulating one by re-measuring scroll offset is
substantially more fragile than the standard pattern of overlaying a fixed-position
composable in a `Box` alongside the scrolling list — the same structural choice already
used for the dedicated Player screen's floating controls.

**Why `NetworkPolicyManager` is called an *additional* time at enqueue rather than
moved from `runDownload()`:** the milestone's requirement is that the user hears about
a mobile-data problem "before enqueueing," i.e. immediately after tapping Download —
not merely "before the bytes start moving," which is what the existing engine-side
check already guarantees. Removing the engine-side check would leave a task silently
sitting `QUEUED` with no upfront warning if it were ever enqueued through a path other
than `HomeViewModel` (e.g. a retry). Calling `evaluate()` twice for the same task is
cheap (no I/O beyond reading the day's already-tracked usage counter) and keeps
`NetworkPolicyManager` itself the single place the actual policy logic lives, per its
own KDoc contract — `HomeViewModel` only reacts to the `NetworkPolicyDecision` it
returns, never re-derives budget/limit math.

**Why a `Warn` decision now requires an explicit "Download anyway" confirmation
instead of proceeding with just an info message (the previous, never-wired-up design
implied by the unused `home_network_warn_*` string resources already sitting in
`strings.xml`):** a `Warn` means "this may exceed today's remaining budget" — a real
risk, not a neutral status update. Auto-proceeding would violate this project's
existing "never silently downgrade or proceed past a risk" principle (already applied
to quality selection); requiring a tap makes the risk something the user actually saw
and accepted, matching what the pre-existing but dormant string resources were
evidently designed for.

**Consequence — a real bug found and fixed during this stage, not a new feature:** the
old `downloadOptionSummary()` checked only `DownloadOption.audioFormat` to decide
whether to show "video only" — but a muxed direct option (already containing its own
audio) never sets that field (it's reserved for a separately-paired audio track), so
the old code labeled *every* muxed format "video only" regardless of whether it
actually had audio. The redesign's `audioAvailabilityLabel()` checks
`videoFormat.hasAudio` directly, fixing this. No test previously caught it because
`downloadOptionSummary()` itself had no unit test; the new formatting functions do
(`HomeFormattingTest`).

**Consequence — verified live on a physical device (Pixel 7a):** analyzed a real
YouTube source (Big Buck Bunny 60fps 4K) and confirmed every UI requirement above by
screenshot at each step, then confirmed end-to-end correctness — not just UI
appearance — by pulling and querying the app's own Room database after tapping
Download, finding the resulting task at `COMPLETED` status with the exact format id
selected on screen. See §34's Current Project State for the full walkthrough and what
was not exercised live this session (the playlist bar; an actual Block/Warn/
QueueForWifi decision).

---

### 2026-08-26 — Global theme system: Light/Dark/System, persisted, applied via one shared `MaterialTheme.colorScheme`

**Decision:** Settings gained a real Light/Dark/System default theme picker
(persisted via a new `ThemeStore`/DataStore, mirroring `NetworkPolicyStore`'s
pattern) that drives a single `MediaVaultTheme(themeMode)` composable at the app
root. A new true-dark/AMOLED-friendly `ColorScheme` was added alongside the existing
approved light/blue one; no screen or component needed its own changes to support it.

**Why no per-screen changes were needed:** every screen/component already read
colors from `MaterialTheme.colorScheme` rather than hard-coded `Color(...)` literals
— confirmed by auditing the app before writing any theme code, per this stage's own
"reuse centralized tokens, no scattered hard-coded colors" requirement. The only
hard-coded colors found anywhere outside `ui/theme/` are in the dedicated Player
screen's video canvas (fullscreen black letterboxing, gesture-bubble scrims), which
are correctly theme-*independent* by design — a video surface and its legibility
overlays don't change with the app's light/dark preference in any mainstream player,
and the Player's own embedded chrome around that canvas was already
`MaterialTheme.colorScheme`-driven. This is why swapping which `ColorScheme` instance
`MediaVaultTheme` hands to `MaterialTheme` was sufficient to re-theme Home, Downloads,
Library, Settings, Supported Sources, dialogs, menus, bottom navigation, and the
format-selection UI/download bar all at once.

**Why a dark scheme is a new user choice, not a return to the original AMOLED
theme:** §37's 2026-08-24 entry replaced an AMOLED-dark default with the approved
light/blue identity as a *product* decision (the owner reviewed and approved
light/blue specifically). This stage doesn't revisit that call — light/blue stays the
default and the approved identity — it only adds Dark as an explicit, opt-in
alternative alongside System, per this stage's own requirement.

**Why flash prevention combines a `values-night` resource with a runtime
`window.setBackgroundDrawable`/`enableEdgeToEdge` call rather than adopting the
`androidx.core.splashscreen` library:** the milestone's scope was explicitly bounded
("no unnecessary refactoring," reuse the existing settings architecture) and a full
splash-screen integration wasn't requested. The resource-qualifier variant handles the
zero-runtime-cost, most-common case (SYSTEM mode, which is the shipped default) at
the native pre-Compose frame; a single synchronous DataStore read in
`MainActivity.onCreate` before `setContent` (the same small-value trade-off already
accepted elsewhere for tiny startup preferences) then corrects the window
background/system-bar style for the remaining case — an explicit Light/Dark override
that disagrees with the system setting — before the first Compose frame draws.

**Consequence — verified live on a physical device (Pixel 7a):** cold-launched with
the system in dark mode and confirmed SYSTEM mode followed it; switched to Light and
Dark from Settings and confirmed instant, correct re-theming across Home/Downloads/
the bottom nav/Settings itself; confirmed the choice survived a force-stop and cold
relaunch (Dark stayed selected, not reverted to System); confirmed the Downloads
screen's existing size/speed/ETA display was unregressed in dark mode. See §34's
Current Project State for the full walkthrough and what wasn't exercised this session
(Player/Library/Supported Sources dark rendering specifically; a live speed/ETA line
in dark mode) — out of scope per this stage's own testing instructions.

---

### 2026-08-26 — Local media import stays SAF-explicit, never a gallery/device scan; "Save to Gallery" moves rather than duplicates a MediaVault-owned file

**Decision:** This milestone started from a broader "local media import & library
scanning" brief and was deliberately narrowed mid-stream to a privacy-first shape: the
Library only ever indexes (a) what MediaVault itself downloaded, or (b) a file/folder
the user explicitly picked through `ACTION_OPEN_DOCUMENT`/`ACTION_OPEN_DOCUMENT_TREE`.
No broad gallery/media permission (`READ_MEDIA_VIDEO`/`READ_MEDIA_AUDIO`, let alone
`MANAGE_EXTERNAL_STORAGE`) is requested anywhere, and no code path enumerates the
device's storage beyond a single user-picked folder's own direct children.

**Why "Save to Gallery" doesn't just copy the file, for a MediaVault-owned download:**
researched before writing any storage code, since the product requirement was
explicitly "one physical file wherever technically possible." Android's scoped storage
model (API 29+) gives apps no public zero-copy way to hand a private file to
`MediaStore` — every write into a MediaStore-backed entry is mediated by the separate
MediaProvider process via `ContentResolver.insert()` + a real `OutputStream`, and the
only bypass (`MANAGE_EXTERNAL_STORAGE`) is exactly the kind of broad access this stage
rules out. Given that hard constraint, the most storage-efficient architecture actually
achievable is copy-then-delete-source: write the Gallery copy, verify it, delete the
now-redundant private copy, and repoint the Library row's `mediaUri` at the new
`content://` URI. The *steady state* is one physical file; the copy step itself is an
unavoidable, momentary cost of crossing that OS-level boundary, not a shortcut taken
without checking for a better one.

**Why that move only ever applies to a MediaVault-private (`file://`, non-imported)
download, never a `content://` source:** MediaVault doesn't own an imported or legacy-
SAF-downloaded document — deleting it as a side effect of an unrelated "also save a
copy to Gallery" action would be exactly the kind of surprising data loss this
project's "never delete an external file the user didn't ask to delete" principle
exists to prevent (see §37's original SAF-based-download entries and this stage's own
`canDeleteUnderlyingFile()`/`MediaOrigin`). "Save to Gallery" on such an item stays a
plain copy, identical in spirit to "Save to Files."

**Why a moved-to-Gallery row is marked `isImported = true` rather than getting a new
dedicated flag:** the delete-safety rule it needs — "don't delete the file when this
Library row is removed" — is identical to an imported item's rule, and `isImported`
already drives exactly that check. Reusing it avoids a Room migration and a second
code path with the same meaning; `MediaOrigin` (keyed off `isImported` **and**
whether `sourceDownloadTaskId` survived) is what keeps "saved to Gallery" and
"imported from outside MediaVault" distinguishable in the UI despite sharing the one
underlying boolean.

**Consequence — verified live on a physical device (Pixel 7a):** see §34's Current
Project State for the full device-testing walkthrough of the import side of this
stage. "Save to Gallery" itself was implemented and reasoned through against the
storage semantics above but not exercised live this session — flagged there, not
glossed over.

---

### 2026-08-27 — Player↔Library transition: `TextureView` over `SurfaceView`, plus a coordinated `NavHost` crossfade

**Decision:** A scoped follow-up to the Player Redesign/polish entries above, fixing one
specific symptom rather than touching player architecture — `MediaVaultNavHost`'s
`NavHost` now applies one coordinated 220ms fade (`enterTransition`/`exitTransition`/
`popEnterTransition`/`popExitTransition`) to every route change instead of an instant
cut, and `PlayerScreen.VideoSurface` constructs its `PlayerView` with a themed
`ContextThemeWrapper` (`R.style.PlayerViewTextureView`, new `res/values/styles.xml`)
forcing `surface_type="texture_view"`. No changes to `PlayerViewModel`'s playback,
position-persistence, or release logic — those were already correct and are now covered
by new targeted unit tests, not new behavior.

**Why `SurfaceView` was the actual bug, not the fade itself:** `PlayerView` constructed
with no XML attrs (the constructor `Media3PlayerEngine`/`PlayerScreen` already used)
defaults to a `SurfaceView`, which is composited by `SurfaceFlinger` on its own layer,
outside Compose's normal draw/alpha pipeline — Google's own `PlayerView` documentation
names this as the cause of flicker/pop/lag behind an animated or transitioning parent.
Adding the crossfade alone would have faded every other pixel on screen while the video
surface itself kept popping in and out a frame behind, which is what "no black/blank
frame" and "player surface and navigation transition stay synchronized" were both
pointing at. `TextureView` is a plain `View` that participates in the ordinary draw
pipeline (alpha/animation included) at a small extra compositing cost — the standard,
documented fix for a player embedded in an animated transition, and safe here since no
part of this codebase uses DRM/secure-surface playback. **This reasoning turned out
correct in theory but wrong in practice** — see the Consequence note below: it broke
real frame rendering outright once tested live, for reasons not fully root-caused, and
was reverted.

**Why lifecycle/release behavior needed no changes:** Navigation-Compose keeps a popped
`NavBackStackEntry`'s content (and its `ViewModel`) alive until its own exit transition
finishes, so `PlayerScreen`'s `onDispose { viewModel.onScreenLeft() }` (pause + persist
position, deliberately *not* release — the `ViewModel` can still be alive on a saved
back-stack entry) and `PlayerViewModel.onCleared()` (release the engine, persist the
final position) already fire in the right order relative to the new fade: audio/video
keep running through the 220ms transition and only stop once it actually completes,
rather than cutting off the instant back is pressed. This was verified by reading
Navigation-Compose's animated-`NavHost` entry-lifecycle contract, not by a device test.

**Why the new tests reach `onCleared()` through a `ViewModelStore` instead of calling
`.clear()` directly:** `androidx.lifecycle.ViewModel.clear()` is `internal` to the
`lifecycle-viewmodel` module (confirmed against this project's pinned 2.11.0 source),
so app-module test code cannot call it directly. Putting the `ViewModel` into a plain
`androidx.lifecycle.ViewModelStore()` and calling that store's own public `clear()` —
which internally calls `viewModel.clear()` for everything it holds — is the standard,
same-module-agnostic way to exercise `onCleared()` from a JVM unit test.

**Consequence — the `TextureView` half of this was wrong, found by later device testing:**
this stage's own verification was deferred (no device, no JDK/SDK available in that
session — everything above was checked by manual source review only, including fetching
the exact pinned `lifecycle-viewmodel:2.11.0` source to confirm `clear()`'s visibility
before writing a test against it). Once a Pixel 7a and a real toolchain became available,
live testing found the `TextureView` change was silently breaking real video rendering:
the video area came up solid black — on every source tried, in both embedded and
fullscreen layouts, confirmed with the device's own native screenshot mechanism (not an
`adb screencap` artifact) — while audio and position continued advancing normally
underneath. Reverted to the default `PlayerView(context)` (`SurfaceView`) construction;
see the 2026-08-27 Player/Navigation/Downloads Stabilization entry below for the full
device-verification pass. The `NavHost` crossfade itself was unaffected and is unchanged.
The exact mechanism of the `TextureView` failure was not root-caused beyond this — the
known-working default was restored rather than debugging the broken path further, since
correct rendering matters more than the original cosmetic pop it was chasing.

---

### 2026-08-27 — Player, Navigation & Downloads UX stabilization; real device toolchain wired in

**Decision:** Rather than assuming the previous stage's deferred verification was fine,
this stage located the Android toolchain already installed on this machine (Android
Studio's bundled JBR at a non-default path, its SDK, and `adb`) and wired
`JAVA_HOME`/`ANDROID_HOME`/`ANDROID_SDK_ROOT` into this project's own
`.claude/settings.local.json` (git-ignored, machine-specific) rather than installing
anything new or touching global Windows environment variables. That unblocked actually
building, testing, and installing on the connected Pixel 7a — which is what surfaced the
`TextureView` regression above; source review alone had not. With real verification
available, this stage then fixed a batch of Downloads/Player/Player-tab/Library defects
in the same pass — see §34's Current Project State for the itemized list and the full
device-verification results.

**Why the toolchain was wired into project-local Claude Code settings, not global
Windows env vars:** explicit instruction to avoid touching global/user Windows
environment variables unnecessarily. `.claude/settings.local.json`'s `env` block scopes
`JAVA_HOME`/`ANDROID_HOME`/`ANDROID_SDK_ROOT` to this project's Claude Code sessions only
— every other application on the machine is unaffected. `PATH` was deliberately left
alone: a single literal value can't safely extend `PATH` for both the Bash tool (Git
Bash) and the PowerShell tool at once, and if the underlying mechanism replaces rather
than merges the variable, it would silently break every other command-line tool for
every future session in this project. `JAVA_HOME`/`ANDROID_HOME` alone are sufficient —
they're the actual mechanism Gradle's daemon-JVM criteria and the Android Gradle Plugin
look for, confirmed by `gradlew -v` reporting the correct launcher/daemon JVM with no
network fetch.

**Why the gesture rewrite is a bigger change than "add double/triple tap":** the
previous single-tap-seeks-on-left/right-third behavior was in direct conflict with this
stage's explicit contract ("single tap = show/hide controls only, never seek"), so this
wasn't additive — the entire tap-resolution path in `VideoArea`'s `awaitEachGesture`
block was rewritten. Counting a 2nd/3rd tap requires waiting out the platform's
double-tap window after every tap before committing to an action (otherwise a double or
triple tap could never be distinguished from a single one), which is why long-press
detection still has to win immediately on the *first* tap — a real long-press must never
be delayed behind tap-counting logic for taps that haven't happened yet.

**Why Continue Watching/Recently Watched needed a schema migration, not just new
ViewModel logic:** there was no existing signal for "when was this last watched" —
`lastPlaybackPositionMs` records *where*, not *when*. Migration 4→5 adds
`lastWatchedAtEpochMs`, stamped by the same `LibraryRepository.updatePlaybackPosition`
call every playback session already makes. The migration backfills it from
`addedAtEpochMs` for any row already mid-playback specifically so a user's existing
in-progress items don't silently vanish from Continue Watching the moment they update —
confirmed this would otherwise happen by installing over an existing v4 database with
a "Lua Tools..." item genuinely in progress.

**Consequence — verified live on a physical device (Pixel 7a), including two defects
device-testing itself surfaced and this session fixed:** see §34's Current Project State
for the itemized pass/not-exercised list. Both newly-found defects (the `TextureView`
video-rendering break, and a `Row`/`weight`/`fillMaxWidth` layout bug that rendered the
watch-history remaining-time label one character per line) were confirmed fixed by
rebuilding, reinstalling, and re-screenshotting on the same device before this stage was
called done — neither fix was accepted on code-review confidence alone.

---

### 2026-08-27 — Downloads "Open", Home tab reset, and Player back-transition pop: three live-testing defects, three targeted fixes

**Decision:** No architecture changed. Each of the three bugs reported from live Pixel
7a testing got the smallest fix that reused an already-correct existing path, rather than
a new one: Downloads "Open" now resolves the Library row a download produced and
navigates through the same `player/{id}` route Library uses (see §34's Current Project
State bullet for the full mechanism); Home gained a `resetToCleanState()` call driven by
`remember(Unit)`; Player's `popExitTransition` changed from `fadeOut()` to
`ExitTransition.None`.

**Why Downloads got its own resolver method instead of just constructing a
`FileProvider` URI and fixing the existing `Intent.ACTION_VIEW` call:** the milestone was
explicit that a second playback implementation must not be created — Library's in-app
Player route was already correct (position persistence, format handling, everything);
the bug was that Downloads used a completely different, broken mechanism (an external
view `Intent`) to reach the same file. Reusing the in-app route by resolving the
Library row id is not a smaller version of the same fix, it's fixing the actual
architectural mistake — Downloads shouldn't be handing playback off to another app at all.

**Why Home's fix lives in `remember(Unit)` inside the composable rather than in
`navigateToDestination()`'s `popUpTo` call:** the tempting alternative — making Home
actually poppable so a fresh instance is created each visit — would mean giving up
`popUpTo`'s `saveState`/`restoreState` symmetry that every other tab relies on, or
special-casing Home out of that shared navigation helper. Both are larger, riskier
changes to a function every tab depends on, for a bug that's entirely local to Home's own
stale state. A composable-scoped reset changes nothing about how any other tab is
reached or restored.

**Why `ExitTransition.None` and not, say, a longer/shorter `fadeOut` duration or a
different curve:** the root problem isn't timing, it's that `fadeOut()` was animating an
alpha value the `SurfaceView` ignores entirely — no duration or easing choice fixes an
animation applied to the wrong pipeline. `ExitTransition.None` sidesteps the pipeline
mismatch by not animating Player's exit at all, letting the *destination's* fade-in (which
Compose does control) be the only thing the user perceives, which was already proven
correct in the original 2026-08-27 Player↔Library crossfade decision below.

**Consequence — verified live on a physical device (Pixel 7a):** all three flows
re-tested end-to-end after the fixes — Download→Downloads→Open→Player with correct
resumed position, Player/Downloads→Home showing the clean default state (including after
a fresh analysis result, to confirm it doesn't linger), and a mid-transition screenshot of
Player→Back showing no black frame or abrupt pop — plus Library→Player confirmed
unregressed and a rapid tab-switching stress pass with no artifacts. 175 unit tests pass,
debug APK builds and installs clean. See §34's Current Project State for the full
walkthrough.

---

### 2026-08-27 — `ExitTransition.None` scoped to the Player pop only; every other route's pop-exit restored to `fadeOut()`

**Decision:** The entry immediately above set `popExitTransition = { ExitTransition.None }` at
the `NavHost` level, applying to *every* route's pop, not just Player's. That was fine for
Player (a `SurfaceView`, immune to Compose alpha) but wrong for ordinary Compose screens: Sources
-> Source Details -> Back showed Source Details' text/icons/button alpha-blended together with
the reappearing Sources list for the full transition, since the exiting screen never faded while
the destination faded in on top of it. `popExitTransition` is now conditional — `initialState.
destination.hierarchy.any { it.route == PLAYER_ITEM_ROUTE }` selects `ExitTransition.None`;
every other pop gets the same `fadeOut(tween(220))` every other transition in the app already
uses.

**Why this wasn't caught when the Player-pop fix shipped:** that stage's live verification
exercised exactly the flows its own bug report named (Player -> Back, Library -> Player -> Back,
rapid tab switching) — none of which pop *out of* a non-Player route, so the global scope of the
`popExitTransition` override had no visible symptom in any of those tests. The bug only surfaces
on a pop out of a different, ordinary Compose screen, which Source Details' own bug report
happened to be the first task to actually exercise.

**Consequence — verified live on a physical device (Pixel 7a):** reproduced the exact reported
repro (search "aeon" -> open Aeon's detail page -> Back) both before and after the fix; after,
Back settles cleanly into the Sources list with the search query and filtered results intact,
no ghosted text/icons/buttons. Repeated three rapid open/back cycles in a row with the same
clean result each time. Player -> Back (the original fix this entry corrects) re-verified
unregressed.

---

### 2026-08-27 — Settings gained Share MediaVault, a "Star this project" call-to-action, and a real centrally-configured feedback email

**Decision:** Three small, purely voluntary product actions were added to Settings' existing
About / Feedback & Contact sections (no new top-level Settings section, per this milestone's
"don't clutter Settings" requirement): **Share MediaVault** (About) builds a plain-text
`ACTION_SEND` message naming the GitHub repository URL and hands it to the system share sheet;
**Star this project on GitHub** (About) is a worded call-to-action that opens the same repository
URL in the browser — same mechanism `openExternalUrl` already used for "View on GitHub", just a
second row with different, explicit wording; **Send Feedback** (Feedback & Contact) now targets a
real, centrally-configured address (`AppConfig.FEEDBACK_EMAIL`) via `ACTION_SENDTO` with a bare
`mailto:` `Uri` and `EXTRA_EMAIL`/`EXTRA_SUBJECT`/`EXTRA_TEXT` extras — pre-filling app version,
device (`Build.MANUFACTURER`/`MODEL`), and Android version (`Build.VERSION.RELEASE`) into the
body — falling back to an inline "no email app found" row with a copy-to-clipboard action
(reusing the same copy-confirmation pattern `SupportSection`'s UPI-ID copy already uses) when no
app resolves the intent.

**Why `AppConfig.supportEmail: String?` became `AppConfig.FEEDBACK_EMAIL: String` (non-null)
instead of just changing its value:** the nullable type existed specifically to model "no address
is configured yet, fall back to GitHub Issues" — a real address is now configured, so the
fallback that matters changed from *no address exists* to *the address exists but no email app is
installed to send it*. Modeling that as a `String?` null-check would have been the wrong branch
firing for the wrong reason; the fallback now lives where it actually applies, inside the
`ACTION_SENDTO` launch's failure path, and it never lost the GitHub Issues route — that stayed
as a second, permanent action in the same section rather than a null-only fallback.

**Why the mailto intent uses `data = Uri.parse("mailto:")` with `EXTRA_EMAIL` rather than
`Uri.parse("mailto:$email?subject=...&body=...")`:** encoding the recipient into the URI query
string is the more common pattern in this codebase's style but is also the less reliable one
across real email clients — some mis-parse a `?subject=&body=` query, and a couple ignore it
entirely. Passing recipient/subject/body as `Intent` extras against a bare `mailto:` scheme is
the combination Android's own developer guidance calls out as most broadly compatible, and it's
what was chosen here.

**Why "Share MediaVault" doesn't claim to star the repository automatically:** the milestone was
explicit that the app must never claim to auto-star GitHub — the share message asks the recipient
to consider starring and giving feedback themselves, and `shareMediaVault()`'s only action is
handing plain text to the OS share sheet; nothing in this app can act on the user's GitHub account.

**Consequence:** `:app:compileDebugKotlin` and the affected unit tests (`PlayerViewModelTest`,
`MarkdownLineTest`) pass — none of the touched files have existing automated Compose-UI coverage,
consistent with the rest of Settings.

**Update — verified live on a physical device (Pixel 7a):** a user testing this stage's build
reported "Share MediaVault" was not visible in About at all. Investigation found no source-level
bug — `AboutSection` renders the row unconditionally, no build flavor/`BuildConfig` flag/resource
overlay hides it, `Icons.Default.Share` resolves fine, and the Settings tab itself is always
reachable. The actual cause: the only debug APK on disk (`app/build/outputs/apk/debug/app-debug.apk`)
had last been built at 17:02:54 that day — before either of this milestone's two commits — and
`adb shell dumpsys package` confirmed the device's installed app matched that same stale build
(`lastUpdateTime` 17:03:30). The device had simply never run a build containing this code. A fresh
`:app:assembleDebug` + `adb install -r` resolved it with no source changes: Settings → About →
"Share MediaVault" is now visible (third row, after "View on GitHub" and "Star this project on
GitHub"); tapping it opens the standard Android share sheet ("Sharing text") offering real targets
(WhatsApp, Gmail, Drive, contacts, etc.); the shared text correctly begins with the explanatory
message and ends with `https://github.com/hereisjayesh-coder/MediaVault`; no GitHub action fires
automatically — the sheet only ever hands text to whichever app the user picks. The Star action's
and feedback email intent's on-device behavior were not re-exercised in this pass (out of scope
for this report) but rely on the same `openExternalUrl`/`ACTION_SENDTO` mechanisms already
unit-verified at the source level.

---

**END OF MASTER SPECIFICATION**
