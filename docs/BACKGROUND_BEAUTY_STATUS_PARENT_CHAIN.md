# 微信首页状态区父链透明修正

基线：dev / 8338c4d；对照：c9932b0。用户反馈上一版状态区仍不透图。本轮只做静态检查，不运行 Gradle、Kotlin/JVM 编译或 APK 构建。

## 已核实的证据

- c9932b0 到基线的 applyBitmap、applyChromeTransparency 及 applySystemBars 主流程未删除首页 Decor 底图和 LAYOUT_FULLSCREEN 策略。直接回退整个功能会同时回退独立的生命周期与聊天修复，没有证据支持这样做。
- 微信 8.0.78 的 MainUI.B0() 使用 getController().g 查找或创建 og9（2131389426），随后写入实色背景。MainUI.initView() 将 nhm（2131387734）存入 r。bvn 布局中 MainUIView 下 ConversationListView 与 nhm 为兄弟节点，nhm 在列表之后绘制；og9 则是控制器额外持有的节点。
- 原处理仅从 Decor 查找两个 ID 并清其自身背景，未检查遮罩父链。片段尚未挂入 Decor 时，按 Decor 查找无法取得 B0 实际持有的节点。
- HostState 已由 Decor 的 hchat_background_host_state tag 强持有。弱索引本身不能证明活动页面状态丢失，8338c4d 的说明不能作为本次问题已定位的依据。
- BottomBarNavigationAppearance.restore() 将创建时的 systemUiVisibility 整值写回，会删除创建后由背景功能追加的 LAYOUT_FULLSCREEN。这是确定的所有权冲突；没有此次设备调用序列，不能认定它就是截图的唯一原因。

## 改动

MainUI.B0() 和配置变更回调传入原生 MainUI。仅在已核实的 8.0.78 使用 getController().g 与 MainUI.r 获取实际遮罩实例，保留原有 Decor ID 查找路径。主页图片已加载且当前页面为微信时，沿两个遮罩的父链使用原有 SurfaceSnapshot 透明化与恢复机制；共享父节点用 identity set 去重，遇 Decor、图片承载层或聊天归属立即停止。此操作不进入兄弟列表或条目，不改 padding、margin、前景及层级，不新增逐帧监听或延迟重试。

底栏恢复仅合并其管理的 LAYOUT_STABLE / LAYOUT_HIDE_NAVIGATION 两个位，保留当前 FULLSCREEN 与其它无关位；导航栏其它逻辑保持原样。

## 静态验证与边界

运行 scripts/check_background_status_scope.py：检查原生引用路径、版本限制、页面及图片就绪条件、父链边界、无新增逐帧回调；穷举 4096 组窗口标志组合，验证 FULLSCREEN 和无关位保留、底栏所管位按快照恢复。git diff --check 通过。既有会调用 Kotlin 编译器的 JVM 回归本轮不执行，以遵守只做静态检查的要求。

上述检查不验证 Android 实际绘制、Insets、挂载时序或截图对应的唯一实色贡献层。现有设备窗口查询未取得可用的微信窗口证据；本轮无修复后的设备验收结果。原生字段路径只核实 8.0.78，不声明其它版本兼容。

待实机验收：首页冷启动、四页切换返回微信、进入聊天返回、深浅模式切换、下拉小程序收起、切换底栏模式以及关闭背景后的原生颜色恢复。
