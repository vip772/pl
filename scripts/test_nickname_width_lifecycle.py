"""Execute the real NicknameWidthFeature/NicknameWidthRuntime/NicknameWidthState production code.

The harness supplies only synthetic platform boundaries (android.os/content/widget, Xposed,
HostPreferences, WeChat host classes). The toggle owner, resource scope, hook ownership and
DexInstallScheduler under test are the real project sources. Not an Android/R8 build and not a
real WeChat session.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
STD = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
PRODUCTION = ROOT / 'app/src/main/java/t/mma/hooks/items/nicknamewidth/NicknameWidthFeature.kt'
CORE = ROOT / 'app/src/main/java/t/mma/hooks/core'

STUBS = {}

STUBS['Platform.kt'] = '''package android.os
class Looper {
    companion object {
        val MAIN = Looper()
        @Volatile var mainThread: Thread? = null
        fun getMainLooper(): Looper = MAIN
        fun myLooper(): Looper? = if (Thread.currentThread() === mainThread) MAIN else null
    }
}
object MainQueue {
    val tasks = mutableListOf<Runnable>()
    @Volatile var reject = false
    fun flush(limit: Int = 128) {
        var count = 0
        while (tasks.isNotEmpty() && count < limit) { val next = tasks.removeAt(0); next.run(); count++ }
    }
    fun pending(): Int = tasks.size
    fun clear() { tasks.clear() }
}
class Handler(val looper: Looper = Looper.getMainLooper()) {
    fun post(task: Runnable): Boolean = enqueue(task)
    fun postDelayed(task: Runnable, delayMs: Long): Boolean = enqueue(task)
    fun removeCallbacks(task: Runnable) { MainQueue.tasks.removeAll { it === task } }
    private fun enqueue(task: Runnable): Boolean {
        if (MainQueue.reject) return false
        MainQueue.tasks.add(task)
        return true
    }
}
'''

STUBS['Widget.kt'] = '''package android.widget
class TextView {
    @Volatile var writes = 0
    @Volatile var failWrites = false
    private var stored = 240
    var maxWidth: Int
        get() = stored
        set(value) {
            if (failWrites) throw IllegalStateException("宽度写入失败")
            stored = value
            writes += 1
        }
}
'''

STUBS['Content.kt'] = '''package android.content
import android.content.pm.PackageManager
class Context {
    var packageName: String = "com.tencent.mm"
    val packageManager: PackageManager = PackageManager()
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

STUBS['PackageManager.kt'] = '''package android.content.pm
class PackageInfo(val versionName: String?)
class PackageManager {
    fun getPackageInfo(packageName: String, flags: Int): PackageInfo = PackageInfo(versionName)
    companion object {
        // Process-wide like the real host package version.
        @Volatile var versionName: String = "8.0.78"
    }
}
'''

STUBS['Xposed.kt'] = '''package de.robv.android.xposed
open class XC_MethodHook {
    class Unhook(val method: java.lang.reflect.Member)
    class MethodHookParam(val args: Array<Any?>)
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}
}
'''

STUBS['CoreStubs.kt'] = '''package t.mma.hooks.core
import android.content.SharedPreferences
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Member

object Harness {
    val logs = mutableListOf<String>()
    val errors = mutableListOf<String>()
}

class FeatureContext(private val host: android.content.Context, private val loader: ClassLoader) {
    fun hostContext(): android.content.Context = host
    fun hostClassLoader(): ClassLoader = loader
}

open class BaseFeature {
    val tracked = mutableListOf<() -> Unit>()
    open fun featureId(): String = ""
    open fun name(): String = ""
    open fun onFeatureInit(context: FeatureContext) {}
    open fun onFeatureInstall(context: FeatureContext) {}
    fun registerSettingsProvider(provider: Any) {}
    fun logError(message: String, throwable: Throwable?) {
        Harness.errors += message + ": " + (throwable?.javaClass?.simpleName ?: "none")
    }
    fun trackResource(release: () -> Unit) { tracked += release }
    fun destroyAll() {
        tracked.asReversed().toList().forEach { release ->
            try { release() } catch (error: Throwable) { Harness.errors += "tracked: " + error.message }
        }
        tracked.clear()
    }
}

class HookRegistry private constructor() {
    private val entries = mutableListOf<Pair<Member, XC_MethodHook>>()
    @Volatile var rejectHook = false
    @Volatile var failUnhook = false
    var unhookCalls = 0
    fun hook(method: Member, callback: XC_MethodHook): XC_MethodHook.Unhook {
        if (rejectHook) throw IllegalStateException("宿主拒绝安装 Hook")
        entries += method to callback
        return XC_MethodHook.Unhook(method)
    }
    fun unhook(handle: XC_MethodHook.Unhook?) {
        unhookCalls += 1
        if (failUnhook) throw IllegalStateException("宿主卸载 Hook 失败")
        if (handle == null) return
        entries.removeAll { it.first === handle.method }
    }
    fun size(): Int = entries.size
    fun methods(): List<Member> = entries.map { it.first }
    fun callbacks(): List<XC_MethodHook> = entries.map { it.second }
    fun fire(index: Int, param: XC_MethodHook.MethodHookParam) = entries[index].second.afterHookedMethod(param)
    fun reset() { entries.clear() }
    companion object {
        private val INSTANCE = HookRegistry()
        fun get(): HookRegistry = INSTANCE
    }
}
'''

STUBS['AppStubs.kt'] = '''package t.mma.preferences
import android.content.SharedPreferences
object HchatStorage {
    @Volatile var current: SharedPreferences? = null
    fun preferences(context: android.content.Context, name: String): SharedPreferences = current!!
}
'''

STUBS['UiStubs.kt'] = '''package t.mma.ui
object FeatureSettingsProvider {
    const val CATEGORY_ENHANCE: String = "enhance"
}
open class SimpleFeatureSettingsProvider(
    val featureIdValue: String,
    val titleValue: String,
    val summaryValue: String,
    val categoryValue: String,
    val defaultEnabled: Boolean
)
'''

STUBS['LogStubs.kt'] = '''package t.mma.utils
object HLog {
    fun d(message: String) { t.mma.hooks.core.Harness.logs += "D:" + message }
    fun i(message: String) { t.mma.hooks.core.Harness.logs += "I:" + message }
    fun w(message: String) { t.mma.hooks.core.Harness.logs += "W:" + message }
    fun e(message: String) { t.mma.hooks.core.Harness.logs += "E:" + message }
    fun e(message: String, throwable: Throwable?) { t.mma.hooks.core.Harness.logs += "E:" + message }
}
'''

STUBS['HostWechat.kt'] = '''package com.tencent.mm.ui.chatting.viewitems
import android.widget.TextView
class h0 { @JvmField var userTV: TextView = TextView() }
class b0 { fun p(holder: h0, chat: gk5.d, message: com.tencent.mm.storage.e9, talker: String) {} }
'''

STUBS['HostChat.kt'] = '''package gk5
class d {
    @Volatile var room: String = "12345@chatroom"
    fun x(): String = room
}
'''

STUBS['HostMessage.kt'] = '''package com.tencent.mm.storage
class e9
'''

TEST = '''package t.mma.hooks.items.nicknamewidth

import android.content.SharedPreferences
import android.os.Looper
import android.os.MainQueue
import android.widget.TextView
import com.tencent.mm.ui.chatting.viewitems.b0
import com.tencent.mm.ui.chatting.viewitems.h0
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.Harness
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage

class StubPrefs : SharedPreferences {
    private val values = HashMap<String, Boolean>()
    private val listeners = ArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] ?: defValue
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += listener
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

fun await(message: String, timeoutMs: Long = 4000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        // The harness main thread drains queued postings, like the real Looper would.
        MainQueue.flush()
        if (condition()) return
        Thread.sleep(2)
    }
    error("等待超时: " + message + " | logs=" + t.mma.hooks.core.Harness.logs + " | errors=" + t.mma.hooks.core.Harness.errors)
}

fun awaitHooks(expected: Int) = await("Hook 数=" + expected) { HookRegistry.get().size() == expected }

internal fun newState(): Pair<NicknameWidthState, MutableList<Throwable>> {
    val errors = mutableListOf<Throwable>()
    return NicknameWidthState { errors += it } to errors
}

fun view(width: Int): TextView = TextView().apply { maxWidth = width; writes = 0 }

internal fun stateSemantics() {
    Looper.mainThread = Thread.currentThread()
    // Default-off / private chat must not write; group bind writes exactly once.
    val (state, errors) = newState()
    val v = view(240)
    state.bind(v, false)
    check(v.writes == 0) { "关闭态不应写宽度" }
    state.bind(v, true)
    check(v.maxWidth == Int.MAX_VALUE && v.writes == 1) { "群聊应放宽且只写一次" }
    repeat(2000) { state.bind(v, true) }
    check(v.writes == 1) { "重复绑定不得重复写" }
    state.bind(v, false)
    check(v.maxWidth == 240 && v.writes == 2) { "退出群聊场景应还原" }
    // Host-side width change wins; close must not clobber it.
    state.bind(v, true)
    v.maxWidth = 180
    state.close()
    check(v.maxWidth == 180) { "宿主已改宽度不应被覆盖" }
    state.close()
    // Unlimited and ems guards.
    val (guard, _) = newState()
    val unlimited = view(Int.MAX_VALUE)
    guard.bind(unlimited, true)
    check(unlimited.writes == 0) { "已是无限宽不应写" }
    val ems = view(-1)
    guard.bind(ems, true)
    check(ems.writes == 0) { "ems 宽度不应改写" }
    guard.close()
    // Recycled view handed from one runtime to the next keeps the real original width.
    val (first, _) = newState()
    val (second, _) = newState()
    val recycled = view(300)
    first.bind(recycled, true)
    check(recycled.maxWidth == Int.MAX_VALUE)
    second.bind(recycled, true)
    check(recycled.maxWidth == Int.MAX_VALUE) { "接管后仍是放宽态" }
    first.close()
    check(recycled.maxWidth == Int.MAX_VALUE) { "旧运行时销毁不得还原新运行时占用的视图" }
    second.close()
    check(recycled.maxWidth == 300) { "新运行时销毁应还原真实原宽" }
    // Non-main close posts; rejected posting restores in place instead of leaking maxWidth.
    val (queued, _) = newState()
    val queuedView = view(400)
    queued.bind(queuedView, true)
    val savedMain = Looper.mainThread
    Looper.mainThread = Thread()
    queued.close()
    check(queuedView.maxWidth == Int.MAX_VALUE) { "跨线程销毁应先投递" }
    MainQueue.flush()
    check(queuedView.maxWidth == 400) { "投递执行后应还原" }
    val (rejected, rejectedErrors) = newState()
    val rejectedView = view(320)
    rejected.bind(rejectedView, true)
    MainQueue.reject = true
    rejected.close()
    check(rejectedView.maxWidth == 320) { "投递被拒应就地还原" }
    check(rejectedErrors.any { it.message?.contains("投递被拒绝") == true }) { "投递被拒应有错误记录" }
    MainQueue.reject = false
    Looper.mainThread = savedMain
    // A throwing host setter must not leave a stale claim behind.
    val (failing, _) = newState()
    val failingView = view(260)
    failingView.failWrites = true
    var threw = false
    try { failing.bind(failingView, true) } catch (error: Throwable) { threw = true }
    check(threw) { "宿主写入异常应抛给回调隔离" }
    failingView.failWrites = false
    failing.close()
    check(failingView.maxWidth == 260) { "写入失败后不得残留登记" }
    println("PASS state: off/private no-write, single write per bind, restore, host override, takeover, queue + rejected post, setter failure rollback")
}

internal fun newFeature(prefs: StubPrefs, loader: ClassLoader): Pair<NicknameWidthFeature, FeatureContext> {
    HchatStorage.current = prefs
    val context = FeatureContext(android.content.Context(), loader)
    val feature = NicknameWidthFeature()
    feature.onFeatureInit(context)
    feature.onFeatureInstall(context)
    return feature to context
}

internal fun runtimeLifecycle() {
    Looper.mainThread = Thread.currentThread()
    MainQueue.clear()
    val registry = HookRegistry.get()
    registry.reset()
    registry.rejectHook = false
    registry.failUnhook = false
    val loader = b0::class.java.classLoader!!
    val prefs = StubPrefs()
    prefs.set(NicknameWidthSettings.ENABLE, false)
    val (feature, _) = newFeature(prefs, loader)
    await("默认关闭不装 Hook", 200) { registry.size() == 0 }
    check(prefs.listenerCount() == 1) { "应只注册一个偏好监听" }
    // Enable: install through the production scheduler.
    prefs.set(NicknameWidthSettings.ENABLE, true)
    awaitHooks(1)
    val method = registry.methods().single()
    check(method.declaringClass.name == "com.tencent.mm.ui.chatting.viewitems.b0" && method.name == "p") {
        "Hook 目标应为 b0.p，实际=" + method.declaringClass.name + "." + method.name
    }
    // Group bind widens, private-chat bind restores.
    val holder = h0()
    val groupView = view(240)
    holder.userTV = groupView
    val chat = gk5.d()
    chat.room = "998877@chatroom"
    registry.fire(0, XC_MethodHook.MethodHookParam(arrayOf(holder, chat, com.tencent.mm.storage.e9(), "998877@chatroom")))
    check(groupView.maxWidth == Int.MAX_VALUE) { "群聊昵称应放宽" }
    chat.room = "wxid_solo"
    registry.fire(0, XC_MethodHook.MethodHookParam(arrayOf(holder, chat, com.tencent.mm.storage.e9(), "wxid_solo")))
    check(groupView.maxWidth == 240) { "单聊不应保持放宽" }
    chat.room = "998877@im.chatroom"
    registry.fire(0, XC_MethodHook.MethodHookParam(arrayOf(holder, chat, com.tencent.mm.storage.e9(), "998877@im.chatroom")))
    check(groupView.maxWidth == Int.MAX_VALUE) { "企业微信群应放宽" }
    // Disable: hook released, width restored, ticket cancelled.
    prefs.set(NicknameWidthSettings.ENABLE, false)
    awaitHooks(0)
    check(groupView.maxWidth == 240) { "关闭后应还原宽度" }
    MainQueue.flush()
    await("关闭后不再安装", 300) { registry.size() == 0 }
    // Re-enable: new runtime, new hook, original width still correct for the recycled view.
    prefs.set(NicknameWidthSettings.ENABLE, true)
    awaitHooks(1)
    registry.fire(0, XC_MethodHook.MethodHookParam(arrayOf(holder, chat, com.tencent.mm.storage.e9(), "998877@im.chatroom")))
    check(groupView.maxWidth == Int.MAX_VALUE) { "重开后应重新放宽" }
    check(groupView.writes <= 6) { "宽度写入次数异常: " + groupView.writes }
    // Install failure rolls back and the bounded scheduler retry installs once.
    prefs.set(NicknameWidthSettings.ENABLE, false)
    awaitHooks(0)
    registry.rejectHook = true
    prefs.set(NicknameWidthSettings.ENABLE, true)
    await("安装失败后不应残留 Hook", 400) { registry.size() == 0 }
    registry.rejectHook = false
    MainQueue.flush()
    awaitHooks(1)
    check(registry.size() == 1) { "重试后应只有一个 Hook，实际=" + registry.size() }
    // Unhook failure during close must not abort cleanup or escape.
    registry.failUnhook = true
    prefs.set(NicknameWidthSettings.ENABLE, false)
    check(groupView.maxWidth == 240) { "卸载异常时仍应还原宽度" }
    check(Harness.errors.any { it.contains("Hook 释放失败") }) { "卸载异常应被隔离上报" }
    registry.failUnhook = false
    registry.reset()
    // Destroy: full release, then a new instance reinstalls.
    prefs.set(NicknameWidthSettings.ENABLE, true)
    awaitHooks(1)
    val beforeDestroy = hookHandleCount()
    feature.destroyAll()
    check(registry.size() == 0) { "销毁后应卸载 Hook" }
    check(groupView.maxWidth == 240) { "销毁后应还原宽度" }
    check(Harness.errors.isNotEmpty())
    check(beforeDestroy == 1)
    MainQueue.flush()
    await("销毁后不得重装", 300) { registry.size() == 0 }
    val (second, _) = newFeature(prefs, loader)
    awaitHooks(1)
    prefs.set(NicknameWidthSettings.ENABLE, false)
    awaitHooks(0)
    second.destroyAll()
    // Version gate.
    registry.reset()
    android.content.pm.PackageManager.versionName = "8.0.79"
    val gated = StubPrefs()
    gated.set(NicknameWidthSettings.ENABLE, true)
    val (gatedFeature, _) = newFeature(gated, loader)
    await("未验证版本不装 Hook", 400) { registry.size() == 0 }
    check(Harness.logs.any { it.contains("未验证微信版本 8.0.79") }) { "版本门应有日志" }
    gated.set(NicknameWidthSettings.ENABLE, false)
    gatedFeature.destroyAll()
    android.content.pm.PackageManager.versionName = "8.0.78"
    println("PASS runtime: default-off, enable, group/private/enterprise bind, disable-unhook-restore, reopen, install rollback + bounded retry, unhook fault isolation, destroy then new instance, version gate")
}

internal fun hookHandleCount(): Int = HookRegistry.get().size()

fun main() {
    stateSemantics()
    runtimeLifecycle()
    println("PASS nickname-width production lifecycle (synthetic platform boundaries only)")
}
'''


def main() -> None:
    sources = [PRODUCTION,
               CORE / 'FeatureOwnership.kt',
               CORE / 'FeatureResourceScope.kt',
               CORE / 'PreferenceRuntimeOwner.kt',
               CORE / 'DexInstallScheduler.kt']
    with tempfile.TemporaryDirectory(prefix='pl-nickname-width-') as td:
        path = Path(td)
        for name, text in STUBS.items():
            (path / name).write_text(text)
        (path / 'Test.kt').write_text(TEST)
        compile_cmd = ['java', '-Xmx320m', '-cp', ':'.join(map(str, LIBS.glob('*.jar'))),
                       'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn',
                       '-no-stdlib', '-no-reflect', '-jvm-target', '17',
                       '-classpath', str(STD), '-d', str(path / 'out')]
        compile_cmd += [str(p) for p in sorted(path.glob('*.kt'))]
        compile_cmd += [str(p) for p in sources]
        subprocess.run(compile_cmd, check=True, timeout=180)
        subprocess.run(['java', '-cp', str(path / 'out') + ':' + str(STD),
                        't.mma.hooks.items.nicknamewidth.TestKt'], check=True, timeout=120)


if __name__ == '__main__':
    main()