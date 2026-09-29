package t.mma.event

fun interface EventHandler<E> {
    fun handleEvent(event: E)
}
