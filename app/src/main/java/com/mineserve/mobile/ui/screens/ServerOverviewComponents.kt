package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.ui.MinecraftArtKind
import com.mineserve.mobile.ui.MinecraftPixelArtIcon

/** Stateless presentation shared by the live overview and runner-rendered layout tests. */
@Composable
internal fun ServerOverviewCard(
    name: String,
    description: String,
    storage: String?,
    status: String,
    statusColor: Color,
    logo: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("server-overview"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                logo()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = statusColor, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(7.dp)) {}
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            if (!storage.isNullOrBlank()) Text(storage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
internal fun ServerMetricsRow(
    tps: String,
    players: String,
    memory: String,
    maxMemoryMb: Int,
    onConsole: () -> Unit,
    onPlayers: () -> Unit,
    onMemory: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ServerMetric("TPS", tps, "Ticks/giây", MinecraftArtKind.REDSTONE, Modifier.weight(1f).fillMaxHeight(), onConsole)
        ServerMetric("Người chơi", players, "Đang online", MinecraftArtKind.DIAMOND_SWORD, Modifier.weight(1f).fillMaxHeight(), onPlayers)
        ServerMetric("RAM", memory, "/ "+maxMemoryMb+" MB", MinecraftArtKind.COMMAND_BLOCK, Modifier.weight(1f).fillMaxHeight(), onMemory)
    }
}

@Composable
private fun ServerMetric(label: String, value: String, detail: String, art: MinecraftArtKind, modifier: Modifier, onClick: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    Card(
        onClick = onClick,
        modifier = modifier.testTag("metric-" + label),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, modifier = Modifier.weight(1f), fontSize = if (value.length > 4) 14.sp else 18.sp, fontWeight = FontWeight.SemiBold)
                if (!largeFont && value.length <= 4) MinecraftPixelArtIcon(art, size = 16.dp)
            }
            Text(label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServerControlActions(
    primaryLabel: String,
    secondaryLabel: String,
    running: Boolean,
    primaryEnabled: Boolean,
    secondaryEnabled: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    val largeFont = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    FlowRow(maxItemsInEachRow = if (maxWidth >= 260.dp && !largeFont) 2 else 1,
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Icon(if (running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(primaryLabel, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = onSecondary, enabled = secondaryEnabled, modifier = Modifier.heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text(secondaryLabel)
        }
    }
    }
}

@Composable
internal fun ServerShortcuts(
    serverCount: Int,
    onCreate: () -> Unit,
    onServers: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onCreate, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
            Text("+ Tạo server")
        }
        TextButton(onClick = onServers, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
            Text("Quản lý ("+serverCount+")")
        }
    }
}

@Composable
internal fun ServerReadySoundSetting(soundEnabled: Boolean, onSoundChange: (Boolean) -> Unit, onPreview: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Chuông khi sẵn sàng", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onPreview) { Icon(Icons.Outlined.VolumeUp, "Nghe thử chuông") }
        Switch(checked = soundEnabled, onCheckedChange = onSoundChange,
            modifier = Modifier.testTag("server-ready-sound").semantics { contentDescription = "Chuông khi sẵn sàng" })
    }
}


@Composable
internal fun ServerAddressRow(label: String, address: String?, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(address ?: "Chưa có địa chỉ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        IconButton(onClick = onCopy, enabled = address != null) { Icon(Icons.Outlined.ContentCopy, "Sao chép " + label) }
    }
}
