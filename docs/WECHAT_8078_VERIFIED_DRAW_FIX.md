# 微信8078渐变自绘与聊天退出完成恢复

依据：f5d9f98运行日志与匹配mapping；持久化微信APK定向Smali。此次新增仅8个链路类及3个直接依赖，不做全量逆向。

已证实：两个GradientColorBackgroundView在MainUIView直接子层/其DynamicBgContainer子层中，全屏执行onDraw，未受模块管理；普通列表与r背景setter已经保留透明。轻拉系统栏为0，不能继续归因于状态栏setter。

源码边界：b0.P初始false，m(true,...)打开通知设置true，n(Z)关闭设置false。m同时通知任务栏q接口。关闭态隐藏动画和轻拉仍可能绘制渐变，因此只在同一MainUIView/列表/任务栏实例匹配、当前微信主页已配置背景、P明确为false时跳过渐变onDraw。P=true、状态未知、其他页面及功能关闭全部放行。保留可见性、alpha、Paint及GL状态；弱引用缓存归属，设置刷新和销毁invalidate使原绘制恢复。

标题绑定：ConversationListView.attachViewToParent将C传入TaskBarContainer.setActionBar并赋给b0.U1；C由y()获取资源0x7f0a00d4。T1获取0x7f0a3e30。在f/m/n完成后，只关闭态登记这两个具体绑定节点，沿现有SurfaceSnapshot保留原背景并同步保护setter。没有修改标题文字、位置或全页扫描；是否对应截图上方全部底色仍需实机确认。

聊天退出：NewChattingTabUI.f(Z)使用uf作为退出AnimationListener；uf.onAnimationEnd将TestTimeForChatting设GONE、清除l并调用u重置SwipeBack状态。已有chat-exit日志发生在滑出动画中。本次在该回调完成后，在聊天状态非true且已配置主页背景时重新准备应用主页，并限频采样chat-animation-end。不使用固定延迟，不清除聊天布局背景。无动画/其他退出路径沿原有事件逻辑，暂不声称已覆盖全部路径。

验证：独立Kotlin类型检查（不是Gradle构建）；现有background_beauty回归；git diff --check。没有真机执行验证。

复测：轻拉回弹、两套标题区域、进入会话返回不切页；完全打开小程序确认其原渐变仍绘制；关闭功能确认原绘制恢复。WXFIX gradientSkipped/title/chatAnimationEnd分别记录实际命中。