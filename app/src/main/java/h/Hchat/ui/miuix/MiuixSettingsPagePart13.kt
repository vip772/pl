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
internal fun GroupMemberGroupManagerPage(
    context: Context,
    listenGroups: String,
    templates: List<GroupLeaveReplyTemplate>,
    templateBindings: List<GroupLeaveReplyTemplateBinding>,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onBatchApplyTemplate: () -> Unit,
    onDeleteGroups: (List<ContactOption>) -> Unit,
    onPickGroups: () -> Unit,
    onOpenGroup: (String, String) -> Unit
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
        title = "监听群",
        largeTitle = "监听群",
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
                        ActionRow(
                            "回复模板管理",
                            if (templates.isEmpty()) "暂无模板，进入后添加" else "${templates.size} 个模板"
                        ) {
                            onOpenTemplates()
                        }
                        InsetDivider()
                        ActionRow(
                            "批量套用模板",
                            if (templates.isEmpty()) "先新增模板，再批量绑定监听群" else groupLeaveTemplateBindingSummary(templateBindings, templates)
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
                                        summary = groupLeaveGroupBindingSummary(option.id, option.id, templateBindings, templates)
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
                                    summary = groupLeaveGroupBindingSummary(option.id, option.id, templateBindings, templates),
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
        message = "将删除已选的 ${selectedGroups.size} 个进退群监听群及其专属配置，此操作不可撤销。",
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
internal fun GroupLeaveTemplateListPage(
    templates: List<GroupLeaveReplyTemplate>,
    bindings: List<GroupLeaveReplyTemplateBinding>,
    onBack: () -> Unit,
    onOpenTemplate: (Int, GroupLeaveReplyTemplate) -> Unit,
    onAddTemplate: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = "回复模板",
        largeTitle = "回复模板",
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
                        EmptyText("暂无模板。模板保存一整套进退群回复配置，再批量套用到监听群。")
                    } else {
                        templates.forEachIndexed { index, template ->
                            val count = bindings.count { it.templateId == template.id }
                            SelectRow(
                                title = template.name.ifBlank { "模板 ${index + 1}" },
                                summary = "${describeGroupLeaveTemplate(template)} · 已绑定 $count 个群",
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
internal fun GroupLeaveBatchTemplateBindingPage(
    context: Context,
    listenGroups: String,
    templates: List<GroupLeaveReplyTemplate>,
    bindings: List<GroupLeaveReplyTemplateBinding>,
    onBack: () -> Unit,
    onSave: (List<GroupLeaveReplyTemplateBinding>) -> Unit
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

    val templateOptions = templates.map { template ->
        PopupChoice(template.name.ifBlank { template.id }, template.id)
    }
    val lower = query.trim().lowercase(Locale.US)
    val visible = groups.filter { it.matchesSearch(lower) }
    val visibleIds = visible.map { it.id }.toSet()
    val visibleAllSelected = visibleIds.isNotEmpty() && visibleIds.all { selectedGroups.contains(it) }
    PageScaffold(
        title = "批量套用模板",
        largeTitle = "批量套用模板",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "套用模板",
                onPrimaryClick = {
                    if (templates.isEmpty()) {
                        Toast.makeText(context, "请先新增模板", Toast.LENGTH_SHORT).show()
                    } else if (selectedTemplateId.isBlank()) {
                        Toast.makeText(context, "请先选择模板", Toast.LENGTH_SHORT).show()
                    } else if (selectedGroups.isEmpty()) {
                        Toast.makeText(context, "请先选择群", Toast.LENGTH_SHORT).show()
                    } else {
                        val labels = groups.associate { it.id to it.label.ifBlank { it.id } }
                        onSave(
                            selectedGroups.map { groupId ->
                                GroupLeaveReplyTemplateBinding(
                                    groupId = groupId,
                                    label = labels[groupId] ?: bindings.firstOrNull { it.groupId == groupId }?.label ?: groupId,
                                    templateId = selectedTemplateId
                                )
                            }
                        )
                    }
                },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (visibleIds.isNotEmpty()) {
                    if (visibleAllSelected) "取消全选" else "全选"
                } else {
                    null
                },
                onMiddleClick = if (visibleIds.isNotEmpty()) {
                    {
                        selectedGroups = if (visibleAllSelected) {
                            selectedGroups - visibleIds
                        } else {
                            selectedGroups + visibleIds
                        }
                    }
                } else {
                    null
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
            item { SmallTitle(text = "模板") }
            item {
                SettingsCard {
                    if (templates.isEmpty()) {
                        EmptyText("暂无模板。先进入“回复模板管理”新增模板。")
                    } else {
                        PopupChoiceRow(
                            title = "选择模板",
                            summary = templates.firstOrNull { it.id == selectedTemplateId }?.let { describeGroupLeaveTemplate(it) } ?: "未选择",
                            options = templateOptions,
                            currentValue = selectedTemplateId,
                            onValueChanged = { selectedTemplateId = it }
                        )
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "监听群") }
            item {
                SettingsCard {
                    InputRow("搜索监听群", "群名称 / 群聊备注 / 群号", query) { query = it }
                }
            }
            item {
                SettingsCard {
                    when {
                        loading -> EmptyText("正在载入监听群...")
                        error.isNotEmpty() -> EmptyText(error)
                        selectedIds.isEmpty() -> EmptyText("暂无监听群。先选择需要监听的群。")
                        visible.isEmpty() -> EmptyText("没有匹配结果")
                        else -> visible.forEachIndexed { index, option ->
                            SwitchRow(
                                checked = selectedGroups.contains(option.id),
                                title = option.label.ifBlank { option.id },
                                summary = groupLeaveGroupBindingSummary(option.id, option.id, bindings, templates),
                                onCheckedChange = { checked ->
                                    selectedGroups = if (checked) selectedGroups + option.id else selectedGroups - option.id
                                }
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
internal fun GroupLeaveTemplateEditorPage(
    context: Context,
    request: GroupLeaveTemplateEditorRequest,
    onBack: () -> Unit,
    onSave: (GroupLeaveReplyTemplate) -> Unit,
    onDelete: () -> Unit
) {
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    var name by remember(request) { mutableStateOf(request.template.name) }
    var enabled by remember(request) { mutableStateOf(request.template.enabled) }
    var joinEnabled by remember(request) { mutableStateOf(request.template.joinEnabled) }
    var leftEnabled by remember(request) { mutableStateOf(request.template.leftEnabled) }
    var promptType by remember(request) { mutableStateOf(request.template.promptType) }
    var bothOrder by remember(request) { mutableStateOf(request.template.bothOrder) }
    var joinText by remember(request) { mutableStateOf(request.template.joinText) }
    var leftText by remember(request) { mutableStateOf(request.template.leftText) }
    var joinCardTitle by remember(request) { mutableStateOf(request.template.joinCardTitle) }
    var joinCardDesc by remember(request) { mutableStateOf(request.template.joinCardDesc) }
    var leftCardTitle by remember(request) { mutableStateOf(request.template.leftCardTitle) }
    var leftCardDesc by remember(request) { mutableStateOf(request.template.leftCardDesc) }
    var mediaMode by remember(request) { mutableStateOf(request.template.mediaMode) }
    var mediaOrder by remember(request) { mutableStateOf(request.template.mediaOrder) }
    var mediaSequence by remember(request) { mutableStateOf(request.template.mediaSequence) }
    var joinImages by remember(request) { mutableStateOf(request.template.joinImages) }
    var leftImages by remember(request) { mutableStateOf(request.template.leftImages) }
    var joinVoices by remember(request) { mutableStateOf(request.template.joinVoices) }
    var leftVoices by remember(request) { mutableStateOf(request.template.leftVoices) }
    var joinEmojis by remember(request) { mutableStateOf(request.template.joinEmojis) }
    var leftEmojis by remember(request) { mutableStateOf(request.template.leftEmojis) }
    var joinVideos by remember(request) { mutableStateOf(request.template.joinVideos) }
    var leftVideos by remember(request) { mutableStateOf(request.template.leftVideos) }
    var joinFiles by remember(request) { mutableStateOf(request.template.joinFiles) }
    var leftFiles by remember(request) { mutableStateOf(request.template.leftFiles) }
    var joinFavorites by remember(request) { mutableStateOf(request.template.joinFavorites) }
    var leftFavorites by remember(request) { mutableStateOf(request.template.leftFavorites) }
    var delayMode by remember(request) { mutableStateOf(request.template.delayMode) }
    var promptDelay by remember(request) { mutableStateOf(request.template.promptDelayMs.coerceAtLeast(0).toString()) }
    var imageDelay by remember(request) { mutableStateOf(request.template.imageDelayMs.coerceAtLeast(0).toString()) }
    var voiceDelay by remember(request) { mutableStateOf(request.template.voiceDelayMs.coerceAtLeast(0).toString()) }
    var emojiDelay by remember(request) { mutableStateOf(request.template.emojiDelayMs.coerceAtLeast(0).toString()) }
    var videoDelay by remember(request) { mutableStateOf(request.template.videoDelayMs.coerceAtLeast(0).toString()) }
    var fileDelay by remember(request) { mutableStateOf(request.template.fileDelayMs.coerceAtLeast(0).toString()) }
    var favoriteDelay by remember(request) { mutableStateOf(request.template.favoriteDelayMs.coerceAtLeast(0).toString()) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    SettingsRouteTransition(
        targetState = favoritePicker,
        label = "GroupLeaveTemplateFavoriteRoute",
        depthOf = { if (it == null) 0 else 1 }
    ) { currentFavoritePicker ->
        if (currentFavoritePicker != null) {
            FavoritePickerPage(
                request = currentFavoritePicker,
                onBack = { favoritePicker = null }
            )
        } else PageScaffold(
            title = name.ifBlank { "回复模板" },
            largeTitle = name.ifBlank { "回复模板" },
            scrollBehavior = scrollBehavior,
            bottomBar = {
                BottomActionBar(
                    primaryText = "保存模板",
                    onPrimaryClick = {
                        onSave(
                            GroupLeaveReplyTemplate(
                                id = request.template.id,
                                name = name.trim().ifBlank { "模板 ${request.index + 1}" },
                                enabled = enabled,
                                joinEnabled = joinEnabled,
                                leftEnabled = leftEnabled,
                                promptType = GroupLeaveMonitorSettings.normalizePromptType(promptType),
                                bothOrder = GroupLeaveMonitorSettings.normalizeBothOrder(bothOrder),
                                joinText = joinText,
                                leftText = leftText,
                                joinCardTitle = joinCardTitle,
                                joinCardDesc = joinCardDesc,
                                leftCardTitle = leftCardTitle,
                                leftCardDesc = leftCardDesc,
                                mediaMode = GroupLeaveMonitorSettings.normalizeMediaMode(mediaMode),
                                mediaOrder = GroupLeaveMonitorSettings.normalizeMediaOrder(mediaOrder),
                                mediaSequence = mediaSequence,
                                joinImages = joinImages,
                                leftImages = leftImages,
                                joinVoices = joinVoices,
                                leftVoices = leftVoices,
                                joinEmojis = joinEmojis,
                                leftEmojis = leftEmojis,
                                joinVideos = joinVideos,
                                leftVideos = leftVideos,
                                joinFiles = joinFiles,
                                leftFiles = leftFiles,
                                joinFavorites = joinFavorites,
                                leftFavorites = leftFavorites,
                                delayMode = GroupLeaveMonitorSettings.normalizeMode(delayMode),
                                promptDelayMs = promptDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS,
                                imageDelayMs = imageDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                voiceDelayMs = voiceDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                emojiDelayMs = emojiDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                videoDelayMs = videoDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                fileDelayMs = fileDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS,
                                favoriteDelayMs = favoriteDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS
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
                    bottom = padding.calculateBottomPadding() + 84.dp
                )
            ) {
                item { SmallTitle(text = "模板") }
                item {
                    SettingsCard {
                        InputRow("模板名称", "用于区分不同群回复配置", name) { name = it }
                        InsetDivider()
                        SwitchRow(enabled, "启用模板", "关闭后绑定此模板的群不会按模板回复", onCheckedChange = { enabled = it })
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "事件") }
                item {
                    SettingsCard {
                        SwitchRow(joinEnabled, "进群回复", "绑定群检测到成员进群时发送", onCheckedChange = { joinEnabled = it })
                        InsetDivider()
                        SwitchRow(leftEnabled, "退群回复", "绑定群检测到成员退群时发送", onCheckedChange = { leftEnabled = it })
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提示模板") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "提示设置",
                            summary = groupPromptTypeWithGlobalLabel(promptType),
                            options = groupPromptTypeWithGlobalChoices(),
                            currentValue = promptType,
                            onValueChanged = { promptType = it }
                        )
                        if (promptType != GroupLeaveMonitorSettings.MODE_GLOBAL) {
                            if (promptType == GroupLeaveMonitorSettings.PROMPT_BOTH) {
                                InsetDivider()
                                PopupChoiceRow(
                                    title = "文本+卡片顺序",
                                    summary = groupBothOrderLabel(bothOrder),
                                    options = groupBothOrderChoices(),
                                    currentValue = bothOrder,
                                    onValueChanged = { bothOrder = it }
                                )
                            }
                            InsetDivider()
                            VariableInputRow("进群文本", "多个模板用 || 分隔随机选择", joinText, groupMemberTemplateVariables, minLines = 4) { joinText = it }
                            InsetDivider()
                            VariableInputRow("退群文本", "多个模板用 || 分隔随机选择", leftText, groupMemberTemplateVariables, minLines = 4) { leftText = it }
                            InsetDivider()
                            VariableInputRow("进群卡片标题", "支持变量", joinCardTitle, groupMemberTemplateVariables) { joinCardTitle = it }
                            InsetDivider()
                            VariableInputRow("进群卡片描述", "支持变量", joinCardDesc, groupMemberTemplateVariables, minLines = 3) { joinCardDesc = it }
                            InsetDivider()
                            VariableInputRow("退群卡片标题", "支持变量", leftCardTitle, groupMemberTemplateVariables) { leftCardTitle = it }
                            InsetDivider()
                            VariableInputRow("退群卡片描述", "支持变量", leftCardDesc, groupMemberTemplateVariables, minLines = 3) { leftCardDesc = it }
                        }
                    }
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "媒体") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "媒体设置",
                            summary = groupMediaModeLabel(mediaMode),
                            options = groupMediaModeChoices(),
                            currentValue = mediaMode,
                            onValueChanged = { mediaMode = it }
                        )
                        if (mediaMode == GroupLeaveMonitorSettings.MODE_CUSTOM) {
                            InsetDivider()
                            PopupChoiceRow(
                                title = "媒体顺序",
                                summary = groupMediaOrderLabel(mediaOrder),
                                options = groupMediaOrderChoices(),
                                currentValue = mediaOrder,
                                onValueChanged = { mediaOrder = it }
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
                }
                item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "延迟") }
                item {
                    SettingsCard {
                        PopupChoiceRow(
                            title = "精细延迟",
                            summary = groupModeLabel(delayMode),
                            options = groupModeChoices(),
                            currentValue = delayMode,
                            onValueChanged = { delayMode = it }
                        )
                        if (delayMode == GroupLeaveMonitorSettings.MODE_CUSTOM) {
                            InsetDivider()
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
                if (request.canDelete) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
                    item {
                        SettingsCard {
                            ActionRow("删除模板", "删除后绑定关系也会移除") {
                                onDelete()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupMemberGroupEditorPage(
    context: Context,
    sp: SharedPreferences,
    groupId: String,
    groupLabel: String,
    templates: List<GroupLeaveReplyTemplate>,
    templateBindings: List<GroupLeaveReplyTemplateBinding>,
    listState: LazyListState,
    onTemplateBindingsChanged: (List<GroupLeaveReplyTemplateBinding>) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val title = groupLabel.ifBlank { groupId }
    val boundBinding = templateBindings.firstOrNull { it.groupId == groupId }
    val boundTemplate = templates.firstOrNull { it.id == boundBinding?.templateId }
    val templateOptions = listOf(PopupChoice("不使用模板", "")) + templates.map { template ->
        PopupChoice(template.name.ifBlank { template.id }, template.id)
    }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    var joinEnabled by rememberSaveable(groupId) {
        mutableStateOf(!parseIds(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, "").orEmpty()).contains(groupId))
    }
    var leftEnabled by rememberSaveable(groupId) {
        mutableStateOf(!parseIds(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, "").orEmpty()).contains(groupId))
    }
    val globalJoinText = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_JOIN_TEXT, GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT)
    val globalLeftText = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_LEFT_TEXT, GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT)
    val globalJoinCardTitle = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE)
    val globalJoinCardDesc = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC)
    val globalLeftCardTitle = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE)
    val globalLeftCardDesc = groupMemberGlobalString(sp, GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC)
    var promptType by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, groupId), GroupLeaveMonitorSettings.MODE_GLOBAL) ?: GroupLeaveMonitorSettings.MODE_GLOBAL)
    }
    var bothOrder by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, groupId), groupBothOrderGlobal(sp)) ?: groupBothOrderGlobal(sp))
    }
    var joinText by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_JOIN_TEXT, groupId, globalJoinText))
    }
    var leftText by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_LEFT_TEXT, groupId, globalLeftText))
    }
    var joinCardTitle by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, groupId, globalJoinCardTitle))
    }
    var joinCardDesc by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, groupId, globalJoinCardDesc))
    }
    var leftCardTitle by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, groupId, globalLeftCardTitle))
    }
    var leftCardDesc by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, groupId, globalLeftCardDesc))
    }
    var mediaMode by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(groupMemberMediaModeKey(groupId), GroupLeaveMonitorSettings.MODE_GLOBAL) ?: GroupLeaveMonitorSettings.MODE_GLOBAL)
    }
    var mediaOrder by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, groupId, sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER))
    }
    var mediaSequence by rememberSaveable(groupId) {
        mutableStateOf(groupMemberString(sp, GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, groupId, sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE))
    }
    var joinImages by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, groupId), "").orEmpty()) }
    var leftImages by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, groupId), "").orEmpty()) }
    var joinVoices by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, groupId), "").orEmpty()) }
    var leftVoices by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, groupId), "").orEmpty()) }
    var joinEmojis by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, groupId), "").orEmpty()) }
    var leftEmojis by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, groupId), "").orEmpty()) }
    var joinVideos by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, groupId), "").orEmpty()) }
    var leftVideos by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, groupId), "").orEmpty()) }
    var joinFiles by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, groupId), "").orEmpty()) }
    var leftFiles by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, groupId), "").orEmpty()) }
    var joinFavorites by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, groupId), "").orEmpty()) }
    var leftFavorites by rememberSaveable(groupId) { mutableStateOf(sp.getString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, groupId), "").orEmpty()) }
    var delayMode by rememberSaveable(groupId) {
        mutableStateOf(sp.getString(groupMemberDelayModeKey(groupId), GroupLeaveMonitorSettings.MODE_GLOBAL) ?: GroupLeaveMonitorSettings.MODE_GLOBAL)
    }
    var promptDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS)) }
    var imageDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var voiceDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var emojiDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var videoDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var fileDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    var favoriteDelay by rememberSaveable(groupId) { mutableStateOf(groupMemberIntString(sp, GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, groupId, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)) }
    val scrollBehavior = MiuixScrollBehavior()

    SettingsRouteTransition(
        targetState = favoritePicker,
        label = "GroupLeaveFavoriteRoute",
        depthOf = { if (it == null) 0 else 1 }
    ) { currentFavoritePicker ->
        if (currentFavoritePicker != null) {
            FavoritePickerPage(
                request = currentFavoritePicker,
                onBack = { favoritePicker = null }
            )
        } else PageScaffold(
            title = title,
            largeTitle = "群专属设置",
            scrollBehavior = scrollBehavior,
            bottomBar = {
                if (boundTemplate != null) {
                    BottomActionBar("返回", onBack)
                } else {
                    BottomActionBar(
                        primaryText = "保存设置",
                        onPrimaryClick = {
                            sp.edit()
                                .putString(GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, formatIds(updateGroupToggle(sp, GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, groupId, joinEnabled)))
                                .putString(GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, formatIds(updateGroupToggle(sp, GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, groupId, leftEnabled)))
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, groupId), promptType)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, groupId), bothOrder)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_TEXT, groupId), joinText)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_TEXT, groupId), leftText)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, groupId), joinCardTitle)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, groupId), joinCardDesc)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, groupId), leftCardTitle)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, groupId), leftCardDesc)
                                .putString(groupMemberMediaModeKey(groupId), mediaMode)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, groupId), mediaOrder)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, groupId), mediaSequence)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, groupId), joinImages)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, groupId), leftImages)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, groupId), joinVoices)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, groupId), leftVoices)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, groupId), joinEmojis)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, groupId), leftEmojis)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, groupId), joinVideos)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, groupId), leftVideos)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, groupId), joinFiles)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, groupId), leftFiles)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, groupId), joinFavorites)
                                .putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, groupId), leftFavorites)
                                .putString(groupMemberDelayModeKey(groupId), delayMode)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, groupId), promptDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, groupId), imageDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, groupId), voiceDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, groupId), emojiDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, groupId), videoDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, groupId), fileDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .putInt(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, groupId), favoriteDelay.toIntOrNull()?.coerceAtLeast(0) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
                                .apply()
                            Toast.makeText(context, "群专属设置已保存", Toast.LENGTH_SHORT).show()
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
                        if (templates.isEmpty()) {
                            EmptyText("暂无模板。先返回回复模板管理新增模板。")
                        } else {
                            PopupChoiceRow(
                                title = "选择模板",
                                summary = boundTemplate?.let { describeGroupLeaveTemplate(it) }
                                    ?: "不使用模板，配置本群专属设置",
                                options = templateOptions,
                                currentValue = boundTemplate?.id.orEmpty(),
                                onValueChanged = { templateId ->
                                    val next = if (templateId.isBlank()) {
                                        templateBindings.filterNot { it.groupId == groupId }
                                    } else {
                                        upsertGroupLeaveTemplateBindings(
                                            templateBindings,
                                            listOf(GroupLeaveReplyTemplateBinding(groupId, title, templateId))
                                        )
                                    }
                                    onTemplateBindingsChanged(next)
                                    Toast.makeText(
                                        context,
                                        if (templateId.isBlank()) "已取消模板绑定" else "模板已绑定",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
                if (boundTemplate != null) {
                    item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "群号") }
                    item {
                        SettingsCard {
                            SelectRow(title = title, summary = groupId, onClick = {})
                        }
                    }
                } else {
                    item { SmallTitle(text = "事件") }
            item {
                SettingsCard {
                    SwitchRow(joinEnabled, "进群回复", "该群检测到成员进群时发送", onCheckedChange = { joinEnabled = it })
                    InsetDivider()
                    SwitchRow(leftEnabled, "退群回复", "该群检测到成员退群时发送", onCheckedChange = { leftEnabled = it })
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "提示模板") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "提示设置",
                        summary = groupPromptTypeWithGlobalLabel(promptType),
                        options = groupPromptTypeWithGlobalChoices(),
                        currentValue = promptType,
                        onValueChanged = {
                            promptType = it
                            sp.edit().putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, groupId), it).apply()
                        }
                    )
                    if (promptType != GroupLeaveMonitorSettings.MODE_GLOBAL) {
                        if (promptType == GroupLeaveMonitorSettings.PROMPT_BOTH) {
                            InsetDivider()
                            PopupChoiceRow(
                                title = "文本+卡片顺序",
                                summary = groupBothOrderLabel(bothOrder),
                                options = groupBothOrderChoices(),
                                currentValue = bothOrder,
                                onValueChanged = {
                                    bothOrder = it
                                    sp.edit().putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, groupId), it).apply()
                                }
                            )
                        }
                        InsetDivider()
                        VariableInputRow("进群文本", "多个模板用 || 分隔随机选择", joinText, groupMemberTemplateVariables, minLines = 4) { joinText = it }
                        InsetDivider()
                        VariableInputRow("退群文本", "多个模板用 || 分隔随机选择", leftText, groupMemberTemplateVariables, minLines = 4) { leftText = it }
                        InsetDivider()
                        ActionRow("随机填充文本", "随机生成本群进群和退群文本") {
                            joinText = groupRandomJoinTexts[Random.nextInt(groupRandomJoinTexts.size)]
                            leftText = groupRandomLeftTexts[Random.nextInt(groupRandomLeftTexts.size)]
                            Toast.makeText(context, "已随机填充本群文本", Toast.LENGTH_SHORT).show()
                        }
                        InsetDivider()
                        ActionRow("恢复全局文本", "用当前全局文本覆盖本群文本") {
                            joinText = globalJoinText
                            leftText = globalLeftText
                            Toast.makeText(context, "已恢复为全局文本", Toast.LENGTH_SHORT).show()
                        }
                        InsetDivider()
                        VariableInputRow("进群卡片标题", "支持变量", joinCardTitle, groupMemberTemplateVariables) { joinCardTitle = it }
                        InsetDivider()
                        VariableInputRow("进群卡片描述", "支持变量", joinCardDesc, groupMemberTemplateVariables, minLines = 3) { joinCardDesc = it }
                        InsetDivider()
                        VariableInputRow("退群卡片标题", "支持变量", leftCardTitle, groupMemberTemplateVariables) { leftCardTitle = it }
                        InsetDivider()
                        VariableInputRow("退群卡片描述", "支持变量", leftCardDesc, groupMemberTemplateVariables, minLines = 3) { leftCardDesc = it }
                        InsetDivider()
                        ActionRow("随机填充卡片", "随机生成本群进群和退群卡片") {
                            joinCardTitle = groupRandomJoinCardTitles[Random.nextInt(groupRandomJoinCardTitles.size)]
                            joinCardDesc = groupRandomJoinCardDescs[Random.nextInt(groupRandomJoinCardDescs.size)]
                            leftCardTitle = groupRandomLeftCardTitles[Random.nextInt(groupRandomLeftCardTitles.size)]
                            leftCardDesc = groupRandomLeftCardDescs[Random.nextInt(groupRandomLeftCardDescs.size)]
                            Toast.makeText(context, "已随机填充本群卡片", Toast.LENGTH_SHORT).show()
                        }
                        InsetDivider()
                        ActionRow("恢复全局卡片", "用当前全局卡片覆盖本群卡片") {
                            joinCardTitle = globalJoinCardTitle
                            joinCardDesc = globalJoinCardDesc
                            leftCardTitle = globalLeftCardTitle
                            leftCardDesc = globalLeftCardDesc
                            Toast.makeText(context, "已恢复为全局卡片", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "媒体") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "媒体设置",
                        summary = groupMediaModeLabel(mediaMode),
                        options = groupMediaModeChoices(),
                        currentValue = mediaMode,
                        onValueChanged = {
                            mediaMode = it
                            sp.edit().putString(groupMemberMediaModeKey(groupId), it).apply()
                        }
                    )
                    if (mediaMode == GroupLeaveMonitorSettings.MODE_CUSTOM) {
                        InsetDivider()
                        PopupChoiceRow(
                            title = "媒体顺序",
                            summary = groupMediaOrderLabel(mediaOrder),
                            options = groupMediaOrderChoices(),
                            currentValue = mediaOrder,
                            onValueChanged = {
                                mediaOrder = it
                                sp.edit().putString(groupMemberGroupKey(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, groupId), it).apply()
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
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "延迟") }
            item {
                SettingsCard {
                    PopupChoiceRow(
                        title = "精细延迟",
                        summary = groupModeLabel(delayMode),
                        options = groupModeChoices(),
                        currentValue = delayMode,
                        onValueChanged = {
                            delayMode = it
                            sp.edit().putString(groupMemberDelayModeKey(groupId), it).apply()
                        }
                    )
                    if (delayMode == GroupLeaveMonitorSettings.MODE_CUSTOM) {
                        InsetDivider()
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
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("删除当前群", "移除监听、模板绑定和本群专属设置") {
                        onDelete()
                    }
                }
            }
        }
    }
}
}

