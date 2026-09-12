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
                bottom = padding.calculateBottomPadding() + 84.dp
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

@Composable
internal fun KeywordReplacementRulesPage(
    context: Context,
    initialRules: List<KeywordReplacementRule>,
    onBack: () -> Unit,
    onSave: (List<KeywordReplacementRule>) -> Unit
) {
    var rules by remember(initialRules) { mutableStateOf(initialRules) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    fun updateRule(index: Int, update: (KeywordReplacementRule) -> KeywordReplacementRule) {
        if (index !in rules.indices) return
        rules = rules.toMutableList().also { next -> next[index] = update(next[index]) }
    }

    fun save() {
        val normalized = rules.map { it.copy(keyword = it.keyword.trim()) }
        if (normalized.any { it.keyword.isEmpty() }) {
            Toast.makeText(context, "原关键词不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        val duplicate = normalized.groupingBy { it.keyword.lowercase(Locale.ROOT) }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        if (duplicate != null) {
            Toast.makeText(context, "存在重复的原关键词", Toast.LENGTH_SHORT).show()
            return
        }
        onSave(normalized)
    }

    PageScaffold(
        title = "替换规则",
        largeTitle = "替换规则",
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("保存", ::save, "返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "关键词替换") }
            if (rules.isEmpty()) {
                item {
                    SettingsCard {
                        InfoRow("替换规则", "暂无规则")
                    }
                }
            } else {
                rules.forEachIndexed { index, rule ->
                    item(key = "keyword-replacement-$index") {
                        SettingsCard {
                            InputRow(
                                "原关键词",
                                "匹配时不区分大小写",
                                rule.keyword
                            ) { value ->
                                updateRule(index) { it.copy(keyword = value) }
                            }
                            InsetDivider()
                            InputRow(
                                "替换内容",
                                "留空表示删除原关键词",
                                rule.replacement
                            ) { value ->
                                updateRule(index) { it.copy(replacement = value) }
                            }
                            InsetDivider()
                            ActionRow("删除本条", "移除第 ${index + 1} 条替换规则") {
                                rules = rules.toMutableList().also { it.removeAt(index) }
                            }
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("新增规则", "添加一条关键词替换规则") {
                        rules = rules + KeywordReplacementRule("", "")
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsAutoRefreshMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsAutoRefreshSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(sp.getBoolean(MomentsAutoRefreshSettings.KEY_ENABLE, false)) }
    var interval by remember {
        mutableStateOf(sp.getInt(MomentsAutoRefreshSettings.KEY_INTERVAL_SECONDS, MomentsAutoRefreshSettings.DEFAULT_INTERVAL_SECONDS).toString())
    }
    var timeWindow by remember {
        mutableStateOf(sp.getBoolean(MomentsAutoRefreshSettings.KEY_TIME_WINDOW_ENABLE, MomentsAutoRefreshSettings.DEFAULT_TIME_WINDOW_ENABLE))
    }
    var startTime by remember {
        mutableStateOf(sp.getString(MomentsAutoRefreshSettings.KEY_START_TIME, MomentsAutoRefreshSettings.DEFAULT_START_TIME).orEmpty())
    }
    var endTime by remember {
        mutableStateOf(sp.getString(MomentsAutoRefreshSettings.KEY_END_TIME, MomentsAutoRefreshSettings.DEFAULT_END_TIME).orEmpty())
    }
    var lastTime by remember { mutableStateOf(sp.getLong(MomentsAutoRefreshSettings.KEY_LAST_TIME, 0L)) }
    var lastResult by remember { mutableStateOf(sp.getString(MomentsAutoRefreshSettings.KEY_LAST_RESULT, "").orEmpty()) }
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
            item { SmallTitle(text = "后台刷新") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈自动刷新", "按设定间隔获取新的朋友圈内容") {
                        enabled = it
                        sp.edit().putBoolean(MomentsAutoRefreshSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        NumberInputRow("刷新间隔", "单位秒，最少 0 秒", interval) {
                            interval = it
                            it.toIntOrNull()?.let { value ->
                                sp.edit().putInt(MomentsAutoRefreshSettings.KEY_INTERVAL_SECONDS, value.coerceAtLeast(0)).apply()
                            }
                        }
                        InsetDivider()
                        SwitchRow(timeWindow, "限制刷新时段", "支持跨零点时段") {
                            timeWindow = it
                            sp.edit().putBoolean(MomentsAutoRefreshSettings.KEY_TIME_WINDOW_ENABLE, it).apply()
                        }
                        if (timeWindow) {
                            InsetDivider()
                            TimeOfDayPickerRow("开始时间", startTime) {
                                startTime = it
                                sp.edit().putString(MomentsAutoRefreshSettings.KEY_START_TIME, it).apply()
                            }
                            InsetDivider()
                            TimeOfDayPickerRow("结束时间", endTime) {
                                endTime = it
                                sp.edit().putString(MomentsAutoRefreshSettings.KEY_END_TIME, it).apply()
                            }
                        }
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "状态") }
                item {
                    SettingsCard {
                        ActionRow(
                            "刷新状态",
                            if (lastTime <= 0L) "尚未执行" else SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(lastTime)) + " · " + lastResult
                        ) {
                            lastTime = sp.getLong(MomentsAutoRefreshSettings.KEY_LAST_TIME, 0L)
                            lastResult = sp.getString(MomentsAutoRefreshSettings.KEY_LAST_RESULT, "").orEmpty()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsContactFilterMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsContactFilterSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsContactFilterRoute>(MomentsContactFilterRoute.Main) }
    var targets by remember {
        mutableStateOf(
            parseIds(
                sp.getString(
                    MomentsContactFilterSettings.KEY_TARGETS,
                    MomentsContactFilterSettings.DEFAULT_TARGETS
                ).orEmpty()
            )
        )
    }

    SettingsRouteTransition(
        targetState = route,
        label = "MomentsContactFilterRoute",
        depthOf = { if (it is MomentsContactFilterRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsContactFilterRoute.Main -> MomentsContactFilterMainPage(
                provider = provider,
                sp = sp,
                targets = targets,
                onBack = onBack,
                onPickTargets = {
                    route = MomentsContactFilterRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "选择朋友圈过滤对象",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(targets),
                            onValue = {},
                            enableLabels = true,
                            enableScopeSelection = true
                        )
                    )
                }
            )
            is MomentsContactFilterRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsContactFilterRoute.Main },
                onConfirm = { picked ->
                    targets = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    sp.edit()
                        .putString(MomentsContactFilterSettings.KEY_TARGETS, formatIds(targets))
                        .apply()
                    route = MomentsContactFilterRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun MomentsContactFilterMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    targets: Set<String>,
    onBack: () -> Unit,
    onPickTargets: () -> Unit
) {
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsContactFilterSettings.KEY_ENABLE,
                MomentsContactFilterSettings.DEFAULT_ENABLE
            )
        )
    }
    var mode by remember {
        mutableStateOf(
            sp.getInt(
                MomentsContactFilterSettings.KEY_MODE,
                MomentsContactFilterSettings.DEFAULT_MODE
            )
        )
    }
    val targetSummary = when {
        targets.isEmpty() -> "未选择好友，暂不筛选"
        mode == MomentsContactFilterSettings.MODE_INCLUDE_ONLY -> "只显示所选 ${targets.size} 位好友"
        else -> "隐藏所选 ${targets.size} 位好友"
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
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "朋友圈过滤",
                        summary = "按好友范围过滤朋友圈内容"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(MomentsContactFilterSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        PopupOptionRow(
                            title = "过滤模式",
                            summary = if (mode == MomentsContactFilterSettings.MODE_INCLUDE_ONLY) {
                                "只看所选好友的朋友圈"
                            } else {
                                "过滤所选好友的朋友圈"
                            },
                            options = optionItems(
                                "过滤所选好友" to MomentsContactFilterSettings.MODE_EXCLUDE,
                                "只看所选好友" to MomentsContactFilterSettings.MODE_INCLUDE_ONLY
                            ),
                            currentValue = mode,
                            onValueChanged = {
                                mode = it
                                sp.edit().putInt(MomentsContactFilterSettings.KEY_MODE, it).apply()
                            }
                        )
                        InsetDivider()
                        ActionRow(
                            title = "过滤对象",
                            summary = targetSummary
                        ) {
                            onPickTargets()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsKeywordBlockMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsKeywordBlockSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsKeywordBlockSettings.KEY_ENABLE,
                MomentsKeywordBlockSettings.DEFAULT_ENABLE
            )
        )
    }
    var keywords by remember {
        mutableStateOf(
            sp.getString(
                MomentsKeywordBlockSettings.KEY_KEYWORDS,
                MomentsKeywordBlockSettings.DEFAULT_KEYWORDS
            ).orEmpty()
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
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "朋友圈关键词屏蔽",
                        summary = "隐藏正文命中任意关键词的朋友圈"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(MomentsKeywordBlockSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        InputRow(
                            title = "屏蔽关键词",
                            summary = "多个关键词用逗号或换行分隔",
                            value = keywords,
                            minLines = 3
                        ) {
                            keywords = it
                            sp.edit().putString(MomentsKeywordBlockSettings.KEY_KEYWORDS, it).apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsBottomDetailMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsBottomDetailSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsBottomDetailSettings.KEY_ENABLE,
                MomentsBottomDetailSettings.DEFAULT_ENABLE
            )
        )
    }
    var textFormat by remember {
        mutableStateOf(
            sp.getString(
                MomentsBottomDetailSettings.KEY_TEXT_FORMAT,
                MomentsBottomDetailSettings.DEFAULT_TEXT_FORMAT
            ).orEmpty()
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                MomentsBottomDetailSettings.KEY_TIME_FORMAT,
                MomentsBottomDetailSettings.DEFAULT_TIME_FORMAT
            ).orEmpty()
        )
    }
    var hideGroupIcon by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsBottomDetailSettings.KEY_HIDE_GROUP_ICON,
                MomentsBottomDetailSettings.DEFAULT_HIDE_GROUP_ICON
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
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = {
                    val timeFormatValid = MomentsBottomDetailSettings.isValidTimeFormat(timeFormat)
                    if (enabled && !timeFormatValid) {
                        Toast.makeText(context, "时间格式无效", Toast.LENGTH_SHORT).show()
                    } else {
                        val normalizedText = MomentsBottomDetailSettings.normalizeTextFormat(textFormat)
                        val normalizedTime = if (timeFormatValid) {
                            MomentsBottomDetailSettings.normalizeTimeFormat(timeFormat)
                        } else {
                            MomentsBottomDetailSettings.normalizeTimeFormat(
                                sp.getString(
                                    MomentsBottomDetailSettings.KEY_TIME_FORMAT,
                                    MomentsBottomDetailSettings.DEFAULT_TIME_FORMAT
                                )
                            )
                        }
                        textFormat = normalizedText
                        timeFormat = normalizedTime
                        sp.edit()
                            .putBoolean(MomentsBottomDetailSettings.KEY_ENABLE, enabled)
                            .putString(MomentsBottomDetailSettings.KEY_TEXT_FORMAT, normalizedText)
                            .putString(MomentsBottomDetailSettings.KEY_TIME_FORMAT, normalizedTime)
                            .putBoolean(MomentsBottomDetailSettings.KEY_HIDE_GROUP_ICON, hideGroupIcon)
                            .apply()
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
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
            item { SmallTitle(text = "朋友圈底部详情") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "朋友圈底部详情",
                        summary = "在朋友圈底部显示自定义时间和详情"
                    ) {
                        enabled = it
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "格式") }
                item {
                    SettingsCard {
                        VariableInputRow(
                            title = "文本格式",
                            summary = "留空使用默认格式",
                            value = textFormat,
                            variables = momentsBottomDetailTemplateVariables,
                            onValueChange = { textFormat = it }
                        )
                        InsetDivider()
                        InputRow(
                            "时间格式",
                            "使用日期格式，例如 yyyy-MM-dd HH:mm:ss",
                            timeFormat
                        ) { timeFormat = it }
                        InsetDivider()
                        SwitchRow(
                            checked = hideGroupIcon,
                            title = "隐藏可见范围",
                            summary = "隐藏朋友圈底部的可见范围图标"
                        ) {
                            hideGroupIcon = it
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MomentsPostNotificationMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsPostNotificationSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<MomentsPostNotificationRoute>(MomentsPostNotificationRoute.Main) }
    var targets by remember {
        mutableStateOf(parseIds(sp.getString(MomentsPostNotificationSettings.KEY_TARGETS, "").orEmpty()))
    }
    SettingsRouteTransition(
        targetState = route,
        label = "MomentsPostNotificationRoute",
        depthOf = { if (it is MomentsPostNotificationRoute.Main) 0 else 1 }
    ) { current ->
        when (current) {
            MomentsPostNotificationRoute.Main -> MomentsPostNotificationMainPage(
                provider = provider,
                sp = sp,
                targetCount = targets.size,
                onBack = onBack,
                onPickTargets = {
                    route = MomentsPostNotificationRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "选择朋友圈提醒好友",
                            mode = ContactPickerMode.FRIENDS,
                            multiSelect = true,
                            existingValue = formatIds(targets),
                            onValue = {},
                            enableLabels = true
                        )
                    )
                }
            )
            is MomentsPostNotificationRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = current.request,
                onBack = { route = MomentsPostNotificationRoute.Main },
                onConfirm = { picked ->
                    targets = picked.map { it.id }.filter { it.isNotBlank() }.toSet()
                    sp.edit().putString(MomentsPostNotificationSettings.KEY_TARGETS, formatIds(targets)).apply()
                    route = MomentsPostNotificationRoute.Main
                }
            )
        }
    }
}

@Composable
internal fun MomentsPostNotificationMainPage(
    provider: FeatureSettingsProvider,
    sp: SharedPreferences,
    targetCount: Int,
    onBack: () -> Unit,
    onPickTargets: () -> Unit
) {
    var enabled by remember { mutableStateOf(sp.getBoolean(MomentsPostNotificationSettings.KEY_ENABLE, false)) }
    var systemNotification by remember {
        mutableStateOf(sp.getBoolean(MomentsPostNotificationSettings.KEY_SYSTEM_NOTIFICATION, MomentsPostNotificationSettings.DEFAULT_SYSTEM_NOTIFICATION))
    }
    var toast by remember {
        mutableStateOf(sp.getBoolean(MomentsPostNotificationSettings.KEY_TOAST, MomentsPostNotificationSettings.DEFAULT_TOAST))
    }
    var titleTemplate by remember {
        mutableStateOf(sp.getString(MomentsPostNotificationSettings.KEY_TITLE_TEMPLATE, MomentsPostNotificationSettings.DEFAULT_TITLE_TEMPLATE).orEmpty())
    }
    var bodyTemplate by remember {
        mutableStateOf(sp.getString(MomentsPostNotificationSettings.KEY_BODY_TEMPLATE, MomentsPostNotificationSettings.DEFAULT_BODY_TEMPLATE).orEmpty())
    }
    var toastTemplate by remember {
        mutableStateOf(sp.getString(MomentsPostNotificationSettings.KEY_TOAST_TEMPLATE, MomentsPostNotificationSettings.DEFAULT_TOAST_TEMPLATE).orEmpty())
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
            item { SmallTitle(text = "发布提醒") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "朋友圈发布通知", "指定好友发布新朋友圈时提醒") {
                        enabled = it
                        sp.edit()
                            .putBoolean(MomentsPostNotificationSettings.KEY_ENABLE, it)
                            .putLong(
                                MomentsPostNotificationSettings.KEY_ENABLED_AT_SECONDS,
                                if (it) System.currentTimeMillis() / 1000L else 0L
                            )
                            .apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        ActionRow("提醒好友", if (targetCount == 0) "未选择好友" else "已选择 $targetCount 位好友", onPickTargets)
                        InsetDivider()
                        SwitchRow(systemNotification, "系统通知", "显示在系统通知栏") {
                            systemNotification = it
                            sp.edit().putBoolean(MomentsPostNotificationSettings.KEY_SYSTEM_NOTIFICATION, it).apply()
                        }
                        InsetDivider()
                        SwitchRow(toast, "Toast提醒", "在微信界面短暂显示提醒") {
                            toast = it
                            sp.edit().putBoolean(MomentsPostNotificationSettings.KEY_TOAST, it).apply()
                        }
                    }
                }
            }
            if (enabled && (systemNotification || toast)) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提醒模板") }
                item {
                    SettingsCard {
                        if (systemNotification) {
                            VariableInputRow("通知标题", "留空使用默认标题", titleTemplate, momentsPostNotificationTemplateVariables) {
                                titleTemplate = it
                                sp.edit().putString(MomentsPostNotificationSettings.KEY_TITLE_TEMPLATE, it).apply()
                            }
                            InsetDivider()
                            VariableInputRow("通知内容", "留空使用发布者、类型和完整正文", bodyTemplate, momentsPostNotificationTemplateVariables, minLines = 2) {
                                bodyTemplate = it
                                sp.edit().putString(MomentsPostNotificationSettings.KEY_BODY_TEMPLATE, it).apply()
                            }
                        }
                        if (toast) {
                            if (systemNotification) InsetDivider()
                            VariableInputRow("Toast内容", "留空使用默认提醒", toastTemplate, momentsPostNotificationTemplateVariables, minLines = 2) {
                                toastTemplate = it
                                sp.edit().putString(MomentsPostNotificationSettings.KEY_TOAST_TEMPLATE, it).apply()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OriginalMomentsUploadMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, OriginalMomentsUploadSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        OriginalMomentsUploadSettings.KEY_ENABLE,
                        "原图上传",
                        "发布朋友圈图片和视频时尽量跳过微信压缩",
                        OriginalMomentsUploadSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun MomentsUploadTailMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MomentsUploadTailSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                MomentsUploadTailSettings.KEY_ENABLE,
                MomentsUploadTailSettings.DEFAULT_ENABLE
            )
        )
    }
    var sdkId by remember {
        mutableStateOf(
            sp.getString(
                MomentsUploadTailSettings.KEY_SDK_ID,
                MomentsUploadTailSettings.DEFAULT_SDK_ID
            ).orEmpty()
        )
    }
    var sdkAppName by remember {
        mutableStateOf(
            sp.getString(
                MomentsUploadTailSettings.KEY_SDK_APP_NAME,
                MomentsUploadTailSettings.DEFAULT_SDK_APP_NAME
            ).orEmpty()
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        enabled,
                        "朋友圈上传尾巴",
                        "发布时附带指定的 SDK 来源"
                    ) {
                        enabled = it
                        sp.edit().putBoolean(MomentsUploadTailSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        InputRow("SDK ID", "输入第三方 SDK 标识", sdkId) {
                            sdkId = it
                            sp.edit().putString(MomentsUploadTailSettings.KEY_SDK_ID, it).apply()
                        }
                        InsetDivider()
                        InputRow("SDK 名称", "输入显示的 SDK 名称", sdkAppName) {
                            sdkAppName = it
                            sp.edit()
                                .putString(MomentsUploadTailSettings.KEY_SDK_APP_NAME, it)
                                .apply()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SnsAntiRecallMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, SnsAntiRecallSettings.PREFS_NAME) }
    var momentMarkText by remember {
        mutableStateOf(
            sp.getString(
                SnsAntiRecallSettings.KEY_CUSTOM_MARK_TEXT,
                SnsAntiRecallSettings.DEFAULT_CUSTOM_MARK_TEXT
            ).orEmpty()
        )
    }
    var commentMarkText by remember {
        mutableStateOf(
            sp.getString(
                SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_TEXT,
                SnsAntiRecallSettings.DEFAULT_COMMENT_CUSTOM_MARK_TEXT
            ).orEmpty()
        )
    }
    var momentMarkEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                SnsAntiRecallSettings.KEY_CUSTOM_MARK_ENABLE,
                SnsAntiRecallSettings.DEFAULT_CUSTOM_MARK_ENABLE
            )
        )
    }
    var commentMarkEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_ENABLE,
                SnsAntiRecallSettings.DEFAULT_COMMENT_CUSTOM_MARK_ENABLE
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
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SnsAntiRecallSettings.KEY_ENABLE,
                        "朋友圈防撤回",
                        "已缓存的朋友圈在对方删除或限制可见范围后继续显示",
                        SnsAntiRecallSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = momentMarkEnabled,
                        title = "自定义朋友圈撤回提示",
                        summary = "开启后可自定义朋友圈正文被删除时的提示文案"
                    ) { checked ->
                        momentMarkEnabled = checked
                        sp.edit().putBoolean(SnsAntiRecallSettings.KEY_CUSTOM_MARK_ENABLE, checked).apply()
                    }
                    if (momentMarkEnabled) {
                        InsetDivider()
                        InputRow("朋友圈提示文案", "默认 [已删除]", momentMarkText) { momentMarkText = it }
                    }
                    InsetDivider()
                    SwitchRow(
                        sp,
                        SnsAntiRecallSettings.KEY_COMMENT_ENABLE,
                        "朋友圈评论防撤回",
                        "已缓存的朋友圈评论被删除后继续显示，并标记已删除",
                        SnsAntiRecallSettings.DEFAULT_COMMENT_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = commentMarkEnabled,
                        title = "自定义评论撤回提示",
                        summary = "开启后可自定义朋友圈评论被删除时的提示文案"
                    ) { checked ->
                        commentMarkEnabled = checked
                        sp.edit().putBoolean(SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_ENABLE, checked).apply()
                    }
                    if (commentMarkEnabled) {
                        InsetDivider()
                        InputRow("评论提示文案", "默认 [已删除]", commentMarkText) { commentMarkText = it }
                    }
                    InsetDivider()
                    SwitchRow(
                        sp,
                        SnsAntiRecallSettings.KEY_FORCE_LEGACY_PROFILE,
                        "强制旧版个人主页朋友圈",
                        "Flutter 个人主页看不到已删除朋友圈时，改用微信旧版 SnsUserUI",
                        SnsAntiRecallSettings.DEFAULT_FORCE_LEGACY_PROFILE
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sp.edit()
                .putString(
                    SnsAntiRecallSettings.KEY_CUSTOM_MARK_TEXT,
                    momentMarkText.ifBlank { SnsAntiRecallSettings.DEFAULT_CUSTOM_MARK_TEXT }
                )
                .putString(
                    SnsAntiRecallSettings.KEY_COMMENT_CUSTOM_MARK_TEXT,
                    commentMarkText.ifBlank { SnsAntiRecallSettings.DEFAULT_COMMENT_CUSTOM_MARK_TEXT }
                )
                .apply()
        }
    }
}

@Composable
internal fun MomentsFakeInteractionMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember {
        HchatStorage.preferences(context, MomentsFakeInteractionSettings.PREFS_NAME)
    }
    val isLike = provider.featureId() == MomentsFakeLikeSettingsProvider.ID
    val isForward = provider.featureId() == MomentsFakeForwardSettingsProvider.ID
    val enableKey = when {
        isLike -> MomentsFakeInteractionSettings.KEY_FAKE_LIKE_ENABLE
        isForward -> MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_ENABLE
        else -> MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_ENABLE
    }
    val defaultEnabled = when {
        isLike -> MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_ENABLE
        isForward -> MomentsFakeInteractionSettings.DEFAULT_FAKE_FORWARD_ENABLE
        else -> MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_ENABLE
    }
    val pendingRestoreKey = if (isLike) {
        MomentsFakeInteractionSettings.KEY_PENDING_RESTORE_LIKES
    } else {
        MomentsFakeInteractionSettings.KEY_PENDING_RESTORE_COMMENTS
    }
    val useNonFriendsKey = if (isLike) {
        MomentsFakeInteractionSettings.KEY_FAKE_LIKE_USE_NON_FRIENDS
    } else {
        MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_USE_NON_FRIENDS
    }
    val defaultUseNonFriends = if (isLike) {
        MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_USE_NON_FRIENDS
    } else {
        MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_USE_NON_FRIENDS
    }
    var enabled by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(enableKey, defaultEnabled)
        )
    }
    var useNonFriends by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(useNonFriendsKey, defaultUseNonFriends)
        )
    }
    var randomCommentContent by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(
                MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_RANDOM_CONTENT,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_RANDOM_CONTENT
            )
        )
    }
    var commentContents by remember(provider.featureId()) {
        mutableStateOf(MomentsFakeInteractionSettings.commentContents(sp))
    }
    var randomOrder by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(
                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_RANDOM_ORDER,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_RANDOM_ORDER
            )
        )
    }
    var autoSelect by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(
                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_AUTO_SELECT
            )
        )
    }
    var autoSelectCount by remember(provider.featureId()) {
        mutableStateOf(
            sp.getInt(
                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT_COUNT,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_AUTO_SELECT_COUNT
            ).toString()
        )
    }
    var excludedIds by remember(provider.featureId()) {
        mutableStateOf(
            sp.getStringSet(
                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_EXCLUDED_IDS,
                emptySet()
            ).orEmpty().toSet()
        )
    }
    val menuTextKey = when {
        isLike -> MomentsFakeInteractionSettings.KEY_FAKE_LIKE_MENU_TEXT
        isForward -> MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_MENU_TEXT
        else -> MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_MENU_TEXT
    }
    val defaultMenuText = when {
        isLike -> MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_MENU_TEXT
        isForward -> MomentsFakeInteractionSettings.DEFAULT_FAKE_FORWARD_MENU_TEXT
        else -> MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_MENU_TEXT
    }
    val hideMenuKey = when {
        isLike -> MomentsFakeInteractionSettings.KEY_FAKE_LIKE_HIDE_MENU
        isForward -> MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_HIDE_MENU
        else -> MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_HIDE_MENU
    }
    val defaultHideMenu = when {
        isLike -> MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_HIDE_MENU
        isForward -> MomentsFakeInteractionSettings.DEFAULT_FAKE_FORWARD_HIDE_MENU
        else -> MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_HIDE_MENU
    }
    var hideMenu by remember(provider.featureId()) {
        mutableStateOf(sp.getBoolean(hideMenuKey, defaultHideMenu))
    }
    var menuText by remember(provider.featureId()) {
        mutableStateOf(sp.getString(menuTextKey, defaultMenuText).orEmpty().ifBlank { defaultMenuText })
    }
    var forwardDebugLog by remember(provider.featureId()) {
        mutableStateOf(
            sp.getBoolean(
                MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_DEBUG_LOG,
                MomentsFakeInteractionSettings.DEFAULT_FAKE_FORWARD_DEBUG_LOG
            )
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
            item { SmallTitle(text = provider.title()) }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = provider.title(),
                        summary = provider.subtitle()
                    ) {
                        enabled = it
                        sp.edit().putBoolean(enableKey, it).commit()
                        MomentsFakeInteractionRuntimeRegistry.reapplyAll()
                    }
                }
            }
            if (enabled && isLike) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "生成方式") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = useNonFriends,
                            title = "使用非好友",
                            summary = "仅在伪集赞选择器中额外加入群成员"
                        ) {
                            useNonFriends = it
                            sp.edit().putBoolean(useNonFriendsKey, it).commit()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = randomOrder,
                            title = "随机排序",
                            summary = "保存时随机排列点赞人的显示顺序"
                        ) {
                            randomOrder = it
                            sp.edit().putBoolean(
                                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_RANDOM_ORDER,
                                it
                            ).commit()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = autoSelect,
                            title = "自动勾选好友",
                            summary = "长按伪集赞后自动勾选指定人数"
                        ) {
                            autoSelect = it
                            sp.edit().putBoolean(
                                MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT,
                                it
                            ).commit()
                        }
                        if (autoSelect) {
                            InsetDivider()
                            NumberInputRow(
                                title = "自动勾选数量",
                                summary = "最少 1 人，不设上限",
                                value = autoSelectCount
                            ) { next ->
                                val count = next.toIntOrNull()?.coerceAtLeast(1)
                                autoSelectCount = count?.toString().orEmpty()
                                count?.let {
                                    sp.edit().putInt(
                                        MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT_COUNT,
                                        it
                                    ).apply()
                                }
                            }
                        }
                        InsetDivider()
                        ActionRow(
                            title = "设置排除名单",
                            summary = if (excludedIds.isEmpty()) "未设置" else "已排除 ${excludedIds.size} 人",
                            onClick = {
                                showMomentsFakeLikeExclusionPicker(
                                    context = context,
                                    includeNonFriends = useNonFriends,
                                    selectedIds = excludedIds
                                ) { selected ->
                                    excludedIds = selected
                                    sp.edit().putStringSet(
                                        MomentsFakeInteractionSettings.KEY_FAKE_LIKE_EXCLUDED_IDS,
                                        selected
                                    ).commit()
                                }
                            }
                        )
                    }
                }
            }
            if (enabled && !isLike && !isForward) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "评论人范围") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = useNonFriends,
                            title = "使用非好友",
                            summary = "仅在伪评论选择器中额外加入群成员"
                        ) {
                            useNonFriends = it
                            sp.edit().putBoolean(useNonFriendsKey, it).commit()
                        }
                        InsetDivider()
                        SwitchRow(
                            checked = randomCommentContent,
                            title = "随机评论",
                            summary = if (commentContents.isEmpty()) {
                                "评论池为空时仍使用手动输入"
                            } else {
                                "为每位选中的好友从 ${commentContents.size} 条评论中随机选择"
                            }
                        ) {
                            randomCommentContent = it
                            sp.edit().putBoolean(
                                MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_RANDOM_CONTENT,
                                it
                            ).commit()
                        }
                        InsetDivider()
                        ActionRow(
                            title = "添加新评论",
                            summary = if (commentContents.isEmpty()) "尚未添加" else "当前 ${commentContents.size} 条"
                        ) {
                            showFakeCommentContentEditor(context, "") { content ->
                                val next = commentContents + content
                                if (MomentsFakeInteractionSettings.saveCommentContents(sp, next)) {
                                    commentContents = next
                                }
                            }
                        }
                        commentContents.forEachIndexed { index, content ->
                            InsetDivider()
                            ActionRow(
                                title = "评论 ${index + 1}",
                                summary = content
                            ) {
                                showFakeCommentContentActions(
                                    context = context,
                                    content = content,
                                    onEdit = { edited ->
                                        val next = commentContents.toMutableList().also { it[index] = edited }
                                        if (MomentsFakeInteractionSettings.saveCommentContents(sp, next)) {
                                            commentContents = next
                                        }
                                    },
                                    onDelete = {
                                        val next = commentContents.toMutableList().also { it.removeAt(index) }
                                        if (MomentsFakeInteractionSettings.saveCommentContents(sp, next)) {
                                            commentContents = next
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (enabled) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "长按菜单") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = hideMenu,
                            title = "隐藏长按菜单",
                            summary = when {
                                isLike -> "隐藏伪集赞入口，已伪造的点赞仍然显示"
                                isForward -> "隐藏伪转发入口，已保存的本地朋友圈仍然显示"
                                else -> "隐藏伪评论入口，已伪造的评论仍然显示"
                            }
                        ) {
                            hideMenu = it
                            sp.edit().putBoolean(hideMenuKey, it).commit()
                        }
                        if (!hideMenu) {
                            InsetDivider()
                            InputRow(
                                title = when {
                                    isLike -> "伪集赞自定义文本"
                                    isForward -> "伪转发自定义文本"
                                    else -> "伪评论自定义文本"
                                },
                                summary = "完整替换朋友圈长按菜单文字",
                                value = menuText,
                                onValueChange = { next ->
                                    menuText = next
                                    sp.edit().putString(menuTextKey, next).apply()
                                }
                            )
                        }
                    }
                }
            }
            if (isForward) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "诊断") }
                item {
                    SettingsCard {
                        SwitchRow(
                            checked = forwardDebugLog,
                            title = "调试日志",
                            summary = "输出伪转发创建、分页、刷新和清理诊断到 LSPosed 日志"
                        ) {
                            if (sp.edit().putBoolean(
                                    MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_DEBUG_LOG,
                                    it
                                ).commit()
                            ) {
                                forwardDebugLog = it
                            }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow(
                            title = "清空伪转发",
                            summary = "删除全部已登记的本地伪转发，不影响真实朋友圈",
                            onClick = {
                                confirmClearMomentsFakeForwards(context) {
                                    val scheduled = MomentsFakeForwardRuntimeRegistry.clearAll { success ->
                                        Toast.makeText(
                                            context,
                                            if (success) "已清空伪转发" else "部分记录删除失败，请稍后重试",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    Toast.makeText(
                                        context,
                                        if (scheduled) "正在清理..." else "微信运行时尚未就绪",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            } else {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow(
                            title = if (isLike) "清除伪集赞" else "清除伪评论",
                        summary = if (isLike) {
                            "清空已保存的本地点赞，保留当前功能设置"
                        } else {
                            "清空已保存的本地评论，保留当前功能设置"
                        },
                        onClick = {
                            confirmClearMomentsFakeInteractions(context, isLike) {
                                scheduleMomentsFakeInteractionRestore(context, sp, isLike)
                            }
                        }
                    )
                    InsetDivider()
                    ActionRow(
                        title = "恢复默认",
                        summary = if (isLike) {
                            "关闭伪集赞并清空已保存的本地点赞"
                        } else {
                            "关闭伪评论并清空已保存的本地评论"
                        },
                        onClick = {
                            val editor = sp.edit()
                                .putBoolean(enableKey, defaultEnabled)
                                .putBoolean(pendingRestoreKey, true)
                            if (isLike) {
                                editor
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_USE_NON_FRIENDS)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_RANDOM_ORDER)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_AUTO_SELECT_COUNT)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_EXCLUDED_IDS)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_HIDE_MENU)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_MENU_TEXT)
                            } else {
                                editor
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_USE_NON_FRIENDS)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_HIDE_MENU)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_MENU_TEXT)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_RANDOM_CONTENT)
                                    .remove(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_CONTENTS)
                            }
                            val settingsRestored = editor.commit()
                            if (!settingsRestored) {
                                Toast.makeText(context, "恢复默认失败", Toast.LENGTH_SHORT).show()
                            } else {
                                enabled = defaultEnabled
                                hideMenu = defaultHideMenu
                                menuText = defaultMenuText
                                useNonFriends = defaultUseNonFriends
                                randomCommentContent = MomentsFakeInteractionSettings.DEFAULT_FAKE_COMMENT_RANDOM_CONTENT
                                commentContents = emptyList()
                                if (isLike) {
                                    randomOrder = MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_RANDOM_ORDER
                                    autoSelect = MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_AUTO_SELECT
                                    autoSelectCount = MomentsFakeInteractionSettings.DEFAULT_FAKE_LIKE_AUTO_SELECT_COUNT.toString()
                                    excludedIds = emptySet()
                                }
                                scheduleMomentsFakeInteractionRestore(context, sp, isLike, "已恢复默认")
                            }
                        }
                    )
                }
            }
            }
        }
    }
}

