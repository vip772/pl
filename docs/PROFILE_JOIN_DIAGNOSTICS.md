# 进群时间定位版（基准 776cdd0）

本轮为诊断，不是修复版。保留现有原生时间优先、当前群历史补查及校验规则；不改实验配置，不发送网络请求，不做跨群扫描或启动全群预热。

## 日志与判读

统一检索 `JOIN_DIAG`，按同一后台查询的输出顺序关联；群查询由现有单线程执行器执行。

- `gate`：只读调用已核实的微信 8.0.78 `lb2.e.g().c(od5.e)`，参数为 `RepairerConfigChatroomJoinInfo`。`state=read value=0/1` 才是实际返回值。`unavailable` 表示反射调用失败，不能推定关闭。其他版本混淆名不保证适用。
- `native`：记录游标/数据行/blob/外层解析状态、字节数、成员项数、可识别成员数、重复数；将成员分为 parseErrors、absent、zero、early、overflow、future、valid。分类计数以成员项为单位，valid 可能包含没有用户名或重复的成员，不能直接等同最终原生命中数。
- `historyTable`：每张候选表输出读取行数、非正时间行数、包含待补成员ID的有效时间消息行数、被现有规则接受的消息行数。行数不等于成员数。查询仍只读取当前群及现有9种消息类型，不能据 rows=0 断言数据库没有任何其他类型的入群事件。
- `historySummary`：输出汇总、补查前后缺失成员数及历史链路耗时。没有输出汇总而出现错误时，应按已输出的表定位失败点，不能视作完整扫描。
- `cacheStored`：查询结果已写入当前代际快照；关闭/代际变化的过时任务不应产生此记录。
- `display`：按 Activity 和状态变化去重；记录群和成员的非加密 hash、快照时间、配置关闭、资料页参数不可用、等待数据、成员无时间、已返回文本。`text-returned` 仅证明 Provider 返回文本，不证明屏幕最终可见。

## 验证流程

安装包含本次修改的版本后重启微信；开启进群时间，分别打开一个已显示和一个缺失成员的资料页，等待当前查询结束。再进入同群另一个缺失成员，并打开另一群缺失成员。导出这一轮完整 Hchat 运行日志。

判读顺序为 gate → native → historyTable/historySummary → 原有索引完成日志 → cacheStored → display。若 native 的 zero 占多数且 gate=0，只能确认当前数据为零和当前开关关闭；还不能证明这些数据历史上由该开关清零。若需要确认服务器是否提供时间，后续必须取得对应详情回包证据，本轮不声称覆盖服务器事件。

## 开销与边界

只有既有按需后台查询读取原生开关；无新增 Hook、定时任务或网络请求。消息循环只增加计数，不逐条输出日志。每个查询每张表最多一条诊断，资料页仅状态变化输出；不输出消息正文、原始 protobuf 或新增完整成员身份。原有日志仍带群ID；资料页 hash 仅用于关联，不是匿名化保证。

原生读取改为直接 Cursor，以区分空游标和空结果，并使用 try-with-resources 关闭。原有 query() 会吞掉部分读取错误，无法作上述区分。其他调用沿用同一解析路径，不增加诊断日志。

未运行 Gradle、未构建 APK，所有定向编译和 JVM 夹具结果都不能替代新版实机数据。最终根因和修复仍待日志取证。

验证记录：scripts/check_profile_runtime_sdk.py PASS（/tmp/join-diag-sdk-final.log）；生产解析器有效/缺失/零值/截断分类断言通过；scripts/tests/profile-demand/run.py PASS（/tmp/join-diag-demand.log），覆盖无启动扫描、禁用不加载、去重、空快照刷新、冷却、销毁。定向 SDK 编译依赖已有 ABI 与夹具。

后续原生写入完整链路诊断与缓存修复见 [PROFILE_JOIN_ROOT_DIAGNOSTICS.md](PROFILE_JOIN_ROOT_DIAGNOSTICS.md)。此前“无新增Hook”仅适用于4bf1d3d定位版，后续本地修改新增由进群时间开关控制的13个只读原生探针。
