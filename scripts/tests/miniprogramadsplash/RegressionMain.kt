package t.mma.hooks.items.miniprogramsplashad

import de.robv.android.xposed.XC_MethodHook

fun enableKey() = SkipGlobalMiniProgramSplashAdsSettings.KEY_ENABLE

fun prefs(): t.mma.preferences.FakePrefs =
    t.mma.preferences.HchatStorage.store(SkipGlobalMiniProgramSplashAdsSettings.PREFS_NAME)

/** One loader per synthetic process. */
object ProcessLoader {
    val INSTANCE: ClassLoader = object : ClassLoader(ProcessLoader::class.java.classLoader) {}
}

fun context(): t.mma.hooks.core.FeatureContext = t.mma.hooks.core.FeatureContext(
    android.content.Context(),
    ProcessLoader.INSTANCE
)

fun splashMethod(): java.lang.reflect.Method =
    com.tencent.mm.plugin.appbrand.fake.SplashAdHost::class.java
        .getDeclaredMethod("canShowAd", com.tencent.mm.plugin.appbrand.fake.AppBrandContact::class.java)

fun resetHost() {
    t.mma.utils.HLog.logs.clear()
    t.mma.preferences.HchatStorage.reset()
    t.mma.dexkit.DexMethodCache.reset()
    t.mma.hooks.core.DexInstallScheduler.reset()
    t.mma.hooks.core.HookRegistry.get().reset()
    org.luckypray.dexkit.BridgeState.reset()
    SkipGlobalMiniProgramSplashAdsFeature.destroyProcessHooks()
    seedDexKit()
}

fun seedDexKit() {
    org.luckypray.dexkit.BridgeState.reset()
    org.luckypray.dexkit.BridgeState.results.add(
        org.luckypray.dexkit.DexMethodData(null, splashMethod())
    )
}

fun registry() = t.mma.hooks.core.HookRegistry.get()

fun scheduler() = t.mma.hooks.core.DexInstallScheduler

fun fire(hook: XC_MethodHook.Unhook): XC_MethodHook.MethodHookParam {
    val param = XC_MethodHook.MethodHookParam(
        arrayOf<Any?>(com.tencent.mm.plugin.appbrand.fake.AppBrandContact()),
        Any()
    )
    hook.callback.beforeHookedMethod(param)
    return param
}

fun releaseErrors(): Int = t.mma.utils.HLog.logs.count { it.contains("释放失败") }

