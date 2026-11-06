# 首页文字颜色晚安装覆盖修复

基线 dev / 60ef388，目标微信8.0.78 / 3180。用户报告暗黑模式文字着色不全面。

## 证据与结论

最新运行日志显示00:40:36.949初始化，00:40:53.587后台安装生效。旧代码仅在Holder创建Hook记录Holder到行View的映射；已创建Holder绑定时查不到映射即返回；安装完成也没有补刷已显示会话行。因此存在独立、可复现的晚安装漏色缺口。没有截图对应行的运行时对照，不能声明它是截图所有异常的唯一根因，也不能确认最近进群时间提交造成了它。

原生8.0.78证据：wechat8078_decoded/smali_classes11/jo5/i0.smali，b(jo5.s,View)末尾调用View.setTag(holder)；jo5.s保存标题、摘要等子View。a(int,jo5.s,jo5.z,storage.k4)为内容绑定。原生初始化/绑定入口继续由既有DexKit结构定位，不新增混淆类名依赖。

## 改动

仅改HomeTextColorFeature。映射缺失时按缓存的Holder View字段向上至多12层寻找tag与当前Holder身份完全相同的行。新装会话Hook后在主线程单次补刷当前Launcher可见列表，要求同时满足标题、摘要、时间锚点，并限制页面遍历预算2048节点；隐藏和无关子树不着色。沿用现有行颜色/Shader恢复及单次延迟回补，不修改全局颜色、共享调度器、其他功能或进群时间。销毁取消补刷并清理新增字段缓存。

## 验证与边界

- scripts/test_home_text_late_install.py：执行新增生产方法，验证安装前Holder、继承字段、旧身份拒绝、可见行补刷、隐藏/无关行排除、遍历预算，并对新增方法做真实Android SDK类型检查；通过。
- scripts/check_home_text_color_sdk.py：整个生产功能文件、设置、真实KavaReflector及compat源码，使用Android SDK和真实DexKit API定向编译；通过。宿主注册服务和成员颜色解析器使用fixture，不等同完整应用构建。
- 既有资料页回归和--production-reflector恢复对象回归通过，日志归档设备 /sdcard/Download/Operit/home-text-late-evidence/。
- 未运行Gradle，未构建APK、未做R8或新版实机验收。mapping附件未用于反混淆，没有最终产物验证证据。
- 新版需集中验证暗黑冷启动已有行、滚动复用、标题/摘要/时间、关闭恢复和其他页不被着色；明暗切换及原色恢复仍需实机确认。此次为已证实覆盖缺口修复，不宣称整个暗黑问题已验收完成。资源生命周期七阶段保持0/7。