internal fun deleteGroupMemberConfiguration(
    sp: SharedPreferences,
    groupId: String,
    nextListenGroups: String,
    nextBindings: List<GroupLeaveReplyTemplateBinding>
) {
    val groupKeys = listOf(
        GroupLeaveMonitorSettings.KEY_PROMPT_TYPE,
        GroupLeaveMonitorSettings.KEY_BOTH_ORDER,
        GroupLeaveMonitorSettings.KEY_JOIN_TEXT,
        GroupLeaveMonitorSettings.KEY_LEFT_TEXT,
        GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE,
        GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC,
        GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE,
        GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC,
        GroupLeaveMonitorSettings.KEY_MEDIA_ORDER,
        GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE,
        GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS,
        GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS,
        GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS,
        GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS,
        GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS
    )
    val joinDisabled = formatIds(
        parseIds(sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, "").orEmpty()) - groupId
    )
    val leftDisabled = formatIds(
        parseIds(sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, "").orEmpty()) - groupId
    )
    val editor = sp.edit()
        .putString(GroupLeaveMonitorSettings.KEY_LISTEN_GROUPS, nextListenGroups)
        .putString(GroupLeaveMonitorSettings.KEY_JOIN_DISABLED_GROUPS, joinDisabled)
        .putString(GroupLeaveMonitorSettings.KEY_LEFT_DISABLED_GROUPS, leftDisabled)
        .putString(
            GroupLeaveMonitorSettings.KEY_REPLY_TEMPLATE_BINDINGS,
            GroupLeaveMonitorSettings.encodeBindings(nextBindings)
        )
        .remove(groupMemberMediaModeKey(groupId))
        .remove(groupMemberDelayModeKey(groupId))
    groupKeys.forEach { key -> editor.remove(groupMemberGroupKey(key, groupId)) }
    sp.all.keys
        .filter { it.startsWith(GroupLeaveMonitorSettings.KEY_INVITE_COUNT_PREFIX + groupId + "|") }
        .forEach { key -> editor.remove(key) }
    editor.apply()
}

