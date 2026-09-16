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
import h.Hchat.hooks.items.chattoolbar.ChatToolbarFeature
import h.Hchat.hooks.items.homesidepanel.HomeSidePanelFeature
import h.Hchat.hooks.items.monetgenerator.MonetModuleGeneratorFeature
import h.Hchat.hooks.items.securemessage.SecureMessageSettings
import h.Hchat.hooks.items.transparentavatar.UploadTransparentAvatarFeature
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
import h.Hchat.hooks.items.profileid.ProfileGroupInfoSettingsProvider
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

internal fun filterPluginSearchResults(
    query: String,
    plugins: List<ScriptPluginRuntime.ScriptPlugin>
): List<ScriptPluginRuntime.ScriptPlugin> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return emptyList()
    return plugins.filter { plugin ->
        containsSearchText(plugin.displayName.orEmpty(), keyword) ||
            containsSearchText(plugin.name, keyword) ||
            containsSearchText(plugin.id, keyword) ||
            containsSearchText(plugin.dir.name, keyword) ||
            containsSearchText(plugin.author, keyword) ||
            containsSearchText(plugin.version, keyword)
    }
}

internal fun containsSearchText(text: String, query: String): Boolean {
    return text.contains(query, ignoreCase = true)
}

internal fun readSearchHistory(sp: SharedPreferences): List<String> {
    val raw = sp.getString(KEY_SEARCH_HISTORY, "").orEmpty()
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val keyword = array.optString(index).trim()
                if (keyword.isNotEmpty() && keyword !in this) add(keyword)
                if (size >= SEARCH_HISTORY_LIMIT) break
            }
        }
    }.getOrDefault(emptyList())
}

internal fun saveSearchHistory(sp: SharedPreferences, history: List<String>) {
    val array = JSONArray()
    history.take(SEARCH_HISTORY_LIMIT).forEach { array.put(it) }
    sp.edit().putString(KEY_SEARCH_HISTORY, array.toString()).apply()
}

internal fun rememberSearchKeyword(sp: SharedPreferences, keyword: String): List<String> {
    val value = keyword.trim()
    if (value.isBlank()) return readSearchHistory(sp)
    val next = (listOf(value) + readSearchHistory(sp).filterNot { it.equals(value, ignoreCase = true) })
        .take(SEARCH_HISTORY_LIMIT)
    saveSearchHistory(sp, next)
    return next
}

internal sealed class FeatureSubRoute {
    object Main : FeatureSubRoute()
    data class ContactPicker(val request: ContactPickerRequest) : FeatureSubRoute()
    data class OptionPicker(val request: OptionPickerRequest) : FeatureSubRoute()
}

internal sealed class MomentsAutoLikeRoute {
    object Main : MomentsAutoLikeRoute()
    data class ContactPicker(val listMode: Int, val request: ContactPickerRequest) : MomentsAutoLikeRoute()
}

internal sealed class AtAllNotificationBlockRoute {
    object Main : AtAllNotificationBlockRoute()
    data class GroupPicker(val request: ContactPickerRequest) : AtAllNotificationBlockRoute()
}

internal sealed class MomentsAutoCommentRoute {
    object Main : MomentsAutoCommentRoute()
    data class ContactPicker(val listMode: Int, val request: ContactPickerRequest) : MomentsAutoCommentRoute()
}

internal sealed class MomentsAutoForwardRoute {
    object Main : MomentsAutoForwardRoute()
    object ReplacementRules : MomentsAutoForwardRoute()
    data class ContactPicker(val request: ContactPickerRequest) : MomentsAutoForwardRoute()
}

internal sealed class MomentsContactFilterRoute {
    object Main : MomentsContactFilterRoute()
    data class ContactPicker(val request: ContactPickerRequest) : MomentsContactFilterRoute()
}

internal sealed class MomentsPostNotificationRoute {
    object Main : MomentsPostNotificationRoute()
    data class ContactPicker(val request: ContactPickerRequest) : MomentsPostNotificationRoute()
}

internal sealed class RedPacketRoute {
    object Main : RedPacketRoute()
    object TemplateManager : RedPacketRoute()
    object ListManager : RedPacketRoute()
    object ReplySteps : RedPacketRoute()
    data class TemplateEditor(val request: RedPacketTemplateEditorRequest) : RedPacketRoute()
    data class BindingEditor(val request: RedPacketBindingEditorRequest) : RedPacketRoute()
    data class ContactPicker(val request: ContactPickerRequest) : RedPacketRoute()
    data class RuleContactPicker(val request: MessageBlockContactPickerRequest) : RedPacketRoute()
    data class OptionPicker(val request: OptionPickerRequest) : RedPacketRoute()
}

