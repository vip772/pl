# 进群时间：8.0.78 原生链与同进程取证

调查基线：dev / 25038ab51dfe794eca2382cd511faa90b7daafcf。范围仅进群时间；初次同进程取证未修改生产代码，后续生命周期审查修复了缓存完整性标志丢失（见下文）。没有运行 Gradle、构建或安装 APK。调查仍未完成，不能视为修复验收。

## 2026-11-07 07:26—07:29 同进程只读取证

脚本由用户在微信插件页手动开启，进程 PID 29587、UID 10314，版本 8.0.78 / 3180。同设备另有 user 150 分身微信；本次证据属于 user 0。脚本无 Hook、网络请求或微信数据库写入，只向本地插件目录写结果。

原生读取链：gp0.j1.v(q02.f.class) → service.a() → storage.N1(group) → room.field_roomdata / room.D0().d；逐成员读取 so.b.d（原始成员ID）、so.b.i（无符号32位秒）。模块读取链：getDatabaseApi().rawQuery 查询 chatroom.roomdata，同一群原始 Blob 逐字节比较。

| 样本 | 原生人数 | 正时间人数 | SQL Blob 类型 | SQL 与原生 Blob |
| --- | ---: | ---: | --- | --- |
| 26人群 | 26 | 0 | BLOB / 4 | 逐字节一致 |
| 208人群 | 208 | 0 | BLOB / 4 | 逐字节一致 |

两群原生/SQL SHA-256 分别为 `1d2a98431ad8107f433f92a487faa39b78597ed48fa999d36f79e880e1c04d02`、`6fd097e30a8f1d40f73116e09cebbe3cc78082b2def119b08d606e915e1612cd`。原始ID逐字比较各找到1个当前账号成员。这排除了本次两个样本的数据库对象或字段误读，不代表通用数据库 API 的跨账号生命周期已经核准。

首份脚本仅使用 messageTableForTalker，映射空值导致历史部分失败；result.json 虽有 finished=true，同时存在两群 error，不能视为完整取证。随后改用生产代码已有的 messageTables 发现方式，文件观察器自动重载，无需用户再次开启。第二份 result-history.json 中16个已识别消息表均成功查询，两群各46/51条 type10000/10002 系统消息，均未触及每表1000条上限。

## 邀请消息角色与来源

旧日志中 length311/331、identity=true、inviteWord=true 的消息已与真实原文对上。连同另一条301字符消息，三条均为模块群监控生成的邀请详情，含 `weixin://weixinhongbao/hchat/group_leave_profile/` 链接和“邀请者／被邀请者／累计邀请”字段，不能当成微信原生入群事件。

源码依据：

- GroupLeaveMonitorSettings.DEFAULT_INVITE_NOTICE_TEXT 定义上述模板。
- GroupLeaveMonitorRuntime.handleChatroomChange 通过名单差集识别新增成员，并可等待后续 roomdata 更新后 flushPendingInviteDetails。
- insertInviteDetailMessage 使用 System.currentTimeMillis() 作为本地系统消息 createTime；不是回包中的入群时间，也不是原始入群事件时间。
- 邀请详情中的3个被邀请人哈希分别为5495dd1e、445cb9a2、9e4c2672，与最新失败日志中所看4个成员173076c8、b723d329、1aa169ca、35af8501均不同。哈希在这里仅用于对照日志，不能充当通用身份凭证。

本次97条系统消息未发现含明确“加入／进群”事件语义的原生消息；邀请关键词命中为上述模块提示。该结论限定当前账号、两个群、当前保留的10000/10002消息，不宣称所有历史、其他消息字段、其他服务端能力都已排除。不能把模块观察时间冒充准确进群时间，不能通过放宽“邀请”关键词匹配修复这4个成员。

## 原生字段、写入与显示条件

逆向目录由用户提供：/sdcard/Download/wechat8078_decoded。

