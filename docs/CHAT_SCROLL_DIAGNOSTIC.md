# 聊天滚动专项诊断

## 首页与聊天装饰归属隔离

981关联日志中相同匿名Toolbar出现CHAT→WECHAT→CHAT→WECHAT。源码确认HomeUI.q/w缓存回调读取首页tab后会处理同Activity的聊天装饰；currentLauncherSlot不返回CHAT，原判断不能阻止越界。修正首页缓存回调排除CHAT标记及当前聊天子树，首页装饰缓存和树扫描同样排除聊天所属区域；聊天扫描继续限定chatHost。保留首页自身背景被外部替换后更新，不以忽略slot差异替代归属校验；聊天退出后的清理与恢复路径保持原实现。

13.881ms刷新发生在正式滚动前，含chrome5.378ms、cleanup3.517ms、chatApply3.367ms，不能将此修正视为全部耗时或卡顿根因。无需新增内部入口；沿用微信8.0.78证据，跨版本和实机导航回归待验收。

## 单次背景刷新关联诊断

applyForActivity 在专项开启时为同步刷新创建递增编号，finally结束并释放线程局部上下文；支持同线程嵌套刷新，异常及提前返回不改业务语义。background.*阶段继续计入原有汇总，同时归入当前刷新。每个5秒汇总窗口只保留最慢一次完成刷新的background.refreshSample，包含refresh编号、结束时间、总耗时、透明背景写入次数及该次刷新内的阶段累计微秒数。阶段仍可嵌套，不能相加充当总耗时；这里只关联同一同步调用，不把异步图片加载后应用归为已结束的刷新。

背景写入在外部状态同步前保存原页面类型；实际写入后增加匿名view令牌（identityHashCode异或本次会话盐值）、from/to页面类型和refresh编号。令牌只在同会话作近似身份线索，存在哈希碰撞，不输出View对象、聊天内容或联系人。非刷新上下文的写入编号为0。每种原因每窗口保留最新样本；最慢刷新另保留最多8条写入样本，最多24类阶段，单份详情最多2400字符，超过限制的样本丢弃或截断。关闭时不创建刷新上下文或样本；启用时有额外计时、字符串和统计成本，不能以专项开启时的帧结果代替关闭后的性能表现。

验证覆盖默认关闭、阶段返回及异常、嵌套上下文释放、最慢样本保留、写入身份/类型关联、样本上限、到期和工作线程释放；背景调用点进行生产片段编译和业务逻辑等价核对，诊断器使用Android36 SDK定向编译，项目外围依赖用桩。无新增背景功能优化，实机关联证据待新包采集。未运行Gradle、未构建APK、未操作微信。

## 979 D组细分采样与映射核对

采样前缀 `Dphase-20261024-100747`，版本9.3.5/979，系统帧窗口10:07:49–10:07:54：424帧，超时帧22.41%，P95/P99=18/25ms。相比978的21.33%、17/27ms有升有降，不判定性能改善。停止专项后的17.69%样本不作同条件对比。

979附件文件名对应37b997b；mapping pg_map_id为 `2b59221fa91881526c5a6ed9e30393afaa4b92d62a46ca14bc65219a23f23bc8`，尚未额外核验安装APK的map-id。准备阶段堆栈 `pi.z.y0:1024` 对应 setTransparentSurface 第2838行背景赋值，`pi.z.d:394` 对应 applyChatFooterTransparency 第2453行，`pi.k.invoke:582` 对应 installChatChromeLifecycle 第2327行。该请求发生在准备阶段，不直接归为滚动期根因。

与滚动开始重叠的25秒汇总窗口：background.refresh 3次、总21.226ms、最大13.012ms；cleanup最大3.559ms、chatApply最大3.857ms、chrome最大2.999ms。阶段最大值独立且可能嵌套，不能相加或当作同一次慢调用的组成。first=130、slot=6仅为该窗口计数，限频最新样本为CdnImageView和Toolbar；不证明全部首次写入来自相同类型，也不证明为同一对象反复写入。窗口包含切回微信过程，末两秒未捕获单独非空阶段汇总。

源码核查：applyBitmap的cleanup会复制并遍历surfaces，恢复未附着视图后移除；applyChromeTransparency先处理已缓存装饰视图，再扫描目标树；setTransparentSurface的匹配条件包含slot。当前没有同一对象slot变化序列或逐次阶段关联，不能认定Toolbar发生错误往返切换，也不能将首次标记等同于冗余写入。暂不修改背景功能行为；进一步优化须先有逐次关联或可复现证据，避免跳过必要背景恢复。mapping与临时分析保留/tmp，不入仓库。

## 978 后背景刷新细分诊断（待实机采样）

仅专项开启时新增 background.target/cleanup/host/chatHost/movingContent/retireChatSurfaces/imageSurface/chatApply/chrome/systemBars/invalidate 阶段计时，沿用每5秒汇总及4ms慢调用样本。background.refresh 为外层总耗时，阶段间及既有 footer 等计时可能嵌套，不能直接相加。异步位图加载后应用不属于原始 refresh 同步窗口，阶段计数也不保证与 refresh 一一对应。

setTransparentSurface 在外部状态同步前识别写入原因，仅实际完成背景赋值后计数：external=已安装背景被外部对象替换，first=无已安装背景标记，slot=页面类型变化，bitmap=位图切换，feedback=其余反馈模式不匹配。原因按上述顺序归类，不表示底层所有条件互斥；first也可能是清理后重新安装。此诊断只覆盖该透明背景赋值入口，不代表所有背景写入或所有 requestLayout 来源。

