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
internal fun AutoMessageForwardMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    AutoMessageForwardSettingsUi.Page(context, provider, onBack)
}

internal object AutoMessageForwardSettingsUi {
@Composable
fun Page(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { AutoMessageForwardSettings }
    var enabled by remember { mutableStateOf(settings.isEnabled(context)) }
    var rules by remember { mutableStateOf(settings.rules(context)) }
    var route by remember { mutableStateOf(AutoMessageForwardRoute.MAIN) }
    var draft by remember { mutableStateOf<AutoMessageForwardRule?>(null) }
    var pickerField by remember { mutableStateOf<AutoMessageForwardContactField?>(null) }
    var ruleQuery by remember { mutableStateOf("") }
    val ruleListState = rememberLazyListState()
    val editorListState = rememberLazyListState()

    fun saveRules(next: List<AutoMessageForwardRule>) {
        rules = next
        settings.saveRules(context, next)
    }

    SettingsRouteTransition(
        targetState = route,
        label = "AutoMessageForwardRoute",
        depthOf = {
            when (it) {
                AutoMessageForwardRoute.MAIN -> 0
                AutoMessageForwardRoute.RULES -> 1
                AutoMessageForwardRoute.EDITOR -> 2
                AutoMessageForwardRoute.CONTACTS,
                AutoMessageForwardRoute.MEMBERS,
                AutoMessageForwardRoute.TYPES,
                AutoMessageForwardRoute.REPLACEMENTS -> 3
            }
        }
    ) { currentRoute ->
        when (currentRoute) {
            AutoMessageForwardRoute.MAIN -> AutoMessageForwardMainPage(
                provider = provider,
                enabled = enabled,
                rules = rules,
                onBack = onBack,
                onEnabledChange = {
                    enabled = it
                    settings.setEnabled(context, it)
                },
                onOpenRules = { route = AutoMessageForwardRoute.RULES }
            )
            AutoMessageForwardRoute.RULES -> AutoMessageForwardRuleListPage(
                rules = rules,
                listState = ruleListState,
                query = ruleQuery,
                onQueryChange = { ruleQuery = it },
                onBack = { route = AutoMessageForwardRoute.MAIN },
                onAdd = {
                    draft = settings.newRule(rules.size + 1)
                    route = AutoMessageForwardRoute.EDITOR
                },
                onEdit = {
                    draft = it
                    route = AutoMessageForwardRoute.EDITOR
                },
                onToggle = { rule, checked ->
                    saveRules(rules.map { if (it.id == rule.id) rule.copy(enabled = checked) else it })
                },
                onDelete = { rule ->
                    saveRules(rules.filterNot { it.id == rule.id })
                    Toast.makeText(context, "规则已删除", Toast.LENGTH_SHORT).show()
                },
                onBatchDelete = { selected ->
                    val ids = selected.mapTo(HashSet()) { it.id }
                    saveRules(rules.filterNot { it.id in ids })
                    Toast.makeText(context, "已删除 ${selected.size} 条规则", Toast.LENGTH_SHORT).show()
                }
            )
            AutoMessageForwardRoute.EDITOR -> {
                val current = draft
                if (current == null) {
                    route = AutoMessageForwardRoute.RULES
                } else {
                    AutoMessageForwardRuleEditorPage(
                        rule = current,
                        listState = editorListState,
                        onBack = { route = AutoMessageForwardRoute.RULES },
                        onDraftChange = { draft = it },
                        onPickContacts = { next, field ->
                            draft = next
                            pickerField = field
                            route = AutoMessageForwardRoute.CONTACTS
                        },
                        onPickMembers = { next ->
                            draft = next
                            route = AutoMessageForwardRoute.MEMBERS
                        },
                        onPickTypes = { next ->
                            draft = next
                            route = AutoMessageForwardRoute.TYPES
                        },
                        onPickReplacementRules = { next ->
                            draft = next
                            route = AutoMessageForwardRoute.REPLACEMENTS
                        },
                        onSave = { saved ->
                            val clean = saved.copy(
                                name = saved.name.trim(),
                                sourceMemberIds = retainAutoMessageForwardSourceMembers(
                                    saved.sourceMemberIds,
                                    saved.sourceIds
                                )
                            )
                            when {
                                clean.name.isBlank() -> Toast.makeText(context, "请输入规则名称", Toast.LENGTH_SHORT).show()
                                clean.sourceIds.isEmpty() -> Toast.makeText(context, "请选择监听会话", Toast.LENGTH_SHORT).show()
                                clean.targetIds.isEmpty() -> Toast.makeText(context, "请选择转发会话", Toast.LENGTH_SHORT).show()
                                clean.messageKinds.isEmpty() -> Toast.makeText(context, "至少选择一种消息类型", Toast.LENGTH_SHORT).show()
                                else -> {
                                    saveRules(
                                        if (rules.any { it.id == clean.id }) {
                                            rules.map { if (it.id == clean.id) clean else it }
                                        } else {
                                            rules + clean
                                        }
                                    )
                                    draft = null
                                    Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
                                    route = AutoMessageForwardRoute.RULES
                                }
                            }
                        }
                    )
                }
            }
            AutoMessageForwardRoute.CONTACTS -> {
                val current = draft
                val field = pickerField
                if (current == null || field == null) {
                    route = AutoMessageForwardRoute.EDITOR
                } else {
                    val sources = field == AutoMessageForwardContactField.SOURCES
                    val contactRequest = remember(current.id, field, current.sourceIds, current.targetIds) {
                        ContactPickerRequest(
                            title = if (sources) "选择监听会话" else "选择转发会话",
                            mode = if (sources) ContactPickerMode.ALL_CHATS else ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = formatIds(if (sources) current.sourceIds else current.targetIds),
                            onValue = { value ->
                                val selected = parseIds(value)
                                draft = if (sources) current.copy(
                                    sourceIds = selected,
                                    sourceMemberIds = retainAutoMessageForwardSourceMembers(current.sourceMemberIds, selected)
                                )
                                else current.copy(targetIds = selected)
                                pickerField = null
                                route = AutoMessageForwardRoute.EDITOR
                            },
                            enableLabels = true
                        )
                    }
                    ContactPickerPage(
                        context = context,
                        request = contactRequest,
                        onBack = {
                            pickerField = null
                            route = AutoMessageForwardRoute.EDITOR
                        },
                        onConfirm = { selected ->
                            val value = formatIds(selected.map { it.id })
                            val ids = parseIds(value)
                            draft = if (sources) current.copy(
                                sourceIds = ids,
                                sourceMemberIds = retainAutoMessageForwardSourceMembers(current.sourceMemberIds, ids)
                            )
                            else current.copy(targetIds = ids)
                            pickerField = null
                            route = AutoMessageForwardRoute.EDITOR
                        }
                    )
                }
            }
            AutoMessageForwardRoute.MEMBERS -> {
                val current = draft
                val sourceGroups = current?.sourceIds.orEmpty().filterTo(linkedSetOf(), ::isAutoMessageForwardGroupId)
                if (current == null || sourceGroups.isEmpty()) {
                    route = AutoMessageForwardRoute.EDITOR
                } else {
                    val request = remember(current.id, current.sourceMemberIds, sourceGroups) {
                        GroupMemberPickerRequest(
                            title = "选择监听群成员",
                            existingValue = formatGroupMemberEntries(current.sourceMemberIds),
                            onValue = {},
                            allowedGroupIds = sourceGroups
                        )
                    }
                    GroupMemberPickerPage(
                        context = context,
                        request = request,
                        onBack = { route = AutoMessageForwardRoute.EDITOR },
                        onConfirm = { entries ->
                            draft = current.copy(sourceMemberIds = entries.toSet())
                            Toast.makeText(context, "已选择 ${entries.size} 个群成员", Toast.LENGTH_SHORT).show()
                            route = AutoMessageForwardRoute.EDITOR
                        }
                    )
                }
            }
            AutoMessageForwardRoute.TYPES -> {
                val current = draft
                if (current == null) {
                    route = AutoMessageForwardRoute.EDITOR
                } else {
                    AutoMessageForwardTypePickerPage(
                        selectedKinds = current.messageKinds,
                        onBack = { route = AutoMessageForwardRoute.EDITOR },
                        onConfirm = {
                            draft = current.copy(messageKinds = it)
                            route = AutoMessageForwardRoute.EDITOR
                        }
                    )
                }
            }
            AutoMessageForwardRoute.REPLACEMENTS -> {
                val current = draft
                if (current == null) {
                    route = AutoMessageForwardRoute.EDITOR
                } else {
                    KeywordReplacementRulesPage(
                        context = context,
                        initialRules = current.keywordReplacements,
                        onBack = { route = AutoMessageForwardRoute.EDITOR },
                        onSave = { updated ->
                            draft = current.copy(keywordReplacements = updated)
                            Toast.makeText(context, "替换规则已保存", Toast.LENGTH_SHORT).show()
                            route = AutoMessageForwardRoute.EDITOR
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AutoMessageForwardMainPage(
    provider: FeatureSettingsProvider,
    enabled: Boolean,
    rules: List<AutoMessageForwardRule>,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenRules: () -> Unit
) {
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
            item { SmallTitle(text = "消息自动转发") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用消息自动转发",
                        summary = "默认不转发自己发送的消息，可在每条规则中单独开启",
                        onCheckedChange = onEnabledChange
                    )
                    InsetDivider()
                    ActionRow(
                        title = "转发规则",
                        summary = if (rules.isEmpty()) "暂无规则" else "${rules.size} 条规则，${rules.count { it.enabled }} 条启用",
                        onClick = onOpenRules
                    )
                }
            }
        }
    }
}

@Composable
internal fun AutoMessageForwardRuleListPage(
    rules: List<AutoMessageForwardRule>,
    listState: LazyListState,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (AutoMessageForwardRule) -> Unit,
    onToggle: (AutoMessageForwardRule, Boolean) -> Unit,
    onDelete: (AutoMessageForwardRule) -> Unit,
    onBatchDelete: (List<AutoMessageForwardRule>) -> Unit
) {
    val context = LocalContext.current
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val visible = rules.filter { rule ->
        val lower = query.trim().lowercase(Locale.US)
        lower.isBlank() ||
            rule.name.lowercase(Locale.US).contains(lower) ||
            rule.sourceIds.any { it.lowercase(Locale.US).contains(lower) } ||
            rule.sourceMemberIds.any { it.lowercase(Locale.US).contains(lower) } ||
            rule.targetIds.any { it.lowercase(Locale.US).contains(lower) } ||
            autoMessageForwardRuleSummary(rule).lowercase(Locale.US).contains(lower)
    }
    val visibleIds = visible.mapTo(LinkedHashSet()) { it.id }
    val selected = rules.filter { it.id in selectedIds }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "转发规则",
        largeTitle = "转发规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selected.size}）",
                    onPrimaryClick = {
                        if (selected.isEmpty()) Toast.makeText(context, "请先选择规则", Toast.LENGTH_SHORT).show()
                        else showDeleteConfirm = true
                    },
                    secondaryText = "取消",
                    onSecondaryClick = {
                        batchDeleteMode = false
                        selectedIds = emptySet()
                    },
                    middleText = if (visibleIds.isEmpty()) null else if (allVisibleSelected) "取消全选" else "全选",
                    onMiddleClick = if (visibleIds.isEmpty()) null else {
                        {
                            selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
                        }
                    }
                )
            } else {
                BottomActionBar(
                    primaryText = "新增规则",
                    onPrimaryClick = onAdd,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
                    middleText = if (rules.isEmpty()) null else "批量删除",
                    onMiddleClick = if (rules.isEmpty()) null else {
                        {
                            batchDeleteMode = true
                            selectedIds = emptySet()
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item {
                SettingsCard {
                    InputRow("搜索", "按规则名、会话、群成员或消息类型筛选", query, onValueChange = onQueryChange)
                }
            }
            if (visible.isEmpty()) {
                item { SettingsCard { EmptyText("暂无规则") } }
            } else {
                visible.forEachIndexed { index, rule ->
                    item {
                        SmallTitle(
                            modifier = Modifier.padding(top = if (index == 0) 10.dp else 18.dp),
                            text = "规则 ${index + 1}"
                        )
                    }
                    item {
                        SettingsCard {
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(rule.name.ifBlank { "未命名规则" }, index, autoMessageForwardRuleSummary(rule)),
                                    selected = rule.id in selectedIds,
                                    onClick = {
                                        selectedIds = if (rule.id in selectedIds) selectedIds - rule.id else selectedIds + rule.id
                                    }
                                )
                            } else {
                                SwitchRow(
                                    checked = rule.enabled,
                                    title = rule.name.ifBlank { "未命名规则" },
                                    summary = autoMessageForwardRuleSummary(rule),
                                    onCheckedChange = { onToggle(rule, it) }
                                )
                                InsetDivider()
                                ActionRow("编辑", "修改监听会话、转发会话和消息类型") { onEdit(rule) }
                                InsetDivider()
                                ActionRow("删除", "移除此规则") { onDelete(rule) }
                            }
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selected.size} 条转发规则，此操作不可撤销。",
        labels = selected.map { it.name.ifBlank { "未命名规则" } },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selected
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedIds = emptySet()
            onBatchDelete(targets)
        }
    )
}

@Composable
internal fun AutoMessageForwardRuleEditorPage(
    rule: AutoMessageForwardRule,
    listState: LazyListState,
    onBack: () -> Unit,
    onDraftChange: (AutoMessageForwardRule) -> Unit,
    onPickContacts: (AutoMessageForwardRule, AutoMessageForwardContactField) -> Unit,
    onPickMembers: (AutoMessageForwardRule) -> Unit,
    onPickTypes: (AutoMessageForwardRule) -> Unit,
    onPickReplacementRules: (AutoMessageForwardRule) -> Unit,
    onSave: (AutoMessageForwardRule) -> Unit
) {
    val context = LocalContext.current
    val current = rule
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "编辑转发规则",
        largeTitle = "编辑转发规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = { onSave(current) },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(current.enabled, "启用规则", "关闭后保留配置但不触发") { onDraftChange(current.copy(enabled = it)) }
                    InsetDivider()
                    SwitchRow(current.forwardOwnMessages, "转发自己发送的消息", "默认关闭；模块自动转发的消息不会再次触发") {
                        onDraftChange(current.copy(forwardOwnMessages = it))
                    }
                    InsetDivider()
                    SwitchRow(current.followSourceRecall, "跟随原消息撤回", "原消息撤回后，撤回该规则自动转发的消息") {
                        onDraftChange(current.copy(followSourceRecall = it))
                    }
                    InsetDivider()
                    InputRow("规则名称", "用于列表里识别规则", current.name) { onDraftChange(current.copy(name = it)) }
                    InsetDivider()
                    SwitchRow(current.delayEnabled, "延迟发送", "开启后按设置时间等待再转发") {
                        onDraftChange(current.copy(delayEnabled = it))
                    }
                    if (current.delayEnabled) {
                        InsetDivider()
                        NumberInputRow("延迟时间", "单位秒，0 表示立即发送", current.delaySeconds.toString()) {
                            onDraftChange(current.copy(delaySeconds = it.toLongOrNull()?.coerceAtLeast(0L) ?: 0L))
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "会话") }
            item {
                SettingsCard {
                    ActionRow("监听会话", autoMessageForwardIdSummary(current.sourceIds, "未选择")) {
                        onPickContacts(current, AutoMessageForwardContactField.SOURCES)
                    }
                    InsetDivider()
                    ActionRow(
                        "群成员范围",
                        if (current.sourceMemberIds.isEmpty()) "不限群成员" else "已选择 ${current.sourceMemberIds.size} 个群成员"
                    ) {
                        if (current.sourceIds.none(::isAutoMessageForwardGroupId)) {
                            Toast.makeText(context, "请先选择监听群聊", Toast.LENGTH_SHORT).show()
                        } else {
                            onPickMembers(current)
                        }
                    }
                    InsetDivider()
                    ActionRow("转发会话", autoMessageForwardIdSummary(current.targetIds, "未选择")) {
                        onPickContacts(current, AutoMessageForwardContactField.TARGETS)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "消息类型") }
            item {
                SettingsCard {
                    ActionRow("选择消息类型", autoMessageForwardTypeSummary(current.messageKinds)) {
                        onPickTypes(current)
                    }
                    InsetDivider()
                    SwitchRow(current.includeKeywordsEnabled, "包含关键词", "开启后仅转发命中关键词的消息") {
                        onDraftChange(current.copy(includeKeywordsEnabled = it))
                    }
                    if (current.includeKeywordsEnabled) {
                        InsetDivider()
                        InputRow("包含关键词内容", "多个关键词用 |、逗号或换行分隔", current.includeKeywords, minLines = 2) {
                            onDraftChange(current.copy(includeKeywords = it))
                        }
                    }
                    InsetDivider()
                    SwitchRow(current.excludeKeywordsEnabled, "排除关键词", "开启后不转发命中关键词的消息") {
                        onDraftChange(current.copy(excludeKeywordsEnabled = it))
                    }
                    if (current.excludeKeywordsEnabled) {
                        InsetDivider()
                        InputRow("排除关键词内容", "多个关键词用 |、逗号或换行分隔", current.excludeKeywords, minLines = 2) {
                            onDraftChange(current.copy(excludeKeywords = it))
                        }
                    }
                    InsetDivider()
                    SwitchRow(current.replaceKeywordsEnabled, "替换关键词", "转发普通文字前按多条规则替换正文") {
                        onDraftChange(current.copy(replaceKeywordsEnabled = it))
                    }
                    if (current.replaceKeywordsEnabled) {
                        InsetDivider()
                        ActionRow(
                            "替换规则",
                            if (current.keywordReplacements.isEmpty()) {
                                "暂无规则"
                            } else {
                                "已设置 ${current.keywordReplacements.size} 条规则"
                            }
                        ) {
                            onPickReplacementRules(current)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AutoMessageForwardTypePickerPage(
    selectedKinds: Set<String>,
    onBack: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selected by remember(selectedKinds) { mutableStateOf(selectedKinds) }
    val options = autoMessageForwardTypeOptions()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "选择消息类型",
        largeTitle = "选择消息类型",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = { onConfirm(selected) },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (selected.size == options.size) "取消全选" else "全选",
                onMiddleClick = {
                    selected = if (selected.size == options.size) emptySet() else options.mapTo(LinkedHashSet()) { it.first }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item {
                SettingsCard {
                    options.forEachIndexed { index, option ->
                        if (index > 0) InsetDivider()
                        SwitchRow(
                            checked = option.first in selected,
                            title = option.second,
                            summary = option.third
                        ) {
                            selected = if (it) selected + option.first else selected - option.first
                        }
                    }
                }
            }
        }
    }
}

internal fun autoMessageForwardRuleSummary(rule: AutoMessageForwardRule): String {
    return buildString {
        append("监听 ${rule.sourceIds.size} 个会话 → 转发到 ${rule.targetIds.size} 个会话 · ${autoMessageForwardTypeSummary(rule.messageKinds)}")
        if (rule.sourceMemberIds.isNotEmpty()) append(" · 指定 ${rule.sourceMemberIds.size} 个群成员")
        if (rule.forwardOwnMessages) append(" · 含自己发送")
        if (rule.followSourceRecall) append(" · 跟随撤回")
        if (rule.delayEnabled && rule.delaySeconds > 0L) append(" · 延迟 ${rule.delaySeconds} 秒")
        if ((rule.includeKeywordsEnabled && rule.includeKeywords.isNotBlank()) ||
            (rule.excludeKeywordsEnabled && rule.excludeKeywords.isNotBlank()) ||
            (rule.replaceKeywordsEnabled && rule.keywordReplacements.isNotEmpty())
        ) {
            append(" · 已设置关键词")
        }
    }
}

internal fun autoMessageForwardIdSummary(ids: Set<String>, empty: String): String {
    return if (ids.isEmpty()) empty else "已选择 ${ids.size} 个会话"
}

internal fun retainAutoMessageForwardSourceMembers(
    memberIds: Set<String>,
    sourceIds: Set<String>
): Set<String> = memberIds.filterTo(linkedSetOf()) { it.substringBefore('/') in sourceIds }

internal fun isAutoMessageForwardGroupId(value: String): Boolean =
    value.endsWith("@chatroom") || value.endsWith("@im.chatroom")

internal fun autoMessageForwardTypeSummary(kinds: Set<String>): String {
    val options = autoMessageForwardTypeOptions().associate { it.first to it.second }
    val labels = kinds.mapNotNull { options[it] }
    return when {
        labels.isEmpty() -> "未选择"
        labels.size == options.size -> "全部支持类型"
        labels.size <= 3 -> labels.joinToString("、")
        else -> "${labels.take(3).joinToString("、")}等 ${labels.size} 类"
    }
}

internal fun autoMessageForwardTypeOptions(): List<Triple<String, String, String>> {
    return listOf(
        Triple(WeChatMessageObserveApi.Kind.TEXT, "文字", "普通文字消息"),
        Triple(WeChatMessageObserveApi.Kind.IMAGE, "图片", "图片消息"),
        Triple(WeChatMessageObserveApi.Kind.VOICE, "语音", "语音消息"),
        Triple(WeChatMessageObserveApi.Kind.VIDEO, "视频", "视频消息"),
        Triple(WeChatMessageObserveApi.Kind.EMOJI, "表情", "动画表情消息"),
        Triple(WeChatMessageObserveApi.Kind.QUOTE, "引用", "引用消息"),
        Triple(WeChatMessageObserveApi.Kind.FILE, "文件", "已下载到本地的文件"),
        Triple(WeChatMessageObserveApi.Kind.LINK, "链接", "网页链接卡片"),
        Triple(WeChatMessageObserveApi.Kind.MUSIC, "音乐", "音乐卡片"),
        Triple(WeChatMessageObserveApi.Kind.APP, "小程序及卡片", "其它可转发的应用消息"),
        Triple(WeChatMessageObserveApi.Kind.LOCATION, "位置", "位置消息"),
        Triple(WeChatMessageObserveApi.Kind.SHARE_CARD, "名片", "联系人名片"),
        Triple(WeChatMessageObserveApi.Kind.NOTE, "聊天记录", "聊天记录等笔记消息"),
        Triple(WeChatMessageObserveApi.Kind.VIDEO_NUMBER_VIDEO, "视频号", "视频号消息")
    )
}
}

@Composable
internal fun AutoReplyMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    rules: List<AutoReplyRule>,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAutoAccept: () -> Unit,
    onOpenGreetAccepted: () -> Unit,
    onOpenAi: () -> Unit
) {
    var excludedTalkersEnabled by remember {
        mutableStateOf(sp.getBoolean(AutoReplySettings.KEY_EXCLUDED_TALKERS_ENABLE, false))
    }
    var excludedTalkers by remember {
        mutableStateOf(sp.getString(AutoReplySettings.KEY_EXCLUDED_TALKERS, "").orEmpty())
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
            item { SmallTitle(text = "聊天自动回复") }
            item {
                SettingsCard {
                    SwitchRow(sp, AutoReplySettings.KEY_ENABLE, "启用自动回复", "开启后按规则回复收到的消息", AutoReplySettings.DEFAULT_ENABLE)
                    InsetDivider()
                    SwitchRow(excludedTalkersEnabled, "排除指定会话", "开启后不回复指定会话的消息") {
                        excludedTalkersEnabled = it
                        sp.edit().putBoolean(AutoReplySettings.KEY_EXCLUDED_TALKERS_ENABLE, it).apply()
                    }
                    if (excludedTalkersEnabled) {
                        InsetDivider()
                        InputRow("排除会话 ID", "多个 ID 用逗号、分号或换行分隔", excludedTalkers, minLines = 2) {
                            excludedTalkers = it
                            sp.edit().putString(AutoReplySettings.KEY_EXCLUDED_TALKERS, it).apply()
                        }
                    }
                    InsetDivider()
                    ActionRow("回复规则", if (rules.isEmpty()) "暂无规则" else "${rules.size} 条规则，${rules.count { it.enabled }} 条启用", onOpenRules)
                    InsetDivider()
                    ActionRow("好友请求处理", "自动同意好友申请并按步骤发送欢迎内容", onOpenAutoAccept)
                    InsetDivider()
                    ActionRow("通过后欢迎语", "对方通过你的好友请求后发送欢迎内容", onOpenGreetAccepted)
                    InsetDivider()
                    ActionRow("AI 配置", "配置小智AI和智聊AI，多智聊模型可切换", onOpenAi)
                }
            }
        }
    }
}

@Composable
internal fun AutoReplyRuleListPage(
    rules: List<AutoReplyRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (AutoReplyRule) -> Unit,
    onToggle: (AutoReplyRule, Boolean) -> Unit,
    onDelete: (AutoReplyRule) -> Unit,
    onBatchDelete: (List<AutoReplyRule>) -> Unit
) {
    val context = LocalContext.current
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val visible = rules.filter {
        val lower = query.trim().lowercase(Locale.US)
        lower.isBlank() ||
            it.name.lowercase(Locale.US).contains(lower) ||
            it.keyword.lowercase(Locale.US).contains(lower) ||
            it.excludedKeywords.lowercase(Locale.US).contains(lower)
    }
    val visibleIds = visible.mapTo(LinkedHashSet()) { it.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val selectedRules = rules.filter { it.id in selectedIds }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "回复规则",
        largeTitle = "回复规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedRules.size}）",
                    onPrimaryClick = {
                        if (selectedRules.isEmpty()) {
                            Toast.makeText(context, "请先选择规则", Toast.LENGTH_SHORT).show()
                        } else {
                            showDeleteConfirm = true
                        }
                    },
                    secondaryText = "取消",
                    onSecondaryClick = {
                        batchDeleteMode = false
                        selectedIds = emptySet()
                    },
                    middleText = if (visibleIds.isEmpty()) null else if (allVisibleSelected) "取消全选" else "全选",
                    onMiddleClick = if (visibleIds.isEmpty()) null else {
                        {
                            selectedIds = if (allVisibleSelected) {
                                selectedIds - visibleIds
                            } else {
                                selectedIds + visibleIds
                            }
                        }
                    }
                )
            } else {
                BottomActionBar(
                    primaryText = "新增规则",
                    onPrimaryClick = onAdd,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
                    middleText = if (rules.isEmpty()) null else "批量删除",
                    onMiddleClick = if (rules.isEmpty()) null else {
                        {
                            batchDeleteMode = true
                            selectedIds = emptySet()
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item {
                SettingsCard {
                    InputRow("搜索", "按规则名或关键词筛选", query, onValueChange = onQueryChange)
                }
            }
            if (visible.isEmpty()) {
                item { SettingsCard { EmptyText("暂无规则") } }
            } else {
                visible.forEachIndexed { index, rule ->
                    item {
                        SmallTitle(
                            modifier = Modifier.padding(top = if (index == 0) 10.dp else 18.dp),
                            text = "规则 ${index + 1}"
                        )
                    }
                    item {
                        SettingsCard {
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = rule.name.ifBlank { "未命名规则" },
                                        value = index,
                                        summary = describeAutoReplyRule(rule)
                                    ),
                                    selected = rule.id in selectedIds,
                                    onClick = {
                                        selectedIds = if (rule.id in selectedIds) {
                                            selectedIds - rule.id
                                        } else {
                                            selectedIds + rule.id
                                        }
                                    }
                                )
                            } else {
                                SwitchRow(
                                    checked = rule.enabled,
                                    title = rule.name.ifBlank { "未命名规则" },
                                    summary = describeAutoReplyRule(rule),
                                    onCheckedChange = { onToggle(rule, it) }
                                )
                                InsetDivider()
                                ActionRow("编辑", "修改触发条件和回复步骤") { onEdit(rule) }
                                InsetDivider()
                                ActionRow("删除", "移除此规则") { onDelete(rule) }
                            }
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedRules.size} 条回复规则，此操作不可撤销。",
        labels = selectedRules.map { it.name.ifBlank { "未命名规则" } },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selectedRules
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedIds = emptySet()
            onBatchDelete(targets)
        }
    )
}

@Composable
internal fun AutoReplyRuleEditorPage(
    rule: AutoReplyRule,
    listState: LazyListState,
    onBack: () -> Unit,
    onSave: (AutoReplyRule) -> Unit,
    onDraftChange: (AutoReplyRule) -> Unit,
    onEditSteps: (AutoReplyRule) -> Unit
) {
    val context = LocalContext.current
    var current by remember(rule) { mutableStateOf(rule) }
    var optionPicker by remember { mutableStateOf<OptionPickerRequest?>(null) }
    var contactPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var groupMemberPicker by remember { mutableStateOf<GroupMemberPickerRequest?>(null) }
    val editorRoute = when {
        optionPicker != null -> AutoReplyEditorRoute.OptionPicker(optionPicker!!)
        contactPicker != null -> AutoReplyEditorRoute.ContactPicker(contactPicker!!)
        groupMemberPicker != null -> AutoReplyEditorRoute.GroupMemberPicker(groupMemberPicker!!)
        else -> AutoReplyEditorRoute.Main
    }
    SettingsRouteTransition(
        targetState = editorRoute,
        label = "AutoReplyEditorRoute",
        depthOf = { it.depth() }
    ) { route ->
        when (route) {
            is AutoReplyEditorRoute.OptionPicker -> {
                val request = route.request
                OptionPickerPage(
                    request = request,
                    onBack = { optionPicker = null },
                    onSelected = { request.onSelected(it); optionPicker = null }
                )
            }
            is AutoReplyEditorRoute.ContactPicker -> {
                val request = route.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { contactPicker = null },
                    onConfirm = { selected ->
                        request.onValue(formatIds(selected.map { it.id }))
                        Toast.makeText(context, "已选择 ${selected.size} 项", Toast.LENGTH_SHORT).show()
                        contactPicker = null
                    }
                )
            }
            is AutoReplyEditorRoute.GroupMemberPicker -> {
                val request = route.request
                GroupMemberPickerPage(
                    context = context,
                    request = request,
                    onBack = { groupMemberPicker = null },
                    onConfirm = { entries ->
                        request.onValue(formatIds(entries))
                        Toast.makeText(context, "已选择 ${entries.size} 个群成员", Toast.LENGTH_SHORT).show()
                        groupMemberPicker = null
                    }
                )
            }
            AutoReplyEditorRoute.Main -> AutoReplyRuleEditorContent(
                rule = current,
                listState = listState,
                onRuleChange = {
                    current = it
                    onDraftChange(it)
                },
                onBack = onBack,
                onSave = { onSave(current) },
                onEditSteps = { onEditSteps(current) },
                onOptionPicker = { optionPicker = it },
                onContactPicker = { contactPicker = it },
                onGroupMemberPicker = { groupMemberPicker = it }
            )
        }
    }
}

@Composable
internal fun AutoReplyRuleEditorContent(
    rule: AutoReplyRule,
    listState: LazyListState,
    onRuleChange: (AutoReplyRule) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onEditSteps: () -> Unit,
    onOptionPicker: (OptionPickerRequest) -> Unit,
    onContactPicker: (ContactPickerRequest) -> Unit,
    onGroupMemberPicker: (GroupMemberPickerRequest) -> Unit
) {
    val current = rule
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "编辑规则",
        largeTitle = "编辑规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = onSave,
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(current.enabled, "启用规则", "关闭后保留配置但不触发") { onRuleChange(current.copy(enabled = it)) }
                    InsetDivider()
                    InputRow("规则名称", "用于列表里识别规则", current.name) { onRuleChange(current.copy(name = it)) }
                    InsetDivider()
                    PopupOptionRow(
                        title = "匹配方式",
                        summary = autoReplyMatchLabel(current.matchType),
                        options = autoReplyMatchOptions(),
                        currentValue = current.matchType,
                        onValueChanged = {
                            onRuleChange(
                                current.copy(
                                    matchType = it,
                                    keyword = if (it == AutoReplySettings.MATCH_ANY) "" else current.keyword
                                )
                            )
                        }
                    )
                    if (current.matchType != AutoReplySettings.MATCH_ANY) {
                        InsetDivider()
                        InputRow("关键词", "多个关键词用 |、逗号或换行分隔", current.keyword, minLines = 2) {
                            onRuleChange(current.copy(keyword = it))
                        }
                    } else {
                        InsetDivider()
                        InputRow("排除关键词", "包含任一关键词时不触发，多个用 |、逗号或换行分隔", current.excludedKeywords, minLines = 2) {
                            onRuleChange(current.copy(excludedKeywords = it))
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "范围") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "生效范围",
                        summary = autoReplyTargetLabel(current.targetMode),
                        options = autoReplyTargetOptions(),
                        currentValue = current.targetMode,
                        onValueChanged = { onRuleChange(current.copy(targetMode = it)) }
                    )
                    if (current.targetMode == AutoReplySettings.TARGET_SPECIFIC) {
                        InsetDivider()
                        ActionRow("选择指定好友", autoReplySelectedIdSummary(formatIds(current.targetIds.filter { !isAutoReplyGroupId(it) }))) {
                            onContactPicker(ContactPickerRequest(
                                title = "选择指定好友",
                                mode = ContactPickerMode.FRIENDS,
                                multiSelect = true,
                                existingValue = formatIds(current.targetIds.filter { !isAutoReplyGroupId(it) }),
                                onValue = { onRuleChange(current.copy(targetIds = replaceAutoReplyTypedIds(current.targetIds, parseIds(it), group = false))) },
                                enableLabels = true
                            ))
                        }
                        InsetDivider()
                        ActionRow("选择指定群聊", autoReplySelectedIdSummary(formatIds(current.targetIds.filter { isAutoReplyGroupId(it) }))) {
                            onContactPicker(ContactPickerRequest(
                                title = "选择指定群聊",
                                mode = ContactPickerMode.GROUPS,
                                multiSelect = true,
                                existingValue = formatIds(current.targetIds.filter { isAutoReplyGroupId(it) }),
                                onValue = { onRuleChange(current.copy(targetIds = replaceAutoReplyTypedIds(current.targetIds, parseIds(it), group = true))) }
                            ))
                        }
                        InsetDivider()
                        ActionRow("选择指定群成员", autoReplySelectedIdSummary(formatIds(current.includedGroupMembers))) {
                            onGroupMemberPicker(GroupMemberPickerRequest(
                                title = "选择指定群成员",
                                existingValue = formatIds(current.includedGroupMembers),
                                onValue = { onRuleChange(current.copy(includedGroupMembers = parseIds(it))) }
                            ))
                        }
                    } else {
                        InsetDivider()
                        ActionRow("选择排除好友", autoReplySelectedIdSummary(formatIds(current.excludedIds.filter { !isAutoReplyGroupId(it) }))) {
                            onContactPicker(ContactPickerRequest(
                                title = "选择排除好友",
                                mode = ContactPickerMode.FRIENDS,
                                multiSelect = true,
                                existingValue = formatIds(current.excludedIds.filter { !isAutoReplyGroupId(it) }),
                                onValue = { onRuleChange(current.copy(excludedIds = replaceAutoReplyTypedIds(current.excludedIds, parseIds(it), group = false))) },
                                enableLabels = true
                            ))
                        }
                        InsetDivider()
                        ActionRow("选择排除群聊", autoReplySelectedIdSummary(formatIds(current.excludedIds.filter { isAutoReplyGroupId(it) }))) {
                            onContactPicker(ContactPickerRequest(
                                title = "选择排除群聊",
                                mode = ContactPickerMode.GROUPS,
                                multiSelect = true,
                                existingValue = formatIds(current.excludedIds.filter { isAutoReplyGroupId(it) }),
                                onValue = { onRuleChange(current.copy(excludedIds = replaceAutoReplyTypedIds(current.excludedIds, parseIds(it), group = true))) }
                            ))
                        }
                        InsetDivider()
                        ActionRow("选择排除群成员", autoReplySelectedIdSummary(formatIds(current.excludedGroupMembers))) {
                            onGroupMemberPicker(GroupMemberPickerRequest(
                                title = "选择排除群成员",
                                existingValue = formatIds(current.excludedGroupMembers),
                                onValue = { onRuleChange(current.copy(excludedGroupMembers = parseIds(it))) }
                            ))
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "触发") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "@ 触发",
                        summary = autoReplyAtLabel(current.atTrigger),
                        options = autoReplyAtOptions(),
                        currentValue = current.atTrigger,
                        onValueChanged = { onRuleChange(current.copy(atTrigger = it)) }
                    )
                    InsetDivider()
                    PopupOptionRow(
                        title = "拍一拍",
                        summary = autoReplyPatLabel(current.patTrigger),
                        options = autoReplyPatOptions(),
                        currentValue = current.patTrigger,
                        onValueChanged = { onRuleChange(current.copy(patTrigger = it)) }
                    )
                    InsetDivider()
                    TimeOfDayPickerRow("开始时间", current.startTime, allowEmpty = true) {
                        onRuleChange(current.copy(startTime = it))
                    }
                    InsetDivider()
                    TimeOfDayPickerRow("结束时间", current.endTime, allowEmpty = true) {
                        onRuleChange(current.copy(endTime = it))
                    }
                    InsetDivider()
                    NumberInputRow("最大回复次数", "0 表示不限制，按规则/会话/发送者统计", current.maxReplyCount.toString()) {
                        onRuleChange(current.copy(maxReplyCount = it.toIntOrNull()?.coerceAtLeast(0) ?: 0))
                    }
                    InsetDivider()
                    NumberInputRow("回复冷却时间", "单位秒，0 表示不限制；同一规则在同一会话内冷却", current.cooldownSeconds.toString()) {
                        onRuleChange(current.copy(cooldownSeconds = it.toLongOrNull()?.coerceAtLeast(0L) ?: 0L))
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "回复") }
            item {
                SettingsCard {
                    SwitchRow(current.replyAsQuote, "引用回复", "文本和 AI 可引用原消息回复") { onRuleChange(current.copy(replyAsQuote = it)) }
                    InsetDivider()
                    ActionRow("回复步骤", describeAutoReplySteps(current.steps), onEditSteps)
                }
            }
        }
    }
}

@Composable
internal fun AutoReplyFriendPage(
    context: Context,
    title: String,
    sp: SharedPreferences,
    enabledKey: String,
    delayKey: String,
    tagEnabledKey: String,
    tagNameKey: String,
    steps: List<AutoReplyStep>,
    listState: LazyListState,
    onBack: () -> Unit,
    automationKeys: AutoReplyFriendAutomationKeys?,
    onSelectLabels: (() -> Unit)?,
    onEditSteps: () -> Unit
) {
    var delayMs by remember { mutableStateOf(sp.getLong(delayKey, 2000L).coerceAtLeast(0L).toString()) }
    var tagName by remember { mutableStateOf(sp.getString(tagNameKey, "") ?: "") }
    var labelDateFormat by remember {
        mutableStateOf(sp.getString(automationKeys?.labelDateFormat ?: "", "yyyy-MM-dd") ?: "yyyy-MM-dd")
    }
    val selectedLabelNames = sp.getString(automationKeys?.labelSelectedNames ?: "", "") ?: ""
    var remarkDateFormat by remember {
        mutableStateOf(sp.getString(automationKeys?.remarkDateFormat ?: "", "yyMMdd") ?: "yyMMdd")
    }
    var remarkCustomText by remember {
        mutableStateOf(sp.getString(automationKeys?.remarkCustomText ?: "", "") ?: "")
    }
    val hasAutomation = automationKeys != null && onSelectLabels != null
    var labelDateEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.labelDateEnable, false) } ?: false) }
    var labelExistingEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.labelExistingEnable, false) } ?: false) }
    var remarkDateEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.remarkDateEnable, false) } ?: false) }
    var remarkCustomEnabled by remember { mutableStateOf(automationKeys?.let { sp.getBoolean(it.remarkCustomEnable, false) } ?: false) }
    var tagEnabled by remember { mutableStateOf(sp.getBoolean(tagEnabledKey, false)) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    sp.edit()
                        .putLong(delayKey, delayMs.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L)
                        .putString(tagNameKey, tagName.trim())
                        .also { editor ->
                            automationKeys?.let { keys ->
                                editor.putString(keys.labelDateFormat, labelDateFormat.trim().ifBlank { "yyyy-MM-dd" })
                                editor.putString(keys.remarkDateFormat, remarkDateFormat.trim().ifBlank { "yyMMdd" })
                                editor.putString(keys.remarkCustomText, remarkCustomText.trim())
                            }
                        }
                        .apply()
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item {
                SettingsCard {
                    SwitchRow(sp, enabledKey, "启用", "开启后自动处理这个场景", false)
                    InsetDivider()
                    NumberInputRow("延迟发送", "单位 ms，通过好友后等待再发欢迎内容", delayMs) { delayMs = it }
                    if (hasAutomation) {
                        val keys = automationKeys!!
                        InsetDivider()
                        SwitchRow(sp, keys.labelNewFriendEnable, "自动标签新加好友", "给好友添加“新加好友”标签", false)
                        InsetDivider()
                        SwitchRow(labelDateEnabled, "自动标签日期", "按日期格式生成一个标签") {
                            labelDateEnabled = it
                            sp.edit().putBoolean(keys.labelDateEnable, it).apply()
                        }
                        if (labelDateEnabled) {
                            InsetDivider()
                            InputRow("标签日期格式", "例如 yyyy-MM-dd", labelDateFormat) { labelDateFormat = it }
                        }
                        InsetDivider()
                        SwitchRow(labelExistingEnabled, "自动标签已有标签", "把好友加入已选择的微信标签") {
                            labelExistingEnabled = it
                            sp.edit().putBoolean(keys.labelExistingEnable, it).apply()
                        }
                        if (labelExistingEnabled) {
                            InsetDivider()
                            ActionRow("选择标签", autoReplyLabelSummary(selectedLabelNames), onSelectLabels!!)
                        }
                        InsetDivider()
                        SwitchRow(sp, keys.remarkNewFriendEnable, "自动备注新加好友", "备注里加入“新加好友”", false)
                        InsetDivider()
                        SwitchRow(sp, keys.remarkNicknameSuffixEnable, "加昵称后面", "备注格式为微信昵称 + 备注内容", false)
                        InsetDivider()
                        SwitchRow(remarkDateEnabled, "自动备注日期", "备注里加入日期") {
                            remarkDateEnabled = it
                            sp.edit().putBoolean(keys.remarkDateEnable, it).apply()
                        }
                        if (remarkDateEnabled) {
                            InsetDivider()
                            InputRow("备注日期格式", "例如 yyMMdd", remarkDateFormat) { remarkDateFormat = it }
                        }
                        InsetDivider()
                        SwitchRow(remarkCustomEnabled, "自动备注自定义文本", "备注里加入自定义内容") {
                            remarkCustomEnabled = it
                            sp.edit().putBoolean(keys.remarkCustomEnable, it).apply()
                        }
                        if (remarkCustomEnabled) {
                            InsetDivider()
                            InputRow("备注自定义文本", "例如 渠道A", remarkCustomText) { remarkCustomText = it }
                        }
                    } else {
                        InsetDivider()
                        SwitchRow(tagEnabled, "自动打标签", "处理后把好友加入指定标签") {
                            tagEnabled = it
                            sp.edit().putBoolean(tagEnabledKey, it).apply()
                        }
                        if (tagEnabled) {
                            InsetDivider()
                            InputRow("标签名称", "标签需要微信里已存在", tagName) { tagName = it }
                        }
                    }
                    InsetDivider()
                    ActionRow("回复步骤", describeAutoReplySteps(steps), onEditSteps)
                }
            }
        }
    }
}

