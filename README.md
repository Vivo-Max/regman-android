# RegMan（安卓版）

协议模式批量注册 + 账号池管理器（安卓原生重构，对齐 any-auto-register 的核心能力）。

> ⚠️ 本工程为**可编译的工程骨架**：架构、模块边界、数据流、Kiro 15 步注册流程骨架均已就位。
> 部分请求细节标有 `[CAPTURE]`（见 docs/KIRO_FLOW.md）——优先直接阅读 KiroX Go 源码逐行翻译，
> 其次抓包补全。

## 模块

| 模块 | 说明 |
|---|---|
| `:core` | 纯 Kotlin 引擎：注册编排器、KiroPlatform（15 步流程骨架）、邮箱/打码/代理 Provider、RemoteConfig 热更新、TOTP。无 Android 依赖，可直接复用到服务端形态 |
| `:app` | Compose UI（概览/账号池/任务队列/注册/邮箱池/代理池/设置）、Room+SQLCipher、Keystore 加密、Cronet HTTP、前台 Service、WorkManager 生命周期巡检 |

## 注册流程蓝本（已回填真实流程）

来源全部为公开项目（详见 docs/KIRO_FLOW.md）：
- **huey1in/KiroX**：AWS Builder ID 纯协议 15 步注册（OIDC 注册 → 设备授权 → 邮箱验证 → 密码设置 → SSO → Kiro Token 交换）
- **GALIAIS/k_i_r_o-register**：hCaptcha 打码、AWS TES 风控实测结论（BLOCKED + OTP 消耗）、住宅代理硬要求
- **pi-kiro-provider**：Kiro desktop social OAuth 端点（已内置进 kiro.config.json [PUBLIC] 字段）

## 与 any-auto-register 的能力对照

| 能力 | 状态 |
|---|---|
| 协议模式注册 | ✅ 15 步骨架 + 真实端点，细节标 [CAPTURE] 待补 |
| 浏览器/无头模式 | ❌ 不可移植，已裁剪 |
| 本地打码 Solver | ❌ 不可移植，远程 hCaptcha 打码（YesCaptcha/Multibot 任务类型） |
| 邮箱池（IMAP/MoeMail/HTTP API） | ✅ |
| 代理池（加权轮询/失败禁用） | ✅ |
| 账号池 + 额度卡片 UI | ✅ |
| Token 续期/到期预警 | ✅（socialRefreshUrl 已内置 [PUBLIC]） |
| 配置热更新 | ✅（RemoteConfig + 远端 JSON） |

## 构建

Android Studio 打开（AGP 8.5 / Kotlin 2.0 / JDK 17），minSdk 26+。

## 首要任务（不要跳过）

1. docs/FINGERPRINT_TEST.md —— Cronet TLS 指纹 + AWS TES 验证（生死题）
2. 按 docs/KIRO_FLOW.md 对照 KiroX 源码补全 `[CAPTURE]` 步骤
3. 补 core/crypto/KiroCrypto.kt（JWE/XXTEA，Kiro 请求体加密）

## 合规提醒

批量注册违反目标平台服务条款；仅供学习研究。AGPL 项目仅作架构参考，未复制其代码。
