# 右划复读文案弹窗首帧定位修复

## 原因

两种文案共用 `SwipeRepeatDialogStyle.confirmation`。旧实现通过 `OnShowListener` 安装卡片并设置窗口宽度，窗口先挂载默认 AlertDialog 内容，再更换为自适应高度的卡片。居中窗口在挂载后发生尺寸变化，形成先显示再上移的路径。

Android 16 原生 `core/java/android/app/Dialog.java` 的 `show()` 顺序为：`dispatchOnCreate(null)`、`onStart()`、`mWindowManager.addView(mDecor, l)`，最后 `sendShowMessage()`。源码来源： https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/app/Dialog.java 。

指纹采用全屏窗口，按钮在 `FingerprintOverlay.onLayout` 中定位，与文案的居中自适应窗口不同。

## 修改

文案对话框在覆写的 `onCreate()` 内安装卡片、背景、内边距及最终窗口宽度，完成后才由 `Dialog.show()` 挂载窗口。移除文案的 `OnShowListener`，不增加延迟任务、额外位移或补偿动画。保留两种文案测量、按钮行为、取消与 dismiss 监听及确认保护。

## 检查与边界

- 右划复读完整生产包、当前相关核心源码及当前工具栏设置通过 Android 36 SDK 定向编译；使用已有工程 ABI 和 UI registry 夹具。
- 静态核查文案路径没有显示后替换内容或调整窗口尺寸的监听。
- 同批提交包含上一项视频号时间宽度修复，其收发夹具与 SDK 编译结果见 `FINDER_CARD_TIME_WIDTH_FIX.md`。
- 不运行 Gradle、不构建 APK。代码与定向检查完成，实机首帧位置及视频号显示效果尚待验收。
