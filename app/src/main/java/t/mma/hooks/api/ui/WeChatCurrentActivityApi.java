package t.mma.hooks.api.ui;

import android.app.Activity;

import t.mma.hooks.core.HookRegistry;
import t.mma.utils.KavaReflector;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 当前微信 Activity 跟踪 API。
 */
public final class WeChatCurrentActivityApi implements AutoCloseable {
    public interface Logger {
        void log(String message);
    }

    private final Logger logger;
    private volatile WeakReference<Activity> currentActivity =
            new WeakReference<>(null);
    private volatile boolean installed;
    private volatile boolean closed;
    private final java.util.ArrayList<XC_MethodHook.Unhook> ownedHooks = new java.util.ArrayList<>();
    private XC_MethodHook.Unhook hook(java.lang.reflect.Member method, XC_MethodHook callback) {
        XC_MethodHook.Unhook handle = HookRegistry.get().hook(method, callback);
        ownedHooks.add(handle);
        return handle;
    }

    public WeChatCurrentActivityApi(Logger logger) {
        this.logger = logger;
    }

    public boolean isAvailable() {
        return installed;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        installed = false;
        currentActivity = new WeakReference<>(null);
        java.util.ArrayList<XC_MethodHook.Unhook> snapshot = new java.util.ArrayList<>(ownedHooks);
        ownedHooks.clear();
        for (XC_MethodHook.Unhook handle : snapshot) {
            try { HookRegistry.get().unhook(handle); } catch (Throwable ignored) { }
        }
    }

    public synchronized void install() {
        if (closed || installed) return;
        try {
            Method onResume = KavaReflector.findDeclaredMethod(Activity.class, "onResume");
            Method onPause = KavaReflector.findDeclaredMethod(Activity.class, "onPause");
            Method onDestroy = KavaReflector.findDeclaredMethod(Activity.class, "onDestroy");
            hook(onResume, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (closed) return;
                    if (param.thisObject instanceof Activity) {
                        currentActivity = new WeakReference<>((Activity) param.thisObject);
                    }
                }
            });
            XC_MethodHook clearHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (closed) return;
                    Activity current = currentActivity();
                    if (current != null && current == param.thisObject) {
                        currentActivity = new WeakReference<>(null);
                    }
                }
            };
            hook(onPause, clearHook);
            hook(onDestroy, clearHook);
            installed = true;
            log("当前Activity Hook已安装");
        } catch (Throwable e) {
            log("当前Activity Hook失败: " + e.getMessage());
        }
    }

    public Activity currentActivity() {
        if (closed) return null;
        WeakReference<Activity> ref = currentActivity;
        return ref != null ? ref.get() : null;
    }

    public synchronized void updateCurrentActivity(Activity activity) {
        if (!closed && activity != null && !activity.isFinishing()) {
            currentActivity = new WeakReference<>(activity);
        }
    }

    public String currentActivityName() {
        Activity activity = currentActivity();
        return activity != null ? activity.getClass().getName() : "";
    }

    public boolean isCurrentActivity(String className) {
        String currentName = currentActivityName();
        return className != null && className.equals(currentName);
    }

    public boolean isCurrentActivityContains(String classNamePart) {
        String currentName = currentActivityName();
        return classNamePart != null && currentName.contains(classNamePart);
    }

    public boolean isInChatting() {
        return isCurrentActivityContains(".ui.chatting.");
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatCurrentActivityApi] " + message);
    }
}
