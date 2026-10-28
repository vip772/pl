# 分组横栏：8.0.78 原生首页数据入口接入

本地功能 ID `conversation_group_bar`，配置 `Hchat_conversation_group_bar_config`，开关 `enable` 默认关闭。增强下“聊天分组”和新功能均收录。未提交、未推送；此前昵称宽度修改和遗留测试保留。

## 功能

固定分组全部/好友/群聊/公众号/未读默认显示；总计最多10组（5个固定、最多5个自定义），可视最多6项，少于6项铺满。两种样式为标准横栏和悬浮胶囊；复用首页音乐的 PopupChoiceRow，透明度使用已有 Slider/数字弹窗。自定义组可添加、重命名、移除、选择好友群聊；选择器复用 ConversationGroupConversationPickerPage，仅传本功能当前组，避免引用原有聊天分组归属。主界面长按菜单左移/右移并保存顺序。

聊天分组与横栏配置独立、运行互斥。生产 HchatStorage 为这两个配置绑定启用字段监听，覆盖设置页、快捷开关及备份导入；开启一方关闭另一方，不删除分组或成员。历史同时开启状态优先横栏。切换到横栏时，先在 ConversationGroupRuntime 原有单线程队列中恢复 parentRef；只有账号、数据库就绪且所有可恢复项目成功、没有 unresolved 时才启用横栏筛选。失败关闭横栏并记日志。

## 反编译证据和接入选择

目录 `/sdcard/Download/wechat8078_decoded/`：

- `smali_classes11/jo5/y0.smali` 的 `S2()` 打印 forceResetAllData，调用 jo5.r.b；后者推进 requestVersion、冻结旧通知并异步重新加载。
- `smali_classes11/po5/u.smali` 的 `S2()` 打印同样日志，调用 ConcurrentMvvmList.f 发起原生 reset。
- `smali_classes11/jo5/m.smali`、`po5/l.smali` 均调用 `jo5.e.g(jo5.m0):jo5.n0`。
- `jo5.e.g` 使用 uf5 查询生成器取会话，随后构建 jo5.z。它保留原生 flag 分页与结果版本规则。
- `po5.l` 的增量加入/更新调用 `jo5.e.l(jo5.z):boolean`；原方法判断原生 parentRef 是否允许出现在首页。横栏只在原结果 true 后补充本组成员判断。
- `smali_classes6/uf5/i0.smali` 生成 SELECT/FROM/WHERE/ORDER BY/LIMIT/OFFSET，`smali/uf5/l0.smali` 同步执行 qf5.k0 查询，因此 ThreadLocal 调用范围可限定实际 SQL。

附件 geek.apk 的查询和位置映射机制用于参考。进一步核查微信8.0.78后，本实现使用更直接的共同数据入口：查询拦截只在已登记的 jo5.y/po5.l 首页数据源调用 g 的线程范围内生效。复用 WeChatDatabaseListenerApi.subscribeQuery，不另装数据库全局 Hook；普通模块/转发/消息查询不在作用域内。SQL 只追加 WHERE 条件，保留原表达式括号、占位符顺序、排序和分页。

因为数据库返回的已经是目标分组数据，ListView 和 RecyclerView 都继续使用原生连续位置，无需再次映射或替换其内部索引；避免重复过滤导致错位。原先仅支持 jo5.f 的索引替换路径已移除。首次/切组使用 S2，原生负责异步版本与列表通知；数据变化事件在120ms窗口内合并一次补充原生 reset，确保未读变化与移出分组更新，持续消息不会无限推迟刷新。

## 开销约束

没有滚动/绘制监听、逐帧扫描、getView/onBindViewHolder 筛选 Hook、共享 MVVM 数组改写。联系人集合后台读取；SQL 条件按不可变选择快照缓存，改写 SQL 有64条有界缓存。样式/透明度变化只更新横栏，不触发会话重载。查询和增量判断共享相同分类语义。关闭清除筛选并原生重载，退出移除横栏及布局约束。

## 验证

- `scripts/check_group_bar_sdk.py`：生产横栏 Store/Filter/Feature/Runtime 与真实 DexInstallScheduler，Android36 SDK；项目服务及 Xposed 为签名桩。
- `scripts/check_group_bar_ui.py`：实际设置页/分组模型，真实 Android/Compose/Miuix 与 Compose 插件；项目壳组件为对照源码的签名桩。
- `scripts/test_group_bar_sql.py`：生产 SQL/增量规则在 SQLite 执行，分类一致、引号、OR、占位符、注释/嵌套、幂等、ORDER/LIMIT/OFFSET、带引号表名和非目标排除。
- `scripts/test_group_bar.py`：默认值、数量、编号、名称/成员/顺序保存；旧通用 GroupBarIndex 缓存测试仅属于独立辅助类，不再证明当前运行切换开销。
- `scripts/check_group_bar_integration.py`：注册、入口、互斥/恢复 guard、作用域和实际 smali 的两种加载/重载/增量契约核对。结构检查不等同于运行时测试。
- `scripts/tests/config_backup/archive.py`：生产配置与备份实现，包含启用/关闭、样式/透明度、顺序/成员/编号、快捷写入互斥和导入不同时启用；其他备份回归保留。
- `scripts/test_parent_repair_readiness.py`：原有父级修复安全约束回归。
- `git diff --check`。

## 验证边界

代码仅支持已核查微信8.0.78，两种首页列表路径均已接入源码并定向编译。没有完整Gradle构建、APK安装、真机显示/点击/快速切组/新消息测试或帧率测量，不能声称已达到附件APK的真机秒显或不掉帧。SQL与调度入口具备静态证据，完整微信运行链路仍需真机复测。

### 2026-10-28 运行日志修复

日志 07:50:29.272 记录真实群聊缺少原始归属备份，07:50:33.408 恢复门禁失败，随后横栏主动关闭。横栏交接允许保留无法可信恢复的旧分组归属，但仍要求所有有备份的恢复写入成功；不猜测原始父级。仅在首页作用域内扩展原生 parentRef 空值条件，使遗留模块父级下的真实会话可见，同时排除模块虚拟条目；增量成员判断采用相同规则。禁用后查询恢复原样。

MainUI 通过列表 topMargin 留出标题区域；横栏承接该边距并让列表位于横栏下方，关闭/卸载时还原，布局变化仅比较并更新边距。选中项文字统一使用蓝色 #1677FF。

管理页删除所有左右移动行；自定义分组的重命名、移除使用 Miuix TextButton 放在同一行右侧。首页原有长按排序保留。

定向验证不能代替真机位置、快速切组和帧率验收。