internal sealed class AutoTransferRoute {
    object Main : AutoTransferRoute()
    object TemplateManager : AutoTransferRoute()
    object BindingManager : AutoTransferRoute()
    object BatchApply : AutoTransferRoute()
    data class GlobalReplySteps(val target: TransferReplyTarget) : AutoTransferRoute()
    data class TemplateEditor(val request: TransferTemplateEditorRequest) : AutoTransferRoute()
    data class BindingEditor(val request: TransferBindingEditorRequest) : AutoTransferRoute()
    data class ContactPicker(val request: ContactPickerRequest) : AutoTransferRoute()
}

internal enum class TransferReplyTarget {
    PRIVATE,
    GROUP
}

internal sealed class MessageBlockRoute {
    object Main : MessageBlockRoute()
    object TemplateManager : MessageBlockRoute()
    object ListManager : MessageBlockRoute()
    data class DefaultRuleEditor(val kind: MessageBlockDefaultRuleKind) : MessageBlockRoute()
    data class TemplateEditor(val request: MessageBlockTemplateEditorRequest) : MessageBlockRoute()
    data class BindingEditor(val request: MessageBlockBindingEditorRequest) : MessageBlockRoute()
    data class BatchBindingEditor(val request: MessageBlockBatchBindingEditorRequest) : MessageBlockRoute()
    data class ContactPicker(val request: ContactPickerRequest) : MessageBlockRoute()
    data class GroupMemberPicker(val request: GroupMemberPickerRequest) : MessageBlockRoute()
}

internal sealed class GroupLeaveRoute {
    object Main : GroupLeaveRoute()
    object GroupManager : GroupLeaveRoute()
    object TemplateManager : GroupLeaveRoute()
    object BatchTemplateBinding : GroupLeaveRoute()
    data class TemplateEditor(val request: GroupLeaveTemplateEditorRequest) : GroupLeaveRoute()
    data class GroupEditor(val groupId: String, val label: String) : GroupLeaveRoute()
    data class ContactPicker(val request: ContactPickerRequest) : GroupLeaveRoute()
    data class FavoritePicker(val request: FavoritePickerRequest) : GroupLeaveRoute()
}

internal sealed class GroupChatLabelRoute {
    object Main : GroupChatLabelRoute()
    data class Editor(val label: GroupChatLabel, val existing: Boolean) : GroupChatLabelRoute()
    data class GroupPicker(val label: GroupChatLabel, val existing: Boolean) : GroupChatLabelRoute()
}

internal sealed class ConversationGroupRoute {
    object Main : ConversationGroupRoute()
    data class Editor(val group: ConversationGroup, val existing: Boolean) : ConversationGroupRoute()
    data class ConversationPicker(val group: ConversationGroup, val existing: Boolean) : ConversationGroupRoute()
    data class ParentPicker(val group: ConversationGroup, val existing: Boolean) : ConversationGroupRoute()
    data class AutomaticGrouping(val group: ConversationGroup) : ConversationGroupRoute()
    data class AutomaticPicker(
        val group: ConversationGroup,
        val kind: ConversationGroupAutomaticPickerKind
    ) : ConversationGroupRoute()
}

internal enum class ConversationGroupAutomaticPickerKind(
    val title: String,
    val emptyText: String
) {
    GROUPS("选择自动归拢群聊", "没有可选群聊"),
    OFFICIAL_INCLUDE("选择要包含的公众号", "没有可选公众号"),
    OFFICIAL_EXCLUDE("选择要排除的公众号", "没有可选公众号")
}

internal sealed class GroupRenameRoute {
    object Main : GroupRenameRoute()
    object GroupManager : GroupRenameRoute()
    object TemplateManager : GroupRenameRoute()
    object BatchTemplateBinding : GroupRenameRoute()
    data class GroupEditor(val groupId: String, val label: String) : GroupRenameRoute()
    data class TemplateEditor(val index: Int, val template: GroupRenameReplyTemplate, val canDelete: Boolean) : GroupRenameRoute()
    data class ContactPicker(val request: ContactPickerRequest, val fromMain: Boolean = false) : GroupRenameRoute()
}

internal sealed class ScheduledTaskRoute {
    object Main : ScheduledTaskRoute()
    object Editor : ScheduledTaskRoute()
    object ContactPicker : ScheduledTaskRoute()
    data class FavoritePicker(val index: Int, val request: FavoritePickerRequest) : ScheduledTaskRoute()
}

internal sealed class SelectedMessagesRoute {
    object Main : SelectedMessagesRoute()
    object Editor : SelectedMessagesRoute()
    object ContactPicker : SelectedMessagesRoute()
    data class FavoritePicker(val request: FavoritePickerRequest) : SelectedMessagesRoute()
}

internal sealed class AudioTransformRoute {
    object Main : AudioTransformRoute()
    object ContactPicker : AudioTransformRoute()
}

internal sealed class CustomNotificationRoute {
    object Main : CustomNotificationRoute()
    object RuleList : CustomNotificationRoute()
    object BatchEditor : CustomNotificationRoute()
    data class DefaultEditor(val kind: CustomNotificationDefaultKind) : CustomNotificationRoute()
    data class RuleEditor(val talker: String) : CustomNotificationRoute()
    data class ContactPicker(val request: ContactPickerRequest) : CustomNotificationRoute()
    data class GroupMemberPicker(val talker: String, val request: GroupMemberPickerRequest) : CustomNotificationRoute()
}

