package com.mineserve.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Global layout, shape, and icon design tokens (4/8dp grid). */
object McSpacing {
    val Page = 16.dp
    val CardGap = 10.dp
    val CardGapCompact = 6.dp
    val CardInner = 16.dp
    val CardInnerCompact = 12.dp
    val Section = 16.dp
    val Row = 8.dp
    val CardHeader = 12.dp
    val CardHeaderCompact = 8.dp
}

object McShape {
    val Card = RoundedCornerShape(16.dp)
    val Control = RoundedCornerShape(12.dp)
    val ControlCompact = RoundedCornerShape(10.dp)
    val Row = RoundedCornerShape(12.dp)
    val Small = RoundedCornerShape(8.dp)
    val Chip = RoundedCornerShape(20.dp)
    val Dialog = RoundedCornerShape(24.dp)
    val Circle = RoundedCornerShape(percent = 50)
}

object McIcon {
    val Inline = 16.dp
    val Action = 18.dp
    val Item = 20.dp
    val Display = 24.dp
    val Touch = 48.dp
    val TouchCompact = 48.dp
}
