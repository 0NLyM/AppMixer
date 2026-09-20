package com.nomixer.volume.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomixer.volume.data.App
import com.nomixer.volume.icons.Hook
import com.nomixer.volume.icons.HookOff
import com.nomixer.volume.ui.theme.MotionTokens
import com.nomixer.volume.ui.theme.LocalSliderCornerRadius
import com.nomixer.volume.ui.theme.Typography
import kotlin.math.roundToInt

/** Same elevation the expanded mixer's per-element shadows use elsewhere. */
private val APP_SLIDER_SHADOW_ELEVATION_DP = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppVolumeSlider(
    app: App,
    showOptions: Boolean,
    enableHide: Boolean = true,
    /** Painted only when the mixer's own panel background is off. */
    shadowColor: Color = Color.Transparent,
    onChange: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackSlider(
            modifier = Modifier
                .weight(1f)
                .softShadow(
                    shadowColor,
                    RoundedCornerShape(LocalSliderCornerRadius.current),
                    APP_SLIDER_SHADOW_ELEVATION_DP
                ),
            value = app.volume,
            // A mixer row moves because the panel it lives in did, not
            // because a finger is on this particular bar -- the softer,
            // more damped tier, so a column of them settles as one body.
            settleSpec = MotionTokens.Spatial.defaultSoft(),
            onValueChange = { value ->
                app.volume = value
                onChange?.invoke()
            }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp, 8.dp)
            ) {
                // An app at zero is silenced exactly the way a stream at
                // zero is, so it wears exactly the same bar -- across its
                // own icon's bounds, cutting its own channel through
                // whatever colours are under it (see [muteBar]) -- at the
                // one size every volume glyph in the mixer is drawn at.
                val muteBarExtent = rememberMuteBarExtent(barred = app.volume <= 0f)
                val barTint = LocalContentColor.current

                if (app.icon != null) {
                    Image(
                        bitmap = app.icon!!,
                        contentDescription = "App icon",
                        modifier = Modifier
                            .size(MixerGlyphSize)
                            .muteBar(muteBarExtent, barTint),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    // The bar before the fill, not after it: the channel
                    // it cuts is punched through everything drawn inside
                    // its own layer, and the placeholder block has to be
                    // one of those things or the cut shows the block
                    // instead of the panel behind it.
                    Box(
                        Modifier
                            .size(MixerGlyphSize)
                            .muteBar(muteBarExtent, barTint)
                            .background(Color.Gray)
                    )
                }

                Text(
                    text = app.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${(app.volume * 100).roundToInt()}/100",
                    style = Typography.labelLarge,
                    maxLines = 1,
                )
            }
        }

        if (showOptions) {
            if (enableHide) {
                ToggleButton(
                    checked = app.hidden,
                    checkedIcon = Icons.Default.VisibilityOff,
                    checkedDescription = "Unhide app",
                    uncheckedIcon = Icons.Default.Visibility,
                    uncheckedDescription = "Hide app"
                ) {
                    app.hidden = it
                }
            }

            ToggleButton(
                checked = app.disableVolumeButtons,
                checkedIcon = HookOff,
                checkedDescription = "Enable volume buttons",
                uncheckedIcon = Hook,
                uncheckedDescription = "Disable volume buttons"
            ) {
                app.disableVolumeButtons = it
            }
        }
    }
}
