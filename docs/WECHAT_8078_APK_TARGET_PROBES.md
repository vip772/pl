# 微信 8.0.78 APK 定点诊断

基于持久化 APK 的 class_defs 与 15 个指定类 Smali，确认 b0.f(III) → y(F) 直接重写滚动宿主背景，TaskBarContainer.l / TaskBarView.z1 存在父容器背景写入，GradientColorBackgroundView.onDraw 存在自绘，HomeUI.v(I) 存在状态栏写入。

本次仅增加诊断，不改变宿主参数、返回值、透明化和恢复逻辑。

新增日志：
- WXPROBE install：每个指定方法的找到/安装结果。
- WXPROBE / WXNODE：同一 call 编号匹配 before/after；记录对象标识、背景颜色、管理/保留状态、slot、可见性、alpha、坐标、尺寸、位移和最多三级父链。
- y(F)：记录滚动接口实现、列表、r、任务栏、动态背景。
- l / z1：记录容器及父级、固定字段对应视图和 G2/P2/Q2/Y2。
- HomeUI.v：记录请求颜色及前后窗口颜色。
- WXBAR：记录系统栏 setter 的请求/有效参数、当前页面、聊天状态、barsApplied、configured、mutation、restoring、pending。
- WXRETURN：聊天事件时和下一帧的页面/窗口保护状态，结合现有 chat-exit 快照。

限制：每个进程 y 最多32次、渐变绘制6次、其他指定方法12次，系统栏每方法每阶段24次、聊天事件12次；没有整页持续扫描。渐变仅在采样到 y(F) 后2秒内记录，避免冷启动耗尽；未采到绘制不能证明其不参与覆盖。使用新进程冷启动直接复现，不先切页或进入设置。

复现：微信页观察状态栏 → 轻拉松手两次（不完全进入小程序）→ 点击会话返回停留两秒 → 最后切页一次观察恢复。提供同次运行日志、LSPosed日志、录屏及匹配mapping。

验证：独立 Kotlin 类型检查及 git diff --check；不执行本地 Gradle/APK 构建。显示问题仍需上述实机证据，不宣称本版已修复。