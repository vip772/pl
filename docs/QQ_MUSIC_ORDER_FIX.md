# QQ 点歌重复卡片与语音崩溃修复

## 证据与结论边界
- 用户截图：每条点歌指令之后有两张相同卡片，时间相隔约一秒。
- WeChatDatabaseListenerApi 对最外层变更以及部分底层标准 insert 都分发通知，WeChatMessageChangeApi 不做业务消息去重；旧 QQMusicOrderRuntime 每次 insert 通知直接 enqueue。一次逻辑插入可能产生两次点歌任务。这是源码确认的重复机制，日志未记录逐次业务请求的消息 ID，不能声称已实机逐帧追踪。
- LSPosed 压缩包 tombstones/tombstone_25：2026-10-11 14:40:51.976，com.tencent.mm PID 2514，线程 HchatQQMusicOrd，SIGSEGV 空指针；前三帧位于 libsilk_codec-57ba924d.so 的 0x246a0、0x2a6a8、0x2bb04。
- 仓库 libsilk_codec.so 的 CRC32 为 57ba924d，与设备加载文件后缀一致。导出符号显示 0x2bb04 在 oggToSilk JNI 符号范围内。不能把它精确归结为 MP3 解码；确定的是压缩音频原生转换发生不可被 Java catch 捕获的崩溃。

## 修改
- MusicCommandDeduplicator：消息 ID + 会话去重；无 ID 则使用创建时间 + 原始内容 + 会话。提交任务之前同步认领，60 秒过期，上限 4096；相同歌名的不同消息照常响应。
- 同一运行时不重复注册监听；销毁后停止接收、清空去重、终止排队任务，处理提交与销毁竞争。
- QQ 点歌不再直接把下载的压缩音频交给 WeChatVoiceApi 自动原生转换。
- 使用已有 AacCodec.mp4ToSilk：名称历史遗留，内部实际用 Android MediaExtractor/MediaCodec 读取受支持的音轨，混为单声道 PCM、重采样、再调用 pcmToSilk；点歌不回退到 autoToSilk/oggToSilk/mp3ToSilk。
- 生成 Silk 后使用 send(talker, silkPath, originalDurationMillis)，保留歌曲真实时长，避免对 Silk 重新探测时长。
- 转换失败返回失败提示，不提交空音频；下载、part 和 Silk 临时文件在 finally 清理。转换器自身清理 PCM 临时文件。
- 未修改全局数据库分发、公共语音 API、JNI 库、配置格式或歌曲检索接口；不迁入上游缓存优化。

## 验证
python3 scripts/tests/music_order/run.py 通过：编译真实 QQMusicOrderRuntime 与去重类，以 JVM 桩替代 Android/JNI/微信/搜索，用本地 HTTP 测试下载流程。
覆盖重复 insert 只检索/发卡一次、相同歌名新消息正常响应、并发认领、TTL、跨会话、无 ID 创建时间、重复安装、发送按钮、系统转换后 Silk/原时长提交、转换失败不发送、临时文件清理、销毁后不响应。

这不是 Android 系统解码、原生 PCM 编码或微信长语音发送的实机验证。实际上传成功仍需修复版在主用户与分身测试；系统不支持的音频会失败而不是回退到已知危险的压缩音频 native 路径。未承诺所有音频格式不会发生其他故障。

未运行 Gradle或本地 APK 构建，未在设备上尝试发送语音或更改数据；当前改动未提交推送。