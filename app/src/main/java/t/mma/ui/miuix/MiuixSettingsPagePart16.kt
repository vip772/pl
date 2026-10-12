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
internal fun RedPacketTemplateListPage(
    templates: List<RedPacketRuleTemplate>,
    onBack: () -> Unit,
    onOpenTemplate: (Int, RedPacketRuleTemplate) -> Unit,
    onAddTemplate: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "红包模板",
        largeTitle = "红包模板",
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
                        EmptyText("暂无模板。模板只配置抢包策略，适用聊天在“适用聊天”里分配。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SelectRow(
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = describeRedPacketTemplate(template),
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
internal fun RedPacketBindingListPage(
    bindings: List<RedPacketRuleBinding>,
    templates: List<RedPacketRuleTemplate>,
    onBack: () -> Unit,
    onOpenBinding: (Int, RedPacketRuleBinding) -> Unit,
    onAddBinding: () -> Unit,
    onDeleteBindings: (List<RedPacketRuleBinding>) -> Unit
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
                (lower.isEmpty() || redPacketBindingMatchesQuery(binding, templates, lower))
        }
    val listTitle = when {
        bindings.isEmpty() -> "适用聊天"
        lower.isEmpty() -> "适用聊天 · ${bindings.size} 项"
        else -> "适用聊天 · ${visibleBindings.size}/${bindings.size} 项"
    }
    val visibleIds = visibleBindings.mapTo(LinkedHashSet()) { it.second.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val selectedBindings = bindings.filter { it.id in selectedIds }
    PageScaffold(
        title = "适用聊天",
        largeTitle = "适用聊天",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedBindings.size}）",
                    onPrimaryClick = {
                        if (selectedBindings.isEmpty()) {
                            Toast.makeText(context, "请先选择适用聊天", Toast.LENGTH_SHORT).show()
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
                    primaryText = "添加聊天",
                    onPrimaryClick = onAddBinding,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
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
                        onSelected = { category = it },
                        includeOfficial = false
                    )
                }
            }
            item {
                SettingsCard {
                    InputRow(
                        title = "搜索聊天",
                        summary = "昵称 / wxid / 群号 / 模板名",
                        value = query,
                        onValueChange = { query = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = listTitle) }
            item {
                SettingsCard {
                    if (bindings.isEmpty()) {
                        EmptyText("暂无适用聊天。添加群聊或好友后，再给它分配红包模板。")
                    } else if (visibleBindings.isEmpty()) {
                        EmptyText("没有匹配结果。可按昵称、ID 或模板名搜索。")
                    } else {
                        visibleBindings.forEachIndexed { rowIndex, (index, binding) ->
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = binding.label.ifBlank { binding.targetId },
                                        value = index,
                                        summary = describeRedPacketBinding(binding, templates)
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
                                    summary = describeRedPacketBinding(binding, templates),
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
    if (showDeleteConfirm) {
        WindowDialog(
            show = true,
            title = "确认批量删除",
            onDismissRequest = { showDeleteConfirm = false },
            content = {
                Column {
                    Text(
                        text = buildString {
                            append("将删除已选的 ${selectedBindings.size} 个适用聊天，此操作不可撤销。")
                            val names = selectedBindings.take(6).joinToString("、") {
                                it.label.ifBlank { it.targetId }
                            }
                            if (names.isNotBlank()) append("\n\n$names")
                            if (selectedBindings.size > 6) append(" 等")
                        },
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
                                val targets = selectedBindings
                                showDeleteConfirm = false
                                batchDeleteMode = false
                                selectedIds = emptySet()
                                onDeleteBindings(targets)
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
internal fun RedPacketTemplatePage(
    context: Context,
    request: RedPacketTemplateEditorRequest,
    onBack: () -> Unit,
    onSave: (RedPacketRuleTemplate) -> Unit,
    onDelete: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RedPacketSettings.PREFS_NAME) }
    var name by remember(request) { mutableStateOf(request.template.name) }
    var enabled by remember(request) { mutableStateOf(request.template.enabled) }
    var grabMode by remember(request) { mutableStateOf(request.template.grabMode) }
    var delayMode by remember(request) { mutableStateOf(normalizeRedPacketDelayModeForUi(request.template)) }
    var delayMs by remember(request) { mutableStateOf(request.template.delayMs.coerceAtLeast(0L).toString()) }
    var randomMinMs by remember(request) { mutableStateOf(request.template.randomMinMs.coerceAtLeast(0L).toString()) }
    var randomMaxMs by remember(request) { mutableStateOf(request.template.randomMaxMs.coerceAtLeast(0L).toString()) }
    var skipSelf by remember(request) { mutableStateOf(request.template.skipSelf) }
    var keywordMode by remember(request) { mutableStateOf(request.template.keywordMode) }
    var keywords by remember(request) { mutableStateOf(request.template.keywords) }
    var quietEnabled by remember(request) { mutableStateOf(request.template.quietEnabled) }
    var quietStart by remember(request) { mutableStateOf(formatRedPacketSecond(request.template.quietStartSecond)) }
    var quietEnd by remember(request) { mutableStateOf(formatRedPacketSecond(request.template.quietEndSecond)) }
    val initialPrivateReplySteps = remember(request) {
        normalizeRedPacketReplyStepsForUi(
            request.template.replySteps,
            request.template.replyMode,
            request.template.replyText,
            request.template.replyDelayMs,
            request.template.replyRandom
        )
    }
    var privateReplySteps by remember(request) {
        mutableStateOf(initialPrivateReplySteps)
    }
    var groupReplySteps by remember(request) {
        mutableStateOf(
            request.template.groupReplySteps
                ?.map(::normalizeRedPacketReplyStepForUi)
                ?: initialPrivateReplySteps
        )
    }
    var notifySystemEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifySystemEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SYSTEM_ENABLE, false)
            }
        )
    }
    var notifyToastEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyToastEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_TOAST_ENABLE, false)
            }
        )
    }
    var notifySoundEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifySoundEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SOUND_ENABLE, false)
            }
        )
    }
    var notifySoundMode by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifySoundMode
            } else {
                sp.getInt(RedPacketSettings.KEY_NOTIFY_SOUND_MODE, RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM)
            }
        )
    }
    var notifyVibrateEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyVibrateEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_VIBRATE_ENABLE, false)
            }
        )
    }
    var notifySoundUri by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifySoundUri
            } else {
                sp.getString(RedPacketSettings.KEY_NOTIFY_SOUND_URI, "") ?: ""
            }
        )
    }
    var notifyText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyText
            } else {
                sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
            }
        )
    }
    var notifyToastText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyToastText
            } else {
                sp.getString(
                    RedPacketSettings.KEY_NOTIFY_TOAST_TEXT,
                    sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
                ) ?: "抢到红包 {amount} 元"
            }
        )
    }
    var notifyFailedSystemEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyFailedSystemEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_SYSTEM_ENABLE, false)
            }
        )
    }
    var notifyFailedToastEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyFailedToastEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_ENABLE, false)
            }
        )
    }
    var notifyFailedText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyFailedText
            } else {
                sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "未抢到红包"
            }
        )
    }
    var notifyFailedToastText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.notifyFailedToastText
            } else {
                sp.getString(
                    RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_TEXT,
                    sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "未抢到红包"
                ) ?: "未抢到红包"
            }
        )
    }
    var announceEnabled by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.announceEnabled
            } else {
                sp.getBoolean(RedPacketSettings.KEY_ANNOUNCE_ENABLE, false)
            }
        )
    }
    var announceText by remember(request) {
        mutableStateOf(
            if (request.template.notificationConfigured) {
                request.template.announceText
            } else {
                sp.getString(RedPacketSettings.KEY_ANNOUNCE_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
            }
        )
    }
    var editingReplyStepsForGroup by remember(request) { mutableStateOf<Boolean?>(null) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    editingReplyStepsForGroup?.let { group ->
        RedPacketReplyStepsPage(
            context = context,
            title = if (group) "群红包回复" else "私聊红包回复",
            initialSteps = if (group) groupReplySteps else privateReplySteps,
            onBack = { editingReplyStepsForGroup = null },
            onSave = { steps ->
                if (group) groupReplySteps = steps else privateReplySteps = steps
                editingReplyStepsForGroup = null
                Toast.makeText(context, "回复步骤已更新", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    PageScaffold(
        title = name.ifBlank { "红包模板" },
        largeTitle = name.ifBlank { "红包模板" },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存模板",
                onPrimaryClick = {
                    val customDelay = if (delayMode == RedPacketRuleConfig.DELAY_CUSTOM) {
                        delayMs.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L
                    } else {
                        0L
                    }
                    val minDelay = randomMinMs.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L
                    val maxDelay = randomMaxMs.toLongOrNull()?.coerceIn(minDelay, 600000L) ?: minDelay
                    val cleanPrivateReplySteps = cleanRedPacketReplySteps(privateReplySteps)
                    val cleanGroupReplySteps = cleanRedPacketReplySteps(groupReplySteps)
                    val firstReply = cleanPrivateReplySteps.firstOrNull()
                    onSave(
                        RedPacketRuleTemplate(
                            id = request.template.id,
                            name = name.trim().ifBlank { "模板 ${request.index + 1}" },
                            enabled = enabled,
                            grabMode = grabMode,
                            delayMode = delayMode,
                            delayMs = customDelay,
                            randomMinMs = minDelay,
                            randomMaxMs = maxDelay,
                            skipSelf = skipSelf,
                            listMode = request.template.listMode,
                            whitelist = request.template.whitelist,
                            blacklist = request.template.blacklist,
                            keywordMode = keywordMode,
                            keywords = if (keywordMode == 0) "" else keywords.trim(),
                            quietEnabled = quietEnabled,
                            quietStartSecond = parseRedPacketSecond(quietStart, request.template.quietStartSecond),
                            quietEndSecond = parseRedPacketSecond(quietEnd, request.template.quietEndSecond),
                            replyMode = firstReply?.mode ?: RedPacketRuleConfig.REPLY_OFF,
                            replyText = firstReply?.content.orEmpty(),
                            replyDelayMs = firstReply?.delayMs ?: 0L,
                            replyRandom = firstReply?.random ?: false,
                            replySteps = cleanPrivateReplySteps,
                            groupReplySteps = cleanGroupReplySteps,
                            notificationConfigured = true,
                            notifySystemEnabled = notifySystemEnabled,
                            notifyToastEnabled = notifyToastEnabled,
                            notifySoundEnabled = notifySoundEnabled,
                            notifySoundMode = notifySoundMode,
                            notifyVibrateEnabled = notifyVibrateEnabled,
                            notifySoundUri = notifySoundUri.trim(),
                            notifyText = notifyText,
                            notifyToastText = notifyToastText,
                            notifyFailedSystemEnabled = notifyFailedSystemEnabled,
                            notifyFailedToastEnabled = notifyFailedToastEnabled,
                            notifyFailedText = notifyFailedText,
                            notifyFailedToastText = notifyFailedToastText,
                            announceEnabled = announceEnabled,
                            announceText = announceText
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
                    InputRow("模板名称", "例如：慢抢红包群、只抢口令红包", name) { name = it }
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用模板",
                        summary = "关闭后使用该模板的聊天不会自动抢",
                        onCheckedChange = { enabled = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "抢包") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "抢包模式",
                        summary = if (grabMode == 1) "静默抢包" else "打开红包页面",
                        options = optionItems("打开红包页面" to 0, "静默抢包" to 1),
                        currentValue = grabMode,
                        onValueChanged = { grabMode = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = skipSelf,
                        title = "跳过自己的红包",
                        summary = "自己发出的红包不会抢",
                        onCheckedChange = { skipSelf = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "延迟") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "抢包延迟",
                        summary = redPacketDelayModeLabel(delayMode),
                        options = redPacketDelayModeOptions(includeRandom = true),
                        currentValue = delayMode,
                        onValueChanged = { delayMode = it }
                    )
                    if (delayMode == RedPacketRuleConfig.DELAY_CUSTOM) {
                        InsetDivider()
                        NumberInputRow("自定义延迟", "单位 ms，0 表示不延迟", delayMs) { delayMs = it }
                    } else if (delayMode == RedPacketRuleConfig.DELAY_RANDOM) {
                        InsetDivider()
                        NumberInputRow("最小延迟", "单位 ms", randomMinMs) { randomMinMs = it }
                        InsetDivider()
                        NumberInputRow("最大延迟", "单位 ms，不能小于最小延迟", randomMaxMs) { randomMaxMs = it }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "过滤") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "关键词过滤",
                        summary = when (keywordMode) {
                            1 -> "只抢含关键词"
                            2 -> "屏蔽含关键词"
                            else -> "不限关键词"
                        },
                        options = optionItems("不限关键词" to 0, "只抢含关键词" to 1, "屏蔽含关键词" to 2),
                        currentValue = keywordMode,
                        onValueChanged = {
                            if (it == 0) keywords = ""
                            keywordMode = it
                        }
                    )
                    if (keywordMode != 0) {
                        InsetDivider()
                        InputRow(
                            title = "关键词",
                            summary = "多个用 |、逗号或换行分隔",
                            value = keywords,
                            minLines = 2,
                            onValueChange = { keywords = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = quietEnabled,
                        title = "禁抢时段",
                        summary = "在指定时间段内不抢红包",
                        onCheckedChange = { quietEnabled = it }
                    )
                    if (quietEnabled) {
                        InsetDivider()
                        TimeOfDayPickerRow("开始时间", quietStart) { quietStart = it }
                        InsetDivider()
                        TimeOfDayPickerRow("结束时间", quietEnd) { quietEnd = it }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "抢后回复") }
            item {
                SettingsCard {
                    SelectRow(
                        title = "私聊红包回复",
                        summary = describeRedPacketReplySteps(privateReplySteps),
                        onClick = { editingReplyStepsForGroup = false }
                    )
                    InsetDivider()
                    SelectRow(
                        title = "群红包回复",
                        summary = describeRedPacketReplySteps(groupReplySteps),
                        onClick = { editingReplyStepsForGroup = true }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "通知提醒") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = notifySystemEnabled,
                        title = "通知栏提醒",
                        summary = "抢到红包时提醒",
                        onCheckedChange = { notifySystemEnabled = it }
                    )
                    if (notifySystemEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "通知栏文案",
                            summary = "支持下方变量",
                            value = notifyText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifyToastEnabled,
                        title = "浮窗提醒",
                        summary = "抢到红包时短暂提示",
                        onCheckedChange = { notifyToastEnabled = it }
                    )
                    if (notifyToastEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "浮窗文案",
                            summary = "支持下方变量",
                            value = notifyToastText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyToastText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifySoundEnabled,
                        title = "通知铃声",
                        summary = "开启后播放通知铃声",
                        onCheckedChange = { notifySoundEnabled = it }
                    )
                    if (notifySoundEnabled) {
                        InsetDivider()
                        PopupOptionRow(
                            title = "铃声模式",
                            summary = if (notifySoundMode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) "从文件选择铃声" else "选择系统铃声",
                            options = optionItems(
                                "选择系统铃声" to RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM,
                                "从文件选择铃声" to RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM
                            ),
                            currentValue = notifySoundMode,
                            onValueChanged = {
                                notifySoundMode = it
                                notifySoundUri = ""
                            }
                        )
                        InsetDivider()
                        ActionRow("选择铃声", ringtoneDisplayName(context, notifySoundUri, notifySoundMode)) {
                            val activity = context as? Activity
                            if (activity == null) {
                                Toast.makeText(context, "当前页面无法打开铃声选择器", Toast.LENGTH_SHORT).show()
                            } else {
                                val onPicked: (String) -> Unit = { uri ->
                                    if (uri.isNotBlank()) {
                                        notifySoundUri = if (notifySoundMode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                                            CustomNotificationRuntime.freezeRingtoneUri(context, uri)
                                        } else {
                                            uri
                                        }
                                    }
                                }
                                if (notifySoundMode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                                    RingtonePickerBridge.launchFile(activity, onPicked)
                                } else {
                                    RingtonePickerBridge.launchSystem(activity, notifySoundUri, onPicked)
                                }
                            }
                        }
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifyVibrateEnabled,
                        title = "通知震动",
                        summary = "开启后触发通知震动",
                        onCheckedChange = { notifyVibrateEnabled = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = announceEnabled,
                        title = "抢到红包播报",
                        summary = "抢到后用系统语音播报",
                        onCheckedChange = { announceEnabled = it }
                    )
                    if (announceEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "播报文案",
                            summary = "支持下方变量",
                            value = announceText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { announceText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifyFailedSystemEnabled,
                        title = "未抢到提醒",
                        summary = "未抢到红包时通知",
                        onCheckedChange = { notifyFailedSystemEnabled = it }
                    )
                    if (notifyFailedSystemEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "未抢到通知栏文案",
                            summary = "支持下方变量",
                            value = notifyFailedText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyFailedText = it }
                        )
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = notifyFailedToastEnabled,
                        title = "未抢到浮窗",
                        summary = "未抢到红包时短暂提示",
                        onCheckedChange = { notifyFailedToastEnabled = it }
                    )
                    if (notifyFailedToastEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "未抢到浮窗文案",
                            summary = "支持下方变量",
                            value = notifyFailedToastText,
                            variables = redPacketTemplateVariables,
                            onValueChange = { notifyFailedToastText = it }
                        )
                    }
                }
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除模板", "删除后使用该模板的聊天会变成未绑定模板") {
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
internal fun RedPacketBindingPage(
    context: Context,
    request: RedPacketBindingEditorRequest,
    templates: List<RedPacketRuleTemplate>,
    onBack: () -> Unit,
    onSave: (RedPacketRuleBinding) -> Unit,
    onDelete: () -> Unit
) {
    var enabled by remember(request) { mutableStateOf(request.binding.enabled) }
    var templateId by remember(request) { mutableStateOf(request.binding.templateId) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val targetTitle = request.binding.label.ifBlank { request.binding.targetId }

    PageScaffold(
        title = targetTitle,
        largeTitle = targetTitle,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存聊天",
                onPrimaryClick = {
                    onSave(
                        request.binding.copy(
                            enabled = enabled,
                            templateId = templateId,
                            customRules = false,
                            overrideRule = null
                        )
                    )
                    Toast.makeText(context, "适用聊天已保存", Toast.LENGTH_SHORT).show()
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
            item { SmallTitle(text = "聊天") }
            item {
                SettingsCard {
                    InfoRow("名称", targetTitle)
                    InsetDivider()
                    InfoRow("ID", request.binding.targetId)
                    InsetDivider()
                    SwitchRow(
                        checked = enabled,
                        title = "启用",
                        summary = "关闭后这个聊天不会按模板自动抢红包",
                        onCheckedChange = { enabled = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
            item {
                SettingsCard {
                    OptionChoiceRow(
                        item = OptionItem("跟随默认规则", -1, "启用后按默认规则或旧版全局设置抢红包"),
                        selected = templateId.isBlank(),
                        onClick = { templateId = "" }
                    )
                    if (templates.isNotEmpty()) InsetDivider()
                    templates.forEachIndexed { index, template ->
                        OptionChoiceRow(
                            item = OptionItem(
                                label = template.name.ifBlank { "模板 ${index + 1}" },
                                value = index,
                                summary = describeRedPacketTemplate(template)
                            ),
                            selected = templateId == template.id,
                            onClick = { templateId = template.id }
                        )
                        if (index < templates.lastIndex) InsetDivider()
                    }
                }
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("移除适用聊天", "移除后该聊天回到默认规则") {
                            onDelete()
                            Toast.makeText(context, "适用聊天已移除", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AntiRecallMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AntiRecallSettings.PREFS_NAME) }
    var noticeText by remember {
        mutableStateOf(
            normalizeAntiRecallNoticeText(sp.getString(
                AntiRecallSettings.KEY_NOTICE_TEXT,
                AntiRecallSettings.DEFAULT_NOTICE_TEXT
            ) ?: AntiRecallSettings.DEFAULT_NOTICE_TEXT)
        )
    }
    var noticeTimeFormat by remember {
        mutableStateOf(
            sp.getString(
                AntiRecallSettings.KEY_NOTICE_TIME_FORMAT,
                AntiRecallSettings.DEFAULT_NOTICE_TIME_FORMAT
            ) ?: AntiRecallSettings.DEFAULT_NOTICE_TIME_FORMAT
        )
    }
    var dynamicRecall by remember { mutableStateOf(sp.getBoolean(AntiRecallSettings.KEY_DYNAMIC_ENABLE, false)) }
    var dynamicColor by remember { mutableStateOf(sp.getString(AntiRecallSettings.KEY_DYNAMIC_COLOR,
        t.mma.hooks.items.antirecall.DynamicRecallGradient.DEFAULT).orEmpty()) }
    var recallMessageEnabled by remember { mutableStateOf(sp.getBoolean(
        AntiRecallSettings.KEY_DYNAMIC_MESSAGE_ENABLE, AntiRecallSettings.DEFAULT_DYNAMIC_MESSAGE_ENABLE)) }
    var recallMessageColor by remember { mutableStateOf(
        t.mma.hooks.items.antirecall.DynamicRecallTint.normalize(
            sp.getString(AntiRecallSettings.KEY_DYNAMIC_MESSAGE_COLOR,
                t.mma.hooks.items.antirecall.DynamicRecallTint.DEFAULT_COLOR))) }
    var recallMessageOpacity by remember { mutableStateOf(
        t.mma.hooks.items.antirecall.DynamicRecallTint.opacity(
            sp.getInt(AntiRecallSettings.KEY_DYNAMIC_MESSAGE_OPACITY,
                t.mma.hooks.items.antirecall.DynamicRecallTint.DEFAULT_OPACITY))) }
    var dynamicSpeed by remember { mutableStateOf(sp.getInt(AntiRecallSettings.KEY_DYNAMIC_SPEED,
        t.mma.hooks.items.dynamicbadge.DynamicBadgeSettings.DEFAULT_NUMBER_SPEED)) }
    var dynamicStroke by remember { mutableStateOf(sp.getInt(AntiRecallSettings.KEY_DYNAMIC_STROKE,
        t.mma.hooks.items.dynamicbadge.DynamicBadgeSettings.DEFAULT_NUMBER_STROKE_WIDTH)) }
    val usesTimeVariable = noticeText.contains(AntiRecallSettings.VAR_SEND_TIME) ||
        noticeText.contains(AntiRecallSettings.VAR_RECALL_TIME)
    val actionBarDensity = LocalDensity.current
    var actionBarHeight by remember { mutableStateOf(0.dp) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            Box(Modifier.onSizeChanged {
                actionBarHeight = with(actionBarDensity) { it.height.toDp() }
            }) {
                BottomActionBar(
                    primaryText = "保存设置",
                    onPrimaryClick = save@{
                        val normalizedTimeFormat = noticeTimeFormat.trim()
                            .ifBlank { AntiRecallSettings.DEFAULT_NOTICE_TIME_FORMAT }
                        val timeFormatValid = runCatching {
                            SimpleDateFormat(normalizedTimeFormat, Locale.getDefault()).format(Date())
                        }.isSuccess
                        if (usesTimeVariable && !timeFormatValid) {
                            Toast.makeText(context, "时间格式无效", Toast.LENGTH_SHORT).show()
                            return@save
                        }
                        sp.edit()
                            .putBoolean(AntiRecallSettings.KEY_DYNAMIC_ENABLE, dynamicRecall)
                            .putString(AntiRecallSettings.KEY_DYNAMIC_COLOR, dynamicColor)
                            .putBoolean(AntiRecallSettings.KEY_DYNAMIC_MESSAGE_ENABLE, recallMessageEnabled)
                            .putString(AntiRecallSettings.KEY_DYNAMIC_MESSAGE_COLOR,
                                t.mma.hooks.items.antirecall.DynamicRecallTint.normalize(recallMessageColor))
                            .putInt(AntiRecallSettings.KEY_DYNAMIC_MESSAGE_OPACITY, recallMessageOpacity)
                            .putInt(AntiRecallSettings.KEY_DYNAMIC_SPEED, dynamicSpeed)
                            .putInt(AntiRecallSettings.KEY_DYNAMIC_STROKE, dynamicStroke)
                            .putString(
                                AntiRecallSettings.KEY_NOTICE_TEXT,
                                noticeText.ifBlank { AntiRecallSettings.DEFAULT_NOTICE_TEXT }
                            )
                            .putString(
                                AntiRecallSettings.KEY_NOTICE_TIME_FORMAT,
                                if (timeFormatValid) {
                                    normalizedTimeFormat
                                } else {
                                    AntiRecallSettings.DEFAULT_NOTICE_TIME_FORMAT
                                }
                            )
                            .apply()
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                    },
                    secondaryText = "返回",
                    onSecondaryClick = onBack
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + maxOf(actionBarHeight, 64.dp) + 20.dp
            )
        ) {
            item { SmallTitle(text = "防撤回") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        AntiRecallSettings.KEY_ENABLE,
                        "防撤回",
                        "保留被撤回的消息，并在下方插入提示",
                        AntiRecallSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        AntiRecallSettings.KEY_KEEP_SELF_RECALL,
                        "保留自己撤回",
                        "开启后自己撤回的消息也会保留",
                        AntiRecallSettings.DEFAULT_KEEP_SELF_RECALL
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        AntiRecallSettings.KEY_SHOW_NOTICE,
                        "显示撤回提示",
                        "在被撤回消息下方插入提示",
                        AntiRecallSettings.DEFAULT_SHOW_NOTICE
                    )
                    InsetDivider()
                    VariableInputRow(
                        title = "提示文案",
                        summary = "点击下方变量插入到光标位置",
                        value = noticeText,
                        variables = antiRecallTemplateVariables,
                        onValueChange = { noticeText = it }
                    )
                    if (usesTimeVariable) {
                        InsetDivider()
                        InputRow(
                            "时间格式",
                            "使用日期格式，例如 yyyy-MM-dd HH:mm:ss",
                            noticeTimeFormat
                        ) { noticeTimeFormat = it }
                    }
                }
            }
            item { SmallTitle(text = "动态撤回") }
            item {
                SwitchRow(dynamicRecall, "动态撤回", "为防撤回保留的原消息显示旋转渐变描边，保存后生效",
                    onCheckedChange = { dynamicRecall = it })
            }
            item {
                SwitchRow(recallMessageEnabled, "撤回信息颜色", "独立控制颜色覆盖和红色撤回标记，保存后生效",
                    onCheckedChange = { recallMessageEnabled = it })
            }
            if (recallMessageEnabled) {
                item {
                    ColorPickerRow("颜色编辑", "覆盖保留消息内容，包含图片、视频、卡片和静态／动态表情", recallMessageColor,
                        allowGradient = false,
                        onReset = { recallMessageColor = t.mma.hooks.items.antirecall.DynamicRecallTint.DEFAULT_COLOR }
                    ) { recallMessageColor = it }
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("颜色覆盖强度 $recallMessageOpacity%", color = MiuixTheme.colorScheme.onSurface)
                        Slider(value = recallMessageOpacity.toFloat(),
                            onValueChange = { recallMessageOpacity = it.toInt().coerceIn(0, 80) },
                            valueRange = 0f..80f, steps = 15,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text("0%不覆盖颜色，仍显示撤回标记；默认35%，保存后生效", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
            if (dynamicRecall) {
                item {
                    ColorPickerRow("渐变颜色", "断点描边三色渐变", dynamicColor,
                        threeColorGradient = true,
                        onReset = { dynamicColor = t.mma.hooks.items.antirecall.DynamicRecallGradient.DEFAULT }
                    ) { dynamicColor = it.take(29) }
                }
                item { speedControl("描边旋转周期", dynamicSpeed) { dynamicSpeed = it } }
                item { strokeWidthControl("描边宽度", dynamicStroke) { dynamicStroke = it } }
            }
        }
    }
}

@Composable
internal fun CrashReportMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, CrashReportSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(CrashReportSettings.KEY_ENABLE, CrashReportSettings.DEFAULT_ENABLE)
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
            item { SmallTitle(text = "调试") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "捕获异常日志",
                        summary = if (enabled) {
                            "记录 Java、Native 和 ANR 异常，并在下次启动时显示日志"
                        } else {
                            "关闭后不再捕获或弹出异常日志"
                        }
                    ) { checked ->
                        if (sp.edit().putBoolean(CrashReportSettings.KEY_ENABLE, checked).commit()) {
                            enabled = checked
                            CrashReportRuntime.onSettingChanged(context, checked)
                        } else {
                            Toast.makeText(context, "保存设置失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProtobufPacketMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, ProtobufPacketSettings.PREFS_NAME) }
    var blockTypes by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_BLOCK_TYPES,
            ProtobufPacketSettings.DEFAULT_BLOCK_TYPES
        ) ?: ProtobufPacketSettings.DEFAULT_BLOCK_TYPES)
    }
    var uri by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_URI,
            ProtobufPacketSettings.DEFAULT_SEND_URI
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_URI)
    }
    var cgiId by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_TYPE,
            ProtobufPacketSettings.DEFAULT_SEND_TYPE
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_TYPE)
    }
    var funcId by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_FUNC_ID,
            ProtobufPacketSettings.DEFAULT_SEND_FUNC_ID
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_FUNC_ID)
    }
    var routeId by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_ROUTE_ID,
            ProtobufPacketSettings.DEFAULT_SEND_ROUTE_ID
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_ROUTE_ID)
    }
    var payload by remember {
        mutableStateOf(sp.getString(
            ProtobufPacketSettings.KEY_SEND_PAYLOAD,
            ProtobufPacketSettings.DEFAULT_SEND_PAYLOAD
        ) ?: ProtobufPacketSettings.DEFAULT_SEND_PAYLOAD)
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
                    sp.edit()
                        .putString(ProtobufPacketSettings.KEY_BLOCK_TYPES, blockTypes)
                        .putString(ProtobufPacketSettings.KEY_SEND_URI, uri.trim())
                        .putString(ProtobufPacketSettings.KEY_SEND_TYPE, cgiId.trim())
                        .putString(ProtobufPacketSettings.KEY_SEND_FUNC_ID, funcId.trim())
                        .putString(ProtobufPacketSettings.KEY_SEND_ROUTE_ID, routeId.trim())
                        .putString(ProtobufPacketSettings.KEY_SEND_PAYLOAD, payload)
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
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "抓包") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        ProtobufPacketSettings.KEY_ENABLE,
                        "Protobuf 抓包",
                        "开启后输出请求和响应到 LSPosed 日志",
                        ProtobufPacketSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        ProtobufPacketSettings.KEY_CAPTURE_REQUEST,
                        "抓请求",
                        "记录请求 URI、Type 和 PB JSON",
                        ProtobufPacketSettings.DEFAULT_CAPTURE_REQUEST
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        ProtobufPacketSettings.KEY_CAPTURE_RESPONSE,
                        "抓响应",
                        "记录响应 URI、Type 和 PB JSON",
                        ProtobufPacketSettings.DEFAULT_CAPTURE_RESPONSE
                    )
                    InsetDivider()
                    InputRow(
                        "过滤 Type",
                        "多个 Type 用逗号或空格分隔",
                        blockTypes,
                        minLines = 3
                    ) { blockTypes = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "自定义发包") }
            item {
                SettingsCard {
                    InputRow("URI", "例如 /cgi-bin/micromsg-bin/oplog", uri) { uri = it }
                    InsetDivider()
                    InputRow("Type", "CGI ID，例如 681", cgiId) { cgiId = it.filter { ch -> ch.isDigit() } }
                    InsetDivider()
                    InputRow("FuncId", "默认 0", funcId) { funcId = it.filter { ch -> ch.isDigit() } }
                    InsetDivider()
                    InputRow("RouteId", "默认 0", routeId) { routeId = it.filter { ch -> ch.isDigit() } }
                    InsetDivider()
                    InputRow(
                        "JSON 载荷",
                        "字段号作为 key；字符串按 UTF-8，hex-> 表示原始字节",
                        payload,
                        minLines = 8
                    ) { payload = it }
                    InsetDivider()
                    ActionRow("发送", "优先通用发包，未就绪时使用同类请求编辑重放") {
                        val type = cgiId.toIntOrNull()
                        val func = funcId.toIntOrNull() ?: 0
                        val route = routeId.toIntOrNull() ?: 0
                        if (type == null) {
                            Toast.makeText(context, "Type 必须是数字", Toast.LENGTH_SHORT).show()
                            return@ActionRow
                        }
                        ProtobufPacketRuntime.send(uri.trim(), type, func, route, payload) { success, message ->
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScriptPluginMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit,
    onOpenMarket: () -> Unit = {},
    onOpenAgent: () -> Unit = {},
    onOpenManager: () -> Unit = {}
) {
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
            item { SmallTitle(text = "脚本插件") }
            item {
                ScriptPluginSettingsMiuixContent.ScriptPluginSettingsContent(
                    context,
                    {},
                    onOpenMarket,
                    onOpenAgent,
                    onOpenManager
                )
            }
        }
    }
}

internal object PluginMarketUi {

internal enum class PluginMarketUploadPhase {
    QUEUED,
    UPLOADING,
    SUCCESS,
    PENDING_REVIEW,
    FAILED
}

internal data class PluginMarketUploadStatus(
    val phase: PluginMarketUploadPhase,
    val message: String
)

@Composable
fun ScriptPluginMarketPage(
    context: Context,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("latest") }
    var refreshVersion by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf("") }
    var plugins by remember { mutableStateOf<List<PluginMarketPlugin>>(emptyList()) }
    var pluginCount by remember { mutableStateOf(0) }
    var selectedPlugin by remember { mutableStateOf<PluginMarketPlugin?>(null) }
    var detailPlugin by remember { mutableStateOf<PluginMarketPlugin?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf("") }
    var detailRetryVersion by remember { mutableStateOf(0) }
    var historyVersions by remember { mutableStateOf<List<PluginMarketHistoryVersion>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf("") }
    var historyInstallVersionId by remember { mutableStateOf<String?>(null) }
    var comments by remember { mutableStateOf<List<PluginMarketComment>>(emptyList()) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentsError by remember { mutableStateOf("") }
    var commentsRetryVersion by remember { mutableStateOf(0) }
    var currentUserIdentity by remember { mutableStateOf<PluginMarketUserIdentity?>(null) }
    var interactionError by remember { mutableStateOf("") }
    var likedByCurrentUser by remember { mutableStateOf(false) }
    var likeCountLoaded by remember { mutableStateOf(false) }
    var commentCountLoaded by remember { mutableStateOf(false) }
    var updatingLike by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    var submittingComment by remember { mutableStateOf(false) }
    var deletingCommentId by remember { mutableStateOf<String?>(null) }
    var detailTargetExists by remember { mutableStateOf(false) }
    var installingRemoteId by remember { mutableStateOf<String?>(null) }
    var deletingRemoteId by remember { mutableStateOf<String?>(null) }
    var installMessage by remember { mutableStateOf("") }
    var pendingOverwrite by remember { mutableStateOf<PluginMarketPlugin?>(null) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf<List<PluginMarketNotification>>(emptyList()) }
    var notificationUnreadCount by remember { mutableStateOf(0L) }
    var notificationsLoading by remember { mutableStateOf(true) }
    var notificationsError by remember { mutableStateOf("") }
    var notificationRefreshVersion by remember { mutableStateOf(0) }
    var showNotifications by remember { mutableStateOf(false) }

    fun updateSocialCounts(
        remotePluginId: String,
        likeCount: Long? = null,
        commentCount: Long? = null
    ) {
        fun updated(plugin: PluginMarketPlugin): PluginMarketPlugin = plugin.copy(
            likeCount = likeCount ?: plugin.likeCount,
            commentCount = commentCount ?: plugin.commentCount
        )
        detailPlugin = detailPlugin?.let { if (it.remotePluginId == remotePluginId) updated(it) else it }
        selectedPlugin = selectedPlugin?.let { if (it.remotePluginId == remotePluginId) updated(it) else it }
        plugins = plugins.map { if (it.remotePluginId == remotePluginId) updated(it) else it }
    }

    LaunchedEffect(query, sort, refreshVersion) {
        delay(250L)
        loading = true
        loadError = ""
        val result = withContext(Dispatchers.IO) {
            PluginMarketRepository.list(
                context = context,
                query = query.trim(),
                sort = sort,
                limit = 100
            )
        }
        result.fold(
            onSuccess = { page ->
                plugins = page.items
                pluginCount = page.count
            },
            onFailure = { error ->
                loadError = pluginMarketErrorMessage(error)
                if (plugins.isEmpty()) pluginCount = 0
            }
        )
        loading = false
    }

    LaunchedEffect(notificationRefreshVersion) {
        notificationsLoading = true
        notificationsError = ""
        val result = withContext(Dispatchers.IO) {
            PluginMarketRepository.notifications(context, limit = 100)
        }
        result.fold(
            onSuccess = { page ->
                notifications = page.items
                notificationUnreadCount = page.unreadCount
            },
            onFailure = { error ->
                notificationsError = pluginMarketErrorMessage(error)
                if (notifications.isEmpty()) notificationUnreadCount = 0L
            }
        )
        notificationsLoading = false
    }

    LaunchedEffect(showNotifications, notificationsLoading) {
        if (!showNotifications || notificationsLoading) return@LaunchedEffect
        val unreadIds = notifications.filterNot { it.read }.map { it.notificationId }
        if (unreadIds.isEmpty()) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            PluginMarketRepository.markNotificationsRead(context, unreadIds)
        }
        result.onSuccess { readResult ->
            notificationUnreadCount = readResult.unreadCount
            val markedIds = unreadIds.toHashSet()
            notifications = notifications.map { item ->
                if (item.notificationId in markedIds) item.copy(read = true) else item
            }
        }
    }

    LaunchedEffect(selectedPlugin?.remotePluginId, detailRetryVersion) {
        val selected = selectedPlugin ?: return@LaunchedEffect
        detailLoading = true
        detailError = ""
        detailPlugin = null
        historyLoading = true
        historyError = ""
        historyVersions = emptyList()
        detailTargetExists = false
        installMessage = ""
        val (detailResult, historyResult) = withContext(Dispatchers.IO) {
            val loadedDetail = PluginMarketRepository.detail(context, selected.remotePluginId).map { detail ->
                val exists = PluginMarketInstaller.existingLocalPluginId(context, detail) != null
                detail to exists
            }
            loadedDetail to PluginMarketRepository.history(context, selected.remotePluginId)
        }
        detailResult.fold(
            onSuccess = { (detail, exists) ->
                val currentSummary = selectedPlugin?.takeIf {
                    it.remotePluginId == selected.remotePluginId
                }
                detailPlugin = detail.copy(
                    likeCount = if (likeCountLoaded) {
                        currentSummary?.likeCount ?: detail.likeCount
                    } else {
                        detail.likeCount
                    },
                    commentCount = if (commentCountLoaded) {
                        currentSummary?.commentCount ?: detail.commentCount
                    } else {
                        detail.commentCount
                    }
                )
                detailTargetExists = exists
            },
            onFailure = { error ->
                detailError = pluginMarketErrorMessage(error)
                detailTargetExists = false
            }
        )
        historyResult.fold(
            onSuccess = { historyVersions = it },
            onFailure = { historyError = pluginMarketErrorMessage(it) }
        )
        detailLoading = false
        historyLoading = false
    }

    LaunchedEffect(selectedPlugin?.remotePluginId, commentsRetryVersion) {
        val selected = selectedPlugin ?: return@LaunchedEffect
        commentsLoading = true
        commentsError = ""
        interactionError = ""
        comments = emptyList()
        currentUserIdentity = null
        likedByCurrentUser = false
        likeCountLoaded = false
        commentCountLoaded = false
        val (identityResult, commentsResult, likedResult) = withContext(Dispatchers.IO) {
            val identity = PluginMarketRepository.currentUserIdentity(context)
            val liked = if (identity.isSuccess) {
                PluginMarketRepository.likeStatus(context, selected.remotePluginId)
            } else {
                Result.failure(identity.exceptionOrNull() ?: IllegalStateException("当前微信账号资料未就绪"))
            }
            Triple(
                identity,
                PluginMarketRepository.comments(context, selected.remotePluginId),
                liked
            )
        }
        identityResult.fold(
            onSuccess = { currentUserIdentity = it },
            onFailure = { interactionError = pluginMarketErrorMessage(it) }
        )
        commentsResult.fold(
            onSuccess = { page ->
                comments = page.items
                commentCountLoaded = true
                updateSocialCounts(selected.remotePluginId, commentCount = page.total)
            },
            onFailure = { commentsError = pluginMarketErrorMessage(it) }
        )
        likedResult.fold(
            onSuccess = { likeResult ->
                likedByCurrentUser = likeResult.liked
                likeCountLoaded = true
                updateSocialCounts(selected.remotePluginId, likeCount = likeResult.likeCount)
            },
            onFailure = {
                if (interactionError.isBlank()) interactionError = pluginMarketErrorMessage(it)
            }
        )
        commentsLoading = false
    }

    fun performInstall(plugin: PluginMarketPlugin, overwrite: Boolean) {
        if (installingRemoteId != null) return
        installingRemoteId = plugin.remotePluginId
        installMessage = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PluginMarketRepository.install(context, plugin, overwrite)
            }
            result.fold(
                onSuccess = { installed ->
                    detailTargetExists = true
                    installed.downloadCount?.let { count ->
                        detailPlugin = detailPlugin?.copy(downloadCount = count)
                        selectedPlugin = selectedPlugin?.copy(downloadCount = count)
                        plugins = plugins.map { item ->
                            if (item.remotePluginId == plugin.remotePluginId) {
                                item.copy(downloadCount = count)
                            } else {
                                item
                            }
                        }
                    }
                    installMessage = if (installed.replacedExisting) {
                        "更新成功，插件已保持禁用"
                    } else {
                        "安装成功，插件默认禁用"
                    }
                    Toast.makeText(context, installMessage, Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    installMessage = "安装失败: ${pluginMarketErrorMessage(error)}"
                }
            )
            installingRemoteId = null
        }
    }

    fun requestInstall(plugin: PluginMarketPlugin) {
        if (installingRemoteId != null) return
        installingRemoteId = plugin.remotePluginId
        installMessage = ""
        scope.launch {
            val exists = withContext(Dispatchers.IO) {
                PluginMarketInstaller.existingLocalPluginId(context, plugin) != null
            }
            installingRemoteId = null
            if (exists) {
                pendingOverwrite = plugin
            } else {
                performInstall(plugin, overwrite = false)
            }
        }
    }

    fun requestHistoryInstall(version: PluginMarketHistoryVersion) {
        val selected = selectedPlugin ?: return
        if (historyInstallVersionId != null || installingRemoteId != null) return
        historyInstallVersionId = version.versionId
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PluginMarketRepository.historyDetail(
                    context,
                    selected.remotePluginId,
                    version.versionId
                )
            }
            historyInstallVersionId = null
            result.fold(
                onSuccess = ::requestInstall,
                onFailure = {
                    Toast.makeText(
                        context,
                        "读取历史版本失败: ${pluginMarketErrorMessage(it)}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun deleteOwnedPlugin(plugin: PluginMarketPlugin) {
        if (deletingRemoteId != null || installingRemoteId != null) return
        deletingRemoteId = plugin.remotePluginId
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PluginMarketRepository.deleteOwnedRemote(context, plugin.remotePluginId)
            }
            deletingRemoteId = null
            result.fold(
                onSuccess = {
                    selectedPlugin = null
                    detailPlugin = null
                    historyVersions = emptyList()
                    plugins = plugins.filterNot { it.remotePluginId == plugin.remotePluginId }
                    pluginCount = (pluginCount - 1).coerceAtLeast(0)
                    refreshVersion++
                    Toast.makeText(context, "线上插件已删除，本地插件不受影响", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        "删除失败: ${pluginMarketErrorMessage(error)}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun togglePluginLike(plugin: PluginMarketPlugin) {
        if (updatingLike) return
        if (currentUserIdentity == null) {
            Toast.makeText(
                context,
                interactionError.ifBlank { "当前微信账号资料尚未就绪" },
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val removeLike = likedByCurrentUser
        updatingLike = true
        interactionError = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (removeLike) {
                    PluginMarketRepository.unlike(context, plugin.remotePluginId)
                } else {
                    PluginMarketRepository.like(context, plugin.remotePluginId)
                }
            }
            result.fold(
                onSuccess = { likeResult ->
                    likedByCurrentUser = likeResult.liked
                    likeCountLoaded = true
                    updateSocialCounts(plugin.remotePluginId, likeCount = likeResult.likeCount)
                },
                onFailure = { error ->
                    interactionError = pluginMarketErrorMessage(error)
                    Toast.makeText(context, "操作失败: $interactionError", Toast.LENGTH_LONG).show()
                }
            )
            updatingLike = false
        }
    }

    fun submitPluginComment(plugin: PluginMarketPlugin) {
        if (submittingComment) return
        val content = commentDraft.trim()
        if (content.isBlank()) {
            Toast.makeText(context, "评论内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentUserIdentity == null) {
            Toast.makeText(
                context,
                interactionError.ifBlank { "当前微信账号资料尚未就绪" },
                Toast.LENGTH_LONG
            ).show()
            return
        }
        submittingComment = true
        interactionError = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PluginMarketRepository.addComment(context, plugin.remotePluginId, content)
            }
            result.fold(
                onSuccess = { mutation ->
                    mutation.comment?.let { added ->
                        comments = (listOf(added) + comments.filterNot { it.commentId == added.commentId })
                    }
                    commentDraft = ""
                    commentCountLoaded = true
                    updateSocialCounts(plugin.remotePluginId, commentCount = mutation.commentCount)
                },
                onFailure = { error ->
                    interactionError = pluginMarketErrorMessage(error)
                    Toast.makeText(context, "评论失败: $interactionError", Toast.LENGTH_LONG).show()
                }
            )
            submittingComment = false
        }
    }

    fun submitPluginReply(
        plugin: PluginMarketPlugin,
        parentComment: PluginMarketComment,
        content: String,
        onCompleted: (Boolean) -> Unit
    ) {
        if (submittingComment) {
            onCompleted(false)
            return
        }
        if (currentUserIdentity == null) {
            Toast.makeText(
                context,
                interactionError.ifBlank { "当前微信账号资料尚未就绪" },
                Toast.LENGTH_LONG
            ).show()
            onCompleted(false)
            return
        }
        submittingComment = true
        interactionError = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PluginMarketRepository.replyComment(
                    context,
                    plugin.remotePluginId,
                    parentComment,
                    content
                )
            }
            var succeeded = false
            result.fold(
                onSuccess = { mutation ->
                    mutation.comment?.let { added ->
                        comments = (listOf(added) + comments.filterNot {
                            it.commentId == added.commentId
                        })
                    }
                    commentCountLoaded = true
                    updateSocialCounts(plugin.remotePluginId, commentCount = mutation.commentCount)
                    succeeded = true
                },
                onFailure = { error ->
                    interactionError = pluginMarketErrorMessage(error)
                    Toast.makeText(context, "回复失败: $interactionError", Toast.LENGTH_LONG).show()
                }
            )
            submittingComment = false
            onCompleted(succeeded)
        }
    }

    fun openNotifications() {
        showNotifications = true
        notificationRefreshVersion++
    }

    fun deletePluginComment(plugin: PluginMarketPlugin, comment: PluginMarketComment) {
        if (deletingCommentId != null) return
        if (!comment.canDelete) {
            Toast.makeText(context, "只能删除自己的评论", Toast.LENGTH_SHORT).show()
            return
        }
        deletingCommentId = comment.commentId
        interactionError = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PluginMarketRepository.deleteOwnComment(context, plugin.remotePluginId, comment)
            }
            result.fold(
                onSuccess = { mutation ->
                    comments = comments.filterNot { it.commentId == comment.commentId }
                    commentCountLoaded = true
                    updateSocialCounts(plugin.remotePluginId, commentCount = mutation.commentCount)
                    if (mutation.commentCount != comments.size.toLong()) commentsRetryVersion++
                },
                onFailure = { error ->
                    interactionError = pluginMarketErrorMessage(error)
                    Toast.makeText(context, "删除评论失败: $interactionError", Toast.LENGTH_LONG).show()
                }
            )
            deletingCommentId = null
        }
    }

    PageScaffold(
        title = "在线插件",
        largeTitle = "在线插件",
        scrollBehavior = scrollBehavior,
        topBarActions = {
            PluginMarketNotificationBell(
                unreadCount = notificationUnreadCount,
                onClick = ::openNotifications
            )
        },
        bottomBar = {
            BottomActionBar(
                primaryText = "上传本地插件",
                onPrimaryClick = { showUploadDialog = true },
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
            item {
                SearchBarSurface(
                    query = query,
                    placeholder = "搜索插件、作者或目录名",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onQueryChange = { query = it }
                )
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "浏览") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "排序方式",
                        summary = if (sort == "downloads") "热门（按下载量）" else "最新发布",
                        options = listOf(
                            PopupChoice("最新", "latest"),
                            PopupChoice("热门", "downloads")
                        ),
                        currentValue = sort,
                        onValueChanged = { sort = it }
                    )
                    InsetDivider()
                    ActionRow(
                        title = "刷新",
                        summary = if (loading) "正在加载在线插件" else "重新获取当前列表"
                    ) {
                        refreshVersion++
                    }
                }
            }
            item {
                SettingsCard(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "社区插件由用户上传，安装前请核对作者、说明和文件内容。下载后的插件默认禁用。",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
            item {
                SmallTitle(
                    modifier = Modifier.padding(top = 10.dp),
                    text = "在线插件(${if (loading && plugins.isEmpty()) "-" else pluginCount})"
                )
            }
            if (loadError.isNotBlank()) {
                item {
                    SettingsCard {
                        Column {
                            EmptyText(loadError)
                            TextButton(
                                text = "重试",
                                onClick = { refreshVersion++ },
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    }
                }
            } else if (loading && plugins.isEmpty()) {
                item { SettingsCard { EmptyText("正在加载在线插件...") } }
            } else if (plugins.isEmpty()) {
                item {
                    SettingsCard {
                        EmptyText(if (query.isBlank()) "暂无在线插件" else "没有匹配的在线插件")
                    }
                }
            } else {
                item {
                    SettingsCard {
                        plugins.forEachIndexed { index, plugin ->
                            PluginMarketListRow(plugin) {
                                selectedPlugin = plugin
                            }
                            if (index != plugins.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }

    selectedPlugin?.let { summary ->
        PluginMarketDetailDialog(
            summary = summary,
            detail = detailPlugin,
            loading = detailLoading,
            error = detailError,
            historyVersions = historyVersions,
            historyLoading = historyLoading,
            historyError = historyError,
            historyInstallVersionId = historyInstallVersionId,
            installed = detailTargetExists,
            installing = installingRemoteId == summary.remotePluginId,
            deleting = deletingRemoteId == summary.remotePluginId,
            owned = PluginMarketRepository.ownsRemotePlugin(context, summary.remotePluginId),
            installMessage = installMessage,
            likedByCurrentUser = likedByCurrentUser,
            updatingLike = updatingLike,
            comments = comments,
            commentsLoading = commentsLoading,
            commentsError = commentsError,
            currentUserWxId = currentUserIdentity?.wxId.orEmpty(),
            interactionError = interactionError,
            commentDraft = commentDraft,
            submittingComment = submittingComment,
            deletingCommentId = deletingCommentId,
            onRetry = { detailRetryVersion++ },
            onRetryComments = { commentsRetryVersion++ },
            onInstall = ::requestInstall,
            onInstallHistory = ::requestHistoryInstall,
            onToggleLike = { togglePluginLike(summary) },
            onCommentDraftChanged = { value -> commentDraft = value.take(1000) },
            onSubmitComment = { submitPluginComment(summary) },
            onDeleteComment = { comment -> deletePluginComment(summary, comment) },
            onSubmitReply = { comment, content, onCompleted ->
                submitPluginReply(summary, comment, content, onCompleted)
            },
            onDelete = { deleteOwnedPlugin(summary) },
            onDismiss = {
                if (
                    installingRemoteId == null &&
                    deletingRemoteId == null &&
                    !updatingLike &&
                    !submittingComment &&
                    deletingCommentId == null
                ) {
                    selectedPlugin = null
                    detailPlugin = null
                    detailError = ""
                    historyVersions = emptyList()
                    historyError = ""
                    comments = emptyList()
                    commentsError = ""
                    currentUserIdentity = null
                    interactionError = ""
                    commentDraft = ""
                    installMessage = ""
                }
            }
        )
    }

    pendingOverwrite?.let { plugin ->
        WindowDialog(
            show = true,
            title = "覆盖本地插件",
            onDismissRequest = { pendingOverwrite = null },
            content = {
                Column {
                    Text(
                        text = "本地已存在目录“${PluginMarketInstaller.defaultLocalPluginId(plugin)}”。更新会覆盖 main.java、main.java.bshs、info.prop 和 README.md，并保留其它本地配置文件。更新后插件保持禁用。",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { pendingOverwrite = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        TextButton(
                            text = "确认覆盖",
                            onClick = {
                                pendingOverwrite = null
                                performInstall(plugin, overwrite = true)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        )
    }

    if (showUploadDialog) {
        PluginMarketUploadDialog(
            context = context,
            onDismiss = { uploaded ->
                showUploadDialog = false
                if (uploaded) refreshVersion++
            }
        )
    }

    if (showNotifications) {
        PluginMarketNotificationDialog(
            notifications = notifications,
            loading = notificationsLoading,
            error = notificationsError,
            onRetry = { notificationRefreshVersion++ },
            onNotificationClick = { notification ->
                showNotifications = false
                val target = plugins.firstOrNull {
                    it.remotePluginId == notification.remotePluginId
                } ?: PluginMarketPlugin(
                    remotePluginId = notification.remotePluginId,
                    sourcePluginId = "",
                    name = notification.pluginName,
                    author = "",
                    version = "",
                    versionId = "",
                    updateTime = "",
                    downloadCount = 0L,
                    likeCount = 0L,
                    commentCount = 0L,
                    description = "",
                    files = emptyList()
                )
                val activity = context as? Activity
                    ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
                if (activity == null) {
                    selectedPlugin = target
                } else {
                    activity.window?.decorView?.postOnAnimation {
                        if (!activity.isFinishing && !activity.isDestroyed) selectedPlugin = target
                    }
                }
            },
            onDismiss = { showNotifications = false }
        )
    }
}

@Composable
internal fun PluginMarketNotificationBell(
    unreadCount: Long,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(40.dp).responsiveTap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            imageVector = NavIcons.Bell,
            contentDescription = "回复通知",
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
            modifier = Modifier.size(24.dp)
        )
        if (unreadCount > 0L) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .height(16.dp)
                    .sizeIn(minWidth = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFD93025))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99L) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun PluginMarketNotificationDialog(
    notifications: List<PluginMarketNotification>,
    loading: Boolean,
    error: String,
    onRetry: () -> Unit,
    onNotificationClick: (PluginMarketNotification) -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = "回复通知",
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${notifications.size} 条",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp
                    )
                    TextButton(
                        text = if (loading) "正在刷新" else "刷新",
                        onClick = onRetry,
                        enabled = !loading,
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    when {
                        loading && notifications.isEmpty() -> EmptyText("正在加载回复通知...")
                        error.isNotBlank() && notifications.isEmpty() -> {
                            EmptyText(error)
                            TextButton(
                                text = "重试",
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                        notifications.isEmpty() -> EmptyText("暂无回复通知")
                        else -> notifications.forEachIndexed { index, notification ->
                            PluginMarketNotificationRow(
                                notification = notification,
                                onClick = { onNotificationClick(notification) }
                            )
                            if (index != notifications.lastIndex) Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    )
}

@Composable
internal fun PluginMarketNotificationRow(
    notification: PluginMarketNotification,
    onClick: () -> Unit
) {
    var pressed by remember(notification.notificationId) { mutableStateOf(false) }
    val feedbackColor = rememberPressFeedbackColor(pressed)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (pressed) feedbackColor else MiuixTheme.colorScheme.secondaryVariant
            )
            .responsiveTap(onClick = onClick, onPressedChange = { pressed = it })
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "${notification.actorNickname.ifBlank { "微信用户" }} 回复了你的评论",
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = notification.pluginName.ifBlank { notification.remotePluginId },
            color = MiuixTheme.colorScheme.primary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
        Text(
            text = notification.content,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
        )
        Text(
            text = "原评论：${notification.originalContent}",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
        )
        Text(
            text = formatPluginMarketTime(notification.createdAt),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
internal fun PluginMarketListRow(
    plugin: PluginMarketPlugin,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(onClick = onClick, onPressedChange = { pressed = it })
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.name.ifBlank { plugin.remotePluginId },
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append("作者: ").append(plugin.author.ifBlank { "未知" })
                    append(" | 版本: ").append(plugin.version.ifBlank { "未知" })
                    append(" | 下载: ").append(plugin.downloadCount)
                    append("\n点赞: ").append(plugin.likeCount)
                    append(" | 评论: ").append(plugin.commentCount)
                    append("\n更新: ").append(formatPluginMarketTime(plugin.updateTime))
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "›",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 22.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
internal fun PluginMarketDetailDialog(
    summary: PluginMarketPlugin,
    detail: PluginMarketPlugin?,
    loading: Boolean,
    error: String,
    historyVersions: List<PluginMarketHistoryVersion>,
    historyLoading: Boolean,
    historyError: String,
    historyInstallVersionId: String?,
    installed: Boolean,
    installing: Boolean,
    deleting: Boolean,
    owned: Boolean,
    installMessage: String,
    likedByCurrentUser: Boolean,
    updatingLike: Boolean,
    comments: List<PluginMarketComment>,
    commentsLoading: Boolean,
    commentsError: String,
    currentUserWxId: String,
    interactionError: String,
    commentDraft: String,
    submittingComment: Boolean,
    deletingCommentId: String?,
    onRetry: () -> Unit,
    onRetryComments: () -> Unit,
    onInstall: (PluginMarketPlugin) -> Unit,
    onInstallHistory: (PluginMarketHistoryVersion) -> Unit,
    onToggleLike: () -> Unit,
    onCommentDraftChanged: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onDeleteComment: (PluginMarketComment) -> Unit,
    onSubmitReply: (PluginMarketComment, String, (Boolean) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var confirmDelete by remember(summary.remotePluginId) { mutableStateOf(false) }
    var pendingDeleteComment by remember(summary.remotePluginId) {
        mutableStateOf<PluginMarketComment?>(null)
    }
    var replyTarget by remember(summary.remotePluginId) {
        mutableStateOf<PluginMarketComment?>(null)
    }
    var replyDraft by remember(summary.remotePluginId) { mutableStateOf("") }
    WindowDialog(
        show = true,
        title = summary.name.ifBlank { "插件详情" },
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).imePadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    val shownPlugin = detail ?: summary
                    PluginMarketDialogInfoRow("作者", shownPlugin.author.ifBlank { "未知" })
                    PluginMarketDialogInfoRow("版本", shownPlugin.version.ifBlank { "未知" })
                    PluginMarketDialogInfoRow("更新时间", formatPluginMarketTime(shownPlugin.updateTime))
                    PluginMarketDialogInfoRow("下载量", shownPlugin.downloadCount.toString())
                    PluginMarketDialogInfoRow("点赞数", shownPlugin.likeCount.toString())
                    PluginMarketDialogInfoRow("评论数", shownPlugin.commentCount.toString())
                    PluginMarketDialogInfoRow("插件 ID", summary.remotePluginId)
                    if (detail?.description?.isNotBlank() == true) {
                        Text(
                            text = detail.description,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                    }
                    if (detail != null) {
                        Text(
                            text = "包含文件",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = detail.files.joinToString("、") { it.name }.ifBlank { "未知" },
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        detail.files.firstOrNull { it.name.equals("README.md", ignoreCase = true) }
                            ?.content?.takeIf { it.isNotBlank() }
                            ?.let { readme ->
                                Text(
                                    text = "插件说明",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    MarkdownUi.Content(
                                        context = context,
                                        markdown = readme,
                                        contentPadding = PaddingValues(0.dp)
                                    )
                                }
                            }
                    }
                    PluginMarketSocialSection(
                        likeCount = shownPlugin.likeCount,
                        commentCount = shownPlugin.commentCount,
                        likedByCurrentUser = likedByCurrentUser,
                        updatingLike = updatingLike,
                        comments = comments,
                        commentsLoading = commentsLoading,
                        commentsError = commentsError,
                        currentUserWxId = currentUserWxId,
                        interactionError = interactionError,
                        commentDraft = commentDraft,
                        submittingComment = submittingComment,
                        deletingCommentId = deletingCommentId,
                        enabled = !installing && !deleting,
                        onToggleLike = onToggleLike,
                        onCommentDraftChanged = onCommentDraftChanged,
                        onSubmitComment = onSubmitComment,
                        onRetryComments = onRetryComments,
                        onDeleteComment = { pendingDeleteComment = it },
                        onReplyComment = { comment ->
                            replyTarget = comment
                            replyDraft = ""
                        }
                    )
                    PluginMarketHistoryUi.HistoryList(
                        versions = historyVersions,
                        loading = historyLoading,
                        error = historyError,
                        installingVersionId = historyInstallVersionId,
                        enabled = !installing,
                        onRetry = onRetry,
                        onInstall = onInstallHistory
                    )
                    Text(
                        text = "社区插件可能执行敏感操作，请确认来源可信。安装或更新完成后插件保持禁用。",
                        color = Color(0xFFD93025),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                    )
                    when {
                        loading -> EmptyText("正在加载插件详情...")
                        error.isNotBlank() -> {
                            EmptyText(error)
                            TextButton(
                                text = "重新加载",
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    }
                    if (installMessage.isNotBlank()) {
                        Text(
                            text = installMessage,
                            color = if (installMessage.startsWith("安装失败")) {
                                Color(0xFFD93025)
                            } else {
                                MiuixTheme.colorScheme.primary
                            },
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                val activeReply = replyTarget
                if (activeReply != null) {
                    val focusRequester = remember(activeReply.commentId) { FocusRequester() }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    LaunchedEffect(activeReply.commentId) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.secondaryVariant)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "回复 ${activeReply.userNickname.ifBlank { "微信用户" }}",
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = activeReply.content.replace('\n', ' ').trim(),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MiuixTheme.colorScheme.background)
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        ) {
                            if (replyDraft.isBlank()) {
                                Text(
                                    text = "回复 ${activeReply.userNickname.ifBlank { "微信用户" }}",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = replyDraft,
                                onValueChange = { replyDraft = it.take(1000) },
                                enabled = !submittingComment,
                                minLines = 1,
                                maxLines = 4,
                                textStyle = TextStyle(
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${replyDraft.length}/1000",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 11.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    text = "取消",
                                    onClick = {
                                        replyTarget = null
                                        replyDraft = ""
                                    },
                                    enabled = !submittingComment,
                                    colors = ButtonDefaults.textButtonColorsPrimary()
                                )
                                TextButton(
                                    text = if (submittingComment) "正在回复" else "发送",
                                    onClick = {
                                        val content = replyDraft.trim()
                                        if (content.isNotBlank()) {
                                            onSubmitReply(activeReply, content) { succeeded ->
                                                if (succeeded) {
                                                    replyTarget = null
                                                    replyDraft = ""
                                                }
                                            }
                                        }
                                    },
                                    enabled = replyDraft.isNotBlank() && !submittingComment,
                                    colors = ButtonDefaults.textButtonColorsPrimary()
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            text = "关闭",
                            onClick = onDismiss,
                            enabled = !installing && !deleting && !updatingLike &&
                                !submittingComment && deletingCommentId == null,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        if (owned) {
                            TextButton(
                                text = if (deleting) "正在删除" else "删除",
                                onClick = { confirmDelete = true },
                                enabled = !installing && !deleting && !updatingLike &&
                                    !submittingComment && deletingCommentId == null,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                        TextButton(
                            text = when {
                                installing -> "正在处理"
                                installed -> "更新"
                                else -> "安装"
                            },
                            onClick = { detail?.let(onInstall) },
                            enabled = detail != null && !loading && error.isBlank() && !installing &&
                                !deleting && !updatingLike && !submittingComment && deletingCommentId == null,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    )
    if (confirmDelete) {
        WindowDialog(
            show = true,
            title = "删除线上插件",
            onDismissRequest = { confirmDelete = false },
            content = {
                Column {
                    Text(
                        text = "确定删除“${summary.name.ifBlank { summary.remotePluginId }}”吗？线上插件及全部历史版本都会被删除，本地插件不会删除。此操作无法撤销。",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { confirmDelete = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        TextButton(
                            text = "确认删除",
                            onClick = {
                                confirmDelete = false
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
    pendingDeleteComment?.let { comment ->
        WindowDialog(
            show = true,
            title = "删除评论",
            onDismissRequest = { pendingDeleteComment = null },
            content = {
                Column {
                    Text(
                        text = "确定删除这条评论吗？此操作无法撤销。",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { pendingDeleteComment = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                        TextButton(
                            text = "确认删除",
                            onClick = {
                                pendingDeleteComment = null
                                onDeleteComment(comment)
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
internal fun PluginMarketSocialSection(
    likeCount: Long,
    commentCount: Long,
    likedByCurrentUser: Boolean,
    updatingLike: Boolean,
    comments: List<PluginMarketComment>,
    commentsLoading: Boolean,
    commentsError: String,
    currentUserWxId: String,
    interactionError: String,
    commentDraft: String,
    submittingComment: Boolean,
    deletingCommentId: String?,
    enabled: Boolean,
    onToggleLike: () -> Unit,
    onCommentDraftChanged: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onRetryComments: () -> Unit,
    onDeleteComment: (PluginMarketComment) -> Unit,
    onReplyComment: (PluginMarketComment) -> Unit
) {
    Text(
        text = "互动",
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 16.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            text = when {
                updatingLike -> "正在处理"
                likedByCurrentUser -> "取消点赞"
                else -> "点赞"
            },
            onClick = onToggleLike,
            enabled = enabled && currentUserWxId.isNotBlank() && !updatingLike,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        Text(
            text = "$likeCount 个赞 · $commentCount 条评论",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
    Text(
        text = "发表评论",
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 10.dp)
    )
    BasicTextField(
        value = commentDraft,
        onValueChange = onCommentDraftChanged,
        enabled = enabled && currentUserWxId.isNotBlank() && !submittingComment,
        minLines = 2,
        maxLines = 4,
        textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.TopStart) {
                if (commentDraft.isBlank()) {
                    Text(
                        text = if (currentUserWxId.isBlank()) "当前微信账号资料尚未就绪" else "填写评论内容",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp
                    )
                }
                innerTextField()
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${commentDraft.length}/1000",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp
        )
        TextButton(
            text = if (submittingComment) "正在发表" else "发表",
            onClick = onSubmitComment,
            enabled = enabled && currentUserWxId.isNotBlank() &&
                commentDraft.isNotBlank() && !submittingComment,
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
    if (interactionError.isNotBlank()) {
        Text(
            text = interactionError,
            color = Color(0xFFD93025),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
    Text(
        text = "评论 ($commentCount)",
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 10.dp)
    )
    val commentThreads = remember(comments) { buildPluginMarketCommentThreads(comments) }
    when {
        commentsLoading -> EmptyText("正在加载评论...")
        commentsError.isNotBlank() -> {
            EmptyText(commentsError)
            TextButton(
                text = "重试",
                onClick = onRetryComments,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
        comments.isEmpty() -> EmptyText("暂无评论")
        else -> commentThreads.forEachIndexed { index, thread ->
            PluginMarketCommentRow(
                comment = thread.root,
                deleting = deletingCommentId == thread.root.commentId,
                enabled = enabled && deletingCommentId == null,
                onDelete = { onDeleteComment(thread.root) },
                onReply = { onReplyComment(thread.root) }
            )
            if (thread.replies.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.secondaryVariant)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    thread.replies.forEachIndexed { replyIndex, reply ->
                        PluginMarketReplyRow(
                            comment = reply,
                            deleting = deletingCommentId == reply.commentId,
                            enabled = enabled && deletingCommentId == null,
                            onDelete = { onDeleteComment(reply) },
                            onReply = { onReplyComment(reply) }
                        )
                        if (replyIndex != thread.replies.lastIndex) InsetDivider()
                    }
                }
            }
            if (index != commentThreads.lastIndex) InsetDivider()
        }
    }
}

@Composable
internal fun PluginMarketCommentRow(
    comment: PluginMarketComment,
    deleting: Boolean,
    enabled: Boolean,
    onDelete: () -> Unit,
    onReply: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled, onClick = onReply)
                .padding(vertical = 7.dp)
        ) {
            Text(
                text = comment.userNickname.ifBlank { "微信用户" },
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatPluginMarketTime(comment.createdAt),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp
            )
            Text(
                text = comment.content,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
        if (comment.canDelete) {
            TextButton(
                text = if (deleting) "正在删除" else "删除",
                onClick = onDelete,
                enabled = enabled && !deleting,
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
internal fun PluginMarketReplyRow(
    comment: PluginMarketComment,
    deleting: Boolean,
    enabled: Boolean,
    onDelete: () -> Unit,
    onReply: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = enabled, onClick = onReply)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                        append(comment.userNickname.ifBlank { "微信用户" })
                    }
                    append(" 回复 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                        append(comment.replyToNickname.ifBlank { "微信用户" })
                    }
                    append("：")
                    append(comment.content)
                },
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = formatPluginMarketTime(comment.createdAt),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (comment.canDelete) {
            TextButton(
                text = if (deleting) "正在删除" else "删除",
                onClick = onDelete,
                enabled = enabled && !deleting,
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

internal data class PluginMarketCommentThread(
    val root: PluginMarketComment,
    val replies: List<PluginMarketComment>
)

internal fun buildPluginMarketCommentThreads(
    comments: List<PluginMarketComment>
): List<PluginMarketCommentThread> {
    val commentsById = comments.associateBy { it.commentId }
    val roots = comments.filter { comment ->
        comment.parentCommentId.isBlank() || comment.parentCommentId !in commentsById
    }.sortedWith(
        compareByDescending<PluginMarketComment> { it.createdAt }
            .thenByDescending { it.commentId }
    )
    val repliesByRoot = linkedMapOf<String, MutableList<PluginMarketComment>>()
    comments.filter { it.parentCommentId.isNotBlank() }.forEach { comment ->
        var cursor = comment
        val visited = mutableSetOf(comment.commentId)
        while (cursor.parentCommentId.isNotBlank()) {
            val parent = commentsById[cursor.parentCommentId] ?: break
            if (!visited.add(parent.commentId)) break
            cursor = parent
        }
        if (cursor.commentId != comment.commentId) {
            repliesByRoot.getOrPut(cursor.commentId) { mutableListOf() }.add(comment)
        }
    }
    return roots.map { root ->
        PluginMarketCommentThread(
            root = root,
            replies = repliesByRoot[root.commentId].orEmpty().sortedWith(
                compareBy<PluginMarketComment> { it.createdAt }.thenBy { it.commentId }
            )
        )
    }
}

@Composable
internal fun PluginMarketDialogInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        )
    }
}

@Composable
internal fun PluginMarketUploadDialog(
    context: Context,
    onDismiss: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf("") }
    var localPlugins by remember { mutableStateOf<List<ScriptPluginRuntime.ScriptPlugin>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var remoteNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var releaseNotes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var extraFiles by remember { mutableStateOf<Map<String, List<PluginMarketFile>>>(emptyMap()) }
    var statuses by remember { mutableStateOf<Map<String, PluginMarketUploadStatus>>(emptyMap()) }
    var uploading by remember { mutableStateOf(false) }
    var uploadedAny by remember { mutableStateOf(false) }
    var reloadVersion by remember { mutableStateOf(0) }

    LaunchedEffect(reloadVersion) {
        loading = true
        loadError = ""
        val result = runCatching {
            withContext(Dispatchers.IO) { ScriptPluginRuntime.listPlugins(context) }
        }
        result.fold(
            onSuccess = { plugins ->
                localPlugins = plugins
                remoteNames = plugins.associate { plugin ->
                    plugin.id to (plugin.displayName ?: plugin.name).ifBlank { plugin.id }
                }
                releaseNotes = plugins.associate { plugin ->
                    plugin.id to releaseNotes[plugin.id].orEmpty()
                }
                extraFiles = extraFiles.filterKeys { id -> plugins.any { it.id == id } }
                selectedIds = selectedIds.intersect(plugins.mapTo(LinkedHashSet()) { it.id })
            },
            onFailure = { error -> loadError = pluginMarketErrorMessage(error) }
        )
        loading = false
    }

    val normalizedQuery = query.trim().lowercase(Locale.US)
    val filteredPlugins = localPlugins.filter { plugin ->
        normalizedQuery.isEmpty() ||
            plugin.id.lowercase(Locale.US).contains(normalizedQuery) ||
            plugin.name.lowercase(Locale.US).contains(normalizedQuery) ||
            plugin.author.lowercase(Locale.US).contains(normalizedQuery)
    }
    val allFilteredSelected = filteredPlugins.isNotEmpty() && filteredPlugins.all { it.id in selectedIds }

    fun startUpload() {
        if (uploading || selectedIds.isEmpty()) return
        val ordered = localPlugins.filter { it.id in selectedIds }
        if (ordered.any { remoteNames[it.id].orEmpty().trim().isBlank() }) {
            Toast.makeText(context, "在线插件名不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        statuses = ordered.associate { plugin ->
            plugin.id to PluginMarketUploadStatus(PluginMarketUploadPhase.QUEUED, "等待上传")
        }
        uploading = true
        scope.launch {
            var publishedCount = 0
            var pendingCount = 0
            ordered.forEach { plugin ->
                statuses = statuses + (
                    plugin.id to PluginMarketUploadStatus(PluginMarketUploadPhase.UPLOADING, "正在上传")
                )
                val result = withContext(Dispatchers.IO) {
                    PluginMarketRepository.upload(
                        context = context,
                        localPluginId = plugin.id,
                        remoteName = remoteNames[plugin.id].orEmpty().trim(),
                        releaseNotes = releaseNotes[plugin.id].orEmpty().trim(),
                        extraFiles = extraFiles[plugin.id].orEmpty()
                    )
                }
                result.fold(
                    onSuccess = { ownership ->
                        uploadedAny = true
                        when (ownership.reviewStatus) {
                            PluginMarketReviewStatus.PENDING -> {
                                pendingCount++
                                statuses = statuses + (
                                    plugin.id to PluginMarketUploadStatus(
                                        PluginMarketUploadPhase.PENDING_REVIEW,
                                        "上传成功，待审核 · ${ownership.remotePluginId}"
                                    )
                                )
                            }
                            PluginMarketReviewStatus.APPROVED -> {
                                publishedCount++
                                statuses = statuses + (
                                    plugin.id to PluginMarketUploadStatus(
                                        PluginMarketUploadPhase.SUCCESS,
                                        "上传成功 · ${ownership.remotePluginId}"
                                    )
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        statuses = statuses + (
                            plugin.id to PluginMarketUploadStatus(
                                PluginMarketUploadPhase.FAILED,
                                "上传失败: ${pluginMarketErrorMessage(error)}"
                            )
                        )
                    }
                )
            }
            uploading = false
            Toast.makeText(
                context,
                "上传完成：已发布 $publishedCount，待审核 $pendingCount，失败 ${ordered.size - publishedCount - pendingCount}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    WindowDialog(
        show = true,
        title = "上传本地插件",
        onDismissRequest = { if (!uploading) onDismiss(uploadedAny) },
        content = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
                SearchBarSurface(
                    query = query,
                    placeholder = "搜索本地插件",
                    onQueryChange = { if (!uploading) query = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选 ${selectedIds.size} / ${localPlugins.size}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (allFilteredSelected) "取消当前筛选" else "全选当前筛选",
                        color = if (filteredPlugins.isEmpty() || uploading) {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(enabled = filteredPlugins.isNotEmpty() && !uploading) {
                            val filteredIds = filteredPlugins.mapTo(LinkedHashSet()) { it.id }
                            selectedIds = if (allFilteredSelected) {
                                selectedIds - filteredIds
                            } else {
                                selectedIds + filteredIds
                            }
                        }.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    when {
                        loading -> item { EmptyText("正在读取本地插件...") }
                        loadError.isNotBlank() -> item {
                            Column {
                                EmptyText(loadError)
                                TextButton(
                                    text = "重试",
                                    onClick = { reloadVersion++ },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColorsPrimary()
                                )
                            }
                        }
                        localPlugins.isEmpty() -> item { EmptyText("没有可运行的本地插件") }
                        filteredPlugins.isEmpty() -> item { EmptyText("没有匹配的本地插件") }
                        else -> items(filteredPlugins.size, key = { filteredPlugins[it].id }) { index ->
                            val plugin = filteredPlugins[index]
                            if (index > 0) InsetDivider(start = 0.dp)
                            PluginMarketUploadRow(
                                plugin = plugin,
                                selected = plugin.id in selectedIds,
                                remoteName = remoteNames[plugin.id].orEmpty(),
                                releaseNotes = releaseNotes[plugin.id].orEmpty(),
                                extraFiles = extraFiles[plugin.id].orEmpty(),
                                status = statuses[plugin.id],
                                enabled = !uploading,
                                onSelectedChange = { selected ->
                                    selectedIds = if (selected) selectedIds + plugin.id else selectedIds - plugin.id
                                },
                                onRemoteNameChange = { name ->
                                    remoteNames = remoteNames + (plugin.id to name.take(100))
                                },
                                onReleaseNotesChange = { notes ->
                                    releaseNotes = releaseNotes + (plugin.id to notes.take(500))
                                },
                                onPickExtraFiles = {
                                    ScriptPluginAgentUi.findAgentActivity(context)?.let { activity ->
                                        PluginMarketExtraFilePickerBridge.launch(activity) { files ->
                                            val existing = extraFiles[plugin.id].orEmpty()
                                            val merged = (files + existing).distinctBy {
                                                it.name.lowercase(Locale.ROOT)
                                            }
                                            extraFiles = extraFiles + (plugin.id to merged)
                                        }
                                    } ?: Toast.makeText(
                                        context,
                                        "无法打开文件选择器",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onClearExtraFiles = {
                                    extraFiles = extraFiles - plugin.id
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "关闭",
                        onClick = { onDismiss(uploadedAny) },
                        enabled = !uploading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = if (uploading) "正在上传" else "上传已选",
                        onClick = ::startUpload,
                        enabled = !uploading && selectedIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

@Composable
internal fun PluginMarketUploadRow(
    plugin: ScriptPluginRuntime.ScriptPlugin,
    selected: Boolean,
    remoteName: String,
    releaseNotes: String,
    extraFiles: List<PluginMarketFile>,
    status: PluginMarketUploadStatus?,
    enabled: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onRemoteNameChange: (String) -> Unit,
    onReleaseNotesChange: (String) -> Unit,
    onPickExtraFiles: () -> Unit,
    onClearExtraFiles: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelectedChange(!selected) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name.ifBlank { plugin.id },
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(plugin.id)
                        append(" · ").append(plugin.author.ifBlank { "未知作者" })
                        append(" · ").append(plugin.version.ifBlank { "未知版本" })
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SelectionMark(selected = selected, multiSelect = true)
        }
        if (selected) {
            Text(
                text = "在线名称",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
            BasicTextField(
                value = remoteName,
                onValueChange = onRemoteNameChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.secondaryVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Text(
                text = "更新说明",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
            BasicTextField(
                value = releaseNotes,
                onValueChange = onReleaseNotesChange,
                enabled = enabled,
                minLines = 2,
                maxLines = 3,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.TopStart) {
                        if (releaseNotes.isBlank()) {
                            Text(
                                text = "填写本次版本的更新内容",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.secondaryVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "附加文件${if (extraFiles.isEmpty()) "" else " (${extraFiles.size})"}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp
                    )
                    if (extraFiles.isNotEmpty()) {
                        Text(
                            text = extraFiles.joinToString("、") { it.name },
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                TextButton(
                    text = "选择",
                    onClick = onPickExtraFiles,
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
                if (extraFiles.isNotEmpty()) {
                    TextButton(
                        text = "清空",
                        onClick = onClearExtraFiles,
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
        if (status != null) {
            val color = when (status.phase) {
                PluginMarketUploadPhase.SUCCESS -> MiuixTheme.colorScheme.primary
                PluginMarketUploadPhase.PENDING_REVIEW -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                PluginMarketUploadPhase.FAILED -> Color(0xFFD93025)
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            }
            Text(
                text = status.message,
                color = color,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp)
            )
        }
    }
}

internal fun pluginMarketErrorMessage(error: Throwable): String {
    if (error is PluginMarketException && error.errorCode == "UPLOADER_BLACKLISTED") {
        return "当前微信账号已被禁止上传在线插件"
    }
    return error.message?.trim().takeUnless { it.isNullOrBlank() } ?: error.javaClass.simpleName
}

}
