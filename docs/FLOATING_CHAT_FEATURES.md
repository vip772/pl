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


## 2026-10-22：首次输入卡片可见性与无描边表面

实机确认标题首帧无闪现、消息无上跳、中段居中正常。新日志头像sourceStatus=fields_empty连续三次，说明img_flag记录存在但reserved1/reserved2为空，未臆造请求内部头像更新接口；既有资料页更新后焦点重试保留。

footer首轮preDraw取消绘制后显式postInvalidateOnAnimation，注册preDraw时也显式请求绘制；初始隐藏后若聊天页临时不可见则恢复原visibility，避免悬置INVISIBLE。不是用延时猜测布局完成。

用户要求悬浮输入框无描边：背景透明处理在floating_chat_footer_enabled时把编辑器设为透明，不创建ChatInputOutline，并清理该编辑器outline样式缓存；关闭悬浮时仍保留已验收的原生透明输入栏描边。悬浮卡片自身的暗色stroke也禁用。

footer预绘制使用既有已缓存GradientDrawable进行常量级背景一致性检查，重新绑定被替换背景、清除残留backgroundTint、恢复Drawable alpha=255，表面颜色仍为0xE6。按颜色/描边模式变化设置stroke，稳定滚动不重复setStroke/requestLayout。FOOTER_SURFACE_REPAIR最多6条，仅含背景身份变化、alpha与tint状态，确认后续是否还有原生写入；未把透明度偶发问题的最终写入来源表述为已实机确认。

验证：Android36 SDK输入框/共享视觉代码定向编译通过（项目辅助API使用桩），输入栏布局、列表padding和背景对齐回归通过，git diff --check通过；完整工程及实机结果以本轮最终报告为准。沉浸背景开启仍掉帧，现有本次日志没有对应慢调用，未据此宣称已解决或猜测新增GPU优化。

悬浮输入卡片深浅模式均取消阴影：elevation与translationZ都置0，预绘制仅在值变化时校正；关闭时恢复原值，保留圆角裁剪。前次完整compileDebugKotlin最终成功（5m6s）；加入最终无阴影修改后再次检查。

最终含无阴影修改的 :app:compileDebugKotlin --offline 成功，耗时43秒；未生成完整APK或执行实机验收。


### 首次调度与输入区统一表面（2026-10-22 后续）

- ChatFooter 原生 onLayout 后补齐首次调度；首次就绪前监听视图树全局布局，就绪和分离时移除。布局回调调度不再 requestLayout，避免重复请求布局。
- 悬浮输入框独立清理输入区域容器、EditText、Button 的 background/tint/foreground，不依赖沉浸背景图片配置；排除 ChatFooterBottom 面板，保留图标 drawable、文字、监听器和输入交互。关闭时恢复已保存表面。
- 保持外层与标题栏相同的 E6 半透明色及零描边、零阴影。布局时重检内部背景，不在 preDraw 扫描子树。
- FOOTER_FIRST_FRAME：等待可见、首次处理失败原因、就绪、就绪后视图树绘制；最多 64 条，普通阶段每实例最多 8 条。记录可见性、尺寸和可见矩形，不记录聊天内容。tree_draw_after_ready 仅说明视图树绘制回调，不能当作输入框实际绘出的证据。
- FOOTER_INPUT_SURFACE：内部控件背景类别、资源名、tint 和前景，最多 24 条；用于确认残余白底或原生背景写回。
- 本轮针对日志已确认的调度缺口和独立背景处理缺口；首次不显示最终根因、实机透明效果仍待安装复测。沉浸式背景滚动掉帧、头像空地址数据源未在本轮解决。


## 2026-10-23：面板展开回归与语音背景分层修正

对照正常基线 `d732440` 与 `89d71aa`，本次运行日志显示：面板状态 2、3 展开后，面板高 867，输入行从 197 被压至 26，footer 上边距从 -223 改成 -52；收起后恢复。后续改动把承载面板和输入列的 RelativeLayout 锁为输入行高度，同时对面板写入 `parent.height - panelHeight` 偏移，并与 footer 圆角裁剪并存。现恢复正常基线的面板参与根容器测量、输入列 BELOW 面板的排列，删除锁高、面板额外偏移及状态回调中被 apply 覆盖的裁剪切换。保留紧凑面板高度限制、输入区域独立测量及生命周期恢复。

用户补充圈中的表情面板拖动区域会导致输入框移位，不能只恢复展示而忽略拖动。原有 scroll Hook 在 isEnabled 后又检测同一启用开关并直接返回，导致悬浮开启时从未运行。删除该重复退出条件，恢复仅在当前聊天 footer 面板可见时，将滚动目标约束为实际 IME inset。8.0.78 原生 ChattingScrollLayout.b(IZII)V 的锚点为 `scrollContentTo: y:%s, targetScroll:%s, alwaysScroll:%s`，参数一写入目标滚动字段，后续驱动 OverScroller；dispatchTouchEvent 还按当前滚动偏移修正触摸坐标。不安装全局触摸拦截，不消费表情面板内部滚动。

