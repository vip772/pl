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


### 2026-10-28 横栏挂载和最高优先级修复

08:47 运行日志仅证明 Hook 安装成功，没有横栏 Session 挂载证据，不能把安装成功当成显示成功。对照 HomeExtensionRuntime：它在早期注册原生列表事件、通过 addHeaderView 插入扩展，并使用 HomeExtensionSurface.Root 防止背景美化遍历破坏。横栏在 BRIDGE 阶段安装，新增安装后及 LauncherUI/MainUI onResume 的一次性首页查找，覆盖已创建列表；仅从 LauncherUI 树查找两种会话控件，不在滚动/绘制/消息回调遍历视图。

横栏仍是会话列表外的固定兄弟视图，承接标题留位，列表 BELOW 横栏；首页扩展位于列表内部，因此开启顺序不改变横栏优先级。横栏采用同一 Root 隔离标记，避免美化将横栏识别为原生首页内容；MainUI.F0 更新标题/分屏边距后重新定位。放宽对父容器和适配器精确类名的静默拒绝，改为校验 RelativeLayout 与可继承的 S2 入口，并记录实际类名及挂载结果。首页数据源也按已验证类型识别，兼容 Hook 安装前已经构造的源。

本轮须真机复测标题线下第一位、首页扩展开关顺序、重启与返回首页；静态和 SDK 验证无法替代实际位置验收。


### 标题真实坐标定位
09:10 日志已证明挂载成功，截图却显示在状态栏，说明原生列表 topMargin 不能代表标题底边（MainUI 的 header 分支会给 F0 传零）。改用侧栏源码已确认的 ez/hb/ih 标题容器，通过 getLocationInWindow 计算标题底边相对 MainUIView 的位置并扣除父级 paddingTop；标题和父容器布局变化时重算，卸载移除监听。横栏仍在列表外，优先于列表内首页扩展，选中文字保持蓝色。SDK 定向编译通过，真机位置待验收。


### 原生 TaskBar 头部与列表边界复核

ConversationListView 实际位于 smali_classes16。其 i 是原生 TaskBar 头容器；getFirstHeaderVisible 跳过该容器，setSelection(0) 会映射到原生可见头并使用字段 s 偏移。原先 BELOW 横栏把整个列表下移，叠加原生头空间且截断顶部透显；已取消所有列表 RelativeLayout 规则和 topMargin 改写，仅增加横栏自身44dp的 paddingTop，clipToPadding=false。背景美化恢复旧 padding 会撤销新布局，现通过弱引用几何所有权协调。停用时仅恢复本功能持有的偏移与裁剪状态。

显式切组调用已核实的 l(0L,0,true) 关闭原生下拉头，下一次UI投递再次回位；不在滚动回调中扫描/强制关闭，用户主动下拉仍由微信处理。横栏卸载时通过主线程投递删除兄弟视图，避免在 ViewGroup.dispatchDetachedFromWindow 的子数组遍历中同步删除。

系统 crash buffer 的09:14堆栈包含 dispatchDetachedFromWindow 空引用；09:36还有 NewChattingTabUI.yf.queueIdle 在预加载后 c 为空的异常，后续数据库关闭异常发生在主线程故障之后。后者尚未证实与横栏的直接因果关系，未添加广泛异常吞噬。真机崩溃与空分组、稀疏分组切换仍需验收。


### 2026-10-28 原生偏移链路纠正（替代上一节 padding 方案）

用户反馈5fc5247：切组标题下移/灰点、小程序状态区出现横栏、首会话遮挡、返回顶部抖动。日志只证明挂载成功，不包含原生滚动参数，不能据此宣称稳定。录像为12.25秒1080×2340约119fps，已解压并提取逐秒联系图；当前图片工具仅OCR，未进行逐帧视觉验收。

smali_classes16/ConversationListView：C()将gl.a(context)+E写入s，向TaskBarAnimController.n复制，并更新TaskBarContainer.q(statusHeight)。setSelection(0)映射getFirstHeaderVisible并使用s。onScroll在头部区域通过childTop-s计算标题translation，写I并设置C.translationY。此前增加padding而保持s不变，改变childTop-s，使原生按下拉处理。x由o()/q(II)分别置真/假。因此撤销padding/clip修改及美化几何豁免，改为原生s和控制器n统一增加横栏高度；C()重算前还原基线，后重新附加，避免累加；卸载恢复基线。

