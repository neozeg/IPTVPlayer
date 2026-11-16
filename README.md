# 轻播 IPTV（IPTV Player for Android）

一个**只做播放器、不带任何频道内容**的 Android IPTV 应用。你提供 M3U / M3U8 播放链接，它负责解析、分组、搜索、收藏和播放。手机、平板、Android TV / 电视盒子都能装。

工程目录：`IPTVPlayer/`（可直接用 Android Studio 打开）

GitHub 仓库：https://github.com/neozeg/IPTVPlayer ｜ 最新 APK：https://github.com/neozeg/IPTVPlayer/releases/latest

---

## 一、最快上手

1. 安装 APK（见下方「拿到 APK」）。
2. 首次打开会弹出「节目源设置」，在第一个输入框贴上你的 **M3U 播放链接**（`http://` 或 `https://`）。
3. 点「确定」→ 频道列表出现 → 点频道即可播放。

也支持另外两种导入方式：

- **导入本地文件**：点「导入本地 .m3u 文件」，选择手机里的 `.m3u` / `.m3u8` / `.txt`。
- **直接粘贴文本**：把整个播放列表内容粘进「或直接粘贴 M3U 文本」框（优先级最高，无需联网）。

> 想先验证 app 是否正常工作，可以把 `sample/playlist.m3u` 里的两条公开测试流粘进去，或导入该文件。

### 播放页操作

| 操作 | 效果 |
| --- | --- |
| 点屏幕 / 按任意键 | 唤出频道信息和控制条（4 秒后自动隐藏） |
| 「上一个 / 下一个」 | 切台 |
| 遥控器 ↑ / ↓、频道 +/-、媒体上一首/下一首 | 切台 |
| 「频道列表」 | 弹出全部频道，直接跳转 |
| 「☆」或列表长按 | 收藏 / 取消收藏 |
| 返回键 | 退出播放页 |
| 播放出错 | 自动重试 2 次；点错误文字可手动重试 |

列表页支持**按频道名/分组搜索**、**分组筛选**、**只看收藏**、**下拉刷新（点「刷新」）**、记住上次所在分组。

---

## 二、节目源高级设置

点右上角「节目源」可随时修改：

| 字段 | 用途 |
| --- | --- |
| M3U 链接 / 本地文件 / 粘贴文本 | 三选一，按「粘贴文本 > 本地文件 > 链接」优先级 |
| XMLTV 节目单地址 | 可选。填了就在播放页显示「正在播放：xxx」；列表自带 `x-tvg-url` 时会自动采用 |
| User-Agent | 部分运营商源要求特定 UA；留空用默认浏览器 UA |
| Referer | 防盗链常用 |
| 自定义请求头 | 每行一个 `Key: Value`，例如 `Origin: https://example.com` |

除全局设置外，解析器还会读取列表里的 `#KODIPROP` / `#EXTVLCOPT` 标签和 Kodi 风格的 `url|User-Agent=xxx&Referer=yyy` 参数，作为**单个频道**的请求头覆盖。

其它细节：

- 支持相对路径频道（按播放列表 URL 自动补全）、UTF-8 BOM、CRLF、GBK 编码自动识别、gzip 压缩的 EPG。
- 已允许明文 HTTP（`usesCleartextTraffic` + network security config），兼容大量仍在用 `http://` 的直播源。
- 网络失败时自动回落到上一次成功加载的**本地缓存**，断网也能看频道列表。
- 播放列表、收藏、设置全部存在本机 SharedPreferences / 应用私有目录，不上传任何服务器。
- 支持格式：HLS (m3u8)、DASH、MPEG-TS、MP4 等 Media3 原生格式。
- **不支持**：RTMP / RTSP / UDP 组播、Widevine / ClearKey DRM（Media3 无内置支持）。遇到这类源请改用带 libmpv/FFmpeg 引擎的播放器（见下方备选项目）。

---

## 三、拿到 APK

