# 资源生命周期优化与验证边界

当前状态：通用任务队列 256、定时发送队列 32；聊天工具栏、首页侧边栏、自动缓存和语音播报已接通实际主开关资源释放/重建。Agent 独占外部 APK 解析实例已接入 30 秒闲置回收及按需重建；宿主共享 DexKit 仍未启用自动关闭，脚本原始对象保活契约、其余功能统一启停及手机/TTS/真实发送验证仍未完成。下列“基线/接续”段落记录各阶段证据，旧阶段的未完成清单不代表最新状态。

基线：dev 2bc5e00。参考 WeKii dev ec3c0d9 的资源所有权与取消思路，未复制实现。本次提交为资源治理的阶段交付，不代表整项优化已完成。

## 实际实施

- 通用异步池 core/max 均为 2，核心线程空闲 30 秒退出。修正无界队列使 max=2 通常不起作用的问题；任务完成顺序无保证；后续已将等待队列限制为 256，见背压接续。
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
2. 通用池 256 和发送池 32 的硬容量及拒绝恢复已在后续接续完成；每插件及备用池也有 256 项上限。真实发送、持久化失败及重启恢复仍需手机验证，不能宣称全项目资源有界。
3. 没有将全部功能的开关改为统一卸载重装；当前各功能存在自己的配置监听和共享服务，必须逐功能接入，不能直接 destroyAll。
4. 已处理背景主要状态索引的反向保活；没有完成微信全部页面和所有第三方 Drawable/Adapter 的堆引用分析，不能宣称全部页面引用泄漏已排除。
5. 不承诺节省具体 MiB，不将此前 ANR 归因于某一个候选风险。

本次按用户授权提交并推送 dev，推送触发仓库现有安卓单频道构建。最终编译和 R8 结果以对应提交的 Actions 为准；手机实测尚未进行。DexKit 释放、队列完整链路真实验证和全功能统一启停清理仍是后续任务，不能将此次推送称为全部优化完成。

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

## 接续：功能独占安装状态和聊天工具栏 Hook
98fccda 的 Actions 41047526655 中“构建当前频道 APK”成功；工作流整体失败发生在发行版发布，日志明确为 v10.2.0 已存在。758df97 的运行 41046821381 已整体成功。两个并发工作流在构建前选中同一版本；单频道工作流现使用仓库级 concurrency（cancel-in-progress=false），将版本选择到发布置于同一互斥区间，避免后续单频道运行互相竞争。没有覆盖已有发行版。
DexInstallScheduler 新增功能独占 Installation 凭证：关闭后拒绝再次登记、清除 installer 和 installed 状态、撤销延迟重试；排队旧状态不再执行，执行中的安装不打断，完成后也不会复活旧状态。新一轮使用新凭证重新安装。共享 API key 继续沿用原语义，不能用功能清理撤销共享安装。凭证要求 key 为该功能独占。
聊天工具栏实际接入此凭证，安装闭包绑定具体运行时，事件迟到时不会操作新运行时；构造 Hook、工具面板 Hook 和偏好监听由运行时作用域释放。关闭不等待 Dex 查询锁，关闭后迟到获得的 Hook/监听立即解除，清理已有工具栏 View。未开启全部功能统一热启停。
test_resource_schedulers.py 新增：失败待重试关闭、关闭后禁止登记、同 key 下一轮安装、正在执行时关闭并重建、排队安装取消；与 Dex 查询及生命周期测试均通过。完整聊天工具栏编译与 R8 由本提交 Actions 验证，手机验证尚缺。

## 接续：聊天工具栏真实开关
聊天工具栏已由实际 SharedPreferences 主开关驱动运行时创建/清理，设置 Provider 保留；功能级偏好监听和 DexReady 订阅保留到功能销毁。关闭清理运行时、独占安装凭证和生命周期订阅，再次开启重新创建；旧监听跨销毁到达时因原代 active=false 不再动作。安装后在当前 Activity 一次扫描已创建的 ChatFooter，恢复当前页面挂载；不增加滚动/绘制期扫描。
生产 ChatToolbarFeature 已加入生命周期 fixture，20 轮开启/重复开启/关闭/重复关闭、安装异常、失败后恢复、销毁后重装和旧监听回调均通过。运行时 Hook 与 UI 的真实 Android 行为仍以完整构建和手机验证为准，不能由此宣称全功能热启停完成。

