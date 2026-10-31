package t.mma;
import t.mma.utils.HLog;

import android.app.Application;
import android.content.Context;

import t.mma.crash.CrashReportRuntime;
import t.mma.dexkit.DexFinder;
import t.mma.hooks.api.media.WeChatImageApi;
import t.mma.hooks.core.FeatureRegistry;
import t.mma.hooks.core.FeatureContext;
import t.mma.hooks.core.DexInstallScheduler;
import t.mma.hooks.core.FeatureManager;
import t.mma.hooks.core.HookRegistry;
import t.mma.hooks.items.fakelocation.FakeLocationFeature;
import t.mma.hooks.items.custombottombar.CustomBottomBarFeature;
import t.mma.hooks.items.hotupdate.DisableHotUpdateFeature;
import t.mma.hooks.items.miniprogrambaselib.FakeMiniProgramBaseLibFeature;
import t.mma.hooks.items.miniprogramsplashad.SkipGlobalMiniProgramSplashAdsFeature;
import t.mma.hooks.items.miniprogramvideoad.SkipMiniProgramVideoAdsFeature;
import t.mma.hooks.items.floatingshortcut.FloatingShortcutRuntime;
import t.mma.hooks.items.script.ScriptPluginRuntime;
import t.mma.hooks.items.script.agent.ScriptPluginAgentLocalReverseTools;
import t.mma.hooks.items.tablet.WeChatTabletFeature;
import t.mma.loader.utils.NativeLibraryLoader;
import t.mma.preferences.ConfigStore;
import t.mma.preferences.TermsGate;
import t.mma.dexkit.DexBridgeHolder;
import t.mma.dexkit.DexKitBridgeManager;
import t.mma.event.EventBus;
import t.mma.ui.UIRegistry;

import org.luckypray.dexkit.DexKitBridge;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 模块入口。
 *
 * 启动流程：
 * 1. handleLoadPackage → 过滤目标包 + 目标进程
 * 2. 小程序子进程安装必要的缓存 Hook 和显式声明的小程序脚本插件
 * 3. 主进程在 Tinker attach 后优先安装自定义底栏 Hook
 * 4. Application.onCreate 后异步初始化完整模块
 *
 * 进程过滤：普通功能只在微信主进程加载；:appbrand* 使用独立的轻量运行时。
 */
public class ModuleEntry implements IXposedHookLoadPackage {
    private static final String TAG = "[Hchat:Entry]";
    private static final String WECHAT_PKG = "com.tencent.mm";
    private static final Object DEXKIT_CREATE_LOCK = new Object();
    private static final Map<String, DexKitBridge> DEXKIT_BRIDGES = new ConcurrentHashMap<>();
    private static final DexKitBridgeManager DEXKIT_MANAGER = new DexKitBridgeManager();

    // 全局基础设施实例（模块生命周期内单例）
    private final EventBus eventBus = EventBus.get();
    private final UIRegistry uiRegistry = UIRegistry.get();

    private FeatureManager featureManager;
    private FeatureContext featureContext;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 1. 包名过滤
        if (!isWeChatPackage(lpparam.packageName)) return;

        // 热更新加载发生在 Tinker attach 阶段，必须早于普通功能初始化安装。
        installHotUpdateEarlyHook(lpparam);

        // 平板模式需要在微信所有进程保持一致，避免升级/降级后登录态校验看到不同设备形态。
        installTabletEarlyHook(lpparam);

        installStartupSyncRecoveryHook(lpparam);

        // 2. 子进程只安装明确需要的最小 Hook。
        if (!isMainProcess(lpparam)) {
            if (isAppBrandProcess(lpparam)) {
                installAppBrandProcessHook(lpparam);
            }
            return;
        }

        // 3. Mars CDN 管理器可能早于模块 API 初始化创建，先按稳定类名捕获实例。
        WeChatImageApi.installMarsCdnManagerHook(
                lpparam.classLoader,
                message -> XposedBridge.log("[Hchat:WechatApi] " + message));

        // 底栏在 LauncherUI 首帧前创建，必须早于完整 DexKit 初始化安装 Hook。
        installCustomBottomBarEarlyHook(lpparam);

