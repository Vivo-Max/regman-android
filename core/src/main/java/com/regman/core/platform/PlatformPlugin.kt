package com.regman.core.platform

import com.regman.core.engine.AccountDraft
import com.regman.core.engine.RegisterContext
import com.regman.core.engine.RegisterResult

interface Quota(val total: Long, val used: Long)

data class QuotaInfo(val plan: String, val quotas: List<Pair<String, Quota>>, val expiresAtMillis: Long)

/** 平台插件：与 any-auto-register 的 platforms/ 插件思想对齐 */
interface PlatformPlugin {
    val name: String
    val displayName: String

    suspend fun register(ctx: RegisterContext): RegisterResult
    suspend fun refreshToken(account: AccountDraft): AccountDraft
    suspend fun queryQuota(account: AccountDraft): QuotaInfo
    /** 对应截图中的 "Start"：激活免费试用/套餐 */
    suspend fun activateTrial(account: AccountDraft): Boolean
}