沿用有界统计，每类原因每窗口最多保留一个最新样本，只含时间与目标类名，不记录View对象、聊天内容或联系人。专项关闭时不构造样本、无阶段lambda分配或高精度计时，但仍有轻量启用状态检查。返回值、异常及业务调用顺序不变。验证包含默认关闭、到期停止、阶段返回值与异常、原因计数与样本合并、隐私和工作线程释放，以及Android36 SDK定向编译（外围依赖用桩）。未运行Gradle、未构建APK、未操作微信，本次随悬浮输入框面板联动修正一并提交；细分数据待新版实机采样。


## 977 透明背景与前景更新分离（本地待验收）

977 mapping 附件名提交与源码 `1f25c8a` 一致；未额外核验已安装 APK 的 R8 map-id。准备阶段 08:33:57 的堆栈还原为 `installChatChromeLifecycle` 第2313行 → `applyChatFooterTransparency` 第2439行 → `setTransparentSurface` 第2815行 `view.background = marker`。该样本早于08:34:17开始的滚动，不得把主要滚动窗口37次外部布局请求全部归到此来源。

源码与提取生产更新代码的 JVM 模型确认：背景仍是正确透明对象、仅前景被替换时，旧代码重新赋值背景一次。此次将背景匹配判断与前景更新分离，仅前景变化时只更新前景；保留外部背景同步、原生背景变化、反馈模式变化、slot变化及位图切换的完整背景更新路径。不拦截布局请求，不更改穿透高度、输入栏时序或遍历范围。

验证：旧代码在前景单独变化时背景写入1次，修正后0次；10000次状态不变调用无额外背景写入；原生背景、反馈开关、slot及位图变化仍触发更新，clearForeground=false不覆盖前景。提取生产更新块通过Android36 SDK定向编译，外围类型简化；模型不等于完整Android运行时，尚不能说明977现场一定由此条件触发或量化收益。未运行Gradle、未构建APK、未操作微信，修改保留本地。实机需验收输入栏、键盘/语音及加号面板透明效果和点击反馈，再判断性能。


## 976 消息详情参数比较修正（本地待验收）
976映射将 oj.l0#f:543 还原到 addBottomDetailsView 原2008行，实际写入详情标签的 layoutParams；MMChattingListView 是接收到传播请求的监测节点。映射附件名提交与当前HEAD一致，未额外核验APK R8 map-id。
AOSP android-16.0.0_r1 的 RelativeLayout.LayoutParams.resolveRules 将 START/END 规则解析为 LEFT/RIGHT。旧代码直接比较已解析与未解析的规则数组，会出现假差异。本轮两个比较调用传入 label.layoutDirection，复制当前参数，将复制件及新建期望参数解析到同方向后比较；保留宽高、边距、锚点检查，并比较 alignWithParent。不修改已安装参数，不拦截布局请求，不跳过重新附着校验。
验证：生产比较函数通过Android36 SDK定向编译；使用AOSP规则解析原文及简化生命周期桩的JVM模型，在LTR/RTL、两侧对齐共40000次比较中，旧规则比较出现40000次假差异，修正后为0。真实锚点、边距、尺寸、对齐侧及alignWithParent变化仍识别；方向切换、空值、类型与原参数未改变检查通过。既有预绘制队列和布局观察器回归通过。模型不等于完整Android运行时，不能量化实机收益。
来源：https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/widget/RelativeLayout.java 。临时脚本 /tmp/check_details_relative_params.py 不入库。未运行Gradle、未构建APK、未操作微信，本次随聚合音乐一并提交推送；实机详情位置、行复用及性能待验收，整体卡顿尚未确认解决。

运行日志页开启日志后，手动点击采集 A/B/C/D 组启动。按钮只标记组别，不自动切换功能。每组最长5分钟，每5秒汇总；点击停止输出最后一份汇总。默认不采集，重启不自动恢复。

保持沉浸背景开启，同一群聊、同一消息范围、同一刷新率和键盘收起状态。先设置悬浮开关，再点击对应采集按钮，返回聊天等待5秒，上下滚动30秒，返回停止并立即导出该组日志。A=输入框开/标题栏开；B=输入框关/标题栏开；C=输入框开/标题栏关；D=输入框关/标题栏关。每组单独导出避免512KiB日志轮转覆盖。最后关闭专项诊断及运行日志，复测最卡的一组作体感对照。

日志标记为 [Hchat:ScrollDiag]，包括组别、会话、悬浮开关实际配置值、原生布局计数、穿透布局应用/恢复/扩展/测量与布局/padding写入、最后一组几何尺寸，以及昵称查询总耗时、微信内部调用、群成员映射和联系人回退耗时。main/worker区分主线程与其他线程；count为次数，totalUs/maxUs为微秒。嵌套耗时不能相加作为总阻塞时间。没有FPS或GPU帧耗时采样，不能仅凭这些计数认定掉帧根因。

昵称重复统计使用本次采集盐值与哈希组合，不保存或输出原始群/成员标识；每5秒最多跟踪256个组合，存在哈希碰撞与超限漏计，因此是线索而非精确业务统计。最多每64次查询采样一次调用来源、每组最多12次，取10个堆栈帧；正式混淆构建可能需要该构建mapping.txt还原。采样不保证覆盖所有调用来源。

