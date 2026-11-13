# 云端音乐管理

新版在同一 Supabase 项目中使用 `public.music_tracks` 保存曲目信息、`private.music_audio` 保存音频二进制。音频通过 `download_music` 下载，接口以调用者身份执行并受账号行级权限限制。APP 没有管理密钥或曲库写入权限。

## 日常修改

在 Supabase Table Editor 打开 `music_tracks`：

- 修改 `title`、`artist`：更改显示名称及作者，不需要重新下载音频。
- 修改 `sort_order`：调整顺序。
- 将 `enabled` 改为 `false`：从各设备曲库及播放队列下架歌曲。
- 删除曲目：其私有音频会级联删除；原有专注数据不会受到影响。

APP 在启动、返回前台、打开音乐面板时静默更新，同时通过实时订阅和前台每 30 秒的轮询接收变化。各设备独立保存下载状态，账号只同步选中的曲目 ID。

## 替换音频

用管理员在数据库中更新 `private.music_audio.data`。`sync_music_audio` 触发器会自动计算大小和校验值、递增音频版本并更新曲库。设备下一次更新曲库时显示“更新可用”；点击该曲目即可下载新版本。不要手动修改大小和校验值来代替更新音频。

可以使用仓库脚本生成事务 SQL，再由管理员在 Supabase SQL Editor 或自己的 PostgreSQL 客户端执行：

```bash
python3 scripts/music-update-sql.py --track-id '<music_tracks.id>' --audio '/path/to/new.mp3' --output /tmp/update-music.sql
```

脚本只生成 SQL，不读取凭据、不自动连接数据库。新增曲目时先在 `music_tracks` 建立归属当前账号的记录，再插入对应 `private.music_audio` 数据；应在音频写入后启用该曲目。管理文件和音频不适用应用源代码的 MIT 许可。

## 下载与离线

点击下载按钮会下载并校验完整性，验证成功后显示播放按钮。下载中断或校验失败不会进入播放队列。后台使用原生队列，只播放当前设备已下载的曲目。已下载文件保存在 APP 文档目录，重启后仍可使用。

切换账号会更换曲库和缓存命名空间。原有 `workspaces`、邮箱账号、专注记录及其策略没有改动。

## 验证记录

已验证同账号下载、完整音频校验、其他账号隔离、未登录拒绝访问，以及替换音频后的自动升版；数据库测试修改均已回滚。新增表启用 RLS，下载函数以调用者权限执行。

如需启用泄露密码保护，可参考 [Supabase 密码保护说明](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)。
