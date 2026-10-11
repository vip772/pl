# 精准诊断：启动服务器提示与遗留会话父级

本轮只增加只读诊断，不宣称问题已修复。未修改网络结果、屏蔽 Toast、清理配置或主动恢复数据库归属；未构建 APK；尚未提交推送。

## 服务器提示

版本标识 `revision=scene-correlation-v2`。使用已从日志堆栈和微信 8.0.78 APK 验证的 x5.onSceneEnd(int,int,String,m1) 精确入口，不扫描所有回调。
记录 sequence、errType、errCode、sceneType、sceneClass、sceneIdentity、线程和启动耗时；错误消息只记录长度，不保存正文或请求载荷。
线程局部嵌套栈将目标 Toast 与当次同步回调精确关联，输出 correlatedScene；若不在该回调内则明确输出 none，不把相邻日志时间当作因果证据。
启动诊断窗口 180 秒，目标提示最多三次、相关场景日志最多20条，结束时卸载 Hook。微信类名变更或入口加载失败输出 sceneHook=unavailable，而不是盲目挂钩其他方法。
暂不增加低层网络/全局 Binder 诊断：先依据失败场景类与类型再分析其请求链。

## 遗留会话归属

`[Hchat:ConversationGroupDiagnostic] revision=parent-snapshot-v1`，每进程每个未解析账号/会话一次，现有副本重写之前记录。
主/备用配置的字节量、账号块数量、当前账号条目数、对应条目状态和对应 parent；当前 parent、虚拟父行是否存在、规划待更新/未解析数量、合并恢复表是否有原始值。
条目状态区分 account_absent、talker_absent、invalid_value、module_parent_invalid_backup、trusted_root、trusted_native_parent。
不输出其他账号的内容、聊天记录或完整配置；当前账号只用非可逆用途的短 hashCode token 区分（不是密码学匿名化，日志仍含故障 talker/parent，分享时需注意隐私）。
保留原 ERROR 与拒绝猜测写入保护。若主副本和备用副本都不存在可信原始值，当前日志本身不能创造历史信息，需要旧配置或用户明确确认目标归属才能恢复。

## 测试

`python3 scripts/tests/server_error_diagnostic/run.py` 通过，编译生产 Java 诊断类并用 JVM 桩验证精确场景/错误关联、嵌套回调清理、无关联提示明确标识、不改变参数和结果、不输出错误正文、180秒窗口及卸载。
`git diff --check` 通过。未完整编译 ConversationGroupRuntime 的 Android 功能；未实机验证诊断 Hook 安装和数据库元数据快照。

## 实机操作

安装包含此改动的版本，主微信和分身分别完全退出后重进，记录是哪一个用户/账号环境。启动后等待约15秒观察提示，最好保留3分钟日志，不清理配置或聊天分组。
导出 Hchat 运行日志和 LSPosed 最新日志；必要截图只记录提示与时间。
查看 sceneHook=installed 是否存在；出现目标提示时保留 targetToast 和 correlatedScene。若 sceneHook=unavailable 或 correlatedScene=none，也原样反馈。
分组问题无需删除/移出群，只提供对应 ConversationGroupDiagnostic 及原失败记录。
取得证据前保持动态角标、动态撤回、QQ点歌现状不变。