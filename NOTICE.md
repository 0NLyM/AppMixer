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

## 2026-09-08 — 1.0.29

- The translucent panel's fallback tint -- painted whenever the
  platform won't grant real system blur, battery saver being the
  common case -- was a flat, evenly-colored fill, nothing like the
  frosted-glass look it's standing in for. A real per-pixel blur of
  whatever's behind the window isn't something an app can fake cheaply
  (that's exactly the capability battery saver revokes, and faking it
  via screen capture would cost more than the real thing, defeating
  the point), but the fallback tint itself can still look less flat.
  It's now painted as a soft diagonal gradient sheen (a new
  `frostedGlassBrush()` helper) instead of a solid color, across the
  disc's ring, the bar styles' panel, and the expanded mixer. Solid
  mode's own flat opacity is untouched, since that's a setting the
  user is directly controlling, not a fallback.

## 2026-09-09 — 1.0.30

- When the disc style is anchored to a side (Start/End) with a low
  horizontal offset, part of it deliberately sits off the physical
  screen -- but the outer volume ring (the 0-16 scale) kept mapping
  its whole range onto a full 360° circle regardless, so only
  whatever arbitrary slice of that circle happened to still be on
  screen was actually readable. The ring now works out exactly where
  the screen's own edge crosses it and redistributes the full 0-16
  scale across only that visible arc -- 0 anchored to the lower cut
  point, 16 to the upper one -- so the whole scale stays readable no
  matter how much of the disc is hanging off screen. A disc that's
  fully on screen is unaffected.

## 2026-09-12 — 1.0.31

- 1.0.30's disc ring fix went too far and froze the tick ring's own spin
  animation whenever the disc was laterally cut -- reverted: the ticks
  keep spinning exactly as before, evenly around the full circle; only
  the fill arc and its outline remap onto the arc still visible past
  the cut.
- Expanding the popup into the full mixer used to visibly jump: for a
  frame or two it showed the mixer's content sitting at the collapsed
  disc's own (narrower, laterally-cut) position, cut in half by the
  screen edge, before snapping to where it actually belongs. The
  window now stays hidden until its corrected position has actually
  landed, so the mixer only ever appears already in the right spot.
- Added a "Center expanded mixer" switch (Position section): with it
  on, expanding any collapsed style opens the mixer centered on
  screen instead of anchored wherever the collapsed popup was.

## 2026-09-12 — 1.0.32

- The volume-key path made a Shizuku-proxied binder call
  (`activityTaskManager.getForegroundTask()`) on every single volume
  key press, before the popup could even start appearing -- needed
  only to check whether the current foreground app is on the
  "disable volume buttons" list, a rare case, but paid on every
  press regardless. Replaced with a plain `rootInActiveWindow` lookup
  already available to this app's own accessibility service
  connection, cutting a full out-of-process IPC hop from the app's
  single most frequent interaction. Same ignore-list behavior, just
  without the extra round trip.

## 2026-09-12 — 1.0.33

- Fixed the disc's outer ring (fill and outline) spinning in the
  opposite direction from the tick knob inside it whenever the disc
  is laterally clipped -- one of the two clip sides had the sweep
  direction backwards. Both still anchor exactly to the two
  screen-edge cut points; only the rotational sense was corrected to
  match the ticks.
- Two more fixes for the expanded mixer still looking briefly cut
  during its own appear animation: the disc's own ring-shaped blur
  view was leaving its old size behind after switching styles or
  expanding, which could distort the shared window's measurement --
  now reset on every teardown path; and the window's reveal after
  repositioning now waits one extra frame in case the app list needs
  a second layout pass to fully settle.
- `release.yml` now takes the actual changelog as a `notes` input and
  publishes it as the GitHub release body, instead of the same fixed
  install blurb on every release.

## 2026-09-13 — 1.0.35

- Fixed the volume disc rotating the wrong way: it now always turns
  counter-clockwise to raise the volume when anchored to the left
  edge or centered, and only reverses to clockwise when clipped
  against the right edge. Also fixed the longest tick mark no longer
  tracking the actual volume level -- it now recomputes its position
  from the live fill edge every frame instead of from a rotation
  value that only coincidentally matched it at 0%/100%.
