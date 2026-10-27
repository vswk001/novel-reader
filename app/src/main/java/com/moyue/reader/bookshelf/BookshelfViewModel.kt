package com.moyue.reader.bookshelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.moyue.reader.R
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.repo.BookTags
import com.moyue.reader.data.repo.ReaderPrefs
import com.moyue.reader.data.repo.SortKind
import com.moyue.reader.di.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 书架 ViewModel：space=0 普通书架，space=1 隐私空间。
 * 两个空间使用各自的页面路由，ViewModel 实例与数据完全独立。
 */
class BookshelfViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    val space: Int = (savedState.get<String>("space")?.toIntOrNull() ?: 0).let {
        if (it in 0..1) it else 0
    }

    data class UiState(
        val space: Int = 0,
        val books: List<BookEntity> = emptyList(),
        val prefs: ReaderPrefs = ReaderPrefs(),
        val query: String = "",
        val importing: Boolean = false,
        val message: String? = null,
        /** 当前标签筛选；null=全部 */
        val tagFilter: String? = null
    ) {
        /** 当前空间全部标签（按书籍数降序、同数按名称） */
        val allTags: List<Pair<String, Int>>
            get() = books
                .flatMap { BookTags.parse(it.tags) }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key to it.value }

        val visible: List<BookEntity>
            get() {
                val q = query.trim()
                var filtered = if (q.isEmpty()) books else books.filter {
                    it.title.contains(q, ignoreCase = true)
                }
                tagFilter?.let { t ->
                    filtered = filtered.filter { t in BookTags.parse(it.tags) }
                }
                val sorted = when (prefs.sortBy) {
                    SortKind.RECENT -> filtered.sortedByDescending { it.lastReadAt }
                    SortKind.TITLE -> filtered.sortedBy { it.title }
                    SortKind.ADDED -> filtered.sortedByDescending { it.addedAt }
                }
                return sorted.sortedByDescending { it.pinned } // 稳定排序，置顶优先
            }
    }

    private val repo = Graph.bookRepository
    private val settings = Graph.settingsRepository
    private val importer = Graph.bookImporter
    private val ctx get() = getApplication<Application>()

    private val query = MutableStateFlow("")
    private val busy = MutableStateFlow(false)

    private val _ui = MutableStateFlow(UiState(space = space))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.observeBooks(space), settings.prefs, query, busy) { books, prefs, q, b ->
                UiState(space = space, books = books, prefs = prefs, query = q, importing = b)
            }.collect { _ui.value = it }
        }
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun setTagFilter(tag: String?) {
        _ui.update { it.copy(tagFilter = tag) }
    }

    fun setBookTags(book: BookEntity, tags: List<String>) {
        viewModelScope.launch { repo.setTags(book.id, tags) }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            val results = importer.importUris(uris, space)
            val ok = results.count { it.ok }
            val failed = results.filter { !it.ok }
            val msg = buildString {
                append(ctx.getString(R.string.msg_import_done, ok))
                if (failed.isNotEmpty()) {
                    append(ctx.getString(R.string.msg_import_failed_part, failed.size))
                    append(
                        failed.joinToString(ctx.getString(R.string.sep_item)) {
                            "${it.fileName}（${it.message}）"
                        }
                    )
                }
            }
            busy.value = false
            _ui.update { it.copy(message = msg) }
        }
    }

    fun scanFolder(treeUri: Uri) {
        viewModelScope.launch {
            busy.value = true
            _ui.update { it.copy(message = ctx.getString(R.string.msg_scanning)) }
            val uris = importer.scanTree(treeUri)
            if (uris.isEmpty()) {
                busy.value = false
                _ui.update { it.copy(message = ctx.getString(R.string.msg_scan_empty)) }
            } else {
                _ui.update { it.copy(message = ctx.getString(R.string.msg_scan_found, uris.size)) }
                val results = importer.importUris(uris, space)
                val ok = results.count { it.ok }
                busy.value = false
                _ui.update {
                    it.copy(
                        message = ctx.getString(R.string.msg_scan_done, ok, uris.size) +
                            if (results.any { !it.ok }) ctx.getString(R.string.msg_scan_skipped) else ""
                    )
                }
            }
        }
    }

    fun togglePin(book: BookEntity) {
        viewModelScope.launch { repo.setPinned(book.id, !book.pinned) }
    }

    fun deleteBook(book: BookEntity, deleteFile: Boolean) {
        viewModelScope.launch {
            repo.deleteBook(book, deleteFile)
            _ui.update {
                it.copy(
                    message = if (deleteFile) ctx.getString(R.string.msg_deleted, book.title)
                    else ctx.getString(R.string.msg_removed)
                )
            }
        }
    }

    /** 移入回收站（7 天内可在设置中恢复） */
    fun trashBook(book: BookEntity) {
        viewModelScope.launch {
            repo.moveToTrash(book.id)
            _ui.update { it.copy(message = ctx.getString(R.string.msg_trash_moved)) }
        }
    }

    /** 在普通书架与隐私空间之间转移 */
    fun moveBook(book: BookEntity, toSpace: Int) {
        viewModelScope.launch {
            repo.setSpace(book.id, toSpace)
            _ui.update {
                it.copy(
                    message = if (toSpace == 1) ctx.getString(R.string.msg_moved_private, book.title)
                    else ctx.getString(R.string.msg_moved_library, book.title)
                )
            }
        }
    }

    /** 用最新章节规则重新切分（进度保留） */
    fun reparseBook(book: BookEntity) {
        viewModelScope.launch {
            _ui.update { it.copy(message = ctx.getString(R.string.msg_reparse_running, book.title)) }
            runCatching { repo.reparseChapters(book.id) }
                .onSuccess { n ->
                    _ui.update {
                        it.copy(message = ctx.getString(R.string.msg_reparse_done, book.title, n))
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(message = ctx.getString(R.string.msg_reparse_failed, e.message ?: "")) }
                }
        }
    }

    fun setSort(kind: SortKind) {
        viewModelScope.launch { settings.update { it.copy(sortBy = kind) } }
    }

    fun toggleGridView() {
        viewModelScope.launch { settings.update { it.copy(bookshelfGrid = !it.bookshelfGrid) } }
    }
}
