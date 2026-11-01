# Hchat 全功能资源所有权清单（持续核查）

## 接续核查工作记录（基线 2f8fb6c，尚未最终交付）
- 逐功能当前对账见 `RESOURCE_LIFECYCLE_FEATURE_AUDIT.json`：109 个注册入口（包含 Java 的 WechatApiFeature）均已完成首轮入口/释放链复核，待核查入口为 0；仍有已记录的 openFindings 未闭环。首轮覆盖率为 100%，不等于修复或整体交付完成率。
- 本轮在已有语音转文字修复基础上，追加群改名、进退群监控、步数倍增及 Protobuf 的明确缺口修复。群提醒改为最多 256 项延迟待办、独占单线程，取消等待不线程中断在途宿主调用；分步顺序及间隔保持原链路，主线程语音入队与销毁同锁。
- Protobuf 共享发送被联系人编辑及脚本依赖，不能按抓包开关撤销；现一 Feature 一实例重试、失败精确回滚 Hook、保留解除失败句柄阻止重复安装、销毁精确 detach，抓包线程可空闲退出。快照 TTL 主动释放、缓存持有对象仍需进一步核查，不能宣称这项全部完成。
- 步数倍增改为每启用区间独占票据和 Hook，旧代闭门、迟到登记立即释放；保留8.0.78版本约束、1至10倍及溢出限幅。未执行实际微信运动上报。
- 新脚本查询 API + ScriptDexKitBridge 的完整便捷辅助链通过受保护惰性结果 JVM fixture：多锚点回退、Class/Member/String 结果出锁遍历不再访问受保护 Data；真实 DexKit ABI 另由既有测试验证。该测试不证明 Agent 全返回链或 native 释放量。
- 定向证据：生产 PendingNoticeTasks、群监控分步/销毁/语音迟到回调、步数生产 Feature 启停/失败/迟到Hook、Protobuf 安装回滚/解除失败/不打断在途测试通过。`test_monitor_sdk.py` 直接 Kotlin+Java/SDK/既有ABI 编译成功（`/tmp/pl-sdk-protobuf-01.exit`=0）；初始过期夹具/终端中断记录保留，不改写为首次全通过。
- 最新已完成冻结证据为 `/tmp/pl-resource-regression-sns-73/DONE.json`：73 项、0 失败、sourceStable=true。追加首页扩展/悬浮标题栏/共用scope异常隔离后，75 项冻结回归在 `/tmp/pl-resource-regression-header-home-75` 执行；以最终 DONE.json 为准。
- 仍需闭环：其余逐功能调用链、GroupBar/昵称宽度/艾特提醒等新发现候选、内部 DexKit 与 Agent 全返回链、缓存与队列持有边界、最终冻结回归、提交推送和对应 Actions/APK。已有 aac00e1 APK 不能作为本轮未提交源码的交付。


## 2026-11-01 首轮入口复核收尾（尚未最终交付）
- 最后六入口已写入审计 JSON：首页扩展、悬浮快捷菜单、悬浮标题栏、自定义底栏、群发助手、转发。入口复核 109/109，不代表 109 项已验收。
- 首页扩展：统一由启用区间所有者释放；逐项隔离订阅/偏好/Hook/Session/日志清理异常，释放读队列并请求私有读线程停止；头像等待队列上限32，提交拒绝时清除请求并回告失败。网络截止、联系人/日历队列及旧Session回调仍待闭环。
- 悬浮标题栏：独占 Installation 捕获本代 Runtime，before/after Hook句柄归scope；关闭门拒绝旧回调，视图恢复主线程执行并逐项隔离。旧代恢复与新代重装交错、原始overlay恢复及Capsule依赖仍待闭环。
- FeatureResourceScope：定向测试发现onError再次抛异常会中断后续释放，已隔离错误处理器异常；新增普通清理/迟到登记/错误处理器同时失败测试。
- 首页扩展与标题栏定向生产方法合成测试、扩大整文件SDK/已有ABI编译通过；75项冻结回归未结束前不得写为全部通过。未执行实机业务测试、本地Gradle、提交、推送或最终构建。

## 当前核查结论（2026-10-31，以本节为准）

以下历史阶段段落保留追溯，不能将其中旧的“未自动关闭”“尚未修复”或局部测试数量当作当前结论。设备验收仍未执行；不宣称所有功能都实现关闭即卸载，也不声称已证明全进程无泄漏。

