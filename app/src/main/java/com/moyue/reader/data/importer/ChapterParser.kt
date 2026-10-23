package com.moyue.reader.data.importer

/**
 * TXT 章节目录识别：多正则“竞选”，按章节数量与平均章节长度选出最合理的划分；
 * 全部不合格时按固定字数分段兜底，保证任何书都能打开阅读。
 */
object ChapterParser {

    data class Chapter(val title: String, val charStart: Int, val charEnd: Int)

    /** 章节标题行的长度上限（超长行直接跳过正则，兼顾性能与误判） */
    private const val MAX_TITLE_LINE = 80

    /** 兜底分段大小（字符） */
    private const val FALLBACK_CHUNK = 100_000

    private val CH_NUM = "[0-9〇零一二两三四五六七八九十百千万亿]+"

    private val PATTERNS: List<Regex> = listOf(
        // 第一章 xxx / 第 12 卷-xxx / 第三节：xxx
        Regex("""^[ \t　]{0,8}第\s*$CH_NUM\s*[章节卷集部回篇幕][^\n]{0,60}[ \t　]*$"""),
        // 序章 / 楔子 / 尾声 / 番外篇 / 后记
        Regex("""^[ \t　]{0,8}(序章|序言|楔子|引子|尾声|终章|后记|完本感言|番外篇?([ 　：:][^\n]{0,25})?)[ \t　]*$"""),
        // Chapter 12 / CHAPTER I
        Regex("""^[ \t　]{0,8}[Cc][Hh][Aa][Pp][Tt][Ee][Rr]\s+\d{1,5}([ .:：][^\n]{0,50})?[ \t　]*$"""),
        // 卷一 / 卷三 风起（行内容太短太长都不算）
        Regex("""^[ \t　]{0,8}卷\s*$CH_NUM([ 　][^\n]{0,20})?[ \t　]*$"""),
        // 12、标题 / 12. 标题 / 12．标题（纯数字编号行）
        Regex("""^[ \t　]{0,8}\d{1,4}[、.．,，:：]\s*\S[^\n]{0,40}[ \t　]*$""")
    )

    /**
     * 解析章节列表，区间 [charStart, charEnd) 基于传入的规范化全文。
     * 优先使用全部模式的并集（楔子/序章/番外 与 第X章 混排常见），
     * 并集不合法时退回单一最优模式；首个标题前超过 50 字的前言归入「开头」，
     * 短前言直接并入第一章，保证任何字符都不会丢失。
     */
    fun parse(text: String): List<Chapter> {
        val perPattern = PATTERNS.map { it to findHeadings(text, it) }
        val union = perPattern
            .flatMap { it.second }
            .distinctBy { it.start }
            .sortedBy { it.start }

        val valid = mutableListOf<List<Heading>>()
        if (candidate(union, text.length)) valid += union
        perPattern.forEach { (_, hs) ->
            if (candidate(hs, text.length)) valid += hs
        }
        val best = valid.maxByOrNull { it.size } ?: emptyList()

        if (best.isEmpty()) return fallback(text)

        val chapters = mutableListOf<Chapter>()
        var cover = 0 // 已覆盖的文本起点
        for (i in best.indices) {
            val h = best[i]
            if (i == 0 && h.start > 50) {
                chapters += Chapter("开头", 0, h.start)
            }
            if (h.start < cover) continue // 防御：重叠标题
            val end = if (i + 1 < best.size) best[i + 1].start else text.length
            chapters += Chapter(h.title, h.start, end)
            cover = end
        }
        if (chapters.isEmpty()) return fallback(text)
        if (chapters[0].charStart > 0) {
            chapters[0] = chapters[0].copy(charStart = 0)
        }
        return chapters
    }

    private class Heading(val title: String, val start: Int)

    private fun findHeadings(text: String, pattern: Regex): List<Heading> {
        val out = mutableListOf<Heading>()
        var lineStart = 0
        val n = text.length
        while (lineStart < n) {
            val nl = text.indexOf('\n', lineStart)
            val lineEnd = if (nl == -1) n else nl
            val len = lineEnd - lineStart
            if (len in 1..MAX_TITLE_LINE) {
                val line = text.substring(lineStart, lineEnd)
                if (pattern.matches(line)) {
                    out += Heading(line.trim(), lineStart)
                }
            }
            lineStart = lineEnd + 1
        }
        return out
    }

    /** 候选合法性：至少 3 章；平均章节长度在合理区间，避免把正文行误判成标题 */
    private fun candidate(headings: List<Heading>, totalLen: Int): Boolean {
        if (headings.size < 3) return false
        val avg = totalLen.toDouble() / headings.size
        return avg in 800.0..300_000.0
    }

    private fun fallback(text: String): List<Chapter> {
        if (text.isEmpty()) return listOf(Chapter("正文", 0, 0))
        if (text.length <= FALLBACK_CHUNK) return listOf(Chapter("正文", 0, text.length))
        val chapters = mutableListOf<Chapter>()
        var start = 0
        var idx = 1
        while (start < text.length) {
            val end = minOf(start + FALLBACK_CHUNK, text.length)
            chapters += Chapter("第 $idx 部分", start, end)
            start = end
            idx++
        }
        return chapters
    }
}
