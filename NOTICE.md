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

## 2026-09-03 — 1.0.3

- The disc is now always drawn as a complete circle -- `VolumeDisc` no
  longer takes a half/`DiscHalf` parameter, dropping the half-moon-specific
  Canvas math and the asymmetric content insets that came with it entirely.
  A laterally-anchored popup gets its "flush with the edge" look from a
  reveal-width clip in the caller instead: at offset zero only the near
  half is visible, every dp of horizontal offset reveals more of it,
  capped once the whole circle shows. The overlay window's own horizontal
  offset is skipped for this case, since revealing the disc is the
  offset's job here, not repositioning it -- the anchor grid covers
  repositioning.
- The dot ring is now small rectangular ticks, with an adjustable corner
  radius, always fully colored rather than lighting up progressively as
  the level rises. The whole ring rotates together by up to one full turn
  across the volume range, like a knob being turned; three consecutive
  ticks are drawn larger in a stepped-down scale so the turn actually
  reads as motion.
- Redrew the launcher icon in Nothing's own idiom -- white background, a
  black three-bar volume glyph, one red accent dot -- replacing the
  previous full-color illustration.
- The volume icon animates between mute/Bluetooth/normal instead of
  snapping, with the same fade-and-scale pop the ringer switch's own icon
  uses. Customization screen sections that appear or disappear when a
  style or toggle changes now animate in and out instead of cutting.
- The full mixer's Ring row used a separate, more limited ring/vibrate-only
  toggle that could never reach silent; replaced it with the same ringer
  switch the collapsed popup uses, so both surfaces share one three-way,
  Shizuku-backed implementation. Its Do Not Disturb toggle now shows the
  negated-DND glyph when DND is off.
- The translucent panel's own painted scrim -- the fallback for whenever
  the system blur isn't granted -- now boosts its opacity when the blur
  actually didn't land, instead of always assuming it did.
- The accessibility service can be fully enabled yet do nothing if Shizuku
  is disconnected, which read as the service itself being absent. Both the
  volume-key path and the on-screen accessibility button now show a
  rate-limited toast naming the actual reason.

## 2026-09-04 — 1.0.4

- Root-caused why the ringer switch had stopped responding entirely: the
  disc's reveal-width clip was clipping the *whole* component, including
  `centerContent`, which sits at the disc's true, unmoving center -- at low
  offsets (including the 16dp stock default) that center fell right at or
  past the clip boundary, making the switch unreachable. The clip now
  applies only to the ring/canvas's drawn content, never to the component
  itself, so the center is always fully visible and clickable regardless
  of how much of the ring is currently revealed.
- The reveal ramp is steeper: it used to take about half the disc's own
  diameter of offset to go from half-revealed to complete, which is why it
  still looked cut at the default. A fixed 24dp span now covers the whole
  transition, and stops responding to further offset past that -- reaching
  further in, or centering the disc outright, is what the anchor grid is
  for.
- The disc's own backdrop is decoupled from the panel's Translucent/Solid
  selector -- which is about whether a *rectangular* panel gets the system
  blur, something the disc never asks for even expanded -- and gets its
  own on/off toggle plus the shared opacity slider directly, with no
  Translucent-mode reduction.
- Disc ticks are bigger, so the corner-radius setting has enough area to
  read clearly; the landmark cluster's sizes are more pronounced (2x
  center, 1.5x adjacent -- exactly between the landmark and a normal
  tick); and each tick's corner radius is now worked out from its own
  scaled size instead of one shared value, so the pill look is consistent
  across every tick size.
- Redrew the launcher's three volume bars as full capsules (corner radius
  equal to half each bar's own width) instead of plain rectangles, to
  match the fully rounded track look of the app's own sliders.

## 2026-09-04 — 1.0.5

- Removed the disc's reveal-width clip entirely: the disc is now always a
  complete circle, positioned by the popup window exactly like the bar
  styles rather than being drawn as a partial shape near a screen edge.
  The window itself now clamps its own offset, once laid out, so it never
  gets cut off the display edge whatever style it holds -- the same fix
  covers every style, not just the disc.
