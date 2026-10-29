# 首次启动原生取证（独立工具）

仅供 Root 的 ARM64 测试设备，不集成 APK。当前仅核对微信 8.0.78，libwechatnetwork.so SHA256 必须是 f06b4984bf113a4c021dbc9720107ee71a8f68fdeab277dd01bbe0708fea31df。源码中偏移与任务字段不得用于其他二进制；首指令检查只防明显错位，不替代完整 SHA256 检查。

编译：

```sh
aarch64-linux-gnu-gcc -O2 -static -Wall -Wextra -Werror netprobe.c -o /临时路径/netprobe
aarch64-linux-gnu-gcc -O2 -static -Wall -Wextra -Werror hwbreak_test.c -o /临时路径/hwbreak_test
```

将产物复制到设备可执行临时目录，Root 下先运行 hwbreak_test；自测应输出断点命中、PC 匹配、解除跟踪后子进程正常退出。netprobe 参数是 push PID 和持续秒数（1—180）。本轮使用 25 秒窗口。

实现：PTRACE_SEIZE + ARM 硬件执行断点，不改写代码/业务参数/返回值；命中后读取寄存器和明确任务字段，临时禁用断点，单步越过命中指令再恢复。线程扫描每 100ms、最多 512 个累计线程槽，最多 128 条业务事件、4096 次 SIGTRAP 处理；存在既有硬件断点时拒绝覆盖。支持 SIGINT/SIGTERM 退出清理，不设置 EXITKILL。

边界：命中会暂停线程，可能改变启动竞态，不能拿采集时未复现证明修复。现有线程注册与新线程发现存在短暂覆盖空隙，未记录不能证明未执行；原生回溯只跟随有效帧指针，省略不在网络库范围的帧，因此不保证完整调用链。进程被 SIGKILL 或工具异常退出的清理能力不等同正常退出；操作结束应逐线程核对 TracerPid=0。本工具不是生产修复。

当前五个观察点：

- StnManager::RedoTasks 0x3277f4。
- MMCore::__RebuildLonglink 0x1666ec。
- 长连接 __SingleRespHandle 0x29c1c0：只记录 9/-7，读取 task+16 taskid、+20 cmdid、+2400 retry、+2352 running_id。
- 短连接处理 0x2f46d0：同上。
- 长连接最终回调前 0x29dc00：从 x19 读取任务，w21/w26 为 type/code，记录所有结果。

coldstart-1.log 为最初五点版本，第五点在 JNI OnTaskEnd；没有该点记录不能断言没有最终结束。coldstart-2.log 改用长连接最终分支，并增加 cmdid；coldstart-3.log 再用重建入口替换网络变化入口。采集脚本版本变化必须结合这段说明解释。

## 本机结果

硬件自测成功，6 个槽；短时挂载和三次冷启动采集均正常清理。第三次退出后逐线程核对 132 个线程，均 TracerPid=0。最终含覆盖上限/既有断点保护的源码通过 -Werror 编译和 1 秒挂载退出检查，未重复全部冷启动。

第二次受控复现：

- 21053915ms：longResponse task=7 cmd=121 retry=1，调用者 0x2a3a98，祖先 0x167158。
- 21054061ms：普通 Mars redoTask 从 JNI 进入。
- 21054076ms：同一 task=7 cmd=121 longResponse retry=0，祖先 0x2c7f9c。
- 21054079ms：同一任务 longFinal 9/-7、retry=0。
- 21054606ms：新任务 task=22 cmd=121 最终 0/0。

第三次捕获 rebuildLonglink 调用者 LR=0x11df1c、祖先 0x280294。ELF 符号/反汇编确认它属于 DispatchMMTlsHostInfo → __RebuildLonglink → 队列回调 → LongLinkTaskManager::RedoTasks。Java 可达入口为 j1.T → t1.b → MMStnManager.setMMTlsHostInfo。

结论：在受控采集中，MMTLS 主机配置引起的长连接重建与稍后的 redoTask 可连续消耗同一同步任务重试，最终上报 9/-7，随后新同步成功。仍缺第二次 redoTask 的 Java 调用者及未受采集干扰的对应证据；不能直接等同于历史 17:00 样本已查清全部因果，更不能据此全局禁止重建/redo。下一步应只补齐 redo 的 j1.v0/y1.b 来源区分，或核对认证下发先后关系，然后设计针对性修复。


来源组补充：启动一次采集到 saveAuthLong、saveAuthShort -> JniRedoTask（同一毫秒），随后 JniSetMMTlsHost -> JniRedoTask（约397ms）。smali 证据确认 y1.b() 在保存认证列表后按 clicfg_android_autoauth_redotask 调用普通 Mars redoTask；t1.b() 仅执行 setMMTlsHostInfo，不调用 redoTask。第二次 JNI redoTask 的 Java 调用者仍需通过线程/Java入口进一步确认，不能仅按时间邻近认定由 t1.b() 直接触发。


## 审查纠正及实验边界

此前将后一次普通redo直接归属于j1$$d或称其为“重复认证”证据不足。完整j1$$d.smali只有onSceneEnd(...)，成功时redo用于释放等待AX认证的请求；tryReusePayAxSession同样通过j1.v0触发普通Mars。不能据此跳过其中一条认证路径。运行证据仍可靠地证明重调度会消耗同步重试并最终返回9/-7。

source-4.log 保存后续来源组采集：保存认证列表后立即JNI redo，MMTLS配置后约397ms再次JNI redo。源码本轮不保留这一临时变体。

allowance-experiment.log 为会写入任务重试字段的临时机制实验，和只读coldstart日志必须区分。实验在cmd121长连接重调度前将retry1改为2一次，随后该任务0/0；但任务在下一次redo前已经成功，未覆盖两轮重调度同时落在该任务上的条件，因此不能用该样本证明效果或等同于模块Java Hook已实机通过。实验C源码单独保留在allowance_experiment.c，不打包进APK。本轮全部采集结束均正常清理，最后逐线程核对133个线程无TracerPid。
