package com.regman.app.data.repo

import com.regman.app.data.db.AccountDao
import com.regman.app.data.db.AccountEntity
import com.regman.core.crypto.AccountCipher
import com.regman.core.engine.AccountDraft
import com.regman.core.platform.PlatformRegistry
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val cipher: AccountCipher,
    private val registry: PlatformRegistry,
) {
    fun observeAll() = dao.observeAll()

    suspend fun saveDraft(draft: AccountDraft, platform: String = "kiro") {
        dao.upsert(
            AccountEntity(
                id = java.util.UUID.randomUUID().toString(),
                platform = platform,
                email = draft.email,
                passwordCipher = cipher.encrypt(draft.password),
                accessTokenCipher = cipher.encrypt(draft.accessToken),
                refreshTokenCipher = cipher.encrypt(draft.refreshToken),
            )
        )
    }

    suspend fun delete(a: AccountEntity) = dao.delete(a)

    suspend fun exportJson(): String {
        // TODO: 明文导出（用户确认后）
        return "[]"
    }

    /** 查询额度并更新卡片（账号池"刷新额度"按钮） */
    suspend fun refreshQuota(a: AccountEntity) {
        val plugin = registry.get(a.platform) ?: return
        val draft = a.toDraft()
        val info = runCatching { plugin.queryQuota(draft) }.getOrNull() ?: return
        dao.upsert(a.copy(
            plan = info.plan,
            quotaJson = buildJsonArray {
                info.quotas.forEach { (name, q) ->
                    add(buildJsonObject { put("name", name); put("total", q.total); put("used", q.used) })
                }
            }.toString(),
            expiresAt = info.expiresAtMillis,
            status = if (a.expiresAt in 1 until System.currentTimeMillis() + 24 * 3600_000L) "EXPIRED_SOON" else "ACTIVE",
        ))
    }

    /** 生命周期巡检：续期 + 刷新额度 + 状态标记（LifecycleWorker 调用） */
    suspend fun maintainAll() {
        dao.all().forEach { a ->
            val plugin = registry.get(a.platform) ?: return@forEach
            var draft = a.toDraft()
            if (draft.refreshToken.isNotBlank()) {
                draft = runCatching { plugin.refreshToken(draft) }.getOrNull() ?: draft
                dao.upsert(a.copy(
                    accessTokenCipher = cipher.encrypt(draft.accessToken),
                    refreshTokenCipher = cipher.encrypt(draft.refreshToken),
                ))
            }
            refreshQuota(a)
        }
    }

    private fun AccountEntity.toDraft() = AccountDraft(
        email = email,
        password = runCatching { cipher.decrypt(passwordCipher) }.getOrDefault(""),
        accessToken = runCatching { cipher.decrypt(accessTokenCipher) }.getOrDefault(""),
        refreshToken = runCatching { cipher.decrypt(refreshTokenCipher) }.getOrDefault(""),
    )
}