专项汇总不记录昵称或消息，但原有运行日志可能含联系人信息，分享前可脱敏。采集器不新增业务查询、不改变布局/昵称查询返回逻辑；启用时仍有计数、计时与少量堆栈成本。停止后释放线程与集合；关闭运行日志页开关也停止专项采集。

验证：Android36 SDK定向编译（项目辅助依赖用桩）、采集器JVM默认关闭/到期停止/隐私及汇总测试、输入区生产逻辑模型回归。未运行Gradle或构建APK；Compose整页和宿主实机效果仍需云端构建与安装验证。


## 穿透布局定向优化（待实机验收）

四组对比中，B/D（悬浮输入框关闭）出现数千次穿透内容扩展，而A/C未执行此路径。此次保留原有扩展与恢复时机，仅缓存经过祖先关系校验的内容区/输入区/列表目标，复用三份屏幕坐标Rect与坐标数组；列表或包装层脱离旧祖先后重新解析。测量尺寸匹配且未请求布局时不重复measure，仍在实际边界变化或重新测量后执行layout。原生滚动布局Hook利用已确认的聊天专用实例作锚点，减少每次两次Activity页面解析；仍检查可见性、背景开关、聊天背景文件和微信版本。

新增underlay.resolveTargets、underlay.actualMeasure、underlay.actualLayout计数，保留原诊断。测量/布局模型新增10000次稳定应用零额外测量布局、原生只改边界不重复测量、包装层替换重新定位测试；原有面板排除、多行输入、窗口变化、底部空间和恢复测试通过。Android36 SDK定向编译通过，项目依赖用桩。没有实机修复后帧耗时，未声称重复原生布局或掉帧已全部消除。

### 单独复测群昵称

先用A组（两项悬浮均开启）减少穿透布局干扰。重启后等待启动任务结束再采集。选有多人历史消息的群，收起键盘，上下滚动相同消息范围30秒，再停留不滚动15秒；停止并导出。另选一个确实已有群内昵称与联系人备注不同的成员，以及没有设置群内昵称的成员，检查现有显示是否符合微信原生规则。不要为制造空结果随意修改真实成员资料。可在另一份日志中切换到另一个群重复，最后用单聊作对照。诊断不保证每个测试成员都走回退分支，以nickname.nativeHit/nativeEmpty、mappingHit/mappingEmpty及contactFallback实际计数为准。

如果要验证昵称变更及时性，只修改你自己的本群昵称或测试联系人的本地备注，记录修改前后时间，并重新进入聊天、滚动旧消息及检查新消息；测试后恢复。此轮没有更改昵称缓存或查询策略，此项主要用于确认显示行为及采集调用来源。正式混淆包的来源还原需要对应构建mapping.txt。


## 第二阶段：请求来源和帧间隔

采集期间临时安装 View.requestLayout 观察 Hook，只统计已登记聊天容器及相关父链（最多32个弱引用），不拦截请求，停止或到期时卸载。框架方法 Hook 本身仍有全局分发开销，须关闭诊断作体感对照。堆栈每128次相关请求最多采一次，每组最多12份、每份14帧。nativeLayout/underlayResize/outside表示请求发生阶段，不是完整调用图。

measureOnly/layoutOnly分别计时；beforeNative/beforeApply/afterApply尺寸快照每类限频250ms，每5秒仅保留最后一条。footer.preDraw/header.apply/background.refresh/background.footer是部分维护耗时，嵌套不可相加，也不覆盖GPU及所有业务Hook。大于等于4ms的调用保留窗口内每操作最后一条时间。

Choreographer在聊天可见时记录frame.interval及20/34/50ms阈值计数。回调间隔不是GPU完成耗时、真实掉帧数或FPS；诊断可能改变性能。

复测A和D：每组清空日志重启，等待启动任务结束，先设置悬浮开关，再手动启动对应采集。返回同一聊天静止10秒、滚动30秒、静止10秒，停止并立即分别导出。关闭专项诊断和普通日志再重复D作体感对照。启动采集后不要重启。群昵称自动同时计数，不必修改成员资料。

本阶段验证：采集器/请求观察辅助类、背景及悬浮相关生产源码通过 Android36 SDK 定向编译（项目依赖与Xposed用桩）；默认关闭/到期清理测试及输入区模型回归通过。请求观察Hook在真实LSPosed下的安装卸载、Choreographer实际采集仍待实机验证，不能以模型测试替代。未运行Gradle或构建APK。


## 标题栏热点保守优化（本地待验收）

A实机专项中header.apply平均约7.8ms、最大28.6ms；C关闭标题栏后的入口快速返回。采样区间/消息内容不同，且C专项在滚动中途到期，不能认定全部帧差异由标题栏造成。

本轮仅缓存按Resources对象、包名和资源名区分的资源ID，Resources用弱引用键，不缓存View、不冻结标题和菜单目标；递归查找改为保持原先深度优先顺序的直接循环，减少Sequence及迭代器分配。内容卡片筛选先做等价尺寸排除，再遍历子树。提示组translationY/elevation相同不重复设置。仍逐次校验原生标题、返回/更多动作、成员数、提示组、顶部空间和位置。没有降频、吞requestLayout或固定高度。

增加header.cards/header.capsule两个默认关闭的子阶段计时，复用既有5秒汇总，以确认下一轮剩余主要开销。现有计时并未证明资源ID解析就是7.8ms的主因，本次仅减少代码明确存在的重复开销，实机改善未知。

