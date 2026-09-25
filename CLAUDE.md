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
| `default` | a surface arriving, expanding or leaving | everything else that fades or tints |
| `defaultSoft` | elements that follow rather than lead | -- |

Plus `Ambient.enter`, which is neither: the light on the glass and the
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
- A fade as an element's *principal* entrance. Something has to move.
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

### One arrival, one spring

`LocalArrival` carries how far the overlay has arrived, 0 to 1, for
anything that phases its own motion off the arrival rather than starting a
second animation. For the disc that is the single spring that slides it out
of its edge (turning slightly as it comes) and takes it away at the end. A
bar arrives in two steps, the mixer's close run backwards: its **panel**
opens out of the screen edge -- a laid-out rectangle growing from nothing at
the edge to its own size -- and only then does its **content** come out onto
it; `LocalArrival` is that content's arrival. On the way out the content goes
first and the panel shuts into the edge after it.

The mixer opening is not an arrival -- the panel is already on screen,
changing shape. The compact popup's content goes at once, where it is (a
bar, a disc and its ticks alike; the disc's face gives way to the panel
behind it), and the panel opens in two phases, one per axis, chained so the
second starts before the first has come to rest (see `OverlayScene`): drawn
out along the axis the user swiped (`Spatial.turn`), then across it to the
mixer's full size (`Spatial.travel`). No rotation.

Closing is a **strict sequence** on `Spatial.leave`, each step starting only
once the one before it has visibly finished: the rows go, bottom first; then
the panel closes along its edge to the compact popup's own band; then it
shuts into the screen edge the popup came from -- never a fade. The order of
the two panel steps is the edge's, not the swipe's (`closingOrder`), so the
last thing a panel does is always go into its edge. With no edge it closes
to the popup's band and then shrinks into its middle.

The mixer's rows are the one deliberate exception to "one spring": each row
is its own object on its own `Spatial.cascade` spring, started
`MotionTokens.Cascade` later than the one above it (see `RowCascade.kt`). A
cascade *is* a sequence.

### One window, one panel

The overlay's window covers the whole screen and **never moves or
resizes**; everything happens inside it, and a touchable region
(`TouchableRegion` in `OverlayGeometry.kt`) lets every touch that misses the
panel through. Do not go back to a window the size of the panel: moving and
resizing a window under an animation is a round trip through the window
manager that lands a frame early or late, and that was every "snap" this
overlay ever had.

The compact popup and the mixer are **one panel** (`SharedPanel`, in
`OverlayScene.kt`): one
shadow, one face, one rim, whose laid-out rectangle travels from the compact
popup's to the mixer's (`OverlayStage.panelRect`). Their contents take turns
inside it. Every destination -- the compact popup's rectangle, the mixer's
(centred on the display, from the display's own size), the edge a closing
mixer shuts into -- is
computed before anything moves, in `OverlayGeometry.kt`. The mixer is as
wide as the platform makes a window that wraps its content
(`config_prefDialogWidth`) -- the width it always had.

### The glass

The glass is a pane over the **real screen**, blurred one of two ways.

While the platform's cross-window blur is on, it is the **system's own
blur**: each pane draws the window's background-blur drawable
(`ViewRootImpl.createBackgroundBlurDrawable`, through reflection;
`SystemGlassBlur.kt`), within its own rounded rectangle. The renderer
reports where that drawable's node lands every frame, the whole transform
from the window down included, so the blurred region travels with the panel
in the same frame -- no second window, and the one window still never moves.
Nothing is captured then. Never `FLAG_BLUR_BEHIND` (it blurs the whole
screen) or a window's own background blur (the window is the whole screen).

Battery saver switches the cross-window blur off -- the platform's rule, with
no way round it -- and then the glass is **the app's own blur** of a capture
(the service decides per popup: `systemBlurAvailable`, kept current by a
cross-window-blur listener, and `PowerManager.isPowerSaveMode`; switched off
under a popup already up, that popup's glass is its tint until the next
one). Before the overlay's window is added, the service captures the
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
it stays put on the screen while a panel travels or the disc turns over it
-- through a lens the user sets (`glassLensEnabled`, `glassLensZoom`: from a
percent smaller to a percent larger about the pane's middle; the system's
blur has none). The arrival waits for the first capture, briefly.

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