语音区核对 8.0.78 原生 t8.xml：bpe 是 WeImageButton；p6x 是按住说话 Button；输入末端 w2n SpeechInputLayout 同级还有独立普通 View wi2（背景 cy4），旧逻辑只处理容器和按钮会漏掉 wi2。保留现有按钮/容器清理，新增同时校验资源名 wi2、普通 View 类型、同父 SpeechInputLayout 的背景层清理，不动图标 drawable。预绘制只重检已捕获输入表面，处理原生模式切换中未触发布局的背景重写；继续排除 ChatFooterBottom 整棵面板子树。关闭时恢复原始背景、tint、foreground。

验证：生产 FloatingChatFooterFeature.kt、FloatingChatCardVisuals.kt、FloatingFooterInputSurfaces.kt 使用真实 Android 36 SDK 及 DexKit 定向编译通过（项目辅助依赖使用桩）；现有生产列表 padding JVM 回归通过；git diff --check 通过。未执行 Gradle 或 APK 构建，未提交或推送。此处原生结构仅核实本机现有 8.0.78 反编译材料，未完成其它微信版本横向验证，也未声称实机展开和拖动已验收。


## 2026-10-23：取消指示条拖动与补齐麦克风背景层

用户确认前次面板展开修复成功，上滑顶部指示条仍移动输入区。8.0.78 原生 EmojiPanelSlideIndicatorView.onTouchEvent 直接读取 rawY，调用 lv5.f0.e(height) 和 h1.a(height)，独立于聊天滚动目标。现只 Hook 此具体指示条，在悬浮启用、祖先 ChatFooter 已接管、祖先 ChatFooterBottom 与状态面板一致且可见时，消费整个触摸序列，不进入原生拖动、速度追踪与回弹；不处理表情列表，不改变已验收的面板排列。

麦克风底色对照 BackgroundBeauty.applySpeechSurfaces/applyChatVoiceSurfaces：ew_.xml 的 SpeechInputLayout 内 wez/wev 是普通背景 View；t8.xml 的 sub/suc 下还有匿名 de7 阴影。前次只补 wi2 同级层，遗漏这些背景。现按相同祖先结构清理普通 View 和语音区 ImageView 的背景/前景，不修改图标 drawable。继续排除 ChatFooterBottom 子树，关闭时恢复原表面。

真实 Android 36 SDK 与 DexKit 定向编译生产输入区、表面清理与共享卡片代码通过，项目辅助依赖使用桩；未运行 Gradle 或生成 APK。原生依据仅核实本机现有 8.0.78，未做其它版本横向验证。新日志附件在当前路径不可访问，未引用其内容；实机手势与底色仍待复测。


## 2026-10-23：沉浸背景聊天滚动热路径

代码确认 BackgroundBeautyRuntime 对 View.setBackgroundDrawable/setBackgroundColor 的全局 after Hook 会收到 RecyclerView 消息复用过程中的背景写入。未接管 View 原先调用 currentActivity/resolveTarget；LauncherUI 的 resolveTarget 通过 chatBackgroundHost/findVisibleDescendant 搜索视图树。即使只是为通讯录延迟行绑定判断，也会在聊天气泡背景写入时执行。已接管表面的回写还调用 resolveTarget，并通过 hasImage 执行文件元数据查询。上述路径在主线程同步执行，属于明确可消除的重复工作；本轮没有设备帧时间采样，不能量化其对总掉帧的占比。

修正：未接管写入先检查已维护的页面状态，只有存在通讯录页面时才按最多 12 层父链匹配已登记的通讯录列表；不再查询当前 Activity 或聊天视图树。已接管表面通过 snapshot.slot 和 state.loadedBitmaps 校验归属及有效图片，不在 setter 内查文件。图片变更仍由现有配置刷新/加载/恢复路径处理。背景位图画质、屏幕对齐、布局和刷新率保持原逻辑；该功能没有 preferredRefreshRate/setFrameRate/显示模式写入，因此没有强制提高刷新率。

验证：新增 check_write_hot_path.py 提取并执行生产未接管写入代码，覆盖 10 万次聊天绑定、通讯录延迟行、混合列表和已接管旁路，并检查 setter 中没有页面解析或图片文件查询。现有背景屏幕对齐、窗口变化、侧滑和反馈回归通过；真实 Android 36 SDK + DexKit 定向编译 BackgroundBeautyFeature/Store/Settings/ChatInputMessageUnderlay/ChatInputOutline 通过（项目辅助 API 使用桩）。未运行 Gradle 或生成 APK，未完成实机 FPS/帧时间验证，不声称已消除所有掉帧。


## 2026-10-23：悬浮组件稳定更新开销优化

保留现有首次调度、面板排列、触摸守卫、背景维护及提示位置更新频率，仅减少重复写入与分配。标题提示卡片仅在规则或边距实际改变时合并一次 layoutParams 写入；轮廓 provider、裁剪、elevation、背景及 padding 按变化设置，删除稳定更新中的无条件 invalidateOutline（尺寸及 provider 变化仍由 View 更新轮廓）。胶囊仅在原生返回/更多/标题点击目标身份变化时重绑监听器，每次仍重新确认目标，保留原生控件替换后的点击转发。输入框每会话复用两组坐标数组，每帧仍读取实际位置，不缓存坐标值。

