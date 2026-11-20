package com.moyue.reader.di

import android.content.Context
import com.moyue.reader.data.backup.BackupManager
import com.moyue.reader.data.db.AppDatabase
import com.moyue.reader.data.importer.BookImporter
import com.moyue.reader.data.repo.BookRepository
import com.moyue.reader.data.repo.SettingsRepository
import com.moyue.reader.data.transfer.TransferServer
import com.moyue.reader.privacy.PrivacyRepository

/** 轻量服务定位器：应用规模小，不引入 Hilt */
object Graph {
    lateinit var appContext: Context
        private set

    /** Robolectric 测试置 true：Room 走单线程执行器（须在首次访问 database 前设置） */
    @Volatile
    var testSingleThreadDb = false

    // 可重置单例（生产等价 lazy；测试类之间须重建，避免跨上下文的旧 SQLite 连接）
    private var db: AppDatabase? = null
    private var bookRepo: BookRepository? = null
    private var settings: SettingsRepository? = null
    private var importer: BookImporter? = null
    private var backup: BackupManager? = null
    private var privacy: PrivacyRepository? = null
    private var transfer: TransferServer? = null

    // @Synchronized：by lazy 的线程安全在改为可空 getter 时丢失，须补回
    // （启动期 IO 线程清理回收站与主线程同时首次访问 → 两个 Room 实例 → SQLite 冲突闪退）
    @get:Synchronized
    val database: AppDatabase
        get() = db ?: AppDatabase.build(appContext, singleThread = testSingleThreadDb).also { db = it }
    @get:Synchronized
    val bookRepository: BookRepository
        get() = bookRepo ?: BookRepository(appContext, database, java.io.File(appContext.filesDir, "books")).also { bookRepo = it }
    @get:Synchronized
    val settingsRepository: SettingsRepository
        get() = settings ?: SettingsRepository(appContext).also { settings = it }
    @get:Synchronized
    val bookImporter: BookImporter
        get() = importer ?: BookImporter(appContext, bookRepository).also { importer = it }
    @get:Synchronized
    val backupManager: BackupManager
        get() = backup ?: BackupManager(appContext, bookRepository, settingsRepository).also { backup = it }
    @get:Synchronized
    val privacyRepository: PrivacyRepository
        get() = privacy ?: PrivacyRepository(appContext).also { privacy = it }
    @get:Synchronized
    val transferServer: TransferServer
        get() = transfer ?: TransferServer(appContext.cacheDir) { f, n, s -> bookImporter.importFile(f, n, s) }.also { transfer = it }

    /** Robolectric 专用：类间断开全部单例引用（旧上下文的 SQLite 连接不可跨类复用；
     *  不 close——close 会让仍存活的协程抛未捕获异常，旧库直接弃置即可） */
    @Synchronized
    fun resetForTest() {
        runCatching { transfer?.stop() }
        transfer = null
        db = null
        bookRepo = null
        settings = null
        importer = null
        backup = null
        privacy = null
    }

    /** 阅读页注册的音量键处理器，返回 true 表示已消费 */
    @Volatile
    var volumeKeyHandler: ((Int) -> Boolean)? = null

    /** 系统「打开方式/分享」带入的待导入 Uri，书架页消费后置空 */
    @Volatile
    var pendingImportUri: android.net.Uri? = null

    // ---- ActivityResult 全局 launcher（Activity.onCreate 一次注册，每进程仅此一次；
    //      避免 rememberLauncherForActivityResult 的注销/重注册循环累积请求码 → 16 位溢出闪退）----

    @Volatile
    var pickFilesLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null
    @Volatile
    var pickFolderLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>? = null
    @Volatile
    var exportBackupLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null
    @Volatile
    var importBackupLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null

    /** 结果回调桥：页面进入时挂接、离开时置空 */
    @Volatile
    var onFilesPicked: ((List<android.net.Uri>) -> Unit)? = null
    @Volatile
    var onFolderPicked: ((android.net.Uri) -> Unit)? = null
    @Volatile
    var onBackupExport: ((android.net.Uri) -> Unit)? = null
    @Volatile
    var onBackupImport: ((android.net.Uri) -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
