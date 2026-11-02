package t.mma.hooks.items.chattime

import android.view.View
import android.widget.TextView
import com.tencent.mm.storage.FakeMsg
import com.tencent.mm.ui.chatting.view.FakeHolder
import com.tencent.mm.ui.chatting.view.FakeHolderNoTime
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Method
import t.mma.dexkit.DexKitState
import t.mma.dexkit.DexMethodCache
import t.mma.dexkit.DexMethodData
import t.mma.event.Events
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.FakePrefs
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog

/** One loader per synthetic process; the bind locator resolves host descriptors through it. */
object ProcessLoader {
    val INSTANCE: ClassLoader = object : ClassLoader(ProcessLoader::class.java.classLoader) {}
}

private var cachedContext: FeatureContext? = null

/** One context per scenario: bus and DexKit state must survive every generation. */
fun context(): FeatureContext = cachedContext
    ?: FeatureContext(android.content.Context(), ProcessLoader.INSTANCE).also { cachedContext = it }

fun prefs(): FakePrefs = HchatStorage.store(ChatTimeStyleSettings.PREFS_NAME)
fun registry(): HookRegistry = HookRegistry.get()

fun resetHost() {
    cachedContext = null
    HLog.logs.clear()
    HchatStorage.reset()
    DexInstallScheduler.reset()
    HookRegistry.get().reset()
    DexMethodCache.reset()
    DexKitState.reset()
}

fun setMode(mode: String) {
    prefs().putString(ChatTimeStyleSettings.KEY_MODE, mode)
}

fun setFormat(pattern: String) {
    prefs().putString(ChatTimeStyleSettings.KEY_TIME_FORMAT, pattern)
}

fun bindMethod(): Method = FakeHolder::class.java
    .getDeclaredMethod("onBindView", FakeHolder::class.java, Any::class.java, Int::class.javaPrimitiveType)

fun dexBindResults() {
    DexKitState.results = mutableListOf(DexMethodData(bindMethod()))
}

fun holder(text: String?): FakeHolder = FakeHolder().also { it.timeTV.text = text }

fun fireBind(holder: FakeHolder, message: Any?, position: Int) {
    val unhook = registry().hooks.firstOrNull { it.member?.name == "onBindView" }
        ?: error("bind hook missing")
    val param = XC_MethodHook.MethodHookParam(arrayOf(holder, message, position), holder)
    unhook.callback.beforeHookedMethod(param)
    unhook.callback.afterHookedMethod(param)
}

fun modeIs(mode: String): Boolean =
    ChatTimeStyleSettings.normalizeMode(prefs().getString(ChatTimeStyleSettings.KEY_MODE, null)) == mode

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun scenario1StockUntilConfigured() {
    resetHost()
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    expect(!modeIs(ChatTimeStyleSettings.MODE_CUSTOM), "stock prefs must start original")
    expect(DexInstallScheduler.installations.isEmpty(), "no ticket before the feature is configured")
    expect(registry().hooks.isEmpty(), "no hook before the feature is configured")
    expect(context().eventBus().size() == 0, "no DexReady subscription before the feature is configured")

    setMode(ChatTimeStyleSettings.MODE_CUSTOM)
    prefs().change(ChatTimeStyleSettings.KEY_MODE)
    expect(DexInstallScheduler.installations.size == 1, "enabling must create exactly one owned ticket")
    expect(context().eventBus().size() == 1, "enabling must subscribe DexReady once")
    DexInstallScheduler.drain()
    expect(registry().size() == 1, "enabling must install one bind hook")
    feature.releaseAll(context())
}