| 所有者 / 范围 | 现有释放与兼容边界 | 验证范围 |
| --- | --- | --- |
| 宿主 DexKit | 内部查询/创建/关闭同一串行门；无 raw pin 时 30 秒闲置回收、按需重建。原始脚本 globals / bridge / holder / FeatureContext 接口固定该代到进程结束，不能因脚本退出擅自撤销 | 管理器与真实 ABI/JVM 定向测试；native 回收量需设备测量 |
| 分析报告与 AI 配置页 | 每报告独立读队列及 Handler；关闭清排队任务，正在读取不强制中断；AI 断开连接并拒绝迟到回调。请求替换关闭旧 AI；报告视图按 Dialog 弱引用隔离；显式截图发送不放进可取消的读队列 | 生产 PageTasks + AI 生命周期提取 JVM；整文件 SDK/ABI；窗口销毁及真实网络待实机 |
| 独立小型串行服务 | 分组菜单、天气、头像预加载保持各自顺序，空闲 30 秒退出；并非合并共享池。LinkedBlockingQueue 仍无容量上限 | IdleSerialExecutor 生产类测试；不宣称全面背压解决 |
| 自定义头像缓存 | 同步访问顺序缓存，64 项 / 8 MiB allocationByteCount；超预算图片只返回不缓存；淘汰不 recycle | 生产预算表达式 JVM；预算不含页面继续持有的 Bitmap |
| 首页扩展 | 主开关绑定私有 Runtime，主线程清 Header/媒体/监听/缓存/Hook；重开一次性查找当前可见列表，不新增滚动轮询。私有读队列清空并 graceful shutdown，头像下载受既有网络超时限制 | 可见性/批量联系人/样式 SDK 与 owner 测试；Header 位置、音乐和滚动待实机 |
| 表情艾特 | 独占安装票据随关闭撤销；关闭清待执行成员查询并停止 30 秒清理；空闲线程退出；旧代清理只删除旧选择，不误清新代。已进入宿主发送不线程中断 | 生产选择队列、原参数契约、旧代/新代交错 JVM |
| 朋友圈关键词 + 联系人过滤 | 两个独立偏好共同决定过滤 Runtime：任一开保留，共同关撤销私有 Hook/查询订阅/票据；主线程还原视图并尝试现有本地重载，使被 SQL 排除的行重新查询。联系人菜单入口另有进程/Feature 所有者 | 开关与清理源码约束、SDK 编译；旧版/新版列表重载待实机 |
| 朋友圈伪互动 / 本地伪转发 | 私有 Hook 登记到作用域，Feature destroy 释放；专属安装票据撤销、迟到登记即时释放。保留本地持久伪数据的安全防护 Hook，不把“菜单关闭”等同数据已恢复；无自动真实发帖/删除测试 | SDK/ABI、资源作用域测试；本地恢复结果和 native 行为待实机 |
| 自定义通知 + 屏蔽艾特所有人 | 独立订阅绑定各自开关；共享 Hook/动作 receiver 保持进程所有权。按 role+member 去重安装；仅完整必需角色成功才标安装成功，receiver 失败可独立重试。快捷回复 goAsync 的 finally.finish 不通过 queue.clear 破坏 | 生产安装方法提取：抛错/部分成功/receiver 重试/重复安装；epoch 测试 |
| 手动发送 / 原生群发 | 停用 stopPending 只阻止后续任务；在途沿既有完成/超时路径退出，用户主动 cancel 保留原语义 | 合成发送批次测试；未执行真实发送 |
| 自动回复/转发、通知、语音、支付、检测、Monet | 见下文已实施所有权与对应脚本；保留独立红包伪造/新群保护开关、检测进度、Monet 已开始用户 ZIP 写入不强中断 | 相关合成测试集中回归；不推断真实业务验收 |
| 共享基础设施及其余静态 Hook | HookRegistry/观察 API/菜单分发/发送按钮等存在多个消费者；部分低成本静态 Hook 和设置监听明确保留进程或 Feature 生命周期，不做全局 unhookAll 式单功能关闭 | 源码边界核查，非所有功能统一“零资源关闭”证明 |

### 验证与交付约束
- 禁止本地 Gradle；完整 Android/R8 构建只由最终提交 GitHub Actions 验证。
- 集中回归记录见 `RESOURCE_LIFECYCLE_VERIFICATION.md`；测试使用桩或提取的生产方法，不访问真实消息、朋友圈、红包、转账和好友删除操作。
- 唯一候选 APK 必须来自最终提交构建，提交/Actions run/版本/APK SHA-256 写入交付记录；旧提交构建不能替代。
- 一次性手机验收见 `RESOURCE_LIFECYCLE_DEVICE_CHECKLIST.md`。实测前不得宣称内存下降、native 释放量、无掉帧或业务兼容通过。

## 历史阶段记录（非当前待办列表）

