# NoMixer

An Android volume-mixer overlay: an accessibility service puts a small
popup on screen (a bar, a vertical bar, a dot matrix or a disc), which
expands into a full per-app mixer.

## Motion

All overlay motion is governed by
`app/src/main/java/com/nomixer/volume/ui/theme/MotionTokens.kt`. That file
carries the binding table; this section is the rule, not a second copy of
it.

### The rule

**`MotionTokens.kt` is the only file in the app allowed to name a spring, a
duration or an easing.** Everywhere else asks for a token by name. In
practice that means this, run from the repo root, returns nothing outside
`MotionTokens.kt`:

```sh
grep -rn 'spring(\|tween(\|durationMillis\|easing *=\|Easing' app/src/main \
  --include='*.kt' | grep -v ui/theme/MotionTokens.kt
```

(The settings screen -- `CustomizationScreen.kt` -- is the one place still
holding raw `tween` call sites. It is a full-screen navigation between two
pages rather than overlay motion, its durations come from
`MotionTokens.Screen`, and it is out of scope for the overlay's own rule.)

### Declaring an animation

Every animation in the overlay declares four things in a comment at its own
call site, and repeats them in its composable's KDoc:

```
// element:  what is moving, named as an object
// model:    what that object physically is (a sheet, a knob, a detent)
// token:    the MotionTokens member it moves on
// property: the exact property it drives
```

**A property that isn't in `MotionTokens`' table doesn't get animated.** If
a change needs one, add the row first, with the model that justifies it,
and only then write the animation. Stop and ask rather than inventing a
spring at the call site.

### Tiers and channels

Three tiers, two channels, and nothing else:

| | `Spatial` (position, scale, rotation) | `Effects` (alpha, colour) |
|---|---|---|
| `fast` | press, tick, thumb -- anything a finger steers | a fade keeping up with a finger |
| `default` | a mark drawing itself (the mute bar) | everything else that fades or tints |
| `defaultSoft` | elements that follow rather than lead | -- |

Plus the dissolves (`Effects.dissolveIn`, `dissolveOut`, `dissolve`: how a
surface comes and goes -- see **One arrival**), and `Ambient.enter`, which is neither: the light on the glass and the
Atmosphere field, settling on a surface that has already arrived. It is the
one tier slower than the panel, and the one thing that does not ride the
arrival -- see **Ambient** below.

`Spatial` may overshoot; a real object carries momentum past its mark.
`Effects` is critically damped, always: an alpha that overshoots is a
flash, and a colour that overshoots is a frame of a colour nobody chose.
**A spatial spring never goes on alpha.**

### Forbidden

- A spring invented at a call site, or one spring per file.
- `scale` animated from (or to) `0`. Scale around the element's own size.
- Anything *moving* as the popup arrives, leaves or hands over to the mixer: no
  slide, no unfolding panel, no scale, no turn (the disc included). The
  entrance is a dissolve, and it was asked for -- see **One arrival** below.
- `tween` anywhere in the overlay, and any loop at all. Atmosphere and the
  glass beam run once on `Ambient.enter` and freeze; everything else is a
  phase of the arrival.
- Rotating or scaling the glass pane. Glass is a still sheet; only the
  light on it moves. (One exception, asked for explicitly: the disc turns
  slightly as a whole on its way in and out, knob face included -- it is
  one object and moves as one.)
- Animating the Atmosphere *container*. Only the field inside it turns.

### Reduced motion

`MotionTokens.reducedMotion` reads the platform's "Remove animations"
switch, per call. Travelling tokens collapse to `snap()`;
`press`/`tick`/`knock`/`shake` stay springs but lose their overshoot and
tail, because a control that answers a finger with nothing reads as broken
rather than as calm.

Anything phased off the arrival needs no check of its own: the arrival
itself snaps. `Ambient.enter` snaps for the same reason, which lands the
glass beam and the Atmosphere field on their settled values with nothing
having travelled.

### Ambient

There are no loops. The glass beam and the Atmosphere field once turned
forever on fixed-length laps; they run **once** now, on `Ambient.enter`,
and freeze where they land.

They are the one thing in the overlay that does not ride `LocalArrival`,
and deliberately so: they are slower than the panel. A light settling on a
sheet is not the sheet arriving a second time -- it is what happens to the
sheet once it is there. Phased off the arrival both effects were over in
the few dozen milliseconds a panel takes to slide out of an edge,
underneath the much larger motion doing the sliding, and neither was ever
visible. They read `LocalAmbientEnter` instead, which runs on its own
spring an order of magnitude softer than `Spatial.default` and outlasts the
panel's own entrance.

Enter-only, and with no exit: by the time the popup leaves, the panel is
fading, and a light retreating under a fading panel is motion nobody asked
to see.

- **Glass** (`GlassScrim.kt`): a flare of brightness as the panel shows up,
  the lit band settling round to the angle the user chose while the panel
  simply sits there, and then nothing. The pane never turns and never
  scales; only the light on it moves.
- **Atmosphere** (`AtmosphereScrim.kt`): the field turns, its centre swings
  along a short arc, and the grain dissolves through a dozen or so whole
  fields -- once, then still. Never the container: only the field inside it.

An ambient value read in the **draw phase** -- through a `() -> Float`,
the way `AtmosphereMotion` hands out all of its -- costs a float read and a
shader uniform per frame and recomposes nothing. A value read in the
**composition phase** costs a recomposition per distinct value, so quantise
it to what the eye actually resolves (see `SHEEN_STEP_DEGREES` in
`GlassScrim.kt`, which the glass beam needs because its brushes are built
in composition). Prefer the draw phase.

### One arrival: a dissolve

Nothing in the overlay moves as it arrives, leaves or hands over. A popup
-- a bar or the disc, the mixer -- is there to the degree its own progress
says, 0 to 1 (`OverlayStage.compactShown`, `mixerShown`), and that is the
whole of the motion. No panel slides out of an edge or unfolds from one
rectangle to the other, no row cascades, nothing scales or turns. Each
surface sits at the rectangle `OverlayGeometry.kt` works out for it
before anything shows. Only the light on the glass (`Ambient.enter`) still
travels, as it always did.

The dissolve is **stochastic** (`StochasticFade.kt`): the content -- the
slider and button, the mixer's rows -- comes and goes as a scatter of
pixels, each switching on at a moment of its own, through an AGSL threshold
on the layer. It is only applied while the progress is strictly between 0
and 1; at rest the layer is drawn as it would be without it. The **panel**
behind it is a plain alpha on the same progress, and the system's blur
inside it is handed its opacity through `LocalGlassPresence`
(`Drawable.setAlpha` on the blur drawable) -- that blur is the compositor's,
not pixels to take a threshold to.

Three tokens, all critically damped: `Effects.dissolveIn` (quick: a popup is
asked for with a key), `Effects.dissolveOut` (slower: what leaves is let go
of), and `Effects.dissolve` for the compact popup handing over to the mixer
-- the popup's progress down and the mixer's up, together, on one spring, so
they take exactly as long. Leaving from the mixer dissolves the mixer out;
there is no closing sequence any more. `LocalArrival` carries the progress
of the surface a reader is on, for the one mark that draws itself as a
surface comes in: the mute bar.

### One window, one panel

The overlay's window covers the whole screen and **never moves or
resizes**; everything happens inside it, and a touchable region
(`TouchableRegion` in `OverlayGeometry.kt`) lets every touch that misses the
panel through. Do not go back to a window the size of the panel: moving and
resizing a window under an animation is a round trip through the window
manager that lands a frame early or late, and that was every "snap" this
overlay ever had.

The compact popup and the mixer are **two panels**, each at its own
rectangle for good (`SharedPanel`, in `OverlayScene.kt`: shadow, face, rim)
-- the compact popup's, and the mixer's, centred on the display, from the
display's own size -- and each dissolves with what is on it. Every
destination is computed in `OverlayGeometry.kt`. The mixer is as wide as the
platform makes a window that wraps its content (`config_prefDialogWidth`) --
the width it always had.

### The glass

The glass is a pane over the **real screen**, blurred one of two ways.

While the platform's cross-window blur is on, it is the **system's own
blur**: each pane draws the window's background-blur drawable
(`ViewRootImpl.createBackgroundBlurDrawable`, through reflection;
`SystemGlassBlur.kt`), within its own rounded rectangle. The renderer
reports where that drawable's node lands every frame, the whole transform
from the window down included, so the blurred region stays with the panel
in the same frame -- no second window, and the one window still never moves.
Nothing is captured then. Never `FLAG_BLUR_BEHIND` (it blurs the whole
screen) or a window's own background blur (the window is the whole screen).

Battery saver switches the cross-window blur off -- the platform's rule, with
no way round it -- and then the glass is **the app's own blur** of a capture
(the service decides per popup: `systemBlurAvailable`, kept current by a
cross-window-blur listener, and `PowerManager.isPowerSaveMode`; switched off
under a popup already up, the glass goes over to the app's own blur from
captures of the app window alone, `fallBackToWindowCaptures`). An app in
picture-in-picture takes the system's blur away too, without the platform
saying so: with one on screen (`pictureInPictureOnScreen`, checked per popup
and on every windows-changed event) the glass is the app's own blur. Window
captures are laid on clipped round every window above the app's (the
picture-in-picture window, the status bar, a keyboard), which keep what the
first capture had. Before the overlay's window is added, the service captures the
whole screen through its own accessibility screenshot capability
(`canTakeScreenshot`; `requestGlassBackdrop` in `Service.kt`); while the
popup is up it captures just the app window behind it every
`GLASS_REFRESH_MS` (`takeScreenshotOfWindow`, Android 14+;
`refreshGlassBackdrop`) -- never the display again, which would have the popup
in it. `GlassBackdropCompositor` (`GlassBackdrop.kt`) shrinks each capture
off the main thread, lays the window onto the first capture, drops one that
hasn't changed, and box-blurs the small result. A capture is never read
back whole: its first halving is drawn on the GPU straight into a buffer the
capture thread reads (`GlassShrinker`) -- copying a hardware bitmap into a
software one happens on the app's one render thread, and at full size it
stalled the overlay's own frames three times a second. Between two looks it also
estimates how far the app scrolled (`estimateShift`, on the small
luminance), how fast, and how fast that speed is dying away (a fling
coasting); the glass slides the captured screen along it on its own spring
(`Spatial.follow`), each look aimed through `Spatial.aimFollow` at where the
content will be -- and how fast it will be going -- when the next look
lands, so a scroll shows through as it happens instead of a look behind. A
look that finds the content stopped is settled on instead
(`Spatial.followSettle`), carrying the glide's own speed, so a glide that
ran past the stop slows and eases back rather than turning in a frame; the
compositor says so once even though the picture hasn't changed. What the
scroll can't explain -- a video, a list changing in place -- eases from the
last backdrop to the new one (`Effects.follow`). Every new popup starts from a
fresh capture: the last one is dropped before asking, so the glass never
flashes the screen as it was the time before. A capture needn't come
back at the screen's own size, nor as just the window: place windows against
the screen's bounds (`GlassBackdropCompositor`'s
`screenWidth`/`screenHeight`), never against the capture's pixels, and cut
away the transparent margin some devices hand a window back with
(`windowContent`) -- stretched over the window's bounds, it shrank the app
towards the middle of the screen, with the first capture showing round it
(`GlassBackdropCompositorTest`). Every pane of glass draws it behind its
tint, lined up with the screen through the whole root-to-node transform, so
it stays put on the screen
-- flat, at the screen's own scale: no lens, which was tried and taken out.
The arrival waits for the first capture, briefly.