验证：Android 36 SDK 标题栏、胶囊、输入框及共享表面生产源码定向编译通过（项目辅助 API 使用桩）；提取生产提示卡片布局更新代码，验证首次布局、连续 10000 次稳定更新无额外布局写入、原生规则/边距被改写后重新修复。未运行 Gradle 或构建 APK，未实机确认帧率提升。按用户要求不提交推送。


## 2026-10-23：移除三项美化功能的运行时诊断

清理沉浸式背景、悬浮输入框、悬浮标题栏及其绘制辅助类的普通诊断日志、限量计数器、性能计时调用、首帧绘制观察器、菜单/状态区/窗口/页面树快照、诊断专用 Hook 和延迟任务。头像空地址时用于区分来源状态的 img_flag 只读查询不参与头像加载，随诊断一起移除；下载、缓存、失败重试与错误处理保留。ChatInputOutline 的首绘计数与时间戳移除，线宽和坐标计算保留。

功能与诊断混用的状态单独保留：任务栏 y(float) 后置背景保护迁入正常 Hook 安装；公告卡一次置顶、状态栏首次透明处理继续由功能状态标记管理；首次布局等待、失败恢复、面板高度/触摸、背景重写维护、系统栏透明、列表留白和头像重试保持。保留 HLog.e/w 必要错误/降级日志，未改共享 PerformanceSample 本体及其它功能调用。前一轮未提交的重复布局/监听器与坐标数组优化保留。

验证：真实 Android 36 SDK 标题栏/胶囊/输入框/共享视觉与背景相关生产源码定向编译全部通过（项目辅助依赖使用桩）；背景对齐/窗口变化/侧滑/点击反馈回归、输入区列表 padding 回归和背景写入热路径回归通过。扫描两个功能目录未剩余普通诊断日志、性能采样、探针或快照引用，git diff --check 通过。未运行 Gradle、未构建 APK、未提交或推送；实机交互仍待复测。


## 2026-10-23：四个主页的沉浸背景低风险开销优化

检查微信、通讯录、发现、我四页共用的行绑定、预绘制、触摸反馈、透明表面与恢复路径。四页 setTransparentSurface 原先也按聊天 AppPanel 逻辑反复查祖先，但这些结果在非聊天 slot 不参与反馈结果；现仅在聊天且需要反馈时执行，四页不再为此查祖先。预绘制每行复用一次 surface snapshot 查找，保留原有 dirty/未接管/通讯录背景被覆盖时的处理与检查频率。

触摸反馈在非 DOWN 且没有正在跟踪的反馈时直接返回：手指开始纵向滚动并清理反馈后，后续 MOVE 不再重复查询偏好、Activity 与位图。DOWN、尚在跟踪的 MOVE 和结束清理仍走原路径。表面几何恢复仅在 padding/minimumWidth/minimumHeight 实际变化时回写，减少无条件 setter 及潜在布局请求；原生改写仍恢复。

验证：背景生产源码真实 Android 36 SDK + DexKit 定向编译通过（辅助项目 API 使用桩）；生产几何恢复方法提取测试覆盖 10000 次稳定调用零写入与原生修改后恢复，反馈策略布尔组合等价检查通过；已有点击/侧滑/背景对齐/窗口变化回归通过。保留原有四页绘制、列表绑定、通讯录图标保护和点击区域识别，不修改图片质量或刷新率。无实机 FPS/帧时间对比，不能声称掉帧全部消除；未运行 Gradle 或构建 APK，未提交推送，之前未提交的优化与诊断清理均保留。


## 悬浮输入框浅色发送文字修复

微信 8.0.78 的 t8.xml 中发送 Button 为 bql，悬浮模式清除其背景后，原生白色文字在浅色卡片上对比不足。仅对已接管的 bql 按钮在浅色模式设置深色文字，深色模式使用接管前保存的原生 ColorStateList；退出悬浮时恢复原生文字颜色。预绘制维护仅在颜色对象变化时回写，以覆盖原生重设和模式变化。未改变发送事件、按钮可见性或其它输入控件文字。

## 悬浮输入框＋号与发送按钮交叉叠字

截图与8.0.78本地ChatFooter反编译对应：u0(int,boolean)进入o0(int)时，G与p同时可见，执行200ms淡出和延迟100ms的200ms淡入；透明按钮底色可能暴露交叉叠字。悬浮开启、当前footer面板已重排且mode为1/2时，仅将u0第二参数改为false，走原生w取消旧动画及settleInputModeSwitchViews互斥可见性/属性复位。继续执行原生其余模式逻辑、发送事件和点击处理，不设置固定宽度、不按文本长度推断发送状态。

仅精确versionName=8.0.78并验证u0(int,boolean):void签名；其他版本不安装该修正，未完成横向适配。现有本地材料可核对，未新增猜测字段或DexKit候选。此路径为与截图吻合的修正，偶发故障实机复现及修复效果仍需新包验收。验收快速输入/清空、发送后清空、语音/文字、表情/＋号切换，以及关闭悬浮后的原生动画。未发送测试消息。
