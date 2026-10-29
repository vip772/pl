# 08:53:55 日志核查与本地诊断

基线 b46742d。用户要求证据定位、不提交推送。

## 已证实

- 日志 417 行，仅含模块日志；无逐次点击、消息子项坐标、滚动状态、anchor 执行记录。
- 08:52:09.165 面板打开后 extra=1411、footerY=1709、列表 height=3120，padding 由400变为1431；08:52:10.435 收起后恢复400。这证明面板高度进入占位计算，不能再归因为未纳入高度。
- 08:52:07.324 第一次列表 padding 20 -> 400，同毫秒 footer-settled complete=false；源码有限三次收尾耗尽后即恢复显示，不保证列表消息坐标正确。此处是可见中间状态的缺口，但日志不足以确认用户提到的那次会话遮挡对应此事件。
- 原生 ChattingScrollLayout.b(int,boolean,int,int) 控制 scroller 与子项 translation；现有 hook 在面板可见时把其目标改为 IME 高度。此改写与模块列表 padding/anchor 一起构成接管链路。日志没有原始/有效参数，不能直接证明它是本次唯一根因。
- 8.0.78 APK smali 确认 ScrollControlRecyclerView 继承 ChattingRecyclerView 且不覆盖 onLayout，父类 onLayout 调用 RecyclerView.onLayout；getCount 返回 adapter itemCount，getLastVisiblePosition 返回 LinearLayoutManager 的末项查询。没有证据表明 hook 只装到父类就必定漏掉子类。
- 沉浸背景日志 floating=true managed=false，与源码启用悬浮输入框时 restore underlay 的分支一致。该日志不能证明其他回调没有改布局，但没有支持其重复叠加占位的证据。
- 当前 complete=true 只验证 padding 达标，不验证消息位置；不能用它断言消息已上推。上轮测试用模拟行验证算法，没有验证实际原生 adapter/footer 行及滚动条件，覆盖不足。

## 本地变化

不修改上推策略、补偿条件、工具栏高度或重试次数。只增加限流事件诊断：

- native-scroll-target：原始/生效 target、IME、面板状态、footer translation。
- list-padding / footer-settled：列表真实类、标识、count/last/scrollState、上下滚动能力、padding、translation、末子项本地与屏幕 bottom。
- anchor-before/captured/after/result 和 skip：捕获、布局消费、溢出补偿及跳过原因。
- 原 complete 改名 paddingComplete，明确验证范围。

诊断复用既有 ChatBottomTrace：每实例每阶段两分钟、最多80条、最短500ms；重的反射和坐标读取在限流通过后的 lazy detail 内执行。没有新增 timer、listener、滚动树扫描或消息内容记录。

## 需要诊断包复现

进入曾出现遮挡的会话；分别打开/收起表情与加号、互切、切键盘，再退出重入。新日志中核对原生目标是否被归零，anchor 被哪一项门控跳过，以及 padding 达标后末子项是否仍在输入框以下。只有确定这一链路才能针对性更改；不应继续试补 padding。

本地定向 anchor、首次布局事务、padding 测试通过。APK assembleDebug 离线构建因缺少缓存 aapt2-9.0.1-14304508-linux.jar 失败；该失败与源码语法无关，不能把旧 APK 当作本轮诊断包交付。最终 :app:compileDebugKotlin 编译 BUILD SUCCESSFUL；当前仅诊断改动，未修复验收、未提交推送。
打包补充：使用本地 AAPT2 覆盖后已继续至 Java 编译/资源合并，但离线缓存还缺 gson 2.13.2、jcommander 1.64 等运行依赖，assembleDebug 最终失败；本轮没有可安装诊断 APK。五项相关回归通过，工作区修改均未提交。
