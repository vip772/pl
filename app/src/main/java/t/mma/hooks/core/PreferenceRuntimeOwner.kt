package t.mma.hooks.core

import android.content.SharedPreferences

/** One feature-private runtime per enabled interval. Listener stays to permit reopening. */
internal class PreferenceRuntimeOwner(
    private val prefs: SharedPreferences,
    private val keys: Set<String>,
    private val enabled: () -> Boolean,
    private val create: (FeatureResourceScope) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val restartOnChange: Boolean = false
) : AutoCloseable {
    private var closed = false
    private var started = false
    private var resources: FeatureResourceScope? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in keys) refresh(restartOnChange)
    }
    @Synchronized fun start() {
        if (closed || started) return
        started = true
        try { prefs.registerOnSharedPreferenceChangeListener(listener); refresh() }
        catch (error: Throwable) { close(); onError(error) }
    }
    @Synchronized fun refresh(restart: Boolean = false) {
        if (closed || !started) return
        try {
            if (!enabled()) { stopRuntime(); return }
            if (restart) stopRuntime()
            if (resources?.isClosed() == true) resources = null
            if (resources != null) return
            val next = FeatureResourceScope(onError)
            resources = next
            try { create(next) } catch (error: Throwable) { stopRuntime(); throw error }
        } catch (error: Throwable) { onError(error) }
    }
    private fun stopRuntime() {
        val old = resources
        resources = null
        old?.close()
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        try { if (started) prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        catch (error: Throwable) { onError(error) }
        finally { stopRuntime() }
    }
}