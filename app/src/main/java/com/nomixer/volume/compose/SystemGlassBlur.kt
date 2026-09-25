package com.nomixer.volume.compose

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import com.nomixer.volume.data.DiagnosticLog
import java.lang.reflect.Method

private const val TAG = "SystemGlassBlur"

/**
 * The system's own blur behind a pane of glass: the compositor blurring
 * whatever is behind the overlay's window, live, within the pane's own
 * rounded rectangle -- what the app's blur of a capture only imitates, at no
 * cost to the app at all.
 *
 * It is the window's background-blur drawable (the one the system's own
 * panels use; `ViewRootImpl.createBackgroundBlurDrawable`), drawn inside the
 * pane like anything else: the renderer reports where the drawable's node
 * lands every frame, the whole transform from the window down to the pane
 * included, and the blurred region is moved with the same frame. So it
 * travels with a panel opening, closing or sliding out of its edge exactly
 * as the glass over it does -- no second window, nothing left a frame behind.
 *
 * Only while the platform's cross-window blur is on (see Service's
 * `systemBlurAvailable`): battery saver switches it off, and the glass falls
 * back on the app's own blur of a capture.
 */
@Composable
internal fun Modifier.systemGlassBlur(radiusPx: Int, shape: Shape): Modifier {
    val view = LocalView.current
    val blur = remember(view) { SystemBlurDrawable.create(view) } ?: return this
    return drawBehind {
        blur.update(
            width = size.width.toInt(),
            height = size.height.toInt(),
            radiusPx = radiusPx,
            corners = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rounded -> outline.roundRect.run {
                    floatArrayOf(
                        topLeftCornerRadius.x,
                        topRightCornerRadius.x,
                        bottomLeftCornerRadius.x,
                        bottomRightCornerRadius.x
                    )
                }
                else -> NO_CORNERS
            }
        )
        drawIntoCanvas { blur.drawable.draw(it.nativeCanvas) }
    }
}

private val NO_CORNERS = floatArrayOf(0f, 0f, 0f, 0f)

/**
 * One background-blur drawable and the hidden setters it is driven through,
 * looked up once: they run on every frame a pane is drawn, and only when
 * what they set has actually changed.
 */
private class SystemBlurDrawable(
    val drawable: Drawable,
    private val setBlurRadius: Method,
    private val setCornerRadii: Method?,
    private val setCornerRadius: Method
) {
    private var radius = -1
    private val corners = FloatArray(4) { -1f }

    fun update(width: Int, height: Int, radiusPx: Int, corners: FloatArray) {
        val bounds = drawable.bounds
        if (bounds.width() != width || bounds.height() != height) {
            drawable.setBounds(0, 0, width, height)
        }
        if (radiusPx != radius) {
            radius = radiusPx
            setBlurRadius.invoke(drawable, radiusPx)
        }
        if (!corners.contentEquals(this.corners)) {
            corners.copyInto(this.corners)
            if (setCornerRadii != null) {
                setCornerRadii.invoke(drawable, corners[0], corners[1], corners[2], corners[3])
            } else {
                setCornerRadius.invoke(drawable, corners.max())
            }
        }
    }

    companion object {
        /** A fresh one for [view]'s window, or null where the platform won't make one. */
        fun create(view: View): SystemBlurDrawable? = try {
            val root = View::class.java.getMethod("getViewRootImpl").invoke(view)
                ?: error("the view has no window yet")
            val drawable = root.javaClass.getMethod("createBackgroundBlurDrawable").invoke(root) as Drawable
            val type = drawable.javaClass
            val floats = Float::class.javaPrimitiveType
            SystemBlurDrawable(
                drawable,
                type.getMethod("setBlurRadius", Int::class.javaPrimitiveType),
                runCatching { type.getMethod("setCornerRadius", floats, floats, floats, floats) }.getOrNull(),
                type.getMethod("setCornerRadius", floats)
            )
        } catch (e: Throwable) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            Log.w(TAG, "No background-blur drawable", cause)
            DiagnosticLog.log("Glass✗", "system blur unavailable: ${cause.javaClass.simpleName}: ${cause.message}")
            null
        }
    }
}
