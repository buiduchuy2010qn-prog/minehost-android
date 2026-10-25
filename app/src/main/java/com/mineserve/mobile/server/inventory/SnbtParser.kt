package com.mineserve.mobile.server.inventory

/**
 * Bộ phân tích cú pháp SNBT (Stringified Named Binary Tag) từ đầu ra Console của Minecraft Java Server
 * (`/data get entity <player> Inventory`, `EnderItems`, `SelectedItemSlot`, `equipment`)
 * và bộ tạo lệnh `/item replace entity` / `/replaceitem` chống tiêm lệnh (Command Injection).
 */
object SnbtParser {

    private val SAFE_PLAYER_NAME_REGEX = Regex("^[.A-Za-z0-9_\\-]{1,32}$")
    private val SAFE_ITEM_ID_REGEX = Regex("^[a-z0-9_\\-./]+:[a-z0-9_\\-./]+$|^[a-z0-9_\\-./]+$", RegexOption.IGNORE_CASE)
    private val ANSI_ESCAPE_REGEX = Regex("\\u001B\\[[0-9;?]*[ -/]*[@-~]|\\u001B\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)")
    private val MC_SECTION_COLOR_REGEX = Regex("§[0-9A-FK-ORXa-fk-orx]")

    private val ENTITY_DATA_LINE_REGEX = Regex(
        "(?:\\]:\\s*|INFO\\]:\\s*|^)([.A-Za-z0-9_\\-]{1,32})\\s+has the following entity data:\\s*(.+)$"
    )
    private val NO_ENTITY_FOUND_REGEX = Regex(
        "(?:No entity was found|Entity .+ cannot be found|No player was found)",
        RegexOption.IGNORE_CASE
    )
    private val NO_ELEMENTS_MATCHING_REGEX = Regex(
        "Found no elements matching\\s+(Inventory|EnderItems|SelectedItemSlot|equipment)",
        RegexOption.IGNORE_CASE
    )
    private val REPLACED_SLOT_SUCCESS_REGEX = Regex(
        "(?:Replaced a slot on|Replaced slot|Gave \\d+|Cleared the inventory of)",
        RegexOption.IGNORE_CASE
    )

    fun stripAnsiAndFormatting(raw: String): String {
        if (raw.isEmpty()) return raw
        var cleaned = raw
        if (cleaned.indexOf('\u001B') >= 0) {
            cleaned = ANSI_ESCAPE_REGEX.replace(cleaned, "")
        }
        if (cleaned.indexOf('§') >= 0) {
            cleaned = MC_SECTION_COLOR_REGEX.replace(cleaned, "")
        }
        return cleaned
    }

    fun isValidPlayerCommandTarget(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() &&
            !trimmed.contains('\n') &&
            !trimmed.contains('\r') &&
            !trimmed.contains(';') &&
            SAFE_PLAYER_NAME_REGEX.matches(trimmed)
    }

    fun isValidItemId(itemId: String): Boolean {
        val trimmed = itemId.trim()
        return trimmed.isNotEmpty() &&
            trimmed.length <= 128 &&
            !trimmed.contains('\n') &&
            !trimmed.contains('\r') &&
            !trimmed.contains(' ') &&
            SAFE_ITEM_ID_REGEX.matches(trimmed)
    }

    fun normalizeItemId(itemId: String): String {
        val trimmed = itemId.trim().lowercase()
        require(isValidItemId(trimmed)) { "ID vật phẩm không hợp lệ: '$itemId'" }
        return if (trimmed.contains(':')) trimmed else "minecraft:$trimmed"
    }

    data class ConsoleEntityDataMatch(
        val playerName: String,
        val snbtPayload: String
    )

    fun matchEntityDataLine(line: String): ConsoleEntityDataMatch? {
        val cleanLine = stripAnsiAndFormatting(line)
        if (!cleanLine.contains("has the following entity data:")) return null
        val m = ENTITY_DATA_LINE_REGEX.find(cleanLine.trim()) ?: return null
        return ConsoleEntityDataMatch(
            playerName = m.groupValues[1].trim(),
            snbtPayload = m.groupValues[2].trim()
        )
    }

