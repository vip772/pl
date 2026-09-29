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
internal fun ConfigBackupCard(
    context: Context,
    onImported: () -> Unit
) {
    val activity = context as? Activity
    SettingsCard {
        ActionRow(
            title = "导出配置",
            summary = "",
            onClick = {
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件管理器", Toast.LENGTH_SHORT).show()
                } else {
                    ConfigImportExportBridge.launchExport(activity)
                }
            }
        )
        InsetDivider()
        ActionRow(
            title = "导入配置",
            summary = "",
            onClick = {
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件管理器", Toast.LENGTH_SHORT).show()
                } else {
                    ConfigImportExportBridge.launchImport(activity, onImported)
                }
            }
        )
    }
}

@Composable
internal fun GroupLinksCard(context: Context) {
    SettingsCard {
        InfoRow(label = "Telegram Channel", value = "Hchat_ci", onClick = {
            openExternalLink(context, "https://t.me/Hchat_ci")
        })
        InsetDivider()
        InfoRow(label = "Telegram Group", value = "Hchat_Group", onClick = {
            openExternalLink(context, "https://t.me/Hchat_Group")
        })
    }
}

@Composable
internal fun ThanksCard(context: Context) {
    SettingsCard {
        InfoRow(label = "KavaRef", value = "HighCapable", onClick = {
            openExternalLink(context, "https://github.com/HighCapable/KavaRef")
        })
        InsetDivider()
        InfoRow(label = "DexKit", value = "LuckyPray", onClick = {
            openExternalLink(context, "https://github.com/LuckyPray/DexKit")
        })
        InsetDivider()
        InfoRow(label = "FastKV", value = "BillyWei01", onClick = {
            openExternalLink(context, "https://github.com/BillyWei01/FastKV")
        })
        InsetDivider()
        InfoRow(label = "WeChat Pad", value = "lovejiuwu", onClick = {
            openExternalLink(context, "https://github.com/Xposed-Modules-Repo/top.hookvip.wxtablet")
        })
        InsetDivider()
        InfoRow(label = "LSPosed", value = "LSPosed", onClick = {
            openExternalLink(context, "https://github.com/LSPosed/LSPosed")
        })
        InsetDivider()
        InfoRow(label = "Miuix", value = "YuKongA", onClick = {
            openExternalLink(context, "https://github.com/compose-miuix-ui/miuix")
        })
        InsetDivider()
        InfoRow(label = "BeanShell-Android", value = "CopyLibs", onClick = {
            openExternalLink(context, "https://github.com/CopyLibs/BeanShell-Android")
        })
        InsetDivider()
        InfoRow(label = "Silk Codec", value = "YunJavaPro", onClick = {
            openExternalLink(context, "https://github.com/YunJavaPro/Silk-Codec-Android")
        })
    }
}

@Composable
internal fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp)
    } else {
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = value, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, textAlign = TextAlign.End)
            if (onClick != null) {
                Text(
                    text = "›",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

@Composable
internal fun XiaozhiMcpStatusRow(status: XiaozhiMcpStatus) {
    val dotColor = if (status.connected) Color(0xFF20C05C) else Color(0xFF8C8C8C)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "连接状态",
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(dotColor)
            )
            Text(
                text = status.label,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

internal fun openExternalLink(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal fun extractFirstHttpUrl(text: String): String? {
    val matcher = android.util.Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val url = matcher.group().orEmpty()
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            return url
        }
    }
    return null
}

@Composable
internal fun EmptyText(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
        text = text,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Center
    )
}

@Composable
public fun InsetDivider(start: Dp = 16.dp) {
    Box(
        modifier = Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(Dp.Hairline)
            .background(MiuixTheme.colorScheme.dividerLine)
    )
}

class EmbeddedComposeOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner,
    NavigationEventDispatcherOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val navigationDispatcher = NavigationEventDispatcher()
    private val composeRuntime = lazy(LazyThreadSafetyMode.NONE) {
        EmbeddedComposeRuntime(lifecycleRegistry)
    }
    private var restored = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val navigationEventDispatcher: NavigationEventDispatcher
        get() = navigationDispatcher

    fun install(view: View) {
        EmbeddedComposeOwnerInstaller.install(view, this, this, this, this)
    }

    fun clear(view: View) {
        EmbeddedComposeOwnerInstaller.clear(view)
    }

    fun installComposition(view: ComposeView) {
        composeRuntime.value.install(view)
    }

    fun attach() {
        if (!restored) {
            savedStateRegistryController.performRestore(Bundle.EMPTY)
            restored = true
        }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        if (composeRuntime.isInitialized()) composeRuntime.value.destroy()
        navigationDispatcher.dispose()
        store.clear()
    }
}

internal fun firstTemplate(value: String): String {
    return value.split('|').firstOrNull { it.trim().isNotEmpty() }?.trim() ?: value.trim()
}

internal fun normalizeAntiRecallNoticeText(value: String): String {
    return if (value == AntiRecallSettings.LEGACY_NOTICE_TEXT) {
        AntiRecallSettings.DEFAULT_NOTICE_TEXT
    } else {
        value
    }
}

internal enum class ContactPickerMode {
    FRIENDS,
    GROUPS,
    BOTH,
    ALL_CHATS
}

internal fun ContactPickerMode.supportsGroups(): Boolean {
    return this == ContactPickerMode.GROUPS ||
        this == ContactPickerMode.BOTH ||
        this == ContactPickerMode.ALL_CHATS
}

internal enum class ContactPickerFilter(
    val label: String,
    val title: String
) {
    FRIENDS("好友", "好友"),
    GROUPS("群聊", "群聊"),
    OFFICIALS("公众号", "公众号"),
    LABELS("标签", "标签好友"),
    ALL("全部", "全部")
}

internal enum class ContactPickerScopeTab(val label: String) {
    FRIENDS("好友"),
    LABELS("标签"),
    CONVERSATION_GROUPS("聊天分组")
}

internal data class ContactPickerScope(
    val name: String,
    val memberIds: Set<String>
)

internal enum class ConversationRuleCategory(val label: String) {
    OFFICIAL("公众号"),
    GROUP("群聊"),
    FRIEND("好友"),
    ALL("全部")
}

internal enum class MessageBlockContactKind {
    FRIEND,
    GROUP,
    OFFICIAL
}

internal enum class MessageBlockContactFilter(
    val label: String,
    val title: String
) {
    FRIENDS("好友", "好友"),
    GROUPS("群聊", "群聊"),
    OFFICIALS("公众号", "公众号"),
    LABELS("标签", "标签好友"),
    ALL("全部", "全部")
}

internal data class ContactPickerRequest(
    val title: String,
    val mode: ContactPickerMode,
    val multiSelect: Boolean,
    val existingValue: String,
    val onValue: (String) -> Unit,
    val enableLabels: Boolean = false,
    val enableGroupLabels: Boolean = true,
    val enableScopeSelection: Boolean = false,
    val singleConfirmText: String = "发送"
)

internal enum class AutoMessageForwardRoute {
    MAIN,
    RULES,
    EDITOR,
    CONTACTS,
    MEMBERS,
    TYPES,
    REPLACEMENTS
}

internal enum class AutoMessageForwardContactField {
    SOURCES,
    TARGETS
}

internal data class FavoritePickerRequest(
    val title: String,
    val existingValue: String,
    val onValue: (String) -> Unit,
    val multiSelect: Boolean = true,
    val delimiter: String = "|"
)

internal enum class FavoritePickerFilter(val label: String) {
    IMAGE("图片"),
    TEXT("文字"),
    VOICE("语音"),
    VIDEO("视频"),
    OTHER("其他"),
    ALL("全部");

    fun matches(type: Int): Boolean = when (this) {
        IMAGE -> type == 2
        TEXT -> type == 1
        VOICE -> type == 3
        VIDEO -> type == 4
        OTHER -> type !in setOf(1, 2, 3, 4)
        ALL -> true
    }
}

internal data class FavoriteMediaPreview(
    val item: WeChatFavoriteItem,
    val path: String
)

internal data class MessageBlockContactPickerRequest(
    val title: String,
    val existingValue: String,
    val onValue: (String) -> Unit,
    val allowOfficialAccounts: Boolean = true
)

internal data class GroupMemberPickerRequest(
    val title: String,
    val existingValue: String,
    val onValue: (String) -> Unit,
    val allowedGroupIds: Set<String>? = null
)

internal data class RedPacketTemplateEditorRequest(
    val index: Int,
    val template: RedPacketRuleTemplate,
    val canDelete: Boolean
)

internal data class RedPacketBindingEditorRequest(
    val index: Int,
    val binding: RedPacketRuleBinding,
    val canDelete: Boolean
)

internal data class TransferTemplateEditorRequest(
    val index: Int,
    val template: TransferRuleTemplate,
    val canDelete: Boolean
)

