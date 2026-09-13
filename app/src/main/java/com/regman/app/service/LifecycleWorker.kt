package com.regman.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 生命周期巡检：Token 续期(12h) / 有效性检测+额度查询(6h) / 到期预警。
 * WorkManager 周期下限 15 分钟，满足需求；国产 ROM 需引导用户关闭电池优化。
 */
@HiltWorker
class LifecycleWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // TODO: accountRepo.all() → platformRegistry.get(platform).refreshToken / queryQuota
        //       失效标记 invalid；到期 <24h 标记 EXPIRED_SOON 并触发通知
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            wm.enqueueUniquePeriodicWork(
                "lifecycle-refresh", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LifecycleWorker>(12, TimeUnit.HOURS).build()
            )
            wm.enqueueUniquePeriodicWork(
                "lifecycle-check", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestRequestCompat(6)
            )
        }

        private fun PeriodicWorkRequestRequestCompat(hours: Long) =
            PeriodicWorkRequestBuilder<LifecycleWorker>(hours, TimeUnit.HOURS).build()
    }
}