    fun isNoEntityFoundLine(line: String): Boolean =
        NO_ENTITY_FOUND_REGEX.containsMatchIn(stripAnsiAndFormatting(line))

    fun matchEmptyPathLine(line: String): String? =
        NO_ELEMENTS_MATCHING_REGEX.find(stripAnsiAndFormatting(line))?.groupValues?.getOrNull(1)

    fun isSlotMutationSuccessLine(line: String): Boolean =
        REPLACED_SLOT_SUCCESS_REGEX.containsMatchIn(stripAnsiAndFormatting(line))

    /**
     * Phân tích danh sách SNBT `[{Slot: 0b, id: "minecraft:...", ...}, ...]` thành danh sách `InventoryItemStack`.
     */
    fun parseItemListSnbt(
        snbtList: String,
        defaultSection: InventorySection? = null
    ): List<InventoryItemStack> {
        val trimmed = stripAnsiAndFormatting(snbtList).trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        val compoundStrings = splitTopLevelElements(inner)
        return compoundStrings.mapNotNull { compStr ->
            parseSingleItemCompoundSnbt(compStr, defaultSection)
        }
    }

    /**
     * Phân tích gói `equipment` của MC 1.21.5+ `{head: {...}, chest: {...}, legs: {...}, feet: {...}, offhand: {...}}`.
     */
    fun parseEquipmentCompoundSnbt(snbtCompound: String): List<InventoryItemStack> {
        val fields = parseCompoundTopLevelFields(stripAnsiAndFormatting(snbtCompound))
        if (fields.isEmpty()) return emptyList()
        val result = mutableListOf<InventoryItemStack>()
        val mapping = listOf(
            "head" to (103 to InventorySection.ARMOR),
            "chest" to (102 to InventorySection.ARMOR),
            "legs" to (101 to InventorySection.ARMOR),
            "feet" to (100 to InventorySection.ARMOR),
            "offhand" to (-106 to InventorySection.OFFHAND)
        )
        for ((key, pair) in mapping) {
            val rawComp = fields[key] ?: continue
            val (slot, section) = pair
            parseSingleItemCompoundSnbt(rawComp, section, forcedSlot = slot)?.let { result.add(it) }
        }
        return result
    }