D的paddingWrite专项只有1次，不能据此认定穿透代码持续写padding。旧mapping中lm.c等不匹配本次堆栈，不可用于归属hi.o/gj.c0；当前未取得已确认匹配安装构建的mapping，D调用来源未精确还原，本轮未改D布局策略。后续需匹配mapping或更直接的局部来源证据。

验证：Android36 SDK定向编译（标题栏Feature、Capsule及CardVisuals，项目依赖用桩）通过；生产顶部inset模型回归通过；diff检查通过。未运行Gradle、未构建APK、未安装实机验证。本轮保留本地，不提交推送。


## 胶囊路径第二轮优化（本地待验收）
9.2.7实机A组，21:05:42/47两个统计窗口：header.apply 618次、累计1748.423ms、平均2.829ms；header.capsule累计1715.392ms，约占98.1%；header.cards累计9.981ms。系统758帧、超时25.07%、P95 25ms。实际滚动样本约9秒，消息与旧样本不同，不能严格量化改善幅度。
源码核查发现胶囊逐次调用versionName，而WeChatVersionApi.current即使命中cached仍先build完整版本指纹，包含包信息及热更新文件检查。本轮只在胶囊实例缓存非空APK versionName；空值或API未就绪继续重试，销毁清空。APK版本在宿主进程内固定，新安装后须重启；未更改公共版本API的热更新检测策略。此调用是明确重复工作，但尚未单独计时，不能声称已证明占胶囊耗时的具体比例。
菜单递归复用按Resources缓存的fq资源ID，不再每节点getIdentifier；无候选图标时跳过兜底Drawable加载。仍逐次校验菜单唯一性、原生控件、标题与成员数，聊天对象查询、头像更新、深浅色适配和布局更新时机保留。
验证：Android36 SDK定向编译（项目API和Xposed依赖用桩）；提取生产版本缓存逻辑做JVM验证，覆盖API为空、版本为空、后续就绪、10000次复用不重查、不同实例独立；diff检查。实机效果待安装复测，未运行Gradle或构建APK，未提交推送。D布局问题未改动。


## 972构建映射核验与背景布局版本检查优化（本地待验收）
用户提供Hchat-release-mapping-ed3adae2897edf1d9731127720401dbbc195443f.zip，包含mapping.txt。文件名提交与本地HEAD一致，映射的方法和行号可对应972诊断堆栈；未用APK内R8 map-id做额外绑定校验。映射仅存本地/tmp/pl-972-mapping，不加入仓库。
R8类合并/内联后不能只按外层类名归属：hi.o虽然类头为DynamicRecallRuntime，但g的307—321行映射到ChatInputMessageUnderlay.apply原76行，实际是list.setPadding。pi.i#invoke:1112映射到BackgroundBeautyRuntime.installChatChromeLifecycle的lambda原2293行；pi.v#afterHookedMethod:8映射hookOnce原1679行；lm.c#beforeHookedMethod:134是ChatScrollTrace的请求观察器。故该请求来自背景穿透代码，不是防撤回。该样本发生在进入群聊时而非正式滚动区间，paddingWrite少量记录不能解释滚动期间8255次父链请求。
972 D专项三个窗口：原生布局/实际测量/实际布局各2116次，measureOnly累计3.571ms、layoutOnly累计1186.158ms；beforeNative常为measured2637/bounds2834/requested=true。结合beforeLayout只有measuredHeight等于expandedHeight时才恢复，可确认多数快照中原生测量已先恢复2637，不能将所有重复变化归因模块beforeLayout显式恢复。原生布局结束后扩展到2834的策略仍执行，初始requestLayout来源仍未确定。
另发现背景onLayout后置兼容检查逐次调用WeChatVersionApi.current()；API即使缓存命中也先build完整包/热更新指纹。该调用不在underlay.layoutOnly内。本轮为聊天onLayout和applyChatChromeRoot两处兼容判断复用非空APK versionName；空值/API未就绪重试。缓存只用于APK版本门槛，不替代公共API的热更新检测。未改布局、padding、键盘、面板或尺寸恢复策略，未声明重复布局根因已解决。
验证范围：生产版本缓存片段JVM检查（未就绪/空值重试、10000次不重查、实例独立）；原穿透生产模型回归；Android36 SDK穿透类定向编译（项目辅助依赖用桩）。背景整文件未全量编译，实机收益和重复请求来源待下一轮验证。未运行Gradle、未构建APK，本轮保留本地不提交推送。


## 973后请求来源采样修正（本地待验收）
973的12份请求样本均在前14帧内被框架Hook和父层requestLayout传播占满，无法确定初始业务调用者；额度在滚动刚开始即耗尽。现有证据不足以修改穿透尺寸策略。
本轮只调整ChatScrollTrace：每个系统elapsedRealtime/5000时间桶至多一份来源、总额60份；保留每128个候选请求才采一次的门槛（时间桶已用额度时不递增候选计数）。检查堆栈前96帧，省略已知Xposed命名空间、观察器自身及android/androidx的requestLayout传播，保留最多24帧，输出request.source。混淆桥接帧可能保留，仍需对应新构建mapping；最多96帧之外的来源仍可能遗漏。采样不保证覆盖所有调用原因，也不区分原始请求与父链传播。
旧12份/14帧说明由此节覆盖。观察Hook的范围、计数、停止卸载机制不变，不拦截请求；关闭专项不采样。每5秒桶通常仅1次深堆栈，桶边界可短时间内出现两次，全会话至多60次。相比旧版单份堆栈更深、总额度更多，仍须无诊断作性能对照。未修改穿透布局、padding、键盘面板策略。
验证：Android36 SDK定向编译（项目/Xposed辅助依赖用桩）；生产过滤片段JVM测试覆盖45层父链后业务调用者、24帧输出上限及生产限额逻辑；实机来源采集仍待安装。未运行Gradle或构建APK，未提交推送。
下一轮保持D配置，重启等待初始化后立即启动D专项，在5分钟内完成约30秒滚动。读取request.source并用该构建mapping还原；若仍只有父链，则再选择系统跟踪或更具体目标Hook，不猜测更改布局策略。


