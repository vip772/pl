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

    class Subscription internal constructor(internal val entry: Any?)

    fun <E> subscribe(type: Class<E>, handler: EventHandler<E>): Subscription =
        subscribe(type, handler, 0)

    fun <E> subscribe(type: Class<E>, handler: EventHandler<E>, priority: Int): Subscription {
        val entry = Entry(type) { handler.handleEvent(instantiate(type)) }
        entries.add(entry)
        return Subscription(entry)
    }

    fun unsubscribe(subscription: Subscription) {
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
