"""One-time, reviewable upstream adaptation. Runs only on a GitHub Actions runner."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path("app/src/main")
BASE = ROOT / "java/com/mineserve/mobile"

def edit(path, old, new, count=1):
    path = Path(path)
    text = path.read_text()
    assert text.count(old) == count, (str(path), old[:90], text.count(old))
    path.write_text(text.replace(old, new))

def span(text, signature, marker="{"):
    start = text.index(signature)
    left = text.index(marker, start) + len(marker) - 1
    depth = 1
    right = left + 1
    while depth:
        assert right < len(text), signature
        depth += (text[right] == "{") - (text[right] == "}")
        right += 1
    return left, right

def replace_body(path, signature, body, marker="{"):
    path = Path(path)
    text = path.read_text()
    left, right = span(text, signature, marker)
    path.write_text(text[:left+1] + "\n" + body + "\n    " + text[right-1:])

build = Path("app/build.gradle.kts")
edit(build, 'archivesName.set("Minexa")', 'archivesName.set("MineHost")')
edit(build, 'applicationId = "com.mineserve.mobile"', 'applicationId = "vn.minehost.android"')
edit(build, 'versionCode = 56', 'versionCode = 1')
edit(build, 'versionName = "1.3.0"', 'versionName = "0.1.0-alpha.1"')
# Test APK only. No production/release signing key has been provisioned.
edit(build, '            signingConfig = signingConfigs.getByName("debug")', '            // Release signing must be configured before production distribution.')

config = BASE / "data/McConfig.kt"
edit(config, 'val customDomain: String = "myworld.mcserver.top"', 'val customDomain: String = ""')
edit(config, 'val maxHeapMb: Int = 1024', 'val maxHeapMb: Int = 2048')
edit(config, 'val selectedJavaVersion: JavaVersion = JavaVersion.Java17', 'val selectedJavaVersion: JavaVersion = JavaVersion.Java21')

# Never write automatic EULA acceptance.
for path in [BASE / "runtime/BootstrapInstaller.kt", BASE / "server/McServerController.kt"]:
    edit(path, 'eula.writeText("eula=true\\n")', 'eula.writeText("eula=false\\n")')

controller = BASE / "server/McServerController.kt"
text = controller.read_text()
if "import kotlinx.coroutines.sync.withLock" not in text:
    text = text.replace("import kotlinx.coroutines.Dispatchers", "import kotlinx.coroutines.sync.withLock\nimport kotlinx.coroutines.Dispatchers")
left, right = span(text, "private suspend fun launchMc(")
original = text[left+1:right-1]
original = original.replace(
    "        val coreType = activeCore?.core ?: config.selectedCore",
    """        val coreType = activeCore?.core ?: config.selectedCore
        check(!termux.isMcRunning()) { "Đã có server đang chạy." }
        if (!coreType.isBedrock) EulaConsent.requireAccepted(serverDir)"""
)
text = text[:left+1] + "\n        AddonSafety.lifecycleGate.withLock {" + original + "\n        }\n    " + text[right-1:]
controller.write_text(text)

vm = BASE / "ui/McViewModel.kt"
edit(vm, "    fun startServer() {", r'''    private val _eulaRequest = MutableStateFlow<String?>(null)
    val eulaRequest: StateFlow<String?> = _eulaRequest.asStateFlow()
    private var startRequestPending = false

    fun dismissEula() { _eulaRequest.value = null }

    fun acceptEulaAndStart(requestedDir: String) {
        if (_eulaRequest.value != requestedDir || activeDirName() != requestedDir || serverState.value.isRunning) return
        _eulaRequest.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.EulaConsent.accept(repo.termuxRuntime.serverDirFor(requestedDir))
                }
                if (activeDirName() == requestedDir) startServer()
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: "Không lưu được xác nhận EULA.")
            }
        }
    }

    fun restartServer() {
        if (_stopInFlight.value || !serverState.value.isRunning) return
        val expectedDir = activeDirName()
        _stopInFlight.value = true
        viewModelScope.launch {
            var stopped = false
            try {
                stopped = controller.stopGracefully()
                if (!stopped) _errorFlow.tryEmit("Server chưa dừng. Không khởi động phiên mới để tránh mất dữ liệu.")
            } catch (e: Exception) {
                _errorFlow.tryEmit(e.message ?: "Không thể dừng server.")
            } finally { _stopInFlight.value = false }
            if (stopped && activeDirName() == expectedDir) startServer()
        }
    }

    fun startServer() {
        if (startRequestPending || serverState.value.isRunning || _stopInFlight.value) return''')
text = vm.read_text()
left, right = span(text, "    fun startServer()")
body = text[left+1:right-1]
body = body.replace("        viewModelScope.launch {\n            try {", r'''        startRequestPending = true
        viewModelScope.launch {
            try {
                val active = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }
                    ?: throw IllegalStateException("Chưa chọn server. Vào Kho cài đặt để tải core.")
                val accepted = active.core.isBedrock || withContext(Dispatchers.IO) {
                    com.mineserve.mobile.server.EulaConsent.isAccepted(repo.termuxRuntime.serverDirFor(active.dirName))
                }
                if (!accepted) {
                    _eulaRequest.value = active.dirName
                    return@launch
                }''', 1)
# Clear the request latch on success, failure and EULA deferral.
last_catch_end = body.rfind("\n            }\n        }")
assert last_catch_end >= 0
body = body[:last_catch_end] + "\n            } finally { startRequestPending = false }\n        }" + body[last_catch_end+len("\n            }\n        }"):]
text = text[:left+1] + body + text[right-1:]
vm.write_text(text)
replace_body(vm, "fun checkForUpdate(", r'''        if (!manual) return
        _lastUpdateCheckResult.value = "Bản thử nghiệm: xem build trên GitHub (cần đăng nhập repo riêng tư)."
        app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/duchilocket-oss/minehost-android/actions"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))''')
edit(BASE / "data/AppUpdateService.kt", "lovehutaocute/MineServe-Mobile", "duchilocket-oss/minehost-android", count=2)
edit(BASE / "data/AppUpdateService.kt", '"MineServeMobile-arm64-v8a-release.apk"', '"MineHost-arm64-v8a-release.apk"')

# Enforce stopped-state and core-type checks at the VM entry points too.
text = vm.read_text()
for name in ["installModFromUri", "installCuratedMod", "installModrinthMod",
             "installModrinthPlugin", "installCuratedPlugin", "installPluginFromUri", "installPluginFromUrl"]:
    left, right = span(text, "fun " + name + "(")
    is_mod = name in ["installModFromUri", "installCuratedMod", "installModrinthMod"]
    supports = "supportsMods" if is_mod else "supportsPlugins"
    guard = '''
        if (!requireServerStopped("cài plugin/mod")) return
        val targetCore = config.value.installedCores.firstOrNull { it.name == config.value.activeCoreName }?.core
        if (targetCore == null || !targetCore.SUPPORTS || targetCore.isBedrock) {
            _errorFlow.tryEmit("Chọn core Java phù hợp trước khi cài loại add-on này.")
            return
        }
'''.replace("SUPPORTS", supports)
    if "Modrinth" in name:
        guard += '''
        if (hit.server_side == "unsupported" || hit.slug.contains("optifine", ignoreCase = true)) {
            _errorFlow.tryEmit("Đây là add-on chỉ dành cho máy người chơi, không cài trên server.")
            return
        }
        if (mcVersion != currentServerVersion()) {
            _errorFlow.tryEmit("Hãy chọn phiên bản Minecraft trùng với server đang quản lý.")
            return
        }
'''
    text = text[:left+1] + guard + text[left+1:]
text = text.replace('if (core == com.mineserve.mobile.data.ServerCore.Fabric || core == com.mineserve.mobile.data.ServerCore.Quilt) "fabric"\n        else "forge"',
'''when (core) {
            ServerCore.Fabric -> "fabric"
            ServerCore.Quilt -> "quilt"
            ServerCore.NeoForge -> "neoforge"
            else -> "forge"
        }''')
text = text.replace("modrinthLoader(config.value.selectedCore)", "modrinthLoader(targetCore)")
text = text.replace('val loader = "bukkit"', 'val loader = if (targetCore == ServerCore.CraftBukkit || targetCore == ServerCore.Spigot) "bukkit" else "paper"')
text = text.replace('请先停止服务端，再$action', 'Hãy dừng server trước khi thay đổi dữ liệu: $action')
vm.write_text(text)

# Safe staging: the old JAR stays intact if download, validation or replacement fails.
pm = BASE / "server/PluginManager.kt"
for signature, mod, directory in [
    ("suspend fun installFromUrl(", False, "pluginsDirOf"),
    ("suspend fun installModFromUrl(", True, "modsDirOf"),
]:
    body = r'''        val dir = DIRECTORY(dirName).apply { mkdirs() }
        val target = File(dir, ensureJarExtension(sanitizeFileName(targetFileName, "addon.jar")))
        val result = AddonSafety.install(target, mod = MOD, loader = detectedLoader(dirName), isRunning = termux::isMcRunning) { staged ->
            downloadTo(url, staged) { downloaded, total, speed ->
                require(downloaded <= AddonSafety.MAX_BYTES && total <= AddonSafety.MAX_BYTES) { "JAR vượt giới hạn 256 MB." }
                onProgress(downloaded, total, speed)
            }
        }
        metaCache.remove(target.absolutePath)
        result'''
    replace_body(pm, signature, body.replace("DIRECTORY", directory).replace("MOD", str(mod).lower()), "): File = withContext(Dispatchers.IO) {")
for signature, mod, directory in [
    ("suspend fun installFromUri(", False, "pluginsDirOf"),
    ("suspend fun installModFromUri(", True, "modsDirOf"),
]:
    body = r'''        val dir = DIRECTORY(dirName).apply { mkdirs() }
        val target = File(dir, ensureJarExtension(sanitizeFileName(queryFileName(uri), fallbackName)))
        AddonSafety.install(target, mod = MOD, loader = detectedLoader(dirName), isRunning = termux::isMcRunning) { staged ->
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Không đọc được tệp đã chọn.")
            input.use { AddonSafety.copyBounded(it, staged) }
        }
        metaCache.remove(target.absolutePath)
        target.name'''
    replace_body(pm, signature, body.replace("DIRECTORY", directory).replace("MOD", str(mod).lower()), "withContext(Dispatchers.IO) {")
edit(pm, "    companion object {", r'''    private fun detectedLoader(dirName: String): String? = when (ServerCoreDetector.detect(termux.serverDirFor(dirName)).core) {
        com.mineserve.mobile.data.ServerCore.Fabric -> "fabric"
        com.mineserve.mobile.data.ServerCore.Forge -> "forge"
        com.mineserve.mobile.data.ServerCore.NeoForge -> "neoforge"
        com.mineserve.mobile.data.ServerCore.Quilt -> "quilt"
        else -> null
    }

    companion object {''')
edit(pm, 'val categories: List<String> = emptyList()', 'val categories: List<String> = emptyList(),\n        val server_side: String = ""')
edit(pm, 'private data class ModrinthVersion(val version_type: String = "", val files: List<ModrinthFile> = emptyList())',
'''private data class ModrinthVersion(
        val version_type: String = "",
        val files: List<ModrinthFile> = emptyList(),
        val dependencies: List<ModrinthDependency> = emptyList()
    )
    @Serializable
    private data class ModrinthDependency(
        val project_id: String? = null, val version_id: String? = null, val dependency_type: String = ""
    )''')
replace_body(pm, "fun resolveModrinthDownload(", r'''        require(slug.matches(Regex("[A-Za-z0-9_-]+"))) { "ID Modrinth không hợp lệ." }
        val gameVersions = java.net.URLEncoder.encode("[\"$mcVersion\"]", "UTF-8")
        val loaderList = when (loader) {
            "paper" -> "[\"paper\",\"spigot\",\"bukkit\"]"
            "bukkit" -> "[\"spigot\",\"bukkit\"]"
            else -> "[\"$loader\"]"
        }
        val loaders = java.net.URLEncoder.encode(loaderList, "UTF-8")
        val body = fetchModrinthText("https://api.modrinth.com/v2/project/$slug/version?game_versions=$gameVersions&loaders=$loaders")
        val versions = modrinthJson.decodeFromString<List<ModrinthVersion>>(body)
        val version = versions.firstOrNull { it.version_type == "release" } ?: versions.firstOrNull() ?: return null
        val required = version.dependencies.filter { it.dependency_type == "required" }
        check(required.isEmpty()) {
            "Cần dependency: " + required.joinToString { it.project_id ?: it.version_id ?: "xem trang dự án" } +
                ". Bản thử nghiệm chưa tự giải quyết dependency. Hãy tải bộ JAR tương thích từ trang Modrinth và nhập thủ công."
        }
        return version.files.firstOrNull { it.primary }?.url ?: version.files.firstOrNull()?.url''')

# Keep English as the base fallback; translate the primary navigation and common actions.
resources = ROOT / "res"
for source in (resources / "values-en").glob("*.xml"):
    (resources / "values" / source.name).write_text(source.read_text())
strings = resources / "values/strings.xml"
text = strings.read_text()
text = re.sub(r'(<string name="app_name"[^>]*>).*?(</string>)', r'\1MineHost\2', text)
strings.write_text(text)
(resources / "values-vi").mkdir(exist_ok=True)
translations = {
    "app_name": "MineHost", "tab_dashboard": "Tổng quan", "tab_download": "Kho cài đặt",
    "tab_server_management": "Server", "tab_players": "Người chơi", "tab_files": "Tệp",
    "tab_terminal": "Console", "tab_settings": "Quản lý"
}
root = ET.Element("resources")
for name, value in translations.items():
    ET.SubElement(root, "string", {"name": name}).text = value
ET.ElementTree(root).write(resources / "values-vi/strings.xml", encoding="utf-8", xml_declaration=True)
# English-locale devices must also see MineHost branding.
p = resources / "values-en/strings.xml"
p.write_text(re.sub(r'(<string name="app_name"[^>]*>).*?(</string>)', r'\1MineHost\2', p.read_text()))

color = BASE / "ui/theme/Color.kt"
for old, new in [
    ("0xFF3B4C9C", "0xFF146C43"), ("0xFF2B3A7A", "0xFF0D4D30"),
    ("0xFFEEF0FA", "0xFFE1F3E8"), ("0xFF2A3563", "0xFF174D35")
]:
    edit(color, old, new)
theme = BASE / "ui/theme/Theme.kt"
theme.write_text(theme.read_text().replace("0xFF9FB0F0", "0xFF8BD9AC").replace("0xFF1B2447", "0xFF06301F").replace("0xFFD5DCFA", "0xFFB9F2CE"))
print("MineHost source adaptation complete.")
