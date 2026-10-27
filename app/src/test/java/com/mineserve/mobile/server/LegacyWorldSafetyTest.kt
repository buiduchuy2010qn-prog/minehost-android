package com.mineserve.mobile.server

import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyWorldSafetyTest {
    @get:Rule val temp = TemporaryFolder()
    private fun world(): File {
        val world = temp.newFolder("world_oneblock")
        File(world, "region").mkdir()
        File(world, "region/r.0.0.mca").writeText("player house")
        File(world, "playerdata").mkdir()
        File(world, "playerdata/player.dat").writeText("player inventory")
        for (folder in listOf("function", "functions")) {
            val file = File(world, "datapacks/minehost_oneblock/data/minecraft/tags/" + folder + "/load.json")
            file.parentFile!!.mkdirs()
            file.writeText("""{"replace":false,"values":["minehost:init","other:keep",{"id":"other:optional","required":false}]}""")
        }
        File(world, "datapacks/minehost_oneblock/pack.mcmeta").writeText("old metadata")
        File(world, "datapacks/minehost_oneblock/loop.txt").writeText("original progress logic")
        File(temp.root, "world_oneblock_nether").mkdir()
        File(temp.root, "world_oneblock_nether/level.dat").writeText("nether")
        File(temp.root, "world_oneblock_the_end").mkdir()
        File(temp.root, "world_oneblock_the_end/level.dat").writeText("end")
        return world
    }
    private fun snapshot(world: File) = world.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(world).invariantSeparatorsPath to it.readText()
    }
    @Test fun inspectionDoesNotWriteAndFindsBothDirectoryFormats() {
        val world = world(); val before = snapshot(world)
        assertEquals(2, LegacyWorldSafety.inspect(world).unsafeTags.size)
        assertEquals(before, snapshot(world))
    }
    @Test fun repairBacksUpDimensionsAndOnlyRemovesAutomaticInit() = runBlocking {
        val world = world(); val before = snapshot(world)
        val report = LegacyWorldSafety.inspect(world)
        val archive = LegacyWorldSafety.repair(world, temp.newFolder("backups"), {})
        val after = snapshot(world)
        before.filterKeys { it !in report.unsafeTags }.forEach { (file, contents) -> assertEquals(contents, after[file]) }
        assertFalse(LegacyWorldSafety.inspect(world).canRepair)
        report.unsafeTags.forEach {
            assertFalse(File(world, it).readText().contains("minehost:init"))
            assertTrue(File(world, it).readText().contains("other:keep"))
            assertTrue(File(world, it).readText().contains("other:optional"))
        }
        ZipFile(archive).use { zip ->
            assertNotNull(zip.getEntry("world_oneblock_nether/level.dat"))
            assertNotNull(zip.getEntry("world_oneblock_the_end/level.dat"))
            before.forEach { (file, contents) -> assertEquals(contents, zip.getInputStream(zip.getEntry(world.name + "/" + file)).bufferedReader().use { it.readText() }) }
        }
    }
    @Test fun failedBackupNeverChangesDatapack() = runBlocking {
        val world = world(); val before = snapshot(world)
        val result = runCatching { LegacyWorldSafety.repair(world, temp.newFolder("backups"), {}, backup = { false }) }
        assertTrue(result.isFailure); assertEquals(before, snapshot(world))
    }
    @Test fun corruptBackupNeverChangesDatapack() = runBlocking {
        val world = world(); val before = snapshot(world)
        val result = runCatching { LegacyWorldSafety.repair(world, temp.newFolder("backups"), {}, backup = { it.writeText("broken zip"); true }) }
        assertTrue(result.isFailure); assertEquals(before, snapshot(world))
    }
    @Test fun runningServerGuardRejectsBeforeBackup() = runBlocking {
        val world = world(); val before = snapshot(world); var called = false
        val result = runCatching { LegacyWorldSafety.repair(world, temp.newFolder("backups"), { error("running") }, backup = { called = true; true }) }
        assertTrue(result.isFailure); assertFalse(called); assertEquals(before, snapshot(world))
    }
    @Test fun changeOfServerAfterBackupPreventsRepair() = runBlocking {
        val world = world(); val before = snapshot(world); var checks = 0
        val result = runCatching { LegacyWorldSafety.repair(world, temp.newFolder("backups"), { check(++checks == 1) }) }
        assertTrue(result.isFailure); assertEquals(before, snapshot(world))
    }
    @Test fun partialRepairRollsBackAlreadyWrittenTagFiles() = runBlocking {
        val world = world(); val before = snapshot(world); var checks = 0
        val backups = temp.newFolder("backups")
        val result = runCatching { LegacyWorldSafety.repair(world, backups, { check(++checks < 4) }) }
        assertTrue(result.isFailure); assertEquals(before, snapshot(world))
        assertTrue(backups.listFiles()!!.any { it.extension == "zip" })
    }
    @Test fun repairedWorldDoesNotOfferRepairAgain() = runBlocking {
        val world = world(); val backups = temp.newFolder("backups")
        LegacyWorldSafety.repair(world, backups, {})
        assertFalse(LegacyWorldSafety.inspect(world).canRepair)
        assertTrue(runCatching { LegacyWorldSafety.repair(world, backups, {}) }.isFailure)
        assertEquals(1, backups.listFiles()!!.size)
    }
    @Test fun newPackIsNotTreatedAsUnsafeLegacyPack() {
        val world = temp.newFolder()
        PlayableWorldPack.write(world, "oneblock", "1.21.1")
        assertFalse(LegacyWorldSafety.inspect(world).canRepair)
    }

    @Test fun linkedWorldDataIsRejectedBeforeBackup() = runBlocking {
        val world = world(); val target = temp.newFile("outside.dat").apply { writeText("untouched") }
        java.nio.file.Files.createSymbolicLink(File(world, "linked.dat").toPath(), target.toPath())
        var called = false
        val result = runCatching { LegacyWorldSafety.repair(world, temp.newFolder("backups"), {}, backup = { called = true; true }) }
        assertTrue(result.isFailure); assertFalse(called)
        assertEquals("untouched", target.readText())
        assertTrue(LegacyWorldSafety.inspect(world).canRepair)
    }
    @Test fun cancellationAfterBackupDoesNotChangeOldMap() = runBlocking {
        val world = world(); val before = snapshot(world); var checks = 0
        val result = runCatching { LegacyWorldSafety.repair(world, temp.newFolder("backups"), {
            if (++checks == 3) throw kotlinx.coroutines.CancellationException("cancel repair")
        }) }
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals(before, snapshot(world))
    }
}
