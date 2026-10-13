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
import java.lang.reflect.Method;
import java.util.ArrayDeque;
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
    private static final java.util.Map<Object, String> networkOrigins = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicInteger networkEvents = new AtomicInteger();
    private static final AtomicInteger sceneEvents = new AtomicInteger();
    private static final AtomicInteger sceneSequence = new AtomicInteger();
    private static final ThreadLocal<ArrayDeque<String>> sceneContext = new ThreadLocal<>();
    private static boolean sceneHookInstalled;
    private static volatile long started;
    private static volatile boolean ready;
    private static volatile boolean installed;

    private ServerErrorToastDiagnostic() {}

    public static synchronized void install(String process) {
        if (installed) return;
        installed = true;
        started = SystemClock.elapsedRealtime();
        emit(TAG + " armed revision=sync-origin-v3 module=" + BuildConfig.VERSION_NAME
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
        installSceneCorrelation(application.getClassLoader());
        installSyncOrigin(application.getClassLoader());
        new Handler(Looper.getMainLooper()).postDelayed(ServerErrorToastDiagnostic::stop,
                Math.max(0L, WINDOW_MS - (SystemClock.elapsedRealtime() - started)));
    }

    /** This exact callback was verified against the host APK and the captured toast stack.
     * No broad callback scan, request-body dump, or mutation of network results. */
    private static synchronized void installSceneCorrelation(ClassLoader loader) {
        if (sceneHookInstalled) return;
        try {
            Class<?> callback = Class.forName("com.tencent.mm.ui.conversation.x5", false, loader);
            Class<?> scene = Class.forName("com.tencent.mm.modelbase.m1", false, loader);
            Method target = callback.getDeclaredMethod("onSceneEnd", int.class, int.class, String.class, scene);
            hooks.add(XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active() || param.args == null || param.args.length != 4) return;
                    try {
                        Object request = param.args[3];
                        String type = sceneType(request);
                        int errType = ((Number) param.args[0]).intValue();
                        int errCode = ((Number) param.args[1]).intValue();
                        String entry = "sequence=" + sceneSequence.incrementAndGet()
                                + " callback=" + target.getDeclaringClass().getName()
                                + " errType=" + errType + " errCode=" + errCode
                                + " sceneType=" + type
                                + " sceneClass=" + (request == null ? "null" : request.getClass().getName())
                                + " sceneIdentity=" + (request == null ? 0 : System.identityHashCode(request))
                                + " errMsgLength=" + (param.args[2] instanceof String ? ((String) param.args[2]).length() : 0)
                                + " thread=" + Thread.currentThread().getName()
                                + " elapsedMs=" + (SystemClock.elapsedRealtime() - started);
                        String origin = request == null ? null : networkOrigins.get(request);
                        entry += " networkOrigin={" + (origin == null ? "not_captured" : origin) + "}";
                        ArrayDeque<String> stack = sceneContext.get();
                        if (stack == null) { stack = new ArrayDeque<>(); sceneContext.set(stack); }
                        stack.addLast(entry);
                        // Mark this invocation: nested callbacks can finish after the window expires.
                        param.setObjectExtra("hchat.scene.diagnostic", Boolean.TRUE);
                        if ((errType != 0 || errCode != 0 || "138".equals(type) || "139".equals(type))
                                && sceneEvents.incrementAndGet() <= 20) emit(TAG + " sceneEnd " + entry);
                    } catch (Throwable error) {
                        emit(TAG + " sceneCaptureFailure=" + error.getClass().getName());
                    }
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getObjectExtra("hchat.scene.diagnostic") != Boolean.TRUE) return;
                    ArrayDeque<String> stack = sceneContext.get();
                    if (stack != null) {
                        stack.pollLast();
                        if (stack.isEmpty()) sceneContext.remove();
                    }
                }
            }));
            sceneHookInstalled = true;
            emit(TAG + " sceneHook=installed callback=" + target.toGenericString());
        } catch (Throwable error) {
            // Explicitly report incompatible host versions instead of pretending correlation exists.
            emit(TAG + " sceneHook=unavailable error=" + error.getClass().getName());
        }
    }

    private static synchronized void installSyncOrigin(ClassLoader loader) {
        try {
            Class<?> sync = Class.forName("y51.b", false, loader);
            Class<?> rr = Class.forName("com.tencent.mm.network.y0", false, loader);
            Method method = sync.getDeclaredMethod("onGYNetEnd", int.class, int.class, int.class,
                    String.class, rr, byte[].class);
            hooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active() || param.args == null || param.args.length != 6) return;
                    try {
                        if (networkEvents.incrementAndGet() > 20) return;
                        Object response = param.args[4];
                        String entry = "sceneIdentity=" + System.identityHashCode(param.thisObject)
                                + " netId=" + param.args[0] + " errType=" + param.args[1]
                                + " errCode=" + param.args[2] + " responseClass="
                                + (response == null ? "null" : response.getClass().getName())
                                + " responseType=" + sceneType(response)
                                + " errMsgLength=" + (param.args[3] instanceof String ? ((String) param.args[3]).length() : 0)
                                + " bufferLength=" + (param.args[5] instanceof byte[] ? ((byte[]) param.args[5]).length : 0)
                                + " elapsedMs=" + (SystemClock.elapsedRealtime() - started)
                                + " thread=" + Thread.currentThread().getName();
                        networkOrigins.put(param.thisObject, entry);
                        emit(TAG + " syncNetworkEnd " + entry);
                        if (Integer.valueOf(1).equals(param.args[1]) && Integer.valueOf(-7).equals(param.args[2])) {
                            StringBuilder stack = new StringBuilder(TAG + " syncFailureOrigin " + entry);
                            StackTraceElement[] frames = Thread.currentThread().getStackTrace();
                            for (int i = 0; i < Math.min(frames.length, 24); i++) stack.append("\n  at ").append(frames[i]);
                            HLog.i(stack.toString());
                        }
                    } catch (Throwable error) { emit(TAG + " syncCaptureFailure=" + error.getClass().getName()); }
                }
            }));
            emit(TAG + " syncHook=installed callback=" + method.toGenericString());
        } catch (Throwable error) { emit(TAG + " syncHook=unavailable error=" + error.getClass().getName()); }
    }

    private static String sceneType(Object request) {
        if (request == null) return "null";
        try {
            Method method = request.getClass().getMethod("getType");
            method.setAccessible(true);
            return String.valueOf(method.invoke(request));
        } catch (Throwable error) { return "unavailable:" + error.getClass().getSimpleName(); }
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
        ArrayDeque<String> context = sceneContext.get();
        out.append("  correlatedScene=").append(context == null || context.isEmpty()
                ? "none (not inside verified callback)" : context.peekLast()).append('\n');
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
        networkOrigins.clear();
        emit(TAG + " finished matches=" + matches.get() + " sceneEvents=" + sceneEvents.get()
                + " sceneHookInstalled=" + sceneHookInstalled);
    }
}
