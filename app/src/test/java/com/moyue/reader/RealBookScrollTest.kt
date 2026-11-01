package com.moyue.reader

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.moyue.reader.data.importer.ChapterParser
import com.moyue.reader.data.importer.CharsetDetector
import com.moyue.reader.data.importer.TextNormalizer
import com.moyue.reader.data.repo.PosLog
import com.moyue.reader.data.repo.ReadMode
import com.moyue.reader.di.Graph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 真书复现：本地长篇书稿（路径取环境变量 MOYUE_TEST_BOOK，默认 D:/workspace/book.txt）。
 * 多轮 开→滚→退→开→退 循环捕捉偶发偏移；结束时输出 PosLog 全决策链。
 * 书稿不在本机（CI）时自动跳过。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RealBookScrollTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val bookFile = File(System.getenv("MOYUE_TEST_BOOK") ?: "D:/workspace/book.txt")
    private val title = bookFile.nameWithoutExtension.take(12).ifBlank { "测试真书" }
    private var bookId = 0L

    @After
    fun tearDownDb() {
        Graph.resetForTest()
    }

    @Before
    fun seedRealBook() {
        assumeTrue("真实书稿不存在，跳过", bookFile.exists())
        runBlocking {
            PosLog.clear()
            val bytes = bookFile.readBytes()
            val cs = CharsetDetector.detect(bytes)
            val bom = CharsetDetector.bomLength(bytes)
            val raw = String(bytes, bom, bytes.size - bom, java.nio.charset.Charset.forName(cs))
            val text = TextNormalizer.normalize(raw)
            val chapters = ChapterParser.parse(text)
            println("REALBOOK charset=$cs chars=${text.length} chapters=${chapters.size}")
            bookId = Graph.bookRepository.createBook(
                title, bookFile.name, bytes.size.toLong(), cs, text.length, chapters.size
            )
            Graph.bookRepository.writeBookFile(bookId, text)
            Graph.bookRepository.saveChapters(bookId, chapters, text)
            // 从全书中部某章中段开始（真实阅读位置）
            val mid = chapters[chapters.size / 2]
            Graph.bookRepository.saveProgress(bookId, chapters.size / 2, mid.charStart + 3000)
            Graph.settingsRepository.update { it.copy(readMode = ReadMode.SCROLL) }
            Graph.settingsRepository.prefs
        }
    }

    private fun savedOffset(): Int = runBlocking {
        Graph.bookRepository.getBook(bookId)!!.curCharOffset
    }

    private fun settleReader() {
        rule.runOnIdle {}
        Thread.sleep(2000)
        rule.runOnIdle {}
        Thread.sleep(500)
        rule.runOnIdle {}
    }

    @Test
    fun realBook_multiCycle_driftRepro() {
        val hasScroll = androidx.compose.ui.test.hasScrollAction()
        val tag15 = androidx.compose.ui.test.hasTestTag("sp-15")

        // 第一次会话：滚到第 15 页并滚入页中
        rule.onAllNodesWithText(title)[0].performClick()
        settleReader()
        rule.waitUntil(20_000) { rule.onAllNodes(hasScroll).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasScroll).performScrollToNode(tag15)
        rule.onNode(hasScroll).performTouchInput {
            val fromY = height / 2f
            swipe(
                start = androidx.compose.ui.geometry.Offset(width / 2f, fromY),
                end = androidx.compose.ui.geometry.Offset(width / 2f, fromY - 450f),
                durationMillis = 300
            )
        }
        rule.runOnIdle {}
        Thread.sleep(600)
        rule.runOnIdle {}
        Espresso.pressBack()
        Thread.sleep(1200)
        val a = savedOffset()
        println("REALBOOK A = $a")

        // 3 轮 重开→(不滚)→退：任何一轮位置变化都记为漂移
        val cs = mutableListOf(a)
        repeat(3) { cycle ->
            rule.onAllNodesWithText(title)[0].performClick()
            settleReader()
            Espresso.pressBack()
            Thread.sleep(1200)
            val c = savedOffset()
            cs += c
            println("REALBOOK cycle$cycle C = $c (Δ=${c - a})")
        }

        println("REALBOOK POSLOG >>>\n" + PosLog.dump().takeLast(6000) + "\n<<< POSLOG")

        cs.forEachIndexed { i, c ->
            assertTrue(
                "第 $i 轮重开后漂移：A=${cs.first()} C=$c Δ=${c - cs.first()}",
                kotlin.math.abs(c - cs.first()) <= 120
            )
        }
    }
}
