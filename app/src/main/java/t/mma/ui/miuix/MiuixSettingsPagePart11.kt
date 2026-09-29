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
internal fun MessageBlockBatchBindingPage(
    context: Context,
    request: MessageBlockBatchBindingEditorRequest,
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onSave: (List<MessageBlockBinding>) -> Unit
) {
    var enabled by remember(request) { mutableStateOf(true) }
    var action by remember(request) { mutableStateOf(MessageBlockSettings.ACTION_BLOCK) }
    var templateIds by remember(request) { mutableStateOf(defaultMessageBlockTemplateIds(templates)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = request.title,
        largeTitle = request.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存名单",
                onPrimaryClick = {
                    if (templateIds.isEmpty()) {
                        Toast.makeText(context, "请先选择模板", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(
                            request.bindings.map {
                                it.copy(
                                    enabled = enabled,
                                    action = action,
                                    templateIds = templateIds,
                                    customRules = false,
                                    typeAll = false,
                                    types = emptySet(),
                                    textKeywords = ""
                                )
                            }
                        )
                    }
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
            item { SmallTitle(text = "批量配置") }
            item {
                SettingsCard {
                    SelectRow(
                        title = "已选择",
                        summary = "${request.bindings.size} 个名单项",
                        onClick = {}
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用名单",
                        summary = "关闭后保留名单但不参与匹配",
                        onCheckedChange = { enabled = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = action == MessageBlockSettings.ACTION_EXCLUDE,
                        title = "排除名单",
                        summary = "开启后这些名单命中时跳过屏蔽",
                        onCheckedChange = {
                            action = if (it) MessageBlockSettings.ACTION_EXCLUDE else MessageBlockSettings.ACTION_BLOCK
                        }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。先新增模板，再回来批量添加名单。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SwitchRow(
                                checked = templateIds.contains(template.id),
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeMessageBlockTemplate(template),
                                onCheckedChange = { checked ->
                                    templateIds = if (checked) {
                                        templateIds + template.id
                                    } else {
                                        templateIds - template.id
                                    }
                                }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "名单") }
            item {
                SettingsCard {
                    request.bindings.take(30).forEachIndexed { index, binding ->
                        SelectRow(
                            title = binding.label.ifBlank { binding.targetId },
                            summary = messageBlockBindingTargetSummary(binding),
                            onClick = {}
                        )
                        if (index < minOf(request.bindings.size, 30) - 1) InsetDivider()
                    }
                    if (request.bindings.size > 30) {
                        InsetDivider()
                        EmptyText("还有 ${request.bindings.size - 30} 个名单项未展开显示")
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageBlockDefaultRulePage(
    context: Context,
    rule: MessageBlockDefaultRule,
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onSave: (MessageBlockDefaultRule) -> Unit
) {
    var enabled by remember(rule) { mutableStateOf(rule.enabled) }
    var templateIds by remember(rule) { mutableStateOf(rule.templateIds) }
    var customRules by remember(rule) { mutableStateOf(rule.customRules) }
    var typeAll by remember(rule) { mutableStateOf(rule.typeAll) }
    var types by remember(rule) { mutableStateOf(rule.types) }
    var textKeywords by remember(rule) { mutableStateOf(rule.textKeywords) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val title = when {
        rule.official -> "默认公众号规则"
        rule.group -> "默认群聊规则"
        else -> "默认私聊规则"
    }
    val scopeSummary = when {
        rule.official -> "未配置名单的公众号按此规则屏蔽"
        rule.group -> "未配置名单的群聊按此规则屏蔽"
        else -> "未配置名单的私聊按此规则屏蔽"
    }
    fun copySelectedTemplateRules() {
        val rules = messageBlockRuleStateFromTemplates(templateIds, templates)
        typeAll = rules.typeAll
        types = rules.types
        textKeywords = rules.textKeywords
    }

    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存规则",
                onPrimaryClick = {
                    if (enabled && !customRules && templateIds.isEmpty()) {
                        Toast.makeText(context, "请先选择模板", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(
                            rule.copy(
                                enabled = enabled,
                                templateIds = templateIds,
                                customRules = customRules,
                                typeAll = typeAll,
                                types = types,
                                textKeywords = if (customRules && !typeAll && types.contains(MessageBlockSettings.TYPE_TEXT)) textKeywords else ""
                            )
                        )
                    }
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
            item { SmallTitle(text = "默认规则") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用默认规则",
                        summary = scopeSummary,
                        onCheckedChange = { enabled = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。先新增模板，或开启下方专属规则。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SwitchRow(
                                checked = templateIds.contains(template.id),
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeMessageBlockTemplate(template),
                                onCheckedChange = { checked ->
                                    templateIds = if (checked) {
                                        templateIds + template.id
                                    } else {
                                        templateIds - template.id
                                    }
                                    if (!customRules) copySelectedTemplateRules()
                                }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "规则") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = customRules,
                        title = "默认专属规则",
                        summary = "开启后不依赖模板，直接按下方类型和关键词屏蔽",
                        onCheckedChange = { checked ->
                            customRules = checked
                            if (checked && !typeAll && types.isEmpty() && textKeywords.isBlank()) {
                                copySelectedTemplateRules()
                            }
                        }
                    )
                    if (customRules) {
                        InsetDivider()
                        ActionRow("套用已选模板规则", "把上方模板的类型和关键词复制到这里") {
                            copySelectedTemplateRules()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = typeAll,
                            title = "所有消息",
                            summary = "开启后默认范围内全部类型生效",
                            onCheckedChange = {
                                typeAll = it
                                if (it) textKeywords = ""
                            }
                        )
                        if (!typeAll) {
                            messageBlockTypeOptions().forEach { option ->
                                InsetDivider()
                                SwitchRow(
                                    checked = types.contains(option.key),
                                    title = option.title,
                                    summary = option.summary,
                                    onCheckedChange = { checked ->
                                        types = if (checked) {
                                            types + option.key
                                        } else {
                                            if (option.key == MessageBlockSettings.TYPE_TEXT) textKeywords = ""
                                            types - option.key
                                        }
                                    }
                                )
                            }
                            if (types.contains(MessageBlockSettings.TYPE_TEXT)) {
                                InsetDivider()
                                InputRow(
                                    "文字关键词",
                                    "文字和文字引用消息生效；多个用 |、逗号或换行分隔，留空则全部命中",
                                    textKeywords,
                                    minLines = 2
                                ) { textKeywords = it }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageBlockTemplatePage(
    context: Context,
    request: MessageBlockTemplateEditorRequest,
    onBack: () -> Unit,
    onSave: (MessageBlockTemplate) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(request) { mutableStateOf(request.template.name) }
    var enabled by remember(request) { mutableStateOf(request.template.enabled) }
    var typeAll by remember(request) { mutableStateOf(request.template.typeAll) }
    var types by remember(request) { mutableStateOf(request.template.types) }
    var textKeywords by remember(request) { mutableStateOf(request.template.textKeywords) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = name.ifBlank { "模板" },
        largeTitle = name.ifBlank { "模板" },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存模板",
                onPrimaryClick = {
                    onSave(
                        MessageBlockTemplate(
                            id = request.template.id,
                            name = name.trim().ifBlank { "模板 ${request.index + 1}" },
                            enabled = enabled,
                            mode = MessageBlockSettings.MODE_TARGETS,
                            targets = "",
                            targetGroupMembers = "",
                            excludes = "",
                            excludeGroupMembers = "",
                            typeAll = typeAll,
                            types = types,
                            textKeywords = if (!typeAll && types.contains(MessageBlockSettings.TYPE_TEXT)) textKeywords else ""
                        )
                    )
                    Toast.makeText(context, "模板已保存", Toast.LENGTH_SHORT).show()
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
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    InputRow("模板名称", "用于区分不同屏蔽模板", name) { name = it }
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用模板",
                        summary = "关闭后使用该模板的名单不会按此规则屏蔽",
                        onCheckedChange = { enabled = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "类型") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = typeAll,
                        title = "所有消息",
                        summary = "开启后该模板命中范围内全部类型生效",
                        onCheckedChange = {
                            typeAll = it
                            if (it) textKeywords = ""
                        }
                    )
                    if (!typeAll) {
                        messageBlockTypeOptions().forEach { option ->
                            InsetDivider()
                            SwitchRow(
                                checked = types.contains(option.key),
                                title = option.title,
                                summary = option.summary,
                                onCheckedChange = { checked ->
                                    types = if (checked) {
                                        types + option.key
                                    } else {
                                        if (option.key == MessageBlockSettings.TYPE_TEXT) textKeywords = ""
                                        types - option.key
                                    }
                                }
                            )
                        }
                        if (types.contains(MessageBlockSettings.TYPE_TEXT)) {
                            InsetDivider()
                            InputRow(
                                "文字关键词",
                                "文字和文字引用消息生效；多个用 |、逗号或换行分隔，留空则全部命中",
                                textKeywords,
                                minLines = 2
                            ) { textKeywords = it }
                        }
                    }
                }
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除模板", "删除后立即从模板列表移除") {
                            onDelete()
                            Toast.makeText(context, "模板已删除", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageBlockBindingPage(
    context: Context,
    request: MessageBlockBindingEditorRequest,
    templates: List<MessageBlockTemplate>,
    onBack: () -> Unit,
    onSave: (MessageBlockBinding) -> Unit,
    onDelete: () -> Unit
) {
    var enabled by remember(request) { mutableStateOf(request.binding.enabled) }
    var action by remember(request) { mutableStateOf(request.binding.action) }
    var templateIds by remember(request) { mutableStateOf(request.binding.templateIds) }
    var customRules by remember(request) { mutableStateOf(request.binding.customRules) }
    var typeAll by remember(request) { mutableStateOf(request.binding.typeAll) }
    var types by remember(request) { mutableStateOf(request.binding.types) }
    var textKeywords by remember(request) { mutableStateOf(request.binding.textKeywords) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val targetTitle = request.binding.label.ifBlank { request.binding.targetId }
    fun copySelectedTemplateRules() {
        val rules = messageBlockRuleStateFromTemplates(templateIds, templates)
        typeAll = rules.typeAll
        types = rules.types
        textKeywords = rules.textKeywords
    }

    PageScaffold(
        title = targetTitle,
        largeTitle = targetTitle,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存名单",
                onPrimaryClick = {
                    onSave(
                        request.binding.copy(
                            enabled = enabled,
                            action = action,
                            templateIds = templateIds,
                            customRules = customRules,
                            typeAll = typeAll,
                            types = types,
                            textKeywords = if (customRules && !typeAll && types.contains(MessageBlockSettings.TYPE_TEXT)) textKeywords else ""
                        )
                    )
                    Toast.makeText(context, "名单已保存", Toast.LENGTH_SHORT).show()
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
            item { SmallTitle(text = "名单信息") }
            item {
                SettingsCard {
                    SelectRow(
                        title = targetTitle,
                        summary = messageBlockBindingTargetSummary(request.binding),
                        onClick = {}
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用名单",
                        summary = "关闭后该名单不参与屏蔽消息匹配",
                        onCheckedChange = { enabled = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = action == MessageBlockSettings.ACTION_EXCLUDE,
                        title = "排除名单",
                        summary = "开启后命中该名单时跳过屏蔽；关闭时按下方模板屏蔽",
                        onCheckedChange = {
                            action = if (it) MessageBlockSettings.ACTION_EXCLUDE else MessageBlockSettings.ACTION_BLOCK
                        }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。先新增模板，再回到这里选择。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SwitchRow(
                                checked = templateIds.contains(template.id),
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeMessageBlockTemplate(template),
                                onCheckedChange = { checked ->
                                    templateIds = if (checked) {
                                        templateIds + template.id
                                    } else {
                                        templateIds - template.id
                                    }
                                    if (!customRules) copySelectedTemplateRules()
                                }
                            )
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "规则") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = customRules,
                        title = "本名单专属规则",
                        summary = if (customRules) {
                            "开启后只按下方勾选屏蔽，不改动模板本身"
                        } else {
                            "关闭时跟随上方模板；开启后可直接给这个名单勾选文字、红包等类型"
                        },
                        onCheckedChange = { checked ->
                            customRules = checked
                            if (checked && !typeAll && types.isEmpty() && textKeywords.isBlank()) {
                                copySelectedTemplateRules()
                            }
                        }
                    )
                    if (customRules) {
                        InsetDivider()
                        ActionRow("套用已选模板规则", "把上方模板的类型和关键词复制到这里") {
                            copySelectedTemplateRules()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = typeAll,
                            title = "所有消息",
                            summary = "开启后该名单命中范围内全部类型生效",
                            onCheckedChange = {
                                typeAll = it
                                if (it) textKeywords = ""
                            }
                        )
                        if (!typeAll) {
                            messageBlockTypeOptions().forEach { option ->
                                InsetDivider()
                                SwitchRow(
                                    checked = types.contains(option.key),
                                    title = option.title,
                                    summary = option.summary,
                                    onCheckedChange = { checked ->
                                        types = if (checked) {
                                            types + option.key
                                        } else {
                                            if (option.key == MessageBlockSettings.TYPE_TEXT) textKeywords = ""
                                            types - option.key
                                        }
                                    }
                                )
                            }
                            if (types.contains(MessageBlockSettings.TYPE_TEXT)) {
                                InsetDivider()
                                InputRow(
                                    "文字关键词",
                                    "文字和文字引用消息生效；多个用 |、逗号或换行分隔，留空则全部命中",
                                    textKeywords,
                                    minLines = 2
                                ) { textKeywords = it }
                            }
                        }
                    }
                }
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除名单", "删除后该名单不再套用模板") {
                            onDelete()
                            Toast.makeText(context, "名单已删除", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationGroupMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    ConversationGroupSettingsUi.Page(context, provider, onBack)
}

internal object ConversationGroupSettingsUi {
@Composable
fun Page(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    var enabled by remember { mutableStateOf(ConversationGroupStore.isEnabled(context)) }
    var groups by remember { mutableStateOf(ConversationGroupStore.load(context)) }
    var route by remember { mutableStateOf<ConversationGroupRoute>(ConversationGroupRoute.Main) }

    fun reload() {
        groups = ConversationGroupStore.load(context)
        ConversationGroupRuntime.syncAsync(context)
    }

    fun saveGroup(group: ConversationGroup, existing: Boolean): Boolean {
        val duplicate = groups.any {
            it.id != group.id && it.parentId == group.parentId &&
                it.name.equals(group.name.trim(), ignoreCase = true)
        }
        if (duplicate) {
            Toast.makeText(context, "同一层级已存在同名分组", Toast.LENGTH_SHORT).show()
            return false
        }
        val success = if (existing) {
            ConversationGroupStore.updateGroup(context, group)
        } else {
            ConversationGroupStore.addGroup(context, group)
        }
        Toast.makeText(context, if (success) "聊天分组已保存" else "聊天分组保存失败", Toast.LENGTH_SHORT).show()
        if (success) reload()
        return success
    }

    SettingsRouteTransition(
        targetState = route,
        label = "ConversationGroupRoute",
        depthOf = {
            when (it) {
                ConversationGroupRoute.Main -> 0
                is ConversationGroupRoute.Editor -> 1
                is ConversationGroupRoute.AutomaticGrouping -> 1
                is ConversationGroupRoute.ConversationPicker,
                is ConversationGroupRoute.ParentPicker,
                is ConversationGroupRoute.AutomaticPicker -> 2
            }
        }
    ) { currentRoute ->
        when (currentRoute) {
            ConversationGroupRoute.Main -> ConversationGroupListPage(
                title = provider.title(),
                enabled = enabled,
                groups = groups,
                onEnabledChange = {
                    enabled = it
                    ConversationGroupStore.setEnabled(context, it)
                    ConversationGroupRuntime.syncAsync(context)
                },
                onBack = onBack,
                onAdd = {
                    route = ConversationGroupRoute.Editor(
                        ConversationGroupStore.newGroup(parentId = null),
                        existing = false
                    )
                },
                onOpenAutomatic = {
                    val target = groups.firstOrNull { it.automaticGroupingEnabled } ?: groups.firstOrNull()
                    if (target == null) {
                        Toast.makeText(context, "请先新增一个聊天分组", Toast.LENGTH_SHORT).show()
                    } else {
                        route = ConversationGroupRoute.AutomaticGrouping(target)
                    }
                },
                onOpen = { route = ConversationGroupRoute.Editor(it, existing = true) }
            )
            is ConversationGroupRoute.Editor -> ConversationGroupEditorPage(
                context = context,
                group = currentRoute.group,
                groups = groups,
                existing = currentRoute.existing,
                onBack = { route = ConversationGroupRoute.Main },
                onPickParent = {
                    route = ConversationGroupRoute.ParentPicker(it, currentRoute.existing)
                },
                onPickConversations = {
                    route = ConversationGroupRoute.ConversationPicker(it, currentRoute.existing)
                },
                onSave = { draft ->
                    if (saveGroup(draft, currentRoute.existing)) route = ConversationGroupRoute.Main
                },
                onReorder = { action, draft ->
                    val success = ConversationGroupStore.reorderGroup(
                        context,
                        currentRoute.group.id,
                        action
                    )
                    Toast.makeText(
                        context,
                        if (success) "分组顺序已更新" else "分组排序失败",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (success) {
                        reload()
                        ConversationGroupStore.load(context)
                            .firstOrNull { it.id == currentRoute.group.id }
                            ?.let { latest ->
                                route = ConversationGroupRoute.Editor(
                                    draft.copy(order = latest.order),
                                    existing = true
                                )
                            }
                    }
                },
                onDelete = {
                    val success = ConversationGroupStore.deleteGroup(context, currentRoute.group.id)
                    Toast.makeText(context, if (success) "分组已删除" else "分组删除失败", Toast.LENGTH_SHORT).show()
                    if (success) {
                        reload()
                        route = ConversationGroupRoute.Main
                    }
                }
            )
            is ConversationGroupRoute.ConversationPicker -> ConversationGroupConversationPickerPage(
                context = context,
                group = currentRoute.group,
                groups = groups,
                onBack = {
                    route = ConversationGroupRoute.Editor(currentRoute.group, currentRoute.existing)
                },
                onConfirm = { selected ->
                    route = ConversationGroupRoute.Editor(
                        currentRoute.group.copy(conversationIds = selected),
                        currentRoute.existing
                    )
                }
            )
            is ConversationGroupRoute.ParentPicker -> ConversationGroupParentPickerPage(
                group = currentRoute.group,
                groups = groups,
                onBack = {
                    route = ConversationGroupRoute.Editor(currentRoute.group, currentRoute.existing)
                },
                onSelected = { parentId ->
                    route = ConversationGroupRoute.Editor(
                        currentRoute.group.copy(parentId = parentId),
                        currentRoute.existing
                    )
                }
            )
            is ConversationGroupRoute.AutomaticGrouping -> ConversationGroupAutomaticGroupingPage(
                context = context,
                group = currentRoute.group,
                groups = groups,
                onBack = { route = ConversationGroupRoute.Main },
                onTargetChanged = { group ->
                    route = ConversationGroupRoute.AutomaticGrouping(group)
                },
                onPick = { group, kind ->
                    route = ConversationGroupRoute.AutomaticPicker(group, kind)
                },
                onSave = { group ->
                    if (saveGroup(group, existing = true)) {
                        route = ConversationGroupRoute.Main
                    }
                }
            )
            is ConversationGroupRoute.AutomaticPicker -> ConversationGroupAutomaticPickerPage(
                context = context,
                group = currentRoute.group,
                kind = currentRoute.kind,
                onBack = {
                    route = ConversationGroupRoute.AutomaticGrouping(currentRoute.group)
                },
                onConfirm = { selected ->
                    val updated = when (currentRoute.kind) {
                        ConversationGroupAutomaticPickerKind.GROUPS -> {
                            currentRoute.group.copy(automaticGroupIds = selected)
                        }
                        ConversationGroupAutomaticPickerKind.OFFICIAL_INCLUDE -> {
                            currentRoute.group.copy(automaticOfficialIncludeIds = selected)
                        }
                        ConversationGroupAutomaticPickerKind.OFFICIAL_EXCLUDE -> {
                            currentRoute.group.copy(automaticOfficialExcludeIds = selected)
                        }
                    }
                    route = ConversationGroupRoute.AutomaticGrouping(updated)
                }
            )
        }
    }
}

@Composable
internal fun ConversationGroupListPage(
    title: String,
    enabled: Boolean,
    groups: List<ConversationGroup>,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenAutomatic: () -> Unit,
    onOpen: (ConversationGroup) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val flattened = remember(groups) { flattenConversationGroups(groups) }
    val lower = query.trim().lowercase(Locale.US)
    val visible = flattened.filter { row ->
        lower.isEmpty() || row.group.name.lowercase(Locale.US).contains(lower) ||
            row.path.lowercase(Locale.US).contains(lower)
    }
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "新增分组",
                onPrimaryClick = onAdd,
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
            item { SmallTitle(text = "功能") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "聊天分组",
                        summary = if (enabled) "已归拢会话从微信首页隐藏" else "分组配置保留，会话恢复在微信首页显示",
                        onCheckedChange = onEnabledChange
                    )
                    InsetDivider()
                    ActionRow(
                        title = "群聊归拢",
                        summary = "按规则自动归拢群聊和公众号到指定分组"
                    ) { onOpenAutomatic() }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "分组") }
            item {
                SettingsCard {
                    InputRow("搜索", "分组名称 / 所属路径", query) { query = it }
                }
            }
            item {
                SettingsCard {
                    when {
                        groups.isEmpty() -> EmptyText("暂无聊天分组")
                        visible.isEmpty() -> EmptyText("没有匹配的聊天分组")
                        else -> visible.forEachIndexed { index, row ->
                            SelectRow(
                                title = "${"  ".repeat(row.depth)}${row.group.name}",
                                summary = buildString {
                                    append(row.group.conversationIds.size).append(" 个直属会话")
                                    val childCount = groups.count { it.parentId == row.group.id }
                                    if (childCount > 0) append(" · ").append(childCount).append(" 个子分组")
                                    if (row.depth > 0) append("\n").append(row.path)
                                },
                                onClick = { onOpen(row.group) }
                            )
                            if (index < visible.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationGroupAutomaticGroupingPage(
    context: Context,
    group: ConversationGroup,
    groups: List<ConversationGroup>,
    onBack: () -> Unit,
    onTargetChanged: (ConversationGroup) -> Unit,
    onPick: (ConversationGroup, ConversationGroupAutomaticPickerKind) -> Unit,
    onSave: (ConversationGroup) -> Unit
) {
    var draft by remember(group) { mutableStateOf(group) }
    val labels = remember(context) { GroupChatLabelStore.load(context) }
    val targetChoices = remember(groups) {
        groups.map { item ->
            PopupChoice(item.name, item.id)
        }
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "群聊归拢",
        largeTitle = "群聊归拢",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = { onSave(draft) },
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
            item { SmallTitle(text = "归拢目标") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "目标聊天分组",
                        summary = "匹配到的会话会自动放入此分组",
                        options = targetChoices,
                        currentValue = group.id,
                        onValueChanged = { id ->
                            groups.firstOrNull { it.id == id }?.let(onTargetChanged)
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = draft.automaticGroupingEnabled,
                        title = "启用群聊归拢",
                        summary = "关闭后自动归拢的会话立即恢复原位置",
                        onCheckedChange = { draft = draft.copy(automaticGroupingEnabled = it) }
                    )
                }
            }
            if (draft.automaticGroupingEnabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "群聊规则") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = draft.automaticAllGroups,
                            title = "自动归拢所有群聊",
                            summary = "包含普通群和企业微信群"
                        ) { draft = draft.copy(automaticAllGroups = it) }
                        InsetDivider()
                        SwitchRow(
                            checked = draft.automaticNewGroups,
                            title = "自动归拢新群聊",
                            summary = "首次发现的新群聊自动加入目标分组"
                        ) { draft = draft.copy(automaticNewGroups = it) }
                        InsetDivider()
                        ActionRow(
                            title = "选择群聊",
                            summary = if (draft.automaticGroupIds.isEmpty()) {
                                "未选择群聊"
                            } else {
                                "已选择 ${draft.automaticGroupIds.size} 个群聊"
                            }
                        ) { onPick(draft, ConversationGroupAutomaticPickerKind.GROUPS) }
                        InsetDivider()
                        SwitchRow(
                            checked = draft.automaticMutedGroups,
                            title = "自动归拢免打扰群聊",
                            summary = "仅归拢已开启微信消息免打扰的群聊"
                        ) { draft = draft.copy(automaticMutedGroups = it) }
                        InsetDivider()
                        SwitchRow(
                            checked = draft.automaticOwnedGroups,
                            title = "自动归拢自己创建的群聊",
                            summary = "按群资料中的群主账号识别"
                        ) { draft = draft.copy(automaticOwnedGroups = it) }
                        InsetDivider()
                        SwitchRow(
                            checked = draft.automaticEnterpriseGroups,
                            title = "自动归拢企业微信群聊",
                            summary = "仅归拢企业微信互通群"
                        ) { draft = draft.copy(automaticEnterpriseGroups = it) }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "群聊标签") }
                item {
                    SettingsCard {
                        if (labels.isEmpty()) {
                            EmptyText("暂无群聊标签")
                        } else {
                            labels.forEachIndexed { index, label ->
                                SwitchRow(
                                    checked = label.id in draft.automaticGroupLabelIds,
                                    title = label.name,
                                    summary = "归拢该标签中的 ${label.groupIds.size} 个群聊"
                                ) { checked ->
                                    draft = draft.copy(
                                        automaticGroupLabelIds = if (checked) {
                                            (draft.automaticGroupLabelIds + label.id).distinct()
                                        } else {
                                            draft.automaticGroupLabelIds.filterNot { it == label.id }
                                        }
                                    )
                                }
                                if (index < labels.lastIndex) InsetDivider()
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "公众号") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = draft.automaticOfficialAccounts,
                            title = "自动归拢公众号",
                            summary = "归拢当前账号可识别的公众号会话"
                        ) { draft = draft.copy(automaticOfficialAccounts = it) }
                        InsetDivider()
                        ActionRow(
                            title = "包含公众号",
                            summary = if (draft.automaticOfficialIncludeIds.isEmpty()) {
                                "未额外包含公众号"
                            } else {
                                "已包含 ${draft.automaticOfficialIncludeIds.size} 个公众号"
                            }
                        ) { onPick(draft, ConversationGroupAutomaticPickerKind.OFFICIAL_INCLUDE) }
                        InsetDivider()
                        ActionRow(
                            title = "排除公众号",
                            summary = if (draft.automaticOfficialExcludeIds.isEmpty()) {
                                "未排除公众号"
                            } else {
                                "已排除 ${draft.automaticOfficialExcludeIds.size} 个公众号"
                            }
                        ) { onPick(draft, ConversationGroupAutomaticPickerKind.OFFICIAL_EXCLUDE) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationGroupAutomaticPickerPage(
    context: Context,
    group: ConversationGroup,
    kind: ConversationGroupAutomaticPickerKind,
    onBack: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val initialSelected = remember(group, kind) {
        when (kind) {
            ConversationGroupAutomaticPickerKind.GROUPS -> group.automaticGroupIds.toSet()
            ConversationGroupAutomaticPickerKind.OFFICIAL_INCLUDE -> {
                group.automaticOfficialIncludeIds.toSet()
            }
            ConversationGroupAutomaticPickerKind.OFFICIAL_EXCLUDE -> {
                group.automaticOfficialExcludeIds.toSet()
            }
        }
    }
    var selectedIds by remember(group, kind) { mutableStateOf(initialSelected) }
    var options by remember(kind) { mutableStateOf(emptyList<ContactOption>()) }
    var loading by remember(kind) { mutableStateOf(true) }
    var error by remember(kind) { mutableStateOf("") }
    var query by remember(kind) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    LaunchedEffect(kind) {
        loading = true
        error = ""
        val result = withContext(Dispatchers.IO) {
            runCatching { loadConversationGroupAutomaticOptions(kind) }
        }
        loading = false
        result.onSuccess { options = it }
            .onFailure { error = it.message ?: "读取联系人失败" }
    }
    val visible = options.filter { it.matchesSearch(query.trim().lowercase(Locale.US)) }
        .selectedFirst { it.id in initialSelected }
    val visibleIds = visible.mapTo(linkedSetOf()) { it.id }
    val visibleAllSelected = visibleIds.isNotEmpty() && visibleIds.all(selectedIds::contains)
    PageScaffold(
        title = kind.title,
        largeTitle = kind.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存选择",
                onPrimaryClick = { onConfirm(selectedIds.toList()) },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (visibleIds.isEmpty()) null else if (visibleAllSelected) "取消全选" else "全选",
                onMiddleClick = if (visibleIds.isEmpty()) null else {
                    {
                        selectedIds = if (visibleAllSelected) {
                            selectedIds - visibleIds
                        } else {
                            selectedIds + visibleIds
                        }
                    }
                }
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
            item {
                SettingsCard {
                    InputRow("搜索", "名称 / 备注 / wxid", query) { query = it }
                }
            }
            item {
                PickerSectionHeader(
                    text = if (visible.isEmpty()) kind.title else "${kind.title} · ${visible.size} 项"
                )
            }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入...") } }
                error.isNotBlank() -> item { SettingsCard { EmptyText(error) } }
                visible.isEmpty() -> item { SettingsCard { EmptyText(kind.emptyText) } }
                else -> visible.forEach { option ->
                    item {
                        ContactListCard(
                            option = option,
                            selected = option.id in selectedIds,
                            multiSelect = true,
                            onClick = {
                                selectedIds = if (option.id in selectedIds) {
                                    selectedIds - option.id
                                } else {
                                    selectedIds + option.id
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun loadConversationGroupAutomaticOptions(
    kind: ConversationGroupAutomaticPickerKind
): List<ContactOption> {
    val contacts = WeChatApis.contacts() ?: throw IllegalStateException("联系人列表不可用")
    val rows = when (kind) {
        ConversationGroupAutomaticPickerKind.GROUPS -> {
            contacts.getPickerGroups().mapNotNull { it.toOption(group = true) }
        }
        ConversationGroupAutomaticPickerKind.OFFICIAL_INCLUDE,
        ConversationGroupAutomaticPickerKind.OFFICIAL_EXCLUDE -> {
            contacts.getPickerOfficialAccounts().mapNotNull { it.toOption(group = false, official = true) }
        }
    }
    return rows.distinctBy { it.id }
        .sortedBy { it.label.lowercase(Locale.US) }
}

@Composable
internal fun ConversationGroupEditorPage(
    context: Context,
    group: ConversationGroup,
    groups: List<ConversationGroup>,
    existing: Boolean,
    onBack: () -> Unit,
    onPickParent: (ConversationGroup) -> Unit,
    onPickConversations: (ConversationGroup) -> Unit,
    onSave: (ConversationGroup) -> Unit,
    onReorder: (ConversationGroupStore.ReorderAction, ConversationGroup) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(group) { mutableStateOf(group.name) }
    var pinned by remember(group) { mutableStateOf(group.pinned) }
    var showDeleteConfirm by remember(group.id) { mutableStateOf(false) }
    var showReorderChoices by remember(group.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val draft = group.copy(name = name.trim(), pinned = pinned)
    val parentPath = group.parentId?.let { conversationGroupPath(groups, it) }.orEmpty()
    val siblings = remember(groups, group.parentId) {
        groups.filter { it.parentId == group.parentId && it.pinned == group.pinned }
            .sortedBy { it.order }
    }
    val siblingIndex = siblings.indexOfFirst { it.id == group.id }
    PageScaffold(
        title = if (existing) "编辑聊天分组" else "新增聊天分组",
        largeTitle = if (existing) "编辑聊天分组" else "新增聊天分组",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存分组",
                onPrimaryClick = {
                    if (draft.name.isBlank()) {
                        Toast.makeText(context, "请输入分组名称", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(draft)
                    }
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
            item { SmallTitle(text = "分组内容") }
            item {
                SettingsCard {
                    InputRow("分组名称", "同一层级内不能重名", name) { name = it }
                    if (!existing) {
                        InsetDivider()
                        SwitchRow(
                            checked = pinned,
                            title = "主页置顶",
                            summary = "新建后显示在当前层级的置顶分组区域",
                            onCheckedChange = { pinned = it }
                        )
                    }
                    InsetDivider()
                    ActionRow(
                        title = "上级分组",
                        summary = parentPath.ifBlank { "微信首页" }
                    ) { onPickParent(draft) }
                    InsetDivider()
                    ActionRow(
                        title = "会话",
                        summary = if (group.conversationIds.isEmpty()) "未选择会话" else "已选择 ${group.conversationIds.size} 个会话"
                    ) { onPickConversations(draft) }
                }
            }
            if (existing) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        if (siblings.size > 1 && siblingIndex >= 0) {
                            ActionRow(
                                title = "分组位置",
                                summary = "当前${if (group.pinned) "置顶" else "普通"}分组第 " +
                                    "${siblingIndex + 1} 个，共 ${siblings.size} 个"
                            ) { showReorderChoices = true }
                            InsetDivider()
                        }
                        ActionRow(
                            title = "删除分组",
                            summary = "子分组移到上一级，直属会话移到上级分组或微信首页"
                        ) { showDeleteConfirm = true }
                    }
                }
            }
        }
    }
    if (showReorderChoices) {
        val actions = buildList {
            if (siblingIndex > 0) {
                add(Triple("移到顶部", "设为当前层级的第一个分组", ConversationGroupStore.ReorderAction.TOP))
                add(Triple("上移", "向前移动一个位置", ConversationGroupStore.ReorderAction.UP))
            }
            if (siblingIndex in 0 until siblings.lastIndex) {
                add(Triple("下移", "向后移动一个位置", ConversationGroupStore.ReorderAction.DOWN))
                add(Triple("移到底部", "设为当前层级的最后一个分组", ConversationGroupStore.ReorderAction.BOTTOM))
            }
        }
        WindowDialog(
            show = true,
            title = "分组位置",
            onDismissRequest = { showReorderChoices = false },
            content = {
                Column {
                    actions.forEachIndexed { index, (title, summary, action) ->
                        SelectRow(title = title, summary = summary) {
                            showReorderChoices = false
                            onReorder(action, draft)
                        }
                        if (index < actions.lastIndex) InsetDivider()
                    }
                }
            }
        )
    }
    if (showDeleteConfirm) {
        WindowDialog(
            show = true,
            title = "删除聊天分组",
            onDismissRequest = { showDeleteConfirm = false },
            content = {
                Column {
                    Text(
                        text = "确定删除“${group.name}”？聊天记录、联系人和群聊不会被删除。",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        TextButton(
                            text = "确认删除",
                            onClick = {
                                showDeleteConfirm = false
                                onDelete()
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
internal fun ConversationGroupConversationPickerPage(
    context: Context,
    group: ConversationGroup,
    groups: List<ConversationGroup>,
    onBack: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var loading by remember(group.id) { mutableStateOf(true) }
    var error by remember(group.id) { mutableStateOf("") }
    var pickerData by remember(group.id) { mutableStateOf(ConversationGroupPickerData()) }
    var query by remember(group.id) { mutableStateOf("") }
    var contactFilter by remember(group.id) { mutableStateOf(ContactPickerFilter.ALL) }
    var selectedLabel by remember(group.id) { mutableStateOf("") }
    var selectedConversationGroupId by remember(group.id) { mutableStateOf("") }
    val initialSelected = remember(group) { group.conversationIds.toSet() }
    var selectedIds by remember(group) { mutableStateOf(initialSelected) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(group.id, groups) {
        loading = true
        error = ""
        val result = withContext(Dispatchers.IO) {
            runCatching { loadConversationGroupOptions(groups, group.id) }
        }
        loading = false
        result.onSuccess { pickerData = it }
            .onFailure { error = it.message ?: "读取会话列表失败" }
    }
    val conversations = pickerData.options
    val conversationGroupFilters = remember(groups, conversations) {
        ConversationGroupPickerSupport.filters(groups, conversations.map { it.id })
    }
    val conversationGroupChoices = remember(conversationGroupFilters) {
        listOf(PopupChoice("全部会话", "")) + conversationGroupFilters.map {
            PopupChoice(it.name, it.id)
        }
    }
    LaunchedEffect(conversationGroupFilters) {
        if (selectedConversationGroupId.isNotBlank() &&
            conversationGroupFilters.none { it.id == selectedConversationGroupId }
        ) {
            selectedConversationGroupId = ""
        }
    }
    val selectedConversationIds = remember(conversationGroupFilters, selectedConversationGroupId) {
        conversationGroupFilters.firstOrNull { it.id == selectedConversationGroupId }?.conversationIds
    }
    val scopedConversations = remember(conversations, selectedConversationIds) {
        selectedConversationIds?.let { ids -> conversations.filter { it.id in ids } } ?: conversations
    }
    val labels = remember(scopedConversations) {
        scopedConversations.flatMap { it.labels }
            .distinct()
            .sortedWith(compareBy { it.lowercase(Locale.US) })
    }
    LaunchedEffect(contactFilter, labels) {
        if (contactFilter == ContactPickerFilter.LABELS) {
            if (selectedLabel.isBlank() || selectedLabel !in labels) {
                selectedLabel = labels.firstOrNull().orEmpty()
            }
        } else if (selectedLabel.isNotBlank()) {
            selectedLabel = ""
        }
    }
    val lower = query.trim().lowercase(Locale.US)
    val filtered = scopedConversations.filter {
        when (contactFilter) {
            ContactPickerFilter.FRIENDS -> it.id in pickerData.friendIds
            ContactPickerFilter.GROUPS -> it.group
            ContactPickerFilter.OFFICIALS -> it.id in pickerData.officialIds
            ContactPickerFilter.LABELS -> selectedLabel.isNotBlank() &&
                it.id in pickerData.friendIds &&
                selectedLabel in it.labels
            ContactPickerFilter.ALL -> true
        }
    }
    val visible = filtered.filter { it.matchesSearch(lower) }
        .selectedFirst { initialSelected.contains(it.id) }
    val visibleIds = visible.map { it.id }.toSet()
    val visibleAllSelected = visibleIds.isNotEmpty() && visibleIds.all(selectedIds::contains)
    PageScaffold(
        title = "选择分组会话",
        largeTitle = "选择分组会话",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存选择",
                onPrimaryClick = { onConfirm(selectedIds.toList()) },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (visibleIds.isNotEmpty()) {
                    if (visibleAllSelected) "取消全选" else "全选"
                } else null,
                onMiddleClick = if (visibleIds.isNotEmpty()) {
                    {
                        selectedIds = if (visibleAllSelected) selectedIds - visibleIds else selectedIds + visibleIds
                    }
                } else null
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
            item {
                SettingsCard {
                    ContactFilterRow(
                        selected = contactFilter,
                        enableLabels = true,
                        enableOfficials = true,
                        onSelected = {
                            contactFilter = it
                            query = ""
                        }
                    )
                }
            }
            if (contactFilter == ContactPickerFilter.LABELS) {
                item {
                    SettingsCard {
                        ContactLabelFilterRow(
                            labels = labels,
                            selected = selectedLabel,
                            onSelected = {
                                selectedLabel = it
                                query = ""
                            }
                        )
                    }
                }
            }
            if (conversationGroupChoices.size > 1) {
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "聊天分组",
                            summary = "只显示所选分组及其子分组中的会话",
                            options = conversationGroupChoices,
                            currentValue = selectedConversationGroupId,
                            onValueChanged = {
                                selectedConversationGroupId = it
                                query = ""
                            }
                        )
                    }
                }
            }
            item {
                SettingsCard {
                    InputRow("搜索", "会话名称 / 备注 / wxid", query) { query = it }
                }
            }
            item {
                PickerSectionHeader(
                    text = if (visible.isNotEmpty()) {
                        "${contactFilter.title} · ${visible.size} 项"
                    } else {
                        contactFilter.title
                    }
                )
            }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入会话...") } }
                error.isNotBlank() -> item { SettingsCard { EmptyText(error) } }
                visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配的会话") } }
                else -> visible.forEach { option ->
                    item {
                        ContactListCard(
                            option = option,
                            selected = selectedIds.contains(option.id),
                            multiSelect = true,
                            onClick = {
                                selectedIds = if (selectedIds.contains(option.id)) {
                                    selectedIds - option.id
                                } else {
                                    selectedIds + option.id
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConversationGroupParentPickerPage(
    group: ConversationGroup,
    groups: List<ConversationGroup>,
    onBack: () -> Unit,
    onSelected: (String?) -> Unit
) {
    val excluded = remember(groups, group.id) {
        ConversationGroupStore.descendantIds(groups, group.id) + group.id
    }
    val candidates = remember(groups, excluded) {
        flattenConversationGroups(groups).filterNot { it.group.id in excluded }
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "选择上级分组",
        largeTitle = "选择上级分组",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "微信首页",
                onPrimaryClick = { onSelected(null) },
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
            item { SmallTitle(text = "可用分组") }
            item {
                SettingsCard {
                    if (candidates.isEmpty()) {
                        EmptyText("没有其它可用分组")
                    } else {
                        candidates.forEachIndexed { index, row ->
                            SelectRow(
                                title = "${"  ".repeat(row.depth)}${row.group.name}",
                                summary = row.path,
                                onClick = { onSelected(row.group.id) }
                            )
                            if (index < candidates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

internal data class FlattenedConversationGroup(
    val group: ConversationGroup,
    val depth: Int,
    val path: String
)

internal fun flattenConversationGroups(groups: List<ConversationGroup>): List<FlattenedConversationGroup> {
    val normalized = ConversationGroupStore.normalize(groups)
    val byParent = normalized.groupBy { it.parentId }
    val result = arrayListOf<FlattenedConversationGroup>()
    val visited = hashSetOf<String>()
    fun visit(parentId: String?, depth: Int, path: List<String>) {
        byParent[parentId].orEmpty().sortedBy { it.order }.forEach { group ->
            if (!visited.add(group.id)) return@forEach
            val nextPath = path + group.name
            result += FlattenedConversationGroup(group, depth, nextPath.joinToString(" / "))
            visit(group.id, depth + 1, nextPath)
        }
    }
    visit(null, 0, emptyList())
    return result
}

internal fun conversationGroupPath(groups: List<ConversationGroup>, groupId: String): String {
    return flattenConversationGroups(groups).firstOrNull { it.group.id == groupId }?.path.orEmpty()
}

internal data class ConversationGroupPickerData(
    val options: List<ContactOption> = emptyList(),
    val friendIds: Set<String> = emptySet(),
    val officialIds: Set<String> = emptySet()
)

internal fun loadConversationGroupOptions(
    groups: List<ConversationGroup>,
    currentGroupId: String
): ConversationGroupPickerData {
    val conversationApi = WeChatApis.conversations() ?: throw IllegalStateException("会话列表不可用")
    val contactApi = WeChatApis.contact().contacts()
    val assignedIds = groups.flatMap { it.conversationIds }.toSet()
    val friendIds = runCatching {
        contactApi?.getPickerContacts().orEmpty()
            .map { it.wxId.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }.getOrDefault(emptySet())
    val officialIds = runCatching {
        contactApi?.getPickerOfficialAccounts().orEmpty()
            .map { it.wxId.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }.getOrDefault(emptySet())
    val labelsByUser = linkedMapOf<String, MutableList<String>>()
    runCatching { contactApi?.getContactLabelList().orEmpty() }
        .getOrDefault(emptyList())
        .forEach { label ->
            val labelName = label.labelName.ifBlank { label.labelId }
            if (labelName.isBlank()) return@forEach
            label.userNameList.forEach { wxId ->
                if (wxId.isNotBlank()) {
                    labelsByUser.getOrPut(wxId) { arrayListOf() }.add(labelName)
                }
            }
        }
    val paths = flattenConversationGroups(groups).associate { it.group.id to it.path }
    val ownerLabels = buildMap {
        groups.filterNot { it.id == currentGroupId }.forEach { owner ->
            owner.conversationIds.forEach { talker ->
                paths[owner.id]?.takeIf(String::isNotBlank)?.let { put(talker, "已在：$it") }
            }
        }
    }
    val rows = conversationApi.getRecentConversationUsernames(10000)
    val result = linkedMapOf<String, ContactOption>()
    rows.forEach { username ->
        val talker = username.trim()
        if (talker.isBlank() || ConversationGroupRuntime.isVirtualTalker(talker) ||
            talker in CONVERSATION_GROUP_EXCLUDED_TALKERS
        ) return@forEach
        val contact = runCatching { contactApi?.getContact(talker) }.getOrNull()
        if (contact == null && talker != "filehelper" && talker !in assignedIds) return@forEach
        val group = contact?.isGroup() == true
        result[talker] = ContactOption(
            id = talker,
            label = contact?.pickerDisplayName(group)
                .orEmpty()
                .ifBlank { conversationApi.getConversationTitle(talker).ifBlank { talker } },
            group = group,
            avatarUrl = contact?.avatarUrl.orEmpty(),
            avatarBackupUrl = contact?.avatarBackupUrl.orEmpty(),
            labels = labelsByUser[talker].orEmpty().distinct(),
            official = contact?.isOfficialAccount() == true,
            extraSummary = ownerLabels[talker].orEmpty(),
            searchAliases = listOfNotNull(
                contact?.remarkName,
                contact?.nickname,
                contact?.customWxId,
                talker
            ).filter { it.isNotBlank() }.distinct()
        )
    }
    assignedIds.filterNot(result::containsKey).forEach { talker ->
        val contact = runCatching { contactApi?.getContact(talker) }.getOrNull()
        val group = contact?.isGroup() == true ||
            talker.endsWith("@chatroom") ||
            talker.endsWith("@im.chatroom")
        result[talker] = ContactOption(
            id = talker,
            label = contact?.pickerDisplayName(group)
                .orEmpty()
                .ifBlank { conversationApi.getConversationTitle(talker).ifBlank { talker } },
            group = group,
            avatarUrl = contact?.avatarUrl.orEmpty(),
            avatarBackupUrl = contact?.avatarBackupUrl.orEmpty(),
            labels = labelsByUser[talker].orEmpty().distinct(),
            official = contact?.isOfficialAccount() == true || talker.startsWith("gh_"),
            extraSummary = ownerLabels[talker].orEmpty(),
            searchAliases = listOfNotNull(
                contact?.remarkName,
                contact?.nickname,
                contact?.customWxId,
                talker
            ).filter { it.isNotBlank() }.distinct()
        )
    }
    return ConversationGroupPickerData(
        options = result.values.toList(),
        friendIds = friendIds,
        officialIds = officialIds + result.values.filter { it.official }.map { it.id }
    )
}

internal val CONVERSATION_GROUP_EXCLUDED_TALKERS = setOf(
    "message_fold",
    "conversationboxservice",
    "officialaccounts",
    "appbrand_notify_message",
    "notifymessage",
    "qmessage",
    "floatbottle"
)
}