## 接续：首页侧边栏销毁链
核查发现 HomeSidePanelRuntime.destroy 未关闭头像执行器，旧安装和页面回调也缺运行时失效门。现新增 active 门、合并回填检查；销毁时移除 Handler 回调，清空等待头像任务并 shutdown，已开始任务不中断，结果不再更新已销毁页面。头像执行器空闲 30 秒退出。页面逐项清理隔离异常，解绑后清除 Session 持有的 avatar/retainedAvatar 引用；不 recycle 已显示 Bitmap。Hook 和偏好监听继续由该运行时独占释放，重复销毁不重复清理。
test_home_side_panel_lifecycle.py 提取生产销毁方法、执行器和回调门，验证排队丢弃、已开始任务不中断、迟到回填、异常隔离、Hook/监听只清一次及关闭后拒绝。此测试不替代完整页面、网络加载和 Android 产物验证。本轮未将首页侧边栏改成全局热启停，DexKit 自动关闭仍未启用。

## 接续：定时发送拒绝后人工恢复
确认设置页开关实际调用 ScheduledTaskRuntimeCoordinator.reload。原先达到 6 次入队重试后，关闭再开启仍保留计数；现关闭 reload 仅清 admissionRetries，保留 deferred 计划、已接收身份和 uncertain 记录。重新开启允许新一轮有界重试，不立即重放结果不确定的发送。
生产 reload/finish fixture 新增：耗尽后关闭重开、deferred 删除及编辑计划时间、执行中删除后完成不重建任务、执行中改计划后完成不覆盖新计划，均通过。test_resource_schedulers.py 也通过。真实微信发送、跨进程 exactly-once 和持久化磁盘失败仍不在已验证结论内。299eec8 的 Actions 41049693859 已整体成功，含 APK/R8。

## 接续：首页侧边栏实际开关
首页侧边栏主开关现驱动独占运行时创建/释放，关闭时解除生命周期/聊天页订阅、Hook、头像任务和页面挂载；设置 Provider 和功能级开关监听保留，重新开启创建新运行时并挂到当前主页。共享 QuickMarkReadRuntime 仅调用幂等 install，不随侧边栏关闭。生产 HomeSidePanelFeature 编译进 fixture，20 轮重复开关、启动失败及恢复、销毁后重装和旧监听迟到均通过；头像销毁链 fixture 同时通过。尚缺真实手机 UI 验证。

## 接续：Agent 重绑入口锁顺序
Agent install 公开入口现自行取得共享 Dex 门后再取 targetLock，外部目标关闭和宿主重绑都在门内。原 ModuleEntry 外层门保持可重入；不能只依赖当前唯一调用位置的外层锁。调度测试增加生产重绑入口的锁顺序约束，原并发、嵌套和异常恢复测试继续覆盖同一共享门。此项不启用宿主 Dex 闲置关闭。

## 接续：Finder 公开定位入口
DexFinder 的 37 个公开 resolve 入口及 findNativeNetSceneClass、SettingsDexFinder.resolveAll(boolean) 现自行进入共享 Dex 门，调用方无需承担其内部查询串行责任；整个原定位体（含 native 延迟结果消费）位于区间内，缓存定位算法未改。原 synchronized resolveAll/resolveChatPageApi 移到门内私有实现，锁顺序为共享门→Finder monitor，避免脚本直接调用与安装器顺序相反。
新增 test_dex_finder_gates.py：编译完整生产 DexFinder、SettingsDexFinder、CacheSnapshotEditor、KavaReflector、版本模型/API 和调度器，使用真实 SDK/DexKit/KavaRef，日志/存储/Xposed 外围 fixture；公开入口门约束检查通过。仍未验证实际 native 查询与全部微信版本，原始 bridge 持有链和自动关闭重建尚未完成。

## 接续：朋友圈定位结果消费
SnsInteractionLocator 的点赞、评论、评论保护方法和刷新构造定位入口现先进入共享门再取定位器 monitor。SnsLivePhotoUploadLocator 的完整定位（含 methodData.usingFields 和反射成员整理）也处于门内，返回仅含反射成员的运行时数据。原字符串匹配、成员校验及缓存 key 未改。test_dex_finder_gates.py 扩展编译这两个完整生产定位器及实际 DexMethodCache，使用真实 SDK/DexKit/KavaRef，通过；仍不替代朋友圈实际发送和多版本 native 查询验证。

