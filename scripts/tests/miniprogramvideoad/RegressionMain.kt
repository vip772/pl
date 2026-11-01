package t.mma.hooks.items.miniprogramvideoad

import de.robv.android.xposed.XC_MethodHook

fun enableKey() = SkipMiniProgramVideoAdsSettings.KEY_ENABLE

fun prefs(): t.mma.preferences.FakePrefs =
    t.mma.preferences.HchatStorage.store(SkipMiniProgramVideoAdsSettings.PREFS_NAME)

/** One loader per synthetic process; the feature resolves its JS bridge through it. */
object ProcessLoader {
    val INSTANCE: ClassLoader = object : ClassLoader(ProcessLoader::class.java.classLoader) {}
    const val BRIDGE = "com.tencent.mm.appbrand.commonjni.AppBrandJsBridgeBinding"
}

fun context(): t.mma.hooks.core.FeatureContext = t.mma.hooks.core.FeatureContext(
    android.content.Context(),
    ProcessLoader.INSTANCE
)

fun resetHost() {
    t.mma.utils.HLog.logs.clear()
    t.mma.preferences.HchatStorage.reset()
    t.mma.hooks.core.DexInstallScheduler.reset()
    t.mma.hooks.core.HookRegistry.get().reset()
    t.mma.utils.KavaReflector.bridgeFor(ProcessLoader.INSTANCE, null)
    SkipMiniProgramVideoAdsFeature.destroyProcessHooks()
}

fun registry() = t.mma.hooks.core.HookRegistry.get()

fun scheduler() = t.mma.hooks.core.DexInstallScheduler

fun fire(hook: XC_MethodHook.Unhook, args: Array<Any?>): XC_MethodHook.MethodHookParam {
    val param = XC_MethodHook.MethodHookParam(args, Any())
    hook.callback.beforeHookedMethod(param)
    return param
}

fun payload(): String = """{"position":0,"duration":30}"""

fun rewritten(): String = """{"position":60,"duration":1}"""

fun releaseErrors(): Int = t.mma.utils.HLog.logs.count { it.contains("释放失败") }