- Root-caused the ringer switch's continued unresponsiveness: `setRingerMode`
  is a blocking Shizuku binder call that used to run inline in the click
  handler, so a fast, silent refusal reverted the optimistic UI update
  before Compose ever got a frame to draw it -- both writes landed in the
  same recomposition, and the switch looked like it had ignored the tap
  outright rather than having flipped and bounced back. It now runs in its
  own coroutine, off the click handler, so a tap always visibly registers
  first.
- The disc's ringer switch and its value label no longer share the icon's
  up-shifted slot, where they could overlap: the switch now sits dead
  center in the disc's hollow middle, with the label moved below it.
- The 3 corner-radius sliders (panel, slider track, button) are hidden
  while the disc style is active, since none of them apply to it.
- Disc ticks are shorter overall, and the 3 landmark ticks now grow only
  in length, not thickness, so they read as longer pills rather than
  fatter marks.

## 2026-09-04 — 1.0.6

- A laterally-anchored disc pokes off the physical screen again, the way
  the original stock-style edge control did -- but now by moving the
  overlay *window* partly off the display (`FLAG_LAYOUT_NO_LIMITS`) rather
  than by clipping anything drawn inside it. The disc itself is still
  always a complete circle; whatever part of the window ends up beyond the
  display edge simply isn't rendered by the compositor, the same as any
  other off-screen window content.
- Horizontal offset now maps that window position between two ends
  instead of moving it freely: at zero the window sits half off-screen,
  and by the top of the offset range it's fully back on screen with a
  small (8dp) gap left to the edge, computed from the window's own real
  measured size rather than the raw dp offset.
- Bar-style popups (and a disc anchored to the middle column) are
  unaffected: they keep the fully-on-screen clamp from 1.0.5.
- The customization screen's live preview mirrors the same behavior,
  using its phone silhouette's own clip to stand in for the real display
  edge.

## 2026-09-04 — 1.0.7

- The disc's ringer switch and value label now pull back from the disc's
  true center whenever a laterally-anchored disc's window is mostly
  off-screen, staying a minimum distance clear of the physical cut edge
  and converging back to dead center as the offset reveals more of the
  disc, instead of riding the fixed center straight past the edge.
- Disc tick ring: every tick's outer end now sits on the same shared
  boundary; a landmark tick's extra length grows inward instead of also
  pushing its outer edge further out, so the whole ring reads as flush
  at the rim.
- The disc has a real background panel again: a circle hugging the disc
  with an adjustable margin (new "Distance from disc" setting) instead
  of a fixed corner radius, so it always reads as proportional to the
  disc regardless of scale.
- The Translucent/Solid background and Blur/Opacity controls now apply
  uniformly to every style's panel, disc included, and are always
  visible -- previously the whole block vanished whenever the disc's own
  backdrop toggle was off. None of this touches the disc's own ring and
  track colors, which stay whatever the palette says.
- The old disc-only "Show backdrop" toggle is now a general "Show glow"
  toggle: a soft painted aura behind the panel, independent of the
  Translucent/Solid fill, available for every style now rather than just
  the disc, at its own fixed intensity instead of sharing the panel's
  opacity slider.
- The window-level system blur now reaches the disc too: it requests the
  disc's own derived corner radius while collapsed (matching its new
  panel) and the shared panel corner radius once expanded into the
  full mixer, rather than skipping the disc's blur outright.

## 2026-09-04 — 1.0.8

- Reverted the ringer switch back to its synchronous `setRingerMode` call:
  the 1.0.7 coroutine wrapper, based on a plausible but wrong theory about
  same-frame writes, made the switch animate on tap without ever actually
  changing the mode.
- 1.0.7's "Show glow" is scoped back to the disc's own knob (the manopola)
  only, renamed to "Show shadow", and lowered from a 70% panel-wide effect
  to a light 35% one, closer to what it looked like before that round.
- Removed the disc's "Distance from disc" margin slider: it only ever
  shifted the whole knob vertically as a side effect, never the button
  offset it was meant to control, which the button now handles on its
  own. The margin is a fixed internal constant again, shared between the
  overlay window's blur shape and the panel's own drawn shape -- the two
  had been read from separate places, which is the actual bug behind the
  disc panel sometimes staying opaque instead of blurring in Translucent
  mode.
