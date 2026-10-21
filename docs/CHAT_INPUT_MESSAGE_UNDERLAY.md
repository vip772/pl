# 沉浸式聊天：消息延伸到透明输入栏后方

仅在微信8.0.78、沉浸式背景开启且当前聊天背景已配置时启用。其他版本没有对应逆向证据，不复用混淆资源名。

## 逆向依据

- `res/layout/ss.xml`：纵向 `ChattingScrollLayout`（bqg）先放权重为1的 `ChattingContent`（bki），再放 `ChatFooter`（k52）。消息包装器为bm6。原生布局为输入栏预留独立空间，仅清背景不能显示后方消息。
- `smali_classes14/com/tencent/mm/pluginsdk/ui/chat/ChattingScrollLayout.smali`：继承LinearLayout，onLayout(ZIIII)调用原生LinearLayout布局；onFinishInflate绑定k52、bki、bm6。
- `ChatFooter`初始化加载0x7f0e0339，即t8.xml。输入操作行位于`bfp`容器内的`bl8/ssr`，面板`bkc`是`bl8`下的独立区域；两者top值必须转换到`bfp`共同坐标系后才能计算。不能将`blr`或整个ChatFooter高度当作输入操作行高度。
- `smali_classes2/.../ChattingContent.smali`：继承FrameLayout，测量和布局调用父类。
- `smali_classes11/.../MMChattingListView.smali`：getListView()返回u3，实际消息列表为ChattingRecyclerView/ScrollControlRecyclerView；ChattingRecyclerView继承RecyclerView。

## 实现与恢复

从现有聊天 chrome 刷新路径更新：bki底部margin减去按`ssr`输入行与可见`bkc`面板边界计算的重叠高度，使权重消息区域扩展并与输入操作区重叠。坐标统一换算到`bfp`祖先；面板区域不计入输入行高度。输入栏层级、按钮和面板不重挂，沿用已有背景透明和输入框描边处理。

实际消息列表底部padding增加同样高度并关闭clipToPadding，使滚动消息能绘制到输入栏后方，同时最后一条消息可完整停留在输入栏上方。多行输入、面板开关、窗口变化随原生布局更新；不使用固定dp或键盘高度。保留原生顶部/侧边padding与外部底部padding更新，重复应用不累加。

关闭背景、恢复聊天槽、销毁时恢复原margin、底部padding及clipToPadding。结构不符合已核实层级时不修改，异常恢复并记录错误。

## 验证边界

`python3 scripts/test_chat_input_underlay.py`直接执行生产控制器的最小View模型回归，覆盖重复布局、面板高度排除、多行高度、原生padding改写、隐藏与恢复、重新开启及错误层级。生产控制器和实际Hook接入代码通过Android SDK依赖桩定向编译。未运行完整Gradle/APK构建；模型测试不能替代微信消息绘制、滑动、键盘、多行输入及按钮点击实机验收。
