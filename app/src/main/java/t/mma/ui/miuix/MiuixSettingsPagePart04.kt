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

@Composable
internal fun InputHintVariableColorRow(sp: android.content.SharedPreferences, variable: TemplateVariable) {
    val startKey = InputHintSettings.variableColorKey(variable.token, false)
    val endKey = InputHintSettings.variableColorKey(variable.token, true)
    var colorSpec by remember { mutableStateOf(composeColorSpec(sp.getString(startKey, InputHintSettings.DEFAULT_VARIABLE_COLOR) ?: InputHintSettings.DEFAULT_VARIABLE_COLOR, sp.getString(endKey, InputHintSettings.DEFAULT_VARIABLE_COLOR) ?: InputHintSettings.DEFAULT_VARIABLE_COLOR)) }
    ColorPickerRow(variable.label, "${variable.label}的渐变颜色", colorSpec, onReset = { colorSpec = InputHintSettings.DEFAULT_VARIABLE_COLOR; sp.edit().remove(startKey).remove(endKey).apply() }) { value ->
        colorSpec = value.take(19)
        val parts = colorSpecParts(colorSpec)
        sp.edit().putString(startKey, parts.first).putString(endKey, parts.second.ifEmpty { parts.first }).apply()
    }
}
@Composable
internal fun PatBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, PatBlockSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天头像") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        PatBlockSettings.KEY_ENABLE,
                        "禁止拍一拍",
                        "双击聊天头像时不发送拍一拍",
                        PatBlockSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun AutoOriginalImageMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AutoOriginalImageSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天图片") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        AutoOriginalImageSettings.KEY_ENABLE,
                        "自动勾选原图",
                        "进入聊天图片发送界面时自动选择原图",
                        AutoOriginalImageSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun AutoViewOriginalMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AutoViewOriginalSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天媒体") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        AutoViewOriginalSettings.KEY_ENABLE,
                        "自动查看原图",
                        "打开聊天图片或视频时自动查看原图或原视频",
                        AutoViewOriginalSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun RemoveForwardLimitMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RemoveForwardLimitSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "消息转发") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        RemoveForwardLimitSettings.KEY_ENABLE,
                        "移除转发限制",
                        "允许微信原生转发选择超过 9 个会话；大量目标仍受微信发送能力限制",
                        RemoveForwardLimitSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun AtAllNotificationBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AtAllNotificationBlockSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(AtAllNotificationBlockSettings.KEY_ENABLE, AtAllNotificationBlockSettings.DEFAULT_ENABLE))
    }
    var selectedGroups by remember {
        mutableStateOf(AtAllNotificationBlockSettings.parseGroups(sp.getString(AtAllNotificationBlockSettings.KEY_GROUPS, "")))
    }
    var legacyAllGroups by remember { mutableStateOf(enabled && !sp.contains(AtAllNotificationBlockSettings.KEY_GROUPS)) }
    var route by remember { mutableStateOf<AtAllNotificationBlockRoute>(AtAllNotificationBlockRoute.Main) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    SettingsRouteTransition(
        targetState = route,
        label = "AtAllNotificationBlockRoute",
        depthOf = { if (it is AtAllNotificationBlockRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            AtAllNotificationBlockRoute.Main -> PageScaffold(
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
                    item { SmallTitle(text = "群聊通知") }
                    item {
                        SettingsCard {
                            SwitchRow(
                                checked = enabled,
                                title = "屏蔽艾特所有人",
                                summary = "仅拦截所选群聊的艾特所有人通知",
                                onCheckedChange = {
                                    enabled = it
                                    val editor = sp.edit().putBoolean(AtAllNotificationBlockSettings.KEY_ENABLE, it)
                                    if (it && !sp.contains(AtAllNotificationBlockSettings.KEY_GROUPS)) {
                                        editor.putString(AtAllNotificationBlockSettings.KEY_GROUPS, "")
                                        legacyAllGroups = false
                                    }
                                    editor.apply()
                                }
                            )
                            if (enabled) {
                                InsetDivider()
                                ActionRow(
                                    "选择屏蔽群聊",
                                    when {
                                        legacyAllGroups -> "当前屏蔽全部群聊，重新选择后按选择生效"
                                        selectedGroups.isEmpty() -> "未选择群聊，不会屏蔽通知"
                                        else -> "已选择 ${selectedGroups.size} 个群聊"
                                    }
                                ) {
                                    route = AtAllNotificationBlockRoute.GroupPicker(
                                        ContactPickerRequest(
                                            title = "选择屏蔽群聊",
                                            mode = ContactPickerMode.GROUPS,
                                            multiSelect = true,
                                            existingValue = formatIds(selectedGroups),
                                            onValue = {}
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is AtAllNotificationBlockRoute.GroupPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = AtAllNotificationBlockRoute.Main },
                onConfirm = { picked ->
                    selectedGroups = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    legacyAllGroups = false
                    sp.edit()
                        .putString(AtAllNotificationBlockSettings.KEY_GROUPS, formatIds(selectedGroups))
                        .apply()
                    route = AtAllNotificationBlockRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun DisableHotUpdateMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, DisableHotUpdateSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "热更新") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        DisableHotUpdateSettings.KEY_ENABLE,
                        "屏蔽热更新",
                        "阻止微信加载和应用热更新补丁",
                        DisableHotUpdateSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun MomentsAutoLikeMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsAutoLikeSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsAutoLikeRoute>(MomentsAutoLikeRoute.Main) }
    var whitelist by remember { mutableStateOf(parseIds(sp.getString(MomentsAutoLikeSettings.KEY_WHITELIST, "").orEmpty())) }
    var blacklist by remember { mutableStateOf(parseIds(sp.getString(MomentsAutoLikeSettings.KEY_BLACKLIST, "").orEmpty())) }

    SettingsRouteTransition(
        targetState = route,
        label = "MomentsAutoLikeRoute",
        depthOf = { if (it is MomentsAutoLikeRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsAutoLikeRoute.Main -> MomentsAutoLikeMainPage(
                provider = provider,
                sp = sp,
                whitelist = whitelist,
                blacklist = blacklist,
                onBack = onBack,
                onPickTargets = { listMode ->
                    val selected = if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) blacklist else whitelist
                    route = MomentsAutoLikeRoute.ContactPicker(
                        listMode,
                        ContactPickerRequest(
                            title = if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) "选择点赞黑名单" else "选择点赞白名单",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(selected),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is MomentsAutoLikeRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsAutoLikeRoute.Main },
                onConfirm = { picked ->
                    val ids = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    if (current.listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) {
                        blacklist = ids
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_BLACKLIST, formatIds(ids)).apply()
                    } else {
                        whitelist = ids
                        sp.edit().putString(MomentsAutoLikeSettings.KEY_WHITELIST, formatIds(ids)).apply()
                    }
                    route = MomentsAutoLikeRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun MomentsAutoLikeMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    whitelist: Set<String>,
    blacklist: Set<String>,
    onBack: () -> Unit,
    onPickTargets: (Int) -> Unit
) {
    var enabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ENABLE, false)) }
    var likeSelf by remember {
        mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_LIKE_SELF, MomentsAutoLikeSettings.DEFAULT_LIKE_SELF))
    }
    var listMode by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_LIST_MODE, MomentsAutoLikeSettings.DEFAULT_LIST_MODE))
    }
    var dailyLikeLimit by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoLikeSettings.KEY_DAILY_LIKE_LIMIT,
                MomentsAutoLikeSettings.DEFAULT_DAILY_LIKE_LIMIT
            ).toString()
        )
    }
    var delayMode by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_DELAY_MODE, MomentsAutoLikeSettings.DEFAULT_DELAY_MODE))
    }
    var fixedDelay by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_FIXED_DELAY_SECONDS, MomentsAutoLikeSettings.DEFAULT_FIXED_DELAY_SECONDS).toString())
    }
    var randomMin by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_RANDOM_MIN_SECONDS, MomentsAutoLikeSettings.DEFAULT_RANDOM_MIN_SECONDS).toString())
    }
    var randomMax by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_RANDOM_MAX_SECONDS, MomentsAutoLikeSettings.DEFAULT_RANDOM_MAX_SECONDS).toString())
    }
    var timeWindow by remember {
        mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_TIME_WINDOW_ENABLE, MomentsAutoLikeSettings.DEFAULT_TIME_WINDOW_ENABLE))
    }
    var startTime by remember {
        mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_START_TIME, MomentsAutoLikeSettings.DEFAULT_START_TIME).orEmpty())
    }
    var endTime by remember {
        mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_END_TIME, MomentsAutoLikeSettings.DEFAULT_END_TIME).orEmpty())
    }
    var maxAge by remember {
        mutableStateOf(sp.getInt(MomentsAutoLikeSettings.KEY_MAX_AGE_HOURS, MomentsAutoLikeSettings.DEFAULT_MAX_AGE_HOURS).toString())
    }
    var allowText by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_TEXT, true)) }
    var allowImage by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_IMAGE, true)) }
    var allowVideo by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_VIDEO, true)) }
    var allowLink by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_LINK, false)) }
    var allowMusic by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_MUSIC, false)) }
    var allowOther by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_OTHER, false)) }
    var allowUnknown by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_ALLOW_UNKNOWN, false)) }
    var keywordsText by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_KEYWORDS_TEXT, "").orEmpty()) }
    var keywordsImageText by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_KEYWORDS_IMAGE_TEXT, "").orEmpty()) }
    var keywordsVideoText by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_KEYWORDS_VIDEO_TEXT, "").orEmpty()) }
    var keywordsCardText by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_KEYWORDS_CARD_TEXT, "").orEmpty()) }
    var keywordText by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_TEXT, true)) }
    var keywordImage by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_IMAGE, true)) }
    var keywordVideo by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_VIDEO, true)) }
    var keywordCard by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_CARD, true)) }
    var logEnabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoLikeSettings.KEY_LOG_ENABLE, false)) }
    var logs by remember { mutableStateOf(sp.getString(MomentsAutoLikeSettings.KEY_LOGS, "").orEmpty()) }
    val list = if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) blacklist else whitelist
    val listSummary = when {
        listMode == MomentsAutoLikeSettings.LIST_BLACKLIST && list.isEmpty() -> "未排除好友，将匹配全部好友"
        listMode == MomentsAutoLikeSettings.LIST_WHITELIST && list.isEmpty() -> "未选择好友，自动点赞不会执行"
        else -> "已选择 ${list.size} 位好友"
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
            item { SmallTitle(text = "自动点赞") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈自动点赞", "按下方规则处理新获取的朋友圈") {
                        enabled = it
                        sp.edit()
                            .putBoolean(MomentsAutoLikeSettings.KEY_ENABLE, it)
                            .putLong(
                                MomentsAutoLikeSettings.KEY_ENABLED_AT_SECONDS,
                                if (it) System.currentTimeMillis() / 1000L else 0L
                            )
                            .apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        SwitchRow(likeSelf, "点赞自己的朋友圈", "自己的朋友圈不受好友名单限制") {
                            likeSelf = it
                            sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_LIKE_SELF, it).apply()
                        }
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "好友范围") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "名单模式",
                            summary = if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) "除黑名单外均可点赞" else "只点赞白名单好友",
                            options = optionItems(
                                "白名单" to MomentsAutoLikeSettings.LIST_WHITELIST,
                                "黑名单" to MomentsAutoLikeSettings.LIST_BLACKLIST
                            ),
                            currentValue = listMode,
                            onValueChanged = {
                                listMode = it
                                sp.edit().putInt(MomentsAutoLikeSettings.KEY_LIST_MODE, it).apply()
                            }
                        )
                        InsetDivider()
                        ActionRow(if (listMode == MomentsAutoLikeSettings.LIST_BLACKLIST) "黑名单" else "白名单", listSummary) {
                            onPickTargets(listMode)
                        }
                        InsetDivider()
                        NumberInputRow("同一人每天点赞数量", "每天最多点赞同一人的朋友圈条数，0 表示不限制", dailyLikeLimit) {
                            dailyLikeLimit = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit()
                                    .putInt(MomentsAutoLikeSettings.KEY_DAILY_LIKE_LIMIT, value.coerceAtLeast(0))
                                    .apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "执行时间") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "点赞延迟",
                            summary = if (delayMode == MomentsAutoLikeSettings.DELAY_RANDOM) "在范围内随机等待" else "每条等待固定时间",
                            options = optionItems(
                                "固定延迟" to MomentsAutoLikeSettings.DELAY_FIXED,
                                "随机延迟" to MomentsAutoLikeSettings.DELAY_RANDOM
                            ),
                            currentValue = delayMode,
                            onValueChanged = {
                                delayMode = it
                                sp.edit().putInt(MomentsAutoLikeSettings.KEY_DELAY_MODE, it).apply()
                            }
                        )
                        if (delayMode == MomentsAutoLikeSettings.DELAY_FIXED) {
                            InsetDivider()
                            NumberInputRow("等待时间", "单位秒，最少 0 秒", fixedDelay) {
                                fixedDelay = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoLikeSettings.KEY_FIXED_DELAY_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                        } else {
                            InsetDivider()
                            NumberInputRow("最短等待", "单位秒，最少 0 秒", randomMin) {
                                randomMin = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoLikeSettings.KEY_RANDOM_MIN_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                            InsetDivider()
                            NumberInputRow("最长等待", "单位秒，不能小于最短等待", randomMax) {
                                randomMax = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoLikeSettings.KEY_RANDOM_MAX_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                        }
                        InsetDivider()
                        SwitchRow(timeWindow, "限制运行时段", "支持跨零点时段") {
                            timeWindow = it
                            sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_TIME_WINDOW_ENABLE, it).apply()
                        }
                        if (timeWindow) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", startTime) {
                                startTime = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_START_TIME, it).apply()
                            }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", endTime) {
                                endTime = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_END_TIME, it).apply()
                            }
                        }
                        InsetDivider()
                        NumberInputRow("发布时间限制", "仅点赞发布后指定小时内的朋友圈，最少 1 小时", maxAge) {
                            maxAge = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit().putInt(MomentsAutoLikeSettings.KEY_MAX_AGE_HOURS, value.coerceAtLeast(1)).apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容过滤") }
                item {
                    SettingsCard {
                        SwitchRow(allowText, "文字朋友圈", "允许自动点赞") {
                            allowText = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_TEXT, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowImage, "图片/图文朋友圈", "允许自动点赞") {
                            allowImage = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_IMAGE, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowVideo, "视频/视文朋友圈", "允许自动点赞") {
                            allowVideo = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_VIDEO, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowLink, "网页/链接朋友圈", "允许自动点赞") {
                            allowLink = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_LINK, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowMusic, "音乐朋友圈", "允许自动点赞") {
                            allowMusic = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_MUSIC, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowOther, "其他卡片朋友圈", "允许自动点赞") {
                            allowOther = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_OTHER, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowUnknown, "未知类型朋友圈", "允许自动点赞，默认关闭") {
                            allowUnknown = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_ALLOW_UNKNOWN, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(keywordText, "文字朋友圈关键词", "开启后按关键词排除") {
                            keywordText = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_TEXT, it).apply()
                        }
                        if (keywordText) {
                            InsetDivider()
                            InputRow("文字排除关键词", "多个关键词用逗号或换行分隔", keywordsText, minLines = 2) {
                                keywordsText = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordImage, "图文朋友圈关键词", "开启后按关键词排除") {
                            keywordImage = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_IMAGE, it).apply()
                        }
                        if (keywordImage) {
                            InsetDivider()
                            InputRow("图文排除关键词", "多个关键词用逗号或换行分隔", keywordsImageText, minLines = 2) {
                                keywordsImageText = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_IMAGE_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordVideo, "视文朋友圈关键词", "开启后按关键词排除") {
                            keywordVideo = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_VIDEO, it).apply()
                        }
                        if (keywordVideo) {
                            InsetDivider()
                            InputRow("视文排除关键词", "多个关键词用逗号或换行分隔", keywordsVideoText, minLines = 2) {
                                keywordsVideoText = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_VIDEO_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordCard, "链接/卡片朋友圈关键词", "开启后按关键词排除") {
                            keywordCard = it; sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_KEYWORD_CARD, it).apply()
                        }
                        if (keywordCard) {
                            InsetDivider()
                            InputRow("链接/卡片排除关键词", "多个关键词用逗号或换行分隔", keywordsCardText, minLines = 2) {
                                keywordsCardText = it
                                sp.edit().putString(MomentsAutoLikeSettings.KEY_KEYWORDS_CARD_TEXT, it).apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行记录") }
                item {
                    SettingsCard {
                        SwitchRow(logEnabled, "记录运行日志", "只保留最近 200 条") {
                            logEnabled = it
                            sp.edit().putBoolean(MomentsAutoLikeSettings.KEY_LOG_ENABLE, it).apply()
                        }
                        if (logEnabled) {
                            InsetDivider()
                            ActionRow("刷新日志", if (logs.isBlank()) "暂无记录" else "${logs.lineSequence().count()} 条记录") {
                                logs = sp.getString(MomentsAutoLikeSettings.KEY_LOGS, "").orEmpty()
                            }
                            if (logs.isNotBlank()) {
                                InsetDivider()
                                Text(
                                    text = logs.take(5000),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                                InsetDivider()
                                ActionRow("清空日志", "删除当前自动点赞运行记录") {
                                    sp.edit().remove(MomentsAutoLikeSettings.KEY_LOGS).apply()
                                    logs = ""
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal object MomentsAutoCommentPage {
    @Composable
    operator fun invoke(
        context: Context,
        provider: FeatureSettingsProvider,
        onBack: () -> Unit
    ) {
    val sp = remember { HchatStorage.preferences(context, MomentsAutoCommentSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsAutoCommentRoute>(MomentsAutoCommentRoute.Main) }
    var whitelist by remember { mutableStateOf(parseIds(sp.getString(MomentsAutoCommentSettings.KEY_WHITELIST, "").orEmpty())) }
    var blacklist by remember { mutableStateOf(parseIds(sp.getString(MomentsAutoCommentSettings.KEY_BLACKLIST, "").orEmpty())) }

    SettingsRouteTransition(
        targetState = route,
        label = "MomentsAutoCommentRoute",
        depthOf = { if (it is MomentsAutoCommentRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsAutoCommentRoute.Main -> MainPage(
                provider = provider,
                sp = sp,
                whitelist = whitelist,
                blacklist = blacklist,
                onBack = onBack,
                onPickTargets = { listMode ->
                    val selected = if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) blacklist else whitelist
                    route = MomentsAutoCommentRoute.ContactPicker(
                        listMode,
                        ContactPickerRequest(
                            title = if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) "选择评论黑名单" else "选择评论白名单",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(selected),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is MomentsAutoCommentRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsAutoCommentRoute.Main },
                onConfirm = { picked ->
                    val ids = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    if (current.listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) {
                        blacklist = ids
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_BLACKLIST, formatIds(ids)).apply()
                    } else {
                        whitelist = ids
                        sp.edit().putString(MomentsAutoCommentSettings.KEY_WHITELIST, formatIds(ids)).apply()
                    }
                    route = MomentsAutoCommentRoute.Main
                }
            )
        }
    }
    }

    @Composable
    private fun MainPage(
        provider: FeatureSettingsProvider,
        sp: SharedPreferences,
        whitelist: Set<String>,
        blacklist: Set<String>,
        onBack: () -> Unit,
        onPickTargets: (Int) -> Unit
    ) {
    var enabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ENABLE, false)) }
    var commentContent by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoCommentSettings.KEY_COMMENT_CONTENT,
                MomentsAutoCommentSettings.DEFAULT_COMMENT_CONTENT
            ).orEmpty()
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoCommentSettings.KEY_TIME_FORMAT,
                MomentsAutoCommentSettings.DEFAULT_TIME_FORMAT
            ).orEmpty()
        )
    }
    var commentSelf by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoCommentSettings.KEY_COMMENT_SELF,
                MomentsAutoCommentSettings.DEFAULT_COMMENT_SELF
            )
        )
    }
    var listMode by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_LIST_MODE, MomentsAutoCommentSettings.DEFAULT_LIST_MODE))
    }
    var dailyCommentLimit by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoCommentSettings.KEY_DAILY_COMMENT_LIMIT,
                MomentsAutoCommentSettings.DEFAULT_DAILY_COMMENT_LIMIT
            ).toString()
        )
    }
    var delayMode by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_DELAY_MODE, MomentsAutoCommentSettings.DEFAULT_DELAY_MODE))
    }
    var fixedDelay by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_FIXED_DELAY_SECONDS, MomentsAutoCommentSettings.DEFAULT_FIXED_DELAY_SECONDS).toString())
    }
    var randomMin by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_RANDOM_MIN_SECONDS, MomentsAutoCommentSettings.DEFAULT_RANDOM_MIN_SECONDS).toString())
    }
    var randomMax by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_RANDOM_MAX_SECONDS, MomentsAutoCommentSettings.DEFAULT_RANDOM_MAX_SECONDS).toString())
    }
    var timeWindow by remember {
        mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_TIME_WINDOW_ENABLE, MomentsAutoCommentSettings.DEFAULT_TIME_WINDOW_ENABLE))
    }
    var startTime by remember {
        mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_START_TIME, MomentsAutoCommentSettings.DEFAULT_START_TIME).orEmpty())
    }
    var endTime by remember {
        mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_END_TIME, MomentsAutoCommentSettings.DEFAULT_END_TIME).orEmpty())
    }
    var maxAge by remember {
        mutableStateOf(sp.getInt(MomentsAutoCommentSettings.KEY_MAX_AGE_HOURS, MomentsAutoCommentSettings.DEFAULT_MAX_AGE_HOURS).toString())
    }
    var allowText by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_TEXT, true)) }
    var allowImage by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_IMAGE, true)) }
    var allowVideo by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_VIDEO, true)) }
    var allowLink by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_LINK, false)) }
    var allowMusic by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_MUSIC, false)) }
    var allowOther by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_OTHER, false)) }
    var allowUnknown by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_ALLOW_UNKNOWN, false)) }
    var keywordsText by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_KEYWORDS_TEXT, "").orEmpty()) }
    var keywordsImageText by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_KEYWORDS_IMAGE_TEXT, "").orEmpty()) }
    var keywordsVideoText by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_KEYWORDS_VIDEO_TEXT, "").orEmpty()) }
    var keywordsCardText by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_KEYWORDS_CARD_TEXT, "").orEmpty()) }
    var keywordText by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_TEXT, true)) }
    var keywordImage by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_IMAGE, true)) }
    var keywordVideo by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_VIDEO, true)) }
    var keywordCard by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_CARD, true)) }
    var logEnabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoCommentSettings.KEY_LOG_ENABLE, false)) }
    var logs by remember { mutableStateOf(sp.getString(MomentsAutoCommentSettings.KEY_LOGS, "").orEmpty()) }
    val list = if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) blacklist else whitelist
    val listSummary = when {
        listMode == MomentsAutoCommentSettings.LIST_BLACKLIST && list.isEmpty() -> "未排除好友，将匹配全部好友"
        listMode == MomentsAutoCommentSettings.LIST_WHITELIST && list.isEmpty() -> "未选择好友，自动评论不会执行"
        else -> "已选择 ${list.size} 位好友"
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
            item { SmallTitle(text = "自动评论") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈自动评论", "按下方规则处理新获取的朋友圈") {
                        enabled = it
                        sp.edit()
                            .putBoolean(MomentsAutoCommentSettings.KEY_ENABLE, it)
                            .putLong(
                                MomentsAutoCommentSettings.KEY_ENABLED_AT_SECONDS,
                                if (it) System.currentTimeMillis() / 1000L else 0L
                            )
                            .apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "评论内容",
                            summary = if (commentContent.isBlank()) "需要填写" else "点击下方中文变量插入到光标位置",
                            value = commentContent,
                            variables = momentsAutoCommentTemplateVariables,
                            minLines = 3
                        ) {
                            commentContent = it
                            sp.edit().putString(MomentsAutoCommentSettings.KEY_COMMENT_CONTENT, it).apply()
                        }
                        if (commentContent.contains(MomentsAutoCommentSettings.VAR_TIME)) {
                            InsetDivider()
                            InputRow(
                                title = "时间变量格式",
                                summary = if (MomentsAutoCommentSettings.isValidTimeFormat(timeFormat)) {
                                    "例如 HH:mm:ss 或 yyyy-MM-dd HH:mm:ss"
                                } else {
                                    "格式无效，当前输入不会保存"
                                },
                                value = timeFormat
                            ) {
                                timeFormat = it
                                if (MomentsAutoCommentSettings.isValidTimeFormat(it)) {
                                    sp.edit().putString(
                                        MomentsAutoCommentSettings.KEY_TIME_FORMAT,
                                        MomentsAutoCommentSettings.normalizeTimeFormat(it)
                                    ).apply()
                                }
                            }
                        }
                        InsetDivider()
                        SwitchRow(commentSelf, "评论自己的朋友圈", "自己的朋友圈不受好友名单限制") {
                            commentSelf = it
                            sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_COMMENT_SELF, it).apply()
                        }
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "好友范围") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "名单模式",
                            summary = if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) "除黑名单外均可评论" else "只评论白名单好友",
                            options = optionItems(
                                "白名单" to MomentsAutoCommentSettings.LIST_WHITELIST,
                                "黑名单" to MomentsAutoCommentSettings.LIST_BLACKLIST
                            ),
                            currentValue = listMode,
                            onValueChanged = {
                                listMode = it
                                sp.edit().putInt(MomentsAutoCommentSettings.KEY_LIST_MODE, it).apply()
                            }
                        )
                        InsetDivider()
                        ActionRow(if (listMode == MomentsAutoCommentSettings.LIST_BLACKLIST) "黑名单" else "白名单", listSummary) {
                            onPickTargets(listMode)
                        }
                        InsetDivider()
                        NumberInputRow("同一人每天评论数量", "每天最多评论同一人的朋友圈条数，0 表示不限制", dailyCommentLimit) {
                            dailyCommentLimit = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit()
                                    .putInt(MomentsAutoCommentSettings.KEY_DAILY_COMMENT_LIMIT, value.coerceAtLeast(0))
                                    .apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "执行时间") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "评论延迟",
                            summary = if (delayMode == MomentsAutoCommentSettings.DELAY_RANDOM) "在范围内随机等待" else "每条等待固定时间",
                            options = optionItems(
                                "固定延迟" to MomentsAutoCommentSettings.DELAY_FIXED,
                                "随机延迟" to MomentsAutoCommentSettings.DELAY_RANDOM
                            ),
                            currentValue = delayMode,
                            onValueChanged = {
                                delayMode = it
                                sp.edit().putInt(MomentsAutoCommentSettings.KEY_DELAY_MODE, it).apply()
                            }
                        )
                        if (delayMode == MomentsAutoCommentSettings.DELAY_FIXED) {
                            InsetDivider()
                            NumberInputRow("等待时间", "单位秒，最少 0 秒", fixedDelay) {
                                fixedDelay = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoCommentSettings.KEY_FIXED_DELAY_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                        } else {
                            InsetDivider()
                            NumberInputRow("最短等待", "单位秒，最少 0 秒", randomMin) {
                                randomMin = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoCommentSettings.KEY_RANDOM_MIN_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                            InsetDivider()
                            NumberInputRow("最长等待", "单位秒，不能小于最短等待", randomMax) {
                                randomMax = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit().putInt(MomentsAutoCommentSettings.KEY_RANDOM_MAX_SECONDS, value.coerceAtLeast(0)).apply()
                                }
                            }
                        }
                        InsetDivider()
                        SwitchRow(timeWindow, "限制运行时段", "支持跨零点时段") {
                            timeWindow = it
                            sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_TIME_WINDOW_ENABLE, it).apply()
                        }
                        if (timeWindow) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", startTime) {
                                startTime = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_START_TIME, it).apply()
                            }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", endTime) {
                                endTime = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_END_TIME, it).apply()
                            }
                        }
                        InsetDivider()
                        NumberInputRow("发布时间限制", "仅评论发布后指定小时内的朋友圈，最少 1 小时", maxAge) {
                            maxAge = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit().putInt(MomentsAutoCommentSettings.KEY_MAX_AGE_HOURS, value.coerceAtLeast(1)).apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容过滤") }
                item {
                    SettingsCard {
                        SwitchRow(allowText, "文字朋友圈", "允许自动评论") {
                            allowText = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_TEXT, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowImage, "图片/图文朋友圈", "允许自动评论") {
                            allowImage = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_IMAGE, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowVideo, "视频/视文朋友圈", "允许自动评论") {
                            allowVideo = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_VIDEO, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowLink, "网页/链接朋友圈", "允许自动评论") {
                            allowLink = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_LINK, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowMusic, "音乐朋友圈", "允许自动评论") {
                            allowMusic = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_MUSIC, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowOther, "其他卡片朋友圈", "允许自动评论") {
                            allowOther = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_OTHER, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowUnknown, "未知类型朋友圈", "允许自动评论，默认关闭") {
                            allowUnknown = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_ALLOW_UNKNOWN, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(keywordText, "文字朋友圈关键词", "开启后按关键词排除") {
                            keywordText = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_TEXT, it).apply()
                        }
                        if (keywordText) {
                            InsetDivider()
                            InputRow("文字排除关键词", "多个关键词用逗号或换行分隔", keywordsText, minLines = 2) {
                                keywordsText = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordImage, "图文朋友圈关键词", "开启后按关键词排除") {
                            keywordImage = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_IMAGE, it).apply()
                        }
                        if (keywordImage) {
                            InsetDivider()
                            InputRow("图文排除关键词", "多个关键词用逗号或换行分隔", keywordsImageText, minLines = 2) {
                                keywordsImageText = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_IMAGE_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordVideo, "视文朋友圈关键词", "开启后按关键词排除") {
                            keywordVideo = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_VIDEO, it).apply()
                        }
                        if (keywordVideo) {
                            InsetDivider()
                            InputRow("视文排除关键词", "多个关键词用逗号或换行分隔", keywordsVideoText, minLines = 2) {
                                keywordsVideoText = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_VIDEO_TEXT, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(keywordCard, "链接/卡片朋友圈关键词", "开启后按关键词排除") {
                            keywordCard = it; sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_KEYWORD_CARD, it).apply()
                        }
                        if (keywordCard) {
                            InsetDivider()
                            InputRow("链接/卡片排除关键词", "多个关键词用逗号或换行分隔", keywordsCardText, minLines = 2) {
                                keywordsCardText = it
                                sp.edit().putString(MomentsAutoCommentSettings.KEY_KEYWORDS_CARD_TEXT, it).apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行记录") }
                item {
                    SettingsCard {
                        SwitchRow(logEnabled, "记录运行日志", "只保留最近 200 条") {
                            logEnabled = it
                            sp.edit().putBoolean(MomentsAutoCommentSettings.KEY_LOG_ENABLE, it).apply()
                        }
                        if (logEnabled) {
                            InsetDivider()
                            ActionRow("刷新日志", if (logs.isBlank()) "暂无记录" else "${logs.lineSequence().count()} 条记录") {
                                logs = sp.getString(MomentsAutoCommentSettings.KEY_LOGS, "").orEmpty()
                            }
                            if (logs.isNotBlank()) {
                                InsetDivider()
                                Text(
                                    text = logs.take(5000),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                                InsetDivider()
                                ActionRow("清空日志", "删除当前自动评论运行记录") {
                                    sp.edit().remove(MomentsAutoCommentSettings.KEY_LOGS).apply()
                                    logs = ""
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun MomentsAutoForwardMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsAutoForwardSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsAutoForwardRoute>(MomentsAutoForwardRoute.Main) }
    var targets by remember {
        mutableStateOf(
            parseIds(
                sp.getString(
                    MomentsAutoForwardSettings.KEY_TARGETS,
                    MomentsAutoForwardSettings.DEFAULT_TARGETS
                ).orEmpty()
            )
        )
    }
    var replacementRules by remember {
        mutableStateOf(
            MomentsAutoForwardSettings.decodeKeywordReplacements(
                sp.getString(
                    MomentsAutoForwardSettings.KEY_KEYWORD_REPLACEMENTS,
                    MomentsAutoForwardSettings.DEFAULT_KEYWORD_REPLACEMENTS
                )
            )
        )
    }
    val mainListState = rememberLazyListState()

    SettingsRouteTransition(
        targetState = route,
        label = "MomentsAutoForwardRoute",
        depthOf = { if (it is MomentsAutoForwardRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsAutoForwardRoute.Main -> MomentsAutoForwardMainPage(
                provider = provider,
                sp = sp,
                listState = mainListState,
                targetCount = targets.size,
                replacementRuleCount = replacementRules.size,
                onBack = onBack,
                onOpenReplacementRules = { route = MomentsAutoForwardRoute.ReplacementRules },
                onPickTargets = {
                    route = MomentsAutoForwardRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "选择转发好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(targets),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is MomentsAutoForwardRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsAutoForwardRoute.Main },
                onConfirm = { picked ->
                    targets = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    sp.edit().putString(MomentsAutoForwardSettings.KEY_TARGETS, formatIds(targets)).apply()
                    route = MomentsAutoForwardRoute.Main
                }
            )
            MomentsAutoForwardRoute.ReplacementRules -> KeywordReplacementRulesPage(
                context = context,
                initialRules = replacementRules,
                onBack = { route = MomentsAutoForwardRoute.Main },
                onSave = { updated ->
                    replacementRules = updated
                    sp.edit()
                        .putString(
                            MomentsAutoForwardSettings.KEY_KEYWORD_REPLACEMENTS,
                            MomentsAutoForwardSettings.encodeKeywordReplacements(updated)
                        )
                        .apply()
                    Toast.makeText(context, "替换规则已保存", Toast.LENGTH_SHORT).show()
                    route = MomentsAutoForwardRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun MomentsAutoForwardMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    listState: LazyListState,
    targetCount: Int,
    replacementRuleCount: Int,
    onBack: () -> Unit,
    onOpenReplacementRules: () -> Unit,
    onPickTargets: () -> Unit
) {
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ENABLE,
                MomentsAutoForwardSettings.DEFAULT_ENABLE
            )
        )
    }
    var allowText by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_TEXT,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_TEXT
            )
        )
    }
    var allowImage by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_IMAGE,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_IMAGE
            )
        )
    }
    var allowVideo by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_VIDEO,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_VIDEO
            )
        )
    }
    var allowLivePhoto by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_LIVE_PHOTO,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_LIVE_PHOTO
            )
        )
    }
    var allowLink by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_LINK,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_LINK
            )
        )
    }
    var allowMusic by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_MUSIC,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_MUSIC
            )
        )
    }
    var allowOther by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_OTHER,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_OTHER
            )
        )
    }
    var allowUnknown by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_ALLOW_UNKNOWN,
                MomentsAutoForwardSettings.DEFAULT_ALLOW_UNKNOWN
            )
        )
    }
    var delayMode by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_DELAY_MODE,
                MomentsAutoForwardSettings.DEFAULT_DELAY_MODE
            )
        )
    }
    var fixedDelay by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_FIXED_DELAY_SECONDS,
                MomentsAutoForwardSettings.DEFAULT_FIXED_DELAY_SECONDS
            ).toString()
        )
    }
    var randomMin by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_RANDOM_MIN_SECONDS,
                MomentsAutoForwardSettings.DEFAULT_RANDOM_MIN_SECONDS
            ).toString()
        )
    }
    var randomMax by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_RANDOM_MAX_SECONDS,
                MomentsAutoForwardSettings.DEFAULT_RANDOM_MAX_SECONDS
            ).toString()
        )
    }
    var dailyLimit by remember {
        mutableStateOf(
            sp.getInt(
                MomentsAutoForwardSettings.KEY_DAILY_LIMIT,
                MomentsAutoForwardSettings.DEFAULT_DAILY_LIMIT
            ).toString()
        )
    }
    var includeKeywords by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoForwardSettings.KEY_INCLUDE_KEYWORDS,
                MomentsAutoForwardSettings.DEFAULT_INCLUDE_KEYWORDS
            ).orEmpty()
        )
    }
    var includeKeywordsEnabled by remember {
        mutableStateOf(MomentsAutoForwardSettings.includeKeywordsEnabled(sp))
    }
    var excludeKeywords by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoForwardSettings.KEY_EXCLUDE_KEYWORDS,
                MomentsAutoForwardSettings.DEFAULT_EXCLUDE_KEYWORDS
            ).orEmpty()
        )
    }
    var excludeKeywordsEnabled by remember {
        mutableStateOf(MomentsAutoForwardSettings.excludeKeywordsEnabled(sp))
    }
    var replaceKeywordsEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_REPLACE_KEYWORDS_ENABLE,
                MomentsAutoForwardSettings.DEFAULT_REPLACE_KEYWORDS_ENABLE
            )
        )
    }
    var contentTemplate by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoForwardSettings.KEY_CONTENT_TEMPLATE,
                MomentsAutoForwardSettings.DEFAULT_CONTENT_TEMPLATE
            ).orEmpty()
        )
    }
    var logEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsAutoForwardSettings.KEY_LOG_ENABLE,
                MomentsAutoForwardSettings.DEFAULT_LOG_ENABLE
            )
        )
    }
    var logs by remember {
        mutableStateOf(
            sp.getString(
                MomentsAutoForwardSettings.KEY_LOGS,
                MomentsAutoForwardSettings.DEFAULT_LOGS
            ).orEmpty()
        )
    }
    val templateVariables = remember {
        listOf(
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_CONTENT, "原文"),
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_SENDER, "发布者"),
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_WXID, "发布者ID"),
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_TYPE, "类型"),
            TemplateVariable(MomentsAutoForwardSettings.VARIABLE_SNS_ID, "朋友圈ID")
        )
    }
    val targetSummary = if (targetCount == 0) {
        "未选择好友，自动转发不会执行"
    } else {
        "已选择 $targetCount 位好友"
    }
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
            item { SmallTitle(text = "自动转发") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈自动转发", "按规则静默转发指定好友的新朋友圈") {
                        enabled = it
                        sp.edit()
                            .putBoolean(MomentsAutoForwardSettings.KEY_ENABLE, it)
                            .putLong(
                                MomentsAutoForwardSettings.KEY_ENABLED_AT_SECONDS,
                                if (it) System.currentTimeMillis() / 1000L else 0L
                            )
                            .apply()
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "转发好友") }
                item {
                    SettingsCard {
                        ActionRow("转发好友", targetSummary, onPickTargets)
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容类型") }
                item {
                    SettingsCard {
                        SwitchRow(allowText, "文字", "允许自动转发") {
                            allowText = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_TEXT, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowImage, "图片/图文", "允许自动转发") {
                            allowImage = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_IMAGE, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowVideo, "视频/视文", "允许自动转发") {
                            allowVideo = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_VIDEO, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowLivePhoto, "实况照片", "允许自动转发") {
                            allowLivePhoto = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_LIVE_PHOTO, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowLink, "网页/链接", "按正文和链接转为文字朋友圈") {
                            allowLink = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_LINK, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowMusic, "音乐", "按正文和链接转为文字朋友圈") {
                            allowMusic = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_MUSIC, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowOther, "其他卡片", "按可解析内容转为文字朋友圈") {
                            allowOther = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_OTHER, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(allowUnknown, "未知类型", "按可解析内容转发，默认关闭") {
                            allowUnknown = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_ALLOW_UNKNOWN, it).apply()
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "执行方式") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "转发延迟",
                            summary = if (delayMode == MomentsAutoForwardSettings.DELAY_RANDOM) {
                                "在范围内随机等待"
                            } else {
                                "每条等待固定时间"
                            },
                            options = optionItems(
                                "固定延迟" to MomentsAutoForwardSettings.DELAY_FIXED,
                                "随机延迟" to MomentsAutoForwardSettings.DELAY_RANDOM
                            ),
                            currentValue = delayMode,
                            onValueChanged = {
                                delayMode = it
                                sp.edit().putInt(MomentsAutoForwardSettings.KEY_DELAY_MODE, it).apply()
                            }
                        )
                        if (delayMode == MomentsAutoForwardSettings.DELAY_FIXED) {
                            InsetDivider()
                            NumberInputRow("等待时间", "单位秒，最少 0 秒", fixedDelay) {
                                fixedDelay = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit()
                                        .putInt(
                                            MomentsAutoForwardSettings.KEY_FIXED_DELAY_SECONDS,
                                            value.coerceAtLeast(0)
                                        )
                                        .apply()
                                }
                            }
                        } else {
                            InsetDivider()
                            NumberInputRow("最短等待", "单位秒，最少 0 秒", randomMin) {
                                randomMin = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit()
                                        .putInt(
                                            MomentsAutoForwardSettings.KEY_RANDOM_MIN_SECONDS,
                                            value.coerceAtLeast(0)
                                        )
                                        .apply()
                                }
                            }
                            InsetDivider()
                            NumberInputRow("最长等待", "单位秒，不能小于最短等待", randomMax) {
                                randomMax = it
                                it.toIntOrNull()?.let { value ->
                                    sp.edit()
                                        .putInt(
                                            MomentsAutoForwardSettings.KEY_RANDOM_MAX_SECONDS,
                                            value.coerceAtLeast(0)
                                        )
                                        .apply()
                                }
                            }
                        }
                        InsetDivider()
                        NumberInputRow("每日转发上限", "每天最多转发的朋友圈数量，0 表示不限制", dailyLimit) {
                            dailyLimit = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit()
                                    .putInt(MomentsAutoForwardSettings.KEY_DAILY_LIMIT, value.coerceAtLeast(0))
                                    .apply()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容处理") }
                item {
                    SettingsCard {
                        SwitchRow(
                            includeKeywordsEnabled,
                            "包含关键词筛选",
                            "开启后，正文需命中任意关键词才转发"
                        ) {
                            includeKeywordsEnabled = it
                            sp.edit()
                                .putBoolean(MomentsAutoForwardSettings.KEY_INCLUDE_KEYWORDS_ENABLE, it)
                                .apply()
                        }
                        if (includeKeywordsEnabled) {
                            InsetDivider()
                            InputRow(
                                "包含关键词",
                                "多个关键词用逗号或换行分隔",
                                includeKeywords,
                                minLines = 2
                            ) {
                                includeKeywords = it
                                sp.edit().putString(MomentsAutoForwardSettings.KEY_INCLUDE_KEYWORDS, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(
                            excludeKeywordsEnabled,
                            "排除关键词筛选",
                            "开启后，正文命中任意关键词时不转发，优先于包含规则"
                        ) {
                            excludeKeywordsEnabled = it
                            sp.edit()
                                .putBoolean(MomentsAutoForwardSettings.KEY_EXCLUDE_KEYWORDS_ENABLE, it)
                                .apply()
                        }
                        if (excludeKeywordsEnabled) {
                            InsetDivider()
                            InputRow(
                                "排除关键词",
                                "多个关键词用逗号或换行分隔",
                                excludeKeywords,
                                minLines = 2
                            ) {
                                excludeKeywords = it
                                sp.edit().putString(MomentsAutoForwardSettings.KEY_EXCLUDE_KEYWORDS, it).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(
                            replaceKeywordsEnabled,
                            "替换关键词",
                            "转发前按多条规则替换朋友圈原文"
                        ) {
                            replaceKeywordsEnabled = it
                            sp.edit()
                                .putBoolean(MomentsAutoForwardSettings.KEY_REPLACE_KEYWORDS_ENABLE, it)
                                .apply()
                        }
                        if (replaceKeywordsEnabled) {
                            InsetDivider()
                            ActionRow(
                                "替换规则",
                                if (replacementRuleCount == 0) {
                                    "暂无规则"
                                } else {
                                    "已设置 $replacementRuleCount 条规则"
                                },
                                onOpenReplacementRules
                            )
                        }
                        InsetDivider()
                        VariableInputRow(
                            "文案模板",
                            "支持下方变量",
                            contentTemplate,
                            templateVariables,
                            minLines = 3
                        ) {
                            contentTemplate = it
                            sp.edit().putString(MomentsAutoForwardSettings.KEY_CONTENT_TEMPLATE, it).apply()
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行记录") }
                item {
                    SettingsCard {
                        SwitchRow(logEnabled, "记录运行日志", "记录自动转发执行情况") {
                            logEnabled = it
                            sp.edit().putBoolean(MomentsAutoForwardSettings.KEY_LOG_ENABLE, it).apply()
                        }
                        if (logEnabled) {
                            InsetDivider()
                            ActionRow(
                                "刷新日志",
                                if (logs.isBlank()) "暂无记录" else "${logs.lineSequence().count()} 条记录"
                            ) {
                                logs = sp.getString(
                                    MomentsAutoForwardSettings.KEY_LOGS,
                                    MomentsAutoForwardSettings.DEFAULT_LOGS
                                ).orEmpty()
                            }
                            if (logs.isNotBlank()) {
                                InsetDivider()
                                Text(
                                    text = logs.take(5000),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                                InsetDivider()
                                ActionRow("清空日志", "删除当前自动转发运行记录") {
                                    sp.edit().remove(MomentsAutoForwardSettings.KEY_LOGS).apply()
                                    logs = ""
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