@Composable
internal fun AutoReplyLabelPickerPage(
    context: Context,
    sp: SharedPreferences,
    selectedKey: String,
    listState: LazyListState,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf<List<ContactLabelBean>>(emptyList()) }
    var selected by remember {
        mutableStateOf(splitAutoReplyNames(sp.getString(selectedKey, "") ?: "").toSet())
    }
    LaunchedEffect(Unit) {
        loading = true
        error = ""
        val api = WeChatApis.contact().contacts()
        if (api == null || !api.isAvailable) {
            labels = emptyList()
            error = "联系人标签不可用"
            loading = false
        } else {
            Thread({
                val result = runCatching { api.getContactLabelList() }
                Handler(Looper.getMainLooper()).post {
                    labels = result.getOrDefault(emptyList())
                        .filter { it.labelName.isNotBlank() || it.labelId.isNotBlank() }
                        .sortedBy { it.labelName.ifBlank { it.labelId }.lowercase(Locale.US) }
                    error = result.exceptionOrNull()?.message.orEmpty()
                    loading = false
                }
            }, "HchatAutoReplyLabels").start()
        }
    }
    val visible = labels.filter { label ->
        val name = label.labelName.ifBlank { label.labelId }
        val key = query.trim().lowercase(Locale.US)
        key.isBlank() || name.lowercase(Locale.US).contains(key)
    }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "选择标签",
        largeTitle = "选择标签",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    sp.edit()
                        .putString(selectedKey, selected.joinToString(";;;"))
                        .apply()
                    Toast.makeText(context, "标签已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item { SmallTitle(text = "搜索") }
            item {
                SettingsCard {
                    InputRow("关键词", "按标签名筛选", query) { query = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "标签") }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入标签...") } }
                error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配标签") } }
                else -> visible.forEach { label ->
                    val name = label.labelName.ifBlank { label.labelId }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                            cornerRadius = 14.dp
                        ) {
                            AutoReplyLabelRow(
                                name = name,
                                summary = "${label.userNameList.size} 人",
                                selected = selected.contains(name),
                                onClick = {
                                    selected = if (selected.contains(name)) selected - name else selected + name
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AutoReplyLabelRow(
    name: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
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
        Box(
            modifier = Modifier.size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).ifEmpty { "签" },
                color = MiuixTheme.colorScheme.onSecondaryVariant,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = name, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        SelectionMark(selected = selected, multiSelect = true)
    }
}

internal fun autoReplyLabelSummary(value: String): String {
    val names = splitAutoReplyNames(value)
    return if (names.isEmpty()) "未选择" else names.take(3).joinToString("、") + if (names.size > 3) " 等 ${names.size} 个" else ""
}

@Composable
internal fun XiaozhiCaptchaSvgView(svg: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        },
        update = { view ->
            view.loadDataWithBaseURL(null, svg, "image/svg+xml", "UTF-8", null)
        }
    )
}

internal fun xiaozhiAgentSummary(agentId: String, agents: List<XiaozhiAgentOption>): String {
    if (agentId.isBlank()) return "未选择，点击拉取"
    val agent = agents.firstOrNull { it.id == agentId }
    return if (agent == null) "已保存 ID: $agentId" else "${agent.name} / ${agent.assistantName.ifBlank { agent.id }}"
}

internal fun xiaozhiModelSummary(model: String, models: List<XiaozhiModelOption>): String {
    if (model.isBlank()) return "未选择，点击拉取"
    val item = models.firstOrNull { it.id == model }
    return if (item == null) "已保存: $model" else "${item.name} / ${item.id}"
}

internal fun xiaozhiVoiceSummary(voice: String, voices: List<XiaozhiVoiceOption>): String {
    if (voice.isBlank()) return "未选择，留空使用控制台当前角色"
    val item = voices.firstOrNull { it.id == voice }
    return if (item == null) "已保存: $voice" else "${item.name} / ${item.id}"
}

internal fun splitAutoReplyNames(value: String): List<String> =
    value.split(";;;", "|", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

@Composable
internal fun AutoReplyAiPage(context: Context, sp: SharedPreferences, onBack: () -> Unit) {
    val settings = remember { AutoReplySettings(context) }
    val scope = rememberCoroutineScope()
    var xiaozhiServe by remember { mutableStateOf(settings.xiaozhiConfig().serveUrl) }
    var xiaozhiOta by remember { mutableStateOf(settings.xiaozhiConfig().otaUrl) }
    var xiaozhiConsole by remember { mutableStateOf(settings.xiaozhiConfig().consoleUrl) }
    var xiaozhiConsolePhone by remember { mutableStateOf(settings.xiaozhiConfig().consolePhone) }
    var xiaozhiConsoleToken by remember { mutableStateOf(settings.xiaozhiConfig().consoleToken) }
    var xiaozhiConsoleAgentId by remember { mutableStateOf(settings.xiaozhiConfig().consoleAgentId) }
    var xiaozhiConsoleModel by remember { mutableStateOf(settings.xiaozhiConfig().consoleModel) }
    var xiaozhiVoiceRole by remember { mutableStateOf(settings.xiaozhiConfig().voiceRole) }
    var xiaozhiMusicMcp by remember { mutableStateOf(settings.xiaozhiConfig().musicMcpEnabled) }
    var xiaozhiMcpBridge by remember { mutableStateOf(settings.xiaozhiConfig().mcpBridgeEnabled) }
    var xiaozhiMcpEndpoint by remember { mutableStateOf(settings.xiaozhiConfig().mcpEndpointUrl) }
    var xiaozhiMcpKugouEnabled by remember { mutableStateOf(settings.xiaozhiConfig().mcpKugouEnabled) }
    var xiaozhiMcpKugouPluginId by remember { mutableStateOf(settings.xiaozhiConfig().mcpKugouPluginId) }
    var xiaozhiMcpKugouFunction by remember { mutableStateOf(settings.xiaozhiConfig().mcpKugouFunctionName) }
    var xiaozhiMcpReadySeconds by remember { mutableStateOf(settings.xiaozhiConfig().mcpReadySeconds.toString()) }
    var xiaozhiMcpIdleSeconds by remember { mutableStateOf(settings.xiaozhiConfig().mcpIdleSeconds.toString()) }
    var xiaozhiMcpStatus by remember { mutableStateOf(XiaozhiMcpStatus(false, "未查询", "点击刷新查询小智控制台")) }
    var configs by remember { mutableStateOf(settings.zhiliaConfigs()) }
    var activeName by remember { mutableStateOf(settings.activeZhiliaName()) }
    val activeConfig = configs.firstOrNull { it.name == activeName } ?: configs.firstOrNull() ?: settings.activeZhiliaConfig()
    var cfgName by remember(activeName, configs) { mutableStateOf(activeConfig.name) }
    var apiKey by remember(activeName, configs) { mutableStateOf(activeConfig.apiKey) }
    var apiBase by remember(activeName, configs) { mutableStateOf(activeConfig.apiBaseUrl) }
    var apiPath by remember(activeName, configs) { mutableStateOf(activeConfig.apiPath) }
    var model by remember(activeName, configs) { mutableStateOf(activeConfig.model) }
    var prompt by remember(activeName, configs) { mutableStateOf(activeConfig.systemPrompt) }
    var contextLimit by remember(activeName, configs) { mutableStateOf(activeConfig.contextLimit.toString()) }
    var modelQuery by remember { mutableStateOf("") }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var favoriteModels by remember(apiBase) { mutableStateOf(settings.favoriteModels(apiBase)) }
    var loadingText by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }
    var xiaozhiBindResult by remember { mutableStateOf("") }
    var xiaozhiCaptchaSvg by remember { mutableStateOf("") }
    var xiaozhiCaptchaCode by remember { mutableStateOf("") }
    var xiaozhiSmsCode by remember { mutableStateOf("") }
    var xiaozhiConsoleResult by remember { mutableStateOf("") }
    var xiaozhiAgents by remember { mutableStateOf<List<XiaozhiAgentOption>>(emptyList()) }
    var xiaozhiModels by remember { mutableStateOf<List<XiaozhiModelOption>>(emptyList()) }
    var xiaozhiVoices by remember { mutableStateOf<List<XiaozhiVoiceOption>>(emptyList()) }
    var route by remember { mutableStateOf<AutoReplyAiRoute>(AutoReplyAiRoute.Main) }
    var zhiliaPendingName by remember { mutableStateOf(activeName) }
    var pendingModel by remember { mutableStateOf(model) }
    val mainListState = rememberLazyListState()
    val xiaozhiListState = rememberLazyListState()
    val zhiliaListState = rememberLazyListState()
    val zhiliaConfigListState = rememberLazyListState()
    val zhiliaModelListState = rememberLazyListState()

    fun currentZhiliaConfig(): AutoReplyZhiliaConfig {
        return AutoReplyZhiliaConfig(
            name = cfgName,
            apiKey = apiKey.trim(),
            apiBaseUrl = apiBase.trim(),
            apiPath = apiPath.trim(),
            model = model.trim(),
            systemPrompt = prompt,
            contextLimit = contextLimit.toIntOrNull() ?: AutoReplySettings.DEFAULT_AI_CONTEXT_LIMIT
        ).normalized()
    }

    fun currentXiaozhiConfig(): AutoReplyXiaozhiConfig {
        return AutoReplyXiaozhiConfig(
            serveUrl = xiaozhiServe,
            otaUrl = xiaozhiOta,
            consoleUrl = xiaozhiConsole,
            consolePhone = xiaozhiConsolePhone,
            consoleToken = xiaozhiConsoleToken,
            consoleAgentId = xiaozhiConsoleAgentId,
            consoleModel = xiaozhiConsoleModel,
            voiceRole = xiaozhiVoiceRole,
            musicMcpEnabled = xiaozhiMusicMcp,
            mcpBridgeEnabled = xiaozhiMcpBridge,
            mcpEndpointUrl = xiaozhiMcpEndpoint,
            mcpKugouEnabled = xiaozhiMcpKugouEnabled,
            mcpKugouPluginId = xiaozhiMcpKugouPluginId,
            mcpKugouFunctionName = xiaozhiMcpKugouFunction,
            mcpReadySeconds = xiaozhiMcpReadySeconds.toIntOrNull()?.coerceIn(1, 30)
                ?: AutoReplySettings.DEFAULT_XIAOZHI_MCP_READY_SECONDS,
            mcpIdleSeconds = xiaozhiMcpIdleSeconds.toIntOrNull()?.coerceIn(10, 600)
                ?: AutoReplySettings.DEFAULT_XIAOZHI_MCP_IDLE_SECONDS
        )
    }

    fun saveXiaozhi(showToast: Boolean) {
        settings.saveXiaozhiConfig(currentXiaozhiConfig())
        if (showToast) Toast.makeText(context, "小智AI配置已保存", Toast.LENGTH_SHORT).show()
    }

    fun xiaozhiNeedToken(): Boolean {
        if (xiaozhiConsoleToken.trim().isBlank()) {
            xiaozhiConsoleResult = "请先登录小智控制台"
            Toast.makeText(context, "请先登录小智控制台", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    fun refreshXiaozhiCaptcha(updateStatus: Boolean = true) {
        if (updateStatus) xiaozhiConsoleResult = "正在刷新图形验证码..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.fetchCaptchaSvg() }
            }
            result.onSuccess {
                xiaozhiCaptchaSvg = it
                xiaozhiCaptchaCode = ""
                if (updateStatus) xiaozhiConsoleResult = "请输入图形验证码"
            }.onFailure {
                xiaozhiConsoleResult = "刷新图形验证码失败: ${it.message}"
            }
        }
    }

    fun sendXiaozhiSmsCode() {
        val phone = xiaozhiConsolePhone.trim()
        val captcha = xiaozhiCaptchaCode.trim()
        if (phone.isBlank() || captcha.isBlank()) {
            Toast.makeText(context, "请填写手机号和图形验证码", Toast.LENGTH_SHORT).show()
            return
        }
        settings.saveXiaozhiConfig(currentXiaozhiConfig())
        xiaozhiConsoleResult = "正在发送短信验证码..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.sendSmsCode(phone, captcha) }
            }
            result.onSuccess {
                xiaozhiConsoleResult = it
                xiaozhiCaptchaCode = ""
                refreshXiaozhiCaptcha(updateStatus = false)
            }.onFailure {
                xiaozhiConsoleResult = "发送短信验证码失败: ${it.message}"
                xiaozhiCaptchaCode = ""
                refreshXiaozhiCaptcha(updateStatus = false)
            }
            Toast.makeText(context, xiaozhiConsoleResult, Toast.LENGTH_SHORT).show()
        }
    }

    fun loginXiaozhiConsole() {
        val phone = xiaozhiConsolePhone.trim()
        val sms = xiaozhiSmsCode.trim()
        if (phone.isBlank() || sms.isBlank()) {
            Toast.makeText(context, "请填写手机号和短信验证码", Toast.LENGTH_SHORT).show()
            return
        }
        xiaozhiConsoleResult = "正在登录小智控制台..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.phoneLogin(phone, sms) }
            }
            result.onSuccess { token ->
                xiaozhiConsoleToken = token
                xiaozhiSmsCode = ""
                xiaozhiCaptchaCode = ""
                settings.saveXiaozhiConfig(currentXiaozhiConfig())
                xiaozhiConsoleResult = "登录成功，token 已保存"
                Toast.makeText(context, "小智控制台登录成功", Toast.LENGTH_SHORT).show()
                val agentsResult = withContext(Dispatchers.IO) {
                    runCatching { XiaozhiConsoleApi.fetchAgents(token) }
                }
                agentsResult.onSuccess { agents ->
                    xiaozhiAgents = agents
                    agents.firstOrNull()?.let { agent ->
                        if (xiaozhiConsoleAgentId.isBlank()) xiaozhiConsoleAgentId = agent.id
                        if (xiaozhiConsoleModel.isBlank()) xiaozhiConsoleModel = agent.model
                        if (xiaozhiVoiceRole.isBlank()) xiaozhiVoiceRole = agent.voice
                        settings.saveXiaozhiConfig(currentXiaozhiConfig())
                    }
                    xiaozhiConsoleResult = "登录成功，已拉取 ${agents.size} 个智能体"
                }.onFailure {
                    xiaozhiConsoleResult = "登录成功，拉取智能体失败: ${it.message}"
                }
            }.onFailure {
                xiaozhiConsoleResult = "登录失败: ${it.message}"
            }
        }
    }

    fun fetchXiaozhiAgents(openPicker: Boolean) {
        if (xiaozhiNeedToken()) return
        xiaozhiConsoleResult = "正在拉取智能体..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.fetchAgents(xiaozhiConsoleToken) }
            }
            result.onSuccess { agents ->
                xiaozhiAgents = agents
                xiaozhiConsoleResult = "已拉取 ${agents.size} 个智能体"
                if (openPicker && agents.isNotEmpty()) {
                    val currentIndex = agents.indexOfFirst { it.id == xiaozhiConsoleAgentId }.coerceAtLeast(0)
                    route = AutoReplyAiRoute.OptionPicker(
                        OptionPickerRequest(
                            title = "选择小智智能体",
                            options = agents.mapIndexed { index, agent ->
                                OptionItem(
                                    label = agent.name,
                                    value = index,
                                    summary = listOf(agent.assistantName, agent.model, agent.voice).filter { it.isNotBlank() }.joinToString(" / ")
                                )
                            },
                            currentValue = currentIndex,
                            onSelected = { selected ->
                                agents.getOrNull(selected.value)?.let { agent ->
                                    xiaozhiConsoleAgentId = agent.id
                                    xiaozhiConsoleModel = agent.model
                                    xiaozhiVoiceRole = agent.voice
                                    settings.saveXiaozhiConfig(currentXiaozhiConfig())
                                }
                                route = AutoReplyAiRoute.Xiaozhi
                            }
                        )
                    )
                }
            }.onFailure {
                xiaozhiConsoleResult = "拉取智能体失败: ${it.message}"
            }
        }
    }

    fun fetchXiaozhiModels(openPicker: Boolean) {
        if (xiaozhiNeedToken()) return
        xiaozhiConsoleResult = "正在拉取模型列表..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.fetchModels(xiaozhiConsoleToken) }
            }
            result.onSuccess { models ->
                xiaozhiModels = models
                xiaozhiConsoleResult = "已拉取 ${models.size} 个模型"
                if (openPicker && models.isNotEmpty()) {
                    val currentIndex = models.indexOfFirst { it.id == xiaozhiConsoleModel }.coerceAtLeast(0)
                    route = AutoReplyAiRoute.OptionPicker(
                        OptionPickerRequest(
                            title = "选择小智模型",
                            options = models.mapIndexed { index, item -> OptionItem(item.name, index, item.id) },
                            currentValue = currentIndex,
                            onSelected = { selected ->
                                models.getOrNull(selected.value)?.let { item ->
                                    xiaozhiConsoleModel = item.id
                                    settings.saveXiaozhiConfig(currentXiaozhiConfig())
                                }
                                route = AutoReplyAiRoute.Xiaozhi
                            }
                        )
                    )
                }
            }.onFailure {
                xiaozhiConsoleResult = "拉取模型失败: ${it.message}"
            }
        }
    }

    fun fetchXiaozhiVoices(openPicker: Boolean) {
        if (xiaozhiNeedToken()) return
        xiaozhiConsoleResult = "正在拉取语音角色..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { XiaozhiConsoleApi.fetchVoices(xiaozhiConsoleToken) }
            }
            result.onSuccess { voices ->
                xiaozhiVoices = voices
                xiaozhiConsoleResult = "已拉取 ${voices.size} 个语音角色"
                if (openPicker && voices.isNotEmpty()) {
                    val currentIndex = voices.indexOfFirst { it.id == xiaozhiVoiceRole }.coerceAtLeast(0)
                    route = AutoReplyAiRoute.OptionPicker(
                        OptionPickerRequest(
                            title = "选择语音角色",
                            options = voices.mapIndexed { index, item ->
                                OptionItem(item.name, index, listOf(item.id, item.languages.joinToString(",")).filter { it.isNotBlank() }.joinToString(" / "))
                            },
                            currentValue = currentIndex,
                            onSelected = { selected ->
                                voices.getOrNull(selected.value)?.let { item ->
                                    xiaozhiVoiceRole = item.id
                                    settings.saveXiaozhiConfig(currentXiaozhiConfig())
                                }
                                route = AutoReplyAiRoute.Xiaozhi
                            }
                        )
                    )
                }
            }.onFailure {
                xiaozhiConsoleResult = "拉取语音角色失败: ${it.message}"
            }
        }
    }

    fun saveXiaozhiConsoleAgentConfig() {
        if (xiaozhiNeedToken()) return
        val agentId = xiaozhiConsoleAgentId.trim()
        if (agentId.isBlank()) {
            Toast.makeText(context, "请先选择智能体", Toast.LENGTH_SHORT).show()
            return
        }
        settings.saveXiaozhiConfig(currentXiaozhiConfig())
        xiaozhiConsoleResult = "正在保存到小智控制台..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    XiaozhiConsoleApi.saveAgentConfig(
                        token = xiaozhiConsoleToken,
                        agentId = agentId,
                        model = xiaozhiConsoleModel,
                        voice = xiaozhiVoiceRole
                    )
                }
            }
            result.onSuccess { agent ->
                xiaozhiConsoleAgentId = agent.id
                xiaozhiConsoleModel = agent.model
                xiaozhiVoiceRole = agent.voice
                settings.saveXiaozhiConfig(currentXiaozhiConfig())
                xiaozhiConsoleResult = "已保存到控制台: ${agent.name}"
                Toast.makeText(context, "小智控制台配置已保存", Toast.LENGTH_SHORT).show()
            }.onFailure {
                xiaozhiConsoleResult = "保存到控制台失败: ${it.message}"
            }
        }
    }

    fun saveZhilia(showToast: Boolean) {
        val saved = currentZhiliaConfig()
        val next = configs.filterNot { it.name == activeName || it.name == saved.name } + saved
        settings.saveZhiliaConfigs(next, saved.name)
        configs = settings.zhiliaConfigs()
        activeName = saved.name
        if (sp.getBoolean(AutoReplySettings.KEY_AI_CLEAR_CONTEXT_ON_SAVE, AutoReplySettings.DEFAULT_AI_CLEAR_CONTEXT_ON_SAVE)) {
            AutoReplyRuntime.clearAiHistories()
        }
        if (showToast) Toast.makeText(context, "智聊AI配置已保存", Toast.LENGTH_SHORT).show()
    }

    fun switchZhiliaTo(name: String) {
        if (name.isBlank()) {
            Toast.makeText(context, "请选择配置", Toast.LENGTH_SHORT).show()
            return
        }
        if (name == activeName) {
            zhiliaPendingName = activeName
            Toast.makeText(context, "已是当前配置", Toast.LENGTH_SHORT).show()
            return
        }
        saveZhilia(showToast = false)
        settings.saveZhiliaConfigs(settings.zhiliaConfigs(), name)
        configs = settings.zhiliaConfigs()
        activeName = settings.activeZhiliaName()
        zhiliaPendingName = activeName
        Toast.makeText(context, "已切换到 $activeName", Toast.LENGTH_SHORT).show()
    }

    fun uniqueConfigName(base: String): String {
        val names = configs.map { it.name }.toSet()
        if (base !in names) return base
        var index = 2
        while ("$base$index" in names) index++
        return "$base$index"
    }

    val visibleModels = remember(fetchedModels, modelQuery, favoriteModels) {
        val keyword = modelQuery.trim().lowercase(Locale.US)
        fetchedModels
            .filter { keyword.isBlank() || it.lowercase(Locale.US).contains(keyword) }
            .sortedWith(compareByDescending<String> { it in favoriteModels }.thenBy { it.lowercase(Locale.US) })
    }

    SettingsRouteTransition(
        targetState = route,
        label = "AutoReplyAiRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            AutoReplyAiRoute.Main -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "AI 配置",
                    largeTitle = "AI 配置",
                    scrollBehavior = scrollBehavior,
                    bottomBar = { BottomActionBar("返回", onBack) }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = mainListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
                    ) {
                        item { SmallTitle(text = "配置分组") }
                        item {
                            SettingsCard {
                                ActionRow("小智AI配置", xiaozhiServe.ifBlank { "未设置 WebSocket 地址" }) {
                                    route = AutoReplyAiRoute.Xiaozhi
                                }
                                InsetDivider()
                                ActionRow("智聊AI配置", "当前启用：$activeName") {
                                    route = AutoReplyAiRoute.Zhilia
                                }
                            }
                        }
                    }
                }
            }
            AutoReplyAiRoute.Xiaozhi -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "小智AI配置",
                    largeTitle = "小智AI配置",
                    scrollBehavior = scrollBehavior,
                    bottomBar = {
                        BottomActionBar(
                            primaryText = "保存小智",
                            onPrimaryClick = { saveXiaozhi(showToast = true) },
                            secondaryText = "返回",
                            onSecondaryClick = { route = AutoReplyAiRoute.Main }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = xiaozhiListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
                    ) {
                        item { SmallTitle(text = "连接地址") }
                        item {
                            SettingsCard {
                                InputRow("WebSocket 地址", "小智服务地址", xiaozhiServe) { xiaozhiServe = it }
                                InsetDivider()
                                InputRow("OTA 地址", "小智 OTA 地址", xiaozhiOta) { xiaozhiOta = it }
                                InsetDivider()
                                InputRow("控制台地址", "用于查看或绑定设备", xiaozhiConsole) { xiaozhiConsole = it }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "设备") }
                        item {
                            SettingsCard {
                                InfoRow("UUID", AutoReplyRuntime.xiaozhiDeviceUuid(context))
                                InsetDivider()
                                InfoRow("MAC 地址", AutoReplyRuntime.xiaozhiDeviceMac(context))
                                InsetDivider()
                                ActionRow("绑定设备", xiaozhiBindResult.ifBlank { "请求 OTA 获取验证码或绑定状态" }) {
                                    xiaozhiBindResult = "正在请求..."
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            AutoReplyRuntime.bindXiaozhiDevice(context, currentXiaozhiConfig())
                                        }
                                        xiaozhiBindResult = result
                                        Toast.makeText(context, result.lineSequence().firstOrNull().orEmpty(), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                val xiaozhiBindLink = remember(xiaozhiBindResult) { extractFirstHttpUrl(xiaozhiBindResult) }
                                if (xiaozhiBindLink != null) {
                                    InsetDivider()
                                    InfoRow("绑定链接", xiaozhiBindLink) {
                                        openExternalLink(context, xiaozhiBindLink)
                                    }
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "控制台登录") }
                        item {
                            SettingsCard {
                                InfoRow("登录状态", if (xiaozhiConsoleToken.isBlank()) "未登录" else "已保存 token")
                                InsetDivider()
                                InputRow("手机号", "登录成功后会记住手机号", xiaozhiConsolePhone) { xiaozhiConsolePhone = it }
                                InsetDivider()
                                ActionRow("刷新图形验证码", if (xiaozhiCaptchaSvg.isBlank()) "先刷新，再输入图片上的字符" else "已刷新，输入下方图形验证码") {
                                    refreshXiaozhiCaptcha()
                                }
                                if (xiaozhiCaptchaSvg.isNotBlank()) {
                                    InsetDivider()
                                    XiaozhiCaptchaSvgView(xiaozhiCaptchaSvg)
                                }
                                InsetDivider()
                                InputRow("图形验证码", "输入上方图片里的字符", xiaozhiCaptchaCode) { xiaozhiCaptchaCode = it }
                                InsetDivider()
                                ActionRow("发送短信验证码", "需要先填写手机号和图形验证码") {
                                    sendXiaozhiSmsCode()
                                }
                                InsetDivider()
                                InputRow("短信验证码", "输入手机收到的验证码", xiaozhiSmsCode) { xiaozhiSmsCode = it }
                                InsetDivider()
                                ActionRow("登录并保存 token", "登录成功后自动保存 token 并拉取智能体") {
                                    loginXiaozhiConsole()
                                }
                                if (xiaozhiConsoleResult.isNotBlank()) {
                                    InsetDivider()
                                    InfoRow("控制台状态", xiaozhiConsoleResult)
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "控制台配置") }
                        item {
                            SettingsCard {
                                ActionRow("选择智能体", xiaozhiAgentSummary(xiaozhiConsoleAgentId, xiaozhiAgents)) {
                                    fetchXiaozhiAgents(openPicker = true)
                                }
                                InsetDivider()
                                InfoRow("Agent ID", xiaozhiConsoleAgentId.ifBlank { "未选择" })
                                InsetDivider()
                                ActionRow("选择模型", xiaozhiModelSummary(xiaozhiConsoleModel, xiaozhiModels)) {
                                    fetchXiaozhiModels(openPicker = true)
                                }
                                InsetDivider()
                                ActionRow("选择语音角色", xiaozhiVoiceSummary(xiaozhiVoiceRole, xiaozhiVoices)) {
                                    fetchXiaozhiVoices(openPicker = true)
                                }
                                InsetDivider()
                                ActionRow("保存到小智控制台", "读取当前智能体配置后只替换模型和语音角色") {
                                    saveXiaozhiConsoleAgentConfig()
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "回复") }
                        item {
                            SettingsCard {
                                InputRow("语音角色ID", "留空使用控制台当前角色", xiaozhiVoiceRole) { xiaozhiVoiceRole = it }
                                InsetDivider()
                                SwitchRow(
                                    checked = xiaozhiMusicMcp,
                                    title = "提示官方 Music 工具",
                                    summary = "点歌类问题会提示小智优先使用控制台启用的官方 Music MCP",
                                    onCheckedChange = { xiaozhiMusicMcp = it }
                                )
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "MCP桥接") }
                        item {
                            SettingsCard {
                                SwitchRow(
                                    checked = xiaozhiMcpBridge,
                                    title = "启用MCP桥接实验",
                                    summary = "把 Hchat 能力暴露给小智调用；端点地址包含 token，请勿外传",
                                    onCheckedChange = { xiaozhiMcpBridge = it }
                                )
                                InsetDivider()
                                InputRow(
                                    "MCP Endpoint",
                                    "wss://api.xiaozhi.me/mcp/?token=...；留空不连接",
                                    xiaozhiMcpEndpoint
                                ) { xiaozhiMcpEndpoint = it }
                                InsetDivider()
                                XiaozhiMcpStatusRow(xiaozhiMcpStatus)
                                InsetDivider()
                                ActionRow("刷新连接状态", xiaozhiMcpStatus.detail) {
                                    if (xiaozhiConsoleToken.trim().isBlank() || xiaozhiConsoleAgentId.trim().isBlank()) {
                                        Toast.makeText(context, "请先登录并选择智能体", Toast.LENGTH_SHORT).show()
                                    } else {
                                        xiaozhiMcpStatus = XiaozhiMcpStatus(false, "查询中", "正在查询小智控制台...")
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    XiaozhiConsoleApi.fetchMcpEndpointStatus(
                                                        xiaozhiConsoleToken,
                                                        xiaozhiConsoleAgentId
                                                    )
                                                }
                                            }
                                            result.onSuccess {
                                                xiaozhiMcpStatus = it
                                            }.onFailure {
                                                xiaozhiMcpStatus = XiaozhiMcpStatus(false, "查询失败", it.message.orEmpty())
                                            }
                                        }
                                    }
                                }
                                InsetDivider()
                                InputRow(
                                    "就绪等待时长",
                                    "断开后首次触发等待 MCP ready 的秒数，默认 5，可填 1-30",
                                    xiaozhiMcpReadySeconds
                                ) { xiaozhiMcpReadySeconds = it.filter { ch -> ch.isDigit() }.take(2) }
                                InsetDivider()
                                InputRow(
                                    "自动断开时长",
                                    "空闲多少秒后断开 MCP，默认 90，可填 10-600",
                                    xiaozhiMcpIdleSeconds
                                ) { xiaozhiMcpIdleSeconds = it.filter { ch -> ch.isDigit() }.take(3) }
                                InsetDivider()
                                SwitchRow(
                                    checked = xiaozhiMcpKugouEnabled,
                                    title = "调用点歌工具",
                                    summary = "让小智理解自然语言后调用本地点歌插件发送音乐卡片",
                                    onCheckedChange = { xiaozhiMcpKugouEnabled = it }
                                )
                                if (xiaozhiMcpKugouEnabled) {
                                    InsetDivider()
                                    InputRow(
                                        "点歌插件ID",
                                        "例如 QQ点歌",
                                        xiaozhiMcpKugouPluginId
                                    ) { xiaozhiMcpKugouPluginId = it }
                                    InsetDivider()
                                    InputRow(
                                        "点歌函数名",
                                        "例如 queryKugouMusic",
                                        xiaozhiMcpKugouFunction
                                    ) { xiaozhiMcpKugouFunction = it }
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行状态") }
                        item {
                            SettingsCard {
                                ActionRow("立即清空上下文", "清除当前运行中的小智对话记忆") {
                                    AutoReplyRuntime.clearAiHistories()
                                    Toast.makeText(context, "小智上下文已清空", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            AutoReplyAiRoute.Zhilia -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "智聊AI配置",
                    largeTitle = "智聊AI配置",
                    scrollBehavior = scrollBehavior,
                    bottomBar = {
                        BottomActionBar(
                            primaryText = "保存智聊",
                            onPrimaryClick = { saveZhilia(showToast = true) },
                            secondaryText = "返回",
                            onSecondaryClick = { route = AutoReplyAiRoute.Main }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = zhiliaListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
                    ) {
                        item { SmallTitle(text = "当前配置") }
                        item {
                            SettingsCard {
                                InfoRow("当前启用", activeName)
                                InsetDivider()
                                InputRow("配置名称", "例如 DeepSeek 主账号", cfgName) { cfgName = it }
                                InsetDivider()
                                InputRow("API Key", "OpenAI 兼容接口密钥", apiKey) { apiKey = it }
                                InsetDivider()
                                InputRow("API 地址", "例如 https://api.xxx.com/v1", apiBase) { apiBase = it }
                                InsetDivider()
                                InputRow("API 路径", "默认 /chat/completions", apiPath) { apiPath = it }
                                InsetDivider()
                                InputRow("模型", "例如 deepseek-ai/DeepSeek-V3", model) { model = it }
                                InsetDivider()
                                InputRow("系统指令", "AI 角色设定和回复要求", prompt, minLines = 4) { prompt = it }
                                InsetDivider()
                                NumberInputRow("上下文轮数", "0 表示不保留上下文", contextLimit) { contextLimit = it }
                                InsetDivider()
                                SwitchRow(sp, AutoReplySettings.KEY_AI_STREAM, "流式请求", "失败时会自动尝试非流式", AutoReplySettings.DEFAULT_AI_STREAM)
                                InsetDivider()
                                SwitchRow(sp, AutoReplySettings.KEY_AI_CLEAR_CONTEXT_ON_SAVE, "保存后清空上下文", "修改人设或模型后避免继续沿用旧对话", AutoReplySettings.DEFAULT_AI_CLEAR_CONTEXT_ON_SAVE)
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "配置管理") }
                        item {
                            SettingsCard {
                                ActionRow("配置列表", "${configs.size} 个配置，当前启用：$activeName") {
                                    zhiliaPendingName = activeName
                                    route = AutoReplyAiRoute.ZhiliaConfigs
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模型工具") }
                        item {
                            SettingsCard {
                                ActionRow("模型选择", model.ifBlank { "未设置模型" }) {
                                    pendingModel = model
                                    route = AutoReplyAiRoute.ZhiliaModels
                                }
                                InsetDivider()
                                ActionRow("测试连通性", testResult.ifBlank { "测试当前模型的流式和非流式请求" }) {
                                    val config = currentZhiliaConfig().toAiConfig(sp.getBoolean(AutoReplySettings.KEY_AI_STREAM, AutoReplySettings.DEFAULT_AI_STREAM))
                                    testResult = "正在测试..."
                                    scope.launch {
                                        testResult = withContext(Dispatchers.IO) {
                                            AutoReplyRuntime.testAiConnectivity(config)
                                        }
                                        Toast.makeText(context, testResult, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行状态") }
                        item {
                            SettingsCard {
                                ActionRow("立即清空上下文", "清除当前运行中的 AI 对话记忆") {
                                    AutoReplyRuntime.clearAiHistories()
                                    Toast.makeText(context, "AI 上下文已清空", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            AutoReplyAiRoute.ZhiliaConfigs -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "智聊配置列表",
                    largeTitle = "智聊配置列表",
                    scrollBehavior = scrollBehavior,
                    bottomBar = {
                        BottomActionBar(
                            primaryText = "切换到所选",
                            onPrimaryClick = { switchZhiliaTo(zhiliaPendingName) },
                            secondaryText = "返回",
                            onSecondaryClick = { route = AutoReplyAiRoute.Zhilia }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = zhiliaConfigListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
                    ) {
                        item { SmallTitle(text = "配置") }
                        configs.forEach { config ->
                            item {
                                SettingsCard(modifier = Modifier.padding(bottom = 8.dp)) {
                                    ActionRow(
                                        title = config.name,
                                        summary = buildString {
                                            append(config.model.ifBlank { "未设置模型" })
                                            if (config.name == activeName) append(" · 当前")
                                            if (config.name == zhiliaPendingName && config.name != activeName) append(" · 已选择")
                                        }
                                    ) {
                                        zhiliaPendingName = config.name
                                        Toast.makeText(context, "已选择 ${config.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "管理") }
                        item {
                            SettingsCard {
                                ActionRow("新增空配置", "创建后留在此页，可再手动切换") {
                                    val newName = uniqueConfigName("新配置")
                                    val next = configs + AutoReplyZhiliaConfig(newName, "", AutoReplySettings.DEFAULT_AI_API_BASE, AutoReplySettings.DEFAULT_AI_API_PATH, AutoReplySettings.DEFAULT_AI_MODEL, "", AutoReplySettings.DEFAULT_AI_CONTEXT_LIMIT)
                                    settings.saveZhiliaConfigs(next, activeName)
                                    configs = settings.zhiliaConfigs()
                                    zhiliaPendingName = newName
                                    Toast.makeText(context, "已新增 $newName", Toast.LENGTH_SHORT).show()
                                }
                                InsetDivider()
                                ActionRow("复制当前输入", "按智聊编辑页当前输入复制配置") {
                                    val newName = uniqueConfigName("${cfgName.ifBlank { activeName }}_副本")
                                    val next = configs + currentZhiliaConfig().copy(name = newName)
                                    settings.saveZhiliaConfigs(next, activeName)
                                    configs = settings.zhiliaConfigs()
                                    zhiliaPendingName = newName
                                    Toast.makeText(context, "已复制 $newName", Toast.LENGTH_SHORT).show()
                                }
                                if (configs.size > 1) {
                                    InsetDivider()
                                    ActionRow("删除当前配置", "删除正在编辑的配置，至少保留一个") {
                                        val next = configs.filterNot { it.name == activeName || it.name == cfgName }
                                        val nextActive = next.firstOrNull()?.name ?: AutoReplySettings.DEFAULT_ZHILIA_CONFIG_NAME
                                        settings.saveZhiliaConfigs(next, nextActive)
                                        configs = settings.zhiliaConfigs()
                                        activeName = settings.activeZhiliaName()
                                        zhiliaPendingName = activeName
                                        Toast.makeText(context, "已删除当前配置", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            AutoReplyAiRoute.ZhiliaModels -> {
                val scrollBehavior = MiuixScrollBehavior()
                PageScaffold(
                    title = "智聊模型选择",
                    largeTitle = "智聊模型选择",
                    scrollBehavior = scrollBehavior,
                    bottomBar = {
                        BottomActionBar(
                            primaryText = "使用所选模型",
                            onPrimaryClick = {
                                model = pendingModel
                                Toast.makeText(context, "已选择模型: $pendingModel", Toast.LENGTH_SHORT).show()
                            },
                            secondaryText = "返回",
                            onSecondaryClick = { route = AutoReplyAiRoute.Zhilia }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = zhiliaModelListState,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
                    ) {
                        item { SmallTitle(text = "拉取模型") }
                        item {
                            SettingsCard {
                                ActionRow("拉取模型列表", if (loadingText.isBlank()) "从当前 API 地址获取 /models" else loadingText) {
                                    val base = apiBase
                                    val key = apiKey
                                    loadingText = "正在拉取模型..."
                                    scope.launch {
                                        val models = withContext(Dispatchers.IO) {
                                            AutoReplyRuntime.fetchAiModels(base, key)
                                        }
                                        fetchedModels = models
                                        loadingText = ""
                                        Toast.makeText(context, if (models.isEmpty()) "未获取到模型" else "已获取 ${models.size} 个模型", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        if (visibleModels.isNotEmpty()) {
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模型列表 · ${visibleModels.size}") }
                            item {
                                SettingsCard(modifier = Modifier.padding(bottom = 8.dp)) {
                                    InputRow("搜索", "输入模型名过滤", modelQuery) { modelQuery = it }
                                }
                            }
                            visibleModels.take(80).forEach { itemModel ->
                                item {
                                    ZhiliaModelSelectCard(
                                        modelName = itemModel,
                                        currentModel = model,
                                        pendingModel = pendingModel,
                                        favorite = itemModel in favoriteModels,
                                        onSelect = {
                                            pendingModel = itemModel
                                            Toast.makeText(context, "已选择 $itemModel", Toast.LENGTH_SHORT).show()
                                        },
                                        onToggleFavorite = {
                                            val next = if (itemModel in favoriteModels) favoriteModels - itemModel else favoriteModels + itemModel
                                            favoriteModels = next
                                            settings.saveFavoriteModels(apiBase, next)
                                            Toast.makeText(context, if (itemModel in next) "已收藏模型" else "已取消收藏", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        } else {
                            item { SettingsCard { EmptyText("暂无模型列表，先点击“拉取模型列表”。") } }
                        }
                    }
                }
            }
            is AutoReplyAiRoute.OptionPicker -> {
                OptionPickerPage(
                    request = currentRoute.request,
                    onBack = { route = AutoReplyAiRoute.Xiaozhi },
                    onSelected = { currentRoute.request.onSelected(it) }
                )
            }
        }
    }
}
