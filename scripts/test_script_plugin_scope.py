"""Execute the real script-plugin ownership code with deterministic host fixtures.

Phase A compiles the real SharedHookLease and drives multi-owner semantics:
install happens only on 0 -> 1 owners, uninstall only on 1 -> 0, a failed install
leaves no ghost owner, acquire during close rolls the fresh install back, and
close() drains every still-active key exactly once.

Phase B compiles the real ScriptHookGeneration class (extracted verbatim from
ScriptPluginFeature.kt) together with the real DexInstallScheduler and drives the
enable-interval lifecycle: WARMUP gate, four ticket keys deduplicated, cancelled
retries after close, shared friend-request lease independence from the auto-reply
owner, and the non-swappable close order (tickets -> owned subscriptions ->
runtime -> lease).

Phase C compiles the real ScriptMessageHook and ScriptMemberChangeHook against a
host that records subscribe/unsubscribe and can replay events, proving that the
subscriptions a generation installed are actually handed back on uninstall and
that no dispatch happens afterwards.

Phase D pins the source invariants that cannot be executed off-device.

None of this is an Android/R8 build and none of it touches a real WeChat session.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
STD = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
CP = ':'.join(str(p) for p in sorted(LIBS.glob('*.jar')))
CORE = ROOT / 'app/src/main/java/t/mma/hooks/core'
SCRIPT = ROOT / 'app/src/main/java/t/mma/hooks/items/script'
AUTOREPLY = ROOT / 'app/src/main/java/t/mma/hooks/items/autoreply'

# Mutation runs point PL_OVERLAY at a directory holding replacement copies keyed by
# repo-relative path; production files are never rewritten, so a crashed run cannot
# leave a mutated file behind.
OVERLAY = Path(os.environ['PL_OVERLAY']) if os.environ.get('PL_OVERLAY') else None


def source(rel: str) -> str:
    if OVERLAY is not None:
        candidate = OVERLAY / rel
        if candidate.exists():
            return candidate.read_text(encoding='utf-8')
    return (ROOT / rel).read_text(encoding='utf-8')


def write_group(tmp: Path, group: str, files: dict):
    target = tmp / group
    target.mkdir(parents=True, exist_ok=True)
    written = []
    for name, body in files.items():
        path = target / name
        path.write_text(body, encoding='utf-8')
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
                             capture_output=True, text=True, timeout=240)
        sys.stdout.write(run.stdout)
        if run.returncode != 0:
            sys.stderr.write(run.stderr)
            raise SystemExit('runtime failed: ' + main + ' exit=' + str(run.returncode))


# --------------------------------------------------------------------------------------
# Shared boundaries
# --------------------------------------------------------------------------------------
OS_STUB = '''package android.os

object MainQueue {
    private val tasks = ArrayList<Runnable>()
    @Volatile var reject = false
    @Volatile var rejectDelayed = false
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
    fun reset() { clear(); reject = false; rejectDelayed = false }
    fun add(task: Runnable, delayed: Boolean): Boolean = synchronized(this) {
        if (delayed) { if (rejectDelayed) return false } else { if (reject) return false }
        tasks.add(task)
        true
    }
    fun remove(task: Runnable) { synchronized(this) { tasks.removeAll { it === task } } }
}

class Looper {
    companion object {
        val MAIN = Looper()
        fun getMainLooper(): Looper = MAIN
        fun myLooper(): Looper? = MAIN
    }
}

open class Handler(private val looper: Looper = Looper.getMainLooper()) {
    open fun post(task: Runnable): Boolean = MainQueue.add(task, false)
    open fun postDelayed(task: Runnable, delayMs: Long): Boolean = MainQueue.add(task, true)
    open fun removeCallbacks(task: Runnable) { MainQueue.remove(task) }
    open fun removeCallbacksAndMessages(token: Any?) { MainQueue.clear() }
}

object SystemClock {
    fun elapsedRealtime(): Long = java.lang.System.nanoTime() / 1_000_000L
    fun uptimeMillis(): Long = elapsedRealtime()
    fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) {} }
}
'''

PROBE_STUB = '''package t.mma.hooks.core

object Probe {
    private val entries = ArrayList<String>()
    @Synchronized fun log(entry: String) { entries.add(entry) }
    @Synchronized fun entries(): List<String> = ArrayList(entries)
    @Synchronized fun clear() { entries.clear() }
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

CONTEXT_STUB = '''package android.content

open class Context
'''

FEATURE_CONTEXT_STUB = '''package t.mma.hooks.core

import android.content.Context

class FeatureContext(
    private val loader: ClassLoader,
    private val host: Context = Context(),
    private val dexKit: Any = Any()
) {
    fun hostClassLoader(): ClassLoader = loader
    fun hostContext(): Context = host
    fun dexKitManager(): Any = dexKit
}
'''


# --------------------------------------------------------------------------------------
# Phase A: real SharedHookLease
# --------------------------------------------------------------------------------------
PHASE_A_TEST = '''package t.mma.hooks.core

private val events = ArrayList<String>()

private fun lease(): SharedHookLease = SharedHookLease { message, error ->
    events += "error:" + message + ":" + (error?.javaClass?.simpleName ?: "none")
}

private fun check(value: Boolean, message: () -> String) {
    if (!value) throw AssertionError(message() + " | events=" + events)
}

fun main() {
    // 1. 0 -> 1 安装，1 -> 2 不再安装，2 -> 1 不撤销，1 -> 0 才撤销。
    var installs = 0
    var uninstalls = 0
    var failInstall = false
    val shared = lease()
    shared.register(
        "k",
        install = { installs += 1; !failInstall },
        uninstall = { uninstalls += 1 }
    )
    check(shared.acquire("k", "a")) { "首个持有者必须安装成功" }
    check(installs == 1) { "0 -> 1 必须安装一次，实际=" + installs }
    check(shared.acquire("k", "b")) { "第二个持有者必须取得租约" }
    check(installs == 1) { "1 -> 2 不得重复安装，实际=" + installs }
    check(shared.owners("k") == setOf("a", "b")) { "持有者集合错误: " + shared.owners("k") }
    check(shared.acquire("k", "a")) { "同一持有者重复取得必须幂等" }
    check(shared.ownerCount("k") == 2) { "重复取得不得增加持有者，实际=" + shared.ownerCount("k") }
    shared.release("k", "a")
    check(uninstalls == 0) { "2 -> 1 不得撤销，实际=" + uninstalls }
    check(shared.isActive("k")) { "仍有持有者时租约必须保持活跃" }
    shared.release("k", "b")
    check(uninstalls == 1) { "1 -> 0 必须撤销一次，实际=" + uninstalls }
    check(!shared.isActive("k")) { "无人持有时租约必须非活跃" }

    // 2. 安装失败不留幽灵持有者：失败者被摘名，其他人仍可重试。
    failInstall = true
    val installsBeforeRetry = installs
    check(!shared.acquire("k", "a")) { "安装失败必须返回 false" }
    check(shared.owners("k").isEmpty()) { "安装失败不得留下持有者: " + shared.owners("k") }
    failInstall = false
    check(shared.acquire("k", "b")) { "安装失败后其他持有者必须能重试" }
    check(installs == installsBeforeRetry + 2) {
        "失败一次与重试一次必须各真正调用一次 install，实际增量=" + (installs - installsBeforeRetry)
    }
    shared.release("k", "b")

    // 3. 安装抛异常按失败处理，且上报一次。
    val throwing = lease()
    throwing.register(
        "boom",
        install = { throw IllegalStateException("host refused") },
        uninstall = { events += "boom:uninstall" }
    )
    check(!throwing.acquire("boom", "a")) { "安装抛异常必须返回 false" }
    check(throwing.owners("boom").isEmpty()) { "异常后不得留下持有者" }
    check(events.any { it.startsWith("error:") }) { "安装异常必须上报: " + events }

    // 4. 撤销抛异常不阻断其他 key 与持有者的释放。
    val noisy = lease()
    noisy.register("x", install = { true }, uninstall = { throw IllegalStateException("unhook failed") })
    noisy.register("y", install = { true }, uninstall = { events += "y:uninstall" })
    check(noisy.acquire("x", "a")) { "x 必须安装" }
    check(noisy.acquire("y", "a")) { "y 必须安装" }
    noisy.release("x", "a")
    noisy.release("y", "a")
    check(events.contains("y:uninstall")) { "一个 key 撤销异常不得阻断另一个 key: " + events }

    // 5. 关闭单向幂等：关闭后 acquire 一律 false，未释放的 key 统一撤销一次。
    var closeInstalls = 0
    var closeUninstalls = 0
    val closing = lease()
    closing.register("c", install = { closeInstalls += 1; true }, uninstall = { closeUninstalls += 1 })
    check(closing.acquire("c", "a")) { "关闭前必须能取得租约" }
    closing.close()
    closing.close()
    check(closeUninstalls == 1) { "关闭必须撤销未释放的 key 且只撤销一次，实际=" + closeUninstalls }
    check(!closing.acquire("c", "b")) { "关闭后 acquire 必须返回 false" }
    check(!closing.isActive("c")) { "关闭后租约必须非活跃" }
    // 关闭后再登记同样不得复活，否则旧代会在关闭后重新装回宿主 Hook。
    closing.register("c2", install = { closeInstalls += 1; true }, uninstall = { closeUninstalls += 1 })
    check(!closing.acquire("c2", "a")) { "关闭后新登记 key 的 acquire 必须返回 false" }
    check(closeUninstalls == 1) { "关闭后新登记不得触发撤销，实际=" + closeUninstalls }

    // 6. 安装期间被关闭：不能把 Hook 留给无人持有的进程。
    var lateInstalls = 0
    var lateUninstalls = 0
    val racing = lease()
    racing.register(
        "r",
        install = { lateInstalls += 1; racing.close(); true },
        uninstall = { lateUninstalls += 1 }
    )
    check(!racing.acquire("r", "a")) { "安装期间关闭必须返回 false" }
    check(lateUninstalls == 1) { "安装期间关闭必须就地撤销，实际=" + lateUninstalls }
    check(racing.owners("r").isEmpty()) { "安装期间关闭不得留下持有者: " + racing.owners("r") }

    println("PASS shared hook lease: 0->1 install, 1->0 uninstall, ghost-free failure, "
        + "isolated uninstall errors, idempotent close, close-during-acquire rollback")
}
'''


def phase_a(tmp: Path):
    files = {
        'Os.kt': OS_STUB,
        'Probe.kt': PROBE_STUB,
        'PhaseA.kt': PHASE_A_TEST,
    }
    written = write_group(tmp, 'a', files)
    written.append(CORE / 'SharedHookLease.kt')
    compile_and_run(tmp, 'phase-a', written, ['t.mma.hooks.core.PhaseAKt'])


# --------------------------------------------------------------------------------------
# Phase B: real ScriptHookGeneration + real DexInstallScheduler
# --------------------------------------------------------------------------------------
PHASE_B_FIXTURES = '''package t.mma.hooks.items.script

import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.Probe
import t.mma.hooks.core.SharedHookLease

class ScriptPluginFeature {
    companion object {
        const val ID = "script_plugin"
    }
}

object ScriptPluginRuntime {
    @Volatile var installCalls = 0
    @Volatile var closeCalls = 0
    fun install(context: FeatureContext) {
        installCalls += 1
        Probe.log("runtime:install")
    }
    fun close() {
        closeCalls += 1
        Probe.log("runtime:close")
    }
    fun loadEnabledPluginsWhenReady(context: android.content.Context) {
        Probe.log("runtime:loadPlugins")
    }
}

/**
 * 好友申请监听的宿主边界：内部就是真实租约语义，用来验证脚本退出不会掐断
 * 自动回复仍在持有的监听。
 */
object ScriptNewFriendHook {
    const val OWNER_SCRIPT = "script_plugin"
    const val OWNER_AUTO_REPLY = "auto_reply"
    private val lease = SharedHookLease { message, _ -> Probe.log("lease:error:" + message) }
    init {
        lease.register(
            "shared:new_friend_listener",
            install = { Probe.log("host:friend-install"); !refuseInstall },
            uninstall = { Probe.log("host:friend-uninstall") }
        )
    }
    @Volatile var refuseInstall = false
    /** 取得租约期间的钩子：用于模拟「拿到租约那一刻本代已被关闭」。 */
    @Volatile var onAcquired: (() -> Unit)? = null
    fun acquire(context: FeatureContext, owner: String): Boolean {
        val acquired = lease.acquire("shared:new_friend_listener", owner)
        if (acquired) onAcquired?.invoke()
        return acquired
    }
    fun release(owner: String) { lease.release("shared:new_friend_listener", owner) }
    fun owners(): Set<String> = lease.owners("shared:new_friend_listener")
    fun isHostInstalled(): Boolean = lease.isActive("shared:new_friend_listener")
}

object ScriptMessageHook {
    @Volatile var installs = 0
    @Volatile var uninstalls = 0
    @Volatile var refuse = false
    fun install(context: FeatureContext): Boolean {
        installs += 1
        Probe.log("hook:message-install")
        return !refuse
    }
    fun uninstall() {
        uninstalls += 1
        Probe.log("hook:message-uninstall")
    }
}

object ScriptMemberChangeHook {
    @Volatile var installs = 0
    @Volatile var uninstalls = 0
    fun install(context: FeatureContext): Boolean {
        installs += 1
        Probe.log("hook:member-install")
        return true
    }
    fun uninstall() {
        uninstalls += 1
        Probe.log("hook:member-uninstall")
    }
}

object ScriptMenuDispatcher {
    @Volatile var installs = 0
    @Volatile var refuse = false
    fun install(context: FeatureContext, finder: Any?, log: (String, Throwable?) -> Unit): Boolean {
        installs += 1
        Probe.log("hook:menu-install")
        return !refuse
    }
}

object QuickMarkReadRuntime {
    @Volatile var installs = 0
    fun install(context: FeatureContext) {
        installs += 1
        Probe.log("quickread:install")
    }
}

object FinderMediaDownloadSupport {
    @Volatile var installs = 0
    fun install(context: FeatureContext): Boolean {
        installs += 1
        Probe.log("shared:finder-install")
        return true
    }
}

object ScriptSendButtonHook {
    @Volatile var installs = 0
    fun install(context: FeatureContext): Boolean {
        installs += 1
        Probe.log("shared:send-install")
        return true
    }
}
'''

DEXKIT_STUB = '''package t.mma.dexkit

object SettingsDexFinder {
    @Volatile var resolveCalls = 0
    fun managed(dexKit: Any?, classLoader: ClassLoader, context: android.content.Context): SettingsDexFinder {
        return this
    }
    fun resolveAll(force: Boolean): Boolean {
        resolveCalls += 1
        return true
    }
}
'''

PHASE_B_TEST = '''package t.mma.hooks.items.script

import android.content.Context
import android.os.MainQueue
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.Probe

private val loader: ClassLoader = Thread.currentThread().contextClassLoader!!
private val reports = ArrayList<String>()

private fun context(): FeatureContext = FeatureContext(loader, Context())

private fun generation(): ScriptHookGeneration =
    ScriptHookGeneration(context()) { message, error ->
        reports += "report:" + message + ":" + (error?.javaClass?.simpleName ?: "none")
    }

private fun count(entry: String): Int = Probe.entries().count { it == entry }

private fun indexOf(entry: String): Int = Probe.entries().indexOf(entry)

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
    error("等待超时: " + message + " | probe=" + Probe.entries())
}

private fun check(value: Boolean, message: () -> String) {
    if (!value) throw AssertionError(message() + " | probe=" + Probe.entries() + " | reports=" + reports)
}

fun main() {
    MainQueue.reset()
    Probe.clear()

    // 1. WARMUP 门：start() 只建票据与运行时，DexKit 就绪前一个 Hook 都不许装。
    val gated = generation()
    gated.start()
    settle(300)
    check(count("runtime:install") == 1) { "启动必须安装本代运行时" }
    check(count("quickread:install") == 1) { "启动必须幂等激活快捷已读入口" }
    check(count("hook:menu-install") == 0) { "WARMUP 前不得安装菜单 Hook" }
    check(count("hook:message-install") == 0) { "WARMUP 前不得安装消息监听" }
    check(count("hook:member-install") == 0) { "WARMUP 前不得安装成员变动监听" }
    check(count("host:friend-install") == 0) { "WARMUP 前不得安装好友申请监听" }
    gated.close()
    settle(150)
    check(count("runtime:close") == 1) { "关闭必须关闭本代运行时" }

    // 2. 就绪后四张票据各装一次；重复调度必须被票据去重。
    DexInstallScheduler.markDexWarmupReady()
    val steady = generation()
    steady.start()
    await("四项安装全部完成") {
        count("hook:menu-install") == 1 && count("hook:message-install") == 1 &&
            count("hook:member-install") == 1 && count("host:friend-install") == 1
    }
    repeat(5) { steady.onDexReady() }
    settle(300)
    check(count("hook:menu-install") == 1) { "重复调度不得重复安装菜单，实际=" + count("hook:menu-install") }
    check(count("hook:message-install") == 1) { "重复调度不得重复安装消息监听，实际=" + count("hook:message-install") }
    check(count("hook:member-install") == 1) { "重复调度不得重复安装成员变动，实际=" + count("hook:member-install") }
    check(count("host:friend-install") == 1) { "重复调度不得重复安装好友申请监听，实际=" + count("host:friend-install") }
    check(ScriptNewFriendHook.owners() == setOf(ScriptNewFriendHook.OWNER_SCRIPT)) {
        "租约持有者必须只有脚本插件: " + ScriptNewFriendHook.owners()
    }

    // 3. 共享租约：自动回复同时持有时，脚本退出不得掐断宿主监听。
    check(ScriptNewFriendHook.acquire(context(), ScriptNewFriendHook.OWNER_AUTO_REPLY)) {
        "自动回复必须能取得同一监听"
    }
    check(ScriptNewFriendHook.owners() == setOf(
        ScriptNewFriendHook.OWNER_SCRIPT, ScriptNewFriendHook.OWNER_AUTO_REPLY
    )) { "两个持有者必须同时在场: " + ScriptNewFriendHook.owners() }
    steady.close()
    settle(200)
    check(count("host:friend-uninstall") == 0) { "脚本退出时自动回复仍持有，不得撤销宿主监听" }
    check(ScriptNewFriendHook.isHostInstalled()) { "脚本退出后宿主监听必须仍然活跃" }
    check(ScriptNewFriendHook.owners() == setOf(ScriptNewFriendHook.OWNER_AUTO_REPLY)) {
        "脚本退出后租约必须只剩自动回复: " + ScriptNewFriendHook.owners()
    }
    ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_AUTO_REPLY)
    check(count("host:friend-uninstall") == 1) { "最后一个持有者离开必须撤销宿主监听，实际=" + count("host:friend-uninstall") }

    // 4. 关闭顺序：票据 -> 本代订阅 -> 运行时 -> 租约，四步不可交换。
    val ordered = generation()
    ordered.start()
    await("顺序用例安装完成") { count("host:friend-install") == 2 }
    val baseRuntimeClose = count("runtime:close")
    val baseMessageUninstall = count("hook:message-uninstall")
    val baseMemberUninstall = count("hook:member-uninstall")
    val baseFriendUninstall = count("host:friend-uninstall")
    ordered.close()
    settle(200)
    check(count("runtime:close") == baseRuntimeClose + 1) { "关闭必须关闭本代运行时" }
    check(count("hook:message-uninstall") == baseMessageUninstall + 1) { "关闭必须撤销本代消息订阅" }
    check(count("hook:member-uninstall") == baseMemberUninstall + 1) { "关闭必须撤销本代成员变动订阅" }
    check(count("host:friend-uninstall") == baseFriendUninstall + 1) { "关闭必须归还好友申请租约" }
    // 探针日志里有早前几代的关闭记录，本次关闭必须取最后一次出现的下标。
    val runtimeAt = Probe.entries().lastIndexOf("runtime:close")
    val messageAt = Probe.entries().lastIndexOf("hook:message-uninstall")
    val memberAt = Probe.entries().lastIndexOf("hook:member-uninstall")
    val friendAt = Probe.entries().lastIndexOf("host:friend-uninstall")
    check(messageAt in 0 until runtimeAt) { "消息订阅必须在运行时关闭之前撤销: " + messageAt + " vs " + runtimeAt }
    check(memberAt in 0 until runtimeAt) { "成员变动订阅必须在运行时关闭之前撤销: " + memberAt + " vs " + runtimeAt }
    check(friendAt > runtimeAt) { "好友申请租约必须在运行时关闭之后归还: " + friendAt + " vs " + runtimeAt }

    // 5. 安装失败进入票据重试；关闭后重试必须停掉，不得再有安装尝试。
    ScriptMenuDispatcher.refuse = true
    val failing = generation()
    // 计数必须相对本用例的起点：前面几代已经装过菜单，绝对阈值会让 await 立刻返回，
    // 把「本代首次尝试」漏在断言之外。
    val menuBase = count("hook:menu-install")
    failing.start()
    await("首次菜单安装尝试") { count("hook:menu-install") > menuBase }
    val before = count("hook:menu-install")
    failing.close()
    // DexInstallScheduler 的取消语义明确不中断已经在执行器里跑的那一次安装，
    // 所以先让在途尝试落地，再取快照，之后跨过两个重试周期（1000ms / 3000ms）
    // 断言计数不再增长——这才是「关闭后不得再起新重试」的可观察判据。
    settle(400)
    val settled = count("hook:menu-install")
    settle(3500)
    check(count("hook:menu-install") == settled) {
        "关闭后不得再重试安装，实际=" + before + "->" + settled + "->" + count("hook:menu-install")
    }
    ScriptMenuDispatcher.refuse = false

    // 6. close() 幂等：重复关闭只关闭一次运行时、只撤销一次订阅。
    val idempotent = generation()
    idempotent.start()
    await("幂等用例安装完成") { ScriptMessageHook.installs >= 2 }
    val runtime0 = count("runtime:close")
    val message0 = count("hook:message-uninstall")
    idempotent.close()
    idempotent.close()
    settle(150)
    check(count("runtime:close") == runtime0 + 1) {
        "重复关闭只许关闭一次运行时，实际增量=" + (count("runtime:close") - runtime0)
    }
    check(count("hook:message-uninstall") == message0 + 1) {
        "重复关闭只许撤销一次订阅，实际增量=" + (count("hook:message-uninstall") - message0)
    }

    // 6b. 取得租约的那一刻本代被关闭：必须立刻把租约还回去，不能把 OWNER_SCRIPT
    //     留在共享监听上（否则自动回复退出后宿主监听也撤不掉）。
    val racing = generation()
    val racingOwnersBefore = ScriptNewFriendHook.owners()
    val racingHostBefore = count("host:friend-uninstall")
    ScriptNewFriendHook.onAcquired = { racing.close() }
    racing.start()
    await("取得租约期间被关闭") { count("host:friend-uninstall") > racingHostBefore }
    ScriptNewFriendHook.onAcquired = null
    check(ScriptNewFriendHook.owners() == racingOwnersBefore) {
        "关闭期间取得的租约必须立即归还，实际持有者=" + ScriptNewFriendHook.owners()
    }
    check(ScriptNewFriendHook.owners().isEmpty()) { "归还后不得残留脚本持有者" }
    check(!ScriptNewFriendHook.isHostInstalled()) { "最后一个持有者离开后宿主监听必须撤掉" }
    val orphan = generation()
    val runtimeBefore = count("runtime:install")
    val runtimeCloseBefore = count("runtime:close")
    orphan.close()
    orphan.start()
    settle(300)
    check(count("runtime:install") == runtimeBefore) { "已关闭的代不得启动运行时" }
    check(count("runtime:close") == runtimeCloseBefore) { "已关闭的代不得触发额外关闭" }

    // 8. 重开新代：上一代被取消的票据键必须能被新一代重新登记并安装。
    val reopened = generation()
    val menuBefore = count("hook:menu-install")
    reopened.start()
    await("重开新代安装完成") { count("hook:menu-install") > menuBefore }
    reopened.close()
    settle(150)
    check(reports.none { it.startsWith("report:") }) { "正常路径不得上报失败: " + reports }

    println("PASS script hook generation: WARMUP gate, four-ticket dedup, cancelled retry on "
        + "close, shared friend-request lease independence, close ordering, idempotent close, "
        + "closed-generation start refusal, clean reopen")
}
'''


def phase_b(tmp: Path):
    raw = source('app/src/main/java/t/mma/hooks/items/script/ScriptPluginFeature.kt')
    start = raw.index('internal class ScriptHookGeneration(')
    # 从代际类一直切到文件末尾：连带真实的 ScriptSharedDexKeys，验证真实键名与真实转发，
    # 而不是另写一份可能与生产漂移的桩。
    generation = raw[start:].rstrip()
    if not generation.endswith('}'):
        raise SystemExit('generation slice is not a closed class')
    wrapper = ('package t.mma.hooks.items.script\n\n'
               + 'import t.mma.dexkit.SettingsDexFinder\n'
               + 'import t.mma.hooks.core.DexInstallScheduler\n'
               + 'import t.mma.hooks.core.FeatureContext\n\n'
               + generation + '\n')
    files = {
        'Os.kt': OS_STUB,
        'Probe.kt': PROBE_STUB,
        'HLog.kt': HLOG_STUB,
        'Context.kt': CONTEXT_STUB,
        'FeatureContext.kt': FEATURE_CONTEXT_STUB,
        'DexKit.kt': DEXKIT_STUB,
        'Fixtures.kt': PHASE_B_FIXTURES,
        'Generation.kt': wrapper,
        'PhaseB.kt': PHASE_B_TEST,
    }
    written = write_group(tmp, 'b', files)
    written.append(CORE / 'DexInstallScheduler.kt')
    written.append(CORE / 'SharedHookLease.kt')
    compile_and_run(tmp, 'phase-b', written, ['t.mma.hooks.items.script.PhaseBKt'])


# --------------------------------------------------------------------------------------
# Phase C: real ScriptMessageHook + real ScriptMemberChangeHook
# --------------------------------------------------------------------------------------
PHASE_C_HOST = '''package t.mma.hooks.api.core

import t.mma.hooks.api.contact.WeChatChatroomChangeApi
import t.mma.hooks.api.message.WeChatMessageChangeApi
import t.mma.hooks.api.message.WeChatMessageObserveApi

object WeChatApis {
    @Volatile var messageChangeApi: WeChatMessageChangeApi? = null
    @Volatile var chatroomChangeApi: WeChatChatroomChangeApi? = null
    @Volatile var observeApi: WeChatMessageObserveApi? = null

    fun message(): MessageGroup = MessageGroup()
    fun chatroomChanges(): WeChatChatroomChangeApi? = chatroomChangeApi
    fun messageObserve(): WeChatMessageObserveApi? = observeApi
    fun contact(): ContactGroup = ContactGroup()

    class MessageGroup {
        fun changes(): WeChatMessageChangeApi? = messageChangeApi
        fun store(): StoreApi? = null
    }

    class ContactGroup {
        fun chatrooms(): ChatroomApi? = null
        fun contacts(): ContactApi? = null
    }

    interface StoreApi {
        fun getMessageById(id: Long): Any?
        fun getMessageBySvrId(svrId: Long): Any?
        fun getMessageBySvrId(talker: String, svrId: Long): Any?
    }

    interface ChatroomApi {
        class Room(val chatroomId: String, val memberIds: List<String>, val rawDisplayNames: String = "")
        fun getChatrooms(): List<Room>
        fun getMemberIds(room: String): List<String>?
        fun getMemberDisplayName(room: String, member: String): String?
    }

    interface ContactApi {
        fun getDisplayName(wxid: String): String?
        fun getGroupMemberRoomDisplayNames(room: String): Map<String, String>?
    }
}
'''

PHASE_C_MESSAGE_API = '''package t.mma.hooks.api.message

class WeChatMessageChangeApi {
    fun interface Listener {
        fun onMessageChanged(change: MessageChange)
    }

    class MessageChange(val message: Any?)

    inner class Subscription internal constructor(private val listener: Listener) {
        @Volatile private var active = true
        fun unsubscribe() {
            if (!active) return
            active = false
            listeners.remove(listener)
            t.mma.hooks.core.Probe.log("api:message-unsubscribe")
        }
        fun isActive(): Boolean = active
    }

    private val listeners = ArrayList<Listener>()
    @Volatile var installed = false
    @Volatile var available = true
    @Volatile var refuseSubscribe = false

    fun isAvailable(): Boolean = available
    val isInstalled: Boolean get() = installed

    fun install() { installed = true }

    @Synchronized
    fun subscribe(listener: Listener): Subscription? {
        if (refuseSubscribe) return null
        listeners.add(listener)
        t.mma.hooks.core.Probe.log("api:message-subscribe")
        return Subscription(listener)
    }

    fun unsubscribe(listener: Listener) {
        listeners.remove(listener)
        t.mma.hooks.core.Probe.log("api:message-unsubscribe-listener")
    }

    fun listenerCount(): Int = listeners.size

    fun emit(message: Any?) {
        for (listener in ArrayList(listeners)) listener.onMessageChanged(MessageChange(message))
    }
}
'''

PHASE_C_OBSERVE_API = '''package t.mma.hooks.api.message

class WeChatMessageObserveApi {
    fun interface Listener {
        fun onObservedMessage(message: Any?)
    }

    inner class Subscription internal constructor(private val listener: Listener) {
        @Volatile private var active = true
        fun unsubscribe() {
            if (!active) return
            active = false
            listeners.remove(listener)
            t.mma.hooks.core.Probe.log("api:observe-unsubscribe")
        }
        fun isActive(): Boolean = active
    }

    private val listeners = ArrayList<Listener>()
    @Volatile var available = true

    fun isAvailable(): Boolean = available
    fun install() {}

    @Synchronized
    fun subscribe(listener: Listener): Subscription? {
        listeners.add(listener)
        t.mma.hooks.core.Probe.log("api:observe-subscribe")
        return Subscription(listener)
    }

    fun unsubscribe(listener: Listener) {
        listeners.remove(listener)
        t.mma.hooks.core.Probe.log("api:observe-unsubscribe-listener")
    }

    fun listenerCount(): Int = listeners.size

    fun emit(message: Any?) {
        for (listener in ArrayList(listeners)) listener.onObservedMessage(message)
    }
}
'''

PHASE_C_CHATROOM_API = '''package t.mma.hooks.api.contact

class WeChatChatroomChangeApi {
    fun interface Listener {
        fun onChatroomChanged(change: ChatroomChange)
    }

    class ChatroomChange(
        private val room: String,
        private val members: List<String> = listOf("a@openim")
    ) {
        class Chatroom(val memberIds: List<String>, val rawDisplayNames: String = "")

        fun mayMemberListChanged(): Boolean = true
        fun chatroomId(): String = room
        val chatroom: Chatroom = Chatroom(members)
    }

    inner class Subscription internal constructor(private val listener: Listener) {
        @Volatile private var active = true
        fun unsubscribe() {
            if (!active) return
            active = false
            listeners.remove(listener)
            t.mma.hooks.core.Probe.log("api:chatroom-unsubscribe")
        }
        fun isActive(): Boolean = active
    }

    private val listeners = ArrayList<Listener>()
    @Volatile var available = true

    fun isAvailable(): Boolean = available
    fun install() {}

    @Synchronized
    fun subscribe(listener: Listener): Subscription? {
        listeners.add(listener)
        t.mma.hooks.core.Probe.log("api:chatroom-subscribe")
        return Subscription(listener)
    }

    fun unsubscribe(listener: Listener) {
        listeners.remove(listener)
        t.mma.hooks.core.Probe.log("api:chatroom-unsubscribe-listener")
    }

    fun listenerCount(): Int = listeners.size

    fun emit(change: ChatroomChange) {
        for (listener in ArrayList(listeners)) listener.onChatroomChanged(change)
    }
}
'''

PHASE_C_BEAN = '''package t.mma.hooks.items.script

class ScriptMessageBean(private val raw: Any?) {
    fun getMsgId(): Long = 0L
    fun getMsgSvrId(): Long = 0L
    fun getTalker(): String = ""
    fun getSender(): String? = null
    fun getMsgType(): String? = null
    fun getContent(): String = ""
    fun getXml(): String = ""
    fun getMsgSource(): String = ""
    fun isSend(): Boolean = false
    fun isImage(): Boolean = false
    fun isVideo(): Boolean = false
    fun isVideoNumberVideo(): Boolean = false
    fun isSystem(): Boolean = false
    fun isGroupChat(): Boolean = false
}
'''

PHASE_C_RUNTIME = '''package t.mma.hooks.items.script

object ScriptPluginRuntime {
    @Volatile var handleCallbacks = true
    @Volatile var imageCallbacks = false
    @Volatile var videoCallbacks = false
    @Volatile var finderCallbacks = false
    @Volatile var dispatched = 0
    @Volatile var memberDispatched = 0

    fun hasHandleMsgCallbacks(): Boolean = handleCallbacks
    fun hasImageDownloadCallback(): Boolean = imageCallbacks
    fun hasVideoDownloadCallback(): Boolean = videoCallbacks
    fun hasFinderMediaDownloadCallback(): Boolean = finderCallbacks
    fun captureHandleMsgDispatch(msgInfoBean: ScriptMessageBean): Runnable? = null
    fun dispatchOnImageDownload(message: ScriptMessageBean) {}
    fun dispatchOnVideoDownload(message: ScriptMessageBean) {}
    fun dispatchOnFinderMediaDownload(message: ScriptMessageBean) {}
    fun dispatchOnMemberChange(type: String, groupWxid: String, userWxid: String, userName: String) {
        memberDispatched += 1
    }
}

object FinderMediaDownloadSupport {
    fun extractMedia(content: String?): String? = null
}
'''

PHASE_C_SHORTVIDEO = '''package t.mma.hooks.items.shortvideo

object FinderMediaDownloadSupport {
    fun extractMedia(content: String?): String? = null
}
'''

PHASE_C_TEST = '''package t.mma.hooks.items.script

import android.content.Context
import t.mma.hooks.api.contact.WeChatChatroomChangeApi
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.message.WeChatMessageChangeApi
import t.mma.hooks.api.message.WeChatMessageObserveApi
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.Probe

private val loader: ClassLoader = Thread.currentThread().contextClassLoader!!
private val context = FeatureContext(loader, Context())

private fun count(entry: String): Int = Probe.entries().count { it == entry }

private fun check(value: Boolean, message: () -> String) {
    if (!value) throw AssertionError(message() + " | probe=" + Probe.entries())
}

fun main() {
    Probe.clear()

    // 1. 消息监听：安装登记一个句柄，重复安装不得重复订阅。
    val messageApi = WeChatMessageChangeApi()
    WeChatApis.messageChangeApi = messageApi
    check(ScriptMessageHook.install(context)) { "消息监听必须安装成功" }
    check(count("api:message-subscribe") == 1) { "必须订阅一次，实际=" + count("api:message-subscribe") }
    check(messageApi.listenerCount() == 1) { "宿主侧必须只有一个监听，实际=" + messageApi.listenerCount() }
    check(ScriptMessageHook.install(context)) { "重复安装必须幂等返回 true" }
    check(count("api:message-subscribe") == 1) { "重复安装不得重复订阅，实际=" + count("api:message-subscribe") }

    // 2. 撤销：句柄与监听都必须还回，之后宿主再发事件不得进入脚本。
    ScriptMessageHook.uninstall()
    check(messageApi.listenerCount() == 0) { "撤销后宿主侧不得残留监听，实际=" + messageApi.listenerCount() }
    check(count("api:message-unsubscribe") == 1) { "必须调用订阅句柄的 unsubscribe，实际=" + count("api:message-unsubscribe") }
    messageApi.emit(null)
    check(ScriptPluginRuntime.dispatched == 0) { "撤销后不得再分发消息" }
    ScriptMessageHook.uninstall()
    check(count("api:message-unsubscribe") == 1) { "重复撤销必须幂等，实际=" + count("api:message-unsubscribe") }

    // 3. 撤销后重装：新一代必须能重新订阅，且旧代句柄必须已被丢弃
    //    （否则下一次 uninstall 会把已经失效的旧句柄再关一遍，且列表无限增长）。
    check(ScriptMessageHook.install(context)) { "撤销后必须能重新安装" }
    check(count("api:message-subscribe") == 2) { "重装必须重新订阅，实际=" + count("api:message-subscribe") }
    ScriptMessageHook.uninstall()
    check(messageApi.listenerCount() == 0) { "再次撤销后宿主侧必须清空" }
    check(count("api:message-unsubscribe") == 2) {
        "重装后的撤销必须只还回本代那一个句柄，实际=" + count("api:message-unsubscribe")
    }
    // 每次撤销只能还回本代登记的那一个监听：若 uninstall 不清空登记表，第二次撤销会把
    // 上一代已失效的监听和句柄一起再还一遍（句柄自身幂等，会在监听解绑次数上露馅）。
    check(count("api:message-unsubscribe-listener") == 2) {
        "每次撤销只许解绑本代那一个监听，实际=" + count("api:message-unsubscribe-listener")
    }

    // 4. 宿主拒绝订阅时安装失败，且不得留下已安装标记。
    messageApi.refuseSubscribe = true
    check(!ScriptMessageHook.install(context)) { "宿主拒绝订阅时安装必须失败" }
    check(count("api:message-subscribe") == 2) { "被拒绝时不得产生订阅，实际=" + count("api:message-subscribe") }
    messageApi.refuseSubscribe = false

    // 5. 成员变动监听：群成员订阅与消息观察订阅都必须登记并可撤销。
    val chatroomApi = WeChatChatroomChangeApi()
    val observeApi = WeChatMessageObserveApi()
    WeChatApis.chatroomChangeApi = chatroomApi
    WeChatApis.observeApi = observeApi
    check(ScriptMemberChangeHook.install(context)) { "成员变动监听必须安装成功" }
    check(count("api:chatroom-subscribe") == 1) { "必须订阅群成员变动，实际=" + count("api:chatroom-subscribe") }
    check(count("api:observe-subscribe") == 1) { "必须订阅消息观察，实际=" + count("api:observe-subscribe") }
    check(ScriptMemberChangeHook.install(context)) { "重复安装必须幂等返回 true" }
    check(count("api:chatroom-subscribe") == 1) { "重复安装不得重复订阅群成员变动" }
    check(count("api:observe-subscribe") == 1) { "重复安装不得重复订阅消息观察" }

    // 群成员事件：首次只建立基线不下发，成员集合发生变化才下发 join/left。
    val memberBefore = ScriptPluginRuntime.memberDispatched
    chatroomApi.emit(WeChatChatroomChangeApi.ChatroomChange("room@chatroom"))
    check(ScriptPluginRuntime.memberDispatched == memberBefore) {
        "首次群成员事件只建立基线，不得下发，实际=" + ScriptPluginRuntime.memberDispatched
    }
    chatroomApi.emit(
        WeChatChatroomChangeApi.ChatroomChange("room@chatroom", listOf("a@openim", "b@openim"))
    )
    check(ScriptPluginRuntime.memberDispatched > memberBefore) {
        "成员集合变化必须下发成员事件，实际=" + ScriptPluginRuntime.memberDispatched
    }

    ScriptMemberChangeHook.uninstall()
    check(chatroomApi.listenerCount() == 0) { "撤销后不得残留群成员监听，实际=" + chatroomApi.listenerCount() }
    check(observeApi.listenerCount() == 0) { "撤销后不得残留消息观察监听，实际=" + observeApi.listenerCount() }
    check(count("api:chatroom-unsubscribe") == 1) { "必须调用群成员订阅句柄的 unsubscribe，实际=" + count("api:chatroom-unsubscribe") }
    check(count("api:observe-unsubscribe") == 1) { "必须调用消息观察订阅句柄的 unsubscribe，实际=" + count("api:observe-unsubscribe") }
    val afterUninstall = ScriptPluginRuntime.memberDispatched
    chatroomApi.emit(WeChatChatroomChangeApi.ChatroomChange("room@chatroom"))
    observeApi.emit(null)
    check(ScriptPluginRuntime.memberDispatched == afterUninstall) { "撤销后不得再分发成员事件" }
    ScriptMemberChangeHook.uninstall()
    check(count("api:chatroom-unsubscribe") == 1) { "重复撤销必须幂等，实际=" + count("api:chatroom-unsubscribe") }

    // 6. 旧代成员基线不得跨代沿用：卸载后重装，同一群首位事件必须重新建基线而不是
    //    与上一代的成员集合做差（否则会凭空复报一次加入）。
    check(ScriptMemberChangeHook.install(context)) { "卸载后必须能重新安装成员变动监听" }
    val rereadBefore = ScriptPluginRuntime.memberDispatched
    chatroomApi.emit(WeChatChatroomChangeApi.ChatroomChange("room@chatroom"))
    check(ScriptPluginRuntime.memberDispatched == rereadBefore) {
        "重装后的首个群事件必须重新建基线，实际复报=" +
            (ScriptPluginRuntime.memberDispatched - rereadBefore)
    }
    ScriptMemberChangeHook.uninstall()

    // 7. 群成员 API 不可用时安装失败且不订阅任何东西。
    chatroomApi.available = false
    val chatroomBefore = count("api:chatroom-subscribe")
    val observeBefore = count("api:observe-subscribe")
    check(!ScriptMemberChangeHook.install(context)) { "群成员 API 不可用时安装必须失败" }
    check(count("api:chatroom-subscribe") == chatroomBefore) { "不可用时不得订阅群成员变动" }
    check(count("api:observe-subscribe") == observeBefore) { "不可用时不得订阅消息观察" }
    chatroomApi.available = true

    println("PASS script hook subscriptions: single subscribe per install, handle plus listener "
        + "released on uninstall, no dispatch after uninstall, idempotent uninstall, "
        + "refused subscribe fails the install, unavailable chatroom api installs nothing")
}
'''


def phase_c(tmp: Path):
    files = {
        'Probe.kt': PROBE_STUB,
        'HLog.kt': HLOG_STUB,
        'Os.kt': OS_STUB,
        'Context.kt': CONTEXT_STUB,
        'FeatureContext.kt': FEATURE_CONTEXT_STUB,
        'Apis.kt': PHASE_C_HOST,
        'MessageChangeApi.kt': PHASE_C_MESSAGE_API,
        'ObserveApi.kt': PHASE_C_OBSERVE_API,
        'ChatroomChangeApi.kt': PHASE_C_CHATROOM_API,
        'Bean.kt': PHASE_C_BEAN,
        'Runtime.kt': PHASE_C_RUNTIME,
        'ShortVideo.kt': PHASE_C_SHORTVIDEO,
        'PhaseC.kt': PHASE_C_TEST,
    }
    written = write_group(tmp, 'c', files)
    written.append(SCRIPT / 'ScriptMessageHook.kt')
    written.append(SCRIPT / 'ScriptMemberChangeHook.kt')
    compile_and_run(tmp, 'phase-c', written, ['t.mma.hooks.items.script.PhaseCKt'])


# --------------------------------------------------------------------------------------
# Phase D: source invariants
# --------------------------------------------------------------------------------------
def phase_d():
    feature = source('app/src/main/java/t/mma/hooks/items/script/ScriptPluginFeature.kt')
    newfriend = source('app/src/main/java/t/mma/hooks/items/script/ScriptNewFriendHook.kt')
    message = source('app/src/main/java/t/mma/hooks/items/script/ScriptMessageHook.kt')
    member = source('app/src/main/java/t/mma/hooks/items/script/ScriptMemberChangeHook.kt')
    autoreply = source('app/src/main/java/t/mma/hooks/items/autoreply/AutoReplyFeature.kt')
    lease = source('app/src/main/java/t/mma/hooks/core/SharedHookLease.kt')

    def need(text, needle, label):
        if needle not in text:
            raise SystemExit('MISSING ' + label + ': ' + needle)

    def forbid(text, needle, label):
        if needle in text:
            raise SystemExit('FORBIDDEN ' + label + ': ' + needle)

    # 四个功能私有安装键必须用票据，不得再走无所有者的 schedule。
    for key in ('menus', 'message', 'new_friend', 'member_change'):
        need(feature, 'DexInstallScheduler.installation("$ID:' + key + '"', 'ticket for ' + key)
        forbid(feature, 'DexInstallScheduler.schedule("$ID:' + key + '"', 'bare schedule for ' + key)
    need(feature, 'internal class ScriptHookGeneration(', 'generation owner')
    need(feature, 'created.forEach(::closeTicket)', 'orphan ticket rollback')
    need(feature, 'if (!accepted)', 'closed-during-start guard')
    need(feature, 'ScriptNewFriendHook.acquire(context, ScriptNewFriendHook.OWNER_SCRIPT)', 'script lease acquire')
    need(feature, 'ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_SCRIPT)', 'script lease release')
    need(feature, 'ScriptMessageHook.uninstall()', 'message unsubscribe')
    need(feature, 'ScriptMemberChangeHook.uninstall()', 'member unsubscribe')
    need(feature, 'trackResource {', 'fallback release')
    need(feature, 'created.close()', 'fallback close call')

    # 关闭顺序：票据 -> 订阅 -> 运行时 -> 租约。
    close_at = feature.index('fun close() {', feature.index('internal class ScriptHookGeneration('))
    body = feature[close_at:]
    order = [
        body.index('taken.first.forEach(::closeTicket)'),
        body.index('uninstallOwnedHooks()'),
        body.index('ScriptPluginRuntime.close()'),
        body.index('ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_SCRIPT)'),
    ]
    if order != sorted(order):
        raise SystemExit('ORDER: close must cancel tickets, uninstall owned hooks, close the runtime, then release the lease')

    # 好友申请监听必须是租约，不得再有进程级 installed 标志。
    forbid(newfriend, 'private var installed = false', 'global installed flag in ScriptNewFriendHook')
    need(newfriend, 'lease.register(', 'lease registration')
    need(newfriend, 'fun acquire(context: FeatureContext, owner: String): Boolean', 'acquire')
    need(newfriend, 'fun release(owner: String)', 'release')
    need(newfriend, 'hostSubscriptions += AutoCloseable { subscription.unsubscribe() }', 'database subscription handle')
    need(newfriend, 'hostSubscriptions += AutoCloseable { context.eventBus().unsubscribe(subscription) }', 'event bus fallback handle')
    need(newfriend, 'private fun uninstallHostListeners()', 'host listener teardown')

    # 两个本代 Hook 必须保存句柄并成对撤销，不得留裸 lambda 订阅。
    for name, text in (('ScriptMessageHook', message), ('ScriptMemberChangeHook', member)):
        need(text, 'private val hostSubscriptions = ArrayList<AutoCloseable>()', name + ' subscription store')
        need(text, 'fun uninstall()', name + ' uninstall')
        forbid(text, 'changeApi.subscribe {', name + ' bare changeApi.subscribe lambda')
        forbid(text, 'observeApi.subscribe {', name + ' bare observeApi.subscribe lambda')
    need(message, 'changeApi.unsubscribe(listener)', 'message api-level unsubscribe')
    need(message, 'subscription.unsubscribe()', 'message handle unsubscribe')
    need(member, 'changeApi.unsubscribe(changeListener)', 'chatroom api-level unsubscribe')
    need(member, 'observeApi.unsubscribe(observeListener)', 'observe api-level unsubscribe')

    # 自动回复必须与脚本共用租约，不得再调用全局 install。
    forbid(autoreply, 'ScriptNewFriendHook.install(context)', 'auto reply global install')
    need(autoreply, 'ScriptNewFriendHook.acquire(context, ScriptNewFriendHook.OWNER_AUTO_REPLY)', 'auto reply acquire')
    need(autoreply, 'resources.register { ScriptNewFriendHook.release(ScriptNewFriendHook.OWNER_AUTO_REPLY) }',
         'auto reply release registration')

    # 租约原语本身：只有 1 -> 0 才撤销，关闭幂等。
    need(lease, 'if (entry.owners.isNotEmpty() || !entry.active) return', 'release gate')
    need(lease, 'if (closed) return', 'idempotent close')
    need(lease, 'entry.owners.clear()', 'close drains owners')

    print('PASS source invariants: four ticketed install keys, generation owner with fallback '
          'release, close order tickets -> subscriptions -> runtime -> lease, friend listener '
          'lease with no global flag, both hooks store and release handles, auto reply shares '
          'the same lease')
    print('feature lines: ' + str(len(feature.splitlines())))
    print('newfriend lines: ' + str(len(newfriend.splitlines())))
    print('message lines: ' + str(len(message.splitlines())))
    print('member lines: ' + str(len(member.splitlines())))


def main():
    if not LIBS.exists():
        raise SystemExit('missing kotlin libs: ' + str(LIBS))
    with tempfile.TemporaryDirectory() as raw:
        tmp = Path(raw)
        print('--- phase A: shared hook lease ---')
        phase_a(tmp)
        print('--- phase B: script hook generation ---')
        phase_b(tmp)
        print('--- phase C: hook subscription ownership ---')
        phase_c(tmp)
    print('--- phase D: source invariants ---')
    phase_d()
    print('ALL PHASES PASSED')


if __name__ == '__main__':
    main()