    fun parseSingleItemCompoundSnbt(
        compoundStr: String,
        defaultSection: InventorySection? = null,
        forcedSlot: Int? = null
    ): InventoryItemStack? {
        val fields = parseCompoundTopLevelFields(stripAnsiAndFormatting(compoundStr))
        if (fields.isEmpty()) return null
        val rawId = unquoteSnbtString(fields["id"] ?: fields["Name"] ?: return null)
        if (rawId.isBlank() || rawId == "minecraft:air" || rawId == "air") return null

        val rawSlotParsed = forcedSlot ?: parseSnbtInt(fields["Slot"] ?: fields["slot"]) ?: 0
        val slot = if (rawSlotParsed == 150) -106 else rawSlotParsed
        val count = (parseSnbtInt(fields["count"] ?: fields["Count"]) ?: 1).coerceAtLeast(0)
        if (count <= 0) return null

        val section = defaultSection ?: InventoryItemStack.sectionForNbtSlot(slot)
        val rawTag = fields["tag"]
        val rawComponents = fields["components"]

        val tagFields = rawTag?.let(::parseCompoundTopLevelFields).orEmpty()
        val compFields = rawComponents?.let(::parseCompoundTopLevelFields).orEmpty()

        val damage = parseSnbtInt(compFields["minecraft:damage"] ?: compFields["\"minecraft:damage\""])
            ?: parseSnbtInt(tagFields["Damage"])
            ?: parseSnbtInt(fields["Damage"])?.takeIf { it > 0 }
        val maxDamage = parseSnbtInt(compFields["minecraft:max_damage"] ?: compFields["\"minecraft:max_damage\""])
        val unbreakable = compFields.containsKey("minecraft:unbreakable") ||
            compFields.containsKey("\"minecraft:unbreakable\"") ||
            parseSnbtInt(tagFields["Unbreakable"]) == 1 ||
            tagFields["Unbreakable"] == "true"

        val customName = extractSnbtCustomName(tagFields, compFields)
        val lore = extractSnbtLore(tagFields, compFields)
        val enchantments = extractSnbtEnchantments(tagFields, compFields)

        val extraMeta = linkedMapOf<String, String>()
        if (rawComponents != null) {
            extraMeta["Định dạng"] = "Data Components (1.20.5+)"
            val knownComps = setOf(
                "minecraft:damage",
                "minecraft:max_damage",
                "minecraft:enchantments",
                "minecraft:stored_enchantments",
                "minecraft:custom_name",
                "minecraft:item_name",
                "minecraft:lore",
                "minecraft:unbreakable"
            )
            val customComps = compFields.keys.filter { it !in knownComps }
            if (customComps.isNotEmpty()) {
                extraMeta["Components 1.20.5+"] = customComps.joinToString(", ")
            }
            compFields["minecraft:custom_data"]?.let { extraMeta["minecraft:custom_data"] = it }
        } else if (rawTag != null) {
            extraMeta["Định dạng"] = "NBT Tag"
            val knownTags = setOf("Damage", "Enchantments", "ench", "StoredEnchantments", "display", "Unbreakable")
            val customTags = tagFields.keys.filter { it !in knownTags }
            if (customTags.isNotEmpty()) {
                extraMeta["Tag NBT mở rộng"] = customTags.joinToString(", ")
            }
        }

        return InventoryItemStack(
            id = if (rawId.contains(':')) rawId else "minecraft:$rawId",
            count = count,
            slot = slot,
            section = section,
            damage = damage,
            maxDamage = maxDamage,
            customName = customName,
            lore = lore,
            enchantments = enchantments,
            unbreakable = unbreakable,
            rawSnbtMetadata = rawComponents ?: rawTag,
            extraMetadata = extraMeta
        )
    }

    private fun extractSnbtCustomName(
        tagFields: Map<String, String>,
        compFields: Map<String, String>
    ): String? {
        val rawCompName = compFields["minecraft:custom_name"]
            ?: compFields["\"minecraft:custom_name\""]
            ?: compFields["minecraft:item_name"]
            ?: compFields["\"minecraft:item_name\""]
        if (rawCompName != null) {
            return NbtCodec.stripJsonTextComponent(unquoteSnbtString(rawCompName))
        }
        val displayStr = tagFields["display"] ?: return null
        val displayFields = parseCompoundTopLevelFields(displayStr)
        val rawName = displayFields["Name"] ?: return null
        return NbtCodec.stripJsonTextComponent(unquoteSnbtString(rawName))
    }

    private fun extractSnbtLore(
        tagFields: Map<String, String>,
        compFields: Map<String, String>
    ): List<String> {
        val rawLoreList = compFields["minecraft:lore"]
            ?: compFields["\"minecraft:lore\""]
            ?: tagFields["display"]?.let(::parseCompoundTopLevelFields)?.get("Lore")
            ?: return emptyList()
        val trimmed = rawLoreList.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        return splitTopLevelElements(inner).map { NbtCodec.stripJsonTextComponent(unquoteSnbtString(it)) }
    }