fun main() {
    // 1) 关闭状态安装：只留 preference owner，不建运行时、不调度、不装 Hook、不订阅
    resetHost()
    val feature = SkipMiniProgramVideoAdsFeature()
    val featureContext = context()
    feature.onFeatureInstall(featureContext)
    check(feature.tracked.size == 1) { "preference owner must stay listenable while disabled" }
    check(scheduler().installations.isEmpty())
    check(registry().hooks.isEmpty())
    check(featureContext.eventBus().size() == 0)

    // 2) 打开开关建立一代：一个票根、一个订阅，安装后恰一个 Hook
    t.mma.utils.KavaReflector.bridgeFor(ProcessLoader.INSTANCE, ProcessLoader.BRIDGE)
    prefs().toggle(true, enableKey())
    check(scheduler().installations.size == 1)
    check(featureContext.eventBus().size() == 1) { "one DexReady subscription per generation" }
    val ticket = scheduler().installations.single()
    check(scheduler().drain() == listOf(true)) { "install must succeed" }
    check(registry().hooks.size == 1)
    val hook = registry().hooks.single()

    // 3) 只在视频进度事件上改写进度，其它事件与非对象载荷透传
    val progress = arrayOf<Any?>("onVideoTimeUpdate", payload(), 0, "extra")
    val rewrite = fire(hook, progress)
    check(rewrite.args[1] == rewritten()) {
        "progress payload must be rewritten, got ${rewrite.args[1]}"
    }
    val other = arrayOf<Any?>("onVideoEnded", payload(), 0, "extra")
    fire(hook, other)
    check(other[1] == payload()) { "other bridge events must stay untouched" }
    val notJson = arrayOf<Any?>("onVideoTimeUpdate", "not-json", 0, "extra")
    fire(hook, notJson)
    check(notJson[1] == "not-json") { "unparsable payload must pass through" }
    val notString = arrayOf<Any?>("onVideoTimeUpdate", 42, 0, "extra")
    fire(hook, notString)
    check(notString[1] == 42) { "non-string payload must pass through" }

    // 4) 设置关闭后同一 Hook 立即失效（回调门，不依赖重建）
    prefs().enabled = false
    val gated = arrayOf<Any?>("onVideoTimeUpdate", payload(), 0, "extra")
    fire(hook, gated)
    check(gated[1] == payload()) { "disabled preference must gate the live hook" }
    prefs().enabled = true

    // 5) 关开关释放运行时、票根、订阅与 Hook
    prefs().toggle(false, enableKey())
    check(ticket.closed) { "installation ticket must be cancelled with the generation" }
    check(registry().hooks.isEmpty())
    check(hook.unhooked)
    check(featureContext.eventBus().size() == 0) { "subscription must be released with the generation" }

    // 6) 再次打开能重新安装（不再被静态已装标记跳过）
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true))
    check(registry().hooks.size == 1)
    prefs().toggle(false, enableKey())
    check(registry().hooks.isEmpty())

    // 7) 桥类缺失返回 false 不装 Hook，恢复后可重试
    resetHost()
    SkipMiniProgramVideoAdsFeature().onFeatureInstall(context())
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(false)) { "missing bridge must not install" }
    check(registry().hooks.isEmpty())
    t.mma.utils.KavaReflector.bridgeFor(ProcessLoader.INSTANCE, ProcessLoader.BRIDGE)
    prefs().toggle(false, enableKey())
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true)) { "retry after bridge restore" }
    check(registry().hooks.size == 1)

    // 8) Hook 安装抛异常：句柄不残留，恢复后可重试
    prefs().toggle(false, enableKey())
    check(registry().hooks.isEmpty())
    prefs().toggle(true, enableKey())
    registry().failHook = true
    check(scheduler().drain() == listOf(false))
    check(registry().hooks.isEmpty()) { "failed hook must not leave a handle" }
    registry().failHook = false
    prefs().toggle(false, enableKey())
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true))
    check(registry().hooks.size == 1)
    prefs().toggle(false, enableKey())
    check(registry().hooks.isEmpty())

    // 9) 小程序进程：桥类缺失时不装 Hook，也不留空所有者
    resetHost()
    prefs().toggle(true, enableKey())
    check(
        !SkipMiniProgramVideoAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(registry().hooks.isEmpty())
    t.mma.utils.KavaReflector.bridgeFor(ProcessLoader.INSTANCE, ProcessLoader.BRIDGE)
    check(
        SkipMiniProgramVideoAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(registry().hooks.size == 1)
    val processHook = registry().hooks.single()

    // 10) 进程 Hook 同样只改视频进度并受设置门控
    val processProgress = arrayOf<Any?>("onVideoTimeUpdate", payload(), 0, "extra")
    fire(processHook, processProgress)
    check(processProgress[1] == rewritten()) { "process hook must rewrite progress" }
    prefs().enabled = false
    val processGated = arrayOf<Any?>("onVideoTimeUpdate", payload(), 0, "extra")
    fire(processHook, processGated)
    check(processGated[1] == payload()) { "process hook must honour the disabled preference" }
    prefs().enabled = true

    // 11) 重复安装不叠加
    check(
        SkipMiniProgramVideoAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(registry().hooks.size == 1)

    // 12) 设置关闭后进程所有者显式释放并可重建
    prefs().enabled = false
    SkipMiniProgramVideoAdsFeature.destroyProcessHooks()
    check(registry().hooks.isEmpty())
    check(processHook.unhooked)
    prefs().enabled = true
    check(
        SkipMiniProgramVideoAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(registry().hooks.size == 1)

    // 13) loader 换代先释放旧所有者，再装新 Hook
    val oldHook = registry().hooks.single()
    val nextLoader = object : ClassLoader(ProcessLoader.INSTANCE) {}
    t.mma.utils.KavaReflector.bridgeFor(nextLoader, ProcessLoader.BRIDGE)
    check(
        SkipMiniProgramVideoAdsFeature.installAppBrandProcessHook(
            android.content.Context(), nextLoader
        )
    )
    check(oldHook.unhooked) { "old loader hook must be released on loader replacement" }
    check(registry().hooks.size == 1)
    check(registry().hooks.single() !== oldHook)

    // 14) 关闭设置时安装入口自释放
    prefs().enabled = false
    check(
        !SkipMiniProgramVideoAdsFeature.installAppBrandProcessHook(
            android.content.Context(), nextLoader
        )
    )
    check(registry().hooks.isEmpty())

    // 15) unhook 抛异常不阻断本地所有权释放，错误被隔离上报
    prefs().enabled = true
    t.mma.utils.HLog.logs.clear()
    check(
        SkipMiniProgramVideoAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    registry().failUnhook = true
    SkipMiniProgramVideoAdsFeature.destroyProcessHooks()
    registry().failUnhook = false
    check(registry().hooks.isEmpty())
    check(releaseErrors() == 1) {
        "unhook failure must be reported once, got " + t.mma.utils.HLog.logs.toString()
    }
    check(
        SkipMiniProgramVideoAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    ) { "unhook failure must not block a later install" }
    SkipMiniProgramVideoAdsFeature.destroyProcessHooks()
    check(registry().hooks.isEmpty())

    // 16) 100 代开关：每代一个运行时、一个票根、一个订阅，关闭代不累积
    resetHost()
    t.mma.utils.KavaReflector.bridgeFor(ProcessLoader.INSTANCE, ProcessLoader.BRIDGE)
    val churn = SkipMiniProgramVideoAdsFeature()
    val churnContext = context()
    churn.onFeatureInstall(churnContext)
    repeat(100) { generation ->
        prefs().toggle(true, enableKey())
        check(scheduler().installations.size == generation + 1)
        check(churnContext.eventBus().size() == 1) {
            "generation ${generation + 1} must own one subscription"
        }
        val generationTicket = scheduler().installations.last()
        check(scheduler().drain() == listOf(true))
        check(registry().hooks.size == 1)
        prefs().toggle(false, enableKey())
        check(generationTicket.closed && registry().hooks.isEmpty())
        check(churnContext.eventBus().size() == 0)
    }

    // 17) 代内重复 DexReady 不重复安装；关闭代不再调度
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true))
    val generations = scheduler().installations.size
    churnContext.eventBus().post(t.mma.event.Events.DexReady())
    check(scheduler().drain() == listOf(true)) {
        "repeated DexReady must not reinstall an installed hook"
    }
    check(registry().hooks.size == 1)
    prefs().toggle(false, enableKey())
    check(scheduler().installations.size == generations)
    check(scheduler().drain().isEmpty()) { "closed generation must not reinstall" }

    // 18) Feature 释放链：关闭后不再产生新代，监听器与订阅解绑
    resetHost()
    val last = SkipMiniProgramVideoAdsFeature()
    last.onFeatureInstall(context())
    t.mma.utils.KavaReflector.bridgeFor(ProcessLoader.INSTANCE, ProcessLoader.BRIDGE)
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true))
    check(registry().hooks.size == 1)
    val before = scheduler().installations.size
    last.releaseAll()
    check(registry().hooks.isEmpty())
    check(prefs().listeners.isEmpty()) { "preference listener must be released" }
    prefs().enabled = true
    prefs().toggle(true, enableKey())
    check(scheduler().installations.size == before) {
        "closed feature must not create new generations"
    }
    check(registry().hooks.isEmpty()) { "no hook may outlive the feature" }

    // 19) 两个 owner 精确释放，不互相牵连
    resetHost()
    t.mma.utils.KavaReflector.bridgeFor(ProcessLoader.INSTANCE, ProcessLoader.BRIDGE)
    val first = SkipMiniProgramVideoAdsFeature()
    val second = SkipMiniProgramVideoAdsFeature()
    val firstContext = context()
    val secondContext = context()
    first.onFeatureInstall(firstContext)
    second.onFeatureInstall(secondContext)
    check(firstContext.eventBus().size() == 0 && secondContext.eventBus().size() == 0)
    prefs().toggle(true, enableKey())
    check(firstContext.eventBus().size() == 1 && secondContext.eventBus().size() == 1)
    check(scheduler().drain() == listOf(true, true)) { "both generations must install" }
    check(registry().hooks.size == 2)
    val surviving = registry().hooks.last()
    first.releaseAll()
    check(registry().hooks.size == 1) { "releasing one feature must not unhook the other" }
    check(registry().hooks.single() === surviving)
    second.releaseAll()
    check(registry().hooks.isEmpty())
    check(prefs().listeners.isEmpty())

    println(
        "PASS production SkipMiniProgramVideoAds: disabled startup; per-interval runtime owning one " +
            "installation ticket and one DexReady subscription; onVideoTimeUpdate payload rewrite with " +
            "non-object and non-string passthrough; live preference gate in both main and appbrand owners; " +
            "release on toggle; retry after missing bridge and hook failure; appbrand process owner with " +
            "dedupe, loader replacement, disabled-entry release and unhook failure isolation; 100 toggle " +
            "generations with one ticket and one subscription each; repeated DexReady dedupe; feature scope " +
            "release; two independent owners"
    )
}