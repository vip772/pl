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
import t.mma.hooks.items.conversationgroup.ConversationGroupPickerSupport
import t.mma.hooks.items.conversationgroup.ConversationGroupRuntime
import t.mma.hooks.items.conversationgroup.ConversationGroupStore
import t.mma.hooks.items.custombottombar.CustomBottomBarFeature
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

internal fun cleanMessageDetailsColor(value: String, fallback: String): String {
    return MemberTitleStore.cleanColor(value).ifEmpty { fallback }
}

internal fun messageDetailsTimeFormatChoices(current: String): List<PopupChoice<String>> {
    val choices = listOf(
        PopupChoice(label = "时分秒 · 14:30:05", value = "HH:mm:ss"),
        PopupChoice(label = "月日 时分秒 · 09-11 14:30:05", value = "MM-dd HH:mm:ss"),
        PopupChoice(label = "年月日 时分秒 · 2026-09-11 14:30:05", value = "yyyy-MM-dd HH:mm:ss"),
        PopupChoice(label = "中文年月日 · 2026年9月11日 14:30:05", value = "yyyy年M月d日 HH:mm:ss"),
        PopupChoice(label = "仅年月日 · 2026-09-11", value = "yyyy-MM-dd")
    )
    return if (choices.any { it.value == current }) choices
    else choices + PopupChoice(label = "自定义 · "+ current.ifBlank { "默认时分秒" }, value = current)
}

internal fun messageDetailsPositionValues(): Set<String> = setOf(
    HchatExtraSettings.POSITION_MESSAGE_BOTTOM,
    HchatExtraSettings.POSITION_AVATAR_ABOVE,
    HchatExtraSettings.POSITION_AVATAR_BELOW
)

internal fun messageDetailsPositionChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice(label = "消息下方", value = HchatExtraSettings.POSITION_MESSAGE_BOTTOM),
    PopupChoice(label = "头像上方", value = HchatExtraSettings.POSITION_AVATAR_ABOVE),
    PopupChoice(label = "头像下方", value = HchatExtraSettings.POSITION_AVATAR_BELOW)
)

internal fun messageDetailsPositionLabel(value: String): String = when (value) {
    HchatExtraSettings.POSITION_AVATAR_ABOVE -> "头像上方"
    HchatExtraSettings.POSITION_AVATAR_BELOW -> "头像下方"
    else -> "消息下方"
}

internal fun messageDetailsVariables(): List<TemplateVariable> {
    return listOf(
        TemplateVariable("\${time}", "时间"),
        TemplateVariable("\${relativeTime}", "相对时间"),
        TemplateVariable("\${type}", "消息类型（含文件、红包、小程序等）"),
        TemplateVariable("\${appType}", "卡片子类型编号（非卡片为空）"),
        TemplateVariable("\${baseType}", "基础消息类型编号"),
        TemplateVariable("\${direction}", "收发方向"),
        TemplateVariable("\${talker}", "会话 ID"),
        TemplateVariable("\${createTime}", "消息时间戳（毫秒）"),
        TemplateVariable("\${rawAtUserList}", "原始艾特名单"),
        TemplateVariable("\${typeDec}", "类型编号（十进制）"),
        TemplateVariable("\${typeHex}", "类型编号（十六进制）"),
        TemplateVariable("\${msgId}", "本地消息编号"),
        TemplateVariable("\${msgSvrId}", "服务端消息编号"),
        TemplateVariable("\${atUserList}", "艾特对象"),
        TemplateVariable("\${mentionedUsers}", "提及摘要")
    )
}

@Composable
internal fun ProfileIdMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val idSp = remember { HchatStorage.preferences(context, ProfileIdSettings.PREFS_NAME) }
    val addTimeSp = remember { HchatStorage.preferences(context, ProfileAddTimeSettings.PREFS_NAME) }
    var addTimeColor by remember {
        mutableStateOf(addTimeSp.getString(ProfileAddTimeSettings.KEY_COLOR, ProfileAddTimeSettings.DEFAULT_COLOR) ?: ProfileAddTimeSettings.DEFAULT_COLOR)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
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
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "资料页") }
            item {
                SettingsCard {
                    SwitchRow(idSp, ProfileIdSettings.KEY_ENABLE, "显示微信 ID", "在资料页头部显示可点击复制的 ID", ProfileIdSettings.DEFAULT_ENABLE)
                    InsetDivider()
                    SwitchRow(addTimeSp, ProfileAddTimeSettings.KEY_ENABLE, "显示添加时间", "格式：yyyy-MM-dd HH:mm:ss", ProfileAddTimeSettings.DEFAULT_ENABLE)
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "添加时间颜色") }
            item { SettingsCard {
                ColorPickerRow("添加时间渐变颜色", "支持单色或左右渐变", addTimeColor, onReset = {
                    addTimeColor = ProfileAddTimeSettings.DEFAULT_COLOR
                    addTimeSp.edit().remove(ProfileAddTimeSettings.KEY_COLOR).apply()
                }) { value ->
                    addTimeColor = ProfileAddTimeSettings.cleanColorSpec(value)
                    addTimeSp.edit().putString(ProfileAddTimeSettings.KEY_COLOR, addTimeColor).apply()
                }
            } }
        }
    }
}

