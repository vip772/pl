# 群公告透明与分组返回布局修正

基线7aa7e34。用户已确认深浅模式切换修复；本次输入为三张截图，无新增运行日志或mapping。内部结构依据已保存的微信8.0.78 APK/Dex重新反编译核实。仅改dev，不运行Gradle或本地APK构建。

## 群公告

classes3.dex的ChatTipsBarGroup构造器加载layout/s3（2131624692）；字段e为hyi RelativeLayout，c()/d()展开与折叠时重新设置背景。s3.xml包含ow1/ovv底层遮罩、hyi卡片背景afn、ovt阴影bce与m98 RecyclerView。s4.xml的提示行根FrameLayout还带独立bce背景，单清外层无法完全透明。

新增限定ChatTipsBarGroup范围的透明处理，清除上述背景/前景及列表项普通容器背景，保留文本、图标drawable、点击、可见性和原生布局。在聊天栏扫描、公告附着、展开/折叠及布局回调执行，覆盖延迟创建与复用项；背景改写继续由现有托管机制保护。新增限频CHATPART notice-before/after。

## 自定义分组返回

BaseConversationUI.access$100首次创建内嵌聊天时，将原action bar/content从decor移入HomeUI.FitSystemWindowLayoutView，调用requestFitSystemWindows及h7；h7把实测顶部Insets送入fitSystemWindows。后者分配聊天Insets，最后调用父FrameLayout处理。BaseConversationUI.enableEdge2Edge()返回false。模块原applySystemBars却给此宿主整体添加LAYOUT_FULLSCREEN/LAYOUT_HIDE_NAVIGATION，退出clearActivity再恢复这些标志，导致原生重包裹后的Insets布局与模块全屏策略在同一窗口切换。

修改为BaseConversationUI及其子类不修改这些systemUiVisibility布局位，始终保留宿主原生策略；仍处理聊天图片、控件透明及栏颜色，LauncherUI逻辑不变。没有按截图硬设padding/margin/translation。新增GROUPRETURN close/close-frame记录systemUiVisibility、包装层padding、内容Y及padding，帮助实机确认。

当前证据确认存在相互冲突的布局策略；缺少此次运行日志，不能声称已确认图3每一段空白的精确贡献或已实机消除偏移。

## 验证

独立JVM背景对齐、反馈及侧边手势回归通过，git diff --check通过。未运行应用编译、Gradle或APK构建；回归脚本不覆盖真实Android Insets、公告布局或返回动画。本次只核实微信8.0.78。

实机复测公告收起/展开、进入后异步出现的公告、公告点击；分组列表首次进入→聊天→返回，反复返回与侧滑取消；比较标题栏位置及顶部空白，检查分组聊天上下透明及背景连续性。若偏移仍存在，需要本次GROUPRETURN日志以核实实际Insets与内容坐标。