- Added a "Value beside switch" toggle for the disc style, placing the
  volume level next to the ringer switch instead of below it.
- Fixed the expanded mixer rendering off-screen when opened from a
  laterally-anchored disc popup: the one-shot listener that clamps the
  window on-screen only ran once, at the collapsed popup's initial
  layout, and never accounted for how much wider the window gets once
  expanded into the full mixer. It now re-arms on the collapsed-to-
  expanded transition too.
- The collapsed active-players list could appear to silently reorder
  itself (rows flickering into a new order, occasionally duplicated
  mid-animation) whenever the volume changed. Root cause: it read
  directly off a `mutableStateMapOf`, whose iteration order isn't
  insertion-ordered and isn't guaranteed to hold steady across unrelated
  structural changes elsewhere in the map, feeding that straight into a
  list that animates every reorder it sees. Sorted it the same way the
  grouped/full view already was, and hardened both name comparators with
  a `packageName` tie-break so two apps sharing a display name can't
  still flip order between recompositions. Diagnosed from the code, not
  reproduced live on a device -- flag it if the flicker is still there.
- The customization screen's settings are now grouped into titled
  sections with a one-line description of what each section's toggles
  actually do, instead of one long undivided list.

## 2026-09-05 — 1.0.9

- "Show shadow" is no longer disc-only: the vertical and horizontal bar
  styles now get the same light shadow, drawn as a soft halo behind their
  own slider track (shape-matched to its corner radius) instead of the
  disc's radial gradient. One toggle, moved into the general section,
  covers all three styles.
- The disc's Translucent blur was showing through its outer margin and the
  decorative shadow-fade sliver around the ring, not just the ring's own
  track -- the actual cause of it reading as "sticking out" and sitting
  underneath the shadow. The disc's panel is now always fully opaque in
  Translucent mode, and the ring cuts a hole shaped exactly like its own
  track -- never the sliver past it -- only when the panel is genuinely
  translucent and the system actually granted the blur. The filled
  (white) portion of the ring still draws opaquely on top, so a full
  volume level reads as fully covered; the unfilled portion tints
  whatever's behind it, real blur or a plain backing, with the same light
  gray wash either way.
- Translucent and Solid shared one opacity value, so adjusting one bled
  into the other: Solid at 100% left a 40%-opaque veil sitting over the
  blur after switching to Translucent, and Solid at 0% could still show
  blur that had nothing to do with the Solid mode it was supposedly in.
  The two are fully separate now -- Solid only ever reads its own
  opacity, Translucent paints nothing of its own once the blur lands and
  a fixed fallback otherwise, with no crossover between the two.
- Root-caused the app volume list still flickering after 1.0.8's sort
  fix: the code that reacts to playback changes cleared every app's
  "is playing" state and then re-added the still-active ones as two
  separate steps, so a screen redraw landing in between them could catch
  the whole active list looking momentarily empty -- and that reaction
  fires on far more than an app starting or stopping, including plain
  volume changes. The two steps are now one atomic update, so a redraw
  can only ever see the state before or the state after, never a state
  in between. Diagnosed from the code, not reproduced live on a device --
  flag it if the flicker is still there.
- The ringer switch not responding is a known, separate issue, deferred
  to a follow-up round at the reporter's own request.

## 2026-09-05 — 1.0.10

- The bar styles' "Show shadow" (added in 1.0.9) only ever drew behind
  the slider track, never the ringer button, as a flat inflated rounded
  rect with no soft falloff -- reading as a stray smear pasted next to
  the bar rather than a shadow. It now wraps the whole panel, button
  included, with a real Gaussian blur behind it instead, matching the
  panel's own corner radius, and stays independent of whether the
  panel's own Translucent/Solid fill is visible at all -- so both
  pieces keep a consistent shadow even when that fill is fully
  invisible.
- The app volume list flicker and the disc's Translucent blur are still
  open; both are being worked separately, one at a time.

