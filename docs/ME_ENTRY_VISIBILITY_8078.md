# 我页面条目隐藏（微信 8.0.78）

入口：侧边栏 → 主题 → 我页面背景下方的五个独立开关。背景设置总开关打开后显示这些控制项；隐藏无需选择背景图片。隐藏开关本身独立保存，不随背景图片删除或背景总开关关闭而重置。默认全部关闭；目前运行路径只为 8.0.78 安装，其它版本保留配置但不执行过滤。

|显示名称|原生 key|
|---|---|
|收藏|settings_mm_favorite|
|朋友圈|settings_my_album|
|作品|settings_my_finder_album、settings_my_finder_and_biz|
|小店与卡包|settings_mm_cardpackage、settings_mm_cardpackage_new|
|表情|settings_emoji_store|

证据来自用户提供 `/storage/emulated/0/Download/wechat8078_decoded/`：`res/xml/b5.xml` 定义列表；`smali_classes9/com/tencent/mm/ui/MoreTabUI.smali` 的 onActivityCreated 将父类 d 保存到 s，r1 更新朋友圈、n1 更新新旧卡包、作品更新覆盖两种灰度入口。`com/tencent/mm/ui/base/preference/g0.smali` 的 l(String,boolean) 写 g 隐藏集合；q() 将 e/f 中未隐藏项写入可见 h，随后整理相邻空分类并生成行布局标志。

只登记 MoreTabUI.onActivityCreated 实际持有的 g0 实例。q() 执行期间临时叠加用户选择的七个原生 key，结束后撤销本模块新增的 key；微信原本的 g 集合保持原样。因此微信后续显示/隐藏入口仍按原生逻辑维护，关闭开关不会错误复活账号不支持的灰度入口。列表过滤收起整行，空组沿用微信 q() 的分类整理，不使用匹配中文文字或全局 View 隐藏。其它页面的 g0 不在登记集合内，不执行过滤。

配置变更触发已登记列表重建；销毁时撤销 Hook 并按原生状态重建。适配器登记使用弱引用，临时叠加仅执行期保留，不持久化。异常走 HLog.e 限次报告，含版本和描述符。

五个布尔设置写入现有 `Hchat_background_beauty_config`，统一备份自动收录；无新增受管理资源或持久化运行缓存。既有 method cache 按备份规则排除。`scripts/tests/config_backup/archive.py` 补充五个 true/false 配置导出导入往返与运行缓存排除验证。

`scripts/test_me_entry_visibility.py` 编译生产过滤器与抽取的真实 Hook 方法（平台类型以 JVM stub 提供），覆盖32种开关组合、两组灰度入口、原生隐藏状态保留和关闭恢复。它不等于 Android 全量编译或实机验收；安装新包后仍需验证五项单独开关/全部开关、空白分组、重新进入我页、原生刷新、账号切换及背景效果。尚未本地运行 Gradle、未构建 APK。仅有用户提供的8.0.78证据，未宣称其它默认版本集合通过兼容验证。
