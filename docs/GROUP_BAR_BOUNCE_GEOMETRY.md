# 分组横栏回弹遮挡与背景几何冲突

基线33b5616，目标微信8.0.78。用户反馈任意分组上滑回弹后横栏遮挡第一行，部分情况下横栏位置异常，下拉恢复；冷启动偶发相同遮挡。

## 源码依据

GroupBarRuntime此前只增加s/v2和TaskBar控制器n的回顶偏移，没有给ListView实际padding边界预留横栏高度，滚动到顶时仍能进入横栏下面。微信ConversationListView.setSelection与onLayout首次布局直接调用AbsListView.setSelectionFromTop，实际位置还会叠加ListView padding，新增预留必须补偿这些原生入口。

横栏positionBar使用窗口坐标，原先只扣标题链translationY，未统一扣除父容器链位移；标题与列表在不同分支时，父容器回弹能污染stableTitleTop。

BackgroundBeautyRuntime.restoreSurfaceGeometry每次背景重设或恢复时会回放快照padding，旧代码仅排除聊天列表，未排除首页两种会话列表。这个几何所有权冲突已通过提取实际生产方法的夹具重现；不把截图当成具体现场方法命中的运行证据。

## 修改

- ListView物理paddingTop增加横栏预留，和原有s/controller.n保持一致；按差量添加/移除，不累计。现代RecyclerView不套用未经验证的ListView padding语义。
- AbsListView.setSelectionFromTop仅对已登记分组Session、传入offset等于当前原生s的调用扣除新增预留，覆盖宿主首次布局的父类直接调用。模块自身已减padding的定位保持原值，不修改position索引。
- 标题和父容器分别扣除整条祖先translationY，回到同一静态坐标系；不在滚动中强制回顶。
- 背景透明处理保留首页ListView/RecyclerView的实时padding，关闭背景也不回放旧值。分组关闭/销毁由分组自身移除预留。
- 保留上一轮工作区的8.0.78下拉遮层修复，一并交付；无Gradle/APK构建。

## 验证及边界

生产分组运行时Android SDK编译、原生偏移/TaskBar一致性与父类回顶补偿、首次布局等待/稳定帧、手势取消及数据重载、背景padding归属和共同/独立父容器位移夹具通过。空白页脚、下拉遮层及背景chrome范围回归通过。git diff --check通过。

上述为源码及受控夹具验证；未在新APK中实测硬件回弹、首次启动和小程序开合，不能宣称所有现场问题已经验收。缺少其它版本对应APK证据，不宣称跨版本验证。


## 现场截图后的补充修复

用户新截图显示首行实际绘制到横栏上方，故仅调整列表偏移不足以覆盖回弹后的层级变化。新增ensureBarOnTop，在每次pre-draw、横栏渲染和原生头部状态变化时校正兄弟顺序，并设置最小translationZ/elevation；不改变横栏内容、触摸和滚动。

这次仍未取得现场数值日志，但截图与“下拉可恢复、再次上滑复现”符合回弹/布局后兄弟层级被宿主重排的条件。新增层级夹具及现有分组、背景回归通过。

## 2026-11-04 分组透底反馈后的修正（取代物理 padding 方案）
用户明确区分：c9932b0 开启横栏后全部分组滚动均能透底，当前只有部分分组可以；前三次按状态区透明方向的修改无效。本轮以此为问题定义，不再追加状态遮罩修复。

对比确定 9b96f3b 在 c9932b0 的原生 s/v2、TaskBar.n 偏移之外，另给 ListView 增加 reserveHeight + floatingInset 的 paddingTop，并 Hook AbsListView.setSelectionFromTop 按 offset == s 条件减去该值。微信 8.0.78 原始 smali 的 C() 重算 s 并复制 n；onScroll 在头部依据条目 top、头索引和 s 计算 I，再设置标题 translationY。列表实际 padding 参与 Android 布局和裁剪，而条件式 selection 补偿只覆盖一种定位参数，不能让所有滚动、短结果与原生重排路径等价于旧版。不同分组共用 Session，但筛选结果改变条目数与顶部布局，故应核查分组几何，不能再把部分组失效解释为窗口系统栏颜色。

撤销分组物理 padding、appliedPaddingInset、nativeSelectionOffset 及其 AbsListView Hook；保留 c9932b0 原生偏移协调与切组新数据回位。八个生产方法经去注释/空白的静态比对与 c9932b0 一致。后续横栏置顶、父链坐标修正、联系人队列和资源清理保留；背景实时 padding 所有权保护保留，不回退无关功能。

