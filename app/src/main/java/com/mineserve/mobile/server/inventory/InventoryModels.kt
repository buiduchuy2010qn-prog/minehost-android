package com.mineserve.mobile.server.inventory

import com.mineserve.mobile.data.ServerCore
import com.mineserve.mobile.server.PlayerManager
import kotlinx.serialization.Serializable

/**
 * Nguồn dữ liệu thực tế của ảnh chụp túi đồ người chơi.
 * Tuyệt đối không dùng dữ liệu mẫu hoặc lấy lịch sử/cache cũ giả làm dữ liệu trực tuyến.
 */
@Serializable
enum class InventorySourceMode(val labelVi: String, val isLiveOnline: Boolean) {
    LIVE_ONLINE_CONSOLE_BRIDGE("Trực tuyến (Truy vấn trực tiếp từ bộ nhớ Server)", true),
    LIVE_ONLINE_LOCAL_BRIDGE("Trực tuyến (Qua cầu nối Local Loopback/Plugin)", true),
    LAST_SAVED_NBT_SNAPSHOT("Bản lưu gần nhất trên đĩa (playerdata/.dat)", false),
    OFFLINE_NBT_FILE("Ngoại tuyến (Đọc trực tiếp từ tệp playerdata/.dat)", false),
    UNSUPPORTED("Core này chưa hỗ trợ đọc túi đồ", false)
}

/**
 * Các khu vực ô trong túi đồ theo chuẩn giao diện Minecraft.
 */
@Serializable
enum class InventorySection(val labelVi: String) {
    HOTBAR("Thanh công cụ nhanh (Hotbar 9 ô)"),
    MAIN("Túi đồ chính (27 ô)"),
    ARMOR("Trang bị giáp (4 ô)"),
    OFFHAND("Tay phụ (Offhand)"),
    ENDER_CHEST("Rương Ender (27 ô)")
}

/**
 * Khả năng hỗ trợ quản lý túi đồ của từng loại ServerCore (Phần 3 & Phần 10).
 */
data class InventoryCoreSupport(
    val core: ServerCore,
    val canViewOnline: Boolean,
    val canEditOnline: Boolean,
    val canViewOffline: Boolean,
    val canEditOffline: Boolean,
    val requiresPluginOrModForOnline: Boolean,
    val requiredAddonHintVi: String?,
    val summaryVi: String,
    val limitationsVi: String
)

/**
 * Thông tin một phù phép (Enchantment) gắn trên vật phẩm.
 */
@Serializable
data class ItemEnchantment(
    val id: String,
    val level: Int
) {
    val displayNameVi: String
        get() {
            val clean = id.removePrefix("minecraft:").lowercase()
            val nameVi = ENCHANTMENT_NAMES_VI[clean] ?: clean.replace('_', ' ').replaceFirstChar { it.uppercase() }
            return "$nameVi ${toRoman(level)}"
        }

    companion object {
        private val ENCHANTMENT_NAMES_VI = mapOf(
            "sharpness" to "Sắc bén",
            "smite" to "Trừng phạt",
            "bane_of_arthropods" to "Khắc tinh chân đốt",
            "knockback" to "Bật lùi",
            "fire_aspect" to "Khía cạnh lửa",
            "looting" to "Nhặt nhạnh",
            "sweeping" to "Quét kiếm",
            "sweeping_edge" to "Quét kiếm",
            "efficiency" to "Hiệu suất",
            "silk_touch" to "Chạm nhẹ (Silk Touch)",
            "unbreaking" to "Chậm hỏng",
            "fortune" to "Gia tài",
            "power" to "Sức mạnh",
            "punch" to "Đấm bật",
            "flame" to "Mũi tên lửa",
            "infinity" to "Vô tận",
            "protection" to "Bảo vệ",
            "fire_protection" to "Chống lửa",
            "feather_falling" to "Rơi nhẹ",
            "blast_protection" to "Chống nổ",
            "projectile_protection" to "Chống đạn",
            "respiration" to "Hô hấp dưới nước",
            "aqua_affinity" to "Đào nhanh dưới nước",
            "thorns" to "Gai phản sát thương",
            "depth_strider" to "Sải bước dưới sâu",
            "frost_walker" to "Chân băng",
            "binding_curse" to "Lời nguyền ràng buộc",
            "vanishing_curse" to "Lời nguyền biến mất",
            "mending" to "Sửa chữa (Mending)",
            "loyalty" to "Trung thành",
            "impaling" to "Đâm xuyên",
            "riptide" to "Dòng xoáy",
            "channeling" to "Dẫn sét",
            "multishot" to "Bắn đa tên",
            "quick_charge" to "Nạp nhanh",
            "piercing" to "Xuyên thấu",
            "soul_speed" to "Tốc độ linh hồn",
            "swift_sneak" to "Rón rén nhanh",
            "density" to "Mật độ nặng",
            "breach" to "Phá giáp",
            "wind_burst" to "Nổ gió"
        )

        fun toRoman(n: Int): String = when (n) {
            1 -> "I"
            2 -> "II"
            3 -> "III"
            4 -> "IV"
            5 -> "V"
            6 -> "VI"
            7 -> "VII"
            8 -> "VIII"
            9 -> "IX"
            10 -> "X"
            else -> n.toString()
        }
    }
}

