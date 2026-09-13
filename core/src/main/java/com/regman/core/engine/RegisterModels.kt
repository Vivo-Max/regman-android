package com.regman.core.engine

import com.regman.core.captcha.CaptchaProvider
import com.regman.core.mailbox.MailboxProvider
import com.regman.core.proxy.ProxyEntry

data class AccountDraft(
    val email: String,
    val password: String,
    val accessToken: String = "",
    val refreshToken: String = "",
    val extra: Map<String, String> = emptyMap(),
)

sealed interface RegisterResult {
    data class Success(val draft: AccountDraft) : RegisterResult
    data class Failure(val failure: com.regman.core.engine.Failure) : RegisterResult
}

/** 单次注册的完整上下文：由编排器组装，平台插件只面向本接口编程 */
data class RegisterContext(
    val taskId: String,
    val mailbox: MailboxProvider,
    val captcha: CaptchaProvider?,
    val proxy: ProxyEntry?,
    val fingerprint: com.regman.core.http.ClientFingerprint,
    val logger: suspend (StepEvent) -> Unit,
)
