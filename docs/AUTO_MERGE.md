# 自动勾选合并

## 需求和入口

独立功能 `auto_merge`，显示名“自动勾选合并”，默认关闭。聊天分组、新功能、搜索和已开启聚合复用同一个 Provider 与配置开关；设置沿用现有 Miuix 页面和导航。

最终计数要求：在聊天相册中，已选照片和视频合计达到3项时自动勾选“发送后合并展示”。支持纯照片、纯视频、照片视频混选。少于3项时模块不操作，是否取消由微信原生逻辑决定。模块不点击发送按钮。

手动取消勾选后不立即抢回；下次原生选择刷新完成时，若仍满足条件会再次自动勾选。微信原生不允许合并、隐藏合并项、特殊媒体过滤或业务限制仍然生效，不绕过宿主限制。

## 逆向依据和兼容边界

当前仅启用已核实的微信8.0.78绑定。运行时严格检查 versionName，并在安装前完整解析两个页面的字段和方法类型。不将这些混淆名称套用到其他版本；未适配版本会停止安装并记录 HLog 错误。8.0.76/8.0.77本地存在部分材料，本次未完成横向适配；项目约定其他默认版本未取得本功能完整证据。相同版本的其他ABI、热更新变体也没有实机验证。

证据来自 `/sdcard/Download/wechat8078_decoded`：

- `smali_classes10/com/tencent/mm/plugin/gallery/ui/AlbumPreviewUI.smali`：`y8(int)`在选择刷新后异步计算可合并数量；`k7(AlbumPreviewUI)`将数量交给主线程 `l93.c0$$f.run()`。该任务少于3项时取消勾选并隐藏选项，满足时执行原生展示。模块在该任务完成后判断当前已选媒体，不重做文件大小、GIF等原生过滤。
- 相册页字段：`m`适配器的`g`是已选 MediaItem；`j2`合并容器、`k2`勾选框、`i2`实际发送状态。
- `smali_classes4/l93/q0.smali`：容器点击翻转`i2`并同步`k2`。发送结果通过`key_send_as_media_group`传递。
- `smali_classes10/com/tencent/mm/plugin/gallery/ui/ImagePreviewUI.smali`：`d8()`异步计算条件，`l93.q3$$h`调用`l7()`，随后主线程`l93.q3$$k.run()`刷新显示与取消状态。模块在最后一个任务完成后执行。
- 预览页字段：`n`是媒体对象列表，`m`是已选路径；计数时按`MediaItem.e`与已选路径交集过滤，避免把未选预览项计入。合并字段为`U1/V1/T1`，点击监听`l93.z3`同步实际发送状态和勾选框。
- `GalleryItem$MediaItem.getType()`：1是照片，2是视频；其他类型不计入触发数量。

运行时同时要求`query_source_type == 3`、`GalleryUI_ToUser`非空、主线程、Activity未结束/销毁、合并容器原生可见且可用、勾选框可用且当前未勾选。只调用合并容器`performClick()`，并检查布尔状态和勾选框同步；不单独改写勾选框或绕过宿主展示条件。

## 生命周期和开销

`PreferenceRuntimeOwner`监听唯一开关键，启用时安装两个精确任务Hook，关闭时通过`FeatureResourceScope`和`HookRegistry.unhook`释放。功能销毁时移除配置监听并清理Hook。已排队宿主任务经过关闭检查后不会继续自动勾选。

不新增线程、轮询、全局View Hook或磁盘运行缓存，不持有Activity集合。反射成员在每次启用时解析一次，运行时只遍历当前选中媒体/预览列表。错误每个启用周期仅报告一次，走HLog.e。

## 配置备份

普通用户库：`Hchat_auto_merge_config`；键：`auto_merge_enable`；默认false。通过`HchatStorage.preferences`自动纳入统一v2备份，不新增专属导入导出路径。

运行状态仅驻留内存，不写入用户配置。本功能无外部受管理资源。备份测试创建名为`Hchat_auto_merge_method_cache`的模拟运行库，验证运行库被排除，生产实现不创建此缓存。

## 已完成验证与剩余边界

- `python3 scripts/test_auto_merge.py`：执行实际新功能、运行时、配置类、Provider和生命周期Owner源码；Android、Xposed、宿主对象、反射适配器和存储使用明确JVM桩。覆盖1/2/3/4项、两种照片视频混选、纯视频、未知类型、预览已选路径、原生状态同步、重复回调、手动取消、业务/可见/线程/销毁边界、默认关闭、20次启停和销毁释放，以及未适配版本停止安装。设置注册/路由/搜索为源码契约检查。
- `python3 scripts/test_auto_merge_sdk.py`：新功能完整源码、实际BaseFeature/FeatureContext/Owner/Scope/HookRegistry/Provider对SDK36和已有依赖ABI直接编译通过。已开启聚合使用明确的编译桩；不包含完整Compose设置页编译、Android打包或R8。
- `python3 scripts/tests/config_backup/archive.py`：实际HchatStorage和HchatConfigBackup配合文件存储/Android桩执行ZIP导出导入，使用实际AutoMergeSettings常量；默认关闭、true/false往返、运行库排除通过，原有归档回归通过。
- `git diff --check`与既有头像修改指纹校验通过。

代码与上述本地验证完成。未运行Gradle，未构建或安装APK，实机验收0次；真实选图/预览切换、发送后的合并结果和真实微信Hook时序仍需新版APK验收，不能把JVM或SDK检查视为实机通过。未提交、未推送。
