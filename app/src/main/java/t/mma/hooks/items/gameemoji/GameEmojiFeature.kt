package t.mma.hooks.items.gameemoji

import android.app.Activity
import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.hooks.core.HookRegistry
import t.mma.ui.miuix.VoiceForwardMiuixDialog
import t.mma.utils.KavaReflector
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

class GameEmojiFeature : BaseFeature() {
    override fun featureId(): String = ID
    override fun name(): String = "指定骰子猜拳"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(GameEmojiSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), GameEmojiSettings.PREFS_NAME)
        val runtime = PreferenceRuntimeOwner(
            prefs, setOf(GameEmojiSettings.KEY_FIXED_RESULT, GameEmojiSettings.KEY_PICK_BEFORE_SEND),
            enabled = { GameEmojiSettings.fixedResultEnabled(context.hostContext()) ||
                GameEmojiSettings.pickBeforeSendEnabled(context.hostContext()) },
            create = { resources ->
                val owner = GameEmojiRuntime(context, ::logRuntimeError)
                resources.register { owner.close() }
                val installation = DexInstallScheduler.installation(ID, name(), stage = DexInstallScheduler.Stage.WARMUP)
                resources.register { installation.close() }
                installation.schedule { owner.install() }
                val subscription = context.eventBus().subscribe(Events.DexReady::class.java, {
                    if (!resources.isClosed()) installation.schedule { owner.install() }
                })
                resources.register { context.eventBus().unsubscribe(subscription) }
            }, onError = { logRuntimeError("游戏表情启停失败", it) },
            // 每次发送都会重新读取两个开关，模式切换无需重建 Hook。
            // 重建会留下无 Hook 窗口并取消正在进行的 Dex 安装重试，属于净开销。
            restartOnChange = false
        )
        trackResource { runtime.close() }
        runtime.start()
    }
    private fun logRuntimeError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "game_emoji_result"
    }
}

