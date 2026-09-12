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

@Composable
internal fun GroupChatLabelMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    var labels by remember { mutableStateOf(GroupChatLabelStore.load(context)) }
    var route by remember { mutableStateOf<GroupChatLabelRoute>(GroupChatLabelRoute.Main) }

    fun saveLabels(next: List<GroupChatLabel>) {
        labels = next
        GroupChatLabelStore.save(context, next)
    }

    SettingsRouteTransition(
        targetState = route,
        label = "GroupChatLabelRoute",
        depthOf = {
            when (it) {
                GroupChatLabelRoute.Main -> 0
                is GroupChatLabelRoute.Editor -> 1
                is GroupChatLabelRoute.GroupPicker -> 2
            }
        }
    ) { currentRoute ->
        when (currentRoute) {
            GroupChatLabelRoute.Main -> GroupChatLabelListPage(
                title = provider.title(),
                labels = labels,
                onBack = onBack,
                onAdd = { route = GroupChatLabelRoute.Editor(GroupChatLabelStore.newLabel(), false) },
                onOpen = { route = GroupChatLabelRoute.Editor(it, true) }
            )
            is GroupChatLabelRoute.Editor -> GroupChatLabelEditorPage(
                context = context,
                label = currentRoute.label,
                existing = currentRoute.existing,
                onBack = { route = GroupChatLabelRoute.Main },
                onPickGroups = { draft ->
                    route = GroupChatLabelRoute.GroupPicker(draft, currentRoute.existing)
                },
                onSave = { draft ->
                    if (labels.any { it.id != draft.id && it.name.equals(draft.name, ignoreCase = true) }) {
                        Toast.makeText(context, "标签名称已存在", Toast.LENGTH_SHORT).show()
                    } else {
                        val next = if (currentRoute.existing) {
                            labels.map { if (it.id == draft.id) draft else it }
                        } else {
                            labels + draft
                        }
                        saveLabels(next)
                        Toast.makeText(context, "群聊标签已保存", Toast.LENGTH_SHORT).show()
                        route = GroupChatLabelRoute.Main
                    }
                },
                onDelete = {
                    saveLabels(labels.filterNot { it.id == currentRoute.label.id })
                    Toast.makeText(context, "群聊标签已删除", Toast.LENGTH_SHORT).show()
                    route = GroupChatLabelRoute.Main
                }
            )
            is GroupChatLabelRoute.GroupPicker -> ContactPickerPage(
                context = context,
                request = ContactPickerRequest(
                    title = "选择标签群聊",
                    mode = ContactPickerMode.GROUPS,
                    multiSelect = true,
                    existingValue = formatIds(currentRoute.label.groupIds),
                    onValue = {},
                    enableGroupLabels = false
                ),
                onBack = {
                    route = GroupChatLabelRoute.Editor(currentRoute.label, currentRoute.existing)
                },
                onConfirm = { selected ->
                    route = GroupChatLabelRoute.Editor(
                        currentRoute.label.copy(groupIds = selected.map { it.id }.toSet()),
                        currentRoute.existing
                    )
                }
            )
        }
    }
}

