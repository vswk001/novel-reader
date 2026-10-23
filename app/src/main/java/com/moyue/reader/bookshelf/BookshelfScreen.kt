package com.moyue.reader.bookshelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.repo.SortKind
import com.moyue.reader.ui.common.BookCover
import com.moyue.reader.ui.common.BookMeta
import com.moyue.reader.ui.common.fmtDuration
import com.moyue.reader.ui.common.fmtSize
import com.moyue.reader.ui.common.maskTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit
) {
    val vm: BookshelfViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BookEntity?>(null) }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importUris(uris) }

    val pickTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.scanFolder(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("墨阅 · 书架") },
                actions = {
                    IconButton(onClick = { vm.togglePrivacy() }) {
                        Icon(
                            if (ui.prefs.privacyMode) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "隐私模式"
                        )
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
                    IconButton(onClick = onOpenStats) {
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
                text = { Text("导入图书") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (ui.importing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            ui.message?.let { msg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("书架空空如也", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点击右下角「导入图书」，选择手机中的 txt 文件\n支持自动识别编码与章节目录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (ui.prefs.bookshelfGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(books, key = { it.id }) { book ->
                        val displayTitle = maskTitle(book.title, ui.prefs.privacyMode)
                        Column(
                            Modifier.combinedClickable(
                                onClick = { onOpenBook(book.id) },
                                onLongClick = { actionBook = book }
                            )
                        ) {
                            BookCover(
                                title = book.title,
                                hue = book.coverHue,
                                masked = ui.prefs.privacyMode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (book.pinned) "📌 $displayTitle" else displayTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(books, key = { it.id }) { book ->
                        val displayTitle = maskTitle(book.title, ui.prefs.privacyMode)
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
                                masked = ui.prefs.privacyMode,
                                modifier = Modifier.size(width = 52.dp, height = 72.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (book.pinned) "📌 $displayTitle" else displayTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
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

    // 导入菜单
    if (showImportMenu) {
        AlertDialog(
            onDismissRequest = { showImportMenu = false },
            title = { Text("导入本地图书") },
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
            title = { Text(maskTitle(book.title, ui.prefs.privacyMode)) },
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
                        Icon(Icons.Filled.DriveFileMove, contentDescription = null, Modifier.size(18.dp))
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

private fun progressText(book: BookEntity): String =
    if (book.totalChars > 0) {
        val percent = ((book.curChapter + 1) * 100L / book.chapterCount.coerceAtLeast(1))
            .coerceIn(0L, 100L)
        "已读 $percent%"
    } else {
        "未开始"
    }
