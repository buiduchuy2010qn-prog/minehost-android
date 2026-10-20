package com.mineserve.mobile.server
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MineHostSafetyTest {
    @get:Rule val temp = TemporaryFolder()
    private fun jar(name: String, entries: Map<String, String>): File {
        val file = File(temp.root, name)
        JarOutputStream(file.outputStream()).use { out ->
            for ((path, content) in entries) {
                out.putNextEntry(JarEntry(path))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }
    @Test fun noImplicitEulaAcceptance() {
        val server = temp.newFolder()
        assertFalse(EulaConsent.isAccepted(server))
        File(server, "eula.txt").writeText("eula=true\n")
        assertFalse(EulaConsent.isAccepted(server))
    }
    @Test fun explicitConsentIsPerServerAndCanBeRevoked() {
        val server = temp.newFolder()
        EulaConsent.accept(server)
        assertTrue(EulaConsent.isAccepted(server))
        assertFalse(EulaConsent.isAccepted(temp.newFolder()))
        File(server, "eula.txt").writeText("eula=false\n")
        assertFalse(EulaConsent.isAccepted(server))
        assertThrows(IllegalStateException::class.java) { EulaConsent.requireAccepted(server) }
    }
    @Test fun validPaperPluginIsAccepted() {
        AddonSafety.validate(jar("paper.jar", mapOf("paper-plugin.yml" to "name: Test")), mod = false)
    }
    @Test fun plainJarIsNotAPlugin() {
        val file = jar("plain.jar", mapOf("example.txt" to "test"))
        assertThrows(IllegalArgumentException::class.java) { AddonSafety.validate(file, false) }
    }
    @Test fun clientOnlyFabricModIsRejected() {
        val file = jar("client.jar", mapOf("fabric.mod.json" to """{"id":"example","environment":"client"}"""))
        assertThrows(IllegalArgumentException::class.java) { AddonSafety.validate(file, true, "fabric") }
    }
    @Test fun fabricModDoesNotInstallAsForge() {
        val file = jar("fabric.jar", mapOf("fabric.mod.json" to """{"id":"example","environment":"*"}"""))
        AddonSafety.validate(file, true, "fabric")
        assertThrows(IllegalArgumentException::class.java) { AddonSafety.validate(file, true, "forge") }
    }
    @Test fun optifineIsRejectedEvenWhenRenamed() {
        val file = jar("renamed.jar", mapOf("optifine/Config.class" to "x", "plugin.yml" to "name: Test"))
        assertThrows(IllegalArgumentException::class.java) { AddonSafety.validate(file, false) }
    }
    @Test fun failedInstallPreservesOldFileAndCleansStage() = runBlocking {
        val target = jar("installed.jar", mapOf("plugin.yml" to "name: Original"))
        val before = target.readBytes()
        val result = runCatching {
            AddonSafety.install(target, false) { it.writeText("<html>bad download</html>") }
        }
        assertTrue(result.isFailure)
        assertArrayEquals(before, target.readBytes())
        assertFalse(temp.root.listFiles()!!.any { it.extension == "part" })
    }
    @Test fun successfulInstallReplacesOnlyAfterValidation() = runBlocking {
        val source = jar("new.jar", mapOf("plugin.yml" to "name: Updated"))
        val target = File(temp.root, "target.jar")
        AddonSafety.install(target, false) { source.copyTo(it, overwrite = true) }
        assertArrayEquals(source.readBytes(), target.readBytes())
    }
    @Test fun runningServerBlocksInstallBeforeDownload() = runBlocking {
        var called = false
        val result = runCatching {
            AddonSafety.install(File(temp.root, "target.jar"), false, isRunning = { true }) { called = true }
        }
        assertTrue(result.isFailure)
        assertFalse(called)
    }
}
