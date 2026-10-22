# 悬浮聊天组件

新增功能位于 `实用 > 美化`：

- `悬浮标题栏`：识别微信聊天页的 `androidx.appcompat.widget.ActionBarContainer`，重挂载到内容根布局，应用边距、顶间距、阴影层级和深色卡片视觉。
- `悬浮输入框`：识别聊天页的 `com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom`，重挂载到内容根布局底部，应用侧边距、底部间距、阴影层级和深色卡片视觉。

两个功能都通过 `BaseFeature` 和 `DexInstallScheduler` 接入，使用微信聊天布局生命周期 Hook 驱动。运行时只处理与屏幕横向相交的当前 `ChattingUILayout`，排除微信预加载在屏幕外的相邻会话。

布局更新按会话实例维护状态，并通过一次性 `OnPreDrawListener` 合并同一帧内的 attach、layout、面板和提示条刷新。输入区在首次定位完成前暂时隐藏，完成面板重排、边距与高度计算后恢复，避免先绘制在屏幕外再跳入。键盘位置使用 `WindowInsets.Type.ime()` 与微信 `ChattingScrollLayout` 的原生滚动入口协调，不通过猜测屏幕可见高度或直接平移输入框实现。

`ChatTipsBarGroup` 单独跟踪实际实例，分别管理内容 RecyclerView、item 根容器和第一层内容容器的背景及布局恢复；公告卡下沿与顶部新消息提醒使用同一纵向坐标区域计算，提醒条始终排列在公告卡之后。Activity/视图销毁或功能销毁时恢复原父容器、布局参数、背景和监听器。功能代码位于 `hooks/items/floatingchat/`。

当前运行时定位依赖微信公开稳定类名；方法级布局状态机仍需在目标微信 8.0.49、8.0.58、8.0.66、8.0.68、8.0.72、8.0.74、8.0.76、8.0.77 的 R8 APK 上逐版本验证。未完成横向 APK 验证前，不应将其视为所有版本已确认兼容。


## 2026-10-22：悬浮输入框与沉浸背景协同、头像重试

源码确认的冲突：背景 inputUnderlay 会在原生布局后扩大 bki 并增加列表 padding；悬浮 footer 又用负 topMargin 与列表 padding 预留输入卡片，叠加会重复处理底部空间。悬浮开启时停止背景 underlay，关闭时保留已验收的原生透明输入栏路径。背景透明化和背景 setter 回写均避让 FloatingChatCardVisuals 管理的卡片，继续处理原生子容器。

footer 原每帧从 rootView 深搜 ChattingContent，现缓存当前 scroll 下的同级 content，排除跨聊天页命中；重复样式设置改为按变化写入。保留新消息提示位置更新，并通过既有 PerformanceSample 增加 floating_footer_predraw 的限量慢调用记录。

列表 padding 更新删除主动 scrollBy 补偿，首次成功重排取消中间帧绘制，等待下一次布局再显示。输入卡片使用与三段标题栏相同的 0xE6 表面 alpha，其他卡片默认颜色保持原值。

头像旧路径失败后依赖后续布局且间隔30秒；现每个会话代次最多三次尝试、失败后2秒主动重试，仍校验代次与控件状态。HEADER_AVATAR_RESULT / HEADER_AVATAR_HTTP 分别限量3条，仅记录源数量/尝试次数/HTTP状态。现有日志未给出本次头像偶发失败的最终原因，不将重试机制修正等同于头像问题已实机验收。

验证：真实Android36 SDK下标题栏与footer定向编译（项目辅助API使用桩）、原生绑定静态检查、输入栏布局回归、生产footer padding JVM回归通过。完整工程 compileDebugKotlin 停留编译阶段并等待超时，未获得成功结果；实际画面、滚动性能与头像联网结果仍待实机验收。