## 974映射定位：语音子树反馈策略反复切换（本地待验收）
映射包Hchat-release-mapping-9df8c5deffae9ed6eda99dde81c4459cd38774ee.zip的方法/行号匹配974诊断；未额外校验APK R8 map-id。映射仅保存在/tmp/pl-974-mapping，不入库。
pi.z#w0:1013还原setTransparentSurface原2822行foreground写入；pi.z#i:68还原applySpeechSurfaces原2063行；pi.z#c:53还原applyChatFooterTransparency原2396行；pi.k#invoke:582还原installChatChromeLifecycle lambda原2312行。
源码确认同次页脚处理先applySpeechSurfaces(preserveFeedback=true)，后通用遍历把语音匿名View设为false、语音ImageView设为!inSpeech=false、非appGrid容器设为false。对未被footerHost屏蔽的普通/图片叶节点，反馈背景与透明背景每轮互换，setTransparentSurface幂等检查因类型不匹配而失效，重新写background及foreground，setForeground可触发新的布局请求。此次现场多份来源直接落在前一遍写入，支持此反馈循环路径；尚不证明所有布局均由此引起。
修复：applySpeechSurfaces与通用页脚遍历统一为不单独添加反馈；保留现有透明标记身份检查、外部背景同步和恢复机制。不改穿透高度/padding/键盘面板布局，不改非语音按钮及appGrid反馈。
验证：生产反馈判断片段+策略断言JVM模型对照，旧叶节点100轮200次写入、统一后10000轮仅首次写入，宿主屏蔽稳定、外部替换重新应用、非语音反馈保留；此为模型而非Android布局实测。Android36 SDK定向编译背景生产代码（项目/Xposed辅助依赖用桩），未运行Gradle、未构建APK。实机待安装后复测D：先观察静止重复请求，再固定8轮往返，兼查文字/语音模式及加号面板。

## 布局来源与长帧关联补充

982滚动窗口有39次outside布局请求但未留下来源，原因是旧采样同时要求每窗口最多一次与每累计128次请求一次。现移除128次间隔，主线程受观察视图每个5秒时段首个请求采栈，整次专项仍最多60条，停止后解除Hook。

新增scroll.nativeLayoutDuration，范围是ChattingScrollLayout原生onLayout的before/after之间，不含beforeLayout恢复与after中的underlay扩展。每次诊断end仅对主线程四类阶段（原生布局、underlay.apply、underlay.measureLayout、background.footer）保留最近16条完成记录，含elapsedRealtime起止时间和耗时；每次Choreographer回调后清空，溢出丢弃最旧条目并计数。>=34ms时输出frame.longContext，记录该回调前完成的阶段及最近布局请求时间和phase，无聊天内容和视图引用。

回调帧时间使用Choreographer time，阶段时间使用elapsedRealtime；关联以回调观察时间和阶段起止时间判断邻近，不能等同GPU耗时、逐帧CPU追踪或因果证明。嵌套阶段耗时重叠不可直接累加。汇总窗口仅保留最新长帧及上下文，两者成对覆盖；来源采样固定时段和日志汇总边界不完全一致。无阶段时输出空记录，不能理解成没有其他主线程工作。专项关闭后不采样、不读取阶段时钟、不改变业务布局；本次未改变背景、面板或消息发送行为。

## 983原生回弹与背景补回核查

8.0.78本地classes16.dex反编译WeUIBounceViewV2.a(View,int)：零偏移调用G.setBackground(null)，正负方向首次变化分别使用J/L、K/M；N避免同方向重复赋值，偏移归零不受此保护。setView创建NestedScrollView并赋给G。MMWeUIBounceView.a调用super；其requestLayout会向祖先传播，因此日志target=MMWeUIBounceView不意味着背景写在该对象本身。

模块installBackgroundWriteHook在原生setBackgroundDrawable/setBackgroundColor之后，只有已纳入surfaceOwners、有snapshot和appliedBackground、模块开关开启且所属slot有有效已加载位图时，才补回原applied背景；surfaceMutationDepth阻止自身递归，不会无限互相调用，但原生更新一次后可能增加一次模块赋值。applyNativeChrome对CHAT且非ActionBarContainer会提前返回，因此不能假定聊天已通过回弹颜色属性全量透明化。983原生栈中未出现模块补回，不足以证明补回没有执行（after Hook在原生requestLayout之后）。背景切换验收正常不等于确认或排除该额外成本。

新增background.repair计时：只覆盖现有after Hook满足条件后的透明补回，finally退出backgroundRepair布局请求phase、恢复重入深度；每汇总窗口保留最新目标类/slot/时间，无内容和视图引用。长帧上下文纳入该阶段，仍最多16条、每回调清空。它和setTransparentSurface写入计数分别表示不同入口，不相加视为完整写入次数。仅专项开启时采样，不禁用原生回弹，不抑制requestLayout，不改变恢复配置的逻辑。

