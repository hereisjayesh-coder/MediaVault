# Changelog

All notable changes to this project are documented here. This project has not yet made
a tagged release; entries below track development stages instead of version numbers.

## [Unreleased]

### Verified — Release Candidate Readiness Pass (No Code Changes)

- **Clean install and navigation**: current build (`796a4e4`) installs and launches cleanly on the
  Pixel 7a; Home → analyze → format selection → download → Downloads/Library → playback all
  confirmed working on a fresh install.
- **Real upgrade/data-safety test, not simulated**: built the project's earliest commit with a real
  Room schema (`44d6fec`, database version 1, pre-dating both FFmpeg merge support and app-private
  storage — downloads went through a SAF folder picker at this point in the project's history),
  installed it fresh on the Pixel 7a, used it to create a real completed download (an
  SAF-delivered audio file) via its own actual UI, then installed the current build **over** it
  (`adb install -r`, no uninstall, no data wipe) and confirmed: the app launched with no crash and
  no migration error in logcat; the Room schema migrated 1→7 through all six registered
  migrations; the pre-existing download survived in both Downloads and Library with correct
  metadata; the file — originally written to a SAF-selected external folder by code that no longer
  exists in the current app — still played back correctly through the current player, confirming
  the storage-architecture change (SAF → app-private, 2026-08-25) didn't strand pre-existing files;
  and App Lock (a feature that didn't exist yet in that old schema) correctly initialized to its
  disabled default with no crash. No destructive migration fallback is configured anywhere.
- **Release build configuration verified, with one real, expected blocker**: `assembleRelease`
  builds cleanly (proguard rules, resource shrinking config, and the `arm64-v8a`/`x86_64` ABI
  restriction all carry through correctly to the release variant) but produces an **unsigned**
  APK — no signing config or keystore exists anywhere in this repository, and none was created
  this stage, per this stage's own explicit "do not invent or commit keystores/passwords"
  instruction. A real signing key, generated and held by the project owner (never by an
  automated session), is a genuine prerequisite for producing an installable/distributable
  release artifact — this is the one concrete remaining blocker before a GitHub Releases
  distribution is possible.
- **Security check on release artifacts**: no `usesCleartextTraffic` override exists in the
  manifest (so the platform default — cleartext HTTP blocked — applies to both build types); the
  merged release manifest correctly has no `android:debuggable` attribute (resolves to `false`,
  vs. the debug variant's explicit `true`); no stray test fixtures, secrets, keystores, or `.env`
  files are tracked in git or bundled in the built APK beyond `kotlinx-coroutines`' own standard,
  inert `DebugProbesKt.bin` resource (present in every app using that library; a no-op unless a
  developer explicitly calls `DebugProbes.install()`, which this app never does); zero `Log.d`/
  `Log.v` calls exist anywhere in the app's source.
- **Legal/open-source docs confirmed present and accurate**: `LICENSE`, `PRIVACY.md`, `TERMS.md`,
  `CHANGELOG.md`, `THIRD-PARTY-NOTICES.md`, `CONTRIBUTING.md`, and `README.md` all exist;
  `THIRD-PARTY-NOTICES.md`'s pinned versions (Chaquopy 17.0.0, FFmpegKit 6.1.1, ZXing 3.5.3) were
  cross-checked against `gradle/libs.versions.toml` and match exactly.
- **No source or app-behavior changes were made this stage** — this was a verification-only pass.
  `versionCode`/`versionName` (`1` / `0.1.0`) were left unchanged; bumping them for an actual
  tagged release is a decision for whenever the project owner is ready to cut one, not something
  to do speculatively during a readiness check.

### Fixed — Settings Scroll Hitch on Fast Reverse Fling

- **Reproduced and fixed a real, measured scroll hitch** reported on Settings when reaching the
  bottom and flinging back up fast. Root cause: the "Support the Project" section's QR code
  (`SupportSection`/`QrCodeGenerator`) was generated with `Bitmap.setPixel()` called individually
  for every one of a 512×512 image's 262,144 pixels — each call crosses into native code
  separately. The result was `remember`-cached, but Compose discards a `LazyColumn` item's
  `remember` cache once it scrolls out of the list's retention window, so the QR bitmap was
  regenerated **synchronously on the main thread, during composition**, every single time that
  section scrolled back into view — exactly the "reach the bottom, fling back up" gesture
  reported. Confirmed live on a Pixel 7a with `dumpsys gfxinfo`: the exact reported gesture
  produced a 150ms outlier frame (plus 31ms/22ms others, "Slow UI thread" flagged), landing
  precisely on the QR section.
- **Fix**: rewrote `generateQrCodeBitmap` to fill a plain `IntArray` and construct the `Bitmap` in
  one bulk `Bitmap.createBitmap(pixels, width, height, config)` call instead of one `setPixel()`
  call per pixel, and reduced the generated size from 512px to 256px (still well above the 96dp
  display size it's actually shown at, so no visible softness — just proportionate to what's
  rendered instead of 4x oversized). No API/behavior change: `SupportSection`'s signature and
  visual output are unchanged, verified identical on-device.
- **Measured result** (Pixel 7a, `dumpsys gfxinfo`, same gesture, repeated 4 times for
  reproducibility): the deterministic 100–150ms spike is gone in every run. "Slow UI thread" reads
  0 in 3 of 4 runs (1 in the fourth); 99th-percentile frame time dropped from 450ms/150ms
  (pre-fix, two separate runs) to 12–34ms (post-fix, four runs) — normal device-level variance,
  not a repeatable defect. The user independently confirmed on their own device: "now it's
  smooth."
- **Also checked, no fix needed**: `SourcesScreen`'s alphabetical `.sorted()` call and
  `DownloadsScreen`'s `groupBySection()` both run once per data change (inside the `LazyColumn`
  scope-builder or a composable that isn't scroll-position-reactive), not per scroll frame — not a
  bug. `AndroidMediaMetadataProbe`'s bitmap decoding already runs on `Dispatchers.IO` and only
  during media import, never during list scrolling. The 1,027-entry Sources catalog (each row
  loading a real network favicon via Coil's `SubcomposeAsyncImage`) showed mild, non-deterministic
  jank under the same aggressive synthetic fling (32–53ms across three runs, no repeatable large
  spike) — judged an inherent characteristic of a very large network-image list under an
  artificially fast synthetic swipe, not the reported defect and not a confirmed reproducible
  issue; changing it would require either dropping the initials-avatar fallback design or a
  non-trivial manual async-image-state rewrite, not attempted without stronger evidence it's a
  real problem.
- **Orientation**: no manifest-level orientation lock exists (confirmed already true, unchanged).
  Settings, Sources, Downloads, and Library all verified live in portrait on the Pixel 7a
  (Downloads/Library currently empty on this device, confirmed empty-state renders correctly in
  portrait too); Settings' scroll position survived a live rotation from landscape to portrait
  with no crash and no jump, confirming the existing `configChanges` handling works correctly.
  Player's own dedicated orientation/fullscreen behavior is unchanged (not touched this stage).

### Fixed — Android Device Compatibility Hardening

- **Fixed a real crash-on-first-use risk on 32-bit-only devices**: the app's native libraries
  (`:app`) previously had no ABI restriction of their own, so the merged APK still packaged
  FFmpegKit's `armeabi-v7a`/`x86` builds even though Chaquopy's embedded Python runtime — which
  every download's extraction (yt-dlp/Instaloader) depends on — has only ever shipped
  `arm64-v8a`/`x86_64` libraries (an existing, deliberate restriction already correctly applied in
  `:core:extractor-ytdlp`, just never mirrored at the app level). A 32-bit-only device could
  therefore install the app successfully and then hard-crash the instant Python was invoked — the
  very first "Analyze Link" tap. Added the matching `ndk.abiFilters` to `:app`'s own
  `defaultConfig`; confirmed via direct APK inspection that the merged debug build now packages
  only `arm64-v8a`/`x86_64` (down from four ABIs), and that this also drops roughly 107 MB of
  native libraries that could never have run on any of this app's actually-supported devices.
- **Fixed the download-progress notification never appearing on Android 13+ (API 33/Tiramisu and
  newer)**: `POST_NOTIFICATIONS` was declared in the manifest but never actually requested at
  runtime — a manifest declaration alone leaves a runtime permission ungranted on API 33+, so the
  persistent foreground-service notification was silently suppressed by the OS on every Android
  13+ device (downloads still ran correctly; only the visible progress notification never showed).
  `MainActivity` now requests it once at startup via the standard
  `ActivityResultContracts.RequestPermission()` flow, guarded to API 33+ only (below that, the
  permission is still install-time-granted as before). Verified live on the Pixel 7a (API 34+):
  the system permission prompt now appears on first launch after a clean install, where it
  previously never did.
- **Fixed non-phone screens (tablets, unfolded foldables, phones in landscape) stretching every
  list/form screen's content edge-to-edge**: Home, Downloads, Library, and Settings had no upper
  width bound, so on anything wider than a phone in portrait the URL bar, cards, and format picker
  would span the full display width — very long text lines, oversized cards, awkward whitespace.
  The dedicated Player screen was already, correctly, exempt (video content should always use the
  full available width). Added a single width cap (600dp — the same "compact" breakpoint
  `WindowSizeClass` itself uses to mean "phone-sized, don't bother constraining") at the `NavHost`
  level in `MediaVaultNavHost`, centered, applied to every route except the Player. Zero visual
  change on any phone in portrait (already narrower than 600dp — confirmed unchanged on the Pixel
  7a); verified live on the Pixel 7a **rotated to landscape** (~915dp-wide at that density, so the
  cap actually engages): content now correctly centers with balanced margins instead of stretching
  to the screen edges. One real bug found and fixed while verifying this live: the first
  implementation (`fillMaxSize().widthIn(max = 600.dp)`) visually capped the width correctly but
  left the capped content flush against the leading edge instead of centered — `fillMaxSize()`
  reports its pre-cap full size back to the centering `Box` before `widthIn` narrows it further
  down the chain. Reordered to `fillMaxHeight().widthIn(max = 600.dp).fillMaxWidth()`, which caps
  before reporting size upward, and re-verified centered correctly on-device.
- **Reviewed and confirmed already sound, no change needed**: biometric UI is already fully gated
  on real device capability at both the Settings toggle (hidden entirely when
  `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` doesn't return `BIOMETRIC_SUCCESS`) and the
  lock-screen auto-prompt; edge-to-edge/system-bar insets were already handled correctly via
  `enableEdgeToEdge` with explicit light/dark status/nav bar styles; every existing API-level guard
  (`Build.VERSION.SDK_INT` checks for notification channels, scoped-storage `MediaStore` behavior,
  and two Android 12+ Settings/Player features) is internally consistent with the project's minSdk;
  the two pieces of engine-owned mutable state shared across coroutines (`liveThroughput`,
  `activeJobs`) are already `ConcurrentHashMap`, not a plain `HashMap`; filename sanitization
  already strips every path separator before an extension is appended, so a crafted source title
  cannot escape the app's private media directory.
- **No emulator profiles were created or tested this stage** — this machine has no Android Virtual
  Devices and no `cmdline-tools`/`sdkmanager` installed to create one, and provisioning that from
  scratch would mean downloading multiple new multi-GB system images mid-task, which was judged out
  of scope for "do not install another JDK/SDK." Compatibility conclusions instead rest on: the
  real Pixel 7a (used for every live check above, including the landscape/orientation-dependent
  ones an emulator profile would otherwise have covered), full code-level review of
  density-independent (`dp`) and responsive (`fillMaxWidth()`, `LazyColumn`) layout usage across
  every screen, and the existing API-level guard audit above. A genuine small-phone/tablet/older-
  API emulator pass remains open for whenever emulator tooling is available on the dev machine —
  documented here rather than fabricated.

### Fixed — Closed the Last Known Test Gap; Full v1 Integration/Security Audit

- **Root-caused the long-deferred pre-existing `HomeViewModelTest` failure**: it was already
  fixed as a side effect of the format-selection redesign two stages ago (the test's own premise —
  a video-only format with no compatible audio being un-selectable — was an old-architecture
  behavior the redesign deliberately and correctly replaced, so the test was deleted rather than
  ported forward). Re-audited that deletion from scratch this stage rather than trusting it at
  face value, and confirmed it really was an **obsolete expectation**, not a real defect or
  test-only drift: the new domain model treats a genuinely audio-less video-only quality as
  already the complete file (nothing exists to pair it with), which is more correct than the old
  behavior of refusing to let a user download a legitimately silent clip at all.
- **Found and closed a real test-coverage gap while re-auditing it**, though not a production
  defect: the playlist path already had a positive test for this exact scenario
  (`a video-only quality with no audio anywhere resolves as a direct pick...`), but the
  single-item `HomeViewModel` path never got its own equivalent — only the pure-domain-level
  `FormatSelectionModelTest` covered it there. Added
  `a video-only quality with no audio anywhere is still selectable and enqueues as a direct
  download` to close that gap.
- **Full v1 integration audit** (Home → Analyze → Format → Download → Downloads → Library →
  Player/Image Viewer) confirmed sound by code review, reusing the extensive live-device evidence
  already on record rather than repeating verification that already exists: multi-track
  pause/cancel/remove in `MediaVaultDownloadEngine` correctly loop over every audio cache path (not
  just one), collection/carousel UI is intact and untouched by the format-selection rewrite, and
  Room's migration chain (versions 1→7) has no gaps and is fully registered.
- **Security/privacy audit**: no hardcoded secrets, no URL/token logging, minimal and fully
  justified permissions (no storage permission at all — app-private storage only), no
  analytics/tracking dependency anywhere, `sanitizeFileName` safely blocks path-traversal via
  stripped `/`/`\` before an extension is always appended, PIN handling already uses
  PBKDF2WithHmacSHA256 (120k iterations, random salt, constant-time comparison) backed by
  Keystore-encrypted storage, and the download notification already has a `hideTitleForPrivacy`
  path for when App Lock is enabled. No gaps found; nothing changed.
- **Platform limitations reconfirmed accurate and unchanged** (extractor code untouched since they
  were last verified): TikTok video extraction still broken upstream, Vimeo still requires login,
  Facebook image posts still unsupported, Reddit multi-image galleries still unsupported.
- Full test suite re-run clean after the fix: 362 tests, zero failures, across all modules. Debug
  APK build reconfirmed with a genuine (non-cached) `BUILD SUCCESSFUL` exit status.

### Verified — Multi-Audio Download and Playback, End-to-End on a Real File

- **Confirmed at the file level, not just the UI, that a multi-audio download produces one real
  multi-track file**: pulled the completed output from a Pixel 7a and inspected it with `ffmpeg`
  on the host — exactly one video stream (h264, 1280x720) and exactly three audio streams tagged
  `eng`/`deu`/`hin` (AAC, 44.1 kHz stereo), matching the three tracks selected in the app. No
  duplicate video, no missing or extra audio track.
- **Confirmed Media3's existing (untouched) track selector reads the real file correctly**: its
  audio-track menu listed exactly `en`/`de`/`hi` — matching the ffmpeg-verified stream tags one
  for one — and switching between all three moved the player's actual applied-track selection
  each time (confirmed by re-opening the menu after each switch and seeing the checkmark on the
  newly selected language), with playback continuing smoothly across every switch.
- Re-confirmed the persistent Download bar's live size updates against a fresh selection (English
  → +German → +Hindi: 164 MB → 183 MB → 202 MB), the Library entry showing the correct combined
  duration/resolution/size for the resulting file, and a separate single-audio (non-toggled) 1080p
  download completing normally afterward with no regression.
- **No code defect found.** Two things that initially looked like bugs during testing were both
  traced to test-automation artifacts, not app behavior, and are noted here so they aren't
  mistaken for real issues in a future session: (1) a scripted tap landed on the "Include multiple
  audio tracks" toggle instead of an audio-track checkbox because a prior scroll gesture's fling
  hadn't fully settled before the tap fired — reproduced cleanly as correctly additive once each
  scroll was verified settled (two consecutive UI dumps compared identical) before tapping; (2) the
  Analyze button appeared unresponsive after a Download → Player → back navigation sequence on a
  real, memory-pressured device (logcat showed the OS actively killing other apps — Camera,
  Messaging, a carrier service — for memory during the test) — a longer wait showed the analysis
  had in fact been running the whole time and completed on its own; a same-URL retry after an app
  restart also succeeded immediately in every case tried. Neither reproduced as a deterministic
  defect independent of these conditions.
- **Scope note:** a live device test of the same multi-audio selection model against a real
  playlist item was not performed this stage — no playlist with genuinely multiple real audio
  languages per item was available to test against, and guessing a playlist URL wasn't an option.
  The playlist code path itself is unchanged since the prior stage and its existing automated
  coverage (`QualityDescriptorTest`'s `resolveForPlaylist`, `HomeViewModelTest`'s playlist
  enqueue tests) still passes; a genuine live playlist confirmation remains open for whenever a
  suitable real multi-language playlist is available to test against.

### Added — Simplified Format Selection and Multi-Audio Downloads

- **Replaced the flat, 100+ row raw format picker with a coarse quality-tier selector** (4K /
  1440p / 1080p / 720p / 480p / lower), with per-tier variants (fps/codec/container/size) only
  shown when a tier genuinely has more than one — verified live against a real modern YouTube
  upload (37 raw video-only formats across 8 resolutions collapsing to 5-6 tiers), and against the
  reported MrBeast multi-audio test case (`Escape 100 Cops, Win $500,000`, 195 raw formats, 22
  audio languages, zero muxed formats).
- **Every available audio track is now shown separately with its real language name** (via
  `java.util.Locale`, never a guessed or raw-code label), with an explicit "Include multiple audio
  tracks" toggle revealing checkboxes for selecting more than one language at once. Previously the
  picker only ever allowed a single audio track.
- **Multi-audio downloads mux the video once plus every selected audio stream into one final
  file**, never duplicating the video, via a generalized FFmpeg command
  (`-map 0:v:0 -map 1:a:0 -map 2:a:0 ... -c copy` with per-track `-metadata:s:a:N language=`) that
  now accepts any number of audio inputs instead of exactly one.
- **Container choice: a single audio track keeps the existing mp4/webm/mkv compatibility logic; 2
  or more audio tracks always mux into MKV** — MP4's multi-track compatibility is inconsistent
  across real codec combinations even though the merge is remux-only, while MKV natively supports
  multi-track audio with per-track language metadata and Media3's existing Matroska extractor
  already reads it, so no player-side code changes were needed.
- The persistent Download bar's estimated size now live-updates as quality/audio selections
  change, summing the video size and every selected audio track's size (binary/1024-based MB,
  consistent with the app's existing size formatting).
- The same model and selection logic is reused for playlists: quality and selected audio languages
  apply across every item, matched per item by language code; a playlist item missing a requested
  quality tier or audio language fails that item clearly rather than silently substituting another
  one (consistent with existing playlist-resolution behavior).
- Verified live on a Pixel 7a against the real MrBeast test video: 720p selectable without
  scrolling through the raw format list, German/Hindi/English selected as three separate audio
  tracks with live size updates (145→164→183→202 MB as each track was added), single download
  producing one file, Media3's existing audio-track picker showing all three languages (`de`/
  `hi`/`en`) with track switching confirmed (selection moved to `hi` and persisted), and a
  separate single-audio (non-toggled) 1080p download completing normally with no regression.
- Two new domain types (`QualityTier`, `FormatSelectionModel`) replace the old flat
  `DownloadOption` model entirely (deleted); one new Room migration (`MIGRATION_6_7`, version 6→7)
  adds a single new column for per-track audio language codes, while every other multi-value field
  reuses an existing TEXT column reinterpreted as comma-joined content — no other schema changes
  or new dependencies.

### Fixed — v1 Core-Flow Hardening Audit

- **A merge-required download (video-only + audio-only remuxed into one file) no longer shows a
  stale, frozen download speed/ETA during the merge step** — that data is meaningless once the
  transfer itself has finished, and previously kept displaying its last value throughout the
  entire merge instead of disappearing. Found via a full audit of the core v1 workflows (video/
  audio/image/collection downloads, download management, storage/privacy, player, settings);
  everything else reviewed (Room migrations, player lifecycle, Library delete-safety, dependency
  list) was confirmed sound with no other defects found. See PROJECT_MASTER.md's 2026-08-29
  hardening-audit decision log entry for the full review.

### Fixed — Major Social Platform Hardening (Download Speed/ETA, Login-Required Errors)

- **Download speed and ETA now actually display, for every download on every source** — the
  Downloads screen already had the UI for `"12 MB/s • ETA 45s"`, but the real values from
  yt-dlp's own progress hooks were being dropped before ever reaching it, so the text never
  appeared. Fixed with an in-memory (never persisted — meaningless past a process restart) live
  value merged into progress at read time. Verified live with a real 689 MB 4K download showing
  live-updating speed/ETA through to completion.
- **A login-required error from any source (Vimeo, Twitter/X protected tweets, Facebook, and
  more) no longer leaks yt-dlp's raw `--cookies`/CLI-hint text** — found live against a real,
  currently-public Vimeo video (Vimeo's default API now requires login for every video). One
  new, generic error-mapping branch now covers every extractor that hits this same shared yt-dlp
  mechanism, producing a single clean "This content requires logging into the source" message
  instead of a raw, developer-facing string.
- Fixed a related gap where yt-dlp's own ALL-CAPS `"ERROR: [extractor] id: ..."` formatted
  exceptions weren't being cleaned by the existing (case-sensitive) prefix-stripping logic,
  silently leaking the full raw message — including CLI-only hints — for any otherwise-
  unrecognized error. Now stripped for any future unrecognized case too, not just the ones found
  live this stage.
- Generalized the existing "image-only post" error message (previously Instagram-only wording)
  to also recognize Twitter/X's differently-worded equivalent, so both produce the same clean
  message instead of one falling through to a raw string.

### Verified — Compatibility Hardening Across the 7 Priority Social/Web Sources

- Ran a real (not simulated) compatibility pass against YouTube, Instagram, Facebook, Reddit,
  TikTok, X/Twitter, and Vimeo — reusing all existing architecture, no new backend, no
  authentication/anti-bot bypass anywhere. TikTok, X/Twitter, and Vimeo are new to the
  compatibility matrix this stage; the other four were already verified in earlier stages and
  were not redundantly retested.
- **X/Twitter video: now confirmed working end-to-end**, verified live on a Pixel 7a — analyze,
  real thumbnail/duration/format list, download, Library insertion, and correct video-player
  playback, all against a real public tweet.
- **TikTok video: currently broken upstream** in this pinned yt-dlp version (confirmed via 4
  independent real posts) — handled gracefully (a clean error, no crash), not worked around.
- **Vimeo video: currently requires login** on yt-dlp's default API client for this platform
  (confirmed via 3 independent real videos, and confirmed not bypassable via yt-dlp's own
  alternate API client without real credentials) — handled gracefully with the new clean
  login-required message, verified live on-device.
- See PROJECT_MASTER.md's 2026-08-29 decision log entry for the full compatibility matrix and
  exact per-case results.

### Changed — Torrent/Magnet Downloading Deferred from v1

- **Product scope decision, not a code change**: torrent/magnet-link downloading is deferred out
  of active v1 development so v1 can focus on social/web media downloading. No `TorrentEngine`
  code existed in the repository to remove — only PROJECT_MASTER.md's spec sections describing
  it, which are now annotated DEFERRED/FUTURE in place rather than deleted, for a future stage.
  No other abstraction (`ExtractorEngine`, `DownloadEngine`, `MediaProcessor`) changed — all were
  already backend-agnostic with nothing torrent-specific to undo.

### Added — Reddit Single-Image Support

- **MediaVault can now analyze and download single-image Reddit posts** (e.g.
  `reddit.com/r/.../comments/.../`, resolving to a direct `i.redd.it/...` image) — the same
  analyze → preview → download → Library → Image Viewer flow Instagram images already use, with
  no new backend or `MediaType`. Reddit video is completely unchanged.
- Extends the existing `YtDlpExtractorEngine`/`mediavault_ytdlp.py` path directly, per this
  stage's explicit brief: a cheap yt-dlp "light probe" (`process=False`) run only for
  `reddit.com`/`redd.it` URLs detects a single direct image before ever reaching the slower full
  pipeline. A resolved single image maps to the same `ExtractionResult.Collection`/
  `MediaCollectionResult` shape a single-image Instagram post already produces, so the UI needed
  no new code at all.
- **Multi-image Reddit galleries are explicitly unsupported and refused with a clear message**,
  not attempted or silently truncated to one image — confirmed by direct testing that yt-dlp's
  Reddit extractor has no reliable gallery support (a gallery post can hang the normal pipeline
  for 60+ seconds before failing).
- DRY fix in passing: extracted the "stream a resolved URL to a file" logic that
  `mediavault_instaloader.py`'s image download already had into a new shared
  `mediavault_direct_download.py` helper, now used by both the Instagram and Reddit image
  download paths instead of two copies of the same code.
- **Real routing bug caught during this stage's own review**: `MediaVaultDownloadEngine`'s
  download-engine routing hint unconditionally pointed every `MediaType.IMAGE` task at
  Instaloader — correct when only Instagram had images, but wrong now that Reddit images share
  the same `MediaType`, since Instaloader doesn't recognize `reddit.com` URLs at all. Fixed to
  only hint Instaloader for an actual `instagram.com` source URL.
- No new third-party dependency — reuses the `requests` package already bundled for Instaloader's
  download path.
- **Verified live on a physical device (Pixel 7a)** against a real public Reddit image post:
  correct thumbnail/title/source in the analysis preview, a single Download button (no gallery
  picker), completed download shown in Downloads and Library with the real thumbnail, "Open"
  correctly launching the Image Viewer (never the video Player) with the real downloaded photo,
  no crash. Gallery rejection and native-video/external-embed fallthrough (i.e., no regression
  in Reddit video handling from the shared bridge-module edits) were verified directly against
  the real, unmodified extraction code via a local Python harness.

### Added — Instagram Image and Carousel Support

- **MediaVault can now analyze and download Instagram image posts and carousels** — a single
  photo post gets a direct "Download" button; a carousel shows every image in order with its
  own real thumbnail, an item count, individual/range selection, and "Download all"/"Download
  selected" — reusing the exact multi-select toolbar pattern already built for video playlists.
  Instagram video (Reels) is completely unchanged — yt-dlp remains the sole video backend.
- A new `CompositeExtractorEngine` is the only thing bound to `ExtractorEngine` — the UI and
  `DownloadEngine` still depend only on that interface, never on yt-dlp or Instaloader directly.
  It tries yt-dlp first and only falls through to Instaloader when yt-dlp genuinely can't find a
  video for a URL it otherwise recognizes (an Instagram image post, most commonly) — it has no
  Instagram-specific knowledge of its own. Adding a further backend is one new `@IntoSet`
  binding, nothing else.
- Downloaded images reuse the *existing* single-item download path end to end (no second
  downloader implementation) and a real multi-image carousel groups its tasks using the same
  playlist-grouping/progress machinery the Downloads screen's "Playlists" section already had —
  independent per-item progress, pause/cancel, and duplicate detection (skip-already-downloaded),
  confirmed live by re-downloading a real carousel and watching two already-completed images get
  correctly skipped while the other three queued.
- A new, deliberately small image viewer (full-bleed image, title, Share) opens instead of the
  video/audio Player for any `MediaType.IMAGE` item, from both Library and Downloads — the video
  player itself is completely untouched.
- **Architecture correction found during implementation, not anticipated going in**: Chaquopy
  (the Python-on-Android bridge yt-dlp already used) only permits its Gradle plugin in a single
  module per app — a separate `core:extractor-instaloader` module built and packaged
  successfully but silently dropped its own Python source at runtime. Instaloader now lives
  inside the existing `core:extractor-ytdlp` module (its own Python environment now installs
  both `yt-dlp` and `instaloader`), in its own Kotlin package for code-level separation. See
  PROJECT_MASTER.md's decision log for the full account.
- **Two real bugs found and fixed during this stage's own Pixel 7a testing**: a real Instagram
  caption can run to several paragraphs, and was being used as a download title verbatim —
  overflowing the Downloads screen entirely before being capped to one short, ellipsized line;
  and downloading a *selected subset* of a carousel (e.g. only 2 of 5 images) numbered them
  against the selected batch's own size instead of the carousel's real size, producing a
  nonsensical label like "(4/2)" — now always numbered against the true collection size.
- New third-party dependency: [Instaloader](https://instaloader.github.io/) `4.15.3`, MIT
  License — see `THIRD-PARTY-NOTICES.md`.
- **Verified live on a physical device (Pixel 7a)** against real public Instagram posts (a NASA
  astronaut single-image post; a real 5-image NASA carousel): single-image analysis and
  download, Library appearance with a real file size, the image viewer (never the video
  player), carousel analysis with all 5 images shown in order, "Download selected" and
  "Download all" (the latter correctly skipping already-downloaded duplicates), preserved
  ordering after the duplicate-skip filter, and a full Home → Downloads → Library → Player →
  Settings navigation sweep with no crash.
- **Unsupported, by design**: Facebook images (still login-gated for every tool tested) and
  Reddit images (a separate, smaller follow-up scoped to the existing yt-dlp path) — neither
  was touched by this stage.

### Researched — Second Extraction Backend for Image/Social-Post Media

- **Research and proof-of-concept only — no app code changed.** Investigated whether a second
  `ExtractorEngine` backend could close the Instagram/Facebook/Reddit image gaps the Cross-Platform
  Media Compatibility QA pass recorded as real, tested limitations of yt-dlp. Tested `gallery-dl`
  and `Instaloader` in an isolated local Python environment (not Chaquopy, not the app) against
  real public posts, alongside the existing yt-dlp baseline for regression cases.
- **Instaloader (MIT license) passed every Instagram case tested, fully unauthenticated**: a single
  image post and a 5-image carousel both analyzed and downloaded successfully (real JPEGs, verified
  by file signature), with clean structured metadata (type, caption, per-item URLs, carousel
  count). A Reel resolved correctly too (video stays on yt-dlp regardless).
- **gallery-dl was rejected on two independent grounds**: it is GPL-2.0-licensed (verified against
  its own bundled `LICENSE` file), which conflicts with this project's MIT license the same way an
  earlier decision already rejected a GPLv3-licensed yt-dlp wrapper (see PROJECT_MASTER.md's
  2026-08-24 Chaquopy decision) — and, independent of licensing, it failed every test case tried
  here: Instagram (login redirect), Facebook (`AuthRequired`), and Reddit (blocked by an anti-bot
  challenge on `reddit.com`, reproducibly, even with a realistic browser User-Agent set).
- **Facebook image posts remain unsupported** — every tool tested (yt-dlp, gallery-dl) requires a
  logged-in session for photo content on a public page; no cookie-login flow is being added, per
  this project's no-unnecessary-accounts privacy default. Documented as a confirmed limitation.
- **Reddit's image gap turned out not to need a second backend at all**: yt-dlp's own Reddit
  extractor already resolves the correct direct image URL (via its existing unauthenticated "old
  reddit" session handshake) — it just has no code path to save a resolved image URL's bytes
  once it hands off to the generic extractor. A plain HTTP fetch of that same resolved URL
  succeeded in the proof-of-concept (real JPEG, 312 KB). This is scoped as a separate, much
  smaller future fix to the existing yt-dlp path, not part of this research's recommendation.
- **Recommended architecture** (proposed for a future milestone, not built): a `CompositeExtractorEngine`
  behind the unchanged `ExtractorEngine` interface (Hilt multibinding over yt-dlp + a new
  `InstaloaderExtractorEngine`, yt-dlp tried first); `MediaType.IMAGE` as a new stored type;
  carousels modeled as a new `ExtractionResult.Collection` (analysis-layer only, never a
  `MediaType`) that reuses the existing playlist download/queue infrastructure
  (`DownloadEngine.enqueuePlaylist`) unchanged — a carousel download is architecturally just a
  playlist of images. Full reasoning, the complete test matrix, and license/runtime findings are in
  PROJECT_MASTER.md's Decision Log (2026-08-28 entry).
- **Recommendation: proceed with Instagram image support via Instaloader** in a future, explicitly
  scoped milestone; do not adopt gallery-dl; do not build Facebook image support without a real
  authentication story. Nothing from this research is integrated into the app yet.

### Fixed — Merged Formats in Playlist Downloads

- **Playlist downloads no longer disable formats that require a separate video+audio merge.**
  The playlist quality-picker previously rejected every video-only format outright
  ("Requires merging — not available yet"), even though the single-item picker had already
  supported FFmpeg merging for a while — this was a gap in the playlist quality-selection
  path specifically, not a missing merge capability.
- The playlist picker now reuses the exact same `buildDownloadOptions()`/`DownloadOption`
  pairing the single-item screen uses (same Video/Audio sections and rows), and each playlist
  item resolves its own compatible video+audio pairing independently via a new
  `List<DownloadOption>.findMatching()` — no duplicated merge or pairing logic. The selected
  audio language is preserved and matched exactly on every other item; an item missing that
  exact resolution+language pairing fails clearly for that item alone, never a silent
  substitution, and never blocks the rest of the playlist.
- The playlist setup bar's estimated total size now sums video+audio per item
  (`DownloadOption.combinedEstimatedSizeBytes`) for a merge-required quality, instead of a
  single format's size.
- Room database bumped to schema version 6 with another purely additive migration
  (`qualityRequiresProcessing`/`qualityAudioLanguageCode` on `download_tasks`) — every
  pre-existing playlist task is unaffected. Playlist order, duplicate detection, retry,
  pause/resume, cancellation, and process-death recovery are all unchanged.
- **Verified live on a physical device (Pixel 7a)** against 2 items selected from the real,
  legitimate "Official Blender Open Movies" YouTube playlist (genuine split video/audio DASH
  streams): selected a 4K merge-required quality that was previously disabled, queued it, and
  confirmed via logcat that FFmpegKit ran a real merge session — the same engine path the
  single-item flow already used. The item completed, appeared in Library with correct
  duration/resolution/size, and played back with a real `AudioTrack` session and visible video.
  The second selected item — whose source genuinely doesn't offer that exact
  resolution+language pairing — failed independently with a clear message while the first item
  completed, confirming per-item failure isolation. A single-video merged download (a
  different source, Sintel) was re-run afterward and completed normally — no regression.
- **Known limitation**: a merge-required quality still matches by exact audio-language
  identity only; an item offering the same resolution in a different language than the one
  first resolved fails that item rather than substituting a different language.

### Fixed — Cross-Platform Media Compatibility QA (YouTube/Instagram/Reddit/Facebook)

A live QA pass against real public URLs across all four platforms, using the app's actual
analyze → format-select → download → Library → player pipeline (not just checking the
Supported Sources catalog). Full platform/media-type matrix in `PROJECT_MASTER.md`'s decision
log. Summary: 6 of 9 tested cases PASS end-to-end; all 3 image-post cases came back
UNSUPPORTED/SOURCE ERROR (a real yt-dlp/platform-side limitation, not a MediaVault gap — see
below), so no speculative `MediaType.IMAGE` architecture was built with nothing real to
validate it against.

- **Fixed a real defect found via the mandated Reddit video test**: a video-only format with
  no audio-only track *anywhere* in the source (a genuinely silent clip — common for
  Reddit-hosted videos/GIFs) was being disabled entirely ("No audio track is available to
  merge with this resolution"), even though there was nothing to merge — the file was already
  complete. `buildDownloadOptions` now treats that case as a direct, selectable,
  no-processing option, same as a muxed file. The genuinely-different "audio exists elsewhere
  but nothing pairs with this resolution" case (`DownloadOption.unavailableReason`) is
  documented as effectively unreachable today, given the existing any-audio-track pairing
  fallback — kept as a defensive invariant, not removed as dead code.
- **Clearer error text** for one real failure mode hit during QA: yt-dlp's Instagram extractor
  raises "There is no video in this post" for a single-image post; this now maps to a plain
  "This post doesn't contain a video MediaVault can download." instead of showing the raw
  Python exception string. No new workaround or bypass — the post is still correctly
  unsupported, just worded clearly.
- **Confirmed working, unchanged**: YouTube video/audio-only/playlist detection, Instagram
  Reel, Facebook video — all verified end-to-end on a physical device (Pixel 7a), including
  Library insertion and the correct video/audio player presentation.
- **Confirmed real, unfixed limitations** (recorded accurately, no workaround attempted per
  this milestone's explicit instruction): Instagram image posts and Facebook photo-permalink
  URLs are both unsupported by yt-dlp's own extractors for those platforms (video-first
  design); a Reddit image post reproducibly failed with a source/network-level error fetching
  the actual image asset (not a MediaVault-side bug). Separately, **YouTube playlist bulk
  downloads that require video+audio merging were blocked entirely** ("Requires merging — not
  available yet") in the playlist quality-picker specifically — a real, pre-existing gap
  distinct from this milestone's media-type-detection scope, left unfixed here since fixing it
  means touching the playlist download-options path, not the video/audio/image classification
  this QA pass targeted. **Fixed in the "Merged Formats in Playlist Downloads" stage above.**

### Added — Dedicated Audio Player Mode

- Audio-only media (no video track) now opens a dedicated audio-player presentation instead
  of the video-player UI: artwork/placeholder, title, scrubber, Play/Pause, Previous/Next,
  ±10s, Speed, audio-track selection (when more than one track exists), loop, sleep timer,
  and details — with no video canvas, no black video area, no fullscreen, no
  Picture-in-Picture, no aspect-ratio controls, and no video-surface touch gestures.
- Media-type detection prefers the player engine's real, live track metadata the moment
  it's known (a new `PlaybackState.hasVideoTrack`, populated from Media3's actual `Tracks`)
  over the library item's stored/guessed `MediaType`, falling back to the stored value only
  for the brief window before the engine has reported real tracks — so a misclassified file
  still gets the correct presentation once it starts playing.
- One `PlayerEngine`, one `PlayerScreen`/`PlayerViewModel`/route — no second playback engine
  and no separate navigation path. The scrubber and transport row are shared, unduplicated
  composables (`ScrubberAndTimeRow`/`TransportRow`) used by both the video and audio control
  panels; only the surrounding video-only chrome (resize/subtitle/PiP/fullscreen) differs.
- Previous/Next and playlist/queue ordering are unchanged and untouched — they were already
  media-type-agnostic (keyed purely on playlist index, never on `MediaType`).
- Speed control is always shown for audio (music and spoken word alike): nothing in the
  current data model distinguishes the two, and hiding it selectively would mean guessing.
- **Verified on a physical device (Pixel 7a)** against a real imported audio file: the audio
  player opens (confirmed via the accessibility tree — zero `SurfaceView`, and none of the
  fullscreen/PiP/aspect-ratio/subtitle controls present), Play/Pause and the scrubber work,
  the position freezes correctly on pause, and reopening the item after returning to Library
  resumes near the last position rather than restarting from zero.

### Added — Private App Lock + Biometric Protection

- **Settings → Security**: a new App Lock toggle, gated behind creating a 4-digit PIN first
  (setup happens inline via a dialog the moment the toggle is switched on). Once enabled:
  an optional "Unlock with biometrics" toggle (shown only when the device actually has
  strong biometric hardware enrolled — `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)`),
  an auto-lock timeout picker (Immediately / 30s / 1 min / 5 min), and a Change PIN action
  (requires the current PIN first). Disabling App Lock also requires the current PIN and
  clears the stored PIN/biometric toggle — re-enabling always starts from a fresh setup.
- **Lock screen**: a full-screen, opaque gate (MediaVault logo, PIN keypad with dot
  progress, biometric button when enabled) shown on cold launch when App Lock is enabled,
  and again after returning from background past the configured timeout. Auto-triggers the
  system `BiometricPrompt` when biometric unlock is on; "Use PIN" falls back to the keypad.
  5 consecutive wrong PINs trigger a 30-second lockout with a live countdown.
  Nothing from Library/Downloads/Player/Settings is ever composed underneath — verified on
  a physical device that no title/thumbnail reaches the accessibility tree while locked.
- **Playback**: a video already playing pauses the instant the app locks (position is
  preserved, same as any other pause) rather than continuing silently behind the lock
  screen — confirmed on-device via the actual `AudioTrack` transitioning to `state:paused`.
  Picture-in-Picture and in-app fullscreen do not trigger a relock, since neither causes
  `ProcessLifecycleOwner`'s `ON_STOP` to fire while the window stays visible.
- **Privacy**: `FLAG_SECURE` is applied to the whole app while App Lock is enabled (blocks
  screenshots and recent-apps thumbnails of Library/Downloads/Player). The download
  foreground-service notification's video-title text is replaced with a generic
  "Downloading…" while App Lock is enabled, closing a leak path independent of the lock
  screen itself.
- **Storage**: the PIN is never stored raw or logged — only a PBKDF2-SHA256 salted hash
  (standard `javax.crypto`/`java.security` APIs, no custom cryptography), kept in
  `EncryptedSharedPreferences` (Android Keystore-backed), never in Room or a plain
  DataStore/SharedPreferences file. Non-secret settings (enabled, biometric toggle,
  timeout) use the app's normal per-feature DataStore convention.
- New dependencies: `androidx.biometric`, `androidx.security:security-crypto`,
  `androidx.fragment` (`MainActivity` is now a `FragmentActivity`, required by
  `BiometricPrompt`), `androidx.lifecycle:lifecycle-process` (`ProcessLifecycleOwner`).
- **Verified on a physical device (Pixel 7a)**, including a real fingerprint touch for the
  biometric path. Found and fixed one real bug during this pass: the biometric prompt
  didn't auto-trigger on a genuine cold app restart (a `LaunchedEffect(Unit)` captured a
  stale `false` before the App Lock settings' first async DataStore emission arrived);
  fixed by keying that effect on the setting value itself.

### Added — Share MediaVault, GitHub Star Call-to-Action, and Centralized Feedback Email

- **Share MediaVault** (Settings → About): opens the system share sheet with a plain-text
  message naming the GitHub repository URL and inviting the recipient to check out the
  project, star it, and share feedback. The app never claims to star the repository
  automatically — it only hands text to the OS share sheet.
- **Star this project on GitHub** (Settings → About): a worded call-to-action that opens
  the repository in the browser, alongside the existing "View on GitHub" row. Purely
  informational/voluntary — no in-app GitHub authentication or automatic starring.
- **Send Feedback** (Settings → Feedback & Contact) now targets a real, centrally
  configured address (`AppConfig.FEEDBACK_EMAIL`) instead of falling back to GitHub
  Issues. Launches the device's default email app via `ACTION_SENDTO`/`mailto:`,
  pre-filling subject ("Feedback for MediaVault") and a body with the app version,
  device, and Android version. If no email app is installed, shows the address inline
  with a copy-to-clipboard action instead of failing silently. No analytics are
  collected or sent automatically.
- All three actions read from the existing `AppConfig` single source of truth; no new
  hard-coded URLs/strings were introduced elsewhere in Settings.
- **Verified live on a physical device (Pixel 7a)**: a report that "Share MediaVault" was
  invisible traced to a stale debug APK on the test device (built before this feature's
  commits) rather than any UI defect — no source changes were needed. After rebuilding and
  reinstalling, the row is visible in About, opens the Android share sheet, and the shared
  text correctly contains the GitHub repository URL with no automatic starring.

### Added — Subtitle Display Styles

- The Subtitles menu in the player now includes a "Style" section with three subtitle
  appearances: **Classic** (white text, black semi-transparent background), **Clean**
  (white text, no background — the new default), and **Outlined** (white text, no
  background, subtle black outline for readability). The choice is persisted across every
  video and app session (DataStore, same pattern as the theme and audio-language
  preferences) and is completely independent of the app's own Light/Dark/System theme.
- Built on the existing Media3 subtitle-rendering path — `PlayerView`'s `SubtitleView` via
  `CaptionStyleCompat` — with no new dependency. The three styles are defined once as a
  small, pure (non-Android) `SubtitleStyleSpec` mapping (`app/player/SubtitleStyle.kt`),
  reused rather than hard-coded inline. Subtitle-track selection/enumeration is untouched.
- **Verified live on a physical device (Pixel 7a)** against the existing
  `multitrack_test.mp4` fixture: all three styles render visibly distinct and switch
  correctly, subtitle-track selection (en/es) keeps working under any style, and no
  layout/control regression in either embedded or fullscreen playback.

### Fixed — Downloads Open Silent Failure, Subtitle-Track Verification Correction

- **Downloads → Open silently did nothing** in the one case where a completed task's
  Library row could no longer be resolved (e.g. deleted/renamed after the download
  finished) — `DownloadsViewModel.openInPlayer()` returned early with no feedback at all.
  Now shows an inline error card ("Couldn't find this item in your Library. It may have
  been removed or renamed."), reusing the same error-card visual convention `HomeScreen`
  already uses elsewhere in the app.
- **Correction**: an earlier changelog entry (Player, Navigation & Downloads UX
  Stabilization, below) claimed subtitle-track switching was verified alongside
  audio-track switching, but `PROJECT_MASTER.md`'s corresponding entry only documented the
  audio-track verification — the two project docs disagreed. Re-verified live on a Pixel
  7a this stage against the same `multitrack_test.mp4` fixture: both "en" and "es"
  subtitle tracks render distinct, correct text when switched. The original claim was
  accurate; it just hadn't been independently confirmed until now. Both docs agree as of
  this entry.
- No changes to Home navigation, the Player back-transition, Player gestures/controls, the
  Player tab's watch-history sections, or Picture-in-Picture — all were re-confirmed
  already correct in the current code before being left untouched.

### Fixed — Source Details Back Transition Ghosting

- Sources -> Source Details -> Back was showing both screens' text/icons/buttons alpha-blended
  together for the full transition (~220ms, perceived as longer since nothing about the outgoing
  screen was fading), instead of a clean crossfade.
- Root cause: the Player-pop fix's `popExitTransition = ExitTransition.None` was set globally on
  `MediaVaultNavHost`'s `NavHost`, so it applied to every route's pop, not just the dedicated
  Player screen it was written for. Leaving an *ordinary* Compose screen fully opaque and
  un-faded while the destination fades in on top of it double-exposes both screens' pixels —
  correct only for Player's `SurfaceView`, which sits outside Compose's alpha pipeline and isn't
  affected by `fadeOut()` at all (see the 2026-08-27 Player<->Library decision log entry).
- Fix: `popExitTransition` now checks whether the entry being popped is the dedicated Player
  route; only then does it use `ExitTransition.None`. Every other pop (Source Details, Library,
  Downloads, etc.) got back its symmetric `fadeOut(tween(220))`, matching every other transition
  in the app. No navigation architecture change — same `NavHost`-level transition mechanism, now
  scoped correctly.

### Added — Source Descriptions

- The Source detail page now shows a compact 1-2 sentence description of what the platform is
  and what kind of media it generally carries (e.g. Instagram: "Social media platform for
  sharing photos, videos, Stories and Reels."), alongside the existing logo, name, domain,
  categories, and extraction-engine support status.
- `Source` gained a new optional `description: String?` field (curated only; defaults to
  `null`). A small hand-curated map (`CuratedSourceDescriptions.byId`, ~20 major services —
  YouTube, Instagram, Reddit, TikTok, etc.) lives in `core/domain/source/SourceDescriptions.kt`,
  applied once at catalog-load time (`SourceCatalog.withCuratedDescriptions()`, called from
  `YtDlpSourceCatalogRepository`) — deliberately not written into the generated
  `source_catalog.json` asset, so the ~1,027-service catalog doesn't carry ~1,000 placeholder
  descriptions and regenerating it via `generate_source_catalog.py` never touches descriptions.
  Every source without a curated entry gets a generic, category-based fallback (e.g. "A video
  source supported by MediaVault's extraction engine.") via `Source.displayDescription()` — the
  single place the curated-vs-fallback decision is made, reused by the UI as-is.
- No changes to the catalog generator, extractor, downloader, player, or navigation. Existing
  search, category filters, A-Z grouping, aliases, and favicon fallback are all unaffected.

### Fixed — Supported Sources Search Accessibility

- The search field on the Supported Sources screen no longer permanently scrolls away once
  the user is deep in the alphabetical list. It now fades/slides back in as a floating bar
  whenever the user scrolls up, and hides again on scroll down — reachable from anywhere in
  the list without a forced scroll back to "A".
- Implemented with `NestedScrollConnection` (observes scroll direction ahead of the
  `LazyColumn` consuming it, consumes nothing itself) driving an `AnimatedVisibility` overlay
  that reuses the same `SourcesSearchField` composable as the original inline field — no new
  dependency, no change to catalog/extractor/filter logic.

### Fixed — Network Timeout Messaging

- Link-analysis failures caused by a genuine connection timeout (yt-dlp's "... timed out" errors)
  now get their own `AppError.Timeout` classification instead of being folded into the generic
  `AppError.Network` case. The user-facing message is now: "Connection timed out. This source may
  be unavailable or blocked on your current network." — worded as a possibility, not an asserted
  cause (a slow source, an unreachable host, and a network-level block all look identical from the
  client side).
- Root cause this fixes: a real timeout (e.g. analyzing a site blocked/unreachable on the current
  network) previously surfaced the same generic "Couldn't reach the source" text as every other
  network failure (DNS failure, connection refused, etc.), giving the user no signal that the
  problem might be their network rather than a transient glitch.
- `core/extractor-ytdlp/YtDlpErrorMapper.kt`'s `PyException.toAppError()` now checks for "timed
  out" before the generic network-failure branch; no extractor, networking, or downloader logic
  changed. Covered by 3 new/updated unit tests in `YtDlpErrorMapperTest`.

### Fixed — Downloads "Open", Home Tab Reset, Player Back Transition

- **Downloads → Open did nothing**: "Open" on a completed download launched an external
  viewer `Intent` on the app's private `file://` URI, which the platform silently rejects
  (`FileUriExposedException`) before any app chooser ever appears — the button had no
  working effect. Now resolves the Library row the download produced
  (`MediaItemEntity.sourceDownloadTaskId`) and navigates to the exact same `player/{id}`
  route Library itself uses — same playback path, same position-resume behavior, no
  second implementation.
- **Home tab didn't reset**: Home is the nav graph's start destination, so switching tabs
  never actually removed it from the back stack — its ViewModel (and an in-progress or
  completed link analysis) stayed alive indefinitely. Tapping Home now always shows the
  clean default screen; device status (storage/network) is kept, not re-fetched.
- **Player → Back transition popped abruptly**: `PlayerView`'s `SurfaceView` composites
  outside Compose's draw/alpha pipeline, so the shared `fadeOut()` used for every other
  pop transition animated everything *except* the actual video pixels — the destination
  faded in on schedule while the still-fully-opaque video sat frozen, then vanished in
  one frame the instant the transition's duration elapsed. The Player pop's exit
  transition no longer tries to fade the video at all (`ExitTransition.None`); it stays
  fully visible, unanimated, until the incoming screen's fade-in has already covered it,
  so there's nothing left to visibly pop. `TextureView` was deliberately not
  reconsidered for this — it previously caused a critical real-video-rendering
  regression (see below) and stays out of scope here.
- **Verified live on a physical device (Pixel 7a)**: Downloads → Open → Player with
  correct resumed position; Home tab showing clean after analyzing a link, navigating
  away, and back; Library → Player → Back with content visibly intact (not black, not
  popped) mid-transition, confirmed via a screenshot caught mid-animation; a full
  Library → Player → Back round trip; rapid tab-switching with no artifacts. 175 unit
  tests pass (3 new for the Open-in-Player lookup, 3 new for the Home reset), debug APK
  builds and installs clean.

### Changed — Player ↔ Library Transition Polish

- Navigating between the Library and the dedicated Player screen (and between any two
  routes) now crossfades over 220ms instead of cutting instantly.
- Playback position/state and engine release behavior were already correct and
  untouched — audio/video now keep running through the fade and stop only once it
  finishes, instead of cutting off the instant back is pressed. Targeted unit tests
  cover this exact boundary (`PlayerViewModelTest`): leaving the screen pauses and
  persists without releasing the engine, and clearing the ViewModel releases the engine
  and persists the final position.
- **Correction, verified live on a physical device (Pixel 7a):** an earlier version of
  this change forced `PlayerView` onto a `TextureView` (via a themed
  `ContextThemeWrapper` + `styles.xml`) to fix the video surface popping during the
  fade. On-device testing found this silently broke real frame rendering instead — the
  video area came up solid black on every source tried, in both the embedded and
  fullscreen layouts, while audio/position continued normally underneath. Reverted to
  the default `SurfaceView` construction; see the Player/Navigation/Downloads
  Stabilization entry below for the full verification.

### Added — Player, Navigation & Downloads UX Stabilization

- **Downloads**: added a real Remove action for Failed/Cancelled/Completed tasks
  (`DownloadEngine.remove` — deletes only the task's own queue row, never the Library
  media a completed task produced; a completed-task removal asks for confirmation with
  copy that says so explicitly). Fixed a real bug where an in-progress split
  video+audio download (`MERGING`) fell into no visible section at all, disappearing
  from the list while it merged. Cancelled now has its own section, no longer merged
  into Failed.
- **Player gestures**: rewritten to the exact required contract — single tap only
  shows/hides controls and never seeks; double-tap left/right seeks ∓10s; triple-tap
  seeks ∓30s; long-press still gives temporary 2x, restoring the exact prior speed on
  release. The zone/tap-count decision is a small, pure, unit-tested function
  (`resolveTapAction`) separate from the pointer-timing plumbing.
- **Player tab**: replaced the single-item "Continue watching" card with real Continue
  Watching / Recently Watched sections showing every in-progress or finished item
  (thumbnail, title, progress, remaining time, tap-to-resume) — added
  `lastWatchedAtEpochMs` to the Library schema (migration 4→5, backfilled for
  already-in-progress rows) to drive it.
- **Library menu**: an imported/external item's three-dot menu now shows only
  Play/Share/Details/Remove from Library — Save to device and Rename (neither of which
  can act meaningfully on a file MediaVault doesn't own) no longer appear for it.
  MediaVault-owned downloads keep the full menu.
- Extracted a shared `MediaThumbnail` component (Downloads/Library/Player tab all used
  near-identical thumbnail-with-fallback code).
- **Verified live on a physical device (Pixel 7a)**: found and fixed two real defects
  device-testing surfaced that no amount of code review would have caught — (1) the
  `TextureView` change from the previous stage was silently breaking video rendering
  entirely (see the corrected entry above); (2) the new watch-history card's `Row` was
  missing `fillMaxWidth()` under a `weight(1f)` child, and its thumbnail column had no
  fixed width under a `fillMaxWidth()` progress bar — together this collapsed the title
  to zero width, rendering the remaining-time label one character per line. Both fixed
  and re-verified on-device. Also confirmed: Downloads remove (task disappears, Library
  media untouched), Library→Player→back, multi-item Continue Watching/Recently
  Watched, single/double/triple-tap and long-press-2x on real touch input, portrait and
  landscape-fullscreen playback, audio/subtitle track menus against a legitimate
  multi-track local test fixture (2 audio + 2 subtitle streams — real content, not
  claimed), and rapid tab-switching with no stale state or visual artifacts. 169 unit
  tests pass. **Not exercised this session**: exact ±10s/±30s seek magnitudes live
  (confirmed instead by unit test, since adb-driven tap timing isn't precise enough to
  isolate a seek from concurrent playback). Picture-in-Picture was verified in a
  follow-up session — see below.

### Verified — Picture-in-Picture, End-to-End on a Physical Device

- **Verified live on a physical device (Pixel 7a), no code changes needed**: opened a
  Library video, started playback, entered PiP (video continued rendering correctly in
  the floating window — confirmed across multiple frames over several minutes of real
  elapsed time, not just a static first frame), tapped the PiP window to restore to the
  full player (position had advanced correctly the whole time, e.g. 3:37 → 7:45 across
  ~4 real minutes), and confirmed normal Library/Player-tab navigation still worked
  immediately afterward, including the finished item correctly appearing under Recently
  Watched with a fully-filled progress bar. No regressions found; no fix required.

### Added — Local Media Import & Privacy-First Storage/Export

- Library gained an explicit "Add media" action (Import a file / Import a folder) using
  the Android system document picker (`ACTION_OPEN_DOCUMENT` for one file,
  `ACTION_OPEN_DOCUMENT_TREE` for a folder). Nothing is ever scanned automatically —
  MediaVault only ever indexes a file or folder the user explicitly picked through the
  OS's own picker, and a folder import only looks at that one folder's direct contents,
  never subfolders or anywhere else on the device. No broad storage/media permission is
  requested anywhere in this feature.
- Imported media is indexed with real detected metadata — duration, resolution, file
  size, and (for video) a thumbnail taken from an actual frame, or (for audio) embedded
  cover art when the file has one — using Android's built-in `MediaMetadataRetriever`,
  no new metadata dependency. A persisted read grant (`takePersistableUriPermission`)
  is taken where the provider supports it, so the import survives an app restart; only
  the reference and probed metadata are stored, never a copy of the file's bytes.
- Imported items play through the existing Media3 player unchanged — it already
  supported `content://` sources from the earlier SAF-based download milestone.
- Library now clearly distinguishes where a file's bytes actually live: a plain
  MediaVault download shows no badge, an imported item shows "Imported", and a download
  later saved to the Gallery shows "In Gallery". "Remove from Library" (the label used
  in place of "Delete" for anything MediaVault doesn't own the file for) only ever
  removes the Library row — it never deletes an imported or Gallery-owned file, and the
  Details dialog now shows this same Origin.
- The three-dot menu's "Export" became "Save to device", opening a Gallery/Files
  choice: "Save to Files" is the existing `ACTION_CREATE_DOCUMENT` export unchanged;
  "Save to Gallery" is new, publishing into `MediaStore` (API 29+; older OS versions
  get a clear "use Save to Files instead" message rather than the broad legacy
  `WRITE_EXTERNAL_STORAGE` permission). For a MediaVault-private download specifically,
  saving to Gallery is a real *move*, not a duplicate: once the Gallery copy is written
  and verified, the redundant private copy is deleted and the Library row is repointed
  at the Gallery file, so the steady state is one physical file, not two — see
  PROJECT_MASTER.md's storage-architecture decision log entry for why Android has no
  zero-copy way to do this directly, and why a copy-then-delete-source is the most
  storage-efficient option actually available. A `content://`-sourced item (imported,
  or a legacy SAF download) is never eligible for this move — MediaVault doesn't own
  that document, so "Save to Gallery" stays a plain copy there, exactly like Files
  export.
- Handles the real-world edge cases this feature invites: a moved/deleted external
  file (existing "File missing" badge, now correctly covers imported items via the
  same `content://` existence check), a revoked/unsupported persistable permission
  (best-effort — the import doesn't fail outright, only the "survives a restart"
  guarantee for that item), an inaccessible folder, and a non-media file mixed into a
  folder import (silently skipped and counted, never fails the whole batch).
- **Verified live on a physical device (Pixel 7a)**: imported a single video (correct
  duration/resolution/size/thumbnail), imported a folder containing two videos and one
  audio file (all three indexed, zero skipped, correct per-file metadata), played an
  imported audio file through the real Player screen, removed an imported item from
  Library and confirmed via `adb` that the original file on disk was untouched,
  force-stopped and relaunched the app and confirmed every imported entry (and the
  earlier removal) persisted, then deleted the underlying file out from under a still-
  Library-listed imported item and confirmed it degraded to a "File missing" badge
  instead of crashing or silently disappearing.

### Added — Global Theme System

- Settings now has a real Appearance section with a Light/Dark/System default theme
  picker, replacing the placeholder-only screen. The choice persists (DataStore) and
  survives app restart.
- Added a true dark, AMOLED-friendly color scheme (near-black surfaces, a lighter blue
  accent tuned for contrast on dark) alongside the existing light/blue design — the
  light scheme stays the default. Every screen, dialog, menu, and the bottom
  navigation bar re-themes automatically because they already read colors from
  `MaterialTheme.colorScheme` rather than hard-coded values; no per-screen changes
  were needed. The dedicated Player screen's own video canvas (fullscreen
  letterboxing, gesture overlay scrims) intentionally stays black/white regardless of
  app theme, matching standard video-player UX — its embedded chrome was already
  theme-token-driven.
- Startup theme flash prevention: a `values-night` variant of the app's native window
  theme covers the pre-Compose frame for the common "follow system" case, and
  `MainActivity` resolves the persisted preference synchronously before `setContent`
  (a single small DataStore read) to set the correct window background and system bar
  icon style for the explicit-override case too, then keeps both in sync reactively
  as the theme changes at runtime.
- No separate player-only theme setting was added — the Player screen follows the
  same global choice as every other screen.
- Confirmed the Downloads screen already showed downloaded/total size, download
  speed, and ETA (`DownloadTaskCard.progressDetailLabel`) before this stage — left
  unchanged, not duplicated.
- **Verified live on a physical device (Pixel 7a)**: launched cold with the device's
  system theme in dark mode and confirmed the app followed it (System default);
  switched to Light and Dark from Settings and confirmed every visible surface —
  Home, the bottom nav bar, cards, Settings itself — re-themed instantly and
  correctly on each switch; force-stopped and relaunched with Dark explicitly
  selected and confirmed it was still selected (not reverted to System) after the
  cold restart; confirmed the Downloads screen (playlists, task cards, status colors,
  progress bars) still renders correctly in dark mode with no regression. Not
  exercised this session: Player/Library/Supported Sources screens' dark rendering,
  and an in-progress download's live speed/ETA line in dark mode specifically (both
  rely on the same shared `MaterialTheme.colorScheme` tokens verified elsewhere and
  were out of this stage's testing scope per its own instructions).

### Added — Format Selection & Download UI Redesign

- The single-item format picker now groups every format into labeled Video/Audio
  sections (sorted highest-to-lowest resolution within Video), instead of one flat
  radio-button list. Each Video row shows resolution, fps, container, codec, the
  final estimated size (already the video+audio sum for a format that needs merging),
  and — never omitted — whether audio is already included, will be merged in after
  download, or genuinely isn't available for that resolution. Each Audio row shows
  its format/container, codec, bitrate, and estimated size. A defensive "Other"
  section exists so a future format shape the picker doesn't yet classify still shows
  up rather than silently vanishing.
- A new persistent bottom Download bar stays visible while the format list scrolls:
  disabled with a prompt until a format is selected, then showing the selected
  quality and its estimated size with an enabled Download button. The playlist
  quality-setup step gets the equivalent persistent bar — selected item count, chosen
  quality, and the running total estimated size — with the Queue action moved out of
  the scrolling card and into that bar.
- `NetworkPolicyManager` is now consulted *before* a download is queued, not only
  once it actually starts: a hard block (e.g. today's mobile-data budget is used up)
  is shown immediately and never queued; a soft warning (may exceed the remaining
  budget) now requires an explicit "Download anyway" confirmation before it proceeds,
  never silently; a "wait for Wi-Fi" decision still queues the task but tells the
  user up front that it will wait. This applies to both the single-item and playlist
  queue paths, priced against the whole batch for playlists.
- `MediaFormat` gained `bitrateKbps`, mapped in `YtDlpResultMapper` from yt-dlp's own
  `abr` (falling back to `tbr`) — never estimated — so audio rows can show a real
  bitrate instead of just a size.
- Fixed a pre-existing display bug where a muxed direct format (already containing
  its own audio track) was unconditionally labeled "video only" in the format list,
  because the old summary only checked for a separately-paired audio format rather
  than the video format's own `hasAudio`.
- **Verified live on a physical device (Pixel 7a)**: analyzed a real YouTube source
  (Big Buck Bunny 60fps 4K) offering split video-only/audio-only DASH streams —
  confirmed the Video section sorted 4K → 1440p → 1080p → 720p → 480p → 144p with
  correct resolution/fps/container/codec/size/audio-availability text on each row,
  the Audio section below it showing real bitrates (129/66/65/50/49 kbps) mapped from
  yt-dlp's `abr`, and the persistent bottom bar staying pinned through the entire
  scroll while correctly toggling disabled↔enabled and updating its quality/size text
  as different rows were selected. Selected a WEBM/opus audio-only format and tapped
  Download; confirmed directly in the app's own Room database (pulled via `adb`) that
  the resulting task reached `COMPLETED` with the exact `formatId` selected in the UI,
  proving the network-policy-gated enqueue path genuinely runs end-to-end, not just
  navigates away optimistically. Not exercised live this session: the playlist
  quality-setup bar (no playlist test URL was rehearsed this session) and a
  network-policy Block/Warn/QueueForWifi decision (the test device had no configured
  mobile-data budget restriction to trigger one) — both are covered by unit tests
  (`HomeViewModelTest`) but not confirmed on-device.

### Added — Player Controls & Gestures Polish

- Popup menus (Speed, Audio, Subtitle, Aspect-ratio, Sleep timer) now open anchored
  directly next to the button that opened them, with a proper trailing checkmark icon
  for the selected option — instead of every menu opening at the same fixed position
  regardless of which icon was tapped.
- Fullscreen now rotates the device to landscape for landscape/square videos (like
  mainstream video apps), and rotates back to portrait automatically when fullscreen is
  exited — playback never stops or glitches through the rotation. Portrait videos are
  never forced into a landscape frame. The fullscreen controls now also stay clear of
  notches, camera cutouts, and gesture-navigation areas in both orientations.
- New YouTube-style touch gestures on the video itself: tap the left third to rewind 10
  seconds, tap the right third to skip forward 10 seconds (both with a brief on-screen
  "-10s"/"+10s" bubble), and hold anywhere to play at 2x — releasing restores the exact
  speed that was active before the hold, not just a default 1x.
- Sleep timer options changed to 15/30/60 minutes, "End of this video," or Off.
- Slightly more breathing room around all controls so nothing sits flush against the
  screen edge.
- **Verified live on a physical device (Pixel 7a)**: three different popup menus each
  opened next to their own icon; left/right seek gestures measured pixel-exact (10
  seconds each way); the hold-for-2x gesture engaged and released correctly, restoring a
  genuinely non-default prior speed; fullscreen rotation to landscape and back to
  portrait both worked with playback uninterrupted; Picture-in-Picture still works after
  the gesture rewrite. Not exercised this session (no suitable test file): a 9:16
  portrait source, and multi-audio-track/subtitle menus.

### Added — Player Redesign

- The Player is now a dedicated, immersive playback screen instead of a sixth piece of
  five-tab content. Opening a Library item or the Player tab's "Continue watching" card
  hides the bottom navigation entirely; the Player tab itself now shows only a
  lightweight resume card and no longer silently starts audio playing in the background
  just from being visited (a real bug in the previous design, fixed by construction).
- Video sizing now matches the source's real aspect ratio instead of assuming 16:9 —
  landscape/square content is sized compactly to its own shape, portrait content uses the
  available height centered, and there is no reserved empty space beyond what the video
  itself occupies. Fullscreen now overlays floating, auto-hiding controls over the full
  video instead of squeezing it into a smaller box.
- New controls: -10s/+10s, loop, a sleep timer (fixed durations or "end of this video"),
  Picture-in-picture, an aspect-ratio picker (Fit/Fill/Zoom/Original), and a media details
  dialog (shared with the Library's existing one). Previous/Next now appear for playlist
  items only, preserving playlist order; reaching the end of a playlist item auto-advances
  to the next, and reaching the end of standalone media offers "replay from the start."
  The last audio language a user picks is remembered and auto-applied to the next file
  that offers a track in the same language.
- **Two real layout bugs found and fixed during this stage's own device testing**: a
  "giant black empty area" bug the milestone was meant to eliminate initially reappeared,
  relocated rather than fixed, due to an unconditional black background bleeding into
  unused layout space; and a separate bug left a persistent gray strip where the status
  bar used to be in fullscreen, because the shared navigation Scaffold's inset padding
  doesn't react to this screen's own system-bar hide/show calls. Both were caught and
  fixed live on a Pixel 7a within this same session — see `PROJECT_MASTER.md` §34/§37 for
  the full root-cause writeup.
- **Verified live on a physical device (Pixel 7a)**: Library → Player and Player tab →
  Player, aspect-correct layout in both embedded and fullscreen (the two bugs above),
  precise seeking (timeline and +10s), completed-playback replay, app restart/resume,
  real system Picture-in-Picture entry/exit, the media details dialog, and the
  aspect-ratio menu. Not exercised live this session (no suitable test file was
  available): a 9:16/portrait source, multi-audio-track switching, and embedded
  subtitles — see `PROJECT_MASTER.md` for the full breakdown of what was and wasn't
  exercised on-device.

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
