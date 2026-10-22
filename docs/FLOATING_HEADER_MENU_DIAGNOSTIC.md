# 8.0.78 三段式标题栏菜单取证

12:23日志确认安装成功、外层重排成功；三段式失败原因是more_menu_missing，collectMenuActions得到0个候选。现有筛选要求完整类名WXActionMenuView、VISIBLE、enabled和clickable；日志未包含实际菜单子树，不能确定应更改哪条筛选或实际点击入口。

保持原生动作识别、点击绑定及单条胶囊回退逻辑。新增HEADER_MENU_DIAG，在more_candidates原有限额内最多输出3次，以header为根记录最多80个节点、深度最多10层，含菜单容器、类名/ID、尺寸/位置、可见/点击/监听器、alpha和布局参数、图标类型。不会输出标题或联系人文本，也不新增逐帧遍历。

复测：安装本提交对应版本并重启微信，保持悬浮标题栏开启，进入已展示问题的聊天页，等待标题栏出现后导出日志。根据HEADER_MENU_DIAG确定coy内真正动作控件及点击入口后修复；此次没有把未确认的按钮当作更多。

验证：输入控制器及标题胶囊分别使用真实Android 36 SDK编译（项目API辅助类桩）；输入栏模型回归、8.0.78绑定静态检查、diff检查通过。未运行完整APK构建或实机绘制测试。

## 定向修复
12:41日志的3次完整子树均无WXActionMenuView，coy内实际可见动作是WeImageView#fq，clickable/listeners均为true。原生ha.smali在fq分支将其保存到U，并在cond_37/goto_11后设置菜单监听器、enabled和fb.h内容描述；fg注册的聊天设置菜单使用string/cm及raw/actionbar_setting_icon。

收集器在coy范围内增加精确类名+fq资源ID支持，要求有点击监听器；继续校验设置文案或图标，歧义时保持回退，不以唯一可点击控件直接猜选。图标读取兼容候选本身为ImageView。右侧三段式入口继续调用原生fq.performClick，原生层级、ID和监听器均保留。原WXActionMenuView路径保留。保留限量失败诊断便于实机验收，成功路径不输出菜单树。

通过真实Android 36 SDK编译标题胶囊、原生绑定静态证据检查、输入栏模型回归及diff检查；完整APK由GitHub构建，三段式显示及返回/标题/更多点击由实机验收。

## 12:57 后续识别门槛修复
日志候选数从0变为1，但设置语义过滤仍失败；实际图标为th5.d/th5.c包装，现有文案及constantState路径均未命中。此前只修正候选收集不足以解决整个问题。
ha.i(IILjava/lang/String;Z...)明确将p2图标资源写入fb.b；菜单创建时将fb.b通过setTag写入fq的id/nwr（0x7f0a6816）。增加精确fq类名+ID+原生nwr标签等于raw/actionbar_setting_icon的校验路径，不以单候选直接放行。失败树增加整数iconTag及资源名称，不记录联系人文本。保留原文案/图标兼容路径。
图2所需三段结构已经由create提供独立返回、头像标题、更多三个表面；本次修正阻止create执行的第二层校验，不宣称群成员/在线数副标题已实现（微信当前可见原生标题数据之外不编造在线人数）。

## 13:10 实机资源分支确认
3条诊断一致：fq.nwr=2131820562（0x7f110012），资源名raw/actionbar_icon_dark_more。此前只允许actionbar_setting_icon导致验证拒绝。fg的goto_9群聊分支明确以0x7f110012、chatroom调用Z0；新增该精确资源标签白名单，保留设置分支。静态检查新增群聊分支证据，避免只验证设置路径。成功时每个胶囊State输出一次HEADER_SPLIT_APPLIED，含容器尺寸及段数；这表示apply执行成功，不等价于实机像素验收。

## 三段启用后的滚动性能
13:45日志确认三段启用，floating_header主线程慢调用18～23ms（采样限3次、仅启动3分钟，不能用于统计总频率）。源码onLayout调度preDraw apply，apply无条件row/header/group.bringToFront，可能由requestLayout形成重复布局；缓存头像每次setImageBitmap，描边及卡片背景每次重写；visibleChatLayout每次根树扫描。
修正：row/header仅非末尾时置顶；公告组仅首次定位置顶，避免与header反复交换顺序；头像同一bitmap不重设；描边/卡片背景/位移/公告margin仅值变化时更新；当前source可见时优先使用，不扫描整树；状态区仅首次或背景被外部重设时调用颜色setter。生命周期和数据更新路径保留，不靠固定延时限频。三个生产文件共同通过真实Android36 SDK编译（项目API桩），静态绑定检查通过；未测实机帧率，不宣称已量化改善或排除其他瓶颈。