internal fun confirmClearMomentsFakeForwards(
    context: Context,
    onConfirmed: () -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    VoiceForwardMiuixDialog.showConfirm(
        activity = activity,
        title = "清空伪转发",
        message = "删除全部已登记的本地伪转发？真实朋友圈不会受影响。",
        onResult = { if (it) onConfirmed() },
        onDismiss = {}
    )
}

internal fun showMomentsFakeLikeExclusionPicker(
    context: Context,
    includeNonFriends: Boolean,
    selectedIds: Set<String>,
    onConfirm: (Set<String>) -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    val loading = VoiceForwardMiuixDialog.showLoading(
        activity = activity,
        title = "设置排除名单",
        message = if (includeNonFriends) "正在载入好友和非好友..." else "正在载入好友列表...",
        onDismiss = {}
    )
    Thread({
        val contacts = runCatching {
            MomentsFakeLikeCandidateRepository.loadForLikes(includeNonFriends)
        }.getOrDefault(emptyList())
        activity.runOnUiThread {
            loading.close()
            activity.window?.decorView?.postOnAnimation {
                if (activity.isFinishing || activity.isDestroyed) return@postOnAnimation
                if (contacts.isEmpty()) {
                    Toast.makeText(activity, "没有可选择的联系人", Toast.LENGTH_SHORT).show()
                    return@postOnAnimation
                }
                val visibleIds = contacts.mapTo(hashSetOf()) { it.id }
                val hiddenSelectedIds = selectedIds.filterNotTo(linkedSetOf()) {
                    visibleIds.contains(it)
                }
                VoiceForwardMiuixDialog.showContacts(
                    activity = activity,
                    contacts = contacts,
                    title = "设置排除名单",
                    confirmText = "保存",
                    showGroupFilter = false,
                    initialSelectedIds = selectedIds,
                    allowEmpty = true,
                    showClearSelectionAction = true,
                    onConfirm = { selected ->
                        onConfirm(hiddenSelectedIds + selected.mapTo(linkedSetOf()) { it.id })
                    },
                    onDismiss = {}
                )
            }
        }
    }, "Hchat-MomentsFakeLikeExclude").apply { isDaemon = true }.start()
}