依据仅覆盖8.0.78本地逆向与983现场；未新增内部Hook、字段或反射签名，未扩展其他版本结论。下一轮检查background.repair及requestLayout.backgroundRepair，再决定是否缩小修复范围；不能凭单次原生回弹栈直接屏蔽其背景更新。

## 系统帧时间线首轮

采集Perfetto-20261024-131132，12秒、24741923字节，正式8轮滑动13:11:36—13:11:41，已返回Operit。采集linux.ftrace调度、gfx/view/input/dalvik、微信atrace及SurfaceFlinger FrameTimeline；解析stats无非零error项。系统帧257、超时124（48.25%）、P95/P99=53/65ms。此为系统追踪场景，开销、消息位置及诊断启用状态不与D组严格匹配，不用于宣称回归。

trace进程16021的主线程utid812、RenderThread16122。主线程MOVE分发最长79.82ms，其中Running60.72ms、Runnable1.01ms，剩余为其他线程状态；另4条52.11—52.93ms MOVE分发中Running51.83—52.49ms。支持触摸分发路径存在显著CPU工作，不支持将这些片段完全解释成调度等待。嵌套input/View#onTouchEvent不可相加，完整trace含滚动前后及返回生命周期；FrameTimeline应用截止错过和Buffer Stuffing分类并存，不把全trace分类当正式滚动帧比例。trace不含Java采样调用栈，尚不能归因到微信或模块的具体方法。后续优先在MOVE事件长片段内用调用栈采样或更小范围trace查耗时，而非继续增添背景计数。原始trace及SQL输出保存在设备采集目录和本机/tmp，未入库。

## 985两轮调用栈热点归属核查

采样Stacks-20261024-134104与StacksRepeat-20261024-134549：主线程99Hz、8秒，分别406/340样本，丢失0，PID17547/25308。两轮包含滑动前后与返回生命周期，未按精确滑动时间筛选，不是单次52ms触摸片段的采样归因。内存DEX解析均报474条Invalid dex file header，数量不等于缺失样本数，不能由unknown占比估计Java栈完整率。

本地微信8.0.78 classes11.dex单类反编译确认lj.h实现android.util.Printer，println是Matrix.LooperMonitor分发监听器，向有效监听器调用dispatch begin/end；pj.t属于Matrix.AnrTracer，c(String)建立AppMethodBeat索引并向Handler投递5000/2000ms检查，b取消检查。因此lj.h.println两轮含子调用4.43%/4.71%不是Hchat普通日志；不能依据名字关闭HLog或宿主ANR监控。

classes14.dex确认n.m实现Window.Callback，dispatchTouchEvent仅委托内部callback。样本按DSO分组明确该方法来自微信base.vdex。旧983/984模块mapping里n.m对应Compose、lj.h对应GroupLeaveMonitor的同名混淆类，均不能跨APK名称直接归属；尤其当前985没有对应mapping附件，禁止以旧表套解当前JIT符号。

classes11.dex确认c9是ChattingUIFragment列表监听器：onScrolled调用聊天组件p0、可选可见区监听、检查ChattingUIFragment.l1与加载条件，必要时触发加载更多。其5.88%—8.13%含子调用，不是该方法自身开销，不能跳过以减少样本。

模块ModernXposedRuntime桥接before/proceed/after，原方法继续执行也在intercept子调用统计范围。gm.a.intercept两轮10.84%/11.76%不能解释为模块独占开销；未完成985精确符号表校验前只作为Hook分发线索。样本还包含匿名内存DEX和适配器绑定调用，无法分解各模块callback。

源码检查发现ScriptSendButtonHook全局View.dispatchTouchEvent维护输入会话与长按，包含同步WeakHashMap查询；HchatExtraFeature全局ViewGroup触摸Hook只处理ACTION_DOWN并检查滚动容器；背景列表触摸处理另按已登记列表维护反馈。它们是进一步计时候选，不是已证实热点。本轮只记录归属，不新增全局Hook、不禁用触摸功能或Matrix、不修改运行时。下一步需获取985对应ee19a52的mapping，并对正式滑动时间筛选样本；未知DEX需补符号或针对现有回调做限量计时，才判断是否优化。

## 985精确mapping补核

已收到ee19a52410fba3f52b687627286e19380fef8b0a mapping，SHA256见本机/tmp/pl-985-mapping校验输出。对应模块gm.a.intercept为ModernXposedRuntime桥接；lj.h虽与GroupLeaveMonitor合成类重名，但模块表不存在println方法，结合原生DEX确认样本仍属微信Matrix。解码工具必须校验类+方法并核对DSO，不能仅按类名替换；JIT app cache不足以单独确定APK归属。R8内联映射无PC/行号时，只列外层及候选，不将全部候选当实际调用。

两轮明确模块线索：fl.i.beforeHookedMethod对应QuickMarkReadDragController安装回调（含子调用1.97%/2.06%），fl.j.e对应其traverse（1.72%/1.76%）；wi.n.onPreDraw对应底栏预绘制合成回调（2.71%/2.35%，合并/内联存在多候选）；oj.s0.onPreDraw对应消息详情预绘制队列（1.72%/1.18%）。这些是全窗口统计，未证明全部发生于正式滑动。

