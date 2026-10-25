package com.mineserve.mobile.server

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Bộ phân tích báo cáo lỗi sập (Crash Report) ngoại tuyến bằng tiếng Việt.
 */
object CrashReportAnalyzer {

    enum class Level { Identified, Possible, Reminder }

    data class Finding(val level: Level, val label: String, val detail: String, val evidence: String, val suggestion: String)

    data class Analysis(
        val title: String,
        val firstException: String?,
        val causedBy: List<String>,
        val exitCode: Int?,
        val components: List<String>,
        val findings: List<Finding>
    ) { val primaryLabel: String get() = findings.firstOrNull()?.label ?: "Chưa xác định rõ nguyên nhân" }

    private val exception = Regex("""(?:[A-Za-z0-9_.]+)?(?:Exception|Error|Throwable)(?::[^\n]*)?""")
    private val causedBy = Regex("""Caused by: ([^\n]+)""")
    private val exitCode = Regex("""(?:exit(?: code)?|退出码|Mã thoát|mã thoát)\s*[:=]\s*(-?\d+)""", RegexOption.IGNORE_CASE)
    private val component = Regex("""([A-Za-z0-9_.-]+\.(?:jar|zip|class))""")
    private val classVersion = Regex("""(?:class file version|major\.minor version)\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val requiresJava = Regex("""(?:requires|need)\s+Java\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val javaOrHigher = Regex("""Java\s+(\d+)\s+or\s+higher""", RegexOption.IGNORE_CASE)

    private val BUILTIN_CLASS_VERSIONS: Map<Int, Int> =
        (45..70).associateWith { it - 44 }

    private class Rule(
        val id: String,
        val level: Level,
        val contains: List<String>,
        val regex: Regex?,
        val exitCodes: Set<Int>,
        val label: String,
        val detail: String,
        val evidence: String,
        val suggest: String
    )

    @Volatile
    private var rules: List<Rule> = emptyList()

    @Volatile
    private var classVersions: Map<Int, Int> = BUILTIN_CLASS_VERSIONS

    @Volatile
    private var loaded: Boolean = false

    @Volatile
    var logger: ((String) -> Unit)? = null

    private fun warn(message: String) {
        logger?.invoke(message)
    }

    val isLoaded: Boolean get() = loaded

    val ruleCount: Int get() = rules.size

    fun customRulesDir(context: Context): File = File(context.filesDir, "error_rules")

    fun initialize(context: Context) {
        val builtin = try {
            context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            warn("Không đọc được bộ quy tắc phân tích lỗi tích hợp sẵn: ${e.message}")
            null
        }
        val custom = try {
            customRulesDir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }
                ?.sortedBy { it.name }
                ?.mapNotNull { f ->
                    if (f.length() > MAX_CUSTOM_RULE_BYTES) {
                        warn("Quy tắc tùy chỉnh ${f.name} vượt quá ${MAX_CUSTOM_RULE_BYTES / 1024} KB, đã bỏ qua")
                        return@mapNotNull null
                    }
                    runCatching { f.readText() }
                        .onFailure { warn("Đọc quy tắc tùy chỉnh ${f.name} thất bại: ${it.message}") }
                        .getOrNull()
                }
                .orEmpty()
        } catch (e: Exception) {
            warn("Quét thư mục quy tắc tùy chỉnh thất bại: ${e.message}")
            emptyList()
        }
        loadFromJson(builtin, *custom.toTypedArray())
    }

    fun loadFromJson(vararg jsons: String?) {
        val builtinRules = mutableListOf<Rule>()
        val overrides = linkedMapOf<String, Rule>()
        var versions: Map<Int, Int>? = null
        var parsedAny = false

        for ((index, json) in jsons.withIndex()) {
            if (json.isNullOrBlank()) continue
            val parsed = runCatching { Json.parseToJsonElement(json) as? JsonObject }
                .onFailure { warn("Phân tích JSON quy tắc lỗi thất bại: ${it.message}") }
                .getOrNull()
            if (parsed == null) {
                warn("Tệp quy tắc JSON thứ ${index + 1} không thể phân tích")
                if (index == 0) {
                    warn("Không thể phân tích quy tắc tích hợp sẵn, giữ nguyên ${rules.size} quy tắc hiện có")
                    loaded = true
                    return
                }
                continue
            }
            parsedAny = true
            parseVersions(parsed)?.let { table ->
                versions = (versions ?: emptyMap()) + table
            }
            val ruleList = parseRules(parsed)
            if (index == 0) {
                builtinRules += ruleList
            } else {
                ruleList.forEach { overrides[it.id] = it }
            }
        }

        if (!parsedAny) {
            warn("Không có tệp JSON quy tắc nào hợp lệ, giữ nguyên ${rules.size} quy tắc hiện có")
            loaded = true
            return
        }

        val effective = buildList {
            addAll(overrides.values)
            addAll(builtinRules.filter { it.id !in overrides })
        }
        classVersions = versions?.let { BUILTIN_CLASS_VERSIONS + it } ?: BUILTIN_CLASS_VERSIONS
        rules = effective
        loaded = true
    }

