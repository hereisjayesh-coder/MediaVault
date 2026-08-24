# Third-Party Notices

MediaVault is built on top of, and is planned to integrate with, several open-source
projects. MediaVault is not affiliated with, endorsed by, or sponsored by any of the
projects listed below. All trademarks belong to their respective owners.

This file will be kept up to date as backends are actually integrated. A dependency is
listed here once code that uses it lands in the repository, not merely because it is
planned.

## Currently integrated

| Project | Purpose in MediaVault | License |
|---|---|---|
| [AndroidX Jetpack libraries](https://developer.android.com/jetpack) (Compose, Room, Lifecycle, Navigation, DataStore, Activity, Core) | UI toolkit, local database, navigation, app architecture | Apache License 2.0 |
| [Media3](https://github.com/androidx/media3) | Built-in video/audio playback (ExoPlayer, session, UI) | Apache License 2.0 |
| [Kotlin / kotlinx.coroutines](https://github.com/Kotlin) | Primary language and asynchronous programming | Apache License 2.0 |
| [Dagger Hilt](https://github.com/google/dagger) | Dependency injection | Apache License 2.0 |

## Planned integrations (not yet in the codebase)

These are named in the project architecture as the intended backends behind MediaVault's
engine abstractions ([`ExtractorEngine`](core/domain/src/main/java/com/mediavault/core/domain/extractor/ExtractorEngine.kt),
a media-processing layer, and [`TorrentEngine`](core/domain/src/main/java/com/mediavault/core/domain/torrent/TorrentEngine.kt)).
They will be added to this table with their exact version and license the moment their
code is vendored or depended upon.

| Project | Planned purpose | License |
|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Media extraction backend behind `ExtractorEngine` | Unlicense |
| [FFmpeg](https://ffmpeg.org/) | Media processing/transcoding/muxing backend | LGPL v2.1+ / GPL v2+ depending on build configuration |
| [libtorrent](https://www.libtorrent.org/) | Torrent/magnet backend behind `TorrentEngine` | BSD 3-Clause |

MediaVault does not redistribute modified copies of the above projects' source code.
When each backend is integrated, this document will state precisely how it is consumed
(vendored source, prebuilt binary, or library dependency) and link to its unmodified
license text.

## A note on FFmpeg licensing

FFmpeg's effective license depends on which components are compiled in. If GPL-licensed
components are ever enabled, this project's distribution terms for the affected binaries
will be updated to comply with the GPL. This will be documented here before any such
build is shipped.
