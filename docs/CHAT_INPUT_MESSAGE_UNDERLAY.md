# 沉浸式聊天：消息延伸到透明输入栏后方

仅在微信8.0.78、沉浸式背景开启且当前聊天背景已配置时启用。其他版本没有对应逆向证据，不复用混淆资源名。

## 聊天工具栏与新消息提示避让

在微信 8.0.78 解码证据中，`ss.xml` 的 `ChattingContent`（`bki`）包含右下角新消息提示 `bm2`，`ChatFooter`（`k52`）是其后续同级输入区；微信原生 `hh.R0(IZ)` 会按提示状态更新其 `FrameLayout.LayoutParams` bottom margin，提示点击监听仍由微信设置。聊天工具栏模块则把 `Hchat:ChatToolbar` 注入 `ChatFooter` 子树。

悬浮标题栏启用时，运行时按 `bki/bm2/k52` ID 精确定位提示、输入区，并在输入区后代中查找模块标记的可见工具栏。仅当提示仍为原生右下角、工具栏位于输入区内且提示底边与工具栏顶边实际相交时，增加提示的 bottom margin；避让量为屏幕坐标实测重叠像素加 8dp 间距。工具栏关闭、隐藏或提示重新定位时恢复记录值；微信后续改写 margin 时更新原生基线，功能销毁时按最新基线恢复。不替换原 LayoutParams，也不修改点击监听或触摸层级。

该定位依据目前仅覆盖本地微信 8.0.78 解码产物。源码契约和几何状态夹具通过不等于 APK/R8 构建或实机验收；需在沉浸式背景、悬浮标题栏下分别验证工具栏开关、横竖屏、键盘/面板状态及提示点击。

## 逆向依据

- `res/layout/ss.xml`：纵向 `ChattingScrollLayout`（bqg）先放权重为1的 `ChattingContent`（bki），再放 `ChatFooter`（k52）。消息包装器为bm6。原生布局为输入栏预留独立空间，仅清背景不能显示后方消息。
- `smali_classes14/com/tencent/mm/pluginsdk/ui/chat/ChattingScrollLayout.smali`：继承LinearLayout，onLayout(ZIIII)调用原生LinearLayout布局；onFinishInflate绑定k52、bki、bm6。
- `ChatFooter`初始化加载0x7f0e0339，即t8.xml。输入操作行位于`bfp`容器内的`bl8/ssr`，面板`bkc`是`bl8`下的独立区域；两者top值必须转换到`bfp`共同坐标系后才能计算。不能将`blr`或整个ChatFooter高度当作输入操作行高度。
- `smali_classes2/.../ChattingContent.smali`：继承FrameLayout，测量和布局调用父类。
- `smali_classes11/.../MMChattingListView.smali`：getListView()返回u3，实际消息列表为ChattingRecyclerView/ScrollControlRecyclerView；ChattingRecyclerView继承RecyclerView。

## 实现与恢复

从现有聊天 chrome 刷新路径更新：bki底部margin减去从footer顶部到可见`ssr`底部的高度，使权重消息区域扩展并与输入操作区重叠。沿父级累加原生top，将`ssr`底部换算到footer坐标系；下方`bkc`面板高度不计入。输入栏层级、按钮和面板不重挂，沿用已有背景透明和输入框描边处理。

实际消息列表底部padding增加同样高度并关闭clipToPadding，使滚动消息能绘制到输入栏后方，同时最后一条消息可完整停留在输入栏上方。多行输入、面板开关、窗口变化随原生布局更新；不使用固定dp或键盘高度。保留原生顶部/侧边padding与外部底部padding更新，重复应用不累加。

关闭背景、恢复聊天槽、销毁时恢复原margin、底部padding及clipToPadding。结构不符合已核实层级时不修改，异常恢复并记录错误。

## 验证边界

`python3 scripts/test_chat_input_underlay.py`直接执行生产控制器的最小View模型回归，覆盖重复布局、面板高度排除、多行高度、原生padding改写、隐藏与恢复、重新开启及错误层级。生产控制器和实际Hook接入代码通过Android SDK依赖桩定向编译。未运行完整Gradle/APK构建；模型测试不能替代微信消息绘制、滑动、键盘、多行输入及按钮点击实机验收。

## 2026-10-22 实机定位
`Hchat-runtime-20261022-105631.log`的57条诊断均显示`blr.height=0`、消息内容与footer重叠为0；可见`ssr`范围为y=2923..3094（171px）。恢复历史`blr.height`计算不能产生覆盖量。现改用可见`ssr`在footer内的底部坐标；诊断排除隐藏/零尺寸EditText，保留消息列表边界快照以供实机验收。回归新增零高度blr、171px输入行、父级偏移/隐藏及面板扩展场景。
真实Android SDK编译检查：`python3 scripts/check_chat_input_sdk.py`。完整Gradle/APK和实机绘制结果仍须分别验收。

## 2026-10-22 贴底回归修正
11:20实机日志显示ssr从2923..3094变为2752..2923，footer上移171px，恰好等于负bottomMargin覆盖量。纵向LinearLayout在现有布局路径下将负margin传导给后续footer位置。
现保持原生margin：onLayout前恢复模块扩大的内容测量值（若已有新原生测量则保留），onLayout后仅对ChattingContent重新精确测量并扩展布局高度，使列表子树向下延伸，而footer及面板的布局和触摸边界保持原生。关闭时恢复内容高度及列表padding/clipToPadding。
footer内结构性ViewGroup统一禁用整块按压反馈，避免原生pressed状态传播到宿主而绘制整块灰色遮罩；实际AppGrid条目及图标按钮仍保留各自反馈。此规则在透明背景公共入口生效，防止其他透明扫描重新覆盖。
回归模拟原生LinearLayout多轮根据measuredHeight安放footer，检查footer不漂移、内容不累加扩大；同时覆盖窗口重新测量、面板高度变化、多行、隐藏和恢复。真实Android SDK编译与模型回归通过不等于实机绘制验收。

## 实机验收后的诊断清理
用户确认输入栏透明及贴底运行正常。移除CHROMEINPUT_DIAG快照、祖先链、遮挡子树扫描、仅用于诊断的EditText查找与缓存，以及CHROMEINPUT/CHATPART相关调试输出。保留screenRect/intersectionRect：它们参与底部尾部覆盖计算，属于功能。保留布局、透明及错误恢复逻辑。清理后真实SDK编译和模型回归通过。

## 2026-11-04 统一提交前避让复核
原始屏幕重叠判断会在模块抬高提示后读取已修改的位置，下次布局可能恢复原值并来回振荡。现从当前坐标加回模块已施加的边距差，重建原生基线，并核查完整矩形交叠；待布局时不重复改写。回归直接提取执行生产apply/restore方法，覆盖5000次稳定布局、工具栏移动、横纵不交叠、关闭、待布局、原生改写及恢复。使用最小View桩，非微信实机或完整标题栏SDK/APK验收。