internal sealed class KeywordNotificationRoute {
    object Main : KeywordNotificationRoute()
    object KeywordList : KeywordNotificationRoute()
    object TemplateEditor : KeywordNotificationRoute()
    data class KeywordEditor(val oldKeyword: String?) : KeywordNotificationRoute()
    data class ContactPicker(val request: ContactPickerRequest, val includeMode: Boolean) : KeywordNotificationRoute()
}

internal sealed class TextSpeechRoute {
    object Main : TextSpeechRoute()
    data class ContactPicker(val request: ContactPickerRequest) : TextSpeechRoute()
}

internal enum class MessageBlockDefaultRuleKind {
    PRIVATE,
    GROUP,
    OFFICIAL
}

internal enum class CustomNotificationDefaultKind {
    PRIVATE,
    GROUP,
    OFFICIAL
}

internal data class AutoReplyFriendAutomationKeys(
    val labelNewFriendEnable: String,
    val labelDateEnable: String,
    val labelDateFormat: String,
    val labelExistingEnable: String,
    val labelSelectedNames: String,
    val remarkNewFriendEnable: String,
    val remarkNicknameSuffixEnable: String,
    val remarkDateEnable: String,
    val remarkDateFormat: String,
    val remarkCustomEnable: String,
    val remarkCustomText: String
)

internal sealed class AutoReplyEditorRoute {
    object Main : AutoReplyEditorRoute()
    data class OptionPicker(val request: OptionPickerRequest) : AutoReplyEditorRoute()
    data class ContactPicker(val request: ContactPickerRequest) : AutoReplyEditorRoute()
    data class GroupMemberPicker(val request: GroupMemberPickerRequest) : AutoReplyEditorRoute()
}

internal sealed class AutoReplyStepsRoute {
    object Main : AutoReplyStepsRoute()
    data class OptionPicker(val request: OptionPickerRequest) : AutoReplyStepsRoute()
    data class ContactPicker(val request: ContactPickerRequest) : AutoReplyStepsRoute()
    data class FavoritePicker(val request: FavoritePickerRequest) : AutoReplyStepsRoute()
}

internal sealed class AutoReplyAiRoute {
    object Main : AutoReplyAiRoute()
    object Xiaozhi : AutoReplyAiRoute()
    object Zhilia : AutoReplyAiRoute()
    object ZhiliaConfigs : AutoReplyAiRoute()
    object ZhiliaModels : AutoReplyAiRoute()
    data class OptionPicker(val request: OptionPickerRequest) : AutoReplyAiRoute()
}

internal sealed class RedPacketReplyStepsRoute {
    object Main : RedPacketReplyStepsRoute()
    data class StepEditor(val index: Int) : RedPacketReplyStepsRoute()
    data class FavoritePicker(val request: FavoritePickerRequest) : RedPacketReplyStepsRoute()
}

internal sealed class MessageBlockContactPickerRoute {
    object Labels : MessageBlockContactPickerRoute()
    data class Contacts(
        val filter: MessageBlockContactFilter,
        val label: MessageBlockLabelOption?
    ) : MessageBlockContactPickerRoute()
}

internal sealed class GroupMemberPickerRoute {
    object Groups : GroupMemberPickerRoute()
    data class Members(val group: ContactOption) : GroupMemberPickerRoute()
}

internal fun FeatureSubRoute.depth(): Int = when (this) {
    FeatureSubRoute.Main -> 0
    is FeatureSubRoute.ContactPicker,
    is FeatureSubRoute.OptionPicker -> 1
}

internal fun RedPacketRoute.depth(): Int = when (this) {
    RedPacketRoute.Main -> 0
    RedPacketRoute.TemplateManager,
    RedPacketRoute.ListManager,
    RedPacketRoute.ReplySteps,
    is RedPacketRoute.ContactPicker,
    is RedPacketRoute.OptionPicker -> 1
    is RedPacketRoute.TemplateEditor,
    is RedPacketRoute.BindingEditor,
    is RedPacketRoute.RuleContactPicker -> 2
}

internal fun AutoTransferRoute.depth(): Int = when (this) {
    AutoTransferRoute.Main -> 0
    AutoTransferRoute.TemplateManager,
    AutoTransferRoute.BindingManager,
    AutoTransferRoute.BatchApply,
    is AutoTransferRoute.GlobalReplySteps,
    is AutoTransferRoute.ContactPicker -> 1
    is AutoTransferRoute.TemplateEditor,
    is AutoTransferRoute.BindingEditor -> 2
}

