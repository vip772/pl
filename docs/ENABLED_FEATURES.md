# 已开启功能统计与刷新

“已开启功能”按已注册设置功能入口的 featureId 去重统计，不按内部开关数、已安装 Hook 数或脚本数量统计。组合功能任一有效子开关开启算一个入口；没有启用概念的工具、说明页不计入。该列表反映用户配置启用，不保证 Hook 在当前微信版本安装成功。

修复首页扩展、聊天工具栏、上传透明头像缺失的启用映射，分别读取 Hchat_home_extension_config/enable、Hchat_chat_toolbar_config/enable、Hchat_upload_transparent_avatar_config/upload_transparent_avatar_enable。隐藏条目按五个原配置开关任一开启判断。其余已登记的字面量库名和键名与对应功能目录中的配置声明核对；组合功能继续使用专用判断。未知入口默认不计入，不再猜测 Hchat_global_config 的同名开关。悬浮聊天头尾等已明确使用全局设置的入口保留显式映射。

聚合输入改为 UIRegistry 的全部 provider，不再只收实用与增强分类，因此包含娱乐类和脚本插件入口。脚本插件按总开关计一项，不按启用插件数量计数。列表和数量来自同一份去重后的功能列表。

返回已开启分组时重新查询当前启用状态，渲染分组时也刷新该聚合，不复用进入功能页前的旧列表。搜索分组移除仅按 provider 列表缓存的启用快照。分组滚动状态改按稳定分组名称保存，成员变化不创建新滚动状态。当前范围覆盖设置页切换返回，未增加全局配置监听或后台轮询。

scripts/test_enabled_features.py 定向编译生产 FeatureToggleRegistry、UIRegistry 和提取的聚合函数；验证字面量映射默认/开/关、娱乐和脚本聚合、去重、旧分组刷新、组合开关及工具入口排除。平台存储和部分设置依赖使用 JVM 桩，不替代真实 Compose 导航、FastKV 或整包编译；委托给设置类的 enabled/isEnabled 实现未在此脚本执行。未运行 Gradle、未构建 APK，需新包核对实际开启列表和返回后的数量。

本次没有新增持久配置或资源，备份格式不变。此前隐藏条目独立改动仍保留，见 ME_ENTRY_VISIBILITY.md。
