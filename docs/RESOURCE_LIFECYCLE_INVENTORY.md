# Hchat 全功能资源所有权清单（dev 当前核查）

核查基线：`e94c8e0`，工作区干净；按当前功能源码、`BaseFeature` / `FeatureResourceScope`、`FeatureManager`、实际资源创建和清理入口核查。该清单覆盖功能私有线程/Handler/订阅/Hook/延迟任务/缓存及共用资源归属；不是 Android 运行时堆快照或所有微信版本的设备验证。

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

## 按资源类型核查的状态与后续优先级

| 资源类别 | 源码现状 / 所有权边界 | 状态 |
| --- | --- | --- |
| Feature 订阅、设置 Provider | 由 BaseFeature 资源代绑定；异步代码须捕获原代 registrar，不能回流新代 | 基础框架已接入；逐功能仍需审计绕开 BaseFeature 登记的订阅 |
| 功能私有 Hook | HookRegistry 是全局句柄索引，个别功能已使用独占 Installation 或自己保存/销毁 Hooker；不能对单功能调用 `unhookAll()` | 部分接入；继续清查非 feature scope Hook 与旧代回调 |
| 独占线程/执行器 | 背景、点歌、自动缓存、语音播报等已提供明确 shutdown；关键词通知本次修复 | 部分接入；尚有具体功能需核实所有创建点和迟到任务 |
| Handler / 延迟任务 | 一些 Runtime destroy 会 remove callback 或身份校验；不可只看发起点，要沿持有的 Runnable / Activity 链核查 | 部分接入；逐功能追踪待执行闭包是否持有 Activity、View、FeatureContext |
| 缓存 | 若为受限 LRU/显式 destroy 已有治理；运行配置/共享索引与运行缓存须区分 | 部分有界；仍需核对进程级 Bitmap、反射 Class/Method 和 Map 的预算及持有者 |
| 共享 Hook/API/DexKit | 发送按钮、QuickMarkRead、观察 API、WeChatSnsPostObserver、WeContactHeaderApi 等存在多消费者；宿主 DexKit 原始对象/脚本 API 仍须保活，尚无安全闲置 close | 必须保留共享所有权；宿主 DexKit 不启用自动关闭 |
| 群员头衔显示 | 渲染器 destroy 已清理自身 Hook/缓存/弱引用和装饰；关闭重开需恢复当前可见行，不能无证据扫描/强刷 Adapter | 资源销毁有覆盖；可见行恢复仍未完成 |
| 手机 TTS、音频/媒体、通知、数据库与真实发送 | JVM fixture 只能验证局部状态及调度，不包含微信 native Hook、真实音频服务/网络和系统对象 | 必须设备验收；资源回收需实测，当前没有 MiB 或 native 释放量证据 |

## 修复后的限制与下一步

- 本次只修复已证实的关键词通知运行时生命周期缺口；未把所有功能宣称为热启停完备。
- 执行器任务仍可能在 `shutdownNow()` 前已经开始进入微信 API/系统服务；停止不保证撤销该操作。关闭门阻止尚未开始的后台/主线程工作。
- 应继续逐个查找绕开 `FeatureResourceScope` 的 Hook 句柄、直接注册的消息/联系人观察、线程池/Timer/Handler、长期缓存；对明确归某一功能的普通 Runtime 优先迁移并配生产入口回归。共享 DexKit、群员可见行恢复及设备测量另行推进。
- 完整构建由本次推送对应 GitHub Actions 验证；未运行本地 Gradle。手机功能、线程终止时间、Java/native 内存变化需分别记录，fixture 通过不可替代它们。
