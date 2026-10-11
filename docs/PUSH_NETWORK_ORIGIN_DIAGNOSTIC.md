# Push 网络错误来源诊断

只增加只读诊断，未构建APK，尚未提交推送。消息时间已实机修好，不改；父级和QQ点歌暂停。

APK证据：CoreService在com.tencent.mm:push；StnManagerCallback.onTaskEnd接收原生类型/错误码；t2.d将多种原生类型转换成上层1；j1.d0处理自动认证失败后调用t2.e，t2.e清理队列并向多个请求广播同一个错误。故主进程138的1/-7与空缓冲不能直接证明VPN连接失败。

新增NetworkOriginDiagnostic，TermsGate允许后在push Application.onCreate之前独立安装三入口：
- taskHook：原生taskId/error、映射队列netId/requestType、连接/握手/收发阶段时间。
- authHook：认证失败输入错误码与调用栈，不输出错误正文或认证对象。
- clearHook：批量清理传入错误码、最多32条队列taskId/netId/type与调用栈，不输出请求或响应内容。

push不初始化HLog、不写主进程运行日志、不启动主功能。诊断仅写LSPosed模块日志，带process/pid/uid。每个push进程首次安装后10分钟自动卸载；任务24条、认证6条、清理6条，诊断异常最多8条。主进程原诊断仍为启动3分钟。
队列映射只读取同一队列的元数据，在已有队列锁下取快照，禁止修改队列/结果。taskId->netId关系已核对t2.p实现；netId是复用队列槽，跨进程日志关联需同时核对用户、时段、类型及任务记录，不能只按相同netId断言因果。

JVM生产类测试通过：push-only和幂等安装、原生task到队列netId映射、profile、auth/clear入口、参数/结果/队列不变、正文排除、限次和超时卸载。主服务器诊断回归通过，git diff --check通过。未完整编译ModuleEntry Android集成，实机尚未验证三个下层入口。

复现：安装后首次需要push进程重新创建。仅退出聊天界面可能不重启push；应通过系统应用信息强行停止对应主微信/分身再打开（会短暂停止消息通知），无需清数据。保留原Wi-Fi与Clash规则模式。确认LSPosed中push-origin-v1及taskHook/authHook/clearHook安装状态。若push存活超过10分钟，窗口已结束，需要重启该进程再测。没有armed或入口unavailable不能解释为下层未出错。
启动后3分钟内若提示复现，导出运行日志和LSPosed，注明用户及提示时间。push证据不在运行日志中，因此LSPosed必需。不关闭VPN或改变分组来碰运气；先根据捕获结果决定下一步。