private class GameEmojiRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val replaying = ThreadLocal<Boolean>()
    private val dialogShowing = AtomicBoolean(false)
    private var emojiFields: Map<String, Field> = emptyMap()

    private var closed = false
    private var installed = false
    private var hook: XC_MethodHook.Unhook? = null
    private var dialog: VoiceForwardMiuixDialog.DialogHandle? = null
    private var pendingSend: PendingSend? = null
    private fun report(message: String, error: Throwable?) {
        runCatching { logger(message, error) }
    }
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        installed = false
        val oldHook = hook
        hook = null
        val oldDialog = dialog
        dialog = null
        pendingSend?.clear()
        pendingSend = null
        emojiFields = emptyMap()
        dialogShowing.set(false)
        runCatching { mainHandler.removeCallbacksAndMessages(null) }
            .onFailure { report("游戏表情任务清理失败", it) }
        runCatching { HookRegistry.get().unhook(oldHook) }
            .onFailure { report("游戏表情 Hook 释放失败", it) }
        runCatching { oldDialog?.close() }
            .onFailure { report("游戏表情弹窗关闭失败", it) }
    }
    @Synchronized
    fun install(): Boolean {
        if (closed) return false
        if (installed) return true
        val method = context.dexFinder().emojiSendMethod ?: return false
        if (!isEmojiSendMethod(method)) return false
        val fields = resolveEmojiFields(method.parameterTypes[1]) ?: return false
        emojiFields = fields
        return runCatching {
            hook = HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // 重发路径由 replay 直接进入宿主方法，不再二次改写同一表情。
                    if (replaying.get() == true) return
                    // 只在取快照时持锁：设置读取、结果改写和宿主调用都不占用 runtime 监视器。
                    val fields = synchronized(this@GameEmojiRuntime) {
                        if (closed) return
                        emojiFields
                    }
                    val emojiInfo = param.args?.getOrNull(1) ?: return
                    val gameType = detectGameType(emojiInfo, fields) ?: return
                    when {
                        GameEmojiSettings.pickBeforeSendEnabled(context.hostContext()) -> {
                            interceptForChoice(method, param, gameType, fields)
                        }
                        GameEmojiSettings.fixedResultEnabled(context.hostContext()) -> {
                            val result = configuredResult(gameType)
                            if (!applyResult(emojiInfo, gameType, result, fields)) {
                                report("改写游戏表情结果失败", null)
                            }
                        }
                    }
                }
            })
            installed = true
            true
        }.getOrElse {
            emojiFields = emptyMap()
            report("游戏表情发送 Hook 安装失败", it)
            false
        }
    }

    private fun interceptForChoice(
        method: Method,
        param: XC_MethodHook.MethodHookParam,
        gameType: GameType,
        fields: Map<String, Field>
    ) {
        if (closed) return
        val activity = (WeChatApis.currentActivity()?.currentActivity() as? Activity)
            ?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        var created: PendingSend? = null
        synchronized(this) {
            if (closed) return
            if (!dialogShowing.compareAndSet(false, true)) {
                // 已有一次待选择在途；保持原有语义，避免弹出第二个并列弹窗。
                param.result = null
                return
            }
            pendingSend?.clear()
            val pending = PendingSend(method, param.thisObject, param.args.copyOf(), gameType, fields)
            pendingSend = pending
            created = pending
            param.result = null
        }
        val pending = created ?: return
        val posted = mainHandler.post {
            var fallback: PendingSend? = null
            synchronized(this@GameEmojiRuntime) {
                if (closed || pendingSend !== pending) {
                    // 已被关闭或换代认领，旧 payload 由持有者释放，这里不重复处理。
                    fallback = null
                } else if (activity.isFinishing || activity.isDestroyed) {
                    dialogShowing.set(false)
                    fallback = pending
                } else {
                    try {
                        val handle = VoiceForwardMiuixDialog.showChoices(
                            activity = activity,
                            title = if (gameType == GameType.DICE) "选择骰子点数" else "选择猜拳结果",
                            summary = "",
                            choices = resultsFor(gameType).map { it.label to "" },
                            onSelected = { index ->
                                resultsFor(gameType).getOrNull(index)?.let { replay(pending, it) }
                            },
                            onDismiss = {
                                synchronized(this@GameEmojiRuntime) {
                                    if (pendingSend === pending) {
                                        dialog = null
                                        dialogShowing.set(false)
                                        // showChoices 先 close 再回调 onSelected。同步选择已认领 payload，
                                        // 这里投递的取消只清理未被认领的旧 payload。
                                        if (!closed && !mainHandler.post { cancelPending(pending) }) {
                                            cancelPending(pending)
                                        }
                                    }
                                }
                            },
                            dispatchAfterClose = false
                        )
                        if (closed || pendingSend !== pending) {
                            handle.close()
                        } else if (handle.isShowing()) {
                            dialog = handle
                        } else {
                            dialogShowing.set(false)
                            fallback = pending
                        }
                    } catch (error: Throwable) {
                        dialogShowing.set(false)
                        cancelPending(pending)
                        report("游戏表情弹窗打开失败", error)
                    }
                }
            }
            // 宿主调用不持监视器，避免 close()/install() 与重发互相阻塞。
            fallback?.let { replay(it, null) }
        }
        if (!posted) {
            dialogShowing.set(false)
            replay(pending, null)
        }
    }
    @Synchronized
    private fun cancelPending(pending: PendingSend) {
        pending.clear()
        if (pendingSend === pending) pendingSend = null
    }
    /** 在锁内认领 payload 并清空引用；返回 null 表示已被关闭或已被他人认领。 */
    private fun claim(pending: PendingSend, selected: GameResult?): Claim? = synchronized(this) {
        if (closed || pendingSend !== pending) {
            pending.clear()
            null
        } else {
            val method = pending.method
            val args = pending.args
            val fields = pending.fields
            val receiver = pending.receiver
            if (method == null || args == null || fields == null) {
                pendingSend = null
                pending.clear()
                null
            } else {
                val gameType = pending.gameType
                pendingSend = null
                pending.clear()
                Claim(method, receiver, args, gameType, fields, selected)
            }
        }
    }
    private fun replay(pending: PendingSend, selected: GameResult?) {
        val claim = claim(pending, selected) ?: return
        val emojiInfo = claim.args.getOrNull(1) ?: return
        if (claim.selected != null && !applyResult(emojiInfo, claim.gameType, claim.selected.value, claim.fields)) {
            report("应用所选游戏表情结果失败", null)
            return
        }
        replaying.set(true)
        try {
            KavaReflector.invokeOrThrow(claim.method, claim.receiver, *claim.args)
        } catch (e: Throwable) {
            report("重新发送游戏表情失败", e)
        } finally {
            replaying.remove()
        }
    }

    private fun configuredResult(gameType: GameType): Int {
        return if (gameType == GameType.DICE) {
            GameEmojiSettings.diceResult(context.hostContext())
        } else {
            GameEmojiSettings.rpsResult(context.hostContext())
        }
    }

    private fun detectGameType(emojiInfo: Any, fields: Map<String, Field>): GameType? {
        val name = readString(emojiInfo, FIELD_NAME, fields)
        val content = readString(emojiInfo, FIELD_CONTENT, fields)
        val md5 = readString(emojiInfo, FIELD_MD5, fields)
        return when {
            name.startsWith("dice", ignoreCase = true) ||
                content.contains("type=\"2\"") || DICE_MD5.contains(md5) -> GameType.DICE
            name.startsWith("jsb", ignoreCase = true) ||
                content.contains("type=\"1\"") || RPS_MD5.contains(md5) -> GameType.RPS
            else -> null
        }
    }

    private fun applyResult(
        emojiInfo: Any,
        gameType: GameType,
        value: Int,
        fields: Map<String, Field>
    ): Boolean {
        val result = resultsFor(gameType).firstOrNull { it.value == value } ?: return false
        val contentValue = if (gameType == GameType.DICE) value + 3 else value
        val values = mapOf<String, Any?>(
            FIELD_MD5 to result.md5,
            FIELD_SIZE to result.size,
            FIELD_CONTENT to "<gameext type=\"${gameType.protocolType}\" content=\"$contentValue\" ></gameext>",
            FIELD_NAME to result.fileName,
            FIELD_SVR_ID to "",
            FIELD_CATALOG to 50,
            FIELD_RESERVED_3 to 0,
            FIELD_RESERVED_4 to 0,
            FIELD_GROUP_ID to "50",
            FIELD_SOURCE to 0,
            FIELD_DESIGNER_ID to null,
            FIELD_THUMB_URL to null
        )
        var success = true
        values.forEach { (name, fieldValue) ->
            success = KavaReflector.writeField(fields[name], emojiInfo, fieldValue) && success
        }
        return success
    }

    private fun readString(emojiInfo: Any, name: String, fields: Map<String, Field>): String {
        return KavaReflector.readField(fields[name], emojiInfo) as? String ?: ""
    }

    private fun resolveEmojiFields(emojiClass: Class<*>): Map<String, Field>? {
        val fields = LinkedHashMap<String, Field>()
        REQUIRED_FIELDS.forEach { name ->
            val field = KavaReflector.findFieldRecursive(emojiClass, name) ?: run {
                logger("EmojiInfo 缺少字段: $name", null)
                return null
            }
            fields[name] = field
        }
        return fields
    }

    private fun isEmojiSendMethod(method: Method): Boolean {
        val types = method.parameterTypes
        return method.returnType == java.lang.Void.TYPE &&
            types.size >= 4 &&
            types[0] == String::class.java &&
            types[1].name == "com.tencent.mm.storage.emotion.EmojiInfo"
    }

    private fun resultsFor(gameType: GameType): List<GameResult> {
        return if (gameType == GameType.DICE) DICE_RESULTS else RPS_RESULTS
    }

    /** 一次重发所需的最小快照；清空 PendingSend 后仍然自持。 */
    private class Claim(
        val method: Method,
        val receiver: Any?,
        val args: Array<Any?>,
        val gameType: GameType,
        val fields: Map<String, Field>,
        val selected: GameResult?
    )
    private class PendingSend(
        var method: Method?,
        var receiver: Any?,
        var args: Array<Any?>?,
        val gameType: GameType,
        var fields: Map<String, Field>?
    ) {
        fun clear() { method = null; receiver = null; args = null; fields = null }
    }
    private enum class GameType(val protocolType: Int) {
        RPS(1),
        DICE(2)
    }

    private data class GameResult(
        val value: Int,
        val label: String,
        val md5: String,
        val size: Int,
        val fileName: String
    )

    companion object {
        private const val FIELD_MD5 = "field_md5"
        private const val FIELD_SVR_ID = "field_svrid"
        private const val FIELD_CATALOG = "field_catalog"
        private const val FIELD_SIZE = "field_size"
        private const val FIELD_NAME = "field_name"
        private const val FIELD_CONTENT = "field_content"
        private const val FIELD_RESERVED_3 = "field_reserved3"
        private const val FIELD_RESERVED_4 = "field_reserved4"
        private const val FIELD_GROUP_ID = "field_groupId"
        private const val FIELD_SOURCE = "field_source"
        private const val FIELD_DESIGNER_ID = "field_designerID"
        private const val FIELD_THUMB_URL = "field_thumbUrl"

        private val REQUIRED_FIELDS = listOf(
            FIELD_MD5,
            FIELD_SVR_ID,
            FIELD_CATALOG,
            FIELD_SIZE,
            FIELD_NAME,
            FIELD_CONTENT,
            FIELD_RESERVED_3,
            FIELD_RESERVED_4,
            FIELD_GROUP_ID,
            FIELD_SOURCE,
            FIELD_DESIGNER_ID,
            FIELD_THUMB_URL
        )

        private val RPS_RESULTS = listOf(
            GameResult(1, "剪刀", "514914788fc461e7205bf0b6ba496c49", 2782, "jsb_j.png"),
            GameResult(2, "石头", "f790e342a02e0f99d34b316547f9aeab", 2278, "jsb_s.png"),
            GameResult(3, "布", "091577322c40c05aa3dd701da29d6423", 3612, "jsb_b.png")
        )
        private val DICE_RESULTS = listOf(
            GameResult(1, "1 点", "da1c289d4e363f3ce1ff36538903b92f", 2342, "dice_1.png"),
            GameResult(2, "2 点", "9e3f303561566dc9342a3ea41e6552a6", 2278, "dice_2.png"),
            GameResult(3, "3 点", "dbcc51db2765c1d0106290bae6326fc4", 2404, "dice_3.png"),
            GameResult(4, "4 点", "9a21c57defc4974ab5b7c842e3232671", 2422, "dice_4.png"),
            GameResult(5, "5 点", "3a8e16d650f7e66ba5516b2780512830", 2538, "dice_5.png"),
            GameResult(6, "6 点", "5ba8e9694b853df10b9f2a77b312cc09", 2536, "dice_6.png")
        )
        private val RPS_MD5 = RPS_RESULTS.mapTo(HashSet()) { it.md5 }
        private val DICE_MD5 = DICE_RESULTS.mapTo(HashSet()) { it.md5 }
    }
}