## 2026-09-05 — 1.0.11

- 1.0.10's bar-style shadow read as a boxy smear rather than a shadow,
  and could sit a pixel off the panel's own edge -- both traceable to
  hand-drawing a flat rounded rect and Gaussian-blurring it as a
  separate layer trying to line up with the panel. Replaced it with
  the platform's own elevation shadow (proper ambient falloff), tinted
  with the same theme color instead of the default black, applied
  directly to the panel itself rather than a separate layer -- so it's
  always exactly aligned to whatever it's shadowing.
- Added a "Show background" switch, next to Translucent/Solid. Off
  removes the panel entirely instead of that same result being
  reachable by dragging the opacity or blur slider to a corner, and
  moves the shadow off the (now absent) panel onto the ringer button
  and slider individually, each shaped to its own outline.
- With "Show background" covering "no background", the Solid opacity
  and Translucent blur-radius sliders no longer go down to 0 -- their
  floor is 1 now, closing off the same state being reachable two
  different ways.
- The launcher icon is about 10% smaller, with a bit more white margin
  around it.
- The app volume list flicker and the disc's Translucent blur are
  still open; both are being worked separately, one at a time.

## 2026-09-05 — 1.0.12

- The disc's Solid/Translucent backing used to fill the whole disc
  bounding circle -- margin and shadow-fade sliver included, not just
  the ring's own track -- so it read as a solid disc that swamped the
  separate "Show shadow" glow and clearly overshot the white volume
  ring. It's now painted as a plain stroke confined to the ring's own
  annulus, never wider than the track itself; the disc's surrounding
  panel is always fully transparent, exactly as it already looked with
  the background switch off. The light gray tint on the ring's unfilled
  portion is unaffected and always stays.
- "Show background" (1.0.11) only ever applied to the collapsed popup.
  The expanded, full-app mixer now respects it too: with the switch
  off, its panel goes fully transparent and each individual slider
  (both app volume rows and the system stream sliders) picks up its
  own soft shadow instead, the same way the collapsed popup's ringer
  button and slider already do.
- The app volume list flicker is still open, being worked separately.

## 2026-09-05 — 1.0.13

- 1.0.12's disc confinement fix only changed what Compose paints, not
  the real background-blur drawable already sitting behind it -- a
  single rounded rect sized to the *whole* overlay view (there's no way
  to shape it as a ring), so Translucent still visibly blurred the
  entire panel, margin and shadow-fade sliver included, not just the
  ring's own track. The collapsed disc now never requests that real
  drawable at all: Translucent falls back to the same fixed,
  ring-confined dim scrim Solid already uses, so both now sit in
  exactly the same place. The expanded mixer is unaffected (it's always
  a plain rounded rectangle regardless of the collapsed style) and
  keeps the real blur.
- Reserved a margin around the whole popup window for the real
  elevation shadow (bars, expanded mixer sliders) to render into: the
  window is sized exactly to its content with nothing spare, and a
  shadow drawn outside those bounds had nowhere left to go and was
  being clipped off at the window's own edge. This one is a best-effort
  diagnosis, not confirmed live -- flag it if the shadow is still
  missing, and also check that the popup still hugs the screen edge the
  way it used to, since the window being a little bigger now could
  nudge it slightly further in than before.

## 2026-09-05 — 1.0.14

- Found the actual reason Translucent didn't show on the disc: the
  "Show shadow" backdrop gradient was painted fully opaque from the
  disc's own center out to its edge (only fading out in the sliver
  beyond that), sitting directly on top of the ring's own track
  backing and diluting it before the gray tint and fill arcs were even
  drawn. Solid's usually-high opacity survived that stack well enough
  to still read through; Translucent's fixed, lower fallback alpha
  barely did, reading as if nothing were there at all. The shadow now
  stays fully transparent through the disc's own body and ring, only
  appearing right at the ring's outer edge and fading out across the
  sliver beyond it -- never overlapping the track itself.

## 2026-09-06 — 1.0.15