@Composable
internal fun GroupChatLabelListPage(
    title: String,
    labels: List<GroupChatLabel>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (GroupChatLabel) -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "新增标签",
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
            item { SmallTitle(text = "分类标签") }
            item {
                SettingsCard {
                    if (labels.isEmpty()) {
                        EmptyText("暂无群聊标签")
                    } else {
                        labels.forEachIndexed { index, label ->
                            SelectRow(
                                title = label.name,
                                summary = "${label.groupIds.size} 个群聊",
                                onClick = { onOpen(label) }
                            )
                            if (index < labels.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupChatLabelEditorPage(
    context: Context,
    label: GroupChatLabel,
    existing: Boolean,
    onBack: () -> Unit,
    onPickGroups: (GroupChatLabel) -> Unit,
    onSave: (GroupChatLabel) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(label) { mutableStateOf(label.name) }
    val groupIds = label.groupIds
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val draft = label.copy(name = name.trim(), groupIds = groupIds)
    PageScaffold(
        title = if (existing) "编辑群聊标签" else "新增群聊标签",
        largeTitle = if (existing) "编辑群聊标签" else "新增群聊标签",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存标签",
                onPrimaryClick = {
                    if (draft.name.isBlank()) {
                        Toast.makeText(context, "请输入标签名称", Toast.LENGTH_SHORT).show()
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
            item { SmallTitle(text = "标签") }
            item {
                SettingsCard {
                    InputRow("标签名称", "用于任务和名单选择器", name) { name = it }
                    InsetDivider()
                    ActionRow(
                        title = "标签群聊",
                        summary = if (groupIds.isEmpty()) "未选择群聊" else "已选择 ${groupIds.size} 个群聊"
                    ) {
                        onPickGroups(draft)
                    }
                }
            }
            if (existing) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除标签", "删除后不会移除原功能中已经保存的群聊", onDelete)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupRenameMonitorMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, GroupRenameMonitorSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<GroupRenameRoute>(GroupRenameRoute.Main) }
    var templates by remember {
        mutableStateOf(GroupRenameMonitorSettings.parseTemplates(sp.getString(GroupRenameMonitorSettings.KEY_TEMPLATES, "").orEmpty()))
    }
    var templateBindings by remember {
        mutableStateOf(GroupRenameMonitorSettings.parseBindings(sp.getString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, "").orEmpty()))
    }
    var noticeEnabled by remember { mutableStateOf(sp.getBoolean(GroupRenameMonitorSettings.KEY_NOTICE_ENABLE, GroupRenameMonitorSettings.DEFAULT_NOTICE_ENABLE)) }
    var noticeText by remember {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_NOTICE_TEXT, GroupRenameMonitorSettings.DEFAULT_NOTICE_TEXT) ?: GroupRenameMonitorSettings.DEFAULT_NOTICE_TEXT)
    }
    var noticeScope by remember {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_NOTICE_SCOPE, GroupRenameMonitorSettings.DEFAULT_NOTICE_SCOPE) ?: GroupRenameMonitorSettings.DEFAULT_NOTICE_SCOPE)
    }
    var noticeGroups by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_NOTICE_GROUPS, "").orEmpty()) }
    var sendEnabled by remember { mutableStateOf(sp.getBoolean(GroupRenameMonitorSettings.KEY_SEND_ENABLE, GroupRenameMonitorSettings.DEFAULT_SEND_ENABLE)) }
    var listenGroups by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_LISTEN_GROUPS, "").orEmpty()) }
    var delaySeconds by remember { mutableStateOf(sp.getInt(GroupRenameMonitorSettings.KEY_DELAY_SECONDS, GroupRenameMonitorSettings.DEFAULT_DELAY_SECONDS).toString()) }
    var promptType by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE) ?: GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE) }
    var bothOrder by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_BOTH_ORDER, GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER) ?: GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER) }
    var text by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_TEXT, GroupRenameMonitorSettings.DEFAULT_TEXT) ?: GroupRenameMonitorSettings.DEFAULT_TEXT) }
    var cardTitle by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_CARD_TITLE, GroupRenameMonitorSettings.DEFAULT_CARD_TITLE) ?: GroupRenameMonitorSettings.DEFAULT_CARD_TITLE) }
    var cardDesc by remember { mutableStateOf(sp.getString(GroupRenameMonitorSettings.KEY_CARD_DESC, GroupRenameMonitorSettings.DEFAULT_CARD_DESC) ?: GroupRenameMonitorSettings.DEFAULT_CARD_DESC) }
    var wxidColor by remember {
        mutableStateOf(
            MemberTitleStore.cleanColor(
                sp.getString(GroupRenameMonitorSettings.KEY_WXID_COLOR, GroupRenameMonitorSettings.DEFAULT_WXID_COLOR)
                    ?.substringBefore(',')
            ).ifEmpty { GroupRenameMonitorSettings.DEFAULT_WXID_COLOR }
        )
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    SettingsRouteTransition(
        targetState = route,
        label = "GroupRenameRoute",
        depthOf = {
            when (it) {
                GroupRenameRoute.Main -> 0
                GroupRenameRoute.GroupManager,
                GroupRenameRoute.TemplateManager,
                GroupRenameRoute.BatchTemplateBinding,
                is GroupRenameRoute.ContactPicker -> 1
                is GroupRenameRoute.GroupEditor,
                is GroupRenameRoute.TemplateEditor -> 2
            }
        }
    ) { currentRoute ->
        when (currentRoute) {
            is GroupRenameRoute.ContactPicker -> ContactPickerPage(
                context = context,
                request = currentRoute.request,
                onBack = { route = if (currentRoute.fromMain) GroupRenameRoute.Main else GroupRenameRoute.GroupManager },
                onConfirm = { selected ->
                    currentRoute.request.onValue(formatIds(selected.map { it.id }))
                    route = if (currentRoute.fromMain) GroupRenameRoute.Main else GroupRenameRoute.GroupManager
                }
            )
            GroupRenameRoute.GroupManager -> GroupRenameGroupManagerPage(
                context = context,
                listenGroups = listenGroups,
                templates = templates,
                bindings = templateBindings,
                onPickGroups = {
                    route = GroupRenameRoute.ContactPicker(
                        ContactPickerRequest(
                            title = "选择改名监听群",
                            mode = ContactPickerMode.GROUPS,
                            multiSelect = true,
                            existingValue = listenGroups,
                            onValue = { next ->
                                listenGroups = next
                                sp.edit().putString(GroupRenameMonitorSettings.KEY_LISTEN_GROUPS, next).apply()
                            }
                        )
                    )
                },
                onOpenGroup = { groupId, label -> route = GroupRenameRoute.GroupEditor(groupId, label) },
                onOpenTemplates = { route = GroupRenameRoute.TemplateManager },
                onBatchApplyTemplate = { route = GroupRenameRoute.BatchTemplateBinding },
                onDeleteGroups = { targets ->
                    val targetIds = targets.mapTo(HashSet()) { it.id }
                    val nextListenGroups = formatIds(parseIds(listenGroups) - targetIds)
                    val nextBindings = templateBindings.filterNot { it.groupId in targetIds }
                    targetIds.forEach { groupId ->
                        deleteGroupRenameConfiguration(sp, groupId, nextListenGroups, nextBindings)
                    }
                    listenGroups = nextListenGroups
                    templateBindings = nextBindings
                    Toast.makeText(context, "已删除 ${targets.size} 个监听群", Toast.LENGTH_SHORT).show()
                },
                onBack = { route = GroupRenameRoute.Main }
            )
            is GroupRenameRoute.GroupEditor -> GroupRenameGroupEditorPage(
                context = context,
                sp = sp,
                groupId = currentRoute.groupId,
                groupLabel = currentRoute.label,
                templates = templates,
                bindings = templateBindings,
                onBindingsChanged = { next ->
                    templateBindings = next
                    sp.edit().putString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, GroupRenameMonitorSettings.encodeBindings(next)).apply()
                },
                onDelete = {
                    listenGroups = formatIds(parseIds(listenGroups) - currentRoute.groupId)
                    templateBindings = templateBindings.filterNot { it.groupId == currentRoute.groupId }
                    deleteGroupRenameConfiguration(sp, currentRoute.groupId, listenGroups, templateBindings)
                    Toast.makeText(context, "群配置已删除", Toast.LENGTH_SHORT).show()
                    route = GroupRenameRoute.GroupManager
                },
                onBack = { route = GroupRenameRoute.GroupManager }
            )
            GroupRenameRoute.TemplateManager -> GroupRenameTemplateListPage(
                templates = templates,
                bindings = templateBindings,
                onBack = { route = GroupRenameRoute.GroupManager },
                onOpenTemplate = { index, template -> route = GroupRenameRoute.TemplateEditor(index, template, true) },
                onAddTemplate = {
                    route = GroupRenameRoute.TemplateEditor(
                        templates.size,
                        newGroupRenameTemplate(templates.size + 1, sp),
                        false
                    )
                }
            )
            is GroupRenameRoute.TemplateEditor -> GroupRenameTemplateEditorPage(
                context = context,
                template = currentRoute.template,
                canDelete = currentRoute.canDelete,
                onBack = { route = GroupRenameRoute.TemplateManager },
                onSave = { updated ->
                    templates = if (currentRoute.index in templates.indices) {
                        templates.toMutableList().also { it[currentRoute.index] = updated }
                    } else {
                        templates + updated
                    }
                    sp.edit().putString(GroupRenameMonitorSettings.KEY_TEMPLATES, GroupRenameMonitorSettings.encodeTemplates(templates)).apply()
                    Toast.makeText(context, "改名模板已保存", Toast.LENGTH_SHORT).show()
                    route = GroupRenameRoute.TemplateManager
                },
                onDelete = {
                    if (currentRoute.index in templates.indices) {
                        val templateId = templates[currentRoute.index].id
                        templates = templates.toMutableList().also { it.removeAt(currentRoute.index) }
                        templateBindings = templateBindings.filterNot { it.templateId == templateId }
                        sp.edit()
                            .putString(GroupRenameMonitorSettings.KEY_TEMPLATES, GroupRenameMonitorSettings.encodeTemplates(templates))
                            .putString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, GroupRenameMonitorSettings.encodeBindings(templateBindings))
                            .apply()
                    }
                    route = GroupRenameRoute.TemplateManager
                }
            )
            GroupRenameRoute.BatchTemplateBinding -> GroupRenameBatchTemplateBindingPage(
                context = context,
                listenGroups = listenGroups,
                templates = templates,
                bindings = templateBindings,
                onBack = { route = GroupRenameRoute.GroupManager },
                onSave = { additions ->
                    templateBindings = upsertGroupRenameTemplateBindings(templateBindings, additions)
                    sp.edit().putString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, GroupRenameMonitorSettings.encodeBindings(templateBindings)).apply()
                    Toast.makeText(context, "模板已套用到 ${additions.size} 个群", Toast.LENGTH_SHORT).show()
                    route = GroupRenameRoute.GroupManager
                }
            )
            GroupRenameRoute.Main -> PageScaffold(
                title = provider.title(),
                largeTitle = provider.title(),
                scrollBehavior = scrollBehavior,
                bottomBar = {
                    BottomActionBar(
                        primaryText = "保存设置",
                        onPrimaryClick = {
                            val color = MemberTitleStore.cleanColor(wxidColor.substringBefore(','))
                                .ifEmpty { GroupRenameMonitorSettings.DEFAULT_WXID_COLOR }
                            wxidColor = color
                            sp.edit()
                                .putBoolean(GroupRenameMonitorSettings.KEY_NOTICE_ENABLE, noticeEnabled)
                                .putString(GroupRenameMonitorSettings.KEY_NOTICE_TEXT, noticeText)
                                .putString(GroupRenameMonitorSettings.KEY_NOTICE_SCOPE, noticeScope)
                                .putString(GroupRenameMonitorSettings.KEY_NOTICE_GROUPS, noticeGroups)
                                .putBoolean(GroupRenameMonitorSettings.KEY_SEND_ENABLE, sendEnabled)
                                .putString(GroupRenameMonitorSettings.KEY_LISTEN_GROUPS, listenGroups)
                                .putInt(GroupRenameMonitorSettings.KEY_DELAY_SECONDS, delaySeconds.toIntOrNull()?.coerceIn(0, 600) ?: GroupRenameMonitorSettings.DEFAULT_DELAY_SECONDS)
                                .putString(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, promptType)
                                .putString(GroupRenameMonitorSettings.KEY_BOTH_ORDER, bothOrder)
                                .putString(GroupRenameMonitorSettings.KEY_TEXT, text)
                                .putString(GroupRenameMonitorSettings.KEY_CARD_TITLE, cardTitle)
                                .putString(GroupRenameMonitorSettings.KEY_CARD_DESC, cardDesc)
                                .putString(GroupRenameMonitorSettings.KEY_WXID_COLOR, color)
                                .apply()
                            Toast.makeText(context, "改名监控设置已保存", Toast.LENGTH_SHORT).show()
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
                    item { SmallTitle(text = "提醒方式") }
                    item {
                        SettingsCard {
                            SwitchRow(noticeEnabled, "插入系统消息", "在对应群聊插入可点击成员资料的改名消息", onCheckedChange = { noticeEnabled = it })
                            InsetDivider()
                            SwitchRow(sendEnabled, "发送改名提醒", "向选择的监听群发送文本或卡片", onCheckedChange = { sendEnabled = it })
                            if (noticeEnabled) {
                                InsetDivider()
                                VariableInputRow(
                                    "系统消息模板",
                                    "wxid变量会生成可点击资料链接",
                                    noticeText,
                                    groupRenameNoticeVariables,
                                    minLines = 3
                                ) { noticeText = it }
                                InsetDivider()
                                PopupChoiceRow(
                                    title = "适用群聊",
                                    summary = systemNoticeScopeLabel(noticeScope, GroupRenameMonitorSettings.NOTICE_SCOPE_SPECIFIC),
                                    options = systemNoticeScopeChoices(
                                        GroupRenameMonitorSettings.NOTICE_SCOPE_ALL,
                                        GroupRenameMonitorSettings.NOTICE_SCOPE_SPECIFIC
                                    ),
                                    currentValue = noticeScope,
                                    onValueChanged = { noticeScope = it }
                                )
                                if (noticeScope == GroupRenameMonitorSettings.NOTICE_SCOPE_SPECIFIC) {
                                    InsetDivider()
                                    ActionRow("选择指定群聊", systemNoticeGroupSummary(noticeGroups)) {
                                        route = GroupRenameRoute.ContactPicker(
                                            request = ContactPickerRequest(
                                                title = "选择系统消息群聊",
                                                mode = ContactPickerMode.GROUPS,
                                                multiSelect = true,
                                                existingValue = noticeGroups,
                                                onValue = { noticeGroups = it }
                                            ),
                                            fromMain = true
                                        )
                                    }
                                }
                                InsetDivider()
                                ColorPickerRow(
                                    "wxid颜色",
                                    "系统消息里的 wxid 链接颜色",
                                    wxidColor,
                                    allowGradient = false,
                                    onReset = { wxidColor = GroupRenameMonitorSettings.DEFAULT_WXID_COLOR }
                                ) { wxidColor = it.take(9) }
                            }
                        }
                    }
                    if (sendEnabled) {
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "发送设置") }
                        item {
                            SettingsCard {
                                ActionRow("监听群与专属设置", groupSelectionSummary(listenGroups)) {
                                    route = GroupRenameRoute.GroupManager
                                }
                                InsetDivider()
                                ActionRow(
                                    "提醒模板管理",
                                    if (templates.isEmpty()) "暂无模板，进入后添加批量配置" else "${templates.size} 个模板，进入后修改或删除"
                                ) {
                                    route = GroupRenameRoute.TemplateManager
                                }
                                InsetDivider()
                                ActionRow(
                                    "批量套用模板",
                                    if (templates.isEmpty()) "先新增模板，再批量绑定监听群" else groupRenameBindingSummary(templateBindings, templates)
                                ) {
                                    route = GroupRenameRoute.BatchTemplateBinding
                                }
                                InsetDivider()
                                NumberInputRow("整体延迟", "单位秒，0-600", delaySeconds) { delaySeconds = it }
                                InsetDivider()
                                PopupChoiceRow(
                                    title = "提示类型",
                                    summary = groupRenamePromptTypeLabel(promptType),
                                    options = groupRenamePromptTypeChoices(),
                                    currentValue = promptType,
                                    onValueChanged = { promptType = it }
                                )
                                if (promptType == GroupRenameMonitorSettings.PROMPT_BOTH) {
                                    InsetDivider()
                                    PopupChoiceRow(
                                        title = "文本+卡片顺序",
                                        summary = groupRenameBothOrderLabel(bothOrder),
                                        options = groupRenameBothOrderChoices(),
                                        currentValue = bothOrder,
                                        onValueChanged = { bothOrder = it }
                                    )
                                }
                            }
                        }
                        if (promptType != GroupRenameMonitorSettings.PROMPT_CARD) {
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本模板") }
                            item {
                                SettingsCard {
                                    VariableInputRow("改名提醒文本", "多个模板用 || 分隔随机选择", text, groupRenameTemplateVariables, minLines = 3) { text = it }
                                }
                            }
                        }
                        if (promptType != GroupRenameMonitorSettings.PROMPT_TEXT) {
                            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "卡片模板") }
                            item {
                                SettingsCard {
                                    VariableInputRow("卡片标题", "多个模板用 || 分隔随机选择", cardTitle, groupRenameTemplateVariables) { cardTitle = it }
                                    InsetDivider()
                                    VariableInputRow("卡片描述", "多个模板用 || 分隔随机选择", cardDesc, groupRenameTemplateVariables, minLines = 3) { cardDesc = it }
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
internal fun GroupRenameGroupManagerPage(
    context: Context,
    listenGroups: String,
    templates: List<GroupRenameReplyTemplate>,
    bindings: List<GroupRenameTemplateBinding>,
    onPickGroups: () -> Unit,
    onOpenGroup: (String, String) -> Unit,
    onOpenTemplates: () -> Unit,
    onBatchApplyTemplate: () -> Unit,
    onDeleteGroups: (List<ContactOption>) -> Unit,
    onBack: () -> Unit
) {
    var loading by remember(listenGroups) { mutableStateOf(true) }
    var error by remember(listenGroups) { mutableStateOf("") }
    var groups by remember(listenGroups) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember { mutableStateOf("") }
    var batchDeleteMode by remember { mutableStateOf(false) }
    var selectedForDelete by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val selectedIds = remember(listenGroups) { parseIds(listenGroups).toList() }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(listenGroups) {
        loadContacts(context, ContactPickerMode.GROUPS) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取群聊失败"
            } else {
                val byId = result.associateBy { it.id }
                groups = selectedIds.map { id ->
                    byId[id] ?: ContactOption(id, id, group = true, avatarUrl = "", avatarBackupUrl = "")
                }
            }
        }
    }
    val lower = query.trim().lowercase(Locale.US)
    val visible = groups.filter { it.matchesSearch(lower) }
    val visibleIds = visible.mapTo(LinkedHashSet()) { it.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedForDelete }
    val selectedGroups = groups.filter { it.id in selectedForDelete }
    PageScaffold(
        title = "改名监听群",
        largeTitle = "改名监听群",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (batchDeleteMode) {
                BottomActionBar(
                    primaryText = "删除所选（${selectedGroups.size}）",
                    onPrimaryClick = {
                        if (selectedGroups.isEmpty()) {
                            Toast.makeText(context, "请先选择监听群", Toast.LENGTH_SHORT).show()
                        } else {
                            showDeleteConfirm = true
                        }
                    },
                    secondaryText = "取消",
                    onSecondaryClick = {
                        batchDeleteMode = false
                        selectedForDelete = emptySet()
                    },
                    middleText = if (visibleIds.isEmpty()) null else if (allVisibleSelected) "取消全选" else "全选",
                    onMiddleClick = if (visibleIds.isEmpty()) null else {
                        {
                            selectedForDelete = if (allVisibleSelected) {
                                selectedForDelete - visibleIds
                            } else {
                                selectedForDelete + visibleIds
                            }
                        }
                    }
                )
            } else {
                BottomActionBar(
                    primaryText = "选择监听群",
                    onPrimaryClick = onPickGroups,
                    secondaryText = "返回",
                    onSecondaryClick = onBack,
                    middleText = if (selectedIds.isEmpty()) null else "批量删除",
                    onMiddleClick = if (selectedIds.isEmpty()) null else {
                        {
                            batchDeleteMode = true
                            selectedForDelete = emptySet()
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
                    InputRow("搜索监听群", "群名称 / 群聊备注 / 群号", query) { query = it }
                }
            }
            if (!batchDeleteMode) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "模板") }
                item {
                    SettingsCard {
                        ActionRow("提醒模板管理", if (templates.isEmpty()) "暂无模板，进入后添加" else "${templates.size} 个模板") {
                            onOpenTemplates()
                        }
                        InsetDivider()
                        ActionRow(
                            "批量套用模板",
                            if (templates.isEmpty()) "先新增模板，再批量绑定监听群" else groupRenameBindingSummary(bindings, templates)
                        ) {
                            onBatchApplyTemplate()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "已监听群 · ${selectedIds.size} 项") }
            item {
                SettingsCard {
                    when {
                        loading -> EmptyText("正在载入监听群...")
                        error.isNotEmpty() -> EmptyText(error)
                        selectedIds.isEmpty() -> EmptyText("暂无监听群。点击底部“选择监听群”添加。")
                        visible.isEmpty() -> EmptyText("没有匹配结果")
                        else -> visible.forEachIndexed { index, option ->
                            if (batchDeleteMode) {
                                OptionChoiceRow(
                                    item = OptionItem(
                                        label = option.label.ifBlank { option.id },
                                        value = index,
                                        summary = groupRenameGroupBindingSummary(option.id, bindings, templates)
                                    ),
                                    selected = option.id in selectedForDelete,
                                    onClick = {
                                        selectedForDelete = if (option.id in selectedForDelete) {
                                            selectedForDelete - option.id
                                        } else {
                                            selectedForDelete + option.id
                                        }
                                    }
                                )
                            } else {
                                SelectRow(
                                    title = option.label.ifBlank { option.id },
                                    summary = groupRenameGroupBindingSummary(option.id, bindings, templates),
                                    onClick = { onOpenGroup(option.id, option.label.ifBlank { option.id }) }
                                )
                            }
                            if (index < visible.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
    }
    BatchDeleteConfirmDialog(
        show = showDeleteConfirm,
        message = "将删除已选的 ${selectedGroups.size} 个改名监听群及其专属配置，此操作不可撤销。",
        labels = selectedGroups.map { it.label.ifBlank { it.id } },
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            val targets = selectedGroups
            showDeleteConfirm = false
            batchDeleteMode = false
            selectedForDelete = emptySet()
            onDeleteGroups(targets)
        }
    )
}

@Composable
internal fun GroupRenameTemplateListPage(
    templates: List<GroupRenameReplyTemplate>,
    bindings: List<GroupRenameTemplateBinding>,
    onBack: () -> Unit,
    onOpenTemplate: (Int, GroupRenameReplyTemplate) -> Unit,
    onAddTemplate: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "改名提醒模板",
        largeTitle = "改名提醒模板",
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
                        EmptyText("暂无模板。新增后可批量套用到监听群。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            SelectRow(
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = "${describeGroupRenameTemplate(template)} · 已绑定 ${bindings.count { it.templateId == template.id }} 个群",
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
internal fun GroupRenameTemplateEditorPage(
    context: Context,
    template: GroupRenameReplyTemplate,
    canDelete: Boolean,
    onBack: () -> Unit,
    onSave: (GroupRenameReplyTemplate) -> Unit,
    onDelete: () -> Unit
) {
    var name by rememberSaveable(template.id) { mutableStateOf(template.name) }
    var enabled by rememberSaveable(template.id) { mutableStateOf(template.enabled) }
    var delaySeconds by rememberSaveable(template.id) { mutableStateOf(template.delaySeconds.toString()) }
    var promptType by rememberSaveable(template.id) { mutableStateOf(template.promptType) }
    var bothOrder by rememberSaveable(template.id) { mutableStateOf(template.bothOrder) }
    var text by rememberSaveable(template.id) { mutableStateOf(template.text) }
    var cardTitle by rememberSaveable(template.id) { mutableStateOf(template.cardTitle) }
    var cardDesc by rememberSaveable(template.id) { mutableStateOf(template.cardDesc) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = name.ifBlank { "改名提醒模板" },
        largeTitle = "编辑改名模板",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存模板",
                onPrimaryClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "请输入模板名称", Toast.LENGTH_SHORT).show()
                    } else {
                        onSave(
                            template.copy(
                                name = name.trim(),
                                enabled = enabled,
                                delaySeconds = delaySeconds.toIntOrNull()?.coerceIn(0, 600) ?: GroupRenameMonitorSettings.DEFAULT_DELAY_SECONDS,
                                promptType = GroupRenameMonitorSettings.normalizePromptType(promptType),
                                bothOrder = GroupRenameMonitorSettings.normalizeBothOrder(bothOrder),
                                text = text,
                                cardTitle = cardTitle,
                                cardDesc = cardDesc
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
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    InputRow("模板名称", "用于列表和批量套用", name) { name = it }
                    InsetDivider()
                    SwitchRow(enabled, "启用模板", "关闭后已绑定群不会发送改名提醒", onCheckedChange = { enabled = it })
                    InsetDivider()
                    NumberInputRow("整体延迟", "单位秒，0-600", delaySeconds) { delaySeconds = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提示设置") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "提示类型",
                        summary = groupRenamePromptTypeLabel(promptType),
                        options = groupRenamePromptTypeChoices(),
                        currentValue = promptType,
                        onValueChanged = { promptType = it }
                    )
                    if (promptType == GroupRenameMonitorSettings.PROMPT_BOTH) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "文本+卡片顺序",
                            summary = groupRenameBothOrderLabel(bothOrder),
                            options = groupRenameBothOrderChoices(),
                            currentValue = bothOrder,
                            onValueChanged = { bothOrder = it }
                        )
                    }
                }
            }
            if (promptType != GroupRenameMonitorSettings.PROMPT_CARD) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本模板") }
                item {
                    SettingsCard {
                        VariableInputRow("改名提醒文本", "多个模板用 || 分隔随机选择", text, groupRenameTemplateVariables, minLines = 3) { text = it }
                    }
                }
            }
            if (promptType != GroupRenameMonitorSettings.PROMPT_TEXT) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "卡片模板") }
                item {
                    SettingsCard {
                        VariableInputRow("卡片标题", "多个模板用 || 分隔随机选择", cardTitle, groupRenameTemplateVariables) { cardTitle = it }
                        InsetDivider()
                        VariableInputRow("卡片描述", "多个模板用 || 分隔随机选择", cardDesc, groupRenameTemplateVariables, minLines = 3) { cardDesc = it }
                    }
                }
            }
            if (canDelete) {
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                item {
                    SettingsCard {
                        ActionRow("删除模板", "删除后同时解除所有群绑定") { onDelete() }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupRenameBatchTemplateBindingPage(
    context: Context,
    listenGroups: String,
    templates: List<GroupRenameReplyTemplate>,
    bindings: List<GroupRenameTemplateBinding>,
    onBack: () -> Unit,
    onSave: (List<GroupRenameTemplateBinding>) -> Unit
) {
    var loading by remember(listenGroups) { mutableStateOf(true) }
    var error by remember(listenGroups) { mutableStateOf("") }
    var groups by remember(listenGroups) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember { mutableStateOf("") }
    var selectedTemplateId by remember(templates) { mutableStateOf(templates.firstOrNull()?.id.orEmpty()) }
    var selectedGroups by remember(listenGroups) { mutableStateOf(parseIds(listenGroups)) }
    val selectedIds = remember(listenGroups) { parseIds(listenGroups).toList() }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(listenGroups) {
        loadContacts(context, ContactPickerMode.GROUPS) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取群聊失败"
            } else {
                val byId = result.associateBy { it.id }
                groups = selectedIds.map { id ->
                    byId[id] ?: ContactOption(id, id, group = true, avatarUrl = "", avatarBackupUrl = "")
                }
            }
        }
    }
    val lower = query.trim().lowercase(Locale.US)
    val visible = groups.filter { it.matchesSearch(lower) }
    val visibleIds = visible.map { it.id }.toSet()
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedGroups }
    PageScaffold(
        title = "批量套用改名模板",
        largeTitle = "批量套用改名模板",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "套用模板",
                onPrimaryClick = {
                    when {
                        selectedTemplateId.isBlank() -> Toast.makeText(context, "请先选择模板", Toast.LENGTH_SHORT).show()
                        selectedGroups.isEmpty() -> Toast.makeText(context, "请先选择群", Toast.LENGTH_SHORT).show()
                        else -> {
                            val labels = groups.associate { it.id to it.label.ifBlank { it.id } }
                            onSave(selectedGroups.map { groupId ->
                                GroupRenameTemplateBinding(
                                    groupId,
                                    labels[groupId] ?: bindings.firstOrNull { it.groupId == groupId }?.label ?: groupId,
                                    selectedTemplateId
                                )
                            })
                        }
                    }
                },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (visibleIds.isNotEmpty()) if (allVisibleSelected) "取消全选" else "全选" else null,
                onMiddleClick = if (visibleIds.isNotEmpty()) {
                    {
                        selectedGroups = if (allVisibleSelected) selectedGroups - visibleIds else selectedGroups + visibleIds
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
                    PopupChoiceRow(
                        title = "选择模板",
                        summary = templates.firstOrNull { it.id == selectedTemplateId }?.name.orEmpty(),
                        options = templates.map { PopupChoice(it.name.ifBlank { it.id }, it.id) },
                        currentValue = selectedTemplateId,
                        onValueChanged = { selectedTemplateId = it }
                    )
                    InsetDivider()
                    InputRow("搜索监听群", "群名称 / 群聊备注 / 群号", query) { query = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "已选择 ${selectedGroups.size} 个群") }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入监听群...") } }
                error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                else -> visible.forEach { option ->
                    item {
                        ContactListCard(
                            option = option,
                            selected = option.id in selectedGroups,
                            multiSelect = true,
                            onClick = {
                                selectedGroups = if (option.id in selectedGroups) selectedGroups - option.id else selectedGroups + option.id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupRenameGroupEditorPage(
    context: Context,
    sp: SharedPreferences,
    groupId: String,
    groupLabel: String,
    templates: List<GroupRenameReplyTemplate>,
    bindings: List<GroupRenameTemplateBinding>,
    onBindingsChanged: (List<GroupRenameTemplateBinding>) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val globalPromptType = sp.getString(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE)
        ?: GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE
    val globalBothOrder = sp.getString(GroupRenameMonitorSettings.KEY_BOTH_ORDER, GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER)
        ?: GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER
    val globalText = sp.getString(GroupRenameMonitorSettings.KEY_TEXT, GroupRenameMonitorSettings.DEFAULT_TEXT)
        ?: GroupRenameMonitorSettings.DEFAULT_TEXT
    val globalCardTitle = sp.getString(GroupRenameMonitorSettings.KEY_CARD_TITLE, GroupRenameMonitorSettings.DEFAULT_CARD_TITLE)
        ?: GroupRenameMonitorSettings.DEFAULT_CARD_TITLE
    val globalCardDesc = sp.getString(GroupRenameMonitorSettings.KEY_CARD_DESC, GroupRenameMonitorSettings.DEFAULT_CARD_DESC)
        ?: GroupRenameMonitorSettings.DEFAULT_CARD_DESC
    var promptType by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, groupId), GroupRenameMonitorSettings.MODE_GLOBAL) ?: GroupRenameMonitorSettings.MODE_GLOBAL)
    }
    var bothOrder by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_BOTH_ORDER, groupId), globalBothOrder) ?: globalBothOrder)
    }
    var text by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_TEXT, groupId), globalText) ?: globalText)
    }
    var cardTitle by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_TITLE, groupId), globalCardTitle) ?: globalCardTitle)
    }
    var cardDesc by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_DESC, groupId), globalCardDesc) ?: globalCardDesc)
    }
    val effectivePromptType = if (promptType == GroupRenameMonitorSettings.MODE_GLOBAL) {
        GroupRenameMonitorSettings.normalizePromptType(globalPromptType)
    } else {
        GroupRenameMonitorSettings.normalizePromptType(promptType)
    }
    val boundTemplate = templates.firstOrNull { template ->
        bindings.firstOrNull { it.groupId == groupId }?.templateId == template.id
    }
    val templateOptions = listOf(PopupChoice("不使用模板", "")) + templates.map {
        PopupChoice(it.name.ifBlank { it.id }, it.id)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = groupLabel.ifBlank { groupId },
        largeTitle = "专属改名设置",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            if (boundTemplate != null) {
                BottomActionBar("返回", onBack)
            } else {
                BottomActionBar(
                    primaryText = "保存设置",
                    onPrimaryClick = {
                        sp.edit()
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, groupId), promptType)
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_BOTH_ORDER, groupId), bothOrder)
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_TEXT, groupId), text)
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_TITLE, groupId), cardTitle)
                            .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_DESC, groupId), cardDesc)
                            .apply()
                        Toast.makeText(context, "专属改名设置已保存", Toast.LENGTH_SHORT).show()
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "选择模板",
                        summary = boundTemplate?.let { describeGroupRenameTemplate(it) } ?: "不使用模板，按本群专属设置发送",
                        options = templateOptions,
                        currentValue = boundTemplate?.id.orEmpty(),
                        onValueChanged = { templateId ->
                            val next = if (templateId.isBlank()) {
                                bindings.filterNot { it.groupId == groupId }
                            } else {
                                upsertGroupRenameTemplateBindings(
                                    bindings,
                                    listOf(GroupRenameTemplateBinding(groupId, groupLabel.ifBlank { groupId }, templateId))
                                )
                            }
                            onBindingsChanged(next)
                        }
                    )
                }
            }
            if (boundTemplate == null) {
                item { SmallTitle(text = "提示设置") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "提示类型",
                            summary = groupRenamePromptTypeWithGlobalLabel(promptType),
                            options = groupRenamePromptTypeWithGlobalChoices(),
                            currentValue = promptType,
                            onValueChanged = { promptType = it }
                        )
                        if (promptType == GroupRenameMonitorSettings.PROMPT_BOTH) {
                            InsetDivider()
                            PopupChoiceRow(
                                title = "文本+卡片顺序",
                                summary = groupRenameBothOrderLabel(bothOrder),
                                options = groupRenameBothOrderChoices(),
                                currentValue = bothOrder,
                                onValueChanged = { bothOrder = it }
                            )
                        }
                    }
                }
                if (effectivePromptType != GroupRenameMonitorSettings.PROMPT_CARD) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本模板") }
                    item {
                        SettingsCard {
                            VariableInputRow("改名提醒文本", "本群专属；多个模板用 || 分隔随机选择", text, groupRenameTemplateVariables, minLines = 3) { text = it }
                        }
                    }
                }
                if (effectivePromptType != GroupRenameMonitorSettings.PROMPT_TEXT) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "卡片模板") }
                    item {
                        SettingsCard {
                            VariableInputRow("卡片标题", "本群专属；多个模板用 || 分隔随机选择", cardTitle, groupRenameTemplateVariables) { cardTitle = it }
                            InsetDivider()
                            VariableInputRow("卡片描述", "本群专属；多个模板用 || 分隔随机选择", cardDesc, groupRenameTemplateVariables, minLines = 3) { cardDesc = it }
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    if (boundTemplate == null) {
                        ActionRow("恢复全局内容", "跟随全局类型并覆盖本群模板") {
                            promptType = GroupRenameMonitorSettings.MODE_GLOBAL
                            bothOrder = globalBothOrder
                            text = globalText
                            cardTitle = globalCardTitle
                            cardDesc = globalCardDesc
                            sp.edit()
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, groupId), GroupRenameMonitorSettings.MODE_GLOBAL)
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_BOTH_ORDER, groupId), globalBothOrder)
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_TEXT, groupId), globalText)
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_TITLE, groupId), globalCardTitle)
                                .putString(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_DESC, groupId), globalCardDesc)
                                .apply()
                        }
                        InsetDivider()
                    }
                    ActionRow("删除当前群", "移除监听和本群专属配置") { onDelete() }
                }
            }
        }
    }
}