## 2026-10-31 朋友圈、语音、支付接续（仍非最终交付）
- 自动点赞/评论/转发及刷新按启用区间持有运行时；关闭取消待办、拒绝旧回调，不用线程中断打断已开始的宿主操作。
- 文本转语音拥有私有菜单 Hook、发送按钮订阅、安装票据；关闭撤销、取消合成/网络并安全排空。旧播放器回调受活动门保护，过期文件清理不再无条件删除新旧代正在使用的文件。
- 自动收款延迟操作及查询回调用代际身份隔离；关闭删除该代 Handler 任务，释放回复、通知音与 TTS。已进入宿主 API 的操作不承诺撤回。
- 红包私有检测/界面/静默/伪造 Hook 精确释放；重试、轮询、回复绑定私有任务作用域；不清理共享任务池或全局 HookRegistry。支付功能独占网络发包器单独销毁，共享 API 的发包器保持原生命周期。
- 红包伪造及新群保护的独立开关仍可单独启用；相关开关变化会重建该私有运行时，防止关闭再开启后旧延迟任务复活。文本语音两个开关采用同样代际切换。
- 验证：本批 Java/Kotlin 整文件 Android SDK + 既有 ABI 定向编译通过；PaymentResourceScope 生产类以 Handler/Hook 桩运行，覆盖取消、替换、逆序异常清理、迟到登记、在途不中断与重开；朋友圈生产线程池提取测试通过。以上不是完整 Android/R8 构建或实机验收。
- 集中回归正在执行；全功能剩余私有 Hook、转发/群发、僵尸粉检测及 UI 生命周期仍在逐项核查，不声明全部完成。


## 2026-10-31 接续批次：以本节覆盖下文历史状态
- DexFinder / SettingsDexFinder 内部入口改为 managed 工厂；旧构造器兼容保留。内部查询只在线程局部保护期间持有 bridge，并在 finally 恢复嵌套线程状态。
- FeatureContext、内部 holder、收藏媒体 API、朋友圈 API、Agent 宿主 Binding 不再持有永久原始宿主 bridge。查询外只保留反射对象、字符串或 JSON；下载等待、文件物化和 Agent APK sourceEntry 索引在 native 查询外。
- ModuleEntry 移除原始 bridge Map；早期安装局部变量只在共享门内同步消费。入口显式接入 30 秒一次性闲置定时，查询/关闭/创建共用串行门，无滚动路径轮询。创建失败可重试，关闭失败隔离旧对象，日志报告代际及异常。
- 原始脚本 globals 仍按旧约定注入：创建解释器或调用 bridge()/holder()/FeatureContext 原始接口即按进程固定该代际。无法证明脚本任意线程/全局引用已消失，因此不在脚本卸载时冒险释放 raw pin；未创建旧解释器、仅使用新查询 API 的运行时可回收。
- 20 项集中回归：17 项首次通过，3 项因查询门签名/日志依赖夹具过期失败，已修正并单独重跑通过。证据分别为整文件 SDK/真实 ABI 编译、表达式 ABI 编译和 JVM 行为模型，不是实机验证；完整 Android/R8 编译以对应提交 Actions 为准。
- **尚未完成**：全功能热启停逐项闭环、自动回复 object 线程/计时器/音频网络链、Monet 进行中导出所有权等仍需实施；本批不是最终版本或最终 APK。

## 历史审计记录（部分“尚未回收”等描述已由上节更新）

核查基线：`dcf9448`，工作区干净；按当前功能源码、`BaseFeature` / `FeatureResourceScope`、`FeatureManager`、实际资源创建和清理入口核查。该清单覆盖功能私有线程/Handler/订阅/Hook/延迟任务/缓存及共用资源归属；不是 Android 运行时堆快照或所有微信版本的设备验证。

## 已完整接入或已有明确清理所有者

- **共享基础设施**：`BaseFeature` 的 `FeatureResourceScope` 逆序释放 Hook 以外登记资源、订阅、设置 Provider；关闭后迟到登记即时清理；安装失败传播回原清理路径。`FeatureManager.destroyAll` 先销毁各 Feature，再调用全局 `HookRegistry.unhookAll()`，仅适用于全局退出，不可用于单功能开关。
- **实际开关热启停**：聊天工具栏、首页侧边栏、自动缓存、语音播报、QQ 点歌创建/销毁独占运行时。QQ 点歌 `shared:send_button` 与 QuickMarkRead 等共享 Hook 保持共享所有权，不随单个使用者退出卸载。
- **重要运行时释放链已有 fixture 覆盖**：背景、输入框提示、群员头衔、自动缓存、QQ 点歌、首页侧边栏、语音播报、消息发送/脚本作用域等，具体历史证据见 `docs/RESOURCE_LIFECYCLE_OPTIMIZATION.md` 各阶段段落。执行器关闭策略区分排队任务取消与已开始任务，不以中断替代业务取消。
- **配置驱动调度器**：朋友圈自动评论/点赞/转发/自动刷新/关键词拦截等独立 Runtime 持有设置监听或计划线程，并提供自身 destroy/取消路径；由 Feature 销毁调用，仍须按具体实现核验异步入队竞态。
- **数据库/联系人观察**：已通过 BaseFeature subscribe 或 FeatureResourceScope registrar 登记的订阅，在功能销毁/旧代回调路径释放；共享观察 API、共享 DexKit Hook 不属于消费者私有资源。

