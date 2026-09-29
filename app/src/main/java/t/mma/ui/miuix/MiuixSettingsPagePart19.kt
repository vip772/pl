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
internal fun WeChatTabletMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, WeChatTabletSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "平板模式") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        WeChatTabletSettings.KEY_ENABLE,
                        "平板模式",
                        "开启平板模式，退出微信登陆生效",
                        WeChatTabletSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun AutoTransferMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, AutoTransferSettings.PREFS_NAME) }
    var templates by remember {
        mutableStateOf(TransferRuleConfig.parseTemplates(sp.getString(TransferRuleConfig.KEY_TEMPLATES, "")))
    }
    var bindings by remember {
        mutableStateOf(TransferRuleConfig.parseBindings(sp.getString(TransferRuleConfig.KEY_BINDINGS, "")))
    }
    var defaultTemplateId by remember {
        mutableStateOf(sp.getString(TransferRuleConfig.KEY_DEFAULT_TEMPLATE_ID, "") ?: "")
    }
    var whitelist by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_WHITELIST, "") ?: "") }
    var blacklist by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_BLACKLIST, "") ?: "") }
    var listMode by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_MODE, 0)) }
    var delayMs by remember { mutableStateOf(sp.getLong(AutoTransferSettings.KEY_DELAY_MS, 0L).toString()) }
    var delayMode by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_DELAY_MODE, TransferRuleConfig.DELAY_CUSTOM)) }
    var randomMinMs by remember { mutableStateOf(sp.getLong(AutoTransferSettings.KEY_DELAY_RANDOM_MIN, 500L).toString()) }
    var randomMaxMs by remember { mutableStateOf(sp.getLong(AutoTransferSettings.KEY_DELAY_RANDOM_MAX, 3000L).toString()) }
    var amountEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_AMOUNT_ENABLE, false)) }
    var amountCondition by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_AMOUNT_COND, 1)) }
    var amountAction by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_AMOUNT_ACTION, 0)) }
    var amountValue by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_AMOUNT_VALUE, "0") ?: "0") }
    var keywords by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_KEYWORDS, "") ?: "") }
    var keywordMode by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_KEYWORD_MODE, 0)) }
    var quietEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_QUIET_ENABLE, false)) }
    var quietStart by remember { mutableStateOf(formatRedPacketSecond(sp.getInt(AutoTransferSettings.KEY_QUIET_START_SECOND, 0))) }
    var quietEnd by remember { mutableStateOf(formatRedPacketSecond(sp.getInt(AutoTransferSettings.KEY_QUIET_END_SECOND, 0))) }
    var replySteps by remember { mutableStateOf(loadGlobalTransferReplySteps(sp)) }
    var groupReplySteps by remember { mutableStateOf(loadGlobalGroupTransferReplySteps(sp)) }
    var notifySystemEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_SYSTEM_ENABLE, false)) }
    var notifyToastEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_TOAST_ENABLE, false)) }
    var notifySoundEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_SOUND_ENABLE, false)) }
    var notifySoundMode by remember { mutableStateOf(sp.getInt(AutoTransferSettings.KEY_NOTIFY_SOUND_MODE, AutoTransferSettings.NOTIFY_SOUND_MODE_SYSTEM)) }
    var notifyVibrateEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_VIBRATE_ENABLE, false)) }
    var notifySoundUri by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_NOTIFY_SOUND_URI, "") ?: "") }
    var notifyText by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_NOTIFY_TEXT, "已收款 {amount} 元") ?: "") }
    var notifyToastText by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_NOTIFY_TOAST_TEXT, "已收款 {amount} 元") ?: "") }
    var announceEnabled by remember { mutableStateOf(sp.getBoolean(AutoTransferSettings.KEY_ANNOUNCE_ENABLE, false)) }
    var announceText by remember { mutableStateOf(sp.getString(AutoTransferSettings.KEY_ANNOUNCE_TEXT, "收到转账 {amount} 元") ?: "") }
    var timeFormat by remember {
        mutableStateOf(
            PaymentTemplateTimeFormatter.normalizePattern(
                sp.getString(AutoTransferSettings.KEY_TIME_FORMAT, AutoTransferSettings.DEFAULT_TIME_FORMAT)
            )
        )
    }
    var receiveAccount by remember {
        mutableStateOf(
            sp.getString(
                AutoTransferSettings.KEY_RECEIVE_ACCOUNT,
                TransferReceiveAccountStore.DEFAULT_KEY
            ) ?: TransferReceiveAccountStore.DEFAULT_KEY
        )
    }
    val receiveAccountOptions = remember { transferReceiveAccountOptions(context) }
    if (receiveAccountOptions.none { it.value == receiveAccount }) {
        receiveAccount = TransferReceiveAccountStore.DEFAULT_KEY
    }
    var picker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var templateEditor by remember { mutableStateOf<TransferTemplateEditorRequest?>(null) }
    var bindingEditor by remember { mutableStateOf<TransferBindingEditorRequest?>(null) }
    var showTemplates by remember { mutableStateOf(false) }
    var showBindings by remember { mutableStateOf(false) }
    var showBatchApply by remember { mutableStateOf(false) }
    var showReplySteps by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf(TransferReplyTarget.PRIVATE) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun persistRules(
        nextTemplates: List<TransferRuleTemplate> = templates,
        nextBindings: List<TransferRuleBinding> = bindings,
        nextDefault: String = defaultTemplateId
    ) {
        sp.edit()
            .putString(TransferRuleConfig.KEY_TEMPLATES, TransferRuleConfig.encodeTemplates(nextTemplates))
            .putString(TransferRuleConfig.KEY_BINDINGS, TransferRuleConfig.encodeBindings(nextBindings))
            .putString(TransferRuleConfig.KEY_DEFAULT_TEMPLATE_ID, nextDefault)
            .commit()
    }

    val route: AutoTransferRoute = when {
        templateEditor != null -> AutoTransferRoute.TemplateEditor(templateEditor!!)
        bindingEditor != null -> AutoTransferRoute.BindingEditor(bindingEditor!!)
        picker != null -> AutoTransferRoute.ContactPicker(picker!!)
        showReplySteps -> AutoTransferRoute.GlobalReplySteps(replyTarget)
        showTemplates -> AutoTransferRoute.TemplateManager
        showBindings -> AutoTransferRoute.BindingManager
        showBatchApply -> AutoTransferRoute.BatchApply
        else -> AutoTransferRoute.Main
    }

    SettingsRouteTransition(
        targetState = route,
        label = "AutoTransferRouteTransition",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is AutoTransferRoute.ContactPicker -> {
                val request = currentRoute.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { picker = null },
                    onConfirm = { selected ->
                        request.onValue(formatIds(selected.map { it.id }))
                        picker = null
                    }
                )
            }
            is AutoTransferRoute.TemplateEditor -> TransferTemplateEditorPage(
                context = context,
                request = currentRoute.request,
                onBack = { templateEditor = null },
                onSave = { updated ->
                    val request = currentRoute.request
                    val next = if (request.index in templates.indices) {
                        templates.toMutableList().also { it[request.index] = updated }
                    } else templates + updated
                    templates = next
                    if (defaultTemplateId.isBlank()) defaultTemplateId = updated.id
                    persistRules(nextTemplates = next, nextDefault = defaultTemplateId)
                    templateEditor = null
                },
                onDelete = {
                    val request = currentRoute.request
                    if (request.index in templates.indices) {
                        val deleted = templates[request.index].id
                        templates = templates.toMutableList().also { it.removeAt(request.index) }
                        bindings = bindings.map { if (it.templateId == deleted) it.copy(templateId = "") else it }
                        if (defaultTemplateId == deleted) defaultTemplateId = templates.firstOrNull()?.id.orEmpty()
                        persistRules()
                    }
                    templateEditor = null
                }
            )
            is AutoTransferRoute.BindingEditor -> TransferBindingEditorPage(
                context = context,
                request = currentRoute.request,
                templates = templates,
                onBack = { bindingEditor = null },
                onSave = { updated ->
                    val base = bindings.toMutableList()
                    if (currentRoute.request.index in base.indices) base.removeAt(currentRoute.request.index)
                    bindings = upsertTransferBindings(base, listOf(updated))
                    persistRules(nextBindings = bindings)
                    bindingEditor = null
                },
                onDelete = {
                    if (currentRoute.request.index in bindings.indices) {
                        bindings = bindings.toMutableList().also { it.removeAt(currentRoute.request.index) }
                        persistRules(nextBindings = bindings)
                    }
                    bindingEditor = null
                }
            )
            AutoTransferRoute.TemplateManager -> TransferTemplateListPage(
                templates = templates,
                onBack = { showTemplates = false },
                onOpen = { index, value -> templateEditor = TransferTemplateEditorRequest(index, value, true) },
                onAdd = { templateEditor = TransferTemplateEditorRequest(templates.size, newTransferTemplate(templates.size + 1, sp), false) }
            )
            AutoTransferRoute.BindingManager -> TransferBindingListPage(
                bindings = bindings,
                templates = templates,
                onBack = { showBindings = false },
                onOpen = { index, value -> bindingEditor = TransferBindingEditorRequest(index, value, true) },
                onDeleteBindings = { targets ->
                    val targetIds = targets.mapTo(HashSet()) { it.id }
                    bindings = bindings.filterNot { it.id in targetIds }
                    persistRules(nextBindings = bindings)
                    Toast.makeText(context, "已删除 ${targets.size} 个适用聊天", Toast.LENGTH_SHORT).show()
                },
                onAdd = {
                    picker = ContactPickerRequest(
                        title = "选择适用聊天",
                        mode = ContactPickerMode.BOTH,
                        multiSelect = true,
                        existingValue = "",
                        enableLabels = true,
                        onValue = { value ->
                            val additions = parseIds(value).map { id ->
                                bindings.firstOrNull { it.targetId == id }
                                    ?: TransferRuleBinding(id, id, transferContactLabel(id), false, if (templates.size == 1) templates.first().id else "")
                            }
                            if (additions.size == 1) {
                                val item = additions.first()
                                val index = bindings.indexOfFirst { it.targetId == item.targetId }
                                bindingEditor = TransferBindingEditorRequest(index.takeIf { it >= 0 } ?: bindings.size, item, index >= 0)
                            } else if (additions.isNotEmpty()) {
                                bindings = upsertTransferBindings(bindings, additions)
                                persistRules(nextBindings = bindings)
                            }
                        }
                    )
                }
            )
            AutoTransferRoute.BatchApply -> TransferBatchApplyPage(
                templates = templates,
                bindings = bindings,
                onBack = { showBatchApply = false },
                onPickChats = { templateId ->
                    picker = ContactPickerRequest(
                        title = "批量套用收款模板",
                        mode = ContactPickerMode.BOTH,
                        multiSelect = true,
                        existingValue = formatIds(bindings.filter { it.templateId == templateId }.map { it.targetId }),
                        enableLabels = true,
                        onValue = { value ->
                            val selected = parseIds(value)
                            val retained = bindings.filterNot { it.templateId == templateId && it.targetId !in selected }
                            val additions = selected.map { id ->
                                TransferRuleBinding(id, id, transferContactLabel(id), true, templateId)
                            }
                            bindings = upsertTransferBindings(retained, additions)
                            persistRules(nextBindings = bindings)
                            Toast.makeText(context, "模板已套用到 ${selected.size} 个聊天", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
            is AutoTransferRoute.GlobalReplySteps -> RedPacketReplyStepsPage(
                context = context,
                title = if (currentRoute.target == TransferReplyTarget.GROUP) "群聊收款回复" else "私聊收款回复",
                initialSteps = if (currentRoute.target == TransferReplyTarget.GROUP) groupReplySteps else replySteps,
                templateVariables = transferTemplateVariables,
                onBack = { showReplySteps = false },
                onSave = {
                    if (currentRoute.target == TransferReplyTarget.GROUP) groupReplySteps = it else replySteps = it
                    showReplySteps = false
                }
            )
            AutoTransferRoute.Main -> PageScaffold(
                title = provider.title(),
                largeTitle = provider.title(),
                scrollBehavior = scrollBehavior,
                bottomBar = {
                    BottomActionBar(
                        primaryText = "保存设置",
                        onPrimaryClick = save@{
                            if (!PaymentTemplateTimeFormatter.isValidPattern(timeFormat)) {
                                Toast.makeText(context, "时间格式无效", Toast.LENGTH_SHORT).show()
                                return@save
                            }
                            val normalizedTimeFormat = PaymentTemplateTimeFormatter.normalizePattern(timeFormat)
                            val minDelay = randomMinMs.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L
                            val maxDelay = randomMaxMs.toLongOrNull()?.coerceIn(minDelay, 600000L) ?: minDelay
                            val cleanSteps = cleanRedPacketReplySteps(replySteps)
                            val cleanGroupSteps = cleanRedPacketReplySteps(groupReplySteps)
                            sp.edit()
                                .putString(AutoTransferSettings.KEY_WHITELIST, whitelist)
                                .putString(AutoTransferSettings.KEY_BLACKLIST, blacklist)
                                .putInt(AutoTransferSettings.KEY_MODE, listMode)
                                .putLong(AutoTransferSettings.KEY_DELAY_MS, delayMs.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L)
                                .putInt(AutoTransferSettings.KEY_DELAY_MODE, delayMode)
                                .putLong(AutoTransferSettings.KEY_DELAY_RANDOM_MIN, minDelay)
                                .putLong(AutoTransferSettings.KEY_DELAY_RANDOM_MAX, maxDelay)
                                .putString(AutoTransferSettings.KEY_AMOUNT_VALUE, amountValue)
                                .putBoolean(AutoTransferSettings.KEY_AMOUNT_ENABLE, amountEnabled)
                                .putInt(AutoTransferSettings.KEY_AMOUNT_COND, amountCondition)
                                .putInt(AutoTransferSettings.KEY_AMOUNT_ACTION, amountAction)
                                .putInt(AutoTransferSettings.KEY_KEYWORD_MODE, keywordMode)
                                .putString(AutoTransferSettings.KEY_KEYWORDS, if (keywordMode == 0) "" else keywords)
                                .putString(AutoTransferSettings.KEY_RECEIVE_ACCOUNT, receiveAccount)
                                .putBoolean(AutoTransferSettings.KEY_QUIET_ENABLE, quietEnabled)
                                .putInt(AutoTransferSettings.KEY_QUIET_START_SECOND, parseRedPacketSecond(quietStart, 0))
                                .putInt(AutoTransferSettings.KEY_QUIET_END_SECOND, parseRedPacketSecond(quietEnd, 0))
                                .putString(AutoTransferSettings.KEY_REPLY_ITEMS, RedPacketRuleConfig.encodeReplySteps(cleanSteps))
                                .putString(AutoTransferSettings.KEY_REPLY_GROUP_ITEMS, RedPacketRuleConfig.encodeReplySteps(cleanGroupSteps))
                                .putBoolean(AutoTransferSettings.KEY_REPLY_ENABLE, cleanSteps.isNotEmpty())
                                .putString(AutoTransferSettings.KEY_REPLY_TEXT, cleanSteps.firstOrNull()?.content.orEmpty())
                                .putBoolean(AutoTransferSettings.KEY_NOTIFY_SYSTEM_ENABLE, notifySystemEnabled)
                                .putBoolean(AutoTransferSettings.KEY_NOTIFY_TOAST_ENABLE, notifyToastEnabled)
                                .putBoolean(AutoTransferSettings.KEY_NOTIFY_SOUND_ENABLE, notifySoundEnabled)
                                .putInt(AutoTransferSettings.KEY_NOTIFY_SOUND_MODE, notifySoundMode)
                                .putBoolean(AutoTransferSettings.KEY_NOTIFY_VIBRATE_ENABLE, notifyVibrateEnabled)
                                .putString(AutoTransferSettings.KEY_NOTIFY_SOUND_URI, notifySoundUri)
                                .putString(AutoTransferSettings.KEY_NOTIFY_TEXT, notifyText)
                                .putString(AutoTransferSettings.KEY_NOTIFY_TOAST_TEXT, notifyToastText)
                                .putBoolean(AutoTransferSettings.KEY_ANNOUNCE_ENABLE, announceEnabled)
                                .putString(AutoTransferSettings.KEY_ANNOUNCE_TEXT, announceText)
                                .putString(AutoTransferSettings.KEY_TIME_FORMAT, normalizedTimeFormat)
                                .apply()
                            replySteps = cleanSteps
                            groupReplySteps = cleanGroupSteps
                            timeFormat = normalizedTimeFormat
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
                    item { SmallTitle(text = "规则") }
                    item {
                        SettingsCard {
                            SwitchRow(sp, AutoTransferSettings.KEY_ENABLE, "自动收款", "自动领取待收款转账", false)
                            InsetDivider()
                            ActionRow("收款规则模板", if (templates.isEmpty()) "暂无模板" else "${templates.size} 个模板") { showTemplates = true }
                            InsetDivider()
                            PopupChoiceRow(
                                title = "默认规则",
                                summary = describeTransferDefault(defaultTemplateId, templates),
                                options = listOf(PopupChoice("旧版全局设置", "")) + templates.map { PopupChoice(it.name, it.id) },
                                currentValue = defaultTemplateId,
                                onValueChanged = {
                                    defaultTemplateId = it
                                    persistRules(nextDefault = it)
                                }
                            )
                            InsetDivider()
                            ActionRow("适用聊天", if (bindings.isEmpty()) "暂无单独配置" else "${bindings.size} 个聊天") { showBindings = true }
                            InsetDivider()
                            ActionRow("批量套用模板", "一次给多个聊天分配同一规则") { showBatchApply = true }
                        }
                    }
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "全局收款") }
                    item {
                        SettingsCard {
                            PopupChoiceRow("收款位置", "使用微信当前账号提供的收款账户", receiveAccountOptions, receiveAccount, onValueChanged = { receiveAccount = it })
                            InsetDivider()
                            SwitchRow(sp, AutoTransferSettings.KEY_REFUND_REJECTED, "拒收时退回", "规则不通过时原路退回", false)
                            InsetDivider()
                            PopupOptionRow("收款延迟", transferDelayModeLabel(delayMode), redPacketDelayModeOptions(true), delayMode, onValueChanged = { delayMode = it })
                            if (delayMode == TransferRuleConfig.DELAY_CUSTOM) {
                                InsetDivider(); NumberInputRow("自定义延迟", "单位 ms", delayMs) { delayMs = it }
                            } else if (delayMode == TransferRuleConfig.DELAY_RANDOM) {
                                InsetDivider(); NumberInputRow("最小延迟", "单位 ms", randomMinMs) { randomMinMs = it }
                                InsetDivider(); NumberInputRow("最大延迟", "单位 ms", randomMaxMs) { randomMaxMs = it }
                            }
                        }
                    }
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "全局过滤") }
                    item {
                        SettingsCard {
                            PopupOptionRow("收款范围", transferListModeLabel(listMode), optionItems("全部接收" to 0, "只接收白名单" to 1, "拒收黑名单" to 2), listMode, onValueChanged = { listMode = it; sp.edit().putInt(AutoTransferSettings.KEY_MODE, it).apply() })
                            if (listMode == 1 || listMode == 2) {
                                InsetDivider()
                                ActionRow(if (listMode == 1) "白名单" else "黑名单", autoReplySelectedIdSummary(if (listMode == 1) whitelist else blacklist)) {
                                    picker = ContactPickerRequest(if (listMode == 1) "选择白名单" else "选择黑名单", ContactPickerMode.BOTH, true, if (listMode == 1) whitelist else blacklist, {
                                        if (listMode == 1) whitelist = it else blacklist = it
                                    }, true)
                                }
                            }
                            InsetDivider()
                            SwitchRow(amountEnabled, "启用金额规则", "按转账金额决定接收或拒收") { amountEnabled = it; sp.edit().putBoolean(AutoTransferSettings.KEY_AMOUNT_ENABLE, it).apply() }
                            if (amountEnabled) {
                                InsetDivider(); PopupOptionRow("金额条件", transferAmountConditionLabel(amountCondition), optionItems("大于" to 0, "小于" to 1, "等于" to 2), amountCondition, onValueChanged = { amountCondition = it; sp.edit().putInt(AutoTransferSettings.KEY_AMOUNT_COND, it).apply() })
                                InsetDivider(); InputRow("金额数值", "单位元，例如 10.5", amountValue) { amountValue = it.filter { ch -> ch.isDigit() || ch == '.' } }
                                InsetDivider(); PopupOptionRow("命中后动作", transferAmountActionLabel(amountAction), optionItems("拒收/忽略" to 0, "仅接收满足条件" to 1), amountAction, onValueChanged = { amountAction = it; sp.edit().putInt(AutoTransferSettings.KEY_AMOUNT_ACTION, it).apply() })
                            }
                            InsetDivider()
                            PopupOptionRow("关键词规则", transferKeywordModeLabel(keywordMode), optionItems("不启用" to 0, "必须包含关键词" to 1, "包含则拒收" to 2), keywordMode, onValueChanged = { keywordMode = it; sp.edit().putInt(AutoTransferSettings.KEY_KEYWORD_MODE, it).apply() })
                            if (keywordMode != 0) {
                                InsetDivider(); InputRow("关键词", "多个关键词用 |、逗号或换行分隔", keywords, minLines = 2) { keywords = it }
                            }
                            InsetDivider()
                            SwitchRow(quietEnabled, "禁收时段", "指定时段内不自动收款") { quietEnabled = it }
                            if (quietEnabled) {
                                InsetDivider(); TimeOfDayPickerRow("开始时间", quietStart) { quietStart = it }
                                InsetDivider(); TimeOfDayPickerRow("结束时间", quietEnd) { quietEnd = it }
                            }
                        }
                    }
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板变量") }
                    item {
                        SettingsCard {
                            InputRow(
                                "时间变量格式",
                                "用于 {time}，例如 yyyy-MM-dd HH:mm:ss",
                                timeFormat
                            ) { timeFormat = it }
                        }
                    }
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "收款后回复") }
                    item {
                        SettingsCard {
                            SelectRow("私聊收款回复", describeRedPacketReplySteps(replySteps)) {
                                replyTarget = TransferReplyTarget.PRIVATE
                                showReplySteps = true
                            }
                            InsetDivider()
                            SelectRow("群聊收款回复", describeRedPacketReplySteps(groupReplySteps)) {
                                replyTarget = TransferReplyTarget.GROUP
                                showReplySteps = true
                            }
                        }
                    }
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "成功提醒") }
                    item {
                        TransferNotificationSettingsCard(
                            context, notifySystemEnabled, { notifySystemEnabled = it }, notifyToastEnabled, { notifyToastEnabled = it },
                            notifySoundEnabled, { notifySoundEnabled = it }, notifySoundMode, { notifySoundMode = it; notifySoundUri = "" },
                            notifyVibrateEnabled, { notifyVibrateEnabled = it }, notifySoundUri, { notifySoundUri = it },
                            notifyText, { notifyText = it }, notifyToastText, { notifyToastText = it },
                            announceEnabled, { announceEnabled = it }, announceText, { announceText = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TransferTemplateListPage(
    templates: List<TransferRuleTemplate>,
    onBack: () -> Unit,
    onOpen: (Int, TransferRuleTemplate) -> Unit,
    onAdd: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "收款规则模板",
        largeTitle = "收款规则模板",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("新增模板", onAdd, "返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。新增后可设为默认规则或分配给指定聊天。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SelectRow(
                                template.name.ifBlank { "模板 ${index + 1}" },
                                describeTransferTemplate(template)
                            ) { onOpen(index, template) }
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TransferBindingListPage(
    bindings: List<TransferRuleBinding>,
    templates: List<TransferRuleTemplate>,
    onBack: () -> Unit,
    onOpen: (Int, TransferRuleBinding) -> Unit,
    onAdd: () -> Unit,
    onDeleteBindings: (List<TransferRuleBinding>) -> Unit
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf(ConversationRuleCategory.ALL) }
    var query by remember { mutableStateOf("") }
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val lower = query.trim().lowercase(Locale.US)
    val visible = bindings.mapIndexed { index, value -> index to value }.filter { (_, value) ->
        conversationRuleCategoryMatches(value, category) && (
            lower.isBlank() || value.label.lowercase(Locale.US).contains(lower) ||
                value.targetId.lowercase(Locale.US).contains(lower) ||
                templates.firstOrNull { it.id == value.templateId }?.name?.lowercase(Locale.US)?.contains(lower) == true
            )
    }
    val visibleIds = visible.mapTo(LinkedHashSet()) { it.second.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
    val selectedBindings = bindings.filter { it.id in selectedIds }
    val scrollBehavior = MiuixScrollBehavior()
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
                    onPrimaryClick = onAdd,
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
            item { SettingsCard { InputRow("搜索聊天", "昵称 / ID / 模板名", query) { query = it } } }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "聊天 · ${visible.size}/${bindings.size} 项") }
            item {
                SettingsCard {
                    if (visible.isEmpty()) {
                        EmptyText(if (bindings.isEmpty()) "暂无适用聊天。" else "没有匹配结果。")
                    } else {
                        visible.forEachIndexed { row, (index, binding) ->
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = binding.label.ifBlank { binding.targetId },
                                        value = index,
                                        summary = describeTransferBinding(binding, templates)
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
                                    binding.label.ifBlank { binding.targetId },
                                    describeTransferBinding(binding, templates)
                                ) { onOpen(index, binding) }
                            }
                            if (row < visible.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedBindings.size} 个适用聊天，此操作不可撤销。",
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

@Composable
internal fun TransferBindingEditorPage(
    context: Context,
    request: TransferBindingEditorRequest,
    templates: List<TransferRuleTemplate>,
    onBack: () -> Unit,
    onSave: (TransferRuleBinding) -> Unit,
    onDelete: () -> Unit
) {
    var enabled by remember(request) { mutableStateOf(request.binding.enabled) }
    var templateId by remember(request) { mutableStateOf(request.binding.templateId) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = request.binding.label.ifBlank { request.binding.targetId },
        largeTitle = request.binding.label.ifBlank { request.binding.targetId },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                "保存聊天",
                {
                    onSave(request.binding.copy(enabled = enabled, templateId = templateId))
                    Toast.makeText(context, "适用聊天已保存", Toast.LENGTH_SHORT).show()
                },
                "返回",
                onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "聊天") }
            item {
                SettingsCard {
                    InfoRow("ID", request.binding.targetId)
                    InsetDivider()
                    SwitchRow(enabled, "启用自动收款", "关闭后该聊天不会自动收款") { enabled = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
            item {
                SettingsCard {
                    OptionChoiceRow(
                        OptionItem("跟随默认规则", -1, "使用默认模板或全局设置"),
                        templateId.isBlank()
                    ) { templateId = "" }
                    templates.forEachIndexed { index, template ->
                        InsetDivider()
                        OptionChoiceRow(
                            OptionItem(template.name.ifBlank { "模板 ${index + 1}" }, index, describeTransferTemplate(template)),
                            templateId == template.id
                        ) { templateId = template.id }
                    }
                }
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item { SettingsCard { ActionRow("移除适用聊天", "移除后恢复默认规则", onDelete) } }
            }
        }
    }
}

@Composable
internal fun TransferBatchApplyPage(
    templates: List<TransferRuleTemplate>,
    bindings: List<TransferRuleBinding>,
    onBack: () -> Unit,
    onPickChats: (String) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "批量套用模板",
        largeTitle = "批量套用模板",
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "选择模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("请先新增收款规则模板。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            val count = bindings.count { it.templateId == template.id }
                            SelectRow(template.name, "$count 个聊天 · ${describeTransferTemplate(template)}") {
                                onPickChats(template.id)
                            }
                            if (index < templates.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TransferTemplateEditorPage(
    context: Context,
    request: TransferTemplateEditorRequest,
    onBack: () -> Unit,
    onSave: (TransferRuleTemplate) -> Unit,
    onDelete: () -> Unit
) {
    var value by remember(request) { mutableStateOf(request.template) }
    var showReplySteps by remember { mutableStateOf(false) }
    var replyTarget by remember { mutableStateOf(TransferReplyTarget.PRIVATE) }
    var groupReplySteps by remember(request) {
        mutableStateOf(request.template.groupReplySteps ?: request.template.replySteps)
    }
    var picker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    val accountOptions = remember { transferReceiveAccountOptions(context) }
    if (showReplySteps) {
        RedPacketReplyStepsPage(
            context = context,
            title = if (replyTarget == TransferReplyTarget.GROUP) "模板群聊收款回复" else "模板私聊收款回复",
            initialSteps = if (replyTarget == TransferReplyTarget.GROUP) groupReplySteps else value.replySteps,
            templateVariables = transferTemplateVariables,
            onBack = { showReplySteps = false },
            onSave = {
                if (replyTarget == TransferReplyTarget.GROUP) groupReplySteps = it else value = value.copy(replySteps = it)
                showReplySteps = false
            }
        )
        return
    }
    picker?.let { requestPicker ->
        ContactPickerPage(
            context = context,
            request = requestPicker,
            onBack = { picker = null },
            onConfirm = {
                requestPicker.onValue(formatIds(it.map { option -> option.id }))
                picker = null
            }
        )
        return
    }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = value.name.ifBlank { "收款模板" },
        largeTitle = value.name.ifBlank { "收款模板" },
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                "保存模板",
                {
                    onSave(value.copy(
                        name = value.name.ifBlank { "收款模板" },
                        replySteps = cleanRedPacketReplySteps(value.replySteps),
                        groupReplySteps = cleanRedPacketReplySteps(groupReplySteps)
                    ))
                    Toast.makeText(context, "收款模板已保存", Toast.LENGTH_SHORT).show()
                },
                "返回",
                onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    InputRow("模板名称", "用于默认规则和聊天绑定", value.name) { value = value.copy(name = it) }
                    InsetDivider()
                    SwitchRow(value.enabled, "启用模板", "关闭后使用该模板的聊天不会自动收款") { value = value.copy(enabled = it) }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "收款") }
            item {
                SettingsCard {
                    PopupChoiceRow("收款位置", "选择转账到账账户", accountOptions, value.receiveAccount, onValueChanged = { value = value.copy(receiveAccount = it) })
                    InsetDivider()
                    SwitchRow(value.refundRejected, "拒收时退回", "规则不通过时原路退回") { value = value.copy(refundRejected = it) }
                    InsetDivider()
                    PopupOptionRow("收款延迟", transferDelayModeLabel(value.delayMode), redPacketDelayModeOptions(true), value.delayMode, onValueChanged = { value = value.copy(delayMode = it) })
                    if (value.delayMode == TransferRuleConfig.DELAY_CUSTOM) {
                        InsetDivider(); NumberInputRow("自定义延迟", "单位 ms", value.delayMs.toString()) { value = value.copy(delayMs = it.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L) }
                    } else if (value.delayMode == TransferRuleConfig.DELAY_RANDOM) {
                        InsetDivider(); NumberInputRow("最小延迟", "单位 ms", value.randomMinMs.toString()) { value = value.copy(randomMinMs = it.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L) }
                        InsetDivider(); NumberInputRow("最大延迟", "单位 ms", value.randomMaxMs.toString()) { value = value.copy(randomMaxMs = it.toLongOrNull()?.coerceIn(value.randomMinMs, 600000L) ?: value.randomMinMs) }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "过滤") }
            item {
                SettingsCard {
                    PopupOptionRow("收款范围", transferListModeLabel(value.listMode), optionItems("全部接收" to 0, "只接收白名单" to 1, "拒收黑名单" to 2), value.listMode, onValueChanged = { value = value.copy(listMode = it) })
                    if (value.listMode == 1 || value.listMode == 2) {
                        InsetDivider()
                        val listValue = if (value.listMode == 1) value.whitelist else value.blacklist
                        ActionRow(if (value.listMode == 1) "白名单" else "黑名单", autoReplySelectedIdSummary(listValue)) {
                            picker = ContactPickerRequest(
                                if (value.listMode == 1) "选择白名单" else "选择黑名单",
                                ContactPickerMode.BOTH,
                                true,
                                listValue,
                                { selected -> value = if (value.listMode == 1) value.copy(whitelist = selected) else value.copy(blacklist = selected) },
                                true
                            )
                        }
                    }
                    InsetDivider()
                    SwitchRow(value.amountEnabled, "启用金额规则", "按转账金额决定接收或拒收") { value = value.copy(amountEnabled = it) }
                    if (value.amountEnabled) {
                        InsetDivider(); PopupOptionRow("金额条件", transferAmountConditionLabel(value.amountCondition), optionItems("大于" to 0, "小于" to 1, "等于" to 2), value.amountCondition, onValueChanged = { value = value.copy(amountCondition = it) })
                        InsetDivider(); InputRow("金额数值", "单位元，例如 10.5", value.amountValue) { text -> value = value.copy(amountValue = text.filter { it.isDigit() || it == '.' }) }
                        InsetDivider(); PopupOptionRow("命中后动作", transferAmountActionLabel(value.amountAction), optionItems("拒收/忽略" to 0, "仅接收满足条件" to 1), value.amountAction, onValueChanged = { value = value.copy(amountAction = it) })
                    }
                    InsetDivider()
                    PopupOptionRow("关键词规则", transferKeywordModeLabel(value.keywordMode), optionItems("不启用" to 0, "必须包含关键词" to 1, "包含则拒收" to 2), value.keywordMode, onValueChanged = { value = value.copy(keywordMode = it) })
                    if (value.keywordMode != 0) {
                        InsetDivider(); InputRow("关键词", "多个关键词用 |、逗号或换行分隔", value.keywords, minLines = 2) { value = value.copy(keywords = it) }
                    }
                    InsetDivider()
                    SwitchRow(value.quietEnabled, "禁收时段", "指定时段内不自动收款") { value = value.copy(quietEnabled = it) }
                    if (value.quietEnabled) {
                        InsetDivider(); TimeOfDayPickerRow("开始时间", formatRedPacketSecond(value.quietStartSecond)) { value = value.copy(quietStartSecond = parseRedPacketSecond(it, value.quietStartSecond)) }
                        InsetDivider(); TimeOfDayPickerRow("结束时间", formatRedPacketSecond(value.quietEndSecond)) { value = value.copy(quietEndSecond = parseRedPacketSecond(it, value.quietEndSecond)) }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "收款后回复") }
            item {
                SettingsCard {
                    SelectRow("私聊收款回复", describeRedPacketReplySteps(value.replySteps)) {
                        replyTarget = TransferReplyTarget.PRIVATE
                        showReplySteps = true
                    }
                    InsetDivider()
                    SelectRow("群聊收款回复", describeRedPacketReplySteps(groupReplySteps)) {
                        replyTarget = TransferReplyTarget.GROUP
                        showReplySteps = true
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "成功提醒") }
            item {
                TransferNotificationSettingsCard(
                    context,
                    value.notifySystemEnabled, { value = value.copy(notifySystemEnabled = it) },
                    value.notifyToastEnabled, { value = value.copy(notifyToastEnabled = it) },
                    value.notifySoundEnabled, { value = value.copy(notifySoundEnabled = it) },
                    value.notifySoundMode, { value = value.copy(notifySoundMode = it, notifySoundUri = "") },
                    value.notifyVibrateEnabled, { value = value.copy(notifyVibrateEnabled = it) },
                    value.notifySoundUri, { value = value.copy(notifySoundUri = it) },
                    value.notifyText, { value = value.copy(notifyText = it) },
                    value.notifyToastText, { value = value.copy(notifyToastText = it) },
                    value.announceEnabled, { value = value.copy(announceEnabled = it) },
                    value.announceText, { value = value.copy(announceText = it) }
                )
            }
            if (request.canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item { SettingsCard { ActionRow("删除模板", "删除后相关聊天恢复默认规则", onDelete) } }
            }
        }
    }
}

@Composable
internal fun TransferNotificationSettingsCard(
    context: Context,
    system: Boolean,
    onSystem: (Boolean) -> Unit,
    toast: Boolean,
    onToast: (Boolean) -> Unit,
    sound: Boolean,
    onSound: (Boolean) -> Unit,
    soundMode: Int,
    onSoundMode: (Int) -> Unit,
    vibrate: Boolean,
    onVibrate: (Boolean) -> Unit,
    soundUri: String,
    onSoundUri: (String) -> Unit,
    noticeText: String,
    onNoticeText: (String) -> Unit,
    toastText: String,
    onToastText: (String) -> Unit,
    announce: Boolean,
    onAnnounce: (Boolean) -> Unit,
    announceText: String,
    onAnnounceText: (String) -> Unit
) {
    SettingsCard {
        SwitchRow(system, "通知栏提醒", "收款请求成功后显示") { onSystem(it) }
        if (system) {
            InsetDivider(); VariableInputRow("通知栏文案", "支持下方变量", noticeText, transferTemplateVariables, onValueChange = onNoticeText)
        }
        InsetDivider()
        SwitchRow(toast, "浮窗提醒", "收款请求成功后短暂提示") { onToast(it) }
        if (toast) {
            InsetDivider(); VariableInputRow("浮窗文案", "支持下方变量", toastText, transferTemplateVariables, onValueChange = onToastText)
        }
        InsetDivider()
        SwitchRow(sound, "通知铃声", "开启后播放通知铃声") { onSound(it) }
        if (sound) {
            InsetDivider()
            PopupOptionRow(
                "铃声模式",
                if (soundMode == AutoTransferSettings.NOTIFY_SOUND_MODE_CUSTOM) "从文件选择铃声" else "选择系统铃声",
                optionItems("选择系统铃声" to AutoTransferSettings.NOTIFY_SOUND_MODE_SYSTEM, "从文件选择铃声" to AutoTransferSettings.NOTIFY_SOUND_MODE_CUSTOM),
                soundMode,
                onSoundMode
            )
            InsetDivider()
            ActionRow("选择铃声", ringtoneDisplayName(context, soundUri, soundMode)) {
                val activity = context as? Activity
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开铃声选择器", Toast.LENGTH_SHORT).show()
                } else {
                    val picked: (String) -> Unit = { uri ->
                        if (uri.isNotBlank()) {
                            onSoundUri(
                                if (soundMode == AutoTransferSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                                    CustomNotificationRuntime.freezeRingtoneUri(context, uri)
                                } else uri
                            )
                        }
                    }
                    if (soundMode == AutoTransferSettings.NOTIFY_SOUND_MODE_CUSTOM) {
                        RingtonePickerBridge.launchFile(activity, picked)
                    } else {
                        RingtonePickerBridge.launchSystem(activity, soundUri, picked)
                    }
                }
            }
        }
        InsetDivider()
        SwitchRow(vibrate, "通知震动", "开启后触发通知震动") { onVibrate(it) }
        InsetDivider()
        SwitchRow(announce, "收款语音播报", "收款请求成功后用系统语音播报") { onAnnounce(it) }
        if (announce) {
            InsetDivider(); VariableInputRow("播报文案", "支持下方变量", announceText, transferTemplateVariables, onValueChange = onAnnounceText)
        }
    }
}

@Composable
internal fun FakeWalletBalanceMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FakeWalletBalanceSettings.PREFS_NAME) }
    var balanceEnabled by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.isAccountEnabled(
                sp,
                FakeWalletBalanceSettings.KEY_BALANCE_ENABLE
            )
        )
    }
    var lqtEnabled by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.isAccountEnabled(
                sp,
                FakeWalletBalanceSettings.KEY_LQT_ENABLE
            )
        )
    }
    var businessEnabled by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.isAccountEnabled(
                sp,
                FakeWalletBalanceSettings.KEY_BUSINESS_ENABLE
            )
        )
    }
    val storedBalanceAmount = remember {
        sp.getString(
            FakeWalletBalanceSettings.KEY_BALANCE_AMOUNT,
            FakeWalletBalanceSettings.DEFAULT_AMOUNT
        ) ?: FakeWalletBalanceSettings.DEFAULT_AMOUNT
    }
    val storedLqtAmount = remember {
        sp.getString(
            FakeWalletBalanceSettings.KEY_LQT_AMOUNT,
            FakeWalletBalanceSettings.DEFAULT_AMOUNT
        ) ?: FakeWalletBalanceSettings.DEFAULT_AMOUNT
    }
    val storedBusinessAmount = remember {
        sp.getString(FakeWalletBalanceSettings.KEY_BUSINESS_AMOUNT, null)
            ?: storedLqtAmount
    }
    var balanceAmount by remember {
        mutableStateOf(FakeWalletBalanceSettings.normalizeAmount(storedBalanceAmount))
    }
    var lqtAmount by remember {
        mutableStateOf(FakeWalletBalanceSettings.normalizeAmount(storedLqtAmount))
    }
    var businessAmount by remember {
        mutableStateOf(FakeWalletBalanceSettings.normalizeAmount(storedBusinessAmount))
    }
    var balanceMode by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.amountMode(
                sp,
                FakeWalletBalanceSettings.KEY_BALANCE_MODE,
                storedBalanceAmount
            )
        )
    }
    var lqtMode by remember {
        mutableStateOf(
            FakeWalletBalanceSettings.amountMode(
                sp,
                FakeWalletBalanceSettings.KEY_LQT_MODE,
                storedLqtAmount
            )
        )
    }
    var businessMode by remember {
        val fallbackMode = if (!sp.contains(FakeWalletBalanceSettings.KEY_BUSINESS_AMOUNT)) {
            FakeWalletBalanceSettings.amountMode(
                sp,
                FakeWalletBalanceSettings.KEY_LQT_MODE,
                storedLqtAmount
            )
        } else {
            FakeWalletBalanceSettings.DEFAULT_MODE
        }
        mutableStateOf(
            FakeWalletBalanceSettings.amountMode(
                sp,
                FakeWalletBalanceSettings.KEY_BUSINESS_MODE,
                storedBusinessAmount,
                fallbackMode
            )
        )
    }
    val amountModeOptions = remember {
        listOf(
            PopupChoice("固定金额", FakeWalletBalanceSettings.MODE_FIXED),
            PopupChoice("增加金额", FakeWalletBalanceSettings.MODE_INCREASE),
            PopupChoice("减少金额", FakeWalletBalanceSettings.MODE_DECREASE)
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
                    val normalizedBalance = FakeWalletBalanceSettings.normalizeAmount(balanceAmount)
                    val normalizedLqt = FakeWalletBalanceSettings.normalizeAmount(lqtAmount)
                    val normalizedBusiness = FakeWalletBalanceSettings.normalizeAmount(businessAmount)
                    balanceAmount = normalizedBalance
                    lqtAmount = normalizedLqt
                    businessAmount = normalizedBusiness
                    sp.edit()
                        .putString(FakeWalletBalanceSettings.KEY_BALANCE_AMOUNT, normalizedBalance)
                        .putString(FakeWalletBalanceSettings.KEY_LQT_AMOUNT, normalizedLqt)
                        .putString(FakeWalletBalanceSettings.KEY_BUSINESS_AMOUNT, normalizedBusiness)
                        .putString(FakeWalletBalanceSettings.KEY_BALANCE_MODE, balanceMode)
                        .putString(FakeWalletBalanceSettings.KEY_LQT_MODE, lqtMode)
                        .putString(FakeWalletBalanceSettings.KEY_BUSINESS_MODE, businessMode)
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
            item { SmallTitle(text = "显示") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = balanceEnabled,
                        title = "余额",
                        summary = "固定显示或按真实金额动态增减"
                    ) { enabled ->
                        balanceEnabled = enabled
                        sp.edit().putBoolean(FakeWalletBalanceSettings.KEY_BALANCE_ENABLE, enabled).apply()
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = lqtEnabled,
                        title = "零钱通",
                        summary = "固定显示或按真实金额动态增减"
                    ) { enabled ->
                        lqtEnabled = enabled
                        sp.edit().putBoolean(FakeWalletBalanceSettings.KEY_LQT_ENABLE, enabled).apply()
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = businessEnabled,
                        title = "经营账户",
                        summary = "固定显示或按真实金额动态增减"
                    ) { enabled ->
                        businessEnabled = enabled
                        sp.edit().putBoolean(FakeWalletBalanceSettings.KEY_BUSINESS_ENABLE, enabled).apply()
                    }
                }
            }
            if (balanceEnabled || lqtEnabled || businessEnabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "金额") }
                item {
                    SettingsCard {
                        if (balanceEnabled) {
                            PopupChoiceRow(
                                title = "余额显示方式",
                                summary = "选择固定显示或按真实余额计算",
                                options = amountModeOptions,
                                currentValue = balanceMode,
                                onValueChanged = { balanceMode = it }
                            )
                            InsetDivider()
                            InputRow(
                                when (balanceMode) {
                                    FakeWalletBalanceSettings.MODE_INCREASE -> "余额增加金额"
                                    FakeWalletBalanceSettings.MODE_DECREASE -> "余额减少金额"
                                    else -> "余额金额"
                                },
                                if (balanceMode == FakeWalletBalanceSettings.MODE_FIXED) {
                                    "直接显示该金额"
                                } else {
                                    "基于微信真实余额计算"
                                },
                                balanceAmount
                            ) { value ->
                                balanceAmount = value.filter { it.isDigit() || it == '.' || it == ',' }
                            }
                        }
                        if (balanceEnabled && (lqtEnabled || businessEnabled)) {
                            InsetDivider()
                        }
                        if (lqtEnabled) {
                            PopupChoiceRow(
                                title = "零钱通显示方式",
                                summary = "选择固定显示或按真实金额计算",
                                options = amountModeOptions,
                                currentValue = lqtMode,
                                onValueChanged = { lqtMode = it }
                            )
                            InsetDivider()
                            InputRow(
                                when (lqtMode) {
                                    FakeWalletBalanceSettings.MODE_INCREASE -> "零钱通增加金额"
                                    FakeWalletBalanceSettings.MODE_DECREASE -> "零钱通减少金额"
                                    else -> "零钱通金额"
                                },
                                if (lqtMode == FakeWalletBalanceSettings.MODE_FIXED) {
                                    "直接显示该金额"
                                } else {
                                    "基于微信真实金额计算"
                                },
                                lqtAmount
                            ) { value ->
                                lqtAmount = value.filter { it.isDigit() || it == '.' || it == ',' }
                            }
                        }
                        if (lqtEnabled && businessEnabled) {
                            InsetDivider()
                        }
                        if (businessEnabled) {
                            PopupChoiceRow(
                                title = "经营账户显示方式",
                                summary = "选择固定显示或按真实金额计算",
                                options = amountModeOptions,
                                currentValue = businessMode,
                                onValueChanged = { businessMode = it }
                            )
                            InsetDivider()
                            InputRow(
                                when (businessMode) {
                                    FakeWalletBalanceSettings.MODE_INCREASE -> "经营账户增加金额"
                                    FakeWalletBalanceSettings.MODE_DECREASE -> "经营账户减少金额"
                                    else -> "经营账户金额"
                                },
                                if (businessMode == FakeWalletBalanceSettings.MODE_FIXED) {
                                    "直接显示该金额"
                                } else {
                                    "基于微信真实金额计算"
                                },
                                businessAmount
                            ) { value ->
                                businessAmount = value.filter { it.isDigit() || it == '.' || it == ',' }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MemberTitleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MemberTitleSettings.PREFS_NAME) }
    var ownerTitle by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_OWNER_TITLE, MemberTitleSettings.DEFAULT_OWNER_TITLE) ?: MemberTitleSettings.DEFAULT_OWNER_TITLE) }
    var adminTitle by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_ADMIN_TITLE, MemberTitleSettings.DEFAULT_ADMIN_TITLE) ?: MemberTitleSettings.DEFAULT_ADMIN_TITLE) }
    var memberTitle by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_MEMBER_TITLE, MemberTitleSettings.DEFAULT_MEMBER_TITLE) ?: MemberTitleSettings.DEFAULT_MEMBER_TITLE) }
    var ownerColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_OWNER_COLOR, MemberTitleSettings.DEFAULT_OWNER_COLOR) ?: MemberTitleSettings.DEFAULT_OWNER_COLOR) }
    var adminColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_ADMIN_COLOR, MemberTitleSettings.DEFAULT_ADMIN_COLOR) ?: MemberTitleSettings.DEFAULT_ADMIN_COLOR) }
    var memberColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_MEMBER_COLOR, MemberTitleSettings.DEFAULT_MEMBER_COLOR) ?: MemberTitleSettings.DEFAULT_MEMBER_COLOR) }
    var customColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_CUSTOM_COLOR, MemberTitleSettings.DEFAULT_CUSTOM_COLOR) ?: MemberTitleSettings.DEFAULT_CUSTOM_COLOR) }
    var ownerTextColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_OWNER_TEXT_COLOR, MemberTitleSettings.DEFAULT_TEXT_COLOR) ?: MemberTitleSettings.DEFAULT_TEXT_COLOR) }
    var adminTextColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_ADMIN_TEXT_COLOR, MemberTitleSettings.DEFAULT_TEXT_COLOR) ?: MemberTitleSettings.DEFAULT_TEXT_COLOR) }
    var memberTextColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_MEMBER_TEXT_COLOR, MemberTitleSettings.DEFAULT_TEXT_COLOR) ?: MemberTitleSettings.DEFAULT_TEXT_COLOR) }
    var customTextColor by remember { mutableStateOf(sp.getString(MemberTitleSettings.KEY_CUSTOM_TEXT_COLOR, MemberTitleSettings.DEFAULT_TEXT_COLOR) ?: MemberTitleSettings.DEFAULT_TEXT_COLOR) }
    var showMember by remember { mutableStateOf(sp.getBoolean(MemberTitleSettings.KEY_SHOW_MEMBER, MemberTitleSettings.DEFAULT_SHOW_MEMBER)) }
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
                        .putString(MemberTitleSettings.KEY_OWNER_TITLE, MemberTitleStore.cleanTitle(ownerTitle).ifEmpty { MemberTitleSettings.DEFAULT_OWNER_TITLE })
                        .putString(MemberTitleSettings.KEY_ADMIN_TITLE, MemberTitleStore.cleanTitle(adminTitle).ifEmpty { MemberTitleSettings.DEFAULT_ADMIN_TITLE })
                        .putString(MemberTitleSettings.KEY_MEMBER_TITLE, MemberTitleStore.cleanTitle(memberTitle).ifEmpty { MemberTitleSettings.DEFAULT_MEMBER_TITLE })
                        .putString(MemberTitleSettings.KEY_OWNER_COLOR, MemberTitleStore.cleanColorSpec(ownerColor).ifEmpty { MemberTitleSettings.DEFAULT_OWNER_COLOR })
                        .putString(MemberTitleSettings.KEY_ADMIN_COLOR, MemberTitleStore.cleanColorSpec(adminColor).ifEmpty { MemberTitleSettings.DEFAULT_ADMIN_COLOR })
                        .putString(MemberTitleSettings.KEY_MEMBER_COLOR, MemberTitleStore.cleanColorSpec(memberColor).ifEmpty { MemberTitleSettings.DEFAULT_MEMBER_COLOR })
                        .putString(MemberTitleSettings.KEY_CUSTOM_COLOR, MemberTitleStore.cleanColorSpec(customColor).ifEmpty { MemberTitleSettings.DEFAULT_CUSTOM_COLOR })
                        .putString(MemberTitleSettings.KEY_OWNER_TEXT_COLOR, MemberTitleStore.cleanColorSpec(ownerTextColor).ifEmpty { MemberTitleSettings.DEFAULT_TEXT_COLOR })
                        .putString(MemberTitleSettings.KEY_ADMIN_TEXT_COLOR, MemberTitleStore.cleanColorSpec(adminTextColor).ifEmpty { MemberTitleSettings.DEFAULT_TEXT_COLOR })
                        .putString(MemberTitleSettings.KEY_MEMBER_TEXT_COLOR, MemberTitleStore.cleanColorSpec(memberTextColor).ifEmpty { MemberTitleSettings.DEFAULT_TEXT_COLOR })
                        .putString(MemberTitleSettings.KEY_CUSTOM_TEXT_COLOR, MemberTitleStore.cleanColorSpec(customTextColor).ifEmpty { MemberTitleSettings.DEFAULT_TEXT_COLOR })
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
            item { SmallTitle(text = "基础") }
            item {
                SettingsCard {
                    SwitchRow(sp, MemberTitleSettings.KEY_ENABLE, "群员头衔", "在群聊昵称左侧显示身份头衔", MemberTitleSettings.DEFAULT_ENABLE)
                    InsetDivider()
                    SwitchRow(showMember, "显示普通群员", "关闭后只显示群主、管理员和自定义头衔") {
                        showMember = it
                        sp.edit().putBoolean(MemberTitleSettings.KEY_SHOW_MEMBER, it).apply()
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "名称") }
            item {
                SettingsCard {
                    InputRow("群主名称", "最多 8 个字符", ownerTitle) { ownerTitle = it.take(8) }
                    InsetDivider()
                    InputRow("管理员名称", "最多 8 个字符", adminTitle) { adminTitle = it.take(8) }
                    if (showMember) {
                        InsetDivider()
                        InputRow("群员名称", "最多 8 个字符", memberTitle) { memberTitle = it.take(8) }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "颜色") }
            item {
                SettingsCard {
                    ColorPickerRow("群主颜色", "默认黄色，渐变用 #F59E0B,#FDE047", ownerColor, onReset = { ownerColor = MemberTitleSettings.DEFAULT_OWNER_COLOR }) { ownerColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow("群主文字颜色", "默认白色，支持渐变", ownerTextColor, onReset = { ownerTextColor = MemberTitleSettings.DEFAULT_TEXT_COLOR }) { ownerTextColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow("管理员颜色", "默认绿色，渐变用 #22C55E,#14B8A6", adminColor, onReset = { adminColor = MemberTitleSettings.DEFAULT_ADMIN_COLOR }) { adminColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow("管理员文字颜色", "默认白色，支持渐变", adminTextColor, onReset = { adminTextColor = MemberTitleSettings.DEFAULT_TEXT_COLOR }) { adminTextColor = it.take(19) }
                    if (showMember) {
                        InsetDivider()
                        ColorPickerRow("群员颜色", "默认灰色，渐变用 #64748B,#94A3B8", memberColor, onReset = { memberColor = MemberTitleSettings.DEFAULT_MEMBER_COLOR }) { memberColor = it.take(19) }
                        InsetDivider()
                        ColorPickerRow("群员文字颜色", "默认白色，支持渐变", memberTextColor, onReset = { memberTextColor = MemberTitleSettings.DEFAULT_TEXT_COLOR }) { memberTextColor = it.take(19) }
                    }
                    InsetDivider()
                    ColorPickerRow("自定义默认色", "点击聊天头衔可单独设置每个人的名称和颜色，支持渐变", customColor, onReset = { customColor = MemberTitleSettings.DEFAULT_CUSTOM_COLOR }) { customColor = it.take(19) }
                    InsetDivider()
                    ColorPickerRow("自定义文字默认色", "单人未设置文字颜色时使用，支持渐变", customTextColor, onReset = { customTextColor = MemberTitleSettings.DEFAULT_TEXT_COLOR }) { customTextColor = it.take(19) }
                }
            }
        }
    }
}
