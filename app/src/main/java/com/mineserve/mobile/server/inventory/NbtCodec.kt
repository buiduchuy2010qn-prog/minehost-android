package com.mineserve.mobile.server.inventory

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Bộ giải mã và ghi nhị phân chuẩn Minecraft Named Binary Tag (NBT) không mất dữ liệu (Lossless AST).
 * Dùng cho:
 * - Đọc và chỉnh sửa có kiểm soát tệp `<world>/playerdata/<uuid>.dat` của Java Edition (1.7.10 – 1.21.5+).
 * - Đọc và chỉnh sửa tệp `players/<name>.dat` của PocketMine-MP / PowerNukkitX.
 * - Bảo toàn 100% các trường NBT của Mod hoặc dữ liệu nhân vật không nằm trong phạm vi túi đồ.
 */
object NbtCodec {

    const val TAG_END: Int = 0
    const val TAG_BYTE: Int = 1
    const val TAG_SHORT: Int = 2
    const val TAG_INT: Int = 3
    const val TAG_LONG: Int = 4
    const val TAG_FLOAT: Int = 5
    const val TAG_DOUBLE: Int = 6
    const val TAG_BYTE_ARRAY: Int = 7
    const val TAG_STRING: Int = 8
    const val TAG_LIST: Int = 9
    const val TAG_COMPOUND: Int = 10
    const val TAG_INT_ARRAY: Int = 11
    const val TAG_LONG_ARRAY: Int = 12

    sealed class NbtTag(val typeId: Int) {
        data class ByteVal(val value: Byte) : NbtTag(TAG_BYTE)
        data class ShortVal(val value: Short) : NbtTag(TAG_SHORT)
        data class IntVal(val value: Int) : NbtTag(TAG_INT)
        data class LongVal(val value: Long) : NbtTag(TAG_LONG)
        data class FloatVal(val value: Float) : NbtTag(TAG_FLOAT)
        data class DoubleVal(val value: Double) : NbtTag(TAG_DOUBLE)
        data class ByteArrayVal(val value: ByteArray) : NbtTag(TAG_BYTE_ARRAY) {
            override fun equals(other: Any?): Boolean = other is ByteArrayVal && value.contentEquals(other.value)
            override fun hashCode(): Int = value.contentHashCode()
        }
        data class StringVal(val value: String) : NbtTag(TAG_STRING)
        data class ListVal(val elementType: Int, val elements: MutableList<NbtTag>) : NbtTag(TAG_LIST)
        data class CompoundVal(val entries: LinkedHashMap<String, NbtTag> = linkedMapOf()) : NbtTag(TAG_COMPOUND) {
            fun getString(key: String): String? = (entries[key] as? StringVal)?.value
            fun getByte(key: String): Byte? = (entries[key] as? ByteVal)?.value
            fun getShort(key: String): Short? = (entries[key] as? ShortVal)?.value
            fun getInt(key: String): Int? = when (val t = entries[key]) {
                is IntVal -> t.value
                is ByteVal -> t.value.toInt()
                is ShortVal -> t.value.toInt()
                is LongVal -> t.value.toInt()
                else -> null
            }
            fun getCompound(key: String): CompoundVal? = entries[key] as? CompoundVal
            fun getList(key: String): ListVal? = entries[key] as? ListVal
        }
        data class IntArrayVal(val value: IntArray) : NbtTag(TAG_INT_ARRAY) {
            override fun equals(other: Any?): Boolean = other is IntArrayVal && value.contentEquals(other.value)
            override fun hashCode(): Int = value.contentHashCode()
        }
        data class LongArrayVal(val value: LongArray) : NbtTag(TAG_LONG_ARRAY) {
            override fun equals(other: Any?): Boolean = other is LongArrayVal && value.contentEquals(other.value)
            override fun hashCode(): Int = value.contentHashCode()
        }
    }

    data class NamedRoot(
        val rootName: String,
        val compound: NbtTag.CompoundVal,
        val gzipCompressed: Boolean = true
    )

    fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    fun readFile(file: File): NamedRoot {
        require(file.isFile && file.length() > 0L) {
            "Tệp dữ liệu người chơi '${file.name}' không tồn tại hoặc đang rỗng."
        }
        val raw = file.readBytes()
        return readBytes(raw)
    }

    fun readBytes(raw: ByteArray): NamedRoot {
        val gzip = isGzip(raw)
        val stream = if (gzip) {
            GZIPInputStream(ByteArrayInputStream(raw))
        } else {
            ByteArrayInputStream(raw)
        }
        DataInputStream(BufferedInputStream(stream)).use { dis ->
            val rootType = dis.readUnsignedByte()
            require(rootType == TAG_COMPOUND) {
                "Định dạng NBT không hợp lệ: thẻ gốc có mã $rootType (kỳ vọng TAG_Compound = 10)."
            }
            val rootName = dis.readUTF()
            val rootCompound = readPayload(dis, TAG_COMPOUND, depth = 0) as NbtTag.CompoundVal
            return NamedRoot(rootName = rootName, compound = rootCompound, gzipCompressed = gzip)
        }
    }

