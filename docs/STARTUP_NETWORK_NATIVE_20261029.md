# 微信 8.0.78 首次同步失败：原生分支核查

> 后续审查纠正：本文部分入口归属推断已被完整 smali 否定。j1$$d 实际方法为 onSceneEnd(...)，成功时重调度用于释放等待AX认证的请求，不能删除。tryReusePayAxSession 也通过 j1.v0 调用普通Mars。原生时间邻近不足以确认第二次JNI的具体Java调用者。最终交付采用首同步一次重试额度补偿，详见 STARTUP_SYNC_RECOVERY.md；不宣称重复认证根因已修复。

状态：本文前半部分记录静态核查时的结论；后续已通过独立硬件断点工具取得新的受控冷启动运行证据，详见末节及 scripts/diagnostics/startup_native/README.md。生产代码未修改，尚未修复。本文件补充 STARTUP_NETWORK_CANCEL_20261029.md，并纠正将 9/-7 主要解释为主动取消的旧排查方向。

## 分析对象与现场

使用用户提供的 wechat8078_decoded/lib/arm64-v8a/libwechatnetwork.so；此前已与设备加载文件核对 SHA256：f06b4984bf113a4c021dbc9720107ee71a8f68fdeab277dd01bbe0708fea31df。下列地址均为该 ELF 的虚拟地址，不是运行时绝对地址。仅有当前 8.0.78 的分析证据，不推广到其他版本和 ABI。

17:00 样本：taskId=6 在 17:00:10.370 提交，channelSelect=3、cmdID=121、cgiID=138；17:00:11.198 原生回调 9/-7、netId=5、requestType=138，channelType=0，已记录阶段时间均为零。17:00:11.235 主进程收到 1/-7，17:00:11.487 新同步任务成功。零阶段字段不能证明从未联网，也不能单独识别错误来源。

Java 侧 clear/reset/destroy/stop 诊断安装成功且失败前未捕获调用；这不能排除 native 内部的重调度、网络变化和 SocketBreaker。诊断回调的 Java 栈不包含原生消息队列此前的投递者。

## 已确认的重调度调用链

1. 微信 network.j1.v0() 调用普通 com.tencent.mars.stn.StnManager.redoTask()。
2. network.j1.s1(String,String,int[],int[]) 构造 network.y1；y1.b() 更新认证长短连接服务器列表。
3. y1.b() 读取 clicfg_android_autoauth_redotask；为真时调用普通 Mars redoTask，为假时调用 WeChatMars.onNetworkChange。支付网络另有独立分支。
4. 原生 StnManager::RedoTasks，0x3277f4：读取 this+224 的 NetCore；存在时调用 0x2c7bb0，不存在时只记录日志。
5. 0x2c7bb0 构造消息与回调；0x2c7e10 通过 MessageQueue::PostMessage 投递。该事实由 PLT 导入符号核对。
6. 投递回调 0x2c7f30：0x2c7f84 先调用 0x2d3cc8；this+120 标志非零时，0x2c7f98 调用长连接 RedoTasks 0x2a31cc，随后调用 0x318470；0x2c7fa8 调用短连接 RedoTasks 0x303ba8。
7. 长连接 RedoTasks 0x2a31cc 遍历长连接对象，末尾 0x2a3464 以空字符串调用内部 __RedoTasks 0x2a34c0。

不能将所有 redoTask 候选混为一条链：j1 中 tryReusePayAxSession，以及 j1$$d 中 axauth done 的 redoTask，调用的是 com.tencent.paymars，不能直接用来解释普通 Mars 同步任务。y1 同时包含普通和支付分支，普通分支仍是有效候选。

## 直接构造 9/-7 的分支

### 长连接内部 __RedoTasks（0x2a34c0）

- 0x2a3754 起判断传入通道名是否为空；非空时比较任务通道字符串，不匹配则跳过该任务。
- 0x2a37cc 清零任务对象 +2408 的字段；该字段的业务名称尚未独立确认。
- 0x2a37f0 选择传入通道名或任务通道名，0x2a3800 查询连接映射。
- 有长连接对象时，0x2a3828 检查任务 +2352 指针；非空进入处理，空则跳过。
- 无对应长连接对象时走错误日志分支，调用 0x29ed44 后同样检查任务 +2352 指针；空则跳过。
- 处理分支取得连接上下文/构造空上下文；0x2a3a88 设置 errType=9，0x2a3a8c 设置 errCode=-7，0x2a3a90 设置处理参数=-1，0x2a3a94 调用 0x29c1c0。
- 0x29c1c0 后续还包含重试/结束分支，因此“调用该处理函数”不能直接等同于“必然立即回调 Java onTaskEnd”。需结合其余任务状态判断。

原生字符串与地址：0x537a1 为 __RedoTasks；0x64c6f 为 longlink_task_manager.cc；0x41415 为 task redo, taskid:%_。

### 短连接 RedoTasks（0x303ba8）

- 0x30404c 读取任务 +2352 指针，并清零 +2408 字段。
- 指针为空则跳过；非空时取得运行对象的连接上下文。
- 0x304024 设置 errType=9，0x304028 设置 errCode=-7，0x30402c 设置处理参数=-1，0x304034 调用 0x2f46d0。
- 同样需要后续处理函数状态才能确定是否最终结束任务。

原生字符串：0x582d4 为 RedoTasks；0x3da13 为 shortlink_task_manager.cc。

### 其他原生 9/-7 来源

0x238f98/0x238f9c、0x23b648/0x23b64c、0x2609b0/0x2609b4 也直接构造 9/-7。后两处前置条件明确调用 mars::comm::SocketBreaker::IsBreak()，分别在 0x23b610 和 0x2609a4；该符号经 ELF 导入表和 0x3865b0 PLT 核对。不能把其中 make non block socket error 等日志直接解释为设备无网络：实际错误选择还依赖 IsBreak() 分支。

