"""Compile the production keyword-notification feature entry and exercise generation ownership without WeChat."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
runtime_source = (root / 'app/src/main/java/t/mma/hooks/items/keywordnotify/KeywordNotificationRuntime.kt').read_text()
feature_source = (root / 'app/src/main/java/t/mma/hooks/items/keywordnotify/KeywordNotificationFeature.kt').read_text()
assert 'Executors.newSingleThreadExecutor' in runtime_source
assert 'notifyGeneration' in runtime_source and 'processingGeneration' in runtime_source
assert 'isCurrentGeneration(generation)' in runtime_source
assert 'if (!notifyGate) return' in runtime_source
assert feature_source.index('trackResource { KeywordNotificationRuntime.stop() }') < feature_source.index('subscribe(Events.DexReady')

stubs = {
    'Context.kt': '''package android.content
open class Context
''',
    'Events.kt': '''package t.mma.event
object Events {
    class DexReady
    class MessageBlocked
}
''',
    'FeatureContext.kt': '''package t.mma.hooks.core
import android.content.Context
class FeatureContext { fun hostContext() = Context(); fun eventBus() = EventBus() }
class EventBus
''',
    'BaseFeature.kt': '''package t.mma.hooks.core
import t.mma.ui.FeatureSettingsProvider
open class BaseFeature {
    private val releases = mutableListOf<() -> Unit>()
    private var closed = false
    open fun featureId() = ""
    open fun name() = ""
    open fun onFeatureInit(context: FeatureContext) {}
    open fun onFeatureInstall(context: FeatureContext) {}
    fun registerSettingsProvider(provider: FeatureSettingsProvider?) {}
    fun trackResource(release: () -> Unit) { if (closed) release() else releases += release }
    fun <T> trackSubscription(subscription: T?): T? = subscription
    fun <E> subscribe(type: Class<E>, handler: (E) -> Unit) {}
    fun close() { if (closed) return; closed = true; releases.asReversed().forEach { it() }; releases.clear() }
}
''',
    'Provider.kt': '''package t.mma.ui
interface FeatureSettingsProvider
''',
    'SettingsProvider.kt': '''package t.mma.hooks.items.keywordnotify
class KeywordNotificationSettingsProvider : t.mma.ui.FeatureSettingsProvider
''',
    'MessageApi.kt': '''package t.mma.hooks.api.core
class ObservedMessage
class Observer { fun subscribe(handler: (ObservedMessage) -> Unit) = Subscription() }
class MessageApi { fun observe() = Observer() }
object WeChatApis { fun message() = MessageApi() }
class Subscription { fun unsubscribe() {} }
''',
    'Log.kt': '''package t.mma.utils
object HLog { fun e(message: String, throwable: Throwable? = null) {} }
''',
    'Runtime.kt': '''package t.mma.hooks.items.keywordnotify
import android.content.Context
import t.mma.hooks.api.core.ObservedMessage
object RuntimeState {
    var enabled = false
    var starts = 0
    var stops = 0
    val queued = mutableListOf<() -> Unit>()
    val main = mutableListOf<() -> Unit>()
    val cache = mutableMapOf<String, String?>()
    var root: String? = null
}
object KeywordNotificationRuntime {
    fun start() { if (!RuntimeState.enabled) { RuntimeState.enabled = true; RuntimeState.starts++ } }
    fun stop() { if (RuntimeState.enabled) { RuntimeState.enabled = false; RuntimeState.stops++ }; RuntimeState.queued.clear(); RuntimeState.main.clear(); RuntimeState.cache.clear(); RuntimeState.root = null }
    fun handleMessage(context: Context, message: ObservedMessage) {}
    fun handleBlockedMessage(context: Context, event: Any) {}
}
''',
    'FeatureTest.kt': '''package t.mma.hooks.items.keywordnotify
import t.mma.hooks.core.FeatureContext
fun main() {
    val feature = KeywordNotificationFeature()
    val context = FeatureContext()
    feature.onFeatureInstall(context)
    check(RuntimeState.enabled && RuntimeState.starts == 1)
    RuntimeState.queued += {}
    RuntimeState.main += {}
    RuntimeState.cache["avatar"] = "bitmap"
    RuntimeState.root = "avatar-root"
    feature.close()
    check(!RuntimeState.enabled && RuntimeState.stops == 1)
    check(RuntimeState.queued.isEmpty() && RuntimeState.main.isEmpty())
    check(RuntimeState.cache.isEmpty() && RuntimeState.root == null)
    feature.close()
    check(RuntimeState.stops == 1)
    val reopened = KeywordNotificationFeature()
    reopened.onFeatureInstall(context)
    check(RuntimeState.enabled && RuntimeState.starts == 2)
    reopened.close()
    check(RuntimeState.stops == 2)
    println("PASS production keyword feature entry: start before subscription, scope cleanup, duplicate destroy and fresh generation")
}
''',
    'GenerationModel.kt': '''
class GenerationModel {
    var gate = false
    var generation = 0L
    val queue = ArrayDeque<() -> Unit>()
    val main = ArrayDeque<() -> Unit>()
    val cache = mutableMapOf<String, String?>()
    var root: String? = null
    fun start() { if (gate) return; generation++; gate = true }
    fun stop() { gate = false; generation++; queue.clear(); main.clear(); cache.clear(); root = null }
    fun post(block: () -> Unit) { if (!gate) return; val g = generation; main.addLast { if (gate && generation == g) block() } }
    fun enqueue() { if (!gate) return; val g = generation; queue.addLast { if (gate && generation == g) {} } }
}
fun main() {
    val runtime = GenerationModel()
    runtime.enqueue(); check(runtime.queue.isEmpty())
    runtime.start(); runtime.enqueue(); runtime.post {}; check(runtime.queue.size == 1 && runtime.main.size == 1)
    runtime.stop(); check(runtime.queue.isEmpty() && runtime.main.isEmpty() && runtime.cache.isEmpty() && runtime.root == null)
    runtime.start(); runtime.post {}; val stale = runtime.main.removeFirst(); runtime.stop(); runtime.start(); stale(); check(runtime.main.isEmpty())
    runtime.enqueue(); runtime.post {}; check(runtime.queue.size == 1 && runtime.main.size == 1)
    runtime.stop(); check(runtime.queue.isEmpty() && runtime.main.isEmpty())
    runtime.start(); runtime.start(); check(runtime.gate); runtime.stop(); runtime.stop(); check(!runtime.gate)
    println("PASS keyword-notification generation model: stale callback rejection, stop cleanup and idempotent restart")
}
''',
}

with tempfile.TemporaryDirectory() as td:
    temp = Path(td)
    for name, content in stubs.items():
        (temp / name).write_text(content)
    compiler = [
        'java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib', '-no-reflect',
        '-classpath', str(stdlib), '-d', str(temp / 'test.jar'),
    ]
    subprocess.run(compiler + [str(path) for path in temp.glob('*.kt')] + [str(root / 'app/src/main/java/t/mma/hooks/items/keywordnotify/KeywordNotificationFeature.kt')], check=True, timeout=45)
    subprocess.run(['java', '-cp', f'{temp / "test.jar"}:{stdlib}', 't.mma.hooks.items.keywordnotify.FeatureTestKt'], check=True, timeout=15)
    subprocess.run(['java', '-cp', f'{temp / "test.jar"}:{stdlib}', 'GenerationModelKt'], check=True, timeout=15)