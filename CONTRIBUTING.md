# Contributing to MediaVault

Thanks for your interest in MediaVault. This document covers how the project is
structured and how to get a change in.

## Project status

MediaVault is under active development, pre-release. Analysis, download, media
processing, local library, and playback are all implemented and wired end-to-end;
torrent/magnet support (`TorrentEngine`) is the main product goal still not
implemented — see the README's [Status](README.md#status-pre-release) and
[Project limitations](README.md#project-limitations) sections. Check
[CHANGELOG.md](CHANGELOG.md) and open issues/PRs before starting work to avoid
duplicating effort.

## Getting set up

The project assumes a working Android Studio + Android SDK installation. There are no
project-specific setup steps beyond that — open the repository root in Android Studio
and let it sync.

To build from the command line:

```
./gradlew build
```

To run unit tests:

```
./gradlew test
```

## Architecture rules (please read before submitting a PR)

MediaVault's core design principle is that the UI and app-level code depend only on the
interfaces in `core/domain`, never on a specific backend implementation:

- **Do not** call yt-dlp, FFmpeg, or a torrent library directly from `app` or from UI
  code. Add or extend an implementation behind `ExtractorEngine`, a media-processing
  abstraction, or `TorrentEngine` instead.
- **Do not** hard-code storage paths outside `Context`'s own app-private directories
  (`getExternalFilesDir()`/`getFilesDir()`/`getCacheDir()`). New downloads are
  app-private by design (see PRIVACY.md and PROJECT_MASTER.md's storage-architecture
  decision log) — reach for the Storage Access Framework only for an explicit,
  user-initiated Export/Share action, never as the default destination for a new
  download.
- Keep the application version, extraction-engine version, and media-processing version
  independently trackable — don't conflate them.
- Favor clear, testable, small modules over large ones. If you're adding a new backend
  implementation, it likely belongs in its own Gradle module.

## Commit style

Use small, descriptive commits. Prefer conventional-commit-style prefixes where they
fit naturally:

```
feat: add download queue persistence
fix: correct subtitle track language fallback
docs: document network policy decision table
chore: bump Media3 to 1.12.0
```

## Code style

- Kotlin, following standard Kotlin conventions (`kotlin.code.style=official` is set in
  `gradle.properties`).
- Compose UI should stay stateless where practical; hoist state to view models.
- Don't add a dependency injection binding, database table, or abstraction "just in
  case" — build for what the current feature actually needs.

## Legal and content policy

MediaVault is not a piracy tool and must not be developed as one. Do not submit code
that circumvents DRM, that hard-codes access to specific copyrighted content, or that
is designed primarily to enable infringing use. See [TERMS.md](TERMS.md).

## Reporting issues

Use the GitHub issue tracker. Include repro steps, expected vs. actual behavior, and
your Android version/device where relevant.
