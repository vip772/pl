# 66fe209 录屏后的回弹、切换开销与波纹修复

输入：08:54 运行日志、同构建 mapping、LSPosed、58.30秒录屏（1080x2340，平均61.97fps）。提取前32秒、后26秒各2fps关键帧查看；可见微信回弹时自定义火山背景短暂变深色，随后恢复。关键帧不足以量化瞬时掉帧，未获得主线程 trace，不报告具体性能收益。

## 确认问题

1. applyNativeChrome 使用实际类名等于 WeUIBounceViewV2/NestedBounceView，运行时却是 MMWeUIBounceView 子类，存量 J/K/L/M 初始化没有执行。APK WeUIBounceViewV2.a(View,int) 将 J/L 或 K/M 写入 G 背景。改为继承关系判定，捕获并清空存量背景，同时直接登记 G 独立背景容器，即使初始背景为 null；关闭恢复原参数。
2. 同步重写修正后仍 scheduleRefresh；切页 hook 额外重试两次；applyLauncherBackground 每次遍历当前及相邻四页所有条目；applyChromeTransparency 又重复进入 ViewPager 四页。增加按 root/state/revision 的准备缓存，恢复/Fragment重新通知时失效；切页仅一次刷新；已同步修正背景不再整页刷新；公共栏遍历止于 ViewPager。保留列表绑定/附着前绘制检查，移除全树及绘制前诊断代码，仅保留 prepare/slowRefresh 限频耗时、回弹初始化及波纹命中证据。
3. 原波纹依赖微信给行背景发送 pressed 状态，但现有桩回归仅检查状态转发，并未证明实机 View 收到 pressed。增加对已登记主页列表 dispatchTouchEvent 的观察：按下命中行设置 hotspot 和 enabled+pressed，MOVE 超出 slop、UP/CANCEL 清除。事件完全由宿主继续分发，不替换 OnTouchListener、不改变消费结果。触摸状态与宿主状态合并，避免宿主刷新 drawableState 取消正在按下的波纹。

## 检查

定向 Kotlin 类型检查，已有背景不透明合成/聊天合成/手势/CANCEL 回归，新增显式触摸 pressed、enabled、宿主状态更新时保留 pressed、释放清除 pressed。未执行 Gradle 或本地 APK 构建。

首次准备仍有扫描成本，新加入的非列表控件需 Fragment 根通知或配置 revision 变化后重新准备。限频慢刷新日志用于新包判断剩余开销。卡顿改善、波纹动画可见性与回弹仍须设备验收，不能用桩测试代替。