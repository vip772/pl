package t.mma.utils;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import t.mma.BuildConfig;

/** Temporary, read-only diagnostic. Never changes arguments, results or exceptions. */
public final class ServerErrorToastDiagnostic {
    private static final String TAG = "[Hchat:ServerErrorDiagnostic]";
    private static final long WINDOW_MS = 180_000L;
    private static final AtomicInteger matches = new AtomicInteger();
    private static final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
    private static final List<String> pending = new ArrayList<>();
    private static final Set<Object> recordedToasts = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile long started;
    private static volatile boolean ready;
    private static volatile boolean installed;

    private ServerErrorToastDiagnostic() {}

    public static synchronized void install(String process) {
        if (installed) return;
        installed = true;
        started = SystemClock.elapsedRealtime();
        emit(TAG + " armed revision=toast-stack-v1 module=" + BuildConfig.VERSION_NAME
                + " code=" + BuildConfig.VERSION_CODE + " process=" + process
                + " pid=" + android.os.Process.myPid() + " windowMs=" + WINDOW_MS);
        try {
            hooks.addAll(XposedBridge.hookAllMethods(Toast.class, "makeText", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!active()) return;
                    Object text = param.args != null && param.args.length > 1 ? param.args[1] : null;
                    if (text instanceof Integer && param.args[0] instanceof android.content.Context) {
                        try { text = ((android.content.Context) param.args[0]).getText((Integer) text); }
                        catch (Throwable ignored) { return; }
                    }
                    if (isTarget(text)) capture("makeText", param.getResult());
                }
            }));
            hooks.addAll(XposedBridge.hookAllMethods(Toast.class, "setText", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active() || param.args == null || param.args.length == 0) return;
                    Object text = param.args[0];
                    if (text instanceof Integer) {
                        try {
                            Field field = Toast.class.getDeclaredField("mContext");
                            field.setAccessible(true);
                            text = ((android.content.Context) field.get(param.thisObject)).getText((Integer) text);
                        } catch (Throwable ignored) { return; }
                    }
                    if (isTarget(text)) capture("setText", param.thisObject);
                }
            }));
            hooks.addAll(XposedBridge.hookAllMethods(Toast.class, "show", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active()) return;
                    synchronized (recordedToasts) {
                        if (recordedToasts.contains(param.thisObject)) return;
                    }
                    try {
                        Field field = Toast.class.getDeclaredField("mText");
                        field.setAccessible(true);
                        if (isTarget(field.get(param.thisObject))) {
                            capture("show.mText", param.thisObject);
                            return;
                        }
                    } catch (Throwable ignored) { }
                    try {
                        if (targetView(((Toast) param.thisObject).getView(), 0)) {
                            capture("show.customView", param.thisObject);
                        }
                    } catch (Throwable ignored) { }
                }
            }));
            hooks.add(XposedBridge.hookMethod(TextView.class.getDeclaredMethod(
                    "setText", CharSequence.class, TextView.BufferType.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (active() && param.args != null && param.args.length > 0 && isTarget(param.args[0])) {
                        capture("TextView.setText", param.thisObject);
                    }
                }
            }));
        } catch (Throwable error) {
            emit(TAG + " installFailure=" + android.util.Log.getStackTraceString(error));
        }
    }

    public static synchronized void onApplicationReady(Application application) {
        HLog.initialize(application);
        ready = true;
        for (String entry : pending) HLog.w(entry);
        pending.clear();
        emit(TAG + " applicationReady elapsedMs=" + (SystemClock.elapsedRealtime() - started));
        new Handler(Looper.getMainLooper()).postDelayed(ServerErrorToastDiagnostic::stop,
                Math.max(0L, WINDOW_MS - (SystemClock.elapsedRealtime() - started)));
    }

    private static boolean active() {
        return installed && matches.get() < 3 && SystemClock.elapsedRealtime() - started < WINDOW_MS;
    }

    private static boolean isTarget(Object text) {
        if (!(text instanceof CharSequence)) return false;
        String value = text.toString();
        return value.contains("无法连接到服务器") || value.contains("(1,-7)")
                || value.contains("(1,−7)") || value.contains("(1, -7)");
    }

    private static boolean targetView(View view, int depth) {
        if (view == null || depth > 5) return false;
        if (view instanceof TextView && isTarget(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < Math.min(group.getChildCount(), 32); i++) {
                if (targetView(group.getChildAt(i), depth + 1)) return true;
            }
        }
        return false;
    }

    private static void capture(String source, Object toast) {
        synchronized (recordedToasts) {
            if (toast != null && !recordedToasts.add(toast)) return;
        }
        int number = matches.incrementAndGet();
        if (number > 3) return;
        StringBuilder out = new StringBuilder(TAG).append(" targetToast index=").append(number)
                .append(" source=").append(source).append(" elapsedMs=")
                .append(SystemClock.elapsedRealtime() - started).append(" thread=")
                .append(Thread.currentThread().getName()).append("\n");
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 80); i++) out.append("  at ").append(stack[i]).append('\n');
        emit(out.toString());
    }

    private static synchronized void emit(String entry) {
        if (ready) HLog.w(entry);
        else {
            if (pending.size() < 12) pending.add(entry);
            XposedBridge.log(entry);
        }
    }

    private static synchronized void stop() {
        for (XC_MethodHook.Unhook hook : hooks) {
            try { hook.unhook(); } catch (Throwable ignored) { }
        }
        hooks.clear();
        emit(TAG + " finished matches=" + matches.get());
    }
}
