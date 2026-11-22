package com.mineserve.mobile.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object McMotion {
    const val FastMs = 150
    const val NormalMs = 220
    const val SlowMs = 320
    const val PulseMs = 1400

    val PressSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)
    val StandardEasing = FastOutSlowInEasing
}

val LocalReducedMotion = staticCompositionLocalOf { false }

fun isReducedMotionEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}.getOrDefault(false)

/**
 * Tactile press-scale feedback using GPU transform (`graphicsLayer`) without shifting layout bounds.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current || remember(context) { isReducedMotionEnabled(context) }
    if (!enabled || reducedMotion) return@composed this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = McMotion.PressSpring,
        label = "press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Live status indicator dot with optional smooth breathing halo ring.
 */
@Composable
fun LiveStatusDot(
    color: Color,
    active: Boolean,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current || remember(context) { isReducedMotionEnabled(context) }
    val animateHalo = active && !reducedMotion

    val haloScale: Float
    val haloAlpha: Float
    if (animateHalo) {
        val transition = rememberInfiniteTransition(label = "live_status_halo")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = McMotion.PulseMs, easing = McMotion.StandardEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "halo_phase"
        )
        haloScale = 1f + phase * 1.15f
        haloAlpha = (1f - phase) * 0.45f
    } else {
        haloScale = 1f
        haloAlpha = if (active) 0.25f else 0f
    }

    Box(
        modifier = modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        if (haloAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = haloScale
                        scaleY = haloScale
                        alpha = haloAlpha
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}