internal fun deleteGroupRenameConfiguration(
    sp: SharedPreferences,
    groupId: String,
    listenGroups: String,
    bindings: List<GroupRenameTemplateBinding>
) {
    sp.edit()
        .putString(GroupRenameMonitorSettings.KEY_LISTEN_GROUPS, listenGroups)
        .putString(GroupRenameMonitorSettings.KEY_TEMPLATE_BINDINGS, GroupRenameMonitorSettings.encodeBindings(bindings))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, groupId))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_BOTH_ORDER, groupId))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_TEXT, groupId))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_TITLE, groupId))
        .remove(GroupRenameMonitorSettings.groupKey(GroupRenameMonitorSettings.KEY_CARD_DESC, groupId))
        .apply()
}

internal fun groupRenamePromptTypeLabel(value: String): String = when (value) {
    GroupRenameMonitorSettings.PROMPT_CARD -> "卡片"
    GroupRenameMonitorSettings.PROMPT_BOTH -> "文本+卡片"
    else -> "文本"
}

internal fun groupRenamePromptTypeChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("文本", GroupRenameMonitorSettings.PROMPT_TEXT),
    PopupChoice("文本+卡片", GroupRenameMonitorSettings.PROMPT_BOTH),
    PopupChoice("卡片", GroupRenameMonitorSettings.PROMPT_CARD)
)