## 本次核查发现并处理

- **关键词通知**（`keywordnotify/KeywordNotificationRuntime.kt`）：原进程级 object 初始化时即创建 `Hchat-KeywordNotify` 单线程执行器，长期常驻；Feature 只释放消息订阅，没有执行器关闭或图片缓存清理。其 `shouldAllowBlockedMessageIntoDatabase` 被独立的消息屏蔽 Hook 同步调用，不能把它整体销毁或转移到消息同步入口。
- 修复后，执行器按 Feature 安装代际创建，Feature ResourceScope 先登记 `stop()` 再订阅；销毁时关闭门、清空主线程待执行通知/Toast/铃声回调、头像缓存及头像路径状态并 `shutdownNow()` 私有工作线程。消息事件处理仍异步执行，关闭后排队任务检查门退出。同步消息屏蔽判定继续使用原 API，不受通知 Feature 生命周期关闭影响。通知/Toast/手动铃声的主线程投递也在执行时检查当前门。
- 新增 `scripts/test_keyword_notification_lifecycle.py`：验证生产源码包含逐 Feature 执行器、门控和主线程投递约束；编译/运行 JVM lifecycle model 检验安装前不工作、关闭清理待办和缓存、迟到投递拒绝及重复启停幂等。该 fixture 不编译 Android 通知实现，也不能替代真实通知与手机验证。

## 后续核查：朋友圈发布通知
- `MomentsPostNotificationFeature` 的独占 Runtime 原只在正常 `onFeatureDestroy` 中释放；`start()` 中途失败时，线程池、已注册的设置监听或订阅可能遗留。原 `destroy()` 未撤销主线程 Toast 回调，也未清理头像 Bitmap 缓存、头像目录状态与进程内去重集合。
- 现于启动前登记 Runtime 资源，失败由 Feature 作用域执行清理；启动/销毁串行化，关闭门阻止旧观察回调、排队任务、Toast 和头像缓存回填，清空待执行回调和缓存，异常取消订阅不跳过后续释放。已进入系统通知服务的 `notify()` 无法撤回；这项仅覆盖 Feature 销毁，不宣称朋友圈通知设置主开关已实现完整热启停。
- `scripts/test_moments_post_notification_lifecycle.py` 覆盖生产源码入口约束和独立关闭模型；仍需 Actions 完整编译、实机朋友圈发布通知/Toast 与资源测量。共享 `WeChatSnsPostObserver` 不随单个消费者退出销毁。

## 按资源类型核查的状态与后续优先级

| 资源类别 | 源码现状 / 所有权边界 | 状态 |
| --- | --- | --- |
| Feature 订阅、设置 Provider | 由 BaseFeature 资源代绑定；异步代码须捕获原代 registrar，不能回流新代 | 基础框架已接入；逐功能仍需审计绕开 BaseFeature 登记的订阅 |
| 功能私有 Hook | HookRegistry 是全局句柄索引，个别功能已使用独占 Installation 或自己保存/销毁 Hooker；不能对单功能调用 `unhookAll()` | 部分接入；继续清查非 feature scope Hook 与旧代回调 |
| 独占线程/执行器 | 背景、点歌、自动缓存、语音播报等已提供明确 shutdown；关键词通知本次修复 | 部分接入；尚有具体功能需核实所有创建点和迟到任务 |
| Handler / 延迟任务 | 一些 Runtime destroy 会 remove callback 或身份校验；不可只看发起点，要沿持有的 Runnable / Activity 链核查 | 部分接入；逐功能追踪待执行闭包是否持有 Activity、View、FeatureContext |
| 缓存 | 若为受限 LRU/显式 destroy 已有治理；运行配置/共享索引与运行缓存须区分 | 部分有界；仍需核对进程级 Bitmap、反射 Class/Method 和 Map 的预算及持有者 |
| 共享 Hook/API/DexKit | 发送按钮、QuickMarkRead、观察 API、WeChatSnsPostObserver、WeContactHeaderApi 等存在多消费者；宿主 DexKit 原始对象/脚本 API 仍须保活，尚无安全闲置 close | 必须保留共享所有权；宿主 DexKit 不启用自动关闭 |
| 群员头衔显示 | 渲染器 destroy 已清理自身 Hook/缓存/弱引用和装饰；使用静态弱键只保留当前可见行的 room/member 绑定，重开时只恢复仍 attached、shown 且属于当前群聊的行，不扫描 Adapter | 源码与 JVM 定向测试已覆盖；手机昵称布局与真实关闭重开仍待验收 |
| 手机 TTS、音频/媒体、通知、数据库与真实发送 | JVM fixture 只能验证局部状态及调度，不包含微信 native Hook、真实音频服务/网络和系统对象 | 必须设备验收；资源回收需实测，当前没有 MiB 或 native 释放量证据 |

