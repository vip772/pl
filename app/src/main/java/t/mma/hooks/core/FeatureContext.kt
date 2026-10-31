package t.mma.hooks.core

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage
import t.mma.dexkit.DexBridgeHolder
import t.mma.dexkit.DexFinder
import t.mma.dexkit.DexKitBridgeManager
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
    dexKitBridge: DexKitBridge?,
    private val dexFinder: DexFinder,
    private val eventBus: EventBus,
    private val configStore: ConfigStore,
    private val dexBridgeHolder: DexBridgeHolder,
    private val uiRegistry: UIRegistry,
    private val dexKitManager: DexKitBridgeManager
) {
    fun hostContext(): Context = hostContext

    fun moduleContext(): Context = moduleContext

    fun hostClassLoader(): ClassLoader = hostClassLoader

    fun loadPackageParam(): XC_LoadPackage.LoadPackageParam = loadPackageParam

    fun dexKitBridge(): DexKitBridge = dexKitManager.rawCompatibilityBridge("legacy-feature-context")

    fun dexFinder(): DexFinder = dexFinder

    fun eventBus(): EventBus = eventBus

    fun configStore(): ConfigStore = configStore

    fun dexBridgeHolder(): DexBridgeHolder = dexBridgeHolder
    /** 生命周期治理入口；内部定位使用受保护管理器；原始接口按进程保活兼容。 */
    fun dexKitManager(): DexKitBridgeManager = dexKitManager
    fun uiRegistry(): UIRegistry = uiRegistry
}
