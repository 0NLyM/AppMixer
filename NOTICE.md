# NOTICE

NoMixer (formerly AppMixer) is a derivative work of [VolumeManager](https://github.com/yume-chan/VolumeManager)
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

## 2026-09-01 — Black/white base, red as accent only; collapsed volume popup

- Rebalanced the Nothing OS theme so red is a genuine *accent* rather than
  the dominant color: `primary` (which drives slider fills and filled
  buttons) is now white-on-black/black-on-white, and red lives only on
  `tertiary`, referenced explicitly by the few detail elements (`NothingDot`,
  the active `ToggleButton` state, and a new small red handle marker at a
  slider's fill edge in `TrackSlider`).
- Changed the volume-key popup (`Service.kt`) to open collapsed by default:
  just the media stream slider, anchored to the screen edge
  (`Gravity.CENTER_VERTICAL or Gravity.END`, like the stock Android popup)
  instead of the full per-app mixer centered on screen. A button next to it
  expands to the previous full panel (system streams + active app sliders)
  for the lifetime of that popup; it resets to collapsed the next time the
  popup is shown from scratch.

## 2026-09-01 — Corrected signing keystore

The initial RSA 4096 release keystore hit a known Android Gradle Plugin bug
(`KeytoolException: Tag number over 30 is not supported`) — AGP's signing
metadata reader can't parse certain certificate extensions newer `keytool`
builds add for large keys. Replaced it with an RSA 2048 keystore (the size
Google Play Console itself defaults to for app signing; no security
downside). No code changes were needed, only the `KEYSTORE_FILE` /
`KEYSTORE_PASSWORD` / `KEY_PASSWORD` repository secrets.

## 2026-09-02 — Customization menu, vertical slider, rotating disc

- Added `data/UiPreferences` and `UiPreferencesStore`: persisted theme mode,
  five nullable color overrides, and the collapsed popup's style, anchor,
  offsets, scale, corner radius, opacity and detail toggles. `Manager`
  tracks them independently of Shizuku and shares one state object between
  the activity and the overlay service.
- `ui/theme/Theme.kt` gained `baseColorScheme()` / `withOverrides()`, so the
  user's colors layer onto the Nothing palette with contrast-aware
  on-colors.
- New `compose/CustomizationScreen.kt` (sectioned settings menu),
  `ColorPickerDialog.kt` (HSV picker with presets and hex entry),
  `VerticalTrackSlider.kt`, `VolumeDisc.kt` (rotary volume wheel) and
  `CollapsedVolumePopup.kt` (style dispatch). All written for this fork.
- The volume-key popup now defaults to a vertical bar and can be switched
  to a horizontal bar or the rotating disc, positioned via a nine-point
  anchor plus offsets that `Service` applies to the overlay window.
- Fixed `.github/workflows/build.yml`, which used the `secrets` context in a
  step `if:` condition and therefore failed to parse.

## 2026-09-02 — Popup refinements

- Style chips wrap (`FlowRow`) instead of being squeezed, and the position
  preview is a fixed silhouette instead of an `aspectRatio` that overflowed
  inside the scrolling column.
- `VolumeDisc` became a vertical-drag control that renders as a half-moon
  on edge anchors and a full circle on centered ones, replacing the rotary
  gesture.
- The popup's two stacked backgrounds (window blur + composable fill) were
  unified into one panel, selectable as translucent or solid, with the
  configured corner radius applied to both.
- Added `compose/RingerModeButton.kt`, a ring/vibrate/silent switch shown
  alongside the collapsed popup.
- Slider corner radius flows through `LocalSliderCornerRadius` so it also
  applies to the full mixer, whose readouts now use the same monospace
  style as the collapsed popup.

## 2026-09-02 — Broadcast receiver crash fix

- Hardened upstream's `VolumeChangeObserver`, which tracked receiver
  liveness by reference count alone and threw
  `IllegalArgumentException: Receiver not registered` whenever the count
  and reality diverged.
- Added `compose/SystemBroadcastEffect.kt`, which registers system
  broadcast receivers on the application context and guards teardown, and
  moved `RingFooter` (upstream) and `RingerModeButton` onto it.

## 2026-09-02 — Collapsed popup fixes

- The horizontal style no longer reuses `StreamVolumeSlider`, so the
  show-icon / show-level toggles apply to it and the stream name is left to
  the full mixer.
- Vertical bar width and the ringer button share one base size and scale
  together.
- The half disc sits flush with the screen edge (panel padding and corner
  rounding dropped on that side) and hosts the ringer switch in its middle.
- The expand button was replaced by an inward swipe, with a hint in the
  customization screen.
- Added `FLAG_NOT_FOCUSABLE` to the overlay window so showing the popup no
  longer dismisses the on-screen keyboard.

## 2026-09-02 — Popup scaling and round disc backdrop

- `RingerModeButton` no longer builds on `IconButton`, whose own 40dp size
  and 48dp minimum touch target overrode the requested size and overlapped
  neighbouring elements below 1x scale.
- `TrackSlider` centers its content vertically, so a slider taller than its
  content (the collapsed horizontal bar) no longer pins the icon and
  readout to the top.
- `VolumeDisc` paints a round backdrop following its own radius, fading to
  transparent at the rim, and the window blur is skipped for that style
  since a blur drawable can only be a rounded rectangle. Its center piece
  is anchored dead center with the readout below it.
- Background opacity is always adjustable and reaches 0%.

## 2026-09-02 — Disc background modes

- The translucent/solid choice applies to the disc again: both modes draw
  the radial fade to full transparency at the rim and differ in how much
  shows through. The disc can't use the system blur the bars get in
  translucent mode, since the platform's background blur drawable is a
  rounded rectangle that can neither follow a circle nor dissolve.

## 2026-09-02 — Disc backdrop, opacity gating, horizontal expand gesture

- `VolumeDisc` insets the disc inside its box so the radial backdrop has a
  ring of its own to fade across. Drawn edge to edge, the backdrop sat
  entirely underneath the disc body and read as no background at all.
- The background opacity slider is hidden again for translucent bars, but
  stays available for the disc, which uses it to weight its own fade.
- The expand swipe is axis-aware: the horizontal bar opens the full mixer
  on an up or down swipe, while the vertical bar and the disc still use an
  inward one. The hint string describes both.

## 2026-09-02 — Expanded panel background, compact readout, ringer button

- The expanded mixer went fully transparent in disc style with a translucent
  background: it painted no fill, expecting the window blur that the disc
  deliberately turns off. `usesWindowBlur()` and `paintedPanelAlpha()` now
  decide that in one place for both the collapsed popup and the expanded
  mixer, so translucent falls back to a lighter tint wherever the blur isn't
  available.
- The collapsed popup shows just the current level; the maximum stays in the
  full mixer.
- `RingerModeButton` and `ToggleButton` follow the corner radius setting
  (capped at half the button, so the default still reads as a circle) and
  the slider palette: the container color when idle, the fill color when
  active, in place of the red accent.

## 2026-09-02 — Round glyph buttons, blur on the expanded mixer

- The glyph buttons' corner radius is a share of their own size
  (`LocalButtonCornerPercent`) instead of an absolute dp clipped to half the
  size they were assumed to have. `IconButton` paints over its 48dp minimum
  touch target rather than its 40dp nominal size, so the old cap left the
  ring/vibrate buttons visibly squared off at the top of the range.
- The window blur is toggled as the popup expands rather than decided once
  when the overlay attaches. The collapsed disc still goes without it -- a
  blur drawable is a rounded rectangle -- but the mixer it expands into is a
  rounded rectangle in every style, so it is frosted again instead of
  falling back to a flat tint. The composables paint their own fill only
  when the blur genuinely isn't up (a device that can't blur), which they
  now read from the view rather than inferring from the preference.

## 2026-09-02 — Motion pass

- Added `ui/theme/Motion.kt`, a shared motion vocabulary (springs, easings,
  durations) so every animated element moves with the same hand.
- Slider fills animate to a new level on all three styles, snapping to the
  finger while a drag is in progress. The fill and its content copy are
  painted in the draw phase rather than clipped by a recomposed shape, so a
  moving bar costs a redraw instead of a recomposition.
- The accent marker stretches by how far the fill still has to travel; the
  disc's dot ring lights as a head with a trailing tail.
- `RingerModeButton` reacts per mode (bell swing, vibrate buzz, silent dip),
  crossfades its icon and animates its colors; `ToggleButton` does the same
  for its colors and glyph.
- The overlay grows out of the screen edge it is anchored to and morphs
  between the compact popup and the full mixer instead of swapping.
- Material color roles crossfade through `ColorScheme.animated()`, the
  customization preview slides between anchors on an animated bias, and
  mixer list items animate as apps move between groups.

## 2026-09-02 — Popup-only colors, consistent translucency

- The five color choices apply to the volume popup only. `AppMixerTheme`
  takes an `applyColorOverrides` flag (the overlay sets it; the app doesn't)
  and a new `PopupColors` wrapper paints the customization preview in the
  popup's palette.
- The color picker gained an opacity slider; 0% disables a role, which
  `withOverrides` applies without touching the matching `on*` colors.
  Swatches sit on a chequerboard.
- Translucent always paints a scrim as well as requesting the window blur,
  which the platform grants only intermittently -- the cause of the popup
  background alternating between invisible and a flat tint. Panel colors are
  animated, so background changes crossfade.
- Replaced the expand `AnimatedContent`/`SizeTransform` with a single
  keyed appearance animation: a `WRAP_CONTENT` overlay can't animate its own
  size without the window resizing every frame and measuring to the union of
  both panels.
- `RingerModeButton` uses one theme color per mode: containers (silent),
  text-and-fills (vibrate), accent (ringing).

## 2026-09-03 — Renamed to NoMixer

- Renamed the project and app from **AppMixer** to **NoMixer** across Gradle
  config, manifest, resources and source code.
- Changed the application/package id from `com.appmixer.volume` to
  `com.nomixer.volume` (all Kotlin sources moved from `com/appmixer/volume`
  to `com/nomixer/volume`), and the baseline profile module's namespace from
  `com.appmixer.baselineprofile` to `com.nomixer.baselineprofile`.
- Log tags, the theme (`AppMixerTheme`/`AppMixerShapes` -> `NoMixerTheme`/
  `NoMixerShapes`), `Theme.AppMixer(.Popup)` styles, CI signing env vars
  (`APPMIXER_*` -> `NOMIXER_*`) and artifact filenames, and the fastlane
  listing were updated to match.
- Because the application id changed, this build does not install as an
  update over a previous AppMixer install -- it is a different app to
  Android and to Shizuku, which needs its permission granted again for the
  new package. Installing it alongside an existing AppMixer requires
  removing the old one first, since both share the same launcher icon slot
  expectations but not the same signature-package identity check some
  device vendors apply; a clean uninstall of AppMixer before installing
  NoMixer is the safest path.
- The GitHub repository itself was not renamed by this change -- that
  requires the repository owner to do it from GitHub's settings, which
  isn't reachable from here.

## 2026-09-03 — 1.0.0

- The expanded mixer's panel uses one inset on every side; the left and
  right ones used to be wider than the top and bottom.
- Blur is its own setting (`popupBlurRadius`), adjustable from the
  customization screen when the background is translucent, and separate
  from the opacity slider that belongs to the solid panel and to the disc's
  backdrop. `Service` rebuilds the blur drawable when the radius changes,
  since a radius can only be set at construction.
- The disc puts the level on its own horizontal midline with the icon or
  ringer switch above it, and pushes both towards the flat edge -- the side
  the disc's hole is centred on. How far is worked out from the popup's
  horizontal offset, so the readout stays at least a fixed margin from the
  side of the screen and recentres as the disc moves inward.
- Added `system/AudioManagerProxy`, which routes ringer mode changes through
  Shizuku the way `NotificationManagerProxy` already routes the interruption
  filter. Silent needs Do Not Disturb access, so the switch used to throw
  and fall back to ringing, leaving a three-position control that only ever
  reached two. It now reports whether the mode was actually taken and leaves
  the phone alone when it wasn't.
- Releases are no longer marked pre-release by default; the workflow takes
  it as an input.

## 2026-09-03 — 1.0.1

- The disc's icon/switch and level no longer crowd its flat edge. They used
  to be pushed in from that edge by however much the popup's own horizontal
  offset didn't already cover, which came out to zero at the 16dp default;
  the inset is now a fixed fraction of the diameter (the semicircular hole's
  visual centroid), independent of the offset.
- The ringer switch's intermittent unresponsiveness was the expand-on-swipe
  gesture living on the popup's root container, making the switch a
  descendant of that drag detector alongside the slider it sits next to. The
  gesture now attaches to just the slider (or, for the disc, the drawing
  surface) in each style, so the switch is a plain sibling outside its
  reach.
- The customization screen's preview is pinned above the scrolling settings
  list instead of scrolling with it, its phone silhouette is painted in the
  app's own theme rather than the popup's (so a disabled popup color can't
  take the "device" down with it), its mockup is built from the real
  `TrackSlider`/`VerticalTrackSlider`/`VolumeDisc` components instead of
  plain boxes, and a new button toggles it between a collapsed-popup mockup
  and a representative expanded-mixer one.
- `popupCornerRadius` no longer doubles as the slider and button radius by
  derivation. `sliderCornerRadius` and `buttonCornerRadius` are their own
  settings now, each with its own slider in the customization screen.
- Added a bar-style setting for what sits in the middle of the slider track:
  the icon or the level, one excluding the other (the disc keeps its own
  independent toggles, since it has room for both plus the ringer switch).
- The volume icon in the collapsed popup and its preview now reflects
  whether the stream is muted or routed to a connected Bluetooth output,
  instead of always showing the plain speaker glyph. The ringer switch's
  ring-mode icon changed from `NotificationsActive` to `RingVolume`, to
  match the full mixer's Ring stream icon.

## 2026-09-03 — 1.0.2

- The disc's ringer-switch/icon slot is inset using the exact local width
  of the half-moon's hole at its own height, matching the canvas's own
  radius math, rather than sharing the level's inset -- the hole narrows
  away from the midline, so the two need different insets to each land in
  the middle of the room they actually have.
- A side-anchored disc becomes a full circle once `popupOffsetX` pulls it
  far enough from the edge it hugs (a threshold proportional to its own
  diameter) instead of staying a half-moon with its flat cut floating in
  the middle of the screen. The drag gesture stays vertical either way.
- The Do Not Disturb toggle in the full mixer's Ring row shows the
  negated-DND glyph (`DoNotDisturbOff`) when DND is off, instead of a
  plain notification icon.
- `RingerModeButton` sets its local ringer mode optimistically the instant
  a tap lands, instead of re-reading `AudioManager`'s ringer mode right
  after the (possibly Shizuku-proxied) system call returns. That read can
  still reflect the old mode -- the change reaches this process a beat
  later, over the ringer-mode-changed broadcast -- which is what made the
  switch read as ignoring the tap even when the change went through a
  moment later.
- Reworked the bar styles' icon/value option a second time, on feedback
  that the previous round's single either-or choice was the wrong shape
  for the request: `popupShowValue`/`popupShowIcon` are independent
  show/hide toggles again, restoring the default side-by-side (horizontal
  bar) or stacked (vertical bar) layout when neither is centered. Two new
  toggles let either one -- never both -- be pulled to the track's dead
  center instead, each disabled while the other is centered or while its
  own item is hidden.

Further functional changes (new features, deeper customization options) will
be appended to this file as they land.
