# 微信 8.0.78 运行诊断后的定向修复

基线：74f2534。输入：2026-10-14 00:07 运行日志、同次 LSPosed 日志及同构建 mapping。

## 已确认原因和处理

- 侧滑区域从 e7f2a43 的 72dp 缩成 24dp。运行日志起点 x=116.71875，开关、会话和聊天状态均正常，整段未消费。恢复 72dp 候选区，保留 Activity 观察、普通点击透传和 CANCEL 重入保护；增加实际进程 density/edgePx。
- 应用后 imk 保留 #FF111111，覆盖通讯录/发现内容区；我页 s1d 与 imk 同样保留实色。按页面白名单处理，沿用背景快照和宿主 setter 管理。
- LauncherUIBottomTabView.c() 创建内部 LinearLayout E 并设置背景色。旧扫描透明化外层即 continue。现在仅处理该原生底栏内层与四个标签容器，保留文字、图标、未读徽标。
- FrostedContentView.dispatchDraw() 通过 RenderNode 在顶部和底部 drawColor(h)。透明 View.background 无法消除。主页背景启用时关闭 frostedEnabled，调用实际 setter，保留正常 super.dispatchDraw；保存宿主值并在关闭时恢复。其 a() 会直接重写颜色并启用磨砂，绘制前再次检查开关。
- ActionBarContainer 存有 h/i/m 三个内部 Drawable，实际 setter 为 setPrimaryBackground/setStackedBackground/setSplitBackground。保存并清空，管理宿主后续 setter 与关闭恢复。
- WeUIBounceViewV2.a(View,int) 会把 J/L 或 K/M Drawable 设置到 G（独立 NestedScrollView）。此前只拦截颜色 setter。补齐 Drawable setter，并通过四个真实 getter/setter 清除 Hook 安装前存量值，关闭时恢复。
- 首页图片透明度小于 1 时旧代码把原窗口实色背景置于图片下层。主页取消这层原背景，聊天背景原逻辑保留。

## 验证与限制

定向 Kotlin 类型检查（真实 Android jar/DexKit，项目边界桩），手势/纯水波纹/CANCEL 重入 JVM 回归，增加日志坐标的候选区回归。未执行 Gradle 或本地 APK 构建。

这不是实机验收结果。新包仍需验证四页内容、顶部和底栏、回弹、小程序下拉、聊天、自定义底栏及关闭恢复。限频诊断暂时保留。

alt-entry 当前可见本地分支为独立旧命名空间 h.Hchat 基线，尚无 HomeSidePanelDrawer；不直接覆盖旧分支整份实现。本次提供命名空间转换后的完整相关文件供同步，不能称为已提交或推送该分支。
