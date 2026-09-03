package com.nomixer.volume.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nomixer.volume.R
import java.util.Locale
import kotlin.math.roundToInt

private val PresetColors = listOf(
    Color(0xFFD7191E), // Nothing red
    Color(0xFFFFFFFF),
    Color(0xFF000000),
    Color(0xFFF7F5F1),
    Color(0xFF1A1A1A),
    Color(0xFF6B6B6B),
    Color(0xFFFF6B00),
    Color(0xFFFFC400),
    Color(0xFF34C759),
    Color(0xFF00B8D4),
    Color(0xFF2979FF),
    Color(0xFF9C27B0)
)

private fun Color.toHsv(): Triple<Float, Float, Float> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return Triple(hsv[0], hsv[1], hsv[2])
}

private fun Color.toHex(): String =
    String.format(Locale.US, "%06X", toArgb() and 0xFFFFFF)

/**
 * The grey chequerboard that shows through a partly transparent swatch, so
 * "disabled" doesn't just look like a black square.
 */
private fun Modifier.checkerboard(square: Dp = 6.dp): Modifier = drawBehind {
    val step = square.toPx()
    drawRect(color = Color(0xFFE8E8E8))
    var row = 0
    var y = 0f
    while (y < size.height) {
        var column = 0
        var x = 0f
        while (x < size.width) {
            if ((row + column) % 2 == 0) {
                drawRect(
                    color = Color(0xFFBDBDBD),
                    topLeft = Offset(x, y),
                    size = Size(
                        width = minOf(step, size.width - x),
                        height = minOf(step, size.height - y)
                    )
                )
            }
            x += step
            column++
        }
        y += step
        row++
    }
}

private fun parseHex(text: String): Color? {
    val cleaned = text.removePrefix("#").trim()
    if (cleaned.length != 6) {
        return null
    }

    val value = cleaned.toLongOrNull(16) ?: return null
    return Color(value or 0xFF000000L)
}

/**
 * HSV color picker: saturation/value field, hue bar, preset swatches and a
 * hex field. [onReset] is offered when the setting can fall back to the
 * built-in palette default.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    onReset: (() -> Unit)? = null
) {
    val initialHsv = remember(initialColor) { initialColor.toHsv() }
    var hue by remember { mutableFloatStateOf(initialHsv.first) }
    var saturation by remember { mutableFloatStateOf(initialHsv.second) }
    var value by remember { mutableFloatStateOf(initialHsv.third) }
    var hexText by remember { mutableStateOf(initialColor.toHex()) }
    var alpha by remember(initialColor) { mutableFloatStateOf(initialColor.alpha) }

    val color = Color.hsv(hue, saturation, value).copy(alpha = alpha)

    fun setColor(newColor: Color) {
        val hsv = newColor.toHsv()
        hue = hsv.first
        saturation = hsv.second
        value = hsv.third
        hexText = newColor.toHex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Saturation (x) / value (y) field for the current hue.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.White, Color.hsv(hue, 1f, 1f))
                            )
                        )
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(16.dp)
                        )
                        .pointerInput(Unit) {
                            fun update(position: Offset) {
                                saturation = (position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (position.y / size.height).coerceIn(0f, 1f)
                                hexText = Color.hsv(hue, saturation, value).toHex()
                            }

                            detectDragGestures(
                                onDragStart = { offset -> update(offset) }
                            ) { change, _ -> update(change.position) }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                                hexText = Color.hsv(hue, saturation, value).toHex()
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        val x = saturation * size.width
                        val y = (1f - value) * size.height
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(x, y),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 11.dp.toPx(),
                            center = Offset(x, y),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // Hue bar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                (0..6).map { step -> Color.hsv(step * 60f, 1f, 1f) }
                            )
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .pointerInput(Unit) {
                            fun update(position: Offset) {
                                hue = ((position.x / size.width).coerceIn(0f, 1f)) * 360f
                                hexText = Color.hsv(hue, saturation, value).toHex()
                            }

                            detectDragGestures(
                                onDragStart = { offset -> update(offset) }
                            ) { change, _ -> update(change.position) }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                hue = ((offset.x / size.width).coerceIn(0f, 1f)) * 360f
                                hexText = Color.hsv(hue, saturation, value).toHex()
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                        val x = (hue / 360f) * size.width
                        drawCircle(
                            color = Color.White,
                            radius = size.height / 2f - 2.dp.toPx(),
                            center = Offset(x.coerceIn(0f, size.width), size.height / 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                // Preview + hex entry.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .checkerboard()
                            .background(color)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                    )

                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text.removePrefix("#").take(6).uppercase(Locale.US)
                            parseHex(hexText)?.let { parsed ->
                                val hsv = parsed.toHsv()
                                hue = hsv.first
                                saturation = hsv.second
                                value = hsv.third
                            }
                        },
                        label = { Text("HEX") },
                        prefix = { Text("#") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Opacity, with 0% meaning "don't paint this at all" -- the
                // way to switch one color of the popup off rather than
                // replace it.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.color_opacity),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (alpha <= 0f) {
                                stringResource(R.string.color_disabled)
                            } else {
                                "${(alpha * 100).roundToInt()}%"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Slider(
                        value = alpha,
                        onValueChange = { alpha = it },
                        valueRange = 0f..1f
                    )
                }

                // Presets.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetColors.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(preset)
                                        .border(
                                            width = if (preset.toHex() == color.toHex()) 3.dp else 1.dp,
                                            color = if (preset.toHex() == color.toHex()) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable { setColor(preset) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(color) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onReset != null) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.reset))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/** A settings row that opens [ColorPickerDialog] for one color role. */
@Composable
fun ColorSettingRow(
    label: String,
    color: Color,
    isCustom: Boolean,
    onColorChange: (Color) -> Unit,
    onReset: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .checkerboard(square = 5.dp)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = when {
                    color.alpha <= 0f -> stringResource(R.string.color_disabled)
                    isCustom && color.alpha < 1f ->
                        "#${color.toHex()} · ${(color.alpha * 100).roundToInt()}%"

                    isCustom -> "#${color.toHex()}"
                    else -> stringResource(R.string.color_default, color.toHex())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            title = label,
            initialColor = color,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                onColorChange(picked)
                showPicker = false
            },
            onReset = if (isCustom) {
                {
                    onReset()
                    showPicker = false
                }
            } else {
                null
            }
        )
    }
}

/** Rounds a float to a single decimal for compact display, e.g. "1.2x". */
internal fun formatScale(value: Float): String =
    "${(value * 10).roundToInt() / 10f}x"
