package com.moyue.reader

import com.moyue.reader.data.importer.ChapterParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.4.0 章节切分增强：括号包裹 / 番外编号 / 相邻误判过滤 */
class ChapterParserEnhancedTest {

    private fun body(seed: Int, chars: Int = 1200): String =
        ("这是第${seed}段正文内容，写一些文字用来撑起章节长度。").repeat((chars / 20).coerceAtLeast(1))

    @Test
    fun `bracketed chapter titles are recognized`() {
        val text = buildString {
            for (i in 1..4) {
                append("\n【第${i}章】觉醒之路\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertTrue(chapters[0].title.contains("第1章"))
    }

    @Test
    fun `full width paren wrapped titles are recognized`() {
        val text = buildString {
            for (i in 1..4) {
                append("\n（第${i}节）风起\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
    }

    @Test
    fun `numbered fanwai is recognized`() {
        val text = buildString {
            for (i in 1..3) {
                append("\n第${i}章 主线\n")
                append(body(i))
            }
            append("\n番外1 婚后日常\n")
            append(body(9))
            append("\n番外二 番外篇：旅行\n")
            append(body(10))
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(5, chapters.size)
        assertTrue(chapters[3].title.startsWith("番外1"))
        assertTrue(chapters[4].title.startsWith("番外二"))
    }

    @Test
    fun `adjacent false positive headings are dropped`() {
        // 第2章之后 100 字就出现一个「卷一」行——过近，判为正文误判
        val text = buildString {
            append("\n第1章 起\n")
            append(body(1))
            append("\n第2章 承\n")
            append(body(2, 100)) // 只有 100 字
            append("\n卷一\n")
            append(body(3))
            append("\n第3章 合\n")
            append(body(4))
        }
        val chapters = ChapterParser.parse(text)
        // 「卷一」与「第2章」间隔过近被过滤；超短的第2章保留（标题本身合法）
        assertEquals(3, chapters.size)
        assertTrue(chapters.none { it.title == "卷一" })
    }

    @Test
    fun `body lines with sentence punctuation are not number titles`() {
        val text = buildString {
            for (i in 1..4) {
                append("\n第${i}章 叙事\n")
                append("1、这是正文第一点。2、这是正文第二点。\n".repeat(60))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
    }

    // ---------- v1.6.1 放宽规则覆盖 ----------

    @Test
    fun `decorated headings with stars are recognized`() {
        val text = buildString {
            for (i in 1..4) {
                append("\n★第${i}章★ 风云再起\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertTrue(chapters[0].title.contains("第1章"))
    }

    @Test
    fun `full width digit chapters are recognized`() {
        val text = buildString {
            for (i in 1..4) {
                append("\n第１${i}章 全角标题\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
    }

    @Test
    fun `number space title format is recognized`() {
        val text = buildString {
            for (i in 1..5) {
                append("\n00${i} 大梦初醒\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(5, chapters.size)
        assertTrue(chapters[0].title.contains("大梦初醒"))
    }

    @Test
    fun `leading whitespace before chapter word is allowed`() {
        val text = buildString {
            for (i in 1..4) {
                append("\n    第${i}章 缩进标题\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
    }

    @Test
    fun `short chapters below 800 avg are still accepted`() {
        val text = buildString {
            for (i in 1..4) {
                append("\n第${i}章 短章\n")
                append(body(i, 500)) // 平均约 500 字/章（旧阈值 800 会整本拒绝）
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
    }

    @Test
    fun `longer titles up to 80 chars are recognized`() {
        val longTitle = "这是一章特别特别长的标题" + "字".repeat(30) // 约 40+ 字
        val text = buildString {
            for (i in 1..4) {
                append("\n第${i}章 $longTitle\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
    }

    // ---------- 用户实书回归：居中标题（大量行首全角空格） ----------

    @Test
    fun `centered chapter heading with full width spaces is recognized`() {
        // 复刻实书：标题用全角空格居中，如 "　　　　　　第九章 神州铁律"
        val text = buildString {
            append("\n")
            append("　　　　　　　　第一章 风起\n")
            append(body(1))
            append("\n　　　　　　　　第九章 神州铁律\n")
            append(body(9))
            append("\n　　　　　　　　第十章 出发\n")
            append(body(10))
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(3, chapters.size)
        assertEquals("第九章 神州铁律", chapters[1].title)
    }

    @Test
    fun `centered heading with regular ascii spaces is recognized`() {
        val text = buildString {
            for (i in 1..4) {
                append("\n                    第${i}章 半角空格居中\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
    }

    @Test
    fun `centered xuyan heading is recognized`() {
        val text = buildString {
            append("　　　　　　序章\n")
            append(body(0))
            for (i in 1..3) {
                append("\n　　　　　　第${chineseNum(i)}章 继续\n")
                append(body(i))
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertEquals("序章", chapters[0].title)
    }

    private fun chineseNum(i: Int): String = when (i) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"
        else -> i.toString()
    }
}
