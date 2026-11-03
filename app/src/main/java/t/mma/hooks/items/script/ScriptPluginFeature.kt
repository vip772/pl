package t.mma.hooks.items.script

import t.mma.event.Events
import t.mma.dexkit.SettingsDexFinder
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.items.quickread.QuickMarkReadRuntime
import t.mma.hooks.items.shortvideo.FinderMediaDownloadSupport

class ScriptPluginFeature : BaseFeature() {
    private var generation: ScriptHookGeneration? = null

    override fun featureId(): String = ID

    override fun name(): String = "脚本插件"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ScriptPluginSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val created = ScriptHookGeneration(context) { message, error -> logError(message, error) }
        generation = created
        // 兜底释放：即使 onFeatureDestroy 未能执行，本代也一定会被撤销。
        trackResource {
            created.close()
            if (generation === created) generation = null
        }
        created.start()
        subscribe(Events.DexReady::class.java) {
            created.onDexReady()
            ScriptPluginRuntime.loadEnabledPluginsWhenReady(context.hostContext())
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        // 本代安装票据、本代订阅与共享租约都由 ScriptHookGeneration 统一撤销，
        // 撤销顺序写在它的 close() 里；这里只断开引用，避免迟到的 DexReady 复用旧代。
        val previous = generation
        generation = null
        previous?.close()
    }

    companion object {
        const val ID = "script_plugin"
    }
}

/**
 * 单个启用区间的脚本插件 Hook 安装代。
 *
 * 持有：本代四张 [DexInstallScheduler.Installation] 票据（菜单 / 消息 / 好友申请 /
 * 成员变动）、好友申请监听的共享租约、以及本代脚本运行时。
 *
 * close() 幂等，顺序不可交换：
 * 1. 先撤销票据——停掉未执行与已排期的重试，否则重试会把 Hook 装进已销毁的运行时；
 * 2. 再撤销两个只属于本代的消息 / 成员变动订阅，避免回调继续进入正在关闭的运行时；
 * 3. 然后关闭 [ScriptPluginRuntime] 本代（插件、观察器、reload 任务、五组执行器）；
 * 4. 最后归还好友申请监听租约——只有最后一个持有者离开时宿主侧监听才真正撤掉，
 *    自动回复仍在运行时不会被本代退出掐断。
 */
