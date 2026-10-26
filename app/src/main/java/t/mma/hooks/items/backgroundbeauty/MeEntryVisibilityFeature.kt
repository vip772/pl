package t.mma.hooks.items.backgroundbeauty

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

class MeEntryVisibilityFeature : BaseFeature() {
    private var runtime: MeEntryVisibilityRuntime? = null
    override fun featureId() = ID
    override fun name() = "隐藏条目"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MeEntryVisibilitySettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        runtime = MeEntryVisibilityRuntime(context).also { it.install() }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }
    companion object { const val ID = "me_entry_visibility" }
}

internal class MeEntryVisibilityRuntime(private val context: FeatureContext) {
    private val prefs = HchatStorage.preferences(context.hostContext(), BackgroundBeautySettings.PREFS_NAME)
    private val main = Handler(Looper.getMainLooper())
    private val ownedHooks = mutableListOf<XC_MethodHook.Unhook>()
    private val meEntryAdapters = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val meEntryOverlays = IdentityHashMap<Any, Set<String>>()
    @Volatile private var requestedMeHiddenKeys = MeEntryVisibility.hiddenKeys { prefs.getBoolean(it, false) }
    private var installed = false
    private var destroyed = false
    private var meEntryFailureLogged = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || MeEntryVisibility.Entry.values().any { it.key == key }) {
            requestedMeHiddenKeys = MeEntryVisibility.hiddenKeys { prefs.getBoolean(it, false) }
            main.post {
                if (!destroyed) {
                    installMeEntryVisibility()
                    meEntryAdapters.toList().forEach { refreshMeEntries(it) }
                }
            }
        }
    }
    fun install() {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        installMeEntryVisibility()
    }
    fun destroy() {
        destroyed = true
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        ownedHooks.forEach { runCatching { HookRegistry.get().unhook(it) } }
        ownedHooks.clear()
        meEntryAdapters.toList().forEach { refreshMeEntries(it, restoring = true) }
        meEntryAdapters.clear()
        meEntryOverlays.clear()
    }
    // Verified 8.0.78: MoreTabUI.s -> g0; q() builds visible rows from e/f minus g,
    // then merges empty categories. Only adapters owned by MoreTabUI are filtered.
    private fun installMeEntryVisibility() {
        if (installed || destroyed || requestedMeHiddenKeys.isEmpty()) return
        if (WeChatApis.version()?.versionName() != "8.0.78") return
        val adapterClass = KavaReflector.loadClass("com.tencent.mm.ui.base.preference.g0", context.hostClassLoader()) ?: return
        val fragmentClass = KavaReflector.loadClass(ME_FRAGMENT, context.hostClassLoader()) ?: return
        val rebuild = KavaReflector.findMethod(adapterClass, "q") ?: return
        val hiddenField = KavaReflector.findFieldRecursive(adapterClass, "g") ?: return
        val adapterField = KavaReflector.findFieldRecursive(fragmentClass, "s") ?: return
        val lifecycleMethods = listOfNotNull(
            KavaReflector.findMethod(fragmentClass, "onActivityCreated", Bundle::class.java),
            KavaReflector.findMethod(fragmentClass, "onResume")
        ).distinct()
        if (lifecycleMethods.isEmpty()) return
        ownedHooks.add(HookRegistry.get().hook(rebuild, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (destroyed || requestedMeHiddenKeys.isEmpty() || param.thisObject !in meEntryAdapters) return
                try {
                    @Suppress("UNCHECKED_CAST")
                    val hidden = KavaReflector.readField(hiddenField, param.thisObject) as? MutableSet<String> ?: return
                    meEntryOverlays[param.thisObject] = MeEntryVisibility.overlay(hidden, requestedMeHiddenKeys)
                } catch (error: Throwable) { logMeEntryFailure(error) }
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                val added = meEntryOverlays.remove(param.thisObject) ?: return
                try {
                    @Suppress("UNCHECKED_CAST")
                    val hidden = KavaReflector.readField(hiddenField, param.thisObject) as? MutableSet<String> ?: return
                    hidden.removeAll(added)
                } catch (error: Throwable) { logMeEntryFailure(error) }
            }
        }))
        lifecycleMethods.forEach { method ->
            ownedHooks.add(HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (destroyed || !fragmentClass.isInstance(param.thisObject)) return
                    val adapter = KavaReflector.readField(adapterField, param.thisObject)
                    if (adapter != null && adapterClass.isInstance(adapter)) {
                        meEntryAdapters.add(adapter)
                        refreshMeEntries(adapter)
                    }
                }
            }))
        }
        installed = true
    }
    private fun refreshMeEntries(adapter: Any, restoring: Boolean = false) {
        try {
            if (restoring) meEntryAdapters.remove(adapter)
            val method = KavaReflector.findMethod(adapter.javaClass, "q") ?: return
            KavaReflector.invokeOrThrow(method, adapter)
        } catch (error: Throwable) { logMeEntryFailure(error) }
    }

    private fun logMeEntryFailure(error: Throwable) {
        if (meEntryFailureLogged) return
        meEntryFailureLogged = true
        HLog.e("$TAG 微信8.0.78 我页 g0.q()/g 条目过滤失败", error)
    }

    companion object {
        private const val TAG = "隐藏条目"
        private const val ME_FRAGMENT = "com.tencent.mm.ui.MoreTabUI"
    }
}
