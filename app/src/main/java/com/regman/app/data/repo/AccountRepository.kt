package com.regman.app.data.repo

import com.regman.app.data.db.AccountDao
import com.regman.app.data.db.AccountEntity
import com.regman.core.crypto.AccountCipher
import com.regman.core.engine.AccountDraft
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val cipher: AccountCipher,
) {
    fun observeAll() = dao.observeAll()

    suspend fun saveDraft(draft: AccountDraft, platform: String = "kiro") {
        dao.upsert(
            AccountEntity(
                id = UUID.randomUUID().toString(),
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

    suspend fun refreshQuota(a: AccountEntity) {
        // TODO: 调 PlatformPlugin.queryQuota 更新 plan/quotaJson/expiresAt/status
    }
}