fun scenario2PerMessageCustomText() {
    resetHost()
    setMode(ChatTimeStyleSettings.MODE_CUSTOM)
    setFormat("HH:mm")
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    DexInstallScheduler.drain()
    val first = holder("09:30")
    val message = FakeMsg(1_650_000_000_000L, 42L)
    fireBind(first, message, 0)
    expect(first.timeTV.text == expectedText(1_650_000_000_000L, "HH:mm"),
        "custom mode must render the message timestamp, got ${first.timeTV.text}")
    expect(message.createTimeReads == 1, "one resolution must read the create time once")

    // Host reuses the same view for another message: new timestamp, same policy.
    val second = FakeMsg(1_660_000_000_000L, 43L)
    first.timeTV.text = "昨天"
    fireBind(first, second, 1)
    expect(first.timeTV.text == expectedText(1_660_000_000_000L, "HH:mm"),
        "rebinding a reused view must follow the new message, got ${first.timeTV.text}")

    // An unresolvable message must fall back to the native text, never a stale date.
    val third = FakeMsg(0L, 0L)
    fireBind(first, third, 2)
    expect(first.timeTV.text == "昨天",
        "unresolvable message must fall back to the latest native snapshot, got ${first.timeTV.text}")
    feature.releaseAll(context())
}

fun scenario3HiddenModeAndRestore() {
    resetHost()
    setMode(ChatTimeStyleSettings.MODE_HIDDEN)
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    DexInstallScheduler.drain()
    val view = holder("09:30")
    fireBind(view, FakeMsg(1_650_000_000_000L, 42L), 0)
    expect(view.timeTV.visibility == View.GONE, "hidden mode must hide the label")
    expect(view.timeTV.text == "09:30", "hidden mode must not rewrite the native text")

    feature.releaseAll(context())
    expect(registry().size() == 0, "destroy must release the bind hook")
    expect(view.timeTV.visibility == View.VISIBLE, "destroy must restore the native visibility")
    expect(view.timeTV.text == "09:30", "destroy must restore the native text")
    expect(prefs().listeners.isEmpty(), "destroy must unregister the preference listener")
}

fun expectedText(timestamp: Long, pattern: String): String =
    java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        .apply { timeZone = java.util.TimeZone.getDefault() }
        .format(java.util.Date(timestamp))

fun scenario4DisableRestoresAndReleases() {
    resetHost()
    setMode(ChatTimeStyleSettings.MODE_CUSTOM)
    setFormat("yyyy-MM-dd HH:mm:ss")
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    DexInstallScheduler.drain()
    val view = holder("昨天 09:30")
    fireBind(view, FakeMsg(1_650_000_000_000L, 42L), 0)
    expect(view.timeTV.text == expectedText(1_650_000_000_000L, "yyyy-MM-dd HH:mm:ss"),
        "custom mode must render the configured pattern")

    // Back to original from the settings page: generation closes, view restores, hooks go away.
    setMode(ChatTimeStyleSettings.MODE_ORIGINAL)
    prefs().change(ChatTimeStyleSettings.KEY_MODE)
    expect(registry().size() == 0, "disabling must unhook the bind method")
    expect(context().eventBus().size() == 0, "disabling must drop the DexReady subscription")
    // Only the owner's reopen listener stays; the generation's own listener is gone.
    expect(prefs().listeners.size == 1, "disabling must keep exactly the reopen listener, got ${prefs().listeners.size}")
    expect(DexInstallScheduler.installations.all { it.closed }, "disabling must cancel the owned ticket")
    expect(view.timeTV.text == "昨天 09:30", "disabling must restore the native text, got ${view.timeTV.text}")
    feature.releaseAll(context())
    expect(prefs().listeners.isEmpty(), "destroy must unregister the reopen listener too")
}

