package com.regman.core.platform.kiro

import com.regman.core.engine.AccountDraft
import com.regman.core.engine.ErrorKind
import com.regman.core.engine.Failure
import com.regman.core.engine.RegisterContext
import com.regman.core.engine.RegisterResult
import com.regman.core.engine.StepEvent
import com.regman.core.http.HttpClient
import com.regman.core.platform.PlatformPlugin
import com.regman.core.platform.QuotaInfo
import kotlinx.serialization.json.Json

/**
 * Kiro 平台插件：AWS Builder ID 协议注册（对齐公开项目 KiroX 的 15 步流程）。
 *
 * 参考来源（均为公开仓库，见 docs/KIRO_FLOW.md）：
 *  - huey1in/KiroX (Go/tls-client, 纯协议)：
 *    OIDC 注册 → 设备授权 → 邮箱验证 → 密码设置 → SSO → Kiro Token 交换
 *  - GALIAIS/k_i_r_o-register (curl_cffi + Playwright 混合)：
 *    create-identity 前置 AWS TES 风控，可能 errorCode=BLOCKED
 *  - pi-kiro-provider 公开配置：social OAuth 端点
 *
 * 每个 [CAPTURE] 步骤需对照参考项目源码/抓包补全请求细节；
 * 所有可变参数走 KiroConfig + 远端 JSON 热更新。
 */