    private fun parseVersions(root: JsonObject): Map<Int, Int>? {
        val obj = root["classVersions"] as? JsonObject ?: return null
        val map = obj.mapNotNull { (k, v) ->
            val key = k.toIntOrNull() ?: return@mapNotNull null
            val value = (v as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            key to value
        }.toMap()
        return map.ifEmpty { null }
    }

    private fun parseRules(root: JsonObject): List<Rule> {
        val array = root["rules"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            val label = obj.str("label") ?: return@mapNotNull null
            val contains = (obj["contains"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                .orEmpty()
            val regex = obj.str("regex")?.let { pattern ->
                runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }
                    .onFailure { warn("Biểu thức chính quy của quy tắc $id không hợp lệ: ${it.message}") }
                    .getOrNull()
            }
            val exitCodes = (obj["exitCodes"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
                ?.toSet()
                .orEmpty()
            if (contains.isEmpty() && regex == null && exitCodes.isEmpty()) return@mapNotNull null
            Rule(
                id = id,
                level = parseLevel(obj.str("level")),
                contains = contains,
                regex = regex,
                exitCodes = exitCodes,
                label = label,
                detail = obj.str("detail").orEmpty(),
                evidence = obj.str("evidence").orEmpty(),
                suggest = obj.str("suggest").orEmpty()
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun parseLevel(raw: String?): Level = when (raw?.lowercase()) {
        "possible" -> Level.Possible
        "reminder" -> Level.Reminder
        else -> Level.Identified
    }

    fun requiredJavaVersion(text: String): Int? {
        classVersion.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { version ->
            classVersions[version]?.let { return it }
        }
        requiresJava.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        javaOrHigher.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return null
    }

    fun analyze(text: String): Analysis {
        val sanitizedText = CrashReportManager.localizeLegacyChineseText(text)
        val first = exception.find(sanitizedText)?.value?.trim()
        val causes = causedBy.findAll(sanitizedText).map { it.groupValues[1].trim() }.distinct().take(6).toList()
        val code = exitCode.find(sanitizedText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val components = component.findAll(sanitizedText).map { it.groupValues[1] }.distinct().take(12).toList()

        val findings = mutableListOf<Finding>()
        for (rule in rules) {
            val hit = rule.regex?.find(sanitizedText)
            val matched = (rule.contains.isNotEmpty() && rule.contains.any { sanitizedText.contains(it, ignoreCase = true) }) ||
                hit != null ||
                (code != null && code in rule.exitCodes)
            if (!matched) continue
            findings += Finding(
                level = rule.level,
                label = render(rule.label, hit).ifBlank { rule.id },
                detail = render(rule.detail, hit),
                evidence = render(rule.evidence, hit),
                suggestion = render(rule.suggest, hit)
            )
        }
        if (findings.isEmpty()) {
            findings += Finding(
                level = Level.Reminder,
                label = "Chưa xác định rõ nguyên nhân",
                detail = "Không tìm thấy mẫu lỗi quen thuộc nào khớp với nội dung báo cáo này.",
                evidence = first ?: "Không tìm thấy ngoại lệ (Exception) rõ ràng",
                suggestion = "Hãy chuyển sang tab「Báo cáo gốc」hoặc「Nhật ký đầy đủ」để xem ngoại lệ đầu tiên và chuỗi Caused by, hoặc kiểm tra phiên bản Java / Mod vừa cài."
            )
        }
        return Analysis(first ?: "Không tìm thấy ngoại lệ đầu tiên", first, causes, code, components, findings)
    }

    private val placeholder = Regex("""\$(\d+|java)""")
    private val optionalFragment = Regex("""\{\{([^{}]*)\}\}""")
    private val emptyParens = Regex("""[（(]\s*[)）]""")
    private val emptyBrackets = Regex("""[【\[]\s*[】\]]""")
    private val repeatedSpaces = Regex("""\s{2,}""")
    private val spaceBeforePunctuation = Regex("""\s+([，。、；：！？）】」])""")

    private fun render(template: String, match: MatchResult?): String {
        if (template.isEmpty()) return template
        val groups = match?.groupValues
        val valueOf = fun(token: String): String = when (token) {
            "java" -> {
                val code = groups?.getOrNull(1)?.toIntOrNull()
                val release = code?.let { classVersions[it] }
                release?.let { "Java $it" }.orEmpty()
            }
            else -> token.toIntOrNull()?.let { groups?.getOrNull(it) }.orEmpty()
        }
        val withoutOptionals = optionalFragment.replace(template) { m ->
            val body = m.groupValues[1]
            val tokens = placeholder.findAll(body).map { it.groupValues[1] }.toList()
            if (tokens.isNotEmpty() && tokens.all { valueOf(it).isEmpty() }) "" else body
        }
        val rendered = placeholder.replace(withoutOptionals) { m -> valueOf(m.groupValues[1]) }
        return rendered
            .replace(emptyParens, "")
            .replace(emptyBrackets, "")
            .replace(repeatedSpaces, " ")
            .replace(spaceBeforePunctuation, "$1")
            .trim()
    }

    private const val ASSET_PATH = "rules/server_errors.json"
    private const val MAX_CUSTOM_RULE_BYTES = 256L * 1024
}