    fun writeBytes(root: NamedRoot): ByteArray {
        val rawOut = ByteArrayOutputStream()
        val targetStream = if (root.gzipCompressed) GZIPOutputStream(rawOut) else rawOut
        DataOutputStream(targetStream).use { dos ->
            dos.writeByte(TAG_COMPOUND)
            dos.writeUTF(root.rootName)
            writePayload(dos, root.compound)
        }
        return rawOut.toByteArray()
    }

    /**
     * Ghi tệp dữ liệu người chơi an toàn theo chuẩn Phần 7:
     * 1. Sao lưu tệp gốc sang `<filename>.bak`.
     * 2. Ghi ra tệp tạm `.tmp`.
     * 3. Giải mã ngược tệp tạm để xác minh tính toàn vẹn 100% trước khi ghi đè.
     * 4. Di chuyển nguyên tử (`ATOMIC_MOVE`); tự động khôi phục từ `.bak` nếu có lỗi.
     */
    fun writeFileSafely(file: File, root: NamedRoot) {
        val parent = file.parentFile ?: error("Đường dẫn thư mục cha không hợp lệ.")
        parent.mkdirs()
        val bakFile = File(parent, "${file.name}.bak")
        val tmpFile = File(parent, ".${file.name}.minehost.tmp")
        if (file.isFile && file.length() > 0L) {
            file.copyTo(bakFile, overwrite = true)
        }
        try {
            val encodedBytes = writeBytes(root)
            FileOutputStream(tmpFile).use { fos ->
                fos.write(encodedBytes)
                fos.fd.sync()
            }
            // Kiểm tra tính toàn vẹn bằng cách đọc lại chính tệp tạm vừa ghi
            val verified = readFile(tmpFile)
            check(verified.compound.entries.isNotEmpty() || root.compound.entries.isEmpty()) {
                "Kiểm tra toàn vẹn NBT sau khi ghi thất bại."
            }
            try {
                Files.move(
                    tmpFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                Files.move(
                    tmpFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            if (bakFile.isFile && bakFile.length() > 0L) {
                runCatching { bakFile.copyTo(file, overwrite = true) }
            }
            throw e
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    fun encodeCompoundBase64(compound: NbtTag.CompoundVal): String {
        val bytes = writeBytes(NamedRoot("", compound, gzipCompressed = false))
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decodeCompoundBase64(base64: String?): NbtTag.CompoundVal? {
        if (base64.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(base64)
            readBytes(bytes).compound
        }.getOrNull()
    }

    private fun readPayload(dis: DataInputStream, type: Int, depth: Int): NbtTag {
        require(depth <= 512) { "Cấu trúc NBT lồng quá sâu (> 512 cấp)." }
        return when (type) {
            TAG_BYTE -> NbtTag.ByteVal(dis.readByte())
            TAG_SHORT -> NbtTag.ShortVal(dis.readShort())
            TAG_INT -> NbtTag.IntVal(dis.readInt())
            TAG_LONG -> NbtTag.LongVal(dis.readLong())
            TAG_FLOAT -> NbtTag.FloatVal(dis.readFloat())
            TAG_DOUBLE -> NbtTag.DoubleVal(dis.readDouble())
            TAG_BYTE_ARRAY -> {
                val len = dis.readInt()
                require(len in 0..(16 * 1024 * 1024)) { "ByteArray NBT quá lớn: $len" }
                val arr = ByteArray(len)
                dis.readFully(arr)
                NbtTag.ByteArrayVal(arr)
            }
            TAG_STRING -> NbtTag.StringVal(dis.readUTF())
            TAG_LIST -> {
                val elemType = dis.readUnsignedByte()
                val len = dis.readInt()
                require(len in 0..65536) { "List NBT có số phần tử không hợp lệ: $len" }
                val list = ArrayList<NbtTag>(len)
                for (i in 0 until len) {
                    list.add(readPayload(dis, elemType, depth + 1))
                }
                NbtTag.ListVal(elemType, list)
            }
            TAG_COMPOUND -> {
                val map = LinkedHashMap<String, NbtTag>()
                while (true) {
                    val childType = dis.readUnsignedByte()
                    if (childType == TAG_END) break
                    val name = dis.readUTF()
                    map[name] = readPayload(dis, childType, depth + 1)
                }
                NbtTag.CompoundVal(map)
            }
            TAG_INT_ARRAY -> {
                val len = dis.readInt()
                require(len in 0..(4 * 1024 * 1024)) { "IntArray NBT quá lớn: $len" }
                val arr = IntArray(len) { dis.readInt() }
                NbtTag.IntArrayVal(arr)
            }
            TAG_LONG_ARRAY -> {
                val len = dis.readInt()
                require(len in 0..(4 * 1024 * 1024)) { "LongArray NBT quá lớn: $len" }
                val arr = LongArray(len) { dis.readLong() }
                NbtTag.LongArrayVal(arr)
            }
            else -> throw IllegalArgumentException("Không nhận diện được mã thẻ NBT: $type")
        }
    }

    private fun writePayload(dos: DataOutputStream, tag: NbtTag) {
        when (tag) {
            is NbtTag.ByteVal -> dos.writeByte(tag.value.toInt())
            is NbtTag.ShortVal -> dos.writeShort(tag.value.toInt())
            is NbtTag.IntVal -> dos.writeInt(tag.value)
            is NbtTag.LongVal -> dos.writeLong(tag.value)
            is NbtTag.FloatVal -> dos.writeFloat(tag.value)
            is NbtTag.DoubleVal -> dos.writeDouble(tag.value)
            is NbtTag.ByteArrayVal -> {
                dos.writeInt(tag.value.size)
                dos.write(tag.value)
            }
            is NbtTag.StringVal -> dos.writeUTF(tag.value)
            is NbtTag.ListVal -> {
                val effectiveType = if (tag.elements.isEmpty()) {
                    tag.elementType.coerceIn(TAG_END, TAG_LONG_ARRAY)
                } else {
                    tag.elements.first().typeId
                }
                dos.writeByte(effectiveType)
                dos.writeInt(tag.elements.size)
                for (elem in tag.elements) {
                    writePayload(dos, elem)
                }
            }
            is NbtTag.CompoundVal -> {
                for ((name, child) in tag.entries) {
                    dos.writeByte(child.typeId)
                    dos.writeUTF(name)
                    writePayload(dos, child)
                }
                dos.writeByte(TAG_END)
            }
            is NbtTag.IntArrayVal -> {
                dos.writeInt(tag.value.size)
                for (v in tag.value) dos.writeInt(v)
            }
            is NbtTag.LongArrayVal -> {
                dos.writeInt(tag.value.size)
                for (v in tag.value) dos.writeLong(v)
            }
        }
    }

    // ── Chuyển đổi giữa Compound NBT của Player và Mô hình Túi đồ Trung gian ──

    data class ParsedNbtInventory(
        val selectedHotbarSlot: Int,
        val hotbar: List<InventoryItemStack?>,
        val mainInventory: List<InventoryItemStack?>,
        val armorHead: InventoryItemStack?,
        val armorChest: InventoryItemStack?,
        val armorLegs: InventoryItemStack?,
        val armorFeet: InventoryItemStack?,
        val offhand: InventoryItemStack?,
        val enderChest: List<InventoryItemStack?>
    )

    fun extractInventoryFromPlayerRoot(root: NbtTag.CompoundVal, isPocketMine: Boolean = false): ParsedNbtInventory {
        val selectedSlot = (root.getInt("SelectedItemSlot") ?: root.getInt("SelectedInventorySlot") ?: 0).coerceIn(0, 8)
        val hotbar = MutableList<InventoryItemStack?>(9) { null }
        val main = MutableList<InventoryItemStack?>(27) { null }
        var armorHead: InventoryItemStack? = null
        var armorChest: InventoryItemStack? = null
        var armorLegs: InventoryItemStack? = null
        var armorFeet: InventoryItemStack? = null
        var offhand: InventoryItemStack? = null
        val ender = MutableList<InventoryItemStack?>(27) { null }

        val invList = root.getList("Inventory")?.elements.orEmpty()
        for (elem in invList) {
            val comp = elem as? NbtTag.CompoundVal ?: continue
            val rawSlot = comp.getByte("Slot")?.toInt() ?: comp.getInt("Slot") ?: continue
            // PocketMine-MP đôi khi lưu giáp ở slot 100..103 hoặc trong Armorinventory
            val parsed = parseItemCompound(comp, rawSlot, InventoryItemStack.sectionForNbtSlot(rawSlot)) ?: continue
            when (rawSlot) {
                in 0..8 -> hotbar[rawSlot] = parsed.copy(section = InventorySection.HOTBAR)
                in 9..35 -> main[rawSlot - 9] = parsed.copy(section = InventorySection.MAIN)
                100 -> armorFeet = parsed.copy(slot = 100, section = InventorySection.ARMOR)
                101 -> armorLegs = parsed.copy(slot = 101, section = InventorySection.ARMOR)
                102 -> armorChest = parsed.copy(slot = 102, section = InventorySection.ARMOR)
                103 -> armorHead = parsed.copy(slot = 103, section = InventorySection.ARMOR)
                -106, 40 -> offhand = parsed.copy(slot = -106, section = InventorySection.OFFHAND)
            }
        }

        // Hỗ trợ định dạng `equipment` mới của Minecraft 1.21.5+
        root.getCompound("equipment")?.let { eq ->
            eq.getCompound("head")?.let { c ->
                parseItemCompound(c, 103, InventorySection.ARMOR)?.let { armorHead = it }
            }
            eq.getCompound("chest")?.let { c ->
                parseItemCompound(c, 102, InventorySection.ARMOR)?.let { armorChest = it }
            }
            eq.getCompound("legs")?.let { c ->
                parseItemCompound(c, 101, InventorySection.ARMOR)?.let { armorLegs = it }
            }
            eq.getCompound("feet")?.let { c ->
                parseItemCompound(c, 100, InventorySection.ARMOR)?.let { armorFeet = it }
            }
            eq.getCompound("offhand")?.let { c ->
                parseItemCompound(c, -106, InventorySection.OFFHAND)?.let { offhand = it }
            }
        }

        // PocketMine-MP ArmorInventory / OffHandInventory riêng nếu có
        if (isPocketMine) {
            root.getList("ArmorInventory")?.elements?.forEachIndexed { idx, elem ->
                val comp = elem as? NbtTag.CompoundVal ?: return@forEachIndexed
                val targetSlot = 103 - idx
                val item = parseItemCompound(comp, targetSlot, InventorySection.ARMOR) ?: return@forEachIndexed
                when (targetSlot) {
                    103 -> if (armorHead == null) armorHead = item
                    102 -> if (armorChest == null) armorChest = item
                    101 -> if (armorLegs == null) armorLegs = item
                    100 -> if (armorFeet == null) armorFeet = item
                }
            }
        }

        val enderList = (root.getList("EnderItems") ?: root.getList("EnderChestInventory"))?.elements.orEmpty()
        for (elem in enderList) {
            val comp = elem as? NbtTag.CompoundVal ?: continue
            val rawSlot = comp.getByte("Slot")?.toInt() ?: comp.getInt("Slot") ?: continue
            if (rawSlot in 0..26) {
                val parsed = parseItemCompound(comp, rawSlot, InventorySection.ENDER_CHEST)
                if (parsed != null) {
                    ender[rawSlot] = parsed
                }
            }
        }

        return ParsedNbtInventory(
            selectedHotbarSlot = selectedSlot,
            hotbar = hotbar,
            mainInventory = main,
            armorHead = armorHead,
            armorChest = armorChest,
            armorLegs = armorLegs,
            armorFeet = armorFeet,
            offhand = offhand,
            enderChest = ender
        )
    }

    fun parseItemCompound(
        comp: NbtTag.CompoundVal,
        slot: Int,
        section: InventorySection
    ): InventoryItemStack? {
        val rawId = comp.getString("id")
            ?: comp.getString("Name")
            ?: comp.getShort("id")?.let { "legacy_id:$it" }
            ?: return null
        if (rawId == "minecraft:air" || rawId == "air" || rawId == "legacy_id:0") return null
        val count = (comp.getInt("count") ?: comp.getInt("Count") ?: 1).coerceAtLeast(0)
        if (count <= 0) return null

        val tagComp = comp.getCompound("tag")
        val componentsComp = comp.getCompound("components")

        val damage = componentsComp?.getInt("minecraft:damage")
            ?: tagComp?.getInt("Damage")
            ?: comp.getShort("Damage")?.toInt()?.takeIf { it > 0 }
        val maxDamage = componentsComp?.getInt("minecraft:max_damage")

        val unbreakable = (componentsComp?.entries?.containsKey("minecraft:unbreakable") == true) ||
            (tagComp?.getInt("Unbreakable") == 1)

        val customName = extractCustomName(tagComp, componentsComp)
        val lore = extractLore(tagComp, componentsComp)
        val enchantments = extractEnchantments(tagComp, componentsComp)
        val extraMeta = linkedMapOf<String, String>()
        tagComp?.entries?.keys?.filter { it !in setOf("Damage", "Enchantments", "ench", "StoredEnchantments", "display", "Unbreakable") }
            ?.takeIf { it.isNotEmpty() }
            ?.let { extraMeta["Tag NBT mở rộng"] = it.joinToString(", ") }
        componentsComp?.entries?.keys?.filter {
            it !in setOf(
                "minecraft:damage",
                "minecraft:max_damage",
                "minecraft:enchantments",
                "minecraft:stored_enchantments",
                "minecraft:custom_name",
                "minecraft:item_name",
                "minecraft:lore",
                "minecraft:unbreakable"
            )
        }?.takeIf { it.isNotEmpty() }?.let {
            extraMeta["Components 1.20.5+"] = it.joinToString(", ")
        }

        return InventoryItemStack(
            id = rawId,
            count = count,
            slot = slot,
            section = section,
            damage = damage,
            maxDamage = maxDamage,
            customName = customName,
            lore = lore,
            enchantments = enchantments,
            unbreakable = unbreakable,
            rawNbtCompoundBase64 = encodeCompoundBase64(comp),
            extraMetadata = extraMeta
        )
    }

    private fun extractCustomName(tagComp: NbtTag.CompoundVal?, componentsComp: NbtTag.CompoundVal?): String? {
        val raw = componentsComp?.getString("minecraft:custom_name")
            ?: componentsComp?.getString("minecraft:item_name")
            ?: tagComp?.getCompound("display")?.getString("Name")
            ?: return null
        return stripJsonTextComponent(raw)
    }

    private fun extractLore(tagComp: NbtTag.CompoundVal?, componentsComp: NbtTag.CompoundVal?): List<String> {
        val list = componentsComp?.getList("minecraft:lore")
            ?: tagComp?.getCompound("display")?.getList("Lore")
            ?: return emptyList()
        return list.elements.mapNotNull { (it as? NbtTag.StringVal)?.value?.let(::stripJsonTextComponent) }
    }

    private fun extractEnchantments(
        tagComp: NbtTag.CompoundVal?,
        componentsComp: NbtTag.CompoundVal?
    ): List<ItemEnchantment> {
        val result = mutableListOf<ItemEnchantment>()
        // 1.13–1.20.4: tag.Enchantments / tag.StoredEnchantments / tag.ench
        val legacyLists = listOfNotNull(
            tagComp?.getList("Enchantments"),
            tagComp?.getList("StoredEnchantments"),
            tagComp?.getList("ench")
        )
        for (lst in legacyLists) {
            for (elem in lst.elements) {
                val c = elem as? NbtTag.CompoundVal ?: continue
                val id = c.getString("id") ?: c.getShort("id")?.let { "ench_$it" } ?: continue
                val lvl = c.getInt("lvl") ?: 1
                result.add(ItemEnchantment(id = id, level = lvl))
            }
        }
        // 1.20.5+: components["minecraft:enchantments"] hoặc levels bên trong
        val compEnchKeys = listOf("minecraft:enchantments", "minecraft:stored_enchantments")
        for (key in compEnchKeys) {
            val enchComp = componentsComp?.getCompound(key) ?: continue
            val levelsComp = enchComp.getCompound("levels") ?: enchComp
            for ((enchId, lvlTag) in levelsComp.entries) {
                if (enchId == "show_in_tooltip") continue
                val lvl = when (lvlTag) {
                    is NbtTag.IntVal -> lvlTag.value
                    is NbtTag.ShortVal -> lvlTag.value.toInt()
                    is NbtTag.ByteVal -> lvlTag.value.toInt()
                    else -> continue
                }
                result.add(ItemEnchantment(id = enchId, level = lvl))
            }
        }
        return result
    }

    fun stripJsonTextComponent(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("\"")) return trimmed
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1).replace("\\\"", "\"")
        }
        val textRegex = Regex(""""text"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val matches = textRegex.findAll(trimmed).map { it.groupValues[1].replace("\\\"", "\"") }.toList()
        return if (matches.isNotEmpty()) matches.joinToString("") else trimmed
    }

    /**
     * Cập nhật một ô trong cây `NbtTag.CompoundVal` của người chơi mà vẫn bảo toàn 100% các trường NBT khác.
     */
    fun applySlotUpdateToPlayerRoot(
        root: NbtTag.CompoundVal,
        section: InventorySection,
        slot: Int,
        newItem: InventoryItemStack?,
        isPocketMine: Boolean = false
    ) {
        val dataVersion = root.getInt("DataVersion") ?: 0
        val usesModernLowercaseCount = !isPocketMine && dataVersion >= 3837 // MC 1.20.5+ DataVersion >= 3837
        val usesModernEquipmentCompound = !isPocketMine && (root.entries.containsKey("equipment") || dataVersion >= 4325)

        val listKey = if (section == InventorySection.ENDER_CHEST) {
            if (isPocketMine && root.entries.containsKey("EnderChestInventory")) "EnderChestInventory" else "EnderItems"
        } else {
            "Inventory"
        }

        val currentList = root.getList(listKey)?.elements ?: mutableListOf()
        val filtered = currentList.filterNot { elem ->
            val comp = elem as? NbtTag.CompoundVal ?: return@filterNot false
            val s = comp.getByte("Slot")?.toInt() ?: comp.getInt("Slot")
            s == slot || (section == InventorySection.OFFHAND && (s == -106 || s == 40))
        }.toMutableList()

        val writeIntoEquipmentOnly = usesModernEquipmentCompound &&
            (section == InventorySection.ARMOR || section == InventorySection.OFFHAND)

        if (!writeIntoEquipmentOnly && newItem != null && newItem.count > 0 && newItem.id.isNotBlank() && newItem.id != "minecraft:air") {
            val itemCompound = buildOrUpdateItemCompound(newItem, slot, usesModernLowercaseCount)
            filtered.add(itemCompound)
        }

        val prevListElemType = root.getList(listKey)?.elementType ?: TAG_END
        root.entries[listKey] = NbtTag.ListVal(
            elementType = if (filtered.isEmpty()) prevListElemType.takeIf { it == TAG_END } ?: TAG_END else TAG_COMPOUND,
            elements = filtered
        )

        // Nếu file dùng cấu trúc `equipment` của 1.21.5+ và thao tác trên giáp/offhand, ghi chuẩn vào `equipment` (không có tag Slot)
        if ((section == InventorySection.ARMOR || section == InventorySection.OFFHAND) && usesModernEquipmentCompound) {
            val eq = root.getCompound("equipment") ?: NbtTag.CompoundVal(linkedMapOf()).also {
                root.entries["equipment"] = it
            }
            val eqKey = when {
                section == InventorySection.OFFHAND -> "offhand"
                slot == 103 -> "head"
                slot == 102 -> "chest"
                slot == 101 -> "legs"
                slot == 100 -> "feet"
                else -> null
            }
            if (eqKey != null) {
                if (newItem == null || newItem.count <= 0 || newItem.id == "minecraft:air") {
                    eq.entries.remove(eqKey)
                } else {
                    val eqComp = buildOrUpdateItemCompound(newItem, slot, usesModernLowercaseCount)
                    eqComp.entries.remove("Slot")
                    eq.entries[eqKey] = eqComp
                }
            }
        }

        // Đồng bộ ArmorInventory riêng của PocketMine-MP nếu tồn tại trong file .dat
        if (isPocketMine && section == InventorySection.ARMOR) {
            root.getList("ArmorInventory")?.let { armorListTag ->
                val targetIdx = 103 - slot
                if (targetIdx in 0..3) {
                    val updatedArmor = armorListTag.elements.filterNot { elem ->
                        val c = elem as? NbtTag.CompoundVal ?: return@filterNot false
                        val s = c.getByte("Slot")?.toInt() ?: c.getInt("Slot")
                        s == targetIdx || s == slot
                    }.toMutableList()
                    if (newItem != null && newItem.count > 0 && newItem.id != "minecraft:air") {
                        val comp = buildOrUpdateItemCompound(newItem, targetIdx, false)
                        updatedArmor.add(comp)
                    }
                    root.entries["ArmorInventory"] = NbtTag.ListVal(
                        elementType = if (updatedArmor.isEmpty()) TAG_END else TAG_COMPOUND,
                        elements = updatedArmor
                    )
                }
            }
        }
    }

    private fun buildOrUpdateItemCompound(
        item: InventoryItemStack,
        targetSlot: Int,
        usesModernLowercaseCount: Boolean
    ): NbtTag.CompoundVal {
        // Nếu vật phẩm có sẵn compound nhị phân gốc (từ vật phẩm Mod hoặc vật phẩm đang chuyển ô),
        // khôi phục nguyên bản cây NBT của vật phẩm rồi chỉ cập nhật Slot, Count và các trường người dùng thực sự sửa!
        val restored = decodeCompoundBase64(item.rawNbtCompoundBase64)
        if (restored != null) {
            val copyMap = LinkedHashMap(restored.entries)
            copyMap["Slot"] = NbtTag.ByteVal(targetSlot.toByte())
            val origParsed = parseItemCompound(restored, item.slot, item.section)
            if (origParsed == null || SnbtParser.normalizeItemId(origParsed.id) != SnbtParser.normalizeItemId(item.id)) {
                copyMap["id"] = NbtTag.StringVal(
                    if (item.id.contains(':')) item.id else "minecraft:${item.id}"
                )
            }
            if (copyMap.containsKey("count") || (usesModernLowercaseCount && !copyMap.containsKey("Count"))) {
                copyMap.remove("Count")
                copyMap["count"] = NbtTag.IntVal(item.count.coerceIn(1, 99))
            } else {
                copyMap["Count"] = NbtTag.ByteVal(item.count.coerceIn(1, 127).toByte())
            }
            // Nếu người dùng chủ động sửa damage / customName / enchantments / lore / unbreakable trên vật phẩm có sẵn,
            // chỉ cập nhật đúng trường thay đổi và giữ nguyên 100% các trường NBT/Mod khác
            if (origParsed != null && (
                    origParsed.damage != item.damage ||
                        origParsed.customName != item.customName ||
                        origParsed.lore != item.lore ||
                        origParsed.enchantments != item.enchantments ||
                        origParsed.unbreakable != item.unbreakable
                    )
            ) {
                if (copyMap.containsKey("components") || (usesModernLowercaseCount && !copyMap.containsKey("tag"))) {
                    val comps = LinkedHashMap((copyMap["components"] as? NbtTag.CompoundVal)?.entries.orEmpty())
                    if (origParsed.damage != item.damage) {
                        if (item.damage != null && item.damage > 0) comps["minecraft:damage"] = NbtTag.IntVal(item.damage)
                        else comps.remove("minecraft:damage")
                    }
                    if (origParsed.customName != item.customName) {
                        if (!item.customName.isNullOrBlank()) {
                            val escaped = item.customName.replace("\\", "\\\\").replace("\"", "\\\"")
                            comps["minecraft:custom_name"] = NbtTag.StringVal("""{"text":"$escaped"}""")
                        } else {
                            comps.remove("minecraft:custom_name")
                        }
                    }
                    if (origParsed.lore != item.lore) {
                        if (item.lore.isNotEmpty()) {
                            val loreElems = item.lore.map { line ->
                                val escaped = line.replace("\\", "\\\\").replace("\"", "\\\"")
                                NbtTag.StringVal("""{"text":"$escaped"}""") as NbtTag
                            }.toMutableList()
                            comps["minecraft:lore"] = NbtTag.ListVal(TAG_STRING, loreElems)
                        } else {
                            comps.remove("minecraft:lore")
                        }
                    }
                    if (origParsed.enchantments != item.enchantments) {
                        if (item.enchantments.isNotEmpty()) {
                            val levels = LinkedHashMap<String, NbtTag>()
                            for (ench in item.enchantments) {
                                val fullId = if (ench.id.contains(':')) ench.id else "minecraft:${ench.id}"
                                levels[fullId] = NbtTag.IntVal(ench.level)
                            }
                            comps["minecraft:enchantments"] = NbtTag.CompoundVal(linkedMapOf("levels" to NbtTag.CompoundVal(levels)))
                        } else {
                            comps.remove("minecraft:enchantments")
                        }
                    }
                    if (origParsed.unbreakable != item.unbreakable) {
                        if (item.unbreakable) comps["minecraft:unbreakable"] = NbtTag.CompoundVal(linkedMapOf())
                        else comps.remove("minecraft:unbreakable")
                    }
                    if (comps.isNotEmpty()) copyMap["components"] = NbtTag.CompoundVal(comps)
                    else copyMap.remove("components")
                } else {
                    val tagMap = LinkedHashMap((copyMap["tag"] as? NbtTag.CompoundVal)?.entries.orEmpty())
                    if (origParsed.damage != item.damage) {
                        if (item.damage != null && item.damage > 0) tagMap["Damage"] = NbtTag.IntVal(item.damage)
                        else tagMap.remove("Damage")
                    }
                    if (origParsed.customName != item.customName || origParsed.lore != item.lore) {
                        val displayMap = LinkedHashMap((tagMap["display"] as? NbtTag.CompoundVal)?.entries.orEmpty())
                        if (!item.customName.isNullOrBlank()) {
                            val escaped = item.customName.replace("\\", "\\\\").replace("\"", "\\\"")
                            displayMap["Name"] = NbtTag.StringVal("""{"text":"$escaped"}""")
                        } else if (origParsed.customName != item.customName) {
                            displayMap.remove("Name")
                        }
                        if (item.lore.isNotEmpty()) {
                            val loreElems = item.lore.map { line ->
                                val escaped = line.replace("\\", "\\\\").replace("\"", "\\\"")
                                NbtTag.StringVal("""{"text":"$escaped"}""") as NbtTag
                            }.toMutableList()
                            displayMap["Lore"] = NbtTag.ListVal(TAG_STRING, loreElems)
                        } else if (origParsed.lore != item.lore) {
                            displayMap.remove("Lore")
                        }
                        if (displayMap.isNotEmpty()) tagMap["display"] = NbtTag.CompoundVal(displayMap)
                        else tagMap.remove("display")
                    }
                    if (origParsed.enchantments != item.enchantments) {
                        if (item.enchantments.isNotEmpty()) {
                            val enchElements = item.enchantments.map { ench ->
                                val em = LinkedHashMap<String, NbtTag>()
                                em["id"] = NbtTag.StringVal(if (ench.id.contains(':')) ench.id else "minecraft:${ench.id}")
                                em["lvl"] = NbtTag.ShortVal(ench.level.toShort())
                                NbtTag.CompoundVal(em) as NbtTag
                            }.toMutableList()
                            tagMap["Enchantments"] = NbtTag.ListVal(TAG_COMPOUND, enchElements)
                        } else {
                            tagMap.remove("Enchantments")
                        }
                    }
                    if (origParsed.unbreakable != item.unbreakable) {
                        if (item.unbreakable) tagMap["Unbreakable"] = NbtTag.ByteVal(1)
                        else tagMap.remove("Unbreakable")
                    }
                    if (tagMap.isNotEmpty()) copyMap["tag"] = NbtTag.CompoundVal(tagMap)
                    else copyMap.remove("tag")
                }
            }
            return NbtTag.CompoundVal(copyMap)
        }

        val map = LinkedHashMap<String, NbtTag>()
        map["Slot"] = NbtTag.ByteVal(targetSlot.toByte())
        map["id"] = NbtTag.StringVal(if (item.id.contains(':')) item.id else "minecraft:${item.id}")
        if (usesModernLowercaseCount) {
            map["count"] = NbtTag.IntVal(item.count.coerceIn(1, 99))
            val comps = LinkedHashMap<String, NbtTag>()
            if (item.damage != null && item.damage > 0) {
                comps["minecraft:damage"] = NbtTag.IntVal(item.damage)
            }
            if (item.maxDamage != null && item.maxDamage > 0) {
                comps["minecraft:max_damage"] = NbtTag.IntVal(item.maxDamage)
            }
            if (!item.customName.isNullOrBlank()) {
                val escaped = item.customName.replace("\\", "\\\\").replace("\"", "\\\"")
                comps["minecraft:custom_name"] = NbtTag.StringVal("""{"text":"$escaped"}""")
            }
            if (item.lore.isNotEmpty()) {
                val loreElems = item.lore.map { line ->
                    val escaped = line.replace("\\", "\\\\").replace("\"", "\\\"")
                    NbtTag.StringVal("""{"text":"$escaped"}""") as NbtTag
                }.toMutableList()
                comps["minecraft:lore"] = NbtTag.ListVal(TAG_STRING, loreElems)
            }
            if (item.enchantments.isNotEmpty()) {
                val levels = LinkedHashMap<String, NbtTag>()
                for (ench in item.enchantments) {
                    val fullId = if (ench.id.contains(':')) ench.id else "minecraft:${ench.id}"
                    levels[fullId] = NbtTag.IntVal(ench.level)
                }
                val enchWrapper = LinkedHashMap<String, NbtTag>()
                enchWrapper["levels"] = NbtTag.CompoundVal(levels)
                comps["minecraft:enchantments"] = NbtTag.CompoundVal(enchWrapper)
            }
            if (item.unbreakable) {
                comps["minecraft:unbreakable"] = NbtTag.CompoundVal(linkedMapOf())
            }
            if (comps.isNotEmpty()) {
                map["components"] = NbtTag.CompoundVal(comps)
            }
        } else {
            map["Count"] = NbtTag.ByteVal(item.count.coerceIn(1, 127).toByte())
            val tagMap = LinkedHashMap<String, NbtTag>()
            if (item.damage != null && item.damage > 0) {
                tagMap["Damage"] = NbtTag.IntVal(item.damage)
            }
            if (!item.customName.isNullOrBlank() || item.lore.isNotEmpty()) {
                val displayMap = LinkedHashMap<String, NbtTag>()
                if (!item.customName.isNullOrBlank()) {
                    val escaped = item.customName.replace("\\", "\\\\").replace("\"", "\\\"")
                    displayMap["Name"] = NbtTag.StringVal("""{"text":"$escaped"}""")
                }
                if (item.lore.isNotEmpty()) {
                    val loreElems = item.lore.map { line ->
                        val escaped = line.replace("\\", "\\\\").replace("\"", "\\\"")
                        NbtTag.StringVal("""{"text":"$escaped"}""") as NbtTag
                    }.toMutableList()
                    displayMap["Lore"] = NbtTag.ListVal(TAG_STRING, loreElems)
                }
                tagMap["display"] = NbtTag.CompoundVal(displayMap)
            }
            if (item.enchantments.isNotEmpty()) {
                val enchElements = item.enchantments.map { ench ->
                    val em = LinkedHashMap<String, NbtTag>()
                    em["id"] = NbtTag.StringVal(if (ench.id.contains(':')) ench.id else "minecraft:${ench.id}")
                    em["lvl"] = NbtTag.ShortVal(ench.level.toShort())
                    NbtTag.CompoundVal(em)
                }.toMutableList<NbtTag>()
                tagMap["Enchantments"] = NbtTag.ListVal(TAG_COMPOUND, enchElements)
            }
            if (item.unbreakable) {
                tagMap["Unbreakable"] = NbtTag.ByteVal(1)
            }
            if (tagMap.isNotEmpty()) {
                map["tag"] = NbtTag.CompoundVal(tagMap)
            }
        }
        return NbtTag.CompoundVal(map)
    }
}