fun main() {
    // 1) 关闭状态安装：只留 preference owner，不建运行时、不调度、不装 Hook、不订阅
    resetHost()
    val feature = SkipGlobalMiniProgramSplashAdsFeature()
    val featureContext = context()
    feature.onFeatureInstall(featureContext)
    check(feature.tracked.size == 1) { "preference owner must stay listenable while disabled" }
    check(scheduler().installations.isEmpty())
    check(registry().hooks.isEmpty())
    check(featureContext.eventBus().size() == 0)
    check(org.luckypray.dexkit.BridgeState.queries == 0) { "disabled startup must not query DexKit" }

    // 2) 打开开关：一代运行时、一个票根、一个订阅；DexKit 只查一次并写回描述符
    prefs().toggle(true, enableKey())
    check(scheduler().installations.size == 1)
    check(featureContext.eventBus().size() == 1) { "one DexReady subscription per generation" }
    val ticket = scheduler().installations.single()
    check(scheduler().drain() == listOf(true)) { "install must succeed" }
    check(org.luckypray.dexkit.BridgeState.queries == 1) { "DexKit must be queried once" }
    check(
        t.mma.dexkit.DexMethodCache.store[SkipGlobalMiniProgramSplashAdsFeature.CACHE_SPLASH_AD_CHECK_METHOD] != null
    ) { "located descriptor must be cached" }
    check(registry().hooks.size == 1)
    val hook = registry().hooks.single()

    // 3) 资格方法被强制返回 false；设置关闭后不再改写
    check(fire(hook).result == false) { "splash ad eligibility must be forced false" }
    prefs().enabled = false
    check(fire(hook).result == null) { "disabled preference must gate the live hook" }
    prefs().enabled = true

    // 4) 关开关释放运行时、票根、订阅与 Hook
    prefs().toggle(false, enableKey())
    check(ticket.closed) { "installation ticket must be cancelled with the generation" }
    check(registry().hooks.isEmpty())
    check(hook.unhooked)
    check(featureContext.eventBus().size() == 0) { "subscription must be released with the generation" }

    // 5) 缓存描述符可重装，不再查 DexKit（不共享 schedule、不捕获旧 runtime）
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true))
    check(org.luckypray.dexkit.BridgeState.queries == 1) { "cached descriptor must skip DexKit" }
    check(registry().hooks.size == 1)
    val secondHook = registry().hooks.single()
    check(secondHook !== hook) { "a new generation must own a new hook handle" }
    check(fire(secondHook).result == false)

    // 6) 旧代 Hook 已解绑：旧句柄回调不再改写
    check(fire(hook).result == null) { "released generation must not rewrite anymore" }
    prefs().toggle(false, enableKey())
    check(registry().hooks.isEmpty())

    // 7) 描述符缺失：返回 false 且清缓存，恢复后可重试
    t.mma.dexkit.DexMethodCache.store.clear()
    org.luckypray.dexkit.BridgeState.reset()
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(false)) { "missing descriptor must not install" }
    check(registry().hooks.isEmpty())
    check(
        t.mma.dexkit.DexMethodCache.cleared.contains(
            SkipGlobalMiniProgramSplashAdsFeature.CACHE_SPLASH_AD_CHECK_METHOD
        )
    ) { "missing descriptor must clear the cache" }
    seedDexKit()
    prefs().toggle(false, enableKey())
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true)) { "retry after descriptor restore" }
    check(registry().hooks.size == 1)
    prefs().toggle(false, enableKey())
    check(registry().hooks.isEmpty())

    // 8) 候选不唯一：不安装且清缓存
    t.mma.dexkit.DexMethodCache.store.clear()
    t.mma.dexkit.DexMethodCache.cleared.clear()
    org.luckypray.dexkit.BridgeState.reset()
    org.luckypray.dexkit.BridgeState.results.add(
        org.luckypray.dexkit.DexMethodData(null, splashMethod())
    )
    org.luckypray.dexkit.BridgeState.results.add(
        org.luckypray.dexkit.DexMethodData(
            null,
            com.tencent.mm.plugin.appbrand.fake.SecondSplashAdHost::class.java.getDeclaredMethod(
                "canShowAd",
                com.tencent.mm.plugin.appbrand.fake.AppBrandContact::class.java
            )
        )
    )
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(false)) { "ambiguous candidates must not install" }
    check(registry().hooks.isEmpty())
    check(t.mma.utils.HLog.logs.any { it.contains("候选不唯一") }) {
        "ambiguous candidates must be reported, got " + t.mma.utils.HLog.logs.toString()
    }
    prefs().toggle(false, enableKey())

    // 9) Hook 安装抛异常：无残留句柄，可重试
    seedDexKit()
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

    // 10) 定位阶段抛异常：错误隔离上报，不装 Hook
    t.mma.dexkit.DexMethodCache.store.clear()
    org.luckypray.dexkit.BridgeState.reset()
    org.luckypray.dexkit.BridgeState.failQueries = true
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(false))
    check(registry().hooks.isEmpty())
    check(t.mma.utils.HLog.logs.any { it.contains("定位小程序开屏广告资格方法失败") }) {
        "locator failure must be reported, got " + t.mma.utils.HLog.logs.toString()
    }
    org.luckypray.dexkit.BridgeState.failQueries = false
    seedDexKit()
    prefs().toggle(false, enableKey())
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true))
    check(registry().hooks.size == 1)
    prefs().toggle(false, enableKey())

    // 11) 主进程 100 代：每代一票根一订阅一 Hook，关闭代不累积
    resetHost()
    val churn = SkipGlobalMiniProgramSplashAdsFeature()
    val churnContext = context()
    churn.onFeatureInstall(churnContext)
    repeat(100) { generation ->
        prefs().toggle(true, enableKey())
        check(scheduler().installations.size == generation + 1)
        check(churnContext.eventBus().size() == 1) {
            "generation ${generation + 1} must own one subscription"
        }
        val genTicket = scheduler().installations.last()
        check(scheduler().drain() == listOf(true))
        check(registry().hooks.size == 1)
        check(fire(registry().hooks.single()).result == false)
        prefs().toggle(false, enableKey())
        check(genTicket.closed && registry().hooks.isEmpty())
        check(churnContext.eventBus().size() == 0)
    }

    // 12) 代内重复 DexReady 不重复安装；关闭代不再调度
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true))
    val generations = scheduler().installations.size
    val queriesBeforeReady = org.luckypray.dexkit.BridgeState.queries
    churnContext.eventBus().post(t.mma.event.Events.DexReady())
    check(scheduler().drain() == listOf(true)) {
        "repeated DexReady must not reinstall an installed hook"
    }
    check(org.luckypray.dexkit.BridgeState.queries == queriesBeforeReady) {
        "installed generation must serve DexReady from cache"
    }
    check(registry().hooks.size == 1)
    prefs().toggle(false, enableKey())
    check(scheduler().installations.size == generations)
    check(scheduler().drain().isEmpty()) { "closed generation must not reinstall" }

    // 13) 小程序进程：描述符缺失时登记重试票根，不装 Hook、不留空所有者
    resetHost()
    prefs().toggle(true, enableKey())
    t.mma.dexkit.DexMethodCache.crossProcessReady = false
    check(
        !SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(registry().hooks.isEmpty())
    check(scheduler().pending.size == 1) { "missing descriptor must register one process retry ticket" }
    check(scheduler().drain() == listOf(false)) {
        "process ticket must not install while the descriptor is missing"
    }
    check(registry().hooks.isEmpty())

    // 14) 缓存补齐后同一入口装成 Hook；重复安装不叠加
    t.mma.dexkit.DexMethodCache.crossProcessReady = true
    t.mma.dexkit.DexMethodCache.saveCrossProcess(
        SkipGlobalMiniProgramSplashAdsFeature.CACHE_SPLASH_AD_CHECK_METHOD,
        splashMethod()
    )
    check(
        SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(registry().hooks.size == 1)
    val processHook = registry().hooks.single()
    check(fire(processHook).result == false) { "process hook must force false" }
    prefs().enabled = false
    check(fire(processHook).result == null) { "process hook must honour the disabled preference" }
    prefs().enabled = true
    check(
        SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(registry().hooks.size == 1)

    // 15) 设置关闭后进程所有者显式释放并可重建
    prefs().enabled = false
    SkipGlobalMiniProgramSplashAdsFeature.destroyProcessHooks()
    check(registry().hooks.isEmpty())
    check(processHook.unhooked)
    prefs().enabled = true
    check(
        SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(registry().hooks.size == 1)

    // 16) loader 换代先释放旧 Hook
    val oldHook = registry().hooks.single()
    val nextLoader = object : ClassLoader(ProcessLoader.INSTANCE) {}
    check(
        SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), nextLoader
        )
    )
    check(oldHook.unhooked) { "old loader hook must be released on loader replacement" }
    check(registry().hooks.size == 1)
    check(registry().hooks.single() !== oldHook)

    // 17) 关闭设置时安装入口自释放；unhook 异常隔离上报
    prefs().enabled = false
    check(
        !SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), nextLoader
        )
    )
    check(registry().hooks.isEmpty())
    prefs().enabled = true
    t.mma.utils.HLog.logs.clear()
    check(
        SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    registry().failUnhook = true
    SkipGlobalMiniProgramSplashAdsFeature.destroyProcessHooks()
    registry().failUnhook = false
    check(registry().hooks.isEmpty())
    check(releaseErrors() == 1) {
        "unhook failure must be reported once, got " + t.mma.utils.HLog.logs.toString()
    }
    check(
        SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    ) { "unhook failure must not block a later install" }
    SkipGlobalMiniProgramSplashAdsFeature.destroyProcessHooks()
    check(registry().hooks.isEmpty())

    // 18) 进程票根归属：关闭所有者时一起作废，关闭后迟到的 drain 不再装 Hook
    t.mma.dexkit.DexMethodCache.crossProcessReady = false
    check(
        !SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(scheduler().pending.size == 1)
    SkipGlobalMiniProgramSplashAdsFeature.destroyProcessHooks()
    check(scheduler().pending.isEmpty()) { "cancelled process ticket must be dropped" }
    check(scheduler().drain().isEmpty()) { "cancelled process ticket must not be schedulable" }
    check(registry().hooks.isEmpty())
    t.mma.dexkit.DexMethodCache.crossProcessReady = true

    // 19) Feature 释放链：关闭后不再产生新代、监听器解绑、进程所有者销毁
    resetHost()
    val last = SkipGlobalMiniProgramSplashAdsFeature()
    last.onFeatureInstall(context())
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true))
    check(
        SkipGlobalMiniProgramSplashAdsFeature.installAppBrandProcessHook(
            android.content.Context(), ProcessLoader.INSTANCE
        )
    )
    check(registry().hooks.size == 2)
    val before = scheduler().installations.size
    last.releaseAll()
    SkipGlobalMiniProgramSplashAdsFeature.destroyProcessHooks()
    check(registry().hooks.isEmpty()) { "no hook may outlive the feature or process owner" }
    check(prefs().listeners.isEmpty()) { "preference listener must be released" }
    prefs().enabled = true
    prefs().toggle(true, enableKey())
    check(scheduler().installations.size == before) {
        "closed feature must not create new generations"
    }

    // 20) 两个 owner 精确释放，不互相牵连
    resetHost()
    val first = SkipGlobalMiniProgramSplashAdsFeature()
    val second = SkipGlobalMiniProgramSplashAdsFeature()
    val firstContext = context()
    val secondContext = context()
    first.onFeatureInstall(firstContext)
    second.onFeatureInstall(secondContext)
    prefs().toggle(true, enableKey())
    check(scheduler().drain() == listOf(true, true))
    check(registry().hooks.size == 2)
    val surviving = registry().hooks.last()
    first.releaseAll()
    check(registry().hooks.size == 1) { "releasing one feature must not unhook the other" }
    check(registry().hooks.single() === surviving)
    second.releaseAll()
    check(registry().hooks.isEmpty())

    println(
        "PASS production SkipGlobalMiniProgramSplashAds: disabled startup without DexKit query; per-interval " +
            "main-process runtime owning one ticket, one subscription and one hook handle; eligibility forced " +
            "false with live preference gate; cached descriptor reuse without a DexKit requery; missing " +
            "descriptor and ambiguous candidates clear the cache and skip installation; locator and hook " +
            "failure isolation with retry; appbrand process owner with retry ticket, dedupe, loader " +
            "replacement, disabled-entry release and unhook failure isolation; 100 main-process generations " +
            "with one ticket and one subscription each; repeated DexReady dedupe; feature scope release; " +
            "two independent owners"
    )
}