internal data class TransferBindingEditorRequest(
    val index: Int,
    val binding: TransferRuleBinding,
    val canDelete: Boolean
)

internal data class MessageBlockTemplateEditorRequest(
    val index: Int,
    val template: MessageBlockTemplate,
    val canDelete: Boolean
)

internal data class MessageBlockBindingEditorRequest(
    val index: Int,
    val binding: MessageBlockBinding,
    val canDelete: Boolean
)

internal data class MessageBlockBatchBindingEditorRequest(
    val title: String,
    val bindings: List<MessageBlockBinding>
)

internal data class GroupLeaveTemplateEditorRequest(
    val index: Int,
    val template: GroupLeaveReplyTemplate,
    val canDelete: Boolean
)

internal data class MessageBlockTypeOption(
    val key: String,
    val title: String,
    val summary: String
)

internal data class MessageBlockRuleState(
    val typeAll: Boolean,
    val types: Set<String>,
    val textKeywords: String
)

internal data class OptionPickerRequest(
    val title: String,
    val options: List<OptionItem>,
    val currentValue: Int,
    val onSelected: (OptionItem) -> Unit
)

internal data class OptionItem(
    val label: String,
    val value: Int,
    val summary: String = ""
)

internal data class PopupChoice<T>(
    val label: String,
    val value: T
)

internal fun optionItems(vararg items: Pair<String, Int>): List<OptionItem> {
    return items.map { OptionItem(label = it.first, value = it.second) }
}

internal fun ringtoneDisplayName(context: Context, uri: String, mode: Int): String {
    if (uri.isBlank()) return "跟随系统"
    val parsed = try {
        Uri.parse(uri)
    } catch (_: Throwable) {
        null
    }
    if (mode == RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM) {
        queryDisplayName(context, parsed)?.let { return it }
    }
    return try {
        val ringtone = parsed?.let { RingtoneManager.getRingtone(context, it) }
        ringtone?.getTitle(context)?.takeIf { it.isNotBlank() }
            ?: queryDisplayName(context, parsed)
            ?: "已选择自定义铃声"
    } catch (_: Throwable) {
        queryDisplayName(context, parsed)
            ?: "已选择自定义铃声"
    }
}

internal fun queryDisplayName(context: Context, uri: Uri?): String? {
    if (uri == null) return null
    if ("content".equals(uri.scheme, ignoreCase = true)) {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            return cursor.getString(index)?.takeIf { it.isNotBlank() }
                        }
                    }
                }
        } catch (_: Throwable) {
        }
    }
    return uri.lastPathSegment
        ?.let { Uri.decode(it) }
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.takeIf { it.isNotBlank() }
}

internal data class ContactOption(
    val id: String,
    val label: String,
    val group: Boolean,
    val avatarUrl: String,
    val avatarBackupUrl: String,
    val labels: List<String> = emptyList(),
    val official: Boolean = false,
    val extraSummary: String = "",
    val searchAliases: List<String> = emptyList()
)

internal fun ContactOption.matchesSearch(lower: String): Boolean {
    return lower.isEmpty() ||
        label.lowercase(Locale.US).contains(lower) ||
        id.lowercase(Locale.US).contains(lower) ||
        labels.any { it.lowercase(Locale.US).contains(lower) } ||
        extraSummary.lowercase(Locale.US).contains(lower) ||
        searchAliases.any { it.lowercase(Locale.US).contains(lower) }
}

internal data class MessageBlockContactOption(
    val contact: ContactOption,
    val kind: MessageBlockContactKind,
    val labelNames: List<String> = emptyList()
)

internal data class MessageBlockLabelOption(
    val id: String,
    val name: String,
    val contactIds: List<String>
)

internal data class MessageBlockContactData(
    val contacts: List<MessageBlockContactOption> = emptyList(),
    val labels: List<MessageBlockLabelOption> = emptyList()
)

internal data class MessageBlockInitialState(
    val templates: List<MessageBlockTemplate>,
    val bindings: List<MessageBlockBinding>,
    val shouldPersist: Boolean
)

internal fun newRedPacketTemplate(index: Int, sp: SharedPreferences): RedPacketRuleTemplate {
    val replyEnabled = sp.getBoolean(RedPacketSettings.KEY_REPLY_ENABLE, false)
    val storedReplyMode = if (replyEnabled) {
        sp.getInt(RedPacketSettings.KEY_REPLY_TYPE, RedPacketRuleConfig.REPLY_TEXT)
    } else {
        RedPacketRuleConfig.REPLY_OFF
    }
    val replySteps = loadGlobalRedPacketReplySteps(sp)
    val firstReply = replySteps.firstOrNull()
    val legacyReplyText = if (redPacketReplyUsesText(storedReplyMode)) {
        sp.getString(
            RedPacketSettings.KEY_REPLY_TEMPLATES,
            sp.getString(RedPacketSettings.KEY_REPLY_TEXT, "谢谢老板") ?: "谢谢老板"
        ) ?: "谢谢老板"
    } else {
        sp.getString(RedPacketSettings.KEY_REPLY_MEDIA_PATHS, "") ?: ""
    }
    return RedPacketRuleTemplate(
        id = System.currentTimeMillis().toString() + "_" + index,
        name = "模板 $index",
        enabled = false,
        grabMode = sp.getInt(RedPacketSettings.KEY_GRAB_MODE, RedPacketSettings.DEFAULT_GRAB_MODE),
        delayMode = redPacketDelayModeFromPrefs(sp),
        delayMs = redPacketDelayMillisFromPrefs(sp),
        randomMinMs = sp.getInt(RedPacketSettings.KEY_DELAY_RANDOM_MIN, 500).coerceAtLeast(0).toLong(),
        randomMaxMs = sp.getInt(RedPacketSettings.KEY_DELAY_RANDOM_MAX, 3000).coerceAtLeast(0).toLong(),
        skipSelf = sp.getBoolean(RedPacketSettings.KEY_SKIP_SELF, false),
        listMode = 0,
        whitelist = "",
        blacklist = "",
        keywordMode = sp.getInt(RedPacketSettings.KEY_KW_MODE, 0),
        keywords = sp.getString(RedPacketSettings.KEY_KEYWORDS, "") ?: "",
        quietEnabled = false,
        quietStartSecond = 0,
        quietEndSecond = 0,
        replyMode = firstReply?.mode ?: normalizeRedPacketReplyModeForUi(storedReplyMode),
        replyText = firstReply?.content ?: normalizeRedPacketReplyTextForUi(storedReplyMode, legacyReplyText),
        replyDelayMs = firstReply?.delayMs ?: redPacketReplyDelayMillisFromPrefs(sp),
        replyRandom = firstReply?.random ?: sp.getBoolean(RedPacketSettings.KEY_REPLY_RANDOM, false),
        replySteps = replySteps,
        groupReplySteps = loadGlobalGroupRedPacketReplySteps(sp),
        notificationConfigured = true,
        notifySystemEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SYSTEM_ENABLE, false),
        notifyToastEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_TOAST_ENABLE, false),
        notifySoundEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_SOUND_ENABLE, false),
        notifySoundMode = sp.getInt(RedPacketSettings.KEY_NOTIFY_SOUND_MODE, RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM),
        notifyVibrateEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_VIBRATE_ENABLE, false),
        notifySoundUri = sp.getString(RedPacketSettings.KEY_NOTIFY_SOUND_URI, "") ?: "",
        notifyText = sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元",
        notifyToastText = sp.getString(
            RedPacketSettings.KEY_NOTIFY_TOAST_TEXT,
            sp.getString(RedPacketSettings.KEY_NOTIFY_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
        ) ?: "抢到红包 {amount} 元",
        notifyFailedSystemEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_SYSTEM_ENABLE, false),
        notifyFailedToastEnabled = sp.getBoolean(RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_ENABLE, false),
        notifyFailedText = sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "未抢到红包",
        notifyFailedToastText = sp.getString(
            RedPacketSettings.KEY_NOTIFY_FAILED_TOAST_TEXT,
            sp.getString(RedPacketSettings.KEY_NOTIFY_FAILED_TEXT, "未抢到红包") ?: "未抢到红包"
        ) ?: "未抢到红包",
        announceEnabled = sp.getBoolean(RedPacketSettings.KEY_ANNOUNCE_ENABLE, false),
        announceText = sp.getString(RedPacketSettings.KEY_ANNOUNCE_TEXT, "抢到红包 {amount} 元") ?: "抢到红包 {amount} 元"
    )
}

internal fun transferReceiveAccountOptions(context: Context): List<PopupChoice<String>> {
    return listOf(
        PopupChoice("零钱", TransferReceiveAccountStore.DEFAULT_KEY),
        PopupChoice("零钱通", TransferReceiveAccountStore.LQT_KEY),
        PopupChoice("经营账户", TransferReceiveAccountStore.BUSINESS_KEY)
    ) + TransferReceiveAccountStore.list(context)
        .filter { it.available }
        .filterNot {
            val name = it.name.replace(" ", "")
            name == "零钱" || name.contains("零钱通") || name.contains("经营") || name.contains("商户")
        }
        .map { PopupChoice(it.name, it.key) }
}

