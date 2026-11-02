package t.mma.event

fun interface EventHandler<E> {
    fun handleEvent(event: E)
}

object Events {
    class DexReady
}

class EventBus {
    private class Entry(val type: Class<*>, val invoke: () -> Unit)
    private val entries = mutableListOf<Entry>()

    class Subscription internal constructor(internal var entry: Any?) {
        internal var active = true

        internal fun attach(value: Any?) {
            entry = value
        }
    }

    fun <E> subscribe(type: Class<E>, handler: EventHandler<E>): Subscription =
        subscribe(type, handler, 0)

    fun <E> subscribe(type: Class<E>, handler: EventHandler<E>, priority: Int): Subscription {
        val subscription = Subscription(null)
        val entry = Entry(type) {
            if (subscription.active) handler.handleEvent(instantiate(type))
        }
        entries.add(entry)
        return subscription.also { it.attach(entry) }
    }

    fun unsubscribe(subscription: Subscription) {
        subscription.active = false
        val entry = subscription.entry ?: return
        entries.removeAll { it === entry }
    }

    fun size(): Int = entries.size

    fun clear() {
        entries.clear()
    }

    fun post(event: Any) {
        entries.toList().filter { it.type.isInstance(event) }.forEach { it.invoke() }
    }

    private fun <E> instantiate(type: Class<E>): E = type.getDeclaredConstructor().newInstance()
}
