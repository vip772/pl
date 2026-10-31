package t.mma.dexkit;

import org.luckypray.dexkit.DexKitBridge;
import t.mma.hooks.core.DexInstallScheduler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 共享宿主 DexKit 的代际与租约登记器。
 *
 * <p>本类只负责所有权登记和查询期间保护，不会默认打开或关闭宿主 bridge。
 * 在原始 bridge 使用者尚未迁移完成前，兼容租约会阻止闲置回收。</p>
 */
public final class DexKitBridgeManager {
    private final Object lock = new Object();
    private final Map<Long, State> generations = new LinkedHashMap<>();
    private long nextGeneration;
    private State current;

    /** 发布或复用当前宿主 bridge，并分配稳定的进程内代际编号。 */
    public Generation publish(DexKitBridge bridge, ClassLoader classLoader, String apkPath) {
        if (bridge == null) throw new IllegalArgumentException("bridge == null");
        synchronized (lock) {
            if (current != null && !current.closed && current.bridge == bridge) {
                return current.snapshot();
            }
            if (current != null && !current.closed) current.retired = true;
            State state = new State(
                    ++nextGeneration,
                    bridge,
                    classLoader,
                    apkPath == null ? "" : apkPath
            );
            generations.put(state.id, state);
            current = state;
            return state.snapshot();
        }
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
        synchronized (lock) {
            if (current == null || current.closed) {
                throw new IllegalStateException("没有可用的 DexKit 代际");
            }
            State state = current;
            state.leaseCount++;
            if (rawCompatibility) state.rawCompatibilityLeaseCount++;
            return new Lease(this, state, owner == null ? "" : owner, rawCompatibility);
        }
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
        State state;
        synchronized (lock) {
            state = current;
            if (state == null || state.closed) {
                throw new IllegalStateException("没有可用的 DexKit 代际");
            }
            state.activeQueryCount++;
        }
        try {
            return DexInstallScheduler.queryDexKitTask(() -> task.apply(state.bridge));
        } finally {
            leave(state);
        }
    }

    /** 当前代没有租约和活动查询时关闭它；默认不会调用，供第 8 步显式启用。 */
    public boolean closeIfIdle() {
        State state;
        synchronized (lock) {
            state = current;
            if (state == null || state.closed || state.leaseCount != 0 ||
                    state.activeQueryCount != 0 || state.rawCompatibilityLeaseCount != 0) {
                return false;
            }
            state.closed = true;
            current = null;
            generations.remove(state.id);
        }
        return closeNative(state);
    }

    /** 关闭已被新代替换且不再有租约/查询的旧代；默认不会自动触发。 */
    public int closeRetiredIfIdle() {
        Map<Long, State> closable = new LinkedHashMap<>();
        synchronized (lock) {
            for (Map.Entry<Long, State> entry : generations.entrySet()) {
                State state = entry.getValue();
                if (!state.retired || state.closed || state.leaseCount != 0 ||
                        state.activeQueryCount != 0 || state.rawCompatibilityLeaseCount != 0) {
                    continue;
                }
                state.closed = true;
                closable.put(entry.getKey(), state);
            }
            for (Long id : closable.keySet()) generations.remove(id);
        }
        int closed = 0;
        for (State state : closable.values()) {
            if (closeNative(state)) closed++;
        }
        return closed;
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
        }
    }

    private boolean closeNative(State state) {
        try {
            state.bridge.close();
            return true;
        } catch (Throwable ignored) {
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
