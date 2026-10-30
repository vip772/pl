# 资源生命周期优化与验证边界

基线：dev 2bc5e00。参考 WeKii dev ec3c0d9 的资源所有权与取消思路，未复制实现。本次提交为资源治理的阶段交付，不代表整项优化已完成。

## 实际实施

- 通用异步池 core/max 均为 2，核心线程空闲 30 秒退出。修正无界队列使 max=2 通常不起作用的问题；任务完成顺序无保证，队列容量仍未设上限。
- 定时发送使用独立的两个工作线程，空闲 30 秒退出，不再占用脚本/通用任务池。运行时关闭后拒绝新任务，已接收发送正常完成，避免半途打断造成重复投递。排队/运行任务有进程内身份记录，reload 不把它们误判为崩溃残留。执行链失败后将本次结果标为不确定，阻止同进程自动重入；编辑计划时间可恢复，跨进程 exactly-once 不在本次保证范围。
- WakeLock 在真正开始发送时取得，结束时释放，不为等待入队的任务持有。
- 安装调度器保持单线程串行和原有重试策略，线程空闲退出；成功或外部标记成功时清理 installer 闭包，重复调度及标记交错采用状态锁。
- 背景加载保持串行，空闲线程退出；替换和页面清理取消未执行任务并从队列移除，完成后校验请求身份、页面存活、图片配置版本。已经开始的解码不强制打断。
- 背景刷新及加载使用页面弱引用，清理页面/运行时销毁时移除 decorView 刷新回调。延迟重试不强持有页面。
- 背景存储缓存同时限制 2 项和 24 MiB 分配字节；头像缓存限制 64 项和 8 MiB 分配字节。超大图片仍可显示但不保存在缓存中。此预算仅覆盖缓存持有的对象，不包含页面 Drawable 所持图片，不能将它称为全部图片内存上限。
- 系统内存压力回调只注册一次；UI_HIDDEN 不视作内存压力。压力时异步合并清缓存，避开主线程等待图片解码锁；工作线程空闲 10 秒退出。仅丢弃缓存引用，不 recycle 正在显示的 Bitmap。
- 插件卸载取消尚未开始的异步闭包，已开始操作不打断。取消任务的包装不强持有插件 scope。每插件最多 256 个未完成任务，过载记录错误并拒绝新任务；服务不可用时使用进程共享双线程备用池，备用队列最多 256 项，空闲 30 秒退出，替代每任务新建线程。
- 同一插件不同 HTTP 超时设置共享 OkHttp 连接池与 dispatcher，最多保留 8 个超时配置，保留原有 1–300 秒超时和重定向语义。

- 背景 HostState 由页面 decorView 的专属资源 ID tag 持有，运行时页面索引、表面索引及 preparedRoots 的状态引用改为弱引用；页面清理和运行时销毁解除 tag。避免弱键表的值通过 Activity/View 反向保活页面。
## 验证

通过生产代码提取编译/JVM fixture：

- test_wechat_task_executor.py：两个阻塞任务均可启动、96 个任务完成、线程池关闭。
- test_background_resource_lifecycle.py：排队取消移除、去重、取消后同配置重建、延迟旧结果拒绝、版本变化、失效刷新、页面销毁。
- test_resource_cache_budgets.py：LRU、分配字节预算、替换计数、空结果、超大项、缓存清理；实际 ComponentCallbacks2 代码通过 Android SDK 编译。
- test_memory_pressure_callbacks.py：注册去重、压力等级门控、异步合并、异常恢复重试。
- test_resource_schedulers.py：安装阶段门控、闭包释放、重复/已标记任务跳过、失败重试、发送隔离、重复入队防护、关闭后排队任务完成、拒绝后恢复待执行、执行链异常不立即重入。
- test_scheduled_send_state.py：生产 reload/finish 状态路径，覆盖其他任务仍在执行时不重复发送、一次性删除、重复计划推进、手动发送保留计划、异常计划暂缓和修改后恢复、启动残留状态恢复。
- test_background_state_ownership.py：活动页面保留状态、无外部引用的页面/状态循环可被 GC、显式移除索引。
- test_script_resource_scope.py：插件卸载取消待执行任务、已开始任务不中断、异常/拒绝清理；300 次提交验证 256 项上限和拒绝计数；真实 OkHttp 验证 8 项缓存、连接池/dispatcher 共用与超时保留。
- test_conversation_empty_footer.py、test_conversation_pull_down_shade.py、tests/background_beauty/run.py、test_background_render_gate.py、test_avatar_memory_cache.py 通过。

