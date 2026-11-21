# Android 构建与发布

[项目首页](../README.zh-CN.md) · [English README](../README.md) · [Supabase 接入](../supabase/README.zh-CN.md)

原生 Android 版使用 Kotlin、Jetpack Compose 和 Miuix，应用 ID 为 `com.pomodorotimer.app`。最低支持 Android 8.0（API 26）。正式 APK 只包含 `arm64-v8a`。

## 开发环境

- JDK 21，用于运行 Gradle。Android 模块的 Kotlin JVM toolchain 为 17，构建机器也需提供 JDK 17。
- Gradle 9.5.0。仓库使用外部 Gradle，可通过 Android Studio 的 Gradle 设置指定本机安装。
- Android SDK Platform `android-37.0` 与 Build Tools `37.0.0`。在 Android Studio 的 SDK Manager 安装，或使用 SDK 命令行工具。

```bash
sdkmanager "platforms;android-37.0" "build-tools;37.0.0"
cd android
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。可用 Android Studio 运行，或在连接开发设备后执行 `adb install -r app/build/outputs/apk/debug/app-debug.apk`。Android 要求相同应用 ID 的覆盖更新使用同一签名。

构建接入云同步和背景音乐时，使用 `ORG_GRADLE_PROJECT_SUPABASE_URL` 和 `ORG_GRADLE_PROJECT_SUPABASE_ANON_KEY`。后者可传 Supabase publishable key。账号、数据库权限和曲库接入见 [Supabase 接入指南](../supabase/README.zh-CN.md)。

## 目录结构

| 路径 | 职责 |
| --- | --- |
| `app/src/main/java/com/pomodorotimer/app/domain` | 计时状态、统计、专注记录合并 |
| `app/src/main/java/com/pomodorotimer/app/ui` | Compose / Miuix 页面及交互 |
| `app/src/main/java/com/pomodorotimer/app/data` | 本机账号工作区、REST 同步、音乐目录和缓存 |
| `app/src/main/java/com/pomodorotimer/app/service` | 计时及媒体服务、提醒和通知操作 |
| `app/src/main/java/com/pomodorotimer/app/focus` | 应用限制规则及辅助功能服务 |
| `app/src/main/java/com/pomodorotimer/app/widget` | 桌面计时、当月日历及本周统计 |
| `app/src/main/java/com/pomodorotimer/app/update` | 检查版本、校验和安装更新 |
| `app/src/test` | Android JVM 单元测试 |

线性图标来自 Lucide，许可见 [Lucide ISC](app/src/main/assets/licenses/lucide-isc.txt)。后台播放、应用限制、超级岛和桌面小组件适合在相应厂商的真机上验证。

## 本地签名构建

在仓库根目录设置以下环境变量后切换到 `android/`。维护者应使用现有发布签名以支持覆盖升级；自建部署可生成自己的签名并自行分发。

| 环境变量 | 值 |
| --- | --- |
| `POMODORO_STORE_FILE` | 本机 keystore 的绝对路径 |
| `POMODORO_STORE_PASSWORD` | keystore 密码 |
| `POMODORO_KEY_ALIAS` | 发布密钥的别名 |
| `POMODORO_KEY_PASSWORD` | 发布密钥密码 |

```bash
cd android
gradle -I signing.init.gradle :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
aapt dump badging app/build/outputs/apk/release/app-release.apk
```

`signing.init.gradle` 会将签名构建限制为 `arm64-v8a`。分发前确认 APK 的应用 ID、版本号、架构和签名。仓库保留源码和公开资产，签名私钥、数据库密码和特权 key 由各部署自行保管。

## GitHub Actions 发布

[Build Android APK](https://github.com/serotonin0228/pomodoro/actions/workflows/build-android.yml) 支持手动运行。直接运行可构建调试包并执行单元测试。选择 `sign` 可生成签名 APK artifact。再选 `publish` 会发布正式版、验证 Release 下载，并更新应用内更新清单。签名和发布限定在 `main`。

| 仓库配置 | 项目 |
| --- | --- |
| Actions Secrets | `POMODORO_SIGNING_KEYSTORE_BASE64`、`POMODORO_STORE_PASSWORD`、`POMODORO_KEY_ALIAS`、`POMODORO_KEY_PASSWORD` |
| Actions Variables | `SUPABASE_URL`、`SUPABASE_ANON_KEY` |

工作流校验官方发布证书的 SHA-256。自建分发需要将工作流中的证书指纹和 [AppUpdates.kt](app/src/main/java/com/pomodorotimer/app/update/AppUpdates.kt) 中的更新地址改为自己的配置。工作流目前针对官方发布流程，使用自建签名前应检查自己的 Release 和分发分支配置。

### 本地签名后发布

1. 在 `app/build.gradle.kts` 提高整数 `versionCode`，并设置形如 `1.3.2` 的 `versionName`。运行测试并构建签名版。
2. 在 `apk-distribution` 分支根目录推送文件 `pomodoro-v<versionName>-arm64-v8a.apk`。
3. 给对应源码提交创建同名的 `v<versionName>` GitHub Release。运行 [Attach signed APK to Release](https://github.com/serotonin0228/pomodoro/actions/workflows/attach-release-apk.yml) 上传并校验 APK。
4. 从 Release 下载，核对 APK 的字节数和 SHA-256。验证后再更新 `apk-distribution/latest.json`，其中 `version` 是三段版本号，`size` 是实际字节数，`sha256` 是 64 位十六进制摘要。客户端按该分支的安装包地址下载。
5. 确认 GitHub Release 和应用内检查更新可用。按当前仓库策略，Release 列表只留最新稳定版。清理旧 Release 时保留 Git 标签，发布分支的更新文件按独立生命周期管理。

## 升级和本机工作区

官方同签名包可覆盖更新。早期版本迁移时，如果已登录，客户端会尝试将尚未迁移的旧工作区和音乐索引归入当前账号；未登录时旧数据保留在访客工作区。之后访客和各账号分别保存本机数据，登录不同 Supabase 邮箱会显示相应的工作区。
