# 首页冷启动背景与头像昵称优化

基线64786ef；输入Hchat-runtime-20261015-121124.log及同提交mapping。该日志以聊天绘制记录开头（首行被截断），无完整冷启动安装时间线，因此不能把用户观察的2秒/5秒作为已测得的某单一方法耗时。

## 已确认等待路径

FeatureRegistry原将BackgroundBeautyFeature放在大量功能之后，FeatureManager串行安装。背景解码原仅在页面刷新时启动。主页侧边栏attach固定post到主线程，标题宿主缺失时300ms重试；头像每个Session通过URL.openStream下载，没有持久缓存，无连接/读取超时，失败时avatarUrl提前赋值还会阻止同URL重试。昵称依赖账户API实时返回。

ModuleEntry在共享DexKit/DexFinder和FeatureContext初始化之后才安装所有功能，这一前置阶段仍存在。本次没有变更全局启动框架或在主线程执行DexKit，不能承诺更新后所有情况下首帧零等待。

## 修改

- BackgroundBeautyFeature移到首页侧边栏之后，提前安装已存在的页面Hook。
- 功能安装开始即在原后台loader预解码微信页图片，与Hook安装重叠；带revision的volatile结果交给首个主页刷新消费，避免主线程等Store解码锁。仍保留设置启用、revision校验和原异步加载路径。
- 标题栏attach在主线程直接执行；后台回调仍post。宿主未就绪的有限重试改为postOnAnimation，并合并待执行重试，继续保留全局布局监听处理稍后创建的标题。
- 昵称按wxid缓存到模块设置，当前账户身份确认后可使用；真实昵称返回后更新。
- 头像按wxid的SHA-256分文件，放HchatStorage/home_profile。后台先读缓存并立即发布到UI，URL变化、缓存缺失或缓存超过6小时再联网更新；连接/读取各4秒超时。成功后写临时文件再替换正式缓存；失败保留旧图，不把失败URL标成已完成。每次发布检查当前wxid与Session附着状态。
- 新增STARTUP homePreloadMs、headerAttached startupMs、avatarCacheMs；时长基点为功能/任务开始，不是进程创建或用户点击图标。

## 边界与验证

新缓存第一次建立仍依赖网络；账号身份尚未确认时不展示另一账号的资料。正常覆盖更新保留模块数据时可继续复用缓存，清除微信数据或删除缓存不在此范围。后台任务失败不影响现有页面和旧头像。现有群公告、分组Insets、返回和描边逻辑不变。

独立JVM背景/反馈/侧边手势回归通过；git diff --check通过。未执行Gradle、本地应用编译或APK构建，也未实测冷启动耗时。独立回归不覆盖Android账号API、磁盘/网络或首屏时序。

实机：安装后等待头像成功加载一次，再彻底关闭微信并重启；分别验证断网重启、覆盖安装后重启、账号切换、头像修改后刷新。检查背景和昵称首屏出现时间及缓存命中日志；如仍有延迟，需从模块初始化开始的完整冷启动日志来区分共享Dex初始化、Hook安装、图片解码、标题宿主和账号就绪耗时。