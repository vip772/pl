package t.mma.hooks.items.miniprogrambaselib

import de.robv.android.xposed.XC_MethodHook

/**
 * Synthetic WeChat host for the FakeMiniProgramBaseLib regression: a 17-parameter
 * request constructor, a private open-URL method, synthetic DexKit results and a
 * synthetic preference store. Not a device test.
 */
object SyntheticHost {
    fun launchConstructor(): java.lang.reflect.Constructor<*> =
        com.tencent.mm.plugin.appbrand.jsapi.CgiLaunchHost::class.java
            .declaredConstructors
            .single { it.parameterTypes.size == 17 }

    fun privateOpenUrlMethod(): java.lang.reflect.Method =
        com.tencent.mm.plugin.appbrand.jsapi.CgiLaunchHost::class.java
            .declaredMethods
            .single { it.name == "privateOpenUrl" }

    fun seedDexKit() {
        org.luckypray.dexkit.BridgeState.reset()
        org.luckypray.dexkit.BridgeState.results.add(
            org.luckypray.dexkit.DexMethodData(launchConstructor(), null)
        )
        org.luckypray.dexkit.BridgeState.results.add(
            org.luckypray.dexkit.DexMethodData(null, privateOpenUrlMethod())
        )
    }

    fun reset() {
        t.mma.dexkit.DexMethodCache.reset()
        t.mma.hooks.core.DexInstallScheduler.reset()
        t.mma.hooks.core.HookRegistry.get().reset()
        t.mma.preferences.HchatStorage.reset()
        t.mma.utils.HLog.logs.clear()
        com.tencent.mm.plugin.appbrand.jsapi.CgiLaunchHost.calls = 0
        com.tencent.mm.plugin.appbrand.jsapi.CgiLaunchHost.observer = null
        seedDexKit()
        FakeMiniProgramBaseLibFeature.destroyProcessHooks()
    }

    fun fireBefore(unhook: XC_MethodHook.Unhook, args: Array<Any?>): XC_MethodHook.MethodHookParam {
        val param = XC_MethodHook.MethodHookParam(args, Any())
        unhook.callback.beforeHookedMethod(param)
        return param
    }
}