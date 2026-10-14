# e30d159失败核查与绘制链修正

输入：13:46运行日志及e30d159匹配mapping；持久化8078APK。没有视频/LSPosed日志，不能把快照当成最终画面验收。

已证实的遗漏：
- gradientSkipped共6条，说明上一版渐变Hook实际执行，但DynamicBackgroundGLSurfaceView在轻拉/回弹快照仍全屏、alpha=1。GameGLSurfaceView父类是android.view.SurfaceView；这是独立Surface，跳过渐变onDraw无效。
- cxw（r/p/cxw.xml，0x7f0e1ca5）为任务栏内标题布局，其TaskBarBottomView在标题RelativeLayout后面；onDraw通过Paint绘制路径/圆弧/矩形。普通标题背景透明无法清除此自绘。
- 没有title/chatAnimationEnd命中，不能宣称上一版这两条Hook有效覆盖。缺少启动日志不能区分未安装、未调用和门控退出，新增WXINSTALL记录。
- 13:46:44.803返回快照系统栏为0，主页ActionBar透明；聊天布局仍处滑出动画，无法证明动画结束后的实际遮挡。

修改：
1. 同一MainUIView/控制器归属、P明确false、微信背景启用时，GL子层设INVISIBLE，记录宿主原visibility；setVisibility保护同时记录宿主最新请求。P为true/未知、其他页/关闭功能时放行或恢复。普通refresh不先恢复再隐藏，避免Surface反复显隐。
2. 针对TaskBarBottomView自绘增加同一任务栏关闭态保护；保留标题文字、触摸、位置，不全局修改Paint颜色。开合/关闭功能invalidate恢复display list。
3. GL保护从已真机命中的b0.y后及渐变绘制前执行，避免只依赖未证实命中的f回调；原f/m/n继续保留。首次宿主显示Surface请求也受保护。
4. ConversationListView.y完成后读取绑定字段C；不把U1/T1当成完整标题自绘链。
5. 返回恢复增加TestTimeForChatting设GONE后的下一帧、NewChattingTabUI.u（重置SwipeBack）和s（主页容器可见）的完成入口，均验证聊天容器GONE及聊天状态非true；补主页背景并重应用任务栏GL保护。保留uf回调，不修改聊天背景或隐藏宿主聊天View。

范围限制：任务栏P在m(true)时置true、n时置false，APK已验证；不改变宿主开合状态。返回不同回调可能在同一结束阶段触发多次，应通过真机命中日志进一步确认频率。Surface显隐可能导致宿主重建Surface，必须复测正式小程序打开和关闭功能恢复，不能由类型检查证明无影响。

检查：独立Kotlin类型检查、现有背景合成/波纹/手势回归、git diff --check；不执行本地Gradle或全量逆向。新增绘制链和返回分支尚未真机验收。
