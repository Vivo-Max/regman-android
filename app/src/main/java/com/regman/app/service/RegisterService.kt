package com.regman.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.regman.core.engine.RegisterOrchestrator
import com.regman.core.platform.PlatformRegistry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 前台 Service：承载批量注册长任务，防 Doze 杀进程 */
@AndroidEntryPoint
class RegisterService : Service() {

    @Inject lateinit var orchestrator: RegisterOrchestrator
    @Inject lateinit var registry: PlatformRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        startForeground(Notifications.NOTIF_ID, Notifications.running(this, "准备中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val platform = intent?.getStringExtra("platform") ?: "kiro"
        val count = intent?.getIntExtra("count", 1) ?: 1
        val concurrency = intent?.getIntExtra("concurrency", 1) ?: 1
        val plugin = registry.get(platform)
        if (plugin == null) { stopSelf(); return START_NOT_STICKY }

        scope.launch {
            val taskId = orchestrator.enqueue(plugin, count, concurrency)
            orchestrator.tasks.collect { ts ->
                val mine = ts.firstOrNull { it.taskId == taskId } ?: return@collect
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(Notifications.NOTIF_ID,
                    Notifications.running(this@RegisterService, "${mine.succeeded}/${mine.done}/${mine.total}"))
                if (!mine.running) { nm.cancel(Notifications.NOTIF_ID); stopSelf() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