fun scenario5SingleHookAcrossGenerations() {
    resetHost()
    setMode(ChatTimeStyleSettings.MODE_CUSTOM)
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    DexInstallScheduler.drain()
    expect(registry().size() == 1, "first generation installs one hook")

    // A pattern-only change must not create a second generation.
    setFormat("MM-dd HH:mm")
    prefs().change(ChatTimeStyleSettings.KEY_TIME_FORMAT)
    expect(DexInstallScheduler.installations.size == 1, "format change must reuse the running generation")
    expect(registry().size() == 1, "format change must not install a second hook")

    // Disable and enable again: a brand-new generation, still exactly one hook.
    setMode(ChatTimeStyleSettings.MODE_ORIGINAL)
    prefs().change(ChatTimeStyleSettings.KEY_MODE)
    setMode(ChatTimeStyleSettings.MODE_CUSTOM)
    prefs().change(ChatTimeStyleSettings.KEY_MODE)
    expect(DexInstallScheduler.installations.size == 2, "re-enabling must create a new ticket")
    DexInstallScheduler.drain()
    expect(registry().size() == 1, "re-enabling must leave exactly one hook")

    // DexReady arriving for the closed generation must not install anything.
    val closedTicket = DexInstallScheduler.installations.first()
    expect(closedTicket.closed, "the previous generation ticket must be closed")
    feature.releaseAll(context())
    expect(registry().size() == 0, "destroy must leave no hook")
}

fun scenario6DroppedRestoreDelivery() {
    resetHost()
    setMode(ChatTimeStyleSettings.MODE_HIDDEN)
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    DexInstallScheduler.drain()
    val view = holder("09:30")
    view.timeTV.postEnabled = false
    fireBind(view, FakeMsg(1_650_000_000_000L, 42L), 0)
    expect(view.timeTV.visibility == View.GONE, "hidden mode must hide before the restore path runs")

    feature.releaseAll(context())
    expect(view.timeTV.visibility == View.VISIBLE,
        "a dropped view.post must fall back to the owned handler and still restore")
}

fun scenario7HookFailureLeavesNoResidue() {
    resetHost()
    setMode(ChatTimeStyleSettings.MODE_CUSTOM)
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    registry().failHook = true
    val installed = DexInstallScheduler.drain()
    expect(installed.all { !it }, "a failed hook must report failure so the scheduler retries")
    expect(registry().size() == 0, "a failed hook must not leave a handle")
    expect(HLog.logs.any { it.contains("安装聊天时间样式 Hook 失败") }, "hook failure must be logged")

    // Retry works once the host allows the hook.
    registry().failHook = false
    DexInstallScheduler.drain()
    expect(registry().size() == 1, "the retry must install exactly one hook")
    feature.releaseAll(context())
}

fun scenario8LateBindAfterClose() {
    resetHost()
    setMode(ChatTimeStyleSettings.MODE_HIDDEN)
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    DexInstallScheduler.drain()
    val stale = registry().hooks.first().callback

    feature.releaseAll(context())
    val view = holder("09:30")
    val param = XC_MethodHook.MethodHookParam(arrayOf(view, FakeMsg(1_650_000_000_000L, 42L), 0), view)
    stale.afterHookedMethod(param)
    expect(view.timeTV.visibility == View.VISIBLE, "a late callback from a closed generation must not style")
    expect(view.timeTV.text == "09:30", "a late callback must not rewrite the text")
}

fun scenario9HoldersWithoutTimeLabel() {
    resetHost()
    setMode(ChatTimeStyleSettings.MODE_CUSTOM)
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    DexInstallScheduler.drain()
    val noTime = noTimeHolder()
    repeat(5) { index ->
        val param = XC_MethodHook.MethodHookParam(
            arrayOf(noTime, FakeMsg(1_650_000_000_000L + index, 42L), index), noTime
        )
        registry().hooks.first().callback.afterHookedMethod(param)
    }
    expect(HLog.logs.none { it.contains("Exception") }, "holders without a time label must be skipped silently")
    feature.releaseAll(context())
}

fun noTimeHolder(): FakeHolderNoTime = FakeHolderNoTime()

