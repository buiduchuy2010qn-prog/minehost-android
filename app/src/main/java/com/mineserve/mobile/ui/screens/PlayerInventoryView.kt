package com.mineserve.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mineserve.mobile.server.PlayerManager
import com.mineserve.mobile.server.inventory.InventoryCoreSupport
import com.mineserve.mobile.server.inventory.InventoryItemStack
import com.mineserve.mobile.server.inventory.InventorySection
import com.mineserve.mobile.server.inventory.InventorySourceMode
import com.mineserve.mobile.server.inventory.MinecraftItemCatalog
import com.mineserve.mobile.server.inventory.PlayerInventorySnapshot
import com.mineserve.mobile.ui.theme.Coral
import com.mineserve.mobile.ui.theme.CoralSoft
import com.mineserve.mobile.ui.theme.Indigo
import com.mineserve.mobile.ui.theme.IndigoSoft
import com.mineserve.mobile.ui.theme.McShape
import com.mineserve.mobile.ui.theme.Mint
import com.mineserve.mobile.ui.theme.MintSoft
import com.mineserve.mobile.ui.theme.Muted

data class SelectedInventorySlot(
    val section: InventorySection,
    val slot: Int
)

@Composable
fun PlayerInventoryTabContent(
    playerName: String,
    playerUuid: String?,
    playerXuid: String?,
    support: InventoryCoreSupport,
    snapshot: PlayerInventorySnapshot?,
    isLoading: Boolean,
    isMutating: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onUpdateSlot: (InventorySection, Int, InventoryItemStack?, String) -> Unit,
    onMoveOrSwapSlot: (InventorySection, Int, InventorySection, Int) -> Unit
) {
    var selectedSlot by remember(playerName) {
        mutableStateOf<SelectedInventorySlot?>(SelectedInventorySlot(InventorySection.HOTBAR, 0))
    }
    var pendingMoveSource by remember(playerName) {
        mutableStateOf<SelectedInventorySlot?>(null)
    }
    var showEnderChest by remember { mutableStateOf(false) }
    var showSetItemDialog by remember { mutableStateOf(false) }
    var showClearSlotConfirm by remember { mutableStateOf(false) }
    var showChangeCountDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── 1. Thanh trạng thái Core Adapter & Nguồn dữ liệu ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(IndigoSoft)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val editionBadge = when (snapshot?.playerEdition) {
                        PlayerManager.PlayerEdition.BEDROCK -> "BEDROCK"
                        PlayerManager.PlayerEdition.JAVA -> "JAVA"
                        else -> if (!playerXuid.isNullOrBlank() || playerName.startsWith(".")) "BEDROCK" else "JAVA"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (editionBadge == "BEDROCK") MintSoft else Indigo.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            editionBadge,
                            color = if (editionBadge == "BEDROCK") Mint else Indigo,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = snapshot?.sourceMode?.labelVi ?: support.summaryVi,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Indigo,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val effUuid = snapshot?.playerUuid ?: playerUuid
                val effXuid = snapshot?.playerXuid ?: playerXuid
                if (!effUuid.isNullOrBlank()) {
                    Text("UUID: $effUuid", color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!effXuid.isNullOrBlank()) {
                    Text("XUID (Xbox/Floodgate): $effXuid", color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (snapshot != null) {
                    Text(
                        "Cập nhật lúc: ${snapshot.capturedAtText} • Đang chứa ${snapshot.totalOccupiedSlots}/41 ô",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
            }
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading && !isMutating
            ) {
                if (isLoading || isMutating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Indigo)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Làm mới túi đồ", tint = Indigo)
                }
            }
        }

        // ── 2. Cảnh báo dữ liệu cũ (Stale) hoặc Lỗi ──
        if (snapshot?.isStale == true && !snapshot.staleReason.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CoralSoft)
                    .padding(8.dp)
            ) {
                Text(
                    "⚠️ ${snapshot.staleReason}",
                    color = Coral,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CoralSoft)
                    .padding(10.dp)
            ) {
                Column {
                    Text("❌ Không thể đọc/cập nhật túi đồ", color = Coral, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(errorMessage, color = Coral, fontSize = 10.sp)
                }
            }
        }

        // Nếu Core hoàn toàn không hỗ trợ (ví dụ Proxy Velocity/BungeeCord, Allay)
        if (!support.canViewOnline && !support.canViewOffline) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CoralSoft)
                    .padding(12.dp)
            ) {
                Column {
                    Text("🚫 Chưa hỗ trợ túi đồ trên ${support.core.displayName}", color = Coral, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(support.limitationsVi, color = Muted, fontSize = 11.sp)
                }
            }
            return@Column
        }

        if (snapshot == null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp, color = Indigo)
                        Spacer(Modifier.height(8.dp))
                        Text("Đang đọc dữ liệu túi đồ thật từ Server...", color = Muted, fontSize = 11.sp)
                    }
                } else {
                    OutlinedButton(onClick = onRefresh, shape = McShape.ControlCompact) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tải túi đồ của $playerName", fontSize = 11.sp)
                    }
                }
            }
            return@Column
        }

        // Hướng dẫn khi đang ở chế độ chọn ô đích để Chuyển / Hoán đổi
        pendingMoveSource?.let { src ->
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MintSoft)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = Mint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Đang chọn chuyển từ ${InventoryItemStack.slotLabelVi(src.section, src.slot)}. Hãy chạm vào 1 ô bất kỳ bên dưới để chuyển/hoán đổi!",
                    color = Mint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { pendingMoveSource = null }) {
                    Text("Hủy", color = Coral, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        val handleSlotClick: (InventorySection, Int) -> Unit = { sec, sl ->
            val moveSrc = pendingMoveSource
            if (moveSrc != null) {
                pendingMoveSource = null
                selectedSlot = SelectedInventorySlot(sec, sl)
                onMoveOrSwapSlot(moveSrc.section, moveSrc.slot, sec, sl)
            } else {
                selectedSlot = SelectedInventorySlot(sec, sl)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── 3. Hàng Trang bị Giáp (4 ô) + Tay phụ Offhand (1 ô) ──
        Text(
            "🛡️ Giáp & Tay phụ • Ô Hotbar đang cầm: #${snapshot.selectedHotbarSlot + 1}",
            color = Indigo,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E222D))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val armorSlots = listOf(
                Triple(103, "Mũ", snapshot.armorHead),
                Triple(102, "Áo", snapshot.armorChest),
                Triple(101, "Quần", snapshot.armorLegs),
                Triple(100, "Giày", snapshot.armorFeet)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                armorSlots.forEach { (slotNum, placeholder, item) ->
                    MinecraftInventorySlotBox(
                        item = item,
                        placeholderLabel = placeholder,
                        isSelected = selectedSlot == SelectedInventorySlot(InventorySection.ARMOR, slotNum),
                        isActiveHotbarSlot = false,
                        isMoveSource = pendingMoveSource == SelectedInventorySlot(InventorySection.ARMOR, slotNum),
                        slotSize = 36.dp,
                        onClick = { handleSlotClick(InventorySection.ARMOR, slotNum) }
                    )
                }
            }
            if (snapshot.offhandSupported) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tay phụ", color = Color(0xFF94A3B8), fontSize = 9.sp, modifier = Modifier.padding(end = 4.dp))
                    MinecraftInventorySlotBox(
                        item = snapshot.offhand,
                        placeholderLabel = "Khiên",
                        isSelected = selectedSlot == SelectedInventorySlot(InventorySection.OFFHAND, -106),
                        isActiveHotbarSlot = false,
                        isMoveSource = pendingMoveSource == SelectedInventorySlot(InventorySection.OFFHAND, -106),
                        slotSize = 36.dp,
                        onClick = { handleSlotClick(InventorySection.OFFHAND, -106) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 4. Lưới Túi đồ chính (27 ô: 3 hàng x 9 cột, slot 9..35) + Thanh Hotbar (9 ô: slot 0..8) ──
        Text(
            "🎒 Túi đồ chính (27 ô) & Thanh công cụ Hotbar (9 ô)",
            color = Indigo,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E222D))
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 3 hàng túi đồ chính (slot 9..17, 18..26, 27..35)
            for (row in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (col in 0 until 9) {
                        val slotIndex = 9 + row * 9 + col
                        val item = snapshot.mainInventory.getOrNull(slotIndex - 9)
                        MinecraftInventorySlotBox(
                            item = item,
                            placeholderLabel = "${slotIndex}",
                            isSelected = selectedSlot == SelectedInventorySlot(InventorySection.MAIN, slotIndex),
                            isActiveHotbarSlot = false,
                            isMoveSource = pendingMoveSource == SelectedInventorySlot(InventorySection.MAIN, slotIndex),
                            slotSize = 31.dp,
                            onClick = { handleSlotClick(InventorySection.MAIN, slotIndex) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(5.dp))

            // 1 hàng Hotbar (slot 0..8)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until 9) {
                    val item = snapshot.hotbar.getOrNull(col)
                    MinecraftInventorySlotBox(
                        item = item,
                        placeholderLabel = "H${col + 1}",
                        isSelected = selectedSlot == SelectedInventorySlot(InventorySection.HOTBAR, col),
                        isActiveHotbarSlot = snapshot.selectedHotbarSlot == col,
                        isMoveSource = pendingMoveSource == SelectedInventorySlot(InventorySection.HOTBAR, col),
                        slotSize = 31.dp,
                        onClick = { handleSlotClick(InventorySection.HOTBAR, col) }
                    )
                }
            }
        }

        // ── 5. Khu vực Rương Ender (Ender Chest 27 ô) ──
        if (snapshot.enderChestSupported) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF261C35))
                    .clickable { showEnderChest = !showEnderChest }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🟣 Rương Ender (${snapshot.enderOccupiedSlots}/27 ô có vật phẩm)",
                    color = Color(0xFFD8B4FE),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (showEnderChest) "Thu gọn ▲" else "Mở rương ▼",
                    color = Color(0xFFC084FC),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (showEnderChest) {
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F162B))
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (row in 0 until 3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (col in 0 until 9) {
                                val slotIdx = row * 9 + col
                                val item = snapshot.enderChest.getOrNull(slotIdx)
                                MinecraftInventorySlotBox(
                                    item = item,
                                    placeholderLabel = "E${slotIdx + 1}",
                                    isSelected = selectedSlot == SelectedInventorySlot(InventorySection.ENDER_CHEST, slotIdx),
                                    isActiveHotbarSlot = false,
                                    isMoveSource = pendingMoveSource == SelectedInventorySlot(InventorySection.ENDER_CHEST, slotIdx),
                                    slotSize = 31.dp,
                                    onClick = { handleSlotClick(InventorySection.ENDER_CHEST, slotIdx) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 6. Khung Chi tiết Ô đang chọn & Thao tác Chỉnh sửa Có kiểm soát (Phần 2 & Phần 6) ──
        selectedSlot?.let { sel ->
            val currentItem = snapshot.itemAt(sel.section, sel.slot)
            Spacer(Modifier.height(10.dp))
            SelectedSlotDetailCard(
                section = sel.section,
                slot = sel.slot,
                item = currentItem,
                canEdit = snapshot.canEdit && !isMutating,
                editDisabledReason = snapshot.editDisabledReason,
                onStartMove = { pendingMoveSource = sel },
                onOpenSetItem = { showSetItemDialog = true },
                onOpenChangeCount = { showChangeCountDialog = true },
                onOpenClearConfirm = { showClearSlotConfirm = true }
            )
        }
    }

    // ── Hộp thoại Đặt / Thêm vật phẩm vào ô đang chọn ──
    val activeSel = selectedSlot
    if (showSetItemDialog && activeSel != null) {
        val existingItem = snapshot?.itemAt(activeSel.section, activeSel.slot)
        SetSlotItemDialog(
            slotLabel = InventoryItemStack.slotLabelVi(activeSel.section, activeSel.slot),
            initialItemId = existingItem?.id ?: "minecraft:diamond_sword",
            initialCount = existingItem?.count ?: 1,
            onDismiss = { showSetItemDialog = false },
            onConfirm = { itemId, count ->
                showSetItemDialog = false
                val newItem = InventoryItemStack(
                    id = itemId,
                    count = count,
                    slot = activeSel.slot,
                    section = activeSel.section
                )
                onUpdateSlot(activeSel.section, activeSel.slot, newItem, "Thêm/Đặt vật phẩm vào ô")
            }
        )
    }

    // ── Hộp thoại Chỉnh sửa số lượng vật phẩm ──
    if (showChangeCountDialog && activeSel != null) {
        val existingItem = snapshot?.itemAt(activeSel.section, activeSel.slot)
        if (existingItem != null) {
            ChangeItemCountDialog(
                item = existingItem,
                slotLabel = InventoryItemStack.slotLabelVi(activeSel.section, activeSel.slot),
                onDismiss = { showChangeCountDialog = false },
                onConfirmCount = { newCount ->
                    showChangeCountDialog = false
                    if (newCount <= 0) {
                        onUpdateSlot(activeSel.section, activeSel.slot, null, "Xóa vật phẩm (số lượng = 0)")
                    } else {
                        onUpdateSlot(
                            activeSel.section,
                            activeSel.slot,
                            existingItem.copy(count = newCount),
                            "Chỉnh số lượng vật phẩm"
                        )
                    }
                }
            )
        }
    }

    // ── Hộp thoại Xác nhận Xóa vật phẩm khỏi ô (Phần 6) ──
    if (showClearSlotConfirm && activeSel != null) {
        val existingItem = snapshot?.itemAt(activeSel.section, activeSel.slot)
        AlertDialog(
            onDismissRequest = { showClearSlotConfirm = false },
            title = { Text("Xác nhận xóa vật phẩm?", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Text(
                    "Bạn có chắc chắn muốn xóa「${existingItem?.displayNameVi ?: "vật phẩm"}」x${existingItem?.count ?: 1} tại ${InventoryItemStack.slotLabelVi(activeSel.section, activeSel.slot)} của $playerName không? Hành động này sẽ được thực thi trên Server và ghi vào nhật ký quản trị.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSlotConfirm = false
                        onUpdateSlot(activeSel.section, activeSel.slot, null, "Xóa vật phẩm khỏi ô")
                    }
                ) {
                    Text("Xác nhận xóa", color = Coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSlotConfirm = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun MinecraftInventorySlotBox(
    item: InventoryItemStack?,
    placeholderLabel: String,
    isSelected: Boolean,
    isActiveHotbarSlot: Boolean,
    isMoveSource: Boolean,
    slotSize: Dp,
    onClick: () -> Unit
) {
    val bgColor = when {
        isMoveSource -> Color(0xFF065F46)
        isSelected -> Color(0xFF334155)
        item != null -> Color(0xFF2B303C)
        else -> Color(0xFF161922)
    }
    val borderColor = when {
        isMoveSource -> Mint
        isSelected -> Color(0xFF38BDF8)
        isActiveHotbarSlot -> Color(0xFFFACC15)
        item?.enchantments?.isNotEmpty() == true -> Color(0xFFA855F7)
        else -> Color(0xFF3F4756)
    }
    val borderWidth = if (isSelected || isActiveHotbarSlot || isMoveSource) 1.8.dp else 1.dp

    Box(
        modifier = Modifier
            .size(slotSize)
            .clip(RoundedCornerShape(5.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(5.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (item == null) {
            Text(
                text = placeholderLabel,
                color = Color(0xFF475569),
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace
            )
        } else {
            MinecraftItemPixelIcon(
                itemId = item.id,
                hasEnchantments = item.enchantments.isNotEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
            // Chỉ báo vật phẩm có Phù phép (Enchanted)
            if (item.enchantments.isNotEmpty()) {
                Text(
                    text = "✨",
                    fontSize = 6.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(1.dp)
                )
            }
            // Số lượng vật phẩm ở góc dưới phải
            if (item.count > 1) {
                Text(
                    text = item.count.toString(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                )
            }
            // Thanh độ bền (Durability Bar) ở cạnh dưới cùng của ô
            val dur = item.durabilityFraction
            if (dur != null && dur < 1.0f) {
                val barColor = when {
                    dur > 0.5f -> Color(0xFF22C55E)
                    dur > 0.2f -> Color(0xFFEAB308)
                    else -> Color(0xFFEF4444)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF0F172A))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(dur.coerceIn(0.05f, 1f))
                            .height(2.dp)
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
fun MinecraftItemPixelIcon(
    itemId: String,
    hasEnchantments: Boolean,
    modifier: Modifier = Modifier
) {
    val category = remember(itemId) { MinecraftItemCatalog.visualCategory(itemId) }
    val primaryArgb = remember(itemId) { MinecraftItemCatalog.materialColorArgb(itemId) }

    if (category == MinecraftItemCatalog.VisualCategory.GENERIC) {
        // Phương án dự phòng trung thực (Phần 2): Hiển thị mã chữ viết tắt của ID vật phẩm, không dùng ảnh không liên quan
        val shortCode = remember(itemId) {
            itemId.substringAfter(':')
                .split('_')
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it.take(2).uppercase() }
                .ifBlank { "IT" }
        }
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color(primaryArgb).copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = shortCode,
                color = Color.White,
                fontSize = 7.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val grid = remember(category, primaryArgb, hasEnchantments) {
        buildItemPixelArt8x8(category, primaryArgb, hasEnchantments)
    }
    Canvas(modifier = modifier) {
        val cw = size.width / 8f
        val ch = size.height / 8f
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val argb = grid[r][c]
                if (argb != 0) {
                    drawRect(
                        color = Color(argb),
                        topLeft = Offset(c * cw, r * ch),
                        size = Size(cw + 0.4f, ch + 0.4f)
                    )
                }
            }
        }
    }
}

private fun buildItemPixelArt8x8(
    category: MinecraftItemCatalog.VisualCategory,
    M: Int,
    enchanted: Boolean
): Array<IntArray> {
    val _0 = 0
    val H = 0xFF78350F.toInt() // Gỗ cán cầm
    val G = if (enchanted) 0xFFC084FC.toInt() else 0xFFFFFFFF.toInt() // Viền sáng / Phù phép
    return when (category) {
        MinecraftItemCatalog.VisualCategory.SWORD -> arrayOf(
            intArrayOf(_0, _0, _0, _0, _0, _0, M, G),
            intArrayOf(_0, _0, _0, _0, _0, M, G, M),
            intArrayOf(_0, _0, _0, _0, M, G, M, _0),
            intArrayOf(_0, _0, _0, M, G, M, _0, _0),
            intArrayOf(_0, M, M, G, M, _0, _0, _0),
            intArrayOf(_0, _0, M, H, M, _0, _0, _0),
            intArrayOf(_0, H, _0, _0, M, _0, _0, _0),
            intArrayOf(H, _0, _0, _0, _0, _0, _0, _0)
        )
        MinecraftItemCatalog.VisualCategory.PICKAXE -> arrayOf(
            intArrayOf(_0, _0, M, M, M, G, _0, _0),
            intArrayOf(_0, M, _0, _0, H, M, M, _0),
            intArrayOf(_0, _0, _0, _0, H, _0, M, _0),
            intArrayOf(_0, _0, _0, H, _0, _0, M, _0),
            intArrayOf(_0, _0, H, _0, _0, _0, _0, _0),
            intArrayOf(_0, H, _0, _0, _0, _0, _0, _0),
            intArrayOf(H, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0)
        )
        MinecraftItemCatalog.VisualCategory.AXE -> arrayOf(
            intArrayOf(_0, _0, M, M, G, _0, _0, _0),
            intArrayOf(_0, M, M, M, H, M, _0, _0),
            intArrayOf(_0, M, M, H, _0, _0, _0, _0),
            intArrayOf(_0, _0, H, _0, _0, _0, _0, _0),
            intArrayOf(_0, H, _0, _0, _0, _0, _0, _0),
            intArrayOf(H, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0)
        )
        MinecraftItemCatalog.VisualCategory.HELMET -> arrayOf(
            intArrayOf(_0, _0, M, M, M, M, _0, _0),
            intArrayOf(_0, M, G, M, M, M, M, _0),
            intArrayOf(_0, M, M, M, M, M, M, _0),
            intArrayOf(_0, M, M, _0, _0, M, M, _0),
            intArrayOf(_0, M, _0, _0, _0, _0, M, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0)
        )
        MinecraftItemCatalog.VisualCategory.CHESTPLATE -> arrayOf(
            intArrayOf(_0, M, M, _0, _0, M, M, _0),
            intArrayOf(M, G, M, M, M, M, M, M),
            intArrayOf(M, M, M, M, M, M, M, M),
            intArrayOf(_0, M, M, M, M, M, M, _0),
            intArrayOf(_0, M, M, M, M, M, M, _0),
            intArrayOf(_0, _0, M, M, M, M, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0)
        )
        MinecraftItemCatalog.VisualCategory.LEGGINGS -> arrayOf(
            intArrayOf(_0, M, M, M, M, M, M, _0),
            intArrayOf(_0, M, G, M, M, M, M, _0),
            intArrayOf(_0, M, M, _0, _0, M, M, _0),
            intArrayOf(_0, M, M, _0, _0, M, M, _0),
            intArrayOf(_0, M, M, _0, _0, M, M, _0),
            intArrayOf(_0, M, M, _0, _0, M, M, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0)
        )
        MinecraftItemCatalog.VisualCategory.BOOTS -> arrayOf(
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, M, M, _0, _0, M, M, _0),
            intArrayOf(_0, M, G, _0, _0, M, M, _0),
            intArrayOf(M, M, M, _0, _0, M, M, M),
            intArrayOf(M, M, M, _0, _0, M, M, M),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0)
        )
        MinecraftItemCatalog.VisualCategory.SHIELD -> arrayOf(
            intArrayOf(_0, H, M, M, M, M, H, _0),
            intArrayOf(_0, M, H, H, H, H, M, _0),
            intArrayOf(_0, M, H, G, G, H, M, _0),
            intArrayOf(_0, M, H, G, G, H, M, _0),
            intArrayOf(_0, _0, M, H, H, M, _0, _0),
            intArrayOf(_0, _0, _0, M, M, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0)
        )
        else -> arrayOf(
            intArrayOf(_0, _0, M, M, M, M, _0, _0),
            intArrayOf(_0, M, G, G, M, M, M, _0),
            intArrayOf(M, G, M, M, M, M, M, M),
            intArrayOf(M, M, M, M, M, M, M, M),
            intArrayOf(M, M, M, M, M, M, M, M),
            intArrayOf(_0, M, M, M, M, M, M, _0),
            intArrayOf(_0, _0, M, M, M, M, _0, _0),
            intArrayOf(_0, _0, _0, _0, _0, _0, _0, _0)
        )
    }
}

@Composable
private fun SelectedSlotDetailCard(
    section: InventorySection,
    slot: Int,
    item: InventoryItemStack?,
    canEdit: Boolean,
    editDisabledReason: String?,
    onStartMove: () -> Unit,
    onOpenSetItem: () -> Unit,
    onOpenChangeCount: () -> Unit,
    onOpenClearConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(IndigoSoft)
            .padding(10.dp)
    ) {
        Text(
            text = "📍 ${InventoryItemStack.slotLabelVi(section, slot)}",
            color = Indigo,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(4.dp))

        if (item == null) {
            Text("Ô này hiện đang trống.", color = Muted, fontSize = 11.sp)
            if (canEdit) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenSetItem,
                    shape = McShape.ControlCompact,
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Thêm / Đặt vật phẩm vào ô này", color = Color.White, fontSize = 11.sp)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E222D))
                        .padding(4.dp)
                ) {
                    MinecraftItemPixelIcon(
                        itemId = item.id,
                        hasEnchantments = item.enchantments.isNotEmpty(),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.displayNameVi} ×${item.count}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "ID: ${item.id}",
                        color = Muted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (!item.customName.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("🏷️ Tên tùy chỉnh: ${item.customName}", fontSize = 10.sp, color = Indigo)
            }

            val maxDur = item.resolvedMaxDamage
            if (item.damage != null && maxDur != null && maxDur > 0) {
                val remaining = (maxDur - item.damage).coerceIn(0, maxDur)
                Spacer(Modifier.height(2.dp))
                Text(
                    "🔨 Độ bền: $remaining / $maxDur (Hao mòn: ${item.damage})${if (item.unbreakable) " • Không thể vỡ (Unbreakable)" else ""}",
                    fontSize = 10.sp,
                    color = Muted
                )
            } else if (item.unbreakable) {
                Spacer(Modifier.height(2.dp))
                Text("🛡️ Không thể vỡ (Unbreakable)", fontSize = 10.sp, color = Mint)
            }

            if (item.enchantments.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("✨ Phù phép (Enchantments):", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7C3AED))
                item.enchantments.forEach { ench ->
                    Text("  • ${ench.displayNameVi} (${ench.id})", fontSize = 10.sp, color = Color(0xFF6D28D9))
                }
            }

            if (item.lore.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("📜 Mô tả (Lore):", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Muted)
                item.lore.forEach { line ->
                    Text("  \"$line\"", fontSize = 10.sp, color = Muted)
                }
            }

            if (item.extraMetadata.isNotEmpty() || item.rawNbtCompoundBase64 != null || item.rawSnbtMetadata != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "🔒 Metadata / NBT gốc của vật phẩm được bảo toàn khi chuyển ô hoặc sửa số lượng.",
                    fontSize = 9.sp,
                    color = Mint
                )
            }

            if (canEdit) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onStartMove,
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⇄ Chuyển ô", fontSize = 10.sp, color = Indigo)
                    }
                    OutlinedButton(
                        onClick = onOpenChangeCount,
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔢 Số lượng", fontSize = 10.sp, color = Indigo)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSetItem,
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✏️ Đổi vật phẩm", fontSize = 10.sp, color = Indigo)
                    }
                    OutlinedButton(
                        onClick = onOpenClearConfirm,
                        shape = McShape.ControlCompact,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = Coral, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Xóa ô", fontSize = 10.sp, color = Coral)
                    }
                }
            }
        }

        if (!canEdit && !editDisabledReason.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = Muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(editDisabledReason, color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SetSlotItemDialog(
    slotLabel: String,
    initialItemId: String,
    initialCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var itemId by remember { mutableStateOf(initialItemId) }
    var countText by remember { mutableStateOf(initialCount.coerceIn(1, 64).toString()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCatalog = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) MinecraftItemCatalog.QUICK_ADD_ITEMS
        else MinecraftItemCatalog.QUICK_ADD_ITEMS.filter {
            it.nameVi.lowercase().contains(q) || it.id.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đặt vật phẩm • $slotLabel", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = itemId,
                    onValueChange = { itemId = it },
                    label = { Text("ID Vật phẩm (VD: minecraft:diamond_sword hoặc mod:item)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Số lượng (1 – 64)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Tìm nhanh vật phẩm phổ biến...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    filteredCatalog.take(6).forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    itemId = opt.id
                                    val cur = countText.toIntOrNull() ?: 1
                                    if (cur > opt.maxStack) countText = opt.maxStack.toString()
                                }
                                .padding(vertical = 5.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(20.dp)) {
                                MinecraftItemPixelIcon(itemId = opt.id, hasEnchantments = false, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(opt.nameVi, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(opt.id, fontSize = 9.sp, color = Muted, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val parsedCount = (countText.toIntOrNull() ?: 1).coerceIn(1, 64)
            TextButton(
                onClick = { onConfirm(itemId.trim(), parsedCount) },
                enabled = itemId.isNotBlank()
            ) {
                Text("Áp dụng lên Server", color = Indigo, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun ChangeItemCountDialog(
    item: InventoryItemStack,
    slotLabel: String,
    onDismiss: () -> Unit,
    onConfirmCount: (Int) -> Unit
) {
    var count by remember { mutableStateOf(item.count.coerceIn(1, 64)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Số lượng • ${item.displayNameVi}", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(slotLabel, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { count = (count - 1).coerceAtLeast(1) }) { Text("-1") }
                    Text(
                        text = count.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Indigo
                    )
                    OutlinedButton(onClick = { count = (count + 1).coerceAtMost(64) }) { Text("+1") }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(1, 16, 32, 64).forEach { preset ->
                        TextButton(onClick = { count = preset }) {
                            Text("×$preset", color = Indigo, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmCount(count) }) {
                Text("Lưu số lượng", color = Indigo, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
