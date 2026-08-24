# MediaVault Project Master Specification

## 1. Project Identity

**Project Name:** MediaVault
**Planned GitHub Repository:** `mediavault-android`
**Project Type:** Open-source Android application
**Distribution:** GitHub Releases / direct APK distribution
**Primary UI:** Native Android, Kotlin, Jetpack Compose
**Design:** Minimal, black/AMOLED, clean, no advertisements

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

_Last updated: 2026-08-24, after the foundation-stage commit._

* Multi-module Gradle project created: `app`, `core:model`, `core:common`,
  `core:domain`, `core:database` (Kotlin, Jetpack Compose, Material 3, AGP 9.3.2,
  Kotlin 2.4.10).
* Minimal AMOLED-black Compose theme, bottom-navigation skeleton, and placeholder
  screens (Home, Downloads, Library, Player, Settings) implemented.
* Room database foundation in place (`download_tasks`, `media_items` tables).
* Core engine interfaces defined in `core/domain`: `ExtractorEngine`, `DownloadEngine`,
  `TorrentEngine`, `NetworkPolicyManager`, `UpdateManager`, `PlayerEngine`. No concrete
  implementations exist yet — no yt-dlp, FFmpeg, or libtorrent integration.
- Hilt wired in; Room database is the first provided dependency.
* Open-source repo docs written: README, LICENSE, PRIVACY, TERMS, CONTRIBUTING,
  CHANGELOG, THIRD-PARTY-NOTICES.
* `gradlew build` succeeds, unit tests pass, and the debug APK has been installed and
  launched on a physical device (Pixel 7a) with working bottom-nav.
* Git repository initialized locally; first commit made. GitHub remote not yet created.

Not yet started: extraction, media processing, torrent downloading, network policy
logic, media playback, library scanning, supported-source index, update checking, and
any ViewModels/ persistence wiring beyond the Room schema itself.

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

**END OF MASTER SPECIFICATION**
