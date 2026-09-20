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

`Spatial` may overshoot; a real object carries momentum past its mark.
`Effects` is critically damped, always: an alpha that overshoots is a
flash, and a colour that overshoots is a frame of a colour nobody chose.
**A spatial spring never goes on alpha.**

### Forbidden

- A spring invented at a call site, or one spring per file.
- `scale` animated from (or to) `0`. Scale around the element's own size.
- A fade as an element's *principal* entrance. Something has to move.
- `tween` anywhere in the overlay, and any loop at all. The overlay has one
  clock: the arrival. Atmosphere and the glass beam are phases of it.
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
itself snaps, which lands the glass beam and the Atmosphere field on their
settled values with nothing having travelled.

### Ambient

There are no loops. The glass beam and the Atmosphere field once turned
forever on fixed-length laps; both are phases of the arrival now
(`LocalArrival`), which is the difference between a panel that has settled
and one that never quite finishes arriving -- and it is what stops a shader
running behind an overlay nobody is looking at.

- **Glass** (`GlassScrim.kt`): one flare of brightness as the panel starts
  arriving, the lit band sweeping round to the angle the user chose as it
  lands, and then nothing. The pane never turns and never scales; only the
  light on it moves.
- **Atmosphere** (`AtmosphereScrim.kt`): the field turns, its centre swings
  along a short arc, and the grain dissolves through a dozen or so whole
  fields -- all of it inside the arrival, all of it frozen from the frame
  the panel lands. Never the container: only the field inside it.

An arrival-phased value read in the **draw phase** -- through a `() -> Float`,
the way `AtmosphereMotion` hands out all of its -- costs a float read and a
shader uniform per frame and recomposes nothing. A value read in the
**composition phase** costs a recomposition per distinct value, so quantise
it to what the eye actually resolves (see `SHEEN_STEP_DEGREES` in
`GlassScrim.kt`, which the glass beam needs because its brushes are built
in composition). Prefer the draw phase.

### One arrival, one spring

`LocalArrival` carries how far the overlay has arrived, 0 to 1, from the
single spring that owns its whole appearance (`Service.kt`). Anything that
phases its own motion off the arrival -- the disc's formation turn, the
glass beam's entering sweep, Atmosphere's entering rotation -- reads it
from there rather than starting a second animation. That keeps every part
of the arrival on one curve, and makes every exit the entrance backwards
for free.

`PanelPlacement` (`Service.kt`) is the same idea for position: one state,
read by both the window's layout and the composition's motion, so the
panel can never be laid out in one place and animated toward another.

## Building

The Android Gradle Plugin comes from `dl.google.com`, which some sandboxes
block; `./gradlew` then fails at plugin resolution before compiling
anything. That is a network policy, not a broken checkout.
