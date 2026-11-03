"""Execute the real HchatExtra resource-ownership code with deterministic host fixtures.

Phase A compiles the production HchatExtraRuntime class (extracted verbatim from
HchatExtraFeature.kt) together with the real DexInstallScheduler and drives the
enable-interval lifecycle: WARMUP gate, ticket ownership, bounded retry, orphan
rollback when the generation is closed during start, and close ordering.

Phase B compiles the real ForwardedRecordTypeLabels plus the real OwnedHooks /
OwnedHandler primitives and drives: refused hook install returning false so the
scheduler can retry, single registration on repeat install, real label binding
through the hooked callback, refused main-queue delivery, and destroy releasing
the handle exactly once while a late callback becomes a no-op.

Phase C compiles the real MessageDetailsPendingLayoutObserver and drives refused
post / postDelayed delivery: a refused delivery must not leave the posted or
polling latch stuck, otherwise the row never retries and never polls again.

Phase D pins the source invariants that cannot be executed off-device.

None of this is an Android/R8 build and none of it touches a real WeChat session.
"""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
STD = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
COMPILER = next(LIBS.glob('kotlin-compiler-embeddable-*.jar'))
CP = ':'.join(str(p) for p in sorted(LIBS.glob('*.jar')))
CORE = ROOT / 'app/src/main/java/t/mma/hooks/core'
HCHAT = ROOT / 'app/src/main/java/t/mma/hooks/items/hchatextra'


def write_group(tmp: Path, group: str, files: dict):
    target = tmp / group
    target.mkdir(parents=True, exist_ok=True)
    written = []
    for name, body in files.items():
        path = target / name
        path.write_text(body)
        written.append(path)
    return written


