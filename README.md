# 钟表店密室 · ClockShop

PICO Spatial SDK 游戏，版本 0.1 / versionCode 1。

在四个相连的钟表铺房间中拨钟、查看线索、修复机关，找回四枚怀表零件。支持分步提示和进度保存。

全沉浸 VR 密室，以眼手指向、捏合和托盘操作探索虚拟房间。

游戏逻辑与素材均在本地，不需要业务服务器。以系统手势和眼手/手势射线交互，不依赖手柄。

## 构建

Gradle 工程根目录即本仓库根。需要 JDK 21 和 Android SDK 35。模型、纹理、音频、图标均随源码提供；构建不需要调用生成模型。

```sh
./gradlew testDebugUnitTest assembleRelease --no-daemon --max-workers=1
```

Windows 使用 gradlew.bat。通过 ANDROID_HOME 指定 SDK；不包含本机配置、访问令牌或签名私钥。产物 app/build/outputs/apk/release/app-release-unsigned.apk 供平台签名。

代码许可见 LICENSE，素材来源见 THIRD_PARTY_NOTICES.md。AI 编程工具和模型按作者确认：Claude Code + Claude Opus 5.5。
