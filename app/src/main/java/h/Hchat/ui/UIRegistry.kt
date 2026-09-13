package h.Hchat.ui

import java.util.Collections

object UIRegistry {
    private val providers = ArrayList<FeatureSettingsProvider>()
    private val enabledChecks = HashMap<String, () -> Boolean>()

    @JvmStatic
    fun get(): UIRegistry = this

    fun registerProvider(provider: FeatureSettingsProvider?, enabledCheck: (() -> Boolean)? = null) {
        if (provider == null) return
        unregisterProvider(provider.featureId())
        providers.add(provider)
        if (enabledCheck != null) {
            enabledChecks[provider.featureId()] = enabledCheck
        }
    }

    fun unregisterProvider(featureId: String?) {
        if (featureId == null) return
        providers.removeIf { provider -> featureId == provider.featureId() }
        enabledChecks.remove(featureId)
    }

    fun getAllProviders(): List<FeatureSettingsProvider> {
        return Collections.unmodifiableList(ArrayList(providers))
    }

    fun getEnabledProviders(): List<FeatureSettingsProvider> {
        return providers.filter { provider ->
            runCatching { enabledChecks[provider.featureId()]?.invoke() ?: true }
                .getOrDefault(false)
        }
    }

    fun clear() {
        providers.clear()
        enabledChecks.clear()
    }
}
