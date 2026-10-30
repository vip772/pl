package t.mma.hooks.core
import t.mma.utils.HLog

import t.mma.event.EventBus
import t.mma.event.EventHandler
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.FeatureToggleRegistry
import t.mma.utils.KavaReflector

/**
 * 功能模块基类。
 */
abstract class BaseFeature : Feature {
    private var resources = newResourceScope()
    private var initialized = false
    private var installed = false
    private fun newResourceScope() = FeatureResourceScope { logError("资源释放异常", it) }
    private var context: FeatureContext? = null

    @Throws(Throwable::class)
    @Synchronized
    final override fun onInit(context: FeatureContext) {
        if (initialized) return
        resources = newResourceScope()
        this.context = context
        initialized = true
        try { onFeatureInit(context) } catch (error: Throwable) {
            onDestroy(context)
            throw error
        }
    }

    @Throws(Throwable::class)
    @Synchronized
    final override fun install(context: FeatureContext) {
        if (installed) return
        if (!initialized) onInit(context)
        installed = true
        try { onFeatureInstall(context) } catch (error: Throwable) {
            onDestroy(context)
            throw error
        }
    }

    @Synchronized
    final override fun onDestroy(context: FeatureContext) {
        if (!initialized) return
        initialized = false
        installed = false
        try { onFeatureDestroy(context) } catch (error: Throwable) {
            logError("onDestroy异常", error)
        } finally {
            resources.close()
            this.context = null
        }
    }
    /** Register only resources owned by this feature, never shared API executors. */
    protected fun trackResource(release: () -> Unit) { resources.register(release) }
    @Throws(Throwable::class)
    protected open fun onFeatureInit(context: FeatureContext) {
    }

    @Throws(Throwable::class)
    protected abstract fun onFeatureInstall(context: FeatureContext)

    protected open fun onFeatureDestroy(context: FeatureContext) {
    }

    protected fun context(): FeatureContext? = context

    protected fun registerSettingsProvider(
        provider: FeatureSettingsProvider?,
        enabledCheck: (() -> Boolean)? = null
    ) {
        val currentContext = context ?: return
        if (provider == null) return
        val registryCheck = enabledCheck ?: {
            FeatureToggleRegistry.isEnabled(currentContext.hostContext(), provider.featureId())
        }
        currentContext.uiRegistry().registerProvider(provider, registryCheck)
        trackResource { currentContext.uiRegistry().unregisterProvider(provider.featureId()) }
    }
    protected fun <E> subscribe(
        eventType: Class<E>,
        handler: EventHandler<E>
    ): EventBus.Subscription {
        return subscribe(eventType, handler, 0)
    }

    protected fun <E> subscribe(
        eventType: Class<E>,
        handler: EventHandler<E>,
        priority: Int
    ): EventBus.Subscription {
        val currentContext = context ?: return EventBus.Subscription.empty()
        val subscription = currentContext.eventBus().subscribe(eventType, handler, priority)
        trackResource { currentContext.eventBus().unsubscribe(subscription) }
        return subscription
    }

    protected fun <T : Any> trackSubscription(subscription: T?): T? {
        if (subscription != null) {
            trackResource {
                KavaReflector.invoke(
                    KavaReflector.findMethod(subscription.javaClass, "unsubscribe"), subscription
                )
            }
        }
        return subscription
    }

    protected fun globalBoolean(key: String, defValue: Boolean): Boolean {
        return context?.configStore()?.getGlobalBoolean(key, defValue) ?: defValue
    }

    protected fun featureBoolean(key: String, defValue: Boolean): Boolean {
        return context?.configStore()?.getBoolean(featureId(), key, defValue) ?: defValue
    }

    protected fun featureInt(key: String, defValue: Int): Int {
        return context?.configStore()?.getInt(featureId(), key, defValue) ?: defValue
    }

    protected fun featureString(key: String, defValue: String): String {
        return context?.configStore()?.getString(featureId(), key, defValue) ?: defValue
    }

    protected fun logEffective(message: String = "功能生效") {
        HLog.i("[Hchat:${featureId()}] $message")
    }

    protected fun logIneffective(reason: String, throwable: Throwable? = null) {
        if (throwable == null) HLog.w("[Hchat:${featureId()}] 功能未生效: $reason")
        else HLog.e("[Hchat:${featureId()}] 功能未生效: $reason", throwable)
    }

    protected fun logError(message: String, throwable: Throwable?) {
        HLog.e(
            "[Hchat:${featureId()}] $message" +
                (throwable?.let { ": ${it.message}" } ?: ""),
            throwable
        )
    }

    /** 兼容旧分支功能实现的诊断日志入口，统一进入 LSPosed 日志。 */
    protected fun logInfo(message: String) {
        HLog.i("[Hchat:${featureId()}] $message")
    }
}
