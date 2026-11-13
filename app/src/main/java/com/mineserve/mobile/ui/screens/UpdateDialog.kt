package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.BuildConfig
import com.mineserve.mobile.R
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.McViewModel.UpdateUiState
import com.mineserve.mobile.ui.theme.HorizonGlass
import com.mineserve.mobile.ui.theme.HorizonTabularStyle
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.horizonGlassPlate

@Composable
private fun UpdateDialogHeader(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    badge: String? = null
) {
    val glass = HorizonGlass
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg)
                    .border(1.dp, iconTint.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!badge.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .horizonGlassPlate(
                                shape = CircleShape,
                                containerColor = glass.accentSoft,
                                borderColor = glass.accent.copy(alpha = 0.42f)
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.merge(HorizonTabularStyle),
                            fontWeight = FontWeight.SemiBold,
                            color = if (glass.isLight) MaterialTheme.colorScheme.primary else glass.accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(vm: McViewModel) {
    val visible by vm.updateDialogVisible.collectAsState()
    val state by vm.updateState.collectAsState()
    if (!visible) return

    val glass = HorizonGlass
    val accentInk = if (glass.isLight) MaterialTheme.colorScheme.primary else glass.accent
    val greenInk = if (glass.isLight) Color(0xFF047857) else glass.liveGreen
    val coralInk = if (glass.isLight) Color(0xFFB91C1C) else glass.dangerCoral

    when (val current = state) {
        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = vm::dismissUpdateDialog,
            modifier = Modifier.border(1.dp, glass.specularTop, McShape.Dialog),
            shape = McShape.Dialog,
            containerColor = glass.glassChrome,
            tonalElevation = 0.dp,
            title = {
                UpdateDialogHeader(
                    icon = Icons.Outlined.SystemUpdate,
                    iconTint = accentInk,
                    iconBg = glass.accentSoft,
                    title = "Kiểm tra bản cập nhật",
                    badge = "v${BuildConfig.VERSION_NAME}"
                )
            },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.glassRow,
                            borderColor = glass.hairline
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = glass.accent
                    )
                    Text(
                        text = "Đang kết nối GitHub Releases để kiểm tra phiên bản chính thức mới nhất…",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = vm::dismissUpdateDialog) {
                    Text(stringResource(R.string.upd_cancel), fontWeight = FontWeight.Medium)
                }
            }
        )

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = vm::skipCurrentUpdate,
            modifier = Modifier.border(1.dp, glass.specularTop, McShape.Dialog),
            shape = McShape.Dialog,
            containerColor = glass.glassChrome,
            tonalElevation = 0.dp,
            title = {
                UpdateDialogHeader(
                    icon = Icons.Outlined.AutoAwesome,
                    iconTint = accentInk,
                    iconBg = glass.accentSoft,
                    title = "Bản cập nhật mới v${current.release.tag}",
                    badge = "v${BuildConfig.VERSION_NAME}  →  v${current.release.tag}"
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.liveGreenSoft,
                                borderColor = glass.liveGreen.copy(alpha = 0.42f)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Cài đè an toàn 100% dữ liệu",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = greenInk
                        )
                        Text(
                            text = "Giữ nguyên toàn bộ Server, Thế giới (Map), Mod & Cấu hình hiện có. Tự động dọn tệp APK tạm ngay sau khi nâng cấp.",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Điểm mới trong bản phát hành",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = current.release.notes.ifBlank { stringResource(R.string.upd_notes_empty) },
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.downloadUpdate(autoInstallWhenDone = true) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cập nhật ngay", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = vm::skipCurrentUpdate,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, glass.hairline)
                ) {
                    Text("Bỏ qua", fontWeight = FontWeight.Medium)
                }
            }
        )

        is UpdateUiState.Downloading -> {
            val clamped = current.progress.coerceIn(0f, 1f)
            val pct = (clamped * 100).toInt()
            AlertDialog(
                onDismissRequest = vm::dismissUpdateDialog,
                modifier = Modifier.border(1.dp, glass.specularTop, McShape.Dialog),
                shape = McShape.Dialog,
                containerColor = glass.glassChrome,
                tonalElevation = 0.dp,
                title = {
                    UpdateDialogHeader(
                        icon = Icons.Outlined.CloudDownload,
                        iconTint = accentInk,
                        iconBg = glass.accentSoft,
                        title = "Đang tải bản cập nhật",
                        badge = "v${current.release.tag} · $pct%"
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizonGlassPlate(
                                shape = McShape.Row,
                                containerColor = glass.glassRow,
                                borderColor = glass.hairline
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tiến độ tải gói OTA",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.titleSmall.merge(HorizonTabularStyle),
                                fontWeight = FontWeight.SemiBold,
                                color = accentInk
                            )
                        }
                        LinearProgressIndicator(
                            progress = { clamped },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape),
                            color = glass.liveGreen,
                            trackColor = glass.hairline
                        )
                        Text(
                            text = "Sẽ tự động mở trình cài đè ngay khi tải hoàn tất 100%.",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = vm::dismissUpdateDialog) {
                        Text("Ẩn xuống nền", fontWeight = FontWeight.Medium)
                    }
                }
            )
        }

        is UpdateUiState.Downloaded -> AlertDialog(
            onDismissRequest = vm::skipCurrentUpdate,
            modifier = Modifier.border(1.dp, glass.specularTop, McShape.Dialog),
            shape = McShape.Dialog,
            containerColor = glass.glassChrome,
            tonalElevation = 0.dp,
            title = {
                UpdateDialogHeader(
                    icon = Icons.Outlined.CheckCircle,
                    iconTint = greenInk,
                    iconBg = glass.liveGreenSoft,
                    title = "Sẵn sàng cài đặt v${current.release.tag}",
                    badge = "Đã xác minh chữ ký số"
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.liveGreenSoft,
                            borderColor = glass.liveGreen.copy(alpha = 0.42f)
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Gói APK v${current.release.tag} đã tải xong và khớp chữ ký bảo mật.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Nhấn 'Cập nhật ngay' để cài đè trực tiếp (giữ nguyên 100% Server & Map, tự xóa APK tạm sau khi xong).",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.installDownloadedUpdate(current.apkPath) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Cập nhật ngay", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = vm::skipCurrentUpdate,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, glass.hairline)
                ) {
                    Text("Bỏ qua", fontWeight = FontWeight.Medium)
                }
            }
        )

        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = vm::dismissUpdateDialog,
            modifier = Modifier.border(1.dp, glass.specularTop, McShape.Dialog),
            shape = McShape.Dialog,
            containerColor = glass.glassChrome,
            tonalElevation = 0.dp,
            title = {
                UpdateDialogHeader(
                    icon = Icons.Outlined.ErrorOutline,
                    iconTint = coralInk,
                    iconBg = glass.dangerCoralSoft,
                    title = stringResource(R.string.upd_failed)
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizonGlassPlate(
                            shape = McShape.Row,
                            containerColor = glass.dangerCoralSoft,
                            borderColor = glass.dangerCoral.copy(alpha = 0.45f)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (current.release == null) vm.checkForUpdate(manual = true)
                        else vm.downloadUpdate(autoInstallWhenDone = true)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (current.release == null) {
                            stringResource(R.string.upd_retry_check)
                        } else {
                            stringResource(R.string.upd_retry_download)
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissUpdateDialog) {
                    Text(stringResource(R.string.upd_close))
                }
            }
        )

        UpdateUiState.Idle -> Unit
    }
}
