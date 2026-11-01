# 资源生命周期验证记录（2026-10-31）

本记录随最终候选源码提交。测试执行期间与复核时源码 SHA-256 均一致；原始清单保存在 /tmp/pl-frozen-regression/source-sha256.json。未运行本地 Gradle，未执行真实发送、朋友圈发布、红包、转账或好友检测/删除。

## 集中回归
- 共 46 项；首次并行 44 项通过，2 项 Kotlin 编译达到测试内 45 秒超时。没有断言失败。
- 两项在相同源码上串行重跑，退出码均为 0；最终 46 项均有通过证据。保留首次失败日志，不将首次结果改写为全通过。
- 推荐命令：python3 scripts/run_resource_regression.py --output /tmp/pl-resource-check-new --jobs 1
- 依赖已有 JDK、/tmp/pl-kotlin-libs、Android SDK 36 及各脚本使用的真实 ABI 缓存；不是可脱离这些依赖的完整 Android 构建。
- 整文件 SDK/既有 ABI 定向编译退出码 0（/tmp/pl-final-sdk2.log）；它不是完整 Android/R8 构建。

| 测试 | 首次退出码 | 最终证据 |
| --- | ---: | --- |
| test_dex_bridge_manager | 0 | 0 |
| test_dex_query_api | 0 | 0 |
| test_dex_query_consumers | 0 | 0 |
| test_dex_finder_gates | 0 | 0 |
| test_early_dex_gates | 0 | 0 |
| test_agent_external_idle | 0 | 0 |
| test_internal_dex_scopes | 0 | 0 |
| test_fake_location_dex_scope | 0 | 0 |
| test_splash_ad_dex_scope | 0 | 0 |
| test_favorite_voice_preparation | 0 | 0 |
| test_feature_resource_scope | 0 | 0 |
| test_member_title_lifecycle | 0 | 0 |
| test_auto_cache_lifecycle | 0 | 0 |
| test_keyword_notification_lifecycle | 0 | 0 |
| test_moments_post_notification_lifecycle | 0 | 0 |
| test_home_side_panel_lifecycle | 1 | 串行重跑 0；首次编译超时 |
| test_qq_music_lifecycle | 1 | 串行重跑 0；首次编译超时 |
| test_text_speech_shutdown | 0 | 0 |
| test_resource_schedulers | 0 | 0 |
| test_script_resource_scope | 0 | 0 |
| test_auto_reply_lifecycle | 0 | 0 |
| test_preference_runtime_owner | 0 | 0 |
| test_auto_forward_lifecycle | 0 | 0 |
| test_monet_export_lifecycle | 0 | 0 |
| test_runtime_install_ownership | 0 | 0 |
| test_moments_textvoice_hotstop | 0 | 0 |
| test_payment_resource_scope | 0 | 0 |
| test_background_resource_lifecycle | 0 | 0 |
| test_resource_cache_budgets | 0 | 0 |
| test_memory_pressure_callbacks | 0 | 0 |
| test_scheduled_send_state | 0 | 0 |
| test_background_state_ownership | 0 | 0 |
| test_input_hint_lifecycle | 0 | 0 |
| test_manual_send_shutdown | 0 | 0 |
| test_zombie_shutdown | 0 | 0 |
| test_pending_send_stop | 0 | 0 |
| test_custom_notification_epoch | 0 | 0 |
| test_idle_serial_avatar_budget | 0 | 0 |
| test_analysis_page_lifecycle | 0 | 0 |
| test_remaining_runtime_ownership | 0 | 0 |
| test_emoji_at_choice_queue | 0 | 0 |
| test_emoji_at_native_passthrough | 0 | 0 |
| test_home_contacts_batch | 0 | 0 |
| test_home_extension_idle | 0 | 0 |
| test_home_music_style_two_sdk | 0 | 0 |
| test_moments_filter_owner | 0 | 0 |

## 证据边界
- PageTasks/IdleSerialExecutor/资源作用域等生产类在 JVM 桩运行；AI、共享通知安装、双开关过滤等测试提取生产方法并替换 Android/网络/宿主依赖。
- 静态入口检查不等于整功能行为证明；测试覆盖详见各脚本头部。
- 最终 SHA 对应 Actions 成功和 APK 哈希在仓库外的交付清单记录，避免把待构建结果提前写成成功。
- 设备内存、native 回收量、滚动帧时长、网络与真实发送均未验收；见 RESOURCE_LIFECYCLE_DEVICE_CHECKLIST.md。


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