internal fun loadGlobalTransferReplySteps(sp: SharedPreferences): List<RedPacketReplyStep> {
    val stored = sp.getString(AutoTransferSettings.KEY_REPLY_ITEMS, "").orEmpty()
    if (stored.isNotBlank()) return RedPacketRuleConfig.parseReplySteps(stored).map(::normalizeRedPacketReplyStepForUi)
    if (!sp.getBoolean(AutoTransferSettings.KEY_REPLY_ENABLE, false)) return emptyList()
    return RedPacketRuleConfig.legacyReplySteps(
        RedPacketRuleConfig.REPLY_TEXT,
        sp.getString(AutoTransferSettings.KEY_REPLY_TEXT, "谢谢老板") ?: "谢谢老板",
        1000L,
        false
    )
}

internal fun loadGlobalGroupTransferReplySteps(sp: SharedPreferences): List<RedPacketReplyStep> {
    if (!sp.contains(AutoTransferSettings.KEY_REPLY_GROUP_ITEMS)) {
        return loadGlobalTransferReplySteps(sp)
    }
    return RedPacketRuleConfig.parseReplySteps(
        sp.getString(AutoTransferSettings.KEY_REPLY_GROUP_ITEMS, "") ?: ""
    ).map(::normalizeRedPacketReplyStepForUi)
}

internal fun newTransferTemplate(index: Int, sp: SharedPreferences): TransferRuleTemplate {
    val minDelay = sp.getLong(AutoTransferSettings.KEY_DELAY_RANDOM_MIN, 500L).coerceIn(0L, 600000L)
    val maxDelay = sp.getLong(AutoTransferSettings.KEY_DELAY_RANDOM_MAX, 3000L).coerceIn(minDelay, 600000L)
    return TransferRuleTemplate(
        id = "transfer_${System.currentTimeMillis()}_$index",
        name = "收款模板 $index",
        enabled = true,
        delayMode = sp.getInt(AutoTransferSettings.KEY_DELAY_MODE, TransferRuleConfig.DELAY_CUSTOM),
        delayMs = sp.getLong(AutoTransferSettings.KEY_DELAY_MS, 0L).coerceIn(0L, 600000L),
        randomMinMs = minDelay,
        randomMaxMs = maxDelay,
        receiveAccount = sp.getString(AutoTransferSettings.KEY_RECEIVE_ACCOUNT, TransferReceiveAccountStore.DEFAULT_KEY)
            ?: TransferReceiveAccountStore.DEFAULT_KEY,
        listMode = sp.getInt(AutoTransferSettings.KEY_MODE, 0),
        whitelist = sp.getString(AutoTransferSettings.KEY_WHITELIST, "").orEmpty(),
        blacklist = sp.getString(AutoTransferSettings.KEY_BLACKLIST, "").orEmpty(),
        amountEnabled = sp.getBoolean(AutoTransferSettings.KEY_AMOUNT_ENABLE, false),
        amountCondition = sp.getInt(AutoTransferSettings.KEY_AMOUNT_COND, 1),
        amountValue = sp.getString(AutoTransferSettings.KEY_AMOUNT_VALUE, "0") ?: "0",
        amountAction = sp.getInt(AutoTransferSettings.KEY_AMOUNT_ACTION, 0),
        keywordMode = sp.getInt(AutoTransferSettings.KEY_KEYWORD_MODE, 0),
        keywords = sp.getString(AutoTransferSettings.KEY_KEYWORDS, "").orEmpty(),
        quietEnabled = sp.getBoolean(AutoTransferSettings.KEY_QUIET_ENABLE, false),
        quietStartSecond = sp.getInt(AutoTransferSettings.KEY_QUIET_START_SECOND, 0),
        quietEndSecond = sp.getInt(AutoTransferSettings.KEY_QUIET_END_SECOND, 0),
        refundRejected = sp.getBoolean(AutoTransferSettings.KEY_REFUND_REJECTED, false),
        replySteps = loadGlobalTransferReplySteps(sp),
        groupReplySteps = loadGlobalGroupTransferReplySteps(sp),
        notificationConfigured = true,
        notifySystemEnabled = sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_SYSTEM_ENABLE, false),
        notifyToastEnabled = sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_TOAST_ENABLE, false),
        notifySoundEnabled = sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_SOUND_ENABLE, false),
        notifySoundMode = sp.getInt(AutoTransferSettings.KEY_NOTIFY_SOUND_MODE, AutoTransferSettings.NOTIFY_SOUND_MODE_SYSTEM),
        notifyVibrateEnabled = sp.getBoolean(AutoTransferSettings.KEY_NOTIFY_VIBRATE_ENABLE, false),
        notifySoundUri = sp.getString(AutoTransferSettings.KEY_NOTIFY_SOUND_URI, "").orEmpty(),
        notifyText = sp.getString(AutoTransferSettings.KEY_NOTIFY_TEXT, "已收款 {amount} 元") ?: "已收款 {amount} 元",
        notifyToastText = sp.getString(AutoTransferSettings.KEY_NOTIFY_TOAST_TEXT, "已收款 {amount} 元") ?: "已收款 {amount} 元",
        announceEnabled = sp.getBoolean(AutoTransferSettings.KEY_ANNOUNCE_ENABLE, false),
        announceText = sp.getString(AutoTransferSettings.KEY_ANNOUNCE_TEXT, "收到转账 {amount} 元") ?: "收到转账 {amount} 元"
    )
}

internal fun describeTransferDefault(id: String, templates: List<TransferRuleTemplate>): String {
    if (id.isBlank()) return "旧版全局设置"
    return templates.firstOrNull { it.id == id }?.name ?: "模板不存在，使用全局设置"
}

internal fun describeTransferTemplate(value: TransferRuleTemplate): String {
    val state = if (value.enabled) "启用" else "关闭"
    val delay = when (value.delayMode) {
        TransferRuleConfig.DELAY_RANDOM -> "随机 ${value.randomMinMs}-${value.randomMaxMs}ms"
        TransferRuleConfig.DELAY_CUSTOM -> "延迟 ${value.delayMs}ms"
        else -> "无延迟"
    }
    val amount = if (value.amountEnabled) {
        "金额${transferAmountConditionLabel(value.amountCondition)}${value.amountValue}"
    } else null
    val quiet = if (value.quietEnabled) {
        "禁收 ${formatRedPacketSecond(value.quietStartSecond)}-${formatRedPacketSecond(value.quietEndSecond)}"
    } else null
    return listOfNotNull(
        state,
        delay,
        transferKeywordModeLabel(value.keywordMode),
        amount,
        quiet,
        "私聊 ${describeRedPacketReplySteps(value.replySteps)}",
        "群聊 ${describeRedPacketReplySteps(value.groupReplySteps ?: value.replySteps)}"
    ).joinToString(" · ")
}

internal fun describeTransferBinding(value: TransferRuleBinding, templates: List<TransferRuleTemplate>): String {
    val state = if (value.enabled) "启用" else "关闭"
    val template = templates.firstOrNull { it.id == value.templateId }?.name ?: "跟随默认规则"
    return "$state · $template · ${value.targetId}"
}

internal fun transferContactLabel(id: String): String = runCatching {
    WeChatApis.contact().contacts()?.getDisplayName(id).orEmpty().ifBlank { id }
}.getOrDefault(id)

internal fun upsertTransferBindings(
    current: List<TransferRuleBinding>,
    additions: List<TransferRuleBinding>
): List<TransferRuleBinding> {
    val result = linkedMapOf<String, TransferRuleBinding>()
    current.forEach { if (it.targetId.isNotBlank()) result[it.targetId.trim()] = it.copy(id = it.targetId.trim()) }
    additions.forEach {
        val id = it.targetId.trim()
        if (id.isNotBlank()) result[id] = it.copy(id = id, targetId = id, label = it.label.ifBlank { transferContactLabel(id) })
    }
    return result.values.toList()
}

internal fun transferDelayModeLabel(value: Int): String = when (value) {
    TransferRuleConfig.DELAY_RANDOM -> "随机延迟"
    TransferRuleConfig.DELAY_CUSTOM -> "自定义延迟"
    else -> "无延迟"
}

internal fun transferListModeLabel(value: Int): String = when (value) {
    1 -> "只接收白名单"
    2 -> "拒收黑名单"
    else -> "全部接收"
}

internal fun transferAmountConditionLabel(value: Int): String = when (value) {
    0 -> "大于"
    2 -> "等于"
    else -> "小于"
}

internal fun transferAmountActionLabel(value: Int): String = if (value == 1) "仅接收满足条件" else "拒收/忽略"

