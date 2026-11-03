"""校验 core/FeatureOwnership.kt：Android SDK 定向编译 + 所有权边界合成宿主行为断言。不跑 Gradle、不碰实机。

两段式：
  A. classpath 用真实 android.jar 做类型检查，证明 Handler/Looper 等 Android API 用法正确；
  B. classpath 换成 android.os 合成 stub 编出可执行 jar，跑行为断言（android.jar 方法体是 Stub!，不能跑）。
"""
from pathlib import Path
import subprocess, sys, tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
SDK = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('/usr/lib/android-sdk/platforms/android-36/android.jar')
SRC = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / 'app/src/main/java/t/mma/hooks/core/FeatureOwnership.kt'
assert LIBS.is_dir(), f'missing kotlin libs: {LIBS}'
assert SDK.is_file(), f'missing android.jar: {SDK}'
assert SRC.is_file(), f'missing source: {SRC}'
std = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))

COMMON = {
    'XposedStub.kt': '''package de.robv.android.xposed
open class XC_MethodHook {
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}
    class MethodHookParam(var args: Array<Any?>? = null) { var result: Any? = null }
    class Unhook(val method: java.lang.reflect.Member, val callback: XC_MethodHook) {
        var unhookCalls = 0
        var failUnhook = false
        fun unhook() { unhookCalls++; if (failUnhook) error("native unhook failed") }
    }
}
''',
    'RegistryStub.kt': '''package t.mma.hooks.core
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Member
class HookRegistry private constructor() {
    val installed = java.util.ArrayList<XC_MethodHook.Unhook>()
    var failHook = false
    var beforeHook: (() -> Unit)? = null
    fun hook(method: Member, callback: XC_MethodHook): XC_MethodHook.Unhook {
        beforeHook?.invoke()
        if (failHook) error("hook rejected")
        return XC_MethodHook.Unhook(method, callback).also { installed.add(it) }
    }
    fun unhook(unhook: XC_MethodHook.Unhook?) { if (unhook != null) unhook.unhook() }
    companion object { private val I = HookRegistry(); @JvmStatic fun get(): HookRegistry = I }
}
''',
}

RUNTIME = {
    'OsStub.kt': """package android.os
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class Looper private constructor() {
    companion object { private val MAIN = Looper(); @JvmStatic fun getMainLooper(): Looper = MAIN }
}
class Handler(private val looper: Looper?) {
    private val queued = java.util.ArrayList<Runnable>()
    var reject = false
    var failPost = false
    var failRemove = false
    var postEntered: CountDownLatch? = null
    var postRelease: CountDownLatch? = null
    var removeCalls = 0
    init { latest = this }
    fun post(task: Runnable): Boolean {
        postEntered?.countDown()
        check(postRelease?.await(5, TimeUnit.SECONDS) != false)
        if (failPost) error("synthetic post failure")
        if (reject) return false
        synchronized(queued) { queued.add(task) }
        return true
    }
    fun postDelayed(task: Runnable, delayMs: Long): Boolean = post(task)
    fun removeCallbacks(task: Runnable) {
        removeCalls++
        if (failRemove) error("synthetic remove failure")
        synchronized(queued) { queued.removeAll { it === task } }
    }
    fun queuedCount(): Int = synchronized(queued) { queued.size }
    fun drain(): Int {
        val pending = synchronized(queued) { queued.toList().also { queued.clear() } }
        pending.forEach { it.run() }
        return pending.size
    }
    companion object { lateinit var latest: Handler }
}
""",}