internal fun MessageBlockRoute.depth(): Int = when (this) {
    MessageBlockRoute.Main -> 0
    MessageBlockRoute.TemplateManager,
    is MessageBlockRoute.DefaultRuleEditor,
    MessageBlockRoute.ListManager -> 1
    is MessageBlockRoute.TemplateEditor,
    is MessageBlockRoute.BindingEditor,
    is MessageBlockRoute.BatchBindingEditor,
    is MessageBlockRoute.ContactPicker,
    is MessageBlockRoute.GroupMemberPicker -> 2
}

internal fun GroupLeaveRoute.depth(): Int = when (this) {
    GroupLeaveRoute.Main -> 0
    GroupLeaveRoute.GroupManager,
    GroupLeaveRoute.TemplateManager,
    GroupLeaveRoute.BatchTemplateBinding,
    is GroupLeaveRoute.ContactPicker,
    is GroupLeaveRoute.FavoritePicker -> 1
    is GroupLeaveRoute.TemplateEditor,
    is GroupLeaveRoute.GroupEditor -> 2
}

internal fun ScheduledTaskRoute.depth(): Int = when (this) {
    ScheduledTaskRoute.Main -> 0
    ScheduledTaskRoute.Editor -> 1
    ScheduledTaskRoute.ContactPicker,
    is ScheduledTaskRoute.FavoritePicker -> 2
}

internal fun SelectedMessagesRoute.depth(): Int = when (this) {
    SelectedMessagesRoute.Main -> 0
    SelectedMessagesRoute.Editor -> 1
    SelectedMessagesRoute.ContactPicker,
    is SelectedMessagesRoute.FavoritePicker -> 2
}

internal fun AudioTransformRoute.depth(): Int = when (this) {
    AudioTransformRoute.Main -> 0
    AudioTransformRoute.ContactPicker -> 1
}

internal fun CustomNotificationRoute.depth(): Int = when (this) {
    CustomNotificationRoute.Main -> 0
    CustomNotificationRoute.RuleList,
    is CustomNotificationRoute.DefaultEditor,
    is CustomNotificationRoute.ContactPicker -> 1
    CustomNotificationRoute.BatchEditor,
    is CustomNotificationRoute.RuleEditor,
    is CustomNotificationRoute.GroupMemberPicker -> 2
}

internal fun KeywordNotificationRoute.depth(): Int = when (this) {
    KeywordNotificationRoute.Main -> 0
    KeywordNotificationRoute.KeywordList,
    KeywordNotificationRoute.TemplateEditor,
    is KeywordNotificationRoute.ContactPicker -> 1
    is KeywordNotificationRoute.KeywordEditor -> 2
}

internal fun TextSpeechRoute.depth(): Int = when (this) {
    TextSpeechRoute.Main -> 0
    is TextSpeechRoute.ContactPicker -> 1
}

internal fun autoReplyRouteDepth(route: String): Int = when (route) {
    "main" -> 0
    "rules",
    "autoAccept",
    "greetAccepted",
    "ai" -> 1
    "ruleEditor",
    "steps",
    "autoAcceptLabels",
    "greetAcceptedLabels" -> 2
    else -> 0
}

internal fun AutoReplyEditorRoute.depth(): Int = when (this) {
    AutoReplyEditorRoute.Main -> 0
    is AutoReplyEditorRoute.OptionPicker,
    is AutoReplyEditorRoute.ContactPicker,
    is AutoReplyEditorRoute.GroupMemberPicker -> 1
}

internal fun AutoReplyStepsRoute.depth(): Int = when (this) {
    AutoReplyStepsRoute.Main -> 0
    is AutoReplyStepsRoute.OptionPicker,
    is AutoReplyStepsRoute.ContactPicker,
    is AutoReplyStepsRoute.FavoritePicker -> 1
}

internal fun AutoReplyAiRoute.depth(): Int = when (this) {
    AutoReplyAiRoute.Main -> 0
    AutoReplyAiRoute.Xiaozhi,
    AutoReplyAiRoute.Zhilia -> 1
    AutoReplyAiRoute.ZhiliaConfigs,
    AutoReplyAiRoute.ZhiliaModels,
    is AutoReplyAiRoute.OptionPicker -> 2
}

internal fun RedPacketReplyStepsRoute.depth(): Int = when (this) {
    RedPacketReplyStepsRoute.Main -> 0
    is RedPacketReplyStepsRoute.StepEditor -> 1
    is RedPacketReplyStepsRoute.FavoritePicker -> 2
}

internal fun MessageBlockContactPickerRoute.depth(): Int = when (this) {
    MessageBlockContactPickerRoute.Labels -> 0
    is MessageBlockContactPickerRoute.Contacts -> if (label == null) 0 else 1
}

