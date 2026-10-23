package com.moyue.reader

import com.moyue.reader.data.importer.CharsetDetector
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class CharsetDetectorTest {

    @Test
    fun `detects utf8 chinese`() {
        val bytes = "第一章 风起云涌\n萧炎抬头望天，心中百感交集。".toByteArray(Charsets.UTF_8)
        assertEquals(CharsetDetector.UTF_8, CharsetDetector.detect(bytes))
    }

    @Test
    fun `detects gbk chinese`() {
        val bytes = "斗气化马，此马恐怖如斯。主角光环护体。".toByteArray(Charset.forName("GBK"))
        assertEquals(CharsetDetector.GB18030, CharsetDetector.detect(bytes))
    }

    @Test
    fun `detects gb18030 four byte`() {
        val bytes = "生僻字𠀀测试继续加油。".toByteArray(Charset.forName("GB18030"))
        assertEquals(CharsetDetector.GB18030, CharsetDetector.detect(bytes))
    }

    @Test
    fun `detects utf8 bom`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "正文开始".toByteArray(Charsets.UTF_8)
        assertEquals(CharsetDetector.UTF_8, CharsetDetector.detect(bytes))
        assertEquals(3, CharsetDetector.bomLength(bytes))
    }

    @Test
    fun `detects utf16le bom`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "中文".toByteArray(Charsets.UTF_16LE)
        assertEquals(CharsetDetector.UTF_16LE, CharsetDetector.detect(bytes))
    }

    @Test
    fun `detects utf16be bom`() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "中文".toByteArray(Charsets.UTF_16BE)
        assertEquals(CharsetDetector.UTF_16BE, CharsetDetector.detect(bytes))
    }

    @Test
    fun `ascii is utf8`() {
        assertEquals(CharsetDetector.UTF_8, CharsetDetector.detect("hello world 123".toByteArray()))
    }

    @Test
    fun `mixed gbk sample is not utf8`() {
        // GBK 中文 + ASCII 混合（典型老网文）
        val text = buildString {
            repeat(500) { i ->
                append("第").append(i).append("节 这是测试段落，内容为中文编码检测样例。\n")
            }
        }
        val bytes = text.toByteArray(Charset.forName("GBK"))
        assertEquals(CharsetDetector.GB18030, CharsetDetector.detect(bytes))
    }

    @Test
    fun `truncated multibyte at sample boundary tolerated`() {
        val full = "很长的中文内容".repeat(100000).toByteArray(Charsets.UTF_8)
        val cut = full.copyOf(12345) // 任意截断
        assertEquals(CharsetDetector.UTF_8, CharsetDetector.detect(cut))
    }
}