- Reverted 1.0.13's popup-shadow outer margin: it was Compose-side
  padding around the whole popup, but the real system background-blur
  drawable is set as the overlay *view's* own background, which always
  covers the view's full bounds with no way to confine it smaller --
  so in Translucent mode the real blur grew right along with that
  margin into a visibly oversized, blurred halo well past the actual
  panel, and the bigger view also pulled the whole popup further in
  from the screen edge than configured, even at an offset of 0. A real
  elevation shadow needing external room to bleed into and a real
  system blur needing the window to stay exactly panel-sized can't
  both be satisfied on this overlay's single WRAP_CONTENT view --
  giving the shadow room properly needs one drawn inside the panel's
  own bounds instead, the way the disc already does it, not a padding
  patch on top of this. Reverted outright rather than patched further;
  the oversized halo and the edge gap should both be gone now, back to
  how bars looked before that change.
- Fixed a separate, real bug found in the process: switching between
  collapsed styles (vertical bar to horizontal bar, say) resizes the
  overlay view without necessarily changing the blur radius or corner
  radius, so the cached "already blurred, nothing to do" check never
  caught it and kept the stale drawable sized for the previous style.
  It now also rebuilds whenever the collapsed style or the
  expanded/collapsed state changes.
- The shadow-visibility-on-bars problem itself is still open -- this
  release only undoes the regression it caused, not the original ask.

## 2026-09-06 — 1.0.16

- Gave the collapsed bar panel and the expanded mixer's panel real room
  for their elevation shadow to render into, without letting the real
  system background-blur drawable leak into that margin the way it did
  when this was tried in 1.0.13. When a real blur drawable is actually
  live behind everything (Translucent, background on, blur landed),
  the reserved margin is painted opaque first, with a hole cut exactly
  where the panel itself sits -- confining the drawable back to the
  panel's own shape -- and the covering's own outer edge is softened
  with a light blur so it fades into the wallpaper instead of sitting
  on it as a hard rectangle. The panel's own shadow, a real, crisp
  elevation shadow, draws on top of it. The disc (which draws its own
  shadow internally, no margin needed) and the per-element case
  (background off, no real blur to leak there) are unaffected.
- Taught the overlay window's own edge-hugging position clamp about
  this margin, so only the margin -- never the panel itself -- can hang
  off the physical screen edge at an offset of 0, the same as it looked
  before this margin ever existed.

## 2026-09-06 — 1.0.17

