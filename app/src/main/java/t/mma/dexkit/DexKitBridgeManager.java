package t.mma.dexkit;

import org.luckypray.dexkit.DexKitBridge;
import t.mma.hooks.core.DexInstallScheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import t.mma.utils.HLog;

/**
 * 共享宿主 DexKit 的代际与租约登记器。
 *
 * <p>默认只登记所有权；完成调用方迁移后由入口显式启用按需重建和闲置回收。
 * 在原始 bridge 使用者尚未迁移完成前，兼容租约会阻止闲置回收。</p>
 */
public final class DexKitBridgeManager {
    private final Object lock = new Object();
    private final Map<Long, State> generations = new LinkedHashMap<>();
    private long nextGeneration;
    private State current;
    private java.util.function.Supplier<DexKitBridge> factory;
    private ClassLoader factoryLoader;
    private String factoryPath;
    private long idleMillis;
    private final java.util.concurrent.ScheduledThreadPoolExecutor idleExecutor = createIdleExecutor();
    private java.util.concurrent.ScheduledFuture<?> idleFuture;
    private long idleTicket;
    private volatile String lastCloseFailure;
    private static java.util.concurrent.ScheduledThreadPoolExecutor createIdleExecutor() {
        java.util.concurrent.ScheduledThreadPoolExecutor executor = new java.util.concurrent.ScheduledThreadPoolExecutor(1,
                task -> { Thread thread = new Thread(task, "Hchat-HostDexIdle"); thread.setDaemon(true); return thread; });
        executor.setRemoveOnCancelPolicy(true);
        executor.setKeepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
    /** Configure only after all internal raw owners have migrated. No polling. */
    public void enableIdleRebuild(java.util.function.Supplier<DexKitBridge> creator,
                                  ClassLoader loader, String path, long timeoutMillis) {
        if (creator == null || timeoutMillis <= 0) throw new IllegalArgumentException("无效的重建配置");
        DexInstallScheduler.runDexKitTask(() -> {
            synchronized (lock) {
                factory = creator; factoryLoader = loader; factoryPath = path; idleMillis = timeoutMillis;
                armIdleLocked();
            }
        });
    }
    public String getLastCloseFailure() { return lastCloseFailure; }
    private State requireCurrentLocked() {
        if (current != null && !current.closed) return current;
        if (factory == null) throw new IllegalStateException("没有可用的 DexKit 代际");
        DexKitBridge bridge = factory.get(); // shared query gate guarantees one creator
        if (bridge == null) throw new IllegalStateException("DexKit 重建返回空实例");
        State state = new State(++nextGeneration, bridge, factoryLoader, factoryPath == null ? "" : factoryPath);
        generations.put(state.id, state); current = state;
        HLog.i("[Hchat:DexKit] 重建代际=" + state.id);
        return state;
    }
    private void cancelIdleLocked() {
        idleTicket++;
        if (idleFuture != null) idleFuture.cancel(false);
        idleFuture = null;
    }
    private void armIdleLocked() {
        cancelIdleLocked();
        if (factory == null || current == null || current.closed || current.leaseCount != 0 || current.activeQueryCount != 0) return;
        long ticket = idleTicket;
        idleFuture = idleExecutor.schedule(() -> DexInstallScheduler.runDexKitTask(() -> {
            synchronized (lock) {
                if (idleTicket != ticket) return;
                closeCurrentLocked();
            }
        }), idleMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    private boolean closeCurrentLocked() {
        State state = current;
        if (state == null || state.closed || state.leaseCount != 0 || state.activeQueryCount != 0) return false;
        cancelIdleLocked();
        state.closed = true; current = null; generations.remove(state.id);
        HLog.i("[Hchat:DexKit] 闲置关闭代际=" + state.id);
        return closeNative(state); // shared gate: no query/create may race native close
    }
    /** Raw objects may escape into arbitrary script globals/threads. Pin this generation for process lifetime. */
    public DexKitBridge rawCompatibilityBridge(String owner) {
        return DexInstallScheduler.queryDexKitTask(() -> {
            synchronized (lock) {
                State state = requireCurrentLocked();
                cancelIdleLocked();
                if (!state.rawExposed) {
                    state.rawExposed = true; state.leaseCount++; state.rawCompatibilityLeaseCount++;
                    HLog.i("[Hchat:DexKit] 原始兼容对象按进程保活，代际=" + state.id + " owner=" + owner);
                }
                return state.bridge;
            }
        });
    }

    /** 发布或复用当前宿主 bridge，并分配稳定的进程内代际编号。 */
    public Generation publish(DexKitBridge bridge, ClassLoader classLoader, String apkPath) {
        if (bridge == null) throw new IllegalArgumentException("bridge == null");
        return DexInstallScheduler.queryDexKitTask(() -> {
            synchronized (lock) {
                for (State known : generations.values()) {
                    if (!known.closed && known.bridge == bridge) {
                        if (current != null && current != known) current.retired = true;
                        known.retired = false; current = known; armIdleLocked(); return known.snapshot();
                    }
                }
                if (current != null && !current.closed) current.retired = true;
                State state = new State(++nextGeneration, bridge, classLoader, apkPath == null ? "" : apkPath);
                generations.put(state.id, state); current = state; armIdleLocked(); return state.snapshot();
            }
        });
    }
    public Generation currentGeneration() {
        synchronized (lock) {
            return current == null || current.closed ? null : current.snapshot();
        }
    }

    public long currentGenerationId() {
        Generation generation = currentGeneration();
        return generation == null ? 0L : generation.id;
    }

    /** 在当前代的活动查询计数保护下执行回调，不向外暴露 DexKit Data。 */
    public <T> T withCurrentBridge(Function<DexKitBridge, T> action) {
        if (action == null) throw new IllegalArgumentException("action == null");
        try {
            return queryCurrentBridge(action);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("DexKit 查询失败", error);
        }
    }

    /**
     * 登记持久租约。rawCompatibility=true 表示调用方可能保留原始 bridge/holder，
     * 该租约释放前不能安全关闭 native bridge。
     */
    public Lease acquire(String owner, boolean rawCompatibility) {
        return DexInstallScheduler.queryDexKitTask(() -> {
            synchronized (lock) {
                State state = requireCurrentLocked(); cancelIdleLocked();
                state.leaseCount++;
                if (rawCompatibility) state.rawCompatibilityLeaseCount++;
                return new Lease(this, state, owner == null ? "" : owner, rawCompatibility);
            }
        });
    }
    /** 让一次查询在租约和统一 DexKit 串行门的共同保护下执行。 */
    public <T> T query(Lease lease, java.util.concurrent.Callable<T> task) throws Exception {
        if (task == null) throw new IllegalArgumentException("task == null");
        State state = enter(lease);
        try {
            return DexInstallScheduler.queryDexKitTask(() -> task.call());
        } finally {
            leave(state);
        }
    }

    /** 无持久租约的短查询入口，供只返回脱离 native 数据的新 API 使用。 */
    public <T> T queryCurrent(java.util.concurrent.Callable<T> task) throws Exception {
        if (task == null) throw new IllegalArgumentException("task == null");
        try {
            return queryCurrentBridge(bridge -> {
                try {
                    return task.call();
                } catch (Exception error) {
                    throw new QueryException(error);
                }
            });
        } catch (QueryException error) {
            throw (Exception) error.getCause();
        }
    }

    /**
     * 捕获同一代的 bridge 后执行短查询，避免发布新代际时把回调切换到错误 bridge。
     */
    public <T> T queryCurrentBridge(java.util.function.Function<DexKitBridge, T> task) throws Exception {
        if (task == null) throw new IllegalArgumentException("task == null");
        return DexInstallScheduler.queryDexKitTask(() -> {
            State state;
            synchronized (lock) {
                state = requireCurrentLocked(); cancelIdleLocked(); state.activeQueryCount++;
            }
            try { return task.apply(state.bridge); }
            finally { leave(state); }
        });
    }
    public boolean closeIfIdle() {
        return DexInstallScheduler.queryDexKitTask(() -> { synchronized (lock) { return closeCurrentLocked(); } });
    }
    public int closeRetiredIfIdle() {
        return DexInstallScheduler.queryDexKitTask(() -> {
            synchronized (lock) {
                int count = 0;
                java.util.Iterator<State> iterator = generations.values().iterator();
                while (iterator.hasNext()) {
                    State state = iterator.next();
                    if (!state.retired || state.closed || state.leaseCount != 0 || state.activeQueryCount != 0) continue;
                    state.closed = true; iterator.remove();
                    if (closeNative(state)) count++;
                }
                return count;
            }
        });
    }
    public boolean hasRawCompatibilityLease() {
        synchronized (lock) {
            return current != null && current.rawCompatibilityLeaseCount != 0;
        }
    }

    public int currentLeaseCount() {
        synchronized (lock) {
            return current == null ? 0 : current.leaseCount;
        }
    }

    public int currentActiveQueryCount() {
        synchronized (lock) {
            return current == null ? 0 : current.activeQueryCount;
        }
    }

    private State enter(Lease lease) {
        synchronized (lock) {
            if (lease == null || lease.manager != this || lease.closed || lease.state.closed) {
                throw new IllegalStateException("DexKit 租约已关闭或不属于当前管理器");
            }
            lease.state.activeQueryCount++;
            return lease.state;
        }
    }

    private void leave(State state) {
        synchronized (lock) {
            if (state.activeQueryCount > 0) state.activeQueryCount--;
            armIdleLocked();
        }
    }

    private void release(Lease lease) {
        synchronized (lock) {
            if (lease.closed) return;
            lease.closed = true;
            if (lease.state.leaseCount > 0) lease.state.leaseCount--;
            if (lease.rawCompatibility && lease.state.rawCompatibilityLeaseCount > 0) {
                lease.state.rawCompatibilityLeaseCount--;
            }
            armIdleLocked();
        }
    }

    private boolean closeNative(State state) {
        try {
            state.bridge.close();
            return true;
        } catch (Throwable failure) {
            // A partially closed native object must never be reused. A later query creates a fresh generation.
            lastCloseFailure = failure.getClass().getName() + ": " + failure.getMessage();
            HLog.w("[Hchat:DexKit] 关闭异常，隔离旧代际=" + state.id + " " + lastCloseFailure);
            return false;
        }
    }

    private static final class QueryException extends RuntimeException {
        private QueryException(Exception cause) {
            super(cause);
        }
    }

    public static final class Generation {
        private final long id;
        private final ClassLoader classLoader;
        private final String apkPath;

        private Generation(long id, ClassLoader classLoader, String apkPath) {
            this.id = id;
            this.classLoader = classLoader;
            this.apkPath = apkPath;
        }

        public long getId() {
            return id;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }

        public String getApkPath() {
            return apkPath;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final DexKitBridgeManager manager;
        private final State state;
        private final String owner;
        private final boolean rawCompatibility;
        private boolean closed;

        private Lease(DexKitBridgeManager manager, State state, String owner, boolean rawCompatibility) {
            this.manager = manager;
            this.state = state;
            this.owner = owner;
            this.rawCompatibility = rawCompatibility;
        }

        public long getGenerationId() {
            return state.id;
        }

        public String getOwner() {
            return owner;
        }

        public boolean isRawCompatibility() {
            return rawCompatibility;
        }

        public synchronized boolean isClosed() {
            return closed;
        }

        @Override
        public synchronized void close() {
            manager.release(this);
        }
    }

    private static final class State {
        private final long id;
        private final DexKitBridge bridge;
        private final ClassLoader classLoader;
        private final String apkPath;
        private int leaseCount;
        private int rawCompatibilityLeaseCount;
        private int activeQueryCount;
        private boolean rawExposed;
        private boolean retired;
        private boolean closed;

        private State(long id, DexKitBridge bridge, ClassLoader classLoader, String apkPath) {
            this.id = id;
            this.bridge = bridge;
            this.classLoader = classLoader;
            this.apkPath = apkPath;
        }

        private Generation snapshot() {
            return new Generation(id, classLoader, apkPath);
        }
    }
}
