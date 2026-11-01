package t.mma.hooks.api.ui;

import android.app.Activity;
import android.content.ContextWrapper;
import android.content.Intent;

import t.mma.hooks.core.HookRegistry;
import t.mma.utils.KavaReflector;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 微信 Activity 启动监听 API。
 */
public final class WeChatActivityStartApi implements AutoCloseable {
    public interface Logger {
        void log(String message);
    }

    public interface Listener {
        void onStartActivity(Intent intent, Object caller, Method method);
    }

    public final class Subscription {
        private final Listener listener;
        private volatile boolean active = true;

        private Subscription(Listener listener) {
            this.listener = listener;
        }

        public void unsubscribe() {
            if (!active) return;
            active = false;
            listeners.remove(listener);
        }

        public boolean isActive() {
            return active && !closed;
        }
    }

    private final Logger logger;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean installed;
    private volatile boolean closed;
    private final java.util.ArrayList<XC_MethodHook.Unhook> ownedHooks = new java.util.ArrayList<>();
    private XC_MethodHook.Unhook hook(java.lang.reflect.Member method, XC_MethodHook callback) {
        XC_MethodHook.Unhook handle = HookRegistry.get().hook(method, callback);
        ownedHooks.add(handle);
        return handle;
    }
    private volatile int hookedMethodCount;

    public WeChatActivityStartApi(Logger logger) {
        this.logger = logger;
    }

    public boolean isAvailable() {
        return installed;
    }

    public int hookedMethodCount() {
        return hookedMethodCount;
    }

    public synchronized Subscription subscribe(Listener listener) {
        if (closed || listener == null) return null;
        listeners.addIfAbsent(listener);
        return new Subscription(listener);
    }

    public void unsubscribe(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        installed = false;
        listeners.clear();
        hookedMethodCount = 0;
        java.util.ArrayList<XC_MethodHook.Unhook> snapshot = new java.util.ArrayList<>(ownedHooks);
        ownedHooks.clear();
        for (XC_MethodHook.Unhook handle : snapshot) {
            try { HookRegistry.get().unhook(handle); } catch (Throwable ignored) { }
        }
    }

    public synchronized void install() {
        if (closed || installed) return;
        int count = hookStartMethods(Activity.class);
        count += hookStartMethods(ContextWrapper.class);
        hookedMethodCount = count;
        installed = count > 0;
        log("Activity启动监听Hook: methods=" + count);
    }

    private int hookStartMethods(Class<?> clazz) {
        int count = 0;
        for (Method method : KavaReflector.declaredMethods(clazz)) {
            if (!isStartMethod(method)) continue;
            hook(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (closed) return;
                    Intent intent = findIntent(param.args);
                    if (intent != null) dispatch(intent, param.thisObject, method);
                }
            });
            count++;
        }
        return count;
    }

    private boolean isStartMethod(Method method) {
        if (method == null) return false;
        String name = method.getName();
        if (!"startActivity".equals(name) && !"startActivityForResult".equals(name)) {
            return false;
        }
        Class<?>[] params = method.getParameterTypes();
        if (params == null || params.length == 0) return false;
        for (Class<?> param : params) {
            if (param == Intent.class) return true;
        }
        return false;
    }

    private Intent findIntent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Intent) return (Intent) arg;
        }
        return null;
    }

    private void dispatch(Intent intent, Object caller, Method method) {
        for (Listener listener : listeners) {
            if (closed) return;
            try {
                listener.onStartActivity(intent, caller, method);
            } catch (Throwable e) {
                log("Activity启动监听回调失败: " + e.getMessage());
            }
        }
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatActivityStartApi] " + message);
    }
}