internal fun groupRenamePromptTypeWithGlobalLabel(value: String): String {
    return if (value == GroupRenameMonitorSettings.MODE_GLOBAL) "跟随全局" else groupRenamePromptTypeLabel(value)
}

internal fun groupRenamePromptTypeWithGlobalChoices(): List<PopupChoice<String>> {
    return listOf(PopupChoice("跟随全局", GroupRenameMonitorSettings.MODE_GLOBAL)) + groupRenamePromptTypeChoices()
}

internal fun groupRenameBothOrderLabel(value: String): String {
    return if (value == GroupRenameMonitorSettings.BOTH_CARD_FIRST) "先卡片后文本" else "先文本后卡片"
}

internal fun groupRenameBothOrderChoices(): List<PopupChoice<String>> = listOf(
    PopupChoice("先文本后卡片", GroupRenameMonitorSettings.BOTH_TEXT_FIRST),
    PopupChoice("先卡片后文本", GroupRenameMonitorSettings.BOTH_CARD_FIRST)
)

internal fun newGroupRenameTemplate(index: Int, sp: SharedPreferences): GroupRenameReplyTemplate {
    return GroupRenameReplyTemplate(
        id = "rename_${System.currentTimeMillis()}_$index",
        name = "改名模板 $index",
        enabled = true,
        delaySeconds = sp.getInt(GroupRenameMonitorSettings.KEY_DELAY_SECONDS, GroupRenameMonitorSettings.DEFAULT_DELAY_SECONDS),
        promptType = sp.getString(GroupRenameMonitorSettings.KEY_PROMPT_TYPE, GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE)
            ?: GroupRenameMonitorSettings.DEFAULT_PROMPT_TYPE,
        bothOrder = sp.getString(GroupRenameMonitorSettings.KEY_BOTH_ORDER, GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER)
            ?: GroupRenameMonitorSettings.DEFAULT_BOTH_ORDER,
        text = sp.getString(GroupRenameMonitorSettings.KEY_TEXT, GroupRenameMonitorSettings.DEFAULT_TEXT)
            ?: GroupRenameMonitorSettings.DEFAULT_TEXT,
        cardTitle = sp.getString(GroupRenameMonitorSettings.KEY_CARD_TITLE, GroupRenameMonitorSettings.DEFAULT_CARD_TITLE)
            ?: GroupRenameMonitorSettings.DEFAULT_CARD_TITLE,
        cardDesc = sp.getString(GroupRenameMonitorSettings.KEY_CARD_DESC, GroupRenameMonitorSettings.DEFAULT_CARD_DESC)
            ?: GroupRenameMonitorSettings.DEFAULT_CARD_DESC
    )
}

