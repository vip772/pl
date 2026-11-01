package t.mma.hooks.api.ui;

import android.app.Activity;

import t.mma.hooks.core.HookRegistry;
import t.mma.utils.KavaReflector;

import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 微信 Activity 生命周期监听 API。
 */
public final class WeChatLifecycleApi implements AutoCloseable {
    public interface Logger {
        void log(String message);
    }

    public interface Listener {
        void onActivityEvent(ActivityEvent event);
    }

    public static final class ActivityEvent {
        public static final String RESUME = "resume";
        public static final String PAUSE = "pause";
        public static final String DESTROY = "destroy";

        public final String event;
        public final Activity activity;
        public final String activityName;

        private ActivityEvent(String event, Activity activity) {
            this.event = event;
            this.activity = activity;
            this.activityName = activity != null ? activity.getClass().getName() : "";
        }

        public boolean isResume() {
            return RESUME.equals(event);
        }

        public boolean isPause() {
            return PAUSE.equals(event);
        }

        public boolean isDestroy() {
            return DESTROY.equals(event);
        }

        public boolean isChatting() {
            return activityName.contains(".ui.chatting.");
        }
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

    public WeChatLifecycleApi(Logger logger) {
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
        int count = 0;
        count += hookLifecycleMethod("onResume", ActivityEvent.RESUME);
        count += hookLifecycleMethod("onPause", ActivityEvent.PAUSE);
        count += hookLifecycleMethod("onDestroy", ActivityEvent.DESTROY);
        hookedMethodCount = count;
        installed = count > 0;
        log("生命周期监听Hook: methods=" + count);
    }

    private int hookLifecycleMethod(String methodName, String event) {
        try {
            Method method = KavaReflector.findDeclaredMethod(Activity.class, methodName);
            hook(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (closed) return;
                    if (param.thisObject instanceof Activity) {
                        dispatch(new ActivityEvent(event, (Activity) param.thisObject));
                    }
                }
            });
            return 1;
        } catch (Throwable e) {
            log("生命周期Hook失败: " + methodName + " " + e.getMessage());
            return 0;
        }
    }

    private void dispatch(ActivityEvent event) {
        for (Listener listener : listeners) {
            if (closed) return;
            try {
                listener.onActivityEvent(event);
            } catch (Throwable e) {
                log("生命周期回调失败: " + e.getMessage());
            }
        }
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatLifecycleApi] " + message);
    }
}
