# Pomodoro — Kotlin 重写

原生 Android 重构版：**Kotlin + Jetpack Compose + Miuix（MIUI / HyperOS 设计体系）**。

原项目是 `serotonin0228/pomodoro`（Expo / React Native + TypeScript），本工程是完整重写，
不改动原仓库代码，功能一一对齐后用原生实现替换。

## 技术选型

| 维度 | 原实现 | 重构后 |
|---|---|---|
| 语言 | TypeScript | Kotlin 2.4 |
| UI | React Native 组件 | Jetpack Compose + **Miuix 0.9.4**（全部控件） |
| 计时 | JS 定时器 + 绝对时间戳 | 同样是绝对时间戳，UI 每 200ms 读取时钟 |
| 存储 | AsyncStorage（JSON） | 待定：DataStore + Room 记录表 |
| 后端 | Supabase JS SDK | Supabase REST + Kotlin 协程 |
| 通知 | expo-notifications | NotificationCompat + 前台服务 |
| 应用限制 | 原生 Kotlin 插件 | 直接复用其策略/服务逻辑 |

## 目录

```
pomodoro-android/
├── app/src/main/java/com/serotonin/pomodoro/
│   ├── domain/          # 纯 Kotlin，无 Android 依赖，可单测
│   │   ├── Model.kt     # Timer / FocusRecord / Settings / 合并与统计
│   │   ├── Goal.kt      # 目标进度计算
│   │   └── Clock.kt     # 带服务器校正的时钟
│   ├── ui/              # Compose + Miuix
│   │   ├── TimerController.kt
│   │   ├── TimerScreen.kt
│   │   └── theme/PomodoroTheme.kt
│   ├── MainActivity.kt
│   └── PomodoroApp.kt
└── app/src/test/java/com/serotonin/pomodoro/domain/ModelTest.kt
```

## 移植不变式（测试守护）

`ModelTest.kt` 逐条移植原仓库 `tests/model.test.ts`，任何一条挂了就说明行为变了：

1. 计时跟墙钟走，暂停时间要扣掉，即使设备长时间挂起。
2. 倒计时恢复时在原始截止点截断，且只落库一次。
3. 休息时间永远不进专注记录。
4. 统计按本地零点切分，多设备重叠区间不重复计数。
5. 已删除记录不会被旧的云端副本复活。
6. 长休息按轮次间隔取时长；`clock()` 格式对齐。
7. 同一条记录的多个副本合并时保留唯一区间、不产生重复。
8. 目标进度按选中日期计算，未来日期不计入分母。

## 已移植

- [x] 领域模型全部函数（`Model.kt`）
- [x] 目标进度（`Goal.kt`）
- [x] 时钟校正（`Clock.kt`）
- [x] 行为契约测试 8 条
- [x] Miuix 主题外壳
- [x] 计时主界面（开始 / 暂停 / 结束，含阶段与轮次）

## 已移植（续）

- [x] 统计页（日/周/月、按天明细、历史、CSV 导出走系统文件选择器）
- [x] 设置页（时长、轮次、目标类型、主题、语言、自动切换、提示音、通知开关）
- [x] 本地持久化（SharedPreferences + JSON，键名与 RN 端一致）
- [x] 通知 + 前台服务 + 精确闹钟，通知栏可直接暂停/继续/结束
- [x] 应用限制 FocusGuard（无障碍服务，原 Kotlin 逻辑整体复用 + 策略测试）
- [x] 中英文 i18n（`Language.ZH/EN`，类型安全文案表）
- [x] Supabase 云同步（邮箱登录、`save_workspace` 乐观并发、冲突合并）
- [x] 背景音乐（`music_tracks` 曲库 + `download_music` 下载缓存 + 本地循环播放）

## 已知差距

- 音乐是**单曲循环**播放，未实现原版的顺序/随机队列与后台播控通知。
- 云同步与音乐需要在构建时注入 `SUPABASE_URL` / `SUPABASE_ANON_KEY`（gradle property），为空时相关入口显示「未配置」。
- 本机没有 JDK / Android SDK，**尚未在真机上编译验证**；所有版本已核对存在于 Maven：
  AGP 8.13.2、Kotlin 2.4.21、Compose BOM 2026.09.00、Miuix 0.9.4。
- 通知动作写回的是持久化快照，页面回到前台时会重新读取（`refresh()`），因此两处不会各算一份。

## 构建

需要 JDK 17 + Android SDK 36。本仓库不含 Gradle Wrapper 二进制，标准做法：

```bash
cd pomodoro-android
gradle wrapper --gradle-version 8.13   # 首次生成 wrapper
./gradlew :app:assembleDebug           # 或直接 gradle assembleDebug
./gradlew :app:testDebugUnitTest       # 跑行为契约测试
```

> 版本均已核对存在：AGP 8.13.2、Kotlin 2.4.21、Compose BOM 2026.09.00、Miuix 0.9.4
> （`top.yukonga.miuix.kmp:*`，Maven Central）。