        // 4. Hook Application.onCreate，等 Application 完全初始化后再启动模块
        HookRegistry.get().add(XposedHelpers.findAndHookMethod(
                Application.class,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Application app = (Application) param.thisObject;
                        if (TermsGate.INSTANCE.isAccepted(app)) {
                            CrashReportRuntime.prepare(app);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Application app = (Application) param.thisObject;
                        if (TermsGate.INSTANCE.isAccepted(app)) {
                            CrashReportRuntime.install(app, ModuleEntry.this.getClass().getClassLoader());
                            // 悬浮入口不依赖 DexKit，先注册生命周期，避免错过首个 Activity。
                            try {
                                FloatingShortcutRuntime.INSTANCE.install(app);
                            } catch (Throwable e) {
                                HLog.e(TAG + " 悬浮快捷菜单早期安装失败", e);
                            }
                        }
                        installCustomBottomBarEarly(app, resolveHostClassLoader(app, lpparam));
                        new Thread(() -> initModule(app, lpparam), "Hchat-Init").start();
                    }
                }
        ));
    }

    /** Bounded first-sync recovery; never initialise full features in push. */
    private void installStartupSyncRecoveryHook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.tencent.mm:push".equals(lpparam.processName)) return;
        HookRegistry.get().add(XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Application app = (Application) param.thisObject;
                    if (!TermsGate.INSTANCE.isAccepted(app)) return;
                    String version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
                    if (!"8.0.78".equals(version)) return;
                    ClassLoader loader = resolveHostClassLoader(app, lpparam);
                    t.mma.utils.StartupSyncRetryGuard.install(loader, version, lpparam.processName);
                } catch (Throwable error) {
                    HLog.e(TAG + " 启动同步恢复保护安装失败", error);
                }
            }
        }));
    }

    // ============ 包名与进程判断 ============

    /**
     * 判断是否为目标微信包。
     * 支持 com.tencent.mm 以及以 com.tencent.mm 开头的分身包名。
     */
    private boolean isWeChatPackage(String packageName) {
        return packageName != null && packageName.startsWith(WECHAT_PKG);
    }

    /**
     * 判断是否为主进程。
     * 微信主进程的 processName 等于 packageName。
     */
    private boolean isMainProcess(XC_LoadPackage.LoadPackageParam lpparam) {
        String process = lpparam.processName;
        return process == null || process.equals(lpparam.packageName);
    }

    private boolean isAppBrandProcess(XC_LoadPackage.LoadPackageParam lpparam) {
        String process = lpparam.processName;
        String packageName = lpparam.packageName;
        return process != null && packageName != null && process.startsWith(packageName + ":appbrand");
    }

    private void installAppBrandProcessHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            HookRegistry.get().add(XposedHelpers.findAndHookMethod(
                    "com.tencent.tinker.loader.app.TinkerApplication",
                    lpparam.classLoader,
                    "onBaseContextAttached",
                    Context.class,
                    long.class,
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Context context = (Context) param.args[0];
                            ClassLoader loader = resolveTinkerClassLoader(param.thisObject);
                            if (loader == null) {
                                loader = resolveHostClassLoader(context, lpparam);
                            }
                            installAppBrandProcessHook(context, loader, lpparam, false);
                        }
                    }
            ));
        } catch (Throwable e) {
            HLog.e(TAG + " 小程序进程早期入口安装失败: " + e.getMessage(), e);
        }
        try {
            HookRegistry.get().add(XposedHelpers.findAndHookMethod(
                    Application.class,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Application app = (Application) param.thisObject;
                            ClassLoader loader = resolveHostClassLoader(app, lpparam);
                            installAppBrandProcessHook(app, loader, lpparam, true);
                        }
                    }
            ));
        } catch (Throwable e) {
            HLog.e(TAG + " 小程序进程兜底入口安装失败: " + e.getMessage(), e);
        }
    }

    private void installAppBrandProcessHook(Context context,
                                            ClassLoader classLoader,
                                            XC_LoadPackage.LoadPackageParam lpparam,
                                            boolean applicationReady) {
        if (!TermsGate.INSTANCE.isAccepted(context)) return;
        boolean failed = false;
        if (FakeMiniProgramBaseLibFeature.isEnabled(context)
                && !FakeMiniProgramBaseLibFeature.installAppBrandProcessHook(context, classLoader)) {
            failed = true;
        }
        if (SkipMiniProgramVideoAdsFeature.isEnabled(context)
                && !SkipMiniProgramVideoAdsFeature.install(context, classLoader)) {
            failed = true;
        }
        if (SkipGlobalMiniProgramSplashAdsFeature.isEnabled(context)) {
            SkipGlobalMiniProgramSplashAdsFeature.scheduleAppBrandProcessHook(context, classLoader);
        }
        if (FakeLocationFeature.isEnabled(context)
                && !FakeLocationFeature.installAppBrandProcessHook(context, classLoader)) {
            failed = true;
        }
        if (applicationReady) {
            ScriptPluginRuntime.installAppBrandProcess(
                    context,
                    classLoader,
                    lpparam.processName
            );
        }
        if (failed && applicationReady) {
            HLog.e(TAG + " 小程序进程Hook安装失败: " + lpparam.processName);
        }
    }

    // ============ 模块初始化 ============

    private void installHotUpdateEarlyHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            HookRegistry.get().add(XposedHelpers.findAndHookMethod(
                    "com.tencent.tinker.loader.app.TinkerApplication",
                    lpparam.classLoader,
                    "onBaseContextAttached",
                    Context.class,
                    long.class,
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            installHotUpdateForAttach(lpparam, param, "before");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            installHotUpdateForAttach(lpparam, param, "after");
                        }
                    }
            ));
        } catch (Throwable e) {
            HLog.e(TAG + " 热更新早期入口安装失败: " + e.getMessage(), e);
        }
    }

    private void installHotUpdateForAttach(XC_LoadPackage.LoadPackageParam lpparam,
                                           XC_MethodHook.MethodHookParam param,
                                           String stage) {
        try {
            Context context = (Context) param.args[0];
            if (!TermsGate.INSTANCE.isAccepted(context)) return;
            if (!DisableHotUpdateFeature.isEnabled(context)) return;
            if ("before".equals(stage)) {
                DisableHotUpdateFeature.installEarly(context, lpparam.classLoader);
                return;
            }
            new Thread(() -> {
                DexInstallScheduler.runDexKitTask(() -> {
                    try {
                        ClassLoader hostClassLoader = resolveTinkerClassLoader(param.thisObject);
                        if (hostClassLoader == null) {
                            hostClassLoader = resolveHostClassLoader(context, lpparam);
                        }
                        NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
                        nativeLibraryLoader.loadDexKit(context, ModuleEntry.this.getClass().getClassLoader());
                        DexKitBridge dexKit = createDexKitBridge(hostClassLoader, lpparam.appInfo.sourceDir);
                        DisableHotUpdateFeature.install(context, hostClassLoader, dexKit);
                    } catch (Throwable e) {
                        HLog.e(TAG + " 热更新后置安装失败: " + e.getMessage(), e);
                    }
                });
            }, "Hchat-HotUpdateHook").start();
        } catch (SecurityException e) {
            throw e;
        } catch (Throwable e) {
            HLog.e(TAG + " 热更新早期Hook失败(" + stage + "): " + e.getMessage(), e);
        }
    }

    private void installTabletEarlyHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            HookRegistry.get().add(XposedHelpers.findAndHookMethod(
                    "com.tencent.tinker.loader.app.TinkerApplication",
                    lpparam.classLoader,
                    "onBaseContextAttached",
                    Context.class,
                    long.class,
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            installTabletHookForAttach(lpparam, param, "before");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            installTabletHookForAttach(lpparam, param, "after");
                        }
                    }
            ));
        } catch (Throwable e) {
            HLog.e(TAG + " 平板模式早期入口安装失败: " + e.getMessage(), e);
        }
    }

    private void installCustomBottomBarEarlyHook(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            HookRegistry.get().add(XposedHelpers.findAndHookMethod(
                    "com.tencent.tinker.loader.app.TinkerApplication",
                    lpparam.classLoader,
                    "onBaseContextAttached",
                    Context.class,
                    long.class,
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Context context = (Context) param.args[0];
                            ClassLoader classLoader = resolveTinkerClassLoader(param.thisObject);
                            if (classLoader == null) {
                                classLoader = resolveHostClassLoader(context, lpparam);
                            }
                            installCustomBottomBarEarly(context, classLoader);
                        }
                    }
            ));
        } catch (Throwable e) {
            HLog.e(TAG + " 自定义底栏早期入口安装失败: " + e.getMessage(), e);
        }
    }

    private void installCustomBottomBarEarly(Context context, ClassLoader classLoader) {
        try {
            if (!TermsGate.INSTANCE.isAccepted(context)) return;
            CustomBottomBarFeature.installEarly(context, classLoader);
        } catch (Throwable e) {
            HLog.e(TAG + " 自定义底栏早期Hook失败: " + e.getMessage(), e);
        }
    }

    private void installTabletHookForAttach(XC_LoadPackage.LoadPackageParam lpparam,
                                            XC_MethodHook.MethodHookParam param,
                                            String stage) {
        try {
            Context context = (Context) param.args[0];
            if (!TermsGate.INSTANCE.isAccepted(context)) return;
            if (!WeChatTabletFeature.isEnabled(context)) return;
            ClassLoader resolvedClassLoader = resolveTinkerClassLoader(param.thisObject);
            if (resolvedClassLoader == null) {
                resolvedClassLoader = resolveHostClassLoader(context, lpparam);
            }
            final ClassLoader hostClassLoader = resolvedClassLoader;
            // 缓存安装不使用 DexKit，避免主线程等待后台预热持有的串行门。
            if (WeChatTabletFeature.installCached(context, hostClassLoader)) return;
            DexInstallScheduler.runDexKitTask(() -> {
                if (WeChatTabletFeature.installCached(context, hostClassLoader)) return;
                if (!"after".equals(stage)) {
                    if (!DisableHotUpdateFeature.isEnabled(context)) return;
                    NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
                    nativeLibraryLoader.loadDexKit(context, ModuleEntry.this.getClass().getClassLoader());
                    DexKitBridge dexKit = createDexKitBridge(lpparam.classLoader, lpparam.appInfo.sourceDir);
                    WeChatTabletFeature.install(context, lpparam.classLoader, dexKit);
                    return;
                }
                NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
                nativeLibraryLoader.loadDexKit(context, ModuleEntry.this.getClass().getClassLoader());
                DexKitBridge dexKit = createDexKitBridge(hostClassLoader, lpparam.appInfo.sourceDir);
                WeChatTabletFeature.install(context, hostClassLoader, dexKit);
            });
        } catch (SecurityException e) {
            throw e;
        } catch (Throwable e) {
            HLog.e(TAG + " 平板模式早期Hook失败(" + stage + "): " + e.getMessage(), e);
        }
    }

    private ClassLoader resolveTinkerClassLoader(Object tinkerApplication) {
        try {
            Object value = XposedHelpers.callMethod(tinkerApplication, "getClassLoader");
            return value instanceof ClassLoader ? (ClassLoader) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void initModule(Context hostContext, XC_LoadPackage.LoadPackageParam lpparam) {
        HLog.initialize(hostContext);
        try {
            t.mma.hooks.core.ResourceMemoryCallbacks.install(hostContext);
        } catch (Throwable error) {
            HLog.e(TAG + " 内存回调注册失败", error);
        }
        // Runtime logging remains available through HLog and RuntimeLogStore.
        try {
            // 1. 加载 Native 库（DexKit）
            NativeLibraryLoader nativeLibraryLoader = new NativeLibraryLoader();
            nativeLibraryLoader.loadDexKit(hostContext, getClass().getClassLoader());
            nativeLibraryLoader.loadSilkCodec(hostContext, getClass().getClassLoader());

            // 2. 创建模块自身 Context
            Context moduleContext = createModuleContext(hostContext);
            ConfigStore configStore = new ConfigStore(hostContext);

            DexInstallScheduler.runDexKitTask(() -> {
                // 3. 初始化 DexKit。优先使用 Tinker 之后的运行时 ClassLoader。
                String apkPath = lpparam.appInfo.sourceDir;
                ClassLoader hostClassLoader = resolveHostClassLoader(hostContext, lpparam);
                DexKitBridge dexKit = createDexKitBridge(hostClassLoader, apkPath);
                DexFinder finder = new DexFinder(dexKit, hostClassLoader, hostContext);
                WeChatImageApi.installMarsCdnManagerHook(
                        finder.marsCdnManagerClass,
                        message -> XposedBridge.log("[Hchat:WechatApi] " + message));

                // 4. 创建共享 DexKit 服务
                DEXKIT_MANAGER.publish(dexKit, hostClassLoader, apkPath);
                DexKitBridgeManager.Lease dexKitCompatibilityLease =
                        DEXKIT_MANAGER.acquire("host-runtime-compatibility", true);
                DexBridgeHolder dexBridgeHolder = new DexBridgeHolder(
                        dexKit, finder, hostClassLoader, apkPath, dexKitCompatibilityLease);
                ScriptPluginAgentLocalReverseTools.install(dexBridgeHolder, hostContext);

                // 5. 创建统一上下文
                featureContext = new FeatureContext(
                        hostContext,
                        moduleContext != null ? moduleContext : hostContext,
                        hostClassLoader,
                        lpparam,
                        dexKit,
                        finder,
                        eventBus,
                        configStore,
                        dexBridgeHolder,
                        uiRegistry,
                        DEXKIT_MANAGER
                );

                // 6. 注册并安装所有功能模块
                featureManager = FeatureRegistry.createDefaultManager();
                featureManager.installAll(featureContext);

                // 7. 防崩兜底：微信 8.0.76 消息描述文本方法对 null 调 isEmpty() 崩溃（收到特殊小程序电商卡片消息触发）
                installMsgDescTextFallback(finder);
            });

        } catch (Throwable e) {
            HLog.e(TAG + " 初始化失败: " + e, e);
        }
    }

    /**
     * 防崩兜底：微信 8.0.76 的 pt0.r.l(Context, boolean) 存在未初始化局部变量，
     * 收到特殊小程序电商卡片消息（type 44 + ecsInfo）时 String.isEmpty() 对 null 调用导致崩溃。
     * 这里 hook 该方法，手动调用原方法并捕获异常，崩溃时兜底返回空串。
     */
    private void installMsgDescTextFallback(DexFinder finder) {
        try {
            finder.resolveMsgDescTextApi();
            if (finder.msgDescTextMethods.isEmpty()) {
                XposedBridge.log("[Hchat:WechatApi] 消息描述防崩兜底未定位到方法");
                return;
            }
            int hooked = 0;
            for (Method m : finder.msgDescTextMethods) {
                if (m == null) continue;
                try {
                    HookRegistry.get().add(XposedHelpers.findAndHookMethod(
                            m.getDeclaringClass(), m.getName(), Context.class, boolean.class,
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    try {
                                        // param.method 是 Member，需转 Method 再手动调用原方法，捕获微信内部 NPE 兜底
                                        Method original = (Method) param.method;
                                        param.setResult(original.invoke(param.thisObject, param.args));
                                    } catch (Throwable t) {
                                        param.setResult("");
                                    }
                                }
                            }
                    ));
                    hooked++;
                } catch (Throwable ignored) {}
            }
            XposedBridge.log("[Hchat:WechatApi] 消息描述防崩兜底已安装: " + hooked + "/"
                    + finder.msgDescTextMethods.size() + " 个方法");
        } catch (Throwable e) {
            HLog.e(TAG + " 消息描述防崩兜底安装失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建模块自身的 Context。
     * 通过 createPackageContext 获取模块自身的 Context，
     * 以便访问模块资源。
     */
    private Context createModuleContext(Context hostContext) {
        try {
            return hostContext.createPackageContext(
                    "t.mma",
                    Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE
            );
        } catch (Throwable e) {
            return null;
        }
    }

    private ClassLoader resolveHostClassLoader(Context hostContext,
                                               XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader lpClassLoader = lpparam.classLoader;
        ClassLoader contextClassLoader = null;
        ClassLoader threadClassLoader = null;
        try {
            contextClassLoader = hostContext.getClassLoader();
        } catch (Throwable ignored) {}
        try {
            threadClassLoader = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ignored) {}

        if (isTinkerClassLoader(contextClassLoader)) return contextClassLoader;
        if (isTinkerClassLoader(threadClassLoader)) return threadClassLoader;
        if (contextClassLoader != null) return contextClassLoader;
        if (threadClassLoader != null) return threadClassLoader;
        return lpClassLoader;
    }

    private DexKitBridge createDexKitBridge(ClassLoader classLoader, String apkPath) {
        String key = apkPath != null ? apkPath : "";
        synchronized (DEXKIT_CREATE_LOCK) {
            DexKitBridge cached = DEXKIT_BRIDGES.get(key);
            if (cached != null) return cached;
            DexKitBridge bridge = DexKitBridge.create(apkPath);
            try {
                // The Java scheduler is serial, but each native query has its own workers.
                // Bound those workers before publishing the bridge or running any query.
                bridge.setThreadNum(2);
            } catch (Throwable error) {
                bridge.close();
                throw error;
            }
            DEXKIT_BRIDGES.put(key, bridge);
            HLog.i(TAG + " DexKit共享查询线程数=2");
            return bridge;
        }
    }
    private boolean isTinkerClassLoader(ClassLoader classLoader) {
        if (classLoader == null) return false;
        String text = String.valueOf(classLoader);
        return text.contains("/tinker/") || text.contains("DelegateLastClassLoader");
    }
}
