package t.mma.hooks.items.emojiat

/** Main-thread queue. A closing search window must not consume a send request. */
internal class EmojiAtChoiceQueue<T : Any> {
    private val waiting = ArrayDeque<T>()
    private var current: T? = null
    fun offer(value: T) { waiting.addLast(value) }
    fun acquire(): T? {
        if (current != null) return null
        return waiting.removeFirstOrNull()?.also { current = it }
    }
    fun complete(value: T): Boolean {
        if (current !== value) return false
        current = null
        return true
    }
    fun retry(value: T): Boolean {
        if (!complete(value)) return false
        waiting.addFirst(value)
        return true
    }
    fun removeIf(predicate: (T) -> Boolean) {
        waiting.removeAll(predicate)
        if (current?.let(predicate) == true) current = null
    }
    fun clear() { waiting.clear(); current = null }
}