    private fun extractSnbtEnchantments(
        tagFields: Map<String, String>,
        compFields: Map<String, String>
    ): List<ItemEnchantment> {
        val result = mutableListOf<ItemEnchantment>()
        // 1.13–1.20.4: Enchantments: [{id: "minecraft:sharpness", lvl: 5s}]
        val legacyKeys = listOf("Enchantments", "StoredEnchantments", "ench")
        for (k in legacyKeys) {
            val rawList = tagFields[k]?.trim() ?: continue
            if (rawList.startsWith("[") && rawList.endsWith("]")) {
                val inner = rawList.substring(1, rawList.length - 1).trim()
                if (inner.isNotEmpty()) {
                    for (elem in splitTopLevelElements(inner)) {
                        val ef = parseCompoundTopLevelFields(elem)
                        val id = ef["id"]?.let(::unquoteSnbtString) ?: continue
                        val lvl = parseSnbtInt(ef["lvl"]) ?: 1
                        result.add(ItemEnchantment(id = id, level = lvl))
                    }
                }
            }
        }
        // 1.20.5+: "minecraft:enchantments": {levels: {"minecraft:sharpness": 5}} hoặc {"minecraft:sharpness": 5}
        val compKeys = listOf(
            "minecraft:enchantments",
            "\"minecraft:enchantments\"",
            "minecraft:stored_enchantments",
            "\"minecraft:stored_enchantments\""
        )
        for (k in compKeys) {
            val rawComp = compFields[k] ?: continue
            val outer = parseCompoundTopLevelFields(rawComp)
            val levelsMap = outer["levels"]?.let(::parseCompoundTopLevelFields) ?: outer
            for ((rawEnchId, rawLvl) in levelsMap) {
                val cleanId = unquoteSnbtString(rawEnchId)
                if (cleanId == "show_in_tooltip" || cleanId == "levels") continue
                val lvl = parseSnbtInt(rawLvl) ?: continue
                result.add(ItemEnchantment(id = cleanId, level = lvl))
            }
        }
        return result
    }

    fun parseSnbtInt(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val clean = stripAnsiAndFormatting(raw).trim().trimEnd('b', 'B', 's', 'S', 'l', 'L')
        return clean.toIntOrNull()
    }

    fun unquoteSnbtString(raw: String): String {
        val t = stripAnsiAndFormatting(raw).trim()
        if (t.length >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            val quote = t.first()
            return t.substring(1, t.length - 1)
                .replace("\\$quote", quote.toString())
                .replace("\\\\", "\\")
        }
        return t
    }

