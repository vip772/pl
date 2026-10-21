# 内置隐藏艾特

入口为增强功能和新功能，共用 hidden_at Provider，配置 Hchat_hidden_at_config/enable，默认关闭。

## 微信8.0.78修复

旧版WCDB insertWithOnConflict拦截、type57替代发送、手工lvbuffer封装和同线程替代记录匹配已删除。现在与表情艾特共用 OutgoingAtMessageHook，仅安装一次真实入库核心 f9.Bb(e9, boolean)。功能只处理自己发出的群聊type1文字，从原生消息G字段读取MsgSource，按名单和微信软分隔符清理正文，再调用b1设置正文；原消息ID、类型和MsgSource沿原发送链路保留，不二次发送。

已确认文字构造器v51.r0在调用Bb前设置r3(MsgSource)，doScene在读取原消息j()后写入pc5.pr4.e，名单继续由微信原生source assembler处理。需要双端确认接收端艾特提醒是否保留。

安装使用DexMethodCache当前runtime key和DexInstallScheduler，关闭时注销自身Observer，不影响表情艾特。日志包含安装成功、无MsgSource/账号未就绪/名单为空/正文片段不匹配等阶段，不输出正文与成员名单。无法确认片段时保留原样；重复提及同一成员、截断名单等数量不一致情况仍保守跳过。

仅已确认8.0.78（versionCode3180）的APK和Smali。其他版本不沿用混淆字段，明确失败关闭。Android SDK依赖桩定向编译与文本/缓存回归通过；未执行Gradle、完整Compose构建或微信双端测试。

设置页按用户要求仅保留功能名称、独立开关与返回入口，开关副标题和说明卡片已移除；技术依据保留在文档。
