package com.moyue.reader.reader

import android.app.Application
import android.graphics.Typeface
import android.text.TextPaint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.db.BookmarkEntity
import com.moyue.reader.data.db.ChapterEntity
import com.moyue.reader.data.repo.BookRepository
import com.moyue.reader.data.repo.ReaderPrefs
import com.moyue.reader.data.repo.SettingsRepository
import com.moyue.reader.di.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    private val bookId: Long = savedState.get<String>("bookId")?.toLongOrNull() ?: -1L
    private val repo: BookRepository = Graph.bookRepository
    private val settings: SettingsRepository = Graph.settingsRepository

    /** 由阅读页测量出的排版参数 */
    data class LayoutSpec(
        val pageWidth: Int,
        val pageHeight: Int,
        val textSizePx: Float,
        val lineSpacing: Float,
        val serif: Boolean
    )

    data class UiState(
        val book: BookEntity? = null,
        val prefs: ReaderPrefs = ReaderPrefs(),
        val chapters: List<ChapterEntity> = emptyList(),
        val chapterIdx: Int = 0,
        val chapterText: String = "",
        val pages: List<PaginationEngine.Page> = emptyList(),
        val pageIdx: Int = 0,
        val loading: Boolean = true,
        val menuVisible: Boolean = false,
        val bookmarks: List<BookmarkEntity> = emptyList(),
        val searchResults: List<BookRepository.SearchHit>? = null,
        val searching: Boolean = false,
        /** 短暂浮层提示（加书签等操作反馈），2 秒自动消失 */
        val transient: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var spec: LayoutSpec? = null

    /** 待定位的绝对字符偏移（恢复进度/跳转/重排后使用） */
    private var pendingOffset: Int? = null

    private var layoutJob: Job? = null
    private var chapterJob: Job? = null
    private var chaptersLoaded = false

    // ---- 阅读时长 ----
    private var readStartAt = 0L

    init {
        viewModelScope.launch {
            repo.observeBook(bookId).collect { b ->
                _ui.update { it.copy(book = b) }
                if (b != null && !chaptersLoaded) {
                    chaptersLoaded = true
                    loadChapters(b)
                }
            }
        }
        viewModelScope.launch {
            repo.observeBookmarks(bookId).collect { bs ->
                _ui.update { it.copy(bookmarks = bs) }
            }
        }
        viewModelScope.launch {
            settings.prefs.collect { p ->
                val old = _ui.value.prefs
                _ui.update { it.copy(prefs = p) }
                if (old != p && chaptersLoaded) {
                    // 排版参数变化：保持当前阅读位置重排
                    pendingOffset = currentCharOffset()
                    spec?.let { applySpec(it) }
                }
            }
        }
    }

    private suspend fun loadChapters(book: BookEntity) {
        val chapters = repo.listChapters(book.id)
        _ui.update {
            it.copy(
                chapters = chapters,
                chapterIdx = book.curChapter.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
            )
        }
        pendingOffset = book.curCharOffset
        loadChapter(_ui.value.chapterIdx)
    }

    /** @param targetOffset 绝对字符偏移；缺省用 pendingOffset（进度恢复） */
    fun loadChapter(idx: Int, targetOffset: Int = -1, atChapterEnd: Boolean = false) {
        val chapters = _ui.value.chapters
        if (chapters.isEmpty()) return
        val safe = idx.coerceIn(0, chapters.size - 1)
        if (targetOffset >= 0) {
            pendingOffset = targetOffset
        } else if (atChapterEnd) {
            pendingOffset = (chapters[safe].charEnd - 1).coerceAtLeast(chapters[safe].charStart)
        }
        chapterJob?.cancel()
        chapterJob = viewModelScope.launch {
            _ui.update { it.copy(chapterIdx = safe, pages = emptyList(), pageIdx = 0, loading = true) }
            val text = repo.readChapterText(bookId, chapters[safe])
            _ui.update { it.copy(chapterText = text) }
            val s = spec
            if (s != null) paginate(s) else _ui.update { it.copy(loading = false) }
        }
    }

    fun applySpec(newSpec: LayoutSpec) {
        spec = newSpec
        if (_ui.value.chapters.isNotEmpty()) paginate(newSpec)
    }

    private fun paginate(s: LayoutSpec) {
        layoutJob?.cancel()
        layoutJob = viewModelScope.launch(Dispatchers.Default) {
            val st = _ui.value
            val text = st.chapterText
            val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = s.textSizePx
                typeface = if (s.serif) Typeface.SERIF else Typeface.DEFAULT
            }
            val engine = PaginationEngine(paint, s.pageWidth, s.pageHeight, s.lineSpacing)
            val pages = engine.paginate(text)
            // 偏移是全书绝对值，页表是章节内相对值：必须换算后再定位
            val chapterStart = st.chapters.getOrNull(st.chapterIdx)?.charStart ?: 0
            val absOffset = pendingOffset ?: currentCharOffset()
            pendingOffset = null
            val relOffset = (absOffset - chapterStart).coerceIn(0, text.length)
            val idx = PaginationEngine.indexOfOffset(pages, relOffset)
            _ui.update { it.copy(pages = pages, pageIdx = idx, loading = false) }
        }
    }

    fun onPageSettled(page: Int) {
        val st = _ui.value
        if (st.pages.isEmpty()) return
        val safe = page.coerceIn(0, st.pages.size - 1)
        if (safe != st.pageIdx) {
            _ui.update { it.copy(pageIdx = safe) }
        }
        persistProgress()
    }

    fun currentCharOffset(): Int {
        val st = _ui.value
        val ch = st.chapters.getOrNull(st.chapterIdx) ?: return st.book?.curCharOffset ?: 0
        val page = st.pages.getOrNull(st.pageIdx) ?: return st.book?.curCharOffset ?: ch.charStart
        return ch.charStart + page.start
    }

    private fun persistProgress() {
        val st = _ui.value
        val book = st.book ?: return
        val ch = st.chapters.getOrNull(st.chapterIdx) ?: return
        val page = st.pages.getOrNull(st.pageIdx) ?: return
        viewModelScope.launch {
            repo.saveProgress(book.id, st.chapterIdx, ch.charStart + page.start)
        }
    }

    fun prevChapter() {
        val i = _ui.value.chapterIdx
        if (i > 0) loadChapter(i - 1, atChapterEnd = true)
    }

    fun nextChapter() {
        val i = _ui.value.chapterIdx
        if (i < _ui.value.chapters.size - 1) loadChapter(i + 1)
    }

    fun jumpToChapter(idx: Int, offsetInChapter: Int = 0) {
        val ch = _ui.value.chapters.getOrNull(idx) ?: return
        loadChapter(idx, targetOffset = ch.charStart + offsetInChapter)
    }

    fun jumpToAbsOffset(chapterIdx: Int, absOffset: Int) {
        loadChapter(chapterIdx, targetOffset = absOffset)
    }

    fun toggleMenu(visible: Boolean? = null) {
        _ui.update { it.copy(menuVisible = visible ?: !it.menuVisible) }
    }

    fun setMenuVisible(v: Boolean) {
        _ui.update { if (it.menuVisible == v) it else it.copy(menuVisible = v) }
    }

    fun updatePrefs(transform: (ReaderPrefs) -> ReaderPrefs) {
        viewModelScope.launch { settings.update(transform) }
    }

    // ---- 书内搜索 ----

    fun search(query: String) {
        if (query.isBlank()) {
            _ui.update { it.copy(searchResults = null, searching = false) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(searching = true) }
            val res = repo.searchBook(bookId, _ui.value.chapters, query.trim())
            _ui.update { it.copy(searching = false, searchResults = res) }
        }
    }

    fun clearSearch() {
        _ui.update { it.copy(searchResults = null, searching = false) }
    }

    // ---- 书签 ----

    fun addBookmarkAtCurrentPage() {
        val st = _ui.value
        val ch = st.chapters.getOrNull(st.chapterIdx)
        val page = st.pages.getOrNull(st.pageIdx)
        if (ch == null || page == null) {
            showTransient("页面尚未加载完成，请稍候")
            return
        }
        val offset = ch.charStart + page.start
        // 同一页去重：避免误以为没点上而连点产生重复书签
        if (st.bookmarks.any { it.chapterIdx == st.chapterIdx && it.charOffset == offset }) {
            showTransient("本页已在书签中")
            return
        }
        val from = page.start.coerceIn(0, st.chapterText.length)
        val to = page.end.coerceIn(0, st.chapterText.length)
        val excerpt = st.chapterText.substring(from, to)
            .replace('\n', ' ').trim().take(60)
            .ifBlank { ch.title }
        viewModelScope.launch {
            repo.addBookmark(bookId, st.chapterIdx, offset, excerpt)
            showTransient("已添加书签")
        }
    }

    fun showTransient(message: String) {
        _ui.update { it.copy(transient = message) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _ui.update { s -> if (s.transient == message) s.copy(transient = null) else s }
        }
    }

    fun deleteBookmark(b: BookmarkEntity) {
        viewModelScope.launch { repo.deleteBookmark(b) }
    }

    // ---- 阅读时长 ----

    fun onReaderVisible() {
        readStartAt = System.currentTimeMillis()
    }

    fun flushReadTime() {
        if (readStartAt <= 0L) return
        val delta = System.currentTimeMillis() - readStartAt
        readStartAt = System.currentTimeMillis()
        if (delta > 0L) {
            viewModelScope.launch { repo.addReadTime(bookId, delta) }
        }
    }

    fun onReaderHidden() {
        if (readStartAt <= 0L) return
        val delta = System.currentTimeMillis() - readStartAt
        readStartAt = 0L
        if (delta > 0L) {
            viewModelScope.launch { repo.addReadTime(bookId, delta) }
        }
    }

    override fun onCleared() {
        val delta = if (readStartAt > 0L) System.currentTimeMillis() - readStartAt else 0L
        readStartAt = 0L
        if (delta > 0L) {
            // onCleared 时 viewModelScope 已取消，用 GlobalScope 兜底写入最后一段时长
            kotlinx.coroutines.GlobalScope.launch { repo.addReadTime(bookId, delta) }
        }
        super.onCleared()
    }
}
