# 微信 8.0.78/3180 媒体安全消息复查

状态：候选修复，尚未实机验收。上一版接收端验收失败；不能视为已解决。

## 原生证据

- 解包目录 /storage/emulated/0/Download/wechat8078_decoded，apktool.yml 标明 8.0.78/3180。
- b41.ma.C(message) 读取 .msgsource.sec_msg_node.sfn，值为 1 才识别为安全。
- vu1.d1.a(message) 普通聊天分支调用 b41.ma.f(null)，没有继承传入消息的完整策略。
- oa0.d.invokeSuspend 普通图片分支新建 n95.e；结果写入 ba0.a.d (w90.i0) 的 f 字段。上传请求读取该字段。
- c72.y 的请求路径为 d.a.a，h27.e 为 kk0 列表，kk0.p 为消息源。
- qi0.o3.a(v2)、s61.q1.I(v2) 重建视频源；部分节点只有 uuid。

## 本轮修改

- 增加 vu1.d1/c72.y 的 8.0.78 表情适配；共享生成入口仅处理自发消息。
- 在图片准备返回时补写上传参数消息源。
- 在两个已核对的视频生成方法返回时补写策略。
- 所有新增 Hook 交由现有运行时登记、去重和关闭释放。

## 验证与边界

SDK 定向编译通过（真实 Android SDK，依赖 ABI 桩）；静态检查和 git diff --check 通过。未运行 Gradle、未生成 APK、未进行实机收发。

未闭环：上一版编码 Hook 未在设备达到目标的具体运行原因仍缺证据；新增入口尚无运行时触发及接收端最终 MsgSource 证据。目标仍为接收方长按图片、视频、自定义表情只显示删除和连续朗读。

## 证据指纹

- smali_classes11/vu1/d1.smali SHA256 9226e8cdcc5118a6825a18741f70d34ff546f766ca9eb07185fa48bf33feb9d2
- smali_classes12/oa0/d.smali SHA256 19f6ecc6b12f0ca4bcb43f0a6032e83b097caef3fab547f697ce70ba4df29e69
- smali_classes12/c72/y.smali SHA256 7027717ccf2f1905e3199c6d6d2b12bf023c1702575795c810bfc13ae77d0f9c
- smali_classes12/qi0/o3.smali SHA256 4f77e5782ce1b219219be59fc9ef07c3e5a07d450aeddc36a66c20b1fb4ed3d6
- smali_classes12/s61/q1.smali SHA256 f61f57c113d1528be9f9c3bffbd8a28d7857102ff1dde0fc9785d97b8f69a534
- smali_classes12/b41/ma.smali SHA256 964590418e8cb2cad7ddd1eb276e26993f4df43806523547895b402a102c4c30