@Composable
internal fun ProfileGroupInfoMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val joinTimeSp = remember { HchatStorage.preferences(context, ProfileGroupJoinTimeSettings.PREFS_NAME) }
    val inviterSp = remember { HchatStorage.preferences(context, ProfileGroupInviterSettings.PREFS_NAME) }
    var joinTimeColor by remember {
        mutableStateOf(joinTimeSp.getString(ProfileGroupJoinTimeSettings.KEY_COLOR, ProfileGroupJoinTimeSettings.DEFAULT_COLOR) ?: ProfileGroupJoinTimeSettings.DEFAULT_COLOR)
    }
    var inviterColor by remember {
        mutableStateOf(inviterSp.getString(ProfileGroupInviterSettings.KEY_COLOR, ProfileGroupInviterSettings.DEFAULT_COLOR) ?: ProfileGroupInviterSettings.DEFAULT_COLOR)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "群成员资料页") }
            item {
                SettingsCard {
                    SwitchRow(
                        joinTimeSp,
                        ProfileGroupJoinTimeSettings.KEY_ENABLE,
                        "显示进群时间",
                        "格式：yyyy-MM-dd HH:mm:ss",
                        ProfileGroupJoinTimeSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        inviterSp,
                        ProfileGroupInviterSettings.KEY_ENABLE,
                        "显示邀请人",
                        "无邀请记录时显示：自然进群",
                        ProfileGroupInviterSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "进群时间颜色") }
            item { SettingsCard {
                ColorPickerRow("进群时间渐变颜色", "支持单色或左右渐变", joinTimeColor, onReset = {
                    joinTimeColor = ProfileGroupJoinTimeSettings.DEFAULT_COLOR
                    joinTimeSp.edit().remove(ProfileGroupJoinTimeSettings.KEY_COLOR).apply()
                }) { value ->
                    joinTimeColor = ProfileGroupJoinTimeSettings.cleanColorSpec(value)
                    joinTimeSp.edit().putString(ProfileGroupJoinTimeSettings.KEY_COLOR, joinTimeColor).apply()
                }
            } }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "邀请人颜色") }
            item { SettingsCard {
                ColorPickerRow("邀请人渐变颜色", "支持单色或左右渐变", inviterColor, onReset = {
                    inviterColor = ProfileGroupInviterSettings.DEFAULT_COLOR
                    inviterSp.edit().remove(ProfileGroupInviterSettings.KEY_COLOR).apply()
                }) { value ->
                    inviterColor = ProfileGroupInviterSettings.cleanColorSpec(value)
                    inviterSp.edit().putString(ProfileGroupInviterSettings.KEY_COLOR, inviterColor).apply()
                }
            } }
        }
    }
}
@Composable
internal fun HideChatMenuMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HideChatMenuSettings.preferences(context) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(HideChatMenuSettings.KEY_ENABLE, HideChatMenuSettings.DEFAULT_ENABLE)
        )
    }
    var titles by remember {
        mutableStateOf(
            sp.getString(HideChatMenuSettings.KEY_TITLES, HideChatMenuSettings.DEFAULT_TITLES)
                .orEmpty()
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun save() {
        val normalized = HideChatMenuSettings.parseTitles(titles).joinToString(",")
        titles = normalized
        sp.edit()
            .putBoolean(HideChatMenuSettings.KEY_ENABLE, enabled)
            .putString(HideChatMenuSettings.KEY_TITLES, normalized)
            .apply()
        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = { save() },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "聊天菜单") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "隐藏长按菜单",
                        summary = "隐藏聊天消息长按菜单中的指定项目",
                        onCheckedChange = { enabled = it }
                    )
                    if (enabled) {
                        InsetDivider()
                        InputRow(
                            "隐藏菜单项",
                            "输入菜单显示名称，多个项目用逗号、分号或换行分隔",
                            titles,
                            minLines = 2,
                            onValueChange = { titles = it }
                        )
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("恢复默认", "恢复初始菜单名称示例") {
                        titles = HideChatMenuSettings.DEFAULT_TITLES
                        Toast.makeText(context, "已恢复默认，保存后生效", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsEntryMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, SettingsEntrySettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
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
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "入口") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SettingsEntrySettings.KEY_PLUS_MENU_ENABLE,
                        "注入加号菜单",
                        "在微信右上角加号菜单中显示 Hchat 入口，重启微信后生效",
                        SettingsEntrySettings.DEFAULT_PLUS_MENU_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        SettingsEntrySettings.KEY_PLUS_LONG_PRESS_ENABLE,
                        "长按加号入口",
                        "长按微信右上角加号打开 Hchat 设置，重启微信后生效",
                        SettingsEntrySettings.DEFAULT_PLUS_LONG_PRESS_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun PluginAgentEntryMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, SettingsEntrySettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "加号菜单") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SettingsEntrySettings.KEY_PLUGIN_AGENT_PLUS_MENU_ENABLE,
                        "插件 Agent 入口",
                        "在微信右上角加号菜单中显示插件 Agent，重启微信后生效",
                        SettingsEntrySettings.DEFAULT_PLUGIN_AGENT_PLUS_MENU_ENABLE
                    )
                }
            }
        }
    }
}

internal data class FloatingShortcutEditorRequest(
    val original: FloatingShortcutItem?,
    val draft: FloatingShortcutItem
)

internal enum class FloatingShortcutRoute {
    MAIN,
    APPEARANCE
}

