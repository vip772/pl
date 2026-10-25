# 设置页底部操作栏避让修正

代码修正已完成，未实机验收。

## 原因
PageScaffold 原先把 Miuix Scaffold 的 bottomBar 设为空，将真实操作栏放在外部 Box 底部叠加。传给内容的 PaddingValues 不包含操作栏高度。动态角标、消息菜单扩展只加 12dp，最后一项容易被遮挡；其他页面用 84dp 或 78dp 猜测操作栏高度，字体和窗口变化时不能保证正确。

## 修正
真实操作栏移动到 Miuix Scaffold 的 bottomBar 测量槽中，导航按钮避让包含在该槽内。Scaffold 在布局同一轮将完整实测高度传给内容，不再额外叠加相同导航边距；无底栏页面仍独立保留系统导航避让。固定操作栏位置与按钮事件保持原逻辑。

所有使用该共用组件的页面移除原来的固定 84dp/78dp 高度补偿，统一保留测量后额外 12dp 的内容末尾间距，防撤回页移除自身重复测量补偿。影响面覆盖共用设置页面，因此不止新功能分组；这是避免共用修正引入双重高度的配套调整。

逐一检查实际 isNewFeature 注册：动态角标、艾特扩展、群聊分析、输入框提示、资料页显示、进入时间/邀请人、悬浮标题栏和悬浮输入框，都使用 PageScaffold 并消费其底部 padding。额外检查首页扩展、首页侧栏、首页文字颜色、自定义底栏、聊天工具栏、缓存、步数等独立页面。动态角标和消息菜单扩展无需在页面中再添加固定高度，直接获得共用测量结果。

## 依据与验证
核对项目依赖 Miuix 0.8.8 源码 ScaffoldLayout：先测量 bottomBarPlaceable，再将其 height.toDp() 写入 contentPadding.bottom（空底栏时用系统 inset），最后在页面底部放置。源码来源：https://repo.maven.apache.org/maven2/top/yukonga/miuix/kmp/miuix-android/0.8.8/miuix-android-0.8.8-sources.jar ，commonMain/top/yukonga/miuix/kmp/basic/Scaffold.kt。

scripts/test_settings_bottom_bar.py 提取并编译执行生产 PageScaffold 函数体，使用简化测量模型验证不同栏高（48/68/110/164dp）、系统底部边距（0/24/48dp）、无底栏、不重复避让，以及独立页面消费底部 padding；不是 Android/Compose 渲染测试。源码 diff 检查通过。未运行 Gradle、未构建 APK、未操作微信；大字体、横屏/小窗、真实 Miuix/Backdrop 渲染与页面末项操作仍需新包实机验收。

与艾特提醒及配置备份修正一并整理提交；实机验收仍待进行。