internal fun GroupMemberPickerRoute.depth(): Int = when (this) {
    GroupMemberPickerRoute.Groups -> 0
    is GroupMemberPickerRoute.Members -> 1
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun <T> SettingsRouteTransition(
    targetState: T,
    modifier: Modifier = Modifier.fillMaxSize(),
    label: String,
    depthOf: (T) -> Int,
    horizontalOnSameDepth: Boolean = true,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            val initialDepth = depthOf(initialState)
            val targetDepth = depthOf(targetState)
            if (!horizontalOnSameDepth && initialDepth == targetDepth) {
                fadeIn(animationSpec = tween(120)) togetherWith
                    fadeOut(animationSpec = tween(90))
            } else {
                val forward = targetDepth >= initialDepth
                val enter = slideInHorizontally(
                    animationSpec = tween(240),
                    initialOffsetX = { width -> if (forward) width else -width / 4 }
                ) + fadeIn(animationSpec = tween(160))
                val exit = slideOutHorizontally(
                    animationSpec = tween(220),
                    targetOffsetX = { width -> if (forward) -width / 4 else width }
                ) + fadeOut(animationSpec = tween(140))
                enter togetherWith exit
            }
        },
        modifier = modifier.background(MiuixTheme.colorScheme.background),
        label = label
    ) { route ->
        content(route)
    }
}

