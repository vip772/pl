package t.mma.hooks.items.multirecall

import android.app.Activity
import android.view.MenuItem
import android.widget.Toast
import com.tencent.mm.storage.fake.FakeMessage
import com.tencent.mm.ui.chatting.component.fake.FakeChattingComponent
import com.tencent.mm.ui.chatting.component.fake.FakeMenu
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Method
import t.mma.dexkit.DexKitState
import t.mma.dexkit.DexMethodCache
import t.mma.dexkit.DexMethodData
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.FakePrefs
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog

/** One loader per synthetic process; the locator resolves host descriptors through it. */
object ProcessLoader {
    val INSTANCE: ClassLoader = object : ClassLoader(ProcessLoader::class.java.classLoader) {}
}

private var cachedContext: FeatureContext? = null

/** One context per scenario: bus and cache state must survive every generation. */
fun context(): FeatureContext = cachedContext
    ?: FeatureContext(android.content.Context(), ProcessLoader.INSTANCE).also { cachedContext = it }

fun prefs(): FakePrefs = HchatStorage.store(MultiRecallSettings.PREFS_NAME)

fun registry(): HookRegistry = HookRegistry.get()

fun scheduler(): DexInstallScheduler = DexInstallScheduler

fun resetHost() {
    cachedContext = null
    HLog.logs.clear()
    Toast.reset()
    HchatStorage.reset()
    DexInstallScheduler.reset()
    HookRegistry.get().reset()
    DexMethodCache.reset()
    DexKitState.reset()
    WeChatApis.resetHost()
}

/** Feeds the synthetic DexKit result list: every query returns exactly this set. */
fun dexResults(vararg methods: Method) {
    DexKitState.results = methods.map { DexMethodData(it) }.toMutableList()
}

fun hostCreate(): Method = FakeChattingComponent::class.java
    .getDeclaredMethod("onCreateMMMenu", Any::class.java)

fun hostClick(): Method = FakeChattingComponent::class.java
    .getDeclaredMethod("onMMMenuItemSelected", MenuItem::class.java, Int::class.javaPrimitiveType)

fun hostExit(): Method = FakeChattingComponent::class.java
    .getDeclaredMethod("exitMultiSelect")

fun msg(id: Long, outgoing: Boolean): FakeMessage = FakeMessage(id, outgoing)

fun component(vararg messages: FakeMessage): FakeChattingComponent =
    FakeChattingComponent().also { host -> messages.forEach { host.selected.add(it) } }

fun menu(): FakeMenu = FakeMenu()

fun ourItem(): MenuItem = MenuItem().also { it.itemId = MENU_ITEM_ID }

fun foreignItem(): MenuItem = MenuItem().also { it.itemId = 0x11223344 }

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

fun installFeature(): MultiRecallFeature = MultiRecallFeature().also { it.onFeatureInstall(context()) }

fun enableRuntime(value: Boolean) {
    prefs().toggle(value, MultiRecallSettings.KEY_ENABLE)
}

fun acceptActivity(): Activity {
    val activity = Activity()
    WeChatApis.installActivity(t.mma.hooks.api.core.FakeActivityApi(activity))
    return activity
}

const val MENU_ITEM_ID = 0x48435243

fun scenarioDisabledStartup() {
    resetHost()
    val feature = installFeature()
    assert(scheduler().installations.isEmpty()) { "disabled startup must not open a ticket" }
    assert(registry().hooks.isEmpty()) { "disabled startup must not hook" }
    assert(context().eventBus().size() == 0) { "disabled startup must not subscribe" }
    assert(prefs().listeners.size == 1) { "one preference listener must stay for reopen" }
    assert(scheduler().drain().isEmpty()) { "disabled startup must queue no installer" }
    feature.releaseAll()
    prefs().reset()
    println("PASS disabled startup leaves only a preference listener")
}

