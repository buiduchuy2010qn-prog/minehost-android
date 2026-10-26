package com.mineserve.mobile.server

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NewWorldCreationTest {
    @get:Rule val temp = TemporaryFolder()

    private fun server(): File = temp.newFolder().also {
        File(it, "server.properties").writeText("# Keep settings\nlevel-name=world\nmax-players=17\nmotd=My server\n")
        File(it, "world/region").mkdirs()
        File(it, "world/region/r.0.0.mca").writeText("original terrain")
        File(it, "world/playerdata").mkdirs()
        File(it, "world/playerdata/player.dat").writeText("original inventory")
    }

    private fun snapshot(dir: File): Map<String, String> = dir.walkTopDown().filter { it.isFile }
        .associate { it.relativeTo(dir).invariantSeparatorsPath to it.readBytes().joinToString(",") }

    private suspend fun create(server: File, mode: String = "oneblock", activate: Boolean = true) =
        ProHostFeatures.createPlayableModeWorldOneTap(server, mode, activate) { _, _ -> }.getOrThrow()

    @Test fun allModesCreateSeparateWorldsAndNeverRewriteExistingFiles() = runBlocking {
        for (mode in ProHostFeatures.PLAYABLE_WORLD_MODES) {
            val root = server()
            val existing = File(root, mode.folderName)
            File(existing, "region").mkdirs()
            File(existing, "datapacks/minehost_" + mode.id).mkdirs()
            File(existing, "datapacks/minehost_" + mode.id + "/pack.mcmeta").writeText("old datapack")
            File(existing, "region/r.0.0.mca").writeText("old build")
            val before = snapshot(root).filterKeys { it != "server.properties" }
            val result = create(root, mode.id)
            assertEquals(mode.folderName + "_2", result.folderName)
            val after = snapshot(root)
            before.forEach { (path, bytes) -> assertEquals(path, bytes, after[path]) }
            assertEquals(result.folderName, ServerProperties.parse(File(root, "server.properties").readText())["level-name"])
            assertEquals("17", ServerProperties.parse(File(root, "server.properties").readText())["max-players"])
            val listed = Worlds.list(root, result.folderName)
            assertTrue(listed.any { it.name == result.folderName && it.isActive })
            assertTrue(listed.any { it.name == "world" && !it.isActive })
            assertTrue(listed.any { it.name == mode.folderName && !it.isActive })
        }
    }

    @Test fun everyCreateGetsANewAutomaticName() = runBlocking {
        val root = server()
        val names = (1..3).map { create(root).folderName }
        assertEquals(listOf("world_oneblock", "world_oneblock_2", "world_oneblock_3"), names)
        assertEquals(4, Worlds.list(root, names.last()).size)
    }

    @Test fun existingUnrecognizedFolderAndBothDimensionsAreReserved() = runBlocking {
        val root = server()
        File(root, "world_oneblock").mkdir()
        File(root, "world_oneblock_2_nether").mkdir()
        File(root, "world_oneblock_3_the_end").writeText("keep dimension path")
        val before = snapshot(root).filterKeys { it != "server.properties" }
        assertEquals("world_oneblock_4", create(root).folderName)
        assertTrue(File(root, "world_oneblock_2_nether").isDirectory)
        before.forEach { (path, bytes) -> assertEquals(bytes, snapshot(root)[path]) }
    }

    @Test fun missingCurrentWorldNameIsStillReserved() = runBlocking {
        val root = server()
        File(root, "server.properties").writeText("level-name:world_oneblock\n")
        assertEquals("world_oneblock_2", create(root).folderName)
        assertFalse(File(root, "world_oneblock").exists())
    }

    @Test fun brokenSymlinkCannotRedirectCreation() = runBlocking {
        val root = server()
        Files.createSymbolicLink(File(root, "world_oneblock").toPath(), File(root, "missing-target").toPath())
        assertEquals("world_oneblock_2", create(root).folderName)
        assertFalse(File(root, "missing-target").exists())
        assertTrue(Files.isSymbolicLink(File(root, "world_oneblock").toPath()))
    }

    @Test fun parallelRequestsNeverShareAWorld() = runBlocking {
        val root = server()
        val names = (1..8).map { async(Dispatchers.Default) { create(root).folderName } }.awaitAll()
        assertEquals(8, names.distinct().size)
        names.forEach { assertTrue(File(root, it + "/region").isDirectory) }
    }

    @Test fun prepareFailurePreservesOldWorldAndConfiguration() = runBlocking {
        val root = server()
        val before = snapshot(root)
        val result = ProHostFeatures.createPlayableModeWorldOneTap(root, "oneblock") { progress, _ ->
            if (progress >= 0.42f) throw IOException("simulated disk failure")
        }
        assertTrue(result.isFailure)
        assertEquals(before, snapshot(root))
        assertFalse(File(root, "world_oneblock").exists())
        assertFalse(root.listFiles()!!.any { it.name.startsWith(".minehost-new-world-") })
    }

    @Test fun failedCommitGuardPreservesOldSelection() = runBlocking {
        val root = server()
        val before = snapshot(root)
        var allow = true
        val result = ProHostFeatures.createPlayableModeWorldOneTap(
            root, "oneblock", checkCanCreate = { check(allow) { "server switched or started" } }
        ) { progress, _ -> if (progress >= 0.80f) allow = false }
        assertTrue(result.isFailure)
        assertEquals(before, snapshot(root))
        assertFalse(File(root, "world_oneblock").exists())
    }

    @Test fun runningServerGuardRejectsBeforeCreatingAnything() = runBlocking {
        val root = server()
        val before = snapshot(root)
        val result = ProHostFeatures.createPlayableModeWorldOneTap(
            root, "oneblock", checkCanCreate = { error("server is running") }
        ) { _, _ -> }
        assertTrue(result.isFailure)
        assertEquals(before, snapshot(root))
        assertFalse(File(root, "world_oneblock").exists())
    }

    @Test fun cancellationIsRethrownAndDoesNotChangeMap() = runBlocking {
        val root = server()
        val before = snapshot(root)
        val result = runCatching {
            ProHostFeatures.createPlayableModeWorldOneTap(root, "skyblock") { progress, _ ->
                if (progress >= 0.42f) throw CancellationException("cancel creation")
            }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(before, snapshot(root))
        assertFalse(File(root, "world_skyblock").exists())
    }

    @Test fun newMapWithoutActivationLeavesPropertiesByteIdentical() = runBlocking {
        val root = server()
        val before = File(root, "server.properties").readBytes()
        val created = create(root, activate = false)
        assertArrayEquals(before, File(root, "server.properties").readBytes())
        assertTrue(Worlds.list(root, "world").any { it.name == created.folderName && !it.isActive })
    }

    @Test fun generatedLoadFunctionDoesNotReinitializeWorldOnEveryReload() = runBlocking {
        for (mode in ProHostFeatures.PLAYABLE_WORLD_MODES) {
            val root = server()
            val created = create(root, mode.id)
            val pack = File(root, created.folderName + "/datapacks/minehost_" + mode.id)
            for (folder in listOf("function", "functions")) {
                assertEquals("""{"values":["minehost:load"]}""", File(pack, "data/minecraft/tags/" + folder + "/load.json").readText())
                assertEquals("execute unless data storage minehost:setup initialized run function minehost:init\n",
                    File(pack, "data/minehost/" + folder + "/load.mcfunction").readText())
                assertTrue(File(pack, "data/minehost/" + folder + "/init.mcfunction").readText()
                    .endsWith("data modify storage minehost:setup initialized set value 1b\n"))
            }
        }
    }

    @Test fun invalidModeFailsWithoutCreatingAFallbackWorld() = runBlocking {
        val root = server()
        val before = snapshot(root)
        assertTrue(ProHostFeatures.createPlayableModeWorldOneTap(root, "typo") { _, _ -> }.isFailure)
        assertEquals(before, snapshot(root))
    }

    @Test fun propertiesEditedDuringPreparationAreNotOverwritten() = runBlocking {
        val root = server()
        val updated = "level-name=world\nmotd=Changed elsewhere\n"
        val result = runCatching {
            NewWorldCreation.create(root, "world_new", emptyMap()) {
                File(root, "server.properties").writeText(updated)
            }
        }
        assertTrue(result.isFailure)
        assertEquals(updated, File(root, "server.properties").readText())
        assertFalse(File(root, "world_new").exists())
    }

    @Test fun newDimensionCollisionBeforeCommitDoesNotDeleteExistingDimension() = runBlocking {
        val root = server()
        val before = File(root, "server.properties").readBytes()
        val result = runCatching {
            NewWorldCreation.create(root, "world_new", emptyMap()) { world ->
                File(root, world.name + "_nether").mkdir()
                File(root, world.name + "_nether/keep.dat").writeText("external dimension")
            }
        }
        assertTrue(result.isFailure)
        assertArrayEquals(before, File(root, "server.properties").readBytes())
        assertEquals("external dimension", File(root, "world_new_nether/keep.dat").readText())
        assertFalse(File(root, "world_new").exists())
    }

    @Test fun completedCreationDoesNotReportFailureWhenFinalProgressListenerThrows() = runBlocking {
        val root = server()
        val result = ProHostFeatures.createPlayableModeWorldOneTap(root, "parkour") { progress, _ ->
            if (progress == 1f) error("UI is gone")
        }
        assertTrue(result.isSuccess)
        assertEquals(result.getOrThrow().folderName, ServerProperties.parse(File(root, "server.properties").readText())["level-name"])
    }

    @Test fun seedWorldCreationUsesTheSameProtectedAllocation() = runBlocking {
        val root = server()
        val before = snapshot(File(root, "world"))
        val created = NewWorldCreation.create(root, "world", mapOf("level-seed" to "123", "level-type" to "minecraft:normal"))
        assertEquals("world_2", created.name)
        assertEquals(before, snapshot(File(root, "world")))
        assertEquals("123", ServerProperties.parse(File(root, "server.properties").readText())["level-seed"])
    }
}
