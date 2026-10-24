package com.moyue.reader.reader

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.TextPaint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.moyue.reader.R
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.db.BookImageEntity
import com.moyue.reader.data.db.BookmarkEntity
import com.moyue.reader.data.db.ChapterEntity
import com.moyue.reader.data.db.HighlightEntity
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
import java.util.Locale

class ReaderViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    private val bookId: Long = savedState.get<String>("bookId")?.toLongOrNull() ?: -1L
    private val repo: BookRepository = Graph.bookRepository
    private val settings: SettingsRepository = Graph.settingsRepository
    private val ctx get() = getApplication<Application>()

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
        val highlights: List<HighlightEntity> = emptyList(),
        /** 当前章内联图片（EPUB） */
        val images: List<BookImageEntity> = emptyList(),
        val searchResults: List<BookRepository.SearchHit>? = null,
        val searching: Boolean = false,
        /** 短暂浮层提示（加书签等操作反馈），2 秒自动消失 */
        val transient: String? = null,
        /** TTS 朗读中 */
        val ttsRunning: Boolean = false,
        /** 自动滚动中（滚动模式） */
        val autoScroll: Boolean = false,
        /** 听书定时：到点时间戳（null=未启用） */
        val ttsSleepDeadline: Long? = null,
        /** 听书定时：本章读完即停 */
        val ttsStopAfterChapter: Boolean = false
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var spec: LayoutSpec? = null

    /** 待定位的绝对字符偏移（恢复进度/跳转/重排后使用） */
    private var pendingOffset: Int? = null

    private var layoutJob: Job? = null
    private var chapterJob: Job? = null
    private var chaptersLoaded = false

    /** 分页结果 LRU 缓存（章节 → 排版参数 + 页表），翻章往返免重算 */
    private val pageCache = object : LinkedHashMap<Int, Pair<LayoutSpec, List<PaginationEngine.Page>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Pair<LayoutSpec, List<PaginationEngine.Page>>>) = size > 8
    }

    /** 进度写库防抖：滚动模式下 onPageSettled 高频触发，停顿后再落库 */
    private var persistJob: Job? = null

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
            repo.observeHighlights(bookId).collect { hs ->
                _ui.update { it.copy(highlights = hs) }
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
            val images = repo.imagesForChapter(bookId, safe)
            _ui.update { it.copy(chapterText = text, images = images) }
            val s = spec
            if (s != null) paginate(s) else _ui.update { it.copy(loading = false) }
        }
    }

    fun applySpec(newSpec: LayoutSpec) {
        spec = newSpec
        if (_ui.value.chapters.isNotEmpty()) paginate(newSpec)
    }

    /**
     * 分页用整章 spanned：与渲染共用 PageSpans.apply，
     * ImageSpan 边界一致 → 含图片行的行高测量与渲染完全相同。
     */
    private fun spannedFor(s: LayoutSpec) = PageSpans.apply(
        _ui.value.chapterText, 0,
        _ui.value.highlights, null, _ui.value.images
    ) { img ->
        PageImageDrawable(
            java.io.File(repo.imageDir(bookId), img.fileName),
            img.srcW, img.srcH,
            maxW = s.pageWidth, maxH = (s.pageHeight * 0.7f).toInt()
        )
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
            val cached = synchronized(pageCache) { pageCache[st.chapterIdx] }
                ?.takeIf { it.first == s }?.second
            val pages = cached ?: engine.paginate(spannedFor(s)).also {
                synchronized(pageCache) { pageCache[st.chapterIdx] = s to it }
            }
            // 偏移是全书绝对值，页表是章节内相对值：必须换算后再定位
            val chapterStart = st.chapters.getOrNull(st.chapterIdx)?.charStart ?: 0
            val absOffset = pendingOffset ?: currentCharOffset()
            pendingOffset = null
            val relOffset = (absOffset - chapterStart).coerceIn(0, text.length)
            val idx = PaginationEngine.indexOfOffset(pages, relOffset)
            _ui.update { it.copy(pages = pages, pageIdx = idx, loading = false) }
            // 章节切换完成且 TTS 在等待 → 继续朗读
            if (ttsPending && _ui.value.ttsRunning) {
                ttsPending = false
                speakCurrentPage()
            }
        }
    }

    fun onPageSettled(page: Int) {
        val st = _ui.value
        if (st.pages.isEmpty()) return
        val safe = page.coerceIn(0, st.pages.size - 1)
        // 页码未变不写库（滚动模式每帧都会上报当前页）
        if (safe == st.pageIdx) return
        _ui.update { it.copy(pageIdx = safe) }
        schedulePersist()
    }

    /** 停顿 400ms 后写一次进度；高频翻页/滚动时自动合并 */
    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            persistProgress()
        }
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

    /** 立即落库当前进度（离开页面/被销毁时兜底） */
    private fun flushProgressNow(scope: kotlinx.coroutines.CoroutineScope) {
        val st = _ui.value
        val book = st.book ?: return
        val ch = st.chapters.getOrNull(st.chapterIdx) ?: return
        val page = st.pages.getOrNull(st.pageIdx) ?: return
        scope.launch { repo.saveProgress(book.id, st.chapterIdx, ch.charStart + page.start) }
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
            showTransient(ctx.getString(R.string.tts_page_not_ready))
            return
        }
        val offset = ch.charStart + page.start
        // 同一页去重：避免误以为没点上而连点产生重复书签
        if (st.bookmarks.any { it.chapterIdx == st.chapterIdx && it.charOffset == offset }) {
            showTransient(ctx.getString(R.string.bookmark_dup))
            return
        }
        val from = page.start.coerceIn(0, st.chapterText.length)
        val to = page.end.coerceIn(0, st.chapterText.length)
        val excerpt = st.chapterText.substring(from, to)
            .replace('\n', ' ')
            .replace(PageSpans.IMG_CHAR, ' ')
            .trim().take(60)
            .ifBlank { ch.title }
        viewModelScope.launch {
            repo.addBookmark(bookId, st.chapterIdx, offset, excerpt)
            showTransient(ctx.getString(R.string.bookmark_added))
        }
    }

    // ---- 划线高亮 ----

    /** 新增高亮（去重：同章节同区间） */
    fun addHighlight(start: Int, end: Int, color: Int, note: String = "") {
        val st = _ui.value
        if (end <= start) return
        if (st.highmarksDupe(start, end)) return
        val excerpt = st.chapterText.substring(start.coerceIn(0, st.chapterText.length), end.coerceIn(0, st.chapterText.length))
            .replace(PageSpans.IMG_CHAR, ' ')
            .replace('\n', ' ').trim()
        if (excerpt.isBlank()) return
        viewModelScope.launch {
            repo.addHighlight(bookId, st.chapterIdx, start, end, excerpt.take(300), note, color)
        }
    }

    private fun UiState.highmarksDupe(start: Int, end: Int): Boolean =
        highlights.any { it.chapterIdx == chapterIdx && it.start == start && it.end == end }

    fun updateHighlight(h: HighlightEntity, note: String, color: Int) {
        viewModelScope.launch { repo.updateHighlight(h.copy(note = note, color = color)) }
    }

    fun deleteHighlight(h: HighlightEntity) {
        viewModelScope.launch { repo.deleteHighlight(h) }
    }

    fun showTransient(message: String) {
        _ui.update { it.copy(transient = message) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _ui.update { s -> if (s.transient == message) s.copy(transient = null) else s }
        }
    }

    // ---- TTS 听书 ----

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsPending = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioFocusRequested = false

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) {
            mainHandler.post { advanceTts() }
        }
        override fun onError(utteranceId: String?) {
            mainHandler.post { stopTts(ctx.getString(R.string.tts_error)) }
        }
    }

    fun toggleTts() {
        if (_ui.value.ttsRunning) {
            stopTts()
            return
        }
        if (tts == null || !ttsReady) {
            // 引擎未初始化/上次失败：重新创建（部分 ROM 用 Application context 偶发初始化失败）
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
            showTransient(ctx.getString(R.string.tts_initing))
            val app = getApplication<Application>()
            tts = TextToSpeech(app) { status ->
                mainHandler.post {
                    if (status == TextToSpeech.SUCCESS) {
                        ttsReady = true
                        pickTtsLanguage()
                        tts?.setOnUtteranceProgressListener(utteranceListener)
                        startTts()
                    } else {
                        // 无可用引擎：引导去系统 TTS 设置
                        showTransient(ctx.getString(R.string.tts_no_engine))
                        openTtsSettings()
                    }
                }
            }
        } else {
            startTts()
        }
    }

    /** 中文不可用时按可用语言回退，避免引擎装了却报"不可用" */
    private fun pickTtsLanguage() {
        val engine = tts ?: return
        val candidates = listOf(
            Locale.SIMPLIFIED_CHINESE, Locale.CHINA, Locale.CHINESE, Locale.TAIWAN, Locale.getDefault()
        )
        for (lang in candidates) {
            if (engine.isLanguageAvailable(lang) != TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = lang
                return
            }
        }
        runCatching {
            engine.availableLanguages.firstOrNull()?.let { engine.language = it }
        }
    }

    /** 打开系统「文字转语音」设置页（各 ROM 动作名不同，逐个尝试） */
    fun openTtsSettings() {
        val app = getApplication<Application>()
        val intents = listOf(
            android.content.Intent("com.android.settings.TTS_SETTINGS"),
            android.content.Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS),
            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (i in intents) {
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { app.startActivity(i) }.isSuccess) return
        }
        showTransient(ctx.getString(R.string.tts_settings_hint))
    }

    private fun startTts() {
        if (!ttsReady) {
            showTransient(ctx.getString(R.string.tts_not_ready))
            toggleTts()
            return
        }
        tts?.setSpeechRate(_ui.value.prefs.ttsRate)
        requestAudioFocus()
        _ui.update { it.copy(ttsRunning = true) }
        showTransient(ctx.getString(R.string.tts_started))
        speakCurrentPage()
    }

    private fun speakCurrentPage() {
        val st = _ui.value
        if (!st.ttsRunning) return
        val page = st.pages.getOrNull(st.pageIdx)
        if (page == null) { // 章节未就绪，等待分页完成
            ttsPending = true
            return
        }
        val text = st.chapterText
            .substring(page.start.coerceIn(0, st.chapterText.length), page.end.coerceIn(0, st.chapterText.length))
            .replace(PageSpans.IMG_CHAR, ' ')
            .trim()
        if (text.isEmpty()) {
            advanceTts()
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "moyue-page")
    }

    /** 读完一页 → 下一页 / 下一章 / 结束 */
    private fun advanceTts() {
        val st = _ui.value
        if (!st.ttsRunning) return
        if (st.pageIdx < st.pages.size - 1) {
            _ui.update { it.copy(pageIdx = st.pageIdx + 1) } // UI 同步滚动 Pager
            speakCurrentPage()
        } else if (st.ttsStopAfterChapter) {
            stopTts(ctx.getString(R.string.tts_timer_chapter_done))
        } else if (st.chapterIdx < st.chapters.size - 1) {
            ttsPending = true
            nextChapter()
        } else {
            stopTts(ctx.getString(R.string.tts_finished))
        }
    }

    private fun stopTts(message: String? = null) {
        tts?.stop()
        abandonAudioFocus()
        ttsSleepJob?.cancel()
        ttsSleepJob = null
        _ui.update { it.copy(ttsRunning = false, ttsSleepDeadline = null, ttsStopAfterChapter = false) }
        ttsPending = false
        if (message != null) showTransient(message)
    }

    // ---- 听书定时 ----

    private var ttsSleepJob: Job? = null

    /** 分钟后停止朗读；null 取消定时 */
    fun setTtsSleep(minutes: Int?) {
        ttsSleepJob?.cancel()
        ttsSleepJob = null
        _ui.update { it.copy(ttsStopAfterChapter = false) }
        if (minutes == null) {
            _ui.update { it.copy(ttsSleepDeadline = null) }
            return
        }
        val deadline = System.currentTimeMillis() + minutes * 60_000L
        _ui.update { it.copy(ttsSleepDeadline = deadline) }
        ttsSleepJob = viewModelScope.launch {
            kotlinx.coroutines.delay(minutes * 60_000L)
            stopTts(ctx.getString(R.string.tts_timer_done))
        }
    }

    /** 本章读完即停 */
    fun setTtsStopAfterChapter(on: Boolean) {
        ttsSleepJob?.cancel()
        ttsSleepJob = null
        _ui.update { it.copy(ttsStopAfterChapter = on, ttsSleepDeadline = null) }
    }

    private fun requestAudioFocus() {
        if (audioFocusRequested) return
        val am = getApplication<Application>()
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.requestAudioFocus(
            null, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
        audioFocusRequested = true
    }

    private fun abandonAudioFocus() {
        if (!audioFocusRequested) return
        val am = getApplication<Application>()
            .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.abandonAudioFocus(null)
        audioFocusRequested = false
    }

    // ---- 自动滚动（滚动模式） ----

    fun setAutoScroll(on: Boolean) {
        _ui.update { if (it.autoScroll == on) it else it.copy(autoScroll = on) }
    }

    fun toggleAutoScroll() = setAutoScroll(!_ui.value.autoScroll)

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
        persistJob?.cancel()
        persistProgress()
        if (readStartAt <= 0L) return
        val delta = System.currentTimeMillis() - readStartAt
        readStartAt = 0L
        if (delta > 0L) {
            viewModelScope.launch { repo.addReadTime(bookId, delta) }
        }
    }

    override fun onCleared() {
        persistJob?.cancel()
        flushProgressNow(kotlinx.coroutines.GlobalScope)
        val delta = if (readStartAt > 0L) System.currentTimeMillis() - readStartAt else 0L
        readStartAt = 0L
        if (delta > 0L) {
            // onCleared 时 viewModelScope 已取消，用 GlobalScope 兜底写入最后一段时长
            kotlinx.coroutines.GlobalScope.launch { repo.addReadTime(bookId, delta) }
        }
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        abandonAudioFocus()
        super.onCleared()
    }
}
