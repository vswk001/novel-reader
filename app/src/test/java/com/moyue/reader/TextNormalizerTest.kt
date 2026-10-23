package com.moyue.reader

import com.moyue.reader.data.importer.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `unifies crlf`() {
        val out = TextNormalizer.normalize("第一行\r\n第二行\r\n")
        assertEquals("第一行\n第二行\n", out)
    }

    @Test
    fun `unifies lone cr`() {
        val out = TextNormalizer.normalize("第一行\r第二行\r")
        assertEquals("第一行\n第二行\n", out)
    }

    @Test
    fun `trims trailing whitespace`() {
        val out = TextNormalizer.normalize("你好世界。   \n下一行\t\n")
        assertEquals("你好世界。\n下一行\n", out)
    }

    @Test
    fun `collapses multiple blank lines`() {
        val out = TextNormalizer.normalize("段落一\n\n\n\n\n段落二\n")
        assertEquals("段落一\n\n段落二\n", out)
    }

    @Test
    fun `removes leading blank lines`() {
        val out = TextNormalizer.normalize("\n\n\n开头\n")
        assertEquals("开头\n", out)
    }

    @Test
    fun `removes bom`() {
        val out = TextNormalizer.normalize("\uFEFF书名\n正文")
        assertFalse(out.startsWith("\uFEFF"))
        assertTrue(out.startsWith("书名"))
    }

    @Test
    fun `ends with single newline`() {
        val out = TextNormalizer.normalize("内容\n\n\n")
        assertEquals("内容\n", out)
        assertEquals(1, out.length - out.trimEnd('\n').length)
    }

    @Test
    fun `preserves paragraph blank separator`() {
        val out = TextNormalizer.normalize("段A\n段A续\n\n段B\n")
        assertEquals("段A\n段A续\n\n段B\n", out)
    }
}
