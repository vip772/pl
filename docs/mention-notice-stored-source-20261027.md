# 艾特提醒数据库快照丢失来源修复

## 对比结论

81bbec7 没有直接修改 MentionNoticeFeature、消息变更 API 或消息存储 API，不能将该优化提交认定为直接原因。

8c4f87a 为修复提醒上下位置偶发错误，将提醒输入从接收事件改为数据库插入快照，并以实际入库 createTime + 1 定位提醒。接收事件原本包含单独解析的 msgSource；改用数据库快照后，来源字段完整性成为必要条件。

现有 WeChatDatabaseListenerApi.normalizeLegacyMsgSource 仅在写入包含空 msgSource 键时从 lvbuffer 提取元数据；缺省该键时直接返回。WeChatMessageStoreApi.queryFirstMessage 虽读取所有列，却忽略 lvbuffer；WeChatMessageChangeApi 的 ContentValues 后备构造也忽略它。仅在 lvbuffer 中存在 atuserlist 的插入快照因而被判为 NONE，handle 静默返回，不会插入提醒。

用户日志先前已确认提醒安装成功，但未包含逐条消息快照或过滤原因。此次测试可以复现上述源码缺陷，无法仅凭日志证明它是用户这次漏提醒的唯一原因；没有进行真机复测。

## 修改与性能范围

新增只读 WeChatStoredMsgSource.resolve：优先保留有效 msgSource，仅在缺失时从现有 lvbuffer 字节或字符串中提取完整 msgsource 段。接入已有 cursor 单行读取、Map 转换及 ContentValues 后备构造。

不修改微信写入参数，不额外查询数据库，不改变排序时间和去重，不增加 UI/滚动监听、轮询或定时任务。正常来源存在时直接返回；新解码只发生在来源缺失且已有 buffer 的消息快照转换中。

## 验证

仅 SDK 定向编译、生产方法回归和静态检查，不做本地完整构建：

- test_mention_stored_source：复现旧路径丢失来源；真实模型识别 binary lvbuffer 中 AT_ME/AT_ALL；来源优先、空值、破损标签、10000 次已有来源快速返回。
- test_mention_notice_order：数据库时间排序与 insert-only、历史、发送消息、私聊和未知 ID 保护。
- test_mention_notice、test_mention_source_pipeline、test_mention_notice_span_bounds：分类、接收来源与引用隔离、宿主文本偏移保持。
- test_message_single_cursor：1215 组转换与异常比较，表顺序、关闭 cursor、后备查询和无行 Map 分配。
- test_mention_quote_scope、test_mention_lazy_readers：引用隔离与 15880 组读取兼容比较。
- git diff --check。
