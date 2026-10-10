package t.mma.ui.miuix

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.DatePicker
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TimePicker
import android.widget.Toast
import android.widget.VideoView
import t.mma.BuildConfig
import t.mma.R
import t.mma.crash.CrashReportRuntime
import t.mma.crash.CrashReportSettings
import t.mma.crash.CrashReportSettingsProvider
import t.mma.hooks.api.ui.HchatAgentIconDrawable
import t.mma.hooks.items.specialmessage.SpecialMessageFeature
import t.mma.hooks.items.floatingshortcut.FloatingShortcutFeature
import t.mma.hooks.items.floatingshortcut.FloatingShortcutGlyph
import t.mma.hooks.items.floatingshortcut.FloatingShortcutGlyphDrawable
import t.mma.hooks.items.floatingshortcut.FloatingShortcutGlyphs
import t.mma.hooks.items.floatingshortcut.FloatingShortcutIconPickResult
import t.mma.hooks.items.floatingshortcut.FloatingShortcutIconPicker
import t.mma.hooks.items.floatingshortcut.FloatingShortcutIconStore
import t.mma.hooks.items.floatingshortcut.FloatingShortcutItem
import t.mma.hooks.items.floatingshortcut.FloatingShortcutRuntime
import t.mma.hooks.items.floatingshortcut.FloatingShortcutSettings
import t.mma.hooks.items.floatingchat.FloatingChatHeaderFeature
import t.mma.hooks.items.floatingchat.FloatingChatFooterFeature
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.media.WeChatFavoriteItem
import t.mma.hooks.api.message.WeChatMessageObserveApi
import t.mma.hooks.api.model.ContactLabelBean
import t.mma.hooks.api.model.WeChatContact
import t.mma.hooks.items.audiotransform.AudioTransformFeature
import t.mma.hooks.items.audiotransform.AudioTransformCancelledException
import t.mma.hooks.items.audiotransform.AudioTransformSegmenter
import t.mma.hooks.items.audiotransform.AudioTransformSettings
import t.mma.hooks.items.antirecall.AntiRecallFeature
import t.mma.hooks.items.antirecall.AntiRecallSettings
import t.mma.hooks.items.autoreply.AutoReplyFeature
import t.mma.hooks.items.autoreply.AutoReplyRule
import t.mma.hooks.items.autoreply.AutoReplyRuntime
import t.mma.hooks.items.autoreply.AutoReplySettings
import t.mma.hooks.items.autoreply.AutoReplyStep
import t.mma.hooks.items.autoreply.XiaozhiMcpStatus
import t.mma.hooks.items.autoreply.AutoReplyXiaozhiConfig
import t.mma.hooks.items.autoreply.AutoReplyZhiliaConfig
import t.mma.hooks.items.autoreply.XiaozhiAgentOption
import t.mma.hooks.items.autoreply.XiaozhiConsoleApi
import t.mma.hooks.items.autoreply.XiaozhiModelOption
import t.mma.hooks.items.autoreply.XiaozhiVoiceOption
import t.mma.hooks.items.autooriginal.AutoOriginalImageFeature
import t.mma.hooks.items.autooriginal.AutoOriginalImageSettings
import t.mma.hooks.items.autovieworiginal.AutoViewOriginalFeature
import t.mma.hooks.items.autovieworiginal.AutoViewOriginalSettings
import t.mma.hooks.items.atallnotify.AtAllNotificationBlockFeature
import t.mma.hooks.items.atallnotify.AtAllNotificationBlockSettings
import t.mma.hooks.items.backgroundbeauty.BackgroundBeautyFeature
import t.mma.hooks.items.backgroundbeauty.BackgroundBeautyPickResult
import t.mma.hooks.items.backgroundbeauty.BackgroundBeautyPicker
import t.mma.hooks.items.backgroundbeauty.BackgroundBeautySettings
import t.mma.hooks.items.backgroundbeauty.BackgroundBeautyStore
import t.mma.hooks.items.automessageforward.AutoMessageForwardFeature
import t.mma.hooks.items.automessageforward.AutoMessageForwardRule
import t.mma.hooks.items.automessageforward.AutoMessageForwardSettings
import t.mma.hooks.items.callmedialimit.CallMediaLimitFeature
import t.mma.hooks.items.callmedialimit.CallMediaLimitSettings
import t.mma.hooks.items.callmedialimit.CallRingtoneBlockFeature
import t.mma.hooks.items.chattime.ChatTimeStyleFeature
import t.mma.hooks.items.chattime.ChatTimeStyleSettings
import t.mma.hooks.items.conversationgroup.ConversationGroup
import t.mma.hooks.items.conversationgroup.ConversationGroupFeature
import t.mma.hooks.items.conversationanalysis.ConversationAnalysisFeature
import t.mma.hooks.items.conversationgroup.ConversationGroupPickerSupport
import t.mma.hooks.items.conversationgroup.ConversationGroupRuntime
import t.mma.hooks.items.conversationgroup.ConversationGroupStore
import t.mma.hooks.items.custombottombar.CustomBottomBarFeature
import t.mma.hooks.items.dynamicbadge.DynamicBadgeSettings
import t.mma.hooks.items.custombottombar.FloatingBottomBarSettings
import t.mma.hooks.items.customnotify.CustomNotificationFeature
import t.mma.hooks.items.customnotify.CustomNotificationRule
import t.mma.hooks.items.customnotify.CustomNotificationRuntime
import t.mma.hooks.items.customfriendavatar.CustomFriendAvatarFeature
import t.mma.hooks.items.customfriendavatar.CustomFriendAvatarPicker
import t.mma.hooks.items.customfriendavatar.CustomFriendAvatarSettings
import t.mma.hooks.items.customfriendavatar.CustomFriendAvatarStore
import t.mma.hooks.items.customnotify.CustomNotificationSettings
import t.mma.hooks.items.editmsg.EditMessageFeature
import t.mma.hooks.items.editmsg.EditMessageSettings
import t.mma.hooks.items.emojisave.EmojiSaveFeature
import t.mma.hooks.items.emojisave.EmojiSaveSettings
import t.mma.hooks.items.fakelocation.FakeLocationFeature
import t.mma.hooks.items.fakelocation.FakeLocationSettings
import t.mma.hooks.items.fakescancamera.FakeScanCameraFeature
import t.mma.hooks.items.fakescancamera.FakeScanCameraSettings
import t.mma.hooks.items.fakevoiceduration.FakeVoiceDurationFeature
import t.mma.hooks.items.fakevoiceduration.FakeVoiceDurationSettings
import t.mma.hooks.items.forwardlimit.RemoveForwardLimitFeature
import t.mma.hooks.items.forwardlimit.RemoveForwardLimitSettings
import t.mma.hooks.items.gameemoji.GameEmojiFeature
import t.mma.hooks.items.gameemoji.GameEmojiSettings
import t.mma.hooks.items.groupleave.GroupLeaveMonitorFeature
import t.mma.hooks.items.groupleave.GroupLeaveReplyTemplate
import t.mma.hooks.items.groupleave.GroupLeaveReplyTemplateBinding
import t.mma.hooks.items.groupleave.GroupLeaveMonitorSettings
import t.mma.hooks.items.grouplabel.GroupChatLabel
import t.mma.hooks.items.grouplabel.GroupChatLabelFeature
import t.mma.hooks.items.grouplabel.GroupChatLabelStore
import t.mma.hooks.items.grouplabel.QuickGroupChatLabelFeature
import t.mma.hooks.items.grouplabel.QuickGroupChatLabelSettings
import t.mma.hooks.items.groupnicknamecolor.GroupNicknameColorSettings
import t.mma.hooks.items.groupnicknamecolor.GroupNicknameColorStore
import t.mma.hooks.items.grouprename.GroupRenameMonitorFeature
import t.mma.hooks.items.grouprename.GroupRenameReplyTemplate
import t.mma.hooks.items.grouprename.GroupRenameMonitorSettings
import t.mma.hooks.items.grouprename.GroupRenameTemplateBinding
import t.mma.hooks.items.hideavatar.HideChatAvatarFeature
import t.mma.hooks.items.hideavatar.HideChatAvatarSettings
import t.mma.hooks.items.hidemenu.HideChatMenuFeature
import t.mma.hooks.items.hidemenu.HideChatMenuSettings
import t.mma.hooks.items.hotupdate.DisableHotUpdateFeature
import t.mma.hooks.items.hotupdate.DisableHotUpdateSettings
import t.mma.hooks.items.hometextcolor.HomeTextColorFeature
import t.mma.hooks.items.hometextcolor.HomeTextColorSettings
import t.mma.hooks.items.inputhint.InputHintFeature
import t.mma.hooks.items.inputhint.InputHintSettings
import t.mma.hooks.items.inputhint.InputHintStats
import t.mma.hooks.items.inputhint.OutgoingMessageStatsRepository
import t.mma.hooks.items.keepalive.WeChatKeepAliveFeature
import t.mma.hooks.items.keepalive.WeChatKeepAliveRuntime
import t.mma.hooks.items.keepalive.WeChatKeepAliveSettings
import t.mma.hooks.items.keywordnotify.KeywordNotificationFeature
import t.mma.hooks.items.keywordnotify.KeywordNotificationRuntime
import t.mma.hooks.items.keywordnotify.KeywordNotificationSettings
import t.mma.hooks.items.keywordnotify.KeywordRule
import t.mma.hooks.items.zombiecheck.ZombieCheckController
import t.mma.hooks.items.zombiecheck.ZombieCheckActionResult
import t.mma.hooks.items.zombiecheck.ZombieCheckFeature
import t.mma.hooks.items.zombiecheck.ZombieCheckResultType
import t.mma.hooks.items.zombiecheck.ZombieCheckSettings
import t.mma.hooks.items.membertitle.MemberTitleFeature
import t.mma.hooks.items.membertitle.MemberTitleSettings
import t.mma.hooks.items.membertitle.MemberTitleStore
import t.mma.hooks.items.messageaffix.MessageAffixFeature
import t.mma.hooks.items.messageaffix.MessageAffixSettings
import t.mma.hooks.items.messagebubble.MessageBubbleFeature
import t.mma.hooks.items.messagebubble.MessageBubblePickResult
import t.mma.hooks.items.messagebubble.MessageBubblePicker
import t.mma.hooks.items.messagebubble.MessageBubbleSettings
import t.mma.hooks.items.messagebubble.MessageBubbleSlot
import t.mma.hooks.items.messagebubble.MessageBubbleStore
import t.mma.hooks.items.messagebubble.MessageBubbleKind
import t.mma.hooks.items.messagetextcolor.MessageTextColorFeature
import t.mma.hooks.items.mentionhighlight.MentionHighlightFeature
import t.mma.hooks.items.messagetextcolor.MessageTextColorSettings
import t.mma.hooks.items.messageblock.MessageBlockBinding
import t.mma.hooks.items.messageblock.MessageBlockDefaultRule
import t.mma.hooks.items.messageblock.MessageBlockFeature
import t.mma.hooks.items.messageblock.MessageBlockSettings
import t.mma.hooks.items.messageblock.MessageBlockTemplate
import t.mma.hooks.items.messageforward.MessageForwardFeature
import t.mma.hooks.items.messageforward.MessageForwardSettings
import t.mma.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibFeature
import t.mma.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibSettings
import t.mma.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsFeature
import t.mma.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsSettings
import t.mma.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsFeature
import t.mma.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsSettings
import t.mma.hooks.items.musicorder.QQMusicOrderFeature
import t.mma.hooks.items.musicorder.QQMusicOrderSettings
import t.mma.hooks.items.moments.OriginalMomentsUploadFeature
import t.mma.hooks.items.moments.OriginalMomentsUploadSettings
import t.mma.hooks.items.moments.MomentsAutoCommentFeature
import t.mma.hooks.items.moments.MomentsAutoCommentSettings
import t.mma.hooks.items.moments.MomentsAutoLikeFeature
import t.mma.hooks.items.moments.MomentsAutoLikeSettings
import t.mma.hooks.items.moments.MomentsAutoForwardFeature
import t.mma.hooks.items.moments.MomentsAutoForwardSettings
import t.mma.hooks.items.moments.MomentsAutoRefreshFeature
import t.mma.hooks.items.moments.MomentsAutoRefreshSettings
import t.mma.hooks.items.moments.MomentsBottomDetailFeature
import t.mma.hooks.items.moments.MomentsBottomDetailSettings
import t.mma.hooks.items.moments.MomentsContactFilterFeature
import t.mma.hooks.items.moments.MomentsContactFilterSettings
import t.mma.hooks.items.moments.MomentsKeywordBlockFeature
import t.mma.hooks.items.moments.MomentsKeywordBlockSettings
import t.mma.hooks.items.moments.MomentsPostNotificationFeature
import t.mma.hooks.items.moments.MomentsPostNotificationSettings
import t.mma.hooks.items.moments.MomentsUploadTailFeature
import t.mma.hooks.items.moments.MomentsUploadTailSettings
import t.mma.hooks.items.moments.RemoveMomentsAdsFeature
import t.mma.hooks.items.moments.RemoveMomentsAdsSettings
import t.mma.hooks.items.moments.SnsAntiRecallFeature
import t.mma.hooks.items.moments.SnsAntiRecallSettings
import t.mma.hooks.items.momentsfake.MomentsFakeCommentSettingsProvider
import t.mma.hooks.items.momentsfake.MomentsFakeForwardRuntimeRegistry
import t.mma.hooks.items.momentsfake.MomentsFakeForwardSettingsProvider
import t.mma.hooks.items.momentsfake.MomentsFakeLikeSettingsProvider
import t.mma.hooks.items.momentsfake.MomentsFakeLikeCandidateRepository
import t.mma.hooks.items.momentsfake.MomentsFakeInteractionRuntimeRegistry
import t.mma.hooks.items.momentsfake.MomentsFakeInteractionSettings
import t.mma.hooks.items.multirecall.MultiRecallFeature
import t.mma.hooks.items.multirecall.MultiRecallSettings
import t.mma.hooks.items.payment.core.AutoRedPacketFeature
import t.mma.hooks.items.payment.core.PaymentTemplateTimeFormatter
import t.mma.hooks.items.payment.core.RedPacketReplyStep
import t.mma.hooks.items.payment.core.RedPacketRuleBinding
import t.mma.hooks.items.payment.core.RedPacketRuleConfig
import t.mma.hooks.items.payment.core.RedPacketRuleTemplate
import t.mma.hooks.items.payment.core.RedPacketSettings
import t.mma.hooks.items.payment.fakebalance.FakeWalletBalanceFeature
import t.mma.hooks.items.payment.fakebalance.FakeWalletBalanceSettings
import t.mma.hooks.items.payment.transfer.AutoTransferFeature
import t.mma.hooks.items.payment.transfer.AutoTransferSettings
import t.mma.hooks.items.payment.transfer.TransferReceiveAccountStore
import t.mma.hooks.items.payment.transfer.TransferRuleBinding
import t.mma.hooks.items.payment.transfer.TransferRuleConfig
import t.mma.hooks.items.payment.transfer.TransferRuleTemplate
import t.mma.hooks.items.patblock.PatBlockFeature
import t.mma.hooks.items.patblock.PatBlockSettings
import t.mma.hooks.items.profileid.ProfileIdFeature
import t.mma.hooks.items.profileid.ProfileIdSettings
import t.mma.hooks.items.profileid.ProfileAddTimeSettings
import t.mma.hooks.items.profileid.ProfileGroupInfoSettingsProvider
import t.mma.hooks.items.profileid.ProfileGroupJoinTimeSettings
import t.mma.hooks.items.profileid.ProfileGroupInviterSettings

