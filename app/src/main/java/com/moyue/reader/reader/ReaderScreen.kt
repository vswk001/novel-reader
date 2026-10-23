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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import com.moyue.reader.data.repo.PageAnim
import com.moyue.reader.data.repo.ReaderThemeKind
import com.moyue.reader.di.Graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // ---------- Pager ----------
    val pagerState = rememberPagerState(initialPage = 0) { ui.pages.size }

    // VM 侧页码变化（恢复进度/跳转/换章）时同步到 Pager
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            vm.onPageSettled(page)
        }
    }
    LaunchedEffect(ui.pages, ui.pageIdx) {
        if (ui.pages.isNotEmpty() &&
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

    // ---------- 沉浸式 ----------
    val view = LocalView.current
    DisposableEffect(ui.menuVisible) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (ui.menuVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
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
    DisposableEffect(prefs.volumeKeyTurn) {
        if (prefs.volumeKeyTurn) {
            Graph.volumeKeyHandler = { keyCode ->
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        turnPage(-1)
                        true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        turnPage(1)
                        true
                    }
                    else -> false
                }
            }
        } else {
            Graph.volumeKeyHandler = null
        }
        onDispose {
            if (Graph.volumeKeyHandler != null && prefs.volumeKeyTurn) {
                Graph.volumeKeyHandler = null
            }
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
                if (ui.loading && ui.pages.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = palette.secondary
                    )
                } else if (ui.pages.isEmpty() && ui.book == null) {
                    Text(
                        "书籍不存在或已删除",
                        color = palette.secondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    HorizontalPager(
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

        // 点击区域
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(prefs.tapTurn, ui.menuVisible) {
                    detectTapGestures { offset ->
                        if (ui.menuVisible) {
                            vm.setMenuVisible(false)
                            return@detectTapGestures
                        }
                        if (!prefs.tapTurn) {
                            vm.toggleMenu()
                        } else {
                            val w = size.width
                            when {
                                offset.x < w * 0.28f -> turnPage(-1)
                                offset.x > w * 0.72f -> turnPage(1)
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

/** 用与分页完全相同的 StaticLayout 参数渲染一页，保证所见即所排 */
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
    pagerState: androidx.compose.foundation.pager.PagerState,
    pageIdx: Int
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
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(animModifier)
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            layout.draw(drawContext.canvas.nativeCanvas)
        }
    }
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
                    .background(palette.secondary.copy(alpha = 0.2f), CircleShape)
                    .padding(1.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(battery / 100f)
                        .height(8.dp)
                        .background(palette.secondary, CircleShape)
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
