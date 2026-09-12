package h.Hchat.ui.miuix

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
import h.Hchat.BuildConfig
import h.Hchat.R
import h.Hchat.crash.CrashReportRuntime
import h.Hchat.crash.CrashReportSettings
import h.Hchat.crash.CrashReportSettingsProvider
import h.Hchat.hooks.api.ui.HchatAgentIconDrawable
import h.Hchat.hooks.items.specialmessage.SpecialMessageFeature
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutFeature
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutGlyph
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutGlyphDrawable
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutGlyphs
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutIconPickResult
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutIconPicker
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutIconStore
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutItem
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutRuntime
import h.Hchat.hooks.items.floatingshortcut.FloatingShortcutSettings
import h.Hchat.hooks.items.floatingchat.FloatingChatHeaderFeature
import h.Hchat.hooks.items.floatingchat.FloatingChatFooterFeature
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
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.media.WeChatFavoriteItem
import h.Hchat.hooks.api.message.WeChatMessageObserveApi
import h.Hchat.hooks.api.model.ContactLabelBean
import h.Hchat.hooks.api.model.WeChatContact
import h.Hchat.hooks.items.audiotransform.AudioTransformFeature
import h.Hchat.hooks.items.audiotransform.AudioTransformCancelledException
import h.Hchat.hooks.items.audiotransform.AudioTransformSegmenter
import h.Hchat.hooks.items.audiotransform.AudioTransformSettings
import h.Hchat.hooks.items.antirecall.AntiRecallFeature
import h.Hchat.hooks.items.antirecall.AntiRecallSettings
import h.Hchat.hooks.items.autoreply.AutoReplyFeature
import h.Hchat.hooks.items.autoreply.AutoReplyRule
import h.Hchat.hooks.items.autoreply.AutoReplyRuntime
import h.Hchat.hooks.items.autoreply.AutoReplySettings
import h.Hchat.hooks.items.autoreply.AutoReplyStep
import h.Hchat.hooks.items.autoreply.XiaozhiMcpStatus
import h.Hchat.hooks.items.autoreply.AutoReplyXiaozhiConfig
import h.Hchat.hooks.items.autoreply.AutoReplyZhiliaConfig
import h.Hchat.hooks.items.autoreply.XiaozhiAgentOption
import h.Hchat.hooks.items.autoreply.XiaozhiConsoleApi
import h.Hchat.hooks.items.autoreply.XiaozhiModelOption
import h.Hchat.hooks.items.autoreply.XiaozhiVoiceOption
import h.Hchat.hooks.items.autooriginal.AutoOriginalImageFeature
import h.Hchat.hooks.items.autooriginal.AutoOriginalImageSettings
import h.Hchat.hooks.items.autovieworiginal.AutoViewOriginalFeature
import h.Hchat.hooks.items.autovieworiginal.AutoViewOriginalSettings
import h.Hchat.hooks.items.atallnotify.AtAllNotificationBlockFeature
import h.Hchat.hooks.items.atallnotify.AtAllNotificationBlockSettings
import h.Hchat.hooks.items.backgroundbeauty.BackgroundBeautyFeature
import h.Hchat.hooks.items.backgroundbeauty.BackgroundBeautyPickResult
import h.Hchat.hooks.items.backgroundbeauty.BackgroundBeautyPicker
import h.Hchat.hooks.items.backgroundbeauty.BackgroundBeautySettings
import h.Hchat.hooks.items.backgroundbeauty.BackgroundBeautyStore
import h.Hchat.hooks.items.automessageforward.AutoMessageForwardFeature
import h.Hchat.hooks.items.automessageforward.AutoMessageForwardRule
import h.Hchat.hooks.items.automessageforward.AutoMessageForwardSettings
import h.Hchat.hooks.items.callmedialimit.CallMediaLimitFeature
import h.Hchat.hooks.items.callmedialimit.CallMediaLimitSettings
import h.Hchat.hooks.items.callmedialimit.CallRingtoneBlockFeature
import h.Hchat.hooks.items.chattime.ChatTimeStyleFeature
import h.Hchat.hooks.items.chattime.ChatTimeStyleSettings
import h.Hchat.hooks.items.conversationgroup.ConversationGroup
import h.Hchat.hooks.items.conversationgroup.ConversationGroupFeature
import h.Hchat.hooks.items.conversationgroup.ConversationGroupPickerSupport
import h.Hchat.hooks.items.conversationgroup.ConversationGroupRuntime
import h.Hchat.hooks.items.conversationgroup.ConversationGroupStore
import h.Hchat.hooks.items.custombottombar.CustomBottomBarFeature
import h.Hchat.hooks.items.custombottombar.FloatingBottomBarSettings
import h.Hchat.hooks.items.customnotify.CustomNotificationFeature
import h.Hchat.hooks.items.customnotify.CustomNotificationRule
import h.Hchat.hooks.items.customnotify.CustomNotificationRuntime
import h.Hchat.hooks.items.customfriendavatar.CustomFriendAvatarFeature
import h.Hchat.hooks.items.customfriendavatar.CustomFriendAvatarPicker
import h.Hchat.hooks.items.customfriendavatar.CustomFriendAvatarSettings
import h.Hchat.hooks.items.customfriendavatar.CustomFriendAvatarStore
import h.Hchat.hooks.items.customnotify.CustomNotificationSettings
import h.Hchat.hooks.items.editmsg.EditMessageFeature
import h.Hchat.hooks.items.editmsg.EditMessageSettings
import h.Hchat.hooks.items.emojisave.EmojiSaveFeature
import h.Hchat.hooks.items.emojisave.EmojiSaveSettings
import h.Hchat.hooks.items.fakelocation.FakeLocationFeature
import h.Hchat.hooks.items.fakelocation.FakeLocationSettings
import h.Hchat.hooks.items.fakescancamera.FakeScanCameraFeature
import h.Hchat.hooks.items.fakescancamera.FakeScanCameraSettings
import h.Hchat.hooks.items.fakevoiceduration.FakeVoiceDurationFeature
import h.Hchat.hooks.items.fakevoiceduration.FakeVoiceDurationSettings
import h.Hchat.hooks.items.forwardlimit.RemoveForwardLimitFeature
import h.Hchat.hooks.items.forwardlimit.RemoveForwardLimitSettings
import h.Hchat.hooks.items.gameemoji.GameEmojiFeature
import h.Hchat.hooks.items.gameemoji.GameEmojiSettings
import h.Hchat.hooks.items.groupleave.GroupLeaveMonitorFeature
import h.Hchat.hooks.items.groupleave.GroupLeaveReplyTemplate
import h.Hchat.hooks.items.groupleave.GroupLeaveReplyTemplateBinding
import h.Hchat.hooks.items.groupleave.GroupLeaveMonitorSettings
import h.Hchat.hooks.items.grouplabel.GroupChatLabel
import h.Hchat.hooks.items.grouplabel.GroupChatLabelFeature
import h.Hchat.hooks.items.grouplabel.GroupChatLabelStore
import h.Hchat.hooks.items.grouplabel.QuickGroupChatLabelFeature
import h.Hchat.hooks.items.grouplabel.QuickGroupChatLabelSettings
import h.Hchat.hooks.items.groupnicknamecolor.GroupNicknameColorSettings
import h.Hchat.hooks.items.groupnicknamecolor.GroupNicknameColorStore
import h.Hchat.hooks.items.grouprename.GroupRenameMonitorFeature
import h.Hchat.hooks.items.grouprename.GroupRenameReplyTemplate
import h.Hchat.hooks.items.grouprename.GroupRenameMonitorSettings
import h.Hchat.hooks.items.grouprename.GroupRenameTemplateBinding
import h.Hchat.hooks.items.hideavatar.HideChatAvatarFeature
import h.Hchat.hooks.items.hideavatar.HideChatAvatarSettings
import h.Hchat.hooks.items.hidemenu.HideChatMenuFeature
import h.Hchat.hooks.items.hidemenu.HideChatMenuSettings
import h.Hchat.hooks.items.hotupdate.DisableHotUpdateFeature
import h.Hchat.hooks.items.hotupdate.DisableHotUpdateSettings
import h.Hchat.hooks.items.hometextcolor.HomeTextColorFeature
import h.Hchat.hooks.items.hometextcolor.HomeTextColorSettings
import h.Hchat.hooks.items.inputhint.InputHintFeature
import h.Hchat.hooks.items.inputhint.InputHintSettings
import h.Hchat.hooks.items.inputhint.InputHintStats
import h.Hchat.hooks.items.inputhint.OutgoingMessageStatsRepository
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveFeature
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveRuntime
import h.Hchat.hooks.items.keepalive.WeChatKeepAliveSettings
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationFeature
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationRuntime
import h.Hchat.hooks.items.keywordnotify.KeywordNotificationSettings
import h.Hchat.hooks.items.keywordnotify.KeywordRule
import h.Hchat.hooks.items.zombiecheck.ZombieCheckController
import h.Hchat.hooks.items.zombiecheck.ZombieCheckActionResult
import h.Hchat.hooks.items.zombiecheck.ZombieCheckFeature
import h.Hchat.hooks.items.zombiecheck.ZombieCheckResultType
import h.Hchat.hooks.items.zombiecheck.ZombieCheckSettings
import h.Hchat.hooks.items.membertitle.MemberTitleFeature
import h.Hchat.hooks.items.membertitle.MemberTitleSettings
import h.Hchat.hooks.items.membertitle.MemberTitleStore
import h.Hchat.hooks.items.messageaffix.MessageAffixFeature
import h.Hchat.hooks.items.messageaffix.MessageAffixSettings
import h.Hchat.hooks.items.messagebubble.MessageBubbleFeature
import h.Hchat.hooks.items.messagebubble.MessageBubblePickResult
import h.Hchat.hooks.items.messagebubble.MessageBubblePicker
import h.Hchat.hooks.items.messagebubble.MessageBubbleSettings
import h.Hchat.hooks.items.messagebubble.MessageBubbleSlot
import h.Hchat.hooks.items.messagebubble.MessageBubbleStore
import h.Hchat.hooks.items.messagebubble.MessageBubbleKind
import h.Hchat.hooks.items.messagetextcolor.MessageTextColorFeature
import h.Hchat.hooks.items.messagetextcolor.MessageTextColorSettings
import h.Hchat.hooks.items.messageblock.MessageBlockBinding
import h.Hchat.hooks.items.messageblock.MessageBlockDefaultRule
import h.Hchat.hooks.items.messageblock.MessageBlockFeature
import h.Hchat.hooks.items.messageblock.MessageBlockSettings
import h.Hchat.hooks.items.messageblock.MessageBlockTemplate
import h.Hchat.hooks.items.messageforward.MessageForwardFeature
import h.Hchat.hooks.items.messageforward.MessageForwardSettings
import h.Hchat.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibFeature
import h.Hchat.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibSettings
import h.Hchat.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsFeature
import h.Hchat.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsSettings
import h.Hchat.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsFeature
import h.Hchat.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsSettings
import h.Hchat.hooks.items.musicorder.QQMusicOrderFeature
import h.Hchat.hooks.items.musicorder.QQMusicOrderSettings
import h.Hchat.hooks.items.moments.OriginalMomentsUploadFeature
import h.Hchat.hooks.items.moments.OriginalMomentsUploadSettings
import h.Hchat.hooks.items.moments.MomentsAutoCommentFeature
import h.Hchat.hooks.items.moments.MomentsAutoCommentSettings
import h.Hchat.hooks.items.moments.MomentsAutoLikeFeature
import h.Hchat.hooks.items.moments.MomentsAutoLikeSettings
import h.Hchat.hooks.items.moments.MomentsAutoForwardFeature
import h.Hchat.hooks.items.moments.MomentsAutoForwardSettings
import h.Hchat.hooks.items.moments.MomentsAutoRefreshFeature
import h.Hchat.hooks.items.moments.MomentsAutoRefreshSettings
import h.Hchat.hooks.items.moments.MomentsBottomDetailFeature
import h.Hchat.hooks.items.moments.MomentsBottomDetailSettings
import h.Hchat.hooks.items.moments.MomentsContactFilterFeature
import h.Hchat.hooks.items.moments.MomentsContactFilterSettings
import h.Hchat.hooks.items.moments.MomentsKeywordBlockFeature
import h.Hchat.hooks.items.moments.MomentsKeywordBlockSettings
import h.Hchat.hooks.items.moments.MomentsPostNotificationFeature
import h.Hchat.hooks.items.moments.MomentsPostNotificationSettings
import h.Hchat.hooks.items.moments.MomentsUploadTailFeature
import h.Hchat.hooks.items.moments.MomentsUploadTailSettings
import h.Hchat.hooks.items.moments.RemoveMomentsAdsFeature
import h.Hchat.hooks.items.moments.RemoveMomentsAdsSettings
import h.Hchat.hooks.items.moments.SnsAntiRecallFeature
import h.Hchat.hooks.items.moments.SnsAntiRecallSettings
import h.Hchat.hooks.items.momentsfake.MomentsFakeCommentSettingsProvider
import h.Hchat.hooks.items.momentsfake.MomentsFakeForwardRuntimeRegistry
import h.Hchat.hooks.items.momentsfake.MomentsFakeForwardSettingsProvider
import h.Hchat.hooks.items.momentsfake.MomentsFakeLikeSettingsProvider
import h.Hchat.hooks.items.momentsfake.MomentsFakeLikeCandidateRepository
import h.Hchat.hooks.items.momentsfake.MomentsFakeInteractionRuntimeRegistry
import h.Hchat.hooks.items.momentsfake.MomentsFakeInteractionSettings
import h.Hchat.hooks.items.multirecall.MultiRecallFeature
import h.Hchat.hooks.items.multirecall.MultiRecallSettings
import h.Hchat.hooks.items.payment.core.AutoRedPacketFeature
import h.Hchat.hooks.items.payment.core.PaymentTemplateTimeFormatter
import h.Hchat.hooks.items.payment.core.RedPacketReplyStep
import h.Hchat.hooks.items.payment.core.RedPacketRuleBinding
import h.Hchat.hooks.items.payment.core.RedPacketRuleConfig
import h.Hchat.hooks.items.payment.core.RedPacketRuleTemplate
import h.Hchat.hooks.items.payment.core.RedPacketSettings
import h.Hchat.hooks.items.payment.fakebalance.FakeWalletBalanceFeature
import h.Hchat.hooks.items.payment.fakebalance.FakeWalletBalanceSettings
import h.Hchat.hooks.items.payment.transfer.AutoTransferFeature
import h.Hchat.hooks.items.payment.transfer.AutoTransferSettings
import h.Hchat.hooks.items.payment.transfer.TransferReceiveAccountStore
import h.Hchat.hooks.items.payment.transfer.TransferRuleBinding
import h.Hchat.hooks.items.payment.transfer.TransferRuleConfig
import h.Hchat.hooks.items.payment.transfer.TransferRuleTemplate
import h.Hchat.hooks.items.patblock.PatBlockFeature
import h.Hchat.hooks.items.patblock.PatBlockSettings
import h.Hchat.hooks.items.profileid.ProfileIdFeature
import h.Hchat.hooks.items.profileid.ProfileIdSettings
import h.Hchat.hooks.items.profileid.ProfileAddTimeSettings
import h.Hchat.hooks.items.protobuf.ProtobufPacketFeature
import h.Hchat.hooks.items.protobuf.ProtobufPacketRuntime
import h.Hchat.hooks.items.protobuf.ProtobufPacketSettings
import h.Hchat.hooks.items.quoteclear.QuoteDeleteClearFeature
import h.Hchat.hooks.items.quoteclear.QuoteDeleteClearSettings
import h.Hchat.hooks.items.quickcontactedit.QuickContactEditFeature
import h.Hchat.hooks.items.quickcontactedit.QuickContactEditSettings
import h.Hchat.hooks.items.quickmoments.QuickMomentsFeature
import h.Hchat.hooks.items.quickmoments.QuickMomentsSettings
import h.Hchat.hooks.items.quickread.QuickMarkReadFeature
import h.Hchat.hooks.items.quickread.QuickMarkReadSettings
import h.Hchat.hooks.items.quickterminate.QuickTerminateFeature
import h.Hchat.hooks.items.quickterminate.QuickTerminateSettings
import h.Hchat.hooks.items.realtail.RealNameTailFeature
import h.Hchat.hooks.items.realtail.RealNameTailSettings
import h.Hchat.hooks.items.realtail.RealNameTailStore
import h.Hchat.hooks.items.roundavatar.RoundAvatarFeature
import h.Hchat.hooks.items.roundavatar.RoundAvatarSettings
import h.Hchat.hooks.items.script.ScriptPluginFeature
import h.Hchat.hooks.items.script.ScriptPluginManager
import h.Hchat.hooks.items.script.ScriptPluginRuntime
import h.Hchat.hooks.items.script.ScriptPluginSettings
import h.Hchat.hooks.items.script.market.PluginMarketHistoryVersion
import h.Hchat.hooks.items.script.market.PluginMarketInstaller
import h.Hchat.hooks.items.script.market.PluginMarketException
import h.Hchat.hooks.items.script.market.PluginMarketFile
import h.Hchat.hooks.items.script.market.PluginMarketComment
import h.Hchat.hooks.items.script.market.PluginMarketNotification
import h.Hchat.hooks.items.script.market.PluginMarketPlugin
import h.Hchat.hooks.items.script.market.PluginMarketRepository
import h.Hchat.hooks.items.script.market.PluginMarketReviewStatus
import h.Hchat.hooks.items.script.market.PluginMarketUserIdentity
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentClient
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentAttachment
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentChatMessage
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentConfig
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentCancellation
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentContext
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentDiff
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentDraft
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentEventIds
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentLocalFiles
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentMcpServer
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentRequest
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentResumeState
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentSession
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentSessionStore
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentSettings
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentProfile
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentQuotedMessage
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentSpeech
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentTextMerge
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentToolEvent
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentToolResultStore
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentValidator
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentWorkspaceChange
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentWorkspaceToolConfirmation
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentWorkspaceWriteDecision
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentWorkspaceTools
import h.Hchat.hooks.items.script.agent.ScriptPluginAgentWriter
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskContentItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskFeature
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskItem
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskRuntimeCoordinator
import h.Hchat.hooks.items.scheduledtask.ScheduledTaskSettings
import h.Hchat.hooks.items.selectedmessages.SelectedMessageSnapshot
import h.Hchat.hooks.items.selectedmessages.SelectedMessagesFeature
import h.Hchat.hooks.items.selectedmessages.SelectedMessagesRuntimeCoordinator
import h.Hchat.hooks.items.selectedmessages.SelectedMessagesSettings
import h.Hchat.hooks.items.settings.PluginAgentEntryProvider
import h.Hchat.hooks.items.settings.SettingsEntrySettings
import h.Hchat.hooks.items.settings.SettingsFeature
import h.Hchat.hooks.items.shortvideo.FinderMediaDownloadFeature
import h.Hchat.hooks.items.shortvideo.FinderMediaDownloadSettings
import h.Hchat.hooks.items.statuslimit.StatusTextLimitFeature
import h.Hchat.hooks.items.statuslimit.StatusTextLimitSettings
import h.Hchat.hooks.items.swipequote.SwipeQuoteFeature
import h.Hchat.hooks.items.swipequote.SwipeQuoteSettings
import h.Hchat.hooks.items.tablet.WeChatTabletFeature
import h.Hchat.hooks.items.tablet.WeChatTabletSettings
import h.Hchat.hooks.items.textspeech.TextSpeechFeature
import h.Hchat.hooks.items.textspeech.TextSpeechEngineCatalog
import h.Hchat.hooks.items.textspeech.TextSpeechSettings
import h.Hchat.hooks.items.textspeech.TextSpeechVoiceCatalog
import h.Hchat.hooks.items.textvoice.TextVoiceFeature
import h.Hchat.hooks.items.textvoice.TextVoiceSettings
import h.Hchat.hooks.items.typingreport.TypingReportBlockFeature
import h.Hchat.hooks.items.typingreport.TypingReportBlockSettings
import h.Hchat.hooks.items.voiceforward.VoiceForwardFeature
import h.Hchat.hooks.items.voiceforward.VoiceForwardSettings
import h.Hchat.hooks.items.hchatextra.GroupMemberHistorySettingsProvider
import h.Hchat.hooks.items.hchatextra.MessageDetailsSettingsProvider
import h.Hchat.hooks.items.hchatextra.RedPacketDetailsSettingsProvider
import h.Hchat.hooks.items.hchatextra.SkipWebRiskSettingsProvider
import h.Hchat.hooks.items.hchatextra.HchatExtraSettings
import h.Hchat.hooks.items.voicepreview.VoicePreviewFeature
import h.Hchat.hooks.items.voicepreview.VoicePreviewSettings
import h.Hchat.media.AudioTransformBridge
import h.Hchat.preferences.HchatStorage
import h.Hchat.preferences.TermsGate
import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.UIRegistry
import h.Hchat.utils.KavaReflector
import h.Hchat.utils.KeywordReplacementRule
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