QuickMarkReadDragController仅以Activity类名LauncherUI判断首页；嵌入式聊天共用LauncherUI。ACTION_DOWN调用startPendingSession，先扫描设置tag、悬浮底栏、原生底栏；findBottomNavContainer使用traverse递归全树，traverse未跳过不可见分支。此为源码确认的聊天触摸下可能触发首页搜索的范围缺口，能解释样本中的遍历，不足以解释全部约52ms工作。后续修正应先限定首页可交互底栏范围并避免扫描聊天子树，保留原生/悬浮底栏拖拽命中、取消和恢复；不依赖可能滞后的聊天talker值，不用全树扫描判断聊天来抵消优化。

本轮未修改运行代码，未构建、未操作微信，mapping及分析输出未入仓库；归属核查已完成，不需要再次滚动以重复确认同一统计。


### 2026-10-24：快捷已读触摸边界与首页标题提前恢复修正

- 延续 985 mapping 核查结果，删除 `findBottomNavContainer` 的 DecorView 全树遍历、底部位置猜测和全树文字收集。ACTION_DOWN 改为沿触点所在的可见分支查找已确认的 `LauncherUIBottomTabView` 或模块悬浮底栏；按 Z 与同层倒序检查，前景聊天、设置页和遮挡层命中后不再搜索背后的首页兄弟分支。角标搜索只发生在已命中的底栏内，排除隐藏和透明节点。
- 保留拖拽启动、移动、结束、取消及原角标恢复代码；新 DOWN 先清理旧会话。不读取聊天 talker，也不通过额外全树扫描判断聊天。悬浮底栏快照由窗口坐标转换为屏幕坐标后检查首页四分之一区域，避免非零窗口偏移造成误命中。
- 用户视频为 `Screen_Recording_20261024_153014_WeChat.mp4`，约 7.04 秒；抽帧 OCR 在约 3.5 秒附近捕获到首页原生“微信(255)”后进入聊天，前后多次显示头像昵称首页。视频只能证实短暂闪回，不能独立证明重启后 30 秒的触发阈值。
- 源码证据：`WeChatChatPageApi.hookStartChattingMethod` 在原生启动方法 beforeHookedMethod 发 ENTER，且 API 的 Dex Hook 可后续补装；`HomeSidePanelRuntime.setChatVisible(true)` 原先立即隐藏自定义首页标题并恢复原生标题。因此异步补装完成后出现、冷启动早期不出现，与该时序缺口一致；没有把 30 秒写成延迟或修复常量。
- 修正仅在侧边栏本地消费层：ENTER 后关闭首页交互，但若已绑定标题栏仍为精确的首页标题（微信、微信(数字)、微信（数字+）），继续保留头像昵称及动态效果；该标题实际变化为聊天标题后，在布局/预绘制时交还原生标题。同一 Activity 内的独立首页标题栏继续归首页，不按 ENTER 提前恢复。预绘制只在聊天事件已到且首页头部仍显示时检查既有标题子树，销毁时移除监听。
- 验证：`scripts/test_home_title_quickread_scope.py` 执行生产触摸搜索与标题判定，覆盖原生/悬浮底栏、含一万节点的聊天遮挡、隐藏/透明层、Z 顺序、设置遮挡、提前 ENTER、真实标题交接及开关关闭；背景 chrome 归属回归通过。两处改动运行代码已对 Android 36 SDK 做定向 Kotlin 编译（外围宿主 API 使用桩），未执行 Gradle、未构建 APK、未操作微信。
- 限制：当前证据与修正针对微信 8.0.78；未做其它微信版本的 APK 横向复核。用户安装 ed4c65b 对应新版后反复测试，明确确认首页标题闪回修复成功，记为实机验收通过；快捷已读拖拽行为与性能变化尚未验收，不能据此宣称全部长帧解决或量化性能收益。


### 快捷已读无响应回归修正

用户反复实测确认 ed4c65b 版本原生和悬浮底栏的未读角标上拖均无反应，快捷已读验收失败。上次模拟用例错误地把所有可见前景容器当成触摸遮挡，没有覆盖被动装饰层，不能作为实际拖拽通过的证据。

源码确认 `findBottomBarAt` 对第一个可见且包含触点的子树直接返回，即使子树没有底栏且不消费触摸，也不继续同层搜索。现对命中结果区分底栏、真实阻挡和被动层；被动层查找为空时继续后面的触点分支。已确认的 ChattingUILayout 和模块设置根阻止向后搜索，可点击/长按控件也作为阻挡；仍排除隐藏/透明/不含触点的节点，不恢复全树文字搜索。该缺陷可解释两种底栏同时失效，但未经新包装机不能断言不存在其它命中问题。

回归新增全屏被动容器及其子视图覆盖原生/悬浮底栏的用例，以及可点击/长按遮挡；保留聊天一万节点隔离、Z 顺序和首页标题交接测试。定向 Android SDK 编译使用宿主辅助桩；未运行 Gradle、未构建 APK、未操作微信。首页标题闪回仍为用户确认通过，不改动其运行代码。

### 9ee89f7 最新基准符号核对与后续优化

以 StacksBaseline-20261024-192339 为准：99Hz 约8秒、284样本、丢失0；往返滑动集中在19:23:40—19:23:43，包含滑动前后样本，不是纯滑动耗时测试。对应 mapping 核对快捷已读 fl.i.beforeHookedMethod 含子调用0.32%，部分搜索内联，不能以无独立遍历符号证明成本为零，也未证明整体性能提升。lj.h.println 3.53% 不能仅凭混淆名认定微信Matrix，模块同名类没有该方法，归属待确认。

