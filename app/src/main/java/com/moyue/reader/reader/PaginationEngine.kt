package com.moyue.reader.reader

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * 基于 StaticLayout 的精准分页引擎：整章一次测量，逐行累加行高得出每页末行，
 * 页边界即字符偏移。分页与渲染共用同一套 Paint/Layout 参数，保证所见即所排。
 */
class PaginationEngine(
    val paint: TextPaint,
    val pageWidth: Int,
    val pageHeight: Int,
    val lineSpacingMult: Float
) {
    /** 章节文本的字符区间 [start, end) */
    data class Page(val start: Int, val end: Int)

    fun paginate(text: String): List<Page> {
        if (text.isEmpty()) return listOf(Page(0, 0))
        val layout = buildLayout(text)
        val pages = mutableListOf<Page>()
        var line = 0
        val lineCount = layout.lineCount
        while (line < lineCount) {
            val first = line
            val top = layout.getLineTop(first)
            var last = first
            while (last + 1 < lineCount &&
                layout.getLineBottom(last + 1) - top <= pageHeight
            ) {
                last++
            }
            val start = layout.getLineStart(first)
            var end = layout.getLineVisibleEnd(last)
            if (end <= start) {
                // 单行高于一页或空行：至少推进一行，避免死循环
                end = if (last + 1 < lineCount) maxOf(layout.getLineEnd(last), start + 1)
                else text.length
                if (end <= start) end = minOf(start + 1, text.length)
            }
            pages += Page(start, end)
            line = last + 1
        }
        if (pages.isEmpty()) pages += Page(0, text.length)
        return pages
    }

    fun buildLayout(text: String): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, pageWidth.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacingMult)
            .setIncludePad(false)
            .build()

    companion object {
        /** 二分查找字符偏移所在页（页区间连续覆盖 [0, length)） */
        fun indexOfOffset(pages: List<Page>, offset: Int): Int {
            if (pages.isEmpty()) return 0
            var lo = 0
            var hi = pages.size - 1
            var res = pages.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val p = pages[mid]
                when {
                    offset < p.start -> hi = mid - 1
                    offset >= p.end -> lo = mid + 1
                    else -> {
                        res = mid
                        break
                    }
                }
            }
            return res.coerceIn(0, pages.size - 1)
        }
    }
}
