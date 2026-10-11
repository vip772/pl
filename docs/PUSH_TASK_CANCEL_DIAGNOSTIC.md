# 原生任务本地取消来源诊断

依据230218运行日志和230226 LSPosed：push taskId=3、netId=2、类型138的原生9/-7，经转换到主进程1/-7并触发提示。微信APK中9=ectLocal；公开Mars源码中-7=kEctLocalCancel。未验证公开源码与微信二进制完全一致，不直接归因于VPN。

## 核实入口和修改

微信8.0.78 j1.j(j1,int)将netId映射为队列s2.k的taskId，再调用StnManager.stopTask(int)，后者调用原生OnJniStopTask(int)。
NetworkOriginDiagnostic版本push-cancel-v2，新增cancelBridge、stopTask、OnJniStopTask、startTask四入口，保留原任务/认证/清理诊断。
取消桥记录netId及调用栈；停止记录taskId、来源方法、管理器标识及调用栈；提交只记录taskID、channelSelect、cmdID、cgiID数字元数据，不输出URL、headers、extraInfo或userContext。
停止来源按taskId关联到nativeTaskEnd的stopOrigin。提交时清除同ID旧记录，结束后取出并移除；最多32个ID，不持有宿主任务对象。提交24条独立限额，停止/取消共32条独立限额。push首次安装后10分钟自动卸载。
JNI Hook不兼容明确记录unavailable，各入口独立安装。stopOrigin=not_captured仅表示未捕获，可能是原生内部取消、入口不兼容或采样边界，不等于没有取消。
目前仅在push安装，调用栈不能跨Binder还原主进程发起者。如证据指向IPC取消，下一步需定位主进程调用者，不能仅凭push栈宣称最终原因。
不拦截取消、不修改参数/结果/任务队列、不屏蔽提示。消息时间保持修复，父级与QQ点歌暂停。

## 验证

扩展JVM生产类测试通过：入口注册与幂等、taskId停止关联、取消桥、重提任务清除旧来源、秘密字段排除、参数/结果/队列不变、超时卸载。主进程服务器诊断回归及git diff --check通过。
未构建APK、未完整编译Android集成。测试桩中的JNI入口是普通方法，不能验证实机原生Hook能力。尚未提交推送。

## 实机复现

更新后保持Wi-Fi+Clash规则模式，通过系统应用信息强停对应微信后重开，确保push新建；无需清数据。强停期间消息通知暂停。
启动3分钟内复现后导出运行日志和LSPosed，注明主微信/分身及提示时间。push诊断只在LSPosed中。
关注push-cancel-v2、stopHook安装状态、cancelBridge、taskSubmit、taskStop和nativeTaskEnd.stopOrigin。若没有入口安装记录或超出诊断窗口，不能把缺日志解释为该路径未发生。