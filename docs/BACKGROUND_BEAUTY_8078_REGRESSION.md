# 8.0.78 沉浸背景与边缘侧滑第二轮修复

## 输入与证据

基线：ed509b3615df38b1ff828b36d3904e551af42e8c。
输入为 22:56 导出的运行日志、LSPosed 日志、该提交 mapping 与四页截图。

运行日志记录四个 slot 均执行应用：22:54:09 wechat（28 surfaces/2 lists）、22:54:20 contacts（64/6）、22:54:23 discover（74/8）、22:54:27 me（93/10）。因此不能把本次黑底解释为根本没安装背景功能。
截图显示发现页主体已露出图片，通讯录行仍为实色，我页头像以下实色，微信状态栏、置顶折叠条与底栏有实色层。日志没有记录具体 View 树，因此不能从日志单独确定全部遮挡层的运行时身份。
最新 LSPosed modules/verbose 未记录该功能的 Hook Throwable；会话分组备份告警、LSPosed Manager 403 和 Shell Activity 异常不作为本功能根因。

下载 Actions 38415363089 的正式 APK，对照用户 mapping 识别 pj.q 为侧边栏 dispatchTouchEvent 回调，并反编译其实际方法。正式代码保留了：横向接管 → edgeDragging=true → decor.dispatchTouchEvent(CANCEL) → 回到同一 Activity Hook → ACTION_CANCEL 清空拖动状态。不是仅凭源码假设 R8 改坏了逻辑。背景的 R8 问题没有产物证据，不将本次背景问题归因于 R8。

## 改动

1. 内部派发宿主 CANCEL 期间禁止侧边栏再次解释该事件，但原宿主继续接收 CANCEL。保护位在 finally 恢复。普通点击仍不被透明覆盖层拦截。
2. 反馈背景只在 pressed 时绘制原背景，focused/selected 不再让整行保留实色；设置后同步 drawableState。
3. 主页条目内部有背景的 ViewGroup 也处理透明，限于已登记主页列表条目；每条最多 160 个节点，排除 Compose、底栏，不修改 ImageView/TextView 的背景、图标、文本、前景和点击监听。
4. 列表类别检查遍历继承链，减少宿主混淆子类名称不包含 RecyclerView 时漏处理的情况。
5. 四主页改为 decor 单张屏幕对齐背景，公共宿主和页面根部透明，避免原底色和图片在多个层重复叠加。聊天页保留原宿主/原生背景路径。
6. 补充系统 statusBarBackground/navigationBarBackground View 与原生底栏背景透明化，保留自定义悬浮底栏自身材质；进入无背景页面时恢复这些共享 surface。
7. 微信页根据实际截图中的“折叠置顶聊天”文本限定父容器透明化。此路径依赖中文标签，未声称覆盖其它语言。
8. 限频记录已识别列表的实际类名及子项数，便于真机核对，不输出联系人和消息正文。

## 验证

- 无 Gradle、本地 APK 构建、安装或重启微信操作。
- Android 37 API、真实 DexKit 与内部接口桩下，涉及的生产 Kotlin 源码类型检查通过。不是完整工程编译。
- JVM 回归调用从生产文件提取的 interceptEdgeTouch 方法，模拟 CANCEL 重新进入原方法；临时移除保护位时测试失败，恢复后通过。覆盖 MOVE 接管、继续拖动、UP 保持打开、普通点击不打开，以及原有手势分类回归。
- 生产 FeedbackDrawable 回归验证 pressed 绘制，focused/selected/释放状态不绘制原实色。
- git diff --check 通过。
- 四页完整透明、状态栏/底栏对齐、列表复用、菜单交互、关闭恢复、深浅色和其它模块共存仍待新包真机验收。不能将本轮修改和桩测试称为四页已实机修复。
