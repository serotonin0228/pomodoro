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


## 发布应用内更新

1. 提高 `versionCode`，并将 `versionName` 设置为三段数字。使用与上一版相同的签名构建 arm64-v8a APK。
2. 将签名后的 APK 以 `pomodoro-v<versionName>-arm64-v8a.apk` 的名称放入 `apk-distribution` 分支根目录。更新同目录下的 `latest.json`，格式为 `{"version":"1.0.11","size":123456}`，其中 `size` 是 APK 的实际字节数。已发布的旧文件可保留，供历史 Release 下载。
3. 建立对应的 GitHub Release，并运行 [Attach signed APK to Release](https://github.com/serotonin0228/pomodoro/actions/workflows/attach-release-apk.yml) 校验和上传 APK。在正式发布中，先推送 APK、检查 Release 的附件可以下载，再更新 `latest.json`。应用在启动时读取此清单。

## 从 v1.0.11 升级

若升级时已登录，客户端会将旧的本机工作区和曲目索引迁入当前账号。旧工作区原始快照保留在本机。若升级时未登录，本机历史保留为访客数据。已有云端数据沿用原有接口。以后切换账号会切换本机工作区和音乐索引。


## GitHub Actions 构建和发布

在 [Build Android APK](https://github.com/serotonin0228/pomodoro/actions/workflows/build-android.yml) 运行 Workflow 可以构建调试包并执行测试。选择 `sign` 可生成签名 APK artifact。再选择 `publish` 则会上传到正式 Release，验证公开下载，然后更新应用内更新清单。签名和发布只对 `main` 开放。

启用签名前，仓库管理员须在 GitHub Actions Secrets 中配置原有发布密钥的 `POMODORO_SIGNING_KEYSTORE_BASE64`、`POMODORO_STORE_PASSWORD`、`POMODORO_KEY_ALIAS`、`POMODORO_KEY_PASSWORD`。在 Actions Variables 中配置 `SUPABASE_URL` 和公开的 `SUPABASE_ANON_KEY`。构建会校验签名指纹，确保 APK 可以覆盖升级。也可以继续本机签名并通过附件 Workflow 发布。

从 Android 桌面的添加小组件入口可添加专注计时、专注日历和专注统计。日历按本月每天的专注时长着色，统计显示今日、本周累计和七天柱状图。
