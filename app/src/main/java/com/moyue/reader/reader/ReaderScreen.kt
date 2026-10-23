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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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

    val scrollState = rememberScrollState()

    // ---------- 翻页（PAGE 模式用 Pager） ----------
    val pagerState = rememberPagerState(initialPage = 0) { ui.pages.size }

    LaunchedEffect(pagerState) {
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
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
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
                    ui.loading && ui.pages.isEmpty() -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = palette.secondary
                    )

                    ui.pages.isEmpty() && ui.book == null -> Text(
                        "书籍不存在或已删除",
                        color = palette.secondary,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    prefs.readMode == ReadMode.SCROLL -> ScrollReaderContent(
                        ui = ui,
                        vm = vm,
                        scrollState = scrollState,
                        pageHeightPx = textAreaSize.height,
                        textSizePx = textSizePx,
                        serif = prefs.serif,
                        lineSpacing = prefs.lineSpacing,
                        widthPx = textAreaSize.width,
                        textColor = palette.text,
                        accentColor = palette.secondary,
                        hasPrev = ui.chapterIdx > 0,
                        hasNext = ui.chapterIdx < ui.chapters.size - 1
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

        // 点击层
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(prefs.tapZone, prefs.readMode, ui.menuVisible) {
                    detectTapGestures { offset ->
                        if (ui.menuVisible) {
                            vm.setMenuVisible(false)
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
                    }
                }
        )

        // 菜单
        ReaderMenuOverlay(
            visible = ui.menuVisible,
            ui = ui,
            palette = palette,
            vm = vm,
            onBack = onBack
        )
    }
}

/** 上下滚动阅读模式：页面纵向连续堆叠，章节首尾提供上/下一章入口 */
@Composable
private fun ScrollReaderContent(
    ui: ReaderViewModel.UiState,
    vm: ReaderViewModel,
    scrollState: androidx.compose.foundation.ScrollState,
    pageHeightPx: Int,
    textSizePx: Float,
    serif: Boolean,
    lineSpacing: Float,
    widthPx: Int,
    textColor: Color,
    accentColor: Color,
    hasPrev: Boolean,
    hasNext: Boolean
) {
    // 进度跟踪：当前页 = 视口中间可见的页
    LaunchedEffect(scrollState, pageHeightPx) {
        snapshotFlow { scrollState.value }.collect { v ->
            if (pageHeightPx > 0 && ui.pages.isNotEmpty()) {
                val idx = (v + pageHeightPx / 2) / pageHeightPx
                vm.onPageSettled(idx.coerceIn(0, ui.pages.size - 1))
            }
        }
    }
    // 进度恢复：分页完成或跳转后滚动到目标页（与当前差异大于半页才跳）
    LaunchedEffect(ui.pages, ui.pageIdx, pageHeightPx) {
        if (ui.pages.isNotEmpty() && pageHeightPx > 0 && !scrollState.isScrollInProgress) {
            val target = ui.pageIdx * pageHeightPx
            if (abs(scrollState.value - target) > pageHeightPx / 2) {
                scrollState.scrollTo(target.coerceIn(0, scrollState.maxValue))
            }
        }
    }

    val density = LocalDensity.current
    val pageHeightDp = with(density) { pageHeightPx.toDp() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        if (hasPrev) {
            ScrollChapterButton("上一章", accentColor) { vm.prevChapter() }
        }
        ui.pages.forEachIndexed { idx, page ->
            StaticPage(
                text = ui.chapterText,
                page = page,
                textSizePx = textSizePx,
                serif = serif,
                lineSpacing = lineSpacing,
                widthPx = widthPx,
                textColor = textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pageHeightDp)
            )
        }
        if (hasNext) {
            ScrollChapterButton("下一章", accentColor) { vm.nextChapter() }
        } else {
            Text(
                "—— 全书完 ——",
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

/** 与分页完全相同的 StaticLayout 参数渲染一页文本（无翻页动画，两种模式共用） */
@Composable
fun StaticPage(
    text: String,
    page: PaginationEngine.Page,
    textSizePx: Float,
    serif: Boolean,
    lineSpacing: Float,
    widthPx: Int,
    textColor: Color,
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
    val layout = remember(text, page, paint, widthPx, lineSpacing) {
        val from = page.start.coerceIn(0, text.length)
        val to = page.end.coerceIn(from, text.length)
        val sub = text.substring(from, to)
        StaticLayout.Builder.obtain(sub, 0, sub.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacing)
            .setIncludePad(false)
            .build()
    }
    androidx.compose.foundation.Canvas(modifier) {
        layout.draw(drawContext.canvas.nativeCanvas)
    }
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
