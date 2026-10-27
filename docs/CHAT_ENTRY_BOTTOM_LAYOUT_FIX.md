# 聊天入口底部背景与首次避让修复

基线 fbc8a17，依据 2026-10-27 两张入口截图及 Hchat-runtime-20261027-133252.log。此次只保留本地修改，不提交、不推送。

## 源码定位

- 最新图标提交只替换工具栏 Drawable，没有改布局参数和底部背景，不能根据出现时间直接认定自绘图标导致问题。
- BackgroundBeautyFeature.applyNativeChrome 对 CHAT 场景只允许 ActionBarContainer，提前排除了 FrostedContentView。透明化 View.background 不会清除其独立绘制的上下磨砂。8.0.78 Smali 的 dispatchDraw 在 frostedEnabled 分支执行 drawColor，getFrostedEnabled/setFrostedEnabled 已在现有功能中使用。本次允许聊天背景管理范围内的 FrostedContentView 复用原有压制、保存和恢复机制，仍不放开聊天场景的 Bounce 类处理。
- 悬浮输入区 apply 修改层级/边距后，applyChatListPadding 遇到 isLayoutRequested 会直接返回，但 apply 原先无条件返回 true，首屏事务被提前标记 ready。下一次布局才补避让，会暴露旧消息行边界。
- 非面板状态使用首次缓存的 baseInputHeight，工具栏后插入或输入行高度变化后可能仍用旧高度。

## 修复

列表避让显式返回完成状态：等待测量、修改 padding 后均返回未完成，只有布局稳定且目标 padding 已应用才返回完成。沿用已有 pre-draw 和最多三次重试，未完成阶段取消本次绘制并要求下一次原生布局，超限恢复原生输入区，避免无界重试。首次成功后沿用已有首帧屏障。

输入区每次布局以实际测量的输入列高度为准，面板高度仍独立计算，仅为真实重叠保留空间，不重复叠加整块输入区。屏幕坐标数组移到 FooterState 复用。

没有新增滚动监听、数据库访问、定时轮询、逐帧树扫描或主动 scroll/selection。背景处理复用现有已接管实例的机制，不全局关闭其他页面磨砂。没有新增配置和备份项。

## 验证与限制

生产方法抽取 Kotlin/JVM 验证：首次层级调整、padding 布局、稳定后展示；三次失败后回滚；功能关闭清理；CHAT 对 FrostedContentView、ActionBarContainer 与 Bounce 的范围区分。

现有生产方法测试覆盖真实重叠、无重叠、1000 次稳定避让不重复写 padding、不主动滚动、面板高度/开关/回滚、输入模式切换、透明度端点与 Android SDK 卡片类定向编译，以及背景字段访问缓存。

日志未发现相关功能异常，唯一 ERROR 仍为历史群归属恢复依据缺失；日志不含入口时的 View 树和布局坐标，不能据此完成实机根因闭环。当前修复为源码与 Smali 证据支持的缺陷修正，未运行 Gradle、未构建 APK、未完成新包实机验收。需核对微信主页和虚拟分组两种入口的底部背景一致性、冷启动首次最后消息、返回重入、键盘和面板切换、工具栏显示模式与多行输入。
