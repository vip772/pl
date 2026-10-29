# 悬浮标题栏背景透明度

标题栏透明度覆盖返回、标题、更多胶囊及标题卡片背景。配置键为 `floating_chat_header_transparency`，沿用现有配置监听，在主线程更新缓存并触发现有刷新。Drawable 复用，仅在颜色变化时更新。

`FloatingChatOpacity` 保留 RGB 并计算 alpha，覆盖 0–100 范围、缺失值默认行为及越界约束；`FloatingChatCardVisuals` 保留标题栏圆角与深色描边。没有新增 Hook、滚动监听、定时任务或数据库访问。

验证脚本为 `scripts/test_floating_chat_opacity.py`，包含生产卡片 Android SDK 定向编译与透明度回归。未进行完整 Compose 页面编译、APK 构建或新包实机验收。