## 接续：消息菜单与收藏运行时查询
SingleMessageMenuLocator、MultiSelectMessageMenuLocator 的所有公开定位入口和 FavoriteMenuLocator 两个菜单入口现通过共享 Dex 门；返回前完成反射 Method 转换。FavoriteVoiceResolver 的下载方法、媒体路径定位入口也在门内，包含延迟 invokes 读取；实际下载/发送不在定位区间执行。定位 matcher、缓存 key、返回类型保持原语义。
test_dex_finder_gates.py 扩展编译上述完整生产定位器、收藏语音解析器及实际缓存，依赖真实 SDK/DexKit/KavaRef/协程；FeatureContext 和任务 API 为外围 fixture。通过，仍不代表真实菜单交互或语音下载已手机验证。5764c96 的完整 Actions 41052126713 已成功。

朋友圈转发原生媒体、本地媒体路径和上下文菜单定位入口（含折叠卡片绑定完成）现自行通过共享 Dex 门，native 查询及 Method 转换均在返回前完成。缓存和定位条件不变；完整生产定位器已加入 test_dex_finder_gates.py 并通过 SDK/DexKit 编译。手机转发与菜单交互尚未验证。

## 接续：语音播报独占清理
TextSpeechFeature 在后续安装前即登记独占 TextSpeechRuntime，音量键 Hook 也登记解除。运行时 shutdown 幂等，移除 Handler 旧回调，主线程立即停止播放并关闭 TTS，后台调用只投递一次清理；销毁后音量键回调直接退出。TTS 原有 generation 和 destroyed 校验保留。test_text_speech_shutdown.py 通过主/后台清理、旧回调移除、重复关闭及生产资源登记约束。实际 TTS 引擎、音频会话和手机播报尚未验证；本项不等于语音播报已接入完整主开关重建。

共享 WeChatSnsPostObserver 和 WeContactHeaderApi 的 install 入口现先取共享 Dex 门，再取原安装 monitor；定位查询及关联 Hook 安装处于同一区间。共享 Hook 所有权不变，不随单个功能消费者关闭。test_dex_finder_gates.py 编译完整生产入口（Xposed/Hook 注册外围 fixture）通过，实际手机 Hook 尚未验证。

独占 Installation 现为每个凭证分配 owner 身份，只有相同身份可撤销其状态；同 key 其他凭证的迟到关闭不会删除现有安装。并发重复占用同 key 明确拒绝，原 owner 关闭后新凭证可接管。生产调度测试新增未登记同名 close、冲突登记、冲突凭证 close 不影响原安装、关闭后新 owner 安装，均通过。共享 schedule 的 owner 为 null，现有共享 API 语义保留。

BaseFeature 事件订阅包装现绑定订阅时的资源作用域，作用域已关闭时拒绝快照中的迟到回调。已开始回调不中断，其线程内 trackResource/trackSubscription/subscribe 继续归属原代，跨关闭重开后的迟到登记立即释放，不污染新代；ThreadLocal 在 finally 恢复/移除，不传递到另外启动的线程。跨线程异步任务仍需显式捕获登记器，不能据此认为所有异步安装器已安全。生命周期 fixture 验证旧快照回调不执行、回调运行中关闭重开、旧回调随后登记资源立即释放，通过。

输入框提示运行时构造后立即登记到功能资源作用域，DexReady 安装闭包固定该轮 runtime，统计订阅使用捕获原作用域的登记器。销毁幂等、清除 Handler 等待回调，销毁后拒绝 Hook/统计重新安装。test_input_hint_lifecycle.py（含新增重复销毁/关闭后拒绝）及 test_input_hint_sdk.py 通过，保留 3000 次稳定布局不重复渲染和原生提示恢复验证；未开启该功能全局热重启。

群员头衔渲染器现实际登记为功能独占资源，安装闭包和群成员刷新固定本轮实例，独占 Installation 关闭撤销旧 installed/重试。渲染器 destroy 幂等，解除自己的 Hook，撤销含运行时身份的延迟任务，清理缓存及绑定；只 hideTitle，不释放共享昵称装饰布局或其它尾缀。旧刷新/延迟回调检查 active；迟到获得 Hook 在已关闭作用域内立即卸载。test_member_title_lifecycle.py 提取生产 destroy/延迟方法及真实作用域验证取消、旧回填拒绝、晚登记 Hook 清理、页面异常隔离和重复关闭，通过；整渲染器编译及真实昵称布局仍待 Actions/手机验证。未接通群员头衔完整主开关重建。