import t.mma.hooks.items.protobuf.ProtobufPacketFeature
import t.mma.hooks.items.protobuf.ProtobufPacketRuntime
import t.mma.hooks.items.protobuf.ProtobufPacketSettings
import t.mma.hooks.items.quoteclear.QuoteDeleteClearFeature
import t.mma.hooks.items.quoteclear.QuoteDeleteClearSettings
import t.mma.hooks.items.quickcontactedit.QuickContactEditFeature
import t.mma.hooks.items.quickcontactedit.QuickContactEditSettings
import t.mma.hooks.items.quickmoments.QuickMomentsFeature
import t.mma.hooks.items.quickmoments.QuickMomentsSettings
import t.mma.hooks.items.quickread.QuickMarkReadFeature
import t.mma.hooks.items.quickread.QuickMarkReadSettings
import t.mma.hooks.items.quickterminate.QuickTerminateFeature
import t.mma.hooks.items.quickterminate.QuickTerminateSettings
import t.mma.hooks.items.realtail.RealNameTailFeature
import t.mma.hooks.items.realtail.RealNameTailSettings
import t.mma.hooks.items.realtail.RealNameTailStore
import t.mma.hooks.items.roundavatar.RoundAvatarFeature
import t.mma.hooks.items.roundavatar.RoundAvatarSettings
import t.mma.hooks.items.script.ScriptPluginFeature
import t.mma.hooks.items.script.ScriptPluginManager
import t.mma.hooks.items.script.ScriptPluginRuntime
import t.mma.hooks.items.script.ScriptPluginSettings
import t.mma.hooks.items.script.market.PluginMarketHistoryVersion
import t.mma.hooks.items.script.market.PluginMarketInstaller
import t.mma.hooks.items.script.market.PluginMarketException
import t.mma.hooks.items.script.market.PluginMarketFile
import t.mma.hooks.items.script.market.PluginMarketComment
import t.mma.hooks.items.script.market.PluginMarketNotification
import t.mma.hooks.items.script.market.PluginMarketPlugin
import t.mma.hooks.items.script.market.PluginMarketRepository
import t.mma.hooks.items.script.market.PluginMarketReviewStatus
import t.mma.hooks.items.script.market.PluginMarketUserIdentity
import t.mma.hooks.items.script.agent.ScriptPluginAgentClient
import t.mma.hooks.items.script.agent.ScriptPluginAgentAttachment
import t.mma.hooks.items.script.agent.ScriptPluginAgentChatMessage
import t.mma.hooks.items.script.agent.ScriptPluginAgentConfig
import t.mma.hooks.items.script.agent.ScriptPluginAgentCancellation
import t.mma.hooks.items.script.agent.ScriptPluginAgentContext
import t.mma.hooks.items.script.agent.ScriptPluginAgentDiff
import t.mma.hooks.items.script.agent.ScriptPluginAgentDraft
import t.mma.hooks.items.script.agent.ScriptPluginAgentEventIds
import t.mma.hooks.items.script.agent.ScriptPluginAgentLocalFiles
import t.mma.hooks.items.script.agent.ScriptPluginAgentMcpServer
import t.mma.hooks.items.script.agent.ScriptPluginAgentRequest
import t.mma.hooks.items.script.agent.ScriptPluginAgentResumeState
import t.mma.hooks.items.script.agent.ScriptPluginAgentSession
import t.mma.hooks.items.script.agent.ScriptPluginAgentSessionStore
import t.mma.hooks.items.script.agent.ScriptPluginAgentSettings
import t.mma.hooks.items.script.agent.ScriptPluginAgentProfile
import t.mma.hooks.items.script.agent.ScriptPluginAgentQuotedMessage
import t.mma.hooks.items.script.agent.ScriptPluginAgentSpeech
import t.mma.hooks.items.script.agent.ScriptPluginAgentTextMerge
import t.mma.hooks.items.script.agent.ScriptPluginAgentToolEvent
import t.mma.hooks.items.script.agent.ScriptPluginAgentToolResultStore
import t.mma.hooks.items.script.agent.ScriptPluginAgentValidator
import t.mma.hooks.items.script.agent.ScriptPluginAgentWorkspaceChange
import t.mma.hooks.items.script.agent.ScriptPluginAgentWorkspaceToolConfirmation
import t.mma.hooks.items.script.agent.ScriptPluginAgentWorkspaceWriteDecision
import t.mma.hooks.items.script.agent.ScriptPluginAgentWorkspaceTools
import t.mma.hooks.items.script.agent.ScriptPluginAgentWriter
import t.mma.hooks.items.scheduledtask.ScheduledTaskContentItem
import t.mma.hooks.items.scheduledtask.ScheduledTaskFeature
import t.mma.hooks.items.scheduledtask.ScheduledTaskItem
import t.mma.hooks.items.scheduledtask.ScheduledTaskRuntimeCoordinator
import t.mma.hooks.items.scheduledtask.ScheduledTaskSettings
import t.mma.hooks.items.selectedmessages.SelectedMessageSnapshot
import t.mma.hooks.items.selectedmessages.SelectedMessagesFeature
import t.mma.hooks.items.selectedmessages.SelectedMessagesRuntimeCoordinator
import t.mma.hooks.items.selectedmessages.SelectedMessagesSettings
import t.mma.hooks.items.settings.PluginAgentEntryProvider
import t.mma.hooks.items.settings.SettingsEntrySettings
import t.mma.hooks.items.settings.SettingsFeature
import t.mma.hooks.items.shortvideo.FinderMediaDownloadFeature
import t.mma.hooks.items.shortvideo.FinderMediaDownloadSettings
import t.mma.hooks.items.statuslimit.StatusTextLimitFeature
import t.mma.hooks.items.statuslimit.StatusTextLimitSettings
import t.mma.hooks.items.swipequote.SwipeQuoteFeature
import t.mma.hooks.items.swipequote.SwipeQuoteSettings
import t.mma.hooks.items.tablet.WeChatTabletFeature
import t.mma.hooks.items.tablet.WeChatTabletSettings
import t.mma.hooks.items.textspeech.TextSpeechFeature
import t.mma.hooks.items.textspeech.TextSpeechEngineCatalog
import t.mma.hooks.items.textspeech.TextSpeechSettings
import t.mma.hooks.items.textspeech.TextSpeechVoiceCatalog
import t.mma.hooks.items.textvoice.TextVoiceFeature
import t.mma.hooks.items.textvoice.TextVoiceSettings
import t.mma.hooks.items.typingreport.TypingReportBlockFeature
import t.mma.hooks.items.typingreport.TypingReportBlockSettings
import t.mma.hooks.items.voiceforward.VoiceForwardFeature
import t.mma.hooks.items.voiceforward.VoiceForwardSettings
import t.mma.hooks.items.hchatextra.GroupMemberHistorySettingsProvider
import t.mma.hooks.items.hchatextra.MessageDetailsSettingsProvider
import t.mma.hooks.items.hchatextra.RedPacketDetailsSettingsProvider
import t.mma.hooks.items.hchatextra.SkipWebRiskSettingsProvider
import t.mma.hooks.items.hchatextra.HchatExtraSettings
import t.mma.hooks.items.voicepreview.VoicePreviewFeature
import t.mma.hooks.items.voicepreview.VoicePreviewSettings
import t.mma.media.AudioTransformBridge
import t.mma.preferences.HchatStorage
import t.mma.preferences.TermsGate
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.UIRegistry
import t.mma.utils.KavaReflector
import t.mma.utils.KeywordReplacementRule
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.extra.WindowDropdown
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.extra.WindowDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.lang.ref.WeakReference
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Calendar
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.random.Random

internal object ScheduledTaskFilePickerBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var onPicked: ((List<String>) -> Unit)? = null
    private var currentActivity: Activity? = null

    @Synchronized
    fun launch(activity: Activity, taskType: Int, onResult: (List<String>) -> Unit) {
        onPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, SCHEDULED_TASK_FILE_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivityForResult(
                Intent.createChooser(fallback, scheduledTaskPickerTitle(taskType)),
                SCHEDULED_TASK_FILE_REQUEST_CODE
            )
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != SCHEDULED_TASK_FILE_REQUEST_CODE) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val activity = currentActivity ?: return
                    val paths = scheduledTaskPickedUris(data)
                        .mapNotNull { uri ->
                            scheduledTaskTakeReadPermission(activity, data, uri)
                            copyScheduledTaskFileToCache(activity, uri)
                        }
                        .distinct()
                    if (paths.isNotEmpty()) onPicked?.invoke(paths)
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }

    private fun scheduledTaskPickedUris(data: Intent): List<Uri> {
        val result = ArrayList<Uri>()
        data.data?.let { result.add(it) }
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                try {
                    clip.getItemAt(index)?.uri?.let { result.add(it) }
                } catch (_: Throwable) {
                }
            }
        }
        @Suppress("DEPRECATION")
        (data.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { result.add(it) }
        return result.distinct()
    }

    private fun scheduledTaskTakeReadPermission(activity: Activity, data: Intent, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }

    private fun copyScheduledTaskFileToCache(context: Context, uri: Uri): String? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            val path = uri.path.orEmpty()
            if (path.isNotBlank() && File(path).isFile) return path
        }
        return try {
            val name = safeScheduledTaskFileName(queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "scheduled_task_file")
            val dir = File(context.filesDir, "Hchat/scheduled_task").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath.takeIf { target.isFile && target.length() > 0L }
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeScheduledTaskFileName(value: String): String {
        return value.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "scheduled_task_file" }
    }
}

internal object AudioTransformDocumentBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var onInputPicked: ((String, String) -> Unit)? = null
    private var onOutputPicked: ((Uri, String) -> Unit)? = null
    private var currentActivity: Activity? = null

    @Synchronized
    fun launchInput(activity: Activity, onResult: (String, String) -> Unit) {
        onInputPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, AUDIO_TRANSFORM_INPUT_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivityForResult(
                Intent.createChooser(fallback, "选择音频文件"),
                AUDIO_TRANSFORM_INPUT_REQUEST_CODE
            )
        }
    }

    @Synchronized
    fun launchOutput(
        activity: Activity,
        suggestedFileName: String,
        mimeType: String,
        onResult: (Uri, String) -> Unit
    ) {
        onOutputPicked = onResult
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, suggestedFileName)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, AUDIO_TRANSFORM_OUTPUT_REQUEST_CODE)
        } catch (_: Throwable) {
            Toast.makeText(activity, "当前系统不支持创建输出文件", Toast.LENGTH_SHORT).show()
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != AUDIO_TRANSFORM_INPUT_REQUEST_CODE &&
                        requestCode != AUDIO_TRANSFORM_OUTPUT_REQUEST_CODE
                    ) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val activity = currentActivity ?: return
                    val uri = data.data ?: return
                    if (requestCode == AUDIO_TRANSFORM_INPUT_REQUEST_CODE) {
                        takeReadPermission(activity, data, uri)
                        val copied = copyInputFileToCache(activity, uri) ?: return
                        onInputPicked?.invoke(copied.first, copied.second)
                    } else {
                        val displayName = queryDisplayName(activity, uri)
                            ?: uri.lastPathSegment
                            ?.substringAfterLast('/')
                            ?.substringAfterLast(':')
                            .orEmpty()
                        onOutputPicked?.invoke(uri, displayName.ifBlank { "output" })
                    }
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }

    private fun takeReadPermission(activity: Activity, data: Intent, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Throwable) {
        }
    }

    private fun copyInputFileToCache(context: Context, uri: Uri): Pair<String, String>? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            val path = uri.path.orEmpty()
            if (path.isNotBlank() && File(path).isFile) {
                val name = File(path).name.ifBlank { "audio_input" }
                return path to name
            }
        }
        return try {
            val name = safeAudioTransformFileName(queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "audio_input")
            val dir = File(context.filesDir, "Hchat/audio_transform/input").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (!target.isFile || target.length() <= 0L) return null
            target.absolutePath to name
        } catch (_: Throwable) {
            null
        }
    }

    private fun safeAudioTransformFileName(value: String): String {
        return value.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "audio_input" }
    }
}

