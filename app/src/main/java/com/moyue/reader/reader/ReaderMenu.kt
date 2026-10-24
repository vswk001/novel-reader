package com.moyue.reader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import com.moyue.reader.data.repo.ReadMode
import com.moyue.reader.data.repo.ReaderThemeKind

private enum class ReaderSheet { TOC, BOOKMARK, SEARCH }

/**
 * 阅读菜单：顶部返回栏 + 底部悬浮圆角面板（适配导航栏/刘海安全区）。
 * 系统栏保持隐藏（沉浸式），菜单不再唤出导航栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderMenuOverlay(
    visible: Boolean,
    ui: ReaderViewModel.UiState,
    palette: ReaderPalette,
    vm: ReaderViewModel,
    onBack: () -> Unit
) {
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        // 顶栏
        Surface(
            color = palette.menuBg.copy(alpha = 0.96f),
            shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.menuText
                    )
                }
                Text(
                    text = ui.book?.title.orEmpty(),
                    color = palette.menuText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.addBookmarkAtCurrentPage() }) {
                    Icon(
                        Icons.Filled.StarBorder,
                        contentDescription = "加书签",
                        tint = palette.menuText
                    )
                }
                IconButton(onClick = { sheet = ReaderSheet.SEARCH }) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "书内搜索",
                        tint = palette.menuText
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut()
    ) {
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f, true))
            Surface(
                color = palette.menuBg.copy(alpha = 0.98f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    // ---- 亮度 ----
                    MenuSliderRow(
                        palette = palette,
                        value = ui.prefs.brightness,
                        enabled = !ui.prefs.brightnessFollowSystem,
                        label = if (ui.prefs.brightnessFollowSystem) "跟随系统" else "手动",
                        onLabelClick = {
                            vm.updatePrefs {
                                it.copy(brightnessFollowSystem = !it.brightnessFollowSystem)
                            }
                        },
                        onValueChange = {
                            vm.updatePrefs { p ->
                                p.copy(brightness = it, brightnessFollowSystem = false)
                            }
                        }
                    )

                    MenuDivider(palette)

                    // ---- 字号 + 主题 + 字体 ----
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        MenuRoundButton("A－", palette) {
                            vm.updatePrefs { it.copy(fontSp = (it.fontSp - 1).coerceIn(10, 40)) }
                        }
                        Text(
                            "${ui.prefs.fontSp}",
                            color = palette.menuText,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        MenuRoundButton("A＋", palette) {
                            vm.updatePrefs { it.copy(fontSp = (it.fontSp + 1).coerceIn(10, 40)) }
                        }
                        Spacer(Modifier.weight(1f))
                        ReaderThemes.selectable().forEach { kind ->
                            val c = ReaderThemes.of(kind)
                            val selected = ui.prefs.theme == kind && !ui.prefs.themeFollowSystem
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(28.dp)
                                    .background(c.bg, CircleShape)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) palette.menuText else palette.divider,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        vm.updatePrefs {
                                            it.copy(theme = kind, themeFollowSystem = false)
                                        }
                                    }
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        MenuRoundButton(
                            if (ui.prefs.serif) "宋" else "黑",
                            palette,
                            highlight = true
                        ) {
                            vm.updatePrefs { it.copy(serif = !it.serif) }
                        }
                    }

                    // ---- 章节进度 ----
                    val chapterCount = ui.chapters.size
                    if (chapterCount > 1) {
                        MenuDivider(palette)
                        var sliderValue by remember(ui.chapterIdx) {
                            mutableStateOf(ui.chapterIdx.toFloat())
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${ui.chapterIdx + 1}/$chapterCount",
                                color = palette.secondary,
                                fontSize = 12.sp
                            )
                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                onValueChangeFinished = {
                                    vm.jumpToChapter(
                                        sliderValue.toInt().coerceIn(0, chapterCount - 1)
                                    )
                                },
                                valueRange = 0f..(chapterCount - 1).toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            )
                        }
                    }

                    MenuDivider(palette)

                    // ---- 功能按钮（两行） ----
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MenuIconButton("目录", Icons.AutoMirrored.Filled.MenuBook, palette) {
                            sheet = ReaderSheet.TOC
                        }
                        MenuIconButton("书签", Icons.Filled.Star, palette) {
                            sheet = ReaderSheet.BOOKMARK
                        }
                        MenuIconButton("上一章", Icons.Filled.KeyboardArrowUp, palette) {
                            vm.prevChapter()
                        }
                        MenuIconButton("下一章", Icons.Filled.KeyboardArrowDown, palette) {
                            vm.nextChapter()
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MenuIconButton(
                            if (ui.prefs.readMode == ReadMode.PAGE) "切滚动" else "切翻页",
                            Icons.Filled.SwapVert,
                            palette
                        ) {
                            vm.updatePrefs {
                                it.copy(
                                    readMode = if (it.readMode == ReadMode.PAGE) ReadMode.SCROLL
                                    else ReadMode.PAGE
                                )
                            }
                        }
                        MenuIconButton(
                            if (ui.ttsRunning) "停止朗读" else "朗读",
                            if (ui.ttsRunning) Icons.Filled.StopCircle
                            else Icons.Filled.RecordVoiceOver,
                            palette
                        ) {
                            vm.toggleTts()
                        }
                        MenuIconButton(
                            "自动滚动",
                            Icons.Filled.PlayCircle,
                            palette
                        ) {
                            // 翻页模式下点自动滚动：先切到滚动模式；启动后收起菜单，
                            // 屏幕底部出现专用控制条（停止/调速），正文触摸也可停止
                            if (ui.prefs.readMode != ReadMode.SCROLL) {
                                vm.updatePrefs { it.copy(readMode = ReadMode.SCROLL) }
                            }
                            vm.setAutoScroll(true)
                            vm.setMenuVisible(false)
                        }
                    }
                }
            }
        }
    }

    // ---- 抽屉 ----
    val current = sheet
    if (current != null) {
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            when (current) {
                ReaderSheet.TOC -> TocSheetContent(ui = ui, palette = palette) { idx ->
                    vm.jumpToChapter(idx)
                    sheet = null
                }
                ReaderSheet.BOOKMARK -> BookmarkSheetContent(ui = ui, palette = palette, vm = vm)
                ReaderSheet.SEARCH -> SearchSheetContent(ui = ui, palette = palette, vm = vm) {
                    vm.jumpToAbsOffset(it.chapterIdx, it.charOffset)
                    sheet = null
                }
            }
        }
    }
}

@Composable
private fun MenuDivider(palette: ReaderPalette) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(0.6.dp)
            .background(palette.divider.copy(alpha = 0.6f))
    )
}

@Composable
private fun MenuSliderRow(
    palette: ReaderPalette,
    value: Float,
    enabled: Boolean,
    label: String,
    onLabelClick: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("☀", fontSize = 16.sp, color = palette.menuText)
        Text(
            label,
            fontSize = 12.sp,
            color = palette.secondary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onLabelClick)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.05f..1f,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MenuRoundButton(
    label: String,
    palette: ReaderPalette,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(36.dp)
            .background(
                if (highlight) palette.menuText.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                CircleShape
            )
            .border(1.dp, palette.divider, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = palette.menuText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MenuIconButton(
    label: String,
    icon: ImageVector,
    palette: ReaderPalette,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(palette.menuText.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = palette.menuText)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = palette.secondary, fontSize = 11.sp)
    }
}

@Composable
private fun TocSheetContent(
    ui: ReaderViewModel.UiState,
    palette: ReaderPalette,
    onPick: (Int) -> Unit
) {
    var reversed by remember { mutableStateOf(false) }
    val list = remember(ui.chapters, reversed) {
        if (reversed) ui.chapters.asReversed() else ui.chapters
    }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "目录 · ${ui.chapters.size} 章",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (reversed) "倒序 ⇅" else "正序 ⇅",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { reversed = !reversed }
                    .padding(8.dp)
            )
        }
        LazyColumn(Modifier.height(480.dp)) {
            items(list, key = { "${it.bookId}-${it.idx}" }) { ch ->
                val isCurrent = ch.idx == ui.chapterIdx
                Text(
                    text = ch.title,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(ch.idx) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BookmarkSheetContent(
    ui: ReaderViewModel.UiState,
    palette: ReaderPalette,
    vm: ReaderViewModel
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "书签 · ${ui.bookmarks.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { vm.addBookmarkAtCurrentPage() }
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新增",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "添加当前页",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
            }
        }
        if (ui.bookmarks.isEmpty()) {
            Text(
                "暂无书签，点击右上角添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
        LazyColumn(Modifier.height(460.dp)) {
            items(ui.bookmarks, key = { it.id }) { bm ->
                val chapterTitle = ui.chapters.getOrNull(bm.chapterIdx)?.title ?: ""
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                vm.jumpToChapter(bm.chapterIdx, bm.charOffset.let {
                                    val ch = ui.chapters.getOrNull(bm.chapterIdx)
                                    if (ch != null) (it - ch.charStart).coerceAtLeast(0) else 0
                                })
                            }
                            .padding(8.dp)
                    ) {
                        Text(
                            chapterTitle,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                        Text(
                            bm.excerpt,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { vm.deleteBookmark(bm) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSheetContent(
    ui: ReaderViewModel.UiState,
    palette: ReaderPalette,
    vm: ReaderViewModel,
    onPick: (com.moyue.reader.data.repo.BookRepository.SearchHit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("书内搜索") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = {
                IconButton(onClick = { if (query.isNotBlank()) vm.search(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )
        Text(
            when {
                ui.searching -> "搜索中…"
                ui.searchResults == null -> "输入关键词，在整本书中查找"
                ui.searchResults.isEmpty() -> "没有找到「$query」"
                else -> "共 ${ui.searchResults.size} 处结果"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyColumn(Modifier.height(420.dp)) {
            items(ui.searchResults.orEmpty(), key = { "${it.chapterIdx}-${it.charOffset}" }) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(hit) }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        hit.chapterTitle,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Text(
                        hit.snippet,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