未运行 Gradle、未构建 APK、未安装手机验证。JVM fixture 替代了宿主/数据库，SDK 检查也只覆盖特定方法，不能代替整项目编译、真实发消息链、全部功能回归或实际内存比较。git diff --check 通过。

## 仍未完成的工作与具体约束

1. DexKit 仍长期驻留，未安装闲置 close。它是 final 类，结果对象可能继续访问原生数据；静态集合、定位器、FeatureContext、Monet、Agent 和脚本直接持有原始对象。脚本 API 文档还承诺 dexKitBridge 原始对象，插件初始化主动注入它。自动关闭会破坏这项已有接口。必须将内部查询及结果消费逐条迁移到受控使用区间，对原始对象暴露采用保活契约；在迁移完成之前启用 close 存在 native 失效风险。本轮没有创建无人使用的管理器冒充完成。
2. 通用池和发送池队列尚未设置硬容量；脚本 scope 已有每插件 256 项上限，共享备用池队列也限制为 256 项。将无返回值的旧接口变为拒绝任务、调用者运行、阻塞主线程或自动丢任务都会改变功能语义；需逐调用方接入可观察失败/取消与背压。不能宣称全项目资源有界。
3. 没有将全部功能的开关改为统一卸载重装；当前各功能存在自己的配置监听和共享服务，必须逐功能接入，不能直接 destroyAll。
4. 已处理背景主要状态索引的反向保活；没有完成微信全部页面和所有第三方 Drawable/Adapter 的堆引用分析，不能宣称全部页面引用泄漏已排除。
5. 不承诺节省具体 MiB，不将此前 ANR 归因于某一个候选风险。

本次按用户授权提交并推送 dev，推送触发仓库现有安卓单频道构建。最终编译和 R8 结果以对应提交的 Actions 为准；手机实测尚未进行。DexKit 释放、全项目队列硬背压和统一启停清理仍是后续任务，不能将此次推送称为全部优化完成。

## 接续：通用和定时发送背压
上一基线 e5af71b 的 Actions 41034226426 已成功完成编译和发布构建。
本次通用队列为 256、发送队列为 32；两个池仍各自两线程。runAsync 保留 void 签名，用明确拒绝异常反馈繁忙/关闭。已核查项目内唯一 runAsync 提交方为 ScriptWaBridge 的 ScriptAsyncScope，拒绝会取消 FutureTask 并记录插件错误，不转到主线程也不无限重试。插件关闭后提交也报告错误。
通用关闭改为排空，避免中断已开始的网络或发送操作。定时发送的 deferred 状态写入原有任务 JSON，跨进程恢复时保留该次未接收计划；有限定时重试及完成唤醒，手动立即执行拒绝返回 false。
定向验证：test_wechat_task_executor.py（满载拒绝、取消清队列、排空）、test_resource_schedulers.py（32 项发送队列满载及状态恢复）、test_scheduled_send_state.py（deferred 超过原窗口仍保留、重启恢复、有限重试）、test_script_resource_scope.py（关闭/过载错误）通过。以上仍不替代 Android 整项目编译与真实发送。
DexKit 安全释放、全功能统一启停仍未完成；本次未启用 native close。

