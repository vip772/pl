package t.mma.hooks.items.securemessage

import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Method
import t.mma.dexkit.DexKitState
import t.mma.dexkit.DexMethodCache
import t.mma.dexkit.DexMethodData
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatVersionInfo
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.FakePrefs
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog

/** One loader per synthetic process; both features resolve host descriptors through it. */
object ProcessLoader {
    val INSTANCE: ClassLoader = object : ClassLoader(ProcessLoader::class.java.classLoader) {}
}

private var cachedContext: FeatureContext? = null

/** One context per scenario: the bus, finder and bridge state must survive a generation. */
fun context(): FeatureContext = cachedContext
    ?: FeatureContext(android.content.Context(), ProcessLoader.INSTANCE).also { cachedContext = it }

fun antiPrefs(): FakePrefs = HchatStorage.store(SecureMessageSettings.ANTI_PREFS)
fun sendPrefs(): FakePrefs = HchatStorage.store(SecureMessageSettings.SEND_PREFS)
fun key(): String = SecureMessageSettings.KEY_ENABLE
fun registry(): HookRegistry = HookRegistry.get()
fun scheduler(): DexInstallScheduler = DexInstallScheduler

fun resetHost() {
    cachedContext = null
    HLog.logs.clear()
    HchatStorage.reset()
    DexInstallScheduler.reset()
    HookRegistry.get().reset()
    DexMethodCache.reset()
    DexKitState.reset()
    WeChatApis.reset()
}

/** Feeds the synthetic DexKit result list: every query returns exactly this set. */
fun dexResults(vararg methods: Method) {
    DexKitState.results = methods.map { DexMethodData(it) }.toMutableList()
}

fun hostCheck(): Method = com.tencent.mm.storage.fake.SecureCheckHost::class.java
    .getDeclaredMethod("checkSecNode", Any::class.java)

fun hostInsert(): Method = com.tencent.mm.storage.fake.FakeMsgStorage::class.java
    .getDeclaredMethod("insert", Int::class.javaPrimitiveType, com.tencent.mm.storage.fake.FakeMsg::class.java)

fun hostMenu(): Method = com.tencent.mm.ui.chatting.viewitems.fake.FakeChattingItem::class.java
    .getDeclaredMethod(
        "onCreateContextMenu",
        Any::class.java,
        android.view.View::class.java,
        android.view.ContextMenu.ContextMenuInfo::class.java
    )

fun hostMsg(id: Long, type: Int, outgoing: Boolean, source: String?): com.tencent.mm.storage.fake.FakeMsg =
    com.tencent.mm.storage.fake.FakeMsg().also {
        it.field_msgId = id
        it.field_type = type
        it.field_isSend = if (outgoing) 1 else 0
        it.field_msgSource = source
    }

fun hookNamed(name: String): XC_MethodHook.Unhook =
    registry().hooks.firstOrNull { it.member?.name == name }
        ?: error("no hook installed for member $name")

fun hookCount(name: String): Int = registry().hooks.count { it.member?.name == name }

fun fire(unhook: XC_MethodHook.Unhook, args: Array<Any?>, thisObject: Any?): XC_MethodHook.MethodHookParam {
    val param = XC_MethodHook.MethodHookParam(args, thisObject)
    unhook.callback.beforeHookedMethod(param)
    return param
}

fun fireAfter(unhook: XC_MethodHook.Unhook, param: XC_MethodHook.MethodHookParam) {
    unhook.callback.afterHookedMethod(param)
}

fun releaseErrors(): Int = HLog.logs.count { it.contains("释放失败") }

fun secureMarker(): String = SecureMessageSettings.SEC_XML

fun markedSource(): String = "<msgsource>" + SecureMessageSettings.SEC_XML + "</msgsource>"

fun plainSource(): String = "<msgsource></msgsource>"

fun installAnti(): AntiSecureMessageFeature {
    val feature = AntiSecureMessageFeature()
    feature.onFeatureInstall(context())
    return feature
}

fun installSend(): SendSecureMessageFeature {
    val feature = SendSecureMessageFeature()
    feature.onFeatureInstall(context())
    return feature
}

