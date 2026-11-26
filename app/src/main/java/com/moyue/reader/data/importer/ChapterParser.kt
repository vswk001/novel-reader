package com.moyue.reader.data.importer

/**
 * TXT 章节目录识别：多正则"竞选"，按章节数量与平均章节长度选出最合理的划分；
 * 全部不合格时按固定字数分段兜底，保证任何书都能打开阅读。
 */
object ChapterParser {

    data class Chapter(val title: String, val charStart: Int, val charEnd: Int)

    /** 章节标题行的长度上限（超长行直接跳过正则，兼顾性能与误判） */
    private const val MAX_TITLE_LINE = 100

    /** 兜底分段大小（字符） */
    private const val FALLBACK_CHUNK = 100_000

    /** 两个相邻标题的最小间隔（字符），过近视为误判（正文行被当标题） */
    private const val MIN_HEADING_GAP = 200

    /** 章节号：半角/全角数字 + 中文数字 */
    private val CH_NUM = "[0-9０-９〇零一二两三四五六七八九十百千万亿]{1,14}"

    /** 标题行首/行尾允许的装饰：少量空白、常见装饰符、包裹符号（数字编号类用，防误判） */
    private val LEAD = "[ \\t　★☆◆◇●○■□▲△※·]{0,4}[【（\\[「《]?"
    private val TAIL = "[ \\t　★☆◆◇●○■□▲△※·】）\\]」》]?"

    /** 词式标题（第X章/卷/番外）行首允许大量空白——居中排版常见几十个全角空格 */
    private val LEAD_CENTER = "[ \\t　]{0,48}[★☆◆◇●○■□▲△※·]{0,4}[【（\\[「《]?"

    /**
     * Markdown 标题前缀（# / ## … 后跟空白），可带尾随 ###。
     * 仅作为【已有标题格式】的可选前缀，不单独成规则：
     * 正文里的话题标签行（如「#今日份开心#」）后面的文字不符合章节格式，不会被误判。
     */
    private val MD_PREFIX = "(?:#{1,6}[ \\t　]+)?"
    private val MD_SUFFIX = "[ \t　]*(?:#+[ \t　]*)?"

    private val PATTERNS: List<Regex> = listOf(
        // 第一章 xxx / 【第1章】觉醒 / ★第一章★ / 居中的　　第九章 xxx / 第１２章（全角数字）
        // 以及 Markdown 标题：# 第一章 xxx / ## 第２章 标题
        Regex("""^${LEAD_CENTER}${MD_PREFIX}第\s*$CH_NUM\s*[章节卷集部回篇幕][^\n]{0,80}$TAIL$MD_SUFFIX"""),
        // 序章 / 楔子 / 尾声 / 后记 / 番外 / 番外篇 / 番外3 / 番外·xxx / 正文（含居中、含 Markdown 前缀）
        Regex(
            """^${LEAD_CENTER}$MD_PREFIX(序章|序言|楔子|引子|尾声|终章|后记|完本感言|正文|""" +
                """番外篇?([0-9０-９〇一二三四五六七八九十]{0,4})([ 　:：·\-—.．][^\n]{0,25})?)$TAIL$MD_SUFFIX"""
        ),
        // Chapter 12 / CHAPTER I The Beginning（含居中、含 Markdown 前缀）
        Regex("""^[ \t　]{0,48}$MD_PREFIX[Cc][Hh][Aa][Pp][Tt][Ee][Rr]\s+\d{1,5}([ .:：][^\n]{0,50})?$MD_SUFFIX"""),
        // 卷一 / 卷三 风起（含居中、含 Markdown 前缀）
        Regex("""^${LEAD_CENTER}${MD_PREFIX}卷\s*$CH_NUM([ 　][^\n]{0,20})?$TAIL$MD_SUFFIX"""),
        // 12、标题 / 12. 标题 / 12．标题（纯数字编号行；以句末标点结尾的正文行不算标题）
        Regex("""^[ \t　]{0,8}\d{1,4}[、.．,，:：]\s*\S[^\n。！？…""]{0,40}[ \t　]*$"""),
        // 001 标题（数字编号 + 空格 + 标题，无标点分隔；同样排除句末标点）
        Regex("""^[ \t　]{0,8}[0-9０-９]{1,4}[ \t　]+\S[^\n。！？…""]{0,36}[ \t　]*$""")
    )

