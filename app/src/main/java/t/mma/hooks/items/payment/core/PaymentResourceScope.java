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

/**
 * Private payment owner; never cancels a shared task key or interrupts a host call.
 *
 * Ownership rules:
 * 1. close() is one-way and idempotent; a closed scope refuses hooks, registrations and postings.
 * 2. A posting is claimed under the same lock close() uses and the claim is re-validated right
 *    before the action runs, so a scope released in between drops the action instead of letting
 *    it touch resources that were already released.
 * 3. A rejected posting is rolled back out of the pending map and reported, never leaked.
 * 4. The pending map is bounded; over budget refuses new work instead of growing without limit
 *    while the host main thread is busy.
 * 5. Releases run outside the registration lock, in reverse registration order, isolated per item.
 */
public final class PaymentResourceScope implements AutoCloseable {
    /** Upper bound on concurrently scheduled host-main tasks owned by one payment scope. */
    private static final int MAX_PENDING = 128;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> releases = new ArrayList<>();
    private final Map<Member, XC_MethodHook.Unhook> hooks = new HashMap<>();
    private final Map<String, Posting> pending = new HashMap<>();
    private volatile boolean closed;

    public boolean isClosed() { return closed; }

    public synchronized int pendingCount() { return pending.size(); }

    public synchronized XC_MethodHook.Unhook hook(Member method, XC_MethodHook callback) {
        if (closed) throw new IllegalStateException("Payment owner closed");
        XC_MethodHook.Unhook existing = hooks.get(method);
        if (existing != null) return existing;
        XC_MethodHook.Unhook handle = HookRegistry.get().hook(method, callback);
        hooks.put(method, handle);
        register(() -> HookRegistry.get().unhook(handle));
        return handle;
    }

    /** Replace the pending task with the same key. False means the scope refused the work. */
    public boolean post(String key, long delayMs, Runnable action) {
        if (action == null) return false;
        synchronized (this) {
            if (closed) return false;
            cancel(key);
            if (pending.size() >= MAX_PENDING) {
                XposedBridge.log("PaymentScope 待执行任务已达上限 " + MAX_PENDING + "，拒绝投递: " + key);
                return false;
            }
            Posting posting = new Posting(key, action);
            pending.put(key, posting);
            boolean queued;
            try {
                queued = handler.postDelayed(posting, Math.max(0L, delayMs));
            } catch (Throwable error) {
                pending.remove(key);
                posting.release();
                XposedBridge.log(error);
                return false;
            }
            if (!queued) {
                // The host main queue refused the posting: never keep a task that can never run.
                pending.remove(key);
                posting.release();
                XposedBridge.log("PaymentScope 主线程投递被拒绝: " + key);
                return false;
            }
            return true;
        }
    }

    public synchronized void cancel(String key) {
        if (key == null) return;
        Posting previous = pending.remove(key);
        if (previous == null) return;
        try {
            handler.removeCallbacks(previous);
        } catch (Throwable error) {
            XposedBridge.log(error);
        }
        previous.release();
    }

    public void register(Runnable release) {
        if (release == null) return;
        synchronized (this) {
            if (!closed) { releases.add(release); return; }
        }
        releaseSafely(release);
    }

    @Override public void close() {
        List<Runnable> snapshot;
        List<Posting> dropped;
        synchronized (this) {
            if (closed) return;
            closed = true;
            dropped = new ArrayList<>(pending.values());
            pending.clear();
            try {
                handler.removeCallbacksAndMessages(null);
            } catch (Throwable error) {
                XposedBridge.log(error);
            }
            snapshot = new ArrayList<>(releases);
            releases.clear();
            hooks.clear();
        }
        // Dropped postings must not keep capturing activities, views or network requests.
        for (Posting posting : dropped) posting.release();
        for (int i = snapshot.size() - 1; i >= 0; i--) releaseSafely(snapshot.get(i));
    }

    private static void releaseSafely(Runnable release) {
        try { release.run(); }
        catch (Throwable error) { XposedBridge.log(error); }
    }

    /** One scheduled task. Draining the capture on release drops whatever the host still holds. */
    private final class Posting implements Runnable {
        private final String key;
        private Runnable action;

        Posting(String key, Runnable action) {
            this.key = key;
            this.action = action;
        }

        void release() {
            action = null;
        }

        @Override public void run() {
            Runnable claimed;
            synchronized (PaymentResourceScope.this) {
                if (closed || pending.get(key) != this) {
                    action = null;
                    return;
                }
                pending.remove(key);
                if (closed) {
                    action = null;
                    return;
                }
                claimed = action;
                action = null;
            }
            if (claimed == null) return;
            try {
                claimed.run();
            } catch (Throwable error) {
                XposedBridge.log(error);
            }
        }
    }
}