# 0851 反馈：语音子树、实际图标线宽与退出完整画面

基线 dcb28fb，输入为 Hchat-runtime-20261015-085110.log 及同提交 mapping。保持已验证的深浅模式配色、表情栏透明和自定义分组支持。

## 本次证据和边界

日志记录新的输入框仍使用 widthPx=4，但没有 CHATSTROKE 实际测量记录；旧方法在 ChatFooter.C 或 drawable 为空时直接返回1.2dp，因此不能声称已测量到系统图标线宽。改用8.0.78 ChatFooter.B0 已确认的资源 id/bqr 定位按钮，并对缺失路径输出日志。

日志中的 SpeechInputLayout 内图标 wet 仍使用模块 FeedbackDrawable，而不是普通透明 drawable；旧底栏遍历最多240节点，深层语音控件没有独立处理入口。不能仅凭未出现限频日志断言 Hook 未安装，因为日志从进程中途开始且存在截断。当前以独立子树入口保证访问，并记录每个节点处理前后。

退出日志在08:51:02.330判定WECHAT；后续仍能看到聊天容器/SwipeBack树，同时主页底图已应用。现有日志没有完整 CHATRETURN 时序，不能据此证明哪一帧由哪个清理函数造成。8.0.78 APK 已核实 NewChattingTabUI.f(Z) 在动画前调用 ChattingUIProxy.onExitBegin/onExitEnd，后者清理 Fragment 生命周期；uf.onAnimationEnd才将容器设GONE。本次用完整退出画面隔离该时间窗口，不再以提前透明化聊天容器作为修复依据。

## 修改

- 语音控件从 ChatFooter 下单独定位 SpeechInputLayout，独立最多80节点扫描。仅处理普通 View、ViewGroup 和 ImageView 的背景/前景，保留图标 drawable 与录音进度控件。取消内部被动图标的模块反馈背景；透明化短路判断同时区分纯透明与反馈背景，避免已有 FeedbackDrawable 无法替换。
- 表情按钮用 id/bqr 定位，按其实际宽高、padding、imageMatrix 与现有 drawable bounds 绘制到小尺寸透明位图；对顶部中心第一段圆环按 alpha覆盖率累计，不以128阈值丢弃抗锯齿边缘，向上取整为描边像素。缓存包含按钮、drawable、尺寸和缩放。保留微信自身深浅模式判断；按钮尚未布局或采样失败明确输出日志并回退原值，后续布局继续测量。
- 原生关闭入口 f(Z) 注册独立生命周期回调，避免已有同成员观察者被 hookOnce 去重后压掉新增回调。ChattingUIProxy.onExitBegin也在清理前捕获，覆盖共同退出生命周期；同一容器只捕获一次。
- 将完整聊天画面临时放在原聊天容器前景，随原生容器移动；不替换Window底图，不更改宿主返回流程。GONE且动画结束、detach、关闭功能、销毁时移除；最长1500ms释放防止意外持有。释放后不立即回收已交给硬件绘制的bitmap，避免RenderThread引用已回收数据。
- 移除上版s(VISIBLE)提前撤去聊天图片和退出刷新时强制透明的处理。保留生命周期日志，新增 CHATRETURN captured/released/proxyExitBegin、CHATSTROKE actual/missing、CHATPART speech-direct-before/after。

## 验证及实机关注点

未执行 Gradle、本地应用编译或APK构建。独立 JVM 背景、反馈与手势回归通过，git diff --check通过；它们不覆盖真实Android截图、动画或图标采样，不能代替实机验证。

捕获1440x3120 ARGB画面临时占用约17MiB，并在返回时同步绘制一次。必须复测返回耗时、普通返回与滑动返回取消、含媒体消息聊天；软件Canvas不保证包含SurfaceView等独立表面。若目标控件是硬件专用drawable，采样失败日志将保留原始Throwable。此次微信内部结构依据8.0.78，未完成其它版本横向验证。