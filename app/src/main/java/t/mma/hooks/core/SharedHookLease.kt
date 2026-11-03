package t.mma.hooks.core

/**
 * 共享 Hook 的租约式所有权。
 *
 * 固定约定：
 * 1. 安装只发生在 0 → 1 个持有者时，撤销只发生在 1 → 0 个持有者时；中间任何
 *    增删持有者都不触碰宿主侧 Hook，因此两个功能同时消费同一监听时，任何一方
 *    退出都不会把另一方的回调掐掉。
 * 2. 安装失败不留下幽灵持有者：失败的那次 acquire 立即把自己的名字摘掉，其他
 *    持有者仍可在后续 acquire 时重试安装。
 * 3. 撤销异常只上报，不阻断其余 key 与其余持有者的释放。
 * 4. 关闭是单向幂等的：关闭后 acquire 一律返回 false，未释放的 key 在此统一撤销。
 * 5. 回调（install/uninstall）在每 key 的独占监视器内执行，不得回租约自身；
 *    调用方只把纯 subscribe/unsubscribe 放进来。
 */
internal class SharedHookLease(
    private val onError: (String, Throwable?) -> Unit
) : AutoCloseable {

    private class Entry(
        val key: String,
        val install: () -> Boolean,
        val uninstall: () -> Unit
    ) {
        val owners = LinkedHashSet<String>()
        var active = false
    }

    private val lock = Any()
    private val entries = HashMap<String, Entry>()
    @Volatile
    private var closed = false

    fun isClosed(): Boolean = closed

    /** 声明一个可共享的安装点。重复登记同一 key 以最后一次为准，不叠加安装。 */
    fun register(key: String, install: () -> Boolean, uninstall: () -> Unit) {
        synchronized(lock) {
            if (closed) return
            entries[key] = Entry(key, install, uninstall)
        }
    }

    fun ownerCount(key: String): Int {
        val entry = synchronized(lock) { entries[key] } ?: return 0
        synchronized(entry) { return entry.owners.size }
    }

    fun owners(key: String): Set<String> {
        val entry = synchronized(lock) { entries[key] } ?: return emptySet()
        synchronized(entry) { return entry.owners.toSet() }
    }

    fun isActive(key: String): Boolean {
        val entry = synchronized(lock) { entries[key] } ?: return false
        synchronized(entry) { return entry.active }
    }

    /** 取得租约。同一持有者重复取得保持幂等，不会再装一次。 */
    fun acquire(key: String, owner: String): Boolean {
        val entry = synchronized(lock) {
            if (closed) return false
            entries[key]
        } ?: return false
        synchronized(entry) {
            if (closed) return false
            if (entry.owners.contains(owner)) return entry.active
            if (entry.active) {
                entry.owners.add(owner)
                return true
            }
            entry.owners.add(owner)
            val installed = try {
                entry.install()
            } catch (error: Throwable) {
                report("${entry.key} 共享 Hook 安装异常", error)
                false
            }
            if (!installed) {
                entry.owners.remove(owner)
                return false
            }
            if (closed) {
                // 安装期间租约被关闭：不能把这个 Hook 留给无人持有的进程。
                entry.owners.clear()
                entry.active = false
                runUninstall(entry)
                return false
            }
            entry.active = true
            return true
        }
    }

    /** 归还租约。最后一个持有者离开时才撤销宿主侧安装。 */
    fun release(key: String, owner: String) {
        val entry = synchronized(lock) { entries[key] } ?: return
        val detach = synchronized(entry) {
            if (!entry.owners.remove(owner)) return
            if (entry.owners.isNotEmpty() || !entry.active) return
            entry.active = false
            true
        }
        if (detach) runUninstall(entry)
    }

    private fun runUninstall(entry: Entry) {
        try {
            entry.uninstall()
        } catch (error: Throwable) {
            report("${entry.key} 共享 Hook 撤销异常", error)
        }
    }

    override fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            val detaching = ArrayList<Entry>(entries.size)
            entries.values.forEach { entry ->
                synchronized(entry) {
                    val active = entry.active
                    entry.owners.clear()
                    entry.active = false
                    if (active) detaching.add(entry)
                }
            }
            entries.clear()
            detaching
        }
        pending.forEach(::runUninstall)
    }

    private fun report(message: String, error: Throwable?) {
        try {
            onError(message, error)
        } catch (_: Throwable) {
            // 上报本身失败不能再阻断释放。
        }
    }
}
