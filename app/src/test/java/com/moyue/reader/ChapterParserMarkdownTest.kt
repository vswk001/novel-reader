package com.moyue.reader

import com.moyue.reader.data.importer.ChapterParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 书稿的章节切分。
 *
 * 来源：用户反馈一本 .md 合稿「没有正确切分目录」——标题是「# 第一章 xxx」这种
 * Markdown 一级标题，原有规则只认纯文本的「第一章 xxx」，一条都没匹配上，
 * 于是走兜底每 10 万字硬切，全书只被切成 2 段。
 *
 * 夹具按该书真实结构构造：书名 + 若干「# 卷X 卷名」(后跟一段卷简介) + 每卷下若干「# 第N章 标题」。
 */
class ChapterParserMarkdownTest {

    private fun body(seed: Int, chars: Int = 1500): String =
        ("这是第${seed}段正文内容，写一些文字用来撑起章节长度。").repeat((chars / 20).coerceAtLeast(1))

    private val numerals = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    private fun cn(n: Int): String = when {
        n <= 10 -> numerals[n - 1]
        n < 20 -> "十${numerals[n - 11]}"
        else -> "${numerals[n / 10 - 1]}十${if (n % 10 == 0) "" else numerals[n % 10 - 1]}"
    }

    /** 一段约 [chars] 字的卷简介 */
    private fun volumeIntro(chars: Int): String =
        "卷首语，交代本卷背景。".repeat((chars / 12).coerceAtLeast(1))

    /** 一个「# 第N章 标题N」+ 正文 */
    private fun chapterBlock(n: Int, heading: String = "#"): String =
        "$heading 第${cn(n)}章 章节标题${n}\n\n${body(n)}\n\n"

    /**
     * @param heading   标题前缀（# / ## / 带缩进的 #）
     * @param introChars 每卷标题后的卷简介长度
     * @param firstVolumeIntro 第一卷是否带卷简介。带的话，书名 + 卷简介位于首个章标题之前，
     *                         按既有规则（> 50 字）会成为「开头」章——真实书稿即如此。
     */
    private fun markdownBook(
        volumes: Int,
        chaptersPerVolume: Int,
        heading: String = "#",
        introChars: Int = 250,
        firstVolumeIntro: Boolean = false
    ): String = buildString {
        append("# 执炬者\n\n")
        var n = 1
        repeat(volumes) { v ->
            append("$heading 卷${numerals[v]} 第${v + 1}卷名\n\n")
            if (v > 0 || firstVolumeIntro) append(volumeIntro(introChars))
            append("\n")
            repeat(chaptersPerVolume) {
                append(chapterBlock(n, heading))
                n++
            }
        }
    }

    private fun len(c: ChapterParser.Chapter) = c.charEnd - c.charStart

    // ---------------- 识别 ----------------

    @Test
    fun `markdown headings are recognized and not hard split`() {
        val chapters = ChapterParser.parse(markdownBook(volumes = 2, chaptersPerVolume = 4))
        // 2 卷 × 4 章 = 8 章；中间卷的标题后紧跟章标题，卷简介并入该卷首章
        assertEquals(8, chapters.size)
        assertFalse(
            "不应走兜底硬切",
            chapters.all { it.title.startsWith("第 ") && it.title.endsWith(" 部分") }
        )
    }

    @Test
    fun `markdown hash prefix is stripped from titles`() {
        val chapters = ChapterParser.parse(markdownBook(volumes = 1, chaptersPerVolume = 4))
        assertEquals(4, chapters.size)
        assertTrue(chapters.all { !it.title.contains("#") })
        assertEquals(
            listOf("第一章 章节标题1", "第二章 章节标题2", "第三章 章节标题3", "第四章 章节标题4"),
            chapters.map { it.title }
        )
    }

    @Test
    fun `level two markdown headings are recognized too`() {
        val chapters = ChapterParser.parse(markdownBook(volumes = 2, chaptersPerVolume = 4, heading = "##"))
        assertEquals(8, chapters.size)
        assertTrue(chapters.all { !it.title.contains("#") })
    }

    @Test
    fun `indented markdown heading is recognized`() {
        val chapters = ChapterParser.parse(markdownBook(volumes = 2, chaptersPerVolume = 4, heading = "　　#"))
        assertEquals(8, chapters.size)
        assertTrue(chapters.all { !it.title.contains("　") })
    }

