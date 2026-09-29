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
internal fun FakeLocationManualInputDialog(
    initialLatitude: String,
    initialLongitude: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var latitude by remember(initialLatitude) { mutableStateOf(initialLatitude) }
    var longitude by remember(initialLongitude) { mutableStateOf(initialLongitude) }
    val parsed = validFakeLocation(latitude.toDoubleOrNull(), longitude.toDoubleOrNull())
    WindowDialog(
        show = true,
        title = "手动输入经纬度",
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                InputRow("纬度", "范围 -90 到 90", latitude) { latitude = cleanCoordinateInput(it) }
                InputRow("经度", "范围 -180 到 180", longitude) { longitude = cleanCoordinateInput(it) }
                if (parsed == null) {
                    Text(
                        text = "请输入有效的纬度和经度",
                        color = Color(0xFFD93025),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "保存",
                        onClick = { parsed?.let { onConfirm(it.first, it.second) } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

internal fun cleanCoordinateInput(value: String): String {
    return value.filterIndexed { index, char ->
        char.isDigit() || char == '.' || (char == '-' && index == 0)
    }
}

internal fun validFakeLocation(latitude: Double?, longitude: Double?): Pair<Double, Double>? {
    if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return latitude to longitude
}

internal fun parseNativeFakeLocation(text: String): Pair<Double, Double>? {
    val latitude = Regex("""(?:lat|latitude)\s*[:= ]\s*([-+]?[0-9]*\.?[0-9]+)""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    val longitude = Regex("""(?:lng|lon|longitude)\s*[:= ]\s*([-+]?[0-9]*\.?[0-9]+)""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    return validFakeLocation(latitude, longitude)
}

internal fun formatFakeLocationCoordinate(value: Double): String {
    return String.format(Locale.US, "%.6f", value)
}

internal fun copyFakeLocationCoordinate(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun MessageBubbleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MessageBubbleSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(MessageBubbleSettings.KEY_ENABLE, MessageBubbleSettings.DEFAULT_ENABLE))
    }
    var separateDarkMode by remember {
        mutableStateOf(
            sp.getBoolean(
                MessageBubbleSettings.KEY_SEPARATE_DARK_MODE,
                MessageBubbleSettings.DEFAULT_SEPARATE_DARK_MODE
            )
        )
    }
    var revision by remember { mutableStateOf(0) }
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用消息气泡",
                        summary = "使用本地图片替换微信原生消息气泡",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(MessageBubbleSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    if (enabled) {
                        InsetDivider()
                        SwitchRow(
                            checked = separateDarkMode,
                            title = "深色模式单独设置",
                            summary = "未设置深色气泡时自动沿用对应浅色气泡",
                            onCheckedChange = {
                                separateDarkMode = it
                                sp.edit()
                                    .putBoolean(MessageBubbleSettings.KEY_SEPARATE_DARK_MODE, it)
                                    .apply()
                            }
                        )
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "浅色模式") }
                item {
                    SettingsCard {
                        MessageBubbleAssetRow(context, MessageBubbleSlot.LEFT_LIGHT, revision) { revision++ }
                        InsetDivider()
                        MessageBubbleAssetRow(context, MessageBubbleSlot.RIGHT_LIGHT, revision) { revision++ }
                    }
                }
                if (separateDarkMode) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "深色模式") }
                    item {
                        SettingsCard {
                            MessageBubbleAssetRow(context, MessageBubbleSlot.LEFT_DARK, revision) { revision++ }
                            InsetDivider()
                            MessageBubbleAssetRow(context, MessageBubbleSlot.RIGHT_DARK, revision) { revision++ }
                        }
                    }
                }
                MessageBubbleKind.values()
                    .filter { it != MessageBubbleKind.GENERAL }
                    .forEach { kind ->
                        item {
                            SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "${kind.displayName}气泡")
                        }
                        item {
                            SettingsCard {
                                if (kind == MessageBubbleKind.SYSTEM) {
                                    MessageBubbleAssetRow(
                                        context,
                                        MessageBubbleSlot.resolve(kind, false, false),
                                        revision
                                    ) { revision++ }
                                } else {
                                    MessageBubbleAssetRow(
                                        context,
                                        MessageBubbleSlot.resolve(kind, false, false),
                                        revision
                                    ) { revision++ }
                                    InsetDivider()
                                    MessageBubbleAssetRow(
                                        context,
                                        MessageBubbleSlot.resolve(kind, true, false),
                                        revision
                                    ) { revision++ }
                                }
                            }
                        }
                        if (separateDarkMode) {
                            item {
                                SettingsCard {
                                    if (kind == MessageBubbleKind.SYSTEM) {
                                        MessageBubbleAssetRow(
                                            context,
                                            MessageBubbleSlot.resolve(kind, false, true),
                                            revision
                                        ) { revision++ }
                                    } else {
                                        MessageBubbleAssetRow(
                                            context,
                                            MessageBubbleSlot.resolve(kind, false, true),
                                            revision
                                        ) { revision++ }
                                        InsetDivider()
                                        MessageBubbleAssetRow(
                                            context,
                                            MessageBubbleSlot.resolve(kind, true, true),
                                            revision
                                        ) { revision++ }
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
internal fun MessageBubbleAssetRow(
    context: Context,
    slot: MessageBubbleSlot,
    revision: Int,
    onChanged: () -> Unit
) {
    val selected = remember(slot, revision) { MessageBubbleStore.hasAsset(context, slot) }
    val emptySummary = when {
        slot.kind == MessageBubbleKind.GENERAL && slot.darkMode -> "未选择，沿用对应浅色气泡"
        slot.kind == MessageBubbleKind.GENERAL -> "未选择，保持微信原生气泡"
        slot.darkMode -> "未选择，沿用同类浅色气泡"
        else -> "未选择，保持微信原生气泡"
    }
    ActionRow(
        title = slot.displayName,
        summary = if (selected) "已选择，点击更换或恢复" else emptySummary
    ) {
        val activity = context as? Activity ?: return@ActionRow
        val pickAsset = {
            MessageBubblePicker.launch(activity, slot) { result ->
                when (result) {
                    MessageBubblePickResult.SAVED -> {
                        onChanged()
                        Toast.makeText(context, "${slot.displayName}已更新", Toast.LENGTH_SHORT).show()
                    }
                    MessageBubblePickResult.FAILED -> {
                        Toast.makeText(context, "气泡图片无效或读取失败", Toast.LENGTH_SHORT).show()
                    }
                    MessageBubblePickResult.CANCELLED -> Unit
                }
            }
        }
        if (!selected) {
            pickAsset()
        } else {
            VoiceForwardMiuixDialog.showChoices(
                activity = activity,
                title = slot.displayName,
                summary = "管理当前气泡图片",
                choices = listOf(
                    "更换气泡" to "重新选择一张本地图片",
                    "恢复微信气泡" to "删除当前自定义气泡"
                ),
                onSelected = { choice ->
                    if (choice == 0) {
                        pickAsset()
                    } else if (MessageBubbleStore.remove(context, slot)) {
                        onChanged()
                        Toast.makeText(context, "已恢复微信气泡", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {}
            )
        }
    }
}

@Composable
internal fun BackgroundBeautyMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, BackgroundBeautySettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                BackgroundBeautySettings.KEY_ENABLE,
                BackgroundBeautySettings.DEFAULT_ENABLE
            )
        )
    }
    var revision by remember { mutableStateOf(sp.getInt(BackgroundBeautySettings.KEY_REVISION, 0)) }
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用沉浸式背景",
                        summary = "分别设置聊天和微信四个主页面背景",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(BackgroundBeautySettings.KEY_ENABLE, it).apply()
                        }
                    )
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "聊天") }
                item {
                    SettingsCard {
                        BackgroundBeautyAssetBlock(
                            context = context,
                            sp = sp,
                            slot = BackgroundBeautySettings.Slot.CHAT,
                            revision = revision,
                            onChanged = {
                                revision = bumpBackgroundBeautyRevision(sp)
                            }
                        )
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "微信首页") }
                item {
                    SettingsCard {
                        val slots = listOf(
                            BackgroundBeautySettings.Slot.WECHAT,
                            BackgroundBeautySettings.Slot.CONTACTS,
                            BackgroundBeautySettings.Slot.DISCOVER,
                            BackgroundBeautySettings.Slot.ME
                        )
                        slots.forEachIndexed { index, slot ->
                            BackgroundBeautyAssetBlock(
                                context = context,
                                sp = sp,
                                slot = slot,
                                revision = revision,
                                onChanged = {
                                    revision = bumpBackgroundBeautyRevision(sp)
                                }
                            )
                            if (index != slots.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackgroundBeautyAssetBlock(
    context: Context,
    sp: SharedPreferences,
    slot: BackgroundBeautySettings.Slot,
    revision: Int,
    onChanged: () -> Unit
) {
    val selected = remember(slot, revision) { BackgroundBeautyStore.hasImage(context, slot) }
    var opacity by remember(slot) {
        mutableStateOf(
            BackgroundBeautySettings.opacity(
                sp.getFloat(
                    BackgroundBeautySettings.opacityKey(slot),
                    BackgroundBeautySettings.DEFAULT_OPACITY
                )
            )
        )
    }
    ActionRow(
        title = slot.title,
        summary = if (selected) "已选择，点击更换或恢复" else slot.summary
    ) {
        val activity = context as? Activity ?: return@ActionRow
        val pickImage = {
            BackgroundBeautyPicker.launch(activity, slot) { result ->
                when (result) {
                    BackgroundBeautyPickResult.SAVED -> {
                        onChanged()
                        Toast.makeText(context, "${slot.title}已更新", Toast.LENGTH_SHORT).show()
                    }
                    BackgroundBeautyPickResult.FAILED -> {
                        Toast.makeText(context, "背景图片无效或读取失败", Toast.LENGTH_SHORT).show()
                    }
                    BackgroundBeautyPickResult.CANCELLED -> Unit
                }
            }
        }
        if (!selected) {
            pickImage()
        } else {
            VoiceForwardMiuixDialog.showChoices(
                activity = activity,
                title = slot.title,
                summary = "管理当前背景图片",
                choices = listOf(
                    "更换背景" to "重新选择一张本地图片",
                    "恢复微信背景" to "删除当前自定义背景"
                ),
                onSelected = { choice ->
                    if (choice == 0) {
                        pickImage()
                    } else if (BackgroundBeautyStore.remove(context, slot)) {
                        onChanged()
                        Toast.makeText(context, "已恢复微信背景", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = {}
            )
        }
    }
    if (selected) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "背景不透明度 ${(opacity * 100f).toInt()}%",
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = opacity,
                onValueChange = { opacity = BackgroundBeautySettings.opacity(it) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                valueRange = 0.10f..1.0f,
                steps = 17,
                onValueChangeFinished = {
                    sp.edit()
                        .putFloat(
                            BackgroundBeautySettings.opacityKey(slot),
                            BackgroundBeautySettings.opacity(opacity)
                        )
                        .apply()
                },
                showKeyPoints = true,
                keyPoints = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f)
            )
        }
    }
}

internal fun bumpBackgroundBeautyRevision(sp: SharedPreferences): Int {
    val current = sp.getInt(BackgroundBeautySettings.KEY_REVISION, 0)
    val next = if (current == Int.MAX_VALUE) 1 else current + 1
    sp.edit().putInt(BackgroundBeautySettings.KEY_REVISION, next).apply()
    return next
}

@Composable
internal fun MessageTextColorMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MessageTextColorSettings.PREFS_NAME) }
    var leftLightColor by remember {
        mutableStateOf(
            sp.getString(
                MessageTextColorSettings.KEY_LEFT_LIGHT_COLOR,
                MessageTextColorSettings.DEFAULT_LEFT_LIGHT_COLOR
            ) ?: MessageTextColorSettings.DEFAULT_LEFT_LIGHT_COLOR
        )
    }
    var rightLightColor by remember {
        mutableStateOf(
            sp.getString(
                MessageTextColorSettings.KEY_RIGHT_LIGHT_COLOR,
                MessageTextColorSettings.DEFAULT_RIGHT_LIGHT_COLOR
            ) ?: MessageTextColorSettings.DEFAULT_RIGHT_LIGHT_COLOR
        )
    }
    var leftDarkColor by remember {
        mutableStateOf(
            sp.getString(
                MessageTextColorSettings.KEY_LEFT_DARK_COLOR,
                MessageTextColorSettings.DEFAULT_LEFT_DARK_COLOR
            ) ?: MessageTextColorSettings.DEFAULT_LEFT_DARK_COLOR
        )
    }
    var rightDarkColor by remember {
        mutableStateOf(
            sp.getString(
                MessageTextColorSettings.KEY_RIGHT_DARK_COLOR,
                MessageTextColorSettings.DEFAULT_RIGHT_DARK_COLOR
            ) ?: MessageTextColorSettings.DEFAULT_RIGHT_DARK_COLOR
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = {
                    val leftLight = MessageTextColorSettings.cleanColorSpec(leftLightColor)
                        .ifEmpty { MessageTextColorSettings.DEFAULT_LEFT_LIGHT_COLOR }
                    val rightLight = MessageTextColorSettings.cleanColorSpec(rightLightColor)
                        .ifEmpty { MessageTextColorSettings.DEFAULT_RIGHT_LIGHT_COLOR }
                    val leftDark = MessageTextColorSettings.cleanColorSpec(leftDarkColor)
                        .ifEmpty { MessageTextColorSettings.DEFAULT_LEFT_DARK_COLOR }
                    val rightDark = MessageTextColorSettings.cleanColorSpec(rightDarkColor)
                        .ifEmpty { MessageTextColorSettings.DEFAULT_RIGHT_DARK_COLOR }
                    sp.edit()
                        .putString(MessageTextColorSettings.KEY_LEFT_LIGHT_COLOR, leftLight)
                        .putString(MessageTextColorSettings.KEY_RIGHT_LIGHT_COLOR, rightLight)
                        .putString(MessageTextColorSettings.KEY_LEFT_DARK_COLOR, leftDark)
                        .putString(MessageTextColorSettings.KEY_RIGHT_DARK_COLOR, rightDark)
                        .apply()
                    leftLightColor = leftLight
                    rightLightColor = rightLight
                    leftDarkColor = leftDark
                    rightDarkColor = rightDark
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
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageTextColorSettings.KEY_ENABLE,
                        "启用消息文本颜色",
                        "处理聊天里的文本消息和引用消息正文",
                        MessageTextColorSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "颜色") }
            item {
                SettingsCard {
                    ColorPickerRow(
                        "左侧浅色",
                        "对方文本消息，浅色模式",
                        leftLightColor,
                        onReset = { leftLightColor = MessageTextColorSettings.DEFAULT_LEFT_LIGHT_COLOR }
                    ) { leftLightColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow(
                        "右侧浅色",
                        "自己发送文本消息，浅色模式",
                        rightLightColor,
                        onReset = { rightLightColor = MessageTextColorSettings.DEFAULT_RIGHT_LIGHT_COLOR }
                    ) { rightLightColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow(
                        "左侧深色",
                        "对方文本消息，深色模式",
                        leftDarkColor,
                        onReset = { leftDarkColor = MessageTextColorSettings.DEFAULT_LEFT_DARK_COLOR }
                    ) { leftDarkColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow(
                        "右侧深色",
                        "自己发送文本消息，深色模式",
                        rightDarkColor,
                        onReset = { rightDarkColor = MessageTextColorSettings.DEFAULT_RIGHT_DARK_COLOR }
                    ) { rightDarkColor = it.take(19) }
                }
            }
        }
    }
}

@Composable
internal fun ChatTimeStyleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, ChatTimeStyleSettings.PREFS_NAME) }
    var mode by remember {
        mutableStateOf(
            ChatTimeStyleSettings.normalizeMode(
                sp.getString(ChatTimeStyleSettings.KEY_MODE, ChatTimeStyleSettings.DEFAULT_MODE)
            )
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                ChatTimeStyleSettings.KEY_TIME_FORMAT,
                ChatTimeStyleSettings.DEFAULT_TIME_FORMAT
            ).orEmpty().ifBlank { ChatTimeStyleSettings.DEFAULT_TIME_FORMAT }
        )
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天时间") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "显示方式",
                        summary = chatTimeModeLabel(mode),
                        options = chatTimeModeChoices(),
                        currentValue = mode,
                        onValueChanged = {
                            mode = ChatTimeStyleSettings.normalizeMode(it)
                            sp.edit().putString(ChatTimeStyleSettings.KEY_MODE, mode).apply()
                        }
                    )
                    if (mode == ChatTimeStyleSettings.MODE_CUSTOM) {
                        InsetDivider()
                        InputRow(
                            title = "时间格式",
                            summary = "例如 yyyy-MM-dd HH:mm:ss",
                            value = timeFormat,
                            onValueChange = {
                                timeFormat = it
                                sp.edit()
                                    .putString(ChatTimeStyleSettings.KEY_TIME_FORMAT, it)
                                    .apply()
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun chatTimeModeChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("微信原样", ChatTimeStyleSettings.MODE_ORIGINAL),
    PopupChoice("自定义", ChatTimeStyleSettings.MODE_CUSTOM),
    PopupChoice("隐藏", ChatTimeStyleSettings.MODE_HIDDEN)
)

internal fun chatTimeModeLabel(mode: String): String = when (mode) {
    ChatTimeStyleSettings.MODE_CUSTOM -> "使用自定义时间格式"
    ChatTimeStyleSettings.MODE_HIDDEN -> "隐藏微信原生聊天时间"
    else -> "保持微信原样"
}

@Composable
internal fun HideChatAvatarMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, HideChatAvatarSettings.PREFS_NAME) }
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天头像") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        HideChatAvatarSettings.KEY_HIDE_SELF,
                        "隐藏自己的头像",
                        "在群聊和私聊中隐藏自己发送消息的头像",
                        HideChatAvatarSettings.DEFAULT_HIDE_SELF
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        HideChatAvatarSettings.KEY_HIDE_OTHER,
                        "隐藏对方的头像",
                        "在群聊和私聊中隐藏对方发送消息的头像",
                        HideChatAvatarSettings.DEFAULT_HIDE_OTHER
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoundAvatarMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RoundAvatarSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(RoundAvatarSettings.KEY_ENABLE, RoundAvatarSettings.DEFAULT_ENABLE))
    }
    var radiusFactor by remember {
        mutableStateOf(
            RoundAvatarSettings.normalizeRadiusFactor(
                sp.getFloat(RoundAvatarSettings.KEY_RADIUS_FACTOR, RoundAvatarSettings.DEFAULT_RADIUS_FACTOR)
            )
        )
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "全局头像") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用圆角头像",
                        summary = "统一应用到微信界面和通知头像",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(RoundAvatarSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    if (enabled) {
                        InsetDivider()
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                text = "圆角弧度 ${RoundAvatarSettings.displayPercent(radiusFactor)}%",
                                color = MiuixTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = radiusFactor,
                                onValueChange = {
                                    radiusFactor = RoundAvatarSettings.normalizeRadiusFactor(it)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                valueRange = RoundAvatarSettings.MIN_RADIUS_FACTOR..RoundAvatarSettings.MAX_RADIUS_FACTOR,
                                steps = 39,
                                onValueChangeFinished = {
                                    sp.edit()
                                        .putFloat(
                                            RoundAvatarSettings.KEY_RADIUS_FACTOR,
                                            RoundAvatarSettings.normalizeRadiusFactor(radiusFactor)
                                        )
                                        .apply()
                                },
                                showKeyPoints = true,
                                keyPoints = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal enum class CustomFriendAvatarRoute {
    MAIN,
    CONTACT_PICKER
}

@Composable
internal fun CustomFriendAvatarMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, CustomFriendAvatarSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(CustomFriendAvatarSettings.KEY_ENABLE, CustomFriendAvatarSettings.DEFAULT_ENABLE))
    }
    var route by remember { mutableStateOf(CustomFriendAvatarRoute.MAIN) }
    var selectedWxid by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(emptyList<ContactOption>()) }
    var revision by remember { mutableStateOf(0) }

    LaunchedEffect(revision) {
        loadContacts(context, ContactPickerMode.BOTH, false) { result, _ -> contacts = result }
    }

    SettingsRouteTransition(
        targetState = route,
        label = "custom_friend_avatar_route",
        depthOf = { if (it == CustomFriendAvatarRoute.MAIN) 0 else 1 }
    ) { current ->
        when (current) {
            CustomFriendAvatarRoute.MAIN -> {
                val selected = contacts.firstOrNull { it.id == selectedWxid }
                val configuredIds = remember(revision) { CustomFriendAvatarStore.configuredFriends(context) }
                val configured = contacts.filter { it.id in configuredIds }
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
                            bottom = padding.calculateBottomPadding() + 84.dp
                        )
                    ) {
                        item { SmallTitle(text = "自定义头像") }
                        item {
                            SettingsCard {
                                SwitchRow(
                                    checked = enabled,
                                    title = "启用自定义头像",
                                    summary = "仅修改本机显示，不会更改微信中的原头像",
                                    onCheckedChange = {
                                        enabled = it
                                        sp.edit().putBoolean(CustomFriendAvatarSettings.KEY_ENABLE, it).apply()
                                    }
                                )
                                if (enabled) {
                                    InsetDivider()
                                    ActionRow(
                                        title = "选择好友或群聊",
                                        summary = selected?.label ?: "选择要设置头像的好友或群聊",
                                        onClick = { route = CustomFriendAvatarRoute.CONTACT_PICKER }
                                    )
                                    if (selected != null) {
                                        InsetDivider()
                                        ActionRow(
                                            title = if (CustomFriendAvatarStore.hasAvatar(context, selected.id)) "更换头像" else "设置头像",
                                            summary = "从系统相册或文件中选择图片",
                                            onClick = {
                                                val activity = context as? Activity
                                                if (activity == null) {
                                                    Toast.makeText(context, "当前页面无法打开图片选择器", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    CustomFriendAvatarPicker.launch(activity, selected.id) { success ->
                                                        Toast.makeText(
                                                            context,
                                                            if (success) "自定义头像已保存" else "头像设置失败",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        if (success) revision++
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (enabled) {
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "应用范围") }
                            item {
                                SettingsCard {
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_CHAT, "聊天消息", "替换群聊和私聊消息头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_CONVERSATION, "会话列表", "替换微信首页的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_CONTACTS, "通讯录", "替换通讯录中的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_PROFILE, "资料页", "替换好友和群聊资料页中的头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_MOMENTS, "朋友圈", "替换朋友圈中的好友头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_OTHER_UI, "其他微信界面", "替换收藏、搜索等界面中的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_DESKTOP_SHORTCUT, "桌面快捷方式", "替换添加到桌面的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_NOTIFICATIONS, "消息通知", "替换微信原生通知和 Hchat 通知中的好友和群聊头像", true)
                                    InsetDivider()
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_MOMENTS_NOTIFICATIONS, "朋友圈通知", "替换朋友圈发布通知中的好友头像", true)
                                }
                            }
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "快捷入口") }
                            item {
                                SettingsCard {
                                    SwitchRow(sp, CustomFriendAvatarSettings.KEY_CONVERSATION_MENU, "会话列表长按菜单", "长按好友或群聊会话时显示设置头像入口，聊天分组内同样生效", true)
                                }
                            }
                            if (configured.isNotEmpty()) {
                                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "已设置头像") }
                                item {
                                    SettingsCard {
                                        configured.forEachIndexed { index, option ->
                                            CustomFriendAvatarRow(context, option, revision) {
                                                selectedWxid = option.id
                                                val activity = context as? Activity
                                                if (activity != null) {
                                                    VoiceForwardMiuixDialog.showChoices(
                                                        activity = activity,
                                                        title = option.label,
                                                        summary = "管理自定义头像",
                                                        choices = listOf(
                                                            "更换头像" to "重新选择一张本地图片",
                                                            "恢复微信头像" to "删除当前自定义头像"
                                                        ),
                                                        onSelected = { choice ->
                                                            if (choice == 0) {
                                                                CustomFriendAvatarPicker.launch(activity, option.id) { success ->
                                                                    if (success) revision++
                                                                }
                                                            } else {
                                                                CustomFriendAvatarStore.remove(context, option.id)
                                                                revision++
                                                            }
                                                        },
                                                        onDismiss = {}
                                                    )
                                                }
                                            }
                                            if (index != configured.lastIndex) InsetDivider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            CustomFriendAvatarRoute.CONTACT_PICKER -> ContactPickerPage(
                context = context,
                request = ContactPickerRequest(
                    title = "选择好友或群聊",
                    mode = ContactPickerMode.BOTH,
                    multiSelect = false,
                    existingValue = selectedWxid,
                    onValue = {},
                    enableLabels = false,
                    enableGroupLabels = false,
                    singleConfirmText = "确定"
                ),
                onBack = { route = CustomFriendAvatarRoute.MAIN },
                onConfirm = { selected ->
                    selected.firstOrNull()?.let { selectedWxid = it.id }
                    route = CustomFriendAvatarRoute.MAIN
                }
            )
        }
    }
}

@Composable
internal fun CustomFriendAvatarRow(
    context: Context,
    option: ContactOption,
    revision: Int,
    onClick: () -> Unit
) {
    val bitmap = remember(option.id, revision) {
        CustomFriendAvatarStore.loadBitmap(context, option.id)?.asImageBitmap()
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.secondaryVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(if (option.group) "群" else option.label.take(1).ifEmpty { "友" })
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(option.label, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text("点击更换或恢复微信头像", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
    }
}

@Composable
internal fun MessageBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MessageBlockSettings.PREFS_NAME) }
    val initialState = remember { initialMessageBlockState(sp) }
    var templates by remember {
        mutableStateOf(initialState.templates)
    }
    var bindings by remember {
        mutableStateOf(initialState.bindings)
    }
    var defaultPrivateRule by remember { mutableStateOf(MessageBlockSettings(context).defaultPrivateRule()) }
    var defaultGroupRule by remember { mutableStateOf(MessageBlockSettings(context).defaultGroupRule()) }
    var defaultOfficialRule by remember { mutableStateOf(MessageBlockSettings(context).defaultOfficialRule()) }
    var templateEditor by remember { mutableStateOf<MessageBlockTemplateEditorRequest?>(null) }
    var bindingEditor by remember { mutableStateOf<MessageBlockBindingEditorRequest?>(null) }
    var batchBindingEditor by remember { mutableStateOf<MessageBlockBatchBindingEditorRequest?>(null) }
    var bindingPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var groupMemberBindingPicker by remember { mutableStateOf<GroupMemberPickerRequest?>(null) }
    var defaultRuleEditor by remember { mutableStateOf<MessageBlockDefaultRuleKind?>(null) }
    var showTemplateManager by remember { mutableStateOf(false) }
    var showListManager by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(Unit) {
        if (initialState.shouldPersist) {
            sp.edit()
                .putString(MessageBlockSettings.KEY_TEMPLATES, MessageBlockSettings.encodeTemplates(templates))
                .putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(bindings))
                .apply()
        }
    }

    val route = when {
        templateEditor != null -> MessageBlockRoute.TemplateEditor(templateEditor!!)
        bindingEditor != null -> MessageBlockRoute.BindingEditor(bindingEditor!!)
        batchBindingEditor != null -> MessageBlockRoute.BatchBindingEditor(batchBindingEditor!!)
        bindingPicker != null -> MessageBlockRoute.ContactPicker(bindingPicker!!)
        groupMemberBindingPicker != null -> MessageBlockRoute.GroupMemberPicker(groupMemberBindingPicker!!)
        defaultRuleEditor != null -> MessageBlockRoute.DefaultRuleEditor(defaultRuleEditor!!)
        showTemplateManager -> MessageBlockRoute.TemplateManager
        showListManager -> MessageBlockRoute.ListManager
        else -> MessageBlockRoute.Main
    }

    SettingsRouteTransition(
        targetState = route,
        label = "MessageBlockRouteTransition",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is MessageBlockRoute.TemplateEditor -> {
                val request = currentRoute.request
                MessageBlockTemplatePage(
                    context = context,
                    request = request,
                    onBack = { templateEditor = null },
                    onSave = { updated ->
                        val next = if (request.index in templates.indices) {
                            templates.toMutableList().also { it[request.index] = MessageBlockSettings.clearTemplateTargets(updated) }
                        } else {
                            templates + MessageBlockSettings.clearTemplateTargets(updated)
                        }
                        templates = next
                        sp.edit().putString(MessageBlockSettings.KEY_TEMPLATES, MessageBlockSettings.encodeTemplates(next)).apply()
                        templateEditor = null
                    },
                    onDelete = {
                        if (request.index in templates.indices) {
                            val deletedId = templates[request.index].id
                            val next = templates.toMutableList().also { it.removeAt(request.index) }
                            val nextBindings = bindings.map { binding ->
                                binding.copy(templateIds = binding.templateIds - deletedId)
                            }
                            fun withoutDeletedTemplate(rule: MessageBlockDefaultRule): MessageBlockDefaultRule {
                                val nextIds = rule.templateIds - deletedId
                                return rule.copy(
                                    enabled = rule.enabled && (rule.customRules || nextIds.isNotEmpty()),
                                    templateIds = nextIds
                                )
                            }
                            val nextDefaultPrivate = withoutDeletedTemplate(defaultPrivateRule)
                            val nextDefaultGroup = withoutDeletedTemplate(defaultGroupRule)
                            val nextDefaultOfficial = withoutDeletedTemplate(defaultOfficialRule)
                            templates = next
                            bindings = nextBindings
                            defaultPrivateRule = nextDefaultPrivate
                            defaultGroupRule = nextDefaultGroup
                            defaultOfficialRule = nextDefaultOfficial
                            sp.edit()
                                .putString(MessageBlockSettings.KEY_TEMPLATES, MessageBlockSettings.encodeTemplates(next))
                                .putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(nextBindings))
                                .putString(
                                    MessageBlockSettings.KEY_DEFAULT_PRIVATE,
                                    MessageBlockSettings.encodeDefaultRule(nextDefaultPrivate, group = false)
                                )
                                .putString(
                                    MessageBlockSettings.KEY_DEFAULT_GROUP,
                                    MessageBlockSettings.encodeDefaultRule(nextDefaultGroup, group = true)
                                )
                                .putString(
                                    MessageBlockSettings.KEY_DEFAULT_OFFICIAL,
                                    MessageBlockSettings.encodeDefaultRule(
                                        nextDefaultOfficial,
                                        group = false,
                                        official = true
                                    )
                                )
                                .apply()
                        }
                        templateEditor = null
                    }
                )
            }
            is MessageBlockRoute.BindingEditor -> {
                val request = currentRoute.request
                MessageBlockBindingPage(
                    context = context,
                    request = request,
                    templates = templates,
                    onBack = { bindingEditor = null },
                    onSave = { updated ->
                        val normalized = normalizedMessageBlockBinding(updated)
                        val base = if (request.index in bindings.indices) {
                            bindings.toMutableList().also { it.removeAt(request.index) }
                        } else {
                            bindings
                        }
                        val next = upsertMessageBlockBindings(base, listOf(normalized))
                        bindings = next
                        sp.edit().putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(next)).apply()
                        bindingEditor = null
                    },
                    onDelete = {
                        if (request.index in bindings.indices) {
                            val next = bindings.toMutableList().also { it.removeAt(request.index) }
                            bindings = next
                            sp.edit().putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(next)).apply()
                        }
                        bindingEditor = null
                    }
                )
            }
            is MessageBlockRoute.BatchBindingEditor -> {
                val request = currentRoute.request
                MessageBlockBatchBindingPage(
                    context = context,
                    request = request,
                    templates = templates,
                    onBack = { batchBindingEditor = null },
                    onSave = { updated ->
                        val next = upsertMessageBlockBindings(bindings, updated)
                        bindings = next
                        sp.edit().putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(next)).apply()
                        Toast.makeText(context, "已保存 ${updated.size} 个名单项", Toast.LENGTH_SHORT).show()
                        batchBindingEditor = null
                    }
                )
            }
            is MessageBlockRoute.DefaultRuleEditor -> {
                val rule = when (currentRoute.kind) {
                    MessageBlockDefaultRuleKind.PRIVATE -> defaultPrivateRule
                    MessageBlockDefaultRuleKind.GROUP -> defaultGroupRule
                    MessageBlockDefaultRuleKind.OFFICIAL -> defaultOfficialRule
                }
                MessageBlockDefaultRulePage(
                    context = context,
                    rule = rule,
                    templates = templates,
                    onBack = { defaultRuleEditor = null },
                    onSave = { updated ->
                        val normalized = when (currentRoute.kind) {
                            MessageBlockDefaultRuleKind.PRIVATE -> updated.copy(group = false, official = false, label = "默认私聊规则")
                            MessageBlockDefaultRuleKind.GROUP -> updated.copy(group = true, official = false, label = "默认群聊规则")
                            MessageBlockDefaultRuleKind.OFFICIAL -> updated.copy(group = false, official = true, label = "默认公众号规则")
                        }
                        when (currentRoute.kind) {
                            MessageBlockDefaultRuleKind.PRIVATE -> {
                                defaultPrivateRule = normalized
                                MessageBlockSettings(context).saveDefaultPrivateRule(normalized)
                            }
                            MessageBlockDefaultRuleKind.GROUP -> {
                                defaultGroupRule = normalized
                                MessageBlockSettings(context).saveDefaultGroupRule(normalized)
                            }
                            MessageBlockDefaultRuleKind.OFFICIAL -> {
                                defaultOfficialRule = normalized
                                MessageBlockSettings(context).saveDefaultOfficialRule(normalized)
                            }
                        }
                        Toast.makeText(context, "默认规则已保存", Toast.LENGTH_SHORT).show()
                        defaultRuleEditor = null
                    }
                )
            }
            is MessageBlockRoute.ContactPicker -> {
                val request = currentRoute.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { bindingPicker = null },
                    onConfirm = { selected ->
                        val additions = selected.map {
                            messageBlockBindingFromContact(
                                option = it,
                                templates = templates,
                                existing = findMessageBlockBinding(bindings, MessageBlockSettings.TARGET_CONTACT, it.id)
                            )
                        }
                        if (additions.size == 1) {
                            val existingIndex = bindings.indexOfFirst {
                                it.targetType == additions.first().targetType &&
                                    it.targetId == additions.first().targetId
                            }
                            bindingEditor = MessageBlockBindingEditorRequest(
                                index = existingIndex.takeIf { it >= 0 } ?: bindings.size,
                                binding = additions.first(),
                                canDelete = existingIndex >= 0
                            )
                        } else if (additions.isNotEmpty()) {
                            batchBindingEditor = MessageBlockBatchBindingEditorRequest(
                                title = "批量添加名单",
                                bindings = additions
                            )
                        }
                        bindingPicker = null
                    }
                )
            }
            is MessageBlockRoute.GroupMemberPicker -> {
                val request = currentRoute.request
                GroupMemberPickerPage(
                    context = context,
                    request = request,
                    onBack = { groupMemberBindingPicker = null },
                    onConfirm = { selected ->
                        val additions = selected.map {
                            messageBlockBindingFromGroupMember(
                                entry = it,
                                templates = templates,
                                existing = findMessageBlockBinding(bindings, MessageBlockSettings.TARGET_GROUP_MEMBER, it)
                            )
                        }
                        if (additions.size == 1) {
                            val binding = additions.first()
                            val existingIndex = bindings.indexOfFirst {
                                it.targetType == binding.targetType &&
                                    it.targetId == binding.targetId
                            }
                            bindingEditor = MessageBlockBindingEditorRequest(
                                index = existingIndex.takeIf { it >= 0 } ?: bindings.size,
                                binding = binding,
                                canDelete = existingIndex >= 0
                            )
                        } else if (additions.isNotEmpty()) {
                            batchBindingEditor = MessageBlockBatchBindingEditorRequest(
                                title = "批量添加群成员",
                                bindings = additions
                            )
                        }
                        groupMemberBindingPicker = null
                    }
                )
            }
            MessageBlockRoute.TemplateManager -> {
                MessageBlockTemplateListPage(
                    templates = templates,
                    onBack = { showTemplateManager = false },
                    onOpenTemplate = { index, template ->
                        templateEditor = MessageBlockTemplateEditorRequest(index, template, canDelete = true)
                    },
                    onAddTemplate = {
                        templateEditor = MessageBlockTemplateEditorRequest(
                            templates.size,
                            newMessageBlockTemplate(templates.size + 1),
                            canDelete = false
                        )
                    }
                )
            }
            MessageBlockRoute.ListManager -> {
                MessageBlockListManagerPage(
                    bindings = bindings,
                    templates = templates,
                    onBack = { showListManager = false },
                    onOpenBinding = { index, binding ->
                        bindingEditor = MessageBlockBindingEditorRequest(index, binding, canDelete = true)
                    },
                    onAddContact = {
                        bindingPicker = ContactPickerRequest(
                            title = "选择名单",
                            mode = ContactPickerMode.ALL_CHATS,
                            multiSelect = true,
                            existingValue = "",
                            onValue = {},
                            enableLabels = true
                        )
                    },
                    onAddGroupMember = {
                        groupMemberBindingPicker = GroupMemberPickerRequest(
                            title = "选择群成员",
                            existingValue = "",
                            onValue = {}
                        )
                    },
                    onDeleteBindings = { targets ->
                        if (targets.isEmpty()) {
                            Toast.makeText(context, "没有可删除的名单", Toast.LENGTH_SHORT).show()
                        } else {
                            val removeKeys = targets.map {
                                MessageBlockSettings.bindingKey(it.targetType, it.targetId)
                            }.toSet()
                            val next = bindings.filterNot {
                                removeKeys.contains(MessageBlockSettings.bindingKey(it.targetType, it.targetId))
                            }
                            bindings = next
                            sp.edit().putString(MessageBlockSettings.KEY_BINDINGS, MessageBlockSettings.encodeBindings(next)).apply()
                            Toast.makeText(context, "已删除 ${targets.size} 个名单项", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            MessageBlockRoute.Main -> PageScaffold(
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageBlockSettings.KEY_ENABLE,
                        "启用屏蔽消息",
                        "总开关开启后，仅命中启用模板的新消息会被拦截",
                        MessageBlockSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "管理") }
            item {
                SettingsCard {
                    SelectRow(
                        title = "默认私聊规则",
                        summary = describeMessageBlockDefaultRule(defaultPrivateRule, templates),
                        onClick = { defaultRuleEditor = MessageBlockDefaultRuleKind.PRIVATE }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "默认群聊规则",
                        summary = describeMessageBlockDefaultRule(defaultGroupRule, templates),
                        onClick = { defaultRuleEditor = MessageBlockDefaultRuleKind.GROUP }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "默认公众号规则",
                        summary = describeMessageBlockDefaultRule(defaultOfficialRule, templates),
                        onClick = { defaultRuleEditor = MessageBlockDefaultRuleKind.OFFICIAL }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "模板管理",
                        summary = if (templates.isEmpty()) "暂无模板，进入后添加屏蔽规则" else "${templates.size} 个模板，进入后添加或修改规则",
                        onClick = { showTemplateManager = true }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "名单管理",
                        summary = if (bindings.isEmpty()) "暂无名单，进入后添加好友、群聊、公众号或群成员" else "${bindings.size} 个名单项，进入后分配模板",
                        onClick = { showListManager = true }
                    )
                }
            }
        }
    }
        }
    }
}

@Composable
internal fun MessageBlockTemplateListPage(
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onOpenTemplate: (Int, MessageBlockTemplate) -> Unit,
    onAddTemplate: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "模板管理",
        largeTitle = "模板管理",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "新增模板",
                onPrimaryClick = onAddTemplate,
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。模板只配置消息类型和关键词，名单在“名单管理”里分配模板。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SelectRow(
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeMessageBlockTemplate(template),
                                onClick = { onOpenTemplate(index, template) }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageBlockListManagerPage(
    bindings: List<MessageBlockBinding>,
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onOpenBinding: (Int, MessageBlockBinding) -> Unit,
    onAddContact: () -> Unit,
    onAddGroupMember: () -> Unit,
    onDeleteBindings: (List<MessageBlockBinding>) -> Unit
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf(ConversationRuleCategory.ALL) }
    var query by remember { mutableStateOf("") }
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val lower = query.trim().lowercase(Locale.US)
    val visibleBindings = bindings.mapIndexed { index, binding -> index to binding }
        .filter { (_, binding) ->
            conversationRuleCategoryMatches(binding, category) &&
                (lower.isEmpty() || messageBlockBindingMatchesQuery(binding, templates, lower))
        }
    val listTitle = when {
        bindings.isEmpty() -> "名单"
        lower.isEmpty() -> "名单 · ${bindings.size} 项"
        else -> "名单 · ${visibleBindings.size}/${bindings.size} 项"
    }
    val visibleIds = visibleBindings.mapTo(LinkedHashSet()) { it.second.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val selectedBindings = bindings.filter { it.id in selectedIds }
    PageScaffold(
        title = "名单管理",
        largeTitle = "名单管理",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedBindings.size}）",
                    onPrimaryClick = {
                        if (selectedBindings.isEmpty()) {
                            Toast.makeText(context, "请先选择名单", Toast.LENGTH_SHORT).show()
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
                    primaryText = "返回",
                    onPrimaryClick = onBack,
                    middleText = if (bindings.isEmpty()) null else "批量删除",
                    onMiddleClick = if (bindings.isEmpty()) null else {
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
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
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
                    InputRow(
                        title = "搜索名单",
                        summary = "昵称 / wxid / 群号 / 模板名",
                        value = query,
                        onValueChange = { query = it }
                    )
                }
            }
            if (!batchDeleteMode) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "添加") }
                item {
                    SettingsCard {
                        ActionRow("添加好友/群聊/公众号", "批量添加后可一次选择模板") {
                            onAddContact()
                        }
                        InsetDivider()
                        ActionRow("添加群成员", "批量添加后可一次选择模板") {
                            onAddGroupMember()
                        }
                    }
                }
            }
            item { SmallTitle(text = listTitle) }
            item {
                SettingsCard {
                    if (bindings.isEmpty()) {
                        EmptyText("暂无名单。添加好友、群聊、公众号或群成员后，再给名单分配模板。")
                    } else if (visibleBindings.isEmpty()) {
                        EmptyText("没有匹配名单。可按昵称、ID 或模板名搜索。")
                    } else {
                        visibleBindings.forEachIndexed { rowIndex, (index, binding) ->
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = binding.label.ifBlank { binding.targetId },
                                        value = index,
                                        summary = describeMessageBlockBinding(binding, templates)
                                    ),
                                    selected = binding.id in selectedIds,
                                    onClick = {
                                        selectedIds = if (binding.id in selectedIds) {
                                            selectedIds - binding.id
                                        } else {
                                            selectedIds + binding.id
                                        }
                                    }
                                )
                            } else {
                                SelectRow(
                                    title = binding.label.ifBlank { binding.targetId },
                                    summary = describeMessageBlockBinding(binding, templates),
                                    onClick = { onOpenBinding(index, binding) }
                                )
                            }
                            if (rowIndex < visibleBindings.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedBindings.size} 个名单项，此操作不可撤销。",
        labels = selectedBindings.map { it.label.ifBlank { it.targetId } },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selectedBindings
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedIds = emptySet()
            onDeleteBindings(targets)
        }
    )
}
