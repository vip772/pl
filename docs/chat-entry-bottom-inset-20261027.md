# 首次聊天末条消息遮挡：定位与修复

## 证据

用户提供的 Hchat-runtime-20261027-154928.log：

- 15:47:35.788：footer-ready，wasReady=false。
- 15:47:35.843：footer-settled，complete=false，ready=true。结构完成未代表列表避让完成。
- 15:47:36.286：listH=3120，footerY=2740，extra=380，base=20，current=20，target=400。
- 15:47:40.209：current=400；15:47:45.503：current 又变成 20。
- 后续多次重复 400 -> 20 -> 400。单纯延长首次 pre-draw 重试不能解决其他路径重放旧 padding 的问题。

微信 8.0.78 反编译目录：

- smali_classes10/com/tencent/mm/pluginsdk/ui/tools/ChattingRecyclerView.smali：继承 RecyclerView；声明 onLayout(ZIIII)V，内部直接调用 RecyclerView.onLayout。提供 getCount/getLastVisiblePosition，后者读取 LinearLayoutManager。
- res/layout/ss.xml 与聊天输入相关结构：消息列表和原生 ChatFooter 的几何布局与悬浮卡片实际覆盖区域不同，必须按实际重叠计算，不能重复叠加完整输入高度。

## 源码原因

1. BackgroundBeautyFeature.restoreSurfaceGeometry 用 SurfaceSnapshot 的历史 padding 恢复聊天 RecyclerView；快照可能早于悬浮标题栏/输入框的避让。背景修复路径会覆盖这些功能的实时留白。
2. FloatingChatFooterFeature.applyChatListPadding 在 isLayoutRequested 时返回；之前的三次 pre-draw 等待结束后允许绘制，列表此后未必及时再有合适的 footer 回调。
3. 修改 RecyclerView padding 不能单独保证已显示的最后一行同步向上移动；原代码只检查 padding 数值，没有保持修改前已经成立的末尾锚点。

## 修改

- 背景恢复不再重放聊天 RecyclerView 的旧 padding，其他表面的恢复行为保留。
- 在已验证的 ChattingRecyclerView.onLayout 前按实际几何重叠设置 padding，让原生列表布局直接消费正确的底部留白。footer.onLayout 的最终几何也可直接触发避让，不再因 View.layout 返回前尚未清除的布局标志退出。
- ChatListBottomInset 仅在 padding 变化且列表静止、原本末行贴底时记录锚点；布局后按剩余遮挡补偿一次。原生已补偿时不重复移动；数据新增/删除、拖动、惯性滑动、历史浏览和短列表不主动定位。
- 没有新增定时器、滚动监听、常驻 pre-draw 监听或逐帧树扫描。反射方法按列表实例缓存；没有待处理锚点时布局后处理直接返回。

## 验证

仅 Android SDK 定向编译与自动化/静态检查；没有完成本地 APK 构建，没有真机安装复测，不宣称已实测所有开关组合下的帧率。

通过：完整 FloatingChatFooterFeature 及关联生产类对 Android 36 SDK / DexKit 定向编译；背景几何恢复生产方法 SDK 编译；test_chat_list_bottom_inset、test_chat_background_padding_owner、test_floating_footer_padding、test_chat_entry_layout、test_floating_footer_panel、test_floating_header_top_inset、test_floating_header_viewport_inset、test_chat_input_underlay、test_background_render_gate；git diff --check。

原先已存在的未跟踪 scripts/test_chat_entry_measurement.py 保留，不纳入本次提交。