聊天时间 resolveNativeMessage 优先候选失败后，原备用分支再次访问相同参数；备用扫描现排除已检查的参数，保留独立深度预算，避免共享 visited 导致深度限制下漏查。隐藏模式仍读取时间，以保留设置即时切换至自定义时间时的完整绑定数据。

消息详情主路径已有文本、颜色、字号与边距比较，不盲目新增消息对象缓存；可变消息对象和布局复用要求每次绑定核对状态。合并转发详情标签路径补齐 alpha、字体内边距、字号和 padding 比较，减少相同值 setter。现有预绘制队列、卡片锚点和绑定生命周期不变。未进行新版性能对照，不能量化收益。

### 聊天页底栏与艾特类型重复解析修正

悬浮底栏可见条件增加公共 chatPage.isInChatPage 判断，聊天页不再保留底栏内容组合，隐藏时跳过syncState；返回首页重新创建内容，保留宿主控件层、原点击目标及状态对象。实际恢复时序和是否存在标题过渡影响仍需实机验证，未以本地检查替代性能复采。

消息详情在不可变MessageDetails快照内记录艾特分类及当前账号；同一快照多个字段复用结果，账号变化重新分类，新快照不继承缓存。不缓存可变原生消息对象，不按msgId跨绑定保留旧分类。新增快照复用/账号变化/新消息隔离测试通过。艾特提醒绑定处理在关闭时立即退出，避免额外遍历。

## 悬浮窗尺寸缓存与聊天时间复用（2026-10-24）
- 本轮核查确认：输入区穿透控制器在原生 onLayout 前，仅按测量高度判断是否撤销模块扩展，未校验测量宽度。新窗口的原生测量若恰好等于旧扩展高度，会误恢复旧尺寸。本次增加宽高联合校验，并在内容宽度、位置改变时更新原生几何缓存。仍按当前输入行计算 overlap，不新增固定底部高度或强制滚到最新的调用。
- 这是源码确认的尺寸缓存缺陷修正，不代表已确认用户截图留白的唯一根因；全屏→悬浮窗→全屏、再次进入聊天及历史消息位置保持仍待微信 8.0.78 实机验收。本轮未新增宿主内部入口，未做其它版本横向验证。
- 聊天时间原先在 isNativeMessage 中读取创建时间，绑定时再次读取；现改为解析直接返回已验证的创建时间，保留消息 ID 校验、递归边界和原有字段/方法回退，不做跨绑定消息缓存。
- scripts/test_chat_input_underlay.py 执行生产控制器模型回归，新增窗口宽度切换且测量高度与旧扩展高度重合、宽度单独变化场景；原有重复布局、面板排除、输入行高度、padding 恢复测试通过。
- scripts/test_chat_time_resolution.py 执行生产解析方法，计数验证创建时间每次解析读取一次，覆盖包装对象、集合、无效 ID/时间和可变消息重绑。bindTime 通过 Android SDK 定向编译。未运行 Gradle、未构建 APK、未操作微信、未重新采样；实际性能收益未知。

## 85a8a50 实机未修复后的底部锚点补偿
用户反馈全屏与悬浮窗下末条消息仍偏高，故上一轮仅几何缓存修正不足，不记为实机修复通过。新版 mapping 已保存至采样目录 mapping-85a8a50.txt；没有新增采样，也不能用 mapping 判断运行时几何。

进一步静态核对微信 8.0.78 解码产物：ChattingContent 继承 FrameLayout，onMeasure/onLayout 直接调用父实现；ChattingRecyclerView 继承 RecyclerView，onLayout 调用父实现；MMChattingListView.getBottomSpace 按列表高度、paddingBottom、末个子项 bottom 及末项可见位置计算；ChattingScrollLayout.onLayout 内调用原生内容平移更新，发生于模块 after-layout 扩展之前。相关文件位于用户已有 wechat8078_decoded 的 smali_classes2、smali_classes10、smali_classes11，RecyclerView.scrollBy(II) 在 smali_classes17 中确认是实际列表滚动而非普通 View.scrollY。

控制器此前恢复原生高度再扩展内容，会让真实列表经历两次布局；原测试 View 模型没有传播内容尺寸到列表，未覆盖末条消息滚动锚点。这次补充传播布局的列表模型，85a8a50 原生产控制器在重复原生布局后的贴底断言失败，新控制器通过。这说明已复现一条可导致上移的模块路径，不代表已证明截图中所有留白均来自该路径。

修正：原生布局前仅记录原本处于末项、可向上滚动但不能继续向下滚动且末项距离有效底边约 0px 的状态；扩展完成后，消息总数与末项位置未变时，按实际几何差值做同步 scrollBy 补偿。查看历史、短列表、数据数量变化不补偿；没有 setSelection、滚到新消息或延迟跳转。已有异常处理由调用处恢复布局并使用 HLog.e 记录。

验证：scripts/test_chat_input_underlay.py 新增真实尺寸传播模型，覆盖重复布局、窗口缩小/放大、历史、短列表、数据数量变化；旧版失败、新版通过，原有几何回归继续通过；完整控制器通过 Android SDK 36 定向编译。仅核对目标 8.0.78，未运行 Gradle、未构建 APK、未操作微信，实机和性能收益均待确认。本轮不改聊天时间或已验收功能。
