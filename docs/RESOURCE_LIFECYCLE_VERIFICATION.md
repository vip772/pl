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