internal fun SelectedMessagesMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    listState: LazyListState,
    onBack: () -> Unit,
    onCompose: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    var officialIntervalMinutes by remember {
        mutableStateOf(
            sp.getInt(
                SelectedMessagesSettings.KEY_OFFICIAL_INTERVAL_MINUTES,
                SelectedMessagesSettings.DEFAULT_OFFICIAL_INTERVAL_MINUTES
            ).coerceIn(0, SelectedMessagesSettings.MAX_OFFICIAL_INTERVAL_MINUTES).toString()
        )
    }
    var sendIntervalSeconds by remember {
        mutableStateOf(
            sp.getInt(
                SelectedMessagesSettings.KEY_SEND_INTERVAL_SECONDS,
                SelectedMessagesSettings.DEFAULT_SEND_INTERVAL_SECONDS
            ).coerceIn(0, SelectedMessagesSettings.MAX_SEND_INTERVAL_SECONDS).toString()
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "多选消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SelectedMessagesSettings.KEY_ENABLE,
                        "群发助手",
                        "在多选消息菜单中显示群发助手[H]和定时转发[H]",
                        SelectedMessagesSettings.DEFAULT_ENABLE
                    )
                    SwitchRow(
                        sp,
                        SelectedMessagesSettings.KEY_BACKGROUND_SILENT_SEND,
                        "后台静默发送",
                        "群发时不显示发送进度窗口",
                        SelectedMessagesSettings.DEFAULT_BACKGROUND_SILENT_SEND
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "发送节奏") }
            item {
                SettingsCard {
                    NumberInputRow(
                        "群发助手间隔延迟",
                        "单位分钟，多个微信原生群发批次之间的等待时间",
                        officialIntervalMinutes
                    ) { value ->
                        officialIntervalMinutes = value
                        value.toIntOrNull()?.let { parsed ->
                            sp.edit().putInt(
                                SelectedMessagesSettings.KEY_OFFICIAL_INTERVAL_MINUTES,
                                parsed.coerceIn(0, SelectedMessagesSettings.MAX_OFFICIAL_INTERVAL_MINUTES)
                            ).apply()
                        }
                    }
                    InsetDivider()
                    NumberInputRow(
                        "群发间隔延迟",
                        "单位秒，群发多条内容或多个目标之间的等待时间",
                        sendIntervalSeconds
                    ) { value ->
                        sendIntervalSeconds = value
                        value.toIntOrNull()?.let { parsed ->
                            sp.edit().putInt(
                                SelectedMessagesSettings.KEY_SEND_INTERVAL_SECONDS,
                                parsed.coerceIn(0, SelectedMessagesSettings.MAX_SEND_INTERVAL_SECONDS)
                            ).apply()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "内容群发") }
            item {
                SettingsCard {
                    ActionRow("自定义群发", "编辑内容并选择模块或微信原生通道") {
                        onCompose()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SelectedMessagesCustomEditorPage(
    draft: ScheduledTaskItem,
    channel: Int,
    listState: LazyListState,
    onBack: () -> Unit,
    onDraftChange: (ScheduledTaskItem) -> Unit,
    onChannelChange: (Int) -> Unit,
    onPickTargets: () -> Unit,
    onPickFiles: (Int, Int) -> Unit,
    onPickFavorite: (Int) -> Unit,
    onSend: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val official = channel == SelectedMessagesRuntimeCoordinator.CHANNEL_OFFICIAL
    val items = scheduledTaskEditableItems(draft)
    PageScaffold(
        title = "自定义群发",
        largeTitle = "自定义群发",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "开始群发",
                onPrimaryClick = onSend,
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
            item { SmallTitle(text = "发送目标") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "发送通道",
                        summary = selectedMessagesChannelLabel(channel),
                        options = selectedMessagesChannelChoices(),
                        currentValue = channel.toString(),
                        onValueChanged = { value ->
                            onChannelChange(
                                value.toIntOrNull()
                                    ?: SelectedMessagesRuntimeCoordinator.CHANNEL_MODULE
                            )
                        }
                    )
                    InsetDivider()
                    ActionRow(
                        if (official) "选择好友" else "选择聊天",
                        scheduledTaskTargetSummary(draft.targetIds)
                    ) {
                        onPickTargets()
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "发送内容") }
            item {
                SettingsCard {
                    ScheduledTaskChatContentRows(
                        task = draft,
                        onTaskChange = onDraftChange,
                        onPickFiles = onPickFiles,
                        onPickFavorite = onPickFavorite,
                        massSendOnly = official
                    )
                }
            }
            if (!official) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "发送节奏") }
                item {
                    SettingsCard {
                        NumberInputRow(
                            "聊天间隔",
                            "单位秒，多个聊天之间的等待时间",
                            draft.intervalSeconds.toString()
                        ) {
                            onDraftChange(
                                draft.copy(intervalSeconds = it.toIntOrNull()?.coerceIn(0, 3600) ?: 0)
                            )
                        }
                        if (items.size > 1) {
                            InsetDivider()
                            NumberInputRow(
                                scheduledTaskItemIntervalTitle(),
                                "单位秒，同一聊天连续发送的等待时间",
                                draft.mediaIntervalSeconds.toString()
                            ) {
                                onDraftChange(
                                    draft.copy(
                                        mediaIntervalSeconds = it.toIntOrNull()?.coerceIn(0, 3600) ?: 0
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun selectedMessagesChannelChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("模块通道", SelectedMessagesRuntimeCoordinator.CHANNEL_MODULE.toString()),
    PopupChoice("微信原生群发助手", SelectedMessagesRuntimeCoordinator.CHANNEL_OFFICIAL.toString())
)

internal fun selectedMessagesChannelLabel(channel: Int): String {
    return if (channel == SelectedMessagesRuntimeCoordinator.CHANNEL_OFFICIAL) {
        "微信原生群发助手"
    } else {
        "模块通道"
    }
}

@Composable
internal fun MessageForwardMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MessageForwardSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageForwardSettings.KEY_ENABLE,
                        "转发菜单",
                        "在消息长按菜单中显示转发[H]",
                        MessageForwardSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        MessageForwardSettings.KEY_MULTI_MOMENTS_ENABLE,
                        "多选转发到朋友圈",
                        "在多选消息菜单中显示转发到朋友圈[H]",
                        MessageForwardSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "收藏") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageForwardSettings.KEY_FAVORITE_FORWARD_ENABLE,
                        "转发收藏",
                        "长按收藏后显示转发[H]",
                        MessageForwardSettings.DEFAULT_ENABLE
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MessageForwardSettings.KEY_SNS_FORWARD_ENABLE,
                        "朋友圈转发",
                        "在发现页或好友个人主页长按朋友圈后显示转发[H]",
                        MessageForwardSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun CallMediaLimitMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, CallMediaLimitSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "音视频通话") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        CallMediaLimitSettings.KEY_ENABLE,
                        "移除通话媒体限制",
                        "通话时允许播放语音和视频，并打开聊天拍摄",
                        CallMediaLimitSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun CallRingtoneBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, CallMediaLimitSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "通话铃声") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        CallMediaLimitSettings.KEY_BLOCK_INCOMING_RINGTONE,
                        "屏蔽通话呼入铃声",
                        "收到微信语音或视频通话时不播放来电铃声",
                        CallMediaLimitSettings.DEFAULT_BLOCK_INCOMING_RINGTONE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        CallMediaLimitSettings.KEY_BLOCK_OUTGOING_RINGTONE,
                        "屏蔽通话呼出铃声",
                        "拨打微信语音或视频通话时不播放等待铃声",
                        CallMediaLimitSettings.DEFAULT_BLOCK_OUTGOING_RINGTONE
                    )
                }
            }
        }
    }
}

