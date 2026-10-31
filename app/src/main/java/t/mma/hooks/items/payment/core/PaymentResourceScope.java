package t.mma.hooks.items.payment.core;

import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import t.mma.hooks.core.HookRegistry;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Private payment owner; never cancels a shared task key or interrupts a host call. */
public final class PaymentResourceScope implements AutoCloseable {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> releases = new ArrayList<>();
    private final Map<Member, XC_MethodHook.Unhook> hooks = new HashMap<>();
    private final Map<String, Runnable> pending = new HashMap<>();
    private volatile boolean closed;

    public boolean isClosed() { return closed; }

    public synchronized XC_MethodHook.Unhook hook(Member method, XC_MethodHook callback) {
        if (closed) throw new IllegalStateException("Payment owner closed");
        XC_MethodHook.Unhook existing = hooks.get(method);
        if (existing != null) return existing;
        XC_MethodHook.Unhook handle = HookRegistry.get().hook(method, callback);
        hooks.put(method, handle);
        register(() -> HookRegistry.get().unhook(handle));
        return handle;
    }

    public void register(Runnable release) {
        synchronized (this) {
            if (!closed) { releases.add(release); return; }
        }
        releaseSafely(release);
    }

    public synchronized void post(String key, long delayMs, Runnable action) {
        if (closed) return;
        cancel(key);
        Runnable wrapped = new Runnable() {
            @Override public void run() {
                synchronized (PaymentResourceScope.this) {
                    if (closed || pending.get(key) != this) return;
                    pending.remove(key);
                }
                action.run();
            }
        };
        pending.put(key, wrapped);
        handler.postDelayed(wrapped, Math.max(0L, delayMs));
    }

    public synchronized void cancel(String key) {
        Runnable previous = pending.remove(key);
        if (previous != null) handler.removeCallbacks(previous);
    }

    @Override public void close() {
        List<Runnable> snapshot;
        synchronized (this) {
            if (closed) return;
            closed = true;
            handler.removeCallbacksAndMessages(null);
            pending.clear();
            hooks.clear();
            snapshot = new ArrayList<>(releases);
            releases.clear();
        }
        for (int i = snapshot.size() - 1; i >= 0; i--) releaseSafely(snapshot.get(i));
    }

    private static void releaseSafely(Runnable release) {
        try { release.run(); }
        catch (Throwable error) { XposedBridge.log(error); }
    }
}