def compile_and_run(tmp: Path, name: str, sources, mains):
    jar = tmp / (name + '.jar')
    args = ['java', '-Xmx512m', '-cp', CP, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-nowarn', '-no-stdlib', '-no-reflect', '-classpath', str(STD), '-d', str(jar)]
    args += [str(s) for s in sources]
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode != 0:
        sys.stdout.write(result.stdout)
        sys.stderr.write(result.stderr)
        raise SystemExit('kompile failed: ' + name)
    for main in mains:
        run = subprocess.run(['java', '-cp', str(jar) + ':' + str(STD), main],
                             capture_output=True, text=True, timeout=180)
        sys.stdout.write(run.stdout)
        if run.returncode != 0:
            sys.stderr.write(run.stderr)
            raise SystemExit('runtime failed: ' + main + ' exit=' + str(run.returncode))


# --------------------------------------------------------------------------------------
# Shared Kotlin boundary: android.os queue + Xposed + HookRegistry
# --------------------------------------------------------------------------------------
OS_STUB = '''package android.os

object MainQueue {
    private val tasks = ArrayList<Runnable>()
    @Volatile var reject = false
    @Volatile var rejectDelayed = false
    @Volatile var throwOnPost = false
    fun flush(limit: Int = 512): Int {
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
    fun reset() { clear(); reject = false; rejectDelayed = false; throwOnPost = false }
    fun add(task: Runnable, delayed: Boolean): Boolean = synchronized(this) {
        if (throwOnPost) throw IllegalStateException("main queue closed")
        // reject 只拦即时投递，rejectDelayed 只拦延迟投递：两者必须能单独注入。
        if (delayed) { if (rejectDelayed) return false } else { if (reject) return false }
        tasks.add(task)
        true
    }
    fun remove(task: Runnable) { synchronized(this) { tasks.removeAll { it === task } } }
}

class Looper {
    companion object {
        val MAIN = Looper()
        @Volatile var mainThread: Thread? = null
        fun getMainLooper(): Looper = MAIN
        fun myLooper(): Looper? = if (Thread.currentThread() === mainThread) MAIN else null
    }
}

open class Handler(private val looper: Looper = Looper.getMainLooper()) {
    open fun post(task: Runnable): Boolean = MainQueue.add(task, false)
    open fun postDelayed(task: Runnable, delayMs: Long): Boolean = MainQueue.add(task, true)
    open fun removeCallbacks(task: Runnable) { MainQueue.remove(task) }
    open fun removeCallbacksAndMessages(token: Any?) { MainQueue.clear() }
}

object SystemClock {
    fun uptimeMillis(): Long = java.lang.System.nanoTime() / 1_000_000L
}
'''

XD_STUB = '''package de.robv.android.xposed

import java.lang.reflect.Member

open class XC_MethodHook {
    class Unhook(val method: Member) {
        fun unhook() { t.mma.hooks.core.HookRegistry.get().unhook(this) }
    }
    class MethodHookParam {
        var args: Array<Any?> = emptyArray()
        var result: Any? = null
        var thisObject: Any? = null
        var throwable: Throwable? = null
        fun hasThrowable(): Boolean = throwable != null
    }
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}
}

object XposedBridge {
    @JvmStatic fun log(message: String) { t.mma.hooks.core.Probe.log(message) }
    @JvmStatic fun log(error: Throwable) { t.mma.hooks.core.Probe.log(error.toString()) }
}
'''

PROBE_STUB = '''package t.mma.hooks.core

object Probe {
    private val lock = Any()
    private val entries = ArrayList<String>()
    @Volatile var clazz: Class<*>? = null
    fun log(message: String) { synchronized(lock) { entries += message } }
    fun entries(): List<String> = synchronized(lock) { entries.toList() }
    fun clear() { synchronized(lock) { entries.clear() } }
    fun has(fragment: String): Boolean = synchronized(lock) { entries.any { it.contains(fragment) } }
    @Suppress("UNCHECKED_CAST")
    fun <T : Throwable> thrower(): (String, T?) -> Unit = { message, error ->
        synchronized(lock) { entries += message + ":" + (error?.javaClass?.simpleName ?: "none") }
    }
}
'''

REGISTRY_STUB = '''package t.mma.hooks.core

import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Member

class HookRegistry private constructor() {
    private val entries = ArrayList<Pair<Member, XC_MethodHook>>()
    @Volatile var rejectHook = false
    @Volatile var failUnhook = false
    var unhookCalls = 0

    fun hook(method: Member, callback: XC_MethodHook): XC_MethodHook.Unhook {
        if (rejectHook) throw IllegalStateException("host refused hook install")
        entries += method to callback
        return XC_MethodHook.Unhook(method)
    }
    fun unhook(handle: XC_MethodHook.Unhook?) {
        unhookCalls += 1
        if (failUnhook) throw IllegalStateException("host refused unhook")
        if (handle == null) return
        entries.removeAll { it.first === handle.method }
    }
    fun size(): Int = synchronized(this) { entries.size }
    fun callbacks(): List<XC_MethodHook> = synchronized(this) { entries.map { it.second } }
    fun reset() { synchronized(this) { entries.clear() }; rejectHook = false; failUnhook = false; unhookCalls = 0 }
    companion object {
        private val INSTANCE = HookRegistry()
        fun get(): HookRegistry = INSTANCE
    }
}
'''

HLOG_STUB = '''package t.mma.utils

object HLog {
    fun d(message: String) { t.mma.hooks.core.Probe.log("D:" + message) }
    fun i(message: String) { t.mma.hooks.core.Probe.log("I:" + message) }
    fun w(message: String) { t.mma.hooks.core.Probe.log("W:" + message) }
    fun e(message: String) { t.mma.hooks.core.Probe.log("E:" + message) }
    fun e(message: String, throwable: Throwable?) { t.mma.hooks.core.Probe.log("E:" + message) }
}
'''

# --------------------------------------------------------------------------------------
# Phase A: real HchatExtraRuntime + real DexInstallScheduler
# --------------------------------------------------------------------------------------
PHASE_A_CONTEXT = '''package t.mma.hooks.core

class FeatureContext(private val loader: ClassLoader) {
    fun hostClassLoader(): ClassLoader = loader
}
'''

PHASE_A_HOOKER = '''package t.mma.hooks.items.hchatextra

import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.Probe

class HchatExtraFeature {
    companion object {
        const val ID = "hchat_extra"
    }
}

/** Host hooker boundary: the production class needs a live WeChat process. */
class HchatExtraHooker(context: FeatureContext, private val report: (String, Throwable?) -> Unit) {
    @Volatile var destroyed = false
    var destroyCalls = 0
    var mainCalls = 0
    var detailsCalls = 0
    var forwardedCalls = 0
    var callsAfterDestroy = 0
    @Volatile var failMain = false

    init {
        Probe.log("hooker:created")
        onConstruct?.let { it() }
        onConstruct = null
    }

    fun isDestroyed(): Boolean = destroyed

    fun install(): Boolean {
        if (destroyed) { callsAfterDestroy += 1; return false }
        mainCalls += 1
        Probe.log("install:main")
        return !failMain && !refuseInstall
    }

    fun installMessageDetails(): Boolean {
        if (destroyed) { callsAfterDestroy += 1; return false }
        detailsCalls += 1
        Probe.log("install:details")
        return true
    }

    fun installForwardedRecordTypes(): Boolean {
        if (destroyed) { callsAfterDestroy += 1; return false }
        forwardedCalls += 1
        Probe.log("install:forwarded")
        return true
    }

    fun destroy() {
        destroyCalls += 1
        destroyed = true
        Probe.log("hooker:destroy")
    }

    companion object {
        /** 注入点：构造期间关闭本代，验证 start() 的孤儿回滚。 */
        @Volatile var onConstruct: (() -> Unit)? = null
        /** 注入点：让安装真实失败，验证票据重试与关闭停重试。 */
        @Volatile var refuseInstall = false
    }
}
'''

PHASE_A_TEST = '''package t.mma.hooks.items.hchatextra

import android.os.Looper
import android.os.MainQueue
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.Probe

private val loader: ClassLoader = PHASE_A_TEST_LOADER
private val visits = ArrayList<String>()

private fun runtime(): HchatExtraRuntime {
    val created = HchatExtraRuntime(FeatureContext(loader)) { message, error ->
        visits += "report:" + message + ":" + (error?.javaClass?.simpleName ?: "none")
    }
    return created
}

/** Drain the synthetic main queue for a fixed window; used for "nothing must happen" checks. */
private fun settle(millis: Long) {
    val deadline = System.currentTimeMillis() + millis
    while (System.currentTimeMillis() < deadline) {
        MainQueue.flush()
        Thread.sleep(2)
    }
}

private fun await(message: String, timeoutMs: Long = 8000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        MainQueue.flush()
        if (condition()) return
        Thread.sleep(2)
    }
    error("等待超时: " + message + " | probe=" + Probe.entries() + " | visits=" + visits)
}

private fun check(value: Boolean, message: String) {
    if (!value) throw AssertionError(message + " | probe=" + Probe.entries() + " | visits=" + visits)
}

private fun count(entry: String): Int = Probe.entries().count { it == entry }

fun main() {
    MainQueue.reset()
    Probe.clear()

    // 1. WARMUP 门：start() 只登记票据，DexKit 就绪前一次安装都不许发生。
    val gated = runtime()
    gated.start()
    settle(300)
    check(count("hooker:created") == 1) { "启动必须创建本代 Hooker" }
    check(count("install:main") == 0) { "WARMUP 前不得安装主 Hook" }
    check(count("install:details") == 0) { "WARMUP 前不得安装消息详情" }
    check(count("install:forwarded") == 0) { "WARMUP 前不得安装聊天记录类型" }
    gated.close()
    settle(150)
    check(count("hooker:destroy") == 1) { "关闭必须销毁本代 Hooker" }

    // 2. 就绪后三张票据各装一次；重复调度必须被票据去重。
    DexInstallScheduler.markDexWarmupReady()
    val steady = runtime()
    steady.start()
    await("三项安装全部完成") {
        count("install:main") == 1 && count("install:details") == 1 && count("install:forwarded") == 1
    }
    repeat(5) { steady.onDexReady() }
    settle(300)
    check(count("install:main") == 1) { "重复调度不得重复安装主 Hook，实际=" + count("install:main") }
    check(count("install:details") == 1) { "重复调度不得重复安装消息详情，实际=" + count("install:details") }
    check(count("install:forwarded") == 1) { "重复调度不得重复安装聊天记录类型，实际=" + count("install:forwarded") }
    steady.close()
    settle(150)
    check(count("hooker:destroy") == 2) { "正常代关闭必须销毁，实际=" + count("hooker:destroy") }

    // 3. 安装失败进入票据重试；关闭后重试必须停掉，不得再有安装尝试。
    HchatExtraHooker.refuseInstall = true
    val failing = runtime()
    val attempts0 = count("install:main")
    failing.start()
    await("首次安装尝试") { count("install:main") > attempts0 }
    failing.close()
    settle(600)
    val stopped = count("install:main")
    settle(600)
    check(count("install:main") == stopped) {
        "关闭后不得再重试安装，实际=" + stopped + "->" + count("install:main")
    }
    check(count("hooker:destroy") == 3) { "失败代关闭必须销毁，实际=" + count("hooker:destroy") }
    HchatExtraHooker.refuseInstall = false

    // 4. close() 幂等：重复关闭只销毁一次。
    val idempotent = runtime()
    val attempts1 = count("install:main")
    val destroys1 = count("hooker:destroy")
    idempotent.start()
    await("幂等用例安装完成") { count("install:main") > attempts1 }
    idempotent.close()
    idempotent.close()
    settle(150)
    check(count("hooker:destroy") == destroys1 + 1) {
        "重复关闭只许销毁一次，实际增量=" + (count("hooker:destroy") - destroys1)
    }

    // 5. 启动过程中被关闭：刚创建的一代必须就地回滚，不能留成孤儿 Hook。
    val orphan = runtime()
    val attempts2 = count("install:main")
    val destroys2 = count("hooker:destroy")
    HchatExtraHooker.onConstruct = { orphan.close() }
    orphan.start()
    HchatExtraHooker.onConstruct = null
    settle(400)
    check(count("hooker:destroy") == destroys2 + 1) {
        "孤儿代必须就地销毁，实际增量=" + (count("hooker:destroy") - destroys2)
    }
    check(count("install:main") == attempts2) {
        "孤儿代不得安装任何 Hook，实际增量=" + (count("install:main") - attempts2)
    }

    // 6. 重开新代：上一代被取消的票据键必须能被新一代重新登记并安装。
    val reopened = runtime()
    val attempts3 = count("install:main")
    val destroys3 = count("hooker:destroy")
    reopened.start()
    await("重开新代安装完成") { count("install:main") > attempts3 }
    reopened.close()
    settle(150)
    check(count("hooker:destroy") == destroys3 + 1) {
        "重开代必须正常销毁，实际增量=" + (count("hooker:destroy") - destroys3)
    }
    check(visits.none { it.startsWith("report:") }) { "正常路径不得上报失败: " + visits }

    println("PASS hchat extra runtime: WARMUP gate, ticket dedup, cancelled retry on close, "
        + "idempotent close, orphan rollback during start, clean reopen")
}
'''


def phase_a(tmp: Path):
    source = (HCHAT / 'HchatExtraFeature.kt').read_text()
    start = source.index('internal class HchatExtraRuntime(')
    end = source.index('private class HchatExtraHooker(')
    runtime = source[start:end].rstrip()
    if not runtime.endswith('}'):
        raise SystemExit('runtime slice is not a closed class')
    wrapper = ('package t.mma.hooks.items.hchatextra\n\n'
               + 'import t.mma.hooks.core.DexInstallScheduler\n'
               + 'import t.mma.hooks.core.FeatureContext\n\n'
               + runtime + '\n')
    test = PHASE_A_TEST.replace('PHASE_A_TEST_LOADER', 'Thread.currentThread().contextClassLoader!!')
    files = {
        'Os.kt': OS_STUB,
        'Xposed.kt': XD_STUB,
        'Probe.kt': PROBE_STUB,
        'Registry.kt': REGISTRY_STUB,
        'HLog.kt': HLOG_STUB,
        'Context.kt': PHASE_A_CONTEXT,
        'Hooker.kt': PHASE_A_HOOKER,
        'Runtime.kt': wrapper,
        'PhaseA.kt': test,
    }
    written = write_group(tmp, 'a', files)
    written.append(CORE / 'DexInstallScheduler.kt')
    compile_and_run(tmp, 'phase-a', written, ['t.mma.hooks.items.hchatextra.PhaseAKt'])


# --------------------------------------------------------------------------------------
# Phase B: real ForwardedRecordTypeLabels + real FeatureOwnership
# --------------------------------------------------------------------------------------
ANDROID_WIDGET_STUB = '''package android.content
open class Context

open class ContextWrapper(base: Context) : Context() {
    open val baseContext: Context = base
}


interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun edit(): Editor
    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun remove(key: String): Editor
        fun apply()
    }
}
'''

ACTIVITY_STUB = '''package android.app

import android.content.Context

open class Activity : Context()
'''

VIEW_STUB = '''package android.view

import android.content.Context

open class View(val context: Context) {
    var parent: ViewGroup? = null
    var visibility: Int = VISIBLE
    var layoutParams: ViewGroup.LayoutParams? = null
    var tag: Any? = null
    var isAttachedToWindow: Boolean = true

    fun post(task: Runnable): Boolean = android.os.MainQueue.add(task, false)
    fun postDelayed(task: Runnable, delayMs: Long): Boolean = android.os.MainQueue.add(task, true)
    fun removeCallbacks(task: Runnable) { android.os.MainQueue.remove(task) }
    fun invalidate() {}

    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
    }
}

open class ViewGroup(context: Context) : View(context) {
    private val children = ArrayList<View>()
    val childCount: Int get() = children.size
    fun getChildAt(index: Int): View = children[index]
    fun addView(child: View) = addView(child, children.size, child.layoutParams)
    fun addView(child: View, index: Int, params: LayoutParams?) {
        child.parent = this
        child.layoutParams = params
        children.add(index.coerceIn(0, children.size), child)
    }
    fun removeView(child: View) {
        if (children.remove(child)) child.parent = null
    }

    open class LayoutParams(val width: Int, val height: Int) {
        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }
}

object Gravity {
    const val START = 0x00800003
    const val CENTER_VERTICAL = 0x10
}
'''

WIDGET_STUB = '''package android.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup

open class TextView(context: Context) : View(context) {
    var text: String? = null
    var gravity: Int = 0
    var isClickable: Boolean = false
    var isLongClickable: Boolean = false
    var isFocusable: Boolean = false
}

open class ImageView(context: Context) : View(context)

open class LinearLayout(context: Context) : ViewGroup(context) {
    var orientation: Int = HORIZONTAL
    companion object {
        const val HORIZONTAL = 0
        const val VERTICAL = 1
    }
    class LayoutParams(width: Int, height: Int) : ViewGroup.LayoutParams(width, height)
}

abstract class BaseAdapter {
    abstract fun getItem(position: Int): Any?
    open fun notifyDataSetChanged() {}
}
'''

DEX_STUB = '''package org.luckypray.dexkit

import java.lang.reflect.Field
import java.lang.reflect.Method

class DexKitBridge {
    fun findMethod(query: org.luckypray.dexkit.query.FindMethod): List<org.luckypray.dexkit.result.MethodData> = emptyList()
    fun getMethodData(method: Method): org.luckypray.dexkit.result.MethodData? = null
}
'''

DEX_QUERY_STUB = '''package org.luckypray.dexkit.query

class FindMethod {
    fun matcher(matcher: org.luckypray.dexkit.query.matchers.MethodMatcher): FindMethod = this
}

class FindClass
'''

DEX_MATCHER_STUB = '''package org.luckypray.dexkit.query.matchers

class MethodMatcher {
    fun usingStrings(values: List<String>): MethodMatcher = this
}

class ClassMatcher {
    fun usingStrings(values: List<String>): ClassMatcher = this
}
'''

DEX_RESULT_STUB = '''package org.luckypray.dexkit.result

import java.lang.reflect.Field
import java.lang.reflect.Method

class FieldRef(
    val descriptor: String,
    val typeName: String,
    val declaredClassName: String,
    private val resolved: Field? = null
) {
    fun getFieldInstance(loader: ClassLoader): Field = resolved ?: error("unresolved field " + descriptor)
}

class FieldData(val field: FieldRef)

class MethodData(
    val methodName: String,
    val paramCount: Int,
    val returnTypeName: String,
    val declaredClassName: String,
    val usingFields: List<FieldData> = emptyList(),
    private val resolved: Method? = null
) {
    fun getMethodInstance(loader: ClassLoader): Method = resolved ?: error("unresolved method " + methodName)
}
'''

DEX_WRAP_STUB = '''package org.luckypray.dexkit.wrap

import java.lang.reflect.Field

class DexField(val descriptor: String) {
    fun getFieldInstance(loader: ClassLoader): Field = t.mma.hooks.items.hchatextra.Fixtures.datatypeField
}
'''

KAVA_STUB = '''package t.mma.utils

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object KavaReflector {
    fun readField(field: Field, target: Any): Any? = field.get(target)
    fun isAbstract(method: Method): Boolean = Modifier.isAbstract(method.modifiers)
    fun findDeclaredField(clazz: Class<*>, name: String): Field? =
        clazz.declaredFields.firstOrNull { it.name == name }?.apply { isAccessible = true }
    fun findDeclaredMethod(clazz: Class<*>, name: String, vararg params: Class<*>): Method? =
        clazz.declaredMethods.firstOrNull {
            it.name == name && it.parameterTypes.toList() == params.toList()
        }?.apply { isAccessible = true }
}
'''

DEXCACHE_STUB = '''package t.mma.dexkit

import android.content.Context
import android.content.SharedPreferences
import java.lang.reflect.Method

object DexMethodCache {
    fun runtimeKey(context: Context, loader: ClassLoader): String = "fixture"
    fun prefs(context: Context, name: String): SharedPreferences = t.mma.hooks.items.hchatextra.Fixtures.prefs
    fun load(prefs: SharedPreferences, key: String, loader: ClassLoader, slot: String): Method? =
        when (slot) {
            "bind" -> t.mma.hooks.items.hchatextra.Fixtures.bindMethod
            "detail" -> t.mma.hooks.items.hchatextra.Fixtures.detailMethod
            else -> null
        }
    fun save(prefs: SharedPreferences, key: String, slot: String, method: Method) {}
    fun clear(prefs: SharedPreferences, key: String, slot: String) {}
}
'''

FEATURE_CONTEXT_STUB = '''package t.mma.hooks.core

import android.content.Context
import org.luckypray.dexkit.DexKitBridge

class DexKitManager(val bridge: DexKitBridge = DexKitBridge()) {
    fun <T> withCurrentBridge(block: (DexKitBridge) -> T): T = block(bridge)
}

class FeatureContext(
    private val host: Context = Context(),
    private val loader: ClassLoader = FeatureContext::class.java.classLoader!!
) {
    fun hostContext(): Context = host
    fun hostClassLoader(): ClassLoader = loader
    fun dexKitManager(): DexKitManager = DexKitManager()
}
'''

LABELS_STUB = '''package t.mma.hooks.items.hchatextra

object MessageTypeLabels {
    fun recordLabel(type: Int): String = "type-" + type
}
'''

PHASE_B_FIXTURES = '''package t.mma.hooks.items.hchatextra

import android.app.Activity
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import org.luckypray.dexkit.result.FieldRef
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Mirrors the host shape: the field's declaring class is the dataitem's own class. */
class RecordData(val datatype: Int)

class RecordAdapter(private val items: List<RecordData>) : BaseAdapter() {
    override fun getItem(position: Int): Any = items[position]
    fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = View(parent!!.context)
    fun getItemViewType(position: Int): Int = items[position].datatype
}

class RecordDetailActivity : Activity() {
    fun bind() {}
}

class StubPrefs : SharedPreferences {
    private val values = HashMap<String, Any?>()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removals = HashSet<String>()
        override fun putString(key: String, value: String?): SharedPreferences.Editor { pending[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { removals += key; return this }
        override fun apply() {
            removals.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}

object Fixtures {
    val datatypeField: Field = RecordData::class.java.getDeclaredField("datatype")
        .apply { isAccessible = true }
    val bindMethod: Method = RecordAdapter::class.java.getDeclaredMethod(
        "getView", Integer.TYPE, View::class.java, ViewGroup::class.java
    )
    val detailMethod: Method = RecordDetailActivity::class.java.getDeclaredMethod("bind")
    private const val DESCRIPTOR = "Lfixture/RecordData;->datatype:I"
    val prefs: SharedPreferences = StubPrefs().apply { edit().putString("datatype", DESCRIPTOR).apply() }
    val datatypeRef: FieldRef = FieldRef(DESCRIPTOR, "int", RecordData::class.java.name, datatypeField)
    fun bindingFieldRefs(): List<FieldRef> = listOf(datatypeRef)
}
'''

PHASE_B_TEST = '''package t.mma.hooks.items.hchatextra

import android.os.Looper
import android.os.MainQueue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry

private fun check(value: Boolean, message: String) {
    if (!value) throw AssertionError(message)
}

private fun column(activity: android.app.Activity): LinearLayout {
    val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    body.addView(TextView(activity), 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 10))
    body.addView(
        ImageView(activity).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1) },
        1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
    )
    return body
}

private fun row(activity: android.app.Activity, body: LinearLayout): LinearLayout {
    val root = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    root.addView(body, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    return root
}

private fun bind(callback: XC_MethodHook, adapter: RecordAdapter, target: View) {
    val param = XC_MethodHook.MethodHookParam()
    param.args = arrayOf<Any?>(0, target)
    param.result = target
    param.thisObject = adapter
    callback.beforeHookedMethod(param)
    callback.afterHookedMethod(param)
}

fun main() {
    Looper.mainThread = Thread.currentThread()
    MainQueue.reset()
    HookRegistry.get().reset()
    val logs = ArrayList<String>()
    var enabled = true
    var styled = ""
    val label = ForwardedRecordTypeLabels(
        FeatureContext(),
        { enabled },
        { view, text -> view.text = text; styled = text },
        { message, error -> logs += message + ":" + (error?.javaClass?.simpleName ?: "none") }
    )

    // 1. A refused install returns false so the scheduler can retry instead of marking success.
    HookRegistry.get().rejectHook = true
    check(!label.install()) { "被拒绝的 Hook 安装必须返回 false" }
    check(HookRegistry.get().size() == 0) { "拒绝后不得登记安装" }
    check(logs.isNotEmpty()) { "安装失败必须上报" }
    HookRegistry.get().rejectHook = false

    // 2. A retried install succeeds and records exactly one handle.
    check(label.install()) { "拒绝解除后必须安装成功" }
    check(HookRegistry.get().size() == 1) { "必须恰好安装一次，实际=" + HookRegistry.get().size() }
    check(label.install()) { "重复安装应短路返回 true" }
    check(HookRegistry.get().size() == 1) { "重复安装不得再装一次" }

    // 3. The hooked callback binds a real label into the verified column structure.
    val activity = RecordDetailActivity()
    val adapter = RecordAdapter(listOf(RecordData(3)))
    val body = column(activity)
    val host = row(activity, body)
    val callback = HookRegistry.get().callbacks().single()
    bind(callback, adapter, host)
    check(body.childCount == 3) { "标签应插入分隔线上方，实际子项=" + body.childCount }
    val inserted = body.getChildAt(1) as TextView
    check(inserted.text == "type-3") { "标签文本应来自记录 datatype，实际=" + inserted.text }
    check(inserted.visibility == View.VISIBLE) { "标签应可见" }

    // 4. A refused main-queue delivery reports and leaves the registry untouched.
    MainQueue.reject = true
    label.refresh()
    check(logs.any { it.contains("投递被拒") }) { "被拒绝的刷新投递必须上报: " + logs }
    MainQueue.reject = false

    // 5. A refresh queued before destroy must not survive it.
    label.refresh()
    check(MainQueue.pending() == 1) { "刷新应已入队，实际=" + MainQueue.pending() }

    // 6. destroy releases the handle exactly once, drops pending work and detaches the label.
    val logsBeforeDestroy = logs.size
    label.destroy()
    check(HookRegistry.get().unhookCalls == 1) { "Hook 必须卸载一次，实际=" + HookRegistry.get().unhookCalls }
    check(HookRegistry.get().size() == 0) { "销毁后不得残留安装" }
    check(MainQueue.pending() == 0) { "销毁必须撤销未执行投递，实际=" + MainQueue.pending() }
    check(body.childCount == 2) { "销毁必须在主线程就地摘除标签，实际子项=" + body.childCount }
    check(logs.size == logsBeforeDestroy) { "正常销毁不得上报失败: " + logs.drop(logsBeforeDestroy) }
    label.destroy()
    check(HookRegistry.get().unhookCalls == 1) { "重复销毁不得再卸载，实际=" + HookRegistry.get().unhookCalls }
    check(logs.size == logsBeforeDestroy) { "重复销毁不得上报: " + logs.drop(logsBeforeDestroy) }

    // 7. A late callback after destroy is a no-op and never re-adds a label.
    check(HookRegistry.get().callbacks().isEmpty()) { "销毁后不得留有回调" }
    val lateBody = column(activity)
    val lateHost = row(activity, lateBody)
    bind(callback, adapter, lateHost)
    check(lateBody.childCount == 2) { "销毁后的迟到回调不得写视图，实际子项=" + lateBody.childCount }

    // 8. install() after destroy reports failure instead of resurrecting the generation.
    check(!label.install()) { "销毁后安装必须返回 false" }
    check(HookRegistry.get().size() == 0) { "销毁后不得再登记安装" }

    // 9. Unhook failure must be isolated: destroy still completes its clears and reports.
    HookRegistry.get().reset()
    val failing = ForwardedRecordTypeLabels(
        FeatureContext(), { true }, { _, _ -> }, { message, error -> logs += message }
    )
    check(failing.install()) { "第二实例应安装成功" }
    HookRegistry.get().failUnhook = true
    val failingLogsBefore = logs.size
    failing.destroy()
    check(logs.size > failingLogsBefore) { "卸载失败必须上报" }
    HookRegistry.get().failUnhook = false

    println("PASS hchat extra labels: refused install returns false, single handle, real label bind, "
        + "refused delivery reported, queued refresh dropped on destroy, handle released once, "
        + "late callback no-op, unhook failure isolated")
}
'''


def phase_b(tmp: Path):
    files = {
        'Content.kt': ANDROID_WIDGET_STUB,
        'Activity.kt': ACTIVITY_STUB,
        'View.kt': VIEW_STUB,
        'Widget.kt': WIDGET_STUB,
        'Dex.kt': DEX_STUB,
        'DexQuery.kt': DEX_QUERY_STUB,
        'DexMatcher.kt': DEX_MATCHER_STUB,
        'DexResult.kt': DEX_RESULT_STUB,
        'DexWrap.kt': DEX_WRAP_STUB,
        'Kava.kt': KAVA_STUB,
        'DexCache.kt': DEXCACHE_STUB,
        'Context.kt': FEATURE_CONTEXT_STUB,
        'Labels.kt': LABELS_STUB,
        'Os.kt': OS_STUB,
        'Xposed.kt': XD_STUB,
        'Probe.kt': PROBE_STUB,
        'Registry.kt': REGISTRY_STUB,
        'Fixtures.kt': PHASE_B_FIXTURES,
        'PhaseB.kt': PHASE_B_TEST,
    }
    written = write_group(tmp, 'b', files)
    written.append(CORE / 'FeatureOwnership.kt')
    written.append(HCHAT / 'ForwardedRecordTypeLabels.kt')
    compile_and_run(tmp, 'phase-b', written, ['t.mma.hooks.items.hchatextra.PhaseBKt'])


# --------------------------------------------------------------------------------------
# Phase C: real MessageDetailsPendingLayoutObserver
# --------------------------------------------------------------------------------------
OBSERVER_VIEW_STUB = '''package android.view

open class View {
    var isAttachedToWindow: Boolean = true
    var viewTreeObserver: ViewTreeObserver = ViewTreeObserver()
    var parent: ViewGroup? = null

    private val attachListeners = linkedSetOf<OnAttachStateChangeListener>()
    val attachListenerCount: Int get() = attachListeners.size

    fun addOnAttachStateChangeListener(listener: OnAttachStateChangeListener) { attachListeners.add(listener) }
    fun removeOnAttachStateChangeListener(listener: OnAttachStateChangeListener) { attachListeners.remove(listener) }
    fun post(task: Runnable): Boolean = android.os.MainQueue.add(task, false)
    fun postDelayed(task: Runnable, delayMs: Long): Boolean = android.os.MainQueue.add(task, true)
    fun removeCallbacks(task: Runnable) { android.os.MainQueue.remove(task) }

    interface OnAttachStateChangeListener {
        fun onViewAttachedToWindow(view: View)
        fun onViewDetachedFromWindow(view: View)
    }
}

open class ViewGroup {
    private val children = ArrayList<View>()
    val childCount: Int get() = children.size
    fun getChildAt(index: Int): View = children[index]
    fun addView(child: View) { child.parent = this; children.add(child) }
    fun removeView(child: View) { if (children.remove(child)) child.parent = null }
}

class ViewTreeObserver {
    var isAlive: Boolean = true
        private set
    private val layoutListeners = linkedSetOf<OnGlobalLayoutListener>()
    val layoutListenerCount: Int get() = layoutListeners.size

    fun interface OnGlobalLayoutListener {
        fun onGlobalLayout()
    }

    fun addOnGlobalLayoutListener(listener: OnGlobalLayoutListener) {
        check(isAlive)
        layoutListeners.add(listener)
    }

    fun removeOnGlobalLayoutListener(listener: OnGlobalLayoutListener) {
        check(isAlive)
        layoutListeners.remove(listener)
    }

    fun dispatch() { layoutListeners.toList().forEach { it.onGlobalLayout() } }
}
'''

PHASE_C_TEST = '''package t.mma.hooks.items.hchatextra

import android.os.MainQueue
import android.view.View

private fun check(value: Boolean, message: String) {
    if (!value) throw AssertionError(message)
}

fun main() {
    // 1. 被拒绝的即时投递必须让 posted 归位：否则该行后续 globalLayout 全部被早退挡掉。
    //    延迟投递同时拦住，避免 observe() 的首个轮询把计数搅浑。
    MainQueue.reset()
    val observer = MessageDetailsPendingLayoutObserver()
    val view = View()
    var attempts = 0
    observer.observe(view) { attempts += 1; true }
    check(attempts == 0) { "观察不应同步执行动作" }
    MainQueue.reject = true
    MainQueue.rejectDelayed = true
    val pendingBefore = MainQueue.pending()
    view.viewTreeObserver.dispatch()
    check(MainQueue.pending() == pendingBefore) {
        "被拒绝的即时投递不应入队，before=" + pendingBefore + " after=" + MainQueue.pending()
    }
    MainQueue.reject = false
    // 投递恢复后必须真的重试。动作返回 true 会 cancel 本行，因此不会被轮询链条放大计数。
    view.viewTreeObserver.dispatch()
    MainQueue.flush()
    check(attempts == 1) { "投递恢复后必须真正重试，实际尝试=" + attempts }
    check(view.attachListenerCount == 0) { "动作成功后必须释放附着监听，实际=" + view.attachListenerCount }

    // 2. 被拒绝的 postDelayed 必须让 polling 归位，否则轮询永久早退：
    //    故障态下一次布局只执行一次动作，正常态会继续按 32ms 轮询重试，所以判据是比较链条是否还活着。
    MainQueue.reset()
    val pollingObserver = MessageDetailsPendingLayoutObserver()
    val pollingView = View()
    var pollAttempts = 0
    MainQueue.rejectDelayed = true
    pollingObserver.observe(pollingView) { pollAttempts += 1; false }
    check(MainQueue.pending() == 0) { "被拒绝的首个轮询不应入队，实际=" + MainQueue.pending() }
    MainQueue.rejectDelayed = false
    pollingView.viewTreeObserver.dispatch()
    MainQueue.flush()
    check(pollAttempts > 1) { "被拒绝的轮询归位后必须能继续调度，实际尝试=" + pollAttempts }

    // 3. clear() 必须摘除附着监听并停止轮询。
    pollingObserver.clear()
    check(pollingView.attachListenerCount == 0) { "clear 必须摘除附着监听，实际=" + pollingView.attachListenerCount }

    // 4. 脱离窗口的行不得执行动作。
    MainQueue.reset()
    MainQueue.rejectDelayed = true
    val detached = View()
    var detachedAttempts = 0
    observer.observe(detached) { detachedAttempts += 1; true }
    detached.viewTreeObserver.dispatch()
    detached.isAttachedToWindow = false
    MainQueue.flush()
    check(detachedAttempts == 0) { "已脱离窗口的行不得执行动作，实际=" + detachedAttempts }

    // 5. 连续被拒后，下一次真实布局必须恢复执行。
    MainQueue.reset()
    val retry = View()
    var retryAttempts = 0
    MainQueue.reject = true
    MainQueue.rejectDelayed = true
    observer.observe(retry) { retryAttempts += 1; true }
    retry.viewTreeObserver.dispatch()
    retry.viewTreeObserver.dispatch()
    check(retryAttempts == 0) { "被拒绝期间不得执行动作，实际=" + retryAttempts }
    MainQueue.reject = false
    retry.viewTreeObserver.dispatch()
    MainQueue.flush()
    check(retryAttempts == 1) { "连续被拒后必须在下一次布局重试，实际=" + retryAttempts }
    check(retry.attachListenerCount == 0) { "动作成功后必须释放附着监听，实际=" + retry.attachListenerCount }
    observer.clear()
    check(detached.attachListenerCount == 0) { "clear 必须摘除遗留附着监听，实际=" + detached.attachListenerCount }
    println("PASS hchat extra pending layout: refused post retries on next layout, "
        + "refused poll keeps polling alive, clear detaches, detach suppresses, repeat refusal recovers")
}
'''


def phase_c(tmp: Path):
    files = {
        'Os.kt': OS_STUB,
        'View.kt': OBSERVER_VIEW_STUB,
        'PhaseC.kt': PHASE_C_TEST,
    }
    written = write_group(tmp, 'c', files)
    written.append(HCHAT / 'MessageDetailsPendingLayoutObserver.kt')
    compile_and_run(tmp, 'phase-c', written, ['t.mma.hooks.items.hchatextra.PhaseCKt'])


# --------------------------------------------------------------------------------------
# Phase D: source invariants that cannot be executed off-device
# --------------------------------------------------------------------------------------
def phase_d():
    feature = (HCHAT / 'HchatExtraFeature.kt').read_text()
    labels = (HCHAT / 'ForwardedRecordTypeLabels.kt').read_text()
    pending = (HCHAT / 'MessageDetailsPendingLayoutObserver.kt').read_text()
    layout = (HCHAT / 'MessageDetailsLayoutObserver.kt').read_text()
    queue = (HCHAT / 'MessageDetailsPreDrawQueue.kt').read_text()

    def need(haystack, needle, label):
        if needle not in haystack:
            raise SystemExit('MISSING ' + label + ': ' + needle)

    def strip_comments(text):
        """Drop imports and comments: prose may name HookRegistry, code may not call it."""
        out = []
        in_block = False
        for line in text.splitlines():
            body = line
            if in_block:
                end = body.find('*/')
                if end < 0:
                    continue
                body = body[end + 2:]
                in_block = False
            while True:
                start = body.find('/*')
                if start < 0:
                    break
                end = body.find('*/', start + 2)
                if end < 0:
                    body = body[:start]
                    in_block = True
                    break
                body = body[:start] + body[end + 2:]
            slash = body.find('//')
            if slash >= 0:
                body = body[:slash]
            if body.strip().startswith('import '):
                continue
            out.append(body)
        return '\n'.join(out)

    # Entry ownership: one preference-scoped generation, release registered before start.
    need(feature, 'PreferenceRuntimeOwner(', 'entry owner')
    need(feature, 'HchatExtraSettings.KEY_MESSAGE_DETAILS,\n            HchatExtraSettings.KEY_RED_PACKET_DETAILS,\n'
                 '            HchatExtraSettings.KEY_SKIP_WEB_RISK,\n            HchatExtraSettings.KEY_GROUP_MEMBER_HISTORY',
         'four restart keys')
    need(feature, 'restartOnChange = true', 'restart on toggle')
    if feature.index('resources.register {') > feature.index('created.start()'):
        raise SystemExit('ORDER: release must be registered before the generation starts')
    if feature.index('trackResource { owner.close() }') > feature.index('owner.start()'):
        raise SystemExit('ORDER: owner.close() must be tracked before owner.start()')
    need(feature, 'owner.refresh()', 'DexReady refresh')
    need(feature, 'runtime?.onDexReady()', 'DexReady reschedule')

    # Hook ownership: no direct HookRegistry use anywhere in the feature.
    if 'HookRegistry' in strip_comments(feature):
        raise SystemExit('LEAK: HchatExtraFeature must not call HookRegistry directly')
    if 'HookRegistry' in strip_comments(labels):
        raise SystemExit('LEAK: ForwardedRecordTypeLabels must not call HookRegistry directly')
    need(feature, 'private val ownedHooks = OwnedHooks', 'owned hooks')
    need(feature, 'return ownedHooks.hook(method, callback) != null', 'hookOwned result')
    need(labels, 'val handle = ownedHooks.hook(targets.bind', 'labels owned hook')
    need(labels, 'installed = handle != null', 'labels install result')
    need(labels, 'private val main = OwnedHandler', 'labels owned handler')
    labels_destroy_at = labels.index('fun destroy() {')
    labels_hook_close = labels.index('ownedHooks.close()', labels_destroy_at)
    labels_main_close = labels.index('main.close()', labels_destroy_at)
    if labels_hook_close > labels_main_close:
        raise SystemExit('ORDER: labels must unhook before closing the refresh handler')
    if labels.index('rows.clear()', labels_destroy_at) < labels_hook_close:
        raise SystemExit('ORDER: labels must unhook before dropping the row registry')

    # Destroy ordering: hooks are released last, after every registry clear.
    destroy_at = feature.index('fun destroy() {')
    hook_marker = 'releaseSafely("独占Hook") { ownedHooks.close() }'
    if hook_marker not in feature[destroy_at:]:
        raise SystemExit('MISSING hook release: the owned hook handle is never closed')
    hook_close = feature.index(hook_marker, destroy_at)
    for earlier in ('releaseSafely("动态撤回运行时")', 'releaseSafely("聊天记录类型")',
                    'releaseSafely("消息详情监听")', 'releaseSafely("尾部修复token")'):
        if feature.index(earlier, destroy_at) > hook_close:
            raise SystemExit('ORDER: ' + earlier + ' must run before the hook release')
    need(feature, 'if (destroyed) return\n        destroyed = true', 'destroy gate')
    # Per-view isolation: one throwing restore must not skip the remaining views.
    need(feature, 'spacings.forEach { (parent, spacing) ->', 'per-view spacing loop')
    need(feature, 'restoreAvatarDetailsSpacing(parent, spacing)', 'per-view restore call')
    need(feature, 'if (!view.clipChildren) view.clipChildren = state.originalClipChildren', 'per-child clip restore')
    per_view_guard = ('try {\n'
                      '                    restoreAvatarDetailsSpacing(parent, spacing)\n'
                      '                } catch (error: Throwable) {\n')
    if per_view_guard not in feature:
        raise SystemExit('MISSING per-view isolation: the restore call is not wrapped in try/catch')
    if 'run {\n                    restoreAvatarDetailsSpacing' in feature:
        raise SystemExit('LEAK per-view isolation: the restore call is not protected by a catch')
    per_child_guard = ('try {\n'
                       '                if (!view.clipChildren) view.clipChildren = state.originalClipChildren\n'
                       '            } catch (error: Throwable) {\n')
    if per_child_guard not in feature:
        raise SystemExit('MISSING per-child isolation: the clip restore is not wrapped in try/catch')

    # Runtime close: tickets before the hooker.
    close_at = feature.index('fun close() {', feature.index('internal class HchatExtraRuntime('))
    if feature.index('snapshot.second.forEach(::closeTicket)') > feature.index('snapshot.first?.let(::destroyHooker)'):
        raise SystemExit('ORDER: tickets must be closed before the hooker is destroyed')
    need(feature, 'createdTickets.forEach(::closeTicket)', 'orphan ticket rollback')
    need(feature, 'destroyHooker(created)', 'orphan hooker rollback')

    # Every main-thread delivery is result-checked.
    need(feature, 'if (!list.postDelayed(timeout, 1550L)) finish()', 'tail repair timeout')
    need(feature, 'if (!textView.post {', 'red packet record time')
    need(feature, 'if (itemView != null && !itemView.post {', 'red packet fallback')
    need(feature, 'if (!label.post {', 'label delivery')
    need(feature, 'logThrottled(', 'throttled reject reporting')
    need(pending, 'val accepted = root.post {', 'pending post result')
    need(pending, 'if (!accepted) posted = false', 'pending posted rollback')
    need(pending, 'if (!root.postDelayed(poll, 32L)) {', 'pending poll result')
    need(pending, 'polling = false', 'pending polling rollback')
    need(layout, 'if (!view.post(callback)) pending = null', 'layout post result')
    need(labels, 'if (!detachHandler.post(detach))', 'labels detach delivery')
    need(labels, 'if (!main.post {', 'labels refresh delivery')

    # Bare (unchecked) delivery statements must be gone from every hchatextra source.
    import re
    bare = re.compile(r'^\s*[A-Za-z_][\w.?]*\.(post|postDelayed)\s*[({]')
    for name, text in (('HchatExtraFeature.kt', feature), ('ForwardedRecordTypeLabels.kt', labels),
                       ('MessageDetailsPendingLayoutObserver.kt', pending),
                       ('MessageDetailsLayoutObserver.kt', layout),
                       ('MessageDetailsPreDrawQueue.kt', queue)):
        for number, line in enumerate(text.splitlines(), 1):
            if bare.match(line) and 'if ' not in line and '=' not in line.split('.post')[0]:
                raise SystemExit('UNCHECKED ' + name + ':' + str(number) + ': ' + line.strip())

    print('PASS source invariants: entry ownership, four restart keys, release-before-start, '
          'no direct HookRegistry, hook released last, tickets before hooker, orphan rollback, '
          'every delivery result-checked')
    print('feature lines: ' + str(len(feature.splitlines())))
    print('labels lines: ' + str(len(labels.splitlines())))


def main():
    if not LIBS.exists():
        raise SystemExit('missing kotlin libs: ' + str(LIBS))
    with tempfile.TemporaryDirectory() as raw:
        tmp = Path(raw)
        print('--- phase A: runtime lifecycle ---')
        phase_a(tmp)
        print('--- phase B: forwarded record labels ---')
        phase_b(tmp)
        print('--- phase C: pending layout observer ---')
        phase_c(tmp)
        print('--- phase D: source invariants ---')
        phase_d()
    print('ALL PHASES PASSED')

if __name__ == '__main__':
    main()