internal fun confirmClearMomentsFakeInteractions(
    context: Context,
    isLike: Boolean,
    onConfirmed: () -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    VoiceForwardMiuixDialog.showConfirm(
        activity = activity,
        title = if (isLike) "清除伪集赞" else "清除伪评论",
        message = if (isLike) "清空全部朋友圈已保存的本地点赞？" else "清空全部朋友圈已保存的本地评论？",
        onResult = { if (it) onConfirmed() },
        onDismiss = {}
    )
}

internal fun showFakeCommentContentEditor(
    context: Context,
    initialValue: String,
    onSaved: (String) -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    VoiceForwardMiuixDialog.showTextInput(
        activity = activity,
        title = if (initialValue.isBlank()) "添加新评论" else "修改评论内容",
        summary = "保存后可用于随机评论",
        initialValue = initialValue,
        placeholder = "请输入评论内容",
        maxLength = 1000,
        singleLine = false,
        onConfirm = onSaved,
        onDismiss = {}
    )
}

internal fun showFakeCommentContentActions(
    context: Context,
    content: String,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit
) {
    val activity = context as? Activity
        ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
        ?: return
    VoiceForwardMiuixDialog.showChoices(
        activity = activity,
        title = "评论内容",
        summary = content,
        choices = listOf(
            "修改" to "编辑这条随机评论内容",
            "删除" to "从评论池移除这条内容"
        ),
        onSelected = { index ->
            if (index == 0) {
                showFakeCommentContentEditor(context, content, onEdit)
            } else {
                VoiceForwardMiuixDialog.showConfirm(
                    activity = activity,
                    title = "删除评论内容",
                    message = "从随机评论池删除这条内容？",
                    onResult = { if (it) onDelete() },
                    onDismiss = {}
                )
            }
        },
        onDismiss = {}
    )
}