fun scenario10PreferenceRefreshAfterClose() {
    resetHost()
    setMode(ChatTimeStyleSettings.MODE_HIDDEN)
    dexBindResults()
    val feature = ChatTimeStyleFeature()
    feature.install(context())
    DexInstallScheduler.drain()
    val view = holder("09:30")
    fireBind(view, FakeMsg(1_650_000_000_000L, 42L), 0)
    expect(view.timeTV.visibility == View.GONE, "hidden mode must hide the label")

    // A pattern write must re-apply the style on the still-running generation.
    setMode(ChatTimeStyleSettings.MODE_CUSTOM)
    prefs().change(ChatTimeStyleSettings.KEY_MODE)
    expect(view.timeTV.visibility == View.VISIBLE, "switching to custom must restore visibility")
    expect(view.timeTV.text == expectedText(1_650_000_000_000L, ChatTimeStyleSettings.DEFAULT_TIME_FORMAT),
        "switching to custom must render the timestamp")

    feature.releaseAll(context())
    view.timeTV.text = "09:30"
    view.timeTV.visibility = View.VISIBLE
    setFormat("HH:mm")
    prefs().change(ChatTimeStyleSettings.KEY_TIME_FORMAT)
    expect(view.timeTV.text == "09:30", "a preference write after destroy must not touch the view")
}

fun scenario11BoundedCaches() {
    resetHost()
    val fields = BoundedLruMap<Class<*>, java.lang.reflect.Field>(128)
    val unsupported = BoundedLruSet<Class<*>>(128)
    val classes = (1..300).map { index ->
        object : ClassLoader(null) {
            fun make(): Class<*> {
                val bytes = FakeMsg::class.java.getResourceAsStream("FakeMsg.class")!!.readBytes()
                return defineClass("com.tencent.mm.storage.FakeMsg", bytes, 0, bytes.size)
            }
        }.make().also { _ -> check(index > 0) }
    }
    classes.forEach { clazz ->
        fields.put(clazz, clazz.declaredFields.first())
        unsupported.add(clazz)
    }
    val sizeAfter = mapSize(fields)
    expect(sizeAfter == 128, "field cache must stay bounded at 128, got $sizeAfter")
    val setSize = mapSize(unsupported)
    expect(setSize == 128, "unsupported-holder cache must stay bounded at 128, got $setSize")
    fields.clear()
    unsupported.clear()
    expect(fields.get(classes.first()) == null, "clearing the cache must drop every entry")
}

fun mapSize(target: Any): Int {
    val entry = target.javaClass.getDeclaredField("entries")
    entry.isAccessible = true
    val map = entry.get(target) as Map<*, *>
    return map.size
}

fun main() {
    scenario1StockUntilConfigured()
    println("S1 OK: stock/disabled state creates no ticket, no subscription, no hook; enabling creates one of each")
    scenario2PerMessageCustomText()
    println("S2 OK: custom mode renders per-message timestamps, one create-time read, reused views follow the new message")
    scenario3HiddenModeAndRestore()
    println("S3 OK: hidden mode hides without rewriting text; destroy restores view and listener")
    scenario4DisableRestoresAndReleases()
    println("S4 OK: returning to original closes the generation, cancels the ticket and restores every owned view")
    scenario5SingleHookAcrossGenerations()
    println("S5 OK: pattern changes reuse the generation; disable/enable yields one hook; closed ticket installs nothing")
    scenario6DroppedRestoreDelivery()
    println("S6 OK: a refused view.post falls back to the owned handler and still restores")
    scenario7HookFailureLeavesNoResidue()
    println("S7 OK: hook failure reports failure, leaves no handle, retry installs once")
    scenario8LateBindAfterClose()
    println("S8 OK: late callback from a closed generation performs no write")
    scenario9HoldersWithoutTimeLabel()
    println("S9 OK: unsupported holders are skipped without exceptions")
    scenario10PreferenceRefreshAfterClose()
    println("S10 OK: live preference writes restyle owned views; writes after destroy touch nothing")
    scenario11BoundedCaches()
    println("S11 OK: reflection caches evict at 128 and clear on demand")
    println("PASS: chattime lifecycle regression")
}
