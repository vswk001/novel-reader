package com.moyue.reader

import com.moyue.reader.data.importer.ChapterParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterParserTest {

    private fun body(seed: Int, chars: Int = 1200): String =
        ("这是第${seed}段正文内容，写一些文字用来撑起章节长度。" ).repeat((chars / 20).coerceAtLeast(1))

    @Test
    fun `parses standard chinese chapters`() {
        val text = buildString {
            append("楔子\n")
            append(body(0))
            for (i in 1..6) {
                append("\n第${chineseNum(i)}章 风云${i}\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        // 楔子 + 6 章
        assertEquals(7, chapters.size)
        assertEquals("楔子", chapters[0].title)
        assertEquals("第一章 风云1", chapters[1].title)
        // 区间连续且覆盖全文
        chapters.zipWithNext().forEach { (a, b) -> assertEquals(a.charEnd, b.charStart) }
        assertEquals(text.length, chapters.last().charEnd)
    }

    @Test
    fun `parses numeric chapter titles`() {
        val text = buildString {
            for (i in 1..5) {
                append("\n第${i}卷 试炼之地\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(5, chapters.size)
        assertTrue(chapters[0].title.contains("第1卷"))
    }

    @Test
    fun `parses english chapter titles`() {
        val text = buildString {
            for (i in 1..4) {
                append("\nChapter $i The Beginning\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
    }

    @Test
    fun `preamble becomes first chapter`() {
        val text = buildString {
            append("书名：测试\n作者：某人\n\n")
            append(body(99)) // 50 字以上的前言
            for (i in 1..4) {
                append("\n第${chineseNum(i)}章 起点\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(5, chapters.size)
        assertEquals("开头", chapters[0].title)
        assertEquals(0, chapters[0].charStart)
    }

    @Test
    fun `short preamble merges into first chapter`() {
        val text = buildString {
            append("书名：测试\n") // 短前言，不单独成章
            for (i in 1..4) {
                append("\n第${chineseNum(i)}章 起点\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertEquals(0, chapters[0].charStart)
    }

    @Test
    fun `no chapters falls back to whole text`() {
        val text = body(1, 3000)
        val chapters = ChapterParser.parse(text)
        assertEquals(1, chapters.size)
        assertEquals("正文", chapters[0].title)
        assertEquals(0, chapters[0].charStart)
        assertEquals(text.length, chapters[0].charEnd)
    }

    @Test
    fun `huge text without chapters splits into parts`() {
        val text = body(1, 250_000)
        val chapters = ChapterParser.parse(text)
        assertTrue(chapters.size >= 2)
        assertEquals("第 1 部分", chapters[0].title)
        chapters.zipWithNext().forEach { (a, b) -> assertEquals(a.charEnd, b.charStart) }
    }

    @Test
    fun `rejects false positive body lines`() {
        // 正文里大量短行（例如对话），不应被识别为章节
        val text = buildString {
            append("第1章 对话\n")
            for (i in 1..3000) {
                append("“今天天气不错。”他说了这样一句话。\n")
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(1, chapters.size) // 单章无法成立（<3），走兜底
        assertEquals("正文", chapters[0].title)
    }

    private fun chineseNum(i: Int): String = when (i) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"
        6 -> "六"; 7 -> "七"; 8 -> "八"; 9 -> "九"; 10 -> "十"
        else -> i.toString()
    }
}
