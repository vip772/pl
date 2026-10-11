package t.mma.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Temporary push-only diagnostic; no arguments, results, task state or payloads are changed. */
public final class NetworkOriginDiagnostic {
    private static final String TAG = "[Hchat:NetworkOriginDiagnostic]";
    private static final long WINDOW_MS = 600_000L;
    private static final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
    private static final AtomicInteger tasks = new AtomicInteger();
    private static final AtomicInteger auth = new AtomicInteger();
    private static final AtomicInteger clears = new AtomicInteger();
    private static final AtomicInteger failures = new AtomicInteger();
    private static final AtomicInteger lifecycle = new AtomicInteger();
    private static final AtomicInteger submissions = new AtomicInteger();
    private static final java.util.Map<Integer, String> stopOrigins = new java.util.LinkedHashMap<>();
    private static boolean installed;
    private static volatile boolean stopped;
    private static long started;
    private static String process;
    private NetworkOriginDiagnostic() {}

    public static synchronized void install(ClassLoader loader, String name) {
        if (installed || !"com.tencent.mm:push".equals(name)) return;
        installed = true;
        process = name;
        started = SystemClock.elapsedRealtime();
        log("armed revision=push-cancel-v2 windowMs=" + WINDOW_MS);
        // Install each verified entry independently: one incompatible signature must not hide the others.
        try {
            Class<?> adapter = Class.forName("com.tencent.mm.network.t2", false, loader);
            Method method = adapter.getDeclaredMethod("e", int.class, int.class, String.class);
            hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active() || clears.incrementAndGet() > 6) return;
                    try {
                        StringBuilder out = new StringBuilder("clearTaskAndCallback errType=")
                                .append(param.args[0]).append(" errCode=").append(param.args[1]);
                        Object queue = read(param.thisObject, "b");
                        if (queue instanceof Object[]) {
                            Object[] entries = (Object[]) queue;
                            // Only metadata. Do not invoke request encoding or inspect auth/response objects.
                            synchronized (entries) {
                                int emitted = 0;
                                for (int i = 0; i < entries.length && emitted < 32; i++) {
                                    Object entry = entries[i];
                                    if (entry == null) continue;
                                    emitted++;
                                    Object request = read(entry, "g");
                                    out.append("\n queue netId=").append(i).append(" taskId=")
                                            .append(read(entry, "k")).append(" requestType=").append(type(request));
                                }
                                out.append("\n queueSlots=").append(entries.length).append(" sampled=").append(emitted);
                            }
                        } else out.append(" queue=unavailable");
                        appendStack(out);
                        log(out.toString());
                    } catch (Throwable error) { failure("clearCapture", error); }
                }
            }));
            log("clearHook=installed signature=" + method.toGenericString());
        } catch (Throwable error) { failure("clearHook", error); }
        try {
            Class<?> auto = Class.forName("com.tencent.mm.network.j1", false, loader);
            Method method = auto.getDeclaredMethod("d0", int.class, int.class, String.class);
            hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active() || auth.incrementAndGet() > 6) return;
                    StringBuilder out = new StringBuilder("autoAuthFail inErrType=").append(param.args[0])
                            .append(" inErrCode=").append(param.args[1]);
                    appendStack(out);
                    log(out.toString());
                }
            }));
            log("authHook=installed signature=" + method.toGenericString());
        } catch (Throwable error) { failure("authHook", error); }
        try {
            Class<?> callback = Class.forName("com.tencent.mars.stn.StnManagerCallback", false, loader);
            Class<?> profile = Class.forName("com.tencent.mars.stn.StnManager$CgiProfile", false, loader);
            Class<?> adapter = Class.forName("com.tencent.mm.network.t2", false, loader);
            Class<?> accessor = Class.forName("com.tencent.mm.network.a3", false, loader);
            Method getAdapter = accessor.getDeclaredMethod("h");
            getAdapter.setAccessible(true);
            Method method = callback.getDeclaredMethod("onTaskEnd", int.class, Object.class,
                    int.class, int.class, profile, byte[].class);
            hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active() || tasks.get() >= 24) return;
                    try {
                        Object owner = getAdapter.invoke(null);
                        String mapping = "queue=unavailable";
                        Object queue = owner == null ? null : read(owner, "b");
                        if (queue instanceof Object[]) {
                            Object[] entries = (Object[]) queue;
                            int taskId = ((Number) param.args[0]).intValue();
                            synchronized (entries) {
                                for (int i = 0; i < entries.length; i++) {
                                    Object entry = entries[i];
                                    if (entry == null) continue;
                                    Object id = read(entry, "k");
                                    if (id instanceof Number && ((Number) id).intValue() == taskId) {
                                        mapping = "netId=" + i + " requestType=" + type(read(entry, "g"));
                                        break;
                                    }
                                }
                            }
                        }
                        boolean failed = ((Number) param.args[2]).intValue() != 0 || ((Number) param.args[3]).intValue() != 0;
                        if (!failed && !mapping.endsWith("requestType=138")) return;
                        if (tasks.incrementAndGet() > 24) return;
                        StringBuilder out = new StringBuilder("nativeTaskEnd taskId=").append(param.args[0])
                                .append(" nativeErrType=").append(param.args[2]).append(" nativeErrCode=")
                                .append(param.args[3]).append(' ').append(mapping);
                        synchronized (stopOrigins) {
                            String origin = stopOrigins.remove(((Number) param.args[0]).intValue());
                            out.append(" stopOrigin={").append(origin == null ? "not_captured" : origin).append('}');
                        }
                        Object value = param.args[4];
                        for (String field : new String[]{"channelType", "protocolType", "startTaskTick", "endTaskTick",
                                "startConnectTime", "connectSuccessfulTime", "startHandshakeTime", "handshakeSuccessfulTime",
                                "startSendPacketTime", "sendPacketFinishedTime", "startReadPacketTime", "readPacketFinishedTime"}) {
                            out.append(' ').append(field).append('=').append(value == null ? "null" : read(value, field));
                        }
                        log(out.toString());
                    } catch (Throwable error) { failure("taskCapture", error); }
                }
            }));
            log("taskHook=installed signature=" + method.toGenericString());
        } catch (Throwable error) { failure("taskHook", error); }
        installTaskLifecycle(loader);
        try {
            new Handler(Looper.getMainLooper()).postDelayed(NetworkOriginDiagnostic::stop, WINDOW_MS);
        } catch (Throwable error) {
            failure("timer", error);
            stop(); // Never leave an unlimited diagnostic hook if the cleanup timer cannot be scheduled.
        }
    }
    private static void installTaskLifecycle(ClassLoader loader) {
        try {
            Class<?> manager = Class.forName("com.tencent.mars.stn.StnManager", false, loader);
            // Independently verify and install wrapper and native boundary. A native hook may be
            // unavailable on a particular framework, but the Java wrapper remains useful.
            for (String name : new String[]{"stopTask", "OnJniStopTask"}) {
                try {
                    Method method = manager.getDeclaredMethod(name, int.class);
                    hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            if (!active() || lifecycle.incrementAndGet() > 32) return;
                            try {
                                int taskId = ((Number) param.args[0]).intValue();
                                StringBuilder out = new StringBuilder("taskStop source=").append(name)
                                        .append(" taskId=").append(taskId)
                                        .append(" managerIdentity=").append(System.identityHashCode(param.thisObject));
                                String origin = "source=" + name + " elapsedMs=" + (SystemClock.elapsedRealtime() - started);
                                synchronized (stopOrigins) {
                                    String previous = stopOrigins.get(taskId);
                                    // Retain wrapper and JNI events without unbounded growth or host object references.
                                    stopOrigins.put(taskId, previous == null ? origin : previous + ";" + origin);
                                    if (stopOrigins.size() > 32) stopOrigins.remove(stopOrigins.keySet().iterator().next());
                                }
                                appendStack(out);
                                log(out.toString());
                            } catch (Throwable error) { failure("stopCapture", error); }
                        }
                    }));
                    log("stopHook=" + name + " installed signature=" + method.toGenericString());
                } catch (Throwable error) { failure("stopHook:" + name, error); }
            }
        } catch (Throwable error) { failure("stopManager", error); }
        // Exact upper cancellation bridge verified in j1.smali: netId -> queue.k -> stopTask(taskId).
        try {
            Class<?> auto = Class.forName("com.tencent.mm.network.j1", false, loader);
            Method method = auto.getDeclaredMethod("j", auto, int.class);
            hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active() || lifecycle.incrementAndGet() > 32) return;
                    StringBuilder out = new StringBuilder("cancelBridge netId=").append(param.args[1]);
                    appendStack(out);
                    log(out.toString());
                }
            }));
            log("cancelBridgeHook=installed signature=" + method.toGenericString());
        } catch (Throwable error) { failure("cancelBridgeHook", error); }
        // Only numeric submission metadata, never cgi URL, headers, extraInfo or userContext.
        try {
            Class<?> manager = Class.forName("com.tencent.mars.stn.StnManager", false, loader);
            Class<?> task = Class.forName("com.tencent.mars.stn.StnManager$Task", false, loader);
            Method method = manager.getDeclaredMethod("startTask", task);
            hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active()) return;
                    try {
                        Object value = param.args[0];
                        Object id = read(value, "taskID");
                        if (id instanceof Number) synchronized (stopOrigins) {
                            stopOrigins.remove(((Number) id).intValue()); // IDs can be reused; never attach stale cancellation.
                        }
                        if (submissions.incrementAndGet() > 24) return;
                        log("taskSubmit taskId=" + id + " channelSelect=" + read(value, "channelSelect")
                                + " cmdID=" + read(value, "cmdID") + " cgiID=" + read(value, "cgiID"));
                    } catch (Throwable error) { failure("submitCapture", error); }
                }
            }));
            log("submitHook=installed signature=" + method.toGenericString());
        } catch (Throwable error) { failure("submitHook", error); }
    }
    private static boolean active() { return !stopped && SystemClock.elapsedRealtime() - started < WINDOW_MS; }
    private static Object read(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static String type(Object target) {
        if (target == null) return "null";
        try {
            Method method = target.getClass().getMethod("getType");
            method.setAccessible(true);
            return String.valueOf(method.invoke(target));
        } catch (Throwable error) { return "unavailable"; }
    }
    private static void appendStack(StringBuilder out) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 24); i++) out.append("\n at ").append(stack[i]);
    }
    private static void failure(String entry, Throwable error) { if (failures.incrementAndGet() <= 8) log(entry + "=unavailable error=" + error.getClass().getName()); }
    private static void log(String entry) {
        // Use LSPosed only: push must not initialise the main process's runtime-file logger/configuration.
        XposedBridge.log(TAG + " process=" + process + " pid=" + android.os.Process.myPid()
                + " uid=" + android.os.Process.myUid() + " elapsedMs="
                + (SystemClock.elapsedRealtime() - started) + " " + entry);
    }
    private static synchronized void stop() {
        if (stopped) return;
        stopped = true;
        for (XC_MethodHook.Unhook hook : hooks) try { hook.unhook(); } catch (Throwable ignored) { }
        hooks.clear();
        synchronized (stopOrigins) { stopOrigins.clear(); }
        log("finished taskEvents=" + tasks.get() + " authEvents=" + auth.get() + " clearEvents=" + clears.get());
    }
}
