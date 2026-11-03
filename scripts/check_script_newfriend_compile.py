"""Real compile gate for ScriptNewFriendHook.kt.

Why this file exists as a separate module instead of another phase inside
test_script_plugin_scope.py: the four behavioural phases compile
ScriptPluginFeature's generation, ScriptMessageHook and ScriptMemberChangeHook, but no
phase ever handed ScriptNewFriendHook.kt to the Kotlin compiler. Phase D only pattern
matched its text. That is exactly how commit b20c093 shipped

    val amp = '&'.single()

(Char is not a CharSequence, so single() does not resolve) into a red CI build while
every local phase still printed PASS.

The gate compiles the real production file together with declaration-only stubs for its
host surface. It proves the file parses and type-checks. It does not prove host behaviour
and it is not an Android/R8 build.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
LIBS = Path('/tmp/pl-kotlin-libs')
STD = next(LIBS.glob('kotlin-stdlib-[0-9]*.jar'))
CP = ':'.join(str(p) for p in sorted(LIBS.glob('*.jar')))
CORE = ROOT / 'app/src/main/java/t/mma/hooks/core'
MODEL = ROOT / 'app/src/main/java/t/mma/hooks/api/model'
SCRIPT = ROOT / 'app/src/main/java/t/mma/hooks/items/script'
ANDROID_JAR = Path('/usr/lib/android-sdk/platforms/android-36/android.jar')

OBSERVED = '''package t.mma.hooks.api.message

class ObservedMessage {
    @JvmField val xml: String? = null
    @JvmField val sender: String? = null
    @JvmField val talker: String? = null
    @JvmField val content: String? = null
    @JvmField val msgSource: String? = null
    @JvmField val msgType: String? = null
    @JvmField val selfWxId: String? = null
    @JvmField val source: String? = null
    @JvmField val msgSvrId: Long = 0L
}
'''

OBSERVE_API = '''package t.mma.hooks.api.message

class WeChatMessageObserveApi {
    fun interface Listener {
        fun onObservedMessage(message: ObservedMessage)
    }

    inner class Subscription internal constructor(private val listener: Listener) {
        fun unsubscribe() {}
        fun isActive(): Boolean = true
    }

    fun isAvailable(): Boolean = true
    fun install() {}
    fun subscribe(listener: Listener): Subscription? = Subscription(listener)
    fun unsubscribe(listener: Listener) {}
}
'''

DATABASE_API = '''package t.mma.hooks.api.runtime

import t.mma.hooks.api.model.DatabaseChange

class WeChatDatabaseListenerApi {
    fun interface Listener {
        fun onDatabaseChanged(change: DatabaseChange)
    }

    inner class Subscription internal constructor(private val listener: Listener) {
        fun unsubscribe() {}
    }

    val isAvailable: Boolean get() = true
    fun install() {}
    fun subscribe(listener: Listener): Subscription? = Subscription(listener)
    fun unsubscribe(listener: Listener) {}
}
'''

HOST = '''package t.mma.hooks.api.core

import t.mma.hooks.api.message.WeChatMessageObserveApi
import t.mma.hooks.api.runtime.WeChatDatabaseListenerApi

object WeChatApis {
    @Volatile var databaseListenerApi: WeChatDatabaseListenerApi? = null
    @Volatile var observeApi: WeChatMessageObserveApi? = null

    fun runtime(): RuntimeGroup = RuntimeGroup()
    fun messageObserve(): WeChatMessageObserveApi? = observeApi

    class RuntimeGroup {
        fun databaseChanges(): WeChatDatabaseListenerApi? = databaseListenerApi
    }
}
'''

EVENTS = '''package t.mma.event

class Events {
    class MessageReceived(
        @JvmField val xml: String? = null,
        @JvmField val sender: String? = null,
        @JvmField val talker: String? = null,
        @JvmField val content: String? = null,
        @JvmField val msgType: String? = null,
        @JvmField val createTimeSeconds: Long = 0L,
        @JvmField val msgSvrId: Long = 0L,
        @JvmField val msgSource: String? = null,
        @JvmField val selfWxId: String? = null,
        @JvmField val source: String? = null,
        @JvmField val outgoing: Boolean = false
    )
}
'''

EVENT_BUS = '''package t.mma.event

class EventBus {
    class Subscription

    fun interface EventHandler<E> {
        fun onEvent(event: E)
    }

    fun <E> subscribe(eventType: Class<E>, handler: EventHandler<E>): Subscription = Subscription()
    fun unsubscribe(subscription: Subscription) {}
}
'''

CONTEXT = '''package android.content

open class Context
'''

FEATURE_CONTEXT = '''package t.mma.hooks.core

import android.content.Context
import t.mma.event.EventBus

class FeatureContext(
    private val loader: ClassLoader,
    private val host: Context = Context(),
    private val bus: EventBus = EventBus()
) {
    fun hostClassLoader(): ClassLoader = loader
    fun hostContext(): Context = host
    fun eventBus(): EventBus = bus
}
'''

RUNTIME = '''package t.mma.hooks.items.script

object ScriptPluginRuntime {
    fun dispatchOnNewFriend(wxid: String, ticket: String, scene: Int) {}
}
'''

PARSE_API = '''package t.mma.hooks.api.message

class WeChatMessageParseApi {
    fun getXmlParamByTag(source: String, tag: String): String? = null
}
'''

BEAN = '''package t.mma.hooks.items.script

class ScriptMessageBean(private val raw: Any?) {
    fun getMsgType(): String = ""
    fun getContent(): String = ""
    fun getXml(): String = ""
    fun getMsgSource(): String = ""
    fun getSender(): String = ""
}
'''

HLOG = '''package t.mma.utils

object HLog {
    fun e(message: String, throwable: Throwable?) {}
}
'''

STUBS = {
    'Observed.kt': OBSERVED,
    'ObserveApi.kt': OBSERVE_API,
    'DatabaseApi.kt': DATABASE_API,
    'Host.kt': HOST,
    'Events.kt': EVENTS,
    'EventBus.kt': EVENT_BUS,
    'Context.kt': CONTEXT,
    'FeatureContext.kt': FEATURE_CONTEXT,
    'Runtime.kt': RUNTIME,
    'ParseApi.kt': PARSE_API,
    'Bean.kt': BEAN,
    'HLog.kt': HLOG,
}


def compile_real(target: Path, stubs: dict, real_sources, jar: Path):
    work = target / 'c2'
    work.mkdir(parents=True, exist_ok=True)
    written = []
    for name, body in stubs.items():
        path = work / name
        path.write_text(body, encoding='utf-8')
        written.append(path)
    written.extend(real_sources)
    args = ['java', '-Xmx512m', '-cp', CP, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
            '-nowarn', '-no-stdlib', '-no-reflect',
            '-classpath', str(STD) + ':' + str(ANDROID_JAR), '-d', str(jar)]
    args += [str(s) for s in written]
    return subprocess.run(args, capture_output=True, text=True)


def run(tmp: Path):
    if not LIBS.exists():
        raise SystemExit('missing kotlin libs: ' + str(LIBS))
    if not ANDROID_JAR.is_file():
        raise SystemExit('missing android.jar: ' + str(ANDROID_JAR))
    target = SCRIPT / 'ScriptNewFriendHook.kt'
    if not target.is_file():
        raise SystemExit('missing production source: ' + str(target))
    real = [CORE / 'SharedHookLease.kt',
            MODEL / 'DatabaseChange.kt',
            MODEL / 'WeChatMessageTypes.kt',
            target]
    result = compile_real(tmp, STUBS, real, tmp / 'c2.jar')
    if result.returncode != 0:
        sys.stdout.write(result.stdout)
        sys.stdout.write(result.stderr)
        raise SystemExit('compile failed: ScriptNewFriendHook.kt does not type-check')
    print('PASS real compile: ScriptNewFriendHook.kt type-checks against host declarations')
    print('newfriend lines: ' + str(len(target.read_text(encoding='utf-8').splitlines())))


if __name__ == '__main__':
    import tempfile
    with tempfile.TemporaryDirectory() as raw:
        run(Path(raw))