/**
 * Mô hình dữ liệu trung gian đại diện cho 1 ô vật phẩm trong túi đồ hoặc rương Ender.
 * Bảo toàn đầy đủ slot index, item ID, số lượng, độ bền, tên tùy chỉnh, lore, phù phép
 * và dữ liệu NBT/SNBT gốc (để không làm mất metadata của vật phẩm Mod hoặc vật phẩm đặc biệt).
 */
@Serializable
data class InventoryItemStack(
    val id: String,
    val count: Int,
    val slot: Int,
    val section: InventorySection = sectionForNbtSlot(slot),
    val damage: Int? = null,
    val maxDamage: Int? = null,
    val customName: String? = null,
    val lore: List<String> = emptyList(),
    val enchantments: List<ItemEnchantment> = emptyList(),
    val unbreakable: Boolean = false,
    /** Chuỗi SNBT của `tag` (1.13–1.20.4) hoặc `components` (1.20.5+) để tái tạo lệnh chính xác */
    val rawSnbtMetadata: String? = null,
    /** Bản mã hóa Base64 của TagCompound nhị phân gốc để chỉnh sửa offline không mất tag của Mod */
    val rawNbtCompoundBase64: String? = null,
    /** Các thuộc tính bổ sung mà Core/Mod cung cấp */
    val extraMetadata: Map<String, String> = emptyMap()
) {
    val cleanId: String
        get() = id.removePrefix("minecraft:")

    val displayNameVi: String
        get() {
            if (!customName.isNullOrBlank()) return customName
            return MinecraftItemCatalog.displayNameVi(id)
        }

    val isModdedItem: Boolean
        get() = id.contains(':') && !id.startsWith("minecraft:", ignoreCase = true)

    val resolvedMaxDamage: Int?
        get() = maxDamage ?: MinecraftItemCatalog.defaultMaxDurability(id)

    val durabilityFraction: Float?
        get() {
            val dmg = damage ?: return null
            val max = resolvedMaxDamage ?: return null
            if (max <= 0) return null
            return ((max - dmg).coerceIn(0, max)).toFloat() / max.toFloat()
        }

    companion object {
        fun sectionForNbtSlot(slot: Int): InventorySection = when (slot) {
            in 0..8 -> InventorySection.HOTBAR
            in 9..35 -> InventorySection.MAIN
            in 100..103 -> InventorySection.ARMOR
            -106, 40 -> InventorySection.OFFHAND
            else -> InventorySection.MAIN
        }

        /**
         * Ánh xạ từ (section, slot) sang tên slot chuẩn trong lệnh `/item replace entity` hoặc `/replaceitem`.
         */
        fun toCommandSlotName(section: InventorySection, slot: Int): String? = when (section) {
            InventorySection.HOTBAR -> if (slot in 0..8) "hotbar.$slot" else null
            InventorySection.MAIN -> if (slot in 9..35) "inventory.${slot - 9}" else null
            InventorySection.ARMOR -> when (slot) {
                103 -> "armor.head"
                102 -> "armor.chest"
                101 -> "armor.legs"
                100 -> "armor.feet"
                else -> null
            }
            InventorySection.OFFHAND -> "weapon.offhand"
            InventorySection.ENDER_CHEST -> if (slot in 0..26) "enderchest.$slot" else null
        }

        fun slotLabelVi(section: InventorySection, slot: Int): String = when (section) {
            InventorySection.HOTBAR -> "Hotbar Ô ${slot + 1} (Slot $slot)"
            InventorySection.MAIN -> "Túi đồ chính Ô ${slot - 8} (Slot $slot)"
            InventorySection.ARMOR -> when (slot) {
                103 -> "Giáp Đầu / Mũ (Slot 103)"
                102 -> "Giáp Ngực / Áo (Slot 102)"
                101 -> "Giáp Chân / Quần (Slot 101)"
                100 -> "Giáp Giày / Ủng (Slot 100)"
                else -> "Ô Giáp (Slot $slot)"
            }
            InventorySection.OFFHAND -> "Tay phụ / Offhand (Slot -106)"
            InventorySection.ENDER_CHEST -> "Rương Ender Ô ${slot + 1} (Slot $slot)"
        }
    }
}

