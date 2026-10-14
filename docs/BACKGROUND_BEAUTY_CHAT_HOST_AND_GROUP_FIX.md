# 聊天切换底图、底栏按钮与分组聊天宿主修复

基线 dev `c03e4ab312df828af0ad79c2a84b126f2c3a1b23`，依据用户三张截图、`Hchat-runtime-20261015-011049.log` 和同基线 mapping；微信内部路径复核使用本机 8.0.78 APK。

## 定位

1. `applyBitmap(CHAT)` 把聊天图片直接写到 LauncherUI 的 DecorView。微信主页和内嵌聊天共享窗口，进入/返回动画仍绘制主页时，主页底图会被聊天图片提前替换。
2. `applyChatFooterTransparency` 只处理 EditText 和 ViewGroup，忽略 ImageView/ImageButton 的独立背景/前景；截图的右侧麦克风按钮表面不在处理范围。ChatFooter 反编译同时确认其图标按钮与输入容器是独立控件，不能把容器透明等同于按钮透明。
3. 自定义分组 `openNativeGroupConversation` 调用原生页面 `startChatting(talker,bundle,true)`；其宿主是 `ConvBoxServiceConversationUI : BaseConversationUI`，内部持有 `chattingView:TestTimeForChatting`、chattingFragmet 和 ChattingUIProxy。此前生命周期只监听 LauncherUI/ChattingUI，resolveTarget 对其它 Activity 直接返回 null。
4. 运行日志出现进入事件后4ms即退出、随后再次进入。进程共享的 launcherChatActive 不能代表每个 Activity 的实际页面，不应据此改变所有 LauncherUI 背景。

## 修改

- 主页继续在 DecorView 绘图，内嵌聊天改由实际 TestTimeForChatting 容器绘制单张不透明底色+配置透明度图片；透明扫描限定该容器子树，并保护容器自身图片背景。独立 ChattingUI 无该容器时使用自身 Decor。
- 图片原生控件查找和祖先透明处理同样限定当前聊天容器，避免误处理同窗口后方主页。保留上次单图、底部补色和顶部独立背景层修复。
- resolveTarget 使用各 Activity 内实际可见聊天容器；删除全局聊天状态。进入/退出事件只请求重新判断。
- 增加 BaseConversationUI 生命周期和关闭聊天入口监听；聊天布局/底栏附着或布局时，尚未应用聊天图片的宿主请求刷新。分组列表本身不套聊天样式，聊天隐藏后恢复其原生背景。
- BaseConversationUI 关闭时先设 GONE 再启动退出动画，动画期间保留聊天底图与透明状态，完成后恢复，避免提前清空动画中页面。主页恢复时也保留仍在动画中的聊天子树。
- 底栏 ImageView/ImageButton 清除背景与前景并保留独立 image drawable、点击行为及透明反馈；宿主后续背景重写使用已有保护/恢复逻辑。编辑框白色描边未变时不重复创建，减少布局反馈与日志。

## 验证

- 不执行 Gradle、本地应用编译、APK 构建或触发远端构建，交由用户 GitHub 构建。
- 独立 JVM 背景/反馈/手势回归脚本通过；`git diff --check` 通过。该脚本不覆盖 Android 实际过渡动画和微信 Activity 生命周期，最终效果需要实机验证。
- 内部结构核实为微信8.0.78，未对其它版本完成横向验证。
- 建议复测：主页与聊天反复进出、返回手势取消；底栏麦克风正常/按下/切换键盘状态；自定义分组联系人与群聊进入、返回分组及再次进入；关闭背景后的原生恢复。
