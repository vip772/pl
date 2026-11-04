# 模式切换后微信页状态栏遮挡

基线 cd75778；证据来自 Hchat-runtime-20261015-105824.log、同提交 Release mapping 和微信8.0.78 APK。前三项语音阴影、返回过渡、按住说话透明已获用户实机确认，本次只补微信页状态栏遮罩处理。

日志10:58:17.108及后续帧：id=2131389426（og9）是1440×129普通View，背景ColorDrawable=-1184275，managed=false，位于FrostedContentView内，与主页内容容器同级。nhm（2131387734）则已使用oi.l0透明Drawable，managed=true。mapping确认oi.l0为BackgroundBeautyTransparentDrawable、oi.m0为ScreenAlignedBitmapDrawable。10:58:19.488系统栏bars=0/0，系统statusBarBackground不可见，窗口底图仍有效。因此本次证据指向微信自己的og9实色遮罩，而非系统状态栏颜色写入失效。日志为进程中途片段，不包含完整模式切换过程，不据此推断切换中每个回调的执行顺序。

8.0.78 MainUI.B0()（onTabResume）从controller.g查找2131389426，缺失时创建普通View，设置宽度全屏、高度gl.i(...)，并每次setBackgroundColor(2131101972)。MainUI.r对应nhm，F0()调整其状态栏高度。现有preparePage扫描主页内容树，不能覆盖controller额外创建的同级og9；公共栏扫描又跳过CustomViewPager内部，缓存preparedRoots不会因同级View新增而失效。

修改：

- 从LauncherUI.decor定向查找og9和nhm，不依赖fragment根或preparedRoots缓存；只在启用功能且配置微信页背景时处理，使用现有SurfaceSnapshot保存、透明化与恢复机制。
- MainUI.B0()、onConfigurationChanged(Configuration)后处理；配置变化额外在下一绘制帧检查一次，覆盖同一轮布局更新。
- applySystemBars()中补定向处理，覆盖初次创建或Hook触发时状态尚未准备好的情况。背景托管后复用现有setBackgroundDrawable/setBackgroundColor保护，保存宿主最新颜色供关闭功能恢复。
- 新增限频WXSTATUSMASK：phase、name、处理前背景类型/颜色、处理后类型、尺寸和当前页面。

验证：git diff --check通过；独立JVM背景/反馈/手势回归通过。未运行Gradle、本地应用编译或APK构建，这些检查不验证真实Android模式切换。内部结构只核实8.0.78，未作其它版本兼容性声明。

实机检查：浅色→深色→浅色，每次停留微信页查看状态栏，切到通讯录/发现/我后回到微信页；再进入聊天返回、下拉小程序并收起；关闭功能确认原始背景恢复。日志关注WXSTATUSMASK name=og9 after=模块透明Drawable，以及原CHROMEDRAW中og9 managed=true/retained=true。