fun hostVersion(name: String, code: Long): WeChatVersionInfo = WeChatVersionInfo(
    "com.tencent.mm", name, code, "0x0", "", "", 0L, "loader", "runtime-key"
)

fun antiScenarios() {
    // 1) 关闭状态安装：只留 preference owner，不建运行时、不调度、不装 Hook、不订阅
    resetHost()
    val feature = installAnti()
    check(feature.tracked.size == 1) { "preference owner must stay listenable while disabled" }
    check(scheduler().installations.isEmpty()) { "disabled startup must not take a ticket" }
    check(registry().hooks.isEmpty()) { "disabled startup must not hook" }
    check(context().eventBus().size() == 0)

    // 2) 打开开关建立一代：恰一票根、一订阅，安装后三组入口各一
    context().dexFinder().localMessageInsertMethod = hostInsert()
    dexResults(hostCheck(), hostMenu())
    antiPrefs().toggle(true, key())
    check(scheduler().installations.size == 1) { "one installation ticket per generation" }
    check(scheduler().drain() == listOf(true)) { "anti install must succeed" }
    check(registry().hooks.size == 3) { "check + insert + menu = 3 hooks, got ${registry().hooks.size}" }

    // 3) 安全检查被拦截：result=false，命中即不复算
    val check = hookNamed("checkSecNode")
    val intercepted = fire(check, arrayOf<Any?>(hostMsg(7L, 1, false, markedSource())), null)
    check(intercepted.result == false) { "secure check must be forced false" }

    // 4) 同一 Hook 在开关关闭后立即失效（回调门，不依赖重建）
    antiPrefs().enabled = false
    val gated = fire(check, arrayOf<Any?>(hostMsg(7L, 1, false, markedSource())), null)
    check(gated.result == null) { "disabled preference must gate the live hook" }
    antiPrefs().enabled = true

    // 5) 收到消息入库前清除安全标记；已发出与自己发出的消息不动
    val insert = hookNamed("insert")
    val incoming = hostMsg(11L, 1, false, markedSource())
    fire(insert, arrayOf<Any?>(0, incoming), null)
    check(!incoming.getMsgSource().contains("sec_msg_node")) {
        "incoming secure marker must be stripped, got ${incoming.getMsgSource()}"
    }
    val outgoing = hostMsg(12L, 1, true, markedSource())
    fire(insert, arrayOf<Any?>(0, outgoing), null)
    check(outgoing.getMsgSource().contains("sec_msg_node")) { "outgoing marker must stay" }
    val plain = hostMsg(13L, 1, false, plainSource())
    fire(insert, arrayOf<Any?>(0, plain), null)
    check(plain.getMsgSource() == plainSource()) { "message without marker must stay untouched" }

    // 6) 菜单构建期临时隐藏标记，after 无条件还原（前后配对）
    val menu = hookNamed("onCreateContextMenu")
    val view = android.view.View()
    val menuMsg = hostMsg(21L, 1, false, markedSource())
    view.tag = menuMsg
    val during = fire(menu, arrayOf<Any?>(null, view, null), null)
    check(!menuMsg.getMsgSource().contains("sec_msg_node")) {
        "menu build must see the stripped source"
    }
    fireAfter(menu, during)
    check(menuMsg.getMsgSource() == markedSource()) {
        "after callback must restore the original source, got ${menuMsg.getMsgSource()}"
    }

    // 7) 关闭后仍压占位，after 出栈不误改消息
    antiPrefs().toggle(false, key())
    check(scheduler().installations.last().closed) { "ticket must be cancelled with the generation" }
    check(registry().hooks.isEmpty()) { "all handles must be released" }
    val guarded = hostMsg(22L, 1, false, markedSource())
    view.tag = guarded
    fire(menu, arrayOf<Any?>(null, view, null), null)
    fireAfter(menu, XC_MethodHook.MethodHookParam(arrayOf<Any?>(null, view, null), null))
    check(guarded.getMsgSource() == markedSource()) { "closed generation must not touch the message" }

    // 8) 卸载后同成员不重复安装（hookOnce 去重）
    antiPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true))
    check(registry().hooks.size == 3)
    check(scheduler().drain().isEmpty()) { "reinstall must not queue a second installer" }
    check(registry().hooks.size == 3) { "repeated install must not duplicate handles" }

    // 9) 100 代开关：每代恰一票根、一订阅，末代仍三钩
    val baseTickets = scheduler().installations.size
    val ctx = context()
    ctx.dexFinder().localMessageInsertMethod = hostInsert()
    feature.releaseAll()
    val generation = AntiSecureMessageFeature()
    generation.onFeatureInstall(ctx)
    for (round in 1..100) {
        antiPrefs().toggle(false, key())
        antiPrefs().toggle(true, key())
        check(ctx.eventBus().size() == 1) { "one DexReady subscription per generation (round $round)" }
        check(scheduler().drain() == listOf(true)) { "round $round install" }
    }
    check(scheduler().installations.size - baseTickets >= 100) { "one ticket per generation" }
    check(registry().hooks.size == 3) { "one handle per entry after 100 generations" }
    antiPrefs().toggle(false, key())
    check(registry().hooks.isEmpty())

    // 10) 重复 DexReady 不重复装钩
    antiPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true))
    repeat(5) { ctx.eventBus().post(t.mma.event.Events.DexReady()) }
    check(scheduler().drain().size == 5)
    check(registry().hooks.size == 3) { "repeated DexReady must dedupe by member" }

    // 11) feature scope 释放：句柄与订阅一并归零
    generation.releaseAll()
    check(registry().hooks.isEmpty()) { "feature destroy must release every handle" }
    check(ctx.eventBus().size() == 0) { "feature destroy must release the subscription" }

    // 12) 缺 Dex 结果返回 false 且恢复后可重试
    resetHost()
    val retry = installAnti()
    antiPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(false)) { "missing every entry must report failure" }
    check(registry().hooks.isEmpty())
    context().dexFinder().localMessageInsertMethod = hostInsert()
    dexResults(hostCheck(), hostMenu())
    antiPrefs().toggle(false, key())
    antiPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true)) { "retry after Dex restore" }
    check(registry().hooks.size == 3) { "all three entries must install after restore" }

    // 13) Hook 安装抛异常：不留句柄，恢复后可重试
    antiPrefs().toggle(false, key())
    registry().failHook = true
    antiPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(false)) { "hook failure must report failure" }
    check(registry().hooks.isEmpty()) { "failed hook must leave no handle" }
    registry().failHook = false
    antiPrefs().toggle(false, key())
    antiPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true))
    check(registry().hooks.size == 3)

    // 14) unhook 抛异常不阻断本地所有权，错误计数可见
    resetHost()
    installAnti()
    context().dexFinder().localMessageInsertMethod = hostInsert()
    dexResults(hostCheck(), hostMenu())
    antiPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true))
    check(registry().hooks.size == 3)
    registry().failUnhook = true
    antiPrefs().toggle(false, key())
    check(registry().hooks.isEmpty()) { "local ownership must be released even on unhook failure" }
    check(releaseErrors() >= 1) { "unhook failure must be reported" }
    registry().failUnhook = false
    retry.releaseAll()
}

