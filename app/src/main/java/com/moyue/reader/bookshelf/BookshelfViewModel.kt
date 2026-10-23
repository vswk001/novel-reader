package com.moyue.reader.bookshelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.moyue.reader.data.db.BookEntity
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
        val message: String? = null
    ) {
        val visible: List<BookEntity>
            get() {
                val q = query.trim()
                val filtered = if (q.isEmpty()) books else books.filter {
                    it.title.contains(q, ignoreCase = true)
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
                append("导入完成：成功 $ok 本")
                if (failed.isNotEmpty()) {
                    append("，失败 ${failed.size} 本：")
                    append(failed.joinToString("；") { "${it.fileName}（${it.message}）" })
                }
            }
            busy.value = false
            _ui.update { it.copy(message = msg) }
        }
    }

    fun scanFolder(treeUri: Uri) {
        viewModelScope.launch {
            busy.value = true
            _ui.update { it.copy(message = "正在扫描文件夹…") }
            val uris = importer.scanTree(treeUri)
            if (uris.isEmpty()) {
                busy.value = false
                _ui.update { it.copy(message = "该文件夹中没有找到支持的文件（txt/log/md）") }
            } else {
                _ui.update { it.copy(message = "扫描到 ${uris.size} 个文件，开始导入…") }
                val results = importer.importUris(uris, space)
                val ok = results.count { it.ok }
                busy.value = false
                _ui.update {
                    it.copy(
                        message = "扫描导入完成：成功 $ok / ${uris.size} 个文件" +
                            if (results.any { !it.ok }) "（重复或失败的已跳过）" else ""
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
                    message = if (deleteFile) "已删除「${book.title}」"
                    else "已移出书架（文件保留）"
                )
            }
        }
    }

    /** 在普通书架与隐私空间之间转移 */
    fun moveBook(book: BookEntity, toSpace: Int) {
        viewModelScope.launch {
            repo.setSpace(book.id, toSpace)
            _ui.update {
                it.copy(
                    message = if (toSpace == 1) "已移入隐私空间「${book.title}」"
                    else "已移回普通书架「${book.title}」"
                )
            }
        }
    }

    /** 用最新章节规则重新切分（进度保留） */
    fun reparseBook(book: BookEntity) {
        viewModelScope.launch {
            _ui.update { it.copy(message = "正在重新分析「${book.title}」的章节…") }
            runCatching { repo.reparseChapters(book.id) }
                .onSuccess { n -> _ui.update { it.copy(message = "重切完成：「${book.title}」共 $n 章（阅读进度已保留）") } }
                .onFailure { e -> _ui.update { it.copy(message = "重切失败：${e.message}") } }
        }
    }

    fun setSort(kind: SortKind) {
        viewModelScope.launch { settings.update { it.copy(sortBy = kind) } }
    }

    fun toggleGridView() {
        viewModelScope.launch { settings.update { it.copy(bookshelfGrid = !it.bookshelfGrid) } }
    }
}
