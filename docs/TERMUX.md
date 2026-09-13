# 在 Termux 中使用

## 可以做的
- `pkg install openjdk-17` 后，工程根目录执行 `./gradlew :core:build`
  → 纯 JVM 的 core 引擎模块可完整编译、跑单元测试，改引擎逻辑完全不用电脑。

## 做不到的（以及为什么）
- `assembleDebug` / 任何 APK 打包任务。
  Android SDK build-tools（aapt2、d8、zipalign、apksigner）官方只发布
  x86_64 Linux 二进制，手机 aarch64 无法执行。box64 模拟极慢，不推荐。

## 手机上出 APK 的推荐路径
1. **GitHub Actions（推荐）**：推送代码 → Actions 云端构建 → 下载 artifacts APK。
2. **AndroidIDE**：应用自带 aarch64 移植的 aapt2，可本地出包（体验一般）。
3. 电脑 Android Studio 同步 Termux 里的工程目录构建。
