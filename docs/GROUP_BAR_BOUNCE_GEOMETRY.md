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
