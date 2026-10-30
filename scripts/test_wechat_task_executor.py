"""Verify the bounded task executor contract without Gradle or Android runtime."""
from pathlib import Path
import subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/t/mma/hooks/api/runtime/WeChatTaskApi.kt').read_text()
assert 'ThreadPoolExecutor(' in source
assert 'LinkedBlockingQueue()' in source
assert 'Executors.newCachedThreadPool()' not in source
assert 'executor.shutdownNow()' in source

code = r'''
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
fun main() {
    val executor = ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS, LinkedBlockingQueue())
    val threads = ConcurrentHashMap.newKeySet<Long>()
    val completed = AtomicInteger()
    val gate = CountDownLatch(1)
    val done = CountDownLatch(96)
    repeat(96) {
        executor.execute {
            threads += Thread.currentThread().id
            gate.await(5, TimeUnit.SECONDS)
            completed.incrementAndGet(); done.countDown()
        }
    }
    check(threads.size <= 2)
    gate.countDown()
    check(done.await(5, TimeUnit.SECONDS))
    check(completed.get() == 96)
    executor.shutdownNow()
    check(executor.isShutdown)
    println("PASS bounded task executor: 96 tasks delivered, max 2 workers, shutdown")
}
'''
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