internal fun transferKeywordModeLabel(value: Int): String = when (value) {
    1 -> "必须包含关键词"
    2 -> "包含则拒收"
    else -> "不启用"
}

internal fun loadGlobalRedPacketReplySteps(sp: SharedPreferences): List<RedPacketReplyStep> {
    val stored = sp.getString(RedPacketSettings.KEY_REPLY_ITEMS, "") ?: ""
    if (stored.isNotBlank()) {
        return RedPacketRuleConfig.parseReplySteps(stored).map { normalizeRedPacketReplyStepForUi(it) }
    }
    val replyEnabled = sp.getBoolean(RedPacketSettings.KEY_REPLY_ENABLE, false)
    if (!replyEnabled) return emptyList()
    val replyMode = sp.getInt(RedPacketSettings.KEY_REPLY_TYPE, RedPacketRuleConfig.REPLY_TEXT)
    val content = if (redPacketReplyUsesText(replyMode)) {
        sp.getString(
            RedPacketSettings.KEY_REPLY_TEMPLATES,
            sp.getString(RedPacketSettings.KEY_REPLY_TEXT, "谢谢老板") ?: "谢谢老板"
        ) ?: "谢谢老板"
    } else {
        sp.getString(RedPacketSettings.KEY_REPLY_MEDIA_PATHS, "") ?: ""
    }
    return normalizeRedPacketReplyStepsForUi(
        RedPacketRuleConfig.legacyReplySteps(
            replyMode,
            content,
            redPacketReplyDelayMillisFromPrefs(sp),
            sp.getBoolean(RedPacketSettings.KEY_REPLY_RANDOM, false)
        ),
        RedPacketRuleConfig.REPLY_OFF,
        "",
        0L,
        false
    )
}

internal fun loadGlobalGroupRedPacketReplySteps(sp: SharedPreferences): List<RedPacketReplyStep> {
    if (!sp.contains(RedPacketSettings.KEY_REPLY_GROUP_ITEMS)) {
        return loadGlobalRedPacketReplySteps(sp)
    }
    return RedPacketRuleConfig.parseReplySteps(
        sp.getString(RedPacketSettings.KEY_REPLY_GROUP_ITEMS, "") ?: ""
    ).map(::normalizeRedPacketReplyStepForUi)
}

internal fun redPacketDelayMillisFromPrefs(sp: SharedPreferences): Long {
    val value = sp.getInt(RedPacketSettings.KEY_DELAY_VALUE, 0).coerceAtLeast(0)
    return if (sp.getInt(RedPacketSettings.KEY_DELAY_UNIT, 0) == 1) value * 1000L else value.toLong()
}

internal fun redPacketDelayModeFromPrefs(sp: SharedPreferences): Int {
    return when (val storedMode = sp.getInt(RedPacketSettings.KEY_DELAY_MODE, Int.MIN_VALUE)) {
        RedPacketRuleConfig.DELAY_FIXED,
        RedPacketRuleConfig.DELAY_CUSTOM,
        RedPacketRuleConfig.DELAY_RANDOM -> storedMode
        else -> if (redPacketDelayMillisFromPrefs(sp) > 0L) {
            RedPacketRuleConfig.DELAY_CUSTOM
        } else {
            RedPacketRuleConfig.DELAY_FIXED
        }
    }
}

internal fun redPacketReplyDelayMillisFromPrefs(sp: SharedPreferences): Long {
    if (!sp.getBoolean(RedPacketSettings.KEY_REPLY_CUSTOM_ENABLE, false)) return 0L
    val value = sp.getInt(RedPacketSettings.KEY_REPLY_DELAY_VALUE, 1).coerceAtLeast(0)
    return if (sp.getInt(RedPacketSettings.KEY_REPLY_DELAY_UNIT, 1) == 1) value * 1000L else value.toLong()
}

internal fun normalizeRedPacketDelayModeForUi(template: RedPacketRuleTemplate): Int {
    return when (template.delayMode) {
        RedPacketRuleConfig.DELAY_RANDOM -> RedPacketRuleConfig.DELAY_RANDOM
        RedPacketRuleConfig.DELAY_CUSTOM -> RedPacketRuleConfig.DELAY_CUSTOM
        else -> if (template.delayMs > 0L) RedPacketRuleConfig.DELAY_CUSTOM else RedPacketRuleConfig.DELAY_FIXED
    }
}

internal fun redPacketDelayModeLabel(mode: Int): String {
    return when (mode) {
        RedPacketRuleConfig.DELAY_CUSTOM -> "自定义延迟"
        RedPacketRuleConfig.DELAY_RANDOM -> "随机延迟"
        else -> "无延迟"
    }
}

internal fun redPacketDelayModeOptions(includeRandom: Boolean): List<OptionItem> {
    val options = mutableListOf(
        OptionItem("无延迟", RedPacketRuleConfig.DELAY_FIXED),
        OptionItem("自定义延迟", RedPacketRuleConfig.DELAY_CUSTOM)
    )
    if (includeRandom) options += OptionItem("随机延迟", RedPacketRuleConfig.DELAY_RANDOM)
    return options
}

internal fun describeRedPacketDefaultTemplate(
    defaultTemplateId: String,
    templates: List<RedPacketRuleTemplate>
): String {
    if (defaultTemplateId.isBlank()) return "旧版全局设置"
    val template = templates.firstOrNull { it.id == defaultTemplateId } ?: return "模板不存在，当前会回到旧版全局设置"
    return template.name.ifBlank { defaultTemplateId }
}

internal fun describeRedPacketTemplate(template: RedPacketRuleTemplate): String {
    val state = if (template.enabled) "启用" else "关闭"
    val mode = if (template.grabMode == 1) "静默" else "打开页面"
    val delay = when (normalizeRedPacketDelayModeForUi(template)) {
        RedPacketRuleConfig.DELAY_RANDOM -> {
            val minDelay = template.randomMinMs.coerceAtLeast(0L)
            val maxDelay = template.randomMaxMs.coerceAtLeast(minDelay)
            "随机 $minDelay-${maxDelay}ms"
        }
        RedPacketRuleConfig.DELAY_CUSTOM -> "延迟 ${template.delayMs.coerceAtLeast(0L)}ms"
        else -> "无延迟"
    }
    val keyword = when (template.keywordMode) {
        1 -> "只抢关键词"
        2 -> "屏蔽关键词"
        else -> "不限关键词"
    }
    val quiet = if (template.quietEnabled) {
        "禁抢 ${formatRedPacketSecond(template.quietStartSecond)}-${formatRedPacketSecond(template.quietEndSecond)}"
    } else {
        null
    }
    val privateReply = describeRedPacketReplySteps(
        normalizeRedPacketReplyStepsForUi(
            template.replySteps,
            template.replyMode,
            template.replyText,
            template.replyDelayMs,
            template.replyRandom
        )
    )
    val groupReply = describeRedPacketReplySteps(
        template.groupReplySteps
            ?.map(::normalizeRedPacketReplyStepForUi)
            ?: normalizeRedPacketReplyStepsForUi(
                template.replySteps,
                template.replyMode,
                template.replyText,
                template.replyDelayMs,
                template.replyRandom
            )
    )
    val notify = describeRedPacketTemplateNotify(template)
    return listOfNotNull(
        state,
        mode,
        delay,
        keyword,
        quiet,
        "私聊 $privateReply",
        "群聊 $groupReply",
        notify
    ).joinToString(" · ")
}

internal fun describeRedPacketTemplateNotify(template: RedPacketRuleTemplate): String? {
    if (!template.notificationConfigured) return "通知沿用全局"
    val items = buildList {
        if (template.notifySystemEnabled) add("通知栏")
        if (template.notifyToastEnabled) add("浮窗")
        if (template.notifySoundEnabled) add("铃声")
        if (template.notifyVibrateEnabled) add("震动")
        if (template.announceEnabled) add("播报")
        if (template.notifyFailedSystemEnabled || template.notifyFailedToastEnabled) add("未抢到提醒")
    }
    return if (items.isEmpty()) "通知关闭" else items.joinToString("/")
}

internal fun redPacketReplyModeOptions(): List<OptionItem> {
    return listOf(
        OptionItem("不回复", RedPacketRuleConfig.REPLY_OFF, "只抢红包，不发送回复"),
        OptionItem("发送文字", RedPacketRuleConfig.REPLY_TEXT, "抢到后发送文字"),
        OptionItem("发送图片", RedPacketRuleConfig.REPLY_IMAGE, "从系统文件管理器选择图片"),
        OptionItem("发送语音", RedPacketRuleConfig.REPLY_VOICE, "从系统文件管理器选择语音文件"),
        OptionItem("发送视频", RedPacketRuleConfig.REPLY_VIDEO, "从系统文件管理器选择视频"),
        OptionItem("发送表情", RedPacketRuleConfig.REPLY_EMOJI, "从系统文件管理器选择表情文件"),
        OptionItem("发送文件", RedPacketRuleConfig.REPLY_FILE, "从系统文件管理器选择任意文件"),
        OptionItem("发送收藏", RedPacketRuleConfig.REPLY_FAVORITE, "从最近收藏选择"),
        OptionItem("发送 XML", RedPacketRuleConfig.REPLY_XML, "发送 AppMsg/XML 内容")
    )
}

