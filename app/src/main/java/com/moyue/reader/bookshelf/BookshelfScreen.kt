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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moyue.reader.R
import com.moyue.reader.data.db.BookEntity
import com.moyue.reader.data.repo.SortKind
import com.moyue.reader.di.Graph
import com.moyue.reader.privacy.PasswordDialog
import com.moyue.reader.privacy.PasswordDialogKind
import com.moyue.reader.ui.common.ActionRow
import com.moyue.reader.ui.common.BookCover
import com.moyue.reader.ui.common.BookProgressBar
import com.moyue.reader.ui.common.MoyueDialog
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
    val context = LocalContext.current

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showPrivateMenu by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BookEntity?>(null) }
    var pwdKind by remember { mutableStateOf<PasswordDialogKind?>(null) }
    var bioChecked by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        bioChecked = Graph.privacyRepository.bioEnabled()
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importUris(uris) }

    val pickTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.scanFolder(it) } }

    // 系统「打开方式/分享」带入的文件：导入到普通书架（仅普通书架路由消费）
    androidx.compose.runtime.LaunchedEffect(space) {
        if (space == 0) {
            Graph.pendingImportUri?.let { uri ->
                Graph.pendingImportUri = null
                vm.importUris(listOf(uri))
            }
        }
    }

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

    // 顶栏动作（普通/隐私空间共用）
    val barActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    if (space == 1) {
                        IconButton(onClick = { showPrivateMenu = true }) {
                            Icon(Icons.Filled.Key, contentDescription = stringResource(R.string.cd_private_settings))
                        }
                        DropdownMenu(
                            expanded = showPrivateMenu,
                            onDismissRequest = { showPrivateMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (bioChecked) stringResource(R.string.menu_biometric) + " ✓"
                                        else stringResource(R.string.menu_biometric)
                                    )
                                },
                                onClick = {
                                    showPrivateMenu = false
                                    scope.launch {
                                        val nv = !bioChecked
                                        Graph.privacyRepository.setBioEnabled(nv)
                                        bioChecked = nv
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_change_password)) },
                                onClick = {
                                    showPrivateMenu = false
                                    pwdKind = PasswordDialogKind.CHANGE
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cd_exit_private)) },
                                onClick = {
                                    showPrivateMenu = false
                                    onBack?.invoke()
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) vm.setQuery("") }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search))
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (ui.prefs.sortBy == SortKind.RECENT)
                                        stringResource(R.string.sort_recent) + " ✓"
                                    else stringResource(R.string.sort_recent)
                                )
                            },
                            onClick = { vm.setSort(SortKind.RECENT); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (ui.prefs.sortBy == SortKind.TITLE)
                                        stringResource(R.string.sort_title) + " ✓"
                                    else stringResource(R.string.sort_title)
                                )
                            },
                            onClick = { vm.setSort(SortKind.TITLE); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (ui.prefs.sortBy == SortKind.ADDED)
                                        stringResource(R.string.sort_added) + " ✓"
                                    else stringResource(R.string.sort_added)
                                )
                            },
                            onClick = { vm.setSort(SortKind.ADDED); showSortMenu = false }
                        )
                    }
                    IconButton(onClick = { vm.toggleGridView() }) {
                        Icon(
                            if (ui.prefs.bookshelfGrid) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = stringResource(R.string.cd_switch_view)
                        )
                    }
                    IconButton(onClick = { onOpenStats(space) }) {
                        Icon(Icons.Filled.Insights, contentDescription = stringResource(R.string.cd_stats))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
    }

    val scrollBehavior = if (space == 0) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (space == 0) {
                // 普通书架：大标题栏，滚动收起
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.title_bookshelf)) },
                    actions = barActions,
                    scrollBehavior = scrollBehavior
                )
            } else {
                // 隐私空间：常规标题栏（返回 + 钥匙菜单）
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.title_private_space),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (space == 1 && onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_exit_private))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    actions = barActions,
                    scrollBehavior = scrollBehavior
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showImportMenu = true },
                icon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                text = { Text(if (space == 1) stringResource(R.string.btn_import_private) else stringResource(R.string.btn_import)) }
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
                        if (armed) stringResource(R.string.pull_release) else stringResource(R.string.pull_hint),
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
                // 继续阅读入口（普通书架 + 有书时）
                if (space == 0 && ui.books.isNotEmpty()) {
                    val lastBook = remember(ui.books) {
                        ui.books.maxByOrNull { it.lastReadAt }
                    }
                    lastBook?.let { book ->
                        Surface(
                            onClick = { onOpenBook(book.id) },
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BookCover(
                                    title = book.title,
                                    hue = book.coverHue,
                                    fontSize = 13.sp,
                                    maxChars = 2,
                                    modifier = Modifier.size(width = 30.dp, height = 42.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.continue_reading),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        book.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        stringResource(
                                            R.string.chapter_progress,
                                            book.curChapter + 1, book.chapterCount, progressText(book)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
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
                            TextButton(onClick = { vm.clearMessage() }) { Text(stringResource(R.string.btn_close)) }
                        }
                    }
                }
                if (showSearch) {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = { vm.setQuery(it) },
                        placeholder = { Text(stringResource(R.string.hint_search_books)) },
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
                                if (space == 1) stringResource(R.string.empty_private_title)
                                else stringResource(R.string.empty_shelf_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (space == 1) stringResource(R.string.empty_private_body)
                                else stringResource(R.string.empty_shelf_body),
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
                                    maxChars = 3,
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
                                        stringResource(
                                            R.string.book_list_meta,
                                            progressText(book), book.chapterCount, fmtSize(book.sizeBytes)
                                        ),
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
        MoyueDialog(
            onDismissRequest = { showImportMenu = false },
            title = stringResource(R.string.import_title),
            subtitle = stringResource(R.string.import_subtitle),
            dismissText = stringResource(R.string.btn_close)
        ) {
            ActionRow(
                Icons.Filled.UploadFile,
                stringResource(R.string.import_pick_files),
                stringResource(R.string.import_pick_files_sub)
            ) {
                showImportMenu = false
                pickFiles.launch(arrayOf("text/*", "application/epub+zip", "application/octet-stream"))
            }
            ActionRow(
                Icons.Filled.FolderOpen,
                stringResource(R.string.import_scan_folder),
                stringResource(R.string.import_scan_folder_sub)
            ) {
                showImportMenu = false
                pickTree.launch(null)
            }
        }
    }

    // 长按操作 → 底部动作表
    actionBook?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { actionBook = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCover(
                    title = book.title,
                    hue = book.coverHue,
                    fontSize = 12.sp,
                    maxChars = 2,
                    modifier = Modifier.size(width = 34.dp, height = 48.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(
                            R.string.action_sheet_meta,
                            book.chapterCount, fmtSize(book.sizeBytes),
                            fmtDuration(context, book.readTimeMillis)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (book.charset != "EPUB") {
                ActionRow(
                    Icons.Filled.Refresh,
                    stringResource(R.string.action_reparse),
                    stringResource(R.string.action_reparse_sub)
                ) {
                    vm.reparseBook(book)
                    actionBook = null
                }
            }
            ActionRow(
                if (space == 1) Icons.Filled.SwapVert else Icons.Filled.DriveFileMove,
                if (space == 1) stringResource(R.string.action_move_to_library)
                else stringResource(R.string.action_move_to_private),
                stringResource(R.string.action_move_sub)
            ) {
                vm.moveBook(book, 1 - space)
                actionBook = null
            }
            ActionRow(
                Icons.Filled.PushPin,
                if (book.pinned) stringResource(R.string.action_unpin)
                else stringResource(R.string.action_pin),
                stringResource(R.string.action_pin_sub)
            ) {
                vm.togglePin(book)
                actionBook = null
            }
            ActionRow(
                Icons.Filled.Delete,
                stringResource(R.string.action_remove),
                stringResource(R.string.action_remove_sub),
                tint = MaterialTheme.colorScheme.error
            ) {
                deleteTarget = book
                actionBook = null
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // 删除确认：默认移入回收站，可选彻底删除
    deleteTarget?.let { book ->
        MoyueDialog(
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.delete_title, book.title),
            subtitle = stringResource(R.string.delete_subtitle),
            confirmText = stringResource(R.string.trash_to_bin),
            onConfirm = {
                vm.trashBook(book)
                deleteTarget = null
            }
        ) {
            ActionRow(
                Icons.Filled.Delete,
                stringResource(R.string.delete_forever),
                stringResource(R.string.delete_forever_sub),
                tint = MaterialTheme.colorScheme.error
            ) {
                vm.deleteBook(book, deleteFile = true)
                deleteTarget = null
            }
        }
    }
}

private fun progressPercent(book: BookEntity): Int =
    if (book.totalChars > 0) {
        (((book.curChapter + 1) * 100L / book.chapterCount.coerceAtLeast(1))
            .coerceIn(0L, 100L)).toInt()
    } else {
        0
    }

@Composable
private fun progressText(book: BookEntity): String {
    val p = progressPercent(book)
    return if (p > 0) stringResource(R.string.progress_read, p)
    else stringResource(R.string.progress_unread)
}