RecyclerView的对应入口为y1()，字段v2/Z2/d3，控制器仍n；o/q负责原生头状态。两条路径均按实际混淆字段分别读取。标题定位扣除其父链translationY，不把下拉动画当成稳定几何位置。小程序展开/下拉位移时隐藏横栏，关闭时恢复。滚动入口仅缓存字段读取和发生变化时的显隐，无视图扫描/SQL/重载/布局重建。

SDK编译、源码契约检查、生产偏移方法的基线重算/不累加/控制器一致/禁用恢复/打开头保持测试以及SQL回归通过。没有新增可用APK进行真机复测；不把定向验证等同首条不遮挡、下拉隐藏和滚动无抖动验收。


### 2026-10-28 全链路手势与刷新审查

32569ff反馈：轻微回弹横栏消失再现、小程序偶尔拉不出。确定代码冲突：以I>0推断小程序打开误伤普通回弹；切组两秒deadline的异步强制l(0,0,true)以及偏移重算后强制selection可能覆盖新手势；独立HorizontalScrollView的竖向起手不进入列表。已删除强制close及时间窗口，显隐仅使用o/q与x/d3真实打开状态。切组仅在无用户输入时允许一次数据完成回位；任何ACTION_DOWN取消待执行回位。C/y1后只同步偏移，不额外选择。

横栏输入按系统touchSlop方向判定：横滑/点击原样，竖向拖动CANCEL横栏点击并把DOWN及后续MOVE/UP/CANCEL转换到列表坐标；副本及时回收，卸载清理保存事件。列表触摸仅观察ACTION_DOWN/UP/CANCEL，不修改原返回值、不替换原OnTouchListener。消息、联系人、配置等所有重载经Session统一触摸/原生头状态门禁，触摸结束及头关闭后合并释放，避免重载中断下拉。

审查同时去掉返回首页重复重建横栏及重复联系人全量加载；列表复用时只刷新位置，联系人集合实际变化才重载。覆盖Store边界与备份、互斥恢复、线程局部SQL、增量过滤、首页扩展Root隔离、偏移恢复、观察者注销、延后卸载。SDK/Compose编译、手势时序、配置边界、原生偏移、SQL与遗留归属测试通过。

本次录像17.16秒1080x2340约117fps，已解压提取逐秒联系图；当前工具不能直接视觉播放，未声称已逐帧验收。没有执行完整Gradle构建或安装新APK。尚不能保证所有设备/快速多指操作端到端正常。


### 2026-10-28 冷启动与过渡定位（仅修改横栏）

用户澄清：首页扩展单独开启冷启动正常，和横栏同时开启才异常；已撤回本轮所有首页扩展改动，保留原实现。日志显示11:56:32横栏安装成功但11:57:44才首次挂载，证明安装完成不代表首页Session已就绪。横栏增加Activity.onResume补发现、LauncherUI.getInstance兜底和有界启动查找（40次/250ms，挂载成功停止）。不添加全局布局监听；标题容器尚不存在时延后Session创建；当前Activity不是Launcher时也使用原生Launcher实例兜底。Session首次挂载及reload均检查ready，归属交接前不启动原生重载。

标题过渡不可见时原positionBar回退nativeTopMargin=0，现保留stableTitleTop，不再零坐标回退。首次测量成功前横栏INVISIBLE；标题父链淡出/隐藏或原生头打开时不显示，恢复时先定位再显示。PreDraw只检查缓存的标题父链字段，不搜索视图、不发SQL、不重载，positionDirty或显隐变化才测量窗口坐标；卸载移除监听。没有新增首页扩展依赖或逐帧视图树搜索。

定向SDK编译、启动方法编译、手势/偏移/SQL回归通过；未执行完整Gradle构建或安装新APK，冷启动与动画端到端仍待验收。


### 首次显示定位与分组排序

首次Session在已布局列表上附加原生偏移时，增加s不会移动已经显示的child。现记录首个可见条目及top，仅按新增inset差值同步setSelectionFromTop，保留条目位置；空列表或Recycler路径只进行一次初始原生顶部定位。原生C()/y1()会在内部以新计算的基线调用setSelection，原先after才附加横栏高度导致原生先定位旧高度。现仅在该更新作用域内拦截定位，在原生读取偏移前附加横栏高度，并同步TaskBar.n；after检测已附加，不再累加。普通滚动和用户手势不增加回位。

