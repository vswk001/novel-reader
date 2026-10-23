package com.moyue.reader

import android.app.Application
import com.moyue.reader.di.Graph

class MoyueApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