按逆序安全撤销 6bd538a、8338c4d、f56967c；BackgroundBeautyFeature、CustomBottomBarFeature 和原状态遮罩文档逐字恢复到前三次修改前的 46f9ec6，移除 6bd538a 的父链文档与专用检查。原有更早的状态遮罩实现不删除。

验证：check_group_bar_viewport_baseline.py、check_group_bar_integration.py 和 git diff --check 通过。集成检查直接读取已解码的 8.0.78 smali；修正了该脚本中早于当前生命周期改造的 candidate.install 断言，改为检查当前 attempt/ticket 与关闭路径。更新原偏移 JVM 夹具以符合不改 padding 的逻辑，但遵守只做静态检查，本轮不执行 Kotlin/JVM 编译、SDK 编译、Gradle 或 APK 构建。

边界：以上确认源码回归点与恢复范围，没有本轮修复包的实机视图树、各组逐帧结果或滚动性能数据；不能声称唯一遮挡贡献层已实测或所有分组已验收。需覆盖全部/好友/群聊/服务号/未读/自定义组，含空、短、长结果，上下滚动、顶部回弹、切组及小程序收起；同时复查首行与横栏遮挡。只核实 8.0.78。


## 2026-11-04 固定横栏与短列表页脚修正（未提交）

基线：`8cd489cbbedc65f7e7e350708d1d25b827340367`，工作分支 `dev`。用户要求横栏在上滑到底及回弹期间固定，回弹后的首会话在横栏下面；本轮明确禁止提交和推送。

### 原生证据与修正范围

微信 8.0.78 `ConversationListView.g(IIZ)F` 的剩余高度计算为视口高度减会话高度、原生 action bar 和状态区高度，不读取已经加上横栏预留的 `s`。`h()` 消费该结果，区分长短结果并设置空白页脚高度。因此单独增加 `s`/控制器 `n`，无法让短结果的原生页脚计算包含横栏预留。现代列表 `j1(IIZ)F` 同样只扣原生 action bar 与 `G2`，经 `k1()/m1()` 交给 `i1(FZ)` 设置页脚，未使用最终 `v2`。

本次分别在这两个已确认的计算方法返回后，对已登记 Session 的结果扣除 `appliedScrollInset`。关闭时预留为零，保持原值；不改原生列表 padding、裁剪、索引、selection 参数或八个既有偏移协调方法。首次布局及原生后续布局会执行 `h()/m1()`，切组仍沿既有新数据页脚协调路径执行。

横栏现有定位只计算静态父容器 margin，不能单凭该值保证父链变化后的窗口坐标。本轮记录去除标题链 translation 的窗口底边，在既有 pre-draw 回调中比较横栏窗口坐标，仅在存在差值时调整横栏自身 translationY。复用 `IntArray(2)`；不添加定时器，不逐帧搜索视图、反射、重算页脚或回顶。该路径使用 `getLocationInWindow`，其内部父链坐标计算仍有开销，未测量实机耗时。未取得截图现场的父链位移数值，不能将某个宿主动画声明为已证实的现场唯一根因。

### 验证

九项检查通过：`check_group_bar_sdk.py`、`test_group_bar_fixed_geometry.py`、`test_group_bar_native_offset.py`、`test_group_bar_first_layout.py`、`test_group_bar_background_geometry.py`、`test_group_bar_gesture_lifecycle.py`、`test_groupbar_install_lifecycle.py`、`check_group_bar_viewport_baseline.py`、`check_group_bar_integration.py`。

新增夹具读取两种原生 smali 契约，并执行生产固定位置/页脚修正方法：189 组几何组合、上下位移与归位、隐藏/未定位/坐标更新/关闭、5000 稳定帧无位置写入。页脚布局部分为按 smali 建模的受控模型，不是 Android ListView 实际布局执行。SDK 检查使用真实 Android API 和项目服务桩；原测试桩缺失当前已有的 `trackResource`，已补齐。证据摘要见 `GROUP_BAR_FIXED_GEOMETRY_20261104.json`。

边界：没有 Gradle、APK/R8 构建或新包实机验收，没有滚动帧耗时数据；只能确认源码修复与上述受控验证完成。当前仅有 8.0.78 解码证据，未验证其他版本。实机仍须覆盖空/短/长分组的上下滑动、到底回弹、首行位置、切组、小程序开合及横竖屏。全部改动保留在工作区，不提交、不推送。
