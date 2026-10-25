package com.moyue.reader.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.moyue.reader.R
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
        val name = queryName(uri) ?: context.getString(R.string.importer_unknown_name)
        val size = querySize(uri)
        try {
            if (size > MAX_FILE_BYTES) {
                return@withContext ImportResult(name, false, context.getString(R.string.err_too_big))
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext ImportResult(name, false, context.getString(R.string.err_unreadable))
            importBytes(name, size, bytes, space)
        } catch (e: EpubParseException) {
            val msg = if (e.detail != null) context.getString(e.resId, e.detail)
            else context.getString(e.resId)
            ImportResult(name, false, msg)
        } catch (e: Exception) {
            val reason = e.message ?: context.getString(R.string.err_unknown)
            ImportResult(name, false, context.getString(R.string.err_import_failed, reason))
        }
    }

    /** Wi-Fi 传书：本地临时文件导入（浏览器上传） */
    suspend fun importFile(file: java.io.File, displayName: String, space: Int = 0): ImportResult =
        withContext(Dispatchers.IO) {
            val name = displayName.ifBlank { context.getString(R.string.importer_unknown_name) }
            try {
                val size = file.length()
                if (size > MAX_FILE_BYTES) {
                    return@withContext ImportResult(name, false, context.getString(R.string.err_too_big))
                }
                val bytes = if (file.exists()) file.readBytes()
                else return@withContext ImportResult(name, false, context.getString(R.string.err_unreadable))
                importBytes(name, size, bytes, space)
            } catch (e: EpubParseException) {
                val msg = if (e.detail != null) context.getString(e.resId, e.detail)
                else context.getString(e.resId)
                ImportResult(name, false, msg)
            } catch (e: Exception) {
                val reason = e.message ?: context.getString(R.string.err_unknown)
                ImportResult(name, false, context.getString(R.string.err_import_failed, reason))
            }
        }

    /** 导入管线：查重 → 编码/EPUB 解析 → 落盘 → 章节入库 */
    private suspend fun importBytes(
        name: String,
        size: Long,
        bytes: ByteArray,
        space: Int
    ): ImportResult {
        val title = name.substringBeforeLast('.')
        if (bytes.isEmpty()) return ImportResult(name, false, context.getString(R.string.err_empty_file))
        if (repo.isDuplicate(title, size, space)) {
            return ImportResult(name, false, context.getString(R.string.err_duplicate))
        }
        return try {
            var bookTitle = title
            val charsetName: String
            val text: String
            val chapters: List<ChapterParser.Chapter>
            // EPUB 图片：(字节集, 全书偏移+href 列表)，createBook 后落盘
            var pendingImages: Pair<Map<String, ByteArray>, List<Triple<Int, Int, String>>>? = null

            if (name.substringAfterLast('.', "").equals("epub", ignoreCase = true)) {
                // EPUB：结构化解析（OPF/spine/NCX），转成与 TXT 相同的规范化文本 + 章节偏移
                val epub = EpubParser.parse(bytes)
                if (epub.title.isNotBlank()) bookTitle = epub.title
                val sb = StringBuilder()
                val list = mutableListOf<ChapterParser.Chapter>()
                val imgPositions = mutableListOf<Triple<Int, Int, String>>() // (章idx, 全书偏移, href)
                for ((ci, c) in epub.chapters.withIndex()) {
                    val start = sb.length
                    sb.append(c.title).append('\n')
                    val normalized = TextNormalizer.normalize(c.text)
                    sb.append(normalized)
                    list += ChapterParser.Chapter(c.title, start, sb.length)
                    val offsets = EpubParser.imageCharOffsets(start, c.title, normalized)
                    offsets.zip(c.imageHrefs).forEach { (off, href) ->
                        imgPositions += Triple(ci, off, href)
                    }
                }
                charsetName = "EPUB"
                text = sb.toString()
                chapters = list
                pendingImages = epub.images to imgPositions
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
                pendingImages?.let { (imgBytes, positions) ->
                    saveEpubImages(bookId, chapters, imgBytes, positions)
                }
            } catch (e: Exception) {
                runCatching { repo.deleteBookById(bookId, deleteFile = true) }
                throw e
            }
            ImportResult(
                name, true,
                context.getString(R.string.msg_imported, charsetName, chapters.size)
            )
        } catch (e: EpubParseException) {
            throw e
        } catch (e: Exception) {
            val reason = e.message ?: context.getString(R.string.err_unknown)
            ImportResult(name, false, context.getString(R.string.err_import_failed, reason))
        }
    }

    /** EPUB 图片落盘：写入 books/{bookId}_img/，记录章节内偏移与解码尺寸（分页测量用） */
    private suspend fun saveEpubImages(
        bookId: Long,
        chapters: List<ChapterParser.Chapter>,
        imgBytes: Map<String, ByteArray>,
        positions: List<Triple<Int, Int, String>>
    ) = withContext(Dispatchers.IO) {
        if (positions.isEmpty()) return@withContext
        val dir = repo.imageDir(bookId).apply { mkdirs() }
        val entities = mutableListOf<com.moyue.reader.data.db.BookImageEntity>()
        positions.forEachIndexed { i, (chapterIdx, absOffset, href) ->
            val data = imgBytes[href] ?: return@forEachIndexed
            val ch = chapters.getOrNull(chapterIdx) ?: return@forEachIndexed
            val ext = href.substringAfterLast('.', "img").lowercase().take(4)
            val fileName = "$i.$ext"
            java.io.File(dir, fileName).writeBytes(data)
            val bounds = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            entities += com.moyue.reader.data.db.BookImageEntity(
                bookId = bookId,
                chapterIdx = chapterIdx,
                charOffset = (absOffset - ch.charStart).coerceAtLeast(0),
                fileName = fileName,
                srcW = bounds.outWidth.coerceAtLeast(1),
                srcH = bounds.outHeight.coerceAtLeast(1)
            )
        }
        if (entities.isNotEmpty()) repo.saveImages(entities)
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