## 本轮源码审计补充
- 群员头衔关闭重开：已完成可见行恢复，恢复范围严格限制为静态弱键记录中的 attached/shown 当前群聊行；未扫描 Adapter，未强刷列表。
- 聊天分组运行时：审计发现其独占执行器、偏好监听、三类数据库订阅和 Hook 句柄原先未完整归属销毁路径；本轮已补齐运行时订阅保存/取消、偏好监听取消、主线程回调移除、独占 Hook 解除、执行器 shutdown、缓存/弱引用索引清空，并允许关闭后下一轮重建执行器。该项需对应 Actions 编译确认，不能由 fixture 通过替代。
- 宿主共享 DexKit：已新增 `DexKitBridgeManager` 记录当前代际、活动查询和租约数量，并由 `ModuleEntry` 发布当前 bridge；`DexBridgeHolder` 持有显式 raw-compatibility 租约，使旧脚本/未迁移入口继续保活。管理器提供受控 `closeIfIdle` / `closeRetiredIfIdle`，但当前没有启用自动回收，仍不得宣称宿主 native close 已完成。

## 修复后的限制与下一步
- 自定义底栏移除捕获 FeatureContext 的原始 bridge provider，改为直接持有管理器，destroy 清空该引用；唯一 Dex 查询与反射转换在活动查询区间完成。早期无 bridge 的缓存/反射路径保留；此批源码所有权检查通过，整文件编译待 Actions。
- 语音转发与原生群发各 3 处直接查询进入管理器保护，群发 `invokes` 和表情 `usingFields` 的同步解析保留在区间内。此批只有源码边界检查及既有消费者契约，完整编译待 Actions；收藏语音共享解析器仍接收原始 bridge，下载等待不应整体放入查询锁，后续需分离其定位与业务执行。
- 朋友圈本地转发追加 7 个定位区间，累计 78 个内联表达式通过真实 ABI 检查。所有改动限于定位查询，未执行转发、删除或发布操作；完整页面与数据库行为仍待统一设备验收。
- Hchat 扩展功能全部 7 处直接查询纳入代际保护，转换辅助函数只在查询内消费 Data；累计 71 个内联表达式通过真实 ABI 编译，类加载、包名限制和缓存策略保持原有语义。
- 好友检测构造器及删除方法回退、朋友圈关键词与底部详情追加 5 个区间，累计 64 个内联表达式通过 ABI 编译；仅改定位，不执行好友检测、转账请求或删除操作。旧 DexFinder 服务入口仍保留兼容所有权。
- 安全消息/反安全消息的精确查询与回退同代消费，左滑引用追加 3 个区间；累计 59 个内联表达式通过真实 ABI 检查。消息插入仍使用已有 DexFinder 兼容路径，本批不改变发送及 Hook 生命周期。
- 转发数量限制两处查询进入代际保护，累计 54 个内联表达式通过 ABI 编译。通话铃声与媒体限制的 `invokes` 消费由管理器同步调用私有 fresh 解析函数，缓存命中在区间外；保留主/分散锚点及旧版回退。通话 helper 边界检查通过，整文件 Android 编译待 Actions，不能据此宣称真实铃声或通话验证通过。
- 扫描相机、背景、选中消息、转账账户和聊天实况图片追加 5 个区间；实况图调用关系查询及提前返回均在管理器回调内，累计 52 个内联表达式通过真实 ABI 检查，消费者串行门契约仍通过。
- 聊天时间、群员头衔、气泡、@ 高亮、拍一拍屏蔽、余额显示和语音时长追加 7 个定位区间，累计 47 个内联查询表达式通过真实 ABI 检查。群员头衔可见行恢复、列表绑定和按键逻辑未改动。
- 首页文字颜色追加 5 个查询区间，类命中展开与懒序列在保护内消费，累计 40 个内联生产查询表达式通过 ABI 检查。统计为本轮新增保护区间，非全项目完成率。
- 实名尾字、视频号下载、消息文字颜色、动态徽标及聊天分组继续迁移 8 个区间；序列均在保护区间终结，缓存和界面绑定在查询外。累计 35 个生产查询表达式通过真实 ABI 编译，不代表这些功能全部运行时资源已释放。
- 朋友圈防撤回、原图上传追加 3 个即时反射转换查询区间，累计 27 个内联区间通过真实 ABI 编译；未改变上传调用和页面 Hook，仍须最终统一设备验收。
- Monet 生成器绑定改为管理器及弱引用所有者，解除独立运行时对原始 bridge 的直接持有；证据收集期间登记活动查询，其他 ZIP/资源处理不持有 bridge。detach 按 FeatureContext 身份匹配，避免旧所有者卸载新绑定。导出任务取消及手机导出验收尚不属于该查询迁移的完成证据。
- 自定义通知两处查询完成代际保护，签名筛选与去重在保护区间内结束，既有通知 Hook 所有权未改变；累计 24 个内联生产查询表达式通过 ABI 检查，另有转发记录私有解析区间通过源码边界检查。
- 转发记录类型标签的缓存未命中解析整体进入管理器，`getMethodData`、`usingFields` 与字段反射转换均由私有 `locateFresh` 同步消费；返回结构只含 `Method`、`Class` 和 `Field`。源码边界已检查，整文件编译仍以 Actions 为准。
- 防撤回补齐 3 个查询区间，`RevokeEntry` 从保存 `MethodData` 改为保存 `List<Method>` 调用关系；读取 `invokes` 和方法转换在同一活动查询内完成，缓存命中仍保留旧版 storage 定位回退。累计 22 个区间通过查询表达式 ABI 检查；消息撤回及媒体保留仍需真实微信验收。
- 既有共享串行门的会话菜单、视频号详情请求和转账前实名查询继续补入代际保护；懒序列在区间内终结，实名查询只输出类名字符串。累计新增迁移 19 个查询区间、15 个文件，已通过生产查询表达式 ABI 检查；原串行门契约测试仍通过。
- 后续内部定位迁移：小程序基础库兼容、隐藏聊天头像、自定义好友头像、退群链接高亮、自动缓存原生媒体、表情 @、输入状态屏蔽、透明头像、朋友圈头像菜单、快捷已读、圆角头像、删除键清引用共 16 个查询区间已迁入管理器短查询保护。只在查询区间持有 native-backed Data，外部返回反射对象或字符串，不新增持久租约。此项不宣称这些功能的 Hook/执行器全部热启停完成。
- `test_internal_dex_scopes.py` 提取上述生产查询表达式，使用真实 DexKit ABI 和生产 Java 管理器编译并约束脱离 native 的返回类型；Android 外围使用夹具，不能替代完整构建或实机。`audit_dex_entries.py` 提供可重跑候选清单，文件含查询门不等于所有调用均已覆盖，仍需沿调用链分类。