internal fun scheduleMomentsFakeInteractionRestore(
    context: Context,
    sp: SharedPreferences,
    isLike: Boolean,
    successText: String = "已清除"
) {
    val pendingKey = if (isLike) {
        MomentsFakeInteractionSettings.KEY_PENDING_RESTORE_LIKES
    } else {
        MomentsFakeInteractionSettings.KEY_PENDING_RESTORE_COMMENTS
    }
    if (!sp.edit().putBoolean(pendingKey, true).commit()) {
        Toast.makeText(context, "保存清理状态失败", Toast.LENGTH_SHORT).show()
        return
    }
    val onRestored: (Boolean) -> Unit = { restored ->
        Toast.makeText(
            context,
            if (restored) successText else "缓存将在下次启动时继续清理",
            Toast.LENGTH_SHORT
        ).show()
    }
    val scheduled = if (isLike) {
        MomentsFakeInteractionRuntimeRegistry.restoreLikes(onRestored)
    } else {
        MomentsFakeInteractionRuntimeRegistry.restoreComments(onRestored)
    }
    Toast.makeText(
        context,
        if (scheduled) "正在清理..." else "缓存将在下次启动时清理",
        Toast.LENGTH_SHORT
    ).show()
}

@Composable
internal fun RemoveMomentsAdsMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RemoveMomentsAdsSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "朋友圈") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        RemoveMomentsAdsSettings.KEY_ENABLE,
                        "去除朋友圈广告",
                        "阻止朋友圈广告信息解析和展示",
                        RemoveMomentsAdsSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun FinderMediaDownloadMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, FinderMediaDownloadSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "视频号") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        FinderMediaDownloadSettings.KEY_ENABLE,
                        "视频号媒体下载",
                        "在视频号分享菜单增加复制链接和下载入口，媒体保存到 Hchat/Finder",
                        FinderMediaDownloadSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun HchatExtraToggleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    key: String,
    defaultValue: Boolean,
    summary: String,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, HchatExtraSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "功能开关") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        key,
                        provider.title(),
                        summary,
                        defaultValue
                    )
                }
            }
        }
    }
}