- smali_classes8/so/b.smali：so.b.i 对应 joinTime，成员 protobuf 字段6；原生 so.a.d 为成员列表。
- smali_classes8/qn/w.smali：onGYNetEnd 读取 RepairerConfigChatroomJoinInfo，保存为 scene.m；完整名单进入 z2.P0 后 replace 持久化。
- smali_classes8/com/tencent/mm/storage/z2.smali，P0 从409行起：已有成员在开关false时将so.b.h/i置0；开关true才复制pc5.a00.u/v；新增成员开关false时保留默认0；随后重组so.a.d，经X0序列化。
- smali_classes13/com/tencent/mm/chatroom/ui/ChatroomInfoUI.smali：开关不为1隐藏room_join_time/room_join_way；开启后使用b41.y1.u()当前账号，z2.q0(self)读取so.b.i，按`& 0xffffffffL`转换后乘1000显示。这是本人资料链，不能作为任意成员日期可得的证据。
- smali_classes4/com/tencent/mm/repairer/config/profile/RepairerConfigChatroomJoinInfo.smali：实验键clicfg_chatroom_join_info，Repairer键RepairerConfig_ChatroomJoinInfo。

开关false可以清零是源码事实，但没有现场“原先正时间 → 此分支 → 变零”的历史证据，不能据此宣布历史全零根因已证明。旧runtime中gate=false的已观察写入，其incoming本身就是0，cleared=0。

既有完整551取证两群分别25/207项0，各1项正时间；回包与当前显示成员不匹配，是已确认的本次数据缺口。尚不能证明其他服务端能力没有日期。

## WA APK 对照

用户附件wa.apk经JADX静态反编译至/tmp/wa-join-review；未安装或运行。

- assets/test.json约1089行的clicfg_chatroom_join_info用于群资料页本人入群信息的实验开关。
- defpackage/C0016.java按default_uin取得账号MMKV：_WxExptAppKeyMmkv、_WxExptAppIdMmkv，以及Repairer。
- AbstractC0276.m330将expt值Base64编码后写实验JSON；repairer Int写入Repairer MMKV。

所核查的配置写入路径没有实现任意成员进群日期查询。直接抄开关只会控制原生读取/写入条件，不能把完整回包中的0变为真实日期；本轮没有改微信开关或持久化配置。

## 证据、状态和剩余工作

原始数据仅保存在设备，含群成员身份及系统消息，不提交Git：`/sdcard/Download/Operit/join-readonly-evidence-20261107/`。

- result.json SHA-256：161fa21b11df70dce82aa25f59067643b210d0ed0f5420eed861c6800c08764a（历史部分失败）。
- result-history.json SHA-256：d6e68c7f9872b190c27cca8713fec4780d325e07025b3d5b6308c3265bcf0e3f（两个群系统消息读取成功）。
- runtime-060210.log SHA-256：1f7627963b155a87d36f9acd4c7fb9d07247b44136902f39e67cca9fb368f6d5。
- SHA256.json记录脚本和其余证据指纹。

调查清单6项：①同进程数据库/原生字段对照完成；②原生写入及本人显示链静态核对完成（历史清零归因未证实）；③两群现存系统消息及角色核实完成；④所提供WA配置实现对照完成；⑤已有有效条目到UI显示的实机验证完成（用户确认一个群本人资料已显示，未记录具体群）；⑥最终数据能力边界、修复或功能内重构与实机验收未完成。当前5/6为已执行调查项，不是功能完成率。不能宣称已修复用户所看成员。

用户随后按指引打开群内本人资料，明确反馈“出现了”。该反馈确认当前安装版至少在一个目标群的本人资料上，已有有效时间可以实际显示；没有新增生产修改，不能将此反馈当成本轮新修复，也不能扩大为两群全员验收。具体群、显示日期及逐条日志未取得，不宣称日期已独立校准。对于无可信日期的成员，必须继续区分数据缺失与显示失败，不用观察时间、首条发言或邀请人时间伪造日期。资源生命周期七阶段仍0/7。

## 本人条目实机确认与临时脚本清理

用户在同一微信账号按指引查看群内本人资料，反馈“出现了”。这使调查清单推进为5/6；原先查看的其他成员仍无已核实日期，最终目标未完成。现有功能有有效时间时能够显示，继续改数据库解析或挂载UI缺乏本次证据支持。下一项应审查原生是否存在其他可用数据来源及其权限、请求条件；当前551和现存系统消息的结论不能外推为所有来源不可得。

临时插件已从扫描目录移至本机证据目录plugin-retired，保留原始结果及执行脚本。用户已被要求先关闭插件，但没有独立读取配置开关回执；不声明已验证配置关闭。取证脚本本身未安装Hook、未创建常驻线程。


