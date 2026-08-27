# Privacy Policy

_Last updated: 2026-08-27_

MediaVault is a local-first application. This document describes, honestly and
specifically, what the app does and does not do with your data as of the current
codebase. It will be revised whenever that changes, and the revision date above will be
updated accordingly.

## What MediaVault does not do

- MediaVault does not run its own backend server and does not transmit your media,
  library metadata, or download history to any MediaVault-operated infrastructure —
  none exists.
- MediaVault does not include advertising or ad-tracking SDKs.
- MediaVault does not require an account or sign-in to use any feature.
- MediaVault does not sell or share data, because it does not collect data to sell or
  share in the first place.

## What MediaVault does do

- **Network requests to sources you provide.** When you analyze or download a URL,
  magnet link, or `.torrent` file, the app contacts the relevant third party (the
  source website, torrent peers/trackers, or GitHub for update checks) to fulfill that
  request. Those third parties see the requests you initiate, subject to their own
  privacy practices — MediaVault does not control them.
- **Local storage.** Download history, library metadata, and playback state are stored
  in a local Room database on your device. Downloaded and imported media files are
  written to the storage location you choose via the Android Storage Access Framework.
  None of this leaves your device unless you export or share it yourself.
- **GitHub update checks.** The app may query the public GitHub Releases API to tell
  you whether a newer version exists. This is a standard HTTPS request to GitHub and is
  subject to GitHub's own privacy policy.
- **Optional App Lock.** If you turn on Settings → Security → App Lock, MediaVault stores
  only a salted, hashed verifier of your PIN (never the PIN itself) in Android's
  Keystore-backed `EncryptedSharedPreferences` — not in the app's Room database and not in
  a plain preferences file. Biometric unlock (when you also enable it) is handled entirely
  by Android's own `BiometricPrompt`; MediaVault never receives or stores your fingerprint
  or face data. While App Lock is on, the app also applies `FLAG_SECURE` (blocking
  screenshots and recent-apps thumbnails of your library) and withholds the video title
  from the download-progress notification. All of this is local to your device; nothing
  related to App Lock is ever transmitted anywhere.

## Data collection and analytics

MediaVault does not currently include any analytics, crash reporting, or telemetry
SDKs. If that ever changes, it will be opt-in, disclosed here before release, and never
described as "zero data" unless that claim is technically verified against the actual
code.

## Your responsibility

MediaVault is a tool. You are responsible for ensuring you have the right to access,
download, and store any content you use it to retrieve — see [TERMS.md](TERMS.md).

## Changes to this policy

Material changes to data handling will be reflected here and noted in
[CHANGELOG.md](CHANGELOG.md).
