# 内置隐藏艾特

入口位于增强分组和新功能聚合，共用同一 Provider 和 Miuix 页面。配置为 `Hchat_hidden_at_config/enable`，默认关闭，即时保存；关闭时卸载本功能 Hook。仅安装于微信主进程，不依赖 ScriptPluginRuntime、BeanShell 或脚本开关。

在 WCDB `insertWithOnConflict(String,String,ContentValues,int)` 的 message 插入入口识别自己发出的群聊文字消息。仅名单与微信软分隔符艾特片段数量一致时转换为 type 57 AppMsg；邮箱、换行、正文空格不做全局替换。过滤自身、系统账号和重复成员，上限为 200。账号或 XML 发送 API 未就绪时不转换。

复用 `WeChatApis.messages().sendXml` 的既有 msgsource 参数能力，XML 中携带 atuserlist，不再沿用脚本的混淆发送任务/protobuf 字段 Hook。ThreadLocal 只在当前发送调用作用域内有效；只给同会话、同标题的嵌套 AppMsg 写入 lvbuffer。发送接口确认接受且观察到替代记录写入后，返回真实替代记录 ID，阻止原文字插入；不 Hook update，避免状态更新再次发送。

限制：当前实现要求发送 API 在当前线程完成替代记录插入。若 API 接受请求后异步写库或重新序列化标题导致匹配失败，则保留原消息并记录错误，此时可能同时出现原消息和转换消息。尚未有双端实机和多版本 APK 验证，不能宣称收件端艾特提醒、并发或事务异常恢复已通过。需覆盖普通/多人艾特、空正文、长群昵称、连续跨群发送、关闭脚本系统、发送失败与重试；不要与原隐藏艾特脚本同时开启。

已验证：运行时代码基于 Android SDK 加依赖桩的定向 Kotlin 编译；11 项文本/XML/元数据回归。未运行完整 Gradle 构建、设置页 Compose 编译和微信实际发送测试。
