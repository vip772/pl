package h.Hchat.hooks.core

import h.Hchat.event.Events
import h.Hchat.hooks.items.settings.SettingsFeature
import h.Hchat.preferences.TermsGate
import h.Hchat.utils.HLog
import java.util.Collections

/**
 * 功能模块调度器。
 */
class FeatureManager {
    private val features = ArrayList<Feature>()
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
        HLog.i("[Hchat:FeatureManager] 开始加载功能，总数=${features.size}")
        val termsAccepted = TermsGate.isAccepted(context.configStore())
        val initializedFeatures = ArrayList<Feature>()
        for (feature in features) {
            val displayName = safeName(feature)
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
            }
        }

        for (feature in initializedFeatures) {
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
        for (i in installedFeatures.size - 1 downTo 0) {
            val feature = installedFeatures[i]
            try {
                HLog.i("[Hchat:FeatureManager] 卸载功能: ${safeName(feature)} (${feature.featureId()})")
                feature.onDestroy(context)
                HLog.i("[Hchat:FeatureManager] 功能卸载完成: ${safeName(feature)}")
            } catch (e: Throwable) {
                HLog.e("$TAG onDestroy 异常: ${safeName(feature)}, error=$e", e)
            }
        }
        installedFeatures.clear()
        HookRegistry.get().unhookAll()
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
