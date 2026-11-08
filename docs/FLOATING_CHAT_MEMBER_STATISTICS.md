# 群员性别好友统计

设置入口为“美化 → 群员性别好友统计”和“新功能 → 群员性别好友统计”，复用同一个 Provider 与独立设置页，开关默认关闭。悬浮标题栏设置页不再包含统计项。当前展示限定既有胶囊支持的微信 8.0.78。用户配置保存于统一管理的 `Hchat_global_config`，键为 `floating_chat_header_member_statistics_enabled`，自动参与配置备份。没有新增资源文件或持久化运行缓存。

## 显示和布局依据

显示 `男[原生男图标]N · 女[原生女图标]N · 未知 N · 好友 N`，位于当前群名 TextView 实际底部下方 3dp（悬浮标题栏开启时使用模块标题，关闭时使用原生 obn），水平居中。固定半透明背景适配深浅模式，文字不随背景降低 alpha。窄屏保持单行并尾部省略。标题尚未测量时隐藏统计，等待既有布局收敛。

证据来自用户指定的 `wechat8078_decoded`：`res/layout/ba.xml` 的 `obn`、`obr`、`coy` 和 `actionbar_up_indicator`；`smali_classes11/com/tencent/mm/ui/chatting/component/fg.smali` 的 `I0` 加载标题布局。`smali_classes3/com/tencent/mm/ui/tipsbar/ChatTipsBarGroup.smali` 加载 `0x7f0e02f4`，由 `public.xml` 映射到 `s3.xml`，包含 `hyi`、`m98`、`ow1` 等既有提示卡片维护目标。

胶囊纳入标题栏实际高度，原有返回、标题、更多保持基础高度。先应用胶囊，再执行既有顶部卡片、提示组、内容卡片和消息区留位。提示组继续使用标题栏屏幕底部加既有间距定位，关闭统计恢复标题栏原高度。未新增滚动监听或消息绑定 Hook。

## 数据与性能

群 ID 来自公共 `WeChatChatPageApi.currentTalker()`，不解析标题文字。后台通过公共联系人 API 的 `getGroupMemberStatistics()` 读取本地成员名单，去重后每批最多 200 个成员查询。性别复用现有 `lvbuff` 解析器，1 为男、2 为女，其余及缺失联系人归未知。好友沿用 `isFriend()` 的严格条件，排除当前账号本人；好友统计与性别统计重叠。

空成员名单、账号未就绪、数据库 cursor 不可用或读取异常返回不可用，不能显示为全部 0。读取过程中检查线程取消并关闭 cursor。不请求网络、不主动拉取群成员资料，因此反映本地已取得的群资料。

只保留当前群的计数字符串。同群重复布局不重新读取；重新进入、切群或重新挂载更新。一个低优先级 worker，队列容量 1，空闲 15 秒退线程。无定时轮询、跨群结果缓存或磁盘缓存。切群、离页或 detach 取消旧任务，以代际校验阻止旧结果回填；关闭解除页面订阅并 shutdown worker，销毁接入原资源恢复链路。布局尺寸和文本未变化时不重复测量、不写高度和边距。

## 验证及边界

专项脚本：

- `scripts/test_floating_member_statistics.py`：执行生产统计方法，验证成员去重、缺失性别、自己排除好友、200 人分批、失败/空名单、取消、cursor 关闭，并用 SQLite 执行生产好友判定 SQL。
- `scripts/test_floating_member_lifecycle.py`：运行完整统计 owner，验证关闭零读取、1000 次绑定只读一次、阻塞旧代回填隔离、重新挂载及关闭清理。
- `scripts/test_floating_member_layout.py`：执行生产胶囊位置方法，验证标题底部加 3、真实高度、稳定调用不重复写入/测量、尺寸更新、关闭恢复及未测量隐藏；静态核查设置和提示组接线。
- `scripts/test_floating_member_sdk.py`：完整标题栏 Kotlin、联系人 Java 与实际核心依赖对 Android SDK / 现有 ABI 定向编译；仅 FeatureToggleRegistry 为编译夹具。
- `scripts/tests/config_backup/archive.py`：真实生产存储及备份实现的默认关闭、false/true/false 导出导入往返。
- `scripts/test_floating_header_viewport_inset.py`：原消息区留位回归。

整包 `test_monitor_sdk.py` 受现有 ABI 缺少无关公共组件阻塞，不算通过。未运行 Gradle、未构建 APK，未进行 Android/R8 或新版实机验收。视觉间距、公告出现/消失、返回与更多操作、长群名/窄屏、深色模式、冷启动数据就绪以及滚动帧耗时仍需实机统一验收。专项通过不证明设备流畅度收益，也不改变资源生命周期七阶段进度。

## 原生性别图标

8.0.78 `public.xml` 定义 raw/ic_sex_male（0x7f1104c9）、raw/ic_sex_female（0x7f1104c8）。`smali_classes16/com/tencent/mm/plugin/profile/ui/tab/m5.smali` 第5533–5539及5566–5572行均调用 `ke5.a.i(Context,int):Drawable`；资料页关闭额外染色。模块仅在已确认的8.0.78胶囊内，通过 KavaReflector 调用同一方法，保留原色，以14dp ImageSpan置于男女标签之后。每个胶囊状态只加载一次，销毁随状态释放；失败限每状态记录一次并保留可读文本，不绘制替代图标。其他版本未验证。

## 独立功能与共享挂载

独立注册 `GroupMemberStatisticsFeature`；为兼容旧配置，功能 ID 保留 `floating_chat_header_member_statistics`，原开关键不变。默认的 FeatureToggleRegistry 按 `${featureId}_enabled` 读取，因此“已开启功能”、搜索和两个分组入口共用一致状态。无需迁移或复制开关；统一配置备份继续包含此键，内存快照不备份。

`ChatHeaderMount` 是两个功能共用的唯一宿主 Hook owner，以引用计数维护安装与释放，最后一个 owner 销毁时清理 scheduler lease、Hook 和视图。`FloatingChatHeaderFeature` 只保留标题栏功能注册；数据 owner 为 `GroupMemberStatisticsRuntime`，展示由 `GroupMemberStatisticsSurface` 独立管理，标题胶囊只提供标题锚点并排除统计子 View。

仅统计开启时，使用原生标题，不执行标题栏重排、透明背景、阴影、边距或窗口 overlay 改写。固定原标题直接子容器的基础高度，避免标题随统计扩高重新居中；扩展标题栏为胶囊占位，继续保留原生内容与公告布局关系。两项同时开启时，在公告及消息区域定位前应用统计实际高度。切换标题栏模式先恢复旧展示和统计占位，再在布局收敛时挂载。关闭统计移除 label 并恢复原高度；两项关闭取消数据任务并恢复挂载。

本次拆分检查记录见 [独立功能验证](GROUP_MEMBER_STATISTICS_INDEPENDENT.md)。旧专项记录是上次提交的历史证据，不代表本次实机验收。