- Replaced the app's dependence on the system's real cross-window
  blur (`WindowManager.isCrossWindowBlurEnabled`) with a single,
  always-on "glassmorphism" scrim (`GlassScrim.kt`) for the
  Translucent background mode. Real blur is unconditionally disabled
  by Android under battery saver with no public or Shizuku-accessible
  override (confirmed against AOSP's `BlurController` source), which
  made the old code depend on a system capability that silently
  stopped working exactly when the user had just asked the OS to
  save power -- and was the root cause of a long history of blur
  regressions across many earlier releases. The new scrim is a
  diagonal gradient tint plus a light AGSL noise/grain texture and a
  thin border, rendered the same way on every device and every power
  state, with no branch for "blur available" vs "blur unavailable."
- Added an optional adaptive tint for the glass scrim (off by
  default): when enabled, it periodically samples the real screen
  behind the popup via the accessibility service's own screenshot
  capability (`android:canTakeScreenshot`, no `MediaProjection`, no
  extra consent dialog), reduces it immediately to a single average
  color, and blends that into the scrim's tint so it leans toward the
  color of whatever is on screen. This has a real, if small, periodic
  cost, so it ships as an explicit switch with its own sampling-
  interval slider rather than being always on.

## 2026-09-14 — 1.0.36

- Fixed the disc anchored to the right edge, at a zero horizontal offset,
  drawing its whole value arc on the half that isn't on screen. The cut's
  distance from the disc's center is signed by which side it falls on, and
  at offset zero the cut runs exactly through the center -- a zero with no
  sign left to read, which the previous side-from-sign inference read as
  "cut on the left" regardless of the actual anchor. VolumeDisc is now told
  which side is hidden instead of inferring it, so this boundary case can't
  land on the wrong half.
- The tick ring is turning again. 1.0.35 had pinned every tick to a fixed
  slot and lengthened whichever three sat nearest the level, which stopped
  reading as a knob. Both the ring's spacing and how far it turns now come
  off the visible arc rather than the full circle, so a laterally-cut disc
  keeps its ticks spread across the part still on screen and coordinated
  with the fill and the knob at every level; on an uncut disc this is
  identical to the previous full-circle rotation.
- The glass background now actually refracts the screen behind it, instead
  of a fixed gradient tint that looked the same in every situation. A still
  of the screen is captured (through the accessibility service's own
  screenshot capability) the instant before the popup appears -- so it
  never captures itself -- and scaled down, which is the blur itself, then
  drawn back up behind each glass panel lined up with the exact part of the
  screen it's covering. One capture per appearance rather than periodic
  polling, so it costs far less than the sampling toggle it replaces --
  it's on by default now, with a blur-amount slider in place of the old
  sample-interval one.

## 2026-09-14 — 1.0.37

- The tick ring is no longer the thing getting cut off. Turning the ring
  (restored in 1.0.36) carried its marks off the visible arc and out of
  sight, so a laterally-cut disc showed only part of its ring at most
  levels. The ticks stay put now, spread across the visible arc so the
  whole ring is always on screen, and the landmark -- the long tick with
  its two shorter neighbours -- is what travels, riding the fill's own
  leading edge so the level still reads off the ring at a glance. A closed
  circle spaces them 24 to the turn (its last tick one step short of the
  start, since step 24 is step 0); a cut arc has two real ends and gets a
  tick on each.
- Added a switch to round off the ends of the disc's value arc and its
  outline, so a part-filled ring finishes in a capped tip instead of a
  squared-off cut. Separate from the existing tick corner radius, which
  rounds the marks rather than the ring they sit on.
- The glass capture now says why it has no backdrop instead of failing
  silently. It was asked for before the overlay window goes up and quietly
  dropped on any failure, so a capability the system hadn't granted, a
  refused request and an unreadable buffer all looked identical to the
  option simply doing nothing. It now checks the service really holds
  `CAPABILITY_CAN_TAKE_SCREENSHOT` -- adding `canTakeScreenshot` to
  accessibility_service_config.xml doesn't reach a service the system is
  already running, so an app update alone can leave it off until the
  accessibility service is toggled off and back on -- and reports the
  actual reason in every failure path, rate-limited. The full-size readback
  also now happens before the hardware buffer is released rather than
  after.

## 2026-09-14 — 1.0.38

- Device testing isolated the glass panel bug precisely: Solid mode
  rendered correctly, but Translucent mode was rendering fully
  transparent -- not just unblurred, but completely invisible, with
  underlying content perfectly readable straight through it. That meant one
  draw call inside the glass panel (either the backdrop's screen-position
  lookup via `View.getLocationOnScreen`, or the `RuntimeShader`/AGSL noise
  shader compiling on that particular device's GPU driver) was throwing and
  aborting the draw before the base tint -- a plain Compose gradient
  `Brush`, unrelated to either of those and normally guaranteed to work --
  ever got painted. Each layer (backdrop, tint, noise) is now wrapped and
  drawn independently, so a failure in one can no longer blank the others:
  the base tint should now always render once Translucent mode is on, with
  the backdrop and grain each best-effort on top of it.

## 2026-09-14 — 1.0.39

- The previous release's capability pre-check assumed that turning the
  accessibility service off and back on would re-grant the screenshot
  capability -- but that advice assumed a toggle switch that turns out not
  to exist on at least one real device (some launchers/OEMs expose only
  the accessibility-button shortcut, never a separate service switch), so
  it was undeliverable. That pre-check is removed entirely. The app now
  calls the platform's screenshot API directly whenever the glass wants a
  backdrop, and reports the real result -- success, with the captured
  image's size, or the exact numeric error code and what it means --
  temporarily on both outcomes, not just failure, so this can finally be
  diagnosed from real data instead of another guess.

## 2026-09-14 — 1.0.40

- 1.0.39's diagnostics still had one silent path left: if any of the three
  settings that gate the glass capture (the refract-the-screen toggle,
  show-background, or Translucent mode) was off, the app returned without
  telling the user anything -- leaving "no toast ever appears" ambiguous
  between "not even trying to capture" and "tried and the platform gave no
  response." That path now reports which of the three settings is actually
  the blocker.

## 2026-09-14 — 1.0.41

- Device testing confirmed the glass diagnostics were working, but the
  toast text was being cut off by this device's two-line Toast limit right
  where the one genuinely useful token -- an exception's class name --
  started, so the last diagnostic round-trip produced an unreadable
  result. Every glass diagnostic toast is now short enough to fit on two
  lines and leads with the actual technical detail (exception name plus a
  trimmed message, the error code, the captured image's dimensions, or
  which setting is off) instead of English framing text around it.

## 2026-09-14 — 1.0.42

- Repeated attempts to fit the glass backdrop capture's diagnostics into a
  Toast kept failing because the device being used to test on truncates
  Toast text to two lines, and the one genuinely useful detail (an
  exception's class name, a full error message) kept landing wherever
  that cutoff happened to fall -- a Toast was never going to reliably
  carry a message whose length isn't known in advance. Added a small
  in-app diagnostic log instead: every glass-capture outcome (why a
  capture wasn't even attempted, the platform's exact success or failure
  result, any exception) is now recorded in full and viewable from a new
  button in the main screen's top bar, with one-tap copy-to-clipboard for
  sharing the whole thing.

## 2026-09-14 — 1.0.43

- Moving away from chasing real screen refraction, which some devices'
  platform policy silently refuses no matter what (as the last several
  releases established), the glass panel now gets a real per-layer blur
  (Android's own RenderEffect/GraphicsLayer blur, not just the old
  backdrop-only downscale) applied to its tint and grain, so it reads as
  frosted glass whether or not a real captured backdrop is behind it --
  the existing Blur slider now drives this directly.
- The panel's border is also now a diagonal light gradient instead of a
  flat semi-transparent line, brighter at the corner a light source would
  actually catch, giving the rim real depth; applied to both the
  bar-style/expanded-mixer panels and the disc's own ring.

## 2026-09-14 — 1.0.44

- Found the actual reason 1.0.43's real blur (and, on inspection, the
  bar-style panel's own shadow) never showed up on device: a window a
  Service adds via `WindowManager.addView()` -- which is how this app's
  whole overlay is created -- stays software-rendered unless
  `FLAG_HARDWARE_ACCELERATED` is set on its `LayoutParams` explicitly.
  Unlike an Activity's window, it does **not** inherit
  `android:hardwareAccelerated` from the manifest automatically. Both the
  glass panel's blur (a `graphicsLayer` `renderEffect`) and its elevation
  shadow (`Modifier.shadow`) need hardware-accelerated rendering to draw
  anything -- without it they don't fail or log, they silently paint
  nothing, which is exactly what made both look like they were doing
  nothing at all. The flag is now set on the overlay window.
- The border's diagonal light now brightens at two opposite corners
  instead of one, fading in between -- closer to how a real edge catches
  light from both a source and its own reflection.
- The expanded mixer's own panel never had an outer-edge shadow of its
  own (only its individual sliders did, once the panel was switched off)
  -- it now gets the same soft shadow the bar-style panel already wraps
  itself in, controlled by the same existing Shadow switch.

## 2026-09-15 — 1.0.45

- Added Atmosphere as a third choice alongside Glass and Solid for every
  background-effect selector (bar styles, the expanded mixer, and the
  disc's own ring): a burst of colored grain, seeded from the panel's own
  base color via a small AGSL shader, that flickers for about half a
  second and then holds perfectly still behind the sliders -- closer to
  how Nothing OS's own wallpaper generator resolves a field of static into
  one fixed image than a looping animation or a flat cut to a still. It
  needs no screen capture and no real blur, so there's no
  accessibility-capability or hardware-acceleration dependency for it to
  fail against -- just the panel's own color -- and its opacity has a
  dedicated slider of its own.
- The glass panel is now lit by a single beam of light rather than two
  unrelated gradients: the sheet thins (so lightens) where the beam
  crosses it, and the rim catches that same beam at exactly the two points
  it runs off the shape's edge. Both halves read off one axis, which is
  what makes it look lit rather than merely shaded, and two new sliders
  under the Glass selector turn that beam and set how broad it is.
- Renamed the Translucent background style to **Glass**, and moved the bar
  background section directly below the colors section.
- The glass tint's opacity now comes from the Background color's own alpha,
  set with that color picker's opacity slider, instead of a separate Glass
  opacity slider that duplicated it -- one control for both the color and
  how much of it there is. Untouched installs keep the same sheet they had.
- Removed the "Refract the screen" option along with the whole screenshot
  backdrop pipeline behind it (including the service's `canTakeScreenshot`
  capability declaration, now that nothing asks for a screenshot). The
  platform refused that capture outright on at least one real device and
  the option did nothing there; the glass now stands on its own tint,
  grain, blur and rim light, which is what it had really been doing all
  along. The Blur slider no longer hides behind that switch.

## 2026-09-15 — 1.0.46

- The glass beam is now light rather than a hole in the tint. It used to
  thin the sheet where the beam landed, which only reads as light when
  whatever is behind the panel is brighter than the tint -- over a dark
  background the same gradient read as a *shadow*, exactly backwards. The
  tint is now even everywhere and white light is added along the beam, so it
  brightens over any background.
- Fixed the flat rectangle visible inside the panel, most obviously in the
  expanded mixer: the blur used `TileMode.Decal`, which treats everything
  past the layer's bounds as transparent and so faded the tint out over the
  blur's whole radius at every edge, leaving a visible inset edge that far
  in. It now clamps instead, carrying the edge pixels outward.
- The panel shadow is black instead of the theme's own background color. A
  shadow tinted the same color as the panel it sits behind is invisible
  against any background near that color -- a white shadow on a white page --
  which is how a switched-on shadow kept looking switched off.
- Dropped the fixed diagonal sheen from the glass grain: it was a second
  light direction, unrelated to the beam and quietly working against it.
- Atmosphere now *turns*. The grain field is wound around the panel's center
  and rotates through about 120 degrees as the popup appears, decelerating
  into stillness, instead of reshuffling itself every frame -- which read as
  static going in all directions at once rather than as one thing moving.
- Atmosphere's two colors now come from the launcher icon of whatever app is
  actually running underneath the popup, rather than the panel's own tint.
  An in-app screenshot of that app is not reachable at all -- a window can
  only ever draw or capture its own pixels, never another app's, which are
  composited together by the system only once both reach the display -- and
  real per-pixel sampling would in any case need exactly the screenshot
  capability the platform refuses outright on some devices (see the 1.0.45
  entry). An initial pass used the wallpaper's own colors instead, reachable
  everywhere but wrong whenever an app other than the launcher is on screen
  -- true most of the time a volume popup actually appears. The app's own
  icon (`PackageManager.getApplicationIcon`, read via the same
  `rootInActiveWindow` this accessibility service already uses to find the
  foreground app, run through `androidx.palette`) needs no extra permission,
  works over any app rather than only the home screen, and is cached per
  package so only the first popup over a given app pays for the lookup.
  Falls back to the panel's own tint whenever no foreground app resolves.

## 2026-09-15 — 1.0.47
- Atmosphere's opacity now comes off the Background color's own alpha, the
  same as Glass, instead of a separate "grain opacity" slider that ignored
  it entirely -- setting that color's opacity to 0% used to leave an
  Atmosphere panel fully visible; now it disappears like every other color
  role does.
- The Disc style's own Blur slider used to do nothing at all: the ring's
  Glass backing was painted straight into VolumeDisc's shared Canvas, which
  has no graphics layer of its own for a real (RenderEffect) blur to run
  on. It's now painted by a genuine sibling composable, clipped to the
  ring's own annulus, with the same blur the bar styles and expanded mixer
  already had. The grain layer's own noise is also coarser now (a few dp
  per cell instead of one pixel), since single-pixel noise already averages
  away almost entirely under the smallest real blur radius -- there was
  nothing left with any spatial size for the slider's higher end to
  visibly soften.
- Atmosphere's settle animation is shorter (650ms, was 900ms) -- same
  curve, same distance, just quicker to finish.
- Atmosphere no longer lights its ring's edge to match Glass's own beam: a
  beam-lit rim on an effect with no beam of its own read as a mismatched
  leftover from Glass. It now gets the same plain outline Solid mode
  already has.
- The removed "grain opacity" slider's slot now holds a grain *intensity*
  slider instead: how strongly the grain's own texture shows, from a
  perfectly smooth two-color sweep at 0% up to the full grain at 100%,
  independent of the panel's own opacity.
- The background-style section (Glass/Solid/Atmosphere and each one's own
  knobs) now sits directly under the Popup section's style picker instead
  of above it, next to Colors -- it's a per-style setting (bar and disc
  keep independent copies), so it only makes sense once the style it
  belongs to has actually been picked.

## 2026-09-15 — 1.0.48

- Atmosphere now ignores the Background color's own transparency entirely
  and is always fully opaque -- a settling field of grain read as an
  unfinished, half-see-through smear at anything less than 100%, unlike
  Glass's own gradient/blur, which still reads as glass at any opacity.
  Added a dedicated grain-size slider alongside the existing intensity one:
  the shader's fixed cell scale read as too fine to register as grain at a
  glance, and the new default is coarser.
- Glass grows a real dedicated noise/sheen layer now, with its own color
  picker and transparency slider, independent of the panel's own tint and
  the blur strength.
- The disc's tick ring is always a complete 360° wheel, whatever the
  screen edge cuts off a laterally-anchored disc -- it used to remap the
  whole ring onto just the arc still visible, which read as the wheel
  itself shrinking rather than a knob mounted partway behind a bezel.
  Restored the "rotating knob" tick animation (the whole ring turns
  together, one tick riding the fill's leading edge) as a switch next to
  the current fixed-slot, growing-landmark one.
- Removed the theme (dark/light/system) selector from the customization
  screen.
- Position (anchor + both offsets) and every color role are now three
  fully independent sets, one per popup style, instead of one shared set
  across all three.
- The top preview now renders the real popup at real scale, live-updating
  -- and along the way fixed a real gap: the old preview never actually
  painted Glass or Atmosphere behind the bar styles, only a plain shadowed
  box, so those two effects were invisible there even though the disc's
  own preview already showed them correctly. The old phone-silhouette
  position mockup moved down next to the anchor grid, whose cells now grow
  tall enough to match its height.
- In landscape, the popup nudges itself clear of the display's camera
  cutout when it would otherwise land partly behind it.

## 2026-09-15 — 1.0.49

- Fixed the collapsed-bar preview being visibly different from the real
  popup: it was missing the ringer switch and the panel's own padding
  entirely, and used hand-picked panel/slider dimensions instead of the
  real popup's own geometry (button size, slider width/height, inner
  padding) -- rebuilt both bar branches structurally identical to
  `CollapsedVolumePopup`, and gave the expanded-mixer preview the panel
  shadow it was missing too.
- Fixed the disc's longest tick not tracking the actual level: it read the
  level as a fraction of the full 360° sweep, but the fill arc itself
  remaps onto a shorter visible arc once the disc is laterally cut near a
  screen edge -- so on any clipped disc the tick pointed nowhere near
  where the fill actually was. Both the landmark tick and the
  rotating-knob rotation now read the same angle the fill arc itself uses.
- Redesigned the Glass noise layer as sparse, jittered, round flecks with
  real empty gaps between them, instead of covering every cell with some
  alpha (which read as a wall-to-wall pixelated wash and barely changed
  under blur) -- the tint/beam underneath now reads through cleanly, and
  blurring the layer actually melts it into a convincing frosted haze.
- Centered the anchor grid + position preview row as one block instead of
  hugging the left edge, with breathing room from the surrounding text.
- Replaced the panel's own platform elevation shadow (heavier on one side
  than the other) with a real Gaussian-blurred halo -- a solid copy of the
  panel's shape, blurred, painted behind it -- so depth reads evenly all
  the way around the outer edge. Applied to the collapsed bar panel, the
  expanded mixer, and both preview equivalents.
- Fixed the ringer switch silently doing nothing when a mode change was
  refused: the tap wrote the new mode optimistically, then wrote it back
  on refusal, both within the same synchronous click handler and before
  Compose ever got a frame to show the first value -- so a refused change
  looked exactly like the tap had done nothing, every time. It now calls
  `setRingerMode` first and writes state exactly once, from the real
  result. Also declares `MODIFY_AUDIO_SETTINGS`, missing for the
  unprivileged ring/vibrate path that doesn't need Shizuku at all.
- Swapped the ringer switch's ring/silent icons from the bell-shaped pair
  to the same speaker-with-waves/speaker-with-slash glyphs the main volume
  icon already uses, so both read as the same "sound on/off" language.

## 2026-09-17 — 1.0.50

- Root-caused the ringer switch regression by comparing it against the Do
  Not Disturb toggle, which has worked throughout: `AudioManagerProxy`
  used to try a plain, unprivileged call first and only escalate to
  Shizuku on refusal, verifying the outcome with an immediate readback and
  returning a boolean for the caller to branch on. That branching -- absent
  from the working `NotificationManagerProxy`, which just has one
  always-elevated setter and getter -- was the recurring site of every
  previous "fix the ringer switch" attempt (an optimistic write later
  corrected in the same synchronous click handler, then a coroutine
  dispatch that made the switch animate without actually changing the
  mode, then a revert back to the original bug). `AudioManagerProxy` now
  mirrors `NotificationManagerProxy` exactly, and the ringer switch's click
  handler mirrors the Do Not Disturb toggle's: call the setter, read the
  real mode back through the same proxy, write local state exactly once.

## 2026-09-18 — 1.0.51

- Fixed a crash on tapping the ringer switch (reported from a Nothing
  Phone on Android 17): the previous release's elevated `setRingerMode`
  call had no exception handling, so a `SecurityException` ("Not allowed
  to change Do Not Disturb state") from the audio service crashed the app
  outright instead of just refusing the change. On this platform version
  that check is gated on the calling package, not bypassed by Shizuku's
  elevated UID the way the Do Not Disturb toggle's own call is -- so the
  refusal is real and can still happen even fully elevated. The call is
  now wrapped in a try/catch that leaves the mode alone on refusal; the
  button's own readback right after already shows the true, unchanged
  state, so it fails visibly but safely instead of taking the app down.

## 2026-09-18 — 1.0.52

- Found the actual cause of the ringer switch's remaining flakiness in the
  Disc style, reported as: works going ring to vibrate, does nothing going
  vibrate to silent, crashes going silent to ring. All three are the same
  conflict: the ringer switch sits in the disc's own hollow middle,
  sharing that screen region with the disc's vertical-drag-to-set-volume
  gesture. Touch-slop detection for that drag doesn't require an
  unconsumed touch, so ordinary finger jitter during a tap on the switch
  could still be picked up as a drag starting there -- which either
  swallowed the switch's own tap, or fired a phantom media-volume change
  through a raw, unguarded call that could throw the very same Do Not
  Disturb-gated crash the ringer switch itself was fixed for last release.
  The disc's drag gesture now checks where a touch starts before ever
  tracking it: one starting inside the switch's hole is never treated as
  a disc drag, for the whole gesture, leaving the switch's tap uncontested.
- Also wrapped the two other places that change stream volume directly
  (the collapsed popup's media slider, the expanded mixer's per-stream
  sliders) in the same catch used for the ringer switch, as a backstop
  against this same refusal on other paths.

## 2026-09-18 — 1.0.53

- Found the real reason silent was unreachable: on this platform,
  `AudioManager.setRingerMode` gates silent behind Do Not Disturb access
  granted to the *app's own package* -- not the caller's UID, which is why
  routing the call through Shizuku (every earlier attempt, including last
  release's) could never satisfy it, elevated or not. That same gate also
  explains why no volume could be changed at all while already silent, on
  any slider, not just the disc -- it isn't a gesture problem. Reaching
  silent no longer touches Shizuku: the switch now asks Android for real
  Do Not Disturb *access* the standard way (a one-time system settings
  screen), which is a permission grant only, with no side effect -- unlike
  actually turning Do Not Disturb *on*, which the app used to lean on
  instead and which silences media and notifications along with the
  ringer. Once granted, the switch and every volume slider work normally
  in every ringer mode, including silent, with no other setting touched.
  `AudioManagerProxy`, which existed only to route this one call through
  Shizuku, is gone -- nothing needs it any more.

## 2026-09-18 — 1.0.54

- Fixed NoMixer not appearing in the Do Not Disturb access settings screen
  at all, so the permission request from last release opened the right
  screen but left nothing to grant it to. The manifest never declared
  `android.permission.ACCESS_NOTIFICATION_POLICY` -- the one apps are
  supposed to hold to use the Do Not Disturb access APIs -- and at least
  on this platform, the settings screen only lists apps that declare it.
  Declared it now.

## 2026-09-18 — 1.0.55

- Reverted the disc's drag gesture to its plain, original form. The
  gesture-conflict theory behind last week's change was wrong: the
  ringer switch's real problem across every popup style, not just the
  disc, was the missing Do Not Disturb permission, already fixed. The
  rewritten gesture broke ordinary dragging on the disc itself, so it's
  gone.
- Confirmed silent genuinely can't be reached without the platform's own
  Do Not Disturb turning on with it: since Android 7, a silent ringer
  and Do Not Disturb's "Total Silence" are the same state in the
  framework, for any app or method that sets it, not something this app
  adds on top. Nothing to fix here -- 1.0.54's behavior is correct.

## 2026-09-19 — 1.0.56

- Correction to last release's note that reaching silent without the
  platform's Do Not Disturb turning on "genuinely can't be done": that
  was true of `AudioManager.setRingerMode`'s public API specifically,
  not of the ringer mode itself. That public entry point
  (`setRingerModeExternal`) gates silent behind Do Not Disturb access
  and brings real system Do Not Disturb along with it as a side effect
  of the *same call* -- confirmed by testing, it visibly turned Do Not
  Disturb on and left it there. `cmd audio set-ringer-mode`, the shell
  command `adb shell` (and Shizuku's shell) runs, reaches AudioService
  through a different internal entry point that carries neither the
  permission gate nor the Do Not Disturb coupling. The ringer switch now
  runs that command as a Shizuku shell process instead of calling the
  public API directly -- the same mechanism already used elsewhere in
  the app to grant itself permissions. Reaching silent, and leaving it,
  no longer touches Do Not Disturb at all.
- Removed the Do Not Disturb access permission request and the
  `ACCESS_NOTIFICATION_POLICY` manifest declaration from two releases
  ago, both made unnecessary by the above.

## 2026-09-19 — 1.0.57

- Every slider (the bars, the disc, the ringer switch, the expand/collapse
  morph) now settles with the platform's own interruptible springs
  instead of a fixed duration, and carries the finger's own release
  velocity into that spring instead of restarting from zero -- a flick
  keeps moving like it was actually flicked, and a new drag interrupts
  mid-settle without snapping first. Color transitions across the same
  surfaces (ringer switch, toggle buttons, volume icon, popup, mixer
  panel, theme crossfade) move through the same spec family, which
  collapses to an instant snap under the platform's "Remove animations"
  accessibility setting instead of ignoring it.
- Added real haptic feedback: a tick on every discretized step of a
  drag (scaled to how big a step it just crossed), a firmer pulse at
  each slider's ends, and a distinct pulse for mute/unmute and for the
  ringer switch's own mode changes and the popup's expand-swipe
  threshold. Silent when the device has no vibrator, and respects the
  user's own haptics setting.

## 2026-09-25 — 1.0.75

- **Restart the accessibility service from the app.** 1.0.74's glass needs
  the service's screenshot capability, but the system only reads a
  service's capabilities when it binds it, and an app update doesn't
  rebind -- so a service first switched on by an older version keeps
  running without it, and every capture is refused. On systems that list
  no switch for the service in their accessibility settings there was no
  way to rebind it short of restarting the phone. When the service is
  running without the capability, the main screen now says so and offers
  a **Restart accessibility service** button, which switches it off and
  back on through the same secure setting the app already uses to enable
  it. The glass's refusal in the diagnostic log now points to it.

## 2026-09-24 — 1.0.74

- **Real frosted glass.** Glass is now a pane over the actual screen behind
  the popup, blurred: every time the popup appears, the accessibility
  service takes a still of the screen just before the popup goes up, and
  the app blurs it itself and draws it behind the glass, lined up with the
  screen -- it stays in place while the panel opens, closes and moves, and
  while the disc turns in. None of it uses the platform's own window blur,
  so battery saver doesn't switch it off. The Blur slider sets how blurred
  the screen behind is.
- The screenshot capability (`canTakeScreenshot`) is back in the
  accessibility service's declaration. Where the platform still refuses the
  capture, the glass falls back to the frosted grain of 1.0.73, and the
  diagnostic log now names the platform's reason correctly -- the old
  capture's log read the platform's error codes off by one, so "no
  accessibility access" and "asked again too soon" were reported as each
  other's neighbours. If the log says the capability isn't granted, turning
  the accessibility service off and on again grants it.

## 2026-09-24 — 1.0.73

- **The bars come out of the screen edge the way the mixer goes into it.**
  A vertical or horizontal bar's panel now opens out of the edge it hugs --
  from nothing at the edge to its full size -- and only then does the bar
  itself come out onto it. On the way out the bar goes first, and the panel
  shuts back into the edge after it.
- **The mixer closes one step at a time.** Its rows tuck away completely
  before the panel moves; the panel then closes along its edge to a single
  band, and only once it has does it shut into the edge. The panel steps
  follow the edge rather than the swipe, so the last thing it does is
  always go into its edge -- and a centred mixer closes to a band and then
  into its own middle, rather than drawing itself out into a long hairline
  first. The whole close is also quicker, and no longer leaves a sliver
  standing at the edge before it goes.
- **Opening the mixer clears the popup at once.** The vertical bar no longer
  drifts to the middle of the growing panel before disappearing, and the
  disc's ticks no longer linger: the popup's content goes straight away,
  where it is, the disc's face gives way to the panel behind it, and the
  panel then opens as before, rows cascading in.
- **Frosted glass.** The glass grain is coarser and sparser -- bigger dots,
  further apart -- and the blur no longer washes it out: the blur used to
  average the grain down to nothing, so more blur made the glass clearer.
  It now spreads and softens each grain instead, until they run together
  into a milky, mottled veil that hides what's behind the panel the way
  frosted glass does, without ever capturing the screen. It's cheaper to
  draw, too: there's no real blur pass behind the glass any more.

## 2026-09-23 — 1.0.72

- **The mixer opens the way the finger went.** No turn any more: the panel
  is drawn out along the axis of the opening swipe first, in its
  direction, and then across it to the mixer's full size, while the rows
  keep coming out one at a time exactly as before (the media row now
  included).
- **The disc grows as its panel opens round it.** The panel's face comes up
  behind the disc from the start of the opening, and the disc travels with
  it and grows as it fades, instead of shrinking to fit a thin row.
- **The disc turns slightly as it arrives, and back as it leaves** -- the
  whole disc, as one object.
- **The mixer shuts instead of fading.** Its rows tuck back in, it closes
  across, and then it slides shut all the way into the screen edge the
  popup came out of, until nothing is left of it.
- **The mixer is its original width again**: the width the platform gives
  a window that wraps its content, which is what it always had before the
  window stopped wrapping it.
- **Glass grain is a fine, even lattice.** Dense, equally spaced dots with
  clear glass between them. Its opacity is the noise colour's own, from the
  colour picker -- the separate transparency slider is gone -- and it no
  longer depends on the background colour's opacity.
- **Shadow width is back**, next to shadow opacity.

## 2026-09-23 — 1.0.71

- **One panel, in one window that never moves.** The compact popup and the
  mixer are no longer two surfaces handing over to each other: there is one
  panel -- one shadow, one sheet of glass or grain, one rim -- whose
  rectangle travels from the compact popup's to the mixer's while their
  contents take turns inside it. The overlay's window now covers the whole
  screen and stays perfectly still; touches that miss the panel go straight
  through to the app underneath (the same touchable-region mechanism the
  platform's own volume dialog uses). The window used to be exactly the
  panel's size, so every change of shape meant moving and resizing it
  through the window manager -- always a frame early or late, and hidden
  outright while it was swapped for the mixer's. That was the step between
  the two, and the snap at the end.
- **The opening is choreographed in two chained phases.** First the panel
  travels to exactly where the mixer's media row will be and takes its
  shape -- a vertical bar turning a quarter clockwise on the way, its content
  staying inside the reshaping panel -- and the popup's content hands over
  to the media row in place. Before that has quite come to rest, the panel
  opens out of the row into the whole mixer. Both destinations are computed
  before anything moves; the centred mixer's from the display's own size.
- **The rows arrive one at a time, as objects.** Each row slides out from
  under its neighbour on a spring of its own, 65 ms after the one above it,
  and leaves the same way in reverse. The ringer and Do Not Disturb switches
  travel with the ring row.
- **The mixer keeps 20 dp from each side of the screen** (40 dp narrower
  than the display).
- **The shadow slider sets its opacity, not its width.** The width is back
  to its fixed 12 dp.
- **Atmosphere's blobs are part of the fill, and the grain is on top of
  everything.** Flat fill, then large soft blobs of colour painted into it,
  then the grain over both -- so the blobs break up into grain at their
  edges instead of sitting on it. Where each blob starts, which way it
  drifts and which grain field the panel settles on are drawn afresh every
  time the popup opens. The pinwheel the colours used to be wound in is
  gone; it met in a point in the middle that read as a hole.
- **The disc arrives and leaves as one object.** It slides out of its edge
  (or grows in place) with everything on it, instead of its ring turning
  into place under a face that stood still and its hand being drawn on
  afterwards.

## 2026-09-22 — 1.0.70

- **The bar turns before the mixer exists, and its window turns with it.**
  It used to lie down inside the mixer's own window -- which at that moment
  is short, because its rows haven't unfolded yet -- so the turning
  rectangle simply ran out through the sides of it. The turn now happens
  while the compact bar is still the only thing on screen, and its own
  window grows to exactly the rectangle the turning panel sweeps out. That
  also removes the guess at the end of it: the mixer is handed the
  rectangle the bar really ended up lying in instead of an estimate, which
  is what made the hand-over between the two read as a step.
- **The enter and exit are markedly slower.** The panel tier was tuned for
  a button answering a finger; a whole mixer crossing the display on it was
  over before anything inside it could be seen. The travelling springs have
  their own stiffness now, about a third of what they were, and the turn is
  slower still -- it is the whole of what there is to watch.
- **The mixer is as wide as it used to be again.** 1.0.68's shadow margin
  was ordinary padding, so it took its width out of the constraints the
  panel laid itself out in. It adds room *around* the panel now without
  taking any from it.
- **Every row in the mixer cascades, the app ones included.** They were the
  exception before, and it showed: the system rows changed the panel's
  height under them every frame, each app row's own `animateItem` chased
  those changes with a spring of its own, and the only thing that appeared
  to move at all was a stack of app sliders sliding around over rows that
  looked frozen. One value, one direction, top to bottom -- and rows unfold
  downward from under the panel's closing edge rather than over the row
  above them.
- **The last of the snap toward the centre.** The window was pushed from a
  collector watching the morph's value, and a snapshot flow emits on the
  frame *after* the one that changed it -- and conflates, so it skipped
  steps whenever a frame ran long. It is pushed from the animation's own
  per-frame callback now, on the same frame as the layer it belongs to.
- **The shadow has a width setting.** A slider in the popup's own section,
  0 to 40dp, which sets both how far the halo reaches and how much room the
  window carries for it. The disc's ring forms again on the way in, too:
  the turn was removed when its glass backing was a sibling that couldn't
  turn with it, and now that the glass is on the knob's face -- where a
  circle looks the same at every angle -- there is nothing left to disagree
  with it.
- **Atmosphere's speckles are part of the field.** They were circles drawn
  over the top, too few and too small, untouched by the grain. They are
  marks inside the shader now: bigger, sparser, one to a cell, modulated by
  the grain underneath them and turning and drifting with everything else.

## 2026-09-22 — 1.0.69

- **The panel stops snapping into place at the end of its journey.** A
  spring stops as soon as it is within its visibility threshold and jumps
  the rest of the way, and the usual one percent is nothing on a scale or
  an alpha -- but the morph is a 0-to-1 number that gets multiplied up into
  real pixels, so one percent of a mixer travelling four hundred of them to
  the middle of the display is a four-pixel jump, landing exactly at the
  end. The arrival and the morph now run on a spring whose threshold is
  measured against what is actually drawn, the same correction Atmosphere's
  own settle needed in 1.0.67.
- **The vertical bar lies down before the mixer opens, not while.** It
  turns a right angle about the ringer switch it hangs from -- always
  inward, so a bar against the left edge turns the opposite way from one
  against the right rather than swinging its body out over the edge it is
  hugging -- and the mixer only starts opening once that turn has finished.
  The two used to overlap, which read as two things happening to two
  different objects.
- **The mixer's rows now carry the panel's own border with them.** Each
  row's reveal is its real laid-out height rather than a transform, so the
  panel grows as they land and shrinks as they leave, and its border keeps
  exactly the gap it has at rest the whole way through -- following the
  rows rather than arriving before them or sitting still. A row that hasn't
  unfolded yet is painted over by the one above it rather than clipped, so
  no shadow is lost to an animation.
- **The expanded panel leaves as itself.** It used to hand its face back to
  the compact popup on the way out, which meant re-composing that panel
  underneath and crossfading to it -- and since nothing follows the exit but
  the window being taken down, all that ever did was flash a bar across the
  middle of the dismissal. The rows retract one at a time from the bottom
  up now, the border closes down after them, and there is no second panel
  in the animation at all.
- **A zero offset means the panel touches the screen edge again.** 1.0.68
  gave the window 20dp of margin for the shadow to bleed into but measured
  the user's offset from the window rather than the panel, so a
  zero-offset panel quietly sat 20dp off the edge it is supposed to hug.
  The offset is the distance to the *panel* now, and the margin -- with the
  shadow in it -- hangs off the display instead.
- **The disc's background effects moved from the ring to the knob.** Glass
  and Atmosphere paint the disc's own face now; the ring is left to do the
  one job it has, which is reading the level. A real knob is a disc of
  material with a scale around its edge, and it is the disc that is made of
  something.
- **Atmosphere has speckles.** A scattering of grainy flecks over the
  field, half light and half dark so they read against whatever colours the
  app underneath gave it, settling in on the same ambient spring the field
  itself uses -- once, then frozen. No loop, and visible on the compact
  popup and the mixer alike.

## 2026-09-22 — 1.0.68

- **The compact panel turns into the mixer instead of just scaling into
  it.** Growing out of the vertical bar, the panel turns 90° clockwise as
  it moves into the media row's own position, then straightens back out as
  it finishes becoming the mixer -- a vertical strip lying down to read as
  a row, rather than being squashed sideways into one. Growing out of the
  disc, the panel's own corners relax from a full circle down to the
  mixer's configured radius over the same stretch -- a knob uncurling
  rather than a circle being stretched into a rectangle. The horizontal
  bar already matches the row's own orientation, so it keeps travelling
  straight through with neither flourish.
- **The mixer's own rows arrive a scaletta.** Call (when shown), Ring, Alarm
  and Notification settle in from just behind the row above them, each a
  later slice of the same arrival the panel's own morph already rides --
  never a spring of their own. The media row itself is exempt: it's the
  panel becoming the mixer, already carrying its own crossfade from the
  compact popup, so it doesn't also get counted as one of the rows unfolding
  underneath it. Reversed on the way out for free, off the same slices: the
  bottom row tucks away first, working back up to the one the panel folds
  back into. The ringer switch and Do Not Disturb button travel with the
  Ring row's own reveal rather than staggering separately, since they're
  its own footer, not rows of their own.
- **Panel-level shadows get real room to bleed into.** A blurred halo
  behind a bar or the mixer panel (PanelShadow) used to have nowhere to
  spread past the panel's own edge -- the window was never bigger than the
  panel itself, so the halo was cut off exactly where the panel ended. The
  window now carries 20dp of invisible margin around the panel whenever
  there's a halo to make room for (never for the disc, which paints its own
  shadow inside its own ring and needs none of this), and a tap that lands
  in that margin dismisses the popup just as a tap outside the window
  always has. Without an offset the panel still hugs the screen edge and
  the shadow on that side is still covered by the display -- that part was
  never the bug.

## 2026-09-22 — 1.0.67

- **The volume keys change the volume again.** A press showed the sliders
  and left the level exactly where it was. `onKeyEvent` asks for the
  adjustment *before* it puts the popup on screen, and the adjustment was
  guarded on the popup already existing -- so on the first press after a
  dismissal the guard failed and the only adjustment left was the
  auto-repeat half a second later, which releasing the key cancels. A
  normal tap therefore changed nothing, and because this service consumes
  the key event the platform did not apply it either. The guard is gone:
  there was nothing for it to protect against, since a volume key means
  change the volume whether or not a panel happens to be on screen yet to
  draw the result. (Not a regression from the recent motion work -- it has
  been there since 1.0.56.)

- **The mixer no longer opens already cut off by the screen.** A laterally
  anchored disc deliberately sits half off the side of the display, and the
  window it lives in is allowed past that edge. The morph took the disc's
  rectangle as it literally is -- partly nowhere -- and started the mixer
  there, so the panel's first frame was outside the display and it had to
  travel in from a place it should never have been. The journey now starts
  from the part of the panel the user can actually see.
- **One volume glyph, and it is the speaker the ringer switch already
  wore.** The hand-drawn speaker whose waves grew and retracted with the
  level is gone: the mixer, the compact bar, the disc and the ringer switch
  all draw the same Material speaker, at the same size, keeping both of its
  waves at every level -- including at zero, where it simply wears the mute
  bar. Dropping the waves as the volume fell was a second way of saying
  what the bar beside it already said, and it made the icon change shape
  for a reason the bar had covered. "Silenced" is a mark put on a speaker,
  not a different speaker.
- **Bluetooth everywhere there is a media level.** Compact bar, disc and
  expanded mixer alike: with an audio device connected the speaker's place
  is taken by the Bluetooth mark, which wears the same bar at zero. It used
  to turn back into a speaker when silenced, which says the output device
  changed when only the level did.
- The glass flare is a hint rather than a sweep. It was thrown a third of
  the way round the panel at twice its settled brightness, so the eye
  followed the band travelling instead of noticing the glass catch
  something. The throw is now a third of what it was and the flare a
  fraction over its resting strength; the speed is unchanged.
- The Atmosphere field no longer ticks as it stops. A spring stops once it
  is within its visibility threshold and jumps the rest of the way, and a
  default one percent is nothing on most properties -- but this value gets
  multiplied up before it is drawn: one percent of the field's whole turn
  is a degree, and one percent of the fourteen grain fields it dissolves
  through is a seventh of a field. It runs all the way down to rest now,
  which costs a few frames nobody can see.

## 2026-09-22 — 1.0.65

- **The disc's ring arrives whole.** It used to turn into place -- a
  rotation on the Canvas that paints the track, the arc, the rim and the
  ticks. But that Canvas does not paint the whole ring: when the backing is
  Glass, the lit sheet behind it is a *sibling* layer, because a real blur
  needs a graphics layer of its own, and that sibling did not turn. For the
  whole of the entrance a still sheet sat under a rotating arc, and the
  further through the turn it got the worse the two disagreed. Turning them
  together is not the fix either -- that rotates the glass pane, and a pane
  of glass that turns is a picture of glass on a piece of card. So nothing
  turns: the ring arrives at its final angle, and the only thing on the
  dial with an entrance is the hand written onto it.
- **Glass and Atmosphere have their own spring, and it is slow.** Both used
  to ride the panel's arrival, which meant they were over in the few dozen
  milliseconds a panel takes to slide out of an edge -- underneath the much
  larger motion doing the sliding. Nobody saw either of them. A light
  settling on a sheet is not the sheet arriving a second time; it is what
  happens to the sheet once it is there, and it takes longer. They now run
  on an `Ambient.enter` token an order of magnitude softer than anything
  else in the file, so the panel shows up and *then* you watch the light
  find its angle and the field turn to where it comes to rest. It still
  runs once and freezes -- no loops -- and it has no exit, because by then
  the panel is fading. Visible on the compact panel and on the mixer alike.
- **One volume glyph everywhere.** The mixer's media row drew a plain
  Material speaker while the compact popup and the ringer switch drew the
  shared one whose waves follow the level. It draws the shared one now, at
  the same size, with the same mute bar.
- **The Bluetooth mark wears the mute bar too,** and it no longer
  disappears at zero. Routed to a sink and silenced, the icon used to turn
  back into a speaker to borrow its bar -- which says the output device has
  changed when only the level has. One mark for "silenced", whatever the
  glyph underneath happens to be. The mark also shows in the expanded
  mixer's media row now, not just in the compact popup.

## 2026-09-21 — 1.0.64

- **The mixer is the compact panel changed shape, not a replacement for
  it.** The panel it grows out of now stays composed underneath for the
  whole morph and hands its face over on the morph's own effects channel,
  so the two are one element changing shape rather than one removed and
  another drawn with only the rectangle agreeing about what happened. The
  outgoing face's layer cancels the container's morph scale exactly, so it
  sits at true size over the pixels it occupied a frame ago and travels
  only because the container's centre does. The exit runs it backwards.
- Glass and Atmosphere were invisible on the mixer, because the arrival
  they phase off was the appearance spring -- which is snapped straight to
  1 for the one entrance the mixer has. The arrival is the product of both
  springs now, with exactly one of them travelling at a time, so the beam
  flares and sweeps and the field turns as the mixer morphs in, then
  freezes.
- **The reflection on the glass takes the back of the arrival rather than
  all of it.** It used to sweep across the whole entrance, which meant it
  happened *while* the panel was still sliding out of the side of the
  screen, over the few dozen milliseconds a spring needs to cover its
  distance: too fast to follow, and hidden behind the larger motion
  carrying it. A spring covers its distance early and then spends most of
  its time creeping the last little way in, so the tail of one arrival is
  long in seconds while being almost still on screen. The light now holds
  its thrown angle and its flare while the panel does the travelling, and
  sweeps home across that tail -- slowly, over a panel that has visibly
  already arrived, finishing just after it comes to rest. No second clock:
  it is the same single arrival, read from a later point.
- The ringer switch's silent face is the media slider's own speaker wearing
  the app's one mute bar, rather than a second speaker drawn differently
  beside it -- ringing and silent are one picture crossed out. The same bar
  on the Do Not Disturb switch is inverted, because a prohibition sign is
  crossed out when the prohibition is *not* in force.
- Shadows and item animations that were being cut by clips with nothing
  drawn on them: the mixer's inset is the list's own content padding now,
  so the scroll container's clip sits at the panel's edge instead of on the
  rows, and the compact panel makes room for the element shadows its
  Surface was otherwise cutting off at the corners.

## 2026-09-21 — 1.0.63

Two separate reasons the last round's changes weren't visible, and both are
fixed here.

- **Most of them were never in a build.** The haptics, the shared speaker
  glyph, the mute bar as one overlay on every volume icon, the Do Not
  Disturb switch sharing the ringer's body and press, and the deeper
  press itself were all written after 1.0.62 was cut, so the APK carrying
  that version predates every one of them. They ship here for the first
  time.
- **The compact panel's entrance really was broken.** It was supposed to
  come out from behind the *display's* edge, travelling the gap the user's
  own offset leaves between the panel and the side of the screen. It did
  that by translating its own graphics layer -- inside a window sized by
  WRAP_CONTENT to exactly the panel's own bounds. A layer pushed past the
  edge of its window is a layer the compositor throws away, which is the
  very rule this codebase already follows for the centred mixer, so every
  pixel of that journey happened where nobody could see it. The bigger the
  offset, the longer the journey and the more of it was discarded: at any
  real offset the panel simply appeared where it belonged.

  On top of that it wiped itself open through a rounded window clipped to
  its own bounds -- a wipe that starts at the panel's own edge by
  construction, which is exactly the thing it was not supposed to look
  like.

  The travel is now the *window's*, read off the same arrival spring and
  pushed the same way the centred mixer's already was, and the local wipe
  is gone with it. The panel genuinely starts off the side of the display,
  and the display is what uncovers it -- the only edge that was ever meant
  to.

## 2026-09-20 — 1.0.62

- The compact panel's entrance is measured from the *display's* edge now,
  plus whatever gap the user's offset leaves between the two, so an offset
  popup slides out from behind the side of the screen rather than from a
  line in mid-air a few dp off its own edge. The gap is read off the
  clamped, cutout-adjusted position the window really ended up at, so it is
  the offset as worn rather than as asked for.
- The centred mixer actually travels to the centre. Its morph always
  carries the full translation, and which of the two carries it is decided
  by geometry rather than by mode: the layer does it exactly when the
  rectangle the morph starts at still fits inside the window it is drawn
  in, and the window does it off the very same morph value when it doesn't
  -- one number for both. That was the last teleport; the travel used to be
  dropped outright for a centred mixer, because a layer translated out
  there is a layer the compositor cuts off.
- The ringer switch gives further under a finger, to 0.89 of its own size,
  and the Do Not Disturb switch beside it is the same object now: the same
  press depth, the same glyph size, and one glyph with a bar put on and
  taken off rather than two pictures swapping.
- **One gesture for every level control.** The slider bars, compact and
  expanded, and the disc's ring and tick wheel now share a single
  implementation, and it is magnetic:
  - Under a finger the fill *is* the finger -- snapped to the touch
    continuously, with nothing animating between the two and no step
    quantisation on the way. The level reported to the system is still the
    step that fraction falls on, so the audio follows in notches while the
    bar stays under the thumb.
  - Let go, and the finger's own speed at the moment it lifted becomes the
    starting velocity of a spring aimed at the nearest step. A flick keeps
    travelling and then clicks onto a notch instead of stopping dead
    wherever the touch happened to end, and what it lands on is a real
    level rather than an arbitrary fraction. Released exactly on a step
    with nothing thrown, nothing runs at all.
  - Touched again mid-flight, the settle is caught where it is and the new
    drag continues from that exact fraction, so grabbing a moving bar never
    makes it jump to meet the finger.
  There is no seam between the three because there is no handover: the
  finger and the spring drive the same value, so the spring departs from
  the position and the speed the drag left behind. A volume key or another
  app arriving mid-settle retargets that same spring rather than restarting
  it.
- The mixer's stream sliders pick the *nearest* step rather than truncating
  toward zero, which is what the magnet needs to pull evenly in both
  directions.
- No new springs anywhere in this: the bars settle on the slider tier and
  the disc on the detent tier, both named where they already were.

## 2026-09-20 — 1.0.61

- Every spring, duration and easing in the overlay now lives in one file,
  `ui/theme/MotionTokens.kt`, as a table each animation declares itself
  against by name: what is moving, what it physically is, the token it
  moves on, and the exact property that token drives. Nothing outside that
  file names a spring any more, so a property that isn't in the table
  can't be animated without adding the row that justifies it first. The
  rule and the table are written down in `CLAUDE.md`.
- Two channels rather than one. A spatial spring overshoots -- that is
  what makes an object read as having mass -- but an overshoot past 1 on
  alpha is clamped by the compositor, so the panel used to reach full
  opacity, sit there for the length of the overshoot, and then ease back
  off it. Alpha and colour are now on their own critically damped channel,
  launched on the same frame as the movement and part of the same
  transition, and the exit waits for both before the window goes.
- With that split each panel can enter as the thing it actually is. A
  panel at a screen edge slides out of that edge and does nothing else --
  it no longer grows on the way in, which read as being created rather
  than uncovered, and which scaled the glass pane with it. A centered
  panel has no edge to come from, so it expands where it is. A
  laterally-anchored disc does neither: it forms by turning, and the hand
  is the only thing on the dial that fades in, written onto the face on a
  later slice of the same arrival rather than by a second animation.
- The centered mixer had two readings of where it was: the window was
  centered by a plain layout update while the composition went on building
  its entrance from the anchor, which still said "left edge". So it played
  a lateral reveal it had no edge for and morphed out toward a rectangle
  the centered window couldn't show, leaving a slice sliding in and then
  the mixer simply being there. One `PanelPlacement` state now feeds both,
  so there is nothing left for them to disagree about -- and at the center
  the morph keeps its size half and drops the travel.
- "Is the panel on screen at all" no longer resets when the panel changes
  shape, so a placement change on a visible panel can't replay an entrance
  it has already finished.
- One spatial animation per control. The ringer switch had two scales on
  the same object on separate clocks -- the container's press and knock,
  and a second one written into the glyph swap, running against the
  finger. The glyph is a child of the layer the press already drives, so
  the swap is now just a crossfade. The press also bottomed out deep
  enough to read as deforming rather than as taking a finger; it is
  shallower now, and so is the knock, whose character comes from the
  spring crossing back out past rest rather than from how far in it went.
- The disc's ticks are a detent rather than a picture of one: every source
  -- a finger, a volume key, another app -- settles the same value on the
  same spring, so a key landing mid-settle bends that settle from where it
  is at the speed it is carrying. Crossing a slot clicks only while the
  knob is actually being turned (a finger on it, or the throw that finger
  let go of still running); a level moved by something nobody is holding
  moves silently.
- Ambient motion, for the two backgrounds that were still pictures once
  they had arrived. The glass light creeps about nine degrees a second,
  one whole turn per lap so the seam has nothing to show -- and it moves
  the light only: the pane underneath never turns and never scales, which
  is the whole difference between glass and paper. Atmosphere turns on its
  own axis and wanders its centre round a small orbit on top of the
  arrival turn it already had. The atmosphere values are shader uniforms
  read in the caller's own draw phase, so a panel of it costs three float
  reads a frame and recomposes nothing; the glass beam is built at
  composition and is quantised to two degrees instead, which is invisible
  on a soft gradient and is the difference between rebuilding two shaders
  sixty times a second and rebuilding them five. Neither runs behind a
  panel that isn't showing it, and every lap is off under reduced motion
  and cancelled with the composition that launched it.

## 2026-09-20 — 1.0.60

Rebuilt the overlay's motion again from the 1.0.56 code, discarding the two
passes in between. Every animated surface in the popup was reset to that
release's state first, so what follows is the whole of the overlay's motion
rather than a layer on top of an earlier attempt.

- One spring vocabulary, shared by everything that moves, and no
  fixed-duration curve left anywhere in the overlay: a fast tier for
  anything a finger steers directly, a heavier one for whole surfaces, a
  softer one for elements that follow rather than lead, and a critically
  damped one for colour. A spring can be retargeted from where it is at the
  speed it is already carrying, which is the only way a second gesture
  landing mid-settle bends the motion instead of restarting it -- and the
  only way a jump between two easings can be ruled out rather than tuned
  around. All of it collapses to an instant snap under the platform's
  "Remove animations" setting; the crossfades, which carry no travel, are
  left alone.
- The window itself no longer fades. It is added invisible, revealed the
  moment it has been laid out and positioned, and from then on the
  composition owns every frame of arriving, morphing and leaving -- one
  clock instead of a window interpolator and a composition curve stacked on
  each other, which is what used to make the popup read as coming up and
  going away in two steps. Asking for the popup again mid-exit bends it
  back from wherever it had got to.
- The compact panel is *revealed* out of the screen edge it is anchored to
  rather than sliding in whole: a rounded window opens from that edge, and
  its corners morph from a full pill to the panel's own radius exactly as
  it finishes opening, with a short push along the same axis so the reveal
  and the travel are one motion. The exit is the same number running back.
- The mixer morphs out of the compact panel for real. The panel's screen
  rectangle is captured the moment the expand is asked for, the mixer's own
  is measured once its window has been repositioned, and the mixer's layer
  starts at exactly the size and place the compact panel occupied -- so
  opening it is one surface changing shape rather than a second one
  appearing. Dismissing from the mixer runs it in the order it was built:
  the mixer folds back into the compact panel's rectangle, and only then
  does that rectangle close back into the screen edge.
- Every slider carries the finger's own release velocity into the spring it
  settles on, converted into the units the fill actually animates in, and
  still tracks the touch exactly 1:1 while one is down.
- The disc arrives by turning: the painted ring -- track, arc and ticks --
  unwinds counterclockwise into place as a graphics-layer rotation, while
  the switch and the reading at its centre stay upright throughout.
- The disc's knob and its volume ring now move as one. The tick taper reads
  the level as the real number it is instead of rounding it to the nearest
  tick, so it slides along the ring with the fill rather than hopping after
  it, and a released drag no longer stops dead before the settle picks it
  up.
- The volume glyph animates its own parts on a speaker that never moves: a
  wave grows out of the cone as the level crosses into its band and
  retracts when it falls out again, and the mute bar draws itself across
  where the waves were at zero, then un-draws the same way. They assemble as
  the popup arrives and fold away as it leaves, on the popup's own spring.
  Bluetooth is the one glyph that still swaps, because it isn't a level.
- Glass: the reflection sweeps once across the face as the panel comes in
  and back out the way it came as it leaves -- one shimmer shared by the
  face and the rim, ending when the panel does, costing nothing at rest.
- Atmosphere: the grain is now alive. It resamples into a whole new field
  several times a second and dissolves between consecutive fields, which is
  what film grain actually does -- two extra hashes per pixel, not a second
  layer. The field's turn is phased off the popup's own arrival, and both
  where it settles and how far its centre sits off the panel's are drawn
  fresh every time the popup appears.
- The ringer switch is one short pop per change -- low damping, high
  stiffness, in and back out past its own size -- and the glyph reacts with
  it: ringing and silent are the same speaker with its waves retracting and
  its mute bar drawing on, and vibrate shakes sideways on a spring loose
  enough to cross back and forth several times.
- Buttons answer the finger itself, separately from whatever the press
  changes, on the same spring a dragged slider settles on -- so pressing a
  control and swiping one feel like the same surface. The toggle buttons'
  glyph swap moved onto those shared springs too.

## 2026-09-20 — 1.0.59

- Second pass over the motion rebuilt in 1.0.58, still on the one shared
  spring family and still with no fixed durations anywhere in the overlay.
  Every part of an appearance is now phased off the *same* spring rather
  than merely started at the same moment: the arrival is handed down the
  composition, and anything that wants to time itself against it reads that
  value instead of running an animation of its own. The practical effect is
  that every exit is its own entrance played backwards, for free, instead
  of a second curve that has to be kept in agreement with the first by
  hand.
- A bar popup now enters and leaves along the direction the *volume* went:
  up when the user is turning it up, down when they're turning it down, so
  the key and the thing it moves agree about which way is more. Summoned by
  the accessibility button instead, with no key behind it, it falls back to
  travelling in from the screen edge it's anchored to.
- The mixer growing out of a popup that sits on the screen's midline has no
  edge to come out of, so it turns over into place instead -- a shallow
  flip about the panel's vertical axis, with the camera set far enough back
  that the near edge doesn't balloon on the way round. Anchored to a side,
  it still grows out of that side as before.
- The disc now arrives the way a radar face does: a sweep travels once
  counterclockwise around the ring and the index lights up just behind the
  line rather than exactly on it. The face, its track and its arc don't
  fade -- only the ticks and the mark riding the fill's leading edge are
  revealed, which is what makes it read as a face coming alive rather than
  as a picture fading in.
- The volume glyph now reads the level rather than labelling it: crossing a
  third of the range adds a wave to the speaker, crossing two thirds adds
  the second, zero puts the bar across it, and each one springs in as it
  arrives instead of the icon being exchanged between frames. Bluetooth
  still takes precedence over all of them when a sink is connected.
- Glass: the reflection's slow continuous drift is gone. The light now
  swings into place as the panel arrives and swings back out the other way
  as it leaves -- one short, physical turn shared by the face and the rim,
  ending when the panel does. Nothing turns while the popup just sits
  there, which also means the effect costs nothing while idle.
- Atmosphere: three soft patches of the second colour are now pooled into
  the field, placed randomly each time the popup appears and drifting past
  each other on their own phases while it's up, so the background is never
  quite the same painting twice. They're a falloff inside the existing
  shader rather than a second layer or a blur pass, and the field's own
  turn and lap are unchanged.
- The ringer switch's character now comes from the recovery spring rather
  than from a different animation per mode: vibrate is left loose enough to
  cross back and forth several times, which is a shake; silent is damped
  nearly flat, which is something stopping; ringing sits between them and
  reads as one confident knock. The glyph rides the same impulse in size as
  the button does, so the pop is the spring crossing its resting value
  rather than a pose written down somewhere.
- Buttons now answer the finger itself, separately from whatever the press
  changes: they give as it lands and let go as it lifts, on the same spring
  a dragged slider settles on -- so pressing a control and swiping one feel
  like the same surface. The toggle buttons' own glyph swap moved onto
  those shared springs too.

## 2026-09-20 — 1.0.58

- Rebuilt the overlay's motion from the 1.0.56 code, dropping the previous
  release's pass entirely along with the haptics that came with it (the
  `VIBRATE` permission is gone again with them). What replaces it is one
  spring family shared by everything that moves: a fast tier for anything a
  finger is steering directly, a slower one for whole surfaces arriving,
  a softer one for elements that follow rather than lead, and a critically
  damped one for colour. Nothing in the popup travels on a fixed duration
  any more.
- The popup and the mixer it grows into now arrive on a single spring that
  owns every part of the appearance at once. The window itself no longer
  fades underneath on an interpolator and a length of its own -- two
  stacked curves on one arrival was what made the popup look like it came
  up in two steps. Coming back while it's still dismissing now bends the
  motion around from wherever it had got to, at the speed it was already
  carrying, rather than restarting it.
- A bar popup enters and leaves along the screen edge it's anchored to,
  growing out of that same edge; a side-anchored one travels sideways, a
  top or bottom one up or down, and a centred one simply grows.
- The disc forms by turning counterclockwise into place about its own
  centre, and doesn't fade as a whole while it does: the fade belongs to
  the index layer -- the ticks and the mark riding the fill's leading edge
  -- so the face turns in rather than materialising.
- Every slider now carries the finger's own release velocity into the
  spring it settles on, so a flick keeps travelling instead of stopping
  dead where the finger left, and still tracks the touch exactly 1:1 while
  one is down. A volume key landing mid-settle retargets the same spring
  from the speed it's currently carrying instead of starting again -- the
  disc's tick ring especially, which is where restarting read worst.
- The mixer's own inner rows settle on the softer tier, so a panel of them
  reacting at once reads as one body moving rather than a dozen separate
  springs.
- The ringer switch takes one impulse per mode change and springs back from
  it, rather than running a scripted sequence of poses -- and every mode
  now moves both the button and the glyph on it, so the switch reacts as
  one object instead of looking like the icon was swapped out underneath.
- Glass: the reflection drifts slowly and continuously around the panel,
  face and rim together as one beam, while the panel itself stays put.
- Atmosphere: the field keeps turning on its own axis after it settles, and
  drifts around its centre on a slow, off-round lap -- the turn inside the
  shader, the lap as a pure graphics-layer transform.
- All of it honours the platform's "Remove animations" setting: the
  travelling springs collapse to an instant snap and the continuous loops
  don't start, while crossfades -- which carry no travel to object to --
  are left alone.

Further functional changes (new features, deeper customization options) will
be appended to this file as they land.
