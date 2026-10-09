# 商品与合并转发卡片标签：参考包对照（2026-09-11）

## 参考与设备核验

通过 DexClub MCP 确认用户指定 app-debug.apk 包名为 com.qechat.wechat，版本 1.0.0，SHA-256 为 7c22adb3d1aa444aa7088676ada05b63d476b64719a49556e15abc646b84afce。对照其 HchatExtraHooker 方法 Java 导出与同项目 Kotlin 源码。

上次对照时（2026-09-11 18:00 前）设备实际安装的 h.Hchat APK SHA-256 为 1eceb6cc4752f4e4181e311e9555b4475c3d838cdc26fbcf59bd5dbffc218a2a（17:01），并非上一轮 17:37 产物 7f3e4b2ae2680db62fabd0f37f43ad8461b88fbc2bcf38dcdabfd37b4b246090。因此 17:43/17:44 的 gg5.t4、gg5.n8 layout 失败日志不能用于判断上一轮 APK 的设备效果。

## 已确认的差异与处理

参考包 installMessageDetails 同时覆盖消息项和适配器绑定；insertMessageDetailsLabel 对 APP/商品卡片调用 safeBottomAnchor、rowBottomAnchor、wrapCardDetailsAnchor。后者把完整消息分支与标签放进纵向 LinearLayout，容器继承原外部布局参数，高度为 WRAP_CONTENT，原卡片 ID、背景和 holder 引用保留。标签不再依赖内部 timeTV/clickArea 是否可用，也不依赖固定高度的原生卡片父容器为额外兄弟标签扩高。

当前实现采用相同的完整行归属与卡片容器方式。缺少可用内容字段时根据消息行底部直接子项定位；多子项尚未测量时延后处理，避免零尺寸阶段错误选中时间条或选择框。已存在容器直接复用，避免重复嵌套。上一轮 MessageDetailsContentAnchor 内容槽方案及其专属测试已移除。

参考包 observeMessageDetailsLayout 会在后续布局和重新附着时检查标签状态。本实现仅对底部卡片标签启用 MessageDetailsLayoutObserver：合并同一行的布局回调，比对深度 4 子树和标签自身状态，仅几何/可见性/父容器变化时恢复；同一状态不会循环插入。重新绑定前取消旧监听并清除旧标签，detach、关闭和销毁会取消排队，旧回调不能回写新消息。普通消息继续使用现有一次附着校验，不增加持续树扫描。

## 分类与范围

继续使用 MessageTypeLabels 分类，合并转发外层为 appmsg type 19，商品使用当前 raw/appmsg 语义。公共 WeChatMessageTypes.normalize 未修改。转发记录详情列表仍用独立 datatype 规则，不能把详情 Hook 与外层卡片问题混为一谈。日期格式选择与悬浮菜单响应优化保留。

## 8.0.78 卡片时间详情位置回归（2026-10-09）

微信 8.0.78（3180）实机截图显示“微信团队”卡片的消息底部详情文字错误叠在昵称和标签区域；模块日志同时记录 `holder=com.tencent.mm.ui.chatting.viewitems.mq`、`root=com.tencent.mm.ui.chatting.viewitems.jh`、`position=message_bottom`、`reason=layout`，且该 holder 没有 clickArea。代码核对发现卡片分支曾回归为直接采用普通消息的 timeTV/content 候选，导致标签可能挂入卡片内部临时时间布局；已有 safeBottomAnchor、rowBottomAnchor、wrapCardDetailsAnchor 专用链路未被调用。

本次恢复卡片候选向完整消息行直接分支的提升，再以整行底部直接子项兜底，不再把内部 timeTV 父容器当作最终卡片锚点。任何插入失败都会在安排预绘制重试前立即清理当前行标签，插入函数自身也清理已创建但未成功挂载的标签，避免重试窗口内在昵称区域显示错误中间态。普通非卡片消息继续使用原有候选锚点；菜单扩展和动态角标代码不在本次修改范围内。

