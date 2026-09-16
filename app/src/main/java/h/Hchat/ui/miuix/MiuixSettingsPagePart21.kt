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
internal fun ColorSpecChip(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline.copy(alpha = 0.45f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ColorPreviewDot(value, size = 24.dp)
        Column {
            Text(text = label, color = MiuixTheme.colorScheme.onSurface, fontSize = 12.sp)
            Text(
                text = value.ifEmpty { "未设置" },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
internal fun ColorPreviewDot(value: String, size: Dp = 34.dp) {
    val colorParts = colorSpecParts(value)
    val startColor = composeColorFromHex(colorParts.first) ?: MiuixTheme.colorScheme.secondaryVariant
    val endColor = composeColorFromHex(colorParts.second)
    val shape = RoundedCornerShape(size / 2)
    val colorBackground = if (endColor != null && endColor != startColor) {
        Modifier.background(Brush.horizontalGradient(listOf(startColor, endColor)))
    } else {
        Modifier.background(startColor)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .then(colorBackground)
            .border(2.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (value.isEmpty()) {
            Text(text = "-", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 15.sp)
        }
    }
}

@Composable
internal fun PsColorPicker(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = colorPickerSelection(value)
    val markerColor = MiuixTheme.colorScheme.onSurface
    val density = LocalDensity.current
    val paletteSize = 228.dp
    val hueBarWidth = 40.dp
    val paletteSizePx = remember(density) { with(density) { paletteSize.roundToPx() } }
    val hueBarWidthPx = remember(density) { with(density) { hueBarWidth.roundToPx() } }
    val paletteBitmap = remember(selected.hue, paletteSizePx) {
        buildSvPaletteBitmap(selected.hue, paletteSizePx)
    }
    val hueBitmap = remember(paletteSizePx, hueBarWidthPx) {
        buildHuePaletteBitmap(hueBarWidthPx, paletteSizePx)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .size(paletteSize)
                .pointerInput(selected.hue) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onValueChange(
                            colorFromSvOffset(
                                selected.hue,
                                down.position,
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            onValueChange(
                                colorFromSvOffset(
                                    selected.hue,
                                    change.position,
                                    size.width.toFloat(),
                                    size.height.toFloat()
                                )
                            )
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
            drawImage(paletteBitmap)
            val marker = Offset(
                selected.saturation * size.width,
                (1f - selected.value) * size.height
            )
            drawCircle(color = markerColor, radius = 9f, center = marker)
            drawCircle(color = Color.White, radius = 5.5f, center = marker)
        }
        Canvas(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(width = hueBarWidth, height = paletteSize)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(selected.saturation, selected.value) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onValueChange(
                            colorFromHueOffset(
                                selected.saturation,
                                selected.value,
                                down.position.y,
                                size.height.toFloat()
                            )
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            onValueChange(
                                colorFromHueOffset(
                                    selected.saturation,
                                    selected.value,
                                    change.position.y,
                                    size.height.toFloat()
                                )
                            )
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
            drawImage(hueBitmap)
            val markerY = (selected.hue / 360f).coerceIn(0f, 1f) * size.height
            drawCircle(color = markerColor, radius = 10f, center = Offset(size.width / 2f, markerY))
            drawCircle(color = Color.White, radius = 6f, center = Offset(size.width / 2f, markerY))
        }
    }
}

internal fun buildSvPaletteBitmap(hue: Float, sizePx: Int): ImageBitmap {
    val safeSize = sizePx.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(safeSize * safeSize)
    var index = 0
    for (y in 0 until safeSize) {
        val value = (1f - (y.toFloat() / (safeSize - 1))).coerceIn(0f, 1f)
        for (x in 0 until safeSize) {
            val saturation = (x.toFloat() / (safeSize - 1)).coerceIn(0f, 1f)
            pixels[index++] = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        }
    }
    bitmap.setPixels(pixels, 0, safeSize, 0, 0, safeSize, safeSize)
    return bitmap.asImageBitmap()
}

internal fun buildHuePaletteBitmap(widthPx: Int, heightPx: Int): ImageBitmap {
    val safeWidth = widthPx.coerceAtLeast(2)
    val safeHeight = heightPx.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(safeWidth * safeHeight)
    var index = 0
    for (y in 0 until safeHeight) {
        val hue = (y.toFloat() / (safeHeight - 1)).coerceIn(0f, 1f) * 360f
        val color = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))
        repeat(safeWidth) {
            pixels[index++] = color
        }
    }
    bitmap.setPixels(pixels, 0, safeWidth, 0, 0, safeWidth, safeHeight)
    return bitmap.asImageBitmap()
}

internal fun composeColorFromHex(value: String): Color? {
    val normalized = RealNameTailStore.cleanColor(value)
    if (normalized.isEmpty()) return null
    return runCatching { Color(AndroidColor.parseColor(normalized)) }.getOrNull()
}

internal fun colorSpecParts(value: String): Pair<String, String> {
    val cleaned = MemberTitleStore.cleanColorSpec(value)
    if (cleaned.isEmpty()) return "" to ""
    val parts = cleaned.split(',').take(2)
    return parts.getOrElse(0) { "" } to parts.getOrElse(1) { "" }
}

internal fun composeColorSpec(start: String, end: String): String {
    val first = MemberTitleStore.cleanColor(start)
    val second = MemberTitleStore.cleanColor(end)
    if (first.isEmpty()) return ""
    return if (second.isEmpty() || second == first) first else "$first,$second"
}

@Composable
internal fun WeightInputRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    InputRow(
        title = title,
        summary = "100-900，600 以上显示为加粗",
        value = value,
        onValueChange = { next ->
            onValueChange(next.filter { it.isDigit() }.take(3))
        }
    )
}

internal data class ColorPickerSelection(val hue: Float, val saturation: Float, val value: Float)

internal fun colorPickerSelection(value: String): ColorPickerSelection {
    val normalized = MemberTitleStore.cleanColor(value)
    if (normalized.isEmpty()) return ColorPickerSelection(0f, 1f, 1f)
    return runCatching {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(AndroidColor.parseColor(normalized), hsv)
        ColorPickerSelection(hsv[0], hsv[1], hsv[2])
    }.getOrDefault(ColorPickerSelection(0f, 1f, 1f))
}

internal fun colorFromSvOffset(hue: Float, offset: Offset, width: Float, height: Float): String {
    val saturation = (offset.x / width).coerceIn(0f, 1f)
    val value = (1f - (offset.y / height)).coerceIn(0f, 1f)
    val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    return String.format("#%06X", 0xFFFFFF and color)
}

internal fun colorFromHueOffset(saturation: Float, value: Float, y: Float, height: Float): String {
    val hue = (y / height).coerceIn(0f, 1f) * 360f
    val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    return String.format("#%06X", 0xFFFFFF and color)
}

@Composable
internal fun VariableInputRow(
    title: String,
    summary: String,
    value: String,
    variables: List<TemplateVariable>,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        if (summary.isNotBlank()) {
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onValueChange(it.text)
            },
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
        VariableChipRow(
            variables = variables,
            onInsert = { token ->
                val next = fieldValue.insertToken(token)
                fieldValue = next
                onValueChange(next.text)
            }
        )
    }
}

@Composable
internal fun RedPacketReplyContentEditor(
    context: Context,
    replyMode: Int,
    value: String,
    onValueChange: (String) -> Unit,
    onFavoritePicker: (FavoritePickerRequest) -> Unit,
    templateVariables: List<TemplateVariable> = redPacketReplyTemplateVariables
) {
    when {
        redPacketReplyUsesText(replyMode) -> {
            VariableInputRow(
                title = "回复内容",
                summary = "多个回复用 | 分隔随机选择",
                value = value,
                variables = templateVariables,
                minLines = 2,
                onValueChange = onValueChange
            )
        }
        replyMode == RedPacketRuleConfig.REPLY_XML -> {
            VariableInputRow(
                title = "XML 内容",
                summary = "输入 <msg><appmsg>...</appmsg></msg>，支持变量",
                value = value,
                variables = templateVariables,
                minLines = 4,
                onValueChange = onValueChange
            )
        }
        replyMode == RedPacketRuleConfig.REPLY_FAVORITE -> {
            ActionRow(
                title = redPacketReplyPickerTitle(replyMode),
                summary = redPacketReplyContentSummary(replyMode, value)
            ) {
                onFavoritePicker(
                    FavoritePickerRequest(
                        title = redPacketReplyPickerTitle(replyMode),
                        existingValue = value,
                        onValue = onValueChange,
                        multiSelect = true,
                        delimiter = "|"
                    )
                )
            }
            if (value.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选收藏", "当前会从该回复类型移除已选收藏") {
                    onValueChange("")
                }
            }
        }
        else -> {
            ActionRow(
                title = redPacketReplyPickerTitle(replyMode),
                summary = redPacketReplyContentSummary(replyMode, value)
            ) {
                val activity = context as? Activity
                if (activity == null) {
                    Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                } else {
                    RedPacketReplyFilePickerBridge.launch(activity, replyMode) { paths ->
                        if (paths.isNotEmpty()) {
                            onValueChange(paths.joinToString("|"))
                            Toast.makeText(context, "已选择 ${paths.size} 个文件", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            if (value.isNotBlank()) {
                InsetDivider()
                ActionRow("清空已选文件", "当前会从该回复类型移除已选文件") {
                    onValueChange("")
                }
            }
        }
    }
}

@Composable
internal fun RedPacketReplyStepsPage(
    context: Context,
    title: String,
    initialSteps: List<RedPacketReplyStep>,
    onBack: () -> Unit,
    onSave: (List<RedPacketReplyStep>) -> Unit,
    templateVariables: List<TemplateVariable> = redPacketReplyTemplateVariables
) {
    var steps by remember(initialSteps) {
        mutableStateOf(initialSteps.map { normalizeRedPacketReplyStepForUi(it) })
    }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var favoritePicker by remember { mutableStateOf<FavoritePickerRequest?>(null) }
    val listState = rememberLazyListState()

    fun updateStep(index: Int, step: RedPacketReplyStep) {
        if (index !in steps.indices) return
        steps = steps.toMutableList().also { it[index] = normalizeRedPacketReplyStepForUi(step) }
    }

    val currentEditingIndex = editingIndex?.takeIf { it in steps.indices }
    val route = when {
        favoritePicker != null -> RedPacketReplyStepsRoute.FavoritePicker(favoritePicker!!)
        currentEditingIndex != null -> RedPacketReplyStepsRoute.StepEditor(currentEditingIndex)
        else -> RedPacketReplyStepsRoute.Main
    }

    SettingsRouteTransition(
        targetState = route,
        label = "RedPacketReplyStepsRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            is RedPacketReplyStepsRoute.FavoritePicker -> {
                FavoritePickerPage(
                    request = currentRoute.request,
                    onBack = { favoritePicker = null }
                )
            }
            is RedPacketReplyStepsRoute.StepEditor -> {
                val index = currentRoute.index
                val step = steps[index]
                RedPacketReplyStepEditorPage(
                    context = context,
                    title = "第 ${index + 1} 条回复",
                    step = step,
                    canMoveUp = index > 0,
                    canMoveDown = index < steps.lastIndex,
                    onBack = { editingIndex = null },
                    onStepChange = { updateStep(index, it) },
                    templateVariables = templateVariables,
                    onFavoritePicker = { favoritePicker = it },
                    onMoveUp = {
                        if (index > 0) {
                            steps = steps.toMutableList().also {
                                val current = it.removeAt(index)
                                it.add(index - 1, current)
                            }
                            editingIndex = index - 1
                        }
                    },
                    onMoveDown = {
                        if (index < steps.lastIndex) {
                            steps = steps.toMutableList().also {
                                val current = it.removeAt(index)
                                it.add(index + 1, current)
                            }
                            editingIndex = index + 1
                        }
                    },
                    onDelete = {
                        steps = steps.toMutableList().also { it.removeAt(index) }
                        editingIndex = null
                    }
                )
            }
            RedPacketReplyStepsRoute.Main -> RedPacketReplyStepsListPage(
                title = title,
                steps = steps,
                listState = listState,
                onBack = onBack,
                onSave = { onSave(cleanRedPacketReplySteps(steps)) },
                onOpenStep = { editingIndex = it },
                onAddStep = {
                    val index = steps.size
                    steps = steps + newRedPacketReplyStep(index + 1)
                    editingIndex = index
                },
                onMoveUp = { index ->
                    if (index > 0) {
                        steps = steps.toMutableList().also {
                            val current = it.removeAt(index)
                            it.add(index - 1, current)
                        }
                    }
                },
                onMoveDown = { index ->
                    if (index < steps.lastIndex) {
                        steps = steps.toMutableList().also {
                            val current = it.removeAt(index)
                            it.add(index + 1, current)
                        }
                    }
                },
                onDelete = { index ->
                    if (index in steps.indices) steps = steps.toMutableList().also { it.removeAt(index) }
                }
            )
        }
    }
}

@Composable
internal fun RedPacketReplyStepsListPage(
    title: String,
    steps: List<RedPacketReplyStep>,
    listState: LazyListState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenStep: (Int) -> Unit,
    onAddStep: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存回复",
                onPrimaryClick = onSave,
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
            item { SmallTitle(text = "按顺序发送") }
            if (steps.isEmpty()) {
                item {
                    SettingsCard {
                        EmptyText("暂无回复步骤。添加后会按列表顺序依次发送。")
                    }
                }
            } else {
                steps.forEachIndexed { index, step ->
                    item { SmallTitle(modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp), text = "第 ${index + 1} 条") }
                    item {
                        SettingsCard {
                            SelectRow(
                                title = redPacketReplyModeLabel(step.mode),
                                summary = redPacketReplyStepSummary(step),
                                onClick = { onOpenStep(index) }
                            )
                            InsetDivider()
                            RedPacketReplyStepActions(
                                canMoveUp = index > 0,
                                canMoveDown = index < steps.lastIndex,
                                onMoveUp = { onMoveUp(index) },
                                onMoveDown = { onMoveDown(index) },
                                onDelete = { onDelete(index) }
                            )
                        }
                    }
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    ActionRow("新增回复", "添加一条按顺序发送的回复", onAddStep)
                }
            }
        }
    }
}

@Composable
internal fun RedPacketReplyStepEditorPage(
    context: Context,
    title: String,
    step: RedPacketReplyStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onBack: () -> Unit,
    onStepChange: (RedPacketReplyStep) -> Unit,
    templateVariables: List<TemplateVariable>,
    onFavoritePicker: (FavoritePickerRequest) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "完成",
                onPrimaryClick = onBack,
                secondaryText = "返回",
                onSecondaryClick = onBack
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
            item { SmallTitle(text = "回复内容") }
            item {
                SettingsCard {
                    PopupOptionRow(
                        title = "回复类型",
                        summary = redPacketReplyModeLabel(step.mode),
                        options = redPacketReplyStepModeOptions(),
                        currentValue = step.mode,
                        onValueChanged = { mode ->
                            onStepChange(
                                step.copy(
                                    mode = mode,
                                    content = if (mode == step.mode) {
                                        step.content
                                    } else {
                                        defaultRedPacketReplyContent(mode)
                                    }
                                )
                            )
                        }
                    )
                    InsetDivider()
                    RedPacketReplyContentEditor(
                        context = context,
                        replyMode = step.mode,
                        value = step.content,
                        onValueChange = { onStepChange(step.copy(content = it)) },
                        onFavoritePicker = onFavoritePicker,
                        templateVariables = templateVariables
                    )
                    InsetDivider()
                    NumberInputRow(
                        title = "发送前延迟",
                        summary = "单位 ms，按步骤顺序等待",
                        value = step.delayMs.coerceAtLeast(0L).toString(),
                        onValueChange = { value ->
                            onStepChange(step.copy(delayMs = value.toLongOrNull()?.coerceIn(0L, 600000L) ?: 0L))
                        }
                    )
                    InsetDivider()
                    SwitchRow(
                        checked = step.random,
                        title = "随机追加延迟",
                        summary = "在发送前延迟后随机追加 0-2 秒",
                        onCheckedChange = { onStepChange(step.copy(random = it)) }
                    )
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "操作") }
            item {
                SettingsCard {
                    RedPacketReplyStepActions(
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
internal fun RedPacketReplyStepActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            text = "上移",
            onClick = { if (canMoveUp) onMoveUp() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        TextButton(
            text = "下移",
            onClick = { if (canMoveDown) onMoveDown() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
        TextButton(
            text = "删除",
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}

@Composable
internal fun VariableChipRow(
    variables: List<TemplateVariable>,
    onInsert: (String) -> Unit
) {
    if (variables.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(top = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        variables.forEach { variable ->
            VariableChip(variable = variable, onClick = { onInsert(variable.token) })
        }
    }
}

@Composable
internal fun VariableChip(variable: TemplateVariable, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val bg = if (pressed) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = variable.label,
            color = MiuixTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

internal fun TextFieldValue.insertToken(token: String): TextFieldValue {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    val left = minOf(start, end)
    val right = maxOf(start, end)
    val nextText = text.replaceRange(left, right, token)
    val cursor = left + token.length
    return TextFieldValue(nextText, TextRange(cursor))
}

@Composable
internal fun NumberInputRow(title: String, summary: String, value: String, onValueChange: (String) -> Unit) {
    InputRow(title, summary, value.filter { it.isDigit() }, onValueChange = { next ->
        onValueChange(next.filter { it.isDigit() })
    })
}

@Composable
internal fun TimeOfDayPickerRow(
    title: String,
    value: String,
    allowEmpty: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val normalized = normalizeTimeOfDay(value, if (allowEmpty) "" else "00:00:00")
    ActionRow(
        title = title,
        summary = normalized.ifBlank { "不限制" },
        onClick = { showPicker = true }
    )
    if (showPicker) {
        TimeOfDayPickerDialog(
            title = title,
            initialValue = normalized,
            allowEmpty = allowEmpty,
            onDismiss = { showPicker = false },
            onConfirm = {
                onValueChange(it)
                showPicker = false
            }
        )
    }
}

@Composable
internal fun TimeOfDayPickerDialog(
    title: String,
    initialValue: String,
    allowEmpty: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialSeconds = parseTimeOfDaySeconds(initialValue).takeIf { it >= 0 } ?: 0
    var hour by remember(initialValue) { mutableStateOf((initialSeconds / 3600).toString()) }
    var minute by remember(initialValue) { mutableStateOf((initialSeconds / 60 % 60).toString()) }
    var second by remember(initialValue) { mutableStateOf((initialSeconds % 60).toString()) }
    val hourValue = hour.toIntOrNull()
    val minuteValue = minute.toIntOrNull()
    val secondValue = second.toIntOrNull()
    val valid = hourValue != null && hourValue in 0..23 &&
        minuteValue != null && minuteValue in 0..59 &&
        secondValue != null && secondValue in 0..59
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeOfDayNumberField("时", hour, 23, Modifier.weight(1f)) { hour = it }
                    TimeOfDayNumberField("分", minute, 59, Modifier.weight(1f)) { minute = it }
                    TimeOfDayNumberField("秒", second, 59, Modifier.weight(1f)) { second = it }
                }
                Text(
                    text = if (valid) {
                        String.format(
                            Locale.US,
                            "%02d:%02d:%02d",
                            hourValue ?: 0,
                            minuteValue ?: 0,
                            secondValue ?: 0
                        )
                    } else {
                        "请输入有效的时、分、秒"
                    },
                    color = if (valid) MiuixTheme.colorScheme.onSurfaceVariantSummary else Color(0xFFD93025),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (allowEmpty) {
                        TextButton(
                            text = "清空",
                            onClick = { onConfirm("") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "确定",
                        onClick = {
                            if (valid) {
                                onConfirm(
                                    String.format(
                                        Locale.US,
                                        "%02d:%02d:%02d",
                                        hourValue ?: 0,
                                        minuteValue ?: 0,
                                        secondValue ?: 0
                                    )
                                )
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
internal fun TimeOfDayNumberField(
    label: String,
    value: String,
    max: Int,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            textAlign = TextAlign.Center
        )
        BasicTextField(
            value = value,
            onValueChange = { next ->
                val digits = next.filter { it.isDigit() }.take(2)
                val number = digits.toIntOrNull()
                if (digits.isEmpty() || (number != null && number <= max)) onValueChange(digits)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        )
    }
}

internal fun normalizeTimeOfDay(value: String?, fallback: String): String {
    val seconds = parseTimeOfDaySeconds(value)
    if (seconds < 0) return fallback
    return String.format(
        Locale.US,
        "%02d:%02d:%02d",
        seconds / 3600,
        seconds / 60 % 60,
        seconds % 60
    )
}

internal fun parseTimeOfDaySeconds(value: String?): Int {
    if (value.isNullOrBlank()) return -1
    val parts = value.trim().split(":")
    if (parts.size !in 2..3) return -1
    val hour = parts[0].toIntOrNull() ?: return -1
    val minute = parts[1].toIntOrNull() ?: return -1
    val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
    if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return -1
    return hour * 3600 + minute * 60 + second
}

@Composable
internal fun MessageBlockContactFilterRow(
    selected: MessageBlockContactFilter,
    enableOfficials: Boolean = true,
    onSelected: (MessageBlockContactFilter) -> Unit
) {
    val items = buildList {
        add(MessageBlockContactFilter.FRIENDS)
        add(MessageBlockContactFilter.GROUPS)
        if (enableOfficials) add(MessageBlockContactFilter.OFFICIALS)
        add(MessageBlockContactFilter.LABELS)
        add(MessageBlockContactFilter.ALL)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            ContactFilterChip(
                text = item.label,
                selected = item == selected,
                onClick = { onSelected(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ConversationRuleCategoryTabs(
    selected: ConversationRuleCategory,
    onSelected: (ConversationRuleCategory) -> Unit,
    includeOfficial: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ConversationRuleCategory.entries
            .filter { includeOfficial || it != ConversationRuleCategory.OFFICIAL }
            .forEach { category ->
            ContactFilterChip(
                text = category.label,
                selected = category == selected,
                onClick = { onSelected(category) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun BatchDeleteConfirmDialog(
    show: Boolean,
    message: String,
    labels: List<String>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!show) return
    WindowDialog(
        show = true,
        title = "确认批量删除",
        onDismissRequest = onDismiss,
        content = {
            Column {
                Text(
                    text = buildString {
                        append(message)
                        val names = labels.filter { it.isNotBlank() }.take(6).joinToString("、")
                        if (names.isNotBlank()) append("\n\n").append(names)
                        if (labels.size > 6) append(" 等")
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
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    TextButton(
                        text = "确认删除",
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
internal fun FriendContactFilterRow(
    selected: ContactPickerFilter,
    onSelected: (ContactPickerFilter) -> Unit
) {
    val items = listOf(ContactPickerFilter.FRIENDS, ContactPickerFilter.LABELS)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            ContactFilterChip(
                text = item.label,
                selected = item == selected,
                onClick = { onSelected(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ContactPickerScopeFilterRow(
    selected: ContactPickerScopeTab,
    onSelected: (ContactPickerScopeTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ContactPickerScopeTab.entries.forEach { item ->
            ContactFilterChip(
                text = item.label,
                selected = item == selected,
                onClick = { onSelected(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ContactPickerScopeRow(
    title: String,
    memberCount: Int,
    selectedCount: Int,
    memberUnit: String,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val allSelected = memberCount > 0 && selectedCount == memberCount
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rememberPressFeedbackColor(pressed))
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (selectedCount == 0) {
                    "$memberCount $memberUnit"
                } else {
                    "已选 $selectedCount / $memberCount $memberUnit"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        SelectionMark(selected = allSelected, multiSelect = true)
    }
}

@Composable
internal fun ContactFilterRow(
    selected: ContactPickerFilter,
    enableLabels: Boolean = false,
    enableOfficials: Boolean = false,
    onSelected: (ContactPickerFilter) -> Unit
) {
    val items = buildList {
        add(ContactPickerFilter.FRIENDS)
        add(ContactPickerFilter.GROUPS)
        if (enableOfficials) add(ContactPickerFilter.OFFICIALS)
        if (enableLabels) add(ContactPickerFilter.LABELS)
        add(ContactPickerFilter.ALL)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            ContactFilterChip(
                text = item.label,
                selected = item == selected,
                onClick = { onSelected(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun FavoritePickerFilterRow(
    selected: FavoritePickerFilter,
    onSelected: (FavoritePickerFilter) -> Unit
) {
    val rows = listOf(
        listOf(FavoritePickerFilter.IMAGE, FavoritePickerFilter.TEXT, FavoritePickerFilter.VOICE),
        listOf(FavoritePickerFilter.VIDEO, FavoritePickerFilter.OTHER, FavoritePickerFilter.ALL)
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        rows.forEachIndexed { index, filters ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { item ->
                    ContactFilterChip(
                        text = item.label,
                        selected = item == selected,
                        onClick = { onSelected(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ContactLabelFilterRow(
    labels: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    if (labels.isEmpty()) {
        EmptyText("没有好友标签")
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            ContactFilterChip(
                text = label,
                selected = label == selected,
                onClick = { onSelected(label) },
                modifier = Modifier.defaultMinSize(minWidth = 70.dp)
            )
        }
    }
}

@Composable
internal fun GroupChatLabelPickerRow(
    label: GroupChatLabel,
    selectedCount: Int,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    val allSelected = selectedCount == label.groupIds.size
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.name,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (selectedCount == 0) {
                    "${label.groupIds.size} 个群聊"
                } else {
                    "已选 $selectedCount / ${label.groupIds.size} 个群聊"
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        SelectionMark(selected = allSelected, multiSelect = true)
    }
}

@Composable
internal fun ContactFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    var visualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            visualPressed = true
        } else {
            delay(100L)
            visualPressed = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (visualPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 60 else 145),
        label = "ContactFilterPressScale"
    )
    val background = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = if (visualPressed) 0.88f else 1f)
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = if (visualPressed) 0.18f else 0.06f)
    }
    val textColor = if (selected) {
        Color.White
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Box(
        modifier = modifier
            .height(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

@Composable
internal fun PickerSectionHeader(
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontWeight = FontWeight.Medium
        )
    }
}


@Composable
internal fun SelectRow(title: String, summary: String, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            if (summary.isNotBlank()) {
                Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            }
        }
        Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
    }
}

@Composable
internal fun MessageBlockLabelListCard(
    label: MessageBlockLabelOption,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 14.dp
    ) {
        var pressed by remember { mutableStateOf(false) }
        val pressFeedbackColor = rememberPressFeedbackColor(pressed)
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(pressFeedbackColor)
                .responsiveTap(
                    onClick = onClick,
                    onPressedChange = { pressed = it }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.secondaryVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.name.take(1).ifEmpty { "签" },
                    color = MiuixTheme.colorScheme.onSecondaryVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = label.name, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(
                    text = "${label.contactIds.size} 人",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp
                )
            }
            Text(text = "›", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 22.sp)
        }
    }
}

@Composable
internal fun MessageBlockContactListCard(
    row: MessageBlockContactOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val option = row.contact
    val summary = if (row.labelNames.isEmpty()) {
        option.id
    } else {
        option.id + " · " + row.labelNames.joinToString(" / ")
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 14.dp
    ) {
        MessageBlockContactRow(
            option = option,
            summary = summary,
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
internal fun MessageBlockContactRow(
    option: ContactOption,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(option)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = option.label, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        SelectionMark(selected = selected, multiSelect = true)
    }
}

@Composable
internal fun ContactListCard(
    option: ContactOption,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 14.dp
    ) {
        ContactRow(
            option = option,
            selected = selected,
            multiSelect = multiSelect,
            onClick = onClick
        )
    }
}

@Composable
internal fun FavoriteListCard(
    item: WeChatFavoriteItem,
    selected: Boolean,
    multiSelect: Boolean,
    onPreview: (String) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        cornerRadius = 8.dp
    ) {
        var pressed by remember { mutableStateOf(false) }
        val pressFeedbackColor = rememberPressFeedbackColor(pressed)
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(pressFeedbackColor)
                .responsiveTap(
                    onClick = onClick,
                    onPressedChange = { pressed = it }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FavoriteMediaThumbnail(item = item, onPreview = onPreview)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = item.displayTitle(),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.displaySummary(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (item.tags.isNotEmpty()) {
                    Text(
                        text = "标签 · ${item.tags.joinToString(" / ")}",
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            SelectionMark(selected = selected, multiSelect = multiSelect)
        }
    }
}

@Composable
internal fun FavoriteMediaThumbnail(
    item: WeChatFavoriteItem,
    onPreview: (String) -> Unit
) {
    var previewPath by remember(item.localId) { mutableStateOf("") }
    var thumbnail by remember(item.localId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.localId, item.type) {
        if (item.type != 2 && item.type != 4) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            val path = WeChatApis.media()?.favorites()?.previewPath(item.localId).orEmpty()
            path to path.takeIf { it.isNotBlank() }
                ?.let { loadFavoriteThumbnail(it, item.type, 320) }
        }
        previewPath = result.first
        thumbnail = result.second
    }
    val previewModifier = if (previewPath.isNotBlank()) {
        Modifier.responsiveTap(onClick = { onPreview(previewPath) })
    } else {
        Modifier
    }
    Box(
        modifier = Modifier.size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant)
            .then(previewModifier),
        contentAlignment = Alignment.Center
    ) {
        val image = thumbnail
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = if (item.type == 4) "预览视频" else "预览图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.type == 4) {
                Box(
                    modifier = Modifier.size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.56f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        imageVector = NavIcons.Entertainment,
                        contentDescription = "播放",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Text(
                text = item.typeLabel().take(1).ifEmpty { "藏" },
                color = MiuixTheme.colorScheme.onSecondaryVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

internal fun loadFavoriteThumbnail(path: String, type: Int, maxDimension: Int): ImageBitmap? {
    if (!File(path).isFile) return null
    return if (type == 4) {
        @Suppress("DEPRECATION")
        ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND)?.asImageBitmap()
    } else {
        decodeFavoriteImage(path, maxDimension)
    }
}

internal fun decodeFavoriteImage(path: String, maxDimension: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}

@Composable
internal fun FavoriteMediaPreviewDialog(
    media: FavoriteMediaPreview,
    onClose: () -> Unit
) {
    var image by remember(media.path) { mutableStateOf<ImageBitmap?>(null) }
    var videoView by remember(media.path) { mutableStateOf<VideoView?>(null) }
    val previewHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.62f).coerceAtMost(560.dp)
    LaunchedEffect(media.path, media.item.type) {
        if (media.item.type == 2) {
            image = withContext(Dispatchers.IO) { decodeFavoriteImage(media.path, 2048) }
        }
    }
    DisposableEffect(media.path) {
        onDispose { videoView?.stopPlayback() }
    }
    WindowDialog(
        show = true,
        title = "${media.item.typeLabel()}预览",
        onDismissRequest = onClose,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (media.item.type == 4) {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).also { view ->
                                val controller = MediaController(context)
                                controller.setAnchorView(view)
                                view.setMediaController(controller)
                                view.setVideoPath(media.path)
                                view.setOnPreparedListener { view.start() }
                                videoView = view
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = previewHeight)
                    )
                } else {
                    val bitmap = image
                    if (bitmap == null) {
                        EmptyText("正在载入预览...")
                    } else {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "图片预览",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = previewHeight)
                        )
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

@Composable
internal fun ContactRow(
    option: ContactOption,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(option)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = option.label, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            val summary = buildList {
                add(option.id)
                option.labels.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" / ")) }
                option.extraSummary.takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(" · ")
            Text(text = summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
        }
        SelectionMark(selected = selected, multiSelect = multiSelect)
    }
}

@Composable
internal fun ContactAvatar(option: ContactOption) {
    val context = LocalContext.current
    var bitmap by remember(option.id, option.avatarUrl, option.avatarBackupUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(option.id, option.avatarUrl, option.avatarBackupUrl) {
        bitmap = loadAvatarBitmap(option.id, option.avatarUrl, option.avatarBackupUrl)
    }
    val image = bitmap
    val avatarBg = if (image == null && option.group) Color(0xFF2E7DFF) else MiuixTheme.colorScheme.secondaryVariant
    val avatarTextColor = if (image == null && option.group) Color.White else MiuixTheme.colorScheme.onSecondaryVariant
    val avatarShape = if (RoundAvatarSettings.enabled(context)) {
        RoundedCornerShape((42f * RoundAvatarSettings.radiusFactor(context)).dp)
    } else {
        RoundedCornerShape(12.dp)
    }
    Box(
        modifier = Modifier.size(42.dp)
            .clip(avatarShape)
            .background(avatarBg),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = if (option.group) "群" else option.label.take(1).ifEmpty { "友" },
                color = avatarTextColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun OptionChoiceRow(
    item: OptionItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressFeedbackColor = rememberPressFeedbackColor(pressed)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pressFeedbackColor)
            .responsiveTap(
                onClick = onClick,
                onPressedChange = { pressed = it }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.label, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            if (item.summary.isNotEmpty()) {
                Text(text = item.summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp)
            }
        }
        SelectionMark(selected = selected, multiSelect = false)
    }
}

@Composable
internal fun SelectionMark(selected: Boolean, multiSelect: Boolean) {
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Checkbox(
            modifier = Modifier.size(22.dp),
            state = if (selected) ToggleableState.On else ToggleableState.Off,
            onClick = null
        )
    }
}

@Composable
internal fun navigationButtonBottomInset(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val gestureNavigation = remember(context) { isGestureNavigationMode(context) }
    if (gestureNavigation) return 0.dp
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val resourceBottom = remember(context, density) {
        with(density) { navigationBarHeightPx(context).toDp() }
    }
    val bottomInset = if (navigationBottom > resourceBottom) navigationBottom else resourceBottom
    return if (bottomInset >= NAVIGATION_BUTTON_MIN_INSET) bottomInset else 0.dp
}

internal fun navigationBarHeightPx(context: Context): Int {
    val resources = context.resources
    val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (id > 0) {
        runCatching { resources.getDimensionPixelSize(id) }.getOrDefault(0)
    } else {
        0
    }
}

internal fun isGestureNavigationMode(context: Context): Boolean {
    val resolver = context.contentResolver
    val androidMode = runCatching {
        Settings.Secure.getInt(resolver, "navigation_mode", -1)
    }.getOrDefault(-1)
    if (androidMode == 2) return true
    val miuiGlobalGesture = runCatching {
        Settings.Global.getInt(resolver, "force_fsg_nav_bar", 0)
    }.getOrDefault(0)
    val miuiSecureGesture = runCatching {
        Settings.Secure.getInt(resolver, "force_fsg_nav_bar", 0)
    }.getOrDefault(0)
    return miuiGlobalGesture == 1 || miuiSecureGesture == 1
}

@Composable
internal fun BottomBarButton(text: String, modifier: Modifier, filled: Boolean, onClick: () -> Unit) {
    val bg = if (filled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryVariant
    val fg = if (filled) Color.White else MiuixTheme.colorScheme.onSecondaryVariant
    Text(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        text = text,
        color = fg,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}

@Composable
internal fun StatsCard(sp: SharedPreferences) {
    SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "红包记录")
    SettingsCard {
        InfoRow("已抢红包", "${sp.getInt(RedPacketSettings.KEY_STATS_COUNT, 0)} 个")
        InsetDivider()
        InfoRow("累计金额", String.format("%.2f 元", sp.getInt(RedPacketSettings.KEY_STATS_AMOUNT, 0) / 100.0))
        InsetDivider()
        InfoRow("今日抢到", "${sp.getInt(RedPacketSettings.KEY_STATS_TODAY, 0)} 个")
        InsetDivider()
        InfoRow("失败次数", "${sp.getInt(RedPacketSettings.KEY_STATS_FAILED, 0)} 次")
    }
}

@Composable
internal fun AboutCard() {
    val hostVersion = WeChatApis.version()?.current()?.displayVersion()?.takeIf { it.isNotBlank() } ?: "未知"
    val moduleVersion = BuildConfig.VERSION_NAME.takeIf { it.isNotBlank() } ?: "未知"
    SettingsCard {
        InfoRow(label = "版本", value = moduleVersion)
        InsetDivider()
        InfoRow(label = "宿主", value = hostVersion)
        InsetDivider()
        InfoRow(label = "Hchat作者", value = "句号总")
        InsetDivider()
        InfoRow(label = "分支作者", value = "浪浪")
    }
}
