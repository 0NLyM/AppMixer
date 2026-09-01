# NOTICE

AppMixer is a derivative work of [VolumeManager](https://github.com/yume-chan/VolumeManager)
by yume-chan, licensed under the GNU General Public License v2.0 (see [`LICENSE`](LICENSE)).
As required by GPLv2 §2(a), this file summarizes the changes made to the original
source since the fork was created.

## 2026-09-01 — Initial fork (AppMixer)

- Renamed the project and app from "Volume Manager" / "AppVolMgr" to **AppMixer**
  across Gradle config, manifest, resources and source code.
- Changed the application/package id from `moe.chensi.volume` to `com.appmixer.volume`
  (all Kotlin sources moved from `moe/chensi/volume` to `com/appmixer/volume`).
- Reset `versionCode`/`versionName` for the new project (fork starts its own
  version history).
- Replaced the Material default purple/teal color scheme with a **Nothing OS
  inspired** black / off-white / red theme (`ui/theme/Color.kt`, `Theme.kt`),
  disabled Material You dynamic color by default in favor of the custom theme,
  and switched headline/title typography to a wide-tracked monospace fallback
  to approximate a dot-matrix look (`ui/theme/Type.kt`).
- Recolored the adaptive launcher icon (black background, red fader tracks)
  to match the new theme (`res/drawable/ic_launcher_foreground.xml`,
  `res/values/ic_launcher_background.xml`).
- Updated the About dialog, README, fastlane metadata and CI artifact naming
  to reflect the new name, while keeping a visible credit and link back to
  the original VolumeManager project and its author.

## 2026-09-01 — Nothing OS restyle and release signing

- Filled in the full Material3 color role set (containers, surfaces,
  outlines) for the Nothing OS theme, added a shared pill-shaped `Shapes`
  set, and restyled `TrackSlider`, `ToggleButton` and the app list/top bar
  with the black/white/red look (dot accents, uppercase dot-matrix
  headers).
- Added `.github/workflows/release.yml` to publish downloadable builds as
  GitHub Releases.
- Generated AppMixer's release signing keystore (RSA 4096, 30-year
  validity) and wired `app/build.gradle.kts` plus both CI workflows to sign
  release builds from it via `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/
  `KEY_ALIAS`/`KEY_PASSWORD` repository secrets, so every future release
  can be installed as an update over the previous one. The keystore itself
  is not committed to the repository.

Further functional changes (new features, deeper customization options) will
be appended to this file as they land.
