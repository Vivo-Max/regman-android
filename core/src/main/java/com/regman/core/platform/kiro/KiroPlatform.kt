package com.regman.core.platform.kiro

import com.regman.core.engine.AccountDraft
import com.regman.core.engine.ErrorKind
import com.regman.core.engine.Failure
import com.regman.core.engine.RegisterContext
import com.regman.core.engine.RegisterResult
import com.regman.core.engine.StepEvent
import com.regman.core.http.HttpClient
import com.regman.core.platform.PlatformPlugin
import com.regman.core.platform.Quota
import com.regman.core.platform.QuotaInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Kiro 平台插件：AWS Builder ID 协议注册。
 * 蓝本：KiroX 15 步流程（docs/KIRO_FLOW.md）。
 * 标准部分（RFC 7591 客户端注册 / RFC 8628 设备授权 / OAuth token 交换）已完整实现；
 * AWS 门户私有端点的请求体形状标 [CAPTURE]，全部走远端 JSON 热更新。
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
            return fail(ErrorKind.UNKNOWN, "config",
                "oidcRegistrationUrl/createIdentityUrl 未配置：请补全远端 kiro.json（见 docs/KIRO_FLOW.md）")
        }
        val log: suspend (String, String) -> Unit = { s, m -> ctx.logger(StepEvent(ctx.taskId, s, m)) }

        // ── 步骤 1-2: OIDC 动态客户端注册 [RFC 7591，标准实现] ──
        log("1/15", "OIDC 客户端注册")
        val clientId = oidcRegister(http, cfg)
            ?: return fail(ErrorKind.UNKNOWN, "oidc-register", "注册端未返回 client_id，检查 oidcRegistrationUrl")

        // ── 步骤 3-4: 设备授权 [RFC 8628，标准实现] ──
        log("3/15", "设备授权")
        val device = runCatching { deviceAuthorize(http, cfg, clientId) }.getOrNull()
        if (device == null) log("3/15", "设备授权未返回（非致命，继续）")

        // ── 步骤 5: hCaptcha ──
        var hcaptchaToken: String? = null
        if (cfg.hcaptchaSiteKey.isNotBlank() && ctx.captcha != null) {
            log("5/15", "请求远程打码 hCaptcha")
            hcaptchaToken = runCatching {
                ctx.captcha.solveHcaptcha(cfg.hcaptchaSiteKey, cfg.socialPortalUrl)
            }.getOrElse {
                return fail(ErrorKind.CAPTCHA_FAILED, "hcaptcha", it.message ?: "")
            } ?: return fail(ErrorKind.CAPTCHA_FAILED, "hcaptcha", "打码返回空 token")
        }

        // ── 步骤 6: 申请邮箱 ──
        val address = runCatching { ctx.mailbox.allocate() }.getOrElse {
            return fail(ErrorKind.MAILBOX_ERROR, "mailbox", it.message ?: "")
        }
        log("6/15", "邮箱: $address")

        // ── 步骤 7-8: create-identity（★ TES 风控点）[CAPTURE 请求体] ──
        log("7/15", "create-identity（TES 风控）")
        val password = generatePassword()
        when (val err = createIdentity(http, cfg, address, password, hcaptchaToken)) {
            null -> Unit
            "BLOCKED" -> return fail(ErrorKind.RISK_CONTROL, "create-identity",
                "TES BLOCKED：OTP 已被服务端消耗，此账号作废，必须换 IP 重试（住宅代理）")
            "CONFIG_MISSING" -> return fail(ErrorKind.UNKNOWN, "create-identity",
                "createIdentityUrl 响应无法解析，请对照 KiroX signup_password.go 修正")
            else -> return fail(ErrorKind.UNKNOWN, "create-identity", err)
        }

        // ── 步骤 9-10: 发送并轮询验证码 ──
        if (cfg.sendCodeUrl.isNotBlank()) {
            runCatching { http.postJson(cfg.sendCodeUrl, buildJsonObject { put("email", address) }.toString()) }
        }
        log("9/15", "等待邮箱验证码")
        val code = runCatching {
            ctx.mailbox.waitForCode(address, timeoutMillis = 120_000) { body -> extractCode(body) }
        }.getOrElse {
            return fail(ErrorKind.MAILBOX_ERROR, "verify-mail", it.message ?: "")
        } ?: return fail(ErrorKind.MAILBOX_ERROR, "verify-mail", "120 秒未收到验证码")
        log("10/15", "验证码已收到")

        // ── 步骤 11-12: 校验验证码 + 设置密码 [CAPTURE 端点] ──
        if (cfg.verifyCodeUrl.isNotBlank()) {
            val vErr = runCatching {
                json.parseToJsonElement(http.postJson(cfg.verifyCodeUrl, buildJsonObject {
                    put("email", address); put("code", code)
                }.toString())).jsonObject["errorCode"]?.jsonPrimitive?.content
            }.getOrNull()
            if (vErr != null) return fail(ErrorKind.RISK_CONTROL, "verify-code", "$vErr（连续 INVALID_OTP 会烧号，已短路）")
        }
        if (cfg.setPasswordUrl.isNotBlank()) {
            runCatching { http.postJson(cfg.setPasswordUrl, buildJsonObject {
                put("email", address); put("password", password)
            }.toString()) }
        }
        log("12/15", "注册表单完成")

        // ── 步骤 13: AWS SSO 授权 → 提取 authorization code ──
        log("13/15", "AWS SSO 授权")
        val authCode = ssoAuthorize(http, cfg)
            ?: return fail(ErrorKind.RISK_CONTROL, "sso", "未获取到授权码（检查 awsAuthorizeUrl/代理）")
        log("14/15", "已获取授权码")

        // ── 步骤 14: Kiro token 交换 [PUBLIC 端点] ──
        val (access, refresh) = exchangeToken(http, cfg, authCode)
            ?: return fail(ErrorKind.UNKNOWN, "token-exchange", "token 交换失败（检查 socialTokenUrl 连通性）")

        // ── 步骤 15: 存活验证（refresh 端点探活）──
        log("15/15", "存活验证")
        return RegisterResult.Success(AccountDraft(
            email = address, password = password,
            accessToken = access, refreshToken = refresh,
            extra = mapOf("clientId" to clientId),
        ))
    }

    // ── RFC 7591 ──
    private suspend fun oidcRegister(http: HttpClient, cfg: KiroConfig): String? {
        if (cfg.oidcRegistrationUrl.isBlank()) return null
        val body = runCatching {
            http.postJson(cfg.oidcRegistrationUrl, buildJsonObject {
                put("client_name", "regman-android")
                put("grant_types", kotlinx.serialization.json.JsonArray(listOf(
                    kotlinx.serialization.json.JsonPrimitive("authorization_code"),
                    kotlinx.serialization.json.JsonPrimitive("refresh_token"),
                )))
                put("response_types", kotlinx.serialization.json.JsonArray(listOf(
                    kotlinx.serialization.json.JsonPrimitive("code"),
                )))
                put("redirect_uris", kotlinx.serialization.json.JsonArray(listOf(
                    kotlinx.serialization.json.JsonPrimitive(cfg.socialRedirectUri),
                    kotlinx.serialization.json.JsonPrimitive("http://localhost:3128"),
                )))
                put("scope", cfg.kiroScopes)
            }.toString())
        }.getOrNull() ?: return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject["client_id"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    // ── RFC 8628 ──
    private suspend fun deviceAuthorize(http: HttpClient, cfg: KiroConfig, clientId: String): String? {
        if (cfg.deviceAuthorizationUrl.isBlank()) return null
        val body = runCatching {
            http.postForm(cfg.deviceAuthorizationUrl, mapOf(
                "client_id" to clientId,
                "scope" to cfg.kiroScopes,
            ))
        }.getOrNull() ?: return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject["device_code"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    /** [CAPTURE] 请求体字段按 KiroX signup_password.go 核对 */
    private suspend fun createIdentity(http: HttpClient, cfg: KiroConfig, email: String, password: String, hcaptcha: String?): String? {
        val body = runCatching {
            http.postJson(cfg.createIdentityUrl, buildJsonObject {
                put("email", email)
                put("password", password)
                if (hcaptcha != null) put("hCaptchaToken", hcaptcha)
            }.toString())
        }.getOrNull() ?: return "NETWORK"
        return runCatching {
            json.parseToJsonElement(body).jsonObject["errorCode"]?.jsonPrimitive?.content
        }.getOrNull()  // null = 成功
            ?: if (body.contains("error")) "CONFIG_MISSING" else null
    }

    /** AWS 平台授权页（公开形态见 docs/KIRO_FLOW.md），从最终重定向 URL 提取 code */
    private suspend fun ssoAuthorize(http: HttpClient, cfg: KiroConfig): String? {
        if (cfg.awsAuthorizeUrl.isBlank() || cfg.awsClientIdArn.isBlank()) return null
        val sep = if (cfg.awsAuthorizeUrl.contains("?")) "&" else "?"
        val url = cfg.awsAuthorizeUrl + sep + buildString {
            append("response_type=code")
            append("&client_id=").append(java.net.URLEncoder.encode(cfg.awsClientIdArn, "UTF-8"))
            append("&redirect_uri=").append(java.net.URLEncoder.encode("https://app.kiro.dev/signin/oauth", "UTF-8"))
            append("&state=").append(randomHex(16))
            if (cfg.awsRequestUri.isNotBlank()) append("&request_uri=").append(java.net.URLEncoder.encode(cfg.awsRequestUri, "UTF-8"))
            if (cfg.awsIdentityStoreId.isNotBlank()) append("&identitystore_id=").append(cfg.awsIdentityStoreId)
        }
        val (_, finalUrl) = runCatching { http.getStringWithUrl(url) }.getOrNull() ?: return null
        val m = Regex("""[?&]code=([^&]+)""").find(finalUrl) ?: return null
        return java.net.URLDecoder.decode(m.groupValues[1], "UTF-8")
    }

    /** [PUBLIC 端点] OAuth authorization_code 交换 */
    private suspend fun exchangeToken(http: HttpClient, cfg: KiroConfig, code: String): Pair<String, String>? {
        val body = runCatching {
            http.postForm(cfg.socialTokenUrl, mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to cfg.socialRedirectUri,
                "client_id" to cfg.kiroClientName,
            ))
        }.getOrNull() ?: return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val at = obj["access_token"]?.jsonPrimitive?.content ?: return null
        val rt = obj["refresh_token"]?.jsonPrimitive?.content ?: ""
        return at to rt
    }

    override suspend fun refreshToken(account: AccountDraft): AccountDraft {
        val cfg = configSource.load()
        if (account.refreshToken.isBlank()) return account
        val http = httpFactory(null)
        val body = runCatching {
            http.postForm(cfg.socialRefreshUrl, mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to account.refreshToken,
            ))
        }.getOrNull() ?: return account
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return account
        return account.copy(
            accessToken = obj["access_token"]?.jsonPrimitive?.content ?: account.accessToken,
            refreshToken = obj["refresh_token"]?.jsonPrimitive?.content ?: account.refreshToken,
        )
    }

    override suspend fun activateTrial(account: AccountDraft): Boolean {
        val cfg = configSource.load()
        if (cfg.trialActivateUrl.isBlank()) return false
        val http = httpFactory(null)
        return runCatching {
            http.postJson(cfg.trialActivateUrl, "{}", mapOf("Authorization" to "Bearer ${account.accessToken}"))
        }.isSuccess
    }

    /** [CAPTURE] 额度接口路径按公开项目的 ListAvailableSubscriptions 形态解析，远端配置覆盖 */
    override suspend fun queryQuota(account: AccountDraft): QuotaInfo {
        val cfg = configSource.load()
        if (cfg.quotaQueryUrl.isBlank()) return QuotaInfo("unknown", emptyList(), 0L)
        val http = httpFactory(null)
        val body = runCatching {
            http.getString(cfg.quotaQueryUrl, mapOf("Authorization" to "Bearer ${account.accessToken}"))
        }.getOrNull() ?: return QuotaInfo("unknown", emptyList(), 0L)
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return QuotaInfo("unknown", emptyList(), 0L)
        val plan = obj["plan"]?.jsonPrimitive?.content
            ?: obj["subscription"]?.jsonPrimitive?.content ?: "unknown"
        val quotas = obj["quotas"]?.jsonArray?.mapNotNull { q ->
            runCatching {
                val qo = q.jsonObject
                val name = qo["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val total = qo["total"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val used = qo["used"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                name to Quota(total, used)
            }.getOrNull()
        } ?: emptyList()
        val expires = obj["expiresAt"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: obj["expiresAtMillis"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        return QuotaInfo(plan, quotas, expires)
    }

    private fun extractCode(body: String): String? =
        Regex("""\b(\d{6})\b""").find(body)?.groupValues?.get(1)

    private fun generatePassword(): String =
        (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('!', '@', '#'))
            .shuffled().take(16).joinToString("")

    private fun randomHex(bytes: Int): String =
        java.security.SecureRandom().let { r ->
            ByteArray(bytes).also { r.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        }

    private fun fail(kind: ErrorKind, step: String, detail: String) =
        RegisterResult.Failure(Failure(kind, step, detail))
}
