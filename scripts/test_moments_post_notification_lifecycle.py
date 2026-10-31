"""Check the production ownership path and exercise the notification close contract."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/t/mma/hooks/items/moments/MomentsPostNotificationFeature.kt').read_text()
install = source.split('override fun onFeatureInstall(', 1)[1].split('companion object', 1)[0]
destroy = source.split('fun destroy() {', 1)[1].split('private fun onPostStored', 1)[0]
assert install.index('resources.register {') < install.index('current.start()')
assert install.index('trackResource { owner.close() }') < install.index('owner.start()')
assert 'current.destroy()' in install
assert 'if (runtime === current) runtime = null' in install
assert 'active.getAndSet(false)' in destroy
assert 'previousSubscription?.unsubscribe()' in destroy
assert 'main.removeCallbacksAndMessages(null)' in destroy
assert 'worker.shutdownNow()' in destroy
assert 'avatarCache.clear()' in destroy and 'observed.clear()' in destroy
assert 'avatarRoot = null' in destroy
assert 'if (!active.get()) return' in source.split('private fun onPostStored(', 1)[1]
assert 'if (!active.get()) return' in source.split('private fun process(', 1)[1]
assert 'main.post { if (active.get()) Toast.makeText' in source
assert 'if (active.get()) manager.notify(' in source
assert 'if (!active.get()) return bitmap' in source

model = r'''
class Runtime {
    var active = false
    var stops = 0
    var notifications = 0
    var toasts = 0
    var avatars = mutableMapOf<String, String>()
    var observed = mutableSetOf<String>()
    var avatarRoot: String? = "root"
    var subscriptionThrows = false
    val pending = mutableListOf<() -> Unit>()
    fun start() { active = true }
    fun enqueue() { if (active) pending += { if (active) notifications++ } }
    fun postToast(): () -> Unit = { if (active) toasts++ }
    fun cacheAvatar() { if (active) avatars["id"] = "bitmap" }
    fun destroy() {
        if (!active) return
        active = false
        try { if (subscriptionThrows) error("unsubscribe") } catch (_: Throwable) { }
        pending.clear()
        avatars.clear()
        observed.clear()
        avatarRoot = null
        stops++
    }
}
fun main() {
    val old = Runtime()
    old.start()
    old.enqueue()
    old.cacheAvatar()
    old.observed += "post"
    val staleToast = old.postToast()
    old.subscriptionThrows = true
    old.destroy()
    old.destroy()
    val fresh = Runtime()
    fresh.start()
    staleToast()
    old.enqueue()
    old.cacheAvatar()
    check(old.notifications == 0 && old.toasts == 0 && old.stops == 1)
    check(old.pending.isEmpty() && old.avatars.isEmpty() && old.observed.isEmpty() && old.avatarRoot == null)
    fresh.enqueue()
    fresh.pending.forEach { it() }
    check(fresh.notifications == 1)
    fresh.destroy()
    println("PASS moments notification close model: stale callbacks, repeated close, cleanup after unsubscribe error")
}
'''
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as td:
    path = Path(td)
    (path / 'Runtime.kt').write_text(model)
    subprocess.run([
        'java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
        '-classpath', str(stdlib), '-d', str(path / 'test.jar'), str(path / 'Runtime.kt')
    ], check=True, timeout=45)
    subprocess.run(['java', '-cp', f'{path / "test.jar"}:{stdlib}', 'RuntimeKt'], check=True, timeout=15)
print('PASS moments notification production-source ownership constraints')
