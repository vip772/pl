# 通用时间绑定路径与138同步入口诊断

本轮只增加诊断，无布局修补、网络结果修改、数据归属恢复，未构建APK、尚未提交推送。
依据210932运行日志及210953 LSPosed：顶部时间仍错位，上版cs成功诊断没有输出；父级缺失仍为两份表talker_absent；分身21:07:33目标Toast与y51.b类型138的1/-7精确关联，随后同步成功。

## 时间

MessageDetailsLayout revision=bind-path-v2：通用外层绑定入口，包括配置关闭时；解析成功/消息未解析、锚点选择/未解析、插入结果、post_dispatch及现有卡片global_layout回调。
记录实际Holder、raw type、enabled、position、原生timeTV父链、View类/资源名/对象标识/坐标/可见性，以及模块标签tag标识；不打印TextView文本或金额、商户、消息内容。
每进程最多16个行对象，每行/Holder/阶段去重；每次树最多48节点、深度6，timeTV父链最多8层。post_dispatch不是严格的布局完成事件，不将其独自作为最终几何证据，卡片global_layout补充真实布局事件。复用同一行同一Holder时首个同阶段样本不会重复输出。
NativeTimeDiagnostic revision=native-time-v1：独立会话时间样式绑定也记录实际原生timeTV对象标识、宿主、模式及坐标，每进程最多12种Holder，避免把原生时间误认为消息详情标签。
没有挂全局TextView.setText，没有修改控件、消息或设置；现有时间插入路径保持不变。

## 138请求

ServerErrorDiagnostic revision=sync-origin-v3，APK核实y51.b.getType=138及onGYNetEnd(int,int,int,String,y0,byte[])精确签名。
新增syncHook安装状态和syncNetworkEnd：netId、输入错误码、请求对象身份、响应类/type、正文/byte[]仅长度、线程和相对时间。以同一个请求对象关联至sceneEnd和correlatedScene中的networkOrigin，不以时间相近推断因果。
每进程启动180秒内最多20次网络样本；到期卸载并清理弱引用映射。没有捕获就输出not_captured，不推断请求未到达。此层能区分错误是否已由network回调传入，不一定能直接确定底层网络失败根因；取得实机证据后再决定是否需要更低层诊断。
父级沿用原诊断，不猜测恢复，不删原失败日志。

## 验证

server_error_diagnostic/run.py通过，扩展测试实际Java诊断类的同步入口->同一请求场景->Toast关联，不输出错误正文及buffer内容；原参数/结果不变、嵌套上下文清理、超时和卸载通过。
git diff --check通过。未完整编译新增Kotlin Android诊断，未实机验证入口及实际树；不能宣称问题修复。

## 实机复现

完全退出后重进，不先浏览大量其他会话，立即进入微信支付、微信收款助手、云家校并上下滚动。16行采样可能被其他会话提前耗尽，因此如果没有覆盖，重启后直接进入目标页面。
主微信和分身分别测，注明用户环境；服务器诊断需启动3分钟内，等待3分钟后导出对应运行日志及最新LSPosed，保留错位截图。不清配置或改变分组。
关注bind-path-v2、native-time-v1、syncHook、syncNetworkEnd和networkOrigin。若通用bind_entry依旧没有记录，不能再修改锚点：需要检查实际View绑定入口是否根本未覆盖该样式。