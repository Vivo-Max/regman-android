# 架构说明

## 分层

UI(Compose) → ViewModel(StateFlow) → Repository/Room → Core Engine(纯 Kotlin)

Core Engine 不依赖 Android，未来可原样搬到 Ktor 服务端走"远程引擎 + App 客户端"形态。

## 关键决策

1. **Cronet 而非裸 OkHttp**：TLS 指纹与 Android Chrome 同源。sec-ch-ua/UA 等应用层头
   由 ClientFingerprint 驱动，可热更新。
2. **每代理独立 CronetEngine**：Cronet 无 per-request proxy，用 engine 粒度隔离代理
   （代理数量大时注意 engine 内存，可考虑连接池复用策略）。
3. **敏感字段三层防护**：Keystore AES/GCM 字段级加密 → SQLCipher 库级加密 → 导出需用户确认。
4. **平台参数热更新**：平台改版只改远端 JSON（sitekey/client_id/endpoints），不发版。
5. **后台策略**：批量注册必须前台 Service；周期巡检用 WorkManager（下限 15min 足够）。

## 注册流程编排

申请邮箱 → 选代理(加权轮询) → OIDC 授权 → [Turnstile → 远程打码] → 提交注册
→ 轮询收验证码 → 换 Token → 激活试用(Start) → 查询额度 → 入库(加密)
失败按 PROXY_BLOCKED / CAPTCHA_FAILED / MAILBOX_ERROR / RISK_CONTROL 归因，
驱动概览统计与代理池权重。
