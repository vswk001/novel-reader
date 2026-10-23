package com.moyue.reader.data.repo

import com.moyue.reader.data.db.AppDatabase
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.db.BookmarkEntity
import com.moyue.reader.data.db.ChapterEntity
import com.moyue.reader.data.db.ReadStatEntity
import com.moyue.reader.data.importer.ChapterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookRepository(
    db: AppDatabase,
    private val booksDir: File
) {
    private val bookDao = db.bookDao()
    private val chapterDao = db.chapterDao()
    private val bookmarkDao = db.bookmarkDao()
    private val statDao = db.readStatDao()

    fun observeBooks(space: Int): Flow<List<BookEntity>> = bookDao.observeAll(space)

    fun observeBook(id: Long): Flow<BookEntity?> = bookDao.observe(id)

    fun observeChapters(bookId: Long): Flow<List<ChapterEntity>> = chapterDao.observeList(bookId)

    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>> = bookmarkDao.observeForBook(bookId)

    suspend fun getBook(id: Long): BookEntity? = bookDao.get(id)

    suspend fun booksOnce(space: Int): List<BookEntity> = bookDao.getAllOnce(space)

    /** 全部空间的书籍（备份导出用） */
    suspend fun booksAll(): List<BookEntity> = bookDao.getAll()

    suspend fun bookmarksOnce(bookId: Long): List<BookmarkEntity> = bookmarkDao.listForBook(bookId)

    suspend fun saveProgressRaw(bookId: Long, chapter: Int, charOffset: Int, lastReadAt: Long) {
        bookDao.updateProgress(bookId, chapter, charOffset, lastReadAt)
    }

    suspend fun listChapters(bookId: Long): List<ChapterEntity> = chapterDao.list(bookId)

    suspend fun isDuplicate(title: String, sizeBytes: Long, space: Int): Boolean =
        bookDao.countDuplicate(title, sizeBytes, space) > 0

    /** 在普通书架与隐私空间之间转移书籍（文件、进度、书签保持不变） */
    suspend fun setSpace(bookId: Long, space: Int) = bookDao.setSpace(bookId, space)

    suspend fun createBook(
        title: String,
        fileName: String,
        sizeBytes: Long,
        charset: String,
        totalChars: Int,
        chapterCount: Int,
        space: Int = 0
    ): Long {
        val hue = ((title.hashCode() % 360) + 360) % 360
        val now = System.currentTimeMillis()
        return bookDao.insert(
            BookEntity(
                title = title,
                fileName = fileName,
                sizeBytes = sizeBytes,
                charset = charset,
                totalChars = totalChars,
                chapterCount = chapterCount,
                addedAt = now,
                lastReadAt = now,
                coverHue = hue,
                space = space
            )
        )
    }

    fun bookFile(bookId: Long): File = File(booksDir, "$bookId.txt")

    suspend fun writeBookFile(bookId: Long, text: String) = withContext(Dispatchers.IO) {
        booksDir.mkdirs()
        bookFile(bookId).writeText(text, Charsets.UTF_8)
    }

    /** 保存章节索引：字符偏移来自解析结果，字节偏移逐行累计换算 */
    suspend fun saveChapters(bookId: Long, chapters: List<ChapterParser.Chapter>, text: String) =
        withContext(Dispatchers.Default) {
            val starts = chapters.map { it.charStart }
            val byteStarts = computeByteOffsets(text, starts)
            val totalBytes = bookFile(bookId).length().toInt()
            val entities = chapters.mapIndexed { i, ch ->
                ChapterEntity(
                    bookId = bookId,
                    idx = i,
                    title = ch.title,
                    charStart = ch.charStart,
                    charEnd = ch.charEnd,
                    byteStart = byteStarts[i],
                    byteEnd = if (i + 1 < chapters.size) byteStarts[i + 1] else totalBytes
                )
            }
            chapterDao.insertAll(entities)
        }

    /** 按字节区间读取章节文本，支持超大文件按需加载 */
    suspend fun readChapterText(bookId: Long, chapter: ChapterEntity): String =
        withContext(Dispatchers.IO) {
            val file = bookFile(bookId)
            if (!file.exists()) return@withContext ""
            val len = (chapter.byteEnd - chapter.byteStart).coerceAtLeast(0)
            if (len == 0) return@withContext ""
            RandomAccessFile(file, "r").use { raf ->
                val buf = ByteArray(len)
                raf.seek(chapter.byteStart.toLong())
                raf.readFully(buf)
                String(buf, Charsets.UTF_8)
            }
        }

    suspend fun saveProgress(bookId: Long, chapter: Int, charOffset: Int) {
        bookDao.updateProgress(bookId, chapter, charOffset, System.currentTimeMillis())
    }

    suspend fun setPinned(bookId: Long, pinned: Boolean) = bookDao.setPinned(bookId, pinned)

    suspend fun addReadTime(bookId: Long, deltaMillis: Long) {
        if (deltaMillis <= 0) return
        bookDao.addReadTime(bookId, deltaMillis)
        val date = today()
        val existing = statDao.get(date)
        statDao.upsert(ReadStatEntity(date, (existing?.millis ?: 0L) + deltaMillis))
    }

    suspend fun readTimeTotalMillis(): Long = statDao.totalMillis()

    suspend fun readStatToday(): Long = statDao.get(today())?.millis ?: 0L

    suspend fun addBookmark(bookId: Long, chapterIdx: Int, charOffset: Int, excerpt: String): Long =
        bookmarkDao.insert(
            BookmarkEntity(
                bookId = bookId,
                chapterIdx = chapterIdx,
                charOffset = charOffset,
                excerpt = excerpt,
                createdAt = System.currentTimeMillis()
            )
        )

    suspend fun deleteBookmark(b: BookmarkEntity) = bookmarkDao.delete(b)

    suspend fun deleteBook(book: BookEntity, deleteFile: Boolean) {
        deleteBookById(book.id, deleteFile)
    }

    suspend fun deleteBookById(bookId: Long, deleteFile: Boolean) {
        bookDao.delete(bookId)
        chapterDao.deleteForBook(bookId)
        bookmarkDao.deleteForBook(bookId)
        if (deleteFile) {
            bookFile(bookId).delete()
        }
    }

    /** 清空某个空间的全部书籍（忘记密码时只清隐私空间、保留普通书架） */
    suspend fun deleteAllInSpace(space: Int, deleteFile: Boolean) {
        val books = bookDao.getAllOnce(space)
        for (b in books) {
            deleteBookById(b.id, deleteFile)
        }
    }

    data class SearchHit(
        val chapterIdx: Int,
        val chapterTitle: String,
        val charOffset: Int,
        val snippet: String
    )

    /** 书内全文搜索（大小写不敏感），命中上限防卡顿 */
    suspend fun searchBook(
        bookId: Long,
        chapters: List<ChapterEntity>,
        query: String,
        limit: Int = 300
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.lowercase()
        val hits = mutableListOf<SearchHit>()
        for (ch in chapters) {
            val text = readChapterText(bookId, ch)
            if (text.isEmpty()) continue
            val lower = text.lowercase()
            var from = 0
            while (true) {
                val i = lower.indexOf(q, from)
                if (i < 0) break
                val snipStart = (i - 24).coerceAtLeast(0)
                val snipEnd = (i + q.length + 48).coerceAtMost(text.length)
                hits += SearchHit(
                    chapterIdx = ch.idx,
                    chapterTitle = ch.title,
                    charOffset = ch.charStart + i,
                    snippet = text.substring(snipStart, snipEnd).replace('\n', ' ').trim()
                )
                from = i + q.length
                if (hits.size >= limit) return@withContext hits
            }
        }
        hits
    }

    companion object {
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        /**
         * 给定一组位于行首的字符偏移（升序），换算 UTF-8 字节偏移。
         * 章节标题必在行首，因此逐行累计即可精确对齐。
         */
        fun computeByteOffsets(text: String, charOffsets: List<Int>): IntArray {
            val res = IntArray(charOffsets.size) { -1 }
            var charPos = 0
            var bytePos = 0
            var idx = 0
            var lineStart = 0
            val n = text.length
            while (idx < charOffsets.size && charOffsets[idx] <= n) {
                while (idx < charOffsets.size && charOffsets[idx] == charPos) {
                    res[idx] = bytePos
                    idx++
                }
                if (idx >= charOffsets.size || lineStart >= n) break
                val nl = text.indexOf('\n', lineStart)
                val lineEnd = if (nl == -1) n else nl + 1
                val line = text.substring(lineStart, lineEnd)
                bytePos += line.toByteArray(Charsets.UTF_8).size
                charPos = lineEnd
                lineStart = lineEnd
            }
            // 未匹配到的（越界防御）落到文件末尾
            while (idx < charOffsets.size) {
                res[idx] = bytePos
                idx++
            }
            return res
        }
    }
}
