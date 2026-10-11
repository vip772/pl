# 配置导入导出重复提示修复

## 问题来源
旧 ConfigImportExportBridge 在 MiuixSettingsPagePart02.kt 中分别调用 hookAllMethods(activity.javaClass, onActivityResult) 和 hookAllMethods(Activity.class, onActivityResult)。

本项目现代 Xposed 兼容层的 hookAllMethods 会遍历整个父类链，因此第一处已包含 Activity，第二处再次注册基类；继承链中的 super.onActivityResult 还能进入其他已挂钩实现。旧桥接只判断请求码，不判断回调 Activity，不消费 currentActivity/onImported。结果是同一结果可以多次执行导入、导出、页面刷新和 Toast，而不只是提示重复。

用户日志 Hchat-runtime-20261011-133038.log 未记录配置成功 Toast 次数；源码确认重复处理机制，具体设备上五次对应的每个栈帧尚未抓取。

## 修复范围
- 将配置文件结果桥接独立为 ConfigImportExportBridge.kt，备份 JSON 格式、配置名单及读写逻辑不变。
- 沿继承链仅 hookMethod 每层 getDeclaredMethod 找到的三参数回调，避免 hookAllMethods 重复遍历。
- 待处理请求记录弱引用 Activity、请求码和导入完成回调。
- 同步校验 Activity 身份与请求码，在任何读写、刷新、提示前清空 pending；取消、空 Intent/URI 也消费请求。
- 同一待处理操作期间拒绝重复启动；失败时清空状态，导入备用选择器也启动失败时显示一次错误。
- 页面刷新失败不会重做导入，也不会谎报已刷新。
- 不修改全局 Xposed 兼容层、不迁入上游优化、不改其他文件选择功能。

## 验证
运行 python3 scripts/tests/config_backup/run.py。测试编译生产桥接代码，用 Android/Xposed/备份函数桩模拟五层回调链：
- 导入、导出、Toast、刷新只执行一次；连续操作不增加 Hook；
- 错误 Activity/请求码不消费当前请求；
- 取消、空 Intent/URI、重复结果不重复处理；
- 主选择器失败备用成功、全部失败后重试、导出启动失败；
- 重复点击、无配置、旧 Activity 已结束、刷新异常。

此验证不是完整 APK 构建，不包含真实文件系统读写、Xposed 框架实机行为或 Activity 重建恢复。仍需要安装修复版后在主微信与分身分别实测成功、取消、失败与再次选择。未提交或推送仓库，未安装或重启微信。