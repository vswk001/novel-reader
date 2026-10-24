package com.moyue.reader.reader

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moyue.reader.R
import com.moyue.reader.data.repo.ClickZone
import com.moyue.reader.data.repo.PageAnim
import com.moyue.reader.data.repo.ReadMode
import com.moyue.reader.data.repo.ReaderThemeKind
import com.moyue.reader.di.Graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ReaderScreen(onBack: () -> Unit) {
    val vm: ReaderViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val prefs = ui.prefs
    val isDarkSystem = isSystemInDarkTheme()
    val palette = remember(prefs.theme, prefs.themeFollowSystem, isDarkSystem) {
        if (prefs.themeFollowSystem && isDarkSystem) ReaderThemes.of(ReaderThemeKind.BLACK)
        else ReaderThemes.of(prefs.theme)
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    // ---------- 排版参数 ----------
    val density = LocalDensity.current
    var textAreaSize by remember { mutableStateOf(IntSize.Zero) }
    val textSizePx = with(density) { prefs.fontSp.sp.toPx() }
    val padH = prefs.paddingH.dp
    val padV = prefs.paddingV.dp
    val footerReserve = 26.dp

    val spec = remember(textAreaSize, textSizePx, prefs.lineSpacing, prefs.serif) {
        if (textAreaSize != IntSize.Zero) {
            ReaderViewModel.LayoutSpec(
                pageWidth = textAreaSize.width,
                pageHeight = textAreaSize.height,
                textSizePx = textSizePx,
                lineSpacing = prefs.lineSpacing,
                serif = prefs.serif
            )
        } else null
    }
    LaunchedEffect(spec) {
        spec?.let { vm.applySpec(it) }
    }

    val scrollState = rememberLazyListState()

    // ---------- 文本选择 / 划线高亮 ----------
    val pageLayouts = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<Int, StaticLayout>() }
    var selStart by remember { mutableStateOf<Int?>(null) }
    var selEnd by remember { mutableStateOf<Int?>(null) }
    var editHl by remember { mutableStateOf<com.moyue.reader.data.db.HighlightEntity?>(null) }
    var newNoteSel by remember { mutableStateOf<IntRange?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val imageDir = remember(ui.book?.id) {
        ui.book?.let { Graph.bookRepository.imageDir(it.id) }
    }
    val selection: IntRange? =
        if (selStart != null && selEnd != null && selEnd!! >= selStart!!) selStart!!..selEnd!! else null

    fun clearSelection() {
        selStart = null; selEnd = null
    }
    fun setSelectionPoint(isStartHandle: Boolean, absOffset: Int) {
        val len = ui.chapterText.length
        val off = absOffset.coerceIn(0, len)
        if (isStartHandle) {
            selStart = off
            if (selEnd != null && selEnd!! < off) selEnd = off
        } else {
            selEnd = off
            if (selStart != null && selStart!! > off) selStart = off
        }
    }

    /** 根坐标 → (页布局, 页内坐标, 页)，不可命中返回 null */
    fun hitTest(pos: Offset): Triple<StaticLayout, Offset, PaginationEngine.Page>? {
        val idx = if (prefs.readMode == ReadMode.SCROLL) {
            val header = if (ui.chapterIdx > 0) 1 else 0
            scrollState.firstVisibleItemIndex - header
        } else ui.pageIdx
        val page = ui.pages.getOrNull(idx) ?: return null
        val layout = pageLayouts[page.start] ?: return null
        val x = pos.x - with(density) { padH.toPx() }
        val y = pos.y - with(density) { padV.toPx() }
        if (x < 0f || y < 0f) return null
        return Triple(layout, Offset(x, y), page)
    }

    fun offsetAt(pos: Offset): Int? {
        val (layout, local, page) = hitTest(pos) ?: return null
        val line = layout.getLineForVertical(local.y.toInt())
        return layout.getOffsetForHorizontal(line, local.x)
            .coerceIn(0, (page.end - page.start).coerceAtLeast(0)) + page.start
    }

    /** 长按 → 选中词 */
    fun selectWordAt(pos: Offset) {
        val off = offsetAt(pos) ?: return
        val wr = PageSpans.wordAt(ui.chapterText, off)
        selStart = wr.first
        selEnd = wr.last
    }

    /** 点击命中已有高亮 */
    fun highlightAt(pos: Offset): com.moyue.reader.data.db.HighlightEntity? {
        val off = offsetAt(pos) ?: return null
        return ui.highlights.firstOrNull { it.chapterIdx == ui.chapterIdx && off >= it.start && off < it.end }
    }

    // 翻页/切章/菜单打开时清除选区
    LaunchedEffect(ui.pageIdx, ui.chapterIdx, ui.menuVisible) {
        if (selStart != null) clearSelection()
    }

    // ---------- 亮度手势：左半屏上下拖动调节（松手持久化，拖动中实时生效） ----------
    var brightnessDragActive by remember { mutableStateOf(false) }
    var brightnessHud by remember { mutableStateOf<Float?>(null) }
    var brightnessStartY by remember { mutableStateOf(0f) }
    var brightnessStartVal by remember { mutableStateOf(0.5f) }

    // ---------- 翻页（PAGE 模式用 Pager） ----------
    val pagerState = rememberPagerState(initialPage = 0) { ui.pages.size }

    LaunchedEffect(pagerState, prefs.readMode) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (prefs.readMode == ReadMode.PAGE) vm.onPageSettled(page)
        }
    }
    LaunchedEffect(ui.pages, ui.pageIdx) {
        if (prefs.readMode == ReadMode.PAGE && ui.pages.isNotEmpty() &&
            pagerState.settledPage != ui.pageIdx &&
            !pagerState.isScrollInProgress
        ) {
            pagerState.scrollToPage(ui.pageIdx)
        }
    }

    val turnPage: (Int) -> Unit = { dir ->
        scope.launch {
            val target = pagerState.currentPage + dir
            if (target < 0) {
                vm.prevChapter()
            } else if (target >= ui.pages.size) {
                vm.nextChapter()
            } else {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    /** 音量键/点击 共用的翻页入口：PAGE 模式翻页，SCROLL 模式按页滚动 */
    fun pageAction(dir: Int) {
        if (prefs.readMode == ReadMode.SCROLL) {
            scope.launch {
                val pageH = textAreaSize.height.toFloat()
                if (pageH > 0f) {
                    scrollState.animateScrollBy(dir * pageH)
                }
            }
        } else {
            turnPage(dir)
        }
    }

    // ---------- 窗口能力：常亮 / 亮度 ----------
    DisposableEffect(prefs.keepScreenOn) {
        val window = activity?.window
        if (prefs.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(prefs.brightnessFollowSystem, prefs.brightness) {
        val window = activity?.window
        val lp = window?.attributes
        if (lp != null) {
            lp.screenBrightness = if (prefs.brightnessFollowSystem) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                prefs.brightness.coerceIn(0.02f, 1f)
            }
            window.attributes = lp
        }
        onDispose {
            if (lp != null) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window?.attributes = lp
            }
        }
    }

    // ---------- 沉浸式：阅读期间（含菜单打开）始终保持全屏，避免系统栏弹出遮挡菜单 ----------
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ---------- 阅读时长统计 ----------
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onReaderVisible()
                Lifecycle.Event.ON_PAUSE -> vm.onReaderHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.onReaderHidden()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            vm.flushReadTime()
        }
    }

    // ---------- 音量键翻页 ----------
    DisposableEffect(prefs.volumeKeyTurn, prefs.readMode) {
        if (prefs.volumeKeyTurn) {
            Graph.volumeKeyHandler = { keyCode ->
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        pageAction(-1)
                        true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        pageAction(1)
                        true
                    }
                    else -> false
                }
            }
        } else {
            Graph.volumeKeyHandler = null
        }
        onDispose {
            Graph.volumeKeyHandler = null
        }
    }

    // ---------- 主体 ----------
    // 点击检测挂在根节点：拖动被上层滚动/翻页消费即视为非点击，
    // 纯点击才触发菜单/翻页——滚动模式的手指滑动因此不被拦截
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            // 自动滚动时：正文区任意触摸立即停止（菜单打开时除外——先收菜单）
            .pointerInput(ui.autoScroll, ui.menuVisible) {
                if (ui.autoScroll && !ui.menuVisible) {
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        vm.setAutoScroll(false)
                    }
                }
            }
            // 亮度手势（菜单关闭时左半屏竖向拖动；翻页模式下生效）
            .pointerInput(prefs.brightnessFollowSystem, ui.menuVisible) {
                detectVerticalDragGestures(
                    onDragStart = { pos ->
                        if (!ui.menuVisible && pos.x < size.width * 0.5f) {
                            brightnessDragActive = true
                            brightnessStartY = pos.y
                            brightnessStartVal =
                                if (prefs.brightnessFollowSystem) 0.5f else prefs.brightness
                            brightnessHud = brightnessStartVal
                        }
                    },
                    onVerticalDrag = { change, _ ->
                        if (brightnessDragActive) {
                            val v = (brightnessStartVal +
                                (brightnessStartY - change.position.y) / (size.height * 0.7f))
                                .coerceIn(0.02f, 1f)
                            brightnessHud = v
                            activity?.window?.let { w ->
                                w.attributes = w.attributes.also { it.screenBrightness = v }
                            }
                        }
                    },
                    onDragEnd = {
                        if (brightnessDragActive) {
                            brightnessDragActive = false
                            brightnessHud?.let { v ->
                                vm.updatePrefs {
                                    it.copy(brightness = v, brightnessFollowSystem = false)
                                }
                            }
                            brightnessHud = null
                        }
                    },
                    onDragCancel = {
                        brightnessDragActive = false
                        brightnessHud = null
                    }
                )
            }
            .pointerInput(prefs.tapZone, prefs.readMode, ui.menuVisible, ui.autoScroll, ui.chapterIdx) {
                detectTapGestures(
                    onLongPress = { pos ->
                        if (!ui.menuVisible && !ui.autoScroll) selectWordAt(pos)
                    },
                    onTap = { offset ->
                        // 优先级：收菜单 > 停滚动 > 命中高亮 > 清选区 > 区域翻页/呼出菜单
                        if (ui.menuVisible) {
                            vm.setMenuVisible(false)
                            return@detectTapGestures
                        }
                        if (ui.autoScroll) {
                            vm.setAutoScroll(false)
                            return@detectTapGestures
                        }
                        highlightAt(offset)?.let {
                            editHl = it
                            return@detectTapGestures
                        }
                        if (selStart != null) {
                            clearSelection()
                            return@detectTapGestures
                        }
                        if (prefs.readMode == ReadMode.SCROLL || prefs.tapZone == ClickZone.OFF) {
                            vm.toggleMenu()
                            return@detectTapGestures
                        }
                    val w = size.width
                    val h = size.height
                    when (prefs.tapZone) {
                        ClickZone.TOP_BOTTOM -> when {
                            offset.y < h * 0.3f -> pageAction(-1)
                            offset.y > h * 0.7f -> pageAction(1)
                            else -> vm.toggleMenu()
                        }
                        else -> when {
                            offset.x < w * 0.28f -> pageAction(-1)
                            offset.x > w * 0.72f -> pageAction(1)
                            else -> vm.toggleMenu()
                        }
                    }
                })
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = padH, vertical = padV)
                .padding(bottom = footerReserve)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { textAreaSize = it }
            ) {
                when {
                    ui.pages.isEmpty() && ui.book == null -> Text(
                        stringResource(R.string.txt_book_missing),
                        color = palette.secondary,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // 含正常加载与偶现竞态兜底：pages 空 + 书存在 → 加载态并自动重排自愈
                    ui.pages.isEmpty() -> Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = palette.secondary
                        )
                        LaunchedEffect(ui.chapterText, ui.chapterIdx, ui.loading) {
                            delay(800)
                            if (ui.pages.isEmpty()) vm.repaginate()
                        }
                    }

                    prefs.readMode == ReadMode.SCROLL -> ScrollReaderContent(
                        ui = ui,
                        vm = vm,
                        scrollState = scrollState,
                        pageHeightPx = textAreaSize.height,
                        autoSpeedPx = with(density) { prefs.autoScrollSpeed.dp.toPx() },
                        textSizePx = textSizePx,
                        serif = prefs.serif,
                        lineSpacing = prefs.lineSpacing,
                        widthPx = textAreaSize.width,
                        textColor = palette.text,
                        accentColor = palette.secondary,
                        hasPrev = ui.chapterIdx > 0,
                        hasNext = ui.chapterIdx < ui.chapters.size - 1,
                        highlights = ui.highlights,
                        images = ui.images,
                        imageDir = imageDir,
                        selection = selection,
                        onLayoutBuilt = { k, l ->
                            if (l != null) pageLayouts[k] = l else pageLayouts.remove(k)
                        },
                        onSelectionDrag = { isStart, off -> setSelectionPoint(isStart, off) }
                    )

                    else -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1
                    ) { pageIdx ->
                        val page = ui.pages.getOrNull(pageIdx)
                        if (page == null) {
                            Box(Modifier.fillMaxSize())
                        } else {
                            PageCanvas(
                                text = ui.chapterText,
                                page = page,
                                textSizePx = textSizePx,
                                serif = prefs.serif,
                                lineSpacing = prefs.lineSpacing,
                                widthPx = textAreaSize.width,
                                textColor = palette.text,
                                pageHeightPx = textAreaSize.height,
                                highlights = ui.highlights,
                                images = ui.images,
                                imageDir = imageDir,
                                selection = selection,
                                onLayoutBuilt = { k, l ->
                                    if (l != null) pageLayouts[k] = l else pageLayouts.remove(k)
                                },
                                onSelectionDrag = { isStart, off -> setSelectionPoint(isStart, off) },
                                pageAnim = prefs.pageAnim,
                                pagerState = pagerState,
                                pageIdx = pageIdx
                            )
                        }
                    }
                }
            }
        }

        // 页脚：章节 · 页码 · 时间 · 电量
        ReaderFooter(
            palette = palette,
            bookTitle = ui.book?.title.orEmpty(),
            chapterTitle = ui.chapters.getOrNull(ui.chapterIdx)?.title.orEmpty(),
            pageIdx = ui.pageIdx,
            pageCount = ui.pages.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = padH + 4.dp, vertical = 4.dp)
        )

        // 亮度手势 HUD
        brightnessHud?.let { v ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.menuBg.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("☀", fontSize = 13.sp, color = palette.menuText)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(110.dp)
                            .background(palette.divider, RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(v)
                                .background(palette.menuText, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${(v * 100).toInt()}%", fontSize = 10.sp, color = palette.menuText)
                }
            }
        }

        // 操作反馈浮层（加书签等）
        ui.transient?.let { msg ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = palette.menuBg.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    msg,
                    color = palette.menuText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }

        // 菜单
        ReaderMenuOverlay(
            visible = ui.menuVisible,
            ui = ui,
            palette = palette,
            vm = vm,
            onBack = onBack
        )

        // 划线工具条（选区激活时）
        selection?.let { sel ->
            val len = ui.chapterText.length
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = palette.menuBg.copy(alpha = 0.98f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padV + 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        stringResource(R.string.hl_copy),
                        color = palette.menuText,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                val s = sel.first.coerceIn(0, len)
                                val e = (sel.last + 1).coerceIn(0, len)
                                if (e > s) clipboard.setText(
                                    androidx.compose.ui.text.AnnotatedString(ui.chapterText.substring(s, e))
                                )
                                clearSelection()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    PageSpans.HIGHLIGHT_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(20.dp)
                                .background(
                                    androidx.compose.ui.graphics.Color(c),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                                .clickable {
                                    vm.addHighlight(sel.first, sel.last + 1, i)
                                    clearSelection()
                                }
                        )
                    }
                    Text(
                        stringResource(R.string.hl_note),
                        color = palette.menuText,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                newNoteSel = sel
                                clearSelection()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = palette.secondary,
                        modifier = Modifier
                            .clickable { clearSelection() }
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
            }
        }

        // 高亮编辑（点击已有划线）
        editHl?.let { h ->
            HighlightNoteDialog(
                excerpt = h.text,
                initialNote = h.note,
                initialColor = h.color,
                onSave = { n, c -> vm.updateHighlight(h, n, c); editHl = null },
                onDelete = { vm.deleteHighlight(h); editHl = null },
                onDismiss = { editHl = null }
            )
        }
        // 新建划线笔记（工具条「笔记」）
        newNoteSel?.let { sel ->
            val len = ui.chapterText.length
            HighlightNoteDialog(
                excerpt = ui.chapterText.substring(
                    sel.first.coerceIn(0, len),
                    (sel.last + 1).coerceIn(0, len)
                ),
                initialNote = "",
                initialColor = 0,
                onSave = { n, c ->
                    vm.addHighlight(sel.first, sel.last + 1, c, n)
                    newNoteSel = null
                },
                onDismiss = { newNoteSel = null }
            )
        }

        // 自动滚动专用悬浮控制条（最高层，滚动期间常驻：停止 / 减速 / 加速）
        if (ui.autoScroll) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = palette.menuBg.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.PauseCircle,
                        contentDescription = stringResource(R.string.cd_stop_autoscroll),
                        tint = palette.menuText,
                        modifier = Modifier
                            .clickable { vm.setAutoScroll(false) }
                            .padding(10.dp)
                            .size(24.dp)
                    )
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.cd_slower),
                        tint = palette.secondary,
                        modifier = Modifier
                            .clickable {
                                vm.updatePrefs {
                                    it.copy(autoScrollSpeed = (it.autoScrollSpeed - 40).coerceAtLeast(40))
                                }
                            }
                            .padding(10.dp)
                            .size(20.dp)
                    )
                    Text(
                        "${prefs.autoScrollSpeed}",
                        color = palette.menuText,
                        fontSize = 13.sp
                    )
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_faster),
                        tint = palette.secondary,
                        modifier = Modifier
                            .clickable {
                                vm.updatePrefs {
                                    it.copy(autoScrollSpeed = (it.autoScrollSpeed + 40).coerceAtMost(400))
                                }
                            }
                            .padding(10.dp)
                            .size(20.dp)
                    )
                }
            }
        }
    }
}

