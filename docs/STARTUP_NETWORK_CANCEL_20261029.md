# 首次启动服务器提示与同步延迟排查

基线：3e944728b944a7bf2825b6eaefb4e2027aed6992。附件运行日志时间从 15:19:27 开始，共 1061 行；没有原生任务结束错误或取消调用栈，不能据此确定本次故障起点。mapping 已在临时目录解压，当前日志没有待还原异常堆栈。

## 已核实的证据

- 历史现场记录见 PUSH_TASK_CANCEL_DIAGNOSTIC.md：类型 138 同步请求曾收到 push 原生 9/-7，主进程转换为 1/-7。该记录属于历史现场，不能冒充本次现场。
- 用户提供的微信 8.0.78 smali：t2.d(int,int) 将原生错误类型 9 转换为上层 1；错误类型不是普通网络连通性测试结果。
- m1.cancel() 设置取消标志，通过 dispatcher.r1(netId) 请求取消；j1.j 将 netId 映射为 s2.k 的 taskId 后调用 StnManager.stopTask。
- j1.d0 处理自动认证失败；t2.e 清理任务并通知回调。尚未证实本次是否经过自动认证失败分支。
- 旧全局 onSceneEnd 拦截已删除；热更新早期替换已停用。现有公共发包器缓存 Hook 不写参数或结果，Protobuf dispatch 关闭抓包且没有监听器时直接返回。
- 当前 LSPosed 日志没有本次取消源记录。历史诊断在 e7f2a43 清理，现有运行日志缺失关键证据并不代表底层没有错误。

## 本轮改动：补齐取消源证据

新增 StartupNetworkDiagnostic：仅微信 8.0.78 主进程，记录类型 138 的 queueSubmit、queueCancel、sceneCancel、queueEnd，另记录队列重置和精确同步 onGYNetEnd；取消、提交和重置带调用栈。

恢复并收窄 NetworkOriginDiagnostic：push 记录原生任务错误、taskId/netId/type、连接阶段时间、取消桥、stopTask/JNI 停止来源及认证/队列清理。两进程均带 PID 和单调时钟时间；跨进程关联必须同时核对时段、请求类型、netId/taskId，不能仅凭相同槽位断言因果。

入口在 Application.onCreate 前，TermsGate 同意后安装，限微信 versionName=8.0.78 或 8.0.78.*。使用已核实具体方法，不做全局签名搜索或 DexKit 扫描。只读，不改网络参数、返回、队列或弹窗；不输出消息、请求/响应正文、错误正文、账号令牌或 URL。

每进程窗口 180 秒，到期卸载；主进程最多 48 条事件，push 沿用任务24、认证6、清理6、停止32、提交24条限额。日志写 LSPosed，不从两个进程并发写模块运行日志文件。不启动 push 的完整功能。仅本机提供的 8.0.78 有本轮入口核对证据，其他版本不安装。

## 验证和后续

- 两个生产诊断类通过真实 Android 36 SDK javac 定向编译，Xposed/KavaReflector 使用桩。
- startup_run.py 和 push_run.py 通过，覆盖幂等、采样范围、参数/结果不变、正文排除、限次、超时和卸载。
- 提供目录中 main/push 方法签名静态核对通过；git diff --check 通过。
- 未运行 Gradle、未编译完整 ModuleEntry 集成、未构建 APK、未提交推送，尚无新包现场证据。

下一轮需包含诊断的新包，强停并重开微信，复现后保存同一时段 LSPosed 模块日志和运行日志。没有 armed/hook installed、已超过180秒或缺 push 重启记录时，不能用缺失事件排除任何路径。先依据 sceneCancel/queueReset 的调用栈和 nativeTaskEnd.stopOrigin 确认取消发起者，再修改业务逻辑；本轮不宣称网络故障已经修复。

## 16:17 复现证据与 push-reset-v4

本次主/push 入口均安装成功。16:17:35.616 提交 taskId=6、CGI138；16:17:36.470 原生回调 taskId=6/netId=5 为9/-7；36.491 主进程同一个 sceneIdentity=145397607/netId=5 收到1/-7；36.720 新任务17同步成功，36.743主进程成功。另taskId=3/type0在同一毫秒收到9/-7，存在多个任务同时终止的线索，但尚未确认批量重置。

没有捕获 sceneCancel、queueCancel、queueReset、cancelBridge、taskStop、autoAuthFail、clearTaskAndCallback。阶段字段全零不能证明从未联网。公共网络Hook在37.906安装，Protobuf在46.182安装，均晚于失败；本次不归因于这两个Hook。mapping确认pm.n是诊断回调，不能把提交栈中的诊断框架当作主动发包者。

新增精准边界：StnManager.clearTask/OnJniClearTask、reset/OnJniReset、resetAndInitEncoderVersion/OnJniResetAndInitEncoderVersion、onDestroy/OnJniDestroyStnManager，八个签名均由本机8.0.78 smali核对。每入口独立安装、明确installed/unavailable。

push-reset-v4记录重置事件ID、管理器身份、线程、单调时间及调用栈；before/after以线程局部上下文保留嵌套来源，原生失败回调增加currentReset、lastReset及其自身栈。currentReset只表示同步嵌套调用；lastReset只提供最近事件上下文，不将时间邻近当因果证据。跨线程回调需要结合事件ID/manager/时间及堆栈判断。最多32次重置事件，主/push仍180秒自动卸载，无轮询、无原生行为修改、不读取编码器字符串。

验证：生产诊断Android36 SDK定向编译、8入口smali签名核对通过；push JVM回归覆盖嵌套重置开始/结束、上下文清理、参数结果保持、编码器字符串排除、32事件上限与卸载；主进程回归通过。尚无v4真机记录，不宣称故障已修复。需新包强停重开，在启动3分钟内复现，并导出LSPosed模块日志及Hchat运行日志。
