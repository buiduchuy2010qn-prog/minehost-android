package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mineserve.mobile.server.PlayableWorldPack
import com.mineserve.mobile.server.LegacyWorldSafety
import com.mineserve.mobile.ui.MinecraftArtKind
import com.mineserve.mobile.ui.MinecraftPixelArtIcon
import com.mineserve.mobile.ui.theme.McShape

@Composable
internal fun WorldListCard(
    folder: String, size: String, kind: String, active: Boolean, canManage: Boolean,
    onDetails: () -> Unit, onPlay: () -> Unit, onRename: () -> Unit,
    onExport: () -> Unit, onBackup: () -> Unit, onReset: () -> Unit, onDelete: () -> Unit
) {
    var menu by remember(folder) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().testTag("world-card-" + folder), shape = McShape.ControlCompact,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick = onDetails).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MinecraftPixelArtIcon(MinecraftArtKind.GRASS, size = 28.dp)
                    Text(PlayableWorldPack.displayName(folder), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Thao tác map " + folder) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Chi tiết map") }, onClick = { menu = false; onDetails() })
                        DropdownMenuItem(text = { Text("Đổi tên") }, enabled = canManage, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Xuất ZIP") }, enabled = canManage, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text("Sao lưu map này") }, enabled = canManage, onClick = { menu = false; onBackup() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Sao lưu & đặt lại") }, enabled = canManage, onClick = { menu = false; onReset() })
                        DropdownMenuItem(text = { Text("Xóa map", color = MaterialTheme.colorScheme.error) }, enabled = canManage,
                            onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (PlayableWorldPack.displayName(folder) != folder) Text("Thư mục: " + folder,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((if (active) "Đang chọn" else "Đang cất giữ") + " · " + kind + " · " + size,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (active) TextButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text("Chi tiết map") }
            else OutlinedButton(onClick = onPlay, enabled = canManage, modifier = Modifier.fillMaxWidth(), shape = McShape.ControlCompact) { Text("Chơi map này") }
        }
    }
}

@Composable
internal fun LegacyWorldSafetyDialog(report: LegacyWorldSafety.Report, busy: Boolean, canRepair: Boolean,
    onDismiss: () -> Unit, onRepair: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Kiểm tra datapack map cũ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(PlayableWorldPack.displayName(report.world), fontWeight = FontWeight.SemiBold)
                report.notes.forEach { Text(it) }
                if (report.canRepair) Text("Khi xác nhận: sao lưu toàn bộ map cùng Nether/End, kiểm tra bản ZIP, rồi chỉ bỏ lệnh tự dựng lại. Không xóa khối, không đặt lại map, không thay tiến trình hoặc nâng gameplay map cũ.")
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Đang sao lưu và kiểm tra. Hãy đợi hoàn tất.") }
            }
        },
        confirmButton = {
            if (report.canRepair) TextButton(onClick = onRepair, enabled = canRepair && !busy) { Text("Sao lưu & bảo vệ") }
            else TextButton(onClick = onDismiss) { Text("Đóng") }
        },
        dismissButton = { if (report.canRepair) TextButton(onClick = onDismiss, enabled = !busy) { Text("Hủy") } }
    )
}