    /**
     * 解析章节列表，区间 [charStart, charEnd) 基于传入的规范化全文。
     * 优先使用全部模式的并集（楔子/序章/番外 与 第X章 混排常见），
     * 并集不合法时退回单一最优模式；首个标题前超过 50 字的前言归入「开头」，
     * 短前言直接并入第一章，保证任何字符都不会丢失。
     */
    /** [PATTERNS] 中「卷X」规则的下标：其匹配结果是分组标题，参与卷/章层级处理 */
    private const val VOLUME_PATTERN_INDEX = 3

    fun parse(text: String): List<Chapter> {
        val perPattern = PATTERNS.mapIndexed { idx, regex ->
            regex to findHeadings(text, regex, isVolume = idx == VOLUME_PATTERN_INDEX)
        }
        val union = perPattern
            .flatMap { it.second }
            .distinctBy { it.start }
            .sortedBy { it.start }
            .let { dropVolumeHeadingsFollowedByChapter(it) }
            .let { dropTooClose(it) }

        val valid = mutableListOf<List<Heading>>()
        if (candidate(union, text.length)) valid += union
        perPattern.forEach { (_, hs) ->
            val filtered = dropTooClose(hs)
            if (candidate(filtered, text.length)) valid += filtered
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

    /**
     * @property isVolume 是否为【卷/部/集】这类分组标题（其下通常紧跟章节标题）
     */
    private class Heading(val title: String, val start: Int, val isVolume: Boolean = false)

    /**
     * 「卷标题」到其下首个章节标题之间的最大间隔（字符）。
     * 卷标题后通常只有一段卷简介（一两百到几百字），远小于正文章节的体量
     * （候选合法性要求平均章节 ≥ 400 字，实际多在数千字）。
     * 超过此值说明卷标题下有正文，该卷应保留为独立章节。
     */
    private const val MAX_VOLUME_INTRO = 1_500

    /**
     * 「卷标题」后面紧跟「章节标题」是层级结构（中间只有卷简介）：
     * 卷标题不单独成章，卷简介并入【其后的那一章】的开头。
     *
     * 实现上不是简单删掉卷标题——那样卷简介会落到【上一章】的末尾
     * （章的区间 = 本标题 → 下一个标题）。这里把卷标题替换为
     * 「位置不变、采用下一章标题」的占位，使下一章从卷标题处开始。
     *
     * 必须在 [dropTooClose] 之前处理——否则间隔小于 [MIN_HEADING_GAP] 时，
     * 通用的「保留前者」规则会把真正的章标题丢掉，使其正文被并入卷标题。
     */
    private fun dropVolumeHeadingsFollowedByChapter(headings: List<Heading>): List<Heading> {
        if (headings.size < 2) return headings
        val out = ArrayList<Heading>(headings.size)
        var i = 0
        while (i < headings.size) {
            val h = headings[i]
            val next = headings.getOrNull(i + 1)
            if (h.isVolume && next != null && !next.isVolume &&
                next.start - h.start < MAX_VOLUME_INTRO
            ) {
                // 卷标题 → 并入下一章：下一章从卷标题处开始（保留其后的 ### 等原文由标题文字决定）
                out += Heading(next.title, h.start, isVolume = false)
                i += 2 // 卷标题与下一章标题都已处理
                continue
            }
            out += h
            i++
        }
        return out
    }

    /** 相邻间隔小于 [MIN_HEADING_GAP] 的标题视为误判，保留前者 */
    private fun dropTooClose(headings: List<Heading>): List<Heading> {
        if (headings.size < 2) return headings
        val out = mutableListOf<Heading>()
        for (h in headings) {
            if (out.isEmpty() || h.start - out.last().start >= MIN_HEADING_GAP) {
                out += h
            }
        }
        return out
    }

    /**
     * 标题的展示文字：去掉首尾空白与 Markdown 的 # / ### 标记。
     * 目录里显示「第一章 第三十天」而不是「# 第一章 第三十天」。
     */
    private fun cleanTitle(line: String): String =
        MD_TITLE_MARKS.replace(line.trim(), "").trim()

    private val MD_TITLE_MARKS = Regex("^(?:#{1,6}[ \\t　]+)|(?:[ \t　]*#+[ \t　]*)$")

    private fun findHeadings(text: String, pattern: Regex, isVolume: Boolean = false): List<Heading> {
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
                    out += Heading(cleanTitle(line), lineStart, isVolume)
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
        return avg in 400.0..300_000.0
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
