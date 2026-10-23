package com.mineserve.mobile.ui

/** Background-safe log translation and keyword classification. */
object TerminalLogProcessor {
    fun process(line: String, translate: Boolean): TerminalDisplayLine {
        val text = if (translate) TerminalLogTranslator.translate(line) else line
        return TerminalDisplayLine(text, classify(text))
    }

    private fun classify(line: String): TerminalLogTone = when {
        line.startsWith("$ ") || line.startsWith("> ") -> TerminalLogTone.Command
        line.contains("placeable_food", true) -> TerminalLogTone.Warning
        line.contains("错误：") || line.contains("[ERROR]", true) || line.contains("[FATAL]", true) ||
            line.contains("Exception", true) || line.contains("failed", true) -> TerminalLogTone.Error
        line.contains("警告：") || line.contains("[WARN]", true) || line.contains("warning", true) || line.contains("Cảnh báo", true) -> TerminalLogTone.Warning
        line.contains("启动完成") || line.contains("已启动") || line.contains("下载完成") ||
            line.contains("khởi động hoàn tất", true) || line.contains("Đã tải hoàn tất", true) -> TerminalLogTone.Success
        line.contains("[INFO]", true) || line.contains("信息：") -> TerminalLogTone.Info
        line.contains("[DEBUG]", true) || line.contains("调试：") -> TerminalLogTone.Debug
        line.contains("[bootstrap]", true) || line.contains("[download]", true) || line.contains("[Tải xuống]", true) -> TerminalLogTone.Download
        else -> TerminalLogTone.Default
    }
}