MAIN = '''import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.OwnedExecutor
import t.mma.hooks.core.OwnedHandler
import t.mma.hooks.core.OwnedHooks
import t.mma.hooks.core.OwnerGeneration
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

var failures = 0
fun check(name: String, ok: Boolean, detail: String = "") {
    if (ok) println("PASS " + name + (if (detail.isEmpty()) "" else " :: " + detail))
    else { failures++; println("FAIL " + name + " :: " + detail) }
}

fun main() {
    val sample: Method = Sample::class.java.getDeclaredMethod("sampleMethod")

    // 1 关闭前登记生效
    run {
        val errors = AtomicInteger()
        val hooks = OwnedHooks { errors.incrementAndGet() }
        val h1 = hooks.hook(sample, XC_MethodHook())
        val h2 = hooks.hook(sample, XC_MethodHook())
        check("1 关闭前登记生效", h1 != null && h2 != null && hooks.size() == 2 && !hooks.isClosed(),
            "size=" + hooks.size() + " errors=" + errors.get())
    }
    // 2 关闭后拒绝安装；迟到句柄释放另由第 22 项覆盖
    run {
        val hooks = OwnedHooks { }
        hooks.close()
        val late = hooks.hook(sample, XC_MethodHook())
        check("2 关闭后拒绝安装", late == null && hooks.size() == 0,
            "unhookCalls=" + late?.unhookCalls + " size=" + hooks.size())
    }
    // 3 关闭幂等
    run {
        val hooks = OwnedHooks { }
        val h = hooks.hook(sample, XC_MethodHook())!!
        hooks.close(); hooks.close(); hooks.close()
        check("3 关闭幂等", h.unhookCalls == 1 && hooks.isClosed(), "unhookCalls=" + h.unhookCalls)
    }
    // 4 单项释放异常不阻断其余（关闭逆序 third -> second -> first）
    run {
        val errors = AtomicInteger()
        val hooks = OwnedHooks { errors.incrementAndGet() }
        val first = hooks.hook(sample, XC_MethodHook())!!
        val second = hooks.hook(sample, XC_MethodHook())!!
        val third = hooks.hook(sample, XC_MethodHook())!!
        second.failUnhook = true
        hooks.close()
        check("4 释放异常隔离不阻断其余",
            first.unhookCalls == 1 && second.unhookCalls == 1 && third.unhookCalls == 1 && errors.get() == 1,
            "calls=" + first.unhookCalls + "/" + second.unhookCalls + "/" + third.unhookCalls + " errors=" + errors.get())
    }
    // 5 Handler 关闭撤销未执行
    run {
        val ran = AtomicInteger()
        val handler = OwnedHandler({ }, android.os.Looper.getMainLooper())
        handler.post { ran.incrementAndGet() }
        handler.postDelayed({ ran.incrementAndGet() }, 60_000L)
        val registered = handler.pendingCount()
        handler.close()
        check("5 Handler 关闭撤销未执行",
            registered == 2 && handler.pendingCount() == 0 && handler.isClosed(),
            "registered=" + registered + " pending=" + handler.pendingCount())
    }
    // 6 Handler 关闭后 post 返回 false
    run {
        val handler = OwnedHandler({ }, android.os.Looper.getMainLooper())
        handler.close()
        check("6 Handler 关闭后 post 返回 false", !handler.post { } && !handler.postDelayed({ }, 10L), "")
    }
    // 7 Executor 关闭后拒绝、关闭后不再执行
    run {
        val executor = OwnedExecutor("Hchat-Test-Owned", { })
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.submit { started.countDown(); release.await(5, TimeUnit.SECONDS) }
        started.await(5, TimeUnit.SECONDS)
        executor.close()
        release.countDown()
        val accepted = executor.submit { }
        Thread.sleep(200)
        check("7 Executor 关闭后拒绝新任务", !accepted && executor.isClosed(), "accepted=" + accepted)
        check("7b Executor 关闭后队列为空", executor.queuedCount() == 0, "queued=" + executor.queuedCount())
    }
    // 8 Generation 旧代失效
    run {
        val gen = OwnerGeneration()
        val initial = gen.isClosed() && !gen.isActive(0)
        val g1 = gen.open()
        val g2 = gen.open()
        val rotated = g2 > g1 && gen.isActive(g2) && !gen.isActive(g1)
        gen.close()
        val dead = gen.isClosed() && !gen.isActive(g1) && !gen.isActive(g2)
        check("8 Generation 旧代失效", initial && rotated && dead,
            "initial=" + initial + " rotated=" + rotated + " dead=" + dead + " g1=" + g1 + " g2=" + g2)
    }
    // Interrupt-resume regressions: exercise the actual pending queue, not only the counter.
    run {
        val calls = AtomicInteger()
        val handler = OwnedHandler({ })
        val queue = android.os.Handler.latest
        val task = Runnable { calls.incrementAndGet() }
        handler.post(task); handler.post(task)
        check("9 repeated task has independent registrations", handler.pendingCount() == 2)
        handler.close()
        check("10 close removes every repeated posting", queue.queuedCount() == 0)
        queue.drain()
        check("11 cancelled postings do not execute", calls.get() == 0)
    }
    run {
        val handler = OwnedHandler({ })
        val queue = android.os.Handler.latest
        val task = Runnable { }
        handler.post(task); handler.postDelayed(task, 99L); handler.remove(task)
        check("12 remove cancels all identical task postings", queue.queuedCount() == 0 && handler.pendingCount() == 0)
        handler.close()
    }
    run {
        val calls = AtomicInteger()
        val handler = OwnedHandler({ })
        val queue = android.os.Handler.latest
        handler.post { calls.incrementAndGet() }
        handler.post { calls.incrementAndGet() }
        queue.drain()
        check("13 active queue executes and releases entries", calls.get() == 2 && handler.pendingCount() == 0)
        handler.close()
    }
    run {
        val handler = OwnedHandler({ throw IllegalStateException("reporter failed") })
        val queue = android.os.Handler.latest
        repeat(3) { handler.post { } }
        queue.failRemove = true
        var escaped = false
        try { handler.close() } catch (_: Throwable) { escaped = true }
        check("14 reporter failure cannot interrupt cleanup", !escaped && queue.removeCalls == 3 && handler.pendingCount() == 0)
        queue.failRemove = false
        queue.drain()
    }
    run {
        val handler = OwnedHandler({ throw IllegalStateException("reporter failed") })
        val queue = android.os.Handler.latest
        queue.failPost = true
        var escaped = false
        var accepted = true
        try { accepted = handler.post { } } catch (_: Throwable) { escaped = true }
        check("15 failed post and failed reporter release entry", !escaped && !accepted && handler.pendingCount() == 0)
        handler.close()
    }
    run {
        val handler = OwnedHandler({ })
        val queue = android.os.Handler.latest
        queue.reject = true
        check("16 rejected post does not retain task", !handler.post { } && handler.pendingCount() == 0)
        handler.close()
    }
    run {
        val errors = AtomicInteger()
        val handler = OwnedHandler({ errors.incrementAndGet() })
        val queue = android.os.Handler.latest
        val calls = AtomicInteger()
        handler.post { error("task failure") }; handler.post { calls.incrementAndGet() }
        queue.drain()
        check("17 task failure cannot block subsequent callback", errors.get() == 1 && calls.get() == 1 && handler.pendingCount() == 0)
        handler.close()
    }
    run {
        val handler = OwnedHandler({ })
        val queue = android.os.Handler.latest
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        queue.postEntered = entered; queue.postRelease = release
        val posting = Thread { handler.post { } }
        val closing = Thread { handler.close() }
        posting.start()
        check("18 posting reached Handler barrier", entered.await(5, TimeUnit.SECONDS))
        closing.start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (closing.isAlive && closing.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield()
        release.countDown(); posting.join(5000); closing.join(5000)
        check("19 close/post race leaves no retained callbacks", !posting.isAlive && !closing.isAlive && queue.queuedCount() == 0 && handler.pendingCount() == 0)
    }
    run {
        val registry = t.mma.hooks.core.HookRegistry.get()
        val previous = registry.installed.size
        val hooks = OwnedHooks { }
        hooks.close()
        val handle = hooks.hook(sample, XC_MethodHook())
        check("20 closed owner refuses native installation", handle == null && registry.installed.size == previous)
    }
    run {
        val registry = t.mma.hooks.core.HookRegistry.get()
        registry.failHook = true
        val hooks = OwnedHooks { throw IllegalStateException("reporter failed") }
        var escaped = false
        try { hooks.hook(sample, XC_MethodHook()) } catch (_: Throwable) { escaped = true }
        registry.failHook = false
        check("21 native installation failure is isolated from reporter", !escaped && hooks.size() == 0)
        hooks.close()
    }
    run {
        val registry = t.mma.hooks.core.HookRegistry.get()
        val hooks = OwnedHooks { }
        registry.beforeHook = { hooks.close() }
        val handle = hooks.hook(sample, XC_MethodHook())
        registry.beforeHook = null
        val native = registry.installed.last()
        check("22 late native handle is released and rejected", handle == null && native.unhookCalls == 1 && hooks.size() == 0)
    }
    run {
        val executor = OwnedExecutor("Hchat-Test-Bounded", { }, 1)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queuedCalls = AtomicInteger()
        executor.submit { started.countDown(); release.await(5, TimeUnit.SECONDS) }
        val running = started.await(5, TimeUnit.SECONDS)
        val queued = executor.submit { queuedCalls.incrementAndGet() }
        val overflow = executor.submit { queuedCalls.incrementAndGet() }
        check("23 executor reports bounded-queue rejection", running && queued && !overflow && executor.queuedCount() == 1)
        executor.close(); release.countDown()
        check("24 close discards queued feature work", executor.queuedCount() == 0 && queuedCalls.get() == 0 && !executor.submit { })
    }
    run {
        val gen = OwnerGeneration()
        val old = gen.open(); gen.close(); val fresh = gen.open()
        check("25 reopen never reactivates closed generation", gen.isActive(fresh) && !gen.isActive(old) && !gen.isClosed())
        gen.close()
    }
    println(if (failures == 0) "OWNERSHIP-OK" else "OWNERSHIP-FAILED=" + failures)
    if (failures != 0) throw IllegalStateException("ownership assertions failed: " + failures)
}

object Sample {
    @JvmStatic fun sampleMethod() {}
}
'''