internal fun upsertGroupRenameTemplateBindings(
    existing: List<GroupRenameTemplateBinding>,
    additions: List<GroupRenameTemplateBinding>
): List<GroupRenameTemplateBinding> {
    val result = LinkedHashMap<String, GroupRenameTemplateBinding>()
    existing.forEach { binding ->
        if (binding.groupId.isNotBlank() && binding.templateId.isNotBlank()) {
            result[binding.groupId] = binding
        }
    }
    additions.forEach { binding ->
        if (binding.groupId.isNotBlank() && binding.templateId.isNotBlank()) {
            result[binding.groupId] = binding
        }
    }
    return result.values.toList()
}

internal fun describeGroupRenameTemplate(template: GroupRenameReplyTemplate): String {
    val type = groupRenamePromptTypeLabel(template.promptType)
    return if (template.enabled) "$type · 延迟 ${template.delaySeconds} 秒" else "已停用"
}

internal fun groupRenameBindingSummary(
    bindings: List<GroupRenameTemplateBinding>,
    templates: List<GroupRenameReplyTemplate>
): String {
    val validIds = templates.map { it.id }.toSet()
    val count = bindings.count { it.templateId in validIds }
    return if (count == 0) "暂无群绑定模板" else "$count 个群已绑定模板"
}

