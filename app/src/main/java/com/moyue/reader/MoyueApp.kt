package com.moyue.reader

import android.app.Application
import com.moyue.reader.di.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MoyueApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // 回收站保留 7 天：启动时清理过期项
        GlobalScope.launch(Dispatchers.IO) {
            runCatching { Graph.bookRepository.purgeExpiredTrash() }
        }
    }
}