## 生命周期与跨功能审查续查（2026-11-07）

审查当前 dev/25038ab 与 ProfileIdFeature、ProfileGroupInfoFeature 的历史提交；保留此前取证记录。项目根目录未发现 AGENTS.md，不宣称已读；遵循 skills/hchat-project/SKILL.md，用户指定仅 dev、仅微信8.0.78及不运行 Gradle。

### 已确认并修复的缺陷

00d8f88 将 getMemberJoinTimes 的返回对象提前转换为普通 HashMap，然后调用 isJoinTimeResultComplete。后者只识别 WeChatChatroomApi 的私有 JoinTimeResult，导致 Snapshot.complete 永远 false。此前测试替身仅返回全局 complete，未模拟真实类型检查，漏掉此缺陷。既有runtime-060210.log亦有现场对应：05:59:22.319、06:01:44.900及06:02:02.352的historySummary均complete=true，紧随的cacheStored却complete=false；这是状态标志丢失的现场佐证，不是成员正时间被清零的证据。

修复为先从原始 API 结果保存 complete，再复制和合并时间 Map，两次 Snapshot 使用保存的状态。complete 表示本地查询过程完整，不等于所有成员都有日期；缺失成员仍沿用15秒按需重试。此修复避免已命中条目在15秒后因假性 incomplete 再扫描，不增加线程或 Hook，不改微信数据或实验开关。

加入返回类型语义后，新增“完整且命中的条目过15秒不再查询”回归在旧生产代码上失败：IllegalStateException: complete hit was scanned again；修复后通过。历史对照确认00d8f88之前直接读取原始结果标志，此缺陷由补查合并引入，不能归因为资源生命周期框架。该缺陷没有制造其他成员的真实日期，不能把这项修复当作其他成员不显示已经解决。

### 已检查的所有权与冲突路径

- ProfileIdFeature.headerOwner 的 enabled 覆盖ID、添加时间、进群时间和邀请人任一开启；relatedPrefs监听刷新默认不重启存量资源。实名尾字由独立 owner 管理；关闭实名尾字不会移除进群时间 Provider。
- headerOwner 只在其功能组全关闭或销毁时移除对应 Provider。JoinTimeRecovery.close 和 stopJoinTime 由进群时间自己的 owner 管理；停止进群任务不移除邀请人 Provider。FeatureResourceScope逆序逐项隔离清理，PreferenceRuntimeOwner不重复创建启用周期。
- ProfileGroupInfoFeature以generation拒绝旧任务写缓存，以原始当前账号拒绝异账号结果；32群缓存、32待加载/验证上限、单线程执行器。removeProvider/stopJoinTime会递增generation、shutdownNow并清状态；本次专项覆盖stop后重新查询和remove/install后重新查询。尚未穷尽账号快速A/B/A及所有并发交错。
- JoinTimeRecovery只匹配自身scene对象身份；版本门控、原始账号/epoch、取消与倒计时唤醒、缓存合并不以零覆盖正值及空闲Hook释放均有专项覆盖。不对正常微信scene统一阻断；HookRegistry.unhook仅移除传入句柄。
- JoinTimeNativeDiagnostics不设置结果、参数或宿主字段。13个probe签名和协议字段与用户提供smali对照通过；没有发现其主动清零时间的写入。
- WeContactHeaderApi使用CopyOnWriteArrayList.addIfAbsent注册；removeProvider只移除指定对象。异步刷新再次校验当前Activity、root、群及成员，避免刷新到已切换的资料页。其他Provider异常未逐项隔离是静态风险，但没有本次异常证据，不作为已证实冲突根因。
- GroupLeaveMonitorRuntime的本地邀请提示使用插入当前时间；没有在该文件发现roomdata或joinTime字段写入。此前已证实提示属于模块生成，不纳入准确入群时间来源。不能据此排除所有共享数据库生命周期问题。

### 验证与边界

