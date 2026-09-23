package com.nomixer.volume.compose

import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_DEFAULT
import com.nomixer.volume.data.ATMOSPHERE_GRAIN_SIZE_DEFAULT
import com.nomixer.volume.ui.theme.LocalAmbientEnter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Atmosphere background: a Nothing-OS-flavoured alternative to
 * [GlassBackground]'s lit glass and a flat Solid fill -- the look Nothing's
 * launcher gives a wallpaper, made from generated noise instead of a photo.
 *
 * Built in the order the eye reads it, and that order is the point:
 *
 * 1. a flat fill, one soft gradient between the two colours;
 * 2. large soft blobs of colour painted *into* that fill -- where each one
 *    starts and which way it drifts is a fresh draw of the dice every time
 *    the popup opens;
 * 3. grain over the result, fill and blobs together.
 *
 * The blobs used to be laid over the grain afterwards, which is why they read
 * as stickers on the surface rather than as part of it. Now the grain comes
 * last and dithers everything below it at once, including the edges of the
 * blobs, which break up into grain instead of ending in a clean line.
 *
 * The field turns through [ATMOSPHERE_TURN_RADIANS] and the blobs travel their
 * paths as the popup appears, and then it holds perfectly still -- see
 * [rememberAtmosphereMotion].
 *
 * Its two colors are handed in by the caller ([colors]) rather than sampled by this file at all -- see
 * [com.nomixer.volume.Service.sampleForegroundAppColors] for where they
 * actually come from: the launcher icon of whatever app is underneath the
 * popup, read via [android.content.pm.PackageManager], run through
 * [androidx.palette.graphics.Palette]. That's the closest thing to "the
 * colors behind the slider" reachable on every device: real per-pixel
 * sampling needs the screenshot capability the platform refuses outright on
 * some of them (see NOTICE.md's 1.0.45 entry), and the wallpaper's own
 * colors (tried first, in 1.0.46's initial pass) only describe the home
 * screen, not whatever app actually happens to be open. `null` -- no
 * foreground app resolved, no icon, no swatches -- falls back to the panel's
 * own tint and nothing else changes.
 *
 * No real blur or separate graphics layer needed here, unlike
 * [GlassBackground]: there's nothing to keep out of the panel's own content,
 * only a colored noise field painted straight into whatever shape is asked
 * for, so both the flat panels and [VolumeDisc]'s own knob face can just
 * draw it directly.
 */
private const val ATMOSPHERE_SHADER_SRC = """
    uniform float2 resolution;
    uniform float rotation;
    uniform float3 colorA;
    uniform float3 colorB;
    uniform float grainIntensity;
    uniform float grainScale;
    uniform float grainPhase;
    uniform float2 drift;
    uniform float seed;
    uniform float travel;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    // One complete grain field, identified by a seed. Two hashes rather
    // than one so a cell's value isn't a straight function of its position,
    // which reads as a pattern rather than as grain.
    float grainField(float2 cell, float fieldSeed) {
        return hash(cell + fieldSeed * 37.0) * 0.55 +
            hash(cell * 1.37 + 19.7 - fieldSeed * 11.0) * 0.45;
    }

    half4 main(float2 fragCoord) {
        float2 center = resolution * 0.5;
        float longest = max(resolution.x, resolution.y);

        // drift moves the whole field's own center off the panel's, by a
        // fraction of its longest side -- picked afresh every time the popup
        // appears, so the same panel is never quite the same painting twice.
        float2 p = fragCoord - center - drift * longest;

        // One rigid turn of the whole field about that center: every sample
        // below reads off these rotated coordinates, so the grain, the sweep
        // and the blobs travel together instead of each wandering off.
        float s = sin(rotation);
        float c = cos(rotation);
        float2 turned = float2(p.x * c - p.y * s, p.x * s + p.y * c);

        // The grain, worked out first because everything below is made of
        // it. Chunky cells rather than per pixel, so it reads as grain at a
        // glance rather than as sensor noise; dissolving between whole
        // fields on the way in, then still. The seed is part of every
        // field, so the grain it settles on differs from one opening to
        // the next.
        float2 cell = floor(turned * grainScale);
        float settled = floor(grainPhase);
        float grain = mix(
            grainField(cell, settled + seed * 97.0),
            grainField(cell, settled + 1.0 + seed * 97.0),
            grainPhase - settled
        );
        float dither = (grain - 0.5) * grainIntensity;

        // 1. The flat fill: one soft gradient from one colour to the other,
        //    across whichever way the field has turned. Not wound round a
        //    centre any more -- a pinwheel has a point in the middle where
        //    every colour meets, and it read as a hole in the painting.
        float band = clamp(0.5 + turned.x / longest * 1.1, 0.0, 1.0);
        band = band * band * (3.0 - 2.0 * band);
        float3 color = mix(colorA, colorB, clamp(band + dither * 0.5, 0.0, 1.0));

        // 2. The blobs, painted *into* that fill: large soft pools of
        //    colour, each thrown somewhere new every time the panel opens
        //    and carried along a path of its own while the panel settles.
        //    Their weight is dithered by the same grain as everything else,
        //    so their edges break up into grain rather than sitting on top
        //    of it as a smooth decal.
        float2 q = turned / longest;
        for (int i = 0; i < 6; i++) {
            float fi = float(i);
            float2 home = float2(hash(float2(seed * 3.1, fi * 1.7 + 0.3)),
                                 hash(float2(fi * 2.3 + 0.9, seed * 5.7))) - 0.5;
            float heading = hash(float2(seed + fi * 0.61, 5.3)) * 6.2831853;
            float reach = 0.30 + 0.35 * hash(float2(fi + 1.3, seed * 9.1));
            float2 at = home * 1.2 + float2(cos(heading), sin(heading)) * reach * travel;
            float size = 0.20 + 0.28 * hash(float2(seed * 1.3 + fi, 2.9));
            float d = length(q - at) / size;
            float weight = exp(-d * d * 2.0) * (0.55 + 0.40 * hash(float2(fi + 4.4, seed)));
            weight = clamp(weight + dither * 1.3 * smoothstep(0.0, 0.3, weight), 0.0, 1.0);
            float pick = hash(float2(fi + 7.7, seed * 0.7));
            float3 tint = pick < 0.38 ? colorA
                : (pick < 0.76 ? colorB
                : (pick < 0.88 ? mix(colorA, float3(1.0), 0.45) : colorB * 0.35));
            color = mix(color, tint, weight);
        }

        // 3. Grain over the whole thing, fill and blobs together.
        float brightnessBase = 1.0 - 0.26 * grainIntensity;
        float brightnessRange = 0.48 * grainIntensity;
        color = color * (brightnessBase + grain * brightnessRange);

        return half4(clamp(color, 0.0, 1.0), 1.0);
    }
"""

/**
 * How far the field turns on its way in, measured back from wherever this
 * appearance happens to have settled on (see [rememberAtmosphereMotion]).
 */
private const val ATMOSPHERE_TURN_RADIANS = 2.1f

private const val TWO_PI = 6.2831855f

/**
 * How many whole grain fields the shader dissolves through on the way in.
 *
 * Enough that the field is visibly alive while the panel is arriving --
 * real film grain is a *new* field of silver every frame, not one still
 * field lit differently -- and it comes to rest on a single field the
 * moment the panel does. Cheap either way: two extra hashes per pixel
 * rather than a second layer.
 */
private const val GRAIN_FIELDS_ON_ENTER = 14f

/** How far off the panel's own center a field's center may be thrown. */
private const val ATMOSPHERE_DRIFT_SPAN = 0.22f


/**
 * The radius of the arc the field's center travels along as the panel
 * arrives, and how far round that arc it comes from -- as a fraction of the
 * panel's longest side, same units as [ATMOSPHERE_DRIFT_SPAN]. Small: this
 * is a field settling into place rather than a thing orbiting, and the only
 * reason to notice it is that the painting is never quite the one you last
 * looked at.
 */
private const val ATMOSPHERE_ORBIT_RADIUS = 0.05f
private const val ATMOSPHERE_ORBIT_RADIANS = 1.4f

// Same reasoning as GlassScrim's own noiseBrush cache: compiling AGSL is far
// too expensive to redo whenever a panel appears. Unlike that cache this one
// is never invalidated by size (the shader reads size from its own
// "resolution" uniform, updated per draw), only ever (re)built once per
// process. Single-window app, only ever touched from the UI thread.
private var atmosphereShader: RuntimeShader? = null
private var atmosphereShaderBroken = false

private fun atmosphereShaderOrNull(): RuntimeShader? {
    if (atmosphereShaderBroken) {
        return null
    }
    atmosphereShader?.let { return it }

    return try {
        RuntimeShader(ATMOSPHERE_SHADER_SRC).also { atmosphereShader = it }
    } catch (e: Throwable) {
        Log.w("GlassScrim", "Atmosphere shader unavailable on this device, falling back to a flat fill", e)
        atmosphereShaderBroken = true
        null
    }
}

/**
 * The finest and coarsest cell divisor [grainSize] maps to -- see
 * [grainScaleFor]. Finer than [GRAIN_SCALE_COARSE]'s own end of the range
 * still resolves as grain rather than a smooth wash; coarser than
 * [GRAIN_SCALE_FINE]'s end stops reading as individual flecks at all.
 */
private const val GRAIN_SCALE_FINE = 1.8f
private const val GRAIN_SCALE_COARSE = 0.25f

/** Maps [grainSize] (0 finest, 1 coarsest) to the shader's own cell divisor. */
private fun grainScaleFor(grainSize: Float): Float {
    val t = grainSize.coerceIn(0f, 1f)
    return GRAIN_SCALE_FINE + (GRAIN_SCALE_COARSE - GRAIN_SCALE_FINE) * t
}

/**
 * The grain brush for the current draw call, made of [colors] (falling back
 * to a single flat [fallback] color when there's nothing better -- see this
 * file's own doc comment), turned by [rotation] and textured by
 * [grainIntensity] (0 a smooth sweep with no grain at all, 1 the full
 * thing) and [grainSize] (0 the finest fleck, 1 the coarsest -- see
 * [grainScaleFor]). Null if the shader can't run on this device at all, in
 * which case a caller should just fall back to a flat fill rather than
 * leaving the panel unpainted.
 */
private fun DrawScope.atmosphereBrush(
    colors: Pair<Color, Color>?,
    fallback: Color,
    rotation: Float,
    grainIntensity: Float,
    grainSize: Float,
    grainPhase: Float = 0f,
    driftX: Float = 0f,
    driftY: Float = 0f,
    seed: Float = 0f,
    travel: Float = 0f
): Brush? {
    val shader = atmosphereShaderOrNull() ?: return null
    return try {
        val (first, second) = colors ?: (fallback to fallback)
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("rotation", rotation)
        shader.setFloatUniform("colorA", first.red, first.green, first.blue)
        shader.setFloatUniform("colorB", second.red, second.green, second.blue)
        shader.setFloatUniform("grainIntensity", grainIntensity.coerceIn(0f, 1f))
        shader.setFloatUniform("grainScale", grainScaleFor(grainSize))
        shader.setFloatUniform("grainPhase", grainPhase)
        shader.setFloatUniform("drift", driftX, driftY)
        shader.setFloatUniform("seed", seed)
        shader.setFloatUniform("travel", travel)
        ShaderBrush(shader)
    } catch (e: Throwable) {
        Log.w("GlassScrim", "Atmosphere shader failed to update", e)
        null
    }
}

/**
 * Everything Atmosphere animates, gathered in one place and handed out as
 * functions rather than values so every reader takes them in its own draw
 * phase: the panel repaints each frame without recomposing anything around
 * it, and stops repainting by itself once there is nothing left to move.
 */
internal class AtmosphereMotion(
    /** The field's own turn, in radians, for the current frame. */
    val rotation: () -> Float,
    /** How far the field's center sits off the panel's, as fractions of its longest side. */
    val driftX: () -> Float,
    val driftY: () -> Float,
    /** Which grain field the shader is dissolving through -- see [GRAIN_FIELDS_ON_ENTER]. */
    val grainPhase: () -> Float,
    /**
     * This appearance's own draw of the dice, 0 to 1: where each blob
     * starts, which way it travels, and which grain field the whole thing
     * settles on -- so no two openings are quite the same painting.
     */
    val seed: Float,
    /** How much of its path each blob still has ahead of it, 1 down to 0. */
    val travel: () -> Float
)

/**
 * The turn the field makes as the popup arrives, and the place it arrives
 * at.
 *
 * All three ride one spring, [MotionTokens.Ambient.enter] through
 * [LocalAmbientEnter] -- deliberately slower than the panel's own arrival,
 * because a field settling is not the panel arriving a second time. On the
 * arrival it was over in the time the panel takes to slide out of an edge,
 * underneath the motion doing the sliding, and nobody saw it.
 * Where it settles is a fresh random angle every time the popup appears,
 * and the field's center is thrown off the panel's by a fresh random amount
 * with it, so the same panel over the same app never looks like the same
 * painting twice.
 *
 * **Nothing here loops.** The field turns, its center travels along a short
 * arc, and the grain dissolves through a dozen or so whole fields -- once,
 * and then completely still. A field that never stops turning is a panel
 * that never finishes arriving; it also keeps a shader running behind an
 * overlay nobody is looking at any more.
 *
 * All three are handed out as functions read in the caller's own draw
 * phase, so a whole panel of this costs three float reads and three shader
 * uniforms per frame while it is arriving: nothing recomposes, nothing
 * relayouts, and no second layer is drawn. Under reduced motion the token
 * collapses to a snap, which lands every one of them on its settled value
 * with nothing having travelled -- so there is no check of its own to make
 * here either.
 *
 * element:  the atmosphere field.
 * model:    a field of particles, settling once the panel is there.
 * token:    [MotionTokens.Ambient.enter], through [LocalAmbientEnter].
 * property: shader rotation, shader center offset, shader grain phase,
 *           and how far along its path each blob is.
 *           Never the container: the panel this is painted into is a
 *           rectangle that sits perfectly still.
 */
@Composable
internal fun rememberAtmosphereMotion(): AtmosphereMotion {
    val settling = LocalAmbientEnter.current

    // One draw of the dice per appearance: where the field settles, how far
    // off center it sits when it gets there, and which way round its own
    // little arc it comes in from.
    val seed = remember { List(4) { Random.nextFloat() } }

    return remember(seed, settling) {
        val settledAngle = seed[0] * TWO_PI
        val settledDriftX = (seed[1] - 0.5f) * ATMOSPHERE_DRIFT_SPAN
        val settledDriftY = (seed[2] - 0.5f) * ATMOSPHERE_DRIFT_SPAN
        val orbitPhase = seed[0] * TWO_PI
        val away = { (1f - settling()).coerceIn(0f, 1f) }

        AtmosphereMotion(
            rotation = { settledAngle + ATMOSPHERE_TURN_RADIANS * away() },
            // The center comes in along a short arc rather than straight,
            // so the field reads as being swung into place rather than slid
            // -- and it lands exactly on the point the dice picked.
            driftX = {
                val turned = orbitPhase + ATMOSPHERE_ORBIT_RADIANS * away()
                settledDriftX + (cos(turned) - cos(orbitPhase)) * ATMOSPHERE_ORBIT_RADIUS
            },
            driftY = {
                val turned = orbitPhase + ATMOSPHERE_ORBIT_RADIANS * away()
                settledDriftY + (sin(turned) - sin(orbitPhase)) * ATMOSPHERE_ORBIT_RADIUS
            },
            grainPhase = { GRAIN_FIELDS_ON_ENTER * away() },
            seed = seed[3],
            travel = away
        )
    }
}

/**
 * The Atmosphere panel's own background -- a plain empty [Box], meant to sit
 * behind a panel's real content the same way [GlassBackground] does (see
 * that function's own doc comment for why: CollapsedVolumePopup.kt and
 * Service.kt's call sites paint this as Surface's sibling in a shared Box
 * stack rather than through a Modifier chained onto Surface).
 */
@Composable
fun AtmosphereBackground(
    shape: Shape,
    baseColor: Color,
    colors: Pair<Color, Color>?,
    modifier: Modifier = Modifier,
    grainIntensity: Float = ATMOSPHERE_GRAIN_DEFAULT,
    grainSize: Float = ATMOSPHERE_GRAIN_SIZE_DEFAULT
) {
    val motion = rememberAtmosphereMotion()

    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val brush = atmosphereBrush(
                    colors,
                    baseColor.copy(alpha = 1f),
                    motion.rotation(),
                    grainIntensity,
                    grainSize,
                    motion.grainPhase(),
                    motion.driftX(),
                    motion.driftY(),
                    motion.seed,
                    motion.travel()
                )
                if (brush != null) {
                    drawRect(brush, alpha = baseColor.alpha)
                } else {
                    drawRect(baseColor)
                }
            }
    )
}