> 已编译好的 **`IPTVPlayer-v1.0.0.apk`**（debug 签名，7.2 MB）可直接从 [GitHub Releases](https://github.com/neozeg/IPTVPlayer/releases/latest) 下载，本地也随附了 **`IPTVPlayer-source.zip`**（完整源码工程）。把 APK 传到手机安装即可，无需自己编译；安装时手机需允许「未知来源」。已验证：包名 `com.workbuddy.iptvplayer`，minSdk 23（Android 6.0+），targetSdk 36。

### 方式 A：本机编译

环境要求：JDK 17、Android SDK Platform 36 + Build Tools 36.0.0、Gradle 8.13（Wrapper 已配好）。

```bash
cd IPTVPlayer
./gradlew assembleDebug        # Windows 用 gradlew.bat assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`，直接用 `adb install` 或拷贝到手机安装。

Android Studio 用户：File → Open → 选择 `IPTVPlayer` 目录 → 等待 Gradle Sync → Run ▶。

### 方式 B：GitHub Actions 云端构建（无需本地环境）

仓库已内置 `.github/workflows/build-apk.yml`（JDK 17 + Android SDK 36 + Gradle Wrapper 一键出包）：

1. 推送到 `main` 后 Actions 自动运行；也可在 [Actions 页](https://github.com/neozeg/IPTVPlayer/actions) 手动 `Run workflow`。
2. 构建完成后在该次运行的 **Artifacts** 里下载 `IPTVPlayer-debug-apk`。
3. 想直接装机，去 [Releases](https://github.com/neozeg/IPTVPlayer/releases/latest) 下载已发布的 APK。

---

## 四、从 GitHub 找到的开源资源

### 本工程实际采用的组件

| 组件 | 版本 | 来源 | License |
| --- | --- | --- | --- |
| AndroidX Media3 / ExoPlayer（含 HLS、DASH、UI） | 1.9.4 | [androidx/media](https://github.com/androidx/media) — Google 官方播放内核 | Apache-2.0 |
| OkHttp | 4.12.0 | [square/okhttp](https://github.com/square/okhttp) — 播放列表 / EPG 下载 | Apache-2.0 |
| Kotlin Coroutines、AndroidX AppCompat / RecyclerView / Core | — | AndroidX / JetBrains | Apache-2.0 |

### 调研过的现成 IPTV App（想直接用成熟产品时）

| 项目 | Star | 技术栈 | 说明 |
| --- | --- | --- | --- |
| [StreamVault-IPTV](https://github.com/Davidona/StreamVault-IPTV) | ~1.0k | Kotlin + Compose + Room + Hilt + Media3 | 功能完整，TV 优先，依赖较重 |
| [OwnTV](https://github.com/ahXN00/OwnTV) | ~825 | Kotlin + Compose for TV + libmpv + Media3 | **仅 Android TV**，GPL-3.0，[Releases 有现成 APK](https://github.com/ahXN00/OwnTV/releases/latest)，支持 Xtream / M3U / EPG |
| [IPVIO-Stream](https://github.com/Emrodev/IPVIO-Stream) | ~769 | Kotlin | Android TV / Fire TV，零配置自带播放列表 |
| [xplayer](https://github.com/TNT-Likely/xplayer) | ~214 | Flutter | 手机 / TV / 桌面跨平台 |
| [android_iptv](https://github.com/enzoftware/android_iptv) | ~53 | Kotlin | 早期的 M3U 解析教学示例 |

**为什么这里选择自己搭一个精简工程**：上面这些大多是 Android TV 专用、GPL 许可、或依赖 Room/Hilt/Compose/native mpv 等重型组件，在手机上体验和编译门槛都不理想。本工程只用官方 Media3 + 少量 AndroidX 依赖，`minSdk 23`、`targetSdk 36`，手机和盒子通用，源码约 9 个 Kotlin 文件，方便你自己改。

> 如果你主要用电视盒子且想要 EPG、回看、多播放列表、DRM 等完整功能，直接装 OwnTV 的 APK 会更省事。

---

## 五、工程结构

```
IPTVPlayer/
├── app/src/main/java/com/workbuddy/iptvplayer/
│   ├── Channel.kt          频道数据模型
│   ├── M3uParser.kt        M3U/M3U8 流式解析（EXTINF / EXTGRP / KODIPROP / 管道参数）
│   ├── PlaylistLoader.kt   网络/本地/粘贴三种来源 + 编码识别 + 离线缓存
│   ├── AppPrefs.kt         SharedPreferences 设置与收藏
│   ├── PlaylistStore.kt    进程内频道列表（避免 Intent 传输超限）
│   ├── Epg.kt              XMLTV 解析与「正在播放」
│   ├── ChannelAdapter.kt   频道列表 RecyclerView
│   ├── MainActivity.kt     列表、搜索、分组、收藏、节目源设置
│   └── PlayerActivity.kt   Media3 全屏播放、切台、重试、遥控器
├── app/src/main/res/       布局 / 主题 / 图标 / 网络安全配置
├── .github/workflows/      云端自动打包 APK
└── sample/playlist.m3u     两条公开测试流
```

---

## 六、常见问题

**Q：频道列表加载出来了，但点进去提示播放失败？**
多为源本身限制。依次检查：链接是否过期、是否需要特定 User-Agent / Referer / Origin（在「节目源」里填）、是否为 RTMP/DRM（本播放器不支持）、手机与源是否在同一个网络（部分源绑定宽带内网）。

**Q：一直转圈 / 缓冲？**
直播源本身不稳定，点错误文字重试或换台。可在播放页「频道列表」里换一条线路。

**Q：中文频道名乱码？**
已做 GBK 自动识别。若仍乱码，把播放列表存成 UTF-8 再导入。

**Q：Android 9+ 打不开 http 源？**
已在 network security config 里放行明文流量。若仍失败，说明是源服务器问题。

---

## 七、免责声明

本应用只是一个**播放器**，不包含、不提供、不推荐任何频道、播放列表或订阅地址，也不内置任何内容。播放源由用户自行配置，请仅使用你拥有合法授权或公开免费的直播源，并遵守所在地法律法规。所使用的第三方开源库各自遵循其原始许可证。
