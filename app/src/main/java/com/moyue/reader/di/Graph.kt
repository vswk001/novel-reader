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

    val database: AppDatabase
        get() = db ?: AppDatabase.build(appContext, singleThread = testSingleThreadDb).also { db = it }
    val bookRepository: BookRepository
        get() = bookRepo ?: BookRepository(appContext, database, java.io.File(appContext.filesDir, "books")).also { bookRepo = it }
    val settingsRepository: SettingsRepository
        get() = settings ?: SettingsRepository(appContext).also { settings = it }
    val bookImporter: BookImporter
        get() = importer ?: BookImporter(appContext, bookRepository).also { importer = it }
    val backupManager: BackupManager
        get() = backup ?: BackupManager(appContext, bookRepository, settingsRepository).also { backup = it }
    val privacyRepository: PrivacyRepository
        get() = privacy ?: PrivacyRepository(appContext).also { privacy = it }
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

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
