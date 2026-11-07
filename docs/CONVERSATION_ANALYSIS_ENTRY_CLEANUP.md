# 群聊分析入口清理与冷启动 Activity 依赖修正

基线：dev / c8ca86517880415262ce6d8ee07c85e8ff103c81。

## 入口范围

删除分组子页面会话长按的“群聊分析”扩展：移除 ConversationAnalysisRuntime.kt，删除 Feature 中的创建、安装、销毁调用，并更新定向编译脚本的源文件列表。

群聊分析只保留聊天消息长按“分析”作为报告入口。设置页、消息菜单开关、报告、统计、AI 生成及截图发送不因入口删除而改变。共享 ConversationMenuExtensionRegistry 仍被自定义头像、快捷群标签等功能使用，保留其公共实现及其他功能注册。

原分组入口只在进程内注册，没有独立持久化设置或缓存，不需要配置迁移。历史资源生命周期证据保留原始记录，不改写成此次验收证据。安装新版并重启微信后旧进程注册随进程退出消失。

## 冷启动路径

旧代码点击“分析”时只通过 WeChatApis.currentActivity() 获取 Activity，返回空值便静默退出。源码确认这是可能导致点击无响应的失败条件，但没有故障时日志证明它是用户此次长时间等待的唯一根因。

创建消息菜单时，从消息 View.context 解开 ContextWrapper，使用弱引用保存有效 Activity；点击时优先使用此 Activity，失效时再使用公共跟踪接口。循环包装使用身份集合防止死循环；拒绝 finishing/destroyed 的 Activity；沿用销毁前及 UI 线程内状态检查。不保存 View、Context 或未使用的原生消息强引用。

解析只在消息菜单创建时执行，没有新增聊天列表绑定、滚动或逐帧任务，没有新增线程、延时或重试。

## 验证范围

- 群聊分析全包、SingleMessageMenuLocator 和相关 core 源码以 Android SDK + 现有项目 ABI 定向编译；FeatureToggleRegistry 使用隔离夹具。不是 Android/R8 构建。
- test_analysis_entry_activity.py：提取生产 Context 解包及点击 Activity 选择，验证嵌套包装、循环包装、全局 Activity 缺失、归属优先、销毁/结束及回退；静态核对分组注册与专用类已移除。
- 既有菜单图标/布局及页面生命周期夹具回归。
- 未运行 Gradle，未构建 APK。冷启动首开时延、真实菜单点击和功能完整效果仍需新版实机验收，不能以以上检查替代。
