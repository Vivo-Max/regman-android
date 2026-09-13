package com.regman.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.regman.app.data.repo.AccountRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 生命周期巡检：Token 续期(12h) + 额度查询/失效检测(6h)。
 * WorkManager 周期下限 15 分钟；国产 ROM 需引导关闭电池优化。
 */
@HiltWorker
class LifecycleWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: AccountRepository,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result =
        runCatching { repo.maintainAll(); Result.success() }
            .getOrElse { Result.retry() }

    companion object {
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            wm.enqueueUniquePeriodicWork(
                "lifecycle-maintain", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LifecycleWorker>(12, TimeUnit.HOURS).build()
            )
        }
    }
}
