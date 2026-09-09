# 悬浮聊天组件

新增功能位于 `实用 > 美化`：

- `悬浮标题栏`：识别微信聊天页的 `androidx.appcompat.widget.ActionBarContainer`，重挂载到内容根布局，应用边距、顶间距、阴影层级和深色卡片视觉。
- `悬浮输入框`：识别聊天页的 `com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom`，重挂载到内容根布局底部，应用侧边距、底部间距、阴影层级和深色卡片视觉。

两个功能都通过 `BaseFeature`、`WeChatApis.lifecycle()` 和 `DexInstallScheduler` 接入，进入聊天页时应用，Activity 销毁或功能销毁时恢复原父容器和布局参数。功能代码位于 `hooks/items/floatingchat/`。

当前运行时定位依赖微信公开稳定类名；方法级布局状态机仍需在目标微信 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76、8.0.77 的 R8 APK 上逐版本验证。未完成横向 APK 验证前，不应将其视为所有版本已确认兼容。