首次修订后的复测仍发现偶发顶部偏移，但日志不再出现 `reason=layout`，说明插入被错误判为成功。截图中标签距行顶部约2dp，与 FrameLayout 分支在 `content.bottom == 0` 时使用 `topMargin = content.bottom + 2dp` 完全一致。进一步修订要求 FrameLayout 的完整卡片分支仍是父容器直接子项，且父容器宽度、卡片宽度和卡片 bottom 均大于零；零尺寸阶段返回失败并交给既有预绘制重试，不再把未布局状态当成成功。

## 验证边界
续接时已运行生产布局观察器 7 项及预绘制/附着队列 14 项回归，全部通过。布局观察器回归覆盖标签异步移除、滚动复用旧回调取消、稳定几何不循环、observer 迁移、深层标签状态变化及 detach/clear 清理。编译和这些模型测试不等同于微信真机 UI 通过。本轮宿主绑定链针对设备微信 8.0.76（3140）核验，未完成 8.0.49/58/66/68/72/74/77 横向验证。上述 8.0.78 回归修复已取得截图和运行日志证据，但仍需 GitHub 构建后的同类卡片实机复测。

## 聊天适配器绑定补齐（2026-09-11 恢复任务）

恢复时源码仍只有消息项绑定 Hook，参考包的适配器完整/payload 绑定尚未整合。通过 DexClub MCP 重新打开设备提取的 wechat-installed.apk，Manifest 确认为 com.tencent.mm 8.0.76（3140），确认以下入口：

- 完整绑定：`Lcom/tencent/mm/ui/chatting/adapter/k;->p0(Lpo5/s0;I)V`。使用 MicroMsg.ChattingDataAdapterV3、_onBindViewHolder[、msgInfo 字符串定位，调用父类完整绑定后仍设置 View tag 和通知原生组件。
- payload 绑定：`Lcom/tencent/mm/view/recyclerview/WxRecyclerAdapter;->q0(Lpo5/s0;ILjava/util/List;)V`。由该基类的 J 桥接入口调用；从当前 data[i] 取得 item，执行消息项 h 后还会执行 t0(view,item,position)。该方法属于共用父类，Hook 必须以聊天适配器实例限定作用范围。
- 消息项：`Lve5/g;->h(Lpo5/s0;Lpo5/c;IIZLjava/util/List;)V`。第二参数为当前消息 item，实际原生消息保存在 item 包装对象内；完整与 payload 的第二参数都是位置，不能按消息对象解析。
- 适配器消息读取：`Lcom/tencent/mm/ui/chatting/adapter/k;->J0(I)Lcom/tencent/mm/storage/e9;` 从当前 I.o 的对应项返回消息。外层 Hook 接收的 thisObject 已经是聊天适配器时直接复用，避免继续从其字段查找另一个适配器。

实现同时保留消息项入口，并安装定位到的完整绑定及相同 holder 类型的 (holder,int,List) payload 入口；排除抽象/静态方法和桥接 holder。描述符列表使用实时 DexMethodCache runtime key 缓存，仅完整/payload 均存在时写入。消息详情单独使用 DexInstallScheduler 任务，避免其它扩展功能已成功导致缺失绑定入口停止补装。

绑定状态按当前行 View 身份匹配：同一行嵌套只清理一次旧标签与监听，内层将当前消息传给同一行外层，外层原生操作完成后挂载；不同消息行分别完成自己的绑定。布局重试保留本次原生消息及绑定 token，避免仅持有位置时读取到后续列表变动后的另一条消息。优先采用宿主绑定完成后的 holder tag。

这些入口修复针对聊天页面的外层商品/合并转发卡片；转发记录详情列表仍走独立 datatype 规则。本次新增绑定入口仅对 8.0.76（3140）取得实现证据，其余约定版本未完成横向核验；静态审查和构建结果不能代替设备 UI 回归。

## 本次产物验证

2026-09-11 本地 Release 构建通过（2 分 46 秒），保留 R8 与资源压缩；APK Signature Scheme v2、ZIP 完整性校验通过，ARM 64/32 库完整，签名与前次本地安装包一致。R8 mapping 包含新适配器绑定定位与布局观察器。布局观察器 7 项、预绘制/附着 14 项回归通过，git diff --check 通过。尚未安装或执行微信 UI 回归。