/**
 * Ảnh chụp đầy đủ túi đồ của một người chơi tại một thời điểm xác định.
 */
@Serializable
data class PlayerInventorySnapshot(
    val serverDirName: String,
    val serverCore: ServerCore,
    val mcVersion: String,
    val playerName: String,
    val playerUuid: String? = null,
    val playerXuid: String? = null,
    val playerEdition: PlayerManager.PlayerEdition = PlayerManager.PlayerEdition.UNKNOWN,
    val isPlayerOnline: Boolean,
    val sourceMode: InventorySourceMode,
    val capturedAtMs: Long,
    val capturedAtText: String,
    val isStale: Boolean = false,
    val staleReason: String? = null,
    val selectedHotbarSlot: Int = 0,
    /** 9 ô Hotbar (index 0..8 -> slot 0..8) */
    val hotbar: List<InventoryItemStack?> = List(9) { null },
    /** 27 ô Túi đồ chính (index 0..26 -> slot 9..35) */
    val mainInventory: List<InventoryItemStack?> = List(27) { null },
    /** 4 ô Giáp: 103=Mũ, 102=Áo, 101=Quần, 100=Giày */
    val armorHead: InventoryItemStack? = null,
    val armorChest: InventoryItemStack? = null,
    val armorLegs: InventoryItemStack? = null,
    val armorFeet: InventoryItemStack? = null,
    /** Ô tay phụ (Offhand, slot -106) */
    val offhand: InventoryItemStack? = null,
    /** 27 ô Rương Ender (index 0..26 -> slot 0..26) */
    val enderChest: List<InventoryItemStack?> = List(27) { null },
    val offhandSupported: Boolean = true,
    val enderChestSupported: Boolean = true,
    val canEdit: Boolean = false,
    val editDisabledReason: String? = null,
    val statusNoticeVi: String? = null
) {
    val totalOccupiedSlots: Int
        get() = hotbar.count { it != null } +
            mainInventory.count { it != null } +
            listOfNotNull(armorHead, armorChest, armorLegs, armorFeet, offhand).size

    val enderOccupiedSlots: Int
        get() = enderChest.count { it != null }

    fun itemAt(section: InventorySection, slot: Int): InventoryItemStack? = when (section) {
        InventorySection.HOTBAR -> hotbar.getOrNull(slot)
        InventorySection.MAIN -> mainInventory.getOrNull(slot - 9)
        InventorySection.ARMOR -> when (slot) {
            103 -> armorHead
            102 -> armorChest
            101 -> armorLegs
            100 -> armorFeet
            else -> null
        }
        InventorySection.OFFHAND -> offhand
        InventorySection.ENDER_CHEST -> enderChest.getOrNull(slot)
    }
}

