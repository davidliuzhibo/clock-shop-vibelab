# 钟表店密室

PICO Vibe Lab 投稿源码，版本 0.4.1，versionCode 5，Android 包名 com.vrgames.clockshop。变更内容见 [更新说明](更新说明.md)。

本仓库根目录为 Gradle 工程。使用 JDK 21 和 Android SDK 35，设置 JAVA_HOME 和 ANDROID_HOME 后运行以下命令。Windows 使用 gradlew.bat。

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

运行时素材在 app/src/main/assets。外部开发签名为可选输入，无签名配置时可生成 app-release-unsigned.apk。
