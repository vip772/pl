# 资源生命周期七项总清单：第1项续作检查点

- 固定基准：`caa675c`。当前工作分支：`dev`。代码收口提交 `b20c093`（第22～28号，79 文件），其后文档勘误提交 `ea46e35`；恢复时一律用 `git -C /root/pl log --oneline -3` 与 `git status --porcelain` 实测当场 HEAD 与工作区，不要照抄本行哈希。
- 当前阶段：**第1项进行中，总清单0/7（0%阶段完成）**。109项历史首轮复核不等于109项最终闭环；第1项内 109 个入口的最终核准仍为 **0/109**。
- 第22～28号累计改动（79 文件）已在 `b20c093` 一次性中文提交并推送；随后的文档勘误（本文件）以 `ea46e35` 推送，两条提交后 `origin/dev` 与本地 `dev` 一致、工作区 0 项未提交。
- 首个检查点当时的「不提交、不推送」约定已作废；后续每个大项完成后统一中文提交并推送，不得把单个入口的专项通过当作大项完成。
- 阅读规则：`## 接续：第N号…` 各小节是**当时的归档记录**，里面出现的「未提交未推送」「下一入口第M号」只描述该次收口瞬间，不是当前状态；当前状态以上面三行 + 文末「编号勘误」为准。

## 固定顺序
1. 全部功能的独占资源生命周期闭环。
2. 全部公共依赖与进程级资源生命周期闭环。
3. 全部异步任务、在途操作与页面恢复闭环。
4. 全部资源容量与长期占用治理。
5. 最终代码、全量回归与Android/R8构建验收。
6. 全部功能统一实机验收。
7. 内存、资源回收与流畅度验收及最终收尾。
前项全部完成才能进入后一项。第6、7项需用户提供约定设备同条件结果；未取得结果不得称实机验收或内存收益成立。

## 本轮已实施（只属于第1项局部范围）
- 恢复未提交的FeatureOwnership.kt和测试：修复重复Runnable登记覆盖、投递/关闭竞态、异常上报打断清理；关闭拒绝Hook安装、迟到句柄释放并返回失败；代际状态一致读取。基础类尚未接入功能，不能据此宣称109项受益。
- 聊天工具栏、首页侧边栏、QQ点歌、自动回复、自动缓存、文字播报：try/finally确保注销监听抛错后仍停止运行时。
- 表情艾特：逐Hook、存储句柄、安装票据、监听、任务与工作线程释放异常隔离；重复安装/销毁幂等；销毁后偏好监听及页面监听入口失效；安装失败复用逐Hook清理。
- 新增异常注入回归；补齐旧feature_resource_scope测试缺少的进程API ABI夹具（不验证进程资源行为）；加强完整工具栏/侧边栏/点歌功能类的注销失败、重装及旧监听回归。

## 验证证据
- `docs/RESOURCE_LIFECYCLE_STAGE1_EVIDENCE.json`：16/16定向测试通过、源码哈希前后一致；包含日志原文、哈希和源码指纹。
- 本机原始证据：`/tmp/pl-stage1-resume-20261102/focused-final-1/`。
- 原始失败日志保留：`ownership-red.log`（10条失败检查）、`cleanup-red.log`（六入口单/双异常共12处）、`emoji-red.log`。
- 原feature_resource_scope首次失败为夹具缺少进程API符号；已补齐并运行加强版通过，不改写原始失败记录。
- SDK类型检查/合成测试不是完整Android/R8构建；没有Gradle、宿主业务调用、设备验收或内存测量。

## 继续位置与未闭环边界
- **仍在第1项**：按下表109项逐项核准入口及其实际运行时，不能把七项局部修复当作七个完整功能闭环。先审查本轮改动和证据，再继续剩余运行时的安装/关闭/失败重试/销毁重装/清理异常隔离。
- 表情艾特：专项只执行提取的生产安装/关闭方法与真实队列，未完整链接Android运行时；清理投递被拒绝时UI恢复仍开放，不得标记功能全部完成。共享存储Hook归第2项；在途发送/UI交错归第3项；队列容量归第4项。
- 共享API、主/子进程归属缺口登记但不提前进入第2项实施。
- 本轮未取得第1项全部109入口闭环证据，阶段完成数仍为0/7。未执行提交与推送。

## 109入口逐项核准台账
下表是最终核准状态，不覆盖历史审计成果；“待核准”不是否认历史修复，而是尚不具备本七项清单的最终闭环证据。

