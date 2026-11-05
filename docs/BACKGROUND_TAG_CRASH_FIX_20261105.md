# 沉浸式背景原生标签比较崩溃修正

日期：2026-11-05。修复基线：`dev` / `a19ccf3c5a35932fbf358c5446786728c854b37d`，开始时工作区干净。

## 已确认的根因证据
- 用户日志：2026-11-05 10:07:28.827，模块11.0.8（1188），微信8.0.78（3180），Android16/SDK36，主线程。
- 异常：`java.lang.String cannot be cast to xn.c`，首帧为 `xn.c.equals`。
- 精确发行版：`https://github.com/vip772/pl/releases/tag/v11.0.8`，源码目标 `5ff80cbb34121fbde2a3ef4b2bbadd3afc67c28c`。
- 映射资产：`https://github.com/vip772/pl/releases/download/v11.0.8/Hchat-release-mapping-5ff80cbb34121fbde2a3ef4b2bbadd3afc67c28c.txt`，151591652字节，R8 9.0.32，pg_map_id `a8634e3468ba1ed2fa762952727d494d8959a9688ca4d713ab675c790cb0cde5`，与日志逐字匹配。
- 还原：`dd.m.b:9` → `Intrinsics.areEqual`；`pi.z.O:31` → `BackgroundBeautyRuntime.isBottomBar` 源码3367行；`pi.z.a:1570` → `applyChromeTransparency` 2676行，内联于 `applyBitmap`；`gj.g.run:255` → `applyForActivity` 的主线程背景加载回调2002行。合成类映射头含标题栏名称不代表该行属于头像加载。
- 微信原生证据：本地8.0.78解码文件 `smali_classes12/xn/c.smali`，`equals(Object)` 入口直接 `check-cast p1, Lxn/c;`，随后读取todoid和roomname，无instance-of保护。
- 旧代码 `view.tag == "Hchat:FloatingBottomBar"` 会向未知原生对象分派equals。同文件2677行的 `!=` 也存在同类风险。具体触发页面和用户操作无法由现有日志确定。

## 修正与范围
- 两处均改为调用私有辅助函数 `isFloatingBottomBar(view)`。
- 标签只读取一次；`tag is String && ...` 短路后才比较自定义底栏已有的 `FLOATING_BOTTOM_BAR_TAG` 常量，不复制另一套标记定义。
- 不改写标签，不调用宿主对象toString/equals，不用宽泛catch掩盖错误。
- 保留原生底栏类名、主页/聊天范围、自定义底栏不透明清理规则；不增加Hook、反射、线程、集合、树扫描或日志。
- 仅1个生产文件更改，未修改已交付的标题栏去头像、艾特设置顺序、朋友圈、工具栏与自定义底栏实现。

## 实际验证
- `python3 scripts/check_background_tag_safety_static.py`：4项源码契约和独立Python模型测试通过，覆盖空标签、匹配/不匹配字符串、数字、具有抛异常equals的宿主替身、原生底栏短路和聊天/非聊天范围；独立模型重复10000次未调用替身equals。
- `python3 scripts/check_floating_header_presentation_static.py`：既有5项检查通过，含192组独立策略模型，确认沉浸式/悬浮所有权源码契约仍在。
- Python AST、`git diff --check`、精确改动范围检查。源码检查与Python模型均不是生产Kotlin执行或Android实机结果。
- 按约定未运行Gradle、Kotlin/SDK编译、APK/R8构建或内含编译器的历史合成测试；未验证修复后发行包DEX。此次修复不更换微信混淆入口，原生证据限定8.0.78，不声明其他版本实机兼容结论。

## 待新版统一实机验收
开启沉浸式背景，覆盖主页底栏、自定义悬浮底栏开关、聊天进出及背景异步加载场景，核查不再出现本次ClassCastException、底栏显示与滚动无回退。代码交付不等于实机验收完成。