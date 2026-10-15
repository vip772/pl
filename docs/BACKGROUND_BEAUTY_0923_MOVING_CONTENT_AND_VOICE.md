# 0923 实机反馈：移动内容底图、语音阴影与按住说话

基线：b29744b。输入：Hchat-runtime-20261015-092346.log、同提交 release mapping、用户截图及视频.zip 内 3.75 秒录屏。遵循用户要求，只改 dev，不运行 Gradle 或本地 APK 构建。

## 证据

- mapping 确认 oi.o0 为模块透明 Drawable、oi.p0 为 ScreenAlignedBitmapDrawable。日志 wet、wes、wey 及 SpeechInputLayout 的背景和前景均已为 oi.o0，因此继续清除图标背景不足以覆盖残留阴影。
- 微信 8.0.78 APK 的 res/layout/t8.xml：sub 容器内 suc 有一个无 ID 的普通 View，背景是 drawable/de7.png（含深浅资源），位于 SpeechInputLayout 外侧，尺寸 dr×dr，负边距 right=-18dp、bottom=-16dp。原普通 View 分支只处理 SpeechInputLayout 内部，漏掉此位图遮罩。
- t8.xml 中 p6x 为 Button；ChatFooter 初始化将其赋给字段 p（2131390873），E(boolean) 按模式重写背景。它既非 EditText、ViewGroup，也非 ImageView，原底栏遍历遗漏它。
- 日志 09:23:23.803/.886/.892 中 ChatFooter 横坐标为 572/1337/1366，退出期间内容在水平移动；ScreenAlignedBitmapDrawable 仍绘制在外层 TestTimeForChatting，内容离开后底图留在原位。
- 09:23:23.984 完整画面捕获，.994 外层聊天设 GONE，.996 捕获释放，.24.014 才记录应用主页背景。原 foreground 截图无法覆盖此后阶段。录屏已提取为 12fps 联系表，并通过浏览器读取帧；不以截图方案成功捕获作为返回缺陷已消除的证据。
- classes16.dex 中 SwipeBackLayout.getTargetContentView() 返回字段 i，setContentView(View) 设置 i，drawChild 在目标内容移动时处理阴影。实际移动内容才应持有聊天底图；外层 TestTimeForChatting 负责页面可见性。

## 修改

1. 聊天图片绑定到 SwipeBackLayout.getTargetContentView()，缺少该结构时沿用页面宿主。目标稍晚初始化时显式撤回旧绘图表面，避免双层。通用透明化跳过已托管的聊天图片表面，防止后续扫描覆盖它。
2. TestTimeForChatting 设 GONE 后在同一 UI 调用内执行已有主页恢复流程，替代延后一帧；保留原生动画、导航与分组入口。
3. 删除完整聊天截图、前景覆盖、逐帧等待、超时及对应 ChattingUIProxy Hook，不再额外分配整页退出位图。
4. 从 ChattingUILayout 范围处理 p6x 与 sub 下无 ID 普通 View，覆盖现代输入树与旧 ChatFooter 并存场景。清除 de7 位图背景；按住说话背景为透明、保留按下反馈，不修改文字、触摸监听和录音行为。
5. 输入框描边及模式配色不变。新增限频 CHATPART hold-to-talk-before/after、voice-overlay-before/after，以及 CHATRETURN movingBackdrop、close 和同步恢复 reason。

## 验证和边界

独立 JVM 回归通过，覆盖背景对齐、反馈 drawable 和侧边手势；git diff --check 通过。未运行 Gradle、未完成应用编译验证，未在微信中加载本次源码。最终效果必须 GitHub 构建后实机验证。

检查普通返回、侧滑返回完成和取消、再次进入聊天、自定义分组聊天；输入模式切换后检查按住说话透明及按下录音；检查右侧麦克风阴影和键盘/表情切换。重点日志为 movingBackdrop 的 outer/target、voice-overlay-after、hold-to-talk-after，以及 returnComplete reason=chat-container-hidden-sync。

本次内部布局与调用结构只核实 8.0.78；未宣称其它版本已验证。其它历史文档中的截图方案由本文替代。