fun scenarioEnabledStartup() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    val feature = installFeature()
    enableRuntime(true)
    assert(scheduler().installations.size == 1) { "one ticket per enabled interval" }
    assert(context().eventBus().size() == 1) { "one DexReady subscription per enabled interval" }
    val results = scheduler().drain()
    assert(results == listOf(true)) { "install must report ready: " + results }
    assert(hookCount("onCreateMMMenu") == 1) { "menu create hook" }
    assert(hookCount("onMMMenuItemSelected") == 1) { "menu click hook" }
    assert(registry().size() == 2) { "exactly two handles, got " + registry().size() }
    feature.releaseAll()
    println("PASS one ticket, one subscription and two hooks per enabled interval")
}

fun scenarioMenuAddsOwnItem() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    val host = component(msg(11L, true), msg(12L, true))
    val target = menu()
    val param = XC_MethodHook.MethodHookParam(arrayOf<Any?>(target), host)
    fireAfter(hookNamed("onCreateMMMenu"), param)
    assert(target.items.containsKey(MENU_ITEM_ID)) { "own item must be added, got " + target.items }
    assert(target.items[MENU_ITEM_ID] == "批量撤回[H]") { "title" }
    // A second menu build must not duplicate the item and must reuse findItem.
    fireAfter(hookNamed("onCreateMMMenu"), XC_MethodHook.MethodHookParam(arrayOf<Any?>(target), host))
    assert(target.items.size == 1) { "item must stay single" }
    assert(target.findItemCalls >= 1) { "existing item must be detected through findItem" }
    feature.releaseAll()
    println("PASS menu build adds exactly one owned item and skips an existing one")
}

fun scenarioMenuSkipsForeignSelection() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    val mixed = component(msg(21L, true), msg(22L, false))
    val mixedMenu = menu()
    fireAfter(hookNamed("onCreateMMMenu"), XC_MethodHook.MethodHookParam(arrayOf<Any?>(mixedMenu), mixed))
    assert(mixedMenu.items.isEmpty()) { "mixed selection must not offer the item" }
    val empty = component()
    val emptyMenu = menu()
    fireAfter(hookNamed("onCreateMMMenu"), XC_MethodHook.MethodHookParam(arrayOf<Any?>(emptyMenu), empty))
    assert(emptyMenu.items.isEmpty()) { "empty selection must not offer the item" }
    feature.releaseAll()
    println("PASS menu build skips mixed and empty selections")
}

fun scenarioPreferenceGateInsideCallback() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    // Flip the stored value without firing the listener: the callback gate must read it.
    prefs().enabled = false
    val host = component(msg(31L, true))
    val target = menu()
    fireAfter(hookNamed("onCreateMMMenu"), XC_MethodHook.MethodHookParam(arrayOf<Any?>(target), host))
    assert(target.items.isEmpty()) { "closed preference must suppress the item without a destroy" }
    val click = fire(hookNamed("onMMMenuItemSelected"), arrayOf<Any?>(ourItem(), 3), host)
    assert(WeChatApis.senderHost().revoked.isEmpty()) { "closed preference must not revoke" }
    assert(click.result == null && !click.hasThrowable()) { "callback must leave the native call alone" }
    feature.releaseAll()
    println("PASS closed preference suppresses both entry points without a destroy")
}

fun scenarioOwnItemClick() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    acceptActivity()
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    val host = component(msg(41L, true), msg(42L, true))
    val param = fire(hookNamed("onMMMenuItemSelected"), arrayOf<Any?>(ourItem(), 0), host)
    assert(WeChatApis.senderHost().revoked.size == 2) {
        "both own messages must be revoked, got " + WeChatApis.senderHost().revoked.size
    }
    assert(host.exitCalls == 1) { "native exit must run exactly once, got " + host.exitCalls }
    assert(Toast.shown.size == 1 && Toast.shown[0].contains("2/2")) { "toast: " + Toast.shown }
    assert(param.result == null) { "owned item must be consumed, not handed back" }
    feature.releaseAll()
    println("PASS owned menu item revokes every own message and exits multi-select once")
}