@Composable
internal fun FloatingShortcutMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FloatingShortcutSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(FloatingShortcutRuntime.isEnabled(context)) }
    var scope by remember {
        mutableStateOf(
            sp.getString(FloatingShortcutSettings.KEY_SCOPE, FloatingShortcutSettings.DEFAULT_SCOPE)
                ?: FloatingShortcutSettings.DEFAULT_SCOPE
        )
    }
    var displayMode by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_DISPLAY_MODE,
                FloatingShortcutSettings.DEFAULT_DISPLAY_MODE
            ) ?: FloatingShortcutSettings.DEFAULT_DISPLAY_MODE
        )
    }
    var expandDirection by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_EXPAND_DIRECTION,
                FloatingShortcutSettings.DEFAULT_EXPAND_DIRECTION
            ) ?: FloatingShortcutSettings.DEFAULT_EXPAND_DIRECTION
        )
    }
    var bubbleIcon by remember {
        mutableStateOf(sp.getString(FloatingShortcutSettings.KEY_BUBBLE_ICON, "").orEmpty())
    }
    var bubbleDarkIcon by remember {
        mutableStateOf(sp.getString(FloatingShortcutSettings.KEY_BUBBLE_DARK_ICON, "").orEmpty())
    }
    var shortcuts by remember { mutableStateOf(FloatingShortcutSettings.loadItems(context)) }
    var editor by remember { mutableStateOf<FloatingShortcutEditorRequest?>(null) }
    var route by remember { mutableStateOf(FloatingShortcutRoute.MAIN) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun saveShortcuts(value: List<FloatingShortcutItem>) {
        shortcuts = value
        FloatingShortcutSettings.saveItems(context, value)
    }

    fun pickIcon(key: String, onSaved: (String) -> Unit) {
        val activity = (context as? Activity) ?: WeChatApis.currentActivity()?.currentActivity()
        if (activity == null) {
            Toast.makeText(context, "当前页面无法打开图片选择器", Toast.LENGTH_SHORT).show()
            return
        }
        FloatingShortcutIconPicker.launch(activity, key) { result ->
            when (result) {
                is FloatingShortcutIconPickResult.Saved -> onSaved(result.path)
                FloatingShortcutIconPickResult.FAILED ->
                    Toast.makeText(context, "图标读取失败", Toast.LENGTH_SHORT).show()
                FloatingShortcutIconPickResult.CANCELLED -> Unit
            }
        }
    }

    fun dismissEditor() {
        val request = editor ?: return
        if (request.draft.iconPath != request.original?.iconPath) {
            FloatingShortcutIconStore.delete(context, request.draft.iconPath)
        }
        if (request.draft.darkIconPath != request.original?.darkIconPath) {
            FloatingShortcutIconStore.delete(context, request.draft.darkIconPath)
        }
        editor = null
    }

    SettingsRouteTransition(
        targetState = route,
        label = "FloatingShortcutRoute",
        depthOf = { if (it == FloatingShortcutRoute.MAIN) 0 else 1 }
    ) { currentRoute ->
        when (currentRoute) {
            FloatingShortcutRoute.MAIN -> PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "悬浮入口") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "悬浮快捷菜单",
                        summary = "在微信页面显示可自由拖动和停放的快捷入口",
                        onCheckedChange = {
                            enabled = it
                            FloatingShortcutRuntime.setEnabled(context, it)
                        }
                    )
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "显示方式") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "显示范围",
                            summary = if (scope == FloatingShortcutSettings.SCOPE_ALL) {
                                "微信所有页面"
                            } else {
                                "仅微信主页"
                            },
                            options = listOf(
                                PopupChoice("仅微信主页", FloatingShortcutSettings.SCOPE_HOME),
                                PopupChoice("所有微信页面", FloatingShortcutSettings.SCOPE_ALL)
                            ),
                            currentValue = scope,
                            onValueChanged = {
                                scope = it
                                sp.edit().putString(FloatingShortcutSettings.KEY_SCOPE, it).apply()
                            }
                        )
                        InsetDivider()
                        PopupChoiceRow(
                            title = "快捷项样式",
                            summary = when (displayMode) {
                                FloatingShortcutSettings.DISPLAY_TEXT -> "仅文字"
                                FloatingShortcutSettings.DISPLAY_BOTH -> "图标和文字"
                                else -> "仅图标"
                            },
                            options = listOf(
                                PopupChoice("仅图标", FloatingShortcutSettings.DISPLAY_ICON),
                                PopupChoice("仅文字", FloatingShortcutSettings.DISPLAY_TEXT),
                                PopupChoice("图标和文字", FloatingShortcutSettings.DISPLAY_BOTH)
                            ),
                            currentValue = displayMode,
                            onValueChanged = {
                                displayMode = it
                                sp.edit().putString(FloatingShortcutSettings.KEY_DISPLAY_MODE, it).apply()
                            }
                        )
                        InsetDivider()
                        PopupChoiceRow(
                            title = "展开方向",
                            summary = when (expandDirection) {
                                FloatingShortcutSettings.EXPAND_DOWN -> "向下展开"
                                FloatingShortcutSettings.EXPAND_LEFT -> "向左展开"
                                FloatingShortcutSettings.EXPAND_RIGHT -> "向右展开"
                                else -> "向上展开"
                            },
                            options = listOf(
                                PopupChoice("向上展开", FloatingShortcutSettings.EXPAND_UP),
                                PopupChoice("向下展开", FloatingShortcutSettings.EXPAND_DOWN),
                                PopupChoice("向左展开", FloatingShortcutSettings.EXPAND_LEFT),
                                PopupChoice("向右展开", FloatingShortcutSettings.EXPAND_RIGHT)
                            ),
                            currentValue = expandDirection,
                            onValueChanged = {
                                expandDirection = it
                                sp.edit().putString(FloatingShortcutSettings.KEY_EXPAND_DIRECTION, it).apply()
                            }
                        )
                        InsetDivider()
                        ActionRow(
                            title = "按钮外观",
                            summary = "设置按钮渐变、大小和菜单名称样式",
                            onClick = { route = FloatingShortcutRoute.APPEARANCE }
                        )
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "主按钮图标") }
                item {
                    SettingsCard {
                        FloatingShortcutIconActionRow(
                            title = "浅色模式图标",
                            iconPath = bubbleIcon,
                            defaultSummary = "未选择时使用内置快捷菜单图标",
                            onSelect = {
                                pickIcon("bubble-${System.nanoTime()}") { path ->
                                    val previous = bubbleIcon
                                    bubbleIcon = path
                                    sp.edit().putString(FloatingShortcutSettings.KEY_BUBBLE_ICON, path).apply()
                                    FloatingShortcutIconStore.delete(context, previous)
                                }
                            },
                            onReset = if (bubbleIcon.isBlank()) null else {
                                {
                                    val previous = bubbleIcon
                                    bubbleIcon = ""
                                    sp.edit().remove(FloatingShortcutSettings.KEY_BUBBLE_ICON).apply()
                                    FloatingShortcutIconStore.delete(context, previous)
                                }
                            }
                        )
                        InsetDivider()
                        FloatingShortcutIconActionRow(
                            title = "深色模式图标",
                            iconPath = bubbleDarkIcon,
                            defaultSummary = "未选择时沿用浅色模式图标",
                            onSelect = {
                                pickIcon("bubble-dark-${System.nanoTime()}") { path ->
                                    val previous = bubbleDarkIcon
                                    bubbleDarkIcon = path
                                    sp.edit().putString(FloatingShortcutSettings.KEY_BUBBLE_DARK_ICON, path).apply()
                                    FloatingShortcutIconStore.delete(context, previous)
                                }
                            },
                            onReset = if (bubbleDarkIcon.isBlank()) null else {
                                {
                                    val previous = bubbleDarkIcon
                                    bubbleDarkIcon = ""
                                    sp.edit().remove(FloatingShortcutSettings.KEY_BUBBLE_DARK_ICON).apply()
                                    FloatingShortcutIconStore.delete(context, previous)
                                }
                            }
                        )
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "快捷项") }
                item {
                    SettingsCard {
                        if (shortcuts.isEmpty()) {
                            Text(
                                text = "暂无快捷项",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            shortcuts.forEachIndexed { index, item ->
                                if (index > 0) InsetDivider()
                                FloatingShortcutSettingsItemRow(
                                    item = item,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < shortcuts.lastIndex,
                                    onEnabledChange = { checked ->
                                        saveShortcuts(shortcuts.toMutableList().apply {
                                            this[index] = item.copy(enabled = checked)
                                        })
                                    },
                                    onMoveUp = {
                                        if (index > 0) {
                                            saveShortcuts(shortcuts.toMutableList().apply {
                                                val moved = removeAt(index)
                                                add(index - 1, moved)
                                            })
                                        }
                                    },
                                    onMoveDown = {
                                        if (index < shortcuts.lastIndex) {
                                            saveShortcuts(shortcuts.toMutableList().apply {
                                                val moved = removeAt(index)
                                                add(index + 1, moved)
                                            })
                                        }
                                    },
                                    onEdit = { editor = FloatingShortcutEditorRequest(item, item) },
                                    onDelete = {
                                        FloatingShortcutIconStore.delete(context, item.iconPath)
                                        FloatingShortcutIconStore.delete(context, item.darkIconPath)
                                        saveShortcuts(shortcuts.filterNot { it.id == item.id })
                                    }
                                )
                            }
                        }
                        InsetDivider()
                        ActionRow(
                            title = "新增快捷项",
                            summary = "添加微信页面、模块设置或插件 Agent 入口",
                            onClick = {
                                editor = FloatingShortcutEditorRequest(
                                    original = null,
                                    draft = FloatingShortcutItem(
                                        id = "shortcut_${System.currentTimeMillis()}_${shortcuts.size}",
                                        title = "",
                                        actionType = FloatingShortcutSettings.ACTION_ACTIVITY,
                                        target = ""
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
            FloatingShortcutRoute.APPEARANCE -> FloatingShortcutAppearanceMiuixPage(
                context = context,
                onBack = { route = FloatingShortcutRoute.MAIN }
            )
        }
    }

    editor?.let { request ->
        val draft = request.draft
        val valid = draft.title.isNotBlank() &&
            (draft.actionType != FloatingShortcutSettings.ACTION_ACTIVITY || draft.target.isNotBlank())
        WindowDialog(
            show = true,
            title = if (request.original == null) "新增快捷项" else "编辑快捷项",
            onDismissRequest = ::dismissEditor,
            content = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    InputRow(
                        title = "名称",
                        summary = "显示在展开菜单中的名称",
                        value = draft.title,
                        onValueChange = { editor = request.copy(draft = draft.copy(title = it)) }
                    )
                    PopupChoiceRow(
                        title = "动作类型",
                        summary = when (draft.actionType) {
                            FloatingShortcutSettings.ACTION_MODULE_SETTINGS -> "打开 Hchat 设置"
                            FloatingShortcutSettings.ACTION_PLUGIN_AGENT -> "展开或收起插件 Agent"
                            else -> "打开微信页面"
                        },
                        options = listOf(
                            PopupChoice("微信页面", FloatingShortcutSettings.ACTION_ACTIVITY),
                            PopupChoice("模块设置", FloatingShortcutSettings.ACTION_MODULE_SETTINGS),
                            PopupChoice("插件 Agent", FloatingShortcutSettings.ACTION_PLUGIN_AGENT)
                        ),
                        currentValue = draft.actionType,
                        onValueChanged = { type ->
                            editor = request.copy(
                                draft = draft.copy(
                                    actionType = type,
                                    target = if (type == FloatingShortcutSettings.ACTION_ACTIVITY) draft.target else ""
                                )
                            )
                        }
                    )
                    if (draft.actionType == FloatingShortcutSettings.ACTION_ACTIVITY) {
                        InputRow(
                            title = "Activity 类名",
                            summary = "填写微信页面的完整 Activity 类名",
                            value = draft.target,
                            onValueChange = { editor = request.copy(draft = draft.copy(target = it)) }
                        )
                    }
                    FloatingShortcutIconActionRow(
                        title = "浅色模式图标",
                        iconPath = draft.iconPath,
                        defaultSummary = "未选择时使用内置图标",
                        onSelect = {
                            pickIcon("item-${draft.id}-${System.nanoTime()}") { path ->
                                if (draft.iconPath != request.original?.iconPath) {
                                    FloatingShortcutIconStore.delete(context, draft.iconPath)
                                }
                                editor = request.copy(draft = draft.copy(iconPath = path))
                            }
                        },
                        onReset = if (draft.iconPath.isBlank()) null else {
                            {
                                if (draft.iconPath != request.original?.iconPath) {
                                    FloatingShortcutIconStore.delete(context, draft.iconPath)
                                }
                                editor = request.copy(draft = draft.copy(iconPath = ""))
                            }
                        }
                    )
                    FloatingShortcutIconActionRow(
                        title = "深色模式图标",
                        iconPath = draft.darkIconPath,
                        defaultSummary = "未选择时沿用浅色模式图标",
                        onSelect = {
                            pickIcon("item-dark-${draft.id}-${System.nanoTime()}") { path ->
                                if (draft.darkIconPath != request.original?.darkIconPath) {
                                    FloatingShortcutIconStore.delete(context, draft.darkIconPath)
                                }
                                editor = request.copy(draft = draft.copy(darkIconPath = path))
                            }
                        },
                        onReset = if (draft.darkIconPath.isBlank()) null else {
                            {
                                if (draft.darkIconPath != request.original?.darkIconPath) {
                                    FloatingShortcutIconStore.delete(context, draft.darkIconPath)
                                }
                                editor = request.copy(draft = draft.copy(darkIconPath = ""))
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = ::dismissEditor,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        TextButton(
                            text = "保存",
                            onClick = {
                                if (!valid) {
                                    Toast.makeText(context, "请填写完整的快捷项信息", Toast.LENGTH_SHORT).show()
                                    return@TextButton
                                }
                                val saved = draft.copy(
                                    title = draft.title.trim(),
                                    target = draft.target.trim()
                                )
                                val original = request.original
                                if (original != null && original.iconPath != saved.iconPath) {
                                    FloatingShortcutIconStore.delete(context, original.iconPath)
                                }
                                if (original != null && original.darkIconPath != saved.darkIconPath) {
                                    FloatingShortcutIconStore.delete(context, original.darkIconPath)
                                }
                                val next = shortcuts.toMutableList()
                                val index = next.indexOfFirst { it.id == saved.id }
                                if (index >= 0) next[index] = saved else next.add(saved)
                                saveShortcuts(next)
                                editor = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        )
    }
}

@Composable
internal fun FloatingShortcutAppearanceMiuixPage(
    context: Context,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FloatingShortcutSettings.PREFS_NAME) }
    var bubbleSize by remember {
        mutableStateOf(
            sp.getInt(
                FloatingShortcutSettings.KEY_BUBBLE_SIZE,
                FloatingShortcutSettings.DEFAULT_BUBBLE_SIZE
            ).coerceIn(FloatingShortcutSettings.MIN_BUTTON_SIZE, FloatingShortcutSettings.MAX_BUTTON_SIZE)
        )
    }
    var bubbleColor by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_BUBBLE_COLOR,
                FloatingShortcutSettings.DEFAULT_BUBBLE_COLOR
            ) ?: FloatingShortcutSettings.DEFAULT_BUBBLE_COLOR
        )
    }
    var actionSize by remember {
        mutableStateOf(
            sp.getInt(
                FloatingShortcutSettings.KEY_ACTION_SIZE,
                FloatingShortcutSettings.DEFAULT_ACTION_SIZE
            ).coerceIn(FloatingShortcutSettings.MIN_BUTTON_SIZE, FloatingShortcutSettings.MAX_BUTTON_SIZE)
        )
    }
    var actionColor by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_ACTION_COLOR,
                FloatingShortcutSettings.DEFAULT_ACTION_COLOR
            ) ?: FloatingShortcutSettings.DEFAULT_ACTION_COLOR
        )
    }
    var labelTextSize by remember {
        mutableStateOf(
            sp.getInt(
                FloatingShortcutSettings.KEY_LABEL_TEXT_SIZE,
                FloatingShortcutSettings.DEFAULT_LABEL_TEXT_SIZE
            ).coerceIn(
                FloatingShortcutSettings.MIN_LABEL_TEXT_SIZE,
                FloatingShortcutSettings.MAX_LABEL_TEXT_SIZE
            )
        )
    }
    var labelColor by remember {
        mutableStateOf(
            sp.getString(
                FloatingShortcutSettings.KEY_LABEL_COLOR,
                FloatingShortcutSettings.DEFAULT_LABEL_COLOR
            ) ?: FloatingShortcutSettings.DEFAULT_LABEL_COLOR
        )
    }
    val currentBubbleSize by rememberUpdatedState(bubbleSize)
    val currentBubbleColor by rememberUpdatedState(bubbleColor)
    val currentActionSize by rememberUpdatedState(actionSize)
    val currentActionColor by rememberUpdatedState(actionColor)
    val currentLabelTextSize by rememberUpdatedState(labelTextSize)
    val currentLabelColor by rememberUpdatedState(labelColor)
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun persistAppearance() {
        sp.edit()
            .putInt(FloatingShortcutSettings.KEY_BUBBLE_SIZE, currentBubbleSize)
            .putString(FloatingShortcutSettings.KEY_BUBBLE_COLOR, currentBubbleColor.trim())
            .putInt(FloatingShortcutSettings.KEY_ACTION_SIZE, currentActionSize)
            .putString(FloatingShortcutSettings.KEY_ACTION_COLOR, currentActionColor.trim())
            .putInt(FloatingShortcutSettings.KEY_LABEL_TEXT_SIZE, currentLabelTextSize)
            .putString(FloatingShortcutSettings.KEY_LABEL_COLOR, currentLabelColor.trim())
            .apply()
    }

    DisposableEffect(sp) {
        onDispose(::persistAppearance)
    }

    PageScaffold(
        title = "按钮外观",
        largeTitle = "按钮外观",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "返回",
                onPrimaryClick = {
                    persistAppearance()
                    onBack()
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "主按钮") }
            item {
                SettingsCard {
                    FloatingShortcutSizeSlider("主按钮大小", bubbleSize) { bubbleSize = it }
                    InsetDivider()
                    ColorPickerRow(
                        title = "主按钮颜色",
                        summary = "圆形背景色",
                        value = bubbleColor,
                        onReset = { bubbleColor = FloatingShortcutSettings.DEFAULT_BUBBLE_COLOR }
                    ) { bubbleColor = it.take(19) }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "副按钮") }
            item {
                SettingsCard {
                    FloatingShortcutSizeSlider("副按钮大小", actionSize) { actionSize = it }
                    InsetDivider()
                    ColorPickerRow(
                        title = "副按钮颜色",
                        summary = "留空时跟随深浅色模式",
                        value = actionColor,
                        onReset = { actionColor = FloatingShortcutSettings.DEFAULT_ACTION_COLOR }
                    ) { actionColor = it.take(19) }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "菜单名称") }
            item {
                SettingsCard {
                    FloatingShortcutTextSizeSlider("菜单名称大小", labelTextSize) { labelTextSize = it }
                    InsetDivider()
                    ColorPickerRow(
                        title = "菜单名称颜色",
                        summary = "留空时跟随深浅色模式",
                        value = labelColor,
                        onReset = { labelColor = FloatingShortcutSettings.DEFAULT_LABEL_COLOR }
                    ) { labelColor = it.take(19) }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "重置") }
            item {
                SettingsCard {
                    ActionRow(
                        title = "恢复默认外观",
                        summary = "恢复按钮和菜单名称的默认外观",
                        onClick = {
                            bubbleSize = FloatingShortcutSettings.DEFAULT_BUBBLE_SIZE
                            bubbleColor = FloatingShortcutSettings.DEFAULT_BUBBLE_COLOR
                            actionSize = FloatingShortcutSettings.DEFAULT_ACTION_SIZE
                            actionColor = FloatingShortcutSettings.DEFAULT_ACTION_COLOR
                            labelTextSize = FloatingShortcutSettings.DEFAULT_LABEL_TEXT_SIZE
                            labelColor = FloatingShortcutSettings.DEFAULT_LABEL_COLOR
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FloatingShortcutTextSizeSlider(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "$title ${value}sp",
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            valueRange = FloatingShortcutSettings.MIN_LABEL_TEXT_SIZE.toFloat()..
                FloatingShortcutSettings.MAX_LABEL_TEXT_SIZE.toFloat(),
            steps = FloatingShortcutSettings.MAX_LABEL_TEXT_SIZE -
                FloatingShortcutSettings.MIN_LABEL_TEXT_SIZE - 1,
            showKeyPoints = true,
            keyPoints = listOf(10f, 12f, 14f, 16f, 18f, 20f, 24f)
        )
    }
}

@Composable
internal fun FloatingShortcutSizeSlider(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "$title ${value}dp",
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            valueRange = FloatingShortcutSettings.MIN_BUTTON_SIZE.toFloat()..
                FloatingShortcutSettings.MAX_BUTTON_SIZE.toFloat(),
            steps = FloatingShortcutSettings.MAX_BUTTON_SIZE -
                FloatingShortcutSettings.MIN_BUTTON_SIZE - 1,
            showKeyPoints = true,
            keyPoints = listOf(36f, 44f, 52f, 60f, 64f)
        )
    }
}

@Composable
internal fun FloatingShortcutSettingsItemRow(
    item: FloatingShortcutItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FloatingShortcutIconPreview(
                if (isSystemInDarkTheme() && item.darkIconPath.isNotBlank()) item.darkIconPath else item.iconPath,
                item.title,
                isPluginAgent = item.actionType == FloatingShortcutSettings.ACTION_PLUGIN_AGENT,
                defaultGlyph = FloatingShortcutGlyphs.forItem(item)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = item.title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (item.actionType) {
                        FloatingShortcutSettings.ACTION_MODULE_SETTINGS -> "模块设置"
                        FloatingShortcutSettings.ACTION_PLUGIN_AGENT -> "插件 Agent"
                        else -> item.target
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = item.enabled, onCheckedChange = onEnabledChange)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingShortcutItemAction(NavIcons.MoveUp, "上移", canMoveUp, onMoveUp)
            FloatingShortcutItemAction(NavIcons.MoveDown, "下移", canMoveDown, onMoveDown)
            FloatingShortcutItemAction(NavIcons.Edit, "编辑", true, onEdit)
            FloatingShortcutItemAction(NavIcons.Delete, "删除", true, onDelete, destructive = true)
        }
    }
}

@Composable
internal fun FloatingShortcutItemAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val color = when {
        !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
        destructive -> Color(0xFFD93025)
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Image(
        imageVector = icon,
        contentDescription = description,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.padding(start = 4.dp).size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(7.dp)
    )
}

@Composable
internal fun FloatingShortcutIconActionRow(
    title: String,
    iconPath: String,
    defaultSummary: String,
    onSelect: () -> Unit,
    onReset: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingShortcutIconPreview(iconPath, title)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(
                text = if (iconPath.isBlank()) defaultSummary else File(iconPath).name,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            text = "选择",
            onClick = onSelect,
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        if (onReset != null) {
            TextButton(
                text = "默认",
                onClick = onReset,
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
internal fun FloatingShortcutIconPreview(
    path: String,
    description: String,
    isPluginAgent: Boolean = false,
    defaultGlyph: FloatingShortcutGlyph? = null
) {
    val previewSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val bitmap = remember(path, previewSizePx) {
        path.takeIf { it.isNotBlank() }?.let { decodeFavoriteImage(it, previewSizePx) }
    }
    val fallbackTint = MiuixTheme.colorScheme.onSecondaryVariant.toArgb()
    Box(
        modifier = Modifier.size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isPluginAgent) {
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
                },
                update = { imageView ->
                    imageView.contentDescription = description
                    imageView.setImageDrawable(
                        HchatAgentIconDrawable(fallbackTint, HchatAgentIconDrawable.Frame.CIRCLE)
                    )
                },
                modifier = Modifier.size(26.dp)
            )
        } else if (defaultGlyph != null) {
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
                },
                update = { imageView ->
                    imageView.contentDescription = description
                    imageView.setImageDrawable(FloatingShortcutGlyphDrawable(defaultGlyph, fallbackTint))
                },
                modifier = Modifier.size(24.dp)
            )
        } else {
            Image(
                imageVector = NavIcons.Practical,
                contentDescription = description,
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSecondaryVariant),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun QuickMarkReadMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickMarkReadSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
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
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "快捷已读") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuickMarkReadSettings.KEY_DRAG_READ,
                        "拖拽已读",
                        "在微信底部未读角标向上拖动后，清空全部会话未读",
                        QuickMarkReadSettings.DEFAULT_DRAG_READ
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QuickMarkReadSettings.KEY_PLUS_MENU_READ,
                        "注入加号菜单已读",
                        "在右上角加号菜单添加“全部已读”，重启微信后生效",
                        QuickMarkReadSettings.DEFAULT_PLUS_MENU_READ
                    )
                }
            }
        }
    }
}

