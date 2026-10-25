# 艾特高亮绑定开销优化

## 依据与范围

74aeb47 与 efe6d62 的聊天滚动采样均出现高亮绑定捕获、具名反射查找；此前有效调用栈也确认了消息存储查询。源码原本在取得有效原生消息 ID 后，无论消息类型和会话，均调用 getMessageById 执行 SQL，再在文本 Hook 中以 isGroupChat/isText 过滤。

本次沿用已有 Hook、原生字段别名、消息模型和公共 API，未增加或替换微信内部签名，未重新进行多版本 APK 逆向；不可视为新增多版本实机验证。

## 改动

- 在查库前读取原生 type/talker，复用与渲染相同的 WeChatMessageTypes.isText 和 WeChatMessage.isGroupTalker 判断。有效正数类型明确不是文本，或非空 talker 明确不是群聊，直接返回空绑定消息，跳过查库和后续模型转换。当前原生字段本来也优先于存储字段，因此该过滤不改变原本渲染资格。
- 类型未知或 talker 为空仍继续查库补充。群聊文本继续保留全部既有数据库合并；不能把原生 msgSource/reserved 的合法空串认定为持久化数据已完整。尚未实现“原生群聊文本完整时免查库”，需要进一步的真实字段完整性证据。
- 高亮运行时内部缓存 numberValue/stringValue 使用的零参数方法、逐级父类声明字段，包括未找到结果。最多 128 个真实 Class 的同步访问 LRU，以 Class 身份区分 ClassLoader。仅缓存元数据，不缓存原生对象或字段值；读取及调用在锁外执行，沿用 getter/字段顺序、失败回退及原有空值语义。
- 绑定根视图的手势状态在消息转换前清理，避免新增提前退出绕过行复用清理。原有 ThreadLocal sentinel 和嵌套绑定出栈逻辑不变。
- 在 `BindState` 内按需缓存当前消息的 `getEffectiveAtUserList()` 结果，包括空列表；同一绑定中的多个文本入口复用该结果，嵌套绑定、RecyclerView 重新绑定和不同消息保持隔离。缓存只保存当前不可变消息模型生成的名单，不跨绑定或跨聊天复用。
- `buildMentionText()` 改为接收绑定级名单，保留原始顺序和重复成员，避免名单顺序与原生点击范围错位。未修改引用范围判定、颜色、ProfileSpan 点击和公共消息解析器。
- 本次未缓存 adapterItem 的方法扫描、递归字段列表或 root 查找；未修改全局 KavaReflector、消息详情、时间解析、背景和底部布局。

## 生命周期与限制

缓存归现有 MentionHighlightRuntime 实例持有，与其已安装 Hook 同寿命；未新增安装/销毁机制。容量有界但强引用 Class，不保证存活期间类加载器立即卸载；淘汰后重新解析。群聊文本仍查库，联系人名称查询也仍保留，不应宣称已消除高亮数据库开销。

## 验证

- scripts/test_mention_binding_lookup.py：编译提取的生产转换和缓存代码，使用真实 WeChatMessage/Types/ParsedMessage，反射及存储用计数桩。覆盖 3000 次稳定转换不再查找元数据、可变/继承字段、getter优先级与异常回退、合法空元数据从数据库补充艾特来源及 reserved、非群聊/非文本零查库、未知字段查库、无效 ID、数据库无记录/失败、同名类不同加载器隔离和 128 条容量上限。
- scripts/test_mention_at_user_cache.py：使用计数桩验证绑定内非空名单和空名单各只读取一次，并验证嵌套绑定、重新绑定互不共享。
- scripts/test_mention_quote_scope.py：验证引用范围判定仍只允许当前正文；scripts/test_mention_source_pipeline.py：验证引用消息来源隔离、真实当前消息艾特、所有人和回退保持正常。

未运行 Gradle、未构建 APK；定向 JVM 回归不等于完整 Android/R8 编译、Hook 生命周期或实际高亮颜色/点击验证。需安装对应新包，用同次 mapping 在原聊天同配置复采并验收普通艾特、所有人、文字变色、点击资料、滚动复用；以 efe6d62 有效三轮作性能参考，CPU占比不能替代帧结果或因果证据。
