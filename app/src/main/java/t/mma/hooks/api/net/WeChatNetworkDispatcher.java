package t.mma.hooks.api.net;

import android.app.Activity;
import android.content.Context;

import t.mma.hooks.core.HookRegistry;
import t.mma.utils.KavaReflector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.WeakHashMap;
import android.os.SystemClock;
import t.mma.utils.HLog;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 微信网络发包器缓存与发包封装。
 * 负责 Hook 网络队列方法以缓存 dispatcher 实例，并提供统一 send()。
 */
public class WeChatNetworkDispatcher {
    public interface Logger {
        void log(String message);
    }

    private volatile boolean destroyed;
    private final java.util.List<XC_MethodHook.Unhook> ownedHooks = new java.util.ArrayList<>();
    private synchronized XC_MethodHook.Unhook hookOwned(java.lang.reflect.Member member, XC_MethodHook callback) {
        if (destroyed) throw new IllegalStateException("Dispatcher closed");
        XC_MethodHook.Unhook handle = HookRegistry.get().hook(member, callback);
        ownedHooks.add(handle);
        return handle;
    }
    public synchronized void destroy() {
        if (destroyed) return;
        destroyed = true;
        for (XC_MethodHook.Unhook handle : ownedHooks) {
            try { HookRegistry.get().unhook(handle); } catch (Throwable error) { de.robv.android.xposed.XposedBridge.log(error); }
        }
        ownedHooks.clear(); hookedQueueMethods.clear(); observedCompletionMethods.clear();
        moduleRequests.clear(); failureLogTimes.clear(); queueCandidateClasses = Collections.emptyList();
        dispatcherInstance = null; dispatcherMethod = null;
    }
    private final Logger logger;
    private volatile Object dispatcherInstance;
    private volatile Method dispatcherMethod;
    private int dispatcherArgCount = 1;
    private List<Class<?>> queueCandidateClasses = Collections.emptyList();
    private final Set<Method> hookedQueueMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Method> observedCompletionMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Object, Long> moduleRequests = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<String, Long> failureLogTimes = new ConcurrentHashMap<>();


    public WeChatNetworkDispatcher(Logger logger) {
        this.logger = logger;
    }

    public void hookNetworkQueue(Class<?> netQueueClass) {
        hookNetworkQueue(netQueueClass, Collections.emptyList());
    }

