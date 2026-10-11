# 动态角标首次列表延迟填充修复（第二轮）

用户确认动态撤回正常，因此本轮不修改 AntiRecall 或 DynamicRecall。
依据 18:46 与 19:01 两份运行日志及用户反馈：首次角标不完整，等待/多次刷新后完整。

## 原因与修改

此前 install/resume 的可见行补扫只执行一次。窗口、ConversationListView 或其子控件晚于补扫创建时没有后续恢复，Holder 回退也可能在未附着时失败。现有窗口描边监听只管理已有覆盖层，不负责发现从未建立覆盖层的角标。

新增 BadgeListRecovery：监听窗口实际 global-layout，合并到下一动画帧，扫描真实 ConversationListView 当前子行，只给缺失/宿主失效的角标入队。窗口附着后额外四次有限重试（100/300/1000/3000ms），不是永久轮询。安装时同样有限重试获取 Activity/window，解决入口尚未就绪。detach/dispose 移除队列、定时回调和监听。
同一行绑定排队去重，避免同帧多个 Hook/布局事件重复扫描。恢复设置时主动补扫。
Holder 诊断输出 cached 与 rowResolved，结果取回退解析后的实际值，原 rowKnown=false 不再作为模糊诊断。
关闭默认动画启停状态诊断，避免两份日志中数百/上千条非故障状态反复写文件；保留限次定位信息与错误。

## 其他日志问题的处理边界

快捷已读两轮初始 native_storage_not_ready 后均在首次后台准备时成功，无停止重试记录。该阶段不能按持续故障修改，也未屏蔽其诊断。
遗留群聊 original parentRef 已丢失且冗余副本是在丢失后新增；当前父级为模块分组，不能凭当前数据推断原始归属。本轮保留 ERROR 与安全写入保护，未自动改为根列表，未宣称恢复完成。仍需可信旧配置/明确归属确认后进行修复。
通话铃声有 CoreV2 替代入口；无新定位失败，真实屏蔽效果仍待验证。
QQ 点歌保持暂停。

## 验证

python3 scripts/tests/dynamic_badge_recall/run.py：通过生产 BadgeListRecovery / BadgeWindowObserver / DynamicRecallStore 的 JVM 桩回归，新增先空后有列表、多个布局同帧去重、有限重试、detach/dispose 清理；已有窗口迁移及标记隔离保持通过。
node scripts/run_message_details_predraw_tests.cjs /tmp/pl-kotlin-libs：通过。
git diff --check：通过。
未做 Gradle/APK 构建，未完整编译 DynamicBadgeFeature，未实机测首次全量显示或帧率。本轮尚未提交推送。