# 聊天底部位置修正与消息时间胶囊背景

## 范围
基线 7fad3c1。输入区穿透路径本来就仅在微信 8.0.78、聊天背景开启且悬浮输入栏关闭时使用，本次不扩展版本范围。逆向依据为本地 8.0.78 ChattingContent.onMeasure(II)、ChattingScrollLayout.onLayout(ZIIII) 和 dispatchTouchEvent(MotionEvent) 的具体实现。未对其他微信版本新增 Hook 行为保证。

## 原因与修改
- beforeLayout 原先用宽高相等推断模块测量；同宽的新原生高度等于旧扩展高度时会误恢复旧高度。现通过 ChattingContent.onMeasure 失效测量所有权；模块自己的同步 measure 使用保护标记排除，不再仅凭尺寸判断。
- 原补偿遇到消息数量变化一律退出，定向模型可留下一个输入行高度的空隙，普通 apply 不会恢复。现只允许本轮布局前已贴底、布局后新的最后一项仍可见且数量未减少的追加场景补偿，不执行选中或跳转到不可见消息。不跨帧保存待执行滚动，不投递延迟任务，不干预原生动画或强设 translationY。
- 触摸按下清除本轮锚点，触摸期间不补偿；MOVE 在 Hook 中直接返回。历史阅读、短列表、末项不可见、数量减少仍不补偿。
- 消息显示时间（HchatExtraFeature，而非 ChatTimeStyleFeature）的 GradientDrawable 半径改为足够大的有限值，让 Android 按实际背景边界裁成胶囊。颜色、透明度、文字、位置和 padding 不变，已有 drawable 复用，不新增布局监听。

## 验证
- scripts/test_chat_input_underlay.py：生产控制器模型，覆盖测量来源、同宽等高新测量、单条/多条追加、不可见末项、删除、触摸取消、历史阅读、面板与窗口尺寸，以及稳定状态一万次 apply 不新增 measure/layout/scroll。
- scripts/test_chat_layout_capsule_sdk.py：完整控制器、原文测量/布局/触摸 Hook 片段与时间背景方法的 Android SDK 定向编译；项目依赖为桩，并非整工程构建。
- scripts/test_chat_time_resolution.py、消息标签布局及预绘制回归。

尚未进行微信实机复现闭环、帧耗时测量或胶囊渲染验收，不能保证截图所有异常已消除。仍保留原有缩小/扩展两阶段布局，未声称消除了其成本；只验证稳定刷新不增加布局工作。原生异步滚动、惯性滑动、媒体高度变化、键盘与面板组合仍需新 APK 验收。未运行 Gradle、未构建 APK、未操作微信。附件 mapping 临时文件已清理，未用本次 mapping 进行最终 DEX 核对。