fun scenarioForeignItemClick() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    acceptActivity()
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    val host = component(msg(51L, true))
    fire(hookNamed("onMMMenuItemSelected"), arrayOf<Any?>(foreignItem(), 1), host)
    assert(WeChatApis.senderHost().revoked.isEmpty()) { "foreign item must not revoke" }
    assert(host.exitCalls == 0) { "foreign item must not exit multi-select" }
    assert(Toast.shown.isEmpty()) { "foreign item must not toast" }
    feature.releaseAll()
    println("PASS foreign menu item is left entirely to the host")
}

fun scenarioIncomingSelectionRejected() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    acceptActivity()
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    val host = component(msg(61L, false), msg(62L, false))
    val param = fire(hookNamed("onMMMenuItemSelected"), arrayOf<Any?>(ourItem(), 2), host)
    assert(WeChatApis.senderHost().revoked.isEmpty()) { "incoming rows must never be revoked" }
    assert(Toast.shown.size == 1 && Toast.shown[0].contains("自己发送")) {
        "rejection toast: " + Toast.shown
    }
    assert(host.exitCalls == 0) { "rejected click must not exit multi-select" }
    assert(param.result == null) { "owned item stays consumed even when rejected" }
    feature.releaseAll()
    println("PASS incoming-only selection is rejected with the exact retry toast")
}

fun scenarioCloseDuringRevokeLoop() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    acceptActivity()
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    val host = component(msg(71L, true), msg(72L, true), msg(73L, true))
    WeChatApis.senderHost().onRevoke = { feature.releaseAll() }
    fire(hookNamed("onMMMenuItemSelected"), arrayOf<Any?>(ourItem(), 0), host)
    assert(WeChatApis.senderHost().revoked.size == 1) {
        "close must stop new submissions, got " + WeChatApis.senderHost().revoked.size
    }
    assert(Toast.shown.isEmpty()) { "a late toast must not fire after close: " + Toast.shown }
    assert(host.exitCalls == 0) { "a late exit must not fire after close" }
    println("PASS close mid loop stops new submissions and drops the late toast and exit")
}

fun scenarioRepeatedInstallDedupes() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    // Force a second installer cycle on the same runtime generation.
    context().eventBus().post(Events.DexReady())
    scheduler().drain()
    assert(hookCount("onCreateMMMenu") == 1) { "per-member dedupe on create" }
    assert(hookCount("onMMMenuItemSelected") == 1) { "per-member dedupe on click" }
    assert(registry().size() == 2) { "repeated install must not add handles" }
    feature.releaseAll()
    println("PASS repeated install cycles keep one handle per member")
}

fun scenarioCacheReuse() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    val first = installFeature()
    enableRuntime(true)
    scheduler().drain()
    val queries = DexKitState.queries
    assert(queries >= 3) { "first generation must query the bridge, got " + queries }
    first.releaseAll()
    val second = installFeature()
    enableRuntime(true)
    scheduler().drain()
    assert(DexKitState.queries == queries) {
        "cached descriptors must skip the bridge, got " + DexKitState.queries + " from " + queries
    }
    assert(registry().size() == 2) { "cached generation still hooks both entries" }
    second.releaseAll()
    println("PASS cached descriptors are reused by the next generation without a bridge query")
}

fun scenarioMissingDescriptorsRetry() {
    resetHost()
    dexResults()
    val feature = installFeature()
    enableRuntime(true)
    val first = scheduler().drain()
    assert(first == listOf(false)) { "a missing descriptor must report not ready: " + first }
    assert(registry().hooks.isEmpty()) { "a missing descriptor must leave no handle" }
    dexResults(hostCreate(), hostClick(), hostExit())
    val retry = scheduler().drain()
    assert(retry.isEmpty()) { "no ticket left queued after the first attempt" }
    context().eventBus().post(Events.DexReady())
    val afterReady = scheduler().drain()
    assert(afterReady == listOf(true)) { "retry on DexReady must succeed: " + afterReady }
    assert(registry().size() == 2) { "retry installs both entries" }
    feature.releaseAll()
    println("PASS a missing descriptor retries after DexReady and then installs")
}

