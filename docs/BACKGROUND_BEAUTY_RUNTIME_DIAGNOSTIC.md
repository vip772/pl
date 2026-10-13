# 四主页完整透明与侧滑运行时诊断

基线 adc78b14f42963f53e0556b6933c1ba5791a828a；输入为 23:31 运行日志、LSPosed 导出、mapping 及四页截图。

## 已确认与未确认

四个 slot 均执行应用。发现页主体图片可见，通讯录及我页主体仍有实色，微信顶部和四页底栏未连续显示背景。现有日志无法分辨实色来自 background、foreground、宿主重写或自绘；不能把 surface 数量当作完整透明验证。
日志证实原列表识别过宽：微信页将 NestedScrollView、TaskBarView、AppBrandDesktopView、小程序任务栏 section RecyclerView 也纳入条目处理。此次仅将实际四页主内容列表登记为条目列表，外围滚动容器只保留容器级处理。
上一轮 CANCEL 重入测试通过仍不能证明用户设备侧滑入口收到事件。现有日志未包含 dispatchTouchEvent 安装结果、边缘事件、Session、chatVisible，因此本次不再宣称侧滑已实机修复，也不凭空改手势阈值。

## 诊断

- 每 slot/reason 最多一次窗口树快照，每次最多 220 个节点。仅可见 View，记录类名、资源名、全局坐标、alpha、背景/前景类、ColorDrawable 色值、tint、管理状态和 onDraw/dispatchDraw/draw 声明；不读取消息内容和联系人文本。
- 切页应用后 600ms 记录快照，便于捕捉后续背景重写。若截断，日志明确 truncated=true。
- 回弹颜色 setter 每方法最多四次入口记录，并触发有限回弹窗口快照；若没有命中，需结合快照的真实回弹类继续定位，不推断为无回弹绘制。
- 已管理背景被宿主重写时，每实际类最多三条，记录 setter 与背景类。
- 侧边栏记录实际 dispatchTouchEvent 方法；前十二次边缘 DOWN/有限 MOVE/结束事件记录坐标、Session、attached、chatVisible、enabled、consumed。没有回调日志与有回调但状态拒绝分别判断。

## 仅水波纹

反馈改为 RippleDrawable(color, content=null, mask=白色遮罩)。遮罩仅限制波纹范围，不作为内容绘制；不绘制原背景。转发 drawable state、bounds、hotspot、hotspot bounds、jumpToCurrentState 和 Drawable.Callback。不承诺原生色值完全一致，当前用半透明白色波纹；真实动画由 Android 实现。

## 验证与采集

未运行 Gradle、本地 APK 构建或安装。定向 Kotlin 类型检查通过；JVM 桩验证无实色 content、原背景不绘制，以及原有 CANCEL 重入回归；不能替代 Android 波纹动画、完整透明和侧滑实机验证。

安装此提交云端产物后保持运行日志开启，重启微信。先在微信页左边缘向右滑两次，短滑一次、长滑一次；再慢速下拉并松开复现原背景；依次进入通讯录、发现、我，每页停留两秒，再返回微信。导出运行日志及同次 LSPosed 日志。若图层 snapshot 被截断，再按已记录的资源/类定向缩小采样；定位确认后移除临时诊断。

本提交是诊断版与波纹需求调整，不是四主页完全透明的最终修复。