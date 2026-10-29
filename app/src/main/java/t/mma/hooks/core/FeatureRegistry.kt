package t.mma.hooks.core

import t.mma.hooks.api.core.WechatApiFeature
import t.mma.hooks.items.audiotransform.AudioTransformFeature
import t.mma.hooks.items.antirecall.AntiRecallFeature
import t.mma.hooks.items.atallnotify.AtAllNotificationBlockFeature
import t.mma.hooks.items.automessageforward.AutoMessageForwardFeature
import t.mma.hooks.items.autoreply.AutoReplyFeature
import t.mma.hooks.items.autooriginal.AutoOriginalImageFeature
import t.mma.hooks.items.autovieworiginal.AutoViewOriginalFeature
import t.mma.hooks.items.backgroundbeauty.BackgroundBeautyFeature
import t.mma.hooks.items.callmedialimit.CallMediaLimitFeature
import t.mma.hooks.items.callmedialimit.CallRingtoneBlockFeature
import t.mma.hooks.items.chattime.ChatTimeStyleFeature
import t.mma.hooks.items.chattoolbar.ChatToolbarFeature
import t.mma.hooks.items.conversationgroup.ConversationGroupFeature
import t.mma.hooks.items.conversationanalysis.ConversationAnalysisFeature
import t.mma.hooks.items.homesidepanel.HomeSidePanelFeature
import t.mma.hooks.items.monetgenerator.MonetModuleGeneratorFeature
import t.mma.hooks.items.securemessage.AntiSecureMessageFeature
import t.mma.hooks.items.securemessage.SendSecureMessageFeature
import t.mma.hooks.items.transparentavatar.UploadTransparentAvatarFeature
import t.mma.hooks.items.custombottombar.CustomBottomBarFeature
import t.mma.hooks.items.dynamicbadge.DynamicBadgeFeature
import t.mma.hooks.items.customfriendavatar.CustomFriendAvatarFeature
import t.mma.hooks.items.customnotify.CustomNotificationFeature
import t.mma.hooks.items.editmsg.EditMessageFeature
import t.mma.hooks.items.emojisave.EmojiSaveFeature
import t.mma.hooks.items.fakelocation.FakeLocationFeature
import t.mma.hooks.items.fakescancamera.FakeScanCameraFeature
import t.mma.hooks.items.fakevoiceduration.FakeVoiceDurationFeature
import t.mma.hooks.items.floatingshortcut.FloatingShortcutFeature
import t.mma.hooks.items.floatingchat.FloatingChatHeaderFeature

import t.mma.hooks.items.forwardlimit.RemoveForwardLimitFeature
import t.mma.hooks.items.gameemoji.GameEmojiFeature
import t.mma.hooks.items.groupleave.GroupLeaveMonitorFeature
import t.mma.hooks.items.grouplabel.GroupChatLabelFeature
import t.mma.hooks.items.grouplabel.QuickGroupChatLabelFeature
import t.mma.hooks.items.grouprename.GroupRenameMonitorFeature
import t.mma.hooks.items.hideavatar.HideChatAvatarFeature
import t.mma.hooks.items.hidemenu.HideChatMenuFeature
import t.mma.hooks.items.messagemenuextension.MessageMenuExtensionFeature
import t.mma.hooks.items.hotupdate.DisableHotUpdateFeature
import t.mma.hooks.items.hometextcolor.HomeTextColorFeature
import t.mma.hooks.items.inputhint.InputHintFeature
import t.mma.hooks.items.keepalive.WeChatKeepAliveFeature
import t.mma.hooks.items.keywordnotify.KeywordNotificationFeature
import t.mma.hooks.items.membertitle.MemberTitleFeature
import t.mma.hooks.items.messageaffix.MessageAffixFeature
import t.mma.hooks.items.messagebubble.MessageBubbleFeature
import t.mma.hooks.items.messagetextcolor.MessageTextColorFeature
import t.mma.hooks.items.mentionhighlight.MentionHighlightFeature

