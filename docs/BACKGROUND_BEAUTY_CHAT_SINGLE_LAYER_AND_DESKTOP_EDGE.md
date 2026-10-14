# 聊天单层背景与小程序底部残留修复

入口为主页侧边栏「主题」，对应 `background_beauty` 设置页及 `BackgroundBeautyFeature.kt`。

## 本次依据

- 基线：dev `107cb950e338417c9fe77b684b60e8ae103e1ca9`。
- 用户截图及 `Hchat-runtime-20261015-002550.log`，配套同基线 release mapping。
- mapping 确认 `oi.b` 为模块反馈 drawable、`oi.j0` 为透明 drawable、`oi.k0` 为全屏图片 drawable，日志里出现这些名称不代表微信自绘类。
- 微信 8.0.78 APK 的 `ChattingUILayout.dispatchDraw(Canvas)` 在绘制子控件后，用字段 `q:Paint` 补画高度为 `p` 的底部矩形；`fitSystemWindows` 会重新设置该画笔颜色。
- 微信 8.0.78 `ConversationFolderItemView` 继承 `X2CLinearLayout`，自身没有覆盖 draw/dispatchDraw；日志显示小程序完全展开时，任务栏容器高 3068、预留 52 px，折叠聊天行从 y=3065 延伸到屏幕底部3120，原先延迟清除该行背景/前景后仍露出其内容。
- 既有 `ActionBarContainer` 独立 primary/stacked/split 背景处理此前直接排除了 CHAT。

## 修改

1. 聊天图片只在 DecorView 绘制。原生 `ChattingImageBGView` 保留布局但替换为透明 drawable，避免按聊天内容区 CENTER_CROP 再画一次；宿主重新设置 drawable/bitmap 时保留最新原生内容供关闭功能恢复，同时维持透明层。
2. 聊天窗口底图与主页一样先绘制不透明主题底色，再按用户配置的图片不透明度绘图，避免透出不同底图。Decor 背景保护同时覆盖 CHAT。
3. 清除聊天图片和聊天布局至 Decor 之间的背景/前景，标题栏沿用已有独立背景层清除与恢复逻辑；公共栏也清除前景。
4. 绘制聊天底部补色时临时令已确认的 `q:Paint` alpha 为 0，绘制结束恢复；不跳过聊天子控件，也不修改键盘 inset 高度。
5. 小程序展开且折叠聊天行仅在屏幕底部露出时，跳过该行整个 draw，避免背景清除后文本/前景仍绘制。限定实际类、任务栏打开状态、屏幕底部位置、启用状态和主页图片配置；收起后直接恢复原生绘制，不修改可见性、尺寸或触摸。移除诊断回调内延迟扫描并修改该行子树的旧处理。
6. 全屏 drawable 每次实际绘制重新计算窗口坐标，覆盖键盘、窗口尺寸和锚点位置变化；移除坐标计算中的自 invalidation，避免持续重绘。

## 验证与范围

- 不执行 Gradle、本地应用编译或 APK 构建，交由 GitHub 构建及用户实机验证。
- `python3 scripts/tests/background_beauty/run.py`：独立 JVM 回归通过，覆盖实际生产 drawable 的锚点移动、窗口高度变化、不自触发重绘、图片不透明度、底色及反馈绘制。修正该脚本此前依赖已删除 `interceptEdgeTouch` 的过期 DrawerHarness，保留独立边缘手势用例。
- `git diff --check` 通过。
- 本次新增内部画笔字段依据来自 8.0.78；未完成其它微信版本横向 APK 验证，不能据此宣称其它版本兼容。字段不存在或类型不是 Paint 时保持原生绘制。
- Android 硬件绘制、导航手势区域、键盘开合及页面切换最终效果仍待实机验证；截图中的灰条来源以运行日志限定候选，本次不宣称已实机消除。

建议实机检查：小程序完全展开及收起；进入聊天顶部/输入框/导航区是否连续；聊天滚动及键盘开合；退出聊天恢复主页；关闭沉浸式背景后原生图片、标题栏和底部补色是否恢复。
