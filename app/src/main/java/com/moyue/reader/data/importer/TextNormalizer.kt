package com.moyue.reader.data.importer

/**
 * 文本规范化：统一换行、去行尾空白、压缩 2+ 连续空行为 1 行、去 BOM 字符。
 * 保留原有的手工换行与段首缩进，保证阅读观感与原文一致。
 */
object TextNormalizer {

    fun normalize(raw: String): String {
        val src = raw.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n')
        val sb = StringBuilder(src.length + 16)
        var blankRun = 0
        var started = false
        var lineStart = 0
        val n = src.length
        var i = 0
        while (i <= n) {
            if (i == n || src[i] == '\n') {
                val line = src.substring(lineStart, if (i == n) n else i).trimEnd()
                if (line.isEmpty()) {
                    if (started && blankRun == 0) sb.append('\n')
                    blankRun++
                } else {
                    started = true
                    blankRun = 0
                    sb.append(line).append('\n')
                }
                lineStart = i + 1
            }
            i++
        }
        return sb.toString().trimEnd('\n') + "\n"
    }
}
