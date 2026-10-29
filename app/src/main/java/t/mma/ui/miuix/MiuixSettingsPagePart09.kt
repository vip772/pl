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

internal enum class ZombieCheckSelection {
    TARGETS,
    EXCLUDED,
    DELETE
}

internal data class ZombieCheckDeleteTarget(val wxid: String, val name: String)

internal sealed class ZombieCheckRoute {
    object Main : ZombieCheckRoute()
    data class ContactPicker(
        val selection: ZombieCheckSelection,
        val request: ContactPickerRequest
    ) : ZombieCheckRoute()
}

@Composable
internal fun ZombieCheckMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { ZombieCheckSettings(context) }
    var route by remember { mutableStateOf<ZombieCheckRoute>(ZombieCheckRoute.Main) }
    var targetIds by remember { mutableStateOf(settings.targetIds()) }
    var excludedIds by remember { mutableStateOf(settings.excludedIds()) }
    var pendingDeleteTargets by remember { mutableStateOf(emptyList<ZombieCheckDeleteTarget>()) }

    SettingsRouteTransition(
        targetState = route,
        label = "ZombieCheckRoute",
        depthOf = { if (it is ZombieCheckRoute.Main) 0 else 1 }
    ) { currentRoute ->
        when (currentRoute) {
            ZombieCheckRoute.Main -> ZombieCheckMainPage(
                context = context,
                provider = provider,
                targetIds = targetIds,
                excludedIds = excludedIds,
                pendingDeleteTargets = pendingDeleteTargets,
                onBack = onBack,
                onPickTargets = {
                    route = ZombieCheckRoute.ContactPicker(
                        ZombieCheckSelection.TARGETS,
                        ContactPickerRequest(
                            title = "选择检测好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = targetIds.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                },
                onPickExcluded = {
                    route = ZombieCheckRoute.ContactPicker(
                        ZombieCheckSelection.EXCLUDED,
                        ContactPickerRequest(
                            title = "选择排除好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = excludedIds.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                },
                onPickDelete = {
                    val detectedIds = ZombieCheckController.snapshot().results
                        .filter { it.type == ZombieCheckResultType.DEAD }
                        .map { it.wxid }
                        .toSet()
                    route = ZombieCheckRoute.ContactPicker(
                        ZombieCheckSelection.DELETE,
                        ContactPickerRequest(
                            title = "批量删除好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = detectedIds.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                },
                onDismissDelete = { pendingDeleteTargets = emptyList() },
                onConfirmDelete = { clearRecord, delaySeconds ->
                    val result = ZombieCheckController.deleteFriends(
                        pendingDeleteTargets.map { it.wxid },
                        clearRecord,
                        delaySeconds
                    )
                    pendingDeleteTargets = emptyList()
                    result
                }
            )
            is ZombieCheckRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = ZombieCheckRoute.Main },
                onConfirm = { picked ->
                    val ids = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    when (currentRoute.selection) {
                        ZombieCheckSelection.TARGETS -> {
                            targetIds = ids
                            settings.saveTargetIds(ids)
                        }
                        ZombieCheckSelection.EXCLUDED -> {
                            excludedIds = ids
                            settings.saveExcludedIds(ids)
                        }
                        ZombieCheckSelection.DELETE -> {
                            pendingDeleteTargets = picked.map {
                                ZombieCheckDeleteTarget(it.id, it.label.ifBlank { it.id })
                            }
                        }
                    }
                    route = ZombieCheckRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun ZombieCheckMainPage(
    context: Context,
    provider: FeatureSettingsProvider,
    targetIds: Set<String>,
    excludedIds: Set<String>,
    pendingDeleteTargets: List<ZombieCheckDeleteTarget>,
    onBack: () -> Unit,
    onPickTargets: () -> Unit,
    onPickExcluded: () -> Unit,
    onPickDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (Boolean, Int) -> ZombieCheckActionResult
) {
    val sp = remember { HchatStorage.preferences(context, ZombieCheckSettings.PREFS_NAME) }
    var snapshot by remember { mutableStateOf(ZombieCheckController.snapshot()) }
    var enabled by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_ENABLE, ZombieCheckSettings.DEFAULT_ENABLE)) }
    var autoTag by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_AUTO_TAG, ZombieCheckSettings.DEFAULT_AUTO_TAG)) }
    var labelName by remember { mutableStateOf(sp.getString(ZombieCheckSettings.KEY_LABEL_NAME, ZombieCheckSettings.DEFAULT_LABEL_NAME).orEmpty()) }
    var autoDelete by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_AUTO_DELETE, ZombieCheckSettings.DEFAULT_AUTO_DELETE)) }
    var clearRecord by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_CLEAR_RECORD, ZombieCheckSettings.DEFAULT_CLEAR_RECORD)) }
    var keepAwake by remember { mutableStateOf(sp.getBoolean(ZombieCheckSettings.KEY_KEEP_AWAKE, ZombieCheckSettings.DEFAULT_KEEP_AWAKE)) }
    var minDelay by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_MIN_DELAY_SECONDS, ZombieCheckSettings.DEFAULT_MIN_DELAY_SECONDS).toString()) }
    var maxDelay by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_MAX_DELAY_SECONDS, ZombieCheckSettings.DEFAULT_MAX_DELAY_SECONDS).toString()) }
    var timeout by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_TIMEOUT_SECONDS, ZombieCheckSettings.DEFAULT_TIMEOUT_SECONDS).toString()) }
    var retries by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_MAX_RETRIES, ZombieCheckSettings.DEFAULT_MAX_RETRIES).toString()) }
    var deleteDelay by remember { mutableStateOf(sp.getInt(ZombieCheckSettings.KEY_DELETE_DELAY_SECONDS, ZombieCheckSettings.DEFAULT_DELETE_DELAY_SECONDS).toString()) }
    var confirmAutoDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = ZombieCheckController.snapshot()
            delay(500L)
        }
    }

    fun saveInt(key: String, value: String, range: IntRange) {
        value.toIntOrNull()?.coerceIn(range.first, range.last)?.let {
            sp.edit().putInt(key, it).apply()
        }
    }

    fun toastResult(result: ZombieCheckActionResult) {
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        snapshot = ZombieCheckController.snapshot()
    }

    val notableResults = snapshot.results.filter { it.type != ZombieCheckResultType.NORMAL }.asReversed()

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
            item { SmallTitle(text = "检测") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用僵尸粉检测",
                        summary = "使用微信转账下单接口核验好友关系，不会确认付款",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_ENABLE, it).apply()
                            if (!it && (snapshot.running || snapshot.deleting)) {
                                toastResult(ZombieCheckController.pause())
                            }
                        }
                    )
                    InsetDivider()
                    InfoRow("状态", if (snapshot.ready) snapshot.status else "检测接口定位中")
                    InsetDivider()
                    InfoRow(
                        "进度",
                        "已检测 ${snapshot.checkedCount}/${snapshot.totalCount}，异常 ${snapshot.deadCount}，失败 ${snapshot.unknownCount}"
                    )
                    if (snapshot.deleting || snapshot.deleteTotalCount > 0) {
                        InsetDivider()
                        InfoRow(
                            "删除进度",
                            "已处理 ${snapshot.deleteCompletedCount}/${snapshot.deleteTotalCount}，已提交 ${snapshot.deleteSuccessCount}，失败 ${snapshot.deleteFailureCount}"
                        )
                    }
                    if (snapshot.currentName.isNotBlank()) {
                        InsetDivider()
                        InfoRow("当前好友", snapshot.currentName)
                    }
                    InsetDivider()
                    ActionRow("检测范围", if (targetIds.isEmpty()) "全部好友" else "已选择 ${targetIds.size} 位好友", onPickTargets)
                    InsetDivider()
                    ActionRow("排除好友", if (excludedIds.isEmpty()) "未排除" else "已排除 ${excludedIds.size} 位好友", onPickExcluded)
                    InsetDivider()
                    ActionRow(
                        if (snapshot.running || snapshot.deleting) "停止当前任务" else "开始 / 继续检测",
                        when {
                            snapshot.deleting -> "停止后不再删除剩余好友"
                            snapshot.running -> "暂停后保留当前队列"
                            else -> "按当前范围继续未完成进度"
                        }
                    ) {
                        toastResult(
                            if (snapshot.running || snapshot.deleting) {
                                ZombieCheckController.pause()
                            } else {
                                ZombieCheckController.start()
                            }
                        )
                    }
                    InsetDivider()
                    ActionRow("重置检测进度", "清空断点、结果和运行日志") {
                        toastResult(ZombieCheckController.reset())
                    }
                }
            }

            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "异常处理") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = autoTag,
                        title = "自动追加标签",
                        summary = "保留好友现有标签后追加指定标签",
                        onCheckedChange = {
                            autoTag = it
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_AUTO_TAG, it).apply()
                        }
                    )
                    if (autoTag) {
                        InsetDivider()
                        InputRow("标签名称", "检测到好友关系异常时追加", labelName) {
                            labelName = it
                            sp.edit().putString(ZombieCheckSettings.KEY_LABEL_NAME, it.trim()).apply()
                        }
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = autoDelete,
                        title = "自动删除异常好友",
                        summary = "高风险操作，默认关闭；暂停检测会取消尚未执行的删除",
                        onCheckedChange = {
                            if (it) {
                                confirmAutoDelete = true
                            } else {
                                autoDelete = false
                                sp.edit().putBoolean(ZombieCheckSettings.KEY_AUTO_DELETE, false).apply()
                            }
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = clearRecord,
                        title = "同时清理聊天记录",
                        summary = "同时用于自动删除和批量删除",
                        onCheckedChange = {
                            clearRecord = it
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_CLEAR_RECORD, it).apply()
                        }
                    )
                    InsetDivider()
                    NumberInputRow("删除间隔", "自动删除和批量删除的等待秒数，0-300", deleteDelay) {
                        deleteDelay = it
                        saveInt(ZombieCheckSettings.KEY_DELETE_DELAY_SECONDS, it, 0..300)
                    }
                    InsetDivider()
                    ActionRow(
                        "批量删除好友",
                        when {
                            snapshot.deleting -> "正在处理 ${snapshot.deleteCompletedCount}/${snapshot.deleteTotalCount}"
                            snapshot.deadCount > 0 -> "可按标签筛选，默认选中 ${snapshot.deadCount} 位异常好友"
                            else -> "从好友或微信标签中多选后删除"
                        }
                    ) {
                        if (snapshot.running || snapshot.deleting) {
                            Toast.makeText(context, "请先停止当前任务", Toast.LENGTH_SHORT).show()
                        } else {
                            onPickDelete()
                        }
                    }
                    InsetDivider()
                    SwitchRow(
                        checked = keepAwake,
                        title = "检测期间保持 CPU 运行",
                        summary = "使用 WakeLock，长时间检测会增加耗电",
                        onCheckedChange = {
                            keepAwake = it
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_KEEP_AWAKE, it).apply()
                        }
                    )
                }
            }

            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "请求参数") }
            item {
                SettingsCard {
                    NumberInputRow("最小间隔", "单位秒，0-60", minDelay) {
                        minDelay = it
                        saveInt(ZombieCheckSettings.KEY_MIN_DELAY_SECONDS, it, 0..60)
                    }
                    InsetDivider()
                    NumberInputRow("最大间隔", "单位秒，0-120", maxDelay) {
                        maxDelay = it
                        saveInt(ZombieCheckSettings.KEY_MAX_DELAY_SECONDS, it, 0..120)
                    }
                    InsetDivider()
                    NumberInputRow("请求超时", "单位秒，5-60", timeout) {
                        timeout = it
                        saveInt(ZombieCheckSettings.KEY_TIMEOUT_SECONDS, it, 5..60)
                    }
                    InsetDivider()
                    NumberInputRow("超时重试", "每位好友最多重试 0-5 次", retries) {
                        retries = it
                        saveInt(ZombieCheckSettings.KEY_MAX_RETRIES, it, 0..5)
                    }
                }
            }

            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "异常结果 · ${notableResults.size}") }
            item {
                SettingsCard {
                    if (notableResults.isEmpty()) {
                        InfoRow("暂无异常结果", "正常好友不会逐项显示")
                    } else {
                        notableResults.take(50).forEachIndexed { index, result ->
                            if (index > 0) InsetDivider()
                            val resultType = if (result.type == ZombieCheckResultType.DEAD) "好友关系异常" else "检测失败"
                            val resultSummary = if (result.message.isBlank()) resultType else "$resultType · ${result.message}"
                            ZombieCheckResultRow(
                                result.name,
                                resultSummary
                            )
                        }
                    }
                }
            }

            if (snapshot.logs.isNotEmpty()) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "运行日志") }
                item {
                    SettingsCard {
                        snapshot.logs.takeLast(12).asReversed().forEachIndexed { index, log ->
                            if (index > 0) InsetDivider()
                            InfoRow("记录", log)
                        }
                    }
                }
            }
        }
    }

    if (confirmAutoDelete) {
        WindowDialog(
            show = true,
            title = "启用自动删除",
            onDismissRequest = { confirmAutoDelete = false },
            content = {
                Column {
                    Text(
                        text = "检测依据来自微信支付接口返回文案。支付风控或服务端文案变化可能产生检测失败，请先小范围验证结果。",
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    TextButton(
                        text = "确认启用",
                        onClick = {
                            autoDelete = true
                            sp.edit().putBoolean(ZombieCheckSettings.KEY_AUTO_DELETE, true).apply()
                            confirmAutoDelete = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "取消",
                        onClick = { confirmAutoDelete = false },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        )
    }

    if (pendingDeleteTargets.isNotEmpty()) {
        WindowDialog(
            show = true,
            title = "确认批量删除",
            onDismissRequest = onDismissDelete,
            content = {
                Column {
                    Text(
                        text = buildString {
                            append("将删除已选的 ${pendingDeleteTargets.size} 位好友")
                            if (clearRecord) append("，并同时清理聊天记录")
                            append("。此操作不可撤销。")
                            val names = pendingDeleteTargets.take(6).joinToString("、") { it.name }
                            if (names.isNotBlank()) append("\n\n$names")
                            if (pendingDeleteTargets.size > 6) append(" 等")
                        },
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    TextButton(
                        text = "确认删除",
                        onClick = {
                            val result = onConfirmDelete(
                                clearRecord,
                                deleteDelay.toIntOrNull()?.coerceIn(0, 300)
                                    ?: ZombieCheckSettings.DEFAULT_DELETE_DELAY_SECONDS
                            )
                            toastResult(result)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "取消",
                        onClick = onDismissDelete,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        )
    }
}

@Composable
internal fun ZombieCheckResultRow(name: String, summary: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = name,
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = summary,
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

internal sealed class QQMusicOrderRoute {
    object Main : QQMusicOrderRoute()
    data class ContactPicker(val request: ContactPickerRequest) : QQMusicOrderRoute()
}

@Composable
internal fun QQMusicOrderMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val settings = remember { QQMusicOrderSettings(context) }
    val sp = remember { HchatStorage.preferences(context, QQMusicOrderSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<QQMusicOrderRoute>(QQMusicOrderRoute.Main) }
    var allowedTalkers by remember { mutableStateOf(settings.allowedTalkers()) }

    SettingsRouteTransition(
        targetState = route,
        label = "QQMusicOrderRoute",
        depthOf = { if (it is QQMusicOrderRoute.Main) 0 else 1 }
    ) { currentRoute ->
        when (currentRoute) {
            QQMusicOrderRoute.Main -> QQMusicOrderMainPage(
                provider = provider,
                sp = sp,
                allowedTalkers = allowedTalkers,
                onBack = onBack,
                onPickTalkers = {
                    route = QQMusicOrderRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "允许他人点歌的聊天",
                            mode = ContactPickerMode.BOTH,
                            multiSelect = true,
                            existingValue = allowedTalkers.joinToString("|"),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is QQMusicOrderRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = QQMusicOrderRoute.Main },
                onConfirm = { picked ->
                    allowedTalkers = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    settings.saveAllowedTalkers(allowedTalkers)
                    Toast.makeText(context, "点歌范围已保存", Toast.LENGTH_SHORT).show()
                    route = QQMusicOrderRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun QQMusicOrderMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    allowedTalkers: Set<String>,
    onBack: () -> Unit,
    onPickTalkers: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var customSingerEnabled by remember {
        mutableStateOf(sp.getBoolean(QQMusicOrderSettings.KEY_CUSTOM_SINGER, QQMusicOrderSettings.DEFAULT_CUSTOM_SINGER))
    }
    var triggers by remember {
        mutableStateOf(sp.getString(QQMusicOrderSettings.KEY_TRIGGERS, QQMusicOrderSettings.DEFAULT_TRIGGERS).orEmpty())
    }
    var defaultSinger by remember {
        mutableStateOf(sp.getString(QQMusicOrderSettings.KEY_DEFAULT_SINGER, QQMusicOrderSettings.DEFAULT_SINGER).orEmpty())
    }
    var appId by remember {
        mutableStateOf(sp.getString(QQMusicOrderSettings.KEY_APP_ID, QQMusicOrderSettings.DEFAULT_APP_ID).orEmpty())
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
            item { SmallTitle(text = "点歌") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_ENABLE,
                        "启用 QQ 点歌",
                        "在聊天中响应点歌指令",
                        QQMusicOrderSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_INTERCEPT_OWN_COMMAND,
                        "拦截自己的点歌指令",
                        "点击发送后不发送原指令，直接执行点歌",
                        QQMusicOrderSettings.DEFAULT_INTERCEPT_OWN_COMMAND
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_SEND_AS_CARD,
                        "点歌发送卡片",
                        "开启后发送音乐卡片，可与歌曲语音同时发送",
                        QQMusicOrderSettings.DEFAULT_SEND_AS_CARD
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_SEND_AS_VOICE,
                        "点歌发送语音",
                        "开启后发送歌曲语音，可与音乐卡片同时发送",
                        QQMusicOrderSettings.DEFAULT_SEND_AS_VOICE
                    )
                    InsetDivider()
                    ActionRow(
                        "允许他人点歌的聊天",
                        if (allowedTalkers.isEmpty()) "未选择" else "已选 ${allowedTalkers.size} 个聊天",
                        onPickTalkers
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "指令") }
            item {
                SettingsCard {
                    InputRow("触发词", "多个触发词用逗号分隔", triggers) {
                        triggers = it
                        sp.edit().putString(QQMusicOrderSettings.KEY_TRIGGERS, it).commit()
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "音乐卡片") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_REPLACE_COVER_WITH_AVATAR,
                        "封面使用点歌人头像",
                        "头像不可用时保留歌曲封面",
                        QQMusicOrderSettings.DEFAULT_REPLACE_COVER_WITH_AVATAR
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        QQMusicOrderSettings.KEY_REPLACE_SINGER_WITH_NICKNAME,
                        "歌手使用点歌人昵称",
                        "昵称不可用时保留原歌手",
                        QQMusicOrderSettings.DEFAULT_REPLACE_SINGER_WITH_NICKNAME
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = customSingerEnabled,
                        title = "自定义 singer",
                        summary = "允许通过 & 指定歌手显示名称",
                        onCheckedChange = {
                            customSingerEnabled = it
                            sp.edit().putBoolean(QQMusicOrderSettings.KEY_CUSTOM_SINGER, it).commit()
                        }
                    )
                    if (customSingerEnabled) {
                        InsetDivider()
                        InputRow("默认 singer", "留空时使用原歌手或点歌人昵称", defaultSinger) {
                            defaultSinger = it
                            sp.edit().putString(QQMusicOrderSettings.KEY_DEFAULT_SINGER, it).commit()
                        }
                    }
                    InsetDivider()
                    InputRow("AppID", "留空时使用 QQ 音乐默认 AppID", appId) {
                        appId = it
                        sp.edit().putString(QQMusicOrderSettings.KEY_APP_ID, it).commit()
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeywordNotificationMainPage(
    context: Context,
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    keywords: List<KeywordRule>,
    excludeContacts: Set<String>,
    includeContacts: Set<String>,
    listState: LazyListState,
    onBack: () -> Unit,
    onOpenKeywords: () -> Unit,
    onOpenTemplates: () -> Unit,
    onPickContacts: (Boolean) -> Unit,
    onClearContacts: (Boolean) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    var filterMode by remember { mutableStateOf(sp.getBoolean(KeywordNotificationSettings.KEY_FILTER_MODE, KeywordNotificationSettings.DEFAULT_FILTER_MODE)) }
    var quietEnabled by remember {
        mutableStateOf(sp.getBoolean(KeywordNotificationSettings.KEY_QUIET, KeywordNotificationSettings.DEFAULT_QUIET))
    }
    var quietStart by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_QUIET_START, KeywordNotificationSettings.DEFAULT_QUIET_START) ?: KeywordNotificationSettings.DEFAULT_QUIET_START) }
    var quietEnd by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_QUIET_END, KeywordNotificationSettings.DEFAULT_QUIET_END) ?: KeywordNotificationSettings.DEFAULT_QUIET_END) }
    val lastTime = sp.getLong(KeywordNotificationSettings.KEY_LAST_TIME, 0L)
    val lastKeyword = sp.getString(KeywordNotificationSettings.KEY_LAST_KEYWORD, "").orEmpty()
    val activeContacts = if (filterMode) includeContacts else excludeContacts
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
            item { SmallTitle(text = "监控") }
            item {
                SettingsCard {
                    SwitchRow(sp, KeywordNotificationSettings.KEY_ENABLE, "启用关键词通知", "收到匹配消息时提醒", KeywordNotificationSettings.DEFAULT_ENABLE)
                    InsetDivider()
                    InfoRow("关键词", "${keywords.size} 个")
                    InsetDivider()
                    InfoRow("上次匹配", if (lastTime > 0) "${KeywordNotificationRuntime.formatLastTime(lastTime)} · $lastKeyword" else "暂无匹配记录")
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "关键词") }
            item {
                SettingsCard {
                    ActionRow("关键词管理", if (keywords.isEmpty()) "未添加关键词" else "${keywords.size} 个关键词") {
                        onOpenKeywords()
                    }
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_ANY_GROUP, "任意关键词-群聊通知", "群聊文字或引用消息都触发", KeywordNotificationSettings.DEFAULT_ANY_GROUP)
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_ANY_PRIVATE, "任意关键词-私聊通知", "私聊文字或引用消息都触发", KeywordNotificationSettings.DEFAULT_ANY_PRIVATE)
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "通知") }
            item {
                SettingsCard {
                    SwitchRow(sp, KeywordNotificationSettings.KEY_NOTIFY, "系统通知", "在通知栏显示提醒", KeywordNotificationSettings.DEFAULT_NOTIFY)
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_TOAST, "Toast 提示", "短暂弹出提示", KeywordNotificationSettings.DEFAULT_TOAST)
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_AT_ME, "@我通知", "群聊有人 @ 我时提醒", KeywordNotificationSettings.DEFAULT_AT_ME)
                    InsetDivider()
                    SwitchRow(sp, KeywordNotificationSettings.KEY_AT_ALL, "@所有人/群公告通知", "命中 @所有人 或群公告时提醒", KeywordNotificationSettings.DEFAULT_AT_ALL)
                    InsetDivider()
                    ActionRow("通知/Toast模板", "留空时使用内置默认模板") {
                        onOpenTemplates()
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "关键词通知") }
            item {
                KeywordNotificationAlertCard(
                    context = context,
                    sp = sp,
                    soundKey = KeywordNotificationSettings.KEY_KEYWORD_NOTIFY_SOUND,
                    vibrateKey = KeywordNotificationSettings.KEY_KEYWORD_NOTIFY_VIBRATE,
                    ringtoneKey = KeywordNotificationSettings.KEY_KEYWORD_NOTIFY_RINGTONE
                )
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "@我通知") }
            item {
                KeywordNotificationAlertCard(
                    context = context,
                    sp = sp,
                    soundKey = KeywordNotificationSettings.KEY_AT_ME_NOTIFY_SOUND,
                    vibrateKey = KeywordNotificationSettings.KEY_AT_ME_NOTIFY_VIBRATE,
                    ringtoneKey = KeywordNotificationSettings.KEY_AT_ME_NOTIFY_RINGTONE
                )
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "@所有人/群公告") }
            item {
                KeywordNotificationAlertCard(
                    context = context,
                    sp = sp,
                    soundKey = KeywordNotificationSettings.KEY_AT_ALL_NOTIFY_SOUND,
                    vibrateKey = KeywordNotificationSettings.KEY_AT_ALL_NOTIFY_VIBRATE,
                    ringtoneKey = KeywordNotificationSettings.KEY_AT_ALL_NOTIFY_RINGTONE
                )
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "生效范围") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = filterMode,
                        title = if (filterMode) "仅生效模式" else "排除模式",
                        summary = if (filterMode) "只处理名单里的聊天" else "不处理名单里的聊天"
                    ) {
                        filterMode = it
                        sp.edit().putBoolean(KeywordNotificationSettings.KEY_FILTER_MODE, it).apply()
                    }
                    InsetDivider()
                    ActionRow(
                        if (filterMode) "仅生效名单" else "排除名单",
                        if (activeContacts.isEmpty()) "未设置" else "${activeContacts.size} 个聊天"
                    ) {
                        onPickContacts(filterMode)
                    }
                    if (activeContacts.isNotEmpty()) {
                        InsetDivider()
                        ActionRow("清空当前名单", if (filterMode) "清空仅生效名单" else "清空排除名单") {
                            onClearContacts(filterMode)
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "免打扰") }
            item {
                SettingsCard {
                    SwitchRow(quietEnabled, "启用免打扰", "指定时间内不提醒") {
                        quietEnabled = it
                        sp.edit().putBoolean(KeywordNotificationSettings.KEY_QUIET, it).apply()
                    }
                    if (quietEnabled) {
                        InsetDivider()
                        TimeOfDayPickerRow("开始时间", quietStart) {
                            quietStart = it
                            sp.edit().putString(KeywordNotificationSettings.KEY_QUIET_START, quietStart).apply()
                        }
                        InsetDivider()
                        TimeOfDayPickerRow("结束时间", quietEnd) {
                            quietEnd = it
                            sp.edit().putString(KeywordNotificationSettings.KEY_QUIET_END, quietEnd).apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeywordNotificationAlertCard(
    context: Context,
    sp: SharedPreferences,
    soundKey: String,
    vibrateKey: String,
    ringtoneKey: String
) {
    var notifySound by remember(soundKey) {
        mutableStateOf(sp.getBoolean(soundKey, sp.getBoolean(KeywordNotificationSettings.KEY_NOTIFY_SOUND, KeywordNotificationSettings.DEFAULT_NOTIFY_SOUND)))
    }
    var notifyVibrate by remember(vibrateKey) {
        mutableStateOf(sp.getBoolean(vibrateKey, sp.getBoolean(KeywordNotificationSettings.KEY_NOTIFY_VIBRATE, KeywordNotificationSettings.DEFAULT_NOTIFY_VIBRATE)))
    }
    var notifyRingtone by remember(ringtoneKey) {
        mutableStateOf(sp.getString(ringtoneKey, sp.getString(KeywordNotificationSettings.KEY_NOTIFY_RINGTONE, "") ?: "").orEmpty())
    }
    SettingsCard {
        SwitchRow(
            checked = notifyVibrate,
            title = "通知震动",
            summary = "触发该类型通知时震动"
        ) {
            notifyVibrate = it
            sp.edit().putBoolean(vibrateKey, it).apply()
        }
        InsetDivider()
        SwitchRow(
            checked = notifySound,
            title = "通知铃声",
            summary = "触发该类型通知时播放系统或自定义铃声"
        ) {
            notifySound = it
            sp.edit().putBoolean(soundKey, it).apply()
        }
        if (notifySound) {
            InsetDivider()
            ActionRow("选择系统铃声", ringtoneDisplayName(context, notifyRingtone, RedPacketSettings.NOTIFY_SOUND_MODE_SYSTEM)) {
                val activity = context as? Activity
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开铃声选择器", Toast.LENGTH_SHORT).show()
                } else {
                    RingtonePickerBridge.launchSystem(activity, notifyRingtone) { picked ->
                        notifyRingtone = CustomNotificationRuntime.freezeRingtoneUri(context, picked)
                        sp.edit().putString(ringtoneKey, notifyRingtone).apply()
                        Toast.makeText(context, "铃声已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            InsetDivider()
            ActionRow("从文件选择铃声", if (notifyRingtone.isBlank()) "未选择" else ringtoneDisplayName(context, notifyRingtone, RedPacketSettings.NOTIFY_SOUND_MODE_CUSTOM)) {
                val activity = context as? Activity
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                } else {
                    RingtonePickerBridge.launchFile(activity) { picked ->
                        notifyRingtone = CustomNotificationRuntime.freezeRingtoneUri(context, picked)
                        sp.edit().putString(ringtoneKey, notifyRingtone).apply()
                        Toast.makeText(context, "铃声已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (notifyRingtone.isNotBlank()) {
                InsetDivider()
                ActionRow("清空铃声", "恢复跟随系统") {
                    notifyRingtone = ""
                    sp.edit().putString(ringtoneKey, "").apply()
                    Toast.makeText(context, "铃声已清空", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
internal fun KeywordNotificationKeywordListPage(
    keywords: List<KeywordRule>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (KeywordRule) -> Unit,
    onClear: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val lower = query.trim().lowercase(Locale.US)
    val visible = keywords.filter {
        lower.isBlank() || it.keyword.lowercase(Locale.US).contains(lower)
    }
    PageScaffold(
        title = "关键词管理",
        largeTitle = "关键词管理",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "添加关键词",
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
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item {
                SettingsCard {
                    InputRow("搜索", "关键词", query, onValueChange = onQueryChange)
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = if (visible.isEmpty()) "关键词" else "关键词 · ${visible.size} 个") }
            when {
                keywords.isEmpty() -> item { SettingsCard { EmptyText("暂无关键词，点击底部“添加关键词”。") } }
                visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                else -> visible.forEach { rule ->
                    item {
                        SettingsCard {
                            ActionRow(rule.keyword, if (rule.wholeWord) "全字匹配" else "模糊匹配") {
                                onEdit(rule)
                            }
                        }
                    }
                }
            }
            if (keywords.isNotEmpty()) {
                item {
                    SettingsCard {
                        ActionRow("清空所有关键词", "移除全部关键词") {
                            onClear()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeywordNotificationKeywordEditorPage(
    initial: KeywordRule?,
    onBack: () -> Unit,
    onSave: (KeywordRule) -> Unit,
    onDelete: (() -> Unit)?
) {
    var keyword by remember(initial) { mutableStateOf(initial?.keyword.orEmpty()) }
    var wholeWord by remember(initial) { mutableStateOf(initial?.wholeWord ?: false) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = if (initial == null) "添加关键词" else "编辑关键词",
        largeTitle = if (initial == null) "添加关键词" else "编辑关键词",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = { onSave(KeywordRule(keyword.trim(), wholeWord)) },
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
            item { SmallTitle(text = "关键词") }
            item {
                SettingsCard {
                    InputRow("关键词", "输入要监控的关键词", keyword) { keyword = it }
                    InsetDivider()
                    SwitchRow(wholeWord, "全字匹配", "关闭为模糊匹配，包含关键词即可触发") { wholeWord = it }
                }
            }
            if (onDelete != null) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除关键词", "移除该关键词") {
                            onDelete()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeywordNotificationTemplatePage(
    context: Context,
    sp: SharedPreferences,
    onBack: () -> Unit
) {
    var keywordTitle by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_KEYWORD_TITLE, "").orEmpty()) }
    var keywordContent by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_KEYWORD_CONTENT, "").orEmpty()) }
    var keywordToast by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_KEYWORD_TOAST, "").orEmpty()) }
    var atMeTitle by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ME_TITLE, "").orEmpty()) }
    var atMeContent by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ME_CONTENT, "").orEmpty()) }
    var atMeToast by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ME_TOAST, "").orEmpty()) }
    var atAllTitle by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ALL_TITLE, "").orEmpty()) }
    var atAllContent by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ALL_CONTENT, "").orEmpty()) }
    var atAllToast by remember { mutableStateOf(sp.getString(KeywordNotificationSettings.KEY_AT_ALL_TOAST, "").orEmpty()) }
    val scrollBehavior = MiuixScrollBehavior()
    fun save() {
        sp.edit()
            .putString(KeywordNotificationSettings.KEY_KEYWORD_TITLE, keywordTitle)
            .putString(KeywordNotificationSettings.KEY_KEYWORD_CONTENT, keywordContent)
            .putString(KeywordNotificationSettings.KEY_KEYWORD_TOAST, keywordToast)
            .putString(KeywordNotificationSettings.KEY_AT_ME_TITLE, atMeTitle)
            .putString(KeywordNotificationSettings.KEY_AT_ME_CONTENT, atMeContent)
            .putString(KeywordNotificationSettings.KEY_AT_ME_TOAST, atMeToast)
            .putString(KeywordNotificationSettings.KEY_AT_ALL_TITLE, atAllTitle)
            .putString(KeywordNotificationSettings.KEY_AT_ALL_CONTENT, atAllContent)
            .putString(KeywordNotificationSettings.KEY_AT_ALL_TOAST, atAllToast)
            .apply()
        Toast.makeText(context, "模板已保存", Toast.LENGTH_SHORT).show()
        onBack()
    }
    PageScaffold(
        title = "自定义文字",
        largeTitle = "自定义文字",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = ::save,
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = "恢复默认",
                onMiddleClick = {
                    keywordTitle = ""
                    keywordContent = ""
                    keywordToast = ""
                    atMeTitle = ""
                    atMeContent = ""
                    atMeToast = ""
                    atAllTitle = ""
                    atAllContent = ""
                    atAllToast = ""
                }
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
            item { SmallTitle(text = "关键词通知") }
            item {
                SettingsCard {
                    VariableInputRow("通知标题模板", "默认：${KeywordNotificationSettings.DEFAULT_KEYWORD_TITLE}", keywordTitle, keywordNotificationTemplateVariables) { keywordTitle = it }
                    InsetDivider()
                    VariableInputRow("通知内容模板", "默认：${KeywordNotificationSettings.DEFAULT_KEYWORD_CONTENT}", keywordContent, keywordNotificationTemplateVariables, minLines = 3) { keywordContent = it }
                    InsetDivider()
                    VariableInputRow("Toast 文字模板", "默认：${KeywordNotificationSettings.DEFAULT_KEYWORD_TOAST}", keywordToast, keywordNotificationTemplateVariables) { keywordToast = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "@我通知") }
            item {
                SettingsCard {
                    VariableInputRow("通知标题模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ME_TITLE}", atMeTitle, keywordNotificationTemplateVariables) { atMeTitle = it }
                    InsetDivider()
                    VariableInputRow("通知内容模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ME_CONTENT}", atMeContent, keywordNotificationTemplateVariables, minLines = 3) { atMeContent = it }
                    InsetDivider()
                    VariableInputRow("Toast 文字模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ME_TOAST}", atMeToast, keywordNotificationTemplateVariables) { atMeToast = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "@所有人/群公告通知") }
            item {
                SettingsCard {
                    VariableInputRow("通知标题模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ALL_TITLE}", atAllTitle, keywordNotificationTemplateVariables) { atAllTitle = it }
                    InsetDivider()
                    VariableInputRow("通知内容模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ALL_CONTENT}", atAllContent, keywordNotificationTemplateVariables, minLines = 3) { atAllContent = it }
                    InsetDivider()
                    VariableInputRow("Toast 文字模板", "默认：${KeywordNotificationSettings.DEFAULT_AT_ALL_TOAST}", atAllToast, keywordNotificationTemplateVariables) { atAllToast = it }
                }
            }
        }
    }
}

@Composable
internal fun QuoteDeleteClearMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuoteDeleteClearSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天输入框") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuoteDeleteClearSettings.KEY_ENABLE,
                        "删除键清引用",
                        "输入框为空且已引用消息时，按输入法删除键直接取消引用",
                        QuoteDeleteClearSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmojiSaveMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, EmojiSaveSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天表情") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        EmojiSaveSettings.KEY_ENABLE,
                        "保存表情",
                        "长按聊天表情后显示保存入口",
                        EmojiSaveSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun SwipeQuoteMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, SwipeQuoteSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "引用") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SwipeQuoteSettings.KEY_ENABLE,
                        "左滑引用回复",
                        "左滑消息后调用微信原生引用入口",
                        SwipeQuoteSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "复读触发方式") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SwipeQuoteSettings.KEY_REPEAT_ENABLE,
                        "右滑复读",
                        "右滑消息后复读到当前聊天",
                        SwipeQuoteSettings.DEFAULT_REPEAT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        SwipeQuoteSettings.KEY_REPEAT_MENU_ENABLE,
                        "长按菜单复读",
                        "长按消息后点击 +1 复读到当前聊天",
                        SwipeQuoteSettings.DEFAULT_REPEAT_MENU_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun FakeVoiceDurationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FakeVoiceDurationSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                FakeVoiceDurationSettings.KEY_ENABLE,
                FakeVoiceDurationSettings.DEFAULT_ENABLE
            )
        )
    }
    var durationSeconds by remember {
        mutableStateOf(
            sp.getInt(
                FakeVoiceDurationSettings.KEY_DURATION_SECONDS,
                FakeVoiceDurationSettings.DEFAULT_DURATION_SECONDS
            ).toString()
        )
    }

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
            item { SmallTitle(text = "聊天语音") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "伪造语音时长",
                        summary = "发送语音时使用自定义显示时长",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(FakeVoiceDurationSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    if (enabled) {
                        InsetDivider()
                        NumberInputRow("显示时长", "单位秒，1-60", durationSeconds) { next ->
                            val value = next.toIntOrNull()
                            durationSeconds = value?.coerceIn(
                                FakeVoiceDurationSettings.MIN_DURATION_SECONDS,
                                FakeVoiceDurationSettings.MAX_DURATION_SECONDS
                            )?.toString() ?: next
                            if (value == null) return@NumberInputRow
                            sp.edit().putInt(
                                FakeVoiceDurationSettings.KEY_DURATION_SECONDS,
                                value.coerceIn(
                                    FakeVoiceDurationSettings.MIN_DURATION_SECONDS,
                                    FakeVoiceDurationSettings.MAX_DURATION_SECONDS
                                )
                            ).apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VoicePreviewMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, VoicePreviewSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天语音") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        VoicePreviewSettings.KEY_ENABLE,
                        "语音消息预览",
                        "长按聊天语音后查看时长并播放",
                        VoicePreviewSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun VoiceToTextMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, t.mma.hooks.items.voicetotext.VoiceToTextFeature.PREFS) }
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
            item { SmallTitle(text = "聊天语音") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        t.mma.hooks.items.voicetotext.VoiceToTextFeature.ENABLE,
                        "语音转文字",
                        "聊天中收到的语音自动显示文字",
                        false
                    )
                }
            }
        }
    }
}

