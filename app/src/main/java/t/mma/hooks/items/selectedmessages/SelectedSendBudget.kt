package t.mma.hooks.items.selectedmessages

/** Budgets retained work across preparation, Handler admission, queued and active batches. */
internal class SelectedSendBudget {
    private var closed = false
    private var batches = 0
    private var items = 0
    private var targets = 0
    private var deliveries = 0L

    @Synchronized
    fun acquire(itemCount: Int, targetCount: Int): Lease? {
        if (closed || itemCount <= 0 || targetCount <= 0) return null
        val work = itemCount.toLong() * targetCount.toLong()
        if (batches >= MAX_BATCHES || itemCount > MAX_ITEMS - items ||
            targetCount > MAX_TARGETS - targets || work > MAX_DELIVERIES - deliveries) return null
        batches++
        items += itemCount
        targets += targetCount
        deliveries += work
        return Lease(itemCount, targetCount, work)
    }

    @Synchronized
    fun close() { closed = true }

    internal inner class Lease(
        private val itemCount: Int, private val targetCount: Int, private val work: Long
    ) {
        private val released = java.util.concurrent.atomic.AtomicBoolean()
        fun release(): Boolean {
            if (!released.compareAndSet(false, true)) return false
            synchronized(this@SelectedSendBudget) {
                batches--
                items -= itemCount
                targets -= targetCount
                deliveries -= work
            }
            return true
        }
    }

    companion object {
        private const val MAX_BATCHES = 16
        private const val MAX_ITEMS = 256
        private const val MAX_TARGETS = 4096
        private const val MAX_DELIVERIES = 65_536L
    }
}