| 编号 | 功能入口 | 第1项状态 |
|---|---|---|
| 1 | SettingsFeature | 待第1项完整核准；保留历史成果 |
| 2 | WechatApiFeature | 待第1项完整核准；保留历史成果 |
| 3 | BackgroundBeautyFeature | 待第1项完整核准；保留历史成果 |
| 4 | MeEntryVisibilityFeature | 待第1项完整核准；保留历史成果 |
| 5 | ChatToolbarFeature | 本轮局部修复及专项通过；整体待核准 |
| 6 | HomeSidePanelFeature | 本轮局部修复及专项通过；整体待核准 |
| 7 | HomeExtensionFeature | 待第1项完整核准；保留历史成果 |
| 8 | StepMultiplierFeature | 待第1项完整核准；保留历史成果 |
| 9 | AutoCacheFeature | 本轮局部修复及专项通过；整体待核准 |
| 10 | MonetModuleGeneratorFeature | 待第1项完整核准；保留历史成果 |
| 11 | AntiSecureMessageFeature | 待第1项完整核准；保留历史成果 |
| 12 | SendSecureMessageFeature | 待第1项完整核准；保留历史成果 |
| 13 | UploadTransparentAvatarFeature | 待第1项完整核准；保留历史成果 |
| 14 | SpecialMessageFeature | 待第1项完整核准；保留历史成果 |
| 15 | HiddenAtFeature | 待第1项完整核准；保留历史成果 |
| 16 | EmojiAtFeature | 本轮局部修复及专项通过；整体待核准 |
| 17 | FloatingShortcutFeature | 待第1项完整核准；保留历史成果 |
| 18 | FloatingChatHeaderFeature | 待第1项完整核准；保留历史成果 |
| 19 | CustomBottomBarFeature | 待第1项完整核准；保留历史成果 |
| 20 | DynamicBadgeFeature | 待第1项完整核准；保留历史成果 |
| 21 | RoundAvatarFeature | 待第1项完整核准；保留历史成果 |
| 22 | CustomFriendAvatarFeature | 待第1项完整核准；保留历史成果 |
| 23 | RealNameTailFeature | 待第1项完整核准；保留历史成果 |
| 24 | MemberTitleFeature | 待第1项完整核准；保留历史成果 |
| 25 | NicknameWidthFeature | 待第1项完整核准；保留历史成果 |
| 26 | AutoRedPacketFeature | 待第1项完整核准；保留历史成果 |
| 27 | WeChatTabletFeature | 待第1项完整核准；保留历史成果 |
| 28 | AutoTransferFeature | 待第1项完整核准；保留历史成果 |
| 29 | FakeWalletBalanceFeature | 待第1项完整核准；保留历史成果 |
| 30 | AntiRecallFeature | 待第1项完整核准；保留历史成果 |
| 31 | MultiRecallFeature | 待第1项完整核准；保留历史成果 |
| 32 | DisableHotUpdateFeature | 待第1项完整核准；保留历史成果 |
| 33 | ProfileIdFeature | 待第1项完整核准；保留历史成果 |
| 34 | HideChatMenuFeature | 待第1项完整核准；保留历史成果 |
| 35 | MessageMenuExtensionFeature | 待第1项完整核准；保留历史成果 |
| 36 | QuickContactEditFeature | 待第1项完整核准；保留历史成果 |
| 37 | QuickGroupChatLabelFeature | 待第1项完整核准；保留历史成果 |
| 38 | QuickMomentsFeature | 待第1项完整核准；保留历史成果 |
| 39 | QuickMarkReadFeature | 待第1项完整核准；保留历史成果 |
| 40 | QuickTerminateFeature | 待第1项完整核准；保留历史成果 |
| 41 | AutoReplyFeature | 本轮局部修复及专项通过；整体待核准 |
| 42 | AutoMessageForwardFeature | 待第1项完整核准；保留历史成果 |
| 43 | ConversationGroupFeature | 待第1项完整核准；保留历史成果 |
| 44 | GroupBarFeature | 待第1项完整核准；保留历史成果 |
| 45 | ConversationAnalysisFeature | 待第1项完整核准；保留历史成果 |
| 46 | MessageAffixFeature | 待第1项完整核准；保留历史成果 |
| 47 | TypingReportBlockFeature | 待第1项完整核准；保留历史成果 |
| 48 | PatBlockFeature | 待第1项完整核准；保留历史成果 |
| 49 | AutoOriginalImageFeature | 待第1项完整核准；保留历史成果 |
| 50 | AutoViewOriginalFeature | 待第1项完整核准；保留历史成果 |
| 51 | RemoveForwardLimitFeature | 待第1项完整核准；保留历史成果 |
| 52 | AtAllNotificationBlockFeature | 待第1项完整核准；保留历史成果 |
| 53 | CallMediaLimitFeature | 待第1项完整核准；保留历史成果 |
| 54 | CallRingtoneBlockFeature | 待第1项完整核准；保留历史成果 |
| 55 | CustomNotificationFeature | 待第1项完整核准；保留历史成果 |
| 56 | KeywordNotificationFeature | 待第1项完整核准；保留历史成果 |
| 57 | TextSpeechFeature | 本轮局部修复及专项通过；整体待核准 |
| 58 | TextVoiceFeature | 待第1项完整核准；保留历史成果 |
| 59 | ZombieCheckFeature | 待第1项完整核准；保留历史成果 |
| 60 | WeChatKeepAliveFeature | 待第1项完整核准；保留历史成果 |
| 61 | QuoteDeleteClearFeature | 待第1项完整核准；保留历史成果 |
| 62 | SwipeQuoteFeature | 待第1项完整核准；保留历史成果 |
| 63 | AudioTransformFeature | 待第1项完整核准；保留历史成果 |
| 64 | FakeVoiceDurationFeature | 待第1项完整核准；保留历史成果 |
| 65 | ChatTimeStyleFeature | 待第1项完整核准；保留历史成果 |
| 66 | InputHintFeature | 待第1项完整核准；保留历史成果 |
| 67 | MessageBubbleFeature | 待第1项完整核准；保留历史成果 |
| 68 | MessageTextColorFeature | 待第1项完整核准；保留历史成果 |
| 69 | MentionHighlightFeature | 待第1项完整核准；保留历史成果 |
| 70 | MentionNoticeFeature | 待第1项完整核准；保留历史成果 |
| 71 | HomeTextColorFeature | 待第1项完整核准；保留历史成果 |
| 72 | HideChatAvatarFeature | 待第1项完整核准；保留历史成果 |
| 73 | MessageBlockFeature | 待第1项完整核准；保留历史成果 |
| 74 | FakeLocationFeature | 待第1项完整核准；保留历史成果 |
| 75 | GameEmojiFeature | 待第1项完整核准；保留历史成果 |
| 76 | FakeMiniProgramBaseLibFeature | 待第1项完整核准；保留历史成果 |
| 77 | SkipMiniProgramVideoAdsFeature | 待第1项完整核准；保留历史成果 |
| 78 | SkipGlobalMiniProgramSplashAdsFeature | 待第1项完整核准；保留历史成果 |
| 79 | QQMusicOrderFeature | 本轮局部修复及专项通过；整体待核准 |
| 80 | MomentsAutoLikeFeature | 待第1项完整核准；保留历史成果 |
| 81 | MomentsAutoCommentFeature | 待第1项完整核准；保留历史成果 |
| 82 | MomentsAutoForwardFeature | 待第1项完整核准；保留历史成果 |
| 83 | MomentsAutoRefreshFeature | 待第1项完整核准；保留历史成果 |
| 84 | MomentsKeywordBlockFeature | 待第1项完整核准；保留历史成果 |
| 85 | MomentsContactFilterFeature | 待第1项完整核准；保留历史成果 |
| 86 | MomentsBottomDetailFeature | 待第1项完整核准；保留历史成果 |
| 87 | MomentsPostNotificationFeature | 待第1项完整核准；保留历史成果 |
| 88 | OriginalMomentsUploadFeature | 待第1项完整核准；保留历史成果 |
| 89 | MomentsUploadTailFeature | 待第1项完整核准；保留历史成果 |
| 90 | SnsAntiRecallFeature | 待第1项完整核准；保留历史成果 |
| 91 | MomentsFakeInteractionFeature | 待第1项完整核准；保留历史成果 |
| 92 | GroupChatLabelFeature | 待第1项完整核准；保留历史成果 |
| 93 | GroupLeaveMonitorFeature | 待第1项完整核准；保留历史成果 |
| 94 | GroupRenameMonitorFeature | 待第1项完整核准；保留历史成果 |
| 95 | RemoveMomentsAdsFeature | 待第1项完整核准；保留历史成果 |
| 96 | EditMessageFeature | 待第1项完整核准；保留历史成果 |
| 97 | EmojiSaveFeature | 待第1项完整核准；保留历史成果 |
| 98 | VoicePreviewFeature | 待第1项完整核准；保留历史成果 |
| 99 | VoiceToTextFeature | 待第1项完整核准；保留历史成果 |
| 100 | VoiceForwardFeature | 待第1项完整核准；保留历史成果 |
| 101 | SelectedMessagesFeature | 待第1项完整核准；保留历史成果 |
| 102 | MessageForwardFeature | 待第1项完整核准；保留历史成果 |
| 103 | ScheduledTaskFeature | 待第1项完整核准；保留历史成果 |
| 104 | FakeScanCameraFeature | 待第1项完整核准；保留历史成果 |
| 105 | ProtobufPacketFeature | 待第1项完整核准；保留历史成果 |
| 106 | StatusTextLimitFeature | 待第1项完整核准；保留历史成果 |
| 107 | FinderMediaDownloadFeature | 待第1项完整核准；保留历史成果 |
| 108 | HchatExtraFeature | 待第1项完整核准；保留历史成果 |
| 109 | ScriptPluginFeature | 待第1项完整核准；保留历史成果 |

