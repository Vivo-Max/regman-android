# 指纹验证实验（动工前必须完成）

目的：验证 Android Cronet 的 TLS 指纹能否通过 AWS TES / Cloudflare 风控。
这是整个项目成立与否的生死题，先用 200 行原型验证，再投入 UI 开发。

## 已知结论（来自公开项目实测，先内化再实验）

1. Kiro 注册流的人机验证是 **hCaptcha**（kiro-register-en）。
2. AWS 在 signin.aws / profile.aws 前有 **TES（Trust Evaluation Service）**：
   - 可疑 IP → `create-identity` 返回 `errorCode=BLOCKED`，且 **OTP 被服务端消耗**（账号报废）；
   - 住宅代理是硬要求，数据中心 IP 基本必死；
   - 协议模式（无 headless 特征）反而比无头浏览器更有利。
3. Kiro 请求体部分 JWE/XXTEA 加密（KiroX 的 internal/crypto）。

## 实验步骤

1. 新建最小 Android 工程，仅依赖 cronet-embedded。
2. 用 CronetHttpClient（见 app 模块参考实现）请求：
   - `https://www.cloudflare.com/cdn-cgi/trace` —— 观察是否出现 `challenge` 标记
   - `https://tls.peet.ws/api/all`（或同类指纹检测站）—— 对比桌面 Chrome 的 JA3/JA4、HTTP/2 指纹
3. **关键实验**：住宅代理下调用 AWS TES 前置接口（create-identity 或其探测端点），
   观察是否返回 BLOCKED；同代理对照桌面 curl_cffi 的表现。
4. 对照 KiroX Go 源码（docs/KIRO_FLOW.md 有模块对照表），把 15 步流程逐条翻译成 Kotlin。

## 判定标准

- Cloudflare trace 无 challenge + TES 不 BLOCK → 继续
- TES BLOCK 但 Cloudflare 通过 → 换代理池策略（住宅池加大、降低并发）后复测
- 均失败 → B 计划：core 模块原样搬到服务器（Ktor），App 退化为客户端
  （core 无 Android 依赖，搬迁成本≈0，这正是该分层的原因）

## 风险记录

- Kiro/AWS 随时可能改版（换 sitekey、加参数）→ RemoteConfig 热更新机制应对
- 国产 ROM 杀后台 → 前台 Service + 电池优化白名单引导
