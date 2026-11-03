# 悬浮菜单、朋友圈卡片、多选撤回修复记录

## 范围和状态

- 工作分支：`dev`；修改基线：`03226a2`。
- 本轮四项代码与专项检查已完成；实机验收 **0/4**。
- 未运行 Gradle、完整本地构建、APK 打包或 R8；使用已存在的依赖 ABI、Android SDK37 定向编译和合成回归。
- 不将 JVM 合成宿主、源码断言或 SDK 定向编译通过解释为微信运行成功、网络撤回成功或流畅度收益。

## 1. 悬浮快捷菜单对齐

- 行布局改为固定 LTR、`CENTER_VERTICAL`，标签与图标共中线。
- 纵向测量前设置最终列表/子项 gravity；定位纳入左右 4dp 内边距，按最终像素按钮尺寸计算中心，避免不同标题宽度造成图标错位。
- 保留原有相邻图标 12dp 间距、展开方向和拖动逻辑，不增加滚动监听、动画线程或图片解码。

检查通过：

```sh
python3 scripts/test_floating_shortcut_alignment.py
python3 scripts/test_floating_shortcut_spacing.py
python3 scripts/test_floating_shortcut_owner.py
```

覆盖：多密度/多尺寸左右图标列几何、测量顺序、行内中线；200 次 owner 安装/退休/重装及 64 项任务上限、迟到任务清理。

## 2. 朋友圈卡片越滚越小

旧原始状态仅由可替换背景关联保活。宿主重新设置背景后，弱引用基线可能消失，下一次绑定把已经加过 inset 的 padding 当成新基线，形成重复叠加。

现在以行 View 的资源 ID tag 持有 `Original`，带 owner 代次校验。重复绑定绝对使用原始 padding 加一次 inset；背景/前景可重装且幂等写入；恢复 padding 不再以背景仍归本功能为前提。弱索引和 192 行上限保留，不在 draw 中创建渐变。

检查通过：

```sh
python3 scripts/test_moments_card.py
python3 scripts/test_weather_moments_surface.py --native-dir /tmp/pl-card-native-fresh
```

覆盖：1000 次重复绑定、200 轮替换背景/周期 GC 后重新绑定，关闭恢复 padding/tag、代次隔离、主线程清理、192 行上限及 draw 分配约束。

微信 8.0.78 原生样本包含多处 `setBackground` 和 `setPadding`。本地抽取证据摘要：

- `fk4/m2.smali` SHA256：`7869e923ee8c0ea5920c2051b6bf5d29e4fbedc193f505d4f53297a58373d561`
- `com/tencent/mm/plugin/sns/ui/improve/component/y0.smali` SHA256：`a48520f75b451c0fcc3621da2a0da8ffc207625dede16289a8334b7589f4fcbf`

## 3. 多选撤回

现场落盘日志已确认：

- 2026-11-03 22:07:44：多选撤回菜单项已加入。
- 22:07:54 和 22:10:40：点击后每条消息报“撤回消息失败: API未就绪”。
- 日志同时存在网络发包器缓存成功记录；原日志没有区分具体缺失成员，不能据此单独声称已经证明哪一成员为空。

源码确认的确定性缺陷：`findRevokeMsgCtor` 强制依赖 `localMessageClass`，但本地消息解析可在系统消息方法和创建时间方法就绪时提前返回、未填写该字段。因此撤回不应依赖本地插入 API 的就绪状态。

修复：从既有撤回字符串锚点定位的场景候选中，验证唯一 `(storage消息,String,String)` 构造器及继承的 `field_msgId:long`、`field_isSend:int`，不污染 `localMessageClass`，保留原缓存、串行门和构造参数。API 未就绪日志区分 finder/ctor/dispatcher；未在点击热路径新增 DexKit 查询。

8.0.78（versionCode 3180）原生确认：菜单创建 `m5.onCreateMMMenu(kj5.i4)`，点击 `n5.onMMMenuItemSelected(MenuItem,int)`，场景 `modelsimple.d1(storage.e9,String,String)`，消息字段来自父类 `im.c8`。本轮其它版本 APK 未重新横向验证，不能宣布全部版本兼容。

检查通过：

```sh
python3 scripts/test_revoke_constructor.py
python3 scripts/test_multi_recall_owner.py
```

前者直接编译生产 matcher 方法体，覆盖缺少本地插入依赖、继承字段、错误字段类型和错误消息类型；后者已补 `java -ea`，确保 Kotlin/JVM `assert` 实际启用，覆盖菜单/点击/归属检查、原生撤回提交与退出、关闭竞争、重试及 100 次启停代次。场景/网络发送为合成宿主，不是微信服务端撤回验收。

## 4. 文字底色及备份

- `快捷项样式` 下新增 `文字底色`；仅文字或图标和文字模式下显示，纯图标模式隐藏无效设置。
- 复用既有 `ColorPickerRow(threeColorGradient = true)`，最长 29 字符，支持单色、三色及 ARGB。留空/重置维持原深浅色默认背景。
- 独立键 `label_background_color` 只作用于文字标签背景，不影响 `label_color`、`action_color` 或图标文件。菜单创建阶段最多解析三个色点，无 draw/滚动轮询开销。
- 往返测试发现 `floating_shortcut_menu` 不符合原有 Hchat 前缀过滤，已在统一存储中对该既有库名做精确兼容，不重命名、不覆盖用户设置，不放宽其它非 Hchat 库。
- 图标仍走已有 `FloatingShortcut/icons` 受管资源规则，没有新增持久化缓存。

检查通过：

```sh
python3 scripts/test_floating_shortcut_label_background.py
python3 scripts/tests/config_backup/archive.py
```

覆盖生产颜色解析及原样 UI 块 SDK37/Compose 编译（共享 UI helper 为显式边界 stub）；空/单色/三色/透明度备份导出导入，文字/图标颜色独立，图标字节、绝对路径重定位，运行缓存排除和非法库名拒绝。归档全套现有安全/回滚测试通过。

## SDK 定向编译范围

本轮完整编译目标代码并通过：

1. `FloatingShortcutRuntime.kt`、`FloatingShortcutSettings.kt`、`FloatingShortcutIconLoader.kt`：SDK37，复用缓存依赖 ABI；Agent UI 入口为显式 boundary stub。
2. `MomentsCardFeature.kt`、设置、渐变及所有权依赖：SDK37；总开关和新增资源 ID 为显式 boundary stub，非 aapt/R8 检查。
3. `DexFinder.java`、`WeChatMessageApi.java`，并补编现有 `DexKitBridgeManager.java`、`EventBus.java`、`WeChatInternalServices.kt`：SDK37 和缓存依赖 ABI。
4. `文字底色` 生产 UI 块：真实 Compose 插件/SDK37，非整个设置页或完整应用构建。

现场辅助命令分别为 `/tmp/check_task_shortcut_sdk.py`、`/tmp/task_card_sdk_check.py`、`/tmp/task_revoke_sdk2.py`。均明确输出 `NOT full build`，不作为可发布 APK 或实机结论。

## 待统一实机验收

1. 向上/下展开，长短标签、左右屏幕边缘、不同字体尺寸，图标同竖线且文字居中。
2. 卡片反复滚动、背景重绑、开关切换后内容宽度稳定，无持续缩小；观察滚动流畅度和长期内存。
3. 选中本人近期消息，批量撤回实际由服务端成功处理，退出多选正确；混入他人消息不得提交。
4. 文字底色单/三色/透明/重置、深浅色与重启保存，备份恢复后外观及图标正确。

四项统一实机验收通过前，只能称“代码及专项检查完成”，不能称“整体实机修复验收完成”。