## 2026-11-02 接续3：第20号动态角标专项
- 动态角标运行时已接入功能独占 PreferenceRuntimeOwner、OwnedHooks、OwnedHandler 与独占 DexInstallScheduler.Installation；关闭、销毁、重开、部分安装失败回滚和迟到旧代回调均有门控。
- BadgeListRecovery、BadgeWindowObserver、BadgeOverlay、AnimatedBadgeDrawable 已补齐重复登记、窗口迁移、监听/任务/Drawable callback 释放及清理异常隔离。
- 验证：dynamic_badge_recall、test_dynamic_badge_cleanup、test_dynamic_badge_sdk 均通过；结果为合成宿主或 SDK/缓存 ABI 类型检查，不是 Android/R8 构建或实机验收。
- 第20项专项状态：源码闭环及专项证据完成；109项最终核准仍 0/109，本阶段仍 0/7。

## 接续：第21号圆角头像专项
- 修复三处通知Hook条件语法；OwnedHooks统一所有权、安装失败/异常回滚及代际门控已落地。
- 发现并修复微信Hook回滚会同时卸载通知Hook、重试却不补装通知Hook的遗漏；重试现在覆盖两类Hook。
- runtime与安装票据清理均在finally切断引用，防止释放抛错遗留schedule。
- round-owner-1.log：7个安装失败位置、完整重试、旧回调、销毁重入迟到句柄、单项unhook失败隔离、新运行时重装通过。
- round-sdk-final.log：完整圆角头像生产运行时代码SDK+缓存ABI类型检查通过；设置开关聚合器为显式合成ABI。
- monitor-round-1.log扩大编译失败原文保留；round-owner-red.log是测试ABI不匹配的编译失败，不冒充行为红测。
- 4个专项测试已登记run_resource_regression.py；未运行全量套件，未执行Gradle/Android/R8/实机验收。
- 仍在第1项，最终核准0/109，总进度0/7；不提交不推送。下一入口第22号CustomFriendAvatarFeature。

