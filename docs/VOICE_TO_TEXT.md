# 语音转文字

默认关闭，独立开关位于语音分组及新功能聚合，复用同一Provider、配置与设置页，纳入已开启统计。配置Hchat_voice_to_text_config/enable使用HchatStorage，自动进入统一配置备份，无模块自管音频/识别文件。运行去重、候选队列和反射成员仅驻内存，不进入备份。

## 8.0.78逆向证据

用户目录/storage/emulated/0/Download/wechat8078_decoded/：

- smali_classes12/com/tencent/mm/ui/chatting/viewitems/bq.smali：O()返回false；n(h0,gk5.d,am5.d,String)是收到语音行绑定，am5.d.d→hn5.a.b取得当前原生e9对象。iq的O()为true，发送语音不安装此Hook。
- smali_classes11/com/tencent/mm/ui/chatting/viewitems/h0.smali：convertView是当前绑定行；am5/d及smali_classes8/hn5/a确认包装字段；im/c8的getMsgId及z0（field_isSend）确认身份和方向。
- smali_classes12/com/tencent/mm/ui/chatting/viewitems/nq.smali：o(e9)判断下载完成，c(int,gk5.d,e9,ak5.l2,boolean,int)走原生转换；普通请求调用vo.b(e9,true,position,0,scene)。ExtVoiceTransformTextClickEvent用于报告，并非识别请求，不直接发布该事件。
- smali_classes12/com/tencent/mm/ui/chatting/component/vo.smali：b(e9,ZIII)复用原生缓存、转换队列和异步识别，y0(msgId)判断排队；缓存结果通过VoiceTransTextCompletedEvent和原生刷新显示。Z1标志已展示转换时再次调用会取消展示，故自动入口必须跳过。gk5.d.c→manager.c.a(ak5.l2)取得当前聊天组件。

实现使用上述精确描述符并限制versionName=8.0.78，不运行DexKit扫描。反射成员在安装时统一验证并缓存，未确认版本不猜测适配。只核查了用户提供版本，没有其它版本兼容结论。

## 触发与成本

开关默认关闭时仅配置监听，不安装语音行Hook。开启后原生接收语音行绑定结束时记录候选，延迟350ms校验当前行附着、显示、焦点、消息ID及网络；已转换、已在原生队列、未下载、出站均跳过。采用原生组件b(message,true,0,0,0)，调用发生在主线程以遵守原生聊天组件线程语义，识别和下载交给微信原生异步系统，不在绑定内直接识别。手工转换保留原行为。

候选最多16条，每500ms最多提交一条；最近512个提交身份去重，绑定复用只检查当前行，不扫描消息列表。没有全局滚动监听、持续轮询或自建识别网络接口；无候选时无任务。弱引用行，关闭会清理未执行任务，已经提交给微信的任务不强制取消。原生缓存和历史转换结果仍由微信维护。

边界：首次开启后等语音行正常绑定触发，不主动刷新整页；未下载、离屏、失焦、断网候选跳过并允许后续绑定再检查，没有主动补下载或联网恢复轮询。已提交失败本进程最近512项内不自动重试，仍可手动转文字。进入聊天时绑定的历史收到语音也可触发，不扫描全量历史。初版只覆盖上述收到语音行入口，不保证其它灰度/引用预览路径。原生转换可能需要联网，并沿用微信服务的语言与识别能力。

## 验证

scripts/test_voice_to_text.py编译全部生产功能与队列类（Android SDK真实接口，微信/Xposed/框架桩），覆盖有界队列、同消息去重、释放与清理。scripts/tests/config_backup/archive.py通过真实HchatStorage及HchatConfigBackup验证开关true/false往返和运行缓存排除。git diff --check通过。没有运行Gradle、没有构建APK、没有实际发语音或提交识别请求；原生文字呈现、识别成功率、实际布局及流畅度仍需新包实机验证，不能用桩测试代替实机验收。

本轮按要求不提交、不推送，保留此前本地修改。