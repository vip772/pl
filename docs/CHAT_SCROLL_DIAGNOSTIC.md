# 聊天滚动专项诊断

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
