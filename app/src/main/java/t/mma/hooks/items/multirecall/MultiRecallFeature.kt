package t.mma.hooks.items.multirecall

import android.app.Activity
import android.content.Context
import android.view.MenuItem
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.message.MultiSelectMessageMenuLocator
import t.mma.hooks.api.message.MultiSelectMessageResolver
import t.mma.hooks.api.message.MultiSelectMessageUi
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import java.lang.reflect.Method

/** 多选撤回：一次启用周期一个运行时，独占安装票根、DexReady 订阅与全部 Hook 句柄。 */
class MultiRecallFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "多选撤回"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MultiRecallSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), MultiRecallSettings.PREFS_NAME)
        val runtime = PreferenceRuntimeOwner(
            prefs, setOf(MultiRecallSettings.KEY_ENABLE),
            enabled = { isEnabled(context.hostContext()) },
            create = { resources ->
                val owner = MultiRecallRuntime(context, ::logFeatureError, ::logInfo)
                // 先登记释放再装 Hook：create 中途抛错时已安装的句柄仍归本代所有。
                resources.register { owner.close() }
                val installation = DexInstallScheduler.installation(
                    ID, name(), stage = DexInstallScheduler.Stage.BRIDGE
                )
                resources.register { installation.close() }
                installation.schedule { owner.install() }
                val subscription = context.eventBus().subscribe(Events.DexReady::class.java, {
                    if (!resources.isClosed()) installation.schedule { owner.install() }
                })
                resources.register { context.eventBus().unsubscribe(subscription) }
            },
            onError = { logFeatureError("多选撤回启停失败", it) }
        )
        trackResource { runtime.close() }
        runtime.start()
    }

    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "multi_recall"

        /** 设置读取入口：安装期与每组回调都用同一判定，不缓存布尔。 */
        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            return HchatStorage.preferences(context, MultiRecallSettings.PREFS_NAME)
                .getBoolean(MultiRecallSettings.KEY_ENABLE, MultiRecallSettings.DEFAULT_ENABLE)
        }
    }
}

/**
 * 关闭只拒绝新工作：已提交的宿主撤回不会被打断，但不会再排入新的撤回请求，
 * 也不会再弹出迟到的提示或退出多选状态。
 */
