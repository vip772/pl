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
internal fun GroupNicknameColorMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, GroupNicknameColorSettings.PREFS_NAME) }
    var enabled by remember {
        mutableStateOf(
            sp.getBoolean(GroupNicknameColorSettings.KEY_ENABLE, GroupNicknameColorSettings.DEFAULT_ENABLE)
        )
    }
    var color by remember {
        mutableStateOf(
            sp.getString(GroupNicknameColorSettings.KEY_COLOR, GroupNicknameColorSettings.DEFAULT_COLOR)
                ?: GroupNicknameColorSettings.DEFAULT_COLOR
        )
    }
    var weight by remember {
        mutableStateOf(
            sp.getInt(GroupNicknameColorSettings.KEY_WEIGHT, GroupNicknameColorSettings.DEFAULT_WEIGHT).toString()
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
                    sp.edit()
                        .putString(
                            GroupNicknameColorSettings.KEY_COLOR,
                            GroupNicknameColorStore.cleanColorSpec(color)
                        )
                        .putInt(
                            GroupNicknameColorSettings.KEY_WEIGHT,
                            GroupNicknameColorStore.cleanWeight(
                                weight.toIntOrNull() ?: GroupNicknameColorSettings.DEFAULT_WEIGHT
                            )
                        )
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
            item { SmallTitle(text = "群昵称") }
            item {
                SettingsCard {
                    SwitchRow(enabled, "群昵称自定义颜色", "修改群聊成员昵称的颜色和粗细") {
                        enabled = it
                        sp.edit().putBoolean(GroupNicknameColorSettings.KEY_ENABLE, it).apply()
                    }
                    if (enabled) {
                        InsetDivider()
                        ColorPickerRow(
                            "昵称颜色",
                            "留空跟随微信，渐变用 #F59E0B,#22C55E",
                            color,
                            onReset = { color = GroupNicknameColorSettings.DEFAULT_COLOR }
                        ) { color = it.take(19) }
                        InsetDivider()
                        WeightInputRow("昵称粗细", weight) { weight = it }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RealNameTailMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, RealNameTailSettings.PREFS_NAME) }
    var prefix by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GLOBAL_PREFIX, "") ?: "") }
    var maleText by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GENDER_MALE_TEXT, RealNameTailSettings.DEFAULT_GENDER_MALE_TEXT) ?: RealNameTailSettings.DEFAULT_GENDER_MALE_TEXT) }
    var femaleText by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GENDER_FEMALE_TEXT, RealNameTailSettings.DEFAULT_GENDER_FEMALE_TEXT) ?: RealNameTailSettings.DEFAULT_GENDER_FEMALE_TEXT) }
    var unknownGenderText by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GENDER_UNKNOWN_TEXT, RealNameTailSettings.DEFAULT_GENDER_UNKNOWN_TEXT) ?: RealNameTailSettings.DEFAULT_GENDER_UNKNOWN_TEXT) }
    var tailColor by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_TAIL_COLOR, RealNameTailSettings.DEFAULT_TAIL_COLOR) ?: RealNameTailSettings.DEFAULT_TAIL_COLOR) }
    var bracketColor by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_BRACKET_COLOR, RealNameTailSettings.DEFAULT_BRACKET_COLOR) ?: RealNameTailSettings.DEFAULT_BRACKET_COLOR) }
    var genderColor by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_GENDER_COLOR, RealNameTailSettings.DEFAULT_GENDER_COLOR) ?: RealNameTailSettings.DEFAULT_GENDER_COLOR) }
    var regionColor by remember { mutableStateOf(sp.getString(RealNameTailSettings.KEY_REGION_COLOR, RealNameTailSettings.DEFAULT_REGION_COLOR) ?: RealNameTailSettings.DEFAULT_REGION_COLOR) }
    var tailWeight by remember { mutableStateOf(sp.getInt(RealNameTailSettings.KEY_TAIL_WEIGHT, RealNameTailSettings.DEFAULT_TEXT_WEIGHT).toString()) }
    var bracketWeight by remember { mutableStateOf(sp.getInt(RealNameTailSettings.KEY_BRACKET_WEIGHT, RealNameTailSettings.DEFAULT_TEXT_WEIGHT).toString()) }
    var genderWeight by remember { mutableStateOf(sp.getInt(RealNameTailSettings.KEY_GENDER_WEIGHT, RealNameTailSettings.DEFAULT_TEXT_WEIGHT).toString()) }
    var regionWeight by remember { mutableStateOf(sp.getInt(RealNameTailSettings.KEY_REGION_WEIGHT, RealNameTailSettings.DEFAULT_TEXT_WEIGHT).toString()) }
    var prefixEnabled by remember { mutableStateOf(sp.getBoolean(RealNameTailSettings.KEY_GLOBAL_PREFIX_ENABLE, false)) }
    var showGender by remember { mutableStateOf(sp.getBoolean(RealNameTailSettings.KEY_SHOW_GENDER, RealNameTailSettings.DEFAULT_SHOW_GENDER)) }
    var showRegion by remember { mutableStateOf(sp.getBoolean(RealNameTailSettings.KEY_SHOW_REGION, RealNameTailSettings.DEFAULT_SHOW_REGION)) }
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
                        .putString(RealNameTailSettings.KEY_GLOBAL_PREFIX, prefix.trim().take(8))
                        .putString(RealNameTailSettings.KEY_GENDER_MALE_TEXT, maleText.trim().take(12))
                        .putString(RealNameTailSettings.KEY_GENDER_FEMALE_TEXT, femaleText.trim().take(12))
                        .putString(RealNameTailSettings.KEY_GENDER_UNKNOWN_TEXT, unknownGenderText.trim().take(12))
                        .putString(RealNameTailSettings.KEY_TAIL_COLOR, RealNameTailStore.cleanColorSpec(tailColor))
                        .putString(RealNameTailSettings.KEY_BRACKET_COLOR, RealNameTailStore.cleanColorSpec(bracketColor))
                        .putString(RealNameTailSettings.KEY_GENDER_COLOR, RealNameTailStore.cleanColorSpec(genderColor))
                        .putString(RealNameTailSettings.KEY_REGION_COLOR, RealNameTailStore.cleanColorSpec(regionColor))
                        .putInt(RealNameTailSettings.KEY_TAIL_WEIGHT, RealNameTailStore.cleanWeight(tailWeight.toIntOrNull() ?: RealNameTailSettings.DEFAULT_TEXT_WEIGHT))
                        .putInt(RealNameTailSettings.KEY_BRACKET_WEIGHT, RealNameTailStore.cleanWeight(bracketWeight.toIntOrNull() ?: RealNameTailSettings.DEFAULT_TEXT_WEIGHT))
                        .putInt(RealNameTailSettings.KEY_GENDER_WEIGHT, RealNameTailStore.cleanWeight(genderWeight.toIntOrNull() ?: RealNameTailSettings.DEFAULT_TEXT_WEIGHT))
                        .putInt(RealNameTailSettings.KEY_REGION_WEIGHT, RealNameTailStore.cleanWeight(regionWeight.toIntOrNull() ?: RealNameTailSettings.DEFAULT_TEXT_WEIGHT))
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
                    SwitchRow(sp, RealNameTailSettings.KEY_ENABLE, "实名尾字", "在群聊昵称后显示已查询到的实名尾字", RealNameTailSettings.DEFAULT_ENABLE)
                    InsetDivider()
                    SwitchRow(sp, RealNameTailSettings.KEY_MESSAGE_QUERY, "消息触发查询", "收到群成员消息后自动补查", RealNameTailSettings.DEFAULT_MESSAGE_QUERY)
                    InsetDivider()
                    SwitchRow(sp, RealNameTailSettings.KEY_VISIBLE_QUERY, "可见成员查询", "聊天页出现未缓存成员时自动补查", RealNameTailSettings.DEFAULT_VISIBLE_QUERY)
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "显示") }
            item {
                SettingsCard {
                    SwitchRow(prefixEnabled, "实名前缀", "用统一前缀替换实名尾字前半部分") {
                        prefixEnabled = it
                        sp.edit().putBoolean(RealNameTailSettings.KEY_GLOBAL_PREFIX_ENABLE, it).apply()
                    }
                    if (prefixEnabled) {
                        InsetDivider()
                        InputRow("前缀内容", "最多 8 个字符，例如 *", prefix) { prefix = it.take(8) }
                    }
                    InsetDivider()
                    SwitchRow(showGender, "显示性别", "在实名尾字后追加联系人性别") {
                        showGender = it
                        sp.edit().putBoolean(RealNameTailSettings.KEY_SHOW_GENDER, it).apply()
                    }
                    if (showGender) {
                        InsetDivider()
                        InputRow("男性文案", "最多 12 个字符，默认 男", maleText) { maleText = it.take(12) }
                        InsetDivider()
                        InputRow("女性文案", "最多 12 个字符，默认 女", femaleText) { femaleText = it.take(12) }
                        InsetDivider()
                        InputRow("未知文案", "最多 12 个字符，留空则不显示", unknownGenderText) { unknownGenderText = it.take(12) }
                    }
                    InsetDivider()
                    SwitchRow(showRegion, "显示地区", "在实名尾字后追加联系人地区") {
                        showRegion = it
                        sp.edit().putBoolean(RealNameTailSettings.KEY_SHOW_REGION, it).apply()
                    }
                    InsetDivider()
                    ColorPickerRow("尾字颜色", "留空跟随昵称，渐变用 #F59E0B,#22C55E", tailColor, onReset = { tailColor = RealNameTailSettings.DEFAULT_TAIL_COLOR }) { tailColor = it.take(19) }
                    InsetDivider()
                    WeightInputRow("尾字粗细", tailWeight) { tailWeight = it }
                    InsetDivider()
                    ColorPickerRow("括号颜色", "留空跟随昵称，渐变用 #F59E0B,#22C55E", bracketColor, onReset = { bracketColor = RealNameTailSettings.DEFAULT_BRACKET_COLOR }) { bracketColor = it.take(19) }
                    InsetDivider()
                    WeightInputRow("括号粗细", bracketWeight) { bracketWeight = it }
                    if (showGender) {
                        InsetDivider()
                        ColorPickerRow("性别颜色", "留空跟随昵称，支持渐变", genderColor, onReset = { genderColor = RealNameTailSettings.DEFAULT_GENDER_COLOR }) { genderColor = it.take(19) }
                        InsetDivider()
                        WeightInputRow("性别粗细", genderWeight) { genderWeight = it }
                    }
                    if (showRegion) {
                        InsetDivider()
                        ColorPickerRow("地区颜色", "留空跟随昵称，支持渐变", regionColor, onReset = { regionColor = RealNameTailSettings.DEFAULT_REGION_COLOR }) { regionColor = it.take(19) }
                        InsetDivider()
                        WeightInputRow("地区粗细", regionWeight) { regionWeight = it }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageBlockContactPickerPage(
    context: Context,
    request: MessageBlockContactPickerRequest,
    onBack: () -> Unit,
    onConfirm: (List<MessageBlockContactOption>) -> Unit
) {
    var loading by remember(request) { mutableStateOf(true) }
    var error by remember(request) { mutableStateOf("") }
    var data by remember(request) { mutableStateOf(MessageBlockContactData()) }
    var query by remember(request) { mutableStateOf("") }
    val initialSelectedIds = remember(request) { parseIds(request.existingValue) }
    var selectedIds by remember(request) { mutableStateOf(initialSelectedIds) }
    var contactFilter by remember(request) { mutableStateOf(MessageBlockContactFilter.ALL) }
    var selectedLabel by remember(request) { mutableStateOf<MessageBlockLabelOption?>(null) }
    var selectedConversationGroupId by remember(request) { mutableStateOf("") }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(request) {
        loadMessageBlockContacts(context) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取联系人失败"
            } else {
                data = result
            }
        }
    }
    LaunchedEffect(contactFilter) {
        if (contactFilter != MessageBlockContactFilter.LABELS) {
            selectedLabel = null
        }
        if (!request.allowOfficialAccounts && contactFilter == MessageBlockContactFilter.OFFICIALS) {
            contactFilter = MessageBlockContactFilter.ALL
        }
    }

    val currentLabel = selectedLabel
    val pageTitle = currentLabel?.let { "标签：${it.name}" } ?: request.title
    val availableContacts = data.contacts.filter {
        request.allowOfficialAccounts || it.kind != MessageBlockContactKind.OFFICIAL
    }
    val conversationGroupFilters = remember(availableContacts) {
        ConversationGroupPickerSupport.filters(context, availableContacts.map { it.contact.id })
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
    val scopedContacts = remember(availableContacts, selectedConversationIds) {
        selectedConversationIds?.let { ids ->
            availableContacts.filter { it.contact.id in ids }
        } ?: availableContacts
    }
    val availableContactIds = scopedContacts.map { it.contact.id }.toSet()
    val bottomLower = query.trim().lowercase(Locale.US)
    val bottomVisibleContacts = scopedContacts.filter { row ->
        val contact = row.contact
        val typeMatched = when (contactFilter) {
            MessageBlockContactFilter.FRIENDS -> row.kind == MessageBlockContactKind.FRIEND
            MessageBlockContactFilter.GROUPS -> row.kind == MessageBlockContactKind.GROUP
            MessageBlockContactFilter.OFFICIALS -> row.kind == MessageBlockContactKind.OFFICIAL
            MessageBlockContactFilter.LABELS -> currentLabel?.contactIds?.contains(contact.id) == true
            MessageBlockContactFilter.ALL -> true
        }
        typeMatched && (
            contact.matchesSearch(bottomLower) ||
                row.labelNames.any { it.lowercase(Locale.US).contains(bottomLower) }
        )
    }
    val bottomShowingLabelList = contactFilter == MessageBlockContactFilter.LABELS && currentLabel == null
    val bottomVisibleContactIds = bottomVisibleContacts.map { it.contact.id }.toSet()
    val bottomVisibleAllSelected = bottomVisibleContactIds.isNotEmpty() && bottomVisibleContactIds.all { selectedIds.contains(it) }
    PageScaffold(
        title = pageTitle,
        largeTitle = pageTitle,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存选择",
                onPrimaryClick = {
                    val picked = availableContacts.filter { selectedIds.contains(it.contact.id) }
                    onConfirm(picked)
                },
                secondaryText = if (currentLabel == null) "返回" else "返回标签",
                onSecondaryClick = {
                    if (currentLabel == null) {
                        onBack()
                    } else {
                        selectedLabel = null
                        query = ""
                    }
                },
                middleText = if (!bottomShowingLabelList && bottomVisibleContactIds.isNotEmpty()) {
                    if (bottomVisibleAllSelected) "取消全选" else "全选"
                } else {
                    null
                },
                onMiddleClick = if (!bottomShowingLabelList && bottomVisibleContactIds.isNotEmpty()) {
                    {
                        selectedIds = if (bottomVisibleAllSelected) {
                            selectedIds - bottomVisibleContactIds
                        } else {
                            selectedIds + bottomVisibleContactIds
                        }
                    }
                } else {
                    null
                }
            )
        }
    ) { padding ->
        val listRoute = if (contactFilter == MessageBlockContactFilter.LABELS && currentLabel == null) {
            MessageBlockContactPickerRoute.Labels
        } else {
            MessageBlockContactPickerRoute.Contacts(contactFilter, currentLabel)
        }
        SettingsRouteTransition(
            targetState = listRoute,
            label = "MessageBlockContactPickerTransition",
            depthOf = { it.depth() },
            horizontalOnSameDepth = false
        ) { route ->
            val lower = query.trim().lowercase(Locale.US)
            val showingLabelList = route is MessageBlockContactPickerRoute.Labels
            val routeFilter = (route as? MessageBlockContactPickerRoute.Contacts)?.filter
                ?: MessageBlockContactFilter.LABELS
            val routeLabel = (route as? MessageBlockContactPickerRoute.Contacts)?.label
            val visibleLabels = data.labels.filter { label ->
                label.contactIds.any { availableContactIds.contains(it) } && (
                    lower.isEmpty() ||
                        label.name.lowercase(Locale.US).contains(lower) ||
                        label.id.lowercase(Locale.US).contains(lower)
                    )
            }.selectedFirst { label -> label.contactIds.any { initialSelectedIds.contains(it) } }
            val visibleContacts = scopedContacts.filter { row ->
                val contact = row.contact
                val typeMatched = when (routeFilter) {
                    MessageBlockContactFilter.FRIENDS -> row.kind == MessageBlockContactKind.FRIEND
                    MessageBlockContactFilter.GROUPS -> row.kind == MessageBlockContactKind.GROUP
                    MessageBlockContactFilter.OFFICIALS -> row.kind == MessageBlockContactKind.OFFICIAL
                    MessageBlockContactFilter.LABELS -> routeLabel?.contactIds?.contains(contact.id) == true
                    MessageBlockContactFilter.ALL -> true
                }
                typeMatched && (
                    contact.matchesSearch(lower) ||
                        row.labelNames.any { it.lowercase(Locale.US).contains(lower) }
                )
            }.selectedFirst { initialSelectedIds.contains(it.contact.id) }
            val visibleContactIds = visibleContacts.map { it.contact.id }.toSet()
            val sectionTitle = when {
                showingLabelList -> "标签"
                routeFilter == MessageBlockContactFilter.LABELS -> "标签成员"
                else -> routeFilter.title
            }
            val sectionCount = if (showingLabelList) visibleLabels.size else visibleContactIds.size
            val routeListState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                state = routeListState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 84.dp
                )
            ) {
                item {
                    SettingsCard {
                        MessageBlockContactFilterRow(
                            selected = contactFilter,
                            enableOfficials = request.allowOfficialAccounts,
                            onSelected = {
                                contactFilter = it
                                if (it == MessageBlockContactFilter.LABELS) {
                                    selectedLabel = null
                                }
                                query = ""
                            }
                        )
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
                        InputRow(
                            title = "搜索",
                            summary = if (showingLabelList) "标签名称" else "昵称 / 群聊备注 / wxid",
                            value = query,
                            onValueChange = { query = it }
                        )
                    }
                }
                item {
                    PickerSectionHeader(
                        text = if (sectionCount > 0) "$sectionTitle · $sectionCount 项" else sectionTitle
                    )
                }
                when {
                    loading -> item { SettingsCard { EmptyText("正在载入列表...") } }
                    error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                    showingLabelList -> {
                        if (visibleLabels.isEmpty()) {
                            item { SettingsCard { EmptyText("没有匹配标签") } }
                        } else {
                            visibleLabels.forEach { label ->
                                item {
                                    MessageBlockLabelListCard(
                                        label = label,
                                        onClick = {
                                            selectedLabel = label
                                            query = ""
                                        }
                                    )
                                }
                            }
                        }
                    }
                    visibleContacts.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                    else -> visibleContacts.forEach { row ->
                        item {
                            val option = row.contact
                            MessageBlockContactListCard(
                                row = row,
                                selected = selectedIds.contains(option.id),
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
}

@Composable
internal fun ContactPickerPage(
    context: Context,
    request: ContactPickerRequest,
    onBack: () -> Unit,
    onConfirm: (List<ContactOption>) -> Unit
) {
    var loading by remember(request) { mutableStateOf(true) }
    var error by remember(request) { mutableStateOf("") }
    var contacts by remember(request) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember(request) { mutableStateOf("") }
    val initialSelectedIds = remember(request) { parseIds(request.existingValue) }
    var selectedIds by remember(request) { mutableStateOf(initialSelectedIds) }
    val scopeSelectionEnabled = request.enableScopeSelection &&
        request.multiSelect &&
        request.mode == ContactPickerMode.FRIENDS
    var scopeTab by remember(request) { mutableStateOf(ContactPickerScopeTab.FRIENDS) }
    var selectedConversationGroupId by remember(request) { mutableStateOf("") }
    var contactFilter by remember(request) {
        mutableStateOf(
            if (request.mode == ContactPickerMode.FRIENDS && request.enableLabels) {
                ContactPickerFilter.FRIENDS
            } else {
                ContactPickerFilter.ALL
            }
        )
    }
    var selectedLabel by remember(request) { mutableStateOf("") }
    val groupLabels = remember(request) {
        if (request.multiSelect && request.enableGroupLabels && request.mode.supportsGroups()) {
            GroupChatLabelStore.load(context)
        } else {
            emptyList()
        }
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(request) {
        loadContacts(context, request.mode, request.enableLabels) { result, throwable ->
            loading = false
            if (throwable != null) {
                error = throwable.message ?: "读取联系人失败"
            } else {
                contacts = result
            }
        }
    }
    val conversationGroupFilters = remember(contacts) {
        ConversationGroupPickerSupport.filters(context, contacts.map { it.id })
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
    val scopedContacts = remember(contacts, selectedConversationIds) {
        selectedConversationIds?.let { ids -> contacts.filter { it.id in ids } } ?: contacts
    }
    val labels = remember(scopedContacts) {
        scopedContacts.flatMap { it.labels }
            .distinct()
            .sortedWith(compareBy { it.lowercase(Locale.US) })
    }
    val contactLabelScopes = remember(contacts) {
        contacts.asSequence()
            .filter { !it.group && !it.official }
            .flatMap { contact -> contact.labels.asSequence().map { it to contact.id } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .map { (name, ids) -> ContactPickerScope(name, ids.toSet()) }
            .sortedBy { it.name.lowercase(Locale.US) }
    }
    val availableGroupIds = remember(scopedContacts) {
        scopedContacts.filter { it.group }.map { it.id }.toSet()
    }
    val selectableGroupLabels = remember(groupLabels, availableGroupIds) {
        groupLabels.mapNotNull { label ->
            val ids = label.groupIds.intersect(availableGroupIds)
            label.copy(groupIds = ids).takeIf { ids.isNotEmpty() }
        }
    }
    val showGroupLabels = request.mode == ContactPickerMode.GROUPS ||
        contactFilter == ContactPickerFilter.GROUPS
    LaunchedEffect(contactFilter, labels) {
        if (contactFilter == ContactPickerFilter.LABELS) {
            if (selectedLabel.isBlank() || !labels.contains(selectedLabel)) {
                selectedLabel = labels.firstOrNull().orEmpty()
            }
        } else if (selectedLabel.isNotBlank()) {
            selectedLabel = ""
        }
    }
    val lower = query.trim().lowercase(Locale.US)
    val visibleContacts = remember(
        scopedContacts,
        contactFilter,
        selectedLabel,
        request.enableLabels,
        lower,
        initialSelectedIds
    ) {
        scopedContacts.asSequence().filter {
            when (contactFilter) {
                ContactPickerFilter.FRIENDS -> !it.group && !it.official
                ContactPickerFilter.GROUPS -> it.group
                ContactPickerFilter.OFFICIALS -> it.official
                ContactPickerFilter.LABELS -> request.enableLabels &&
                    selectedLabel.isNotBlank() &&
                    !it.group &&
                    !it.official &&
                    it.labels.contains(selectedLabel)
                ContactPickerFilter.ALL -> true
            }
        }.filter { it.matchesSearch(lower) }
            .toList()
            .selectedFirst { initialSelectedIds.contains(it.id) }
    }
    val visibleLabelScopes = remember(contactLabelScopes, lower, initialSelectedIds) {
        contactLabelScopes.filter {
            lower.isEmpty() || it.name.lowercase(Locale.US).contains(lower)
        }.selectedFirst { scope -> scope.memberIds.any(initialSelectedIds::contains) }
    }
    val visibleConversationGroupScopes = remember(conversationGroupFilters, lower, initialSelectedIds) {
        conversationGroupFilters.filter {
            lower.isEmpty() || it.name.lowercase(Locale.US).contains(lower)
        }.selectedFirst { scope -> scope.conversationIds.any(initialSelectedIds::contains) }
    }
    val visibleContactIds = remember(visibleContacts) {
        visibleContacts.mapTo(linkedSetOf()) { it.id }
    }
    val bottomVisibleIds = remember(
        scopeSelectionEnabled,
        scopeTab,
        visibleContactIds,
        visibleLabelScopes,
        visibleConversationGroupScopes
    ) {
        when {
            !scopeSelectionEnabled || scopeTab == ContactPickerScopeTab.FRIENDS -> visibleContactIds
            scopeTab == ContactPickerScopeTab.LABELS -> {
                visibleLabelScopes.flatMapTo(linkedSetOf()) { it.memberIds }
            }
            else -> {
                visibleConversationGroupScopes.flatMapTo(linkedSetOf()) { it.conversationIds }
            }
        }
    }
    val bottomVisibleAllSelected = bottomVisibleIds.isNotEmpty() && bottomVisibleIds.all { selectedIds.contains(it) }

    PageScaffold(
        title = request.title,
        largeTitle = request.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = if (request.multiSelect) "保存选择" else request.singleConfirmText,
                onPrimaryClick = {
                    val picked = contacts.filter { selectedIds.contains(it.id) }
                    if (picked.isEmpty() && !request.multiSelect) {
                        Toast.makeText(context, "请选择联系人", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(picked)
                    }
                },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (request.multiSelect && bottomVisibleIds.isNotEmpty()) {
                    if (bottomVisibleAllSelected) "取消全选" else "全选"
                } else {
                    null
                },
                onMiddleClick = if (request.multiSelect && bottomVisibleIds.isNotEmpty()) {
                    {
                        selectedIds = if (bottomVisibleAllSelected) {
                            selectedIds - bottomVisibleIds
                        } else {
                            selectedIds + bottomVisibleIds
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
            if (scopeSelectionEnabled) {
                item {
                    SettingsCard {
                        ContactPickerScopeFilterRow(
                            selected = scopeTab,
                            onSelected = {
                                scopeTab = it
                                contactFilter = ContactPickerFilter.FRIENDS
                                query = ""
                            }
                        )
                    }
                }
            } else if (request.mode == ContactPickerMode.FRIENDS && request.enableLabels) {
                item {
                    SettingsCard {
                        FriendContactFilterRow(
                            selected = contactFilter,
                            onSelected = {
                                contactFilter = it
                                query = ""
                            }
                        )
                    }
                }
            } else if (request.mode == ContactPickerMode.BOTH || request.mode == ContactPickerMode.ALL_CHATS) {
                item {
                    SettingsCard {
                        ContactFilterRow(
                            selected = contactFilter,
                            enableLabels = request.enableLabels,
                            enableOfficials = request.mode == ContactPickerMode.ALL_CHATS,
                            onSelected = {
                                contactFilter = it
                                query = ""
                            }
                        )
                    }
                }
            }
            if (!scopeSelectionEnabled && request.enableLabels && contactFilter == ContactPickerFilter.LABELS) {
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
            if (!scopeSelectionEnabled && conversationGroupChoices.size > 1) {
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
            if (showGroupLabels && selectableGroupLabels.isNotEmpty()) {
                item { PickerSectionHeader(text = "群聊标签") }
                item {
                    SettingsCard {
                        selectableGroupLabels.forEachIndexed { index, label ->
                            val selectedCount = label.groupIds.count { selectedIds.contains(it) }
                            GroupChatLabelPickerRow(
                                label = label,
                                selectedCount = selectedCount,
                                onClick = {
                                    selectedIds = if (selectedCount == label.groupIds.size) {
                                        selectedIds - label.groupIds
                                    } else {
                                        selectedIds + label.groupIds
                                    }
                                }
                            )
                            if (index < selectableGroupLabels.lastIndex) InsetDivider()
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    InputRow(
                        title = "搜索",
                        summary = when {
                            scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.LABELS -> "好友标签名称"
                            scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.CONVERSATION_GROUPS -> "聊天分组名称"
                            else -> "昵称 / 群聊备注 / wxid"
                        },
                        value = query,
                        onValueChange = { query = it }
                    )
                }
            }
            item {
                PickerSectionHeader(
                    text = when {
                        scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.LABELS -> {
                            if (visibleLabelScopes.isEmpty()) "好友标签" else "好友标签 · ${visibleLabelScopes.size} 项"
                        }
                        scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.CONVERSATION_GROUPS -> {
                            if (visibleConversationGroupScopes.isEmpty()) "聊天分组" else "聊天分组 · ${visibleConversationGroupScopes.size} 项"
                        }
                        visibleContacts.isNotEmpty() -> "${contactFilter.title} · ${visibleContacts.size} 项"
                        else -> contactFilter.title
                    }
                )
            }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入列表...") } }
                error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.LABELS -> {
                    if (visibleLabelScopes.isEmpty()) {
                        item { SettingsCard { EmptyText("没有匹配的好友标签") } }
                    } else {
                        item {
                            SettingsCard {
                                visibleLabelScopes.forEachIndexed { index, scope ->
                                    val selectedCount = scope.memberIds.count(selectedIds::contains)
                                    ContactPickerScopeRow(
                                        title = scope.name,
                                        memberCount = scope.memberIds.size,
                                        selectedCount = selectedCount,
                                        memberUnit = "位好友",
                                        onClick = {
                                            selectedIds = if (selectedCount == scope.memberIds.size) {
                                                selectedIds - scope.memberIds
                                            } else {
                                                selectedIds + scope.memberIds
                                            }
                                        }
                                    )
                                    if (index < visibleLabelScopes.lastIndex) InsetDivider()
                                }
                            }
                        }
                    }
                }
                scopeSelectionEnabled && scopeTab == ContactPickerScopeTab.CONVERSATION_GROUPS -> {
                    if (visibleConversationGroupScopes.isEmpty()) {
                        item { SettingsCard { EmptyText("没有匹配的聊天分组") } }
                    } else {
                        item {
                            SettingsCard {
                                visibleConversationGroupScopes.forEachIndexed { index, scope ->
                                    val selectedCount = scope.conversationIds.count(selectedIds::contains)
                                    ContactPickerScopeRow(
                                        title = scope.name,
                                        memberCount = scope.conversationIds.size,
                                        selectedCount = selectedCount,
                                        memberUnit = "位好友",
                                        onClick = {
                                            selectedIds = if (selectedCount == scope.conversationIds.size) {
                                                selectedIds - scope.conversationIds
                                            } else {
                                                selectedIds + scope.conversationIds
                                            }
                                        }
                                    )
                                    if (index < visibleConversationGroupScopes.lastIndex) InsetDivider()
                                }
                            }
                        }
                    }
                }
                visibleContacts.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                else -> items(items = visibleContacts, key = { it.id }) { option ->
                    ContactListCard(
                        option = option,
                        selected = selectedIds.contains(option.id),
                        multiSelect = request.multiSelect,
                        onClick = {
                            selectedIds = if (request.multiSelect) {
                                if (selectedIds.contains(option.id)) {
                                    selectedIds - option.id
                                } else {
                                    selectedIds + option.id
                                }
                            } else {
                                setOf(option.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FavoritePickerPage(
    request: FavoritePickerRequest,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val favoriteApi = WeChatApis.media()?.favorites()
    val initialCache = remember(request) { favoriteApi?.cachedList() }
    var loading by remember(request) { mutableStateOf(initialCache?.items.isNullOrEmpty()) }
    var error by remember(request) { mutableStateOf("") }
    var favorites by remember(request) { mutableStateOf(initialCache?.items.orEmpty()) }
    var backgroundLoading by remember(request) { mutableStateOf(false) }
    var query by remember(request) { mutableStateOf("") }
    var filter by remember(request) { mutableStateOf(FavoritePickerFilter.ALL) }
    var preview by remember(request) { mutableStateOf<FavoriteMediaPreview?>(null) }
    val initialSelectedIds = remember(request) { parseFavoriteIds(request.existingValue) }
    var selectedIds by remember(request) { mutableStateOf(initialSelectedIds) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(request) {
        loading = favorites.isEmpty()
        error = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val api = favoriteApi
                    ?: throw IllegalStateException("收藏 API 未就绪")
                if (!api.canList()) {
                    throw IllegalStateException("收藏列表不可用")
                }
                var page = h.Hchat.hooks.api.media.WeChatFavoriteApi.FavoritePage(emptyList(), false)
                for (attempt in 0..2) {
                    page = api.openCachedList()
                    if (page.items.isNotEmpty() || attempt == 2) break
                    delay(350L)
                }
                page
            }
        }
        loading = false
        result.onSuccess {
            favorites = it.items
            if (it.hasMore) {
                backgroundLoading = true
                runCatching {
                    while (true) {
                        val page = withContext(Dispatchers.IO) {
                            favoriteApi?.loadNextPage()
                                ?: throw IllegalStateException("收藏 API 未就绪")
                        }
                        favorites = page.items
                        if (!page.hasMore) break
                        delay(FAVORITE_BACKGROUND_BATCH_DELAY_MS)
                    }
                }.onFailure {
                    error = it.message ?: "后台读取收藏失败"
                }
                backgroundLoading = false
            }
        }
            .onFailure {
                error = it.message ?: "读取收藏失败"
                if (favorites.isEmpty()) favorites = emptyList()
                backgroundLoading = false
            }
    }

    val lower = query.trim().lowercase(Locale.US)
    val visible = favorites.filter { item ->
        filter.matches(item.type) && (
            lower.isEmpty() ||
                item.displayTitle().lowercase(Locale.US).contains(lower) ||
                item.displaySummary().lowercase(Locale.US).contains(lower) ||
                item.tags.any { it.lowercase(Locale.US).contains(lower) }
            )
    }.selectedFirst { initialSelectedIds.contains(it.localId.toString()) }
    val visibleIds = visible.map { it.localId.toString() }.toSet()
    val visibleAllSelected = visibleIds.isNotEmpty() && visibleIds.all { selectedIds.contains(it) }
    PageScaffold(
        title = request.title,
        largeTitle = request.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存选择",
                onPrimaryClick = {
                    if (!request.multiSelect && selectedIds.isEmpty()) {
                        Toast.makeText(context, "请选择收藏", Toast.LENGTH_SHORT).show()
                    } else {
                        request.onValue(formatFavoriteIds(selectedIds, request.delimiter, request.multiSelect))
                        onBack()
                    }
                },
                secondaryText = "返回",
                onSecondaryClick = onBack,
                middleText = if (request.multiSelect && visibleIds.isNotEmpty()) {
                    if (visibleAllSelected) "取消全选" else "全选"
                } else {
                    null
                },
                onMiddleClick = if (request.multiSelect && visibleIds.isNotEmpty()) {
                    {
                        selectedIds = if (visibleAllSelected) {
                            selectedIds - visibleIds
                        } else {
                            selectedIds + visibleIds
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
            item {
                SettingsCard {
                    InputRow("搜索", "标题 / 来源 / 标签", query) { query = it }
                    FavoritePickerFilterRow(selected = filter, onSelected = { filter = it })
                }
            }
            item {
                PickerSectionHeader(
                    text = if (backgroundLoading) {
                        "${filter.label}收藏 · ${visible.size} 项 · 后台加载中（${favorites.size}）"
                    } else {
                        "${filter.label}收藏 · ${visible.size} / ${favorites.size} 项"
                    }
                )
            }
            when {
                loading -> item { SettingsCard { EmptyText("正在载入收藏...") } }
                error.isNotEmpty() -> item { SettingsCard { EmptyText(error) } }
                visible.isEmpty() && !backgroundLoading -> item {
                    SettingsCard {
                        EmptyText(if (query.isBlank()) "没有可选收藏" else "没有匹配收藏")
                    }
                }
                visible.isEmpty() -> item { SettingsCard { EmptyText("正在后台查找收藏...") } }
                else -> visible.forEach { favorite ->
                    item {
                        val id = favorite.localId.toString()
                        FavoriteListCard(
                            item = favorite,
                            selected = selectedIds.contains(id),
                            multiSelect = request.multiSelect,
                            onPreview = { path -> preview = FavoriteMediaPreview(favorite, path) },
                            onClick = {
                                selectedIds = if (request.multiSelect) {
                                    if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
                                } else {
                                    setOf(id)
                                }
                            }
                        )
                    }
                }
            }
            if (backgroundLoading && visible.isNotEmpty()) {
                item { SettingsCard { EmptyText("正在后台加载更多收藏...") } }
            }
        }
    }
    preview?.let { media ->
        FavoriteMediaPreviewDialog(media = media, onClose = { preview = null })
    }
}

@Composable
internal fun GroupMemberPickerPage(
    context: Context,
    request: GroupMemberPickerRequest,
    onBack: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var groupLoading by remember(request) { mutableStateOf(true) }
    var groupError by remember(request) { mutableStateOf("") }
    var groups by remember(request) { mutableStateOf(emptyList<ContactOption>()) }
    var selectedGroup by remember(request) { mutableStateOf<ContactOption?>(null) }
    var memberLoading by remember(request) { mutableStateOf(false) }
    var memberError by remember(request) { mutableStateOf("") }
    var members by remember(request) { mutableStateOf(emptyList<ContactOption>()) }
    var query by remember(request) { mutableStateOf("") }
    val initialSelectedEntries = remember(request) {
        parseGroupMemberEntries(request.existingValue).filterTo(linkedSetOf()) { entry ->
            request.allowedGroupIds?.let { entry.substringBefore('/') in it } != false
        }
    }
    var selectedEntries by remember(request) { mutableStateOf<Set<String>>(initialSelectedEntries) }
    var selectionEdited by remember(request) { mutableStateOf(initialSelectedEntries.isNotEmpty()) }
    val groupListState = rememberLazyListState()
    val memberListState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(request) {
        loadContacts(context, ContactPickerMode.GROUPS) { result, throwable ->
            groupLoading = false
            if (throwable != null) {
                groupError = throwable.message ?: "读取群聊失败"
            } else {
                groups = request.allowedGroupIds?.let { allowed ->
                    result.filter { it.id in allowed }
                } ?: result
            }
        }
    }
    LaunchedEffect(selectedGroup?.id) {
        val group = selectedGroup
        if (group == null) {
            members = emptyList()
            memberError = ""
            memberLoading = false
            return@LaunchedEffect
        }
        memberLoading = true
        memberError = ""
        members = emptyList()
        query = ""
        memberListState.scrollToItem(0)
        loadGroupMembers(context, group) { result, throwable ->
            memberLoading = false
            if (throwable != null) {
                memberError = throwable.message ?: "读取群成员失败"
            } else {
                members = result
            }
        }
    }

    val group = selectedGroup
    PageScaffold(
        title = group?.label ?: request.title,
        largeTitle = group?.label ?: request.title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = if (group == null && selectedEntries.isEmpty() && !selectionEdited) "选择群聊" else "保存选择",
                onPrimaryClick = {
                    if (selectedGroup == null && selectedEntries.isEmpty() && !selectionEdited) {
                        Toast.makeText(context, "请先选择群聊", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(selectedEntries.toList())
                    }
                },
                secondaryText = if (group == null) "返回" else "重新选群",
                onSecondaryClick = {
                    if (selectedGroup == null) {
                        onBack()
                    } else {
                        selectedGroup = null
                        query = ""
                    }
                },
                middleText = if (selectedEntries.isEmpty()) null else "清空选择",
                onMiddleClick = if (selectedEntries.isEmpty()) null else {
                    {
                        selectedEntries = emptySet()
                        selectionEdited = true
                    }
                }
            )
        }
    ) { padding ->
        val listRoute = group?.let { GroupMemberPickerRoute.Members(it) } ?: GroupMemberPickerRoute.Groups
        SettingsRouteTransition(
            targetState = listRoute,
            label = "GroupMemberPickerTransition",
            depthOf = { it.depth() }
        ) { route ->
            val routeGroup = (route as? GroupMemberPickerRoute.Members)?.group
            val lower = query.trim().lowercase(Locale.US)
            val rows = if (routeGroup == null) groups else members
            val visible = rows.filter {
                it.matchesSearch(lower)
            }.selectedFirst { option ->
                if (routeGroup == null) {
                    initialSelectedEntries.any { it.substringBefore('/') == option.id }
                } else {
                    initialSelectedEntries.contains(groupMemberEntry(routeGroup.id, option.id))
                }
            }
            val routeListState = if (routeGroup == null) groupListState else memberListState
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                state = routeListState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 84.dp
                )
            ) {
                item {
                    SettingsCard {
                        InputRow(
                            title = "搜索",
                            summary = if (routeGroup == null) "群名称 / 群聊备注 / 群号" else "群昵称 / wxid",
                            value = query,
                            onValueChange = { query = it }
                        )
                    }
                }
                item {
                    SmallTitle(
                        modifier = Modifier.padding(top = 10.dp),
                        text = if (routeGroup == null) "群聊" else "群成员"
                    )
                }
                when {
                    routeGroup == null && groupLoading -> item { SettingsCard { EmptyText("正在载入群聊...") } }
                    routeGroup == null && groupError.isNotEmpty() -> item { SettingsCard { EmptyText(groupError) } }
                    routeGroup != null && memberLoading -> item { SettingsCard { EmptyText("正在载入群成员...") } }
                    routeGroup != null && memberError.isNotEmpty() -> item { SettingsCard { EmptyText(memberError) } }
                    visible.isEmpty() -> item { SettingsCard { EmptyText("没有匹配结果") } }
                    else -> visible.forEach { option ->
                        item {
                            val entry = routeGroup?.let { groupMemberEntry(it.id, option.id) }.orEmpty()
                            ContactListCard(
                                option = option,
                                selected = if (routeGroup == null) false else selectedEntries.contains(entry),
                                multiSelect = routeGroup != null,
                                onClick = {
                                    if (routeGroup == null) {
                                        selectedGroup = option
                                    } else if (selectedEntries.contains(entry)) {
                                        selectedEntries = selectedEntries - entry
                                        selectionEdited = true
                                    } else {
                                        selectedEntries = selectedEntries + entry
                                        selectionEdited = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OptionPickerPage(
    request: OptionPickerRequest,
    onBack: () -> Unit,
    onSelected: (OptionItem) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var selectedValue by remember(request) { mutableStateOf(request.currentValue) }
    PageScaffold(
        title = request.title,
        largeTitle = request.title,
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
            item { SmallTitle(text = "选项") }
            item {
                SettingsCard {
                    request.options.forEachIndexed { index, item ->
                        OptionChoiceRow(
                            item = item,
                            selected = item.value == selectedValue,
                            onClick = {
                                selectedValue = item.value
                                onSelected(item)
                            }
                        )
                        if (index < request.options.lastIndex) InsetDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun UnsupportedFeaturePage(provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
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
            item {
                SettingsCard {
                    EmptyText("这个模块暂时没有可配置项")
                }
            }
        }
    }
}

@Composable
public fun PageScaffold(
    title: String,
    largeTitle: String,
    scrollBehavior: ScrollBehavior,
    onBack: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable ((Backdrop) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    RegisterSettingsBackHandler(onBack)
    val graphicsLayer = rememberGraphicsLayer()
    val backdrop = rememberLayerBackdrop(graphicsLayer)
    val navigationInset = navigationButtonBottomInset()
    val navigationGap = if (navigationInset > 0.dp) NAVIGATION_BUTTON_EXTRA_GAP else 0.dp
    val bottomAvoidance = navigationInset + navigationGap
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            top.yukonga.miuix.kmp.basic.Scaffold(
                topBar = {
                    TopAppBar(
                        title = title,
                        largeTitle = largeTitle,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            onBack?.let { back ->
                                Box(
                                    modifier = Modifier.size(40.dp).responsiveTap(onClick = back),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        imageVector = NavIcons.Back,
                                        contentDescription = "返回",
                                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        },
                        actions = topBarActions,
                        defaultWindowInsetsPadding = true
                    )
                },
                bottomBar = {},
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                content = { padding ->
                    content(
                        PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + bottomAvoidance
                        )
                    )
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomAvoidance)
                .align(Alignment.BottomCenter)
        ) {
            bottomBar?.invoke(backdrop)
        }
    }
}

@Composable
internal fun OptionRow(
    sp: SharedPreferences,
    key: String,
    title: String,
    options: List<OptionItem>,
    defaultValue: Int,
    onValueChanged: (Int) -> Unit = {}
) {
    var currentValue by remember {
        mutableStateOf(sp.getInt(key, defaultValue))
    }
    PopupOptionRow(
        title = title,
        summary = options.firstOrNull { it.value == currentValue }?.label
            ?: options.firstOrNull { it.value == defaultValue }?.label
            ?: "",
        options = options,
        currentValue = currentValue,
        onValueChanged = {
            currentValue = it
            onValueChanged(it)
            sp.edit().putInt(key, it).apply()
        }
    )
}

@Composable
internal fun PopupOptionRow(
    title: String,
    summary: String,
    options: List<OptionItem>,
    currentValue: Int,
    onValueChanged: (Int) -> Unit,
    enabled: Boolean = true
) {
    val selectedIndex = options.indexOfFirst { it.value == currentValue }.takeIf { it >= 0 } ?: 0
    val labels = options.map { it.label }
    WindowDropdown(
        items = labels,
        selectedIndex = selectedIndex,
        title = title,
        summary = summary,
        enabled = enabled,
        showValue = true,
        onSelectedIndexChange = { index ->
            options.getOrNull(index)?.let { onValueChanged(it.value) }
        }
    )
}

@Composable
public fun InputRow(
    title: String,
    summary: String,
    value: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp
            ),
            minLines = minLines,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.secondaryVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
internal fun PopupChoiceRow(
    title: String,
    summary: String,
    options: List<PopupChoice<String>>,
    currentValue: String,
    onValueChanged: (String) -> Unit,
    enabled: Boolean = true
) {
    val selectedIndex = options.indexOfFirst { it.value == currentValue }.takeIf { it >= 0 } ?: 0
    WindowDropdown(
        items = options.map { it.label },
        selectedIndex = selectedIndex,
        title = title,
        summary = summary,
        enabled = enabled,
        showValue = true,
        onSelectedIndexChange = { index ->
            options.getOrNull(index)?.let { onValueChanged(it.value) }
        }
    )
}

@Composable
public fun ColorPickerRow(
    title: String,
    summary: String,
    value: String,
    allowGradient: Boolean = true,
    onReset: (() -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentValue by rememberUpdatedState(value)
    val colorParts = if (allowGradient) {
        colorSpecParts(value)
    } else {
        MemberTitleStore.cleanColor(value) to ""
    }
    var editEnd by remember { mutableStateOf(colorParts.second.isNotEmpty()) }
    val selectedColor = if (allowGradient && editEnd) colorParts.second.ifEmpty { colorParts.first } else colorParts.first
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                Text(
                    text = if (allowGradient) {
                        "$summary，支持 #RRGGBB / #AARRGGBB / #A,#B 渐变"
                    } else {
                        "$summary，支持 #RRGGBB / #AARRGGBB"
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )
            }
            ColorPreviewDot(if (allowGradient) value else colorParts.first)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(if (allowGradient) 19 else 9)) },
                textStyle = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.secondaryVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
            if (onReset != null) {
                Text(
                    text = "重置",
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onReset)
                        .background(MiuixTheme.colorScheme.secondaryVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
        if (expanded) {
            if (allowGradient) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorSpecChip(
                        label = "起始色",
                        value = colorParts.first,
                        selected = !editEnd,
                        onClick = { editEnd = false },
                        modifier = Modifier.weight(1f)
                    )
                    ColorSpecChip(
                        label = "结束色",
                        value = colorParts.second,
                        selected = editEnd,
                        onClick = { editEnd = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            PsColorPicker(
                value = selectedColor,
                onValueChange = { picked ->
                    val next = if (allowGradient) {
                        val latestParts = colorSpecParts(currentValue)
                        if (editEnd) {
                            composeColorSpec(latestParts.first.ifEmpty { picked }, picked)
                        } else {
                            composeColorSpec(picked, latestParts.second)
                        }
                    } else {
                        picked
                    }
                    onValueChange(next)
                },
                modifier = Modifier.padding(top = if (allowGradient) 12.dp else 8.dp)
            )
            Text(
                text = if (allowGradient) {
                    "先选起始色或结束色，再用色盘取色；清空输入框可恢复默认/跟随昵称"
                } else {
                    "可直接输入颜色值，也可以用色盘取色；清空输入框可恢复默认色"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