import t.mma.hooks.items.messageblock.MessageBlockFeature
import t.mma.hooks.items.messageforward.MessageForwardFeature
import t.mma.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibFeature
import t.mma.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsFeature
import t.mma.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsFeature
import t.mma.hooks.items.musicorder.QQMusicOrderFeature
import t.mma.hooks.items.moments.OriginalMomentsUploadFeature
import t.mma.hooks.items.moments.MomentsAutoLikeFeature
import t.mma.hooks.items.moments.MomentsAutoCommentFeature
import t.mma.hooks.items.moments.MomentsAutoForwardFeature
import t.mma.hooks.items.moments.MomentsAutoRefreshFeature
import t.mma.hooks.items.moments.MomentsBottomDetailFeature
import t.mma.hooks.items.moments.MomentsContactFilterFeature
import t.mma.hooks.items.moments.MomentsKeywordBlockFeature
import t.mma.hooks.items.moments.MomentsPostNotificationFeature
import t.mma.hooks.items.moments.RemoveMomentsAdsFeature
import t.mma.hooks.items.moments.SnsAntiRecallFeature
import t.mma.hooks.items.moments.MomentsUploadTailFeature
import t.mma.hooks.items.momentsfake.MomentsFakeInteractionFeature
import t.mma.hooks.items.multirecall.MultiRecallFeature
import t.mma.hooks.items.payment.core.AutoRedPacketFeature
import t.mma.hooks.items.payment.fakebalance.FakeWalletBalanceFeature
import t.mma.hooks.items.payment.transfer.AutoTransferFeature
import t.mma.hooks.items.patblock.PatBlockFeature
import t.mma.hooks.items.profileid.ProfileIdFeature
import t.mma.hooks.items.profileid.ProfileAddTimeFeature
import t.mma.hooks.items.protobuf.ProtobufPacketFeature
import t.mma.hooks.items.quoteclear.QuoteDeleteClearFeature
import t.mma.hooks.items.quickcontactedit.QuickContactEditFeature
import t.mma.hooks.items.quickmoments.QuickMomentsFeature
import t.mma.hooks.items.quickread.QuickMarkReadFeature
import t.mma.hooks.items.quickterminate.QuickTerminateFeature
import t.mma.hooks.items.realtail.RealNameTailFeature
import t.mma.hooks.items.roundavatar.RoundAvatarFeature
import t.mma.hooks.items.specialmessage.SpecialMessageFeature
import t.mma.hooks.items.script.ScriptPluginFeature
import t.mma.hooks.items.scheduledtask.ScheduledTaskFeature
import t.mma.hooks.items.selectedmessages.SelectedMessagesFeature
import t.mma.hooks.items.settings.SettingsFeature
import t.mma.hooks.items.shortvideo.FinderMediaDownloadFeature
import t.mma.hooks.items.statuslimit.StatusTextLimitFeature
import t.mma.hooks.items.swipequote.SwipeQuoteFeature
import t.mma.hooks.items.tablet.WeChatTabletFeature
import t.mma.hooks.items.autocache.AutoCacheFeature
import t.mma.hooks.items.textspeech.TextSpeechFeature
import t.mma.hooks.items.textvoice.TextVoiceFeature
import t.mma.hooks.items.typingreport.TypingReportBlockFeature
import t.mma.hooks.items.voiceforward.VoiceForwardFeature
import t.mma.hooks.items.hchatextra.HchatExtraFeature
import t.mma.hooks.items.voicepreview.VoicePreviewFeature
import t.mma.hooks.items.zombiecheck.ZombieCheckFeature

/**
 * 全部功能的集中注册表。
 */
