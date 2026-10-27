package com.moyue.reader.data.repo

import android.content.Context
import com.moyue.reader.R
import com.moyue.reader.data.db.AppDatabase
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.db.BookImageEntity
import com.moyue.reader.data.db.BookmarkEntity
import com.moyue.reader.data.db.ChapterEntity
import com.moyue.reader.data.db.HighlightEntity
import com.moyue.reader.data.db.ReadStatEntity
import com.moyue.reader.data.importer.ChapterParser
import com.moyue.reader.reader.PageSpans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookRepository(
    private val context: Context,
    db: AppDatabase,
    private val booksDir: File
) {
    private val bookDao = db.bookDao()
    private val chapterDao = db.chapterDao()
    private val bookmarkDao = db.bookmarkDao()
    private val statDao = db.readStatDao()
    private val highlightDao = db.highlightDao()
    private val imageDao = db.bookImageDao()

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

    suspend fun setTags(bookId: Long, tags: List<String>) =
        bookDao.setTags(bookId, BookTags.serialize(tags))

    /**
     * 用最新章节规则重新切分已导入书籍（基于应用内副本，无需源文件）。
     * 阅读进度按字符偏移映射到新章节，不会丢失；EPUB 的结构化目录不参与重切。
     * @return 新章节数
     */
    suspend fun reparseChapters(bookId: Long): Int = withContext(Dispatchers.IO) {
        val book = bookDao.get(bookId) ?: error(context.getString(R.string.err_book_missing))
        if (book.charset == "EPUB") error(context.getString(R.string.err_epub_no_reparse))
        val file = bookFile(bookId)
        if (!file.exists()) error(context.getString(R.string.err_copy_missing))
        val oldChapters = chapterDao.list(bookId)
        val oldHighlights = highlightDao.listForBook(bookId)
        val text = file.readText(Charsets.UTF_8)
        val chapters = withContext(Dispatchers.Default) { ChapterParser.parse(text) }
        chapterDao.deleteForBook(bookId)
        saveChapters(bookId, chapters, text)
        // 高亮迁移：旧(章内区间) → 全书绝对偏移 → 新(章内区间)，错位则删除
        if (oldHighlights.isNotEmpty()) {
            val oldByIdx = oldChapters.associateBy { it.idx }
            for (h in oldHighlights) {
                val oldCh = oldByIdx[h.chapterIdx]
                if (oldCh == null) {
                    highlightDao.delete(h)
                    continue
                }
                val absStart = oldCh.charStart + h.start
                val absEnd = oldCh.charStart + h.end
                val nIdx = chapters.indexOfFirst {
                    absStart >= it.charStart && absStart < it.charEnd
                }.let { if (it >= 0) it else 0 }
                val nCh = chapters[nIdx]
                val ns = (absStart - nCh.charStart).coerceIn(0, nCh.charEnd - nCh.charStart)
                val ne = (absEnd - nCh.charStart).coerceIn(ns, nCh.charEnd - nCh.charStart)
                if (ne > ns) {
                    highlightDao.update(h.copy(chapterIdx = nIdx, start = ns, end = ne))
                } else {
                    highlightDao.delete(h)
                }
            }
        }
        // 进度：按字符偏移找到所在的新章节
        val newIdx = chapters.indexOfFirst {
            book.curCharOffset >= it.charStart && book.curCharOffset < it.charEnd
        }.let { if (it >= 0) it else 0 }
        bookDao.update(
            book.copy(
                totalChars = text.length,
                chapterCount = chapters.size,
                curChapter = newIdx
            )
        )
        chapters.size
    }

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

    // ---- 回收站 ----

    suspend fun moveToTrash(bookId: Long) = bookDao.setDeletedAt(bookId, System.currentTimeMillis())

    suspend fun restoreFromTrash(bookId: Long) = bookDao.setDeletedAt(bookId, 0L)

    suspend fun listTrashed(space: Int): List<BookEntity> = bookDao.listTrash(space)

    /** 清空某空间回收站（连文件一起删） */
    suspend fun emptyTrash(space: Int) {
        for (b in bookDao.listTrash(space)) deleteBookById(b.id, deleteFile = true)
    }

    /** 启动时清理：删除超过 [keepMillis] 的回收站书籍 */
    suspend fun purgeExpiredTrash(keepMillis: Long = 7L * 24 * 3600 * 1000) {
        val deadline = System.currentTimeMillis() - keepMillis
        for (b in bookDao.listExpired(deadline)) deleteBookById(b.id, deleteFile = true)
    }

    suspend fun addReadTime(bookId: Long, deltaMillis: Long) {
        if (deltaMillis <= 0) return
        bookDao.addReadTime(bookId, deltaMillis)
        val date = today()
        val existing = statDao.get(date)
        statDao.upsert(ReadStatEntity(date, (existing?.millis ?: 0L) + deltaMillis))
    }

    suspend fun readTimeTotalMillis(): Long = statDao.totalMillis()

    suspend fun readStatToday(): Long = statDao.get(today())?.millis ?: 0L

    /** 连续阅读天数：从今天（今天没读则从昨天）往回数，每天阅读 >= 1 分钟即算 */
    suspend fun streakDays(): Int = withContext(Dispatchers.Default) {
        val active = statDao.getAllOnce().filter { it.millis >= 60_000L }
            .map { it.date }.toHashSet()
        if (active.isEmpty()) return@withContext 0
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        // 今天尚未达标时从昨天起算（连续记录不因当天还没读而断）
        if (today() !in active) cal.add(java.util.Calendar.DATE, -1)
        var streak = 0
        while (fmt.format(cal.time) in active) {
            streak++
            cal.add(java.util.Calendar.DATE, -1)
        }
        streak
    }

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

    // ---- 划线高亮 ----

    fun observeHighlights(bookId: Long): Flow<List<HighlightEntity>> =
        highlightDao.observeForBook(bookId)

    suspend fun listHighlightsOnce(bookId: Long): List<HighlightEntity> =
        highlightDao.listForBook(bookId)

    suspend fun addHighlight(
        bookId: Long, chapterIdx: Int, start: Int, end: Int, text: String, note: String, color: Int
    ): Long = highlightDao.insert(
        HighlightEntity(
            bookId = bookId, chapterIdx = chapterIdx, start = start, end = end,
            text = text, note = note, color = color, createdAt = System.currentTimeMillis()
        )
    )

    suspend fun updateHighlight(h: HighlightEntity) = highlightDao.update(h)

    suspend fun deleteHighlight(h: HighlightEntity) = highlightDao.delete(h)

    // ---- EPUB 内联图片 ----

    fun imageDir(bookId: Long): File = File(booksDir, "${bookId}_img")

    suspend fun imagesForChapter(bookId: Long, chapterIdx: Int): List<BookImageEntity> =
        imageDao.listForChapter(bookId, chapterIdx)

    suspend fun saveImages(items: List<BookImageEntity>) = imageDao.insertAll(items)

    suspend fun deleteBook(book: BookEntity, deleteFile: Boolean) {
        deleteBookById(book.id, deleteFile)
    }

    suspend fun deleteBookById(bookId: Long, deleteFile: Boolean) {
        bookDao.delete(bookId)
        chapterDao.deleteForBook(bookId)
        bookmarkDao.deleteForBook(bookId)
        highlightDao.deleteForBook(bookId)
        imageDao.deleteForBook(bookId)
        if (deleteFile) {
            bookFile(bookId).delete()
            imageDir(bookId).deleteRecursively()
        }
    }

    /** 清空某个空间的全部书籍（忘记密码时只清隐私空间、保留普通书架）；含回收站 */
    suspend fun deleteAllInSpace(space: Int, deleteFile: Boolean) {
        for (b in bookDao.getAllOnce(space)) {
            deleteBookById(b.id, deleteFile)
        }
        for (b in bookDao.listTrash(space)) {
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
                    snippet = text.substring(snipStart, snipEnd)
                        .replace('\n', ' ')
                        .replace(PageSpans.IMG_CHAR, ' ')
                        .trim()
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
