# 群聊滚动与 QQ 点歌语音性能排查

## 验证与边界
- python3 scripts/tests/music_order/run.py：通过生产运行时 JVM 桩测试，增加缓存命中不再次转码、过期淘汰、条目上限与坏时长记录测试。
- 消息详情 layout 与 pre-draw JVM 回归通过。第一次 pre-draw 失败原因是上述旧包路径，修正后通过。
- git diff --check 通过。
- 未编译完整 MentionHighlight/HchatExtra Android 功能，未运行 Gradle或构建 APK，未实测 FPS、首次语音耗时或上传速度。
- 群聊绑定仍有反射、数据库补全等成本；本轮没有以冒险缓存数据库数据代替实时信息。若仍卡顿，应采集主线程 trace 并按功能逐项对照，而非直接取消艾特功能。
- 未迁入上游启动缓存/内存回收优化，未提交或推送当前改动。
