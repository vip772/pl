# 会话分组、快捷已读、消息详情与通话/输入区故障恢复

## 输入与边界

依据 LSPosed_20261011_161344.zip 和 Hchat-runtime-20261011-160452.log。
不运行 Gradle，不构建或安装 APK，不清理设备配置/数据库。本次仅本地提交，不推送。

## 1. 会话分组

- 为账号隔离的原始 parentRef 增加同配置存储内的冗余副本，和主副本同步 commit。
- 同账号主副本缺少条目时可从副本补全；保存当前账号时保留其他账号副本。
- 保留先提交恢复信息再修改数据库的顺序，以及未知归属拒绝猜测写入的保护。
- 未解析会话日志增加记录缺失/恢复备份缺失区分和当前归属。
- 两份数据在同一个 preferences 中，并非独立磁盘备份；不能抵御整个配置文件删除/损坏，也无法找回修复前已经丢失的原始归属。日志涉及的遗留群聊是否能恢复尚未确认，不能宣称此问题完全解决。

## 2. 快捷已读

- 将准备失败区分为方法缺失、数据库 API 未就绪、原生存储未就绪，保留失败诊断。
- DexReady、NetworkDispatcherReady 和用户操作入口可再次调度准备，5 秒节流，不在用户操作内执行 Dex 扫描。
- 对已验证的方法增加被动 Hook，原生调用时捕获正确类型的 receiver；不替换返回值、不主动触发已读。
- 存储未捕获前仍有原生对象图查找回退；找到 receiver 后避免重复查找。
- 未验证分身实际已读行为；若仍失败，新日志将区分失败阶段。

## 3. 消息详情

- 非卡片底部插入优先将 holder 内容提升到 adapter 行的直接分支，避免仅凭 timeTV 父容器在 ConstraintLayout/X2C 内插入失败；系统居中通知保留原路径。
- 帧重试耗尽后保留实际布局事件恢复路径，并保留绑定失败日志。
- PendingLayout 的短时轮询最多八次；反复注册同一行只更新动作，避免重置预算导致每 32ms 无限轮询。
- 新绑定、成功、清理和脱离窗口仍保持取消/有效性检查。
- 布局监听仍会响应实际 global-layout 事件；具体 kn5.d9 / mq 行的显示、位置与复用需要实机验证。

## 4. 群通话铃声与悬浮输入框

- 本地微信 8.0.78 APK 内没有旧 MultiTalkAudioManager 路由字符串，但有 CoreV2 的 startRing 请求入口；smali 确认参数 J/[B/I/Z、roomRole 取反为 outgoing，以及末尾 a4(J) 确认回调。
- 保留已有 CoreV2 定位/确认逻辑，更新失败扫描缓存版本以重新评估旧失败结果；同时保留旧 MT 路径扫描，避免影响旧微信两路共存。
- 仅当 CoreV2 未就绪时将旧群入口缺失报告为失败；有替代入口时输出各路径数量。定位异常、候选过多和确认回调失败仍记录。此次没有新增模糊通话 Hook，也没有把未测的真实铃声屏蔽写为已验证。
- 悬浮输入框禁用时不隐藏原输入框/排队重试；确认输入高度可用后才迁移面板。
- 失败耗尽时恢复原输入框及已经变动的面板/样式；失败提示限次，后续实际布局变化仍可再试。成功重置重试状态。

## 验证

- `python3 scripts/tests/function_recovery/run.py`：生产父级规划策略、生产 PendingLayout observer 的 JVM 桩回归通过。覆盖未知归属保护、可信归属恢复、缺失会话保护、有界重入轮询、后续布局恢复、成功/退出清理、脱离窗口抑制。
- `node scripts/run_message_details_layout_tests.cjs /tmp/pl-kotlin-libs`：7 项布局观察及卡片层级回归通过。
- `node scripts/run_message_details_predraw_tests.cjs /tmp/pl-kotlin-libs`：预绘制回归通过。
- `git diff --check` 通过。
- 这些测试不等于完整 Android feature 编译；未完整编译 ConversationGroupRuntime / QuickMarkReadRuntime / HchatExtraFeature / FooterRuntime / CallRingtoneBlockHooker，亦未实机验证数据库恢复、分身快捷已读、消息详情显示、铃声屏蔽和悬浮输入框。