    @Test
    fun `closing hash marks are stripped`() {
        val text = buildString {
            append("# 卷一 卷名\n\n")
            append(volumeIntro(60))
            append("\n")
            for (i in 1..4) append(chapterBlock(i).replace("标题$i\n", "标题$i ###\n"))
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertTrue(chapters.all { !it.title.contains("#") })
    }

    @Test
    fun `markdown prologue style titles are recognized`() {
        // 序章 / 楔子 / 番外 等 Markdown 标题
        val text = buildString {
            append("# 楔子\n\n${body(0)}\n\n")
            append("# 第一章 起点\n\n${body(1)}\n\n")
            append("# 番外 小剧场\n\n${body(2)}\n\n")
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(listOf("楔子", "第一章 起点", "番外 小剧场"), chapters.map { it.title })
    }

    // ---------------- 卷 / 章 层级 ----------------

    @Test
    fun `volume heading followed by chapter is merged not kept as tiny chapter`() {
        val chapters = ChapterParser.parse(markdownBook(volumes = 3, chaptersPerVolume = 3))
        // 没有「卷」碎章，也没有丢章
        assertEquals(9, chapters.size)
        assertTrue(chapters.none { it.title.startsWith("卷") })
        assertTrue(chapters.all { len(it) > 1000 })
    }

    @Test
    fun `volume intro is merged into the first chapter of that volume`() {
        // 卷二的卷简介并入 第四章（卷二首章）的开头，而不是落到上一章（第三章）的末尾
        val text = markdownBook(volumes = 2, chaptersPerVolume = 3)
        val chapters = ChapterParser.parse(text)
        val firstOfVol2 = chapters.first { it.title.startsWith("第四章") }
        val lastOfVol1 = chapters.first { it.title.startsWith("第三章") }

        // 第四章从「# 卷二 …」标题行处开始：其正文以卷标题与卷简介打头
        val head = text.substring(firstOfVol2.charStart, firstOfVol2.charStart + 40)
        assertTrue("第四章应从卷标题处开始: 「$head」", head.startsWith("# 卷二 第2卷名"))
        assertTrue("第四章开头应包含卷简介: 「$head」", head.contains("卷首语"))

        // 第三章（上一卷末章）到第四章之间没有任何遗漏：首尾相接
        assertEquals(firstOfVol2.charStart, lastOfVol1.charEnd)
        // 第三章的结尾是它自己的正文，而不是卷二的简介
        val tail = text.substring(lastOfVol1.charEnd - 30, lastOfVol1.charEnd)
        assertTrue("第三章末尾不应含卷二简介: 「$tail」", !tail.contains("卷首语"))
    }

    @Test
    fun `chapter right after a volume heading is not dropped`() {
        // 回归：卷标题与下一章间隔小于 200 字时，通用「保留前者」规则曾把真正的章标题丢掉，
        // 导致该章正文被并入卷标题（用户书稿里的「第二十章」就是这样消失的）
        val chapters = ChapterParser.parse(
            markdownBook(volumes = 3, chaptersPerVolume = 3, introChars = 60)
        )
        assertEquals(
            listOf(
                "第一章 章节标题1", "第二章 章节标题2", "第三章 章节标题3",
                "第四章 章节标题4", "第五章 章节标题5", "第六章 章节标题6",
                "第七章 章节标题7", "第八章 章节标题8", "第九章 章节标题9"
            ),
            chapters.map { it.title }
        )
    }

    @Test
    fun `volume with long body under it stays as its own chapter`() {
        // 卷标题下有正文（远超卷简介体量）时，它本身就是一个章，不应被并入下一章
        val text = buildString {
            append("# 卷一 起源\n\n${body(0, 6000)}\n\n")
            for (i in 1..3) append(chapterBlock(i))
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertEquals("卷一 起源", chapters.first().title)
    }

    @Test
    fun `book title and first volume intro are merged into the first chapter`() {
        // 真实书稿的结构：书名 + 第一卷简介 位于首个章标题之前。
        // 卷标题并入其后的章 → 第一章从「卷一」标题处开始，书名与卷简介都归入第一章开头，
        // 目录里没有「开头」这种碎章，内容一个字不丢。
        val chapters = ChapterParser.parse(
            markdownBook(volumes = 5, chaptersPerVolume = 6, firstVolumeIntro = true)
        )
        assertEquals(30, chapters.size)
        assertEquals("第一章 章节标题1", chapters.first().title)
        // 第一章 = 书名 + 卷一简介 + 正文，明显长于普通章
        assertTrue("首章应包含书名与卷简介: ${len(chapters.first())}", len(chapters.first()) > len(chapters[1]))
        // 没有任何「卷」碎章，也没有「开头」碎章
        assertTrue(chapters.none { it.title.startsWith("卷") || it.title == "开头" })
    }

    @Test
    fun `long preamble before any heading becomes an opening chapter`() {
        // 既有规则不回归：首个标题前有超过 50 字的正文（而非紧随卷标题的卷简介）→ 归入「开头」
        val text = buildString {
            append("这是一段很长的前言，交代故事背景。".repeat(10))
            append("\n\n")
            for (i in 1..3) append(chapterBlock(i))
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertEquals("开头", chapters.first().title)
    }

    // ---------------- 反例：# 不能让正文行变成标题 ----------------

    @Test
    fun `hashtag lines in body text are not headings`() {
        // 微博体正文里的话题标签：以 # 开头但后面不是章节格式
        val text = buildString {
            for (i in 1..4) {
                append("# 第${cn(i)}章 标题${i}\n\n")
                append(body(i))
                append("#今日份开心# 今天也顺利完成了任务。\n")       // 话题标签，# 后无空格
                append("# 突然想聊聊这件事，其实也没有什么特别的。\n")    // # 开头但不是章节名
                append(body(i))
                append("\n\n")
            }
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertTrue(chapters.none { it.title.contains("今日份") || it.title.contains("突然想聊") })
    }

    @Test
    fun `bare hash followed by non chapter text is not a heading`() {
        val text = buildString {
            append(chapterBlock(1)); append(chapterBlock(2)); append(chapterBlock(3))
            append("# 突然的感慨 ###\n\n${body(4)}\n\n") // 不是合法的章节标题
            append(chapterBlock(4))
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertTrue(chapters.none { it.title.contains("突然的感慨") })
    }

    @Test
    fun `plain text titles still work without markdown`() {
        // 既有行为不回归：不带 # 的纯文本标题
        val text = buildString {
            for (i in 1..4) append(chapterBlock(i, heading = ""))
        }
        val chapters = ChapterParser.parse(text)
        assertEquals(4, chapters.size)
        assertEquals("第一章 章节标题1", chapters.first().title)
    }
}