internal fun redPacketReplyStepModeOptions(): List<OptionItem> {
    return redPacketReplyModeOptions().filter { it.value != RedPacketRuleConfig.REPLY_OFF }
}

internal fun redPacketReplyUsesText(replyMode: Int): Boolean {
    return replyMode == RedPacketRuleConfig.REPLY_TEXT ||
        replyMode == RedPacketRuleConfig.REPLY_AT_SENDER
}

internal fun normalizeRedPacketReplyModeForUi(replyMode: Int): Int {
    return if (replyMode == RedPacketRuleConfig.REPLY_AT_SENDER) {
        RedPacketRuleConfig.REPLY_TEXT
    } else {
        replyMode
    }
}

internal fun normalizeRedPacketReplyTextForUi(replyMode: Int, text: String): String {
    if (replyMode != RedPacketRuleConfig.REPLY_AT_SENDER) return text
    if (redPacketReplyContainsAtSenderVariable(text)) return text
    return REDPACKET_AT_SENDER_VARIABLE + text
}

internal fun redPacketReplyContainsAtSenderVariable(text: String): Boolean {
    return REDPACKET_AT_SENDER_VARIABLES.any { text.contains(it) }
}

internal fun normalizeRedPacketReplyStepsForUi(
    steps: List<RedPacketReplyStep>,
    legacyMode: Int,
    legacyText: String,
    legacyDelayMs: Long,
    legacyRandom: Boolean
): List<RedPacketReplyStep> {
    val source = if (steps.isNotEmpty()) {
        steps
    } else {
        RedPacketRuleConfig.legacyReplySteps(legacyMode, legacyText, legacyDelayMs, legacyRandom)
    }
    return source.map { normalizeRedPacketReplyStepForUi(it) }
}

internal fun normalizeRedPacketReplyStepForUi(step: RedPacketReplyStep): RedPacketReplyStep {
    val mode = normalizeRedPacketReplyModeForUi(step.mode)
    val content = normalizeRedPacketReplyTextForUi(step.mode, step.content)
    return step.copy(
        id = step.id.ifBlank { System.currentTimeMillis().toString() },
        mode = mode,
        content = content,
        delayMs = step.delayMs.coerceIn(0L, 600000L)
    )
}

internal fun cleanRedPacketReplySteps(steps: List<RedPacketReplyStep>): List<RedPacketReplyStep> {
    return steps.map { normalizeRedPacketReplyStepForUi(it) }
        .filter { it.mode != RedPacketRuleConfig.REPLY_OFF && it.content.isNotBlank() }
}

internal fun newRedPacketReplyStep(index: Int): RedPacketReplyStep {
    return RedPacketReplyStep(
        id = System.currentTimeMillis().toString() + "_$index",
        mode = RedPacketRuleConfig.REPLY_TEXT,
        content = "谢谢老板",
        delayMs = 0L,
        random = false
    )
}

internal fun defaultRedPacketReplyContent(replyMode: Int): String {
    return when {
        redPacketReplyUsesText(replyMode) -> "谢谢老板"
        replyMode == RedPacketRuleConfig.REPLY_XML -> "<msg><appmsg appid=\"\" sdkver=\"0\"><title>谢谢老板</title></appmsg></msg>"
        else -> ""
    }
}

internal fun redPacketReplyModeLabel(replyMode: Int): String {
    return when (normalizeRedPacketReplyModeForUi(replyMode)) {
        RedPacketRuleConfig.REPLY_TEXT -> "发送文字"
        RedPacketRuleConfig.REPLY_IMAGE -> "发送图片"
        RedPacketRuleConfig.REPLY_VOICE -> "发送语音"
        RedPacketRuleConfig.REPLY_VIDEO -> "发送视频"
        RedPacketRuleConfig.REPLY_EMOJI -> "发送表情"
        RedPacketRuleConfig.REPLY_FILE -> "发送文件"
        RedPacketRuleConfig.REPLY_FAVORITE -> "发送收藏"
        RedPacketRuleConfig.REPLY_XML -> "发送 XML"
        else -> "不回复"
    }
}

internal fun describeRedPacketReplySteps(steps: List<RedPacketReplyStep>): String {
    if (steps.isEmpty()) return "不回复"
    val types = steps.take(3).joinToString("/") { redPacketReplyModeShortLabel(it.mode) }
    val suffix = if (steps.size > 3) "等" else ""
    return "${steps.size} 条回复 · $types$suffix · 顺序发送"
}

internal fun redPacketReplyStepSummary(step: RedPacketReplyStep): String {
    val content = if (redPacketReplyUsesText(step.mode) || step.mode == RedPacketRuleConfig.REPLY_XML) {
        step.content.trim().replace('\n', ' ').ifBlank { "未填写内容" }.take(24)
    } else {
        redPacketReplyContentSummary(step.mode, step.content, withHint = false).removePrefix("已选择：")
    }
    val delay = step.delayMs.coerceAtLeast(0L)
    val delayText = if (delay > 0L) "延迟 ${delay}ms" else "无延迟"
    val randomText = if (step.random) "随机追加" else "固定"
    return "$content · $delayText · $randomText"
}

internal fun redPacketReplyModeShortLabel(replyMode: Int): String {
    return when (normalizeRedPacketReplyModeForUi(replyMode)) {
        RedPacketRuleConfig.REPLY_TEXT -> "文字"
        RedPacketRuleConfig.REPLY_IMAGE -> "图片"
        RedPacketRuleConfig.REPLY_VOICE -> "语音"
        RedPacketRuleConfig.REPLY_VIDEO -> "视频"
        RedPacketRuleConfig.REPLY_EMOJI -> "表情"
        RedPacketRuleConfig.REPLY_FILE -> "文件"
        RedPacketRuleConfig.REPLY_FAVORITE -> "收藏"
        RedPacketRuleConfig.REPLY_XML -> "XML"
        else -> "关闭"
    }
}

internal fun redPacketReplyPickerTitle(replyMode: Int): String {
    return when (replyMode) {
        RedPacketRuleConfig.REPLY_IMAGE -> "选择图片"
        RedPacketRuleConfig.REPLY_VOICE -> "选择语音文件"
        RedPacketRuleConfig.REPLY_VIDEO -> "选择视频"
        RedPacketRuleConfig.REPLY_EMOJI -> "选择表情文件"
        RedPacketRuleConfig.REPLY_FILE -> "选择文件"
        RedPacketRuleConfig.REPLY_FAVORITE -> "选择收藏"
        else -> "选择文件"
    }
}

internal fun redPacketReplyContentSummary(replyMode: Int, value: String, withHint: Boolean = true): String {
    return if (replyMode == RedPacketRuleConfig.REPLY_FAVORITE) {
        favoriteSummary(value)
    } else {
        redPacketReplyFileSummary(value, withHint)
    }
}

internal fun redPacketReplyFileSummary(value: String, withHint: Boolean = true): String {
    val names = RedPacketRuleConfig.splitTokens(value)
        .map { redPacketReplyFileDisplayName(it) }
        .filter { it.isNotBlank() }
    if (names.isEmpty()) return "未选择文件"
    val preview = names.take(3).joinToString("、")
    val suffix = when {
        names.size == 1 -> ""
        names.size > 3 -> " 等 ${names.size} 个文件"
        else -> "，共 ${names.size} 个"
    }
    val summary = "已选择：$preview$suffix"
    return if (withHint) "$summary，点击重新选择" else summary
}

internal fun redPacketReplyFileDisplayName(value: String): String {
    val raw = value.trim()
    if (raw.isBlank()) return ""
    val name = if (raw.startsWith("content://", ignoreCase = true) || raw.startsWith("file://", ignoreCase = true)) {
        runCatching { Uri.parse(raw) }.getOrNull()
            ?.lastPathSegment
            ?.let { Uri.decode(it) }
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            .orEmpty()
            .ifBlank { raw.substringAfterLast('/') }
    } else {
        File(raw).name.ifBlank { raw.substringAfterLast('/') }
    }
    val cacheNameMatch = Regex("""^\d{10,}_(.+)$""").matchEntire(name)
    return (cacheNameMatch?.groupValues?.getOrNull(1) ?: name).trim()
}

internal fun describeRedPacketBinding(
    binding: RedPacketRuleBinding,
    templates: List<RedPacketRuleTemplate>
): String {
    val state = if (binding.enabled) "启用" else "关闭"
    val template = templates.firstOrNull { it.id == binding.templateId }
        ?.name
        ?.ifBlank { binding.templateId }
        ?: "跟随默认规则"
    return "$state · $template · ${binding.targetId}"
}