单纯搜索 -7 会混入容量计算常量、其他错误域及 errno 包装，不能把全部匹配项当作 STN 9/-7 的直接来源。

## 模块状态核查边界

模块 Java 源码中检索 redoTask、saveAuthLongList、clicfg_android_autoauth_redotask、WeChatMars、SocketBreaker 未找到直接调用，不代表不存在间接影响。

DisableHotUpdateFeature.installEarly 当前只检查配置，不安装早期 Tinker 返回值改写；后期仍有更新响应处理 Hook。WeChatTabletFeature 若启用会改写设备形态判断结果。设备两个配置的 kva 文件头和前 128 字节为零，两份文件时间均为 10 月 25 日；HchatStorage 使用未配置 cipher 的 FastKV.Builder。未核对完整 FastKV 文件及运行中缓存前，不据此断言两个功能实际开启或关闭。

## 仍缺少的因果证据

目前不能确定 17:00 的错误来自长连接重调度、短连接重调度还是 I/O 中断，也未证明 y1.b() 在失败前实际执行。现有日志没有 native 调用位置，继续静态阅读不能恢复已经丢失的运行时分支选择。

若后续确需运行证据，应只回答两个问题：本次 9/-7 由上述哪个原生调用点进入任务完成链；其发起者是认证列表更新、网络变化还是其他连接中断。优先利用已有 native xlog 的准确函数/任务记录；若不足，再评估启动限时、仅在失败时采集原生回溯的可行性和开销。仅增加 Java 清理/取消入口覆盖无法回答该问题。尚未实现这类采集，也未要求用户更换新包。

不得直接禁止 redoTask/onNetworkChange，或把 9/-7 改成成功：这些路径参与连接恢复及同步重试，缺少实际触发证据时不具备修复依据。
## 追加：重试条件与原生日志格式

公开参考源码来自 https://github.com/Tencent/mars ，固定提交 6aa5b567afada93936252bac2f271c4b43eccd1f，仅用于辅助理解；当前微信的结论仍以实际 ELF 为准。

公开 longlink_task_manager.cc 的 __SingleRespHandle 在剩余重试次数耗尽时结束任务，否则递减后重试。实际微信 ELF 中，0x29c560 读取任务 +2400 的有符号值，0x29c568 在其小于等于零时进入结束分支 0x29cf40；0x29ce94 至 0x29cebc 的重试分支将同一字段减一。这是重调度的 9/-7 可能被内部重试或向上结束的具体条件，不能仅看到 RedoTasks 就断言必然弹窗。

微信 t2.v(...) 创建普通 Mars Task 后会设置 retryCount；特殊请求和 longPolling 可进一步覆盖；末尾在第四个参数 p4==1 时将 retryCount 设为零（smali 约 7930 行）。尚未证明失败的 CGI138 使用了哪种覆盖，也没有本次 native 剩余重试值，因此不能据此直接增加同步重试次数。

设备 /data/user/0/com.tencent.mm/files/xlog 保留 PUSH_20261029.xlog。只读检查前 32 字节显示首字节 0x0c，后随非零密钥材料；官方 mars/xlog/crypt/log_magic_num.h 将 0x0c 定义为 kMagicAsyncZstdStart（加密的异步 zstd），0x0d 才是无加密版本。官方解码脚本对 0x0c 需要相应 ECDH 私钥派生 TEA key，不能用无加密解码脚本直接恢复。公开示例私钥不代表微信实际日志私钥，本轮未将其当作可用密钥，也未解出日志正文。未上传设备日志。

## 本轮验证范围


通过当前 ELF 反汇编、动态符号、PLT、字符串和 8.0.78 smali 交叉核对；对照已有 16:17、17:00 失败和 16:43 成功样本。未运行 Gradle、未构建 APK、未改生产行为；不以本轮静态核查作为实机修复验证。

## 后续受控冷启动证据

已实现独立 Root ARM64 硬件断点采集器，无需换包；源码、自测和三次原始采集保存于 scripts/diagnostics/startup_native/。第二次样本直接记录同一 cmd121 同步任务先以 retry=1 经长连接重调度，再以 retry=0 经第二轮重调度，最终 longFinal 9/-7；新同步任务随后 0/0。第三次确认第一条重建路径来自 DispatchMMTlsHostInfo 的 __RebuildLonglink，LR=0x11df1c。第二轮 redoTask 的 Java 调用者仍待区分，采集暂停可能改变竞态，不能将受控结果冒充历史17:00的完整因果证明。采集结束均正常解除，第三次逐线程确认132个线程无TracerPid。生产代码未改，未修复、未提交推送。


## 入口归属补充

普通 Mars 的 `redoTask()` 直接调用点在当前 8.0.78 smali 中确认有两处：`y1.b()` 和 `j1$$d` 的会话复用流程；`j1.v0()` 只是普通 Mars 的封装。`j1$$d` 在恢复 `AccInfo` 会话并执行 `AccInfo.e2(...)` 后调用 `j1.v0()`，随后另行调用 PayMars 的 `redoTask()`。因此来源组采集到的第二次普通 `JniRedoTask`，与 MMTLS 主机配置下发后的 AX 会话复用/认证恢复时序相符；`t1.b()` 本身只调用 `MMStnManager.setMMTlsHostInfo()`，没有直接调用 redo。

当前最有根据的修复候选是消除启动阶段 `y1.b()` 与 `j1$$d` 对同一批待处理任务的重复普通 Mars 重调度，或者让其中一个入口只更新认证/主机状态而不立即 redo；但仍需核对两入口的业务前置条件和是否存在非启动场景，不能全局屏蔽 `redoTask()`。本轮未修改生产代码。
