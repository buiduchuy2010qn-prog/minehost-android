package com.mineserve.mobile.server

import com.mineserve.mobile.runtime.TermuxRuntime
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * server.properties 文件读写。
 *
 * - 解析/序列化交给 [ServerProperties]（保真：注释、空行、行序都保留）
 * - 服务器未首次启动时该文件可能不存在，[readProperties] 返回空 Map
 * - 通过 [exists] 判断配置文件是否已生成，供 UI 决定是否展示编辑表单
 *
 * @param termux Termux 运行时，用于定位 server 目录
 */
class ServerPropertiesManager(private val termux: TermuxRuntime) {

    /** server.properties 文件路径：home/servers/{dirName}/server.properties */
    private fun propertiesFile(dirName: String): File =
        File(termux.installer.rootDir, "home/servers/$dirName/server.properties")

    /**
     * 配置文件是否已由服务端生成。
     *
     * 服务端首次启动前该文件不存在。此状态下 UI 不应展示可编辑表单：
     * 界面里的预设值并非服务端真实生效值，编辑并保存会生成一份不完整配置，
     * 与服务端自动补齐的默认值产生错位。应引导用户先启动一次服务端。
     */
    fun exists(dirName: String): Boolean = propertiesFile(dirName).isFile

    /**
     * 读取所有属性为有序 Map（注释与空行被忽略，但不会影响原文件）。
     *
     * - 文件不存在返回空 Map
     * - 仅按第一个 `=` 分割（值本身可能含有 `=`）
     * - 保持文件中的出现顺序
     */
    fun readProperties(dirName: String): Map<String, String> =
        readDocument(dirName).toMap()

    /** 读取完整文档（保留注释与行序），文件不存在时返回空文档。 */
    fun readDocument(dirName: String): ServerProperties {
        val file = propertiesFile(dirName)
        val text = try {
            if (!file.exists()) "" else file.readText()
        } catch (e: Exception) {
            ""
        }
        return ServerProperties.parse(text)
    }

    /**
     * 把 [props] 合并写回文件（带自动 .bak 备份与原子替换）。
     */
    fun writeProperties(props: Map<String, String>, dirName: String): Boolean {
        val file = propertiesFile(dirName)
        return try {
            val parent = file.parentFile
            parent?.mkdirs()
            val doc = if (file.exists()) ServerProperties.parse(file.readText())
            else ServerProperties.of(emptyMap())
            doc.merge(props)
            if (file.isFile && file.length() > 0L) {
                runCatching { file.copyTo(File(parent, "server.properties.bak"), overwrite = true) }
            }
            val temp = File(parent, ".server.properties.mineserve.tmp")
            try {
                temp.writeText(doc.toText())
                try {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 更新单个属性（保真：只改这一行）。
     */
    fun setProperty(dirName: String, key: String, value: String): Boolean =
        writeProperties(mapOf(key to value), dirName)

    /**
     * 读取单个属性。
     */
    fun getProperty(key: String, dirName: String, default: String = ""): String {
        return readProperties(dirName)[key] ?: default
    }
}
