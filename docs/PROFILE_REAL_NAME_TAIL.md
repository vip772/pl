# 资料页实名尾字

“界面 → 资料页显示”新增独立“实名尾字”，默认关闭。开启后在同一卡片内展开现有 InputRow 文本编辑和 ColorPickerRow 三色渐变编辑。独立配置库为 `Hchat_profile_real_name_tail_config`，键为 `enabled/prefix/color`；与聊天实名功能的开关和前缀互不影响。

固定标签为“实名尾字：”。读取已有实名缓存，缺失时复用 BeforeTransferNameQuery 查询，只展示实名最后一个 Unicode 字符，不使用联系人昵称。已知尾字“东”时，默认或空白前缀显示 `实名尾字：可能是个东`，自定义“小”显示 `实名尾字：小东`；无有效尾字显示 `实名尾字：未知`，未知不带前缀。空白前缀回退到“可能是个”，已有明确保存的“＊＊”仍作为自定义值保留。

## 接线与生命周期

通过显示邀请人所使用的 WeContactHeaderApi.Provider 插入头部。公共刷新支持普通联系人无群上下文，并在执行刷新前复核页面身份，避免切页后的旧请求刷新新页。

开关监听在功能初始化阶段登记，避免全部默认关闭时被安装门控跳过。开启后使用统一 DexInstallScheduler 有限补装头部；关闭时释放独占查询 Hook、Provider 和缓存，允许再次开启。共享线程池不由此功能关闭。

查询结果仅保存在 Provider 内存中，最多 64 条；同时最多 8 个请求，20 秒后允许淘汰过期请求，空结果冷却 60 秒。关闭后的排队任务和迟到回调通过关闭标记及请求 token 失效。复用查询器的 `Hchat_feature_real_name_tail_runtime` 显式登记为 runtimePreferences，定位缓存排除备份。开关、原始前缀和三色配置由统一备份自动收录，无新增文件资源。

## 验证

- `python scripts/check_profile_display_sdk.py`：页面、配置类、三色绘制 SDK 定向编译；默认关闭、默认/空/自定义前缀、未知、补充平面汉字和启用聚合检查。
- `python scripts/check_profile_real_tail.py`：生产 Provider、设置、PreferenceRuntimeOwner 和 FeatureResourceScope SDK 定向编译；项目查询和任务接口使用夹具。JVM 行为验证去重、8 请求上限、空结果冷却重试、任务拒绝、关闭后迟到回调和反复启停清理。安装门控接线作静态检查。
- `python scripts/tests/config_backup/archive.py`：生产 HchatStorage 和 HchatConfigBackup 的真实 ZIP 导出导入；关闭/开启 × 空/自定义/新默认/旧自定义前缀共八组与三色配置往返恢复，查询定位缓存库从归档排除。

日志分别为 `/tmp/check_profile_display_sdk.py.current.log`、`/tmp/check_profile_real_tail.py.current.log`、`/tmp/archive.py.current.log`。

查询协议、微信 Hook 实际命中、不同版本可用性和界面最终效果仍待实机验收，夹具验证不代表真实网络查询成功。本次不运行 Gradle、不生成 APK，不提交、不推送。

后续用户已授权将此前工作区修改与群聊分析界面调整统一提交并推送至 dev；前述“不提交、不推送”记录对应当时开发阶段。
