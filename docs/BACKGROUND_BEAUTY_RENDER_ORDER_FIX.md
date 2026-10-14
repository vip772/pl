# 3897a8f 闪底、残影与波纹修复依据

输入：2026-10-14 07:50/07:56 两份运行日志、LSPosed、同构建 mapping 与四页截图。

## 证据

- 两份 imageDraw 均出现 alpha=127，bitmap 有效且源图 alpha=false，绘制范围覆盖窗口。此前主页取消底层后，只用半透明图片绘制窗口，不能提供完整不透明背景覆盖；截图显示旧文字头像重复残留。改为窗口 Drawable 内先绘制不透明主题合成底再绘制用户图片，内容/标题/系统栏/底栏依旧透明，用户图片 alpha 不被强制改成 255。主页背景报告 OPAQUE，聊天路径不改变。
- 07:52:21、07:52:25 g6w 重写为原生 ColorDrawable，pending=true/image=null；07:52:37 statusBarBackground 与 ActionBar 被写成原生色。旧 setter Hook after 只 postOnAnimation，允许原生状态保留到下一帧。现在 after 返回前重新挂回同一受管理 Drawable，并保存宿主最新背景用于恢复；PhoneWindow 系统栏 setter 仅在主页背景受管理时固定透明。
- 源码 MAX_LAUNCHER_BITMAPS=2，淘汰调用 restoreSlot，会恢复被淘汰页所有背景。四页切换反复触发恢复及异步加载。改为四页缓存，图片淘汰不触发界面恢复；相邻已创建且已配置背景的页面也处理，避免只处理选中页。
- onBindViewHolder 日志 parent=null，旧处理必须已有 parent 才应用；仅按 dispatchChildAttached 名字查找不能证明混淆后的入口命中。注册到已确认主页列表的绘制前监听，处理未管理/脏条目，在绘制前完成透明化。只遍历当前附着行，非脏且已管理行跳过，不扫描全窗口。
- 水波纹旧代码仅在 originalBackground 非空时建立，已存在透明标记时提前返回，且 AbsListView.selector 未接管。移除原背景非空限制，允许透明标记升级，ListView selector 改为纯 RippleDrawable 包装，关闭恢复原 selector；使用灰色透明波纹改善浅色图可见性。

## 验证与边界

新增生产 Drawable JVM 回归：50% 图片先绘制底层、连续两帧覆盖顺序、窗口 OPAQUE、聊天保留透明合成。已有纯波纹与 CANCEL 重入回归。定向 Kotlin 类型检查使用 Android jar/DexKit 和项目边界桩，不是 APK 构建或真机 GPU 测试。

没有禁用硬件加速或调用无限 invalidate，没有宣称宿主单一原因导致全部残影。现有诊断保留以确认新包实际背景写入/绘制；四页缓存增加常驻图片内存（按四张解码尺寸计），有上限并随 Activity 状态清理。仍需实机验收初次加载、页面切换、回弹、滚动、水波纹、聊天/小程序、自定义底栏和关闭恢复。