## 接续：第22号自定义头像专项
- 通知/主头像与菜单/桌面快捷方式采用三组独占OwnedHooks；局部失败不会卸载其他组，重试不受旧标记短路。
- 两张安装票据分开登记释放，schedule/runtime引用在finally断开；OwnedHandler关闭撤销独占Toast投递。
- 主组回滚与销毁逐项隔离，注销菜单抛错不阻断映射和位图缓存清理；旧Hook/菜单回调检查捕获的owner是否已关闭。
- custom-sdk-3.log、custom-owner-1.log通过；事务回归覆盖7个故障位置、重试、跨组独立性、清理抛错、销毁重入。业务回调、定位与UI是显式合成边界，不冒充完整业务测试。
- CustomFriendAvatarPicker/Store存在会话分组、设置页和多个通知消费者，归第2项共享归属；没有为关闭单个功能而全局销毁它们。
- 原始失败日志和源码指纹已归档；新增两项测试登记总回归，但未执行全量回归/Gradle/Android/R8/实机验收。
- 最终核准仍0/109，总进度0/7；不提交不推送。第23号RealNameTailFeature开始源码核查。

## 接续：第23号实名尾字专项（2026-11-02）
- OR双开关真实表达式+附加监听在生产PreferenceRuntimeOwner/FeatureResourceScope上做合成验证：任一开关保持启用，双关释放，创建失败回滚后重试，旧监听、关闭重开及新实例重装。
- 查询Hook安装仍受OwnedHooks持有；修正query与DexKit串行锁反转风险，网络发送抛错或拒绝时清理pending；部分创建失败时清掉store引用。
- 调度器/资料预取独占线程统一登记、退出清理、中断；补线程构造/执行异常隔离，保持不阻塞UI的销毁。首次worker回归在生产线程体异常处失败；修复后通过。
- test_realtail_workers、test_realtail_query_send、test_realtail_toggle、test_realtail_owner、test_realtail_scheduler_lifecycle、test_preference_runtime_owner及test_realtail_sdk通过，新增五项实名尾字专项已登记run_resource_regression.py；全量回归尚未运行。原始日志及源码SHA256见EVIDENCE的RealNameTailFeature，原始日志留/tmp/pl-stage1-20261102-resume4/。
- 合成方法/SDK+缓存ABI并非完整Android/R8构建、微信业务或设备/内存验收；共享NativeGroupNicknameGate归第2项，在途业务/页面归第3项，容量归第4项。历史1～22与第23号均未最终核准，仍0/109，七阶段0/7；未提交未推送。下一入口第24号MemberTitleFeature，但不得据此自动跳过第23号后续整体核准。

## 接续：第24号群员头衔专项（2026-11-02）
- 默认关闭不装Hook；以PreferenceRuntimeOwner启用区间持有renderer、Dex安装票据和chatroomChanges订阅，关闭撤销、旧订阅无效、重开创建新实例。
- 安装异常释放昵称门控租约且日志异常不阻断回滚；renderer.destroy同步关闭、单项缓存/Hook/门控/任务/UI清理隔离，离主线程迟到恢复加代际门控，避免旧实例覆盖新实例。
- test_member_title_toggle、test_member_title_owner、test_member_title_lifecycle（增加旧实例迟到恢复）、test_member_title_sdk通过；新三项已登记总回归但未运行全量。证据SHA256与文件指纹归档EVIDENCE，原始日志在/tmp/pl-stage1-20261102-resume4/。
- 部分合成宿主和SDK/缓存ABI检查不等于Android/R8构建及实机；NativeGroupNicknameGate共享内部缺口归第2项，在途页面/业务归第3项，容量归第4项。24号专项不代表最终核准，仍0/109、阶段0/7；没有提交/推送。下一入口25号NicknameWidthFeature。

## 接续：第25号昵称宽度专项（2026-11-02）
- 默认关闭不装 Hook；关闭卸载独占 Hook，重开在主线程先恢复原宽度再补装；旧代 Hook/恢复回调受 generation 门控，版本仍仅允许 8.0.78。
- test_nickname_width、test_nickname_width_toggle、check_nickname_width_ui 均通过；其中 toggle 为源码契约和合成状态模型，非真实微信 Hook 行为。SHA256 与原始日志归档 EVIDENCE。
- 未运行 Gradle/Android/R8 或实机验收；在途回调与主线程投递失败尚需后续闭环。109入口最终核准仍0/109、七阶段0/7；未提交未推送。

