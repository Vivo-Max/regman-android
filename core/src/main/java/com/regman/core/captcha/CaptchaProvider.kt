package com.regman.core.captcha

/**
 * 远程打码服务抽象。
 * 注意：公开项目（kiro-register-en）确认 Kiro/AWS Builder ID 注册流用的是 hCaptcha，
 * 不是 Turnstile；Turnstile 保留给将来接入其他平台（ChatGPT 等）。
 */
interface CaptchaProvider {
    val name: String
    suspend fun solveHcaptcha(siteKey: String, pageUrl: String): String?
    suspend fun solveTurnstile(siteKey: String, pageUrl: String): String? = null
}