保留BRIDGE安装阶段（数据库监听API是公共依赖），没有为缩短延迟提前未就绪的数据服务。首次约5秒出现的总耗时未作保证。联系人集合变化仅在好友/公众号筛选时触发重载；单纯名称/顺序变化不重载会话列表。

管理页每个条目右侧上下箭头，边界禁用；自定义重命名与移除间距12dp。首页长按后横拖、松手保存，长按未拖动松手保留左移右移菜单，取消恢复视图状态；排序手势不向下转发。保存沿用独立配置，与管理页顺序一致。未执行完整APK构建或真机排序/动画验收。


### 冷启动排队与原生重复定位修复（本节覆盖前述菜单/箭头行为）

用户日志14:39:23.580入队、14:39:27.541开始安装、27.571完成安装、27.591挂载。排队约3961ms，安装约30ms。保持BRIDGE公共API就绪条件，横栏priority=-100（同阶段较小值先入队），不修改全局调度器，不提前数据库服务。FeatureManager先安装公共API、最后markDexBridgeReady，因此公共API对象已初始化。实际首屏耗时仍需新APK日志确认，不能保证设备端绝对一秒。

原C/y1先读取旧s/v2，再写入新基线、复制到TaskBar.n、调用TaskBarContainer.q，最后比较旧新偏移，变化则setSelection(1)。此前before先撤回基线，导致新旧比较受到模块干预；只在setSelection入口附加reserve还会产生假变化。现在before保留旧完整偏移，TaskBarContainer.q入口在该更新作用域内给当前列表附加reserve，确保比较的是完整偏移；不改q的原生参数和小程序容器margin。ListView.i/RecyclerView.Y2验证容器归属，重复附加有幂等保护。普通滚动不执行此逻辑。回归复现原生顺序，重复相同几何100次不定位，几何变化一次保留原生定位。

删除首页长按左右移动菜单和管理页上下箭头；长按横拖排序及重命名/移除12dp间距保留。不修改首页扩展。SDK、Compose、原生偏移及手势回归通过；未完整构建安装，首条会话动画与首次显示时长仍待真机确认。


### f421417两轮反馈：启动、短列表切组、原生布局索引

两轮日志：15:37:28.206调度，30.275入队，31.841开始，32.222挂载；15:41:06.460调度，09.998入队，10.511开始，10.677挂载。提高队列优先级仍等待FeatureManager末尾BRIDGE及共享执行器。HomeExtension在feature.install直接注册生命周期。横栏现同样直接install，失败销毁后才进入BRIDGE重试，保留筛选ready/handoff保护，不修改公共调度器/首页扩展。

新提供geek.apk SHA256=23b1261c3a7d607d748baba7d4997d2bb6b9c09ee30296157e3ae86ee6fefe51。META-INF/xposed/java_init.list入口com.ljx.wechatmod.hook.MainHook；对该APK定向JADX确认Application.attachBaseContext/onCreate Hook及配置AtomicBoolean初始化，与已有同名源码核对。借鉴早注册生命周期，不把静态入口当作所有环境秒显的实测证明。JADX报告2处反编译错误，不宣称全APK反编译完整。提供release mapping已提取GroupBar/HomeExtension类段核对。

切组原实现先回位旧结果，数据通知后又post回位，短/空结果存在绘制TaskBar窗口。现在只在数据通知内同步回位，保留手势/open/dispose取消。原生ConversationListView.onLayout与l3.run硬编码setSelection(1)；实际getFirstHeaderVisible遍历头部，跳过TaskBar/隐藏头，返回实际可见头部索引。仅这两个原生作用域，在首页关闭且无用户触摸时改用原生可见头部索引；不对任意setSelection做全局改写。此为代码中的确定定位缺口，用户动画是否完全消除仍需真机验证，日志不包含逐帧布局信息。

公众号保留getPickerOfficialAccounts的gh_及verifyFlag!=0识别（含旧名称认证账号），增补微信原生服务号聚合conversationboxservice、服务通知notifymessage；SQL与增量同语义，不主动改变原生隐藏会话归属。服务号用例纳入SQL对等回归。

当前验证为SDK、SQL、手势、偏移/生命周期定向检查，未完成新APK构建安装和真机动画/秒显验收。


