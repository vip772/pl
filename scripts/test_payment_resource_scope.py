"""Execute the real PaymentResourceScope with a deterministic Handler/Hook boundary.

Phase A compiles the production PaymentResourceScope.java against synthetic android.os /
Xposed / HookRegistry fixtures and runs the real scheduling, claim, close and release paths.
Phase B compiles the real AutoRedPacketFeature.kt + RedPacketRuntime with the real
PreferenceRuntimeOwner / FeatureResourceScope / FeatureOwnership / DexInstallScheduler and a
stub host hooker, then drives the toggle, restart, retry, destroy and reopen paths.

Neither phase is an Android/R8 build and neither touches a real WeChat session.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
STD = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
PAYMENT = ROOT / 'app/src/main/java/t/mma/hooks/items/payment/core'
CORE = ROOT / 'app/src/main/java/t/mma/hooks/core'

JAVA_FIXTURES = {
    'android/os/Looper.java': '''package android.os;
public class Looper {
    public static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return MAIN; }
}''',
    'android/os/Handler.java': '''package android.os;
import java.util.ArrayList;
import java.util.List;
public class Handler {
    public static final List<Handler> ALL = new ArrayList<>();
    public static volatile boolean reject;
    public static volatile boolean throwOnPost;
    public final List<Runnable> queue = new ArrayList<>();
    public Handler(Looper looper) { ALL.add(this); }
    public boolean post(Runnable task) { return enqueue(task); }
    public boolean postDelayed(Runnable task, long delayMs) { return enqueue(task); }
    public void removeCallbacks(Runnable task) { queue.remove(task); }
    public void removeCallbacksAndMessages(Object token) { queue.clear(); }
    private boolean enqueue(Runnable task) {
        if (throwOnPost) throw new IllegalStateException("主线程队列已关闭");
        if (reject) return false;
        queue.add(task);
        return true;
    }
    public static void drain() {
        for (Handler handler : new ArrayList<>(ALL)) {
            for (Runnable task : new ArrayList<>(handler.queue)) {
                handler.queue.remove(task);
                task.run();
            }
        }
    }
    public static void reset() {
        ALL.clear();
        reject = false;
        throwOnPost = false;
    }
}''',
    'de/robv/android/xposed/XC_MethodHook.java': '''package de.robv.android.xposed;
public class XC_MethodHook {
    public static class Unhook { public final Object method; public Unhook(Object m) { method = m; } }
}''',
    'de/robv/android/xposed/XposedBridge.java': '''package de.robv.android.xposed;
import java.util.ArrayList;
import java.util.List;
public class XposedBridge {
    public static final List<String> LOGS = new ArrayList<>();
    public static void log(String message) { LOGS.add(message); }
    public static void log(Throwable error) { LOGS.add(String.valueOf(error)); }
}''',
    't/mma/hooks/core/HookRegistry.java': '''package t.mma.hooks.core;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Member;
public class HookRegistry {
    private static final HookRegistry INSTANCE = new HookRegistry();
    public static int installed;
    public static int removed;
    public static volatile boolean rejectHook;
    public static volatile boolean failUnhook;
    public static HookRegistry get() { return INSTANCE; }
    public XC_MethodHook.Unhook hook(Member method, XC_MethodHook callback) {
        if (rejectHook) throw new IllegalStateException("宿主拒绝安装 Hook");
        installed += 1;
        return new XC_MethodHook.Unhook(method);
    }
    public void unhook(XC_MethodHook.Unhook handle) {
        removed += 1;
        if (failUnhook) throw new IllegalStateException("宿主卸载 Hook 失败");
    }
    public static void reset() { installed = 0; removed = 0; rejectHook = false; failUnhook = false; }
}''',
    'Test.java': '''import t.mma.hooks.items.payment.core.PaymentResourceScope;
import android.os.Handler;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import t.mma.hooks.core.HookRegistry;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

public class Test {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static Object field(Object target, String name) throws Exception {
        Field declared = target.getClass().getDeclaredField(name);
        declared.setAccessible(true);
        return declared.get(target);
    }
    static Runnable lastPosted() {
        Handler handler = Handler.ALL.get(Handler.ALL.size() - 1);
        return handler.queue.get(handler.queue.size() - 1);
    }

    public static void main(String[] args) throws Exception {
        // 1. Same-key replacement: the first posting is cancelled, only the replacement runs.
        Handler.reset();
        HookRegistry.reset();
        AtomicInteger count = new AtomicInteger();
        PaymentResourceScope scope = new PaymentResourceScope();
        check(scope.post("one", 1, () -> count.addAndGet(1)), "首次投递应被接受");
        check(scope.post("one", 1, () -> count.addAndGet(2)), "替换投递应被接受");
        check(scope.pendingCount() == 1, "同键只应保留一个待执行任务");
        Handler.drain();
        check(count.get() == 2, "同键应只执行最后一次，实际=" + count.get());

        // 2. Explicit cancel drops the posting and the capture.
        check(scope.post("cancel", 1, () -> count.addAndGet(100)), "取消前投递应被接受");
        Runnable cancelled = lastPosted();
        scope.cancel("cancel");
        check(field(cancelled, "action") == null, "取消后应释放任务捕获");
        cancelled.run();
        Handler.drain();
        check(count.get() == 2, "取消后不得执行，实际=" + count.get());
        check(scope.pendingCount() == 0, "取消后待执行计数应为 0");

        // 3. A refused posting must not stay in the pending map (the old code leaked the entry).
        Handler.reject = true;
        check(!scope.post("refused", 0, () -> count.incrementAndGet()), "被拒绝的投递应返回 false");
        check(scope.pendingCount() == 0, "被拒绝的投递不得留在待执行表");
        Handler.reject = false;
        Handler.throwOnPost = true;
        check(!scope.post("thrown", 0, () -> count.incrementAndGet()), "投递异常应返回 false");
        check(scope.pendingCount() == 0, "投递异常后不得留在待执行表");
        Handler.throwOnPost = false;
        Handler.drain();
        check(count.get() == 2, "被拒绝与异常的投递都不得执行，实际=" + count.get());

        // 4. Pending budget is hard: 128 accepted, the 129th refused, no map growth.
        PaymentResourceScope bounded = new PaymentResourceScope();
        int accepted = 0;
        for (int i = 0; i < 129; i++) if (bounded.post("key-" + i, 1000, () -> { })) accepted += 1;
        check(accepted == 128, "待执行上界应为 128，实际接受=" + accepted);
        check(bounded.pendingCount() == 128, "上界内计数应为 128，实际=" + bounded.pendingCount());
        bounded.close();

        // 5. Hook ownership: same Member installed once, released once, closed scope refuses.
        scope.hook(Test.class.getMethod("main", String[].class), new XC_MethodHook());
        scope.hook(Test.class.getMethod("main", String[].class), new XC_MethodHook());
        check(HookRegistry.installed == 1, "同一方法只应安装一次 Hook，实际=" + HookRegistry.installed);

        // 6. Reverse-order, exception-isolated, idempotent release.
        StringBuilder order = new StringBuilder();
        scope.register(() -> order.append("a"));
        scope.register(() -> { throw new RuntimeException("释放异常"); });
        scope.register(() -> order.append("b"));
        scope.close();
        scope.close();
        check(order.toString().equals("ba"), "释放应逆序且继续执行，实际=" + order);
        check(HookRegistry.removed == 1, "Hook 应只卸载一次，实际=" + HookRegistry.removed);
        check(XposedBridge.LOGS.stream().anyMatch(m -> m.contains("释放异常")), "释放异常应被上报");
        boolean refusedHook = false;
        try { scope.hook(Test.class.getMethod("main", String[].class), new XC_MethodHook()); }
        catch (IllegalStateException expected) { refusedHook = true; }
        check(refusedHook, "关闭后的 scope 必须拒绝安装 Hook");

        // 7. Register and post after close release/refuse immediately instead of queueing forever.
        scope.register(() -> order.append("c"));
        check(order.toString().equals("bac"), "关闭后登记的释放应就地执行，实际=" + order);
        check(!scope.post("late", 0, () -> count.incrementAndGet()), "关闭后投递应被拒绝");

        // 8. close() drops queued postings and their captures; a stale runnable is a no-op.
        PaymentResourceScope closing = new PaymentResourceScope();
        AtomicInteger afterClose = new AtomicInteger();
        check(closing.post("pending", 500, afterClose::incrementAndGet), "关闭前投递应被接受");
        Runnable pending = lastPosted();
        closing.close();
        check(field(pending, "action") == null, "关闭后应释放待执行任务捕获");
        check(closing.pendingCount() == 0, "关闭后待执行表应清空");
        pending.run();
        Handler.drain();
        check(afterClose.get() == 0, "关闭后遗留任务不得执行，实际=" + afterClose.get());

        // 9. An action that throws does not escape or kill the scope.
        PaymentResourceScope throwing = new PaymentResourceScope();
        throwing.post("boom", 0, () -> { throw new IllegalStateException("业务异常"); });
        Handler.drain();
        check(XposedBridge.LOGS.stream().anyMatch(m -> m.contains("业务异常")), "业务异常应被上报");
        throwing.close();

        System.out.println("PASS payment scope: same-key replacement, cancel capture drop, refused/throwing posting rollback, "
                + "128 budget, exact hook ownership, reverse isolated idempotent close, late refusal, dropped captures");
    }
}'''
}

KOTLIN_STUBS = {}

KOTLIN_STUBS['Platform.kt'] = '''package android.os
class Looper {
    companion object {
        val MAIN = Looper()
        @Volatile var mainThread: Thread? = null
        fun getMainLooper(): Looper = MAIN
        fun myLooper(): Looper? = if (Thread.currentThread() === mainThread) MAIN else null
    }
}
object MainQueue {
    private val tasks = ArrayList<Runnable>()
    @Volatile var reject = false
    @Volatile var throwOnPost = false
    fun flush(limit: Int = 256): Int {
        var ran = 0
        while (true) {
            val next = synchronized(this) {
                if (tasks.isEmpty() || ran >= limit) null else tasks.removeAt(0)
            } ?: return ran
            next.run()
            ran += 1
        }
    }
    fun pending(): Int = synchronized(this) { tasks.size }
    fun clear() { synchronized(this) { tasks.clear() } }
    internal fun add(task: Runnable): Boolean = synchronized(this) {
        if (throwOnPost) throw IllegalStateException("主线程队列已关闭")
        if (reject) return false
        tasks.add(task)
        true
    }
    internal fun remove(task: Runnable) { synchronized(this) { tasks.removeAll { it === task } } }
    internal fun removeAll() { synchronized(this) { tasks.clear() } }
}
class Handler(val looper: Looper = Looper.getMainLooper()) {
    fun post(task: Runnable): Boolean = MainQueue.add(task)
    fun postDelayed(task: Runnable, delayMs: Long): Boolean = MainQueue.add(task)
    fun removeCallbacks(task: Runnable) { MainQueue.remove(task) }
    fun removeCallbacksAndMessages(token: Any?) { MainQueue.removeAll() }
}
'''

KOTLIN_STUBS['Content.kt'] = '''package android.content
class Context {
    var packageName: String = "com.tencent.mm"
}
interface SharedPreferences {
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener)
    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?)
    }
}
'''

KOTLIN_STUBS['Xposed.kt'] = '''package de.robv.android.xposed
open class XC_MethodHook {
    class Unhook(val method: java.lang.reflect.Member)
    class MethodHookParam(val args: Array<Any?>)
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}
}
object XposedBridge {
    @JvmStatic fun log(message: String) { t.mma.hooks.core.Harness.log(message) }
    @JvmStatic fun log(error: Throwable) { t.mma.hooks.core.Harness.log(error.toString()) }
}
'''

KOTLIN_STUBS['LogStubs.kt'] = '''package t.mma.utils
object HLog {
    fun d(message: String) { t.mma.hooks.core.Harness.log("D:" + message) }
    fun i(message: String) { t.mma.hooks.core.Harness.log("I:" + message) }
    fun w(message: String) { t.mma.hooks.core.Harness.log("W:" + message) }
    fun e(message: String) { t.mma.hooks.core.Harness.log("E:" + message) }
    fun e(message: String, throwable: Throwable?) { t.mma.hooks.core.Harness.log("E:" + message) }
}
'''

KOTLIN_STUBS['Events.kt'] = '''package t.mma.event
object Events {
    class RedPacketGrabbed
    class DexReady
}
'''

KOTLIN_STUBS['DexFinder.kt'] = '''package t.mma.dexkit
class DexFinder
'''

KOTLIN_STUBS['CoreStubs.kt'] = '''package t.mma.hooks.core
import android.content.SharedPreferences
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Member

/**
 * Cross-thread observation point. The scheduler installs on its own worker thread while the test
 * drains the synthetic main queue, so every read is a snapshot taken under one lock.
 */
object Harness {
    private val lock = Any()
    private val logEntries = ArrayList<String>()
    private val errorEntries = ArrayList<String>()
    private val eventEntries = ArrayList<String>()
    private val hookerList = ArrayList<Any>()
    private val handlers = LinkedHashMap<Class<*>, MutableList<(Any?) -> Unit>>()
    private var instances = 0

    @Volatile var prefs: SharedPreferences? = null
    /** Number of upcoming hookAll() calls that must fail; deterministic bounded-retry injection. */
    @Volatile var failHookAllTimes = 0

    fun log(message: String) { synchronized(lock) { logEntries += message } }
    fun error(message: String) { synchronized(lock) { errorEntries += message } }
    fun event(message: String) { synchronized(lock) { eventEntries += message } }
    fun addHooker(target: Any) { synchronized(lock) { hookerList += target } }
    fun logs(): List<String> = synchronized(lock) { logEntries.toList() }
    fun errors(): List<String> = synchronized(lock) { errorEntries.toList() }
    fun events(): List<String> = synchronized(lock) { eventEntries.toList() }
    fun hookers(): List<Any> = synchronized(lock) { hookerList.toList() }
    fun clearEvents() { synchronized(lock) { eventEntries.clear() } }
    fun eventCount(prefix: String): Int = synchronized(lock) { eventEntries.count { it.startsWith(prefix) } }
    fun nextInstance(): Int = synchronized(lock) { instances += 1; instances }
    fun consumeFailure(): Boolean = synchronized(lock) {
        if (failHookAllTimes > 0) { failHookAllTimes -= 1; true } else false
    }
    fun subscribe(type: Class<*>, handler: (Any?) -> Unit) {
        synchronized(lock) { handlers.getOrPut(type) { mutableListOf() }.add(handler) }
    }
    fun unsubscribe(type: Class<*>, handler: (Any?) -> Unit) {
        synchronized(lock) { handlers[type]?.remove(handler) }
    }
    fun fire(type: Class<*>) {
        val snapshot = synchronized(lock) { handlers[type]?.toList() ?: emptyList() }
        // Real subscribers declare a non-null event parameter, so the payload must be a real instance.
        val event = type.getDeclaredConstructor().newInstance()
        snapshot.forEach { it(event) }
    }
    fun handlerCount(type: Class<*>) = synchronized(lock) { handlers[type]?.size ?: 0 }
}

fun interface EventHandler<E> {
    fun handleEvent(event: E)
}

class FeatureContext(
    private val host: android.content.Context,
    private val loader: ClassLoader
) {
    fun hostContext(): android.content.Context = host
    fun hostClassLoader(): ClassLoader = loader
    fun dexFinder(): t.mma.dexkit.DexFinder = t.mma.dexkit.DexFinder()
}

open class BaseFeature {
    private val tracked = ArrayList<() -> Unit>()
    open fun featureId(): String = ""
    open fun name(): String = ""
    open fun onFeatureInit(context: FeatureContext) {}
    open fun onFeatureInstall(context: FeatureContext) {}
    fun registerSettingsProvider(provider: Any) { }
    fun <E> subscribe(type: Class<E>, handler: EventHandler<E>) {
        val wrapper: (Any?) -> Unit = { event -> handler.handleEvent(type.cast(event)) }
        Harness.subscribe(type, wrapper)
        trackResource { Harness.unsubscribe(type, wrapper) }
    }
    fun trackResource(release: () -> Unit) { synchronized(this) { tracked += release } }
    fun logError(message: String, throwable: Throwable?) {
        Harness.error(message + ": " + (throwable?.javaClass?.simpleName ?: "none"))
    }
    fun destroyAll() {
        val pending = synchronized(this) { tracked.asReversed().toList().also { tracked.clear() } }
        pending.forEach { release ->
            try { release() } catch (error: Throwable) { Harness.error("tracked: " + error.message) }
        }
    }
}

class HookRegistry private constructor() {
    private val entries = ArrayList<Pair<Member, XC_MethodHook>>()
    @Volatile var rejectHook = false
    @Volatile var failUnhook = false
    var unhookCalls = 0
    fun hook(method: Member, callback: XC_MethodHook): XC_MethodHook.Unhook {
        if (rejectHook) throw IllegalStateException("宿主拒绝安装 Hook")
        synchronized(this) { entries += method to callback }
        return XC_MethodHook.Unhook(method)
    }
    fun unhook(handle: XC_MethodHook.Unhook?) {
        unhookCalls += 1
        if (failUnhook) throw IllegalStateException("宿主卸载 Hook 失败")
        if (handle == null) return
        synchronized(this) { entries.removeAll { it.first === handle.method } }
    }
    fun size(): Int = synchronized(this) { entries.size }
    fun reset() {
        synchronized(this) { entries.clear() }
        rejectHook = false
        failUnhook = false
        unhookCalls = 0
    }
    companion object {
        private val INSTANCE = HookRegistry()
        fun get(): HookRegistry = INSTANCE
    }
}
'''

KOTLIN_STUBS['PaymentStubs.kt'] = '''package t.mma.hooks.items.payment.core
import android.content.SharedPreferences

/** Host settings boundary: only the keys the feature entry reads. */
class RedPacketSettings @JvmOverloads constructor(
    private val classLoader: ClassLoader,
    private val hostContext: android.content.Context? = null
) {
    fun getHostPreferences(): SharedPreferences? = t.mma.hooks.core.Harness.prefs
    companion object {
        const val PREFS_NAME = "Hchat_redpacket_config"
        const val KEY_ENABLE = "hb_auto_enable"
        const val KEY_FAKE_PACKET_ENABLE = "hb_fake_packet_enable"
        const val KEY_BLOCK_NEW_GROUP_ENABLE = "hb_block_new_group_enable"
    }
}

class RedPacketSettingsProvider

/**
 * Host hooker boundary. The production class is exercised by the Android build only; here it
 * records installation, destruction and failure ordering so the runtime contract is observable.
 */
class RedPacketHooker(
    private val context: android.content.Context,
    private val loader: ClassLoader,
    private val dexFinder: t.mma.dexkit.DexFinder
) {
    private val id = t.mma.hooks.core.Harness.nextInstance()
    @Volatile var hookAllCalls = 0
    @Volatile var dexReadyQueries = 0
    @Volatile var destroyed = false
    @Volatile var failDestroy = false
    val instanceId: Int get() = id

    init { t.mma.hooks.core.Harness.addHooker(this) }

    fun isDestroyed(): Boolean = destroyed

    fun hookAll(): List<Any> {
        if (destroyed) return emptyList()
        if (t.mma.hooks.core.Harness.consumeFailure()) {
            throw IllegalStateException("红包 Hook 安装失败#" + id)
        }
        hookAllCalls += 1
        t.mma.hooks.core.Harness.event("hookAll#" + id)
        return emptyList()
    }

    fun isDexReadyEnough(): Boolean {
        dexReadyQueries += 1
        return true
    }

    fun destroy() {
        destroyed = true
        t.mma.hooks.core.Harness.event("destroy#" + id)
        if (failDestroy) throw IllegalStateException("红包运行时释放失败#" + id)
    }
}
'''

KOTLIN_TEST = '''package t.mma.hooks.items.payment.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.os.MainQueue
import t.mma.event.Events
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.Harness

class StubPrefs : SharedPreferences {
    private val values = HashMap<String, Any>()
    private val listeners = ArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        if (listeners.none { it === listener }) listeners += listener
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= listener
    }
    fun set(key: String, value: Boolean) {
        values[key] = value
        listeners.toList().forEach { it.onSharedPreferenceChanged(this, key) }
    }
    fun listenerCount(): Int = listeners.size
}

internal fun installs(): Int = Harness.eventCount("hookAll#")

internal fun destroys(): Int = Harness.eventCount("destroy#")

fun await(message: String, timeoutMs: Long = 8000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        MainQueue.flush()
        if (condition()) return
        Thread.sleep(2)
    }
    error("等待超时: " + message + " | events=" + Harness.events() + " | errors=" + Harness.errors()
            + " | logs=" + Harness.logs())
}

internal fun startFeature(store: StubPrefs, feature: AutoRedPacketFeature) {
    Harness.prefs = store
    val context = FeatureContext(Context(), AutoRedPacketFeature::class.java.classLoader!!)
    feature.onFeatureInit(context)
    feature.onFeatureInstall(context)
}

/** WARMUP gate: nothing installs before the Dex bridge reports warm-up readiness. */
internal fun runtimeGate() {
    Looper.mainThread = Thread.currentThread()
    MainQueue.clear()
    Harness.clearEvents()
    val store = StubPrefs()
    store.set(RedPacketSettings.KEY_ENABLE, true)
    val feature = AutoRedPacketFeature()
    startFeature(store, feature)
    check(Harness.events().none { it.startsWith("hookAll#") }) {
        "DexKit 未就绪前不得安装 Hook: " + Harness.events()
    }
    await("DexKit 未就绪不安装", 200) { Harness.events().isEmpty() }
    check(store.listenerCount() == 1) { "应只注册一个偏好监听，实际=" + store.listenerCount() }
    feature.destroyAll()
    check(Harness.handlerCount(Events.DexReady::class.java) == 0) { "销毁后应退订 DexReady" }
    check(store.listenerCount() == 0) { "销毁后应退订偏好监听，实际=" + store.listenerCount() }
    check(destroys() == 1) { "销毁应释放本代运行时: " + Harness.events() }
    println("PASS runtime gate: WARMUP defers install, one preference listener, destroy releases the generation")
}

internal fun runtimeLifecycle() {
    Looper.mainThread = Thread.currentThread()
    MainQueue.clear()
    Harness.clearEvents()
    val hookersBase = Harness.hookers().size
    val store = StubPrefs()
    val feature = AutoRedPacketFeature()
    startFeature(store, feature)
    check(installs() == 0 && destroys() == 0) { "默认关闭不得创建运行时: " + Harness.events() }

    // One enabled weight owns exactly one runtime and installs once.
    store.set(RedPacketSettings.KEY_ENABLE, true)
    await("启用后安装红包 Hook") { installs() == 1 }
    val first = Harness.events().single { it.startsWith("hookAll#") }.substringAfter('#').toInt()
    check(destroys() == 0) { "启用过程不应销毁代际: " + Harness.events() }
    check(Harness.errors().isEmpty()) { "启用过程不应报错: " + Harness.errors() }
    check(Harness.hookers().size == hookersBase + 1) { "启用只应创建一代运行时" }

    // DexReady after a successful install must neither duplicate the install nor rebuild.
    Harness.fire(Events.DexReady::class.java)
    MainQueue.flush()
    await("DexReady 后不重复安装", 300) { installs() == 1 && destroys() == 0 }

    // Disabling the only weight destroys exactly that generation.
    store.set(RedPacketSettings.KEY_ENABLE, false)
    await("关闭后销毁运行时") { destroys() == 1 }
    check(Harness.events().last() == "destroy#" + first) { "应销毁刚安装的那一代: " + Harness.events() }
    check(Harness.hookers().size == hookersBase + 1) { "关闭不得重建实例" }

    // A second weight alone must also own a runtime (the three keys are OR-ed).
    store.set(RedPacketSettings.KEY_FAKE_PACKET_ENABLE, true)
    await("伪造红包开关单独启用") { installs() == 2 }

    // restartOnChange: another weight change restarts, destroy precedes the new install.
    store.set(RedPacketSettings.KEY_BLOCK_NEW_GROUP_ENABLE, true)
    await("权重变化触发重启") { destroys() == 2 && installs() == 3 }
    val restartOrder = Harness.events()
    check(restartOrder.indexOfLast { it.startsWith("destroy#") } < restartOrder.indexOfLast { it.startsWith("hookAll#") }) {
        "重启应先销毁旧代再安装新代: " + restartOrder
    }

    // Turning one weight off while the feature stays enabled restarts again, no generation stacking.
    store.set(RedPacketSettings.KEY_FAKE_PACKET_ENABLE, false)
    await("部分关闭触发重启") { destroys() == 3 && installs() == 4 }

    // All weights off: full release, and DexReady must not resurrect the generation.
    store.set(RedPacketSettings.KEY_BLOCK_NEW_GROUP_ENABLE, false)
    await("全部关闭后销毁") { destroys() == 4 }
    val hookersAfterClose = Harness.hookers().size
    Harness.fire(Events.DexReady::class.java)
    MainQueue.flush()
    await("关闭后 DexReady 不安装", 300) { installs() == 4 }
    check(Harness.hookers().size == hookersAfterClose) { "关闭后 DexReady 不得重建运行时" }

    // Reopen after close: the scheduler install key must be free again.
    store.set(RedPacketSettings.KEY_ENABLE, true)
    await("重开后重新安装") { installs() == 5 }
    check(Harness.errors().none { it.contains("another owner") }) { "安装键归属必须已释放: " + Harness.errors() }

    // Install failure: the ticket's bounded retry converges on a single successful install.
    store.set(RedPacketSettings.KEY_ENABLE, false)
    await("关闭准备失败注入") { destroys() == 5 }
    val hookersBeforeRetry = Harness.hookers().size
    val installsBeforeRetry = installs()
    Harness.failHookAllTimes = 2
    store.set(RedPacketSettings.KEY_ENABLE, true)
    await("失败注入后重试最终安装成功", 12000) { installs() == installsBeforeRetry + 1 }
    check(Harness.errors().any { it.contains("红包 Hook 安装失败") }) {
        "安装失败应被上报: " + Harness.errors()
    }
    check(Harness.hookers().size == hookersBeforeRetry + 1) {
        "重试不得重复创建实例，新增=" + (Harness.hookers().size - hookersBeforeRetry)
    }
    val recovered = Harness.hookers().last() as RedPacketHooker
    check(recovered.hookAllCalls == 1) { "重试收敛后只应留下一次安装，实际=" + recovered.hookAllCalls }

    // Destroy path: a throwing hooker must be reported, never escape, and never leak the reference.
    recovered.failDestroy = true
    val errorsBeforeDestroy = Harness.errors().size
    feature.destroyAll()
    check(Harness.errors().size > errorsBeforeDestroy) { "释放异常应被上报" }
    check(Harness.errors().any { it.contains("释放红包运行时失败") }) {
        "释放异常应记录到功能错误: " + Harness.errors()
    }
    val destroysBeforeRepeat = destroys()
    feature.destroyAll()
    check(destroys() == destroysBeforeRepeat) { "重复销毁不得再次释放: " + Harness.events() }

    // After destroy the feature is inert: no listener, no DexReady subscription, no installs.
    check(store.listenerCount() == 0) { "销毁后应退订偏好监听，实际=" + store.listenerCount() }
    check(Harness.handlerCount(Events.DexReady::class.java) == 0) { "销毁后应退订 DexReady" }
    val installsAfterDestroy = installs()
    store.set(RedPacketSettings.KEY_ENABLE, false)
    store.set(RedPacketSettings.KEY_ENABLE, true)
    Harness.fire(Events.DexReady::class.java)
    MainQueue.flush()
    await("销毁后不再响应开关", 300) { installs() == installsAfterDestroy }

    println("PASS runtime: default-off, weight-OR enable, DexReady idempotent, restart ordering, "
            + "partial-weight restart, full-close then DexReady no-revive, reopen key ownership, "
            + "bounded retry to single install, throwing destroy isolation, post-destroy inert")
}

fun main() {
    runtimeGate()
    // Unlock the production scheduler's WARMUP stage for the remaining lifecycle checks.
    t.mma.hooks.core.DexInstallScheduler.markDexWarmupReady()
    runtimeLifecycle()
    println("PASS auto-redpacket production lifecycle (synthetic platform boundaries only)")
}
'''


def run_phase_a() -> None:
    with tempfile.TemporaryDirectory(prefix='pl-payment-scope-') as td:
        path = Path(td)
        for name, text in JAVA_FIXTURES.items():
            target = path / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
        sources = [PAYMENT / 'PaymentResourceScope.java']
        sources += [path / name for name in JAVA_FIXTURES if name.endswith('.java')]
        subprocess.run(
            ['javac', '-Xlint:-options', '-d', str(path / 'classes'), *map(str, sources)],
            check=True, timeout=120
        )
        subprocess.run(['java', '-cp', str(path / 'classes'), 'Test'], check=True, timeout=60)


def run_phase_b() -> None:
    sources = [
        PAYMENT / 'AutoRedPacketFeature.kt',
        CORE / 'FeatureOwnership.kt',
        CORE / 'FeatureResourceScope.kt',
        CORE / 'PreferenceRuntimeOwner.kt',
        CORE / 'DexInstallScheduler.kt',
    ]
    with tempfile.TemporaryDirectory(prefix='pl-redpacket-') as td:
        path = Path(td)
        for name, text in KOTLIN_STUBS.items():
            (path / name).write_text(text)
        (path / 'Test.kt').write_text(KOTLIN_TEST)
        compile_cmd = ['java', '-Xmx384m', '-cp', ':'.join(map(str, LIBS.glob('*.jar'))),
                       'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn',
                       '-no-stdlib', '-no-reflect', '-jvm-target', '17',
                       '-classpath', str(STD), '-d', str(path / 'out')]
        compile_cmd += [str(p) for p in sorted(path.glob('*.kt'))]
        compile_cmd += [str(p) for p in sources]
        subprocess.run(compile_cmd, check=True, timeout=300)
        subprocess.run(['java', '-cp', str(path / 'out') + ':' + str(STD),
                        't.mma.hooks.items.payment.core.TestKt'], check=True, timeout=180)


if __name__ == '__main__':
    run_phase_a()
    run_phase_b()