object FeatureRegistry {
    @JvmStatic
    fun createDefaultManager(): FeatureManager {
        return FeatureManager()
            .register(SettingsFeature())
            .register(WechatApiFeature())
            // Visual hooks must precede optional toolbar/sidebar installation.
            // Keep the public API first so the chat-page subscription is available.
            .register(BackgroundBeautyFeature())
            .register(t.mma.hooks.items.backgroundbeauty.MeEntryVisibilityFeature())
            .register(ChatToolbarFeature())
            .register(HomeSidePanelFeature())
            .register(t.mma.hooks.items.homeextension.HomeExtensionFeature())
            .register(t.mma.hooks.items.stepmultiplier.StepMultiplierFeature())
            .register(AutoCacheFeature())
            .register(MonetModuleGeneratorFeature())
            .register(AntiSecureMessageFeature())
            .register(SendSecureMessageFeature())
            .register(UploadTransparentAvatarFeature())
            .register(SpecialMessageFeature())
            .register(t.mma.hooks.items.hiddenat.HiddenAtFeature())
            .register(t.mma.hooks.items.emojiat.EmojiAtFeature())
            .register(FloatingShortcutFeature())
            .register(FloatingChatHeaderFeature())
            .register(CustomBottomBarFeature())
            .register(DynamicBadgeFeature())

            .register(RoundAvatarFeature())
            .register(CustomFriendAvatarFeature())
            .register(RealNameTailFeature())
            .register(MemberTitleFeature())
            .register(t.mma.hooks.items.nicknamewidth.NicknameWidthFeature())
            .register(AutoRedPacketFeature())
            .register(WeChatTabletFeature())
            .register(AutoTransferFeature())
            .register(FakeWalletBalanceFeature())
            .register(AntiRecallFeature())
            .register(MultiRecallFeature())
            .register(DisableHotUpdateFeature())
            .register(ProfileIdFeature())
            .register(HideChatMenuFeature())
            .register(MessageMenuExtensionFeature())
            .register(QuickContactEditFeature())
            .register(QuickGroupChatLabelFeature())
            .register(QuickMomentsFeature())
            .register(QuickMarkReadFeature())
            .register(QuickTerminateFeature())
            .register(AutoReplyFeature())
            .register(AutoMessageForwardFeature())
            .register(ConversationGroupFeature())
            .register(t.mma.hooks.items.groupbar.GroupBarFeature())
            .register(ConversationAnalysisFeature())
            .register(MessageAffixFeature())
            .register(TypingReportBlockFeature())
            .register(PatBlockFeature())
            .register(AutoOriginalImageFeature())
            .register(AutoViewOriginalFeature())
            .register(RemoveForwardLimitFeature())
            .register(AtAllNotificationBlockFeature())
            .register(CallMediaLimitFeature())
            .register(CallRingtoneBlockFeature())
            .register(CustomNotificationFeature())
            .register(KeywordNotificationFeature())
            .register(TextSpeechFeature())
            .register(TextVoiceFeature())
            .register(ZombieCheckFeature())
            .register(WeChatKeepAliveFeature())
            .register(QuoteDeleteClearFeature())
            .register(SwipeQuoteFeature())
            .register(AudioTransformFeature())
            .register(FakeVoiceDurationFeature())
            .register(ChatTimeStyleFeature())
            .register(InputHintFeature())
            .register(MessageBubbleFeature())
            .register(MessageTextColorFeature())
            .register(MentionHighlightFeature())
            .register(t.mma.hooks.items.mentionextension.MentionNoticeFeature())
            .register(HomeTextColorFeature())
            .register(HideChatAvatarFeature())
            .register(MessageBlockFeature())
            .register(FakeLocationFeature())
            .register(GameEmojiFeature())
            .register(FakeMiniProgramBaseLibFeature())
            .register(SkipMiniProgramVideoAdsFeature())
            .register(SkipGlobalMiniProgramSplashAdsFeature())
            .register(QQMusicOrderFeature())
            .register(MomentsAutoLikeFeature())
            .register(MomentsAutoCommentFeature())
            .register(MomentsAutoForwardFeature())
            .register(MomentsAutoRefreshFeature())
            .register(MomentsKeywordBlockFeature())
            .register(MomentsContactFilterFeature())
            .register(MomentsBottomDetailFeature())
            .register(MomentsPostNotificationFeature())
            .register(OriginalMomentsUploadFeature())
            .register(MomentsUploadTailFeature())
            .register(SnsAntiRecallFeature())
            .register(MomentsFakeInteractionFeature())
            .register(GroupChatLabelFeature())
            .register(GroupLeaveMonitorFeature())
            .register(GroupRenameMonitorFeature())
            .register(RemoveMomentsAdsFeature())
            .register(EditMessageFeature())
            .register(EmojiSaveFeature())
            .register(VoicePreviewFeature())
            .register(t.mma.hooks.items.voicetotext.VoiceToTextFeature())
            .register(VoiceForwardFeature())
            .register(SelectedMessagesFeature())
            .register(MessageForwardFeature())
            .register(ScheduledTaskFeature())
            .register(FakeScanCameraFeature())
            .register(ProtobufPacketFeature())
            .register(StatusTextLimitFeature())
            .register(FinderMediaDownloadFeature())
            .register(HchatExtraFeature())
            .register(ScriptPluginFeature())
    }
}