/**
 * Nhật ký kiểm toán (Audit Log) cho mọi thao tác chỉnh sửa túi đồ (Phần 6).
 */
@Serializable
data class InventoryAuditLogEntry(
    val timestamp: String,
    val serverDirName: String,
    val playerName: String,
    val playerUuid: String? = null,
    val actionType: String,
    val targetSlotLabel: String,
    val detailVi: String,
    val verifiedByServer: Boolean
)

/**
 * Danh mục tên tiếng Việt, độ bền tối đa mặc định và phân loại biểu tượng cho vật phẩm Minecraft.
 */
object MinecraftItemCatalog {

    enum class VisualCategory {
        SWORD, PICKAXE, AXE, SHOVEL, HOE, BOW, CROSSBOW, TRIDENT, MACE, SHIELD,
        HELMET, CHESTPLATE, LEGGINGS, BOOTS, ELYTRA, TOTEM, APPLE, POTION, BOOK,
        PEARL, INGOT, GEM, BLOCK, WOOD, FOOD, REDSTONE, BUCKET, SPAWN_EGG, GENERIC
    }

    data class CommonItemOption(
        val id: String,
        val nameVi: String,
        val maxStack: Int = 64
    )

    val QUICK_ADD_ITEMS: List<CommonItemOption> = listOf(
        CommonItemOption("minecraft:diamond_sword", "Kiếm Kim Cương", 1),
        CommonItemOption("minecraft:netherite_sword", "Kiếm Netherite", 1),
        CommonItemOption("minecraft:diamond_pickaxe", "Cúp Kim Cương", 1),
        CommonItemOption("minecraft:netherite_pickaxe", "Cúp Netherite", 1),
        CommonItemOption("minecraft:diamond_axe", "Rìu Kim Cương", 1),
        CommonItemOption("minecraft:bow", "Cung Tên", 1),
        CommonItemOption("minecraft:shield", "Khiên Chắn", 1),
        CommonItemOption("minecraft:totem_of_undying", "Vật Tổ Trường Sinh (Totem)", 1),
        CommonItemOption("minecraft:elytra", "Cánh Cứng (Elytra)", 1),
        CommonItemOption("minecraft:golden_apple", "Táo Vàng", 64),
        CommonItemOption("minecraft:enchanted_golden_apple", "Táo Vàng Phù Phép", 64),
        CommonItemOption("minecraft:cooked_beef", "Bít Tết (Thịt bò chín)", 64),
        CommonItemOption("minecraft:golden_carrot", "Cà Rốt Vàng", 64),
        CommonItemOption("minecraft:diamond", "Kim Cương", 64),
        CommonItemOption("minecraft:netherite_ingot", "Phôi Netherite", 64),
        CommonItemOption("minecraft:emerald", "Ngọc Lục Bảo", 64),
        CommonItemOption("minecraft:iron_ingot", "Phôi Sắt", 64),
        CommonItemOption("minecraft:gold_ingot", "Phôi Vàng", 64),
        CommonItemOption("minecraft:ender_pearl", "Ngọc Ender", 16),
        CommonItemOption("minecraft:firework_rocket", "Pháo Hoa Bay", 64),
        CommonItemOption("minecraft:arrow", "Mũi Tên", 64),
        CommonItemOption("minecraft:torch", "Đuốc Sáng", 64),
        CommonItemOption("minecraft:oak_log", "Gỗ Sồi", 64),
        CommonItemOption("minecraft:cobblestone", "Đá Cuội", 64),
        CommonItemOption("minecraft:water_bucket", "Xô Nước", 1),
        CommonItemOption("minecraft:lava_bucket", "Xô Dung Nham", 1),
        CommonItemOption("minecraft:diamond_helmet", "Mũ Kim Cương", 1),
        CommonItemOption("minecraft:diamond_chestplate", "Áo Giáp Kim Cương", 1),
        CommonItemOption("minecraft:diamond_leggings", "Quần Giáp Kim Cương", 1),
        CommonItemOption("minecraft:diamond_boots", "Giày Kim Cương", 1),
        CommonItemOption("minecraft:netherite_helmet", "Mũ Netherite", 1),
        CommonItemOption("minecraft:netherite_chestplate", "Áo Giáp Netherite", 1),
        CommonItemOption("minecraft:netherite_leggings", "Quần Giáp Netherite", 1),
        CommonItemOption("minecraft:netherite_boots", "Giày Netherite", 1)
    )