## 接续：第25号昵称宽度闭环修正（2026-11-03）
- 先核实现场：分支 dev、HEAD c90207f18fe7729860619748b185053b841c4b27、`git diff --check` 退出码 0、工作区 56 项未提交改动（含新增测试与证据）全部保留，未执行 reset/clean，未运行 Gradle。
- 原实现问题（源码证据，非推测）：单个 Feature 直接持 hook + WeakHashMap 状态，关闭只还原宽度不撤状态结构；旧 restoreAll 可覆盖新实例已接管的视图；`main.post` 返回值被忽略，投递失败即永久丢失还原；安装失败无回滚重试；回调异常时直接置 enabled=false 静默停用，无频率限制且绕过所有权释放。
- 改造：拆分为 Feature + NicknameWidthRuntime（每个启用区间一个新实例）+ NicknameWidthState（进程级 WeakHashMap<TextView,Record> + token 归属）。Runtime 独占 OwnedHooks 与 DexInstallScheduler.Installation 票据；close 顺序为「停绑定并还原宽度 → 逐句柄卸载 → 取消票据」；跨线程还原在主线程投递被拒时就地还原并上报；旧实例只清自己 token 的记录。
- 验证（生产代码真执行，不只是字符串断言）：`scripts/test_nickname_width_lifecycle.py` 编译并运行真实 production 源码 + 真实 PreferenceRuntimeOwner/FeatureResourceScope/OwnedHooks/DexInstallScheduler，覆盖默认关闭不装 Hook、开启安装 b0.p、群聊/单聊/企业微信群宽度、关闭卸载并还原、重开重装、安装失败回滚后有限重试、unhook 抛错隔离、销毁后新实例重装、版本门 8.0.79 拒绝、post 被拒就地还原、宿主改宽不被覆盖、回收视图跨实例接管。
- `scripts/test_nickname_width.py` 改为 android.jar ABI 直编（含核心所有权源码），原版本编译已破坏故重写；`scripts/check_nickname_width_ui.py` 通过。`test_nickname_width_toggle.py` 已删除（纯源码契约+合成模型，被真实执行取代），总回归登记改为 `test_nickname_width_lifecycle`。
- 三份日志与源码 SHA256 写入 `docs/RESOURCE_LIFECYCLE_STAGE1_EVIDENCE.json:featureContinuations.NicknameWidthFeature`，原始日志在 `/tmp/pl-stage1-20261103-nickname-width/`；审计条目 `NicknameWidthFeature` 已按真实源码重写，旧「关闭不撤 Hook」结论移入 resolvedFindings。
- 边界：未运行 Gradle/Android/R8 构建，未在真实微信 8.0.78 会话验证回调线程、View 复用与内存回收；共享 DexInstallScheduler owner=null 通用边界归第2项。109入口最终核准仍 0/109，七阶段 0/7；未提交未推送。下一入口第26号。
## 接续：第26号自动抢红包专项（2026-11-03）
- 现场：分支 dev、HEAD c90207f18fe7729860619748b185053b841c4b27、`git diff --check` 退出码 0；历史未提交改动与新增测试/证据全部保留，未 reset/clean，未运行 Gradle。
- 入口所有权：三个开关（hb_auto_enable / hb_fake_packet_enable / hb_block_new_group_enable）的 OR 作为 PreferenceRuntimeOwner 启用区间，restartOnChange=true；每个区间独占一个 RedPacketRuntime，Runtime 独占本代 RedPacketHooker 与 DexInstallScheduler.installation(auto_redpacket, WARMUP) 票据。
- 关闭顺序（源码注释写明不可交换）：锁内置 closed 并取 hooker/ticket 快照双置 null → closeTicket 停重试 → destroyHooker。destroy 首行置 destroyed 挡住此后进入的调度重试，先 resources.close()，再按安装逆序逐子组件销毁（newGroupBlocker→notifier→announcer→autoReply→silentHandler→fakePacketHook→uiAutomator→databaseHook→messageHook→networkDispatcher），逐项 catch 隔离。hookAll 改 synchronized 且首行判 isDestroyed。
- PaymentResourceScope 契约收紧：post 返回 boolean，抛错与被拒两路都回滚 pending 并 release 捕获；MAX_PENDING=128 硬上限；Posting.run 锁内校验 closed 与 pending 身份、取走 claimed 后出锁执行，未 claim 条目 close 时统一释放；close 先 release 全部 Posting 再逆序执行 releases；关闭后 hook 抛 Payment owner closed。
- 追加修复两处此前未处理的投递返回值（本轮范围内真实缺陷）：RedPacketAnnouncer.retryPendingSpeeches 末尾 postDelayed 原先裸调，被拒会让 pendingSpeeches 里的播报键永久 withhold，现改为 catch + !queued 时全量 releasePending 并记「红包播报重试投递被拒绝」；RedPacketNotifier.playManualNotificationSound 的 stopRingtone 延迟任务原先裸调，被拒会让铃声响到进程结束，现改为 catch + !scheduled 时就地 stopRingtone 并记「铃声停止任务投递被拒绝，就地停止」。
- 验证（生产代码真执行）：`scripts/test_payment_resource_scope.py` 重写为 701 行，Phase A 用 javac 编译真实 PaymentResourceScope + 合成 Looper/Handler/HookRegistry，覆盖同键替换、cancel 丢弃捕获、被拒/抛错回滚、128 预算、同 Member 只装一次、逆序异常隔离幂等关闭、关闭后拒绝 hook/post、关闭丢弃待执行捕获、action 抛错不外泄；Phase B 用 Kotlin 编译器编译真实 AutoRedPacketFeature + 真实 PreferenceRuntimeOwner/FeatureResourceScope/DexInstallScheduler，覆盖默认关闭、单键启用、DexReady 幂等、关闭销毁、第二键单独启用、restartOnChange 重启顺序、部分权重关闭重启、全关后 DexReady 不复活、重开后安装键归属已释放、failHookAllTimes=2 后有限重试收敛到单次安装、destroy 抛错隔离且重复销毁不重复释放、销毁后开关与 DexReady 均失效。四行 PASS、exit=0。
- 新增 `scripts/test_payment_notify_abi.py`（57 行）：对真实 RedPacketAnnouncer/RedPacketNotifier 用 android.jar + 缓存模块 ABI 做类型检查，唯一合成物为一方法的 CustomFriendAvatarStore 桩，PASS 行明写「not Android/R8/device」；已登记 run_resource_regression.py。
- 突变检验（证明脚本不是恒绿）：A 删除 post 的 !queued 回滚块 → Phase A `AssertionError: 被拒绝的投递应返回 false`；B 删除 close() 的 closeTicket → Phase B `IllegalStateException: 等待超时: 启用后安装红包 Hook`，日志给出 `Dex installation key already belongs to another owner` 与 6 次重试后停止。两次注入均被捕获，突变副本已删除。
- 边界：未运行 Gradle/Android/R8 构建；缓存 ABI 为 2026-10-29～11-02 旧产物，WeChatNetworkDispatcher 缺 destroy 且多个宿主类缺失，故全 payment 包编译不可成立，只能声称两个 notify 文件通过类型检查。PaymentResourceScope 是各子组件独立实例而非共享单例，共享调度与在途业务分别归第2/第3项，容量归第4项，未越界改动。
- 审计第26条已按真实源码重写（原「post 失败未处理/pending 无硬总量/出锁交错」结论移入 resolvedFindings），EVIDENCE 新增 featureContinuations.AutoRedPacketFeature（含两份日志 SHA256、11 个文件指纹、两次突变记录）。109 入口最终核准仍 0/109，七阶段 0/7；未提交未推送。下一入口第27号 HchatExtraFeature。
## 接续：第27号分支扩展专项（2026-11-03）
- 现场：分支 dev、HEAD `c90207f18fe7729860619748b185053b841c4b27`、固定基准 `caa675c`、`git diff --check` 退出码 0；历史未提交改动与新增测试/证据全部保留，未 reset/clean，未运行 Gradle。
- 入口所有权：`HchatExtraFeature` 不再直接持 Hook；每个启用区间新建一个 `HchatExtraRuntime`，独占本代 `HchatExtraHooker` 与三张 `DexInstallScheduler.installation` 票据（`hchat_extra_id` / `hchat_extra_message_details` / `hchat_extra_forwarded_types`，均 `Stage.WARMUP`）。`start()` 在建 hooker 与票据任一步抛错时回滚已建部分；关闭期间并发进入的 `start()` 在锁内被判 `closed` 后 `closeTicket` + `destroyHooker` 回滚孤儿，不得留下无人持有的代。
- 关闭顺序（源码注释写明不可交换）：锁内置 `closed`、取 `hooker to tickets` 快照并双置 null → `tickets.forEach(::closeTicket)` 先停重试 → `destroyHooker` 后销毁本代。反过来会让调度重试把 Hook 装进已销毁实例。
- `HchatExtraHooker.destroy()` 置 `destroyed` 门后按九个 `releaseSafely` 逐项释放（动态撤回运行时 / 聊天记录类型 / 消息详情偏好监听 / 隐藏头像偏好监听 / 头像详情间距 / 消息详情登记 / 消息详情监听 / 尾部修复token / 独占Hook），Hook 最后撤；每个 helper 自带 try/catch，上报失败也不阻断后续释放。
- `ForwardedRecordTypeLabels` 全面改用 `OwnedHooks` / `OwnedHandler`：`install()` 由 Hook 句柄是否为 null 决定返回值，被拒不再谎报成功；`refresh()` 与 `destroy()` 的摘除投递都判返回值（摘除走不受本代关闭影响的 `detachHandler`），`destroy()` 顺序为 摘句柄 → 关 main → 清 rows/adapters → 摘行。
- 投递返回值全覆盖：本文件九处 `post`/`postDelayed`（尾部修复超时、红包记录两次时间写入、消息详情颜色/布局刷新、附着补色、编辑滚动恢复）全部改为判返回值，被拒走 `logThrottled`（10 秒闸门）。本轮把上一轮判为「允许例外」的 `editor.post` 反转为真实检查——第27号现在没有豁免的裸投递。
- 新发现并修掉的真实缺陷：`releaseSafely("头像详情间距")` 与 `restoreAvatarDetailsSpacing` 内部都是整批 `forEach`，单视图抛错会跳过其余视图的 padding/clip 还原，现改为逐项 try/catch 隔离（`分支扩展释放失败: 头像详情间距`）。
- `MessageDetailsPendingLayoutObserver` 修掉两处锁存器死锁：即时投递被拒时 `posted` 未归位会让该行后续 `globalLayout` 全部早退；轮询投递被拒时 `polling` 未归位会让 `schedulePoll` 永久早退、8 次预算白扣。现在两处都在被拒路径归位，轮询链能活到下一次布局。
- 验证（生产代码真执行，非字符串断言）：`scripts/test_hchat_extra_scope.py` 四阶段。Phase A 切片真实 `HchatExtraRuntime` 原文 + 真实 `DexInstallScheduler` + 桩 hooker，覆盖 WARMUP 门、票据去重、关闭停重试、幂等 close、start 中孤儿回滚、干净重开且不误报；Phase B 编译真实 `ForwardedRecordTypeLabels.kt` + 真实 `FeatureOwnership.kt`，覆盖被拒安装返回 false、单句柄、真实 bind、被拒投递上报、销毁丢弃排队刷新、句柄只释放一次、迟到回调 no-op、unhook 失败隔离；Phase C 编译真实 `MessageDetailsPendingLayoutObserver.kt`，覆盖即时投递被拒后下一次布局重试、轮询被拒后链条仍活、clear 摘监听、脱离窗口抑制、连续被拒后恢复；Phase D 剥注释后做源码不变量（入口所有权、四个重启键、先释放后启动、不直接触 `HookRegistry`、Hook 最后撤、票据先于 hooker、孤儿回滚、每处投递判返回值）。四行 PASS、`feature lines: 4436`、`labels lines: 270`、`ALL PHASES PASSED`、exit=0。
- 突变检验（证明脚本不恒绿）：`scripts/mutate_hchat_extra_scope.py` 八条注入全部 FAIL-CAUGHT——A posted 归位失效、B labels 谎报安装成功、C 删 `ownedHooks.close()`、D 尾部超时改裸调、E 逐视图隔离换 `run{}`+`if (false)`、F 摘除投递不判返回值、G labels 先关 main 再卸句柄、H Runtime 先销毁后停重试。`BASELINE exit=0`、`mutations caught: 8/8`、`FINAL exit=0 (restored)`。
- 证据：两份日志 SHA256 为 `fdc3de24e4d32e81e4cce549c52fabc8f8bd6b4a0f834df32631b0670e95ff36`（924 字节）/ `2a812a097e83cae66e00354d5238d8ddaf0d20ac944463160afb6c310c3bd35a`（1307 字节），原始日志在 `/tmp/pl-stage1-20261103-hchatextra/`；10 个文件指纹写入 `docs/RESOURCE_LIFECYCLE_STAGE1_EVIDENCE.json:featureContinuations.HchatExtraFeature`，历史 7 个 continuation 指纹零漂移；`run_resource_regression.py` 登记 `test_hchat_extra_scope`（129 → 130）。审计条目 `features[107]` 已按真实源码重写，四条旧缺口移入 resolvedFindings，openFindings 收为 2 条（109 核准 0/109、设备验收缺口）。
- 边界：未运行 Gradle/Android/R8 构建，未在真实微信 8.0.78 会话验证回调线程、View 复用与内存回收；缓存 ABI 为 2026-10-29～11-02 旧产物且缺多个宿主类，全包编译不可成立，只能声称四阶段定向编译并执行通过。共享 `DexInstallScheduler` 与 `NativeGroupNicknameGate` 内部引用计数归第2项，在途页面与业务归第3项，容量归第4项。109 入口最终核准仍 0/109，七阶段 0/7，`deviceAcceptance` 仍为 false。（本节为第27号收口当时状态：当时未提交未推送；第28号已完成，两者一并进入了 `b20c093`。）

