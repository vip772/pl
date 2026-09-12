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

internal fun ClickHintTag() {
    Text(
        text = "单击",
        color = MiuixTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

object ScriptPluginSettingsMiuixContent {

@Composable
fun ScriptPluginSettingsContent(
    context: Context,
    onOpenReadme: (ScriptPluginRuntime.ScriptPlugin) -> Unit,
    onOpenMarket: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenManager: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sp = remember { HchatStorage.preferences(context, ScriptPluginSettings.PREFS_NAME) }
    val pluginRootPath = remember(context) { ScriptPluginRuntime.scriptDir(context).absolutePath }
    var pluginListVersion by remember { mutableStateOf(0) }
    val managedPlugins = remember(pluginListVersion) { ScriptPluginManager.listForDisplay(context) }
    val plugins = remember(managedPlugins) { managedPlugins.map { it.plugin } }
    val pinnedIds = remember(managedPlugins) {
        managedPlugins.filter { it.pinned }.mapTo(LinkedHashSet()) { it.plugin.id }
    }
    var globalEnabled by remember {
        mutableStateOf(sp.getBoolean(ScriptPluginSettings.KEY_ENABLE, ScriptPluginSettings.DEFAULT_ENABLE))
    }
    var pluginEnabledStates by remember(plugins) {
        mutableStateOf(plugins.associate { it.id to ScriptPluginRuntime.isPluginEnabled(context, it.id) })
    }
    var showPluginRootDialog by remember { mutableStateOf(false) }
    var actionPlugin by remember { mutableStateOf<ScriptPluginRuntime.ScriptPlugin?>(null) }
    var renamePlugin by remember { mutableStateOf<ScriptPluginRuntime.ScriptPlugin?>(null) }
    var deletePlugin by remember { mutableStateOf<ScriptPluginRuntime.ScriptPlugin?>(null) }
    DisposableEffect(context) {
        val subscription = ScriptPluginRuntime.subscribePluginCatalog(context) {
            Handler(Looper.getMainLooper()).post {
                pluginEnabledStates = ScriptPluginRuntime.listPlugins(context)
                    .associate { it.id to ScriptPluginRuntime.isPluginEnabled(context, it.id) }
                globalEnabled = sp.getBoolean(
                    ScriptPluginSettings.KEY_ENABLE,
                    ScriptPluginSettings.DEFAULT_ENABLE
                )
                pluginListVersion++
            }
        }
        onDispose { subscription.unsubscribe() }
    }

    Column {
        SettingsCard {
            PathSwitchRow(
                globalEnabled,
                "插件总开关",
                "启动时自动加载已启用插件\n相关说明:\n请确认插件安全再进行加载,\n否则造成的后果需自行承担。",
                onInfoClick = { showPluginRootDialog = true },
            ) { next ->
                val old = globalEnabled
                globalEnabled = next
                Thread({
                    val result = ScriptPluginRuntime.setGlobalEnabled(context, next)
                    Handler(Looper.getMainLooper()).post {
                        if (result.isFailure) {
                            globalEnabled = old
                            Toast.makeText(
                                context,
                                "切换失败: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            pluginListVersion++
                        }
                    }
                }, "Hchat-Script-Global").start()
            }
        }
        if (showPluginRootDialog) {
            ScriptPluginPathDialog(
                context = context,
                title = "插件目录",
                path = pluginRootPath,
                onClose = { showPluginRootDialog = false }
            )
        }
        SettingsCard(modifier = Modifier.padding(top = 10.dp)) {
            ActionRow("插件 Agent", "按需求生成或修改脚本插件") {
                onOpenAgent()
            }
        }
        SettingsCard(modifier = Modifier.padding(top = 10.dp)) {
            ActionRow("在线插件", "浏览、安装或上传社区脚本插件") {
                onOpenMarket()
            }
        }
        SettingsCard(modifier = Modifier.padding(top = 10.dp)) {
            ActionRow("本地插件管理", "排序、置顶、导入、导出或批量管理插件") {
                onOpenManager()
            }
        }
        SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "本地插件(${plugins.size})")
        SettingsCard {
            if (plugins.isEmpty()) {
                Text(
                    text = "暂无插件",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            } else {
                plugins.forEachIndexed { index, plugin ->
                    val checked = pluginEnabledStates[plugin.id]
                        ?: ScriptPluginRuntime.isPluginEnabled(context, plugin.id)
                    val summary = buildString {
                        append(plugin.dir.name)
                        append("\n")
                        append("作者: ")
                        append(plugin.author.ifBlank { "未知" })
                        append(" | 更新于: ")
                        append(plugin.updateTime.ifBlank { "未知" })
                    }
                    val title = buildString {
                        append(plugin.displayName ?: "未知")
                        append("(")
                        append(plugin.version.ifBlank { "未知" })
                        append(")")
                    }
                    ScriptPluginRow(
                        checked = checked,
                        title = title,
                        summary = summary,
                        showSettings = ScriptPluginRuntime.canOpenSettings(plugin),
                        onOpenReadme = { onOpenReadme(plugin) },
                        onOpenSettings = {
                            val result = ScriptPluginRuntime.callOpenSettings(plugin.id)
                            if (result.isFailure) {
                                Toast.makeText(
                                    context,
                                    result.exceptionOrNull()?.message ?: "打开设置失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onOpenManager = { actionPlugin = plugin },
                        onCheckedChange = { next ->
                            val oldMap = pluginEnabledStates
                            pluginEnabledStates = pluginEnabledStates + (plugin.id to next)
                            Thread({
                                val result = ScriptPluginRuntime.setPluginEnabled(context, plugin.id, next)
                                Handler(Looper.getMainLooper()).post {
                                    if (result.isFailure) {
                                        pluginEnabledStates = oldMap
                                        Toast.makeText(
                                            context,
                                            "加载[${plugin.displayName ?: "未知"}]失败，已自动关闭",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }, "Hchat-Script-${plugin.id}").start()
                        }
                    )
                    if (index != plugins.lastIndex) InsetDivider()
                }
            }
        }
        actionPlugin?.let { plugin ->
            ScriptPluginActionDialog(
                plugin = plugin,
                pinned = plugin.id in pinnedIds,
                onDismiss = { actionPlugin = null },
                onPinChanged = { pinned ->
                    actionPlugin = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ScriptPluginManager.setPinned(context, listOf(plugin.id), pinned)
                        }
                        result.fold(
                            onSuccess = {
                                pluginListVersion++
                                Toast.makeText(
                                    context,
                                    if (pinned) "已置顶" else "已取消置顶",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onFailure = { showScriptPluginManagerError(context, it) }
                        )
                    }
                },
                onRename = {
                    actionPlugin = null
                    showAfterDialogDismiss(context) { renamePlugin = plugin }
                },
                onExport = {
                    actionPlugin = null
                    showAfterDialogDismiss(context) {
                        launchScriptPluginExport(
                            context = context,
                            plugins = listOf(plugin)
                        )
                    }
                },
                onDelete = {
                    actionPlugin = null
                    showAfterDialogDismiss(context) { deletePlugin = plugin }
                }
            )
        }
        renamePlugin?.let { plugin ->
            ScriptPluginRenameDialog(
                plugin = plugin,
                onDismiss = { renamePlugin = null },
                onConfirm = { name ->
                    renamePlugin = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ScriptPluginManager.renamePlugin(context, plugin.id, name)
                        }
                        result.fold(
                            onSuccess = {
                                pluginListVersion++
                                Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { showScriptPluginManagerError(context, it) }
                        )
                    }
                }
            )
        }
        deletePlugin?.let { plugin ->
            ScriptPluginDeleteDialog(
                pluginNames = listOf(plugin.displayName ?: plugin.name.ifBlank { plugin.id }),
                onDismiss = { deletePlugin = null },
                onConfirm = {
                    deletePlugin = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ScriptPluginManager.deletePlugin(context, plugin.id)
                        }
                        result.fold(
                            onSuccess = {
                                pluginListVersion++
                                Toast.makeText(context, "插件已删除", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { showScriptPluginManagerError(context, it) }
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun ScriptPluginManagerPage(
    context: Context,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    var query by rememberSaveable { mutableStateOf("") }
    var refreshVersion by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var managedPlugins by remember {
        mutableStateOf<List<ScriptPluginManager.ManagedPlugin>>(emptyList())
    }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var draggedPluginId by remember { mutableStateOf<String?>(null) }
    var dragOrderChanged by remember { mutableStateOf(false) }
    var actionPlugin by remember { mutableStateOf<ScriptPluginManager.ManagedPlugin?>(null) }
    var renamePlugin by remember { mutableStateOf<ScriptPluginRuntime.ScriptPlugin?>(null) }
    var deletePlugins by remember { mutableStateOf<List<ScriptPluginRuntime.ScriptPlugin>>(emptyList()) }
    var importInspection by remember { mutableStateOf<ScriptPluginManager.ImportInspection?>(null) }
    var importOverwriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busyText by remember { mutableStateOf("") }
    val pageActive = remember { AtomicBoolean(true) }
    val activeImportSessionId = remember { AtomicReference<String?>(null) }

    LaunchedEffect(refreshVersion) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                ScriptPluginManager.cleanupStaleDisplayIds(context).getOrThrow()
                ScriptPluginManager.listForDisplay(context)
            }
        }
        result.fold(
            onSuccess = { plugins ->
                managedPlugins = plugins
                selectedIds = selectedIds.intersect(plugins.mapTo(LinkedHashSet()) { it.plugin.id })
            },
            onFailure = { showScriptPluginManagerError(context, it) }
        )
        loading = false
    }
    DisposableEffect(context) {
        val subscription = ScriptPluginRuntime.subscribePluginCatalog(context) {
            Handler(Looper.getMainLooper()).post { refreshVersion++ }
        }
        onDispose { subscription.unsubscribe() }
    }
    DisposableEffect(Unit) {
        pageActive.set(true)
        onDispose {
            pageActive.set(false)
            activeImportSessionId.getAndSet(null)?.let { sessionId ->
                Thread({ ScriptPluginManager.discardImport(context, sessionId) }, "Hchat-Plugin-Import-Cleanup").start()
            }
        }
    }

    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val visiblePlugins = managedPlugins.filter { item ->
        val plugin = item.plugin
        normalizedQuery.isEmpty() ||
            plugin.id.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            plugin.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            plugin.author.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            plugin.version.lowercase(Locale.ROOT).contains(normalizedQuery)
    }
    val pinnedPlugins = visiblePlugins.filter { it.pinned }
    val normalPlugins = visiblePlugins.filterNot { it.pinned }
    val visibleIds = visiblePlugins.mapTo(LinkedHashSet()) { it.plugin.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }

    fun refresh() {
        refreshVersion++
    }

    fun setPinned(ids: Set<String>, pinned: Boolean) {
        if (ids.isEmpty() || busyText.isNotEmpty()) return
        busyText = if (pinned) "正在置顶" else "正在取消置顶"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ScriptPluginManager.setPinned(context, ids, pinned)
            }
            busyText = ""
            result.fold(
                onSuccess = {
                    refresh()
                    Toast.makeText(
                        context,
                        if (pinned) "已置顶 ${ids.size} 个插件" else "已取消置顶 ${ids.size} 个插件",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { showScriptPluginManagerError(context, it) }
            )
        }
    }

    fun commitOrder() {
        val ids = managedPlugins.map { it.plugin.id }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ScriptPluginManager.saveDisplayOrder(context, ids)
            }
            result.onFailure { showScriptPluginManagerError(context, it) }
        }
    }

    fun movePlugin(item: ScriptPluginManager.ManagedPlugin, delta: Int): Boolean {
        if (query.isNotBlank()) return false
        val index = managedPlugins.indexOfFirst { it.plugin.id == item.plugin.id }
        val targetIndex = index + delta
        if (index < 0 || targetIndex !in managedPlugins.indices) return false
        if (managedPlugins[targetIndex].pinned != item.pinned) return false
        val next = managedPlugins.toMutableList()
        val moved = next.removeAt(index)
        next.add(targetIndex, moved)
        managedPlugins = next
        return true
    }

    fun launchImportPicker() {
        val activity = context as? Activity
        if (activity == null) {
            Toast.makeText(context, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
            return
        }
        ScriptPluginDocumentBridge.launchImport(activity) { uri ->
            if (!pageActive.get()) return@launchImport
            busyText = "正在检查导入包"
            Thread({
                val result = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        ScriptPluginManager.inspectImport(context, input).getOrThrow()
                    } ?: error("无法读取所选文件")
                }
                if (!pageActive.get()) {
                    result.getOrNull()?.let { inspection ->
                        ScriptPluginManager.discardImport(context, inspection.sessionId)
                    }
                    return@Thread
                }
                Handler(Looper.getMainLooper()).post {
                    if (!pageActive.get()) {
                        result.getOrNull()?.let { inspection ->
                            Thread(
                                { ScriptPluginManager.discardImport(context, inspection.sessionId) },
                                "Hchat-Plugin-Import-Late-Cleanup"
                            ).start()
                        }
                        return@post
                    }
                    busyText = ""
                    result.fold(
                        onSuccess = { inspection ->
                            activeImportSessionId.set(inspection.sessionId)
                            importInspection = inspection
                            importOverwriteIds = emptySet()
                        },
                        onFailure = { showScriptPluginManagerError(context, it) }
                    )
                }
            }, "Hchat-Plugin-Import-Inspect").start()
        }
    }

    PageScaffold(
        title = "本地插件管理",
        largeTitle = "本地插件管理",
        scrollBehavior = scrollBehavior,
        onBack = onBack,
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
            item {
                SearchBarSurface(
                    query = query,
                    placeholder = "搜索本地插件",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    onQueryChange = { query = it }
                )
            }
            item { SmallTitle(modifier = Modifier.padding(top = 6.dp), text = "管理") }
            item {
                SettingsCard {
                    ScriptPluginManagerCommandRow(
                        icon = NavIcons.Import,
                        title = "导入插件",
                        summary = "从 ZIP 文件导入，导入后默认关闭",
                        enabled = busyText.isEmpty(),
                        onClick = ::launchImportPicker
                    )
                    InsetDivider()
                    ScriptPluginSelectionToolbar(
                        selectedCount = selectedIds.size,
                        allVisibleSelected = allVisibleSelected,
                        enabled = visibleIds.isNotEmpty() && busyText.isEmpty(),
                        onSelectAll = {
                            selectedIds = if (allVisibleSelected) {
                                selectedIds - visibleIds
                            } else {
                                selectedIds + visibleIds
                            }
                        },
                        onInvert = {
                            selectedIds = (selectedIds - visibleIds) + (visibleIds - selectedIds)
                        }
                    )
                    if (selectedIds.isNotEmpty()) {
                        InsetDivider()
                        ScriptPluginBatchActions(
                            enabled = busyText.isEmpty(),
                            onPin = { setPinned(selectedIds, true) },
                            onUnpin = { setPinned(selectedIds, false) },
                            onExport = {
                                val selected = managedPlugins.filter { it.plugin.id in selectedIds }.map { it.plugin }
                                launchScriptPluginExport(context, selected)
                            },
                            onDelete = {
                                deletePlugins = managedPlugins
                                    .filter { it.plugin.id in selectedIds }
                                    .map { it.plugin }
                            }
                        )
                    }
                }
            }
            if (busyText.isNotEmpty()) {
                item {
                    Text(
                        text = busyText,
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
            when {
                loading -> item { EmptyText("正在加载插件") }
                managedPlugins.isEmpty() -> item { EmptyText("暂无本地插件") }
                visiblePlugins.isEmpty() -> item { EmptyText("没有匹配的插件") }
                else -> {
                    if (pinnedPlugins.isNotEmpty()) {
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "已置顶(${pinnedPlugins.size})") }
                        item {
                            SettingsCard {
                                pinnedPlugins.forEachIndexed { index, item ->
                                    key(item.plugin.id) {
                                        ScriptPluginManagerRow(
                                            modifier = Modifier,
                                            item = item,
                                            selected = item.plugin.id in selectedIds,
                                            dragEnabled = query.isBlank() && busyText.isEmpty(),
                                            onSelectedChange = { selected ->
                                                selectedIds = if (selected) {
                                                    selectedIds + item.plugin.id
                                                } else {
                                                    selectedIds - item.plugin.id
                                                }
                                            },
                                            onOpenActions = { actionPlugin = item },
                                            onDragMove = { delta -> movePlugin(item, delta) },
                                            onDragStateChange = { dragging ->
                                                if (dragging) {
                                                    draggedPluginId = item.plugin.id
                                                    dragOrderChanged = false
                                                } else if (draggedPluginId == item.plugin.id) {
                                                    draggedPluginId = null
                                                    if (dragOrderChanged) commitOrder()
                                                }
                                            },
                                            onOrderChanged = { dragOrderChanged = true }
                                        )
                                        if (index != pinnedPlugins.lastIndex) InsetDivider()
                                    }
                                }
                            }
                        }
                    }
                    if (normalPlugins.isNotEmpty()) {
                        item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "其他插件(${normalPlugins.size})") }
                        item {
                            SettingsCard {
                                normalPlugins.forEachIndexed { index, item ->
                                    key(item.plugin.id) {
                                        ScriptPluginManagerRow(
                                            modifier = Modifier,
                                            item = item,
                                            selected = item.plugin.id in selectedIds,
                                            dragEnabled = query.isBlank() && busyText.isEmpty(),
                                            onSelectedChange = { selected ->
                                                selectedIds = if (selected) {
                                                    selectedIds + item.plugin.id
                                                } else {
                                                    selectedIds - item.plugin.id
                                                }
                                            },
                                            onOpenActions = { actionPlugin = item },
                                            onDragMove = { delta -> movePlugin(item, delta) },
                                            onDragStateChange = { dragging ->
                                                if (dragging) {
                                                    draggedPluginId = item.plugin.id
                                                    dragOrderChanged = false
                                                } else if (draggedPluginId == item.plugin.id) {
                                                    draggedPluginId = null
                                                    if (dragOrderChanged) commitOrder()
                                                }
                                            },
                                            onOrderChanged = { dragOrderChanged = true }
                                        )
                                        if (index != normalPlugins.lastIndex) InsetDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionPlugin?.let { item ->
        ScriptPluginActionDialog(
            plugin = item.plugin,
            pinned = item.pinned,
            onDismiss = { actionPlugin = null },
            onPinChanged = { pinned ->
                actionPlugin = null
                setPinned(setOf(item.plugin.id), pinned)
            },
            onRename = {
                actionPlugin = null
                showAfterDialogDismiss(context) { renamePlugin = item.plugin }
            },
            onExport = {
                actionPlugin = null
                showAfterDialogDismiss(context) {
                    launchScriptPluginExport(context, listOf(item.plugin))
                }
            },
            onDelete = {
                actionPlugin = null
                showAfterDialogDismiss(context) { deletePlugins = listOf(item.plugin) }
            }
        )
    }
    renamePlugin?.let { plugin ->
        ScriptPluginRenameDialog(
            plugin = plugin,
            onDismiss = { renamePlugin = null },
            onConfirm = { name ->
                renamePlugin = null
                busyText = "正在重命名"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        ScriptPluginManager.renamePlugin(context, plugin.id, name)
                    }
                    busyText = ""
                    result.fold(
                        onSuccess = {
                            refresh()
                            Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { showScriptPluginManagerError(context, it) }
                    )
                }
            }
        )
    }
    if (deletePlugins.isNotEmpty()) {
        val pendingDelete = deletePlugins
        ScriptPluginDeleteDialog(
            pluginNames = pendingDelete.map { it.displayName ?: it.name.ifBlank { it.id } },
            onDismiss = { deletePlugins = emptyList() },
            onConfirm = {
                deletePlugins = emptyList()
                busyText = "正在删除插件"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        ScriptPluginManager.deletePlugins(context, pendingDelete.map { it.id })
                    }
                    busyText = ""
                    result.fold(
                        onSuccess = {
                            selectedIds = selectedIds - pendingDelete.mapTo(LinkedHashSet()) { it.id }
                            refresh()
                            Toast.makeText(context, "已删除 ${pendingDelete.size} 个插件", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { showScriptPluginManagerError(context, it) }
                    )
                }
            }
        )
    }
    importInspection?.let { inspection ->
        ScriptPluginImportDialog(
            inspection = inspection,
            overwriteIds = importOverwriteIds,
            applying = busyText == "正在导入插件",
            onOverwriteChanged = { pluginId, overwrite ->
                importOverwriteIds = if (overwrite) {
                    importOverwriteIds + pluginId
                } else {
                    importOverwriteIds - pluginId
                }
            },
            onDismiss = {
                activeImportSessionId.set(null)
                importInspection = null
                Thread(
                    { ScriptPluginManager.discardImport(context, inspection.sessionId) },
                    "Hchat-Plugin-Import-Discard"
                ).start()
            },
            onConfirm = {
                busyText = "正在导入插件"
                scope.launch {
                    val actions = inspection.conflicts.associateWith { pluginId ->
                        if (pluginId in importOverwriteIds) {
                            ScriptPluginManager.ImportConflictAction.OVERWRITE
                        } else {
                            ScriptPluginManager.ImportConflictAction.SKIP
                        }
                    }
                    val result = withContext(Dispatchers.IO) {
                        ScriptPluginManager.applyImport(context, inspection.sessionId, actions)
                    }
                    busyText = ""
                    result.fold(
                        onSuccess = { imported ->
                            activeImportSessionId.set(null)
                            importInspection = null
                            importOverwriteIds = emptySet()
                            refresh()
                            Toast.makeText(
                                context,
                                "已导入 ${imported.importedPluginIds.size} 个插件，保持关闭状态",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onFailure = { showScriptPluginManagerError(context, it) }
                    )
                }
            }
        )
    }
}

@Composable
internal fun ScriptPluginSelectionToolbar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    enabled: Boolean,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (selectedCount == 0) "未选择插件" else "已选择 $selectedCount 个",
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            text = if (allVisibleSelected) "取消全选" else "全选",
            enabled = enabled,
            onClick = onSelectAll,
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        TextButton(
            text = "反选",
            enabled = enabled,
            onClick = onInvert,
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}

@Composable
internal fun ScriptPluginBatchActions(
    enabled: Boolean,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScriptPluginCompactAction("置顶", enabled, Modifier.weight(1f), onPin)
            ScriptPluginCompactAction("取消置顶", enabled, Modifier.weight(1f), onUnpin)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScriptPluginCompactAction("导出", enabled, Modifier.weight(1f), onExport)
            ScriptPluginCompactAction("删除", enabled, Modifier.weight(1f), onDelete, destructive = true)
        }
    }
}

@Composable
internal fun ScriptPluginCompactAction(
    text: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val color = when {
        !enabled -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f)
        destructive -> Color(0xFFD93025)
        else -> MiuixTheme.colorScheme.primary
    }
    Box(
        modifier = modifier.height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun ScriptPluginManagerCommandRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = if (enabled) MiuixTheme.colorScheme.onSurface else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            imageVector = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(text = title, color = color, fontWeight = FontWeight.Medium)
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun ScriptPluginManagerRow(
    modifier: Modifier,
    item: ScriptPluginManager.ManagedPlugin,
    selected: Boolean,
    dragEnabled: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onOpenActions: () -> Unit,
    onDragMove: (Int) -> Boolean,
    onDragStateChange: (Boolean) -> Unit,
    onOrderChanged: () -> Unit
) {
    val plugin = item.plugin
    val density = LocalDensity.current
    var itemHeightPx by remember(plugin.id) { mutableStateOf(0f) }
    var dragDistanceY by remember(plugin.id) { mutableStateOf(0f) }
    var dragging by remember(plugin.id) { mutableStateOf(false) }
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDragStateChange by rememberUpdatedState(onDragStateChange)
    val currentOnOrderChanged by rememberUpdatedState(onOrderChanged)
    val visualDragY by animateFloatAsState(
        targetValue = dragDistanceY,
        animationSpec = tween(durationMillis = if (dragging) 0 else 160),
        label = "ScriptPluginDragOffset"
    )
    Row(
        modifier = modifier.zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = visualDragY
                shadowElevation = if (dragging) with(density) { 7.dp.toPx() } else 0f
                shape = RoundedCornerShape(8.dp)
            }
            .fillMaxWidth()
            .onSizeChanged { itemHeightPx = it.height.toFloat() }
            .clickable { onSelectedChange(!selected) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionMark(selected = selected, multiSelect = true)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plugin.displayName ?: plugin.name.ifBlank { plugin.id },
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.pinned) {
                    Image(
                        imageVector = NavIcons.Pin,
                        contentDescription = "已置顶",
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary),
                        modifier = Modifier.padding(start = 6.dp).size(16.dp)
                    )
                }
            }
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
        Image(
            imageVector = NavIcons.More,
            contentDescription = "管理插件",
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary),
            modifier = Modifier.padding(start = 8.dp).size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    onOpenActions()
                }
                .padding(5.dp)
        )
        Image(
            imageVector = NavIcons.Drag,
            contentDescription = if (dragEnabled) "长按拖动排序" else "搜索时不可排序",
            colorFilter = ColorFilter.tint(
                MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = if (dragEnabled) 1f else 0.35f)
            ),
            modifier = Modifier.padding(start = 4.dp).size(32.dp)
                .pointerInput(plugin.id, dragEnabled, itemHeightPx) {
                    if (!dragEnabled) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragDistanceY = 0f
                            dragging = true
                            currentOnDragStateChange(true)
                        },
                        onDragEnd = {
                            dragging = false
                            dragDistanceY = 0f
                            currentOnDragStateChange(false)
                        },
                        onDragCancel = {
                            dragging = false
                            dragDistanceY = 0f
                            currentOnDragStateChange(false)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragDistanceY += dragAmount.y
                            val step = itemHeightPx.takeIf { it > 0f } ?: with(density) { 62.dp.toPx() }
                            while (dragDistanceY <= -step * 0.5f) {
                                if (currentOnDragMove(-1)) {
                                    dragDistanceY += step
                                    currentOnOrderChanged()
                                } else {
                                    dragDistanceY = -step * 0.45f
                                    break
                                }
                            }
                            while (dragDistanceY >= step * 0.5f) {
                                if (currentOnDragMove(1)) {
                                    dragDistanceY -= step
                                    currentOnOrderChanged()
                                } else {
                                    dragDistanceY = step * 0.45f
                                    break
                                }
                            }
                        }
                    )
                }
                .padding(5.dp)
        )
    }
}

@Composable
internal fun ScriptPluginActionDialog(
    plugin: ScriptPluginRuntime.ScriptPlugin,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onPinChanged: (Boolean) -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    WindowDialog(
        show = true,
        title = plugin.displayName ?: plugin.name.ifBlank { plugin.id },
        onDismissRequest = onDismiss,
        content = {
            Column {
                ScriptPluginDialogActionRow(
                    icon = NavIcons.Pin,
                    text = if (pinned) "取消置顶" else "置顶",
                    onClick = { onPinChanged(!pinned) }
                )
                ScriptPluginDialogActionRow(NavIcons.Edit, "重命名", onClick = onRename)
                ScriptPluginDialogActionRow(NavIcons.Export, "导出", onClick = onExport)
                ScriptPluginDialogActionRow(NavIcons.Delete, "删除", destructive = true, onClick = onDelete)
            }
        }
    )
}

@Composable
internal fun ScriptPluginDialogActionRow(
    icon: ImageVector,
    text: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (destructive) Color(0xFFD93025) else MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (destructive) Color(0xFFD93025).copy(alpha = 0.08f)
                else MiuixTheme.colorScheme.secondaryVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            imageVector = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(22.dp)
        )
        Text(text = text, color = color, fontSize = 15.sp, modifier = Modifier.padding(start = 18.dp))
    }
}

@Composable
internal fun ScriptPluginRenameDialog(
    plugin: ScriptPluginRuntime.ScriptPlugin,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(plugin.id) {
        mutableStateOf(plugin.displayName ?: plugin.name.ifBlank { plugin.id })
    }
    WindowDialog(
        show = true,
        title = "重命名插件",
        onDismissRequest = onDismiss,
        content = {
            Column {
                Text(
                    text = "仅修改展示名称，插件目录和 ID 保持不变",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    singleLine = true,
                    textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.secondaryVariant)
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "确定",
                        onClick = { onConfirm(name.trim()) },
                        enabled = name.trim().isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

@Composable
internal fun ScriptPluginDeleteDialog(
    pluginNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    WindowDialog(
        show = true,
        title = if (pluginNames.size == 1) "删除插件" else "批量删除插件",
        onDismissRequest = onDismiss,
        content = {
            Column {
                Text(
                    text = if (pluginNames.size == 1) {
                        "确定删除“${pluginNames.first()}”吗？插件会先停止运行，删除后无法恢复。"
                    } else {
                        "确定删除已选的 ${pluginNames.size} 个插件吗？插件会先停止运行，删除后无法恢复。"
                    },
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "删除",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

@Composable
internal fun ScriptPluginImportDialog(
    inspection: ScriptPluginManager.ImportInspection,
    overwriteIds: Set<String>,
    applying: Boolean,
    onOverwriteChanged: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    WindowDialog(
        show = true,
        title = "导入插件",
        onDismissRequest = { if (!applying) onDismiss() },
        content = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                Text(
                    text = "共 ${inspection.plugins.size} 个插件。已有同 ID 插件默认跳过，可单独选择覆盖。",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 10.dp)
                ) {
                    items(inspection.plugins, key = { it.pluginId }) { plugin ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !applying && plugin.conflict) {
                                    onOverwriteChanged(plugin.pluginId, plugin.pluginId !in overwriteIds)
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plugin.name.ifBlank { plugin.pluginId },
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (plugin.conflict) {
                                        if (plugin.pluginId in overwriteIds) "覆盖已有插件" else "跳过已有插件"
                                    } else {
                                        "新增插件"
                                    },
                                    color = if (plugin.conflict && plugin.pluginId in overwriteIds) {
                                        Color(0xFFD93025)
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    },
                                    fontSize = 12.sp
                                )
                            }
                            if (plugin.conflict) {
                                SelectionMark(
                                    selected = plugin.pluginId in overwriteIds,
                                    multiSelect = true
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        enabled = !applying,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = if (applying) "正在导入" else "导入",
                        onClick = onConfirm,
                        enabled = !applying,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

internal fun launchScriptPluginExport(
    context: Context,
    plugins: List<ScriptPluginRuntime.ScriptPlugin>
) {
    if (plugins.isEmpty()) {
        Toast.makeText(context, "未选择插件", Toast.LENGTH_SHORT).show()
        return
    }
    val activity = context as? Activity
    if (activity == null) {
        Toast.makeText(context, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        return
    }
    val fileName = scriptPluginExportFileName(plugins)
    ScriptPluginDocumentBridge.launchExport(activity, fileName) { uri ->
        Thread({
            val result = runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    ScriptPluginManager.exportPlugins(context, plugins.map { it.id }, output).getOrThrow()
                } ?: error("无法写入所选文件")
            }
            if (result.isFailure) runCatching { context.contentResolver.delete(uri, null, null) }
            Handler(Looper.getMainLooper()).post {
                result.fold(
                    onSuccess = { exported ->
                        Toast.makeText(
                            context,
                            "已导出 ${exported.exportedPluginIds.size} 个插件",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { showScriptPluginManagerError(context, it) }
                )
            }
        }, "Hchat-Plugin-Export").start()
    }
}

internal fun scriptPluginExportFileName(plugins: List<ScriptPluginRuntime.ScriptPlugin>): String {
    val base = if (plugins.size == 1) {
        plugins.first().displayName ?: plugins.first().name.ifBlank { plugins.first().id }
    } else {
        "Hchat_脚本插件_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
    }
    val safe = base.removeSuffix(".zip")
        .replace(Regex("""[\\/:*?\"<>|]"""), "_")
        .trim()
        .ifBlank { "Hchat_脚本插件" }
    return "$safe.zip"
}

internal fun showAfterDialogDismiss(context: Context, action: () -> Unit) {
    val activity = context as? Activity
    if (activity != null) {
        activity.window.decorView.postOnAnimation(action)
    } else {
        Handler(Looper.getMainLooper()).post(action)
    }
}

internal fun showScriptPluginManagerError(context: Context, error: Throwable) {
    h.Hchat.utils.HLog.e("[Hchat:ScriptManager] ${error.message ?: "操作失败"}", error)
    Toast.makeText(context, error.message ?: "操作失败", Toast.LENGTH_LONG).show()
}

@Composable
internal fun PathSwitchRow(
    checked: Boolean,
    title: String,
    summary: String,
    onInfoClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(pressFeedbackColor)
                .responsiveTap(
                    onClick = onInfoClick,
                    onPressedChange = { pressed = it }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ClickHintTag()
            }
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
internal fun ScriptPluginRow(
    checked: Boolean,
    title: String,
    summary: String,
    showSettings: Boolean,
    onOpenReadme: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManager: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
                .responsiveTap(
                    onClick = onOpenReadme,
                    onPressedChange = { pressed = it }
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ClickHintTag()
            }
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        if (showSettings) {
            Image(
                imageVector = NavIcons.Settings,
                contentDescription = "插件设置",
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary),
                modifier = Modifier
                    .padding(start = 10.dp, end = 8.dp)
                    .size(22.dp)
                    .responsiveTap(onClick = onOpenSettings)
            )
        }
        Image(
            imageVector = NavIcons.More,
            contentDescription = "管理插件",
            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary),
            modifier = Modifier
                .padding(start = if (showSettings) 0.dp else 10.dp, end = 8.dp)
                .size(22.dp)
                .responsiveTap(onClick = onOpenManager)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
internal fun ScriptPluginPathDialog(
    context: Context,
    title: String,
    path: String,
    onClose: () -> Unit
) {
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onClose,
        content = {
            Column {
                Text(
                    text = path,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
                TextButton(
                    text = "复制路径",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("HchatScriptDir", path))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
                TextButton(
                    text = "关闭",
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    )
}

@Composable
fun FirstUseAgreementDialog(
    context: Context,
    onCancel: () -> Unit,
    onAccepted: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var remainingSeconds by remember { mutableStateOf(30) }
    val acceptedText = TermsGate.AGREEMENT_TEXT
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        }
    }
    val agreement = remember {
        """
        Hchat 使用协议与免责声明

        1. 本模块为免费模块，仅供个人学习、研究、测试、逆向分析和备份用途使用，不提供任何商业授权、售后承诺或稳定性保证。
        2. 本模块与微信、腾讯及其关联主体无关，不代表官方立场，也不是官方客户端、官方插件或官方服务的一部分。
        3. 禁止倒卖、付费分发、捆绑销售、二次打包收费、引流售卖、以捐赠名义收费，禁止冒充作者、官方渠道或授权代理发布。
        4. 请勿在国内公开平台、群组、论坛、短视频平台、网盘分享页、应用市场或其它公开渠道传播、推广、引流、售卖或组织分发本模块。
        5. 本模块可能会修改微信运行时行为，使用后可能出现功能异常、消息异常、账号风控、限制登录、数据异常、闪退、掉线、模块冲突或其它不可预期问题。
        6. 使用者应自行确认所在地法律法规、平台协议、设备环境和账号风险；因安装、使用、传播、修改、二次分发或与其它模块共存产生的任何后果均由使用者自行承担。
        7. 禁止将本模块用于骚扰、欺诈、刷量、营销轰炸、盗取信息、破坏服务稳定性、绕过平台风控、侵犯他人权益或其它违法违规用途。
        8. 本模块不保证适配所有微信版本、系统版本、设备环境、热更新状态和其它模块共存环境，也不承诺持续维护、及时修复或提供任何形式的服务保障。
        9. 如果你不同意以上任一条款，请点击取消并停止使用本模块。

        如果你理解并接受以上内容，请在下方输入“$acceptedText”后继续使用。
        """.trimIndent()
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val dialogMaxHeight = (screenHeight * 0.78f).coerceAtMost(640.dp)
    val agreementMaxHeight = (screenHeight * 0.42f).coerceIn(220.dp, 430.dp)
    WindowDialog(
        show = true,
        title = "使用协议",
        onDismissRequest = {},
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = agreementMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = agreement,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
                Text(
                    text = "请输入“$acceptedText”确认",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 14.dp)
                )
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MiuixTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.secondaryVariant)
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = if (remainingSeconds > 0) "继续（${remainingSeconds}s）" else "同意并继续",
                        onClick = {
                            if (remainingSeconds > 0) {
                                Toast.makeText(context, "请等待 ${remainingSeconds} 秒后继续", Toast.LENGTH_SHORT).show()
                            } else if (input.trim() == acceptedText) {
                                if (TermsGate.accept(context)) {
                                    onAccepted()
                                } else {
                                    Toast.makeText(context, "协议状态保存失败，请重试", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "请输入“$acceptedText”后继续", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}

@Composable
fun ScriptPluginReadmeDialog(
    context: Context,
    plugin: ScriptPluginRuntime.ScriptPlugin,
    onClose: () -> Unit
) {
    val readme = remember(plugin.id) {
        runCatching {
            val file = File(plugin.dir, "README.md")
            if (file.isFile) file.readText(Charsets.UTF_8) else ""
        }.getOrDefault("")
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val dialogMaxHeight = (screenHeight * 0.78f).coerceAtMost(640.dp)
    val readmeMaxHeight = (screenHeight * 0.58f).coerceIn(240.dp, 520.dp)

    WindowDialog(
        show = true,
        title = plugin.displayName ?: "未知",
        onDismissRequest = onClose,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = readmeMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (readme.isBlank()) {
                        Text(
                            text = "暂无说明",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        MarkdownUi.Content(context, readme)
                    }
                }
                TextButton(
                    text = "关闭",
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    )
}

}

internal object MarkdownUi {

@Composable
fun Content(
    context: Context,
    markdown: String,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    bodyFontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    onCopyCode: ((String) -> Unit)? = null
) {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    var inCodeBlock = false
    var inlineState = MarkdownInlineState()
    val codeLines = ArrayList<String>()
    Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.trimStart().startsWith("```")) {
                if (inCodeBlock) {
                    CodeBlock(codeLines.joinToString("\n"), onCopyCode)
                    codeLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeLines.clear()
                }
                return@forEach
            }
            if (inCodeBlock) {
                codeLines += rawLine
                return@forEach
            }
            val nextState = MarkdownLine(context, line, inlineState, bodyFontSize)
            inlineState = nextState
        }
        if (inCodeBlock && codeLines.isNotEmpty()) {
            CodeBlock(codeLines.joinToString("\n"), onCopyCode)
        }
    }
}

@Composable
internal fun MarkdownLine(
    context: Context,
    line: String,
    inlineState: MarkdownInlineState,
    bodyFontSize: androidx.compose.ui.unit.TextUnit = 13.sp
): MarkdownInlineState {
    val trimmed = line.trim()
    return when {
        trimmed.isBlank() -> {
            Box(modifier = Modifier.height(8.dp))
            inlineState
        }
        trimmed.matches(Regex("""-{3,}|_{3,}|\*{3,}""")) -> {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.outline)
            )
            inlineState
        }
        trimmed.startsWith("#") -> {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
            val text = trimmed.drop(level).trim()
            MarkdownTextResult(
                context = context,
                text = text,
                inlineState = inlineState,
                modifier = Modifier.padding(top = if (level <= 2) 10.dp else 8.dp, bottom = 4.dp),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = when (level) {
                    1 -> 22.sp
                    2 -> 19.sp
                    3 -> 17.sp
                    else -> 15.sp
                },
                fontWeight = FontWeight.SemiBold
            )
        }
        trimmed.startsWith(">") -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier.padding(end = 8.dp)
                        .size(width = 3.dp, height = 20.dp)
                        .background(MiuixTheme.colorScheme.primary)
                )
                MarkdownTextResult(
                    context = context,
                    text = trimmed.removePrefix(">").trim(),
                    inlineState = inlineState,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = bodyFontSize,
                    modifier = Modifier.weight(1f)
                )
            }
            inlineState
        }
        trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
            MarkdownBullet(context, trimmed.drop(2).trim(), inlineState, fontSize = bodyFontSize)
        }
        Regex("""^\d+[.)]\s+.*""").matches(trimmed) -> {
            val marker = trimmed.substringBefore(' ').trim()
            MarkdownBullet(context, trimmed.removePrefix(marker).trim(), inlineState, marker, bodyFontSize)
        }
        else -> {
            MarkdownTextResult(
                context = context,
                text = line,
                inlineState = inlineState,
                fontSize = bodyFontSize,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
internal fun MarkdownBullet(
    context: Context,
    text: String,
    inlineState: MarkdownInlineState,
    marker: String = "•",
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp
): MarkdownInlineState {
    var nextState = inlineState
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = marker,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = fontSize,
            modifier = Modifier.padding(end = 8.dp)
        )
        nextState = MarkdownTextResult(
            context = context,
            text = text,
            inlineState = inlineState,
            fontSize = fontSize,
            modifier = Modifier.weight(1f)
        )
    }
    return nextState
}

@Composable
fun CodeBlock(code: String, onCopy: ((String) -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
    ) {
        if (onCopy != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "代码",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Image(
                    imageVector = NavIcons.Copy,
                    contentDescription = "复制代码",
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary),
                    modifier = Modifier.size(26.dp).clip(RoundedCornerShape(4.dp))
                        .clickable { onCopy(code) }
                        .padding(6.dp)
                )
            }
        }
        Text(
            text = code,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun Text(
    context: Context,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onSurface,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    fontWeight: FontWeight? = null
) {
    MarkdownTextResult(context, text, MarkdownInlineState(), modifier, color, fontSize, fontWeight)
}

@Composable
internal fun MarkdownTextResult(
    context: Context,
    text: String,
    inlineState: MarkdownInlineState,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onSurface,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    fontWeight: FontWeight? = null
): MarkdownInlineState {
    val primary = MiuixTheme.colorScheme.primary
    val result = remember(text, primary, inlineState.bold) {
        buildMarkdownAnnotatedString(text, primary, inlineState)
    }
    ClickableText(
        text = result.text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight),
        onClick = { offset ->
            result.text.getStringAnnotations(MARKDOWN_LINK_TAG, offset, offset)
                .firstOrNull()
                ?.let { annotation -> openMarkdownLink(context, annotation.item) }
        }
    )
    return result.state
}

internal data class MarkdownInlineState(val bold: Boolean = false)

internal data class MarkdownInlineResult(
    val text: AnnotatedString,
    val state: MarkdownInlineState
)

internal fun buildMarkdownAnnotatedString(
    text: String,
    accent: Color,
    initialState: MarkdownInlineState
): MarkdownInlineResult {
    var state = initialState
    val annotated = buildAnnotatedString {
        state = appendInlineMarkdown(text, accent, state)
    }
    return MarkdownInlineResult(annotated, state)
}

internal fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    accent: Color,
    initialState: MarkdownInlineState
): MarkdownInlineState {
    var state = initialState
    var segmentStart = 0
    MARKDOWN_LINK_REGEX.findAll(text).forEach { match ->
        if (match.range.first > segmentStart) {
            state = appendInlineMarkdownSegment(
                text.substring(segmentStart, match.range.first),
                accent,
                state
            )
        }
        val label = match.groupValues[1]
        val url = match.groupValues[2].trim()
        if (label.isNotBlank() && url.isNotBlank()) {
            pushStringAnnotation(MARKDOWN_LINK_TAG, url)
            appendStyledInline(label, accent, state, link = true)
            pop()
        } else {
            append(match.value)
        }
        segmentStart = match.range.last + 1
    }
    if (segmentStart < text.length) {
        state = appendInlineMarkdownSegment(text.substring(segmentStart), accent, state)
    }
    return state
}

internal fun AnnotatedString.Builder.appendInlineMarkdownSegment(
    text: String,
    accent: Color,
    initialState: MarkdownInlineState
): MarkdownInlineState {
    var state = initialState
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                state = state.copy(bold = !state.bold)
                index += 2
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    withStyle(SpanStyle(color = accent, fontFamily = FontFamily.Monospace)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }
            else -> {
                appendStyledInline(text[index].toString(), accent, state, link = false)
                index++
            }
        }
    }
    return state
}

internal fun AnnotatedString.Builder.appendStyledInline(
    value: String,
    accent: Color,
    state: MarkdownInlineState,
    link: Boolean
) {
    val style = SpanStyle(
        color = if (link) accent else Color.Unspecified,
        fontWeight = when {
            link -> FontWeight.Medium
            state.bold -> FontWeight.SemiBold
            else -> null
        }
    )
    withStyle(style) {
        append(value)
    }
}

internal fun openMarkdownLink(context: Context, url: String) {
    val value = url.trim()
    if (value.isBlank()) return
    runCatching {
        val normalized = if (value.contains("://")) value else "https://$value"
        val uri = Uri.parse(normalized)
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

}

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
