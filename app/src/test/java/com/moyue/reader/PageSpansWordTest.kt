package com.moyue.reader

import com.moyue.reader.reader.PageSpans
import org.junit.Assert.assertEquals
import org.junit.Test

/** 长按选词扩展逻辑（中英文）回归测试 */
class PageSpansWordTest {

    @Test
    fun `english word selected`() {
        assertEquals(4..8, PageSpans.wordAt("abc hello world", 5))
    }

    @Test
    fun `cjk expands to punctuation boundaries`() {
        // 中文无词边界：扩展到句读（"少年推开柴门" 6 字整体选中）
        assertEquals(0..5, PageSpans.wordAt("少年推开柴门，风雪扑面。", 2))
    }

    @Test
    fun `press on punctuation spans adjacent sentences`() {
        // 现有行为：长按标点扩展覆盖标点前后两个短句（锁定行为防意外变更）
        assertEquals(0..4, PageSpans.wordAt("前句。后句", 2))
    }

    @Test
    fun `boundaries clamped`() {
        assertEquals(0..0, PageSpans.wordAt("字", 0))
        val text = "hello"
        assertEquals(0..4, PageSpans.wordAt(text, 0))
        assertEquals(0..4, PageSpans.wordAt(text, 4))
    }

    @Test
    fun `newline is a boundary`() {
        assertEquals(0..2, PageSpans.wordAt("第一行\n第二行", 1))
    }
}
