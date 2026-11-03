package t.mma.hooks.core
import t.mma.utils.HLog

import t.mma.event.Events
import t.mma.hooks.items.settings.SettingsFeature
import t.mma.preferences.TermsGate
import java.util.Collections

/**
 * 功能模块调度器。
 */
class FeatureManager {
    private val features = ArrayList<Feature>()
    private val initializedFeatures = ArrayList<Feature>()
    private val installedFeatures = ArrayList<Feature>()

    fun register(feature: Feature?): FeatureManager {
        if (feature != null) {
            features.add(feature)
        }
        return this
    }

    fun features(): List<Feature> = Collections.unmodifiableList(features)

    fun installedFeatures(): List<Feature> = Collections.unmodifiableList(installedFeatures)

    fun installAll(context: FeatureContext) {
        t.mma.hooks.api.media.WeChatImageApi.openMarsRuntime()
        HLog.i("[Hchat:FeatureManager] 开始加载功能，总数=${features.size}")
        val termsAccepted = TermsGate.isAccepted(context.configStore())
        if (initializedFeatures.isNotEmpty()) return
        for (feature in features) {
            val displayName = safeName(feature)
            HLog.d("[Hchat:FeatureManager] 功能初始化开始: $displayName (${feature.featureId()})")
            if (!termsAccepted && feature.featureId() != SettingsFeature.ID) {
                HLog.i("[Hchat:FeatureManager] 跳过功能（未同意协议）: $displayName")
                continue
            }
            try {
                HLog.i("[Hchat:FeatureManager] 初始化功能: $displayName (${feature.featureId()})")
                feature.onInit(context)
                initializedFeatures.add(feature)
                HLog.i("[Hchat:FeatureManager] 功能初始化完成: $displayName")
            } catch (e: Throwable) {
                HLog.e("$TAG onInit 失败: $displayName, error=$e", e)
                try { feature.onDestroy(context) } catch (cleanup: Throwable) {
                    HLog.e("$TAG 初始化失败清理异常: $displayName", cleanup)
                }
            }
        }

        for (feature in initializedFeatures.toList()) {
            val displayName = safeName(feature)
            try {
                if (!feature.isEnabled(context)) {
                    HLog.i("[Hchat:FeatureManager] 功能未启用，跳过安装: $displayName")
                    continue
                }
            } catch (e: Throwable) {
                HLog.e("$TAG isEnabled 检查异常，默认启用: $displayName", e)
            }

            try {
                feature.install(context)
                installedFeatures.add(feature)
                HLog.i("[Hchat:FeatureManager] 功能安装流程完成: $displayName (${feature.featureId()})")
                try {
                    context.eventBus().post(Events.FeatureInstalled(displayName))
                } catch (_: Throwable) {
                }
            } catch (e: Throwable) {
                HLog.e("$TAG 功能安装失败: $displayName, error=$e", e)
                try { feature.onDestroy(context) } catch (cleanup: Throwable) {
                    HLog.e("$TAG 安装失败清理异常: $displayName", cleanup)
                }
                initializedFeatures.remove(feature)
            }
        }
        DexInstallScheduler.markDexBridgeReady()
    }

    fun notifyConfigChanged(context: FeatureContext, featureId: String?, key: String?) {
        for (feature in installedFeatures) {
            if (featureId != null && featureId != feature.featureId()) continue
            try {
                HLog.i("[Hchat:FeatureManager] 配置变化: ${safeName(feature)} key=${key ?: "<all>"}")
                feature.onConfigChanged(context, key)
            } catch (e: Throwable) {
                HLog.e("$TAG onConfigChanged 异常: ${safeName(feature)}, error=$e", e)
            }
        }
    }

    fun destroyAll(context: FeatureContext) {
        fun release(label: String, block: () -> Unit) {
            try { block() } catch (error: Throwable) {
                try { HLog.e("$TAG 释放失败: $label", error) } catch (_: Throwable) { }
            }
        }
        val pending = initializedFeatures.asReversed().toList()
        installedFeatures.clear()
        initializedFeatures.clear()
        pending.forEach { feature ->
            // Diagnostic failures must never prevent invoking the feature's cleanup.
            release(safeName(feature)) { feature.onDestroy(context) }
        }
        release("悬浮快捷入口") { t.mma.hooks.items.floatingshortcut.FloatingShortcutRuntime.destroy() }
        release("早期底栏") { t.mma.hooks.items.custombottombar.CustomBottomBarFeature.destroyEarlyRuntimes() }
        release("底栏图标选择器") { t.mma.hooks.items.custombottombar.CustomBottomBarIconPicker.destroy() }
        release("位置进程Hook") { t.mma.hooks.items.fakelocation.FakeLocationFeature.destroyProcessHooks() }
        release("小程序视频Hook") { t.mma.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsFeature.destroyProcessHooks() }
        release("小程序开屏Hook") { t.mma.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsFeature.destroyProcessHooks() }
        release("Mars运行时") { t.mma.hooks.api.media.WeChatImageApi.closeMarsRuntime() }
        release("剩余Hook") { HookRegistry.get().unhookAll() }
    }
    private fun safeName(feature: Feature): String {
        return try {
            val name = feature.name()
            if (name.isEmpty()) feature.javaClass.name else name
        } catch (_: Throwable) {
            feature.javaClass.name
        }
    }

    companion object {
        private const val TAG = "[Hchat:FeatureManager]"
    }
}