@Composable
internal fun FakeMiniProgramBaseLibMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FakeMiniProgramBaseLibSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                FakeMiniProgramBaseLibSettings.KEY_ENABLE,
                FakeMiniProgramBaseLibSettings.DEFAULT_ENABLE
            )
        )
    }
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
            item { SmallTitle(text = "小程序") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "兼容低版本小程序",
                        summary = "伪装启动基础库版本并阻止官方升级页",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(FakeMiniProgramBaseLibSettings.KEY_ENABLE, it).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SkipMiniProgramVideoAdsMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember {
        HchatStorage.preferences(context, SkipMiniProgramVideoAdsSettings.PREFS_NAME)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                SkipMiniProgramVideoAdsSettings.KEY_ENABLE,
                SkipMiniProgramVideoAdsSettings.DEFAULT_ENABLE
            )
        )
    }
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
            item { SmallTitle(text = "小程序") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "跳过小程序视频广告",
                        summary = "自动跳过小程序视频广告",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(
                                SkipMiniProgramVideoAdsSettings.KEY_ENABLE,
                                it
                            ).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SkipGlobalMiniProgramSplashAdsMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember {
        HchatStorage.preferences(context, SkipGlobalMiniProgramSplashAdsSettings.PREFS_NAME)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                SkipGlobalMiniProgramSplashAdsSettings.KEY_ENABLE,
                SkipGlobalMiniProgramSplashAdsSettings.DEFAULT_ENABLE
            )
        )
    }
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
            item { SmallTitle(text = "小程序") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "跳过全局小程序开屏广告",
                        summary = "阻止所有小程序展示启动开屏广告，修改后需重启微信",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(
                                SkipGlobalMiniProgramSplashAdsSettings.KEY_ENABLE,
                                it
                            ).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FakeLocationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FakeLocationSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(FakeLocationSettings.KEY_ENABLE, FakeLocationSettings.DEFAULT_ENABLE))
    }
    var latitude by remember {
        mutableStateOf(sp.getString(FakeLocationSettings.KEY_LATITUDE, FakeLocationSettings.DEFAULT_LATITUDE).orEmpty())
    }
    var longitude by remember {
        mutableStateOf(sp.getString(FakeLocationSettings.KEY_LONGITUDE, FakeLocationSettings.DEFAULT_LONGITUDE).orEmpty())
    }
    var showManualInput by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun saveLocation(lat: Double, lon: Double) {
        latitude = formatFakeLocationCoordinate(lat)
        longitude = formatFakeLocationCoordinate(lon)
        sp.edit()
            .putString(FakeLocationSettings.KEY_LATITUDE, latitude)
            .putString(FakeLocationSettings.KEY_LONGITUDE, longitude)
            .apply()
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
            item { SmallTitle(text = "定位") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "启用虚拟定位",
                        summary = "同时覆盖微信和小程序获取的位置",
                        onCheckedChange = {
                            enabled = it
                            sp.edit().putBoolean(FakeLocationSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    InsetDivider()
                    ActionRow("搜索并选择位置", "打开微信选择位置页，可搜索地点") {
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开微信地图", Toast.LENGTH_SHORT).show()
                        } else {
                            NativeFakeLocationPickerBridge.launch(activity) { lat, lon ->
                                saveLocation(lat, lon)
                                Toast.makeText(context, "已保存虚拟定位", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    InsetDivider()
                    ActionRow("手动输入经纬度", "纬度 -90~90，经度 -180~180") {
                        showManualInput = true
                    }
                    InsetDivider()
                    InfoRow("当前纬度", latitude) {
                        copyFakeLocationCoordinate(context, "Hchat 纬度", latitude)
                    }
                    InsetDivider()
                    InfoRow("当前经度", longitude) {
                        copyFakeLocationCoordinate(context, "Hchat 经度", longitude)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "说明") }
            item {
                SettingsCard {
                    InfoRow("生效范围", "微信和小程序定位")
                }
            }
        }
    }

    if (showManualInput) {
        FakeLocationManualInputDialog(
            initialLatitude = latitude,
            initialLongitude = longitude,
            onDismiss = { showManualInput = false },
            onConfirm = { lat, lon ->
                saveLocation(lat, lon)
                showManualInput = false
                Toast.makeText(context, "已保存虚拟定位", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
internal fun GameEmojiMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, GameEmojiSettings.PREFS_NAME) }
    var fixedResult by remember {
        mutableStateOf(
            sp.getBoolean(
                GameEmojiSettings.KEY_FIXED_RESULT,
                GameEmojiSettings.DEFAULT_FIXED_RESULT
            )
        )
    }
    var pickBeforeSend by remember {
        mutableStateOf(
            sp.getBoolean(
                GameEmojiSettings.KEY_PICK_BEFORE_SEND,
                GameEmojiSettings.DEFAULT_PICK_BEFORE_SEND
            )
        )
    }
    var diceResult by remember {
        mutableStateOf(
            sp.getInt(GameEmojiSettings.KEY_DICE_RESULT, GameEmojiSettings.DEFAULT_DICE_RESULT)
                .coerceIn(1, 6)
        )
    }
    var rpsResult by remember {
        mutableStateOf(
            sp.getInt(GameEmojiSettings.KEY_RPS_RESULT, GameEmojiSettings.DEFAULT_RPS_RESULT)
                .coerceIn(GameEmojiSettings.RPS_SCISSORS, GameEmojiSettings.RPS_PAPER)
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
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "发送方式") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = fixedResult,
                        title = "使用固定结果",
                        summary = "直接发送下方设置的骰子点数或猜拳结果",
                        onCheckedChange = { checked ->
                            fixedResult = checked
                            if (checked) pickBeforeSend = false
                            sp.edit()
                                .putBoolean(GameEmojiSettings.KEY_FIXED_RESULT, checked)
                                .putBoolean(
                                    GameEmojiSettings.KEY_PICK_BEFORE_SEND,
                                    if (checked) false else pickBeforeSend
                                )
                                .apply()
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = pickBeforeSend,
                        title = "发送时选择",
                        summary = "每次发送骰子或猜拳前弹出结果选择",
                        onCheckedChange = { checked ->
                            pickBeforeSend = checked
                            if (checked) fixedResult = false
                            sp.edit()
                                .putBoolean(GameEmojiSettings.KEY_PICK_BEFORE_SEND, checked)
                                .putBoolean(
                                    GameEmojiSettings.KEY_FIXED_RESULT,
                                    if (checked) false else fixedResult
                                )
                                .apply()
                        }
                    )
                }
            }
            if (fixedResult) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "固定结果") }
                item {
                    SettingsCard {
                        PopupOptionRow(
                            title = "骰子点数",
                            summary = "$diceResult 点",
                            options = (1..6).map { OptionItem("$it 点", it, "") },
                            currentValue = diceResult,
                            onValueChanged = { value ->
                                diceResult = value
                                sp.edit().putInt(GameEmojiSettings.KEY_DICE_RESULT, value).apply()
                            }
                        )
                        InsetDivider()
                        PopupOptionRow(
                            title = "猜拳结果",
                            summary = GameEmojiSettings.rpsLabel(rpsResult),
                            options = listOf(
                                OptionItem("剪刀", GameEmojiSettings.RPS_SCISSORS, ""),
                                OptionItem("石头", GameEmojiSettings.RPS_ROCK, ""),
                                OptionItem("布", GameEmojiSettings.RPS_PAPER, "")
                            ),
                            currentValue = rpsResult,
                            onValueChanged = { value ->
                                rpsResult = value
                                sp.edit().putInt(GameEmojiSettings.KEY_RPS_RESULT, value).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}