    private val ITEM_NAMES_VI = QUICK_ADD_ITEMS.associate { it.id.removePrefix("minecraft:") to it.nameVi } + mapOf(
        "wooden_sword" to "Kiếm Gỗ",
        "stone_sword" to "Kiếm Đá",
        "iron_sword" to "Kiếm Sắt",
        "golden_sword" to "Kiếm Vàng",
        "wooden_pickaxe" to "Cúp Gỗ",
        "stone_pickaxe" to "Cúp Đá",
        "iron_pickaxe" to "Cúp Sắt",
        "golden_pickaxe" to "Cúp Vàng",
        "wooden_axe" to "Rìu Gỗ",
        "stone_axe" to "Rìu Đá",
        "iron_axe" to "Rìu Sắt",
        "golden_axe" to "Rìu Vàng",
        "netherite_axe" to "Rìu Netherite",
        "wooden_shovel" to "Xẻng Gỗ",
        "stone_shovel" to "Xẻng Đá",
        "iron_shovel" to "Xẻng Sắt",
        "golden_shovel" to "Xẻng Vàng",
        "diamond_shovel" to "Xẻng Kim Cương",
        "netherite_shovel" to "Xẻng Netherite",
        "wooden_hoe" to "Cuốc Gỗ",
        "stone_hoe" to "Cuốc Đá",
        "iron_hoe" to "Cuốc Sắt",
        "golden_hoe" to "Cuốc Vàng",
        "diamond_hoe" to "Cuốc Kim Cương",
        "netherite_hoe" to "Cuốc Netherite",
        "iron_helmet" to "Mũ Sắt",
        "iron_chestplate" to "Áo Giáp Sắt",
        "iron_leggings" to "Quần Giáp Sắt",
        "iron_boots" to "Giày Sắt",
        "golden_helmet" to "Mũ Vàng",
        "golden_chestplate" to "Áo Giáp Vàng",
        "golden_leggings" to "Quần Giáp Vàng",
        "golden_boots" to "Giày Vàng",
        "leather_helmet" to "Mũ Da",
        "leather_chestplate" to "Áo Da",
        "leather_leggings" to "Quần Da",
        "leather_boots" to "Giày Da",
        "chainmail_helmet" to "Mũ Xích",
        "chainmail_chestplate" to "Áo Giáp Xích",
        "chainmail_leggings" to "Quần Giáp Xích",
        "chainmail_boots" to "Giày Xích",
        "turtle_helmet" to "Mũ Mai Rùa",
        "trident" to "Đinh Ba",
        "crossbow" to "Nỏ",
        "mace" to "Chùy Trọng Lực (Mace)",
        "fishing_rod" to "Cần Câu Cá",
        "flint_and_steel" to "Dụng Cụ Đánh Lửa",
        "shears" to "Kéo Cắt",
        "bread" to "Bánh Mì",
        "apple" to "Táo Đỏ",
        "cooked_porkchop" to "Thịt Heo Nướng",
        "cooked_chicken" to "Thịt Gà Nướng",
        "cooked_mutton" to "Thịt Cừu Nướng",
        "baked_potato" to "Khoai Tây Nướng",
        "ender_chest" to "Rương Ender",
        "chest" to "Rương Gỗ",
        "shulker_box" to "Hộp Shulker",
        "crafting_table" to "Bàn Chế Tạo",
        "furnace" to "Lò Nung",
        "anvil" to "Đe Rèn",
        "enchanting_table" to "Bàn Phù Phép",
        "enchanted_book" to "Sách Phù Phép",
        "book" to "Quyển Sách",
        "experience_bottle" to "Chai Kinh Nghiệm",
        "potion" to "Thuốc Pha Chế",
        "splash_potion" to "Thuốc Ném",
        "lingering_potion" to "Thuốc Lưu Hương",
        "dirt" to "Đất",
        "grass_block" to "Khối Cỏ",
        "stone" to "Đá",
        "obsidian" to "Hắc Diện Thạch",
        "bedrock" to "Đá Nền (Bedrock)",
        "tnt" to "Thuốc Nổ TNT",
        "redstone" to "Bột Đá Đỏ",
        "coal" to "Than Đá",
        "lapis_lazuli" to "Ngọc Lưu Ly",
        "quartz" to "Thạch Anh Nether",
        "amethyst_shard" to "Mảnh Thạch Anh Tím",
        "nether_star" to "Sao Nether",
        "beacon" to "Đèn Hiệu (Beacon)",
        "bucket" to "Xô Rỗng",
        "milk_bucket" to "Xô Sữa",
        "compass" to "La Bàn",
        "clock" to "Đồng Hồ"
    )

