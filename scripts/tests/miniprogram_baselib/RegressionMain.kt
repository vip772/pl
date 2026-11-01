package t.mma.hooks.items.miniprogrambaselib

import de.robv.android.xposed.XC_MethodHook

fun prefs() = t.mma.preferences.HchatStorage.prefs

fun newContext() = t.mma.hooks.core.FeatureContext(
    android.content.Context(),
    SyntheticHost::class.java.classLoader
)

fun launchArgs(version: Int = 1): Array<Any?> = arrayOf(
    "cgi", false, null, null, null, "session", version,
    com.tencent.mm.plugin.appbrand.report.quality.QualitySession(),
    "instance", com.tencent.mm.plugin.appbrand.config.AppBrandLaunchFromNotifyReferrer(),
    "source", null, 1, false, "fallback", false, false
)

fun releaseErrors(): Int = t.mma.utils.HLog.logs.count { it.contains("释放失败") }

fun main() {
    // 1) 关闭状态安装：只留 preference owner，不建 runtime、不调度、不装 Hook、不订阅
    SyntheticHost.reset()
    val feature = FakeMiniProgramBaseLibFeature()
    val context = newContext()
    feature.onFeatureInstall(context)
    check(feature.tracked.size == 1) { "preference owner must stay listenable while disabled" }
    check(t.mma.hooks.core.DexInstallScheduler.installations.isEmpty())
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())
    check(context.eventBus().size() == 0)

    // 2) 打开开关后建立一代 runtime，装上构造器 Hook 并订阅 DexReady
    prefs().toggle(true)
    check(t.mma.hooks.core.DexInstallScheduler.installations.size == 1)
    check(context.eventBus().size() == 1) { "one DexReady subscription per generation" }
    val ticket = t.mma.hooks.core.DexInstallScheduler.installations.single()
    check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(true)) { "install must succeed" }
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)
    val constructorHook = t.mma.hooks.core.HookRegistry.get().hooks.single()

    // 3) 基础库版本伪装只改第 7 个参数（索引 6），其余参数保持原值
    val args = launchArgs(version = 1)
    SyntheticHost.fireBefore(constructorHook, args)
    check(args[6] == 9999) { "lib version must be spoofed, got ${args[6]}" }
    check(args[0] == "cgi" && args[5] == "session" && args[9] != null && args[16] == false)

    // 4) 设置关闭后同一 Hook 立即失效（回调门，不依赖重建）
    prefs().enabled = false
    val gated = launchArgs(version = 7)
    SyntheticHost.fireBefore(constructorHook, gated)
    check(gated[6] == 7) { "disabled preference must gate the live hook" }
    prefs().enabled = true

    // 5) 关开关释放 runtime、票根、订阅与 Hook
    prefs().toggle(false)
    check(ticket.closed) { "installation ticket must be cancelled with the generation" }
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())
    check(constructorHook.unhooked)
    check(context.eventBus().size() == 0) { "subscription must be released with the generation" }

    // 6) 再次打开能重新安装（不是一次性布尔）
    prefs().toggle(true)
    check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(true))
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)
    check(context.eventBus().size() == 1)
    prefs().toggle(false)
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())
    check(context.eventBus().size() == 0)

    // 7) 定位失败返回 false 不装 Hook，恢复后可重试
    SyntheticHost.reset()
    feature.onFeatureInstall(newContext())
    prefs().toggle(true)
    t.mma.dexkit.DexMethodCache.reset()
    org.luckypray.dexkit.BridgeState.results.clear()
    check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(false)) { "missing dex must not install" }
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())
    SyntheticHost.seedDexKit()
    prefs().toggle(false)
    prefs().toggle(true)
    check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(true)) { "retry after dex restore" }
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)
    prefs().toggle(false)
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())

    // 8) Hook 安装抛异常：句柄不残留，恢复后可重试
    prefs().toggle(true)
    t.mma.hooks.core.HookRegistry.get().failHook = true
    check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(false))
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty()) { "failed hook must not leave a handle" }
    t.mma.hooks.core.HookRegistry.get().failHook = false
    prefs().toggle(false)
    prefs().toggle(true)
    check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(true))
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)
    prefs().toggle(false)
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())

    // 9) 小程序进程：Dex 描述符缺失时不装 Hook，也不留空 owner
    SyntheticHost.reset()
    prefs().enabled = true
    t.mma.dexkit.DexMethodCache.crossProcessReady = false
    check(
        !FakeMiniProgramBaseLibFeature.installAppBrandProcessHook(
            android.content.Context(), SyntheticHost::class.java.classLoader
        )
    )
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())

    // 10) 描述符命中后 appbrand Hook 安装一次并只拦截官方升级页
    t.mma.dexkit.DexMethodCache.crossProcessReady = true
    t.mma.dexkit.DexMethodCache.store[FakeMiniProgramBaseLibFeature.CACHE_PRIVATE_OPEN_URL_METHOD] =
        SyntheticHost.privateOpenUrlMethod()
    val loader = SyntheticHost::class.java.classLoader
    check(FakeMiniProgramBaseLibFeature.installAppBrandProcessHook(android.content.Context(), loader))
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)
    val appBrandHook = t.mma.hooks.core.HookRegistry.get().hooks.single()
    val updateRequest = org.json.JSONObject().put("url", "https://support.weixin.qq.com/update/more")
    SyntheticHost.fireBefore(appBrandHook, arrayOf("private_openUrl", updateRequest, 0))
    check(updateRequest.optString("url") == "") { "official update URL must be neutralised" }
    val szRequest = org.json.JSONObject().put("url", "https://szsupport.weixin.qq.com/update")
    SyntheticHost.fireBefore(appBrandHook, arrayOf("private_openUrl", szRequest, 0))
    check(szRequest.optString("url") == "") { "szsupport update URL must be neutralised" }
    val otherRequest = org.json.JSONObject().put("url", "https://example.com/page")
    SyntheticHost.fireBefore(appBrandHook, arrayOf("private_open_url", otherRequest, 0))
    check(otherRequest.optString("url") == "https://example.com/page") { "unrelated URL must stay untouched" }

    // 11) 重复安装不叠加 Hook
    check(FakeMiniProgramBaseLibFeature.installAppBrandProcessHook(android.content.Context(), loader))
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)

    // 12) 设置关闭后进程 owner 显式释放并可重建
    prefs().enabled = false
    FakeMiniProgramBaseLibFeature.destroyProcessHooks()
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())
    check(appBrandHook.unhooked)
    prefs().enabled = true
    check(FakeMiniProgramBaseLibFeature.installAppBrandProcessHook(android.content.Context(), loader))
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)

    // 13) loader 换代先释放旧 owner，再装新 Hook
    val oldHook = t.mma.hooks.core.HookRegistry.get().hooks.single()
    val nextLoader = object : ClassLoader(SyntheticHost::class.java.classLoader) {}
    check(FakeMiniProgramBaseLibFeature.installAppBrandProcessHook(android.content.Context(), nextLoader))
    check(oldHook.unhooked) { "old loader hook must be released on loader replacement" }
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)
    check(t.mma.hooks.core.HookRegistry.get().hooks.single() !== oldHook)

    // 14) 关闭设置时安装入口自己释放
    prefs().enabled = false
    check(!FakeMiniProgramBaseLibFeature.installAppBrandProcessHook(android.content.Context(), nextLoader))
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())

    // 15) unhook 抛异常不阻断本地所有权释放，错误被隔离上报
    prefs().enabled = true
    check(FakeMiniProgramBaseLibFeature.installAppBrandProcessHook(android.content.Context(), loader))
    t.mma.hooks.core.HookRegistry.get().failUnhook = true
    FakeMiniProgramBaseLibFeature.destroyProcessHooks()
    t.mma.hooks.core.HookRegistry.get().failUnhook = false
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())
    check(releaseErrors() == 1) {
        "unhook failure must be reported and isolated, got " + t.mma.utils.HLog.logs.toString()
    }

    // 17) 100 代开关：每代一个 runtime、一个票根、一个订阅，关闭代不累积
    SyntheticHost.reset()
    val churn = FakeMiniProgramBaseLibFeature()
    val churnContext = newContext()
    churn.onFeatureInstall(churnContext)
    repeat(100) { generation ->
        prefs().toggle(true)
        check(t.mma.hooks.core.DexInstallScheduler.installations.size == generation + 1)
        check(churnContext.eventBus().size() == 1) { "generation ${generation + 1} must own one subscription" }
        val genTicket = t.mma.hooks.core.DexInstallScheduler.installations.last()
        check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(true))
        check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)
        prefs().toggle(false)
        check(genTicket.closed && t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())
        check(churnContext.eventBus().size() == 0)
    }

    // 18) 关闭后迟到的 DexReady 闭包不得再调度安装
    prefs().toggle(true)
    val staleContext = churnContext
    val staleGeneration = t.mma.hooks.core.DexInstallScheduler.installations.size
    // 订阅本身已随代释放；这里再验证代内重复 DexReady 不会重复安装
    check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(true))
    staleContext.eventBus().post(t.mma.event.Events.DexReady())
    check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(true)) {
        "repeated DexReady must not reinstall an installed hook"
    }
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)
    prefs().toggle(false)
    check(t.mma.hooks.core.DexInstallScheduler.installations.size == staleGeneration)
    check(t.mma.hooks.core.DexInstallScheduler.drain().isEmpty()) { "closed generation must not reinstall" }

    // 19) Feature 释放链：关闭后不再产生新代，监听器与订阅解绑
    SyntheticHost.reset()
    val last = FakeMiniProgramBaseLibFeature()
    last.onFeatureInstall(newContext())
    prefs().toggle(true)
    check(t.mma.hooks.core.DexInstallScheduler.drain() == listOf(true))
    check(t.mma.hooks.core.HookRegistry.get().hooks.size == 1)
    val generationsBefore = t.mma.hooks.core.DexInstallScheduler.installations.size
    last.releaseAll()
    check(t.mma.hooks.core.HookRegistry.get().hooks.isEmpty())
    check(prefs().listeners.isEmpty()) { "preference listener must be released" }
    prefs().enabled = true
    prefs().toggle(true)
    check(t.mma.hooks.core.DexInstallScheduler.installations.size == generationsBefore) {
        "closed feature must not create new generations"
    }

    println(
        "PASS production FakeMiniProgramBaseLib: disabled startup; per-interval main-process runtime; " +
            "lib version argument spoof; live preference gate; release on toggle; retry after missing dex " +
            "and hook failure; appbrand process owner with cross-process descriptor, dedupe, loader " +
            "replacement, disabled-entry release, unhook failure isolation; 100 toggle generations with " +
            "one ticket and one subscription each; repeated DexReady dedupe; feature scope release"
    )
}