internal object ScriptPluginDocumentBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var exportCallback: ((Uri) -> Unit)? = null
    private var importCallback: ((Uri) -> Unit)? = null
    private var exportOwner: WeakReference<Activity>? = null
    private var importOwner: WeakReference<Activity>? = null

    @Synchronized
    fun launchExport(activity: Activity, fileName: String, onPicked: (Uri) -> Unit) {
        if (exportCallback != null) {
            Toast.makeText(activity, "已有导出文件选择正在进行", Toast.LENGTH_SHORT).show()
            return
        }
        exportCallback = onPicked
        exportOwner = WeakReference(activity)
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, SCRIPT_PLUGIN_EXPORT_REQUEST_CODE)
        } catch (_: Throwable) {
            exportCallback = null
            exportOwner = null
            Toast.makeText(activity, "当前系统不支持创建 ZIP 文件", Toast.LENGTH_SHORT).show()
        }
    }

    @Synchronized
    fun launchImport(activity: Activity, onPicked: (Uri) -> Unit) {
        if (importCallback != null) {
            Toast.makeText(activity, "已有导入文件选择正在进行", Toast.LENGTH_SHORT).show()
            return
        }
        importCallback = onPicked
        importOwner = WeakReference(activity)
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, SCRIPT_PLUGIN_IMPORT_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                activity.startActivityForResult(
                    Intent.createChooser(fallback, "选择插件 ZIP 文件"),
                    SCRIPT_PLUGIN_IMPORT_REQUEST_CODE
                )
            } catch (_: Throwable) {
                importCallback = null
                importOwner = null
                Toast.makeText(activity, "当前系统不支持选择 ZIP 文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != SCRIPT_PLUGIN_EXPORT_REQUEST_CODE &&
                        requestCode != SCRIPT_PLUGIN_IMPORT_REQUEST_CODE
                    ) {
                        return
                    }
                    val owner = param.thisObject as? Activity ?: return
                    val callback = synchronized(this@ScriptPluginDocumentBridge) {
                        if (requestCode == SCRIPT_PLUGIN_EXPORT_REQUEST_CODE) {
                            val matches = exportOwner?.get() === owner
                            exportCallback.takeIf { matches }.also {
                                exportCallback = null
                                exportOwner = null
                            }
                        } else {
                            val matches = importOwner?.get() === owner
                            importCallback.takeIf { matches }.also {
                                importCallback = null
                                importOwner = null
                            }
                        }
                    } ?: return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val uri = data.data ?: return
                    if (requestCode == SCRIPT_PLUGIN_IMPORT_REQUEST_CODE && uri.scheme == "content") {
                        runCatching {
                            val flags = data.flags and
                                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            if ((flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                                owner.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                        }
                    }
                    callback(uri)
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }
}

internal object ConfigImportExportBridge {
    private val hookedClasses = HashSet<Class<*>>()
    private var onImported: (() -> Unit)? = null
    private var currentActivity: Activity? = null

    @Synchronized
    fun launchExport(activity: Activity) {
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val fileName = "Hchat_config_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, CONFIG_EXPORT_REQUEST_CODE)
        } catch (_: Throwable) {
            Toast.makeText(activity, "当前系统不支持创建配置文件", Toast.LENGTH_SHORT).show()
        }
    }

    @Synchronized
    fun launchImport(activity: Activity, onDone: () -> Unit) {
        onImported = onDone
        currentActivity = activity
        hookActivityResult(activity.javaClass)
        hookActivityResult(Activity::class.java)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.preferSystemDocumentsUi(activity)
        try {
            activity.startActivityForResult(intent, CONFIG_IMPORT_REQUEST_CODE)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivityForResult(Intent.createChooser(fallback, "选择 Hchat 配置文件"), CONFIG_IMPORT_REQUEST_CODE)
        }
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != CONFIG_EXPORT_REQUEST_CODE && requestCode != CONFIG_IMPORT_REQUEST_CODE) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val data = param.args.getOrNull(2) as? Intent ?: return
                    val uri = data.data ?: return
                    val activity = currentActivity ?: return
                    if (requestCode == CONFIG_EXPORT_REQUEST_CODE) {
                        val count = HchatConfigBackup.exportToUri(activity, uri)
                        if (count > 0) {
                            Toast.makeText(activity, "已导出 $count 项配置", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, "导出失败", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val count = HchatConfigBackup.importFromUri(activity, uri)
                        if (count > 0) {
                            onImported?.invoke()
                            Toast.makeText(activity, "已导入 $count 项配置，当前设置页已刷新", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(activity, "导入失败或文件无配置", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
            hookedClasses.add(clazz)
        } catch (_: Throwable) {
        }
    }
}

internal object HchatConfigBackup {
    private const val VERSION = 1
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "string_set"
    private const val KEY_TYPE = "type"
    private const val KEY_VALUE = "value"

    private val prefsNames = listOf(
        UI_PREFS_NAME,
        CallMediaLimitSettings.PREFS_NAME,
        FakeLocationSettings.PREFS_NAME,
        FinderMediaDownloadSettings.PREFS_NAME,
        HchatExtraSettings.PREFS_NAME,
        StatusTextLimitSettings.PREFS_NAME,
        EditMessageSettings.PREFS_NAME,
        DisableHotUpdateSettings.PREFS_NAME,
        RemoveMomentsAdsSettings.PREFS_NAME,
        MomentsAutoCommentSettings.PREFS_NAME,
        MomentsAutoLikeSettings.PREFS_NAME,
        MomentsAutoForwardSettings.PREFS_NAME,
        MomentsAutoRefreshSettings.PREFS_NAME,
        MomentsContactFilterSettings.PREFS_NAME,
        MomentsBottomDetailSettings.PREFS_NAME,
        MomentsPostNotificationSettings.PREFS_NAME,
        MomentsFakeInteractionSettings.PREFS_NAME,
        ProfileIdSettings.PREFS_NAME,
        SettingsEntrySettings.PREFS_NAME,
        FloatingShortcutSettings.PREFS_NAME,
        SpecialMessageFeature.PREFS,
        QuickMarkReadSettings.PREFS_NAME,
        CustomNotificationSettings.PREFS_NAME,
        ConversationGroupStore.PREFS_NAME,
        AutoReplySettings.PREFS_NAME,
        MessageAffixSettings.PREFS_NAME,
        InputHintSettings.PREFS_NAME,
        AutoOriginalImageSettings.PREFS_NAME,
        AutoViewOriginalSettings.PREFS_NAME,
        AtAllNotificationBlockSettings.PREFS_NAME,
        KeywordNotificationSettings.PREFS_NAME,
        TextSpeechSettings.PREFS_NAME,
        TextVoiceSettings.PREFS_NAME,
        ZombieCheckSettings.PREFS_NAME,
        WeChatKeepAliveSettings.PREFS_NAME,
        QuoteDeleteClearSettings.PREFS_NAME,
        ChatTimeStyleSettings.PREFS_NAME,
        EmojiSaveSettings.PREFS_NAME,
        SwipeQuoteSettings.PREFS_NAME,
        AudioTransformSettings.PREFS_NAME,
        FakeVoiceDurationSettings.PREFS_NAME,
        MessageBubbleSettings.PREFS_NAME,
        MessageTextColorSettings.PREFS_NAME,
        HideChatAvatarSettings.PREFS_NAME,
        HideChatMenuSettings.PREFS_NAME,
        t.mma.hooks.items.messagemenuextension.MessageMenuExtensionSettings.PREFS_NAME,
        QuickContactEditSettings.PREFS_NAME,
        RoundAvatarSettings.PREFS_NAME,
        CustomFriendAvatarSettings.PREFS_NAME,
        MessageBlockSettings.PREFS_NAME,
        GroupLeaveMonitorSettings.PREFS_NAME,
        GroupRenameMonitorSettings.PREFS_NAME,
        MultiRecallSettings.PREFS_NAME,
        VoiceForwardSettings.PREFS_NAME,
        FakeScanCameraSettings.PREFS_NAME,
        RedPacketSettings.PREFS_NAME,
        AntiRecallSettings.PREFS_NAME,
        ProtobufPacketSettings.PREFS_NAME,
        ScriptPluginSettings.PREFS_NAME,
        WeChatTabletSettings.PREFS_NAME,
        AutoTransferSettings.PREFS_NAME,
        FakeWalletBalanceSettings.PREFS_NAME,
        MemberTitleSettings.PREFS_NAME,
        RealNameTailSettings.PREFS_NAME,
        GroupNicknameColorSettings.PREFS_NAME
    ).distinct()

    fun exportToUri(context: Context, uri: Uri): Int {
        return runCatching {
            val prefsJson = JSONObject()
            var itemCount = 0
            prefsNames.forEach { name ->
                val sp = HchatStorage.preferences(context, name)
                val entries = JSONObject()
                sp.all.forEach { (key, value) ->
                    encodePreferenceValue(value)?.let { encoded ->
                        entries.put(key, encoded)
                        itemCount++
                    }
                }
                if (entries.length() > 0) {
                    prefsJson.put(name, entries)
                }
            }
            val root = JSONObject().apply {
                put("format", "HchatConfigBackup")
                put("version", VERSION)
                put("appVersion", BuildConfig.VERSION_NAME)
                put("exportedAt", System.currentTimeMillis())
                put("prefs", prefsJson)
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return 0
            itemCount
        }.getOrDefault(0)
    }

    fun importFromUri(context: Context, uri: Uri): Int {
        return runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.reader(Charsets.UTF_8).readText()
            }.orEmpty()
            if (text.isBlank()) return 0
            val root = JSONObject(text)
            if (root.optString("format") != "HchatConfigBackup") return 0
            val prefsJson = root.optJSONObject("prefs") ?: return 0
            var itemCount = 0
            prefsNames.forEach { name ->
                val entries = prefsJson.optJSONObject(name) ?: return@forEach
                val editor = HchatStorage.preferences(context, name).edit().clear()
                val keys = entries.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val encoded = entries.optJSONObject(key) ?: continue
                    if (decodePreferenceValue(editor, key, encoded)) {
                        itemCount++
                    }
                }
                editor.commit()
            }
            itemCount
        }.getOrDefault(0)
    }

    private fun encodePreferenceValue(value: Any?): JSONObject? {
        val obj = JSONObject()
        when (value) {
            is Boolean -> {
                obj.put(KEY_TYPE, TYPE_BOOLEAN)
                obj.put(KEY_VALUE, value)
            }
            is Int -> {
                obj.put(KEY_TYPE, TYPE_INT)
                obj.put(KEY_VALUE, value)
            }
            is Long -> {
                obj.put(KEY_TYPE, TYPE_LONG)
                obj.put(KEY_VALUE, value)
            }
            is Float -> {
                obj.put(KEY_TYPE, TYPE_FLOAT)
                obj.put(KEY_VALUE, value.toDouble())
            }
            is String -> {
                obj.put(KEY_TYPE, TYPE_STRING)
                obj.put(KEY_VALUE, value)
            }
            is Set<*> -> {
                obj.put(KEY_TYPE, TYPE_STRING_SET)
                obj.put(KEY_VALUE, JSONArray().apply {
                    value.filterIsInstance<String>().forEach { put(it) }
                })
            }
            else -> return null
        }
        return obj
    }

    private fun decodePreferenceValue(
        editor: SharedPreferences.Editor,
        key: String,
        encoded: JSONObject
    ): Boolean {
        return try {
            when (encoded.optString(KEY_TYPE)) {
                TYPE_BOOLEAN -> editor.putBoolean(key, encoded.optBoolean(KEY_VALUE))
                TYPE_INT -> editor.putInt(key, encoded.optInt(KEY_VALUE))
                TYPE_LONG -> editor.putLong(key, encoded.optLong(KEY_VALUE))
                TYPE_FLOAT -> editor.putFloat(key, encoded.optDouble(KEY_VALUE).toFloat())
                TYPE_STRING -> editor.putString(key, encoded.optString(KEY_VALUE, ""))
                TYPE_STRING_SET -> editor.putStringSet(key, decodeStringSet(encoded.optJSONArray(KEY_VALUE)))
                else -> return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun decodeStringSet(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val result = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            result.add(array.optString(i, ""))
        }
        return result
    }
}

@Composable
internal fun Modifier.responsiveTap(
    onClick: () -> Unit,
    onPressedChange: (Boolean) -> Unit = {}
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            currentOnPressedChange(true)
            val up = waitForUpOrCancellation()
            currentOnPressedChange(false)
            if (up != null) {
                currentOnClick()
            }
        }
    }
}

@Composable
internal fun rememberPressFeedbackColor(pressed: Boolean): Color {
    var feedbackVisible by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            feedbackVisible = true
        } else {
            delay(PRESS_RELEASE_DELAY_MS)
            feedbackVisible = false
        }
    }
    val target = if (feedbackVisible) {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.075f)
    } else {
        Color.Transparent
    }
    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (pressed) 90 else 210),
        label = "PressFeedback"
    ).value
}

