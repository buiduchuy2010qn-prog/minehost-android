package com.mineserve.mobile.server

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlayableWorldPackTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun namesAreReadableWithoutRenamingUserFolders() {
        assertEquals("OneBlock #2", PlayableWorldPack.displayName("world_oneblock_2"))
        assertEquals("SkyBlock #1", PlayableWorldPack.displayName("world_skyblock"))
        assertEquals("my_world", PlayableWorldPack.displayName("my_world"))
        assertEquals("world_oneblock_custom", PlayableWorldPack.displayName("world_oneblock_custom"))
    }
    @Test fun exactVersionFormatsAndUnknownVersionsFailClosed() {
        assertEquals(26, PlayableWorldPack.formatFor("1.20.4"))
        assertEquals(48, PlayableWorldPack.formatFor("1.21.1"))
        assertEquals(61, PlayableWorldPack.formatFor("1.21.4"))
        assertNull(PlayableWorldPack.formatFor("1.21.5"))
        assertNull(PlayableWorldPack.formatFor("unknown"))
    }
    @Test fun everyModeUsesVoidWorldSettingsAndNoStructures() {
        PlayableWorldPack.modeNames.keys.forEach {
            val properties = PlayableWorldPack.properties(it)
            assertEquals("minecraft:flat", properties["level-type"])
            assertTrue(properties.getValue("generator-settings").contains("minecraft:air"))
            assertEquals("false", properties["generate-structures"])
        }
    }
    @Test fun playerInitializationAndRescueAreDimensionScoped() {
        PlayableWorldPack.modeNames.keys.forEach {
            val functions = PlayableWorldPack.functions(it)
            assertFalse(functions.getValue("init").any { line -> line.startsWith("tp @a") || line.startsWith("give @a") })
            assertTrue(functions.getValue("world_tick").filter { line -> line.contains("as @a") }.all { line -> line.contains("if dimension minecraft:overworld") })
            assertTrue(functions.getValue("join").contains("tag @s add mh_joined"))
        }
    }
    @Test fun oneblockHasThreePhasesAndRewardsWithoutResetOnLoad() {
        val functions = PlayableWorldPack.functions("oneblock")
        val commands = functions.getValue("next_block").joinToString("\n")
        listOf("..49", "50..149", "150..", "minecraft:diamond_ore", "minecraft:iron_ore", "loot minehost:bonus").forEach { assertTrue(it, commands.contains(it)) }
        assertFalse(functions.getValue("load").any { it.contains("scoreboard players set") })
        assertTrue(functions.getValue("tick").any { it.contains("unless data storage minehost:setup initialized") })
    }
    @Test fun parkourJumpsStayReachableAndCheckpointHeightsMatch() {
        var last = Triple(0, 70, 2)
        for (step in 1..25) {
            val current = PlayableWorldPack.checkpoint(step)
            assertTrue(current.third - last.third <= 3)
            assertTrue(current.second - last.second <= 1)
            assertTrue(kotlin.math.abs(current.first - last.first) <= 2)
            last = current
        }
        val functions = PlayableWorldPack.functions("parkour")
        listOf(5,10,15,20,25).forEach { step ->
            assertTrue(functions.getValue("checkpoint_" + step).any { it.startsWith("spawnpoint @s") })
            assertTrue(functions.getValue("rescue").any { it.contains("matches " + step + " run tp") })
        }
    }
    @Test fun writingNeverOverwritesAnExistingPack() {
        val world = temp.newFolder()
        PlayableWorldPack.write(world, "skyblock", "1.21.1")
        val marker = File(world, "datapacks/minehost_skyblock/keep.txt").apply { writeText("keep") }
        assertThrows(IllegalStateException::class.java) { PlayableWorldPack.write(world, "skyblock", "1.21.1") }
        assertEquals("keep", marker.readText())
    }
    @Test fun exportExactAppGeneratedFixturesForRealMinecraftChecks() = runBlocking {
        for (version in listOf("1.20.4", "1.21.1", "1.21.4")) for (mode in PlayableWorldPack.modeNames.keys) {
            val server = File("build/generated/playable/" + version + "/" + mode)
            server.mkdirs()
            File(server, "server.properties").writeText("server-ip=127.0.0.1\nonline-mode=false\nenable-rcon=true\nrcon.password=minehost-local-test\nrcon.port=25575\nserver-port=25565\nview-distance=2\nsimulation-distance=2\nmax-players=2\nlevel-name=world\nsync-chunk-writes=true\n")
            val result = ProHostFeatures.createPlayableModeWorldOneTap(server, mode, minecraftVersion = version) { _, _ -> }.getOrThrow()
            assertTrue(File(server, result.folderName + "/datapacks/minehost_" + mode + "/pack.mcmeta").isFile)
        }
    }
}