@Composable
internal fun FloatingChatToggleMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val store = remember { HchatStorage.preferences(context, "Hchat_global_config") }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val key = "${provider.featureId()}_enabled"
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
            item { SmallTitle(text = "功能开关") }
            item {
                SettingsCard {
                    SwitchRow(store, key, provider.title(), provider.subtitle(), false)
                }
            }
        }
    }
}

@Composable
internal fun MessageDetailsConfigPage(
    context: Context,
    sp: SharedPreferences,
    onBack: () -> Unit
) {
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(
                HchatExtraSettings.KEY_MESSAGE_DETAILS,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS
            )
        )
    }
    var lightBg by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_BG,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG
            ).orEmpty()
        )
    }
    var lightText by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_TEXT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT
            ).orEmpty()
        )
    }
    var darkBg by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_BG,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG
            ).orEmpty()
        )
    }
    var darkText by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_TEXT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT
            ).orEmpty()
        )
    }
    var format by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT
            ).orEmpty()
        )
    }
    var timeFormat by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_TIME_FORMAT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TIME_FORMAT
            ).orEmpty()
        )
    }
    var position by remember {
        mutableStateOf(
            sp.getString(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_POSITION,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_POSITION
            ).orEmpty().ifBlank { HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_POSITION }
        )
    }
    var avatarGap by remember {
        mutableStateOf(
            sp.getInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_AVATAR_GAP,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_AVATAR_GAP
            ).toString()
        )
    }
    var leftMargin by remember {
        mutableStateOf(
            sp.getInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_LEFT_MARGIN,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LEFT_MARGIN
            ).toString()
        )
    }
    var rightMargin by remember {
        mutableStateOf(
            sp.getInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_RIGHT_MARGIN,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_RIGHT_MARGIN
            ).toString()
        )
    }
    var textSize by remember {
        mutableStateOf(
            sp.getInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_TEXT_SIZE,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TEXT_SIZE
            ).toString()
        )
    }
    var clickShow by remember {
        mutableStateOf(
            sp.getBoolean(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_CLICK_SHOW,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_CLICK_SHOW
            )
        )
    }
    var formatContent by remember {
        mutableStateOf(
            sp.getBoolean(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT_CONTENT,
                HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT_CONTENT
            )
        )
    }

    fun resetValues() {
        enabled = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS
        lightBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG
        lightText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT
        darkBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG
        darkText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT
        format = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT
        timeFormat = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TIME_FORMAT
        position = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_POSITION
        avatarGap = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_AVATAR_GAP.toString()
        leftMargin = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LEFT_MARGIN.toString()
        rightMargin = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_RIGHT_MARGIN.toString()
        textSize = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TEXT_SIZE.toString()
        clickShow = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_CLICK_SHOW
        formatContent = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT_CONTENT
    }

    fun saveValues() {
        val savedLightBg = cleanMessageDetailsColor(lightBg, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG)
        val savedLightText = cleanMessageDetailsColor(lightText, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT)
        val savedDarkBg = cleanMessageDetailsColor(darkBg, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG)
        val savedDarkText = cleanMessageDetailsColor(darkText, HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT)
        val savedPosition = position.takeIf { it in messageDetailsPositionValues() }
            ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_POSITION
        val savedAvatarGap = (avatarGap.toIntOrNull() ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_AVATAR_GAP)
            .coerceIn(0, 64)
        sp.edit()
            .putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS, enabled)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_BG, savedLightBg)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_LIGHT_TEXT, savedLightText)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_BG, savedDarkBg)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_DARK_TEXT, savedDarkText)
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT, format.ifBlank { HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_FORMAT })
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_TIME_FORMAT, timeFormat.ifBlank { HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TIME_FORMAT })
            .putString(HchatExtraSettings.KEY_MESSAGE_DETAILS_POSITION, savedPosition)
            .putInt(HchatExtraSettings.KEY_MESSAGE_DETAILS_AVATAR_GAP, savedAvatarGap)
            .putInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_LEFT_MARGIN,
                leftMargin.toIntOrNull() ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LEFT_MARGIN
            )
            .putInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_RIGHT_MARGIN,
                rightMargin.toIntOrNull() ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_RIGHT_MARGIN
            )
            .putInt(
                HchatExtraSettings.KEY_MESSAGE_DETAILS_TEXT_SIZE,
                textSize.toIntOrNull() ?: HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_TEXT_SIZE
            )
            .putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS_CLICK_SHOW, clickShow)
            .putBoolean(HchatExtraSettings.KEY_MESSAGE_DETAILS_FORMAT_CONTENT, formatContent)
            .apply()
        lightBg = savedLightBg
        lightText = savedLightText
        darkBg = savedDarkBg
        darkText = savedDarkText
        position = savedPosition
        avatarGap = savedAvatarGap.toString()
        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "消息显示时间",
        largeTitle = "消息显示时间",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = { saveValues() },
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
                        checked = enabled,
                        title = "消息显示时间",
                        summary = "开启后按所选位置显示自定义消息时间",
                        onCheckedChange = { enabled = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = clickShow,
                        title = "点击显详情",
                        summary = "点击详情文字后打开消息内容详情",
                        onCheckedChange = { clickShow = it }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = formatContent,
                        title = "内容格式化",
                        summary = "打开详情时格式化 XML 内容",
                        onCheckedChange = { formatContent = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "颜色") }
            item {
                SettingsCard {
                    ColorPickerRow(
                        "浅色背景",
                        "浅色模式消息时间背景",
                        lightBg,
                        allowGradient = false,
                        onReset = { lightBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_BG }
                    ) { lightBg = it.take(9) }
                    InsetDivider()
                    ColorPickerRow(
                        "浅色文字",
                        "浅色模式消息时间文字",
                        lightText,
                        allowGradient = false,
                        onReset = { lightText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_LIGHT_TEXT }
                    ) { lightText = it.take(9) }
                    InsetDivider()
                    ColorPickerRow(
                        "深色背景",
                        "深色模式消息时间背景",
                        darkBg,
                        allowGradient = false,
                        onReset = { darkBg = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_BG }
                    ) { darkBg = it.take(9) }
                    InsetDivider()
                    ColorPickerRow(
                        "深色文字",
                        "深色模式消息时间文字",
                        darkText,
                        allowGradient = false,
                        onReset = { darkText = HchatExtraSettings.DEFAULT_MESSAGE_DETAILS_DARK_TEXT }
                    ) { darkText = it.take(9) }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本") }
            item {
                SettingsCard {
                    VariableInputRow(
                        title = "文本格式",
                        summary = "显示在消息旁的内容模板",
                        value = format,
                        variables = messageDetailsVariables(),
                        onValueChange = { format = it }
                    )
                    InsetDivider()
                    PopupChoiceRow(
                        title = "时间显示",
                        summary = messageDetailsTimeFormatChoices(timeFormat).first { it.value == timeFormat }.label,
                        options = messageDetailsTimeFormatChoices(timeFormat),
                        currentValue = timeFormat,
                        onValueChanged = { timeFormat = it }
                    )
                    InsetDivider()
                    InputRow("自定义时间格式", "yyyy 年、MM 月、dd 日；例如 yyyy-MM-dd HH:mm:ss", timeFormat, onValueChange = { timeFormat = it })
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "布局") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "显示位置",
                        summary = messageDetailsPositionLabel(position),
                        options = messageDetailsPositionChoices(),
                        currentValue = position,
                        onValueChanged = { position = it }
                    )
                    InsetDivider()
                    if (position == HchatExtraSettings.POSITION_MESSAGE_BOTTOM) {
                        NumberInputRow("左边距", "单位 dp，对方消息使用", leftMargin, onValueChange = { leftMargin = it })
                        InsetDivider()
                        NumberInputRow("右边距", "单位 dp，自己消息使用", rightMargin, onValueChange = { rightMargin = it })
                        InsetDivider()
                    } else {
                        NumberInputRow("与头像间距", "单位 dp，可设置 0-64", avatarGap, onValueChange = { avatarGap = it })
                        InsetDivider()
                    }
                    NumberInputRow("字体大小", "单位 sp", textSize, onValueChange = { textSize = it })
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("恢复默认", "重置本页全部消息显示时间设置") {
                        resetValues()
                        Toast.makeText(context, "已恢复默认，保存后生效", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

