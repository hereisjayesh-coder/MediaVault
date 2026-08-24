# MediaVault

MediaVault is an open-source, local-first Android application for analyzing,
downloading, organizing, and playing media — with intelligent mobile-data management
and torrent/magnet support — built around clean, swappable engine abstractions rather
than a hard dependency on any one backend.

This repository is under active early development. See [CHANGELOG.md](CHANGELOG.md)
for what currently exists versus what is planned.

## Status: extraction (analysis-only) stage

The architectural foundation is in place, and the first real backend is wired up:
pasting a URL on the Home screen runs it through `ExtractorEngine` → `YtDlpExtractorEngine`
(yt-dlp, embedded via Chaquopy) and shows real metadata — title, thumbnail, duration,
source, and available formats. It does **not** yet download media, process media, or
handle torrents — those backends are intentionally not wired up yet. See
[Project limitations](#project-limitations) below.

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
| [`ExtractorEngine`](core/domain/src/main/java/com/mediavault/core/domain/extractor/ExtractorEngine.kt) | Analyze a source URL into metadata, formats, and tracks; drive extraction-based downloads | **Implemented** — [`YtDlpExtractorEngine`](core/extractor-ytdlp/src/main/java/com/mediavault/core/extractor/ytdlp/YtDlpExtractorEngine.kt) (analysis only; download is not implemented yet) |
| [`DownloadEngine`](core/domain/src/main/java/com/mediavault/core/domain/download/DownloadEngine.kt) | Queue, transfer, pause/resume/cancel/retry downloads | Not implemented — planned HTTP downloader |
| [`TorrentEngine`](core/domain/src/main/java/com/mediavault/core/domain/torrent/TorrentEngine.kt) | Magnet/`.torrent` handling, metadata, file selection, progress | Not implemented — planned libtorrent |
| [`NetworkPolicyManager`](core/domain/src/main/java/com/mediavault/core/domain/network/NetworkPolicyManager.kt) | Wi-Fi/mobile decisions, daily budget, quality recommendation | Not implemented — pure logic, no backend needed |
| [`UpdateManager`](core/domain/src/main/java/com/mediavault/core/domain/update/UpdateManager.kt) | GitHub release/version checks | Not implemented — planned GitHub Releases API |
| [`PlayerEngine`](core/domain/src/main/java/com/mediavault/core/domain/player/PlayerEngine.kt) | Playback control surface for the UI | Not implemented — planned Media3 (ExoPlayer) |

Media processing (transcoding/muxing, planned FFmpeg backend) will get its own
interface in `core/domain` once implementation work on it begins, following the same
pattern.

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
- **Storage:** Android Storage Access Framework (user-selected locations, no
  hard-coded paths)

See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for the full list of dependencies
and their licenses, including planned integrations (FFmpeg, libtorrent) that are not
yet part of the codebase.

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

- `ExtractorEngine` can analyze a URL (metadata, formats, tracks) but cannot download
  yet — `download()` returns a "not implemented" result on purpose rather than faking
  progress.
- No media-processing backend is implemented.
- No torrent backend is implemented — `TorrentEngine` has no concrete implementation
  yet.
- The network policy engine, update checker, and player are interfaces only; they have
  no concrete implementation wired into the app yet.
- The supported-sources index does not exist yet.
- There is no Compose UI test suite yet; test coverage is JVM unit tests plus one
  on-device instrumented test that exercises the real yt-dlp extraction path.

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
