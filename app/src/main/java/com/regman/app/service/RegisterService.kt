package com.regman.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

/**
 * 前台 Service：承载批量注册长任务，防 Doze/后台限制杀进程。
 * 生命周期巡检等周期任务走 LifecycleWorker（WorkManager）。
 */
@AndroidEntryPoint
class RegisterService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        startForeground(Notifications.NOTIF_ID, Notifications.running(this, "准备中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: 从 intent 读取 platform/count/concurrency，经 RegisterOrchestrator.enqueue 执行，
        //       并在这里更新通知进度（scope.launch { orchestrator.tasks.collect { ... } }）
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
