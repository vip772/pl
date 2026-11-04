# 昵称头像首屏补挂与悬浮标题栏复核（2026-11-03）

## 状态

本次两个问题：首屏补挂代码已修改并通过无编译源码约束检查；标题栏偶发灰化尚未定位到复现瞬间的写入者，未修改其行为。代码修复 1/2，实机验收 0/2，整体未完成。不运行 Gradle、Kotlin/SDK 编译，不以静态检查代替构建、内存、帧时或实机验收。用户随后明确要求现在提交推送，本次仅提交首屏补挂代码、相关检查脚本与本记录；标题栏灰化仍未修复，朋友圈外观本地改动不纳入提交。

## 现场与首屏证据

仓库 /root/pl，分支 dev，起始 HEAD ab67d2565acfaf335bd5bc851854faec17a2a777。恢复现场时 HomeSidePanelRuntime.kt 已有一次性原生实例补挂草稿，保留并补齐，不按摘要误判为无改动。

runtime.log 在本轮只读复核时仍为 199435 字节，末次写入 2026-11-03 18:18。既有筛选件：/sdcard/Download/Operit/header-repair-20261103/relevant-runtime.log。日志记录侧边栏安装流程于 18:03:17.359 完成、18:03:27.386 才 headerAttached（startupMs=10164），18:03:27.390 才 sessionAttached。它证明存在安装后尚未挂载的窗口，但不记录首次 onResume，因此不能声称日志独立证明该次 onResume 必然被错过，或此路径是全部首屏闪现的唯一原因。

源码明确存在可达缺口：HomeSidePanelFeature.updateRuntime 在订阅后仅查询一次 currentActivity；WeChatCurrentActivityApi 依赖安装后的 Activity.onResume 更新弱引用，currentActivity() 本身只读弱引用。安装晚于首个回调且没有后续事件时没有主动补挂。/tmp/wx8078-review/LauncherUI.java 的 getInstance() 返回存活实例集合首项，集合为空则返回 null；同仓库 GroupBarRuntime.bootstrapHomepage 已采用原生实例兜底及 40 次/250ms 的有界引导。原生证据仅覆盖本机微信 8.0.78（3180），未完成其他版本横向验证。

## 修改范围与资源边界

仅在 HomeSidePanelRuntime.install 注册既有 Hook 后启动一次引导。反射通过 KavaReflector 解析一次 getInstance，在主线程优先查询公共 currentActivity、再查询原生实例；两路均过滤非 Launcher、finishing、destroyed Activity。成功建立 Session 或运行时关闭即停止；未就绪最多 40 次、间隔 250ms（首轮到末轮最多 9.75 秒，不含队列阻塞）。这是安装期有界任务，不是常驻轮询。

沿用原 handler 与 postActive 闸门；destroy 的 removeCallbacksAndMessages(null) 清理排队任务，active 闸门拒绝已竞态取出的回调。不新增线程、逐帧树扫描、滚动监听、常驻日志或全局 currentActivity 写入。Session、头像执行器、正在运行任务不打断、异常隔离清理、监听器/Hook 释放代码与 ab67d25 完全一致。此为源码保持证明，不是实际内存或流畅度收益证明。

## 灰化交叉审计与阻塞

FloatingChatActionBarBackgrounds 接管 primary/stacked/split 三层；BackgroundBeautyFeature.restoreBounceColors 经 onNativeWrite 转交；setTransparentSurface、installBackgroundWriteHook、restoreSurface 已检查 FloatingChatCardVisuals.contains。FloatingChatHeaderCapsule 自建胶囊表面，FloatingChatCardVisuals 管理背景和 tint。HomeSidePanel Session 对首页标题显隐与头像 foreground 的操作不等于聊天胶囊背景灰化写入证据；安装时间重叠同样不能证明互相牵连。

本轮未取得灰化瞬间的目标 View、Drawable/tint/alpha 状态和调用栈，现有 runtime.log 没有这些记录，且未出现新复现窗口。故不新增全局 setter Hook、刷帧修色或猜测性所有权改写，不声称灰化已修复。后续须先取得该瞬间的写入证据，再决定具体修改点。

## 本轮继续：核对真机最终产物与原生颜色路径

只读核实设备已安装 t.mma 10.9.0（versionCode 1169），更新时间 2026-11-03 18:02:37。复制已安装 APK 到 /sdcard/Download/Operit/header-repair-20261103/installed-module-1169.apk，SHA256 c0b7f2dd579c8c65a0fdd5f9a266d26c1731b3f0fe63ac48d89700f17bdc56a6；仅反汇编现成产物，未编译、替换或安装任何 APK，未重启微信、改配置或操作聊天。

最终产物已混淆，按真实调用链确认：gj/n 为三层 setter 安装分支，gj/e0.k 注册 gj/c0，gj/c0.beforeHookedMethod 调用 ak/d 的对应分支，后者调用 gj/a.a 查询所有权、记录最新 Drawable 并按结果把参数置 null。gj/a 对应三层背景所有者。由此确认旧保护分支确实存在于安装包，不能把复现归因于“旧修复没打包”；字节码存在不等于证明具体时刻 Hook 已执行。

微信 8.0.78 classes16.dex 定向反汇编 ha.p0(I)V：ColorDrawable -> appcompat.app.b.w(Drawable)；classes14.dex 的 y0.w 调用 ActionBarContainer.setPrimaryBackground。widget.c.draw 读取 h/i/m 三层。该原生颜色入口是现有守卫覆盖的 setter，不是新发现的绕过证据。ha.u0(I)V 另有对 jlt 和自定义内容的 setBackgroundResource，但没有复现 View 与此分支关联的证据，故不将其当根因、不添加 Hook。

