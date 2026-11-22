package com.mineserve.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mineserve.mobile.ui.theme.McMotion

/** Expand a tool without running it; saved drafts survive collapsing the section. */
@Composable
internal fun HostDisclosure(
    title: String,
    detail: String,
    visible: Boolean = true,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val savedContent = rememberSaveableStateHolder()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = McMotion.NormalMs, easing = McMotion.StandardEasing),
        label = "disclosure_chevron"
    )
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Đang mở" else "Đang thu gọn" }
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Thu gọn" else "Mở công cụ") { expanded = !expanded }
                    .heightIn(min = 64.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(McMotion.NormalMs, easing = McMotion.StandardEasing)) + fadeIn(tween(McMotion.FastMs)),
                exit = shrinkVertically(tween(McMotion.FastMs, easing = McMotion.StandardEasing)) + fadeOut(tween(110))
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    )
                    savedContent.SaveableStateProvider("content") {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    }
                }
            }
        }
    }
}

/** Keep action labels readable instead of squeezing equal-width buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdaptiveActionRow(content: @Composable RowScope.() -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = if (maxWidth < 320.dp || fontScale > 1.15f) 1 else 2
        ) { content() }
    }
}
