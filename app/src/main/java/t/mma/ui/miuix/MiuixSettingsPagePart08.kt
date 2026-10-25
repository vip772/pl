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
internal fun ZhiliaModelSelectCard(
    modelName: String,
    currentModel: String,
    pendingModel: String,
    favorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val selected = modelName == pendingModel
    val current = modelName == currentModel
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 14.dp
    ) {
        var pressed by remember { mutableStateOf(false) }
        val pressFeedbackColor = rememberPressFeedbackColor(pressed)
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(pressFeedbackColor)
                .responsiveTap(
                    onClick = onSelect,
                    onPressedChange = { pressed = it }
                )
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = modelName, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    val state = buildString {
                        if (current) append("当前")
                        if (selected && !current) {
                            if (isNotEmpty()) append(" · ")
                            append("已选择")
                        }
                        if (favorite) {
                            if (isNotEmpty()) append(" · ")
                            append("已收藏")
                        }
                        if (isEmpty()) append("点击选择")
                    }
                    Text(text = state, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
                }
                Text(
                    text = if (selected) "已选" else "选择",
                    color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            InsetDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggleFavorite() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    text = if (favorite) "取消收藏" else "收藏",
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun AutoReplyStepsPage(
    title: String,
    initialSteps: List<AutoReplyStep>,
    onBack: () -> Unit,
    onSave: (List<AutoReplyStep>) -> Unit
) {
    val context = LocalContext.current
    var steps by remember(initialSteps) { mutableStateOf(initialSteps.ifEmpty { listOf(AutoReplyStep()) }) }
    var optionPicker by remember { mutableStateOf<OptionPickerRequest?>(null) }
    var contactPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    fun update(index: Int, step: AutoReplyStep) {
        steps = steps.toMutableList().also { if (index in it.indices) it[index] = step }
    }
    val stepsRoute = when {
        favoritePicker != null -> AutoReplyStepsRoute.FavoritePicker(favoritePicker!!)
        optionPicker != null -> AutoReplyStepsRoute.OptionPicker(optionPicker!!)
        contactPicker != null -> AutoReplyStepsRoute.ContactPicker(contactPicker!!)
        else -> AutoReplyStepsRoute.Main
    }
    SettingsRouteTransition(
        targetState = stepsRoute,
        label = "AutoReplyStepsRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is AutoReplyStepsRoute.OptionPicker -> {
                val request = currentRoute.request
                OptionPickerPage(
                    request = request,
                    onBack = { optionPicker = null },
                    onSelected = { request.onSelected(it); optionPicker = null }
                )
            }
            is AutoReplyStepsRoute.ContactPicker -> {
                val request = currentRoute.request
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
            is AutoReplyStepsRoute.FavoritePicker -> {
                FavoritePickerPage(
                    request = currentRoute.request,
                    onBack = { favoritePicker = null }
                )
            }
            AutoReplyStepsRoute.Main -> PageScaffold(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                bottomBar = {
                    BottomActionBar(
                        primaryText = "保存回复",
                        onPrimaryClick = { onSave(steps.filter { AutoReplySettings.isAiReplyMode(it.mode) || it.content.isNotBlank() }) },
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
                    steps.forEachIndexed { index, step ->
                        item { SmallTitle(modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp), text = "第 ${index + 1} 条") }
                        item {
                            SettingsCard {
                                PopupOptionRow(
                                    title = "回复类型",
                                    summary = autoReplyStepLabel(step.mode),
                                    options = autoReplyStepOptions(),
                                    currentValue = step.mode,
                                    onValueChanged = {
                                        update(
                                            index,
                                            step.copy(
                                                mode = it,
                                                content = when {
                                                    AutoReplySettings.isAiReplyMode(it) -> ""
                                                    it == step.mode -> step.content
                                                    it == AutoReplySettings.REPLY_FAVORITE ||
                                                        step.mode == AutoReplySettings.REPLY_FAVORITE -> ""
                                                    else -> step.content
                                                }
                                            )
                                        )
                                    }
                                )
                                InsetDivider()
                                if (!AutoReplySettings.isAiReplyMode(step.mode)) {
                                    AutoReplyStepContentEditor(
                                        context = context,
                                        step = step,
                                        onStepChange = { update(index, it) },
                                        onContactPicker = { contactPicker = it },
                                        onFavoritePicker = { favoritePicker = it }
                                    )
                                    InsetDivider()
                                }
                                NumberInputRow("发送前延迟", "单位 ms", step.delayMs.toString()) {
                                    update(index, step.copy(delayMs = it.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L))
                                }
                                InsetDivider()
                                SwitchRow(step.randomDelay, "随机追加延迟", "发送前额外随机等待 0-2 秒") {
                                    update(index, step.copy(randomDelay = it))
                                }
                                InsetDivider()
                                RedPacketReplyStepActions(
                                    canMoveUp = index > 0,
                                    canMoveDown = index < steps.lastIndex,
                                    onMoveUp = {
                                        steps = steps.toMutableList().also {
                                            val item = it.removeAt(index)
                                            it.add(index - 1, item)
                                        }
                                    },
                                    onMoveDown = {
                                        steps = steps.toMutableList().also {
                                            val item = it.removeAt(index)
                                            it.add(index + 1, item)
                                        }
                                    },
                                    onDelete = { steps = steps.toMutableList().also { it.removeAt(index) }.ifEmpty { listOf(AutoReplyStep()) } }
                                )
                            }
                        }
                    }
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                    item {
                        SettingsCard {
                            ActionRow("新增回复", "添加一条按顺序发送的回复") {
                                steps = steps + AutoReplyStep(id = System.currentTimeMillis().toString(), content = "你好")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AutoReplyStepContentEditor(
    context: Context,
    step: AutoReplyStep,
    onStepChange: (AutoReplyStep) -> Unit,
    onContactPicker: (ContactPickerRequest) -> Unit,
    onFavoritePicker: (FavoritePickerRequest) -> Unit
) {
    when {
        step.mode == AutoReplySettings.REPLY_TEXT || step.mode == AutoReplySettings.REPLY_XML -> {
            VariableInputRow(
                title = if (step.mode == AutoReplySettings.REPLY_XML) "XML 内容" else "回复内容",
                summary = autoReplyStepContentHint(step.mode),
                value = step.content,
                variables = autoReplyTemplateVariables,
                minLines = 4
            ) {
                onStepChange(step.copy(content = it))
            }
        }
        autoReplyStepUsesContactPicker(step.mode) -> {
            val mode = if (step.mode == AutoReplySettings.REPLY_CARD) ContactPickerMode.FRIENDS else ContactPickerMode.GROUPS
            ActionRow(
                title = autoReplyPickerTitle(step.mode),
                summary = autoReplySelectedIdSummary(step.content)
            ) {
                onContactPicker(
                    ContactPickerRequest(
                        title = autoReplyPickerTitle(step.mode),
                        mode = mode,
                        multiSelect = true,
                        existingValue = formatIds(autoReplySplitContent(step.content)),
                        onValue = { onStepChange(step.copy(content = it)) },
                        enableLabels = step.mode == AutoReplySettings.REPLY_CARD
                    )
                )
            }
            if (step.content.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选", "移除当前已选择内容") {
                    onStepChange(step.copy(content = ""))
                }
            }
        }
        step.mode == AutoReplySettings.REPLY_FAVORITE -> {
            ActionRow(
                title = autoReplyPickerTitle(step.mode),
                summary = favoriteSummary(step.content)
            ) {
                onFavoritePicker(
                    FavoritePickerRequest(
                        title = autoReplyPickerTitle(step.mode),
                        existingValue = step.content,
                        onValue = { onStepChange(step.copy(content = it)) },
                        multiSelect = true,
                        delimiter = ";;;"
                    )
                )
            }
            if (step.content.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选收藏", "移除当前回复类型的收藏") {
                    onStepChange(step.copy(content = ""))
                }
            }
        }
        autoReplyStepUsesFilePicker(step.mode) -> {
            ActionRow(
                title = autoReplyPickerTitle(step.mode),
                summary = autoReplyFileSummary(step.content)
            ) {
                val activity = context as? Activity
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                    return@ActionRow
                }
                AutoReplyFilePickerBridge.launch(activity, step.mode) { paths ->
                    val merged = (autoReplySplitContent(step.content) + paths)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    if (merged.isNotEmpty()) {
                        onStepChange(step.copy(content = merged.joinToString(";;;")))
                        Toast.makeText(context, "已选择 ${paths.size} 个文件", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (step.content.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选文件", "移除当前回复类型的文件") {
                    onStepChange(step.copy(content = ""))
                }
            }
        }
        else -> {
            InputRow("回复内容", autoReplyStepContentHint(step.mode), step.content, minLines = 2) {
                onStepChange(step.copy(content = it))
            }
        }
    }
}

internal fun describeAutoReplyRule(rule: AutoReplyRule): String {
    val state = if (rule.enabled) "启用" else "关闭"
    val keyword = if (rule.matchType == AutoReplySettings.MATCH_ANY) "任意消息" else rule.keyword.ifBlank { "未填关键词" }
    return buildList {
        add(state)
        add(autoReplyMatchLabel(rule.matchType))
        add(keyword)
        add(autoReplyTargetLabel(rule.targetMode))
        if (rule.cooldownSeconds > 0L) add("冷却 ${rule.cooldownSeconds} 秒")
        add(describeAutoReplySteps(rule.steps))
    }.joinToString(" · ")
}

internal fun describeAutoReplySteps(steps: List<AutoReplyStep>): String {
    if (steps.isEmpty()) return "暂无回复"
    return steps.take(3).joinToString("、") { autoReplyStepLabel(it.mode) } +
        if (steps.size > 3) " 等 ${steps.size} 条" else "，共 ${steps.size} 条"
}

internal fun replaceAutoReplyTypedIds(old: Set<String>, picked: Set<String>, group: Boolean): Set<String> {
    return (old.filter { isAutoReplyGroupId(it) != group } + picked)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

internal fun isAutoReplyGroupId(value: String): Boolean =
    value.endsWith("@chatroom") || value.endsWith("@im.chatroom")

internal fun conversationRuleCategoryForId(value: String): ConversationRuleCategory {
    val normalized = value.trim().lowercase(Locale.US)
    return when {
        normalized.startsWith("gh_") || normalized.endsWith("@app") || normalized == "newsapp" -> {
            ConversationRuleCategory.OFFICIAL
        }
        normalized.endsWith("@chatroom") || normalized.endsWith("@im.chatroom") -> {
            ConversationRuleCategory.GROUP
        }
        else -> ConversationRuleCategory.FRIEND
    }
}

internal fun conversationRuleCategoryForTarget(value: String): ConversationRuleCategory {
    return conversationRuleCategoryForId(value.substringBefore('/'))
}

internal fun conversationRuleCategoryMatches(
    rule: CustomNotificationRule,
    category: ConversationRuleCategory
): Boolean {
    if (category == ConversationRuleCategory.ALL) return true
    val actual = when {
        rule.official -> ConversationRuleCategory.OFFICIAL
        rule.group -> ConversationRuleCategory.GROUP
        else -> ConversationRuleCategory.FRIEND
    }
    return actual == category
}

internal fun conversationRuleCategoryMatches(
    binding: MessageBlockBinding,
    category: ConversationRuleCategory
): Boolean {
    if (category == ConversationRuleCategory.ALL) return true
    val actual = if (binding.targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        ConversationRuleCategory.GROUP
    } else {
        conversationRuleCategoryForTarget(binding.targetId)
    }
    return actual == category
}

internal fun conversationRuleCategoryMatches(
    binding: RedPacketRuleBinding,
    category: ConversationRuleCategory
): Boolean {
    return category == ConversationRuleCategory.ALL ||
        conversationRuleCategoryForId(binding.targetId) == category
}

internal fun conversationRuleCategoryMatches(
    binding: TransferRuleBinding,
    category: ConversationRuleCategory
): Boolean {
    return category == ConversationRuleCategory.ALL ||
        conversationRuleCategoryForId(binding.targetId) == category
}

internal fun autoReplyMatchOptions(): List<OptionItem> = listOf(
    OptionItem("模糊匹配", AutoReplySettings.MATCH_FUZZY, "内容包含关键词就触发"),
    OptionItem("全字匹配", AutoReplySettings.MATCH_EXACT, "消息内容与关键词完全相同"),
    OptionItem("正则匹配", AutoReplySettings.MATCH_REGEX, "关键词按正则表达式处理"),
    OptionItem("任意消息", AutoReplySettings.MATCH_ANY, "收到符合范围的消息就触发")
)

internal fun autoReplyMatchLabel(value: Int): String = autoReplyMatchOptions().firstOrNull { it.value == value }?.label ?: "模糊匹配"

internal fun autoReplyTargetOptions(): List<OptionItem> = listOf(
    OptionItem("全部聊天", AutoReplySettings.TARGET_ALL, "好友、群聊和公众号都可触发"),
    OptionItem("仅私聊", AutoReplySettings.TARGET_PRIVATE, "只回复好友私聊"),
    OptionItem("仅群聊", AutoReplySettings.TARGET_GROUP, "只回复群消息"),
    OptionItem("指定名单", AutoReplySettings.TARGET_SPECIFIC, "只回复指定 wxid、群号或群成员"),
    OptionItem("仅公众号", AutoReplySettings.TARGET_OFFICIAL, "只回复公众号消息")
)

internal fun autoReplyTargetLabel(value: Int): String = autoReplyTargetOptions().firstOrNull { it.value == value }?.label ?: "全部聊天"

internal fun autoReplyAtOptions(): List<OptionItem> = listOf(
    OptionItem("不限", AutoReplySettings.AT_NONE),
    OptionItem("@我触发", AutoReplySettings.AT_ME),
    OptionItem("@全体触发", AutoReplySettings.AT_ALL)
)

internal fun autoReplyAtLabel(value: Int): String = autoReplyAtOptions().firstOrNull { it.value == value }?.label ?: "不限"

internal fun autoReplyPatOptions(): List<OptionItem> = listOf(
    OptionItem("不限", AutoReplySettings.PAT_NONE),
    OptionItem("被拍一拍触发", AutoReplySettings.PAT_ME)
)

internal fun autoReplyPatLabel(value: Int): String = autoReplyPatOptions().firstOrNull { it.value == value }?.label ?: "不限"

internal fun autoReplyStepOptions(): List<OptionItem> = listOf(
    OptionItem("发送文字", AutoReplySettings.REPLY_TEXT, "支持变量和 | 分隔多条"),
    OptionItem("发送图片", AutoReplySettings.REPLY_IMAGE, "从系统文件管理器选择"),
    OptionItem("发送语音", AutoReplySettings.REPLY_VOICE, "从系统文件管理器选择"),
    OptionItem("随机语音", AutoReplySettings.REPLY_VOICE_RANDOM_FOLDER, "从已选语音随机发送"),
    OptionItem("发送表情", AutoReplySettings.REPLY_EMOJI, "从系统文件管理器选择"),
    OptionItem("发送视频", AutoReplySettings.REPLY_VIDEO, "从系统文件管理器选择"),
    OptionItem("发送名片", AutoReplySettings.REPLY_CARD, "从好友列表选择"),
    OptionItem("发送文件", AutoReplySettings.REPLY_FILE, "从系统文件管理器选择"),
    OptionItem("发送收藏", AutoReplySettings.REPLY_FAVORITE, "从最近收藏选择"),
    OptionItem("邀请进群", AutoReplySettings.REPLY_INVITE_GROUP, "从群聊列表选择"),
    OptionItem("发送 XML", AutoReplySettings.REPLY_XML, "AppMsg/XML 原文"),
    OptionItem("智聊AI回复", AutoReplySettings.REPLY_ZHILIA_AI, "使用智聊/OpenAI 兼容配置生成回复"),
    OptionItem("小智AI回复", AutoReplySettings.REPLY_XIAOZHI_AI, "使用小智 WebSocket 生成文字回复"),
    OptionItem("小智语音回复", AutoReplySettings.REPLY_XIAOZHI_VOICE, "使用小智 TTS 生成语音回复")
)

internal fun autoReplyStepLabel(value: Int): String = autoReplyStepOptions().firstOrNull { it.value == value }?.label ?: "发送文字"

internal fun autoReplyStepContentHint(value: Int): String = when (value) {
    AutoReplySettings.REPLY_TEXT -> "多条文本用 | 分隔；下方变量可点击插入"
    AutoReplySettings.REPLY_XML -> "填写 XML 原文；下方变量可点击插入"
    AutoReplySettings.REPLY_CARD -> "选择要分享的好友名片"
    AutoReplySettings.REPLY_INVITE_GROUP -> "选择要邀请对方加入的群聊"
    AutoReplySettings.REPLY_FAVORITE -> "从最近收藏选择，可多选后按顺序发送"
    AutoReplySettings.REPLY_VOICE_RANDOM_FOLDER -> "选择多个语音文件后随机发送一个"
    else -> "填写文件绝对路径，每行一个或用 ;;; 分隔"
}

internal fun autoReplyStepUsesFilePicker(mode: Int): Boolean = mode in setOf(
    AutoReplySettings.REPLY_IMAGE,
    AutoReplySettings.REPLY_VOICE,
    AutoReplySettings.REPLY_VOICE_RANDOM_FOLDER,
    AutoReplySettings.REPLY_EMOJI,
    AutoReplySettings.REPLY_VIDEO,
    AutoReplySettings.REPLY_FILE
)

internal fun autoReplyStepUsesContactPicker(mode: Int): Boolean =
    mode == AutoReplySettings.REPLY_CARD || mode == AutoReplySettings.REPLY_INVITE_GROUP

internal fun autoReplyPickerTitle(mode: Int): String = when (mode) {
    AutoReplySettings.REPLY_IMAGE -> "选择图片"
    AutoReplySettings.REPLY_VOICE -> "选择语音"
    AutoReplySettings.REPLY_VOICE_RANDOM_FOLDER -> "选择随机语音"
    AutoReplySettings.REPLY_EMOJI -> "选择表情文件"
    AutoReplySettings.REPLY_VIDEO -> "选择视频"
    AutoReplySettings.REPLY_CARD -> "选择名片好友"
    AutoReplySettings.REPLY_FILE -> "选择文件"
    AutoReplySettings.REPLY_FAVORITE -> "选择收藏"
    AutoReplySettings.REPLY_INVITE_GROUP -> "选择群聊"
    else -> "选择文件"
}

internal fun scheduledTaskPickerTitle(taskType: Int): String = when (taskType) {
    ScheduledTaskSettings.TYPE_IMAGE -> "选择图片"
    ScheduledTaskSettings.TYPE_VIDEO -> "选择视频"
    ScheduledTaskSettings.TYPE_VOICE -> "选择语音文件"
    ScheduledTaskSettings.TYPE_EMOJI -> "选择表情文件"
    ScheduledTaskSettings.TYPE_FILE -> "选择文件"
    ScheduledTaskSettings.TYPE_FAVORITE -> "选择收藏"
    else -> "选择文件"
}

internal fun autoReplyFileSummary(value: String): String {
    val items = autoReplySplitContent(value)
    if (items.isEmpty()) return "未选择"
    val names = items.take(3).map { File(it).name.ifBlank { it } }
    return names.joinToString("、") + if (items.size > 3) " 等 ${items.size} 个文件" else "，共 ${items.size} 个文件"
}

internal fun autoReplySelectedIdSummary(value: String): String {
    val count = autoReplySplitContent(value).size
    return if (count == 0) "未选择" else "已选择 $count 项"
}

internal fun autoReplySplitContent(value: String): List<String> {
    return value.split(";;;")
        .flatMap { it.split('\n') }
        .flatMap { it.split('|') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

@Composable
internal fun CustomNotificationMainPage(
    context: Context,
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    rules: List<CustomNotificationRule>,
    defaultPrivateRule: CustomNotificationRule,
    defaultGroupRule: CustomNotificationRule,
    defaultOfficialRule: CustomNotificationRule,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onEditDefault: (CustomNotificationDefaultKind) -> Unit
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
            item { SmallTitle(text = "自定义通知") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        CustomNotificationSettings.KEY_ENABLE,
                        "启用自定义通知",
                        "开启后按会话专属规则或默认规则接管微信通知",
                        CustomNotificationSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    ActionRow(
                        "默认私聊通知",
                        customNotificationRuleSummary(context, defaultPrivateRule)
                    ) {
                        onEditDefault(CustomNotificationDefaultKind.PRIVATE)
                    }
                    InsetDivider()
                    ActionRow(
                        "默认群聊通知",
                        customNotificationRuleSummary(context, defaultGroupRule)
                    ) {
                        onEditDefault(CustomNotificationDefaultKind.GROUP)
                    }
                    InsetDivider()
                    ActionRow(
                        "默认公众号通知",
                        customNotificationRuleSummary(context, defaultOfficialRule)
                    ) {
                        onEditDefault(CustomNotificationDefaultKind.OFFICIAL)
                    }
                    InsetDivider()
                    ActionRow(
                        "会话规则",
                        if (rules.isEmpty()) "未配置会话" else "${rules.size} 个会话，${rules.count { it.enabled }} 个启用"
                    ) {
                        onOpenRules()
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomNotificationRuleListPage(
    context: Context,
    rules: List<CustomNotificationRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onBack: () -> Unit,
    onSaveRules: (List<CustomNotificationRule>) -> Unit,
    onEditRule: (String) -> Unit,
    onToggleRule: (CustomNotificationRule, Boolean) -> Unit,
    onBatchEdit: () -> Unit,
    onAddRules: () -> Unit
) {
    var category by remember { mutableStateOf(ConversationRuleCategory.ALL) }
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedTalkers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val lower = query.trim().lowercase(Locale.US)
    val visible = rules.filter {
        conversationRuleCategoryMatches(it, category) && (
            lower.isBlank() ||
                it.label.lowercase(Locale.US).contains(lower) ||
                it.talker.lowercase(Locale.US).contains(lower)
            )
    }.sortedWith(compareBy<CustomNotificationRule> { !it.enabled }.thenBy { it.group }.thenBy { it.label.lowercase(Locale.US) })
    val visibleTalkers = visible.mapTo(LinkedHashSet()) { it.talker }
    val allVisibleSelected = visibleTalkers.isNotEmpty() && visibleTalkers.all { it in selectedTalkers }
    val selectedRules = rules.filter { it.talker in selectedTalkers }
    PageScaffold(
        title = "会话规则",
        largeTitle = "会话规则",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedRules.size}）",
                    onPrimaryClick = {
                        if (selectedRules.isEmpty()) {
                            Toast.makeText(context, "请先选择会话规则", Toast.LENGTH_SHORT).show()
                        } else {
                            showDeleteConfirm = true
                        }
                    },
                    secondaryText = "取消",
                    onSecondaryClick = {
                        batchDeleteMode = false
                        selectedTalkers = emptySet()
                    },
                    middleText = if (visibleTalkers.isEmpty()) null else if (allVisibleSelected) "取消全选" else "全选",
                    onMiddleClick = if (visibleTalkers.isEmpty()) null else {
                        {
                            selectedTalkers = if (allVisibleSelected) {
                                selectedTalkers - visibleTalkers
                            } else {
                                selectedTalkers + visibleTalkers
                            }
                        }
                    }
                )
            } else {
                BottomActionBar(
                    primaryText = "添加会话",
                    onPrimaryClick = onAddRules,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
                    middleText = if (rules.isEmpty()) null else "批量删除",
                    onMiddleClick = if (rules.isEmpty()) null else {
                        {
                            batchDeleteMode = true
                            selectedTalkers = emptySet()
                        }
                    }
                )
            }
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
            item {
                SettingsCard {
                    ConversationRuleCategoryTabs(
                        selected = category,
                        onSelected = { category = it }
                    )
                }
            }
            item {
                SettingsCard {
                    InputRow("搜索", "昵称 / wxid / 群号", query, onValueChange = onQueryChange)
                }
            }
            if (rules.isNotEmpty() && !batchDeleteMode) {
                item {
                    SettingsCard {
                        ActionRow("批量配置", "统一修改全部 ${rules.size} 个会话规则", onBatchEdit)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = if (visible.isEmpty()) "规则" else "规则 · ${visible.size} 项") }
            when {
                rules.isEmpty() -> item { SettingsCard { EmptyText("暂无会话规则，点击底部“添加会话”。") } }
                visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                else -> visible.forEach { rule ->
                    item {
                        SettingsCard(modifier = Modifier.padding(bottom = 8.dp)) {
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = customNotificationRuleTitle(rule),
                                        value = 0,
                                        summary = customNotificationRuleSummary(context, rule)
                                    ),
                                    selected = rule.talker in selectedTalkers,
                                    onClick = {
                                        selectedTalkers = if (rule.talker in selectedTalkers) {
                                            selectedTalkers - rule.talker
                                        } else {
                                            selectedTalkers + rule.talker
                                        }
                                    }
                                )
                            } else {
                                ActionRow(
                                    title = customNotificationRuleTitle(rule),
                                    summary = customNotificationRuleSummary(context, rule)
                                ) {
                                    onEditRule(rule.talker)
                                }
                                InsetDivider()
                                SwitchRow(
                                    checked = rule.enabled,
                                    title = "启用此规则",
                                    summary = if (rule.enabled) "当前会话已接管通知" else "当前会话不接管通知"
                                ) {
                                    onToggleRule(rule, it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedRules.size} 个会话规则，此操作不可撤销。",
        labels = selectedRules.map { customNotificationRuleTitle(it) },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selectedRules
            val targetTalkers = targets.mapTo(HashSet()) { it.talker }
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedTalkers = emptySet()
            onSaveRules(rules.filterNot { it.talker in targetTalkers })
            Toast.makeText(context, "已删除 ${targets.size} 个会话规则", Toast.LENGTH_SHORT).show()
        }
    )
}

@Composable
internal fun CustomNotificationBatchEditorPage(
    ruleCount: Int,
    onBack: () -> Unit,
    onApply: (CustomNotificationBatchUpdate) -> Unit
) {
    var applyEnabled by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var applyMode by remember { mutableStateOf(false) }
    var dnd by remember { mutableStateOf(false) }
    var applyVibrate by remember { mutableStateOf(false) }
    var vibrate by remember { mutableStateOf(true) }
    var applySound by remember { mutableStateOf(false) }
    var sound by remember { mutableStateOf(true) }
    var applyMarkRead by remember { mutableStateOf(false) }
    var markRead by remember { mutableStateOf(true) }
    var applyQuickReply by remember { mutableStateOf(false) }
    var quickReply by remember { mutableStateOf(false) }
    var applyQuoteQuickReply by remember { mutableStateOf(false) }
    var quoteQuickReply by remember { mutableStateOf(false) }
    var applyMergeByTalker by remember { mutableStateOf(false) }
    var mergeByTalker by remember { mutableStateOf(false) }
    var applyShowDetail by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(true) }
    var applyIgnoreWechatDnd by remember { mutableStateOf(false) }
    var ignoreWechatDnd by remember { mutableStateOf(false) }
    var applyMute by remember { mutableStateOf(false) }
    var muteEnable by remember { mutableStateOf(false) }
    var muteStart by remember { mutableStateOf(CustomNotificationSettings.DEFAULT_MUTE_START) }
    var muteEnd by remember { mutableStateOf(CustomNotificationSettings.DEFAULT_MUTE_END) }
    var applyAtRules by remember { mutableStateOf(false) }
    var blockAtAll by remember { mutableStateOf(false) }
    var blockAtMe by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "批量配置",
        largeTitle = "批量配置",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "应用到 $ruleCount 项",
                onPrimaryClick = {
                    onApply(
                        CustomNotificationBatchUpdate(
                            applyEnabled = applyEnabled,
                            enabled = enabled,
                            applyMode = applyMode,
                            dnd = dnd,
                            applyVibrate = applyVibrate,
                            vibrate = vibrate,
                            applySound = applySound,
                            sound = sound,
                            applyMarkRead = applyMarkRead,
                            markRead = markRead,
                            applyQuickReply = applyQuickReply,
                            quickReply = quickReply,
                            applyQuoteQuickReply = applyQuoteQuickReply,
                            quoteQuickReply = quoteQuickReply,
                            applyMergeByTalker = applyMergeByTalker,
                            mergeByTalker = mergeByTalker,
                            applyShowDetail = applyShowDetail,
                            showDetail = showDetail,
                            applyIgnoreWechatDnd = applyIgnoreWechatDnd,
                            ignoreWechatDnd = ignoreWechatDnd,
                            applyMute = applyMute,
                            muteEnable = muteEnable,
                            muteStart = muteStart,
                            muteEnd = muteEnd,
                            applyAtRules = applyAtRules,
                            blockAtAll = blockAtAll,
                            blockAtMe = blockAtMe
                        )
                    )
                },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "应用范围") }
            item { SettingsCard { EmptyText("只会修改已勾选“应用”的项目，未勾选的设置保持原样。群聊专属的 @ 规则只影响群聊规则。") } }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(applyEnabled, "应用启用状态", "批量开关会话规则") { applyEnabled = it }
                    if (applyEnabled) {
                        InsetDivider()
                        SwitchRow(enabled, "启用规则", "关闭后不再接管对应会话通知") { enabled = it }
                    }
                    InsetDivider()
                    SwitchRow(applyMode, "应用免打扰", "批量设置是否弹自定义通知") { applyMode = it }
                    if (applyMode) {
                        InsetDivider()
                        SwitchRow(dnd, "免打扰", "开启后不弹自定义通知，只拦截原生通知") { dnd = it }
                    }
                    InsetDivider()
                    SwitchRow(applyIgnoreWechatDnd, "应用微信免打扰策略", "批量设置是否忽略微信原生会话免打扰") {
                        applyIgnoreWechatDnd = it
                    }
                    if (applyIgnoreWechatDnd) {
                        InsetDivider()
                        SwitchRow(
                            ignoreWechatDnd,
                            "忽略微信自带的消息免打扰",
                            "开启后，对应会话在微信中设置免打扰仍会弹出自定义通知"
                        ) { ignoreWechatDnd = it }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提醒") }
            item {
                SettingsCard {
                    SwitchRow(applyVibrate, "应用震动", "批量修改震动状态") { applyVibrate = it }
                    if (applyVibrate) {
                        InsetDivider()
                        SwitchRow(vibrate, "震动", "通知时震动") { vibrate = it }
                    }
                    InsetDivider()
                    SwitchRow(applySound, "应用铃声", "批量修改铃声状态，不修改每个会话已选铃声文件") { applySound = it }
                    if (applySound) {
                        InsetDivider()
                        SwitchRow(sound, "铃声", "通知时播放铃声") { sound = it }
                    }
                    InsetDivider()
                    SwitchRow(applyMarkRead, "应用已读按钮", "批量修改通知栏已读按钮") { applyMarkRead = it }
                    if (applyMarkRead) {
                        InsetDivider()
                        SwitchRow(markRead, "已读按钮", "通知栏直接标记当前会话已读") { markRead = it }
                    }
                    InsetDivider()
                    SwitchRow(applyQuickReply, "应用快捷回复", "批量修改通知栏快捷回复") { applyQuickReply = it }
                    if (applyQuickReply) {
                        InsetDivider()
                        SwitchRow(quickReply, "快捷回复", "通知栏直接回复文本消息") { quickReply = it }
                    }
                    InsetDivider()
                    SwitchRow(applyQuoteQuickReply, "应用引用消息回复", "批量修改快捷回复是否引用原消息") {
                        applyQuoteQuickReply = it
                    }
                    if (applyQuoteQuickReply) {
                        InsetDivider()
                        SwitchRow(quoteQuickReply, "引用消息回复", "快捷回复时引用触发通知的原消息") {
                            quoteQuickReply = it
                        }
                    }
                    InsetDivider()
                    SwitchRow(applyMergeByTalker, "应用通知展示方式", "批量设置同会话通知合并或分散") { applyMergeByTalker = it }
                    if (applyMergeByTalker) {
                        InsetDivider()
                        SwitchRow(mergeByTalker, "合并同会话通知", "同一 wxid 的新消息更新到一条通知") { mergeByTalker = it }
                    }
                    InsetDivider()
                    SwitchRow(applyShowDetail, "应用消息详情", "批量修改通知内容是否显示详情") { applyShowDetail = it }
                    if (applyShowDetail) {
                        InsetDivider()
                        SwitchRow(showDetail, "显示消息详情", "关闭后只显示收到一条新消息") { showDetail = it }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "静默与群聊") }
            item {
                SettingsCard {
                    SwitchRow(applyMute, "应用静默时段", "批量修改时段静默") { applyMute = it }
                    if (applyMute) {
                        InsetDivider()
                        SwitchRow(muteEnable, "开启时段静默", "指定时间内不弹通知") { muteEnable = it }
                        if (muteEnable) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", muteStart) { muteStart = it }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", muteEnd) { muteEnd = it }
                        }
                    }
                    InsetDivider()
                    SwitchRow(applyAtRules, "应用群聊 @ 规则", "只对群聊规则生效") { applyAtRules = it }
                    if (applyAtRules) {
                        InsetDivider()
                        SwitchRow(blockAtAll, "屏蔽@所有人", "命中 @所有人 时不弹通知") { blockAtAll = it }
                        InsetDivider()
                        SwitchRow(blockAtMe, "屏蔽@我", "命中 @我 时不弹通知") { blockAtMe = it }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomNotificationMissingRulePage(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "会话规则",
        largeTitle = "会话规则",
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SettingsCard { EmptyText("该规则已不存在") } }
        }
    }
}

@Composable
internal fun CustomNotificationRuleEditorPage(
    context: Context,
    initialRule: CustomNotificationRule,
    listState: LazyListState,
    isDefault: Boolean = false,
    onBack: () -> Unit,
    onSave: (CustomNotificationRule) -> Unit,
    onDelete: () -> Unit,
    onPickOnlyMembers: (String) -> Unit,
    onPickBlockMembers: (String) -> Unit
) {
    var enabled by remember(initialRule) { mutableStateOf(initialRule.enabled) }
    var dnd by remember(initialRule) { mutableStateOf(initialRule.mode == CustomNotificationSettings.MODE_DND) }
    var vibrate by remember(initialRule) { mutableStateOf(initialRule.vibrate) }
    var sound by remember(initialRule) { mutableStateOf(initialRule.sound) }
    var markRead by remember(initialRule) { mutableStateOf(initialRule.markRead) }
    var quickReply by remember(initialRule) { mutableStateOf(initialRule.quickReply) }
    var quoteQuickReply by remember(initialRule) { mutableStateOf(initialRule.quoteQuickReply) }
    var mergeByTalker by remember(initialRule) { mutableStateOf(initialRule.mergeByTalker) }
    var showDetail by remember(initialRule) { mutableStateOf(initialRule.showDetail) }
    var ignoreWechatDnd by remember(initialRule) { mutableStateOf(initialRule.ignoreWechatDnd) }
    var muteEnable by remember(initialRule) { mutableStateOf(initialRule.muteEnable) }
    var muteStart by remember(initialRule) { mutableStateOf(initialRule.muteStart) }
    var muteEnd by remember(initialRule) { mutableStateOf(initialRule.muteEnd) }
    var ringtone by remember(initialRule) { mutableStateOf(initialRule.ringtone) }
    var blockAtAll by remember(initialRule) { mutableStateOf(initialRule.blockAtAll) }
    var blockAtMe by remember(initialRule) { mutableStateOf(initialRule.blockAtMe) }
    var onlyMembers by remember(initialRule) { mutableStateOf(initialRule.onlyMembers) }
    var blockMembers by remember(initialRule) { mutableStateOf(initialRule.blockMembers) }
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = when {
            isDefault && initialRule.official -> "默认公众号通知"
            isDefault && initialRule.group -> "默认群聊通知"
            isDefault -> "默认私聊通知"
            initialRule.group -> "群聊通知配置"
            initialRule.official -> "公众号通知配置"
            else -> "私聊通知配置"
        },
        largeTitle = initialRule.label.ifBlank { initialRule.talker },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    onSave(
                        initialRule.copy(
                            enabled = enabled,
                            mode = if (dnd) CustomNotificationSettings.MODE_DND else CustomNotificationSettings.MODE_NOTIFY,
                            vibrate = vibrate,
                            sound = sound,
                            markRead = markRead,
                            quickReply = quickReply,
                            quoteQuickReply = quoteQuickReply,
                            mergeByTalker = mergeByTalker,
                            showDetail = showDetail,
                            ignoreWechatDnd = ignoreWechatDnd,
                            muteEnable = muteEnable,
                            muteStart = CustomNotificationSettings.normalizeTime(muteStart, CustomNotificationSettings.DEFAULT_MUTE_START),
                            muteEnd = CustomNotificationSettings.normalizeTime(muteEnd, CustomNotificationSettings.DEFAULT_MUTE_END),
                            ringtone = ringtone,
                            blockAtAll = blockAtAll,
                            blockAtMe = blockAtMe,
                            onlyMembers = if (isDefault) "" else CustomNotificationSettings.normalizeMemberRules(onlyMembers),
                            blockMembers = if (isDefault) "" else CustomNotificationSettings.normalizeMemberRules(blockMembers)
                        )
                    )
                },
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
            item { SmallTitle(text = "会话") }
            item {
                SettingsCard {
                    if (isDefault) {
                        InfoRow(
                            "类型",
                            when {
                                initialRule.official -> "未单独配置的公众号"
                                initialRule.group -> "未单独配置的群聊"
                                else -> "未单独配置的私聊"
                            }
                        )
                    } else {
                        InfoRow("名称", initialRule.label.ifBlank { initialRule.talker })
                        InsetDivider()
                        InfoRow("ID", initialRule.talker)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "通知") }
            item {
                SettingsCard {
                    SwitchRow(
                        enabled,
                        if (isDefault) "启用默认规则" else "启用此会话规则",
                        if (isDefault) "开启后接管未单独配置的对应会话通知" else "关闭后不再接管该会话通知"
                    ) { enabled = it }
                    InsetDivider()
                    SwitchRow(dnd, "免打扰", "开启后不弹自定义通知，也会拦截原生通知") { dnd = it }
                    InsetDivider()
                    SwitchRow(
                        ignoreWechatDnd,
                        "忽略微信自带的消息免打扰",
                        "开启后，该会话在微信中设置免打扰仍会弹出自定义通知"
                    ) { ignoreWechatDnd = it }
                    InsetDivider()
                    SwitchRow(vibrate, "震动", "通知时震动") { vibrate = it }
                    InsetDivider()
                    SwitchRow(sound, "铃声", "通知时播放系统或自定义铃声") { sound = it }
                    InsetDivider()
                    SwitchRow(markRead, "已读按钮", "通知栏直接标记当前会话已读") { markRead = it }
                    InsetDivider()
                    SwitchRow(quickReply, "快捷回复", "通知栏直接回复文本消息") { quickReply = it }
                    if (quickReply) {
                        InsetDivider()
                        SwitchRow(quoteQuickReply, "引用消息回复", "快捷回复时引用触发通知的原消息") {
                            quoteQuickReply = it
                        }
                    }
                    InsetDivider()
                    SwitchRow(mergeByTalker, "合并同会话通知", "同一 wxid 的新消息更新到一条通知") { mergeByTalker = it }
                    InsetDivider()
                    SwitchRow(showDetail, "通知显示消息详情", "关闭后只显示收到一条新消息") { showDetail = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "铃声") }
            item {
                SettingsCard {
                    ActionRow("选择系统铃声", ringtoneDisplayName(context, ringtone, RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM)) {
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开铃声选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            RingtonePickerBridge.launchSystem(activity, ringtone) { picked ->
                                ringtone = CustomNotificationRuntime.freezeRingtoneUri(context, picked)
                                Toast.makeText(context, "铃声已选择，保存后生效", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    InsetDivider()
                    ActionRow("从文件选择铃声", if (ringtone.isBlank()) "未选择" else ringtoneDisplayName(context, ringtone, RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM)) {
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            RingtonePickerBridge.launchFile(activity) { picked ->
                                ringtone = CustomNotificationRuntime.freezeRingtoneUri(context, picked)
                                Toast.makeText(context, "铃声已选择，保存后生效", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    if (ringtone.isNotBlank()) {
                        InsetDivider()
                        ActionRow("清空铃声", "恢复跟随系统") {
                            ringtone = ""
                            Toast.makeText(context, "铃声已清空，保存后生效", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "静默时段") }
            item {
                SettingsCard {
                    SwitchRow(muteEnable, "开启时段静默", "指定时间内不弹通知") { muteEnable = it }
                    if (muteEnable) {
                        InsetDivider()
                        TimeOfDayPickerRow("开始时间", muteStart) { muteStart = it }
                        InsetDivider()
                        TimeOfDayPickerRow("结束时间", muteEnd) { muteEnd = it }
                    }
                }
            }
            if (initialRule.group) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "群聊过滤") }
                item {
                    SettingsCard {
                        if (!isDefault) {
                            ActionRow("仅显示成员通知", memberRulesSummary(onlyMembers)) {
                                onPickOnlyMembers(onlyMembers)
                            }
                            InsetDivider()
                            ActionRow("屏蔽成员通知", memberRulesSummary(blockMembers)) {
                                onPickBlockMembers(blockMembers)
                            }
                            InsetDivider()
                        }
                        SwitchRow(blockAtAll, "屏蔽@所有人", "命中 @所有人 时不弹通知") { blockAtAll = it }
                        InsetDivider()
                        SwitchRow(blockAtMe, "屏蔽@我", "命中 @我 时不弹通知") { blockAtMe = it }
                    }
                }
            }
            if (!isDefault) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除规则", "移除该会话自定义通知配置") {
                            onDelete()
                        }
                    }
                }
            }
        }
    }
}

internal fun customNotificationRuleFromContact(option: ContactOption): CustomNotificationRule {
    return CustomNotificationRule(
        id = option.id,
        talker = option.id,
        label = option.label.ifBlank { option.id },
        group = option.group,
        official = option.official
    )
}

internal fun customNotificationRuleTitle(rule: CustomNotificationRule): String {
    val prefix = when {
        rule.group -> "群聊"
        rule.official -> "公众号"
        else -> "私聊"
    }
    val state = if (rule.enabled) "" else "（关闭）"
    return "$prefix · ${rule.label.ifBlank { rule.talker }}$state"
}

internal fun customNotificationRuleSummary(context: Context, rule: CustomNotificationRule): String {
    val items = ArrayList<String>()
    if (!rule.enabled) items += "未启用"
    if (rule.mode == CustomNotificationSettings.MODE_DND) items += "免打扰"
    if (rule.sound) items += if (rule.ringtone.isBlank()) "铃声" else ringtoneDisplayName(context, rule.ringtone, RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM)
    if (rule.vibrate) items += "震动"
    if (rule.markRead) items += "已读按钮"
    if (rule.quickReply) items += if (rule.quoteQuickReply) "引用快捷回复" else "快捷回复"
    if (rule.mergeByTalker) items += "合并通知"
    if (rule.ignoreWechatDnd) items += "忽略微信免打扰"
    if (rule.muteEnable) items += "${rule.muteStart}-${rule.muteEnd}静默"
    if (rule.group) {
        if (rule.onlyMembers.isNotBlank()) items += "仅${CustomNotificationSettings.splitMemberRules(rule.onlyMembers).size}成员"
        if (rule.blockMembers.isNotBlank()) items += "屏蔽${CustomNotificationSettings.splitMemberRules(rule.blockMembers).size}成员"
        if (rule.blockAtAll) items += "屏蔽@所有人"
        if (rule.blockAtMe) items += "屏蔽@我"
    }
    return items.ifEmpty { listOf("点击配置通知规则") }.joinToString(" / ")
}

internal fun memberRulesSummary(value: String): String {
    val count = CustomNotificationSettings.splitMemberRules(value).size
    return if (count == 0) "未设置" else "已设置 $count 项"
}

internal fun memberRulesToGroupEntries(groupId: String, value: String): String {
    return CustomNotificationSettings.splitMemberRules(value)
        .map { member -> groupMemberEntry(groupId, member) }
        .joinToString(",")
}

internal fun groupEntriesToMemberRules(groupId: String, entries: List<String>): String {
    val members = entries.mapNotNull { entry ->
        val normalized = entry.trim()
        val separators = charArrayOf('/', '#', ':', '：')
        val index = separators.map { normalized.indexOf(it) }
            .filter { it > 0 }
            .minOrNull()
            ?: return@mapNotNull normalized.takeIf { it.isNotBlank() }
        val group = normalized.substring(0, index).trim()
        val member = normalized.substring(index + 1).trim()
        if (group == groupId && member.isNotBlank()) member else null
    }
    return members.distinct().joinToString(",")
}

internal data class CustomNotificationBatchUpdate(
    val applyEnabled: Boolean,
    val enabled: Boolean,
    val applyMode: Boolean,
    val dnd: Boolean,
    val applyVibrate: Boolean,
    val vibrate: Boolean,
    val applySound: Boolean,
    val sound: Boolean,
    val applyMarkRead: Boolean,
    val markRead: Boolean,
    val applyQuickReply: Boolean,
    val quickReply: Boolean,
    val applyQuoteQuickReply: Boolean,
    val quoteQuickReply: Boolean,
    val applyMergeByTalker: Boolean,
    val mergeByTalker: Boolean,
    val applyShowDetail: Boolean,
    val showDetail: Boolean,
    val applyIgnoreWechatDnd: Boolean,
    val ignoreWechatDnd: Boolean,
    val applyMute: Boolean,
    val muteEnable: Boolean,
    val muteStart: String,
    val muteEnd: String,
    val applyAtRules: Boolean,
    val blockAtAll: Boolean,
    val blockAtMe: Boolean
) {
    fun hasChanges(): Boolean {
        return applyEnabled || applyMode || applyVibrate || applySound || applyMarkRead || applyQuickReply || applyQuoteQuickReply || applyMergeByTalker ||
            applyShowDetail || applyIgnoreWechatDnd || applyMute || applyAtRules
    }

    fun apply(rule: CustomNotificationRule): CustomNotificationRule {
        var next = rule
        if (applyEnabled) next = next.copy(enabled = enabled)
        if (applyMode) next = next.copy(mode = if (dnd) CustomNotificationSettings.MODE_DND else CustomNotificationSettings.MODE_NOTIFY)
        if (applyVibrate) next = next.copy(vibrate = vibrate)
        if (applySound) next = next.copy(sound = sound)
        if (applyMarkRead) next = next.copy(markRead = markRead)
        if (applyQuickReply) next = next.copy(quickReply = quickReply)
        if (applyQuoteQuickReply) next = next.copy(quoteQuickReply = quoteQuickReply)
        if (applyMergeByTalker) next = next.copy(mergeByTalker = mergeByTalker)
        if (applyShowDetail) next = next.copy(showDetail = showDetail)
        if (applyIgnoreWechatDnd) next = next.copy(ignoreWechatDnd = ignoreWechatDnd)
        if (applyMute) {
            next = next.copy(
                muteEnable = muteEnable,
                muteStart = CustomNotificationSettings.normalizeTime(muteStart, CustomNotificationSettings.DEFAULT_MUTE_START),
                muteEnd = CustomNotificationSettings.normalizeTime(muteEnd, CustomNotificationSettings.DEFAULT_MUTE_END)
            )
        }
        if (applyAtRules && next.group) {
            next = next.copy(blockAtAll = blockAtAll, blockAtMe = blockAtMe)
        }
        return next
    }
}

@Composable
internal fun KeywordNotificationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { KeywordNotificationSettings(context) }
    val sp = remember { HchatStorage.preferences(context, KeywordNotificationSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<KeywordNotificationRoute>(KeywordNotificationRoute.Main) }
    var keywords by remember { mutableStateOf(settings.keywords()) }
    var excludeContacts by remember { mutableStateOf(settings.excludeContacts()) }
    var includeContacts by remember { mutableStateOf(settings.includeContacts()) }
    val keywordMainListState = rememberLazyListState()
    val keywordListState = rememberLazyListState()
    var keywordQuery by remember { mutableStateOf("") }

    fun saveKeywords(next: List<KeywordRule>) {
        val clean = next.map { it.copy(keyword = it.keyword.trim()) }
            .filter { it.keyword.isNotBlank() }
            .distinctBy { it.keyword }
        keywords = clean
        settings.saveKeywords(clean)
    }

    fun saveContacts(includeMode: Boolean, ids: Set<String>) {
        if (includeMode) {
            includeContacts = ids
            settings.saveIncludeContacts(ids)
        } else {
            excludeContacts = ids
            settings.saveExcludeContacts(ids)
        }
    }

    SettingsRouteTransition(
        targetState = route,
        label = "KeywordNotificationRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            KeywordNotificationRoute.Main -> KeywordNotificationMainPage(
                context = context,
                provider = provider,
                sp = sp,
                keywords = keywords,
                excludeContacts = excludeContacts,
                includeContacts = includeContacts,
                listState = keywordMainListState,
                onBack = onBack,
                onOpenKeywords = { route = KeywordNotificationRoute.KeywordList },
                onOpenTemplates = { route = KeywordNotificationRoute.TemplateEditor },
                onPickContacts = { includeMode ->
                    val current = if (includeMode) includeContacts else excludeContacts
                    route = KeywordNotificationRoute.ContactPicker(
                        request = ContactPickerRequest(
                            title = if (includeMode) "选择仅生效聊天" else "选择排除聊天",
                            mode = ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = current.joinToString(","),
                            onValue = {},
                            enableLabels = true
                        ),
                        includeMode = includeMode
                    )
                },
                onClearContacts = { includeMode ->
                    saveContacts(includeMode, emptySet())
                    Toast.makeText(context, if (includeMode) "已清空仅生效名单" else "已清空排除名单", Toast.LENGTH_SHORT).show()
                }
            )
            KeywordNotificationRoute.KeywordList -> KeywordNotificationKeywordListPage(
                keywords = keywords,
                query = keywordQuery,
                onQueryChange = { keywordQuery = it },
                listState = keywordListState,
                onBack = { route = KeywordNotificationRoute.Main },
                onAdd = { route = KeywordNotificationRoute.KeywordEditor(null) },
                onEdit = { route = KeywordNotificationRoute.KeywordEditor(it.keyword) },
                onClear = {
                    saveKeywords(emptyList())
                    Toast.makeText(context, "已清空关键词", Toast.LENGTH_SHORT).show()
                }
            )
            is KeywordNotificationRoute.KeywordEditor -> {
                val old = currentRoute.oldKeyword?.let { oldKeyword -> keywords.firstOrNull { it.keyword == oldKeyword } }
                KeywordNotificationKeywordEditorPage(
                    initial = old,
                    onBack = { route = KeywordNotificationRoute.KeywordList },
                    onSave = { saved ->
                        if (saved.keyword.isBlank()) {
                            Toast.makeText(context, "关键词不能为空", Toast.LENGTH_SHORT).show()
                        } else if (currentRoute.oldKeyword != saved.keyword && keywords.any { it.keyword == saved.keyword }) {
                            Toast.makeText(context, "该关键词已存在", Toast.LENGTH_SHORT).show()
                        } else {
                            val next = if (currentRoute.oldKeyword == null) {
                                keywords + saved
                            } else {
                                keywords.map { if (it.keyword == currentRoute.oldKeyword) saved else it }
                            }
                            saveKeywords(next)
                            Toast.makeText(context, "关键词已保存", Toast.LENGTH_SHORT).show()
                            route = KeywordNotificationRoute.KeywordList
                        }
                    },
                    onDelete = if (old == null) {
                        null
                    } else {
                        {
                            saveKeywords(keywords.filterNot { it.keyword == old.keyword })
                            Toast.makeText(context, "关键词已删除", Toast.LENGTH_SHORT).show()
                            route = KeywordNotificationRoute.KeywordList
                        }
                    }
                )
            }
            KeywordNotificationRoute.TemplateEditor -> KeywordNotificationTemplatePage(
                context = context,
                sp = sp,
                onBack = { route = KeywordNotificationRoute.Main }
            )
            is KeywordNotificationRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = KeywordNotificationRoute.Main },
                onConfirm = { picked ->
                    val ids = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    saveContacts(currentRoute.includeMode, ids)
                    Toast.makeText(context, "名单已保存", Toast.LENGTH_SHORT).show()
                    route = KeywordNotificationRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun TextSpeechMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { TextSpeechSettings(context) }
    val sp = remember { HchatStorage.preferences(context, TextSpeechSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<TextSpeechRoute>(TextSpeechRoute.Main) }
    var allowedContacts by remember { mutableStateOf(settings.allowedContacts()) }
    val mainListState = rememberLazyListState()

    fun saveAllowedContacts(ids: Set<String>) {
        allowedContacts = ids
        settings.saveAllowedContacts(ids)
    }

    SettingsRouteTransition(
        targetState = route,
        label = "TextSpeechRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            TextSpeechRoute.Main -> TextSpeechMainPage(
                provider = provider,
                sp = sp,
                allowedContacts = allowedContacts,
                listState = mainListState,
                onBack = onBack,
                onPickContacts = {
                    route = TextSpeechRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "设置允许名单",
                            mode = ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = allowedContacts.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is TextSpeechRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = TextSpeechRoute.Main },
                onConfirm = { picked ->
                    saveAllowedContacts(
                        picked.map { it.id }
                            .filter { it.isNotBlank() }
                            .toSet()
                    )
                    Toast.makeText(context, "允许名单已保存", Toast.LENGTH_SHORT).show()
                    route = TextSpeechRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun TextSpeechMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    allowedContacts: Set<String>,
    listState: LazyListState,
    onBack: () -> Unit,
    onPickContacts: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(sp.getBoolean(TextSpeechSettings.KEY_ENABLE, TextSpeechSettings.DEFAULT_ENABLE))
    }
    var ttsEngine by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_TTS_ENGINE, TextSpeechSettings.DEFAULT_TTS_ENGINE)
                ?: TextSpeechSettings.DEFAULT_TTS_ENGINE
        )
    }
    var ttsVoice by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_TTS_VOICE, TextSpeechSettings.DEFAULT_TTS_VOICE)
                ?: TextSpeechSettings.DEFAULT_TTS_VOICE
        )
    }
    var ttsVoiceChoices by remember { mutableStateOf<List<PopupChoice<String>>>(emptyList()) }
    var ttsVoiceLoading by remember { mutableStateOf(true) }
    var ttsVoiceError by remember { mutableStateOf("") }
    var ttsFallbackEnginePackage by remember { mutableStateOf("") }
    val installedTtsEngines = remember(context) { TextSpeechEngineCatalog.installed(context) }
    val ttsFallbackEngineLabel = remember(installedTtsEngines, ttsFallbackEnginePackage) {
        installedTtsEngines.firstOrNull { it.packageName == ttsFallbackEnginePackage }
            ?.label
            ?: ttsFallbackEnginePackage
    }
    val ttsEngineOptions = remember(installedTtsEngines, ttsEngine) {
        buildList {
            add(PopupChoice("跟随系统默认", TextSpeechSettings.DEFAULT_TTS_ENGINE))
            installedTtsEngines.forEach { add(PopupChoice(it.label, it.packageName)) }
            if (ttsEngine.isNotBlank() && installedTtsEngines.none { it.packageName == ttsEngine }) {
                add(PopupChoice("已不可用（$ttsEngine）", ttsEngine))
            }
        }
    }
    val ttsVoiceOptions = remember(ttsVoiceChoices, ttsVoice) {
        buildList {
            add(PopupChoice("跟随引擎默认", TextSpeechSettings.DEFAULT_TTS_VOICE))
            addAll(ttsVoiceChoices)
            if (ttsVoice.isNotBlank() && ttsVoiceChoices.none { it.value == ttsVoice }) {
                add(PopupChoice("已不可用（$ttsVoice）", ttsVoice))
            }
        }
    }
    DisposableEffect(context, ttsEngine) {
        ttsVoiceLoading = true
        ttsVoiceError = ""
        ttsFallbackEnginePackage = ""
        ttsVoiceChoices = emptyList()
        val handle = TextSpeechVoiceCatalog.load(context, ttsEngine) { result ->
            ttsVoiceChoices = result.options.map { PopupChoice(it.label, it.name) }
            ttsVoiceError = result.error
            ttsFallbackEnginePackage = if (result.usedFallback) {
                result.activeEnginePackage.ifBlank { "其它可用引擎" }
            } else {
                ""
            }
            ttsVoiceLoading = false
        }
        onDispose { handle.cancel() }
    }
    var volumeControl by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_VOLUME_CONTROL,
                TextSpeechSettings.DEFAULT_VOLUME_CONTROL
            )
        )
    }
    var playVoiceMessages by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_PLAY_VOICE_MESSAGES,
                TextSpeechSettings.DEFAULT_PLAY_VOICE_MESSAGES
            )
        )
    }
    var customAnnouncement by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_ANNOUNCE_SENDER,
                TextSpeechSettings.DEFAULT_ANNOUNCE_SENDER
            )
        )
    }
    var announcementTemplate by remember {
        mutableStateOf(
            sp.getString(
                TextSpeechSettings.KEY_ANNOUNCEMENT_TEMPLATE,
                TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
            ) ?: TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_TIME_FORMAT, TextSpeechSettings.DEFAULT_TIME_FORMAT)
                ?: TextSpeechSettings.DEFAULT_TIME_FORMAT
        )
    }
    var respectWechatDnd by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_RESPECT_WECHAT_DND,
                TextSpeechSettings.DEFAULT_RESPECT_WECHAT_DND
            )
        )
    }
    var quietEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                TextSpeechSettings.KEY_QUIET_ENABLE,
                TextSpeechSettings.DEFAULT_QUIET_ENABLE
            )
        )
    }
    var quietStart by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_QUIET_START, TextSpeechSettings.DEFAULT_QUIET_START)
                ?: TextSpeechSettings.DEFAULT_QUIET_START
        )
    }
    var quietEnd by remember {
        mutableStateOf(
            sp.getString(TextSpeechSettings.KEY_QUIET_END, TextSpeechSettings.DEFAULT_QUIET_END)
                ?: TextSpeechSettings.DEFAULT_QUIET_END
        )
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
            item { SmallTitle(text = "播报") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "自动播放语音",
                        summary = "播报允许名单内收到的消息"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(TextSpeechSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "语音引擎",
                            summary = "选择 TTS 播报使用的语音引擎",
                            options = ttsEngineOptions,
                            currentValue = ttsEngine,
                            onValueChanged = {
                                ttsEngine = it
                                ttsVoice = TextSpeechSettings.DEFAULT_TTS_VOICE
                                sp.edit()
                                    .putString(TextSpeechSettings.KEY_TTS_ENGINE, it)
                                    .putString(
                                        TextSpeechSettings.KEY_TTS_VOICE,
                                        TextSpeechSettings.DEFAULT_TTS_VOICE
                                    )
                                    .apply()
                            }
                        )
                        InsetDivider()
                        PopupChoiceRow(
                            title = "播报角色",
                            summary = when {
                                ttsVoiceLoading -> "正在读取所选引擎的播报角色"
                                ttsVoiceError.isNotBlank() -> "读取失败：$ttsVoiceError"
                                ttsFallbackEnginePackage.isNotBlank() ->
                                    "系统默认不可用，已临时使用 $ttsFallbackEngineLabel"
                                ttsVoiceChoices.isEmpty() -> "所选引擎未提供可选角色，将跟随引擎默认"
                                else -> "选择所选引擎提供的播报角色"
                            },
                            options = ttsVoiceOptions,
                            currentValue = ttsVoice,
                            enabled = !ttsVoiceLoading,
                            onValueChanged = {
                                ttsVoice = it
                                sp.edit().putString(TextSpeechSettings.KEY_TTS_VOICE, it).apply()
                            }
                        )
                        InsetDivider()
                        SwitchRow(
                            checked = playVoiceMessages,
                            title = "播放语音消息",
                            summary = "收到语音时直接播放原语音，不使用 TTS"
                        ) {
                            playVoiceMessages = it
                            sp.edit()
                                .putBoolean(TextSpeechSettings.KEY_PLAY_VOICE_MESSAGES, it)
                                .apply()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = volumeControl,
                            title = "音键控制",
                            summary = "音量减：暂停\n音量加：继续\n暂停时再按音量减：跳过"
                        ) {
                            volumeControl = it
                            sp.edit().putBoolean(TextSpeechSettings.KEY_VOLUME_CONTROL, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = customAnnouncement,
                            title = "自定义播报内容",
                            summary = "分别使用消息对应的昵称、群聊和正文变量"
                        ) {
                            customAnnouncement = it
                            sp.edit().putBoolean(TextSpeechSettings.KEY_ANNOUNCE_SENDER, it).apply()
                        }
                        if (customAnnouncement) {
                            InsetDivider()
                            VariableInputRow(
                                title = "播报内容格式",
                                summary = "点击下方中文变量插入到光标位置",
                                value = announcementTemplate,
                                variables = textSpeechTemplateVariables,
                                minLines = 3
                            ) {
                                announcementTemplate = it
                                sp.edit()
                                    .putString(TextSpeechSettings.KEY_ANNOUNCEMENT_TEMPLATE, it)
                                    .apply()
                            }
                            if (announcementTemplate.contains(TextSpeechSettings.VAR_MESSAGE_TIME)) {
                                InsetDivider()
                                InputRow(
                                    title = "消息时间格式",
                                    summary = if (TextSpeechSettings.isValidTimeFormat(timeFormat)) {
                                        "例如 HH:mm:ss 或 yyyy-MM-dd HH:mm:ss"
                                    } else {
                                        "格式无效，当前输入不会保存"
                                    },
                                    value = timeFormat
                                ) {
                                    timeFormat = it
                                    if (TextSpeechSettings.isValidTimeFormat(it)) {
                                        sp.edit().putString(
                                            TextSpeechSettings.KEY_TIME_FORMAT,
                                            TextSpeechSettings.normalizeTimeFormat(it)
                                        ).apply()
                                    }
                                }
                            }
                            InsetDivider()
                            ActionRow(
                                title = "恢复默认格式",
                                summary = TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
                            ) {
                                announcementTemplate = TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
                                timeFormat = TextSpeechSettings.DEFAULT_TIME_FORMAT
                                sp.edit()
                                    .putString(
                                        TextSpeechSettings.KEY_ANNOUNCEMENT_TEMPLATE,
                                        TextSpeechSettings.DEFAULT_ANNOUNCEMENT_TEMPLATE
                                    )
                                    .putString(
                                        TextSpeechSettings.KEY_TIME_FORMAT,
                                        TextSpeechSettings.DEFAULT_TIME_FORMAT
                                    )
                                    .apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = respectWechatDnd,
                            title = "免打扰不播报",
                            summary = "私聊或群聊开启微信消息免打扰后停止播报"
                        ) {
                            respectWechatDnd = it
                            sp.edit()
                                .putBoolean(TextSpeechSettings.KEY_RESPECT_WECHAT_DND, it)
                                .apply()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = quietEnabled,
                            title = "定时免打扰",
                            summary = "指定时段内暂停自动播报"
                        ) {
                            quietEnabled = it
                            sp.edit().putBoolean(TextSpeechSettings.KEY_QUIET_ENABLE, it).apply()
                        }
                        if (quietEnabled) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", quietStart) {
                                quietStart = it
                                sp.edit().putString(TextSpeechSettings.KEY_QUIET_START, it).apply()
                            }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", quietEnd) {
                                quietEnd = it
                                sp.edit().putString(TextSpeechSettings.KEY_QUIET_END, it).apply()
                            }
                        }
                        InsetDivider()
                        ActionRow(
                            title = "设置允许名单",
                            summary = if (allowedContacts.isEmpty()) {
                                "未设置"
                            } else {
                                "已选择 ${allowedContacts.size} 个聊天"
                            },
                            onClick = onPickContacts
                        )
                    }
                }
            }
        }
    }
}
