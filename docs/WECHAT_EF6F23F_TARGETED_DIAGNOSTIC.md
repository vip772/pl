# 微信页 ef6f23f 定向覆盖层诊断

输入：10:14 运行日志、对应 mapping、LSPosed、录屏压缩包。聚焦微信页，未修改其他页面或透明化范围。

## 已排除

10:13:38 的 bounceOffset 在偏移321、346、362、376、386时，MStart2EndBg/MEnd2StartBg/MStart2EndBgByActionBar/MEnd2StartBgByNavigationBar 均 ColorDrawable:0，G为模块背景且managed=true。这些被采样时刻不能将露底归因于四个回弹背景或G未管理。仍不能排除其他时刻或其他绘制层。

## 新诊断

- 位移回调 a(View,int) after，宿主已执行背景写入及位移之后触发。
- 顶部/窗口被管理节点背景重写时采集调用栈，最多每类setter两次。
- 触发时及后续两次preDraw，记录可见顶部和宽覆盖节点：类/资源ID/范围/背景与前景类及颜色/tint/管理和归属/滚动与位移/自绘声明；Frosted内部开关/色值/顶部高度；ActionBar内部h/i/m背景。
- 同时记录window系统栏颜色、chat状态、待刷新状态。每reason最多三次，总共最多14份快照，每份最多65条节点。不是持续全窗口扫描；关闭或销毁解除监听。

这版只有诊断，未增加未经证实的透明化Hook、没有改变回弹/聊天行为。新证据用于区分遗漏覆盖层、同步保护未生效与独立自绘。先冷启动直接微信页下拉回弹，再进入聊天返回观察标题/状态栏，日志和录屏同次采集。