    /**
     * Tách các phần tử cấp cao nhất của danh sách hoặc compound SNBT theo dấu phẩy `,`
     * (bỏ qua dấu phẩy nằm trong chuỗi `"..."`, `'...'`, `{...}`, `[...]`).
     */
    fun splitTopLevelElements(content: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var braceDepth = 0
        var bracketDepth = 0
        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false

        for (ch in content) {
            if (escaped) {
                sb.append(ch)
                escaped = false
                continue
            }
            if (ch == '\\' && (inDoubleQuote || inSingleQuote)) {
                sb.append(ch)
                escaped = true
                continue
            }
            when {
                ch == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                    sb.append(ch)
                }
                ch == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                    sb.append(ch)
                }
                !inDoubleQuote && !inSingleQuote -> {
                    when (ch) {
                        '{' -> {
                            braceDepth++
                            sb.append(ch)
                        }
                        '}' -> {
                            braceDepth = (braceDepth - 1).coerceAtLeast(0)
                            sb.append(ch)
                        }
                        '[' -> {
                            bracketDepth++
                            sb.append(ch)
                        }
                        ']' -> {
                            bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                            sb.append(ch)
                        }
                        ',' -> {
                            if (braceDepth == 0 && bracketDepth == 0) {
                                val token = sb.toString().trim()
                                if (token.isNotEmpty()) result.add(token)
                                sb.setLength(0)
                            } else {
                                sb.append(ch)
                            }
                        }
                        else -> sb.append(ch)
                    }
                }
                else -> sb.append(ch)
            }
        }
        val last = sb.toString().trim()
        if (last.isNotEmpty()) result.add(last)
        return result
    }

    /**
     * Phân tích một compound SNBT `{key1: val1, key2: val2}` thành Map các trường cấp 1.
     */
    fun parseCompoundTopLevelFields(compoundStr: String): Map<String, String> {
        val trimmed = compoundStr.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return emptyMap()
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyMap()
        // Bỏ tiền tố mảng kiểu [B; ...] hoặc [I; ...] nếu là mảng đặc biệt
        if (inner.startsWith("B;") || inner.startsWith("I;") || inner.startsWith("L;")) return emptyMap()

        val entries = splitTopLevelElements(inner)
        val map = LinkedHashMap<String, String>()
        for (entry in entries) {
            val colonIdx = findTopLevelColon(entry)
            if (colonIdx <= 0) continue
            val rawKey = entry.substring(0, colonIdx).trim()
            val cleanKey = unquoteSnbtString(rawKey)
            val rawVal = entry.substring(colonIdx + 1).trim()
            map[cleanKey] = rawVal
        }
        return map
    }

    private fun findTopLevelColon(entry: String): Int {
        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false
        for (i in entry.indices) {
            val ch = entry[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && (inDoubleQuote || inSingleQuote)) {
                escaped = true
                continue
            }
            when {
                ch == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                ch == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
                ch == ':' && !inDoubleQuote && !inSingleQuote -> return i
            }
        }
        return -1
    }

    /**
     * Kiểm tra định dạng SNBT trả về có khớp với loại truy vấn đang chờ hay không
     * (tránh nhầm dòng log trễ của pha trước sang pha tiếp theo).
     */
    fun matchesExpectedPhasePayload(expectedPhase: String, snbtPayload: String): Boolean {
        val t = snbtPayload.trim()
        return when (expectedPhase) {
            "SelectedItemSlot" -> parseSnbtInt(t) != null
            "Inventory", "EnderItems" -> t.startsWith("[") && t.endsWith("]")
            "equipment" -> t.startsWith("{") && t.endsWith("}")
            else -> true
        }
    }

    /**
     * Tạo lệnh sao chép nguyên bản vật phẩm giữa 2 ô trực tiếp trong bộ nhớ Server (MC >= 1.17):
     * `item replace entity <player> <toSlotName> from entity <player> <fromSlotName>`
     * Giúp di chuyển vật phẩm sang ô trống mà bảo toàn 100% NBT / Data Components / Mod Capabilities.
     */
    fun buildSlotCopyCommand(
        mcVersion: String,
        playerName: String,
        fromSection: InventorySection,
        fromSlot: Int,
        toSection: InventorySection,
        toSlot: Int
    ): String? {
        if (isMcVersionBelow117(mcVersion)) return null
        require(isValidPlayerCommandTarget(playerName)) {
            "Tên người chơi không hợp lệ để thực thi lệnh túi đồ: '$playerName'"
        }
        val fromSlotName = InventoryItemStack.toCommandSlotName(fromSection, fromSlot)
            ?: throw IllegalArgumentException("Ô nguồn không hợp lệ: $fromSection / slot $fromSlot")
        val toSlotName = InventoryItemStack.toCommandSlotName(toSection, toSlot)
            ?: throw IllegalArgumentException("Ô đích không hợp lệ: $toSection / slot $toSlot")
        return "item replace entity $playerName $toSlotName from entity $playerName $fromSlotName"
    }

    /**
     * Tạo lệnh console an toàn để đặt hoặc xóa vật phẩm tại một ô cụ thể của người chơi đang online.
     * - MC >= 1.20.5: `item replace entity <player> <slotName> with <itemId>[comp1=val1,...] <count>`
     * - MC 1.17–1.20.4: `item replace entity <player> <slotName> with <itemId>{tag} <count>`
     * - MC 1.13–1.16.5: `replaceitem entity <player> <slotName> <itemId> <count> [tag]` (hoặc `<itemId>{tag} <count>`)
     * Nếu vật phẩm có dữ liệu NBT nhị phân / Mod phức tạp không thể mã hóa không mất mát thành lệnh console,
     * hàm sẽ chủ động chặn và ném lỗi rõ ràng thay vì âm thầm ghi đè bằng vật phẩm trắng.
     */
    fun buildSlotReplaceCommand(
        mcVersion: String,
        playerName: String,
        section: InventorySection,
        slot: Int,
        item: InventoryItemStack?
    ): String {
        require(isValidPlayerCommandTarget(playerName)) {
            "Tên người chơi không hợp lệ để thực thi lệnh túi đồ: '$playerName'"
        }
        val slotName = InventoryItemStack.toCommandSlotName(section, slot)
            ?: throw IllegalArgumentException("Ô túi đồ không hợp lệ: $section / slot $slot")
        val useLegacyReplaceItem = isMcVersionBelow117(mcVersion)
        if (item == null || item.count <= 0 || item.id == "minecraft:air") {
            return if (useLegacyReplaceItem) {
                "replaceitem entity $playerName $slotName minecraft:air 1"
            } else {
                "item replace entity $playerName $slotName with minecraft:air 1"
            }
        }
        val normalizedId = normalizeItemId(item.id)
        val safeCount = item.count.coerceIn(1, 64)
        val metadataSuffix = serializeOnlineItemMetadataSuffix(mcVersion, item)
        return if (useLegacyReplaceItem) {
            "replaceitem entity $playerName $slotName $normalizedId$metadataSuffix $safeCount"
        } else {
            "item replace entity $playerName $slotName with $normalizedId$metadataSuffix $safeCount"
        }
    }

    /**
     * Chuyển đổi metadata của `InventoryItemStack` sang hậu tố lệnh `<id>{tag}` (1.13–1.20.4)
     * hoặc `<id>[k1=v1, k2=v2]` (1.20.5+).
     * Chặn ngay nếu phát hiện metadata nhị phân/mod không thể biểu diễn an toàn qua lệnh console.
     */
    fun serializeOnlineItemMetadataSuffix(mcVersion: String, item: InventoryItemStack): String {
        val hasStructuredMeta = (item.damage != null && item.damage > 0) ||
            !item.customName.isNullOrBlank() ||
            item.lore.isNotEmpty() ||
            item.enchantments.isNotEmpty() ||
            item.unbreakable
        val hasBinaryOnlyCustomMeta = item.rawNbtCompoundBase64 != null &&
            item.rawSnbtMetadata.isNullOrBlank() &&
            (item.extraMetadata.isNotEmpty() || item.isModdedItem)

        check(!hasBinaryOnlyCustomMeta) {
            "Không thể sửa trực tuyến vật phẩm '${item.id}' có dữ liệu NBT nhị phân/Mod mở rộng khi chưa có biểu diễn SNBT đầy đủ để tránh mất metadata. Hãy dừng server và chỉnh sửa ở chế độ Ngoại tuyến."
        }

        val useLegacyTagSyntax = isMcVersionBelow1205(mcVersion)
        val rawSnbt = item.rawSnbtMetadata?.trim()

        if (!rawSnbt.isNullOrEmpty()) {
            check(!rawSnbt.contains('\n') && !rawSnbt.contains('\r') && !rawSnbt.contains('\u0000') && rawSnbt.length <= 3500) {
                "Metadata SNBT của vật phẩm '${item.id}' quá dài hoặc chứa ký tự điều khiển không an toàn cho lệnh console. Hãy dừng server để chỉnh sửa ngoại tuyến."
            }
            check(rawSnbt.startsWith("{") && rawSnbt.endsWith("}")) {
                "Metadata SNBT của vật phẩm '${item.id}' không đúng cấu trúc compound hợp lệ. Đã chặn thao tác để bảo vệ dữ liệu."
            }
            val parsedFields = parseCompoundTopLevelFields(rawSnbt)
            check(parsedFields.isNotEmpty()) {
                "Không thể phân tích đầy đủ các trường metadata của '${item.id}'. Đã chặn thao tác trực tuyến để tránh làm mất thuộc tính vật phẩm."
            }
            val looksLikeComponents = parsedFields.keys.any { it.contains(':') }
            return if (useLegacyTagSyntax) {
                check(!looksLikeComponents) {
                    "Vật phẩm '${item.id}' mang định dạng Data Components 1.20.5+ không tương thích với cú pháp NBT Tag của phiên bản MC $mcVersion."
                }
                rawSnbt
            } else {
                if (looksLikeComponents) {
                    val bracketBody = parsedFields.entries.joinToString(", ") { (k, v) -> "$k=$v" }
                    "[$bracketBody]"
                } else {
                    // Trường hợp vật phẩm có rawTag cũ nhưng đang chạy trên 1.20.5+
                    val converted = buildModernComponentBracketFromItem(item, extraCustomDataSnbt = rawSnbt)
                    converted
                }
            }
        }

        if (!hasStructuredMeta) return ""

        return if (useLegacyTagSyntax) {
            buildLegacyTagSnbtFromItem(item)
        } else {
            buildModernComponentBracketFromItem(item, extraCustomDataSnbt = null)
        }
    }

    private fun buildModernComponentBracketFromItem(
        item: InventoryItemStack,
        extraCustomDataSnbt: String?
    ): String {
        val parts = mutableListOf<String>()
        if (item.damage != null && item.damage > 0) {
            parts.add("minecraft:damage=${item.damage}")
        }
        if (item.maxDamage != null && item.maxDamage > 0) {
            parts.add("minecraft:max_damage=${item.maxDamage}")
        }
        if (!item.customName.isNullOrBlank()) {
            val escaped = item.customName.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
            parts.add("""minecraft:custom_name='{"text":"$escaped"}'""")
        }
        if (item.lore.isNotEmpty()) {
            val loreItems = item.lore.joinToString(", ") { line ->
                val escaped = line.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
                """'{"text":"$escaped"}'"""
            }
            parts.add("minecraft:lore=[$loreItems]")
        }
        if (item.enchantments.isNotEmpty()) {
            val levels = item.enchantments.joinToString(", ") { ench ->
                val id = if (ench.id.contains(':')) ench.id else "minecraft:${ench.id}"
                "\"$id\": ${ench.level}"
            }
            parts.add("minecraft:enchantments={levels: {$levels}}")
        }
        if (item.unbreakable) {
            parts.add("minecraft:unbreakable={}")
        }
        if (!extraCustomDataSnbt.isNullOrBlank()) {
            parts.add("minecraft:custom_data=$extraCustomDataSnbt")
        }
        return if (parts.isEmpty()) "" else "[${parts.joinToString(", ")}]"
    }

    private fun buildLegacyTagSnbtFromItem(item: InventoryItemStack): String {
        val parts = mutableListOf<String>()
        if (item.damage != null && item.damage > 0) {
            parts.add("Damage:${item.damage}")
        }
        if (!item.customName.isNullOrBlank() || item.lore.isNotEmpty()) {
            val displayParts = mutableListOf<String>()
            if (!item.customName.isNullOrBlank()) {
                val escaped = item.customName.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
                displayParts.add("""Name:'{"text":"$escaped"}'""")
            }
            if (item.lore.isNotEmpty()) {
                val loreItems = item.lore.joinToString(",") { line ->
                    val escaped = line.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
                    """'{"text":"$escaped"}'"""
                }
                displayParts.add("Lore:[$loreItems]")
            }
            parts.add("display:{${displayParts.joinToString(",")}}")
        }
        if (item.enchantments.isNotEmpty()) {
            val enchItems = item.enchantments.joinToString(",") { ench ->
                val id = if (ench.id.contains(':')) ench.id else "minecraft:${ench.id}"
                """{id:"$id",lvl:${ench.level}s}"""
            }
            parts.add("Enchantments:[$enchItems]")
        }
        if (item.unbreakable) {
            parts.add("Unbreakable:1b")
        }
        return if (parts.isEmpty()) "" else "{${parts.joinToString(",")}}"
    }

    fun isMcVersionBelow1215(mcVersion: String): Boolean {
        val parts = mcVersion.trim().removePrefix("v").split('.', '-')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return major == 1 && (minor < 21 || (minor == 21 && patch < 5))
    }

    fun isMcVersionBelow1205(mcVersion: String): Boolean {
        val parts = mcVersion.trim().removePrefix("v").split('.', '-')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return major == 1 && (minor < 20 || (minor == 20 && patch < 5))
    }

    fun isMcVersionBelow117(mcVersion: String): Boolean {
        val parts = mcVersion.trim().removePrefix("v").split('.', '-')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        return major == 1 && minor < 17
    }

    fun isMcVersionBelow113(mcVersion: String): Boolean {
        val parts = mcVersion.trim().removePrefix("v").split('.', '-')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        return major == 1 && minor < 13
    }
}