视频号详情定位、聊天实况图片 fresh 定位、官方群发运行时定位及转账实名 ensureReady 现自行通过共享 Dex 门，含延迟 invokes 消费、缓存整理及关联 Hook。网络发送和响应等待不放在查询区间内。test_dex_query_consumers.py 新增这些生产入口及官方群发子定位的源码约束；完整四文件编译仍依赖 Actions，不能把该约束当作真实发送/网络查询通过。原 matcher 和缓存 key 未改。

会话长按菜单和朋友圈头像菜单定位入口自行通过共享 Dex 门，完整生产定位器加入 SDK/DexKit 编译并通过。收款账户解析共享 Hook 安装先取共享门再取安装 monitor，源码入口约束已覆盖，整文件编译以 Actions 为准；不改账户解析字段、收款行为或缓存定位条件。1e7a88c 的完整 Actions 41056337449 已成功。

## 接续：早期入口与外部 APK 闲置回收
平板模式、屏蔽热更新的公开 install 入口现自行进入共享 Dex 门，屏蔽热更新内部 monitor 在共享门内；Monet 证据采集也自行持有门直到 callers/invokes/usingFields 转换为独立数据完成。定位条件、缓存、Hook 行为保持原实现。test_early_dex_gates.py 编译三个完整生产文件及生产缓存/调度器/Monet 模型，依赖真实 Android SDK 和 DexKit ABI，通过；test_dex_query_consumers.py 的入口约束和并发测试通过。
Agent 外部 APK 在最后一次工具执行结束 30 秒后释放独占 Binding，包括已初始化的资源解析模块和 Dex native 实例；回收在后台取得共享门→targetLock，查询进行中不关闭。每次执行（含失败、取消）重新登记票据，迟到旧票据和旧目标身份均不能关闭新目标；重绑会取消票据。只卸载解析实例，已登记目标会话和 APK 文件保留；下次查询沿原 resolveTarget 校验文件后重建。外部实例发布前设置 native 线程数为 2，初始化失败关闭未发布实例。定时执行器取消后移除任务，闲置核心线程可退出，不在主线程执行 native close。
test_agent_external_idle.py 提取生产回收方法并结合生产共享门，验证旧票据、目标替换、关闭/重绑取消、活动查询排斥、重调度和只释放一次；test_resource_schedulers.py 通过。该 fixture 不包含真实 native close/重建，整文件编译和 R8 仍以对应 Actions 为准。宿主共享 DexKit 未启用闲置关闭，脚本原始对象兼容保活契约和全持有链迁移仍待完成。
累计构建证据：7992896 对应 Actions 41057094593、3c05219 对应 Actions 41057865324 均已 completed/success；包括群员头衔完整源码编译。本轮新增修改需等待新的 Actions，不将前一提交成功扩大到当前修改。手机昵称布局、实际查询重建和真实发送仍未验证。群员头衔可见行恢复的源码与定向测试已在本轮完成，仍不等于手机验收。
## 接续：DexKit 租约与代际管理基础
本轮新增 `DexKitBridgeManager`，在共享宿主 bridge 发布时分配代际编号，记录活动查询、普通租约和 raw-compatibility 租约。`ModuleEntry` 发布当前 bridge，并由 `DexBridgeHolder` 持有一份兼容租约；因此旧脚本公开的原始 `bridge()` / `holder()` 以及尚未迁移的内部入口仍阻止 native 闲置关闭，兼容行为没有被破坏。

管理器提供 `query(Lease, Callable)` 和无持久租约的 `queryCurrent(Callable)`，并提供 `closeIfIdle()` / `closeRetiredIfIdle()` 作为后续第 8 步的受控开关。当前没有自动调用关闭方法，也没有把管理器对象当成 native 回收完成。新增 `test_dex_bridge_manager.py` 使用替身 bridge 验证代际复用、raw 租约阻止关闭、查询计数、旧代/当前代空闲关闭和关闭后拒绝查询；未运行 Gradle、未加载真实 native、未开启宿主回收。

## 接续：聊天分组运行时销毁链

逐项审计 `ConversationGroupRuntime` 后确认其执行器、偏好监听、conversation/contact/chatroom 三类订阅及独占 Hook 集合此前没有完整销毁链。现保存三类订阅和运行时偏好对象；destroy 幂等关闭 active 门，取消订阅和偏好监听，移除主线程回调，解除本运行时登记的 Hook，清空绑定/索引/缓存并 shutdown 独占执行器；下一次 install 在需要时创建新执行器。异步回调增加 active 检查，避免关闭后继续触发同步。
本轮仅完成源码根因修复和现有分组/父级恢复定向测试；未运行本地 Gradle，需等待本次提交对应 Actions 的完整编译/R8，并在手机上验证聊天分组关闭重开、首页/原生折叠分组、置顶/隐藏/已读和资源回收。

