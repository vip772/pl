# 0736 实机反馈：返回纯底图、语音表面、描边和表情栏

基线 `7095468`；用户 `Hchat-runtime-20261015-073640.log` 及对应 mapping、截图。本文更新上次方案，最终效果待用户实机复核。

## APK 核对依据（微信 8.0.78）

- `NewChattingTabUI.f(boolean)`：设置 `k=true`，调用 `s(0)` 恢复主页，随后调用 ChattingUIProxy.onExitBegin/onExitEnd 并启动退出动画。`uf.onAnimationEnd` 才设聊天容器 GONE。之前单独在聊天容器绘制不透明底图，在退出生命周期清理聊天内容期间仍可能遮住后方主页；当前日志未完整覆盖所有退出阶段，不能认定已实机证明具体消失帧。
- `SpeechInputLayout`（classes16.dex）构造加载 layout `0x7f0e1c2a`，实际文件 `r/p/ew_.xml`。其中 `m`（0x7f0a6f5c）、`o`（0x7f0a6f60）为普通 View，分别使用 `r/z/dck.xml`、`dcl.xml` 实色椭圆背景。已有扫描处理容器与图标但漏掉这两个 View。
- mapping 确认本基线 `oi.k0` 是模块透明 drawable，`oi.b` 是反馈 drawable，不能将日志里这些名称误认成微信阴影实现。
- `SmileyPanelImpl`（classes12.dex）构造加载 layout `0x7f0e0661`，实际文件 `r/p/a77.xml`；其 manager `f.m` 是普通 View 标签背景层，`f.l` 为标签容器、`f.p` 为内容 NestedBounceView。`setTabBackgroundColor/Resource`、`setShowClose` 会更新 m 背景，内容 p 还有独立 foreground。
- `gk.D()`（classes15.dex）读取微信自身深色模式和跟随系统设置，适用于描边颜色判断。
- ChatFooter.C 为旁边表情按钮。APK raw 图标路径经过打包，未据其它模块 SVG 直接套用固定线宽；运行时绘制实际 drawable 到小尺寸透明 bitmap，采样上部中心的连续圆环像素，结合实际 imageMatrix 的 Y 缩放确定描边物理像素宽度。

## 修改

1. 在已核实的 `s(VISIBLE)` 且 `k=true` 退出入口，把本模块管理的聊天容器不透明图片背景转为透明，使后方主页可绘制；退出中的普通刷新同样不重装该图片层。不强制隐藏宿主页面，不跳过宿主退出回调。保留正常进入与自定义分组容器识别。
2. 仅对 SpeechInputLayout 子树内普通 View 的独立背景/前景做透明处理，保留语音图标和进度控件。
3. 深色描边白色，浅色黑色。线宽基于实际表情 drawable 的圆环采样与缩放，按 drawable+缩放缓存；无法采样时明确日志并回退已有1.2dp。描边样式缓存包含颜色和线宽，模式变化后更新。
4. 针对 SmileyPanelImpl 的 m/p/l 背景、前景透明化，覆盖附着、布局和已核实的背景设置入口；已有面板也在聊天背景应用时处理。不清除表情图片、删除按钮图标、分类选择状态。

## 针对性日志

- `CHATRETURN`：关闭前后、下一帧、主页容器恢复可见时，记录 k/l、主页/聊天/快照的可见性和背景、动画状态、聊天直接子控件。
- `CHATPART speech-before/after/speech-icon`：语音背景层处理前后及图标 drawable、bounds、elevation/translationZ。
- `CHATPART emoji-before/after`：面板 m/p/l 各层处理前后。
- `CHATSTROKE`、`CHROMEINPUT outlineStyle`：实际 drawable、采样圆环宽度、缩放、最终线宽和颜色。日志限频，采样失败输出 HLog.e。

## 验证

没有执行 Gradle、本地应用编译或 APK 构建。独立 JVM 背景绘制/反馈/手势回归通过，`git diff --check` 通过；现有回归不覆盖 Android 页面动画、实际图标采样、微信表情面板。请实机复测普通返回/滑动返回、浅色和深色描边、语音图标正常/录音状态、打开与切换表情分类。内部结构仅核实8.0.78，未完成其它版本横向验证。
