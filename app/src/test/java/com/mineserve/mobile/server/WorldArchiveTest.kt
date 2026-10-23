package com.mineserve.mobile.server
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorldArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private val free = 8L * 1024 * 1024 * 1024
    private fun zip(vararg entries: Pair<String, String>): File {
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, value) ->
                out.putNextEntry(ZipEntry(name)); out.write(value.toByteArray()); out.closeEntry()
            }
        }
        return file
    }
    @Test fun acceptsJavaWorldAtRoot() { WorldArchive.validate(zip("level.dat" to "data", "region/r.0.0.mca" to "region"), free) }
    @Test fun acceptsWorldAndDimensions() {
        WorldArchive.validate(zip("map/level.dat" to "data", "map_nether/level.dat" to "data", "map_the_end/level.dat" to "data"), free)
    }
    @Test fun detectsRegionWithoutDirectoryEntry() {
        val z = zip("map/region/r.0.0.mca" to "data")
        assertEquals("map", Worlds.inspectZip(z).primary)
        WorldArchive.validate(z, free)
    }
    @Test fun rejectsBedrockForJava() {
        assertThrows(IllegalArgumentException::class.java) {
            WorldArchive.validate(zip("world/level.dat" to "data", "world/db/CURRENT" to "db"), free)
        }
    }
    @Test fun acceptsBedrockOnlyWhenRequested() {
        WorldArchive.validate(zip("world/level.dat" to "data", "world/db/CURRENT" to "db"), free, true)
    }
    @Test fun rejectsZipSlip() {
        assertThrows(IllegalArgumentException::class.java) {
            WorldArchive.validate(zip("world/level.dat" to "data", "world/../../config" to "bad"), free)
        }
    }
    @Test fun rejectsAbsolutePaths() {
        assertThrows(IllegalArgumentException::class.java) { WorldArchive.validate(zip("/world/level.dat" to "data"), free) }
    }
    @Test fun rejectsAmbiguousWorlds() {
        assertThrows(IllegalArgumentException::class.java) {
            WorldArchive.validate(zip("one/level.dat" to "data", "two/level.dat" to "data"), free)
        }
    }
    @Test fun requiresDiskReserve() {
        assertThrows(IllegalArgumentException::class.java) { WorldArchive.validate(zip("level.dat" to "data"), WorldArchive.DISK_RESERVE_BYTES) }
    }
    @Test fun extractionLimitRollsBack() {
        val root = temp.newFolder()
        assertFalse(Worlds.importWorldZip(zip("world/level.dat" to "too much data"), root, "new", maxExpandedBytes = 3))
        assertFalse(File(root, "new").exists())
    }
    @Test fun failurePreservesExistingWorld() {
        val root = temp.newFolder()
        val old = File(root, "old").apply { mkdir() }
        File(old, "level.dat").writeText("keep")
        assertFalse(Worlds.importWorldZip(zip("world/level.dat" to "replacement"), root, "old"))
        assertEquals("keep", File(old, "level.dat").readText())
    }
    @Test fun cancellationCleansOnlyNewFiles() {
        val root = temp.newFolder()
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            Worlds.importWorldZip(zip("world/level.dat" to "data"), root, "new",
                checkCancelled = { throw java.util.concurrent.CancellationException() })
        }
        assertFalse(File(root, "new").exists())
    }
    @Test fun corruptCrcRollsBackInsteadOfImportingDamagedWorld() {
        val z = zip("world/level.dat" to "data")
        val bytes = z.readBytes()
        val central = (0..bytes.size - 4).first {
            bytes[it] == 0x50.toByte() && bytes[it + 1] == 0x4b.toByte() &&
                bytes[it + 2] == 0x01.toByte() && bytes[it + 3] == 0x02.toByte()
        }
        bytes[central + 16] = (bytes[central + 16].toInt() xor 1).toByte()
        z.writeBytes(bytes)
        val root = temp.newFolder()
        assertFalse(Worlds.importWorldZip(z, root, "new"))
        assertFalse(File(root, "new").exists())
    }
}
