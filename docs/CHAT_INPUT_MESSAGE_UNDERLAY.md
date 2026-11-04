# 沉浸式聊天：消息延伸到透明输入栏后方

仅在微信8.0.78、沉浸式背景开启且当前聊天背景已配置时启用。其他版本没有对应逆向证据，不复用混淆资源名。

## 聊天工具栏与新消息提示避让

在微信 8.0.78 解码证据中，`ss.xml` 的 `ChattingContent`（`bki`）包含右下角新消息提示 `bm2`，`ChatFooter`（`k52`）是其后续同级输入区；微信原生 `hh.R0(IZ)` 会按提示状态更新其 `FrameLayout.LayoutParams` bottom margin，提示点击监听仍由微信设置。聊天工具栏模块则把 `Hchat:ChatToolbar` 注入 `ChatFooter` 子树。

2026-11-04 容器修正：避让责任移至工具栏所有的 `ChatToolbarNewMessagePosition`，不再依赖悬浮标题栏启用或刷新。仅在微信 **8.0.78/3180**，沿 footer 祖先确认 `ChattingUILayout`，并校验 `bki` 的真实类型为 `ChattingContent`、`bm2` 直接属于该容器且使用底部 gravity 后生效。
工具栏可见且附着时登记预绘制监听，按实际屏幕坐标让提示底部屏幕Y坐标不超过工具栏顶部减 8dp；**提示完全位于工具栏下方、尚未相交也需要上移**。只修改自有 `translationY`，不写微信 bottomMargin，不更换原生 LayoutParams、点击监听或父容器。原生位置/外部 translation 改写后重新记录基线；恢复仅在当前值仍为模块最后写入值时进行，避免覆盖外部更新。
关闭工具栏、空工具栏、脱离窗口、Activity 销毁和运行时销毁均移除监听并恢复自有位移；重新显示时幂等附着。稳定可见帧不请求布局、不重复写属性，已找到的提示直接缓存，不每帧遍历 footer；错误限频记录。未验证版本或容器不符不修改。
验证：`test_chat_toolbar_new_message_chip.py` 执行完整生产协调器的合成容器回归（完全位于下方、10000个整数/小数基线稳定帧、原生位置变化、DPI、隐藏、gravity、外部所有权、延迟出现、错误容器、版本门、关闭重开）；`check_chat_toolbar_chip_contract_sdk.py` 编译完整协调器并保留私有常量访问负向测试；`check_chat_toolbar_runtime_sdk.py` 编译完整生产工具栏及标题栏接线，依赖项目桩和真实 Android SDK/DexKit 类型。**定向通过不代表 Android 绘制/触摸或微信实机通过**，最终构建与实机状态见本轮独立证据文件，不沿用旧提交构建结果。

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

## 2026-11-04 构建失败纠正
完整检查用户提供的 `logs_113357264731.zip`（SHA256 `cab4306880402b367e8aeaf924095b1f1b64a83821a192b62d6ceee0fab9f24f`）全部18个文件；汇总日志与APK步骤重复报告同一个Kotlin错误：`FloatingChatHeaderFeature.kt:787:72` 访问 `ChatToolbarRuntime` 的private companion。缓存ReserveCacheError发生于失败后的post步骤；SDK37/AGP、原生库strip、Gradle10、Node/action弃用、wrapper权限为其他警告，不是此次Kotlin失败原因。不通过升级依赖或屏蔽警告掩盖源码错误。
将工具栏View标记移至既有 `ChatToolbarSettings` 的internal常量；挂载、查重、避让三个调用点统一引用，标记字符串保持不变，运行时companion继续private。旧测试把运行时替换成公开object，未保留访问边界，导致漏检。新增 `check_chat_toolbar_chip_contract_sdk.py` 用真实设置源文件与原样private companion编译生产apply/restore方法，并用旧提交原样声明做负向控制，确认同类错误确实能被拦截。该检查不是完整APK或R8验收。