- 1.0.16's shadow-room technique (reserved margin plus an anti-leak
  covering) didn't work on the actual device and made things worse:
  real Translucent blur stopped showing at all, not just staying
  unconfined, and none of the three things it set out to fix actually
  worked. Reverted outright back to 1.0.15's behavior rather than
  layering another unverified attempt on top of it. Panel-level shadow
  visibility on bars and the expanded mixer remains an open, unsolved
  problem; real background blur on those panels is back to working
  (unconfined to the panel, as it's been since it was reintroduced)
  rather than not working at all.

## 2026-09-06 — 1.0.18

- Bar styles (Vertical Bar, Horizontal Bar) and the Disc now keep fully
  independent copies of the ten appearance settings that each style can
  meaningfully differ on: scale, button corner radius, background mode
  (Translucent/Solid), background on/off switch, background opacity, blur
  radius, icon visible, value visible, ringer-button visible, and shadow.
  Changing any of these under one style no longer touches the other.
  The customization screen and the popup look exactly as before; the
  change is purely in how the settings are stored and read internally.
  Users who had already customised any of those ten settings while in Disc
  mode will find the disc-specific copy reset to defaults and will need to
  set it once more; the bar-style copy is unchanged.

## 2026-09-06 — 1.0.19

- Disc: turning its Background switch off now leaves it fully
  transparent. The backdrop/shadow halo painted behind the ring used to
  stay visible at its own fixed alpha regardless of that switch, which
  read as the background never actually turning off; the customization
  screen's live preview got the same fix, and now also previews the
  ring's own backing tint (Solid/Translucent) instead of always showing
  nothing there.
- Renamed the shared "Background" section/toggle to "Bar background" /
  "Show bar background" while a bar style (Vertical Bar, Horizontal Bar)
  is selected, since 1.0.18 made the setting bar-only data; unchanged
  for the Disc.
- The volume bar sliders' fill now ends in a rounded cap on the edge
  that actually moves as the level changes, instead of the same small
  corner radius as the anchored edge -- reads as a rounded bar growing
  rather than a rectangle with a squared-off front.
- Disc Translucent mode gets a real, ring-confined system blur again,
  after 1.0.9-1.0.17's several attempts at this were all abandoned: the
  platform's own cross-window blur drawable can only ever be a rounded
  rectangle, never a ring, so a *separate* native view now carries it,
  clipped by its own annulus-shaped outline to exactly the ring track's
  geometry and sitting behind the disc's own Compose content -- confined
  to the same footprint Solid mode already paints its tint in, rather
  than leaking across the whole panel. Bar styles and the expanded mixer
  are unaffected. This is a new technique, untested on a real device;
  if the blur doesn't land exactly on the ring, it needs a follow-up
  fix or another revert.

## 2026-09-06 — 1.0.20 (critical hotfix for 1.0.19)

- 1.0.19 crashed on launch for every user: wrapping the popup's
  ComposeView in a FrameLayout (to host the new ring-shaped disc blur
  view alongside it) made that FrameLayout the actual window root, but
  the lifecycle/saved-state owner was still tagged only on the
  ComposeView child. Compose's own recomposer setup looks that owner
  up starting from the window root and never climbs back down into a
  child, so it found nothing and crashed immediately, falling back to
  the stock Android volume slider. Fixed by building the owner once
  and tagging both the FrameLayout and the ComposeView with it.
- The rounded slider fill-edge introduced in 1.0.19 is now scoped to
  where it was actually meant to be: reverted entirely on the
  collapsed Vertical Bar popup, and off by default on the collapsed
  Horizontal Bar popup too -- both keep their original squared-off
  fill. It stays on only for the expanded mixer's own sliders (per-app
  and system volume).

## 2026-09-07 — 1.0.21

- Reverted the slider fill's rounded leading edge entirely -- 1.0.20
  had already scoped it back to just the expanded mixer's own sliders
  (per-app and system volume), but it's now gone from there too, back
  to the original squared-off fill everywhere.
- Fixed tapping outside the popup (collapsed or expanded) no longer
  dismissing it: 1.0.19 wrapped the popup's ComposeView in a
  FrameLayout for the disc ring-blur view, which made that FrameLayout
  the actual window root, but the handler for an outside tap
  (FLAG_WATCH_OUTSIDE_TOUCH's ACTION_OUTSIDE) was still only on the
  ComposeView -- a child, which that event is never delivered to.
  Moved the handler to the FrameLayout, restoring the original
  tap-outside-to-dismiss behavior.

## 2026-09-07 — 1.0.22

- Closed a hairline seam of unblurred wallpaper visible right at the
  disc ring track's own edge in Translucent mode: Compose measures the
  disc's box to a whole pixel before ever drawing the ring into it, but
  the ring-blur view's own geometry was computed as raw, unrounded
  floats -- occasionally a fraction of a pixel short of where the
  ring's paint actually lands. The blur view's inner and outer radius
  are now widened by 1.5dp past the ring's true edges, so it fully
  backs the ring with a bit of margin to spare; the extra sliver is
  either hidden under the ring's own opaque stroke or bleeds into the
  much wider transparent backdrop-fade margin around it, invisible
  either way.

## 2026-09-07 — 1.0.23

- 1.0.22's 1.5dp padding on the disc ring-blur view, meant to close a
  hairline seam, showed as its own worse artifact instead: a visible
  gray ring, since nothing in the disc's own drawing paints that far
  out over it. Reverted to matching the ring's true edges exactly.
- The disc ring's own outline now draws after the level fill instead
  of before, so it stays visible as a border over the filled arc too,
  rather than being erased by the fill wherever the level already
  reaches.

## 2026-09-07 — 1.0.24

- The disc ring's outline was still a wash across the ring's whole
  width even after 1.0.23's reorder, so with a custom outline color
  set it visibly tinted the translucent blur revealed in between --
  reported as "the volume bar takes on the outline's color" too, since
  both previews (the large one and the device mockup) share the same
  disc. Replaced with two thin (1.5dp) border strokes, one at the
  ring's outer edge and one at its inner edge; the blur (or Solid's
  tint) now shows clean in between, with the outline reading as an
  actual border rather than a flat tint over the whole track.
