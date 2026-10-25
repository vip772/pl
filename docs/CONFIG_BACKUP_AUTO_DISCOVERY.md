# 配置自动备份与资源恢复

已实现，验证范围如下，实机验收待进行。

## 配置发现
原 HchatConfigBackup 在 MiuixSettingsPagePart02.kt 中手工列出约 60 个配置库，导入导出共同受该名单限制。现独立为 ui/miuix/HchatConfigBackup.kt，删除旧名单。

HchatStorage.backupPreferenceNames 组合当前包已打开的缓存实例及 Hchat 根目录的 .kva/.kvb/.kvc 文件名，自动发现本次尚未打开的配置库。用户设置名称应使用 Hchat 前缀和字母、数字、下划线、连字符（最长 200 字符），通过 HchatStorage.preferences 或 ConfigStore 保存。新增符合约定的功能无需修改备份名单。空配置库也保留在清单中。

运行数据使用 HchatStorage.runtimePreferences，分类标记持久化于 .runtime_stores；DexMethodCache 已统一接入。兼容现有命名，默认排除 _cache、_seen 后缀、_method_cache 和 _runtime_log。不要用这些名称保存用户设置。未来采用普通 preferences 且不遵循运行数据约定的运行库仍可能被收录，开发者必须正确分类。

会话分析 AI 设置原先绕过统一存储，现切换到 HchatStorage，并在创建该配置库时一次性迁移旧 SharedPreferences 缺失键。导出也会检查这份旧库，不要求用户先打开 AI 设置页；已有 FastKV 值优先。迁移标记不参与备份。

## 格式与资源
新导出为 ZIP（application/zip），包含 config.json 和 resources/ 下的资源，清单版本为 2；旧 HchatConfigBackup 版本 1 JSON 可导入，旧版应用不支持新版 ZIP。

支持已有资源目录：background_beauty、side_panel、home_profile、custom_friend_avatars、custom_bottom_bar_icons、message_bubbles、FloatingShortcut/icons。以后新增资源统一通过 HchatStorage.resourceDirectory(context, feature) 保存在 resources/<feature>/ 中，即自动收录。临时 .tmp/.import 文件不导出。

不会扫描任意外部目录、脚本源码、下载媒体、日志或崩溃记录。仅保存路径的外部文件不等于已备份。归档带原模块根路径，恢复时对直接以原根路径开头的字符串及字符串集合做新根路径替换；任意 JSON 字符串内部或第三方外部路径不自动改写。背景图片、头像和模块图标按受管理目录恢复。

备份会包含用户保存的 AI API Key 等设置，文件为明文 ZIP，分享前注意其中的个人配置。

## 恢复与失败处理
导入读取清单中的实际配置库，不要求预先注册目标功能。先验证全部类型、配置名、清单/资源集合、重复 ZIP 项、版本、大小和资源路径，再修改目标。拒绝路径穿越和符号链接，清单最多 32MB，总解压体积最多 512MB、归档最多 10000 项。导出先完整构建本地归档，再写到用户选择的文件。

导入覆盖清单中包含的配置库；清单未包含的本地库和资源不清除。所有受影响的配置保存内存快照，资源保存临时磁盘快照，发生可捕获的写入异常时回滚；回滚失败明确提示并记录 HLog.e，不报成功。暂存目录最终清理。此为进程内失败恢复，不是断电或强杀进程后的持久事务，也不对运行中功能并发修改设置提供跨进程隔离；导入后应重启微信。资源复制可在执行期间短暂被其它线程看到。

ConfigImportExportBridge 延续单次消费 Activity 结果的机制，使用单后台工作线程处理文件，忙碌时拒绝重复操作；成功提示分别显示配置库、键值及资源数，不以键值数为零判断失败。Activity 已销毁时不再操作其 UI。

## 验证
- scripts/tests/config_backup/archive.py：编译执行生产 HchatStorage 和归档实现，覆盖全新无名单配置、未打开的磁盘配置、运行库排除、旧 AI 配置迁移、六种值类型、资源恢复与根路径重定位、仅资源备份、旧 JSON、无效类型、运行库导入拒绝、commit 失败后的配置与资源回滚、路径穿越和链接拒绝。
- scripts/tests/config_backup/run.py：生产结果桥接 JVM 回归，五层 Activity 回调只执行一次，覆盖取消、错误 owner/code、重复回调、选择器回退、处理失败、重复启动和页面刷新失败；已适配后台处理和结果统计。
- Android SDK 36 + 实际 FastKV 3.0.1 AAR 定向编译：完整存储、归档、结果桥接和会话分析配置类通过；Xposed 和部分宿主依赖使用桩。测试 JSON 实现来源为 https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar ，测试通过 JSON_JAR 指定路径。
- 未运行 Gradle、未构建 APK、未操作微信；Android 文件提供器、FastKV native 实际读写及实机重启恢复仍待验收。