- 本次只修复已证实的关键词通知运行时生命周期缺口；未把所有功能宣称为热启停完备。
- 执行器任务仍可能在 `shutdownNow()` 前已经开始进入微信 API/系统服务；停止不保证撤销该操作。关闭门阻止尚未开始的后台/主线程工作。
- 应继续逐个查找绕开 `FeatureResourceScope` 的 Hook 句柄、直接注册的消息/联系人观察、线程池/Timer/Handler、长期缓存；对明确归某一功能的普通 Runtime 优先迁移并配生产入口回归。宿主共享 DexKit 仍不能自动关闭，设备测量另行推进。
- 完整构建由本次推送对应 GitHub Actions 验证；未运行本地 Gradle。手机功能、线程终止时间、Java/native 内存变化需分别记录，fixture 通过不可替代它们。

## 自动回复运行时接续审计
- 自动回复和自动通过好友是独立设置：任一启用维持运行时，任一开关变化销毁旧代并按当前设置重建，避免另一开关仍开时旧排队任务继续执行。设置监听/消息订阅/好友观察归 Feature scope，安装失败回收。
- 运行代拥有工作线程、定时线程、网络客户端、WebSocket/MCP、TTS 集合和缓存；停止关闭入队门、清除排队任务，已开始宿主发送使用 graceful shutdown，不中断也不重试。旧回调按原代拒绝，旧任务缓存不能写入新代。
- MCP 闲置与重连由可取消定时任务替代睡眠线程；停止释放网络与 TTS，异常清理不跳过后续资源。设置页面设备绑定/连通性测试使用独立惰性 utilityClient，不被业务开关中断。
- `test_auto_reply_lifecycle.py` 提取生产代际实现做 JVM 行为测试：反复启停、排队取消、已开始任务不被中断、旧回调拒绝、缓存隔离、线程退出。整文件手工 Kotlin 编译使用 Android SDK、实际依赖和已有缓存的外围类；完整当前项目构建仍须 Actions，未运行本地 Gradle。

## 通知热启停补齐
- 关键词通知、朋友圈发布通知接入 PreferenceRuntimeOwner：默认关闭不创建工作运行时；开关关闭释放私有线程、观察订阅、回调和缓存，重新启用创建新运行代。监听归 Feature scope；安装失败和迟到设置回调均有清理门控。
- 关键词通知同步数据库过滤 API 保持原有独立语义。朋友圈重新启用重置启用时间与去重状态，避免补发关闭期间通知。共享 SNS 原生观察 Hook 不随单消费者销毁。
- 生产 owner JVM 行为、关键词生产入口夹具和朋友圈关闭模型通过；通知整文件使用 SDK 和真实依赖编译通过。实际系统通知仍待统一实机测试。

