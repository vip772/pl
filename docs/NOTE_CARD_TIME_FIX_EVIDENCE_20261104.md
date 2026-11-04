# 收藏笔记卡片消息时间错位修复依据

日期：2026-11-04。工作分支：`dev`。修复基线：`c888765`。

## 问题与历史

用户反馈：接收的收藏卡片消息，附加时间标签挤入群昵称一行。

`MessageDetailsCardHierarchy.isCardMessage` 原先把 AppMsg 子类型 `24` 排除在卡片布局之外，使收藏笔记走普通原生时间/内容锚点路径。历史提交 `ff3fa6d` 已针对子类型 `19` 的合并聊天记录使用完整卡片底部锚点，但没有覆盖仍在排除集合中的 `24`。因此不能将这次问题归结为历史修复被撤销。

## 微信 8.0.78 原生证据

本轮核验微信 `8.0.78 (3180)` 的本地 APK 解码产物：

- `kn5.g4.p()` 根据 `nh5.f0.i(msg)` 分派发送 `kn5.m4` 与接收 `kn5.i4`，两者共用 `kn5.h4` holder。
- 接收布局 `0x7f0e03af` 对应 `layout/e1w`，发送布局 `0x7f0e0402` 对应 `layout/e2b`。
- `kn5.h4.b` 的声明类型为 `com.tencent.mm.ui.chatting.viewitems.mvvmview.ChattingNoteMvvmView`，由资源 `0x7f0a107d`（`stp`）初始化。
- 接收布局的竖向 `bjy` 分支内，昵称 `brc` 位于 `FrameLayout(a1l) → ChattingNoteMvvmView(stp)` 上方。时间标签必须追加在完整内容分支之后，不能使用昵称/时间条的位置猜测卡片底部。
- 发送布局为横向结构，同样通过上述语义内容视图上溯到当前消息行的完整分支。

解码产物 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| `smali_classes12/kn5/g4.smali` | `51293729e39b9ce862dc5da171701c9e6f4afa6d28a19f7d668aa2b525fd27d6` |
| `smali_classes12/kn5/h4.smali` | `ff2c8c4cc38afbb787b04a991af1059b5ecc46da666c731808ba9aa69001a53d` |
| `smali_classes12/kn5/i4.smali` | `6a4e2206e784d24986c3753c8f9c7da6b3cffe7a889068e18ecf1a44611a8d10` |
| `smali_classes12/kn5/m4.smali` | `7ce65363c86704dc61a3dfe7dd48b807e392cc048710d4b83723c7a2ce35b983` |
| `res/layout/e1w.xml` | `1243bdc86afbaccaaa690b68b5229412a1ccbc0aada4684625572a11c16797ed` |
| `res/layout/e2b.xml` | `8fae447e8e1ee2a22251d19e005dce94763a75e3eec9cce2f820e62a787d9ff9` |

## 修改与开销

1. 子类型 `24` 纳入卡片分类，普通文本、媒体及引用消息的排除类型保持不变；分类使用 `when`，不再为该判断构造 `setOf`。
2. 新增 `noteCardContainer`，从缓存字段中按声明类型识别收藏笔记内容，实际字段读取统一通过 `KavaReflector`。类型不存在时复用已有 `getMainContainerView` 解析；已识别字段为空、隐藏或不属于当前行时返回失败，等待既有布局重试。
3. 收藏笔记和合并聊天记录都要求完整内容锚点，不能降级为猜测的昵称/头部。布局未就绪时沿用既有队列、观察器和取消机制。
4. 将已有字段缓存读取提取为 `messageContentFields`，由旧内容解析与收藏笔记共享 `holderContentFieldCache`。新增路径未增加缓存、线程、轮询、周期日志或新的布局监听器。缓存命中后仍有缓存字段列表筛选及当前字段值读取，并非运行时完全没有反射操作；测试证明重复绑定不再枚举字段元数据。既有缓存容量策略未在本任务改造。

## 可复现验证

```sh
node scripts/run_message_type_labels_tests.cjs /tmp/pl-kotlin-libs
node scripts/run_message_details_layout_tests.cjs /tmp/pl-kotlin-libs
node scripts/run_message_details_predraw_tests.cjs /tmp/pl-kotlin-libs
python3 scripts/test_note_card_time.py
```

收藏卡片专项支持 `KOTLIN_LIB_DIR` 与 `ANDROID_JAR` 环境变量；消息类型脚本同时支持原 Gradle 依赖缓存目录和扁平 JAR 目录，并修正生产包路径为 `t/mma`。

结果：四个入口退出码均为 `0`。

- 消息类型：62 项断言通过，包含子类型 `24`、收藏笔记中文标签和内嵌聊天记录不覆盖外层类型。
- 布局：7 项观察器回归及卡片层级/分类回归通过。
- 预绘制：缓存行重新附着、取消/替换快照、观察器迁移、关闭清理等回归通过。
- 收藏笔记：接收/发送结构各 5000 次热绑定，字段元数据枚举计数不增长；覆盖昵称上方结构、零尺寸后恢复、隐藏、脱离、空字段、异行、注入标签排除与 holder 内容替换。
- 提取实际生产收藏解析、缓存字段和底部锚点方法，使用 SDK37 编译通过。辅助边界使用合成实现；这不是完整应用、Android/R8 构建或设备测试。
- `git diff --check` 通过。

## 交付与验证边界

本轮未运行 Gradle，未构建/安装 APK，未完成实机截图、长时间滚动帧率或内存测量。需在修复包中验收群聊接收收藏笔记的昵称/时间布局、发送方向、快速滚动复用，以及时间功能开关和位置切换。

本轮新增原生结构证据仅覆盖 8.0.78。现场另有 8.0.76 APK，但未完成其收藏笔记链路横向复核；8.0.49/8.0.58/8.0.66/8.0.68/8.0.72/8.0.74/8.0.77 在本轮已核对的 APK 目录未找到，不作这些版本的兼容性结论。

本仓库远程当前仅有 `dev` 与 `main`，没有 `alt-entry`。本次按连续任务既有 `dev` 分支交付，未同步或创建其它分支；频道入口规则保持现状。本文仅描述本项定向修复，不代表资源生命周期七阶段项目完成。
