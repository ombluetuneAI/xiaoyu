package com.xiaoyu.app

import android.app.Application
import com.xiaoyu.service.XiaoyuAppGraph

class XiaoyuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val graph = XiaoyuAppGraph.init(this)
        graph.preferences.migrateKwsSensitivityIfNeeded()
    }
}
