# 设置页底部操作栏避让修正

代码修正已完成，未实机验收。

## 原因
PageScaffold 原先把 Miuix Scaffold 的 bottomBar 设为空，将真实操作栏放在外部 Box 底部叠加。传给内容的 PaddingValues 不包含操作栏高度。动态角标、消息菜单扩展只加 12dp，最后一项容易被遮挡；其他页面用 84dp 或 78dp 猜测操作栏高度，字体和窗口变化时不能保证正确。

## 修正
真实操作栏移动到 Miuix Scaffold 的 bottomBar 测量槽中，导航按钮避让包含在该槽内。Scaffold 在布局同一轮将完整实测高度传给内容，不再额外叠加相同导航边距；无底栏页面仍独立保留系统导航避让。固定操作栏位置与按钮事件保持原逻辑。

所有使用该共用组件的页面移除原来的固定 84dp/78dp 高度补偿，统一保留测量后额外 12dp 的内容末尾间距，防撤回页移除自身重复测量补偿。影响面覆盖共用设置页面，因此不止新功能分组；这是避免共用修正引入双重高度的配套调整。

## 依据与验证
核对项目依赖 Miuix 0.8.8 源码 ScaffoldLayout：先测量 bottomBarPlaceable，再将其 height.toDp() 写入 contentPadding.bottom（空底栏时用系统 inset），最后在页面底部放置。源码来源：https://repo.maven.apache.org/maven2/top/yukonga/miuix/kmp/miuix-android/0.8.8/miuix-android-0.8.8-sources.jar ，commonMain/top/yukonga/miuix/kmp/basic/Scaffold.kt。

scripts/test_settings_bottom_bar.py 提取并编译执行生产 PageScaffold 函数体，使用简化测量模型验证不同栏高（48/68/110/164dp）、系统底部边距（0/24/48dp）、无底栏、不重复避让，以及独立页面消费底部 padding；不是 Android/Compose 渲染测试。源码 diff 检查通过。未运行 Gradle、未构建 APK、未操作微信；大字体、横屏/小窗、真实 Miuix/Backdrop 渲染与页面末项操作仍需新包实机验收。

与艾特提醒及配置备份修正一并整理提交；实机验收仍待进行。
## 9c8e7e9 设置入口原生崩溃修正
用户安装后进入设置即闪退。读取已有 crash buffer（未主动复现），10-25 14:40、14:41、14:44 多次记录均为 com.tencent.mm 的 RenderThread，SIGSEGV/SEGV_ACCERR，libhwui.so RenderNode::prepareTreeImpl 反复递归。日志保存于 /sdcard/Download/Operit/settings-crash-9c8e7e9.log。该栈为 native 渲染栈，无可用 R8 Java 帧；用户 mapping 临时附件在继续时已清理，未能解压，不将 mapping 当作已核对证据。

源码对照确认上一修正将真实 bottomBar 放入 Scaffold 的同时，仍把整个 Scaffold 置于 layerBackdrop 采集层内。首页 MainNavigationBar 的 FloatingBottomBar 使用 drawBackdrop 读取同一层，导致生产者包含自身消费者，构成 RenderNode 循环。这是上一布局修正引入的回归；仅布局数值模型未覆盖渲染依赖。

本次将 layerBackdrop 移至 Scaffold 的 content 槽，底栏与其成为同级渲染分支，保持真实 bottomBar 测量与末项避让，不关闭玻璃功能、不清空用户配置。新增静态回归禁止在 Scaffold 外层采集整个树，检查采集仅存在于内容槽；原可变栏高和导航边距模型仍通过。完整 PageScaffold 通过实际 Compose 编译插件、Miuix、Backdrop 与 Android SDK 的定向编译。静态结构检查及编译不能替代设备 RenderThread 实机验证。

未运行 Gradle、未构建 APK、未主动打开微信。需安装修正版本后确认设置入口恢复、玻璃导航显示正常及动态角标末项不遮挡。

## c07f44a 玻璃背景间隔发灰修正
对照 0bcfb31：旧版捕获完整 Scaffold（含其不透明底色），c07f44a 为避免自采集循环改为仅捕获内容，但主题底色位于捕获层之外。卡片间隔与列表末尾的透明区域未携带页面底色，可能导致玻璃效果对透明输入叠加表面色后显灰。本次在内容 Box 的 layerBackdrop 后、子内容前绘制相同主题 background，使底色进入录制。保留底栏为同级分支和实测高度避让，不重新引入 RenderNode 循环，不改卡片/底栏颜色参数。
新增源码顺序断言：背景绘制必须位于采集 modifier 内部；原底栏测量及循环隔离检查通过，完整 PageScaffold 通过实际 Compose/Miuix/Backdrop 定向编译。上述测试不是设备渲染验证，真实色彩与模糊结果仍待验收。未运行 Gradle、未构建 APK、未操作微信；后经用户授权与引用艾特修正一并提交推送。
