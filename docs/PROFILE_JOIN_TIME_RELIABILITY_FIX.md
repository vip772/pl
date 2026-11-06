# 群成员资料页进群时间显示核查

本轮基准：dev / 812b6ab；保留此前防撤回未提交修改，不提交、不推送。

## 已确认

- db92f65 设置迁移保留 ProfileGroupJoinTimeSettings 的配置库、开关键，UI 与 Provider 读取相同常量；该提交没有修改 ProfileGroupInfoFeature 的查询及格式化逻辑。
- 实名尾字 Provider 使用独立配置及结果缓存，不写入进群时间配置，也不删除群资料 Provider。公共头部按 Provider 逐项追加显示。
- 原有资料页 Provider 在 onFeatureInstall 注册，FeatureManager 只在启动时对启用功能调用 install。进程启动时全部关闭，随后只打开进群时间，不会注册它的 Provider。
- 原有头部 Hook 单次安装的 false 返回值未纳入重试。实名尾字新增的独立安装票据具备重试，因此原有功能不应借其启用来保证公共头部可用。

## 修改

ProfileIdFeature 初始化阶段为微信 ID、添加时间、进群时间及邀请人的现有共享 Provider 注册建立独立 PreferenceRuntimeOwner。四项配置均能刷新 owner；任一启用即注册 Provider 并通过独立票据调度公共头部安装，失败由现有调度器有限重试。全部关闭或销毁时移除这些 Provider、关闭票据并释放群时间查询资源；实名尾字继续由自己的 owner 管理。

未更改进群时间来源、历史数据识别规则或设置存储键，不恢复冷启动全群扫描。查询仍只在打开对应群成员资料页时后台加载。

## 验证与边界

定向编译入口：scripts/check_profile_runtime_sdk.py。使用真实 Android 36 SDK、当前 profileid 全包、公共头部 API 和资源所有权源码，依赖本机现有项目 ABI、依赖 JAR 以及 UI 注册边界夹具；不等价于完整构建。

回归入口：scripts/check_profile_real_tail.py，验证原实名 Provider、PreferenceRuntimeOwner 及资源作用域的已有行为；项目 API、网络及宿主使用夹具，不覆盖真实微信。

未运行 Gradle、未构建 APK、未做实机验证。上述安装缺口是源码证实的缺陷，尚无本次设备日志证明它就是用户所遇现象的唯一根因。若历史入群系统消息不存在、群成员数据为空、头部布局不匹配，仍可能不显示，不能填造进群时间。

统一实机验收：关闭实名尾字及其他资料页项，冷启动后打开进群时间，再进入有已知入群记录的群成员资料页；核验后台完成后补显示、再次进入、关闭再开启、实名尾字同时启用及单独关闭。必要时依据现有日志“未找到资料页头部绑定方法”“群成员进群时间索引完成/失败”区分安装、数据与布局问题。资源生命周期七阶段仍为 0/7。

本轮验证结果：定向 SDK 编译 PASS（/tmp/profile-runtime-sdk-final.log，进程退出 0）；实名 Provider/owner 回归 PASS（/tmp/profile-tail-regression.log）；独立 owner 四配置库接线静态断言与 git diff --check PASS（退出 0）。静态接线检查不等价于动态设置页或宿主行为验证。核查、代码修复、专项验证记录为 3/3，实机验收待完成。