@Composable
internal fun HchatMiuixTheme(context: Context, content: @Composable () -> Unit) {
    val colors = if (isDarkMode(context)) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    MiuixTheme(colors = colors, content = content)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun MainSettingsPage(
    context: Context,
    providers: List<FeatureSettingsProvider>,
    listState: LazyListState,
    selectedTab: MainTab,
    configVersion: Int,
    onSelectedTab: (MainTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProvider: (FeatureSettingsProvider) -> Unit,
    onOpenGroup: (FeatureGroupEntry) -> Unit,
    onOpenScriptPluginReadme: (ScriptPluginRuntime.ScriptPlugin) -> Unit,
    onOpenScriptPluginAgent: () -> Unit,
    onOpenScriptPluginMarket: () -> Unit,
    onOpenScriptPluginManager: () -> Unit,
    onOpenRuntimeLog: () -> Unit,
    onConfigImported: () -> Unit
) {
    val uiPrefs = remember(configVersion) { HchatStorage.preferences(context, UI_PREFS_NAME) }
    val glassSupported = remember { isLiquidGlassSupported() }
    var floatingNav by remember(configVersion) { mutableStateOf(uiPrefs.getBoolean(KEY_FLOATING_NAV, true)) }
    var glassNav by remember(configVersion) { mutableStateOf(glassSupported && uiPrefs.getBoolean(KEY_GLASS_NAV, true)) }
    val entertainmentListState = rememberLazyListState()
    val pluginListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val practicalProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_PRACTICAL }
    val entertainmentProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_ENTERTAINMENT }
    val enhanceProviders = providers.filter {
        it.category() == FeatureSettingsProvider.CATEGORY_ENHANCE &&
            it.featureId() != ScriptPluginFeature.ID
    }
    val practicalGroups = practicalFeatureGroups(practicalProviders, enhanceProviders)
    val entertainmentGroups = entertainmentFeatureGroups(entertainmentProviders)
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "Hchat",
        largeTitle = "Hchat",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            MainNavigationBar(
                selectedTab = selectedTab,
                floating = floatingNav,
                glass = glassSupported && glassNav,
                backdrop = it,
                onSelected = onSelectedTab
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .mainTabSwipe(selectedTab, onSelectedTab),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enter = slideInHorizontally(
                    animationSpec = tween(240),
                    initialOffsetX = { width -> if (forward) width / 3 else -width / 3 }
                ) + fadeIn(animationSpec = tween(160))
                val exit = slideOutHorizontally(
                    animationSpec = tween(220),
                    targetOffsetX = { width -> if (forward) -width / 5 else width / 5 }
                ) + fadeOut(animationSpec = tween(140))
                enter togetherWith exit
            },
            label = "HchatMainTabTransition"
        ) { tab ->
            val activeListState = when (tab) {
                MainTab.PRACTICAL -> listState
                MainTab.ENTERTAINMENT -> entertainmentListState
                MainTab.PLUGIN -> pluginListState
                MainTab.SETTINGS -> settingsListState
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                state = activeListState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 78.dp
                )
            ) {
                item {
                    SettingsSearchEntry(onClick = onOpenSearch)
                }
                when (tab) {
                    MainTab.PRACTICAL -> {
                        featureGroupMenu(
                            title = "实用功能",
                            groups = practicalGroups,
                            onOpenGroup = onOpenGroup
                        )
                        if (practicalProviders.isEmpty() && enhanceProviders.isEmpty()) {
                            item { SmallTitle(text = "实用功能") }
                            item { FeatureListCard(emptyList(), "暂无实用功能", onOpenProvider) }
                        }
                    }
                    MainTab.ENTERTAINMENT -> {
                        featureGroupMenu(
                            title = "娱乐功能",
                            groups = entertainmentGroups,
                            onOpenGroup = onOpenGroup
                        )
                        if (entertainmentProviders.isEmpty()) {
                            item { SmallTitle(text = "娱乐功能") }
                            item { FeatureListCard(emptyList(), "暂无娱乐功能", onOpenProvider) }
                        }
                    }
                    MainTab.PLUGIN -> {
                        item { SmallTitle(text = "脚本插件") }
                        item {
                            ScriptPluginSettingsMiuixContent.ScriptPluginSettingsContent(
                                context,
                                onOpenScriptPluginReadme,
                                onOpenScriptPluginMarket,
                                onOpenScriptPluginAgent,
                                onOpenScriptPluginManager
                            )
                        }
                    }
                    MainTab.SETTINGS -> {
                        item { SmallTitle(text = "设置") }
                        item {
                            SettingsCard {
                                SwitchRow(
                                    checked = floatingNav,
                                    title = "悬浮底栏",
                                    summary = "使用悬浮样式的底部导航栏",
                                    onCheckedChange = {
                                        floatingNav = it
                                        uiPrefs.edit().putBoolean(KEY_FLOATING_NAV, it).apply()
                                    }
                                )
                                InsetDivider()
                                SwitchRow(
                                    checked = glassSupported && glassNav,
                                    title = "液态玻璃",
                                    summary = if (glassSupported) {
                                        "启用悬浮底栏的液态玻璃效果"
                                    } else {
                                        "Android 13 以下不支持液态玻璃效果"
                                    },
                                    enabled = glassSupported,
                                    onCheckedChange = {
                                        if (!glassSupported) return@SwitchRow
                                        glassNav = it
                                        uiPrefs.edit().putBoolean(KEY_GLASS_NAV, it).apply()
                                    }
                                )
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "关于") }
                        item { AboutCard() }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "诊断") }
                        item {
                            SettingsCard {
                                ActionRow("运行日志", "查看功能是否生效及未生效原因") {
                                    onOpenRuntimeLog()
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "配置") }
                        item {
                            ConfigBackupCard(
                                context = context,
                                onImported = onConfigImported
                            )
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "群组") }
                        item { GroupLinksCard(context) }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "致谢") }
                        item { ThanksCard(context) }
                        item {
                            Text(
                                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                                text = "Hchat",
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun List<FeatureSettingsProvider>.filterByIds(vararg ids: String): List<FeatureSettingsProvider> {
    val order = ids.toList()
    return filter { it.featureId() in order }
        .sortedBy { order.indexOf(it.featureId()) }
}

internal fun newFeatureProviders(
    practicalProviders: List<FeatureSettingsProvider>
): List<FeatureSettingsProvider> = practicalProviders.filter { it.isNewFeature() }

internal fun enabledFeatureProviders(
    providers: List<FeatureSettingsProvider>
): List<FeatureSettingsProvider> {
    val enabledIds = UIRegistry.get().getEnabledProviders().mapTo(HashSet()) { it.featureId() }
    return providers.filter { it.featureId() in enabledIds }
}

internal fun practicalFeatureGroups(
    practicalProviders: List<FeatureSettingsProvider>,
    enhanceProviders: List<FeatureSettingsProvider>
): List<FeatureGroupEntry> {
    val newProviders = newFeatureProviders(practicalProviders + enhanceProviders)
    val enabledProviders = enabledFeatureProviders(practicalProviders + enhanceProviders)
    return listOf(
        FeatureGroupEntry(
            title = "新功能",
            providers = newProviders
        ),
        FeatureGroupEntry(
            title = "已开启功能",
            providers = enabledProviders
        ),
        FeatureGroupEntry(
            title = "聊天",
            providers = practicalProviders.filterByIds(
                SpecialMessageFeature.ID,
                AntiRecallFeature.ID,
                MultiRecallFeature.ID,
                AutoReplyFeature.ID,
                MessageAffixFeature.ID,
                TypingReportBlockFeature.ID,
                PatBlockFeature.ID,
                AutoOriginalImageFeature.ID,
                AutoViewOriginalFeature.ID,
                RemoveForwardLimitFeature.ID,
                MessageBlockFeature.ID,
                QuickMarkReadFeature.ID,
                MessageDetailsSettingsProvider.FEATURE_ID,
                QuoteDeleteClearFeature.ID,
                SwipeQuoteFeature.ID,
                EmojiSaveFeature.ID,
                CallMediaLimitFeature.ID,
                CallRingtoneBlockFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "语音",
            providers = practicalProviders.filterByIds(
                AudioTransformFeature.ID,
                TextVoiceFeature.ID,
                FakeVoiceDurationFeature.ID,
                VoicePreviewFeature.ID,
                VoiceForwardFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "红包转账",
            providers = practicalProviders.filterByIds(
                AutoRedPacketFeature.ID,
                AutoTransferFeature.ID,
                FakeWalletBalanceFeature.ID,
                RedPacketDetailsSettingsProvider.FEATURE_ID
            )
        ),
        FeatureGroupEntry(title = "增强", providers = enhanceProviders),
        FeatureGroupEntry(
            title = "群组",
            providers = practicalProviders.filterByIds(
                GroupChatLabelFeature.ID,
                ConversationAnalysisFeature.ID,
                AtAllNotificationBlockFeature.ID,
                GroupLeaveMonitorFeature.ID,
                GroupRenameMonitorFeature.ID,
                ProfileGroupInfoSettingsProvider.ID
            )
        ),
        FeatureGroupEntry(
            title = "朋友圈",
            providers = practicalProviders.filterByIds(
                MomentsAutoLikeFeature.ID,
                MomentsAutoCommentFeature.ID,
                MomentsAutoForwardFeature.ID,
                MomentsAutoRefreshFeature.ID,
                MomentsContactFilterFeature.ID,
                MomentsKeywordBlockFeature.ID,
                MomentsBottomDetailFeature.ID,
                MomentsPostNotificationFeature.ID,
                OriginalMomentsUploadFeature.ID,
                MomentsUploadTailFeature.ID,
                SnsAntiRecallFeature.ID,
                MomentsFakeLikeSettingsProvider.ID,
                MomentsFakeCommentSettingsProvider.ID,
                MomentsFakeForwardSettingsProvider.ID,
                RemoveMomentsAdsFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "美化",
            providers = practicalProviders.filterByIds(
                MessageBubbleFeature.ID,
                MessageTextColorFeature.ID,
                MentionHighlightFeature.ID,
                HomeTextColorFeature.ID,
                ChatTimeStyleFeature.ID,
                InputHintFeature.ID,
                HideChatAvatarFeature.ID,
                RoundAvatarFeature.ID,
                CustomFriendAvatarFeature.ID,
                CustomBottomBarFeature.ID,
                DynamicBadgeSettings.ID,
                FloatingBottomBarSettings.FEATURE_ID,
                FloatingChatHeaderFeature.ID,
                FloatingChatFooterFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "界面",
            providers = practicalProviders.filterByIds(
                SettingsFeature.ID,
                PluginAgentEntryProvider.ID,
                FloatingShortcutFeature.ID,
                ProfileIdFeature.ID,
                GroupMemberHistorySettingsProvider.FEATURE_ID,
                QuickContactEditFeature.ID,
                QuickGroupChatLabelFeature.ID,
                QuickMomentsFeature.ID,
                QuickTerminateFeature.ID,
                HideChatMenuFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "杂项",
            providers = practicalProviders.filterByIds(
                WeChatKeepAliveFeature.ID,
                WeChatTabletFeature.ID,
                DisableHotUpdateFeature.ID,
                FakeMiniProgramBaseLibFeature.ID,
                SkipMiniProgramVideoAdsFeature.ID,
                SkipGlobalMiniProgramSplashAdsFeature.ID,
                FakeLocationFeature.ID,
                GameEmojiFeature.ID,
                FakeScanCameraFeature.ID,
                FinderMediaDownloadFeature.ID,
                SkipWebRiskSettingsProvider.FEATURE_ID
            )
        )
    ).filter {
        it.providers.isNotEmpty() ||
            it.title == "新功能" ||
            it.title.startsWith("已开启功能")
    }
}
internal fun entertainmentFeatureGroups(
    entertainmentProviders: List<FeatureSettingsProvider>
): List<FeatureGroupEntry> {
    return listOf(
        FeatureGroupEntry(
            title = "点歌",
            providers = entertainmentProviders.filterByIds(QQMusicOrderFeature.ID)
        ),
        FeatureGroupEntry(
            title = "群聊",
            providers = entertainmentProviders.filterByIds(
                RealNameTailFeature.ID,
                GroupNicknameColorSettings.FEATURE_ID,
                MemberTitleFeature.ID
            )
        ),
        FeatureGroupEntry(
            title = "状态",
            providers = entertainmentProviders.filterByIds(StatusTextLimitFeature.ID)
        ),
        FeatureGroupEntry(
            title = "聊天",
            providers = entertainmentProviders.filterByIds(EditMessageFeature.ID)
        ),
        FeatureGroupEntry(
            title = "抓包",
            providers = entertainmentProviders.filterByIds(ProtobufPacketFeature.ID)
        ),
        FeatureGroupEntry(
            title = "调试",
            providers = entertainmentProviders.filterByIds(CrashReportSettingsProvider.ID)
        )
    ).filter { it.providers.isNotEmpty() }
}

@Composable
internal fun SettingsSearchEntry(onClick: () -> Unit) {
    SearchBarSurface(
        query = "",
        placeholder = "搜索功能和插件",
        readOnly = true,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        onClick = onClick,
        onQueryChange = {}
    )
}

@Composable
internal fun SettingsSearchPage(
    context: Context,
    providers: List<FeatureSettingsProvider>,
    onBack: () -> Unit,
    onOpenProvider: (FeatureSettingsProvider, FeatureGroupEntry?) -> Unit,
    onOpenScriptPluginReadme: (ScriptPluginRuntime.ScriptPlugin) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var pluginListVersion by remember { mutableStateOf(0) }
    val uiPrefs = remember { HchatStorage.preferences(context, UI_PREFS_NAME) }
    var searchHistory by remember { mutableStateOf(readSearchHistory(uiPrefs)) }
    val practicalProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_PRACTICAL }
    val entertainmentProviders = providers.filter { it.category() == FeatureSettingsProvider.CATEGORY_ENTERTAINMENT }
    val enhanceProviders = providers.filter {
        it.category() == FeatureSettingsProvider.CATEGORY_ENHANCE &&
            it.featureId() != ScriptPluginFeature.ID
    }
    val groups = remember(practicalProviders, enhanceProviders, entertainmentProviders) {
        practicalFeatureGroups(practicalProviders, enhanceProviders) +
            entertainmentFeatureGroups(entertainmentProviders)
    }
    val featureResults = remember(query, groups, providers) {
        filterFeatureSearchResults(query, groups, providers)
    }
    val plugins = remember(pluginListVersion) {
        ScriptPluginManager.listForDisplay(context).map { it.plugin }
    }
    val pluginResults = remember(query, plugins) { filterPluginSearchResults(query, plugins) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    fun recordSearch() {
        val next = rememberSearchKeyword(uiPrefs, query)
        searchHistory = next
    }
    DisposableEffect(context) {
        val subscription = ScriptPluginRuntime.subscribePluginCatalog(context) {
            Handler(Looper.getMainLooper()).post { pluginListVersion++ }
        }
        onDispose { subscription.unsubscribe() }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 74.dp,
                bottom = navigationButtonBottomInset() + NAVIGATION_BUTTON_EXTRA_GAP + 24.dp
            )
        ) {
            if (query.isBlank()) {
                item { SearchHistorySection(searchHistory, onSelect = { query = it }, onClear = {
                    saveSearchHistory(uiPrefs, emptyList())
                    searchHistory = emptyList()
                }) }
            } else {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "功能(${featureResults.size})") }
                item {
                    FeatureSearchResultCard(
                        results = featureResults,
                        onOpenProvider = { provider, group ->
                            recordSearch()
                            onOpenProvider(provider, group)
                        }
                    )
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "插件(${pluginResults.size})") }
                item {
                    PluginSearchResultCard(
                        plugins = pluginResults,
                        onOpenReadme = { plugin ->
                            recordSearch()
                            onOpenScriptPluginReadme(plugin)
                        }
                    )
                }
            }
        }
        SearchPageTopBar(
            query = query,
            focusRequester = focusRequester,
            onQueryChange = { query = it },
            onCancel = onBack
        )
    }
}

@Composable
internal fun SearchPageTopBar(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MiuixTheme.colorScheme.background)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                start = 12.dp,
                end = 12.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SearchBarSurface(
            query = query,
            placeholder = "搜索功能和插件",
            modifier = Modifier.weight(1f),
            focusRequester = focusRequester,
            onQueryChange = onQueryChange
        )
        Text(
            text = "取消",
            color = MiuixTheme.colorScheme.primary,
            fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onCancel() }
                .padding(horizontal = 4.dp, vertical = 10.dp)
        )
    }
}