fun scenarioHookFailureRetry() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    registry().failHook = true
    val feature = installFeature()
    enableRuntime(true)
    val failed = scheduler().drain()
    assert(failed == listOf(false)) { "a hook failure must report not ready: " + failed }
    assert(registry().hooks.isEmpty()) { "a failed hook must leave no handle" }
    assert(HLog.logs.any { it.contains("Hook安装失败") } ||
        HLog.logs.any { it.contains("未安装") }) { "hook failure must be reported" }
    registry().failHook = false
    context().eventBus().post(Events.DexReady())
    val retry = scheduler().drain()
    assert(retry == listOf(true)) { "retry must succeed: " + retry }
    assert(registry().size() == 2) { "retry installs both entries" }
    feature.releaseAll()
    println("PASS a hook failure leaves no handle and the next attempt installs")
}

fun scenarioUnhookFailureStillReleases() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    assert(registry().size() == 2) { "precondition: two handles" }
    registry().failUnhook = true
    feature.releaseAll()
    assert(registry().hooks.isEmpty()) { "local ownership must be released even when unhook throws" }
    assert(releaseErrors() >= 1) { "unhook failure must be reported" }
    assert(scheduler().installations.last().closed) { "the ticket must be closed" }
    registry().failUnhook = false
    println("PASS an unhook failure still releases local ownership and reports once per handle")
}

fun scenarioScopeReleaseClearsEverything() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    val feature = installFeature()
    enableRuntime(true)
    scheduler().drain()
    assert(registry().size() == 2)
    assert(context().eventBus().size() == 1)
    feature.releaseAll()
    assert(registry().hooks.isEmpty()) { "every handle must be gone" }
    assert(context().eventBus().size() == 0) { "the subscription must be gone" }
    assert(scheduler().installations.last().closed) { "the ticket must be closed" }
    assert(scheduler().pending.isEmpty()) { "no installer may stay queued" }
    assert(prefs().listeners.isEmpty()) { "the preference listener must be gone" }
    assert(releaseErrors() == 0) { "no release error on a clean shutdown" }
    println("PASS feature scope release clears every handle, subscription and ticket")
}

fun scenarioHundredGenerations() {
    resetHost()
    dexResults(hostCreate(), hostClick(), hostExit())
    repeat(100) { generation ->
        val feature = installFeature()
        enableRuntime(true)
        val results = scheduler().drain()
        assert(scheduler().installations.size == generation + 1) {
            "generation " + generation + " ticket count " + scheduler().installations.size
        }
        assert(results == listOf(true)) { "generation " + generation + " install: " + results }
        assert(registry().size() == 2) { "generation " + generation + " handles" }
        assert(context().eventBus().size() == 1) { "generation " + generation + " subscriptions" }
        feature.releaseAll()
        assert(registry().hooks.isEmpty()) { "generation " + generation + " handles after release" }
        assert(context().eventBus().size() == 0) { "generation " + generation + " subscription after release" }
        enableRuntime(false)
    }
    assert(scheduler().pending.isEmpty()) { "no installer may survive 100 generations" }
    assert(prefs().listeners.isEmpty()) { "no listener may survive 100 generations" }
    println("PASS 100 toggle generations keep one ticket, one subscription and two handles each")
}

fun main() {
    scenarioDisabledStartup()
    scenarioEnabledStartup()
    scenarioMenuAddsOwnItem()
    scenarioMenuSkipsForeignSelection()
    scenarioPreferenceGateInsideCallback()
    scenarioOwnItemClick()
    scenarioForeignItemClick()
    scenarioIncomingSelectionRejected()
    scenarioCloseDuringRevokeLoop()
    scenarioRepeatedInstallDedupes()
    scenarioCacheReuse()
    scenarioMissingDescriptorsRetry()
    scenarioHookFailureRetry()
    scenarioUnhookFailureStillReleases()
    scenarioScopeReleaseClearsEverything()
    scenarioHundredGenerations()
    println("PASS multi recall: preference owner per enabled interval, one ticket and one " +
        "DexReady subscription each, per-member hook dedupe, own menu item only for an " +
        "outright own selection, close stops new revokes and drops late toast and exit, " +
        "cached descriptors reused, retry after missing descriptors and after hook " +
        "failures, unhook failure still releases local ownership, and 100 generations " +
        "leave no ticket, listener or handle behind")
}