## 接续：自动缓存独占运行时
AutoCacheFeature 每轮安装创建 AutoCacheRuntime，创建后先登记关闭再启动；运行时独占工作队列、待处理集合、原生入口和 Installation，订阅使用创建代际登记器。关闭幂等，撤销等待/重试安装，取消延迟任务、清理集合并释放原生入口引用；新安装创建新队列，旧回调只访问旧运行时。安装中关闭后不发布新原生入口、不继续订阅；迟到订阅由原作用域立即解除。工作任务入队与关闭并发的拒绝显式清理标记，关闭后在途下载结果不再回填 lastSubmitted。已经进入微信原生调用的操作不保证撤销，用户开关暂仍保持原有回调过滤语义，未宣称完整热启停。
test_auto_cache_lifecycle.py 编译完整生产功能及调度器/资源作用域（微信 API、存储、原生下载为 fixture），30 轮验证延迟任务取消、独占凭证关闭、集合清空、幂等及新运行时独立队列，通过；test_feature_resource_scope.py 通过。实际下载、手机验证和本提交 Actions 尚待核实。

Agent 工具执行的宿主 Binding 读取已移入共享门和 targetLock 内，排队等待期间发生重绑后读取新绑定，避免继续使用重绑前已关闭的资源模块。调度测试增加锁内读取约束；外部闲置回收与调度测试通过。

自动缓存主开关现接入运行时创建/关闭：关闭释放消息订阅、独占安装凭证和延迟检查队列，重新开启创建新运行时并有限补装。配置 Provider 与开关监听保留到功能销毁，旧监听快照以 active 拦截，旧订阅登记固定原运行时资源作用域。媒体类型和会话筛选保持原语义，不补发关闭期间错过的事件，不保证撤销已进入微信的下载。test_auto_cache_lifecycle.py 扩展编译完整功能并通过 20 轮实际偏好监听驱动的开关、重复开启不重复创建、关闭释放、销毁后旧监听拒绝，以及原 30 轮运行时清理。宿主 API、真实下载和手机仍待验证。

自动缓存任务读取开关/账号已纳入异常重试区间，关闭检查优先于存储读取；此前这部分异常会直接结束任务并遗留 pending。定向测试新增实际生产 enqueue 的活跃运行时入队拒绝、关闭后迟到入队及偏好读取失败到达重试上限后的双集合清理，均通过。保持既有 720 次上限和间隔，不改原生下载入口。

## 接续：语音播报回调代际与订阅去重
文字转语音播报消息与音量键回调固定创建时的 TextSpeechRuntime，不再通过可变 runtime 字段查找新实例。每轮安装捕获 subscriptionRegistrar，重复 DexReady 通过原子状态避免重复消息订阅，订阅异常重置以允许重试；关闭过程中迟到订阅立即交回原作用域释放。test_text_speech_feature.py 编译完整生产功能入口并验证重复 readiness、订阅失败恢复、跨代旧消息/按键隔离及订阅中关闭；test_text_speech_shutdown.py 验证主后台关闭，均通过。当时尚未接通语音播报主开关；后续已完成，见本文件最新语音接续段。真实 TTS/手机仍待验证。
累计 9437a6f 的 Actions 41062098925 与最新 f7485f2 的 Actions 41064186034 均已成功；后者覆盖语音播报主开关完整 APK/R8 和发布构建。

## 接续：群员头衔关闭重开后的可见行恢复
群员头衔渲染器现在为已成功绑定的 TextView 额外保留静态弱键映射，仅记录 room/member 绑定，不持有 Adapter、Activity 或其它页面强引用。关闭时仍按本代 boundViews 清理装饰；重新安装成功后只在主线程检查仍 attached、shown 且 roomId 与当前聊天一致的行，再调用现有 `applyTitle` 恢复，不扫描 Adapter、不强刷列表，也不会恢复其它群聊或不可见/已脱离窗口的行。
`test_member_title_lifecycle.py` 新增跨代可见行模型：验证关闭后旧延迟任务不回填、恢复只作用于当前群聊可见行并移除已脱离窗口的记录；原有 Hook/缓存/重复销毁/异常隔离断言继续通过。该测试仍不能替代微信真实 RecyclerView 复用、昵称布局和设备关闭重开。