@Composable
internal fun SearchBarSurface(
    query: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: (() -> Unit)? = null,
    onQueryChange: (String) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            imageVector = NavIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary)
        )
        if (readOnly) {
            Text(
                text = query.ifEmpty { placeholder },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
            )
        } else {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .padding(start = 10.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
internal fun SearchHistorySection(
    history: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    if (history.isEmpty()) {
        EmptyText("暂无搜索记录")
        return
    }
    SmallTitle(text = "搜索记录")
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        history.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { keyword ->
                    ContactFilterChip(
                        text = keyword,
                        selected = false,
                        onClick = { onSelect(keyword) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
    Text(
        text = "清除搜索记录",
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth()
            .padding(top = 18.dp)
            .clickable { onClear() },
        textAlign = TextAlign.Center
    )
}

@Composable
internal fun FeatureSearchResultCard(
    results: List<FeatureSearchResult>,
    onOpenProvider: (FeatureSettingsProvider, FeatureGroupEntry?) -> Unit
) {
    SettingsCard {
        if (results.isEmpty()) {
            EmptyText("没有匹配功能")
        } else {
            results.forEachIndexed { index, result ->
                FeatureSearchResultRow(
                    result = result,
                    onClick = { onOpenProvider(result.provider, result.group) }
                )
                if (index < results.lastIndex) {
                    InsetDivider()
                }
            }
        }
    }
}

@Composable
internal fun FeatureSearchResultRow(result: FeatureSearchResult, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = result.provider.title(), color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(
                text = result.summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
    }
}

@Composable
internal fun PluginSearchResultCard(
    plugins: List<ScriptPluginRuntime.ScriptPlugin>,
    onOpenReadme: (ScriptPluginRuntime.ScriptPlugin) -> Unit
) {
    SettingsCard {
        if (plugins.isEmpty()) {
            EmptyText("没有匹配插件")
        } else {
            plugins.forEachIndexed { index, plugin ->
                PluginSearchResultRow(
                    plugin = plugin,
                    onClick = { onOpenReadme(plugin) }
                )
                if (index < plugins.lastIndex) {
                    InsetDivider()
                }
            }
        }
    }
}

@Composable
internal fun PluginSearchResultRow(plugin: ScriptPluginRuntime.ScriptPlugin, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    val title = buildString {
        append(plugin.displayName ?: plugin.name.ifBlank { plugin.dir.name })
        if (plugin.version.isNotBlank()) {
            append("(")
            append(plugin.version)
            append(")")
        }
    }
    val summary = buildString {
        append(plugin.dir.name)
        if (plugin.author.isNotBlank()) {
            append("\n作者: ")
            append(plugin.author)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ClickHintTag()
            }
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
    }
}

internal fun LazyListScope.featureGroupMenu(
    title: String,
    groups: List<FeatureGroupEntry>,
    onOpenGroup: (FeatureGroupEntry) -> Unit
) {
    if (groups.isEmpty()) return
    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = title) }
    item {
        SettingsCard {
            groups.forEachIndexed { index, group ->
                FeatureGroupRow(
                    group = group,
                    onClick = { onOpenGroup(group) }
                )
                if (index < groups.lastIndex) {
                    InsetDivider()
                }
            }
        }
    }
}

@Composable
internal fun FeatureGroupSettingsPage(
    group: FeatureGroupEntry,
    listState: LazyListState,
    onBack: () -> Unit,
    onOpenProvider: (FeatureSettingsProvider) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = group.title,
        largeTitle = group.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "功能") }
            item { FeatureListCard(group.providers, "暂无功能", onOpenProvider) }
        }
    }
}

internal enum class MainTab {
    PRACTICAL,
    ENTERTAINMENT,
    PLUGIN,
    SETTINGS
}

internal fun MainTab.offset(delta: Int): MainTab {
    val tabs = MainTab.entries
    val index = (ordinal + delta).coerceIn(0, tabs.lastIndex)
    return tabs[index]
}

internal fun Modifier.mainTabSwipe(
    selectedTab: MainTab,
    onSelectedTab: (MainTab) -> Unit
): Modifier {
    return pointerInput(selectedTab) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalX = 0f
            var totalY = 0f
            var horizontal = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - change.previousPosition.x
                val dy = change.position.y - change.previousPosition.y
                totalX += dx
                totalY += dy
                if (!horizontal && abs(totalX) > 24f && abs(totalX) > abs(totalY) * 1.35f) {
                    horizontal = true
                }
                if (horizontal) {
                    change.consume()
                }
                if (!change.pressed) break
            }
            if (horizontal && abs(totalX) > 86f && abs(totalX) > abs(totalY) * 1.2f) {
                val target = if (totalX < 0f) selectedTab.offset(1) else selectedTab.offset(-1)
                if (target != selectedTab) {
                    onSelectedTab(target)
                }
            }
        }
    }
}

internal data class MainNavItem(
    val tab: MainTab,
    val label: String,
    val icon: ImageVector
)

internal data class FeatureGroupEntry(
    val title: String,
    val providers: List<FeatureSettingsProvider>
)

internal data class FeatureSearchResult(
    val provider: FeatureSettingsProvider,
    val group: FeatureGroupEntry?,
    val summary: String
)

internal data class TemplateVariable(
    val token: String,
    val label: String
)

internal sealed class DetailPage {
    object Search : DetailPage()
    object RuntimeLog : DetailPage()
    data class FeatureGroup(val group: FeatureGroupEntry) : DetailPage()
    data class Feature(
        val provider: FeatureSettingsProvider,
        val sourceGroup: FeatureGroupEntry? = null,
        val returnToSearch: Boolean = false
    ) : DetailPage()
    data class ScriptPluginAgent(val parentFeature: Feature? = null) : DetailPage()
    data class ScriptPluginMarket(val parentFeature: Feature? = null) : DetailPage()
    data class ScriptPluginManager(val parentFeature: Feature? = null) : DetailPage()
}

internal fun detailPageDepth(page: DetailPage?): Int = when (page) {
    null -> 0
    is DetailPage.Search -> 1
    is DetailPage.RuntimeLog -> 1
    is DetailPage.FeatureGroup -> 1
    is DetailPage.Feature -> 2
    is DetailPage.ScriptPluginAgent -> if (page.parentFeature == null) 1 else 3
    is DetailPage.ScriptPluginMarket -> if (page.parentFeature == null) 1 else 3
    is DetailPage.ScriptPluginManager -> if (page.parentFeature == null) 1 else 3
}

internal fun previousDetailPage(page: DetailPage?): DetailPage? = when (page) {
    null -> null
    is DetailPage.Search -> null
    is DetailPage.RuntimeLog -> null
    is DetailPage.FeatureGroup -> null
    is DetailPage.Feature -> when {
        page.returnToSearch -> DetailPage.Search
        page.sourceGroup != null -> DetailPage.FeatureGroup(page.sourceGroup)
        else -> null
    }
    is DetailPage.ScriptPluginAgent -> page.parentFeature
    is DetailPage.ScriptPluginMarket -> page.parentFeature
    is DetailPage.ScriptPluginManager -> page.parentFeature
}