internal class ScriptHookGeneration(
    private val context: FeatureContext,
    private val report: (String, Throwable?) -> Unit
) {
    private val lock = Any()
    private var closed = false
    private var started = false
    private var tickets: List<DexInstallScheduler.Installation> = emptyList()
    private var newFriendLease = false

    fun start() {
        val created = try {
            listOf(
                DexInstallScheduler.installation("$ID:menus", "$NAME-菜单", DexInstallScheduler.Stage.WARMUP),
                DexInstallScheduler.installation("$ID:message", "$NAME-消息监听", DexInstallScheduler.Stage.WARMUP),
                DexInstallScheduler.installation("$ID:new_friend", "$NAME-好友申请", DexInstallScheduler.Stage.WARMUP),
                DexInstallScheduler.installation("$ID:member_change", "$NAME-成员变动", DexInstallScheduler.Stage.WARMUP)
            )
        } catch (error: Throwable) {
            report("创建脚本插件安装票据失败", error)
            return
        }
        val accepted = synchronized(lock) {
            if (closed || started) {
                false
            } else {
                started = true
                tickets = created
                true
            }
        }
        if (!accepted) {
            // 启动过程中已被关闭：刚创建的票据就地撤销，不留成孤儿重试。
            created.forEach(::closeTicket)
            return
        }
        // 运行时自身不抛是常态；万一抛也不能让本代停在半启动状态：
        // 这里上报后继续，closed 已为真的取消路径仍会在 close() 里兜底清理。
        try {
            ScriptPluginRuntime.install(context)
        } catch (error: Throwable) {
            report("启动脚本插件运行时失败", error)
        }
        // 快捷已读原生入口是进程级幂等激活，所有权属于快捷已读功能自身；
        // 脚本只借用它的 markRead 能力，不建票据、不撤销。
        try {
            QuickMarkReadRuntime.install(context)
        } catch (error: Throwable) {
            report("快捷已读原生入口激活失败", error)
        }
        scheduleSharedHooks()
        scheduleInstall()
    }

    /**
     * 两个进程级共享安装点：视频号详情解析与聊天发送按钮由多个功能共同消费，
     * 安装成功后由 [DexInstallScheduler] 的共享状态长期持有，本代退出不去撤销它们
     * （撤销权不属于脚本插件）。这里只保证本代关闭后不再发起新的安装尝试。
     */
    private fun scheduleSharedHooks() {
        if (isClosed()) return
        try {
            DexInstallScheduler.schedule(
                ScriptSharedDexKeys.FINDER_FEED_DETAIL,
                "视频号详情解析",
                stage = DexInstallScheduler.Stage.WARMUP
            ) {
                if (isClosed()) return@schedule false
                ScriptSharedDexKeys.installFinderFeedDetail(context)
            }
            DexInstallScheduler.schedule(
                ScriptSharedDexKeys.SEND_BUTTON,
                "聊天发送按钮",
                stage = DexInstallScheduler.Stage.WARMUP
            ) {
                if (isClosed()) return@schedule false
                ScriptSharedDexKeys.installSendButton(context)
            }
        } catch (error: Throwable) {
            report("调度共享 Hook 安装失败", error)
        }
    }

    fun onDexReady() {
        scheduleInstall()
    }

    private fun scheduleInstall() {
        val snapshot = synchronized(lock) {
            if (closed) null else tickets.toList()
        } ?: return
        if (snapshot.isEmpty()) return
        try {
            snapshot.getOrNull(0)?.schedule { installMenus() }
            snapshot.getOrNull(1)?.schedule { installMessage() }
            snapshot.getOrNull(2)?.schedule { installNewFriend() }
            snapshot.getOrNull(3)?.schedule { installMemberChange() }
        } catch (error: Throwable) {
            report("调度脚本插件 Hook 安装失败", error)
        }
    }

    /** 在 DexInstallScheduler 线程执行；返回 false 表示交给票据做有限重试。 */
    private fun installMenus(): Boolean {
        if (isClosed()) return false
        return try {
            val finder = SettingsDexFinder.managed(
                context.dexKitManager(),
                context.hostClassLoader(),
                context.hostContext()
            )
            finder.resolveAll(true)
            ScriptMenuDispatcher.install(context, finder, report)
        } catch (error: Throwable) {
            report("脚本菜单 Hook 安装失败", error)
            false
        }
    }

    private fun installMessage(): Boolean {
        if (isClosed()) return false
        return try {
            ScriptMessageHook.install(context)
        } catch (error: Throwable) {
            report("脚本消息监听安装失败", error)
            false
        }
    }

    private fun installNewFriend(): Boolean {
        if (isClosed()) return false
        val acquired = try {
            ScriptNewFriendHook.acquire(context, ScriptNewFriendHook.OWNER_SCRIPT)
        } catch (error: Throwable) {
            report("脚本好友申请监听取得租约失败", error)
            false
        }
        if (!acquired) return false
        val keep = synchronized(lock) {
            if (closed) {
                false
            } else {
                newFriendLease = true
                true
            }
        }
        if (!keep) {
            // 取得租约期间本代被关闭：立刻归还，不能把自己的名字留在租约里。
            ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_SCRIPT)
            return false
        }
        return true
    }

    private fun installMemberChange(): Boolean {
        if (isClosed()) return false
        return try {
            ScriptMemberChangeHook.install(context)
        } catch (error: Throwable) {
            report("脚本成员变动监听安装失败", error)
            false
        }
    }

    private fun isClosed(): Boolean = synchronized(lock) { closed }

    fun close() {
        val taken = synchronized(lock) {
            if (closed) return
            closed = true
            val currentTickets = tickets
            tickets = emptyList()
            val lease = newFriendLease
            newFriendLease = false
            // 从未成功 start() 的代没有装过运行时、票据、订阅与租约，
            // 它的 close() 只能是空操作，否则会去关闭别人的运行时、撤销别人的订阅。
            val owned = started
            Triple(currentTickets, lease, owned)
        }
        if (!taken.third) return
        taken.first.forEach(::closeTicket)
        uninstallOwnedHooks()
        try {
            ScriptPluginRuntime.close()
        } catch (error: Throwable) {
            report("关闭脚本插件运行时失败", error)
        }
        if (taken.second) {
            try {
                ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_SCRIPT)
            } catch (error: Throwable) {
                report("脚本好友申请监听归还租约失败", error)
            }
        }
    }

    private fun uninstallOwnedHooks() {
        try {
            ScriptMessageHook.uninstall()
        } catch (error: Throwable) {
            report("撤销脚本消息监听失败", error)
        }
        try {
            ScriptMemberChangeHook.uninstall()
        } catch (error: Throwable) {
            report("撤销脚本成员变动监听失败", error)
        }
    }

    private fun closeTicket(installation: DexInstallScheduler.Installation) {
        try {
            installation.close()
        } catch (error: Throwable) {
            report("取消脚本插件安装重试失败", error)
        }
    }

    companion object {
        private const val ID = ScriptPluginFeature.ID
        private const val NAME = "脚本插件"
    }
}

/**
 * 保留给别的功能直接复用的共享安装点：视频号详情解析与聊天发送按钮由多个功能
 * 共同消费，安装与撤销都不归脚本插件所有，这里仅导出键名以便核对。
 */
internal object ScriptSharedDexKeys {
    const val FINDER_FEED_DETAIL = "shared:finder_feed_detail"
    const val SEND_BUTTON = "shared:send_button"

    fun installFinderFeedDetail(context: FeatureContext): Boolean =
        FinderMediaDownloadSupport.install(context)

    fun installSendButton(context: FeatureContext): Boolean =
        ScriptSendButtonHook.install(context)
}
