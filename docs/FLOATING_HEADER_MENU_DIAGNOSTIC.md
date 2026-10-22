# 8.0.78 三段式标题栏菜单取证

12:23日志确认安装成功、外层重排成功；三段式失败原因是more_menu_missing，collectMenuActions得到0个候选。现有筛选要求完整类名WXActionMenuView、VISIBLE、enabled和clickable；日志未包含实际菜单子树，不能确定应更改哪条筛选或实际点击入口。

保持原生动作识别、点击绑定及单条胶囊回退逻辑。新增HEADER_MENU_DIAG，在more_candidates原有限额内最多输出3次，以header为根记录最多80个节点、深度最多10层，含菜单容器、类名/ID、尺寸/位置、可见/点击/监听器、alpha和布局参数、图标类型。不会输出标题或联系人文本，也不新增逐帧遍历。

复测：安装本提交对应版本并重启微信，保持悬浮标题栏开启，进入已展示问题的聊天页，等待标题栏出现后导出日志。根据HEADER_MENU_DIAG确定coy内真正动作控件及点击入口后修复；此次没有把未确认的按钮当作更多。

验证：输入控制器及标题胶囊分别使用真实Android 36 SDK编译（项目API辅助类桩）；输入栏模型回归、8.0.78绑定静态检查、diff检查通过。未运行完整APK构建或实机绘制测试。

## 定向修复
12:41日志的3次完整子树均无WXActionMenuView，coy内实际可见动作是WeImageView#fq，clickable/listeners均为true。原生ha.smali在fq分支将其保存到U，并在cond_37/goto_11后设置菜单监听器、enabled和fb.h内容描述；fg注册的聊天设置菜单使用string/cm及raw/actionbar_setting_icon。

收集器在coy范围内增加精确类名+fq资源ID支持，要求有点击监听器；继续校验设置文案或图标，歧义时保持回退，不以唯一可点击控件直接猜选。图标读取兼容候选本身为ImageView。右侧三段式入口继续调用原生fq.performClick，原生层级、ID和监听器均保留。原WXActionMenuView路径保留。保留限量失败诊断便于实机验收，成功路径不输出菜单树。

通过真实Android 36 SDK编译标题胶囊、原生绑定静态证据检查、输入栏模型回归及diff检查；完整APK由GitHub构建，三段式显示及返回/标题/更多点击由实机验收。
