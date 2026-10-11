# 首次进入角标缺失与首条动态撤回排查

## 证据

输入 Hchat-runtime-20261011-175616.log、LSPosed_20261011_175631.zip、用户截图。
运行日志现代绑定诊断有 40 条 rowKnown=false，限次日志范围内没有 rowKnown=true；Hook 安装成功并不代表这些行挂载角标成功。
现代绑定原实现仅查现代 Holder 创建 Hook 建立的缓存，缓存未命中直接返回。晚安装与已有 Holder 复用会遗漏创建事件。
原 BadgeOverlay 只在构造时注册 host.viewTreeObserver，未保存实际观察器或在新窗口重新注册；恢复可见性依赖此监听，因此暂停后可能无法重新启动。它也永久引用首次列表宿主，跨父容器复用会留下错误绘制宿主。
动态撤回标记原来只在高级 handleRecall 路径保存；同步安装的 SQLite 自撤回保护和原生存储拦截路径只保护消息内容，没有写动态描边索引。这是明确的路径覆盖缺口。日志没有首条撤回调用链/消息 ID 诊断，不能断言用户首条必然走了此路径。
最新 LSPosed 模块日志未找到动态角标/动态撤回自身异常；仍有会话归属备份缺失和快捷已读失败，属于其他问题，未在本轮改动。

## 本地修改

- Hook 安装后与微信 Activity 恢复时，扫描实际 ConversationListView 当前子行，不等待用户滚动再触发 bind。
- 现代 Holder 缓存缺失时，从 View 字段沿层级寻找真实 ConversationListView 直接子行；只接受唯一结果，不猜其他容器。
- Holder 创建也排队处理行，支持附着后恢复。
- BadgeWindowObserver 保存窗口观察器，支持 detach/attach、死观察器合并迁移、销毁后旧快照失效；更新时重新检查窗口。
- 跨行/宿主复用重建覆盖层，已离窗或销毁后的排队绑定不挂载。
- 自撤回 SQLite 保护与存储拦截同步保存原消息的动态描边标记；继续遵守防撤回/保留自己撤回开关，仍使用账号隔离索引，原消息不可解析时不制造记录。
- 不修改 QQ 点歌，不启用危险原生音频路径。

## 验证与限制

`python3 scripts/tests/dynamic_badge_recall/run.py` 通过：真实 BadgeWindowObserver 与 DynamicRecallStore 在 JVM 桩中验证观察器迁移、重复附着、死观察器合并、销毁/旧快照、首条标记保存、本地/服务器 ID、账号与会话隔离。
`node scripts/run_message_details_predraw_tests.cjs /tmp/pl-kotlin-libs` 通过。
`git diff --check` 通过。
未运行 Gradle/构建 APK，未完整编译 DynamicBadgeFeature / AntiRecallFeature，未实测实际 Holder 回退、截图绘制或首条撤回。JVM 索引测试不等于原生撤回 Hook 集成测试；账号 API 或原消息未就绪仍可能无法保存标记，需要实机复现验证。
本轮尚未提交推送。