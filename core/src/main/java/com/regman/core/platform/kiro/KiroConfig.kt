package com.regman.core.platform.kiro

import com.regman.core.engine.RemoteConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Kiro 注册配置。
 * 标注 [PUBLIC] 的字段取自公开项目配置（pi-kiro-provider / KiroX / kiro-register-en），可直接使用；
 * 标注 [CAPTURE] 的字段随平台版本变化，需对照 KiroX 源码或抓包确认。
 */
@Serializable
data class KiroConfig(
    // ── AWS Builder ID / OIDC 注册链路 [CAPTURE，参考 KiroX 15 步流程] ──
    val oidcIssuer: String = "",
    val oidcRegistrationUrl: String = "",
    val deviceAuthorizationUrl: String = "",
    val tokenUrl: String = "",
    /** AWS 平台授权页，形如 https://us-east-1.signin.aws/platform/authorize */
    val awsAuthorizeUrl: String = "",
    /** Builder ID 身份创建接口（TES 风控前置，可能返回 errorCode=BLOCKED） */
    val createIdentityUrl: String = "",

    // ── Kiro 桌面端 social OAuth [PUBLIC] ──
    val socialAuthorizeUrl: String = "https://prod.us-east-1.auth.desktop.kiro.dev/login",
    val socialTokenUrl: String = "https://prod.us-east-1.auth.desktop.kiro.dev/oauth/token",
    val socialRefreshUrl: String = "https://prod.us-east-1.auth.desktop.kiro.dev/refreshToken",
    val socialRedirectUri: String = "kiro://kiro.kiroAgent/authenticate-success",
    val socialPortalUrl: String = "https://app.kiro.dev/signin",
    val kiroClientName: String = "kiro-oauth-client",
    val kiroScopes: String = "codewhisperer:completions codewhisperer:analysis codewhisperer:conversations",

    // ── 人机验证 / 业务接口 ──
    /** [CAPTURE] kiro-register-en 确认是 hCaptcha（不是 Turnstile） */
    val hcaptchaSiteKey: String = "",
    /** [CAPTURE] Kiro API 基址（额度/订阅查询） */
    val apiBase: String = "",
    val trialActivateUrl: String = "",
    val quotaQueryUrl: String = "",
)

class KiroConfigSource(private val remote: RemoteConfig, private val url: String) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun str(obj: kotlinx.serialization.json.JsonObject, k: String) =
        obj[k]?.jsonPrimitive?.content ?: ""

    suspend fun load(): KiroConfig {
        val obj = remote.get(url).jsonObject
        return KiroConfig(
            oidcIssuer = str(obj, "oidcIssuer"),
            oidcRegistrationUrl = str(obj, "oidcRegistrationUrl"),
            deviceAuthorizationUrl = str(obj, "deviceAuthorizationUrl"),
            tokenUrl = str(obj, "tokenUrl"),
            awsAuthorizeUrl = str(obj, "awsAuthorizeUrl"),
            createIdentityUrl = str(obj, "createIdentityUrl"),
            socialAuthorizeUrl = str(obj, "socialAuthorizeUrl").ifEmpty { KiroConfig().socialAuthorizeUrl },
            socialTokenUrl = str(obj, "socialTokenUrl").ifEmpty { KiroConfig().socialTokenUrl },
            socialRefreshUrl = str(obj, "socialRefreshUrl").ifEmpty { KiroConfig().socialRefreshUrl },
            socialRedirectUri = str(obj, "socialRedirectUri").ifEmpty { KiroConfig().socialRedirectUri },
            socialPortalUrl = str(obj, "socialPortalUrl").ifEmpty { KiroConfig().socialPortalUrl },
            kiroClientName = str(obj, "kiroClientName").ifEmpty { KiroConfig().kiroClientName },
            kiroScopes = str(obj, "kiroScopes").ifEmpty { KiroConfig().kiroScopes },
            hcaptchaSiteKey = str(obj, "hcaptchaSiteKey"),
            apiBase = str(obj, "apiBase"),
            trialActivateUrl = str(obj, "trialActivateUrl"),
            quotaQueryUrl = str(obj, "quotaQueryUrl"),
        )
    }
}
