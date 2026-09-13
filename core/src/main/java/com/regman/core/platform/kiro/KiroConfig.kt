package com.regman.core.platform.kiro

import com.regman.core.engine.RemoteConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Kiro 注册配置。
 * [PUBLIC] 字段取自公开项目，可直接使用；[CAPTURE] 字段需对照 KiroX 源码或抓包确认。
 */
@Serializable
data class KiroConfig(
    // ── AWS Builder ID / OIDC 注册链路 [CAPTURE] ──
    val oidcIssuer: String = "",
    val oidcRegistrationUrl: String = "",
    val deviceAuthorizationUrl: String = "",
    val tokenUrl: String = "",
    /** 发验证码（注册门户）[CAPTURE] */
    val sendCodeUrl: String = "",
    /** 校验邮箱验证码 [CAPTURE] */
    val verifyCodeUrl: String = "",
    /** 设置密码 [CAPTURE] */
    val setPasswordUrl: String = "",
    /** AWS 平台授权页，形如 https://us-east-1.signin.aws/platform/authorize */
    val awsAuthorizeUrl: String = "",
    /** Builder ID 身份创建接口（TES 风控前置，可能 errorCode=BLOCKED） */
    val createIdentityUrl: String = "",
    /** AWS SSO authorize 参数 [CAPTURE] */
    val awsClientIdArn: String = "",
    val awsRequestUri: String = "",
    val awsIdentityStoreId: String = "",

    // ── Kiro 桌面端 social OAuth [PUBLIC] ──
    val socialAuthorizeUrl: String = "https://prod.us-east-1.auth.desktop.kiro.dev/login",
    val socialTokenUrl: String = "https://prod.us-east-1.auth.desktop.kiro.dev/oauth/token",
    val socialRefreshUrl: String = "https://prod.us-east-1.auth.desktop.kiro.dev/refreshToken",
    val socialRedirectUri: String = "kiro://kiro.kiroAgent/authenticate-success",
    val socialPortalUrl: String = "https://app.kiro.dev/signin",
    val kiroClientName: String = "kiro-oauth-client",
    val kiroScopes: String = "codewhisperer:completions codewhisperer:analysis codewhisperer:conversations",

    // ── 人机验证 / 业务接口 ──
    /** [CAPTURE] kiro-register-en 确认是 hCaptcha */
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
        val def = KiroConfig()
        return KiroConfig(
            oidcIssuer = str(obj, "oidcIssuer"),
            oidcRegistrationUrl = str(obj, "oidcRegistrationUrl"),
            deviceAuthorizationUrl = str(obj, "deviceAuthorizationUrl"),
            tokenUrl = str(obj, "tokenUrl"),
            sendCodeUrl = str(obj, "sendCodeUrl"),
            verifyCodeUrl = str(obj, "verifyCodeUrl"),
            setPasswordUrl = str(obj, "setPasswordUrl"),
            awsAuthorizeUrl = str(obj, "awsAuthorizeUrl"),
            createIdentityUrl = str(obj, "createIdentityUrl"),
            awsClientIdArn = str(obj, "awsClientIdArn"),
            awsRequestUri = str(obj, "awsRequestUri"),
            awsIdentityStoreId = str(obj, "awsIdentityStoreId"),
            socialAuthorizeUrl = str(obj, "socialAuthorizeUrl").ifEmpty { def.socialAuthorizeUrl },
            socialTokenUrl = str(obj, "socialTokenUrl").ifEmpty { def.socialTokenUrl },
            socialRefreshUrl = str(obj, "socialRefreshUrl").ifEmpty { def.socialRefreshUrl },
            socialRedirectUri = str(obj, "socialRedirectUri").ifEmpty { def.socialRedirectUri },
            socialPortalUrl = str(obj, "socialPortalUrl").ifEmpty { def.socialPortalUrl },
            kiroClientName = str(obj, "kiroClientName").ifEmpty { def.kiroClientName },
            kiroScopes = str(obj, "kiroScopes").ifEmpty { def.kiroScopes },
            hcaptchaSiteKey = str(obj, "hcaptchaSiteKey"),
            apiBase = str(obj, "apiBase"),
            trialActivateUrl = str(obj, "trialActivateUrl"),
            quotaQueryUrl = str(obj, "quotaQueryUrl"),
        )
    }
}