internal fun redPacketBindingMatchesQuery(
    binding: RedPacketRuleBinding,
    templates: List<RedPacketRuleTemplate>,
    lowerQuery: String
): Boolean {
    if (binding.label.lowercase(Locale.US).contains(lowerQuery)) return true
    if (binding.targetId.lowercase(Locale.US).contains(lowerQuery)) return true
    val template = templates.firstOrNull { it.id == binding.templateId }
    return template != null && (
        template.name.lowercase(Locale.US).contains(lowerQuery) ||
            describeRedPacketTemplate(template).lowercase(Locale.US).contains(lowerQuery)
        )
}

internal fun redPacketBindingFromContact(
    row: MessageBlockContactOption,
    templates: List<RedPacketRuleTemplate>,
    existing: RedPacketRuleBinding?
): RedPacketRuleBinding {
    val option = row.contact
    val targetId = option.id.trim()
    return RedPacketRuleBinding(
        id = existing?.id ?: RedPacketRuleConfig.bindingKey(targetId),
        targetId = targetId,
        label = existing?.label?.takeIf { it.isNotBlank() } ?: option.label.ifBlank { targetId },
        enabled = existing?.enabled ?: false,
        templateId = existing?.templateId ?: if (templates.size == 1) templates.first().id else "",
        customRules = false,
        overrideRule = null
    )
}

internal fun findRedPacketBinding(
    bindings: List<RedPacketRuleBinding>,
    targetId: String
): RedPacketRuleBinding? {
    val key = RedPacketRuleConfig.bindingKey(targetId)
    return bindings.firstOrNull { RedPacketRuleConfig.bindingKey(it.targetId) == key }
}

internal fun normalizedRedPacketBinding(binding: RedPacketRuleBinding): RedPacketRuleBinding {
    val targetId = binding.targetId.trim()
    return binding.copy(
        id = RedPacketRuleConfig.bindingKey(targetId),
        targetId = targetId,
        label = binding.label.ifBlank { targetId },
        templateId = binding.templateId.trim(),
        customRules = false,
        overrideRule = null
    )
}

internal fun upsertRedPacketBindings(
    current: List<RedPacketRuleBinding>,
    additions: List<RedPacketRuleBinding>
): List<RedPacketRuleBinding> {
    val result = linkedMapOf<String, RedPacketRuleBinding>()
    current.forEach { binding ->
        val normalized = normalizedRedPacketBinding(binding)
        if (normalized.targetId.isNotBlank()) {
            result[RedPacketRuleConfig.bindingKey(normalized.targetId)] = normalized
        }
    }
    additions.forEach { binding ->
        val normalized = normalizedRedPacketBinding(binding)
        if (normalized.targetId.isBlank()) return@forEach
        val key = RedPacketRuleConfig.bindingKey(normalized.targetId)
        val old = result[key]
        result[key] = if (old == null) {
            normalized
        } else {
            normalized.copy(
                id = old.id,
                enabled = old.enabled,
                label = old.label.ifBlank { normalized.label },
                templateId = old.templateId.ifBlank { normalized.templateId }
            )
        }
    }
    return result.values.toList()
}

internal fun formatRedPacketSecond(value: Int): String {
    val second = value.coerceIn(0, 86399)
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        second / 3600,
        second / 60 % 60,
        second % 60
    )
}

internal fun parseRedPacketSecond(value: String, fallback: Int): Int {
    return parseTimeOfDaySeconds(value).takeIf { it >= 0 } ?: fallback.coerceIn(0, 86399)
}

internal fun initialMessageBlockState(sp: SharedPreferences): MessageBlockInitialState {
    val rawTemplates = sp.getString(MessageBlockSettings.KEY_TEMPLATES, "") ?: ""
    val rawBindings = sp.getString(MessageBlockSettings.KEY_BINDINGS, "") ?: ""
    val parsedTemplates = MessageBlockSettings.parseTemplates(rawTemplates)
    val cleanedTemplates = parsedTemplates.map { MessageBlockSettings.clearTemplateTargets(it) }
    val existingBindings = MessageBlockSettings.parseBindings(rawBindings)
    val legacyBindings = if (rawBindings.isBlank()) {
        MessageBlockSettings.legacyBindingsFromTemplates(parsedTemplates)
    } else {
        emptyList()
    }
    val bindings = if (existingBindings.isNotEmpty()) existingBindings else legacyBindings
    val shouldPersist = rawBindings.isBlank() && legacyBindings.isNotEmpty()
    return MessageBlockInitialState(cleanedTemplates, bindings, shouldPersist)
}

internal fun newMessageBlockTemplate(index: Int): MessageBlockTemplate {
    return MessageBlockTemplate(
        id = System.currentTimeMillis().toString() + "_" + index,
        name = "模板 $index",
        enabled = true,
        mode = MessageBlockSettings.MODE_TARGETS,
        targets = "",
        targetGroupMembers = "",
        excludes = "",
        excludeGroupMembers = "",
        typeAll = false,
        types = emptySet(),
        textKeywords = ""
    )
}

internal fun describeMessageBlockTemplate(template: MessageBlockTemplate): String {
    val enabled = if (template.enabled) "启用" else "关闭"
    val type = describeMessageBlockRuleState(
        MessageBlockRuleState(template.typeAll, template.types, template.textKeywords)
    )
    return "$enabled · $type"
}

internal fun describeMessageBlockRuleState(rules: MessageBlockRuleState): String {
    val type = if (rules.typeAll) "所有消息" else {
        val names = messageBlockTypeOptions()
            .filter { rules.types.contains(it.key) }
            .joinToString("、") { it.title }
        names.ifBlank { "未选类型" }
    }
    val keyword = if (rules.textKeywords.isBlank()) "" else "，关键词"
    return "$type$keyword"
}

