# 6c870ac 聊天返回与首次准备修复

输入：09:43 运行日志、同构建映射、LSPosed、65秒录屏。录屏已解压并提取1fps联系表；本轮结论以运行日志和源码/API调用链为依据，不以低频关键帧证明瞬态像素根因。

- 09:42:00 我/发现/通讯录主列表 children=0，preparePage 已登记缓存。随后新控件加入时旧缓存不失效。空列表不标记准备完成；列表出现首批子节点后通过已注册 preDraw 做一次定向重新准备，无需用户切页，不开启持续整页遍历。
- 背景功能没有订阅成熟 WeChatApis.chatPage；LauncherUI 内部聊天 Fragment 不保证触发 Activity resume。新增受 BaseFeature 管理的订阅，退出聊天时主动刷新 LauncherUI，并显式更新聊天状态，避免退出回调时旧 Fragment 尚未切换导致误判。
- 当 LauncherUI 内聊天没有配置聊天背景时，原 applyForActivity 恢复整个 Activity 公共背景；点击进入聊天和返回可露出主页原生背景。该分支保留主页背后的受管理状态。背景 setter 保护按 snapshot 原所属页面是否配置背景判断，而非瞬间选中的聊天 slot；关闭功能仍放行恢复。
- 本次 bouncePrepared/颜色setter已命中，不能再归因子类初始化漏掉。剩余回弹露底尚缺位移时字段值证据。Hook 微信 APK 中 MMWeUIBounceView.a(View,int) 实际回调，仅限频记录四个 Drawable 与 G 管理状态，最多12次，不修改滚动/回弹行为。

验证：定向 Kotlin 类型检查（公共 API 与 BaseFeature 新桩签名已与实际源码核对），原背景合成、触摸波纹、手势/CANCEL回归，git diff --check。无 Gradle/本地 APK 构建；没有声明回弹已最终解决。

新包需要依次冷启动直接进入我、进入聊天立即返回、不切页继续回弹。日志 chatTransition/bounceOffset 对应实际退出与位移窗口。