@Composable
internal fun StatusTextLimitMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, StatusTextLimitSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "个人状态") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        StatusTextLimitSettings.KEY_ENABLE,
                        "解除状态词长度限制",
                        "开启后个人状态词可超过微信默认 10 字限制",
                        StatusTextLimitSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun EditMessageMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, EditMessageSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天记录") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        EditMessageSettings.KEY_ENABLE,
                        "修改聊天记录",
                        "长按文字、引用或转账消息后可修改本地记录",
                        EditMessageSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuickContactEditMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickContactEditSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "会话列表") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuickContactEditSettings.KEY_ENABLE,
                        "快捷设置备注和标签",
                        "长按好友、群聊会话或朋友圈头像时显示设置入口，聊天分组内同样生效",
                        QuickContactEditSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuickGroupChatLabelMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickGroupChatLabelSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "会话列表") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuickGroupChatLabelSettings.KEY_ENABLE,
                        "快捷设置群聊标签",
                        "长按群聊会话时显示群聊标签入口，聊天分组内同样生效",
                        QuickGroupChatLabelSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuickMomentsMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickMomentsSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "会话列表") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuickMomentsSettings.KEY_ENABLE,
                        "快捷查看朋友圈",
                        "长按本人、好友或企业微信联系人会话时显示朋友圈入口，聊天分组内同样生效",
                        QuickMomentsSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuickTerminateMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, QuickTerminateSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "加号菜单") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        QuickTerminateSettings.KEY_ENABLE,
                        "快捷终止",
                        "在微信右上角加号菜单中添加快捷终止，重启微信后生效",
                        QuickTerminateSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun TypingReportBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, TypingReportBlockSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "输入状态") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        TypingReportBlockSettings.KEY_ENABLE,
                        "拦截正在输入上报",
                        "输入文字时不向对方显示正在输入状态",
                        TypingReportBlockSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun MessageAffixMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { MessageAffixSettings.preferences(context) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(MessageAffixSettings.KEY_ENABLE, MessageAffixSettings.DEFAULT_ENABLE))
    }
    var textFormat by remember {
        mutableStateOf(MessageAffixSettings.textFormat(sp))
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                MessageAffixSettings.KEY_TIME_FORMAT,
                MessageAffixSettings.DEFAULT_TIME_FORMAT
            ).orEmpty()
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
                primaryText = "保存",
                onPrimaryClick = {
                    when {
                        enabled && MessageAffixSettings.originalTextVariableCount(textFormat) != 1 -> {
                            Toast.makeText(
                                context,
                                "文本格式必须且只能包含一个原消息变量",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        enabled && !MessageAffixSettings.isValidTimeFormat(timeFormat) -> {
                            Toast.makeText(context, "时间格式无效", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            textFormat = MessageAffixSettings.normalizeTextFormat(textFormat)
                            timeFormat = MessageAffixSettings.normalizeTimeFormat(timeFormat)
                            sp.edit()
                                .putBoolean(MessageAffixSettings.KEY_ENABLE, enabled)
                                .putString(MessageAffixSettings.KEY_TEXT_FORMAT, textFormat)
                                .putString(MessageAffixSettings.KEY_TIME_FORMAT, timeFormat)
                                .apply()
                            Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                secondaryText = "重置",
                onSecondaryClick = {
                    textFormat = MessageAffixSettings.DEFAULT_TEXT_FORMAT
                    timeFormat = MessageAffixSettings.DEFAULT_TIME_FORMAT
                },
                middleText = "取消",
                onMiddleClick = onBack
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
            item { SmallTitle(text = "聊天消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "发送文本格式",
                        summary = "将聊天发送的文字按自定义格式处理"
                    ) {
                        enabled = it
                    }
                    if (enabled) {
                        InsetDivider()
                        VariableInputRow(
                            title = "文本格式",
                            summary = "点击下方中文变量插入到光标位置",
                            value = textFormat,
                            variables = messageAffixTemplateVariables,
                            minLines = 2
                        ) {
                            textFormat = it
                        }
                        InsetDivider()
                        InputRow("时间格式", "例如 HH:mm:ss 或 yyyy-MM-dd HH:mm:ss", timeFormat) {
                            timeFormat = it
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InputHintMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { InputHintSettings.preferences(context) }
    var enabled by remember {
        mutableStateOf(sp.getBoolean(InputHintSettings.KEY_ENABLE, InputHintSettings.DEFAULT_ENABLE))
    }
    var statisticsEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                InputHintSettings.KEY_STATISTICS_ENABLE,
                InputHintSettings.DEFAULT_STATISTICS_ENABLE
            )
        )
    }
    var template by remember { mutableStateOf(InputHintSettings.template(sp)) }
    var stats by remember { mutableStateOf(InputHintStats()) }
    DisposableEffect(statisticsEnabled) {
        val subscription = if (statisticsEnabled) {
            stats = OutgoingMessageStatsRepository.current()
            OutgoingMessageStatsRepository.subscribe {
                stats = OutgoingMessageStatsRepository.current()
            }
        } else {
            stats = InputHintStats()
            null
        }
        onDispose { subscription?.unsubscribe() }
    }
    val preview = InputHintSettings.renderTemplateSpanned(template, stats, InputHintSettings.variableColors(sp))
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存",
                onPrimaryClick = {
                    template = InputHintSettings.normalizeTemplate(template)
                    sp.edit()
                        .putBoolean(InputHintSettings.KEY_ENABLE, enabled)
                        .putBoolean(InputHintSettings.KEY_STATISTICS_ENABLE, statisticsEnabled)
                        .putString(InputHintSettings.KEY_TEMPLATE, template)
                        .apply()
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                },
                secondaryText = "重置",
                onSecondaryClick = {
                    enabled = InputHintSettings.DEFAULT_ENABLE
                    statisticsEnabled = InputHintSettings.DEFAULT_STATISTICS_ENABLE
                    template = InputHintSettings.DEFAULT_TEMPLATE
                },
                middleText = "取消",
                onMiddleClick = onBack
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
            item { SmallTitle(text = "聊天输入框") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "输入框提示",
                        summary = "自定义聊天输入框没有文字时显示的内容"
                    ) {
                        enabled = it
                    }
                    if (enabled) {
                        InsetDivider()
                        SwitchRow(
                            checked = statisticsEnabled,
                            title = "统计今日发送数量",
                            summary = "统计今天自己实际发送的消息"
                        ) {
                            statisticsEnabled = it
                        }
                        InsetDivider()
                        VariableInputRow(
                            title = "提示文本",
                            summary = if (statisticsEnabled) {
                                "点击下方中文变量插入到光标位置"
                            } else {
                                "输入自定义提示内容"
                            },
                            value = template,
                            variables = if (statisticsEnabled) inputHintTemplateVariables else emptyList(),
                            minLines = 2
                        ) {
                            template = it
                        }
                        InsetDivider()
                        inputHintTemplateVariables.forEachIndexed { index, variable ->
                            InputHintVariableColorRow(sp, variable)
                            if (index < inputHintTemplateVariables.lastIndex) InsetDivider()
                        }
                        InfoRow("当前提示", preview.toString())
                    }
                }
            }
        }
    }
}

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
                bottom = padding.calculateBottomPadding() + 84.dp
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
                bottom = padding.calculateBottomPadding() + 84.dp
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
                bottom = padding.calculateBottomPadding() + 84.dp
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
                bottom = padding.calculateBottomPadding() + 84.dp
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
                        bottom = padding.calculateBottomPadding() + 84.dp
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
                bottom = padding.calculateBottomPadding() + 84.dp
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
                bottom = padding.calculateBottomPadding() + 84.dp
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
                bottom = padding.calculateBottomPadding() + 84.dp
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
