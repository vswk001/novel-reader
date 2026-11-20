package com.moyue.reader

import android.app.Application
import com.moyue.reader.di.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MoyueApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // 未捕获异常记录到文件（闪退诊断）
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(filesDir, "crash.log").appendText(
                    "=== ${System.currentTimeMillis()} thread=$thread ===\n${sw}\n\n"
                )
            }
            // 交回系统默认处理（结束进程）
            defaultHandler?.uncaughtException(thread, throwable)
        }
        // 回收站保留 7 天：启动时清理过期项
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { Graph.bookRepository.purgeExpiredTrash() }
        }
    }

    companion object {
        private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    }
}
