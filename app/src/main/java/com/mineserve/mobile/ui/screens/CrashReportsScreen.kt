package com.mineserve.mobile.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.R
import com.mineserve.mobile.server.CrashReportAnalyzer
import com.mineserve.mobile.server.CrashReportManager
import com.mineserve.mobile.ui.BackBar
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.SegmentedTabs
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.McIcon
import com.mineserve.mobile.ui.theme.Muted

@Composable
fun CrashReportsScreen(
    vm: McViewModel,
    onBack: () -> Unit,
    onGoToJavaInstall: () -> Unit,
    onGoToModManager: (() -> Unit)? = null,
    onGoToDiagnostics: (() -> Unit)? = null
) {
    val reports by vm.crashReports.collectAsState()
    val modDoctorBusy by vm.modDoctorBusy.collectAsState()
    val modDoctorProgress by vm.modDoctorProgress.collectAsState()
    val lastModDoctorReport by vm.lastModDoctorReport.collectAsState()
    LaunchedEffect(Unit) { vm.loadCrashReports() }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                BackBar(stringResource(R.string.crash_title), onBack)
            }
            if (reports.isNotEmpty()) {
                OutlinedButton(
                    onClick = { vm.clearAllCrashReports() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Coral, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Xóa tất cả (${reports.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Coral)
                }
            }
        }

        // Thẻ Bác sĩ Tự động Sửa lỗi Mod & Điều hướng nhanh ngay đầu trang Báo cáo lỗi
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B).copy(alpha = 0.28f)),
            border = BorderStroke(1.2.dp, Color(0xFF10B981))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "🛠️ Tự Động Sửa Lỗi Mod & Cứu Server 1 Chạm",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                    color = Color(0xFF34D399)
                )
                Text(
                    "Nếu Server bị lỗi nạp Mod / xung đột phiên bản (FormattedException), bấm nút dưới để MineHost tự tải bù thư viện thiếu (Fabric API...) và tự tạm tắt các Mod lệch phiên bản Minecraft:",
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp
                )
                if (modDoctorBusy && !modDoctorProgress.isNullOrBlank()) {
                    Text(
                        modDoctorProgress.orEmpty(),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                }
                lastModDoctorReport?.takeIf { it.isNotBlank() }?.let { rep ->
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            rep,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFA7F3D0),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Button(
                    onClick = { vm.autoFixModCrashAndRestart(autoRestart = true) },
                    enabled = !modDoctorBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (modDoctorBusy) "⏳ Đang tự động sửa lỗi Mod..." else "🛠️ Tự Động Sửa Lỗi Mod & Bật Lại Server Ngay",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onGoToModManager != null) {
                        OutlinedButton(
                            onClick = onGoToModManager,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🧩 Quản Lý Mod", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (onGoToDiagnostics != null) {
                        OutlinedButton(
                            onClick = onGoToDiagnostics,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🩺 Chẩn Đoán", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✅ Không có báo cáo lỗi sập nào", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(stringResource(R.string.crash_empty), color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reports, key = { it.path }, contentType = { "crash-report" }) { report ->
                    val reportAnalysis = remember(report.preview) { CrashReportAnalyzer.analyze(report.preview) }
                    val label = reportAnalysis.primaryLabel
                    val javaIssue = reportAnalysis.findings.any { it.label.startsWith("Java") }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.readCrashReport(report.fileName) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        border = BorderStroke(1.dp, Coral.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Warning, null, tint = Coral, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        label,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp,
                                        color = Coral,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${report.fileName} • ${report.createdText} • ${report.sizeText}",
                                        fontSize = 11.sp,
                                        color = Muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (javaIssue) {
                                    TextButton(onClick = onGoToJavaInstall) {
                                        Text(
                                            stringResource(R.string.env_go_install),
                                            color = Indigo,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { vm.deleteCrashReport(report.fileName) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Xóa báo cáo",
                                        tint = Muted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            reportAnalysis.findings.firstOrNull()?.suggestion?.takeIf { it.isNotBlank() }?.let { sug ->
                                Surface(
                                    color = Color(0xFF065F46).copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "💡 Gợi ý sửa: $sug",
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF34D399),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class CrashViewMode { Summary, RawReport, RunLog }

@Composable
fun CrashReportDialog(
    content: String,
    analysis: CrashReportAnalyzer.Analysis,
    runLog: String,
    onDismiss: () -> Unit,
    vm: McViewModel? = null,
    onGoToJavaInstall: (() -> Unit)? = null,
    onGoToModManager: (() -> Unit)? = null,
    onGoToDiagnostics: (() -> Unit)? = null,
    onGoToProperties: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(CrashViewMode.Summary) }
    val localizedContent = remember(content) { CrashReportManager.localizeLegacyChineseText(content) }
    val localizedRunLog = remember(runLog) { CrashReportManager.localizeLegacyChineseText(runLog) }
    val javaIssue = analysis.findings.any { it.label.startsWith("Java") }
    val modIssue = analysis.modConflictDetails.isNotEmpty() || analysis.findings.any {
        it.label.contains("Mod", ignoreCase = true) ||
            it.label.contains("Mixin", ignoreCase = true) ||
            it.label.contains("Plugin", ignoreCase = true) ||
            it.label.contains("thư viện", ignoreCase = true)
    }
    val portIssue = analysis.findings.any { it.label.contains("cổng", ignoreCase = true) }
    val envIssue = analysis.findings.any {
        it.label.contains("quyền", ignoreCase = true) ||
            it.label.contains("fontconfig", ignoreCase = true) ||
            it.label.contains(".so", ignoreCase = true)
    }
    val modDoctorBusy = vm?.modDoctorBusy?.collectAsState()?.value ?: false
    val modDoctorProgress = vm?.modDoctorProgress?.collectAsState()?.value
    val lastModDoctorReport = vm?.lastModDoctorReport?.collectAsState()?.value
    val summary = remember(analysis) {
        buildString {
            appendLine("【Tóm tắt nguyên nhân】 ${analysis.primaryLabel}")
            analysis.exitCode?.let { appendLine("• Mã thoát tiến trình (Exit code): $it") }
            if (analysis.components.isNotEmpty()) {
                appendLine("• Thành phần liên quan: ${analysis.components.joinToString(", ")}")
            }
            if (analysis.title.isNotBlank()) {
                appendLine("• Ngoại lệ chính: ${analysis.title}")
            }
            if (analysis.modConflictDetails.isNotEmpty()) {
                appendLine("• Chi tiết Mod xung đột / thiếu thư viện:")
                analysis.modConflictDetails.forEach { appendLine("  $it") }
            }
            analysis.causedBy.forEach { appendLine("  ↳ Caused by: $it") }
            appendLine()
            analysis.findings.forEachIndexed { idx, f ->
                appendLine("${idx + 1}. [${f.label}] ${f.detail}")
                if (f.evidence.isNotBlank()) {
                    appendLine("   - Bằng chứng: ${f.evidence}")
                }
                appendLine("   👉 Cách khắc phục: ${f.suggestion}")
                appendLine()
            }
        }.trim()
    }
    val displayedText = when (mode) {
        CrashViewMode.Summary -> summary
        CrashViewMode.RawReport -> localizedContent
        CrashViewMode.RunLog -> localizedRunLog.ifBlank { stringResource(R.string.crash_run_log_empty) }
    }
    val title = when (mode) {
        CrashViewMode.Summary -> stringResource(R.string.crash_analysis_title)
        CrashViewMode.RawReport -> stringResource(R.string.crash_raw_title)
        CrashViewMode.RunLog -> stringResource(R.string.crash_run_log_title)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 490.dp)) {
                Text(
                    stringResource(R.string.crash_share_hint),
                    color = Coral,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                SegmentedTabs(
                    options = listOf(
                        stringResource(R.string.crash_summary),
                        stringResource(R.string.crash_raw),
                        stringResource(R.string.crash_run_log)
                    ),
                    selectedIndex = mode.ordinal,
                    onSelect = { index -> CrashViewMode.entries.getOrNull(index)?.let { mode = it } }
                )
                Spacer(Modifier.height(10.dp))

                if (mode == CrashViewMode.Summary) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Thẻ tổng quan mã thoát & thành phần gây lỗi
                        Surface(
                            color = Coral.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Coral.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "⚠️ Chẩn đoán nhanh: ${analysis.primaryLabel}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Coral
                                )
                                analysis.exitCode?.let { code ->
                                    val codeHint = when (code) {
                                        137 -> " (SIGKILL — Hệ điều hành buộc dừng do tràn RAM hoặc giới hạn chạy nền)"
                                        1 -> " (Lỗi cấu hình / Ngoại lệ Java khi khởi chạy)"
                                        126, 127 -> " (Thiếu quyền thực thi hoặc chưa cài đặt gói môi trường)"
                                        else -> ""
                                    }
                                    Text(
                                        "• Mã thoát tiến trình: $code$codeHint",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (analysis.components.isNotEmpty()) {
                                    Text(
                                        "• Thành phần nghi vấn trong nhật ký: ${analysis.components.joinToString(", ")}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24)
                                    )
                                }
                            }
                        }

                        // Hộp Công Cụ Tự Động Sửa Lỗi & Dẫn Đường 1 Chạm Ngay Trong Báo Cáo
                        if (vm != null) {
                            Surface(
                                color = Color(0xFF064E3B).copy(alpha = 0.28f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text(
                                        "🛠️ Tự Động Khắc Phục & Dẫn Đường Sửa Lỗi Ngay:",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF34D399)
                                    )
                                    if (modDoctorBusy && !modDoctorProgress.isNullOrBlank()) {
                                        Text(
                                            modDoctorProgress,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24)
                                        )
                                    }
                                    lastModDoctorReport?.takeIf { it.isNotBlank() }?.let { rep ->
                                        Surface(
                                            color = Color(0xFF0F172A),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                rep,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.5.sp,
                                                color = Color(0xFFA7F3D0),
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { vm.autoFixModCrashAndRestart(autoRestart = true) },
                                        enabled = !modDoctorBusy,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF059669),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            if (modDoctorBusy) "⏳ Đang tự sửa lỗi Mod..."
                                            else "🛠️ Tự Động Sửa Lỗi Mod & Bật Lại Server (1 Chạm)",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { vm.autoFixModCrashAndRestart(autoRestart = false) },
                                            enabled = !modDoctorBusy,
                                            shape = RoundedCornerShape(9.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("🔧 Chỉ Sửa Mod", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        if (onGoToModManager != null) {
                                            OutlinedButton(
                                                onClick = onGoToModManager,
                                                shape = RoundedCornerShape(9.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("🧩 Mở Quản Lý Mod", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    if (javaIssue && onGoToJavaInstall != null) {
                                        Button(
                                            onClick = onGoToJavaInstall,
                                            colors = ButtonDefaults.buttonColors(containerColor = Indigo, contentColor = Color.White),
                                            shape = RoundedCornerShape(9.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("☕ Dẫn Tới Cài Đặt Môi Trường Java Ngay", fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                                        }
                                    }
                                    if (envIssue && onGoToDiagnostics != null) {
                                        OutlinedButton(
                                            onClick = onGoToDiagnostics,
                                            shape = RoundedCornerShape(9.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("🩺 Dẫn Tới Chẩn Đoán & Tự Sửa Môi Trường", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        }
                                    }
                                    if (portIssue && onGoToProperties != null) {
                                        OutlinedButton(
                                            onClick = onGoToProperties,
                                            shape = RoundedCornerShape(9.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("🔌 Dẫn Tới Đổi Cổng Server (server.properties)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Danh sách các phát hiện & cách khắc phục trực quan
                        analysis.findings.forEachIndexed { idx, finding ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "🔍 Nguyên nhân ${idx + 1}: ${finding.label}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        finding.detail,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    )
                                    if (finding.evidence.isNotBlank()) {
                                        Surface(
                                            color = Color(0xFF0F172A),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "Bằng chứng: ${finding.evidence}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.sp,
                                                color = Color(0xFFE2E8F0),
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        color = Color(0xFF065F46).copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.55f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "💡 Cách khắc phục: ${finding.suggestion}",
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34D399),
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Chuỗi ngoại lệ (Exception & Caused by)
                        if (analysis.firstException != null || analysis.causedBy.isNotEmpty()) {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "📋 Chuỗi ngoại lệ Java (Exception / Caused by):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF94A3B8)
                                    )
                                    analysis.firstException?.let { firstEx ->
                                        Text(
                                            firstEx,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            color = Color(0xFFFCA5A5)
                                        )
                                    }
                                    analysis.causedBy.forEach { cause ->
                                        Text(
                                            "↳ Caused by: $cause",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            color = Color(0xFFFDE047)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                    ) {
                        Text(
                            displayedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (javaIssue && onGoToJavaInstall != null) {
                    TextButton(onClick = onGoToJavaInstall) {
                        Text(stringResource(R.string.env_go_install), color = Indigo, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { copy(context, displayedText) }, modifier = Modifier.size(McIcon.Touch)) {
                    Icon(Icons.Outlined.ContentCopy, stringResource(R.string.crash_copy), tint = Indigo, modifier = Modifier.size(McIcon.Item))
                }
                val shareTitle = stringResource(R.string.crash_share)
                val fullText = buildString {
                    appendLine(summary)
                    appendLine()
                    appendLine("==========================================")
                    appendLine(localizedContent)
                    if (localizedRunLog.isNotBlank()) {
                        appendLine()
                        appendLine("--- ${stringResource(R.string.crash_run_log_title)} ---")
                        append(localizedRunLog)
                    }
                }
                IconButton(
                    onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, fullText)
                        }, shareTitle))
                    },
                    modifier = Modifier.size(McIcon.Touch)
                ) {
                    Icon(Icons.Outlined.IosShare, stringResource(R.string.crash_share_full), tint = Indigo, modifier = Modifier.size(McIcon.Item))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.s620), fontWeight = FontWeight.Bold) }
            }
        }
    )
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("Crash report", text))
}