## 自动转发热启停补齐
- 主开关按启用区间创建运行时和消息/撤回订阅；关闭后拒绝迟到回调和计划任务，重新开启使用新实例。
- 关闭不再 shutdownNow 中断已开始的宿主发送；停止入队、清除延迟队列后，将缓存清理排到同一工作线程，避免关闭线程与正在发送的任务并发改写非线程安全集合。
- 生产执行器/停止/调度代码提取行为测试通过，功能目录整文件 SDK/依赖编译通过；真实转发和撤回留待统一实机验收。

## Monet 导出任务所有权
- 导出会话独立保存可清空的界面回调，解绑/替换所有者取消旧回调并清除 Handler token；阶段边界和 Dex 查询前校验绑定身份。
- 已开始写入用户文档的 ZIP 复制允许完成，避免中断造成损坏；未进入写入阶段的旧任务在下一检查点停止，临时目录在 finally 清理。线程启动失败重置运行状态。
- 生产回调会话 JVM 测试和整文件 SDK 编译通过；完整 ZIP 内容及文档提供器行为仍需用户验收。

## 安装失败所有权修复
- 11 个功能由“also 安装成功后赋值”改为先将运行时保存至 Feature，再调用可失败安装。BaseFeature 捕获失败时，既有 destroy 链能看到部分初始化实例；不改变原开关或 Hook 定位语义。
- 涉及透明头像、圆角头像、表情艾特、隐藏艾特、聊天分析、我页入口、首页扩展、朋友圈自动点赞/评论/转发、自定义头像。首次顺序检查发现透明/圆角头像原 destroy 仅清空引用，未通过；接续补齐私有 Hook 释放后，顺序检查及 BaseFeature 失败清理 JVM 回归重跑通过。

## 头像私有 Hook 所有权
- 透明、圆角、自定义头像的 Hook 句柄登记到私有 FeatureResourceScope；销毁解除自身 Hook，不调用全局 unhookAll。迟到登记即时释放，旧回调检查 destroyed；自定义菜单扩展注册同时登记反注册。
- 三项主开关拥有运行时、设置监听、Dex 安装重试票据；关闭取消重试和资源，重开创建新实例。圆角 Bitmap 弱缓存清空，不 recycle 仍可能被通知使用的 Bitmap。
- 本次验证包括整文件 SDK 编译、生产资源作用域并发/失败测试；当前屏幕已有头像是否立即刷新仍需统一实机观察，不能由 Hook 释放测试推断。
本轮续修：隐藏长按菜单按启用区间管理私有Hook/票据，合成启停/失败重试/迟到句柄释放通过；消息菜单扩展按区间持有Runtime与票据，行为测试待补。分组横栏安装销毁同锁并撤销旧票据，worker关闭入队捕获拒绝；昵称宽度增加代际门控。上述改动直接SDK/既有ABI编译通过；横栏安装测试与昵称现有测试结果待核实，完整代际测试仍待补。前次54项通过记录不覆盖这些新增改动；未提交/未推送/未构建最终APK。

补核结果：横栏安装竞态测试与昵称宽度现有测试已通过；消息菜单扩展启停测试已通过（停用启动、关闭重开、旧安装闭包隔离、同一Feature重装、监听/票据/Runtime释放）。回归入口现58项，尚未执行新冻结全集；95项待核查，艾特提醒仍待修复，昵称代际测试及菜单Runtime内部清理仍未闭环。

### 后续核查进行中

- 艾特提醒：补充每启用区间worker、安装票据、数据库订阅、Renderer Hook所有权；SDK/既有ABI编译和入口启停、排序、span测试通过。持久化_seen预算与Runtime并发行为仍未闭环。
- 滑动手势：发现专用语音线程和Hook未释放，已增加Adapter销毁与两个独占票据、旧回调门控；定向SDK编译通过，行为验证待补。
- 分组横栏和消息菜单扩展：逐项释放，单个unhook失败不阻断其他释放。
- 109入口已进行资源关键词扫描，该扫描只用于定位，不能替代逐项源码及依赖核查。待核查状态保留。

- 冻结验证：/tmp/pl-resource-regression-frozen-61/DONE.json 记录 total=61, failed=0, sourceStable=true。此冻结记录早于随后群聊分析和实名尾字修改，不覆盖这两组后续修改。
- 实名尾字调度器：新增test_realtail_scheduler_lifecycle.py通过；查询Hook/预取器/调度器均已补销毁入口，完整依赖编译和并发竞态仍待确认。


