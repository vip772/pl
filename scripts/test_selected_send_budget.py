"""Compile the production budget; test concurrent retention and all quota dimensions, no host calls."""
from pathlib import Path
import subprocess, tempfile
r = Path(__file__).resolve().parents[1]
b = r / 'app/src/main/java/t/mma/hooks/items/selectedmessages'
code = '''package t.mma.hooks.items.selectedmessages
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
fun main() {
    val budget = SelectedSendBudget()
    check(budget.acquire(0, 1) == null && budget.acquire(1, 0) == null)
    check(budget.acquire(Int.MAX_VALUE, Int.MAX_VALUE) == null)
    val leases = (1..16).map { checkNotNull(budget.acquire(1, 1)) }
    check(budget.acquire(1, 1) == null)
    check(leases.first().release() && !leases.first().release())
    val replacement = checkNotNull(budget.acquire(1, 1))
    leases.forEach { it.release() }; replacement.release()
    val items = checkNotNull(budget.acquire(256, 1))
    check(budget.acquire(1, 1) == null); items.release()
    val targets = checkNotNull(budget.acquire(1, 4096))
    check(budget.acquire(1, 1) == null); targets.release()
    val work = checkNotNull(budget.acquire(256, 256))
    check(budget.acquire(1, 1) == null); work.release()
    val ready = CountDownLatch(64); val start = CountDownLatch(1)
    val admitted = AtomicInteger(); val acquired = CountDownLatch(64)
    val release = CountDownLatch(1)
    val threads = (1..64).map {
        Thread {
            ready.countDown(); check(start.await(5, TimeUnit.SECONDS))
            val lease = budget.acquire(1, 1)
            if (lease != null) admitted.incrementAndGet()
            acquired.countDown(); check(release.await(5, TimeUnit.SECONDS))
            lease?.release()
        }.apply { start() }
    }
    check(ready.await(5, TimeUnit.SECONDS)); start.countDown()
    check(acquired.await(5, TimeUnit.SECONDS)); check(admitted.get() == 16)
    release.countDown(); threads.forEach { it.join(5000); check(!it.isAlive) }
    val after = checkNotNull(budget.acquire(256, 256)); after.release()
    val old = checkNotNull(budget.acquire(1, 1)); budget.close(); budget.close()
    check(budget.acquire(1, 1) == null); check(old.release()); check(!old.release())
    check(budget.acquire(1, 1) == null)
    checkNotNull(SelectedSendBudget().acquire(1, 1)).release()
    println("PASS production SelectedSendBudget: batch/item/target/delivery limits, overflow, 64 concurrent admissions, exact-once refund, close/fresh generation")
}
'''
for name in ('SelectedMessageModuleSender', 'OfficialMassSendSender'):
    source = (b / (name + '.kt')).read_text()
    assert 'sendBudget.acquire(snapshots.size, targetIds.size)' in source
    assert 'if (!accepted) lease.release()' in source
    assert 'if (lease.release()) {' in source
    assert 'sendBudget.close()' in source
libs = Path('/tmp/pl-kotlin-libs'); std = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    p = Path(td); (p / 'Test.kt').write_text(code)
    subprocess.run(['java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
                    '-classpath', str(std), '-d', str(p / 'test.jar'),
                    str(b / 'SelectedSendBudget.kt'), str(p / 'Test.kt')], check=True, timeout=90)
    subprocess.run(['java', '-cp', str(p / 'test.jar') + ':' + str(std),
                    't.mma.hooks.items.selectedmessages.TestKt'], check=True, timeout=30)
print('PASS structural only: both senders reserve before preparation, refund rejected paths in finally and terminal callback, close their own budget')
