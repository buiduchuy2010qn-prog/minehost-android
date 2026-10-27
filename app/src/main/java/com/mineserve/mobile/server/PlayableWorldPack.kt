package com.mineserve.mobile.server

import java.io.File

/** Pure generator shared by the app and real-server integration fixtures. Only for NEW worlds. */
object PlayableWorldPack {
    const val SCHEMA = 2
    const val SUPPORTED_VERSIONS = "Java 1.20–1.21.4"
    val modeNames = linkedMapOf("oneblock" to "OneBlock", "skyblock" to "SkyBlock", "parkour" to "Parkour", "lucky_island" to "Đảo kho báu")
    const val VOID_SETTINGS = """{"biome":"minecraft:plains","layers":[{"block":"minecraft:air","height":1}],"features":false,"lakes":false,"structure_overrides":[]}"""

    fun formatFor(version: String): Int? = when (version.trim()) {
        "1.20", "1.20.1" -> 15
        "1.20.2" -> 18
        "1.20.3", "1.20.4" -> 26
        "1.20.5", "1.20.6" -> 41
        "1.21", "1.21.1" -> 48
        "1.21.2", "1.21.3" -> 57
        "1.21.4" -> 61
        else -> null
    }

    fun displayName(folder: String): String {
        for ((id, title) in modeNames) {
            val base = "world_" + id
            if (folder == base) return title + " #1"
            if (folder.startsWith(base + "_")) {
                val number = folder.removePrefix(base + "_")
                if (number.matches(Regex("[1-9][0-9]*"))) return title + " #" + number
            }
        }
        return folder
    }

    fun properties(mode: String): Map<String, String> {
        require(mode in modeNames)
        return mapOf("level-type" to "minecraft:flat", "generator-settings" to VOID_SETTINGS,
            "generate-structures" to "false", "allow-flight" to "true", "spawn-protection" to "0")
    }

    fun write(world: File, mode: String, version: String) {
        require(mode in modeNames) { "Chế độ chơi không hợp lệ." }
        val format = requireNotNull(formatFor(version)) { "Chế độ dựng sẵn hỗ trợ " + SUPPORTED_VERSIONS + ". Phiên bản này chưa được hỗ trợ." }
        val pack = File(world, "datapacks/minehost_" + mode)
        check(!pack.exists()) { "Không ghi đè datapack đang tồn tại." }
        check(pack.mkdirs()) { "Không tạo được datapack." }
        fun put(path: String, text: String) {
            val file = File(pack, path)
            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            file.writeText(text)
        }
        put("pack.mcmeta", """{"pack":{"pack_format":$format,"description":"MineHost - new world gameplay v2"}}""")
        put("minehost-schema.txt", SCHEMA.toString() + "\n")
        val functions = functions(mode)
        // Singular registry directories since 1.21; both copies permit upgrades within the declared range.
        for (folder in listOf("function", "functions")) {
            for ((name, lines) in functions) put("data/minehost/" + folder + "/" + name + ".mcfunction", lines.joinToString("\n", postfix = "\n"))
            put("data/minecraft/tags/" + folder + "/load.json", """{"values":["minehost:load"]}""")
            put("data/minecraft/tags/" + folder + "/tick.json", """{"values":["minehost:tick"]}""")
        }
        for (folder in listOf("loot_table", "loot_tables")) {
            put("data/minehost/" + folder + "/bonus.json", """{"type":"minecraft:generic","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:bread"}]},{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:oak_sapling"}]},{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:golden_apple"}]}]}""")
        }
    }

    internal fun checkpoint(step: Int): Triple<Int, Int, Int> = Triple(0, 70 + step / 5, 2 + step * 3)