private class MultiRecallRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit,
    private val info: (String) -> Unit
) : AutoCloseable {
    private val lock = Any()
    private val hooks = ArrayList<XC_MethodHook.Unhook>()
    private val installedMembers = LinkedHashSet<String>()
    private var createInstalled = false
    private var clickInstalled = false
    private var createMissingLogged = false
    private var clickMissingLogged = false
    private var disabledHitLogged = false
    private var menuAddedLogged = false
    @Volatile private var closed = false

    private fun report(message: String, error: Throwable?) {
        runCatching { logger(message, error) }
    }

    private fun log(message: String) {
        runCatching { info(message) }
    }

    fun isClosed(): Boolean = closed

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        createInstalled = false
        clickInstalled = false
        val pending = synchronized(lock) { hooks.toList().also { hooks.clear() } }
        pending.asReversed().forEach { unhook ->
            runCatching { HookRegistry.get().unhook(unhook) }
                .onFailure { report("多选撤回Hook释放失败", it) }
        }
        synchronized(lock) { installedMembers.clear() }
    }

    /** 返回本次两个入口是否都可用；失败保留票根，由调度器按退避重试。 */
    @Synchronized
    fun install(): Boolean {
        if (closed) return false
        val createMethod = MultiSelectMessageMenuLocator.menuCreateMethod(context) { message, error ->
            report(message, error)
        }
        val clickMethod = MultiSelectMessageMenuLocator.menuClickMethod(context) { message, error ->
            report(message, error)
        }
        val exitMethod = clickMethod?.let { method ->
            MultiSelectMessageMenuLocator.multiSelectExitMethod(context, method) { message, error ->
                report(message, error)
            }
        }
        // 两个入口各自按所需描述符判定：任一入口缺描述符不拖累另一个入口。
        val createReady = when {
            createInstalled -> true
            createMethod == null -> false
            else -> installCreateHook(createMethod)
        }
        val clickReady = when {
            clickInstalled -> true
            clickMethod == null || exitMethod == null -> false
            else -> installClickHook(clickMethod, exitMethod)
        }
        if (!createReady && !createMissingLogged) {
            createMissingLogged = true
            report("多选撤回菜单创建Hook未安装", null)
        }
        if (!clickReady && !clickMissingLogged) {
            clickMissingLogged = true
            report("多选撤回菜单点击Hook未安装", null)
        }
        return createReady && clickReady
    }

    /** 同一成员只装一次；句柄归本代所有，关闭与安装竞争时立即回滚新句柄。 */
    private fun hookOnce(method: Method, callback: XC_MethodHook): Boolean {
        val memberKey = method.toGenericString()
        synchronized(lock) {
            if (closed) return false
            if (!installedMembers.add(memberKey)) return true
        }
        val unhook = runCatching { HookRegistry.get().hook(method, callback) }.getOrElse {
            synchronized(lock) { installedMembers.remove(memberKey) }
            report("多选撤回Hook安装失败: $memberKey", it)
            return false
        }
        synchronized(lock) {
            if (!closed) {
                hooks.add(unhook)
                return true
            }
        }
        runCatching { HookRegistry.get().unhook(unhook) }
            .onFailure { report("多选撤回Hook释放失败", it) }
        synchronized(lock) { installedMembers.remove(memberKey) }
        return false
    }

    private fun installCreateHook(method: Method): Boolean {
        if (closed) return false
        val ready = hookOnce(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                addRecallMenu(param)
            }
        })
        createInstalled = ready
        if (ready) log("多选撤回菜单创建Hook已安装: ${method.toGenericString()}")
        return ready
    }

    private fun installClickHook(method: Method, exitMethod: Method): Boolean {
        if (closed) return false
        val ready = hookOnce(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                handleRecallMenuClick(param, exitMethod)
            }
        })
        clickInstalled = ready
        if (ready) log("多选撤回菜单点击Hook已安装: ${method.toGenericString()}")
        return ready
    }

    private fun addRecallMenu(param: XC_MethodHook.MethodHookParam) {
        // 关闭后不再往菜单里加自定义项。
        if (closed) return
        if (!enabled()) {
            logDisabledHit()
            return
        }
        val selected = MultiSelectMessageResolver.resolve(param.thisObject)
        if (recallMessages(selected) == null) return
        val menu = param.args?.getOrNull(0) ?: return
        addMenuItem(menu)
    }

    private fun handleRecallMenuClick(param: XC_MethodHook.MethodHookParam, exitMethod: Method) {
        // 关闭后不再拦截点击，交回宿主原生处理。
        if (closed) return
        if (!enabled()) {
            logDisabledHit()
            return
        }
        val menuItem = param.args?.getOrNull(0) as? MenuItem ?: return
        if (menuItem.itemId != MENU_ITEM_ID) return
        // 命中本功能菜单项后接管本次点击，后续任何分支都不再交回宿主。
        param.result = null
        val activity = currentActivity()
        val selected = MultiSelectMessageResolver.resolve(param.thisObject)
        val messages = recallMessages(selected)
        if (messages == null) {
            toast(activity, if (selected.isEmpty()) "未找到选中的消息" else "只能批量撤回自己发送的消息")
            return
        }
        val exitTarget = MultiSelectMessageUi.resolveExitTarget(
            param.thisObject, exitMethod
        ) { message, error -> report(message, error) }
        if (exitTarget == null) {
            toast(activity, "无法退出多选状态，请稍后重试")
            return
        }
        val sender = WeChatApis.message().sender()
        var attempted = 0
        var success = 0
        for (message in messages) {
            // 关闭后不再提交新的撤回；已提交的原生撤回不会被打断。
            if (closed) break
            attempted++
            val revoked = runCatching { sender?.revokeNative(message) == true }
                .onFailure { report("批量撤回消息异常: msgId=${messageId(message)}", it) }
                .getOrDefault(false)
            if (revoked) success++
        }
        // 关闭后跳过迟到提示与退出：宿主状态已不受本代控制。
        if (closed) return
        val text = if (attempted < messages.size) {
            "已发起撤回 $success/$attempted 条消息，其余未提交"
        } else {
            "已发起撤回 $success/${messages.size} 条消息"
        }
        toast(activity, text)
        exitTarget.exit { message, error -> report(message, error) }
    }

    private fun enabled(): Boolean = MultiRecallFeature.isEnabled(context.hostContext())

    private fun logDisabledHit() {
        if (disabledHitLogged) return
        disabledHitLogged = true
        log("多选撤回回调已触发，但功能开关已关闭")
    }

    private fun recallMessages(messages: List<Any>): List<Any>? {
        if (messages.isEmpty()) return null
        val ids = messages.map { message ->
            messageId(message).takeIf { it > 0L && isOutgoing(message, it) } ?: return null
        }
        return messages.takeIf { ids.distinct().size == messages.size }
    }

    private fun addMenuItem(menu: Any) {
        if (KavaReflector.invokeMethod(menu, "findItem", MENU_ITEM_ID) != null) return
        val added = KavaReflector.invokeMethod(menu, "add", 0, MENU_ITEM_ID, 0, MENU_TITLE)
            ?: KavaReflector.invokeMethod(menu, "add", 0, MENU_ITEM_ID, 0, MENU_TITLE as CharSequence)
        if (added != null) {
            if (!menuAddedLogged) {
                menuAddedLogged = true
                log("多选撤回菜单项已加入")
            }
            return
        }
        KavaReflector.invokeMethod(menu, "f", MENU_ITEM_ID, MENU_TITLE)
            ?: KavaReflector.invokeMethod(menu, "f", MENU_ITEM_ID, MENU_TITLE as CharSequence)
    }

    private fun messageId(message: Any): Long {
        for (name in arrayOf("getMsgId", "getMsgID", "getId")) {
            (KavaReflector.invokeMethod(message, name) as? Number)?.toLong()
                ?.takeIf { it > 0L }?.let { return it }
        }
        for (name in arrayOf("field_msgId", "msgId", "msgID", "id")) {
            (KavaReflector.readField(message, name) as? Number)?.toLong()
                ?.takeIf { it > 0L }?.let { return it }
        }
        return 0L
    }

    private fun isOutgoing(message: Any, messageId: Long): Boolean {
        for (name in arrayOf("field_isSend", "isSend")) {
            when (val value = KavaReflector.readField(message, name)) {
                is Boolean -> return value
                is Number -> return value.toInt() != 0
            }
        }
        for (name in arrayOf("getIsSend", "isSend")) {
            when (val value = KavaReflector.invokeMethod(message, name)) {
                is Boolean -> return value
                is Number -> return value.toInt() != 0
            }
        }
        return WeChatApis.message().store()?.getMessageById(messageId)?.isSend == 1
    }

    private fun currentActivity(): Activity? {
        return (WeChatApis.currentActivity()?.currentActivity() as? Activity)
            ?.takeUnless { it.isFinishing }
    }

    private fun toast(activity: Activity?, text: String) {
        val target = activity ?: currentActivity() ?: return
        // 提示走 UI 线程；关闭后到达的迟到回调不再弹窗。
        target.runOnUiThread {
            if (closed) return@runOnUiThread
            Toast.makeText(target, text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val MENU_ITEM_ID = 0x48435243
        private const val MENU_TITLE = "批量撤回[H]"
    }
}
