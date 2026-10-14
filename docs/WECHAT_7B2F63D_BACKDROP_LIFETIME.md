# 7b2f63d背景归属与缓存重绘核查

输入：14:30至14:33运行日志，匹配mapping；APK已有定向源码。用户确认轻拉闪底和可滚动标题已改善，要求完全展开的主页小程序入口区域也透明。

关键证据：returnComplete在14:31:04.578/.601及后续3次返回均命中，窗口bars=0/0，主页DecorView/FirstScreenFrameLayout/ConversationListView为模块Drawable。不能再说返回未刷新。返回后两个渐变节点仍可见且alpha=1，其绘制内容是否为空无法从background=null判断。

源码缺口：渐变/GL/标题自绘原守卫依赖resolveTarget=WECHAT；聊天和切页时放行。进入CHAT会invalidate生成原绘制，completeWechatReturn只applyForActivity不invalidate实际渐变子View；refreshAll切页却invalidate该子View。因此存在状态变更后原绘制display list未失效的缺口，与切页恢复表现一致，仍须新包实际像素验证。

修改：
- 专属于同一MainUIView/TaskBarContainer实例的绘制保护按LauncherUI+功能开启+WECHAT背景存在维持，不随聊天/其他Tab短暂放行。
- 取消这些主页背景层P关闭态限制，打开的小程序桌面入口也透明，符合本轮用户明确目标。独立小程序Activity、聊天底图不匹配该实例父链，不全局屏蔽绘制。
- 聊天状态事件、returnComplete、HomeUI完成和主页图片应用后，定向invalidate已登记渐变与标题自绘子View；不是遍历整页，不使用持续preDraw扫描。
- 关闭功能/移除图片恢复GL最新宿主visibility并invalidate自绘；共享U1/T1透明处理仍限制当前微信Tab，避免将共享栏按微信页配置覆盖其他Tab。

限制：当前日志没有证明点击行内部叶节点或foreground是否另有偶发重写；不为此清除所有文字/头像背景。新包若仍有单行闪底，需要记录实际行的foreground/叶背景，而不是认定本次缓存修复已覆盖全部点击问题。

检查：独立Kotlin类型检查、现有背景/波纹/手势回归及git diff --check。不执行本地Gradle构建。