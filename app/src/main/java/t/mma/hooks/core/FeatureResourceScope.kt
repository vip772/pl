package t.mma.hooks.core

/** Own only feature-local resources. Cleanup never runs under the registration lock. */
internal class FeatureResourceScope(private val onError: (Throwable) -> Unit) {
    private val lock = Any()
    private val releases = ArrayList<() -> Unit>()
    private var closed = false
    fun register(release: () -> Unit) {
        val releaseNow = synchronized(lock) {
            if (closed) true else { releases.add(release); false }
        }
        if (releaseNow) releaseSafely(release)
    }
    fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            releases.asReversed().toList().also { releases.clear() }
        }
        pending.forEach(::releaseSafely)
    }
    private fun releaseSafely(release: () -> Unit) {
        try { release() } catch (error: Throwable) { onError(error) }
    }
}