internal fun filterFeatureSearchResults(
    query: String,
    groups: List<FeatureGroupEntry>,
    allProviders: List<FeatureSettingsProvider>
): List<FeatureSearchResult> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return emptyList()
    val grouped = LinkedHashSet<String>()
    val results = ArrayList<FeatureSearchResult>()
    groups.forEach { group ->
        group.providers.forEach { provider ->
            val matchedTerms = matchedFeatureSubTerms(keyword, provider)
            if (matchesFeature(keyword, provider, group.title) || matchedTerms.isNotEmpty()) {
                grouped.add(provider.featureId())
                results += FeatureSearchResult(
                    provider = provider,
                    group = group,
                    summary = featureSearchSummary(group.title, provider, matchedTerms)
                )
            }
        }
    }
    allProviders.forEach { provider ->
        val matchedTerms = matchedFeatureSubTerms(keyword, provider)
        if (provider.featureId() !in grouped && (matchesFeature(keyword, provider, null) || matchedTerms.isNotEmpty())) {
            results += FeatureSearchResult(
                provider = provider,
                group = null,
                summary = featureSearchSummary(null, provider, matchedTerms)
            )
        }
    }
    return results
}

internal fun featureSearchSummary(
    groupTitle: String?,
    provider: FeatureSettingsProvider,
    matchedTerms: List<String>
): String {
    return buildString {
        if (!groupTitle.isNullOrBlank()) {
            append(groupTitle)
            append(" / ")
        }
        append(provider.subtitle())
        if (matchedTerms.isNotEmpty()) {
            append("\n匹配: ")
            append(matchedTerms.joinToString("、"))
        }
    }
}

internal fun matchesFeature(
    query: String,
    provider: FeatureSettingsProvider,
    groupTitle: String?
): Boolean {
    return containsSearchText(provider.title(), query) ||
        containsSearchText(provider.subtitle(), query) ||
        containsSearchText(provider.featureId(), query) ||
        containsSearchText(groupTitle.orEmpty(), query)
}

internal fun matchedFeatureSubTerms(query: String, provider: FeatureSettingsProvider): List<String> {
    return featureSubSearchTerms(provider.featureId())
        .filter { containsSearchText(it, query) }
        .distinct()
        .take(5)
}

