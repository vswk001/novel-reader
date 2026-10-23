package com.moyue.reader.reader

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyue.reader.data.repo.ReaderThemeKind

private enum class ReaderSheet { TOC, BOOKMARK, SEARCH }

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

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ---- 顶栏 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(palette.menuBg)
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
                IconButton(onClick = {
                    vm.updatePrefs { it.copy(privacyMode = !it.privacyMode) }
                }) {
                    Icon(
                        if (ui.prefs.privacyMode) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = "隐私模式",
                        tint = palette.menuText
                    )
                }
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

            Spacer(Modifier.weight(1f, true))

            // ---- 底部面板 ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.menuBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // 亮度
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        vm.updatePrefs {
                            it.copy(brightnessFollowSystem = !it.brightnessFollowSystem)
                        }
                    }) {
                        Icon(
                            Icons.Filled.Brightness6,
                            contentDescription = "亮度",
                            tint = if (ui.prefs.brightnessFollowSystem) palette.secondary else palette.menuText
                        )
                    }
                    Text(
                        if (ui.prefs.brightnessFollowSystem) "跟随系统" else "手动",
                        color = palette.secondary,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = ui.prefs.brightness,
                        onValueChange = {
                            vm.updatePrefs { p ->
                                p.copy(brightness = it, brightnessFollowSystem = false)
                            }
                        },
                        valueRange = 0.05f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        enabled = !ui.prefs.brightnessFollowSystem
                    )
                }

                // 字号 + 主题 + 字体
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
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
                                .size(26.dp)
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
                    Spacer(Modifier.width(6.dp))
                    MenuRoundButton(
                        if (ui.prefs.serif) "宋" else "黑",
                        palette,
                        highlight = true
                    ) {
                        vm.updatePrefs { it.copy(serif = !it.serif) }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 章节进度
                val chapterCount = ui.chapters.size
                if (chapterCount > 1) {
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
                                vm.jumpToChapter(sliderValue.toInt().coerceIn(0, chapterCount - 1))
                            },
                            valueRange = 0f..(chapterCount - 1).toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 功能按钮
                Row(
                    Modifier.fillMaxWidth(),
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
            }
        }
    }

    // ---- 抽屉 ----
    val current = sheet
    if (current != null) {
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            containerColor = palette.menuBg
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
private fun MenuRoundButton(
    label: String,
    palette: ReaderPalette,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(34.dp)
            .border(1.dp, if (highlight) palette.menuText else palette.divider, CircleShape)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    palette: ReaderPalette,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = palette.menuText)
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
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "目录（${ui.chapters.size} 章）",
                color = palette.menuText,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (reversed) "倒序" else "正序",
                color = palette.secondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { reversed = !reversed }
                    .padding(6.dp)
            )
        }
        LazyColumn(Modifier.height(480.dp)) {
            items(list, key = { "${it.bookId}-${it.idx}" }) { ch ->
                val isCurrent = ch.idx == ui.chapterIdx
                Text(
                    text = ch.title,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else palette.menuText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(ch.idx) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
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
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "书签（${ui.bookmarks.size}）",
                color = palette.menuText,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { vm.addBookmarkAtCurrentPage() }
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新增",
                    tint = palette.menuText,
                    modifier = Modifier.size(18.dp)
                )
                Text("添加当前页", color = palette.secondary, fontSize = 13.sp)
            }
        }
        if (ui.bookmarks.isEmpty()) {
            Text(
                "暂无书签，点击右上角添加",
                color = palette.secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(Modifier.height(460.dp)) {
            items(ui.bookmarks, key = { it.id }) { bm ->
                val chapterTitle = ui.chapters.getOrNull(bm.chapterIdx)?.title ?: ""
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable {
                                vm.jumpToChapter(bm.chapterIdx, bm.charOffset.let {
                                    val ch = ui.chapters.getOrNull(bm.chapterIdx)
                                    if (ch != null) (it - ch.charStart).coerceAtLeast(0) else 0
                                })
                            }
                    ) {
                        Text(
                            chapterTitle,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                        Text(
                            bm.excerpt,
                            color = palette.menuText,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { vm.deleteBookmark(bm) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = palette.secondary,
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
            placeholder = { Text("书内搜索", color = palette.secondary) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { if (query.isNotBlank()) vm.search(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", tint = palette.menuText)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Text(
            when {
                ui.searching -> "搜索中…"
                ui.searchResults == null -> "输入关键词，在整本书中查找"
                ui.searchResults.isEmpty() -> "没有找到「$query」"
                else -> "共 ${ui.searchResults.size} 处结果"
            },
            color = palette.secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyColumn(Modifier.height(420.dp)) {
            items(ui.searchResults.orEmpty(), key = { "${it.chapterIdx}-${it.charOffset}" }) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(hit) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        hit.chapterTitle,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Text(
                        hit.snippet,
                        color = palette.menuText,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
