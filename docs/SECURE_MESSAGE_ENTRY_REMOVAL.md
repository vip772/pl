# 安全消息入口清理与媒体发送修正

基准：dev / 9366061。本轮统一提交独立入口清理、主页开关调整及媒体请求补写。

## 入口与配置

独立安全消息入口与特殊消息内部开关共用 SendSecureMessageFeature 和 Hchat_secure_message 配置。删除独立设置提供器注册、SecureMessageSettingsProviders.kt、SecureMessageMiuixPage、SEND_ID/ANTI_ID 专用页面路由，以及无用导入和测试提供器桩。反安全消息提供器原本未注册，随无用文件清理。

特殊消息主页直接显示安全消息和反安全消息两个开关，删除内部二级页面、路由状态和返回处理。保留两个运行时注册、已有配置名称和开关键；默认均关闭，不迁移用户配置。

## 媒体发送

微信 8.0.78/3180 的媒体上传会重建 MsgSource，消息入库补写不能覆盖最终上行请求。新增限定该版本的请求编码 Hook，覆盖图片 CDN 请求、PB3 图片上传、视频、原画视频及表情请求。大小计算和实际编码前均补齐标记，避免 protobuf 长度前缀失配。PB3 先精确判断请求类型，其他类型不读取偏好或处理 XML。Hook 使用现有运行时资源登记，关闭时释放。

安全节点仅含 UUID 不代表已启用安全策略；现在检查 sfn=1，并在补写时保留 UUID、节点属性、未知子节点及其他 MsgSource 内容。配置、反安全消息及 8.0.76/3140、8.0.77/3160 旧表情适配源码保持与基准一致。原生请求字段、tag、接口及样本哈希见 SECURE_MESSAGE_MEDIA_NATIVE_EVIDENCE.json。其他版本不套用 8.0.78 混淆类名。

## 验证范围

- `python3 scripts/check_secure_message_entry_sdk.py --static-only`：入口清理、主页双开关、运行时保留、原配置及旧适配不变、媒体版本限制与大小计算/编码入口静态断言通过。
- `python3 scripts/check_secure_message_entry_sdk.py`：以上静态检查及全部安全消息生产源码 SDK 定向编译通过。使用真实 Android 36 SDK 和依赖 ABI 桩，同时编译实际资源所有者及相关消息模型；不包含 Compose UI 完整文件类型验证。脚本需要 `/tmp/pl-kotlin-libs` 中的 Kotlin 编译器依赖。
- `git diff --check`：通过。
- 先前依赖缓存缺失造成的编译受阻已通过依赖 ABI 桩方式解决；历史失败日志不作为当前通过证据。最终 SDK 日志为 `/tmp/pl-secure-final-sdk.log`。
- 未运行 Gradle、完整 Android/R8 构建、合成宿主行为回归或实机测试。SDK 编译和静态断言不能证明实际媒体收发效果，仍待新版实机验收。
