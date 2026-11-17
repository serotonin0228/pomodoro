# 番茄钟 Android 版

原生 Android 应用，使用 Kotlin、Jetpack Compose 和 Miuix；包名为 `com.pomodorotimer.app`。仓库中保留的 React Native 实现仅供历史参考。

## 功能

- 倒计时和正计时、专注及休息阶段、通知与前台计时服务。
- 按日、周、月查看专注统计、趋势图、日历和记录，并可删除记录。
- 云端音乐目录、本地按需下载、后台播放与单曲、顺序、随机模式。
- 黑名单或白名单应用限制；需要用户在系统设置中启用辅助功能。
- 可选 Supabase 邮箱登录和自动同步；无账号时使用本地数据。
- 中文和英文、浅色与深色主题。

## 构建

需要 JDK 21、Android SDK Platform 37、Gradle 9.5，以及 Android SDK 构建工具。项目的 Kotlin 目标为 JVM 17，最低支持 Android 8.0（API 26）。仓库不包含 Gradle Wrapper 二进制，可在 `android/` 下使用已安装的 Gradle：

```sh
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

云同步与音乐需要在构建时提供 Gradle 属性 `SUPABASE_URL` 和 `SUPABASE_ANON_KEY`，可通过 `ORG_GRADLE_PROJECT_SUPABASE_URL` 与 `ORG_GRADLE_PROJECT_SUPABASE_ANON_KEY` 环境变量传入。仅使用项目的公开 URL 和 publishable/anon key；不要提交密钥文件、账号令牌、签名文件或私有凭据。

发布 APK 需要另外配置签名。需要单一架构时，可在构建配置中将 ABI 限定为 `arm64-v8a`；默认构建设置不包含发布签名。

## 源码

- `app/src/main/java/com/pomodorotimer/app/domain`：计时、统计和合并逻辑。
- `app/src/main/java/com/pomodorotimer/app/ui`：Compose 和 Miuix 界面。
- `app/src/main/java/com/pomodorotimer/app/data`：本地持久化和云端访问。
- `app/src/main/java/com/pomodorotimer/app/service`：计时及媒体前台服务。
- `app/src/main/java/com/pomodorotimer/app/focus`：应用限制策略和辅助功能服务。

线性图标来自 Lucide，许可文本见 `app/src/main/assets/licenses/lucide-isc.txt`。真实设备的后台播放、系统权限与电池管理行为需要在对应设备上验证。
