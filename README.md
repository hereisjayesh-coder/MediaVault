# MediaVault

MediaVault is an open-source, local-first Android application for analyzing,
downloading, organizing, and playing media — with intelligent mobile-data management
and torrent/magnet support — built around clean, swappable engine abstractions rather
than a hard dependency on any one backend.

This repository is under active early development. See [CHANGELOG.md](CHANGELOG.md)
for what currently exists versus what is planned.

## Status: pre-release

Analysis, download, media processing, local library, and playback are all implemented
and wired end-to-end: pasting a URL on the Home screen runs it through `ExtractorEngine`
(`YtDlpExtractorEngine` and `InstaloaderExtractorEngine`, composed behind
`CompositeExtractorEngine`) for real metadata, formats, and tracks; `DownloadEngine`
queues, transfers, pauses/resumes/cancels/retries, and (for multi-audio-track sources)
remuxes the result via FFmpegKit; completed downloads land in a local Library with a
built-in Media3 player (multi-audio, subtitles, PiP, gesture controls). Mobile-data
policy, a supported-sources index, and an optional PIN/biometric App Lock are also
implemented. Torrent/magnet support (`TorrentEngine`) is the one product goal below
still not wired up. See [Project limitations](#project-limitations) below, and
[CHANGELOG.md](CHANGELOG.md) for the detailed, dated history of what was built and
verified.

## Product goals

- Analyze and download media from a broad range of sources through a modular
  extraction engine, without the app being tightly coupled to any specific extractor
  implementation.
- Give users real control over mobile-data usage: per-download limits, a daily budget,
  actual transferred-byte tracking, and smart quality recommendations that respect the
  remaining budget.
- Provide a genuine local media library and built-in player (multi-audio, subtitles,
  playback resume, PiP) — not just a downloader.
- Support magnet links and `.torrent` files through a dedicated torrent engine.
- Stay local-first: no ads, no required accounts, no unnecessary server infrastructure.

## Architecture

MediaVault follows Clean Architecture / MVVM, split into Gradle modules so the app and
UI layers can only depend on stable interfaces, not concrete backends:

```
app                     Compose UI, navigation, Hilt wiring, screens, view models
core/model              Pure Kotlin data models shared across modules (no Android deps)
core/common             Cross-cutting utilities (dispatcher provider, result types)
core/domain             The engine interfaces described below (pure Kotlin)
core/database           Room database, entities, DAOs
core/extractor-ytdlp    ExtractorEngine implementation: yt-dlp via Chaquopy
```

Backend implementations live in their own modules that implement the interfaces in
`core/domain` — the UI never references a backend (yt-dlp, and eventually FFmpeg and
libtorrent) directly; it only ever depends on `core/domain`.

### Core engine interfaces

| Interface | Responsibility | Backend |
|---|---|---|
| [`ExtractorEngine`](core/domain/src/main/java/com/mediavault/core/domain/extractor/ExtractorEngine.kt) | Analyze a source URL into metadata, formats, and tracks; drive extraction-based downloads | **Implemented** — `YtDlpExtractorEngine` and `InstaloaderExtractorEngine` (Instagram), composed behind `CompositeExtractorEngine`, both analysis and download |
| [`DownloadEngine`](core/domain/src/main/java/com/mediavault/core/domain/download/DownloadEngine.kt) | Queue, transfer, pause/resume/cancel/retry downloads | **Implemented** — `MediaVaultDownloadEngine`, including multi-audio-track split downloads remuxed via FFmpegKit |
| [`TorrentEngine`](core/domain/src/main/java/com/mediavault/core/domain/torrent/TorrentEngine.kt) | Magnet/`.torrent` handling, metadata, file selection, progress | Not implemented — planned libtorrent |
| [`NetworkPolicyManager`](core/domain/src/main/java/com/mediavault/core/domain/network/NetworkPolicyManager.kt) | Wi-Fi/mobile decisions, daily budget, quality recommendation | **Implemented** |
| [`UpdateManager`](core/domain/src/main/java/com/mediavault/core/domain/update/UpdateManager.kt) | GitHub release/version checks | Not implemented — Settings' "Check for updates" currently just opens the GitHub Releases page in the browser |
| [`PlayerEngine`](core/domain/src/main/java/com/mediavault/core/domain/player/PlayerEngine.kt) | Playback control surface for the UI | **Implemented** — `Media3PlayerEngine` (multi-audio, subtitles, Picture-in-Picture, gesture controls) |

Media processing (remux/mux via FFmpegKit, `-c copy` only — MediaVault never
transcodes) is implemented behind `MediaProcessor`/`FFmpegMediaProcessor` in `app`.

### Why yt-dlp runs via Chaquopy, not a prebuilt wrapper

yt-dlp is a Python project. The common way to run it on Android is
`youtubedl-android`, a ready-made Kotlin wrapper around a bundled yt-dlp binary — but
that wrapper is GPLv3-licensed, and combining it into MediaVault would effectively force
the whole project to relicense under the GPL to be distributed. Instead, MediaVault
embeds a Python interpreter via [Chaquopy](https://chaquo.com/chaquopy/) (MIT-licensed
as of v12.0.1) and `pip install`s yt-dlp (Unlicense) directly, keeping every dependency
on the extraction path permissively licensed. The tradeoff is that MediaVault owns a
small Kotlin↔Python bridge itself (`core/extractor-ytdlp/src/main/python/`) instead of
using an off-the-shelf Kotlin API. See
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for details.

Why this matters: the app version, the extraction-engine version, and the
media-processing version can evolve independently — upgrading yt-dlp or FFmpeg should
never require an app release, and an app release should never be blocked on a backend
upgrade.

## Technology

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Architecture:** MVVM / Clean Architecture, modular Gradle project
- **Async:** Kotlin Coroutines + Flow
- **Persistence:** Room
- **DI:** Hilt
- **Playback:** Media3
- **Extraction:** yt-dlp, embedded via Chaquopy (see above)
- **Images:** Coil (thumbnail loading)
- **Storage:** app-private storage (`getExternalFilesDir()`) by default for new
  downloads — nothing is written to shared/public storage without the user explicitly
  choosing to Export or Share a file via the Storage Access Framework

See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for the full list of dependencies
and their licenses, including planned integrations (libtorrent) that are not yet part
of the codebase.

## Setup

This project assumes an existing Android Studio + Android SDK + JDK setup — open the
repository root in Android Studio and let Gradle sync, or build from the command line:

```
./gradlew build
./gradlew test
```

No project-specific SDK components beyond a standard Android Studio install are
required at this stage.

## Project limitations

Being upfront about the current state:

- No torrent backend is implemented — `TorrentEngine` has no concrete implementation
  yet; magnet/`.torrent` support remains a product goal, not a shipped feature.
- `UpdateManager` has no concrete implementation — Settings' "Check for updates" opens
  the GitHub Releases page in the browser rather than querying an API in-app.
- Some known upstream source-extraction gaps exist and are documented, not hidden — see
  CHANGELOG.md/PROJECT_MASTER.md's per-source compatibility notes (e.g. TikTok video
  extraction is currently broken upstream in the pinned yt-dlp version; Vimeo video
  requires login on yt-dlp's default API client).
- There is no Compose UI test suite; test coverage is JVM unit tests across all
  modules plus targeted on-device verification for UI/device-specific behavior (see
  PROJECT_MASTER.md's decision log for what's been verified live and how).
- The release build is not yet signed — no signing key exists in this repository by
  design (see [Setup](#setup) below); this is the one concrete blocker before a signed,
  distributable release artifact can exist.

## User responsibility

MediaVault is a tool, not a source of content. You are responsible for ensuring you
have the right to access, download, store, and use any content you retrieve with it.
MediaVault does not implement DRM circumvention and is not intended for copyright
infringement. See [TERMS.md](TERMS.md).

## Privacy

MediaVault is local-first: no ads, no required accounts, no analytics SDKs as of this
writing. See [PRIVACY.md](PRIVACY.md) for the full, honestly-maintained policy.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for architecture rules, commit conventions, and
how to get set up.

## License

MediaVault's own source code is licensed under the [MIT License](LICENSE). It depends
on and will bundle third-party open-source components under their own licenses — see
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
