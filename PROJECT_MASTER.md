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

_Last updated: 2026-08-24, after the real DownloadEngine stage._

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

Not yet started: playlist downloading (queuing multiple `DownloadTask`s — the domain
model already carries `playlistContext`/`sourceMediaId` for this), media
processing/FFmpeg (still deliberately avoided — see above), torrent downloading, media
playback, library scanning, supported-source index, and update checking.

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

**END OF MASTER SPECIFICATION**