internal fun groupRenameGroupBindingSummary(
    groupId: String,
    bindings: List<GroupRenameTemplateBinding>,
    templates: List<GroupRenameReplyTemplate>
): String {
    val binding = bindings.firstOrNull { it.groupId == groupId } ?: return groupId
    val template = templates.firstOrNull { it.id == binding.templateId } ?: return "$groupId · 模板已失效"
    return "$groupId · ${template.name.ifBlank { template.id }}"
}

@Composable
internal fun GroupLeaveMonitorMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, GroupLeaveMonitorSettings.PREFS_NAME) }
    var groupPicker by remember { mutableStateOf<ContactPickerRequest?>(null) }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    var showGroupManager by remember { mutableStateOf(false) }
    var showTemplateManager by remember { mutableStateOf(false) }
    var showBatchTemplateBinding by remember { mutableStateOf(false) }
    var groupEditor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var templateEditor by remember { mutableStateOf<GroupLeaveTemplateEditorRequest?>(null) }
    var replyTemplates by remember {
        mutableStateOf(GroupLeaveMonitorSettings.parseTemplates(sp.getString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATES, "").orEmpty()))
    }
    var replyTemplateBindings by remember {
        mutableStateOf(GroupLeaveMonitorSettings.parseBindings(sp.getString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS, "").orEmpty()))
    }
    var leaveNoticeEnabled by remember {
        mutableStateOf(sp.getBoolean(GroupLeaveMonitorSettings.KEY_ENABLE, GroupLeaveMonitorSettings.DEFAULT_ENABLE))
    }
    var leaveNoticeText by remember {
        mutableStateOf(
            sp.getString(
                GroupLeaveMonitorSettings.KEY_LEAVE_NOTICE_TEXT,
                GroupLeaveMonitorSettings.DEFAULT_LEAVE_NOTICE_TEXT
            ) ?: GroupLeaveMonitorSettings.DEFAULT_LEAVE_NOTICE_TEXT
        )
    }
    var inviteNoticeEnabled by remember {
        mutableStateOf(
            sp.getBoolean(
                GroupLeaveMonitorSettings.KEY_INVITE_DETAIL_ENABLE,
                GroupLeaveMonitorSettings.DEFAULT_INVITE_DETAIL_ENABLE
            )
        )
    }
    var inviteNoticeText by remember {
        mutableStateOf(
            sp.getString(
                GroupLeaveMonitorSettings.KEY_INVITE_NOTICE_TEXT,
                GroupLeaveMonitorSettings.DEFAULT_INVITE_NOTICE_TEXT
            ) ?: GroupLeaveMonitorSettings.DEFAULT_INVITE_NOTICE_TEXT
        )
    }
    var noticeScope by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_NOTICE_SCOPE, GroupLeaveMonitorSettings.DEFAULT_NOTICE_SCOPE) ?: GroupLeaveMonitorSettings.DEFAULT_NOTICE_SCOPE)
    }
    var noticeGroups by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_NOTICE_GROUPS, "").orEmpty()) }
    var wxidColor by remember {
        mutableStateOf(
            MemberTitleStore.cleanColor(
                sp.getString(
                    GroupLeaveMonitorSettings.KEY_WXID_COLOR,
                    GroupLeaveMonitorSettings.DEFAULT_WXID_COLOR
                )?.substringBefore(',')
            ).ifEmpty { GroupLeaveMonitorSettings.DEFAULT_WXID_COLOR }
        )
    }
    var listenGroups by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LISTEN_GROUPS, "").orEmpty())
    }
    var promptType by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, GroupLeaveMonitorSettings.DEFAULT_PROMPT_TYPE) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_TYPE)
    }
    var bothOrder by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER)
    }
    var delaySeconds by remember {
        mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_DELAY_SECONDS, GroupLeaveMonitorSettings.DEFAULT_DELAY_SECONDS).toString())
    }
    var joinText by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_TEXT, GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT)
    }
    var leftText by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_TEXT, GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT)
    }
    var joinCardTitle by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE)
    }
    var joinCardDesc by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC)
    }
    var leftCardTitle by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE)
    }
    var leftCardDesc by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC)
    }
    var mediaOrder by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER)
    }
    var mediaSequence by remember {
        mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE)
    }
    var joinImages by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, "").orEmpty()) }
    var leftImages by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, "").orEmpty()) }
    var joinVoices by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, "").orEmpty()) }
    var leftVoices by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, "").orEmpty()) }
    var joinEmojis by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, "").orEmpty()) }
    var leftEmojis by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, "").orEmpty()) }
    var joinVideos by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, "").orEmpty()) }
    var leftVideos by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, "").orEmpty()) }
    var joinFiles by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, "").orEmpty()) }
    var leftFiles by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, "").orEmpty()) }
    var joinFavorites by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, "").orEmpty()) }
    var leftFavorites by remember { mutableStateOf(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, "").orEmpty()) }
    var promptDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS).toString()) }
    var imageDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var voiceDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var emojiDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var videoDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var fileDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    var favoriteDelay by remember { mutableStateOf(sp.getInt(GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS).toString()) }
    val listState = rememberLazyListState()
    val groupEditorListStates = remember { mutableMapOf<String, LazyListState>() }
    val scrollBehavior = MiuixScrollBehavior()

    val route = when {
        templateEditor != null -> GroupLeaveRoute.TemplateEditor(templateEditor!!)
        groupEditor != null -> GroupLeaveRoute.GroupEditor(groupEditor!!.first, groupEditor!!.second)
        showBatchTemplateBinding -> GroupLeaveRoute.BatchTemplateBinding
        showTemplateManager -> GroupLeaveRoute.TemplateManager
        favoritePicker != null -> GroupLeaveRoute.FavoritePicker(favoritePicker!!)
        groupPicker != null -> GroupLeaveRoute.ContactPicker(groupPicker!!)
        showGroupManager -> GroupLeaveRoute.GroupManager
        else -> GroupLeaveRoute.Main
    }

    SettingsRouteTransition(
        targetState = route,
        label = "GroupLeaveRouteTransition",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is GroupLeaveRoute.ContactPicker -> {
                val request = currentRoute.request
                ContactPickerPage(
                    context = context,
                    request = request,
                    onBack = { groupPicker = null },
                    onConfirm = { selected ->
                        request.onValue(formatIds(selected.map { it.id }))
                        groupPicker = null
                    }
                )
            }
            is GroupLeaveRoute.FavoritePicker -> {
                FavoritePickerPage(
                    request = currentRoute.request,
                    onBack = { favoritePicker = null }
                )
            }
            GroupLeaveRoute.GroupManager -> {
                GroupMemberGroupManagerPage(
                    context = context,
                    listenGroups = listenGroups,
                    templates = replyTemplates,
                    templateBindings = replyTemplateBindings,
                    onBack = { showGroupManager = false },
                    onOpenTemplates = { showTemplateManager = true },
                    onBatchApplyTemplate = { showBatchTemplateBinding = true },
                    onDeleteGroups = { targets ->
                        val targetIds = targets.mapTo(HashSet()) { it.id }
                        val nextListenGroups = formatIds(parseIds(listenGroups) - targetIds)
                        val nextBindings = replyTemplateBindings.filterNot { it.groupId in targetIds }
                        targetIds.forEach { groupId ->
                            deleteGroupMemberConfiguration(sp, groupId, nextListenGroups, nextBindings)
                            groupEditorListStates.remove(groupId)
                        }
                        listenGroups = nextListenGroups
                        replyTemplateBindings = nextBindings
                        Toast.makeText(context, "已删除 ${targets.size} 个监听群", Toast.LENGTH_SHORT).show()
                    },
                    onPickGroups = {
                        groupPicker = ContactPickerRequest(
                            title = "选择监听群",
                            mode = ContactPickerMode.GROUPS,
                            multiSelect = true,
                            existingValue = listenGroups,
                            onValue = { next ->
                                listenGroups = next
                                sp.edit().putString(GroupLeaveMonitorSettings.KEY_LISTEN_GROUPS, next).apply()
                            }
                        )
                    },
                    onOpenGroup = { id, label -> groupEditor = id to label }
                )
            }
            is GroupLeaveRoute.GroupEditor -> {
                GroupMemberGroupEditorPage(
                    context = context,
                    sp = sp,
                    groupId = currentRoute.groupId,
                    groupLabel = currentRoute.label,
                    templates = replyTemplates,
                    templateBindings = replyTemplateBindings,
                    listState = groupEditorListStates.getOrPut(currentRoute.groupId) { LazyListState() },
                    onTemplateBindingsChanged = { next ->
                        replyTemplateBindings = next
                        sp.edit()
                            .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS, GroupLeaveMonitorSettings.encodeBindings(next))
                            .apply()
                    },
                    onDelete = {
                        val nextListenGroups = formatIds(parseIds(listenGroups) - currentRoute.groupId)
                        val nextBindings = replyTemplateBindings.filterNot { it.groupId == currentRoute.groupId }
                        deleteGroupMemberConfiguration(
                            sp,
                            currentRoute.groupId,
                            nextListenGroups,
                            nextBindings
                        )
                        listenGroups = nextListenGroups
                        replyTemplateBindings = nextBindings
                        groupEditorListStates.remove(currentRoute.groupId)
                        Toast.makeText(context, "群配置已删除", Toast.LENGTH_SHORT).show()
                        groupEditor = null
                    },
                    onBack = { groupEditor = null }
                )
            }
            GroupLeaveRoute.TemplateManager -> {
                GroupLeaveTemplateListPage(
                    templates = replyTemplates,
                    bindings = replyTemplateBindings,
                    onBack = { showTemplateManager = false },
                    onOpenTemplate = { index, template ->
                        templateEditor = GroupLeaveTemplateEditorRequest(index, template, canDelete = true)
                    },
                    onAddTemplate = {
                        templateEditor = GroupLeaveTemplateEditorRequest(
                            replyTemplates.size,
                            newGroupLeaveTemplate(replyTemplates.size + 1, sp),
                            canDelete = false
                        )
                    }
                )
            }
            GroupLeaveRoute.BatchTemplateBinding -> {
                GroupLeaveBatchTemplateBindingPage(
                    context = context,
                    listenGroups = listenGroups,
                    templates = replyTemplates,
                    bindings = replyTemplateBindings,
                    onBack = { showBatchTemplateBinding = false },
                    onSave = { additions ->
                        val next = upsertGroupLeaveTemplateBindings(replyTemplateBindings, additions)
                        replyTemplateBindings = next
                        sp.edit()
                            .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS, GroupLeaveMonitorSettings.encodeBindings(next))
                            .apply()
                        Toast.makeText(context, "模板已套用到 ${additions.size} 个群", Toast.LENGTH_SHORT).show()
                        showBatchTemplateBinding = false
                    }
                )
            }
            is GroupLeaveRoute.TemplateEditor -> {
                val request = currentRoute.request
                GroupLeaveTemplateEditorPage(
                    context = context,
                    request = request,
                    onBack = { templateEditor = null },
                    onSave = { updated ->
                        val next = if (request.index in replyTemplates.indices) {
                            replyTemplates.toMutableList().also { it[request.index] = updated }
                        } else {
                            replyTemplates + updated
                        }
                        replyTemplates = next
                        sp.edit()
                            .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATES, GroupLeaveMonitorSettings.encodeTemplates(next))
                            .apply()
                        Toast.makeText(context, "模板已保存", Toast.LENGTH_SHORT).show()
                        templateEditor = null
                    },
                    onDelete = {
                        if (request.index in replyTemplates.indices) {
                            val deletedId = replyTemplates[request.index].id
                            val nextTemplates = replyTemplates.toMutableList().also { it.removeAt(request.index) }
                            val nextBindings = replyTemplateBindings.filterNot { it.templateId == deletedId }
                            replyTemplates = nextTemplates
                            replyTemplateBindings = nextBindings
                            sp.edit()
                                .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATES, GroupLeaveMonitorSettings.encodeTemplates(nextTemplates))
                                .putString(GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS, GroupLeaveMonitorSettings.encodeBindings(nextBindings))
                                .apply()
                            Toast.makeText(context, "模板已删除", Toast.LENGTH_SHORT).show()
                        }
                        templateEditor = null
                    }
                )
            }
            GroupLeaveRoute.Main -> PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = {
                    val cleaned = MemberTitleStore.cleanColor(wxidColor.substringBefore(','))
                        .ifEmpty { GroupLeaveMonitorSettings.DEFAULT_WXID_COLOR }
                    wxidColor = cleaned
                    sp.edit()
                        .putBoolean(GroupLeaveMonitorSettings.KEY_ENABLE, leaveNoticeEnabled)
                        .putString(GroupLeaveMonitorSettings.KEY_LEAVE_NOTICE_TEXT, leaveNoticeText)
                        .putBoolean(GroupLeaveMonitorSettings.KEY_INVITE_DETAIL_ENABLE, inviteNoticeEnabled)
                        .putString(GroupLeaveMonitorSettings.KEY_INVITE_NOTICE_TEXT, inviteNoticeText)
                        .putString(GroupLeaveMonitorSettings.KEY_NOTICE_SCOPE, noticeScope)
                        .putString(GroupLeaveMonitorSettings.KEY_NOTICE_GROUPS, noticeGroups)
                        .putString(GroupLeaveMonitorSettings.KEY_WXID_COLOR, cleaned)
                        .putString(GroupLeaveMonitorSettings.KEY_LISTEN_GROUPS, listenGroups)
                        .putInt(GroupLeaveMonitorSettings.KEY_DELAY_SECONDS, delaySeconds.toIntOrNull()?.coerceIn(0, 600) ?: GroupLeaveMonitorSettings.DEFAULT_DELAY_SECONDS)
                        .putString(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, promptType)
                        .putString(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, bothOrder)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_TEXT, joinText)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_TEXT, leftText)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, joinCardTitle)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, joinCardDesc)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, leftCardTitle)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, leftCardDesc)
                        .putString(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, mediaOrder)
                        .putString(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, mediaSequence)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, joinImages)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, leftImages)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, joinVoices)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, leftVoices)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, joinEmojis)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, leftEmojis)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, joinVideos)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, leftVideos)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, joinFiles)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, leftFiles)
                        .putString(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, joinFavorites)
                        .putString(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, leftFavorites)
                        .putInt(GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, promptDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, imageDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, voiceDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, emojiDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, videoDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, fileDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                        .putInt(GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, favoriteDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
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
            item { SmallTitle(text = "系统消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        leaveNoticeEnabled,
                        "退群系统消息",
                        "保留现有退群监控逻辑，检测到成员退出后插入可点击资料页的系统消息",
                        onCheckedChange = {
                            leaveNoticeEnabled = it
                            sp.edit().putBoolean(GroupLeaveMonitorSettings.KEY_ENABLE, it).apply()
                        }
                    )
                    if (leaveNoticeEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            "退群系统消息模板",
                            "wxid变量会生成可点击资料链接",
                            leaveNoticeText,
                            groupLeaveNoticeVariables,
                            minLines = 3
                        ) { leaveNoticeText = it }
                    }
                    InsetDivider()
                    SwitchRow(
                        inviteNoticeEnabled,
                        "邀请详情",
                        "检测到邀请进群后插入邀请者、被邀请者和累计邀请次数",
                        onCheckedChange = {
                            inviteNoticeEnabled = it
                            sp.edit().putBoolean(GroupLeaveMonitorSettings.KEY_INVITE_DETAIL_ENABLE, it).apply()
                        }
                    )
                    if (inviteNoticeEnabled) {
                        InsetDivider()
                        VariableInputRow(
                            "邀请详情系统消息模板",
                            "邀请者和被邀请者wxid会生成可点击资料链接",
                            inviteNoticeText,
                            groupInviteNoticeVariables,
                            minLines = 4
                        ) { inviteNoticeText = it }
                    }
                    if (leaveNoticeEnabled || inviteNoticeEnabled) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "适用群聊",
                            summary = systemNoticeScopeLabel(noticeScope, GroupLeaveMonitorSettings.NOTICE_SCOPE_SPECIFIC),
                            options = systemNoticeScopeChoices(
                                GroupLeaveMonitorSettings.NOTICE_SCOPE_ALL,
                                GroupLeaveMonitorSettings.NOTICE_SCOPE_SPECIFIC
                            ),
                            currentValue = noticeScope,
                            onValueChanged = { noticeScope = it }
                        )
                        if (noticeScope == GroupLeaveMonitorSettings.NOTICE_SCOPE_SPECIFIC) {
                            InsetDivider()
                            ActionRow("选择指定群聊", systemNoticeGroupSummary(noticeGroups)) {
                                groupPicker = ContactPickerRequest(
                                    title = "选择系统消息群聊",
                                    mode = ContactPickerMode.GROUPS,
                                    multiSelect = true,
                                    existingValue = noticeGroups,
                                    onValue = { noticeGroups = it }
                                )
                            }
                        }
                    }
                    InsetDivider()
                    ColorPickerRow(
                        "wxid颜色",
                        "退群和邀请详情里的 wxid 链接颜色",
                        wxidColor,
                        allowGradient = false,
                        onReset = { wxidColor = GroupLeaveMonitorSettings.DEFAULT_WXID_COLOR }
                    ) {
                        wxidColor = it.take(9)
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "进退群回复") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        GroupLeaveMonitorSettings.KEY_REPLY_ENABLE,
                        "启用自动回复",
                        "只对下方监听群生效，默认关闭",
                        GroupLeaveMonitorSettings.DEFAULT_REPLY_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        GroupLeaveMonitorSettings.KEY_JOIN_REPLY_ENABLE,
                        "进群回复",
                        "检测到新成员进群后发送欢迎内容",
                        GroupLeaveMonitorSettings.DEFAULT_JOIN_REPLY_ENABLE
                    )
                    InsetDivider()
                    SwitchRow(
                        sp,
                        GroupLeaveMonitorSettings.KEY_LEFT_REPLY_ENABLE,
                        "退群回复",
                        "检测到成员退群后发送退群内容",
                        GroupLeaveMonitorSettings.DEFAULT_LEFT_REPLY_ENABLE
                    )
                    InsetDivider()
                    ActionRow("监听群与专属设置", groupSelectionSummary(listenGroups)) {
                        showGroupManager = true
                    }
                    InsetDivider()
                    ActionRow(
                        "回复模板管理",
                        if (replyTemplates.isEmpty()) "暂无模板，进入后添加批量配置" else "${replyTemplates.size} 个模板，进入后修改或删除"
                    ) {
                        showTemplateManager = true
                    }
                    InsetDivider()
                    ActionRow(
                        "批量套用模板",
                        if (replyTemplates.isEmpty()) "先新增模板，再批量绑定监听群" else groupLeaveTemplateBindingSummary(replyTemplateBindings, replyTemplates)
                    ) {
                        showBatchTemplateBinding = true
                    }
                    InsetDivider()
                    NumberInputRow("整体延迟", "单位秒，0-600", delaySeconds) { delaySeconds = it }
                    InsetDivider()
                    PopupChoiceRow(
                        title = "提示类型",
                        summary = groupPromptTypeLabel(promptType),
                        options = groupPromptTypeChoices(),
                        currentValue = promptType,
                        onValueChanged = {
                            promptType = it
                        }
                    )
                    if (promptType == GroupLeaveMonitorSettings.PROMPT_BOTH) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "文本+卡片顺序",
                            summary = groupBothOrderLabel(bothOrder),
                            options = groupBothOrderChoices(),
                            currentValue = bothOrder,
                            onValueChanged = {
                                bothOrder = it
                            }
                        )
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "文本模板") }
            item {
                SettingsCard {
                    VariableInputRow(
                        title = "进群文本",
                        summary = "多个模板用 || 分隔随机选择",
                        value = joinText,
                        variables = groupMemberTemplateVariables,
                        minLines = 4,
                        onValueChange = { joinText = it }
                    )
                    InsetDivider()
                    VariableInputRow(
                        title = "退群文本",
                        summary = "多个模板用 || 分隔随机选择",
                        value = leftText,
                        variables = groupMemberTemplateVariables,
                        minLines = 4,
                        onValueChange = { leftText = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "卡片模板") }
            item {
                SettingsCard {
                    VariableInputRow("进群卡片标题", "支持变量", joinCardTitle, groupMemberTemplateVariables) { joinCardTitle = it }
                    InsetDivider()
                    VariableInputRow("进群卡片描述", "支持变量", joinCardDesc, groupMemberTemplateVariables, minLines = 3) { joinCardDesc = it }
                    InsetDivider()
                    VariableInputRow("退群卡片标题", "支持变量", leftCardTitle, groupMemberTemplateVariables) { leftCardTitle = it }
                    InsetDivider()
                    VariableInputRow("退群卡片描述", "支持变量", leftCardDesc, groupMemberTemplateVariables, minLines = 3) { leftCardDesc = it }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "媒体") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "媒体顺序",
                        summary = groupMediaOrderLabel(mediaOrder),
                        options = groupMediaOrderChoices(),
                        currentValue = mediaOrder,
                        onValueChanged = {
                            mediaOrder = it
                        }
                    )
                    InsetDivider()
                    InputRow("媒体类型顺序", "英文逗号分隔：image,voice,emoji,video,file,favorite", mediaSequence) { mediaSequence = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群图片", joinImages, RedPacketRuleConfig.REPLY_IMAGE) { joinImages = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群图片", leftImages, RedPacketRuleConfig.REPLY_IMAGE) { leftImages = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群语音", joinVoices, RedPacketRuleConfig.REPLY_VOICE) { joinVoices = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群语音", leftVoices, RedPacketRuleConfig.REPLY_VOICE) { leftVoices = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群表情", joinEmojis, RedPacketRuleConfig.REPLY_EMOJI) { joinEmojis = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群表情", leftEmojis, RedPacketRuleConfig.REPLY_EMOJI) { leftEmojis = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群视频", joinVideos, RedPacketRuleConfig.REPLY_VIDEO) { joinVideos = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群视频", leftVideos, RedPacketRuleConfig.REPLY_VIDEO) { leftVideos = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "进群文件", joinFiles, RedPacketRuleConfig.REPLY_FILE) { joinFiles = it }
                    InsetDivider()
                    GroupMemberMediaRow(context, "退群文件", leftFiles, RedPacketRuleConfig.REPLY_FILE) { leftFiles = it }
                    InsetDivider()
                    GroupMemberMediaRow(
                        context,
                        "进群收藏",
                        joinFavorites,
                        RedPacketRuleConfig.REPLY_FAVORITE,
                        onValueChange = { joinFavorites = it },
                        onFavoritePicker = { favoritePicker = it }
                    )
                    InsetDivider()
                    GroupMemberMediaRow(
                        context,
                        "退群收藏",
                        leftFavorites,
                        RedPacketRuleConfig.REPLY_FAVORITE,
                        onValueChange = { leftFavorites = it },
                        onFavoritePicker = { favoritePicker = it }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "精细延迟") }
            item {
                SettingsCard {
                    NumberInputRow("提示延迟", "单位 ms", promptDelay) { promptDelay = it }
                    InsetDivider()
                    NumberInputRow("图片延迟", "单位 ms", imageDelay) { imageDelay = it }
                    InsetDivider()
                    NumberInputRow("语音延迟", "单位 ms", voiceDelay) { voiceDelay = it }
                    InsetDivider()
                    NumberInputRow("表情延迟", "单位 ms", emojiDelay) { emojiDelay = it }
                    InsetDivider()
                    NumberInputRow("视频延迟", "单位 ms", videoDelay) { videoDelay = it }
                    InsetDivider()
                    NumberInputRow("文件延迟", "单位 ms", fileDelay) { fileDelay = it }
                    InsetDivider()
                    NumberInputRow("收藏延迟", "单位 ms", favoriteDelay) { favoriteDelay = it }
                }
            }
        }
    }
        }
    }
}
