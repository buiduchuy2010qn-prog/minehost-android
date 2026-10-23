package com.mineserve.mobile.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mineserve.mobile.R
import com.mineserve.mobile.ui.BackBar
import com.mineserve.mobile.ui.McCard
import com.mineserve.mobile.ui.McViewModel
import com.mineserve.mobile.ui.MinecraftArtKind
import com.mineserve.mobile.ui.MinecraftPixelArtIcon
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.FieldGray
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.McSpacing
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerIconScreen(vm: McViewModel, onBack: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val props by vm.serverProperties.collectAsStateWithLifecycle()
    val version by vm.serverIconVersion.collectAsState()

    LaunchedEffect(config.activeCoreName) {
        vm.loadServerProperties()
    }

    val activeCore = config.installedCores.firstOrNull { it.name == config.activeCoreName }
    var serverDisplayName by remember(config.activeCoreName) {
        mutableStateOf(config.activeCoreName.orEmpty())
    }
    var motdText by remember(props["motd"]) {
        mutableStateOf(props["motd"] ?: "MineHost Server — Chơi cùng bạn bè!")
    }
    var selectedArtKind by remember { mutableStateOf<MinecraftArtKind?>(null) }
    val isNameAndMotdSaved = serverDisplayName.trim() == config.activeCoreName.orEmpty().trim() &&
        motdText == (props["motd"] ?: "MineHost Server — Chơi cùng bạn bè!")

    val bitmap by produceState<Bitmap?>(null, version, config.activeCoreName) {
        value = null
        value = withContext(Dispatchers.IO) {
            vm.serverIconFile()?.let { file ->
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = 1
                })
            }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let(vm::setServerIcon)
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("🎨 Đổi Tên, Mô Tả & Ảnh Đại Diện Server", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            // 1. Khung xem trước kiểu Danh Sách Máy Chủ Minecraft (Server List Preview)
            McCard(title = "🖥️ Xem Trước Trong Danh Sách Server Minecraft") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0D1318))
                        .border(1.dp, Indigo.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "server-icon.png",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        MinecraftPixelArtIcon(kind = MinecraftArtKind.GRASS, size = 64.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                serverDisplayName.ifBlank { "MineHost Server" },
                                color = Color(0xFFF3F6F8),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "20/20 📶",
                                color = Mint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            motdText.ifBlank { "MineHost Server" },
                            color = Color(0xFFA8B6C6),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${activeCore?.core?.displayName ?: "Fabric"} ${activeCore?.version ?: "1.21.11"} · server-icon.png (64×64 PNG)",
                            color = Indigo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. Đổi tên hiển thị & Mô tả MOTD
            McCard(title = "✏️ Đổi Tên Hiển Thị & Mô Tả Máy Chủ (MOTD)") {
                Text(
                    "Đổi tên hiển thị giúp bạn dễ phân biệt các server trong MineHost mà không làm mất thế giới hay cài đặt. Dòng MOTD sẽ hiển thị trực tiếp trong danh sách Multiplayer của người chơi.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = serverDisplayName,
                    onValueChange = { serverDisplayName = it },
                    label = { Text("Tên hiển thị Server trong MineHost", fontSize = 11.sp) },
                    placeholder = { Text("Ví dụ: Sinh Tồn 56 Mod Cùng Bạn Bè", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = motdText,
                    onValueChange = { motdText = it },
                    label = { Text("Mô tả Server trong game (MOTD)", fontSize = 11.sp) },
                    placeholder = { Text("Chào mừng đến với thế giới Minecraft!", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.renameAndDescribeActiveServer(serverDisplayName, motdText) },
                    enabled = activeCore != null && serverDisplayName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isNameAndMotdSaved) Mint else Indigo),
                    shape = McShape.ControlCompact,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isNameAndMotdSaved) "✅ Đã Lưu Tên Hiển Thị & Mô Tả MOTD" else "💾 Lưu Tên Hiển Thị & Mô Tả MOTD",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 3. Chọn từ 8 Biểu Tượng Pixel Art Minecraft có sẵn hoặc Ảnh từ Điện Thoại
            McCard(title = "🖼️ Chọn Ảnh Đại Diện Server (Tự chuẩn hóa 64×64 PNG)") {
                Text(
                    "Chạm vào một biểu tượng Minecraft bên dưới để đặt làm `server-icon.png` (64×64) ngay lập tức, hoặc chọn ảnh bất kỳ từ thư viện điện thoại (ứng dụng sẽ tự động cắt vuông và thu phóng chuẩn 64×64 PNG):",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MinecraftArtKind.entries.forEach { kind ->
                        val isChosen = selectedArtKind == kind
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isChosen) Mint.copy(alpha = 0.16f) else FieldGray)
                                .border(1.5.dp, if (isChosen) Mint else Indigo.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedArtKind = kind
                                    vm.setServerIconFromMinecraftArt(kind)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MinecraftPixelArtIcon(kind = kind, size = 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    if (isChosen) "✅ ${kind.titleVi}" else kind.titleVi,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChosen) Mint else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (isChosen) "Đang dùng Icon này" else kind.subtitleVi,
                                    fontSize = 9.sp,
                                    color = if (isChosen) Mint else Muted
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { launcher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📱 Chọn Ảnh Điện Thoại", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = vm::removeServerIcon,
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.ui_server_icon_reset), color = Coral, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
