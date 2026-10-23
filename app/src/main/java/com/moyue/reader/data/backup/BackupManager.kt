package com.moyue.reader.data.backup

import android.content.Context
import android.net.Uri
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.db.BookmarkEntity
import com.moyue.reader.data.repo.BookRepository
import com.moyue.reader.data.repo.ReaderPrefs
import com.moyue.reader.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 本地备份：书架元数据 + 阅读进度 + 书签 + 设置 导出为 JSON。
 * 注意：备份不包含书籍文件本身（文件较大且属用户源数据），恢复时按
 * 「书名 + 原文件大小」匹配本机已有书籍合并进度。
 */
class BackupManager(
    private val context: Context,
    private val repo: BookRepository,
    private val settings: SettingsRepository
) {
    @Serializable
    data class BackupBookmark(
        val chapterIdx: Int,
        val charOffset: Int,
        val excerpt: String,
        val createdAt: Long
    )

    @Serializable
    data class BackupBook(
        val title: String,
        val sizeBytes: Long,
        val charset: String,
        val totalChars: Int,
        val chapterCount: Int,
        val curChapter: Int,
        val curCharOffset: Int,
        val readTimeMillis: Long,
        val addedAt: Long,
        val lastReadAt: Long,
        val pinned: Boolean,
        val coverHue: Int,
        val space: Int = 0,
        val bookmarks: List<BackupBookmark>
    )

    @Serializable
    data class BackupData(
        val app: String = "moyue",
        val version: Int = 1,
        val exportedAt: Long,
        val prefs: ReaderPrefs,
        val books: List<BackupBook>
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val books = repo.booksAll()
            val payload = BackupData(
                exportedAt = System.currentTimeMillis(),
                prefs = settings.current(),
                books = books.map { b ->
                    b.toBackup().copy(
                        bookmarks = repo.bookmarksOnce(b.id).map { it.toBackupBookmark() }
                    )
                }
            )
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(json.encodeToString(BackupData.serializer(), payload).toByteArray(Charsets.UTF_8))
            } ?: error("无法写入备份文件")
            books.size
        }
    }

    /** 返回 (匹配到的书数, 合并进度数) */
    suspend fun importFrom(uri: Uri): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("无法读取备份文件")
            val data = json.decodeFromString(BackupData.serializer(), text)
            runCatching { settings.update { data.prefs } }
            val local = repo.booksAll()
            var matched = 0
            var merged = 0
            for (bk in data.books) {
                val target = local.firstOrNull { it.title == bk.title && it.sizeBytes == bk.sizeBytes }
                if (target == null) continue
                matched++
                val newer = bk.lastReadAt > target.lastReadAt
                if (newer) {
                    repo.saveProgressRaw(target.id, bk.curChapter, bk.curCharOffset, bk.lastReadAt)
                    merged++
                }
                val existing = repo.bookmarksOnce(target.id)
                for (bm in bk.bookmarks) {
                    val dup = existing.any {
                        it.chapterIdx == bm.chapterIdx && it.charOffset == bm.charOffset
                    }
                    if (!dup) {
                        repo.addBookmark(target.id, bm.chapterIdx, bm.charOffset, bm.excerpt)
                    }
                }
            }
            matched to merged
        }
    }

    companion object {
        fun suggestFileName(): String =
            "moyue-backup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(Date()) + ".json"
    }
}

private fun BookEntity.toBackup() = BackupManager.BackupBook(
    title = title,
    sizeBytes = sizeBytes,
    charset = charset,
    totalChars = totalChars,
    chapterCount = chapterCount,
    curChapter = curChapter,
    curCharOffset = curCharOffset,
    readTimeMillis = readTimeMillis,
    addedAt = addedAt,
    lastReadAt = lastReadAt,
    pinned = pinned,
    coverHue = coverHue,
    space = space,
    bookmarks = emptyList()
)

private fun BookmarkEntity.toBackupBookmark() = BackupManager.BackupBookmark(
    chapterIdx = chapterIdx,
    charOffset = charOffset,
    excerpt = excerpt,
    createdAt = createdAt
)