The system reads a service's capabilities only when it binds it, and an app
update does not rebind: a service first switched on by a version without
`canTakeScreenshot` keeps running without it. The main screen then offers
**Restart accessibility service** (`restartAccessibilityService` in
`MainActivity.kt`), and the system's own accessibility settings have a
"Use NoMixer" switch for it. Keep that switch: do not request the
accessibility button (`flagRequestAccessibilityButton`) -- since Android 12
a service that does gets only a shortcut on its settings page, and can
never be switched off and on by hand. And keep
`res/xml/accessibility_service_config.xml` the service's only declaration:
a leftover copy under `res/xml-v31` once replaced it on every Android 12+
device, and nothing changed in the real one ever reached a phone.

Where there is no capture -- the settings preview, or a platform that
refuses one (the reason goes to the diagnostic log) -- the glass is its tint
and its light alone. There is no grain: it stood in for a blurred screen
before there was one. Never put a real blur (`RenderEffect`) over the glass
layer: there is nothing under the pane inside our own window to blur.

## Watching the motion

`OverlayFramesTest` films the overlay -- every frame of each popup style
arriving, opening into the mixer and closing, over a captured backdrop that
changes halfway through the opening -- through Robolectric's native graphics on a paused clock, as PNGs. It is
skipped unless `FRAMES_OUT` is set:

```sh
FRAMES_OUT=/tmp/frames ./gradlew :app:testDebugUnitTest --tests '*OverlayFramesTest*'
python3 tools/contact_sheet.py /tmp/frames/vbar 3close 1 12 0.3 sheet.png
```

Frames are named `<phase>_<milliseconds>.png` (`1enter`, `2open`,
`3close`). Where Maven Central rate-limits, point Robolectric's own download
of its Android jars at a mirror with `ROBOLECTRIC_REPO_URL`. Look at the
frames before shipping a change to the motion.

## Building

The Android Gradle Plugin comes from `dl.google.com`, which some sandboxes
block; `./gradlew` then fails at plugin resolution before compiling
anything. That is a network policy, not a broken checkout.
