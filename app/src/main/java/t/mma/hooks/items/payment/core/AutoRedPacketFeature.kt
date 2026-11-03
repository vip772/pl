package t.mma.hooks.items.payment.core

import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner

/**
 * 自动抢红包功能入口。
 *
 * 一个启用区间对应一个 [RedPacketRuntime]：独占本代 [RedPacketHooker] 与安装票据。
 * 关闭顺序固定为“先撤销安装票据与重试，再销毁本代 hooker”，保证在途安装不会把新 Hook
 * 装进已经销毁的代际里。任何一步抛出都只上报，不影响其余资源释放。
 */
class AutoRedPacketFeature : BaseFeature() {
    private var runtime: RedPacketRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = TITLE

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(RedPacketSettingsProvider())

        subscribe(Events.RedPacketGrabbed::class.java) {
            // 预留给跨功能通知、统计等逻辑。
        }
    }

    @Throws(Throwable::class)
    override fun onFeatureInstall(context: FeatureContext) {
        val settings = RedPacketSettings(context.hostClassLoader(), context.hostContext())
        val prefs = settings.getHostPreferences() ?: return
        val keys = setOf(
            RedPacketSettings.KEY_ENABLE,
            RedPacketSettings.KEY_FAKE_PACKET_ENABLE,
            RedPacketSettings.KEY_BLOCK_NEW_GROUP_ENABLE
        )
        val owner = PreferenceRuntimeOwner(
            prefs,
            keys,
            enabled = { keys.any { prefs.getBoolean(it, false) } },
            create = { resources ->
                val created = RedPacketRuntime(context) { message, error -> logError(message, error) }
                runtime = created
                resources.register {
                    // 清理异常必须隔离：引用一定断开，异常不能外泄到 FeatureResourceScope。
                    try {
                        created.close()
                    } finally {
                        if (runtime === created) runtime = null
                    }
                }
                created.start()
            },
            onError = { logError("红包资源启停失败", it) },
            restartOnChange = true
        )
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) {
            owner.refresh()
            // DexKit 就绪后重新调度：票据自身负责去重与有限重试，已关闭的代际直接忽略。
            runtime?.onDexReady()
        }
    }

    companion object {
        const val ID = "auto_redpacket"
        const val TITLE = "自动抢红包"
    }
}

/**
 * 单个启用区间的红包运行时。
 *
 * 持有本代 [RedPacketHooker] 与 [DexInstallScheduler.Installation]；`close()` 幂等，
 * 先撤销票据（取消未执行的重试）再销毁 hooker（逆序卸载子组件 Hook 与延迟任务）。
 * 安装体在调度线程执行，进入与退出都重新核对代际，避免关闭后继续安装。
 */
internal class RedPacketRuntime(
    private val context: FeatureContext,
    private val report: (String, Throwable?) -> Unit
) {
    private val lock = Any()
    private var closed = false
    private var hooker: RedPacketHooker? = null
    private var ticket: DexInstallScheduler.Installation? = null

    fun start() {
        val created = try {
            RedPacketHooker(context.hostContext(), context.hostClassLoader(), context.dexFinder())
        } catch (error: Throwable) {
            report("创建红包运行时失败", error)
            return
        }
        val createdTicket = try {
            DexInstallScheduler.installation(
                AutoRedPacketFeature.ID,
                AutoRedPacketFeature.TITLE,
                DexInstallScheduler.Stage.WARMUP
            )
        } catch (error: Throwable) {
            report("创建红包安装票据失败", error)
            destroyHooker(created)
            return
        }
        val accepted = synchronized(lock) {
            if (closed) {
                false
            } else {
                hooker = created
                ticket = createdTicket
                true
            }
        }
        if (!accepted) {
            // 启动过程中已被关闭：刚创建的这一代就地释放，不能留成孤儿 Hook。
            closeTicket(createdTicket)
            destroyHooker(created)
            return
        }
        scheduleInstall()
    }

    fun onDexReady() {
        scheduleInstall()
    }

    private fun scheduleInstall() {
        val current = synchronized(lock) { if (closed) null else ticket } ?: return
        try {
            current.schedule { install() }
        } catch (error: Throwable) {
            report("调度红包 Hook 安装失败", error)
        }
    }

    /** 在 DexInstallScheduler 线程执行；返回 false 表示交给票据做有限重试。 */
    private fun install(): Boolean {
        val current = synchronized(lock) { if (closed) null else hooker } ?: return false
        if (current.isDestroyed()) return false
        return try {
            current.hookAll()
            current.isDexReadyEnough()
        } catch (error: Throwable) {
            report("红包 Hook 安装失败", error)
            false
        }
    }

    fun close() {
        val snapshot = synchronized(lock) {
            if (closed) return
            closed = true
            val taken = hooker to ticket
            hooker = null
            ticket = null
            taken
        }
        // 顺序不可交换：先停重试，再销毁本代，否则重试会把 Hook 装进已销毁的实例。
        closeTicket(snapshot.second)
        snapshot.first?.let(::destroyHooker)
    }

    private fun closeTicket(installation: DexInstallScheduler.Installation?) {
        if (installation == null) return
        try {
            installation.close()
        } catch (error: Throwable) {
            report("取消红包安装重试失败", error)
        }
    }

    private fun destroyHooker(target: RedPacketHooker) {
        try {
            target.destroy()
        } catch (error: Throwable) {
            report("释放红包运行时失败", error)
        }
    }
}