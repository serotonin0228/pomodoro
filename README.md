# Pomodoro

简洁的跨平台专注计时应用。使用 Expo、React Native 和 TypeScript 构建，支持中英文界面、专注统计和可选的跨设备同步。

## 功能

- 倒计时和正计时，支持暂停、继续、保存或丢弃；专注、短休息和长休息可配置。
- 日、周、月统计与时段分布；跨午夜拆分、重叠时段去重，支持 CSV 导出。
- 本地循环钢琴背景音乐，可独立试听；无音乐媒体通知。
- 浅色与深色主题、中英文切换；手机和平板布局。
- 可选的 Supabase 邮箱账号、设备接管、实时同步和冲突处理；访客数据留在本机。
- Android 专注状态通知及暂停／继续操作，支持请求 Android Live Updates。具体胶囊展示由设备系统决定。

## 开始使用

需要 Node.js 22.13 或更新版本。

```sh
npm ci
npm run web
```

原生开发需要 Android SDK，或 macOS 与 Xcode：

```sh
npx expo prebuild
npx expo run:android
npx expo run:ios
```

原生功能需要 development build；Web 预览不能验证 Android 通知、后台行为和原生模糊。Android 项目由 Expo 配置插件生成，`plugins/withFocusNative.cjs` 配置计时服务，`plugins/withPlainLaunch.cjs` 配置启动画面。

## 使用方式

- 点击时间：设置时长、休息、自动循环和计时通知。
- 统计页的「每日目标」：设置目标；页面底部可导出记录。
- 右上角云图标：登录、同步、设备接管与语言选择。
- 右上角太阳／月亮：切换浅色与深色模式。

## 可选的云同步

1. 创建自己的 Supabase 项目，并在 SQL Editor 中运行 `supabase/schema.sql`。
2. 复制 `.env.example` 为 `.env`，填写项目 URL 与**公开 publishable key**。
3. 在 Supabase Auth 开启邮箱登录，并配置确认邮件、SMTP 与回跳地址。
4. 重启应用，在两台设备上登录同一账号进行验证。

不要把 service-role key、数据库密码、签名文件或 `.env` 提交到仓库。访客与账号数据保存在独立空间，登录不会自动导入访客记录。

数据库使用 RLS 和版本比较 RPC；Realtime 和前台轮询负责同步。后台连接受系统限制。应用目前使用本地到时通知，并未实现 APNs/FCM 服务端远程推送。Android Live Updates 是否展示为状态栏胶囊取决于系统与权限；iOS 灵动岛尚未实现。

## 开发与验证

```sh
npm run lint
npm run typecheck
npm test
npm run build
```

浏览器端到端测试使用 Playwright。先启动静态服务，再运行测试：

```sh
python -m http.server 8081 --directory dist
npm run test:e2e
```

Chromium 默认路径为 `/usr/bin/chromium`，可设置 `PLAYWRIGHT_CHROMIUM_EXECUTABLE`。账号测试使用模拟响应，不发送邮件。HyperOS 通知展示、后台音频和启动效果应在真实设备上验证。

## 项目结构

- `App.tsx`：应用界面与交互。
- `src/model.ts`：计时状态与统计计算。
- `src/Stats.tsx`：统计和历史记录。
- `src/useWorkspace.ts`、`src/cloud.ts`：本地数据与同步。
- `src/i18n.tsx`、`src/locales/`：语言与文案。
- `src/notifications.ts`、`plugins/focus-native/`：通知和 Android 前台计时服务。
- `supabase/schema.sql`：数据库结构和权限策略。
- `assets/audio/CC0-LICENSES.json`：曲目授权和来源记录。

## 许可

代码采用 MIT 许可；内置第三方音乐的许可和来源见 `assets/audio/CC0-LICENSES.json`。Expo 模板的原始许可声明保留在 `NOTICE` 中。
