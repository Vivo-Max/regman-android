# Kiro / AWS Builder ID 协议注册流程（公开项目参考汇总）

> 本文件汇总公开仓库中已验证的注册流程，作为 KiroPlatform 的实现蓝本。
> 所有引用项目均为开源，建议直接阅读其源码核对细节。

## 一、整体流程（KiroX 的 15 步）

来源：huey1in/KiroX（Go + tls-client，纯协议实现，Wails 桌面）。

```
OIDC 动态客户端注册 (RFC 7591)
  → 设备授权 (device_code)
  → hCaptcha 人机验证
  → 申请邮箱地址
  → create-identity 创建 Builder ID（★ TES 风控点）
  → 发送邮箱验证码
  → 轮询收码 (6 位数字)
  → 设置密码
  → 完成注册
  → AWS SSO 授权 (signin.aws/platform/authorize)
  → 用 auth code 到 Kiro desktop OAuth 端点换 token
  → 账号存活验证
```

KiroX 源码模块对照（直接按文件名读 Go 源码即可逐行翻译）：

| KiroX 文件 | 对应步骤 |
|---|---|
| internal/core/registrar.go | OIDC 客户端注册 + 初始授权 |
| internal/core/run.go | 15 步编排 |
| internal/core/signup_flow.go | 注册流程 + 邮箱验证码 |
| internal/core/signup_password.go | 身份创建 + 密码设置 |
| internal/core/auth.go | SSO 工作流 + 令牌获取 |
| internal/core/kiro_auth.go / kiro_exchange.go | Kiro 授权 + 第 15 步 token 交换 |
| internal/core/verify.go | 存活验证 |
| internal/crypto/ | JWE 加密、XXTEA（Kiro 请求体部分加密，移植时必需） |

## 二、已确认可用的公开端点 [PUBLIC]

来源：pi-kiro-provider 公开配置 + Kiro 官方文档。

| 用途 | 端点 |
|---|---|
| social 授权页 | https://prod.us-east-1.auth.desktop.kiro.dev/login |
| social token 交换 | https://prod.us-east-1.auth.desktop.kiro.dev/oauth/token |
| social token 刷新 | https://prod.us-east-1.auth.desktop.kiro.dev/refreshToken |
| social redirect_uri | kiro://kiro.kiroAgent/authenticate-success |
| portal | https://app.kiro.dev/signin |
| scopes | codewhisperer:completions / analysis / conversations |
| client | kiro-oauth-client (public) |
| AWS 平台授权页形态 | https://us-east-1.signin.aws/platform/authorize?callback_url=..&request_uri=..&identitystore_id=..&state=..&response_type=code&redirect_uri=https%3A%2F%2Fapp.kiro.dev%2Fsignin%2Foauth&client_id=arn%3Aaws%3Asso%3A%3A432677196278%3Aapplication%2Fssoins-XXX%2Fapl-XXX |

Kiro CLI 同样支持 Builder ID 的 device flow（官方文档确认），可作注册后登录的备选路径。

## 三、风控要点（kiro-register-en 实测结论）

1. **人机验证是 hCaptcha，不是 Turnstile**（打码服务用 YesCaptcha / Multibot 的 hCaptcha 任务类型）。
2. **AWS TES（Trust Evaluation Service）** 挡在 signin.aws / profile.aws 前面：
   - 可疑 IP 触发 `create-identity` 返回 `errorCode=BLOCKED`；
   - **OTP 被服务端消耗**：第一次被 block，重试直接 INVALID_OTP → 整个账号报废；
   - 必须住宅代理（住宅 IP 是硬要求，数据中心 IP 基本必死）；
   - TES 会画像 headless Chrome，无头特征（本项目的协议模式反而规避了这一点）。
3. Kiro 请求体部分使用 JWE/XXTEA 加密（KiroX 的 internal/crypto），移植时不能省略。
4. 连续 `INVALID_OTP` 要短路退出，避免烧号。

## 四、两个参考项目的路线差异

| | KiroX | kiro-register-en |
|---|---|---|
| 语言 | Go (tls-client) | Python (curl_cffi + Playwright) |
| 注册方式 | 纯协议 15 步 | 浏览器为主，协议辅助 |
| 适用 | **安卓移植的最佳蓝本** | 流程细节/风控结论的参考 |
| 打码 | 集成在流程内 | YesCaptcha / Multibot 可插拔 |

## 五、移植到安卓的对应关系

| KiroX (Go) | 本工程 (Kotlin) |
|---|---|
| tls-client | CronetHttpClient（见 FINGERPRINT_TEST.md 风险） |
| internal/task 并发调度 | engine/RegisterOrchestrator |
| internal/email 邮箱池 | mailbox/（IMAP/MoeMail 已就位） |
| internal/proxy 代理池 | proxy/ProxyPool |
| internal/crypto JWE/XXTEA | 待补：core/crypto/KiroCrypto.kt（BouncyCastle/原生实现） |
| frontend 日志推送 | StateFlow 日志流（已就位） |

## 六、待抓包确认项 [CAPTURE]

- OIDC issuer / registration / device_authorization / token 端点完整 URL
- create-identity 请求体与响应结构
- hCaptcha sitekey 与 token 注入位置
- AWS SSO authorize 的参数生成（state、request_uri 来源）
- Kiro API 额度/订阅接口路径（ListAvailableSubscriptions / CreateSubscriptionToken 等）

方法：桌面浏览器 DevTools / mitmproxy 走住宅代理抓 Kiro IDE 首次登录全过程，
或直接阅读 KiroX Go 源码（它已经做完了这一步）。
