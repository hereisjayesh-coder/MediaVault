# MediaVault

MediaVault is an open-source, local-first Android application for analyzing,
downloading, organizing, and playing media — with intelligent mobile-data management
and torrent/magnet support — built around clean, swappable engine abstractions rather
than a hard dependency on any one backend.

This repository is under active early development. See [CHANGELOG.md](CHANGELOG.md)
for what currently exists versus what is planned.

## Status: foundation stage

The current codebase is the **architectural foundation**: project structure, module
boundaries, navigation skeleton, Room database scaffolding, and the core interfaces the
rest of the app will be implemented against. It does **not** yet download media,
process media, or handle torrents — those backends are intentionally not wired up yet.
See [Project limitations](#project-limitations) below.

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
app                 Compose UI, navigation, Hilt wiring, screens, view models
core/model           Pure Kotlin data models shared across modules (no Android deps)
core/common          Cross-cutting utilities (dispatcher provider, result types)
core/domain          The engine interfaces described below (pure Kotlin)
core/database        Room database, entities, DAOs
```

Future backend implementations (a yt-dlp-backed extractor, an FFmpeg-backed processor,
a libtorrent-backed torrent engine, an HTTP download engine) are expected to live in
their own modules that implement the interfaces in `core/domain` — the UI never
references those backends directly.

### Core engine interfaces

| Interface | Responsibility | Planned backend |
|---|---|---|
| [`ExtractorEngine`](core/domain/src/main/java/com/mediavault/core/domain/extractor/ExtractorEngine.kt) | Analyze a source URL into metadata, formats, and tracks; drive extraction-based downloads | yt-dlp |
| [`DownloadEngine`](core/domain/src/main/java/com/mediavault/core/domain/download/DownloadEngine.kt) | Queue, transfer, pause/resume/cancel/retry downloads | HTTP downloader |
| [`TorrentEngine`](core/domain/src/main/java/com/mediavault/core/domain/torrent/TorrentEngine.kt) | Magnet/`.torrent` handling, metadata, file selection, progress | libtorrent |
| [`NetworkPolicyManager`](core/domain/src/main/java/com/mediavault/core/domain/network/NetworkPolicyManager.kt) | Wi-Fi/mobile decisions, daily budget, quality recommendation | — (pure logic) |
| [`UpdateManager`](core/domain/src/main/java/com/mediavault/core/domain/update/UpdateManager.kt) | GitHub release/version checks | GitHub Releases API |
| [`PlayerEngine`](core/domain/src/main/java/com/mediavault/core/domain/player/PlayerEngine.kt) | Playback control surface for the UI | Media3 (ExoPlayer) |

Media processing (transcoding/muxing, planned FFmpeg backend) will get its own
interface in `core/domain` once implementation work on it begins, following the same
pattern.

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
- **Storage:** Android Storage Access Framework (user-selected locations, no
  hard-coded paths)

See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for the full list of dependencies
and their licenses, including planned integrations (yt-dlp, FFmpeg, libtorrent) that
are not yet part of the codebase.

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

- No extraction backend is implemented — `ExtractorEngine` has no concrete
  implementation yet, so URL analysis and downloading do not work.
- No media-processing backend is implemented.
- No torrent backend is implemented — `TorrentEngine` has no concrete implementation
  yet.
- The network policy engine, update checker, and player are interfaces only; they have
  no concrete implementation wired into the app yet.
- The supported-sources index does not exist yet.
- There is no automated UI/instrumentation test suite yet, only JVM unit tests.

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