    public synchronized void hookNetworkQueue(Class<?> netQueueClass, List<Class<?>> candidates) {
        if (destroyed) return;
        LinkedHashSet<Class<?>> classes = new LinkedHashSet<>();
        if (netQueueClass != null) classes.add(netQueueClass);
        if (candidates != null) classes.addAll(candidates);
        queueCandidateClasses = new ArrayList<>(classes);
        if (classes.isEmpty()) {
            log("网络队列类为null，且没有候选类");
            return;
        }
        int hooked = 0;
        for (Class<?> queueClass : classes) {
            if (queueClass == null) continue;
            try {
                for (Method method : KavaReflector.declaredMethods(queueClass)) {
                    if (!isSendMethodCandidate(method, null) || !hookedQueueMethods.add(method)) continue;
                    try {
                        hookOwned(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                        if (destroyed) return;
                                if (dispatcherInstance == null
                                        || methodPriority(method) > methodPriority(dispatcherMethod)) {
                                    cacheDispatcher(param.thisObject, method, "网络发包器已缓存");
                                }
                            }
                        });
                        hooked++;
                    } catch (Throwable hookError) {
                        hookedQueueMethods.remove(method);
                        log("网络队列方法Hook失败: " + method.toGenericString() + " " + hookError.getMessage());
                    }
                }
            } catch (Throwable e) {
                log("网络队列类扫描失败: " + queueClass.getName() + " " + e.getMessage());
            }
        }
        if (hooked == 0 && hookedQueueMethods.isEmpty()) {
            log("网络队列候选类无合适方法可Hook: " + classes.size());
        } else if (hooked > 0) {
            log("网络队列补装Hook: classes=" + classes.size() + " methods=" + hooked);
        }
    }

    public boolean send(Object request) {
        if (destroyed) return false;
        if (request == null) return false;
        Object realRequest = unwrapRequest(request);
        if (realRequest == null) return false;

        if (dispatcherInstance == null || dispatcherMethod == null) {
            ensureReady(realRequest.getClass());
        } else if (methodPriority(dispatcherMethod) < 100) {
            tryCacheFromObject(dispatcherInstance, realRequest.getClass());
        }

        if (dispatcherInstance != null && dispatcherMethod != null) {
            try {
                moduleRequests.put(realRequest, SystemClock.elapsedRealtime());
                Object result;
                if (dispatcherArgCount == 2) {
                    result = KavaReflector.invokeOrThrow(dispatcherMethod, dispatcherInstance, realRequest, 0);
                } else {
                    result = KavaReflector.invokeOrThrow(dispatcherMethod, dispatcherInstance, realRequest);
                }
                boolean accepted = normalizeSendResult(result);
                if (!accepted) moduleRequests.remove(realRequest);
                HLog.i("[Hchat:NetworkTrace] moduleSubmit scene=" + realRequest.getClass().getName()
                        + " accepted=" + accepted + " queue=" + dispatcherMethod.toGenericString());
                return accepted;
            } catch (InvocationTargetException invokeErr) {
                // Original code may already have submitted work before throwing. Never resubmit.
                moduleRequests.remove(realRequest);
                HLog.e("[Hchat:NetworkTrace] 模块请求原生入队异常 scene="
                        + realRequest.getClass().getName(),
                        invokeErr.getCause() != null ? invokeErr.getCause() : invokeErr);
                return false;
            } catch (Throwable invokeErr) {
                moduleRequests.remove(realRequest);
                HLog.e("[Hchat:NetworkTrace] 模块请求反射入队失败 scene=" + realRequest.getClass().getName(), invokeErr);
                return false;
            }
        } else {
            log("sendNetworkRequest 失败: dispatcher=" + (dispatcherInstance != null)
                    + " method=" + (dispatcherMethod != null));
        }
        return false;
    }

    /** Read-only: never intercept native completion or inspect message/response contents. */
    private void observeQueueCompletion(Class<?> queueClass) {
        for (Method method : KavaReflector.declaredMethods(queueClass)) {
            Class<?>[] types = method.getParameterTypes();
            if (!"onSceneEnd".equals(method.getName()) || method.getReturnType() != void.class
                    || Modifier.isAbstract(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                    || types.length != 4 || types[0] != int.class || types[1] != int.class
                    || types[2] != String.class || !isNetworkRequestType(types[3])
                    || !observedCompletionMethods.add(method)) continue;
            try {
                hookOwned(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (destroyed) return;
                        // Candidate classes include business listeners. Only the exact cached queue applies.
                        if (param.thisObject != dispatcherInstance || param.args == null || param.args.length != 4) return;
                        try {
                            int errType = ((Number) param.args[0]).intValue();
                            int errCode = ((Number) param.args[1]).intValue();
                            Object scene = param.args[3];
                            Long submitted = scene != null ? moduleRequests.remove(scene) : null;
                            if (errType == 0 && errCode == 0 && !param.hasThrowable()) return;
                            long now = SystemClock.elapsedRealtime();
                            String key = errType + ":" + errCode;
                            Long last = failureLogTimes.put(key, now);
                            if (last != null && now - last < 2000L && submitted == null) return;
                            if (failureLogTimes.size() > 64) failureLogTimes.clear();
                            Object type = scene != null ? KavaReflector.invokeMethod(scene, "getType") : null;
                            HLog.w("[Hchat:NetworkTrace] queueFailure errType=" + errType + " errCode=" + errCode
                                    + " cgi=" + type + " scene=" + (scene != null ? scene.getClass().getName() : "null")
                                    + " moduleSubmitted=" + (submitted != null)
                                    + " elapsedMs=" + (submitted != null ? now - submitted : -1)
                                    + " thread=" + Thread.currentThread().getName());
                            if (param.hasThrowable()) HLog.e("[Hchat:NetworkTrace] 网络队列原始完成回调异常", param.getThrowable());
                        } catch (Throwable error) {
                            HLog.e("[Hchat:NetworkTrace] 只读网络诊断失败", error);
                        }
                    }
                });
                HLog.i("[Hchat:NetworkTrace] completionObserver=" + method.toGenericString());
            } catch (Throwable error) {
                observedCompletionMethods.remove(method);
                HLog.e("[Hchat:NetworkTrace] 网络完成诊断安装失败: " + method.toGenericString(), error);
            }
        }
    }

    private boolean normalizeSendResult(Object result) {
        if (result instanceof Boolean) return (Boolean) result;
        if (result instanceof Number) return ((Number) result).intValue() >= 0;
        return true;
    }

    public boolean isReady() {
        return dispatcherInstance != null && dispatcherMethod != null;
    }

    public boolean hasDispatcherInstance() {
        return dispatcherInstance != null;
    }

    public boolean hasDispatcherMethod() {
        return dispatcherMethod != null;
    }

    private boolean ensureReady(Class<?> requestClass) {
        if (requestClass == null) return false;
        if (dispatcherInstance != null && dispatcherMethod != null) return true;
        return tryCacheFromClassStatics(queueCandidateClasses, requestClass);
    }

    private boolean isSendMethodCandidate(Method method, Class<?> requestClass) {
        try {
            if (method == null) return false;
            String name = method.getName();
            if ("equals".equals(name) || "hashCode".equals(name) || "toString".equals(name)
                    || "wait".equals(name) || "notify".equals(name) || "notifyAll".equals(name)) {
                return false;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params == null || (params.length != 1 && params.length != 2)) return false;
            if (params.length == 2 && params[1] != int.class && params[1] != Integer.class) return false;
            Class<?> first = params[0];
            if (first == null || first.isPrimitive() || first == String.class || first == Object.class) return false;
            if (requestClass != null && !first.isAssignableFrom(requestClass)) return false;
            if (requestClass == null && !isNetworkRequestType(first)) return false;
            Class<?> rt = method.getReturnType();
            return rt == boolean.class || rt == Boolean.class || rt == int.class || rt == void.class;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isNetworkRequestType(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isInterface()) return false;
        Method getType = KavaReflector.findMethodRecursive(type, "getType");
        return getType != null
                && getType.getParameterTypes().length == 0
                && getType.getReturnType() == int.class;
    }

    private Object unwrapRequest(Object request) {
        if (request instanceof Object[]) {
            Object[] array = (Object[]) request;
            if (array.length == 1) return array[0];
        }
        return request;
    }

    private boolean tryCacheFromObject(Object candidate, Class<?> requestClass) {
        if (candidate == null || requestClass == null) return false;
        try {
            Method best = null;
            for (Method method : KavaReflector.declaredMethods(candidate.getClass())) {
                if (isSendMethodCandidate(method, requestClass)) {
                    if (best == null || methodPriority(method) > methodPriority(best)) {
                        best = method;
                    }
                }
            }
            if (best != null) {
                cacheDispatcher(candidate, best, "网络发包器方法匹配");
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            for (Field field : KavaReflector.declaredFields(candidate.getClass())) {
                Object value = KavaReflector.readField(field, candidate);
                if (value == null || value == candidate) continue;
                try {
                    Method best = null;
                    for (Method method : KavaReflector.declaredMethods(value.getClass())) {
                        if (isSendMethodCandidate(method, requestClass)) {
                            if (best == null || methodPriority(method) > methodPriority(best)) {
                                best = method;
                            }
                        }
                    }
                    if (best != null) {
                        cacheDispatcher(value, best, "网络发包器方法匹配");
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private int methodPriority(Method method) {
        if (method == null) return 0;
        int score = 0;
        String name = method.getName();
        if ("g".equals(name) || "f".equals(name)) score += 80;
        if ("h".equals(name)) score += 70;
        if ("doScene".equals(name) || "doSceneImp".equals(name)) score += 80;
        if ("d".equals(name) || "cancel".equals(name)) score -= 100;
        Class<?> rt = method.getReturnType();
        if (rt == boolean.class || rt == Boolean.class) score += 100;
        if (rt == int.class || rt == Integer.class) score += 20;
        if (rt == void.class) score -= 50;
        if (method.getParameterTypes().length == 2) score += 10;
        return score;
    }

    private boolean tryCacheFromObjectDeep(Object candidate, Class<?> requestClass,
                                           int depth, Set<Object> visited) {
        if (candidate == null || requestClass == null) return false;
        try {
            if (visited != null) {
                if (visited.contains(candidate)) return false;
                visited.add(candidate);
            }
        } catch (Throwable ignored) {}
        if (tryCacheFromObject(candidate, requestClass)) return true;
        if (depth <= 0) return false;

        try {
            Class<?> cur = candidate.getClass();
            while (cur != null && cur != Object.class) {
                for (Field field : KavaReflector.declaredFields(cur)) {
                    try {
                        Object value = KavaReflector.readField(field, candidate);
                        if (shouldSkipDeepScan(value)) continue;
                        if (tryCacheFromObjectDeep(value, requestClass, depth - 1, visited)) return true;
                    } catch (Throwable ignored) {}
                }
                cur = cur.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean tryCacheFromClassStatics(List<Class<?>> classes, Class<?> requestClass) {
        if (classes == null || requestClass == null) return false;
        for (Class<?> clazz : classes) {
            if (tryCacheFromClassStatics(clazz, requestClass)) return true;
        }
        return false;
    }

    private boolean tryCacheFromClassStatics(Class<?> clazz, Class<?> requestClass) {
        if (clazz == null || requestClass == null) return false;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            Class<?> cur = clazz;
            while (cur != null && cur != Object.class) {
                for (Field field : KavaReflector.declaredFields(cur)) {
                    try {
                        if (!KavaReflector.isStatic(field)) continue;
                        Object value = KavaReflector.readField(field, (Object) null);
                        if (shouldSkipDeepScan(value)) continue;
                        if (tryCacheFromObjectDeep(value, requestClass, 3, visited)) {
                            log("网络发包器主动缓存成功: " + clazz.getName());
                            return true;
                        }
                    } catch (Throwable ignored) {}
                }
                cur = cur.getSuperclass();
            }
        } catch (Throwable ignored) {}

        try {
            for (Method method : KavaReflector.declaredMethods(clazz)) {
                try {
                    if (!KavaReflector.isStatic(method)) continue;
                    if (method.getParameterTypes().length != 0) continue;
                    Class<?> rt = method.getReturnType();
                    if (rt == null || rt == void.class || rt.isPrimitive() || rt == String.class) continue;
                    Object value = KavaReflector.invoke(method, null);
                    if (shouldSkipDeepScan(value)) continue;
                    if (tryCacheFromObjectDeep(value, requestClass, 3, visited)) {
                        log("网络发包器主动缓存成功: " + clazz.getName() + "#" + method.getName());
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean shouldSkipDeepScan(Object value) {
        if (value == null) return true;
        try {
            Class<?> clazz = value.getClass();
            if (clazz.isPrimitive() || clazz.isEnum() || clazz.isArray()) return true;
            if (value instanceof String || value instanceof Number || value instanceof Boolean
                    || value instanceof Character || value instanceof Context || value instanceof Activity
                    || value instanceof Class || value instanceof Method || value instanceof Field) {
                return true;
            }
            String name = clazz.getName();
            return name.startsWith("java.")
                    || name.startsWith("android.view.")
                    || name.startsWith("android.widget.")
                    || name.startsWith("android.graphics.");
        } catch (Throwable ignored) {
            return true;
        }
    }

    private synchronized void cacheDispatcher(Object instance, Method method, String reason) {
        if (destroyed) return;
        if (instance == null || method == null) return;
        dispatcherInstance = instance;
        dispatcherMethod = method;
        dispatcherArgCount = method.getParameterTypes().length;
        // 网络完成回调只用于诊断，不参与发包；启动阶段不安装观察 Hook，避免影响微信原生链路。
        log(reason + ": " + instance.getClass().getName() + "#" + method.getName());
    }

    private void log(String message) {
        if (logger != null) logger.log(message);
    }
}
