# 钟表店密室

PICO Vibe Lab 原作品版本更新。版本 0.5.2 / versionCode 8。Android 包名 `com.vrgames.clockshop`。

本版变更见[更新说明](更新说明.md)。正常构建使用仓库内已落盘的代码和素材，不需要模型生成服务。

## 构建

仓库根目录是 Gradle 工程。使用 JDK 21 和 Android SDK 35：

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Windows 使用 gradlew.bat。运行代码、资源和单元测试位于 app/src。发布需沿用原应用签名；外部签名可通过 VRGAMES_SIGNING 指定，无此配置时生成未签名 Release。签名凭据和生成服务密钥不包含在仓库中。

本次只准备原作品版本更新，商店图标、海报和截图沿用。
