# 消息媒体转发修订与验收边界

状态：源码修订及定向验证进行中；尚未完成新包实机验收。工作分支 dev，基线 HEAD 27f5684e3a6d423579ee08d1f47bb04da2be6a7a。

## 故障与证据

用户确认视频给好友/群聊实际发送成功，但模块界面等待、不结束；图片未发送且等待。两类现象分别核查，不能用退出页面证明投递成功。

本次读取用户授权的 `/storage/emulated/0/Download/wechat8078_decoded/`。8.0.78 的 `smali_classes12/com/tencent/mm/ui/transmit/MsgRetransmitUI.smali` 中，`i7(String)` 构造 SendMsgUsernames/sendResult；g7 视频链路及异步路径可以直接 finish。该类声明自己的 finish()，仅监听 i7 不能覆盖所有媒体退出。旧安装 LSPosed 日志的重发等待超时是修改前证据，不能作为修订后验收。

图片异步入口见 `smali_classes12/qs5/q1.smali`：读取文件列表或 Retr_File_Name，经 VFS 检查并构造发送参数。该文件直接调用 `com.tencent.mm.vfs.z6.E(String): InputStream`。`qs5/v5.smali` 的 b 和 kj 都包含 sendImg: args error 锚点，均为 9 参数 void 方法，首参 Context，随后接收人、文件路径、int 标志、两个 String，与当前 DexFinder 基础筛选及调用位置相符。b 的末三参为 String、k7、s0.d；kj 的末三参为 k7、ScanCodeInfo、SourceImgInfo。空 appId 使用现有短签名参数填充，不代表已验证异步任务投递。

图片未发送的唯一根因仍未确认；目前没有新包日志证明文件解析修订已解决该现象。此次只完成 8.0.78 新材料核查，其它版本新增行为尚未重新横向验证。

## 源码修订

- 通用完成 Hook 改为后置观察，让原生方法执行；finish 后投递主线程收尾，仅处理匹配当前批次 token 的页面。无结果退出不增加成功数，旧 token、重复或空 token 不推进新批次。
- SelectedMessageMediaFiles 统一模块发送、朋友圈和系统分享的媒体解析，接受非零、可读文件，必要时复用原生对象和公共路径 API。图片/视频补充 z6 VFS 候选，命名入口检查 static 和 InputStream 返回类型。
- 真实聊天图片继续原生转发，保留实况和旧版安全边界；只有 msgId <= 0 的图片快照允许模块图片直发。图片反射调用抛出的异常返回失败，避免静默反射失败被当成受理。
- 视频原生重发优先使用原消息 imagePath 标识，文件物化后的路径用于直接发送、分享与朋友圈编辑。
- 聊天转朋友圈的媒体解析及视频缩略图准备移至现有后台执行器；编辑器的 KSightThumbPath 使用生成的缩略图，准备失败提示错误，关闭或 Activity 销毁后不打开编辑器。此入口使用 Toast，无新增全屏等待遮罩。

## 验证

定向检查不运行 Gradle，不产出 APK。`scripts/test_forward_sdk.py` 编译真实转发菜单/Hook/发送器等 Kotlin 与媒体 Java，使用本机 Android SDK、缓存 ABI 及临时设置开关替身；不能证明全项目构建、R8 或真实 Hook 兼容性。

已通过的合成专项：

- test_forward_completion.py：生产完成处理函数的成功、无结果退出、重复信号、过期/空 token、关闭、缺失批次、日志异常。
- test_selected_sender_admission.py：入队容量、拒绝退款、回调异常与重入。
- test_selected_senders_shutdown.py：待入队/活跃/排队资源释放与迟到入队阻断。
- test_selected_send_budget.py：容量、溢出、并发准入和只退款一次。
- test_selected_late_publication.py：关闭/替换批次的迟到发布及协调器 detach 竞态。
- test_selected_feature_detach.py：清理隔离、队列和主线程回调释放；联系人预热部分仅结构检查。

本机日志目录 `/tmp/pl-forward-fix/`：sdk.log、sdk-final.log、completion.log、admission.log、shutdown.log、budget.log、late.log、detach.log。临时日志不是可长期复现的仓库产物，应以脚本重跑。

## 剩余边界

需新包核查好友/群聊视频发送后的界面退出和提示、普通/实况图片的实际发送、原生回退、多目标串行、取消与关闭、朋友圈图片/视频编辑、系统分享。没有结果的原生退出目前可能显示部分失败，即使消息实际已发出；在没有可靠发送结果前不虚报成功。图片故障未确认闭环，全部新代码实机验收为未完成。

本轮不运行 Gradle、不构建 APK、不提交、不推送；保留头像提醒等既有未提交修改。
