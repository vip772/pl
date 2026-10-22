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


## 2026-10-22：首帧、运行时开关与性能分项取证

日志：floating_header 主线程19/22/22ms；头像连续三次 sources=0，未进入有效地址下载，不能归因于HTTP失败。

标题 preDraw 在重排、清除顶部占位后原来仍返回 true，导致旧bounds与新层级同帧显示。现在检测聊天布局及父层的待布局状态，最多三轮取消中间帧并重新调度，避免无限阻止绘制。聊天内容查找由rootView全树收集改为当前ChattingUILayout内bki定位；轮廓与裁剪按变化更新。

两个功能原来关闭时被FeatureManager跳过安装，运行时没有偏好监听。现在始终安装有开关守卫的生命周期钩子，直接监听Hchat_global_config中的对应开关，在主线程刷新现存可见布局；销毁时注销监听。配置通知回调同时保留。

背景的ChattingUILayout/ChatFooter生命周期处理原通过view.post排队设置描边。现改为原生布局结束后同步准备footer透明与描边，不再在此路径重复全聊天根透明及underlay处理（完整刷新和scroll布局路径仍保留）。state尚未建立时仍通过scheduleRefresh建立，不宣称已覆盖所有延迟来源。

PerformanceSample新增floating_header_cards、floating_header_capsule、chat_input_transparency，沿用启动180秒内慢调用每类最多3条；结合floating_footer与floating_footer_predraw区分耗时，不输出消息文字。头像诊断增加contactsReady/group两项布尔值；现有证据不足以确认img_flag空地址是数据缺失还是会话/服务时序，不替换未核实的数据源。

验证：真实Android36 SDK标题栏及footer定向编译（项目依赖使用桩）、输入栏透底/列表padding/顶部占位回归、8.0.78原生绑定检查通过。完整工程compileDebugKotlin等待超时，未获得成功结果；首帧、动态开关、描边与性能改善尚未实机验收。


## 2026-10-22：中段居中、背景写回冲突和首帧取证

头像与昵称改为WRAP_CONTENT组合居中、两侧8dp对称内边距，昵称按中段剩余宽度设置maxWidth并保持单行省略。保留原生返回、标题和更多点击入口。

三段展示创建时在FloatingChatCardVisuals登记外层与展示控件所有权，沉浸背景现有透明化与setter监听避让这些控件，恢复时释放。日志曾在HEADER_SPLIT_APPLIED附近反复记录ActionBarContainer background=null随后被背景监听写回；此处消除确定的双向覆盖。菜单备用Drawable改为惰性获取，原生nwr标签或文字已命中时不再加载备用资源；文字颜色、无障碍属性与描述按变化写入。

首次标题重排前移到当前聊天布局原生onLayout之后，并允许屏幕右边界上的进入布局准备；后续仍合并到preDraw。移除已验收状态区大树快照调用，新增HEADER_FIRST_READY（最多6条）与HEADER_WAIT（最多3条），不以重排完成日志等同实机首帧验收。

背景缓存目标矩形的尺寸与偏移，仍每帧检查位置以保留键盘/侧滑对齐。移除Drawable每实例8条draw日志，增加限量background_bitmap_draw_cpu慢调用采样（只代表CPU提交，不代表GPU耗时）；导航区dispatchDraw钩子使用已应用状态，避免每帧resolveTarget及图片文件检查。BACKDROP_HOST最多8条记录宿主类型与位图尺寸。

描边HostState可在图片异步加载完成前建立；新增INPUT_OUTLINE_SET与INPUT_OUTLINE_FIRST_DRAW各最多6条，区分描边安装延迟与首次绘制延迟。头像在空URL时于后台执行同表只读查询，sourceStatus区分query_unavailable/row_missing/fields_empty/fields_present_after_lookup，不记录用户名或URL。头像仍为空时，窗口重新获得焦点会开启新一轮有上限重试，保留代次防串图校验。

验证：真实Android36 SDK标题/输入栏定向编译、背景屏幕对齐/窗口变化/侧滑JVM回归、顶部padding/输入栏布局/列表padding回归和原生绑定静态检查通过。完整工程首轮编译暴露缓存字段漏声明，已修正；重跑停留compileDebugKotlin等待超时，尚无成功结果。APK构建和本轮视觉/性能效果未实机验收。