/** 上下滚动阅读模式：LazyColumn 懒构建页面布局（大章节不再全量排版），章节首尾提供上/下一章入口 */
@Composable
private fun ScrollReaderContent(
    ui: ReaderViewModel.UiState,
    vm: ReaderViewModel,
    scrollState: LazyListState,
    pageHeightPx: Int,
    autoSpeedPx: Float,
    textSizePx: Float,
    serif: Boolean,
    lineSpacing: Float,
    widthPx: Int,
    textColor: Color,
    accentColor: Color,
    hasPrev: Boolean,
    hasNext: Boolean,
    highlights: List<com.moyue.reader.data.db.HighlightEntity> = emptyList(),
    images: List<com.moyue.reader.data.db.BookImageEntity> = emptyList(),
    imageDir: java.io.File? = null,
    selection: IntRange? = null,
    onLayoutBuilt: ((Int, StaticLayout?) -> Unit)? = null,
    onSelectionDrag: ((Boolean, Int) -> Unit)? = null
) {
    val headerCount = if (hasPrev) 1 else 0

    // 自动滚动：按帧推进，触底自动进入下一章；任意触摸停止（见 ReaderScreen 根手势）
    LaunchedEffect(ui.autoScroll, ui.pages, pageHeightPx) {
        if (!ui.autoScroll || pageHeightPx <= 0) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (lastNs != 0L) {
                val dt = (now - lastNs) / 1_000_000_000f
                scrollState.scrollBy(autoSpeedPx * dt)
                if (!scrollState.canScrollForward) {
                    if (hasNext) vm.nextChapter() else vm.setAutoScroll(false)
                    break
                }
            }
            lastNs = now
        }
    }

    // 进度跟踪：当前页 = 视口顶部可见的页（headerCount 变化需重启，否则映射偏移）
    LaunchedEffect(scrollState, ui.pages.size, headerCount) {
        snapshotFlow { scrollState.firstVisibleItemIndex }.collect { first ->
            if (ui.pages.isNotEmpty()) {
                vm.onPageSettled((first - headerCount).coerceIn(0, ui.pages.size - 1))
            }
        }
    }
    // 进度恢复：分页完成或跳转后滚动到目标页
    LaunchedEffect(ui.pages, ui.pageIdx, pageHeightPx) {
        if (ui.pages.isNotEmpty() && pageHeightPx > 0 && !scrollState.isScrollInProgress) {
            val target = headerCount + ui.pageIdx
            if (scrollState.firstVisibleItemIndex != target) {
                scrollState.scrollToItem(target)
            }
        }
    }

    val density = LocalDensity.current
    val pageHeightDp = with(density) { pageHeightPx.toDp() }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize()
    ) {
        if (hasPrev) {
            item(key = "prev", contentType = "chapterBtn") {
                ScrollChapterButton(stringResource(R.string.btn_prev_chapter), accentColor) { vm.prevChapter() }
            }
        }
        items(ui.pages.size, key = { it }, contentType = { "page" }) { idx ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(pageHeightDp)
            ) {
                StaticPage(
                    text = ui.chapterText,
                    page = ui.pages[idx],
                    textSizePx = textSizePx,
                    serif = serif,
                    lineSpacing = lineSpacing,
                    widthPx = widthPx,
                    textColor = textColor,
                    pageHeightPx = pageHeightPx,
                    highlights = highlights,
                    images = images,
                    imageDir = imageDir,
                    selection = selection,
                    onLayoutBuilt = onLayoutBuilt,
                    onSelectionDrag = onSelectionDrag,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        item(key = "tail", contentType = "chapterBtn") {
            if (hasNext) {
                ScrollChapterButton(stringResource(R.string.btn_next_chapter), accentColor) { vm.nextChapter() }
            } else {
                Text(
                    stringResource(R.string.txt_the_end),
                    color = accentColor,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ScrollChapterButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 13.sp)
    }
}

/** 与分页完全相同的 StaticLayout 参数渲染一页（无翻页动画，两种模式共用）；支持高亮/图片/选区 */
@Composable
fun StaticPage(
    text: String,
    page: PaginationEngine.Page,
    textSizePx: Float,
    serif: Boolean,
    lineSpacing: Float,
    widthPx: Int,
    textColor: Color,
    pageHeightPx: Int = 0,
    highlights: List<com.moyue.reader.data.db.HighlightEntity> = emptyList(),
    images: List<com.moyue.reader.data.db.BookImageEntity> = emptyList(),
    imageDir: java.io.File? = null,
    selection: IntRange? = null,
    onLayoutBuilt: ((Int, StaticLayout?) -> Unit)? = null,
    onSelectionDrag: ((isStartHandle: Boolean, absOffset: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val paint = remember(textSizePx, serif, textColor) {
        TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = if (serif) android.graphics.Typeface.SERIF else android.graphics.Typeface.DEFAULT
            color = android.graphics.Color.argb(
                (textColor.alpha * 255).toInt(),
                (textColor.red * 255).toInt(),
                (textColor.green * 255).toInt(),
                (textColor.blue * 255).toInt()
            )
        }
    }
    val from = page.start.coerceIn(0, text.length)
    val to = page.end.coerceIn(from, text.length)
    val spanned = remember(text, page, paint, widthPx, lineSpacing, highlights, images, selection, pageHeightPx) {
        PageSpans.apply(
            text.substring(from, to), from,
            highlights, selection, images
        ) { img ->
            if (imageDir == null || pageHeightPx <= 0) null
            else PageImageDrawable(
                java.io.File(imageDir, img.fileName),
                img.srcW, img.srcH,
                maxW = widthPx, maxH = (pageHeightPx * 0.7f).toInt()
            )
        }
    }
    val layout = remember(spanned, paint, widthPx, lineSpacing) {
        StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
            .build()
    }
    // 向上注册本页布局（长按命中测试/工具条定位用）
    onLayoutBuilt?.let { cb ->
        androidx.compose.runtime.DisposableEffect(page.start) {
            cb(page.start, layout)
            onDispose { cb(page.start, null) }
        }
    }

    // 选区手柄位置（本页局部坐标 px）
    val selLocal = selection?.let { sel ->
        val s = sel.first - from
        val e = sel.last - from
        if (e >= 0 && s < to - from && s <= e) s..e else null
    }
    val handleColor = MaterialTheme.colorScheme.primary
    var draggingHandle by remember(page.start) { mutableStateOf(-1) }

    Box(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            layout.draw(drawContext.canvas.nativeCanvas)
        }
        if (selLocal != null && onSelectionDrag != null) {
            val startHandle = handlePoint(layout, selLocal.first)
            val endHandle = handlePoint(layout, selLocal.last + 1)
            @Composable
            fun handle(pos: Offset, isStart: Boolean) {
                Box(
                    Modifier
                        .offset { IntOffset((pos.x - 9.dp.toPx()).roundToInt(), (pos.y - 20.dp.toPx()).roundToInt()) }
                        .size(18.dp, 24.dp)
                ) {
                    Box(
                        Modifier
                            .align(if (isStart) Alignment.TopStart else Alignment.TopEnd)
                            .size(5.dp, 20.dp)
                            .background(handleColor, RoundedCornerShape(3.dp))
                    )
                    Box(
                        Modifier
                            .align(if (isStart) Alignment.TopStart else Alignment.TopEnd)
                            .padding(top = 16.dp)
                            .size(18.dp)
                            .background(handleColor, androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
            handle(startHandle, true)
            handle(endHandle, false)
            // 拖动手柄：起点落在任一手柄附近即可拖动调整选区
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page.start, selLocal) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val nearStart = (down.position - startHandle).getDistance() < 72f
                            val nearEnd = (down.position - endHandle).getDistance() < 72f
                            if (!nearStart && !nearEnd) return@awaitEachGesture
                            val which = if (nearStart) 0 else 1
                            var dragging = true
                            while (dragging) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.changedToUp()) {
                                    dragging = false
                                } else if (change.pressed) {
                                    val line = layout.getLineForVertical(change.position.y.toInt())
                                    val off = layout.getOffsetForHorizontal(line, change.position.x)
                                    onSelectionDrag(which == 0, off + page.start)
                                    change.consume()
                                }
                            }
                        }
                    }
            )
        }
    }
}

private fun handlePoint(layout: StaticLayout, localOffset: Int): Offset {
    val line = layout.getLineForOffset(localOffset.coerceIn(0, layout.text.length))
    return Offset(
        layout.getPrimaryHorizontal(localOffset.coerceIn(0, layout.text.length)),
        layout.getLineBottom(line).toFloat()
    )
}

/** 翻页模式页面 + 翻页动画 */
@Composable
private fun PageCanvas(
    text: String,
    page: PaginationEngine.Page,
    textSizePx: Float,
    serif: Boolean,
    lineSpacing: Float,
    widthPx: Int,
    textColor: Color,
    pageHeightPx: Int = 0,
    highlights: List<com.moyue.reader.data.db.HighlightEntity> = emptyList(),
    images: List<com.moyue.reader.data.db.BookImageEntity> = emptyList(),
    imageDir: java.io.File? = null,
    selection: IntRange? = null,
    onLayoutBuilt: ((Int, StaticLayout?) -> Unit)? = null,
    onSelectionDrag: ((Boolean, Int) -> Unit)? = null,
    pageAnim: PageAnim,
    pagerState: PagerState,
    pageIdx: Int
) {
    val animModifier = when (pageAnim) {
        PageAnim.SLIDE -> Modifier
        PageAnim.COVER -> Modifier.graphicsLayer {
            val f = pagerState.currentPageOffsetFraction
            val isCurrent = pagerState.currentPage == pageIdx
            // 前进：当前页钉住，下一页自然滑入覆盖；后退：当前页自然滑出露出上一页
            translationX = if (f >= 0f && isCurrent) f * size.width else 0f
        }
        PageAnim.FADE -> Modifier.graphicsLayer {
            val pageOffset =
                (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction
            alpha = 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
        }
        PageAnim.NONE -> Modifier.graphicsLayer {
            translationX = pagerState.currentPageOffsetFraction * size.width
        }
        PageAnim.CURL -> Modifier.graphicsLayer {
            // 仿真翻页（书本式）：当前页绕书脊 3D 翻转，下一页/上一页垫底静止
            val f = pagerState.currentPageOffsetFraction
            val w = size.width
            when {
                pagerState.currentPage == pageIdx && f > 0f -> {
                    cameraDistance = 10f * density
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    rotationY = -180f * f
                    alpha = if (f > 0.5f) (1f - (f - 0.5f) * 2f).coerceIn(0f, 1f) else 1f
                }
                pagerState.currentPage + 1 == pageIdx && f > 0f -> {
                    translationX = -(1f - f) * w
                }
                pagerState.currentPage == pageIdx && f < 0f -> {
                    // 后退时当前页保持静止，等待上一页翻回
                }
                pagerState.currentPage - 1 == pageIdx && f < 0f -> {
                    val g = -f
                    cameraDistance = 10f * density
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                    rotationY = 180f * g
                    translationX = (1f - g) * w
                    alpha = if (g < 0.5f) g * 2f else 1f
                }
            }
        }
    }
    StaticPage(
        text = text,
        page = page,
        textSizePx = textSizePx,
        serif = serif,
        lineSpacing = lineSpacing,
        widthPx = widthPx,
        textColor = textColor,
        pageHeightPx = pageHeightPx,
        highlights = highlights,
        images = images,
        imageDir = imageDir,
        selection = selection,
        onLayoutBuilt = onLayoutBuilt,
        onSelectionDrag = onSelectionDrag,
        modifier = Modifier
            .fillMaxSize()
            .then(animModifier)
    )
}

@Composable
private fun ReaderFooter(
    palette: ReaderPalette,
    bookTitle: String,
    chapterTitle: String,
    pageIdx: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    var battery by remember { mutableIntStateOf(-1) }
    var clock by remember { mutableStateOf("") }

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    battery = level * 100 / scale
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chapterTitle.ifBlank { bookTitle },
            color = palette.secondary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (pageCount > 0) "${pageIdx + 1}/$pageCount" else "",
            color = palette.secondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.width(10.dp))
        Text(clock, color = palette.secondary, fontSize = 11.sp)
        Spacer(Modifier.width(10.dp))
        if (battery >= 0) {
            Box(
                Modifier
                    .width(18.dp)
                    .height(10.dp)
                    .background(palette.secondary.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                    .padding(1.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(battery / 100f)
                        .height(8.dp)
                        .background(palette.secondary, RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.width(2.dp))
            Text("$battery%", color = palette.secondary, fontSize = 11.sp)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
