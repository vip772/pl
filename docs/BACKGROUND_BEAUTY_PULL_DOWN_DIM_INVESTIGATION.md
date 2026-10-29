# 分组下拉时整片轻微变暗：只读核查

基线dev 33b56162bdc83e983ea2168deeb1e759eb861f17。用户确认空白页脚修复成功，新现象为任意分组下拉按住轻微变暗，上滑无同样变化。本轮只读核查生产源码、微信8.0.78 smali和附件mapping，不修改运行行为，不提交推送。

附件Hchat-release-mapping-33b56162bdc83e983ea2168deeb1e759eb861f17.zip包含mapping.txt；确认BackgroundBeautyRuntime -> pi.z、GroupBarRuntime -> kj.c0。没有新异常堆栈，mapping不能提供本次遮层命中的运行证据。

## 对称的模块反馈路径

BackgroundBeautyRuntime.updateListRipple在ACTION_DOWN只对含kbq可见标题的实际会话行启用手动波纹；空白页脚被排除。ACTION_MOVE按abs(dx)、abs(dy)与scaledTouchSlop比较，超过任一方向阈值便clearListRipple，UP/CANCEL同样清理。WECHAT行使用manualOnly，不直接消费宿主pressed状态。横栏纵向拖动转发也按abs(dy)判断，不单独区分向上或向下。仅据截图不能完全排除波纹退场动画，但当前这条链没有与现象对应的方向条件。

## 原生下拉绘制路径

smali_classes16/com/tencent/mm/ui/conversation/ConversationListView.smali：

- onDraw(Canvas)先调用View.onDraw，再独立绘制TaskBar的备份页脚矩形。
- 条件包括i（TaskBar容器）存在、firstVisiblePosition==0、TaskBar bottom>6，且字段o>=30。
- Paint p的颜色取0x7f060307，对应color/cz；values中#ff7a7396，values-night中#797979。
- t()为真时p.alpha=o，否则alpha=0；t()委托taskbar.ui.b0.k()。
- b0.k()要求容器bottom>=n+10且firstVisiblePosition==0。
- onScroll中o=int(TaskBar.bottom*255/TaskBar.height)，即随下拉露出比例改变。
- 通过getBackUpFooterRect()取矩形范围，再Canvas.drawRect绘制。这个绘制不依赖View.background，不经过模块setBackground透明保护。
- q(II)关闭头部时将o设为0。

因此原生onDraw存在明确仅在顶部下拉露出后生效的主题色遮层，能够解释下拉与上滑的差异。现有背景美化抑制DynamicBackgroundGradientView、TaskBarBottomView绘制及普通View背景，但没有处理ConversationListView.onDraw中的这块独立Paint矩形。

另一条dispatchDraw使用L:Paint、K:Rect绘制局部蒙层，当前未取得K非空的本次运行证据，不能把所有遮层合并为同一原因。

## 结论与边界

原生下拉备份页脚遮层是目前有直接代码条件支持的首要来源；截图还不能证明本次变暗全部由它产生。未进行新设备手势、断点、运行参数修改或遮层开关对照，不能宣称已实机定位/修复。

后续验证应限定微信首页自定义背景开启的实际ConversationListView，对该独立绘制分支做前后对照，保留父类绘制、会话点击反馈、滚动与TaskBar开合。不能跳过整个onDraw/dispatchDraw，不能全局拦截Paint或Canvas。透明度在原生onDraw内部重新写入，简单在入口令p.alpha=0不会有效，必须选择能覆盖真实绘制点的方案并正常恢复。


## 已实现的源码处理（未提交）

新增 installConversationPullDownShadeHook：严格限定版本8.0.78和具体ConversationListView.onDraw(Canvas):void，预解析并校验字段o:int。只在被背景美化登记的WECHAT列表、首页图片已配置且实际bitmap未回收、当前目标为微信首页、渲染及功能开关开启时处理。

onDraw开始前保存o；o>=30时临时写0，使原生跳过独立主题色矩形分支，仍执行原方法及父类绘制。after回调无条件恢复本次保存值，包括宿主异常、渲染开关中途改变等路径。字段仅在绘制调用期间变化，不改滚动、下拉开合、布局高度和会话点击反馈。元数据仅安装时解析；不遍历子树，不新增定时任务或诊断日志。其它版本因缺少对应APK逆向依据保持原行为，本次不宣称跨版本兼容。

验证：scripts/test_conversation_pull_down_shade.py提取实际生产安装代码完成JVM行为回归及Android SDK签名编译，覆盖阈值、原生绘制保留、关闭/其它页面/图片缺失及回收、异常及中途开关恢复、元数据/版本校验、写入失败放行和重复安装/安装失败重试。首次SDK检查被测试夹具继承View方式阻断，修正夹具后完整通过。scripts/test_conversation_empty_footer.py原有页脚回归及SDK编译通过，scripts/test_background_render_gate.py的64种渲染组合通过。上述测试为受控夹具验证，尚未验证真实设备渲染效果或帧耗时。

未运行Gradle、未构建安装APK、未提交推送。仍需新包实机确认下拉轻微变暗是否全部消除，并检查下拉小程序展开/收起和单会话点击反馈。
