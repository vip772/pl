"""Verify the bounded task executor contract without Gradle or Android runtime."""
from pathlib import Path
import subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/t/mma/hooks/api/runtime/WeChatTaskApi.kt').read_text()
assert 'ThreadPoolExecutor(' in source
assert 'LinkedBlockingQueue<Runnable>(256)' in source
a = source.index('    private val executor = ThreadPoolExecutor(')
b = source.index('    private val closed', a)
executor_source = source[a:b].replace('private val executor', 'val executor')
assert 'Executors.newCachedThreadPool()' not in source
assert 'executor.shutdown()' in source

code = r'''
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
fun log(s: String) {}
fun main() {
    EXECUTOR_SOURCE
    val threads = ConcurrentHashMap.newKeySet<Long>()
    val completed = AtomicInteger()
    val gate = CountDownLatch(1)
    val done = CountDownLatch(96)
    val started = CountDownLatch(2)

    repeat(96) {
        executor.execute {
            threads += Thread.currentThread().id
            started.countDown()
            gate.await(5, TimeUnit.SECONDS)
            completed.incrementAndGet(); done.countDown()
        }
    }
    // Both blocked workers must start before we release either of them.
    check(started.await(3, TimeUnit.SECONDS))
    check(threads.size == 2) { "Second worker did not start: ${threads.size}" }
    // Saturate the queue with the original cancellable futures.
    val cancelled = FutureTask<Unit> { error("cancelled task ran") }
    executor.execute(cancelled)
    repeat(161) { executor.execute {} }
    check(executor.queue.size == 256)
    var rejected = false
    try { executor.execute { error("rejected task ran") } }
    catch (_: RejectedExecutionException) { rejected = true }
    check(rejected)
    cancelled.cancel(false); executor.purge()
    check(executor.queue.size == 255)
    executor.execute {}
    gate.countDown()
    check(done.await(5, TimeUnit.SECONDS))
    check(completed.get() == 96)
    executor.shutdown()
    check(executor.awaitTermination(5, TimeUnit.SECONDS))
    check(executor.isShutdown)
    println("PASS bounded task executor: 96 tasks delivered, max 2 workers, shutdown")
}
'''
code = code.replace('    EXECUTOR_SOURCE', executor_source.replace('    init { executor.allowCoreThreadTimeOut(true) }', '    executor.allowCoreThreadTimeOut(true)'))
libs = Path('/tmp/pl-kotlin-libs')
std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    (p / 'Test.kt').write_text(code)
    cmd = ['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
           'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
           '-classpath', str(std), '-d', str(p / 'test.jar'), str(p / 'Test.kt')]
    subprocess.run(cmd, check=True, timeout=45)
    subprocess.run(['java', '-cp', f'{p / "test.jar"}:{std}', 'TestKt'], check=True, timeout=10)
