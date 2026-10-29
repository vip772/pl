# 微信 8.0.78 首次同步重调度恢复保护

这是针对已捕获失败机制的有界恢复措施，不是对重复认证来源已经完整修复的声明。仅支持 com.tencent.mm:push、versionName 精确为 8.0.78；其他版本不安装，缺少横向二进制证据时不复用内部签名。

## 现场证据

独立硬件断点采集确认，MMTLS 配置导致的长连接重建与普通 Mars redoTask 可连续处理同一 cmd121 同步任务。coldstart-2.log 的 task7 在第一次重调度时剩余重试为1，第二次为0，最终9/-7；随后新同步成功。smali 和既有主/push关联日志确认 cmd121 / CGI138 是同步请求。

必须纠正早期结论：j1$$d 的实际方法为 onSceneEnd(int,int,String,m1)，成功时日志是“axauth done, trigger redoTask for held CGI”。该调用用于唤醒等待AX认证的请求，不能仅凭发生较晚就删除；tryReusePayAxSession 也通过 j1.v0 调用普通Mars后另行调用PayMars。第二轮的具体Java发起者尚未通过Java栈验证。不存在已验证的 j1$$d.b()；该候选Hook已撤回，未保留、未提交。

## 改动

StartupSyncRetryGuard 在 Application.onCreate 前经TermsGate同意、版本与进程校验后安装。只Hook普通Mars StnManager.startTask(Task)，预先核验cgiID:J、cmdID:I、retryCount:I、longPolling:Z。

启动60秒内首次非longPolling、cgiID138/cmdID121请求，如果宿主retryCount恰为1，则改为2，允许已捕获的两次启动重调度后仍执行一次请求。宿主选择0或已有其他值时保持原值；只消费一次机会，随后立即卸载。60秒未匹配也卸载。字段/方法不匹配、调度器拒绝超时任务、运行异常时清理并报错。

保留全部认证、MMTLS配置、普通Mars和PayMars重调度；不拦截回调、不改错误为成功、不屏蔽提示。真实网络失败最多为这一个首同步任务增加一次原生尝试，可能增加该任务的失败确认时间。这个行为是恢复额度补偿，不能表述成重复认证根因已消除。

旧StartupNetworkDiagnostic与NetworkOriginDiagnostic从ModuleEntry运行入口移除，修复候选不再安装旧的主/push诊断；历史源码和原始记录供审计。正常运行没有原生断点、轮询、调用栈采集，不影响聊天滚动路径。

## 验证

recovery_run.py 编译真实生产类，JVM覆盖正常首同步、非目标请求、longPolling、二次请求、宿主0/2策略、版本/进程范围、幂等、超时和定时器拒绝清理。生产类通过Android36真实SDK定向javac，Xposed/Kava/注册器边界使用桩；入口验证为源码检查，不是完整ModuleEntry编译。未运行Gradle、未构建APK，最终包尚需实机验收。


原生机制实验追加：临时工具在长连接处理入口将首个cmd121任务retry从1改为2一次，观察到同任务最终0/0。该任务在第二轮redo之前已完成，不能声称覆盖了历史失败条件；Java Hook的最终APK未安装验证。实验结束133个线程全部TracerPid=0。实验及源码单独保存，未加入APK运行路径。