以下专项已通过，均不代表APK或实机验收：
- scripts/tests/profile-demand/run.py：按需查询、合并去重、缺失/不完整重试、原生写入失效、完整标志保留、停止重开及Provider移除重装。
- scripts/tests/profile-demand/recovery.py：版本限制、关闭无请求、请求版本0、有效值合并、冷却、自有scene拦截、账号隔离、关闭取消唤醒和Hook释放。
- scripts/test_preference_runtime_owner.py：关闭启动、反复切换、不重复安装、失败清理重试、旧回调拒绝、监听移除异常仍清理。
- scripts/tests/profile-demand/check_native_probes.py：13个签名和字段对照，只读观察不改结果。
- scripts/check_profile_runtime_sdk.py：当前资料页Kotlin和群API Java定向SDK编译，以及生产原生解析/完整性状态回归。首次前台执行超时未计通过，后台补跑取得完整PASS及退出0；使用现有ABI和UI registry替身，不是Android/R8构建。

目前有直接证据的数据缺口仍是两个群本地原生时间全0，完整551回包中所查看其他成员为0，且当前保留系统消息没有其可信原生入群事件。未发现生命周期或跨功能把这些成员已有正值丢弃的现场证据；不能声称已全面排除冲突。

最终目标仍未完成。其余原生数据来源和权限条件调查、完整并发审查、Android/R8构建及新版实机验收仍待完成；未证明内存收益。原六项调查仍5/6，资源生命周期七阶段仍0/7。本次缓存修复按功能范围归档，不计作资源生命周期阶段验收。

补充边界：本轮尝试对用户提供的整个smali目录搜索so.b.i/pc5.a00.v引用，共享存储遍历超过前台时限，后台扫描也未取得完成标志；仅有部分输出，不能据此声明全引用已覆盖或其他原生来源已排除。已直接核查z2.P0、qn.w及ChatroomInfoUI的已知字段路径。

固定基准caa675c对比：当时ProfileGroupInfoFeature已持有可关闭执行器和generation，但用loadedGroups标记永久加载、cache无32群边界，且刷新遍历命中成员；当前增加按需过期、容量边界、active及账号隔离。该差异核对不能证明原基准或当前所有竞争已安全。完整性标志丢失定位到后续00d8f88，而非仅凭改造时间先后归因。

续查：使用生产KavaReflector再次运行recovery.py --production-reflector通过。新增可控旧查询忽略中断、stop后新查询完成再释放旧查询的回归通过，旧代未覆盖新缓存；新增切换原始账号后不返回旧账号已命中条目通过。以上仅验证上层缓存与模拟宿主查询，不代表底层数据库跨账号安全。
公共数据库审查发现：WeChatDatabaseApi.ensureReady在dbWrapper/queryMethod非空时直接返回，getCoreStorage同样复用coreStorage；这些对象未逐次校验账号。WeChatApis.clear仅由WechatApiFeature.onDestroy调用，HookRegistry.unhookAll仅在FeatureManager.destroyAll调用，未发现其他功能单项关闭调用它们。该数据库对象跨账号复用属于未实机证实的风险，两群现有Blob一致的取证不支持将其认定本次原因；修改公共数据库将影响其他功能，须另有原生账号生命周期证据和回归，未在本轮盲改。群变更API只订阅/消费数据库事件，不写roomdata或joinTime。


## 全目录字段引用与 ModContact 持久化闭环（续查）
本次已取得全目录扫描真实完成标志：`join-native-direct.exit` 为0，`join-native-direct-errors.txt`为空。对所提供解包目录全部 `smali*` 搜索 `Lso/b;->i:I|Lpc5/a00;->v:I`，共6个命中文件。此前未完成扫描的记录保留为历史，本段更新当前状态。精确描述符搜索不覆盖反射访问、其他协议字段或服务端能力。

| 解包相对路径 | SHA-256 |
| --- | --- |
| `smali_classes13/com/tencent/mm/chatroom/ui/ChatroomInfoUI.smali` | `d5d9185de950abd8b982696830eb8141e76c579bdb68233bac58614e0a30e587` |
| `smali_classes8/b41/u1.smali` | `5e3316bb7ad048d2efe83bb9d10f6f7c7e4f8892d13f13e5ea2fa63d1aff8668` |
| `smali_classes8/com/tencent/mm/storage/z2.smali` | `e76b0d7f1d0797e91d6ff6c8ef88586fed23cd0b17dcc2e6edaa35e97e5ebf4d` |
| `smali_classes8/qn/w.smali` | `6be14c56079ee65dfb4a1609c7e29539902ca8bb30c3513774ff32c54645be7b` |
| `smali_classes8/pc5/a00.smali` | `9ebbd6de5e58feaff7dafedde407ff2e63d70d72224958eeacb97d28e859dd55` |
| `smali_classes8/so/b.smali` | `3e90c72acd298b882eecd2a589031e6d987dd15b30e970dc4c0b8816f6415cbc` |