## 接续：语音播报主开关与独占运行时重建
文字转语音播报现由主开关创建/释放独占 TextSpeechRuntime、音量键 Hook 和消息订阅。关闭按运行时作用域逆序解除 Hook、订阅并调用幂等 TTS shutdown；再次开启创建新实例。DexReady 安装闭包固定该代实例，重复 ready 不重复订阅，订阅异常可重试，销毁后的旧偏好监听快照被 active 拦截。运行时自身偏好监听也先检查 destroyed。test_text_speech_feature.py 现编译完整生产 Feature 和资源作用域，20 轮主开关、旧代回调、订阅失败恢复、重复 ready、关闭/重开和销毁后迟到监听通过；test_text_speech_shutdown.py 原主/后台关闭测试通过。真实 TTS 引擎、音量键设备行为和手机仍待验证；不保证撤销已经交给语音服务的播放。

## 接续：QQ 点歌运行时主开关
QQMusicOrderFeature 监听既有 `qq_music_order_enable`，开关关闭时逆序释放消息变更订阅并销毁独占 QQMusicOrderRuntime，重新开启创建新运行时。偏好 listener 与 DexReady 回调在功能销毁后由原资源代拦截；运行时创建后先登记销毁，安装失败继续抛回 BaseFeature 的原清理路径。共享 `shared:send_button` Hook 仍按共享所有权安装，不随点歌功能关闭卸载；点歌 handler 订阅跟随运行时释放。
`test_qq_music_lifecycle.py` 编译完整生产 Feature，覆盖多轮开关、重复开启去重、关闭/重开、旧偏好监听、旧运行时登记器和共享 Hook 隔离；`test_feature_resource_scope.py` 保留生产入口安装失败/迟到订阅清理检查并增加 QQ 点歌关闭后迟到监听隔离，均通过。实际 QQ 音乐网络和微信卡片/语音发送、手机验证仍未执行；开关关闭不会撤销已经进入发送流程的请求。

## 接续：全功能资源清单与关键词通知运行时释放
全功能资源归属清单见 `docs/RESOURCE_LIFECYCLE_INVENTORY.md`。按功能 Feature、资源类别（线程/Handler/订阅/Hook/缓存）、共享所有者与设备验证边界梳理；这是一份源码清单，不是运行时堆分析或项目所有功能均已完成热启停的结论。

源码核查确认 `KeywordNotificationRuntime` 原在 object 初始化时创建永久单线程执行器，Feature 销毁只释放消息订阅。现改为按 Feature 代际创建执行器并登记 `stop()`，销毁关闭运行门、取消待执行消息任务、清理主线程待通知/Toast/铃声回调及头像缓存，关闭私有执行器。运行代际编号贯穿后台消息任务、主线程投递和延迟铃声停止；旧代尚未执行的回调不能在关闭后新一代重开时复活。`MessageBlockFeature` 同步使用的 `shouldAllowBlockedMessageIntoDatabase` 保持可用且不要求通知 Feature 的工作线程。

`test_keyword_notification_lifecycle.py` 编译实际 `KeywordNotificationFeature` 生产入口，验证作用域资源先登记、重复销毁、重开；独立 generation model 覆盖旧任务跨代拒绝与队列清理。该 fixture 对 Android Runtime 及通知服务使用替身，不能证明微信 Hook、系统通知/音频或实际线程/Bitmap 回收。`test_feature_resource_scope.py`、`test_input_hint_lifecycle.py`、`test_qq_music_lifecycle.py`、`test_text_speech_shutdown.py`、`test_auto_cache_lifecycle.py`、`test_home_side_panel_lifecycle.py`、`test_member_title_lifecycle.py`、`test_resource_schedulers.py`、`test_script_resource_scope.py` 均通过，且 `git diff --check` 通过。未执行 Gradle；完整 APK/R8 构建以本次提交对应 Actions 为准，手机功能与内存测量仍需独立验证。

用户于微信 8.0.78 / Android 16 反馈 QQ 点歌（卡片、语音、主开关关闭/重开、重复启停和不同触发方式）、语音播报和自动缓存功能测试成功。此处记录为用户功能验收反馈，不等同于线程/native 释放测量，也不扩展为其他微信版本兼容性结论；本次没有改变这三项已验收行为。
