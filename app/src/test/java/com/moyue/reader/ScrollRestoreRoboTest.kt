package com.moyue.reader

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.moyue.reader.data.importer.ChapterParser
import com.moyue.reader.data.importer.TextNormalizer
import com.moyue.reader.data.repo.ReadMode
import com.moyue.reader.di.Graph
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.content.Context

/**
 * 滚动模式「重开回到章节顶部」复现测试（Robolectric 真实应用全链路）。
 * 判定逻辑：
 *  - 第一次退出后 DB 偏移 A —— 保存侧是否正确（A 应深入章节）
 *  - 重开且不滚动、再次退出后 DB 偏移 C —— 恢复侧是否正确（C 应接近 A；若 ≈0 则重开落在顶部）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScrollRestoreRoboTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private var bookId = 0L
    private val title = "滚动定位测试书"

    @Before
    fun seedBook() {
        runBlocking {
        // 多章节书：5 章；初始进度设在第 3 章（下标 2）中途——覆盖"非首章 + 带上一章头部项"的真实场景
        val sb = StringBuilder()
        val cn = listOf("一", "二", "三", "四", "五")
        for (c in 1..5) {
            sb.append("第").append(cn[c - 1]).append("章 第").append(c).append("号章节\n")
            repeat(130) { i ->
                sb.append("C").append(c).append("P").append(i).append(" ")
                    .append("这是用于填充分页的测试正文，重复多次以形成足够的页数，内容本身无意义。")
                    .append("XYZQ").append(i).append("。\n")
            }
        }
        val text = TextNormalizer.normalize(sb.toString())
        val chapters = ChapterParser.parse(text)
        bookId = Graph.bookRepository.createBook(
            title, "scroll-test.txt", text.toByteArray(Charsets.UTF_8).size.toLong(),
            "UTF-8", text.length, chapters.size
        )
        Graph.bookRepository.writeBookFile(bookId, text)
        Graph.bookRepository.saveChapters(bookId, chapters, text)
        // 模拟"读到第 3 章中部"
        Graph.bookRepository.saveProgress(bookId, 2, chapters[2].charStart + 2000)
        Graph.settingsRepository.update { it.copy(readMode = ReadMode.SCROLL) }
        Graph.settingsRepository.prefs // 触发 DataStore 初始化
        }
    }

    private fun savedOffset(): Int = runBlocking {
        Graph.bookRepository.getBook(bookId)!!.curCharOffset
    }

    @Test
    fun scrollExitReopen_positionPreserved() {
        // 打开书
        rule.onAllNodesWithText(title)[0].performClick()
        settleReader(rule)

        // 等滚动容器出现（Robolectric 首跑布局较慢）
        val hasScroll = androidx.compose.ui.test.hasScrollAction()
        rule.waitUntil(20_000) {
            rule.onAllNodes(hasScroll).fetchSemanticsNodes().isNotEmpty()
        }
        // 语义滚动到第 60 页（可靠、与手势时序无关）
        rule.onNode(hasScroll)
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("sp-15"))
        // 再滚入页面中部：制造"页已滚出一半"的场景（旧实现会回退到上一页开头）
        rule.onNode(hasScroll).performTouchInput {
            val fromY = height / 2f
            val topCenter = androidx.compose.ui.geometry.Offset(width / 2f, fromY)
            val endPt = androidx.compose.ui.geometry.Offset(width / 2f, fromY - 450f)
            swipe(start = topCenter, end = endPt, durationMillis = 300)
        }
        rule.runOnIdle {}
        Thread.sleep(600)
        rule.runOnIdle {}

        // 退出（触发 ON_PAUSE 保存）
        Espresso.pressBack()
        Thread.sleep(1200)
        val offA = savedOffset()
        val chList = runBlocking { Graph.bookRepository.listChapters(bookId) }
        chList.forEach { println("SCROLLTEST ch[${it.idx}] ${it.charStart}..${it.charEnd} ${it.title}") }
        println("SCROLLTEST save-offset A = $offA")

        // 重开（不滚动）
        rule.onAllNodesWithText(title)[0].performClick()
        settleReader(rule)

        // 直接退出：保存的即"重开后实际停留的页"
        Espresso.pressBack()
        Thread.sleep(1200)
        val offC = savedOffset()
        println("SCROLLTEST reopen-position C = $offC (chapterStart≈0)")

        assertTrue(
            "保存侧异常：退出后偏移 $offA 未深入第 3 章",
            offA > runBlocking { Graph.bookRepository.listChapters(bookId)[2].charStart } + 2000
        )
        assertTrue(
            "恢复侧行级精度异常：重开后停在 $offC，保存值为 $offA（差 ${offA - offC} 超过 1 行）",
            offC > offA - 120
        )
        val b = runBlocking { Graph.bookRepository.getBook(bookId)!! }
        assertTrue("章节丢失：当前第 ${b.curChapter} 章", b.curChapter == 2)
    }

    private fun settleReader(@Suppress("UNUSED_PARAMETER") rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, MainActivity>) {
        // 等章节加载 + 首帧布局 + 恢复滚动完成（IO 线程真实调度，主线程空闲驱动）
        rule.runOnIdle {}
        Thread.sleep(2000)
        rule.runOnIdle {}
        Thread.sleep(500)
        rule.runOnIdle {}
    }
}