## 接续：第28号脚本插件专项（2026-11-03）
- 现场：分支 dev、HEAD `c90207f18fe7729860619748b185053b841c4b27`、固定基准 `caa675c`；历史未提交改动与新增测试/证据全部保留，未 reset/clean，未运行 Gradle。（收口当时未提交；此后已完成一次性中文提交并推送，结果见文件头 `b20c093`。）
- 入口所有权：`ScriptPluginFeature` 不再持有任何全局安装标志；每个启用区间新建 `ScriptHookGeneration`，独占四张 `DexInstallScheduler.Installation` 票据（`script_plugin:menus` / `:message` / `:new_friend` / `:member_change`，全部 `Stage.WARMUP`）、两份本代订阅（`ScriptMessageHook` 的消息变动监听 + 订阅句柄、`ScriptMemberChangeHook` 的群成员监听 + 消息观察监听）以及好友申请监听的共享租约（`SharedHookLease`，key `shared:new_friend_listener`，owner `script_plugin`）。`BaseFeature.trackResource` 兜底登记，`onFeatureDestroy` 未执行也会撤销本代。
- 关闭顺序（源码注释写明不可交换）：锁内置 `closed`、取 `tickets` / `newFriendLease` / `started` 三元组；`started` 为假直接 return（从未成功 start 的代不关闭别人的运行时、不撤销别人的订阅）→ 取消票据停重试 → 撤销本代两份订阅 → 关闭本代运行时 → 归还共享租约。`start()` 在锁内被拒时对新建票据 `created.forEach(::closeTicket)` 回滚孤儿。
- 本轮修掉的真实生产缺陷：`installNewFriend()` 在取得租约后才发现 `closed`，原先直接 `return false` 会把 owner 留在共享租约里，脚本退出后自动回复仍持有时监听撤不掉、反之亦然；现改为就地 `release(OWNER_SCRIPT)`。`ScriptPluginRuntime.install(context)` 包 try/catch，运行时不抛是常态，万一抛也不能让本代停在半启动状态。
- 验证（生产代码真执行，非字符串断言）：`scripts/test_script_plugin_scope.py`（1164 行）四阶段。Phase A 编译真实 `SharedHookLease.kt`，覆盖 0→1 装、1→2 不装、同 owner 幂等、2→1 不撤、1→0 撤、失败不留幽灵、异常按失败处理并上报、单 key 撤销异常不阻断其他 key、关闭幂等、关闭后 acquire false、关闭后再 register 不复活、安装期间被关闭就地撤销；Phase B 切真实 `ScriptHookGeneration` + 真实 `ScriptSharedDexKeys`（原文切到文件末尾）并接入真实 `DexInstallScheduler.kt`/`SharedHookLease.kt`，覆盖 WARMUP 门、四票去重、关闭停重试、共享租约独立性、四步关闭顺序、幂等 close、已关闭代拒绝启动、取得租约期间被关闭立即归还、干净重开；Phase C 编译真实 `ScriptMessageHook.kt` 与 `ScriptMemberChangeHook.kt` 配三份 WeChat API 桩，覆盖单次订阅、句柄与 listener 双还、撤销后不分发、重复撤销幂等、重装后只还本代句柄（监听解绑次数）、被拒订阅安装失败、API 不可用不订阅、成员首次建基线、重装必重建基线；Phase D 剥注释后做源码不变量（四个 `$ID:*` 必须用 `installation` 且禁止裸 `schedule`、`trackResource` 兜底、`close()` 四步顺序、`ScriptNewFriendHook` 无 `installed` 全局标志、两个 Hook 必须有 `hostSubscriptions` 与 `uninstall`、`AutoReplyFeature` 必须 acquire/release）。四行 PASS、`feature lines: 299`、`ALL PHASES PASSED`、exit=0，连跑三次无抖动。
- 突变检验（证明脚本不恒绿）：`scripts/mutate_script_plugin_scope.py`（259 行，19 条注入）。第一轮 15/19，逐条定性后收口：第 3 条「删 `close()` 内 `if (closed) return`」判定为等价突变（关闭守卫在 register/acquire/close 三层都在），替换为「`close()` 不撤宿主 Hook」；第 6 条（uninstall 不清登记表）、第 7 条（成员快照跨代沿用）、第 14 条（取得租约期间被关闭不归还）为测试真实盲区，分别补上监听解绑次数、重装首个群事件必须重建基线、`onAcquired` 回调触发并发 close 三条断言。补强后 `BASELINE exit=0`、`mutations caught: 19/19`、`ALL MUTANTS CAUGHT`、exit=0。
- 顺带修掉的测试侧缺陷（不影响生产语义但会让红测指错地方）：Phase B 的 `await("首次菜单安装尝试") { count > 0 }` 用绝对阈值，前面几代已装过菜单时会立刻返回，漏掉本代首次尝试，改为相对 `menuBase`；「关闭后不得再重试安装」原先固定等 1200ms 就断言，而 `DexInstallScheduler.cancel` 明确不中断已在执行器里跑的那一次安装，改为先让在途尝试落地取快照、再跨过 1000ms/3000ms 两个重试周期断言计数不增长。
- 证据：`docs/RESOURCE_LIFECYCLE_STAGE1_EVIDENCE.json:featureContinuations.ScriptPluginFeature` 含两份日志（`test_script_plugin_scope.log` SHA256 `b7a5bcfde9e7ae8713b41c26bbb27023be34ce36ac7d57ab122df1cd1df6a2a2` 1108 字节、`mutate_script_plugin_scope.log` SHA256 `54db4bde15f5c081a94f9c596d5b1ccb30de5ae756b970e3bc422eb3936453e7` 4546 字节，内嵌文本与磁盘逐字节一致）、8 个文件指纹、4 条突变定性记录、7 条 deferred 与验证边界。原始日志在 `/tmp/pl-stage1-20261103-scriptplugin/`。
- 回归：`scripts/run_resource_regression.py` 登记 `test_script_plugin_scope`（130 → 131，131 项唯一）。审计 `features[108]` 已按真实源码重写（status 366 字），本轮收口补证追加进 resolvedFindingEvidence（现 7 条），openFindings 仍 2 条（109 核准 0/109、设备验收缺口），findingReview 2 条验收边界。
- 边界：未运行 Gradle/Android/R8 构建，未在真实微信 8.0.78 会话验证宿主回调线程、插件生命周期与内存回收；四阶段是合成 JVM 边界下真执行生产代码切片与源码不变量。共享 `NativeGroupNicknameGate` 引用计数与共享 `DexInstallScheduler.schedule(owner=null)` 通用重试边界归第2项，在途消息分发与脚本执行业务归第3项，消息分发队列 128 格 / 媒体补查队列 32 格 / 去重表容量归第4项。109 入口最终核准仍 0/109，七阶段 0/7，`deviceAcceptance` 仍为 false。（收口当时未提交未推送；已在 `b20c093` 一次性中文提交并推送到 `origin/dev`，见文件头。）
- 编号勘误（2026-11-03 核实）：第28号对应台账第 109 行、审计 `features[108]`，即 109 个入口的最后一条，**不存在第29号**。此前若干小节里的「下一入口第N号」只是当时随手指到的下一个未收口条目，不是严格顺序编号——例如第26号 AutoRedPacketFeature 之后并没有走台账第 27 行 WeChatTabletFeature，而是直接跳到台账第 108 行 HchatExtraFeature。后续接续一律以台账行号与审计下标为准，不按「+1」推。第1项剩余待收口条目：台账共 109 行，已收口 9 行——台账行 20~26（审计 features[19]~[25]：DynamicBadge、RoundAvatar、CustomFriendAvatar、RealNameTail、MemberTitle、NicknameWidth、AutoRedPacket）、台账行 108（审计 features[107] HchatExtraFeature）、台账行 109（审计 features[108] ScriptPluginFeature）；其余 100 行为未核准入口，从台账第 1 行 SettingsFeature（审计 features[0]）起逐条推进。换算关系固定为：台账行号 = 审计下标 + 1。
