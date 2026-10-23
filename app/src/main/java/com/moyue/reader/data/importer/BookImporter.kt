package com.moyue.reader.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.moyue.reader.data.repo.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/** SAF 导入管线：读字节 → 编码检测 → 解码规范化 → 落盘 → 章节索引入库 */
class BookImporter(
    private val context: Context,
    private val repo: BookRepository
) {
    data class ImportResult(val fileName: String, val ok: Boolean, val message: String)

    companion object {
        val SUPPORTED_EXTS = setOf("txt", "log", "md", "epub")
        private const val MAX_FILE_BYTES = 256L * 1024 * 1024
    }

    suspend fun importUris(uris: List<Uri>, space: Int = 0): List<ImportResult> =
        uris.map { importUri(it, space) }

    suspend fun importUri(uri: Uri, space: Int = 0): ImportResult = withContext(Dispatchers.IO) {
        val name = queryName(uri) ?: "未知文件.txt"
        try {
            if (querySize(uri) > MAX_FILE_BYTES) {
                return@withContext ImportResult(name, false, "文件过大（上限 256MB）")
            }
            val title = name.substringBeforeLast('.')
            val size = querySize(uri)
            if (repo.isDuplicate(title, size, space)) {
                return@withContext ImportResult(name, false, "已在书架中")
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext ImportResult(name, false, "无法读取文件")
            if (bytes.isEmpty()) return@withContext ImportResult(name, false, "文件为空")

            var bookTitle = title
            val charsetName: String
            val text: String
            val chapters: List<ChapterParser.Chapter>

            if (name.substringAfterLast('.', "").equals("epub", ignoreCase = true)) {
                // EPUB：结构化解析（OPF/spine/NCX），转成与 TXT 相同的规范化文本 + 章节偏移
                val epub = EpubParser.parse(bytes)
                if (epub.title.isNotBlank()) bookTitle = epub.title
                val sb = StringBuilder()
                val list = mutableListOf<ChapterParser.Chapter>()
                for (c in epub.chapters) {
                    val start = sb.length
                    sb.append(c.title).append('\n')
                    sb.append(TextNormalizer.normalize(c.text))
                    list += ChapterParser.Chapter(c.title, start, sb.length)
                }
                charsetName = "EPUB"
                text = sb.toString()
                chapters = list
            } else {
                charsetName = CharsetDetector.detect(bytes)
                val bom = CharsetDetector.bomLength(bytes)
                val raw = String(bytes, bom, bytes.size - bom, Charset.forName(charsetName))
                text = TextNormalizer.normalize(raw)
                chapters = ChapterParser.parse(text)
            }

            val bookId = repo.createBook(
                title = bookTitle,
                fileName = name,
                sizeBytes = size,
                charset = charsetName,
                totalChars = text.length,
                chapterCount = chapters.size,
                space = space
            )
            try {
                repo.writeBookFile(bookId, text)
                repo.saveChapters(bookId, chapters, text)
            } catch (e: Exception) {
                runCatching { repo.deleteBookById(bookId, deleteFile = true) }
                throw e
            }
            ImportResult(name, true, "已导入（$charsetName，${chapters.size} 章）")
        } catch (e: Exception) {
            ImportResult(name, false, "导入失败：${e.message ?: "未知错误"}")
        }
    }

    /** 递归扫描 SAF 目录树，返回支持的文档 Uri */
    suspend fun scanTree(treeUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext emptyList()
        val out = mutableListOf<Uri>()
        walk(root, out)
        out
    }

    private fun walk(dir: DocumentFile, out: MutableList<Uri>) {
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (f in children) {
            if (out.size >= 500) return // 安全上限
            if (f.isDirectory) {
                walk(f, out)
            } else {
                val n = f.name ?: continue
                val ext = n.substringAfterLast('.', "").lowercase()
                if (ext in SUPPORTED_EXTS && f.length() in 1..MAX_FILE_BYTES) {
                    out += f.uri
                }
            }
        }
    }

    private fun queryName(uri: Uri): String? =
        query(uri)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        } ?: uri.lastPathSegment

    private fun querySize(uri: Uri): Long =
        query(uri)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) c.getLong(idx) else 0L
        } ?: 0L

    private fun query(uri: Uri) = runCatching {
        context.contentResolver.query(uri, null, null, null, null)
    }.getOrNull()
}
