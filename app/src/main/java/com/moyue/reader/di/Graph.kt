package com.moyue.reader.di

import android.content.Context
import com.moyue.reader.data.backup.BackupManager
import com.moyue.reader.data.db.AppDatabase
import com.moyue.reader.data.importer.BookImporter
import com.moyue.reader.data.repo.BookRepository
import com.moyue.reader.data.repo.SettingsRepository
import com.moyue.reader.privacy.PrivacyRepository

/** 轻量服务定位器：应用规模小，不引入 Hilt */
object Graph {
    lateinit var appContext: Context
        private set

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val bookRepository: BookRepository by lazy {
        BookRepository(appContext, database, java.io.File(appContext.filesDir, "books"))
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val bookImporter: BookImporter by lazy { BookImporter(appContext, bookRepository) }
    val backupManager: BackupManager by lazy {
        BackupManager(appContext, bookRepository, settingsRepository)
    }
    val privacyRepository: PrivacyRepository by lazy { PrivacyRepository(appContext) }

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