    fun displayNameVi(rawId: String): String {
        val clean = rawId.trim().removePrefix("minecraft:").lowercase()
        ITEM_NAMES_VI[clean]?.let { return it }
        val namespace = if (rawId.contains(':') && !rawId.startsWith("minecraft:", true)) {
            "[${rawId.substringBefore(':')}] "
        } else ""
        val base = rawId.substringAfter(':')
            .split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        return (namespace + base).ifBlank { rawId }
    }

    fun defaultMaxDurability(rawId: String): Int? {
        val clean = rawId.trim().removePrefix("minecraft:").lowercase()
        return when {
            clean == "elytra" -> 432
            clean == "shield" -> 336
            clean == "bow" -> 384
            clean == "crossbow" -> 465
            clean == "trident" -> 250
            clean == "mace" -> 500
            clean == "fishing_rod" -> 64
            clean == "flint_and_steel" -> 64
            clean == "shears" -> 238
            clean.startsWith("netherite_") -> when {
                clean.endsWith("_helmet") -> 407
                clean.endsWith("_chestplate") -> 592
                clean.endsWith("_leggings") -> 555
                clean.endsWith("_boots") -> 481
                else -> 2031
            }
            clean.startsWith("diamond_") -> when {
                clean.endsWith("_helmet") -> 363
                clean.endsWith("_chestplate") -> 528
                clean.endsWith("_leggings") -> 495
                clean.endsWith("_boots") -> 429
                else -> 1561
            }
            clean.startsWith("iron_") -> when {
                clean.endsWith("_helmet") -> 165
                clean.endsWith("_chestplate") -> 240
                clean.endsWith("_leggings") -> 225
                clean.endsWith("_boots") -> 195
                else -> 250
            }
            clean.startsWith("golden_") -> when {
                clean.endsWith("_helmet") -> 77
                clean.endsWith("_chestplate") -> 112
                clean.endsWith("_leggings") -> 105
                clean.endsWith("_boots") -> 91
                else -> 32
            }
            clean.startsWith("stone_") -> 131
            clean.startsWith("wooden_") -> 59
            else -> null
        }
    }

