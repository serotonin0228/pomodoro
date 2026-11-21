# 接入自己的 Supabase

[English](README.md) · [返回项目首页](../README.zh-CN.md)

官方 APK 已在构建时绑定维护者的 Supabase 项目。要连接自己的项目，请用下面的配置构建 APK。计时、统计和桌面小组件可离线使用，云同步与云端曲库可按需启用。

## 1. 创建项目和账号

1. 在 [Supabase Dashboard](https://supabase.com/dashboard) 创建项目。在 **Connect** 对话框或项目 API 设置中取得 **Project URL** 和 **Publishable key**（通常以 `sb_publishable_` 开头）。旧项目的 `anon` key 也可用。
2. 在 **Authentication → Sign In / Providers** 启用 **Email** 登录。开放注册时，建议启用邮件确认并配置可向用户发信的 [自定义 SMTP](https://supabase.com/docs/guides/auth/auth-smtp)。如果只自用，可在 **Authentication → Users** 创建已确认的邮箱用户，再按需要关闭公开注册。
3. 注册启用邮件确认的账号后，先在浏览器打开邮件确认链接，再回 App 输入邮箱和密码登录。App 使用邮箱密码登录，应把 Supabase Auth 的 Site URL 配成你自己可用的 HTTPS 页面。

APK 只使用项目 URL 和可公开分发的客户端 key。数据库密码、`service_role`、`sb_secret_`、访问令牌和签名密钥应保存在受控环境。

## 2. 初始化同步数据库

1. 在新项目的 **SQL Editor** 执行 [`schema.sql`](schema.sql) 全部内容一次。它创建 `public.workspaces`、`save_workspace` 和 `server_time`，同时设置访问权限和行级安全策略。脚本需要 Supabase 提供的 `auth.users` 和 `supabase_realtime`。
2. 确认项目的 **Data API** 已开启，且暴露的 schema 包含 `public`。SQL 脚本已向 `authenticated` 显式授予所需权限。每个用户只能通过 RLS 访问自己的工作区。现有项目应先检查表、函数和策略，再补缺失项，避免重复运行初始化脚本。
3. 在开发或测试项目的 SQL Editor 执行 [`verify.sql`](verify.sql)。末尾应返回 `PASS`。脚本验证创建、更新、版本冲突及账号隔离，并在结束时回滚测试数据。

客户端走 Supabase Auth 和 Data API，通过 REST 轮询更新。写入时通过 `save_workspace` 比对版本号，发生冲突会重新拉取并合并。服务器时钟用于校准设备间的计时。项目 SQL 为旧客户端保留了 Realtime 发布配置，当前原生客户端通过 REST 同步。

参考 [Supabase API Keys](https://supabase.com/docs/guides/api/api-keys)、[Data API 权限](https://supabase.com/docs/guides/api/securing-your-api) 和 [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security)。

## 3. 构建连接到自己项目的 APK

从仓库根目录运行。把示例值换成第 1 步获取的项目 URL 和公开客户端 key。

```bash
export ORG_GRADLE_PROJECT_SUPABASE_URL='https://YOUR_PROJECT_REF.supabase.co'
export ORG_GRADLE_PROJECT_SUPABASE_ANON_KEY='sb_publishable_YOUR_KEY'
cd android
gradle :app:assembleDebug
```

调试包位于 `android/app/build/outputs/apk/debug/app-debug.apk`。两个变量在构建时写入 APK，修改后需要重新构建。自编译包若要安装到已装官方 APK 的设备，Android 要求两者使用同一个签名。可用另一台设备或独立测试环境验证调试包。正式签名方式见 [Android 构建指南](../android/README.md)。

## 4. 用两台设备验证

1. 在两台设备安装连接同一项目的 APK，在 **设置 → 云同步 → 账号** 登录同一个邮箱账号。
2. 设备 A 开始正计时，保持联网。设备 B 打开计时页，同步后应显示正在运行的计时。点 **在此设备接管** 后，B 从当前进度继续。
3. 在 B 结束并保存，回到 A 的统计页检查记录是否出现。再让 B 断网完成一条记录，恢复网络后确认记录合并。
4. 用另一个账号登录，确认看不到第一个账号的记录。访客和已登录账号有各自的本机工作区。

同步内容包括专注记录、工作区设置和计时状态。应用限制名单、无障碍授权和已下载的 MP3 属于设备或账号在本机的配置。同步有网络传播时间，跨设备计时按已同步的开始时间计算。

## 5. 可选：启用私有 MP3 曲库

在同一个新 Supabase 项目的 SQL Editor，按顺序各执行一次：

1. [`music-catalog.sql`](music-catalog.sql)
2. [`music-audio.sql`](music-audio.sql)
3. [`music-audio-version.sql`](music-audio-version.sql)

音乐目录保存在 `public.music_tracks`，MP3 二进制保存在 `private.music_audio.data`，通过 `download_music` 下载。保留 `private` 在 Data API 暴露列表之外。当前实现使用数据库二进制字段，`storage_path` 是保留字段，无需实际创建 Storage 文件或桶。单首 MP3 最大 30,000,000 字节。

管理员可通过自己本机上的 `psql` 添加 MP3。使用 Supabase 的 **Connect → Session pooler** 或可直连的数据库地址。替换示例的用户 UUID、曲名、作者、路径和文件名。下面的 `\lo_import` 是 **psql 客户端命令**，读取的是运行 psql 那台电脑上的文件，在 Dashboard SQL Editor 里应使用 SQL 安装步骤。

```sql
\set ON_ERROR_STOP on
\set owner_id '00000000-0000-0000-0000-000000000000'
\set title 'My track'
\set artist 'Artist'
\set track_filename 'my-track.mp3'
begin;
\lo_import '/absolute/local/path/my-track.mp3'
insert into public.music_tracks (user_id, title, artist, storage_path, bytes, sha256, md5)
select :'owner_id'::uuid, :'title', :'artist',
       :'owner_id' || '/' || :'track_filename',
       octet_length(data), encode(sha256(data), 'hex'), md5(data)
from (select pg_catalog.lo_get(:LASTOID) as data) as mp3
returning id as track_id, audio_revision
\gset
insert into private.music_audio (track_id, user_id, audio_revision, data)
values (:'track_id'::uuid, :'owner_id'::uuid, :audio_revision, pg_catalog.lo_get(:LASTOID));
select pg_catalog.lo_unlink(:LASTOID);
commit;
```

同名路径需在目录中唯一。要换音频，在事务中用相同方式导入新文件，按 `track_id` 和 `user_id` 更新对应的 `private.music_audio.data`，最后删除中转大对象并提交。触发器会更新大小、校验值与音频版本。客户端随后刷新目录并按版本重新下载。要隐藏曲目，管理员可将 `music_tracks.enabled` 设为 `false`。测试项目录入曲目后，可运行 [`verify-music.sql`](verify-music.sql) 检查下载、权限和版本变更。

## 常见问题

| 现象 | 检查 |
| --- | --- |
| 账号页不能配置同步 | 核对构建时两个 Gradle 环境变量，重新安装对应 APK。 |
| 注册后收不到邮件 | 检查 Email provider、邮件确认和 SMTP。Supabase 默认邮件服务面向测试，发送对象和速率受限。 |
| 登录成功但一直未同步 | 检查两台设备的邮箱、项目 URL、Data API 暴露和 `workspaces` 的 RLS、授权及 RPC，再用测试项目运行 `verify.sql`。 |
| 曲库为空 | 检查目录行的 `user_id`、`enabled` 以及是否已登录曲目所属账号。 |
| 曲目可见但下载失败 | 核对目录和私有音频行的 `track_id`、`user_id`，以及音频版本脚本和 RPC 是否都已安装。 |
