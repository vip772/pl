# 悬浮聊天组件

新增功能位于 `实用 > 美化`：

- `悬浮标题栏`：识别微信聊天页的 `androidx.appcompat.widget.ActionBarContainer`，重挂载到内容根布局，应用边距、顶间距、阴影层级和深色卡片视觉。
- `悬浮输入框`：识别聊天页的 `com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom`，重挂载到内容根布局底部，应用侧边距、底部间距、阴影层级和深色卡片视觉。

两个功能都通过 `BaseFeature` 和 `DexInstallScheduler` 接入，使用微信聊天布局生命周期 Hook 驱动。运行时只处理与屏幕横向相交的当前 `ChattingUILayout`，排除微信预加载在屏幕外的相邻会话。

布局更新按会话实例维护状态，并通过一次性 `OnPreDrawListener` 合并同一帧内的 attach、layout、面板和提示条刷新。输入区在首次定位完成前暂时隐藏，完成面板重排、边距与高度计算后恢复，避免先绘制在屏幕外再跳入。键盘位置使用 `WindowInsets.Type.ime()` 与微信 `ChattingScrollLayout` 的原生滚动入口协调，不通过猜测屏幕可见高度或直接平移输入框实现。

`ChatTipsBarGroup` 单独跟踪实际实例，分别管理内容 RecyclerView、item 根容器和第一层内容容器的背景及布局恢复；公告卡下沿与顶部新消息提醒使用同一纵向坐标区域计算，提醒条始终排列在公告卡之后。Activity/视图销毁或功能销毁时恢复原父容器、布局参数、背景和监听器。功能代码位于 `hooks/items/floatingchat/`。

当前运行时定位依赖微信公开稳定类名；方法级布局状态机仍需在目标微信 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76、8.0.77 的 R8 APK 上逐版本验证。未完成横向 APK 验证前，不应将其视为所有版本已确认兼容。
