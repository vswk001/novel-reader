package com.moyue.reader

import com.moyue.reader.data.importer.EpubParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun minimalEpub(): ByteArray = zip(
        "mimetype" to "application/epub+zip",
        "META-INF/container.xml" to """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent(),
        "OEBPS/content.opf" to """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="uid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>测试之书</dc:title>
                <dc:creator>某作者</dc:creator>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx">
                <itemref idref="c1"/>
                <itemref idref="c2"/>
              </spine>
            </package>
        """.trimIndent(),
        "OEBPS/toc.ncx" to """
            <?xml version="1.0"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="n1"><navLabel><text>第一章 起风</text></navLabel>
                  <content src="ch1.xhtml"/></navPoint>
                <navPoint id="n2"><navLabel><text>第二章 落雨</text></navLabel>
                  <content src="ch2.xhtml"/></navPoint>
              </navMap>
            </ncx>
        """.trimIndent(),
        "OEBPS/ch1.xhtml" to """
            <html><head><title>ch1</title></head><body>
            <h1>第一章 起风</h1><p>少年推开柴门，风雪扑面。</p>
            <p>他说：&ldquo;今日之事&nbsp;&amp;&nbsp;明日再说。&rdquo;</p><br/>
            </body></html>
        """.trimIndent(),
        "OEBPS/ch2.xhtml" to """
            <html><head><title>ch2</title></head><body>
            <p>第二章的正文内容，用来验证第二章节。</p>
            </body></html>
        """.trimIndent()
    )

    @Test
    fun `parses title author and ncx chapters`() {
        val book = EpubParser.parse(minimalEpub())
        assertEquals("测试之书", book.title)
        assertEquals("某作者", book.author)
        assertEquals(2, book.chapters.size)
        assertEquals("第一章 起风", book.chapters[0].title)
        assertEquals("第二章 落雨", book.chapters[1].title)
    }

    @Test
    fun `xhtml tags stripped and entities decoded`() {
        val book = EpubParser.parse(minimalEpub())
        val t = book.chapters[0].text
        assertTrue(t.contains("少年推开柴门，风雪扑面。"))
        assertTrue(t.contains("“今日之事 & 明日再说。”"))  // &ldquo; &amp; &nbsp; 已解码
        assertTrue(!t.contains("<p>"))
        assertTrue(!t.contains("&amp;"))
    }

    @Test
    fun `paragraphs are line separated`() {
        val book = EpubParser.parse(minimalEpub())
        val lines = book.chapters[0].text.lines().filter { it.isNotBlank() }
        assertEquals(3, lines.size) // h1 + 两段 p
    }

    @Test
    fun `missing container throws`() {
        val bad = zip("foo.txt" to "not an epub")
        try {
            EpubParser.parse(bad)
            fail("should throw")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("container.xml"))
        }
    }

    @Test
    fun `epub3 nav toc supported`() {
        val epub = zip(
            "META-INF/container.xml" to
                """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>""",
            "content.opf" to """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Nav书</dc:title></metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>
                    <item id="b" href="b.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="a"/><itemref idref="b"/></spine>
                </package>
            """.trimIndent(),
            "nav.xhtml" to """
                <html><body><nav epub:type="toc"><ol>
                <li><a href="a.xhtml">甲篇</a></li>
                <li><a href="b.xhtml">乙篇</a></li>
                </ol></nav></body></html>
            """.trimIndent(),
            "a.xhtml" to "<html><body><p>甲的内容。</p></body></html>",
            "b.xhtml" to "<html><body><p>乙的内容。</p></body></html>"
        )
        val book = EpubParser.parse(epub)
        assertEquals("Nav书", book.title)
        assertEquals(listOf("甲篇", "乙篇"), book.chapters.map { it.title })
    }

    @Test
    fun `numeric entities decoded`() {
        assertEquals("A中", EpubParser.decodeEntities("&#65;&#20013;"))
        assertEquals("B", EpubParser.decodeEntities("&#x42;"))
    }
}