### 诊断版：撤回未经实测验证的索引改写

用户授权清理无效补丁并采集真实运行记录。本次删除cdd242d新增的onLayout/l3.run作用域索引参数改写及closedHeaderIndex，恢复这两个原生调用原始参数；其余偏移协调保留为固定诊断基线，避免同时撤销曾改善留白、透显与手势的代码。此版不声称修复闪动。启动提前注册、服务号、拖动排序保留。

新增[Hchat:GroupBarDiag]，切组或header开关触发1800ms窗口，1700ms结束记录，最多60条状态/12条定位堆栈。记录单调时钟、分组、总条目/头数量、前4个child的内存标识及几何可见状态、列表/TaskBar偏移、原生open、横栏、重载与手势标记。无聊天正文/昵称/消息读取；窗口外不反射、搜索或构造状态字符串。定位Hook不修改参数；追踪setSelection/setSelectionFromTop/smoothScrollBy来源，DataSetObserver记录数据变更，PreDraw只在窗口内采集变化。此开销是临时诊断开销，不能拿该包进行正式性能验收。卸载清理定时器及Hook。

用户安装诊断包后授权通过UI执行分组切换和下拉小程序收回，并同步录屏/取日志；当前未操作微信、未声称复现。最新附件日志在当前cleanOnExit路径不存在，不能声称已读取该附件。


## 2026-10-28 诊断清理与背景对照

已撤除 GroupBarDiag 的选择调用、布局、绘制观察 Hook、逐帧状态反射、短时计时器与调用栈日志。保留 4e16291 的新数据首轮布局前页脚协调，以及原有手势取消、原生头部偏移和横栏显隐协调。c06dfd4 撤回的原生索引改写仍不恢复。

9593586 撤除沉浸式背景对 ConversationFolderItemView.draw 的整行绘制抑制，用户已确认该版验收通过，小程序收回后的首条会话正常。展开面板底部透出情况尚无单独验收结论。历史诊断说明和已采集文件作为证据保留，不再代表当前运行代码。


### 验收后本地精简

9c0d467 已移除横栏临时诊断；本轮继续合并同内容的布局监听器，移除启动取证状态日志、仅供日志使用的发现计数和测试诊断占位。保留错误日志、生命周期保护、切组页脚协调、原生偏移及首次挂载补偿。文档上方诊断版章节仅为历史记录。

### 2026-10-29 自定义服务号筛选与空白反馈

本次对照 Hchat-runtime-20261029-123517.log、截图和 8.0.78 原生 smali：jo5.e.g 使用 im.j2.W（parentRef）调用 uf5.d.m；uf5.d.z 生成表名.列名，uf5.c0.d 生成 `rconversation.parentRef is null OR rconversation.parentRef = ''`。旧 GroupBarSql 明确跳过点号前缀，导致之前仅针对无前缀 parentRef 的放行修改在真实首页查询中不生效。现同时处理无前缀和明确 rconversation 前缀，保留 native WHERE、flag、分页、占位符和其它父容器限制。com.tencent.mm.storage.l4 的原生服务会话查询确认 conversationboxservice，显式选择成员的父级白名单同步补充此项；增量判断采用相同规则，不写数据库归属。

更新 test_group_bar_explicit_official.py，使用实际带表名前缀的 SQL 和截图两项 ID（gh_8a8cce549a85、gh_c2381078be34）验证，未选择公众号及其它隐藏父级仍排除；78 条 SQL 语义测试和旧父级测试通过。横栏手势、原生偏移及首次布局回归通过，生产横栏 Android 36 SDK 定向编译通过。

空白区水波纹的代码依据是背景 updateListRipple 对首页任意命中行启动反馈，缺少空白占位高度限制。现仅让 40–120dp 首页行产生反馈，并排除 TaskBar 和模块扩展；高大占位既不启动手动反馈，也不持有原生状态传播的反馈表面。只变更背景反馈，不扩大横栏点击范围、不修改原生触摸返回值。check_home_conversation_feedback_sdk.py 对真实生产条件做 SDK 编译和边界测试。具体截图空白视图类型日志未记录，需真机确认该反馈路径是否完整覆盖现象。

仅在已有 8.0.78 证据范围修正，未新增其它版本兼容声明。未执行 Gradle、APK 构建或真机帧率测量，未提交推送。
