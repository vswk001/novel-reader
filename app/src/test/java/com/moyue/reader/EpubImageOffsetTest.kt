package com.moyue.reader

import com.moyue.reader.data.importer.EpubParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * EPUB 图片占位偏移回归测试。
 * v1.9.2 之前的 bug：偏移漏加章节标题长度，导致渲染端标记位置校验失败、图片全部不显示。
 */
class EpubImageOffsetTest {

    @Test
    fun `no markers yields empty`() {
        assertArrayEquals(intArrayOf(), EpubParser.imageCharOffsets(100, "第一章", "普通正文"))
    }

    @Test
    fun `offset includes title length and newline`() {
        // 标题 3 字符 + '\n' = 4；标记在 normalized 首位 → 100 + 4 + 0
        val offsets = EpubParser.imageCharOffsets(100, "第一章", "${EpubParser.MARKER}\n正文")
        assertArrayEquals(intArrayOf(104), offsets)
    }

    @Test
    fun `multiple markers in order`() {
        val text = "前${EpubParser.MARKER}中${EpubParser.MARKER}后${EpubParser.MARKER}"
        val offsets = EpubParser.imageCharOffsets(0, "T", text)
        // base = 0 + len("T") + 1 = 2；标记在 normalized 内 1/3/5 位
        assertArrayEquals(intArrayOf(3, 5, 7), offsets)
    }

    @Test
    fun `regression - title length must shift offsets`() {
        // bug 本质：同一标记位置，标题变长偏移必须等量增加
        val normalized = "x${EpubParser.MARKER}y"
        val a = EpubParser.imageCharOffsets(50, "", normalized).first()
        val b = EpubParser.imageCharOffsets(50, "第一章 觉醒", normalized).first()
        assertEquals("第一章 觉醒".length, b - a)
    }

    @Test
    fun `marker survives at boundaries`() {
        val offsets = EpubParser.imageCharOffsets(7, "末章", EpubParser.MARKER.toString())
        assertArrayEquals(intArrayOf(7 + 2 + 1), offsets)
    }
}
