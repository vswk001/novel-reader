package com.moyue.reader.bookshelf

import com.moyue.reader.data.db.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 书架阅读进度百分比的回归测试。
 *
 * 旧实现按「(当前章 + 1) × 100 / 总章数」粗算：新书 curChapter=0，于是
 * 2 章的书没读过就显示 50%、1 章显示 100%。现改为按字符偏移精确计算。
 */
class BookshelfProgressTest {

    private fun book(
        curChapter: Int = 0,
        curCharOffset: Int = 0,
        totalChars: Int,
        chapterCount: Int = 1
    ) = BookEntity(
        title = "t", fileName = "f", sizeBytes = 1, charset = "UTF-8",
        totalChars = totalChars, chapterCount = chapterCount,
        addedAt = 0, lastReadAt = 0,
        curChapter = curChapter, curCharOffset = curCharOffset
    )

    @Test
    fun unreadBookShowsZeroPercent_evenWithFewChapters() {
        // 用户反馈的现象：刚导入、只切成 2 段的书显示 50%
        assertEquals(0, ReadingProgress.percent(book(totalChars = 1000, chapterCount = 2)))
        // 单章的书也不该显示 100%
        assertEquals(0, ReadingProgress.percent(book(totalChars = 1000, chapterCount = 1)))
    }

    @Test
    fun progressFollowsCharOffset() {
        assertEquals(25, ReadingProgress.percent(book(totalChars = 400, curCharOffset = 100)))
        assertEquals(50, ReadingProgress.percent(book(totalChars = 400, curCharOffset = 200)))
        assertEquals(99, ReadingProgress.percent(book(totalChars = 300, curCharOffset = 299)))
    }

    @Test
    fun finishedBookShowsFull() {
        assertEquals(
            100,
            ReadingProgress.percent(book(totalChars = 400, curCharOffset = 400, chapterCount = 10))
        )
    }

    @Test
    fun offsetIsClampedToBookLength() {
        // 异常数据：偏移超过总长或为负，不应得到 >100 或 <0 的值
        assertEquals(100, ReadingProgress.percent(book(totalChars = 400, curCharOffset = 9999)))
        assertEquals(0, ReadingProgress.percent(book(totalChars = 400, curCharOffset = -5)))
    }

    @Test
    fun chapterIndexAloneDoesNotAffectPercent() {
        // 进度只取决于字符偏移，与章节下标无关（短章/长章不影响比例）
        val a = book(totalChars = 1000, curChapter = 5, chapterCount = 10, curCharOffset = 300)
        val b = book(totalChars = 1000, curChapter = 1, chapterCount = 10, curCharOffset = 300)
        assertEquals(30, ReadingProgress.percent(a))
        assertEquals(30, ReadingProgress.percent(b))
    }

    @Test
    fun emptyBookIsZero() {
        assertEquals(0, ReadingProgress.percent(book(totalChars = 0)))
    }

    /** 向下取整（与实现一致）：199/400 = 49.75% → 49 */
    @Test
    fun truncatesRemainder() {
        assertEquals(49, ReadingProgress.percent(book(totalChars = 400, curCharOffset = 199)))
    }
}
