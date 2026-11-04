# 沉浸式聊天：消息延伸到透明输入栏后方

仅在微信8.0.78、沉浸式背景开启且当前聊天背景已配置时启用。其他版本没有对应逆向证据，不复用混淆资源名。

## 聊天工具栏与新消息提示避让

### 2026-11-04 实际目标容器纠正

上一轮把 `bm2` 当成“条新消息”，并引用 `hh.R0(IZ)` 作为其行为依据，这是错误对应。重新核对微信 **8.0.78/3180** 的原始 APK 二进制布局 `r/p/ss.xml`、资源表和 Smali：
- `hh.a()` 取 `0x7f0a0fc0`（`bm4`），`hh.S0(I)` 使用 `b2_`（`%d条新消息`）更新文本并设置原生点击监听，`hh.R0(IZ)` 在同一 View 上切换顶部/底部 gravity；这是截图中实际的新消息/历史消息提示。
- `sk.a()` 取 `0x7f0a0fbd`（`bm2`），源码标识 `QuoteMsgTongueComponent`，这是引用消息提示，不能用它代替新消息提醒。
- 两者均直接属于 `ChattingContent`（`bki`）；`ChatFooter`（`k52`）是 `ChattingScrollLayout` 内后续同级。沉浸式 underlay 扩展 content，但保留 footer 原生布局及触摸区域，故只避开工具栏可见条目、不避开 footer 整体命中范围，不能充分约束点击位置。

`ChatToolbarNewMessagePosition` 现在分别记录两个 View 的位移所有权，仅处理可见底部 gravity；顶部历史消息提示继续交给原生和既有悬浮标题栏。工具栏可见时以工具栏顶部为锚，关闭/空工具栏时以 `ssr` 输入行为锚，同时约束在 footer 顶部和 content 底边之上，留 8dp 间隔，且整个提示必须留在原生 content 父容器内。不重挂提示、不改原生 LayoutParams、margin、点击/触摸监听，不强行提升 Z 或拦截全局触摸。

定位观察者归 footer 生命周期所有，而非工具栏显示状态；关闭工具栏仅隐藏自有行并清除其按钮，保留输入框避让。工具面板 Dex 安装票据关闭并清空，采集/主动初始化/测量改写均检查启用状态；重新启用更新票据并初始化已知面板。detach、Activity 销毁、功能销毁或异常时释放观察者并只恢复仍归本模块所有的位移。

稳定帧不请求布局、不重复设置相同位移；两个目标按 childCount/旧 View 脱离来失效缓存，隐藏或仅顶部提示时不读取屏幕坐标；复用坐标数组，无轮询、无新增线程或延迟任务。未验证微信版本或容器不符不移动提示。以上为源码约束，不能代替实机流畅度测量。

本次用户明确不做本地构建，因此**未执行 Gradle、Kotlin/SDK 编译、APK/R8 构建或需要编译的旧测试**。执行 `scripts/check_chat_new_message_position_static.py`：源码契约、旧提交错误目标负向检查、180 个独立几何模型用例（180000 次稳定迭代）、原生资源/Smali 对照均通过。独立 Python 模型不是生产 Kotlin 执行，更不是 Android 绘制/触摸验证。既有 Kotlin 合成容器测试已更新 bm4、关闭工具栏、键盘及 footer 边界夹具，本轮未运行，不沿用历史通过结论。完整证据与待验收矩阵见 [本轮修复记录](CHAT_NEW_MESSAGE_CONTAINER_FIX_20261104.md)。

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

## 历史记录：2026-11-04 统一提交前避让复核（目标对应已被上文纠正）
原始屏幕重叠判断会在模块抬高提示后读取已修改的位置，下次布局可能恢复原值并来回振荡。现从当前坐标加回模块已施加的边距差，重建原生基线，并核查完整矩形交叠；待布局时不重复改写。回归直接提取执行生产apply/restore方法，覆盖5000次稳定布局、工具栏移动、横纵不交叠、关闭、待布局、原生改写及恢复。使用最小View桩，非微信实机或完整标题栏SDK/APK验收。

## 2026-11-04 构建失败纠正
完整检查用户提供的 `logs_113357264731.zip`（SHA256 `cab4306880402b367e8aeaf924095b1f1b64a83821a192b62d6ceee0fab9f24f`）全部18个文件；汇总日志与APK步骤重复报告同一个Kotlin错误：`FloatingChatHeaderFeature.kt:787:72` 访问 `ChatToolbarRuntime` 的private companion。缓存ReserveCacheError发生于失败后的post步骤；SDK37/AGP、原生库strip、Gradle10、Node/action弃用、wrapper权限为其他警告，不是此次Kotlin失败原因。不通过升级依赖或屏蔽警告掩盖源码错误。
将工具栏View标记移至既有 `ChatToolbarSettings` 的internal常量；挂载、查重、避让三个调用点统一引用，标记字符串保持不变，运行时companion继续private。旧测试把运行时替换成公开object，未保留访问边界，导致漏检。新增 `check_chat_toolbar_chip_contract_sdk.py` 用真实设置源文件与原样private companion编译生产apply/restore方法，并用旧提交原样声明做负向控制，确认同类错误确实能被拦截。该检查不是完整APK或R8验收。
