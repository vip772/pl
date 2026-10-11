# 群聊滚动与 QQ 点歌语音性能排查

## 本次输入与证据
Hchat-runtime-20261011-153154.log 和 LSPosed_20261011_153233.zip。
- 运行日志约一分钟内包含 2732 条 MentionHighlight 日志，包含每条消息绑定、setText、每个艾特范围。HLog.d 同步调用 RuntimeLogStore.append，在锁内打开/追加日志文件；滚动绑定发生在主线程。
- MentionHighlight 文本 Hook 在读取 renderedCache 之前已执行 buildMentionText；缓存没有避免这一步工作。
- LSPosed 中消息卡片锚点未就绪/定位成功日志通过 HLog.e 生成 RuntimeException 栈。未布局的 0x0 视图是正常第一阶段，并非这些日志本身说明功能抛异常。每个 holder 类已限频，不能夸称其为持续无限日志。
- 悬浮输入框首次定位失败后恢复原输入区，是降级记录，不应生成异常栈。仍保留降级行为，未声称修好了所有定位失败。
- LSPosed Manager 请求 modules.lsposed.org 返回 403 属于管理器仓库网络问题，不是 Hchat 异常或群聊掉帧证据。
- 没有 FrameTimeline/Perfetto、UI 线程采样或点歌下载/转码分段计时，不能证明全部掉帧原因或四五秒由哪一段占用。

## 本地改动
1. 艾特高亮的高频诊断改为默认关闭且惰性求值，不构造日志字符串、不写文件；保留真正错误。
2. 相同消息/颜色/会话/艾特元数据以及相同文本的普通 CharSequence 可以直接使用缓存；Spanned 输入仍经过原处理以保留原生 span 的差异与边界，避免跨消息复用。
3. 删除消息卡片锚点的临时诊断代码，卡片字段校验、测量等待、回退选择保持不变。
4. 悬浮输入框降级提示由 ERROR 改为 DEBUG，避免制造堆栈；定位逻辑未变。
5. QQ 点歌加入已编码 Silk 磁盘缓存，以歌曲 mid 与转换版本为标识；最多 8 首、32MiB，24 小时未使用过期。存储在微信 cache/Hchat_qq_music_order_voice/encoded，主用户与分身自然隔离。失败/超限不保存，成功缓存不保存播放 URL 或账号信息。
6. 缓存命中省去下载和转换；不省略歌曲查询，也不将压缩音频直接发给 native 解码。首次歌仍需下载全曲、系统解码、PCM 编码和微信提交，未承诺秒发。
7. 下载后只读取一次时长/音频元数据，不重复打开 MediaMetadataRetriever。
8. 每次语音缓存命中/未命中记录少量 QQMusicTiming；未命中包含 downloadMs/convertMs/submitMs。submitMs 仅表示 API 提交，不代表远端上传完成。
9. 修正原 pre-draw 回归脚本残留 h/Hchat 路径和主类名到 t/mma；这是包名变更遗留的测试入口错误，不是业务优化。

## 验证与边界
- python3 scripts/tests/music_order/run.py：通过生产运行时 JVM 桩测试，增加缓存命中不再次转码、过期淘汰、条目上限与坏时长记录测试。
- 消息详情 layout 与 pre-draw JVM 回归通过。第一次 pre-draw 失败原因是上述旧包路径，修正后通过。
- git diff --check 通过。
- 未编译完整 MentionHighlight/HchatExtra Android 功能，未运行 Gradle或构建 APK，未实测 FPS、首次语音耗时或上传速度。
- 群聊绑定仍有反射、数据库补全等成本；本轮没有以冒险缓存数据库数据代替实时信息。若仍卡顿，应采集主线程 trace 并按功能逐项对照，而非直接取消艾特功能。
- 未迁入上游启动缓存/内存回收优化，未提交或推送当前改动。