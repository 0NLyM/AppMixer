package com.nomixer.volume.compose

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

private const val TAG = "StochasticFade"

/**
 * How far the pane of glass a reader is drawn on has come into being, 0 to
 * 1, for the one thing a dissolve of pixels cannot reach: the system's own
 * blur. That is a region the compositor blurs behind the window, not pixels
 * the app draws, so it has to be handed its opacity (see [systemGlassBlur]).
 * Read in the draw phase. Fully there outside the overlay.
 */
internal val LocalGlassPresence = compositionLocalOf<() -> Float> { { 1f } }

/** The size of one grain of the dissolve, in dp. */
private const val GRAIN_DP = 1.5f

/**
 * Every pixel of the layer gets a threshold of its own from a fixed noise,
 * and shows only once [progress] has passed it. The ramp is a little wider
 * than a single threshold, so the grain is a pale scatter rather than a
 * hard salt and pepper; at 0 nothing is shown at all and at 1 all of it is.
 *
 * A hash of the grain's own cell rather than a texture: nothing to load, and
 * no lattice to see (the usual dither patterns have one, and it showed).
 */
private const val DISSOLVE_SHADER = """
uniform shader content;
uniform float progress;
uniform float grain;
half4 main(float2 p) {
    float2 q = floor(p / grain);
    float n = fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453123);
    float a = clamp((progress * 1.2 - n) / 0.2, 0.0, 1.0);
    return content.eval(p) * half(a);
}
"""

/**
 * A stochastic fade: what is under this modifier comes and goes as a
 * scatter of pixels rather than as a smooth alpha -- each pixel switching on
 * at a moment of its own as [shown] rises from 0 to 1, and off again as it
 * falls. Nothing moves; it is a condition of the picture changing, not an
 * object travelling.
 *
 * Only while [shown] is between 0 and 1 does the layer carry an effect (and
 * so an offscreen buffer): fully there it is drawn as it would be without
 * the modifier, and fully gone it is not drawn at all. Where the platform
 * won't build the shader the fade is a plain alpha instead.
 *
 * element:  a surface's content -- a popup's slider and button, the mixer's rows.
 * model:    -- opacity is not an object.
 * token:    MotionTokens.Effects.dissolveIn / dissolveOut / dissolve, through
 *           the progress it is handed.
 * property: the dissolve's progress.
 */
@Composable
internal fun Modifier.stochasticFade(shown: () -> Float): Modifier {
    val grain = LocalDensity.current.density * GRAIN_DP
    val shader = remember(grain) {
        try {
            RuntimeShader(DISSOLVE_SHADER).also { it.setFloatUniform("grain", grain) }
        } catch (e: Throwable) {
            Log.w(TAG, "No dissolve shader; fading plainly", e)
            null
        }
    }
    return graphicsLayer {
        val progress = shown().coerceIn(0f, 1f)
        when {
            progress >= 1f -> {
                alpha = 1f
                renderEffect = null
            }
            progress <= 0f -> {
                alpha = 0f
                renderEffect = null
            }
            shader == null -> {
                alpha = progress
                renderEffect = null
            }
            else -> {
                alpha = 1f
                shader.setFloatUniform("progress", progress)
                renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }
        }
    }
}