def compile_kotlin(out: Path, sources, classpath: str):
    cmd = [
        'java', '-Xmx384m', '-cp', ':'.join(map(str, LIBS.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-no-reflect', '-nowarn',
        '-classpath', classpath, '-d', str(out), *map(str, sources),
    ]
    done = subprocess.run(cmd, capture_output=True, text=True, timeout=900)
    if done.returncode:
        print(done.stdout)
        print(done.stderr)
        raise SystemExit(done.returncode)
    return out


def main():
    with tempfile.TemporaryDirectory() as td:
        p = Path(td)
        sdk_dir = p / 'sdk'
        sdk_dir.mkdir()
        for name, body in COMMON.items():
            (sdk_dir / name).write_text(body)
        compile_kotlin(sdk_dir / 'sdkcheck.jar', [SRC, *sdk_dir.glob('*.kt')], f'{std}:{SDK}')
        print('A PASS: FeatureOwnership type-checks against', SDK.name)

        run_dir = p / 'run'
        run_dir.mkdir()
        for name, body in {**COMMON, **RUNTIME}.items():
            (run_dir / name).write_text(body)
        (run_dir / 'Main.kt').write_text(MAIN)
        jar = compile_kotlin(run_dir / 'run.jar', [SRC, *run_dir.glob('*.kt')], str(std))
        done = subprocess.run(
            ['java', '-Xmx256m', '-cp', f'{jar}:{std}', 'MainKt'],
            capture_output=True, text=True, timeout=300,
        )
        print(done.stdout.strip())
        if done.returncode:
            print(done.stderr.strip())
            raise SystemExit(done.returncode)
        if 'OWNERSHIP-OK' not in done.stdout:
            raise SystemExit(1)
    print('PASS: FeatureOwnership compiles and all ownership assertions hold')


if __name__ == '__main__':
    main()