`b41.u1.N(String,String,pc5.zz,int,int,int,String,so.a,boolean,boolean,int)` 是本轮补齐的原生成员同步入口：
- 约1296—1324行读取 RepairerConfigChatroomJoinInfo，只有返回值严格等于1才启用复制。
- 约1380—1406行为有效成员新建 so.b，gate=true时从 pc5.a00.u/v 复制到 so.b.h/i。
- 约1550—1589行从旧 z2.q0(member) 取旧成员；gate=true且新值为0时保留旧 h/i。gate=false跳过复制和旧值保留，新建对象的时间仍为默认0。此结论要求走到该成员构建路径，不能推广为每次N调用都清零。
- 新成员加入 so.a.d；约1826、1957行两条分支调用 z2.X0。X0约1135—1143行直接序列化收到的 so.a 到 field_roomdata 并更新 l2，没有成员时间合并恢复逻辑。增量分支在新成员列表为空时沿用旧列表，此空列表情形不能当作清零。
- 约1990行 u1.M(z2) 经版本/人数等原生检查后调用 a3.replace；a3.replace 约400行转发到 Q1(z2,true)。Q1约243—338行仅查已有行并调用 insertNotify 或 update，不修复成员时间。因此gate=false生成的零时间具有持久化路径，实际是否成功仍取决于原生检查和数据库返回。
- a3.smali SHA-256：b163980b76cdc4befab3a7100376300f84da0954100f920ad62ddb7270b541bb。

原生 RepairerConfigChatroomJoinInfo.c() 默认值受 z.h 取值0x2f00000f/0x2f00000e或bf5.c.a()控制；配置key为 RepairerConfig_ChatroomJoinInfo，实验key为 clicfg_chatroom_join_info。默认值不等于运行时最终值。对应smali SHA-256：85718ad00c9e3d6717fba934e9610e105bbd2ff650ab38bfdc4e5ebc2d1e5f24。本轮未改该全局开关。

模块交互核对：JoinTimeNativeDiagnostics已包含N、M、X0、Q1观察，观察代码不设置宿主参数/结果/成员字段；Q1成功后的回调只使模块缓存失效并排队读取。JoinTimeRecovery仅对自有scene设置Hook结果以阻止其原生写库。全项目直接类名、roomdata及实验key引用搜索未发现其他业务功能写这两个成员时间字段的路径；这不是对动态反射或外部模块的全面排除。旧现场日志仅有probe stage=N的安装记录，没有N执行前后事件或目标正时间变零记录。新增分支是已确认可发生的机制，尚未证明目标全零的历史成因。

### 本轮验证与阻塞边界
2026-11-07续查重新运行 scripts/tests/profile-demand/run.py，完整PASS且进程退出0，本机日志 /tmp/join-demand-final.log。覆盖完整标志保留、缺失/不完整重试、原生写入失效、stop/reopen、忽略中断旧查询不覆盖新代结果、切号不返回旧命中缓存。此前SDK检查结束文件再次核验为0，日志有完整解析、完整性回归及Kotlin/Java编译PASS；没有新生产修改需要重复SDK检查。
已完成精确字段全目录引用核查和新增原生写入链静态闭环。既有数据不足以还原其他目标成员真实日期；没有已验证的新协议可替代日期为0的完整551回包。公共数据库缓存对象切号复用、Provider异常隔离仍是待验证风险。没有运行Gradle、生成/安装新版APK或完成新版实机验收，未测量内存收益。调查清单仍5/6，资源生命周期仍0/7，功能总体未完成。

本次收口重新运行 recovery.py --production-reflector、test_preference_runtime_owner.py、check_native_probes.py，均取得完整PASS及退出码0；日志分别为 /tmp/join-reflector-final.log、/tmp/join-owner-final.log、/tmp/join-probes-final.log。受控并发回归仍含短sleep，未穷尽交错，不将专项等同完整并发证明。
