package t.mma.dexkit;
import t.mma.utils.HLog;
import t.mma.hooks.core.DexInstallScheduler;

import t.mma.dexkit.DexFinder;

import org.luckypray.dexkit.DexKitBridge;

/**
 * 共享 DexKit 实例持有者。
 *
 * 统一管理 DexKitBridge、ClassLoader、DexFinder 的生命周期。
 * 避免每个功能模块各自创建 DexKitBridge（浪费内存和 CPU）。
 * 
 * 由模块入口在初始化时创建，通过 FeatureContext 传递给所有功能模块。
 */
public final class DexBridgeHolder {

    private static final String TAG = "[Hchat:DexBridgeHolder]";

    private final DexKitBridge dexKitBridge;
    private final DexFinder dexFinder;
    private final ClassLoader hostClassLoader;
    private final String apkPath;
    private final DexKitBridgeManager.Lease compatibilityLease;

    public DexBridgeHolder(DexKitBridge dexKitBridge, DexFinder dexFinder,
                           ClassLoader hostClassLoader, String apkPath) {
        this(dexKitBridge, dexFinder, hostClassLoader, apkPath, null);
    }

    /**
     * 创建带兼容租约的共享持有者。旧脚本和未迁移的内部入口通过该租约保持
     * 宿主 bridge 保活；租约本身不改变原有 bridge/holder 接口。
     */
    public DexBridgeHolder(DexKitBridge dexKitBridge, DexFinder dexFinder,
                           ClassLoader hostClassLoader, String apkPath,
                           DexKitBridgeManager.Lease compatibilityLease) {
        this.dexKitBridge = dexKitBridge;
        this.dexFinder = dexFinder;
        this.hostClassLoader = hostClassLoader;
        this.apkPath = apkPath;
        this.compatibilityLease = compatibilityLease;
    }

    public long getGenerationId() {
        return compatibilityLease == null ? 0L : compatibilityLease.getGenerationId();
    }

    public boolean hasCompatibilityLease() {
        return compatibilityLease != null && !compatibilityLease.isClosed();
    }

    public DexKitBridge getDexKitBridge() {
        return dexKitBridge;
    }

    public DexFinder getDexFinder() {
        return dexFinder;
    }

    public ClassLoader getHostClassLoader() {
        return hostClassLoader;
    }

    public String getApkPath() {
        return apkPath;
    }

    /**
     * 通用字符串搜索：在 DexKit 中查找使用了指定字符串的方法所属类。
     * 各功能模块可以直接调用此方法做自己的 Dex 扫描，无需自己维护 DexKit 实例。
     * 
     * @param anchorStrings 用于定位的字符串锚点
     * @return 匹配到的类名列表
     */
    public java.util.List<String> findClassesByStrings(String... anchorStrings) {
        return DexInstallScheduler.queryDexKitTask(() -> findClassesByStringsLocked(anchorStrings));
    }
    private java.util.List<String> findClassesByStringsLocked(String... anchorStrings) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (dexKitBridge == null || anchorStrings == null) return result;
        try {
            org.luckypray.dexkit.query.FindClass fc = new org.luckypray.dexkit.query.FindClass();
            org.luckypray.dexkit.query.matchers.ClassMatcher cm = new org.luckypray.dexkit.query.matchers.ClassMatcher();
            cm.usingStrings(java.util.Arrays.asList(anchorStrings));
            fc.matcher(cm);
            java.util.List<org.luckypray.dexkit.result.ClassData> classes = dexKitBridge.findClass(fc);
            for (org.luckypray.dexkit.result.ClassData cd : classes) {
                result.add(cd.getName());
            }
        } catch (Throwable e) {
            HLog.e(TAG + " findClassesByStrings 失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 通用方法搜索：查找使用了指定字符串的方法。
     * @return 匹配到的方法信息列表（className + methodName）
     */
    public java.util.List<MethodInfo> findMethodsByStrings(String... anchorStrings) {
        return DexInstallScheduler.queryDexKitTask(() -> findMethodsByStringsLocked(anchorStrings));
    }
    private java.util.List<MethodInfo> findMethodsByStringsLocked(String... anchorStrings) {
        java.util.List<MethodInfo> result = new java.util.ArrayList<>();
        if (dexKitBridge == null || anchorStrings == null) return result;
        try {
            org.luckypray.dexkit.query.FindMethod fm = new org.luckypray.dexkit.query.FindMethod();
            org.luckypray.dexkit.query.matchers.MethodMatcher mm = new org.luckypray.dexkit.query.matchers.MethodMatcher();
            mm.usingStrings(java.util.Arrays.asList(anchorStrings));
            fm.matcher(mm);
            java.util.List<org.luckypray.dexkit.result.MethodData> methods = dexKitBridge.findMethod(fm);
            for (org.luckypray.dexkit.result.MethodData md : methods) {
                result.add(new MethodInfo(md.getClassName(), md.getName()));
            }
        } catch (Throwable e) {
            HLog.e(TAG + " findMethodsByStrings 失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 方法信息简单封装。
     */
    public static final class MethodInfo {
        public final String className;
        public final String methodName;

        public MethodInfo(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }

        @Override
        public String toString() {
            return className + "." + methodName;
        }
    }
}