    internal fun functions(mode: String): Map<String, List<String>> {
        require(mode in modeNames)
        val out = linkedMapOf<String, List<String>>()
        val parkour = mode == "parkour"
        val spawn = if (parkour) "0 71 0" else "0 65 0"
        val endZ = if (parkour) 80 else 0
        out["load"] = listOf("execute in minecraft:overworld unless data storage minehost:setup initialized run forceload add -16 -16 16 $endZ")
        val loadedChunks = buildList {
            for (x in -16..16 step 16) for (z in -16..endZ step 16) add("if loaded $x 64 $z")
        }.joinToString(" ")
        out["tick"] = listOf(
            "execute in minecraft:overworld unless data storage minehost:setup initialized $loadedChunks run function minehost:init",
            "execute in minecraft:overworld if data storage minehost:setup initialized run function minehost:world_tick"
        )
        val init = mutableListOf("gamerule keepInventory true", "gamerule spawnRadius 0", "setworldspawn $spawn")
        if (parkour || mode == "oneblock") init += listOf("gamerule fallDamage false", "gamerule doMobSpawning false")
        when (mode) {
            "oneblock" -> {
                init += listOf("scoreboard objectives add mh_blocks dummy", "scoreboard objectives add mh_cycle dummy",
                    "scoreboard players set #blocks mh_blocks 0", "scoreboard players set #cycle mh_cycle 0",
                    "setblock 0 64 0 minecraft:grass_block",
                    "fill 2 64 -1 4 64 1 minecraft:oak_planks", "setblock 3 65 0 minecraft:chest")
                init += chestItems("3 65 0", listOf("water_bucket" to 1, "lava_bucket" to 1, "bread" to 16, "oak_sapling" to 4, "wooden_pickaxe" to 1))
            }
            "skyblock", "lucky_island" -> {
                val radius = if (mode == "skyblock") 3 else 7
                init += listOf("fill -$radius 62 -$radius $radius 63 $radius minecraft:dirt",
                    "fill -$radius 64 -$radius $radius 64 $radius minecraft:grass_block",
                    "fill 2 65 2 2 68 2 minecraft:oak_log", "fill 0 69 0 4 70 4 minecraft:oak_leaves[persistent=true]",
                    "setblock -2 65 0 minecraft:chest")
                init += chestItems("-2 65 0", listOf("ice" to 2, "lava_bucket" to 1, "bone_meal" to 16, "melon_seeds" to 4, "oak_sapling" to 4, "bread" to 16))
                if (mode == "lucky_island") {
                    init += listOf("fill 20 62 -4 28 63 4 minecraft:dirt", "fill 20 64 -4 28 64 4 minecraft:grass_block",
                        "setblock 24 65 0 minecraft:chest", "setblock 25 65 1 minecraft:enchanting_table")
                    init += chestItems("24 65 0", listOf("diamond" to 5, "golden_apple" to 4, "obsidian" to 10, "book" to 8))
                }
            }
            "parkour" -> {
                init += listOf("scoreboard objectives add mh_checkpoint dummy", "scoreboard objectives add mh_won dummy",
                    "fill -2 70 -2 2 70 2 minecraft:quartz_block", "time set noon", "gamerule doDaylightCycle false")
                for (step in 1..25) {
                    val (x, y, z) = checkpoint(step)
                    init += if (step % 5 == 0) "fill "+(x-1)+" $y $z "+(x+1)+" $y $z minecraft:emerald_block"
                        else "setblock $x $y $z minecraft:lime_concrete"
                }
                init += "fill -2 75 80 2 75 82 minecraft:diamond_block"
            }
        }
        // Do not teleport/give @a during load: players may not have joined yet.
        init += "data modify storage minehost:setup initialized set value 1b"
        // Startup reservation is temporary; normal player chunk loading takes over afterward.
        init += "forceload remove -16 -16 16 $endZ"
        out["init"] = init
        out["join"] = buildList {
            add("tp @s $spawn")
            add("spawnpoint @s $spawn")
            if (parkour) { add("scoreboard players set @s mh_checkpoint 0"); add("scoreboard players set @s mh_won 0") }
            add("tag @s add mh_joined")
        }
        val tick = mutableListOf("execute as @a[tag=!mh_joined] at @s if dimension minecraft:overworld run function minehost:join")
        tick += "execute as @a[tag=mh_joined] at @s if dimension minecraft:overworld if entity @s[y=-2048,dy=2088] run function minehost:rescue"
        out["rescue"] = if (!parkour) listOf("tp @s $spawn") else buildList {
            add("execute if score @s mh_checkpoint matches 0 run tp @s $spawn")
            for (step in listOf(5,10,15,20,25)) {
                val (x, y, z) = checkpoint(step)
                add("execute if score @s mh_checkpoint matches $step run tp @s $x " + (y+1) + " $z")
            }
        }
        if (mode == "oneblock") {
            tick += "execute if block 0 64 0 minecraft:air run function minehost:next_block"
            out["next_block"] = buildList {
                add("scoreboard players add #blocks mh_blocks 1")
                add("scoreboard players add #cycle mh_cycle 1")
                add("execute if score #cycle mh_cycle matches 10.. run scoreboard players set #cycle mh_cycle 0")
                val phases = listOf("..49" to listOf("oak_log","dirt","cobblestone","oak_log","grass_block","cobblestone","oak_log","dirt","stone","coal_ore"),
                    "50..149" to listOf("stone","iron_ore","coal_ore","spruce_log","dirt","copper_ore","stone","iron_ore","gravel","oak_log"),
                    "150.." to listOf("stone","diamond_ore","gold_ore","oak_log","iron_ore","redstone_ore","lapis_ore","dirt","emerald_ore","coal_ore"))
                for ((range, blocks) in phases) for ((index, block) in blocks.withIndex())
                    add("execute if score #blocks mh_blocks matches $range if score #cycle mh_cycle matches $index run setblock 0 64 0 minecraft:$block")
                add("scoreboard players operation #reward mh_cycle = #blocks mh_blocks")
                add("scoreboard players set #interval mh_cycle 25")
                add("scoreboard players operation #reward mh_cycle %= #interval mh_cycle")
                add("execute if score #reward mh_cycle matches 0 run loot spawn 0.5 65 0.5 loot minehost:bonus")
                add("""execute positioned 0 65 0 run title @a[distance=..64] actionbar {"text":"OneBlock • Đã đào: ","extra":[{"score":{"name":"#blocks","objective":"mh_blocks"}}]}""")
            }
        }
        if (parkour) {
            for (step in listOf(5,10,15,20,25)) {
                val (x,y,z) = checkpoint(step)
                tick += "execute as @a[tag=mh_joined,scores={mh_checkpoint=.."+(step-1)+"}] at @s if dimension minecraft:overworld if entity @s[x="+(x-1)+",y="+(y+1)+",z=$z,dx=2,dy=1,dz=0] run function minehost:checkpoint_$step"
                out["checkpoint_$step"] = listOf("scoreboard players set @s mh_checkpoint $step", "spawnpoint @s $x "+(y+1)+" $z",
                    """title @s actionbar {"text":"Checkpoint $step / 25","color":"green"}""")
            }
            tick += "execute as @a[tag=mh_joined,scores={mh_checkpoint=25,mh_won=0}] at @s if dimension minecraft:overworld if entity @s[x=-2,y=76,z=80,dx=4,dy=1,dz=2] run function minehost:finish"
            out["finish"] = listOf("scoreboard players set @s mh_won 1", "give @s minecraft:diamond 1",
                """title @s title {"text":"Hoàn thành!","color":"gold"}""", "particle minecraft:firework ~ ~1 ~ 0.4 0.4 0.4 0.05 20")
        }
        out["world_tick"] = tick
        return out
    }

    private fun chestItems(pos: String, items: List<Pair<String, Int>>): List<String> = items.mapIndexed { index, (id, count) ->
        "item replace block $pos container.$index with minecraft:$id $count"
    }
}