class KiroPlatform(
    private val httpFactory: (com.regman.core.proxy.ProxyEntry?) -> HttpClient,
    private val configSource: KiroConfigSource,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : PlatformPlugin {

    override val name = "kiro"
    override val displayName = "Kiro"

    override suspend fun register(ctx: RegisterContext): RegisterResult {
        val http = httpFactory(ctx.proxy)
        val cfg = configSource.load()
        if (cfg.oidcRegistrationUrl.isEmpty() || cfg.createIdentityUrl.isEmpty()) {
            return RegisterResult.Failure(Failure(ErrorKind.UNKNOWN, "config",
                "OIDC/身份创建端点未配置：请对照 KiroX 源码补全远端 kiro.json（见 docs/KIRO_FLOW.md）"))
        }

        // ── 步骤 1-2: OIDC 动态客户端注册 [CAPTURE，参考 KiroX registrar.go] ──
        ctx.logger(StepEvent(ctx.taskId, "1/15", "OIDC 客户端注册"))
        // POST cfg.oidcRegistrationUrl (RFC 7591: client_name, grant_types, redirect_uris, scope)
        // → 保存 client_id / client_secret（若有）

        // ── 步骤 3-4: 设备授权 [CAPTURE] ──
        ctx.logger(StepEvent(ctx.taskId, "3/15", "申请设备授权码"))
        // POST cfg.deviceAuthorizationUrl (device_code flow, 参考 AWS Builder ID device flow)

        // ── 步骤 5: hCaptcha（kiro-register-en 确认是 hCaptcha） ──
        var hcaptchaToken: String? = null
        if (cfg.hcaptchaSiteKey.isNotEmpty() && ctx.captcha != null) {
            ctx.logger(StepEvent(ctx.taskId, "5/15", "请求远程打码 hCaptcha"))
            hcaptchaToken = runCatching {
                ctx.captcha.solveHcaptcha(cfg.hcaptchaSiteKey, cfg.socialPortalUrl)
            }.getOrElse {
                return RegisterResult.Failure(Failure(ErrorKind.CAPTCHA_FAILED, "hcaptcha", it.message ?: ""))
            } ?: return RegisterResult.Failure(Failure(ErrorKind.CAPTCHA_FAILED, "hcaptcha", "空 token"))
        }

        // ── 步骤 6: 申请邮箱 ──
        val address = runCatching { ctx.mailbox.allocate() }.getOrElse {
            return RegisterResult.Failure(Failure(ErrorKind.MAILBOX_ERROR, "mailbox", it.message ?: ""))
        }
        ctx.logger(StepEvent(ctx.taskId, "6/15", "邮箱: $address"))

        // ── 步骤 7-8: 创建 Builder ID 身份（TES 风控点）[CAPTURE] ──
        ctx.logger(StepEvent(ctx.taskId, "7/15", "create-identity（TES 风控）"))
        // POST cfg.createIdentityUrl
        // ⚠️ TES 拦截时返回 errorCode=BLOCKED，且 OTP 被服务端消耗 → 必须换 IP 重试；
        //    连续 INVALID_OTP 要短路，避免烧号（kiro-register-en 实测结论）

        // ── 步骤 9-10: 发送并轮询邮箱验证码 ──
        // TODO(抓包补全): 触发发信请求
        ctx.logger(StepEvent(ctx.taskId, "9/15", "等待邮箱验证码"))
        val code = runCatching {
            ctx.mailbox.waitForCode(address, timeoutMillis = 120_000) { body -> extractCode(body) }
        }.getOrElse {
            return RegisterResult.Failure(Failure(ErrorKind.MAILBOX_ERROR, "verify-mail", it.message ?: ""))
        } ?: return RegisterResult.Failure(Failure(ErrorKind.MAILBOX_ERROR, "verify-mail", "超时未收到验证码"))
        ctx.logger(StepEvent(ctx.taskId, "10/15", "验证码已收到"))

        // ── 步骤 11-12: 设置密码 / 完成注册 [CAPTURE，参考 KiroX signup_password.go] ──
        val password = generatePassword()

        // ── 步骤 13: AWS SSO 授权 [CAPTURE] ──
        ctx.logger(StepEvent(ctx.taskId, "13/15", "AWS SSO 授权"))
        // GET cfg.awsAuthorizeUrl?...&redirect_uri=https://app.kiro.dev/signin/oauth&response_type=code
        // 携带 state/cookie，parse redirect → authorization code

        // ── 步骤 14: Kiro Token 交换 [CAPTURE] ──
        ctx.logger(StepEvent(ctx.taskId, "14/15", "Kiro Token 交换"))
        // POST cfg.socialTokenUrl (grant_type=authorization_code, redirect_uri=cfg.socialRedirectUri)

        // ── 步骤 15: 账号存活验证 ──
        ctx.logger(StepEvent(ctx.taskId, "15/15", "存活验证"))

        val draft = AccountDraft(
            email = address,
            password = password,
            accessToken = "",     // TODO(步骤14)
            refreshToken = "",    // TODO(步骤14)
            extra = mapOf("hcaptcha" to (hcaptchaToken != null).toString()),
        )
        return RegisterResult.Success(draft)
    }

    override suspend fun refreshToken(account: AccountDraft): AccountDraft {
        val cfg = configSource.load()
        val http = httpFactory(null)
        // POST cfg.socialRefreshUrl [PUBLIC 端点, body 格式 CAPTURE]
        return account
    }

    override suspend fun activateTrial(account: AccountDraft): Boolean {
        val cfg = configSource.load()
        // TODO(抓包补全): 对应截图中的 "Start"（开通试用）
        // 参考 kiro-register-en 的 Pro 订阅自动化（Stripe Checkout 流程）
        return false
    }

    override suspend fun queryQuota(account: AccountDraft): QuotaInfo {
        val cfg = configSource.load()
        // 参考公开项目确认的 Kiro 后端接口形态：
        //   ListAvailableSubscriptions / CreateSubscriptionToken / ListAvailableModels
        // 路径形如 cfg.apiBase + "/..."（[CAPTURE]，参考 chaogei/Kiro-account-manager）
        return QuotaInfo(plan = "unknown", quotas = emptyList(), expiresAtMillis = 0L)
    }

    private fun extractCode(body: String): String? =
        Regex("""\b(\d{6})\b""").find(body)?.groupValues?.get(1)

    private fun generatePassword(): String =
        (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('!', '@', '#'))
            .shuffled().take(16).joinToString("")
}