@Composable
internal fun MainNavigationBar(
    selectedTab: MainTab,
    floating: Boolean,
    glass: Boolean,
    backdrop: Backdrop,
    onSelected: (MainTab) -> Unit
) {
    val items = remember {
        listOf(
            MainNavItem(MainTab.PRACTICAL, "实用", NavIcons.Practical),
            MainNavItem(MainTab.ENTERTAINMENT, "娱乐", NavIcons.Entertainment),
            MainNavItem(MainTab.PLUGIN, "插件", NavIcons.Modules),
            MainNavItem(MainTab.SETTINGS, "设置", NavIcons.Settings)
        )
    }
    val selectedIndex = items.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
    val contentColor = MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (floating) 12.dp else 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingBottomBar(
            selectedIndex = selectedIndex,
            onSelected = { index -> onSelected(items[index].tab) },
            backdrop = backdrop,
            tabsCount = items.size,
            isBlurEnabled = glass,
            floating = floating
        ) { selectTab ->
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                FloatingBottomBarItem(
                    onClick = { selectTab(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                ) {
                    Image(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        colorFilter = ColorFilter.tint(contentColor),
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = item.label,
                        color = contentColor,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
internal fun FeatureSettingsPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit,
    onOpenScriptPluginAgent: () -> Unit = {},
    onOpenScriptPluginMarket: () -> Unit = {},
    onOpenScriptPluginManager: () -> Unit = {}
) {
    when (provider.featureId()) {
        AutoRedPacketFeature.ID -> RedPacketMiuixPage(context, provider, onBack)
        AutoTransferFeature.ID -> AutoTransferMiuixPage(context, provider, onBack)
        FakeWalletBalanceFeature.ID -> FakeWalletBalanceMiuixPage(context, provider, onBack)
        RealNameTailFeature.ID -> RealNameTailMiuixPage(context, provider, onBack)
        GroupNicknameColorSettings.FEATURE_ID -> GroupNicknameColorMiuixPage(context, provider, onBack)
        MemberTitleFeature.ID -> MemberTitleMiuixPage(context, provider, onBack)
        AntiRecallFeature.ID -> AntiRecallMiuixPage(context, provider, onBack)
        MultiRecallFeature.ID -> MultiRecallMiuixPage(context, provider, onBack)
        MessageForwardFeature.ID -> MessageForwardMiuixPage(context, provider, onBack)
        SelectedMessagesFeature.ID -> SelectedMessagesMiuixPage(context, provider, onBack)
        CallMediaLimitFeature.ID -> CallMediaLimitMiuixPage(context, provider, onBack)
        CallRingtoneBlockFeature.ID -> CallRingtoneBlockMiuixPage(context, provider, onBack)
        DisableHotUpdateFeature.ID -> DisableHotUpdateMiuixPage(context, provider, onBack)
        ProfileIdFeature.ID -> ProfileIdMiuixPage(context, provider, onBack)
        ProfileGroupInfoSettingsProvider.ID -> ProfileGroupInfoMiuixPage(context, provider, onBack)
        QuickContactEditFeature.ID -> QuickContactEditMiuixPage(context, provider, onBack)
        QuickGroupChatLabelFeature.ID -> QuickGroupChatLabelMiuixPage(context, provider, onBack)
        QuickMomentsFeature.ID -> QuickMomentsMiuixPage(context, provider, onBack)
        QuickTerminateFeature.ID -> QuickTerminateMiuixPage(context, provider, onBack)
        HideChatMenuFeature.ID -> HideChatMenuMiuixPage(context, provider, onBack)
        MessageAffixFeature.ID -> MessageAffixMiuixPage(context, provider, onBack)
        TypingReportBlockFeature.ID -> TypingReportBlockMiuixPage(context, provider, onBack)
        PatBlockFeature.ID -> PatBlockMiuixPage(context, provider, onBack)
        AutoOriginalImageFeature.ID -> AutoOriginalImageMiuixPage(context, provider, onBack)
        AutoViewOriginalFeature.ID -> AutoViewOriginalMiuixPage(context, provider, onBack)
        RemoveForwardLimitFeature.ID -> RemoveForwardLimitMiuixPage(context, provider, onBack)
        AtAllNotificationBlockFeature.ID -> AtAllNotificationBlockMiuixPage(context, provider, onBack)
        SettingsFeature.ID -> SettingsEntryMiuixPage(context, provider, onBack)
        PluginAgentEntryProvider.ID -> PluginAgentEntryMiuixPage(context, provider, onBack)
        SpecialMessageFeature.ID -> SpecialMessageMiuixPage(context, onBack)
        FloatingShortcutFeature.ID -> FloatingShortcutMiuixPage(context, provider, onBack)
        FloatingChatHeaderFeature.ID -> FloatingChatToggleMiuixPage(context, provider, onBack)
        FloatingChatFooterFeature.ID -> FloatingChatToggleMiuixPage(context, provider, onBack)
        MessageDetailsSettingsProvider.FEATURE_ID -> MessageDetailsConfigPage(
            context = context,
            sp = HchatStorage.preferences(context, HchatExtraSettings.PREFS_NAME),
            onBack = onBack
        )
        GroupMemberHistorySettingsProvider.FEATURE_ID -> HchatExtraToggleMiuixPage(
            context = context,
            provider = provider,
            key = HchatExtraSettings.KEY_GROUP_MEMBER_HISTORY,
            defaultValue = HchatExtraSettings.DEFAULT_GROUP_MEMBER_HISTORY,
            summary = "在群成员资料页添加历史发言记录入口",
            onBack = onBack
        )
        RedPacketDetailsSettingsProvider.FEATURE_ID -> HchatExtraToggleMiuixPage(
            context = context,
            provider = provider,
            key = HchatExtraSettings.KEY_RED_PACKET_DETAILS,
            defaultValue = HchatExtraSettings.DEFAULT_RED_PACKET_DETAILS,
            summary = "红包详情页显示金额、个数和领取时间",
            onBack = onBack
        )
        SkipWebRiskSettingsProvider.FEATURE_ID -> HchatExtraToggleMiuixPage(
            context = context,
            provider = provider,
            key = HchatExtraSettings.KEY_SKIP_WEB_RISK,
            defaultValue = HchatExtraSettings.DEFAULT_SKIP_WEB_RISK,
            summary = "跳过微信 WebView 高风险网页拦截提示",
            onBack = onBack
        )
        QuickMarkReadFeature.ID -> QuickMarkReadMiuixPage(context, provider, onBack)
        AutoReplyFeature.ID -> AutoReplyMiuixPage(context, provider, onBack)
        AutoMessageForwardFeature.ID -> AutoMessageForwardMiuixPage(context, provider, onBack)
        ConversationGroupFeature.ID -> ConversationGroupMiuixPage(context, provider, onBack)
        CustomNotificationFeature.ID -> CustomNotificationMiuixPage(context, provider, onBack)
        KeywordNotificationFeature.ID -> KeywordNotificationMiuixPage(context, provider, onBack)
        TextSpeechFeature.ID -> TextSpeechMiuixPage(context, provider, onBack)
        ZombieCheckFeature.ID -> ZombieCheckMiuixPage(context, provider, onBack)
        QQMusicOrderFeature.ID -> QQMusicOrderMiuixPage(context, provider, onBack)
        WeChatKeepAliveFeature.ID -> WeChatKeepAliveMiuixPage(context, provider, onBack)
        QuoteDeleteClearFeature.ID -> QuoteDeleteClearMiuixPage(context, provider, onBack)
        EmojiSaveFeature.ID -> EmojiSaveMiuixPage(context, provider, onBack)
        SwipeQuoteFeature.ID -> SwipeQuoteMiuixPage(context, provider, onBack)
        AudioTransformFeature.ID -> AudioTransformSettingsPage(context, provider, onBack)
        TextVoiceFeature.ID -> TextVoiceMiuixPage(context, provider, onBack)
        FakeVoiceDurationFeature.ID -> FakeVoiceDurationMiuixPage(context, provider, onBack)
        VoicePreviewFeature.ID -> VoicePreviewMiuixPage(context, provider, onBack)
        FakeMiniProgramBaseLibFeature.ID -> FakeMiniProgramBaseLibMiuixPage(context, provider, onBack)
        SkipMiniProgramVideoAdsFeature.ID -> SkipMiniProgramVideoAdsMiuixPage(context, provider, onBack)
        SkipGlobalMiniProgramSplashAdsFeature.ID -> SkipGlobalMiniProgramSplashAdsMiuixPage(context, provider, onBack)
        FakeLocationFeature.ID -> FakeLocationMiuixPage(context, provider, onBack)
        GameEmojiFeature.ID -> GameEmojiMiuixPage(context, provider, onBack)
        MessageBubbleFeature.ID -> MessageBubbleMiuixPage(context, provider, onBack)
        BackgroundBeautyFeature.ID -> BackgroundBeautyMiuixPage(context, provider, onBack)
        MessageTextColorFeature.ID -> MessageTextColorMiuixPage(context, provider, onBack)
        HomeTextColorFeature.ID -> HomeTextColorMiuixPage(context, provider, onBack)
        ChatTimeStyleFeature.ID -> ChatTimeStyleMiuixPage(context, provider, onBack)
        ChatToolbarFeature.ID -> ChatToolbarMiuixPage(context, provider, onBack)
        HomeSidePanelFeature.ID -> HomeSidePanelMiuixPage(context, provider, onBack)
        MonetModuleGeneratorFeature.ID -> MonetModuleGeneratorMiuixPage(context, provider, onBack)
        SecureMessageSettings.SEND_ID,
        SecureMessageSettings.ANTI_ID -> SecureMessageMiuixPage(context, provider, onBack)
        UploadTransparentAvatarFeature.ID -> UploadTransparentAvatarMiuixPage(context, provider, onBack)
        InputHintFeature.ID -> InputHintMiuixPage(context, provider, onBack)
        HideChatAvatarFeature.ID -> HideChatAvatarMiuixPage(context, provider, onBack)
        CustomBottomBarFeature.ID -> CustomBottomBarMiuixPage(context, provider, onBack)
        FloatingBottomBarSettings.FEATURE_ID -> FloatingBottomBarMiuixPage(context, provider, onBack)
        RoundAvatarFeature.ID -> RoundAvatarMiuixPage(context, provider, onBack)
        CustomFriendAvatarFeature.ID -> CustomFriendAvatarMiuixPage(context, provider, onBack)
        MessageBlockFeature.ID -> MessageBlockMiuixPage(context, provider, onBack)
        GroupChatLabelFeature.ID -> GroupChatLabelMiuixPage(context, provider, onBack)
        GroupLeaveMonitorFeature.ID -> GroupLeaveMonitorMiuixPage(context, provider, onBack)
        GroupRenameMonitorFeature.ID -> GroupRenameMonitorMiuixPage(context, provider, onBack)
        MomentsAutoLikeFeature.ID -> MomentsAutoLikeMiuixPage(context, provider, onBack)
        MomentsAutoCommentFeature.ID -> MomentsAutoCommentPage(context, provider, onBack)
        MomentsAutoForwardFeature.ID -> MomentsAutoForwardMiuixPage(context, provider, onBack)
        MomentsAutoRefreshFeature.ID -> MomentsAutoRefreshMiuixPage(context, provider, onBack)
        MomentsContactFilterFeature.ID -> MomentsContactFilterMiuixPage(context, provider, onBack)
        MomentsKeywordBlockFeature.ID -> MomentsKeywordBlockMiuixPage(context, provider, onBack)
        MomentsBottomDetailFeature.ID -> MomentsBottomDetailMiuixPage(context, provider, onBack)
        MomentsPostNotificationFeature.ID -> MomentsPostNotificationMiuixPage(context, provider, onBack)
        OriginalMomentsUploadFeature.ID -> OriginalMomentsUploadMiuixPage(context, provider, onBack)
        MomentsUploadTailFeature.ID -> MomentsUploadTailMiuixPage(context, provider, onBack)
        SnsAntiRecallFeature.ID -> SnsAntiRecallMiuixPage(context, provider, onBack)
        MomentsFakeLikeSettingsProvider.ID,
        MomentsFakeCommentSettingsProvider.ID,
        MomentsFakeForwardSettingsProvider.ID -> MomentsFakeInteractionMiuixPage(context, provider, onBack)
        RemoveMomentsAdsFeature.ID -> RemoveMomentsAdsMiuixPage(context, provider, onBack)
        EditMessageFeature.ID -> EditMessageMiuixPage(context, provider, onBack)
        VoiceForwardFeature.ID -> VoiceForwardMiuixPage(context, provider, onBack)
        ScheduledTaskFeature.ID -> ScheduledTaskMiuixPage(context, provider, onBack)
        FakeScanCameraFeature.ID -> FakeScanCameraMiuixPage(context, provider, onBack)
        FinderMediaDownloadFeature.ID -> FinderMediaDownloadMiuixPage(context, provider, onBack)
        ProtobufPacketFeature.ID -> ProtobufPacketMiuixPage(context, provider, onBack)
        CrashReportSettingsProvider.ID -> CrashReportMiuixPage(context, provider, onBack)
        StatusTextLimitFeature.ID -> StatusTextLimitMiuixPage(context, provider, onBack)
        ScriptPluginFeature.ID -> ScriptPluginMiuixPage(
            context,
            provider,
            onBack,
            onOpenScriptPluginMarket,
            onOpenScriptPluginAgent,
            onOpenScriptPluginManager
        )
        WeChatTabletFeature.ID -> WeChatTabletMiuixPage(context, provider, onBack)
        else -> UnsupportedFeaturePage(provider, onBack)
    }
}

@Composable
internal fun MultiRecallMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MultiRecallSettings.PREFS_NAME) }
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
            item { SmallTitle(text = "聊天消息") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        MultiRecallSettings.KEY_ENABLE,
                        "多选撤回",
                        "在多选消息的分享菜单中显示批量撤回",
                        MultiRecallSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}

@Composable
internal fun SelectedMessagesMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, SelectedMessagesSettings.PREFS_NAME) }
    var route by remember { mutableStateOf<SelectedMessagesRoute>(SelectedMessagesRoute.Main) }
    var draft by remember {
        mutableStateOf(
            ScheduledTaskSettings.newDraft().copy(
                type = ScheduledTaskSettings.TYPE_IMAGE,
                items = listOf(ScheduledTaskContentItem(ScheduledTaskSettings.TYPE_IMAGE, "")),
                intervalSeconds = SelectedMessagesSettings.sendIntervalSeconds(context),
                mediaIntervalSeconds = SelectedMessagesSettings.sendIntervalSeconds(context)
            )
        )
    }
    var channel by remember { mutableStateOf(SelectedMessagesRuntimeCoordinator.CHANNEL_MODULE) }
    val mainListState = rememberLazyListState()
    val editorListState = rememberLazyListState()

    fun validationError(): String? {
        return SelectedMessagesRuntimeCoordinator.validationError(
            channel,
            scheduledTaskEditableItems(draft)
        )
    }

    fun openContactPicker() {
        validationError()?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            return
        }
        route = SelectedMessagesRoute.ContactPicker
    }

    fun startSend() {
        val error = validationError() ?: if (draft.targetIds.isEmpty()) "请选择群发对象" else null
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            Toast.makeText(context, "当前页面无法启动群发", Toast.LENGTH_SHORT).show()
            return
        }
        SelectedMessagesRuntimeCoordinator.sendCustom(
            activity = activity,
            channel = channel,
            items = scheduledTaskEditableItems(draft),
            targetIds = draft.targetIds,
            targetIntervalSeconds = draft.intervalSeconds,
            itemIntervalSeconds = draft.mediaIntervalSeconds
        )
    }

    SettingsRouteTransition(
        targetState = route,
        label = "SelectedMessagesRoute",
        depthOf = { it.depth() }
    ) { currentRoute ->
        when (currentRoute) {
            SelectedMessagesRoute.Main -> {
                SelectedMessagesMainPage(
                    provider = provider,
                    sp = sp,
                    listState = mainListState,
                    onBack = onBack,
                    onCompose = { route = SelectedMessagesRoute.Editor }
                )
            }
            SelectedMessagesRoute.Editor -> {
                SelectedMessagesCustomEditorPage(
                    draft = draft,
                    channel = channel,
                    listState = editorListState,
                    onBack = { route = SelectedMessagesRoute.Main },
                    onDraftChange = { draft = it },
                    onChannelChange = { next ->
                        if (next != channel) {
                            channel = next
                            draft = draft.copy(targetIds = emptyList())
                        }
                    },
                    onPickTargets = ::openContactPicker,
                    onPickFiles = { itemIndex, itemType ->
                        val activity = context as? Activity
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法打开文件选择器", Toast.LENGTH_SHORT).show()
                        } else {
                            ScheduledTaskFilePickerBridge.launch(activity, itemType) { paths ->
                                draft = updateScheduledTaskMediaItem(draft, itemIndex, itemType, paths)
                            }
                        }
                    },
                    onPickFavorite = { itemIndex ->
                        scheduledTaskEditableItems(draft).getOrNull(itemIndex)?.let { item ->
                            route = SelectedMessagesRoute.FavoritePicker(
                                FavoritePickerRequest(
                                    title = scheduledTaskPickerTitle(item.type),
                                    existingValue = item.value,
                                    onValue = { value ->
                                        draft = updateScheduledTaskContentItemValue(draft, itemIndex, value)
                                    },
                                    multiSelect = false,
                                    delimiter = "|"
                                )
                            )
                        }
                    },
                    onSend = ::startSend
                )
            }
            SelectedMessagesRoute.ContactPicker -> {
                val official = channel == SelectedMessagesRuntimeCoordinator.CHANNEL_OFFICIAL
                ContactPickerPage(
                    context = context,
                    request = ContactPickerRequest(
                        title = if (official) "选择官方群发好友" else "选择群发对象",
                        mode = if (official) ContactPickerMode.FRIENDS else ContactPickerMode.ALL_CHATS,
                        multiSelect = true,
                        existingValue = formatIds(draft.targetIds),
                        onValue = {},
                        enableLabels = true
                    ),
                    onBack = { route = SelectedMessagesRoute.Editor },
                    onConfirm = { selected ->
                        draft = draft.copy(targetIds = selected.map { it.id })
                        route = SelectedMessagesRoute.Editor
                    }
                )
            }
            is SelectedMessagesRoute.FavoritePicker -> {
                FavoritePickerPage(
                    request = currentRoute.request,
                    onBack = { route = SelectedMessagesRoute.Editor }
                )
            }
        }
    }
}

@Composable
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