@Composable
internal fun GroupMemberMediaRow(
    context: Context,
    title: String,
    value: String,
    replyMode: Int,
    onFavoritePicker: ((FavoritePickerRequest) -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    ActionRow(title, redPacketReplyContentSummary(replyMode, value)) {
        if (replyMode == RedPacketRuleConfig.REPLY_FAVORITE) {
            onFavoritePicker?.invoke(
                FavoritePickerRequest(
                    title = title,
                    existingValue = value,
                    onValue = onValueChange,
                    multiSelect = true,
                    delimiter = "|"
                )
            )
            return@ActionRow
        }
        val activity = context as? Activity
        if (activity == null) {
            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
            return@ActionRow
        }
        RedPacketReplyFilePickerBridge.launch(activity, replyMode) { paths ->
            if (paths.isNotEmpty()) {
                onValueChange(paths.joinToString("|"))
                Toast.makeText(context, "已选择 ${paths.size} 个文件", Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (value.isNotBlank()) {
        InsetDivider()
        ActionRow(
            "清空$title",
            if (replyMode == RedPacketRuleConfig.REPLY_FAVORITE) "移除已选择收藏" else "移除已选择文件"
        ) {
            onValueChange("")
        }
    }
}

internal fun groupSelectionSummary(value: String): String {
    val count = parseIds(value).size
    return if (count == 0) "未选择监听群" else "已选择 $count 个群"
}

internal fun systemNoticeScopeChoices(allValue: String, specificValue: String): List<PopupChoice<String>> = listOf(
    PopupChoice("全部群聊", allValue),
    PopupChoice("指定群聊", specificValue)
)

internal fun systemNoticeScopeLabel(value: String, specificValue: String): String {
    return if (value == specificValue) "指定群聊" else "全部群聊"
}

internal fun systemNoticeGroupSummary(value: String): String {
    val count = parseIds(value).size
    return if (count == 0) "未选择群聊" else "已选择 $count 个群"
}

internal fun describeGroupLeaveTemplate(template: GroupLeaveReplyTemplate): String {
    val join = if (template.joinEnabled) "进群" else ""
    val left = if (template.leftEnabled) "退群" else ""
    val scope = listOf(join, left).filter { it.isNotBlank() }.joinToString("、").ifBlank { "未启用事件" }
    val media = when (template.mediaMode) {
        GroupLeaveMonitorSettings.MEDIA_NONE -> "不发媒体"
        GroupLeaveMonitorSettings.MODE_CUSTOM -> "自定义媒体"
        else -> "跟随全局媒体"
    }
    val delay = when (template.delayMode) {
        GroupLeaveMonitorSettings.MODE_CUSTOM -> "自定义延迟"
        else -> "跟随全局延迟"
    }
    return "$scope · ${groupPromptTypeWithGlobalLabel(template.promptType)} · $media · $delay"
}

internal fun groupLeaveTemplateBindingSummary(
    bindings: List<GroupLeaveReplyTemplateBinding>,
    templates: List<GroupLeaveReplyTemplate>
): String {
    if (templates.isEmpty()) return "暂无模板"
    val count = bindings.size
    return if (count == 0) "暂无群已绑定模板" else "已绑定 $count 个群"
}

internal fun groupLeaveGroupBindingSummary(
    groupId: String,
    defaultLabel: String,
    bindings: List<GroupLeaveReplyTemplateBinding>,
    templates: List<GroupLeaveReplyTemplate>
): String {
    val binding = bindings.firstOrNull { it.groupId == groupId } ?: return "未绑定模板"
    val template = templates.firstOrNull { it.id == binding.templateId } ?: return "模板已不存在"
    return "模板：${template.name.ifBlank { binding.label.ifBlank { defaultLabel } }}"
}

internal fun newGroupLeaveTemplate(index: Int, sp: SharedPreferences): GroupLeaveReplyTemplate {
    return GroupLeaveReplyTemplate(
        id = System.currentTimeMillis().toString() + "_" + index,
        name = "模板 $index",
        enabled = true,
        joinEnabled = sp.getBoolean(GroupLeaveMonitorSettings.KEY_JOIN_REPLY_ENABLE, GroupLeaveMonitorSettings.DEFAULT_JOIN_REPLY_ENABLE),
        leftEnabled = sp.getBoolean(GroupLeaveMonitorSettings.KEY_LEFT_REPLY_ENABLE, GroupLeaveMonitorSettings.DEFAULT_LEFT_REPLY_ENABLE),
        promptType = sp.getString(GroupLeaveMonitorSettings.KEY_PROMPT_TYPE, GroupLeaveMonitorSettings.DEFAULT_PROMPT_TYPE) ?: GroupLeaveMonitorSettings.DEFAULT_PROMPT_TYPE,
        bothOrder = sp.getString(GroupLeaveMonitorSettings.KEY_BOTH_ORDER, GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_BOTH_ORDER,
        joinText = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_TEXT, GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_TEXT,
        leftText = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_TEXT, GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_TEXT,
        joinCardTitle = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_TITLE,
        joinCardDesc = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC) ?: GroupLeaveMonitorSettings.DEFAULT_JOIN_CARD_DESC,
        leftCardTitle = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_TITLE, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_TITLE,
        leftCardDesc = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_CARD_DESC, GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC) ?: GroupLeaveMonitorSettings.DEFAULT_LEFT_CARD_DESC,
        mediaMode = GroupLeaveMonitorSettings.MODE_CUSTOM,
        mediaOrder = sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_ORDER, GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_ORDER,
        mediaSequence = sp.getString(GroupLeaveMonitorSettings.KEY_MEDIA_SEQUENCE, GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE) ?: GroupLeaveMonitorSettings.DEFAULT_MEDIA_SEQUENCE,
        joinImages = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_IMAGE_PATHS, "").orEmpty(),
        leftImages = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_IMAGE_PATHS, "").orEmpty(),
        joinVoices = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_VOICE_PATHS, "").orEmpty(),
        leftVoices = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_VOICE_PATHS, "").orEmpty(),
        joinEmojis = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_EMOJI_PATHS, "").orEmpty(),
        leftEmojis = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_EMOJI_PATHS, "").orEmpty(),
        joinVideos = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_VIDEO_PATHS, "").orEmpty(),
        leftVideos = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_VIDEO_PATHS, "").orEmpty(),
        joinFiles = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_FILE_PATHS, "").orEmpty(),
        leftFiles = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_FILE_PATHS, "").orEmpty(),
        joinFavorites = sp.getString(GroupLeaveMonitorSettings.KEY_JOIN_FAVORITE_PATHS, "").orEmpty(),
        leftFavorites = sp.getString(GroupLeaveMonitorSettings.KEY_LEFT_FAVORITE_PATHS, "").orEmpty(),
        delayMode = GroupLeaveMonitorSettings.MODE_CUSTOM,
        promptDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_PROMPT_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_PROMPT_DELAY_MS),
        imageDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_IMAGE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        voiceDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_VOICE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        emojiDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_EMOJI_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        videoDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_VIDEO_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        fileDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_FILE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS),
        favoriteDelayMs = sp.getInt(GroupLeaveMonitorSettings.KEY_FAVORITE_DELAY_MS, GroupLeaveMonitorSettings.DEFAULT_MEDIA_DELAY_MS)
    )
}