LSPosed 模块日志尾部截至同日 18:03 的当前启动样本，筛选未提供灰化写入堆栈；runtime.log 仍为 199435 字节、末次 18:18，无新复现证据。没有据此排除所有外部模块或其他原生路径，也没有把“未见异常”当作没有问题。

新增 scripts/check_header_installed_evidence.py，对固定安装 APK 指纹、原生颜色调用链、三层绘制与安装包前置回调链作只读断言。命令：python3 -S scripts/check_header_installed_evidence.py --evidence-dir /tmp/pl-header-fix-20261103 --installed-apk /sdcard/Download/Operit/header-repair-20261103/installed-module-1169.apk，检查通过。证据位于 /tmp/pl-header-fix-20261103/native 与 installed/smali；脚本输出各文件 SHA256。它不是生产 Kotlin 执行测试或实机灰化修复验证。

本轮没有新增猜测性生产修改。状态仍为首屏代码处理 1/2、灰化定位阻塞、实机验收 0/2；整个双问题大项未闭环。后续按用户最新要求提交当前已完成的首屏代码与排查记录，不将提交视为两个问题已全部完成。

## 验证与历史改动保护

python3 -S scripts/test_home_header_bootstrap_source.py：源码约束检查通过，包含任务上限、主线程入口、有效 Activity 过滤、关闭闸门以及原有生命周期/Session 与背景相关生产文件保持检查。它不执行 Kotlin 生产代码。原 test_home_side_panel_lifecycle.py 和 test_floating_header_background_owner.py 会调用 Kotlin 编译器，遵守不本地编译要求未执行。

python3 -S scripts/test_weather_moments_surface.py --native-dir /tmp/pl-card-native-fresh：源码、绘制几何与既有原生证据检查通过；不代表实机验收。

朋友圈外观 5 个既有修改文件的 git diff SHA256 仍为 a04db15baf29fcdcc559a78e8d7c019e825fd8b3faaadb0dbc9ec3c85f04a7d1，与 /tmp/pl-header-fix-20261103/prior-moments.patch 一致；docs/MOMENTS_CARD_APPEARANCE_LOCAL.md 同样保留，禁止混入本次提交。
## 后续提交范围变更

首屏补挂代码与排查记录已以 dd3f945 中文提交并推送 dev。用户随后明确要求全部提交推送，现将此前保留的朋友圈外观代码、检查脚本及外观记录一并提交到 dev；前述不纳入提交的表述仅为当时范围，已被本次授权覆盖。标题栏灰化仍未修复，实机验收仍为 0/2，提交推送不代表整个双问题任务已完成。

## 昵称头像冷启动半裁剪修复（2026-11-04）

用户反馈新版本重启微信后首页顶部头像只显示一半。核对上一提交 `56776ac` 后确认，其改动仅涉及 `hchatextra` 收藏笔记卡片时间锚点，不触及首页昵称头像实现。现象对应 `HomeSidePanelRuntime.Session` 自身的冷启动布局竞态：`sizeHeader()` 在宿主标题栏高度尚未稳定时仍将头像包装器保持在至少 `38dp`，而 `refresh()` 同一轮已把标题根设为 `VISIBLE`；宿主高度小于头像最小尺寸时，父布局裁剪头像，形成半头像。

修复仅作用于昵称头像：`sizeHeader()` 返回宿主是否具备有效宽度和至少 `38dp` 高度；`refresh()` 在尺寸未就绪时将自定义标题根设为 `INVISIBLE`，保留布局占位，宿主下一次已有全局布局回调达到条件后再显示。没有新增线程、计时器、扫描或监听器；每次原有刷新只增加一次布尔尺寸判定。头像本体仍为 `38dp`，没有改动收藏卡片时间、其它首页功能或共享背景功能。

源码契约回归：`python3 -S scripts/test_home_profile_header_ready.py` 通过。该回归不替代 Android/R8 构建和实机重启、切页、滚动验收。

## 头像半裁剪复核与针对性修复（2026-11-04）

复核截图问题后发现上述修复没有约束头像子视图尺寸：`sizeHeader()` 将 `avatarWrap` 缩至宿主高度，但内部 `ImageView` 仍固定为 `38dp` 并居中。当宿主在启动/布局切换期间高度小于 `38dp` 时，即使标题根已延迟显示，子视图仍可能超出 wrapper 并被宿主裁剪；原有契约只验证宿主门槛及初始 38dp 值，未检查运行时父子尺寸关系。

本次在原有 `sizeHeader()` 调整点同步将头像 ImageView 限制为 `min(38dp, wrapperSide)`，仅在宽高/Gravity 参数变化时更新 LayoutParams。宿主稳定时头像仍保持 38dp；窄/矮宿主期间不再让子视图越出 wrapper。未修改图片解码、头像效果 foreground、名称/状态文本、抽屉入口、滚动标题及共享首页背景逻辑；每次既有刷新仅多做一次轻量参数比较，不增加回调、线程、计时器或扫描。

源码契约已扩展并通过：`python3 -S scripts/test_home_profile_header_ready.py` 验证运行时子头像尺寸不超过 wrapper。当前环境无 `android.jar`，未能执行 Android SDK 定向编译；静态契约检查不是编译或实机验收。仍需用户设备冷启动及页面切换实测确认视觉结果。
