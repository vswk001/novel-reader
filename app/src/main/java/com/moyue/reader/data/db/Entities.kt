package com.moyue.reader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val fileName: String,
    val sizeBytes: Long,
    val charset: String,
    val totalChars: Int,
    val chapterCount: Int,
    val addedAt: Long,
    val lastReadAt: Long,
    /** 当前章节下标 */
    val curChapter: Int = 0,
    /** 章节内字符偏移（相对规范化全文），与分页参数无关，重排不丢进度 */
    val curCharOffset: Int = 0,
    val readTimeMillis: Long = 0L,
    val pinned: Boolean = false,
    /** 封面渐变色调 0..360 */
    val coverHue: Int = 0,
    /** 所属空间：0=普通书架，1=隐私空间（完全独立管理） */
    val space: Int = 0,
    /** 回收站：0=正常在架；>0=移入回收站的时间戳（7 天后自动清理） */
    val deletedAt: Long = 0L
)

@Entity(tableName = "chapters", primaryKeys = ["bookId", "idx"])
data class ChapterEntity(
    val bookId: Long,
    val idx: Int,
    val title: String,
    /** 规范化全文中的字符区间 [charStart, charEnd) */
    val charStart: Int,
    val charEnd: Int,
    /** UTF-8 落盘文件中的字节区间，用于大文件按需读取 */
    val byteStart: Int,
    val byteEnd: Int
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val chapterIdx: Int,
    val charOffset: Int,
    val excerpt: String,
    val createdAt: Long
)

@Entity(tableName = "read_stats")
data class ReadStatEntity(
    /** yyyy-MM-dd */
    @PrimaryKey val date: String,
    val millis: Long
)