## 接续：统一资源作用域首批接入
003e75f 已推送，Actions 41040645860 最终成功（包含 APK/R8 构建）。
新增 FeatureResourceScope 并实际替换 BaseFeature 原三个资源列表；已接入所有通过 subscribe/trackSubscription/registerSettingsProvider 登记的功能资源，以及背景、聊天工具栏、首页侧边栏、自动转发的独占运行时。重复初始化/安装/销毁及失败路径现在有明确清理；FeatureManager 也销毁已初始化但未安装功能。
test_feature_resource_scope.py、test_background_resource_lifecycle.py、test_conversation_pull_down_shade.py、test_conversation_empty_footer.py 通过；后两项含定向 Android SDK 编译。未执行本地 Gradle、未安装手机。
此为 C 的首批实接入，不是全部功能热启停完成；未改动所有功能的配置监听、共享资源边界和 Dex 安装状态。DexKit A 仍未实施安全关闭，不应将本阶段描述为三项全部完成。

## 接续：Agent 共享 Dex 查询串行
7c8d20f 的 Actions 41041713476 已成功完成。原有 13 项定向资源回归及新增生命周期回归通过（不代表整项目无回归）。
Agent 工具原来仅持有 targetLock，未通过共享 Dex 串行门；现在查询、延迟结果读取及 JSON 整理整体位于 DexInstallScheduler.queryDexKit 区间，锁顺序为共享 Dex 门→targetLock，与初始化一致。调度测试新增 40 次并发查询不重叠、嵌套可重入、异常后恢复，以及实际 Agent 区间源码约束。完整 Agent 编译以 Actions 为准。
这只是 A 的串行约束接入，不启用闲置关闭或重建。ModuleEntry 静态 bridge 集合、原始对象接口、内部持有链及脚本兼容保活契约仍未完成迁移。

## 接续：脚本与 Holder 查询区间、运行时订阅归属
c17dd61 的 Actions 41042601510 已核实 completed/success，包含完整 APK/R8 构建。
ScriptDexKitBridge 的类/成员便捷查询及 DexBridgeHolder 的字符串查询现在通过与安装器、Agent 相同的可重入串行门；结果转换为 Class、Member 或字符串信息后才离开区间。Java 调用入口为 queryDexKitTask(Callable)。原始 bridge()/holder() 和脚本原始对象注入保持兼容；外部直接操作原始对象仍不受此门自动约束，本次不启用 native close。
BaseFeature 新增捕获创建时资源作用域的 subscriptionRegistrar。QQ 点歌实际使用该登记器，独占运行时构造后、安装前即登记 destroy，订阅清理先于运行时关闭；安装中途抛错也能释放该运行时。定时任务同样在 coordinator attach/start 前登记独占运行时，启动失败会 detach 并排空已接收任务。旧登记器在关闭并重新初始化后继续回调时立即释放旧资源，不登记到新一代。本次不代表所有异步安装器已迁移，也没有开启全局热卸载。
验证：test_dex_query_consumers.py 使用真实 DexKit JAR 编译生产 Java Holder、Kotlin 脚本查询与调度器；验证空/不可用实例路径、120 次 Java/Kotlin 混合串行、嵌套及异常恢复。test_feature_resource_scope.py 新增跨两代登记验证；test_resource_schedulers.py 通过。native 查询、关闭重建、真实发送及手机验证仍未完成。

### 生产入口失败注入与回归补充
生命周期 fixture 现编译实际 QQMusicOrderFeature，以及 ScheduledTaskFeature 的生产功能入口（运行时由 fixture 替换）。注入运行时启动异常，验证订阅释放、独占运行时关闭、定时任务 coordinator 解绑；再次安装、重复销毁及旧订阅登记回调也通过。此验证覆盖入口归属，不替代真实 QQ 音乐网络或微信发送。
已有 13 项资源定向回归与新增两项（feature_resource_scope、dex_query_consumers）均通过；背景综合测试曾被组合命令总限时取消，单独重跑通过。没有执行本地 Gradle，没有安装手机。
