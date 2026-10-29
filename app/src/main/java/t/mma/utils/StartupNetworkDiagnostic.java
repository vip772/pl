package t.mma.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded, read-only trace of verified WeChat 8.0.78 startup sync/cancellation entries. */
public final class StartupNetworkDiagnostic {
    private static final long WINDOW_MS = 180_000L;
    private static final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
    private static final AtomicInteger events = new AtomicInteger();
    private static volatile boolean stopped;
    private static boolean installed;
    private static long started;
    private static String process;

    private StartupNetworkDiagnostic() {}

    public static synchronized void install(ClassLoader loader, String name) {
        if (installed) return;
        installed = true;
        started = SystemClock.elapsedRealtime();
        process = name;
        log("armed revision=main-sync-cancel-v1 windowMs=" + WINDOW_MS);
        try {
            Class<?> scene = Class.forName("com.tencent.mm.modelbase.m1", false, loader);
            hook(scene.getDeclaredMethod("cancel"), "sceneCancel", true, true);
            Class<?> queue = Class.forName("com.tencent.mm.modelbase.r1", false, loader);
            hook(queue.getDeclaredMethod("h", scene, int.class), "queueSubmit", true, true);
            hook(queue.getDeclaredMethod("d", scene), "queueCancel", true, true);
            for (String reset : new String[]{"f", "r", "s"}) {
                hook(queue.getDeclaredMethod(reset), "queueReset:" + reset, false, true);
            }
            Class<?> response = Class.forName("com.tencent.mm.network.y0", false, loader);
            Class<?> sync = Class.forName("y51.b", false, loader);
            hook(sync.getDeclaredMethod("onGYNetEnd", int.class, int.class, int.class,
                    String.class, response, byte[].class), "syncNetworkEnd", false, false);
            hook(queue.getDeclaredMethod("onSceneEnd", int.class, int.class, String.class, scene),
                    "queueEnd", true, false);
        } catch (Throwable error) {
            log("entry=unavailable error=" + error.getClass().getName());
        }
        try {
            if (!new Handler(Looper.getMainLooper()).postDelayed(StartupNetworkDiagnostic::stop, WINDOW_MS)) stop();
        } catch (Throwable error) { stop(); }
    }

    private static void hook(Method method, String entry, boolean filterScene, boolean stack) {
        try {
            hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active() || events.get() >= 48) return;
                    try {
                        Object scene = entry.equals("sceneCancel") ? param.thisObject : null;
                        if (filterScene && !entry.equals("sceneCancel")) {
                            scene = param.args[entry.equals("queueEnd") ? 3 : 0];
                        }
                        if (filterScene && (scene == null || !Integer.valueOf(138).equals(
                                KavaReflector.invokeOrThrow(KavaReflector.findMethodRecursive(scene.getClass(), "getType"), scene)))) return;
                        if (events.incrementAndGet() > 48) return;
                        StringBuilder out = new StringBuilder(entry);
                        if (scene != null) {
                            out.append(" sceneIdentity=").append(System.identityHashCode(scene))
                                    .append(" sceneClass=").append(scene.getClass().getName())
                                    .append(" netId=").append(KavaReflector.readField(scene, "netId"));
                        }
                        if (entry.equals("queueEnd")) {
                            out.append(" errType=").append(param.args[0]).append(" errCode=").append(param.args[1]);
                        } else if (entry.equals("syncNetworkEnd")) {
                            out.append(" sceneIdentity=").append(System.identityHashCode(param.thisObject))
                                    .append(" netId=").append(param.args[0])
                                    .append(" errType=").append(param.args[1]).append(" errCode=").append(param.args[2]);
                        }
                        if (stack) {
                            StackTraceElement[] frames = new Throwable().getStackTrace();
                            for (int i = 0; i < Math.min(28, frames.length); i++) out.append("\n at ").append(frames[i]);
                        }
                        log(out.toString());
                    } catch (Throwable error) {
                        // Diagnostic failure must not propagate into native request processing.
                        log(entry + " capture=unavailable error=" + error.getClass().getName());
                    }
                }
            }));
            log("hook=installed entry=" + entry + " signature=" + method.toGenericString());
        } catch (Throwable error) { log("hook=unavailable entry=" + entry + " error=" + error.getClass().getName()); }
    }

    private static boolean active() {
        return !stopped && SystemClock.elapsedRealtime() - started < WINDOW_MS;
    }

    private static void log(String value) {
        // Shared monotonic uptime permits main/push correlation without writing one file from two processes.
        XposedBridge.log("[Hchat:StartupNetwork] process=" + process + " pid=" + android.os.Process.myPid()
                + " uptimeMs=" + SystemClock.elapsedRealtime() + " elapsedMs="
                + (SystemClock.elapsedRealtime() - started) + " " + value);
    }

    private static synchronized void stop() {
        if (stopped) return;
        stopped = true;
        for (XC_MethodHook.Unhook hook : hooks) try { hook.unhook(); } catch (Throwable ignored) {}
        hooks.clear();
        log("finished events=" + events.get());
    }
}