internal fun featureSubSearchTerms(featureId: String): List<String> {
    return when (featureId) {
        AntiRecallFeature.ID -> listOf(
            "动态撤回", "渐变颜色", "描边旋转周期", "描边宽度", "私聊防撤回", "群聊防撤回", "公众号防撤回", "撤回提示", "自定义撤回提示", "发送时间", "撤回时间", "时间格式", "语音防撤回", "图片防撤回", "视频防撤回"
        )
        MultiRecallFeature.ID -> listOf("多选撤回", "批量撤回", "多选消息", "分享菜单", "撤回自己消息")
        MessageForwardFeature.ID -> listOf("转发", "转发[H]", "朋友圈", "好友", "分享", "群发助手", "好友标签", "朋友圈转发", "个人主页朋友圈")
        SelectedMessagesFeature.ID -> listOf("群发助手", "群发助手[H]", "定时转发[H]", "多选消息", "定时转发", "模块群发", "微信原生群发助手", "群发助手间隔延迟", "群发间隔延迟")
        AutoReplyFeature.ID -> listOf(
            "自动回复规则", "任意消息", "关键词", "正则", "艾特我", "@我", "艾特全体", "@所有人", "回复步骤", "延迟回复",
            "回复冷却时间", "冷却秒数", "最大回复次数",
            "小智", "小智AI", "小智回复", "小智语音", "小智语音回复", "小智文字回复", "唤醒词", "语音输入", "TTS",
            "上下文", "固定ID", "清空上下文", "智谱", "模型", "Agent", "角色", "语音角色", "好友自动化", "新好友打招呼"
        )
        AutoMessageForwardFeature.ID -> listOf(
            "消息自动转发", "自动转发消息", "转发规则", "监听会话", "转发到", "好友", "群聊", "公众号",
            "文字", "图片", "语音", "视频", "表情", "文件", "链接", "音乐", "小程序", "关键词", "包含关键词", "排除关键词", "替换关键词", "替换规则"
        )
        ConversationGroupFeature.ID -> listOf(
            "会话分组", "聊天分组", "嵌套分组", "归拢会话", "隐藏会话", "移动会话", "分组管理"
        )
        MessageAffixFeature.ID -> listOf(
            "发送文本格式", "消息格式", "消息前后缀", "消息前缀", "消息后缀", "固定文字", "发送时间", "换行"
        )
        InputHintFeature.ID -> listOf(
            "输入框提示", "聊天输入框", "默认提示", "占位文字", "发送统计", "消息数量", "文字字数"
        )
        MessageBlockFeature.ID -> listOf(
            "屏蔽消息", "屏蔽规则", "默认规则", "私聊规则", "群聊规则", "公众号规则", "任意消息", "关键词", "正则",
            "艾特我", "@我", "艾特全体", "@所有人", "允许入库", "消息入库", "模板", "名单", "批量绑定", "标签好友", "未知类型"
        )
        QuickMarkReadFeature.ID -> listOf("快捷已读", "一键已读", "长按已读", "通知已读", "会话已读")
        AutoOriginalImageFeature.ID -> listOf("自动勾选原图", "原图发送", "聊天图片原图", "发送图片")
        AutoViewOriginalFeature.ID -> listOf(
            "自动查看原图", "查看原图", "自动查看原视频", "查看原视频", "聊天图片", "聊天视频", "原画质"
        )
        RemoveForwardLimitFeature.ID -> listOf("移除转发限制", "转发上限", "9个会话", "多选转发", "原生转发")
        TypingReportBlockFeature.ID -> listOf("拦截正在输入上报", "屏蔽正在输入", "正在输入", "输入状态", "输入上报")
        PatBlockFeature.ID -> listOf("禁止拍一拍", "屏蔽拍一拍", "双击头像", "头像双击")
        MessageDetailsSettingsProvider.FEATURE_ID -> listOf(
            "消息显示时间", "消息显示详情", "消息详情", "消息时间", "消息类型", "时间格式", "时间颜色", "显示位置", "点击显详情"
        )
        GroupMemberHistorySettingsProvider.FEATURE_ID -> listOf("历史发言记录", "群成员历史发言", "群成员资料")
        AtAllNotificationBlockFeature.ID -> listOf("屏蔽艾特所有人", "屏蔽@所有人", "艾特全体", "通知拦截")
        QuoteDeleteClearFeature.ID -> listOf("删除键清引用", "清除引用", "引用消息", "删除引用")
        EmojiSaveFeature.ID -> listOf("保存表情", "表情保存", "动画表情", "长按菜单", "本地文件")
        SwipeQuoteFeature.ID -> listOf("滑动手势", "左滑引用", "右滑复读", "长按复读", "菜单复读", "+1", "复读语音", "语音时长", "滑动距离")
        CallMediaLimitFeature.ID -> listOf("通话媒体限制", "语音通话", "视频通话", "播放语音", "查看视频", "打开拍摄")
        CallRingtoneBlockFeature.ID -> listOf("屏蔽通话铃声", "呼入铃声", "呼出铃声", "来电铃声", "等待铃声", "语音通话", "视频通话")
        AudioTransformFeature.ID -> listOf(
            "音频转换",
            "音频转码",
            "语音转码",
            "silk",
            "mp3",
            "m4a",
            "语音发送",
            "切割语音",
            "分段语音",
            "裁剪语音",
            "自动分割",
            "开始时间",
            "结束时间",
            "试听",
            "播放音频"
        )
        TextVoiceFeature.ID -> listOf("文本转语音", "文字转语音", "在线语音", "发送文字转语音", "转语音播放", "#tts", "英文语音", "TTS引擎", "TTS角色", "语速", "播放速度")
        FakeVoiceDurationFeature.ID -> listOf("伪造语音时长", "语音显示时长", "假语音时长", "语音秒数")
        VoicePreviewFeature.ID -> listOf("语音消息预览", "预览语音", "播放语音", "语音时长", "长按语音")
        FakeMiniProgramBaseLibFeature.ID -> listOf("兼容低版本小程序", "伪装小程序宿主版本", "阻止小程序升级跳转", "微信版本较低", "官方升级页")
        SkipMiniProgramVideoAdsFeature.ID -> listOf("跳过小程序视频广告", "小程序广告", "视频广告")
        SkipGlobalMiniProgramSplashAdsFeature.ID -> listOf("跳过全局小程序开屏广告", "小程序开屏广告", "启动广告", "全局广告")
        FakeLocationFeature.ID -> listOf("虚拟定位", "模拟定位", "小程序定位", "微信地图", "地图选点", "搜索地点", "经纬度", "GPS")
        GameEmojiFeature.ID -> listOf("指定骰子猜拳", "自定义骰子", "骰子点数", "猜拳结果", "剪刀", "石头", "布", "发送时选择")
        VoiceForwardFeature.ID -> listOf("语音转发", "语音保存", "合并语音", "合并MP3", "转发弹窗", "好友列表", "群聊列表", "语音列表", "批量转发")
        ScheduledTaskFeature.ID -> listOf("定时任务", "定时发送", "群发任务", "计划发送", "循环发送", "每日发送", "每周发送", "朋友圈", "图文", "视文")
        AutoRedPacketFeature.ID -> listOf(
            "自动抢红包", "红包关键词", "红包规则", "规则模板", "回复步骤", "私聊红包回复", "群红包回复", "延迟抢红包", "仅群聊", "仅私聊", "排除名单", "领取后回复"
        )
        AutoTransferFeature.ID -> listOf(
            "自动收款", "转账关键词", "转账规则", "延迟到账", "自动领取转账", "自动收转账", "收款回复",
            "收款模板", "适用聊天", "批量套用", "随机延迟", "禁收时段", "收款通知", "收款播报", "媒体回复"
        )
        FakeWalletBalanceFeature.ID -> listOf("伪造零钱", "钱包余额", "零钱余额", "零钱通", "经营账户", "金额显示")
        RedPacketDetailsSettingsProvider.FEATURE_ID -> listOf("红包显示详情", "红包详情", "红包金额", "领取时间")
        CustomNotificationFeature.ID -> listOf(
            "自定义通知", "通知规则", "通知模板", "私聊通知", "群聊通知", "公众号通知", "艾特我", "@我", "艾特全体", "@所有人", "免打扰通知", "微信消息免打扰"
        )
        KeywordNotificationFeature.ID -> listOf(
            "关键词通知", "关键词列表", "关键词模板", "通知模板", "艾特我", "@我", "艾特全体", "@所有人", "允许入库", "消息入库"
        )
        TextSpeechFeature.ID -> listOf(
            "文字转语音播报", "文字转语音", "语音播报", "TTS引擎", "语音引擎", "播报角色", "TTS角色", "声音角色", "音色", "Voice", "自动播放语音", "播放语音消息", "原语音", "音键控制", "音量键控制", "播报发送人", "免打扰", "开始时间", "结束时间", "设置允许名单", "允许名单"
        )
        ZombieCheckFeature.ID -> listOf("僵尸粉检测", "好友关系检测", "单向好友", "删除好友", "异常好友", "好友标签")
        QQMusicOrderFeature.ID -> listOf("QQ点歌", "点歌", "音乐卡片", "点歌发送卡片", "点歌发送语音", "触发词", "歌手", "封面", "AppID", "允许点歌聊天")
        GroupChatLabelFeature.ID -> listOf("群聊标签", "群组分类", "群聊分类", "标签群聊", "批量选择群聊")
        QuickGroupChatLabelFeature.ID -> listOf("快捷设置群聊标签", "群聊标签", "会话长按菜单", "聊天分组", "群聊分类")
        GroupLeaveMonitorFeature.ID -> listOf("进退群监控", "进群邀请", "退群提醒", "监听群", "批量保护", "新群保护", "群成员变化")
        GroupRenameMonitorFeature.ID -> listOf("改名监控", "群昵称", "群内昵称", "改名提醒", "改名卡片", "改名模板", "批量套用", "改名系统消息")
        MomentsAutoLikeFeature.ID -> listOf("朋友圈自动点赞", "自动点赞", "点赞自己的朋友圈", "点赞白名单", "点赞黑名单", "同一人每天点赞数量", "每日点赞上限", "随机延迟", "发布时间限制", "关键词过滤", "链接朋友圈", "音乐朋友圈", "卡片朋友圈", "未知类型朋友圈")
        MomentsAutoCommentFeature.ID -> listOf("朋友圈自动评论", "自动评论", "评论内容", "时间变量", "评论时间", "时间格式", "评论自己的朋友圈", "评论白名单", "评论黑名单", "同一人每天评论数量", "每日评论上限", "评论延迟", "随机延迟", "发布时间限制", "关键词过滤", "评论日志", "链接朋友圈", "音乐朋友圈", "卡片朋友圈", "未知类型朋友圈")
        MomentsAutoForwardFeature.ID -> listOf("朋友圈自动转发", "静默转发", "指定好友", "每日上限", "包含关键词", "排除关键词", "替换关键词", "替换规则", "文案替换", "文案模板", "链接朋友圈", "音乐朋友圈", "卡片朋友圈", "未知类型朋友圈")
        MomentsAutoRefreshFeature.ID -> listOf("朋友圈自动刷新", "后台刷新", "原生刷新", "刷新间隔", "刷新时段")
        MomentsContactFilterFeature.ID -> listOf("朋友圈过滤", "好友过滤", "过滤好友", "只看好友", "朋友圈白名单", "朋友圈黑名单", "好友标签", "聊天分组")
        MomentsKeywordBlockFeature.ID -> listOf("朋友圈关键词屏蔽", "朋友圈屏蔽", "关键词过滤", "隐藏朋友圈")
        MomentsBottomDetailFeature.ID -> listOf("朋友圈底部详情", "朋友圈时间", "底部时间", "自定义时间", "时间格式", "可见范围")
        MomentsPostNotificationFeature.ID -> listOf("朋友圈发布通知", "朋友圈提醒", "好友发布朋友圈", "朋友圈Toast", "通知模板")
        OriginalMomentsUploadFeature.ID -> listOf("朋友圈原图", "原图上传", "原视频", "原视频上传", "不压缩", "朋友圈图片", "朋友圈视频")
        MomentsUploadTailFeature.ID -> listOf("朋友圈上传尾巴", "SDK ID", "SDK名称", "SDK来源", "第三方来源")
        SnsAntiRecallFeature.ID -> listOf(
            "朋友圈防撤回", "朋友圈评论防撤回", "评论防撤回", "已删除标签", "自定义撤回提示", "评论撤回提示", "提示开关", "三天可见", "个人主页朋友圈", "发现页朋友圈", "强制旧版个人主页朋友圈"
        )
        MomentsFakeLikeSettingsProvider.ID -> listOf("朋友圈伪集赞", "伪集赞[H]", "本地点赞", "使用非好友", "群成员", "随机排序", "自动勾选好友", "排除名单", "随机选择好友", "凭空生成点赞", "虚拟点赞人", "伪造点赞数量", "隐藏长按菜单", "自定义文本")
        MomentsFakeCommentSettingsProvider.ID -> listOf("朋友圈伪评论", "伪评论[H]", "使用非好友", "群成员", "多选好友", "全选好友", "随机评论", "添加新评论", "评论池", "评论时间", "评论顺序", "隐藏长按菜单", "自定义文本", "清除伪评论")
        MomentsFakeForwardSettingsProvider.ID -> listOf("朋友圈伪转发", "伪转发[H]", "本地朋友圈", "日期", "时间", "重复转发", "隐藏长按菜单", "自定义文本", "清空伪转发", "调试日志")
        RemoveMomentsAdsFeature.ID -> listOf("去除朋友圈广告", "朋友圈广告", "广告屏蔽")
        MessageBubbleFeature.ID -> listOf("消息气泡", "聊天气泡", "自定义气泡", "左侧气泡", "右侧气泡", "红包气泡", "转账气泡", "系统消息气泡", "浅色模式", "深色模式", "NinePatch", "九宫格")
        MessageTextColorFeature.ID -> listOf("消息文本颜色", "文字颜色", "聊天气泡文字", "取色器", "浅色模式", "深色模式")
        MentionHighlightFeature.ID -> listOf("艾特高亮", "艾特", "@我", "@所有人", "点击资料页", "群聊艾特", "高亮颜色")
        HomeTextColorFeature.ID -> listOf("首页文字颜色", "标题颜色", "副标题颜色", "渐变文字", "微信首页", "通讯录", "发现", "我")
        SpecialMessageFeature.ID -> listOf("特殊消息", "安全消息", "文本", "链接", "表情", "生效名单")
        ChatTimeStyleFeature.ID -> listOf("会话时间样式", "聊天时间", "微信时间", "自定义时间", "隐藏时间", "时间格式")
        HideChatAvatarFeature.ID -> listOf("隐藏头像", "隐藏自己头像", "隐藏对方头像", "聊天头像", "群聊头像", "私聊头像")
        CustomBottomBarFeature.ID -> listOf(
            "自定义底栏", "底部导航", "微信首页", "修改图标", "修改标题", "隐藏标题", "隐藏底栏"
        )
        FloatingBottomBarSettings.FEATURE_ID -> listOf(
            "悬浮底栏", "底部导航", "微信首页", "液态玻璃", "模糊半径", "隐藏标签", "显示角标", "振动反馈", "振动强度"
        )
        RoundAvatarFeature.ID -> listOf("圆角头像", "圆形头像", "头像圆角", "头像弧度", "朋友圈头像", "公众号头像", "通知头像")
        SettingsFeature.ID -> listOf(
            "设置入口",
            "微信设置入口",
            "加号菜单入口",
            "长按加号",
            "启动器入口",
            "入口开关"
        )
        PluginAgentEntryProvider.ID -> listOf("插件 Agent 入口", "插件Agent入口", "右上角加号", "AI 插件入口")
        FloatingShortcutFeature.ID -> listOf(
            "悬浮快捷菜单", "悬浮按钮", "快捷入口", "插件 Agent", "展开 Agent", "收起 Agent",
            "按钮外观", "主按钮大小", "主按钮颜色", "副按钮大小", "副按钮颜色", "按钮渐变",
            "菜单名称颜色", "菜单名称大小", "菜单文字",
            "微信页面", "扫一扫", "朋友圈", "视频号", "收藏", "钱包"
        )
        ProfileIdFeature.ID -> listOf("资料页显示", "显示微信 ID", "显示ID", "好友ID", "群聊ID", "wxid", "复制ID", "添加时间", "好友添加时间", "yyyy-MM-dd HH:mm:ss", "渐变颜色")
        QuickContactEditFeature.ID -> listOf("快捷设置备注和标签", "好友备注", "好友标签", "新建标签", "会话长按菜单", "朋友圈头像")
        QuickMomentsFeature.ID -> listOf("快捷查看朋友圈", "朋友圈", "会话长按菜单", "个人朋友圈", "聊天分组", "自己", "企业微信联系人")
        QuickTerminateFeature.ID -> listOf("快捷终止", "结束微信", "终止微信", "加号菜单", "关闭微信")
        t.mma.hooks.items.messagemenuextension.MessageMenuExtensionSettings.ID -> listOf("消息菜单扩展", "长按消息", "菜单图标", "6个", "5排", "诊断")
        HideChatMenuFeature.ID -> listOf("隐藏聊天菜单", "长按菜单", "隐藏菜单项", "提醒", "搜一搜", "收藏")
        WeChatKeepAliveFeature.ID -> listOf("微信保活", "常驻通知", "WakeLock", "Root保活", "保活服务")
        WeChatTabletFeature.ID -> listOf("平板模式", "Pad模式", "设备登录", "多设备登录", "登录为平板")
        DisableHotUpdateFeature.ID -> listOf("屏蔽热更新", "禁用热更新", "Tinker", "补丁", "热补丁")
        FakeScanCameraFeature.ID -> listOf("伪造扫码相机", "扫码相机", "扫一扫", "相册扫码", "扫码入口")
        SkipWebRiskSettingsProvider.FEATURE_ID -> listOf("跳过网页风险", "网页风险", "WebView", "风险拦截")
        EditMessageFeature.ID -> listOf("修改聊天记录", "编辑消息", "改消息", "消息XML", "发送XML")
        ProtobufPacketFeature.ID -> listOf("Protobuf抓包", "Protobuf发包", "抓包", "发包", "pb", "网络包", "重放")
        CrashReportSettingsProvider.ID -> listOf("捕获异常日志", "异常日志", "闪退日志", "Java异常", "Native异常", "ANR", "调试")
        StatusTextLimitFeature.ID -> listOf("解除状态词长度限制", "状态词", "微信状态", "状态长度", "字数限制")
        RealNameTailFeature.ID -> listOf("实名尾巴", "群聊尾巴", "实名后缀", "尾巴格式", "群成员实名")
        GroupNicknameColorSettings.FEATURE_ID -> listOf("群昵称自定义颜色", "群昵称颜色", "群内昵称颜色", "昵称渐变", "昵称粗细", "颜色取色器")
        MemberTitleFeature.ID -> listOf("群成员头衔", "头衔颜色", "自定义头衔", "头衔模板", "群昵称头衔", "颜色取色器")
        ScriptPluginFeature.ID -> listOf("脚本插件", "插件列表", "插件 Agent", "AI 生成插件", "插件 README", "插件配置", "启用插件", "禁用插件")
        else -> emptyList()
    }
}
