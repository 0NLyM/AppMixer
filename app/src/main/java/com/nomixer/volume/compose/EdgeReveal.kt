package com.nomixer.volume.compose

/** Which screen edge a panel is revealed from -- or [None], for one with no edge to come out of. */
enum class RevealEdge {
    Left,
    Right,
    Top,
    Bottom,
    None;

    /** Whether the panel comes out of a *side* of the display rather than its top or bottom. */
    val isHorizontal: Boolean
        get() = this == Left || this == Right
}
