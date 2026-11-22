package com.mineserve.mobile.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.ui.CoreBrandLogo
import com.mineserve.mobile.ui.theme.LiveStatusDot
import com.mineserve.mobile.ui.theme.McCodeStyle
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.McMetricStyle
import com.mineserve.mobile.ui.theme.McMotion
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.mineHostColors
import com.mineserve.mobile.ui.theme.pressScale
import kotlinx.coroutines.delay

/**
 * Elevated flat surface card with hairline 1.dp border and optional tactile press feedback.
 */
@Composable
fun MhCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
    shape: Shape = McShape.Card,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val scaleModifier = if (onClick != null) modifier.pressScale(interaction) else modifier
    if (onClick != null) {
        Card(
            onClick = onClick,
            interactionSource = interaction,
            modifier = scaleModifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    } else {
        Card(
            modifier = scaleModifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun MhSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun MhStatusBadge(
    label: String,
    accent: Color,
    container: Color,
    contentColor: Color = accent,
    pulsing: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val badgeShape = CircleShape
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = badgeShape,
            color = container,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                LiveStatusDot(color = accent, active = pulsing, size = 7.dp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Surface(
            shape = badgeShape,
            color = container,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.36f)),
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                LiveStatusDot(color = accent, active = pulsing, size = 7.dp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Bento 2x2 metric tile using JetBrains Mono tabular numerals and smooth Cubic Bezier sparklines.
 */
@Composable
fun MhBentoMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    sparklineSamples: List<Float> = emptyList(),
    sparklineMax: Float = 20f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = value,
                style = McMetricStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sparklineSamples.size >= 2) {
                MhSmoothSparkline(
                    samples = sparklineSamples,
                    maxValue = sparklineMax,
                    color = accentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                )
            }
        }
    }
}

@Composable
fun MhSmoothSparkline(
    samples: List<Float>,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val safeMax = maxValue.coerceAtLeast(1f)
        val w = size.width
        val h = size.height
        val padY = 3.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(2f)
        val stepX = w / (samples.size - 1).coerceAtLeast(1).toFloat()

        val points = samples.mapIndexed { idx, v ->
            val norm = (v / safeMax).coerceIn(0.03f, 1f)
            Offset(
                x = idx * stepX,
                y = padY + usableH * (1f - norm)
            )
        }

        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.24f), Color.Transparent)
            )
        )
        drawPath(
            path = strokePath,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val last = points.last()
        drawCircle(color = color.copy(alpha = 0.25f), radius = 4.dp.toPx(), center = last)
        drawCircle(color = color, radius = 2.dp.toPx(), center = last)
    }
}

/**
 * Copy-first connection address row with animated Copy -> Check confirmation.
 */
@Composable
fun MhCopyAddressRow(
    platformLabel: String,
    address: String?,
    fallbackText: String,
    icon: ImageVector,
    subtitle: String? = null,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val semanticsColors = MaterialTheme.mineHostColors
    var copied by remember(address) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600L)
            copied = false
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (copied) semanticsColors.success.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !address.isNullOrBlank()) {
                    address?.let {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copied = true
                        onCopy(it)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = platformLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = address ?: fallbackText,
                    style = if (address != null) McCodeStyle.copy(fontSize = 11.5.sp, lineHeight = 16.sp) else MaterialTheme.typography.bodyMedium,
                    color = if (address != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Button(
                onClick = {
                    address?.let {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copied = true
                        onCopy(it)
                    }
                },
                enabled = !address.isNullOrBlank(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (copied) semanticsColors.success else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (copied) semanticsColors.onSuccess else MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                AnimatedContent(
                    targetState = copied,
                    transitionSpec = {
                        (fadeIn(tween(McMotion.FastMs)) + scaleIn(initialScale = 0.85f)) togetherWith
                            (fadeOut(tween(110)) + scaleOut(targetScale = 0.85f))
                    },
                    label = "copy_icon_morph"
                ) { isCopied ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = if (isCopied) "Đã chép" else "Chép",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isCopied) "Đã chép" else "Chép",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean onboarding choice card used in Empty State (e.g., Pure Rust vs Java & Bedrock).
 */
@Composable
fun MhEngineOptionCard(
    title: String,
    badge: String,
    subtitle: String,
    accent: Color,
    accentContainer: Color,
    onAccentContainer: Color,
    coreLogo: ServerCore? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction, enabled = enabled),
        shape = McShape.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (coreLogo != null) {
                CoreBrandLogo(core = coreLogo, size = 46.dp)
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentContainer)
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TitleWithBadge(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    badge = {
                        Surface(
                            color = accentContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = onAccentContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TitleWithBadge(
    title: @Composable () -> Unit,
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            title()
            badge()
        },
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val titlePlaceable = measurables[0].measure(childConstraints)
        val badgePlaceable = measurables[1].measure(childConstraints)
        val gapPx = 8.dp.roundToPx()
        val vGapPx = 4.dp.roundToPx()

        val sideBySide = constraints.maxWidth == Constraints.Infinity ||
            titlePlaceable.width + gapPx + badgePlaceable.width <= constraints.maxWidth
        val layoutWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else if (sideBySide) {
            titlePlaceable.width + gapPx + badgePlaceable.width
        } else {
            maxOf(titlePlaceable.width, badgePlaceable.width)
        }
        val layoutHeight = if (sideBySide) {
            maxOf(titlePlaceable.height, badgePlaceable.height)
        } else {
            titlePlaceable.height + vGapPx + badgePlaceable.height
        }

        layout(layoutWidth, layoutHeight) {
            if (sideBySide) {
                titlePlaceable.placeRelative(0, (layoutHeight - titlePlaceable.height) / 2)
                badgePlaceable.placeRelative(titlePlaceable.width + gapPx, (layoutHeight - badgePlaceable.height) / 2)
            } else {
                titlePlaceable.placeRelative(0, 0)
                badgePlaceable.placeRelative(0, titlePlaceable.height + vGapPx)
            }
        }
    }
}