    fun visualCategory(rawId: String): VisualCategory {
        val clean = rawId.trim().substringAfter(':').lowercase()
        return when {
            clean.endsWith("_sword") -> VisualCategory.SWORD
            clean.endsWith("_pickaxe") -> VisualCategory.PICKAXE
            clean.endsWith("_axe") -> VisualCategory.AXE
            clean.endsWith("_shovel") -> VisualCategory.SHOVEL
            clean.endsWith("_hoe") -> VisualCategory.HOE
            clean == "bow" -> VisualCategory.BOW
            clean == "crossbow" -> VisualCategory.CROSSBOW
            clean == "trident" -> VisualCategory.TRIDENT
            clean == "mace" -> VisualCategory.MACE
            clean == "shield" -> VisualCategory.SHIELD
            clean.endsWith("_helmet") || clean == "turtle_helmet" -> VisualCategory.HELMET
            clean.endsWith("_chestplate") -> VisualCategory.CHESTPLATE
            clean.endsWith("_leggings") -> VisualCategory.LEGGINGS
            clean.endsWith("_boots") -> VisualCategory.BOOTS
            clean == "elytra" -> VisualCategory.ELYTRA
            clean.contains("totem") -> VisualCategory.TOTEM
            clean.contains("apple") -> VisualCategory.APPLE
            clean.contains("potion") || clean == "experience_bottle" -> VisualCategory.POTION
            clean.contains("book") -> VisualCategory.BOOK
            clean.contains("pearl") || clean.contains("eye") -> VisualCategory.PEARL
            clean.endsWith("_ingot") || clean == "netherite_scrap" -> VisualCategory.INGOT
            clean in setOf("diamond", "emerald", "lapis_lazuli", "quartz", "amethyst_shard", "coal") -> VisualCategory.GEM
            clean.contains("bucket") -> VisualCategory.BUCKET
            clean.endsWith("_spawn_egg") -> VisualCategory.SPAWN_EGG
            clean.contains("log") || clean.contains("planks") || clean.contains("wood") -> VisualCategory.WOOD
            clean.contains("beef") || clean.contains("pork") || clean.contains("chicken") ||
                clean.contains("bread") || clean.contains("carrot") || clean.contains("potato") ||
                clean.contains("stew") || clean.contains("fish") || clean.contains("cod") ||
                clean.contains("salmon") -> VisualCategory.FOOD
            clean.contains("redstone") || clean == "tnt" || clean == "repeater" || clean == "comparator" -> VisualCategory.REDSTONE
            clean.contains("stone") || clean.contains("dirt") || clean.contains("block") ||
                clean.contains("ore") || clean.contains("sand") || clean.contains("obsidian") ||
                clean.contains("chest") || clean.contains("table") || clean.contains("furnace") -> VisualCategory.BLOCK
            else -> VisualCategory.GENERIC
        }
    }

    /**
     * Màu vật liệu chủ đạo để vẽ pixel-art chuẩn chất liệu Minecraft (Gỗ, Đá, Sắt, Vàng, Kim cương, Netherite, Ngọc...).
     */
    fun materialColorArgb(rawId: String): Int {
        val clean = rawId.trim().substringAfter(':').lowercase()
        return when {
            clean.startsWith("netherite_") || clean.contains("obsidian") -> 0xFF4A3B4C.toInt()
            clean.startsWith("diamond_") || clean == "diamond" -> 0xFF38BDF8.toInt()
            clean.startsWith("golden_") || clean.startsWith("gold_") || clean.contains("golden") -> 0xFFFACC15.toInt()
            clean.startsWith("iron_") || clean == "shears" || clean == "bucket" -> 0xFFE2E8F0.toInt()
            clean.startsWith("stone_") || clean.contains("cobble") || clean == "stone" -> 0xFF94A3B8.toInt()
            clean.startsWith("wooden_") || clean.contains("oak") || clean.contains("log") || clean.contains("chest") -> 0xFFB45309.toInt()
            clean.startsWith("leather_") -> 0xFF9A3412.toInt()
            clean.startsWith("chainmail_") -> 0xFF64748B.toInt()
            clean.contains("emerald") || clean.contains("turtle") || clean.contains("slime") -> 0xFF10B981.toInt()
            clean.contains("ender") || clean.contains("shulker") || clean.contains("amethyst") || clean.contains("enchant") -> 0xFFA855F7.toInt()
            clean.contains("redstone") || clean == "tnt" || clean.contains("apple") || clean.contains("lava") -> 0xFFEF4444.toInt()
            clean.contains("water") || clean.contains("lapis") || clean == "trident" -> 0xFF3B82F6.toInt()
            clean.contains("totem") -> 0xFFEAB308.toInt()
            else -> 0xFF6366F1.toInt()
        }
    }
}
