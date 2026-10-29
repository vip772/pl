package t.mma.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import t.mma.hooks.core.HookRegistry;

/** One bounded recovery allowance for the first startup sync in the verified 8.0.78 push. */
public final class StartupSyncRetryGuard {
    private static final String TAG = "[Hchat:StartupSyncRetryGuard]";
    private static final long WINDOW_MS = 60000;
    private static boolean installed;
    private StartupSyncRetryGuard() {}

    public static synchronized void install(ClassLoader loader, String version, String processName) {
        if (installed || loader == null || !"8.0.78".equals(version)
                || !"com.tencent.mm:push".equals(processName)) return;
        List<XC_MethodHook.Unhook> handles = new ArrayList<>();
        try {
            Class<?> manager = KavaReflector.loadClass("com.tencent.mars.stn.StnManager", loader);
            Class<?> task = KavaReflector.loadClass("com.tencent.mars.stn.StnManager$Task", loader);
            Method start = KavaReflector.findDeclaredMethod(manager, "startTask", task);
            Field cgi = required(task, "cgiID", long.class);
            Field cmd = required(task, "cmdID", int.class);
            Field retry = required(task, "retryCount", int.class);
            Field polling = required(task, "longPolling", boolean.class);
            if (start == null || start.getReturnType() != void.class) {
                throw new IllegalStateException("8.0.78 StnManager.startTask(Task):void unavailable");
            }
            Handler handler = new Handler(Looper.getMainLooper());
            long until = SystemClock.elapsedRealtime() + WINDOW_MS;
            Runnable cleanup = () -> {
                synchronized (handles) {
                    for (XC_MethodHook.Unhook handle : handles) HookRegistry.get().unhook(handle);
                    handles.clear();
                }
            };
            XC_MethodHook callback = new XC_MethodHook() {
                private boolean consumed;
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (SystemClock.elapsedRealtime() >= until) { cleanup.run(); return; }
                    try {
                        Object value = param.args[0];
                        if (value == null || cgi.getLong(value) != 138 || cmd.getInt(value) != 121
                                || polling.getBoolean(value)) return;
                        synchronized (this) {
                            if (consumed) return;
                            // Consume even when the host chose a different retry policy. Never override 0.
                            consumed = true;
                            if (retry.getInt(value) == 1) {
                                retry.setInt(value, 2);
                            }
                        }
                        cleanup.run(); // No per-task overhead after the initial sync.
                    } catch (Throwable error) {
                        cleanup.run();
                        HLog.e(TAG + " 8.0.78 startTask 首次同步保护失败，停用保护", error);
                    }
                }
            };
            synchronized (handles) { handles.add(HookRegistry.get().hook(start, callback)); }
            if (!handler.postDelayed(cleanup, WINDOW_MS)) {
                cleanup.run();
                throw new IllegalStateException("startup sync guard expiry unavailable");
            }
            installed = true;
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook handle : handles) HookRegistry.get().unhook(handle);
            handles.clear();
            HLog.e(TAG + " 8.0.78 push 安装失败", error);
        }
    }

    private static Field required(Class<?> type, String name, Class<?> expected) {
        Field field = KavaReflector.findDeclaredField(type, name);
        if (field == null || field.getType() != expected) {
            throw new IllegalStateException("8.0.78 StnManager.Task." + name + " descriptor mismatch");
        }
        return field;
    }
}
