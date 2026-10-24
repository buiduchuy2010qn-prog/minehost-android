package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.BuildConfig
import com.mineserve.mobile.R
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.McViewModel.UpdateUiState
import com.mineserve.mobile.ui.theme.Indigo

@Composable
fun UpdateDialog(vm: McViewModel) {
    val visible by vm.updateDialogVisible.collectAsState()
    val state by vm.updateState.collectAsState()
    val autoInstall by vm.autoInstallUpdateEnabled.collectAsState()
    if (!visible) return
    when (val current = state) {
        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = vm::dismissUpdateDialog,
            title = { Text("🔄 Đang kiểm tra bản cập nhật mới...") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Đang kết nối tới GitHub Releases để kiểm tra phiên bản mới nhất...", fontSize = 13.sp)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::dismissUpdateDialog) { Text(stringResource(R.string.upd_cancel)) } }
        )
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = vm::skipCurrentUpdate,
            title = { Text("🚀 Phát hiện bản cập nhật mới v${current.release.tag}!") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Phiên bản hiện tại: v${BuildConfig.VERSION_NAME} → Bản mới: v${current.release.tag}",
                        fontSize = 12.5.sp,
                        color = Indigo,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "✅ Hỗ trợ cài đè trực tiếp 100% — Giữ nguyên toàn bộ Server, Thế giới (Map), Mod & Cấu hình!",
                        color = Color(0xFF2E7D32),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        current.release.notes.ifBlank { stringResource(R.string.upd_notes_empty) },
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 8.dp).heightIn(max = 180.dp).verticalScroll(rememberScrollState())
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("⚡ Tự động tải & bật cài đè", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = autoInstall,
                            onCheckedChange = { vm.setAutoInstallUpdateEnabled(it) }
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = vm::openGithubUpdate) { Text("Mở GitHub") }
                    Button(
                        onClick = { vm.downloadUpdate(autoInstallWhenDone = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("🚀 Tải & Cài Đè Ngay", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }
            },
            dismissButton = { TextButton(onClick = vm::skipCurrentUpdate) { Text(stringResource(R.string.upd_skip)) } }
        )
        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = vm::dismissUpdateDialog,
            title = { Text("⬇️ Đang tải bản cập nhật v${current.release.tag}...") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator({ current.progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    Text(
                        "Tiến độ: ${(current.progress.coerceIn(0f, 1f) * 100).toInt()}% • Tự động mở bảng Cài đè ngay khi tải xong 100%!",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "💡 Bạn có thể bấm 'Ẩn xuống nền' — ứng dụng vẫn tiếp tục tải ngầm.",
                        fontSize = 11.5.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = vm::dismissUpdateDialog) { Text("Ẩn xuống nền") }
            }
        )
        is UpdateUiState.Downloaded -> AlertDialog(
            onDismissRequest = vm::dismissUpdateDialog,
            title = { Text("✅ Đã tải xong bản v${current.release.tag} — Sẵn sàng cài đè!") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "File APK v${current.release.tag} đã được xác minh chữ ký số an toàn. Bấm nút bên dưới để mở trình cài đặt hệ thống và cài đè trực tiếp (không mất dữ liệu Server & Map).",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.installDownloadedUpdate(current.apkPath) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("📲 Cài Đè Ngay (Giữ 100% Dữ Liệu)", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissUpdateDialog) { Text(stringResource(R.string.upd_install_later)) } }
        )
        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = vm::dismissUpdateDialog,
            title = { Text(stringResource(R.string.upd_failed)) },
            text = { Text(current.message, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    if (current.release == null) vm.checkForUpdate(manual = true) else vm.downloadUpdate(autoInstallWhenDone = true)
                }) { Text(if (current.release == null) stringResource(R.string.upd_retry_check) else stringResource(R.string.upd_retry_download), color = Indigo) }
            },
            dismissButton = { TextButton(onClick = vm::dismissUpdateDialog) { Text(stringResource(R.string.upd_close)) } }
        )
        UpdateUiState.Idle -> Unit
    }
}