- The color picker dialog's Reset/Cancel/Apply buttons now sit
  explicitly on one row (Reset and Cancel grouped on the left, Apply
  on the right) instead of wrapping onto two lines once a longer hex
  value pushed them past the dialog's default button row width.

## 2026-09-07 — 1.0.25

- Setting the disc ring's outline color to 0% opacity -- the
  documented way to switch a color off -- didn't actually hide it:
  both of 1.0.24's edge border strokes forced their own fixed alpha
  instead of reading the color's own. Now respects whatever opacity is
  actually picked, transparent included.
- Dropped the ring's inner-edge border: the disc face already has its
  own outline stroke right around the switch/knob, and the ring's
  inner border sat a separate 1dp further in, so the two never quite
  lined up -- showing as a stray, slightly offset second line rather
  than a clean frame. The ring keeps only its outer-edge border now;
  the face's own outline still frames the inner edge as it always has.

## 2026-09-07 — 1.0.26

- Fixed Translucent blur silently not landing on the Vertical Bar and
  Horizontal Bar popup styles (and the expanded mixer): 1.0.19/1.0.20
  wrapped the popup's own ComposeView in a FrameLayout to add the
  disc's ring-blur view alongside it, which made that FrameLayout the
  actual window root -- but the bar/mixer panel blur kept setting its
  system blur drawable on the ComposeView itself, a plain child from
  that point on. The drawable still existed and rendered, just with
  nothing behind it actually blurred, since the real cross-window
  blur-behind effect only lands when set on the window's true root.
  Moved it there. The disc's own ring blur, already on its own
  dedicated view, was never affected by this.

## 2026-09-07 — 1.0.27

- Fixed the ringer switch (Ring/Vibrate/Silent) not responding to taps
  on some devices even though it correctly tracked the phone's ringer
  state when changed elsewhere: the audio service's binder was only
  ever wrapped once, at app start, to route silent-mode changes
  through Shizuku's elevated access; if that one attempt failed for
  any reason, every later tap silently fell back to the same
  unprivileged call that had just failed, forever, with no retry and
  no visible error. The wrap is now retried right before each
  elevated ringer change instead of only once at startup, and a
  failed change now logs Shizuku's live connection state so a repeat
  is diagnosable. The equivalent binder-wrap calls in `Manager` were
  also given the same failure handling they were missing (a reflection
  failure there previously had no fallback at all).
- Added a small Shizuku status icon to the main screen's top bar: a
  checkmark when connected, a warning otherwise. Tapping it while
  something's wrong re-does whatever step is actually blocking the
  connection (requesting permission, opening Shizuku, or opening its
  Play Store listing if it isn't installed), so a lapsed pairing is
  visible and fixable from the app itself instead of only showing up
  as controls quietly not working.

## 2026-09-08 — 1.0.28

- Fixed the popup's panel going fully invisible (no blur, no fallback
  color either) when battery saver was turned on: Android disables
  real cross-window blur system-wide while battery saver is active --
  expected, universal OS behavior no app can override -- but this app
  kept believing a blur that had already landed was still working
  even after the platform silently revoked it, since the check that
  decides "was blur actually granted" was only re-evaluated when the
  panel's own radius/style/scale changed, never on its own. With that
  stuck at "landed", the app's own translucent fallback color (which
  is meant to stand in exactly when real blur isn't granted) never
  kicked in either, so the panel had nothing painting it at all. Now
  re-checked on every apply, and a live
  `WindowManager.addCrossWindowBlurEnabledListener` reacts immediately
  if battery saver is toggled while the popup or expanded mixer is
  already on screen, instead of only noticing on some unrelated later
  change.

Further functional changes (new features, deeper customization options) will
be appended to this file as they land.
