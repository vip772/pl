# 首页聚合音乐

搜索面板第一位新增“聚合音乐”，默认选中。当前聚合 QQ 与网易云；酷狗取流主路径及两条备用路径验证失败，暂不加入。

## 搜索和播放

- QQ 使用 `https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp`，参数 `p=1..3`、`n=30`、`w=关键词`、`format=json`，Referer 为 `https://y.qq.com/`。从 `data.song.list` 解析歌曲、歌手、封面与 songmid，单平台最多 80 首。
- 网易云使用 `https://music-api.gdstudio.xyz/api.php?types=search&source=netease&name=关键词&count=80&pages=1`，解析顶层数组中的 id、name、artist。
- 两路搜索在专用后台线程池中执行，等待上限 28 秒；一侧失败仍可显示另一侧结果。按两平台排序交替合并，以来源＋ID 和规范化歌名＋歌手去重，最终总数最多 80 首。相同歌名和歌手的跨平台重复项保留先出现的项，不在此版本合并它们的备用取流。
- 结果通过首页音乐现有 Handler 回到主线程；切源、重新搜索或关闭面板后，现有 generation 校验丢弃旧回调。
- QQ 播放使用 `https://a.aa.cab/qq.music?msg=歌名+歌手&n=1&type=1`，解析 `data.music`。必须同时确认 `data.mid` 等于所选 songmid，避免按关键词解析到其他版本。没有匹配版本时返回不可播放，不静默换歌。QQ 只展示接口当前音源，不声明高品质或无损。
- 网易云播放使用 GD Studio 的 `types=url&source=netease&id=歌曲ID&br=128/320/740`，解析顶层 url。请求升级音质时检查返回 br，未满足则返回不可用；歌词使用网易云按 ID 的歌词接口。
- 收藏、队列、持久化保留独立内部来源“聚合QQ”“聚合网易云”和稳定歌曲 ID，再次需要取流时路由到聚合实现。现有独立 QQ、网易云、酷我、咪咕入口仍按原路径运行。

## 验证与限制

音源依据是用户提供的音乐 APK 内 `assets/sources/search_qq.js`、`xinghai_v2.js`、`quandouyao.js`。附件 SHA-256：`8c86dd33ca22a16e1d0f4da094fd2f827425e8cf7c526de4d86db54b5163bc65`。只定点迁移 Kotlin 请求及解析，不引入 JS 引擎或 lx 桥接。

前置实测：QQ 搜索有效且需要分页，网易云搜索返回 80 条；QQ 和网易云播放地址可读取音频片段，FFmpeg 解码前 3 秒通过。酷狗 GD Studio 取流失败；长青备用返回 `code=201` 错误，念心备用 HTTP 404。

本轮验证：Android 36 SDK 定向编译通过（首页音乐、聚合源、原有音源、悬浮歌词及相关生产文件；项目外围依赖用桩）。将生产聚合实现编译为临时 DEX，在 Android app_process 独立进程运行：真实搜索“晴天 周杰伦”合并得到 80 首（QQ 40、网易云 40）；合并上限、跨源重复去除及单路结果保留检查通过；后台到主线程回调通过。QQ 样本 songmid=0039MnYb0qxYhV、网易云样本 id=2652820720 均完成 MediaPlayer.onPrepared，时长分别为 269793ms、279014ms，静音短播后进度为 669ms、798ms。此验证使用生产搜索/解析代码和 Android MediaPlayer，但不等同于模块完整 UI 回归。临时验证 DEX、音频和日志不入仓库。未运行 Gradle，未构建模块 APK；完整搜索面板、收藏和播放控件交互仍需安装构建后验收。接口服务状态与歌曲授权随时可能变化，样本通过不能证明全部搜索结果都有可用完整音频。
