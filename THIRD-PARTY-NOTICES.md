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
| [Kotlin / kotlinx.coroutines / kotlinx.serialization](https://github.com/Kotlin) | Primary language, async programming, JSON parsing | Apache License 2.0 |
| [Dagger Hilt](https://github.com/google/dagger) | Dependency injection | Apache License 2.0 |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) (pinned `2026.8.19`) | Media extraction backend behind `ExtractorEngine`, run via Chaquopy | Unlicense |
| [Instaloader](https://instaloader.github.io/) (pinned `4.15.3`) | Second `ExtractorEngine` backend, behind `InstaloaderExtractorEngine`/`CompositeExtractorEngine` — resolves Instagram posts and carousels (single items and mixed image/video carousels alike) that yt-dlp's own Instagram extractor can't. Runs anonymously; never logs in or accesses login-gated content. Installed in the same Chaquopy environment as yt-dlp (`core:extractor-ytdlp`) — see this file's Instaloader decision-log entry for why it isn't a separate module. | MIT License |
| [Chaquopy](https://chaquo.com/chaquopy/) (`17.0.0`) | Embeds a Python interpreter in the app so yt-dlp (a Python project) can run on Android | MIT License (open-sourced as of v12.0.1) |
| [Coil](https://coil-kt.github.io/coil/) | Thumbnail image loading in Compose | Apache License 2.0 |
| [FFmpegKit](https://github.com/moizhassankh/ffmpeg-kit-android-16KB) (`com.moizhassan.ffmpeg:ffmpeg-kit-16kb`, `6.1.1`) | Remuxes a separately-downloaded video-only and audio-only stream into one playable file (`-c copy` only — MediaVault never transcodes) behind `MediaProcessor`/`FFmpegMediaProcessor` | GNU Lesser General Public License v3.0 |
| [ZXing](https://github.com/zxing/zxing) core (`3.5.3`) | Local, offline QR-code generation for Settings' "Support the Project" UPI QR code — encoding only, no scanning, no camera permission, no network call | Apache License 2.0 |

MediaVault deliberately chose Chaquopy over the more common `youtubedl-android` wrapper
(a ready-made Kotlin API around a bundled yt-dlp binary) because that wrapper is
GPLv3-licensed; combining it into MediaVault would have effectively forced the whole
project to relicense under the GPL to distribute it. Chaquopy plus yt-dlp directly keeps
every extraction-path dependency permissively licensed, matching MediaVault's MIT
license, at the cost of MediaVault owning its own thin Kotlin↔Python bridge instead of
using an off-the-shelf one.

## Planned integrations (not yet in the codebase)

These are named in the project architecture as the intended backends behind MediaVault's
remaining engine abstractions (a media-processing layer and
[`TorrentEngine`](core/domain/src/main/java/com/mediavault/core/domain/torrent/TorrentEngine.kt)).
They will be added to the table above with their exact version and license the moment
their code is vendored or depended upon.

| Project | Planned purpose | License |
|---|---|---|
| [libtorrent](https://www.libtorrent.org/) | Torrent/magnet backend behind `TorrentEngine` | BSD 3-Clause |

MediaVault does not redistribute modified copies of the above projects' source code.
When each backend is integrated, this document will state precisely how it is consumed
(vendored source, prebuilt binary, or library dependency) and link to its unmodified
license text.

## A note on FFmpeg licensing

MediaVault depends on FFmpegKit (see the table above) purely as a prebuilt Maven `.aar`
dependency — no FFmpeg source is vendored or modified in this repository. The specific
build MediaVault pulls in (`ffmpeg-kit-16kb`) is published under the LGPL v3, which
FFmpegKit's underlying FFmpeg build satisfies without requiring any GPL-licensed
components, and MediaVault's own usage (`FFmpegMediaProcessor`) only ever calls FFmpeg
with `-c copy` — a stream-copy remux, never a re-encode — so no GPL-only codec or filter
is invoked. If a future stage ever needs a GPL-only FFmpeg component, this project's
distribution terms for the affected binaries will be updated to comply with the GPL
first, and that change will be documented here before any such build is shipped.
