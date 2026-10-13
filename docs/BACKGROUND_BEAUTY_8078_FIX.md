# 微信 8.0.78 沉浸式背景与侧边栏触摸修复

## 基线与证据

项目基线：dev / e7f2a43132841c2ae9b76a8da536bcd4aa6ab4e5。
用户提供 APK：com.tencent.mm，versionName 8.0.78，versionCode 3180，targetSdk 34。
定向 JADX、Smali 与 aapt 资源检查确认：

- LauncherUI 持有 HomeUI，HomeUI 提供 getMainTabUI；MainTabUI.g() 返回当前主页 Fragment。LauncherUI.getCurrentFragmet 有聊天 Fragment 分支，不能作为四主页的唯一来源。
- MainUI.getLayoutView 使用 layout/bvn；会话列表存在 ListView 与 ConversationRecyclerView 替换路径。MainUI.B0 动态创建 id/og9（0x7f0a6bf2）并设置实色背景；回弹路径使用 NestedBounceView。
- MvvmAddressUIFragment.getLayoutView 使用 layout/df，内部为 FrameLayout/WxRecyclerView；初始化存在重新设置根背景色路径。
- FindMoreFriendsUI 继承 MMPreferenceFragment，使用 layout/c07；MoreTabUI 使用 layout/dvi。二者都有 TouchableLayout/PullDownListView。
- Preference 适配器 getView 会重设条目根背景和 id/cgi、id/cg7、id/oct 对应内容容器背景。我页面初始化及状态切换还会重设列表背景。
- 侧边栏原实现收起后保留左侧全高 72dp View，触摸监听始终返回 true，普通点击也被截走。

## 改动

- 主页根节点补找使用 getHomeUI → getMainTabUI → g，保留聊天 Fragment 分流及既有布局创建 Hook；补充 onActivityCreated 后刷新。
- 从已识别页面根节点向背景宿主清理中间背景；列表查找不再受三层深度限制，仍保留 1200 节点扫描上限、Compose 与底栏排除。
- 增加 NestedBounceView 颜色入口及已确认 og9/jlt 背景容器处理。
- 只对已登记主页列表处理条目绑定：ListView 实际适配器 getView、RecyclerView 实际适配器完整/payload 绑定和子项附着。其他页面列表不消费这些回调。
- 条目普通状态隐藏背景，按下、焦点或选中状态绘制原背景；不修改条目点击监听、文字、图标和子项前景。
- 背景 setter Hook 只对本功能已登记 surface 生效，未登记 View 快速返回；模块自身写入用深度计数防重入。宿主重新设置背景后保存最新恢复值，合并到下一帧刷新。
- 关闭或 Activity 销毁时恢复背景、tint、前景和系统栏；脱离窗口的 surface 在刷新时恢复并移除；销毁时移除本运行时 Hook 句柄。
- 抽屉收起时整个 root 为 GONE，不再有 72dp 触摸遮挡。Activity 触摸回调观察 24dp 边缘手势，DOWN/普通点击/纵向滚动继续交给宿主，确认横向滑动后给原触摸目标发送 CANCEL 并接管后续事件。
- 快速重开取消旧关闭动画；禁用时立即隐藏，卸载恢复原系统手势排除区域。
- 页面切换应用时记录少量 slot/surface/list 计数；该日志仅代表已执行应用，不代表真机所有绘制层都透明。

## 验证与边界

- 未运行 Gradle、未生成 APK、未安装或重启微信。
- 修改涉及的七个生产 Kotlin 文件使用 Android 37 android.jar、真实 DexKit 依赖与内部接口桩进行定向类型检查，通过；内部接口桩检查不是完整 Android 工程编译。
- `python3 scripts/tests/background_beauty/run.py`：生产手势状态类与生产反馈 Drawable 的 JVM 回归通过，覆盖点击抖动、纵向/反向/区域外/禁用状态透传、横向接管、取消复位、1000 次状态循环及按压反馈释放。
- `git diff --check` 通过。
- 当前只有 8.0.78 APK，未对其他微信版本及运行时 Tinker 补丁做横向确认。
- 尚未做真机四页显示、列表复用、系统手势、快速切页、深浅色、侧边栏菜单/返回、自定义底栏、小程序下拉及关闭恢复回归。不能将类型检查和 JVM 桩测试表述为所有功能已实机通过。
- 回弹颜色按实际 setter 保存宿主最后传入值，关闭、进入无背景页面或销毁时调用同一 setter 恢复，恢复期间禁止再次改写；仍需真机检查渐变方向及关闭恢复。

## 真机验收

同一安装版本下依次检查微信、通讯录、发现、我：首次打开、滚动、返回首页、切换深浅色、更换各页图片、透明度调整、关闭功能。同步检查底部四按钮、列表左侧头像、纵向滚动、边缘侧滑、菜单跳转、取消手势与快速开关抽屉。关闭沉浸背景后应恢复原生页面且不影响聊天背景、底栏及小程序下拉。未通过的项目继续按实际日志和 View 层级修复，不能用全局清空背景兜底。
