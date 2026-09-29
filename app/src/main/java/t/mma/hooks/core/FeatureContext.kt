package t.mma.hooks.core

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage
import t.mma.dexkit.DexBridgeHolder
import t.mma.dexkit.DexFinder
import t.mma.event.EventBus
import t.mma.preferences.ConfigStore
import t.mma.ui.UIRegistry
import org.luckypray.dexkit.DexKitBridge

/**
 * 功能模块运行时上下文。
 */
class FeatureContext(
    private val hostContext: Context,
    private val moduleContext: Context,
    private val hostClassLoader: ClassLoader,
    private val loadPackageParam: XC_LoadPackage.LoadPackageParam,
    private val dexKitBridge: DexKitBridge,
    private val dexFinder: DexFinder,
    private val eventBus: EventBus,
    private val configStore: ConfigStore,
    private val dexBridgeHolder: DexBridgeHolder,
    private val uiRegistry: UIRegistry
) {
    fun hostContext(): Context = hostContext

    fun moduleContext(): Context = moduleContext

    fun hostClassLoader(): ClassLoader = hostClassLoader

    fun loadPackageParam(): XC_LoadPackage.LoadPackageParam = loadPackageParam

    fun dexKitBridge(): DexKitBridge = dexKitBridge

    fun dexFinder(): DexFinder = dexFinder

    fun eventBus(): EventBus = eventBus

    fun configStore(): ConfigStore = configStore

    fun dexBridgeHolder(): DexBridgeHolder = dexBridgeHolder

    fun uiRegistry(): UIRegistry = uiRegistry
}
