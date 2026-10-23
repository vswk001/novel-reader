package com.moyue.reader.bookshelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.repo.SortKind
import com.moyue.reader.di.Graph
import com.moyue.reader.privacy.PasswordDialog
import com.moyue.reader.privacy.PasswordDialogKind
import com.moyue.reader.ui.common.BookCover
import com.moyue.reader.ui.common.BookProgressBar
import com.moyue.reader.ui.common.fmtDuration
import com.moyue.reader.ui.common.fmtSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: (Int) -> Unit,
    onEnterPrivate: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val vm: BookshelfViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val space = ui.space
    val scope = rememberCoroutineScope()

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showPrivateMenu by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BookEntity?>(null) }
    var pwdKind by remember { mutableStateOf<PasswordDialogKind?>(null) }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importUris(uris) }

    val pickTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.scanFolder(it) } }

    /** 下拉过半屏松开 → 进入隐私空间（未设密码则先引导设置） */
    fun attemptEnterPrivate() {
        scope.launch {
            pwdKind = if (Graph.privacyRepository.hasPassword()) {
                PasswordDialogKind.UNLOCK
            } else {
                PasswordDialogKind.SETUP
            }
        }
    }

    // ---------- 隐私空间下拉手势 ----------
    val density = LocalDensity.current
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val thresholdPx = screenH * 0.5f
    val maxPullPx = screenH * 0.62f
    var pullPx by remember { mutableFloatStateOf(0f) }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    val pullEnabled = space == 0
    val armed = pullPx >= thresholdPx

    val pullConnection = remember(pullEnabled) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!pullEnabled || source != NestedScrollSource.UserInput || available.y <= 0f) {
                    return Offset.Zero
                }
                snapJob?.cancel()
                pullPx = (pullPx + available.y).coerceIn(0f, maxPullPx)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullPx > 0f) {
                    val go = pullPx >= thresholdPx
                    val from = pullPx
                    snapJob = scope.launch {
                        animate(from, 0f) { v, _ -> pullPx = v }
                    }
                    if (go) attemptEnterPrivate()
                }
                return Velocity.Zero
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (space == 1) "墨阅 · 隐私空间" else "墨阅 · 书架") },
                navigationIcon = {
                    if (space == 1 && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出隐私空间")
                        }
                    }
                },
                colors = if (space == 1) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                actions = {
                    if (space == 1) {
                        IconButton(onClick = { showPrivateMenu = true }) {
                            Icon(Icons.Filled.Key, contentDescription = "隐私空间设置")
                        }
                        DropdownMenu(
                            expanded = showPrivateMenu,
                            onDismissRequest = { showPrivateMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("修改密码") },
                                onClick = {
                                    showPrivateMenu = false
                                    pwdKind = PasswordDialogKind.CHANGE
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("退出隐私空间") },
                                onClick = {
                                    showPrivateMenu = false
                                    onBack?.invoke()
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) vm.setQuery("") }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "排序")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (ui.prefs.sortBy == SortKind.RECENT) "最近阅读 ✓" else "最近阅读") },
                            onClick = { vm.setSort(SortKind.RECENT); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (ui.prefs.sortBy == SortKind.TITLE) "书名 ✓" else "书名") },
                            onClick = { vm.setSort(SortKind.TITLE); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (ui.prefs.sortBy == SortKind.ADDED) "添加时间 ✓" else "添加时间") },
                            onClick = { vm.setSort(SortKind.ADDED); showSortMenu = false }
                        )
                    }
                    IconButton(onClick = { vm.toggleGridView() }) {
                        Icon(
                            if (ui.prefs.bookshelfGrid) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "切换视图"
                        )
                    }
                    IconButton(onClick = { onOpenStats(space) }) {
                        Icon(Icons.Filled.Insights, contentDescription = "阅读统计")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showImportMenu = true },
                icon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                text = { Text(if (space == 1) "导入到隐私空间" else "导入图书") }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullConnection)
        ) {
            // ---- 下拉提示（仅普通书架） ----
            if (space == 0) {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .graphicsLayer {
                            alpha = ((pullPx - 30f) / thresholdPx).coerceIn(0f, 1f)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (armed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (armed) "松开进入隐私空间" else "下拉进入隐私空间",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (armed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (pullPx / thresholdPx).coerceIn(0f, 1f) },
                        modifier = Modifier.width(140.dp)
                    )
                }
            }

            // ---- 内容（随下拉轻微下移，形成橡皮筋效果） ----
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = pullPx * 0.3f }
            ) {
                if (ui.importing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                ui.message?.let { msg ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.clearMessage() }) { Text("关闭") }
                        }
                    }
                }
                if (showSearch) {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = { vm.setQuery(it) },
                        placeholder = { Text("搜索书名") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                val books = ui.visible
                if (books.isEmpty()) {
                    // verticalScroll 使空书架也支持下拉进入隐私空间
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(96.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (space == 1) "隐私空间是空的" else "书架还空着",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (space == 1) {
                                    "导入的书籍只存在于隐私空间\n长按书籍可在两个空间之间转移"
                                } else {
                                    "导入手机中的 txt 文件开始阅读\n自动识别编码与章节目录"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else if (ui.prefs.bookshelfGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(books, key = { it.id }) { book ->
                            Column(
                                Modifier
                                    .animateItem()
                                    .combinedClickable(
                                        onClick = { onOpenBook(book.id) },
                                        onLongClick = { actionBook = book }
                                    )
                            ) {
                                BookCover(
                                    title = book.title,
                                    hue = book.coverHue,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (book.pinned) "📌 ${book.title}" else book.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                BookProgressBar(
                                    progressPercent(book),
                                    Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    progressText(book),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(books, key = { it.id }) { book ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onOpenBook(book.id) },
                                        onLongClick = { actionBook = book }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BookCover(
                                    title = book.title,
                                    hue = book.coverHue,
                                    fontSize = 20.sp,
                                    modifier = Modifier.size(width = 52.dp, height = 72.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (book.pinned) "📌 ${book.title}" else book.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    BookProgressBar(
                                        progressPercent(book),
                                        Modifier
                                            .fillMaxWidth(0.72f)
                                            .height(3.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${progressText(book)} · ${book.chapterCount} 章 · ${fmtSize(book.sizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 密码对话框 ----------
    pwdKind?.let { kind ->
        PasswordDialog(
            kind = kind,
            onDismiss = { pwdKind = null },
            onDone = {
                when (kind) {
                    PasswordDialogKind.UNLOCK -> {
                        pwdKind = null
                        onEnterPrivate()
                    }
                    PasswordDialogKind.SETUP -> {
                        pwdKind = null
                        onEnterPrivate()
                    }
                    PasswordDialogKind.CHANGE -> pwdKind = null
                }
            }
        )
    }

    // 导入菜单
    if (showImportMenu) {
        AlertDialog(
            onDismissRequest = { showImportMenu = false },
            title = { Text("导入图书") },
            text = { Text("支持 txt / log / md 文本文件，自动识别编码（GBK/UTF-8/UTF-16）与章节目录。") },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        showImportMenu = false
                        pickFiles.launch(arrayOf("text/*", "application/octet-stream"))
                    }) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("选择文件（可多选）")
                    }
                    TextButton(onClick = {
                        showImportMenu = false
                        pickTree.launch(null)
                    }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("扫描整个文件夹")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportMenu = false }) { Text("取消") }
            }
        )
    }

    // 长按操作
    actionBook?.let { book ->
        AlertDialog(
            onDismissRequest = { actionBook = null },
            title = { Text(book.title) },
            text = {
                Column {
                    Text("章节：${book.chapterCount}　字符：${book.totalChars}")
                    Text("编码：${book.charset}　大小：${fmtSize(book.sizeBytes)}")
                    Text("累计阅读：${fmtDuration(book.readTimeMillis)}")
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        vm.moveBook(book, 1 - space)
                        actionBook = null
                    }) {
                        Icon(
                            if (space == 1) Icons.Filled.SwapVert else Icons.Filled.DriveFileMove,
                            contentDescription = null,
                            Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (space == 1) "移回普通书架" else "移入隐私空间")
                    }
                    TextButton(onClick = {
                        vm.togglePin(book)
                        actionBook = null
                    }) {
                        Icon(Icons.Filled.PushPin, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (book.pinned) "取消置顶" else "置顶")
                    }
                    TextButton(onClick = {
                        deleteTarget = book
                        actionBook = null
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("移出书架 / 删除")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { actionBook = null }) { Text("关闭") }
            }
        )
    }

    // 删除确认
    deleteTarget?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${book.title}」") },
            text = { Text("移出书架会保留应用内副本，删除文件则同时移除副本，均不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBook(book, deleteFile = false)
                    deleteTarget = null
                }) { Text("仅移出书架") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deleteBook(book, deleteFile = true)
                    deleteTarget = null
                }) { Text("删除（含文件）") }
            }
        )
    }
}

private fun progressPercent(book: BookEntity): Int =
    if (book.totalChars > 0) {
        (((book.curChapter + 1) * 100L / book.chapterCount.coerceAtLeast(1))
            .coerceIn(0L, 100L)).toInt()
    } else {
        0
    }

private fun progressText(book: BookEntity): String {
    val p = progressPercent(book)
    return if (p > 0) "已读 $p%" else "未开始"
}