internal fun messageBlockRuleStateFromTemplates(
    templateIds: Set<String>,
    templates: List<MessageBlockTemplate>
): MessageBlockRuleState {
    val selected = templates.filter { templateIds.contains(it.id) }
    if (selected.isEmpty()) return MessageBlockRuleState(false, emptySet(), "")
    val types = LinkedHashSet<String>()
    selected.forEach { template -> types += template.types }
    val textKeywords = selected.map { it.textKeywords.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("\n")
    return MessageBlockRuleState(
        typeAll = selected.any { it.typeAll },
        types = types,
        textKeywords = textKeywords
    )
}

internal fun describeMessageBlockBinding(binding: MessageBlockBinding, templates: List<MessageBlockTemplate>): String {
    if (binding.quickBlockAll) return "聊天分组快捷屏蔽 · 所有消息"
    val state = if (binding.enabled) "启用" else "关闭"
    val action = if (binding.action == MessageBlockSettings.ACTION_EXCLUDE) "排除" else "屏蔽"
    val names = templates
        .filter { binding.templateIds.contains(it.id) }
        .joinToString("、") { it.name.ifBlank { it.id } }
        .ifBlank { "未绑定模板" }
    val rules = if (binding.customRules) {
        " · 专属规则：${describeMessageBlockRuleState(MessageBlockRuleState(binding.typeAll, binding.types, binding.textKeywords))}"
    } else {
        ""
    }
    return "$state · $action · $names$rules"
}

internal fun describeMessageBlockDefaultRule(rule: MessageBlockDefaultRule, templates: List<MessageBlockTemplate>): String {
    if (!rule.enabled) return "未启用"
    val names = templates
        .filter { rule.templateIds.contains(it.id) }
        .joinToString("、") { it.name.ifBlank { it.id } }
    val rules = if (rule.customRules) {
        "专属规则：${describeMessageBlockRuleState(MessageBlockRuleState(rule.typeAll, rule.types, rule.textKeywords))}"
    } else {
        names.ifBlank { "未绑定模板" }
    }
    return "启用 · $rules"
}

internal fun messageBlockBindingMatchesQuery(
    binding: MessageBlockBinding,
    templates: List<MessageBlockTemplate>,
    lowerQuery: String
): Boolean {
    if (binding.label.lowercase(Locale.US).contains(lowerQuery)) return true
    if (binding.targetId.lowercase(Locale.US).contains(lowerQuery)) return true
    if (binding.customRules) {
        val rules = describeMessageBlockRuleState(
            MessageBlockRuleState(binding.typeAll, binding.types, binding.textKeywords)
        ).lowercase(Locale.US)
        if (rules.contains(lowerQuery) || binding.textKeywords.lowercase(Locale.US).contains(lowerQuery)) return true
    }
    return templates.any { template ->
        binding.templateIds.contains(template.id) &&
            (
                template.name.lowercase(Locale.US).contains(lowerQuery) ||
                    template.id.lowercase(Locale.US).contains(lowerQuery)
                )
    }
}

internal fun messageBlockBindingTargetSummary(binding: MessageBlockBinding): String {
    val type = if (binding.targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) "群成员" else "联系人 / 群聊 / 公众号"
    return "$type · ${binding.targetId}"
}

internal fun messageBlockBindingFromContact(
    option: ContactOption,
    templates: List<MessageBlockTemplate>,
    existing: MessageBlockBinding?
): MessageBlockBinding {
    val label = option.label.ifBlank { option.id }
    return MessageBlockBinding(
        id = existing?.id ?: MessageBlockSettings.bindingKey(
            MessageBlockSettings.TARGET_CONTACT,
            option.id
        ),
        targetType = MessageBlockSettings.TARGET_CONTACT,
        targetId = option.id,
        label = label,
        enabled = existing?.enabled ?: true,
        action = existing?.action ?: MessageBlockSettings.ACTION_BLOCK,
        templateIds = existing?.templateIds ?: defaultMessageBlockTemplateIds(templates),
        quickBlockAll = existing?.quickBlockAll ?: false
    )
}

internal fun messageBlockBindingFromGroupMember(
    entry: String,
    templates: List<MessageBlockTemplate>,
    existing: MessageBlockBinding?
): MessageBlockBinding {
    val normalized = normalizeGroupMemberEntry(entry) ?: entry.trim()
    return MessageBlockBinding(
        id = existing?.id ?: MessageBlockSettings.bindingKey(
            MessageBlockSettings.TARGET_GROUP_MEMBER,
            normalized
        ),
        targetType = MessageBlockSettings.TARGET_GROUP_MEMBER,
        targetId = normalized,
        label = existing?.label?.takeIf { it.isNotBlank() } ?: normalized,
        enabled = existing?.enabled ?: true,
        action = existing?.action ?: MessageBlockSettings.ACTION_BLOCK,
        templateIds = existing?.templateIds ?: defaultMessageBlockTemplateIds(templates),
        quickBlockAll = existing?.quickBlockAll ?: false
    )
}

internal fun defaultMessageBlockTemplateIds(templates: List<MessageBlockTemplate>): Set<String> {
    return if (templates.size == 1) setOf(templates.first().id) else emptySet()
}

internal fun normalizedMessageBlockBinding(binding: MessageBlockBinding): MessageBlockBinding {
    val targetType = if (binding.targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        MessageBlockSettings.TARGET_GROUP_MEMBER
    } else {
        MessageBlockSettings.TARGET_CONTACT
    }
    val normalizedTarget = if (targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        normalizeGroupMemberEntry(binding.targetId) ?: binding.targetId.trim()
    } else {
        binding.targetId.trim()
    }
    val action = if (binding.action == MessageBlockSettings.ACTION_EXCLUDE) {
        MessageBlockSettings.ACTION_EXCLUDE
    } else {
        MessageBlockSettings.ACTION_BLOCK
    }
    return binding.copy(
        id = MessageBlockSettings.bindingKey(targetType, normalizedTarget),
        targetType = targetType,
        targetId = normalizedTarget,
        action = action,
        templateIds = binding.templateIds.filter { it.isNotBlank() }.toSet(),
        types = binding.types.filter { it.isNotBlank() }.toSet(),
        textKeywords = binding.textKeywords.trim()
    )
}

internal fun findMessageBlockBinding(
    bindings: List<MessageBlockBinding>,
    targetType: String,
    targetId: String
): MessageBlockBinding? {
    val normalizedType = if (targetType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        MessageBlockSettings.TARGET_GROUP_MEMBER
    } else {
        MessageBlockSettings.TARGET_CONTACT
    }
    val normalizedTarget = if (normalizedType == MessageBlockSettings.TARGET_GROUP_MEMBER) {
        normalizeGroupMemberEntry(targetId) ?: targetId.trim()
    } else {
        targetId.trim()
    }
    return bindings.firstOrNull {
        it.targetType == normalizedType && it.targetId == normalizedTarget
    }
}

internal fun upsertMessageBlockBindings(
    current: List<MessageBlockBinding>,
    additions: List<MessageBlockBinding>
): List<MessageBlockBinding> {
    val result = linkedMapOf<String, MessageBlockBinding>()
    current.forEach { binding ->
        val normalized = normalizedMessageBlockBinding(binding)
        result[MessageBlockSettings.bindingKey(normalized.targetType, normalized.targetId)] = normalized
    }
    additions.forEach { binding ->
        val normalized = normalizedMessageBlockBinding(binding)
        val key = MessageBlockSettings.bindingKey(normalized.targetType, normalized.targetId)
        val old = result[key]
        result[key] = if (old == null) {
            normalized
        } else {
            normalized.copy(
                id = old.id,
                enabled = old.enabled,
                action = old.action,
                templateIds = old.templateIds + normalized.templateIds,
                customRules = old.customRules,
                typeAll = old.typeAll,
                types = old.types,
                textKeywords = old.textKeywords
            )
        }
    }
    return result.values.toList()
}

internal fun messageBlockTypeOptions(): List<MessageBlockTypeOption> {
    return listOf(
        MessageBlockTypeOption(MessageBlockSettings.TYPE_TEXT, "文字", "普通文本消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_QUOTE, "引用消息", "引用文字、图片和其它消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_IMAGE, "图片", "图片消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_VIDEO, "小视频", "视频和短视频消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_VOICE, "语音", "语音消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_LINK, "文章、链接", "文章、链接、文件和接龙类 AppMsg"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_MUSIC, "音乐", "音乐类 AppMsg"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_MINI_PROGRAM, "小程序", "小程序卡片"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_CARD, "名片", "联系人名片"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_EMOJI, "动画表情", "表情消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_RED_PACKET, "红包", "红包消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_TRANSFER, "转账", "转账消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_VOIP, "视频/语音聊天", "通话邀请和记录"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_LOCATION, "地图位置", "位置分享消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_SYSTEM, "系统消息", "普通系统提示"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_PAT, "拍一拍", "拍一拍系统消息"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_VIDEO_NUMBER, "视频号链接", "视频号分享卡片"),
        MessageBlockTypeOption(MessageBlockSettings.TYPE_UNKNOWN, "未知类型", "微信新增或暂未识别的消息类型")
    )
}

internal fun loadMessageBlockContacts(
    context: Context,
    callback: (MessageBlockContactData, Throwable?) -> Unit
) {
    val api = WeChatApis.contact().contacts()
    if (api == null || !api.isAvailable) {
        callback(MessageBlockContactData(), IllegalStateException("联系人列表不可用"))
        return
    }
    val main = Handler(Looper.getMainLooper())
    Thread({
        try {
            val labelsByUser = linkedMapOf<String, MutableList<String>>()
            val labels = runCatching { api.getContactLabelList() }
                .getOrDefault(emptyList())
                .mapNotNull { label ->
                    val labelId = label.labelId.trim()
                    val labelName = label.labelName.trim().ifBlank { labelId }
                    if (labelId.isBlank() && labelName.isBlank()) return@mapNotNull null
                    val ids = label.userNameList.ifEmpty {
                        if (labelId.isBlank()) emptyList() else runCatching { api.getContactByLabelId(labelId) }.getOrDefault(emptyList())
                    }.map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    ids.forEach { wxId ->
                        labelsByUser.getOrPut(wxId) { arrayListOf() }.add(labelName)
                    }
                    MessageBlockLabelOption(
                        id = labelId.ifBlank { labelName },
                        name = labelName,
                        contactIds = ids
                    )
                }
                .sortedBy { it.name.lowercase(Locale.US) }

            val byId = linkedMapOf<String, MessageBlockContactOption>()
            fun putContact(contact: WeChatContact?, kind: MessageBlockContactKind, group: Boolean) {
                val option = contact.toOption(group) ?: return
                byId[option.id] = MessageBlockContactOption(
                    contact = option,
                    kind = kind,
                    labelNames = labelsByUser[option.id].orEmpty().distinct()
                )
            }

            api.getPickerContacts().forEach { putContact(it, MessageBlockContactKind.FRIEND, false) }
            api.getPickerGroups().forEach { putContact(it, MessageBlockContactKind.GROUP, true) }
            api.getPickerOfficialAccounts().forEach { putContact(it, MessageBlockContactKind.OFFICIAL, false) }

            val missingLabelIds = labels.flatMap { it.contactIds }
                .distinct()
                .filterNot { byId.containsKey(it) }
            if (missingLabelIds.isNotEmpty()) {
                api.getContactsByIds(missingLabelIds).forEach { contact ->
                    val kind = when {
                        contact.isGroup() -> MessageBlockContactKind.GROUP
                        contact.isOfficialAccount() -> MessageBlockContactKind.OFFICIAL
                        else -> MessageBlockContactKind.FRIEND
                    }
                    putContact(contact, kind, contact.isGroup())
                }
            }

            val contacts = byId.values.sortedWith(
                compareBy<MessageBlockContactOption> { it.kind.ordinal }
                    .thenBy { it.contact.label.lowercase(Locale.US) }
            )
            main.post { callback(MessageBlockContactData(contacts, labels), null) }
        } catch (throwable: Throwable) {
            main.post { callback(MessageBlockContactData(), throwable) }
        }
    }, "HchatMessageBlockContacts").start()
}

internal fun loadContacts(
    context: Context,
    mode: ContactPickerMode,
    includeLabels: Boolean = false,
    callback: (List<ContactOption>, Throwable?) -> Unit
) {
    val api = WeChatApis.contact().contacts()
    if (api == null || !api.isAvailable) {
        callback(emptyList(), IllegalStateException("联系人列表不可用"))
        return
    }
    val main = Handler(Looper.getMainLooper())
    Thread({
        try {
            val rows = mutableListOf<ContactOption>()
            val labelsByUser = linkedMapOf<String, MutableList<String>>()
            if (includeLabels) {
                runCatching { api.getContactLabelList() }.getOrDefault(emptyList()).forEach { label ->
                    val labelName = label.labelName.ifBlank { label.labelId }
                    if (labelName.isBlank()) return@forEach
                    label.userNameList.forEach { wxId ->
                        if (wxId.isNotBlank()) {
                            labelsByUser.getOrPut(wxId) { arrayListOf() }.add(labelName)
                        }
                    }
                }
            }
            if (mode == ContactPickerMode.FRIENDS || mode == ContactPickerMode.BOTH || mode == ContactPickerMode.ALL_CHATS) {
                rows += api.getPickerContacts().mapNotNull { it.toOption(false, labelsByUser[it.wxId].orEmpty()) }
            }
            if (mode == ContactPickerMode.GROUPS || mode == ContactPickerMode.BOTH || mode == ContactPickerMode.ALL_CHATS) {
                rows += api.getPickerGroups().mapNotNull { it.toOption(true) }
            }
            if (mode == ContactPickerMode.ALL_CHATS) {
                rows += api.getPickerOfficialAccounts().mapNotNull { it.toOption(false, official = true) }
            }
            val conversationOrder = WeChatApis.conversations()
                ?.getRecentConversationUsernames(10000)
                .orEmpty()
                .mapIndexed { index, username -> username to index }
                .toMap()
            val sorted = rows.distinctBy { it.id }.sortedWith(
                compareBy<ContactOption> { conversationOrder[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.group }
                    .thenBy { it.official }
                    .thenBy { it.label.lowercase(Locale.US) }
            )
            main.post { callback(sorted, null) }
        } catch (throwable: Throwable) {
            main.post { callback(emptyList(), throwable) }
        }
    }, "HchatContactPicker").start()
}

internal fun loadGroupMembers(
    context: Context,
    group: ContactOption,
    callback: (List<ContactOption>, Throwable?) -> Unit
) {
    val api = WeChatApis.contact().contacts()
    if (api == null || !api.isAvailable) {
        callback(emptyList(), IllegalStateException("群成员列表不可用"))
        return
    }
    val main = Handler(Looper.getMainLooper())
    Thread({
        try {
            val roomNames = api.getGroupMemberRoomDisplayNames(group.id)
            val rows = api.getGroupMembers(group.id).mapNotNull { contact ->
                val id = contact.wxId.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ContactOption(
                    id = id,
                    label = roomNames[id]?.takeIf { it.isNotBlank() }
                        ?: contact.displayName().ifEmpty { id },
                    group = false,
                    avatarUrl = contact.avatarUrl,
                    avatarBackupUrl = contact.avatarBackupUrl,
                    searchAliases = listOf(contact.remarkName, contact.nickname, contact.customWxId)
                        .filter { it.isNotBlank() }
                        .distinct()
                )
            }.sortedBy { it.label.lowercase(Locale.US) }
            main.post { callback(rows, null) }
        } catch (throwable: Throwable) {
            main.post { callback(emptyList(), throwable) }
        }
    }, "HchatGroupMemberPicker").start()
}

internal fun WeChatContact?.toOption(
    group: Boolean,
    labels: List<String> = emptyList(),
    official: Boolean = false
): ContactOption? {
    if (this == null || wxId.isNullOrEmpty()) return null
    return ContactOption(
        id = wxId,
        label = pickerDisplayName(group),
        group = group,
        avatarUrl = avatarUrl,
        avatarBackupUrl = avatarBackupUrl,
        labels = labels.distinct(),
        official = official,
        searchAliases = listOf(remarkName, nickname, customWxId)
            .filter { it.isNotBlank() }
            .distinct()
    )
}

internal fun parseIds(value: String): Set<String> {
    return value.split('|').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

internal fun formatIds(ids: Iterable<String>): String {
    return ids
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("|")
}

internal fun parseFavoriteIds(value: String): Set<String> {
    return value.replace(";;;", "|")
        .split("|", ",", "，", "\n", "\r")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

internal fun formatFavoriteIds(ids: Iterable<String>, delimiter: String, multiSelect: Boolean): String {
    val values = ids
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (values.isEmpty()) return ""
    return if (multiSelect) {
        values.joinToString(delimiter)
    } else {
        values.first()
    }
}

internal fun favoriteSummary(value: String): String {
    val count = parseFavoriteIds(value).size
    return if (count == 0) "未选择收藏" else "已选择 $count 个收藏"
}

internal fun <T> List<T>.selectedFirst(isSelected: (T) -> Boolean): List<T> {
    if (size < 2) return this
    val selected = ArrayList<T>()
    val unselected = ArrayList<T>()
    for (value in this) {
        if (isSelected(value)) selected.add(value) else unselected.add(value)
    }
    if (selected.isEmpty() || unselected.isEmpty()) return this
    return selected.apply { addAll(unselected) }
}

internal fun groupMemberEntry(groupId: String, memberId: String): String {
    return "${groupId.trim()}/${memberId.trim()}"
}

internal fun parseGroupMemberEntries(value: String): Set<String> {
    return value.split("|", ",", "，", "\n", "\r")
        .mapNotNull { normalizeGroupMemberEntry(it) }
        .toSet()
}

internal fun formatGroupMemberEntries(entries: Iterable<String>): String {
    return entries
        .mapNotNull { normalizeGroupMemberEntry(it) }
        .distinct()
        .joinToString("|")
}

internal fun normalizeGroupMemberEntry(value: String): String? {
    val token = value.trim()
    if (token.isEmpty()) return null
    val separators = charArrayOf('/', '#', ':', '：')
    val index = separators.map { token.indexOf(it) }
        .filter { it > 0 }
        .minOrNull()
        ?: return null
    val groupId = token.substring(0, index).trim()
    val memberId = token.substring(index + 1).trim()
    if (groupId.isEmpty() || memberId.isEmpty()) return null
    return groupMemberEntry(groupId, memberId)
}

suspend fun loadAvatarBitmap(wxId: String, primary: String, backup: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val sources = avatarSources(wxId, primary, backup)
    if (sources.isEmpty()) {
        null
    } else {
        val cacheKey = sources.joinToString("|")
        val cached = AvatarMemoryCache.cached(cacheKey)
        if (cached.first) {
            cached.second
        } else {
            var bitmap: ImageBitmap? = null
            for (source in sources) {
                bitmap = loadAvatarSource(source)?.asImageBitmap()
                if (bitmap != null) break
            }
            bitmap?.also { AvatarMemoryCache.put(cacheKey, it) }
        }
    }
}

internal fun avatarSources(wxId: String, primary: String, backup: String): List<String> {
    val result = LinkedHashSet<String>()
    if (primary.isNotBlank()) result.add(primary)
    if (backup.isNotBlank()) result.add(backup)
    val local = avatarLocalPath(wxId, hd = false)
    if (!local.isNullOrBlank()) result.add(local)
    val hdLocal = avatarLocalPath(wxId, hd = true)
    if (!hdLocal.isNullOrBlank()) result.add(hdLocal)
    return result.toList()
}

internal fun avatarLocalPath(wxId: String, hd: Boolean): String? {
    if (wxId.isBlank()) return null
    val root = WeChatAvatarPathCache.resolveAvatarRoot() ?: return null
    val hash = md5Lower(wxId) ?: return null
    return buildString(root.length + 64) {
        append(root.trimEnd('/'))
        append('/')
        append(hash.substring(0, 2))
        append('/')
        append(hash.substring(2, 4))
        append("/user_")
        if (hd) append("hd_")
        append(hash)
        append(".png")
    }
}

internal fun md5Lower(value: String): String? {
    return try {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        buildString(digest.size * 2) {
            for (b in digest) {
                append(((b.toInt() ushr 4) and 0xF).toString(16))
                append((b.toInt() and 0xF).toString(16))
            }
        }
    } catch (_: Throwable) {
        null
    }
}