## 2026-11-01 接续核实：75 项冻结回归
- HEAD：2f8fb6cd9e5bb33cc0e0e981bed89030fbc74d98。
- /tmp/pl-resource-regression-header-home-75b/DONE.json：total=75，failed=0，sourceStable=true，resumed=true。
- summary.json 中 75 个唯一测试与执行清单完全一致；接续核查时所有 source-sha256.json 文件哈希一致。
- 保留 75 首轮 ownership 旧断言失败、75b 等待超时中断、22 项保留及 53 项补跑记录，详见 RESUMED.json；不改写为首次全通过。
- 这是当前修改前快照的合成回归和直接 SDK/ABI 编译证据，不是 109 项全部闭环、Android/R8 全构建或实机验收。


## 2026-11-01 标题栏恢复链补修（78项冻结进行中）
- Capsule.restore/destroy逐项隔离观察器、视觉、视图、worker、Handler、cache及日志异常；清理点击闭包和头像引用，销毁幂等。
- 私有头像池改为单worker、16项等待队列、15秒核心线程回收；拒绝时复位loading。下载增加8秒检查式预算，阻塞读仍受4秒readTimeout约束；不声称严格8秒终止。
- overlay先读取并记录isInOverlayMode，后set；禁用/销毁恢复原值。背景各层反射claim/restore隔离。
- 旧代主线程恢复登记并在新apply前排空；普通布局用volatile快速门控。attach/layout闭包增加closed门。
- 实际发现共享卡片遗漏backgroundTintList/backgroundTintMode恢复；增加原值归属和逐属性异常隔离。内容卡片恢复translation时补背景恢复，headerZone销毁不再错误依赖已清空headers映射。
- 新增test_header_capsule_shutdown、test_header_visual_recovery、test_header_card_restore，均提取生产方法；本轮78项冻结中前三项已退出0。Hook定向退出0。此前直接SDK/ABI编译通过，但最终以78项快照内test_monitor_sdk为准。
- visual_recovery首次测试桩遗漏TAG编译失败，保留/tmp/pl-header-visual-verify/test_header_visual_recovery.log；修正桩后retry通过，不能记录首次全过。
- 当前冻结目录/tmp/pl-resource-regression-header-recovery-78；日志/tmp/pl-header-recovery-78.log。冻结未结束前不修改app/src或scripts。
- 仍未验收：真实Android UI交错、网络/DNS截止、拒绝后的重试时序以及全部109入口依赖闭环。未执行业务操作、本地Gradle、提交推送或Actions。

2026-11-01：78项冻结结束，total=78，failed=0，sourceStable=true；78个唯一测试与清单一致，结束后所有源码哈希一致。含最新标题栏/共享卡片直接SDK编译。证据：/tmp/pl-resource-regression-header-recovery-78。之后群发修改另行验证。

## 2026-11-01 群发与设置入口续修（不是全项目完成）
- 修复群发测试生成语法及提取锚点；首次失败保留在/tmp/pl-selected-senders-recovery/targeted.initial-failure.log；定向retry和补锁后测试通过。
- 群发新增生产admission/shutdown合成、Feature.detach/dialog异常注入合成；同测主线程/非主线程、回调及logger失败、拒绝回滚、晚到资源与幂等。最新六项定向confirm.exit全部0，未调用真实宿主发送。
- 联系人预热不再另起无归属Thread，进入Feature自有队列4的worker；共享TTL缓存为MessageForward消费者保留，不全局清理。
- 扩大直接SDK/ABI编译发现并修复两个shutdown重复@Synchronized；通过记录/tmp/pl-selected-senders-recovery/sdk.retry.exit=0。
- 设置Feature改为BaseFeature持有3个Provider；SettingsInjector全路径Hook局部持有、私有Handler、关闭门及弱引用原listener/tag恢复；生产Java提取合成通过。
- 设置定向测试桩首次遗漏implements AutoCloseable失败，保留initial-failure日志；扩大编译先暴露旧ABI缺managed，再补当前SettingsDexFinder/DexKitBridgeManager源到javac，最终/tmp/pl-settings-owner/sdk.final.exit=0。
- 设置恢复投递前清空自身绑定图，安装/标题Hook/adapter增量取得与close互斥；不得以合成关闭通过等同原生设置页面移除验收。
- 81项新冻结目录/tmp/pl-resource-regression-selected-settings-81，日志/tmp/pl-selected-settings-81.log；进行中，冻结期间不改app/src和scripts。旧78项快照不覆盖本次修改。
- 尚未完成109入口：群发队列预算、退出投递拒绝、官方准备与回调交错；设置原生页面；公共API进程所有者/观察订阅/全局HookRegistry异常；脚本全局可重建关闭以及审计中的其他openFindings。未执行业务、Gradle、提交推送或Actions。
