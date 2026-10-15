# 自动缓存、侧边栏与悬浮底栏背景透明度

## 悬浮底栏

背景不透明度范围 0–100%，默认 100%。背景胶囊、液态玻璃、阴影和背景高光单独绘制在图标下方；透明度只作用于这一背景层。图标、标签、红点及未读数字保持原有不透明度。选中项指示器保持原显示效果。配置仍为 `Hchat_custom_bottom_bar_config/custom_bottom_bar_opacity`，兼容上一版已保存数值。

## 首页侧边栏

“增强”和“新功能”共用原设置页，增加“昵称头像”独立开关 `Hchat_home_side_panel/show_profile`，默认开启。关闭时隐藏模块自定义标题头，恢复微信原生标题及由微信维护的未读数；长按原生微信标题切换侧边栏。标题特效改为绘制在原生标题前景，保持原特效配置。重新开启后恢复头像昵称和点击面板行为。关闭侧边栏、切换页面或销毁时恢复原生标题前景和长按监听。

## 自动缓存

独立总开关默认关闭，沿用 `Hchat_auto_cache_config/enable`；增强、新功能及搜索入口保留。不筛选私聊、群聊或文件扩展名。处理图片、聊天视频和 AppMsg 类型 6 的附件文档，不把网页链接、小程序等卡片当作文件。

本版删除上一版下载到 `Hchat/AutoCache` 的独立副本逻辑，改成微信内部原生下载：

- 图片：按真实接收消息的服务端 ID 查询微信 ImgInfo2，取得图片本地 ID 和高清父图标记，调用原生 NetSceneGetMsgImg；有原图记录时使用高清模式。由微信写入其图片文件并更新下载状态。
- 视频：使用消息 imgPath 查询原生 VideoInfo，调用 VideoLogic 离线完整接收入口，由微信调度下载并写回状态。
- 任意扩展名的文件文档：通过 AppMsgLogic 的 initDownloadAttach 初始化缺失的附件记录和微信原生路径，再构造 NetSceneDownloadAppAttach，提交微信网络队列。已有记录优先复用；下载完成状态由微信维护，不手动伪造成功。
- 未上传完成、元数据未写齐、网络未就绪时每五秒补查最新消息，最长一小时；已提交任务每分钟检查重试条件，完成后退出队列。观察来源仅使用统一 message.observe，不另订阅重复的新消息来源。
- 当前登录账号未就绪时不提交；切换账号、关闭开关和销毁功能会停止排队任务。微信内部已提交的任务可能继续执行。
- 下载成功后直接从原聊天消息打开。不会继续产生新的模块独立缓存副本；上一版已保存的副本保留。

需要微信进程运行、网络可用和足够存储空间。及时成功下载可避免未下载附件随后失效，但不能恢复已过期内容，也不能保证断网、服务器拒绝或本地文件被清理时仍可打开。

## 源码依据与验证边界

本次新增原生入口已核对设备微信 8.0.78 的 Smali：

- `l51/j` 的 DownloadImgService 创建 `l51/t0`，参数为图片本地 ID、消息本地 ID、talker、压缩模式、进度监听和场景；`l51/t0` 中 `MicroMsg.NetSceneGetMsgImg` 构造器与这些参数一致。
- `l51/l0` 的 ImgInfo2 建表定义确认 id、msgSvrId、reserved1、offset、totalLen；入站图片按 msgSvrId 查询。`l51/b0` 持久化转换确认 reserved1 是原图父记录 ID。
- 聊天图库 `db` 的 `start offline mode receive` 分支调用 `s61/d3.K(String)`；该入口更新 VideoInfo 接收状态并启动微信下载调度。通过日志锚点及签名排除在线流播入口。
- AppAttachNewDownloadUI 的 Y7 调用 `AppMsgLogic.initDownloadAttach`；X7 构造 `k04/l0(long,String,String,progressCallback)` 并提交微信网络队列。`appattach` 中 msgInfoId、msgInfoTalker、status 的结构和下载完成状态 199 已确认。

运行时用日志字符串和参数签名定位，不硬编码上述混淆类名。新增定位均通过 DexMethodCache 按实时微信运行时 key 缓存，在 WARMUP 阶段经统一 DexInstallScheduler 串行定位和有限补装。入口未全部定位成功时不启用自动下载，不回退到上一版独立 CDN 副本。

当前直接逆向证据来自 8.0.78；未完成其它微信版本的横向 APK 核对，不能宣称全部版本可用。按用户要求没有本地编译或构建，已进行源码调用签名核对、入口配置检查和 git diff --check。GitHub 构建后需实机检查：背景 0% 图标清晰、昵称头像开关切换及未读数/特效/长按入口、私聊和群聊图片原图、视频、普通及大附件、上传占位消息和断网重试。
