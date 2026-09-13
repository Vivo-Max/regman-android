package com.regman.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.regman.app.data.repo.PoolSync
import com.regman.app.data.settings.SettingsRepository
import com.regman.app.service.LifecycleWorker
import com.regman.core.captcha.YesCaptcha
import com.regman.core.engine.CaptchaHolder
import com.regman.core.http.HttpClient
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltAndroidApp
class RegManApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var captchaHolder: CaptchaHolder
    @Inject lateinit var http: HttpClient
    @Inject lateinit var poolSync: PoolSync

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        LifecycleWorker.schedule(this)
        // 设置页保存打码 key → 注入引擎
        appScope.launch {
            settings.captchaKey.collect { key ->
                captchaHolder.current = key.ifBlank { null }?.let { YesCaptcha(http, it) }
            }
        }
        // 数据库 → 代理池/邮箱池
        poolSync.attach(appScope)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
