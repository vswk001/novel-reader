package com.moyue.reader.bookshelf

import com.moyue.reader.data.db.BookEntity

/**
 * 书架上的阅读进度百分比。
 *
 * 按【字符偏移】精确计算——与阅读器恢复位置用的是同一个量（curCharOffset / totalChars）。
 * 不能按「第几章」粗算：新书 curChapter=0，若用 (curChapter+1)/章数，章数少的书没读过
 * 也会显示很高的进度（2 章即 50%，1 章即 100%）。
 */
object ReadingProgress {

    /** 0..100；未读为 0，读完为 100。偏移越界时被钳制到全书范围内。 */
    fun percent(book: BookEntity): Int {
        if (book.totalChars <= 0) return 0
        val cur = book.curCharOffset.coerceIn(0, book.totalChars)
        return (cur * 100L / book.totalChars).toInt()
    }
}
