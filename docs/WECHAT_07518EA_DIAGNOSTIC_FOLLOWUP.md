# 07518ea 微信标题栏与未覆盖复现窗口

输入：10:50 运行日志、同构建 mapping、LSPosed、录屏压缩包。

10:50:35 WXWRITE 调用链确认 HomeUI.q -> HomeUI.w -> TabsAdapter.onPageSelected 修改 ActionBarContainer ei。立即快照为 ColorDrawable -1184275，owned=false、原所属ME；下一preDraw已为模块透明背景、所属WECHAT。Frosted=false、ActionBar h/i/m均null。增加HomeUI q/w完成后的公共栏透明化，依据实际宿主调用链，保留系统栏处理。

本份没有 bounceOffset 或回弹 WXFRAME，只有切页触发的顶部重写快照。聊天退出事件有命中但缺少退出后窗口状态。不能声称已找到回弹或返回露底根因。增加LauncherUI dispatchTouchEvent after触发下拉/松手快照；聊天退出刷新后采样；WXRESULT记录setter同步保护是否保留模块背景，WXRESTORE记录主动恢复时目标和开关。写入与触摸采样分配独立预算，避免启动阶段消耗全部采样。

类型检查、已有背景/波纹/手势回归、差异检查。无Gradle/本地APK构建。回弹和聊天返回仍是诊断，不宣称最终修复；冷启动直接微信下拉回弹，再进入聊天返回，不先切换其他页面。