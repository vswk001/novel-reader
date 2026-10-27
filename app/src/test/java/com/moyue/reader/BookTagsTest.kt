package com.moyue.reader

import com.moyue.reader.data.repo.BookTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 书籍分组标签序列化（DB v5 tags 列 + 备份） */
class BookTagsTest {

    @Test
    fun `roundtrip`() {
        val tags = listOf("玄幻", "正在追", "Sci-Fi")
        assertEquals(tags, BookTags.parse(BookTags.serialize(tags)))
    }

    @Test
    fun `empty and invalid fall back`() {
        assertEquals(emptyList<String>(), BookTags.parse("[]"))
        assertEquals(emptyList<String>(), BookTags.parse(""))
        assertEquals(emptyList<String>(), BookTags.parse("not json"))
    }

    @Test
    fun `dedupe and blank stripped`() {
        // serialize 清洗（去空白、去重）→ parse 得到干净列表
        assertEquals(
            listOf("a", "b"),
            BookTags.parse(BookTags.serialize(listOf("a", " b ", "a", "")))
        )
        // 脏数据直接 parse 同样被清洗（trim + 过滤 + 去重）
        assertEquals(
            listOf("b", "a"),
            BookTags.parse("""[" b ","a","a",""]""")
        )
    }

    @Test
    fun `legacy column default parses to empty`() {
        assertEquals(emptyList<String>(), BookTags.parse("[]"))
        assertTrue(BookTags.serialize(emptyList()) == "[]")
    }
}
