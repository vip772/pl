# 首次我页静音层与侧边栏会话挂载修复

输入：a7df00c运行日志17:08:54 MEMUTE与17:09:03 session=false。定向提取8078 MoreTabUI(classes9)与StoryMuteView(classes4)，未全量逆向。

静音层：日志识别StoryMuteView，首次VISIBLE，随后宿主设GONE。MoreTabUI.C0(Bundle)创建v并按BOTTOM|LEFT插入主页父FrameLayout，没有初始化隐藏；StoryMuteView.c()设置静音图标和文字。透明化后默认可见底层露出。仅在C0完成且我页背景已配置/启用时隐藏初始VISIBLE实例；不阻止宿主后续可见性写入，并在宿主写入时释放恢复记录。关闭背景功能/移除我页背景时恢复仍由模块持有的初始化状态。

头像入口：session=false证明未创建会话，不是Tab显示条件。LauncherUI没有直接声明onCreate/onResume，原findMethod漏继承protected方法，改findMethodRecursive；首次LauncherUI触摸补挂缺失会话。仅微信Tab显示入口的规则保留。新增sessionAttached日志。

独立Kotlin类型检查及现有背景回归检查，不执行本地Gradle构建。最终首次我页及头像显示待实机验证。