## 2026-11-01 接续与阶段推送

用户授权在全项闭环前推送一个阶段版本并继续修复，替代此前禁止阶段提交/推送的约束；此版本不是109项完成声明。

原81项冻结运行中断在78项，无DONE、无退出码；原目录与日志保留。续跑剩余3项至独立目录 `/tmp/pl-resource-regression-selected-settings-81-resume`，合并81个唯一测试，failed=0、sourceStable=true、listMatch=true、resumed=true。冻结HEAD为 `2f8fb6cd9e5bb33cc0e0e981bed89030fbc74d98`，全部已记录源码SHA256与续跑结束源码一致。首次读取清单使用ast.literal_eval遇到Call表达式失败，改为runpy读取清单后核实；不属于生产测试失败。

此阶段仅有直接SDK/ABI编译及JVM合成测试证据；公共API、脚本全局运行时、群发容量与复杂UI等剩余边界仍按审计文档继续，最终Actions与实机功能、线程、Java/native内存验收未完成。


### 2026-11-01 公共API后续修复与第二次接续

阶段提交3a6d05c已推送dev，Actions 41266154636构建成功，发布v10.7.0；该APK对应阶段快照，不包含随后尚未提交的公共API修改，也不代表109项闭环。
公共API新增HookRegistry异常finally移除本地句柄测试、生产Change/Observe/Event/数据库API合成关闭测试；初始与扩展定向均通过。数据库嵌套depth平衡、重复close、关闭后拒收、fresh实例、消息去重缓存硬上限已纳入测试。UI/本地消息/SNS观察新增close仍需扩大编译与集成验证。

编译历史完整保留：initial旧ABI缺DexKitBridgeManager构造签名；retry1错误引用不存在的WeChatMediaApi.kt及更多旧ABI；retry2编译期间源码继续修改导致符号视图不一致；frozen-v1无完成/退出记录，接续发现进程已终止，不能计通过。resume1以当前冻结源码重新启动，日志/退出码独立保存。
83项冻结在12个结果后会话中断；原目录/DONE缺失状态保留，源SHA256复核一致，在独立resume1目录续跑剩余71项。完成后必须复核83个唯一测试、失败列表、源哈希及清单一致性。

只读依赖复核发现ScriptNewFriendHook同时供AutoReplyFeature消费，不能在脚本销毁时全局关闭或清原生订阅者。脚本全局Runtime、目录观察器、重载线程、executor可重建及跨代回调尚未修复，不能把局部scope测试列为该Runtime验收。

### 2026-11-01 公共API阶段提交前复核
用户授权“先修好，然后提交推送”。移除上轮误加入、调用不存在的 ScriptPluginRuntime.close() 的 Feature 销毁钩子，恢复可编译状态；未以空实现伪装 Runtime 生命周期修复。脚本 Runtime 代际关闭、可重建及迟到回调隔离仍为 open findings，共享好友/发送按钮监听未关闭。
修正 test_dex_finder_gates 的 Xposed Java 测试桩，补齐 Unhook、hookMethod、log，删除重复/丢失的 Kotlin 桩输入；测试编译真实 HookRegistry。原首败与所有后续失败日志保留，不覆盖为通过。
独立定向：test_hook_registry_shutdown、test_public_api_shutdown 通过；test_dex_finder_gates 修正后通过（/tmp/pl-dex-finder-fixed4.exit=0）。扩大直接 SDK/ABI 编译通过（/tmp/pl-api-sdk.final.exit=0），不等同 Android/R8 构建。
全新冻结回归 /tmp/pl-resource-regression-final-83：83/83、83个唯一测试、failed=0、sourceStable=true、listMatch=true；结束后独立SHA256重算零漂移，退出码0。证据 /tmp/pl-resource-final83-verified.json；历史83项中1项失败记录保留。文档更新在冻结验证完成后进行，不改生产源码/测试。
这是公共 API 所有权与关闭链的阶段提交，不代表109项审计完成。最终 Actions 构建结果需推送后核实，实机功能/线程/Java-native 内存验收未执行。
