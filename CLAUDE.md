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
  light on it moves.
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

`LocalArrival` carries how far the overlay has arrived, 0 to 1, from the
single spring that brings the compact popup out of its edge and takes the
whole overlay away at the end (`Service.kt`). Anything that phases its own
motion off the arrival reads it from there rather than starting a second
animation. That keeps every part of the arrival on one curve, and makes
every exit the entrance backwards for free.

The mixer opening is not an arrival -- the panel is already on screen,
changing shape -- and it has its own two phases, chained so the second
starts before the first has come to rest (see `OverlayContent` in
`Service.kt`): the panel travels to the media row (`Spatial.turn`), then
opens into the mixer (`Spatial.travel`). The mixer's rows are the one
deliberate exception to "one spring": each row is its own object on its own
`Spatial.cascade` spring, started `MotionTokens.Cascade` later than the one
above it (see `RowCascade.kt`). A cascade *is* a sequence.

### One window, one panel

The overlay's window covers the whole screen and **never moves or
resizes**; everything happens inside it, and a touchable region
(`TouchableRegion` in `OverlayGeometry.kt`) lets every touch that misses the
panel through. Do not go back to a window the size of the panel: moving and
resizing a window under an animation is a round trip through the window
manager that lands a frame early or late, and that was every "snap" this
overlay ever had.

The compact popup and the mixer are **one panel** (`SharedPanel`): one
shadow, one face, one rim, whose laid-out rectangle travels from the compact
popup's to the mixer's (`OverlayStage.panelRect`). Their contents take turns
inside it. Every destination -- the compact popup's rectangle, the mixer's
(centred on the display, from the display's own size), the media row's -- is
computed before anything moves, in `OverlayGeometry.kt`.

## Building

The Android Gradle Plugin comes from `dl.google.com`, which some sandboxes
block; `./gradlew` then fails at plugin resolution before compiling
anything. That is a network policy, not a broken checkout.