fun sendScenarios() {
    // 1) 关闭状态安装：只留 preference owner，不建运行时、不调度、不装 Hook、不订阅
    resetHost()
    val feature = installSend()
    check(feature.tracked.size == 1) { "send: preference owner must stay listenable while disabled" }
    check(scheduler().installations.isEmpty()) { "send: disabled startup must not take a ticket" }
    check(registry().hooks.isEmpty()) { "send: disabled startup must not hook" }
    check(context().eventBus().size() == 0)

    // 2) 打开开关：一票根一订阅；本地插入 API 命中时每代一钩
    val ctx = context()
    ctx.dexFinder().localMessageInsertMethod = hostInsert()
    dexResults(hostCheck())
    sendPrefs().toggle(true, key())
    check(scheduler().installations.size == 1) { "send: one ticket per generation" }
    check(ctx.eventBus().size() == 1) { "send: one DexReady subscription per generation" }
    check(scheduler().drain() == listOf(true)) { "send install must succeed" }

    // 3) 入库前给自己发出的受支持消息打标；收到的不打、未知类型也放行
    val insert = hookNamed("insert")
    val outgoing = hostMsg(31L, 1, true, plainSource())
    fire(insert, arrayOf<Any?>(0, outgoing), null)
    check(outgoing.getMsgSource().contains("sec_msg_node")) {
        "send: outgoing marker must be written, got ${outgoing.getMsgSource()}"
    }
    val image = hostMsg(32L, 3, true, "")
    fire(insert, arrayOf<Any?>(0, image), null)
    check(image.getMsgSource().contains("sec_msg_node")) { "send: image must be marked" }
    val incoming = hostMsg(33L, 1, false, plainSource())
    fire(insert, arrayOf<Any?>(0, incoming), null)
    check(!incoming.getMsgSource().contains("sec_msg_node")) { "send: incoming must stay clean" }
    val already = hostMsg(34L, 1, true, markedSource())
    fire(insert, arrayOf<Any?>(0, already), null)
    check(already.getMsgSource() == markedSource()) { "send: marker must not duplicate" }

    // 4) 设置关闭后同一 Hook 立即失效（回调门，不依赖重建）
    sendPrefs().enabled = false
    val gated = hostMsg(35L, 1, true, plainSource())
    fire(insert, arrayOf<Any?>(0, gated), null)
    check(!gated.getMsgSource().contains("sec_msg_node")) { "send: disabled preference must gate the hook" }
    sendPrefs().enabled = true

    // 5) msgSource setter 层合并：仅对受支持的自己发出的消息生效
    val setter = hookNamed("setMsgSource")
    val setterMsg = hostMsg(41L, 1, true, null)
    val setterParam = XC_MethodHook.MethodHookParam(arrayOf<Any?>(plainSource()), setterMsg)
    setter.callback.beforeHookedMethod(setterParam)
    check((setterParam.args[0] as String).contains("sec_msg_node")) {
        "send: merge hook must rewrite the setter argument"
    }
    val strangerParam = XC_MethodHook.MethodHookParam(arrayOf<Any?>("not-a-msgsource"), setterMsg)
    setter.callback.beforeHookedMethod(strangerParam)
    check(strangerParam.args[0] == "not-a-msgsource") { "send: non-msgsource must pass through" }

    // 6) 表情链路：未映射版本不额外装钩，已映射版本按 8.0.77 描述符装两钩
    check(SendSecureMessageFeature.isEnabled(context().hostContext()))
    WeChatApis.versionApi = t.mma.hooks.api.core.VersionApi(hostVersion("8.0.75", 3120L))
    sendPrefs().toggle(false, key())
    check(registry().hooks.isEmpty())
    sendPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true))
    check(hookCount("a") == 0 && hookCount("doScene") == 0) { "unmapped version must not add emoji hooks" }
    WeChatApis.versionApi = t.mma.hooks.api.core.VersionApi(hostVersion("8.0.77", 3160L))
    sendPrefs().toggle(false, key())
    sendPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true))
    check(hookCount("a") == 1) { "emoji source hook must be installed once" }
    check(hookCount("doScene") == 1) { "emoji dispatch hook must be installed once" }

    // 7) 表情 MsgSource 合并在 after 回填，请求图在 before 改写
    val sourceHook = hookNamed("a")
    val emojiMsg = com.tencent.mm.storage.e9()
    val sourceParam = XC_MethodHook.MethodHookParam(arrayOf<Any?>(emojiMsg), com.tencent.mm.storage.e9())
    sourceParam.result = plainSource()
    sourceHook.callback.afterHookedMethod(sourceParam)
    check((sourceParam.result as String).contains("sec_msg_node")) { "emoji source must be marked" }
    val scene = k52.y()
    val item = k52.EmojiItem()
    item.p = plainSource()
    val list = k52.EmojiList()
    list.e.add(item)
    val wrapper = k52.EmojiWrapper()
    wrapper.a = list
    val request = k52.EmojiRequest()
    request.a = wrapper
    scene.d = request
    val dispatchHook = hookNamed("doScene")
    fire(dispatchHook, arrayOf<Any?>(com.tencent.mm.network.s(), com.tencent.mm.modelbase.u0()), scene)
    check(item.p!!.contains("sec_msg_node")) { "emoji request source must be marked, got ${item.p}" }

    // 8) 关开关释放票根、订阅与全部句柄
    sendPrefs().toggle(false, key())
    check(scheduler().installations.last().closed) { "send: ticket must be cancelled" }
    check(registry().hooks.isEmpty()) { "send: every handle must be released" }
    check(ctx.eventBus().size() == 0) { "send: subscription must be released" }

    // 9) 重装不再被静态已装标记跳过
    sendPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true))
    check(hookCount("insert") == 1)
    sendPrefs().toggle(false, key())
    check(registry().hooks.isEmpty())

    // 10) 100 代开关：每代恰一票根一订阅
    val baseTickets = scheduler().installations.size
    // 旧一代先释放，否则两个 owner 同时监听同一份偏好，每次开关会排入两个安装器。
    feature.releaseAll()
    val generation = SendSecureMessageFeature()
    val genCtx = FeatureContext(android.content.Context(), ProcessLoader.INSTANCE)
    genCtx.dexFinder().localMessageInsertMethod = hostInsert()
    generation.onFeatureInstall(genCtx)
    for (round in 1..100) {
        sendPrefs().toggle(false, key())
        sendPrefs().toggle(true, key())
        check(genCtx.eventBus().size() == 1) { "send: one subscription per generation (round $round)" }
        val drained = scheduler().drain()
        check(drained == listOf(true)) { "send round $round install -> $drained" }
    }
    check(scheduler().installations.size - baseTickets >= 100) { "send: one ticket per generation" }
    sendPrefs().toggle(false, key())
    check(registry().hooks.isEmpty())

    // 11) 重复 DexReady 去重
    sendPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true))
    val before = registry().hooks.size
    repeat(5) { genCtx.eventBus().post(t.mma.event.Events.DexReady()) }
    check(scheduler().drain().size == 5)
    check(registry().hooks.size == before) { "send: repeated DexReady must dedupe by member" }

    // 12) feature scope 释放
    generation.releaseAll()
    check(registry().hooks.isEmpty()) { "send: feature destroy must release every handle" }
    check(genCtx.eventBus().size() == 0) { "send: feature destroy must release the subscription" }

    // 13) 缺入库方法返回 false 且恢复后可重试
    resetHost()
    val retry = installSend()
    val retryCtx = context()
    dexResults(hostCheck())
    sendPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(false)) { "send: missing insert entry must report failure" }
    check(registry().hooks.isEmpty())
    retryCtx.dexFinder().localMessageInsertMethod = hostInsert()
    sendPrefs().toggle(false, key())
    sendPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true)) { "send: retry after insert restore" }
    check(hookCount("insert") == 1)

    // 14) Hook 抛异常不留句柄，恢复后可重试
    sendPrefs().toggle(false, key())
    registry().failHook = true
    sendPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(false)) { "send: hook failure must report failure" }
    check(registry().hooks.isEmpty())
    registry().failHook = false
    sendPrefs().toggle(false, key())
    sendPrefs().toggle(true, key())
    check(scheduler().drain() == listOf(true))
    retry.releaseAll()
    check(registry().hooks.isEmpty()) { "send: feature destroy after retry must release every handle" }
}

fun main() {
    antiScenarios()
    sendScenarios()
    println(
        "PASS secure message pair: disabled startup leaves only a preference owner; " +
            "one installation ticket and one DexReady subscription per enabled interval; " +
            "secure check forced false with a live preference gate; " +
            "incoming secure node stripped at the insert boundary while outgoing messages stay untouched; " +
            "menu build hides and restores the marker with paired callbacks and a closed-state placeholder; " +
            "per-member hook dedupe on repeated install and repeated DexReady; " +
            "100 toggle generations with one ticket and one subscription each; " +
            "retry after missing descriptors and after hook failures; " +
            "unhook failure releases local ownership and reports once; " +
            "send side marks outgoing text, image and emoji sources through the insert hook, " +
            "the msgSource setter layer and the verified emoji request graph only; " +
            "unmapped host versions add no emoji hooks; feature scope release clears every handle and subscription"
    )
}