@Composable
internal fun WeChatKeepAliveMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, WeChatKeepAliveSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_ENABLE, WeChatKeepAliveSettings.DEFAULT_ENABLE)) }
    var foregroundService by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_FOREGROUND_SERVICE, WeChatKeepAliveSettings.DEFAULT_FOREGROUND_SERVICE)) }
    var wakeLock by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_WAKE_LOCK, WeChatKeepAliveSettings.DEFAULT_WAKE_LOCK)) }
    var rootWhitelist by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_ROOT_DOZE_WHITELIST, WeChatKeepAliveSettings.DEFAULT_ROOT_DOZE_WHITELIST)) }
    var watchdog by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_WATCHDOG, WeChatKeepAliveSettings.DEFAULT_WATCHDOG)) }
    var rootAppOps by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_ROOT_APP_OPS, WeChatKeepAliveSettings.DEFAULT_ROOT_APP_OPS)) }
    var networkHeartbeat by remember { mutableStateOf(sp.getBoolean(WeChatKeepAliveSettings.KEY_NETWORK_HEARTBEAT, WeChatKeepAliveSettings.DEFAULT_NETWORK_HEARTBEAT)) }
    var ignoringBattery by remember { mutableStateOf(WeChatKeepAliveRuntime.isIgnoringBatteryOptimizations(context)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun saveBoolean(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
        WeChatKeepAliveRuntime.apply(context)
    }

    fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show() }
    }

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "强保活") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用微信强保活",
                        summary = "开启后尝试保持微信息屏运行",
                        onCheckedChange = {
                            enabled = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_ENABLE, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = foregroundService,
                        title = "前台服务保活",
                        summary = "通过常驻通知提高后台存活率",
                        onCheckedChange = {
                            foregroundService = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_FOREGROUND_SERVICE, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = wakeLock,
                        title = "WakeLock 保活",
                        summary = "息屏后保持 CPU 运行，耗电会增加",
                        onCheckedChange = {
                            wakeLock = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_WAKE_LOCK, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = rootWhitelist,
                        title = "Root Doze 白名单",
                        summary = "有 Root 时执行 deviceidle 白名单命令",
                        onCheckedChange = {
                            rootWhitelist = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_ROOT_DOZE_WHITELIST, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = rootAppOps,
                        title = "Root 后台限制放行",
                        summary = "有 Root 时放行微信后台运行相关 AppOps",
                        onCheckedChange = {
                            rootAppOps = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_ROOT_APP_OPS, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = watchdog,
                        title = "看门狗拉起微信",
                        summary = "前台服务存活时定期尝试拉起微信",
                        onCheckedChange = {
                            watchdog = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_WATCHDOG, it)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = networkHeartbeat,
                        title = "网络心跳保活",
                        summary = "定时发起轻量网络请求保持链路活跃",
                        onCheckedChange = {
                            networkHeartbeat = it
                            saveBoolean(WeChatKeepAliveSettings.KEY_NETWORK_HEARTBEAT, it)
                        }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "系统限制") }
            item {
                SettingsCard {
                    InfoRow(
                        "电池优化白名单",
                        if (ignoringBattery) "微信已在白名单" else "微信可能仍受系统省电影响"
                    )
                    InsetDivider()
                    ActionRow("打开电池优化设置", "建议把微信设置为不限制") {
                        openBatterySettings()
                    }
                    InsetDivider()
                    ActionRow("刷新状态", if (ignoringBattery) "当前已忽略优化" else "当前未忽略优化") {
                        ignoringBattery = WeChatKeepAliveRuntime.isIgnoringBatteryOptimizations(context)
                        Toast.makeText(context, "状态已刷新", Toast.LENGTH_SHORT).show()
                    }
                    InsetDivider()
                    ActionRow("立即应用保活", "重新启动服务并应用 WakeLock / Root 白名单") {
                        WeChatKeepAliveRuntime.apply(context)
                        ignoringBattery = WeChatKeepAliveRuntime.isIgnoringBatteryOptimizations(context)
                        Toast.makeText(context, "已应用", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomNotificationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { CustomNotificationSettings(context) }
    val sp = remember { HchatStorage.preferences(context, CustomNotificationSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<CustomNotificationRoute>(CustomNotificationRoute.Main) }
    var rules by remember { mutableStateOf(settings.rules()) }
    var defaultPrivateRule by remember { mutableStateOf(settings.defaultPrivateRule()) }
    var defaultGroupRule by remember { mutableStateOf(settings.defaultGroupRule()) }
    var defaultOfficialRule by remember { mutableStateOf(settings.defaultOfficialRule()) }
    val ruleListState = rememberLazyListState()
    var ruleQuery by remember { mutableStateOf("") }
    val ruleEditorListState = rememberLazyListState()
    val defaultEditorListState = rememberLazyListState()

    fun saveRules(next: List<CustomNotificationRule>) {
        val clean = next.distinctBy { it.talker }
        rules = clean
        settings.saveRules(clean)
    }

    fun saveDefaultRule(rule: CustomNotificationRule) {
        when {
            rule.official -> {
                defaultOfficialRule = rule
                settings.saveDefaultOfficialRule(rule)
            }
            rule.group -> {
                defaultGroupRule = rule
                settings.saveDefaultGroupRule(rule)
            }
            else -> {
                defaultPrivateRule = rule
                settings.saveDefaultPrivateRule(rule)
            }
        }
    }

    SettingsRouteTransition(
        targetState = route,
        label = "CustomNotificationRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            CustomNotificationRoute.Main -> CustomNotificationMainPage(
                context = context,
                provider = provider,
                sp = sp,
                rules = rules,
                defaultPrivateRule = defaultPrivateRule,
                defaultGroupRule = defaultGroupRule,
                defaultOfficialRule = defaultOfficialRule,
                onBack = onBack,
                onOpenRules = { route = CustomNotificationRoute.RuleList },
                onEditDefault = { route = CustomNotificationRoute.DefaultEditor(it) }
            )
            is CustomNotificationRoute.DefaultEditor -> {
                val rule = when (currentRoute.kind) {
                    CustomNotificationDefaultKind.PRIVATE -> defaultPrivateRule
                    CustomNotificationDefaultKind.GROUP -> defaultGroupRule
                    CustomNotificationDefaultKind.OFFICIAL -> defaultOfficialRule
                }
                CustomNotificationRuleEditorPage(
                    context = context,
                    initialRule = rule,
                    listState = defaultEditorListState,
                    isDefault = true,
                    onBack = { route = CustomNotificationRoute.Main },
                    onSave = { saved ->
                        saveDefaultRule(saved)
                        Toast.makeText(context, "默认规则已保存", Toast.LENGTH_SHORT).show()
                        route = CustomNotificationRoute.Main
                    },
                    onDelete = {},
                    onPickOnlyMembers = {},
                    onPickBlockMembers = {}
                )
            }
            CustomNotificationRoute.RuleList -> CustomNotificationRuleListPage(
                context = context,
                rules = rules,
                query = ruleQuery,
                onQueryChange = { ruleQuery = it },
                listState = ruleListState,
                onBack = { route = CustomNotificationRoute.Main },
                onSaveRules = ::saveRules,
                onEditRule = { route = CustomNotificationRoute.RuleEditor(it) },
                onToggleRule = { rule, enabled ->
                    saveRules(rules.map { if (it.talker == rule.talker) it.copy(enabled = enabled) else it })
                    Toast.makeText(context, if (enabled) "已启用该会话规则" else "已关闭该会话规则", Toast.LENGTH_SHORT).show()
                },
                onBatchEdit = { route = CustomNotificationRoute.BatchEditor },
                onAddRules = {
                    route = CustomNotificationRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "添加通知会话",
                            mode = ContactPickerMode.ALL_CHATS,
                            multiSelect = true,
                            existingValue = "",
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            CustomNotificationRoute.BatchEditor -> CustomNotificationBatchEditorPage(
                ruleCount = rules.size,
                onBack = { route = CustomNotificationRoute.RuleList },
                onApply = { update ->
                    if (!update.hasChanges()) {
                        Toast.makeText(context, "请选择要应用的配置", Toast.LENGTH_SHORT).show()
                    } else {
                        saveRules(rules.map { rule -> update.apply(rule) })
                        Toast.makeText(context, "批量配置已应用", Toast.LENGTH_SHORT).show()
                        route = CustomNotificationRoute.RuleList
                    }
                }
            )
            is CustomNotificationRoute.RuleEditor -> {
                val rule = rules.firstOrNull { it.talker == currentRoute.talker }
                if (rule == null) {
                    CustomNotificationMissingRulePage(
                        onBack = { route = CustomNotificationRoute.RuleList }
                    )
                } else {
                    CustomNotificationRuleEditorPage(
                        context = context,
                        initialRule = rule,
                        listState = ruleEditorListState,
                        onBack = { route = CustomNotificationRoute.RuleList },
                        onSave = { saved ->
                            saveRules(rules.map { if (it.talker == saved.talker) saved else it })
                            Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
                            route = CustomNotificationRoute.RuleList
                        },
                        onDelete = {
                            saveRules(rules.filterNot { it.talker == rule.talker })
                            Toast.makeText(context, "规则已删除", Toast.LENGTH_SHORT).show()
                            route = CustomNotificationRoute.RuleList
                        },
                        onPickOnlyMembers = { value ->
                            route = CustomNotificationRoute.GroupMemberPicker(
                                talker = rule.talker,
                                request = GroupMemberPickerRequest(
                                    title = "仅显示成员通知",
                                    existingValue = memberRulesToGroupEntries(rule.talker, value),
                                    onValue = {}
                                )
                            )
                        },
                        onPickBlockMembers = { value ->
                            route = CustomNotificationRoute.GroupMemberPicker(
                                talker = rule.talker,
                                request = GroupMemberPickerRequest(
                                    title = "屏蔽成员通知",
                                    existingValue = memberRulesToGroupEntries(rule.talker, value),
                                    onValue = {}
                                )
                            )
                        }
                    )
                }
            }
            is CustomNotificationRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = CustomNotificationRoute.RuleList },
                onConfirm = { picked ->
                    if (picked.isEmpty()) {
                        Toast.makeText(context, "未选择会话", Toast.LENGTH_SHORT).show()
                        route = CustomNotificationRoute.RuleList
                    } else {
                        val byTalker = rules.associateBy { it.talker }.toMutableMap()
                        var added = 0
                        var updated = 0
                        picked.forEach { option ->
                            val old = byTalker[option.id]
                            if (old == null) {
                                added++
                                byTalker[option.id] = customNotificationRuleFromContact(option)
                            } else {
                                updated++
                                byTalker[option.id] = old.copy(
                                    label = option.label,
                                    group = option.group,
                                    official = option.official
                                )
                            }
                        }
                        saveRules(byTalker.values.sortedWith(compareBy<CustomNotificationRule> { !it.enabled }.thenBy { it.label.lowercase(Locale.US) }))
                        val message = when {
                            added > 0 && updated > 0 -> "已添加 $added 个会话，更新 $updated 个会话"
                            added > 0 -> "已添加 $added 个会话"
                            else -> "已更新 $updated 个会话"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        route = CustomNotificationRoute.RuleList
                    }
                }
            )
            is CustomNotificationRoute.GroupMemberPicker -> GroupMemberPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = CustomNotificationRoute.RuleEditor(currentRoute.talker) },
                onConfirm = { entries ->
                    val talker = currentRoute.talker
                    val memberValue = groupEntriesToMemberRules(talker, entries)
                    val title = currentRoute.request.title
                    rules.firstOrNull { it.talker == talker }?.let { old ->
                        val nextRule = if (title.contains("屏蔽")) {
                            old.copy(blockMembers = memberValue)
                        } else {
                            old.copy(onlyMembers = memberValue)
                        }
                        saveRules(rules.map { if (it.talker == talker) nextRule else it })
                        Toast.makeText(context, "成员规则已保存", Toast.LENGTH_SHORT).show()
                    }
                    route = CustomNotificationRoute.RuleEditor(talker)
                }
            )
        }
    }
}

@Composable
internal fun AutoReplyMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AutoReplySettings.PREFS_NAME) }
    val settings = remember { AutoReplySettings(context) }
    var rules by remember { mutableStateOf(settings.rules()) }
    var route by remember { mutableStateOf("main") }
    var editingRuleId by remember { mutableStateOf<String?>(null) }
    var editingRuleDraft by remember { mutableStateOf<AutoReplyRule?>(null) }
    var editingSteps by remember { mutableStateOf<Pair<String, List<AutoReplyStep>>?>(null) }
    val ruleListState = rememberLazyListState()
    var ruleQuery by remember { mutableStateOf("") }
    val ruleEditorListState = rememberLazyListState()
    val autoAcceptListState = rememberLazyListState()
    val greetAcceptedListState = rememberLazyListState()
    val autoAcceptLabelListState = rememberLazyListState()
    val greetAcceptedLabelListState = rememberLazyListState()
    val autoAcceptAutomationKeys = remember {
        AutoReplyFriendAutomationKeys(
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_NEW_FRIEND_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_DATE_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_DATE_FORMAT,
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_EXISTING_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_SELECTED_NAMES,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_NEW_FRIEND_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_NICKNAME_SUFFIX_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_DATE_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_DATE_FORMAT,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_CUSTOM_ENABLE,
            AutoReplySettings.KEY_AUTO_ACCEPT_REMARK_CUSTOM_TEXT
        )
    }
    val greetAcceptedAutomationKeys = remember {
        AutoReplyFriendAutomationKeys(
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_NEW_FRIEND_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_DATE_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_DATE_FORMAT,
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_EXISTING_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_SELECTED_NAMES,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_NEW_FRIEND_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_NICKNAME_SUFFIX_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_DATE_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_DATE_FORMAT,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_CUSTOM_ENABLE,
            AutoReplySettings.KEY_GREET_ACCEPTED_REMARK_CUSTOM_TEXT
        )
    }

    fun saveRules(next: List<AutoReplyRule>) {
        rules = next
        settings.saveRules(next)
    }

    val autoReplyRoute = if (editingSteps != null) "steps" else route
    SettingsRouteTransition(
        targetState = autoReplyRoute,
        label = "AutoReplyRouteTransition",
        depthOf = { autoReplyRouteDepth(it) }
    ) { currentRoute ->
        when (currentRoute) {
            "steps" -> {
                val request = editingSteps ?: return@SettingsRouteTransition
            AutoReplyStepsPage(
                title = request.first,
                initialSteps = request.second,
                onBack = { editingSteps = null },
                onSave = { steps ->
                    when (route) {
                        "autoAccept" -> settings.saveAutoAcceptSteps(steps)
                        "greetAccepted" -> settings.saveGreetAcceptedSteps(steps)
                        else -> {
                            val id = editingRuleId
                            if (id != null) {
                                val updated = (editingRuleDraft ?: rules.firstOrNull { it.id == id })
                                    ?.copy(steps = steps)
                                if (updated != null) {
                                    editingRuleDraft = updated
                                    saveRules(rules.map { if (it.id == id) updated else it })
                                }
                            }
                        }
                    }
                    Toast.makeText(context, "回复步骤已保存", Toast.LENGTH_SHORT).show()
                    editingSteps = null
                }
            )
            }
        "rules" -> AutoReplyRuleListPage(
            rules = rules,
            query = ruleQuery,
            onQueryChange = { ruleQuery = it },
            listState = ruleListState,
            onBack = { route = "main" },
            onAdd = {
                val rule = AutoReplyRule(
                    id = System.currentTimeMillis().toString(),
                    name = "规则 ${rules.size + 1}"
                )
                saveRules(rules + rule)
                editingRuleId = rule.id
                editingRuleDraft = rule
                route = "ruleEditor"
            },
            onEdit = {
                editingRuleId = it.id
                editingRuleDraft = it
                route = "ruleEditor"
            },
            onToggle = { rule, enabled -> saveRules(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }) },
            onDelete = { rule ->
                saveRules(rules.filterNot { it.id == rule.id })
                Toast.makeText(context, "规则已删除", Toast.LENGTH_SHORT).show()
            },
            onBatchDelete = { targets ->
                val targetIds = targets.mapTo(HashSet()) { it.id }
                saveRules(rules.filterNot { it.id in targetIds })
                Toast.makeText(context, "已删除 ${targets.size} 条规则", Toast.LENGTH_SHORT).show()
            }
        )
        "ruleEditor" -> {
            val rule = editingRuleDraft ?: rules.firstOrNull { it.id == editingRuleId }
            if (rule == null) {
                editingRuleDraft = null
                route = "rules"
            } else {
                AutoReplyRuleEditorPage(
                    rule = rule,
                    listState = ruleEditorListState,
                    onBack = {
                        editingRuleDraft = null
                        route = "rules"
                    },
                    onSave = { saved ->
                        saveRules(rules.map { if (it.id == saved.id) saved else it })
                        editingRuleDraft = null
                        Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
                        route = "rules"
                    },
                    onDraftChange = { editingRuleDraft = it },
                    onEditSteps = { draft ->
                        editingRuleDraft = draft
                        editingSteps = "规则回复" to draft.steps
                    }
                )
            }
        }
        "autoAccept" -> AutoReplyFriendPage(
            context = context,
            title = "好友请求处理",
            sp = sp,
            enabledKey = AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE,
            delayKey = AutoReplySettings.KEY_AUTO_ACCEPT_DELAY_MS,
            tagEnabledKey = AutoReplySettings.KEY_AUTO_ACCEPT_TAG_ENABLE,
            tagNameKey = AutoReplySettings.KEY_AUTO_ACCEPT_TAG_NAME,
            steps = settings.autoAcceptSteps(),
            listState = autoAcceptListState,
            onBack = { route = "main" },
            automationKeys = autoAcceptAutomationKeys,
            onSelectLabels = { route = "autoAcceptLabels" },
            onEditSteps = { editingSteps = "好友请求回复" to settings.autoAcceptSteps() }
        )
        "autoAcceptLabels" -> AutoReplyLabelPickerPage(
            context = context,
            sp = sp,
            selectedKey = AutoReplySettings.KEY_AUTO_ACCEPT_LABEL_SELECTED_NAMES,
            listState = autoAcceptLabelListState,
            onBack = { route = "autoAccept" }
        )
        "greetAccepted" -> AutoReplyFriendPage(
            context = context,
            title = "通过后欢迎语",
            sp = sp,
            enabledKey = AutoReplySettings.KEY_GREET_ACCEPTED_ENABLE,
            delayKey = AutoReplySettings.KEY_GREET_ACCEPTED_DELAY_MS,
            tagEnabledKey = AutoReplySettings.KEY_GREET_ACCEPTED_TAG_ENABLE,
            tagNameKey = AutoReplySettings.KEY_GREET_ACCEPTED_TAG_NAME,
            steps = settings.greetAcceptedSteps(),
            listState = greetAcceptedListState,
            onBack = { route = "main" },
            automationKeys = greetAcceptedAutomationKeys,
            onSelectLabels = { route = "greetAcceptedLabels" },
            onEditSteps = { editingSteps = "通过后欢迎语" to settings.greetAcceptedSteps() }
        )
        "greetAcceptedLabels" -> AutoReplyLabelPickerPage(
            context = context,
            sp = sp,
            selectedKey = AutoReplySettings.KEY_GREET_ACCEPTED_LABEL_SELECTED_NAMES,
            listState = greetAcceptedLabelListState,
            onBack = { route = "greetAccepted" }
        )
        "ai" -> AutoReplyAiPage(context, sp, onBack = { route = "main" })
        else -> AutoReplyMainPage(
            provider = provider,
            sp = sp,
            rules = rules,
            onBack = onBack,
            onOpenRules = { route = "rules" },
            onOpenAutoAccept = { route = "autoAccept" },
            onOpenGreetAccepted = { route = "greetAccepted" },
            onOpenAi = { route = "ai" }
        )
        }
    }
}
