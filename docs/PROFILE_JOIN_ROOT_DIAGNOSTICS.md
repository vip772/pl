# 进群时间：原生写入诊断与缓存修复

基准 dev / 4bf1d3d25352de0b6f3824f60d04e88a196ed0e3。本轮初始依用户要求保留本地修改；用户随后要求与右划复读弹窗文案一起全部提交推送。未运行 Gradle、未生成或安装 APK。

## 现有实机证据

Hchat-runtime-20261106-214743.log：26/208/200 个成员项的原生时间字段均存在且为0；解析失败、字段缺失、时间校验排除均为0；配置实读0；历史补查得到2/4/2个成员并成功缓存。它只能证实当前读取状态，不能追溯安装前的清零原因。

## 已实施的源码证据修复

1. 单张历史表失败不再终止后续表；保留已有结果并标记 complete=false。原生读取不完整、成员解析失败、表发现为空、无成员列表、数据库不可用均不记作完整结果。
2. 不完整快照即使已有当前成员命中，也允许15秒冷却后的按需重试，防止部分历史结果长期遮蔽后续原生时间。该修复解决源码中可达的错误路径，不宣称它造成了此次全零。
3. 原生成功写入触发旧群缓存失效；单线程、按群合并、最多32群的验证任务读回数据库，输出对象blob与读回blob的SHA-256比较。异步读回可能看到后续写入或未提交事务，hashMatches=false不能单独判定写入失败。
4. 查询完成时刷新当前群资料页一次，涵盖当前成员缺失情况，替换逐命中成员发起刷新。关闭后停止任务；旧代际任务不能移除新代际任务标记。

没有强制开启微信配置、改写回包/微信成员数据、跨群补时间或伪造未知时间。历史规则中的邀请人/入群人歧义仍保留为待实机结构核实问题，不把泛化匹配接受行当作真实入群证明。

## 原生取证覆盖

独立 JoinTimeNativeDiagnostics 由进群时间偏好所有权控制，从功能初始化开始安装，不等待第一次打开资料页；关闭时卸载所有句柄并清理字段缓存。观察器异常隔离，不影响原生参数、返回值、异常。

13个方法签名均对照用户提供的8.0.78 smali：

| 方法 | 记录 |
|---|---|
| qn.w.doScene | 请求群、版本、scene对象标识、返回值 |
| qn.w.onGYNetEnd | errType/errCode、响应版本、声明/实际成员数、JoinTime分类；完成时使用的m开关 |
| lb2.e.c | JoinInfo最终实际返回值 |
| ou5.n1.m/g | 本地策略分支、实际本地返回值 |
| RepairerConfigChatroomJoinInfo.c | 实际调用时的默认值 |
| lb2.e.i/a | 该实验键的fallback、flags、原始返回为空/数字、最终数字 |
| storage.z2.P0 | 实际gate参数、输入时间、更新前后map/proto/blob；按原始ID在内存关联，统计清零、移除、变化、正值复制与未复制 |
| b41.u1.N | ModContact输入成员JoinTime、旧proto、返回值 |
| b41.u1.M | 持久化派发对象与返回值，包含提前拒绝 |
| storage.z2.X0 | 序列化输入proto、输出blob状态 |
| storage.a3.Q1 | 存储输入、结果/异常、输出对象；成功后触发后台读回 |

nativeEvent 的 id/parent 关联同线程嵌套调用；请求和响应以群+scene标识关联，异步阶段不能仅凭parent=0当作无来源。probeSummary expected=13；任一 unavailable 或 observerError 都表示取证有缺口，不能宣称该阶段正常。实际Hook安装和字段读取成功仍待设备验证。

## 历史、缓存及界面取证

- discovery：映射消息表及候选表数。
- typeInventory：只统计当前群各类型数量，不读取普通聊天正文，能发现固定9种候选类型之外确有数据；每表最多64种类型输出，超限明确truncated。这项增加一次当前群分组查询，仍需实机评估耗时。
- historyTable/historySummary：候选行、时间非法、ID关联、规则接受、完整性及耗时；每表错误记录后继续。
- historyShape：候选事件XML标签、role/user数量和hash、加入/邀请关键词标记，帮助核查现有泛化规则。每表最多32种脱敏样本，超出记shapeOmittedRows；单条仅检查前16384字符并记录truncated。原文、昵称、原始成员ID不输出。hash不是匿名化保证。
- schedule/cacheStored/display：队列容量、API不可用、完整性、缓存时间和是否待查询，区分等待与确认缺失。
- render：模板父容器异常、非纵向布局、时间行已添加及attached/shown。row-added不等价于用户肉眼可见或无裁剪。

## 判读和不可省略的边界

先看probeSummary，再关联 request → responseMembers → gatePolicy/gateLocal/gateExperiment → gateUsed → detailApply/detailApplied 或 modContact/serialize → persistResult → persistedRead → native/history → cacheStored/display → render。

输入正值、实际gate=false、同成员输出变零，才能证实捕获到该次丢弃；仅gate=0和库零值并存不够。响应零值说明本次响应无有效时间，不代表服务器从未存有时间，也不能仅凭此确定服务端内部原因。请求失败、版本未更新、空列表、成员数不一致、存储拒绝、字段读取失败必须按实际记录判读。

本轮不主动制造成员更新或新增网络请求。必须捕获自然发生的成员详情/ModContact更新；若无request/response事件，不能称服务器链路已验收。安装前历史原因不能由新增日志追溯。脱敏样本被截断、未覆盖其他事件类型的语义、不同微信版本签名变化等均明确保留边界，不能保证有限一次日志解释所有未知原因。

## 验证

- scripts/tests/profile-demand/check_native_probes.py：反编译签名/关键字段校验。
- scripts/tests/profile-demand/run.py：生产Provider按需加载、关闭、去重、空缓存刷新、不完整已命中快照重试、原生写入失效和读回。
- scripts/check_profile_runtime_sdk.py：定向SDK/Kotlin/Java编译及生产protobuf解析器、脱敏结构、数据库不可用完整性、原生成员转换分类测试。
- git diff --check。

以上不是完整Android/R8构建或实机验收；七阶段资源生命周期验收维持0/7。最终测试输出另见docs/evidence/PROFILE_JOIN_ROOT_LOCAL_CHECKS.txt。
